package app.orchard.tv.quiz

import android.util.Log
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizWrongReviewLevel

/**
 * The optional wrong-review level lives under its own key. It deliberately
 * does not share the custom-level list: the tag-level cap and the
 * collection-id parser must never decide whether this level survives.
 */
object QuizWrongReviewLevelStore {
    private const val TAG = "QuizWrongReviewLevelStore"
    private const val KEY = "quiz.wrongReviewLevel"

    private val lock = Any()

    fun level(): QuizWrongReviewLevel? = synchronized(lock) {
        val raw = TvStore.getString(KEY) ?: return null
        val parsed = QuizWrongReviewLevel.parseEncoded(raw)
        if (parsed == null) Log.e(TAG, "stored wrong-review level is corrupt; ignoring it")
        parsed
    }

    fun save(level: QuizWrongReviewLevel): Boolean = synchronized(lock) {
        val parsed = QuizWrongReviewLevel.parse(level.encode()) ?: return false
        TvStore.putStringDurable(KEY, parsed.encode().toString())
    }

    fun clear(): Boolean = synchronized(lock) {
        TvStore.removeDurable(KEY)
    }
}
