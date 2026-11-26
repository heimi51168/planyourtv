package com.planyourtv.policy

/**
 * The child unlock paths. They are independent: turning one on never pauses another, and every
 * successful session consumes both its own daily count and the shared entertainment quota.
 * [FRUIT] reads the LOCAL fruit ledger in LOCAL mode and the HOSTED cloud projection in the
 * hosted rule guard; the two accounts never mix.
 */
enum class UnlockRoute {
    QUIZ,
    STUDY_REWARD,
    DIRECT,
    FRUIT,
}

/**
 * Why a route is visible or unavailable. [SWITCH_OFF] is internal; the TV must not draw a card
 * for it. Every other reason may be rendered as the disabled-card explanation.
 */
enum class UnlockRouteReason {
    READY,
    SWITCH_OFF,
    INVALID_RULES,
    STUDY_NOT_CONFIGURED,
    OUTSIDE_WINDOW,
    RESTING,
    BUSY,
    COUNT_ZERO,
    COUNT_EXHAUSTED,
    QUOTA_EXHAUSTED,
    NOT_EARNED,
    /** The member has no fruit account, the account is disabled, or its rate is invalid. */
    ACCOUNT_DISABLED,
    /** The whole-fruit balance is zero. */
    BALANCE_EMPTY,
    /** The balance is positive but below one whole fruit; TV exchanges whole fruits only. */
    BALANCE_BELOW_ONE,
    /** One fruit at the current rate does not fit in the shared session cap. */
    CAP_TOO_SMALL,
    /** The selected whole-fruit count exceeds the recalculated maximum. */
    EXCHANGE_TOO_LARGE,
    /** The current entertainment window has too little time left for one whole fruit. */
    WINDOW_TOO_SHORT,
}

/**
 * Successful sessions granted today, per route. The date is part of the value so a stale
 * snapshot from yesterday is treated as an empty ledger rather than carrying counts forward.
 */
data class RouteUsage(
    val date: String = "",
    val quizGranted: Int = 0,
    val studyRewardGranted: Int = 0,
    val directGranted: Int = 0,
    val fruitGranted: Int = 0,
) {
    fun granted(route: UnlockRoute): Int = when (route) {
        UnlockRoute.QUIZ -> quizGranted
        UnlockRoute.STUDY_REWARD -> studyRewardGranted
        UnlockRoute.DIRECT -> directGranted
        UnlockRoute.FRUIT -> fruitGranted
    }.coerceAtLeast(0)

    /** Returns counts owned by [day], or a zeroed ledger when this value belongs to another day. */
    fun normalizedFor(day: String): RouteUsage =
        if (date == day) {
            copy(
                quizGranted = quizGranted.coerceAtLeast(0),
                studyRewardGranted = studyRewardGranted.coerceAtLeast(0),
                directGranted = directGranted.coerceAtLeast(0),
                fruitGranted = fruitGranted.coerceAtLeast(0),
            )
        } else {
            RouteUsage(date = day)
        }

    /**
     * The only mutation used after a session is committed. It intentionally does not clamp to
     * the current rule limit: if a parent lowers the limit below the already-used count, the
     * planner reports the route as exhausted instead of rewriting history.
     */
    fun withGrant(route: UnlockRoute, day: String): RouteUsage {
        val base = normalizedFor(day)
        return when (route) {
            UnlockRoute.QUIZ -> base.copy(quizGranted = base.quizGranted + 1)
            UnlockRoute.STUDY_REWARD ->
                base.copy(studyRewardGranted = base.studyRewardGranted + 1)
            UnlockRoute.DIRECT -> base.copy(directGranted = base.directGranted + 1)
            UnlockRoute.FRUIT -> base.copy(fruitGranted = base.fruitGranted + 1)
        }
    }
}

/** Runtime facts that cannot be derived from the pure rules (rest cursor and a busy gate). */
data class UnlockRouteFacts(
    val resting: Boolean = false,
    val busy: Boolean = false,
)

/**
 * The current data source's fruit account projection. [selectedFruits] is `null` while the
 * child has not chosen a value; the decision then describes the maximum whole-fruit exchange.
 * [supported] is false when no account cache exists for the active data source.
 *
 * [hostedMaxFruits] and [hostedRemaining] are set only by the hosted cloud projection. The
 * orchard has already applied its balance / single-capture / quota / window / daily-count
 * ceilings, so those two values replace the planner's LOCAL arithmetic instead of being
 * intersected with unrelated device counters. Both stay null for the LOCAL ledger.
 */
