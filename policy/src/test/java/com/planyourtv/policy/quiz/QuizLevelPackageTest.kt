package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizLevelPackageTest {
    private fun scope(subject: String = "CHINESE", unitNo: Int = 2) = QuizAnswerScope.create(
        grade = 4,
        semester = 1,
        subject = subject,
        textbook = "BUBIAN",
        scopeType = "UNIT",
        unitNo = unitNo,
        difficulty = null,
    )!!

    private fun course(
        subject: String = "CHINESE",
        unitNo: Int = 2,
        kind: QuizTypeFilter = QuizTypeFilter.ANY,
    ) = QuizPackageSource.Course(scope(subject, unitNo), kind)

    @Test
    fun roundTripsMixedSourcesAndTypeFilters() {
        val levels = listOf(
            QuizLevelPackage(
                id = "lvl-a",
                name = "语文数学错题",
                sources = listOf(
                    course("CHINESE", 2, QuizTypeFilter.SINGLE),
                    course("MATH", 1, QuizTypeFilter.MULTIPLE),
                    QuizPackageSource.Collection("col-a"),
                    QuizPackageSource.WrongReview,
                ),
            ),
            QuizLevelPackage(
                id = "wrong-legacy",
                name = "",
                sources = listOf(QuizPackageSource.WrongReview),
            ),
        )
        assertEquals(levels, QuizLevelPackage.parseList(QuizLevelPackage.encodeList(levels)))

        val encoded = levels.first().encode().getJSONArray("sources")
        assertEquals("course", encoded.getJSONObject(0).getString("type"))
        assertEquals("single", encoded.getJSONObject(0).getString("answerKind"))
        assertEquals("multiple", encoded.getJSONObject(1).getString("answerKind"))
    }

    @Test
    fun enabledDefaultsToTrueAndPausedLevelsRoundTrip() {
        val legacy = JSONObject()
            .put("id", "lvl-legacy")
            .put("sources", JSONArray().put(JSONObject().put("type", "wrong")))
        assertTrue(QuizLevelPackage.parse(legacy)!!.enabled)

        val paused = QuizLevelPackage(
            id = "lvl-paused",
            name = "复习",
            sources = listOf(QuizPackageSource.WrongReview),
            enabled = false,
        )
        val parsed = QuizLevelPackage.parse(paused.encode())!!
        assertFalse(parsed.enabled)
        assertEquals(paused, parsed)

        // A malformed value must not silently resume a paused level.
        assertNull(QuizLevelPackage.parse(paused.encode().put("enabled", "false")))
    }

    @Test
    fun missingTypeFilterMeansMixedAndUnknownValuesAreRejected() {
        val json = JSONObject()
            .put("id", "lvl-a")
            .put(
                "sources",
                JSONArray().put(
                    JSONObject()
                        .put("type", "course")
                        .put("scope", scope().encode()),
                ),
            )
        assertEquals(
            QuizTypeFilter.ANY,
            (QuizLevelPackage.parse(json)!!.sources.single() as QuizPackageSource.Course).answerKind,
        )

        json.getJSONArray("sources").getJSONObject(0).put("answerKind", "essay")
        assertNull(QuizLevelPackage.parse(json))
    }

    @Test
    fun sameScopeCanAppearWithDifferentTypeFiltersButNotTwiceWithTheSame() {
        val mixed = QuizLevelPackage.create(
            listOf(
                course(kind = QuizTypeFilter.SINGLE),
                course(kind = QuizTypeFilter.MULTIPLE),
                course(kind = QuizTypeFilter.ANY),
            ),
        )
        assertNotNull(mixed)

        val duplicate = QuizLevelPackage.create(
            listOf(course(kind = QuizTypeFilter.SINGLE), course(kind = QuizTypeFilter.SINGLE)),
        )
        assertNull(duplicate)
        assertEquals(
            SourceIssue.DUPLICATE_COURSE,
            QuizLevelPackage.sourceIssue(
                listOf(course(kind = QuizTypeFilter.SINGLE), course(kind = QuizTypeFilter.SINGLE)),
            ),
        )
    }

    @Test
    fun missingOriginMeansAllAndExplicitOriginsRoundTrip() {
        val json = JSONObject()
            .put("id", "lvl-a")
            .put(
                "sources",
                JSONArray().put(
                    JSONObject()
                        .put("type", "course")
                        .put("scope", scope().encode()),
                ),
            )
        assertEquals(
            CourseOrigin.ALL,
            (QuizLevelPackage.parse(json)!!.sources.single() as QuizPackageSource.Course).origin,
        )

        val preset = QuizPackageSource.Course(scope(), QuizTypeFilter.ANY, CourseOrigin.PRESET)
        val ai = QuizPackageSource.Course(scope(), QuizTypeFilter.ANY, CourseOrigin.AI)
        val encoded = QuizLevelPackage("lvl-b", "", listOf(preset, ai)).encode()
        val sources = encoded.getJSONArray("sources")
        assertEquals("preset", sources.getJSONObject(0).getString("origin"))
        assertEquals("ai", sources.getJSONObject(1).getString("origin"))
        assertEquals(
            listOf(preset, ai),
            QuizLevelPackage.parse(encoded)!!.sources,
        )

        // A legacy source keeps the combined behavior and writes no field.
        val legacy = QuizPackageSource.Course(scope())
        assertFalse(
            QuizLevelPackage("lvl-c", "", listOf(legacy)).encode()
                .getJSONArray("sources").getJSONObject(0).has("origin"),
        )

        json.getJSONArray("sources").getJSONObject(0).put("origin", "cloud")
        assertNull(QuizLevelPackage.parse(json))
    }

    @Test
    fun sameScopeCanAppearWithDifferentOriginsButNotTwiceWithTheSame() {
        val preset = QuizPackageSource.Course(scope(), QuizTypeFilter.ANY, CourseOrigin.PRESET)
        val ai = QuizPackageSource.Course(scope(), QuizTypeFilter.ANY, CourseOrigin.AI)
        assertNotNull(QuizLevelPackage.create(listOf(preset, ai)))
        assertEquals(
            SourceIssue.DUPLICATE_COURSE,
            QuizLevelPackage.sourceIssue(listOf(preset, preset)),
        )
    }

    @Test
    fun presetBankSourceRoundTripsAndRejectsUnknownBanks() {
        val preset = QuizPackageSource.Preset(QuizPresetBank.PARENT_LIFE)
        val level = QuizLevelPackage("lvl-life", "生活常识", listOf(preset))
        val encoded = level.encode()
        val source = encoded.getJSONArray("sources").getJSONObject(0)
        assertEquals("preset", source.getString("type"))
        assertEquals("parent-life", source.getString("bankId"))
        assertEquals(level, QuizLevelPackage.parse(encoded))

        assertNull(
            QuizLevelPackage.parseSource(
                JSONObject().put("type", "preset").put("bankId", "parent-fun"),
            ),
        )
        assertEquals(
            SourceIssue.DUPLICATE_PRESET,
            QuizLevelPackage.sourceIssue(listOf(preset, preset)),
        )
        assertNull(QuizLevelPackage.create(listOf(preset, preset)))
    }

    @Test
    fun duplicateTagsAndWrongSourcesAreRejected() {
        assertEquals(
            SourceIssue.DUPLICATE_COLLECTION,
            QuizLevelPackage.sourceIssue(
                listOf(
                    QuizPackageSource.Collection("col-a"),
                    QuizPackageSource.Collection("col-a"),
                ),
            ),
        )
        assertEquals(
            SourceIssue.DUPLICATE_WRONG,
            QuizLevelPackage.sourceIssue(
                listOf(QuizPackageSource.WrongReview, QuizPackageSource.WrongReview),
            ),
        )
        assertEquals(
            SourceIssue.INVALID_COLLECTION,
            QuizLevelPackage.sourceIssue(listOf(QuizPackageSource.Collection("bad"))),
        )
    }

    @Test
    fun sourceListIsBoundedAndNeverEmpty() {
        assertEquals(SourceIssue.EMPTY, QuizLevelPackage.sourceIssue(emptyList()))
        val tooMany = (0..QuizLevelPackage.MAX_SOURCES).map {
            QuizPackageSource.Collection("col-$it")
        }
        assertEquals(SourceIssue.TOO_MANY, QuizLevelPackage.sourceIssue(tooMany))
        assertNull(
            QuizLevelPackage.sourceIssue(
                (0 until QuizLevelPackage.MAX_SOURCES).map {
                    QuizPackageSource.Collection("col-$it")
                },
            ),
        )
    }

    @Test
    fun parseListRejectsDuplicateIdsAndTooManyPackages() {
        val first = QuizLevelPackage("lvl-a", "", listOf(QuizPackageSource.WrongReview))
        val duplicate = QuizLevelPackage("lvl-a", "另一个", listOf(QuizPackageSource.WrongReview))
        assertNull(
            QuizLevelPackage.parseList(
                JSONArray().put(first.encode()).put(duplicate.encode()),
            ),
        )
        val many = JSONArray()
        repeat(QuizLevelPackage.MAX_LEVELS + 1) {
            many.put(QuizLevelPackage("lvl-$it", "", listOf(QuizPackageSource.WrongReview)).encode())
        }
        assertNull(QuizLevelPackage.parseList(many))
        assertTrue(QuizLevelPackage.parseList(many, max = Int.MAX_VALUE)!!.size == many.length())
    }

    @Test
    fun missingIdsAreAssignedAndMalformedIdsRejected() {
        val parsed = QuizLevelPackage.parse(
            JSONObject().put(
                "sources",
                JSONArray().put(JSONObject().put("type", "wrong")),
            ),
        )
        assertNotNull(parsed)
        assertNotNull(parsed!!.id)
        assertTrue(parsed.id.startsWith("lvl-"))

        assertNull(
            QuizLevelPackage.parse(
                JSONObject()
                    .put("id", "bad id")
                    .put("sources", JSONArray().put(JSONObject().put("type", "wrong"))),
            ),
        )
    }

    @Test
    fun nameKeepsTheLegacyTenCodePointLimit() {
        assertEquals("", QuizLevelPackage.normalizeName("   "))
        assertNull(QuizLevelPackage.normalizeName("一二三四五六七八九十一"))
    }
}
