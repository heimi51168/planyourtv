package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizAnswerScope
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizAnswerScopeStoreTest {
    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
        // Explicit "null" pins the bound mode; generation prefs stay separate.
        QuizSessionStore.saveAnswerScope(null)
        QuizSessionStore.saveGenerationPrefs(
            JSONObject()
                .put("grade", "4a")
                .put("subject", "all")
                .put("textbook", "general")
                .put("scope", "review")
                .put("unit", 3)
                .put("difficulty", "medium"),
        )
    }

    @Test
    fun boundAllSubjectKeepsTheOtherFieldsAsWildcards() {
        // The legacy single scope migrates into the ordered level list.
        val levels = QuizAnswerScopeStore.levels()
        assertEquals(1, levels.size)
        val scope = levels.single().scope
        assertNotNull(scope)
        assertEquals(QuizAnswerScope.SUBJECT_ALL, scope.subject)
        assertEquals(QuizAnswerScope.TEXTBOOK_ALL, scope.textbook)
        assertEquals(4, scope.grade)
        assertEquals(1, scope.semester)
        assertEquals("REVIEW", scope.scopeType)
        assertEquals("MEDIUM", scope.difficulty)
        assertNull(scope.unitNo)
    }
}
