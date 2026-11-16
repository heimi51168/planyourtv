package app.orchard.tv.bot

import app.orchard.tv.quiz.QuizEarnedStore
import com.planyourtv.policy.TvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale

/**
 * The earned-minutes ledger closes the "endless quiz-for-time" gap: the cap
 * clamps what a round may still pay today, and the counter resets with the
 * local date.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizEarnedStoreTest {

    @Before
    fun resetStore() {
        TvStore.init(RuntimeEnvironment.getApplication())
        TvStore.remove("quiz.earn.date")
        TvStore.remove("quiz.earn.minutes")
        TvStore.remove("quiz.earn.cap.minutes")
    }

    private fun ms(day: Int, hour: Int = 10): Long = Calendar.getInstance(Locale.CHINA).apply {
        set(2026, Calendar.NOVEMBER, day, hour, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun capDefaultsToUnlimitedAndValidatesRange() {
        assertEquals(0, QuizEarnedStore.capMinutes())
        assertTrue(QuizEarnedStore.setCapMinutes(30))
        assertEquals(30, QuizEarnedStore.capMinutes())
        assertFalse("above 240 must be refused", QuizEarnedStore.setCapMinutes(300))
        assertFalse("negative must be refused", QuizEarnedStore.setCapMinutes(-1))
        assertEquals(30, QuizEarnedStore.capMinutes())
    }

    @Test
    fun unlimitedCapMeansUnlimitedRemaining() {
        QuizEarnedStore.recordEarned(500, ms(14))
        assertEquals(Int.MAX_VALUE, QuizEarnedStore.remainingToday(ms(14)))
    }

    @Test
    fun recordEarnedClampsToTheRemainingAllowance() {
        QuizEarnedStore.setCapMinutes(40)
        assertEquals(30, QuizEarnedStore.recordEarned(30, ms(14)))
        assertEquals(10, QuizEarnedStore.remainingToday(ms(14)))
        // A 25-minute round only pays the last 10 minutes of the day's cap.
        assertEquals(10, QuizEarnedStore.recordEarned(25, ms(14, 12)))
        assertEquals(0, QuizEarnedStore.remainingToday(ms(14, 20)))
        assertEquals(0, QuizEarnedStore.recordEarned(5, ms(14, 21)))
    }

    @Test
    fun counterResetsOnANewLocalDay() {
        QuizEarnedStore.setCapMinutes(30)
        QuizEarnedStore.recordEarned(30, ms(14))
        assertEquals(0, QuizEarnedStore.remainingToday(ms(14, 22)))
        assertEquals(30, QuizEarnedStore.remainingToday(ms(15, 8)))
        assertEquals(0, QuizEarnedStore.earnedToday(ms(15, 8)))
    }

    @Test
    fun zeroAndNegativeRecordingIsANoOp() {
        QuizEarnedStore.setCapMinutes(10)
        assertEquals(0, QuizEarnedStore.recordEarned(0, ms(14)))
        assertEquals(0, QuizEarnedStore.recordEarned(-5, ms(14)))
        assertEquals(10, QuizEarnedStore.remainingToday(ms(14)))
    }
}
