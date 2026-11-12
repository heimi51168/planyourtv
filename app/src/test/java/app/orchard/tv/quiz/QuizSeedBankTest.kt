package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizCurriculum
import com.planyourtv.policy.quiz.QuizSessionCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class QuizSeedBankTest {
    private val now = 1_760_000_000_000L
    private val testDb = "quiz_seed_bank_test.db"

    private fun itemJson(id: String, prompt: String = "Question $id", answerIndex: Int = 0) = """
        {
          "questionId": "$id",
          "prompt": "$prompt",
          "options": ["A", "B", "C", "D"],
          "answerIndex": $answerIndex,
          "explanation": "Because.",
          "subject": "数学",
          "grade": "小学一年级",
          "difficulty": "easy",
          "points": 1,
          "timeLimitSec": 60,
          "recommendedTimeSec": 60
        }
    """.trimIndent()

    private fun bank(vararg items: String) = """{"items": [${items.joinToString(",")}]}"""

    private fun question(id: String) = requireNotNull(QuizSessionCodec.decodeItem(itemJson(id)))

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        TvStore.init(context)
        QuizQuestionCache.initForTest(context, testDb)
        QuizQuestionCache.clearBank(QuizQuestionCache.LOCAL_SCOPE, QuizQuestionCache.ClearMode.ALL)
        QuizQuestionCache.releaseOrphans(null)
        QuizSeedBank.clearMarkerForTest()
    }

    @Test
    fun importAppendsItemsOnlyOnce() {
        val raw = bank(itemJson("seed-test-0001"), itemJson("seed-test-0002"))

        assertEquals(2, QuizSeedBank.importJson(raw, version = 1, nowMs = now))
        assertEquals(2, QuizQuestionCache.availableCount(nowMs = now))
        assertEquals(0, QuizSeedBank.importJson(raw, version = 1, nowMs = now))
        assertEquals(2, QuizQuestionCache.availableCount(nowMs = now))
    }

    @Test
    fun clearedBankIsNotResurrected() {
        val raw = bank(itemJson("seed-test-0003"), itemJson("seed-test-0004"))
        assertEquals(2, QuizSeedBank.importJson(raw, version = 1, nowMs = now))

        QuizQuestionCache.clearBank(QuizQuestionCache.LOCAL_SCOPE, QuizQuestionCache.ClearMode.ALL)

        assertEquals(0, QuizSeedBank.importJson(raw, version = 1, nowMs = now))
        assertEquals(0, QuizQuestionCache.availableCount(nowMs = now))
    }

    @Test
    fun newVersionReplacesBundledSeedRows() {
        assertEquals(
            1,
            QuizSeedBank.importJson(bank(itemJson("seed-test-0005")), version = 1, nowMs = now),
        )

        val v2 = bank(itemJson("seed-test-0005"), itemJson("seed-test-0006"))
        assertEquals(2, QuizSeedBank.importJson(v2, version = 2, nowMs = now))
        assertEquals(2, QuizQuestionCache.availableCount(nowMs = now))
    }

    @Test
    fun newVersionKeepsQuestionsGeneratedByTheUser() {
        assertEquals(
            1,
            QuizQuestionCache.appendLocalQuestions(
                "user-batch",
                listOf(question("user-test-0001")),
                now,
            ).appended,
        )
        assertEquals(
            1,
            QuizSeedBank.importJson(bank(itemJson("seed-test-0010")), version = 1, nowMs = now),
        )

        assertEquals(
            1,
            QuizSeedBank.importJson(bank(itemJson("seed-test-0011")), version = 2, nowMs = now),
        )
        assertEquals(2, QuizQuestionCache.availableCount(nowMs = now))
        val ids = QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "all", nowMs = now)
            .map { it.question.questionId }
            .toSet()
        assertEquals(setOf("user-test-0001", "seed-test-0011"), ids)
    }

    @Test
    fun freshInstallDefaultsToMidtermScope() {
        val prefs = QuizStudyProfile.current()

        assertNotNull(prefs)
        assertEquals("MIDTERM", prefs!!.optString("scope"))
        assertEquals(4, prefs.optInt("grade"))
        assertEquals("MATH", prefs.optString("subject"))
    }

    @Test
    fun invalidItemsAreSkipped() {
        val raw = bank(itemJson("seed-test-0007"), itemJson("seed-test-0008", answerIndex = 9))

        assertEquals(1, QuizSeedBank.importJson(raw, version = 1, nowMs = now))
        assertEquals(1, QuizQuestionCache.availableCount(nowMs = now))
    }

    @Test
    fun emptyBankDoesNotSetTheMarker() {
        assertEquals(0, QuizSeedBank.importJson("""{"items": []}""", version = 1, nowMs = now))

        // A later start with a valid asset must still be able to import.
        val raw = bank(itemJson("seed-test-0009"))
        assertEquals(1, QuizSeedBank.importJson(raw, version = 1, nowMs = now))
    }

    @Test
    fun shippedAssetImportsCleanly() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(5_500, QuizSeedBank.install(context))
        assertEquals(5_500, QuizQuestionCache.availableCount(nowMs = now))
        assertEquals(0, QuizSeedBank.install(context))

        val curriculum = requireNotNull(QuizCurriculum.current())
        var combinations = 0
        for (grade in 1..9) {
            for (subject in curriculum.subjectIds()) {
                if (subject == QuizAnswerScope.SUBJECT_ALL) continue
                val textbook = curriculum.defaultTextbook(grade, subject) ?: continue
                val scope = QuizAnswerScope.create(
                    grade = grade,
                    semester = 1,
                    subject = subject,
                    textbook = textbook,
                    scopeType = "MIDTERM",
                    unitNo = null,
                    difficulty = null,
                )
                assertEquals(
                    "$grade/$subject/$textbook",
                    100,
                    QuizQuestionCache.availableCount(nowMs = now, answerScope = scope),
                )
                combinations++
            }
        }
        assertEquals(49, combinations)

        for (grade in 1..6) {
            val scope = QuizAnswerScope.create(
                grade = grade,
                semester = 1,
                subject = "MATH",
                textbook = "XINANDA",
                scopeType = "MIDTERM",
                unitNo = null,
                difficulty = null,
            )
            assertEquals(
                "XINANDA $grade/MATH",
                100,
                QuizQuestionCache.availableCount(nowMs = now, answerScope = scope),
            )
        }
    }
}
