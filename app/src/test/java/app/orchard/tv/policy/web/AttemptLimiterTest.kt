package app.orchard.tv.policy.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-IP PIN/pairing limiter used by the V27 web parent center. */
class AttemptLimiterTest {
    private val limiter = AttemptLimiter(maxFailures = 5, lockMs = 10 * 60_000L)

    @Test
    fun fiveFailuresLockOnlyThatAddress() {
        repeat(4) { limiter.onFailure("10.0.0.2", nowMs = 1_000L) }
        assertEquals(0L, limiter.lockedSeconds("10.0.0.2", nowMs = 1_000L))
        limiter.onFailure("10.0.0.2", nowMs = 1_000L)
        assertTrue(limiter.lockedSeconds("10.0.0.2", nowMs = 1_000L) > 0L)
        assertEquals(0L, limiter.lockedSeconds("10.0.0.3", nowMs = 1_000L))
    }

    @Test
    fun lockEndsAfterTheWindow() {
        limiter.lockOut("10.0.0.2", nowMs = 1_000L)
        assertEquals(600L, limiter.lockedSeconds("10.0.0.2", nowMs = 1_000L))
        assertEquals(0L, limiter.lockedSeconds("10.0.0.2", nowMs = 1_000L + 600_000L))
    }

    @Test
    fun successClearsFailuresAndLock() {
        repeat(4) { limiter.onFailure("10.0.0.2", nowMs = 1_000L) }
        limiter.onSuccess("10.0.0.2")
        repeat(4) { limiter.onFailure("10.0.0.2", nowMs = 1_000L) }
        assertEquals(0L, limiter.lockedSeconds("10.0.0.2", nowMs = 1_000L))
    }

    @Test
    fun failuresDuringALockDoNotExtendIt() {
        limiter.lockOut("10.0.0.2", nowMs = 1_000L)
        limiter.onFailure("10.0.0.2", nowMs = 2_000L)
        assertEquals(600L, limiter.lockedSeconds("10.0.0.2", nowMs = 1_000L))
    }
}
