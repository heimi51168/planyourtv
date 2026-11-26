package app.orchard.tv.quiz

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CloudQuizClientsTest {
    private val revision = "sha256:" + "a".repeat(64)
    private val etag = "sha256:" + "b".repeat(64)

    @Test
    fun rulesResponseCarriesResultAndNormalizedEtag() {
        // interpret 只接收已规范化的 ETag；引号剥离由 normalizeEtag 负责（见下一个用例）。
        val result = CloudQuizRulesClient.interpret(
            200,
            etag,
            JSONObject()
                .put("code", 200)
                .put("result", JSONObject().put("documentId", "quizcfg:family:1:member:2"))
                .toString(),
        )

        assertTrue(result is CloudRulesResult.Ok)
        val ok = result as CloudRulesResult.Ok
        assertEquals(etag, ok.response.etag)
        assertEquals("quizcfg:family:1:member:2", ok.response.document.getString("documentId"))
    }

    @Test
    fun rulesAuthFailuresAreNotRetried() {
        assertTrue(
            CloudQuizRulesClient.interpret(401, null, "{}") is CloudRulesResult.Unauthorized,
        )
        assertTrue(
            CloudQuizRulesClient.interpret(
                200,
                null,
                JSONObject().put("code", 403).put("message", "denied").toString(),
            ) is CloudRulesResult.Unauthorized,
        )
    }

    @Test
    fun rulesServerErrorsAreRetryableAnd304IsNotModified() {
        assertTrue(CloudQuizRulesClient.interpret(503, null, "") is CloudRulesResult.Retryable)
        assertTrue(
            CloudQuizRulesClient.interpret(304, etag, "") is CloudRulesResult.NotModified,
        )
        assertTrue(
            CloudQuizRulesClient.interpret(200, null, "not-json") is CloudRulesResult.Invalid,
        )
        assertTrue(
            CloudQuizRulesClient.interpret(200, null, JSONObject().put("code", 200).toString())
                is CloudRulesResult.Invalid,
        )
    }

    @Test
    fun etagNormalizationRejectsMalformedValues() {
        assertEquals(etag, CloudQuizRulesClient.normalizeEtag("W/\"$etag\""))
        assertEquals(etag, CloudQuizRulesClient.normalizeEtag("\"$etag\""))
        assertNull(CloudQuizRulesClient.normalizeEtag("sha256:short"))
        assertNull(CloudQuizRulesClient.normalizeEtag(""))
    }

    @Test
    fun levelParserAcceptsQcdContractShape() {
        val result = validResult()

        val bundle = QbClient.parseLevelsResult(result, requestedLearnerId = 7)

        assertEquals(7L, bundle.learnerId)
        assertEquals(9L, bundle.memberId)
        assertEquals(revision, bundle.revision)
        assertEquals("CUSTOM", bundle.levelsSource)
        assertEquals(1, bundle.levels.size)
        val level = bundle.levels.single()
        assertEquals(11L, level.id)
        assertEquals("EASY", level.difficulty)
        assertEquals(listOf("SINGLE"), level.questionTypes)
        assertEquals(7, level.availableCount)
    }

    @Test
    fun levelParserAcceptsBuiltInDefaultNegativeIds() {
        // QB 未自定义孩子的默认关卡 id 是负数（DEFAULT_LEVEL_MIXED=-4），不能当非法值拒绝。
        val result = validResult().put("levels", JSONArray().put(levelJson(-4)))

        val bundle = QbClient.parseLevelsResult(result, requestedLearnerId = 7)

        assertEquals(-4L, bundle.levels.single().id)
    }

    @Test
    fun levelParserRejectsMismatchedLearnerAndInvalidRevision() {
        val mismatched = validResult().put(
            "learner",
            JSONObject().put("learnerId", 8).put("memberId", 9),
        )
        assertThrows(IOException::class.java) {
            QbClient.parseLevelsResult(mismatched, requestedLearnerId = 7)
        }

        val badRevision = validResult().put("revision", "sha256:short")
        assertThrows(IOException::class.java) {
            QbClient.parseLevelsResult(badRevision, requestedLearnerId = 7)
        }
    }

    @Test
    fun levelParserRejectsMissingQuestionTypesAndDuplicateIds() {
        val missingTypes = validResult()
        missingTypes.getJSONArray("levels").getJSONObject(0).remove("questionTypes")
        assertThrows(IOException::class.java) {
            QbClient.parseLevelsResult(missingTypes, requestedLearnerId = 7)
        }

        val duplicate = validResult()
            .put(
                "levels",
                JSONArray()
                    .put(levelJson(11))
                    .put(levelJson(11)),
            )
        assertThrows(IOException::class.java) {
            QbClient.parseLevelsResult(duplicate, requestedLearnerId = 7)
        }
    }

    @Test
    fun questionParserAcceptsUlidAndWrongPersonalIds() {
        // 公共/年级题是 ULID；错题/我的题库由 QB 合成 WRONG-<id> / PERSONAL-<id>。
        listOf("01HZXK8W6Q9V3M2N7B4T5R8YCD", "WRONG-123", "PERSONAL-45").forEach { uid ->
            val item = QbClient.parseItem(itemJson(uid))
            assertEquals(QbClient.QUESTION_ID_PREFIX + uid, item.questionId)
        }
    }

    @Test
    fun questionParserRejectsUnknownIdShapes() {
        listOf("", "WRONG-", "PERSONAL-abc", "WRONG-12x", "short", "q-123").forEach { uid ->
            assertThrows(IOException::class.java) {
                QbClient.parseItem(itemJson(uid))
            }
        }
    }

    @Test
    fun imageUrlAcceptsOnlyPublicHttpsPng() {
        assertNull(QbClient.parseImageUrl(itemJson("01HZXK8W6Q9V3M2N7B4T5R8YCD")))
        assertNull(
            QbClient.parseImageUrl(
                itemJson("01HZXK8W6Q9V3M2N7B4T5R8YCD").put("imageUrl", JSONObject.NULL),
            ),
        )
        val url = "https://quiz-assets.oss-cn-hangzhou.aliyuncs.com/qb_figure/abc.png"
        assertEquals(
            url,
            QbClient.parseImageUrl(
                itemJson("01HZXK8W6Q9V3M2N7B4T5R8YCD").put("imageUrl", url),
            ),
        )
        listOf(
            "http://example.com/figure.png",
            "https://user:pass@example.com/figure.png",
            "https://example.com/figure.jpg",
            "https://example.com/",
            "not-a-url",
        ).forEach { bad ->
            assertThrows(IOException::class.java) {
                QbClient.parseImageUrl(
                    itemJson("01HZXK8W6Q9V3M2N7B4T5R8YCD").put("imageUrl", bad),
                )
            }
        }
        assertThrows(IOException::class.java) {
            QbClient.parseImageUrl(
                itemJson("01HZXK8W6Q9V3M2N7B4T5R8YCD").put("imageUrl", 42),
            )
        }
    }

    @Test
    fun levelParserRejectsMoreThanTwentyLevels() {
        val levels = JSONArray()
        (1..21).forEach { levels.put(levelJson(it.toLong())) }
        val result = validResult().put("levels", levels)

        assertThrows(IOException::class.java) {
            QbClient.parseLevelsResult(result, requestedLearnerId = 7)
        }
    }

    private fun validResult(): JSONObject = JSONObject()
        .put("learner", JSONObject().put("learnerId", 7).put("memberId", 9).put("name", "小明"))
        .put("levels", JSONArray().put(levelJson(11)))
        .put("revision", revision)
        .put("levelsSource", "CUSTOM")
        .put("checkin", JSONObject().put("checkedToday", true))

    private fun levelJson(id: Long): JSONObject = JSONObject()
        .put("id", id)
        .put("name", "第一关")
        .put("enabled", true)
        .put("sortNo", 0)
        .put("sources", JSONArray(listOf("BANK")))
        .put("subjects", JSONArray(listOf("MATH")))
        .put("scopeType", "MIDTERM")
        .put("unitNos", JSONArray(listOf(1, 2)))
        .put("unitGroups", JSONArray())
        .put("textbookId", 3)
        .put("grade", 3)
        .put("semester", 1)
        .put("difficulty", "easy")
        .put("advisoryQuestionCount", 5)
        .put("questionTypes", JSONArray(listOf("SINGLE")))
        .put("sourceScopes", JSONObject())
        .put("origin", "CLOUD")
        .put("questionCount", 5)
        .put("available", true)
        .put("availableCount", 7)
        .put("unavailableReason", JSONObject.NULL)

    private fun itemJson(questionId: String): JSONObject = JSONObject()
        .put("questionId", questionId)
        .put("prompt", "1 + 1 = ?")
        .put("options", JSONArray(listOf("1", "2", "3", "4")))
        .put("answerIndex", 1)
        .put("explanation", "1 + 1 = 2")
        .put("subject", "MATH")
        .put("grade", "1")
        .put("difficulty", "easy")
        .put("points", 1)
        .put("timeLimitSec", 60)

    @Test
    fun ttsResultAcceptsAStrictPublicMp3Url() {
        val url = "https://cdn.example.com/qb_tts/abc.mp3"
        val clip = QbClient.parseTtsResult(
            JSONObject()
                .put("part", "stem")
                .put("url", url)
                .put("generated", true),
            expectedPart = "stem",
        )
        assertEquals(url, clip.url)
        assertTrue(clip.generated)

        val uppercase = QbClient.parseTtsResult(
            JSONObject().put("part", "option0").put("url", url.uppercase()),
            expectedPart = "option0",
        )
        assertTrue(uppercase.url.endsWith(".MP3"))
    }

    @Test
    fun ttsResultRejectsPartMismatchAndUnsafeUrls() {
        assertThrows(IOException::class.java) {
            QbClient.parseTtsResult(
                JSONObject()
                    .put("part", "option0")
                    .put("url", "https://example.com/a.mp3"),
                expectedPart = "stem",
            )
        }
        val badUrls = listOf<Any?>(
            null,
            42,
            "",
            "http://example.com/a.mp3",
            "https://user:pass@example.com/a.mp3",
            "https://example.com/a.wav",
            "https://example.com/",
            "not-a-url",
            "https://example.com/" + "a".repeat(2048) + ".mp3",
        )
        badUrls.forEach { bad ->
            assertThrows(IOException::class.java) {
                QbClient.parseTtsResult(
                    JSONObject().put("part", "stem").put("url", bad),
                    expectedPart = "stem",
                )
            }
        }
    }

}
