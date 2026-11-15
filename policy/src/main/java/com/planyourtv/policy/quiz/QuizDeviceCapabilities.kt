package com.planyourtv.policy.quiz

/**
 * TV v1 execution limits. A document that exceeds them is rejected as a whole;
 * callers must keep the previous active document instead of trimming fields.
 */
object QuizDeviceCapabilities {
    const val MAX_LEVELS = QUIZ_DOC_MAX_LEVELS
    const val CLOUD_MAX_QUESTIONS = 10
    const val LOCAL_MAX_QUESTIONS = QUIZ_DOC_MAX_QUESTION_COUNT
    /** Same ceiling as the QCD validator; a longer timer is already invalid there. */
    const val MAX_TIME_LIMIT_SEC = QUIZ_DOC_MAX_TIME_LIMIT_SEC

    fun issues(document: QuizConfigDocument, cloudRound: Boolean): List<QuizDocIssue> {
        val issues = mutableListOf<QuizDocIssue>()
        val types = document.questionPolicy.allowedTypes +
            document.levels.flatMap { it.questionTypes }
        if (QuizQuestionType.FILL in types ||
            QuizCapability.FILL in document.requirements.required
        ) {
            issues.issue("questionPolicy.allowedTypes", "UNSUPPORTED_FILL")
        }
        if (document.rules.feedback.autoNext ||
            QuizCapability.FEEDBACK_AUTO_NEXT in document.requirements.required
        ) {
            issues.issue("rules.feedback.autoNext", "UNSUPPORTED_AUTO_NEXT")
        }
        if (QuizCapability.PINYIN in document.requirements.required) {
            issues.issue("requirements.required", "UNSUPPORTED_PINYIN")
        }
        if (cloudRound && document.rules.questionCount > CLOUD_MAX_QUESTIONS) {
            issues.issue("rules.questionCount", "CLOUD_QUESTION_LIMIT_10")
        }
        if (document.rules.timeLimitSec > MAX_TIME_LIMIT_SEC) {
            issues.issue("rules.timeLimitSec", "TIME_LIMIT_OVER_MAX")
        }
        if (document.levels.size > MAX_LEVELS) {
            issues.issue("levels", "LEVEL_LIMIT_20")
        }
        return issues
    }

    private fun MutableList<QuizDocIssue>.issue(field: String, code: String) {
        add(QuizDocIssue(field, code))
    }
}
