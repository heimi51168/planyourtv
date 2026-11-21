package com.planyourtv.policy

/**
 * The manual time-rule set. The app scope stays device-wide in [LocalSharedAppScope]; the
 * schedule, rest and screen-off buffer belong to the set.
 */
data class LocalRuleSet(
    val dayGroups: List<LocalDayGroup> = LocalRules.DEFAULT_DAY_GROUPS,
    /** Direct-start "each viewing" duration; the shared ceiling lives in [sessionCapMinutes]. */
    val sessionLimitMinutes: Int = LocalRules.DEFAULTS.sessionLimitMinutes,
    /** Shared single-session ceiling for all three child unlock routes. */
    val sessionCapMinutes: Int = LocalRules.DEFAULTS.sessionCapMinutes,
    val restMinutes: Int = LocalRules.DEFAULTS.restMinutes,
    val sessionScreenOffBufferMinutes: Int =
        LocalRules.DEFAULTS.sessionScreenOffBufferMinutes,
    val quizGateEnabled: Boolean = LocalRules.DEFAULTS.quizGateEnabled,
    val studyEarnEnabled: Boolean = LocalRules.DEFAULTS.studyEarnEnabled,
    val directStartEnabled: Boolean = LocalRules.DEFAULTS.directStartEnabled,
    val quizDailyLimitCount: Int = LocalRules.DEFAULTS.quizDailyLimitCount,
    val studyRewardDailyLimitCount: Int = LocalRules.DEFAULTS.studyRewardDailyLimitCount,
    val directDailyFreeCount: Int = LocalRules.DEFAULTS.directDailyFreeCount,
) {
    fun applyTo(rules: LocalRules): LocalRules = rules.copy(
        dayGroups = dayGroups,
        sessionLimitMinutes = sessionLimitMinutes,
        sessionCapMinutes = sessionCapMinutes,
        restMinutes = restMinutes,
        sessionScreenOffBufferMinutes = sessionScreenOffBufferMinutes,
        quizGateEnabled = quizGateEnabled,
        studyEarnEnabled = studyEarnEnabled,
        directStartEnabled = directStartEnabled,
        quizDailyLimitCount = quizDailyLimitCount,
        studyRewardDailyLimitCount = studyRewardDailyLimitCount,
        directDailyFreeCount = directDailyFreeCount,
    )

    /**
     * Same acceptance rule as the executor: a complete seven-day schedule plus valid limits.
     * Checked against a materialized snapshot so the local set and its shared app scope stay
     * 1:1 with [LocalRules].
     */
    fun isUsableWith(shared: LocalSharedAppScope): Boolean =
        shared.materialize(this).isUsable

    fun entertainmentWindowsFor(dayOfWeek: Int): List<LocalTimeWindow> =
        dayGroups.firstOrNull { dayOfWeek in it.days }?.entertainmentWindows.orEmpty()

    fun entertainmentDailyLimitFor(dayOfWeek: Int): Int? =
        dayGroups.firstOrNull { dayOfWeek in it.days }?.entertainmentDailyLimitMinutes

    /** Cloud/handoff projections derived from the Monday and Saturday groups. */
    val weekdayWindows: List<LocalTimeWindow> get() = entertainmentWindowsFor(1)

    val weekendWindows: List<LocalTimeWindow> get() = entertainmentWindowsFor(6)

    val autoDailyLimitMinutes: Int
        get() = entertainmentDailyLimitFor(1) ?: LocalRules.MAX_DAILY_MINUTES

    val weekendDailyLimitMinutes: Int
        get() = entertainmentDailyLimitFor(6) ?: LocalRules.MAX_DAILY_MINUTES

    val weekendRestMinutes: Int get() = restMinutes

    val weekendSessionScreenOffBufferMinutes: Int get() = sessionScreenOffBufferMinutes

    companion object {
        fun of(rules: LocalRules): LocalRuleSet = LocalRuleSet(
            dayGroups = rules.dayGroups,
            sessionLimitMinutes = rules.sessionLimitMinutes,
            sessionCapMinutes = rules.sessionCapMinutes,
            restMinutes = rules.restMinutes,
            sessionScreenOffBufferMinutes = rules.sessionScreenOffBufferMinutes,
            quizGateEnabled = rules.quizGateEnabled,
            studyEarnEnabled = rules.studyEarnEnabled,
            directStartEnabled = rules.directStartEnabled,
            quizDailyLimitCount = rules.quizDailyLimitCount,
            studyRewardDailyLimitCount = rules.studyRewardDailyLimitCount,
            directDailyFreeCount = rules.directDailyFreeCount,
        )
    }
}

