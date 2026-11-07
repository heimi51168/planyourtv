package com.planyourtv.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Outcome of applying the cloud-authoritative PIN to the shared local stores. */
enum class CloudPinApplyResult {
    APPLIED,
    UNCHANGED,
    IGNORED,
    INVALID,
    NOT_READY,
}

/**
 * Backend MQTT command-signing state.
 *
 * [known] is false until this v2 device has fetched `/current/command-key` at least once.
 * A v2 device with an unknown state must drop dangerous MQTT commands: without knowing whether
 * the backend signs, accepting an unsigned command is indistinguishable from accepting a forgery.
 */
data class CommandSigningState(
    val known: Boolean,
    val enabled: Boolean,
    val keyId: String,
    val publicKey: String,
    val fetchedAtMs: Long,
)

/**
 * Phase 0/1 product facade.
 *
 * The session and guard are device-level. There is deliberately no per-app
 * allowlist in the session: ACTIVE means the whole TV is usable. App-level
 * access is a separate [PolicyAccessPolicy] that can be switched on later
 * without touching this state machine.
 */
object PolicyRuntime {
    private const val TAG = "PolicyRuntime"
    private const val LEGACY_PIN_ITERATIONS = 100_000
    private const val MAX_FAILED = 5
    private const val LOCKOUT_MS = 30_000L
    private const val SAVE_EVERY_TICKS = 5L
    private const val REPORT_INTERVAL_MS = 30_000L
    private const val SYNC_PENDING_MS = 5_000L
    private const val SYNC_ACTIVE_MS = 10_000L
    private const val SYNC_IDLE_MS = 10_000L
    private const val SYNC_UNBOUND_MS = 60_000L
    private const val SYNC_MAX_MS = 60_000L

    @Volatile
    private var host: PolicyHost? = null

    @Volatile
    private var syncHost: PolicySyncHost? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var syncJob: Job? = null
    private var appContext: Context? = null
    private var started = false
    private var screenReceiver: BroadcastReceiver? = null

    private var session = PolicySession()
    private var guardState = GuardStatus.ENFORCING
    private var pinConfigured = false
    private var bound = false
    private var cloudNeedsGrant = false
    private var deviceNo = ""
    private var bindingDeferred = false
    private var loaded = false
    private var storageLoaded = false
    private var lastSavedTick = 0L

    // Cloud session metadata. The session itself stays a pure PolicySession.
    private var recordId = 0
    private var recordStarted = false
    private var accessMode = AccessMode.SCREEN
    private var allowedPackages: Set<String> = emptySet()
    private var accessPolicy: PolicyAccessPolicy = ScreenAccessPolicy
    /** Packages that stay blocked in APPS mode even if the grant includes them (AI assistants). */
    private var accessBlocklist: Set<String> = emptySet()
    /**
     * Child study mode entered from the lock screen. It is not a session and never charges the
     * grant: while set, only the cloud study list stays launchable. Memory only, like PAUSED.
     */
    private var studyMode = false
    /** Last cloud policy mirror; read-only on the TV. Execution still uses the legacy engines. */
    private var remotePolicySnapshot: RemotePolicy? = null
    private var validUntil = ""
    private var userIconUrl = ""
    /**
     * True while the local rule executor owns the guard. The redemption engine keeps network,
     * binding and identity duties, but must not run a second grant state machine: no PENDING
     * session, no GRANT_READY voice, no bringHome, no timer.
     */
    private var hostedRuleActive = false
    private var lockReason = LockReason.NONE
    private var windowDeadlineElapsed = 0L
    private val spokenThresholds = mutableSetOf<Long>()

    // Reporting / sync state. Only touched from synchronized methods or the sync coroutine.
    private var lastReportAt = 0L
    private val reportInFlight = AtomicBoolean(false)
    private val syncInFlight = AtomicBoolean(false)
    /**
     * Set by a stop/config event while a fetch is already running. The in-flight fetch re-runs
     * once after it finishes, so an MQTT event cannot be swallowed until the next poll.
     */
    @Volatile
    private var syncDirty = false
    @Volatile
    private var syncFailures = 0

    private val _state = MutableStateFlow(PolicyUiState())
    val state: StateFlow<PolicyUiState> = _state.asStateFlow()

    private val _cloudActive = MutableStateFlow(true)
    /** False while the product is in LOCAL mode: no polling, ticker or usage reports. */
    val cloudActive: StateFlow<Boolean> = _cloudActive.asStateFlow()

    private val _remotePolicy = MutableStateFlow<RemotePolicy?>(null)
    /**
     * Last known `policy` node. V6 runtime wiring publishes it for the read-only parent-center
     * summary; it does not alter the current cloud session or the local rule engine yet.
     */
    val remotePolicy: StateFlow<RemotePolicy?> = _remotePolicy.asStateFlow()

    /** Latest usable redemption grant, used by the hosted runtime as a second arm path. */
    private val _cloudGrant = MutableStateFlow<PolicyConfig?>(null)
    val cloudGrant: StateFlow<PolicyConfig?> = _cloudGrant.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Dedupe for the hosted-flow trace; every poll otherwise repeats the same grant. */
    private var lastCloudConfigTrace = ""

    /** Fixed voice cues; the app module plays the matching offline clip. */
    private val _voiceCues = MutableSharedFlow<PolicyVoiceCue>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val voiceCues: SharedFlow<PolicyVoiceCue> = _voiceCues.asSharedFlow()

    /**
     * Idempotent. [devPin] is only for a debug build; a real build must set the
     * PIN during binding, never from a hard-coded value.
     */
    @Synchronized
    fun start(context: Context, devPin: String? = null, active: Boolean = true) {
        val ctx = context.applicationContext
        appContext = ctx
        if (!TvStore.isReady) {
            // A LOCKED_BOOT_COMPLETED receiver can run before credential-protected
            // storage is available. MainActivity/HomeGuard will start us again after
            // the user is unlocked.
            if (!TvStore.init(ctx)) return
        }
        if (!storageLoaded) {
            TvPinStore.migrateIfNeeded()
            readPin()
            readSession()
            readDeviceNo()
            readRemotePolicy()
            storageLoaded = true
        }
        // A cloud install keeps the legacy first-run default so the binding screen can finish.
        // Local-only installs choose their PIN when child mode is first enabled; no known
        // default is written there. Nothing here touches the network.
        if (active && TvPinStore.ensureDefaultIfUnset()) {
            readPin()
        }
        if (devPin != null && !pinConfigured) {
            setPin(devPin)
        }
        if (!started) {
            started = true
            loaded = true
            registerScreenReceiver(ctx)
            publish()
            _cloudActive.value = active
            if (active) startCloudLoopLocked()
        } else {
            loaded = true
            setCloudActiveLocked(active)
            publish()
        }
    }

    /** The coordinator turns the cloud engine off in LOCAL mode and back on after a switch. */
    @Synchronized
    fun setCloudActive(active: Boolean) {
        if (!started) return
        setCloudActiveLocked(active)
        publish()
    }

    private fun setCloudActiveLocked(active: Boolean) {
        if (_cloudActive.value == active) return
        _cloudActive.value = active
        if (active) {
            startCloudLoopLocked()
            requestSync()
        } else {
            ticker?.cancel()
            ticker = null
            syncJob?.cancel()
            syncJob = null
            // A deactivated cloud engine must not hand a stale grant to the hosted guard when
            // it is switched back on before the first fresh poll.
            _cloudGrant.value = null
        }
    }

