package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The single executor for both lock-surface edges; see LockSurfacePolicy.kt. */
class LockSurfaceRelayTest {
    private var inFront = false
    private var recentlyBroughtBack = false
    private var broughtHome = 0

    private fun state(
        reason: LockReason = LockReason.NONE,
        guard: GuardStatus = GuardStatus.ENFORCING,
        session: SessionState = SessionState.IDLE,
    ) = PolicyUiState(
        loaded = true,
        mode = PolicyMode.CLOUD,
        sessionState = session,
        guardState = guard,
        lockReason = reason,
    )

    private fun relay(initial: PolicyUiState) = LockSurfaceRelay(
        initialState = initial,
        isAerialInFront = { inFront },
        broughtBackRecently = { recentlyBroughtBack },
        bringHome = { broughtHome++ },
    )

    private val overridden = state(guard = GuardStatus.PARENT_OVERRIDE)
    private val enforced = state(LockReason.MANUAL_WAITING)

    @Test
    fun bringsHomeOncePerRestoreEdge() {
        val relay = relay(overridden)
        assertEquals(LockSurfaceEdge.GUARD_RESTORED, relay.onState(enforced))
        assertEquals(1, broughtHome)
        // The ticker re-publishes the same enforced state; that is not a new edge.
        assertNull(relay.onState(enforced))
        assertEquals(1, broughtHome)
    }

    @Test
    fun aLaterRestoreIsANewEdge() {
        val relay = relay(overridden)
        relay.onState(enforced)
        assertNull(relay.onState(overridden))
        assertEquals(LockSurfaceEdge.GUARD_RESTORED, relay.onState(enforced))
        assertEquals(2, broughtHome)
    }

    @Test
    fun sessionEndUsesTheSameRelay() {
        val relay = relay(state(session = SessionState.ACTIVE))
        assertEquals(
            LockSurfaceEdge.SESSION_ENDED,
            relay.onState(state(LockReason.TIME_UP)),
        )
        assertEquals(1, broughtHome)
    }

    @Test
    fun aerialAlreadyInFrontSkipsTheStart() {
        inFront = true
        val relay = relay(overridden)
        assertNull(relay.onState(enforced))
        assertEquals(0, broughtHome)
    }

    @Test
    fun aRecentBringBackSkipsTheStart() {
        recentlyBroughtBack = true
        val relay = relay(overridden)
        assertNull(relay.onState(enforced))
        assertEquals(0, broughtHome)
    }

    @Test
    fun aSuppressedEdgeDoesNotFireOnTheNextTick() {
        inFront = true
        val relay = relay(overridden)
        assertNull(relay.onState(enforced))
        inFront = false
        // The ticker republishes the same enforced state; the edge was already consumed.
        assertNull(relay.onState(enforced))
        assertEquals(0, broughtHome)
    }

    @Test
    fun unrelatedTransitionsDoNothing() {
        val relay = relay(state(LockReason.TIME_UP))
        assertNull(relay.onState(state(LockReason.MANUAL_WAITING)))
        assertEquals(0, broughtHome)
    }
}
