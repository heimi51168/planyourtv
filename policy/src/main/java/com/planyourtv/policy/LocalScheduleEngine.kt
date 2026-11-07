package com.planyourtv.policy

/**
 * Why the automatically chosen session is limited; used to decide whether rest follows.
 * [WINDOW_END] only survives for plans written by older builds: a window now gates whether a
 * session may start, not how long the started session runs.
 */
enum class LocalSessionEnd {
    SESSION_LIMIT,
    DAILY_LIMIT,
    WINDOW_END,
}

/**
 * Pure local-rule evaluation. The runtime owns the clock, persistence and the one-second ticker.
 */
sealed interface LocalDecision {
    data object SetupRequired : LocalDecision

    data class Active(
        val sessionSeconds: Long,
        /** The constraint that will end this session first. */
        val endReason: LocalSessionEnd,
        /**
         * Seconds left in the current allowed window at decision time. Informational: the
         * window only decides whether a session may start, so it never caps [sessionSeconds].
         */
        val windowRemainingSeconds: Long,
        val dailyRemainingSeconds: Long,
    ) : LocalDecision

    data class Locked(
        val reason: LockReason,
        /** 0 = today, 1 = tomorrow; -1 when no future window exists. */
        val nextWindowDayOffset: Int = -1,
        /** Minute of day for the next window start; -1 when none. */
        val nextWindowMinuteOfDay: Int = -1,
        val breakRemainingSeconds: Long = 0,
    ) : LocalDecision
}

object LocalScheduleEngine {
    /**
     * @param dayOfWeek 1 = Monday ... 7 = Sunday
     * @param secondOfDay 0 until 86400
     * @param usedSecondsToday already ended sessions for the TV's local date
     * @param breakRemainingSeconds wall-clock rest left; zero when no rest is active
     */
    fun evaluate(
        dayOfWeek: Int,
        secondOfDay: Int,
        rules: LocalRules,
        usedSecondsToday: Long,
        breakRemainingSeconds: Long = 0,
    ): LocalDecision {
        if (!rules.isUsable) return LocalDecision.SetupRequired
        if (breakRemainingSeconds > 0) {
            val next = nextWindow(dayOfWeek, secondOfDay, rules)
            return LocalDecision.Locked(
                reason = LockReason.RESTING,
                nextWindowDayOffset = next?.first ?: -1,
                nextWindowMinuteOfDay = next?.second ?: -1,
                breakRemainingSeconds = breakRemainingSeconds,
            )
        }

        val dailyRemaining = (rules.autoDailyLimitMinutes * 60L - usedSecondsToday).coerceAtLeast(0L)
        if (dailyRemaining <= 0) {
            val next = nextWindow(dayOfWeek, secondOfDay, rules)
            return LocalDecision.Locked(
                reason = LockReason.TIME_UP,
                nextWindowDayOffset = next?.first ?: -1,
                nextWindowMinuteOfDay = next?.second ?: -1,
            )
        }

        val window = rules.windowsFor(dayOfWeek).firstOrNull { it.contains(secondOfDay) }
        if (window == null) {
            val next = nextWindow(dayOfWeek, secondOfDay, rules)
            return LocalDecision.Locked(
                reason = LockReason.OUTSIDE_WINDOW,
                nextWindowDayOffset = next?.first ?: -1,
                nextWindowMinuteOfDay = next?.second ?: -1,
            )
        }

        val windowRemaining = (window.endSecond - secondOfDay).coerceAtLeast(0).toLong()
        val sessionRemaining = rules.sessionLimitMinutes * 60L
        // The window only gates whether a session may start. Once the child starts inside it,
        // the segment runs to its session/daily budget even if the window closes meanwhile.
        val seconds = minOf(sessionRemaining, dailyRemaining)
        val endReason = if (seconds == sessionRemaining) {
            LocalSessionEnd.SESSION_LIMIT
        } else {
            LocalSessionEnd.DAILY_LIMIT
        }
        return LocalDecision.Active(
            sessionSeconds = seconds,
            endReason = endReason,
            windowRemainingSeconds = windowRemaining,
            dailyRemainingSeconds = dailyRemaining,
        )
    }

    /**
     * The next window start after [secondOfDay], including a later window today. Returns
     * (dayOffset, minuteOfDay), or null if every template has no windows.
     */
    fun nextWindow(
        dayOfWeek: Int,
        secondOfDay: Int,
        rules: LocalRules,
    ): Pair<Int, Int>? {
        for (offset in 0..7) {
            val day = ((dayOfWeek - 1 + offset) % 7) + 1
            val windows = rules.windowsFor(day).sortedBy { it.startMinute }
            val candidate = if (offset == 0) {
                windows.firstOrNull { it.startMinute * 60 > secondOfDay }
            } else {
                windows.firstOrNull()
            } ?: continue
            return offset to candidate.startMinute
        }
        return null
    }
}
