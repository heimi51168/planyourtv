package app.orchard.tv.policy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import app.orchard.tv.BuildConfig
import app.orchard.tv.R
import app.orchard.tv.guard.HomeGuardService
import app.orchard.tv.policy.web.ParentWebAccess
import app.orchard.tv.policy.web.ParentWebCenter
import app.orchard.tv.quiz.QuizAccess
import app.orchard.tv.quiz.QuizChallenge
import app.orchard.tv.quiz.QuizConfigDocumentSync
import app.orchard.tv.quiz.QuizConfigSync
import app.orchard.tv.quiz.QuizRuntime
import com.planyourtv.policy.AccessMode
import com.planyourtv.policy.Activation
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalPin
import com.planyourtv.policy.LocalRuleSets
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalRulesError
import com.planyourtv.policy.ManualTimerScope
import com.planyourtv.policy.ParentAuthSession
import com.planyourtv.policy.PinResult
import com.planyourtv.policy.PolicyFlowTrace
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyGuardChoice
import com.planyourtv.policy.PolicyGuardSelector
import com.planyourtv.policy.PolicyModeStore
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.PolicyUiState
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.PolicyVoiceBus
import com.planyourtv.policy.RemoteHandoffState
import com.planyourtv.policy.RemoteModeAnnouncer
import com.planyourtv.policy.RemotePolicy
import com.planyourtv.policy.RemotePolicyMode
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.TvStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the NONE/LOCAL/CLOUD choice and makes sure at most one engine is active. NONE is a real
 * mode: no lock screen, no PIN gate, no cloud polling or MQTT. The UI, HomeGuard and MainActivity
 * read this facade, never the engines directly.
 */
object PolicyModeCoordinator {
    sealed interface SwitchResult {
        data object Success : SwitchResult
        data class WrongPin(val result: PinResult) : SwitchResult
        data object PendingMustBeCanceled : SwitchResult
        data class CloudSettlementFailed(val message: String) : SwitchResult
        data object NotReady : SwitchResult
    }

    /** Why the web page may or may not offer its temporary unlock action. */
    enum class WebUnlockReason {
        OK,
        NOT_LOCAL,
        NOT_READY,
        SETUP_REQUIRED,
        PIN_NOT_SET,
        STUDY_MODE,
        ALREADY_UNLOCKED,
        SESSION_ACTIVE,
        QUIZ_ACTIVE,
        SCREEN_OFF,
        NOT_ON_LOCK_SCREEN,
        DEFAULT_PIN,
        STATE_CHANGED,
    }

    /** Lock state published by the page heartbeat; [eligible] drives the action button. */
    data class WebUnlockStatus(
        val eligible: Boolean,
        val reason: WebUnlockReason,
        val unlocked: Boolean,
    )

    /** Result of the page's unlock action. A rejected PIN never changes the guard. */
    sealed interface WebUnlockResult {
        data object Ok : WebUnlockResult
        data class Rejected(val reason: WebUnlockReason) : WebUnlockResult
        data class PinInvalid(val locked: Boolean) : WebUnlockResult
    }

    /** Why the web page may or may not start one manual session. */
    enum class WebManualStartReason {
        OK,
        NOT_LOCAL,
        NOT_READY,
        SETUP_REQUIRED,
        PIN_NOT_SET,
        STUDY_MODE,
        ALREADY_UNLOCKED,
        SESSION_ACTIVE,
        SESSION_PENDING,
        QUIZ_ACTIVE,
        SCREEN_OFF,
        NOT_ON_LOCK_SCREEN,
        DEFAULT_PIN,
        RESTING,
        OUTSIDE_WINDOW,
        DAILY_LIMIT,
        NO_TIMED_APPS,
        /** The manual scope has only unlimited apps, so a countdown would be meaningless. */
        ONLY_UNLIMITED,
        STATE_CHANGED,
    }

    /** Lock-screen state published to the page; [eligible] drives the single-use action. */
    data class WebManualStartStatus(
        val eligible: Boolean,
        val reason: WebManualStartReason,
        val restRemainingSeconds: Long = 0L,
        val remainingSeconds: Long = 0L,
        /**
         * True when [reason] is [WebManualStartReason.SESSION_ACTIVE] and the running session
         * came from the parent's out-of-plan action. Pages must not show a child's plan timer
         * as an out-of-plan countdown.
         */
        val outsidePlan: Boolean = false,
    )

    /** Result of the page's single-use action. A rejected PIN never starts a session. */
    sealed interface WebManualStartResult {
        data object Ok : WebManualStartResult
        data object InvalidDuration : WebManualStartResult
        data class Rejected(val reason: WebManualStartReason) : WebManualStartResult
        data class PinInvalid(val locked: Boolean) : WebManualStartResult
    }

    /**
     * Outcome of the garden's remote single session. [message] is shown to the parent verbatim
     * in the orchard page, so it must always be human text.
     */
    data class RemoteSingleSessionResult(val ok: Boolean, val message: String)

    /** Result of the one-time local-to-cloud rule handoff. */
    sealed interface HandoffResult {
        data object Success : HandoffResult
        data object NotNeeded : HandoffResult
        data object NeedPassword : HandoffResult
        data object NoRules : HandoffResult
        data object Busy : HandoffResult
        data class Failed(val message: String) : HandoffResult
    }

    private const val TAG = "PolicyMode"
    private const val MODE_HEARTBEAT_MS = 5 * 60_000L

    /**
     * Garden "single session" bounds; mirrors the orchard page (1–180 minutes).
     * 2026-11-21: minimum lowered from 5 to 1 so parents can run quick end-to-end tests.
     */
    private const val SINGLE_SESSION_MIN_MINUTES = 1
    private const val SINGLE_SESSION_MAX_MINUTES = 180

    /**
     * A remote timer first aligns the TV's cloud active member, so the wait covers one config
     * fetch plus a possible second fetch when the switch landed mid-request.
     */
    private const val SINGLE_SESSION_SYNC_TIMEOUT_MS = 10_000L
    private const val SINGLE_SESSION_SYNC_POLL_MS = 250L

    /**
     * 2026-11-19: QR binding reports this TV's own device state only. It no longer uploads
     * the local rules or the parent password; the cloud config and the local config stay
     * independent. The handoff implementation below is kept intact as a rollback path, but
     * it must not be invoked while this is false.
     */
    const val LOCAL_RULES_UPLOAD_ENABLED = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var context: Context? = null
    private var devPin: String? = null
    private var initialized = false
    private var collectorsStarted = false
    private var heartbeatStarted = false

    @Volatile
    var mode: PolicyMode = PolicyMode.NONE
        private set

    /**
     * A bound V6 TV in RULE (or handoff PENDING) runs the same local rule engine behind the
     * cloud facade. PolicyRuntime keeps owning network/binding; this flag selects the guard.
     */
    private var ruleGuardActive = false
    private var appliedRemoteMode: RemotePolicyMode? = null
    private var appliedRemoteVersion = 0L
    private val handoffInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Speaks only after the first observed cloud mode, so startup stays quiet. */
    private val remoteModeAnnouncer = RemoteModeAnnouncer()

    private val _state = MutableStateFlow(PolicyUiState(mode = PolicyMode.NONE, loaded = false))
    val state: StateFlow<PolicyUiState> = _state.asStateFlow()

    /**
     * A parent who verified the PIN in the parent center or on the lock screen is not asked for
     * it again during the same temporary unlock. Only the proof expiry is persisted to MMKV;
     * the PIN itself stays in memory. Screen-off, guard resume, mode switch and unbind clear it,
     * and [start] drops a restored proof unless the live guard is still temporarily unlocked.
     */
    private val parentAuth = ParentAuthSession()
    val parentAuthenticated: StateFlow<Boolean> = parentAuth.authenticated

    /**
     * The PIN that proved the current parent access. The parent center uses it for actions that
     * need the value (disable guard, handoff), matching what typing the PIN at its own gate
     * would have provided. Null when only a restored, secret-free proof exists.
     */
    fun sharedParentPin(): String? =
        if (parentAuth.authenticated.value) parentAuth.verifiedPin else null

    /** Share a PIN verified by the parent-center gate or dialog with the rest of the TV. */
    fun markParentVerified(pin: String) {
        parentAuth.onParentVerified(pin)
    }

    /**
     * Whether the shared parent proof is still inside its TTL. The web parent center cannot
     * rely on [parentAuthenticated]: the flow may stay true until the next guard event.
     */
    fun parentProofValid(): Boolean = parentAuth.proofValid()

    /** Remaining parent proof time in milliseconds; zero when no proof is valid. */
    fun parentProofRemainingMs(): Long = parentAuth.remainingMs()

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Last local access scope seen by the collector; Home reloads only when this changes. */
    private var lastLocalScopeFingerprint: String? = null
    /**
     * Last local session state seen by the collector. The engines publish every second, but a
     * real PENDING/ACTIVE/IDLE transition must reach the phone without waiting for the five
     * minute heartbeat, because the phone uses it as the device-level start mutex.
     */
    private var lastObservedSessionState: SessionState? = null
    /** Dedupe for the hosted-flow trace; the engines publish every second. */
    private var lastFlowStateTrace = ""

    @Synchronized
    fun start(ctx: Context, debugPin: String? = null) {
        val app = ctx.applicationContext
        context = app
        devPin = debugPin
        if (!TvStore.isReady && !TvStore.init(app)) return
        // Before the user is unlocked, credential-protected storage is unavailable. Do not
        // decide a mode here: the boot receiver would otherwise save a guess.
        if (!TvStore.isReady) return
        // The session object exists before the store is ready; restore the persisted proof now
        // that reads are real. The guard check below still drops it unless it is still unlocked.
        parentAuth.reloadFromStore()
        if (!initialized) {
            val stored = PolicyModeStore.load()
            // Fresh installs have no stored mode and no cloud identity: NONE. An old cloud
            // install with no mode key is migrated to CLOUD; an unbound one falls back to NONE
            // below. The choice is persisted so a later update cannot silently flip it.
            val restored = stored ?: if (PolicyRuntime.hasStoredCloudIdentity()) PolicyMode.CLOUD else PolicyMode.NONE
            // A community build has no backend to talk to. If it inherits a cloud choice (for
            // example after a build made without -PtvApiBase), start unguarded instead of
            // showing a binding screen that can never complete.
            mode = if (!BuildConfig.CLOUD_ENABLED && restored == PolicyMode.CLOUD) {
                Log.i(TAG, "cloud mode requested without a backend; starting in NONE")
                PolicyMode.NONE
            } else {
                restored
            }
            PolicyModeStore.save(mode)
            PolicyRuntime.start(
                app,
                devPin = if (mode == PolicyMode.CLOUD) debugPin else null,
                active = mode == PolicyMode.CLOUD,
            )
            LocalPolicyRuntime.initialize(app)
            // A restart while the binding screen was open must not cancel the pending cloud
            // choice; keep CLOUD and show the binding screen again. Only an explicit Back or a
            // confirmed unbind returns to NONE.
            if (mode == PolicyMode.CLOUD && !PolicyRuntime.state.value.bound) {
                PolicyRuntime.setTemporaryUnlock(true)
            }
            initialized = true
            startCollectors()
            startModeHeartbeat()
            applyRemotePolicyMode(PolicyRuntime.remotePolicy.value)
            if (mode == PolicyMode.CLOUD) QuizConfigSync.resume()
            when (mode) {
                PolicyMode.LOCAL -> {
                    LocalPolicyRuntime.setSource(PolicySource.LOCAL)
                    // Do not reset the guard here: initialize() has already restored a
                    // parent override or study lease that still has live evidence.
                    LocalPolicyRuntime.activate()
                }
                PolicyMode.CLOUD -> {
                    if (!ruleGuardActive) {
                        LocalPolicyRuntime.setSource(PolicySource.NONE)
                        LocalPolicyRuntime.deactivate()
                    }
                }
                PolicyMode.NONE -> {
                    LocalPolicyRuntime.setSource(PolicySource.NONE)
                    LocalPolicyRuntime.deactivate()
                }
            }
            _state.value = currentEngineState()
            // A restored MMKV proof is only valid if this process still holds the temporary
            // unlock. After a restart the guard is enforcing again, so the proof is dropped.
            parentAuth.onGuardStatus(_state.value.guardState)
            reportModeNow()
        } else {
            // Later callers (HomeGuard/MainActivity) must not re-enable the wrong engine.
            PolicyRuntime.start(
                app,
                devPin = if (mode == PolicyMode.CLOUD) debugPin else null,
                active = mode == PolicyMode.CLOUD,
            )
            LocalPolicyRuntime.initialize(app)
            applyRemotePolicyMode(PolicyRuntime.remotePolicy.value)
            if (mode == PolicyMode.CLOUD) QuizConfigSync.resume()
            if (mode == PolicyMode.NONE) _state.value = noneState()
            parentAuth.onGuardStatus(currentEngineState().guardState)
            startModeHeartbeat()
            reportModeNow()
        }
    }

