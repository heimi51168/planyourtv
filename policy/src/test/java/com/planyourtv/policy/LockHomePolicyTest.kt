package com.planyourtv.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The no-guard watchdog's decision; see LockHomePolicy.kt. */
class LockHomePolicyTest {
    private fun due(
        lockScreen: Boolean = true,
        guardRunning: Boolean = false,
        aerialInFront: Boolean = false,
        lockOwnedAppInFront: Boolean = false,
        screenInteractive: Boolean = true,
        userUnlocked: Boolean = true,
        nowMs: Long = 10_000L,
        lastAttemptMs: Long = 0L,
        cooldownMs: Long = 2_500L,
    ) = shouldBringHomeWithoutGuard(
        lockScreen = lockScreen,
        guardRunning = guardRunning,
        aerialInFront = aerialInFront,
        lockOwnedAppInFront = lockOwnedAppInFront,
        screenInteractive = screenInteractive,
        userUnlocked = userUnlocked,
        nowMs = nowMs,
        lastAttemptMs = lastAttemptMs,
        cooldownMs = cooldownMs,
    )

    @Test
    fun bringsTheLockForwardWithoutAGuard() {
        assertTrue(due())
    }

    @Test
    fun aRunningGuardOwnsTheEdge() {
        assertFalse(due(guardRunning = true))
    }

    @Test
    fun aerialAlreadyInFrontNeedsNoStart() {
        assertFalse(due(aerialInFront = true))
    }

    @Test
    fun noLockNoStart() {
        assertFalse(due(lockScreen = false))
    }

    @Test
    fun theLocksOwnStudyAppIsLeftInFront() {
        assertFalse(due(lockOwnedAppInFront = true))
    }

    @Test
    fun anotherAppWhileLockedIsStillReclaimed() {
        assertTrue(due(lockOwnedAppInFront = false))
    }

    @Test
    fun screenOffWaits() {
        assertFalse(due(screenInteractive = false))
    }

    @Test
    fun lockedUserWaits() {
        assertFalse(due(userUnlocked = false))
    }

    @Test
    fun cooldownSuppressesARepeatedStart() {
        assertFalse(due(nowMs = 11_000L, lastAttemptMs = 10_000L))
        assertTrue(due(nowMs = 12_500L, lastAttemptMs = 10_000L))
    }

    @Test
    fun aZeroCooldownAllowsEveryAttempt() {
        assertTrue(due(nowMs = 10_001L, lastAttemptMs = 10_000L, cooldownMs = 0L))
    }
}