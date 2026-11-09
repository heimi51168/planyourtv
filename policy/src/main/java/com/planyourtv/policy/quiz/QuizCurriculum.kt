package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Versioned curriculum data used by the local quiz stack.
 *
 * The bundled JSON is the source of truth for grades, stages, subjects, textbooks,
 * defaults, and unit limits. This object only validates, queries, and caches it.
 * A missing or invalid file fails closed: callers see no supported values instead
 * of silently widening an answer scope.
 */
object QuizCurriculum {
    /**
     * Bound TVs still depend on the backend whitelist and cloud question bank.
     * Keep junior selections local-only until those are ready.
     */
    const val CLOUD_JUNIOR_READY = false

    private const val RESOURCE = "quiz/curriculum.json"
    private const val SUPPORTED_SCHEMA = 1
    private val REQUIRED_LANGUAGES = listOf("zh", "en", "ru", "uk", "kk")
    private val ID_PATTERN = Regex("[A-Z][A-Z0-9_]*")
    private val GRADE_CODE_PATTERN = Regex("^([1-9])([ab])$")

    data class Stage(
        val id: String,
        val minGrade: Int,
        val maxGrade: Int,
        val unitMax: Int,
        val labels: Map<String, String>,
    )

    data class Grade(
        val no: Int,
        val stage: String,
        val labels: Map<String, String>,
    )

    data class SubjectOption(
        val minGrade: Int,
        val maxGrade: Int,
        val defaultTextbook: String,
        val textbooks: List<String>,
    )

    data class Subject(
        val id: String,
        val labels: Map<String, String>,
        val aliases: List<String>,
        val options: List<SubjectOption>,
    )

    data class Textbook(
        val id: String,
        val labels: Map<String, String>,
    )

