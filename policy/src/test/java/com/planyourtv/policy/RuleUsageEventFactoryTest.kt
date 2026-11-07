package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleUsageEventFactoryTest {
    private fun plan(
        accessMode: AccessMode = AccessMode.SCREEN,
        packages: Set<String> = emptySet(),
        version: Long = 12L,
        budget: Long = 600L,
    ) = PolicyExecutionPlan(
        id = "rule-session-1",
        source = PolicyPlanSource.RULE,
        activation = PolicyActivation.AUTO,
        endReason = PolicyEndReason.SESSION_LIMIT,
        budgetSeconds = budget,
        deadlineEpochMs = null,
        accessMode = accessMode,
        allowedPackages = packages,
        restAfterSeconds = 60L,
        templateVersion = version,
        sessionId = "rule-session-1",
    )

    @Test
    fun startCarriesThePlanSnapshotAndAbsoluteDayTotal() {
        val event = RuleUsageEventFactory.start(
            plan = plan(),
            usageDate = "2026-10-30",
            startedAtEpochMs = 1_000L,
            usedSecondsToday = 120L,
        )
        assertEquals(RuleUsageEventType.START, event.type)
        assertEquals(600L, event.plannedSeconds)
        assertEquals(0L, event.sessionUsedSeconds)
        assertEquals(120L, event.usedSecondsToday)
        assertEquals(12L, event.templateVersion)
        assertEquals(RuleUsageAccessMode.ALL_VALID_APP, event.accessMode)
        assertEquals("", event.appScopeHash)
        assertNull(event.endReason)
    }

    @Test
    fun appsScopeIsHashedWithoutLeakingTheList() {
        val a = RuleUsageEventFactory.progress(
            plan = plan(AccessMode.APPS, setOf("com.b", "com.a")),
            usageDate = "2026-10-30",
            startedAtEpochMs = 1_000L,
            sessionUsedSeconds = 60L,
            usedSecondsToday = 60L,
        )
        val b = RuleUsageEventFactory.progress(
            plan = plan(AccessMode.APPS, setOf("com.a", "com.b")),
            usageDate = "2026-10-30",
            startedAtEpochMs = 1_000L,
            sessionUsedSeconds = 60L,
            usedSecondsToday = 60L,
        )
        assertEquals(RuleUsageAccessMode.APPS, a.accessMode)
        assertEquals(a.appScopeHash, b.appScopeHash)
        assertEquals(64, a.appScopeHash.length)
        assertNotEquals(
            a.appScopeHash,
            RuleUsageEventFactory.progress(
                plan = plan(AccessMode.APPS, setOf("com.c")),
                usageDate = "2026-10-30",
                startedAtEpochMs = 1_000L,
                sessionUsedSeconds = 60L,
                usedSecondsToday = 60L,
            ).appScopeHash,
        )
    }

    @Test
    fun endCarriesReasonAndTime() {
        val event = RuleUsageEventFactory.end(
            plan = plan(),
            usageDate = "2026-10-30",
            startedAtEpochMs = 1_000L,
            endedAtEpochMs = 61_000L,
            sessionUsedSeconds = 60L,
            usedSecondsToday = 60L,
            endReason = PolicyEndReason.SESSION_LIMIT,
        )
        assertEquals(RuleUsageEventType.END, event.type)
        assertEquals(PolicyEndReason.SESSION_LIMIT, event.endReason)
        assertEquals(61_000L, event.endedAtEpochMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun progressCannotExceedThePlanBeyondTheGrace() {
        RuleUsageEventFactory.progress(
            plan = plan(budget = 60L),
            usageDate = "2026-10-30",
            startedAtEpochMs = 1_000L,
            sessionUsedSeconds = 400L,
            usedSecondsToday = 400L,
        )
    }
}
