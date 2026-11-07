package com.planyourtv.policy

/**
 * A same-day allowed window. Minutes are local wall-clock minutes and the end is exclusive:
 * 18:00-20:00 covers up to, but not including, 20:00.
 */
data class LocalTimeWindow(
    val startMinute: Int,
    val endMinute: Int,
) {
    val startSecond: Int get() = startMinute * 60
    val endSecond: Int get() = endMinute * 60

    fun contains(secondOfDay: Int): Boolean =
        secondOfDay >= startSecond && secondOfDay < endSecond
}

enum class LocalRulesError {
    NONE,
    TOO_MANY_WINDOWS,
    INVALID_WINDOW,
    OVERLAPPING_WINDOWS,
    INVALID_LIMITS,
    INVALID_ALLOWED_PACKAGES,
    INVALID_STUDY_PACKAGES,
}

/**
 * Offline screen-time rules. This class is pure: no Android, clock or locale. The app converts
 * the TV's local date/time into [dayOfWeek] (1 = Monday ... 7 = Sunday) and [secondOfDay].
 *
 * Schema 2 splits the old single app list into two explicit scopes:
 * - [allowedPackages] is the entertainment/timed scope. An explicit empty set means no
 *   entertainment apps; only [ALL_VALID_APP] means every launchable app.
 * - [studyPackages] is the learning scope. It is never timed and never accepts the all-apps
 *   sentinel. An empty set means the TV has no study mode.
 *
 * Schema 1 had no study list and treated an empty entertainment list as "all apps". [normalized]
 * performs that one-way migration; schema 2 empty lists stay empty.
 */
