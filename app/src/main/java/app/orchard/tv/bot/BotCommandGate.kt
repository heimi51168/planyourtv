package app.orchard.tv.bot

import java.util.ArrayDeque

/**
 * Chat-side admission in front of every command. Authentication is the
 * binding (created behind the TV parent center's PIN gate), so this gate is
 * about transport and abuse, not identity:
 *
 * 1. dedup by (binding, message id) in an LRU like MqttCommandVerifier's
 *    commandId table — the transport delivers at-least-once;
 * 2. per-binding rate limit so a held phone cannot spam the TV;
 * 3. one global execution lock: commands from every binding run one at a time.
 *
 * Accountability lives in BotAuditLog plus the broadcast to every binding.
 */
internal class BotCommandGate(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        private const val MAX_REMEMBERED_IDS = 512
        private const val RATE_LIMIT_COMMANDS = 6
        private const val RATE_LIMIT_WINDOW_MS = 60_000L
    }

    private val lock = Any()

    /** messageId -> seenAt; insertion-ordered for the LRU eviction. */
    private val seenMessageIds = object : LinkedHashMap<String, Long>(
        MAX_REMEMBERED_IDS,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_REMEMBERED_IDS
    }

    private val commandTimes = HashMap<String, ArrayDeque<Long>>()

    // --- admission ------------------------------------------------------------

    /** True the first time this (binding, message) pair is seen. */
    fun admitMessage(bindingId: String, messageId: String): Boolean {
        if (messageId.isEmpty()) return true
        val key = "$bindingId:$messageId"
        synchronized(seenMessageIds) {
            if (seenMessageIds.containsKey(key)) return false
            seenMessageIds[key] = clock()
            return true
        }
    }

    /** True when the binding is under the command-per-minute budget. */
    fun admitRate(bindingId: String): Boolean {
        val now = clock()
        synchronized(lock) {
            val times = commandTimes.getOrPut(bindingId) { ArrayDeque() }
            while (times.isNotEmpty() && now - times.first() >= RATE_LIMIT_WINDOW_MS) {
                times.removeFirst()
            }
            if (times.size >= RATE_LIMIT_COMMANDS) return false
            times.addLast(now)
            return true
        }
    }

    // --- serialization -----------------------------------------------------------

    /**
     * Runs [block] under the one global command lock. Re-entrancy is not
     * needed: nothing inside a command admits another command.
     */
    fun <T> runExclusive(block: () -> T): T = synchronized(executeLock) { block() }

    private val executeLock = Any()
}
