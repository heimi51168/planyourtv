package com.planyourtv.policy.quiz

/**
 * Semantic validation for QCD v1.
 *
 * [validate] never throws and never returns exception text: every problem is a
 * field code the local parent web can display. Structural decode failures are
 * reported by [QuizConfigDocumentCodec] before this validator runs.
 */
object QuizConfigDocumentValidator {
    fun validate(document: QuizConfigDocument, localWrite: Boolean = false): List<QuizDocIssue> {
        val issues = mutableListOf<QuizDocIssue>()
        validateTopLevel(document, localWrite, issues)
        validateRules(document.rules, document.requirements, issues)
        validateReward(document.reward, document.rules.legacy, issues)
        validateQuota(document.quota, issues)
        validateCalendar(document.calendar, issues)
        validateLevels(document, issues)
        return issues
    }

    private fun validateTopLevel(
        document: QuizConfigDocument,
        localWrite: Boolean,
        issues: MutableList<QuizDocIssue>,
    ) {
        if (document.schemaVersion != QUIZ_DOC_SCHEMA_VERSION) {
            issues.issue("schemaVersion", "UNSUPPORTED_SCHEMA")
        }
        if (document.documentId.isBlank()) issues.issue("documentId", "EMPTY")
        if (document.revision < 0) issues.issue("revision", "NEGATIVE")
        if (document.etag.isNotEmpty() && !ETAG_RE.matches(document.etag)) {
            issues.issue("etag", "INVALID_ETAG")
        }
        if (document.levelsEtag.isNotEmpty() && !ETAG_RE.matches(document.levelsEtag)) {
            issues.issue("levelsEtag", "INVALID_ETAG")
        }
        if (!UPDATED_AT_RE.matches(document.updatedAt)) {
            issues.issue("updatedAt", "INVALID_TIMESTAMP")
        }

        val localOrigin = document.origin == QuizDocOrigin.LOCAL ||
            document.origin == QuizDocOrigin.LOCAL_MIGRATED
        if (localOrigin && !isLocalDocumentId(document.documentId)) {
            issues.issue("documentId", "LOCAL_DOCUMENT_ID_REQUIRED")
        }
        if (localOrigin && document.reward.mode != QuizRewardMode.DIFFICULTY_SECONDS) {
            issues.issue("reward.mode", "LOCAL_REQUIRES_DIFFICULTY_SECONDS")
        }
        if (localWrite) {
            if (!localOrigin) issues.issue("origin", "LOCAL_DOCUMENT_REQUIRED")
            if (document.reward.mode == QuizRewardMode.FLAT_MINUTES) {
                issues.issue("reward.mode", "LOCAL_WRITE_FLAT_MINUTES")
            }
        }
    }

