package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizCalendar
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizDayType
import com.planyourtv.policy.quiz.QuizDifficultySeconds
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizReward
import com.planyourtv.policy.quiz.QuizRewardProfile
import com.planyourtv.policy.quiz.QuizRewardProfiles
import com.planyourtv.policy.quiz.QuizRun
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizSubmission
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/**
 * Pins the v6 start-time snapshot: the day type and reward profile are chosen
 * once from the active local QCD, frozen into the session, and settlement reads
 * the frozen copy rather than the calendar again. Legacy v5 records keep their
 * minute engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizLocalScoringIntegrationTest {
    private val now = 1_760_000_000_000L

    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    @Test
    fun weekendDocumentFreezesTheWeekendProfile() {
        val document = documentWithProfiles()
        val plan = plan(document, at(2026, Calendar.NOVEMBER, 14, 10))

        assertEquals(QuizDayType.WEEKEND, plan.dayType)
        assertEquals(90, plan.profile.correctSeconds.easy)
        assertEquals(150, plan.profile.correctSeconds.medium)
        assertEquals(2700, plan.profile.maxEarnedSeconds)
        assertEquals(document.revision, plan.document.revision)
        assertEquals(document.etag, plan.document.etag)
    }

    @Test
    fun weekdayUsesItsOwnProfileAndMissingDayFallsBackAsAWhole() {
        val document = documentWithProfiles()
        val weekday = plan(document, at(2026, Calendar.NOVEMBER, 13, 10))
        assertEquals(QuizDayType.WEEKDAY, weekday.dayType)
        assertEquals(30, weekday.profile.correctSeconds.easy)
        assertEquals(5, weekday.profile.penaltySeconds.easy)

        val defaultOnly = document.copy(
            reward = document.reward.copy(
                profiles = document.reward.profiles.copy(weekend = null),
            ),
        ).let(QuizLocalConfigDocument::withContentHashes)
        val weekend = plan(defaultOnly, at(2026, Calendar.NOVEMBER, 14, 10))
        assertEquals(QuizDayType.WEEKEND, weekend.dayType)
        // A missing weekend entry means the default profile, never a partial merge.
        assertEquals(60, weekend.profile.correctSeconds.easy)
        assertEquals(1800, weekend.profile.maxEarnedSeconds)
    }

    @Test
    fun invalidDocumentIsRejectedInsteadOfStarting() {
        val document = documentWithProfiles()
        val shortRound = document.copy(
            rules = document.rules.copy(questionCount = 0),
        ).let(QuizLocalConfigDocument::withContentHashes)
        assertRejected(shortRound, QuizLocalRoundPlanner.DOCUMENT_INVALID)

        val badZone = document.copy(
            calendar = QuizCalendar(timezone = "Mars/Olympus"),
        ).let(QuizLocalConfigDocument::withContentHashes)
        assertRejected(badZone, QuizLocalRoundPlanner.DOCUMENT_INVALID)
    }

    @Test
    fun frozenWeekendProfileSettlesTheRoundAfterTheDayChanges() {
        val plan = plan(documentWithProfiles(), at(2026, Calendar.NOVEMBER, 14, 10))
        val session = localSession(
            originSchemaVersion = 6,
            profile = plan.profile,
        )

        // Two easy correct + one medium correct at the weekend rates, one wrong
        // easy answer deducted. Monday rates would produce 85 instead of 230.
        assertEquals(230, QuizSettlement.earnedSeconds(session))
    }

    @Test
    fun cloudV6RoundPaysTheFrozenProfileAndKeepsSubMinuteSeconds() {
        val plan = plan(documentWithProfiles(), at(2026, Calendar.NOVEMBER, 14, 10))
        val cloud = localSession(
            originSchemaVersion = 6,
            profile = plan.profile,
            source = QbClient.CLOUD_SESSION_SOURCE,
        )
        // The same 230 s a local v6 round pays. The server recomputes this exact
        // formula from the reported answers instead of trusting a minute figure.
        assertEquals(230, QuizSettlement.earnedSeconds(cloud))

        val shortProfile = plan.profile.copy(
            correctSeconds = plan.profile.correctSeconds.copy(easy = 25),
            penaltySeconds = plan.profile.penaltySeconds.copy(
                easy = 5, medium = 10, hard = 15,
            ),
            maxEarnedSeconds = 600,
        )
        val shortRound = cloud.copy(
            rewardProfile = shortProfile,
            run = cloud.run!!.copy(submissions = cloud.run!!.submissions.take(1)),
        )
        // A cloud round can earn less than one minute; it must not truncate to 0.
        assertEquals(25, QuizSettlement.earnedSeconds(shortRound))
    }

    @Test
    fun legacyCloudRoundStillDefersToTheServer() {
        // Cloud v5 rounds carry no frozen profile and keep siding with the server.
        val cloud = localSession(
            originSchemaVersion = 5,
            profile = null,
            source = QbClient.CLOUD_SESSION_SOURCE,
        )
        assertNull(QuizSettlement.earnedSeconds(cloud))
    }

    @Test
    fun v5SessionStillUsesTheMinuteEngine() {
        val v5 = localSession(originSchemaVersion = 5, profile = null)
        assertEquals(180, QuizSettlement.earnedSeconds(v5))

        // A v6 record written by an earlier build without a frozen profile must
        // keep the v5 settlement its round already promised.
        val v6WithoutProfile = localSession(originSchemaVersion = 6, profile = null)
        assertEquals(180, QuizSettlement.earnedSeconds(v6WithoutProfile))
    }

    private fun plan(
        document: QuizConfigDocument,
        atMs: Long,
    ): QuizLocalRoundPlan {
        val result = QuizLocalRoundPlanner.plan(document, atMs)
        assertTrue("expected Ready, got $result", result is QuizLocalPlanResult.Ready)
        return (result as QuizLocalPlanResult.Ready).plan
    }

    private fun assertRejected(document: QuizConfigDocument, code: String) {
        val result = QuizLocalRoundPlanner.plan(document, now)
        assertTrue("expected Rejected($code), got $result", result is QuizLocalPlanResult.Rejected)
        assertEquals(code, (result as QuizLocalPlanResult.Rejected).code)
    }

    private fun documentWithProfiles(): QuizConfigDocument {
        val base = QuizLocalConfigDocument.create(
            installId = "test-install",
            nowMs = now,
            config = QuizConfig(pullCount = 5, maxEarnedMinutes = 30),
            legacyAnswerDisplaySec = 3,
            levels = emptyList(),
            migrated = false,
        )
        return base.copy(
            reward = QuizReward(
                profiles = QuizRewardProfiles(
                    default = profile(
                        correct = intArrayOf(60, 120, 300),
                        penalty = intArrayOf(0, 0, 0),
                        maxEarned = 1800,
                    ),
                    weekday = profile(
                        correct = intArrayOf(30, 60, 120),
                        penalty = intArrayOf(5, 10, 20),
                        maxEarned = 900,
                    ),
                    weekend = profile(
                        correct = intArrayOf(90, 150, 330),
                        penalty = intArrayOf(10, 20, 30),
                        maxEarned = 2700,
                    ),
                ),
            ),
        ).let(QuizLocalConfigDocument::withContentHashes)
    }

    private fun profile(
        correct: IntArray,
        penalty: IntArray,
        maxEarned: Int,
    ): QuizRewardProfile = QuizRewardProfile(
        correctSeconds = QuizDifficultySeconds(correct[0], correct[1], correct[2]),
        penaltySeconds = QuizDifficultySeconds(penalty[0], penalty[1], penalty[2]),
        maxEarnedSeconds = maxEarned,
        minUnlockSeconds = 0,
    )

    private fun localSession(
        originSchemaVersion: Int,
        profile: QuizRewardProfile?,
        source: String = QuizChallenge.LOCAL_BANK_SESSION_SOURCE,
    ): QuizSession {
        val items = listOf(item("q1", "easy"), item("q2", "easy"), item("q3", "medium"))
        val run = QuizRun(
            correctCount = 2,
            submissions = listOf(
                QuizSubmission("q1", 0, correct = true, timedOut = false, answeredAt = now),
                QuizSubmission("q2", 1, correct = false, timedOut = false, answeredAt = now),
                QuizSubmission("q3", 2, correct = true, timedOut = false, answeredAt = now),
            ),
        )
        return QuizSession(
            schemaVersion = 6,
            originSchemaVersion = originSchemaVersion,
            id = "local-test-round",
            source = source,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + 60_000,
            status = QuizSessionStatus.SETTLING,
            config = QuizConfig(
                easyRewardMinutes = 1,
                mediumRewardMinutes = 2,
                hardRewardMinutes = 5,
                maxEarnedMinutes = 60,
            ),
            rewardProfile = profile,
            items = items,
            run = run,
        )
    }

    private fun item(id: String, difficulty: String): QuizItem = QuizItem(
        questionId = id,
        prompt = "prompt-$id",
        options = listOf("a", "b", "c", "d"),
        answerIndex = 0,
        difficulty = difficulty,
    )

    /** 10:00 local time in the QCD's Shanghai timezone. */
    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis

    private fun clearState() {
        listOf(
            "quiz.doc.local",
            "quiz.doc.installId",
            "quiz.feedbackVoiceHoldMigrated.v1",
            "quiz.doc.unreadable.v1",
            "quiz.challengeConfig",
            "quiz.answerScopes",
            "quiz.answerScope",
            "quiz.generationPrefs",
        ).forEach { TvStore.removeDurable(it) }
    }
}
