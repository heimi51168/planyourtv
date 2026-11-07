package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulePlanSchedulerTest {
    private val startOfWindow = 18 * 3600
    private val rules = LocalRules(
        weekdayWindows = listOf(LocalTimeWindow(18 * 60, 20 * 60)),
        weekendWindows = listOf(LocalTimeWindow(9 * 60, 20 * 60)),
        autoDailyLimitMinutes = 30,
        sessionLimitMinutes = 10,
        restMinutes = 5,
    )

    private fun decide(
        secondOfDay: Int = startOfWindow,
        usedSecondsToday: Long = 0L,
        breakRemainingSeconds: Long = 0L,
        rules: LocalRules = this.rules,
        sessionId: String = "rule-session-1",
        templateVersion: Long = 12L,
        accessMode: AccessMode = AccessMode.SCREEN,
        allowedPackages: Set<String> = emptySet(),
    ): RulePlanDecision = RulePlanScheduler.decide(
        dayOfWeek = 1,
        secondOfDay = secondOfDay,
        rules = rules,
        templateVersion = templateVersion,
        sessionId = sessionId,
        usedSecondsToday = usedSecondsToday,
        breakRemainingSeconds = breakRemainingSeconds,
        accessMode = accessMode,
        allowedPackages = allowedPackages,
    )

    private fun start(decision: RulePlanDecision): RulePlanDecision.Start {
        assertTrue("expected a plan, was $decision", decision is RulePlanDecision.Start)
        return decision as RulePlanDecision.Start
    }

    @Test
    fun firstSegmentUsesSessionLimitAndSnapshotsRest() {
        val decision = start(decide())
        val plan = decision.plan
        assertEquals(600L, plan.budgetSeconds)
        assertEquals(PolicyEndReason.SESSION_LIMIT, plan.endReason)
        assertEquals(300L, plan.restAfterSeconds)
        assertEquals(PolicyPlanSource.RULE, plan.source)
        assertEquals(PolicyActivation.AUTO, plan.activation)
        assertEquals("rule-session-1", plan.sessionId)
        assertEquals(12L, plan.templateVersion)
        // A window gates the start only; it must not become a hard plan deadline.
        assertNull(plan.deadlineEpochMs)
        assertEquals(LocalTimeWindow(18 * 60, 20 * 60), decision.window)
        assertEquals(7_200L, decision.windowRemainingSeconds)
        assertEquals(1_800L, decision.dailyRemainingSeconds)
    }

    @Test
    fun thirtyMinuteDailyQuotaYieldsThreeTenMinuteSegments() {
        var used = 0L
        repeat(3) {
            val plan = start(decide(usedSecondsToday = used)).plan
            assertEquals(600L, plan.budgetSeconds)
            assertEquals(PolicyEndReason.SESSION_LIMIT, plan.endReason)
            used += plan.budgetSeconds
        }
        assertTrue(decide(usedSecondsToday = used) is RulePlanDecision.Locked)
    }

    @Test
    fun dailyLimitEndsPlanWithoutRest() {
        val plan = start(decide(usedSecondsToday = 1_500L)).plan
        assertEquals(300L, plan.budgetSeconds)
        assertEquals(PolicyEndReason.DAILY_LIMIT, plan.endReason)
        assertEquals(0L, plan.restAfterSeconds)
    }

    @Test
    fun startingNearWindowEndStillGetsTheFullSessionBudget() {
        // 19:55 in an 18:00-20:00 window: only five minutes of window remain, but the plan
        // still grants the ten-minute session limit and has no window deadline.
        val decision = start(decide(secondOfDay = 19 * 3600 + 55 * 60))
        val plan = decision.plan
        assertEquals(600L, plan.budgetSeconds)
        assertEquals(PolicyEndReason.SESSION_LIMIT, plan.endReason)
        assertEquals(300L, plan.restAfterSeconds)
        assertNull(plan.deadlineEpochMs)
        assertEquals(300L, decision.windowRemainingSeconds)
    }

    @Test
    fun exhaustedDayIsLockedWithTimeUp() {
        val decision = decide(usedSecondsToday = 1_800L)
        assertTrue(decision is RulePlanDecision.Locked)
        assertEquals(LockReason.TIME_UP, (decision as RulePlanDecision.Locked).reason)
    }

    @Test
    fun outsideWindowIsLockedWithNextWindow() {
        val decision = decide(secondOfDay = 17 * 3600)
        assertTrue(decision is RulePlanDecision.Locked)
        val locked = decision as RulePlanDecision.Locked
        assertEquals(LockReason.OUTSIDE_WINDOW, locked.reason)
        assertEquals(0, locked.nextWindowDayOffset)
        assertEquals(18 * 60, locked.nextWindowMinuteOfDay)
    }

    @Test
    fun activeRestWinsOverWindowAndDaily() {
        val decision = decide(breakRemainingSeconds = 60L)
        assertTrue(decision is RulePlanDecision.Locked)
        val locked = decision as RulePlanDecision.Locked
        assertEquals(LockReason.RESTING, locked.reason)
        assertEquals(60L, locked.breakRemainingSeconds)
    }

    @Test
    fun appScopeSnapshotIsCarriedIntoThePlan() {
        val plan = start(
            decide(
                accessMode = AccessMode.APPS,
                allowedPackages = setOf("com.example.video"),
            ),
        ).plan
        assertEquals(AccessMode.APPS, plan.accessMode)
        assertEquals(setOf("com.example.video"), plan.allowedPackages)
        assertFalse(plan.allowedPackages.isEmpty())
    }

    @Test
    fun invalidRulesRequireSetup() {
        val empty = LocalRules(weekdayWindows = emptyList(), weekendWindows = emptyList())
        assertTrue(decide(rules = empty) is RulePlanDecision.SetupRequired)
    }

    @Test(expected = IllegalArgumentException::class)
    fun startingAPlanRequiresASessionId() {
        decide(sessionId = " ")
    }
}