    private fun validateRules(
        rules: QuizRules,
        requirements: QuizRequirements,
        issues: MutableList<QuizDocIssue>,
    ) {
        if (rules.questionCount !in QUIZ_DOC_MIN_QUESTION_COUNT..QUIZ_DOC_MAX_QUESTION_COUNT) {
            issues.issue("rules.questionCount", "OUT_OF_RANGE")
        }
        if (rules.timeLimitSec !in QUIZ_DOC_MIN_TIME_LIMIT_SEC..QUIZ_DOC_MAX_TIME_LIMIT_SEC) {
            issues.issue("rules.timeLimitSec", "OUT_OF_RANGE")
        }
        if (rules.easyTimeLimitSec !in QUIZ_DOC_MIN_TIME_LIMIT_SEC..QUIZ_DOC_MAX_TIME_LIMIT_SEC) {
            issues.issue("rules.easyTimeLimitSec", "OUT_OF_RANGE")
        }
        if (rules.mediumTimeLimitSec !in QUIZ_DOC_MIN_TIME_LIMIT_SEC..QUIZ_DOC_MAX_TIME_LIMIT_SEC) {
            issues.issue("rules.mediumTimeLimitSec", "OUT_OF_RANGE")
        }
        if (rules.hardTimeLimitSec !in QUIZ_DOC_MIN_TIME_LIMIT_SEC..QUIZ_DOC_MAX_TIME_LIMIT_SEC) {
            issues.issue("rules.hardTimeLimitSec", "OUT_OF_RANGE")
        }
        if (rules.quizTvSizeInch !in 24..100) {
            issues.issue("rules.quizTvSizeInch", "OUT_OF_RANGE")
        }
        if (rules.quizTextScalePct !in 60..240) {
            issues.issue("rules.quizTextScalePct", "OUT_OF_RANGE")
        }
        if (rules.feedback.extraHoldSec !in 0..QUIZ_DOC_MAX_EXTRA_HOLD_SEC) {
            issues.issue("rules.feedback.extraHoldSec", "OUT_OF_RANGE")
        }
        if (rules.feedback.answerRevealSec !in
            QUIZ_DOC_MIN_ANSWER_REVEAL_SEC..QUIZ_DOC_MAX_ANSWER_REVEAL_SEC
        ) {
            issues.issue("rules.feedback.answerRevealSec", "OUT_OF_RANGE")
        }
        if (rules.feedback.autoNext &&
            QuizCapability.FEEDBACK_AUTO_NEXT !in requirements.required
        ) {
            issues.issue("rules.feedback.autoNext", "CAPABILITY_NOT_DECLARED")
        }
        val expectedSources = setOf(
            QuizQuestionSource.WRONG,
            QuizQuestionSource.PUBLIC,
            QuizQuestionSource.PERSONAL,
        )
        if (rules.sourceOrder.size != expectedSources.size ||
            rules.sourceOrder.toSet() != expectedSources
        ) {
            issues.issue("rules.sourceOrder", "INVALID_ORDER")
        }
        if (rules.subjects.any { it.isBlank() }) {
            issues.issue("rules.subjects", "EMPTY_ITEM")
        }
        if (rules.subjects.size != rules.subjects.toSet().size) {
            issues.issue("rules.subjects", "DUPLICATE")
        }
        rules.legacy?.let { validateLegacy(it, issues) }
    }

    private fun validateLegacy(legacy: QuizLegacyRules, issues: MutableList<QuizDocIssue>) {
        if (legacy.minCorrectCount !in 1..QUIZ_DOC_MAX_QUESTION_COUNT) {
            issues.issue("rules.legacy.minCorrectCount", "OUT_OF_RANGE")
        }
        if (legacy.minutesPerPoint !in 1..30) {
            issues.issue("rules.legacy.minutesPerPoint", "OUT_OF_RANGE")
        }
        if (legacy.minutesPerQuestion !in 1..30) {
            issues.issue("rules.legacy.minutesPerQuestion", "OUT_OF_RANGE")
        }
        if (legacy.pointsPerQuestion !in 1..5) {
            issues.issue("rules.legacy.pointsPerQuestion", "OUT_OF_RANGE")
        }
    }

    private fun validateReward(
        reward: QuizReward,
        legacy: QuizLegacyRules?,
        issues: MutableList<QuizDocIssue>,
    ) {
        reward.profiles.default?.let { validateProfile("reward.profiles.default", it, issues) }
        reward.profiles.weekday?.let { validateProfile("reward.profiles.weekday", it, issues) }
        reward.profiles.weekend?.let { validateProfile("reward.profiles.weekend", it, issues) }

        when (reward.mode) {
            QuizRewardMode.DIFFICULTY_SECONDS -> {
                if (reward.profiles.default == null) {
                    issues.issue("reward.profiles.default", "REQUIRED")
                }
            }
            QuizRewardMode.FLAT_MINUTES -> {
                if (legacy == null) {
                    issues.issue("rules.legacy", "REQUIRED_FOR_FLAT_MINUTES")
                } else {
                    if (legacy.minutesPerQuestion < 1) {
                        issues.issue("rules.legacy.minutesPerQuestion", "OUT_OF_RANGE")
                    }
                    if (legacy.minCorrectCount < 1) {
                        issues.issue("rules.legacy.minCorrectCount", "OUT_OF_RANGE")
                    }
                }
            }
        }
    }

    /** Reusable profile checks for snapshots stored outside a full QCD. */
    fun rewardProfileIssues(prefix: String, profile: QuizRewardProfile): List<QuizDocIssue> {
        val issues = mutableListOf<QuizDocIssue>()
        validateProfile(prefix, profile, issues)
        return issues
    }

