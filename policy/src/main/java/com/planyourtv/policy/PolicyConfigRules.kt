package com.planyourtv.policy

/** Pure decisions used by [PolicyRuntime] when a cloud config arrives. */
internal object PolicyConfigRules {
    fun isUsable(config: PolicyConfig?): Boolean = config?.usable == true

    fun isSameRecord(config: PolicyConfig, recordId: Int): Boolean =
        config.recordId == recordId

    /** A record resumes without the gate when it was already started here or on the server. */
    fun shouldResume(config: PolicyConfig, recordId: Int, started: Boolean): Boolean =
        isSameRecord(config, recordId) && (started || config.costSeconds > 0)

    /**
     * Never rewind an ACTIVE session just because the last usage report has not reached the
     * server yet; the lower of the two values is the safe one.
     */
    fun nextRemaining(localRemaining: Long, configRemaining: Long): Long =
        minOf(localRemaining.coerceAtLeast(0L), configRemaining.coerceAtLeast(0L))

    /**
     * Phone parity: what the child can still use is the smaller of the points remaining and the
     * physical window remaining. The window includes the server's settlement buffer.
     */
    fun effectiveRemaining(pointsRemaining: Long, windowRemainingSeconds: Long?): Long {
        val points = pointsRemaining.coerceAtLeast(0L)
        return if (windowRemainingSeconds == null) {
            points
        } else {
            minOf(points, windowRemainingSeconds.coerceAtLeast(0L))
        }
    }
}
