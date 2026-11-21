package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local-session scope: configured entertainment + unlimited + every selected study app while the
 * study-earn switch is on. Quiz, study reward, direct start and both manual timers share it, so
 * the manual-timer name stays as an alias. Study and unlimited always win over entertainment.
 */
class ManualTimerScopeTest {
    private val video = "com.example.video"
    private val game = "com.example.game"
    private val study = "com.example.study"
    private val free = "com.example.free"

    @Test
    fun configuredScopesFollowTheCategoryPrecedence() {
        val rules = LocalRules(
            allowedPackages = setOf(video, study, free),
            studyPackages = setOf(study),
            studyEarnEnabled = true,
            unlimitedPackages = setOf(free),
        )

        val scope = rules.manualTimerScope(emptySet())

        assertEquals(1, scope.entertainmentCount)
        assertFalse(scope.entertainmentAll)
        assertEquals(1, scope.unlimitedCount)
        assertEquals(1, scope.studyRewardCount)
        assertTrue(scope.studyRewardIncluded)
        assertTrue(scope.hasTimedApps)
        assertEquals(setOf(video, free, study), rules.manualVisiblePackages(emptySet()))
    }

    @Test
    fun theLocalSessionScopeIsTheSameUnionAsTheManualTimer() {
        val rules = LocalRules(
            allowedPackages = setOf(video, study, free),
            studyPackages = setOf(study),
            studyEarnEnabled = true,
            unlimitedPackages = setOf(free),
        )

        assertEquals(
            setOf(video, free, study),
            rules.sessionVisiblePackages(emptySet()),
        )
        assertEquals(
            rules.manualVisiblePackages(emptySet()),
            rules.sessionVisiblePackages(emptySet()),
        )
    }

    @Test
    fun theStudySwitchRemovesTheRewardScope() {
        val rules = LocalRules(
            allowedPackages = setOf(video, study),
            studyPackages = setOf(study),
            studyEarnEnabled = false,
        )

        val scope = rules.manualTimerScope(emptySet())

        assertEquals(1, scope.entertainmentCount)
        assertEquals(0, scope.studyRewardCount)
        assertFalse(scope.studyRewardIncluded)
        assertTrue(scope.hasTimedApps)
        assertEquals(setOf(video), rules.manualVisiblePackages(emptySet()))
    }

    @Test
    fun anEmptyStudyListHidesTheRewardChip() {
        val rules = LocalRules(
            allowedPackages = setOf(video),
            studyEarnEnabled = true,
        )

        val scope = rules.manualTimerScope(emptySet())

        assertEquals(0, scope.studyRewardCount)
        assertFalse(scope.studyRewardIncluded)
        assertTrue(scope.hasTimedApps)
    }

    @Test
    fun theAllAppsSentinelExpandsAgainstTheInstalledScan() {
        val rules = LocalRules(
            allowedPackages = setOf(ALL_VALID_APP),
            studyPackages = setOf(study),
            studyEarnEnabled = true,
            unlimitedPackages = setOf(free),
        )
        val installed = setOf(video, game, study, free)

        val scope = rules.manualTimerScope(installed)

        assertTrue(scope.entertainmentAll)
        assertEquals(2, scope.entertainmentCount)
        assertEquals(setOf(video, game, free, study), rules.manualVisiblePackages(installed))
    }

    @Test
    fun unlimitedOnlyHasAppsButNoCountdown() {
        val rules = LocalRules(
            allowedPackages = emptySet(),
            unlimitedPackages = setOf(free),
        )

        val scope = rules.manualTimerScope(emptySet())

        assertEquals(0, scope.entertainmentCount)
        assertEquals(1, scope.unlimitedCount)
        assertTrue(scope.hasApps)
        assertFalse(scope.hasTimedApps)
        assertFalse(rules.hasManualTimedApps(emptySet()))
    }

    @Test
    fun anEmptyScopeHasNeitherAppsNorACountdown() {
        val rules = LocalRules(allowedPackages = emptySet())

        val scope = rules.manualTimerScope(emptySet())

        assertFalse(scope.hasApps)
        assertFalse(scope.hasTimedApps)
        assertFalse(rules.hasManualTimedApps(emptySet()))
    }
}