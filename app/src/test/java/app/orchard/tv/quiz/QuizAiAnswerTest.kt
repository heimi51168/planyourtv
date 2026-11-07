package app.orchard.tv.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
