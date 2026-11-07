package com.planyourtv.policy

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * JSON contract for the backend `policy` node and for the TV's persisted mirror.
 *
 * Field names and minute units are the `LocalRules` 1:1 contract; keep them aligned with the
 * backend DTOs. Unknown fields are ignored so a newer backend can add fields safely.
 */
object RemotePolicyCodec {
    fun parse(json: JSONObject?): RemotePolicy? {
        if (json == null || json.length() == 0) return null
        val mode = parseMode(json.optString("mode", "")) ?: return null
        val handoff = parseHandoff(json.optString("handoffState", "")) ?: return null
        // Absent on an old backend: use the product default (manual). A malformed value is
        // rejected instead of silently switching the TV to another activation.
        val activation = if (json.has("activation")) {
            parseActivation(json.optString("activation", "")) ?: return null
        } else {
            Activation.MANUAL
        }
        val rulesJson = json.optJSONObject("rules") ?: return null
        val rules = parseRules(rulesJson, activation) ?: return null
        // Two sets only exist on a backend that runs the rule-set contract. When either set is
        // present both must parse; when neither is present this is an explicit old-backend
        // single-set mode (logged, never silently guessed).
        val ruleSets = if (json.has("autoRules") || json.has("manualRules")) {
            parseRuleSets(json, rules) ?: return null
        } else {
            PolicyFlowTrace.event(
                "remote.singleSetMode",
                "reason" to "backend_missing_rule_sets",
            )
            null
        }
        val configVersion = json.optLong("configVersion", 0L).coerceAtLeast(0L)
        val appliedVersion = json.optLong("appliedVersion", 0L).coerceAtLeast(0L)
        return RemotePolicy(
            mode = mode,
            handoffState = handoff,
            configVersion = configVersion,
            appliedVersion = appliedVersion,
            appliedMode = parseMode(json.optString("appliedMode", "")),
            supportsUnifiedPolicy = json.optBoolean("supportsUnifiedPolicy", false),
            rules = rules,
            activation = activation,
            supportsQuizConfig = json.optBoolean("supportsQuizConfig", false),
            supportsCloudQuiz = json.optBoolean("supportsCloudQuiz", false),
            ruleSets = ruleSets,
        )
    }

    /**
     * Parses the backend's two independent rule sets. Time fields are required: a partial set
     * would otherwise let one activation inherit another activation's values. The screen-off
     * buffer is optional and falls back to the flat snapshot, so an older backend keeps working
     * without rewriting either set.
     */
    fun parseRuleSets(json: JSONObject, rules: LocalRules): LocalRuleSets? {
        val fallbackBuffer = rules.sessionScreenOffBufferMinutes
        val auto = parseRuleSet(json.optJSONObject("autoRules"), fallbackBuffer) ?: return null
        val manual = parseRuleSet(json.optJSONObject("manualRules"), fallbackBuffer) ?: return null
        return LocalRuleSets(
            auto = auto,
            manual = manual,
            shared = LocalSharedAppScope.of(rules),
        )
    }

