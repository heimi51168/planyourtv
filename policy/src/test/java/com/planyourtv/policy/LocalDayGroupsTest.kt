package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V42 schema 3: a complete seven-day group schedule with nullable study/entertainment quotas and
 * app scopes ordered study > unlimited > entertainment.
 */
class LocalDayGroupsTest {
    private val allDay = LocalTimeWindow(0, 24 * 60)

    private fun group(
        days: List<Int>,
        studyLimit: Int? = null,
        entertainmentLimit: Int? = null,
        studyWindows: List<LocalTimeWindow> = listOf(allDay),
        entertainmentWindows: List<LocalTimeWindow> = listOf(allDay),
    ) = LocalDayGroup(
        days = days,
        studyWindows = studyWindows,
        studyDailyLimitMinutes = studyLimit,
        entertainmentWindows = entertainmentWindows,
        entertainmentDailyLimitMinutes = entertainmentLimit,
    )

    private fun rules(
        groups: List<LocalDayGroup> = listOf(
            group(listOf(1, 2, 3, 4, 5), entertainmentLimit = 60),
            group(listOf(6, 7), entertainmentLimit = 120),
        ),
    ) = LocalRules(dayGroups = groups, restMinutes = 5, sessionScreenOffBufferMinutes = 20)

    @Test
    fun everyWeekdayBelongsToExactlyOneGroup() {
        val three = listOf(
            group(listOf(5, 1, 2), entertainmentLimit = 30),
            group(listOf(3, 4), entertainmentLimit = 45),
            group(listOf(6, 7), entertainmentLimit = 90),
        )

        val rules = rules(three).normalized()

        assertEquals(listOf(1, 2, 5), rules.dayGroups[0].days)
        assertEquals(listOf(3, 4), rules.dayGroups[1].days)
        assertEquals(listOf(6, 7), rules.dayGroups[2].days)
        for (day in 1..7) {
            assertEquals(1, rules.dayGroups.count { it.includes(day) })
        }
        assertEquals(45, rules.dailyLimitFor(3))
        assertEquals(90, rules.dailyLimitFor(7))
        assertEquals(LocalRulesError.NONE, rules.validationError())
    }

    @Test
    fun missingDuplicateOrOverlappingDaysAreRejected() {
        val missing = rules(listOf(group(listOf(1, 2, 3, 4, 5), entertainmentLimit = 60)))
        assertEquals(LocalRulesError.INVALID_DAY_GROUPS, missing.validationError())

        val duplicateInside = rules(
            listOf(group(listOf(1, 1, 2, 3, 4, 5, 6, 7), entertainmentLimit = 60)),
        )
        assertEquals(LocalRulesError.INVALID_DAY_GROUPS, duplicateInside.validationError())

        val overlapping = rules(
            listOf(
                group(listOf(1, 2, 3, 4, 5, 6), entertainmentLimit = 60),
                group(listOf(6, 7), entertainmentLimit = 60),
            ),
        )
        assertEquals(LocalRulesError.INVALID_DAY_GROUPS, overlapping.validationError())

        val outOfRange = rules(
            listOf(
                group(listOf(1, 2, 3, 4, 5, 6), entertainmentLimit = 60),
                group(listOf(7, 8), entertainmentLimit = 60),
            ),
        )
        assertEquals(LocalRulesError.INVALID_DAY_GROUPS, outOfRange.validationError())
    }

    @Test
    fun normalizationDropsEmptyGroupsAndOrdersByFirstDay() {
        val messy = rules(
            listOf(
                group(emptyList(), entertainmentLimit = 60),
                group(listOf(6, 7), entertainmentLimit = 120),
                group(listOf(1, 2, 3, 4, 5), entertainmentLimit = 60),
            ),
        ).normalized()

        assertEquals(2, messy.dayGroups.size)
        assertEquals(1, messy.dayGroups[0].firstDay)
        assertEquals(6, messy.dayGroups[1].firstDay)
        assertEquals(LocalRulesError.NONE, messy.validationError())
    }

