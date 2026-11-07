package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyGuardSelectorTest {
    private fun policy(
        mode: RemotePolicyMode = RemotePolicyMode.RULE,
        handoff: RemoteHandoffState = RemoteHandoffState.DONE,
        rules: LocalRules = LocalRules.DEFAULTS,
    ) = RemotePolicy(
        mode = mode,
        handoffState = handoff,
        configVersion = 7L,
        appliedVersion = 0L,
        appliedMode = null,
        supportsUnifiedPolicy = true,
        rules = rules,
    )

    @Test
    fun unboundIsInactive() {
        assertEquals(
            PolicyGuardChoice.Inactive,
            PolicyGuardSelector.select(false, policy(), localRulesUsable = true),
        )
    }

    @Test
    fun oldBackendWithoutPolicyFallsBackToRedemption() {
        assertEquals(
            PolicyGuardChoice.CloudRedeem,
            PolicyGuardSelector.select(true, null, localRulesUsable = true),
        )
    }

    @Test
    fun doneRuleWithUsableTemplateUsesRemoteRule() {
        val remote = policy()
        val choice = PolicyGuardSelector.select(true, remote, localRulesUsable = false)
        assertTrue(choice is PolicyGuardChoice.RemoteRule)
        assertEquals(remote, (choice as PolicyGuardChoice.RemoteRule).policy)
    }

    @Test
    fun doneRuleWithBrokenTemplateFallsBackToRedemption() {
        val broken = LocalRules(weekdayWindows = emptyList(), weekendWindows = emptyList())
        assertEquals(
            PolicyGuardChoice.CloudRedeem,
            PolicyGuardSelector.select(true, policy(rules = broken), localRulesUsable = true),
        )
    }

    @Test
    fun pendingKeepsLocalRulesOnlyWhenTheyAreUsable() {
        val pending = policy(handoff = RemoteHandoffState.PENDING)
        assertEquals(
            PolicyGuardChoice.PendingLocalRule,
            PolicyGuardSelector.select(true, pending, localRulesUsable = true),
        )
        assertEquals(
            PolicyGuardChoice.CloudRedeem,
            PolicyGuardSelector.select(true, pending, localRulesUsable = false),
        )
    }

    @Test
    fun redeemModeAlwaysUsesTheRedeemEngine() {
        assertEquals(
            PolicyGuardChoice.CloudRedeem,
            PolicyGuardSelector.select(
                true,
                policy(mode = RemotePolicyMode.REDEEM),
                localRulesUsable = true,
            ),
        )
    }
}
