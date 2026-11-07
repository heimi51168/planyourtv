package com.planyourtv.policy

/**
 * Why Aerial must bring its lock surface forward. The lock is a normal Compose surface
 * inside Aerial, not an overlay window, so a state change alone does not show it while
 * another app is in front.
 */
enum class LockSurfaceEdge {
    /** The running session ended; the ticker published a lock reason. */
    SESSION_ENDED,

    /** The parent's temporary override ended: remote restore, timed expiry or local center. */
    GUARD_RESTORED,
}

/**
 * The single decision for every lock-surface edge. Returns null when the transition is not
 * one of them. Mode switches, cross-day rollovers and deliberate parent unlocks stay quiet.
 *
 * The caller passes consecutive [PolicyUiState] emissions; a session end and a restored
 * guard are the two transitions that make the lock due while the child may be in an app.
 */
fun lockSurfaceEdge(previous: PolicyUiState?, current: PolicyUiState): LockSurfaceEdge? {
    if (previous == null) return null
    // No lock surface can be due while unguarded, unconfigured, unlocked or in a session.
    if (current.mode == PolicyMode.NONE || current.setupRequired) return null
    if (current.guardState != GuardStatus.ENFORCING) return null
    if (current.sessionState == SessionState.ACTIVE) return null

    if (previous.sessionState == SessionState.ACTIVE) {
        // A session end with no lock reason is a mode switch, rollover or parent takeover.
        return if (current.lockReason != LockReason.NONE) LockSurfaceEdge.SESSION_ENDED else null
    }
    if (previous.guardState == GuardStatus.PARENT_OVERRIDE) {
        // A restored guard may land on the PENDING confirm gate, whose lock reason is NONE.
        return LockSurfaceEdge.GUARD_RESTORED
    }
    return null
}

/**
 * Tracks consecutive [PolicyUiState] emissions and calls [bringHome] once per lock-surface
 * edge. It is the one place that decides whether the guard must front Aerial; hosts only
 * supply the current state, the "already visible" check, the shared bring-back debounce and
 * the actual Home action.
 *
 * Not thread-safe: call [onState] from a single state collector. [initialState] is the state
 * at the moment the collector starts, so the first emission is compared rather than counted
 * as an edge by itself.
 */
class LockSurfaceRelay(
    initialState: PolicyUiState,
    private val isAerialInFront: () -> Boolean,
    private val broughtBackRecently: () -> Boolean,
    private val bringHome: () -> Unit,
) {
    private var previous: PolicyUiState = initialState

    /** @return the edge that brought Aerial forward, or null when nothing was done. */
    fun onState(current: PolicyUiState): LockSurfaceEdge? {
        val edge = lockSurfaceEdge(previous, current)
        // Advance even when the edge is suppressed: a skipped edge must not fire later.
        previous = current
        if (edge == null) return null
        if (isAerialInFront()) return null
        if (broughtBackRecently()) return null
        bringHome()
        return edge
    }
}