    private fun validateProfile(
        prefix: String,
        profile: QuizRewardProfile,
        issues: MutableList<QuizDocIssue>,
    ) {
        validateSecondMap("$prefix.correctSeconds", profile.correctSeconds, issues)
        validateSecondMap("$prefix.penaltySeconds", profile.penaltySeconds, issues)
        if (profile.maxEarnedSeconds !in
            QUIZ_DOC_MIN_MAX_EARNED_SECONDS..QUIZ_DOC_MAX_MAX_EARNED_SECONDS
        ) {
            issues.issue("$prefix.maxEarnedSeconds", "OUT_OF_RANGE")
        }
        if (profile.minUnlockSeconds !in 0..QUIZ_DOC_MAX_MIN_UNLOCK_SECONDS) {
            issues.issue("$prefix.minUnlockSeconds", "OUT_OF_RANGE")
        }
    }

    private fun validateSecondMap(
        prefix: String,
        value: QuizDifficultySeconds,
        issues: MutableList<QuizDocIssue>,
    ) {
        if (value.easy !in 0..1800) issues.issue("$prefix.EASY", "OUT_OF_RANGE")
        if (value.medium !in 0..1800) issues.issue("$prefix.MEDIUM", "OUT_OF_RANGE")
        if (value.hard !in 0..1800) issues.issue("$prefix.HARD", "OUT_OF_RANGE")
    }

    private fun validateQuota(quota: QuizQuota, issues: MutableList<QuizDocIssue>) {
        // 0 is the explicit "no local daily cap" value; other values must be sane.
        val daily = quota.dailyMaxSeconds
        if (daily != 0 && daily !in QUIZ_DOC_MIN_DAILY_SECONDS..QUIZ_DOC_MAX_DAILY_SECONDS) {
            issues.issue("quota.dailyMaxSeconds", "OUT_OF_RANGE")
        }
    }

    private fun validateCalendar(calendar: QuizCalendar, issues: MutableList<QuizDocIssue>) {
        if (calendar.timezone != QUIZ_DOC_TIMEZONE) {
            issues.issue("calendar.timezone", "UNSUPPORTED_TIMEZONE")
        }
        if (calendar.weekendDays.isEmpty()) {
            issues.issue("calendar.weekendDays", "EMPTY")
        } else {
            if (calendar.weekendDays.any { it !in 1..7 }) {
                issues.issue("calendar.weekendDays", "OUT_OF_RANGE")
            }
            if (calendar.weekendDays.size != calendar.weekendDays.toSet().size) {
                issues.issue("calendar.weekendDays", "DUPLICATE")
            }
        }
    }

    private fun validateLevels(document: QuizConfigDocument, issues: MutableList<QuizDocIssue>) {
        if (document.levels.size > QUIZ_DOC_MAX_LEVELS) {
            issues.issue("levels", "LEVEL_LIMIT_20")
        }
        val ids = mutableSetOf<String>()
        val scopes = mutableSetOf<String>()
        val localOrigin = document.origin == QuizDocOrigin.LOCAL ||
            document.origin == QuizDocOrigin.LOCAL_MIGRATED
        document.levels.forEachIndexed { index, level ->
            val prefix = "levels[$index]"
            validateLevelId(level, prefix, ids, issues)
            if (localOrigin && level.origin == QuizLevelOrigin.CLOUD) {
                issues.issue("$prefix.origin", "CLOUD_LEVEL_IN_LOCAL")
            }
            if (level.sources.isEmpty()) {
                issues.issue("$prefix.sources", "EMPTY")
            }
            if (QuizLevelSource.GRADE in level.sources && level.sources.size != 1) {
                issues.issue("$prefix.sources", "GRADE_MUST_BE_ALONE")
            }
            if (level.subjects.any { it.isBlank() }) {
                issues.issue("$prefix.subjects", "EMPTY_ITEM")
            }
            val allowedScopeTypes = if (level.origin == QuizLevelOrigin.CLOUD) {
                CLOUD_SCOPE_TYPES
            } else {
                LOCAL_SCOPE_TYPES
            }
            if (level.scopeType != null && level.scopeType !in allowedScopeTypes) {
                issues.issue("$prefix.scopeType", "INVALID")
            }
            if (level.unitNos.any { it < 1 }) {
                issues.issue("$prefix.unitNos", "OUT_OF_RANGE")
            }
            if (level.unitNos.size != level.unitNos.toSet().size) {
                issues.issue("$prefix.unitNos", "DUPLICATE")
            }
            if (level.unitGroups.any { it < 1 }) {
                issues.issue("$prefix.unitGroups", "OUT_OF_RANGE")
            }
            if (level.unitGroups.size != level.unitGroups.toSet().size) {
                issues.issue("$prefix.unitGroups", "DUPLICATE")
            }
            if (level.grade != null && level.grade < 1) {
                issues.issue("$prefix.grade", "OUT_OF_RANGE")
            }
            if (level.semester != null && level.semester !in 1..2) {
                issues.issue("$prefix.semester", "OUT_OF_RANGE")
            }
            if (level.difficulty != null && level.difficulty !in DIFFICULTIES) {
                issues.issue("$prefix.difficulty", "INVALID")
            }
            if (level.advisoryQuestionCount != null &&
                level.advisoryQuestionCount !in 1..QUIZ_DOC_MAX_QUESTION_COUNT
            ) {
                issues.issue("$prefix.advisoryQuestionCount", "OUT_OF_RANGE")
            }
            if (level.sortNo < 0) issues.issue("$prefix.sortNo", "OUT_OF_RANGE")
            if (level.questionTypes.size != level.questionTypes.toSet().size) {
                issues.issue("$prefix.questionTypes", "DUPLICATE")
            }
            if (level.origin == QuizLevelOrigin.LOCAL) {
                validateLocalScope(level, prefix, scopes, issues)
            }
        }
    }

