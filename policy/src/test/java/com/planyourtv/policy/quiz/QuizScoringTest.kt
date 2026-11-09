package com.planyourtv.policy.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizScoringTest {
    private val config = QuizConfig(
        maxEarnedMinutes = 60,
        easyRewardMinutes = 1,
        mediumRewardMinutes = 2,
        hardRewardMinutes = 5,
        easyPenaltyMinutes = 0,
        mediumPenaltyMinutes = 0,
        hardPenaltyMinutes = 0,
        minUnlockMinutes = 0,
    )

    private fun item(difficulty: String, id: String = "q_1234567") = QuizItem(
        questionId = id,
        prompt = id,
        options = listOf("A", "B"),
        answerIndex = 0,
        difficulty = difficulty,
    )

    private fun answer(
        item: QuizItem,
        correct: Boolean,
        timedOut: Boolean = false,
    ): QuizSubmission = QuizSubmission(
        questionId = item.questionId,
        selectedIndex = if (timedOut) null else 0,
        correct = correct,
        timedOut = timedOut,
        answeredAt = 1L,
    )

    @Test
    fun defaultRewardsUseDifficulty() {
        val items = listOf(
            item("easy", "q_easy000"),
            item("medium", "q_med0000"),
            item("hard", "q_hard000"),
        )
        val submissions = listOf(
            answer(items[0], correct = true),
            answer(items[1], correct = true),
            answer(items[2], correct = true),
        )
        val score = QuizScoring.score(items, submissions, config)
        assertEquals(3, score.correctCount)
        assertEquals(8, score.grossMinutes)
        assertEquals(0, score.penaltyMinutes)
        assertEquals(8, score.earnedMinutes)
    }

    @Test
    fun penaltiesReduceNetAndNeverGoBelowZero() {
        val items = listOf(item("easy", "q_easy000"), item("hard", "q_hard000"))
        val penalized = config.copy(
            easyPenaltyMinutes = 2,
            hardPenaltyMinutes = 10,
        )
        val score = QuizScoring.score(
            items,
            listOf(
                answer(items[0], correct = true),
                answer(items[1], correct = false),
            ),
            penalized,
        )
        assertEquals(1, score.grossMinutes)
        assertEquals(10, score.penaltyMinutes)
        assertEquals(0, score.netMinutes)
        assertEquals(0, score.earnedMinutes)

        val zero = QuizScoring.score(items, emptyList(), penalized)
        assertEquals(0, zero.netMinutes)
        assertEquals(0, zero.earnedMinutes)
    }

    @Test
    fun thresholdIsAQualificationLineNotADeduction() {
        val items = listOf(item("hard", "q_hard000"))
        val threshold = config.copy(minUnlockMinutes = 5)
        val score = QuizScoring.score(
            items,
            listOf(answer(items[0], correct = true)),
            threshold,
        )
        assertTrue(score.unlockReached)
        assertEquals(5, score.earnedMinutes)
    }

    @Test
    fun belowThresholdEarnsNothing() {
        val items = listOf(item("easy", "q_easy000"))
        val threshold = config.copy(minUnlockMinutes = 5)
        val score = QuizScoring.score(
            items,
            listOf(answer(items[0], correct = true)),
            threshold,
        )
        assertFalse(score.unlockReached)
        assertEquals(1, score.netMinutes)
        assertEquals(0, score.earnedMinutes)
    }

    @Test
    fun zeroThresholdNeedsNoQualification() {
        val items = listOf(item("easy", "q_easy000"))
        val score = QuizScoring.score(
            items,
            listOf(answer(items[0], correct = true)),
            config,
        )
        assertTrue(score.unlockReached)
        assertEquals(1, score.earnedMinutes)
    }

    @Test
    fun capAppliesAfterPenalty() {
        val items = listOf(item("hard", "q_hard000"), item("hard", "q_hard001"))
        val capped = config.copy(maxEarnedMinutes = 6)
        val score = QuizScoring.score(
            items,
            listOf(
                answer(items[0], correct = true),
                answer(items[1], correct = true),
            ),
            capped,
        )
        assertEquals(10, score.grossMinutes)
        assertEquals(6, score.earnedMinutes)
    }

    @Test
    fun timeoutUsesTheWrongAnswerPenalty() {
        val items = listOf(item("hard", "q_hard000"))
        val penalized = config.copy(hardPenaltyMinutes = 3)
        val score = QuizScoring.score(
            items,
            listOf(answer(items[0], correct = false, timedOut = true)),
            penalized,
        )
        assertEquals(0, score.correctCount)
        assertEquals(3, score.penaltyMinutes)
    }

    @Test
    fun duplicateSubmissionForOneQuestionCountsOnce() {
        val items = listOf(item("hard", "q_hard000"))
        val submission = answer(items[0], correct = false, timedOut = true)
        val score = QuizScoring.score(
            items,
            listOf(submission, submission),
            config.copy(hardPenaltyMinutes = 4),
        )
        assertEquals(4, score.penaltyMinutes)
    }

    @Test
    fun emptyOrUnknownDifficultyCountsAsMedium() {
        val items = listOf(item("", "q_empty00"), item("custom", "q_custom0"))
        val medium = config.copy(mediumRewardMinutes = 7)
        val score = QuizScoring.score(
            items,
            listOf(
                answer(items[0], correct = true),
                answer(items[1], correct = true),
            ),
            medium,
        )
        assertEquals(14, score.grossMinutes)
    }
}
