package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One parent-designed level that draws only from a single custom tag. It is
 * deliberately not a [QuizLevel]: there is no grade/semester/textbook scope and
 * no difficulty filter. Course levels and collection levels are stored under
 * separate keys so a cloud scope refresh can never erase a tag level.
 */
data class QuizCollectionLevel(
    val id: String,
    val name: String,
    val collectionId: String,
) {
    fun encode(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("collectionId", collectionId)

    companion object {
        const val MAX_NAME_CODE_POINTS = 10

        private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,40}$")
        private val COLLECTION_ID_PATTERN = Regex("^col-[A-Za-z0-9_-]{1,40}$")

        fun newId(): String =
            "lvl-" + UUID.randomUUID().toString().replace("-", "").take(16)

        /** Empty names are allowed; the TV falls back to the tag name. */
        fun normalizeName(raw: String): String? {
            val name = raw.trim()
            if (name.codePointCount(0, name.length) > MAX_NAME_CODE_POINTS) return null
            return name
        }

        fun create(
            collectionId: String,
            name: String = "",
            id: String? = null,
        ): QuizCollectionLevel? {
            val cleanName = normalizeName(name) ?: return null
            val cleanId = id?.trim()?.takeIf { it.isNotEmpty() } ?: newId()
            if (!ID_PATTERN.matches(cleanId)) return null
            if (!COLLECTION_ID_PATTERN.matches(collectionId.trim())) return null
            return QuizCollectionLevel(cleanId, cleanName, collectionId.trim())
        }

        /**
         * Strict parse of one stored/web level. A missing id is assigned a new
         * one; a present but malformed id is rejected instead of remapped.
         * Whether the tag exists is checked by the store, not here.
         */
        fun parse(json: JSONObject?): QuizCollectionLevel? {
            if (json == null) return null
            val collectionId = json.optString("collectionId", "").trim()
            if (!COLLECTION_ID_PATTERN.matches(collectionId)) return null
            val name = normalizeName(json.optString("name", "")) ?: return null
            val rawId = if (json.has("id") && !json.isNull("id")) {
                json.optString("id", "").trim()
            } else {
                ""
            }
            val id = rawId.ifEmpty { newId() }
            if (!ID_PATTERN.matches(id)) return null
            return QuizCollectionLevel(id, name, collectionId)
        }

        /**
         * Parses 0..[max] levels. Duplicate ids and duplicate tag references
         * are rejected: two levels over the same tag would be indistinguishable
         * when counts change, mirroring the duplicate-scope rule on QuizLevel.
         */
        fun parseList(array: JSONArray?, max: Int = QuizLevel.MAX_LEVELS): List<QuizCollectionLevel>? {
            if (array == null) return null
            if (max < 0 || array.length() > max) return null
            val levels = ArrayList<QuizCollectionLevel>(array.length())
            val ids = HashSet<String>(array.length())
            val collectionIds = HashSet<String>(array.length())
            for (index in 0 until array.length()) {
                val level = parse(array.optJSONObject(index)) ?: return null
                if (!ids.add(level.id)) return null
                if (!collectionIds.add(level.collectionId)) return null
                levels += level
            }
            return levels
        }

        fun encodeList(levels: List<QuizCollectionLevel>): JSONArray {
            val array = JSONArray()
            levels.forEach { array.put(it.encode()) }
            return array
        }
    }
}
