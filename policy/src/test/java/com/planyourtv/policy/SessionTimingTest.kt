package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimingTest {
    private val start = 1_800_000_000_000L

    @Test
    fun deadlineAddsTheBufferToTheBudget() {
        val deadline = SessionTiming.wallDeadlineEpochMs(
            startedAtEpochMs = start,
            budgetSeconds = 10 * 60L,
            bufferMinutes = 20,
        )
        assertEquals(start + 30 * 60 * 1_000L, deadline)
    }

    @Test
    fun planDeadlineCapsTheBuffer() {
        val planDeadline = start + 12 * 60 * 1_000L
        val deadline = SessionTiming.wallDeadlineEpochMs(
            startedAtEpochMs = start,
            budgetSeconds = 10 * 60L,
            bufferMinutes = 20,
            planDeadlineEpochMs = planDeadline,
        )
        assertEquals(planDeadline, deadline)
    }

    @Test
    fun midnightCapsAPlanWithoutItsOwnDeadline() {
        val nextMidnight = start + 25 * 60 * 1_000L
        val deadline = SessionTiming.wallDeadlineEpochMs(
            startedAtEpochMs = start,
            budgetSeconds = 10 * 60L,
            bufferMinutes = 20,
            nextMidnightEpochMs = nextMidnight,
        )
        assertEquals(nextMidnight, deadline)
    }

    @Test
    fun oneSecondUsedAndTwentyMinutesOffKeepsTheBudget() {
        val deadline = SessionTiming.wallDeadlineEpochMs(start, 10 * 60L, 20)
        val budgetRemaining = 10 * 60L - 1L
        val effective = SessionTiming.effectiveRemainingSeconds(
            budgetRemainingSeconds = budgetRemaining,
            wallDeadlineEpochMs = deadline,
            nowMs = start + 20 * 60 * 1_000L + 1_000L,
        )
        // Wall clock has 10 minutes left, so the budget is still the limiting value.
        assertEquals(budgetRemaining, effective)
    }

    @Test
    fun lateResumeShortensTheRemainingTimeWithoutChangingTheBudget() {
        val deadline = SessionTiming.wallDeadlineEpochMs(start, 10 * 60L, 20)
        val budgetRemaining = 10 * 60L - 1L
        val effective = SessionTiming.effectiveRemainingSeconds(
            budgetRemainingSeconds = budgetRemaining,
            wallDeadlineEpochMs = deadline,
            nowMs = start + 25 * 60 * 1_000L + 1_000L,
        )
        // At 25 minutes off the wall clock has 5 minutes left minus the one used second.
        assertEquals(5 * 60L - 1L, effective)
    }

    @Test
    fun wallRemainingRoundsUpAndNeverGoesNegative() {
        assertEquals(2L, SessionTiming.wallRemainingSeconds(2_001L, 1_000L))
        assertEquals(1L, SessionTiming.wallRemainingSeconds(2_000L, 1_000L))
        assertEquals(0L, SessionTiming.wallRemainingSeconds(2_000L, 2_000L))
        assertEquals(0L, SessionTiming.wallRemainingSeconds(2_000L, 3_000L))
    }

    @Test
    fun effectiveRemainingIsBoundedByBothClocks() {
        assertEquals(
            30L,
            SessionTiming.effectiveRemainingSeconds(60L, start + 30_000L, start),
        )
        assertEquals(
            60L,
            SessionTiming.effectiveRemainingSeconds(60L, start + 90_000L, start),
        )
    }

    @Test
    fun aMissingDeadlineMeansNoWallLimit() {
        assertEquals(
            Long.MAX_VALUE,
            SessionTiming.wallRemainingSeconds(0L, start),
        )
        assertFalse(SessionTiming.hasReachedDeadline(0L, start))
    }

    @Test
    fun deadlineIsReachedExactlyAtTheBoundary() {
        assertFalse(SessionTiming.hasReachedDeadline(start + 1_000L, start + 999L))
        assertTrue(SessionTiming.hasReachedDeadline(start + 1_000L, start + 1_000L))
    }

    @Test
    fun anAlreadyClosedWindowStillYieldsAValidImmediateDeadline() {
        val deadline = SessionTiming.wallDeadlineEpochMs(
            startedAtEpochMs = start,
            budgetSeconds = 60L,
            bufferMinutes = 20,
            planDeadlineEpochMs = start - 1_000L,
        )
        assertEquals(start + 1_000L, deadline)
        assertFalse(SessionTiming.hasReachedDeadline(deadline, start))
    }
}
