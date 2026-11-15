package com.planyourtv.policy.quiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizConfigDocumentCodecTest {
    @Test
    fun validLocalRoundTripsAndPreservesUnknownFields() {
        val document = decodeFixture("valid-local.json")
        assertEquals(QuizDocOrigin.LOCAL, document.origin)
        assertEquals(2, document.levels.size)
        assertEquals(0, document.rules.feedback.extraHoldSec)
        assertEquals(30, document.extensions.getJSONObject("local").getInt("legacyAnswerDisplaySec"))

        val encoded = QuizConfigDocumentCodec.encode(document)
        val root = JSONObject(encoded)
        assertEquals(1, root.getJSONObject("x-top-note").getInt("a"))
        assertEquals("preserve me", root.getJSONObject("rules").getString("x-section-note"))
        assertEquals(
            "preserve me too",
            root.getJSONArray("levels").getJSONObject(0).getString("x-level-note"),
        )
        assertTrue(root.getJSONObject("extensions").getJSONObject("cloudUnknown").getBoolean("keep"))

        val roundTripped = decode(encoded)
        assertEquals(document.documentId, roundTripped.documentId)
        assertEquals(document.levels.size, roundTripped.levels.size)
        assertEquals(
            document.rules.feedback.extraHoldSec,
            roundTripped.rules.feedback.extraHoldSec,
        )
        assertEquals(
            document.rules.feedback.answerRevealSec,
            roundTripped.rules.feedback.answerRevealSec,
        )
        assertEquals(document.rules.feedback.autoNext, roundTripped.rules.feedback.autoNext)
        assertEquals(document.quota.dailyMaxSeconds, roundTripped.quota.dailyMaxSeconds)
        assertEquals(document.quota.enforcedBy, roundTripped.quota.enforcedBy)
    }

    @Test
    fun validCloudParsesFillWithoutExecutingIt() {
        val document = decodeFixture("valid-cloud.json")
        assertEquals(QuizDocOrigin.CLOUD, document.origin)
        assertEquals("quizcfg:family:123:member:456", document.documentId)
        assertTrue(QuizQuestionType.FILL in document.questionPolicy.allowedTypes)
        assertTrue(QuizQuestionType.FILL in document.levels[1].questionTypes)
        assertEquals(88L, document.levels[0].textbookId)
        assertTrue(document.levels[0].sourceScopes != null)
        assertTrue(QuizConfigDocumentValidator.validate(document).isEmpty())
    }

    @Test
    fun emptyFixtureIsAValidMinimalDocument() {
        val document = decodeFixture("empty.json")
        assertTrue(document.levels.isEmpty())
        assertTrue(QuizConfigDocumentValidator.validate(document).isEmpty())
    }

    @Test
    fun invalidJsonIsRejected() {
        val result = QuizConfigDocumentCodec.decode("{")
        assertTrue(result is QuizDocDecodeResult.Failure)
        assertTrue(
            (result as QuizDocDecodeResult.Failure).issues.any {
                it.field == "document" && it.code == "INVALID_JSON"
            },
        )
    }

    @Test
    fun unsupportedSchemaVersionIsRejected() {
        val root = JSONObject(fixture("valid-local.json")).put("schemaVersion", 2)
        val result = QuizConfigDocumentCodec.decode(root)
        assertTrue(result is QuizDocDecodeResult.Failure)
        assertTrue(
            (result as QuizDocDecodeResult.Failure).issues.any {
                it.field == "schemaVersion" && it.code == "UNSUPPORTED_SCHEMA"
            },
        )
    }

    @Test
    fun numbersOutsideIntRangeAreRejectedInsteadOfTruncating() {
        val root = JSONObject(fixture("valid-local.json"))
        root.getJSONObject("quota").put("dailyMaxSeconds", 5_000_000_000L)
        val result = QuizConfigDocumentCodec.decode(root)
        assertTrue(result is QuizDocDecodeResult.Failure)
        assertTrue(
            (result as QuizDocDecodeResult.Failure).issues.any {
                it.field == "quota.dailyMaxSeconds" && it.code == "NOT_INTEGER"
            },
        )
    }

    @Test
    fun halfRewardProfileIsRejected() {
        val result = QuizConfigDocumentCodec.decode(fixture("profiles-half.json"))
        assertTrue(result is QuizDocDecodeResult.Failure)
        assertTrue(
            (result as QuizDocDecodeResult.Failure).issues.any {
                it.field == "reward.profiles.weekday.minUnlockSeconds" && it.code == "MISSING"
            },
        )
    }

    private fun decodeFixture(name: String): QuizConfigDocument = decode(fixture(name))

    private fun decode(raw: String): QuizConfigDocument {
        val result = QuizConfigDocumentCodec.decode(raw)
        return when (result) {
            is QuizDocDecodeResult.Success -> result.document
            is QuizDocDecodeResult.Failure -> error("decode failed: ${result.issues}")
        }
    }

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader?.getResource("quiz-doc/$name"),
    ) { "missing fixture quiz-doc/$name" }.readText(Charsets.UTF_8)
}
