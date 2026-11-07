package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRulesAllowedPackagesTest {
    @Test
    fun defaultRulesAllowAllApps() {
        val rules = LocalRules.DEFAULTS
        assertEquals(setOf(ALL_VALID_APP), rules.effectiveAllowedPackages)
        assertFalse(rules.usesAppSelection)
        assertEquals(LocalRulesError.NONE, rules.validationError())
    }

    @Test
    fun schema1EmptyListMeansAllApps() {
        val rules = LocalRules(schemaVersion = 1, allowedPackages = emptySet())
        assertEquals(setOf(ALL_VALID_APP), rules.effectiveAllowedPackages)
        assertFalse(rules.usesAppSelection)
        assertEquals(setOf(ALL_VALID_APP), rules.normalized().allowedPackages)
        assertEquals(LocalRules.CURRENT_SCHEMA_VERSION, rules.normalized().schemaVersion)
    }

    @Test
    fun schema2EmptyListMeansNoEntertainmentApps() {
        val rules = LocalRules(schemaVersion = 2, allowedPackages = emptySet())
        assertTrue(rules.effectiveAllowedPackages.isEmpty())
        assertTrue(rules.usesAppSelection)
        assertTrue(rules.normalized().allowedPackages.isEmpty())
        assertEquals(LocalRulesError.NONE, rules.validationError())
    }

    @Test
    fun explicitListIsPreservedAndNormalized() {
        val rules = LocalRules(allowedPackages = setOf("com.example.kids"))
        assertTrue(rules.usesAppSelection)
        assertEquals(setOf("com.example.kids"), rules.effectiveAllowedPackages)
        assertEquals(setOf("com.example.kids"), rules.normalized().allowedPackages)
    }

    @Test
    fun studyPackagesAreSeparateFromEntertainment() {
        val rules = LocalRules(
            allowedPackages = setOf("com.example.tv", "com.example.study"),
            studyPackages = setOf("com.example.study"),
        )
        assertEquals(setOf("com.example.study"), rules.effectiveStudyPackages)
        assertEquals(
            setOf("com.example.tv", "com.example.study"),
            rules.effectiveAllowedPackages,
        )
        assertEquals(LocalRulesError.NONE, rules.validationError())
        assertTrue(rules.hasStudyMode)
    }

    @Test
    fun studyModeIsUnavailableWithoutStudyPackages() {
        assertFalse(LocalRules.DEFAULTS.hasStudyMode)
        assertFalse(LocalRules(allowedPackages = emptySet()).hasStudyMode)
    }

    @Test
    fun timedPackagesExcludeStudyAppsEvenWhenStaleDataOverlaps() {
        val rules = LocalRules(
            allowedPackages = setOf("com.example.tv", "com.example.study"),
            studyPackages = setOf("com.example.study"),
        )
        assertEquals(
            setOf("com.example.tv"),
            rules.effectiveTimedPackages(setOf("com.example.tv", "com.example.study")),
        )
    }

    @Test
    fun sentinelCollapsesEverythingElse() {
        val rules = LocalRules(
            allowedPackages = setOf("com.example.kids", ALL_VALID_APP),
        )
        assertEquals(setOf(ALL_VALID_APP), rules.normalized().allowedPackages)
        assertFalse(rules.normalized().usesAppSelection)
    }

    @Test
    fun studySentinelAndMalformedNamesAreInvalid() {
        assertEquals(
            LocalRulesError.INVALID_STUDY_PACKAGES,
            LocalRules(studyPackages = setOf(ALL_VALID_APP)).validationError(),
        )
        assertEquals(
            LocalRulesError.INVALID_STUDY_PACKAGES,
            LocalRules(studyPackages = setOf("com.example kids")).validationError(),
        )
    }

    @Test
    fun blankOrUnsafePackageNamesAreInvalid() {
        assertEquals(
            LocalRulesError.INVALID_ALLOWED_PACKAGES,
            LocalRules(allowedPackages = setOf("  ")).validationError(),
        )
        assertEquals(
            LocalRulesError.INVALID_ALLOWED_PACKAGES,
            LocalRules(allowedPackages = setOf("com.example kids")).validationError(),
        )
        assertEquals(
            LocalRulesError.INVALID_ALLOWED_PACKAGES,
            LocalRules(allowedPackages = setOf("x".repeat(LocalRules.MAX_PACKAGE_NAME_LENGTH + 1)))
                .validationError(),
        )
    }
}
