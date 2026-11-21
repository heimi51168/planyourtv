package app.orchard.tv.quiz

import android.util.Log
import com.planyourtv.policy.PolicyRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * 绑定态的 QCD 云端同步（Phase 2 只读）。
 *
 * 流程：QB 选中孩子 → 主后端 `quiz-rules`（规则）→ QB `deviceLevels`（关卡）→ 组装 + 校验
 * → 原子写 `quiz.doc.cloud.<learnerId>` 与 `quiz.doc.active` → `QuizRuntime.refresh()`。
 *
 * 失败时**永远保留当前 activeDoc**：网络失败不把来源从 CLOUD 换成 LOCAL，也不静默用本机
 * 文档顶替；状态只写状态码，细节进日志。规则与关卡可能独立变化：主后端 ETag 只覆盖规则，
 * 关卡由 QB 的 `levelsEtag` 标识，二者分开比较。
 */
object QuizConfigDocumentSync {
    private const val TAG = "QuizDocSync"

    const val STATE_UNBOUND = "UNBOUND"
    const val STATE_BOUND_SYNCED = "BOUND_SYNCED"
    const val STATE_BOUND_OFFLINE = "BOUND_OFFLINE"
    const val STATE_BOUND_PENDING_LOCAL = "BOUND_PENDING_LOCAL"
    const val STATE_BOUND_CONFLICT = "BOUND_CONFLICT"
    const val STATE_BOUND_OFFLINE_PENDING = "BOUND_OFFLINE_PENDING"
    const val STATE_BOUND_NO_LEARNER = "BOUND_NO_LEARNER"
    const val STATE_CREDENTIAL_INVALID = "BOUND_CREDENTIAL_INVALID"
    const val STATE_INVALID_DOCUMENT = "INVALID_DOCUMENT"
    const val STATE_UNSUPPORTED = "UNSUPPORTED"
    const val STATE_REVISION_REUSE = "REVISION_REUSE"
    const val STATE_STORAGE = "STORAGE"

    private const val THROTTLE_MS = 15_000L
    private val RETRY_DELAYS_MS = longArrayOf(
        2_000L,
        5_000L,
        15_000L,
        60_000L,
        5 * 60_000L,
        10 * 60_000L,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val lock = Any()

    private var retryJob: Job? = null
    private var retryAttempt = 0
    private var lastRequestAt = 0L

    private val _status = MutableStateFlow(QuizDocSyncStatus(state = STATE_UNBOUND))
    val status: StateFlow<QuizDocSyncStatus> = _status.asStateFlow()

    /**
     * 节流刷新：启动/周期配置/MQTT 都会调用；15 秒内重复调用被丢弃。
     * 未绑定直接返回，不产生任何网络请求。
     */
    fun requestSync(force: Boolean = false) {
        val cloud = PolicyRuntime.state.value
        if (!cloud.bound || cloud.deviceNo.isBlank()) {
            _status.value = QuizDocSyncStatus(state = STATE_UNBOUND)
            return
        }
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (!force && now - lastRequestAt < THROTTLE_MS) return
            lastRequestAt = now
        }
        scope.launch { syncNow() }
    }

    /** 启动/回到 CLOUD 模式：重置退避并强制对齐一次。 */
    fun resume() {
        synchronized(lock) {
            retryAttempt = 0
            cancelRetryLocked()
        }
        requestSync(force = true)
    }

    /**
     * 新绑定：先丢弃上一个家庭的云端缓存与 active。本机 QCD 保留。
     * 不在这一步发起同步——绑定交接（设备信息/规则上报）完成后再由调用方触发。
     */
    fun onBindingConfirmed() {
        synchronized(lock) {
            retryAttempt = 0
            cancelRetryLocked()
            lastRequestAt = 0L
        }
        QuizConfigDocumentStore.clearSyncStates()
        QuizConfigDocumentStore.clearActiveDocument()
        QuizConfigDocumentStore.clearCloudDocuments()
        _status.value = QuizDocSyncStatus(state = STATE_BOUND_PENDING_LOCAL)
    }

