package app.orchard.tv.quiz

import android.util.Log
import app.orchard.tv.policy.PolicyCloud
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizPendingKind
import com.planyourtv.policy.quiz.QuizSessionCodec
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizSource
import com.planyourtv.policy.quiz.RemoteQuizConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Keeps the two quiz configuration origins in step.
 *
 * Local edits are written first and uploaded afterwards; a failure never rolls the local value
 * back. Remote versions are applied only when the TV has no unsent local edit. A conflict is a
 * visible state ([QuizSyncState.conflict]), not a silent overwrite: the parent page must offer a
 * refresh/retry action.
 */
object QuizConfigSync {
    private const val TAG = "QuizConfigSync"

    /** This build parses and applies the cloud quiz node. */
    const val SUPPORTS_QUIZ_CONFIG = true

    /** This build pulls questions from QB, so the backend may issue device tickets. */
    const val SUPPORTS_CLOUD_QUIZ = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushMutex = Mutex()
    private val stateLock = Any()

    private var retryJob: Job? = null
    private var retryAttempt = 0
    private var lastRefreshAt = 0L

    sealed interface ApplyResult {
        data object Ignored : ApplyResult
        data object Applied : ApplyResult
        data object PendingLocal : ApplyResult
        data object Conflict : ApplyResult
        data class Rejected(val code: String) : ApplyResult
    }

    sealed interface PushResult {
        data object Synced : PushResult
        data object Skipped : PushResult
        data object NetworkError : PushResult
        data object Rejected : PushResult
        data object Conflict : PushResult
        data object CloudManaged : PushResult
    }

    fun appliedVersion(): Long = QuizSessionStore.loadSourceConfig().configVersion

    fun isCloudManaged(): Boolean =
        QuizSessionStore.loadSourceConfig().source == QuizSource.CLOUD

    fun syncState() = QuizSessionStore.loadSyncState()

    /**
     * A confirmed binding switches the TV to the cloud bank immediately: bound TVs never serve
     * locally published questions again. The previous family's attempt, cached pool and pending
     * uploads are dropped here so the new learner cannot inherit them. The local challenge config
     * is kept: it is this TV's actual configuration and is what the binding report uploads.
     */
    fun onBindingConfirmed(deviceNo: String) {
        if (deviceNo.isBlank()) return
        val now = System.currentTimeMillis()
        // A local READY session from the old publish flow becomes bank rows
        // before the TV switches to the cloud source.
        QuizRecovery.importLegacyLocalReady(now)
        CloudQuizPreparer.discardCloudState(deviceNo)
        synchronized(stateLock) {
            cancelRetryLocked()
            val source = QuizSessionStore.loadSourceConfig()
            QuizSessionStore.saveSourceConfig(
                source.copy(
                    enabled = QuizSessionStore.enabled(),
                    source = QuizSource.CLOUD,
                    configVersion = 0,
                    updatedAt = now,
                ),
            )
            QuizSessionStore.saveSyncState(
                QuizSessionStore.loadSyncState().copy(
                    pendingLocal = true,
                    conflict = false,
                    pendingKind = QuizPendingKind.BINDING,
                    lastError = "",
                    updatedAt = now,
                ),
            )
        }
    }

    /**
     * Uploads the challenge configuration right after the QR binding succeeds. Failure keeps
     * [QuizSyncState.pendingLocal] and is retried; it never blocks the binding success screen.
     */
    suspend fun reportBinding(deviceNo: String): PushResult {
        if (deviceNo.isBlank()) return PushResult.Skipped
        markPending(QuizPendingKind.BINDING)
        return pushNow()
    }