    private fun startCloudLoopLocked() {
        if (ticker == null) {
            ticker = scope.launch {
                while (isActive) {
                    delay(1_000)
                    onTick()
                }
            }
        }
        startSyncLocked()
    }

    /**
     * The app module owns HTTP/MQTT and installs itself here. :policy never sees
     * BuildConfig or a network client.
     */
    @Synchronized
    fun attachSyncHost(host: PolicySyncHost) {
        syncHost = host
        if (started && _cloudActive.value) {
            startSyncLocked()
            requestSync()
        }
    }

    fun attachHost(host: PolicyHost) {
        this.host = host
    }

    fun detachHost(host: PolicyHost) {
        if (this.host === host) this.host = null
    }

    fun host(): PolicyHost? = host

    /** True for an install that already has cloud binding or a cloud PIN. Used for migration only. */
    fun hasStoredCloudIdentity(): Boolean {
        if (!TvStore.isReady) return false
        val bound = TvStore.getBoolean(PolicyKeys.BOUND, false)
        val pin = !TvStore.getString(PolicyKeys.PIN_SALT, null).isNullOrEmpty() &&
            !TvStore.getString(PolicyKeys.PIN_HASH, null).isNullOrEmpty()
        return bound || pin
    }

    /**
     * TV Link v2 bearer issued by the backend after enrollment.
     *
     * Kept out of [PolicyUiState] and logs on purpose: the public deviceNo is what the parent
     * scans, while this token is the only secret that authorizes device API calls. Returns an
     * empty string when the device has not enrolled or credential storage is not ready.
     */
    fun deviceToken(): String {
        if (!TvStore.isReady) return ""
        return TvStore.getDeviceToken().orEmpty()
    }

    /**
     * Persists the enrollment token before it is used for any request. Returns false if the
     * process cannot write credential storage or the value does not look like a v2 token.
     */
    @Synchronized
    fun storeDeviceToken(token: String): Boolean {
        if (!TvStore.isReady) return false
        val clean = token.trim()
        if (!clean.startsWith("dt_") || clean.length < 46 || clean.length > 128) return false
        return TvStore.putDeviceToken(clean)
    }

    @Synchronized
    fun clearDeviceToken() {
        if (!TvStore.isReady) return
        TvStore.clearDeviceToken()
    }

    /**
     * Current command-signing state. The public key is not a secret, but it must only be
     * accepted from the authenticated v2 endpoint; callers never put it in logs or UI.
     */
    fun commandSigning(): CommandSigningState {
        if (!TvStore.isReady) return CommandSigningState(false, false, "", "", 0L)
        val fetchedAt = TvStore.getLong(PolicyKeys.COMMAND_SIGNING_FETCHED_AT, 0L)
        return when (TvStore.getString(PolicyKeys.COMMAND_SIGNING_STATE, null)) {
            "enabled" -> CommandSigningState(
                known = true,
                enabled = true,
                keyId = TvStore.getString(PolicyKeys.COMMAND_SIGNING_KEY_ID, null).orEmpty(),
                publicKey = TvStore.getString(PolicyKeys.COMMAND_SIGNING_PUBLIC_KEY, null).orEmpty(),
                fetchedAtMs = fetchedAt,
            )
            "disabled" -> CommandSigningState(true, false, "", "", fetchedAt)
            else -> CommandSigningState(false, false, "", "", 0L)
        }
    }

    /**
     * Stores the backend's signing state. Returns false when the response is malformed or
     * storage cannot persist it; callers must then leave the previous state untouched.
     */
    @Synchronized
    fun storeCommandSigning(enabled: Boolean, keyId: String, publicKey: String): Boolean {
        if (!TvStore.isReady) return false
        if (enabled) {
            val cleanKeyId = keyId.trim()
            val cleanKey = publicKey.trim()
            if (cleanKeyId.isEmpty() || cleanKeyId.length > 64) return false
            if (cleanKey.isEmpty() || cleanKey.length > 4096) return false
            TvStore.putString(PolicyKeys.COMMAND_SIGNING_KEY_ID, cleanKeyId)
            TvStore.putString(PolicyKeys.COMMAND_SIGNING_PUBLIC_KEY, cleanKey)
            TvStore.putLong(PolicyKeys.COMMAND_SIGNING_FETCHED_AT, System.currentTimeMillis())
            TvStore.putString(PolicyKeys.COMMAND_SIGNING_STATE, "enabled")
            TvStore.sync()
            return TvStore.getString(PolicyKeys.COMMAND_SIGNING_STATE, null) == "enabled" &&
                TvStore.getString(PolicyKeys.COMMAND_SIGNING_KEY_ID, null) == cleanKeyId &&
                TvStore.getString(PolicyKeys.COMMAND_SIGNING_PUBLIC_KEY, null) == cleanKey
        }
        TvStore.remove(PolicyKeys.COMMAND_SIGNING_KEY_ID)
        TvStore.remove(PolicyKeys.COMMAND_SIGNING_PUBLIC_KEY)
        TvStore.putLong(PolicyKeys.COMMAND_SIGNING_FETCHED_AT, System.currentTimeMillis())
        TvStore.putString(PolicyKeys.COMMAND_SIGNING_STATE, "disabled")
        TvStore.sync()
        return TvStore.getString(PolicyKeys.COMMAND_SIGNING_STATE, null) == "disabled"
    }

    @Synchronized
    fun clearCommandSigning() {
        if (!TvStore.isReady) return
        TvStore.remove(PolicyKeys.COMMAND_SIGNING_STATE)
        TvStore.remove(PolicyKeys.COMMAND_SIGNING_KEY_ID)
        TvStore.remove(PolicyKeys.COMMAND_SIGNING_PUBLIC_KEY)
        TvStore.remove(PolicyKeys.COMMAND_SIGNING_FETCHED_AT)
        TvStore.sync()
    }

    /** True while the stored parent PIN is still the built-in 123456. */
    @Synchronized
    fun isDefaultPin(local: Boolean = false): Boolean = TvPinStore.isDefaultConfigured(local)

    /** True for the built-in PIN values that a new install must not keep. */
    fun isDefaultPinValue(pin: String): Boolean = TvPinStore.isDefault(pin)

    /**
     * True when local child mode must first ask the parent to choose a PIN: there is no local
     * PIN, or an install made before [start] stopped creating defaults still has 123456. The
     * parent center shows its setup dialog before switching to LOCAL.
     */
    @Synchronized
    fun needsLocalPinSetup(): Boolean =
        !TvPinStore.localConfigured() || TvPinStore.isDefaultConfigured(local = true)

    /** The cloud study list; empty means the TV has no study mode. */
    private fun studyPackages(): Set<String> =
        remotePolicySnapshot?.rules?.effectiveStudyPackages.orEmpty()

    /**
     * True while the study scope replaces the desktop and foreground gate. Study apps do not
     * need an ACTIVE session, so this is deliberately separate from [isAppScopeActive].
     */
    fun isStudyMode(): Boolean =
        _cloudActive.value && loaded && !hostedRuleActive && studyMode &&
            studyPackages().isNotEmpty()