data class FruitFacts(
    val accountPresent: Boolean = false,
    val accountEnabled: Boolean = false,
    val exchangeRateMinutesPerFruit: Int = 0,
    val availableFruitHundredths: Long = 0L,
    val selectedFruits: Int? = null,
    val supported: Boolean = true,
    val hostedMaxFruits: Int? = null,
    val hostedRemaining: Int? = null,
) {
    val wholeFruits: Long
        get() = (availableFruitHundredths / 100L).coerceAtLeast(0L)

    val rateSeconds: Long
        get() = exchangeRateMinutesPerFruit.coerceIn(0, MAX_RATE) * 60L

    val rateValid: Boolean
        get() = exchangeRateMinutesPerFruit in MIN_RATE..MAX_RATE

    companion object {
        const val MIN_RATE = 1
        const val MAX_RATE = 60
        val UNAVAILABLE = FruitFacts(supported = false)
    }
}

/**
 * One route's projection. [grantSeconds] is the V44 `G`: the route's requested seconds clamped
 * by the shared cap and today's remaining entertainment quota. It is still populated when the
 * route is disabled by a count/window/rest/busy condition so the TV can show the potential
 * length next to the reason; it is zero when the quota is zero or the quiz earned nothing.
 */
data class UnlockRouteDecision(
    val route: UnlockRoute,
    val visible: Boolean,
    val available: Boolean,
    val reason: UnlockRouteReason,
    val dailyLimit: Int,
    val used: Int,
    val remaining: Int,
    val requestedSeconds: Long,
    val capSeconds: Long,
    val grantSeconds: Long,
)

/** The single projection consumed by the TV UI, the runtime and the Web snapshot. */
data class UnlockRoutePlan(
    val date: String,
    val quiz: UnlockRouteDecision,
    val studyReward: UnlockRouteDecision,
    val direct: UnlockRouteDecision,
    val fruit: UnlockRouteDecision,
    /** `null` means the day has no entertainment quota limit. */
    val quotaRemainingSeconds: Long?,
    val capSeconds: Long,
) {
    val decisions: List<UnlockRouteDecision>
        get() = listOf(quiz, studyReward, direct, fruit)

    fun decision(route: UnlockRoute): UnlockRouteDecision = when (route) {
        UnlockRoute.QUIZ -> quiz
        UnlockRoute.STUDY_REWARD -> studyReward
        UnlockRoute.DIRECT -> direct
        UnlockRoute.FRUIT -> fruit
    }

    /** At least one switch is on. This includes visible routes that are currently unavailable. */
    val anyEnabled: Boolean
        get() = decisions.any { it.visible }

    /** At least one route can actually establish a session right now. */
    val anyAvailable: Boolean
        get() = decisions.any { it.available }
}

/**
 * Pure V44 planner. Callers supply the TV's local date/time and the runtime facts; the planner
 * owns the only route/count/cap/quota arithmetic. Neither the UI nor the Web page recomputes it.
 */
object UnlockRoutePlanner {
    /** [UnlockRouteDecision.remaining] value meaning "no daily cap" (orchard's count 0). */
    const val UNLIMITED_REMAINING = -1

