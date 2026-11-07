package app.orchard.tv.quiz

import android.util.Log
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizSubmission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Durable write-behind queue for the cloud attempt.
 *
 * Why this exists: QB keeps one RUNNING attempt per device learner and refuses
 * every new `next` until it is submitted or abandoned. The TV can lose network
 * mid-round or be killed, so "fire and forget" uploads would leave a stuck
 * attempt that blocks the next round forever. Each answer is appended to this
 * queue first and uploaded in order:
 *
 *   START -> ANSWERS -> SUBMIT | ABANDON
 *
 * The queue survives restarts. A terminal QB rejection (409/404: already
 * submitted, abandoned or expired) drops the record, because retrying can never
 * succeed; a transport failure keeps it and retries, because the request may
 * never have reached the server. Order is enforced by [flushNow], so an answer
 * can never overtake the start call.
 */
object QuizCloudOutbox {
    private const val TAG = "QuizCloudOutbox"
    private const val KEY_OUTBOX = "quiz.cloudOutbox.v1"
    private const val CLOSE_SUBMIT = "SUBMIT"
    private const val CLOSE_ABANDON = "ABANDON"
    private const val RETRY_DELAY_MS = 30_000L

    private data class Record(
        val attemptUid: String,
        var started: Boolean = false,
        val answers: MutableList<QuizSubmission> = mutableListOf(),
        var close: String = "",
    )

    private val lock = Any()
    private val flushMutex = Mutex()
    private val flushRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun attemptUidOf(sessionId: String): String? {
        if (!sessionId.startsWith(QbClient.SESSION_ID_PREFIX)) return null
        val uid = sessionId.removePrefix(QbClient.SESSION_ID_PREFIX)
        return uid.ifBlank { null }
    }

    /** Registers a fresh attempt as soon as its questions are cached. */
    fun begin(attemptUid: String) = synchronized(lock) {
        if (attemptUid.isBlank()) return
        val records = readRecords()
        // Re-preparing the same unstarted attempt must not wipe a queued answer.
        if (records.any { it.attemptUid == attemptUid }) return
        records += Record(attemptUid)
        writeRecords(records)
    }

    fun markStarted(attemptUid: String) = synchronized(lock) {
        update(attemptUid) { it.started = true }
    }

    /** Appends one answer; a repeated question id replaces the earlier upload. */
    fun appendAnswer(attemptUid: String, submission: QuizSubmission) = synchronized(lock) {
        update(attemptUid) { record ->
            record.answers.removeAll { it.questionId == submission.questionId }
            record.answers += submission
        }
    }

    fun markSubmit(attemptUid: String) = synchronized(lock) {
        update(attemptUid) { it.close = CLOSE_SUBMIT }
    }

    fun markAbandon(attemptUid: String) = synchronized(lock) {
        update(attemptUid) { it.close = CLOSE_ABANDON }
    }

    fun hasPending(attemptUid: String? = null): Boolean = synchronized(lock) {
        val records = readRecords()
        if (attemptUid == null) records.isNotEmpty()
        else records.any { it.attemptUid == attemptUid && recordPending(it) }
    }

    /** Drops the record. Only for states the server can never accept again. */
    fun reset(attemptUid: String? = null) = synchronized(lock) {
        if (attemptUid == null) {
            writeRecords(emptyList())
            return
        }
        writeRecords(readRecords().filterNot { it.attemptUid == attemptUid })
    }

    /**
     * Uploads every pending record in order. Returns true only when the queue is
     * empty afterwards; a transport failure leaves the record for a later retry.
     */
    suspend fun flushNow(): Boolean = flushMutex.withLock {
        withContext(Dispatchers.IO) {
            var allDone = true
            var records = synchronized(lock) { readRecords() }
            for (record in records) {
                if (!flushRecord(record)) {
                    allDone = false
                    continue
                }
                // Re-read between records so a close marked by another thread
                // during the upload is picked up by the next iteration.
                records = synchronized(lock) { readRecords() }
            }
            allDone && synchronized(lock) { readRecords().none(::recordPending) }
        }
    }

    /**
     * Fire-and-forget upload with one delayed retry. Used after each answer and
     * before settlement; callers that need certainty use [flushNow] directly.
     */
    fun flushAsync() {
        if (!flushRunning.compareAndSet(false, true)) return
        scope.launch {
            try {
                if (flushNow() || !hasPending()) return@launch
                delay(RETRY_DELAY_MS)
                if (hasPending()) flushNow()
            } catch (error: Exception) {
                Log.e(TAG, "cloud outbox flush failed", error)
            } finally {
                flushRunning.set(false)
            }
        }
    }