/**
 * Device-wide app scope. Categories are mutually exclusive with study > unlimited >
 * entertainment; [LocalRules.normalized] enforces that when the editor writes.
 */
data class LocalSharedAppScope(
    val allowedPackages: Set<String> = setOf(ALL_VALID_APP),
    val studyPackages: Set<String> = emptySet(),
    val unlimitedPackages: Set<String> = emptySet(),
    val studyEarnPackages: Set<String> = emptySet(),
    val studyEarnStudyMinutes: Int = LocalRules.DEFAULT_STUDY_EARN_STUDY_MINUTES,
    val studyEarnRewardMinutes: Int = LocalRules.DEFAULT_STUDY_EARN_REWARD_MINUTES,
) {
    fun materialize(ruleSet: LocalRuleSet): LocalRules = ruleSet.applyTo(
        LocalRules(
            allowedPackages = allowedPackages,
            studyPackages = studyPackages,
            unlimitedPackages = unlimitedPackages,
            studyEarnPackages = studyEarnPackages,
            studyEarnStudyMinutes = studyEarnStudyMinutes,
            studyEarnRewardMinutes = studyEarnRewardMinutes,
        ),
    )

    companion object {
        fun of(rules: LocalRules): LocalSharedAppScope = LocalSharedAppScope(
            allowedPackages = rules.effectiveAllowedPackages,
            studyPackages = rules.effectiveStudyPackages,
            unlimitedPackages = rules.effectiveUnlimitedPackages,
            studyEarnPackages = rules.effectiveStudyEarnPackages,
            studyEarnStudyMinutes = rules.studyEarnStudyMinutes,
            studyEarnRewardMinutes = rules.studyEarnRewardMinutes,
        )
    }
}

/**
 * The stored manual time-rule set plus the shared app scope. The executor never sees this
 * object: [rules] materializes it into the existing [LocalRules] shape, so all runtime
 * scheduling, rest and app-scope logic keeps consuming one snapshot.
 *
 * Persisted as schema 3. Documents from older schemas are treated as unconfigured; V42 does not
 * migrate local rules from a previous installation.
 */