    fun plan(
        rules: LocalRules,
        usage: RouteUsage? = null,
        usedSeconds: Long = 0L,
        date: String,
        dayOfWeek: Int,
        secondOfDay: Int,
        facts: UnlockRouteFacts = UnlockRouteFacts(),
        /**
         * The quiz result in seconds when known. `null` means "before the quiz", which lets the
         * TV show the maximum possible quiz grant without pretending the result is zero.
         */
        quizEarnedSeconds: Long? = null,
        fruit: FruitFacts = FruitFacts(),
    ): UnlockRoutePlan {
        val today = (usage ?: RouteUsage(date)).normalizedFor(date)
        val capSeconds = rules.effectiveSessionCapMinutes * 60L
        val quotaLimitMinutes = rules.entertainmentDailyLimitFor(dayOfWeek)
        val quotaRemaining = quotaLimitMinutes?.let {
            (it * 60L - usedSeconds.coerceAtLeast(0L)).coerceAtLeast(0L)
        }
        val windows = rules.entertainmentWindowsFor(dayOfWeek)
        val windowOpen = windows.any { it.contains(secondOfDay) }
        // The fruit route promises only time that can still be used before the window closes:
        // an early end never refunds fruit, so a 10-fruit exchange in the last 2 minutes would
        // otherwise charge 10 fruits for 2 usable minutes.
        val windowRemainingSeconds = windows
            .filter { it.contains(secondOfDay) }
            .maxOfOrNull { (it.endSecond - secondOfDay).toLong() }
            ?.coerceAtLeast(0L)
            ?: 0L
        val invalidRules = rules.validationError() != LocalRulesError.NONE

        val quizRequested = quizEarnedSeconds
            ?.coerceAtLeast(0L)
            ?.coerceAtMost(capSeconds)
            ?: capSeconds
        val studyRequested = rules.effectiveStudyRewardMinutes * 60L
        val directRequested = rules.effectiveDirectMinutes * 60L
        val studyConfigured = rules.hasStudyMode &&
            rules.effectiveStudyEarnPackages.isNotEmpty()

        return UnlockRoutePlan(
            date = date,
            quiz = decision(
                route = UnlockRoute.QUIZ,
                visible = rules.quizGateEnabled,
                configured = true,
                requestedSeconds = quizRequested,
                rules = rules,
                usage = today,
                quotaRemainingSeconds = quotaRemaining,
                capSeconds = capSeconds,
                windowOpen = windowOpen,
                facts = facts,
                invalidRules = invalidRules,
            ),
            studyReward = decision(
                route = UnlockRoute.STUDY_REWARD,
                visible = rules.studyEarnEnabled,
                configured = studyConfigured,
                requestedSeconds = studyRequested,
                rules = rules,
                usage = today,
                quotaRemainingSeconds = quotaRemaining,
                capSeconds = capSeconds,
                windowOpen = windowOpen,
                facts = facts,
                invalidRules = invalidRules,
            ),
            direct = decision(
                route = UnlockRoute.DIRECT,
                visible = rules.directStartEnabled,
                configured = true,
                requestedSeconds = directRequested,
                rules = rules,
                usage = today,
                quotaRemainingSeconds = quotaRemaining,
                capSeconds = capSeconds,
                windowOpen = windowOpen,
                facts = facts,
                invalidRules = invalidRules,
            ),
            fruit = fruitDecision(
                visible = rules.fruitExchangeEnabled && fruit.supported,
                rules = rules,
                usage = today,
                fruit = fruit,
                quotaRemainingSeconds = quotaRemaining,
                capSeconds = capSeconds,
                windowOpen = windowOpen,
                windowRemainingSeconds = windowRemainingSeconds,
                facts = facts,
                invalidRules = invalidRules,
            ),
            quotaRemainingSeconds = quotaRemaining,
            capSeconds = capSeconds,
        )
    }

    /**
     * V58 fruit route. The exchange is always a whole number of fruits; [FruitFacts.selectedFruits]
     * is the child's current wheel value (`null` before choosing, which projects the maximum).
     * `grantSeconds` is exactly `fruits * rate * 60`, never a partial minute that the ledger
     * cannot charge for. All four ceilings shrink [maxFruits]; a selected value above it is
     * rejected rather than silently reduced.
     */
    private fun fruitDecision(
        visible: Boolean,
        rules: LocalRules,
        usage: RouteUsage,
        fruit: FruitFacts,
        quotaRemainingSeconds: Long?,
        capSeconds: Long,
        windowOpen: Boolean,
        windowRemainingSeconds: Long,
        facts: UnlockRouteFacts,
        invalidRules: Boolean,
    ): UnlockRouteDecision {
        val dailyLimit = rules.dailyLimitFor(UnlockRoute.FRUIT)
        val used = usage.granted(UnlockRoute.FRUIT)
        val remaining = fruit.hostedRemaining
            ?: if (dailyLimit < 0) UNLIMITED_REMAINING else (dailyLimit - used).coerceAtLeast(0)
        val rateSeconds = fruit.rateSeconds
        val rateValid = fruit.rateValid
        val accountReady = fruit.accountPresent && fruit.accountEnabled && rateValid
        val wholeBalance = fruit.wholeFruits
        val maxByCap = if (rateSeconds > 0L) capSeconds / rateSeconds else 0L
        val maxByQuota = if (quotaRemainingSeconds == null) {
            Int.MAX_VALUE.toLong()
        } else if (rateSeconds > 0L) {
            quotaRemainingSeconds / rateSeconds
        } else {
            0L
        }
        val maxByWindow = if (rateSeconds > 0L) windowRemainingSeconds / rateSeconds else 0L
        val cloudMax = fruit.hostedMaxFruits
        val maxFruits = if (!accountReady || invalidRules) {
            0L
        } else if (cloudMax != null) {
            minOf(wholeBalance, cloudMax.toLong().coerceAtLeast(0L)).coerceAtLeast(0L)
        } else {
            minOf(wholeBalance, maxByCap, maxByQuota, maxByWindow).coerceAtLeast(0L)
        }
        val selected = fruit.selectedFruits?.toLong()
        val requestedFruits = when {
            selected == null -> maxFruits
            selected < 1L -> 0L
            else -> selected
        }
        val requestedSeconds = requestedFruits * rateSeconds
        val grantedFruits = minOf(requestedFruits, maxFruits).coerceAtLeast(0L)
        val grantedSeconds = grantedFruits * rateSeconds
        val reason = when {
            !visible -> UnlockRouteReason.SWITCH_OFF
            invalidRules -> UnlockRouteReason.INVALID_RULES
            !windowOpen -> UnlockRouteReason.OUTSIDE_WINDOW
            facts.resting -> UnlockRouteReason.RESTING
            facts.busy -> UnlockRouteReason.BUSY
            !accountReady -> UnlockRouteReason.ACCOUNT_DISABLED
            cloudMax == null && dailyLimit == 0 -> UnlockRouteReason.COUNT_ZERO
            cloudMax == null && dailyLimit > 0 && remaining == 0 ->
                UnlockRouteReason.COUNT_EXHAUSTED
            fruit.availableFruitHundredths <= 0L -> UnlockRouteReason.BALANCE_EMPTY
            wholeBalance < 1L -> UnlockRouteReason.BALANCE_BELOW_ONE
            cloudMax == null && maxByCap < 1L -> UnlockRouteReason.CAP_TOO_SMALL
            cloudMax == null && maxByWindow < 1L -> UnlockRouteReason.WINDOW_TOO_SHORT
            cloudMax == null && maxByQuota < 1L -> UnlockRouteReason.QUOTA_EXHAUSTED
            selected != null && (selected < 1L || selected > maxFruits) ->
                UnlockRouteReason.EXCHANGE_TOO_LARGE
            maxFruits < 1L -> UnlockRouteReason.EXCHANGE_TOO_LARGE
            else -> UnlockRouteReason.READY
        }
        return UnlockRouteDecision(
            route = UnlockRoute.FRUIT,
            visible = visible,
            available = visible && reason == UnlockRouteReason.READY &&
                grantedSeconds > 0L,
            reason = reason,
            dailyLimit = dailyLimit,
            used = used,
            remaining = remaining,
            requestedSeconds = requestedSeconds,
            capSeconds = capSeconds,
            grantSeconds = grantedSeconds,
        )
    }

