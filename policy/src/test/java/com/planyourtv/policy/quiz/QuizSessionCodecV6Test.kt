package com.planyourtv.policy.quiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSessionCodecV6Test {
    private val now = 1_760_000_000_000L
    private val shaA = "sha256:" + "a".repeat(64)
    private val shaB = "sha256:" + "b".repeat(64)
    private val item = QuizItem(
        questionId = "q_1234567",
        prompt = "下面哪个是偶数？",
        options = listOf("3", "5", "8", "9"),
        answerIndex = 2,
        explanation = "8 能被 2 整除。",
        timeLimitSec = 60,
        recommendedTimeSec = 60,
    )
    private val profile = QuizRewardProfile(
        correctSeconds = QuizDifficultySeconds(easy = 60, medium = 120, hard = 300),
        penaltySeconds = QuizDifficultySeconds(easy = 0, medium = 30, hard = 60),
        maxEarnedSeconds = 3600,
        minUnlockSeconds = 600,
    )

    private fun session(
        status: QuizSessionStatus = QuizSessionStatus.READY,
        run: QuizRun? = null,
        config: QuizConfig = QuizConfig(minCorrectCount = 1),
        originSchemaVersion: Int = QUIZ_SCHEMA_VERSION,
        docRevision: Long = 0L,
        docEtag: String = "",
        levelsEtag: String = "",
        dayType: String = "",
        rewardProfile: QuizRewardProfile? = null,
    ) = QuizSession(
        originSchemaVersion = originSchemaVersion,
        id = "sess_12345678",
        createdAt = now,
        updatedAt = now,
        expiresAt = now + 24 * 60 * 60 * 1000L,
        status = status,
        config = config,
        docRevision = docRevision,
        docEtag = docEtag,
        levelsEtag = levelsEtag,
        dayType = dayType,
        rewardProfile = rewardProfile,
        items = listOf(item),
        run = run,
    )

    @Test
    fun roundTripsV6FeedbackAndFrozenDocumentIdentity() {
        val run = QuizRun(
            startedAt = now,
            startedDate = "2025-10-09",
            index = 1,
            correctCount = 0,
            earnedSeconds = 120,
            deadlineAt = now + 60_000,
            submissions = listOf(
                QuizSubmission(
                    questionId = item.questionId,
                    selectedIndex = 1,
                    correct = false,
                    timedOut = false,
                    answeredAt = now + 1_000,
                    feedbackHoldMs = QuizVoiceHold.WRONG_HOLD_MS,
                ),
            ),
        )
        val source = session(
            status = QuizSessionStatus.RUNNING,
            run = run,
            config = QuizConfig(minCorrectCount = 1, feedbackExtraHoldSec = 5),
            docRevision = 7,
            docEtag = shaA,
            levelsEtag = shaB,
            dayType = QuizDayType.WEEKEND.name,
            rewardProfile = profile,
        )
        val encoded = QuizSessionCodec.encode(source)
        val decoded = QuizSessionCodec.decode(encoded)
        assertTrue(decoded is QuizDecodeResult.Success)
        // JSONObject-backed "extra" maps compare by identity, so round-trip the
        // canonical wire shape instead of the data-class equality.
        assertEquals(encoded, QuizSessionCodec.encode((decoded as QuizDecodeResult.Success).session))
        assertEquals(5, decoded.session.config.feedbackExtraHoldSec)
        assertEquals(7L, decoded.session.docRevision)
        assertEquals(QuizDayType.WEEKEND.name, decoded.session.dayType)
        assertEquals(120, decoded.session.run?.earnedSeconds)
        assertEquals(
            QuizVoiceHold.WRONG_HOLD_MS,
            decoded.session.run?.submissions?.single()?.feedbackHoldMs,
        )
    }

    @Test
    fun roundTripsFeedbackExtraHold() {
        val config = QuizConfig(minCorrectCount = 1, feedbackExtraHoldSec = QUIZ_DOC_MAX_EXTRA_HOLD_SEC)
        assertEquals(config, QuizSessionCodec.decodeConfig(QuizSessionCodec.encodeConfig(config)))
    }

    @Test
    fun legacyConfigOmitsTheV6OnlyFeedbackField() {
        val wire = QuizSessionCodec.encodeLegacyConfig(QuizConfig(feedbackExtraHoldSec = 9))
        assertFalse(wire.has("feedbackExtraHoldSec"))
        assertEquals(QUIZ_DEFAULT_EXTRA_HOLD_SEC, QuizSessionCodec.decodeConfig(wire)?.feedbackExtraHoldSec)
    }

    @Test
    fun readsV5RecordWithV6Defaults() {
        val v5 = JSONObject(
            QuizSessionCodec.encode(
                session(
                    status = QuizSessionStatus.RUNNING,
                    run = QuizRun(
                        startedAt = now,
                        startedDate = "2025-10-09",
                        deadlineAt = now + 60_000,
                        submissions = listOf(
                            QuizSubmission(item.questionId, 1, false, false, now + 1_000),
                        ),
                    ),
                    config = QuizConfig(minCorrectCount = 1, answerDisplaySec = 30),
                ),
            ),
        ).apply {
            put("schemaVersion", 5)
            put("originSchemaVersion", 5)
            remove("docRevision")
            remove("docEtag")
            remove("levelsEtag")
            remove("dayType")
            remove("rewardProfile")
            getJSONObject("config").remove("feedbackExtraHoldSec")
            getJSONObject("run").remove("earnedSeconds")
            getJSONObject("run").getJSONArray("submissions")
                .getJSONObject(0)
                .remove("feedbackHoldMs")
        }.toString()
        val decoded = QuizSessionCodec.decode(v5)
        assertTrue(decoded is QuizDecodeResult.Success)
        val restored = (decoded as QuizDecodeResult.Success).session
        assertEquals(5, restored.originSchemaVersion)
        assertEquals(0L, restored.docRevision)
        assertEquals("", restored.docEtag)
        assertEquals("", restored.dayType)
        assertNull(restored.rewardProfile)
        assertEquals(QUIZ_DEFAULT_EXTRA_HOLD_SEC, restored.config.feedbackExtraHoldSec)
        assertEquals(0, restored.run?.earnedSeconds)
        assertNull(restored.run?.submissions?.single()?.feedbackHoldMs)
        // A v5 answer keeps its historical answerDisplaySec hold.
        assertEquals(30_000L, QuizFeedbackHold.effectiveHoldMs(restored.run!!.submissions.single(), 30))
    }

    @Test
    fun rejectsOutOfRangeV6Values() {
        val baseConfig = QuizSessionCodec.encodeConfig(QuizConfig())
        assertNull(
            QuizSessionCodec.decodeConfig(
                JSONObject(baseConfig.toString()).put(
                    "feedbackExtraHoldSec",
                    QUIZ_DOC_MAX_EXTRA_HOLD_SEC + 1,
                ),
            ),
        )
        assertNull(
            QuizSessionCodec.decodeConfig(JSONObject(baseConfig.toString()).put("feedbackExtraHoldSec", -1)),
        )

        val run = QuizRun(
            startedAt = now,
            startedDate = "2025-10-09",
            deadlineAt = now + 60_000,
            submissions = listOf(
                QuizSubmission(item.questionId, 1, false, false, now + 1_000, 1L),
            ),
        )
        val base = QuizSessionCodec.encode(
            session(status = QuizSessionStatus.RUNNING, run = run),
        )
        for (value in listOf(-1L, QUIZ_MAX_FEEDBACK_HOLD_MS + 1)) {
            val raw = JSONObject(base).apply {
                getJSONObject("run").getJSONArray("submissions")
                    .getJSONObject(0)
                    .put("feedbackHoldMs", value)
            }.toString()
            assertTrue("holdMs=$value", QuizSessionCodec.decode(raw) is QuizDecodeResult.Failure)
        }
        val tooMuchTime = JSONObject(base).apply {
            getJSONObject("run").put("earnedSeconds", QUIZ_DOC_MAX_MAX_EARNED_SECONDS + 1)
        }.toString()
        assertTrue(QuizSessionCodec.decode(tooMuchTime) is QuizDecodeResult.Failure)
    }

    @Test
    fun rejectsInvalidDocumentIdentityAndRewardProfile() {
        val run = QuizRun(startedAt = now, startedDate = "2025-10-09", deadlineAt = now + 60_000)
        val base = QuizSessionCodec.encode(session(status = QuizSessionStatus.RUNNING, run = run))

        val badEtag = JSONObject(base).put("docEtag", "sha256:xyz").toString()
        assertTrue(QuizSessionCodec.decode(badEtag) is QuizDecodeResult.Failure)

        val badDay = JSONObject(base).put("dayType", "HOLIDAY").toString()
        assertTrue(QuizSessionCodec.decode(badDay) is QuizDecodeResult.Failure)

        val badProfile = JSONObject(base).put(
            "rewardProfile",
            JSONObject()
                .put("correctSeconds", JSONObject().put("easy", 1).put("medium", 1).put("hard", 1801))
                .put("penaltySeconds", JSONObject().put("easy", 0).put("medium", 0).put("hard", 0))
                .put("maxEarnedSeconds", 59)
                .put("minUnlockSeconds", 0),
        ).toString()
        val decoded = QuizSessionCodec.decode(badProfile)
        assertTrue(decoded is QuizDecodeResult.Failure)
        val errors = (decoded as QuizDecodeResult.Failure).errors
        assertTrue(errors.any { it.contains("rewardProfile") })
    }
}
