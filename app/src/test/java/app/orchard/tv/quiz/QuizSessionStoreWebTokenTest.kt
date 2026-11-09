package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizSessionStoreWebTokenTest {
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    private fun digest(char: Char) = char.toString().repeat(64)

    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
        QuizSessionStore.activeWebToken(now)?.let {
            QuizSessionStore.removeWebToken(it.digest, now)
        }
    }

    @Test
    fun newestRememberedPhoneIsTheOnlyValidSession() {
        assertTrue(QuizSessionStore.saveWebToken(digest('a'), now + hour, now))
        assertEquals(digest('a'), QuizSessionStore.activeWebToken(now)?.digest)

        assertTrue(QuizSessionStore.saveWebToken(digest('b'), now + hour, now))
        assertEquals(1, QuizSessionStore.readWebTokens(now).size)
        assertEquals(digest('b'), QuizSessionStore.activeWebToken(now)?.digest)
        assertFalse(QuizSessionStore.webTokenValid(digest('a'), now))
        assertTrue(QuizSessionStore.webTokenValid(digest('b'), now))
    }

    @Test
    fun onlyTheOwnerCanLogOutAndFreeTheSlot() {
        QuizSessionStore.saveWebToken(digest('a'), now + hour, now)
        QuizSessionStore.saveWebToken(digest('b'), now + hour, now)

        assertFalse(QuizSessionStore.removeWebToken(digest('a'), now))
        val activeBeforeLogout = QuizSessionStore.activeWebToken(now)
        assertNotNull(activeBeforeLogout)
        assertEquals(digest('b'), activeBeforeLogout?.digest)

        val removed = QuizSessionStore.removeWebToken(digest('b'), now)
        assertTrue("removed=$removed active=${activeBeforeLogout?.digest}", removed)
        assertNull(QuizSessionStore.activeWebToken(now))
        assertFalse(QuizSessionStore.webTokenValid(digest('b'), now))
    }

    @Test
    fun expiredSessionFreesTheSlotAtLocalMidnight() {
        assertTrue(QuizSessionStore.saveWebToken(digest('a'), now + hour, now))
        assertNull(QuizSessionStore.activeWebToken(now + hour + 1))
    }

    @Test
    fun tokenBeyondTheDayCapIsRejected() {
        assertFalse(QuizSessionStore.saveWebToken(digest('a'), now + 26 * hour, now))
        assertNull(QuizSessionStore.activeWebToken(now))
    }
}
