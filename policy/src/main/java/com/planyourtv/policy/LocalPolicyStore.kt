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
    /** Every session is started by a parent action. */
    val activation: PolicyActivation = PolicyActivation.MANUAL,
    val accessMode: AccessMode = AccessMode.SCREEN,
    val allowedPackages: Set<String> = emptySet(),
    /** V45 parent-started session outside the child plan; never writes the daily ledger. */
    val outsidePlan: Boolean = false,
)

/**
 * A focused study round. Stored separately from the runtime snapshot because it is a transient
 * timer, not part of the entertainment session/ledger contract. [boot] proves the round belongs
 * to the current boot; a restart restores it paused and drops it when the boot changed.
 *
 * [totalSeconds]/[remainingSeconds] are the study allowance for this round, [studiedSeconds] is
 * the accumulated focused time used for the X-minute reward trigger, and [usageDate] is the
 * study-ledger date the round settles into.
 */
data class SavedStudyRound(
    val studyPackage: String,
    val targetPackage: String?,
    val totalSeconds: Long,
    val remainingSeconds: Long,
    val studiedSeconds: Long = 0L,
    val rewardSeconds: Long = 0L,
    val rewardEnabled: Boolean = false,
    val rewardAttempted: Boolean = false,
    val rewardGranted: Boolean = false,
    val usageDate: String = "",
    val startedAtEpochMs: Long,
    val boot: BootEvidence = BootEvidence(),
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
    /**
     * [hosted] selects the cloud-owned execution partition. LOCAL and HOSTED keep separate
     * epochs, grants, rules and rounds so leaving the cloud cannot leak the cloud ledger into
     * the TV's own child mode (and vice versa).
     */
    fun policyEpoch(hosted: Boolean = false, hostedScope: String? = null): Long =
        if (TvStore.isReady) {
            TvStore.getLong(
                if (hosted) {
                    PolicyKeys.hostedScoped(PolicyKeys.HOSTED_POLICY_EPOCH, hostedScope)
                } else {
                    PolicyKeys.LOCAL_POLICY_EPOCH
                },
                0L,
            )
        } else {
            0L
        }

    /**
     * Exchange records this TV has already consumed. Kept across restarts so a grant poll that
     * still reports an old record with costTime=0 cannot arm a second pending gate.
     */
    fun settledGrantRecordIds(
        hosted: Boolean = false,
        hostedScope: String? = null,
    ): Set<Int> {
        if (!TvStore.isReady) return emptySet()
        val key = if (hosted) {
            PolicyKeys.hostedScoped(PolicyKeys.HOSTED_SETTLED_GRANT_RECORDS, hostedScope)
        } else {
            PolicyKeys.LOCAL_SETTLED_GRANT_RECORDS
        }
        return TvStore.getString(key, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it > 0 }
            ?.toSet()
            .orEmpty()
    }

    fun saveSettledGrantRecordIds(
        ids: Collection<Int>,
        hosted: Boolean = false,
        hostedScope: String? = null,
    ) {
        if (!TvStore.isReady) return
        val key = if (hosted) {
            PolicyKeys.hostedScoped(PolicyKeys.HOSTED_SETTLED_GRANT_RECORDS, hostedScope)
        } else {
            PolicyKeys.LOCAL_SETTLED_GRANT_RECORDS
        }
        val clean = ids.filter { it > 0 }
        if (clean.isEmpty()) {
            TvStore.remove(key)
        } else {
            TvStore.putString(key, clean.joinToString(","))
        }
        TvStore.sync()
    }

    fun clearSettledGrantRecordIds(hosted: Boolean = false, hostedScope: String? = null) {
        if (!TvStore.isReady) return
        TvStore.remove(
            if (hosted) {
                PolicyKeys.hostedScoped(PolicyKeys.HOSTED_SETTLED_GRANT_RECORDS, hostedScope)
            } else {
                PolicyKeys.LOCAL_SETTLED_GRANT_RECORDS
            },
        )
        TvStore.sync()
    }

    fun bumpEpoch(hosted: Boolean = false, hostedScope: String? = null): Long {
        if (!TvStore.isReady) return 0L
        val key = if (hosted) {
            PolicyKeys.hostedScoped(PolicyKeys.HOSTED_POLICY_EPOCH, hostedScope)
        } else {
            PolicyKeys.LOCAL_POLICY_EPOCH
        }
        val next = maxOf(System.currentTimeMillis(), policyEpoch(hosted, hostedScope) + 1L)
        TvStore.putLong(key, next)
        TvStore.sync()
        return next
    }

    /**
     * Remembers a cloud member that owns a scoped hosted partition, so an unbind can drop all
     * of them without wiping the TV's own LOCAL state.
     */
    fun rememberHostedSubject(subjectId: Long) {
        if (!TvStore.isReady || subjectId <= 0L) return
        val ids = hostedSubjects().toMutableSet()
        if (!ids.add(subjectId)) return
        TvStore.putString(
            PolicyKeys.HOSTED_SUBJECT_INDEX_V1,
            ids.sorted().joinToString(","),
        )
        TvStore.sync()
    }

    fun hostedSubjects(): List<Long> {
        if (!TvStore.isReady) return emptyList()
        return TvStore.getString(PolicyKeys.HOSTED_SUBJECT_INDEX_V1, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it > 0L }
            .orEmpty()
    }

    /**
     * The pre-2026-11-21 cloud partition was device-global, so switching members leaked the
     * previous child's ledger and rest cursor. Adopting it into the first scoped member is the
     * only non-destructive migration: that member keeps the usage already recorded today and
     * every other member starts from an honest empty partition. The caller logs the adoption.
     */
    fun adoptUnscopedHostedPartition(subjectId: Long): Boolean {
        if (!TvStore.isReady || subjectId <= 0L) return false
        if (TvStore.getBoolean(PolicyKeys.HOSTED_LEGACY_ADOPTED_V1, false)) return false
        val scope = hostedScopeOf(subjectId)
        val scopedRuntime = PolicyKeys.hostedScoped(PolicyKeys.HOSTED_RUNTIME_V2, scope)
        if (TvStore.getString(scopedRuntime, null) != null) return false
        TvStore.putBoolean(PolicyKeys.HOSTED_LEGACY_ADOPTED_V1, true)
        var copied = false
        listOf(
            PolicyKeys.HOSTED_RUNTIME_V2,
            PolicyKeys.HOSTED_GUARD_V2,
            PolicyKeys.HOSTED_STUDY_ROUND_V1,
        ).forEach { base ->
            val raw = TvStore.getString(base, null) ?: return@forEach
            TvStore.putString(PolicyKeys.hostedScoped(base, scope), raw)
            copied = true
        }
        val epoch = TvStore.getLong(PolicyKeys.HOSTED_POLICY_EPOCH, 0L)
        if (epoch > 0L) {
            TvStore.putLong(
                PolicyKeys.hostedScoped(PolicyKeys.HOSTED_POLICY_EPOCH, scope),
                epoch,
            )
            copied = true
        }
        val settled = TvStore.getString(PolicyKeys.HOSTED_SETTLED_GRANT_RECORDS, null)
        if (!settled.isNullOrEmpty()) {
            TvStore.putString(
                PolicyKeys.hostedScoped(PolicyKeys.HOSTED_SETTLED_GRANT_RECORDS, scope),
                settled,
            )
            copied = true
        }
        // The marker must persist even when there was nothing to adopt.
        TvStore.sync()
        return copied
    }

    /** Drops every cloud execution partition, scoped or not, without touching LOCAL state. */
    fun clearHostedExecution() {
        if (!TvStore.isReady) return
        val bases = listOf(
            PolicyKeys.HOSTED_POLICY_EPOCH,
            PolicyKeys.HOSTED_SETTLED_GRANT_RECORDS,
            PolicyKeys.HOSTED_RUNTIME_V2,
            PolicyKeys.HOSTED_GUARD_V2,
            PolicyKeys.HOSTED_STUDY_ROUND_V1,
        )
        bases.forEach { TvStore.remove(it) }
        hostedSubjects().forEach { id ->
            val scope = hostedScopeOf(id)
            bases.forEach { base ->
                TvStore.remove(PolicyKeys.hostedScoped(base, scope))
            }
        }
        TvStore.remove(PolicyKeys.HOSTED_SUBJECT_INDEX_V1)
        TvStore.remove(PolicyKeys.HOSTED_LEGACY_ADOPTED_V1)
        TvStore.sync()
    }

    fun hostedScopeOf(subjectId: Long): String? =
        if (subjectId > 0L) "member:$subjectId" else null

    /** True only while a parent temporary unlock is active; never restored as an unlock. */
    fun parentOverrideActive(): Boolean =
        TvStore.isReady && TvStore.getBoolean(PolicyKeys.LOCAL_PARENT_OVERRIDE, false)

    fun saveParentOverrideActive(active: Boolean) {
        if (!TvStore.isReady) return
        if (active) TvStore.putBoolean(PolicyKeys.LOCAL_PARENT_OVERRIDE, true)
        else TvStore.remove(PolicyKeys.LOCAL_PARENT_OVERRIDE)
        TvStore.sync()
    }

    /**
     * Flat rules reader kept for callers that predate rule sets. Schema 3 stores everything in
     * [PolicyKeys.LOCAL_RULES_V3], so this delegates and there is only one decoder.
     */
    fun loadRules(): LocalRules? =
        (loadRuleSets() as? LocalRuleSetsLoad.Loaded)?.ruleSets?.rules()

    fun saveRules(rules: LocalRules) {
        if (!TvStore.isReady) return
        saveRuleSets(LocalRuleSets.from(rules))
        bumpEpoch()
    }

    /**
     * Strict read of schema 3. Older documents are deliberately not migrated: V42 requires a
     * clean install, and a schema-2 document must fail closed as unconfigured rather than half
     * applying an older rule shape.
     */
    fun loadRuleSets(): LocalRuleSetsLoad {
        if (!TvStore.isReady) return LocalRuleSetsLoad.Missing
        val raw = TvStore.getString(PolicyKeys.LOCAL_RULES_V3, null)
            ?: return LocalRuleSetsLoad.Missing
        val parsed = runCatching { parseRuleSetsJson(JSONObject(raw)) }
            .getOrElse { return LocalRuleSetsLoad.Corrupt(it.javaClass.simpleName) }
            ?: return LocalRuleSetsLoad.Corrupt("invalid rule sets")
        return LocalRuleSetsLoad.Loaded(parsed)
    }

    /**
     * Persists the manual time-rule set and the shared app scope as schema 3. This key is the
     * only local rules store; no legacy mirror is written. This does not bump the epoch: callers
     * decide whether the change must interrupt the running session.
     */
    fun saveRuleSets(ruleSets: LocalRuleSets) {
        if (!TvStore.isReady) return
        TvStore.putString(
            PolicyKeys.LOCAL_RULES_V3,
            ruleSetsToJson(ruleSets).toString(),
        )
        TvStore.sync()
    }

    /**
     * V40 subject documents reuse the exact local rule-set contract. Keeping the encoder and
     * decoder in this file means `local_rules_v3` and a subject config cannot drift into two
     * slightly different schemas.
     */
    internal fun encodeRuleSetsForSubject(ruleSets: LocalRuleSets): String =
        ruleSetsToJson(ruleSets).toString()

    internal fun decodeRuleSetsForSubject(raw: String?): LocalRuleSets? {
        if (raw.isNullOrBlank()) return null
        return runCatching { parseRuleSetsJson(JSONObject(raw)) }.getOrNull()
    }

    private fun ruleSetsToJson(ruleSets: LocalRuleSets): JSONObject = JSONObject()
        .put("schemaVersion", LocalRuleSets.CURRENT_SCHEMA_VERSION)
        .put("activation", Activation.MANUAL.name)
        .put("manual", ruleSets.manual.toJson())
        .put(
            "apps",
            JSONObject()
                .put("allowedPackages", AllowedPackagesCodec.toJson(ruleSets.shared.allowedPackages))
                .put("studyPackages", AllowedPackagesCodec.toJson(ruleSets.shared.studyPackages))
                .put(
                    "unlimitedPackages",
                    AllowedPackagesCodec.toJson(ruleSets.shared.unlimitedPackages),
                )
                .put(
                    "studyEarnPackages",
                    AllowedPackagesCodec.toJson(ruleSets.shared.studyEarnPackages),
                )
                .put("studyEarnStudyMinutes", ruleSets.shared.studyEarnStudyMinutes)
                .put("studyEarnRewardMinutes", ruleSets.shared.studyEarnRewardMinutes),
        )

    private fun parseRuleSetsJson(json: JSONObject): LocalRuleSets? {
        if (json.optInt("schemaVersion", -1) != LocalRuleSets.CURRENT_SCHEMA_VERSION) return null
        val manual = parseStoredRuleSet(json.optJSONObject("manual")) ?: return null
        val apps = json.optJSONObject("apps") ?: return null
        val allowed = AllowedPackagesCodec.parse(apps.optJSONArray("allowedPackages"))
            ?: return null
        val study = AllowedPackagesCodec.parse(apps.optJSONArray("studyPackages"))
            ?: return null
        val unlimited = AllowedPackagesCodec.parse(apps.optJSONArray("unlimitedPackages"))
            ?: return null
        val studyEarn = AllowedPackagesCodec.parse(apps.optJSONArray("studyEarnPackages"))
            ?: return null
        val studyEarnStudy = apps.optInt("studyEarnStudyMinutes", -1)
        val studyEarnReward = apps.optInt("studyEarnRewardMinutes", -1)
        if (studyEarnStudy !in LocalRules.MIN_STUDY_EARN_MINUTES..LocalRules.MAX_STUDY_EARN_MINUTES ||
            studyEarnReward !in LocalRules.MIN_STUDY_EARN_MINUTES..LocalRules.MAX_STUDY_EARN_MINUTES
        ) {
            return null
        }
        val sets = LocalRuleSets(
            manual = manual,
            shared = LocalSharedAppScope(
                allowedPackages = allowed,
                studyPackages = study,
                unlimitedPackages = unlimited,
                studyEarnPackages = studyEarn,
                studyEarnStudyMinutes = studyEarnStudy,
                studyEarnRewardMinutes = studyEarnReward,
            ),
        )
        val materialized = sets.rules()
        if (materialized.validationError() != LocalRulesError.NONE) return null
        return sets.withRules(materialized)
    }

    /**
     * Route counts are stored as Ints but are not product-capped. Missing values keep the
     * historical default; anything that is not an exact Int (including a value that would
     * wrap through [JSONObject.optInt]) rejects the document.
     */
    private fun JSONObject.storedRouteCount(key: String): Int? {
        if (!has(key)) return LocalRules.DEFAULT_ROUTE_DAILY_COUNT
        return storedIntInRange(key, -1, LocalRules.MAX_ROUTE_DAILY_COUNT)
    }

    /**
     * Exact whole-number read. `optInt` can wrap a value outside the Int range into a valid
     * small number, so a stored document is only accepted when it fits the declared bounds.
     */
    private fun JSONObject.storedIntInRange(key: String, min: Int, max: Int): Int? {
        if (!has(key)) return null
        val raw = opt(key)
        if (raw !is Number) return null
        val value = raw.toDouble()
        if (value != Math.floor(value) || value < min || value > max) return null
        return value.toInt()
    }

    private fun parseStoredRuleSet(json: JSONObject?): LocalRuleSet? {
        if (json == null) return null
        val groups = json.optJSONArray("dayGroups")?.toDayGroupsStrict() ?: return null
        val session = json.optInt("sessionLimitMinutes", -1)
        // Missing V44 fields are safe defaults: schema 3 existed before the shared cap and the
        // three daily counts, and old documents must keep loading.
        val cap = if (json.has("sessionCapMinutes")) {
            json.storedIntInRange(
                "sessionCapMinutes",
                LocalRules.MIN_SESSION_CAP_MINUTES,
                LocalRules.MAX_SESSION_CAP_MINUTES,
            ) ?: return null
        } else {
            LocalRules.DEFAULT_SESSION_CAP_MINUTES
        }
        val quizCount = json.storedRouteCount("quizDailyLimitCount") ?: return null
        val studyRewardCount = json.storedRouteCount("studyRewardDailyLimitCount") ?: return null
        val directFreeCount = json.storedRouteCount("directDailyFreeCount") ?: return null
        val rest = json.optInt("restMinutes", -1)
        val buffer = json.optInt("sessionScreenOffBufferMinutes", -1)
        if (session !in 1..LocalRules.MAX_DAILY_MINUTES ||
            cap !in LocalRules.MIN_SESSION_CAP_MINUTES..LocalRules.MAX_SESSION_CAP_MINUTES ||
            quizCount !in -1..LocalRules.MAX_ROUTE_DAILY_COUNT ||
            studyRewardCount !in -1..LocalRules.MAX_ROUTE_DAILY_COUNT ||
            directFreeCount !in -1..LocalRules.MAX_ROUTE_DAILY_COUNT ||
            rest !in 0..LocalRules.MAX_REST_MINUTES ||
            buffer !in LocalRules.MIN_SESSION_BUFFER_MINUTES..LocalRules.MAX_SESSION_BUFFER_MINUTES
        ) {
            return null
        }
        return LocalRuleSet(
            dayGroups = groups,
            sessionLimitMinutes = session,
            sessionCapMinutes = cap,
            restMinutes = rest,
            sessionScreenOffBufferMinutes = buffer,
            // V43 documents stored by a V42 build predate the route switches. The defaults
            // preserve the historical quiz + study behaviour.
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
            quizDailyLimitCount = quizCount,
            studyRewardDailyLimitCount = studyRewardCount,
            directDailyFreeCount = directFreeCount,
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

    /** Seconds left on the recovery-code lockout; zero once another attempt may run. */
    fun recoveryLockSeconds(): Long {
        if (!TvStore.isReady) return 0L
        val remaining = TvStore.getLong(PolicyKeys.LOCAL_RECOVERY_LOCKOUT, 0L) -
            System.currentTimeMillis()
        return if (remaining > 0L) (remaining + 999L) / 1000L else 0L
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
                ?: PolicyActivation.MANUAL,
            accessMode = TvStore.getString(PolicyKeys.LOCAL_SESSION_ACCESS_MODE, null)
                ?.let { runCatching { AccessMode.valueOf(it) }.getOrNull() }
                ?: AccessMode.SCREEN,
            allowedPackages = TvStore.getString(PolicyKeys.LOCAL_SESSION_ALLOWED_PACKAGES, null)
                ?.let(AllowedPackagesCodec::parseString)
                .orEmpty(),
            outsidePlan = TvStore.getBoolean(PolicyKeys.LOCAL_SESSION_OUTSIDE_PLAN, false),
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
        TvStore.putBoolean(PolicyKeys.LOCAL_SESSION_OUTSIDE_PLAN, session.outsidePlan)
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
            PolicyKeys.LOCAL_SESSION_OUTSIDE_PLAN,
        ).forEach { TvStore.remove(it) }
        TvStore.sync()
    }

    fun loadStudyRound(
        hosted: Boolean = false,
        hostedScope: String? = null,
    ): SavedStudyRound? {
        if (!TvStore.isReady) return null
        val key = if (hosted) {
            PolicyKeys.hostedScoped(PolicyKeys.HOSTED_STUDY_ROUND_V1, hostedScope)
        } else {
            PolicyKeys.LOCAL_STUDY_ROUND_V1
        }
        return decodeStudyRound(TvStore.getString(key, null))
    }

    /**
     * The V40 subject cache stores the same study-round document inside its runtime snapshot.
     * Sharing this codec keeps a migrated round readable by the legacy key and vice versa.
     */
    internal fun decodeStudyRound(raw: String?): SavedStudyRound? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schemaVersion", -1) != STUDY_ROUND_SCHEMA_VERSION) {
                return@runCatching null
            }
            val study = json.optString("studyPackage", "")
            val total = json.optLong("totalSeconds", -1L)
            val remaining = json.optLong("remainingSeconds", -1L)
            val studied = json.optLong("studiedSeconds", -1L)
            val reward = json.optLong("rewardSeconds", -1L)
            val rewardEnabled = json.optBoolean("rewardEnabled", false)
            val rewardAttempted = json.optBoolean("rewardAttempted", false)
            val rewardGranted = json.optBoolean("rewardGranted", false)
            val usageDate = json.optString("usageDate", "")
            val startedAt = json.optLong("startedAtEpochMs", -1L)
            if (study.isBlank() ||
                total !in 1L..MAX_STUDY_ROUND_SECONDS ||
                remaining !in 0L..total ||
                studied !in 0L..total ||
                reward !in 0L..MAX_STUDY_ROUND_REWARD_SECONDS ||
                usageDate.isBlank() ||
                startedAt <= 0L ||
                (rewardGranted && !rewardAttempted) ||
                (!rewardEnabled && (rewardAttempted || rewardGranted))
            ) {
                return@runCatching null
            }
            val boot = BootEvidence(
                bootId = json.optString("bootId", ""),
                bootStampMs = json.optLong("bootStampMs", 0L),
            )
            if (!boot.valid) return@runCatching null
            SavedStudyRound(
                studyPackage = study,
                targetPackage = json.optString("targetPackage", "").ifBlank { null },
                totalSeconds = total,
                remainingSeconds = remaining,
                studiedSeconds = studied,
                rewardSeconds = reward,
                rewardEnabled = rewardEnabled,
                rewardAttempted = rewardAttempted,
                rewardGranted = rewardGranted,
                usageDate = usageDate,
                startedAtEpochMs = startedAt,
                boot = boot,
            )
        }.getOrNull()
    }

    internal fun encodeStudyRound(round: SavedStudyRound): JSONObject = JSONObject()
        .put("schemaVersion", STUDY_ROUND_SCHEMA_VERSION)
        .put("studyPackage", round.studyPackage)
        .put("totalSeconds", round.totalSeconds)
        .put("remainingSeconds", round.remainingSeconds)
        .put("studiedSeconds", round.studiedSeconds)
        .put("rewardSeconds", round.rewardSeconds)
        .put("rewardEnabled", round.rewardEnabled)
        .put("rewardAttempted", round.rewardAttempted)
        .put("rewardGranted", round.rewardGranted)
        .put("usageDate", round.usageDate)
        .put("startedAtEpochMs", round.startedAtEpochMs)
        .put("bootId", round.boot.bootId)
        .put("bootStampMs", round.boot.bootStampMs)
        .also { json -> round.targetPackage?.let { json.put("targetPackage", it) } }

    fun saveStudyRound(
        round: SavedStudyRound,
        hosted: Boolean = false,
        hostedScope: String? = null,
    ) {
        if (!TvStore.isReady) return
        val key = if (hosted) {
            PolicyKeys.hostedScoped(PolicyKeys.HOSTED_STUDY_ROUND_V1, hostedScope)
        } else {
            PolicyKeys.LOCAL_STUDY_ROUND_V1
        }
        TvStore.putString(key, encodeStudyRound(round).toString())
        TvStore.sync()
    }

    fun clearStudyRound(hosted: Boolean = false, hostedScope: String? = null) {
        if (!TvStore.isReady) return
        TvStore.remove(
            if (hosted) {
                PolicyKeys.hostedScoped(PolicyKeys.HOSTED_STUDY_ROUND_V1, hostedScope)
            } else {
                PolicyKeys.LOCAL_STUDY_ROUND_V1
            },
        )
        TvStore.sync()
    }

    /** Removes local rules/PIN/recovery but keeps the mode and cloud data. Used by recovery reset. */
    fun resetLocalSetup() {
        if (!TvStore.isReady) return
        listOf(
            PolicyKeys.LOCAL_RULES,
            PolicyKeys.LOCAL_RULES_V2,
            PolicyKeys.LOCAL_RULES_V3,
            PolicyKeys.LOCAL_POLICY_EPOCH,
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
            // V37 execution state: rules and PINs are gone, so a restored session/override
            // would no longer be explainable to the parent.
            PolicyKeys.LOCAL_RUNTIME_V2,
            PolicyKeys.LOCAL_GUARD_V2,
            PolicyKeys.LOCAL_STUDY_ROUND_V1,
        ).forEach { TvStore.remove(it) }
        TvStore.sync()
    }

    private const val STUDY_ROUND_SCHEMA_VERSION = 2
    private const val MAX_STUDY_ROUND_SECONDS = 24L * 60L * 60L
    private const val MAX_STUDY_ROUND_REWARD_SECONDS = 24L * 60L * 60L
}

