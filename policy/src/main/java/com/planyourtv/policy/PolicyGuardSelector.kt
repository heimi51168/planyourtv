package com.planyourtv.policy

/**
 * Which guard a bound cloud TV should run.
 *
 * The cloud policy is the only input: no UI state or network detail leaks into the decision, so
 * it can be unit tested and both the TV facade and future callers agree on fallback behaviour.
 */
sealed interface PolicyGuardChoice {
    /** Not bound (or cloud inactive): no remote guard is implied. */
    data object Inactive : PolicyGuardChoice

    /** Existing redemption engine. Also the safe fallback for an old backend without a policy. */
    data object CloudRedeem : PolicyGuardChoice

    /** Handoff completed and the cloud template is executable. */
    data class RemoteRule(val policy: RemotePolicy) : PolicyGuardChoice

    /** Handoff pending: keep the TV's own rules guarding until migration finishes. */
    data object PendingLocalRule : PolicyGuardChoice
}

object PolicyGuardSelector {
    fun select(
        bound: Boolean,
        policy: RemotePolicy?,
        localRulesUsable: Boolean,
    ): PolicyGuardChoice {
        if (!bound) return PolicyGuardChoice.Inactive
        if (policy == null) return PolicyGuardChoice.CloudRedeem
        if (policy.handoffState == RemoteHandoffState.DONE &&
            policy.mode == RemotePolicyMode.RULE &&
            policy.rulesUsable
        ) {
            return PolicyGuardChoice.RemoteRule(policy)
        }
        if (policy.handoffState == RemoteHandoffState.PENDING && localRulesUsable) {
            return PolicyGuardChoice.PendingLocalRule
        }
        return PolicyGuardChoice.CloudRedeem
    }
}
