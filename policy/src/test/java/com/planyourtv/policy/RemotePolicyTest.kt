package com.planyourtv.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePolicyTest {
    private val rules = LocalRules(
        weekdayWindows = listOf(LocalTimeWindow(18 * 60, 20 * 60)),
        weekendWindows = listOf(LocalTimeWindow(9 * 60, 20 * 60)),
        autoDailyLimitMinutes = 60,
        sessionLimitMinutes = 20,
        restMinutes = 10,
    )

    private fun policy(
        mode: RemotePolicyMode = RemotePolicyMode.REDEEM,
        handoff: RemoteHandoffState = RemoteHandoffState.DONE,
        configVersion: Long = 3L,
        appliedVersion: Long = 3L,
        appliedMode: RemotePolicyMode? = mode,
        rules: LocalRules = this.rules,
    ) = RemotePolicy(
        mode = mode,
        handoffState = handoff,
        configVersion = configVersion,
        appliedVersion = appliedVersion,
        appliedMode = appliedMode,
        supportsUnifiedPolicy = true,
        rules = rules,
    )

    @Test
    fun redemptionRequiresAppliedRedeemVersion() {
        assertTrue(policy().readyForRedemption)
        assertFalse(policy(appliedMode = null).readyForRedemption)
        assertFalse(policy(appliedMode = RemotePolicyMode.RULE).readyForRedemption)
        assertFalse(policy(appliedVersion = 2L).readyForRedemption)
        assertFalse(policy(handoff = RemoteHandoffState.PENDING).readyForRedemption)
        assertFalse(policy(mode = RemotePolicyMode.RULE).readyForRedemption)
    }

    @Test
    fun ruleModeRequiresBothDayWindowsAndAppliedVersion() {
        assertTrue(policy(mode = RemotePolicyMode.RULE).readyForRule)
        assertFalse(policy(mode = RemotePolicyMode.RULE, appliedMode = null).readyForRule)
        assertFalse(policy(mode = RemotePolicyMode.RULE, appliedVersion = 1L).readyForRule)
    }

    @Test
    fun emptyWeekendWindowIsNotUsableForRemoteRules() {
        val weekdayOnly = rules.copy(weekendWindows = emptyList())
        val remote = policy(mode = RemotePolicyMode.RULE, rules = weekdayOnly)
        assertFalse(remote.rulesUsable)
        assertFalse(remote.readyForRule)
    }

    @Test
    fun pendingApplyTracksModeAndVersion() {
        assertFalse(policy().pendingApply)
        assertTrue(policy(appliedMode = null).pendingApply)
        assertTrue(policy(appliedVersion = 1L).pendingApply)
    }
}
