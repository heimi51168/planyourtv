package app.orchard.tv.quiz

import android.util.Log
import app.orchard.tv.policy.FamilyHub
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

    /** Wrong-review is a parent-designed level, so it follows the active child. */
    private fun key(base: String): String = QuizSubjectScope.key(base)

    /** A named member's key, for the web editor while another child watches. */
    private fun keyFor(base: String, userId: String): String {
        val family = FamilyHub.state.value
        if (!family.ready) return base
        return QuizSubjectScope.keyFor(family, userId, base)
    }

    fun level(): QuizWrongReviewLevel? = read(key(KEY))

    /** The wrong-review level of a named member; never moves the active pointer. */
    fun levelFor(userId: String): QuizWrongReviewLevel? = read(keyFor(KEY, userId))

    private fun read(storageKey: String): QuizWrongReviewLevel? = synchronized(lock) {
        val raw = TvStore.getString(storageKey) ?: return null
        val parsed = QuizWrongReviewLevel.parseEncoded(raw)
        if (parsed == null) Log.e(TAG, "stored wrong-review level is corrupt; ignoring it")
        parsed
    }

    fun save(level: QuizWrongReviewLevel): Boolean = write(key(KEY), level)

    fun saveFor(userId: String, level: QuizWrongReviewLevel): Boolean =
        write(keyFor(KEY, userId), level)

    private fun write(storageKey: String, level: QuizWrongReviewLevel): Boolean =
        synchronized(lock) {
            val parsed = QuizWrongReviewLevel.parse(level.encode()) ?: return false
            TvStore.putStringDurable(storageKey, parsed.encode().toString())
        }

    fun clear(): Boolean = synchronized(lock) {
        TvStore.removeDurable(key(KEY))
    }

    fun clearFor(userId: String): Boolean = synchronized(lock) {
        TvStore.removeDurable(keyFor(KEY, userId))
    }
}