    @Test
    fun nullIsUnlimitedAndZeroIsAFiniteLock() {
        val unlimited = rules(
            listOf(
                group(listOf(1, 2, 3, 4, 5, 6, 7), studyLimit = null, entertainmentLimit = null),
            ),
        )
        assertNull(unlimited.studyDailyLimitFor(1))
        assertNull(unlimited.entertainmentDailyLimitFor(1))
        assertEquals(LocalRules.MAX_DAILY_MINUTES, unlimited.autoDailyLimitMinutes)
        assertEquals(LocalRulesError.NONE, unlimited.validationError())

        val locked = rules(
            listOf(group(listOf(1, 2, 3, 4, 5, 6, 7), studyLimit = 0, entertainmentLimit = 0)),
        )
        assertEquals(0, locked.studyDailyLimitFor(1))
        assertEquals(0, locked.entertainmentDailyLimitFor(1))
        assertEquals(LocalRulesError.NONE, locked.validationError())

        val negative = rules(
            listOf(group(listOf(1, 2, 3, 4, 5, 6, 7), entertainmentLimit = -1)),
        )
        assertEquals(LocalRulesError.INVALID_LIMITS, negative.validationError())
    }

    @Test
    fun emptyWindowsCloseACategoryAndAllDayStaysOpen() {
        val closedEntertainment = rules(
            listOf(
                group(
                    listOf(1, 2, 3, 4, 5, 6, 7),
                    entertainmentLimit = 60,
                    entertainmentWindows = emptyList(),
                ),
            ),
        )
        assertTrue(closedEntertainment.entertainmentWindowsFor(1).isEmpty())
        // Study is still open, so the schedule stays usable; entertainment is only closed.
        assertTrue(closedEntertainment.isUsable)

        val closedBoth = closedEntertainment.copy(
            dayGroups = closedEntertainment.dayGroups.map { it.copy(studyWindows = emptyList()) },
        )
        assertFalse(closedBoth.hasAnyWindow)
        assertFalse(closedBoth.isUsable)
    }

    @Test
    fun eachCategoryAcceptsOnlyOneWindow() {
        val two = rules(
            listOf(
                group(
                    listOf(1, 2, 3, 4, 5, 6, 7),
                    entertainmentLimit = 60,
                    entertainmentWindows = listOf(
                        LocalTimeWindow(0, 600),
                        LocalTimeWindow(1200, 1440),
                    ),
                ),
            ),
        )
        assertEquals(LocalRulesError.TOO_MANY_WINDOWS, two.validationError())

        val overlap = rules(
            listOf(group(listOf(1, 2, 3, 4, 5, 6, 7), studyWindows = listOf(
                LocalTimeWindow(0, 600),
                LocalTimeWindow(300, 900),
            ))),
        )
        assertEquals(LocalRulesError.TOO_MANY_WINDOWS, overlap.validationError())
    }

    @Test
    fun studyWinsOverUnlimitedWhichWinsOverEntertainment() {
        val rules = rules().copy(
            allowedPackages = setOf("com.example.tv", "com.example.study", "com.example.free"),
            studyPackages = setOf("com.example.study", "com.example.free"),
            unlimitedPackages = setOf("com.example.free", "com.example.tv"),
        )

        val normalized = rules.normalized()

        assertEquals(setOf("com.example.study", "com.example.free"), normalized.studyPackages)
        assertEquals(setOf("com.example.tv"), normalized.unlimitedPackages)
        assertTrue(normalized.allowedPackages.isEmpty())
        assertEquals(emptySet<String>(), normalized.effectiveAllowedPackages)
        assertEquals(setOf("com.example.tv"), normalized.effectiveUnlimitedPackages)
        assertFalse("com.example.tv" in normalized.effectiveTimedPackages)
        assertEquals(LocalRulesError.NONE, normalized.validationError())
    }

    @Test
    fun studyEarnIsAlwaysTheCurrentStudyScope() {
        val rules = rules().copy(
            studyPackages = setOf("com.example.study"),
            studyEarnPackages = setOf("com.example.study", "com.example.old"),
        )
        assertEquals(setOf("com.example.study"), rules.normalized().studyEarnPackages)
    }

    @Test
    fun restAndBufferAreNotSplitByGroup() {
        val rules = rules().copy(restMinutes = 7, sessionScreenOffBufferMinutes = 33)

        assertEquals(7, rules.restFor(1))
        assertEquals(7, rules.restFor(7))
        assertEquals(7, rules.weekendRestMinutes)
        assertEquals(33, rules.screenOffBufferFor(6))
        assertEquals(33, rules.weekendSessionScreenOffBufferMinutes)
    }

    @Test
    fun anInvalidUnlimitedListIsRejected() {
        assertEquals(
            LocalRulesError.INVALID_UNLIMITED_PACKAGES,
            rules().copy(unlimitedPackages = setOf(ALL_VALID_APP)).validationError(),
        )
        assertEquals(
            LocalRulesError.INVALID_UNLIMITED_PACKAGES,
            rules().copy(unlimitedPackages = setOf("com.example free")).validationError(),
        )
    }
}