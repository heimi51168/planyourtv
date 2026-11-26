package com.planyourtv.policy.quiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizVisualCodecTest {
    private val digest = "sha256:" + "a".repeat(64)

    private val visual = QuizVisual(
        rich = QuizRichVisual(
            assetId = "qb/figure",
            sha256 = digest,
            width = 960,
            height = 640,
        ),
    )

    private val item = QuizItem(
        questionId = "q_1234567",
        prompt = "如图，平行四边形的面积是多少？",
        options = listOf("12", "16", "20", "24"),
        answerIndex = 1,
        points = 1,
        timeLimitSec = 60,
        recommendedTimeSec = 60,
    )

    @Test
    fun roundTripsRichImageAndKeepsTextWireClean() {
        val textWire = QuizSessionCodec.encodeItem(item)
        assertTrue(!textWire.has("visual"))

        val withVisual = item.copy(visual = visual)
        val encoded = QuizSessionCodec.encodeItem(withVisual)
        val decoded = QuizSessionCodec.decodeItem(encoded.toString())
        assertEquals(withVisual, decoded)
        assertEquals("qb/figure", decoded?.visual?.rich?.assetId)
        assertEquals(digest, decoded?.visual?.rich?.sha256)
    }

    @Test
    fun dropsMalformedVisualWithoutLosingTheTextQuestion() {
        val raw = QuizSessionCodec.encodeItem(item)
            .put(
                "visual",
                JSONObject()
                    .put("schema", 2)
                    .put("mode", "auto")
                    .put("required", true)
                    .put(
                        "rich",
                        JSONObject()
                            .put("assetId", "../escape")
                            .put("sha256", "sha256:not-a-digest")
                            .put("kind", "image")
                            .put("mime", "image/png")
                            .put("width", 960)
                            .put("height", 640),
                    ),
            )
        val decoded = QuizSessionCodec.decodeItem(raw.toString())
        assertEquals(item, decoded)
        assertNull(decoded?.visual)
    }

    @Test
    fun rejectsUnsupportedVisualShapesLeniently() {
        val base = JSONObject()
            .put("schema", 2)
            .put("mode", "auto")
            .put("required", true)
            .put(
                "rich",
                JSONObject()
                    .put("assetId", "qb/figure")
                    .put("sha256", digest)
                    .put("kind", "image")
                    .put("mime", "image/png")
                    .put("width", 960)
                    .put("height", 640),
            )

        assertNull(QuizVisualCodec.parseOrNull(JSONObject(base.toString()).put("schema", 3)))
        assertNull(QuizVisualCodec.parseOrNull(JSONObject(base.toString()).put("required", false)))
        assertNull(
            QuizVisualCodec.parseOrNull(
                JSONObject(base.toString()).put("simple", JSONObject().put("qvd", 1)),
            ),
        )
        assertNull(
            QuizVisualCodec.parseOrNull(
                JSONObject(base.toString()).put("mode", "simple"),
            ),
        )
        assertNull(
            QuizVisualCodec.parseOrNull(
                JSONObject(base.toString())
                    .put("rich", JSONObject(base.getJSONObject("rich").toString()).put("kind", "video")),
            ),
        )
    }

    @Test
    fun validatesAssetIdentityAndImageBounds() {
        assertTrue(QuizVisualCodec.issues(visual).isEmpty())
        assertEquals("qb/figure:$digest", QuizVisualCodec.canonicalKey(visual))
        assertTrue(!QuizVisualCodec.isValidAssetId("../escape"))
        assertTrue(!QuizVisualCodec.isValidAssetId("/absolute"))
        assertTrue(!QuizVisualCodec.isValidAssetId("qb//figure"))
        assertTrue(QuizVisualCodec.isValidAssetId("qb/figure"))

        val tooWide = visual.copy(
            rich = visual.rich!!.copy(width = 4097, height = 1),
        )
        assertTrue(QuizVisualCodec.issues(tooWide).isNotEmpty())
        val tooManyPixels = visual.copy(
            rich = visual.rich!!.copy(width = 4096, height = 4096),
        )
        assertTrue(QuizVisualCodec.issues(tooManyPixels).isNotEmpty())
    }
}