data class LocalRules(
    // A fresh setup allows the whole day; the parent narrows it after choosing the daily
    // quota and app scope.
    val weekdayWindows: List<LocalTimeWindow> = listOf(LocalTimeWindow(1, 23 * 60 + 59)),
    val weekendWindows: List<LocalTimeWindow> = listOf(LocalTimeWindow(1, 23 * 60 + 59)),
    val autoDailyLimitMinutes: Int = 60,
    val sessionLimitMinutes: Int = 20,
    /**
     * Manual is the default activation, and a parent starts every manual segment, so the
     * default rest is zero. Automatic mode has its own ten-minute default; see [AUTO_DEFAULTS].
     */
    val restMinutes: Int = 0,
    /**
     * Wall-clock grace after this session's own budget, used while the screen is off. The
     * deadline is fixed at the first session start, so this is not a pause budget and never
     * adds usable time. The cloud stores it in the existing device-level `device_list.buffer_time`
     * column, which is also the redemption settlement buffer; the TV clamps it for sessions.
     */
    val sessionScreenOffBufferMinutes: Int = DEFAULT_SESSION_BUFFER_MINUTES,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val allowedPackages: Set<String> = setOf(ALL_VALID_APP),
    val studyPackages: Set<String> = emptySet(),
) {
    fun windowsFor(dayOfWeek: Int): List<LocalTimeWindow> =
        if (dayOfWeek in 1..5) weekdayWindows else weekendWindows

    val hasAnyWindow: Boolean get() = weekdayWindows.isNotEmpty() || weekendWindows.isNotEmpty()

    /** Schema 1 stored an empty entertainment list to mean all apps; schema 2 does not. */
    private val migratedAllowedPackages: Set<String>
        get() = if (schemaVersion < CURRENT_SCHEMA_VERSION && allowedPackages.isEmpty()) {
            setOf(ALL_VALID_APP)
        } else {
            allowedPackages
        }

    /** True when the entertainment list is the explicit all-apps sentinel. */
    val allowsAllEntertainmentApps: Boolean
        get() = ALL_VALID_APP in migratedAllowedPackages

    /**
     * Canonical entertainment scope. Empty is preserved: it is a valid explicit "no
     * entertainment apps" scope, not a request to allow everything.
     */
    val effectiveAllowedPackages: Set<String>
        get() = if (allowsAllEntertainmentApps) {
            setOf(ALL_VALID_APP)
        } else {
            migratedAllowedPackages
        }

    /** Study apps are explicit; empty means study mode is unavailable. */
    val effectiveStudyPackages: Set<String>
        get() = studyPackages

    /**
     * Entertainment scope with study packages removed. When the entertainment scope is "all
     * apps", the all-apps sentinel stays in the result and callers must expand it against the
     * installed app list before deciding whether the scope is really empty.
     */
    val effectiveTimedPackages: Set<String>
        get() = effectiveAllowedPackages - studyPackages

    /**
     * Resolve the timed scope against the installed launchable apps. Study apps always win over
     * entertainment apps, even if a stale client put the same package in both lists.
     */
    fun effectiveTimedPackages(installedApps: Set<String>): Set<String> {
        val entertainment = if (allowsAllEntertainmentApps) {
            installedApps
        } else {
            migratedAllowedPackages
        }
        return entertainment - studyPackages
    }

    /** An explicit list is app-scoped; an empty schema 2 list is still an explicit scope. */
    val usesAppSelection: Boolean
        get() = !allowsAllEntertainmentApps

    /** True when study mode can be shown and entered. */
    val hasStudyMode: Boolean
        get() = studyPackages.isNotEmpty()

    /**
     * Migrates schema 1 to schema 2 and canonicalizes the all-apps sentinel.
     *
     * The schema 1 empty-list-as-all rule is applied only here. A schema 2 empty list is
     * deliberately preserved; widening it to all apps would bypass an explicit parent choice.
     */
    fun normalized(): LocalRules {
        val canonicalAllowed = if (allowsAllEntertainmentApps) {
            setOf(ALL_VALID_APP)
        } else {
            migratedAllowedPackages
        }
        return if (schemaVersion == CURRENT_SCHEMA_VERSION &&
            canonicalAllowed == allowedPackages
        ) {
            this
        } else {
            copy(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                allowedPackages = canonicalAllowed,
            )
        }
    }

    fun validationError(): LocalRulesError {
        if (weekdayWindows.size > MAX_WINDOWS_PER_DAY || weekendWindows.size > MAX_WINDOWS_PER_DAY) {
            return LocalRulesError.TOO_MANY_WINDOWS
        }
        for (window in weekdayWindows + weekendWindows) {
            if (window.startMinute !in 0 until 1440 ||
                window.endMinute !in 1..1440 ||
                window.startMinute >= window.endMinute
            ) {
                return LocalRulesError.INVALID_WINDOW
            }
        }
        if (weekdayWindows.overlaps() || weekendWindows.overlaps()) {
            return LocalRulesError.OVERLAPPING_WINDOWS
        }
        // The automatic daily quota and the per-session cap are independent. Manual sessions
        // have no daily quota, so a small automatic quota must not shrink the manual cap.
        if (autoDailyLimitMinutes !in 1..MAX_DAILY_MINUTES ||
            sessionLimitMinutes !in 1..MAX_DAILY_MINUTES ||
            restMinutes !in 0..MAX_REST_MINUTES ||
            sessionScreenOffBufferMinutes !in MIN_SESSION_BUFFER_MINUTES..MAX_SESSION_BUFFER_MINUTES
        ) {
            return LocalRulesError.INVALID_LIMITS
        }
        val packages = effectiveAllowedPackages
        if (packages.size > MAX_ALLOWED_PACKAGES || packages.any(::isInvalidPackage)) {
            return LocalRulesError.INVALID_ALLOWED_PACKAGES
        }
        if (ALL_VALID_APP in studyPackages ||
            studyPackages.size > MAX_ALLOWED_PACKAGES ||
            studyPackages.any(::isInvalidPackage)
        ) {
            return LocalRulesError.INVALID_STUDY_PACKAGES
        }
        return LocalRulesError.NONE
    }

    val isUsable: Boolean
        get() = hasAnyWindow && validationError() == LocalRulesError.NONE

    /**
     * Moves the rest to the new mode's default only while the value still equals the old
     * mode's default. A parent's custom rest is preserved across an activation switch.
     */
    fun withModeDefaultRest(previous: Activation, next: Activation): LocalRules {
        val previousDefault = defaultsFor(previous).restMinutes
        val nextDefault = defaultsFor(next).restMinutes
        return if (restMinutes == previousDefault && previousDefault != nextDefault) {
            copy(restMinutes = nextDefault)
        } else {
            this
        }
    }

    @Deprecated("Use autoDailyLimitMinutes", ReplaceWith("autoDailyLimitMinutes"))
    val dailyLimitMinutes: Int
        get() = autoDailyLimitMinutes

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val MAX_WINDOWS_PER_DAY = 2
        const val MAX_DAILY_MINUTES = 24 * 60
        const val MAX_REST_MINUTES = 24 * 60
        const val MAX_ALLOWED_PACKAGES = 500
        const val MAX_PACKAGE_NAME_LENGTH = 255
        const val DEFAULT_SESSION_BUFFER_MINUTES = 20
        const val MIN_SESSION_BUFFER_MINUTES = 5
        const val MAX_SESSION_BUFFER_MINUTES = 60

        /** Clamps a cloud-supplied buffer to the range this app can enforce safely. */
        fun sanitizeSessionBufferMinutes(value: Int): Int =
            if (value <= 0) {
                DEFAULT_SESSION_BUFFER_MINUTES
            } else {
                value.coerceIn(MIN_SESSION_BUFFER_MINUTES, MAX_SESSION_BUFFER_MINUTES)
            }

        /**
         * Defaults for the default activation (manual). The parent starts each segment, so no
         * built-in break is inserted between them.
         */
        val DEFAULTS = LocalRules()

        /**
         * Defaults used only while automatic mode is the selected activation. Automatic mode
         * starts segments itself, so it waits ten minutes after one ends before offering the
         * next start. Existing rules keep their configured rest; this is a first-use default.
         */
        val AUTO_DEFAULTS = DEFAULTS.copy(restMinutes = 10)

        fun defaultsFor(activation: Activation): LocalRules =
            if (activation == Activation.AUTO) AUTO_DEFAULTS else DEFAULTS
    }
}

private fun isInvalidPackage(pkg: String): Boolean =
    pkg.isBlank() ||
        pkg.length > LocalRules.MAX_PACKAGE_NAME_LENGTH ||
        pkg.any { ch -> ch.isWhitespace() }

private fun List<LocalTimeWindow>.overlaps(): Boolean {
    val sorted = sortedBy { it.startMinute }
    return sorted.zipWithNext().any { (left, right) -> left.endMinute > right.startMinute }
}
