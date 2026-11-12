package app.orchard.tv.policy

import android.content.Context
import android.os.PowerManager
import android.util.Log
import app.orchard.tv.BuildConfig
import app.orchard.tv.guard.HomeGuardService
import app.orchard.tv.quiz.QuizConfigSync
import app.orchard.tv.quiz.QuizRuntime
import com.planyourtv.policy.AccessMode
import com.planyourtv.policy.Activation
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalPin
import com.planyourtv.policy.LocalRuleSets
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalRulesError
import com.planyourtv.policy.LockReason
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
import com.planyourtv.policy.RemoteActivationAnnouncer
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        AUTO_MODE,
        RESTING,
        OUTSIDE_WINDOW,
        NO_TIMED_APPS,
        STATE_CHANGED,
    }

    /** Lock-screen state published to the page; [eligible] drives the single-use action. */
    data class WebManualStartStatus(
        val eligible: Boolean,
        val reason: WebManualStartReason,
        val restRemainingSeconds: Long = 0L,
        val remainingSeconds: Long = 0L,
    )

    /** Result of the page's single-use action. A rejected PIN never starts a session. */
    sealed interface WebManualStartResult {
        data object Ok : WebManualStartResult
        data object InvalidDuration : WebManualStartResult
        data class Rejected(val reason: WebManualStartReason) : WebManualStartResult
        data class PinInvalid(val locked: Boolean) : WebManualStartResult
    }

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
    /** Same baseline rule for the phone's AUTO ↔ MANUAL switch. */
    private val remoteActivationAnnouncer = RemoteActivationAnnouncer()

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
                    LocalPolicyRuntime.lockForModeSwitch()
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
                if (mode == PolicyMode.CLOUD) applyRemotePolicyMode(remote)
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

    private fun currentEngineState(): PolicyUiState = when (mode) {
        PolicyMode.CLOUD -> if (ruleGuardActive) {
            ruleCloudState(PolicyRuntime.state.value)
        } else {
            cloudState(PolicyRuntime.state.value)
        }
        PolicyMode.LOCAL -> LocalPolicyRuntime.state.value.copy(mode = PolicyMode.LOCAL)
        PolicyMode.NONE -> noneState()
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
            autoReady = local.autoReady,
            noTimedApps = local.noTimedApps,
            totalSeconds = local.totalSeconds,
            remainingSeconds = local.remainingSeconds,
            effectiveRemainingSeconds = local.effectiveRemainingSeconds,
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
                LocalPolicyRuntime.setSource(PolicySource.LOCAL)
                RuleUsageReporter.stop()
            }
        }

        if (!ruleGuardActive) {
            // Stop the legacy grant engine before the local one starts: it remains the network
            // and identity adapter, but only the local executor may own a pending gate.
            PolicyRuntime.setHostedRuleActive(true)
            LocalPolicyRuntime.lockForModeSwitch()
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
     * Both SetActivation and the config pull that follows a phone edit flow through here. The
     * announcer speaks only on a real AUTO ↔ MANUAL transition after the first observed value.
     */
    private fun announceRemoteActivation(activation: Activation) {
        val cue = remoteActivationAnnouncer.observe(
            activation = activation,
            active = mode == PolicyMode.CLOUD,
        ) ?: return
        presentRemoteChangeOnPanel()
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
        LocalPolicyRuntime.setSource(PolicySource.HOSTED)
        // Old backends only carry one set; mirror it explicitly into both local sets. New
        // backends apply both and let the runtime decide whether the running session or only
        // the inactive set changed.
        val incomingSets = remote.ruleSets ?: LocalRuleSets.mirrorSingleSet(remote.rules)
        val rulesChanged = appliedRemoteVersion != remote.configVersion ||
            LocalPolicyRuntime.currentRules != remote.rules ||
            LocalPolicyRuntime.currentRuleSets != incomingSets
        if (rulesChanged) {
            if (!LocalPolicyRuntime.setRuleSetsWithVersion(incomingSets, remote.configVersion)) {
                return false
            }
        }
        // Activation is cached beside the rules, never mixed into them. Applying the same value
        // is a no-op; a real AUTO ↔ MANUAL switch ends the running segment first (V11).
        val activationBefore = LocalPolicyRuntime.state.value.activation
        val activationApplied = activationBefore != remote.activation &&
            LocalPolicyRuntime.setActivation(remote.activation)
        // The local state flow normally refreshes the UI through its collector on the main
        // looper. That collector can lag behind the voice cue on a busy TV, which leaves the
        // lock screen on the old AUTO/MANUAL badge after the switch was already announced.
        // Publish the derived UI state here so the badge and the cue change together.
        _state.value = currentEngineState()
        PolicyFlowTrace.event(
            "remote.activation.apply",
            "via" to "config",
            "version" to remote.configVersion,
            "before" to activationBefore,
            "remote" to remote.activation,
            "localAfter" to LocalPolicyRuntime.state.value.activation,
            "uiAfter" to _state.value.activation,
            "applied" to activationApplied,
        )
        // The phone's switch may arrive only through this config pull. Observe the activation
        // that was actually applied (not the remote copy) so a refused apply never speaks, and
        // the first observed value still primes the announcer silently.
        announceRemoteActivation(LocalPolicyRuntime.state.value.activation)
        // A rule edit changes the lock screen's detail rows even when the activation did not
        // move; wake that idle panel too. The object drops this when the activation path just
        // nudged, so one config apply still produces one BACK.
        if (rulesChanged) presentRemoteChangeOnPanel()
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
        PolicyMode.CLOUD -> if (ruleGuardActive) {
            SourceEntrySettings.isVisible() && localSourceEntryAllowed()
        } else {
            SourceEntrySettings.isVisible() && PolicyRuntime.allowsSource(sourceId)
        }
        PolicyMode.LOCAL -> SourceEntrySettings.isVisible() && localSourceEntryAllowed()
    }

    private fun localSourceEntryAllowed(): Boolean {
        val local = LocalPolicyRuntime.state.value
        if (!local.loaded) return true
        return when {
            local.guardState == GuardStatus.PARENT_OVERRIDE -> true
            local.guardState == GuardStatus.STUDY_MODE -> false
            local.sessionState == SessionState.ACTIVE &&
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
            ?: LocalRules.defaultsFor(LocalPolicyRuntime.state.value.activation)
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
     * Lock state for the page's single-use action. This mirrors the lock-screen manual start,
     * not the temporary unlock: the TV must be in manual mode, showing the lock surface, and
     * waiting for the parent, with no rest, session, study mode or quiz in progress. The page
     * only draws the button from this snapshot; [startManualFromWeb] repeats every check after
     * the PIN and the runtime repeats the policy checks atomically.
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
        if (ui.activation != Activation.MANUAL) {
            return WebManualStartStatus(false, WebManualStartReason.AUTO_MODE)
        }
        if (ui.sessionState == SessionState.ACTIVE) {
            return WebManualStartStatus(
                eligible = false,
                reason = WebManualStartReason.SESSION_ACTIVE,
                remainingSeconds = ui.effectiveRemainingSeconds,
            )
        }
        if (ui.sessionState == SessionState.PENDING) {
            return WebManualStartStatus(false, WebManualStartReason.SESSION_PENDING)
        }
        if (ui.lockReason == LockReason.RESTING) {
            return WebManualStartStatus(
                eligible = false,
                reason = WebManualStartReason.RESTING,
                restRemainingSeconds = ui.restRemainingSeconds,
            )
        }
        if (ui.lockReason == LockReason.OUTSIDE_WINDOW) {
            return WebManualStartStatus(false, WebManualStartReason.OUTSIDE_WINDOW)
        }
        if (ui.noTimedApps || ui.lockReason == LockReason.NO_TIMED_APPS) {
            return WebManualStartStatus(false, WebManualStartReason.NO_TIMED_APPS)
        }
        if (ui.lockReason != LockReason.MANUAL_WAITING) {
            return WebManualStartStatus(false, WebManualStartReason.STATE_CHANGED)
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
     * The page's single-use action. It requires the parent PIN every time; the remembered admin
     * token only authenticates the page, never the start. The PIN is checked before and the
     * whole state is checked again afterwards, so a session, rest, mode switch or quiz that
     * begins during the (slow) credential check cannot be bypassed. The final start still goes
     * through the same local manual path as the lock screen.
     */
    @Synchronized
    fun startManualFromWeb(pin: String, minutes: Int): WebManualStartResult {
        if (!ManualSessionLimits.isValid(minutes)) {
            return WebManualStartResult.InvalidDuration
        }
        if (!LocalPin.isValid(pin)) {
            return WebManualStartResult.PinInvalid(locked = false)
        }
        val before = webManualStartStatus()
        if (!before.eligible) return WebManualStartResult.Rejected(before.reason)
        when (verifyPin(pin)) {
            PinResult.OK -> Unit
            PinResult.LOCKED_OUT -> return WebManualStartResult.PinInvalid(locked = true)
            PinResult.WRONG, PinResult.NOT_SET -> return WebManualStartResult.PinInvalid(locked = false)
        }
        if (isDefaultPinValue(pin)) {
            return WebManualStartResult.Rejected(WebManualStartReason.DEFAULT_PIN)
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
        RejectReason.ACTIVATION_MISMATCH -> WebManualStartReason.AUTO_MODE
        RejectReason.STALE_EPOCH, RejectReason.STALE_VERSION,
        RejectReason.EXPIRED, RejectReason.INVALID_COMMAND,
        RejectReason.INVALID_SCOPE -> WebManualStartReason.STATE_CHANGED
        RejectReason.PARENT_OVERRIDE -> WebManualStartReason.ALREADY_UNLOCKED
        RejectReason.BUSY -> WebManualStartReason.SESSION_ACTIVE
        RejectReason.RESTING -> WebManualStartReason.RESTING
        RejectReason.SCREEN_OFF -> WebManualStartReason.SCREEN_OFF
        RejectReason.OUTSIDE_WINDOW -> WebManualStartReason.OUTSIDE_WINDOW
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

    /** AUTO_READY "开始计时" on the lock screen. Manual mode rejects this path. */
    fun startAutoSession(): SessionStartResult = when (mode) {
        PolicyMode.CLOUD -> if (ruleGuardActive) {
            LocalPolicyRuntime.startAutoSession()
        } else {
            SessionStartResult.Rejected(RejectReason.GUARD_DISABLED)
        }
        PolicyMode.LOCAL -> LocalPolicyRuntime.startAutoSession()
        PolicyMode.NONE -> SessionStartResult.Rejected(RejectReason.GUARD_DISABLED)
    }

    /**
     * Parent-confirmed local manual start; the caller already verified the parent PIN.
     * [allowHostedRule] is set by the lock-screen picker and the local parent web page: the
     * V10 hosted rule guard accepts a local start in manual mode, while legacy boundaries
     * (REDEEM, migration and quiz rewards) keep the old local-only behaviour.
     */
    fun startLocalManualSession(
        durationSeconds: Long,
        expiresAtEpochMs: Long? = null,
        allowHostedRule: Boolean = false,
    ): SessionStartResult = when {
        mode == PolicyMode.LOCAL ->
            LocalPolicyRuntime.startManualSession(durationSeconds, expiresAtEpochMs)
        mode == PolicyMode.CLOUD && allowHostedRule && ruleGuardActive &&
            LocalPolicyRuntime.state.value.source == PolicySource.HOSTED ->
            LocalPolicyRuntime.startManualSession(durationSeconds, expiresAtEpochMs)
        else -> SessionStartResult.Rejected(RejectReason.HOSTED_MANUAL_REMOTE_ONLY)
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

    /** Local parent-center automatic/manual switch. Hosted config is edited on the phone. */
    fun setLocalActivation(activation: Activation): Boolean {
        if (mode != PolicyMode.LOCAL) return false
        if (LocalPolicyRuntime.state.value.source != PolicySource.LOCAL) return false
        val previous = LocalPolicyRuntime.state.value.activation
        if (previous == activation) {
            LocalPolicyRuntime.setActivation(activation)
            publishCurrent()
            return true
        }
        val changed = LocalPolicyRuntime.setActivation(activation)
        if (!changed) return false
        // AUTO and MANUAL now own independent time-rule sets. Switching swaps which set the
        // executor runs; it must not rewrite the rest (or any other value) of either set.
        publishCurrent()
        return true
    }

    /**
     * Hosted SetActivation. Both the config version and the policy epoch must match so an old
     * command cannot flip the mode after a rule edit or a previous switch.
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
        val activationBefore = LocalPolicyRuntime.state.value.activation
        val changed = LocalPolicyRuntime.setActivation(activation)
        if (changed) {
            // Keep the cached hosted mirror on the same activation. Without this, the next
            // config emission would look like an older phone value and flip the TV back.
            PolicyRuntime.onRemotePolicy(remote.copy(activation = activation))
            publishCurrent()
            PolicyFlowTrace.event(
                "remote.activation.apply",
                "via" to "command",
                "version" to expectedVersion,
                "before" to activationBefore,
                "remote" to activation,
                "localAfter" to LocalPolicyRuntime.state.value.activation,
                "uiAfter" to _state.value.activation,
            )
            announceRemoteActivation(activation)
        }
        return changed
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
        // The temporary-unlock proof expires with the unlock it came from.
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
    suspend fun requestModeSwitch(target: PolicyMode, pin: String): SwitchResult {
        if (!initialized) return SwitchResult.NotReady
        if (target == PolicyMode.CLOUD && !BuildConfig.CLOUD_ENABLED) return SwitchResult.NotReady
        if (target == mode) return SwitchResult.Success
        if (mode != PolicyMode.NONE) {
            val verified = verifyPin(pin)
            if (verified != PinResult.OK) return SwitchResult.WrongPin(verified)
        }
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
                            ensureLocalRules()
                            LocalPolicyRuntime.setSource(PolicySource.LOCAL)
                            LocalPolicyRuntime.lockForModeSwitch()
                            LocalPolicyRuntime.activate()
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
                        ensureLocalRules()
                        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
                        LocalPolicyRuntime.lockForModeSwitch()
                        LocalPolicyRuntime.activate()
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
                LocalRules.defaultsFor(LocalPolicyRuntime.state.value.activation),
            )
        }
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
            supportsQuizConfig = QuizConfigSync.SUPPORTS_QUIZ_CONFIG,
            supportsCloudQuiz = QuizConfigSync.SUPPORTS_CLOUD_QUIZ,
            appliedQuizVersion = QuizConfigSync.appliedVersion().takeIf { it > 0 },
        ).onFailure { Log.w(TAG, "mode report failed; heartbeat will retry", it) }
    }

    /**
     * A parent just confirmed the scan binding. Binding success is itself the mode decision:
     * force CLOUD (even if the screen was reached from NONE), keep the guard RUNNING and report
     * the mode. This is the one-step flow; a later Back must not undo it.
     */
    @Synchronized
    fun confirmCloudBinding() {
        val app = context ?: return
        Log.i(TAG, "confirm cloud binding; previous mode=$mode")
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
     * One-time handoff from the local rules to the cloud. Remote management is gated by the
     * privacy notice, not by the local parent PIN, so the PIN is optional here: when the parent
     * center already verified one, it is uploaded so the phone keeps the same password; when it
     * did not, only the rules are handed off and the cloud password is left untouched. Password
     * and rules are separate requests by contract; a password failure aborts the handoff so the
     * cloud never ends up done with a different PIN. The call is idempotent on the backend.
     */
    suspend fun completeHandoff(pin: String = ""): HandoffResult {
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
                quiz = QuizConfigSync.reportPayload(),
            ).getOrElse {
                return HandoffResult.Failed(it.message ?: "handoff failed")
            } ?: return HandoffResult.Failed("handoff returned no policy")
            PolicyRuntime.onRemotePolicy(policy)
            applyRemotePolicyMode(policy)
            // The handoff may have carried the binding snapshot; repeating the report is
            // idempotent and clears a pending upload whose first attempt was lost.
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
        parentAuth.clear()
        Log.i(TAG, "cloud unbound from backend; returning to NONE")
        stopRuleGuard()
        RuleUsageReporter.clear()
        // Set the mode first: PolicyRuntime.onCloudUnbound publishes a cloud state synchronously,
        // and the collector must not put it back on screen after the switch.
        mode = PolicyMode.NONE
        PolicyModeStore.save(mode)
        // This queues one final usage report for an ACTIVE record; it is not awaited.
        PolicyRuntime.onCloudUnbound()
        PolicyRuntime.setCloudActive(false)
        // Do not delete the local question cache: it is this TV's own content. Only the cloud
        // source selection and any pending upload belong to the removed family.
        QuizConfigSync.onUnbound()
        LocalPolicyRuntime.deactivate()
        LocalPolicyRuntime.setSource(PolicySource.NONE)
        _state.value = noneState()
        _revision.value = _revision.value + 1L
        return true
    }

    /** Back/"bind later" on the remote-mode binding screen: remote guard needs a binding. */
    @Synchronized
    fun cancelRemoteBinding() {
        if (mode != PolicyMode.CLOUD) return
        parentAuth.clear()
        // Once the backend has confirmed the binding, Back must not silently leave the TV in
        // NONE; the one-step flow is already committed at that point.
        if (PolicyRuntime.state.value.bound) return
        stopRuleGuard()
        PolicyRuntime.setCloudActive(false)
        LocalPolicyRuntime.deactivate()
        LocalPolicyRuntime.setSource(PolicySource.NONE)
        mode = PolicyMode.NONE
        PolicyModeStore.save(mode)
        _state.value = noneState()
        _revision.value = _revision.value + 1L
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
                LocalPolicyRuntime.setSource(PolicySource.LOCAL)
                LocalPolicyRuntime.activate()
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