    private fun flushRecord(record: Record): Boolean {
        val uid = record.attemptUid
        if (!record.started) {
            if (!call(QbClient::startAttempt, uid, "start")) return false
            markStarted(uid)
        }
        val answers = synchronized(lock) {
            readRecords().firstOrNull { it.attemptUid == uid }?.answers?.toList().orEmpty()
        }
        if (answers.isNotEmpty()) {
            if (!call({ QbClient.saveAnswers(uid, answers) }, uid, "answers")) return false
            val uploaded = answers.map { it.questionId }.toSet()
            synchronized(lock) {
                update(uid) { it.answers.removeAll { answer -> answer.questionId in uploaded } }
            }
        }
        val close = synchronized(lock) {
            readRecords().firstOrNull { it.attemptUid == uid }?.close.orEmpty()
        }
        when (close) {
            CLOSE_SUBMIT -> {
                if (!call({ QbClient.submitAttempt(uid) }, uid, "submit")) return false
                reset(uid)
            }
            CLOSE_ABANDON -> {
                if (!call(QbClient::abandonAttempt, uid, "abandon")) return false
                reset(uid)
            }
            else -> Unit
        }
        return true
    }

    /**
     * Runs one QB call. Returns true on success, false on transport failure
     * (keep the record and retry later) and true on a terminal business
     * rejection (drop the record because no retry can succeed).
     */
    private fun call(action: (String) -> Unit, attemptUid: String, step: String): Boolean {
        return try {
            action(attemptUid)
            true
        } catch (error: QbClient.QbException) {
            if (isTerminal(error)) {
                Log.w(TAG, "cloud attempt $attemptUid rejected at $step: ${error.message}")
                reset(attemptUid)
                true
            } else {
                Log.w(TAG, "cloud attempt $attemptUid will retry at $step: ${error.message}")
                false
            }
        } catch (error: Exception) {
            Log.w(TAG, "cloud attempt $attemptUid transport failure at $step: ${error.message}")
            false
        }
    }

    private fun isTerminal(error: QbClient.QbException): Boolean =
        error.httpCode == 404 || error.apiCode == 404 ||
            error.httpCode == 409 || error.apiCode == 409

    private fun recordPending(record: Record): Boolean =
        !record.started || record.answers.isNotEmpty() || record.close.isNotEmpty()

    private fun update(attemptUid: String, mutate: (Record) -> Unit) {
        val records = readRecords()
        val record = records.firstOrNull { it.attemptUid == attemptUid }
        if (record == null) {
            Log.w(TAG, "no outbox record for attempt $attemptUid")
            return
        }
        mutate(record)
        writeRecords(records)
    }

    private fun readRecords(): MutableList<Record> {
        val raw = TvStore.getString(KEY_OUTBOX, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val uid = json.optString("attemptUid", "").trim()
                if (uid.isEmpty()) return@mapNotNull null
                val answersJson = json.optJSONArray("answers") ?: JSONArray()
                val answers = (0 until answersJson.length()).mapNotNull { answerIndex ->
                    val answer = answersJson.optJSONObject(answerIndex) ?: return@mapNotNull null
                    val questionId = answer.optString("questionId", "").trim()
                    if (questionId.isEmpty()) return@mapNotNull null
                    QuizSubmission(
                        questionId = questionId,
                        selectedIndex = if (answer.isNull("selectedIndex")) {
                            null
                        } else {
                            answer.optInt("selectedIndex")
                        },
                        correct = answer.optBoolean("correct", false),
                        timedOut = answer.optBoolean("timedOut", false),
                        answeredAt = answer.optLong("answeredAt", 0L),
                    )
                }.toMutableList()
                Record(
                    attemptUid = uid,
                    started = json.optBoolean("started", false),
                    answers = answers,
                    close = json.optString("close", ""),
                )
            }.toMutableList()
        } catch (error: Exception) {
            // The stored queue cannot be parsed. Keeping it would block every
            // future round, so report it and start clean; the affected attempt
            // is abandoned server-side by QB's own expiry/409 handling.
            Log.e(TAG, "cloud outbox is corrupt; discarding it", error)
            mutableListOf()
        }
    }

    private fun writeRecords(records: List<Record>) {
        val array = JSONArray()
        records.forEach { record ->
            val answers = JSONArray()
            record.answers.forEach { answer ->
                answers.put(
                    JSONObject()
                        .put("questionId", answer.questionId)
                        .put(
                            "selectedIndex",
                            answer.selectedIndex?.let { it } ?: JSONObject.NULL,
                        )
                        .put("correct", answer.correct)
                        .put("timedOut", answer.timedOut)
                        .put("answeredAt", answer.answeredAt),
                )
            }
            array.put(
                JSONObject()
                    .put("attemptUid", record.attemptUid)
                    .put("started", record.started)
                    .put("answers", answers)
                    .put("close", record.close),
            )
        }
        TvStore.putStringDurable(KEY_OUTBOX, array.toString())
    }
}
