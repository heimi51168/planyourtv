package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizWrongReviewLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizWrongReviewLevelStoreTest {
    private val key = "quiz.wrongReviewLevel"

    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
        TvStore.removeDurable(key)
    }

    @Test
    fun saveLoadAndClearUseTheirOwnKey() {
        val level = requireNotNull(
            QuizWrongReviewLevel.create("错题复习", id = "wrong-store"),
        )
        assertTrue(QuizWrongReviewLevelStore.save(level))
        assertEquals(level, QuizWrongReviewLevelStore.level())

        assertTrue(QuizWrongReviewLevelStore.clear())
        assertNull(QuizWrongReviewLevelStore.level())
    }

    @Test
    fun corruptStoredValueIsIgnoredInsteadOfCrashingTheLevelWheel() {
        TvStore.putStringDurable(key, "not json")
        assertNull(QuizWrongReviewLevelStore.level())
    }
}
