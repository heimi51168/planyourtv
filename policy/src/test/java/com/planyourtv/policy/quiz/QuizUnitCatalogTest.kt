package com.planyourtv.policy.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizUnitCatalogTest {
    @Test
    fun bundledCatalogCarriesOfficialTitlesAndLessons() {
        val catalog = QuizUnitCatalog.current()
        assertNotNull(catalog)
        val data = catalog!!
        assertEquals(1, data.schemaVersion)
        assertTrue(data.catalogVersion.isNotBlank())
        assertTrue(data.books.isNotEmpty())

        val book = data.book(4, 1, "math", "renjiao")
        assertNotNull(book)
        assertEquals("上册", book!!.volume)
        assertEquals(8, book.maxUnitNo)
        assertEquals("一 万以上数的认识", book.unitTitle(1))
        val unit = book.unit(1)
        assertNotNull(unit)
        assertEquals("UNIT", unit!!.kind)
        assertTrue(unit.lessons.contains("亿以内数的认识-读数"))
        assertFalse(book.unit(9)?.isUnit ?: true)
    }

    @Test
    fun xinandaMathEditionCarriesBothGradeFiveVolumes() {
        val catalog = QuizUnitCatalog.current()!!

        val first = catalog.book(5, 1, "MATH", "XINANDA")
        assertNotNull(first)
        assertEquals("上册", first!!.volume)
        assertEquals(12, first.maxUnitNo)
        assertEquals("一 小数乘法", first.unitTitle(1))
        val firstUnit = first.unit(1)
        assertNotNull(firstUnit)
        assertTrue(firstUnit!!.lessons.contains("小数乘整数"))

        val second = catalog.book(5, 2, "math", "xinanda")
        assertNotNull(second)
        assertEquals("下册", second!!.volume)
        assertEquals(11, second.maxUnitNo)
        assertEquals("一 倍数与因数", second.unitTitle(1))
    }

    @Test
    fun editorVariantEditionsCarryTheirUnits() {
        val catalog = QuizUnitCatalog.current()!!

        val waiyanFirst = catalog.book(1, 1, "ENGLISH", "WAIYAN")
        assertNotNull(waiyanFirst)
        assertEquals(13, waiyanFirst!!.maxUnitNo)
        assertEquals("Module 1", waiyanFirst.unitTitle(1))
        assertTrue(waiyanFirst.unit(1)!!.lessons.contains("Unit 1 Hello!"))

        val waiyanFifth = catalog.book(5, 2, "english", "waiyan")
        assertNotNull(waiyanFifth)
        assertEquals(12, waiyanFifth!!.maxUnitNo)
        assertEquals("Module 1", waiyanFifth.unitTitle(1))

        // The platform only publishes the YILIN primary series from grade 3.
        assertNull(catalog.book(1, 1, "ENGLISH", "YILIN"))
        assertNull(catalog.book(2, 2, "ENGLISH", "YILIN"))
        val yilinThird = catalog.book(3, 1, "ENGLISH", "YILIN")
        assertNotNull(yilinThird)
        assertEquals(10, yilinThird!!.maxUnitNo)
        assertEquals("Unit 1 Hello!", yilinThird.unitTitle(1))

        val geography = catalog.book(7, 1, "GEOGRAPHY", "ZHONGTU")
        assertNotNull(geography)
        assertEquals(5, geography!!.maxUnitNo)
        assertEquals("第1章 认识地球", geography.unitTitle(1))

        val physics = catalog.book(8, 1, "PHYSICS", "BEISHIDA")
        assertNotNull(physics)
        assertEquals("全一册", physics!!.volume)
        assertEquals(12, physics.maxUnitNo)
        assertEquals("走进物理世界", physics.unitTitle(1))
        assertEquals(12, catalog.unitMax(8, 2, "PHYSICS", "BEISHIDA"))
        assertEquals(9, catalog.unitMax(9, 1, "PHYSICS", "BEISHIDA"))
    }

    @Test
    fun catalogExtendsUnitLimitsPerBookAndEdition() {
        val catalog = QuizUnitCatalog.current()!!
        assertEquals(8, QuizCurriculum.current()!!.unitMax(4))

        // The authority lists two more top-level nodes than the stage default.
        assertEquals(10, catalog.unitMax(4, 2, "MATH", "RENJIAO"))
        assertEquals(10, catalog.unitMax(4, 2, "math", "renjiao"))
        // A wildcard takes the largest matching book for that term; the SWU
        // edition now has fourteen top-level nodes in the first volume.
        assertEquals(14, catalog.unitMax(4, 1, "ALL", "ALL"))
        // An uncatalogued edition must leave the caller on the stage limit.
        assertNull(catalog.unitMax(4, 1, "SCIENCE", "GENERAL"))
        // Wrong term must not borrow the other volume's limit.
        assertNull(catalog.unitMax(4, 2, "SCIENCE", "GENERAL"))
    }

    @Test
    fun termSpecificVolumeWinsOverAFullVolumeBook() {
        val catalog = QuizUnitCatalog.current()!!
        // Grade 9 English publishes both a new first-term book and an older
        // full-volume book; the scope only knows the semester, so the term
        // book must win in term one and the full volume in term two.
        val first = catalog.book(9, 1, "ENGLISH", "RENJIAO")
        assertNotNull(first)
        assertEquals("上册", first!!.volume)
        assertEquals(8, first.maxUnitNo)
        assertEquals("Unit 1 The Changing World", first.unitTitle(1))
        assertEquals(8, catalog.unitMax(9, 1, "ENGLISH", "RENJIAO"))

        val second = catalog.book(9, 2, "ENGLISH", "RENJIAO")
        assertNotNull(second)
        assertEquals("全一册", second!!.volume)
        assertEquals(14, second.maxUnitNo)
        assertEquals(14, catalog.unitMax(9, 2, "ENGLISH", "RENJIAO"))
    }

    @Test
    fun catalogOnlyRescuesUnitScopes() {
        // 4th grade math has eight units in the stage default and ten in the
        // second-term catalogue. Midterm/final/review must not need the extra.
        val scope = { scopeType: String, unitNo: Int? ->
            QuizAnswerScope.create(
                grade = 4,
                semester = 2,
                subject = "math",
                textbook = "renjiao",
                scopeType = scopeType,
                unitNo = unitNo,
                difficulty = null,
            )
        }
        assertNotNull(scope("UNIT", 10))
        assertNull(scope("UNIT", 11))
        assertNotNull(scope("MIDTERM", null))
        assertNotNull(scope("FINAL", null))
        assertNotNull(scope("REVIEW", null))
        assertNull(scope("MIDTERM", 10))
    }

    @Test
    fun missingCatalogKeepsMidtermWorkingAndUnitFallsBackToStage() {
        QuizUnitCatalog.installForTest(null)
        try {
            assertNull(QuizUnitCatalog.current())
            listOf("MIDTERM", "FINAL", "REVIEW").forEach { scopeType ->
                assertNotNull(
                    QuizAnswerScope.create(
                        grade = 4,
                        semester = 2,
                        subject = "math",
                        textbook = "renjiao",
                        scopeType = scopeType,
                        unitNo = null,
                        difficulty = null,
                    ),
                )
            }
            assertNotNull(
                QuizAnswerScope.create(
                    grade = 4,
                    semester = 2,
                    subject = "math",
                    textbook = "renjiao",
                    scopeType = "UNIT",
                    unitNo = 8,
                    difficulty = null,
                ),
            )
            assertNull(
                QuizAnswerScope.create(
                    grade = 4,
                    semester = 2,
                    subject = "math",
                    textbook = "renjiao",
                    scopeType = "UNIT",
                    unitNo = 9,
                    difficulty = null,
                ),
            )
        } finally {
            QuizUnitCatalog.clearTestOverride()
        }
    }

    @Test
    fun invalidCatalogsFailClosed() {
        assertNull(QuizUnitCatalog.parse("{}"))
        assertNull(
            QuizUnitCatalog.parse(
                """{"schemaVersion":2,"books":[]}""",
            ),
        )
        assertNull(
            QuizUnitCatalog.parse(
                """{
                    "schemaVersion":1,
                    "books":[{
                        "grade":4,
                        "semesters":[1],
                        "subject":"MATH",
                        "textbook":"RENJIAO",
                        "volume":"上册",
                        "units":[{"no":1,"title":"一 万以上数的认识","kind":"UNIT"}]
                    },{
                        "grade":4,
                        "semesters":[1],
                        "subject":"MATH",
                        "textbook":"RENJIAO",
                        "volume":"上册",
                        "units":[{"no":1,"title":"重复","kind":"UNIT"}]
                    }]
                }""",
            ),
        )
    }

    @Test
    fun descriptorCarriesKindLessonsAndBookIdentity() {
        val catalog = QuizUnitCatalog.current()!!
        val descriptor = catalog.descriptor()
        assertEquals(1, descriptor.getInt("schemaVersion"))
        val books = descriptor.getJSONArray("books")
        assertTrue(books.length() > 0)
        var matched = false
        (0 until books.length()).forEach { index ->
            val book = books.getJSONObject(index)
            if (book.getInt("grade") == 4 &&
                book.getString("subject") == "MATH" &&
                book.getString("textbook") == "RENJIAO" &&
                book.getString("volume") == "上册"
            ) {
                matched = true
                assertEquals(8, book.getInt("maxUnitNo"))
                val unit = book.getJSONArray("units").getJSONObject(0)
                assertEquals(1, unit.getInt("no"))
                assertEquals("UNIT", unit.getString("kind"))
                assertTrue(unit.getJSONArray("lessons").length() > 0)
            }
        }
        assertTrue(matched)
    }
}
