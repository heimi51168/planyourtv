package app.orchard.tv.policy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.MemberPin
import com.planyourtv.policy.switchPinExemptAt
import java.time.LocalDateTime

/**
 * The TV lock screen's switch password guard.
 *
 * The switch and its optional waiver window are device-wide ([SwitchProtectionSettings]); the
 * target member's 6-digit credential is still verified individually, and failed attempts lock
 * out only that member. The old per-member `switchPinRequired` and exemption fields remain in
 * storage for compatibility but are ignored once global migration succeeds.
 *
 * Upgraded devices may still carry the old device-wide key or per-member protection.
 * [migrateLegacyToMembers] first copies the pre-V48 device-wide key to the roster, then
 * [SwitchProtectionSettings] derives the global policy. Until both writes succeed, [requiresPin]
 * keeps honoring the old value so an upgrade can never silently remove protection.
 *
 * Failed attempts are in memory only; a reboot resets them, which is acceptable for a 6-digit
 * convenience password that is not parent authorization.
 */
object MemberSwitchAuth {
    private const val TAG = "MemberSwitchAuth"
    private const val PREFS = "policy_member_switch"
    private const val KEY_LEGACY_REQUIRED = "required"
    private const val KEY_MIGRATED = "per_member_migrated"

    enum class VerifyResult { OK, INVALID, LOCKED_OUT, UNKNOWN_MEMBER }

    private data class Attempts(
        var failed: Int = 0,
        var lockedUntil: Long = 0L,
    )

    private var prefs: SharedPreferences? = null
    private var legacyFallbackRequired = false
    private val attempts = mutableMapOf<String, Attempts>()

    fun install(context: Context) {
        if (prefs != null) return
        val loaded = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = loaded
        legacyFallbackRequired =
            !loaded.getBoolean(KEY_MIGRATED, false) &&
                loaded.getBoolean(KEY_LEGACY_REQUIRED, false)
    }

    /**
     * Applies the old device-wide switch to the roster once and marks the per-member migration.
     * A failed roster write leaves the old value active for this boot and retries on the next
     * start.
     *
     * @return true when the old fallback is no longer needed.
     */
    fun migrateLegacyToMembers(): Boolean {
        val loaded = prefs ?: return false
        if (loaded.getBoolean(KEY_MIGRATED, false)) {
            legacyFallbackRequired = false
            return true
        }
        if (legacyFallbackRequired && !FamilyHub.migrateLegacySwitchPinRequired(true)) {
            Log.w(TAG, "legacy member-switch migration failed; keeping the old guard")
            return false
        }
        val stored = runCatching {
            loaded.edit().putBoolean(KEY_MIGRATED, true).commit()
        }.getOrDefault(false)
        if (stored) legacyFallbackRequired = false
        return stored
    }

    /** True until the pre-V48 device-wide switch has been copied and marked migrated. */
    fun legacyFallbackActive(): Boolean = legacyFallbackRequired

    /**
     * True when entering [member] must ask for its password at [now].
     *
     * The global policy wins as soon as its migration write succeeds. Before that, the legacy
     * device-wide fallback or the old per-member flag is used. The fallback always wins over a
     * waiver: during an upgrade no exemption may weaken existing protection.
     */
    fun requiresPin(
        member: FamilyMember,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        if (SwitchProtectionSettings.isMigrated()) {
            val current = SwitchProtectionSettings.current()
            return current.required &&
                !current.isExemptAt(now.dayOfWeek.value, now.hour * 60 + now.minute)
        }
        if (legacyFallbackRequired) return true
        if (!member.switchPinRequired) return false
        return !isExemptNow(member, now)
    }

    /** Whether the active day/time waiver applies to [member] at [now]. */
    fun isExemptNow(
        member: FamilyMember,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        if (SwitchProtectionSettings.isMigrated()) {
            val current = SwitchProtectionSettings.current()
            return current.required &&
                current.isExemptAt(now.dayOfWeek.value, now.hour * 60 + now.minute)
        }
        return !legacyFallbackRequired &&
            member.switchPinExemptAt(
                dayOfWeek = now.dayOfWeek.value,
                minuteOfDay = now.hour * 60 + now.minute,
            )
    }

    /** Whether the global policy currently protects switching at all. */
    fun protectionEnabled(member: FamilyMember): Boolean {
        if (SwitchProtectionSettings.isMigrated()) {
            return SwitchProtectionSettings.current().required
        }
        if (legacyFallbackRequired) return true
        return member.switchPinRequired
    }

    @Synchronized
    fun verify(userId: String, candidate: String): VerifyResult {
        if (FamilyHub.member(userId) == null) return VerifyResult.UNKNOWN_MEMBER
        val now = System.currentTimeMillis()
        val attempt = attempts.getOrPut(userId) { Attempts() }
        if (attempt.lockedUntil > now) return VerifyResult.LOCKED_OUT
        if (FamilyHub.verifySwitchPin(userId, candidate)) {
            attempts.remove(userId)
            return VerifyResult.OK
        }
        attempt.failed += 1
        if (attempt.failed >= MemberPin.MAX_FAILED) {
            attempt.failed = 0
            attempt.lockedUntil = now + MemberPin.LOCKOUT_MS
        }
        return VerifyResult.INVALID
    }

    /** Seconds left before this member may retry, zero when not locked. */
    @Synchronized
    fun lockoutSecondsLeft(userId: String): Int {
        val attempt = attempts[userId] ?: return 0
        val remaining = attempt.lockedUntil - System.currentTimeMillis()
        return if (remaining > 0L) ((remaining + 999L) / 1000L).toInt() else 0
    }
}
