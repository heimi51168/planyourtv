package app.orchard.tv.policy

import android.content.Context
import android.content.SharedPreferences
import com.planyourtv.policy.MemberPin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-wide setting for the TV lock screen's member list.
 *
 * ON means the child must type the target member's 6-digit password before the switch. OFF means
 * switching is direct; both paths still share [SubjectSwitcher]'s state gate, so a running timer,
 * quiz or study round blocks the switch either way. Historical devices read the missing key as
 * false, matching the upgrade behavior: no prompt until the parent enables it.
 *
 * Failed attempts are limited per member rather than per device so one child's mistakes do not
 * lock out a sibling. The counters are in memory only; a reboot resets them, which is acceptable
 * for a 6-digit convenience password that is not parent authorization.
 */
object MemberSwitchAuth {
    private const val PREFS = "policy_member_switch"
    private const val KEY_REQUIRED = "required"

    enum class VerifyResult { OK, INVALID, LOCKED_OUT, UNKNOWN_MEMBER }

    private data class Attempts(
        var failed: Int = 0,
        var lockedUntil: Long = 0L,
    )

    private var prefs: SharedPreferences? = null
    private val attempts = mutableMapOf<String, Attempts>()

    private val _required = MutableStateFlow(false)
    val required: StateFlow<Boolean> = _required.asStateFlow()

    fun install(context: Context) {
        if (prefs != null) return
        val loaded = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = loaded
        _required.value = loaded.getBoolean(KEY_REQUIRED, false)
    }

    fun isRequired(): Boolean = _required.value

    fun setRequired(required: Boolean): Boolean {
        val loaded = prefs ?: return false
        val stored = runCatching {
            loaded.edit().putBoolean(KEY_REQUIRED, required).commit()
        }.getOrDefault(false)
        if (!stored) return false
        _required.value = required
        return true
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
