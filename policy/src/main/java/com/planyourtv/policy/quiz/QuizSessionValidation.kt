package com.planyourtv.policy.quiz

/**
 * Validation is a server-side (TV-side) boundary. The web page may validate for
 * UX, but a session is accepted only after this object has checked every field.
 */
object QuizSessionValidation {
    const val MIN_TTL_MS = 60 * 60 * 1000L
    const val MAX_TTL_MS = 7 * 24 * 60 * 60 * 1000L

    private val sessionId = Regex("sess_[A-Za-z0-9_-]{8,64}")
    private val questionId = Regex("[A-Za-z][A-Za-z0-9_-]{7,63}")
    private val source = Regex("[a-z][a-z0-9_-]{0,23}")
    private val language = Regex("[a-z]{2,3}(-[A-Za-z]{2,8})?")
    private val date = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val difficulties = setOf("easy", "medium", "hard")

    /** Checks every stored field, including runtime consistency. */
    fun validateStored(session: QuizSession): List<String> {
        val errors = mutableListOf<String>()
        if (session.schemaVersion != QUIZ_SCHEMA_VERSION) errors += "session.schemaVersion: unsupported"
        if (session.originSchemaVersion !in QUIZ_MIN_SCHEMA_VERSION..QUIZ_SCHEMA_VERSION) {
            errors += "session.originSchemaVersion: unsupported"
        }
        if (!sessionId.matches(session.id)) errors += "session.id: invalid"
        if (!source.matches(session.source)) errors += "session.source: invalid"
        if (!language.matches(session.language)) errors += "session.language: invalid"
        if (session.createdAt <= 0) errors += "session.createdAt: invalid"
        if (session.updatedAt < session.createdAt) errors += "session.updatedAt: before createdAt"
        if (session.expiresAt <= 0) errors += "session.expiresAt: invalid"
        validateConfig(session.config, errors)
        validateItems(session.items, errors)
        validateStatus(session, errors)
        return errors
    }

    /**
     * Adds the rules that apply only when a phone page creates/replaces a session.
     * A READY session cannot carry runtime state, and its lifetime is bounded.
     */
    fun validateForPublish(session: QuizSession, nowMs: Long): List<String> {
        val errors = validateStored(session).toMutableList()
        if (session.originSchemaVersion != QUIZ_SCHEMA_VERSION) {
            errors += "session.schemaVersion: only the current schema may be published"
        }
        if (session.status != QuizSessionStatus.DRAFT && session.status != QuizSessionStatus.READY) {
            errors += "session.status: only DRAFT or READY may be published"
        }
        if (session.run != null) errors += "session.run: must be null when publishing"
        if (session.expiresAt < nowMs + MIN_TTL_MS || session.expiresAt > nowMs + MAX_TTL_MS) {
            errors += "session.expiresAt: outside allowed lifetime"
        }
        if (session.updatedAt > nowMs + 5 * 60 * 1000L) errors += "session.updatedAt: in the future"
        return errors
    }

    private fun validateConfig(config: QuizConfig, errors: MutableList<String>) {
        if (config.defaultTimeLimitSec !in 15..300) errors += "config.defaultTimeLimitSec: out of range"
        if (config.minutesPerPoint !in 1..30) errors += "config.minutesPerPoint: out of range"
        if (config.minCorrectCount !in 1..QUIZ_MAX_ITEMS) errors += "config.minCorrectCount: out of range"
        if (config.maxEarnedMinutes !in 1..QUIZ_MAX_EARNED_MINUTES) {
            errors += "config.maxEarnedMinutes: out of range"
        }
        if (config.answerDisplaySec !in QUIZ_MIN_ANSWER_DISPLAY_SEC..QUIZ_MAX_ANSWER_DISPLAY_SEC) {
            errors += "config.answerDisplaySec: out of range"
        }
        if (config.pullCount !in QUIZ_MIN_PULL_COUNT..QUIZ_MAX_PULL_COUNT) {
            errors += "config.pullCount: out of range"
        }
        if (config.minutesPerQuestion !in 1..30) errors += "config.minutesPerQuestion: out of range"
        if (config.pointsPerQuestion != 1) errors += "config.pointsPerQuestion: must be 1"
        // v5 scoring is independent per difficulty. The cap may deliberately be
        // below one question's reward; the web treats that as a soft warning,
        // never a hard save error.
        val scoreRange = 0..QUIZ_MAX_DIFFICULTY_MINUTES
        if (config.easyRewardMinutes !in scoreRange) errors += "config.easyRewardMinutes: out of range"
        if (config.mediumRewardMinutes !in scoreRange) errors += "config.mediumRewardMinutes: out of range"
        if (config.hardRewardMinutes !in scoreRange) errors += "config.hardRewardMinutes: out of range"
        if (config.easyPenaltyMinutes !in scoreRange) errors += "config.easyPenaltyMinutes: out of range"
        if (config.mediumPenaltyMinutes !in scoreRange) errors += "config.mediumPenaltyMinutes: out of range"
        if (config.hardPenaltyMinutes !in scoreRange) errors += "config.hardPenaltyMinutes: out of range"
        if (config.minUnlockMinutes !in 0..QUIZ_MAX_UNLOCK_MINUTES) {
            errors += "config.minUnlockMinutes: out of range"
        }
    }

