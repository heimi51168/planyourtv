package app.orchard.tv.bot

import app.orchard.tv.quiz.QuizSessionStore
import com.planyourtv.policy.TvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * setQuizField writes through the same challenge-config store the web page
 * uses, with the codec as the range authority: a legal value lands verbatim,
 * an illegal one leaves the stored config untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BotActionsQuizFieldTest {

    @Before
    fun resetStore() {
        TvStore.init(RuntimeEnvironment.getApplication())
        TvStore.remove("quiz.challengeConfig")
    }

    private fun set(field: BotCommand.QuizField, value: Int): BotActions.Outcome =
        BotActions.handle(BotCommand.SetQuizField(field, value), "妈妈")

    @Test
    fun legalValuesPersistThroughTheConfigStore() {
        assertTrue(set(BotCommand.QuizField.ROUND_COUNT, 10).audit != null)
        assertEquals(10, QuizSessionStore.loadChallengeConfig()?.pullCount)

        assertTrue(set(BotCommand.QuizField.TIME_LIMIT, 45).audit != null)
        assertEquals(45, QuizSessionStore.loadChallengeConfig()?.defaultTimeLimitSec)

        assertTrue(set(BotCommand.QuizField.UNLOCK_GATE, 5).audit != null)
        assertEquals(5, QuizSessionStore.loadChallengeConfig()?.minUnlockMinutes)

        assertTrue(set(BotCommand.QuizField.ROUND_CAP, 15).audit != null)
        assertEquals(15, QuizSessionStore.loadChallengeConfig()?.maxEarnedMinutes)
    }

    @Test
    fun outOfRangeValuesAreRefusedAndLeaveTheConfigAlone() {
        assertTrue(set(BotCommand.QuizField.ROUND_COUNT, 10).audit != null)
        // The v1.97 codec widened the round count to 1..1000, so 99 is legal;
        // 2000 must still be refused without touching the stored value.
        assertTrue(set(BotCommand.QuizField.ROUND_COUNT, 99).audit != null)
        assertEquals(99, QuizSessionStore.loadChallengeConfig()?.pullCount)
        val refused = set(BotCommand.QuizField.ROUND_COUNT, 2000)
        assertNull("2000 questions must be refused", refused.audit)
        assertTrue(refused.text.contains("1–1000"))
        assertEquals("stored value unchanged", 99, QuizSessionStore.loadChallengeConfig()?.pullCount)

        // The v1.97 codec lowered the time-limit floor to 5 s, so 5 is legal;
        // 1 s must still be refused.
        assertTrue(set(BotCommand.QuizField.TIME_LIMIT, 5).audit != null)
        val tooFast = set(BotCommand.QuizField.TIME_LIMIT, 1)
        assertNull(tooFast.audit)
        assertTrue(tooFast.text.contains("5–1800"))
    }
}
