package com.planyourtv.policy

import org.json.JSONArray
import org.json.JSONObject

/** Persisted NONE/LOCAL/CLOUD choice and the monotonic version reported to the cloud. */
object PolicyModeStore {
    fun load(): PolicyMode? {
        if (!TvStore.isReady) return null
        val raw = TvStore.getString(PolicyKeys.LOCAL_MODE, null) ?: return null
        return runCatching { PolicyMode.valueOf(raw) }.getOrNull()
    }

    fun version(): Long =
        if (TvStore.isReady) TvStore.getLong(PolicyKeys.MODE_VERSION, 0L) else 0L

    /**
     * Saves the mode and bumps the version only when the mode actually changes. Returns the
     * current version so callers can include it in the cloud report.
     */
    fun save(mode: PolicyMode): Long {
        if (!TvStore.isReady) return version()
        val previous = load()
        var version = version()
        if (previous != mode) {
            // An epoch-based value survives a reinstall with a stable deviceNo: the backend may
            // still hold a larger counter from the previous install, so a plain +1 could be
            // ignored forever. max() keeps both reinstall and normal local monotonicity.
            version = maxOf(System.currentTimeMillis(), version + 1L)
            TvStore.putLong(PolicyKeys.MODE_VERSION, version)
        }
        TvStore.putString(PolicyKeys.LOCAL_MODE, mode.name)
        TvStore.sync()
        return version
    }
}

/**
 * A running rule session. The first three fields exist since V5; the rest were added in V6 and
 * default to "unknown" so a session saved by an older build still restores after an upgrade.
 */
data class SavedLocalSession(
    val totalSeconds: Long,
    val remainingSeconds: Long,
    val endReason: LocalSessionEnd,
    val sessionId: String = "",
    val templateVersion: Long = 0L,
    val restAfterSeconds: Long = 0L,
    val startedAtEpochMs: Long = 0L,
    val usageDate: String = "",
    val deadlineEpochMs: Long = 0L,
    /** Fixed wall deadline including screen-off grace; 0 on sessions saved before V11. */
    val wallDeadlineEpochMs: Long = 0L,
    /** Non-zero while the screen is off. Pausing never consumes budget. */
    val pausedAtEpochMs: Long = 0L,
    /** The session ran while the remote rule guard owned the child mode. */
    val reportUsage: Boolean = false,
    /** The manual session was started by a parent-confirmed local action, not a phone grant. */
    val startedLocally: Boolean = false,
    /** AUTO follows the scheduler; MANUAL was started by a parent action. */
    val activation: PolicyActivation = PolicyActivation.AUTO,
    val accessMode: AccessMode = AccessMode.SCREEN,
    val allowedPackages: Set<String> = emptySet(),
)

/**
 * Outcome of reading `local_rules_v2`. [Missing] is a fresh install or a pre-v2 upgrade and may
 * start the one-way v1 migration; [Corrupt] must not be silently replaced by v1 because that
 * would discard the inactive set without a trace.
 */
sealed interface LocalRuleSetsLoad {
    data class Loaded(val ruleSets: LocalRuleSets) : LocalRuleSetsLoad

    object Missing : LocalRuleSetsLoad

    data class Corrupt(val reason: String) : LocalRuleSetsLoad
}

/**
 * Product-owned local persistence. The rules JSON carries a schema version so later fields can
 * be added without breaking an installed TV; the PIN/recovery values are PBKDF2 hashes only.
 */
object LocalPolicyStore {
    /** First run defaults to manual: the parent deliberately starts each segment. */
    fun loadActivation(): Activation =
        if (TvStore.isReady) {
            TvStore.getString(PolicyKeys.LOCAL_ACTIVATION, null)
                ?.let { runCatching { Activation.valueOf(it) }.getOrNull() }
                ?: Activation.MANUAL
        } else {
            Activation.MANUAL
        }

    fun saveActivation(activation: Activation) {
        if (!TvStore.isReady || loadActivation() == activation) return
        TvStore.putString(PolicyKeys.LOCAL_ACTIVATION, activation.name)
        bumpEpoch()
    }

