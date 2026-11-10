package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Presence semantics for the parent settings page: a remembered phone may keep
 * its token for the day, but the editing slot is held only while heartbeats
 * arrive. These tests do not go through the PIN gate; they exercise the token
 * and presence layer that [QuizAccess.authenticate] updates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizAccessSessionTest {
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000
    private val tokenA = "a".repeat(32)
    private val tokenB = "b".repeat(32)

    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
        QuizSessionStore.readWebTokens(now).forEach {
            QuizSessionStore.removeWebToken(it.digest, now)
        }
    }

    private fun remember(token: String) {
        assertTrue(
            QuizSessionStore.saveWebToken(QuizAccess.fingerprint(token), now + hour, now),
        )
    }

    @Test
    fun heartbeatKeepsTheSlotUntilItGoesStale() {
        remember(tokenA)
        assertTrue(QuizAccess.adminValid(tokenA, now))
        assertTrue(QuizAccess.hasActiveSession(now + QuizAccess.SESSION_IDLE_MS - 1))

        val refreshed = now + QuizAccess.SESSION_IDLE_MS - 1
        assertTrue(QuizAccess.adminValid(tokenA, refreshed))
        assertTrue(QuizAccess.hasActiveSession(refreshed + QuizAccess.SESSION_IDLE_MS - 1))
        assertFalse(QuizAccess.hasActiveSession(refreshed + QuizAccess.SESSION_IDLE_MS))
    }

    @Test
    fun staleSessionFreesTheSlotAndResumesWithoutThePin() {
        remember(tokenA)
        assertTrue(QuizAccess.adminValid(tokenA, now))

        val stale = now + QuizAccess.SESSION_IDLE_MS
        assertFalse(QuizAccess.hasActiveSession(stale))

        // Nobody took the slot, so this phone may continue without the PIN.
        assertTrue(QuizAccess.adminValid(tokenA, stale))
        assertTrue(QuizAccess.hasActiveSession(stale))
    }

    @Test
    fun releaseFreesTheSlotImmediatelyButKeepsTheToken() {
        remember(tokenA)
        assertTrue(QuizAccess.adminValid(tokenA, now))

        assertTrue(QuizAccess.release(tokenA, now + 1_000))
        assertFalse(QuizAccess.hasActiveSession(now + 1_000))

        assertTrue(QuizAccess.adminValid(tokenA, now + 2_000))
        assertTrue(QuizAccess.hasActiveSession(now + 2_000))
    }

    @Test
    fun releaseFromAnotherPhoneCannotFreeTheSlot() {
        remember(tokenA)
        assertTrue(QuizAccess.adminValid(tokenA, now))

        assertFalse(QuizAccess.release(tokenB, now + 1_000))
        assertTrue(QuizAccess.hasActiveSession(now + 1_000))
    }

    @Test
    fun newLoginReplacesTheOldPhone() {
        remember(tokenA)
        assertTrue(QuizAccess.adminValid(tokenA, now))

        // authenticate() overwrites the remembered digest; this is the takeover.
        remember(tokenB)
        assertFalse(QuizAccess.adminValid(tokenA, now + 1_000))
        assertTrue(QuizAccess.adminValid(tokenB, now + 1_000))
        assertTrue(QuizAccess.hasActiveSession(now + 1_000))
        assertFalse(QuizAccess.hasActiveSession(now + 1_000 + QuizAccess.SESSION_IDLE_MS))
    }

    @Test
    fun logoutForgetsTheResumeToken() {
        remember(tokenA)
        assertTrue(QuizAccess.adminValid(tokenA, now))

        QuizAccess.logout(tokenA, now)
        assertFalse(QuizAccess.adminValid(tokenA, now))
        assertFalse(QuizAccess.hasActiveSession(now))
        assertNull(QuizSessionStore.activeWebToken(now))
    }
}
