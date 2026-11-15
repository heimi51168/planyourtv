package com.planyourtv.policy.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class QuizRewardResolverTest {
    private val default = profile(easy = 60, medium = 120, hard = 300, max = 600, unlock = 60)
    private val weekday = profile(easy = 30, medium = 90, hard = 180, max = 300, unlock = 0)
    private val weekend = profile(easy = 120, medium = 240, hard = 480, max = 900, unlock = 180)

    @Test
    fun missingDayProfilesUseTheDefaultProfile() {
        val reward = QuizReward(
            mode = QuizRewardMode.DIFFICULTY_SECONDS,
            profiles = QuizRewardProfiles(default = default),
        )
        assertEquals(default, QuizRewardResolver.resolve(reward, QuizDayType.WEEKDAY))
        assertEquals(default, QuizRewardResolver.resolve(reward, QuizDayType.WEEKEND))
    }

    @Test
    fun weekdayAndWeekendEntriesReplaceTheDefaultAsAWhole() {
        val reward = QuizReward(
            mode = QuizRewardMode.DIFFICULTY_SECONDS,
            profiles = QuizRewardProfiles(default = default, weekday = weekday, weekend = weekend),
        )
        assertEquals(weekday, QuizRewardResolver.resolve(reward, QuizDayType.WEEKDAY))
        assertEquals(weekend, QuizRewardResolver.resolve(reward, QuizDayType.WEEKEND))
        assertEquals(900, QuizRewardResolver.resolve(reward, QuizDayType.WEEKEND)!!.maxEarnedSeconds)
    }

    @Test
    fun missingDefaultWithNoDayProfileIsExplicitlyNull() {
        val reward = QuizReward(
            mode = QuizRewardMode.DIFFICULTY_SECONDS,
            profiles = QuizRewardProfiles(default = null, weekday = weekday),
        )
        assertNull(QuizRewardResolver.resolve(reward, QuizDayType.WEEKEND))
    }

    @Test
    fun dayTypeFollowsTheConfiguredWeekendDaysInShanghaiTime() {
        val calendar = QuizCalendar(timezone = "Asia/Shanghai", weekendDays = listOf(6, 7))
        assertEquals(QuizDayType.WEEKDAY, QuizRewardResolver.dayType(calendar, fridayNoon))
        assertEquals(QuizDayType.WEEKEND, QuizRewardResolver.dayType(calendar, saturdayNoon))
        assertEquals(QuizDayType.WEEKEND, QuizRewardResolver.dayType(calendar, sundayNoon))
        assertEquals(QuizDayType.WEEKEND, QuizRewardResolver.dayType(calendar, sundayLateNight))
        assertEquals(QuizDayType.WEEKDAY, QuizRewardResolver.dayType(calendar, mondayMidnight))
    }

    @Test
    fun aCustomWeekendDayIsHonoured() {
        val calendar = QuizCalendar(timezone = "Asia/Shanghai", weekendDays = listOf(5, 6))
        assertEquals(QuizDayType.WEEKEND, QuizRewardResolver.dayType(calendar, fridayNoon))
        assertEquals(QuizDayType.WEEKDAY, QuizRewardResolver.dayType(calendar, sundayNoon))
    }

    @Test
    fun anUnknownTimezoneFailsInsteadOfFallingBackToGmt() {
        val calendar = QuizCalendar(timezone = "Mars/Olympus", weekendDays = listOf(6, 7))
        assertThrows(IllegalStateException::class.java) {
            QuizRewardResolver.dayType(calendar, fridayNoon)
        }
    }

    private fun profile(
        easy: Int,
        medium: Int,
        hard: Int,
        max: Int,
        unlock: Int,
    ): QuizRewardProfile = QuizRewardProfile(
        correctSeconds = QuizDifficultySeconds(easy, medium, hard),
        penaltySeconds = QuizDifficultySeconds(0, 0, 0),
        maxEarnedSeconds = max,
        minUnlockSeconds = unlock,
    )

    private companion object {
        // 2026-11-13 is a Friday. All values are Asia/Shanghai (UTC+8).
        const val fridayNoon = 1794542400000L
        const val saturdayNoon = 1794628800000L
        const val sundayNoon = 1794715200000L
        const val sundayLateNight = 1794758399000L
        const val mondayMidnight = 1794758400000L
    }
}
