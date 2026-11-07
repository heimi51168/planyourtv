package com.planyourtv.policy

/**
 * Fixed wall-clock timing for one rule session.
 *
 * The deadline is computed once, at the first start of the session:
 *
 * ```
 * min(startedAt + (budget + screenOffBuffer), plan deadline, next local midnight)
 * ```
 *
 * While the screen is off the budget stops decreasing, but wall-clock time keeps moving. The
 * buffer is therefore a grace period after this session's own budget, not a pause allowance:
 * returning late shortens the remaining time instead of extending the deadline. Time forfeited
 * this way is never added to the daily ledger; only [budgetRemainingSeconds] is settled.
 *
 * This file is pure. The app supplies all epoch values.
 */
object SessionTiming {
    /**
     * Computes the immutable deadline for a new session. [planDeadlineEpochMs] is an optional
     * hard plan deadline (legacy window-capped plans and redemption plans); new local plans
     * leave it null because a window only gates the session start. [nextMidnightEpochMs]
     * keeps every plan inside the current local day.
     */
    fun wallDeadlineEpochMs(
        startedAtEpochMs: Long,
        budgetSeconds: Long,
        bufferMinutes: Int,
        planDeadlineEpochMs: Long? = null,
        nextMidnightEpochMs: Long? = null,
    ): Long {
        require(startedAtEpochMs > 0L) { "startedAtEpochMs must be positive" }
        require(budgetSeconds > 0L) { "budgetSeconds must be positive" }
        val bufferSeconds = bufferMinutes.coerceAtLeast(0).toLong() * 60L
        var deadline = startedAtEpochMs + (budgetSeconds + bufferSeconds) * 1_000L
        planDeadlineEpochMs?.takeIf { it > 0L }?.let { deadline = minOf(deadline, it) }
        nextMidnightEpochMs?.takeIf { it > 0L }?.let { deadline = minOf(deadline, it) }
        // A plan whose window has already closed must still produce a valid, immediately
        // expired deadline instead of a zero or past-epoch sentinel.
        return deadline.coerceAtLeast(startedAtEpochMs + 1_000L)
    }

    /** Whole seconds until the fixed deadline, rounded up; zero once it has passed. */
    fun wallRemainingSeconds(wallDeadlineEpochMs: Long, nowMs: Long): Long {
        if (wallDeadlineEpochMs <= 0L) return Long.MAX_VALUE
        val delta = wallDeadlineEpochMs - nowMs
        return if (delta <= 0L) 0L else (delta + 999L) / 1_000L
    }

    /**
     * The value shown to the child and used for voice thresholds. It is never written back to
     * the budget: settlement must keep using active-screen points only.
     */
    fun effectiveRemainingSeconds(
        budgetRemainingSeconds: Long,
        wallDeadlineEpochMs: Long,
        nowMs: Long,
    ): Long {
        val budget = budgetRemainingSeconds.coerceAtLeast(0L)
        return minOf(budget, wallRemainingSeconds(wallDeadlineEpochMs, nowMs))
    }

    fun hasReachedDeadline(wallDeadlineEpochMs: Long, nowMs: Long): Boolean =
        wallDeadlineEpochMs > 0L && nowMs >= wallDeadlineEpochMs
}