    private fun decision(
        route: UnlockRoute,
        visible: Boolean,
        configured: Boolean,
        requestedSeconds: Long,
        rules: LocalRules,
        usage: RouteUsage,
        quotaRemainingSeconds: Long?,
        capSeconds: Long,
        windowOpen: Boolean,
        facts: UnlockRouteFacts,
        invalidRules: Boolean,
    ): UnlockRouteDecision {
        val dailyLimit = rules.dailyLimitFor(route)
        val used = usage.granted(route)
        // -1 is the cloud's "unlimited" projection of the orchard's 0 and never runs out.
        val remaining =
            if (dailyLimit < 0) UNLIMITED_REMAINING else (dailyLimit - used).coerceAtLeast(0)
        val grantSeconds = if (invalidRules) {
            0L
        } else {
            listOfNotNull(
                requestedSeconds.coerceAtLeast(0L),
                capSeconds,
                quotaRemainingSeconds,
            ).minOrNull()?.coerceAtLeast(0L) ?: 0L
        }
        val reason = when {
            !visible -> UnlockRouteReason.SWITCH_OFF
            invalidRules -> UnlockRouteReason.INVALID_RULES
            !configured -> UnlockRouteReason.STUDY_NOT_CONFIGURED
            !windowOpen -> UnlockRouteReason.OUTSIDE_WINDOW
            facts.resting -> UnlockRouteReason.RESTING
            facts.busy -> UnlockRouteReason.BUSY
            dailyLimit == 0 -> UnlockRouteReason.COUNT_ZERO
            dailyLimit > 0 && remaining == 0 -> UnlockRouteReason.COUNT_EXHAUSTED
            quotaRemainingSeconds != null && quotaRemainingSeconds <= 0L ->
                UnlockRouteReason.QUOTA_EXHAUSTED
            requestedSeconds <= 0L -> UnlockRouteReason.NOT_EARNED
            else -> UnlockRouteReason.READY
        }
        return UnlockRouteDecision(
            route = route,
            visible = visible,
            available = visible && reason == UnlockRouteReason.READY && grantSeconds > 0L,
            reason = reason,
            dailyLimit = dailyLimit,
            used = used,
            remaining = remaining,
            requestedSeconds = requestedSeconds,
            capSeconds = capSeconds,
            grantSeconds = grantSeconds,
        )
    }
}