    /**
     * Called after the local parent saved challenge rules or published a session. The local value
     * is already persisted; this only advances the device-local version and starts the upload.
     */
    fun afterLocalEdit() {
        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            val source = QuizSessionStore.loadSourceConfig()
            val next = source.copy(
                enabled = QuizSessionStore.enabled(),
                localVersion = source.localVersion + 1,
                updatedAt = now,
            )
            QuizSessionStore.saveSourceConfig(next)
            // Unbound: there is no cloud row yet. The binding report uploads this snapshot;
            // showing "pending sync" before a binding would describe an upload that cannot exist.
            if (!isBound()) return
            QuizSessionStore.saveSyncState(
                QuizSessionStore.loadSyncState().copy(
                    pendingLocal = true,
                    conflict = false,
                    pendingKind = QuizPendingKind.EDIT,
                    lastError = "",
                    updatedAt = now,
                ),
            )
            retryAttempt = 0
            scheduleRetryLocked()
        }
        scope.launch { pushNow() }
    }

    /** The local on/off switch is part of the same uploaded configuration. */
    fun afterLocalEnabledChange(enabled: Boolean) {
        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            val source = QuizSessionStore.loadSourceConfig()
            QuizSessionStore.saveSourceConfig(
                source.copy(
                    enabled = enabled,
                    localVersion = source.localVersion + 1,
                    updatedAt = now,
                ),
            )
            if (!isBound()) return
            QuizSessionStore.saveSyncState(
                QuizSessionStore.loadSyncState().copy(
                    pendingLocal = true,
                    conflict = false,
                    pendingKind = QuizPendingKind.EDIT,
                    lastError = "",
                    updatedAt = now,
                ),
            )
            retryAttempt = 0
            scheduleRetryLocked()
        }
        scope.launch { pushNow() }
    }

    /** Applies a `quiz` node from `/devices/current/config`. */
    fun applyRemote(remote: RemoteQuizConfig?, nowMs: Long = System.currentTimeMillis()): ApplyResult {
        if (remote == null || remote.version <= 0L) return ApplyResult.Ignored
        var applied = false
        val result = synchronized(stateLock) {
            val current = QuizSessionStore.loadSourceConfig()
            val sync = QuizSessionStore.loadSyncState()
            if ((sync.pendingLocal || sync.conflict) && remote.version > current.configVersion) {
                // Parent priority only after a fresh binding: the QR scan is the consent, and
                // the backend explicitly reports CLOUD for this device. An edit made on the TV
                // stays local and is surfaced as a conflict instead of being overwritten.
                if (sync.pendingKind == QuizPendingKind.BINDING && remote.source == QuizSource.CLOUD) {
                    // 扫码绑定后的交接以本机范围为准；绑定的上报响应才会带回云端权威范围。
                    val appliedResult = applyRemoteLocked(remote, current, nowMs, applyScope = false)
                    if (appliedResult is ApplyResult.Applied) {
                        QuizSessionStore.saveSyncState(
                            emptySyncState(nowMs),
                        )
                        applied = true
                    }
                    appliedResult
                } else {
                    QuizSessionStore.saveSyncState(
                        sync.copy(
                            conflict = true,
                            lastError = "CONFLICT",
                            updatedAt = nowMs,
                        ),
                    )
                    ApplyResult.Conflict
                }
            } else if (remote.version < current.configVersion) {
                // Older than what this TV already applied; ignore instead of stepping back.
                ApplyResult.Ignored
            } else if (remote.version == current.configVersion) {
                ApplyResult.Ignored
            } else {
                val appliedResult = applyRemoteLocked(remote, current, nowMs)
                if (appliedResult is ApplyResult.Applied) {
                    QuizSessionStore.saveSyncState(emptySyncState(nowMs))
                    applied = true
                }
                appliedResult
            }
        }
        if (applied) QuizRuntime.refresh()
        return result
    }

    private fun applyRemoteLocked(
        remote: RemoteQuizConfig,
        current: com.planyourtv.policy.quiz.QuizSourceConfig,
        nowMs: Long,
        applyScope: Boolean = true,
    ): ApplyResult {
        val local = QuizSessionStore.loadChallengeConfig() ?: QuizConfig()
        val next = remote.toQuizConfig(local)
        val shouldApplyScope = applyScope && remote.scopeProvided
        val previousScope = if (shouldApplyScope) QuizAnswerScopeStore.currentSignature() else null
        if (shouldApplyScope && !QuizSessionStore.saveAnswerScope(remote.scope)) {
            return ApplyResult.Rejected("STORAGE")
        }
        if (!QuizSessionStore.saveChallengeConfig(next)) return ApplyResult.Rejected("STORAGE")
        QuizSessionStore.setEnabled(remote.enabled)
        val saved = QuizSessionStore.saveSourceConfig(
            current.copy(
                enabled = remote.enabled,
                source = remote.source,
                configVersion = remote.version,
                localVersion = maxOf(current.localVersion, remote.localVersion),
                updatedAt = nowMs,
            ),
        )
        if (!saved) return ApplyResult.Rejected("STORAGE")
        if (previousScope != null && previousScope != QuizAnswerScopeStore.currentSignature()) {
            Log.i(TAG, "answer scope changed remotely; dropping stale cloud round")
            invalidateCloudRoundAfterScopeChange(remote.deviceNo)
        }
        updateReadySession(next, nowMs)
        Log.i(
            TAG,
            "applied remote quiz config: version=${remote.version} source=${remote.source} " +
                "enabled=${remote.enabled} scopeProvided=$shouldApplyScope",
        )
        return ApplyResult.Applied
    }

    /**
     * 本地改了答题范围：下一轮必须按新范围出题。已就绪的云端题集直接作废；
     * 正在进行的轮次保留（题目已固定，QB 提交只认设备与学习者），只换掉 QB 会话。
     */
    fun afterLocalScopeChange() {
        val cloud = PolicyRuntime.state.value
        val deviceNo = cloud.deviceNo.takeIf { cloud.bound && it.isNotBlank() }
        invalidateCloudRoundAfterScopeChange(deviceNo)
        QuizRuntime.refresh()
    }

    private fun invalidateCloudRoundAfterScopeChange(deviceNo: String?) {
        val session = QuizSessionStore.loadSession()
        val readyCloud = session != null &&
            session.status == QuizSessionStatus.READY &&
            session.source == QbClient.CLOUD_SESSION_SOURCE
        if (readyCloud && !deviceNo.isNullOrBlank()) {
            CloudQuizPreparer.discardCloudState(deviceNo)
        } else {
            QbClient.invalidateSession()
        }
    }

    /** A READY session is the next round; a RUNNING/ACTIVE session keeps its snapshot. */
    private fun updateReadySession(config: QuizConfig, nowMs: Long) {
        val session = QuizSessionStore.loadSession() ?: return
        if (session.status != QuizSessionStatus.READY) return
        if (session.originSchemaVersion < 4) return
        if (session.config == config) return
        if (!QuizSessionStore.saveSession(session.copy(config = config, updatedAt = nowMs))) {
            Log.w(TAG, "ready session config update failed; the next session keeps old defaults")
        }
    }

    /** Soft refresh used when the parent opens the local settings page. */
    fun refreshFromCloudAsync(force: Boolean = false) {
        val cloud = PolicyRuntime.state.value
        if (!cloud.bound || cloud.deviceNo.isBlank()) return
        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            if (!force && now - lastRefreshAt < REFRESH_THROTTLE_MS) return
            lastRefreshAt = now
        }
        scope.launch {
            PolicyCloud.fetchConfig(cloud.deviceNo)
                .onSuccess { snapshot -> applyRemote(snapshot.quiz) }
                .onFailure { Log.w(TAG, "quiz config refresh failed", it) }
        }
    }

    /** Startup: resume a pending upload and pick up a config changed while the TV was off. */
    fun resume() {
        val cloud = PolicyRuntime.state.value
        if (!cloud.bound || cloud.deviceNo.isBlank()) return
        if (QuizSessionStore.loadSyncState().pendingLocal) {
            synchronized(stateLock) {
                retryAttempt = 0
                scheduleRetryLocked()
            }
            scope.launch { pushNow() }
        }
        refreshFromCloudAsync(force = true)
    }

    fun retryNow() {
        scope.launch { pushNow() }
    }

    /**
     * Explicit conflict resolution chosen by the parent: drop the unsent local draft and take
     * the cloud value. Never called automatically.
     */
    fun useRemoteAfterConflictAsync() {
        synchronized(stateLock) {
            val sync = QuizSessionStore.loadSyncState()
            if (!sync.conflict) return
            QuizSessionStore.saveSyncState(emptySyncState(System.currentTimeMillis()))
        }
        refreshFromCloudAsync(force = true)
    }

    fun onUnbound() {
        CloudQuizPreparer.discardCloudState()
        synchronized(stateLock) {
            cancelRetryLocked()
            retryAttempt = 0
            val source = QuizSessionStore.loadSourceConfig()
            QuizSessionStore.saveSourceConfig(
                source.copy(
                    enabled = QuizSessionStore.enabled(),
                    source = QuizSource.LOCAL,
                    configVersion = 0,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            QuizSessionStore.saveSyncState(emptySyncState(System.currentTimeMillis()))
        }
    }

    private suspend fun pushNow(): PushResult = pushMutex.withLock {
        val cloud = PolicyRuntime.state.value
        if (!cloud.bound || cloud.deviceNo.isBlank()) return PushResult.Skipped
        val sync = synchronized(stateLock) {
            val current = QuizSessionStore.loadSyncState()
            if (!current.pendingLocal || current.conflict) return PushResult.Skipped
            current
        }
        val kind = sync.pendingKind
        val source = QuizSessionStore.loadSourceConfig()
        val sentLocal = source.localVersion
        val payload = reportPayload()
        val result = when (kind) {
            QuizPendingKind.BINDING, QuizPendingKind.NONE ->
                PolicyCloud.reportQuizConfig(cloud.deviceNo, payload)
            QuizPendingKind.EDIT ->
                PolicyCloud.updateQuizConfig(
                    deviceNo = cloud.deviceNo,
                    config = payload,
                    localVersion = sentLocal,
                    baseVersion = source.configVersion,
                )
        }
        result.fold(
            onSuccess = { remote ->
                if (remote == null) {
                    markFailure(kind, "SERVER")
                    PushResult.Rejected
                } else {
                    applyPushSuccess(remote, sentLocal, kind)
                }
            },
            onFailure = { error ->
                val rejection = PolicyCloud.isServerRejection(error)
                Log.w(TAG, "quiz config upload failed (${if (rejection) "rejected" else "network"})", error)
                markFailure(kind, if (rejection) rejectionCode(kind) else "NETWORK")
                if (rejection && kind == QuizPendingKind.EDIT) PushResult.Conflict
                else if (rejection) PushResult.Rejected
                else PushResult.NetworkError
            },
        )
    }

    private fun applyPushSuccess(
        remote: RemoteQuizConfig,
        sentLocal: Long,
        kind: QuizPendingKind,
    ): PushResult {
        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            val current = QuizSessionStore.loadSourceConfig()
            val newerEdit = current.localVersion > sentLocal
            if (newerEdit) {
                // A newer local edit is already waiting; upload it against the fresh base version
                // instead of applying a remote snapshot that is about to be superseded.
                QuizSessionStore.saveSourceConfig(
                    current.copy(
                        enabled = remote.enabled,
                        source = QuizSource.LOCAL,
                        configVersion = remote.version,
                        localVersion = maxOf(current.localVersion, remote.localVersion, sentLocal),
                        updatedAt = now,
                    ),
                )
                QuizSessionStore.saveSyncState(
                    emptySyncState(now).copy(
                        pendingLocal = true,
                        pendingKind = QuizPendingKind.EDIT,
                    ),
                )
                retryAttempt = 0
                scheduleRetryLocked()
                Log.i(
                    TAG,
                    "quiz config uploaded: kind=$kind localVersion=$sentLocal version=${remote.version}",
                )
            } else {
                // The response is authoritative (it may also be a duplicate-retry answer that
                // carries a parent edit made after this upload), so converge to it instead of
                // only advancing the version counters. Once bound the backend always answers
                // CLOUD, including the binding report itself.
                val applied = applyRemoteLocked(remote, current, now)
                if (applied is ApplyResult.Applied) {
                    QuizSessionStore.saveSyncState(emptySyncState(now))
                    cancelRetryLocked()
                    retryAttempt = 0
                    Log.i(
                        TAG,
                        "quiz config uploaded: kind=$kind localVersion=$sentLocal version=${remote.version}",
                    )
                } else {
                    QuizSessionStore.saveSyncState(
                        QuizSessionStore.loadSyncState().copy(
                            conflict = true,
                            lastError = (applied as? ApplyResult.Rejected)?.code ?: "STORAGE",
                            updatedAt = now,
                        ),
                    )
                    cancelRetryLocked()
                }
            }
        }
        QuizRuntime.refresh()
        return PushResult.Synced
    }

    private fun markPending(kind: QuizPendingKind) {
        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            val source = QuizSessionStore.loadSourceConfig()
            QuizSessionStore.saveSourceConfig(
                source.copy(enabled = QuizSessionStore.enabled(), updatedAt = now),
            )
            QuizSessionStore.saveSyncState(
                QuizSessionStore.loadSyncState().copy(
                    pendingLocal = true,
                    conflict = false,
                    pendingKind = kind,
                    lastError = "",
                    updatedAt = now,
                ),
            )
            retryAttempt = 0
            scheduleRetryLocked()
        }
    }

    private fun markFailure(kind: QuizPendingKind, code: String) {
        val now = System.currentTimeMillis()
        synchronized(stateLock) {
            val sync = QuizSessionStore.loadSyncState()
            if (!sync.pendingLocal) return
            QuizSessionStore.saveSyncState(
                sync.copy(
                    conflict = code == "CONFLICT",
                    lastError = code,
                    updatedAt = now,
                ),
            )
            if (code != "CONFLICT") {
                scheduleRetryLocked()
            } else {
                cancelRetryLocked()
            }
        }
    }

    private fun rejectionCode(kind: QuizPendingKind): String =
        if (kind == QuizPendingKind.EDIT) "CONFLICT" else "SERVER"

    private fun scheduleRetryLocked() {
        if (retryJob?.isActive == true) return
        val attempt = retryAttempt
        val wait = RETRY_DELAYS_MS[attempt.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
        retryAttempt = attempt + 1
        retryJob = scope.launch {
            delay(wait)
            pushNow()
        }
    }

    private fun cancelRetryLocked() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun isCloudManagedLocked(): Boolean = isCloudManaged()

    private fun isBound(): Boolean {
        val cloud = PolicyRuntime.state.value
        return cloud.bound && cloud.deviceNo.isNotBlank()
    }

    /** The exact snapshot the backend normalizes into `device_quiz_config`. */
    fun reportPayload(): JSONObject {
        val config = QuizSessionStore.loadChallengeConfig() ?: QuizConfig()
        val source = QuizSessionStore.loadSourceConfig()
        val answerScope = QuizAnswerScopeStore.loadOrInitialize()
        return JSONObject()
            .put("enabled", QuizSessionStore.enabled())
            // Binding alone decides the source; the backend normalizes this anyway.
            .put("source", "CLOUD")
            .put("pullCount", config.pullCount)
            .put("minutesPerQuestion", config.minutesPerQuestion)
            .put("pointsPerQuestion", 1)
            .put("defaultTimeLimitSec", config.defaultTimeLimitSec)
            .put("answerDisplaySec", config.answerDisplaySec)
            .put("shuffleOptions", config.shuffleOptions)
            .put("maxEarnedMinutes", config.maxEarnedMinutes)
            // 显式携带范围：本地永远知道自己的范围（null = 绑定出题范围），
            // 不能因为缺字段让云端保留一个电视已经不再使用的范围。
            .put("scopeProvided", true)
            .put("scope", answerScope?.encode() ?: JSONObject.NULL)
            .put("sessionConfig", QuizSessionCodec.encodeConfig(config))
            .put("localVersion", source.localVersion)
    }

    private fun emptySyncState(nowMs: Long) = com.planyourtv.policy.quiz.QuizSyncState(
        pendingLocal = false,
        conflict = false,
        pendingKind = QuizPendingKind.NONE,
        lastError = "",
        updatedAt = nowMs,
    )

    private const val REFRESH_THROTTLE_MS = 15_000L

    private val RETRY_DELAYS_MS = longArrayOf(
        2_000L,
        5_000L,
        15_000L,
        60_000L,
        5 * 60_000L,
        10 * 60_000L,
    )
}
