package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizDifficultySeconds
import com.planyourtv.policy.quiz.QuizEngineConfig
import com.planyourtv.policy.quiz.QuizReward
import com.planyourtv.policy.quiz.QuizRewardProfile
import com.planyourtv.policy.quiz.QuizRewardProfiles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The old web page and the v5 engine still read a [QuizConfig]. These tests pin
 * how the active QCD projects into that shape: minutes come from the default
 * profile only, and answerDisplaySec is the display-only legacy echo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizLocalConfigProjectionTest {
    private val now = 1_760_000_000_000L

    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun legacyProjectionReadsTheDefaultProfileAndLegacyEcho() {
        val document = document().let { base ->
            base.copy(
                reward = QuizReward(
                    profiles = QuizRewardProfiles(
                        default = profile(120, 240, 600, 30, 1800, 600),
                        weekday = profile(60, 120, 300, 0, 900, 0),
                        weekend = profile(180, 300, 900, 30, 5400, 300),
                    ),
                ),
                extensions = JSONObject().put(
                    "local",
                    JSONObject().put("legacyAnswerDisplaySec", 12),
                ),
            )
        }

        val config = requireNotNull(document.toLegacyConfig())

        assertEquals(8, config.pullCount)
        assertEquals(45, config.defaultTimeLimitSec)
        assertEquals(30, config.maxEarnedMinutes)
        assertEquals(10, config.minUnlockMinutes)
        assertEquals(2, config.easyRewardMinutes)
        assertEquals(4, config.mediumRewardMinutes)
        assertEquals(10, config.hardRewardMinutes)
        assertEquals(0, config.easyPenaltyMinutes)
        assertEquals(1, config.mediumPenaltyMinutes)
        assertEquals(2, config.hardPenaltyMinutes)
        assertEquals(12, config.answerDisplaySec)
        assertEquals(10, config.feedbackExtraHoldSec)
    }

    @Test
    fun weekdayAndWeekendProfilesNeverLeakIntoTheLegacyView() {
        val document = document().copy(
            reward = QuizReward(
                profiles = QuizRewardProfiles(
                    default = profile(60, 120, 300, 0, 1800, 0),
                    weekend = profile(600, 900, 1200, 60, 7200, 600),
                ),
            ),
        )

        val config = requireNotNull(document.toLegacyConfig())

        assertEquals(1, config.easyRewardMinutes)
        assertEquals(2, config.mediumRewardMinutes)
        assertEquals(5, config.hardRewardMinutes)
        assertEquals(30, config.maxEarnedMinutes)
    }

    @Test
    fun missingLegacyEchoFallsBackToTheDisplayDefault() {
        val config = requireNotNull(document().copy(extensions = JSONObject()).toLegacyConfig())
        assertEquals(3, config.answerDisplaySec)
    }

    @Test
    fun legacyProjectionRejectsARoundTheLocalSurfaceCannotRun() {
        val document = document().copy(rules = document().rules.copy(questionCount = 0))
        assertNull(document.toLegacyConfig())
    }

    @Test
    fun legacyProjectionAcceptsASingleQuestionRound() {
        val document = document().copy(rules = document().rules.copy(questionCount = 1))
        val config = requireNotNull(document.toLegacyConfig())
        assertEquals(1, config.pullCount)
    }

    @Test
    fun engineProjectionMatchesTheLegacyConfigItDerivesFrom() {
        val config = requireNotNull(document().toLegacyConfig())
        val engine = QuizEngineConfig.from(config)

        assertEquals(config.pullCount, engine.pullCount)
        assertEquals(config.defaultTimeLimitSec, engine.defaultTimeLimitSec)
        assertEquals(config.answerDisplaySec, engine.answerDisplaySec)
        assertEquals(config.minutesPerQuestion, engine.minutesPerQuestion)
        assertEquals(config.shuffleOptions, engine.shuffleOptions)
    }

    private fun document(): QuizConfigDocument = QuizLocalConfigDocument.create(
        installId = "test-install",
        nowMs = now,
        config = QuizConfig(
            pullCount = 8,
            defaultTimeLimitSec = 45,
            minutesPerQuestion = 7,
            maxEarnedMinutes = 30,
        ),
        legacyAnswerDisplaySec = 30,
        levels = emptyList(),
        migrated = false,
    )

    private fun profile(
        easyCorrect: Int,
        mediumCorrect: Int,
        hardCorrect: Int,
        easyPenalty: Int?,
        maxEarned: Int,
        minUnlock: Int,
    ): QuizRewardProfile = QuizRewardProfile(
        correctSeconds = QuizDifficultySeconds(easyCorrect, mediumCorrect, hardCorrect),
        penaltySeconds = QuizDifficultySeconds(
            easyPenalty ?: 0,
            (easyPenalty ?: 0) * 2,
            (easyPenalty ?: 0) * 4,
        ),
        maxEarnedSeconds = maxEarned,
        minUnlockSeconds = minUnlock,
    )
}
