package com.planyourtv.policy.quiz

import org.json.JSONObject

/** Current QCD wire schema. An unknown version is rejected as a whole document. */
const val QUIZ_DOC_SCHEMA_VERSION = 1
const val QUIZ_DOC_MAX_LEVELS = 20
/**
 * A round has no product-level question cap. This technical ceiling keeps the
 * session JSON and the encrypted preference record within sane bounds.
 */
const val QUIZ_DOC_MAX_QUESTION_COUNT = 1000
const val QUIZ_DOC_MIN_QUESTION_COUNT = 1
const val QUIZ_DOC_MIN_TIME_LIMIT_SEC = 5
const val QUIZ_DOC_MAX_TIME_LIMIT_SEC = 1800
/**
 * Feedback minimum total stay. The UI has no practical cap; the ceiling is the
 * largest second value whose millisecond form still fits in an Int.
 */
const val QUIZ_DOC_MAX_EXTRA_HOLD_SEC = Int.MAX_VALUE / 1000
/** New TVs keep a wrong answer's explanation on screen for at least 10 seconds. */
const val QUIZ_DEFAULT_EXTRA_HOLD_SEC = 10
const val QUIZ_DOC_MIN_ANSWER_REVEAL_SEC = 5
const val QUIZ_DOC_MAX_ANSWER_REVEAL_SEC = 120
const val QUIZ_DOC_MIN_MAX_EARNED_SECONDS = 60
const val QUIZ_DOC_MAX_MAX_EARNED_SECONDS = 10800
/** Largest whole-minute threshold whose seconds form still fits in an Int. */
const val QUIZ_DOC_MAX_MIN_UNLOCK_SECONDS = (Int.MAX_VALUE / 60) * 60
const val QUIZ_DOC_MAX_DAILY_SECONDS = 36000
const val QUIZ_DOC_MIN_DAILY_SECONDS = 60
const val QUIZ_DOC_TIMEZONE = "Asia/Shanghai"

enum class QuizDocOrigin {
    CLOUD,
    LOCAL,
    CLOUD_SNAPSHOT,
    LOCAL_MIGRATED,
}

enum class QuizDocUpdatedBy {
    PARENT_APP,
    TV_WEB,
    TV_LOCAL_EDIT,
    BINDING_HANDOFF,
    MIGRATION,
}

enum class QuizRewardMode {
    DIFFICULTY_SECONDS,
    FLAT_MINUTES,
}

enum class QuizQuotaEnforcedBy {
    CLOUD,
    LOCAL,
}

enum class QuizJudgeMode {
    LOCAL_OR_SERVER,
}

enum class QuizQuestionType {
    SINGLE,
    FILL,
}

enum class QuizQuestionSource {
    WRONG,
    PUBLIC,
    PERSONAL,
}

enum class QuizLevelSource {
    BANK,
    WRONG,
    PERSONAL,
    GRADE,
    LOCAL,
}

enum class QuizLevelOrigin {
    CLOUD,
    LOCAL,
}

enum class QuizCapability {
    SINGLE,
    FILL,
    REWARD_PROFILES,
    PINYIN,
    CHECKIN_GATE,
    FEEDBACK_AUTO_NEXT,
}

/** A field-level rejection. [field] is safe to show to the local parent web. */
data class QuizDocIssue(
    val field: String,
    val code: String,
) {
    override fun toString(): String = "$field:$code"
}

data class QuizFeedbackRules(
    /**
     * Minimum total stay before Continue unlocks after a wrong or timed-out
     * answer. 0 means the local voice floor is the only minimum; a longer voice
     * clip always wins. A correct answer never holds. New configs default to
     * [QUIZ_DEFAULT_EXTRA_HOLD_SEC] seconds.
     */
    val extraHoldSec: Int = QUIZ_DEFAULT_EXTRA_HOLD_SEC,
    /** Explanation display suggestion. It is not a hold and never blocks continue. */
    val answerRevealSec: Int = 30,
    val autoNext: Boolean = false,
    val extra: JSONObject = JSONObject(),
)

data class QuizLegacyRules(
    val minCorrectCount: Int = 1,
    val minutesPerPoint: Int = 5,
    val minutesPerQuestion: Int = 5,
    val pointsPerQuestion: Int = 1,
    val extra: JSONObject = JSONObject(),
)

data class QuizRules(
    val questionCount: Int = 5,
    val timeLimitSec: Int = 60,
    /** Per-difficulty answer windows; [timeLimitSec] is the unknown-difficulty fallback. */
    val easyTimeLimitSec: Int = 60,
    val mediumTimeLimitSec: Int = 90,
    val hardTimeLimitSec: Int = 120,
    /** Quiz text sizing. AUTO derives sp from screen pixels and the selected TV size. */
    val quizTextAuto: Boolean = true,
    val quizTvSizeInch: Int = 42,
    /** MANUAL only: percent of the 42-inch reference size, 60..240. */
    val quizTextScalePct: Int = 100,
    val feedback: QuizFeedbackRules = QuizFeedbackRules(),
    val shuffleOptions: Boolean = true,
    val showPinyin: Boolean = false,
    val sourceOrder: List<QuizQuestionSource> = listOf(
        QuizQuestionSource.WRONG,
        QuizQuestionSource.PUBLIC,
        QuizQuestionSource.PERSONAL,
    ),
    val subjects: List<String> = emptyList(),
    val legacy: QuizLegacyRules? = null,
    val extra: JSONObject = JSONObject(),
)