    fun policyEpoch(): Long =
        if (TvStore.isReady) TvStore.getLong(PolicyKeys.LOCAL_POLICY_EPOCH, 0L) else 0L

    /**
     * Exchange records this TV has already consumed. Kept across restarts so a grant poll that
     * still reports an old record with costTime=0 cannot arm a second pending gate.
     */
    fun settledGrantRecordIds(): Set<Int> {
        if (!TvStore.isReady) return emptySet()
        return TvStore.getString(PolicyKeys.LOCAL_SETTLED_GRANT_RECORDS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?.toSet()
            .orEmpty()
    }

    fun saveSettledGrantRecordIds(ids: Collection<Int>) {
        if (!TvStore.isReady) return
        val clean = ids.filter { it > 0 }
        if (clean.isEmpty()) {
            TvStore.remove(PolicyKeys.LOCAL_SETTLED_GRANT_RECORDS)
        } else {
            TvStore.putString(PolicyKeys.LOCAL_SETTLED_GRANT_RECORDS, clean.joinToString(","))
        }
        TvStore.sync()
    }

    fun clearSettledGrantRecordIds() {
        if (!TvStore.isReady) return
        TvStore.remove(PolicyKeys.LOCAL_SETTLED_GRANT_RECORDS)
        TvStore.sync()
    }

    fun bumpEpoch(): Long {
        if (!TvStore.isReady) return 0L
        val next = maxOf(System.currentTimeMillis(), policyEpoch() + 1L)
        TvStore.putLong(PolicyKeys.LOCAL_POLICY_EPOCH, next)
        TvStore.sync()
        return next
    }

    fun autoBlockKey(): String =
        if (TvStore.isReady) {
            TvStore.getString(PolicyKeys.LOCAL_AUTO_BLOCK, "").orEmpty()
        } else {
            ""
        }

    fun saveAutoBlockKey(key: String) {
        if (!TvStore.isReady) return
        if (key.isEmpty()) TvStore.remove(PolicyKeys.LOCAL_AUTO_BLOCK)
        else TvStore.putString(PolicyKeys.LOCAL_AUTO_BLOCK, key)
        TvStore.sync()
    }

    fun autoReady(): Boolean =
        TvStore.isReady && TvStore.getBoolean(PolicyKeys.LOCAL_AUTO_READY, false)

    fun saveAutoReady(ready: Boolean) {
        if (!TvStore.isReady) return
        if (ready) TvStore.putBoolean(PolicyKeys.LOCAL_AUTO_READY, true)
        else TvStore.remove(PolicyKeys.LOCAL_AUTO_READY)
        TvStore.sync()
    }

    /** True only while a parent temporary unlock is active; never restored as an unlock. */
    fun parentOverrideActive(): Boolean =
        TvStore.isReady && TvStore.getBoolean(PolicyKeys.LOCAL_PARENT_OVERRIDE, false)

    fun saveParentOverrideActive(active: Boolean) {
        if (!TvStore.isReady) return
        if (active) TvStore.putBoolean(PolicyKeys.LOCAL_PARENT_OVERRIDE, true)
        else TvStore.remove(PolicyKeys.LOCAL_PARENT_OVERRIDE)
        TvStore.sync()
    }

    fun loadRules(): LocalRules? {
        if (!TvStore.isReady) return null
        val raw = TvStore.getString(PolicyKeys.LOCAL_RULES, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            // Missing fields fall back to the defaults of the activation saved on this TV, so
            // manual and automatic first-run rules keep their own rest defaults.
            val defaults = LocalRules.defaultsFor(loadActivation())
            val schemaVersion = json.optInt("schemaVersion", 1)
            // Schema 1 omitted the app list (or stored an empty list) to mean all apps. Schema 2
            // always writes the field; a missing field there is malformed, not permission to
            // widen the scope.
            val allowedPackages = when {
                json.has("allowedPackages") ->
                    AllowedPackagesCodec.parse(json.optJSONArray("allowedPackages"))
                        ?: return@runCatching null
                schemaVersion < LocalRules.CURRENT_SCHEMA_VERSION -> defaults.allowedPackages
                else -> return@runCatching null
            }
            val studyPackages = when {
                json.has("studyPackages") ->
                    AllowedPackagesCodec.parse(json.optJSONArray("studyPackages"))
                        ?: return@runCatching null
                else -> emptySet()
            }
            val autoDailyLimitMinutes = when {
                json.has("autoDailyLimitMinutes") -> json.optInt("autoDailyLimitMinutes")
                else -> json.optInt("dailyLimitMinutes", defaults.autoDailyLimitMinutes)
            }
            val rules = LocalRules(
                weekdayWindows = json.optJSONArray("weekday")?.toWindows().orEmpty(),
                weekendWindows = json.optJSONArray("weekend")?.toWindows().orEmpty(),
                autoDailyLimitMinutes = autoDailyLimitMinutes,
                sessionLimitMinutes = json.optInt("sessionLimitMinutes", defaults.sessionLimitMinutes),
                restMinutes = json.optInt("restMinutes", defaults.restMinutes),
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
            rules.takeIf { it.isUsable }
        }.getOrNull()
    }

    fun saveRules(rules: LocalRules) {
        if (!TvStore.isReady) return
        writeLegacyRules(rules)
        TvStore.sync()
        bumpEpoch()
    }

    /**
     * Strict read of `local_rules_v2`. Unlike v1 there are no per-field defaults: a v2 document
     * missing windows, limits or the app scope is corrupt, and the caller must fail closed
     * instead of silently substituting values.
     */
    fun loadRuleSets(): LocalRuleSetsLoad {
        if (!TvStore.isReady) return LocalRuleSetsLoad.Missing
        val raw = TvStore.getString(PolicyKeys.LOCAL_RULES_V2, null)
            ?: return LocalRuleSetsLoad.Missing
        val parsed = runCatching { parseRuleSetsJson(JSONObject(raw)) }
            .getOrElse { return LocalRuleSetsLoad.Corrupt(it.javaClass.simpleName) }
            ?: return LocalRuleSetsLoad.Corrupt("invalid rule sets")
        return LocalRuleSetsLoad.Loaded(parsed)
    }

    /**
     * Persists both time-rule sets and the shared app scope. The active set is written through
     * to `local_rules_v1` so old builds and rollback still see the rules in effect. This does
     * not bump the epoch: callers decide whether the change must interrupt the running session.
     */
    fun saveRuleSets(ruleSets: LocalRuleSets, activation: Activation) {
        if (!TvStore.isReady) return
        TvStore.putString(
            PolicyKeys.LOCAL_RULES_V2,
            ruleSetsToJson(ruleSets, activation).toString(),
        )
        writeLegacyRules(ruleSets.rulesFor(activation))
        TvStore.sync()
    }

    private fun writeLegacyRules(rules: LocalRules) {
        val normalized = rules.normalized()
        val json = JSONObject()
            .put("schemaVersion", normalized.schemaVersion)
            .put("weekday", normalized.weekdayWindows.toJson())
            .put("weekend", normalized.weekendWindows.toJson())
            .put("autoDailyLimitMinutes", normalized.autoDailyLimitMinutes)
            // Keep the V6 key while the backend and old installs still read it.
            .put("dailyLimitMinutes", normalized.autoDailyLimitMinutes)
            .put("sessionLimitMinutes", normalized.sessionLimitMinutes)
            .put("restMinutes", normalized.restMinutes)
            .put("sessionScreenOffBufferMinutes", normalized.sessionScreenOffBufferMinutes)
            .put("allowedPackages", AllowedPackagesCodec.toJson(normalized.effectiveAllowedPackages))
            .put("studyPackages", AllowedPackagesCodec.toJson(normalized.effectiveStudyPackages))
        TvStore.putString(PolicyKeys.LOCAL_RULES, json.toString())
    }

    private fun ruleSetsToJson(
        ruleSets: LocalRuleSets,
        activation: Activation,
    ): JSONObject = JSONObject()
        .put("schemaVersion", LocalRuleSets.CURRENT_SCHEMA_VERSION)
        .put("activation", activation.name)
        .put("auto", ruleSets.auto.toJson())
        .put("manual", ruleSets.manual.toJson())
        .put(
            "apps",
            JSONObject()
                .put("allowedPackages", AllowedPackagesCodec.toJson(ruleSets.shared.allowedPackages))
                .put("studyPackages", AllowedPackagesCodec.toJson(ruleSets.shared.studyPackages))
                // Legacy mirror: old builds read the buffer here. Always write the value of the
                // activation currently in effect, so a switch cannot leave a stale copy.
                .put(
                    "sessionScreenOffBufferMinutes",
                    ruleSets.rulesFor(activation).sessionScreenOffBufferMinutes,
                ),
        )

    private fun parseRuleSetsJson(json: JSONObject): LocalRuleSets? {
        if (json.optInt("schemaVersion", -1) != LocalRuleSets.CURRENT_SCHEMA_VERSION) return null
        val auto = parseStoredRuleSet(json.optJSONObject("auto")) ?: return null
        val manual = parseStoredRuleSet(json.optJSONObject("manual")) ?: return null
        val apps = json.optJSONObject("apps") ?: return null
        val allowed = AllowedPackagesCodec.parse(apps.optJSONArray("allowedPackages"))
            ?: return null
        val study = AllowedPackagesCodec.parse(apps.optJSONArray("studyPackages"))
            ?: return null
        val buffer = apps.optInt("sessionScreenOffBufferMinutes", -1)
        if (buffer !in LocalRules.MIN_SESSION_BUFFER_MINUTES..LocalRules.MAX_SESSION_BUFFER_MINUTES) {
            return null
        }
        return LocalRuleSets(
            auto = auto,
            manual = manual,
            shared = LocalSharedAppScope(
                allowedPackages = allowed,
                studyPackages = study,
                sessionScreenOffBufferMinutes = buffer,
            ),
        )
    }

    private fun parseStoredRuleSet(json: JSONObject?): LocalRuleSet? {
        if (json == null || !json.has("weekday") || !json.has("weekend")) return null
        val weekday = json.optJSONArray("weekday")?.toWindowsStrict() ?: return null
        val weekend = json.optJSONArray("weekend")?.toWindowsStrict() ?: return null
        val daily = json.optInt("autoDailyLimitMinutes", -1)
        val session = json.optInt("sessionLimitMinutes", -1)
        val rest = json.optInt("restMinutes", -1)
        if (daily < 0 || session < 0 || rest < 0) return null
        // Optional: documents written before the per-set split omit it and are materialized
        // with the shared `apps` value. A present but out-of-range value is corrupt.
        val buffer = if (json.has("sessionScreenOffBufferMinutes")) {
            val value = json.optInt("sessionScreenOffBufferMinutes", -1)
            if (value !in LocalRules.MIN_SESSION_BUFFER_MINUTES..LocalRules.MAX_SESSION_BUFFER_MINUTES) {
                return null
            }
            value
        } else {
            null
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

    val pinConfigured: Boolean
        get() = TvStore.isReady &&
            !TvStore.getString(PolicyKeys.LOCAL_PIN_SALT, null).isNullOrEmpty() &&
            !TvStore.getString(PolicyKeys.LOCAL_PIN_HASH, null).isNullOrEmpty()

    fun setPin(pin: String) {
        require(LocalPin.isValid(pin))
        // Keep the logical password unified: TvPinStore writes the local and cloud copies.
        TvPinStore.set(pin)
    }

    fun verifyPin(pin: String): PinResult {
        if (!TvStore.isReady || !pinConfigured) return PinResult.NOT_SET
        val now = System.currentTimeMillis()
        var failed = TvStore.getInt(PolicyKeys.LOCAL_PIN_FAILED, 0)
        var lockoutUntil = TvStore.getLong(PolicyKeys.LOCAL_PIN_LOCKOUT, 0L)
        if (lockoutUntil > 0 && now >= lockoutUntil) {
            failed = 0
            lockoutUntil = 0L
            TvStore.putInt(PolicyKeys.LOCAL_PIN_FAILED, 0)
            TvStore.putLong(PolicyKeys.LOCAL_PIN_LOCKOUT, 0L)
        }
        if (lockoutUntil > now) return PinResult.LOCKED_OUT
        val salt = TvStore.getString(PolicyKeys.LOCAL_PIN_SALT, null).orEmpty()
        val hash = TvStore.getString(PolicyKeys.LOCAL_PIN_HASH, null).orEmpty()
        if (LocalPin.verify(pin, salt, hash)) {
            TvStore.putInt(PolicyKeys.LOCAL_PIN_FAILED, 0)
            TvStore.putLong(PolicyKeys.LOCAL_PIN_LOCKOUT, 0L)
            TvStore.sync()
            return PinResult.OK
        }
        failed += 1
        if (failed >= LocalPin.MAX_FAILED) {
            TvStore.putInt(PolicyKeys.LOCAL_PIN_FAILED, 0)
            TvStore.putLong(PolicyKeys.LOCAL_PIN_LOCKOUT, now + LocalPin.LOCKOUT_MS)
            TvStore.sync()
            return PinResult.LOCKED_OUT
        }
        TvStore.putInt(PolicyKeys.LOCAL_PIN_FAILED, failed)
        return PinResult.WRONG
    }

    fun setRecovery(code: String) {
        val salt = LocalRecovery.newSalt()
        val hash = LocalRecovery.hash(code, salt) ?: return
        TvStore.putString(PolicyKeys.LOCAL_RECOVERY_SALT, salt)
        TvStore.putString(PolicyKeys.LOCAL_RECOVERY_HASH, hash)
        TvStore.putInt(PolicyKeys.LOCAL_RECOVERY_FAILED, 0)
        TvStore.putLong(PolicyKeys.LOCAL_RECOVERY_LOCKOUT, 0L)
        TvStore.sync()
    }

    val recoveryConfigured: Boolean
        get() = TvStore.isReady &&
            !TvStore.getString(PolicyKeys.LOCAL_RECOVERY_SALT, null).isNullOrEmpty() &&
            !TvStore.getString(PolicyKeys.LOCAL_RECOVERY_HASH, null).isNullOrEmpty()

    fun verifyRecovery(code: String): PinResult {
        if (!TvStore.isReady || !recoveryConfigured) return PinResult.NOT_SET
        val now = System.currentTimeMillis()
        var failed = TvStore.getInt(PolicyKeys.LOCAL_RECOVERY_FAILED, 0)
        var lockoutUntil = TvStore.getLong(PolicyKeys.LOCAL_RECOVERY_LOCKOUT, 0L)
        if (lockoutUntil > 0 && now >= lockoutUntil) {
            failed = 0
            lockoutUntil = 0L
            TvStore.putInt(PolicyKeys.LOCAL_RECOVERY_FAILED, 0)
            TvStore.putLong(PolicyKeys.LOCAL_RECOVERY_LOCKOUT, 0L)
        }
        if (lockoutUntil > now) return PinResult.LOCKED_OUT
        val salt = TvStore.getString(PolicyKeys.LOCAL_RECOVERY_SALT, null).orEmpty()
        val hash = TvStore.getString(PolicyKeys.LOCAL_RECOVERY_HASH, null).orEmpty()
        if (LocalRecovery.verify(code, salt, hash)) {
            TvStore.putInt(PolicyKeys.LOCAL_RECOVERY_FAILED, 0)
            TvStore.putLong(PolicyKeys.LOCAL_RECOVERY_LOCKOUT, 0L)
            TvStore.sync()
            return PinResult.OK
        }
        failed += 1
        if (failed >= LocalPin.MAX_FAILED) {
            TvStore.putInt(PolicyKeys.LOCAL_RECOVERY_FAILED, 0)
            TvStore.putLong(PolicyKeys.LOCAL_RECOVERY_LOCKOUT, now + LocalPin.LOCKOUT_MS)
            TvStore.sync()
            return PinResult.LOCKED_OUT
        }
        TvStore.putInt(PolicyKeys.LOCAL_RECOVERY_FAILED, failed)
        return PinResult.WRONG
    }

    /** Daily usage is keyed by the TV's local date; a new date starts at zero. */
    fun usedSeconds(date: String): Long {
        if (!TvStore.isReady) return 0
        val storedDate = TvStore.getString(PolicyKeys.LOCAL_DAY_DATE, "")
        return if (storedDate == date) TvStore.getLong(PolicyKeys.LOCAL_DAY_USED, 0L) else 0L
    }

    fun saveUsed(date: String, seconds: Long) {
        if (!TvStore.isReady) return
        TvStore.putString(PolicyKeys.LOCAL_DAY_DATE, date)
        TvStore.putLong(PolicyKeys.LOCAL_DAY_USED, seconds.coerceAtLeast(0L))
        TvStore.sync()
    }

    fun breakUntil(): Long = if (TvStore.isReady) TvStore.getLong(PolicyKeys.LOCAL_BREAK_UNTIL, 0L) else 0L

    fun saveBreakUntil(epochMillis: Long) {
        if (!TvStore.isReady) return
        if (epochMillis <= 0) TvStore.remove(PolicyKeys.LOCAL_BREAK_UNTIL)
        else TvStore.putLong(PolicyKeys.LOCAL_BREAK_UNTIL, epochMillis)
        TvStore.sync()
    }

    /** Wall-clock end of the last rest-worthy child segment, used to recover the rest cursor. */
    fun lastSessionEndedAt(): Long =
        if (TvStore.isReady) TvStore.getLong(PolicyKeys.LOCAL_LAST_SESSION_ENDED_AT, 0L) else 0L

    fun saveLastSessionEndedAt(epochMillis: Long) {
        if (!TvStore.isReady) return
        if (epochMillis <= 0) TvStore.remove(PolicyKeys.LOCAL_LAST_SESSION_ENDED_AT)
        else TvStore.putLong(PolicyKeys.LOCAL_LAST_SESSION_ENDED_AT, epochMillis)
        TvStore.sync()
    }

    fun loadSession(): SavedLocalSession? {
        if (!TvStore.isReady) return null
        val total = TvStore.getLong(PolicyKeys.LOCAL_SESSION_TOTAL, 0L)
        val remaining = TvStore.getLong(PolicyKeys.LOCAL_SESSION_REMAINING, 0L)
        if (total <= 0 || remaining <= 0) return null
        val end = TvStore.getString(PolicyKeys.LOCAL_SESSION_END, null)
            ?.let { runCatching { LocalSessionEnd.valueOf(it) }.getOrNull() }
            ?: return null
        return SavedLocalSession(
            totalSeconds = total,
            remainingSeconds = remaining,
            endReason = end,
            sessionId = TvStore.getString(PolicyKeys.LOCAL_SESSION_ID, null).orEmpty(),
            templateVersion = TvStore.getLong(PolicyKeys.LOCAL_SESSION_TEMPLATE_VERSION, 0L),
            restAfterSeconds = TvStore.getLong(PolicyKeys.LOCAL_SESSION_REST_AFTER, 0L),
            startedAtEpochMs = TvStore.getLong(PolicyKeys.LOCAL_SESSION_STARTED_AT, 0L),
            usageDate = TvStore.getString(PolicyKeys.LOCAL_SESSION_USAGE_DATE, null).orEmpty(),
            deadlineEpochMs = TvStore.getLong(PolicyKeys.LOCAL_SESSION_DEADLINE, 0L),
            wallDeadlineEpochMs = TvStore.getLong(PolicyKeys.LOCAL_SESSION_WALL_DEADLINE, 0L),
            pausedAtEpochMs = TvStore.getLong(PolicyKeys.LOCAL_SESSION_PAUSED_AT, 0L),
            reportUsage = TvStore.getBoolean(PolicyKeys.LOCAL_SESSION_REPORT_USAGE, false),
            startedLocally = TvStore.getBoolean(PolicyKeys.LOCAL_SESSION_STARTED_LOCALLY, false),
            activation = TvStore.getString(PolicyKeys.LOCAL_SESSION_ACTIVATION, null)
                ?.let { runCatching { PolicyActivation.valueOf(it) }.getOrNull() }
                ?: PolicyActivation.AUTO,
            accessMode = TvStore.getString(PolicyKeys.LOCAL_SESSION_ACCESS_MODE, null)
                ?.let { runCatching { AccessMode.valueOf(it) }.getOrNull() }
                ?: AccessMode.SCREEN,
            allowedPackages = TvStore.getString(PolicyKeys.LOCAL_SESSION_ALLOWED_PACKAGES, null)
                ?.let(AllowedPackagesCodec::parseString)
                .orEmpty(),
        )
    }

    fun saveSession(session: SavedLocalSession) {
        if (!TvStore.isReady) return
        TvStore.putLong(PolicyKeys.LOCAL_SESSION_TOTAL, session.totalSeconds)
        TvStore.putLong(PolicyKeys.LOCAL_SESSION_REMAINING, session.remainingSeconds)
        TvStore.putString(PolicyKeys.LOCAL_SESSION_END, session.endReason.name)
        TvStore.putString(PolicyKeys.LOCAL_SESSION_ID, session.sessionId)
        TvStore.putLong(PolicyKeys.LOCAL_SESSION_TEMPLATE_VERSION, session.templateVersion)
        TvStore.putLong(PolicyKeys.LOCAL_SESSION_REST_AFTER, session.restAfterSeconds)
        TvStore.putLong(PolicyKeys.LOCAL_SESSION_STARTED_AT, session.startedAtEpochMs)
        TvStore.putString(PolicyKeys.LOCAL_SESSION_USAGE_DATE, session.usageDate)
        TvStore.putLong(PolicyKeys.LOCAL_SESSION_DEADLINE, session.deadlineEpochMs)
        TvStore.putLong(PolicyKeys.LOCAL_SESSION_WALL_DEADLINE, session.wallDeadlineEpochMs)
        TvStore.putLong(PolicyKeys.LOCAL_SESSION_PAUSED_AT, session.pausedAtEpochMs)
        TvStore.putBoolean(PolicyKeys.LOCAL_SESSION_REPORT_USAGE, session.reportUsage)
        TvStore.putBoolean(PolicyKeys.LOCAL_SESSION_STARTED_LOCALLY, session.startedLocally)
        TvStore.putString(PolicyKeys.LOCAL_SESSION_ACTIVATION, session.activation.name)
        TvStore.putString(PolicyKeys.LOCAL_SESSION_ACCESS_MODE, session.accessMode.name)
        TvStore.putString(
            PolicyKeys.LOCAL_SESSION_ALLOWED_PACKAGES,
            AllowedPackagesCodec.toJsonString(session.allowedPackages),
        )
    }

    fun clearSession() {
        if (!TvStore.isReady) return
        listOf(
            PolicyKeys.LOCAL_SESSION_TOTAL,
            PolicyKeys.LOCAL_SESSION_REMAINING,
            PolicyKeys.LOCAL_SESSION_END,
            PolicyKeys.LOCAL_SESSION_ID,
            PolicyKeys.LOCAL_SESSION_TEMPLATE_VERSION,
            PolicyKeys.LOCAL_SESSION_REST_AFTER,
            PolicyKeys.LOCAL_SESSION_STARTED_AT,
            PolicyKeys.LOCAL_SESSION_USAGE_DATE,
            PolicyKeys.LOCAL_SESSION_DEADLINE,
            PolicyKeys.LOCAL_SESSION_WALL_DEADLINE,
            PolicyKeys.LOCAL_SESSION_PAUSED_AT,
            PolicyKeys.LOCAL_SESSION_REPORT_USAGE,
            PolicyKeys.LOCAL_SESSION_STARTED_LOCALLY,
            PolicyKeys.LOCAL_SESSION_ACTIVATION,
            PolicyKeys.LOCAL_SESSION_ACCESS_MODE,
            PolicyKeys.LOCAL_SESSION_ALLOWED_PACKAGES,
        ).forEach { TvStore.remove(it) }
        TvStore.sync()
    }

    /** Removes local rules/PIN/recovery but keeps the mode and cloud data. Used by recovery reset. */
    fun resetLocalSetup() {
        if (!TvStore.isReady) return
        listOf(
            PolicyKeys.LOCAL_RULES,
            PolicyKeys.LOCAL_RULES_V2,
            PolicyKeys.LOCAL_ACTIVATION,
            PolicyKeys.LOCAL_POLICY_EPOCH,
            PolicyKeys.LOCAL_AUTO_BLOCK,
            PolicyKeys.LOCAL_AUTO_READY,
            PolicyKeys.LOCAL_PARENT_OVERRIDE,
            PolicyKeys.LOCAL_PIN_SALT,
            PolicyKeys.LOCAL_PIN_HASH,
            PolicyKeys.LOCAL_PIN_FAILED,
            PolicyKeys.LOCAL_PIN_LOCKOUT,
            PolicyKeys.LOCAL_RECOVERY_SALT,
            PolicyKeys.LOCAL_RECOVERY_HASH,
            PolicyKeys.LOCAL_RECOVERY_FAILED,
            PolicyKeys.LOCAL_RECOVERY_LOCKOUT,
            PolicyKeys.LOCAL_BREAK_UNTIL,
            PolicyKeys.LOCAL_LAST_SESSION_ENDED_AT,
            PolicyKeys.LOCAL_SESSION_TOTAL,
            PolicyKeys.LOCAL_SESSION_REMAINING,
            PolicyKeys.LOCAL_SESSION_END,
            PolicyKeys.LOCAL_SESSION_ID,
            PolicyKeys.LOCAL_SESSION_TEMPLATE_VERSION,
            PolicyKeys.LOCAL_SESSION_REST_AFTER,
            PolicyKeys.LOCAL_SESSION_STARTED_AT,
            PolicyKeys.LOCAL_SESSION_USAGE_DATE,
            PolicyKeys.LOCAL_SESSION_DEADLINE,
            PolicyKeys.LOCAL_SESSION_WALL_DEADLINE,
            PolicyKeys.LOCAL_SESSION_PAUSED_AT,
            PolicyKeys.LOCAL_SESSION_REPORT_USAGE,
            PolicyKeys.LOCAL_SESSION_STARTED_LOCALLY,
            PolicyKeys.LOCAL_SESSION_ACTIVATION,
            PolicyKeys.LOCAL_SESSION_ACCESS_MODE,
            PolicyKeys.LOCAL_SESSION_ALLOWED_PACKAGES,
        ).forEach { TvStore.remove(it) }
        TvStore.sync()
    }
}

private fun List<LocalTimeWindow>.toJson(): JSONArray = JSONArray().also { array ->
    forEach { window ->
        array.put(JSONArray().put(window.startMinute).put(window.endMinute))
    }
}

private fun LocalRuleSet.toJson(): JSONObject = JSONObject()
    .put("weekday", weekdayWindows.toJson())
    .put("weekend", weekendWindows.toJson())
    .put("autoDailyLimitMinutes", autoDailyLimitMinutes)
    .put("sessionLimitMinutes", sessionLimitMinutes)
    .put("restMinutes", restMinutes)
    .apply {
        // Null marks a legacy set that has not been edited since the buffer split; writing an
        // invented value would silently change the other mode's behaviour.
        sessionScreenOffBufferMinutes?.let { put("sessionScreenOffBufferMinutes", it) }
    }

private fun JSONArray.toWindows(): List<LocalTimeWindow> = buildList {
    for (i in 0 until length()) {
        val pair = optJSONArray(i) ?: continue
        val start = pair.optInt(0, -1)
        val end = pair.optInt(1, -1)
        if (start >= 0 && end > start) add(LocalTimeWindow(start, end))
    }
}

/** Strict variant for v2: any malformed entry invalidates the whole document. */
private fun JSONArray.toWindowsStrict(): List<LocalTimeWindow>? = buildList {
    for (i in 0 until length()) {
        val pair = optJSONArray(i) ?: return null
        val start = pair.optInt(0, -1)
        val end = pair.optInt(1, -1)
        if (start < 0 || end <= start) return null
        add(LocalTimeWindow(start, end))
    }
}
