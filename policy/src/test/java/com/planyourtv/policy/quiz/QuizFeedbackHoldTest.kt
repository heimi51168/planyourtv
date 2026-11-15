package com.planyourtv.policy.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizFeedbackHoldTest {
    private val answeredAt = 1_000_000L

    @Test
    fun v5SubmissionsDoNotFreezeANewValue() {
        assertNull(
            QuizFeedbackHold.frozenHoldMs(
                correct = false,
                timedOut = false,
                extraHoldSec = 2,
                originSchemaVersion = 5,
            ),
        )
    }

    @Test
    fun v6CorrectAnswersFreezeZeroAndWrongAnswersUseTheVoiceFloor() {
        assertEquals(
            0L,
            QuizFeedbackHold.frozenHoldMs(
                correct = true,
                timedOut = true,
                extraHoldSec = 10,
                originSchemaVersion = 6,
            ),
        )
        assertEquals(
            QuizVoiceHold.WRONG_HOLD_MS,
            QuizFeedbackHold.frozenHoldMs(
                correct = false,
                timedOut = false,
                extraHoldSec = 0,
                originSchemaVersion = 6,
            ),
        )
        assertEquals(
            QuizVoiceHold.TIMEOUT_HOLD_MS,
            QuizFeedbackHold.frozenHoldMs(
                correct = false,
                timedOut = true,
                extraHoldSec = 2,
                originSchemaVersion = 6,
            ),
        )
        assertEquals(
            10_000L,
            QuizFeedbackHold.frozenHoldMs(
                correct = false,
                timedOut = true,
                extraHoldSec = 10,
                originSchemaVersion = 6,
            ),
        )
    }

    @Test
    fun aFrozenHoldIsUsedExactlyEvenWhenTheConfigChangedLater() {
        val submission = submission(feedbackHoldMs = QuizVoiceHold.WRONG_HOLD_MS)
        assertTrue(
            QuizFeedbackHold.isLocked(
                submission,
                answerDisplaySec = 30,
                nowMs = answeredAt + QuizVoiceHold.WRONG_HOLD_MS - 1,
            ),
        )
        assertFalse(
            QuizFeedbackHold.isLocked(
                submission,
                answerDisplaySec = 30,
                nowMs = answeredAt + QuizVoiceHold.WRONG_HOLD_MS,
            ),
        )
    }

    @Test
    fun v5RecordsKeepTheirHistoricalAnswerDisplaySeconds() {
        val submission = submission(feedbackHoldMs = null)
        assertTrue(QuizFeedbackHold.isLocked(submission, answerDisplaySec = 3, nowMs = answeredAt + 2_999))
        assertFalse(QuizFeedbackHold.isLocked(submission, answerDisplaySec = 3, nowMs = answeredAt + 3_000))
        // Old out-of-range values normalize to the documented default.
        assertEquals(
            QUIZ_DEFAULT_ANSWER_DISPLAY_SEC * 1_000L,
            QuizFeedbackHold.effectiveHoldMs(submission, answerDisplaySec = 120),
        )
    }

    @Test
    fun aCorrectAnswerNeverHoldsEvenWithACorruptFrozenValue() {
        val submission = submission(correct = true, feedbackHoldMs = 5_000L)
        assertEquals(0L, QuizFeedbackHold.effectiveHoldMs(submission, answerDisplaySec = 30))
        assertFalse(QuizFeedbackHold.isLocked(submission, answerDisplaySec = 30, nowMs = answeredAt))
    }

    private fun submission(
        correct: Boolean = false,
        feedbackHoldMs: Long?,
    ) = QuizSubmission(
        questionId = "q_1234567",
        selectedIndex = if (correct) 0 else 1,
        correct = correct,
        timedOut = false,
        answeredAt = answeredAt,
        feedbackHoldMs = feedbackHoldMs,
    )
}
