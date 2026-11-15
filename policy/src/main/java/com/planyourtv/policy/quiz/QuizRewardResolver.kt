package com.planyourtv.policy.quiz

import java.util.Calendar
import java.util.TimeZone

enum class QuizDayType {
    WEEKDAY,
    WEEKEND,
}

/**
 * Resolves the reward profile a local round freezes at start time.
 *
 * A weekday or weekend entry is a complete replacement, never a partial merge:
 * a missing key is an invalid document, not an invitation to guess a default.
 * The validator must run before this resolver; [dayType] deliberately throws on
 * an unknown timezone instead of silently falling back to GMT.
 */
object QuizRewardResolver {
    fun resolve(reward: QuizReward, dayType: QuizDayType): QuizRewardProfile? =
        when (dayType) {
            QuizDayType.WEEKDAY -> reward.profiles.weekday ?: reward.profiles.default
            QuizDayType.WEEKEND -> reward.profiles.weekend ?: reward.profiles.default
        }

    /** ISO day of week: 1 = Monday ... 7 = Sunday. */
    fun dayType(calendar: QuizCalendar, epochMs: Long): QuizDayType {
        val zone = TimeZone.getTimeZone(calendar.timezone)
        check(zone.id == calendar.timezone) {
            "unsupported QCD timezone: ${calendar.timezone}"
        }
        val local = Calendar.getInstance(zone)
        local.timeInMillis = epochMs
        val isoDay = when (local.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> error("unreachable Calendar.DAY_OF_WEEK")
        }
        return if (isoDay in calendar.weekendDays) QuizDayType.WEEKEND else QuizDayType.WEEKDAY
    }
}
