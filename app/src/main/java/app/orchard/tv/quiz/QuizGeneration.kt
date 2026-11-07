package app.orchard.tv.quiz

import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.math.min

/**
 * One in-memory AI generation job. Produced questions stay in a temporary
 * batch until the parent selects them on the phone; nothing reaches the bank
 * without an explicit [add] call, so cancelling or leaving the page never
 * writes a half-reviewed batch.
 */
object QuizGeneration {
    const val JOB_TTL_MS = 10 * 60 * 1000L
    const val MAX_PROMPT_CHARS = 400
    // "Grade 4 · Semester 1" and its five-language variants all fit here.
    const val MAX_GRADE_CHARS = 32
    // 自定义主题只用于提示词和清单展示，限制长度避免把提示词挤出上限。
    const val MAX_CUSTOM_TOPIC_CHARS = 30
    const val BATCH_SIZE = 5
    const val DEFAULT_TARGET_COUNT = 20
    // 版本 3：取消默认“期中必考”，题目要求改为自由输入；旧版 0/2 仍只读兼容。
    const val PRESET_VERSION = 3
    val TARGET_COUNTS = setOf(20, 30, 50)
    private const val LEGACY_BATCH_COUNT = 10
    private const val DEFAULT_OPTIONS = 4
    private const val DEFAULT_TIME_LIMIT_SEC = 60
    private const val DEFAULT_POINTS = 1

    data class Request(
        val targetCount: Int,
        val options: Int,
        val language: String,
        val subject: String,
        val grade: String,
        val prompt: String,
        val difficulty: String,
        /** Canonical range fields used for bank metadata and answer-scope filtering. */
        val subjectId: String = "",
        val gradeNo: Int = 0,
        val semester: Int = 0,
        val textbook: String = "",
        val scopeType: String = "",
        val unitNo: Int? = null,
    ) {
        fun scope(): QuizAnswerScope? {
            if (subjectId.isBlank() || gradeNo == 0 || semester == 0) return null
            return QuizAnswerScope.create(
                grade = gradeNo,
                semester = semester,
                subject = subjectId,
                textbook = textbook,
                scopeType = scopeType,
                unitNo = if (scopeType.equals("UNIT", ignoreCase = true)) unitNo else null,
                difficulty = difficulty.ifBlank { null },
            )
        }
    }

    data class StagedItem(
        val item: QuizItem,
        val duplicate: Boolean,
    )

    data class Progress(
        val target: Int,
        /** Every valid question kept in the temporary batch, duplicates included. */
        val generated: Int,
        /** Questions that are not already in the bank (and not repeated in this batch). */
        val unique: Int,
        val duplicates: Int,
        val rejected: Int,
        val batchIndex: Int,
        val warnings: List<String>,
        val counts: QuizQuestionCache.BankCounts,
        val expiresAt: Long,
    ) {
        val remaining: Int get() = (target - unique).coerceAtLeast(0)
    }

    sealed interface ParseResult {
        data class Ok(val request: Request) : ParseResult
        data class Invalid(val fields: List<String>) : ParseResult
    }

    sealed interface StartResult {
        data class Started(val jobId: String, val expiresAt: Long) : StartResult
        data class Failure(val code: String) : StartResult
    }

    sealed interface PollResult {
        data class Running(val progress: Progress) : PollResult
        data class Ready(val progress: Progress, val batch: List<StagedItem>) : PollResult
        data class Failed(
            val errorCode: String,
            val progress: Progress,
            val batch: List<StagedItem>,
        ) : PollResult
        data object Lost : PollResult
        data object Expired : PollResult
    }

    sealed interface CommitResult {
        data class Ok(
            val appended: Int,
            val duplicates: Int,
            val rejected: Int,
            val capacityReached: Boolean,
            val counts: QuizQuestionCache.BankCounts,
        ) : CommitResult
        data object Lost : CommitResult
        data object NotReady : CommitResult
        data class BadIndexes(val indexes: List<Int>) : CommitResult
    }