    /** App-level policy is on. Screen-level keeps this false so HomeGuard is unchanged. */
    fun isEnabled(): Boolean =
        _cloudActive.value && loaded && (isStudyMode() || accessPolicy.enabled)

    /** APPS filtering is active only while the cloud session is ACTIVE and RUNNING. */
    fun isAppScopeActive(): Boolean =
        _cloudActive.value &&
            loaded &&
            guardState == GuardStatus.ENFORCING &&
            session.state == SessionState.ACTIVE &&
            accessPolicy.enabled

    /** Desktop presentation filter; non-ACTIVE/PAUSED keeps the full list. */
    fun allowsPackage(packageName: String): Boolean = when {
        isStudyMode() ->
            packageName in studyPackages() && packageName !in accessBlocklist
        !isAppScopeActive() -> true
        else -> accessPolicy.isAllowed(packageName) && packageName !in accessBlocklist
    }

    /** True when the current grant means "all apps" rather than a specific list. */
    fun allAppsAllowed(): Boolean =
        accessPolicy is AppAccessPolicy && (accessPolicy as AppAccessPolicy).isAllValid

    /**
     * AppCatalog hook. Package rows stay unfiltered so HomeViewModel can filter at presentation
     * time without deleting the tile disk cache. Shortcuts and pinned inputs are hidden here.
     */
    fun allowsEntry(
        context: Context,
        key: String,
        isSource: Boolean = false,
        isShortcut: Boolean = false,
    ): Boolean {
        // Study mode is an explicit app scope, so inputs and shortcuts stay out of it.
        if (isStudyMode()) return !isSource && !isShortcut
        if (!isAppScopeActive()) return true
        if (isShortcut || isSource) return allAppsAllowed()
        return true
    }

    fun allowsSource(sourceId: String): Boolean {
        if (isStudyMode()) return false
        return !isAppScopeActive() || allAppsAllowed()
    }

    fun shouldBlock(context: Context, packageName: String): Boolean {
        if (isStudyMode()) {
            return packageName != context.packageName &&
                (packageName in accessBlocklist || packageName !in studyPackages())
        }
        return isAppScopeActive() &&
            packageName != context.packageName &&
            (packageName in accessBlocklist || !accessPolicy.isAllowed(packageName))
    }

    fun setAccessBlocklist(packages: Set<String>) {
        accessBlocklist = packages
        publish()
    }

    /** True while HomeGuard should keep dragging foreground apps back to Aerial. */
    fun isLockScreen(): Boolean =
        loaded && guardState == GuardStatus.ENFORCING && session.state != SessionState.ACTIVE

    fun isActive(): Boolean = session.state == SessionState.ACTIVE

    fun isTemporaryUnlocked(): Boolean = guardState == GuardStatus.PARENT_OVERRIDE

    /** The child pressed "确认开始计时" on the PENDING screen. */
    @Synchronized
    fun startSession() {
        if (!_cloudActive.value) return
        PolicyFlowTrace.event(
            "session.confirm.legacy",
            "session" to session.state,
            "recordId" to recordId,
            "remaining" to session.remainingSeconds,
        )
        val next = session.start()
        if (next == session) {
            PolicyFlowTrace.event("session.confirm.legacy.reject", "session" to session.state)
            return
        }
        PolicyFlowTrace.event(
            "session.started.legacy",
            "recordId" to recordId,
            "total" to next.totalSeconds,
            "remaining" to next.remainingSeconds,
        )
        session = next
        guardState = GuardStatus.ENFORCING
        recordStarted = true
        lastReportAt = SystemClock.elapsedRealtime()
        spokenThresholds.clear()
        saveSession()
        publish()
        notifyPolicyChanged()
        emitVoice(PolicyVoiceCue.START)
    }

    /**
     * Child action on the lock screen. Study needs a cloud study list and no running session;
     * an already-armed grant may wait underneath, so leaving study returns to its confirm gate.
     * No PIN, timer, ledger or grant is touched.
     */
    @Synchronized
    fun enterStudyMode(): Boolean {
        if (isStudyMode()) return false
        if (!_cloudActive.value || !loaded || hostedRuleActive) return false
        if (guardState != GuardStatus.ENFORCING) return false
        if (session.state == SessionState.ACTIVE) return false
        if (studyPackages().isEmpty()) return false
        studyMode = true
        guardState = GuardStatus.STUDY_MODE
        publish()
        notifyPolicyChanged()
        host?.bringHome()
        return true
    }

    /** Leave study and return to whatever lock screen the current grant produced. */
    @Synchronized
    fun exitStudyMode() {
        if (!studyMode) return
        studyMode = false
        if (guardState == GuardStatus.STUDY_MODE) guardState = GuardStatus.ENFORCING
        publish()
        notifyPolicyChanged()
    }

    /**
     * Parent PIN accepted. This mirrors the phone app: PAUSED is not a session
     * and has no expiry; screen-off/standby/restart puts the guard back to
     * RUNNING. No new quota is created and an existing ACTIVE session keeps
     * counting.
     */
    @Synchronized
    fun submitPin(pin: String): PinResult {
        val result = verifyPin(pin)
        if (result == PinResult.OK) {
            // The parent takes the TV over, so the child's study scope ends with the unlock.
            studyMode = false
            guardState = GuardStatus.PARENT_OVERRIDE
            bindingDeferred = false
            publish()
            notifyPolicyChanged()
        }
        return result
    }

    /** Non-mutating PIN check for the parent center; it does not enter PAUSED. */
    @Synchronized
    fun verifyPin(pin: String): PinResult {
        if (!_cloudActive.value) return PinResult.NOT_SET
        if (!TvStore.isReady || !pinConfigured) return PinResult.NOT_SET
        val now = System.currentTimeMillis()
        var failed = TvStore.getInt(PolicyKeys.PIN_FAILED, 0)
        var lockoutUntil = TvStore.getLong(PolicyKeys.PIN_LOCKOUT, 0L)
        if (lockoutUntil > 0 && now >= lockoutUntil) {
            failed = 0
            lockoutUntil = 0L
            TvStore.putInt(PolicyKeys.PIN_FAILED, 0)
            TvStore.putLong(PolicyKeys.PIN_LOCKOUT, 0L)
        }
        if (lockoutUntil > now) return PinResult.LOCKED_OUT
        val salt = TvStore.getString(PolicyKeys.PIN_SALT, null).orEmpty()
        val hash = TvStore.getString(PolicyKeys.PIN_HASH, null).orEmpty()
        // Phase 0 briefly wrote 100k-iteration hashes; keep those readable.
        val iterations = TvStore.getInt(PolicyKeys.PIN_ITERATIONS, LEGACY_PIN_ITERATIONS)
        if (PolicyPin.verify(pin, salt, hash, iterations)) {
            TvStore.putInt(PolicyKeys.PIN_FAILED, 0)
            TvStore.putLong(PolicyKeys.PIN_LOCKOUT, 0L)
            TvStore.sync()
            return PinResult.OK
        }
        failed += 1
        if (failed >= MAX_FAILED) {
            TvStore.putInt(PolicyKeys.PIN_FAILED, 0)
            TvStore.putLong(PolicyKeys.PIN_LOCKOUT, now + LOCKOUT_MS)
            TvStore.sync()
            publish()
            return PinResult.LOCKED_OUT
        }
        TvStore.putInt(PolicyKeys.PIN_FAILED, failed)
        return PinResult.WRONG
    }