data class QuizDifficultySeconds(
    val easy: Int,
    val medium: Int,
    val hard: Int,
    val extra: JSONObject = JSONObject(),
)

data class QuizRewardProfile(
    val correctSeconds: QuizDifficultySeconds,
    val penaltySeconds: QuizDifficultySeconds,
    val maxEarnedSeconds: Int,
    val minUnlockSeconds: Int,
    val extra: JSONObject = JSONObject(),
)

/**
 * A weekday/weekend entry is a complete replacement, never a partial merge.
 * Null means "use default".
 */
data class QuizRewardProfiles(
    val default: QuizRewardProfile? = null,
    val weekday: QuizRewardProfile? = null,
    val weekend: QuizRewardProfile? = null,
    val extra: JSONObject = JSONObject(),
)

data class QuizReward(
    val mode: QuizRewardMode = QuizRewardMode.DIFFICULTY_SECONDS,
    val profiles: QuizRewardProfiles = QuizRewardProfiles(),
    val extra: JSONObject = JSONObject(),
)

data class QuizQuota(
    /** 0 means no local daily cap. Otherwise 60..36000 seconds. */
    val dailyMaxSeconds: Int = 0,
    val enforcedBy: QuizQuotaEnforcedBy = QuizQuotaEnforcedBy.LOCAL,
    val extra: JSONObject = JSONObject(),
)

data class QuizCalendar(
    val timezone: String = QUIZ_DOC_TIMEZONE,
    val weekendDays: List<Int> = listOf(6, 7),
    val extra: JSONObject = JSONObject(),
)

data class QuizQuestionPolicy(
    val allowedTypes: List<QuizQuestionType> = listOf(QuizQuestionType.SINGLE),
    val judgeMode: QuizJudgeMode = QuizJudgeMode.LOCAL_OR_SERVER,
    val extra: JSONObject = JSONObject(),
)

data class QuizRequirements(
    val required: List<QuizCapability> = listOf(
        QuizCapability.SINGLE,
        QuizCapability.REWARD_PROFILES,
    ),
    val optional: List<QuizCapability> = emptyList(),
    val extra: JSONObject = JSONObject(),
)

/**
 * Structural local scope. Legality against the bundled curriculum is checked by
 * [QuizConfigDocumentValidator]; the codec stays pure and testable off-device.
 */
data class QuizLocalScope(
    val grade: Int,
    val semester: Int,
    val subject: String,
    val textbook: String,
    val scopeType: String,
    val unitNo: Int? = null,
    val difficulty: String? = null,
    val extra: JSONObject = JSONObject(),
)

data class QuizLevelDoc(
    val levelId: String,
    val origin: QuizLevelOrigin,
    val name: String = "",
    val enabled: Boolean = true,
    val sortNo: Int = 0,
    val sources: List<QuizLevelSource> = emptyList(),
    val subjects: List<String> = emptyList(),
    val scopeType: String? = null,
    val unitNos: List<Int> = emptyList(),
    val unitGroups: List<Int> = emptyList(),
    val grade: Int? = null,
    val semester: Int? = null,
    val textbookId: Long? = null,
    val textbook: String? = null,
    val difficulty: String? = null,
    val advisoryQuestionCount: Int? = null,
    val questionTypes: List<QuizQuestionType> = emptyList(),
    /** Raw cloud source scopes. The TV never resolves questions from this block. */
    val sourceScopes: JSONObject? = null,
    val localScope: QuizLocalScope? = null,
    val extra: JSONObject = JSONObject(),
)

/**
 * Canonical quiz configuration document (QCD v1).
 *
 * Unknown top-level and section fields are preserved so a round trip through the
 * TV cannot silently drop cloud fields it does not execute yet. [extensions] is
 * preserved verbatim.
 */
data class QuizConfigDocument(
    val schemaVersion: Int = QUIZ_DOC_SCHEMA_VERSION,
    val documentId: String,
    val revision: Long,
    val levelsEtag: String,
    val etag: String,
    val origin: QuizDocOrigin,
    val updatedAt: String,
    val updatedBy: QuizDocUpdatedBy,
    val enabled: Boolean,
    val rules: QuizRules,
    val reward: QuizReward,
    val quota: QuizQuota,
    val calendar: QuizCalendar,
    val questionPolicy: QuizQuestionPolicy,
    val requirements: QuizRequirements,
    val levels: List<QuizLevelDoc>,
    val extensions: JSONObject = JSONObject(),
    val extraTopLevel: JSONObject = JSONObject(),
)


/**
 * The answer window for one question. Unknown difficulties (including an empty
 * legacy value) use the document's default so old banks keep their old timing.
 */
fun QuizRules.timeLimitSecFor(difficulty: String): Int = when (difficulty.trim().lowercase()) {
    "easy" -> easyTimeLimitSec
    "hard" -> hardTimeLimitSec
    "medium" -> mediumTimeLimitSec
    else -> timeLimitSec
}
