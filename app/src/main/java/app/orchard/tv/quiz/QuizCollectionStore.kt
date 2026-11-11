package app.orchard.tv.quiz

import android.util.Log
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizCollection
import com.planyourtv.policy.quiz.QuizCollectionLevel
import org.json.JSONArray
import java.util.Locale

/**
 * Local tags and the levels that draw from them. Both live under their own
 * TvStore keys so a cloud scope refresh, which only rewrites
 * `quiz.answerScopes`, can never erase a parent's imported material.
 */
object QuizCollectionStore {
    private const val TAG = "QuizCollectionStore"
    private const val KEY_COLLECTIONS = "quiz.collections"
    private const val KEY_CUSTOM_LEVELS = "quiz.customLevels"

    private val lock = Any()

    fun collections(): List<QuizCollection> = synchronized(lock) {
        val raw = TvStore.getString(KEY_COLLECTIONS) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull()
        val parsed = QuizCollection.parseList(array)
        if (parsed == null) Log.e(TAG, "stored tags are corrupt; ignoring them")
        parsed.orEmpty()
    }

    fun hasCollections(): Boolean = TvStore.getString(KEY_COLLECTIONS) != null

    fun find(id: String?): QuizCollection? {
        if (id.isNullOrBlank()) return null
        return collections().firstOrNull { it.id == id }
    }

    fun nameExists(name: String, excludingId: String? = null): Boolean {
        val normalized = QuizCollection.normalizeName(name) ?: return false
        return collections().any {
            it.id != excludingId && it.name.equals(normalized, ignoreCase = true)
        }
    }

    /** Adds one tag at the end of the list; null on duplicate name, limit, or IO failure. */
    fun addCollection(name: String): QuizCollection? = synchronized(lock) {
        val existing = collections()
        val collection = QuizCollection.create(name) ?: return null
        if (existing.size >= QuizCollection.MAX_COLLECTIONS) return null
        if (nameExists(collection.name)) return null
        if (!saveCollectionsLocked(existing + collection)) return null
        collection
    }

    /** Renames an existing tag; null when absent, duplicate, or IO failed. */
    fun renameCollection(id: String, name: String): QuizCollection? = synchronized(lock) {
        val existing = collections()
        val index = existing.indexOfFirst { it.id == id }
        if (index < 0) return null
        val cleanName = QuizCollection.normalizeName(name) ?: return null
        if (existing.any { it.id != id && it.name.equals(cleanName, ignoreCase = true) }) return null
        val updated = existing.toMutableList().also {
            it[index] = it[index].copy(name = cleanName)
        }
        if (!saveCollectionsLocked(updated)) return null
        updated[index]
    }

    fun saveCollections(collections: List<QuizCollection>): Boolean = synchronized(lock) {
        saveCollectionsLocked(collections)
    }

    /**
     * Best-effort rollback for a tag created for an import that then failed.
     * A tag still referenced by a custom level is kept so no level is orphaned.
     */
    fun removeCollection(id: String): Boolean = synchronized(lock) {
        val existing = collections()
        if (existing.none { it.id == id }) return true
        if (customLevels().any { it.collectionId == id }) return false
        saveCollectionsLocked(existing.filterNot { it.id == id })
    }

    private fun saveCollectionsLocked(collections: List<QuizCollection>): Boolean {
        if (QuizCollection.parseList(QuizCollection.encodeList(collections)) == null) return false
        return TvStore.putStringDurable(KEY_COLLECTIONS, QuizCollection.encodeList(collections).toString())
    }

    /**
     * Custom levels whose tag still exists. A level pointing at a manually
     * removed tag is hidden, not silently rewritten: the row stays on disk so a
     * repaired collection list can recover it, matching the "never drop parent
     * data on read" rule used by answer scopes.
     */
    fun customLevels(): List<QuizCollectionLevel> = synchronized(lock) {
        val raw = TvStore.getString(KEY_CUSTOM_LEVELS) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull()
        val parsed = QuizCollectionLevel.parseList(array)
        if (parsed == null) {
            Log.e(TAG, "stored custom levels are corrupt; ignoring them")
            return emptyList()
        }
        val ids = collections().mapTo(HashSet()) { it.id }
        val valid = parsed.filter { it.collectionId in ids }
        (parsed.size - valid.size).takeIf { it > 0 }?.let {
            Log.w(TAG, "$it custom level(s) reference a missing tag")
        }
        valid
    }

    fun hasCustomLevels(): Boolean = TvStore.getString(KEY_CUSTOM_LEVELS) != null

    fun customLevel(id: String?): QuizCollectionLevel? {
        if (id.isNullOrBlank()) return null
        return customLevels().firstOrNull { it.id == id }
    }

    fun saveCustomLevels(levels: List<QuizCollectionLevel>): Boolean = synchronized(lock) {
        val parsed = QuizCollectionLevel.parseList(
            QuizCollectionLevel.encodeList(levels),
            MAX_CUSTOM_LEVELS,
        ) ?: return false
        val collections = collections().mapTo(HashSet()) { it.id }
        if (parsed.any { it.collectionId !in collections }) return false
        TvStore.putStringDurable(KEY_CUSTOM_LEVELS, QuizCollectionLevel.encodeList(parsed).toString())
    }

    /** Stable order for the TV: course levels first, then tags in saved order. */
    fun combinedLevelCount(): Int =
        QuizAnswerScopeStore.levels().size + customLevels().size

    fun tagName(collectionId: String?): String =
        find(collectionId)?.name ?: ""

    fun tagSignature(levels: List<QuizCollectionLevel>): String =
        levels.joinToString(";") { "${it.id}:${it.collectionId}" }

    fun normalizedName(name: String): String =
        QuizCollection.normalizeName(name)?.lowercase(Locale.ROOT) ?: ""

    private const val MAX_CUSTOM_LEVELS = 6
}
