package com.planyourtv.policy.quiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteQuizConfigTest {
    private fun valid(overrides: (JSONObject) -> Unit = {}): JSONObject {
        val json = JSONObject()
            .put("deviceNo", "tv-1")
            .put("familyId", 12L)
            .put("enabled", true)
            .put("source", "local")
            .put("pullCount", 8)
            .put("minutesPerQuestion", 7)
            .put("pointsPerQuestion", 1)
            .put("defaultTimeLimitSec", 45)
            .put("answerDisplaySec", 20)
            .put("shuffleOptions", false)
            .put("maxEarnedMinutes", 30)
            .put("version", 6L)
            .put("appliedVersion", 5L)
            .put("localVersion", 3L)
            .put("localUploaded", true)
        overrides(json)
        return json
    }

    @Test
    fun parsesTheContractIntoTheLocalConfig() {
        val parsed = RemoteQuizConfig.parse(valid())
        requireNotNull(parsed)
        assertEquals("tv-1", parsed.deviceNo)
        assertEquals(12L, parsed.familyId)
        assertEquals(QuizSource.LOCAL, parsed.source)
        assertEquals(6L, parsed.version)
        assertEquals(3L, parsed.localVersion)

        val legacy = QuizConfig(defaultTimeLimitSec = 60, minutesPerPoint = 5, minCorrectCount = 2)
        val applied = parsed.toQuizConfig(legacy)
        assertEquals(45, applied.defaultTimeLimitSec)
        assertEquals(7, applied.minutesPerQuestion)
        // The legacy reward field stays in step so older scoring paths read the same value.
        assertEquals(7, applied.minutesPerPoint)
        assertEquals(8, applied.pullCount)
        assertEquals(30, applied.maxEarnedMinutes)
        assertEquals(20, applied.answerDisplaySec)
        assertEquals(false, applied.shuffleOptions)
        // Fields owned by the local editor are preserved.
        assertEquals(2, applied.minCorrectCount)
    }

    @Test
    fun missingNodeIsIgnored() {
        assertNull(RemoteQuizConfig.parse(null))
        assertNull(RemoteQuizConfig.parse(JSONObject()))
    }

    @Test
    fun rejectsAnUnknownSource() {
        assertNull(RemoteQuizConfig.parse(valid { it.put("source", "BANK") }))
    }

    @Test
    fun rejectsAMissingSwitch() {
        assertNull(RemoteQuizConfig.parse(valid { it.remove("enabled") }))
    }

    @Test
    fun rejectsAPointsContractChange() {
        assertNull(RemoteQuizConfig.parse(valid { it.put("pointsPerQuestion", 2) }))
    }

    @Test
    fun rejectsOutOfRangeNumbers() {
        assertNull(RemoteQuizConfig.parse(valid { it.put("pullCount", 4) }))
        assertNull(RemoteQuizConfig.parse(valid { it.put("pullCount", 51) }))
        assertNull(RemoteQuizConfig.parse(valid { it.put("minutesPerQuestion", 0) }))
        assertNull(RemoteQuizConfig.parse(valid { it.put("minutesPerQuestion", 31) }))
        assertNull(RemoteQuizConfig.parse(valid { it.put("defaultTimeLimitSec", 14) }))
        assertNull(RemoteQuizConfig.parse(valid { it.put("answerDisplaySec", 121) }))
        assertNull(RemoteQuizConfig.parse(valid { it.put("maxEarnedMinutes", 181) }))
    }

    @Test
    fun nullFamilyIsAccepted() {
        val parsed = RemoteQuizConfig.parse(valid { it.put("familyId", JSONObject.NULL) })
        requireNotNull(parsed)
        assertNull(parsed.familyId)
    }

    @Test
    fun cloudSourceIsParsedButTheBuildStillDecidesSupport() {
        val parsed = RemoteQuizConfig.parse(valid { it.put("source", "cloud") })
        requireNotNull(parsed)
        assertEquals(QuizSource.CLOUD, parsed.source)
        assertTrue(parsed.pointsPerQuestion == 1)
    }

    @Test
    fun oldBackendWithoutAScopeKeyKeepsTheLocalScope() {
        // 字段缺失只可能来自旧后端：保留本地范围，不能当成“跟随孩子档案”。
        val parsed = RemoteQuizConfig.parse(valid())
        requireNotNull(parsed)
        assertTrue(!parsed.scopeProvided)
        assertNull(parsed.scope)
    }

    @Test
    fun explicitNullScopeMeansFollowTheLearner() {
        val parsed = RemoteQuizConfig.parse(valid { it.put("scope", JSONObject.NULL) })
        requireNotNull(parsed)
        assertTrue(parsed.scopeProvided)
        assertNull(parsed.scope)
    }

    @Test
    fun parsesTheCanonicalScopeWithDifficulty() {
        val parsed = RemoteQuizConfig.parse(valid {
            it.put("scope", scopeJson(difficulty = "MEDIUM"))
        })
        requireNotNull(parsed)
        assertTrue(parsed.scopeProvided)
        val scope = requireNotNull(parsed.scope)
        assertEquals(4, scope.grade)
        assertEquals(2, scope.semester)
        assertEquals("MATH", scope.subject)
        assertEquals("RENJIAO", scope.textbook)
        assertEquals("UNIT", scope.scopeType)
        assertEquals(3, scope.unitNo)
        assertEquals("MEDIUM", scope.difficulty)
    }

    @Test
    fun acceptsAllSubjectAndTextbookWildcards() {
        val parsed = RemoteQuizConfig.parse(valid {
            it.put("scope", scopeJson().put("subject", "ALL").put("textbook", "ALL"))
        })
        requireNotNull(parsed)
        val scope = requireNotNull(parsed.scope)
        assertEquals(QuizAnswerScope.SUBJECT_ALL, scope.subject)
        assertEquals(QuizAnswerScope.TEXTBOOK_ALL, scope.textbook)
        assertEquals("UNIT", scope.scopeType)
    }

    @Test
    fun difficultyCanBeAbsent() {
        val parsed = RemoteQuizConfig.parse(valid {
            it.put("scope", scopeJson(difficulty = null))
        })
        requireNotNull(parsed)
        assertNull(requireNotNull(parsed.scope).difficulty)
    }

    @Test
    fun anInvalidScopeRejectsTheWholeRemoteNode() {
        // 不静默换成默认范围：整包拒绝，调用方保留上一份已应用配置。
        assertNull(RemoteQuizConfig.parse(valid {
            it.put("scope", scopeJson().put("subject", "MUSIC"))
        }))
        assertNull(RemoteQuizConfig.parse(valid {
            it.put("scope", scopeJson(unitNo = null))
        }))
        assertNull(RemoteQuizConfig.parse(valid {
            it.put("scope", scopeJson(scopeType = "MIDTERM", unitNo = 3))
        }))
        assertNull(RemoteQuizConfig.parse(valid {
            it.put("scope", scopeJson(difficulty = "EXPERT"))
        }))
        assertNull(RemoteQuizConfig.parse(valid {
            it.put("scope", JSONObject().put("subject", "MATH"))
        }))
    }

    private fun scopeJson(
        scopeType: String = "UNIT",
        unitNo: Int? = if (scopeType == "UNIT") 3 else null,
        difficulty: String? = "MEDIUM",
    ): JSONObject = JSONObject()
        .put("grade", 4)
        .put("semester", 2)
        .put("subject", "MATH")
        .put("textbook", "RENJIAO")
        .put("scopeType", scopeType)
        .put("unitNo", unitNo ?: JSONObject.NULL)
        .put("difficulty", difficulty ?: JSONObject.NULL)
}