    /** The TV went off/standby: temporary unlock ends; the session itself is unchanged. */
    @Synchronized
    fun onScreenOff() {
        if (!_cloudActive.value) return
        bindingDeferred = false
        // Study mode is an in-memory child scope; standby always returns to the lock screen.
        if (studyMode) exitStudyMode()
        if (guardState == GuardStatus.PARENT_OVERRIDE) {
            guardState = GuardStatus.ENFORCING
            publish()
            notifyPolicyChanged()
        }
        // Do not wait for the next 30s report when the child stops watching.
        maybeReportUsage(force = true)
    }

    /** Parent chose "later" on the demo binding screen: show Home for this unlock. */
    @Synchronized
    fun deferBinding() {
        bindingDeferred = true
        publish()
    }

    /**
     * The backend reports device_list.is_enabled=1 for this deviceNo. Keep the
     * guard as it is: the caller is either on the binding screen (PAUSED) or
     * already on Home after "bind later".
     */
    @Synchronized
    fun onCloudBound() {
        bound = true
        cloudNeedsGrant = true
        bindingDeferred = false
        // A bound cloud TV is guarded until a grant starts; do not leave it on an unlocked Home.
        guardState = GuardStatus.ENFORCING
        TvStore.putBoolean(PolicyKeys.BOUND, true)
        TvStore.sync()
        publish()
        notifyPolicyChanged()
        requestSync()
    }

    /**
     * Startup sync found that the parent unbound this TV. PAUSED is memory-only;
     * locking here also covers the case where Home was shown through "bind later".
     */
    @Synchronized
    fun onCloudUnbound() {
        if (session.state == SessionState.ACTIVE && recordId > 0) {
            settleRecord(recordId, (session.totalSeconds - session.remainingSeconds).coerceAtLeast(0L))
        }
        bound = false
        cloudNeedsGrant = false
        bindingDeferred = false
        _cloudGrant.value = null
        TvStore.putBoolean(PolicyKeys.BOUND, false)
        clearSessionFields()
        clearRemotePolicyMirror()
        studyMode = false
        guardState = GuardStatus.ENFORCING
        saveSession()
        TvStore.sync()
        publish()
        notifyPolicyChanged()
    }

    /** A new owner must never inherit the previous binding's rule mirror. */
    private fun clearRemotePolicyMirror() {
        remotePolicySnapshot = null
        _remotePolicy.value = null
        RemotePolicyStore.clear()
        TvStore.remove(PolicyKeys.CLOUD_PIN_VERSION)
        TvStore.sync()
    }

    /** Demo path for offline UI testing; real binding goes through [onCloudBound]. */
    @Synchronized
    fun completeDemoBinding() {
        bound = true
        bindingDeferred = false
        TvStore.putBoolean(PolicyKeys.BOUND, true)
        TvStore.sync()
        guardState = GuardStatus.PARENT_OVERRIDE
        publish()
        notifyPolicyChanged()
    }

    @Synchronized
    fun debugUnbind() {
        if (session.state == SessionState.ACTIVE && recordId > 0) {
            settleRecord(recordId, (session.totalSeconds - session.remainingSeconds).coerceAtLeast(0L))
        }
        bound = false
        bindingDeferred = false
        TvStore.putBoolean(PolicyKeys.BOUND, false)
        clearSessionFields()
        clearRemotePolicyMirror()
        studyMode = false
        // Show the binding prototype immediately; the next Back/"later" goes Home.
        guardState = GuardStatus.PARENT_OVERRIDE
        saveSession()
        TvStore.sync()
        publish()
        notifyPolicyChanged()
        host?.bringHome()
    }

    /** Debug hooks, used by the debug-only broadcast receiver and the TV test harness. */
    @Synchronized
    fun debugPending(seconds: Long, userName: String = "小明") {
        clearSessionFields()
        session = PolicySession().pending(seconds.coerceAtLeast(1), userName)
        recordStarted = false
        guardState = GuardStatus.ENFORCING
        saveSession()
        publish()
        notifyPolicyChanged()
        host?.bringHome()
    }

    @Synchronized
    fun debugActive(seconds: Long, userName: String = "小明") {
        clearSessionFields()
        session = PolicySession()
            .pending(seconds.coerceAtLeast(1), userName)
            .start()
        recordStarted = true
        lastReportAt = SystemClock.elapsedRealtime()
        guardState = GuardStatus.ENFORCING
        saveSession()
        publish()
        notifyPolicyChanged()
        host?.bringHome()
    }

    @Synchronized
    fun debugLocked() {
        clearSessionFields()
        session = PolicySession()
        guardState = GuardStatus.ENFORCING
        saveSession()
        publish()
        notifyPolicyChanged()
        host?.bringHome()
    }

    @Synchronized
    fun debugScreenOff() = onScreenOff()

    /** Debug-only PIN override; ignored by the production path. */
    @Synchronized
    fun debugSetPin(pin: String) {
        setPin(pin)
        publish()
    }

    /**
     * Applies the cloud password to the local PIN. Called only while the cloud engine is active.
     *
     * Deliberately does not consume the PIN failure-lockout state: a backend password change is
     * an authoritative parent action and must not be blocked by failed attempts on the TV.
     * A default value only stays ignored before the V6 handoff, where legacy TVs may still carry
     * a seeded 123456 in `device_list.device_pass_word`. Once handoff is DONE the cloud is the
     * single source of truth, including a deliberate 123456/666666.
     */
    @Synchronized
    fun applyCloudPin(password: String?): CloudPinApplyResult {
        if (!_cloudActive.value || !bound) return CloudPinApplyResult.NOT_READY
        if (!TvStore.isReady) return CloudPinApplyResult.NOT_READY
        val pin = password?.trim().orEmpty()
        if (!TvPinStore.isValid(pin)) {
            Log.w(TAG, "ignore invalid cloud PIN")
            return CloudPinApplyResult.INVALID
        }
        if (!pinConfigured) {
            setPin(pin)
            publish()
            notifyPolicyChanged()
            emitVoice(PolicyVoiceCue.PIN_UPDATED)
            Log.i(TAG, "cloud PIN initialized")
            return CloudPinApplyResult.APPLIED
        }
        val salt = TvStore.getString(PolicyKeys.PIN_SALT, null).orEmpty()
        val hash = TvStore.getString(PolicyKeys.PIN_HASH, null).orEmpty()
        val iterations = TvStore.getInt(PolicyKeys.PIN_ITERATIONS, LEGACY_PIN_ITERATIONS)
        if (PolicyPin.verify(pin, salt, hash, iterations)) return CloudPinApplyResult.UNCHANGED
        if (TvPinStore.isDefault(pin) &&
            remotePolicySnapshot?.handoffState != RemoteHandoffState.DONE
        ) {
            Log.i(TAG, "ignore default cloud PIN before handoff")
            return CloudPinApplyResult.IGNORED
        }
        setPin(pin)
        publish()
        notifyPolicyChanged()
        emitVoice(PolicyVoiceCue.PIN_UPDATED)
        Log.i(TAG, "cloud PIN applied")
        return CloudPinApplyResult.APPLIED
    }

