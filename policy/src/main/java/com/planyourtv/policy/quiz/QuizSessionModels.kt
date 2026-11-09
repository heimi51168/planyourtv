package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** The wire schema stored under `quiz.session`. Bump this for any incompatible edit. */
const val QUIZ_SCHEMA_VERSION = 5
/** v2 differs only by the removed daily-attempt limit, so old records remain readable. */
const val QUIZ_MIN_SCHEMA_VERSION = 2
const val QUIZ_MAX_EARNED_MINUTES = 180
const val QUIZ_MAX_DIFFICULTY_MINUTES = 30
const val QUIZ_MAX_UNLOCK_MINUTES = 180
const val QUIZ_MIN_PULL_COUNT = 5
const val QUIZ_MAX_PULL_COUNT = 50
const val QUIZ_DEFAULT_PULL_COUNT = 5
const val QUIZ_MAX_ITEMS = 50
const val QUIZ_MIN_ITEMS = 1
const val QUIZ_MIN_OPTIONS = 2
const val QUIZ_MAX_OPTIONS = 6
const val QUIZ_MAX_SESSION_CHARS = 512 * 1024
const val QUIZ_MAX_PROMPT_CHARS = 200
const val QUIZ_MAX_OPTION_CHARS = 60
const val QUIZ_MAX_EXPLANATION_CHARS = 600
const val QUIZ_MIN_ANSWER_DISPLAY_SEC = 5
const val QUIZ_MAX_ANSWER_DISPLAY_SEC = 120
const val QUIZ_DEFAULT_ANSWER_DISPLAY_SEC = 30

enum class QuizSessionStatus {
    DRAFT,
    READY,
    RUNNING,
    SETTLING,
    STARTING,
    START_FAILED,
}

/**
 * The only quiz data TV owns. There is deliberately no bank, history, or candidate
 * collection here: a challenge is one session, and it is deleted when it ends.
 */
data class QuizConfig(
    val defaultTimeLimitSec: Int = 60,
    /** Legacy field kept for schema v2/v3 sessions and for the binding report. */
    val minutesPerPoint: Int = 5,
    val minCorrectCount: Int = 1,
    val maxEarnedMinutes: Int = 60,
    val shuffleOptions: Boolean = true,
    val answerDisplaySec: Int = QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
    /** v4: how many questions one round reserves from the cache. */
    val pullCount: Int = QUIZ_DEFAULT_PULL_COUNT,
    /** v4: minutes earned per correctly answered question; old rounds and cloud only. */
    val minutesPerQuestion: Int = 5,
    /** v4 internal compatibility assumption. Never displayed to a user. */
    val pointsPerQuestion: Int = 1,
    /** v5: minutes awarded for a correct answer at each difficulty. */
    val easyRewardMinutes: Int = 1,
    val mediumRewardMinutes: Int = 2,
    val hardRewardMinutes: Int = 5,
    /** v5: minutes deducted for a wrong or timed-out answer at each difficulty. */
    val easyPenaltyMinutes: Int = 0,
    val mediumPenaltyMinutes: Int = 0,
    val hardPenaltyMinutes: Int = 0,
    /** v5: minimum net minutes needed to unlock the session; 0 means no gate. */
    val minUnlockMinutes: Int = 0,
)

data class QuizItem(
    val questionId: String,
    val prompt: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String = "",
    val subject: String = "",
    val grade: String = "",
    val difficulty: String = "",
    /**
     * 出题与答题范围共用的规范字段。旧题库没有这些字段，保持 null/空串表示
     * “范围未知”，自定义答题范围不会把这类旧题当成匹配。
     */
    val scopeGrade: Int? = null,
    val scopeSemester: Int? = null,
    val scopeSubject: String = "",
    val scopeTextbook: String = "",
    val scopeType: String = "",
    val scopeUnitNo: Int? = null,
    val points: Int = 1,
    val timeLimitSec: Int = 60,
    val recommendedTimeSec: Int = 60,
    /** Cache identity. Empty for a legacy session that predates the cache. */
    val cacheId: String = "",
    /** Audit-only metadata. The engine never branches on it. */
    val sourceKind: String = "",
    val sourceRef: String = "",
    /**
     * Shuffled display position -> index in the published option order. Empty
     * when the options are shown in their published order. The cloud exam judges
     * in the published order, so a submission is mapped back through this before
     * it is uploaded; the UI keeps using the display index.
     */
    val optionOrder: List<Int> = emptyList(),
)

