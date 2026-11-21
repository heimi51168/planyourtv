package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyEarnRulesTest {
    private val rules = LocalRules(
        allowedPackages = setOf("com.example.tv"),
        studyPackages = setOf("com.example.study", "com.example.other"),
        // V45: every selected study app participates; the legacy field mirrors the full list.
        studyEarnPackages = setOf("com.example.study", "com.example.other"),
    )

    @Test
    fun defaultsDisableTheFeatureAndUseTenToFiveMinutes() {
        assertEquals(emptySet<String>(), LocalRules.DEFAULTS.studyEarnPackages)
        assertEquals(10, LocalRules.DEFAULTS.studyEarnStudyMinutes)
        assertEquals(5, LocalRules.DEFAULTS.studyEarnRewardMinutes)
        assertTrue(LocalRules.DEFAULTS.copy(studyPackages = setOf("a.b")).isUsable)
    }

    @Test
    fun normalizationMakesEverySelectedStudyAppRedeemable() {
        // V45 removed the per-app switches. An old subset (or a stale entry) is rewritten to the
        // current study scope instead of splitting the feature.
        val stale = rules.copy(
            studyPackages = setOf("com.example.study", "com.example.other"),
            studyEarnPackages = setOf("com.example.study", "com.example.removed"),
        )

        assertEquals(
            setOf("com.example.study", "com.example.other"),
            stale.normalized().studyEarnPackages,
        )
    }

    @Test
    fun anInvalidRatioIsRejected() {
        assertEquals(
            LocalRulesError.INVALID_STUDY_EARN,
            rules.copy(studyEarnStudyMinutes = 0).validationError(),
        )
        assertEquals(
            LocalRulesError.INVALID_STUDY_EARN,
            rules.copy(studyEarnRewardMinutes = LocalRules.MAX_STUDY_EARN_MINUTES + 1)
                .validationError(),
        )
    }

    @Test
    fun redeemablePackagesMustBeConcreteStudyPackages() {
        assertEquals(
            LocalRulesError.INVALID_STUDY_EARN,
            rules.copy(studyEarnPackages = setOf(ALL_VALID_APP)).validationError(),
        )
        assertEquals(
            LocalRulesError.INVALID_STUDY_EARN,
            rules.copy(studyEarnPackages = setOf("bad package")).validationError(),
        )
        // V45 treats the legacy field as a projection of the study scope: a package that is not
        // a study app never survives normalization, and the edit itself stays valid.
        assertEquals(
            setOf("com.example.study", "com.example.other"),
            rules.copy(studyEarnPackages = setOf("com.example.absent"))
                .normalized()
                .studyEarnPackages,
        )
    }

    @Test
    fun theSharedScopeCarriesTheFeatureAcrossRuleSets() {
        val sets = LocalRuleSets.from(rules)
        val materialized = sets.rules()

        assertEquals(rules.studyEarnPackages, sets.shared.studyEarnPackages)
        assertEquals(rules.studyEarnStudyMinutes, materialized.studyEarnStudyMinutes)
        assertEquals(rules.studyEarnRewardMinutes, materialized.studyEarnRewardMinutes)
        assertTrue(sets.isUsable)
    }

    @Test
    fun aCloudRuleSnapshotKeepsTheDeviceStudyEarnChoice() {
        val current = LocalRuleSets.from(rules)
        val remote = LocalRuleSets.from(
            rules.copy(
                dayGroups = rules.dayGroups.map {
                    it.copy(entertainmentDailyLimitMinutes = 30)
                },
                studyEarnPackages = emptySet(),
                studyEarnStudyMinutes = 10,
                studyEarnRewardMinutes = 5,
            ),
        )

        val applied = remote.preservingDeviceFieldsFrom(current)

        assertEquals(rules.studyEarnPackages, applied.shared.studyEarnPackages)
        assertEquals(rules.studyEarnStudyMinutes, applied.shared.studyEarnStudyMinutes)
        assertEquals(rules.studyEarnRewardMinutes, applied.shared.studyEarnRewardMinutes)
        // The hosted contract is a flat weekday/weekend projection, so it cannot express custom
        // groups. The locally owned schedule (and its 60-minute quota) is kept unchanged.
        assertEquals(rules.dayGroups, applied.manual.dayGroups)
        assertEquals(rules.autoDailyLimitMinutes, applied.rules().autoDailyLimitMinutes)
    }

    @Test
    fun anInvalidRatioSurvivesTheSharedScopeAndFailsTheSet() {
        val sets = LocalRuleSets.from(rules.copy(studyEarnRewardMinutes = 0))

        assertFalse(sets.isUsable)
        assertFalse(sets.rules().isUsable)
    }
}
