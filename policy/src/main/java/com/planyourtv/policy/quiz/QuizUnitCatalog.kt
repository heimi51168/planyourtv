package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Optional textbook unit names published by the national lesson platform.
 *
 * The catalogue is display and prompt metadata only. [QuizAnswerScope] keeps
 * `unitNo` as the canonical key, so the bank, cache, cloud scope and signatures
 * do not change when this file is missing or older than the curriculum. When a
 * book is not covered here, callers fall back to the stage unit limit and the
 * existing "第N单元" label.
 */
object QuizUnitCatalog {
    private const val RESOURCE = "quiz/unit_catalog.json"
    private const val SUPPORTED_SCHEMA = 1

    data class Unit(
        val no: Int,
        val title: String,
        val kind: String,
        val lessons: List<String>,
    ) {
        val isUnit: Boolean get() = kind == KIND_UNIT

        companion object {
            const val KIND_UNIT = "UNIT"
        }
    }

    data class Book(
        val grade: Int,
        val semesters: Set<Int>,
        val subject: String,
        val textbook: String,
        val textbookName: String,
        val volume: String,
        val editionType: String,
        val maxUnitNo: Int,
        val units: List<Unit>,
    ) {
        fun unit(no: Int): Unit? = units.firstOrNull { it.no == no }

        fun unitTitle(no: Int): String? = unit(no)?.title?.takeIf { it.isNotEmpty() }
    }

    data class Catalog(
        val schemaVersion: Int,
        val catalogVersion: String,
        val fetchedAt: String,
        val source: String,
        val books: List<Book>,
    ) {
        /**
         * Exact book lookup. Wildcard scopes have no single textbook and must
         * fall back to the stage limit instead of guessing an arbitrary book.
         */
        fun book(grade: Int, semester: Int, subject: String, textbook: String): Book? {
            val normalizedSubject = subject.trim().uppercase(Locale.ROOT)
            val normalizedTextbook = textbook.trim().uppercase(Locale.ROOT)
            val candidates = books.filter { book ->
                book.grade == grade &&
                    semester in book.semesters &&
                    book.subject == normalizedSubject &&
                    book.textbook == normalizedTextbook
            }
            // A full-volume book can overlap the single-term book. Prefer the
            // term-specific edition so titles and limits match what the
            // semester picker implies; fall back to the combined volume.
            val preferredVolume = if (semester == 1) "上册" else "下册"
            return candidates.firstOrNull { it.volume == preferredVolume }
                ?: candidates.firstOrNull()
        }

        fun unit(
            grade: Int,
            semester: Int,
            subject: String,
            textbook: String,
            unitNo: Int,
        ): Unit? = book(grade, semester, subject, textbook)?.unit(unitNo)

        fun unitTitle(
            grade: Int,
            semester: Int,
            subject: String,
            textbook: String,
            unitNo: Int,
        ): String? = unit(grade, semester, subject, textbook, unitNo)?.title

        /** Unit limit for an exact book, or for a subject / grade wildcard. */
        fun unitMax(grade: Int, semester: Int, subject: String, textbook: String): Int? {
            book(grade, semester, subject, textbook)?.let { return it.maxUnitNo }
            val normalizedSubject = subject.trim().uppercase(Locale.ROOT)
            val normalizedTextbook = textbook.trim().uppercase(Locale.ROOT)
            return books.asSequence()
                .filter { book ->
                    book.grade == grade &&
                        semester in book.semesters &&
                        (normalizedSubject == QuizAnswerScope.SUBJECT_ALL ||
                            book.subject == normalizedSubject) &&
                        (normalizedTextbook == QuizAnswerScope.TEXTBOOK_ALL ||
                            book.textbook == normalizedTextbook)
                }
                .maxOfOrNull { it.maxUnitNo }
                ?.takeIf { it > 0 }
        }

        fun unitMaxForGrade(grade: Int): Int? =
            books.asSequence()
                .filter { it.grade == grade }
                .maxOfOrNull { it.maxUnitNo }
                ?.takeIf { it > 0 }

        fun maxUnit(): Int = books.maxOfOrNull { it.maxUnitNo } ?: 0

        fun descriptor(): JSONObject = JSONObject()
            .put("schemaVersion", schemaVersion)
            .put("catalogVersion", catalogVersion)
            .put("fetchedAt", fetchedAt)
            .put("source", source)
            .put("books", JSONArray().apply {
                books.forEach { book ->
                    put(
                        JSONObject()
                            .put("grade", book.grade)
                            .put("semesters", JSONArray().apply {
                                book.semesters.sorted().forEach { put(it) }
                            })
                            .put("subject", book.subject)
                            .put("textbook", book.textbook)
                            .put("textbookName", book.textbookName)
                            .put("volume", book.volume)
                            .put("editionType", book.editionType)
                            .put("maxUnitNo", book.maxUnitNo)
                            .put("units", JSONArray().apply {
                                book.units.forEach { unit ->
                                    put(
                                        JSONObject()
                                            .put("no", unit.no)
                                            .put("title", unit.title)
                                            .put("kind", unit.kind)
                                            .put("lessons", JSONArray().apply {
                                                unit.lessons.forEach { put(it) }
                                            }),
                                    )
                                }
                            }),
                    )
                }
            })
    }