    /** Parent-center password change; both stores are written and both lockouts are cleared. */
    @Synchronized
    fun changePin(oldPin: String, newPin: String): Boolean {
        if (verifyPin(oldPin) != PinResult.OK) return false
        if (!TvPinStore.isValid(newPin)) return false
        setPin(newPin)
        publish()
        notifyPolicyChanged()
        return true
    }

    /** Used after a LOCAL-side write so the cloud runtime's cached pinConfigured is current. */
    @Synchronized
    fun reloadPinState() {
        readPin()
        publish()
    }

    fun isPasswordSyncPending(): Boolean = TvPinStore.pendingSync

    fun markPasswordSyncPending() = TvPinStore.markPendingSync()

    fun clearPasswordSyncPending() = TvPinStore.clearPendingSync()

    /** Opaque server fingerprint of the PIN last synchronized from the cloud. */
    fun cloudPinVersion(): String? =
        if (TvStore.isReady) TvStore.getString(PolicyKeys.CLOUD_PIN_VERSION, null) else null

    fun setCloudPinVersion(version: String) {
        if (!TvStore.isReady || version.isBlank()) return
        TvStore.putString(PolicyKeys.CLOUD_PIN_VERSION, version)
        TvStore.sync()
    }

    fun notifyPolicyChanged() {
        _revision.value = _revision.value + 1L
    }

    fun setTemporaryUnlock(enabled: Boolean, durationMs: Long = 0L) {
        guardState = if (enabled) GuardStatus.PARENT_OVERRIDE else GuardStatus.ENFORCING
        publish()
        notifyPolicyChanged()
    }

    fun clearRedeemed() {
        // V4 has no redeemed app set. Kept for the V3 call sites.
        notifyPolicyChanged()
    }

    /**
     * Stores the latest cloud policy mirror. A missing node or an old backend keeps the previous
     * snapshot; the mirror is read-only and does not change the running session.
     */
    @Synchronized
    fun onRemotePolicy(policy: RemotePolicy?) {
        val next = policy ?: return
        if (!_cloudActive.value) return
        if (remotePolicySnapshot == next) return
        remotePolicySnapshot = next
        RemotePolicyStore.save(next)
        _remotePolicy.value = next
        // The study list comes from this mirror even in redemption mode. Publish it so the
        // lock screen knows whether the fixed study entry can open, and reload the desktop
        // when the list changes underneath an active study scope.
        publish()
        if (studyMode) {
            if (next.rules.effectiveStudyPackages.isEmpty()) {
                exitStudyMode()
            } else {
                notifyPolicyChanged()
            }
        }
    }

    /**
     * Switches the redemption engine between owning the guard and feeding identity only.
     * Entering hosted mode drops a legacy PENDING gate (the local executor re-arms from the
     * same grant) but keeps the cached recipient icon for the lock screen.
     */
    @Synchronized
    fun setHostedRuleActive(active: Boolean) {
        if (hostedRuleActive == active) return
        hostedRuleActive = active
        if (active) {
            // The hosted executor owns the guard; its own study mode replaces this one.
            studyMode = false
            if (guardState == GuardStatus.STUDY_MODE) guardState = GuardStatus.ENFORCING
        }
        if (active && session.state == SessionState.PENDING) {
            // Do not settle: a pending redemption consumed no budget. Clear only the transient
            // gate fields; keep userIconUrl as identity for the hosted lock screen.
            session = PolicySession()
            recordId = 0
            recordStarted = false
            validUntil = ""
            lockReason = LockReason.NONE
            windowDeadlineElapsed = 0L
            accessMode = AccessMode.SCREEN
            allowedPackages = emptySet()
            accessPolicy = ScreenAccessPolicy
            lastSavedTick = 0L
            lastReportAt = 0L
            spokenThresholds.clear()
            saveSession()
        }
        publish()
    }

    /** Hosted arm brings the lock screen forward, matching the legacy grant flow. */
    fun bringHome() {
        host?.bringHome()
    }

    /**
     * Applies a cloud grant. null means the backend has no active record (settled, canceled
     * or expired), which is a successful answer, not a network failure.
     */
    @Synchronized
    fun onCloudConfig(config: PolicyConfig?) {
        if (!_cloudActive.value) return
        if (!bound) {
            _cloudGrant.value = null
            return
        }
        val usable = PolicyConfigRules.isUsable(config)
        if (PolicyFlowTrace.enabled()) {
            val traceKey = listOf(
                config?.recordId,
                config?.costSeconds,
                config?.validWindowSeconds,
                config?.userName?.isNotEmpty(),
                config?.userIconUrl?.isNotEmpty(),
                usable,
                hostedRuleActive,
                bound,
                session.state,
            ).joinToString("|")
            if (traceKey != lastCloudConfigTrace) {
                lastCloudConfigTrace = traceKey
                PolicyFlowTrace.event(
                    "grant.apply",
                    "recordId" to (config?.recordId ?: 0),
                    "status" to (config?.status ?: 0),
                    "total" to (config?.totalSeconds ?: 0L),
                    "cost" to (config?.costSeconds ?: 0L),
                    "remaining" to (config?.remainingSeconds ?: 0L),
                    "window" to config?.validWindowSeconds,
                    "usable" to usable,
                    "hosted" to hostedRuleActive,
                    "bound" to bound,
                    "session" to session.state,
                    "lock" to lockReason,
                    "user" to (config?.userName?.isNotEmpty() == true),
                    "icon" to (config?.userIconUrl?.isNotEmpty() == true),
                )
            }
        }
        _cloudGrant.value = config?.takeIf { usable }
        if (hostedRuleActive) {
            // The local rule executor owns the timer and the PENDING gate. Here the grant is
            // identity only: an exchange grant may replace the avatar, while a direct manual
            // dispatch carries no recipient and must leave the current avatar unchanged.
            val granted = config?.takeIf { usable }
            val icon = granted?.userIconUrl.orEmpty()
            val nextIcon = if (granted != null && icon.isEmpty()) userIconUrl else icon
            val label = granted?.validUntilLabel.orEmpty()
            if (userIconUrl != nextIcon || validUntil != label) {
                userIconUrl = nextIcon
                validUntil = label
                saveSession()
            }
            publish()
            return
        }
        if (!usable) {
            cloudNeedsGrant = true
            // A poll with no active record must not keep republishing/notifying the UI.
            // recordId == 0 is a debug-only local session; leave it alone for the test harness.
            if (recordId > 0 ||
                accessMode != AccessMode.SCREEN ||
                allowedPackages.isNotEmpty() ||
                validUntil.isNotEmpty()
            ) {
                val wasActive = session.state == SessionState.ACTIVE
                val wasPending = session.state == SessionState.PENDING
                clearCloudState(
                    settle = wasActive,
                    cue = when {
                        wasActive -> PolicyVoiceCue.TIME_UP
                        wasPending -> PolicyVoiceCue.CANCELED
                        else -> null
                    },
                    reason = when {
                        wasActive -> LockReason.TIME_UP
                        wasPending -> LockReason.CANCELED
                        else -> LockReason.NONE
                    },
                )
            }
            publish()
            return
        }
        val c = config ?: return
        cloudNeedsGrant = false
        val oldAccessMode = accessMode
        val oldAllowedPackages = allowedPackages
        applyAccess(c)
        if (oldAccessMode != accessMode || oldAllowedPackages != allowedPackages) {
            // The desktop must re-arrange when a mid-session grant changes the app list.
            notifyPolicyChanged()
        }
        userIconUrl = c.userIconUrl
        lockReason = LockReason.NONE

        if (c.recordId == recordId) {
            if (PolicyConfigRules.shouldResume(c, recordId, recordStarted)) {
                val wasActive = session.state == SessionState.ACTIVE
                val remaining = PolicyConfigRules.nextRemaining(
                    session.remainingSeconds,
                    c.remainingSeconds,
                )
                if (remaining <= 0) {
                    clearCloudState(
                        settle = true,
                        cue = PolicyVoiceCue.TIME_UP,
                        reason = LockReason.TIME_UP,
                    )
                    return
                }
                recordStarted = true
                session = PolicySession(SessionState.ACTIVE, c.totalSeconds, remaining, c.userName)
                validUntil = c.validUntilLabel
                updateWindow(c)
                if (!wasActive) {
                    lastReportAt = SystemClock.elapsedRealtime()
                    spokenThresholds.clear()
                }
                saveSession()
                publish()
                return
            }
            // Same record, not confirmed yet: refresh the gate with authoritative server data.
            val wasPending = session.state == SessionState.PENDING
            session = PolicySession()
                .pending(c.remainingSeconds, c.userName)
                .copy(totalSeconds = c.totalSeconds)
            recordStarted = false
            validUntil = c.validUntilLabel
            updateWindow(c)
            spokenThresholds.clear()
            saveSession()
            publish()
            if (!wasPending) {
                emitVoice(PolicyVoiceCue.GRANT_READY)
                if (guardState == GuardStatus.ENFORCING) host?.bringHome()
            }
            return
        }

        // A new record replaces the current one. Settle the previous ACTIVE record first.
        if (session.state == SessionState.ACTIVE && recordId > 0) {
            settleRecord(recordId, (session.totalSeconds - session.remainingSeconds).coerceAtLeast(0L))
        }
        recordId = c.recordId
        validUntil = c.validUntilLabel
        updateWindow(c)
        spokenThresholds.clear()
        if (c.costSeconds > 0) {
            // The record was already started elsewhere (or before a lost start flag).
            recordStarted = true
            session = PolicySession(SessionState.ACTIVE, c.totalSeconds, c.remainingSeconds, c.userName)
            lastReportAt = SystemClock.elapsedRealtime()
        } else {
            recordStarted = false
            session = PolicySession()
                .pending(c.remainingSeconds, c.userName)
                .copy(totalSeconds = c.totalSeconds)
        }
        saveSession()
        publish()
        notifyPolicyChanged()
        if (session.state == SessionState.PENDING) {
            emitVoice(PolicyVoiceCue.GRANT_READY)
            if (guardState == GuardStatus.ENFORCING) host?.bringHome()
        }
    }

