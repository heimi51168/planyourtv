package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The lock desktop's quiz entry is display-only now: off by default, independent
 * from the legacy cloud feature switch, and every new local document starts
 * enabled so the quiz itself is always available.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizLockEntryFlagTest {
    private val now = 1_760_000_000_000L

    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
        QuizSessionStore.setLockEntryVisible(false)
        QuizSessionStore.setEnabled(true)
    }

    @Test
    fun displayFlagDefaultsOff() {
        assertFalse(QuizSessionStore.lockEntryVisible())
    }

    @Test
    fun displayFlagPersistsWithoutTouchingTheLegacySwitch() {
        QuizSessionStore.setLockEntryVisible(true)
        assertTrue(QuizSessionStore.lockEntryVisible())

        QuizSessionStore.setEnabled(false)
        assertTrue(QuizSessionStore.lockEntryVisible())
        assertFalse(QuizSessionStore.enabled())

        QuizSessionStore.setLockEntryVisible(false)
        assertFalse(QuizSessionStore.lockEntryVisible())
        assertFalse(QuizSessionStore.enabled())
    }

    @Test
    fun newLocalDocumentIsAlwaysEnabled() {
        val document = QuizLocalConfigDocument.create(
            installId = "lock-entry-test",
            nowMs = now,
            config = QuizConfig(),
            legacyAnswerDisplaySec = 3,
            levels = emptyList(),
            migrated = false,
        )
        assertTrue(document.enabled)
    }
}