    fun parseRules(json: JSONObject, activation: Activation = Activation.MANUAL): LocalRules? {
        val weekday = json.optJSONArray("weekdayWindows").toWindowsOrNull() ?: return null
        val weekend = json.optJSONArray("weekendWindows").toWindowsOrNull() ?: return null
        val defaults = LocalRules.defaultsFor(activation)
        val schemaVersion = json.optInt("schemaVersion", 1)
        // Omitted by an old TV handoff / old backend: all apps. Schema 2 must carry the list;
        // without it we cannot tell "zero entertainment apps" from truncated data, so reject.
        val allowedPackages = when {
            json.has("allowedPackages") ->
                AllowedPackagesCodec.parse(json.optJSONArray("allowedPackages")) ?: return null
            schemaVersion < LocalRules.CURRENT_SCHEMA_VERSION -> defaults.allowedPackages
            else -> return null
        }
        val studyPackages = when {
            json.has("studyPackages") ->
                AllowedPackagesCodec.parse(json.optJSONArray("studyPackages")) ?: return null
            else -> emptySet()
        }
        val autoDailyLimitMinutes = when {
            json.has("autoDailyLimitMinutes") -> json.optInt("autoDailyLimitMinutes")
            else -> json.optInt("dailyLimitMinutes", defaults.autoDailyLimitMinutes)
        }
        val rules = LocalRules(
            weekdayWindows = weekday,
            weekendWindows = weekend,
            autoDailyLimitMinutes = autoDailyLimitMinutes,
            sessionLimitMinutes = json.optInt("sessionLimitMinutes", defaults.sessionLimitMinutes),
            restMinutes = json.optInt("restMinutes", defaults.restMinutes),
            // Shared with the existing device-level buffer. Older backends omit it, and legacy
            // values such as 1470 are clamped so the TV never pauses for a day.
            sessionScreenOffBufferMinutes = LocalRules.sanitizeSessionBufferMinutes(
                json.optInt(
                    "sessionScreenOffBufferMinutes",
                    LocalRules.DEFAULT_SESSION_BUFFER_MINUTES,
                ),
            ),
            schemaVersion = schemaVersion,
            allowedPackages = allowedPackages,
            studyPackages = studyPackages,
        ).normalized()
        return rules.takeIf { it.validationError() == LocalRulesError.NONE }
    }

    fun toJson(policy: RemotePolicy): JSONObject {
        val json = JSONObject()
            .put("mode", policy.mode.name)
            .put("handoffState", policy.handoffState.name)
            .put("configVersion", policy.configVersion)
            .put("appliedVersion", policy.appliedVersion)
            .put("appliedMode", policy.appliedMode?.name ?: JSONObject.NULL)
            .put("supportsUnifiedPolicy", policy.supportsUnifiedPolicy)
            .put("supportsQuizConfig", policy.supportsQuizConfig)
            .put("supportsCloudQuiz", policy.supportsCloudQuiz)
            .put("supportsRuleSets", policy.supportsRuleSets)
            .put("activation", policy.activation.name)
            .put("rules", rulesToJson(policy.rules))
        policy.ruleSets?.let { sets ->
            json.put("autoRules", ruleSetToJson(sets.auto))
            json.put("manualRules", ruleSetToJson(sets.manual))
        }
        return json
    }

    /**
     * Backend `PolicyRuleSetDto` contract: one set, `dailyLimitMinutes` naming. The buffer is
     * written only when the set carries one; a legacy set keeps the field absent so the backend
     * can distinguish "not split yet" from a real value.
     */
    fun ruleSetToJson(set: LocalRuleSet): JSONObject = JSONObject()
        .put("weekdayWindows", set.weekdayWindows.toJson())
        .put("weekendWindows", set.weekendWindows.toJson())
        .put("dailyLimitMinutes", set.autoDailyLimitMinutes)
        .put("sessionLimitMinutes", set.sessionLimitMinutes)
        .put("restMinutes", set.restMinutes)
        .apply {
            set.sessionScreenOffBufferMinutes?.let {
                put("sessionScreenOffBufferMinutes", it)
            }
        }

    private fun parseRuleSet(json: JSONObject?, fallbackBuffer: Int): LocalRuleSet? {
        if (json == null) return null
        if (!json.has("dailyLimitMinutes") ||
            !json.has("sessionLimitMinutes") ||
            !json.has("restMinutes")
        ) {
            return null
        }
        val weekday = json.optJSONArray("weekdayWindows").toWindowsOrNull() ?: return null
        val weekend = json.optJSONArray("weekendWindows").toWindowsOrNull() ?: return null
        val daily = json.optInt("dailyLimitMinutes", -1)
        val session = json.optInt("sessionLimitMinutes", -1)
        val rest = json.optInt("restMinutes", -1)
        if (daily < 0 || session < 0 || rest < 0) return null
        val buffer = if (json.has("sessionScreenOffBufferMinutes")) {
            val value = json.optInt("sessionScreenOffBufferMinutes", -1)
            if (value !in LocalRules.MIN_SESSION_BUFFER_MINUTES..LocalRules.MAX_SESSION_BUFFER_MINUTES) {
                return null
            }
            value
        } else {
            fallbackBuffer
        }
        return LocalRuleSet(
            weekdayWindows = weekday,
            weekendWindows = weekend,
            autoDailyLimitMinutes = daily,
            sessionLimitMinutes = session,
            restMinutes = rest,
            sessionScreenOffBufferMinutes = buffer,
        )
    }