    /** MQTT stop_session. Returns true when this TV owned the record and acted on it. */
    @Synchronized
    fun onRemoteStop(commandRecordId: Int): Boolean {
        if (!_cloudActive.value || hostedRuleActive) return false
        if (commandRecordId > 0 && commandRecordId != recordId) return false
        return when (session.state) {
            SessionState.ACTIVE -> {
                clearCloudState(
                    settle = true,
                    cue = PolicyVoiceCue.PARENT_STOP,
                    reason = LockReason.PARENT_STOP,
                )
                true
            }
            SessionState.PENDING -> {
                clearCloudState(
                    settle = false,
                    cue = PolicyVoiceCue.PARENT_STOP,
                    reason = LockReason.PARENT_STOP,
                )
                true
            }
            SessionState.IDLE -> false
        }
    }

    /** MQTT cancel_session (only valid before the timer starts). */
    @Synchronized
    fun onRemoteCancel(commandRecordId: Int): Boolean {
        if (!_cloudActive.value || hostedRuleActive) return false
        if (commandRecordId > 0 && commandRecordId != recordId) return false
        if (session.state == SessionState.IDLE) return false
        clearCloudState(
            settle = false,
            cue = PolicyVoiceCue.CANCELED,
            reason = LockReason.CANCELED,
        )
        return true
    }

    /**
     * Ends the cloud session before a mode switch. ACTIVE is settled synchronously; PENDING is
     * refused because the TV has no safe cancel path (the parent must cancel in uniapp first).
     * On success the cloud session is cleared; on failure the current session is left untouched.
     */
    suspend fun endCloudSessionForSwitch(): Result<Unit> {
        var currentState: SessionState = SessionState.IDLE
        var currentRecordId: Int = 0
        var duration: Long = 0L
        var reportHost: PolicySyncHost? = null
        var reportDevice: String = ""
        synchronized(this) {
            if (!_cloudActive.value) return Result.success(Unit)
            currentState = session.state
            currentRecordId = recordId
            duration = (session.totalSeconds - session.remainingSeconds).coerceAtLeast(0L)
            reportHost = syncHost
            reportDevice = deviceNo
        }
        if (currentState == SessionState.IDLE) return Result.success(Unit)
        if (currentState == SessionState.PENDING) {
            return Result.failure(IllegalStateException("cloud pending must be canceled first"))
        }
        val host = reportHost
        if (currentRecordId > 0) {
            if (host == null || reportDevice.isBlank()) {
                return Result.failure(IllegalStateException("cloud settlement is unavailable"))
            }
            val settled = host.reportUsage(reportDevice, currentRecordId, duration, timeout = true)
            if (settled.isFailure) return settled
        }
        synchronized(this) {
            if (session.state == currentState && recordId == currentRecordId) {
                clearSessionFields()
                lockReason = LockReason.NONE
                guardState = GuardStatus.ENFORCING
                saveSession()
                publish()
                notifyPolicyChanged()
                host()?.bringHome()
            }
        }
        return Result.success(Unit)
    }

    /** Trigger an immediate config fetch; safe to call from screen-on and MQTT events. */
    fun requestSync() {
        if (!_cloudActive.value || syncHost == null) return
        PolicyFlowTrace.event(
            "config.sync.request",
            "inFlight" to syncInFlight.get(),
            "dirty" to syncDirty,
        )
        syncDirty = true
        scope.launch { fetchConfigNow() }
    }

    private fun startSyncLocked() {
        if (!_cloudActive.value || syncJob != null || syncHost == null) return
        syncJob = scope.launch {
            while (isActive) {
                val state = _state.value
                val base = when {
                    !state.bound -> SYNC_UNBOUND_MS
                    state.sessionState == SessionState.PENDING -> SYNC_PENDING_MS
                    state.sessionState == SessionState.ACTIVE -> SYNC_ACTIVE_MS
                    else -> SYNC_IDLE_MS
                }
                val backoff = if (syncFailures == 0) {
                    base
                } else {
                    minOf(SYNC_MAX_MS, base * (1L shl syncFailures.coerceAtMost(4)))
                }
                delay(backoff)
                val ok = fetchConfigNow()
                syncFailures = if (ok) 0 else (syncFailures + 1).coerceAtMost(4)
            }
        }
    }

