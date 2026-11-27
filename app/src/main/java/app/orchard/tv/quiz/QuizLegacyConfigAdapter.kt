package app.orchard.tv.quiz

import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizDifficultySeconds
import com.planyourtv.policy.quiz.QuizLegacyRules
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizRewardMode
import com.planyourtv.policy.quiz.QuizRewardProfile
import org.json.JSONObject

/**
 * Projects an old challenge-config form onto the active QCD.
 *
 * The old page knows only the default profile, the legacy minute fields and one
 * answerDisplaySec. Everything it cannot express — weekday/weekend profiles,
 * unknown extension fields, feedback rules it did not send — must survive a
 * save from a page that predates them.
 */
object QuizLegacyConfigAdapter {
    /** Only fields the request actually carried are applied; null means keep. */
    data class FeedbackOverride(
        val extraHoldSec: Int? = null,
        val answerRevealSec: Int? = null,
        val autoNext: Boolean? = null,
    )

    fun apply(
        current: QuizConfigDocument,
        config: QuizConfig,
        feedback: FeedbackOverride,
        hasAnswerDisplaySec: Boolean,
        levels: List<QuizLevel>?,
    ): QuizConfigDocument {
        val extensions = JSONObject(current.extensions.toString())
        if (hasAnswerDisplaySec) {
            // Legacy echo only: it never becomes the v6 feedback hold.
            val local = JSONObject(extensions.optJSONObject("local")?.toString() ?: "{}")
            local.put("legacyAnswerDisplaySec", config.answerDisplaySec)
            extensions.put("local", local)
        }
        val currentFeedback = current.rules.feedback
        return current.copy(
            rules = current.rules.copy(
                questionCount = config.pullCount,
                timeLimitSec = config.defaultTimeLimitSec,
                easyTimeLimitSec = config.easyTimeLimitSec,
                mediumTimeLimitSec = config.mediumTimeLimitSec,
                hardTimeLimitSec = config.hardTimeLimitSec,
                quizTextAuto = config.quizTextAuto,
                quizTvSizeInch = config.quizTvSizeInch,
                quizTextScalePct = config.quizTextScalePct,
                feedback = currentFeedback.copy(
                    extraHoldSec = feedback.extraHoldSec ?: currentFeedback.extraHoldSec,
                    answerRevealSec = feedback.answerRevealSec ?: currentFeedback.answerRevealSec,
                    autoNext = feedback.autoNext ?: currentFeedback.autoNext,
                ),
                shuffleOptions = config.shuffleOptions,
                legacy = QuizLegacyRules(
                    minCorrectCount = config.minCorrectCount,
                    minutesPerPoint = config.minutesPerPoint,
                    minutesPerQuestion = config.minutesPerQuestion,
                    pointsPerQuestion = config.pointsPerQuestion,
                ),
            ),
            reward = current.reward.copy(
                mode = QuizRewardMode.DIFFICULTY_SECONDS,
                // weekday/weekend stay as they are: the old form edits only default.
                profiles = current.reward.profiles.copy(
                    default = QuizRewardProfile(
                        correctSeconds = QuizDifficultySeconds(
                            easy = config.easyRewardMinutes * 60,
                            medium = config.mediumRewardMinutes * 60,
                            hard = config.hardRewardMinutes * 60,
                        ),
                        penaltySeconds = QuizDifficultySeconds(
                            easy = config.easyPenaltyMinutes * 60,
                            medium = config.mediumPenaltyMinutes * 60,
                            hard = config.hardPenaltyMinutes * 60,
                        ),
                        maxEarnedSeconds = config.maxEarnedMinutes * 60,
                        minUnlockSeconds = config.minUnlockMinutes * 60,
                    ),
                ),
            ),
            levels = levels?.mapIndexed { index, level ->
                QuizLocalConfigDocument.levelDoc(level, index)
            } ?: current.levels,
            extensions = extensions,
        )
    }
}