data class QuizSubmission(
    val questionId: String,
    val selectedIndex: Int?,
    val correct: Boolean,
    val timedOut: Boolean,
    val answeredAt: Long,
)

/**
 * Runtime fields live in the same encrypted record as the questions so a process
 * restart cannot re-roll the challenge. [savedRemainingMs] is written only when
 * the app leaves the foreground or the process is about to stop; the live
 * deadline is authoritative while the challenge screen is visible.
 */
data class QuizRun(
    val startedAt: Long = 0L,
    val startedDate: String = "",
    val index: Int = 0,
    val correctCount: Int = 0,
    val earnedPoints: Int = 0,
    val earnedMinutes: Int = 0,
    val deadlineAt: Long = 0L,
    val savedRemainingMs: Long = 0L,
    val savedAt: Long = 0L,
    /**
     * Feedback for the last submission is on screen. The next question's timer
     * is paused until [closeFeedback] runs; [feedbackRemainingMs] holds its
     * full remaining time across a process restart.
     */
    val feedbackOpen: Boolean = false,
    val feedbackRemainingMs: Long = 0L,
    val startErrorCode: String? = null,
    /**
     * Hosted settlement refusal in the backend's own user-facing words. Only
     * written together with [startErrorCode] = SERVER_REJECTED; it is never an
     * exception message or transport detail.
     */
    val startErrorMessage: String? = null,
    /**
     * Cloud quiz config version this attempt started with. Hosted settlement sends it
     * back so a parent edit made mid-round is an explicit rejection, not a different
     * reward than the one the child saw. 0 for records written before this field.
     */
    val configVersion: Long = 0L,
    val submissions: List<QuizSubmission> = emptyList(),
)

