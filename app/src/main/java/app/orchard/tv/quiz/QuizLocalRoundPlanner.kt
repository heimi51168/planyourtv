package app.orchard.tv.quiz

import android.util.Log
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizDayType
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizRewardProfile
import com.planyourtv.policy.quiz.QuizRewardResolver

/** The document-derived inputs a local v6 round freezes at start. */
data class QuizLocalRoundPlan(
    /** The exact document revision the round froze, for the session snapshot. */
    val document: QuizConfigDocument,
    val config: QuizConfig,
    val dayType: QuizDayType,
    val profile: QuizRewardProfile,
    val levels: List<QuizLevel>,
)

sealed interface QuizLocalPlanResult {
    data class Ready(val plan: QuizLocalRoundPlan) : QuizLocalPlanResult

    /** [code] is internal; the UI already blocks the entry before it gets here. */
    data class Rejected(val code: String) : QuizLocalPlanResult
}

/**
 * Turns one local QCD into the values a round executes with. Everything is read
 * once here so a parent edit during the round cannot change its payout: the day
 * type and reward profile are frozen into the session by [QuizChallenge].
 */
object QuizLocalRoundPlanner {
    private const val TAG = "QuizLocalRoundPlanner"
    const val DOCUMENT_UNREADABLE = "DOCUMENT_UNREADABLE"
    const val DOCUMENT_INVALID = "DOCUMENT_INVALID"

    fun plan(document: QuizConfigDocument?, nowMs: Long): QuizLocalPlanResult {
        if (document == null) return QuizLocalPlanResult.Rejected(DOCUMENT_UNREADABLE)
        val issues = QuizLocalConfigDocument.localReadIssues(document)
        if (issues.isNotEmpty()) {
            Log.e(TAG, "local quiz document failed validation: $issues")
            return QuizLocalPlanResult.Rejected(DOCUMENT_INVALID)
        }
        val dayType = runCatching {
            QuizRewardResolver.dayType(document.calendar, nowMs)
        }.getOrElse {
            Log.e(TAG, "local quiz document timezone is unsupported", it)
            return QuizLocalPlanResult.Rejected(DOCUMENT_INVALID)
        }
        // A weekday/weekend entry is a complete replacement; a missing one falls
        // back to the default profile as a whole, never field by field.
        val profile = QuizRewardResolver.resolve(document.reward, dayType)
            ?: return QuizLocalPlanResult.Rejected(DOCUMENT_INVALID)
        val config = document.toLegacyConfig()
            ?: return QuizLocalPlanResult.Rejected(DOCUMENT_INVALID)
        val levels = document.localLevels()
            ?: return QuizLocalPlanResult.Rejected(DOCUMENT_UNREADABLE)
        return QuizLocalPlanResult.Ready(
            QuizLocalRoundPlan(
                document = document,
                config = config,
                dayType = dayType,
                profile = profile,
                levels = levels,
            ),
        )
    }
}
