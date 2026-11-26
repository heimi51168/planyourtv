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

/**
 * One or more weekdays sharing the same study and entertainment schedule. Every day 1..7 must
 * belong to exactly one group; [days] is normalized to a sorted, duplicate-free list.
 *
 * A daily limit of `null` means unlimited. A numeric zero is a real limit of zero minutes and
 * locks that category for the day. This distinction is deliberate: the uniapp contract uses
 * zero for unlimited, but the TV web UI has an explicit unlimited option and maps it to null.
 */
data class LocalDayGroup(
    val days: List<Int> = emptyList(),
    /** Empty closes study for the group; [LocalTimeWindow] 0..1440 means all day. */
    val studyWindows: List<LocalTimeWindow> = emptyList(),
    val studyDailyLimitMinutes: Int? = null,
    /** Empty closes entertainment for the group; 0..1440 means all day. */
    val entertainmentWindows: List<LocalTimeWindow> = emptyList(),
    val entertainmentDailyLimitMinutes: Int? = null,
) {
    val firstDay: Int get() = days.minOrNull() ?: 0

    fun includes(dayOfWeek: Int): Boolean = dayOfWeek in days

    fun normalized(): LocalDayGroup = copy(days = days.distinct().filter { it in 1..7 }.sorted())
}

enum class LocalRulesError {
    NONE,
    TOO_MANY_WINDOWS,
    INVALID_WINDOW,
    OVERLAPPING_WINDOWS,
    INVALID_LIMITS,
    INVALID_ALLOWED_PACKAGES,
    INVALID_STUDY_PACKAGES,
    INVALID_STUDY_EARN,
    INVALID_DAY_GROUPS,
    INVALID_UNLIMITED_PACKAGES,
}

/**
 * Offline screen-time rules. This class is pure: no Android, clock or locale. The app converts
 * the TV's local date/time into [dayOfWeek] (1 = Monday ... 7 = Sunday) and [secondOfDay].
 *
 * Schema 3 makes [dayGroups] the only schedule source. Each group carries its own study and
 * entertainment windows and nullable daily limits (`null` = unlimited). The old weekday/weekend
 * properties at the bottom of this class are derived projections for the cloud/handoff contract;
 * runtime, web and local storage must read the groups.
 *
 * App scopes have a fixed precedence: study > unlimited > entertainment. [normalized] enforces
 * that a package appears in only one category, and every effective timed/study/unlimited view
 * subtracts the higher-priority categories defensively.
 */
