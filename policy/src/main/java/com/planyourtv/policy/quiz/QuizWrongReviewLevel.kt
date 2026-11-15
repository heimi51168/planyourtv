package com.planyourtv.policy.quiz

import org.json.JSONObject
import java.util.UUID

/**
 * The optional single level that re-asks the child's own wrong questions.
 * Unlike [QuizLevel] it has no curriculum scope, and unlike
 * [QuizCollectionLevel] it is not tied to an imported tag: its pool is every
 * local row whose last answer was wrong. A single slot is enough because one
 * wrong-review entry always covers the whole local bank.
 */
data class QuizWrongReviewLevel(
    val id: String,
    val name: String,
) {
    fun encode(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)

    companion object {
        const val ID_PREFIX = "wrong-"

        private val ID_PATTERN = Regex("^wrong-[A-Za-z0-9_-]{1,40}$")

        fun newId(): String =
            ID_PREFIX + UUID.randomUUID().toString().replace("-", "").take(16)

        /** Empty names are allowed; the TV falls back to the localized title. */
        fun normalizeName(raw: String): String? {
            val name = raw.trim()
            if (name.codePointCount(0, name.length) > QuizLevel.MAX_NAME_CODE_POINTS) return null
            return name
        }

        fun create(name: String = "", id: String? = null): QuizWrongReviewLevel? {
            val cleanName = normalizeName(name) ?: return null
            val cleanId = id?.trim()?.takeIf { it.isNotEmpty() } ?: newId()
            if (!ID_PATTERN.matches(cleanId)) return null
            return QuizWrongReviewLevel(cleanId, cleanName)
        }

        /**
         * Strict parse of one stored/web level. A missing id is assigned a new
         * one; a present but malformed id is rejected instead of remapped, so
         * a stale web page cannot address a different level.
         */
        fun parse(json: JSONObject?): QuizWrongReviewLevel? {
            if (json == null) return null
            val name = normalizeName(json.optString("name", "")) ?: return null
            val rawId = if (json.has("id") && !json.isNull("id")) {
                json.optString("id", "").trim()
            } else {
                ""
            }
            val id = rawId.ifEmpty { newId() }
            if (!ID_PATTERN.matches(id)) return null
            return QuizWrongReviewLevel(id, name)
        }

        /** Reads the durable single-value record; malformed data yields null. */
        fun parseEncoded(raw: String?): QuizWrongReviewLevel? {
            if (raw.isNullOrBlank()) return null
            return parse(runCatching { JSONObject(raw) }.getOrNull())
        }
    }
}