    private enum class State { RUNNING, READY, ERROR }

    private data class GenerationJob(
        val id: String,
        val owner: String,
        val state: State,
        val progress: Progress,
        val batch: List<StagedItem> = emptyList(),
        val errorCode: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: GenerationJob? = null
    private var network: Job? = null

    @Synchronized
    fun start(ownerToken: String, request: Request, nowMs: Long = System.currentTimeMillis()): StartResult {
        if (!QuizSessionStore.consentAccepted()) return StartResult.Failure("CONSENT_REQUIRED")
        val key = QuizSessionStore.providerKey() ?: return StartResult.Failure("PROVIDER_NOT_CONFIGURED")
        if (!QuizQuestionCache.isReady()) return StartResult.Failure("CACHE_UNAVAILABLE")
        val meta = QuizSessionStore.loadProviderMeta()
        val owner = QuizAccess.fingerprint(ownerToken)
        val current = job
        if (current != null && current.state == State.RUNNING &&
            current.owner == owner && nowMs < current.progress.expiresAt
        ) {
            return StartResult.Failure("BUSY")
        }
        val counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, nowMs)
        if (QuizQuestionCache.BANK_CAPACITY - counts.total < request.targetCount) {
            return StartResult.Failure("BANK_FULL")
        }

        val id = "job_" + UUID.randomUUID().toString().replace("-", "").take(20)
        val expiresAt = nowMs + JOB_TTL_MS
        val progress = Progress(
            target = request.targetCount,
            generated = 0,
            unique = 0,
            duplicates = 0,
            rejected = 0,
            batchIndex = 0,
            warnings = emptyList(),
            counts = counts,
            expiresAt = expiresAt,
        )
        val next = GenerationJob(id, owner, State.RUNNING, progress)
        network?.cancel()
        job = next
        network = scope.launch {
            runJob(id, meta, key, request, expiresAt)
        }
        return StartResult.Started(id, expiresAt)
    }

