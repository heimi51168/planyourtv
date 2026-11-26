package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FruitRoutePlanTest {
    private val date = "2026-11-24"
    private val noon = 12 * 60 * 60

    private fun rules(
        fruit: Boolean = true,
        fruitCount: Int = 1000,
        quotaMinutes: Int? = 60,
        capMinutes: Int = 30,
        windows: List<LocalTimeWindow> = listOf(LocalTimeWindow(0, 24 * 60)),
    ): LocalRules = LocalRules(
        dayGroups = listOf(
            LocalDayGroup(
                days = (1..7).toList(),
                studyWindows = listOf(LocalTimeWindow(0, 24 * 60)),
                studyDailyLimitMinutes = null,
                entertainmentWindows = windows,
                entertainmentDailyLimitMinutes = quotaMinutes,
            ),
        ),
        quizGateEnabled = false,
        studyEarnEnabled = false,
        directStartEnabled = false,
        sessionCapMinutes = capMinutes,
        fruitExchangeEnabled = fruit,
        fruitExchangeDailyLimitCount = fruitCount,
    )

    private fun plan(
        rules: LocalRules,
        fruit: FruitFacts = fruitFacts(),
        usage: RouteUsage? = null,
        usedSeconds: Long = 0L,
        secondOfDay: Int = noon,
        facts: UnlockRouteFacts = UnlockRouteFacts(),
    ): UnlockRoutePlan = UnlockRoutePlanner.plan(
        rules = rules,
        usage = usage,
        usedSeconds = usedSeconds,
        date = date,
        dayOfWeek = 5,
        secondOfDay = secondOfDay,
        facts = facts,
        fruit = fruit,
    )

    private fun fruitFacts(
        balance: Long = 1234L,
        rate: Int = 1,
        selected: Int? = null,
        enabled: Boolean = true,
        present: Boolean = true,
        supported: Boolean = true,
        hostedMax: Int? = null,
        hostedRemaining: Int? = null,
    ) = FruitFacts(
        accountPresent = present,
        accountEnabled = enabled,
        exchangeRateMinutesPerFruit = rate,
        availableFruitHundredths = balance,
        selectedFruits = selected,
        supported = supported,
        hostedMaxFruits = hostedMax,
        hostedRemaining = hostedRemaining,
    )

    @Test
    fun balanceIsFlooredToWholeFruitsAndCappedByTheSessionLimit() {
        val p = plan(rules(), fruitFacts(balance = 1234L))

        assertEquals(UnlockRouteReason.READY, p.fruit.reason)
        assertTrue(p.fruit.available)
        assertEquals(12L * 60L, p.fruit.requestedSeconds)
        assertEquals(12L * 60L, p.fruit.grantSeconds)
        assertEquals(0, p.fruit.used)
        assertEquals(1000, p.fruit.remaining)
    }

    @Test
    fun selectedFruitsProduceExactlyWholeFruitMinutes() {
        val p = plan(rules(), fruitFacts(selected = 3))

        assertTrue(p.fruit.available)
        assertEquals(3L * 60L, p.fruit.requestedSeconds)
        assertEquals(3L * 60L, p.fruit.grantSeconds)
    }

    @Test
    fun zeroAndSubFruitBalancesHaveDistinctReasons() {
        val empty = plan(rules(), fruitFacts(balance = 0L))
        assertEquals(UnlockRouteReason.BALANCE_EMPTY, empty.fruit.reason)
        assertFalse(empty.fruit.available)

        val below = plan(rules(), fruitFacts(balance = 99L))
        assertEquals(UnlockRouteReason.BALANCE_BELOW_ONE, below.fruit.reason)
        assertFalse(below.fruit.available)
    }

    @Test
    fun missingDisabledOrInvalidAccountIsUnavailable() {
        assertEquals(
            UnlockRouteReason.ACCOUNT_DISABLED,
            plan(rules(), fruitFacts(present = false)).fruit.reason,
        )
        assertEquals(
            UnlockRouteReason.ACCOUNT_DISABLED,
            plan(rules(), fruitFacts(enabled = false)).fruit.reason,
        )
        assertEquals(
            UnlockRouteReason.ACCOUNT_DISABLED,
            plan(rules(), fruitFacts(rate = 0)).fruit.reason,
        )
        assertEquals(
            UnlockRouteReason.ACCOUNT_DISABLED,
            plan(rules(), fruitFacts(rate = 61)).fruit.reason,
        )
    }

    @Test
    fun switchOffAndUnsupportedPartitionsHideTheRoute() {
        val off = plan(rules(fruit = false))
        assertFalse(off.fruit.visible)
        assertEquals(UnlockRouteReason.SWITCH_OFF, off.fruit.reason)

        val hosted = plan(rules(), fruitFacts(supported = false))
        assertFalse(hosted.fruit.visible)
        assertEquals(UnlockRouteReason.SWITCH_OFF, hosted.fruit.reason)
    }

    @Test
    fun theSessionCapAndQuotaShrinkTheMaximumFruitCount() {
        val byCap = plan(rules(capMinutes = 2), fruitFacts(balance = 1000L))
        assertEquals(UnlockRouteReason.READY, byCap.fruit.reason)
        assertEquals(2L * 60L, byCap.fruit.grantSeconds)

        val byQuota = plan(
            rules(quotaMinutes = 60),
            fruitFacts(balance = 1000L),
            usedSeconds = 59 * 60L,
        )
        assertEquals(UnlockRouteReason.READY, byQuota.fruit.reason)
        assertEquals(1L * 60L, byQuota.fruit.grantSeconds)

        val exhausted = plan(
            rules(quotaMinutes = 60),
            fruitFacts(balance = 1000L),
            usedSeconds = 60 * 60L,
        )
        assertEquals(UnlockRouteReason.QUOTA_EXHAUSTED, exhausted.fruit.reason)
    }

    @Test
    fun aRateAboveTheSessionCapCannotExchangeOneFruit() {
        val p = plan(rules(capMinutes = 30), fruitFacts(rate = 60, balance = 10_000L))

        assertEquals(UnlockRouteReason.CAP_TOO_SMALL, p.fruit.reason)
        assertFalse(p.fruit.available)
    }

    @Test
    fun theRemainingWindowBoundsTheExchange() {
        // Noon is outside this window.
        val outside = plan(
            rules(windows = listOf(LocalTimeWindow(0, 60))),
            fruitFacts(balance = 1000L),
        )
        assertEquals(UnlockRouteReason.OUTSIDE_WINDOW, outside.fruit.reason)

        // 30 seconds remain in the window, less than one one-minute fruit.
        val tooShort = plan(
            rules(windows = listOf(LocalTimeWindow(11 * 60, 12 * 60))),
            fruitFacts(balance = 1000L),
            secondOfDay = 12 * 60 * 60 - 30,
        )
        assertEquals(UnlockRouteReason.WINDOW_TOO_SHORT, tooShort.fruit.reason)

        // Two minutes remain and the cap is larger, so the wheel stops at two fruits.
        val twoMinutes = plan(
            rules(windows = listOf(LocalTimeWindow(11 * 60, 12 * 60))),
            fruitFacts(balance = 1000L),
            secondOfDay = 12 * 60 * 60 - 120,
        )
        assertEquals(UnlockRouteReason.READY, twoMinutes.fruit.reason)
        assertEquals(2L * 60L, twoMinutes.fruit.grantSeconds)
    }

    @Test
    fun countRulesBeatBalanceReasons() {
        val zero = plan(rules(fruitCount = 0), fruitFacts(balance = 0L))
        assertEquals(UnlockRouteReason.COUNT_ZERO, zero.fruit.reason)

        val used = plan(
            rules(fruitCount = 2),
            fruitFacts(balance = 1000L),
            usage = RouteUsage(date = date, fruitGranted = 2),
        )
        assertEquals(UnlockRouteReason.COUNT_EXHAUSTED, used.fruit.reason)

        val unlimited = plan(
            rules(fruitCount = -1),
            fruitFacts(balance = 1000L),
            usage = RouteUsage(date = date, fruitGranted = 20),
        )
        assertEquals(UnlockRouteReason.READY, unlimited.fruit.reason)
        assertEquals(UnlockRoutePlanner.UNLIMITED_REMAINING, unlimited.fruit.remaining)
    }

    @Test
    fun selectingMoreThanTheRecalculatedMaximumIsRejected() {
        val p = plan(rules(capMinutes = 5), fruitFacts(balance = 1000L, selected = 6))

        assertEquals(UnlockRouteReason.EXCHANGE_TOO_LARGE, p.fruit.reason)
        assertFalse(p.fruit.available)
    }

    @Test
    fun selectedZeroIsRejectedInsteadOfSilentlyAdjusted() {
        val p = plan(rules(), fruitFacts(selected = 0))

        assertEquals(UnlockRouteReason.EXCHANGE_TOO_LARGE, p.fruit.reason)
        assertFalse(p.fruit.available)
    }

    @Test
    fun aDifferentRateShrinksTheWheelWithoutChangingTheBalanceCeiling() {
        val p = plan(
            rules(capMinutes = 30, quotaMinutes = null),
            fruitFacts(balance = 1234L, rate = 3),
        )

        assertEquals(UnlockRouteReason.READY, p.fruit.reason)
        // 12 fruits still fit the balance, but 30 minutes only pay for 10 at 3 minutes each.
        assertEquals(10L * 3L * 60L, p.fruit.requestedSeconds)
        assertEquals(10L * 3L * 60L, p.fruit.grantSeconds)
    }

    @Test
    fun fruitGrantIncrementsOnlyTheFruitCounter() {
        val usage = RouteUsage(date = date, fruitGranted = 2)
            .withGrant(UnlockRoute.FRUIT, date)

        assertEquals(3, usage.fruitGranted)
        assertEquals(0, usage.quizGranted)
        assertEquals(0, usage.studyRewardGranted)
        assertEquals(0, usage.directGranted)
    }

    @Test
    fun theHostedCeilingReplacesTheLocalCapQuotaAndCount() {
        // Every LOCAL ceiling would refuse or shrink this exchange: the count is zero, one
        // minute of quota is left and the session cap only pays for two fruits.
        val p = plan(
            rules(fruitCount = 0, quotaMinutes = 1, capMinutes = 2),
            fruitFacts(balance = 1000L, hostedMax = 5, hostedRemaining = 3),
        )

        assertEquals(UnlockRouteReason.READY, p.fruit.reason)
        assertTrue(p.fruit.available)
        // The orchard already applied balance / single-limit / quota / window to maxFruit.
        assertEquals(5L * 60L, p.fruit.requestedSeconds)
        assertEquals(5L * 60L, p.fruit.grantSeconds)
        assertEquals(3, p.fruit.remaining)
        assertEquals(0, p.fruit.used)
    }

    @Test
    fun theHostedBalanceStillCapsTheCloudCeiling() {
        val p = plan(
            rules(capMinutes = 2),
            fruitFacts(balance = 250L, hostedMax = 5),
        )

        assertEquals(UnlockRouteReason.READY, p.fruit.reason)
        assertEquals(2L * 60L, p.fruit.requestedSeconds)
        assertEquals(2L * 60L, p.fruit.grantSeconds)
    }

    @Test
    fun aHostedRateAboveTheLocalCapIsNotReinterpretedByTheTv() {
        // 20 minutes per fruit would be CAP_TOO_SMALL locally, but the cloud maxFruit already
        // says one fruit is available, and the backend dispatches the same duration.
        val p = plan(
            rules(capMinutes = 10),
            fruitFacts(rate = 20, hostedMax = 1),
        )

        assertEquals(UnlockRouteReason.READY, p.fruit.reason)
        assertEquals(20L * 60L, p.fruit.grantSeconds)
    }

    @Test
    fun theHostedRemainingIsShownEvenWhenTheLocalCountIsExhausted() {
        val p = plan(
            rules(fruitCount = 1),
            fruitFacts(hostedMax = 2, hostedRemaining = 2),
            usage = RouteUsage(date = date, fruitGranted = 1),
        )

        assertEquals(UnlockRouteReason.READY, p.fruit.reason)
        assertEquals(2, p.fruit.remaining)
    }

    @Test
    fun unlimitedHostedRemainingUsesTheUnlimitedProjection() {
        val p = plan(rules(), fruitFacts(hostedMax = 2, hostedRemaining = -1))

        assertEquals(UnlockRouteReason.READY, p.fruit.reason)
        assertEquals(UnlockRoutePlanner.UNLIMITED_REMAINING, p.fruit.remaining)
    }

    @Test
    fun aHostedSelectionAboveTheCloudCeilingIsRejected() {
        val p = plan(
            rules(),
            fruitFacts(balance = 1000L, hostedMax = 5, selected = 6),
        )

        assertEquals(UnlockRouteReason.EXCHANGE_TOO_LARGE, p.fruit.reason)
        assertFalse(p.fruit.available)
    }

    @Test
    fun aZeroHostedBalanceKeepsTheBalanceReason() {
        val empty = plan(rules(), fruitFacts(balance = 0L, hostedMax = 5))
        assertEquals(UnlockRouteReason.BALANCE_EMPTY, empty.fruit.reason)

        val below = plan(rules(), fruitFacts(balance = 99L, hostedMax = 5))
        assertEquals(UnlockRouteReason.BALANCE_BELOW_ONE, below.fruit.reason)
    }

    @Test
    fun theHostedRouteStillRespectsTheSharedWindowAndRestGate() {
        val outside = plan(
            rules(windows = listOf(LocalTimeWindow(0, 60))),
            fruitFacts(hostedMax = 5),
        )
        assertEquals(UnlockRouteReason.OUTSIDE_WINDOW, outside.fruit.reason)

        val resting = plan(
            rules(),
            fruitFacts(hostedMax = 5),
            facts = UnlockRouteFacts(resting = true),
        )
        assertEquals(UnlockRouteReason.RESTING, resting.fruit.reason)
    }

    @Test
    fun theLocalPlanIsUnchangedWhenNoHostedProjectionExists() {
        // The hosted fields stay null in LOCAL mode: every LOCAL reason applies as before.
        val zero = plan(rules(fruitCount = 0), fruitFacts(balance = 1000L))
        assertEquals(UnlockRouteReason.COUNT_ZERO, zero.fruit.reason)

        val cap = plan(rules(capMinutes = 30), fruitFacts(rate = 60, balance = 10_000L))
        assertEquals(UnlockRouteReason.CAP_TOO_SMALL, cap.fruit.reason)
    }
}