    /** 解绑：清除云端缓存与 active，恢复本机文档执行；本机 QCD 原样保留。 */
    fun onUnbound() {
        synchronized(lock) {
            retryAttempt = 0
            cancelRetryLocked()
            lastRequestAt = 0L
        }
        QuizConfigDocumentStore.clearSyncStates()
        QuizConfigDocumentStore.clearActiveDocument()
        QuizConfigDocumentStore.clearCloudDocuments()
        _status.value = QuizDocSyncStatus(state = STATE_UNBOUND)
    }

    /** 单次同步。并发调用串行化；不抛异常，失败通过 [QuizDocSyncStatus] 返回。 */
    suspend fun syncNow(): QuizDocSyncStatus = syncMutex.withLock {
        val cloud = PolicyRuntime.state.value
        if (!cloud.bound || cloud.deviceNo.isBlank()) {
            return publish(QuizDocSyncStatus(state = STATE_UNBOUND))
        }

        // 跟随果园当前用户（device_list.active_member_id）：用户切换后必须重新解析 QB 孩子，
        // 否则 QB 上次的选择会继续生效，把上一个孩子的题库下发给新用户。
        val activeMemberId = cloud.cloudActiveMemberId
        val selection = CloudLevelsClient.resolveForMember(activeMemberId)
        val selected = when (selection) {
            is CloudLevelsResult.Ok -> selection.bundle
            is CloudLevelsResult.Unauthorized -> return fail(
                STATE_CREDENTIAL_INVALID,
                "QB_AUTH",
                null,
            )
            is CloudLevelsResult.Retryable -> return offline("QB_LEARNERS", null)
            is CloudLevelsResult.Rejected -> return fail(
                STATE_BOUND_NO_LEARNER,
                // 只透传自己的稳定状态码；QB 拒绝的 message 可能带 HTTP/API 细节，不进状态。
                selection.message.takeIf { it == "NO_LEARNER_FOR_MEMBER" }
                    ?: "NO_SELECTED_LEARNER",
                null,
            )
            is CloudLevelsResult.Invalid -> return fail(
                STATE_INVALID_DOCUMENT,
                "QB_LEARNER_INVALID",
                null,
            )
        }
        val memberId = selected.memberId
            ?: return fail(STATE_BOUND_NO_LEARNER, "MEMBER_ID_MISSING", selected.learnerId)
        val learnerId = selected.learnerId
        val previous = QuizConfigDocumentStore.cloudDocument(learnerId)

        val conditionalEtag = previous?.takeIf { it.rulesPart != null }?.rulesHttpEtag
        val rulesResult = CloudQuizRulesClient.fetch(memberId, conditionalEtag)
        val rulesJson: JSONObject
        var rulesHttpEtag: String? = null
        when (rulesResult) {
            is CloudRulesResult.Ok -> {
                rulesJson = rulesResult.response.document
                rulesHttpEtag = rulesResult.response.etag
            }
            is CloudRulesResult.NotModified -> {
                // 只有确实带着旧规则发出条件请求时才可能收到 304。
                val cached = previous?.rulesPart
                    ?: return fail(STATE_INVALID_DOCUMENT, "RULES_CACHE_MISSING", learnerId)
                rulesJson = cached
                rulesHttpEtag = rulesResult.etag ?: previous.rulesHttpEtag
            }
            is CloudRulesResult.Unauthorized -> return fail(
                STATE_CREDENTIAL_INVALID,
                "RULES_AUTH",
                learnerId,
            )
            is CloudRulesResult.Retryable -> return offline("RULES", learnerId)
            is CloudRulesResult.Rejected -> return fail(
                STATE_INVALID_DOCUMENT,
                "RULES_REJECTED",
                learnerId,
            )
            is CloudRulesResult.Invalid -> return fail(
                STATE_INVALID_DOCUMENT,
                "RULES_INVALID",
                learnerId,
            )
        }

        val levelsResult = CloudLevelsClient.fetch(learnerId)
        val levels = when (levelsResult) {
            is CloudLevelsResult.Ok -> levelsResult.bundle
            is CloudLevelsResult.Unauthorized -> return fail(
                STATE_CREDENTIAL_INVALID,
                "QB_AUTH",
                learnerId,
            )
            is CloudLevelsResult.Retryable -> return offline("QB_LEVELS", learnerId)
            is CloudLevelsResult.Rejected -> return fail(
                STATE_INVALID_DOCUMENT,
                "QB_LEVELS_REJECTED",
                learnerId,
            )
            is CloudLevelsResult.Invalid -> return fail(
                STATE_INVALID_DOCUMENT,
                "QB_LEVELS_INVALID",
                learnerId,
            )
        }
        if (levels.memberId != null && levels.memberId != memberId) {
            return fail(STATE_INVALID_DOCUMENT, "MEMBER_MISMATCH", learnerId)
        }

        val assembled = QuizConfigDocumentAssembler.assemble(rulesJson, levels)
        val bundle = when (assembled) {
            is QuizConfigDocumentAssembler.Result.Ok -> assembled.bundle
            is QuizConfigDocumentAssembler.Result.Invalid -> return fail(
                STATE_INVALID_DOCUMENT,
                "ASSEMBLE_INVALID",
                learnerId,
            )
            is QuizConfigDocumentAssembler.Result.Unsupported -> return fail(
                STATE_UNSUPPORTED,
                "CAPABILITY",
                learnerId,
            )
        }

        val now = System.currentTimeMillis()
        val candidate = CloudQuizDocument(
            document = bundle.document,
            fetchedAt = now,
            qbRevision = bundle.qbRevision,
            levelsSource = bundle.levelsSource,
            checkin = JSONObject(bundle.checkin.toString()),
            levelStatus = JSONObject(bundle.levelStatus.toString()),
            rulesHttpEtag = rulesHttpEtag,
            rulesPart = JSONObject(rulesJson.toString()),
        )

        return when (decide(previous, candidate)) {
            RevisionDecision.Unchanged -> {
                // 关卡可用题数/打卡状态每天都变；刷新云信封但不改 active 文档。
                if (!QuizConfigDocumentStore.saveCloudDocument(learnerId, candidate)) {
                    return fail(STATE_STORAGE, "STORE_CLOUD", learnerId)
                }
                publish(
                    QuizDocSyncStatus(
                        state = STATE_BOUND_SYNCED,
                        learnerId = learnerId,
                        revision = candidate.document.revision,
                        levelsEtag = candidate.document.levelsEtag,
                        syncedAt = now,
                    ),
                )
            }
            RevisionDecision.Stale -> {
                Log.i(
                    TAG,
                    "ignored older cloud revision ${candidate.document.revision} " +
                        "< ${previous?.document?.revision}",
                )
                publish(statusOf(STATE_BOUND_SYNCED, previous, learnerId))
            }
            RevisionDecision.Reuse -> fail(
                STATE_REVISION_REUSE,
                "REVISION_REUSE",
                learnerId,
                previous,
            )
            RevisionDecision.Apply -> {
                if (!QuizConfigDocumentStore.activateCloud(learnerId, candidate)) {
                    return fail(STATE_STORAGE, "STORE_ACTIVE", learnerId, previous)
                }
                QuizRuntime.refresh()
                Log.i(
                    TAG,
                    "activated cloud quiz document: learner=$learnerId " +
                        "revision=${candidate.document.revision} " +
                        "levels=${candidate.document.levels.size}",
                )
                publish(
                    QuizDocSyncStatus(
                        state = STATE_BOUND_SYNCED,
                        learnerId = learnerId,
                        revision = candidate.document.revision,
                        levelsEtag = candidate.document.levelsEtag,
                        syncedAt = now,
                    ),
                )
            }
        }
    }

