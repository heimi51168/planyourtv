package app.orchard.tv.quiz

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuizAiAnswerTest {
    private val options = listOf("Alpha", "Bravo", "Charlie", "Delta")

    @Test
    fun letterAndTextMustAgree() {
        val resolved = QuizAiClient.resolveAnswerIndex(options, "C", "Charlie")
        assertNull(resolved.error)
        assertEquals(2, resolved.index)
    }

    @Test
    fun letterAcceptsCommonForms() {
        assertEquals(2, QuizAiClient.resolveAnswerIndex(options, "C.", "Charlie").index)
        assertEquals(2, QuizAiClient.resolveAnswerIndex(options, "(C)", "Charlie").index)
        assertEquals(2, QuizAiClient.resolveAnswerIndex(options, "Option C", "Charlie").index)
        assertEquals(2, QuizAiClient.resolveAnswerIndex(options, "答案：C", "Charlie").index)
    }

    @Test
    fun textToleratesPrefixesAndQuotes() {
        assertEquals(2, QuizAiClient.resolveAnswerIndex(options, "C", "C. Charlie").index)
        assertEquals(2, QuizAiClient.resolveAnswerIndex(options, "C", "(C) Charlie").index)
        assertEquals(2, QuizAiClient.resolveAnswerIndex(options, "C", "“Charlie”").index)
    }

    @Test
    fun textMatchIgnoresCase() {
        val resolved = QuizAiClient.resolveAnswerIndex(options, "C", "charlie")
        assertNull(resolved.error)
        assertEquals(2, resolved.index)
    }

    @Test
    fun missingLetterIsRejectedInsteadOfUsingTextAlone() {
        val resolved = QuizAiClient.resolveAnswerIndex(options, "", "Charlie")
        assertEquals("answerLetter", resolved.error)
        assertNull(resolved.index)
    }

    @Test
    fun outOfRangeLetterIsRejectedInsteadOfFallingBack() {
        val resolved = QuizAiClient.resolveAnswerIndex(options, "E", "Alpha")
        assertEquals("answerLetter range", resolved.error)
        assertNull(resolved.index)
    }

    @Test
    fun missingTextIsRejectedInsteadOfUsingLetterAlone() {
        val resolved = QuizAiClient.resolveAnswerIndex(options, "C", "")
        assertEquals("answerText", resolved.error)
        assertNull(resolved.index)
    }

    @Test
    fun textMustBeOneOfTheOptions() {
        val resolved = QuizAiClient.resolveAnswerIndex(options, "C", "Echo")
        assertEquals("answerText match", resolved.error)
        assertNull(resolved.index)
    }

    @Test
    fun conflictingLetterAndTextAreRejected() {
        val resolved = QuizAiClient.resolveAnswerIndex(options, "C", "Delta")
        assertEquals("answer mismatch", resolved.error)
        assertNull(resolved.index)
    }

    @Test
    fun solutionKeepsNumberedStepsOnSeparateLines() {
        val raw = "1. 8 = 2 × 4\r\n2. 4 = 2 × 2\n\n\n3. 所以 8 是偶数"
        assertEquals(
            "1. 8 = 2 × 4\n2. 4 = 2 × 2\n\n3. 所以 8 是偶数",
            QuizAiClient.cleanSolution(raw),
        )
    }

    @Test
    fun solutionCollapsesOnlyHorizontalWhitespace() {
        assertEquals(
            "1. Step one\n2. Step two",
            QuizAiClient.cleanSolution("  1.   Step\tone  \n 2. Step   two  "),
        )
    }

    @Test
    fun missingSolutionIsEmpty() {
        assertEquals("", QuizAiClient.cleanSolution(null))
        assertEquals("", QuizAiClient.cleanSolution(42))
    }

    @Test
    fun truncatedResponseKeepsCompleteQuestionObjects() {
        val content = """
            {"questions":[
              {"prompt":"q1","options":["a","b"]},
              {"prompt":"q2","options":["a","b"]},
              {"prompt":"q3","options":["a","b"
        """.trimIndent()
        val questions = QuizAiClient.salvageQuestions(content, 5)!!
        assertEquals(2, questions.length())
        assertEquals("q1", questions.getJSONObject(0).getString("prompt"))
        assertEquals("q2", questions.getJSONObject(1).getString("prompt"))
    }

    @Test
    fun salvageAcceptsTopLevelArrayAndFences() {
        val content = """
            ```json
            [{"prompt":"q1","options":["a"]},{"prompt":"q2","options":["b"]}]
            ```
        """.trimIndent()
        val questions = QuizAiClient.salvageQuestions(content, 5)!!
        assertEquals(2, questions.length())
        assertEquals("q2", questions.getJSONObject(1).getString("prompt"))
    }

    @Test
    fun salvageIgnoresObjectsWithoutQuestionShape() {
        val content = """{"other":{"prompt":"not-a-question"},"questions":[{"prompt":"q","options":[]}"""
        val questions = QuizAiClient.salvageQuestions(content, 5)!!
        assertEquals(1, questions.length())
        assertEquals("q", questions.getJSONObject(0).getString("prompt"))
    }

    @Test
    fun truncatedProviderResponseKeepsCompleteQuestions() {
        val first = questionJson("q1").toString()
        val second = questionJson("q2").toString()
        val content = """{"questions":[$first,$second,{"prompt":"q3","options":["a","b","c","d"]"""
        val parsed = QuizAiClient.parse(envelope(content), request(count = 5))
        assertTrue(parsed is QuizAiClient.Result.Success)
        val success = parsed as QuizAiClient.Result.Success
        assertEquals(2, success.items.size)
        assertEquals("q1", success.items[0].prompt)
        assertTrue(success.warnings.any { it.contains("recovered") })
    }

    @Test
    fun fiftyQuestionResponseIsSupported() {
        val questions = (1..50).joinToString(",") { questionJson("q$it").toString() }
        val content = """{"questions":[$questions]}"""
        val parsed = QuizAiClient.parse(envelope(content), request(count = 50))
        assertTrue(parsed is QuizAiClient.Result.Success)
        assertEquals(50, (parsed as QuizAiClient.Result.Success).items.size)
    }

    private fun request(count: Int): QuizAiClient.Request = QuizAiClient.Request(
        count = count,
        options = 4,
        language = "zh-CN",
        subject = "语文",
        grade = "小学四年级",
        prompt = "第三单元",
        difficulty = "",
        points = 1,
        timeLimitSec = 60,
    )

    private fun envelope(content: String): String = JSONObject()
        .put(
            "choices",
            JSONArray().put(
                JSONObject().put(
                    "message",
                    JSONObject().put("content", content),
                ),
            ),
        )
        .toString()

    private fun questionJson(prompt: String): JSONObject = JSONObject()
        .put("prompt", prompt)
        .put("options", JSONArray().put("a").put("b").put("c").put("d"))
        .put("answerLetter", "A")
        .put("answerText", "a")
        .put("explanation", "1. 正确答案是 a。\n2. 其他选项不符合题意。")
}
