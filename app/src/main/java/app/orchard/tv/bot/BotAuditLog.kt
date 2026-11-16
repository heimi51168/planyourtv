package app.orchard.tv.bot

import com.planyourtv.policy.TvStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable record of every chat control command: who ran what, with which
 * arguments, and whether the TV accepted it. This is the accountability half
 * of the no-PIN model — the binding authenticates, the audit log explains.
 *
 * Entries survive restarts (TvStore, durable write) and are capped by a ring
 * of [MAX_ENTRIES]; the newest keep their order. Queries are read-only and
 * never feed back into admission.
 */
internal object BotAuditLog {
    private const val KEY = "bot.audit.v1"
    private const val MAX_ENTRIES = 200

    data class Entry(
        val atMs: Long,
        /** Bound nickname / 家长N of the sender. */
        val actor: String,
        /** Canonical command, e.g. 加时20. */
        val command: String,
        /** true when the TV executed it; false when policy refused. */
        val accepted: Boolean,
        /** One-line outcome for humans, e.g. "已开始 20 分钟观看" or the refusal reason. */
        val detail: String,
    )

    @Synchronized
    fun append(entry: Entry) {
        val array = loadArray()
        array.put(encode(entry))
        // Trim from the head; the ring keeps the newest MAX_ENTRIES.
        while (array.length() > MAX_ENTRIES) array.remove(0)
        TvStore.putStringDurable(KEY, array.toString())
    }

    /** Newest-last. [limit] caps the reply length; [includeRejected] keeps refusals visible. */
    @Synchronized
    fun recent(limit: Int = 20, includeRejected: Boolean = true): List<Entry> {
        val array = loadArray()
        val all = ArrayList<Entry>(array.length())
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            decode(json)?.let(all::add)
        }
        val filtered = if (includeRejected) all else all.filter { it.accepted }
        return filtered.takeLast(limit.coerceIn(1, MAX_ENTRIES))
    }

    private fun loadArray(): JSONArray {
        val raw = TvStore.getString(KEY, null) ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun encode(entry: Entry): JSONObject = JSONObject()
        .put("t", entry.atMs)
        .put("actor", entry.actor)
        .put("cmd", entry.command)
        .put("ok", entry.accepted)
        .put("detail", entry.detail.take(80))

    private fun decode(json: JSONObject): Entry? {
        val actor = json.optString("actor", "")
        val command = json.optString("cmd", "")
        if (actor.isEmpty() || command.isEmpty()) return null
        return Entry(
            atMs = json.optLong("t", 0L),
            actor = actor,
            command = command,
            accepted = json.optBoolean("ok", false),
            detail = json.optString("detail", ""),
        )
    }
}