    private fun validateLevelId(
        level: QuizLevelDoc,
        prefix: String,
        ids: MutableSet<String>,
        issues: MutableList<QuizDocIssue>,
    ) {
        if (level.levelId.isBlank()) {
            issues.issue("$prefix.levelId", "EMPTY")
            return
        }
        if (!ids.add(level.levelId)) {
            issues.issue("$prefix.levelId", "DUPLICATE")
        }
        val prefixMatches = when (level.origin) {
            QuizLevelOrigin.CLOUD -> level.levelId.startsWith("cloud:")
            QuizLevelOrigin.LOCAL -> level.levelId.startsWith("local:")
        }
        if (!prefixMatches) {
            issues.issue("$prefix.levelId", "PREFIX_MISMATCH")
        }
    }

    private fun validateLocalScope(
        level: QuizLevelDoc,
        prefix: String,
        scopes: MutableSet<String>,
        issues: MutableList<QuizDocIssue>,
    ) {
        val scope = level.localScope
        if (scope == null) {
            issues.issue("$prefix.localScope", "REQUIRED")
            return
        }
        val parsed = QuizAnswerScope.create(
            grade = scope.grade,
            semester = scope.semester,
            subject = scope.subject,
            textbook = scope.textbook,
            scopeType = scope.scopeType,
            unitNo = scope.unitNo,
            difficulty = scope.difficulty,
        )
        if (parsed == null) {
            issues.issue("$prefix.localScope", "INVALID")
            return
        }
        if (!scopes.add(parsed.signature())) {
            issues.issue("$prefix.localScope", "DUPLICATE")
        }
    }

    private fun isLocalDocumentId(documentId: String): Boolean =
        documentId.startsWith(LOCAL_DOCUMENT_PREFIX) &&
            documentId.length > LOCAL_DOCUMENT_PREFIX.length

    private fun MutableList<QuizDocIssue>.issue(field: String, code: String) {
        add(QuizDocIssue(field, code))
    }

    private const val LOCAL_DOCUMENT_PREFIX = "quizcfg:local:"
    // TOPIC（奥数专题）只由云端题库下发；本地模式没有该关卡入口，
    // 因此只在 CLOUD 关卡上放行，LOCAL 关卡与本地文档的校验口径保持不变。
    private val LOCAL_SCOPE_TYPES = setOf("UNIT", "MIDTERM", "FINAL", "REVIEW")
    private val CLOUD_SCOPE_TYPES = LOCAL_SCOPE_TYPES + "TOPIC"
    private val DIFFICULTIES = setOf("EASY", "MEDIUM", "HARD")
    private val ETAG_RE = Regex("^sha256:[0-9a-f]{64}$")
    private val UPDATED_AT_RE =
        Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?(Z|[+-]\\d{2}:\\d{2})$")
}
