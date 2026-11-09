package app.orchard.tv.quiz

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionCodec
import java.security.MessageDigest
import java.util.UUID

/**
 * The bounded, source-agnostic question pool. The web (local) and the cloud
 * preparer each own one scope; the engine only reserves from the scope chosen
 * by the policy mode. Local rows are the product's own bank and survive
 * binding/unbinding; cloud rows are disposable attempt data.
 */
object QuizQuestionCache {
    const val LOCAL_SCOPE = "local"

    /**
     * Leaves room for the 4,900 bundled questions plus user-generated batches.
     * The seed import is one transaction; the web lists the bank with paging,
     * so the larger bound does not turn into a larger page.
     */
    const val BANK_CAPACITY = 6_000

    private const val TABLE = "quiz_question_cache"
    private const val DB_NAME = "quiz_question_cache.db"
    private const val DB_VERSION = 3

    private const val STATE_AVAILABLE = "AVAILABLE"
    private const val STATE_RESERVED = "RESERVED"
    private const val STATE_CONSUMED = "CONSUMED"
    private const val STATE_EXPIRED = "EXPIRED"
    private const val SOURCE_LOCAL = "LOCAL"
    private const val SOURCE_CLOUD = "CLOUD"
    private const val RESULT_CORRECT = "CORRECT"
    private const val RESULT_WRONG = "WRONG"
    private const val TAG = "QuizQuestionCache"

    private val lock = Any()
    @Volatile private var helper: Helper? = null

    data class CachedQuestion(
        val cacheId: String,
        val question: QuizItem,
        val sourceKind: String,
        val sourceRef: String?,
        val scopeKey: String,
        val receivedAt: Long,
        val expiresAt: Long?,
        val state: String,
    )

    data class Counts(
        val available: Int = 0,
        val reserved: Int = 0,
        val consumed: Int = 0,
        val expired: Int = 0,
    ) {
        val total: Int get() = available + reserved + consumed + expired
    }

    data class BankRow(
        val cacheId: String,
        val question: QuizItem,
        val sourceKind: String,
        val sourceRef: String?,
        val scopeKey: String,
        val receivedAt: Long,
        val expiresAt: Long?,
        val state: String,
        val lastResult: String?,
        val wrongCount: Int,
        val answeredAt: Long?,
        val timedOut: Boolean,
        val selectedIndex: Int?,
    ) {
        fun status(nowMs: Long = System.currentTimeMillis()): String = when {
            state == STATE_AVAILABLE && expiresAt != null && expiresAt <= nowMs -> "expired"
            state == STATE_AVAILABLE -> "unused"
            state == STATE_RESERVED -> "in_round"
            state == STATE_CONSUMED && lastResult == RESULT_CORRECT -> "correct"
            state == STATE_CONSUMED && lastResult == RESULT_WRONG -> "wrong"
            state == STATE_CONSUMED -> "legacy"
            else -> "expired"
        }
    }

    data class BankCounts(
        val total: Int = 0,
        val unused: Int = 0,
        val inRound: Int = 0,
        val correct: Int = 0,
        val wrong: Int = 0,
        val legacy: Int = 0,
        val expired: Int = 0,
    ) {
        val answered: Int get() = correct + wrong + legacy
    }

    data class AnswerWrite(
        val cacheId: String,
        val questionId: String,
        val selectedIndex: Int?,
        val correct: Boolean,
        val timedOut: Boolean,
        val answeredAt: Long,
    )

    data class AppendResult(
        val appended: Int = 0,
        val duplicates: Int = 0,
        val rejected: Int = 0,
        val capacityReached: Boolean = false,
        val items: List<CachedQuestion> = emptyList(),
    )

    enum class ClearMode {
        UNUSED,
        ANSWERED,
        CORRECT,
        WRONG,
        ALL,
    }

    class CacheException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun init(context: Context): Boolean = initDb(context, DB_NAME)

    @VisibleForTesting
    internal fun initForTest(context: Context, dbName: String): Boolean = initDb(context, dbName)

    private fun initDb(context: Context, dbName: String): Boolean = synchronized(lock) {
        if (helper?.dbName == dbName) return true
        helper?.close()
        helper = null
        return runCatching {
            helper = Helper(context.applicationContext, dbName)
            helper!!.writableDatabase
            true
        }.getOrElse { error ->
            Log.e(TAG, "question cache init failed", error)
            helper = null
            false
        }
    }

    @VisibleForTesting
    internal fun closeForTest() = synchronized(lock) {
        helper?.close()
        helper = null
    }

    fun isReady(): Boolean = helper != null

    fun cloudScope(deviceNo: String): String = "cloud:$deviceNo"

