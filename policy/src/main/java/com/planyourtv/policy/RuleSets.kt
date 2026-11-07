package com.planyourtv.policy

/**
 * One independent time-rule set. AUTO and MANUAL each own one of these; the parent edits only
 * the set of the active activation. The app scope and study list stay device-wide in
 * [LocalSharedAppScope]; the screen-off buffer now belongs to each set.
 */
data class LocalRuleSet(
    val weekdayWindows: List<LocalTimeWindow> = LocalRules.DEFAULTS.weekdayWindows,
    val weekendWindows: List<LocalTimeWindow> = LocalRules.DEFAULTS.weekendWindows,
    val autoDailyLimitMinutes: Int = LocalRules.DEFAULTS.autoDailyLimitMinutes,
    val sessionLimitMinutes: Int = LocalRules.DEFAULTS.sessionLimitMinutes,
    val restMinutes: Int = LocalRules.DEFAULTS.restMinutes,
    /**
     * Per-mode screen-off grace. Null means the document predates the split; materialization
     * then falls back to the shared value so an upgraded TV keeps its old behaviour without
     * rewriting stored data.
     */
    val sessionScreenOffBufferMinutes: Int? = null,
) {
    fun applyTo(rules: LocalRules): LocalRules = rules.copy(
        weekdayWindows = weekdayWindows,
        weekendWindows = weekendWindows,
        autoDailyLimitMinutes = autoDailyLimitMinutes,
        sessionLimitMinutes = sessionLimitMinutes,
        restMinutes = restMinutes,
        sessionScreenOffBufferMinutes = sessionScreenOffBufferMinutes
            ?: rules.sessionScreenOffBufferMinutes,
    )

    /**
     * Same acceptance rule as the executor: at least one window day plus valid limits. Checked
     * against a materialized snapshot so a local set and its shared app scope stay 1:1 with
     * [LocalRules].
     */
    fun isUsableWith(shared: LocalSharedAppScope): Boolean =
        shared.materialize(this).isUsable

    companion object {
        fun of(rules: LocalRules): LocalRuleSet = LocalRuleSet(
            weekdayWindows = rules.weekdayWindows,
            weekendWindows = rules.weekendWindows,
            autoDailyLimitMinutes = rules.autoDailyLimitMinutes,
            sessionLimitMinutes = rules.sessionLimitMinutes,
            restMinutes = rules.restMinutes,
            sessionScreenOffBufferMinutes = rules.sessionScreenOffBufferMinutes,
        )
    }
}

/**
 * Device-wide app scope plus the legacy shared screen-off buffer. The buffer is mirrored from
 * the active set so old builds (and the `apps` JSON node) keep seeing the value in effect; new
 * reads prefer each set's own buffer and only fall back here when it is absent.
 */
data class LocalSharedAppScope(
    val allowedPackages: Set<String> = setOf(ALL_VALID_APP),
    val studyPackages: Set<String> = emptySet(),
    val sessionScreenOffBufferMinutes: Int = LocalRules.DEFAULT_SESSION_BUFFER_MINUTES,
) {
    fun materialize(ruleSet: LocalRuleSet): LocalRules = ruleSet.applyTo(
        LocalRules(
            allowedPackages = allowedPackages,
            studyPackages = studyPackages,
            sessionScreenOffBufferMinutes = sessionScreenOffBufferMinutes,
        ),
    )

    companion object {
        fun of(rules: LocalRules): LocalSharedAppScope = LocalSharedAppScope(
            allowedPackages = rules.effectiveAllowedPackages,
            studyPackages = rules.effectiveStudyPackages,
            sessionScreenOffBufferMinutes = rules.sessionScreenOffBufferMinutes,
        )
    }
}

/**
 * The two stored time-rule sets plus the shared app scope. The executor never sees this object:
 * [rulesFor] materializes the active activation into the existing [LocalRules] shape, so all
 * runtime scheduling, rest and app-scope logic keeps consuming one snapshot.
 *
 * Persisted as `local_rules_v2`; `local_rules_v1` keeps the active set as a write-through
 * snapshot for old builds and rollback.
 */
data class LocalRuleSets(
    val auto: LocalRuleSet,
    val manual: LocalRuleSet,
    val shared: LocalSharedAppScope,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    fun ruleSetFor(activation: Activation): LocalRuleSet =
        if (activation == Activation.AUTO) auto else manual

    fun rulesFor(activation: Activation): LocalRules =
        shared.materialize(ruleSetFor(activation)).normalized()

    /** True only when both sets are individually usable with the shared app scope. */
    val isUsable: Boolean =
        auto.isUsableWith(shared) && manual.isUsableWith(shared)

    /**
     * Writes the editor's materialized rules back into the active set and refreshes the shared
     * app scope. The inactive set keeps its own windows, limits, rest and buffer untouched.
     */
    fun withActiveRules(rules: LocalRules, activation: Activation): LocalRuleSets {
        val normalized = rules.normalized()
        val updated = LocalRuleSet.of(normalized)
        return copy(
            auto = if (activation == Activation.AUTO) updated else auto,
            manual = if (activation == Activation.MANUAL) updated else manual,
            shared = LocalSharedAppScope.of(normalized),
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        /** One-way v1 upgrade: both sets start from the single stored set, rest preserved. */
        fun from(rules: LocalRules): LocalRuleSets {
            val normalized = rules.normalized()
            val set = LocalRuleSet.of(normalized)
            return LocalRuleSets(
                auto = set,
                manual = set,
                shared = LocalSharedAppScope.of(normalized),
            )
        }

        /**
         * Fresh setup: both sets copy the configured windows, limits and buffer, but the
         * inactive activation keeps its own first-use rest default (manual 0, automatic 10)
         * instead of inheriting the active mode's value. Existing stored documents never go
         * through this overload, so a parent's configured rest is not rewritten.
         */
        fun from(rules: LocalRules, activation: Activation): LocalRuleSets {
            val normalized = rules.normalized()
            val activeSet = LocalRuleSet.of(normalized)
            val inactive = if (activation == Activation.AUTO) Activation.MANUAL else Activation.AUTO
            val inactiveSet = activeSet.copy(
                restMinutes = LocalRules.defaultsFor(inactive).restMinutes,
            )
            return LocalRuleSets(
                auto = if (activation == Activation.AUTO) activeSet else inactiveSet,
                manual = if (activation == Activation.MANUAL) activeSet else inactiveSet,
                shared = LocalSharedAppScope.of(normalized),
            )
        }

        /**
         * Old backend without rule sets: it can only mirror one set. Copy it into both sets
         * explicitly instead of guessing which local set is stale.
         */
        fun mirrorSingleSet(rules: LocalRules): LocalRuleSets = from(rules)
    }
}
