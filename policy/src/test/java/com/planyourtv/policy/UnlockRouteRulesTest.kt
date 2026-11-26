package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockRouteRulesTest {
    private val studyApp = "com.example.study"

    private fun base(
        quiz: Boolean = true,
        study: Boolean = true,
        direct: Boolean = false,
    ) = LocalRules(
        quizGateEnabled = quiz,
        studyEarnEnabled = study,
        directStartEnabled = direct,
        studyPackages = setOf(studyApp),
        studyEarnPackages = setOf(studyApp),
    )

    @Test
    fun defaultsEnableOnlyTheQuizRoute() {
        val defaults = LocalRules.DEFAULTS
        assertTrue(defaults.quizGateEnabled)
        assertFalse(defaults.studyEarnEnabled)
        assertFalse(defaults.directStartEnabled)
        assertTrue(defaults.quizRouteActive)
        assertFalse(defaults.studyRouteActive)
        assertFalse(defaults.directRouteActive)
        // No study apps configured: the quiz route is the only unlock method.
        assertTrue(defaults.hasUnlockRoute)
        assertEquals(20, defaults.sessionLimitMinutes)
        assertEquals(600, defaults.sessionCapMinutes)
        // 2026-11-26: 默认与果园统一为「不限制」（-1）；旧的 1000 在读取时归一化。
        assertEquals(-1, defaults.quizDailyLimitCount)
        assertEquals(-1, defaults.studyRewardDailyLimitCount)
        assertEquals(-1, defaults.directDailyFreeCount)
        assertEquals(-1, defaults.fruitExchangeDailyLimitCount)
    }

    @Test
    fun directStartDoesNotPauseQuizOrStudy() {
        val rules = base(direct = true)

        assertTrue(rules.quizGateEnabled)
        assertTrue(rules.studyEarnEnabled)
        assertTrue(rules.quizRouteActive)
        assertTrue(rules.studyRouteActive)
        assertTrue(rules.studyUnlockRouteActive)
        assertTrue(rules.directRouteActive)
        assertTrue(rules.hasUnlockRoute)
    }

    @Test
    fun theStudySwitchAloneControlsTheRewardRoute() {
        // V45: every selected study app participates, so an old empty per-app subset no longer
        // hides the reward. The feature switch and the study list are the only inputs.
        val oldSubset = base(quiz = false).copy(studyEarnPackages = emptySet())
        assertTrue(oldSubset.studyRouteActive)
        assertTrue(oldSubset.studyUnlockRouteActive)

        val noStudyApps = base(quiz = false).copy(studyPackages = emptySet())
        assertFalse(noStudyApps.studyRouteActive)
        assertFalse(noStudyApps.studyUnlockRouteActive)
        assertFalse(noStudyApps.hasUnlockRoute)

        val switchedOff = base(quiz = false, study = false)
        assertFalse(switchedOff.studyRouteActive)
        assertFalse(switchedOff.studyUnlockRouteActive)
        assertFalse(switchedOff.hasUnlockRoute)
    }

    @Test
    fun anAllOffDocumentIsUsableButHasNoUnlockRoute() {
        val rules = base(quiz = false, study = false)

        assertEquals(LocalRulesError.NONE, rules.validationError())
        assertFalse(rules.hasUnlockRoute)
    }

    @Test
    fun effectiveDurationsClampOldValuesToTheSharedCap() {
        val rules = base().copy(
            sessionLimitMinutes = 40,
            sessionCapMinutes = 30,
            studyEarnStudyMinutes = 30,
            studyEarnRewardMinutes = 25,
        )

        assertEquals(30, rules.effectiveDirectMinutes)
        assertEquals(25, rules.effectiveStudyRewardMinutes)

        // X and Y are independent inputs: a reward above the study task is valid.
        val rewardAboveStudy = rules.copy(
            studyEarnStudyMinutes = 10,
            studyEarnRewardMinutes = 25,
        )
        assertEquals(25, rewardAboveStudy.effectiveStudyRewardMinutes)

        val cappedStudy = rules.copy(studyEarnRewardMinutes = 40)
        assertEquals(30, cappedStudy.effectiveStudyRewardMinutes)
    }

    @Test
    fun largeSessionCapsAreNotProductCapped() {
        val large = base().copy(sessionCapMinutes = 100000)
        assertEquals(LocalRulesError.NONE, large.validationError())
        assertEquals(100000, large.effectiveSessionCapMinutes)

        val storageBound = large.copy(sessionCapMinutes = LocalRules.MAX_SESSION_CAP_MINUTES)
        assertEquals(LocalRulesError.NONE, storageBound.validationError())
        assertEquals(Int.MAX_VALUE, storageBound.effectiveSessionCapMinutes)
    }

    @Test
    fun ruleSetsCarryTheRoutesDurationsAndCountsIntoTheMaterializedRules() {
        val rules = base(quiz = false, study = false, direct = true).copy(
            sessionLimitMinutes = 20,
            sessionCapMinutes = 40,
            quizDailyLimitCount = 0,
            studyRewardDailyLimitCount = 1,
            directDailyFreeCount = 2,
        )
        val sets = LocalRuleSets.from(rules)

        assertTrue(sets.manual.directStartEnabled)
        assertFalse(sets.manual.quizGateEnabled)
        assertFalse(sets.manual.studyEarnEnabled)
        assertEquals(20, sets.manual.sessionLimitMinutes)
        assertEquals(40, sets.manual.sessionCapMinutes)
        assertEquals(0, sets.manual.quizDailyLimitCount)
        assertEquals(1, sets.manual.studyRewardDailyLimitCount)
        assertEquals(2, sets.manual.directDailyFreeCount)
        assertEquals(rules, sets.rules())
    }

    @Test
    fun aCloudSnapshotKeepsTheLocalRouteChoicesAndDurations() {
        val previous = LocalRuleSets.from(
            base(quiz = false, study = false, direct = true).copy(
                sessionLimitMinutes = 20,
                sessionCapMinutes = 40,
                quizDailyLimitCount = 1,
                studyRewardDailyLimitCount = 2,
                directDailyFreeCount = 5,
            ),
        )
        val cloud = LocalRuleSets(
            manual = LocalRuleSet(
                dayGroups = LocalRules.DEFAULT_DAY_GROUPS,
                sessionLimitMinutes = 45,
                sessionCapMinutes = 60,
                restMinutes = 10,
            ),
            shared = LocalSharedAppScope(
                allowedPackages = setOf("com.example.video"),
                studyPackages = setOf(studyApp),
                studyEarnPackages = setOf(studyApp),
                studyEarnStudyMinutes = 10,
                studyEarnRewardMinutes = 5,
            ),
        )

        val merged = cloud.preservingDeviceFieldsFrom(previous)

        assertTrue(merged.manual.directStartEnabled)
        assertFalse(merged.manual.quizGateEnabled)
        assertFalse(merged.manual.studyEarnEnabled)
        // The cloud's schedule and rest still apply; local-only V44 values stay put.
        assertEquals(10, merged.manual.restMinutes)
        assertEquals(20, merged.manual.sessionLimitMinutes)
        assertEquals(40, merged.manual.sessionCapMinutes)
        assertEquals(1, merged.manual.quizDailyLimitCount)
        assertEquals(2, merged.manual.studyRewardDailyLimitCount)
        assertEquals(5, merged.manual.directDailyFreeCount)
    }
}