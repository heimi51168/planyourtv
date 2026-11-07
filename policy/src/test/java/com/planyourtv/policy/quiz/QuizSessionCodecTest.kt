package com.planyourtv.policy.quiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSessionCodecTest {
    private val now = 1_760_000_000_000L
    private val item = QuizItem(
        questionId = "q_1234567",
        prompt = "下面哪个是偶数？",
        options = listOf("3", "5", "8", "9"),
        answerIndex = 2,
        explanation = "8 能被 2 整除。",
        subject = "math",
        grade = "g2",
        difficulty = "easy",
        points = 1,
        timeLimitSec = 60,
        recommendedTimeSec = 60,
    )

    private fun session(
        status: QuizSessionStatus = QuizSessionStatus.READY,
        items: List<QuizItem> = listOf(item),
        run: QuizRun? = null,
    ) = QuizSession(
        id = "sess_12345678",
        source = "web",
        createdAt = now,
        updatedAt = now,
        expiresAt = now + 24 * 60 * 60 * 1000L,
        status = status,
        language = "zh",
        config = QuizConfig(minCorrectCount = 1),
        items = items,
        run = run,
    )

    @Test
    fun roundTripsReadySession() {
        val source = session()
        val decoded = QuizSessionCodec.decode(QuizSessionCodec.encode(source))
        assertTrue(decoded is QuizDecodeResult.Success)
        assertEquals(source, (decoded as QuizDecodeResult.Success).session)
    }

    @Test
    fun roundTripsChallengeConfigDefaults() {
        val config = QuizConfig(
            defaultTimeLimitSec = 90,
            minutesPerPoint = 3,
            minCorrectCount = 2,
            maxEarnedMinutes = 45,
            shuffleOptions = false,
            answerDisplaySec = 45,
        )
        assertEquals(config, QuizSessionCodec.decodeConfig(QuizSessionCodec.encodeConfig(config)))
    }

    @Test
    fun defaultsAnswerDisplayTimeForLegacyConfig() {
        val legacy = QuizSessionCodec.encodeConfig(QuizConfig()).apply { remove("answerDisplaySec") }
        assertEquals(
            QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
            QuizSessionCodec.decodeConfig(legacy)?.answerDisplaySec,
        )
        val legacySession = JSONObject(QuizSessionCodec.encode(session()))
            .also { it.getJSONObject("config").remove("answerDisplaySec") }
            .toString()
        val decoded = QuizSessionCodec.decode(legacySession)
        assertTrue(decoded is QuizDecodeResult.Success)
        assertEquals(
            QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
            (decoded as QuizDecodeResult.Success).session.config.answerDisplaySec,
        )
    }

    @Test
    fun rejectsOutOfRangeAnswerDisplayTime() {
        for (value in listOf(QUIZ_MIN_ANSWER_DISPLAY_SEC - 1, QUIZ_MAX_ANSWER_DISPLAY_SEC + 1)) {
            val config = QuizSessionCodec.encodeConfig(QuizConfig()).put("answerDisplaySec", value)
            assertEquals(null, QuizSessionCodec.decodeConfig(config))
        }
    }

    @Test
    fun readsV2SessionAndNormalizesToCurrentSchema() {
        val source = session()
        // A record published before the daily-attempt limit was removed.
        val v2 = JSONObject(QuizSessionCodec.encode(source))
            .put("schemaVersion", 2)
            .also { it.getJSONObject("config").put("maxChallengesPerDay", 1) }
            .toString()
        val decoded = QuizSessionCodec.decode(v2)
        assertTrue(decoded is QuizDecodeResult.Success)
        val success = decoded as QuizDecodeResult.Success
        assertEquals(source.copy(originSchemaVersion = 2), success.session)
        assertEquals(QUIZ_SCHEMA_VERSION, success.session.schemaVersion)
        assertEquals(2, success.session.originSchemaVersion)
    }

    @Test
    fun rejectsOlderSchema() {
        val v1 = JSONObject(QuizSessionCodec.encode(session()))
            .put("schemaVersion", 1)
            .toString()
        assertTrue(QuizSessionCodec.decode(v1) is QuizDecodeResult.Failure)
    }

    @Test
    fun publishValidationRejectsRuntimeState() {
        val run = QuizRun(
            startedAt = now,
            startedDate = "2025-10-09",
            deadlineAt = now + 60_000,
        )
        val errors = QuizSessionValidation.validateForPublish(
            session(status = QuizSessionStatus.RUNNING, run = run),
            now,
        )
        assertTrue(errors.any { it.contains("DRAFT or READY") })
        assertTrue(errors.any { it.contains("must be null") })
    }

    @Test
    fun roundTripsOpenFeedbackPause() {
        val second = item.copy(questionId = "q_7654321", prompt = "下面哪个是奇数？")
        val run = QuizRun(
            startedAt = now,
            startedDate = "2025-10-09",
            index = 1,
            correctCount = 1,
            earnedPoints = 1,
            deadlineAt = now + 60_000,
            feedbackOpen = true,
            feedbackRemainingMs = 45_000,
            submissions = listOf(
                QuizSubmission(item.questionId, 2, true, false, now + 1_000),
            ),
        )
        val source = session(
            status = QuizSessionStatus.RUNNING,
            items = listOf(item, second),
            run = run,
        )
        val decoded = QuizSessionCodec.decode(QuizSessionCodec.encode(source))
        assertTrue(decoded is QuizDecodeResult.Success)
        assertEquals(source, (decoded as QuizDecodeResult.Success).session)
    }

    @Test
    fun defaultsLegacyFeedbackPauseFields() {
        val run = QuizRun(
            startedAt = now,
            startedDate = "2025-10-09",
            index = 1,
            correctCount = 1,
            earnedPoints = 1,
            deadlineAt = now + 60_000,
            submissions = listOf(
                QuizSubmission(item.questionId, 2, true, false, now + 1_000),
            ),
        )
        val second = item.copy(questionId = "q_7654321", prompt = "下面哪个是奇数？")
        val encoded = JSONObject(
            QuizSessionCodec.encode(
                session(
                    status = QuizSessionStatus.RUNNING,
                    items = listOf(item, second),
                    run = run,
                ),
            ),
        ).also {
            it.getJSONObject("run").remove("feedbackOpen")
            it.getJSONObject("run").remove("feedbackRemainingMs")
        }.toString()
        val decoded = QuizSessionCodec.decode(encoded)
        assertTrue(decoded is QuizDecodeResult.Success)
        val restored = (decoded as QuizDecodeResult.Success).session.run
        assertEquals(false, restored?.feedbackOpen)
        assertEquals(0L, restored?.feedbackRemainingMs)
    }

    @Test
    fun rejectsOutOfRangeAnswerAndDuplicateOptions() {
        val invalid = item.copy(answerIndex = 9, options = listOf("A", "A", "B", "C"))
        val decoded = QuizSessionCodec.decode(QuizSessionCodec.encode(session(items = listOf(invalid))))
        assertTrue(decoded is QuizDecodeResult.Failure)
        val errors = (decoded as QuizDecodeResult.Failure).errors
        assertTrue(errors.any { it.contains("answerIndex") })
        assertTrue(errors.any { it.contains("duplicate") })
    }

    @Test
    fun acceptsTheSemesterGradeLabelsPublishedByThePhone() {
        val source = session(items = listOf(item.copy(grade = "Grade 4 · Semester 2")))
        val decoded = QuizSessionCodec.decode(QuizSessionCodec.encode(source))
        assertTrue(decoded is QuizDecodeResult.Success)
        assertEquals(source, (decoded as QuizDecodeResult.Success).session)
    }

    @Test
    fun roundTripsMultilineSolution() {
        val solution = "1. 先看个位\n2. 再除以 2\n3. 结论：8 是偶数"
        val source = session(items = listOf(item.copy(explanation = solution)))
        val decoded = QuizSessionCodec.decode(QuizSessionCodec.encode(source))
        assertTrue(decoded is QuizDecodeResult.Success)
        assertEquals(source, (decoded as QuizDecodeResult.Success).session)
    }

    @Test
    fun acceptsSolutionAtTheCapAndRejectsLongerText() {
        val atCap = session(
            items = listOf(item.copy(explanation = "x".repeat(QUIZ_MAX_EXPLANATION_CHARS))),
        )
        assertTrue(QuizSessionCodec.decode(QuizSessionCodec.encode(atCap)) is QuizDecodeResult.Success)

        val overCap = session(
            items = listOf(item.copy(explanation = "x".repeat(QUIZ_MAX_EXPLANATION_CHARS + 1))),
        )
        val decoded = QuizSessionCodec.decode(QuizSessionCodec.encode(overCap))
        assertTrue(decoded is QuizDecodeResult.Failure)
        assertTrue((decoded as QuizDecodeResult.Failure).errors.any { it.contains("explanation") })
    }

    @Test
    fun rejectsShortAndLongLifetimes() {
        val tooShort = session().copy(expiresAt = now + 30 * 60 * 1000L)
        val tooLong = session().copy(expiresAt = now + 8 * 24 * 60 * 60 * 1000L)
        assertTrue(QuizSessionValidation.validateForPublish(tooShort, now).any { it.contains("lifetime") })
        assertTrue(QuizSessionValidation.validateForPublish(tooLong, now).any { it.contains("lifetime") })
    }
    @Test
    fun roundTripsV4Config() {
        val config = QuizConfig(
            defaultTimeLimitSec = 90,
            minutesPerPoint = 3,
            minCorrectCount = 2,
            maxEarnedMinutes = 45,
            shuffleOptions = false,
            answerDisplaySec = 45,
            pullCount = 7,
            minutesPerQuestion = 4,
            pointsPerQuestion = 1,
        )
        assertEquals(config, QuizSessionCodec.decodeConfig(QuizSessionCodec.encodeConfig(config)))
    }

    @Test
    fun roundTripsV4ItemSourceMetadata() {
        val cached = item.copy(
            cacheId = "cache-1",
            sourceKind = "CLOUD",
            sourceRef = "qb:attempt:123",
        )
        val source = session(items = listOf(cached))
        val decoded = QuizSessionCodec.decode(QuizSessionCodec.encode(source))
        assertTrue(decoded is QuizDecodeResult.Success)
        val restored = (decoded as QuizDecodeResult.Success).session
        assertEquals(QUIZ_SCHEMA_VERSION, restored.originSchemaVersion)
        assertEquals(cached, restored.items.single())
    }

    @Test
    fun defaultsV4ConfigForOlderConfigJson() {
        val legacy = QuizSessionCodec.encodeConfig(QuizConfig()).apply {
            remove("pullCount")
            remove("minutesPerQuestion")
            remove("pointsPerQuestion")
        }
        val decoded = QuizSessionCodec.decodeConfig(legacy) ?: error("config should decode")
        assertEquals(QUIZ_DEFAULT_PULL_COUNT, decoded.pullCount)
        assertEquals(decoded.minutesPerPoint, decoded.minutesPerQuestion)
        assertEquals(1, decoded.pointsPerQuestion)
    }

    @Test
    fun rejectsOutOfRangeV4Config() {
        val base = QuizSessionCodec.encodeConfig(QuizConfig())
        for ((field, value) in listOf(
            "pullCount" to QUIZ_MIN_PULL_COUNT - 1,
            "pullCount" to QUIZ_MAX_PULL_COUNT + 1,
            "minutesPerQuestion" to 0,
            "minutesPerQuestion" to 31,
        )) {
            val config = JSONObject(base.toString()).put(field, value)
            assertEquals(null, QuizSessionCodec.decodeConfig(config))
        }
        val points = JSONObject(base.toString()).put("pointsPerQuestion", 2)
        assertEquals(null, QuizSessionCodec.decodeConfig(points))
    }

    @Test
    fun engineConfigMapsOnlyEngineFields() {
        val config = QuizConfig(
            defaultTimeLimitSec = 90,
            maxEarnedMinutes = 45,
            answerDisplaySec = 25,
            shuffleOptions = false,
            pullCount = 6,
            minutesPerQuestion = 4,
        )
        val engine = QuizEngineConfig.from(config)
        assertEquals(6, engine.pullCount)
        assertEquals(90, engine.defaultTimeLimitSec)
        assertEquals(25, engine.answerDisplaySec)
        assertEquals(4, engine.minutesPerQuestion)
        assertEquals(false, engine.shuffleOptions)
        val restored = engine.toQuizConfig(legacy = config)
        assertEquals(4, restored.minutesPerQuestion)
        assertEquals(4, restored.minutesPerPoint)
        assertEquals(1, restored.pointsPerQuestion)
        assertEquals(45, restored.maxEarnedMinutes)
    }

    @Test
    fun sourceConfigRoundTripsAndRejectsUnknownSource() {
        val config = QuizSourceConfig(
            enabled = false,
            source = QuizSource.CLOUD,
            configVersion = 7,
            localVersion = 3,
            updatedAt = now,
        )
        assertEquals(config, QuizSourceConfig.decode(config.encode().toString()))
        assertNull(QuizSourceConfig.decode(null))
        val unknown = JSONObject().put("source", "remote")
        assertEquals(null, QuizSourceConfig.decode(unknown.toString()))
    }

}