    private fun startCollectors() {
        if (collectorsStarted) return
        collectorsStarted = true
        scope.launch {
            PolicyRuntime.state.collect { cloud ->
                if (mode == PolicyMode.CLOUD) {
                    // The redemption engine resumes the guard on screen-off even if HomeGuard
                    // was not the one that observed it; the parent auth must expire with it.
                    // Read the live state: a slow collector can still be handling the previous
                    // RUNNING emission after submitPin has already moved the engine to PAUSED.
                    if (!ruleGuardActive) parentAuth.onGuardStatus(PolicyRuntime.state.value.guardState)
                    _state.value = if (ruleGuardActive) ruleCloudState(cloud) else cloudState(cloud)
                }
            }
        }
        scope.launch {
            LocalPolicyRuntime.state.collect { local ->
                if (mode == PolicyMode.LOCAL || (mode == PolicyMode.CLOUD && ruleGuardActive)) {
                    // Live state for the same reason as the cloud collector above.
                    parentAuth.onGuardStatus(LocalPolicyRuntime.state.value.guardState)
                }
                when {
                    mode == PolicyMode.LOCAL -> _state.value = local.copy(mode = PolicyMode.LOCAL)
                    mode == PolicyMode.CLOUD && ruleGuardActive ->
                        _state.value = ruleCloudState(PolicyRuntime.state.value)
                }
                // Prime silently on the first emission; a later PENDING/ACTIVE/IDLE move is a
                // real session transition the phone must see before its next heartbeat.
                val sessionNow = local.sessionState
                val previousSession = lastObservedSessionState
                lastObservedSessionState = sessionNow
                if (previousSession != null && previousSession != sessionNow &&
                    mode == PolicyMode.CLOUD && ruleGuardActive
                ) {
                    reportModeNow()
                }
                // The local engine publishes every second, but Home only needs a reload when the
                // usable-app scope actually changes. This is what makes a local/cloud app-list
                // edit visible immediately instead of at the next session.
                val fingerprint = accessFingerprint(local)
                val previous = lastLocalScopeFingerprint
                lastLocalScopeFingerprint = fingerprint
                if (previous != null && previous != fingerprint) {
                    _revision.value = _revision.value + 1L
                }
            }
        }
        scope.launch {
            PolicyRuntime.remotePolicy.collect { remote ->
                if (mode == PolicyMode.CLOUD) {
                    applyRemotePolicyMode(remote)
                    // 入口展示是设备级字段（2026-11-21），单独改它不会改规则版本，也不会经过
                    // applyRemoteRule 的状态回写；这里重算一次 UiState，让信号源入口立即跟随。
                    // 值相等时 StateFlow 不发射，不会造成额外重组。
                    _state.value = currentEngineState()
                }
            }
        }
        // 设备级「入口展示 → 答题闯关」（2026-11-21）：入口可见性由 QuizRuntime 合成到锁屏，
        // 单独改这一项不会改规则版本，因此在这里显式让它重算一次；刷新在后台协程执行，
        // 不阻塞主线程。首次发射也刷新一次：进程重启后磁盘里的快照可能已经把入口关掉，
        // 不能只等下一次变化。null（旧后端）→ 值 的变化同样触发（后端升级路径）。
        scope.launch {
            PolicyRuntime.remotePolicy
                .map { it?.quizEntryVisible }
                .distinctUntilChanged()
                .collect { QuizRuntime.refresh() }
        }
        // Hosted state is partitioned per garden member. A switch can arrive through the config
        // poll without any rule change, so the executor must re-scope on the member id itself —
        // otherwise the outgoing child's ledger and rest cursor stay in memory and the new child
        // inherits them (2026-11-21: 佳奇's 6-minute rest followed the switch to another user).
        scope.launch {
            PolicyRuntime.state
                .map { it.cloudActiveMemberId }
                .distinctUntilChanged()
                .collect { member ->
                    if (mode != PolicyMode.CLOUD) return@collect
                    // 题库跟着当前用户切换（2026-11-21）：先让锁屏/答题页立刻反映「切换中」，
                    // 再强制重同步 QCD——同步内部会按 memberId 重新 select QB 孩子并激活新文档。
                    // 首次发射也执行：进程重启后磁盘 active 文档可能属于上一个用户。
                    QuizRuntime.refresh()
                    QuizConfigDocumentSync.requestSync(force = true)
                    if (!LocalPolicyRuntime.isHostedExecutionLoaded()) return@collect
                    if (LocalPolicyRuntime.ensureHostedSubject(member ?: 0L)) publishCurrent()
                }
        }
        // The REST grant poll is a delivery path equal to the MQTT command. In hosted mode it
        // must arm the local confirm gate, never the legacy PENDING session.
        scope.launch {
            PolicyRuntime.cloudGrant.collect { grant ->
                if (mode != PolicyMode.CLOUD || !ruleGuardActive) return@collect
                if (LocalPolicyRuntime.state.value.source != PolicySource.HOSTED) return@collect
                // The grant poll re-sends a decreasing window each tick, so only a transition
                // into PENDING may bring Home forward; repeated merges must stay quiet.
                val wasPending =
                    LocalPolicyRuntime.state.value.sessionState == SessionState.PENDING
                val result = if (grant != null) {
                    // Warm the avatar cache before arming. The gate's first PENDING frame then
                    // shows the recipient identity together with the orange button and voice,
                    // instead of swapping the apple a frame later.
                    if (grant.userIconUrl.isNotBlank()) {
                        context?.let { PolicyAvatarStore.load(it, grant.userIconUrl) }
                    }
                    LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant)
                } else {
                    LocalPolicyRuntime.onHostedGrantRemoved()
                    null
                }
                PolicyFlowTrace.event(
                    "grant.deliver",
                    "recordId" to (grant?.recordId ?: 0),
                    "wasPending" to wasPending,
                    "result" to when (result) {
                        is SessionStartResult.Pending -> "PENDING"
                        is SessionStartResult.Started -> "STARTED"
                        is SessionStartResult.Rejected -> "REJECTED:${result.reason}"
                        null -> "REMOVED"
                    },
                )
                if (!wasPending && result is SessionStartResult.Pending) {
                    PolicyRuntime.bringHome()
                }
                publishCurrent()
            }
        }
        // Keep the old "reload Home when cloud policy changes" behaviour. Local state updates
        // every second, so it must not drive Home reloads.
        scope.launch {
            PolicyRuntime.revision.collect {
                if (mode == PolicyMode.CLOUD) _revision.value = _revision.value + 1L
            }
        }
        if (PolicyFlowTrace.enabled()) {
            scope.launch {
                _state.collect { traceFlowState(it) }
            }
        }
    }

    private fun noneState(): PolicyUiState {
        val cloud = PolicyRuntime.state.value
        val local = LocalPolicyRuntime.state.value
        return PolicyUiState(
            loaded = initialized,
            mode = PolicyMode.NONE,
            sessionState = SessionState.IDLE,
            guardState = GuardStatus.ENFORCING,
            pinConfigured = false,
            bound = cloud.bound,
            deviceNo = cloud.deviceNo,
            localRulesConfigured = LocalPolicyRuntime.currentRules?.isUsable == true,
            setupRequired = false,
            dailyUsedSeconds = local.dailyUsedSeconds,
            dailyLimitSeconds = local.dailyLimitSeconds,
            sessionLimitSeconds = local.sessionLimitSeconds,
            sessionCapSeconds = local.sessionCapSeconds,
            unlockRoutePlan = local.unlockRoutePlan,
            nextWindowLabel = local.nextWindowLabel,
            todayWindowsLabel = local.todayWindowsLabel,
            windowRemainingSeconds = local.windowRemainingSeconds,
            nextWindowInSeconds = local.nextWindowInSeconds,
        )
    }

    /** A bound CLOUD TV with no active session always needs a grant; the UI uses this for the
     *  mini-program guide. Deriving it here keeps the guide correct even if a config poll was
     *  missed or failed. */
    private fun cloudState(state: PolicyUiState): PolicyUiState = state.copy(
        mode = PolicyMode.CLOUD,
        remoteMode = PolicyRuntime.remotePolicy.value?.mode,
        cloudNeedsGrant = state.bound && state.sessionState == SessionState.IDLE,
    )

    private fun currentEngineState(): PolicyUiState {
        val base = when (mode) {
            PolicyMode.CLOUD -> if (ruleGuardActive) {
                ruleCloudState(PolicyRuntime.state.value)
            } else {
                cloudState(PolicyRuntime.state.value)
            }
            PolicyMode.LOCAL -> LocalPolicyRuntime.state.value.copy(mode = PolicyMode.LOCAL)
            PolicyMode.NONE -> noneState()
        }
        if (mode != PolicyMode.CLOUD) return base
        // 入口展示（2026-11-21）：设备级云端开关。字段缺失（旧后端）按「显示」保持历史行为。
        // 放进 UiState 是为了让只改这一项、规则版本没变的刷新也能触发界面重组。
        val remote = PolicyRuntime.remotePolicy.value
        return base.copy(
            sourceEntryVisible = remote?.sourceEntryVisible ?: true,
            quizEntryVisible = remote?.quizEntryVisible ?: true,
        )
    }

    /**
     * Cloud identity/binding fields from PolicyRuntime, guard fields from the rule engine.
     * The UI still sees mode=CLOUD; only the guard source changes.
     */
    private fun ruleCloudState(cloud: PolicyUiState): PolicyUiState {
        val local = LocalPolicyRuntime.state.value
        return cloud.copy(
            remoteMode = PolicyRuntime.remotePolicy.value?.mode,
            sessionState = local.sessionState,
            guardState = local.guardState,
            activation = local.activation,
            source = PolicySource.HOSTED,
            policyEpoch = local.policyEpoch,
            studyModeAvailable = local.studyModeAvailable,
            studyModeActive = local.studyModeActive,
            studyPackages = local.studyPackages,
            studyEarnPackages = local.studyEarnPackages,
            studyEarnStudyMinutes = local.studyEarnStudyMinutes,
            studyEarnRewardMinutes = local.studyEarnRewardMinutes,
            quizGateEnabled = local.quizGateEnabled,
            studyEarnEnabled = local.studyEarnEnabled,
            directStartEnabled = local.directStartEnabled,
            unlockRoutePlan = local.unlockRoutePlan,
            noTimedApps = local.noTimedApps,
            totalSeconds = local.totalSeconds,
            remainingSeconds = local.remainingSeconds,
            effectiveRemainingSeconds = local.effectiveRemainingSeconds,
            activeSessionOutsidePlan = local.activeSessionOutsidePlan,
            // The armed grant's "valid until" label comes from the local pending object; the
            // legacy field is identity-only in hosted mode.
            validUntil = local.validUntil,
            // The REST-armed pending object carries the recipient identity in the same publish
            // as the button/voice. A record-less PTV dispatch leaves these empty, so the cloud
            // identity (the previous avatar) stays unchanged.
            userName = local.userName.ifEmpty { cloud.userName },
            userIconUrl = local.userIconUrl.ifEmpty { cloud.userIconUrl },
            pinConfigured = local.pinConfigured,
            cloudNeedsGrant = false,
            localRulesConfigured = local.localRulesConfigured,
            setupRequired = local.setupRequired,
            dailyUsedSeconds = local.dailyUsedSeconds,
            dailyLimitSeconds = local.dailyLimitSeconds,
            sessionLimitSeconds = local.sessionLimitSeconds,
            sessionCapSeconds = local.sessionCapSeconds,
            configuredRestMinutes = local.configuredRestMinutes,
            configuredBufferMinutes = local.configuredBufferMinutes,
            nextWindowLabel = local.nextWindowLabel,
            todayWindowsLabel = local.todayWindowsLabel,
            windowRemainingSeconds = local.windowRemainingSeconds,
            nextWindowInSeconds = local.nextWindowInSeconds,
            restRemainingSeconds = local.restRemainingSeconds,
            parentUnlockRemainingSeconds = local.parentUnlockRemainingSeconds,
            lockReason = local.lockReason,
            // The app scope is a live access decision, not a session-budget snapshot: the
            // latest synced rules must apply to a session that is already running.
            accessMode = local.accessMode,
            allowedPackages = local.allowedPackages,
            // 2026-11-21: the lock screen docks render unlimited tiles from this set. Omitting
            // it here silently dropped the cloud-projected "无限制应用" (for example 央视频).
            unlimitedPackages = local.unlimitedPackages,
        )
    }

    /**
     * Selects the guard from the latest read-only cloud policy.
     *
     * - bound + DONE + RULE: remote rules become the local template and usage is reported.
     * - bound + PENDING with existing local rules: keep the local rule guard during migration.
     * - bound + REDEEM / old backend / no policy node: existing redemption engine.
     */
    @Synchronized
    private fun applyRemotePolicyMode(remote: RemotePolicy?) {
        val choice = PolicyGuardSelector.select(
            bound = mode == PolicyMode.CLOUD && PolicyRuntime.state.value.bound,
            policy = remote,
            localRulesUsable = LocalPolicyRuntime.currentRules?.isUsable == true,
        )
        announceRemoteMode(remote)
        when (choice) {
            PolicyGuardChoice.Inactive -> {
                stopRuleGuard()
                publishCurrent()
                return
            }
            PolicyGuardChoice.CloudRedeem -> {
                stopRuleGuard()
                // Leaving the rule guard is what applies REDEEM. Record it before reporting:
                // the phone's exchange guard compares appliedMode/appliedVersion with the
                // cloud config, and reportModeOnce() only sends these two fields. Without
                // this the TV keeps reporting the previous RULE version, so every exchange
                // stays blocked as "still switching" even though the engine already changed.
                val redeem = remote?.takeIf {
                    it.handoffState == RemoteHandoffState.DONE &&
                        it.mode == RemotePolicyMode.REDEEM
                }
                if (redeem != null) {
                    val changed = appliedRemoteMode != RemotePolicyMode.REDEEM ||
                        appliedRemoteVersion != redeem.configVersion
                    appliedRemoteMode = RemotePolicyMode.REDEEM
                    appliedRemoteVersion = redeem.configVersion
                    publishCurrent()
                    // Report immediately instead of waiting for the 5-minute mode heartbeat.
                    if (changed) reportModeNow()
                } else {
                    publishCurrent()
                }
                return
            }
            is PolicyGuardChoice.RemoteRule -> {
                if (!applyRemoteRule(choice.policy)) {
                    stopRuleGuard()
                    publishCurrent()
                    return
                }
            }
            PolicyGuardChoice.PendingLocalRule -> {
                // Migration is not complete: keep counting with the TV's own rules, but do not
                // attach the cloud usage outbox yet.
                if (LocalPolicyRuntime.isHostedExecutionLoaded()) {
                    // A hosted session is already running, so a PENDING handoff is stale (the
                    // migration had completed). Flipping the source here would checkpoint the
                    // hosted usage into the local partition; keep the hosted executor until a
                    // complete document replaces the template.
                    Log.w(TAG, "policy regressed to PENDING while hosted execution is loaded; keeping hosted session")
                } else {
                    LocalPolicyRuntime.setSource(PolicySource.LOCAL)
                    RuleUsageReporter.stop()
                }
            }
        }

        if (!ruleGuardActive) {
            // Stop the legacy grant engine before the local one starts: it remains the network
            // and identity adapter, but only the local executor may own a pending gate.
            PolicyRuntime.setHostedRuleActive(true)
            // Attaching the guard is not a mode switch: a cold start may be attaching after
            // initialize() restored an evidence-backed override. Genuine LOCAL/CLOUD/NONE
            // transitions reset the guard at their own call sites.
            LocalPolicyRuntime.activate()
            ruleGuardActive = true
            if (LocalPolicyRuntime.state.value.source == PolicySource.HOSTED) {
                val wasPending =
                    LocalPolicyRuntime.state.value.sessionState == SessionState.PENDING
                val result = PolicyRuntime.cloudGrant.value?.let {
                    LocalPolicyRuntime.armRemoteManualSessionFromGrant(it)
                }
                if (!wasPending && result is SessionStartResult.Pending) {
                    PolicyRuntime.bringHome()
                }
            }
        }
        publishCurrent()
        reportModeNow()
    }

    /**
     * The phone's mode switch reaches the TV through the config poll. Prime the first observed
     * mode without speaking; only a later RULE/REDEEM transition is a user-visible switch.
     */
    private fun announceRemoteMode(remote: RemotePolicy?) {
        val cue = remoteModeAnnouncer.observe(
            mode = remote?.mode,
            active = mode == PolicyMode.CLOUD && PolicyRuntime.state.value.bound,
        ) ?: return
        PolicyVoiceBus.emit(cue)
    }

    /**
     * An idle TV keeps presenting the frame from before a remote edit, so the new badge, actions
     * and rule details are not seen until a key arrives; see PolicyPanelRefresh for the evidence
     * and the BACK-plus-redraw nudge.
     */
    private fun presentRemoteChangeOnPanel() {
        if (!isLockScreen()) return
        PolicyPanelRefresh.afterRemoteChange()
    }

    /** Applies a DONE/RULE template. The running session keeps its old plan. */
    private fun applyRemoteRule(remote: RemotePolicy): Boolean {
        // Old backends only carry one set; treat it as the manual set. The member mask decides
        // which of the fields the cloud actually projected for the active orchard member.
        val incomingSets = (remote.ruleSets ?: LocalRuleSets.from(remote.rules))
            .preservingDeviceFieldsFrom(
                LocalPolicyRuntime.currentRuleSets,
                remote.memberScopedFields,
            )
        // The first rule after entering CLOUD must switch to the hosted execution partition.
        // Mutating the template alone would keep counting in whatever ledger the previous mode
        // left in memory, and the next checkpoint would persist it under the hosted keys.
        val enteringPartition = !LocalPolicyRuntime.isHostedExecutionLoaded()
        val activeMemberId = PolicyRuntime.state.value.cloudActiveMemberId ?: 0L
        // Member switches that re-project rules must not apply the new template on top of the
        // outgoing member's partition; adopt the new scope first (settles the old session).
        if (!enteringPartition && activeMemberId > 0L) {
            LocalPolicyRuntime.ensureHostedSubject(activeMemberId)
        }
        val rulesChanged = appliedRemoteVersion != remote.configVersion ||
            LocalPolicyRuntime.currentRuleSets != incomingSets
        if (rulesChanged || enteringPartition) {
            // One line per real snapshot change (config polls repeat the same version): which
            // member mask arrived and what the TV will actually enforce. This is the first
            // place to look when a parent edit does not reach the desktop.
            val mask = remote.memberScopedFields
            // Study X/Y live on the materialized rules, not on the rule-set value object.
            val materialized = incomingSets.rules()
            PolicyFlowTrace.event(
                "remote.rules.apply",
                "version" to remote.configVersion,
                "enteringHosted" to enteringPartition,
                "changed" to rulesChanged,
                "mask" to (mask?.joinToString(",") ?: "LEGACY_ALL_LOCAL"),
                "allowed" to incomingSets.shared.allowedPackages.size,
                "study" to incomingSets.shared.studyPackages.size,
                "unlimited" to incomingSets.shared.unlimitedPackages.size,
                "earn" to incomingSets.shared.studyEarnPackages.size,
                "dayGroups" to incomingSets.manual.dayGroups.size,
                "cap" to incomingSets.manual.sessionCapMinutes,
                "direct" to incomingSets.manual.directStartEnabled,
                "quiz" to incomingSets.manual.quizGateEnabled,
                "studyMin" to materialized.studyEarnStudyMinutes,
                "rewardMin" to materialized.studyEarnRewardMinutes,
                "usable" to incomingSets.isUsable,
                // 设备级入口展示（2026-11-21）；LEGACY = 旧后端未投影，电视保持历史组合。
                "sourceEntry" to (remote.sourceEntryVisible?.toString() ?: "LEGACY"),
                "quizEntry" to (remote.quizEntryVisible?.toString() ?: "LEGACY"),
            )
        }
        if (enteringPartition) {
            // A session running under the TV's own rules must not migrate into the cloud
            // partition; settle it into the local ledger before the executor switches.
            LocalPolicyRuntime.endSessionForSwitch()
            if (!LocalPolicyRuntime.enterHostedMode(incomingSets, remote.configVersion, activeMemberId)) {
                PolicyFlowTrace.event(
                    "remote.rules.reject",
                    "stage" to "enterHosted",
                    "version" to remote.configVersion,
                    "usable" to incomingSets.isUsable,
                )
                return false
            }
        } else if (rulesChanged) {
            if (!LocalPolicyRuntime.setRuleSetsWithVersion(incomingSets, remote.configVersion)) {
                PolicyFlowTrace.event(
                    "remote.rules.reject",
                    "stage" to "setRuleSets",
                    "version" to remote.configVersion,
                    "usable" to incomingSets.isUsable,
                )
                return false
            }
        }
        _state.value = currentEngineState()
        // A rule edit changes the lock screen's detail rows; wake that idle panel.
        if (rulesChanged || enteringPartition) presentRemoteChangeOnPanel()
        appliedRemoteMode = RemotePolicyMode.RULE
        appliedRemoteVersion = remote.configVersion
        RuleUsageReporter.start(PolicyRuntime.state.value.deviceNo)
        return true
    }

    /**
     * Leaves the rule guard. A running session is committed as MODE_SWITCH before the engine
     * stops so its END reaches the outbox while the sink is still attached.
     */
    @Synchronized
    private fun stopRuleGuard() {
        if (!ruleGuardActive) {
            PolicyRuntime.setHostedRuleActive(false)
            RuleUsageReporter.stop()
            return
        }
        LocalPolicyRuntime.endSessionForSwitch()
        LocalPolicyRuntime.deactivate()
        LocalPolicyRuntime.setSource(PolicySource.NONE)
        RuleUsageReporter.stop()
        ruleGuardActive = false
        // Hand the guard back to the redemption engine (REDEEM or unbound); its next config
        // poll is authoritative for the legacy PENDING gate.
        PolicyRuntime.setHostedRuleActive(false)
    }

    private fun publishCurrent() {
        _state.value = currentEngineState()
        _revision.value = _revision.value + 1L
    }

    /** One line per visible guard transition; countdown ticks are bucketed to whole minutes. */
    private fun traceFlowState(state: PolicyUiState) {
        if (!PolicyFlowTrace.enabled()) return
        val activeMinute = if (state.sessionState == SessionState.ACTIVE) {
            state.remainingSeconds / 60L
        } else {
            0L
        }
        val key = listOf(
            state.mode,
            state.source,
            state.activation,
            state.sessionState,
            state.guardState,
            state.lockReason,
            state.remoteMode,
            state.bound,
            state.noTimedApps,
            state.windowRemainingSeconds > 0L,
            state.userName.isNotBlank(),
            state.userIconUrl.isNotBlank(),
            state.studyModeAvailable,
            state.studyModeActive,
            activeMinute,
        ).joinToString("|")
        if (key == lastFlowStateTrace) return
        lastFlowStateTrace = key
        PolicyFlowTrace.event(
            "state",
            "mode" to state.mode,
            "source" to state.source,
            "activation" to state.activation,
            "session" to state.sessionState,
            "guard" to state.guardState,
            "lock" to state.lockReason,
            "remoteMode" to state.remoteMode,
            "bound" to state.bound,
            "windowOpen" to (state.windowRemainingSeconds > 0L),
            "windowRemain" to state.windowRemainingSeconds,
            "remaining" to state.remainingSeconds,
            "effective" to state.effectiveRemainingSeconds,
            "rest" to state.restRemainingSeconds,
            "parentUnlock" to state.parentUnlockRemainingSeconds,
            "user" to state.userName.isNotBlank(),
            "icon" to state.userIconUrl.isNotBlank(),
            "studyAvailable" to state.studyModeAvailable,
            "studyActive" to state.studyModeActive,
            "noTimedApps" to state.noTimedApps,
        )
    }

    fun isCloud(): Boolean = mode == PolicyMode.CLOUD

    fun isNone(): Boolean = mode == PolicyMode.NONE

    /**
     * Whether a policy may exist even though [state] has not finished loading. The guard's
     * revival gate must not read the initial `mode = NONE, loaded = false` as "no policy":
     * that would let an automatic revival silence the lock screen. On a fresh install with a
     * ready store and no stored mode this is false, so the gate keeps its ordinary behaviour.
     */
    fun policyMayExist(): Boolean {
        if (mode != PolicyMode.NONE) return true
        val stored = PolicyModeStore.load()
        if (stored != null) return stored != PolicyMode.NONE
        // Credential storage not open, or an old CLOUD install whose mode key was never written.
        return !TvStore.isReady || PolicyRuntime.hasStoredCloudIdentity()
    }

    fun isLockScreen(): Boolean = when (mode) {
        PolicyMode.CLOUD -> if (ruleGuardActive) {
            LocalPolicyRuntime.isLockScreen() || LocalPolicyRuntime.isSetupRequired
        } else {
            PolicyRuntime.isLockScreen()
        }
        PolicyMode.LOCAL -> LocalPolicyRuntime.isLockScreen() || LocalPolicyRuntime.isSetupRequired
        PolicyMode.NONE -> false
    }

    /**
     * True while an APPS rule scope is actually filtering. LOCAL and the cloud rule-guard facade
     * share the local engine, so both must be covered; redemption keeps its own cloud policy.
     */
    fun isEnabled(): Boolean = when (mode) {
        PolicyMode.CLOUD -> if (ruleGuardActive) localRuleScopeActive() else PolicyRuntime.isEnabled()
        PolicyMode.LOCAL -> localRuleScopeActive()
        PolicyMode.NONE -> false
    }

    fun shouldBlock(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        return when (mode) {
            PolicyMode.CLOUD -> if (ruleGuardActive) {
                localRuleScopeActive() && packageName !in LocalPolicyRuntime.state.value.allowedPackages
            } else {
                PolicyRuntime.shouldBlock(context, packageName)
            }
            PolicyMode.LOCAL ->
                localRuleScopeActive() && packageName !in LocalPolicyRuntime.state.value.allowedPackages
            PolicyMode.NONE -> false
        }
    }

    /** Desktop presentation filter; APPS specific hides sources/inputs. */
    fun allowsHomePackage(packageName: String): Boolean = when (mode) {
        PolicyMode.CLOUD -> if (ruleGuardActive) {
            allowsRulePackage(packageName)
        } else {
            PolicyRuntime.allowsPackage(packageName)
        }
        PolicyMode.LOCAL -> allowsRulePackage(packageName)
        PolicyMode.NONE -> true
    }

    /** Screen-level and all_valid allow every source; a specific list hides pinned inputs. */
    fun allowsSource(sourceId: String): Boolean = when (mode) {
        PolicyMode.CLOUD -> if (ruleGuardActive) {
            !localRuleScopeActive()
        } else {
            PolicyRuntime.allowsSource(sourceId)
        }
        PolicyMode.LOCAL -> !localRuleScopeActive()
        PolicyMode.NONE -> true
    }

    /**
     * The parent-center source-entry switch. This is a presentation gate: while child mode is on
     * and the switch is off, Aerial hides every source entry it owns, but the firmware's own
     * Source key and CEC remain outside it. Turning child mode off restores the entries.
     *
     * A local rule set deliberately overrides the old "inputs only when all apps are allowed"
     * rule: with the switch on, the parent chose to expose sources during an active session.
     */
    fun sourceEntriesAllowed(): Boolean = sourceEntryGate("")

    fun allowsSourceEntry(sourceId: String): Boolean = sourceEntryGate(sourceId)

    private fun sourceEntryGate(sourceId: String): Boolean = when (mode) {
        // The switch lives in the child-mode rules; with child mode off there is nothing to hide.
        PolicyMode.NONE -> true
        PolicyMode.CLOUD -> {
            val rulesAllow = if (ruleGuardActive) {
                localSourceEntryAllowed()
            } else {
                PolicyRuntime.allowsSource(sourceId)
            }
            // 设备级「入口展示 → 信号源」（2026-11-21）：云端开关与电视本机开关取交集，
            // 任一关闭即隐藏。null = 旧后端未投影该字段，保持本机开关 × 规则闸门的历史组合。
            SourceEntrySettings.isVisible() && cloudSourceEntryVisible() && rulesAllow
        }
        PolicyMode.LOCAL -> SourceEntrySettings.isVisible() && localSourceEntryAllowed()
    }

    /** 云端入口展示（信号源）；null（旧后端）按显示处理，显式 false 才隐藏。 */
    private fun cloudSourceEntryVisible(): Boolean =
        PolicyRuntime.remotePolicy.value?.sourceEntryVisible != false

    private fun localSourceEntryAllowed(): Boolean {
        val local = LocalPolicyRuntime.state.value
        if (!local.loaded) return true
        return when {
            local.guardState == GuardStatus.PARENT_OVERRIDE -> true
            local.guardState == GuardStatus.STUDY_MODE -> false
            // The switch is the parent's whole decision. The lock screen does not draw Home
            // behind it, so allowing entries while the child is not in a session only affects
            // the parent panel that the lock screen offers from its top-right pill.
            local.guardState == GuardStatus.ENFORCING -> true
            else -> false
        }
    }

    /** Stable fingerprint of what the desktop should currently show, used by the readiness gate. */
    fun accessFingerprint(state: PolicyUiState = _state.value): String {
        if (state.mode == PolicyMode.NONE) return "ALL"
        if (state.guardState == GuardStatus.PARENT_OVERRIDE) return "ALL"
        // Study mode has no session but still replaces the visible app scope.
        if (state.sessionState != SessionState.ACTIVE &&
            state.guardState != GuardStatus.STUDY_MODE
        ) {
            return "ALL"
        }
        if (state.accessMode != AccessMode.APPS) return "ALL"
        // Empty is a distinct fingerprint ("APPS:"), not "ALL": a config change from a list to
        // the explicit empty scope must still reload the desktop.
        return "APPS:" + state.allowedPackages.sorted().joinToString(",")
    }

    private fun localRuleScopeActive(): Boolean {
        val local = LocalPolicyRuntime.state.value
        // An explicit empty APPS scope is a real scope: it blocks every other app. Treating it
        // as "inactive" would silently widen zero entertainment / empty study lists to all.
        // Study mode is a scope too, with the study list as its allowlist.
        return local.loaded &&
            local.accessMode == AccessMode.APPS &&
            (
                (local.sessionState == SessionState.ACTIVE &&
                    local.guardState == GuardStatus.ENFORCING) ||
                    local.guardState == GuardStatus.STUDY_MODE
                )
    }

    private fun allowsRulePackage(packageName: String): Boolean =
        !localRuleScopeActive() || packageName in LocalPolicyRuntime.state.value.allowedPackages

    fun isActive(): Boolean = state.value.sessionState == SessionState.ACTIVE

    /** True while the bound hosted rule mirror owns execution; remote commands target it. */
    fun isHostedRuleActive(): Boolean = mode == PolicyMode.CLOUD && ruleGuardActive

    /**
     * Hosted rule guard with its execution partition loaded. The child unlock routes
     * (direct/quiz/study) run on the same local planner as LOCAL mode; only this combination
     * may use them, never the legacy CLOUD redemption engine.
     */
    private fun hostedRuleGuardActive(): Boolean =
        mode == PolicyMode.CLOUD && ruleGuardActive &&
            LocalPolicyRuntime.state.value.source == PolicySource.HOSTED

    fun isTemporaryUnlocked(): Boolean = state.value.guardState == GuardStatus.PARENT_OVERRIDE

    /** Used by the parent center only for the "default PIN, please change" hint. */
    fun isDefaultPin(): Boolean = when (mode) {
        PolicyMode.LOCAL -> PolicyRuntime.isDefaultPin(local = true)
        PolicyMode.CLOUD -> PolicyRuntime.isDefaultPin()
        PolicyMode.NONE -> false
    }

    /**
     * Whether enabling LOCAL mode must first ask the parent to choose a PIN. This is checked
     * while the switch is still off, so it reads the LOCAL store directly instead of [state]
     * (NONE reports pinConfigured=false by design).
     */
    fun needsLocalPinSetup(): Boolean = PolicyRuntime.needsLocalPinSetup()

    /** True for the built-in PINs the first-run dialog must reject. */
    fun isDefaultPinValue(pin: String): Boolean = PolicyRuntime.isDefaultPinValue(pin)

    private fun screenInteractive(): Boolean =
        (context?.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true

    /**
     * The lock surface is a Compose screen inside Aerial, not a system window, so the guard
     * state says nothing about what is in front. The parent center shares the same resumed
     * window, which is fine: it is only reachable through the parent PIN.
     */
    private fun lockSurfaceInFront(): Boolean = HomeGuardService.isAerialResumed()

    /**
     * First-run LOCAL enable: saves the chosen PIN together with the current/default rules and
     * a recovery code, then shares the PIN proof so turning the guard off does not ask again.
     * Returns the recovery code for the caller to show, or null when nothing was written.
     */
    @Synchronized
    fun completeLocalSetup(pin: String, recoveryCode: String? = null): String? {
        if (!PolicyRuntime.needsLocalPinSetup()) return null
        if (PolicyRuntime.isDefaultPinValue(pin)) return null
        val rules = LocalPolicyRuntime.currentRules?.takeIf { it.isUsable }
            ?: LocalRules.DEFAULTS
        val code = LocalPolicyRuntime.completeSetup(pin, rules, recoveryCode) ?: return null
        // completeSetup writes through TvPinStore, which also updates the cloud copy; refresh
        // its cached pinConfigured so a later mode switch sees the same logical PIN.
        PolicyRuntime.reloadPinState()
        // A storage failure can return a code even though no PIN landed; do not enable the
        // guard on a PIN the parent cannot prove.
        if (!LocalPolicyRuntime.state.value.pinConfigured) return null
        parentAuth.onParentVerified(pin)
        return code
    }

    fun submitPin(pin: String): PinResult {
        val result = when (mode) {
            PolicyMode.CLOUD -> if (ruleGuardActive) {
                LocalPolicyRuntime.submitPin(pin)
            } else {
                PolicyRuntime.submitPin(pin)
            }
            PolicyMode.LOCAL -> LocalPolicyRuntime.submitPin(pin)
            PolicyMode.NONE -> PinResult.NOT_SET
        }
        // This is the lock screen's temporary-unlock path. Share that proof with the parent
        // center until the guard resumes (screen-off, remote lock, mode switch or restart).
        parentAuth.onLockScreenPin(pin, result)
        return result
    }

    fun verifyPin(pin: String): PinResult = when (mode) {
        PolicyMode.CLOUD -> if (ruleGuardActive) {
            LocalPolicyRuntime.verifyPin(pin)
        } else {
            PolicyRuntime.verifyPin(pin)
        }
        PolicyMode.LOCAL -> LocalPolicyRuntime.verifyPin(pin)
        PolicyMode.NONE -> PinResult.NOT_SET
    }

    /**
     * Lock state for the local parent page. The page can only mirror the lock-screen PIN, so
     * this uses the same surface rule as the TV: local mode, default guard, no running session,
     * no study mode and a visible screen. The guard state alone does not prove the lock surface
     * is on screen, so Aerial must also be the resumed window; another app or the stock
     * launcher in front is not remotely unlocked. An active quiz round is rejected even though
     * the guard is idle, because the page must never interrupt it.
     */
    @Synchronized
    fun webUnlockStatus(): WebUnlockStatus {
        if (mode != PolicyMode.LOCAL) {
            return WebUnlockStatus(false, WebUnlockReason.NOT_LOCAL, false)
        }
        val ui = state.value
        if (ui.guardState == GuardStatus.PARENT_OVERRIDE) {
            return WebUnlockStatus(false, WebUnlockReason.ALREADY_UNLOCKED, true)
        }
        if (!ui.loaded) return WebUnlockStatus(false, WebUnlockReason.NOT_READY, false)
        if (ui.setupRequired) {
            return WebUnlockStatus(false, WebUnlockReason.SETUP_REQUIRED, false)
        }
        if (!ui.pinConfigured) {
            return WebUnlockStatus(false, WebUnlockReason.PIN_NOT_SET, false)
        }
        if (ui.guardState == GuardStatus.STUDY_MODE) {
            return WebUnlockStatus(false, WebUnlockReason.STUDY_MODE, false)
        }
        if (ui.guardState != GuardStatus.ENFORCING) {
            return WebUnlockStatus(false, WebUnlockReason.NOT_READY, false)
        }
        if (ui.sessionState == SessionState.ACTIVE) {
            return WebUnlockStatus(false, WebUnlockReason.SESSION_ACTIVE, false)
        }
        if (!screenInteractive()) {
            return WebUnlockStatus(false, WebUnlockReason.SCREEN_OFF, false)
        }
        if (!lockSurfaceInFront()) {
            return WebUnlockStatus(false, WebUnlockReason.NOT_ON_LOCK_SCREEN, false)
        }
        if (QuizRuntime.isRoundActive()) {
            return WebUnlockStatus(false, WebUnlockReason.QUIZ_ACTIVE, false)
        }
        if (isDefaultPin()) {
            return WebUnlockStatus(false, WebUnlockReason.DEFAULT_PIN, false)
        }
        if (!LocalPolicyRuntime.canBeginParentOverrideFromLockScreen()) {
            return WebUnlockStatus(false, WebUnlockReason.STATE_CHANGED, false)
        }
        return WebUnlockStatus(true, WebUnlockReason.OK, false)
    }

    /**
     * The page's temporary-unlock action. It intentionally does not call [submitPin]: the
     * runtime's lock-screen-only path refuses running sessions and study mode, so the web
     * page cannot end a timer that the TV itself would not end from the lock screen. The
     * proof is shared only after the atomic transition succeeds.
     */
    @Synchronized
    fun unlockFromWeb(pin: String): WebUnlockResult {
        if (!LocalPin.isValid(pin)) return WebUnlockResult.PinInvalid(locked = false)
        val before = webUnlockStatus()
        if (!before.eligible) return WebUnlockResult.Rejected(before.reason)
        when (verifyPin(pin)) {
            PinResult.OK -> Unit
            PinResult.LOCKED_OUT -> return WebUnlockResult.PinInvalid(locked = true)
            PinResult.WRONG, PinResult.NOT_SET -> return WebUnlockResult.PinInvalid(locked = false)
        }
        // Defense in depth: even if the stored PIN were changed to a built-in value, the web
        // page must not turn it into a remote unlock.
        if (isDefaultPinValue(pin)) {
            return WebUnlockResult.Rejected(WebUnlockReason.DEFAULT_PIN)
        }
        // Re-check after the (possibly slow) credential check: a session or quiz may have
        // started, or the screen may have gone dark, while the PIN was being verified.
        val after = webUnlockStatus()
        if (!after.eligible) return WebUnlockResult.Rejected(after.reason)
        if (!LocalPolicyRuntime.tryBeginParentOverrideFromLockScreen()) {
            return WebUnlockResult.Rejected(WebUnlockReason.STATE_CHANGED)
        }
        parentAuth.onLockScreenPin(pin, PinResult.OK)
        return WebUnlockResult.Ok
    }

    /**
     * Lock state for the page's single-use action. V45 makes this a parent authorization that
     * runs outside the child plan: rest, a closed window, an exhausted daily quota and an empty
     * timed plan do not block it. The TV must still be in local child mode, showing the lock
     * surface, with no session, study round, study mode or quiz in progress, and the manual app
     * scope must contain at least one timed app. The page only draws the button from this
     * snapshot; [startManualFromWeb] repeats every check after the PIN and the runtime repeats
     * the policy checks atomically.
     */
    @Synchronized
    fun webManualStartStatus(): WebManualStartStatus {
        if (mode != PolicyMode.LOCAL) {
            return WebManualStartStatus(false, WebManualStartReason.NOT_LOCAL)
        }
        val ui = state.value
        if (ui.guardState == GuardStatus.PARENT_OVERRIDE) {
            return WebManualStartStatus(false, WebManualStartReason.ALREADY_UNLOCKED)
        }
        if (!ui.loaded) return WebManualStartStatus(false, WebManualStartReason.NOT_READY)
        if (ui.setupRequired) {
            return WebManualStartStatus(false, WebManualStartReason.SETUP_REQUIRED)
        }
        if (!ui.pinConfigured) {
            return WebManualStartStatus(false, WebManualStartReason.PIN_NOT_SET)
        }
        if (ui.guardState == GuardStatus.STUDY_MODE) {
            return WebManualStartStatus(false, WebManualStartReason.STUDY_MODE)
        }
        if (ui.guardState != GuardStatus.ENFORCING) {
            return WebManualStartStatus(false, WebManualStartReason.NOT_READY)
        }
        if (ui.sessionState == SessionState.ACTIVE) {
            // The manual-start payload is only allowed to carry progress for an out-of-plan
            // timer. Keeping a plan session's countdown out of it also protects older pages.
            val outsidePlan = ui.activeSessionOutsidePlan
            return WebManualStartStatus(
                eligible = false,
                reason = WebManualStartReason.SESSION_ACTIVE,
                remainingSeconds = if (outsidePlan) ui.effectiveRemainingSeconds else 0L,
                outsidePlan = outsidePlan,
            )
        }
        if (ui.sessionState == SessionState.PENDING) {
            return WebManualStartStatus(false, WebManualStartReason.SESSION_PENDING)
        }
        // A focus round occupies the same single-session slot but keeps the guard ENFORCING.
        if (ui.studyRound != null) {
            return WebManualStartStatus(false, WebManualStartReason.SESSION_ACTIVE)
        }
        // V45: the manual timer is a deliberate out-of-plan authorization. Rest, a closed
        // window, an exhausted daily quota and an empty timed plan must not block it. Only the
        // manual app scope and the live surface checks remain; the runtime repeats them.
        val manualScope = manualTimerScope()
            ?: return WebManualStartStatus(false, WebManualStartReason.NOT_READY)
        if (!manualScope.hasTimedApps) {
            return WebManualStartStatus(
                eligible = false,
                reason = if (manualScope.hasApps) {
                    WebManualStartReason.ONLY_UNLIMITED
                } else {
                    WebManualStartReason.NO_TIMED_APPS
                },
            )
        }
        if (!screenInteractive()) {
            return WebManualStartStatus(false, WebManualStartReason.SCREEN_OFF)
        }
        if (!lockSurfaceInFront()) {
            return WebManualStartStatus(false, WebManualStartReason.NOT_ON_LOCK_SCREEN)
        }
        if (QuizRuntime.isRoundActive()) {
            return WebManualStartStatus(false, WebManualStartReason.QUIZ_ACTIVE)
        }
        if (isDefaultPin()) {
            return WebManualStartStatus(false, WebManualStartReason.DEFAULT_PIN)
        }
        return WebManualStartStatus(true, WebManualStartReason.OK)
    }

    /**
     * App counts for the parent timer sheet: configured entertainment, unlimited, and — only
     * while the study-reward feature is on — every selected study app. Null when no usable
     * local rules exist yet.
     */
    fun manualTimerScope(): ManualTimerScope? = LocalPolicyRuntime.manualTimerScope()

    /**
     * The page's single-use action. By default it requires the parent PIN every time; the
     * remembered admin token only authenticates the page, never the start. The PIN is checked
     * before and the whole state is checked again afterwards, so a session, rest, mode switch
     * or quiz that begins during the (slow) credential check cannot be bypassed. The final
     * start still goes through the same local manual path as the lock screen.
     *
     * [requirePin] is false only when the parent deliberately turned off the web
     * user-configuration PIN prompts. The page-login/editor checks and every state check stay
     * in place; the quiz page's own start route keeps the default true.
     */
    @Synchronized
    fun startManualFromWeb(
        pin: String,
        minutes: Int,
        requirePin: Boolean = true,
    ): WebManualStartResult {
        if (!ManualSessionLimits.isValid(minutes)) {
            return WebManualStartResult.InvalidDuration
        }
        if (requirePin && !LocalPin.isValid(pin)) {
            return WebManualStartResult.PinInvalid(locked = false)
        }
        val before = webManualStartStatus()
        if (!before.eligible) return WebManualStartResult.Rejected(before.reason)
        if (requirePin) {
            when (verifyPin(pin)) {
                PinResult.OK -> Unit
                PinResult.LOCKED_OUT -> return WebManualStartResult.PinInvalid(locked = true)
                PinResult.WRONG, PinResult.NOT_SET ->
                    return WebManualStartResult.PinInvalid(locked = false)
            }
            if (isDefaultPinValue(pin)) {
                return WebManualStartResult.Rejected(WebManualStartReason.DEFAULT_PIN)
            }
        }
        val after = webManualStartStatus()
        if (!after.eligible) return WebManualStartResult.Rejected(after.reason)
        // Close the last window before the runtime's atomic admission. A quiz may have started
        // while the PIN was being verified; the final check is still repeated by the caller's
        // status refresh if the admission itself loses a race.
        if (QuizRuntime.isRoundActive()) {
            return WebManualStartResult.Rejected(WebManualStartReason.QUIZ_ACTIVE)
        }
        return when (val started = startLocalManualSession(
            durationSeconds = minutes * 60L,
            allowHostedRule = true,
            outsidePlan = true,
        )) {
            SessionStartResult.Started -> {
                publishCurrent()
                WebManualStartResult.Ok
            }
            is SessionStartResult.Rejected -> {
                publishCurrent()
                val current = webManualStartStatus()
                WebManualStartResult.Rejected(
                    if (current.eligible) webManualReasonFor(started.reason) else current.reason,
                )
            }
            SessionStartResult.Pending -> {
                publishCurrent()
                WebManualStartResult.Rejected(WebManualStartReason.STATE_CHANGED)
            }
        }
    }

    private fun webManualReasonFor(reason: RejectReason): WebManualStartReason = when (reason) {
        RejectReason.GUARD_DISABLED -> WebManualStartReason.NOT_READY
        RejectReason.HOSTED_MANUAL_REMOTE_ONLY -> WebManualStartReason.NOT_LOCAL
        RejectReason.STALE_EPOCH, RejectReason.STALE_VERSION,
        RejectReason.EXPIRED, RejectReason.INVALID_COMMAND,
        RejectReason.INVALID_SCOPE, RejectReason.ROUTE_LIMIT ->
            WebManualStartReason.STATE_CHANGED
        RejectReason.PARENT_OVERRIDE -> WebManualStartReason.ALREADY_UNLOCKED
        RejectReason.BUSY -> WebManualStartReason.SESSION_ACTIVE
        RejectReason.RESTING -> WebManualStartReason.RESTING
        RejectReason.SCREEN_OFF -> WebManualStartReason.SCREEN_OFF
        RejectReason.OUTSIDE_WINDOW -> WebManualStartReason.OUTSIDE_WINDOW
        RejectReason.DAILY_LIMIT -> WebManualStartReason.DAILY_LIMIT
        RejectReason.NO_TIMED_APPS -> WebManualStartReason.NO_TIMED_APPS
    }

    /**
     * Parent-center password change. Both engines write both PIN stores through TvPinStore, so
     * the next mode switch keeps the same password. The caller is responsible for pushing the
     * new value to the cloud (or marking it pending when offline).
     */
    fun changePin(oldPin: String, newPin: String): Boolean {
        val changed = when (mode) {
            PolicyMode.CLOUD -> if (ruleGuardActive) {
                LocalPolicyRuntime.changePin(oldPin, newPin).also {
                    if (it) PolicyRuntime.reloadPinState()
                }
            } else {
                PolicyRuntime.changePin(oldPin, newPin)
            }
            PolicyMode.LOCAL -> LocalPolicyRuntime.changePin(oldPin, newPin).also {
                if (it) PolicyRuntime.reloadPinState()
            }
            PolicyMode.NONE -> false
        }
        if (changed) parentAuth.onParentVerified(newPin)
        return changed
    }

    /** Remote temporary unlock is only meaningful while the cloud engine owns the guard. */
    fun setTemporaryUnlock(enabled: Boolean, durationMs: Long = 0L): Boolean {
        if (mode != PolicyMode.CLOUD) return false
        if (!enabled) parentAuth.clear()
        if (ruleGuardActive) {
            LocalPolicyRuntime.setTemporaryUnlock(enabled, durationMs)
        } else {
            PolicyRuntime.setTemporaryUnlock(enabled)
        }
        return true
    }

    /**
     * The lock screen's PENDING button. In hosted mode it confirms the locally armed remote
     * grant (the MQTT/REST paths only arm); in redemption mode it starts the legacy record.
     */
    fun startSession(): SessionStartResult = when {
        mode == PolicyMode.CLOUD && ruleGuardActive ->
            LocalPolicyRuntime.confirmRemoteManualSession()
        mode == PolicyMode.CLOUD -> {
            PolicyRuntime.startSession()
            if (PolicyRuntime.isActive()) {
                SessionStartResult.Started
            } else {
                SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
            }
        }
        else -> SessionStartResult.Rejected(RejectReason.GUARD_DISABLED)
    }

    /**
     * Focus-study entry. [targetPackage] is the entertainment app the child tapped in the
     * unlock-choice route, or null for the direct study tile. It never starts an entertainment
     * session; the runtime starts the reward session only after the round ends.
     *
     * 2026-11-21: hosted rule guard runs the same route as LOCAL mode because the cloud
     * projection carries the parent's complete member configuration. The legacy CLOUD
     * redemption engine still rejects it.
     */
    fun startStudyRound(
        studyPackage: String,
        targetPackage: String?,
        fromStudyMode: Boolean = false,
    ): SessionStartResult {
        if (mode != PolicyMode.LOCAL && !hostedRuleGuardActive()) {
            PolicyFlowTrace.event(
                "study.round.reject",
                "reason" to RejectReason.INVALID_COMMAND,
                "mode" to mode,
                "source" to LocalPolicyRuntime.state.value.source,
                "ruleGuard" to ruleGuardActive,
            )
            return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        }
        val result = LocalPolicyRuntime.startStudyRound(
            studyPackage = studyPackage,
            targetPackage = targetPackage,
            fromStudyMode = fromStudyMode,
        )
        PolicyFlowTrace.event(
            "study.round.entry",
            "study" to studyPackage,
            "target" to (targetPackage ?: "-"),
            "fromStudyMode" to fromStudyMode,
            "result" to result,
        )
        publishCurrent()
        return result
    }

    /**
     * A study launcher tile: the admission starts a timed round and leaves the launcher only
     * when it succeeds. MainActivity calls this before opening the app, so a closed study
     * window or an exhausted study quota cannot open an untimed app.
     */
    fun startStudyFromStudyMode(studyPackage: String): SessionStartResult =
        startStudyRound(studyPackage, targetPackage = null, fromStudyMode = true)

    /** Foreground reporting from the Home guard, used to pause/resume around unlimited apps. */
    fun onForegroundChanged(packageName: String) {
        if (packageName.isBlank()) return
        when (mode) {
            PolicyMode.LOCAL -> LocalPolicyRuntime.onForegroundChanged(packageName)
            PolicyMode.CLOUD -> if (ruleGuardActive) {
                LocalPolicyRuntime.onForegroundChanged(packageName)
            }
            PolicyMode.NONE -> Unit
        }
    }

    /**
     * Freezes a running focus round. Aerial coming back to the front, screen off and a launch the
     * study app never covered all call this; only 继续学习 resumes it.
     */
    fun pauseStudyRound(reason: String): Boolean {
        if (mode != PolicyMode.LOCAL && !hostedRuleGuardActive()) return false
        val paused = LocalPolicyRuntime.pauseStudyRound(reason)
        if (paused) {
            PolicyFlowTrace.event("study.round.pause.entry", "reason" to reason, "mode" to mode)
            publishCurrent()
        }
        return paused
    }

    /** Child-confirmed resume from the focus desktop. Re-checks the window and quota. */
    fun resumeStudyRound(): SessionStartResult {
        if (mode != PolicyMode.LOCAL && !hostedRuleGuardActive()) {
            PolicyFlowTrace.event(
                "study.round.resume.reject",
                "reason" to RejectReason.INVALID_COMMAND,
                "mode" to mode,
                "source" to LocalPolicyRuntime.state.value.source,
            )
            return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        }
        val result = LocalPolicyRuntime.resumeStudyRound()
        PolicyFlowTrace.event("study.round.resume.entry", "result" to result)
        publishCurrent()
        return result
    }

    /** Leaves a focus round without a reward. Idempotent. */
    fun cancelStudyRound(reason: String, bringHome: Boolean = false): Boolean {
        if (mode != PolicyMode.LOCAL && !hostedRuleGuardActive()) return false
        val canceled = LocalPolicyRuntime.cancelStudyRound(reason, bringHome)
        if (canceled) {
            PolicyFlowTrace.event(
                "study.round.cancel.entry",
                "reason" to reason,
                "bringHome" to bringHome,
            )
            publishCurrent()
        }
        return canceled
    }

    /**
     * Parent-confirmed local manual start; the caller already verified the parent PIN.
     * [allowHostedRule] is set by the lock-screen picker and the local parent web page: the
     * V10 hosted rule guard accepts a local start in manual mode, while legacy boundaries
     * (REDEEM, migration and quiz rewards) keep the old local-only behaviour.
     *
     * [outsidePlan] is V45's parent Web timer: a plan-exempt authorization that uses the manual
     * app scope and never writes the daily ledger, route counts or rest. The TV lock-screen
     * picker keeps the in-plan default.
     */
    fun startLocalManualSession(
        durationSeconds: Long,
        expiresAtEpochMs: Long? = null,
        allowHostedRule: Boolean = false,
        outsidePlan: Boolean = false,
    ): SessionStartResult = when {
        mode == PolicyMode.LOCAL ->
            LocalPolicyRuntime.startManualSession(durationSeconds, expiresAtEpochMs, outsidePlan)
        mode == PolicyMode.CLOUD && allowHostedRule && ruleGuardActive &&
            LocalPolicyRuntime.state.value.source == PolicySource.HOSTED ->
            LocalPolicyRuntime.startManualSession(durationSeconds, expiresAtEpochMs, outsidePlan)
        else -> SessionStartResult.Rejected(RejectReason.HOSTED_MANUAL_REMOTE_ONLY)
    }

    /**
     * Garden "single session" (2026-11-21). The orchard page replaces the TV's own parent Web
     * timer, so this aligns the cloud active member with the selected child and then calls
     * [startLocalManualSession] — the exact runtime entry the TV picker and the parent Web page
     * use. There is no second execution chain: the cloud only supplies the member and duration.
     *
     * <p>Runs off the MQTT callback thread (the caller launches a coroutine) because the member
     * switch needs HTTP and a config fetch.
     */
    suspend fun startRemoteSingleSession(
        durationMinutes: Int,
        memberId: Long,
    ): RemoteSingleSessionResult {
        if (durationMinutes < SINGLE_SESSION_MIN_MINUTES ||
            durationMinutes > SINGLE_SESSION_MAX_MINUTES
        ) {
            return remoteSingleSessionRejected("duration", "minutes" to durationMinutes)
        }
        if (memberId <= 0L) {
            return remoteSingleSessionRejected("member")
        }
        if (!hostedRuleGuardActive()) {
            PolicyFlowTrace.event(
                "remote.single.reject",
                "reason" to "notHosted",
                "minutes" to durationMinutes,
                "memberId" to memberId,
                "mode" to mode,
                "source" to LocalPolicyRuntime.state.value.source,
                "ruleGuard" to ruleGuardActive,
                "activation" to LocalPolicyRuntime.state.value.activation,
            )
            // The garden projection only reaches the TV in hosted rule mode; LOCAL mode has its
            // own identity model, so a member-scoped timer must not be guessed into it.
            return RemoteSingleSessionResult(
                false,
                "电视当前不是云端托管模式，请在电视上切换到云端模式后再试",
            )
        }
        val before = PolicyRuntime.state.value
        if (before.deviceNo.isBlank()) {
            return remoteSingleSessionRejected("unbound")
        }
        if (before.cloudActiveMemberId != memberId) {
            val switched = PolicyCloud.switchActiveMember(before.deviceNo, memberId)
            if (switched.isFailure) {
                val error = switched.exceptionOrNull()
                PolicyFlowTrace.event(
                    "remote.single.reject",
                    "reason" to "switchMember",
                    "memberId" to memberId,
                    "detail" to (error?.let { PolicyCloud.errorDetail(it) } ?: "-"),
                )
                val message = error?.let { PolicyCloud.businessMessage(it) }
                    ?: "切换电视当前用户失败，请稍后重试"
                return RemoteSingleSessionResult(false, message)
            }
            PolicyRuntime.requestSync()
        }
        // Also when the member already matches, the last projection may belong to a switch the
        // TV just made locally (cloudActiveMemberId updates before the rule fetch completes).
        // Starting with the previous child's scope would grant the wrong apps, so wait for the
        // rule set this snapshot will install; an applied snapshot passes immediately.
        val aligned = withTimeoutOrNull(SINGLE_SESSION_SYNC_TIMEOUT_MS) {
            while (PolicyRuntime.state.value.cloudActiveMemberId != memberId) {
                delay(SINGLE_SESSION_SYNC_POLL_MS)
                // A switch that landed while a fetch was in flight needs one more pull.
                PolicyRuntime.requestSync()
            }
            // onCloudMembers is published before onRemotePolicy; wait for the projected rule
            // set too, so the timer starts with this child's app scope, not the previous one.
            var expected = expectedRemoteRuleSets()
            while (expected != null && LocalPolicyRuntime.currentRuleSets != expected) {
                delay(SINGLE_SESSION_SYNC_POLL_MS)
                PolicyRuntime.requestSync()
                expected = expectedRemoteRuleSets()
            }
            true
        } ?: false
        if (!aligned) {
            PolicyFlowTrace.event(
                "remote.single.reject",
                "reason" to "syncTimeout",
                "memberId" to memberId,
                "active" to PolicyRuntime.state.value.cloudActiveMemberId,
            )
            return RemoteSingleSessionResult(false, "电视还没同步到这个用户的配置，请稍后重试")
        }
        val seconds = durationMinutes.toLong() * 60L
        // outsidePlan = the same plan-exempt parent timer as the TV picker / local Web page.
        val result = startLocalManualSession(
            durationSeconds = seconds,
            allowHostedRule = true,
            outsidePlan = true,
        )
        val ok = result !is SessionStartResult.Rejected
        PolicyFlowTrace.event(
            "remote.single.result",
            "minutes" to durationMinutes,
            "memberId" to memberId,
            "result" to result,
            "ok" to ok,
        )
        if (ok) publishCurrent()
        return when (result) {
            SessionStartResult.Started ->
                RemoteSingleSessionResult(true, "已在电视上开始 $durationMinutes 分钟计时")
            SessionStartResult.Pending ->
                RemoteSingleSessionResult(true, "已发送到电视，请在电视上确认开始")
            is SessionStartResult.Rejected ->
                RemoteSingleSessionResult(false, remoteSingleSessionRejectMessage(result.reason))
        }
    }

    /** The rule set the remote collector will install for the current snapshot. */
    private fun expectedRemoteRuleSets(): LocalRuleSets? {
        val remote = PolicyRuntime.remotePolicy.value ?: return null
        return (remote.ruleSets ?: LocalRuleSets.from(remote.rules))
            .preservingDeviceFieldsFrom(
                LocalPolicyRuntime.currentRuleSets,
                remote.memberScopedFields,
            )
    }

    private fun remoteSingleSessionRejected(
        reason: String,
        vararg extras: Pair<String, Any?>,
    ): RemoteSingleSessionResult {
        PolicyFlowTrace.event("remote.single.reject", "reason" to reason, *extras)
        return RemoteSingleSessionResult(
            false,
            when (reason) {
                "duration" -> "单次计时需要在 1-180 分钟之间"
                "member" -> "请先选择要计时的用户"
                "unbound" -> "电视还没有绑定家庭，请先在果园里完成绑定"
                else -> "电视当前无法开始计时"
            },
        )
    }

    /** Human text for every admission result; the orchard page shows it without changes. */
    private fun remoteSingleSessionRejectMessage(reason: RejectReason): String = when (reason) {
        RejectReason.GUARD_DISABLED -> "电视的儿童模式当前没有运行，请先在电视上进入儿童模式"
        RejectReason.BUSY -> "电视正在使用中，等当前计时结束后再试"
        RejectReason.SCREEN_OFF -> "电视屏幕已关闭，请点亮电视后再试"
        RejectReason.PARENT_OVERRIDE -> "电视当前处于家长临时解锁状态，请结束后再试"
        RejectReason.RESTING -> "电视还在休息时间，暂时不能开始计时"
        RejectReason.OUTSIDE_WINDOW -> "当前不在可用时段，暂时不能开始计时"
        RejectReason.DAILY_LIMIT -> "今天的娱乐额度已经用完了"
        RejectReason.ROUTE_LIMIT -> "今天的解锁次数已经用完了"
        RejectReason.EXPIRED -> "这次计时请求已经过期，请重试"
        RejectReason.STALE_EPOCH, RejectReason.STALE_VERSION ->
            "电视上的配置刚刚更新过，请重试"
        RejectReason.NO_TIMED_APPS, RejectReason.INVALID_SCOPE ->
            "这台电视上没有可计时的娱乐应用，请先在配置里添加"
        RejectReason.HOSTED_MANUAL_REMOTE_ONLY -> "电视当前不允许远程计时"
        RejectReason.INVALID_COMMAND -> "电视当前配置不允许开始计时"
    }

    /**
     * Child-started direct session. The hosted rule guard runs the same route: the cloud
     * projection carries the parent's direct-start switch, watch duration and daily count.
     * The legacy CLOUD redemption engine keeps rejecting it.
     */
    fun startLocalDirectSession(): SessionStartResult {
        if (mode != PolicyMode.LOCAL && !hostedRuleGuardActive()) {
            PolicyFlowTrace.event(
                "session.direct.reject",
                "reason" to RejectReason.INVALID_COMMAND,
                "mode" to mode,
                "source" to LocalPolicyRuntime.state.value.source,
            )
            return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        }
        val result = LocalPolicyRuntime.startDirectSession()
        PolicyFlowTrace.event("session.direct.entry", "mode" to mode, "result" to result)
        if (result !is SessionStartResult.Rejected) publishCurrent()
        return result
    }

    /**
     * V44 child quiz route. The report shows [localQuizGrantSeconds]; this start repeats the
     * same planner calculation inside the runtime, so the count/cap/quota can never be bypassed
     * by the UI. Hosted rules use the same route; the legacy CLOUD redemption engine keeps the
     * historical manual path unchanged.
     */
    fun startLocalQuizSession(earnedSeconds: Long): SessionStartResult = when {
        mode == PolicyMode.LOCAL || hostedRuleGuardActive() -> {
            val result = LocalPolicyRuntime.startQuizRewardSession(earnedSeconds)
            PolicyFlowTrace.event(
                "session.quiz.entry",
                "earned" to earnedSeconds,
                "mode" to mode,
                "result" to result,
            )
            if (result !is SessionStartResult.Rejected) publishCurrent()
            result
        }
        else -> startLocalManualSession(earnedSeconds)
    }

    /** The exact quiz grant the report must display, in seconds; null outside rule modes. */
    fun localQuizGrantSeconds(earnedSeconds: Long): Long? = when {
        mode == PolicyMode.LOCAL || hostedRuleGuardActive() ->
            LocalPolicyRuntime.quizGrantSeconds(earnedSeconds)
        else -> null
    }

    /**
     * Hosted control-plane start. The caller (command admission) has already verified TV
     * presence, the signed envelope and the command TTL; the executor repeats every policy check
     * locally against the cached config and epoch.
     */
    fun startRemoteManualSession(
        durationSeconds: Long,
        expectedEpoch: Long,
        allowedPackages: Set<String>?,
        expiresAtEpochMs: Long? = null,
        recordId: Int = 0,
    ): SessionStartResult {
        if (mode != PolicyMode.CLOUD || !ruleGuardActive) {
            return SessionStartResult.Rejected(RejectReason.GUARD_DISABLED)
        }
        val wasPending = LocalPolicyRuntime.state.value.sessionState == SessionState.PENDING
        val result = LocalPolicyRuntime.startRemoteManualSession(
            durationSeconds = durationSeconds,
            expectedEpoch = expectedEpoch,
            allowedPackages = allowedPackages,
            expiresAtEpochMs = expiresAtEpochMs,
            recordId = recordId,
        )
        if (!wasPending && result is SessionStartResult.Pending) PolicyRuntime.bringHome()
        publishCurrent()
        return result
    }

    /** MQTT cancel_session while the hosted grant is still waiting for the child. */
    fun cancelRemoteSession(commandRecordId: Int): Boolean {
        if (mode != PolicyMode.CLOUD) return false
        if (!ruleGuardActive) return PolicyRuntime.onRemoteCancel(commandRecordId)
        val canceled = LocalPolicyRuntime.cancelRemotePending(commandRecordId)
        if (canceled) publishCurrent()
        return canceled
    }

    /**
     * Hosted SetActivation. Execution is manual-only now, so the command is accepted only when
     * it still matches the current config version and policy epoch. An AUTO command never
     * reaches here: MQTT parsing rejects it.
     */
    fun applyRemoteActivation(
        activation: Activation,
        expectedVersion: Long,
        expectedEpoch: Long,
    ): Boolean {
        if (mode != PolicyMode.CLOUD || !ruleGuardActive) return false
        val remote = PolicyRuntime.remotePolicy.value ?: return false
        if (expectedVersion != remote.configVersion) return false
        if (expectedEpoch != LocalPolicyRuntime.state.value.policyEpoch) return false
        PolicyFlowTrace.event(
            "remote.activation.apply",
            "via" to "command",
            "version" to expectedVersion,
            "activation" to activation,
        )
        return true
    }

    /** Hosted StopSession: either a running segment or a grant still waiting for the child. */
    fun stopRemoteSession(commandRecordId: Int = 0): Boolean {
        if (mode != PolicyMode.CLOUD || !ruleGuardActive) return false
        val stopped = LocalPolicyRuntime.stopRemoteSessionByParent(commandRecordId)
        if (stopped) publishCurrent()
        return stopped
    }

    /**
     * Child action from the TV control center. This is a rule-session action only; the old
     * redemption session keeps its parent-start/stop path.
     */
    fun endSessionByUser(): Boolean {
        val ruleSessionActive = mode == PolicyMode.LOCAL ||
            (mode == PolicyMode.CLOUD && ruleGuardActive)
        if (!ruleSessionActive || !LocalPolicyRuntime.isActive) return false
        val ended = LocalPolicyRuntime.endSessionByUser()
        if (ended) publishCurrent()
        return ended
    }

    /**
     * Web control: temporarily hand the TV to the parent from any child-mode subflow.
     *
     * This is intentionally not the lock-screen unlock path. It ends a running session and
     * skips that segment's rest, clears a quiz round without paying its reward, and leaves a
     * pending remote grant in place. Repeating it while already overridden is a no-op and must
     * not extend the override deadline. Returns false when there was nothing to change.
     */
    @Synchronized
    fun takeOverFromWeb(): Boolean {
        if (mode != PolicyMode.LOCAL) return false
        if (state.value.guardState == GuardStatus.PARENT_OVERRIDE) return false
        // Clear a live round before the guard moves. If the round's review thread wins the
        // race, the runtime admission below still refuses a reward under PARENT_OVERRIDE.
        QuizChallenge.abandon()
        LocalPolicyRuntime.beginParentOverride()
        publishCurrent()
        return true
    }

    /** Web control: leave the parent override and return to the normal child guard. */
    @Synchronized
    fun enterChildModeFromWeb(): Boolean {
        if (mode != PolicyMode.LOCAL) return false
        if (state.value.guardState != GuardStatus.PARENT_OVERRIDE) return false
        resumeChildGuard()
        // resumeChildGuard() delegates to onScreenOff(), whose runtime publishes on its own
        // thread; mirror it here so the HTTP response already carries the restored state.
        publishCurrent()
        return true
    }

    /**
     * Web control: the child's "exit timer" action. It keeps the used time and follows the
     * normal USER_EXIT path, which starts the configured rest instead of skipping it.
     */
    @Synchronized
    fun stopTimerFromWeb(): Boolean {
        if (mode != PolicyMode.LOCAL) return false
        if (LocalPolicyRuntime.state.value.sessionState != SessionState.ACTIVE) return false
        val ended = LocalPolicyRuntime.endSessionByUser()
        if (ended) publishCurrent()
        return ended
    }

    /** Live quiz state for the control page. Refreshes the lightweight persisted snapshot. */
    fun quizRoundActive(): Boolean = QuizRuntime.isRoundActive()

    /**
     * Values that make a confirmation token single-use for the exact guard/session/quiz it was
     * issued against. Countdowns are deliberately absent: a token remains valid while the same
     * timer runs, but a new timer or a quiz round transition changes the string.
     */
    fun controlStateFingerprint(): String {
        val ui = state.value
        val quiz = QuizRuntime.state.value.session
        val quizPart = quiz?.let {
            "${it.id}:${it.status.name}:${it.run?.startedAt ?: 0L}"
        } ?: "none"
        return listOf(
            ui.mode.name,
            ui.guardState.name,
            ui.sessionState.name,
            ui.activation.name,
            ui.lockReason.name,
            ui.studyModeActive.toString(),
            LocalPolicyRuntime.activeSessionStartedAtEpochMs().toString(),
            quizPart,
        ).joinToString("|")
    }

    /**
     * Study mode is an access state entered from the lock-screen overlay. The lock screen shows
     * the entry in every mode, so the redemption engine needs one too instead of delegating only
     * to the hosted/local rule executor.
     */
    fun enterStudyMode(): Boolean {
        val entered = when (mode) {
            PolicyMode.CLOUD -> if (ruleGuardActive) {
                LocalPolicyRuntime.enterStudyMode()
            } else {
                PolicyRuntime.enterStudyMode()
            }
            PolicyMode.LOCAL -> LocalPolicyRuntime.enterStudyMode()
            PolicyMode.NONE -> false
        }
        if (entered) publishCurrent()
        return entered
    }

    fun exitStudyMode() {
        val exited = when {
            LocalPolicyRuntime.isStudyMode() -> {
                LocalPolicyRuntime.exitStudyMode()
                true
            }
            PolicyRuntime.isStudyMode() -> {
                PolicyRuntime.exitStudyMode()
                true
            }
            else -> false
        }
        if (exited) publishCurrent()
    }

    /**
     * Parent chose to return to the child guard without turning the screen off. It follows the
     * same lifecycle as standby: the temporary unlock ends and its PIN proof is dropped. Guarded
     * so a stray call can never pause a running child session or leave study mode.
     */
    fun resumeChildGuard() {
        if (state.value.guardState != GuardStatus.PARENT_OVERRIDE) return
        onScreenOff()
    }

    fun onScreenOff() {
        // Only the temporary-unlock proof expires with the guard. The web parent center is
        // authenticated by the normal quiz parent login in V29, so a screen-off no longer
        // logs the web page out.
        parentAuth.clear()
        when (mode) {
            PolicyMode.CLOUD -> if (ruleGuardActive) {
                LocalPolicyRuntime.onScreenOff()
            } else {
                PolicyRuntime.onScreenOff()
            }
            PolicyMode.LOCAL -> LocalPolicyRuntime.onScreenOff()
            PolicyMode.NONE -> Unit
        }
    }

    /** Screen on only resumes a paused rule session; it never starts a new one. */
    fun onScreenOn() {
        when (mode) {
            PolicyMode.CLOUD -> if (ruleGuardActive) LocalPolicyRuntime.onScreenOn()
            PolicyMode.LOCAL -> LocalPolicyRuntime.onScreenOn()
            PolicyMode.NONE -> Unit
        }
    }

    /**
     * Manual mode change. Leaving a guarded mode needs that mode's PIN; entering from NONE does
     * not. ACTIVE is ended first (cloud settlement is required; pending cloud time is refused),
     * and the target mode never inherits the source mode's temporary unlock.
     */
    suspend fun requestModeSwitch(
        target: PolicyMode,
        pin: String,
        invalidateWeb: Boolean = true,
    ): SwitchResult {
        if (!initialized) return SwitchResult.NotReady
        if (target == PolicyMode.CLOUD && !BuildConfig.CLOUD_ENABLED) return SwitchResult.NotReady
        if (target == mode) return SwitchResult.Success
        if (mode != PolicyMode.NONE) {
            val verified = verifyPin(pin)
            if (verified != PinResult.OK) return SwitchResult.WrongPin(verified)
        }
        // A TV-driven mode switch revokes both the quiz login and the parent editor: the web
        // page is only valid in LOCAL and unbound, so the authorization changed underneath it.
        if (invalidateWeb) invalidateWebSessions("mode_switch")
        parentAuth.clear()
        return when (mode) {
            PolicyMode.CLOUD -> {
                stopRuleGuard()
                val ended = PolicyRuntime.endCloudSessionForSwitch()
                if (ended.isFailure) {
                    val error = ended.exceptionOrNull()
                    if (error?.message?.contains("pending") == true) SwitchResult.PendingMustBeCanceled
                    else SwitchResult.CloudSettlementFailed(error?.message.orEmpty())
                } else {
                    PolicyRuntime.setCloudActive(false)
                    when (target) {
                        PolicyMode.NONE -> {
                            mode = PolicyMode.NONE
                            PolicyModeStore.save(mode)
                            LocalPolicyRuntime.setSource(PolicySource.NONE)
                            LocalPolicyRuntime.deactivate()
                        }
                        PolicyMode.LOCAL -> {
                            mode = PolicyMode.LOCAL
                            PolicyModeStore.save(mode)
                            activateLocalFromStorage()
                        }
                        PolicyMode.CLOUD -> Unit
                    }
                    publishModeChange()
                    SwitchResult.Success
                }
            }
            PolicyMode.LOCAL -> {
                LocalPolicyRuntime.endSessionForSwitch()
                LocalPolicyRuntime.deactivate()
                when (target) {
                    PolicyMode.NONE -> {
                        mode = PolicyMode.NONE
                        PolicyModeStore.save(mode)
                        LocalPolicyRuntime.setSource(PolicySource.NONE)
                        PolicyRuntime.setCloudActive(false)
                    }
                    PolicyMode.CLOUD -> {
                        mode = PolicyMode.CLOUD
                        PolicyModeStore.save(mode)
                        if (!activateCloud()) return SwitchResult.NotReady
                    }
                    PolicyMode.LOCAL -> Unit
                }
                publishModeChange()
                SwitchResult.Success
            }
            PolicyMode.NONE -> {
                when (target) {
                    PolicyMode.LOCAL -> {
                        mode = PolicyMode.LOCAL
                        PolicyModeStore.save(mode)
                        activateLocalFromStorage()
                    }
                    PolicyMode.CLOUD -> {
                        mode = PolicyMode.CLOUD
                        PolicyModeStore.save(mode)
                        if (!activateCloud()) return SwitchResult.NotReady
                    }
                    PolicyMode.NONE -> Unit
                }
                publishModeChange()
                SwitchResult.Success
            }
        }
    }

    /**
     * The parent center's switch is a one-step local mode: a fresh install must not fall into
     * the local setup screen just because no rules were saved yet. Existing rules are preserved.
     */
    private fun ensureLocalRules() {
        if (LocalPolicyRuntime.currentRules?.isUsable != true) {
            LocalPolicyRuntime.setRules(
                LocalRules.DEFAULTS,
            )
        }
    }

    /**
     * (Re)attaches the LOCAL child-mode engine. Leaving the cloud partition must reload the
     * local rules/ledger from disk instead of activating whatever the hosted executor left in
     * memory; otherwise the first tick would count against the cloud session under local rules.
     * When the source is already LOCAL the in-memory state is kept (setup and test flows).
     */
    private fun activateLocalFromStorage(resetGuard: Boolean = true) {
        val leavingHosted = LocalPolicyRuntime.state.value.source != PolicySource.LOCAL
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        if (leavingHosted) LocalPolicyRuntime.reloadActiveSubject()
        ensureLocalRules()
        if (resetGuard) LocalPolicyRuntime.resetGuardForModeSwitch()
        LocalPolicyRuntime.activate()
    }

    private fun activateCloud(): Boolean {
        val app = context ?: return false
        PolicyRuntime.start(app, devPin = devPin, active = true)
        // The target mode starts guarded; it must not inherit another mode's unlock.
        PolicyRuntime.setTemporaryUnlock(false)
        if (!PolicyRuntime.state.value.bound) {
            // Show the existing binding screen until the phone scans the code.
            PolicyRuntime.setTemporaryUnlock(true)
        } else {
            // The local bound flag can be stale after an out-of-band unbind; verify it before
            // presenting this as a usable cloud guard. The binding screen path is left alone.
            scope.launch { PolicyCloud.syncOnStartup() }
        }
        applyRemotePolicyMode(PolicyRuntime.remotePolicy.value)
        return true
    }

    private fun publishModeChange() {
        _state.value = currentEngineState()
        _revision.value = _revision.value + 1L
        reportModeNow()
        if (mode == PolicyMode.CLOUD && PolicyRuntime.state.value.bound) {
            PolicyAppInventory.requestSync("mode-cloud", immediate = true)
        }
    }

    /** Starts the low-frequency mode heartbeat once per process. */
    private fun startModeHeartbeat() {
        if (heartbeatStarted) return
        heartbeatStarted = true
        scope.launch {
            while (isActive) {
                delay(MODE_HEARTBEAT_MS)
                reportModeOnce()
            }
        }
    }

    /**
     * One revocation point for every transition that invalidates web authorization. Clearing
     * the remembered quiz token as well as the editor lease is what forces the page back to
     * its login or disabled screen instead of letting a cached tab keep editing.
     */
    private fun invalidateWebSessions(reason: String) {
        if (ParentWebCenter.ENABLED) ParentWebAccess.invalidate(reason)
        QuizAccess.invalidateWebSession(reason)
    }

    /** Report immediately after binding, mode switch, or when HomeGuard asks for a refresh. */
    fun reportModeNow() {
        scope.launch { reportModeOnce() }
    }

    private suspend fun reportModeOnce() {
        if (!initialized) return
        val cloud = PolicyRuntime.state.value
        if (cloud.deviceNo.isBlank()) return
        // Report NONE/unbound too: the phone's device page reads this field and must not keep
        // showing a stale "cloud" mode after the TV was unbound locally or reset.
        PolicyCloud.reportPolicyMode(
            deviceNo = cloud.deviceNo,
            mode = mode,
            version = PolicyModeStore.version(),
            bound = cloud.bound,
            sessionState = state.value.sessionState.name,
            // This build understands the V6 policy node and handoff. Report it so an existing
            // binding upgraded from an older APK can move from the legacy redeem page to V6;
            // old builds omit the field and keep the backend's stored capability.
            supportsUnifiedPolicy = true,
            appliedMode = appliedRemoteMode,
            appliedConfigVersion = appliedRemoteVersion.takeIf { it > 0 },
            policyEpoch = state.value.policyEpoch,
            quizCapabilities = QuizConfigSync.capabilityReport(),
            appliedQuizVersion = QuizConfigSync.appliedVersion().takeIf { it > 0 },
        ).onFailure { Log.w(TAG, "mode report failed; heartbeat will retry", it) }
    }

    /**
     * A parent just confirmed the scan binding. Binding success is itself the mode decision:
     * force CLOUD (even if the screen was reached from NONE), keep the guard RUNNING and report
     * the mode and device status immediately. This is the one-step flow; a later Back must not
     * undo it. Only status is reported: the local rules and parent password stay on the TV.
     */
    @Synchronized
    fun confirmCloudBinding() {
        val app = context ?: return
        Log.i(TAG, "confirm cloud binding; previous mode=$mode")
        // Binding hands the TV to the phone app. Any web page authorized for the local,
        // unbound state must lose both its quiz login and its editor lease.
        invalidateWebSessions("cloud_bound")
        if (mode != PolicyMode.CLOUD) {
            // The embedded binding guide runs while LOCAL is still protecting the TV. End that
            // session before handing the guard to the cloud engine; the scan is the consent.
            if (mode == PolicyMode.LOCAL) LocalPolicyRuntime.endSessionForSwitch()
            mode = PolicyMode.CLOUD
            PolicyModeStore.save(mode)
            LocalPolicyRuntime.setSource(PolicySource.NONE)
            LocalPolicyRuntime.deactivate()
        }
        PolicyRuntime.start(app, devPin = devPin, active = true)
        PolicyRuntime.onCloudBound()
        applyRemotePolicyMode(PolicyRuntime.remotePolicy.value)
        _state.value = currentEngineState()
        _revision.value = _revision.value + 1L
        reportModeNow()
        PolicyAppInventory.requestSync("bound", immediate = true)
    }

    /**
     * Binding detected inside the parent center. Switch synchronously so the bound pane can
     * appear immediately, then run the post-bind reports on the coordinator scope so they
     * survive the guide leaving composition.
     */
    fun confirmCloudBindingFromGuide(deviceNo: String) {
        confirmCloudBinding()
        if (deviceNo.isNotBlank()) {
            scope.launch { PolicyCloud.onBound(deviceNo) }
        }
    }

    /**
     * Legacy one-time handoff from the local rules to the cloud.
     *
     * 2026-11-19: binding no longer uploads local data, so this returns [HandoffResult.NotNeeded]
     * and never touches local rules or the parent password. Remote binding only reports device
     * status; the cloud and local configurations are independent. The implementation stays for
     * a controlled rollback via [LOCAL_RULES_UPLOAD_ENABLED].
     */
    suspend fun completeHandoff(pin: String = ""): HandoffResult {
        if (!LOCAL_RULES_UPLOAD_ENABLED) return HandoffResult.NotNeeded
        val cloud = PolicyRuntime.state.value
        if (mode != PolicyMode.CLOUD || !cloud.bound) {
            return HandoffResult.Failed("device is not bound")
        }
        val remote = PolicyRuntime.remotePolicy.value
            ?: return HandoffResult.Failed("cloud policy is not loaded")
        if (remote.handoffState != RemoteHandoffState.PENDING) return HandoffResult.NotNeeded
        val syncPin = pin.takeIf { it.length == 6 && it.all { ch -> ch in '0'..'9' } }
        val rules = LocalPolicyRuntime.currentRules
        if (rules == null ||
            rules.validationError() != LocalRulesError.NONE ||
            rules.weekdayWindows.isEmpty() ||
            rules.weekendWindows.isEmpty()
        ) {
            return HandoffResult.NoRules
        }
        // A backend that stores two sets needs both; if the local v2 document is unavailable
        // while the backend advertises rule sets, fail closed instead of uploading one set and
        // silently collapsing the other.
        val localSets = LocalPolicyRuntime.currentRuleSets
        if (remote.supportsRuleSets && localSets == null) return HandoffResult.NoRules
        val deviceNo = cloud.deviceNo
        if (deviceNo.isBlank()) return HandoffResult.Failed("device number is empty")
        if (!handoffInFlight.compareAndSet(false, true)) return HandoffResult.Busy
        return try {
            if (syncPin != null) {
                PolicyCloud.updatePassword(deviceNo, syncPin).getOrElse {
                    return HandoffResult.Failed(it.message ?: "password update failed")
                }
                PolicyRuntime.clearPasswordSyncPending()
            } else {
                Log.i(TAG, "handoff without verified parent PIN; cloud password untouched")
            }
            val policy = PolicyCloud.handoff(
                deviceNo = deviceNo,
                rules = rules,
                activation = LocalPolicyRuntime.state.value.activation,
                ruleSets = localSets.takeIf { remote.supportsRuleSets },
                // 2026-11-12: 交接只上传本地规则，不再携带本机答题配置。
                // 绑定后答题配置完全以云端为准（题库孩子级配置 + TV 关卡）。
                // 回滚：恢复 quiz = QuizConfigSync.reportPayload()。
                quiz = null,
            ).getOrElse {
                return HandoffResult.Failed(it.message ?: "handoff failed")
            } ?: return HandoffResult.Failed("handoff returned no policy")
            PolicyRuntime.onRemotePolicy(policy)
            applyRemotePolicyMode(policy)
            // 上行关闭后 retryNow 是 no-op（只清理旧版本遗留的 pending 标记）。
            QuizConfigSync.retryNow()
            publishCurrent()
            if (policy.handoffState == RemoteHandoffState.DONE) {
                HandoffResult.Success
            } else {
                HandoffResult.Failed("handoff did not complete")
            }
        } finally {
            handoffInFlight.set(false)
        }
    }

    /** Read-only summary refresh; never switches the guard on its own beyond the policy state. */
    fun refreshRemotePolicy() {
        scope.launch { refreshRemotePolicyNow() }
    }

    /**
     * Suspend variant for the parent-center retry button: the caller needs to know whether a
     * config was actually loaded so it can show a failure instead of silently doing nothing.
     */
    suspend fun refreshRemotePolicyNow(): Boolean {
        val cloud = PolicyRuntime.state.value
        if (!cloud.bound || cloud.deviceNo.isBlank()) return false
        return PolicyCloud.fetchConfig(cloud.deviceNo).fold(
            onSuccess = { snapshot ->
                val policy = snapshot.remotePolicy
                if (policy == null) {
                    // HTTP succeeded, but the backend has not produced the V6 policy node yet;
                    // that is still "waiting for config" from the parent center's point of view.
                    false
                } else {
                    PolicyRuntime.onRemotePolicy(policy)
                    applyRemotePolicyMode(policy)
                    QuizConfigSync.applyRemote(snapshot.quiz)
                    QuizConfigDocumentSync.requestSync(force = true)
                    true
                }
            },
            onFailure = { false },
        )
    }

    /**
     * Called by the MQTT bind_status_changed command or the binding-status poll. The backend
     * has already made the database authoritative; the TV stops the cloud engine and returns to
     * NONE. Settlement is best-effort only and never blocks this transition.
     */
    @Synchronized
    fun onCloudUnboundFromBackend(): Boolean {
        if (mode != PolicyMode.CLOUD) return false
        // The web parent center was authorized against the local, unbound state. It is not
        // available while bound, but an old tab may still hold a login; revoke it.
        invalidateWebSessions("cloud_unbound")
        parentAuth.clear()
        Log.i(TAG, "cloud unbound from backend; child mode is turned off")
        stopRuleGuard()
        RuleUsageReporter.clear()
        // This queues one final usage report for an ACTIVE record; it is not awaited.
        PolicyRuntime.onCloudUnbound()
        PolicyRuntime.setCloudActive(false)
        // Do not delete the local question cache: it is this TV's own content. Only the cloud
        // source selection and any pending upload belong to the removed family.
        QuizConfigSync.onUnbound()
        // The cloud family owned the guard. Switching straight into the TV's own child mode
        // would start limiting apps under a plan nobody just confirmed on this TV, so an
        // unbind leaves it unguarded (NONE). The local rules and ledger stay on disk; the
        // parent turns child mode back on manually from the local parent center.
        mode = PolicyMode.NONE
        PolicyModeStore.save(mode)
        // Deactivate before dropping the source: a pending cloud grant must be discarded into
        // the hosted partition, not checkpointed into the local one on the way out.
        LocalPolicyRuntime.deactivate()
        LocalPolicyRuntime.setSource(PolicySource.NONE)
        // The removed family's usage/guard/study state must not come back on a later rebind.
        LocalPolicyRuntime.clearHostedExecution()
        publishModeChange()
        notifyCloudUnboundChildModeOff()
        return true
    }

    /** Unbinding is visible on the TV; say that limits stopped instead of changing silently. */
    private fun notifyCloudUnboundChildModeOff() {
        val app = context ?: return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, R.string.cloud_unbound_child_mode_off, Toast.LENGTH_LONG).show()
        }
    }

    /** Back/"bind later" on the remote-mode binding screen: remote guard needs a binding. */
    @Synchronized
    fun cancelRemoteBinding() {
        if (mode != PolicyMode.CLOUD) return
        invalidateWebSessions("cloud_bind_canceled")
        parentAuth.clear()
        // Once the backend has confirmed the binding, Back must not silently leave the TV in
        // NONE; the one-step flow is already committed at that point.
        if (PolicyRuntime.state.value.bound) return
        stopRuleGuard()
        PolicyRuntime.setCloudActive(false)
        mode = PolicyMode.LOCAL
        PolicyModeStore.save(mode)
        // Backing out of the binding screen never ran a cloud session: restore the local child
        // mode that was running before it, from disk, instead of adopting hosted leftovers.
        LocalPolicyRuntime.clearHostedExecution()
        activateLocalFromStorage()
        publishModeChange()
    }

    /** Debug-only: used by the adb harness to avoid waiting for a real clock. */
    internal fun debugForceMode(target: PolicyMode) {
        when (target) {
            PolicyMode.NONE -> {
                PolicyRuntime.setCloudActive(false)
                LocalPolicyRuntime.deactivate()
                LocalPolicyRuntime.setSource(PolicySource.NONE)
            }
            PolicyMode.LOCAL -> {
                PolicyRuntime.setCloudActive(false)
                // The debug switch mirrors a real manual start, but never resets an override
                // the harness is about to exercise.
                activateLocalFromStorage(resetGuard = false)
            }
            PolicyMode.CLOUD -> {
                LocalPolicyRuntime.deactivate()
                LocalPolicyRuntime.setSource(PolicySource.NONE)
                context?.let {
                    PolicyRuntime.start(it, devPin = devPin, active = true)
                    PolicyRuntime.setTemporaryUnlock(!PolicyRuntime.state.value.bound)
                }
            }
        }
        mode = target
        PolicyModeStore.save(target)
        _state.value = currentEngineState()
        _revision.value = _revision.value + 1L
        reportModeNow()
    }

    internal fun debugSetLocalRules(rules: com.planyourtv.policy.LocalRules) {
        LocalPolicyRuntime.setRules(rules)
    }

    internal fun debugLocalSetup(pin: String, rules: com.planyourtv.policy.LocalRules): String? =
        LocalPolicyRuntime.completeSetup(pin, rules)
}
