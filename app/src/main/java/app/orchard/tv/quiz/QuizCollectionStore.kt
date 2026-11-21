package app.orchard.tv.quiz

import android.util.Log
import app.orchard.tv.policy.FamilyHub
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizCollection
import com.planyourtv.policy.quiz.QuizCollectionLevel
import org.json.JSONArray
import java.util.Locale

/**
 * Local tags and the levels that draw from them. Both live under their own
 * TvStore keys so a cloud scope refresh, which only rewrites
 * `quiz.answerScopes`, can never erase a parent's imported material.
 *
 * V41 makes tags private to one member. The default member keeps the legacy
 * key names so an upgraded TV reads its old tags and a downgraded APK still
 * sees them; every later member gets a `#<userId>` suffix through
 * [QuizSubjectScope]. There is no family-wide tag namespace any more.
 */
object QuizCollectionStore {
    private const val TAG = "QuizCollectionStore"
    private const val KEY_COLLECTIONS = "quiz.collections"
    private const val KEY_CUSTOM_LEVELS = "quiz.customLevels"

    private val lock = Any()

    /** The member's private tags; a null id means the member the TV runs now. */
    fun collections(subjectUserId: String? = null): List<QuizCollection> = synchronized(lock) {
        readCollections(keyFor(KEY_COLLECTIONS, subjectUserId))
    }

    fun hasCollections(subjectUserId: String? = null): Boolean =
        TvStore.getString(keyFor(KEY_COLLECTIONS, subjectUserId)) != null

    fun find(id: String?, subjectUserId: String? = null): QuizCollection? {
        if (id.isNullOrBlank()) return null
        return collections(subjectUserId).firstOrNull { it.id == id }
    }

    fun nameExists(
        name: String,
        excludingId: String? = null,
        subjectUserId: String? = null,
    ): Boolean {
        val normalized = QuizCollection.normalizeName(name) ?: return false
        return collections(subjectUserId).any {
            it.id != excludingId && it.name.equals(normalized, ignoreCase = true)
        }
    }

    /** Adds one tag at the end of the member's list; null on duplicate, limit, or IO failure. */
    fun addCollection(name: String, subjectUserId: String? = null): QuizCollection? =
        synchronized(lock) {
            val key = keyFor(KEY_COLLECTIONS, subjectUserId)
            val existing = readCollections(key)
            val collection = QuizCollection.create(name) ?: return null
            if (existing.size >= QuizCollection.MAX_COLLECTIONS) return null
            if (existing.any { it.name.equals(collection.name, ignoreCase = true) }) return null
            if (!saveCollectionsLocked(key, existing + collection)) return null
            collection
        }

    /** Renames one of the member's tags; null when absent, duplicate, or IO failed. */
    fun renameCollection(
        id: String,
        name: String,
        subjectUserId: String? = null,
    ): QuizCollection? = synchronized(lock) {
        val key = keyFor(KEY_COLLECTIONS, subjectUserId)
        val existing = readCollections(key)
        val index = existing.indexOfFirst { it.id == id }
        if (index < 0) return null
        val cleanName = QuizCollection.normalizeName(name) ?: return null
        if (existing.any { it.id != id && it.name.equals(cleanName, ignoreCase = true) }) return null
        val updated = existing.toMutableList().also {
            it[index] = it[index].copy(name = cleanName)
        }
        if (!saveCollectionsLocked(key, updated)) return null
        updated[index]
    }

    fun saveCollections(
        collections: List<QuizCollection>,
        subjectUserId: String? = null,
    ): Boolean = synchronized(lock) {
        saveCollectionsLocked(keyFor(KEY_COLLECTIONS, subjectUserId), collections)
    }

    /**
     * Best-effort rollback for a tag created for an import that then failed.
     * A tag still referenced by one of this member's custom levels is kept so
     * no level is orphaned.
     */
    fun removeCollection(id: String, subjectUserId: String? = null): Boolean = synchronized(lock) {
        val collectionKey = keyFor(KEY_COLLECTIONS, subjectUserId)
        val levelKey = keyFor(KEY_CUSTOM_LEVELS, subjectUserId)
        val existing = readCollections(collectionKey)
        if (existing.none { it.id == id }) return true
        if (readCustomLevels(levelKey, existing).any { it.collectionId == id }) {
            return false
        }
        saveCollectionsLocked(collectionKey, existing.filterNot { it.id == id })
    }