    private fun offline(code: String, learnerId: Long?): QuizDocSyncStatus {
        val previous = learnerId?.let { QuizConfigDocumentStore.cloudDocument(it) }
        val status = statusOf(STATE_BOUND_OFFLINE, previous, learnerId).copy(
            lastError = code,
        )
        scheduleRetry()
        return publish(status)
    }

    private fun fail(
        state: String,
        code: String,
        learnerId: Long?,
        previous: CloudQuizDocument? = null,
    ): QuizDocSyncStatus {
        val cached = previous ?: learnerId?.let { QuizConfigDocumentStore.cloudDocument(it) }
        return publish(statusOf(state, cached, learnerId).copy(lastError = code))
    }

    private fun statusOf(
        state: String,
        cached: CloudQuizDocument?,
        learnerId: Long?,
    ): QuizDocSyncStatus = QuizDocSyncStatus(
        state = state,
        learnerId = learnerId,
        revision = cached?.document?.revision,
        levelsEtag = cached?.document?.levelsEtag,
        syncedAt = cached?.fetchedAt,
    )

    private fun publish(status: QuizDocSyncStatus): QuizDocSyncStatus {
        // 一次成功同步后重新计退避；否则下一次断网会直接跳到最后几档（最长 10 分钟）。
        if (status.state == STATE_BOUND_SYNCED) {
            synchronized(lock) {
                retryAttempt = 0
                cancelRetryLocked()
            }
        }
        _status.value = status
        val learnerId = status.learnerId
            ?: QuizConfigDocumentStore.cloudLearnerIds().firstOrNull()
        if (learnerId != null) {
            QuizConfigDocumentStore.saveSyncState(
                learnerId,
                QuizDocSyncState(
                    state = status.state,
                    lastError = status.lastError,
                    baseRevision = status.revision,
                    lastSyncAt = status.syncedAt,
                ),
            )
        }
        return status
    }

