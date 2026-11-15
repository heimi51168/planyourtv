package com.planyourtv.policy.quiz

/**
 * Resolves the minimum feedback stay for one recorded answer.
 *
 * v6 freezes the nominal hold into the submission, so a later config edit cannot
 * shorten the page this child is already reading. v5 records have no frozen
 * value and keep their historical answerDisplaySec semantics until they finish.
 */
object QuizFeedbackHold {
    /** The value to store on a new submission; null for v5 rounds. */
    fun frozenHoldMs(
        correct: Boolean,
        timedOut: Boolean,
        extraHoldSec: Int,
        originSchemaVersion: Int,
    ): Long? = when {
        originSchemaVersion < 6 -> null
        correct -> 0L
        else -> QuizVoiceHold.holdMs(
            correct = false,
            timedOut = timedOut,
            extraHoldSec = extraHoldSec,
        )
    }

    /**
     * The effective stay for a stored submission. A correct answer never holds,
     * even if a corrupt record carries a nonzero value.
     */
    fun effectiveHoldMs(submission: QuizSubmission, answerDisplaySec: Int): Long {
        if (submission.correct) return 0L
        return submission.feedbackHoldMs
            ?: (normalizeAnswerHoldSec(answerDisplaySec) * 1_000L)
    }

    fun isLocked(submission: QuizSubmission, answerDisplaySec: Int, nowMs: Long): Boolean {
        val holdMs = effectiveHoldMs(submission, answerDisplaySec)
        return holdMs > 0L && nowMs < submission.answeredAt + holdMs
    }
}