    @Synchronized
    fun poll(
        ownerToken: String,
        jobId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): PollResult {
        val current = job ?: return PollResult.Lost
        if (current.owner != QuizAccess.fingerprint(ownerToken) || current.id != jobId) {
            return PollResult.Lost
        }
        if (nowMs >= current.progress.expiresAt) {
            job = null
            network?.cancel()
            return PollResult.Expired
        }
        val progress = current.progress.copy(
            counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, nowMs),
        )
        return when (current.state) {
            State.RUNNING -> PollResult.Running(progress)
            State.READY -> PollResult.Ready(progress, current.batch)
            State.ERROR -> PollResult.Failed(current.errorCode ?: "AI_FAILED", progress, current.batch)
        }
    }

    /**
     * Stops the network work but keeps everything already produced. The page
     * then polls once more and receives a READY partial batch to review.
     */
    @Synchronized
    fun cancel(ownerToken: String, jobId: String): Boolean {
        val current = job ?: return false
        if (current.owner != QuizAccess.fingerprint(ownerToken) || current.id != jobId) return false
        if (current.state == State.RUNNING) {
            job = current.copy(state = State.READY, errorCode = null)
        }
        network?.cancel()
        network = null
        return true
    }

    /** Re-checks the chosen rows, appends them, and discards the batch on success. */
    @Synchronized
    fun add(
        ownerToken: String,
        jobId: String,
        indexes: List<Int>,
        nowMs: Long = System.currentTimeMillis(),
    ): CommitResult {
        val current = job ?: return CommitResult.Lost
        if (current.owner != QuizAccess.fingerprint(ownerToken) || current.id != jobId) {
            return CommitResult.Lost
        }
        if (current.state == State.RUNNING) return CommitResult.NotReady
        if (current.batch.isEmpty()) return CommitResult.BadIndexes(indexes.take(8))
        val uniqueIndexes = indexes.distinct()
        val invalid = uniqueIndexes.filter { it !in current.batch.indices }
        if (invalid.isNotEmpty() || uniqueIndexes.isEmpty()) {
            return CommitResult.BadIndexes(invalid.take(8).ifEmpty { listOf(-1) })
        }
        val selected = uniqueIndexes
            .filterNot { current.batch[it].duplicate }
            .map { current.batch[it].item }
        if (selected.isEmpty()) {
            return CommitResult.Ok(
                appended = 0,
                duplicates = uniqueIndexes.size,
                rejected = 0,
                capacityReached = false,
                counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, nowMs),
            )
        }
        val append = QuizQuestionCache.appendLocalQuestions(current.id, selected, nowMs)
        val counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, nowMs)
        job = null
        network?.cancel()
        network = null
        return CommitResult.Ok(
            appended = append.appended,
            duplicates = append.duplicates,
            rejected = append.rejected,
            capacityReached = append.capacityReached,
            counts = counts,
        )
    }

    /** Throws away the temporary batch without writing anything to the bank. */
    @Synchronized
    fun discard(ownerToken: String, jobId: String): Boolean {
        val current = job ?: return false
        if (current.owner != QuizAccess.fingerprint(ownerToken) || current.id != jobId) return false
        job = null
        network?.cancel()
        network = null
        return true
    }

    private suspend fun runJob(
        id: String,
        meta: QuizProviderMeta,
        key: String,
        request: Request,
        expiresAt: Long,
    ) {
        var generated = 0
        var unique = 0
        var duplicates = 0
        var rejected = 0
        var batchIndex = 0
        var warnings = emptyList<String>()
        var errorCode: String? = null
        val staged = mutableListOf<QuizItem>()
        val maxAttempts = ((request.targetCount + BATCH_SIZE - 1) / BATCH_SIZE) * 3 + 2

        while (unique < request.targetCount) {
            if (!isRunning(id)) return
            if (System.currentTimeMillis() >= expiresAt) {
                errorCode = "JOB_EXPIRED"
                break
            }
            if (batchIndex >= maxAttempts) {
                warnings = (warnings + "BANK_DUPLICATE_LIMIT").takeLast(8)
                break
            }
            val count = min(BATCH_SIZE, request.targetCount - unique).coerceAtLeast(1)
            val result = runCatching {
                QuizAiClient.generate(meta, key, request.toAiRequest(count))
            }.getOrElse { QuizAiClient.Result.Failure("PROVIDER_NETWORK") }
            if (!isRunning(id)) return
            when (result) {
                is QuizAiClient.Result.Failure -> {
                    errorCode = result.code
                    break
                }
                is QuizAiClient.Result.Success -> {
                    staged += result.items.map { request.stamp(it) }
                    val flags = QuizQuestionCache.duplicateFlags(staged)
                    val batch = staged.mapIndexed { index, item ->
                        StagedItem(item, flags[index])
                    }
                    generated = batch.size
                    unique = flags.count { !it }
                    duplicates = flags.count { it }
                    warnings = (warnings + result.warnings).takeLast(8)
                    val progress = Progress(
                        target = request.targetCount,
                        generated = generated,
                        unique = unique,
                        duplicates = duplicates,
                        rejected = rejected,
                        batchIndex = batchIndex + 1,
                        warnings = warnings,
                        counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE),
                        expiresAt = expiresAt,
                    )
                    val finished = unique >= request.targetCount
                    synchronized(this@QuizGeneration) {
                        val active = job
                        if (active?.id != id || active.state != State.RUNNING) return
                        job = active.copy(
                            state = if (finished) State.READY else State.RUNNING,
                            progress = progress,
                            batch = batch,
                        )
                    }
                    if (finished) return
                }
            }
            batchIndex++
        }

        synchronized(this@QuizGeneration) {
            val active = job
            if (active?.id != id || active.state != State.RUNNING) return
            val progress = active.progress.copy(
                counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE),
            )
            job = active.copy(
                state = if (errorCode == null) State.READY else State.ERROR,
                progress = progress,
                errorCode = errorCode,
            )
        }
    }

    @Synchronized
    private fun isRunning(id: String): Boolean =
        job?.id == id && job?.state == State.RUNNING

    private fun Request.toAiRequest(count: Int): QuizAiClient.Request = QuizAiClient.Request(
        count = count,
        options = options,
        language = language,
        subject = subject,
        grade = grade,
        prompt = prompt,
        difficulty = difficulty,
        points = DEFAULT_POINTS,
        timeLimitSec = DEFAULT_TIME_LIMIT_SEC,
    )

    private fun Request.stamp(item: QuizItem): QuizItem {
        val answerScope = scope() ?: return item
        return item.copy(
            // 入题时以出题配置为准，避免模型自报难度导致答题范围统计漂移。
            difficulty = difficulty.ifBlank { item.difficulty },
            scopeGrade = answerScope.grade,
            scopeSemester = answerScope.semester,
            scopeSubject = answerScope.subject,
            scopeTextbook = answerScope.textbook,
            scopeType = answerScope.scopeType,
            scopeUnitNo = answerScope.unitNo,
        )
    }

    fun parseRequest(root: JSONObject): ParseResult {
        val errors = mutableListOf<String>()
        val targetCount = when {
            root.has("targetCount") -> {
                val value = root.optInt("targetCount", 0)
                if (value !in TARGET_COUNTS) errors += "targetCount: out of range"
                value
            }
            root.has("count") -> {
                // A cached pre-V17 page sends a single batch of at most 10.
                val value = root.optInt("count", 0)
                if (value !in 1..LEGACY_BATCH_COUNT) errors += "count: out of range"
                value
            }
            else -> DEFAULT_TARGET_COUNT
        }
        val options = root.optInt("options", DEFAULT_OPTIONS)
        val language = root.optString("language", "zh").trim()
        val subject = root.optString("subject", "").trim()
        val grade = root.optString("grade", "").trim()
        val prompt = root.optString("prompt", "").trim()
        val difficulty = root.optString("difficulty", "").trim().lowercase()
        val subjectId = root.optString("subjectId", "").trim().lowercase()
        val gradeNo = root.optInt("gradeNo", 0)
        val semester = root.optInt("semester", 0)
        val textbook = root.optString("textbook", "").trim().lowercase()
        val scopeType = root.optString(
            "scopeType",
            root.optString("scope", ""),
        ).trim().lowercase()
        val unitNo = when {
            root.has("unitNo") && !root.isNull("unitNo") -> root.optInt("unitNo", 0)
            root.has("unit") -> root.optInt("unit", 0)
            else -> null
        }
        if (options !in 2..6) errors += "options: out of range"
        if (!Regex("[a-z]{2,3}(-[A-Za-z]{2,8})?").matches(language)) errors += "language: invalid"
        if (subject.length > 32) errors += "subject: too long"
        if (grade.length > MAX_GRADE_CHARS) errors += "grade: too long"
        if (prompt.length > MAX_PROMPT_CHARS) errors += "prompt: too long"
        if (difficulty.isNotEmpty() && difficulty !in setOf("easy", "medium", "hard")) {
            errors += "difficulty: invalid"
        }
        val hasScopeMetadata = subjectId.isNotEmpty() || gradeNo != 0 || semester != 0 ||
            textbook.isNotEmpty() || scopeType.isNotEmpty() || unitNo != null
        if (hasScopeMetadata) {
            if (subjectId !in SCOPE_SUBJECTS) errors += "subjectId: invalid"
            if (gradeNo !in 1..6) errors += "gradeNo: out of range"
            if (semester !in 1..2) errors += "semester: out of range"
            if (textbook !in TEXTBOOKS) errors += "textbook: invalid"
            if (scopeType !in SCOPE_TYPES) errors += "scopeType: invalid"
            if (scopeType == "unit") {
                if (unitNo == null || unitNo !in 1..8) errors += "unitNo: out of range"
            } else if (unitNo != null) {
                errors += "unitNo: must be empty outside unit scope"
            }
        }
        return if (errors.isEmpty()) {
            ParseResult.Ok(
                Request(
                    targetCount = targetCount,
                    options = options,
                    language = language,
                    subject = subject,
                    grade = grade,
                    prompt = prompt,
                    difficulty = difficulty,
                    subjectId = subjectId,
                    gradeNo = gradeNo,
                    semester = semester,
                    textbook = textbook,
                    scopeType = scopeType,
                    unitNo = if (scopeType == "unit") unitNo else null,
                ),
            )
        } else {
            ParseResult.Invalid(errors)
        }
    }

    /**
     * The web editor's remembered form. Unknown target counts are rejected so a
     * stale page cannot start a batch size the current UI does not offer.
     */
    fun parsePreferences(root: JSONObject): JSONObject? {
        val grade = root.optString("grade", "")
        val subject = root.optString("subject", "")
        val textbook = root.optString("textbook", "")
        val scope = root.optString("scope", "")
        val unit = root.optInt("unit", 0)
        val options = root.optInt("options", 0)
        val difficulty = root.optString("difficulty", "").trim().lowercase()
        val rawCustomTopic = if (root.has("customTopic") && !root.isNull("customTopic")) {
            root.opt("customTopic")
        } else {
            null
        }
        if (rawCustomTopic != null && rawCustomTopic !is String) return null
        val customTopic = (rawCustomTopic as? String ?: "")
            .trim()
            .replace(Regex("\\s+"), " ")
        if (customTopic.any { it.isISOControl() } ||
            customTopic.length > MAX_CUSTOM_TOPIC_CHARS
        ) {
            return null
        }
        val rawAutoAdd = if (root.has("autoAdd") && !root.isNull("autoAdd")) {
            root.opt("autoAdd")
        } else {
            null
        }
        if (rawAutoAdd != null && rawAutoAdd !is Boolean) return null
        val autoAdd = rawAutoAdd as? Boolean ?: true
        val rawPresetVersion = if (root.has("presetVersion") && !root.isNull("presetVersion")) {
            root.opt("presetVersion")
        } else {
            null
        }
        if (rawPresetVersion != null && rawPresetVersion !is Number) return null
        val presetVersion = (rawPresetVersion as? Number)?.toInt() ?: 0
        if (presetVersion != 0 && presetVersion != 2 && presetVersion != PRESET_VERSION) return null
        val targetCount = when {
            root.has("targetCount") -> root.optInt("targetCount", 0)
            root.has("count") -> root.optInt("count", 0)
            else -> DEFAULT_TARGET_COUNT
        }
        val valid = grade.matches(Regex("[1-6][ab]")) &&
            subject in setOf("chinese", "math", "english", "science", "all") &&
            textbook in setOf(
                "bubian", "renjiao", "sujiao", "beishida", "qingdao",
                "pep", "waiyan", "yilin", "kejiao", "general",
            ) &&
            scope in setOf("unit", "midterm", "final", "review") &&
            unit in 1..8 &&
            options in setOf(4, 6) &&
            difficulty in setOf("", "easy", "medium", "hard") &&
            (targetCount in TARGET_COUNTS || (root.has("count") && targetCount in 1..LEGACY_BATCH_COUNT))
        if (!valid) return null
        val effectiveTarget = if (targetCount in TARGET_COUNTS) targetCount else DEFAULT_TARGET_COUNT
        return JSONObject()
            .put("grade", grade)
            .put("subject", subject)
            .put("textbook", textbook)
            .put("scope", scope)
            .put("unit", unit)
            .put("targetCount", effectiveTarget)
            .put("options", options)
            .put("difficulty", difficulty)
            .put("customTopic", customTopic)
            .put("autoAdd", autoAdd)
            .put("presetVersion", presetVersion)
    }

    private val SCOPE_SUBJECTS = setOf("chinese", "math", "english", "science")
    private val TEXTBOOKS = setOf(
        "bubian", "renjiao", "sujiao", "beishida", "qingdao",
        "pep", "waiyan", "yilin", "kejiao", "general",
    )
    private val SCOPE_TYPES = setOf("unit", "midterm", "final", "review")
}
