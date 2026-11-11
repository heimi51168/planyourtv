package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizBankImportTest {
    private fun jsonItem(
        prompt: String = "12 + 8 = ?",
        options: JSONArray = JSONArray().put("18").put("20").put("22").put("24"),
        answer: Any? = "B",
        answerIndex: Any? = null,
        explanation: Any? = "12 + 8 = 20",
        difficulty: Any? = "EASY",
    ): JSONObject = JSONObject().apply {
        put("prompt", prompt)
        put("options", options)
        if (answer != null) put("answer", answer)
        if (answerIndex != null) put("answerIndex", answerIndex)
        if (explanation != null) put("explanation", explanation)
        if (difficulty != null) put("difficulty", difficulty)
    }

    private fun errors(result: QuizImportResult): List<QuizImportError> =
        (result as QuizImportResult.Failure).errors

    private fun success(result: QuizImportResult): List<QuizItem> =
        (result as QuizImportResult.Success).items

    @Test
    fun jsonParsesLetterTextAndIndexAnswers() {
        val items = success(
            QuizBankImport.parseJsonItems(
                JSONArray()
                    .put(jsonItem())
                    .put(jsonItem(answer = "20", difficulty = null))
                    .put(jsonItem(answer = null, answerIndex = 2)),
            ),
        )
        assertEquals(3, items.size)
        assertEquals(1, items[0].answerIndex)
        assertEquals(1, items[1].answerIndex)
        assertEquals(2, items[2].answerIndex)
        assertEquals("medium", items[1].difficulty)
        assertEquals("easy", items[0].difficulty)
        assertEquals(60, items[0].timeLimitSec)
        assertTrue(items[0].questionId.matches(Regex("[A-Za-z][A-Za-z0-9_-]{7,63}")))
    }

    @Test
    fun jsonRejectsUnknownFieldsAndAnswerConflicts() {
        val unknown = jsonItem().put("type", "single")
        val unknownErrors = errors(QuizBankImport.parseJsonItems(JSONArray().put(unknown)))
        assertEquals("UNKNOWN_FIELD", unknownErrors.single().code)

        val conflict = jsonItem(answer = "B", answerIndex = 1)
        assertEquals("ANSWER_CONFLICT", errors(QuizBankImport.parseJsonItems(JSONArray().put(conflict))).single().code)
    }

    @Test
    fun jsonRejectsBadAnswersAndOptions() {
        assertEquals(
            "ANSWER_RANGE",
            errors(QuizBankImport.parseJsonItems(JSONArray().put(jsonItem(answer = "F")))).single().code,
        )
        assertEquals(
            "ANSWER_FORMAT",
            errors(QuizBankImport.parseJsonItems(JSONArray().put(jsonItem(answer = "AB")))).single().code,
        )
        assertEquals(
            "COUNT",
            errors(
                QuizBankImport.parseJsonItems(
                    JSONArray().put(jsonItem(options = JSONArray().put("only-one"))),
                ),
            ).single().code,
        )
        assertEquals(
            "DUPLICATE",
            errors(
                QuizBankImport.parseJsonItems(
                    JSONArray().put(jsonItem(options = JSONArray().put("a").put("A"))),
                ),
            ).single().code,
        )
        assertEquals(
            "INVALID_DIFFICULTY",
            errors(QuizBankImport.parseJsonItems(JSONArray().put(jsonItem(difficulty = "EASY!")))).single().code,
        )
    }

    @Test
    fun jsonRejectsMultiAnswerSeparatorsAndUnmatchedText() {
        listOf("AB", "A、B", "A,B", "A 和 B").forEach { answer ->
            assertEquals(
                "ANSWER_FORMAT",
                errors(QuizBankImport.parseJsonItems(JSONArray().put(jsonItem(answer = answer)))).single().code,
            )
        }
        assertEquals(
            "ANSWER_RANGE",
            errors(QuizBankImport.parseJsonItems(JSONArray().put(jsonItem(answer = "不知道")))).single().code,
        )
        assertEquals(
            "ANSWER_RANGE",
            errors(
                QuizBankImport.parseJsonItems(
                    JSONArray().put(jsonItem(answer = null, answerIndex = -1)),
                ),
            ).single().code,
        )
    }

    @Test
    fun jsonRejectsEmptyAndOversizedBatches() {
        assertEquals("NO_ITEMS", errors(QuizBankImport.parseJsonItems(JSONArray())).single().code)
        val tooMany = JSONArray()
        repeat(QuizBankImport.MAX_ITEMS + 1) { tooMany.put(jsonItem()) }
        assertEquals("TOO_MANY_ITEMS", errors(QuizBankImport.parseJsonItems(tooMany)).single().code)
    }

    @Test
    fun textParsesNumberedQuestions() {
        val text = """
            1. 12 + 8 = ?
            A. 18
            B. 20
            C. 22
            D. 24
            答案：B
            解析：12 + 8 = 20

            2. 太阳从哪边升起？
            A. 东
            B. 西
            答案：东
        """.trimIndent()
        val items = success(QuizBankImport.parseText(text, defaultDifficulty = "hard"))
        assertEquals(2, items.size)
        assertEquals("12 + 8 = ?", items[0].prompt)
        assertEquals(1, items[0].answerIndex)
        assertEquals("12 + 8 = 20", items[0].explanation)
        assertEquals("hard", items[0].difficulty)
        assertEquals(0, items[1].answerIndex)
    }

    @Test
    fun textSupportsJudgmentAndReportsTheParagraphNumber() {
        val judgment = """
            1. 地球是圆的。
            A. 正确
            B. 错误
            答案：A
        """.trimIndent()
        assertEquals(2, success(QuizBankImport.parseText(judgment)).single().options.size)

        val broken = """
            1. 缺少答案的题
            A. 对
            B. 错

            2. 正常题
            A. 对
            B. 错
            答案：A
        """.trimIndent()
        val failure = errors(QuizBankImport.parseText(broken))
        assertEquals(0, failure.single().index)
        assertEquals("REQUIRED", failure.single().code)
    }

    @Test
    fun textRejectsMultiAnswerAndDuplicateOptions() {
        listOf("AB", "A、B", "A,B", "A 和 B").forEach { answer ->
            val multi = """
                1. 多选题
                A. 一
                B. 二
                C. 三
                答案：$answer
            """.trimIndent()
            assertFalse(successOrNull(QuizBankImport.parseText(multi)) != null)
            assertEquals("ANSWER_FORMAT", errors(QuizBankImport.parseText(multi)).single().code)
        }

        val duplicate = """
            1. 重复选项
            A. 相同
            B. 相同
            答案：A
        """.trimIndent()
        assertTrue(errors(QuizBankImport.parseText(duplicate)).any { it.code == "DUPLICATE" })
    }

    @Test
    fun textMapsSparseOptionLettersByLabel() {
        val sparse = """
            1. 非连续字母
            A. one
            C. three
            D. four
            答案：C
        """.trimIndent()
        val item = success(QuizBankImport.parseText(sparse)).single()
        assertEquals(1, item.answerIndex)
        assertEquals("three", item.options[item.answerIndex])

        val outOfOrder = """
            1. 乱序字母
            A. one
            C. three
            B. two
            答案：B
        """.trimIndent()
        val outOfOrderItem = success(QuizBankImport.parseText(outOfOrder)).single()
        assertEquals(2, outOfOrderItem.answerIndex)
        assertEquals("two", outOfOrderItem.options[outOfOrderItem.answerIndex])
    }

    @Test
    fun textRejectsMissingLetterAndUnmatchedAnswerText() {
        val missingLetter = """
            1. 缺少字母
            A. one
            C. three
            答案：B
        """.trimIndent()
        assertEquals("ANSWER_RANGE", errors(QuizBankImport.parseText(missingLetter)).single().code)

        val unmatched = """
            1. 无法匹配
            A. one
            B. two
            答案：不知道
        """.trimIndent()
        assertEquals("ANSWER_RANGE", errors(QuizBankImport.parseText(unmatched)).single().code)
    }

    @Test
    fun textRejectsContentWithoutQuestionNumbers() {
        assertEquals(
            "NO_QUESTIONS",
            errors(QuizBankImport.parseText("这是一段没有题号的文字")).single().code,
        )
    }

    private fun successOrNull(result: QuizImportResult): List<QuizItem>? =
        (result as? QuizImportResult.Success)?.items
}
