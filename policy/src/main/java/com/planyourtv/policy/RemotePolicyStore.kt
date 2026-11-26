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
        // Absent on an old backend: use the product default (manual). AUTO is no longer a valid
        // activation; an explicit AUTO makes the whole snapshot invalid instead of silently
        // switching the TV back to automatic scheduling.
        val activation = if (json.has("activation")) {
            parseActivation(json.optString("activation", "")) ?: run {
                PolicyFlowTrace.event(
                    "remote.invalidActivation",
                    "reason" to "auto_not_supported",
                )
                return null
            }
        } else {
            Activation.MANUAL
        }
        val rulesJson = json.optJSONObject("rules") ?: return null
        val rules = parseRules(rulesJson) ?: return null
        // The manual rule set is the only accepted set. `autoRules` from an older backend is
        // ignored; a snapshot with only `autoRules` falls back to the flat single-set rules.
        val memberScopedFields = parseMemberScopedFields(
            json,
            rulesJson,
            json.optJSONObject("manualRules"),
        ) ?: return null
        val scopedMask = memberScopedFields.fields.takeIf { memberScopedFields.present }
        val ruleSets = if (json.has("manualRules")) {
            parseRuleSets(json, rules) ?: return null
        } else {
            if (json.has("autoRules")) {
                PolicyFlowTrace.event(
                    "remote.autoRulesIgnored",
                    "reason" to "manual_rules_missing",
                )
            } else {
                PolicyFlowTrace.event(
                    "remote.singleSetMode",
                    "reason" to "backend_missing_rule_sets",
                )
            }
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
            memberScopedFields = scopedMask,
            sourceEntryVisible = parseOptionalBoolean(
                json,
                "sourceEntryVisible",
                "remote.invalidSourceEntryVisible",
            ),
            quizEntryVisible = parseOptionalBoolean(
                json,
                "quizEntryVisible",
                "remote.invalidQuizEntryVisible",
            ),
            switchProtection = parseSwitchProtection(json),
        )
    }

    /**
     * Reads the device-level switch protection node (2026-11-25).
     *
     * Absent / explicit null = a backend that does not project it; CLOUD mode keeps its
     * historical direct switch. A present but malformed node fails closed (protection on, no
     * waiver) instead of silently removing protection. `required=false` is the real off value
     * and ignores the window.
     */
    private fun parseSwitchProtection(json: JSONObject): SwitchProtection? {
        if (!json.has("switchProtection") || json.isNull("switchProtection")) return null
        val node = json.optJSONObject("switchProtection")
            ?: return failClosedSwitchProtection("not_object")
        val required = node.opt("required")
        if (required !is Boolean) return failClosedSwitchProtection("required_not_boolean")
        if (!required) return SwitchProtection()
        val daysArray = node.optJSONArray("exemptDays")
            ?: return failClosedSwitchProtection("days_missing")
        val days = ArrayList<Int>(daysArray.length())
        for (index in 0 until daysArray.length()) {
            val day = daysArray.opt(index) as? Int
                ?: return failClosedSwitchProtection("day_not_integer")
            if (day !in 1..7) return failClosedSwitchProtection("day_out_of_range")
            days.add(day)
        }
        val start = node.opt("exemptStartMinute") as? Int
            ?: return failClosedSwitchProtection("start_invalid")
        val end = node.opt("exemptEndMinute") as? Int
            ?: return failClosedSwitchProtection("end_invalid")
        return SwitchProtection(
            required = true,
            exemptDays = days,
            exemptStartMinute = start,
            exemptEndMinute = end,
        ).normalized() ?: failClosedSwitchProtection("window_invalid")
    }

    private fun failClosedSwitchProtection(reason: String): SwitchProtection {
        PolicyFlowTrace.event("remote.invalidSwitchProtection", "reason" to reason)
        return SwitchProtection(required = true)
    }

    /**
     * Reads one optional device-level boolean (入口展示, 2026-11-21).
     *
     * Absent / explicit null = a backend that does not project the field: the caller keeps its
     * previous composition. A wrong type is reported and also treated as absent — rejecting the
     * whole snapshot over a display flag would turn one malformed field into "no cloud policy
     * at all", which is strictly worse than leaving the local switch in charge. Never guessed
     * silently: [traceName] is emitted so the malformed value stays visible in the trace.
     */
    private fun parseOptionalBoolean(
        json: JSONObject,
        key: String,
        traceName: String,
    ): Boolean? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.opt(key)
        if (value is Boolean) return value
        PolicyFlowTrace.event(
            traceName,
            "reason" to "not_boolean",
            "value" to value.toString(),
        )
        return null
    }

    /**
     * Reads the backend `memberScopedFields` mask. [MemberScopedFields.present] is false when
     * the key is absent or explicitly null, i.e. the snapshot was not projected for a member;
     * the caller must then keep every local field. A non-null value must be a clean string
     * array: a malformed mask returns null and invalidates the snapshot instead of being read
     * as "no field is member-scoped" (that would widen the child's scope).
     */
    private fun parseMemberScopedFields(
        root: JSONObject,
        rules: JSONObject,
        manual: JSONObject?,
    ): MemberScopedFields? {
        // 2026-11-22: the backend writes an explicit JSON null when this snapshot was not
        // projected for a member. Treat that as "no mask" (keep every local field) instead of
        // invalidating the whole snapshot; a real mask in another location still wins.
        val source = sequenceOf(root, manual, rules)
            .filterNotNull()
            .firstOrNull { it.has("memberScopedFields") && !it.isNull("memberScopedFields") }
            ?: return MemberScopedFields(present = false, fields = emptySet())
        val array = source.optJSONArray("memberScopedFields") ?: return null
        val fields = LinkedHashSet<String>()
        for (index in 0 until array.length()) {
            val raw = array.opt(index) as? String ?: return null
            val value = raw.trim()
            if (value.isEmpty()) return null
            fields.add(value)
        }
        return MemberScopedFields(present = true, fields = fields)
    }

    /**
     * Parses the backend's manual rule set. Time fields are required: a partial set would
     * otherwise let the TV run with half the parent's limits. The screen-off buffer is optional
     * and falls back to the flat snapshot, so an older backend keeps working without rewriting
     * the set.
     */
    fun parseRuleSets(json: JSONObject, rules: LocalRules): LocalRuleSets? {
        val fallbackBuffer = rules.sessionScreenOffBufferMinutes
        val manual =
            parseRuleSet(json.optJSONObject("manualRules"), fallbackBuffer) ?: return null
        return LocalRuleSets(
            manual = manual,
            shared = LocalSharedAppScope.of(rules),
        )
    }

    /**
     * Optional package lists: absent and explicit JSON null both mean the backend did not
     * project this list; a present value still has to be a clean string array.
     */
    private fun parseOptionalPackages(json: JSONObject, key: String): Set<String>? {
        if (!json.has(key) || json.isNull(key)) return emptySet()
        return AllowedPackagesCodec.parse(json.optJSONArray(key))
    }

    fun parseRules(json: JSONObject): LocalRules? {
        val weekday = json.optJSONArray("weekdayWindows").toWindowsOrNull() ?: return null
        val weekend = json.optJSONArray("weekendWindows").toWindowsOrNull() ?: return null
        val defaults = LocalRules.DEFAULTS
        // The hosted contract always carries the entertainment list. A missing list is treated
        // as truncated data, not as permission to widen the scope to every app.
        val allowedPackages = AllowedPackagesCodec.parse(json.optJSONArray("allowedPackages"))
            ?: return null
        // 2026-11-22: the backend serializes an unprojected optional list as an explicit
        // JSON null (Jackson includes nulls). That means "not projected", not truncated data:
        // the member scope mask decides whether the local list survives, so accept it as empty.
        val studyPackages = parseOptionalPackages(json, "studyPackages") ?: return null
        val unlimitedPackages = parseOptionalPackages(json, "unlimitedPackages") ?: return null
        val studyEarnPackages = parseOptionalPackages(json, "studyEarnPackages") ?: return null
        // 2026-11-21: the member projection sends an explicit `null` for "unlimited"
        // (果园 0 / 1440). A missing key is still truncated data and rejects the snapshot.
        val autoRead = json.cloudMinutes("autoDailyLimitMinutes")
            .takeIf { it.present } ?: json.cloudMinutes("dailyLimitMinutes")
        if (!autoRead.present || !autoRead.valid) return null
        val autoDailyLimitMinutes = autoRead.value
        val weekendRead = json.cloudMinutes("weekendDailyLimitMinutes")
        if (!weekendRead.valid) return null
        val weekendDailyLimitMinutes =
            if (weekendRead.present) weekendRead.value else autoDailyLimitMinutes
        val restRead = json.cloudMinutes("restMinutes")
        if (!restRead.valid) return null
        val restMinutes = when (val value = restRead.value) {
            // Absent on very old snapshots; keep the historical default rather than rejecting.
            null -> if (restRead.present) return null else defaults.restMinutes
            else -> value.takeIf { it in 0..LocalRules.MAX_REST_MINUTES } ?: return null
        }
        val sessionLimitRead = json.cloudMinutes("sessionLimitMinutes")
        if (!sessionLimitRead.valid) return null
        val sessionLimitMinutes = when (val value = sessionLimitRead.value) {
            null -> if (sessionLimitRead.present) return null else defaults.sessionLimitMinutes
            else -> value.takeIf { it in 1..LocalRules.MAX_DAILY_MINUTES } ?: return null
        }
        // 单次娱乐上限：果园按「孩子 × 设备」配置，电视所有解锁路由共用。
        val sessionCapRead = json.cloudMinutes("sessionCapMinutes", LocalRules.MAX_SESSION_CAP_MINUTES)
        if (!sessionCapRead.valid) return null
        val sessionCapMinutes = when (val value = sessionCapRead.value) {
            // Explicit null = the snapshot did not project a cap (device-level policy);
            // the member mask decides whether the local value is replaced.
            null -> defaults.sessionCapMinutes
            else -> value.takeIf {
                it in LocalRules.MIN_SESSION_CAP_MINUTES..LocalRules.MAX_SESSION_CAP_MINUTES
            } ?: return null
        }
        // Shared with the existing device-level buffer. Older backends omit it, and legacy
        // values such as 1470 are clamped so the TV never pauses for a day.
        val sessionBufferMinutes = LocalRules.sanitizeSessionBufferMinutes(
            json.optInt(
                "sessionScreenOffBufferMinutes",
                LocalRules.DEFAULT_SESSION_BUFFER_MINUTES,
            ),
        )
        // The hosted contract is still Monday-group/weekend-group. Custom local groups are
        // preserved separately when a snapshot is applied; this adapter only feeds the flat
        // read-only policy object.
        val dayGroupsRead = parseOptionalDayGroups(json) ?: return null
        val dayGroups = dayGroupsRead.groups.takeIf { dayGroupsRead.present }
        val rules = LocalRules(
            dayGroups = dayGroups ?: LocalRules.fromLegacyWeekdayWeekend(
                weekdayWindows = weekday,
                weekendWindows = weekend,
                weekdayLimitMinutes = autoDailyLimitMinutes?.let(::cloudLimitToLocal),
                weekendLimitMinutes = weekendDailyLimitMinutes?.let(::cloudLimitToLocal),
            ),
            sessionLimitMinutes = sessionLimitMinutes,
            sessionCapMinutes = sessionCapMinutes,
            restMinutes = restMinutes,
            sessionScreenOffBufferMinutes = sessionBufferMinutes,
            // 各解锁方式每日次数；-1 = 不限制（果园 0）。
            quizDailyLimitCount = json.cloudCount(
                "quizDailyLimitCount",
                defaults.quizDailyLimitCount,
            ) ?: return null,
            studyRewardDailyLimitCount = json.cloudCount(
                "studyRewardDailyLimitCount",
                defaults.studyRewardDailyLimitCount,
            ) ?: return null,
            directDailyFreeCount = json.cloudCount(
                "directDailyFreeCount",
                defaults.directDailyFreeCount,
            ) ?: return null,
            fruitExchangeDailyLimitCount = json.cloudCount(
                "fruitExchangeDailyLimitCount",
                defaults.fruitExchangeDailyLimitCount,
            ) ?: return null,
            // The current backend DTO does not carry V43 route switches. An absent field keeps
            // the historical behaviour here; a locally stored set is preserved separately by
            // LocalRuleSets.preservingDeviceFieldsFrom.
            quizGateEnabled = json.optBoolean("quizGateEnabled", defaults.quizGateEnabled),
            studyEarnEnabled = json.optBoolean("studyEarnEnabled", defaults.studyEarnEnabled),
            directStartEnabled = json.optBoolean("directStartEnabled", defaults.directStartEnabled),
            fruitExchangeEnabled = json.optBoolean(
                "fruitExchangeEnabled",
                defaults.fruitExchangeEnabled,
            ),
            allowedPackages = allowedPackages,
            studyPackages = studyPackages,
            unlimitedPackages = unlimitedPackages,
            studyEarnPackages = studyEarnPackages,
            studyEarnStudyMinutes = json.optInt(
                "studyEarnStudyMinutes",
                defaults.studyEarnStudyMinutes,
            ),
            studyEarnRewardMinutes = json.optInt(
                "studyEarnRewardMinutes",
                defaults.studyEarnRewardMinutes,
            ),
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
            .put("rules", rulesToJson(policy.rules).withLocalSnapshotFields(policy.rules))
        policy.ruleSets?.let { sets ->
            json.put("manualRules", ruleSetToJson(sets.manual).withLocalSnapshotFields(sets.manual))
        }
        // TV-local bookkeeping: the backend owns the mask on the wire, but the persisted mirror
        // must keep it so a restart still knows which fields are member-scoped.
        policy.memberScopedFields?.let { fields ->
            json.put("memberScopedFields", JSONArray().also { array -> fields.forEach(array::put) })
        }
        // Absent means "the backend never projected it"; writing null would turn that into an
        // explicit value after the first restart.
        policy.sourceEntryVisible?.let { json.put("sourceEntryVisible", it) }
        policy.quizEntryVisible?.let { json.put("quizEntryVisible", it) }
        // Same absent-means-unknown rule as the entry flags: writing null would turn "old
        // backend does not project it" into an explicit off after the first restart.
        policy.switchProtection?.let { protection ->
            json.put(
                "switchProtection",
                JSONObject()
                    .put("required", protection.required)
                    .put(
                        "exemptDays",
                        JSONArray().also { array -> protection.exemptDays.forEach(array::put) },
                    )
                    .put("exemptStartMinute", protection.exemptStartMinute)
                    .put("exemptEndMinute", protection.exemptEndMinute),
            )
        }
        return json
    }

    /**
     * Local-mirror additions on top of the handoff contract. [rulesToJson] / [ruleSetToJson]
     * stay byte-compatible with the backend upload; only the persisted snapshot carries the
     * member-scoped fields, day groups and route counts so a reload can restore them exactly.
     */
    private fun JSONObject.withLocalSnapshotFields(rules: LocalRules): JSONObject = apply {
        put("sessionCapMinutes", rules.sessionCapMinutes)
        put("quizDailyLimitCount", rules.quizDailyLimitCount)
        put("studyRewardDailyLimitCount", rules.studyRewardDailyLimitCount)
        put("directDailyFreeCount", rules.directDailyFreeCount)
        put("fruitExchangeDailyLimitCount", rules.fruitExchangeDailyLimitCount)
        put("dayGroups", dayGroupsToJson(rules.dayGroups))
    }

    private fun JSONObject.withLocalSnapshotFields(set: LocalRuleSet): JSONObject = apply {
        put("sessionCapMinutes", set.sessionCapMinutes)
        put("quizDailyLimitCount", set.quizDailyLimitCount)
        put("studyRewardDailyLimitCount", set.studyRewardDailyLimitCount)
        put("directDailyFreeCount", set.directDailyFreeCount)
        put("fruitExchangeDailyLimitCount", set.fruitExchangeDailyLimitCount)
        put("dayGroups", dayGroupsToJson(set.dayGroups))
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
        .put("weekendDailyLimitMinutes", set.weekendDailyLimitMinutes)
        .put("sessionLimitMinutes", set.sessionLimitMinutes)
        .put("restMinutes", set.restMinutes)
        .put("weekendRestMinutes", set.weekendRestMinutes)
        .put("sessionScreenOffBufferMinutes", set.sessionScreenOffBufferMinutes)
        .put("weekendSessionScreenOffBufferMinutes", set.weekendSessionScreenOffBufferMinutes)
        .put("quizGateEnabled", set.quizGateEnabled)
        .put("studyEarnEnabled", set.studyEarnEnabled)
        .put("directStartEnabled", set.directStartEnabled)
        .put("fruitExchangeEnabled", set.fruitExchangeEnabled)

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
        // 2026-11-21: the member projection sends an explicit null for "unlimited".
        val dailyRead = json.cloudMinutes("dailyLimitMinutes")
        val weekendRead = json.cloudMinutes("weekendDailyLimitMinutes")
        if (!dailyRead.valid || !weekendRead.valid) return null
        val daily = dailyRead.value
        // Backends that predate the weekday/weekend split omit the weekend quota; the old
        // single value applied to every day, so reuse it instead of inventing a new limit.
        val weekendDaily = if (weekendRead.present) weekendRead.value else daily
        val sessionRead = json.cloudMinutes("sessionLimitMinutes")
        if (!sessionRead.valid) return null
        val session = sessionRead.value?.takeIf { it in 1..LocalRules.MAX_DAILY_MINUTES }
            ?: return null
        val restRead = json.cloudMinutes("restMinutes")
        if (!restRead.valid) return null
        val rest = restRead.value?.takeIf { it in 0..LocalRules.MAX_REST_MINUTES } ?: return null
        // One rest and one screen-off grace now apply to every day; a backend that still sends
        // weekend values has them projected onto the single local value.
        val buffer = if (json.has("sessionScreenOffBufferMinutes")) {
            val value = json.optInt("sessionScreenOffBufferMinutes", -1)
            if (value !in LocalRules.MIN_SESSION_BUFFER_MINUTES..LocalRules.MAX_SESSION_BUFFER_MINUTES) {
                return null
            }
            value
        } else {
            fallbackBuffer
        }
        val sessionCapRead = json.cloudMinutes("sessionCapMinutes", LocalRules.MAX_SESSION_CAP_MINUTES)
        if (!sessionCapRead.valid) return null
        val sessionCap = when (val value = sessionCapRead.value) {
            null -> LocalRules.DEFAULTS.sessionCapMinutes
            else -> value.takeIf {
                it in LocalRules.MIN_SESSION_CAP_MINUTES..LocalRules.MAX_SESSION_CAP_MINUTES
            } ?: return null
        }
        val quizCount = json.cloudCount(
            "quizDailyLimitCount",
            LocalRules.DEFAULTS.quizDailyLimitCount,
        ) ?: return null
        val studyRewardCount = json.cloudCount(
            "studyRewardDailyLimitCount",
            LocalRules.DEFAULTS.studyRewardDailyLimitCount,
        ) ?: return null
        val directCount = json.cloudCount(
            "directDailyFreeCount",
            LocalRules.DEFAULTS.directDailyFreeCount,
        ) ?: return null
        val fruitCount = json.cloudCount(
            "fruitExchangeDailyLimitCount",
            LocalRules.DEFAULTS.fruitExchangeDailyLimitCount,
        ) ?: return null
        val dayGroupsRead = parseOptionalDayGroups(json) ?: return null
        val dayGroups = dayGroupsRead.groups.takeIf { dayGroupsRead.present }
            ?: LocalRules.fromLegacyWeekdayWeekend(
                weekdayWindows = weekday,
                weekendWindows = weekend,
                weekdayLimitMinutes = daily?.let(::cloudLimitToLocal),
                weekendLimitMinutes = weekendDaily?.let(::cloudLimitToLocal),
            )
        return LocalRuleSet(
            dayGroups = dayGroups,
            sessionLimitMinutes = session,
            sessionCapMinutes = sessionCap,
            restMinutes = rest,
            sessionScreenOffBufferMinutes = buffer,
            // Absent on the V42 backend DTO. Defaults are the historical routes; an existing
            // local document re-supplies its own values when the snapshot is applied.
            quizGateEnabled = json.optBoolean(
                "quizGateEnabled",
                LocalRules.DEFAULTS.quizGateEnabled,
            ),
            studyEarnEnabled = json.optBoolean(
                "studyEarnEnabled",
                LocalRules.DEFAULTS.studyEarnEnabled,
            ),
            directStartEnabled = json.optBoolean(
                "directStartEnabled",
                LocalRules.DEFAULTS.directStartEnabled,
            ),
            fruitExchangeEnabled = json.optBoolean(
                "fruitExchangeEnabled",
                LocalRules.DEFAULTS.fruitExchangeEnabled,
            ),
            quizDailyLimitCount = quizCount,
            studyRewardDailyLimitCount = studyRewardCount,
            directDailyFreeCount = directCount,
            fruitExchangeDailyLimitCount = fruitCount,
        )
    }

    /** The handoff upload uses exactly the same LocalRules contract as the cloud node. */
    fun rulesToJson(rules: LocalRules): JSONObject = JSONObject()
        .put("schemaVersion", rules.schemaVersion)
        .put("weekdayWindows", rules.weekdayWindows.toJson())
        .put("weekendWindows", rules.weekendWindows.toJson())
        .put("autoDailyLimitMinutes", rules.autoDailyLimitMinutes)
        .put("weekendDailyLimitMinutes", rules.weekendDailyLimitMinutes)
        // Keep the V6 key while the backend and old installs still read it.
        .put("dailyLimitMinutes", rules.autoDailyLimitMinutes)
        .put("sessionLimitMinutes", rules.sessionLimitMinutes)
        .put("restMinutes", rules.restMinutes)
        .put("weekendRestMinutes", rules.weekendRestMinutes)
        .put("sessionScreenOffBufferMinutes", rules.sessionScreenOffBufferMinutes)
        .put("quizGateEnabled", rules.quizGateEnabled)
        .put("studyEarnEnabled", rules.studyEarnEnabled)
        .put("directStartEnabled", rules.directStartEnabled)
        .put("fruitExchangeEnabled", rules.fruitExchangeEnabled)
        .put(
            "weekendSessionScreenOffBufferMinutes",
            rules.weekendSessionScreenOffBufferMinutes,
        )
        .put("allowedPackages", AllowedPackagesCodec.toJson(rules.effectiveAllowedPackages))
        .put("studyPackages", AllowedPackagesCodec.toJson(rules.effectiveStudyPackages))

    private fun parseMode(raw: String): RemotePolicyMode? = when (raw.uppercase(Locale.ROOT)) {
        RemotePolicyMode.RULE.name -> RemotePolicyMode.RULE
        RemotePolicyMode.REDEEM.name -> RemotePolicyMode.REDEEM
        else -> null
    }

    /** AUTO is deliberately rejected so an old backend cannot re-enable local auto timing. */
    private fun parseActivation(raw: String): Activation? =
        when (raw.uppercase(Locale.ROOT)) {
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

/**
 * Cloud 额度原样落到本地（2026-11-21 与果园 / 本地 web「用户配置」同口径）：
 * JSON `null` = 不限（由 [cloudMinutes] 读成 null，调用方用 `?.let` 保持 null），
 * **0 = 0 分钟**（当天禁止娱乐），1..1440 = 字面分钟数（1440 = 24:00，也是可选的显式值）。
 * 旧实现把 1440 折叠成 null，会让「设为 1440 分钟」在电视上显示成「不限」，与家长所配不一致。
 */
private fun cloudLimitToLocal(value: Int): Int? = value

/** Presence of the member mask distinguishes "old backend" from a real empty mask. */
private class MemberScopedFields(val present: Boolean, val fields: Set<String>)

/**
 * One cloud minute value. [present] = the key exists; [valid] = the value was understood;
 * [value] = null for JSON `null` ("unlimited") or the 0..1440 minute count.
 */
private class CloudMinutes(val present: Boolean, val valid: Boolean, val value: Int?)

/**
 * Reads a minute value that may legitimately be JSON `null` (unlimited). A non-number or an
 * out-of-range value is invalid and the caller must reject the whole snapshot instead of
 * inventing a default — a silently widened or narrowed quota is a real behaviour change.
 */
private fun JSONObject.cloudMinutes(
    key: String,
    max: Int = LocalRules.MAX_DAILY_MINUTES,
): CloudMinutes {
    if (!has(key)) return CloudMinutes(present = false, valid = true, value = null)
    if (isNull(key)) return CloudMinutes(present = true, valid = true, value = null)
    val raw = opt(key)
    if (raw !is Number) return CloudMinutes(present = true, valid = false, value = null)
    val exact = raw.toDouble()
    val value = raw.toInt()
    if (exact != value.toDouble() || value !in 0..max) {
        return CloudMinutes(present = true, valid = false, value = null)
    }
    return CloudMinutes(present = true, valid = true, value = value)
}

/**
 * Reads a route daily count. The backend projects the orchard's "0 = unlimited" as `-1`, which
 * is kept as-is; any other accepted value is 0..99.
 */
private fun JSONObject.cloudCount(key: String, fallback: Int): Int? {
    if (!has(key) || isNull(key)) return fallback
    val raw = opt(key)
    if (raw !is Number) return null
    val exact = raw.toDouble()
    val value = raw.toInt()
    if (exact != value.toDouble() || value !in -1..LocalRules.MAX_ROUTE_DAILY_COUNT) return null
    return value
}

/** [present] is false when the backend did not project a per-day schedule for this member. */
private class CloudDayGroups(val present: Boolean, val groups: List<LocalDayGroup>)

/**
 * Parses `dayGroups` when present. A malformed structure returns null so the caller rejects
 * the snapshot: running on a half-understood schedule is worse than reporting the fault.
 */
private fun parseOptionalDayGroups(json: JSONObject): CloudDayGroups? {
    if (!json.has("dayGroups") || json.isNull("dayGroups")) {
        return CloudDayGroups(present = false, groups = emptyList())
    }
    val array = json.optJSONArray("dayGroups") ?: return null
    val groups = parseDayGroups(array) ?: return null
    return CloudDayGroups(present = true, groups = groups)
}

private fun parseDayGroups(array: JSONArray): List<LocalDayGroup>? {
    if (array.length() !in 1..LocalRules.MAX_DAY_GROUPS) return null
    val assigned = HashSet<Int>()
    val groups = ArrayList<LocalDayGroup>(array.length())
    for (index in 0 until array.length()) {
        val group = array.opt(index) as? JSONObject ?: return null
        val daysArray = group.optJSONArray("days") ?: return null
        val days = ArrayList<Int>(daysArray.length())
        for (dayIndex in 0 until daysArray.length()) {
            val raw = daysArray.opt(dayIndex)
            if (raw !is Number) return null
            val exact = raw.toDouble()
            val day = raw.toInt()
            if (exact != day.toDouble() || day !in 1..7) return null
            if (!assigned.add(day)) return null
            days.add(day)
        }
        if (days.isEmpty()) return null
        val studyWindows = group.optJSONArray("studyWindows").toWindowsOrNull() ?: return null
        val entertainmentWindows =
            group.optJSONArray("entertainmentWindows").toWindowsOrNull() ?: return null
        val studyLimit = group.cloudMinutes("studyDailyLimitMinutes")
        if (!studyLimit.valid) return null
        val entertainmentLimit = group.cloudMinutes("entertainmentLimitMinutes")
        if (!entertainmentLimit.valid) return null
        groups.add(
            LocalDayGroup(
                days = days.sorted(),
                studyWindows = studyWindows,
                studyDailyLimitMinutes = studyLimit.value?.let(::cloudLimitToLocal),
                entertainmentWindows = entertainmentWindows,
                entertainmentDailyLimitMinutes =
                    entertainmentLimit.value?.let(::cloudLimitToLocal),
            ),
        )
    }
    if (assigned.size != 7) return null
    return groups.sortedBy { it.firstDay }
}

private fun dayGroupsToJson(groups: List<LocalDayGroup>): JSONArray = JSONArray().also { array ->
    for (group in groups) {
        val days = JSONArray()
        group.days.forEach(days::put)
        array.put(
            JSONObject()
                .put("days", days)
                .put("studyWindows", group.studyWindows.toJson())
                .put("studyDailyLimitMinutes", group.studyDailyLimitMinutes ?: JSONObject.NULL)
                .put("entertainmentWindows", group.entertainmentWindows.toJson())
                .put(
                    "entertainmentLimitMinutes",
                    group.entertainmentDailyLimitMinutes ?: JSONObject.NULL,
                ),
        )
    }
}
