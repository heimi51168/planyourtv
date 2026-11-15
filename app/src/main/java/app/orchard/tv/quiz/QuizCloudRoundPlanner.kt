package app.orchard.tv.quiz

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizConfigDocumentValidator
import com.planyourtv.policy.quiz.QuizDayType
import com.planyourtv.policy.quiz.QuizDeviceCapabilities
import com.planyourtv.policy.quiz.QuizDocOrigin
import com.planyourtv.policy.quiz.QuizLevelOrigin
import com.planyourtv.policy.quiz.QuizRewardProfile
import com.planyourtv.policy.quiz.QuizRewardResolver

/** One enabled cloud level plus the QB availability that arrived with the QCD. */
data class QuizCloudLevel(
    /** QCD level id, `cloud:<qbLevelId>`. */
    val id: String,
    /** QB level id; built-in default levels are negative, never zero. */
    val qbLevelId: Long,
    val name: String,
    val questionCount: Int,
    val available: Int,
    val unavailableReason: String?,
)

/**
 * Everything one cloud round is allowed to read from the active QCD.
 *
 * The document, member identity, day type and reward profile are frozen into the
 * READY session by [CloudQuizPreparer], so a parent edit during the round cannot
 * change its questions or payout.
 */
data class QuizCloudRoundPlan(
    val document: QuizConfigDocument,
    val memberId: Long,
    val qbLearnerId: Long,
    val config: QuizConfig,
    val dayType: QuizDayType,
    val profile: QuizRewardProfile,
    val levels: List<QuizCloudLevel>,
    val checkinBlocked: Boolean,
    val checkinMessage: String?,
)

sealed interface QuizCloudPlanResult {
    data class Ready(val plan: QuizCloudRoundPlan) : QuizCloudPlanResult

    /** [code] is internal; the UI maps it to human copy and never shows it raw. */
    data class Rejected(val code: String) : QuizCloudPlanResult
}

/**
 * Projects the bound TV's active cloud QCD onto the execution values a round
 * needs. Local mode uses [QuizLocalRoundPlanner] instead; the two must not fall
 * back to each other.
 */
object QuizCloudRoundPlanner {
    private const val TAG = "QuizCloudPlanner"

    const val NO_ACTIVE_DOCUMENT = "NO_ACTIVE_DOCUMENT"
    const val CLOUD_ENVELOPE_MISSING = "CLOUD_ENVELOPE_MISSING"
    const val NOT_CLOUD_DOCUMENT = "NOT_CLOUD_DOCUMENT"
    const val DOCUMENT_INVALID = "DOCUMENT_INVALID"
    const val DOCUMENT_UNSUPPORTED = "DOCUMENT_UNSUPPORTED"
    const val DISABLED = "DISABLED"
    const val QUESTION_COUNT_UNSUPPORTED = "QUESTION_COUNT_UNSUPPORTED"
    const val LEVEL_STATUS_MISSING = "LEVEL_STATUS_MISSING"
    const val LEVEL_ORIGIN_INVALID = "LEVEL_ORIGIN_INVALID"
    const val MEMBER_UNKNOWN = "MEMBER_UNKNOWN"

    private val DOCUMENT_ID = Regex("^quizcfg:family:([0-9]+):member:([0-9]+)$")

    /** Reads the active document and its envelope; null active is an explicit refusal. */
    fun plan(nowMs: Long = System.currentTimeMillis()): QuizCloudPlanResult {
        if (QuizConfigDocumentStore.activeDocument() == null) {
            return QuizCloudPlanResult.Rejected(NO_ACTIVE_DOCUMENT)
        }
        val active = QuizConfigDocumentStore.activeCloudDocument()
            ?: return QuizCloudPlanResult.Rejected(CLOUD_ENVELOPE_MISSING)
        return plan(active, nowMs)
    }

