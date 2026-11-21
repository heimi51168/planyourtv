package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A level is an ordered package of question sources rather than one source
 * type. At round start the TV visits the sources in order, starting at a random
 * offset, and draws one question from each until the round is full.
 *
 * The package shape is local today. Keeping it in `policy` gives the future
 * cloud document one JSON shape to adopt, but cloud still reads its own levels.
 */
data class QuizLevelPackage(
    val id: String,
    val name: String,
    val sources: List<QuizPackageSource>,
    /**
     * A paused level stays configured and keeps its questions, but the child's
     * challenge hides it and an explicit start is refused. Missing on old
     * records means enabled, so an upgrade never pauses a parent's levels.
     */
    val enabled: Boolean = true,
) {
    fun encode(): JSONObject {
        val array = JSONArray()
        sources.forEach { array.put(encodeSource(it)) }
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("sources", array)
            .put("enabled", enabled)
    }

    companion object {
        /** Existing course + custom + wrong total was capped at 20 by the web. */
        const val MAX_LEVELS = QuizLevel.MAX_LEVELS
        const val MAX_SOURCES = 8
        const val MAX_NAME_CODE_POINTS = QuizLevel.MAX_NAME_CODE_POINTS

        private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,40}$")
        private val COLLECTION_ID_PATTERN = Regex("^col-[A-Za-z0-9_-]{1,40}$")

        fun newId(): String = QuizLevel.newId()

        fun normalizeName(raw: String): String? = QuizLevel.normalizeName(raw)

        fun create(
            sources: List<QuizPackageSource>,
            name: String = "",
            id: String? = null,
            enabled: Boolean = true,
        ): QuizLevelPackage? {
            val cleanName = normalizeName(name) ?: return null
            val cleanId = id?.trim()?.takeIf { it.isNotEmpty() } ?: newId()
            if (!ID_PATTERN.matches(cleanId)) return null
            if (sourceIssue(sources) != null) return null
            return QuizLevelPackage(cleanId, cleanName, sources, enabled)
        }

        /**
         * Strict parse of one stored/web package. A missing id is assigned a new
         * one; a present but malformed id is rejected instead of remapped.
         * Package ordering is the caller's list order.
         */
        fun parse(json: JSONObject?): QuizLevelPackage? {
            if (json == null) return null
            val name = normalizeName(json.optString("name", "")) ?: return null
            val rawId = if (json.has("id") && !json.isNull("id")) {
                json.optString("id", "").trim()
            } else {
                ""
            }
            val id = rawId.ifEmpty { newId() }
            if (!ID_PATTERN.matches(id)) return null
            val enabled = when {
                !json.has("enabled") || json.isNull("enabled") -> true
                json.opt("enabled") is Boolean -> json.getBoolean("enabled")
                else -> return null
            }
            val array = json.optJSONArray("sources") ?: return null
            if (array.length() > MAX_SOURCES) return null
            val sources = ArrayList<QuizPackageSource>(array.length())
            for (index in 0 until array.length()) {
                sources += parseSource(array.optJSONObject(index)) ?: return null
            }
            if (sourceIssue(sources) != null) return null
            return QuizLevelPackage(id, name, sources, enabled)
        }

        /**
         * Parses 0..[max] packages and rejects duplicate package ids. [max]
         * exists so migration can preserve a legacy list that is already over
         * the new limit instead of silently dropping a parent's levels.
         */
        fun parseList(
            array: JSONArray?,
            max: Int = MAX_LEVELS,
        ): List<QuizLevelPackage>? {
            if (array == null) return null
            if (max < 0 || array.length() > max) return null
            val levels = ArrayList<QuizLevelPackage>(array.length())
            val ids = HashSet<String>(array.length())
            for (index in 0 until array.length()) {
                val level = parse(array.optJSONObject(index)) ?: return null
                if (!ids.add(level.id)) return null
                levels += level
            }
            return levels
        }

        fun encodeList(levels: List<QuizLevelPackage>): JSONArray {
            val array = JSONArray()
            levels.forEach { array.put(it.encode()) }
            return array
        }

        /** First invalid source rule, or null when [sources] is saveable. */
        fun sourceIssue(sources: List<QuizPackageSource>): SourceIssue? {
            if (sources.isEmpty()) return SourceIssue.EMPTY
            if (sources.size > MAX_SOURCES) return SourceIssue.TOO_MANY
            val courses = HashSet<String>(sources.size)
            val collections = HashSet<String>(sources.size)
            val presets = HashSet<QuizPresetBank>(sources.size)
            var wrong = false
            sources.forEach { source ->
                when (source) {
                    is QuizPackageSource.Course -> {
                        if (!courses.add(courseKey(source))) return SourceIssue.DUPLICATE_COURSE
                    }
                    is QuizPackageSource.Collection -> {
                        if (!COLLECTION_ID_PATTERN.matches(source.collectionId)) {
                            return SourceIssue.INVALID_COLLECTION
                        }
                        if (!collections.add(source.collectionId)) {
                            return SourceIssue.DUPLICATE_COLLECTION
                        }
                    }
                    is QuizPackageSource.Preset -> {
                        if (!presets.add(source.bank)) return SourceIssue.DUPLICATE_PRESET
                    }
                    QuizPackageSource.WrongReview -> {
                        if (wrong) return SourceIssue.DUPLICATE_WRONG
                        wrong = true
                    }
                }
            }
            return null
        }

        fun courseKey(source: QuizPackageSource.Course): String =
            source.scope.signature() + "|" + source.answerKind.wire + "|" + source.origin.wire

        private fun encodeSource(source: QuizPackageSource): JSONObject = when (source) {
            is QuizPackageSource.Course -> JSONObject()
                .put("type", "course")
                .put("scope", source.scope.encode())
                .put("answerKind", source.answerKind.wire)
                // Old packages have no origin and keep the combined preset + AI
                // behavior; only explicit new sources need the extra field.
                .apply {
                    if (source.origin != CourseOrigin.ALL) put("origin", source.origin.wire)
                }

            is QuizPackageSource.Collection -> JSONObject()
                .put("type", "collection")
                .put("collectionId", source.collectionId)

            is QuizPackageSource.Preset -> JSONObject()
                .put("type", "preset")
                .put("bankId", source.bank.wire)

            QuizPackageSource.WrongReview -> JSONObject().put("type", "wrong")
        }

        /** Parses one source row so a caller can name the exact invalid row. */
        fun parseSource(json: JSONObject?): QuizPackageSource? {
            if (json == null) return null
            return when (json.optString("type", "").trim().lowercase()) {
                "course" -> {
                    val scope = QuizAnswerScope.parse(json.optJSONObject("scope")) ?: return null
                    val kind = if (json.has("answerKind") && !json.isNull("answerKind")) {
                        QuizTypeFilter.fromWire(json.optString("answerKind", "")) ?: return null
                    } else {
                        QuizTypeFilter.ANY
                    }
                    val origin = if (json.has("origin") && !json.isNull("origin")) {
                        CourseOrigin.fromWire(json.optString("origin", "")) ?: return null
                    } else {
                        CourseOrigin.ALL
                    }
                    QuizPackageSource.Course(scope, kind, origin)
                }
                "collection" -> {
                    val collectionId = json.optString("collectionId", "").trim()
                    if (!COLLECTION_ID_PATTERN.matches(collectionId)) return null
                    QuizPackageSource.Collection(collectionId)
                }
                "preset" -> {
                    val bank = QuizPresetBank.fromWire(json.optString("bankId", ""))
                        ?: return null
                    QuizPackageSource.Preset(bank)
                }
                "wrong" -> QuizPackageSource.WrongReview
                else -> null
            }
        }
    }
}

