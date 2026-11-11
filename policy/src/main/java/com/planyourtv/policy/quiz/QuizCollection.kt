package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One parent-created tag over manually imported questions. The web page calls
 * it a "标签"; storage and APIs keep the collection name so the model can grow
 * into multi-tag membership later without renaming every call site.
 *
 * Collections are local-only: they never enter the curriculum catalogue, the
 * cloud scope report, or the AI generation range.
 */
data class QuizCollection(
    val id: String,
    val name: String,
    val createdAt: Long,
) {
    fun encode(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("createdAt", createdAt)

    companion object {
        const val MAX_COLLECTIONS = 20
        const val MAX_NAME_CODE_POINTS = 20

        private val ID_PATTERN = Regex("^col-[A-Za-z0-9_-]{1,40}$")

        fun newId(): String =
            "col-" + UUID.randomUUID().toString().replace("-", "").take(16)

        /** Trims a parent-visible tag name; null when empty or too long. */
        fun normalizeName(raw: String): String? {
            val name = raw.trim()
            val length = name.codePointCount(0, name.length)
            if (length !in 1..MAX_NAME_CODE_POINTS) return null
            return name
        }

        fun create(
            name: String,
            createdAt: Long = System.currentTimeMillis(),
            id: String? = null,
        ): QuizCollection? {
            val cleanName = normalizeName(name) ?: return null
            val cleanId = id?.trim()?.takeIf { it.isNotEmpty() } ?: newId()
            if (!ID_PATTERN.matches(cleanId) || createdAt <= 0) return null
            return QuizCollection(cleanId, cleanName, createdAt)
        }

        /** Strict parse of one stored tag; a malformed row rejects the list. */
        fun parse(json: JSONObject?): QuizCollection? {
            if (json == null) return null
            val id = json.optString("id", "").trim()
            if (!ID_PATTERN.matches(id)) return null
            val name = normalizeName(json.optString("name", "")) ?: return null
            val createdAt = numberValue(json.opt("createdAt")) ?: return null
            if (createdAt <= 0) return null
            return QuizCollection(id, name, createdAt)
        }

        /** Parses 0..[MAX_COLLECTIONS] tags and rejects duplicate ids/names. */
        fun parseList(array: JSONArray?): List<QuizCollection>? {
            if (array == null) return null
            if (array.length() > MAX_COLLECTIONS) return null
            val collections = ArrayList<QuizCollection>(array.length())
            val ids = HashSet<String>(array.length())
            val names = HashSet<String>(array.length())
            for (index in 0 until array.length()) {
                val collection = parse(array.optJSONObject(index)) ?: return null
                if (!ids.add(collection.id)) return null
                if (!names.add(collection.name.lowercase())) return null
                collections += collection
            }
            return collections
        }

        fun encodeList(collections: List<QuizCollection>): JSONArray {
            val array = JSONArray()
            collections.forEach { array.put(it.encode()) }
            return array
        }

        private fun numberValue(value: Any?): Long? {
            if (value !is Number) return null
            val double = value.toDouble()
            if (double != value.toLong().toDouble()) return null
            return value.toLong()
        }
    }
}
