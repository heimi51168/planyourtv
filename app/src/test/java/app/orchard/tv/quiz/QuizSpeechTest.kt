package app.orchard.tv.quiz

import com.planyourtv.policy.quiz.QuizItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizSpeechTest {
    private val item = QuizItem(
        questionId = "q1",
        prompt = "2+2=?",
        options = listOf("3", "4", "5"),
        answerIndex = 1,
    )

    @Test
    fun defaultIsOnButInactiveUntilSaved() {
        val settings = QuizSpeechSettings()
        assertTrue(settings.enabled)
        assertTrue(settings.readPrompt)
        assertTrue(settings.readOptions)
        assertTrue(settings.readAnswer)
        assertFalse(settings.active)
    }

    @Test
    fun activeRequiresSavedEnabledAndAtLeastOnePart() {
        assertTrue((QuizSpeechSettings(initialized = true)).active)
        assertFalse(QuizSpeechSettings(initialized = true, enabled = false).active)
        assertFalse(
            QuizSpeechSettings(
                initialized = true,
                readPrompt = false,
                readOptions = false,
                readAnswer = false,
            ).active,
        )
    }

    @Test
    fun jsonRoundTripKeepsParentChoices() {
        val saved = QuizSpeechSettings(
            initialized = true,
            enabled = false,
            readPrompt = true,
            readOptions = false,
            readAnswer = true,
        )
        assertEquals(saved, QuizSpeechSettings.fromJson(saved.toJson()))
    }

    @Test
    fun missingRecordKeepsTheMigrationGateClosed() {
        val settings = QuizSpeechSettings.fromJson(JSONObject())
        assertFalse(settings.initialized)
        assertFalse(settings.active)
    }

    @Test
    fun questionReadingIncludesPromptAndOptionsButNeverTheAnswer() {
        val text = QuizSpeechText.question(item, QuizSpeechSettings(initialized = true))
        assertEquals("题目：2加2等于?\n选项A，3；选项B，4；选项C，5。", text)
        assertFalse(text!!.contains("正确答案"))
        // The correct option appears only as one of the choices, with no marker.
        assertFalse(text.contains("答案"))
    }

    @Test
    fun questionReadingHonoursEachContentChoice() {
        val promptOnly = QuizSpeechText.question(
            item,
            QuizSpeechSettings(initialized = true, readOptions = false),
        )
        assertEquals("题目：2加2等于?", promptOnly)

        val optionsOnly = QuizSpeechText.question(
            item,
            QuizSpeechSettings(initialized = true, readPrompt = false),
        )
        assertEquals("选项A，3；选项B，4；选项C，5。", optionsOnly)

        assertNull(
            QuizSpeechText.question(
                item,
                QuizSpeechSettings(
                    initialized = true,
                    readPrompt = false,
                    readOptions = false,
                ),
            ),
        )
    }

    @Test
    fun chineseMathPromptSpellsOperatorsSoTheVoiceStaysChinese() {
        val math = item.copy(
            prompt = "已知M÷N=40，那么(M×3)÷(N×6)= ( )。",
            options = listOf("20", "40", "60", "80"),
            answerIndex = 0,
        )
        assertEquals(
            "题目：已知M除以N等于40，那么(M乘3)除以(N乘6)等于 ( )。\n" +
                "选项A，20；选项B，40；选项C，60；选项D，80。",
            QuizSpeechText.question(math, QuizSpeechSettings(initialized = true)),
        )
        assertEquals(
            "正确答案是选项A，20。",
            QuizSpeechText.answer(math, QuizSpeechSettings(initialized = true)),
        )
    }

    @Test
    fun chineseMathKeepsArithmeticAndDatesReadable() {
        fun promptOnly(prompt: String): String? = QuizSpeechText.question(
            item.copy(prompt = prompt, options = emptyList()),
            QuizSpeechSettings(initialized = true, readOptions = false),
        )

        assertEquals("题目：5乘3减4等于?", promptOnly("5×3-4=?"))
        assertEquals("题目：2026年11月17日是星期几？", promptOnly("2026-11-17是星期几？"))
        assertEquals("题目：增长率是百分之50，怎么算？", promptOnly("增长率是50%，怎么算？"))
    }

    @Test
    fun englishQuestionsUseEnglishFramesAndKeepTheQuestionLanguage() {
        val english = item.copy(
            prompt = "What is 2+2?",
            options = listOf("3", "4", "5"),
            answerIndex = 1,
        )
        assertEquals(
            "Question: What is 2+2?\nOptions: A, 3; B, 4; C, 5.",
            QuizSpeechText.question(english, QuizSpeechSettings(initialized = true)),
        )
        assertEquals(
            "The correct answer is B, 4.",
            QuizSpeechText.answer(english, QuizSpeechSettings(initialized = true)),
        )
    }

    @Test
    fun fillInBlanksAreSpokenAsWordsInsteadOfUnderscores() {
        val blank = item.copy(
            prompt = "she ___ to ___ school",
            options = listOf("go", "goes", "went"),
        )
        val text = QuizSpeechText.question(blank, QuizSpeechSettings(initialized = true))
        assertEquals(
            "Question: she blank to blank school\nOptions: A, go; B, goes; C, went.",
            text,
        )
        assertFalse(text!!.contains("_"))
    }

    @Test
    fun blankNormalisationCoversFullWidthAndSpacedUnderscores() {
        fun promptOnly(prompt: String): String? = QuizSpeechText.question(
            item.copy(prompt = prompt, options = emptyList()),
            QuizSpeechSettings(initialized = true, readOptions = false),
        )

        assertEquals("题目：她用空格去了空格。", promptOnly("她用＿＿去了＿＿。"))
        assertEquals("题目：她空格去学校", promptOnly("她_去学校"))
        assertEquals("Question: fill blank here", promptOnly("fill _ _ _ here"))
        assertEquals("Question: fill blank here", promptOnly("fill _ here"))
    }

    @Test
    fun underscoresInsideWordsAreNotTreatedAsBlanks() {
        fun promptOnly(prompt: String): String? = QuizSpeechText.question(
            item.copy(prompt = prompt, options = emptyList()),
            QuizSpeechSettings(initialized = true, readOptions = false),
        )

        assertEquals("Question: my_var and _private", promptOnly("my_var and _private"))
        assertEquals("Question: my__var and __init__", promptOnly("my__var and __init__"))
    }

    @Test
    fun optionsAndAnswersWithBlanksAreSpokenAsWordsToo() {
        val blankOption = item.copy(
            prompt = "she ___ to school",
            options = listOf("___", "goes", "went"),
            answerIndex = 0,
        )
        assertEquals(
            "Options: A, blank; B, goes; C, went.",
            QuizSpeechText.question(
                blankOption,
                QuizSpeechSettings(initialized = true, readPrompt = false),
            ),
        )
        assertEquals(
            "The correct answer is A, blank.",
            QuizSpeechText.answer(blankOption, QuizSpeechSettings(initialized = true)),
        )
    }

    @Test
    fun answerReadingUsesTheDisplayedLabelAndHonoursTheChoice() {
        assertEquals(
            "正确答案是选项B，4。",
            QuizSpeechText.answer(item, QuizSpeechSettings(initialized = true)),
        )
        assertNull(
            QuizSpeechText.answer(
                item,
                QuizSpeechSettings(initialized = true, readAnswer = false),
            ),
        )

        val multiple = item.copy(
            options = listOf("3", "4", "5", "6"),
            answerKind = com.planyourtv.policy.quiz.QuizAnswerKind.MULTIPLE,
            answerIndices = listOf(1, 3),
            answerIndex = 1,
        )
        assertEquals(
            "正确答案是选项B，4；选项D，6。",
            QuizSpeechText.answer(multiple, QuizSpeechSettings(initialized = true)),
        )
    }

    @Test
    fun answerReadingIncludesTheExplanationInTheSameUtterance() {
        val withExplanation = item.copy(explanation = "先算括号里的加法，再除以6。")
        assertEquals(
            "正确答案是选项B，4。\n解题过程：先算括号里的加法，再除以6。",
            QuizSpeechText.answer(withExplanation, QuizSpeechSettings(initialized = true)),
        )

        val english = item.copy(
            prompt = "What is 2+2?",
            explanation = "Add two and two to get four.",
        )
        assertEquals(
            "The correct answer is B, 4.\nExplanation: Add two and two to get four.",
            QuizSpeechText.answer(english, QuizSpeechSettings(initialized = true)),
        )

        assertNull(
            QuizSpeechText.answer(
                withExplanation,
                QuizSpeechSettings(initialized = true, readAnswer = false),
            ),
        )
    }

    @Test
    fun explanationLanguageAndMathAreNormalisedIndependentlyFromThePrompt() {
        val mixed = item.copy(
            prompt = "What is 2+2?",
            explanation = "2×2=4，所以答案是4。",
        )
        assertEquals(
            "The correct answer is B, 4.\n解题过程：2乘2等于4，所以答案是4。",
            QuizSpeechText.answer(mixed, QuizSpeechSettings(initialized = true)),
        )
    }

    @Test
    fun speechRequestUsesItsOwnParametersAndNoChatModel() {
        val body = JSONObject(DeepSeekTtsClient.requestBody("你好", "stella"))
        assertEquals("你好", body.getString("text"))
        assertEquals("stella", body.getString("voice_id"))
        assertEquals("pcm", body.getString("format"))
        assertFalse(body.has("model"))
    }

    @Test
    fun officialDeepSeekOnlyAcceptsTheRealHostAndSpeechPath() {
        assertTrue(QuizProviderMeta.isOfficialDeepSeek("https://api.deepseek.com"))
        assertTrue(QuizProviderMeta.isOfficialDeepSeek("https://api.deepseek.com/v1"))
        assertTrue(QuizProviderMeta.isOfficialDeepSeek("https://api.deepseek.com/v1/"))

        assertFalse(QuizProviderMeta.isOfficialDeepSeek("https://api.deepseek.com.evil.test"))
        assertFalse(QuizProviderMeta.isOfficialDeepSeek("https://proxy.example.com/v1"))
        assertFalse(QuizProviderMeta.isOfficialDeepSeek("https://api.deepseek.com.proxy/v1"))
        assertFalse(QuizProviderMeta.isOfficialDeepSeek("http://api.deepseek.com/v1"))
        assertFalse(QuizProviderMeta.isOfficialDeepSeek("https://api.deepseek.com/v1?x=1"))
        assertFalse(QuizProviderMeta.isOfficialDeepSeek("https://api.deepseek.com/chat"))
        assertFalse(QuizProviderMeta.isOfficialDeepSeek(""))
    }

    @Test
    fun cloudSpeechDoesNotRequireTheLocalConsentRecord() {
        assertTrue(QuizSpeechSettings().cloudActive)
        assertFalse(QuizSpeechSettings(enabled = false).cloudActive)
        assertFalse(
            QuizSpeechSettings(
                readPrompt = false,
                readOptions = false,
                readAnswer = false,
            ).cloudActive,
        )
        assertTrue(
            QuizSpeechSettings(
                initialized = false,
                enabled = true,
                readPrompt = true,
                readOptions = false,
                readAnswer = false,
            ).cloudActive,
        )
    }

    @Test
    fun cloudQuestionPartsFollowTheParentChoices() {
        val all = QuizCloudSpeechParts.question(item, QuizSpeechSettings())
        assertEquals(listOf("stem", "option0", "option1", "option2"), all.map { it.name })
        assertEquals("2+2=?", all[0].cacheText)
        assertEquals("4", all[2].cacheText)

        val promptOnly = QuizCloudSpeechParts.question(
            item,
            QuizSpeechSettings(readOptions = false),
        )
        assertEquals(listOf("stem"), promptOnly.map { it.name })

        assertTrue(
            QuizCloudSpeechParts.question(
                item,
                QuizSpeechSettings(enabled = false),
            ).isEmpty(),
        )
    }

    @Test
    fun cloudOptionsRequestThePublishedSlotBehindEachDisplayPosition() {
        val shuffled = item.copy(
            options = listOf("5", "3", "4"),
            optionOrder = listOf(2, 0, 1),
            answerIndex = 1,
        )
        val parts = QuizCloudSpeechParts.question(shuffled, QuizSpeechSettings())
        assertEquals(
            listOf("stem", "option2", "option0", "option1"),
            parts.map { it.name },
        )
        assertEquals(listOf("2+2=?", "5", "3", "4"), parts.map { it.cacheText })
    }

    @Test
    fun cloudAnswerPartsUseTheAnswerTextAndSkipMissingExplanation() {
        val withExplanation = item.copy(explanation = "  two plus two is four  ")
        val parts = QuizCloudSpeechParts.answer(withExplanation, QuizSpeechSettings())
        assertEquals(listOf("answer", "explanation"), parts.map { it.name })
        assertEquals("4", parts[0].cacheText)
        assertEquals("two plus two is four", parts[1].cacheText)

        val noExplanation = QuizCloudSpeechParts.answer(item, QuizSpeechSettings())
        assertEquals(listOf("answer"), noExplanation.map { it.name })

        assertTrue(
            QuizCloudSpeechParts.answer(
                withExplanation,
                QuizSpeechSettings(readAnswer = false),
            ).isEmpty(),
        )
        assertTrue(
            QuizCloudSpeechParts.answer(
                item.copy(answerIndex = 9),
                QuizSpeechSettings(),
            ).isEmpty(),
        )
    }

}