/** A source-level question-type filter; it never changes how an item is scored. */
enum class QuizTypeFilter(val wire: String) {
    ANY("any"),
    SINGLE("single"),
    MULTIPLE("multiple"),
    ;

    companion object {
        fun fromWire(value: String): QuizTypeFilter? =
            entries.firstOrNull { it.wire == value.trim().lowercase() }
    }
}

/**
 * Which rows a course source may draw. [ALL] is the pre-split behavior and is
 * only produced by packages saved before the origin field existed; the page
 * never offers it as a new source.
 */
enum class CourseOrigin(val wire: String) {
    ALL("all"),
    PRESET("preset"),
    AI("ai"),
    ;

    companion object {
        fun fromWire(value: String): CourseOrigin? =
            entries.firstOrNull { it.wire == value.trim().lowercase() }
    }
}

/**
 * A bundled, non-curriculum question bank. The wire id is stable because it
 * also appears in the seed rows' `source_ref`; renaming it would strand the
 * already-imported rows.
 */
enum class QuizPresetBank(val wire: String) {
    PARENT_LIFE("parent-life"),
    ;

    companion object {
        fun fromWire(value: String): QuizPresetBank? =
            entries.firstOrNull { it.wire == value.trim().lowercase() }
    }
}

sealed interface QuizPackageSource {
    data class Course(
        val scope: QuizAnswerScope,
        val answerKind: QuizTypeFilter = QuizTypeFilter.ANY,
        val origin: CourseOrigin = CourseOrigin.ALL,
    ) : QuizPackageSource

    data class Collection(val collectionId: String) : QuizPackageSource

    /** One of the banks shipped inside the APK, independent of the curriculum. */
    data class Preset(val bank: QuizPresetBank) : QuizPackageSource

    data object WrongReview : QuizPackageSource
}

/** Why a source list cannot be saved. Kept stable for the web error surface. */
enum class SourceIssue {
    EMPTY,
    TOO_MANY,
    DUPLICATE_COURSE,
    DUPLICATE_COLLECTION,
    DUPLICATE_PRESET,
    DUPLICATE_WRONG,
    INVALID_COLLECTION,
}