    data class Curriculum(
        val schemaVersion: Int,
        val curriculumVersion: String,
        val stages: List<Stage>,
        val grades: List<Grade>,
        val subjects: List<Subject>,
        val textbooks: List<Textbook>,
        val unitAliases: List<String>,
    ) {
        fun grade(no: Int): Grade? = grades.firstOrNull { it.no == no }

        fun stage(id: String): Stage? = stages.firstOrNull { it.id == id }

        fun stageFor(grade: Int): Stage? = grade(grade)?.let { stage(it.stage) }

        fun isGradeSupported(grade: Int): Boolean = grade(grade) != null

        fun subject(id: String): Subject? {
            val normalized = id.trim().uppercase(Locale.ROOT)
            return subjects.firstOrNull { it.id == normalized }
        }

        fun subjectsFor(grade: Int): List<Subject> = subjects.filter { isSubjectSupported(grade, it.id) }

        fun isSubjectSupported(grade: Int, subject: String): Boolean {
            val normalized = subject.trim().uppercase(Locale.ROOT)
            return subjects.any { candidate ->
                candidate.id == normalized && candidate.options.any { grade in it.minGrade..it.maxGrade }
            }
        }

        fun textbooksFor(grade: Int, subject: String): List<String> {
            val normalized = subject.trim().uppercase(Locale.ROOT)
            val option = subjects.firstOrNull { it.id == normalized }
                ?.options
                ?.firstOrNull { grade in it.minGrade..it.maxGrade }
            return option?.textbooks.orEmpty()
        }

        fun defaultTextbook(grade: Int, subject: String): String? {
            val normalized = subject.trim().uppercase(Locale.ROOT)
            return subjects.firstOrNull { it.id == normalized }
                ?.options
                ?.firstOrNull { grade in it.minGrade..it.maxGrade }
                ?.defaultTextbook
        }

        fun isTextbookSupported(grade: Int, subject: String, textbook: String): Boolean {
            val normalized = textbook.trim().uppercase(Locale.ROOT)
            return normalized in textbooksFor(grade, subject)
        }

        fun unitMax(grade: Int): Int? = stageFor(grade)?.unitMax

        fun maxUnit(): Int = stages.maxOfOrNull { it.unitMax } ?: 0

        fun subjectIds(): Set<String> = subjects.mapTo(linkedSetOf()) { it.id }

        fun textbookIds(): Set<String> = textbooks.mapTo(linkedSetOf()) { it.id }

        /** Returns the grade number for a `7a` / `7b` code, or null when unsupported. */
        fun gradeNoForCode(code: String): Int? {
            val match = GRADE_CODE_PATTERN.matchEntire(code.trim().lowercase(Locale.ROOT)) ?: return null
            val grade = match.groupValues[1].toInt()
            return grade.takeIf { isGradeSupported(it) }
        }

        fun semesterForCode(code: String): Int? {
            val normalized = code.trim().lowercase(Locale.ROOT)
            if (gradeNoForCode(normalized) == null) return null
            return if (normalized.endsWith("b")) 2 else 1
        }

        fun gradeCode(grade: Int, semester: Int): String? =
            if (isGradeSupported(grade) && semester in 1..2) {
                "$grade${if (semester == 2) "b" else "a"}"
            } else {
                null
            }

        fun descriptor(): JSONObject = JSONObject()
            .put("schemaVersion", schemaVersion)
            .put("curriculumVersion", curriculumVersion)
            .put("stages", JSONArray().apply {
                stages.forEach { stage ->
                    put(
                        JSONObject()
                            .put("id", stage.id)
                            .put("grades", JSONArray().put(stage.minGrade).put(stage.maxGrade))
                            .put("unitMax", stage.unitMax)
                            .put("labels", labelsJson(stage.labels)),
                    )
                }
            })
            .put("grades", JSONArray().apply {
                grades.sortedBy { it.no }.forEach { grade ->
                    put(
                        JSONObject()
                            .put("no", grade.no)
                            .put("stage", grade.stage)
                            .put("labels", labelsJson(grade.labels)),
                    )
                }
            })
            .put("subjects", JSONArray().apply {
                subjects.forEach { subject ->
                    put(
                        JSONObject()
                            .put("id", subject.id)
                            .put("labels", labelsJson(subject.labels))
                            .put("aliases", JSONArray().apply { subject.aliases.forEach { put(it) } })
                            .put("options", JSONArray().apply {
                                subject.options.forEach { option ->
                                    put(
                                        JSONObject()
                                            .put("grades", JSONArray().put(option.minGrade).put(option.maxGrade))
                                            .put("defaultTextbook", option.defaultTextbook)
                                            .put("textbooks", JSONArray().apply {
                                                option.textbooks.forEach { put(it) }
                                            }),
                                    )
                                }
                            }),
                    )
                }
            })
            .put("textbooks", JSONArray().apply {
                textbooks.forEach { textbook ->
                    put(JSONObject().put("id", textbook.id).put("labels", labelsJson(textbook.labels)))
                }
            })
            .put("unitAliases", JSONArray().apply { unitAliases.forEach { put(it) } })
    }

    private val bundled: Curriculum? by lazy { loadBundled() }

    @Volatile
    private var testOverride: Curriculum? = null

    @Volatile
    private var overrideInstalled = false

    /** Returns the installed test curriculum, the bundled file, or null when loading failed. */
    fun current(): Curriculum? = if (overrideInstalled) testOverride else bundled

    internal fun installForTest(curriculum: Curriculum?) {
        testOverride = curriculum
        overrideInstalled = true
    }

    internal fun clearTestOverride() {
        testOverride = null
        overrideInstalled = false
    }

    internal fun parse(json: String): Curriculum? =
        runCatching { parseOrThrow(JSONObject(json)) }.getOrNull()

    private fun loadBundled(): Curriculum? {
        val loader = QuizCurriculum::class.java.classLoader
            ?: Thread.currentThread().contextClassLoader
        val stream = loader?.getResourceAsStream(RESOURCE)
        if (stream == null) {
            System.err.println("[QuizCurriculum] missing resource $RESOURCE")
            return null
        }
        return runCatching {
            stream.bufferedReader(Charsets.UTF_8).use { reader -> parseOrThrow(JSONObject(reader.readText())) }
        }.onFailure {
            System.err.println("[QuizCurriculum] invalid resource $RESOURCE: ${it.message}")
        }.getOrNull()
    }

