package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRestPolicyTest {
    private val tenMinutes = 10

    @Test
    fun bothModesRestAfterANaturalSessionEnd() {
        for (activation in Activation.values()) {
            assertEquals(
                "$activation must rest after a session limit",
                600L,
                LocalRestPolicy.restSeconds(
                    activation = activation,
                    configuredRestMinutes = tenMinutes,
                    endReason = PolicyEndReason.SESSION_LIMIT,
                ),
            )
        }
    }

    @Test
    fun bothModesRestAfterAChildExitsEarly() {
        for (activation in Activation.values()) {
            assertEquals(
                "$activation must not skip rest by exiting",
                600L,
                LocalRestPolicy.restSeconds(
                    activation = activation,
                    configuredRestMinutes = tenMinutes,
                    endReason = PolicyEndReason.USER_EXIT,
                ),
            )
        }
    }

    @Test
    fun bothModesRestAfterAParentStopsTheSession() {
        for (activation in Activation.values()) {
            assertEquals(
                "$activation must rest after the parent ends the segment",
                600L,
                LocalRestPolicy.restSeconds(
                    activation = activation,
                    configuredRestMinutes = tenMinutes,
                    endReason = PolicyEndReason.PARENT_STOP,
                ),
            )
        }
    }

    @Test
    fun bothModesRestAfterTheScreenOffDeadline() {
        for (activation in Activation.values()) {
            assertEquals(
                "$activation must rest after a screen-off timeout",
                600L,
                LocalRestPolicy.restSeconds(
                    activation = activation,
                    configuredRestMinutes = tenMinutes,
                    endReason = PolicyEndReason.SCREEN_OFF_TIMEOUT,
                ),
            )
        }
    }

    @Test
    fun parentTakeoverDoesNotStartTheChildsRest() {
        assertEquals(
            0L,
            LocalRestPolicy.restSeconds(
                activation = Activation.AUTO,
                configuredRestMinutes = tenMinutes,
                endReason = PolicyEndReason.PARENT_STOP,
                skipRest = true,
            ),
        )
    }

    @Test
    fun planRestSnapshotWinsOverALaterConfigurationEdit() {
        assertEquals(
            300L,
            LocalRestPolicy.restSeconds(
                activation = Activation.AUTO,
                configuredRestMinutes = 20,
                endReason = PolicyEndReason.SESSION_LIMIT,
                planRestSeconds = 300L,
            ),
        )
    }

    @Test
    fun zeroConfiguredRestDisablesTheInterval() {
        assertEquals(
            0L,
            LocalRestPolicy.restSeconds(
                activation = Activation.MANUAL,
                configuredRestMinutes = 0,
                endReason = PolicyEndReason.USER_EXIT,
            ),
        )
    }

    @Test
    fun spentDailyQuotaAndLegacyWindowEndDoNotAddRest() {
        for (reason in listOf(PolicyEndReason.DAILY_LIMIT, PolicyEndReason.WINDOW_END)) {
            assertEquals(
                "$reason already locks the child",
                0L,
                LocalRestPolicy.restSeconds(
                    activation = Activation.AUTO,
                    configuredRestMinutes = tenMinutes,
                    endReason = reason,
                ),
            )
        }
    }

    @Test
    fun restDeadlineUsesTheActualSessionEndTimestamp() {
        val endedAt = 1_700_000_000_000L
        assertEquals(endedAt + 600_000L, LocalRestPolicy.restUntilEpochMs(endedAt, 600L))
        assertEquals(0L, LocalRestPolicy.restUntilEpochMs(endedAt, 0L))
        assertEquals(0L, LocalRestPolicy.restUntilEpochMs(0L, 600L))
    }

    @Test
    fun aFractionOfTheLastSecondStillCountsAsRest() {
        val until = 1_700_000_600_000L
        assertEquals(1L, LocalRestPolicy.remainingSeconds(until, until - 1L))
        assertEquals(1L, LocalRestPolicy.remainingSeconds(until, until - 999L))
        assertEquals(1L, LocalRestPolicy.remainingSeconds(until, until - 1_000L))
        assertEquals(2L, LocalRestPolicy.remainingSeconds(until, until - 1_001L))
        assertEquals(0L, LocalRestPolicy.remainingSeconds(until, until))
        assertEquals(0L, LocalRestPolicy.remainingSeconds(until, until + 1L))
    }
}
