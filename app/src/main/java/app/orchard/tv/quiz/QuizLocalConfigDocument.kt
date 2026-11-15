package app.orchard.tv.quiz

import com.planyourtv.policy.quiz.QUIZ_DEFAULT_ANSWER_DISPLAY_SEC
import com.planyourtv.policy.quiz.QUIZ_MAX_EARNED_MINUTES
import com.planyourtv.policy.quiz.QUIZ_MIN_LOCAL_PULL_COUNT
import com.planyourtv.policy.quiz.QUIZ_MAX_PULL_COUNT
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizCalendar
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizConfigDocumentEtag
import com.planyourtv.policy.quiz.QuizConfigDocumentValidator
import com.planyourtv.policy.quiz.QuizDeviceCapabilities
import com.planyourtv.policy.quiz.QuizDifficultySeconds
import com.planyourtv.policy.quiz.QuizDocIssue
import com.planyourtv.policy.quiz.QuizDocOrigin
import com.planyourtv.policy.quiz.QuizDocUpdatedBy
import com.planyourtv.policy.quiz.QuizFeedbackRules
import com.planyourtv.policy.quiz.QuizLegacyRules
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizLevelDoc
import com.planyourtv.policy.quiz.QuizLevelOrigin
import com.planyourtv.policy.quiz.QuizLevelSource
import com.planyourtv.policy.quiz.QuizLocalScope
import com.planyourtv.policy.quiz.QuizQuestionPolicy
import com.planyourtv.policy.quiz.QuizQuota
import com.planyourtv.policy.quiz.QuizQuotaEnforcedBy
import com.planyourtv.policy.quiz.QuizRequirements
import com.planyourtv.policy.quiz.QuizReward
import com.planyourtv.policy.quiz.QuizRewardMode
import com.planyourtv.policy.quiz.QuizRewardProfile
import com.planyourtv.policy.quiz.QuizRewardProfiles
import com.planyourtv.policy.quiz.QuizRules
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds the local QCD and projects it back onto the v5 execution model.
 *
 * The local TV has no child identity: one document describes the device, and
 * the levels use LOCAL ids that never collide with cloud levels. The legacy
 * keys stay readable but are never used as a second source of truth.
 */
object QuizLocalConfigDocument {
    const val LOCAL_DOCUMENT_PREFIX = "quizcfg:local:"
    const val LOCAL_LEVEL_PREFIX = "local:"

    /** Rules that keep a local document exactly as executable as the old form. */
    fun localReadIssues(document: QuizConfigDocument): List<QuizDocIssue> {
        val issues = QuizConfigDocumentValidator.validate(document).toMutableList()
        issues += executionIssues(document)
        return issues
    }

    fun localWriteIssues(document: QuizConfigDocument): List<QuizDocIssue> {
        val issues = QuizConfigDocumentValidator.validate(document, localWrite = true).toMutableList()
        issues += executionIssues(document)
        return issues
    }

    private fun executionIssues(document: QuizConfigDocument): List<QuizDocIssue> {
        val issues = mutableListOf<QuizDocIssue>()
        if (document.rules.questionCount !in QUIZ_MIN_LOCAL_PULL_COUNT..QUIZ_MAX_PULL_COUNT) {
            issues += QuizDocIssue("rules.questionCount", "LOCAL_MIN_QUESTIONS_5")
        }
        issues += QuizDeviceCapabilities.issues(document, cloudRound = false)
        return issues
    }

    /** The first local document: legacy values when present, product defaults otherwise. */
    fun create(
        installId: String,
        nowMs: Long,
        config: QuizConfig,
        legacyAnswerDisplaySec: Int,
        levels: List<QuizLevel>,
        migrated: Boolean,
    ): QuizConfigDocument {
        val profile = QuizRewardProfile(
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
        )
        val document = QuizConfigDocument(
            documentId = documentId(installId),
            revision = 1,
            levelsEtag = "",
            etag = "",
            origin = if (migrated) QuizDocOrigin.LOCAL_MIGRATED else QuizDocOrigin.LOCAL,
            updatedAt = isoTime(nowMs),
            updatedBy = if (migrated) QuizDocUpdatedBy.MIGRATION else QuizDocUpdatedBy.TV_LOCAL_EDIT,
            enabled = QuizSessionStore.enabled(),
            rules = QuizRules(
                questionCount = config.pullCount,
                timeLimitSec = config.defaultTimeLimitSec,
                // The old answerDisplaySec was the total hold, so it still never
                // maps onto the extra hold. New configs start from the product
                // default (10 s); migrated legacy rules may carry their own value.
                feedback = QuizFeedbackRules(
                    extraHoldSec = config.feedbackExtraHoldSec,
                    answerRevealSec = 30,
                    autoNext = false,
                ),
                shuffleOptions = config.shuffleOptions,
                legacy = QuizLegacyRules(
                    minCorrectCount = config.minCorrectCount,
                    minutesPerPoint = config.minutesPerPoint,
                    minutesPerQuestion = config.minutesPerQuestion,
                    pointsPerQuestion = config.pointsPerQuestion,
                ),
            ),
            reward = QuizReward(
                mode = QuizRewardMode.DIFFICULTY_SECONDS,
                profiles = QuizRewardProfiles(default = profile),
            ),
            quota = QuizQuota(dailyMaxSeconds = 0, enforcedBy = QuizQuotaEnforcedBy.LOCAL),
            calendar = QuizCalendar(),
            questionPolicy = QuizQuestionPolicy(),
            requirements = QuizRequirements(),
            levels = levels.mapIndexed { index, level -> levelDoc(level, index) },
            extensions = JSONObject().put(
                "local",
                JSONObject().put("legacyAnswerDisplaySec", legacyAnswerDisplaySec),
            ),
        )
        return withContentHashes(document)
    }