    @VisibleForTesting
    internal fun plan(
        active: ActiveCloudQuizDocument,
        nowMs: Long,
    ): QuizCloudPlanResult {
        val document = active.cloud.document
        if (document.origin != QuizDocOrigin.CLOUD) {
            return QuizCloudPlanResult.Rejected(NOT_CLOUD_DOCUMENT)
        }
        val validation = QuizConfigDocumentValidator.validate(document)
        if (validation.isNotEmpty()) {
            Log.e(TAG, "cloud quiz document failed validation: $validation")
            return QuizCloudPlanResult.Rejected(DOCUMENT_INVALID)
        }
        val capability = QuizDeviceCapabilities.issues(document, cloudRound = true)
        if (capability.isNotEmpty()) {
            Log.e(TAG, "cloud quiz document exceeds TV capabilities: $capability")
            return QuizCloudPlanResult.Rejected(DOCUMENT_UNSUPPORTED)
        }
        if (!document.enabled) return QuizCloudPlanResult.Rejected(DISABLED)
        if (document.rules.questionCount !in 1..QuizDeviceCapabilities.CLOUD_MAX_QUESTIONS) {
            return QuizCloudPlanResult.Rejected(QUESTION_COUNT_UNSUPPORTED)
        }

        val memberId = memberIdOf(document)
            ?: return QuizCloudPlanResult.Rejected(MEMBER_UNKNOWN)
        if (active.learnerId <= 0L) return QuizCloudPlanResult.Rejected(MEMBER_UNKNOWN)

        val dayType = runCatching {
            QuizRewardResolver.dayType(document.calendar, nowMs)
        }.getOrElse {
            Log.e(TAG, "cloud quiz calendar timezone is unsupported", it)
            return QuizCloudPlanResult.Rejected(DOCUMENT_INVALID)
        }
        // A weekday/weekend entry is a complete replacement; a missing one uses
        // the default profile as a whole, exactly like the local planner.
        val profile = QuizRewardResolver.resolve(document.reward, dayType)
            ?: return QuizCloudPlanResult.Rejected(DOCUMENT_INVALID)

        val levels = ArrayList<QuizCloudLevel>(document.levels.size)
        for (level in document.levels.sortedBy { it.sortNo }) {
            if (!level.enabled) continue
            if (level.origin != QuizLevelOrigin.CLOUD) {
                Log.e(TAG, "cloud document carries a non-cloud level: ${level.levelId}")
                return QuizCloudPlanResult.Rejected(LEVEL_ORIGIN_INVALID)
            }
            val status = active.cloud.levelStatus.optJSONObject(level.levelId)
            if (status == null) {
                Log.e(TAG, "cloud level has no availability status: ${level.levelId}")
                return QuizCloudPlanResult.Rejected(LEVEL_STATUS_MISSING)
            }
            val qbLevelId = status.optLong("qbLevelId", 0L)
            if (qbLevelId == 0L) {
                Log.e(TAG, "cloud level status has no QB id: ${level.levelId}")
                return QuizCloudPlanResult.Rejected(LEVEL_STATUS_MISSING)
            }
            val availableCount = status.optInt("availableCount", 0)
            val questionCount = status.optInt("questionCount", document.rules.questionCount)
            if (questionCount !in 1..QuizDeviceCapabilities.CLOUD_MAX_QUESTIONS) {
                Log.e(TAG, "cloud level question count is unsupported: ${level.levelId}")
                return QuizCloudPlanResult.Rejected(QUESTION_COUNT_UNSUPPORTED)
            }
            levels += QuizCloudLevel(
                id = level.levelId,
                qbLevelId = qbLevelId,
                name = level.name,
                questionCount = questionCount,
                available = if (status.optBoolean("available", false)) availableCount else 0,
                unavailableReason = status.optString("unavailableReason", "").ifBlank { null },
            )
        }

        val checkin = active.cloud.checkin
        return QuizCloudPlanResult.Ready(
            QuizCloudRoundPlan(
                document = document,
                memberId = memberId,
                qbLearnerId = active.learnerId,
                config = document.cloudConfig(profile, dayType),
                dayType = dayType,
                profile = profile,
                levels = levels,
                checkinBlocked = checkin.optBoolean("blocked", false),
                checkinMessage = checkin.optString("message", "").ifBlank { null },
            ),
        )
    }

    private fun memberIdOf(document: QuizConfigDocument): Long? {
        val match = DOCUMENT_ID.matchEntire(document.documentId) ?: return null
        val familyId = match.groupValues[1].toLongOrNull() ?: return null
        val memberId = match.groupValues[2].toLongOrNull() ?: return null
        if (familyId <= 0L || memberId <= 0L) return null
        return memberId
    }
}

/**
 * The display/legacy projection of a cloud round.
 *
 * [profile] is the resolved day profile, not necessarily `reward.profiles.default`:
 * the minute mirrors must describe the round that will actually run. The v6
 * payout still uses the frozen profile itself; these fields are display-only.
 */
private fun QuizConfigDocument.cloudConfig(
    profile: QuizRewardProfile,
    dayType: QuizDayType,
): QuizConfig = QuizConfig(
    defaultTimeLimitSec = rules.timeLimitSec,
    minutesPerPoint = rules.legacy?.minutesPerPoint ?: 5,
    minCorrectCount = rules.legacy?.minCorrectCount ?: 1,
    maxEarnedMinutes = (profile.maxEarnedSeconds / 60).coerceIn(1, 180),
    shuffleOptions = rules.shuffleOptions,
    answerDisplaySec = 30,
    feedbackExtraHoldSec = rules.feedback.extraHoldSec,
    pullCount = rules.questionCount,
    minutesPerQuestion = rules.legacy?.minutesPerQuestion ?: 5,
    pointsPerQuestion = rules.legacy?.pointsPerQuestion ?: 1,
    easyRewardMinutes = profile.correctSeconds.easy / 60,
    mediumRewardMinutes = profile.correctSeconds.medium / 60,
    hardRewardMinutes = profile.correctSeconds.hard / 60,
    easyPenaltyMinutes = profile.penaltySeconds.easy / 60,
    mediumPenaltyMinutes = profile.penaltySeconds.medium / 60,
    hardPenaltyMinutes = profile.penaltySeconds.hard / 60,
    minUnlockMinutes = profile.minUnlockSeconds / 60,
)
