package com.planyourtv.policy

import java.security.MessageDigest

/**
 * Builds the START/PROGRESS/END contract from an immutable plan. The app engine supplies only
 * the clock, the local day total and the lifecycle end reason.
 */
object RuleUsageEventFactory {
    fun start(
        plan: PolicyExecutionPlan,
        usageDate: String,
        startedAtEpochMs: Long,
        usedSecondsToday: Long,
    ): RuleUsageEvent = event(
        plan = plan,
        type = RuleUsageEventType.START,
        usageDate = usageDate,
        startedAtEpochMs = startedAtEpochMs,
        sessionUsedSeconds = 0L,
        usedSecondsToday = usedSecondsToday,
    )

    fun progress(
        plan: PolicyExecutionPlan,
        usageDate: String,
        startedAtEpochMs: Long,
        sessionUsedSeconds: Long,
        usedSecondsToday: Long,
    ): RuleUsageEvent = event(
        plan = plan,
        type = RuleUsageEventType.PROGRESS,
        usageDate = usageDate,
        startedAtEpochMs = startedAtEpochMs,
        sessionUsedSeconds = sessionUsedSeconds,
        usedSecondsToday = usedSecondsToday,
    )

    fun end(
        plan: PolicyExecutionPlan,
        usageDate: String,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long,
        sessionUsedSeconds: Long,
        usedSecondsToday: Long,
        endReason: PolicyEndReason,
    ): RuleUsageEvent = event(
        plan = plan,
        type = RuleUsageEventType.END,
        usageDate = usageDate,
        startedAtEpochMs = startedAtEpochMs,
        sessionUsedSeconds = sessionUsedSeconds,
        usedSecondsToday = usedSecondsToday,
        endReason = endReason,
        endedAtEpochMs = endedAtEpochMs,
    )

    private fun event(
        plan: PolicyExecutionPlan,
        type: RuleUsageEventType,
        usageDate: String,
        startedAtEpochMs: Long,
        sessionUsedSeconds: Long,
        usedSecondsToday: Long,
        endReason: PolicyEndReason? = null,
        endedAtEpochMs: Long? = null,
    ): RuleUsageEvent {
        val isAppScope = plan.accessMode == AccessMode.APPS
        return RuleUsageEvent(
            sessionId = requireNotNull(plan.sessionId) { "rule plan has no session id" },
            type = type,
            usageDate = usageDate,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            plannedSeconds = plan.budgetSeconds,
            sessionUsedSeconds = sessionUsedSeconds,
            usedSecondsToday = usedSecondsToday,
            endReason = endReason,
            templateVersion = plan.templateVersion,
            accessMode = if (isAppScope) {
                RuleUsageAccessMode.APPS
            } else {
                RuleUsageAccessMode.ALL_VALID_APP
            },
            appScopeHash = if (isAppScope) scopeHash(plan.allowedPackages) else "",
        )
    }

    /** Only a digest leaves the TV; the full list stays in the local plan. */
    private fun scopeHash(packages: Set<String>): String {
        val canonical = packages.sorted().joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