    private suspend fun fetchConfigNow(): Boolean {
        if (!_cloudActive.value) return true
        val currentHost = syncHost ?: return true
        val current = _state.value
        if (!current.bound || current.deviceNo.isBlank()) return true
        if (!syncInFlight.compareAndSet(false, true)) return true
        var success = true
        try {
            do {
                // Consume the pending event before fetching: an event that arrives during the
                // request sets it again and makes this loop fetch one more time.
                syncDirty = false
                val result = currentHost.fetchConfig(current.deviceNo)
                result.onSuccess { snapshot ->
                    onRemotePolicy(snapshot.remotePolicy)
                    onCloudConfig(snapshot.grant)
                }.onFailure { Log.w(TAG, "config fetch failed", it) }
                if (result.isSuccess) flushPendingSettlement()
                success = result.isSuccess
            } while (syncDirty)
        } finally {
            syncInFlight.set(false)
            // Close the check/release window: an event that landed between the loop condition
            // and this release still gets one follow-up fetch.
            if (syncDirty) scope.launch { fetchConfigNow() }
        }
        return success
    }

    @Synchronized
    private fun onTick() {
        if (!_cloudActive.value) return
        // The local executor owns countdown, rest, screen pause and thresholds in hosted mode.
        if (hostedRuleActive) return
        val now = SystemClock.elapsedRealtime()
        if (session.state == SessionState.ACTIVE &&
            windowDeadlineElapsed > 0 &&
            now >= windowDeadlineElapsed
        ) {
            clearCloudState(
                settle = true,
                cue = PolicyVoiceCue.TIME_UP,
                reason = LockReason.TIME_UP,
            )
            return
        }

        val nowInteractive = appContext
            ?.getSystemService(Context.POWER_SERVICE)
            ?.let { (it as? PowerManager)?.isInteractive }
            ?: true

        if (!nowInteractive) {
            // Some TV firmwares do not deliver ACTION_SCREEN_OFF; polling covers it.
            if (studyMode) exitStudyMode()
            if (guardState == GuardStatus.PARENT_OVERRIDE) {
                guardState = GuardStatus.ENFORCING
                publish()
                notifyPolicyChanged()
            }
            return
        }

        if (session.state != SessionState.ACTIVE) return
        val next = session.tick(interactive = true)
        session = next
        if (next.state != SessionState.ACTIVE) {
            clearCloudState(
                settle = true,
                cue = PolicyVoiceCue.TIME_UP,
                reason = LockReason.TIME_UP,
            )
        } else {
            lastSavedTick += 1
            if (lastSavedTick >= SAVE_EVERY_TICKS) {
                lastSavedTick = 0
                saveSession()
            }
            maybeVoiceThreshold(next.remainingSeconds)
            maybeReportUsage(force = false)
            publish()
        }
    }

    /** The phone app's reminder set: 60/30/20/10/5 seconds, once per session. */
    private fun maybeVoiceThreshold(remaining: Long) {
        val cue = when (remaining) {
            60L -> PolicyVoiceCue.ONE_MINUTE
            30L -> PolicyVoiceCue.THIRTY_SECONDS
            20L -> PolicyVoiceCue.TWENTY_SECONDS
            10L -> PolicyVoiceCue.TEN_SECONDS
            5L -> PolicyVoiceCue.FIVE_SECONDS
            else -> return
        }
        if (spokenThresholds.add(remaining)) emitVoice(cue)
    }

    private fun emitVoice(cue: PolicyVoiceCue) {
        _voiceCues.tryEmit(cue)
        PolicyVoiceBus.emit(cue)
    }

