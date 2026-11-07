package com.planyourtv.policy

/**
 * Immutable snapshot the shared executor consumes.
 *
 * Rule and redemption sessions have different producers but the same execution shape: a budget,
 * an optional wall-clock deadline, an app scope, and a usage sink identity. A running plan is
 * never rewritten by a later rule/config update; the next plan picks up the new template.
 */
data class PolicyExecutionPlan(
    val id: String,
    val source: PolicyPlanSource,
    val activation: PolicyActivation,
    /**
     * The natural end of this plan's budget or window. Lifecycle commands (parent stop, mode
     * switch, crash) override it in the usage event; they do not rewrite the snapshot.
     */
    val endReason: PolicyEndReason,
    val budgetSeconds: Long,
    val deadlineEpochMs: Long?,
    val accessMode: AccessMode,
    val allowedPackages: Set<String>,
    val restAfterSeconds: Long,
    val templateVersion: Long,
    /** Redemption record id; null for rule plans. */
    val recordId: Long? = null,
    /** TV-generated rule session id; null for redemption plans. */
    val sessionId: String? = null,
) {
    init {
        require(id.isNotBlank()) { "plan id is blank" }
        require(budgetSeconds > 0) { "budgetSeconds must be positive" }
        require(deadlineEpochMs == null || deadlineEpochMs > 0) { "deadlineEpochMs must be positive" }
        require(restAfterSeconds >= 0) { "restAfterSeconds must not be negative" }
        require(templateVersion >= 0) { "templateVersion must not be negative" }

        when (source) {
            PolicyPlanSource.RULE -> {
                require(recordId == null) { "rule plans have no redemption record" }
                require(!sessionId.isNullOrBlank()) { "rule plans need a session id" }
                require(
                    endReason == PolicyEndReason.SESSION_LIMIT ||
                        endReason == PolicyEndReason.DAILY_LIMIT ||
                        endReason == PolicyEndReason.WINDOW_END,
                ) { "rule plans end on a session, daily or window limit" }
                require(
                    restAfterSeconds == 0L || endReason == PolicyEndReason.SESSION_LIMIT,
                ) { "only a session-limit plan carries rest" }
            }
            PolicyPlanSource.REDEEM -> {
                require(activation == PolicyActivation.MANUAL) { "redemption plans are parent-started" }
                require(recordId != null && recordId > 0) { "redemption plans need a record id" }
                require(sessionId == null) { "redemption plans have no rule session id" }
                require(endReason == PolicyEndReason.REDEMPTION_END) {
                    "redemption plans are budgeted until redemption end"
                }
                require(restAfterSeconds == 0L) { "redemption plans have no rule rest" }
            }
        }
    }
}

enum class PolicyPlanSource {
    RULE,
    REDEEM,
}

enum class PolicyActivation {
    AUTO,
    MANUAL,
}

/** End reasons written to the rule usage table; redemption keeps its existing reason strings. */
enum class PolicyEndReason {
    SESSION_LIMIT,
    DAILY_LIMIT,
    WINDOW_END,
    REDEMPTION_END,
    PARENT_STOP,
    MODE_SWITCH,
    /** The child chose “退出计时” in the TV control center. */
    USER_EXIT,
    /** AUTO ↔ MANUAL changed, so the running session was ended before the switch. */
    ACTIVATION_SWITCH,
    /** The fixed wall deadline ran out while the screen was off or paused. */
    SCREEN_OFF_TIMEOUT,
    CANCELED,
    CRASH,
    UNKNOWN,
}
