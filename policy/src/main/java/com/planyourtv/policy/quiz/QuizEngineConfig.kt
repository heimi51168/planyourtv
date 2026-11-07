package com.planyourtv.policy.quiz

/**
 * The only configuration the quiz engine understands. It is deliberately
 * separate from [QuizSourceConfig] so a source cannot leak into timing, reward,
 * or question selection.
 */
data class QuizEngineConfig(
    val pullCount: Int = QUIZ_DEFAULT_PULL_COUNT,
    val defaultTimeLimitSec: Int = 60,
    val answerDisplaySec: Int = QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
    val minutesPerQuestion: Int = 5,
    val shuffleOptions: Boolean = true,
) {
    fun toQuizConfig(legacy: QuizConfig = QuizConfig()): QuizConfig = legacy.copy(
        defaultTimeLimitSec = defaultTimeLimitSec,
        // Keep the legacy field aligned for the binding report and old clients.
        minutesPerPoint = minutesPerQuestion,
        maxEarnedMinutes = legacy.maxEarnedMinutes.coerceIn(1, QUIZ_MAX_EARNED_MINUTES),
        shuffleOptions = shuffleOptions,
        answerDisplaySec = answerDisplaySec,
        pullCount = pullCount,
        minutesPerQuestion = minutesPerQuestion,
        pointsPerQuestion = 1,
    )

    companion object {
        fun from(config: QuizConfig): QuizEngineConfig = QuizEngineConfig(
            pullCount = config.pullCount,
            defaultTimeLimitSec = config.defaultTimeLimitSec,
            answerDisplaySec = config.answerDisplaySec,
            minutesPerQuestion = config.minutesPerQuestion,
            shuffleOptions = config.shuffleOptions,
        )
    }
}
