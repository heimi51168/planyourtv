package com.planyourtv.policy.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizScoringV6Test {
    private val profile = QuizRewardProfile(
        correctSeconds = QuizDifficultySeconds(60, 120, 300),
        penaltySeconds = QuizDifficultySeconds(5, 10, 15),
        maxEarnedSeconds = 600,
        minUnlockSeconds = 0,
    )

    @Test
    fun correctAnswersAddTheirDifficultySecondsAndWrongOnesOnlyPenalize() {
        val score = QuizScoringV6.score(
            items = listOf(item("easy"), item("medium"), item("hard")),
            submissions = listOf(
                answer("easy", correct = true),
                answer("medium", correct = true),
                answer("hard", correct = false),
            ),
            profile = profile,
        )
        assertEquals(2, score.correctCount)
        assertEquals(180, score.grossSeconds)
        assertEquals(15, score.penaltySeconds)
        assertEquals(165, score.netSeconds)
        assertEquals(165, score.cappedSeconds)
        assertTrue(score.unlockReached)
        assertEquals(165, score.earnedSeconds)
    }

    @Test
    fun unknownDifficultyIsScoredAsMedium() {
        val score = QuizScoringV6.score(
            items = listOf(item("mystery")),
            submissions = listOf(answer("mystery", correct = true)),
            profile = profile,
        )
        assertEquals(120, score.grossSeconds)
    }

    @Test
    fun aTimedOutAnswerIsWrongEvenIfTheFlagSaysCorrect() {
        val score = QuizScoringV6.score(
            items = listOf(item("hard")),
            submissions = listOf(answer("hard", correct = true, timedOut = true)),
            profile = profile,
        )
        assertEquals(0, score.correctCount)
        assertEquals(0, score.grossSeconds)
        assertEquals(15, score.penaltySeconds)
        assertEquals(0, score.earnedSeconds)
    }

    @Test
    fun duplicateSubmissionsUseOnlyTheFirstOne() {
        val score = QuizScoringV6.score(
            items = listOf(item("easy")),
            submissions = listOf(
                answer("easy", correct = true, at = 1L),
                answer("easy", correct = false, at = 2L),
            ),
            profile = profile,
        )
        assertEquals(1, score.correctCount)
        assertEquals(60, score.grossSeconds)
        assertEquals(0, score.penaltySeconds)
    }

    @Test
    fun submissionsForUnknownItemsAreIgnored() {
        val score = QuizScoringV6.score(
            items = listOf(item("easy")),
            submissions = listOf(answer("ghost", correct = true)),
            profile = profile,
        )
        assertEquals(0, score.correctCount)
        assertEquals(0, score.earnedSeconds)
    }

    @Test
    fun netCannotBeNegativeAndTheCapAndThresholdAreApplied() {
        val capped = QuizScoringV6.score(
            items = List(4) { item("hard", id = "hard-$it") },
            submissions = List(4) { answer("hard-$it", correct = true) },
            profile = profile,
        )
        assertEquals(1200, capped.grossSeconds)
        assertEquals(600, capped.cappedSeconds)
        assertEquals(600, capped.earnedSeconds)

        val ungated = QuizScoringV6.score(
            items = listOf(item("easy"), item("easy", id = "easy-2")),
            submissions = listOf(
                answer("easy", correct = false),
                answer("easy-2", correct = true),
            ),
            profile = profile.copy(penaltySeconds = QuizDifficultySeconds(100, 0, 0)),
        )
        assertEquals(0, ungated.netSeconds)
        assertEquals(0, ungated.earnedSeconds)

        val gated = QuizScoringV6.score(
            items = listOf(item("easy")),
            submissions = listOf(answer("easy", correct = true)),
            profile = profile.copy(minUnlockSeconds = 120),
        )
        assertEquals(60, gated.cappedSeconds)
        assertFalse(gated.unlockReached)
        assertEquals(0, gated.earnedSeconds)
    }

    @Test
    fun aZeroThresholdAlwaysUnlocks() {
        val score = QuizScoringV6.score(
            items = listOf(item("easy")),
            submissions = listOf(answer("easy", correct = false)),
            profile = profile.copy(minUnlockSeconds = 0),
        )
        assertTrue(score.unlockReached)
        assertEquals(0, score.earnedSeconds)
    }

    @Test
    fun aMigratedProfileIsEquivalentToTheV5MinuteEngine() {
        val config = QuizConfig(
            easyRewardMinutes = 1,
            mediumRewardMinutes = 2,
            hardRewardMinutes = 5,
            easyPenaltyMinutes = 1,
            mediumPenaltyMinutes = 2,
            hardPenaltyMinutes = 3,
            maxEarnedMinutes = 10,
            minUnlockMinutes = 2,
        )
        val migratedProfile = QuizRewardProfile(
            correctSeconds = QuizDifficultySeconds(
                config.easyRewardMinutes * 60,
                config.mediumRewardMinutes * 60,
                config.hardRewardMinutes * 60,
            ),
            penaltySeconds = QuizDifficultySeconds(
                config.easyPenaltyMinutes * 60,
                config.mediumPenaltyMinutes * 60,
                config.hardPenaltyMinutes * 60,
            ),
            maxEarnedSeconds = config.maxEarnedMinutes * 60,
            minUnlockSeconds = config.minUnlockMinutes * 60,
        )
        val items = listOf(item("easy"), item("medium"), item("hard"), item("unknown"))
        val submissions = listOf(
            answer("easy", correct = true),
            answer("medium", correct = false),
            answer("hard", correct = true),
            answer("unknown", correct = true),
        )
        val v5 = QuizScoring.score(items, submissions, config)
        val v6 = QuizScoringV6.score(items, submissions, migratedProfile)
        assertEquals(v5.correctCount, v6.correctCount)
        assertEquals(v5.grossMinutes * 60, v6.grossSeconds)
        assertEquals(v5.penaltyMinutes * 60, v6.penaltySeconds)
        assertEquals(v5.netMinutes * 60, v6.netSeconds)
        assertEquals(v5.cappedMinutes * 60, v6.cappedSeconds)
        assertEquals(v5.thresholdMinutes * 60, v6.thresholdSeconds)
        assertEquals(v5.unlockReached, v6.unlockReached)
        assertEquals(v5.earnedMinutes * 60, v6.earnedSeconds)
    }

    private fun item(difficulty: String, id: String = difficulty): QuizItem = QuizItem(
        questionId = id,
        prompt = "prompt",
        options = listOf("a", "b"),
        answerIndex = 0,
        difficulty = difficulty,
    )

    private fun answer(
        id: String,
        correct: Boolean,
        timedOut: Boolean = false,
        at: Long = 1L,
    ): QuizSubmission = QuizSubmission(
        questionId = id,
        selectedIndex = if (correct) 0 else 1,
        correct = correct,
        timedOut = timedOut,
        answeredAt = at,
    )
}