    private fun validateItems(items: List<QuizItem>, errors: MutableList<String>) {
        if (items.size !in QUIZ_MIN_ITEMS..QUIZ_MAX_ITEMS) {
            errors += "items: count out of range"
            return
        }
        val ids = mutableSetOf<String>()
        items.forEachIndexed { i, item ->
            val prefix = "items[$i]"
            if (!questionId.matches(item.questionId)) errors += "$prefix.questionId: invalid"
            if (!ids.add(item.questionId)) errors += "$prefix.questionId: duplicate"
            if (item.prompt.isBlank() || item.prompt.length > QUIZ_MAX_PROMPT_CHARS) {
                errors += "$prefix.prompt: invalid"
            }
            if (item.options.size !in QUIZ_MIN_OPTIONS..QUIZ_MAX_OPTIONS) {
                errors += "$prefix.options: count out of range"
            }
            if (item.options.any { it.isBlank() || it.length > QUIZ_MAX_OPTION_CHARS }) {
                errors += "$prefix.options: invalid text"
            }
            if (item.options.map { it.trim().lowercase() }.toSet().size != item.options.size) {
                errors += "$prefix.options: duplicate"
            }
            if (item.answerIndex !in item.options.indices) errors += "$prefix.answerIndex: out of range"
            if (item.explanation.length > QUIZ_MAX_EXPLANATION_CHARS) errors += "$prefix.explanation: too long"
            if (item.subject.length > 32 || item.grade.length > 32) errors += "$prefix.metadata: too long"
            if (item.difficulty.isNotEmpty() && item.difficulty !in difficulties) {
                errors += "$prefix.difficulty: invalid"
            }
            if (item.points !in 1..5) errors += "$prefix.points: out of range"
            if (item.timeLimitSec !in 15..300) errors += "$prefix.timeLimitSec: out of range"
            if (item.recommendedTimeSec !in 15..300) errors += "$prefix.recommendedTimeSec: out of range"
            if (item.cacheId.length > 64) errors += "$prefix.cacheId: too long"
            if (item.sourceKind.length > 32) errors += "$prefix.sourceKind: too long"
            if (item.sourceRef.length > 128) errors += "$prefix.sourceRef: too long"
        }
    }

    private fun validateStatus(session: QuizSession, errors: MutableList<String>) {
        // v5 has no minimum-correct gate: minCorrectCount is a legacy/cloud field
        // and may legitimately be larger than one local round. Older records
        // keep the historical consistency check.
        if (session.originSchemaVersion < 5 &&
            session.config.minCorrectCount > session.items.size
        ) {
            errors += "config.minCorrectCount: greater than item count"
        }
        val run = session.run
        when (session.status) {
            QuizSessionStatus.DRAFT, QuizSessionStatus.READY -> {
                if (run != null) errors += "session.run: not allowed for status"
            }
            QuizSessionStatus.RUNNING, QuizSessionStatus.SETTLING,
            QuizSessionStatus.STARTING, QuizSessionStatus.START_FAILED,
            -> {
                if (run == null) {
                    errors += "session.run: required for status"
                    return
                }
                if (run.startedAt <= 0) errors += "run.startedAt: invalid"
                if (!date.matches(run.startedDate)) errors += "run.startedDate: invalid"
                if (run.deadlineAt < run.startedAt) errors += "run.deadlineAt: invalid"
                if (run.index !in 0..session.items.size) errors += "run.index: out of range"
                if (run.correctCount !in 0..session.items.size) errors += "run.correctCount: out of range"
                if (run.earnedPoints !in 0..session.items.sumOf { it.points }) {
                    errors += "run.earnedPoints: out of range"
                }
                val maxEarned = if (session.originSchemaVersion >= 4) {
                    minOf(session.config.maxEarnedMinutes, QUIZ_MAX_EARNED_MINUTES)
                } else {
                    session.config.maxEarnedMinutes
                }
                if (run.earnedMinutes !in 0..maxEarned) {
                    errors += "run.earnedMinutes: out of range"
                }
                if (run.savedRemainingMs < 0 || run.savedAt < 0) errors += "run.saved: invalid"
                if (run.feedbackRemainingMs < 0) errors += "run.feedbackRemainingMs: invalid"
                if (run.feedbackOpen) {
                    if (run.submissions.isEmpty()) errors += "run.feedbackOpen: missing submission"
                    if (run.index < session.items.size && run.feedbackRemainingMs <= 0) {
                        errors += "run.feedbackRemainingMs: required while feedback is open"
                    }
                    if (run.index >= session.items.size && run.feedbackRemainingMs != 0L) {
                        errors += "run.feedbackRemainingMs: unexpected after the last answer"
                    }
                } else if (run.feedbackRemainingMs != 0L) {
                    errors += "run.feedbackRemainingMs: only allowed while feedback is open"
                }
                val known = session.items.map { it.questionId }.toSet()
                val submitted = mutableSetOf<String>()
                run.submissions.forEachIndexed { i, sub ->
                    val item = session.items.firstOrNull { it.questionId == sub.questionId }
                    if (item == null) {
                        errors += "run.submissions[$i].questionId: unknown"
                    } else if (sub.selectedIndex != null && sub.selectedIndex !in item.options.indices) {
                        errors += "run.submissions[$i].selectedIndex: out of range"
                    }
                    if (!submitted.add(sub.questionId)) errors += "run.submissions[$i].questionId: duplicate"
                    if (sub.answeredAt <= 0) errors += "run.submissions[$i].answeredAt: invalid"
                }
                if (run.correctCount != run.submissions.count { it.correct }) {
                    errors += "run.correctCount: inconsistent"
                }
                if (run.earnedPoints != run.submissions.filter { it.correct }.sumOf { sub ->
                        session.items.firstOrNull { it.questionId == sub.questionId }?.points ?: 0
                    }
                ) {
                    errors += "run.earnedPoints: inconsistent"
                }
            }
        }
    }
}
