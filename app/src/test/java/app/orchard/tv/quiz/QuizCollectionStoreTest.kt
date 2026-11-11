package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizCollection
import com.planyourtv.policy.quiz.QuizCollectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class QuizCollectionStoreTest {
    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
        TvStore.removeDurable("quiz.collections")
        TvStore.removeDurable("quiz.customLevels")
        TvStore.removeDurable("quiz.answerScopes")
        TvStore.removeDurable("quiz.answerScope")
    }

    @Test
    fun addCollectionTrimsAndRejectsDuplicateNames() {
        val tag = QuizCollectionStore.addCollection("  成语专项  ")
        assertNotNull(tag)
        assertEquals("成语专项", tag!!.name)
        assertNull(QuizCollectionStore.addCollection("成语专项"))
    }

    @Test
    fun renameKeepsTheIdAndRejectsDuplicates() {
        val first = QuizCollectionStore.addCollection("成语")!!
        val second = QuizCollectionStore.addCollection("英语")!!
        val renamed = QuizCollectionStore.renameCollection(first.id, "语文")
        assertNotNull(renamed)
        assertEquals(first.id, renamed!!.id)
        assertEquals("语文", QuizCollectionStore.find(first.id)!!.name)
        assertNull(QuizCollectionStore.renameCollection(second.id, "语文"))
        assertNull(QuizCollectionStore.renameCollection("col-missing", "x"))
    }

    @Test
    fun collectionCapIsTwenty() {
        repeat(QuizCollection.MAX_COLLECTIONS) { index ->
            assertNotNull(QuizCollectionStore.addCollection("tag-$index"))
        }
        assertNull(QuizCollectionStore.addCollection("one-too-many"))
    }

    @Test
    fun customLevelsAreHiddenWhenTheirTagIsMissing() {
        val tag = QuizCollectionStore.addCollection("成语")!!
        val level = QuizCollectionLevel.create(tag.id, "成语闯关")!!
        assertTrue(QuizCollectionStore.saveCustomLevels(listOf(level)))
        assertEquals(listOf(level), QuizCollectionStore.customLevels())

        // Simulate a manually edited store that lost the tag definition.
        TvStore.putStringDurable(
            "quiz.collections",
            QuizCollection.encodeList(emptyList()).toString(),
        )
        assertTrue(QuizCollectionStore.customLevels().isEmpty())
        assertEquals(QuizAnswerScopeStore.levels().size, QuizCollectionStore.combinedLevelCount())
    }

    @Test
    fun saveCustomLevelsRejectsMissingTagsAndDuplicateTags() {
        val tag = QuizCollectionStore.addCollection("成语")!!
        val missing = QuizCollectionLevel.create("col-missing", "")!!
        assertFalse(QuizCollectionStore.saveCustomLevels(listOf(missing)))

        val first = QuizCollectionLevel.create(tag.id, "一")!!
        val second = QuizCollectionLevel.create(tag.id, "二")!!
        assertFalse(QuizCollectionStore.saveCustomLevels(listOf(first, second)))
    }
}