    private fun readCollections(key: String): List<QuizCollection> {
        val raw = TvStore.getString(key) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull()
        val parsed = QuizCollection.parseList(array)
        if (parsed == null) Log.e(TAG, "stored tags are corrupt; ignoring them")
        return parsed.orEmpty()
    }

    private fun saveCollectionsLocked(key: String, collections: List<QuizCollection>): Boolean {
        if (QuizCollection.parseList(QuizCollection.encodeList(collections)) == null) return false
        return TvStore.putStringDurable(key, QuizCollection.encodeList(collections).toString())
    }

    /**
     * Custom levels whose tag still exists. A level pointing at a manually
     * removed tag is hidden, not silently rewritten: the row stays on disk so a
     * repaired collection list can recover it, matching the "never drop parent
     * data on read" rule used by answer scopes.
     */
    fun customLevels(subjectUserId: String? = null): List<QuizCollectionLevel> =
        synchronized(lock) {
            readCustomLevels(
                keyFor(KEY_CUSTOM_LEVELS, subjectUserId),
                readCollections(keyFor(KEY_COLLECTIONS, subjectUserId)),
            )
        }

    private fun readCustomLevels(
        key: String,
        collections: List<QuizCollection>,
    ): List<QuizCollectionLevel> {
        val raw = TvStore.getString(key) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull()
        val parsed = QuizCollectionLevel.parseList(array)
        if (parsed == null) {
            Log.e(TAG, "stored custom levels are corrupt; ignoring them")
            return emptyList()
        }
        val ids = collections.mapTo(HashSet()) { it.id }
        val valid = parsed.filter { it.collectionId in ids }
        (parsed.size - valid.size).takeIf { it > 0 }?.let {
            Log.w(TAG, "$it custom level(s) reference a missing tag")
        }
        return valid
    }

    fun hasCustomLevels(subjectUserId: String? = null): Boolean =
        TvStore.getString(keyFor(KEY_CUSTOM_LEVELS, subjectUserId)) != null

    fun customLevel(
        id: String?,
        subjectUserId: String? = null,
    ): QuizCollectionLevel? {
        if (id.isNullOrBlank()) return null
        return customLevels(subjectUserId).firstOrNull { it.id == id }
    }

    fun saveCustomLevels(
        levels: List<QuizCollectionLevel>,
        subjectUserId: String? = null,
    ): Boolean = synchronized(lock) {
        val parsed = QuizCollectionLevel.parseList(
            QuizCollectionLevel.encodeList(levels),
            MAX_CUSTOM_LEVELS,
        ) ?: return false
        val collections = readCollections(keyFor(KEY_COLLECTIONS, subjectUserId))
            .mapTo(HashSet()) { it.id }
        if (parsed.any { it.collectionId !in collections }) return false
        TvStore.putStringDurable(
            keyFor(KEY_CUSTOM_LEVELS, subjectUserId),
            QuizCollectionLevel.encodeList(parsed).toString(),
        )
    }

    /** Stable order for the TV: course levels first, then the member's tags in saved order. */
    fun combinedLevelCount(subjectUserId: String? = null): Int =
        QuizAnswerScopeStore.levels().size + customLevels(subjectUserId).size

    fun tagName(collectionId: String?, subjectUserId: String? = null): String =
        find(collectionId, subjectUserId)?.name ?: ""

    fun tagSignature(levels: List<QuizCollectionLevel>): String =
        levels.joinToString(";") { "${it.id}:${it.collectionId}" }

    fun normalizedName(name: String): String =
        QuizCollection.normalizeName(name)?.lowercase(Locale.ROOT) ?: ""

    /**
     * Resolves a member's private key. An explicit id works before the active
     * pointer moves (new-member provisioning); a null id follows the TV.
     */
    private fun keyFor(base: String, subjectUserId: String?): String {
        val user = subjectUserId?.trim()?.takeIf { it.isNotEmpty() }
            ?: return QuizSubjectScope.key(base)
        val family = FamilyHub.state.value
        if (!family.ready) return base
        return QuizSubjectScope.keyFor(family, user, base)
    }

    private const val MAX_CUSTOM_LEVELS = 6
}