    /**
     * Legacy path retained for old callers. V17 production code appends to the
     * bank through [appendLocalQuestions] instead.
     */
    fun replaceLocalSession(session: QuizSession, nowMs: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) {
            val db = database() ?: return false
            db.beginTransaction()
            try {
                db.delete(
                    TABLE,
                    "scope_key = ? AND state IN (?, ?)",
                    arrayOf(LOCAL_SCOPE, STATE_AVAILABLE, STATE_RESERVED),
                )
                session.items.forEach { item ->
                    val sourceRef = "local:${session.id}:${item.questionId}"
                    db.delete(TABLE, "source_ref = ?", arrayOf(sourceRef))
                    val stored = item.copy(cacheId = "", sourceKind = SOURCE_LOCAL, sourceRef = "", optionOrder = emptyList())
                    val values = ContentValues().apply {
                        put("cache_id", UUID.randomUUID().toString())
                        put("question_json", QuizSessionCodec.encodeItem(stored).toString())
                        put("question_id", stored.questionId)
                        put("source_kind", SOURCE_LOCAL)
                        put("source_ref", sourceRef)
                        put("scope_key", LOCAL_SCOPE)
                        putScopeFields(stored)
                        put("fingerprint", fingerprint(stored))
                        put("state", STATE_AVAILABLE)
                        putNull("reserved_by")
                        put("received_at", nowMs)
                        putNull("expires_at")
                        putNull("reserved_at")
                        putNull("consumed_at")
                        put("last_result", null as String?)
                        put("wrong_count", 0)
                        put("answered_at", null as Long?)
                        put("last_round_id", null as String?)
                        put("last_timed_out", 0)
                        put("last_selected_index", null as Int?)
                    }
                    db.insertOrThrow(TABLE, null, values)
                }
                db.setTransactionSuccessful()
                true
            } catch (error: Exception) {
                Log.e(TAG, "replace local session failed", error)
                false
            } finally {
                db.endTransaction()
            }
        }

    fun replaceCloudSet(
        attemptUid: String,
        items: List<QuizItem>,
        expiresAt: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = replaceCloudSet(attemptUid, items, expiresAt, cloudScope("legacy"), nowMs)

    /**
     * Replaces one cloud scope with one attempt. Local rows are never touched.
     * Callers must have flushed the previous attempt first.
     */
    fun replaceCloudSet(
        attemptUid: String,
        items: List<QuizItem>,
        expiresAt: Long,
        scopeKey: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        if (attemptUid.isBlank() || items.isEmpty() || expiresAt <= nowMs || scopeKey == LOCAL_SCOPE) return false
        val db = database() ?: return false
        db.beginTransaction()
        try {
            db.delete(
                TABLE,
                "scope_key = ? AND state IN (?, ?)",
                arrayOf(scopeKey, STATE_AVAILABLE, STATE_RESERVED),
            )
            items.forEach { item ->
                val sourceRef = "cloud:$attemptUid:${item.questionId}"
                val stored = item.copy(cacheId = "", sourceKind = SOURCE_CLOUD, sourceRef = sourceRef, optionOrder = emptyList())
                val values = ContentValues().apply {
                    put("cache_id", UUID.randomUUID().toString())
                    put("question_json", QuizSessionCodec.encodeItem(stored).toString())
                    put("question_id", stored.questionId)
                    put("source_kind", SOURCE_CLOUD)
                    put("source_ref", sourceRef)
                    put("scope_key", scopeKey)
                    putScopeFields(stored)
                    put("fingerprint", fingerprint(stored))
                    put("state", STATE_AVAILABLE)
                    putNull("reserved_by")
                    put("received_at", nowMs)
                    put("expires_at", expiresAt)
                    putNull("reserved_at")
                    putNull("consumed_at")
                    put("last_result", null as String?)
                    put("wrong_count", 0)
                    put("answered_at", null as Long?)
                    put("last_round_id", null as String?)
                    put("last_timed_out", 0)
                    put("last_selected_index", null as Int?)
                }
                db.insertOrThrow(TABLE, null, values)
            }
            db.setTransactionSuccessful()
            true
        } catch (error: Exception) {
            Log.e(TAG, "replace cloud set failed", error)
            false
        } finally {
            db.endTransaction()
        }
    }

    fun cloudAttemptUid(scopeKey: String = LOCAL_SCOPE): String? = synchronized(lock) {
        val db = database() ?: return null
        runCatching {
            val attempts = mutableSetOf<String>()
            db.rawQuery(
                "SELECT source_ref FROM $TABLE " +
                    "WHERE scope_key = ? AND source_kind = ? AND state IN (?, ?) " +
                    "AND source_ref IS NOT NULL",
                arrayOf(scopeKey, SOURCE_CLOUD, STATE_AVAILABLE, STATE_RESERVED),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val ref = cursor.getString(0) ?: continue
                    if (!ref.startsWith("cloud:")) continue
                    val rest = ref.removePrefix("cloud:")
                    val uid = rest.substringBefore(':', missingDelimiterValue = "")
                    if (uid.isNotEmpty()) attempts += uid
                }
            }
            attempts.singleOrNull()
        }.onFailure { Log.e(TAG, "cloud attempt lookup failed", it) }.getOrNull()
    }

    /**
     * Atomically selects up to [count] available rows. Partial results are
     * returned as-is; an empty list means the pool has no usable question.
     */
    fun reserve(
        count: Int,
        sessionId: String,
        scopeKey: String,
        nowMs: Long = System.currentTimeMillis(),
    ): List<CachedQuestion> = reserveForRound(count, sessionId, scopeKey, 1, nowMs)

