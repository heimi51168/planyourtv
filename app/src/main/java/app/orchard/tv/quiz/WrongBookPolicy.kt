package app.orchard.tv.quiz

import android.util.Log
import com.planyourtv.policy.TvStore

/**
 * The wrong book's own weekly cycle: the questions answered wrongly are mixed into
 * practice, answering one correctly takes it out (the mastery hook already lives in
 * [QuizQuestionCache.writeAnswersLocked]), and once a wrong answer is seven days old
 * practice only serves the wrong book until it is empty again.
 *
 * The cycle start is kept here rather than in the question cache: adding a column
 * would force a schema upgrade and a backfill for a single timestamp, and the book is
 * empty when there is nothing to date. Two keys, both written through TvStore:
 * [KEY_CYCLE_START] (when the book first had something in it, 0 when it is empty) and
 * [KEY_LAST_CLEAR] (when it was last emptied, for the web page to show).
 *
 * Only the local (unbound) bank is covered. Cloud rounds pull their questions from the
 * server through [CloudQuizPreparer], so their wrong answers never reach this scope;
 * every caller here reads [QuizQuestionCache.LOCAL_SCOPE].
 */
object WrongBookPolicy {
    private const val TAG = "WrongBookPolicy"
    private const val KEY_CYCLE_START = "quiz.wrongBook.cycleStartMs"
    private const val KEY_LAST_CLEAR = "quiz.wrongBook.lastClearMs"

    /** A wrong answer is due for the wrong-only round after this long. */
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000L
    private const val DAY_MS = 24L * 60 * 60 * 1000L

    private val lock = Any()

    /**
     * What practice has to do with the wrong book right now.
     *
     * [count] is how many questions the book holds, [overdue] whether the cycle ran out
     * while it still held some, and [daysLeft] how long is left of the cycle (0 once
     * overdue).
     */
    data class State(
        val count: Int,
        val cycleStartMs: Long,
        val overdue: Boolean,
        val daysLeft: Int,
    ) {
        /** Questions go into a normal round with new ones while the cycle is still running. */
        val mixing: Boolean get() = count > 0 && !overdue
    }

    /**
     * Reads the book and dates it: a book that is empty closes the cycle, and the first
     * wrong answer of a new cycle starts it. Callers that write answers or clear the
     * book run this straight after, so a cycle never starts days late.
     */
    fun state(nowMs: Long = System.currentTimeMillis()): State = synchronized(lock) {
        val count = runCatching { QuizQuestionCache.wrongReviewCount() }.getOrDefault(0)
        if (count <= 0) {
            if (cycleStart() != 0L) saveCycleStart(0L)
            return State(count = 0, cycleStartMs = 0L, overdue = false, daysLeft = 0)
        }
        var start = cycleStart()
        if (start <= 0L) {
            start = nowMs
            saveCycleStart(start)
        }
        val elapsed = nowMs - start
        val remaining = WEEK_MS - elapsed
        State(
            count = count,
            cycleStartMs = start,
            overdue = remaining <= 0L,
            daysLeft = if (remaining <= 0L) 0 else ((remaining + DAY_MS - 1) / DAY_MS).toInt(),
        )
    }

    /** Practice may only serve wrong answers until the book is empty; see [state]. */
    fun overdue(nowMs: Long = System.currentTimeMillis()): Boolean = state(nowMs).overdue

    /**
     * The parent emptied the book (or practice worked through it): the next wrong answer
     * starts a fresh cycle, so the week begins again from here.
     */
    fun markCleared(nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        TvStore.putLong(KEY_LAST_CLEAR, nowMs)
        saveCycleStart(0L)
        TvStore.sync()
    }

    /** When the book was last emptied, 0 when it never was. */
    fun lastClearMs(): Long = runCatching { TvStore.getLong(KEY_LAST_CLEAR, 0L) }.getOrDefault(0L)

    private fun cycleStart(): Long = runCatching { TvStore.getLong(KEY_CYCLE_START, 0L) }.getOrDefault(0L)

    private fun saveCycleStart(value: Long) {
        runCatching { TvStore.putLong(KEY_CYCLE_START, value) }
            .onFailure { Log.e(TAG, "could not store the wrong-book cycle start", it) }
        runCatching { TvStore.sync() }
    }
}