data class LocalRuleSets(
    val manual: LocalRuleSet,
    val shared: LocalSharedAppScope,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    fun rules(): LocalRules = shared.materialize(manual).normalized()

    val isUsable: Boolean = manual.isUsableWith(shared)

    /** Writes the editor's materialized rules back into the manual set and app scope. */
    fun withRules(rules: LocalRules): LocalRuleSets {
        val normalized = rules.normalized()
        return copy(
            manual = LocalRuleSet.of(normalized),
            shared = LocalSharedAppScope.of(normalized),
        )
    }

    /**
     * Merges a cloud snapshot with the locally owned configuration.
     *
     * [scoped] is the backend's `memberScopedFields` mask (2026-11-21): the fields the cloud has
     * actually projected for the active orchard member. A field inside the mask takes the cloud
     * value; any other field keeps the local value. `null` means a backend that predates the
     * mask and cannot express these fields at all, so every local value survives.
     *
     * Custom day groups, unlimited apps and the study quota used to be local-only, which is why
     * an unmasked snapshot never overwrote them. The masked snapshot is the contract that makes
     * the uniapp "小果农配置" the authority for exactly the fields it saved.
     */
    fun preservingDeviceFieldsFrom(
        previous: LocalRuleSets?,
        scoped: Set<String>? = null,
    ): LocalRuleSets {
        if (previous == null) return this
        if (scoped == null) {
            return copy(
                manual = manual.copy(
                    dayGroups = previous.manual.dayGroups,
                    // V44 route choices and durations are local-only on a backend without the
                    // member mask. Keep them when a cloud snapshot cannot express them.
                    sessionLimitMinutes = previous.manual.sessionLimitMinutes,
                    sessionCapMinutes = previous.manual.sessionCapMinutes,
                    quizGateEnabled = previous.manual.quizGateEnabled,
                    studyEarnEnabled = previous.manual.studyEarnEnabled,
                    directStartEnabled = previous.manual.directStartEnabled,
                    quizDailyLimitCount = previous.manual.quizDailyLimitCount,
                    studyRewardDailyLimitCount = previous.manual.studyRewardDailyLimitCount,
                    directDailyFreeCount = previous.manual.directDailyFreeCount,
                ),
                shared = shared.copy(
                    unlimitedPackages = previous.shared.unlimitedPackages,
                    studyEarnPackages = previous.shared.studyEarnPackages
                        .intersect(previous.shared.studyPackages),
                    studyEarnStudyMinutes = previous.shared.studyEarnStudyMinutes,
                    studyEarnRewardMinutes = previous.shared.studyEarnRewardMinutes,
                ),
            )
        }
        fun <T> pick(field: String, cloud: T, local: T): T = if (field in scoped) cloud else local
        return copy(
            manual = manual.copy(
                dayGroups = pick(
                    "dayGroups",
                    manual.dayGroups,
                    previous.manual.dayGroups,
                ),
                sessionLimitMinutes = pick(
                    "sessionLimitMinutes",
                    manual.sessionLimitMinutes,
                    previous.manual.sessionLimitMinutes,
                ),
                sessionCapMinutes = pick(
                    "sessionCapMinutes",
                    manual.sessionCapMinutes,
                    previous.manual.sessionCapMinutes,
                ),
                restMinutes = pick(
                    "restMinutes",
                    manual.restMinutes,
                    previous.manual.restMinutes,
                ),
                sessionScreenOffBufferMinutes = pick(
                    "sessionScreenOffBufferMinutes",
                    manual.sessionScreenOffBufferMinutes,
                    previous.manual.sessionScreenOffBufferMinutes,
                ),
                quizGateEnabled = pick(
                    "quizGateEnabled",
                    manual.quizGateEnabled,
                    previous.manual.quizGateEnabled,
                ),
                studyEarnEnabled = pick(
                    "studyEarnEnabled",
                    manual.studyEarnEnabled,
                    previous.manual.studyEarnEnabled,
                ),
                directStartEnabled = pick(
                    "directStartEnabled",
                    manual.directStartEnabled,
                    previous.manual.directStartEnabled,
                ),
                quizDailyLimitCount = pick(
                    "quizDailyLimitCount",
                    manual.quizDailyLimitCount,
                    previous.manual.quizDailyLimitCount,
                ),
                studyRewardDailyLimitCount = pick(
                    "studyRewardDailyLimitCount",
                    manual.studyRewardDailyLimitCount,
                    previous.manual.studyRewardDailyLimitCount,
                ),
                directDailyFreeCount = pick(
                    "directDailyFreeCount",
                    manual.directDailyFreeCount,
                    previous.manual.directDailyFreeCount,
                ),
            ),
            shared = shared.copy(
                allowedPackages = pick(
                    "allowedPackages",
                    shared.allowedPackages,
                    previous.shared.allowedPackages,
                ),
                studyPackages = pick(
                    "studyPackages",
                    shared.studyPackages,
                    previous.shared.studyPackages,
                ),
                unlimitedPackages = pick(
                    "unlimitedPackages",
                    shared.unlimitedPackages,
                    previous.shared.unlimitedPackages,
                ),
                studyEarnPackages = pick(
                    "studyEarnPackages",
                    shared.studyEarnPackages.intersect(shared.studyPackages),
                    previous.shared.studyEarnPackages,
                ),
                studyEarnStudyMinutes = pick(
                    "studyEarnStudyMinutes",
                    shared.studyEarnStudyMinutes,
                    previous.shared.studyEarnStudyMinutes,
                ),
                studyEarnRewardMinutes = pick(
                    "studyEarnRewardMinutes",
                    shared.studyEarnRewardMinutes,
                    previous.shared.studyEarnRewardMinutes,
                ),
            ),
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3

        fun from(rules: LocalRules): LocalRuleSets {
            val normalized = rules.normalized()
            return LocalRuleSets(
                manual = LocalRuleSet.of(normalized),
                shared = LocalSharedAppScope.of(normalized),
            )
        }
    }
}