    private val bundled: Catalog? by lazy { loadBundled() }

    @Volatile
    private var testOverride: Catalog? = null

    @Volatile
    private var overrideInstalled = false

    /** Returns the installed test catalogue, the bundled file, or null. */
    fun current(): Catalog? = if (overrideInstalled) testOverride else bundled

    internal fun installForTest(catalog: Catalog?) {
        testOverride = catalog
        overrideInstalled = true
    }

    internal fun clearTestOverride() {
        testOverride = null
        overrideInstalled = false
    }

    internal fun parse(json: String): Catalog? =
        runCatching { parseOrThrow(JSONObject(json)) }.getOrNull()

    private fun loadBundled(): Catalog? {
        val loader = QuizUnitCatalog::class.java.classLoader
            ?: Thread.currentThread().contextClassLoader
        val stream = loader?.getResourceAsStream(RESOURCE)
        if (stream == null) {
            System.err.println("[QuizUnitCatalog] missing resource $RESOURCE")
            return null
        }
        return runCatching {
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                parseOrThrow(JSONObject(reader.readText()))
            }
        }.onFailure {
            System.err.println("[QuizUnitCatalog] invalid resource $RESOURCE: ${it.message}")
        }.getOrNull()
    }

    private fun parseOrThrow(root: JSONObject): Catalog {
        val schemaVersion = root.optInt("schemaVersion", 0)
        require(schemaVersion == SUPPORTED_SCHEMA) { "unsupported schemaVersion $schemaVersion" }
        val booksJson = root.optJSONArray("books") ?: error("books is missing")
        val books = (0 until booksJson.length()).mapNotNull { index ->
            parseBook(booksJson.optJSONObject(index), index)
        }
        require(books.isNotEmpty()) { "books is empty" }
        require(books.map { "${it.grade}|${it.subject}|${it.textbook}|${it.volume}" }
            .toSet().size == books.size) { "duplicate book" }
        return Catalog(
            schemaVersion = schemaVersion,
            catalogVersion = root.optString("catalogVersion", ""),
            fetchedAt = root.optString("fetchedAt", ""),
            source = root.optString("source", ""),
            books = books,
        )
    }

    private fun parseBook(json: JSONObject?, index: Int): Book? {
        if (json == null) return null
        val grade = json.optInt("grade", 0)
        if (grade !in 1..9) return null
        val semestersJson = json.optJSONArray("semesters") ?: return null
        val semesters = (0 until semestersJson.length())
            .map { semestersJson.optInt(it, 0) }
            .filter { it in 1..2 }
            .toSet()
        if (semesters.isEmpty()) return null
        val subject = json.optString("subject", "").trim().uppercase(Locale.ROOT)
        val textbook = json.optString("textbook", "").trim().uppercase(Locale.ROOT)
        if (subject.isEmpty() || textbook.isEmpty()) return null
        val unitsJson = json.optJSONArray("units") ?: return null
        val units = (0 until unitsJson.length()).mapNotNull { unitIndex ->
            parseUnit(unitsJson.optJSONObject(unitIndex))
        }
        if (units.isEmpty()) return null
        val computedMax = units.filter { it.isUnit }.maxOfOrNull { it.no } ?: 0
        val maxUnitNo = json.optInt("maxUnitNo", computedMax)
        if (computedMax < 1 && maxUnitNo < 1) return null
        return Book(
            grade = grade,
            semesters = semesters,
            subject = subject,
            textbook = textbook,
            textbookName = json.optString("textbookName", ""),
            volume = json.optString("volume", ""),
            editionType = json.optString("editionType", ""),
            maxUnitNo = maxUnitNo.coerceAtLeast(computedMax),
            units = units,
        )
    }

    private fun parseUnit(json: JSONObject?): Unit? {
        if (json == null) return null
        val no = json.optInt("no", 0)
        if (no !in 1..99) return null
        val title = json.optString("title", "").trim()
        if (title.isEmpty()) return null
        val kind = json.optString("kind", Unit.KIND_UNIT)
            .trim()
            .uppercase(Locale.ROOT)
            .ifEmpty { Unit.KIND_UNIT }
        val lessons = json.optJSONArray("lessons")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optString(index, "").trim().takeIf { it.isNotEmpty() }
            }
        }.orEmpty()
        return Unit(no = no, title = title, kind = kind, lessons = lessons)
    }
}
