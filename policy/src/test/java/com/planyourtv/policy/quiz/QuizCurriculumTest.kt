package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizCurriculumTest {
    @Test
    fun bundledCurriculumLoadsAndCoversPrimaryGrades() {
        val curriculum = QuizCurriculum.current()
        assertNotNull(curriculum)
        val data = curriculum!!
        assertEquals(1, data.schemaVersion)
        assertTrue(data.curriculumVersion.isNotBlank())
        assertTrue(data.isGradeSupported(1))
        assertTrue(data.isGradeSupported(6))
        assertNull(data.unitMax(10))
        assertFalse(data.isGradeSupported(10))
        assertFalse(data.isGradeSupported(0))
    }

    @Test
    fun everyGradeHasFiveLanguageLabelsAndAStage() {
        val data = QuizCurriculum.current()!!
        data.grades.forEach { grade ->
            assertEquals(
                setOf("zh", "en", "ru", "uk", "kk"),
                grade.labels.keys,
            )
            assertNotNull("grade ${grade.no} has no stage", data.stageFor(grade.no))
            assertTrue(data.unitMax(grade.no)!! > 0)
        }
        data.subjects.forEach { subject ->
            assertEquals(setOf("zh", "en", "ru", "uk", "kk"), subject.labels.keys)
            assertTrue(subject.aliases.isNotEmpty())
        }
        data.textbooks.forEach { textbook ->
            assertEquals(setOf("zh", "en", "ru", "uk", "kk"), textbook.labels.keys)
        }
    }

    @Test
    fun juniorDataWhenPresentMatchesTheSchema() {
        val data = QuizCurriculum.current()!!
        val junior = data.stages.firstOrNull { it.id == "junior" } ?: return
        assertEquals(7, junior.minGrade)
        assertEquals(9, junior.maxGrade)
        (junior.minGrade..junior.maxGrade).forEach { grade ->
            assertTrue(data.isGradeSupported(grade))
            assertEquals(junior.unitMax, data.unitMax(grade))
        }
        assertTrue(data.isSubjectSupported(8, "PHYSICS"))
        assertFalse(data.isSubjectSupported(7, "PHYSICS"))
        assertTrue(data.isSubjectSupported(9, "CHEMISTRY"))
        assertFalse(data.isSubjectSupported(8, "CHEMISTRY"))
        assertTrue(data.textbooksFor(8, "PHYSICS").contains("SUKE"))
    }

    @Test
    fun curatedCatalogDropsCombinationsMissingFromTheAuthority() {
        val data = QuizCurriculum.current()!!
        assertEquals(listOf("BUBIAN"), data.textbooksFor(6, "CHINESE"))
        assertTrue(data.isSubjectSupported(8, "GEOGRAPHY"))
        assertFalse(data.isSubjectSupported(9, "GEOGRAPHY"))
        assertTrue(data.isSubjectSupported(8, "BIOLOGY"))
        assertFalse(data.isSubjectSupported(9, "BIOLOGY"))
        assertFalse(data.textbooksFor(7, "MATH").contains("HUJIAO"))
        assertFalse(data.textbooksFor(7, "MATH").contains("LUJIAO"))
        assertFalse(data.textbooksFor(7, "ENGLISH").contains("RENAI"))
        assertFalse(data.textbooksFor(7, "ENGLISH").contains("NIUJIN"))
        assertFalse(data.textbooksFor(7, "ENGLISH").contains("LUJIAO"))
        assertFalse(data.textbooksFor(9, "CHEMISTRY").contains("LUKE"))
        assertTrue(data.textbooksFor(5, "MATH").contains("XINANDA"))
        assertTrue(data.textbooksFor(1, "ENGLISH").contains("WAIYAN"))
        assertFalse(data.textbooksFor(1, "ENGLISH").contains("YILIN"))
        assertFalse(data.textbooksFor(2, "ENGLISH").contains("YILIN"))
        assertTrue(data.textbooksFor(3, "ENGLISH").contains("YILIN"))
        assertTrue(data.textbooksFor(6, "ENGLISH").contains("YILIN"))
        assertEquals(listOf("KEJIAO", "SUJIAO", "RENJIAO_EJ"), data.textbooksFor(4, "SCIENCE"))
        assertEquals(
            listOf("HUASHIDA", "HUJIAO", "ZHEJIAO", "GENERAL", "ALL"),
            data.textbooksFor(7, "SCIENCE"),
        )
        assertEquals("GENERAL", data.defaultTextbook(7, "SCIENCE"))
        // Removed options stay in the global table so old scopes and bank rows
        // remain readable; only the picker stops offering them.
        assertTrue(data.textbookIds().containsAll(listOf("RENAI", "NIUJIN", "LUKE")))
    }

    @Test
    fun curatedCatalogUsesOfficialNames() {
        val data = QuizCurriculum.current()!!
        assertEquals("统编版", data.textbooks.first { it.id == "BUBIAN" }.labels["zh"])
        assertEquals("人教版（PEP）", data.textbooks.first { it.id == "PEP" }.labels["zh"])
        assertEquals("外研社版", data.textbooks.first { it.id == "WAIYAN" }.labels["zh"])
        assertEquals("沪科技版", data.textbooks.first { it.id == "HUIKE" }.labels["zh"])
        assertEquals("华东师大版", data.textbooks.first { it.id == "HUASHIDA" }.labels["zh"])
        val xinanda = data.textbooks.first { it.id == "XINANDA" }
        assertEquals("西南大学版", xinanda.labels["zh"])
        assertEquals("SWU edition", xinanda.labels["en"])
        assertEquals("Синанда", xinanda.labels["ru"])
        assertEquals("Сінанда", xinanda.labels["uk"])
        assertEquals("Синанда", xinanda.labels["kk"])
        assertEquals("生物学", data.subjects.first { it.id == "BIOLOGY" }.labels["zh"])
    }

    @Test
    fun gradeAndUnitBoundsComeFromTheStageData() {
        val data = QuizCurriculum.current()!!
        val grade = data.grades.first().no
        val stage = data.stageFor(grade)!!
        assertEquals(stage.unitMax, data.unitMax(grade))
        assertNull(data.gradeNoForCode("10a"))
        assertNull(data.gradeNoForCode("0b"))
        assertEquals(grade, data.gradeNoForCode("${grade}a"))
    }

    @Test
    fun schemaAcceptsTwoStagesWithoutCodeChanges() {
        val junior = parseWithJuniorFixture()
        assertNotNull(junior)
        val data = junior!!
        assertTrue(data.isGradeSupported(7))
        assertTrue(data.isGradeSupported(9))
        assertFalse(data.isGradeSupported(10))
        assertEquals(15, data.unitMax(7))
        assertEquals(8, data.unitMax(6))
        assertEquals(9, data.gradeNoForCode("9b"))
        assertEquals("SUKE", data.defaultTextbook(7, "PHYSICS"))
        assertTrue(data.subjectIds().contains("PHYSICS"))
        assertTrue(data.textbookIds().contains("SUKE"))
    }

    @Test
    fun juniorScopeAcceptsUnitFifteenAndRejectsGradeTen() {
        val junior = parseWithJuniorFixture()!!
        QuizCurriculum.installForTest(junior)
        // The bundled catalogue covers grade 8 physics and caps units at the
        // book value; this test targets the stage-level fallback instead.
        QuizUnitCatalog.installForTest(null)
        try {
            val scope = QuizAnswerScope.create(
                grade = 8,
                semester = 2,
                subject = "physics",
                textbook = "suke",
                scopeType = "unit",
                unitNo = 15,
                difficulty = "HARD",
            )
            assertNotNull(scope)
            assertNull(
                QuizAnswerScope.create(
                    grade = 8,
                    semester = 2,
                    subject = "physics",
                    textbook = "suke",
                    scopeType = "unit",
                    unitNo = 16,
                    difficulty = null,
                ),
            )
            assertNull(
                QuizAnswerScope.create(
                    grade = 10,
                    semester = 1,
                    subject = "physics",
                    textbook = "suke",
                    scopeType = "review",
                    unitNo = null,
                    difficulty = null,
                ),
            )
        } finally {
            QuizUnitCatalog.clearTestOverride()
            QuizCurriculum.clearTestOverride()
        }
    }

    @Test
    fun invalidDataFailsClosed() {
        val broken = JSONObject(bundledJson())
        broken.getJSONArray("grades").getJSONObject(0).remove("labels")
        assertNull(QuizCurriculum.parse(broken.toString()))

        val unknownTextbook = JSONObject(bundledJson())
        unknownTextbook.getJSONArray("subjects")
            .getJSONObject(0)
            .getJSONArray("options")
            .getJSONObject(0)
            .put("textbooks", JSONArray().put("MISSING"))
        assertNull(QuizCurriculum.parse(unknownTextbook.toString()))
    }

    private fun parseWithJuniorFixture(): QuizCurriculum.Curriculum? {
        val root = JSONObject(bundledJson())
        // The fixture must not depend on whether the bundled data already has
        // junior grades; strip primary-only coverage first, then add a known set.
        stripJuniorCoverage(root)
        val stages = root.getJSONArray("stages")
        stages.put(
            JSONObject()
                .put("id", "junior")
                .put("grades", JSONArray().put(7).put(9))
                .put("unitMax", 15)
                .put("labels", labels("初中", "Junior high", "Средняя школа", "Середня школа", "Орта мектеп")),
        )
        val grades = root.getJSONArray("grades")
        (7..9).forEach { grade ->
            grades.put(
                JSONObject()
                    .put("no", grade)
                    .put("stage", "junior")
                    .put("labels", labels("$grade 年级", "Grade $grade", "$grade класс", "$grade клас", "$grade-сынып")),
            )
        }
        val textbooks = root.getJSONArray("textbooks")
        if ("SUKE" !in ids(textbooks)) {
            textbooks.put(
                JSONObject()
                    .put("id", "SUKE")
                    .put("labels", labels("苏科版", "SuKe edition", "Суке", "Суке", "Суке")),
            )
        }
        val subjects = root.getJSONArray("subjects")
        if ("PHYSICS" !in ids(subjects)) {
            subjects.put(
                JSONObject()
                    .put("id", "PHYSICS")
                    .put("labels", labels("物理", "Physics", "Физика", "Фізика", "Физика"))
                    .put("aliases", JSONArray().put("物理").put("physics"))
                    .put(
                        "options",
                        JSONArray().put(
                            JSONObject()
                                .put("grades", JSONArray().put(7).put(9))
                                .put("defaultTextbook", "SUKE")
                                .put("textbooks", JSONArray().put("SUKE")),
                        ),
                    ),
            )
        }
        subjectOptions(subjects, "ALL").put(
            JSONObject()
                .put("grades", JSONArray().put(7).put(9))
                .put("defaultTextbook", "GENERAL")
                .put("textbooks", JSONArray().put("GENERAL").put("ALL")),
        )
        return QuizCurriculum.parse(root.toString())
    }

    private fun stripJuniorCoverage(root: JSONObject) {
        val stages = root.getJSONArray("stages")
        for (index in stages.length() - 1 downTo 0) {
            if (stages.getJSONObject(index).getJSONArray("grades").optInt(0) >= 7) {
                stages.remove(index)
            }
        }
        val grades = root.getJSONArray("grades")
        for (index in grades.length() - 1 downTo 0) {
            if (grades.getJSONObject(index).optInt("no") >= 7) grades.remove(index)
        }
        val subjects = root.getJSONArray("subjects")
        for (index in subjects.length() - 1 downTo 0) {
            val options = subjects.getJSONObject(index).getJSONArray("options")
            for (optionIndex in options.length() - 1 downTo 0) {
                val range = options.getJSONObject(optionIndex).getJSONArray("grades")
                if (range.optInt(0) >= 7) {
                    options.remove(optionIndex)
                } else if (range.optInt(1) > 6) {
                    range.put(1, 6)
                }
            }
            if (options.length() == 0) subjects.remove(index)
        }
    }

    private fun subjectOptions(subjects: JSONArray, id: String): JSONArray {
        for (index in 0 until subjects.length()) {
            val subject = subjects.getJSONObject(index)
            if (subject.optString("id") == id) return subject.getJSONArray("options")
        }
        error("missing subject $id")
    }

    private fun ids(array: JSONArray): Set<String> =
        (0 until array.length()).map { array.getJSONObject(it).optString("id") }.toSet()

    private fun labels(zh: String, en: String, ru: String, uk: String, kk: String): JSONObject =
        JSONObject()
            .put("zh", zh)
            .put("en", en)
            .put("ru", ru)
            .put("uk", uk)
            .put("kk", kk)

    private fun bundledJson(): String {
        val stream = QuizCurriculum::class.java.classLoader
            ?.getResourceAsStream("quiz/curriculum.json")
            ?: error("curriculum.json is not on the test classpath")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