    private fun parseOrThrow(root: JSONObject): Curriculum {
        val schemaVersion = root.optInt("schemaVersion", 0)
        require(schemaVersion == SUPPORTED_SCHEMA) { "unsupported schemaVersion $schemaVersion" }
        val curriculumVersion = root.optString("curriculumVersion", "").trim()
        require(curriculumVersion.isNotEmpty()) { "curriculumVersion is empty" }

        val stagesJson = root.optJSONArray("stages") ?: error("stages is missing")
        require(stagesJson.length() > 0) { "stages is empty" }
        val stages = (0 until stagesJson.length()).map { index ->
            val item = stagesJson.optJSONObject(index) ?: error("stages[$index] is not an object")
            val id = item.optString("id", "").trim()
            require(id.isNotEmpty()) { "stages[$index].id is empty" }
            val range = parseGradeRange(item.optJSONArray("grades"), "stages[$index].grades")
            val unitMax = item.optInt("unitMax", 0)
            require(unitMax in 1..99) { "stages[$index].unitMax is invalid" }
            Stage(
                id = id,
                minGrade = range.first,
                maxGrade = range.second,
                unitMax = unitMax,
                labels = parseLabels(item, "stages[$index]"),
            )
        }
        require(stages.map { it.id }.toSet().size == stages.size) { "duplicate stage id" }

        val gradesJson = root.optJSONArray("grades") ?: error("grades is missing")
        require(gradesJson.length() > 0) { "grades is empty" }
        val grades = (0 until gradesJson.length()).map { index ->
            val item = gradesJson.optJSONObject(index) ?: error("grades[$index] is not an object")
            val no = item.optInt("no", 0)
            require(no in 1..9) { "grades[$index].no is invalid" }
            val stageId = item.optString("stage", "").trim()
            val stage = stages.firstOrNull { it.id == stageId }
                ?: error("grades[$index].stage is unknown")
            require(no in stage.minGrade..stage.maxGrade) { "grades[$index] is outside its stage" }
            Grade(no = no, stage = stageId, labels = parseLabels(item, "grades[$index]"))
        }.sortedBy { it.no }
        require(grades.map { it.no }.toSet().size == grades.size) { "duplicate grade no" }

        val textbooksJson = root.optJSONArray("textbooks") ?: error("textbooks is missing")
        require(textbooksJson.length() > 0) { "textbooks is empty" }
        val textbooks = (0 until textbooksJson.length()).map { index ->
            val item = textbooksJson.optJSONObject(index) ?: error("textbooks[$index] is not an object")
            val id = parseId(item, "textbooks[$index]")
            Textbook(id = id, labels = parseLabels(item, "textbooks[$index]"))
        }
        require(textbooks.map { it.id }.toSet().size == textbooks.size) { "duplicate textbook id" }
        val textbookIds = textbooks.mapTo(linkedSetOf()) { it.id }
        require("GENERAL" in textbookIds) { "GENERAL textbook is missing" }
        require("ALL" in textbookIds) { "ALL textbook is missing" }

        val subjectsJson = root.optJSONArray("subjects") ?: error("subjects is missing")
        require(subjectsJson.length() > 0) { "subjects is empty" }
        val subjects = (0 until subjectsJson.length()).map { index ->
            val item = subjectsJson.optJSONObject(index) ?: error("subjects[$index] is not an object")
            val id = parseId(item, "subjects[$index]")
            val aliases = parseStringList(item.optJSONArray("aliases"), "subjects[$index].aliases")
                .map { it.lowercase(Locale.ROOT) }
                .distinct()
            require(aliases.isNotEmpty()) { "subjects[$index].aliases is empty" }
            val optionsJson = item.optJSONArray("options") ?: error("subjects[$index].options is missing")
            require(optionsJson.length() > 0) { "subjects[$index].options is empty" }
            val options = (0 until optionsJson.length()).map { optionIndex ->
                val option = optionsJson.optJSONObject(optionIndex)
                    ?: error("subjects[$index].options[$optionIndex] is not an object")
                val range = parseGradeRange(
                    option.optJSONArray("grades"),
                    "subjects[$index].options[$optionIndex].grades",
                )
                val defaultTextbook = option.optString("defaultTextbook", "")
                    .trim()
                    .uppercase(Locale.ROOT)
                require(defaultTextbook in textbookIds) {
                    "subjects[$index].options[$optionIndex].defaultTextbook is unknown"
                }
                val books = parseStringList(
                    option.optJSONArray("textbooks"),
                    "subjects[$index].options[$optionIndex].textbooks",
                ).map { it.uppercase(Locale.ROOT) }.distinct()
                require(books.isNotEmpty()) { "subjects[$index].options[$optionIndex].textbooks is empty" }
                require(books.all { it in textbookIds }) {
                    "subjects[$index].options[$optionIndex].textbooks references an unknown id"
                }
                require(defaultTextbook in books) {
                    "subjects[$index].options[$optionIndex].defaultTextbook is not listed"
                }
                SubjectOption(
                    minGrade = range.first,
                    maxGrade = range.second,
                    defaultTextbook = defaultTextbook,
                    textbooks = books,
                )
            }.sortedBy { it.minGrade }
            require(options.zipWithNext().none { (a, b) -> a.maxGrade >= b.minGrade }) {
                "subjects[$index].options overlap"
            }
            require(options.map { it.minGrade to it.maxGrade }.toSet().size == options.size) {
                "subjects[$index].options duplicate"
            }
            Subject(
                id = id,
                labels = parseLabels(item, "subjects[$index]"),
                aliases = aliases,
                options = options,
            )
        }
        require(subjects.map { it.id }.toSet().size == subjects.size) { "duplicate subject id" }
        require(subjects.any { it.id == "ALL" }) { "ALL subject is missing" }
        grades.forEach { grade ->
            require(subjects.any { subject ->
                subject.options.any { grade.no in it.minGrade..it.maxGrade }
            }) { "grade ${grade.no} has no subject" }
        }

        val unitAliases = parseStringList(root.optJSONArray("unitAliases"), "unitAliases")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        require(unitAliases.isNotEmpty()) { "unitAliases is empty" }

        return Curriculum(
            schemaVersion = schemaVersion,
            curriculumVersion = curriculumVersion,
            stages = stages,
            grades = grades,
            subjects = subjects,
            textbooks = textbooks,
            unitAliases = unitAliases,
        )
    }

