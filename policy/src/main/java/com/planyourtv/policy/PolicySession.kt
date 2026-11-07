package com.planyourtv.policy

/** The device-level session shown by the TV lock screen. */
enum class SessionState {
    IDLE,

    /** Legacy cloud redemption only: a grant was issued but the child has not started it. */
    @Deprecated("V10 manual sessions start immediately; this exists only for the V9 redemption adapter")
    PENDING,
    ACTIVE,
}

/**
 * The access state is independent of the session:
 *
 * - ENFORCING follows [SessionState]: IDLE/PENDING show the lock screen, ACTIVE shows Home.
 * - PARENT_OVERRIDE is the parent's temporary use; it ends on screen-off, timeout or restart.
 * - STUDY_MODE only exposes the study list; it has no timer, ledger or plan.
 */
enum class GuardStatus {
    ENFORCING,
    STUDY_MODE,
    PARENT_OVERRIDE,
}

enum class PinResult {
    OK,
    WRONG,
    LOCKED_OUT,
    NOT_SET,
}

/**
 * Pure device-level session state. Time is advanced by one second at a time only while the
 * screen is interactive; the runtime owns the clock and persistence.
 */
data class PolicySession(
    val state: SessionState = SessionState.IDLE,
    val totalSeconds: Long = 0,
    val remainingSeconds: Long = 0,
    val userName: String = "",
) {
    /** A granted session that the child has not started yet. */
    fun pending(totalSeconds: Long, userName: String): PolicySession =
        copy(
            state = SessionState.PENDING,
            totalSeconds = totalSeconds.coerceAtLeast(0),
            remainingSeconds = totalSeconds.coerceAtLeast(0),
            userName = userName,
        )

    /** The child pressed "开始观看". Only a granted PENDING session can start. */
    fun start(): PolicySession =
        if (state == SessionState.PENDING && remainingSeconds > 0) {
            copy(state = SessionState.ACTIVE)
        } else {
            this
        }

    /** Advance one second. A non-interactive screen does not consume quota. */
    fun tick(interactive: Boolean): PolicySession {
        if (!interactive || state != SessionState.ACTIVE || remainingSeconds <= 0) return this
        val next = remainingSeconds - 1
        return if (next <= 0) {
            copy(state = SessionState.IDLE, remainingSeconds = 0)
        } else {
            copy(remainingSeconds = next)
        }
    }

    fun lock(): PolicySession = copy(state = SessionState.IDLE, remainingSeconds = 0)

    /** A completed session has no pending grant. */
    fun clear(): PolicySession = PolicySession()
}
