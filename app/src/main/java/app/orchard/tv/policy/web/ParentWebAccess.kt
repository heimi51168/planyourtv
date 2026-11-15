package app.orchard.tv.policy.web

import android.content.Context
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.PolicyProduct
import app.orchard.tv.quiz.QuizAccess
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyRuntime

/**
 * Access gate for the web parent center.
 *
 * V29 makes the web page the only local configuration surface. It is available exactly while
 * the TV is in LOCAL mode and not bound to the cloud; the page login is the normal quiz parent
 * PIN, so there is no temporary-unlock proof, pairing code, or second password. Remote bound
 * TVs are configured by the phone app, and child mode off has nothing to configure.
 *
 * The single-editor lease still serializes two phones editing the same page. It never guards
 * the TV: the TV parent center is read-only, does not acquire the lease, and may stay open
 * while a phone configures the TV.
 */
object ParentWebAccess {
    enum class GateStatus { READY, WEB_LOCAL_ONLY, NOT_READY }

    /** Read-only state used by the page. Contains no secret. */
    data class Gate(
        val status: GateStatus,
        val mode: PolicyMode,
        val reason: String,
        val webAvailable: Boolean,
        val quizAdminRequired: Boolean,
    )

    sealed interface AcquireOutcome {
        data class Ok(
            val editorToken: String,
            val expiresAtMs: Long,
            val quizBound: Boolean,
        ) : AcquireOutcome

        data class Rejected(val code: String) : AcquireOutcome
    }

    sealed interface EditorCheck {
        data class Ok(val remainingMs: Long) : EditorCheck
        data class Failed(val code: String) : EditorCheck
    }

    private val changePinLimiter = AttemptLimiter(5, 10 * 60_000L)
    private var currentBoundQuizFingerprint: String? = null

    @Suppress("UNUSED_PARAMETER")
    fun install(context: Context) {
        // Kept as a hook so PolicyProduct has one install surface; the gate is stateless
        // apart from its in-memory limiters and the current quiz binding.
    }

    @Synchronized
    fun stop() {
        changePinLimiter.reset()
        currentBoundQuizFingerprint = null
        ParentSensitiveAuth.invalidate()
    }

    /** The one rule that decides whether the web parent center may be used at all. */
    fun webAllowed(): Boolean {
        val ui = PolicyModeCoordinator.state.value
        return ui.loaded &&
            PolicyModeCoordinator.mode == PolicyMode.LOCAL &&
            !isRemoteBound()
    }

    private fun isRemoteBound(): Boolean =
        PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound

    /** Stable machine reason; the page owns the five-language copy for each value. */
    fun blockedReason(): String = when {
        !PolicyModeCoordinator.state.value.loaded -> "NOT_READY"
        isRemoteBound() -> "REMOTE_BOUND"
        PolicyModeCoordinator.mode == PolicyMode.CLOUD -> "REMOTE_MODE"
        PolicyModeCoordinator.mode == PolicyMode.NONE -> "LOCAL_MODE_REQUIRED"
        else -> "REMOTE_BOUND"
    }

    fun gate(): Gate {
        val mode = PolicyModeCoordinator.mode
        val allowed = webAllowed()
        val reason = if (allowed) "OK" else blockedReason()
        return Gate(
            status = when {
                reason == "NOT_READY" -> GateStatus.NOT_READY
                allowed -> GateStatus.READY
                else -> GateStatus.WEB_LOCAL_ONLY
            },
            mode = mode,
            reason = reason,
            webAvailable = allowed,
            quizAdminRequired = allowed,
        )
    }

    /**
     * Exchanges a logged-in quiz session for the single-editor lease. A second phone may take
     * over explicitly (the shared parent PIN already proved it is a parent); otherwise it gets
     * WEB_EDITING and the page offers the takeover button.
     */
    @Synchronized
    fun acquire(quizAdminToken: String?, takeover: Boolean): AcquireOutcome {
        if (!webAllowed()) return AcquireOutcome.Rejected("WEB_LOCAL_ONLY")
        val token = quizAdminToken.orEmpty()
        if (!QuizAccess.adminValid(token)) {
            return AcquireOutcome.Rejected("QUIZ_LOGIN_REQUIRED")
        }
        val fingerprint = QuizAccess.fingerprint(token)
        // A second phone may take over explicitly; the previous page's next request fails.
        if (takeover) ParentEditorLease.invalidateWeb("takeover")
        return when (val lease = ParentEditorLease.acquireWeb()) {
            is ParentEditorLeaseEngine.AcquireResult.Ok -> {
                currentBoundQuizFingerprint = fingerprint
                AcquireOutcome.Ok(lease.token, lease.expiresAtMs, quizBound = true)
            }
            ParentEditorLeaseEngine.AcquireResult.WebEditing ->
                AcquireOutcome.Rejected("WEB_EDITING")
        }
    }

