package app.orchard.tv.policy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.SwitchPinExempt
import com.planyourtv.policy.SwitchProtection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local, device-wide switch protection shared by every family member.
 *
 * This is a local Web/TV setting: it is never part of [com.planyourtv.policy.FamilyDocument],
 * never synced to the cloud, and never written into a member's hosted partition. The old
 * per-member `switchPinRequired` and exemption fields stay in storage for compatibility but are
 * ignored once [migrateFromMembers] succeeds.
 *
 * Migration is fail-closed. It keeps protection on when the old device-wide switch was on or any
 * active member was protected. A waiver is inherited only when every protected active member
 * shared the exact same valid window; otherwise the family always asks for the password.
 */
object SwitchProtectionSettings {
    private const val TAG = "SwitchProtection"
    private const val PREFS = "policy_switch_protection"
    private const val KEY_REQUIRED = "required"
    private const val KEY_EXEMPT_DAYS = "exempt_days"
    private const val KEY_EXEMPT_START = "exempt_start_minute"
    private const val KEY_EXEMPT_END = "exempt_end_minute"
    private const val KEY_MIGRATED = "migrated"

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    @Volatile
    private var policy = SwitchProtection()

    @Volatile
    private var migrated = false

    private val _state = MutableStateFlow(policy)
    val state: StateFlow<SwitchProtection> = _state.asStateFlow()

    fun install(context: Context) {
        if (prefs != null) return
        val application = context.applicationContext
        appContext = application
        val loaded = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = loaded
        migrated = loaded.getBoolean(KEY_MIGRATED, false)
        policy = readPolicy(loaded)
        _state.value = policy
    }

    fun current(): SwitchProtection = policy

    fun isMigrated(): Boolean = migrated

    /**
     * Applies the old per-member state once the roster is available. The caller passes the old
     * device-wide fallback explicitly so a failed per-member migration still keeps protection on.
     *
     * @return true when a migrated policy is now stored, including when it was already migrated.
     */
    @Synchronized
    fun migrateFromMembers(
        members: Collection<FamilyMember>,
        legacyFallbackRequired: Boolean,
    ): Boolean {
        val loaded = prefs ?: return false
        if (migrated) return true
        val active = members.filter { it.status == com.planyourtv.policy.MemberStatus.ACTIVE }
        val required = legacyFallbackRequired || active.any { it.switchPinRequired }
        val protectedMembers =
            if (legacyFallbackRequired) active else active.filter { it.switchPinRequired }
        val inherited = if (required) {
            SwitchProtection.commonExemption(protectedMembers)
        } else {
            null
        }
        val next = inherited ?: SwitchProtection(required = required)
        if (!write(loaded, next, migrated = true)) {
            Log.w(TAG, "migration write failed; keeping legacy enforcement")
            return false
        }
        policy = next
        migrated = true
        _state.value = next
        return true
    }

    /**
     * Replaces the policy after validation by the caller. Invalid input is rejected rather than
     * coerced; a failed storage write leaves the previous policy active.
     */
    @Synchronized
    fun setPolicy(next: SwitchProtection): Boolean {
        val loaded = prefs ?: return false
        val normalized = next.normalized() ?: return false
        if (!write(loaded, normalized, migrated = true)) return false
        policy = normalized
        migrated = true
        _state.value = normalized
        return true
    }

    /**
     * Drops the in-process handle so the next [install] reads storage again. Tests use the
     * default to clear the file, or `clearStorage = false` to verify persistence.
     */
    internal fun debugResetForTest(clearStorage: Boolean = true) {
        if (clearStorage) {
            appContext
                ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()
                ?.clear()
                ?.commit()
        }
        prefs = null
        appContext = null
        policy = SwitchProtection()
        migrated = false
        _state.value = policy
    }

    private fun readPolicy(loaded: SharedPreferences): SwitchProtection {
        val required = loaded.getBoolean(KEY_REQUIRED, false)
        val days = parseDays(loaded.getString(KEY_EXEMPT_DAYS, null))
        val start = loaded.getInt(KEY_EXEMPT_START, SwitchPinExempt.DEFAULT_START_MINUTE)
        val end = loaded.getInt(KEY_EXEMPT_END, SwitchPinExempt.DEFAULT_END_MINUTE)
        val candidate = SwitchProtection(required, days ?: emptyList(), start, end)
        return candidate.normalized() ?: SwitchProtection(required = required)
    }

    private fun parseDays(raw: String?): List<Int>? {
        if (raw.isNullOrBlank()) return emptyList()
        val values = raw.split(',').map { it.trim().toIntOrNull() ?: return null }
        return SwitchPinExempt.normalizeDays(values)
    }

    private fun write(
        loaded: SharedPreferences,
        next: SwitchProtection,
        migrated: Boolean,
    ): Boolean = runCatching {
        loaded.edit()
            .putBoolean(KEY_REQUIRED, next.required)
            .putString(KEY_EXEMPT_DAYS, next.exemptDays.joinToString(","))
            .putInt(KEY_EXEMPT_START, next.exemptStartMinute)
            .putInt(KEY_EXEMPT_END, next.exemptEndMinute)
            .putBoolean(KEY_MIGRATED, migrated)
            .commit()
    }.getOrDefault(false)
}