    fun documentId(installId: String): String = LOCAL_DOCUMENT_PREFIX + installId

    fun levelDoc(level: QuizLevel, index: Int): QuizLevelDoc {
        val scope = level.scope
        val levelId = if (level.id.startsWith(LOCAL_LEVEL_PREFIX)) {
            level.id
        } else {
            LOCAL_LEVEL_PREFIX + level.id
        }
        return QuizLevelDoc(
            levelId = levelId,
            origin = QuizLevelOrigin.LOCAL,
            name = level.name,
            enabled = true,
            sortNo = index,
            sources = listOf(QuizLevelSource.BANK),
            subjects = listOf(scope.subject),
            scopeType = scope.scopeType,
            unitNos = if (scope.scopeType == "UNIT") {
                scope.unitNo?.let { listOf(it) } ?: emptyList()
            } else {
                emptyList()
            },
            grade = scope.grade,
            semester = scope.semester,
            textbook = scope.textbook,
            difficulty = scope.difficulty,
            localScope = QuizLocalScope(
                grade = scope.grade,
                semester = scope.semester,
                subject = scope.subject,
                textbook = scope.textbook,
                scopeType = scope.scopeType,
                unitNo = scope.unitNo,
                difficulty = scope.difficulty,
            ),
        )
    }

    /** Applies the two content hashes a stored document must carry. */
    fun withContentHashes(document: QuizConfigDocument): QuizConfigDocument {
        val levelsEtag = QuizConfigDocumentEtag.levelsEtag(document.levels)
        val staged = document.copy(levelsEtag = levelsEtag)
        return staged.copy(etag = QuizConfigDocumentEtag.etag(staged))
    }
}

/**
 * The ordered curriculum levels the TV executes. Returns null when a level
 * cannot be rebuilt, which keeps a corrupt document from becoming a partial
 * list. Disabled levels are skipped but stay in the document for the web page.
 */
fun QuizConfigDocument.localLevels(): List<QuizLevel>? {
    val result = ArrayList<QuizLevel>(levels.size)
    for (level in levels.sortedBy { it.sortNo }) {
        if (!level.enabled) continue
        if (level.origin != QuizLevelOrigin.LOCAL) return null
        val scope = level.localScope ?: return null
        val answerScope = QuizAnswerScope.create(
            grade = scope.grade,
            semester = scope.semester,
            subject = scope.subject,
            textbook = scope.textbook,
            scopeType = scope.scopeType,
            unitNo = scope.unitNo,
            difficulty = scope.difficulty,
        ) ?: return null
        val id = level.levelId.removePrefix(QuizLocalConfigDocument.LOCAL_LEVEL_PREFIX)
        if (id.isEmpty() || id == level.levelId) return null
        result += QuizLevel.create(answerScope, level.name, id) ?: return null
    }
    return result
}

/**
 * The projection consumed by the v5 engine and the older web form. The v6
 * session freezes the reward profile itself; these minute fields exist so
 * existing callers keep a coherent snapshot.
 */
fun QuizConfigDocument.toLegacyConfig(): QuizConfig? {
    val profile = reward.profiles.default ?: return null
    if (rules.questionCount !in QUIZ_MIN_LOCAL_PULL_COUNT..QUIZ_MAX_PULL_COUNT) return null
    return QuizConfig(
        defaultTimeLimitSec = rules.timeLimitSec,
        minutesPerPoint = rules.legacy?.minutesPerPoint ?: 5,
        minCorrectCount = rules.legacy?.minCorrectCount ?: 1,
        maxEarnedMinutes = (profile.maxEarnedSeconds / 60)
            .coerceIn(1, QUIZ_MAX_EARNED_MINUTES),
        shuffleOptions = rules.shuffleOptions,
        // Display only: the old page echoes the value it saved before the
        // upgrade. It never drives feedback timing again.
        answerDisplaySec = extensions.optJSONObject("local")
            ?.optInt("legacyAnswerDisplaySec", QUIZ_DEFAULT_ANSWER_DISPLAY_SEC)
            ?: QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
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
}

private fun isoTime(nowMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(nowMs))
