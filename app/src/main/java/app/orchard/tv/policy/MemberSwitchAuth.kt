package app.orchard.tv.policy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.MemberPin
import com.planyourtv.policy.switchPinExemptAt
import java.time.LocalDateTime

/**
 * The TV lock screen's per-member switch password guard.
 *
 * Whether a prompt is shown is stored on the target [FamilyMember] (`switchPinRequired`), so
 * each child can be protected independently. A member may also select weekdays and one same-day
 * time range where the prompt is skipped, so a grandparent can switch without the child's code.
 * This object only evaluates that waiver, verifies the 6-digit password and counts failures per
 * target member; one child's mistakes never lock out a sibling.
 *
 * Upgraded devices may still carry the old device-wide key. [migrateLegacyToMembers] copies an
 * old "on" value to every existing member once. Until that write succeeds, [requiresPin] keeps
 * honoring the old value so an upgrade can never silently remove protection.
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
     * Applies the old device-wide switch to the roster once. A failed roster write leaves the
     * old value active for this boot and retries on the next start.
     */
    fun migrateLegacyToMembers() {
        val loaded = prefs ?: return
        if (loaded.getBoolean(KEY_MIGRATED, false)) {
            legacyFallbackRequired = false
            return
        }
        if (legacyFallbackRequired && !FamilyHub.migrateLegacySwitchPinRequired(true)) {
            Log.w(TAG, "legacy member-switch migration failed; keeping the old guard")
            return
        }
        val stored = runCatching {
            loaded.edit().putBoolean(KEY_MIGRATED, true).commit()
        }.getOrDefault(false)
        if (stored) legacyFallbackRequired = false
    }

    /**
     * True when entering [member] must ask for its password at [now].
     *
     * A per-member waiver is only considered when protection is on. The legacy upgrade fallback
     * always wins: until the old device-wide switch is migrated, no exemption may weaken it.
     */
    fun requiresPin(
        member: FamilyMember,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean {
        if (legacyFallbackRequired) return true
        if (!member.switchPinRequired) return false
        return !isExemptNow(member, now)
    }

    /** Whether the member's configured day/time waiver applies at [now]. */
    fun isExemptNow(
        member: FamilyMember,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean =
        !legacyFallbackRequired &&
            member.switchPinExemptAt(
                dayOfWeek = now.dayOfWeek.value,
                minuteOfDay = now.hour * 60 + now.minute,
            )

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