private fun List<LocalTimeWindow>.toJson(): JSONArray = JSONArray().also { array ->
    forEach { window ->
        array.put(JSONArray().put(window.startMinute).put(window.endMinute))
    }
}

private fun LocalDayGroup.toJson(): JSONObject = JSONObject()
    .put("days", JSONArray(days))
    .put("studyWindows", studyWindows.toJson())
    .put("studyDailyLimitMinutes", studyDailyLimitMinutes ?: JSONObject.NULL)
    .put("entertainmentWindows", entertainmentWindows.toJson())
    .put("entertainmentDailyLimitMinutes", entertainmentDailyLimitMinutes ?: JSONObject.NULL)

private fun List<LocalDayGroup>.toJsonGroups(): JSONArray = JSONArray().also { array ->
    forEach { group -> array.put(group.toJson()) }
}

private fun LocalRuleSet.toJson(): JSONObject = JSONObject()
    .put("dayGroups", dayGroups.toJsonGroups())
    .put("sessionLimitMinutes", sessionLimitMinutes)
    .put("sessionCapMinutes", sessionCapMinutes)
    .put("restMinutes", restMinutes)
    .put("sessionScreenOffBufferMinutes", sessionScreenOffBufferMinutes)
    .put("quizGateEnabled", quizGateEnabled)
    .put("studyEarnEnabled", studyEarnEnabled)
    .put("directStartEnabled", directStartEnabled)
    .put("quizDailyLimitCount", quizDailyLimitCount)
    .put("studyRewardDailyLimitCount", studyRewardDailyLimitCount)
    .put("directDailyFreeCount", directDailyFreeCount)