    /**
     * Reserves a round only when at least [minRequired] questions are available.
     * A short pool returns no rows and leaves every row available, so a failed
     * start never strands reservations.
     */
    fun reserveForRound(
        count: Int,
        sessionId: String,
        scopeKey: String,
        minRequired: Int,
        nowMs: Long = System.currentTimeMillis(),
        answerScope: QuizAnswerScope? = null,
    ): List<CachedQuestion> = synchronized(lock) {
        if (count <= 0 || minRequired <= 0) return emptyList()
        val db = database() ?: throw CacheException("cache not initialized")
        db.beginTransaction()
        try {
            expire(db, scopeKey, nowMs)
            val (scopeWhere, scopeArgs) = answerScopeWhere(answerScope)
            val baseArgs = arrayOf(scopeKey, STATE_AVAILABLE, nowMs.toString()) + scopeArgs
            val total = db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE WHERE scope_key = ? AND state = ? " +
                    "AND (expires_at IS NULL OR expires_at > ?)$scopeWhere",
                baseArgs,
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (total < minRequired) {
                db.setTransactionSuccessful()
                return emptyList()
            }
            val candidates = mutableListOf<CachedQuestion>()
            db.rawQuery(
                "SELECT cache_id, question_json, source_kind, source_ref, scope_key, " +
                    "received_at, expires_at, state, last_result, wrong_count, answered_at, " +
                    "last_timed_out, last_selected_index FROM $TABLE " +
                    "WHERE scope_key = ? AND state = ? " +
                    "AND (expires_at IS NULL OR expires_at > ?)$scopeWhere " +
                    "ORDER BY received_at ASC, rowid ASC LIMIT ?",
                baseArgs + count.toString(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    candidates += readQuestion(cursor)
                }
            }
            val picked = candidates
            picked.forEach { item ->
                db.update(
                    TABLE,
                    ContentValues().apply {
                        put("state", STATE_RESERVED)
                        put("reserved_by", sessionId)
                        put("reserved_at", nowMs)
                    },
                    "cache_id = ? AND state = ?",
                    arrayOf(item.cacheId, STATE_AVAILABLE),
                )
            }
            db.setTransactionSuccessful()
            picked.map { it.copy(state = STATE_RESERVED) }
        } catch (e: CacheException) {
            throw e
        } catch (e: Exception) {
            throw CacheException("reserve failed", e)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Appends generated questions to the local bank. Duplicates are matched by
     * content fingerprint across all states, so retrying a generation batch is
     * idempotent. The bank never deletes an existing row to make room.
     */
    fun appendLocalQuestions(
        batchId: String,
        items: List<QuizItem>,
        nowMs: Long = System.currentTimeMillis(),
    ): AppendResult = synchronized(lock) {
        if (batchId.isBlank() || items.isEmpty()) return AppendResult(rejected = items.size)
        val db = database() ?: return AppendResult(rejected = items.size)
        db.beginTransaction()
        try {
            val result = appendLocalQuestionsLocked(db, batchId, items, nowMs)
            db.setTransactionSuccessful()
            result
        } catch (error: Exception) {
            Log.e(TAG, "append local questions failed", error)
            AppendResult(rejected = items.size)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Replaces the bundled seed rows in one transaction. Only rows whose source
     * ref starts with `local:preset-v` are removed, so questions generated by
     * the user keep both their content and their answer history. Rows reserved
     * by a running round are kept: deleting them would strand that round's
     * answer writes during an APK upgrade.
     */
    fun replaceSeedQuestions(
        batchId: String,
        items: List<QuizItem>,
        nowMs: Long = System.currentTimeMillis(),
    ): AppendResult = synchronized(lock) {
        if (batchId.isBlank() || items.isEmpty()) return AppendResult(rejected = items.size)
        val db = database() ?: return AppendResult(rejected = items.size)
        db.beginTransaction()
        try {
            db.delete(
                TABLE,
                "scope_key = ? AND source_kind = ? AND source_ref LIKE ? AND state != ?",
                arrayOf(LOCAL_SCOPE, SOURCE_LOCAL, "local:preset-v%", STATE_RESERVED),
            )
            val result = appendLocalQuestionsLocked(db, batchId, items, nowMs)
            db.setTransactionSuccessful()
            result
        } catch (error: Exception) {
            Log.e(TAG, "replace seed questions failed", error)
            AppendResult(rejected = items.size)
        } finally {
            db.endTransaction()
        }
    }

    private fun appendLocalQuestionsLocked(
        db: SQLiteDatabase,
        batchId: String,
        items: List<QuizItem>,
        nowMs: Long,
    ): AppendResult {
        var remaining = (BANK_CAPACITY - rowCount(db, LOCAL_SCOPE)).coerceAtLeast(0)
        val seen = mutableSetOf<String>()
        val appended = mutableListOf<CachedQuestion>()
        var duplicates = 0
        var rejected = 0
        var capacityReached = false
        for (candidate in items) {
            val item = candidate.copy(
                cacheId = "",
                sourceKind = SOURCE_LOCAL,
                sourceRef = "",
                optionOrder = emptyList(),
            )
            if (!isValid(item)) {
                rejected++
                continue
            }
            val fp = fingerprint(item)
            if (!seen.add(fp)) {
                duplicates++
                continue
            }
            if (fingerprintExists(db, LOCAL_SCOPE, fp)) {
                duplicates++
                continue
            }
            if (remaining <= 0) {
                rejected++
                capacityReached = true
                continue
            }
            val sourceRef = "local:$batchId:${item.questionId}"
            if (sourceRefExists(db, SOURCE_LOCAL, sourceRef)) {
                duplicates++
                continue
            }
            val cacheId = UUID.randomUUID().toString()
            val values = ContentValues().apply {
                put("cache_id", cacheId)
                put("question_json", QuizSessionCodec.encodeItem(item).toString())
                put("question_id", item.questionId)
                put("source_kind", SOURCE_LOCAL)
                put("source_ref", sourceRef)
                put("scope_key", LOCAL_SCOPE)
                putScopeFields(item)
                put("fingerprint", fp)
                put("state", STATE_AVAILABLE)
                putNull("reserved_by")
                put("received_at", nowMs)
                putNull("expires_at")
                putNull("reserved_at")
                putNull("consumed_at")
                put("last_result", null as String?)
                put("wrong_count", 0)
                put("answered_at", null as Long?)
                put("last_round_id", null as String?)
                put("last_timed_out", 0)
                put("last_selected_index", null as Int?)
            }
            db.insertOrThrow(TABLE, null, values)
            remaining--
            appended += CachedQuestion(
                cacheId = cacheId,
                question = item,
                sourceKind = SOURCE_LOCAL,
                sourceRef = sourceRef,
                scopeKey = LOCAL_SCOPE,
                receivedAt = nowMs,
                expiresAt = null,
                state = STATE_AVAILABLE,
            )
        }
        return AppendResult(appended.size, duplicates, rejected, capacityReached, appended)
    }

    /**
     * 生成结果暂存时用来标记“已在题库”的题。返回 true 表示该条不可再加入：
     * 非法内容、与题库重复或与本批前面的题重复。
     */
    fun duplicateFlags(items: List<QuizItem>): List<Boolean> = synchronized(lock) {
        val db = database()
        val seen = mutableSetOf<String>()
        items.map { candidate ->
            val item = candidate.copy(
                cacheId = "",
                sourceKind = SOURCE_LOCAL,
                sourceRef = "",
                optionOrder = emptyList(),
            )
            if (!isValid(item)) return@map true
            val fp = fingerprint(item)
            val fresh = seen.add(fp)
            !fresh || (db != null && fingerprintExists(db, LOCAL_SCOPE, fp))
        }
    }

    /** Every local bank row, used by the faceted custom-range picker (capacity is bounded). */
    fun listAllBank(
        scopeKey: String,
        nowMs: Long = System.currentTimeMillis(),
    ): List<BankRow> = synchronized(lock) {
        val db = database() ?: return emptyList()
        runCatching {
            expire(db, scopeKey, nowMs)
            val rows = mutableListOf<BankRow>()
            db.rawQuery(
                "SELECT cache_id, question_json, source_kind, source_ref, scope_key, " +
                    "received_at, expires_at, state, last_result, wrong_count, answered_at, " +
                    "last_timed_out, last_selected_index FROM $TABLE WHERE scope_key = ? " +
                    "ORDER BY received_at DESC, rowid DESC",
                arrayOf(scopeKey),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows += readBankRow(cursor)
                }
            }
            rows
        }.onFailure { Log.e(TAG, "list all bank failed", it) }.getOrDefault(emptyList())
    }

    fun listBank(
        scopeKey: String,
        filter: String = "all",
        offset: Int = 0,
        limit: Int = 50,
        nowMs: Long = System.currentTimeMillis(),
    ): List<BankRow> = synchronized(lock) {
        val db = database() ?: return emptyList()
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceIn(1, 100)
        return runCatching {
            expire(db, scopeKey, nowMs)
            val (where, args) = when (filter) {
                "unused" -> "scope_key = ? AND state = ?" to arrayOf(scopeKey, STATE_AVAILABLE)
                "answered" -> "scope_key = ? AND state = ?" to arrayOf(scopeKey, STATE_CONSUMED)
                "wrong" -> "scope_key = ? AND state = ? AND last_result = ?" to
                    arrayOf(scopeKey, STATE_CONSUMED, RESULT_WRONG)
                "correct" -> "scope_key = ? AND state = ? AND last_result = ?" to
                    arrayOf(scopeKey, STATE_CONSUMED, RESULT_CORRECT)
                else -> "scope_key = ?" to arrayOf(scopeKey)
            }
            val order = if (filter == "wrong" || filter == "answered") {
                "answered_at DESC, received_at DESC, rowid DESC"
            } else {
                "received_at DESC, rowid DESC"
            }
            val rows = mutableListOf<BankRow>()
            db.rawQuery(
                "SELECT cache_id, question_json, source_kind, source_ref, scope_key, " +
                    "received_at, expires_at, state, last_result, wrong_count, answered_at, " +
                    "last_timed_out, last_selected_index FROM $TABLE WHERE $where " +
                    "ORDER BY $order LIMIT ? OFFSET ?",
                args + arrayOf(safeLimit.toString(), safeOffset.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows += readBankRow(cursor)
                }
            }
            rows
        }.onFailure { Log.e(TAG, "list bank failed", it) }.getOrDefault(emptyList())
    }

    fun bankCounts(
        scopeKey: String,
        nowMs: Long = System.currentTimeMillis(),
    ): BankCounts = synchronized(lock) {
        val db = database() ?: return BankCounts()
        runCatching {
            expire(db, scopeKey, nowMs)
            var total = 0
            var unused = 0
            var inRound = 0
            var correct = 0
            var wrong = 0
            var legacy = 0
            var expired = 0
            db.rawQuery(
                "SELECT state, last_result, COUNT(*) FROM $TABLE " +
                    "WHERE scope_key = ? GROUP BY state, last_result",
                arrayOf(scopeKey),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val state = cursor.getString(0)
                    val result = if (cursor.isNull(1)) null else cursor.getString(1)
                    val count = cursor.getInt(2)
                    total += count
                    when (state) {
                        STATE_AVAILABLE -> unused += count
                        STATE_RESERVED -> inRound += count
                        STATE_CONSUMED -> when (result) {
                            RESULT_CORRECT -> correct += count
                            RESULT_WRONG -> wrong += count
                            else -> legacy += count
                        }
                        STATE_EXPIRED -> expired += count
                    }
                }
            }
            BankCounts(total, unused, inRound, correct, wrong, legacy, expired)
        }.onFailure { Log.e(TAG, "bank counts failed", it) }.getOrDefault(BankCounts())
    }

    /**
     * Writes per-question outcomes. Repeating the same round is a no-op for
     * statistics, which makes recovery and retries safe.
     */
    fun writeAnswers(
        roundId: String,
        scopeKey: String,
        answers: List<AnswerWrite>,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        val db = database() ?: return false
        db.beginTransaction()
        try {
            writeAnswersLocked(db, roundId, scopeKey, answers, nowMs)
            db.setTransactionSuccessful()
            true
        } catch (error: Exception) {
            Log.e(TAG, "write answers failed", error)
            false
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Finalizes a round: writes every answer, then releases questions that were
     * reserved but never reached. Already answered rows stay consumed.
     */
    fun settleRound(
        roundId: String,
        scopeKey: String,
        answers: List<AnswerWrite>,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        val db = database() ?: return false
        db.beginTransaction()
        try {
            writeAnswersLocked(db, roundId, scopeKey, answers, nowMs)
            db.update(
                TABLE,
                ContentValues().apply {
                    put("state", STATE_AVAILABLE)
                    putNull("reserved_by")
                    putNull("reserved_at")
                },
                "scope_key = ? AND state = ? AND reserved_by = ?",
                arrayOf(scopeKey, STATE_RESERVED, roundId),
            )
            db.setTransactionSuccessful()
            true
        } catch (error: Exception) {
            Log.e(TAG, "settle round failed", error)
            false
        } finally {
            db.endTransaction()
        }
    }

    fun releaseOrphans(activeSessionId: String? = null): Boolean = synchronized(lock) {
        val db = database() ?: return false
        return runCatching {
            val values = ContentValues().apply {
                put("state", STATE_AVAILABLE)
                putNull("reserved_by")
                putNull("reserved_at")
            }
            if (activeSessionId == null) {
                db.update(TABLE, values, "state = ?", arrayOf(STATE_RESERVED))
            } else {
                db.update(
                    TABLE,
                    values,
                    "state = ? AND (reserved_by IS NULL OR reserved_by != ?)",
                    arrayOf(STATE_RESERVED, activeSessionId),
                )
            }
            true
        }.onFailure { Log.e(TAG, "release orphans failed", it) }.getOrDefault(false)
    }

    fun clearBank(scopeKey: String, mode: ClearMode): Boolean = synchronized(lock) {
        val db = database() ?: return false
        val (where, args) = when (mode) {
            ClearMode.UNUSED -> "scope_key = ? AND state IN (?, ?)" to
                arrayOf(scopeKey, STATE_AVAILABLE, STATE_EXPIRED)
            ClearMode.ANSWERED -> "scope_key = ? AND state = ?" to arrayOf(scopeKey, STATE_CONSUMED)
            ClearMode.CORRECT -> "scope_key = ? AND state = ? AND last_result = ?" to
                arrayOf(scopeKey, STATE_CONSUMED, RESULT_CORRECT)
            ClearMode.WRONG -> "scope_key = ? AND state = ? AND last_result = ?" to
                arrayOf(scopeKey, STATE_CONSUMED, RESULT_WRONG)
            ClearMode.ALL -> "scope_key = ?" to arrayOf(scopeKey)
        }
        return runCatching {
            db.delete(TABLE, where, args)
            true
        }.onFailure { Log.e(TAG, "clear bank failed", it) }.getOrDefault(false)
    }

    fun importLegacyReady(session: QuizSession, nowMs: Long = System.currentTimeMillis()): AppendResult {
        if (session.status != com.planyourtv.policy.quiz.QuizSessionStatus.READY) {
            return AppendResult(rejected = session.items.size)
        }
        if (session.source.startsWith("cloud")) return AppendResult(rejected = session.items.size)
        return appendLocalQuestions(session.id, session.items, nowMs)
    }

    fun migrateLegacyCloudScope(deviceNo: String): Boolean = synchronized(lock) {
        val db = database() ?: return false
        return runCatching {
            db.update(
                TABLE,
                ContentValues().apply { put("scope_key", cloudScope(deviceNo)) },
                "scope_key = ? AND source_kind = ?",
                arrayOf("cloud:legacy", SOURCE_CLOUD),
            )
            true
        }.onFailure { Log.e(TAG, "migrate cloud scope failed", it) }.getOrDefault(false)
    }

    fun clearCloudScopes(): Boolean = synchronized(lock) {
        val db = database() ?: return false
        return runCatching {
            db.delete(TABLE, "scope_key LIKE ?", arrayOf("cloud:%"))
            true
        }.onFailure { Log.e(TAG, "clear cloud scopes failed", it) }.getOrDefault(false)
    }

    fun release(sessionId: String): Boolean = synchronized(lock) {
        val db = database() ?: return false
        return runCatching {
            db.update(
                TABLE,
                ContentValues().apply {
                    put("state", STATE_AVAILABLE)
                    putNull("reserved_by")
                    putNull("reserved_at")
                },
                "state = ? AND reserved_by = ?",
                arrayOf(STATE_RESERVED, sessionId),
            )
            true
        }.onFailure { Log.e(TAG, "release failed", it) }.getOrDefault(false)
    }

    /** Legacy helper: marks reserved rows consumed without a per-question result. */
    fun consume(sessionId: String, nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val db = database() ?: return false
        return runCatching {
            db.update(
                TABLE,
                ContentValues().apply {
                    put("state", STATE_CONSUMED)
                    put("consumed_at", nowMs)
                },
                "state = ? AND reserved_by = ?",
                arrayOf(STATE_RESERVED, sessionId),
            )
            true
        }.onFailure { Log.e(TAG, "consume failed", it) }.getOrDefault(false)
    }

    fun removePublished(sessionId: String): Boolean = synchronized(lock) {
        val db = database() ?: return false
        return runCatching {
            db.delete(
                TABLE,
                "source_ref LIKE ? AND state = ?",
                arrayOf("local:$sessionId:%", STATE_AVAILABLE),
            )
            true
        }.onFailure { Log.e(TAG, "remove published failed", it) }.getOrDefault(false)
    }

    fun clearAvailable(scopeKey: String = LOCAL_SCOPE): Boolean = synchronized(lock) {
        val db = database() ?: return false
        return runCatching {
            db.delete(
                TABLE,
                "scope_key = ? AND state = ?",
                arrayOf(scopeKey, STATE_AVAILABLE),
            )
            true
        }.onFailure { Log.e(TAG, "clear available failed", it) }.getOrDefault(false)
    }

    fun clearUnconsumed(scopeKey: String = LOCAL_SCOPE): Boolean = synchronized(lock) {
        val db = database() ?: return false
        return runCatching {
            db.delete(
                TABLE,
                "scope_key = ? AND state IN (?, ?)",
                arrayOf(scopeKey, STATE_AVAILABLE, STATE_RESERVED),
            )
            true
        }.onFailure { Log.e(TAG, "clear unconsumed failed", it) }.getOrDefault(false)
    }

    fun counts(scopeKey: String = LOCAL_SCOPE, nowMs: Long = System.currentTimeMillis()): Counts =
        synchronized(lock) {
            val db = database() ?: return Counts()
            runCatching {
                expire(db, scopeKey, nowMs)
                val result = IntArray(4)
                db.rawQuery(
                    "SELECT state, COUNT(*) FROM $TABLE WHERE scope_key = ? GROUP BY state",
                    arrayOf(scopeKey),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val index = when (cursor.getString(0)) {
                            STATE_AVAILABLE -> 0
                            STATE_RESERVED -> 1
                            STATE_CONSUMED -> 2
                            STATE_EXPIRED -> 3
                            else -> -1
                        }
                        if (index >= 0) result[index] = cursor.getInt(1)
                    }
                }
                Counts(result[0], result[1], result[2], result[3])
            }.onFailure { Log.e(TAG, "counts failed", it) }.getOrDefault(Counts())
        }

    fun availableCount(
        scopeKey: String = LOCAL_SCOPE,
        nowMs: Long = System.currentTimeMillis(),
        answerScope: QuizAnswerScope? = null,
    ): Int = synchronized(lock) {
        if (answerScope == null) return counts(scopeKey, nowMs).available
        val db = database() ?: return 0
        runCatching {
            expire(db, scopeKey, nowMs)
            val (scopeWhere, scopeArgs) = answerScopeWhere(answerScope)
            db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE WHERE scope_key = ? AND state = ? " +
                    "AND (expires_at IS NULL OR expires_at > ?)$scopeWhere",
                arrayOf(scopeKey, STATE_AVAILABLE, nowMs.toString()) + scopeArgs,
            ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }.onFailure { Log.e(TAG, "scoped available count failed", it) }.getOrDefault(0)
    }

    /**
     * The SQL twin of the old in-memory matcher. Grade, semester, topic and
     * difficulty are always strict; subject/textbook are wildcards only when
     * the answer scope uses `ALL`. Rows without a scope never match a custom
     * range because their nullable columns yield NULL, not a wildcard.
     */
    private fun answerScopeWhere(scope: QuizAnswerScope?): Pair<String, List<String>> {
        if (scope == null) return "" to emptyList()
        val clauses = mutableListOf(
            "scope_grade = ?",
            "scope_semester = ?",
            "UPPER(scope_type) = ?",
        )
        val args = mutableListOf(
            scope.grade.toString(),
            scope.semester.toString(),
            scope.scopeType.uppercase(),
        )
        if (!scope.subject.equals(QuizAnswerScope.SUBJECT_ALL, ignoreCase = true)) {
            clauses += "UPPER(scope_subject) = ?"
            args += scope.subject.uppercase()
        }
        if (!scope.textbook.equals(QuizAnswerScope.TEXTBOOK_ALL, ignoreCase = true)) {
            clauses += "UPPER(scope_textbook) = ?"
            args += scope.textbook.uppercase()
        }
        if (scope.scopeType.equals("UNIT", ignoreCase = true)) {
            clauses += "scope_unit_no = ?"
            args += scope.unitNo.toString()
        } else {
            clauses += "scope_unit_no IS NULL"
        }
        val difficulty = scope.difficulty
        if (difficulty != null) {
            clauses += "LOWER(difficulty) = ?"
            args += difficulty.lowercase()
        }
        return " AND " + clauses.joinToString(" AND ") to args
    }

    private fun database(): SQLiteDatabase? = helper?.writableDatabase

    private fun expire(db: SQLiteDatabase, scopeKey: String, nowMs: Long) {
        db.update(
            TABLE,
            ContentValues().apply { put("state", STATE_EXPIRED) },
            "scope_key = ? AND state = ? AND expires_at IS NOT NULL AND expires_at <= ?",
            arrayOf(scopeKey, STATE_AVAILABLE, nowMs.toString()),
        )
    }

    private fun rowCount(db: SQLiteDatabase, scopeKey: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $TABLE WHERE scope_key = ?", arrayOf(scopeKey)).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    private fun countState(db: SQLiteDatabase, scopeKey: String, state: String): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE scope_key = ? AND state = ?",
            arrayOf(scopeKey, state),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun fingerprintExists(db: SQLiteDatabase, scopeKey: String, fp: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM $TABLE WHERE scope_key = ? AND fingerprint = ? LIMIT 1",
            arrayOf(scopeKey, fp),
        ).use { it.moveToFirst() }

    private fun sourceRefExists(db: SQLiteDatabase, sourceKind: String, sourceRef: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM $TABLE WHERE source_kind = ? AND source_ref = ? LIMIT 1",
            arrayOf(sourceKind, sourceRef),
        ).use { it.moveToFirst() }

    private fun writeAnswersLocked(
        db: SQLiteDatabase,
        roundId: String,
        scopeKey: String,
        answers: List<AnswerWrite>,
        nowMs: Long,
    ) {
        answers.forEach { answer ->
            val row = findAnswerRow(db, scopeKey, answer) ?: return@forEach
            val (cacheId, state, lastRoundId, wrongCount) = row
            if (state == STATE_CONSUMED && lastRoundId == roundId) return@forEach
            if (state != STATE_RESERVED && !(state == STATE_CONSUMED && lastRoundId.isNullOrBlank())) {
                return@forEach
            }
            val values = ContentValues().apply {
                put("state", STATE_CONSUMED)
                put("last_result", if (answer.correct) RESULT_CORRECT else RESULT_WRONG)
                put("wrong_count", wrongCount + if (answer.correct) 0 else 1)
                put("answered_at", if (answer.answeredAt > 0) answer.answeredAt else nowMs)
                put("last_round_id", roundId)
                put("last_timed_out", if (answer.timedOut) 1 else 0)
                if (answer.selectedIndex == null) {
                    putNull("last_selected_index")
                } else {
                    put("last_selected_index", answer.selectedIndex)
                }
                put("consumed_at", nowMs)
                putNull("reserved_by")
                putNull("reserved_at")
            }
            db.update(TABLE, values, "cache_id = ?", arrayOf(cacheId))
        }
    }

    private fun findAnswerRow(
        db: SQLiteDatabase,
        scopeKey: String,
        answer: AnswerWrite,
    ): AnswerRow? {
        val selection: String
        val args: Array<String>
        if (answer.cacheId.isNotBlank()) {
            selection = "scope_key = ? AND cache_id = ?"
            args = arrayOf(scopeKey, answer.cacheId)
        } else if (answer.questionId.isNotBlank()) {
            selection = "scope_key = ? AND question_id = ?"
            args = arrayOf(scopeKey, answer.questionId)
        } else {
            return null
        }
        return db.rawQuery(
            "SELECT cache_id, state, last_round_id, wrong_count FROM $TABLE " +
                "WHERE $selection LIMIT 1",
            args,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            AnswerRow(
                cacheId = cursor.getString(0),
                state = cursor.getString(1),
                lastRoundId = if (cursor.isNull(2)) null else cursor.getString(2),
                wrongCount = cursor.getInt(3),
            )
        }
    }

    private data class AnswerRow(
        val cacheId: String,
        val state: String,
        val lastRoundId: String?,
        val wrongCount: Int,
    )

    private fun readQuestion(cursor: Cursor): CachedQuestion {
        val raw = cursor.getString(1)
        val question = QuizSessionCodec.decodeItem(raw)
            ?: throw CacheException("corrupt cached question")
        return CachedQuestion(
            cacheId = cursor.getString(0),
            question = question,
            sourceKind = cursor.getString(2) ?: "",
            sourceRef = if (cursor.isNull(3)) null else cursor.getString(3),
            scopeKey = cursor.getString(4),
            receivedAt = cursor.getLong(5),
            expiresAt = if (cursor.isNull(6)) null else cursor.getLong(6),
            state = cursor.getString(7) ?: STATE_AVAILABLE,
        )
    }

    private fun readBankRow(cursor: Cursor): BankRow {
        val raw = cursor.getString(1)
        val question = QuizSessionCodec.decodeItem(raw)
            ?: throw CacheException("corrupt cached question")
        return BankRow(
            cacheId = cursor.getString(0),
            question = question,
            sourceKind = cursor.getString(2) ?: "",
            sourceRef = if (cursor.isNull(3)) null else cursor.getString(3),
            scopeKey = cursor.getString(4),
            receivedAt = cursor.getLong(5),
            expiresAt = if (cursor.isNull(6)) null else cursor.getLong(6),
            state = cursor.getString(7) ?: STATE_AVAILABLE,
            lastResult = if (cursor.isNull(8)) null else cursor.getString(8),
            wrongCount = cursor.getInt(9),
            answeredAt = if (cursor.isNull(10)) null else cursor.getLong(10),
            timedOut = cursor.getInt(11) != 0,
            selectedIndex = if (cursor.isNull(12)) null else cursor.getInt(12),
        )
    }

    private fun isValid(item: QuizItem): Boolean = runCatching {
        QuizSessionCodec.decodeItem(QuizSessionCodec.encodeItem(item).toString()) != null
    }.getOrDefault(false)

    /**
     * Scope columns let the hot paths (bank state after every answer, reserve
     * at round start) filter in SQLite instead of decoding the whole bank.
     * `question_json` remains the source of truth; these are a query index.
     */
    private fun ContentValues.putScopeFields(item: QuizItem) {
        if (item.scopeGrade == null) putNull("scope_grade") else put("scope_grade", item.scopeGrade)
        if (item.scopeSemester == null) putNull("scope_semester") else put("scope_semester", item.scopeSemester)
        put("scope_subject", item.scopeSubject)
        put("scope_textbook", item.scopeTextbook)
        put("scope_type", item.scopeType)
        if (item.scopeUnitNo == null) putNull("scope_unit_no") else put("scope_unit_no", item.scopeUnitNo)
        put("difficulty", item.difficulty)
    }

    private fun fingerprint(item: QuizItem): String {
        val canonical = item.prompt.trim().lowercase() + " " +
            item.options.joinToString(" ") { it.trim().lowercase() } + " " +
            item.answerIndex
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private class Helper(context: Context, val dbName: String) :
        SQLiteOpenHelper(context, dbName, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (" +
                    "cache_id TEXT PRIMARY KEY, " +
                    "question_json TEXT NOT NULL, " +
                    "question_id TEXT NOT NULL DEFAULT '', " +
                    "source_kind TEXT NOT NULL, " +
                    "source_ref TEXT, " +
                    "scope_key TEXT NOT NULL, " +
                    "scope_grade INTEGER, " +
                    "scope_semester INTEGER, " +
                    "scope_subject TEXT NOT NULL DEFAULT '', " +
                    "scope_textbook TEXT NOT NULL DEFAULT '', " +
                    "scope_type TEXT NOT NULL DEFAULT '', " +
                    "scope_unit_no INTEGER, " +
                    "difficulty TEXT NOT NULL DEFAULT '', " +
                    "fingerprint TEXT NOT NULL, " +
                    "state TEXT NOT NULL, " +
                    "reserved_by TEXT, " +
                    "received_at INTEGER NOT NULL, " +
                    "expires_at INTEGER, " +
                    "reserved_at INTEGER, " +
                    "consumed_at INTEGER, " +
                    "last_result TEXT, " +
                    "wrong_count INTEGER NOT NULL DEFAULT 0, " +
                    "answered_at INTEGER, " +
                    "last_round_id TEXT, " +
                    "last_timed_out INTEGER NOT NULL DEFAULT 0, " +
                    "last_selected_index INTEGER)",
            )
            createIndexes(db)
            createAnswerScopeIndex(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 1) {
                onCreate(db)
                return
            }
            if (oldVersion < 2) {
                addColumn(db, "question_id", "TEXT NOT NULL DEFAULT ''")
                addColumn(db, "last_result", "TEXT")
                addColumn(db, "wrong_count", "INTEGER NOT NULL DEFAULT 0")
                addColumn(db, "answered_at", "INTEGER")
                addColumn(db, "last_round_id", "TEXT")
                addColumn(db, "last_timed_out", "INTEGER NOT NULL DEFAULT 0")
                addColumn(db, "last_selected_index", "INTEGER")
                backfillQuestionIds(db)
                db.execSQL(
                    "UPDATE $TABLE SET scope_key = 'cloud:legacy' " +
                        "WHERE scope_key = ? AND source_kind = ?",
                    arrayOf(LOCAL_SCOPE, SOURCE_CLOUD),
                )
                createIndexes(db)
            }
            if (oldVersion < 3) {
                addScopeColumns(db)
            }
        }

        private fun addScopeColumns(db: SQLiteDatabase) {
            addColumn(db, "scope_grade", "INTEGER")
            addColumn(db, "scope_semester", "INTEGER")
            addColumn(db, "scope_subject", "TEXT NOT NULL DEFAULT ''")
            addColumn(db, "scope_textbook", "TEXT NOT NULL DEFAULT ''")
            addColumn(db, "scope_type", "TEXT NOT NULL DEFAULT ''")
            addColumn(db, "scope_unit_no", "INTEGER")
            addColumn(db, "difficulty", "TEXT NOT NULL DEFAULT ''")
            backfillScopeColumns(db)
            createIndexes(db)
            createAnswerScopeIndex(db)
        }

        private fun backfillScopeColumns(db: SQLiteDatabase) {
            val rows = mutableListOf<Pair<String, String>>()
            db.rawQuery(
                "SELECT cache_id, question_json FROM $TABLE WHERE scope_type = ''",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows += cursor.getString(0) to cursor.getString(1)
                }
            }
            rows.forEach { (cacheId, raw) ->
                val item = QuizSessionCodec.decodeItem(raw) ?: return@forEach
                db.update(
                    TABLE,
                    ContentValues().apply { putScopeFields(item) },
                    "cache_id = ?",
                    arrayOf(cacheId),
                )
            }
        }

        private fun createIndexes(db: SQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_qc_state ON $TABLE(state, expires_at)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_qc_scope ON $TABLE(scope_key, state)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_qc_bank_filter " +
                    "ON $TABLE(scope_key, state, last_result, received_at)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_qc_scope_fingerprint " +
                    "ON $TABLE(scope_key, fingerprint)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_qc_question ON $TABLE(scope_key, question_id)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_qc_source ON $TABLE(source_kind, source_ref) " +
                    "WHERE source_ref IS NOT NULL",
            )
        }

        private fun createAnswerScopeIndex(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_qc_answer_scope " +
                    "ON $TABLE(scope_key, state, scope_grade, scope_semester, scope_type)",
            )
        }

        private fun addColumn(db: SQLiteDatabase, name: String, type: String) {
            runCatching {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN $name $type")
            }.onFailure { error ->
                if (!error.message.orEmpty().contains("duplicate column")) {
                    Log.w(TAG, "cache migration add column $name failed", error)
                }
            }
        }

        private fun backfillQuestionIds(db: SQLiteDatabase) {
            val rows = mutableListOf<Pair<String, String>>()
            db.rawQuery(
                "SELECT cache_id, question_json FROM $TABLE WHERE question_id = ''",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows += cursor.getString(0) to cursor.getString(1)
                }
            }
            rows.forEach { (cacheId, raw) ->
                val item = QuizSessionCodec.decodeItem(raw) ?: return@forEach
                db.update(
                    TABLE,
                    ContentValues().apply { put("question_id", item.questionId) },
                    "cache_id = ?",
                    arrayOf(cacheId),
                )
            }
        }
    }
}
