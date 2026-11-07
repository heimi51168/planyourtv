package com.planyourtv.policy

/** Rule usage events are statistics only; they never feed back into the local day ledger. */
enum class RuleUsageEventType {
    START,
    PROGRESS,
    END,
}

/** Backend contract: the rule-mode app scope, not the redemption access mode. */
enum class RuleUsageAccessMode {
    ALL_VALID_APP,
    APPS,
}

/**
 * One START/PROGRESS/END report for a rule session.
 *
 * The TV is the execution authority. [usedSecondsToday] is the absolute local day total, which
 * makes repeated and out-of-order reports safe: the backend only moves the daily total forward.
 */
data class RuleUsageEvent(
    val sessionId: String,
    val type: RuleUsageEventType,
    /** TV local date, yyyy-MM-dd. */
    val usageDate: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val plannedSeconds: Long,
    val sessionUsedSeconds: Long,
    val usedSecondsToday: Long,
    /** Only END carries a reason. */
    val endReason: PolicyEndReason? = null,
    val templateVersion: Long,
    val accessMode: RuleUsageAccessMode = RuleUsageAccessMode.ALL_VALID_APP,
    val appScopeHash: String = "",
    /** Filled by [RuleUsageQueue]; the reporter sends batches in this order. */
    val seq: Long = 0L,
) {
    init {
        require(sessionId.isNotBlank() && sessionId.length <= 64) { "invalid sessionId" }
        require(usageDate.matches(DATE_PATTERN)) { "invalid usageDate" }
        require(startedAtEpochMs > 0) { "startedAtEpochMs must be positive" }
        require(plannedSeconds >= 0) { "plannedSeconds must not be negative" }
        require(sessionUsedSeconds >= 0) { "sessionUsedSeconds must not be negative" }
        require(usedSecondsToday >= 0) { "usedSecondsToday must not be negative" }
        require(sessionUsedSeconds <= plannedSeconds + USED_SECONDS_GRACE) {
            "sessionUsedSeconds exceeds the plan"
        }
        require(usedSecondsToday + 1 >= sessionUsedSeconds) {
            "usedSecondsToday is smaller than this session"
        }
        require(templateVersion >= 0) { "templateVersion must not be negative" }
        require(appScopeHash.length <= 128) { "appScopeHash is too long" }
        require(seq >= 0) { "seq must not be negative" }
        when (type) {
            RuleUsageEventType.START,
            RuleUsageEventType.PROGRESS,
            -> {
                require(endReason == null) { "only END carries an end reason" }
                require(endedAtEpochMs == null) { "only END carries an end time" }
            }
            RuleUsageEventType.END -> {
                require(endReason != null) { "END needs an end reason" }
                require(endedAtEpochMs != null && endedAtEpochMs > 0) {
                    "END needs a positive end time"
                }
            }
        }
    }

    companion object {
        private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
        /** The backend allows a small overrun between the last progress and the final END. */
        const val USED_SECONDS_GRACE = 300L
    }
}

/**
 * In-memory reporter queue.
 *
 * At most one event per session is pending: newer START/PROGRESS replace the previous one, END
 * replaces everything and blocks later PROGRESS. Acknowledge only removes the exact delivered
 * sequence, so an update enqueued while a batch is in flight is not lost.
 */
class RuleUsageQueue private constructor(
    restored: List<RuleUsageEvent>,
    nextSequence: Long,
) {
    private val pending = LinkedHashMap<String, RuleUsageEvent>()
    private var nextSequence = nextSequence

    init {
        restored.sortedBy { it.seq }.forEach { event -> pending[event.sessionId] = event }
    }

    fun enqueue(event: RuleUsageEvent): RuleUsageEvent {
        val current = pending[event.sessionId]
        if (current != null && current.type == RuleUsageEventType.END &&
            event.type != RuleUsageEventType.END
        ) {
            return current
        }
        val stamped = event.copy(seq = nextSequence)
        nextSequence += 1L
        pending[event.sessionId] = stamped
        return stamped
    }

    /** A stable batch in enqueue order; nothing is removed until [acknowledge]. */
    fun snapshot(): List<RuleUsageEvent> = pending.values.sortedBy { it.seq }

    fun acknowledge(delivered: List<RuleUsageEvent>) {
        for (event in delivered) {
            val current = pending[event.sessionId] ?: continue
            if (current.seq == event.seq) pending.remove(event.sessionId)
        }
    }

    fun pendingFor(sessionId: String): RuleUsageEvent? = pending[sessionId]

    val size: Int get() = pending.size

    val isEmpty: Boolean get() = pending.isEmpty()

    companion object {
        fun empty(): RuleUsageQueue = RuleUsageQueue(emptyList(), nextSequence = 1L)

        fun restore(events: List<RuleUsageEvent>): RuleUsageQueue {
            val next = (events.maxOfOrNull { it.seq } ?: 0L) + 1L
            return RuleUsageQueue(events, nextSequence = next)
        }
    }
}
