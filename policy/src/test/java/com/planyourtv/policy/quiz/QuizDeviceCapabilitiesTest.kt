package com.planyourtv.policy.quiz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizDeviceCapabilitiesTest {
    @Test
    fun validLocalDocumentIsExecutable() {
        val document = decode("valid-local.json")
        assertTrue(QuizDeviceCapabilities.issues(document, cloudRound = false).isEmpty())
    }

    @Test
    fun cloudDocumentWithFillIsRejected() {
        val codes = codesFor("valid-cloud.json", cloudRound = true)
        assertTrue("questionPolicy.allowedTypes:UNSUPPORTED_FILL" in codes)
    }

    @Test
    fun cloudRoundRejectsMoreThanTenQuestionsButLocalDoesNot() {
        val document = decode("valid-local.json")
        val eleven = document.copy(rules = document.rules.copy(questionCount = 11))
        assertTrue(
            "rules.questionCount:CLOUD_QUESTION_LIMIT_10" in
                QuizDeviceCapabilities.issues(eleven, cloudRound = true)
                    .map { "${it.field}:${it.code}" }
                    .toSet(),
        )
        assertFalse(
            "rules.questionCount:CLOUD_QUESTION_LIMIT_10" in
                QuizDeviceCapabilities.issues(eleven, cloudRound = false)
                    .map { "${it.field}:${it.code}" }
                    .toSet(),
        )
    }

    @Test
    fun timeLimitOverTheDocumentMaximumIsRejected() {
        val document = decode("valid-local.json")
        val longLimit = document.copy(
            rules = document.rules.copy(timeLimitSec = QUIZ_DOC_MAX_TIME_LIMIT_SEC + 1),
        )
        assertTrue(
            "rules.timeLimitSec:TIME_LIMIT_OVER_MAX" in
                QuizDeviceCapabilities.issues(longLimit, cloudRound = false)
                    .map { "${it.field}:${it.code}" }
                    .toSet(),
        )
        // The old 300-second TV ceiling is gone; the web editor already offered 30 minutes.
        val fiveMinutes = document.copy(rules = document.rules.copy(timeLimitSec = 5 * 60))
        assertTrue(
            QuizDeviceCapabilities.issues(fiveMinutes, cloudRound = false).isEmpty(),
        )
    }

    @Test
    fun moreThanTwentyLevelsIsRejected() {
        val codes = codesFor("invalid-levels.json", cloudRound = false)
        assertTrue("levels:LEVEL_LIMIT_20" in codes)
    }

    private fun codesFor(name: String, cloudRound: Boolean): Set<String> =
        QuizDeviceCapabilities.issues(decode(name), cloudRound)
            .map { "${it.field}:${it.code}" }
            .toSet()

    private fun decode(name: String): QuizConfigDocument {
        val raw = checkNotNull(
            javaClass.classLoader?.getResource("quiz-doc/$name"),
        ) { "missing fixture quiz-doc/$name" }.readText(Charsets.UTF_8)
        val result = QuizConfigDocumentCodec.decode(raw)
        return when (result) {
            is QuizDocDecodeResult.Success -> result.document
            is QuizDocDecodeResult.Failure -> error("decode failed for $name: ${result.issues}")
        }
    }
}
