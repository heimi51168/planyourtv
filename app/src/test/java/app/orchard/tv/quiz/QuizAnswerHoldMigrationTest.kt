package app.orchard.tv.quiz

import android.util.Base64
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QUIZ_DEFAULT_ANSWER_DISPLAY_SEC
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionCodec
import com.planyourtv.policy.quiz.QuizSessionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * answerDisplaySec changed from an auto-advance delay (old default 30) to a
 * minimum feedback stay (default 3). An upgraded TV must not turn every old
 * config or stored session into a 30-second hold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizAnswerHoldMigrationTest {
    private val now = System.currentTimeMillis()
    private val sessionKey = "quiz.session"
    private val challengeKey = "quiz.challengeConfig"
    private val migratedKey = "quiz.answerDisplayMigrated.v1"
    private val sessionMigratedKey = "quiz.sessionAnswerDisplayMigrated.v1"

    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
        clearMigrationState()
        // Re-seed the challenge config after removing the marker: leaving the
        // old default in place is exactly the upgrade state under test.
        TvStore.putStringDurable(
            challengeKey,
            QuizSessionCodec.encodeConfig(QuizConfig(answerDisplaySec = 30)).toString(),
        )
        TvStore.putStringDurable(sessionKey, plainSession(answerDisplaySec = 30))
    }

    @After
    fun tearDown() {
        clearMigrationState()
    }

    @Test
    fun oldChallengeConfigMigratesOnceToTheNewDefault() {
        assertEquals(
            QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
            QuizSessionStore.loadChallengeConfig()?.answerDisplaySec,
        )
        // The migration is one-time: an intentional 30 chosen in the new UI
        // must survive the next launch.
        assertTrue(QuizSessionStore.saveChallengeConfig(QuizConfig(answerDisplaySec = 30)))
        assertEquals(30, QuizSessionStore.loadChallengeConfig()?.answerDisplaySec)
    }

    @Test
    fun oldStoredSessionMigratesToTheNewDefault() {
        assertEquals(
            QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
            QuizSessionStore.loadSession()?.config?.answerDisplaySec,
        )
        // A session written by this build carries the migration marker, so an
        // intentional 12 must survive. Seeding the stored record directly keeps
        // this test on the plaintext path: Robolectric has no AndroidKeyStore
        // for QuizCrypto to encrypt through saveSession.
        TvStore.putStringDurable(sessionKey, plainSession(answerDisplaySec = 12))
        TvStore.putStringDurable(sessionMigratedKey, "1")
        assertEquals(12, QuizSessionStore.loadSession()?.config?.answerDisplaySec)
    }

    private fun clearMigrationState() {
        TvStore.removeDurable(sessionKey)
        TvStore.removeDurable(challengeKey)
        TvStore.removeDurable(migratedKey)
        TvStore.removeDurable(sessionMigratedKey)
    }

    private fun plainSession(answerDisplaySec: Int): String {
        val item = QuizItem(
            questionId = "q_1234567",
            prompt = "下面哪个是偶数？",
            options = listOf("3", "5", "8", "9"),
            answerIndex = 2,
            explanation = "8 能被 2 整除。",
            subject = "math",
            grade = "g2",
            difficulty = "easy",
            points = 1,
            timeLimitSec = 60,
            recommendedTimeSec = 60,
        )
        val session = QuizSession(
            id = "sess_12345678",
            source = "web",
            createdAt = now,
            updatedAt = now,
            expiresAt = now + 24 * 60 * 60 * 1000L,
            status = QuizSessionStatus.READY,
            language = "zh",
            config = QuizConfig(minCorrectCount = 1, answerDisplaySec = answerDisplaySec),
            items = listOf(item),
            run = null,
        )
        val encoded = QuizSessionCodec.encode(session).toString().toByteArray(Charsets.UTF_8)
        return "plain:v1:" + Base64.encodeToString(encoded, Base64.NO_WRAP)
    }
}
