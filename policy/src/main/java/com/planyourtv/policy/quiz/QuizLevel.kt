package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One parent-designed quiz level: a stable id, an optional short name for the
 * TV, and the answer scope it filters the bank by. The web page owns the list
 * order; the TV only shows the levels the parent saved.
 */
data class QuizLevel(
    val id: String,
    val name: String,
    val scope: QuizAnswerScope,
) {
    fun encode(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("scope", scope.encode())

    companion object {
        const val MAX_LEVELS = 6
        const val MAX_NAME_CODE_POINTS = 10

        private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,40}$")

        fun newId(): String =
            "lvl-" + UUID.randomUUID().toString().replace("-", "").take(16)

        /** Trims a parent-visible level name; returns null when it is too long. */
        fun normalizeName(raw: String): String? {
            val name = raw.trim()
            if (name.codePointCount(0, name.length) > MAX_NAME_CODE_POINTS) return null
            return name
        }

        fun create(
            scope: QuizAnswerScope,
            name: String = "",
            id: String? = null,
        ): QuizLevel? {
            val cleanName = normalizeName(name) ?: return null
            val cleanId = id?.trim()?.takeIf { it.isNotEmpty() } ?: newId()
            if (!ID_PATTERN.matches(cleanId)) return null
            return QuizLevel(cleanId, cleanName, scope)
        }

        /**
         * Strict parse of one stored/web level. A missing id is assigned a new
         * one; an id that is present but malformed is rejected instead of
         * silently remapped, so the web cannot address the wrong level.
         */
        fun parse(json: JSONObject?): QuizLevel? {
            if (json == null) return null
            val scope = QuizAnswerScope.parse(json.optJSONObject("scope")) ?: return null
            val name = normalizeName(json.optString("name", "")) ?: return null
            val rawId = if (json.has("id") && !json.isNull("id")) {
                json.optString("id", "").trim()
            } else {
                ""
            }
            val id = rawId.ifEmpty { newId() }
            if (!ID_PATTERN.matches(id)) return null
            return QuizLevel(id, name, scope)
        }

        /**
         * Parses 0..[MAX_LEVELS] levels. Duplicate scopes are rejected: two
         * identical levels would be indistinguishable on the TV. Duplicate ids
         * are rejected as well so editing always addresses one level.
         */
        fun parseList(array: JSONArray?): List<QuizLevel>? {
            if (array == null) return null
            if (array.length() > MAX_LEVELS) return null
            val levels = ArrayList<QuizLevel>(array.length())
            val scopes = HashSet<String>(array.length())
            val ids = HashSet<String>(array.length())
            for (index in 0 until array.length()) {
                val level = parse(array.optJSONObject(index)) ?: return null
                if (!scopes.add(level.scope.signature())) return null
                if (!ids.add(level.id)) return null
                levels += level
            }
            return levels
        }

        fun encodeList(levels: List<QuizLevel>): JSONArray {
            val array = JSONArray()
            levels.forEach { array.put(it.encode()) }
            return array
        }
    }
}
