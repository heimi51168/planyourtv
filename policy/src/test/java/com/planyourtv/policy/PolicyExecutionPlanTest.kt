package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyExecutionPlanTest {
    private fun rulePlan(
        id: String = "session-1",
        sessionId: String? = "session-1",
        recordId: Long? = null,
        activation: PolicyActivation = PolicyActivation.AUTO,
        endReason: PolicyEndReason = PolicyEndReason.SESSION_LIMIT,
        budgetSeconds: Long = 600L,
        restAfterSeconds: Long = 600L,
    ) = PolicyExecutionPlan(
        id = id,
        source = PolicyPlanSource.RULE,
        activation = activation,
        endReason = endReason,
        budgetSeconds = budgetSeconds,
        deadlineEpochMs = 1_000L,
        accessMode = AccessMode.SCREEN,
        allowedPackages = emptySet(),
        restAfterSeconds = restAfterSeconds,
        templateVersion = 3L,
        recordId = recordId,
        sessionId = sessionId,
    )

    private fun redeemPlan(
        id: String = "42",
        recordId: Long? = 42L,
        sessionId: String? = null,
        activation: PolicyActivation = PolicyActivation.MANUAL,
        endReason: PolicyEndReason = PolicyEndReason.REDEMPTION_END,
        restAfterSeconds: Long = 0L,
    ) = PolicyExecutionPlan(
        id = id,
        source = PolicyPlanSource.REDEEM,
        activation = activation,
        endReason = endReason,
        budgetSeconds = 900L,
        deadlineEpochMs = null,
        accessMode = AccessMode.APPS,
        allowedPackages = setOf("com.example.video"),
        restAfterSeconds = restAfterSeconds,
        templateVersion = 7L,
        recordId = recordId,
        sessionId = sessionId,
    )

    @Test
    fun rulePlanCarriesSessionIdentity() {
        val plan = rulePlan()
        assertEquals(PolicyPlanSource.RULE, plan.source)
        assertEquals("session-1", plan.sessionId)
        assertEquals(600L, plan.budgetSeconds)
        assertEquals(600L, plan.restAfterSeconds)
    }

    @Test
    fun redemptionPlanCarriesRecordIdentity() {
        val plan = redeemPlan()
        assertEquals(PolicyPlanSource.REDEEM, plan.source)
        assertEquals(42L, plan.recordId)
        assertEquals(setOf("com.example.video"), plan.allowedPackages)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rulePlanCannotCarryRedemptionRecord() {
        rulePlan(recordId = 42L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rulePlanNeedsSessionId() {
        rulePlan(sessionId = null)
    }

    @Test
    fun rulePlanCanRunManualSessions() {
        val plan = rulePlan(activation = PolicyActivation.MANUAL)
        assertEquals(PolicyActivation.MANUAL, plan.activation)
    }

    @Test(expected = IllegalArgumentException::class)
    fun redemptionPlanNeedsRecordId() {
        redeemPlan(recordId = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun redemptionPlanCannotCarryRuleSessionId() {
        redeemPlan(sessionId = "session-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun redemptionPlanMustBeManual() {
        redeemPlan(activation = PolicyActivation.AUTO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun budgetMustBePositive() {
        rulePlan(budgetSeconds = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rulePlanEndsOnARuleLimit() {
        rulePlan(endReason = PolicyEndReason.REDEMPTION_END)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rulePlanCarriesRestOnlyOnSessionLimit() {
        rulePlan(endReason = PolicyEndReason.DAILY_LIMIT, restAfterSeconds = 600L)
    }

    @Test
    fun rulePlanWithoutRestIsValid() {
        val plan = rulePlan(endReason = PolicyEndReason.DAILY_LIMIT, restAfterSeconds = 0L)
        assertEquals(PolicyEndReason.DAILY_LIMIT, plan.endReason)
        assertEquals(0L, plan.restAfterSeconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun redemptionPlanCannotEndOnARuleLimit() {
        redeemPlan(endReason = PolicyEndReason.SESSION_LIMIT)
    }

    @Test(expected = IllegalArgumentException::class)
    fun redemptionPlanCannotCarryRuleRest() {
        redeemPlan(restAfterSeconds = 600L)
    }
}
