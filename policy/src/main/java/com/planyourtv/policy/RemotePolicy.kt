package com.planyourtv.policy

/**
 * TV-side mirror of `device_policy_config`.
 *
 * The cloud is the source of truth after handoff. The TV keeps this immutable snapshot for
 * offline execution and for the read-only parent-center summary; it never edits it locally.
 */
data class RemotePolicy(
    val mode: RemotePolicyMode,
    val handoffState: RemoteHandoffState,
    val configVersion: Long,
    val appliedVersion: Long,
    val appliedMode: RemotePolicyMode?,
    val supportsUnifiedPolicy: Boolean,
    val rules: LocalRules,
    /** The cached activation that decides the next segment and the lock screen. */
    val activation: Activation = Activation.MANUAL,
    /** Capability bits this TV reported. An old backend does not mirror them yet. */
    val supportsQuizConfig: Boolean = false,
    val supportsCloudQuiz: Boolean = false,
    /**
     * Two independent time-rule sets plus the shared app scope, present only when the backend
     * reports them. [rules] stays the materialized active snapshot for the executor.
     */
    val ruleSets: LocalRuleSets? = null,
) {
    /** Old backends carry a single rule set; the TV then runs explicit single-set mode. */
    val supportsRuleSets: Boolean
        get() = ruleSets != null

    /** Both days are required by the cloud contract; LocalRules itself only needs one. */
    val rulesUsable: Boolean
        get() = rules.validationError() == LocalRulesError.NONE &&
            rules.weekdayWindows.isNotEmpty() &&
            rules.weekendWindows.isNotEmpty()

    val modeApplied: Boolean
        get() = appliedMode == mode

    val versionApplied: Boolean
        get() = appliedVersion == configVersion

    /** The cloud mode/version has not reached the executor yet. */
    val pendingApply: Boolean
        get() = !modeApplied || !versionApplied

    /**
     * Redemption may start only after handoff completed, the cloud mode is REDEEM, and the TV
     * has applied that exact version and mode. The backend enforces the same conditions.
     */
    val readyForRedemption: Boolean
        get() = handoffState == RemoteHandoffState.DONE &&
            mode == RemotePolicyMode.REDEEM &&
            appliedMode == RemotePolicyMode.REDEEM &&
            versionApplied

    val readyForRule: Boolean
        get() = handoffState == RemoteHandoffState.DONE &&
            mode == RemotePolicyMode.RULE &&
            appliedMode == RemotePolicyMode.RULE &&
            versionApplied &&
            rulesUsable
}

enum class RemotePolicyMode {
    RULE,
    REDEEM,
}

enum class RemoteHandoffState {
    PENDING,
    DONE,
}