data class QuizSession(
    val schemaVersion: Int = QUIZ_SCHEMA_VERSION,
    /**
     * The schema version read from disk. It stays below [schemaVersion] for a
     * record written by an older build, so reward compatibility never depends
     * on guessing from optional fields.
     */
    val originSchemaVersion: Int = QUIZ_SCHEMA_VERSION,
    val id: String,
    val source: String = "web",
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
    val status: QuizSessionStatus,
    val language: String = "zh",
    val config: QuizConfig = QuizConfig(),
    val items: List<QuizItem> = emptyList(),
    val run: QuizRun? = null,
) {
    /** Content identity for idempotent PUTs; updatedAt and runtime state do not count. */
    fun contentHash(): String {
        val canonical = QuizSessionCodec.encode(copy(updatedAt = 0L, run = null, status = QuizSessionStatus.DRAFT))
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

sealed interface QuizDecodeResult {
    data class Success(val session: QuizSession) : QuizDecodeResult
    data class Failure(val errors: List<String>) : QuizDecodeResult
}

/**
 * JSON is encoded and decoded here, in the policy module, so the same contract is
 * used by the phone API, the TV runtime, and JVM tests. It never logs a payload.
 */
object QuizSessionCodec {
    fun encode(session: QuizSession): String = JSONObject()
        .put("schemaVersion", session.schemaVersion)
        .put("originSchemaVersion", session.originSchemaVersion)
        .put("id", session.id)
        .put("source", session.source)
        .put("createdAt", session.createdAt)
        .put("updatedAt", session.updatedAt)
        .put("expiresAt", session.expiresAt)
        .put("status", session.status.name)
        .put("language", session.language)
        .put("config", encodeConfig(session.config))
        .put("items", JSONArray().apply { session.items.forEach { put(encodeItem(it)) } })
        .put("run", session.run?.let(::encodeRun) ?: JSONObject.NULL)
        .toString()

    fun decode(raw: String): QuizDecodeResult {
        if (raw.isBlank()) return QuizDecodeResult.Failure(listOf("session: empty"))
        if (raw.length > QUIZ_MAX_SESSION_CHARS) {
            return QuizDecodeResult.Failure(listOf("session: too large"))
        }
        val root = runCatching { JSONObject(raw) }.getOrElse {
            return QuizDecodeResult.Failure(listOf("session: invalid json"))
        }
        val errors = mutableListOf<String>()
        val session = parseSession(root, errors)
        return when {
            errors.isNotEmpty() || session == null -> QuizDecodeResult.Failure(errors.ifEmpty { listOf("session: invalid") })
            else -> {
                val validation = QuizSessionValidation.validateStored(session)
                if (validation.isEmpty()) QuizDecodeResult.Success(session)
                else QuizDecodeResult.Failure(validation)
            }
        }
    }

    private fun parseSession(root: JSONObject, errors: MutableList<String>): QuizSession? {
        val schema = root.requiredInt("schemaVersion", "session", errors)
        val id = root.requiredString("id", "session", errors)
        val source = root.requiredString("source", "session", errors)
        val createdAt = root.requiredLong("createdAt", "session", errors)
        val updatedAt = root.requiredLong("updatedAt", "session", errors)
        val expiresAt = root.requiredLong("expiresAt", "session", errors)
        val statusText = root.requiredString("status", "session", errors)
        val language = root.requiredString("language", "session", errors)
        val configObject = root.requiredObject("config", "session", errors)
        val itemsArray = root.requiredArray("items", "session", errors)
        val config = configObject?.let { parseConfig(it, errors) }
        val items = itemsArray?.let { parseItems(it, errors) }
        val run = when {
            !root.has("run") || root.isNull("run") -> null
            else -> root.requiredObject("run", "session", errors)?.let { parseRun(it, errors) }
        }
        val status = statusText?.let {
            runCatching { QuizSessionStatus.valueOf(it) }.getOrElse {
                errors += "session.status: invalid"
                null
            }
        }
        if (schema == null || schema !in QUIZ_MIN_SCHEMA_VERSION..QUIZ_SCHEMA_VERSION) {
            errors += "session.schemaVersion: unsupported"
        }
        if (id == null || !SESSION_ID_RE.matches(id)) errors += "session.id: invalid"
        if (source == null || !SOURCE_RE.matches(source)) errors += "session.source: invalid"
        if (language == null || !LANGUAGE_RE.matches(language)) errors += "session.language: invalid"
        if (createdAt == null || createdAt <= 0) errors += "session.createdAt: invalid"
        if (updatedAt == null || updatedAt <= 0) errors += "session.updatedAt: invalid"
        if (expiresAt == null || expiresAt <= 0) errors += "session.expiresAt: invalid"
        if (createdAt != null && updatedAt != null && updatedAt < createdAt) {
            errors += "session.updatedAt: before createdAt"
        }
        if (config == null || items == null || status == null) return null
        if (id == null || source == null || language == null || createdAt == null ||
            updatedAt == null || expiresAt == null
        ) {
            return null
        }
        if (run != null && status !in RUNTIME_STATUSES) errors += "session.run: not allowed for status"
        if (run == null && status in RUNTIME_STATUSES) errors += "session.run: required for status"
        return QuizSession(
            // v2/v3 records are upgraded on read so the next save writes the
            // current shape, while [originSchemaVersion] preserves their reward
            // semantics until the session ends.
            schemaVersion = QUIZ_SCHEMA_VERSION,
            originSchemaVersion = schema ?: QUIZ_SCHEMA_VERSION,
            id = id,
            source = source,
            createdAt = createdAt,
            updatedAt = updatedAt,
            expiresAt = expiresAt,
            status = status,
            language = language,
            config = config,
            items = items,
            run = run,
        )
    }

    private fun parseConfig(root: JSONObject, errors: MutableList<String>): QuizConfig? {
        val defaultTime = root.requiredInt("defaultTimeLimitSec", "config", errors)
        val minutesPerPoint = root.requiredInt("minutesPerPoint", "config", errors)
        val minCorrect = root.requiredInt("minCorrectCount", "config", errors)
        val maxEarned = root.requiredInt("maxEarnedMinutes", "config", errors)
        val shuffle = root.requiredBoolean("shuffleOptions", "config", errors)
        // Optional so records written before configurable answer feedback stay readable.
        val answerDisplay = root.optInt("answerDisplaySec", QUIZ_DEFAULT_ANSWER_DISPLAY_SEC)
        // v4 fields are optional on read: a v2/v3 record derives them from the
        // legacy fields instead of being rejected.
        val pullCount = root.optInt("pullCount", QUIZ_DEFAULT_PULL_COUNT)
        val minutesPerQuestion = root.optInt("minutesPerQuestion", minutesPerPoint ?: 5)
        val pointsPerQuestion = root.optInt("pointsPerQuestion", 1)
        // v5 difficulty scoring is optional on read: old v4 records and cached
        // pages simply fall back to the product defaults. v4 rounds still use
        // their own settlement formula, selected by originSchemaVersion.
        val easyReward = optionalInt(root, "easyRewardMinutes", "config", errors) ?: 1
        val mediumReward = optionalInt(root, "mediumRewardMinutes", "config", errors) ?: 2
        val hardReward = optionalInt(root, "hardRewardMinutes", "config", errors) ?: 5
        val easyPenalty = optionalInt(root, "easyPenaltyMinutes", "config", errors) ?: 0
        val mediumPenalty = optionalInt(root, "mediumPenaltyMinutes", "config", errors) ?: 0
        val hardPenalty = optionalInt(root, "hardPenaltyMinutes", "config", errors) ?: 0
        val minUnlock = optionalInt(root, "minUnlockMinutes", "config", errors) ?: 0
        if (defaultTime == null || minutesPerPoint == null || minCorrect == null ||
            maxEarned == null || shuffle == null
        ) {
            return null
        }
        if (defaultTime !in TIME_LIMIT_RANGE) errors += "config.defaultTimeLimitSec: out of range"
        if (minutesPerPoint !in 1..30) errors += "config.minutesPerPoint: out of range"
        if (minCorrect !in 1..QUIZ_MAX_ITEMS) errors += "config.minCorrectCount: out of range"
        if (maxEarned !in 1..QUIZ_MAX_EARNED_MINUTES) errors += "config.maxEarnedMinutes: out of range"
        if (answerDisplay !in QUIZ_MIN_ANSWER_DISPLAY_SEC..QUIZ_MAX_ANSWER_DISPLAY_SEC) {
            errors += "config.answerDisplaySec: out of range"
        }
        if (pullCount !in QUIZ_MIN_PULL_COUNT..QUIZ_MAX_PULL_COUNT) {
            errors += "config.pullCount: out of range"
        }
        if (minutesPerQuestion !in 1..30) errors += "config.minutesPerQuestion: out of range"
        if (pointsPerQuestion != 1) errors += "config.pointsPerQuestion: must be 1"
        val scoreRange = 0..QUIZ_MAX_DIFFICULTY_MINUTES
        if (easyReward !in scoreRange) errors += "config.easyRewardMinutes: out of range"
        if (mediumReward !in scoreRange) errors += "config.mediumRewardMinutes: out of range"
        if (hardReward !in scoreRange) errors += "config.hardRewardMinutes: out of range"
        if (easyPenalty !in scoreRange) errors += "config.easyPenaltyMinutes: out of range"
        if (mediumPenalty !in scoreRange) errors += "config.mediumPenaltyMinutes: out of range"
        if (hardPenalty !in scoreRange) errors += "config.hardPenaltyMinutes: out of range"
        if (minUnlock !in 0..QUIZ_MAX_UNLOCK_MINUTES) {
            errors += "config.minUnlockMinutes: out of range"
        }
        return QuizConfig(
            defaultTimeLimitSec = defaultTime,
            minutesPerPoint = minutesPerPoint,
            minCorrectCount = minCorrect,
            maxEarnedMinutes = maxEarned,
            shuffleOptions = shuffle,
            answerDisplaySec = answerDisplay,
            pullCount = pullCount,
            minutesPerQuestion = minutesPerQuestion,
            pointsPerQuestion = pointsPerQuestion,
            easyRewardMinutes = easyReward,
            mediumRewardMinutes = mediumReward,
            hardRewardMinutes = hardReward,
            easyPenaltyMinutes = easyPenalty,
            mediumPenaltyMinutes = mediumPenalty,
            hardPenaltyMinutes = hardPenalty,
            minUnlockMinutes = minUnlock,
        )
    }

    private fun parseItems(root: JSONArray, errors: MutableList<String>): List<QuizItem>? {
        if (root.length() !in QUIZ_MIN_ITEMS..QUIZ_MAX_ITEMS) {
            errors += "items: count out of range"
            return null
        }
        val items = ArrayList<QuizItem>(root.length())
        val ids = mutableSetOf<String>()
        for (i in 0 until root.length()) {
            val item = root.optJSONObject(i)
            if (item == null) {
                errors += "items[$i]: not an object"
                continue
            }
            val prefix = "items[$i]"
            val id = item.requiredString("questionId", prefix, errors)
            val prompt = item.requiredString("prompt", prefix, errors)
            val optionsArray = item.requiredArray("options", prefix, errors)
            val answer = item.requiredInt("answerIndex", prefix, errors)
            val explanation = item.optString("explanation", "")
            val subject = item.optString("subject", "")
            val grade = item.optString("grade", "")
            val difficulty = item.optString("difficulty", "")
            val scopeGrade = optionalInt(item, "scopeGrade", prefix, errors)
            val scopeSemester = optionalInt(item, "scopeSemester", prefix, errors)
            val scopeSubject = item.optString("scopeSubject", "").trim().uppercase()
            val scopeTextbook = item.optString("scopeTextbook", "").trim().uppercase()
            val scopeType = item.optString("scopeType", "").trim().uppercase()
            val scopeUnitNo = optionalInt(item, "scopeUnitNo", prefix, errors)
            val points = item.requiredInt("points", prefix, errors)
            val timeLimit = item.requiredInt("timeLimitSec", prefix, errors)
            val recommended = item.optInt("recommendedTimeSec", timeLimit ?: 60)
            val cacheId = item.optString("cacheId", "").take(64)
            val sourceKind = item.optString("sourceKind", "").take(32)
            val sourceRef = item.optString("sourceRef", "").take(128)
            if (id == null || prompt == null || optionsArray == null || answer == null ||
                points == null || timeLimit == null
            ) {
                continue
            }
            val options = ArrayList<String>(optionsArray.length())
            for (j in 0 until optionsArray.length()) {
                val option = optionsArray.opt(j)
                if (option !is String) {
                    errors += "$prefix.options[$j]: not a string"
                } else {
                    options += option
                }
            }
            if (!ids.add(id)) errors += "$prefix.questionId: duplicate"
            if (!QUESTION_ID_RE.matches(id)) errors += "$prefix.questionId: invalid"
            if (prompt.isBlank() || prompt.length > QUIZ_MAX_PROMPT_CHARS) errors += "$prefix.prompt: invalid"
            if (options.size !in QUIZ_MIN_OPTIONS..QUIZ_MAX_OPTIONS) errors += "$prefix.options: count out of range"
            if (options.any { it.isBlank() || it.length > QUIZ_MAX_OPTION_CHARS }) {
                errors += "$prefix.options: invalid text"
            }
            if (options.map { it.trim().lowercase() }.toSet().size != options.size) {
                errors += "$prefix.options: duplicate"
            }
            if (answer !in options.indices) errors += "$prefix.answerIndex: out of range"
            if (explanation.length > QUIZ_MAX_EXPLANATION_CHARS) errors += "$prefix.explanation: too long"
            if (subject.length > 32 || grade.length > 32 || difficulty.length > 16) {
                errors += "$prefix.metadata: too long"
            }
            if (difficulty.isNotEmpty() && difficulty !in DIFFICULTIES) {
                errors += "$prefix.difficulty: invalid"
            }
            val hasAnyScope = scopeGrade != null || scopeSemester != null ||
                scopeSubject.isNotEmpty() || scopeTextbook.isNotEmpty() ||
                scopeType.isNotEmpty() || scopeUnitNo != null
            val normalizedScope = if (hasAnyScope) {
                if (scopeGrade == null || scopeSemester == null || scopeSubject.isEmpty() ||
                    scopeTextbook.isEmpty() || scopeType.isEmpty()
                ) {
                    errors += "$prefix.scope: incomplete"
                    null
                } else {
                    QuizAnswerScope.create(
                        grade = scopeGrade,
                        semester = scopeSemester,
                        subject = scopeSubject,
                        textbook = scopeTextbook,
                        scopeType = scopeType,
                        unitNo = scopeUnitNo,
                        difficulty = null,
                    ).also {
                        if (it == null) errors += "$prefix.scope: invalid"
                    }
                }
            } else {
                null
            }
            if (points !in 1..5) errors += "$prefix.points: out of range"
            if (timeLimit !in TIME_LIMIT_RANGE) errors += "$prefix.timeLimitSec: out of range"
            if (recommended !in TIME_LIMIT_RANGE) errors += "$prefix.recommendedTimeSec: out of range"
            val optionOrder = item.optJSONArray("optionOrder")?.let { orderArray ->
                val order = (0 until orderArray.length()).map { orderArray.optInt(it, -1) }
                when {
                    order.isEmpty() -> emptyList()
                    order.size == options.size && order.toSet() == options.indices.toSet() -> order
                    else -> {
                        errors += "$prefix.optionOrder: invalid"
                        emptyList()
                    }
                }
            } ?: emptyList()
            items += QuizItem(
                questionId = id,
                prompt = prompt,
                options = options,
                answerIndex = answer,
                explanation = explanation,
                subject = subject,
                grade = grade,
                difficulty = difficulty,
                scopeGrade = normalizedScope?.grade ?: scopeGrade,
                scopeSemester = normalizedScope?.semester ?: scopeSemester,
                scopeSubject = normalizedScope?.subject ?: scopeSubject,
                scopeTextbook = normalizedScope?.textbook ?: scopeTextbook,
                scopeType = normalizedScope?.scopeType ?: scopeType,
                scopeUnitNo = normalizedScope?.unitNo ?: scopeUnitNo,
                points = points,
                timeLimitSec = timeLimit,
                recommendedTimeSec = recommended,
                cacheId = cacheId,
                sourceKind = sourceKind,
                sourceRef = sourceRef,
                optionOrder = optionOrder,
            )
        }
        return if (errors.isEmpty()) items else null
    }

    private fun parseRun(root: JSONObject, errors: MutableList<String>): QuizRun? {
        val startedAt = root.requiredLong("startedAt", "run", errors)
        val startedDate = root.requiredString("startedDate", "run", errors)
        val index = root.requiredInt("index", "run", errors)
        val correct = root.requiredInt("correctCount", "run", errors)
        val points = root.requiredInt("earnedPoints", "run", errors)
        val minutes = root.requiredInt("earnedMinutes", "run", errors)
        val deadline = root.requiredLong("deadlineAt", "run", errors)
        val savedRemaining = root.optLong("savedRemainingMs", 0L)
        val savedAt = root.optLong("savedAt", 0L)
        val feedbackOpen = root.optBoolean("feedbackOpen", false)
        val feedbackRemaining = root.optLong("feedbackRemainingMs", 0L)
        val errorCode = root.optString("startErrorCode", "").ifBlank { null }
        val errorMessage = root.optString("startErrorMessage", "").ifBlank { null }
        val configVersion = root.optLong("configVersion", 0L)
        val subs = ArrayList<QuizSubmission>()
        val array = root.optionalArray("submissions", errors)
        if (array != null) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                if (obj == null) {
                    errors += "run.submissions[$i]: not an object"
                    continue
                }
                val prefix = "run.submissions[$i]"
                val questionId = obj.requiredString("questionId", prefix, errors)
                val selected = if (obj.isNull("selectedIndex")) null else obj.requiredInt("selectedIndex", prefix, errors)
                val isCorrect = obj.requiredBoolean("correct", prefix, errors)
                val timedOut = obj.requiredBoolean("timedOut", prefix, errors)
                val answeredAt = obj.requiredLong("answeredAt", prefix, errors)
                if (questionId != null && isCorrect != null && timedOut != null && answeredAt != null) {
                    subs += QuizSubmission(questionId, selected, isCorrect, timedOut, answeredAt)
                }
            }
        }
        if (startedAt == null || startedDate == null || index == null || correct == null ||
            points == null || minutes == null || deadline == null
        ) {
            return null
        }
        if (!DATE_RE.matches(startedDate)) errors += "run.startedDate: invalid"
        if (startedAt <= 0 || deadline <= 0 || deadline < startedAt) errors += "run.deadlineAt: invalid"
        if (index !in 0..QUIZ_MAX_ITEMS) errors += "run.index: out of range"
        if (correct !in 0..QUIZ_MAX_ITEMS || points !in 0..QUIZ_MAX_ITEMS * 5) {
            errors += "run.score: out of range"
        }
        if (minutes !in 0..180) errors += "run.earnedMinutes: out of range"
        if (savedRemaining < 0 || savedAt < 0) errors += "run.saved: invalid"
        if (feedbackRemaining < 0) errors += "run.feedbackRemainingMs: invalid"
        if (feedbackOpen && subs.isEmpty()) errors += "run.feedbackOpen: missing submission"
        return QuizRun(
            startedAt = startedAt,
            startedDate = startedDate,
            index = index,
            correctCount = correct,
            earnedPoints = points,
            earnedMinutes = minutes,
            deadlineAt = deadline,
            savedRemainingMs = savedRemaining,
            savedAt = savedAt,
            feedbackOpen = feedbackOpen,
            feedbackRemainingMs = feedbackRemaining,
            startErrorCode = errorCode?.take(32),
            startErrorMessage = errorMessage?.take(160),
            configVersion = configVersion.coerceAtLeast(0L),
            submissions = subs,
        )
    }

    fun encodeConfig(config: QuizConfig): JSONObject = encodeConfig(config, includeScoring = true)

    /**
     * The cloud wire contract still knows only the single-rate v4 shape. Local
     * difficulty scoring must not leak into uploads until the backend owns the
     * same fields, so the binding report uses this stripped snapshot.
     */
    fun encodeLegacyConfig(config: QuizConfig): JSONObject =
        encodeConfig(config, includeScoring = false)

    private fun encodeConfig(config: QuizConfig, includeScoring: Boolean): JSONObject = JSONObject()
        .put("defaultTimeLimitSec", config.defaultTimeLimitSec)
        .put("minutesPerPoint", config.minutesPerPoint)
        .put("minCorrectCount", config.minCorrectCount)
        .put("maxEarnedMinutes", config.maxEarnedMinutes)
        .put("shuffleOptions", config.shuffleOptions)
        .put("answerDisplaySec", config.answerDisplaySec)
        .put("pullCount", config.pullCount)
        .put("minutesPerQuestion", config.minutesPerQuestion)
        .put("pointsPerQuestion", config.pointsPerQuestion)
        .apply {
            if (!includeScoring) return@apply
            put("easyRewardMinutes", config.easyRewardMinutes)
            put("mediumRewardMinutes", config.mediumRewardMinutes)
            put("hardRewardMinutes", config.hardRewardMinutes)
            put("easyPenaltyMinutes", config.easyPenaltyMinutes)
            put("mediumPenaltyMinutes", config.mediumPenaltyMinutes)
            put("hardPenaltyMinutes", config.hardPenaltyMinutes)
            put("minUnlockMinutes", config.minUnlockMinutes)
        }

    /** Decodes stored challenge defaults; returns null when a field is out of range. */
    fun decodeConfig(root: JSONObject): QuizConfig? {
        val errors = mutableListOf<String>()
        val config = parseConfig(root, errors)
        return if (errors.isEmpty()) config else null
    }

    /** Decodes one cached item; null means the stored row is corrupt. */
    fun decodeItem(raw: String): QuizItem? {
        if (raw.isBlank()) return null
        val array = runCatching { JSONArray().put(org.json.JSONObject(raw)) }.getOrNull() ?: return null
        val errors = mutableListOf<String>()
        return parseItems(array, errors)?.firstOrNull()?.takeIf { errors.isEmpty() }
    }

    fun encodeItem(item: QuizItem): JSONObject = JSONObject()
        .put("questionId", item.questionId)
        .put("prompt", item.prompt)
        .put("options", JSONArray(item.options))
        .put("answerIndex", item.answerIndex)
        .put("explanation", item.explanation)
        .put("subject", item.subject)
        .put("grade", item.grade)
        .put("difficulty", item.difficulty)
        .apply {
            // 只有新题库条目带范围元数据；旧条目保持旧 wire 形状。
            if (item.scopeGrade != null) put("scopeGrade", item.scopeGrade)
            if (item.scopeSemester != null) put("scopeSemester", item.scopeSemester)
            if (item.scopeSubject.isNotEmpty()) put("scopeSubject", item.scopeSubject)
            if (item.scopeTextbook.isNotEmpty()) put("scopeTextbook", item.scopeTextbook)
            if (item.scopeType.isNotEmpty()) put("scopeType", item.scopeType)
            if (item.scopeUnitNo != null) put("scopeUnitNo", item.scopeUnitNo)
        }
        .put("points", item.points)
        .put("timeLimitSec", item.timeLimitSec)
        .put("recommendedTimeSec", item.recommendedTimeSec)
        .put("cacheId", item.cacheId)
        .put("sourceKind", item.sourceKind)
        .put("sourceRef", item.sourceRef)
        .apply {
            // Omit the identity order so cached/unshuffled items keep the
            // pre-existing wire shape.
            if (item.optionOrder.isNotEmpty()) {
                put("optionOrder", JSONArray(item.optionOrder))
            }
        }

    private fun encodeRun(run: QuizRun): JSONObject = JSONObject()
        .put("startedAt", run.startedAt)
        .put("startedDate", run.startedDate)
        .put("index", run.index)
        .put("correctCount", run.correctCount)
        .put("earnedPoints", run.earnedPoints)
        .put("earnedMinutes", run.earnedMinutes)
        .put("deadlineAt", run.deadlineAt)
        .put("savedRemainingMs", run.savedRemainingMs)
        .put("savedAt", run.savedAt)
        .put("feedbackOpen", run.feedbackOpen)
        .put("feedbackRemainingMs", run.feedbackRemainingMs)
        .put("startErrorCode", run.startErrorCode ?: JSONObject.NULL)
        .put("startErrorMessage", run.startErrorMessage ?: JSONObject.NULL)
        .put("configVersion", run.configVersion)
        .put("submissions", JSONArray().apply {
            run.submissions.forEach { sub ->
                put(
                    JSONObject()
                        .put("questionId", sub.questionId)
                        .put("selectedIndex", sub.selectedIndex ?: JSONObject.NULL)
                        .put("correct", sub.correct)
                        .put("timedOut", sub.timedOut)
                        .put("answeredAt", sub.answeredAt),
                )
            }
        })

    private fun JSONObject.requiredString(name: String, prefix: String, errors: MutableList<String>): String? {
        if (!has(name) || isNull(name)) {
            errors += "$prefix.$name: missing"
            return null
        }
        val value = opt(name)
        if (value !is String) {
            errors += "$prefix.$name: not a string"
            return null
        }
        return value
    }

    private fun JSONObject.requiredInt(name: String, prefix: String, errors: MutableList<String>): Int? {
        if (!has(name) || isNull(name)) {
            errors += "$prefix.$name: missing"
            return null
        }
        val value = opt(name)
        if (value !is Number || value.toDouble() != value.toLong().toDouble()) {
            errors += "$prefix.$name: not an integer"
            return null
        }
        return value.toInt()
    }

    private fun optionalInt(
        root: JSONObject,
        name: String,
        prefix: String,
        errors: MutableList<String>,
    ): Int? {
        if (!root.has(name) || root.isNull(name)) return null
        val value = root.opt(name)
        if (value !is Number || value.toDouble() != value.toLong().toDouble()) {
            errors += "$prefix.$name: not an integer"
            return null
        }
        return value.toInt()
    }

    private fun JSONObject.requiredLong(name: String, prefix: String, errors: MutableList<String>): Long? {
        if (!has(name) || isNull(name)) {
            errors += "$prefix.$name: missing"
            return null
        }
        val value = opt(name)
        if (value !is Number || value.toDouble() != value.toLong().toDouble()) {
            errors += "$prefix.$name: not an integer"
            return null
        }
        return value.toLong()
    }

    private fun JSONObject.requiredBoolean(name: String, prefix: String, errors: MutableList<String>): Boolean? {
        if (!has(name) || isNull(name)) {
            errors += "$prefix.$name: missing"
            return null
        }
        val value = opt(name)
        if (value !is Boolean) {
            errors += "$prefix.$name: not a boolean"
            return null
        }
        return value
    }

    private fun JSONObject.requiredObject(name: String, prefix: String, errors: MutableList<String>): JSONObject? {
        if (!has(name) || isNull(name)) {
            errors += "$prefix.$name: missing"
            return null
        }
        val value = opt(name)
        if (value !is JSONObject) {
            errors += "$prefix.$name: not an object"
            return null
        }
        return value
    }

    private fun JSONObject.requiredArray(name: String, prefix: String, errors: MutableList<String>): JSONArray? {
        if (!has(name) || isNull(name)) {
            errors += "$prefix.$name: missing"
            return null
        }
        val value = opt(name)
        if (value !is JSONArray) {
            errors += "$prefix.$name: not an array"
            return null
        }
        return value
    }

    private fun JSONObject.optionalArray(name: String, errors: MutableList<String>): JSONArray? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        if (value !is JSONArray) {
            errors += "$name: not an array"
            return null
        }
        return value
    }

    private val TIME_LIMIT_RANGE = 15..300
    private val DIFFICULTIES = setOf("easy", "medium", "hard")
    private val RUNTIME_STATUSES = setOf(
        QuizSessionStatus.RUNNING,
        QuizSessionStatus.SETTLING,
        QuizSessionStatus.STARTING,
        QuizSessionStatus.START_FAILED,
    )
    private val SESSION_ID_RE = Regex("sess_[A-Za-z0-9_-]{8,64}")
    private val QUESTION_ID_RE = Regex("[A-Za-z][A-Za-z0-9_-]{7,63}")
    private val SOURCE_RE = Regex("[a-z][a-z0-9_-]{0,23}")
    private val LANGUAGE_RE = Regex("[a-z]{2,3}(-[A-Za-z]{2,8})?")
    private val DATE_RE = Regex("\\d{4}-\\d{2}-\\d{2}")
}