/** Strict schema-3 windows: any malformed entry invalidates the whole document. */
private fun JSONArray.toWindowsStrict(): List<LocalTimeWindow>? = buildList {
    for (i in 0 until length()) {
        val pair = optJSONArray(i) ?: return null
        val start = pair.optInt(0, -1)
        val end = pair.optInt(1, -1)
        if (start < 0 || end <= start || end > 24 * 60) return null
        add(LocalTimeWindow(start, end))
    }
}

/** Strict schema-3 day groups; coverage and every limit are validated after materialization. */
private fun JSONArray.toDayGroupsStrict(): List<LocalDayGroup>? = buildList {
    for (i in 0 until length()) {
        val group = optJSONObject(i) ?: return null
        val daysJson = group.optJSONArray("days") ?: return null
        val days = buildList {
            for (d in 0 until daysJson.length()) {
                val day = daysJson.optInt(d, -1)
                if (day !in 1..7) return null
                add(day)
            }
        }
        if (days.isEmpty() || days.toSet().size != days.size) return null
        val studyWindows = group.optJSONArray("studyWindows")?.toWindowsStrict() ?: return null
        val entertainmentWindows =
            group.optJSONArray("entertainmentWindows")?.toWindowsStrict() ?: return null
        val studyLimit = group.nullableLimit("studyDailyLimitMinutes") ?: return null
        val entertainmentLimit =
            group.nullableLimit("entertainmentDailyLimitMinutes") ?: return null
        add(
            LocalDayGroup(
                days = days,
                studyWindows = studyWindows,
                studyDailyLimitMinutes = studyLimit.value,
                entertainmentWindows = entertainmentWindows,
                entertainmentDailyLimitMinutes = entertainmentLimit.value,
            ),
        )
    }
}

/**
 * Reads a nullable minute limit. The [NullableLimit] wrapper tells "unlimited" (JSON null) from
 * "invalid" (null return): a missing key, a non-number, a fractional number or a value outside
 * 0..1440 invalidates the whole document.
 */
private fun JSONObject.nullableLimit(key: String): NullableLimit? {
    if (!has(key)) return null
    if (isNull(key)) return NullableLimit(null)
    val raw = opt(key)
    if (raw !is Number) return null
    val value = raw.toInt()
    if (value.toDouble() != raw.toDouble()) return null
    if (value !in 0..LocalRules.MAX_DAILY_MINUTES) return null
    return NullableLimit(value)
}

private data class NullableLimit(val value: Int?)
