package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalRulesDefaultsTest {
    @Test
    fun defaultsStartFromManualTiming() {
        val rules = LocalRules()
        assertEquals(LocalRules.DEFAULTS, rules)
        assertEquals(0, rules.restMinutes)
        assertEquals(0, rules.weekendRestMinutes)
        assertEquals(setOf(ALL_VALID_APP), rules.allowedPackages)
        assertEquals(emptySet<String>(), rules.studyPackages)
        assertEquals(emptySet<String>(), rules.unlimitedPackages)
    }

    @Test
    fun groupsCarryTheirOwnQuotaAndShareTheSingleRestAndBuffer() {
        val allDay = LocalTimeWindow(0, 24 * 60)
        val rules = LocalRules.DEFAULTS.copy(
            dayGroups = listOf(
                LocalDayGroup(
                    days = listOf(1, 2, 3, 4, 5),
                    studyWindows = listOf(allDay),
                    studyDailyLimitMinutes = 30,
                    entertainmentWindows = listOf(allDay),
                    entertainmentDailyLimitMinutes = 60,
                ),
                LocalDayGroup(
                    days = listOf(6, 7),
                    studyWindows = listOf(allDay),
                    studyDailyLimitMinutes = null,
                    entertainmentWindows = listOf(allDay),
                    entertainmentDailyLimitMinutes = 120,
                ),
            ),
            restMinutes = 5,
            sessionScreenOffBufferMinutes = 20,
        )

        for (weekday in 1..5) {
            assertEquals(60, rules.dailyLimitFor(weekday))
            assertEquals(30, rules.studyDailyLimitFor(weekday))
            assertEquals(5, rules.restFor(weekday))
            assertEquals(20, rules.screenOffBufferFor(weekday))
        }
        for (weekend in 6..7) {
            assertEquals(120, rules.dailyLimitFor(weekend))
            assertEquals(null, rules.studyDailyLimitFor(weekend))
            assertEquals(5, rules.restFor(weekend))
            assertEquals(20, rules.screenOffBufferFor(weekend))
        }
        assertEquals(LocalRulesError.NONE, rules.validationError())
    }

    @Test
    fun firstRunUsesAllDayWithUnlimitedQuotas() {
        val allDay = LocalTimeWindow(0, 24 * 60)
        for (group in LocalRules.DEFAULTS.dayGroups) {
            assertEquals(allDay, group.studyWindows.single())
            assertEquals(allDay, group.entertainmentWindows.single())
            assertNull(group.studyDailyLimitMinutes)
            assertNull(group.entertainmentDailyLimitMinutes)
        }
        assertEquals(listOf(allDay), LocalRules.DEFAULTS.weekdayWindows)
        assertEquals(listOf(allDay), LocalRules.DEFAULTS.weekendWindows)
        assertEquals(LocalRulesError.NONE, LocalRules.DEFAULTS.validationError())
    }
}