    private fun scheduleRetry() {
        synchronized(lock) {
            if (retryJob?.isActive == true) return
            val attempt = retryAttempt
            val wait = RETRY_DELAYS_MS[attempt.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
            retryAttempt = attempt + 1
            retryJob = scope.launch {
                delay(wait)
                syncNow()
            }
        }
    }

    private fun cancelRetryLocked() {
        retryJob?.cancel()
        retryJob = null
    }

    /**
     * 云端版本决策。
     *
     * - 主 `revision` 回退：忽略（不降级）。
     * - revision 前进：应用。
     * - revision 相同：规则 ETag 与关卡 `levelsEtag` 都相同 → 无变化；规则变了 → REVISION_REUSE；
     *   只有关卡变（QB 独立版本前进）→ 应用。
     * - 缺少主后端 ETag 时退回整文档 etag 比较（保守）。
     */
    internal fun decide(
        previous: CloudQuizDocument?,
        candidate: CloudQuizDocument,
    ): RevisionDecision {
        if (previous == null) return RevisionDecision.Apply
        val previousDoc = previous.document
        val candidateDoc = candidate.document
        if (candidateDoc.documentId != previousDoc.documentId) {
            return RevisionDecision.Reuse
        }
        if (candidateDoc.revision < previousDoc.revision) return RevisionDecision.Stale
        if (candidateDoc.revision > previousDoc.revision) return RevisionDecision.Apply
        val levelsSame = candidateDoc.levelsEtag == previousDoc.levelsEtag
        val rulesSame = when {
            candidate.rulesHttpEtag != null && previous.rulesHttpEtag != null ->
                candidate.rulesHttpEtag == previous.rulesHttpEtag
            levelsSame -> candidateDoc.etag == previousDoc.etag
            else -> false
        }
        if (rulesSame && levelsSame) return RevisionDecision.Unchanged
        if (!rulesSame) return RevisionDecision.Reuse
        return RevisionDecision.Apply
    }
}

internal sealed interface RevisionDecision {
    data object Apply : RevisionDecision
    data object Unchanged : RevisionDecision
    data object Stale : RevisionDecision
    data object Reuse : RevisionDecision
}

/** 同步状态快照：只含状态码/版本/时间，不含文档内容或服务器错误细节。 */
data class QuizDocSyncStatus(
    val state: String,
    val learnerId: Long? = null,
    val revision: Long? = null,
    val levelsEtag: String? = null,
    val syncedAt: Long? = null,
    val lastError: String? = null,
)
