package app.orchard.tv.policy

import android.util.Log
import app.orchard.tv.quiz.QuizQuestionCache
import app.orchard.tv.quiz.QuizRuntime
import app.orchard.tv.quiz.QuizSubjectScope
import com.planyourtv.policy.MemberStatus

/**
 * The one path that moves the TV from one child to another.
 *
 * The TV lock-screen list and the Web parent center share [stateAllowsSwitch] and [switchTo], so
 * both triggers validate exactly the same state: the TV must be sitting on the lock screen and
 * the current child must own nothing — no running session, focus round, pending grant, temporary
 * unlock, setup wizard or active quiz round. Authentication stays intentionally different: the
 * TV asks for the target member's own 6-digit password when that member opted into switch
 * protection, while Web acts under the parent-center login and never asks for it.
 *
 * A successful switch commits the outgoing runtime, moves the active pointer, hands the shared
 * question bank's answer state to the target child and reloads the subject cache. Cloud mode
 * stays single-member until the backend carries per-member policy, usage and commands.
 */
object SubjectSwitcher {
    private const val TAG = "SubjectSwitcher"

    enum class Result {
        OK,
        NOT_READY,
        UNKNOWN_MEMBER,
        CLOUD_MODE,
        BUSY,
        NOT_LOCKED,
        STATE_CHANGED,
        FAILED,
    }

    /** Whether the chip may open the switching list instead of the single-member profile. */
    fun canSwitch(): Boolean = stateAllowsSwitch()

    /**
     * The shared state gate. A setup-required TV is not a lock screen: switching there would let
     * a secondary child land in the first-run wizard that also creates the parent PIN.
     */
    fun stateAllowsSwitch(): Boolean {
        val family = FamilyHub.state.value
        return family.ready &&
            family.canSwitchMember &&
            !PolicyModeCoordinator.isCloud() &&
            !LocalPolicyRuntime.isSetupRequired &&
            LocalPolicyRuntime.isLockScreen() &&
            LocalPolicyRuntime.canSwitchSubject() &&
            !QuizRuntime.isRoundActive()
    }

    /**
     * Switches to [userId]. [expectedEpoch] protects a Web form rendered against an older active
     * member: the switch is refused instead of silently overriding a newer one. Authentication is
     * the caller's responsibility; PIN attempts are not repeated here so a failed switch cannot
     * become a PIN oracle.
     */
    @Synchronized
    fun switchTo(userId: String, expectedEpoch: Long? = null): Result {
        val family = FamilyHub.state.value
        if (!family.ready) return Result.NOT_READY
        val member = family.members.firstOrNull {
            it.userId == userId && it.status == MemberStatus.ACTIVE
        } ?: return Result.UNKNOWN_MEMBER
        if (expectedEpoch != null && family.activeEpoch != expectedEpoch) {
            return Result.STATE_CHANGED
        }
        if (member.userId == family.activeUserId) return Result.OK
        if (!family.canSwitchMember) return Result.NOT_READY
        if (PolicyModeCoordinator.isCloud()) return Result.CLOUD_MODE
        if (LocalPolicyRuntime.isSetupRequired || !LocalPolicyRuntime.isLockScreen()) {
            return Result.NOT_LOCKED
        }
        if (!LocalPolicyRuntime.canSwitchSubject()) return Result.BUSY
        if (QuizRuntime.isRoundActive()) return Result.BUSY
        if (!LocalPolicyRuntime.prepareSubjectSwitch()) return Result.BUSY

        val previous = family.activeUserId
        val activated = when (FamilyHub.activate(member.userId)) {
            FamilyHub.ActivateResult.OK -> true
            FamilyHub.ActivateResult.UNKNOWN_MEMBER -> return Result.UNKNOWN_MEMBER
            FamilyHub.ActivateResult.NOT_READY -> return Result.NOT_READY
            FamilyHub.ActivateResult.FAILED -> false
        }
        if (!activated) return Result.FAILED

        // A new child inherits the family's level structure and quiz settings, never its
        // progress. Existing documents are left untouched; a missing one is cloned from the
        // member that was active a moment ago. The question state moves separately below.
        QuizSubjectScope.provisionLocalDocument(member.userId, previous)
        if (!QuizQuestionCache.switchSubjectState(member.userId)) {
            Log.e(TAG, "answer state switch failed; restoring the previous member")
            FamilyHub.activate(previous)
            LocalPolicyRuntime.reloadActiveSubject()
            QuizRuntime.refresh()
            return Result.FAILED
        }
        LocalPolicyRuntime.reloadActiveSubject()
        QuizRuntime.refresh()
        return Result.OK
    }
}
