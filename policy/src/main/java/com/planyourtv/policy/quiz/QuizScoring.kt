package com.planyourtv.policy.quiz

import java.util.Locale
import kotlin.math.min

/** The settled score of one local v5 round. */
data class QuizScore(
    val correctCount: Int,
    val grossMinutes: Int,
    val penaltyMinutes: Int,
    val netMinutes: Int,
    val cappedMinutes: Int,
    val thresholdMinutes: Int,
    val unlockReached: Boolean,
    val earnedMinutes: Int,
)

/**
 * Difficulty scoring is intentionally pure: the TV UI, the settlement path and
 * the stored-session checks all ask the same object, so a value cannot drift
 * between what the child was shown and what the round pays out.
 *
 * A question with no recognizable difficulty is treated as medium. Duplicate
 * submissions for one question are ignored after the first, which makes the
 * timeout-versus-tap race and any stale retry harmless.
 */
object QuizScoring {
    fun rewardMinutes(difficulty: String, config: QuizConfig): Int = when (normalize(difficulty)) {
        "easy" -> config.easyRewardMinutes
        "hard" -> config.hardRewardMinutes
        else -> config.mediumRewardMinutes
    }

    fun penaltyMinutes(difficulty: String, config: QuizConfig): Int = when (normalize(difficulty)) {
        "easy" -> config.easyPenaltyMinutes
        "hard" -> config.hardPenaltyMinutes
        else -> config.mediumPenaltyMinutes
    }

    fun score(
        items: List<QuizItem>,
        submissions: List<QuizSubmission>,
        config: QuizConfig,
    ): QuizScore {
        val byId = items.associateBy { it.questionId }
        var correctCount = 0
        var gross = 0
        var penalty = 0
        submissions.distinctBy { it.questionId }.forEach { submission ->
            val item = byId[submission.questionId] ?: return@forEach
            val correct = submission.correct && !submission.timedOut
            if (correct) {
                correctCount += 1
                gross += rewardMinutes(item.difficulty, config)
            } else {
                penalty += penaltyMinutes(item.difficulty, config)
            }
        }
        val net = (gross - penalty).coerceAtLeast(0)
        val cap = config.maxEarnedMinutes.coerceIn(1, QUIZ_MAX_EARNED_MINUTES)
        val capped = min(net, cap)
        val threshold = config.minUnlockMinutes.coerceIn(0, QUIZ_MAX_UNLOCK_MINUTES)
        val reached = threshold == 0 || capped >= threshold
        return QuizScore(
            correctCount = correctCount,
            grossMinutes = gross,
            penaltyMinutes = penalty,
            netMinutes = net,
            cappedMinutes = capped,
            thresholdMinutes = threshold,
            unlockReached = reached,
            earnedMinutes = if (reached) capped else 0,
        )
    }

    /** Empty or unknown metadata falls back to medium, matching the product rule. */
    fun normalize(difficulty: String): String = when (difficulty.trim().lowercase(Locale.ROOT)) {
        "easy", "medium", "hard" -> difficulty.trim().lowercase(Locale.ROOT)
        else -> "medium"
    }
}
