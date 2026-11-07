package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The two edges that make Aerial's lock surface due; see LockSurfacePolicy.kt. */
class LockSurfacePolicyTest {
    private fun state(
        reason: LockReason = LockReason.NONE,
        mode: PolicyMode = PolicyMode.LOCAL,
        guard: GuardStatus = GuardStatus.ENFORCING,
        session: SessionState = SessionState.IDLE,
        setupRequired: Boolean = false,
    ) = PolicyUiState(
        loaded = true,
        mode = mode,
        sessionState = session,
        guardState = guard,
        lockReason = reason,
        setupRequired = setupRequired,
    )

    private fun running(reason: LockReason = LockReason.NONE) =
        state(reason, session = SessionState.ACTIVE)

    @Test
    fun everyLockReasonAtSessionEndIsAnEdge() {
        listOf(
            LockReason.TIME_UP,
            LockReason.MANUAL_WAITING,
            LockReason.AUTO_READY,
            LockReason.RESTING,
            LockReason.OUTSIDE_WINDOW,
            LockReason.PARENT_STOP,
            LockReason.AUTO_BLOCKED,
            LockReason.NO_TIMED_APPS,
        ).forEach { reason ->
            assertEquals(
                reason.name,
                LockSurfaceEdge.SESSION_ENDED,
                lockSurfaceEdge(running(), state(reason)),
            )
        }
    }

    @Test
    fun automaticAndManualModesUseTheSameEdge() {
        assertEquals(
            LockSurfaceEdge.SESSION_ENDED,
            lockSurfaceEdge(running(), state(LockReason.TIME_UP, mode = PolicyMode.CLOUD)),
        )
    }

    @Test
    fun aModeSwitchOrCrossDayEndStaysQuiet() {
        assertNull(lockSurfaceEdge(running(), state(LockReason.NONE)))
    }

    @Test
    fun onlyARunningSessionCanEnd() {
        assertNull(lockSurfaceEdge(null, state(LockReason.TIME_UP)))
        assertNull(lockSurfaceEdge(state(LockReason.NONE), state(LockReason.TIME_UP)))
        assertNull(
            lockSurfaceEdge(
                state(LockReason.NONE, session = SessionState.PENDING),
                state(LockReason.TIME_UP),
            ),
        )
    }

    @Test
    fun aSessionThatIsStillRunningIsNotAnEnd() {
        assertNull(
            lockSurfaceEdge(
                running(),
                state(LockReason.TIME_UP, session = SessionState.ACTIVE),
            ),
        )
    }

    @Test
    fun temporaryUnlockAndStudyModeDoNotSummonTheLock() {
        assertNull(
            lockSurfaceEdge(
                running(),
                state(LockReason.TIME_UP, guard = GuardStatus.PARENT_OVERRIDE),
            ),
        )
        assertNull(
            lockSurfaceEdge(
                running(),
                state(LockReason.TIME_UP, guard = GuardStatus.STUDY_MODE),
            ),
        )
    }

    @Test
    fun setupAndUnguardedTvDoNotSummonTheLock() {
        assertNull(
            lockSurfaceEdge(
                running(),
                state(LockReason.SETUP_REQUIRED, setupRequired = true),
            ),
        )
        assertNull(
            lockSurfaceEdge(
                running(),
                state(LockReason.TIME_UP, mode = PolicyMode.NONE),
            ),
        )
    }

    @Test
    fun remoteRestoreShowsTheLock() {
        val overridden = state(guard = GuardStatus.PARENT_OVERRIDE, mode = PolicyMode.CLOUD)
        assertEquals(
            LockSurfaceEdge.GUARD_RESTORED,
            lockSurfaceEdge(
                overridden,
                state(LockReason.MANUAL_WAITING, mode = PolicyMode.CLOUD),
            ),
        )
    }

    @Test
    fun timedOverrideExpiryShowsTheLock() {
        val overridden = state(guard = GuardStatus.PARENT_OVERRIDE, mode = PolicyMode.CLOUD)
        assertEquals(
            LockSurfaceEdge.GUARD_RESTORED,
            lockSurfaceEdge(overridden, state(LockReason.RESTING, mode = PolicyMode.CLOUD)),
        )
    }

    @Test
    fun restoreWithAnArmedGrantReturnsTheConfirmGate() {
        val overridden = state(guard = GuardStatus.PARENT_OVERRIDE, mode = PolicyMode.CLOUD)
        assertEquals(
            LockSurfaceEdge.GUARD_RESTORED,
            lockSurfaceEdge(
                overridden,
                state(LockReason.NONE, mode = PolicyMode.CLOUD, session = SessionState.PENDING),
            ),
        )
    }

    @Test
    fun anAlreadyEnforcedStateIsNotAnEdge() {
        assertNull(lockSurfaceEdge(state(LockReason.TIME_UP), state(LockReason.MANUAL_WAITING)))
    }

    @Test
    fun aRestoreOnAnUnguardedOrSetupTvStaysQuiet() {
        val overridden = state(guard = GuardStatus.PARENT_OVERRIDE)
        assertNull(lockSurfaceEdge(overridden, state(LockReason.NONE, mode = PolicyMode.NONE)))
        assertNull(lockSurfaceEdge(overridden, state(LockReason.NONE, setupRequired = true)))
    }
}
