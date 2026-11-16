package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The weekly wrong-book cycle: when it starts, that later answers do not push the
 * deadline out, that it goes overdue a week later, and that emptying the book (by
 * clearing it or by answering everything right) starts a fresh week.
 */
@RunWith(RobolectricTestRunner::class)
class WrongBookPolicyTest {
    private val now = 1_760_000_000_000L
    private val day = 24 * 60 * 60 * 1000L
    private val testDb = "wrong_book_test.db"

    // Same keys WrongBookPolicy owns; kept literal so the policy can keep them private.
    private val cycleStartKey = "quiz.wrongBook.cycleStartMs"
    private val lastClearKey = "quiz.wrongBook.lastClearMs"

    private fun item(tag: String, index: Int) = QuizItem(
        questionId = "q_wb_${tag}_$index",
        prompt = "Question $tag number $index",
        options = listOf("A", "B", "C", "D"),
        answerIndex = 1,
        explanation = "",
        subject = "math",
        grade = "g1",
        difficulty = "easy",
        points = 1,
        timeLimitSec = 60,
        recommendedTimeSec = 60,
    )

    /** Puts [count] freshly answered-wrong questions in the book, answered at [at]. */
    private fun seedWrong(count: Int, at: Long, tag: String) {
        val items = (1..count).map { item(tag, it) }
        val appended = QuizQuestionCache.appendLocalQuestions("batch_$tag", items, at)
        assertEquals(count, appended.appended)
        val round = "sess_$tag"
        val reserved = QuizQuestionCache.reserveForRound(
            count = count,
            sessionId = round,
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = count,
            nowMs = at,
        )
        assertEquals(count, reserved.size)
        assertTrue(
            QuizQuestionCache.settleRound(
                round,
                QuizQuestionCache.LOCAL_SCOPE,
                reserved.map { cached ->
                    QuizQuestionCache.AnswerWrite(
                        cacheId = cached.cacheId,
                        questionId = cached.question.questionId,
                        selectedIndex = 0,
                        correct = false,
                        timedOut = false,
                        answeredAt = at,
                    )
                },
                at,
            ),
        )
    }

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        TvStore.init(context)
        TvStore.removeDurable(cycleStartKey)
        TvStore.removeDurable(lastClearKey)
        assertTrue(QuizQuestionCache.initForTest(context, testDb))
        QuizQuestionCache.clearBank(
            QuizQuestionCache.LOCAL_SCOPE,
            QuizQuestionCache.ClearMode.ALL,
            QuizQuestionCache.BankSource.ALL,
        )
        assertEquals(0, QuizQuestionCache.wrongReviewCount())
    }

    @Test
    fun anEmptyBookHasNoCycle() {
        val state = WrongBookPolicy.state(now)
        assertEquals(0, state.count)
        assertEquals(0L, state.cycleStartMs)
        assertFalse(state.overdue)
        assertFalse(state.mixing)
        assertEquals(0L, WrongBookPolicy.lastClearMs())
    }

    @Test
    fun theFirstWrongAnswerStartsTheWeek() {
        seedWrong(count = 2, at = now, tag = "first")
        val state = WrongBookPolicy.state(now)
        assertEquals(2, state.count)
        assertEquals(now, state.cycleStartMs)
        assertFalse(state.overdue)
        assertTrue(state.mixing)
        assertEquals(7, state.daysLeft)
    }

    @Test
    fun theCycleIsNotRestartedByLaterAnswers() {
        seedWrong(count = 1, at = now, tag = "early")
        assertEquals(now, WrongBookPolicy.state(now).cycleStartMs)
        // A wrong answer days later must not push the deadline out.
        seedWrong(count = 1, at = now + 3 * day, tag = "late")
        val state = WrongBookPolicy.state(now + 3 * day)
        assertEquals(2, state.count)
        assertEquals(now, state.cycleStartMs)
        assertEquals(4, state.daysLeft)
        assertFalse(state.overdue)
        assertTrue(state.mixing)
    }

    @Test
    fun aWeekOldBookIsOverdueAndStopsMixing() {
        seedWrong(count = 2, at = now, tag = "stale")
        // Reading the state right after the first wrong answer dates the week.
        assertEquals(now, WrongBookPolicy.state(now).cycleStartMs)
        val state = WrongBookPolicy.state(now + 7 * day)
        assertTrue(state.overdue)
        assertFalse(state.mixing)
        assertEquals(0, state.daysLeft)
        assertEquals(2, state.count)
        assertTrue(WrongBookPolicy.overdue(now + 7 * day))
        assertFalse(WrongBookPolicy.overdue(now + 6 * day))
    }

    @Test
    fun emptyingTheBookClosesTheCycle() {
        seedWrong(count = 1, at = now, tag = "solo")
        assertEquals(now, WrongBookPolicy.state(now).cycleStartMs)
        assertTrue(
            QuizQuestionCache.clearBank(
                QuizQuestionCache.LOCAL_SCOPE,
                QuizQuestionCache.ClearMode.WRONG,
            ),
        )
        val state = WrongBookPolicy.state(now + day)
        assertEquals(0, state.count)
        assertEquals(0L, state.cycleStartMs)
        assertFalse(state.overdue)
        assertFalse(state.mixing)
    }

    @Test
    fun markClearedStartsAFreshWeek() {
        seedWrong(count = 2, at = now, tag = "cleared")
        // Date the week at the first wrong answer, the way a real round does.
        assertEquals(now, WrongBookPolicy.state(now).cycleStartMs)
        assertTrue(WrongBookPolicy.state(now + 7 * day).overdue)

        WrongBookPolicy.markCleared(now + 7 * day)
        assertEquals(now + 7 * day, WrongBookPolicy.lastClearMs())

        // The questions are still in the book, so the next read dates a fresh week
        // instead of leaving practice blocked on the old one.
        val restarted = WrongBookPolicy.state(now + 8 * day)
        assertEquals(2, restarted.count)
        assertEquals(now + 8 * day, restarted.cycleStartMs)
        assertFalse(restarted.overdue)
        assertTrue(restarted.mixing)
        assertEquals(7, restarted.daysLeft)
    }
}