    private fun parseId(item: JSONObject, field: String): String {
        val id = item.optString("id", "").trim().uppercase(Locale.ROOT)
        require(ID_PATTERN.matches(id)) { "$field.id is invalid" }
        return id
    }

    private fun parseGradeRange(array: JSONArray?, field: String): Pair<Int, Int> {
        require(array != null && array.length() == 2) { "$field must have two values" }
        val min = array.optInt(0, 0)
        val max = array.optInt(1, 0)
        require(min in 1..9 && max in min..9) { "$field is invalid" }
        return min to max
    }

    private fun parseStringList(array: JSONArray?, field: String): List<String> {
        require(array != null && array.length() > 0) { "$field is missing or empty" }
        return (0 until array.length()).map { index ->
            val value = array.optString(index, "").trim()
            require(value.isNotEmpty()) { "$field[$index] is empty" }
            value
        }
    }

    private fun parseLabels(item: JSONObject, field: String): Map<String, String> {
        val labelsJson = item.optJSONObject("labels") ?: error("$field.labels is missing")
        val labels = linkedMapOf<String, String>()
        REQUIRED_LANGUAGES.forEach { language ->
            val value = labelsJson.optString(language, "").trim()
            require(value.isNotEmpty()) { "$field.labels.$language is empty" }
            labels[language] = value
        }
        return labels
    }

    private fun labelsJson(labels: Map<String, String>): JSONObject = JSONObject().apply {
        labels.forEach { (language, value) -> put(language, value) }
    }
}