    private fun maybeReportUsage(force: Boolean) {
        if (!_cloudActive.value || session.state != SessionState.ACTIVE || recordId <= 0 || deviceNo.isBlank()) return
        val currentHost = syncHost ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastReportAt < REPORT_INTERVAL_MS) return
        if (!reportInFlight.compareAndSet(false, true)) return
        lastReportAt = now
        val duration = (session.totalSeconds - session.remainingSeconds).coerceAtLeast(0L)
        val reportRecordId = recordId
        val reportDeviceNo = deviceNo
        scope.launch {
            currentHost.reportUsage(reportDeviceNo, reportRecordId, duration, timeout = false)
                .onFailure { Log.w(TAG, "usage report failed", it) }
            reportInFlight.set(false)
        }
    }

    private fun settleRecord(settleRecordId: Int, durationSeconds: Long) {
        if (settleRecordId <= 0 || syncHost == null || deviceNo.isBlank()) return
        TvStore.putInt(PolicyKeys.PENDING_SETTLE_ID, settleRecordId)
        TvStore.putLong(PolicyKeys.PENDING_SETTLE_SECONDS, durationSeconds.coerceAtLeast(0L))
        TvStore.sync()
        scope.launch { flushPendingSettlement() }
    }

    private suspend fun flushPendingSettlement() {
        val pendingId = TvStore.getInt(PolicyKeys.PENDING_SETTLE_ID, 0)
        if (pendingId <= 0) return
        val pendingHost = syncHost ?: return
        val currentDeviceNo = deviceNo
        if (currentDeviceNo.isBlank()) return
        val duration = TvStore.getLong(PolicyKeys.PENDING_SETTLE_SECONDS, 0L)
        pendingHost.reportUsage(currentDeviceNo, pendingId, duration, timeout = true)
            .onSuccess { clearPendingSettlement(pendingId) }
            .onFailure { Log.w(TAG, "settlement retry failed", it) }
    }

    private fun clearPendingSettlement(pendingId: Int) {
        if (TvStore.getInt(PolicyKeys.PENDING_SETTLE_ID, 0) != pendingId) return
        TvStore.remove(PolicyKeys.PENDING_SETTLE_ID)
        TvStore.remove(PolicyKeys.PENDING_SETTLE_SECONDS)
        TvStore.sync()
    }

    private fun applyAccess(config: PolicyConfig) {
        accessMode = config.accessMode
        allowedPackages = config.allowedPackages
        accessPolicy = if (config.accessMode == AccessMode.APPS) {
            AppAccessPolicy(redeemedAllowlist = config.allowedPackages)
        } else {
            ScreenAccessPolicy
        }
    }

    private fun updateWindow(config: PolicyConfig) {
        val seconds = config.validWindowSeconds
        windowDeadlineElapsed = if (seconds != null && seconds > 0) {
            SystemClock.elapsedRealtime() + seconds * 1_000L
        } else {
            0L
        }
    }

    /** Ends the current cloud session, optionally settling it, and locks the TV. */
    @Synchronized
    private fun clearCloudState(
        settle: Boolean,
        cue: PolicyVoiceCue? = null,
        reason: LockReason = LockReason.NONE,
    ) {
        val oldRecordId = recordId
        val oldDuration = (session.totalSeconds - session.remainingSeconds).coerceAtLeast(0L)
        clearSessionFields()
        cloudNeedsGrant = bound
        _cloudGrant.value = null
        lockReason = reason
        // Cancelling/stopping a pending grant must not throw the child out of study mode:
        // study is not a session and owns no grant. No ACTIVE session can coexist with study.
        guardState = if (studyMode) GuardStatus.STUDY_MODE else GuardStatus.ENFORCING
        saveSession()
        publish()
        notifyPolicyChanged()
        if (settle && oldRecordId > 0) {
            settleRecord(oldRecordId, oldDuration)
        }
        cue?.let(::emitVoice)
        host?.bringHome()
    }

    private fun clearSessionFields() {
        session = PolicySession()
        recordId = 0
        recordStarted = false
        validUntil = ""
        userIconUrl = ""
        lockReason = LockReason.NONE
        windowDeadlineElapsed = 0L
        accessMode = AccessMode.SCREEN
        allowedPackages = emptySet()
        accessPolicy = ScreenAccessPolicy
        lastSavedTick = 0L
        lastReportAt = 0L
        spokenThresholds.clear()
    }

    private fun registerScreenReceiver(context: Context) {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_SCREEN_ON -> {
                        onTick()
                        requestSync()
                    }
                }
            }
        }
        screenReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun readSession() {
        if (!TvStore.isReady) return
        val stateName = TvStore.getString(PolicyKeys.STATE, SessionState.IDLE.name)
            ?.let { runCatching { SessionState.valueOf(it) }.getOrNull() }
            ?: SessionState.IDLE
        val total = TvStore.getLong(PolicyKeys.TOTAL, 0L)
        val remaining = TvStore.getLong(PolicyKeys.REMAINING, 0L)
        val user = TvStore.getString(PolicyKeys.USER, "").orEmpty()
        bound = TvStore.getBoolean(PolicyKeys.BOUND, false)
        recordId = TvStore.getInt(PolicyKeys.RECORD_ID, 0)
        cloudNeedsGrant = bound && recordId == 0
        recordStarted = TvStore.getBoolean(PolicyKeys.RECORD_STARTED, false)
        accessMode = TvStore.getString(PolicyKeys.ACCESS_MODE, AccessMode.SCREEN.name)
            ?.let { runCatching { AccessMode.valueOf(it) }.getOrNull() }
            ?: AccessMode.SCREEN
        allowedPackages = TvStore.getStringSet(PolicyKeys.ALLOWED_PACKAGES)
        accessPolicy = if (accessMode == AccessMode.APPS) {
            AppAccessPolicy(redeemedAllowlist = allowedPackages)
        } else {
            ScreenAccessPolicy
        }
        accessBlocklist = emptySet()
        validUntil = TvStore.getString(PolicyKeys.VALID_UNTIL, "").orEmpty()
        userIconUrl = TvStore.getString(PolicyKeys.USER_ICON, "").orEmpty()
        lockReason = LockReason.NONE
        spokenThresholds.clear()
        bindingDeferred = false
        session = when {
            stateName == SessionState.ACTIVE && remaining > 0 ->
                PolicySession(SessionState.ACTIVE, total, remaining, user)
            stateName == SessionState.PENDING && remaining > 0 ->
                PolicySession(SessionState.PENDING, total, remaining, user)
            else -> PolicySession()
        }
        // Normalize the saved pair so a restart never turns an already-started session
        // back into a gate, or leaves a confirmed PENDING record behind.
        if (recordStarted && session.state == SessionState.PENDING && session.remainingSeconds > 0) {
            session = session.start()
        } else if (!recordStarted && session.state == SessionState.ACTIVE) {
            recordStarted = true
        }
        lastReportAt = SystemClock.elapsedRealtime()
        // PAUSED is intentionally in-memory only: a process start always locks
        // a temporary unlock again.
        guardState = GuardStatus.ENFORCING
    }

    private fun readPin() {
        if (!TvStore.isReady) return
        pinConfigured = !TvStore.getString(PolicyKeys.PIN_SALT, null).isNullOrEmpty() &&
            !TvStore.getString(PolicyKeys.PIN_HASH, null).isNullOrEmpty()
    }

    /**
     * A random per-install identity is what the parent scans.
     *
     * It must not be derived from Android ID or another value that survives clearing app data:
     * the v2 device token lives in app storage, so after a data clear the old ACTIVE credential
     * can no longer be proven. A new random id lets that install enroll again as a new device
     * instead of being permanently locked out. Existing ids are kept so an update does not
     * orphan a binding.
     */
    private fun readDeviceNo() {
        if (!TvStore.isReady) return
        val stored = TvStore.getString(PolicyKeys.DEVICE_NO, null).orEmpty()
        deviceNo = stored.ifBlank { UUID.randomUUID().toString() }
        if (stored != deviceNo) {
            TvStore.putString(PolicyKeys.DEVICE_NO, deviceNo)
            TvStore.sync()
        }
    }

    private fun readRemotePolicy() {
        if (!TvStore.isReady) return
        remotePolicySnapshot = RemotePolicyStore.load()
        _remotePolicy.value = remotePolicySnapshot
    }

    private fun setPin(pin: String) {
        if (!TvStore.isReady || !TvPinStore.isValid(pin)) return
        TvPinStore.set(pin)
        pinConfigured = true
    }

    private fun saveSession() {
        if (!TvStore.isReady) return
        TvStore.putString(PolicyKeys.STATE, session.state.name)
        TvStore.putLong(PolicyKeys.TOTAL, session.totalSeconds)
        TvStore.putLong(PolicyKeys.REMAINING, session.remainingSeconds)
        TvStore.putString(PolicyKeys.USER, session.userName)
        TvStore.putInt(PolicyKeys.RECORD_ID, recordId)
        TvStore.putBoolean(PolicyKeys.RECORD_STARTED, recordStarted)
        TvStore.putString(PolicyKeys.ACCESS_MODE, accessMode.name)
        TvStore.putStringSet(PolicyKeys.ALLOWED_PACKAGES, allowedPackages)
        TvStore.putString(PolicyKeys.VALID_UNTIL, validUntil)
        TvStore.putString(PolicyKeys.USER_ICON, userIconUrl)
    }

    /** Phone parity: effective remaining = min(points remaining, physical window remaining). */
    private fun effectiveRemainingSeconds(): Long {
        val points = session.remainingSeconds.coerceAtLeast(0L)
        val window = if (windowDeadlineElapsed <= 0L) {
            null
        } else {
            ((windowDeadlineElapsed - SystemClock.elapsedRealtime()) / 1_000L)
                .coerceAtLeast(0L)
        }
        return PolicyConfigRules.effectiveRemaining(points, window)
    }

    private fun publish() {
        val packages = studyPackages()
        val studyActive = isStudyMode()
        _state.value = PolicyUiState(
            loaded = loaded,
            sessionState = session.state,
            guardState = guardState,
            totalSeconds = session.totalSeconds,
            remainingSeconds = session.remainingSeconds,
            effectiveRemainingSeconds = effectiveRemainingSeconds(),
            userName = session.userName,
            pinConfigured = pinConfigured,
            bound = bound,
            cloudNeedsGrant = cloudNeedsGrant,
            deviceNo = deviceNo,
            bindingDeferred = bindingDeferred,
            // Study replaces the desktop scope with the study list; the grant itself is kept
            // untouched underneath so leaving study returns to the same lock screen.
            accessMode = if (studyActive) AccessMode.APPS else accessMode,
            allowedPackages = if (studyActive) packages else allowedPackages,
            validUntil = validUntil,
            userIconUrl = userIconUrl,
            lockReason = lockReason,
            studyModeAvailable = packages.isNotEmpty(),
            studyModeActive = studyActive,
            studyPackages = packages,
        )
    }
}