data class LocalRules(
    val dayGroups: List<LocalDayGroup> = DEFAULT_DAY_GROUPS,
    /** Rest after a session. One value for every day. */
    val restMinutes: Int = 0,
    /** Wall-clock screen-off grace for a session. One value for every day, 0..60. */
    val sessionScreenOffBufferMinutes: Int = DEFAULT_SESSION_BUFFER_MINUTES,
    /**
     * Duration of one direct-start session. This is the parent's "each viewing" value, not the
     * shared ceiling. [effectiveDirectMinutes] clamps it to [sessionCapMinutes] for old data.
     */
    val sessionLimitMinutes: Int = DEFAULT_DIRECT_SESSION_MINUTES,
    /**
     * Shared ceiling for every child unlock route (quiz, study reward and direct start), in
     * minutes. This is separate from [sessionLimitMinutes]; new writes are 5..120, while local
     * documents accept a wider legacy range.
     */
    val sessionCapMinutes: Int = DEFAULT_SESSION_CAP_MINUTES,
    /** Master switch for the quiz unlock path. Independent of the other two routes. */
    val quizGateEnabled: Boolean = true,
    /**
     * Master switch for study mode and study-to-earn. Off keeps the study/study-earn app lists
     * and minutes stored but hides them from the child. New and reset rules start with only the
     * quiz route enabled; saved rules keep their stored value.
     */
    val studyEarnEnabled: Boolean = false,
    /** Master switch for direct start. Independent of the quiz and study routes. */
    val directStartEnabled: Boolean = false,
    /**
     * Successful quiz-route sessions allowed today: -1 means unlimited, 0 means never, and
     * any positive count up to [MAX_ROUTE_DAILY_COUNT] is the daily cap. The orchard's 0
     * ("unlimited") is projected as -1 by the backend.
     */
    val quizDailyLimitCount: Int = DEFAULT_ROUTE_DAILY_COUNT,
    /** Successful study-reward sessions today: -1 unlimited, 0 never, positive count = cap. */
    val studyRewardDailyLimitCount: Int = DEFAULT_ROUTE_DAILY_COUNT,
    /** Successful direct-start sessions today: -1 unlimited, 0 never, positive count = cap. */
    val directDailyFreeCount: Int = DEFAULT_ROUTE_DAILY_COUNT,
    /**
     * V58 master switch for the fruit-exchange route. The balance, rate and ledger live in the
     * member's fruit account, never in the device rules. Off by default so an upgrade cannot
     * surface an empty dead entry.
     */
    val fruitExchangeEnabled: Boolean = false,
    /** Successful fruit exchanges today: -1 unlimited, 0 never, positive count = cap. */
    val fruitExchangeDailyLimitCount: Int = DEFAULT_ROUTE_DAILY_COUNT,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    /** Entertainment scope. [ALL_VALID_APP] expands to every launchable app except study/unlimited. */
    val allowedPackages: Set<String> = setOf(ALL_VALID_APP),
    /** Learning scope. Every app here is timed by the study schedule. */
    val studyPackages: Set<String> = emptySet(),
    /** Apps that never consume entertainment or study time and are launched directly. */
    val unlimitedPackages: Set<String> = emptySet(),
    /**
     * Study apps that trade focused minutes for entertainment time. Empty disables the feature.
     * Always a subset of [studyPackages]; [normalized] intersects the two so removing a study
     * app cannot leave a dead redemption entry behind.
     */
    val studyEarnPackages: Set<String> = emptySet(),
    /** Focused study minutes needed for one reward. */
    val studyEarnStudyMinutes: Int = DEFAULT_STUDY_EARN_STUDY_MINUTES,
    /** Entertainment minutes granted when a focus round completes. */
    val studyEarnRewardMinutes: Int = DEFAULT_STUDY_EARN_REWARD_MINUTES,
) {
    /** The group owning [dayOfWeek], or null for an invalid/incomplete schedule. */
    fun groupForDay(dayOfWeek: Int): LocalDayGroup? =
        dayGroups.firstOrNull { it.includes(dayOfWeek) }

    /** Entertainment windows for [dayOfWeek]; an empty list closes entertainment. */
    fun entertainmentWindowsFor(dayOfWeek: Int): List<LocalTimeWindow> =
        groupForDay(dayOfWeek)?.entertainmentWindows.orEmpty()

    /** Legacy name for [entertainmentWindowsFor]. */
    fun windowsFor(dayOfWeek: Int): List<LocalTimeWindow> = entertainmentWindowsFor(dayOfWeek)

    /** Study windows for [dayOfWeek]; an empty list closes study. */
    fun studyWindowsFor(dayOfWeek: Int): List<LocalTimeWindow> =
        groupForDay(dayOfWeek)?.studyWindows.orEmpty()

    /** Entertainment quota for [dayOfWeek]; null means unlimited. */
    fun entertainmentDailyLimitFor(dayOfWeek: Int): Int? =
        groupForDay(dayOfWeek)?.entertainmentDailyLimitMinutes

    /** Legacy name for [entertainmentDailyLimitFor]. */
    fun dailyLimitFor(dayOfWeek: Int): Int? = entertainmentDailyLimitFor(dayOfWeek)

    /** Study quota for [dayOfWeek]; null means unlimited. */
    fun studyDailyLimitFor(dayOfWeek: Int): Int? =
        groupForDay(dayOfWeek)?.studyDailyLimitMinutes

    /** Replaces the entertainment windows of the group that owns [dayOfWeek]. */
    fun withEntertainmentWindowsForDay(
        dayOfWeek: Int,
        windows: List<LocalTimeWindow>,
    ): LocalRules = copy(
        dayGroups = dayGroups.map {
            if (it.includes(dayOfWeek)) it.copy(entertainmentWindows = windows) else it
        },
    )

    /** Replaces the study windows of the group that owns [dayOfWeek]. */
    fun withStudyWindowsForDay(
        dayOfWeek: Int,
        windows: List<LocalTimeWindow>,
    ): LocalRules = copy(
        dayGroups = dayGroups.map {
            if (it.includes(dayOfWeek)) it.copy(studyWindows = windows) else it
        },
    )

    /** Rest is a single value; the argument is kept so call sites stay schedule-shaped. */
    fun restFor(dayOfWeek: Int): Int = restMinutes

    /** Screen-off grace is a single value; the argument is kept so call sites stay shaped. */
    fun screenOffBufferFor(dayOfWeek: Int): Int = sessionScreenOffBufferMinutes

    /** At least one study or entertainment window exists across the schedule. */
    val hasAnyWindow: Boolean
        get() = dayGroups.any {
            it.studyWindows.isNotEmpty() || it.entertainmentWindows.isNotEmpty()
        }

    /** True when the entertainment list is the explicit all-apps sentinel. */
    val allowsAllEntertainmentApps: Boolean
        get() = ALL_VALID_APP in allowedPackages

    /**
     * Canonical entertainment scope. Empty is preserved: it is a valid explicit "no
     * entertainment apps" scope, not a request to allow everything. Study and unlimited apps are
     * removed even if an unnormalized caller left them in this list.
     */
    val effectiveAllowedPackages: Set<String>
        get() = if (allowsAllEntertainmentApps) {
            setOf(ALL_VALID_APP)
        } else {
            allowedPackages - studyPackages - effectiveUnlimitedPackages
        }

    /** Study apps are explicit; empty means study mode is unavailable. */
    val effectiveStudyPackages: Set<String>
        get() = studyPackages

    /** Unlimited apps lose to study when the same package appears in both lists. */
    val effectiveUnlimitedPackages: Set<String>
        get() = unlimitedPackages - studyPackages

    /** Redeemable study apps. V45 makes every selected study app participate. */
    val effectiveStudyEarnPackages: Set<String>
        get() = effectiveStudyPackages

    /**
     * Entertainment scope with study and unlimited packages removed. When the entertainment
     * scope is "all apps", the sentinel stays in the result and callers must expand it against
     * the installed app list before deciding whether the scope is really empty.
     */
    val effectiveTimedPackages: Set<String>
        get() = effectiveAllowedPackages - studyPackages - unlimitedPackages

    /**
     * Local-session entertainment scope. All-apps expands against the installed launchers;
     * study and unlimited apps still win over entertainment.
     */
    fun manualEntertainmentPackages(installedApps: Set<String>): Set<String> =
        effectiveTimedPackages(installedApps)

    /**
     * Study apps authorized inside a local session. The feature switch excludes these apps
     * entirely; when it is on, every selected study app participates.
     */
    fun manualStudyRewardPackages(): Set<String> =
        if (studyEarnEnabled) effectiveStudyEarnPackages else emptySet()

    /**
     * Every app a local session may open: configured entertainment, unlimited, and study apps
     * when the study switch authorizes them. It never widens an empty unlimited list to every
     * installed app. Quiz, study reward, direct start and both manual timers share this scope.
     */
    fun sessionVisiblePackages(installedApps: Set<String>): Set<String> =
        manualEntertainmentPackages(installedApps) +
            effectiveUnlimitedPackages +
            manualStudyRewardPackages()

    /** V45 parent Web timer name for [sessionVisiblePackages]; same union, kept for the contract. */
    fun manualVisiblePackages(installedApps: Set<String>): Set<String> =
        sessionVisiblePackages(installedApps)

    /**
     * True when a manual timer has at least one timed app. Unlimited-only scope is valid for
     * launching apps but does not need a countdown, so the parent timer stays disabled.
     */
    fun hasManualTimedApps(installedApps: Set<String>): Boolean =
        manualEntertainmentPackages(installedApps).isNotEmpty() ||
            manualStudyRewardPackages().isNotEmpty()

    /** Backward-compatible summary of the manual timer scope shown by the parent Web page. */
    fun manualTimerScope(installedApps: Set<String>): ManualTimerScope = ManualTimerScope(
        entertainmentCount = manualEntertainmentPackages(installedApps).size,
        entertainmentAll = allowsAllEntertainmentApps,
        unlimitedCount = effectiveUnlimitedPackages.size,
        studyRewardCount = manualStudyRewardPackages().size,
        // The sheet only needs the third chip when the switch is on and there is something to
        // launch; an empty study list would otherwise show a meaningless "0 apps".
        studyRewardIncluded = manualStudyRewardPackages().isNotEmpty(),
    )

    /**
     * Resolve the timed scope against the installed launchable apps. Study and unlimited win
     * over entertainment even if a stale client put the same package in multiple lists.
     */
    fun effectiveTimedPackages(installedApps: Set<String>): Set<String> {
        val entertainment = if (allowsAllEntertainmentApps) {
            installedApps
        } else {
            allowedPackages
        }
        return entertainment - studyPackages - unlimitedPackages
    }

    /** An explicit list is app-scoped; an empty schema 3 list is still an explicit scope. */
    val usesAppSelection: Boolean
        get() = !allowsAllEntertainmentApps

    /** True when study mode can be shown and entered. */
    val hasStudyMode: Boolean
        get() = studyPackages.isNotEmpty()

    /** The shared single-session ceiling actually used at runtime; malformed values fail safe. */
    val effectiveSessionCapMinutes: Int
        get() = sessionCapMinutes.coerceIn(1, MAX_SESSION_CAP_MINUTES)

    /** Direct-start duration after clamping old documents to the shared ceiling. */
    val effectiveDirectMinutes: Int
        get() = minOf(sessionLimitMinutes, effectiveSessionCapMinutes).coerceAtLeast(1)

    /**
     * Study reward after applying the shared ceiling. X only sets the study task; it is an
     * independent input and never caps Y.
     */
    val effectiveStudyRewardMinutes: Int
        get() = minOf(
            studyEarnRewardMinutes,
            effectiveSessionCapMinutes,
        ).coerceAtLeast(1)

    /** The quiz route is independent; direct start no longer pauses it. */
    val quizRouteActive: Boolean
        get() = quizGateEnabled

    /** Study mode may be entered whenever the switch is on and a study app exists. */
    val studyRouteActive: Boolean
        get() = studyEarnEnabled && hasStudyMode

    /** The study route can unlock entertainment: study mode is on and a reward app exists. */
    val studyUnlockRouteActive: Boolean
        get() = studyRouteActive && effectiveStudyEarnPackages.isNotEmpty()

    /** Tapping an entertainment app can start a session without a quiz or study round. */
    val directRouteActive: Boolean
        get() = directStartEnabled

    /** The child may open the fruit wheel; account balance is checked by the planner. */
    val fruitRouteActive: Boolean
        get() = fruitExchangeEnabled

    /** Daily successful-session allowance for [route]. */
    fun dailyLimitFor(route: UnlockRoute): Int = when (route) {
        UnlockRoute.QUIZ -> quizDailyLimitCount
        UnlockRoute.STUDY_REWARD -> studyRewardDailyLimitCount
        UnlockRoute.DIRECT -> directDailyFreeCount
        UnlockRoute.FRUIT -> fruitExchangeDailyLimitCount
    }

    /**
     * At least one route can unlock entertainment. A saved document can still be all-off (for
     * example an old cloud snapshot); the runtime then fails closed and opens nothing.
     */
    val hasUnlockRoute: Boolean
        get() = quizRouteActive || studyUnlockRouteActive || directRouteActive ||
            fruitRouteActive

    /**
     * Canonicalizes the schedule and the three app categories. Invalid schedules are preserved
     * (only empty groups are dropped) so [validationError] can reject them; this method never
     * widens a broken document into a usable one.
     */
    fun normalized(): LocalRules {
        val canonicalGroups = dayGroups
            .map { it.normalized() }
            .filter { it.days.isNotEmpty() }
            .sortedBy { it.firstDay }
        val canonicalStudy = studyPackages
        val canonicalUnlimited = unlimitedPackages - canonicalStudy
        val canonicalAllowed = if (ALL_VALID_APP in allowedPackages) {
            setOf(ALL_VALID_APP)
        } else {
            allowedPackages - canonicalStudy - canonicalUnlimited
        }
        // V45 removed the per-app switches: every selected study app participates. Keep the
        // legacy field for the Web/cloud contract, but never let old subsets split the scope.
        val canonicalStudyEarn = canonicalStudy
        return if (schemaVersion == CURRENT_SCHEMA_VERSION &&
            canonicalGroups == dayGroups &&
            canonicalAllowed == allowedPackages &&
            canonicalUnlimited == unlimitedPackages &&
            canonicalStudyEarn == studyEarnPackages
        ) {
            this
        } else {
            copy(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                dayGroups = canonicalGroups,
                allowedPackages = canonicalAllowed,
                unlimitedPackages = canonicalUnlimited,
                studyEarnPackages = canonicalStudyEarn,
            )
        }
    }

    fun validationError(): LocalRulesError {
        if (dayGroups.isEmpty() || dayGroups.size > MAX_DAY_GROUPS) {
            return LocalRulesError.INVALID_DAY_GROUPS
        }
        val assignedDays = mutableSetOf<Int>()
        for (group in dayGroups) {
            if (group.days.isEmpty() ||
                group.days.any { it !in 1..7 } ||
                group.days.toSet().size != group.days.size
            ) {
                return LocalRulesError.INVALID_DAY_GROUPS
            }
            for (day in group.days) {
                if (!assignedDays.add(day)) return LocalRulesError.INVALID_DAY_GROUPS
            }
            if (group.studyWindows.size > MAX_WINDOWS_PER_GROUP ||
                group.entertainmentWindows.size > MAX_WINDOWS_PER_GROUP
            ) {
                return LocalRulesError.TOO_MANY_WINDOWS
            }
            for (window in group.studyWindows + group.entertainmentWindows) {
                if (window.startMinute !in 0 until 1440 ||
                    window.endMinute !in 1..1440 ||
                    window.startMinute >= window.endMinute
                ) {
                    return LocalRulesError.INVALID_WINDOW
                }
            }
            if (group.studyWindows.overlaps() || group.entertainmentWindows.overlaps()) {
                return LocalRulesError.OVERLAPPING_WINDOWS
            }
            if (!validLimit(group.studyDailyLimitMinutes) ||
                !validLimit(group.entertainmentDailyLimitMinutes)
            ) {
                return LocalRulesError.INVALID_LIMITS
            }
        }
        if (assignedDays.size != 7) return LocalRulesError.INVALID_DAY_GROUPS
        if (sessionLimitMinutes !in 1..MAX_DAILY_MINUTES ||
            sessionCapMinutes !in 1..MAX_SESSION_CAP_MINUTES ||
            quizDailyLimitCount !in -1..MAX_ROUTE_DAILY_COUNT ||
            studyRewardDailyLimitCount !in -1..MAX_ROUTE_DAILY_COUNT ||
            directDailyFreeCount !in -1..MAX_ROUTE_DAILY_COUNT ||
            fruitExchangeDailyLimitCount !in -1..MAX_ROUTE_DAILY_COUNT ||
            restMinutes !in 0..MAX_REST_MINUTES ||
            sessionScreenOffBufferMinutes !in
            MIN_SESSION_BUFFER_MINUTES..MAX_SESSION_BUFFER_MINUTES
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
        if (ALL_VALID_APP in unlimitedPackages ||
            unlimitedPackages.size > MAX_ALLOWED_PACKAGES ||
            unlimitedPackages.any(::isInvalidPackage)
        ) {
            return LocalRulesError.INVALID_UNLIMITED_PACKAGES
        }
        if (studyEarnStudyMinutes !in MIN_STUDY_EARN_MINUTES..MAX_STUDY_EARN_MINUTES ||
            studyEarnRewardMinutes !in MIN_STUDY_EARN_MINUTES..MAX_STUDY_EARN_MINUTES ||
            ALL_VALID_APP in studyEarnPackages ||
            studyEarnPackages.size > MAX_ALLOWED_PACKAGES ||
            studyEarnPackages.any(::isInvalidPackage)
        ) {
            return LocalRulesError.INVALID_STUDY_EARN
        }
        return LocalRulesError.NONE
    }

    val isUsable: Boolean
        get() = hasAnyWindow && validationError() == LocalRulesError.NONE

    // Cloud/handoff projections. These are derived from dayGroups and are never read by the
    // runtime, the web rule editor or the local store. Monday and Saturday identify the two
    // projected groups; unlimited is projected as a full-day quota (1440).
    val weekdayWindows: List<LocalTimeWindow> get() = entertainmentWindowsFor(1)

    val weekendWindows: List<LocalTimeWindow> get() = entertainmentWindowsFor(6)

    val autoDailyLimitMinutes: Int
        get() = projectedLimit(entertainmentDailyLimitFor(1))

    val weekendDailyLimitMinutes: Int
        get() = projectedLimit(entertainmentDailyLimitFor(6))

    /** Rest is no longer split; the weekend projection mirrors the single stored value. */
    val weekendRestMinutes: Int get() = restMinutes

    /** The screen-off grace is no longer split; the weekend projection mirrors the value. */
    val weekendSessionScreenOffBufferMinutes: Int get() = sessionScreenOffBufferMinutes

    @Deprecated("Use autoDailyLimitMinutes", ReplaceWith("autoDailyLimitMinutes"))
    val dailyLimitMinutes: Int
        get() = autoDailyLimitMinutes

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
        const val MAX_WINDOWS_PER_GROUP = 1
        const val MAX_WINDOWS_PER_DAY = MAX_WINDOWS_PER_GROUP
        const val MAX_DAY_GROUPS = 7
        const val MAX_DAILY_MINUTES = 24 * 60
        const val MAX_REST_MINUTES = 24 * 60
        const val MAX_ALLOWED_PACKAGES = 500
        const val MAX_PACKAGE_NAME_LENGTH = 255
        const val DEFAULT_SESSION_BUFFER_MINUTES = 20
        /** Zero disables the screen-off grace; negative values are malformed. */
        const val MIN_SESSION_BUFFER_MINUTES = 0
        const val MAX_SESSION_BUFFER_MINUTES = 60
        const val DEFAULT_DIRECT_SESSION_MINUTES = 20
        /** A generous default; the parent page still accepts any positive minute count. */
        const val DEFAULT_SESSION_CAP_MINUTES = 600
        // 1 minute is the short-session/test floor; normal adjustments still use 5-minute steps.
        const val MIN_WRITE_DIRECT_SESSION_MINUTES = 1
        const val MIN_SESSION_CAP_MINUTES = 1
        /** Storage bound only, not a product limit; the Web accepts a free-form minute count. */
        const val MAX_SESSION_CAP_MINUTES = Int.MAX_VALUE
        /**
         * 默认不限制（2026-11-26 起与果园统一：果园 0 = 不限制，云端投影为 -1）。
         * 旧版默认 1000 的本地存档在 [LocalPolicyStore] 读取时归一化为 -1。
         */
        const val DEFAULT_ROUTE_DAILY_COUNT = -1
        /**
         * Storage bound only, not a product limit. The parent page accepts any non-negative
         * whole number; limiting it to [Int.MAX_VALUE] keeps the JSON, ledger and UI counters
         * inside the Int fields they already use. -1 stays the cloud's "unlimited" sentinel.
         */
        const val MAX_ROUTE_DAILY_COUNT = Int.MAX_VALUE
        const val DEFAULT_STUDY_EARN_STUDY_MINUTES = 10
        const val DEFAULT_STUDY_EARN_REWARD_MINUTES = 5
        const val MIN_STUDY_EARN_MINUTES = 1
        const val MIN_WRITE_STUDY_EARN_MINUTES = 1
        const val MAX_STUDY_EARN_MINUTES = 180
        const val STUDY_EARN_MINUTE_STEP = 5

        /**
         * Default schedule: workdays and weekends share the same all-day 00:00–24:00 window for
         * study and entertainment. Both daily quotas start unlimited so a first-run family is
         * never stopped by a cap they did not choose. The window itself stays unrestricted for
         * the same reason.
         */
        val DEFAULT_DAY_GROUPS: List<LocalDayGroup> = listOf(
            LocalDayGroup(
                days = listOf(1, 2, 3, 4, 5),
                studyWindows = listOf(LocalTimeWindow(0, 24 * 60)),
                studyDailyLimitMinutes = null,
                entertainmentWindows = listOf(LocalTimeWindow(0, 24 * 60)),
                entertainmentDailyLimitMinutes = null,
            ),
            LocalDayGroup(
                days = listOf(6, 7),
                studyWindows = listOf(LocalTimeWindow(0, 24 * 60)),
                studyDailyLimitMinutes = null,
                entertainmentWindows = listOf(LocalTimeWindow(0, 24 * 60)),
                entertainmentDailyLimitMinutes = null,
            ),
        )

        /** Clamps a cloud-supplied buffer; only a negative (malformed) value gets the default. */
        fun sanitizeSessionBufferMinutes(value: Int): Int =
            if (value < 0) {
                DEFAULT_SESSION_BUFFER_MINUTES
            } else {
                value.coerceIn(MIN_SESSION_BUFFER_MINUTES, MAX_SESSION_BUFFER_MINUTES)
            }

        /**
         * Builds a one-week schedule from the flat cloud contract. This is a projection adapter
         * for the read-only hosted snapshot, not local-data migration.
         */
        fun fromLegacyWeekdayWeekend(
            weekdayWindows: List<LocalTimeWindow>,
            weekendWindows: List<LocalTimeWindow>,
            weekdayLimitMinutes: Int?,
            weekendLimitMinutes: Int?,
            studyWindows: List<LocalTimeWindow> = listOf(LocalTimeWindow(0, 24 * 60)),
            studyLimitMinutes: Int? = null,
        ): List<LocalDayGroup> = listOf(
            LocalDayGroup(
                days = listOf(1, 2, 3, 4, 5),
                studyWindows = studyWindows,
                studyDailyLimitMinutes = studyLimitMinutes,
                entertainmentWindows = weekdayWindows,
                entertainmentDailyLimitMinutes = weekdayLimitMinutes,
            ),
            LocalDayGroup(
                days = listOf(6, 7),
                studyWindows = studyWindows,
                studyDailyLimitMinutes = studyLimitMinutes,
                entertainmentWindows = weekendWindows,
                entertainmentDailyLimitMinutes = weekendLimitMinutes,
            ),
        )

        /** Defaults for manual mode. The parent starts each segment. */
        val DEFAULTS = LocalRules()
    }
}

/**
 * Counts shown by the parent Web page for an out-of-plan manual timer. Unlimited apps are part
 * of the launchable scope but never justify a countdown on their own.
 */
data class ManualTimerScope(
    val entertainmentCount: Int,
    val entertainmentAll: Boolean,
    val unlimitedCount: Int,
    val studyRewardCount: Int,
    val studyRewardIncluded: Boolean,
) {
    val hasTimedApps: Boolean get() = entertainmentCount > 0 || studyRewardCount > 0

    val hasApps: Boolean get() = hasTimedApps || unlimitedCount > 0
}

private fun validLimit(value: Int?): Boolean =
    value == null || value in 0..LocalRules.MAX_DAILY_MINUTES

private fun projectedLimit(value: Int?): Int = value ?: LocalRules.MAX_DAILY_MINUTES

private fun isInvalidPackage(pkg: String): Boolean =
    pkg.isBlank() ||
        pkg.length > LocalRules.MAX_PACKAGE_NAME_LENGTH ||
        pkg.any { ch -> ch.isWhitespace() }

private fun List<LocalTimeWindow>.overlaps(): Boolean {
    val sorted = sortedBy { it.startMinute }
    return sorted.zipWithNext().any { (left, right) -> left.endMinute > right.startMinute }
}