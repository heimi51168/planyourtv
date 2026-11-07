package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyConfigRulesTest {
    private fun config(
        recordId: Int = 42,
        total: Long = 1_800,
        cost: Long = 0,
        status: Int = 1,
        window: Long? = 7_200,
    ) = PolicyConfig(
        recordId = recordId,
        totalSeconds = total,
        costSeconds = cost,
        status = status,
        userName = "小明",
        validWindowSeconds = window,
    )

    @Test fun aFreshGrantIsUsableAndPendingShaped() {
        val grant = config()
        assertTrue(grant.usable)
        assertEquals(1_800L, grant.remainingSeconds)
        assertTrue(PolicyConfigRules.isUsable(grant))
        assertFalse(PolicyConfigRules.shouldResume(grant, recordId = 0, started = false))
    }

    @Test fun anExpiredOrEmptyGrantIsNotUsable() {
        assertFalse(config(status = 0).usable)
        assertFalse(config(recordId = -1).usable)
        assertFalse(config(cost = 1_800).usable)
        assertFalse(config(window = 0).usable)
        assertFalse(PolicyConfigRules.isUsable(null))
    }

    @Test fun aStartedOrPartiallyUsedRecordResumesWithoutTheGate() {
        val grant = config(cost = 120)
        assertTrue(PolicyConfigRules.shouldResume(grant, recordId = 42, started = false))
        assertTrue(PolicyConfigRules.shouldResume(config(cost = 0), recordId = 42, started = true))
        assertFalse(PolicyConfigRules.shouldResume(config(cost = 0), recordId = 42, started = false))
    }

    @Test fun remainingNeverRewindsOrExceedsEitherSide() {
        assertEquals(100L, PolicyConfigRules.nextRemaining(100, 300))
        assertEquals(120L, PolicyConfigRules.nextRemaining(300, 120))
        assertEquals(0L, PolicyConfigRules.nextRemaining(-5, 120))
        assertEquals(0L, PolicyConfigRules.nextRemaining(100, -1))
    }

    @Test fun effectiveRemainingMatchesThePhoneMinOfPointsAndWindow() {
        assertEquals(600L, PolicyConfigRules.effectiveRemaining(600, null))
        assertEquals(300L, PolicyConfigRules.effectiveRemaining(600, 300))
        assertEquals(120L, PolicyConfigRules.effectiveRemaining(120, 300))
        assertEquals(0L, PolicyConfigRules.effectiveRemaining(120, 0))
        assertEquals(0L, PolicyConfigRules.effectiveRemaining(-1, 300))
    }
}
