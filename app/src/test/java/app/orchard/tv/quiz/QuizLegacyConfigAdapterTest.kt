package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizDifficultySeconds
import com.planyourtv.policy.quiz.QuizFeedbackRules
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizReward
import com.planyourtv.policy.quiz.QuizRewardProfile
import com.planyourtv.policy.quiz.QuizRewardProfiles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The old form cannot express weekday/weekend profiles, feedback rules or
 * unknown extension fields. Saving from it must apply only what it shows and
 * keep everything else, otherwise a cached page silently erases JSON settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizLegacyConfigAdapterTest {
    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun oldFormSaveKeepsProfilesFeedbackAndUnknownExtensions() {
        val current = currentDocument()
        val weekend = requireNotNull(current.reward.profiles.weekend)

        val saved = QuizLegacyConfigAdapter.apply(
            current = current,
            config = legacyConfig(),
            feedback = QuizLegacyConfigAdapter.FeedbackOverride(),
            hasAnswerDisplaySec = false,
            levels = null,
        )

        // Only the default profile is projected from the old minute fields.
        assertEquals(60, saved.reward.profiles.default?.correctSeconds?.easy)
        assertEquals(1800, saved.reward.profiles.default?.maxEarnedSeconds)
        assertEquals(current.reward.profiles.weekday, saved.reward.profiles.weekday)
        assertEquals(weekend, saved.reward.profiles.weekend)
        // Feedback the old form never sent stays untouched.
        assertEquals(5, saved.rules.feedback.extraHoldSec)
        assertEquals(20, saved.rules.feedback.answerRevealSec)
        assertTrue(saved.rules.feedback.autoNext)
        // Unknown extension fields survive; the legacy echo is not rewritten.
        assertEquals(1, saved.extensions.getJSONObject("cloud").getInt("keep"))
        assertEquals(9, saved.extensions.getJSONObject("local").getInt("legacyAnswerDisplaySec"))
    }

    @Test
    fun explicitFeedbackFieldsOverrideOnlyThemselves() {
        val saved = QuizLegacyConfigAdapter.apply(
            current = currentDocument(),
            config = legacyConfig(),
            feedback = QuizLegacyConfigAdapter.FeedbackOverride(extraHoldSec = 7),
            hasAnswerDisplaySec = false,
            levels = null,
        )

        assertEquals(7, saved.rules.feedback.extraHoldSec)
        assertEquals(20, saved.rules.feedback.answerRevealSec)
        assertTrue(saved.rules.feedback.autoNext)
    }

    @Test
    fun answerDisplaySecOnlyUpdatesTheLegacyEcho() {
        val config = legacyConfig().copy(answerDisplaySec = 12)

        val saved = QuizLegacyConfigAdapter.apply(
            current = currentDocument(),
            config = config,
            feedback = QuizLegacyConfigAdapter.FeedbackOverride(),
            hasAnswerDisplaySec = true,
            levels = null,
        )

        assertEquals(12, saved.extensions.getJSONObject("local").getInt("legacyAnswerDisplaySec"))
        assertEquals(5, saved.rules.feedback.extraHoldSec)
    }

    @Test
    fun levelsAreRebuiltOnlyWhenTheFormSendsThem() {
        val kept = QuizLegacyConfigAdapter.apply(
            current = currentDocument(),
            config = legacyConfig(),
            feedback = QuizLegacyConfigAdapter.FeedbackOverride(),
            hasAnswerDisplaySec = false,
            levels = null,
        )
        assertEquals(1, kept.levels.size)
        assertEquals("local:lvl-old", kept.levels.first().levelId)

        val level = requireNotNull(QuizLevel.create(scope(), "新关卡", "lvl-new"))
        val replaced = QuizLegacyConfigAdapter.apply(
            current = currentDocument(),
            config = legacyConfig(),
            feedback = QuizLegacyConfigAdapter.FeedbackOverride(),
            hasAnswerDisplaySec = false,
            levels = listOf(level),
        )
        assertEquals(1, replaced.levels.size)
        assertEquals("local:lvl-new", replaced.levels.first().levelId)
        assertEquals(0, replaced.levels.first().sortNo)
    }

    private fun currentDocument(): QuizConfigDocument {
        val base = QuizLocalConfigDocument.create(
            installId = "test-install",
            nowMs = 1_760_000_000_000L,
            config = legacyConfig(),
            legacyAnswerDisplaySec = 9,
            levels = listOf(requireNotNull(QuizLevel.create(scope(), "旧关卡", "lvl-old"))),
            migrated = false,
        )
        return QuizLocalConfigDocument.withContentHashes(
            base.copy(
                rules = base.rules.copy(
                    feedback = QuizFeedbackRules(extraHoldSec = 5, answerRevealSec = 20, autoNext = true),
                ),
                reward = QuizReward(
                    profiles = QuizRewardProfiles(
                        default = profile(60, 120, 300),
                        weekday = profile(30, 60, 120),
                        weekend = profile(90, 150, 330),
                    ),
                ),
                extensions = JSONObject()
                    .put("cloud", JSONObject().put("keep", 1))
                    .put("local", JSONObject().put("legacyAnswerDisplaySec", 9)),
            ),
        )
    }

    private fun legacyConfig(): QuizConfig = QuizConfig(
        pullCount = 8,
        defaultTimeLimitSec = 45,
        maxEarnedMinutes = 30,
        easyRewardMinutes = 1,
        mediumRewardMinutes = 2,
        hardRewardMinutes = 5,
        easyPenaltyMinutes = 0,
        mediumPenaltyMinutes = 1,
        hardPenaltyMinutes = 2,
        minUnlockMinutes = 10,
    )

    private fun scope(): QuizAnswerScope = requireNotNull(
        QuizAnswerScope.create(
            grade = 3,
            semester = 1,
            subject = "MATH",
            textbook = "XINANDA",
            scopeType = "UNIT",
            unitNo = 1,
            difficulty = "EASY",
        ),
    )

    private fun profile(easy: Int, medium: Int, hard: Int): QuizRewardProfile = QuizRewardProfile(
        correctSeconds = QuizDifficultySeconds(easy, medium, hard),
        penaltySeconds = QuizDifficultySeconds(5, 10, 20),
        maxEarnedSeconds = 1800,
        minUnlockSeconds = 600,
    )
}
