package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalScheduleEngineTest {
    private val monday = 1
    private val saturday = 6
    private fun at(hour: Int, minute: Int = 0) = hour * 3600 + minute * 60

    // The first-run default now spans the day. Tests about a narrow start window keep an
    // explicit school-day template so they still exercise the same boundary behavior.
    private val schoolWeek = LocalRules.DEFAULTS.copy(
        weekdayWindows = listOf(LocalTimeWindow(15 * 60, 21 * 60)),
        weekendWindows = listOf(LocalTimeWindow(12 * 60, 21 * 60)),
    )

    @Test
    fun defaultsAreUsableAndAllowTheWholeDay() {
        val rules = LocalRules.DEFAULTS
        assertEquals(LocalRulesError.NONE, rules.validationError())
        assertTrue(rules.isUsable)
        assertEquals(listOf(LocalTimeWindow(1, 23 * 60 + 59)), rules.windowsFor(monday))
        assertEquals(listOf(LocalTimeWindow(1, 23 * 60 + 59)), rules.windowsFor(saturday))
    }

    @Test
    fun beforeTheWindowIsLockedAndPointsAtItsStart() {
        val decision = LocalScheduleEngine.evaluate(
            dayOfWeek = monday,
            secondOfDay = at(14, 30),
            rules = schoolWeek,
            usedSecondsToday = 0,
        )
        val locked = decision as LocalDecision.Locked
        assertEquals(LockReason.OUTSIDE_WINDOW, locked.reason)
        assertEquals(0, locked.nextWindowDayOffset)
        assertEquals(15 * 60, locked.nextWindowMinuteOfDay)
    }

    @Test
    fun insideTheWindowStartsWithTheSessionLimit() {
        val decision = LocalScheduleEngine.evaluate(
            dayOfWeek = monday,
            secondOfDay = at(18, 30),
            rules = schoolWeek,
            usedSecondsToday = 0,
        )
        val active = decision as LocalDecision.Active
        assertEquals(20 * 60L, active.sessionSeconds)
        assertEquals(LocalSessionEnd.SESSION_LIMIT, active.endReason)
        assertEquals(150 * 60L, active.windowRemainingSeconds)
        assertEquals(60 * 60L, active.dailyRemainingSeconds)
    }

    @Test
    fun theWindowOnlyGatesTheStartAndDoesNotCapASession() {
        // A ten-minute window remains a legal start window. A child starting five minutes
        // before it closes still gets the full session limit and may finish outside it.
        val rules = LocalRules.DEFAULTS.copy(
            weekdayWindows = listOf(LocalTimeWindow(18 * 60, 18 * 60 + 10)),
        )
        val decision = LocalScheduleEngine.evaluate(
            dayOfWeek = monday,
            secondOfDay = at(18, 5),
            rules = rules,
            usedSecondsToday = 0,
        ) as LocalDecision.Active
        assertEquals(20 * 60L, decision.sessionSeconds)
        assertEquals(LocalSessionEnd.SESSION_LIMIT, decision.endReason)
        assertEquals(5 * 60L, decision.windowRemainingSeconds)
    }

    @Test
    fun theDailyQuotaCapsASession() {
        val decision = LocalScheduleEngine.evaluate(
            dayOfWeek = monday,
            secondOfDay = at(18, 30),
            rules = LocalRules.DEFAULTS,
            usedSecondsToday = 50 * 60L,
        ) as LocalDecision.Active
        assertEquals(10 * 60L, decision.sessionSeconds)
        assertEquals(LocalSessionEnd.DAILY_LIMIT, decision.endReason)
    }

    @Test
    fun spentDailyQuotaLocksUntilTheNextWindow() {
        val decision = LocalScheduleEngine.evaluate(
            dayOfWeek = monday,
            secondOfDay = at(18, 30),
            rules = schoolWeek,
            usedSecondsToday = 60 * 60L,
        ) as LocalDecision.Locked
        assertEquals(LockReason.TIME_UP, decision.reason)
        // The next window after Monday 18:30 is Tuesday 15:00.
        assertEquals(1, decision.nextWindowDayOffset)
        assertEquals(15 * 60, decision.nextWindowMinuteOfDay)
    }

    @Test
    fun restWinsUntilItEnds() {
        val decision = LocalScheduleEngine.evaluate(
            dayOfWeek = monday,
            secondOfDay = at(18, 30),
            rules = LocalRules.DEFAULTS,
            usedSecondsToday = 0,
            breakRemainingSeconds = 300,
        ) as LocalDecision.Locked
        assertEquals(LockReason.RESTING, decision.reason)
        assertEquals(300L, decision.breakRemainingSeconds)
    }

    @Test
    fun theEndSecondItselfIsOutside() {
        val decision = LocalScheduleEngine.evaluate(
            dayOfWeek = monday,
            secondOfDay = at(21, 0),
            rules = schoolWeek,
            usedSecondsToday = 0,
        ) as LocalDecision.Locked
        assertEquals(LockReason.OUTSIDE_WINDOW, decision.reason)
        assertEquals(1, decision.nextWindowDayOffset)
        assertEquals(15 * 60, decision.nextWindowMinuteOfDay)
    }

    @Test
    fun defaultWindowEndsJustBeforeMidnight() {
        val lastMinute = LocalScheduleEngine.evaluate(
            dayOfWeek = monday,
            secondOfDay = at(23, 58),
            rules = LocalRules.DEFAULTS,
            usedSecondsToday = 0,
        ) as LocalDecision.Active
        assertEquals(20 * 60L, lastMinute.sessionSeconds)

        val end = LocalScheduleEngine.evaluate(
            dayOfWeek = monday,
            secondOfDay = at(23, 59),
            rules = LocalRules.DEFAULTS,
            usedSecondsToday = 0,
        ) as LocalDecision.Locked
        assertEquals(LockReason.OUTSIDE_WINDOW, end.reason)
        assertEquals(1, end.nextWindowDayOffset)
        assertEquals(1, end.nextWindowMinuteOfDay)
    }

    @Test
    fun nextWindowCrossesIntoTheWeekendTemplate() {
        val rules = LocalRules.DEFAULTS.copy(
            weekdayWindows = listOf(LocalTimeWindow(18 * 60, 19 * 60)),
            weekendWindows = listOf(LocalTimeWindow(9 * 60, 20 * 60)),
        )
        val next = LocalScheduleEngine.nextWindow(
            dayOfWeek = 5,
            secondOfDay = at(20, 0),
            rules = rules,
        )
        assertEquals(1 to 9 * 60, next)
    }

    @Test
    fun noWindowsMeansSetupNotALock() {
        val rules = LocalRules.DEFAULTS.copy(weekdayWindows = emptyList(), weekendWindows = emptyList())
        assertFalse(rules.isUsable)
        assertEquals(
            LocalDecision.SetupRequired,
            LocalScheduleEngine.evaluate(1, at(18, 30), rules, 0),
        )
        assertNull(LocalScheduleEngine.nextWindow(1, at(18, 30), rules))
    }

    @Test
    fun validationRejectsBrokenRules() {
        assertEquals(
            LocalRulesError.TOO_MANY_WINDOWS,
            LocalRules.DEFAULTS.copy(
                weekdayWindows = List(3) { LocalTimeWindow(8 * 60, 9 * 60) },
            ).validationError(),
        )
        assertEquals(
            LocalRulesError.INVALID_WINDOW,
            LocalRules.DEFAULTS.copy(
                weekdayWindows = listOf(LocalTimeWindow(20 * 60, 18 * 60)),
            ).validationError(),
        )
        assertEquals(
            LocalRulesError.OVERLAPPING_WINDOWS,
            LocalRules.DEFAULTS.copy(
                weekdayWindows = listOf(
                    LocalTimeWindow(18 * 60, 19 * 60),
                    LocalTimeWindow(18 * 60 + 30, 20 * 60),
                ),
            ).validationError(),
        )
        assertEquals(
            LocalRulesError.INVALID_LIMITS,
            LocalRules.DEFAULTS.copy(autoDailyLimitMinutes = 0).validationError(),
        )
        assertEquals(
            LocalRulesError.INVALID_LIMITS,
            LocalRules.DEFAULTS.copy(sessionLimitMinutes = LocalRules.MAX_DAILY_MINUTES + 1)
                .validationError(),
        )
    }

    @Test
    fun sessionLimitIsIndependentOfTheAutomaticDailyQuota() {
        // V10: the automatic quota is 10 minutes, but manual sessions still use their own cap.
        val rules = LocalRules.DEFAULTS.copy(
            autoDailyLimitMinutes = 10,
            sessionLimitMinutes = 20,
        )
        assertEquals(LocalRulesError.NONE, rules.validationError())
        val active = LocalScheduleEngine.evaluate(
            dayOfWeek = monday,
            secondOfDay = at(18, 30),
            rules = rules,
            usedSecondsToday = 0,
        ) as LocalDecision.Active
        assertEquals(10 * 60L, active.sessionSeconds)
        assertEquals(LocalSessionEnd.DAILY_LIMIT, active.endReason)
    }
}
