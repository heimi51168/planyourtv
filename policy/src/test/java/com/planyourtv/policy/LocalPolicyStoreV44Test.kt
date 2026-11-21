package com.planyourtv.policy

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalPolicyStoreV44Test {
    private fun rules() = LocalRules(
        sessionLimitMinutes = 20,
        sessionCapMinutes = 40,
        quizDailyLimitCount = 0,
        studyRewardDailyLimitCount = 1,
        directDailyFreeCount = 2,
        quizGateEnabled = false,
        studyEarnEnabled = false,
        directStartEnabled = true,
        studyPackages = setOf("com.example.study"),
        studyEarnPackages = setOf("com.example.study"),
        studyEarnStudyMinutes = 30,
        studyEarnRewardMinutes = 10,
    )

    @Test
    fun routeFieldsRoundTripThroughTheSubjectCodec() {
        val decoded = LocalPolicyStore.decodeRuleSetsForSubject(
            LocalPolicyStore.encodeRuleSetsForSubject(LocalRuleSets.from(rules())),
        )

        assertNotNull(decoded)
        val actual = decoded!!.rules()
        assertEquals(20, actual.sessionLimitMinutes)
        assertEquals(40, actual.sessionCapMinutes)
        assertEquals(0, actual.quizDailyLimitCount)
        assertEquals(1, actual.studyRewardDailyLimitCount)
        assertEquals(2, actual.directDailyFreeCount)
        assertEquals(false, actual.quizGateEnabled)
        assertEquals(false, actual.studyEarnEnabled)
        assertEquals(true, actual.directStartEnabled)
    }

    @Test
    fun missingV44FieldsUseSafeDefaults() {
        val raw = JSONObject(
            LocalPolicyStore.encodeRuleSetsForSubject(LocalRuleSets.from(rules())),
        )
        val manual = raw.getJSONObject("manual")
        manual.remove("sessionCapMinutes")
        manual.remove("quizDailyLimitCount")
        manual.remove("studyRewardDailyLimitCount")
        manual.remove("directDailyFreeCount")

        val decoded = LocalPolicyStore.decodeRuleSetsForSubject(raw.toString())

        assertNotNull(decoded)
        val actual = decoded!!.rules()
        assertEquals(30, actual.sessionCapMinutes)
        assertEquals(3, actual.quizDailyLimitCount)
        assertEquals(3, actual.studyRewardDailyLimitCount)
        assertEquals(3, actual.directDailyFreeCount)
    }

    @Test
    fun oldOneMinuteStudyValuesStillLoad() {
        val raw = JSONObject(
            LocalPolicyStore.encodeRuleSetsForSubject(LocalRuleSets.from(rules())),
        )
        raw.getJSONObject("apps")
            .put("studyEarnStudyMinutes", 1)
            .put("studyEarnRewardMinutes", 1)

        val decoded = LocalPolicyStore.decodeRuleSetsForSubject(raw.toString())

        assertNotNull(decoded)
        assertEquals(1, decoded!!.rules().studyEarnStudyMinutes)
        assertEquals(1, decoded.rules().studyEarnRewardMinutes)
    }

    @Test
    fun anOutOfRangeCountInvalidatesTheDocument() {
        val raw = JSONObject(
            LocalPolicyStore.encodeRuleSetsForSubject(LocalRuleSets.from(rules())),
        )
        raw.getJSONObject("manual").put("quizDailyLimitCount", 100)

        assertNull(LocalPolicyStore.decodeRuleSetsForSubject(raw.toString()))
    }
}