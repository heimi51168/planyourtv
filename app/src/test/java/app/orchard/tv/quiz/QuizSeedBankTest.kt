package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizAnswerKind
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizCurriculum
import com.planyourtv.policy.quiz.QuizPresetBank
import com.planyourtv.policy.quiz.QuizSessionCodec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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

    private fun multiItemJson(
        id: String,
        answerIndices: String = "0, 2",
        prompt: String = "Which animals are mammals?",
    ) = """
        {
          "questionId": "$id",
          "prompt": "$prompt",
          "options": ["whale", "penguin", "bat", "carp"],
          "type": "multiple",
          "presentation": "choice",
          "answerIndices": [$answerIndices],
          "explanation": "Whales and bats are mammals.",
          "subject": "science",
          "grade": "g1",
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
    fun multipleChoiceBankUsesItsOwnSeedTag() {
        assertEquals(
            1,
            QuizSeedBank.importJson(bank(itemJson("seed-test-0030")), version = 1, nowMs = now),
        )
        assertEquals(
            1,
            QuizSeedBank.importMultiJson(
                bank(multiItemJson("seed-multi-test-0031")),
                version = 1,
                nowMs = now,
            ),
        )
        assertEquals(2, QuizQuestionCache.availableCount(nowMs = now))

        // Replacing the single-choice bank must not delete multiple-choice rows.
        assertEquals(
            1,
            QuizSeedBank.importJson(bank(itemJson("seed-test-0032")), version = 2, nowMs = now),
        )
        assertEquals(2, QuizQuestionCache.availableCount(nowMs = now))
        var kinds = QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "all", nowMs = now)
            .associate { it.question.questionId to it.question.answerKind }
        assertEquals(QuizAnswerKind.SINGLE, kinds["seed-test-0032"])
        assertEquals(QuizAnswerKind.MULTIPLE, kinds["seed-multi-test-0031"])

        // Replacing the multiple-choice bank must not delete single-choice rows.
        assertEquals(
            1,
            QuizSeedBank.importMultiJson(
                bank(multiItemJson("seed-multi-test-0033")),
                version = 2,
                nowMs = now,
            ),
        )
        assertEquals(2, QuizQuestionCache.availableCount(nowMs = now))
        kinds = QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "all", nowMs = now)
            .associate { it.question.questionId to it.question.answerKind }
        assertEquals(QuizAnswerKind.SINGLE, kinds["seed-test-0032"])
        assertEquals(QuizAnswerKind.MULTIPLE, kinds["seed-multi-test-0033"])
    }

    @Test
    fun multipleChoiceImportKeepsTheAnswerSet() {
        assertEquals(
            1,
            QuizSeedBank.importMultiJson(
                bank(multiItemJson("seed-multi-test-0040")),
                version = 1,
                nowMs = now,
            ),
        )
        assertEquals(
            0,
            QuizSeedBank.importMultiJson(
                bank(multiItemJson("seed-multi-test-0040")),
                version = 1,
                nowMs = now,
            ),
        )

        val item = QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "all", nowMs = now)
            .single()
            .question
        assertEquals(QuizAnswerKind.MULTIPLE, item.answerKind)
        assertEquals(listOf(0, 2), item.answerIndices)
        assertEquals(0, item.answerIndex)
    }

    @Test
    fun shippedParentFunAssetImportsAsItsOwnPresetBank() {
        val context = RuntimeEnvironment.getApplication()
        val expected = context.assets.open("quiz/parent_fun_bank.json").bufferedReader().use {
            JSONObject(it.readText()).optInt("count", 0)
        }
        assertEquals(150, expected)

        assertEquals(expected, QuizSeedBank.installParentFun(context))
        assertEquals(0, QuizSeedBank.installParentFun(context))

        // Life rows are public and unscoped, so they are countable as one bank
        // but can never be drawn by a curriculum course source.
        val preset = QuizQuestionCache.bankCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            nowMs = now,
            presetBank = QuizPresetBank.PARENT_LIFE,
        )
        assertEquals(expected, preset.total)
        assertEquals(expected, preset.unused)
        assertEquals(expected, QuizQuestionCache.availableCount(nowMs = now))

        val courseScope = QuizAnswerScope.create(
            grade = 4,
            semester = 1,
            subject = "MATH",
            textbook = "BUBIAN",
            scopeType = "MIDTERM",
            unitNo = null,
            difficulty = null,
        )!!
        assertEquals(0, QuizQuestionCache.availableCount(nowMs = now, answerScope = courseScope))

        val items = QuizQuestionCache.listAllBank(QuizQuestionCache.LOCAL_SCOPE, nowMs = now)
            .map { it.question }
        assertEquals(expected, items.size)
        assertEquals(100, items.count { it.answerKind == QuizAnswerKind.SINGLE })
        assertEquals(50, items.count { it.answerKind == QuizAnswerKind.MULTIPLE })
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
    fun brokenNewBankKeepsThePreviousSeedBank() {
        assertEquals(
            1,
            QuizSeedBank.importJson(bank(itemJson("seed-test-0020")), version = 1, nowMs = now),
        )

        // Streaming import has already deleted the old rows by the time the
        // second item turns out to be truncated; the transaction must roll back.
        val broken = """{"items": [${itemJson("seed-test-0021")}, {"questionId": "seed-test-0022""""
        assertEquals(0, QuizSeedBank.importJson(broken, version = 2, nowMs = now))

        val ids = QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "all", nowMs = now)
            .map { it.question.questionId }
            .toSet()
        assertEquals(setOf("seed-test-0020"), ids)

        // The marker stayed unset, so a later valid asset can still import.
        assertEquals(
            1,
            QuizSeedBank.importJson(bank(itemJson("seed-test-0022")), version = 2, nowMs = now),
        )
    }

    @Test
    fun unusableNewBankKeepsThePreviousSeedBank() {
        assertEquals(
            1,
            QuizSeedBank.importJson(bank(itemJson("seed-test-0023")), version = 1, nowMs = now),
        )

        val invalid = bank(itemJson("seed-test-0024", answerIndex = 9))
        assertEquals(0, QuizSeedBank.importJson(invalid, version = 2, nowMs = now))
        assertEquals(1, QuizQuestionCache.availableCount(nowMs = now))
    }

    @Test
    fun shippedAssetImportsCleanly() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(5_500, QuizSeedBank.installSingle(context))
        assertEquals(5_500, QuizQuestionCache.availableCount(nowMs = now))
        assertEquals(0, QuizSeedBank.installSingle(context))
        assertBundledScopes()
    }

    @Test
    fun shippedMultiAssetImportsCleanlyWhenBundled() {
        val context = RuntimeEnvironment.getApplication()
        val bundled = context.assets.list("quiz")?.contains("seed_bank_multi.json") == true
        assumeTrue("seed_bank_multi.json is generated separately", bundled)

        val expected = context.assets.open("quiz/seed_bank_multi.json").bufferedReader().use {
            JSONObject(it.readText()).optInt("count", 0)
        }
        assertTrue(expected > 0)

        assertEquals(expected, QuizSeedBank.installMulti(context))
        assertEquals(expected, QuizQuestionCache.availableCount(nowMs = now))
        assertEquals(0, QuizSeedBank.installMulti(context))

        // listBank is paged (default 50); this test needs the whole bank.
        val items = QuizQuestionCache.listAllBank(QuizQuestionCache.LOCAL_SCOPE, nowMs = now)
            .map { it.question }
        assertEquals(expected, items.size)
        assertTrue(items.all { it.answerKind == QuizAnswerKind.MULTIPLE })
        assertTrue(items.all { it.answerIndices.size in 2..3 })
        assertEveryBundledScopeHasItems()
    }

    private fun bundledScopes(): List<Pair<String, QuizAnswerScope>> {
        val curriculum = requireNotNull(QuizCurriculum.current())
        val scopes = ArrayList<Pair<String, QuizAnswerScope>>()
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
                ) ?: continue
                scopes += "$grade/$subject/$textbook" to scope
            }
        }
        for (grade in 1..6) {
            val scope = QuizAnswerScope.create(
                grade = grade,
                semester = 1,
                subject = "MATH",
                textbook = "XINANDA",
                scopeType = "MIDTERM",
                unitNo = null,
                difficulty = null,
            ) ?: continue
            scopes += "XINANDA $grade/MATH" to scope
        }
        return scopes
    }

    private fun assertBundledScopes() {
        val scopes = bundledScopes()
        assertEquals(55, scopes.size)
        for ((label, scope) in scopes) {
            assertEquals(
                label,
                100,
                QuizQuestionCache.availableCount(nowMs = now, answerScope = scope),
            )
        }
    }

    private fun assertEveryBundledScopeHasItems() {
        val scopes = bundledScopes()
        assertEquals(55, scopes.size)
        for ((label, scope) in scopes) {
            assertTrue(
                "$label has no multiple-choice questions",
                QuizQuestionCache.availableCount(nowMs = now, answerScope = scope) > 0,
            )
        }
    }
}
