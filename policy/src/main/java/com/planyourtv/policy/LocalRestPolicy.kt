package com.planyourtv.policy

/**
 * The rest interval after a child entertainment session. Automatic and manual segments share
 * the same rule: whether the segment ended naturally, the child exited early, the parent ended
 * it, or the screen-off deadline expired, the child waits one configured rest before any child
 * entry can start again.
 *
 * A parent takeover is the exception: the parent is about to use the TV, so the child's rest
 * does not begin. A mode switch is also a bookkeeping end: the caller stops the old mode's rest
 * cursor instead of carrying it into the new mode. Midnight/window rollovers add no rest, and a
 * spent daily quota already locks the child on its own.
 */
object LocalRestPolicy {
    fun restSeconds(
        activation: Activation,
        configuredRestMinutes: Int,
        endReason: PolicyEndReason,
        planRestSeconds: Long = 0L,
        skipRest: Boolean = false,
    ): Long {
        if (skipRest) return 0L
        if (endReason == PolicyEndReason.DAILY_LIMIT ||
            endReason == PolicyEndReason.WINDOW_END
        ) {
            return 0L
        }
        val configured = configuredRestMinutes.coerceAtLeast(0).toLong() * 60L
        // A running automatic plan snapshots its rest. Keep that snapshot when present so a
        // configuration edit cannot shorten a rest the child already earned.
        val seconds = if (planRestSeconds > 0L) planRestSeconds else configured
        return when (activation) {
            Activation.AUTO, Activation.MANUAL -> seconds
        }
    }

    /** Epoch millis at which the rest ends; zero when there is no rest. */
    fun restUntilEpochMs(endedAtEpochMs: Long, restSeconds: Long): Long =
        if (endedAtEpochMs <= 0L || restSeconds <= 0L) {
            0L
        } else {
            endedAtEpochMs + restSeconds * 1_000L
        }

    /**
     * Whole display seconds left in the rest, rounded up. The fraction matters: flooring a
     * sub-second remainder to zero would let an automatic tick start the next segment early,
     * and would show the manual entries as usable before the rest really ends.
     */
    fun remainingSeconds(breakUntilEpochMs: Long, nowEpochMs: Long): Long =
        if (breakUntilEpochMs > nowEpochMs) {
            ((breakUntilEpochMs - nowEpochMs + 999L) / 1_000L).coerceAtLeast(1L)
        } else {
            0L
        }
}