    /**
     * Every protected request goes through this: availability, lease TTL, and the quiz binding.
     * A binding or mode change invalidates the page even if its lease has not expired yet.
     */
    @Synchronized
    fun checkEditor(editorToken: String?, quizAdminToken: String?): EditorCheck {
        if (!webAllowed()) {
            ParentEditorLease.invalidateWeb("web_not_available")
            currentBoundQuizFingerprint = null
            ParentSensitiveAuth.invalidate()
            return EditorCheck.Failed("WEB_LOCAL_ONLY")
        }
        if (!ParentWebCrypto.isToken(editorToken)) return EditorCheck.Failed("EDITOR_REQUIRED")
        val digest = ParentWebCrypto.sha256Hex(editorToken.orEmpty())
        val bound = currentBoundQuizFingerprint
        if (bound == null || !QuizAccess.adminValid(quizAdminToken.orEmpty()) ||
            QuizAccess.fingerprint(quizAdminToken.orEmpty()) != bound
        ) {
            ParentEditorLease.invalidateWeb("quiz_takeover")
            currentBoundQuizFingerprint = null
            ParentSensitiveAuth.invalidate()
            return EditorCheck.Failed("QUIZ_TAKEN_OVER")
        }
        return when (val heartbeat = ParentEditorLease.heartbeat(digest)) {
            is ParentEditorLeaseEngine.HeartbeatResult.Ok ->
                EditorCheck.Ok(heartbeat.remainingMs)
            ParentEditorLeaseEngine.HeartbeatResult.Expired -> {
                currentBoundQuizFingerprint = null
                ParentSensitiveAuth.invalidate()
                EditorCheck.Failed("LEASE_EXPIRED")
            }
            ParentEditorLeaseEngine.HeartbeatResult.TakenOver -> {
                currentBoundQuizFingerprint = null
                ParentSensitiveAuth.invalidate()
                EditorCheck.Failed("LEASE_TAKEN_OVER")
            }
        }
    }

    @Synchronized
    fun release(editorToken: String?) {
        if (!ParentWebCrypto.isToken(editorToken)) return
        ParentEditorLease.releaseWeb(ParentWebCrypto.sha256Hex(editorToken.orEmpty()))
        currentBoundQuizFingerprint = null
    }

    @Synchronized
    fun invalidate(reason: String) {
        ParentEditorLease.invalidateWeb(reason)
        currentBoundQuizFingerprint = null
        ParentSensitiveAuth.invalidate()
    }

    @Synchronized
    fun changePinLockSeconds(clientIp: String): Long = changePinLimiter.lockedSeconds(clientIp)

    @Synchronized
    fun onChangePinAttempt(clientIp: String, success: Boolean, wrongOldPin: Boolean = false) {
        when {
            success -> changePinLimiter.onSuccess(clientIp)
            wrongOldPin -> changePinLimiter.onFailure(clientIp)
        }
    }
}

/**
 * Small in-memory failure limiter keyed by client address. A single noisy device must not lock
 * the whole LAN out, so the old-PIN gate uses per-IP keys; the global cap below bounds total
 * damage.
 */
internal class AttemptLimiter(
    private val maxFailures: Int,
    private val lockMs: Long,
) {
    private val failures = HashMap<String, Int>()
    private val lockedUntil = HashMap<String, Long>()

    @Synchronized
    fun lockedSeconds(key: String, nowMs: Long = System.currentTimeMillis()): Long {
        val until = lockedUntil[key] ?: return 0L
        if (until <= nowMs) {
            lockedUntil.remove(key)
            failures.remove(key)
            return 0L
        }
        return ((until - nowMs + 999) / 1000).coerceAtLeast(1L)
    }

    @Synchronized
    fun onFailure(key: String, nowMs: Long = System.currentTimeMillis()) {
        if (lockedUntil[key]?.let { it > nowMs } == true) return
        val next = (failures[key] ?: 0) + 1
        if (next >= maxFailures) {
            failures.remove(key)
            lockedUntil[key] = nowMs + lockMs
            if (lockedUntil.size > 128) {
                val first = lockedUntil.keys.firstOrNull()
                if (first != null) lockedUntil.remove(first)
            }
        } else {
            failures[key] = next
            if (failures.size > 256) failures.clear()
        }
    }

    @Synchronized
    fun lockOut(key: String, nowMs: Long = System.currentTimeMillis()) {
        failures.remove(key)
        lockedUntil[key] = nowMs + lockMs
    }

    @Synchronized
    fun onSuccess(key: String) {
        failures.remove(key)
        lockedUntil.remove(key)
    }

    @Synchronized
    fun reset() {
        failures.clear()
        lockedUntil.clear()
    }
}
