package com.planyourtv.policy.quiz

import kotlin.math.min

/** The settled score of one local v6 round, in canonical seconds. */
data class QuizScoreV6(
    val correctCount: Int,
    val grossSeconds: Int,
    val penaltySeconds: Int,
    val netSeconds: Int,
    val cappedSeconds: Int,
    val thresholdSeconds: Int,
    val unlockReached: Boolean,
    val earnedSeconds: Int,
)

/**
 * QCD v1 local settlement. The rules are identical to [QuizScoring]: unknown
 * difficulty is medium, duplicate submissions count once, a timed-out answer is
 * wrong, net cannot be negative, and a missed unlock threshold pays zero. Only
 * the unit changes, so a migrated profile with minute values times 60 produces
 * exactly the same payout as the v5 engine did.
 */
object QuizScoringV6 {
    fun score(
        items: List<QuizItem>,
        submissions: List<QuizSubmission>,
        profile: QuizRewardProfile,
    ): QuizScoreV6 {
        val byId = items.associateBy { it.questionId }
        var correctCount = 0
        var gross = 0
        var penalty = 0
        submissions.distinctBy { it.questionId }.forEach { submission ->
            val item = byId[submission.questionId] ?: return@forEach
            val correct = submission.correct && !submission.timedOut
            if (correct) {
                correctCount += 1
                gross += correctSeconds(item.difficulty, profile)
            } else {
                penalty += penaltySeconds(item.difficulty, profile)
            }
        }
        val net = (gross - penalty).coerceAtLeast(0)
        val capped = min(net, profile.maxEarnedSeconds)
        val threshold = profile.minUnlockSeconds
        val reached = threshold == 0 || capped >= threshold
        return QuizScoreV6(
            correctCount = correctCount,
            grossSeconds = gross,
            penaltySeconds = penalty,
            netSeconds = net,
            cappedSeconds = capped,
            thresholdSeconds = threshold,
            unlockReached = reached,
            earnedSeconds = if (reached) capped else 0,
        )
    }

    fun correctSeconds(difficulty: String, profile: QuizRewardProfile): Int =
        when (QuizScoring.normalize(difficulty)) {
            "easy" -> profile.correctSeconds.easy
            "hard" -> profile.correctSeconds.hard
            else -> profile.correctSeconds.medium
        }

    fun penaltySeconds(difficulty: String, profile: QuizRewardProfile): Int =
        when (QuizScoring.normalize(difficulty)) {
            "easy" -> profile.penaltySeconds.easy
            "hard" -> profile.penaltySeconds.hard
            else -> profile.penaltySeconds.medium
        }
}
