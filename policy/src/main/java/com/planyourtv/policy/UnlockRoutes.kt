package com.planyourtv.policy

/**
 * The three child unlock paths. They are independent: turning one on never pauses another, and
 * every successful session consumes both its own daily count and the shared entertainment quota.
 */
enum class UnlockRoute {
    QUIZ,
    STUDY_REWARD,
    DIRECT,
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
) {
    fun granted(route: UnlockRoute): Int = when (route) {
        UnlockRoute.QUIZ -> quizGranted
        UnlockRoute.STUDY_REWARD -> studyRewardGranted
        UnlockRoute.DIRECT -> directGranted
    }.coerceAtLeast(0)

    /** Returns counts owned by [day], or a zeroed ledger when this value belongs to another day. */
    fun normalizedFor(day: String): RouteUsage =
        if (date == day) {
            copy(
                quizGranted = quizGranted.coerceAtLeast(0),
                studyRewardGranted = studyRewardGranted.coerceAtLeast(0),
                directGranted = directGranted.coerceAtLeast(0),
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
        }
    }
}

/** Runtime facts that cannot be derived from the pure rules (rest cursor and a busy gate). */
data class UnlockRouteFacts(
    val resting: Boolean = false,
    val busy: Boolean = false,
)

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
    /** `null` means the day has no entertainment quota limit. */
    val quotaRemainingSeconds: Long?,
    val capSeconds: Long,
) {
    val decisions: List<UnlockRouteDecision>
        get() = listOf(quiz, studyReward, direct)

    fun decision(route: UnlockRoute): UnlockRouteDecision = when (route) {
        UnlockRoute.QUIZ -> quiz
        UnlockRoute.STUDY_REWARD -> studyReward
        UnlockRoute.DIRECT -> direct
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
    ): UnlockRoutePlan {
        val today = (usage ?: RouteUsage(date)).normalizedFor(date)
        val capSeconds = rules.effectiveSessionCapMinutes * 60L
        val quotaLimitMinutes = rules.entertainmentDailyLimitFor(dayOfWeek)
        val quotaRemaining = quotaLimitMinutes?.let {
            (it * 60L - usedSeconds.coerceAtLeast(0L)).coerceAtLeast(0L)
        }
        val windowOpen = rules.entertainmentWindowsFor(dayOfWeek)
            .any { it.contains(secondOfDay) }
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
            quotaRemainingSeconds = quotaRemaining,
            capSeconds = capSeconds,
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