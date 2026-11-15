package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Canonical QCD content hashes.
 *
 * Object keys are sorted recursively, arrays keep their order, and strings use
 * [JSONObject.quote] escaping. `etag` excludes the volatile fields that are not
 * content: its own value, revision, levelsEtag, updatedAt and updatedBy.
 */
object QuizConfigDocumentEtag {
    fun etag(document: QuizConfigDocument): String {
        val root = QuizConfigDocumentCodec.encodeDocument(document)
        ETAG_EXCLUDED_FIELDS.forEach { root.remove(it) }
        return hash(root)
    }

    fun levelsEtag(levels: List<QuizLevelDoc>): String {
        if (levels.isEmpty()) return ""
        val array = JSONArray()
        levels.forEach { array.put(QuizConfigDocumentCodec.encodeLevel(it)) }
        return hash(array)
    }

    internal fun canonicalize(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            val keys = value.keys().asSequence().toList().sorted()
            keys.joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                JSONObject.quote(key) + ":" + canonicalize(value.opt(key))
            }
        }
        is JSONArray -> (0 until value.length()).joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
        ) { index -> canonicalize(value.opt(index)) }
        is String -> JSONObject.quote(value)
        is Boolean -> value.toString()
        is Number -> canonicalNumber(value)
        else -> JSONObject.quote(value.toString())
    }

    private fun hash(value: Any?): String = hash(canonicalize(value))

    private fun hash(canonical: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun canonicalNumber(value: Number): String = when (value) {
        is Double -> {
            if (value.isNaN() || value.isInfinite()) {
                throw IllegalArgumentException("non-finite JSON number")
            }
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        }
        is Float -> {
            if (value.isNaN() || value.isInfinite()) {
                throw IllegalArgumentException("non-finite JSON number")
            }
            if (value.toDouble() == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                value.toString()
            }
        }
        else -> value.toString()
    }

    private val ETAG_EXCLUDED_FIELDS = setOf(
        "etag", "revision", "levelsEtag", "updatedAt", "updatedBy",
    )
}
