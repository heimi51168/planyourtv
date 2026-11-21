package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockRoutePlanTest {
    private val studyApp = "com.example.study"
    private val date = "2026-11-20"
    private val noon = 12 * 60 * 60

    private fun rules(
        quiz: Boolean = true,
        study: Boolean = true,
        direct: Boolean = false,
        quotaMinutes: Int? = 60,
        entertainmentWindowOpen: Boolean = true,
        x: Int = 30,
        y: Int = 10,
        directMinutes: Int = 20,
        capMinutes: Int = 30,
        quizCount: Int = 3,
        studyCount: Int = 3,
        directCount: Int = 3,
    ): LocalRules {
        val windows = if (entertainmentWindowOpen) {
            listOf(LocalTimeWindow(0, 24 * 60))
        } else {
            listOf(LocalTimeWindow(0, 60))
        }
        return LocalRules(
            dayGroups = listOf(
                LocalDayGroup(
                    days = (1..7).toList(),
                    studyWindows = listOf(LocalTimeWindow(0, 24 * 60)),
                    studyDailyLimitMinutes = null,
                    entertainmentWindows = windows,
                    entertainmentDailyLimitMinutes = quotaMinutes,
                ),
            ),
            quizGateEnabled = quiz,
            studyEarnEnabled = study,
            directStartEnabled = direct,
            studyPackages = setOf(studyApp),
            studyEarnPackages = setOf(studyApp),
            studyEarnStudyMinutes = x,
            studyEarnRewardMinutes = y,
            sessionLimitMinutes = directMinutes,
            sessionCapMinutes = capMinutes,
            quizDailyLimitCount = quizCount,
            studyRewardDailyLimitCount = studyCount,
            directDailyFreeCount = directCount,
        )
    }

    private fun plan(
        rules: LocalRules,
        usage: RouteUsage? = null,
        usedSeconds: Long = 0L,
        secondOfDay: Int = noon,
        quizEarnedSeconds: Long? = null,
        facts: UnlockRouteFacts = UnlockRouteFacts(),
    ): UnlockRoutePlan = UnlockRoutePlanner.plan(
        rules = rules,
        usage = usage,
        usedSeconds = usedSeconds,
        date = date,
        dayOfWeek = 5,
        secondOfDay = secondOfDay,
        facts = facts,
        quizEarnedSeconds = quizEarnedSeconds,
    )

    @Test
    fun routesAreIndependentAndStudyIsVisibleButUnconfiguredWithoutStudyApps() {
        val p = plan(LocalRules(dayGroups = rules().dayGroups))

        assertTrue(p.quiz.visible)
        assertTrue(p.quiz.available)
        assertTrue(p.studyReward.visible)
        assertFalse(p.studyReward.available)
        assertEquals(UnlockRouteReason.STUDY_NOT_CONFIGURED, p.studyReward.reason)
        assertFalse(p.direct.visible)
        assertEquals(UnlockRouteReason.SWITCH_OFF, p.direct.reason)
        assertTrue(p.anyEnabled)
        assertTrue(p.anyAvailable)
    }

    @Test
    fun allOffIsAValidPlanWithNoEnabledRoutes() {
        val p = plan(rules(quiz = false, study = false, direct = false))

        assertFalse(p.anyEnabled)
        assertFalse(p.anyAvailable)
        p.decisions.forEach { assertEquals(UnlockRouteReason.SWITCH_OFF, it.reason) }
    }

    @Test
    fun directUsesItsOwnDurationNotTheSharedCap() {
        val p = plan(rules(quiz = false, study = false, direct = true))

        assertTrue(p.direct.available)
        assertEquals(20 * 60L, p.direct.grantSeconds)
        assertEquals(30 * 60L, p.direct.capSeconds)
    }

    @Test
    fun oldDirectDurationAboveTheCapIsTruncated() {
        val p = plan(
            rules(quiz = false, study = false, direct = true, directMinutes = 40),
        )

        assertEquals(30 * 60L, p.direct.grantSeconds)
    }

    @Test
    fun studyRewardIsClampedByTheSharedCapButNotByStudyMinutes() {
        val normal = plan(rules(quiz = false, direct = false, x = 30, y = 10))
        assertEquals(10 * 60L, normal.studyReward.grantSeconds)

        // X and Y are independent; a reward longer than the study task is valid.
        val rewardAboveStudy = plan(rules(quiz = false, direct = false, x = 10, y = 20))
        assertEquals(20 * 60L, rewardAboveStudy.studyReward.grantSeconds)

        val legacy = plan(
            rules(quiz = false, direct = false, x = 30, y = 25, capMinutes = 20),
        )
        assertEquals(20 * 60L, legacy.studyReward.grantSeconds)
    }

    @Test
    fun quizEarnedSecondsAreCappedAndReportTheSameGrant() {
        val earned = plan(
            rules(quiz = true, study = false),
            quizEarnedSeconds = 60 * 60L,
        )
        assertEquals(30 * 60L, earned.quiz.grantSeconds)

        val nothing = plan(
            rules(quiz = true, study = false),
            quizEarnedSeconds = 0L,
        )
        assertEquals(UnlockRouteReason.NOT_EARNED, nothing.quiz.reason)
        assertEquals(0L, nothing.quiz.grantSeconds)
        assertFalse(nothing.quiz.available)
    }

    @Test
    fun remainingQuotaPartiallyGrantsAndConsumesOneCountAtRuntime() {
        val p = plan(
            rules(quiz = false, study = false, direct = true, directMinutes = 10),
            usedSeconds = 55 * 60L,
        )

        assertEquals(5 * 60L, p.direct.grantSeconds)
        assertTrue(p.direct.available)
        assertEquals(5 * 60L, p.quotaRemainingSeconds)
    }

    @Test
    fun zeroQuotaMakesEveryRouteUnavailable() {
        val p = plan(
            rules(quiz = true, study = true, direct = true),
            usedSeconds = 60 * 60L,
        )

        assertEquals(0L, p.quiz.grantSeconds)
        assertEquals(UnlockRouteReason.QUOTA_EXHAUSTED, p.quiz.reason)
        assertEquals(UnlockRouteReason.QUOTA_EXHAUSTED, p.studyReward.reason)
        assertEquals(UnlockRouteReason.QUOTA_EXHAUSTED, p.direct.reason)
        assertFalse(p.anyAvailable)
    }

    @Test
    fun unlimitedQuotaHasNoRemainingCounter() {
        val p = plan(rules(quiz = false, direct = true, quotaMinutes = null))

        assertNull(p.quotaRemainingSeconds)
        assertTrue(p.direct.available)
        assertEquals(20 * 60L, p.direct.grantSeconds)
    }

    @Test
    fun countZeroAndExhaustedHaveDistinctReasons() {
        val zero = plan(
            rules(quiz = false, direct = true, directCount = 0),
        )
        assertEquals(UnlockRouteReason.COUNT_ZERO, zero.direct.reason)
        assertEquals(0, zero.direct.remaining)

        val used = plan(
            rules(quiz = false, direct = true, directCount = 3),
            usage = RouteUsage(date = date, directGranted = 3),
        )
        assertEquals(UnlockRouteReason.COUNT_EXHAUSTED, used.direct.reason)
        assertEquals(0, used.direct.remaining)
    }

    @Test
    fun aNegativeCountIsTheCloudsUnlimitedProjection() {
        // The orchard stores 0 ("no limit") and the backend projects it as -1; unlike the local
        // editor's 0 (= never), -1 keeps the route available no matter how many were used.
        val p = plan(
            rules(quiz = false, direct = true, directCount = -1),
            usage = RouteUsage(date = date, directGranted = 12),
        )

        assertTrue(p.direct.available)
        assertEquals(UnlockRouteReason.READY, p.direct.reason)
        assertEquals(UnlockRoutePlanner.UNLIMITED_REMAINING, p.direct.remaining)
        assertEquals(-1, p.direct.dailyLimit)
        assertEquals(20 * 60L, p.direct.grantSeconds)
    }

    @Test
    fun yesterdayCountsDoNotCarryForward() {
        val p = plan(
            rules(quiz = false, direct = true, directCount = 3),
            usage = RouteUsage(date = "2026-11-19", directGranted = 3),
        )

        assertEquals(3, p.direct.remaining)
        assertTrue(p.direct.available)
    }

    @Test
    fun windowRestAndBusyAreReportedBeforeCounts() {
        val outside = plan(
            rules(quiz = false, direct = true, entertainmentWindowOpen = false),
            secondOfDay = 2 * 60 * 60,
        )
        assertEquals(UnlockRouteReason.OUTSIDE_WINDOW, outside.direct.reason)

        val resting = plan(
            rules(quiz = false, direct = true),
            facts = UnlockRouteFacts(resting = true),
        )
        assertEquals(UnlockRouteReason.RESTING, resting.direct.reason)

        val busy = plan(
            rules(quiz = false, direct = true),
            facts = UnlockRouteFacts(busy = true),
        )
        assertEquals(UnlockRouteReason.BUSY, busy.direct.reason)
    }

    @Test
    fun grantMutationIncrementsOnlyTheSelectedRouteAndResetsTheDate() {
        val old = RouteUsage(date = "2026-11-19", directGranted = 3)

        val quiz = old.withGrant(UnlockRoute.QUIZ, date)
        assertEquals(date, quiz.date)
        assertEquals(1, quiz.quizGranted)
        assertEquals(0, quiz.studyRewardGranted)
        assertEquals(0, quiz.directGranted)

        val direct = quiz.withGrant(UnlockRoute.DIRECT, date)
        assertEquals(1, direct.quizGranted)
        assertEquals(1, direct.directGranted)
    }
}