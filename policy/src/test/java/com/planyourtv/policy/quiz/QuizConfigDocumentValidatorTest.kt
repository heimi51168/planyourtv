package com.planyourtv.policy.quiz

import org.junit.Assert.assertTrue
import org.junit.Test

class QuizConfigDocumentValidatorTest {
    @Test
    fun validLocalAndCloudDocumentsPassStructuralValidation() {
        assertTrue(QuizConfigDocumentValidator.validate(decode("valid-local.json")).isEmpty())
        assertTrue(QuizConfigDocumentValidator.validate(decode("valid-cloud.json")).isEmpty())
        assertTrue(QuizConfigDocumentValidator.validate(decode("profiles-full.json")).isEmpty())
        assertTrue(QuizConfigDocumentValidator.validate(decode("empty.json")).isEmpty())
    }

    @Test
    fun reviewScopeAndZeroDailyQuotaAreAccepted() {
        val document = decode("valid-local.json")
        assertTrue(document.levels.any { it.localScope?.scopeType == "REVIEW" })
        assertTrue(document.quota.dailyMaxSeconds == 0)
        assertTrue(QuizConfigDocumentValidator.validate(document).isEmpty())
    }

    @Test
    fun invalidRangesReportTheExactFields() {
        val codes = decode("invalid-ranges.json").let { document ->
            QuizConfigDocumentValidator.validate(document).map { "${it.field}:${it.code}" }.toSet()
        }
        listOf(
            "rules.questionCount:OUT_OF_RANGE",
            "rules.timeLimitSec:OUT_OF_RANGE",
            "rules.feedback.extraHoldSec:OUT_OF_RANGE",
            "rules.feedback.answerRevealSec:OUT_OF_RANGE",
            "rules.subjects:DUPLICATE",
            "rules.legacy.minCorrectCount:OUT_OF_RANGE",
            "reward.profiles.default.correctSeconds.EASY:OUT_OF_RANGE",
            "reward.profiles.default.maxEarnedSeconds:OUT_OF_RANGE",
            "reward.profiles.default.minUnlockSeconds:OUT_OF_RANGE",
            "reward.profiles.weekday.penaltySeconds.EASY:OUT_OF_RANGE",
            "quota.dailyMaxSeconds:OUT_OF_RANGE",
            "calendar.timezone:UNSUPPORTED_TIMEZONE",
            "calendar.weekendDays:EMPTY",
        ).forEach { expected ->
            assertTrue("missing $expected in $codes", expected in codes)
        }
    }

    @Test
    fun invalidLevelsReportLimitDuplicateIdAndDuplicateScope() {
        val codes = decode("invalid-levels.json").let { document ->
            QuizConfigDocumentValidator.validate(document).map { "${it.field}:${it.code}" }.toSet()
        }
        assertTrue("levels:LEVEL_LIMIT_20" in codes)
        assertTrue("levels[2].levelId:DUPLICATE" in codes)
        assertTrue("levels[1].localScope:DUPLICATE" in codes)
    }

    @Test
    fun localWriteRejectsFlatMinutesAndCloudLevels() {
        val local = decode("valid-local.json")
        val flat = local.copy(
            reward = local.reward.copy(mode = QuizRewardMode.FLAT_MINUTES),
        )
        val flatCodes = QuizConfigDocumentValidator.validate(flat, localWrite = true)
            .map { "${it.field}:${it.code}" }
            .toSet()
        assertTrue("reward.mode:LOCAL_REQUIRES_DIFFICULTY_SECONDS" in flatCodes)
        assertTrue("reward.mode:LOCAL_WRITE_FLAT_MINUTES" in flatCodes)

        val cloudLevel = decode("valid-cloud.json").levels.first()
        val mixed = local.copy(levels = listOf(cloudLevel))
        val mixedCodes = QuizConfigDocumentValidator.validate(mixed)
            .map { "${it.field}:${it.code}" }
            .toSet()
        assertTrue("levels[0].origin:CLOUD_LEVEL_IN_LOCAL" in mixedCodes)
    }

    @Test
    fun localWriteRejectsCloudOrigin() {
        val codes = QuizConfigDocumentValidator.validate(decode("valid-cloud.json"), localWrite = true)
            .map { "${it.field}:${it.code}" }
            .toSet()
        assertTrue("origin:LOCAL_DOCUMENT_REQUIRED" in codes)
    }

    @Test
    fun autoNextNeedsTheCapabilityDeclarationButRemainsUnsupportedByTheTv() {
        val document = decode("auto-next.json")
        assertTrue(QuizConfigDocumentValidator.validate(document).isEmpty())
        val codes = QuizDeviceCapabilities.issues(document, cloudRound = false)
            .map { "${it.field}:${it.code}" }
            .toSet()
        assertTrue("rules.feedback.autoNext:UNSUPPORTED_AUTO_NEXT" in codes)
    }

    @Test
    fun topicScopeIsAcceptedForCloudLevelsButRejectedForLocalLevels() {
        val cloud = decode("valid-cloud.json")
        val cloudTopic = cloud.copy(
            levels = listOf(cloud.levels.first().copy(scopeType = "TOPIC")),
        )
        assertTrue(QuizConfigDocumentValidator.validate(cloudTopic).isEmpty())

        val local = decode("valid-local.json")
        val localTopic = local.copy(
            levels = listOf(local.levels.first().copy(scopeType = "TOPIC")),
        )
        val codes = QuizConfigDocumentValidator.validate(localTopic)
            .map { "${it.field}:${it.code}" }
            .toSet()
        assertTrue("levels[0].scopeType:INVALID" in codes)
    }

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