    /** The handoff upload uses exactly the same LocalRules contract as the cloud node. */
    fun rulesToJson(rules: LocalRules): JSONObject = JSONObject()
        .put("schemaVersion", rules.schemaVersion)
        .put("weekdayWindows", rules.weekdayWindows.toJson())
        .put("weekendWindows", rules.weekendWindows.toJson())
        .put("autoDailyLimitMinutes", rules.autoDailyLimitMinutes)
        // Keep the V6 key while the backend and old installs still read it.
        .put("dailyLimitMinutes", rules.autoDailyLimitMinutes)
        .put("sessionLimitMinutes", rules.sessionLimitMinutes)
        .put("restMinutes", rules.restMinutes)
        .put("sessionScreenOffBufferMinutes", rules.sessionScreenOffBufferMinutes)
        .put("allowedPackages", AllowedPackagesCodec.toJson(rules.effectiveAllowedPackages))
        .put("studyPackages", AllowedPackagesCodec.toJson(rules.effectiveStudyPackages))

    private fun parseMode(raw: String): RemotePolicyMode? = when (raw.uppercase(Locale.ROOT)) {
        RemotePolicyMode.RULE.name -> RemotePolicyMode.RULE
        RemotePolicyMode.REDEEM.name -> RemotePolicyMode.REDEEM
        else -> null
    }

    private fun parseActivation(raw: String): Activation? =
        when (raw.uppercase(Locale.ROOT)) {
            Activation.AUTO.name -> Activation.AUTO
            Activation.MANUAL.name -> Activation.MANUAL
            else -> null
        }

    private fun parseHandoff(raw: String): RemoteHandoffState? =
        when (raw.uppercase(Locale.ROOT)) {
            RemoteHandoffState.PENDING.name -> RemoteHandoffState.PENDING
            RemoteHandoffState.DONE.name -> RemoteHandoffState.DONE
            else -> null
        }
}

/** Persisted last-known cloud policy. It lets the read-only summary survive offline/restart. */
object RemotePolicyStore {
    fun load(): RemotePolicy? {
        if (!TvStore.isReady) return null
        val raw = TvStore.getString(PolicyKeys.REMOTE_POLICY, null) ?: return null
        return runCatching { RemotePolicyCodec.parse(JSONObject(raw)) }.getOrNull()
    }

    fun save(policy: RemotePolicy) {
        if (!TvStore.isReady) return
        TvStore.putString(PolicyKeys.REMOTE_POLICY, RemotePolicyCodec.toJson(policy).toString())
        TvStore.sync()
    }

    fun clear() {
        if (!TvStore.isReady) return
        TvStore.remove(PolicyKeys.REMOTE_POLICY)
        TvStore.sync()
    }
}

private fun List<LocalTimeWindow>.toJson(): JSONArray = JSONArray().also { array ->
    forEach { window ->
        array.put(
            JSONObject()
                .put("startMinute", window.startMinute)
                .put("endMinute", window.endMinute),
        )
    }
}

private fun JSONArray?.toWindowsOrNull(): List<LocalTimeWindow>? {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val window = opt(i)
            if (window !is JSONObject ||
                !window.has("startMinute") ||
                !window.has("endMinute")
            ) {
                return null
            }
            add(
                LocalTimeWindow(
                    startMinute = window.optInt("startMinute"),
                    endMinute = window.optInt("endMinute"),
                ),
            )
        }
    }
}
