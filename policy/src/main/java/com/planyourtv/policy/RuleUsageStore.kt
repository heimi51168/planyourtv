package com.planyourtv.policy

import org.json.JSONArray
import org.json.JSONObject

/** JSON contract for rule usage events. Field names match `RuleUsageEventDto`. */
object RuleUsageCodec {
    fun toJson(event: RuleUsageEvent): JSONObject = JSONObject()
        .put("sessionId", event.sessionId)
        .put("event", event.type.name)
        .put("usageDate", event.usageDate)
        .put("startedAt", event.startedAtEpochMs)
        .put("endedAt", event.endedAtEpochMs ?: JSONObject.NULL)
        .put("plannedSeconds", event.plannedSeconds)
        .put("sessionUsedSeconds", event.sessionUsedSeconds)
        .put("usedSecondsToday", event.usedSecondsToday)
        .put("endReason", event.endReason?.name ?: JSONObject.NULL)
        .put("templateVersion", event.templateVersion)
        .put("accessMode", event.accessMode.name)
        .put("appScopeHash", event.appScopeHash)
        .put("seq", event.seq)

    fun fromJson(json: JSONObject): RuleUsageEvent? = runCatching {
        RuleUsageEvent(
            sessionId = json.getString("sessionId"),
            type = RuleUsageEventType.valueOf(json.getString("event")),
            usageDate = json.getString("usageDate"),
            startedAtEpochMs = json.getLong("startedAt"),
            endedAtEpochMs = json.optLongOrNull("endedAt"),
            plannedSeconds = json.optLong("plannedSeconds", 0L),
            sessionUsedSeconds = json.optLong("sessionUsedSeconds", 0L),
            usedSecondsToday = json.optLong("usedSecondsToday", 0L),
            endReason = json.stringOrNull("endReason")?.let { PolicyEndReason.valueOf(it) },
            templateVersion = json.optLong("templateVersion", 0L),
            accessMode = json.stringOrNull("accessMode")
                ?.let { RuleUsageAccessMode.valueOf(it) }
                ?: RuleUsageAccessMode.ALL_VALID_APP,
            appScopeHash = json.optString("appScopeHash", ""),
            seq = json.optLong("seq", 0L),
        )
    }.getOrNull()

    fun toJsonArray(events: List<RuleUsageEvent>): JSONArray = JSONArray().also { array ->
        events.forEach { array.put(toJson(it)) }
    }

    fun fromJsonArray(raw: String?): List<RuleUsageEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    fromJson(item)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }
}

/** Persists the reporter queue in TvStore. A failed read must not affect rule execution. */
object RuleUsageStore {
    fun load(): RuleUsageQueue {
        if (!TvStore.isReady) return RuleUsageQueue.empty()
        val raw = TvStore.getString(PolicyKeys.RULE_USAGE_QUEUE, null)
        return RuleUsageQueue.restore(RuleUsageCodec.fromJsonArray(raw))
    }

    fun save(queue: RuleUsageQueue) {
        if (!TvStore.isReady) return
        val events = queue.snapshot()
        if (events.isEmpty()) {
            TvStore.remove(PolicyKeys.RULE_USAGE_QUEUE)
        } else {
            TvStore.putString(
                PolicyKeys.RULE_USAGE_QUEUE,
                RuleUsageCodec.toJsonArray(events).toString(),
            )
        }
        TvStore.sync()
    }

    fun clear() {
        if (!TvStore.isReady) return
        TvStore.remove(PolicyKeys.RULE_USAGE_QUEUE)
        TvStore.sync()
    }
}

private fun JSONObject.optLongOrNull(name: String): Long? {
    if (isNull(name)) return null
    return optLong(name, 0L).takeIf { it > 0 }
}

private fun JSONObject.stringOrNull(name: String): String? {
    if (isNull(name)) return null
    return optString(name, "").takeIf { it.isNotBlank() }
}
