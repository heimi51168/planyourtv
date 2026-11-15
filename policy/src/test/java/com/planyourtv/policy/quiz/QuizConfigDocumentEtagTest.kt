package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizConfigDocumentEtagTest {
    @Test
    fun etagIgnoresRevisionTimestampsAndOtherHashes() {
        val document = decode("valid-local.json")
        val before = QuizConfigDocumentEtag.etag(document)
        val changed = document.copy(
            revision = document.revision + 100,
            updatedAt = "2027-01-01T00:00:00+08:00",
            updatedBy = QuizDocUpdatedBy.PARENT_APP,
            etag = "sha256:" + "f".repeat(64),
            levelsEtag = "sha256:" + "e".repeat(64),
        )
        assertEquals(before, QuizConfigDocumentEtag.etag(changed))
        assertTrue(before.startsWith("sha256:"))
        assertEquals("sha256:".length + 64, before.length)
    }

    @Test
    fun etagChangesWhenRulesOrLevelsChange() {
        val document = decode("valid-local.json")
        val before = QuizConfigDocumentEtag.etag(document)
        val rulesChanged = document.copy(
            rules = document.rules.copy(questionCount = document.rules.questionCount + 1),
        )
        assertNotEquals(before, QuizConfigDocumentEtag.etag(rulesChanged))

        val levelsChanged = document.copy(
            levels = listOf(document.levels[0].copy(name = "改名")) + document.levels.drop(1),
        )
        assertNotEquals(before, QuizConfigDocumentEtag.etag(levelsChanged))
    }

    @Test
    fun levelsEtagTracksOnlyTheLevelsArray() {
        val document = decode("valid-local.json")
        val before = QuizConfigDocumentEtag.levelsEtag(document.levels)
        assertTrue(before.startsWith("sha256:"))

        val volatileChanged = document.copy(
            revision = 999,
            updatedAt = "2027-02-02T00:00:00+08:00",
            updatedBy = QuizDocUpdatedBy.MIGRATION,
        )
        assertEquals(before, QuizConfigDocumentEtag.levelsEtag(volatileChanged.levels))

        val levelChanged = document.copy(
            levels = listOf(document.levels[0].copy(enabled = false)) + document.levels.drop(1),
        )
        assertNotEquals(before, QuizConfigDocumentEtag.levelsEtag(levelChanged.levels))
        assertEquals("", QuizConfigDocumentEtag.levelsEtag(emptyList()))
    }

    @Test
    fun publishedVectorsStayStableAcrossImplementations() {
        // These hashes are the cross-implementation contract: any producer (TV, cloud,
        // local web) must get the same value for the same document content.
        val empty = decode("empty.json")
        assertEquals(
            "sha256:1f999992593e1ca32aa675914abaf15ab97a6bc9194fe9df730964de7fcdab23",
            QuizConfigDocumentEtag.etag(empty),
        )
        assertEquals("", QuizConfigDocumentEtag.levelsEtag(empty.levels))

        val local = decode("valid-local.json")
        assertEquals(
            "sha256:a61ae314d0f8ef64043ca9f0c6ab0dd8556c0513e0f17d58782c3460e56b9d58",
            QuizConfigDocumentEtag.etag(local),
        )
        assertEquals(
            "sha256:593b77907d2b3e5379ef9885fa2b69ccb5c7ca24c2890867c86d52eb2c4b5e1b",
            QuizConfigDocumentEtag.levelsEtag(local.levels),
        )
    }

    @Test
    fun canonicalizationSortsObjectKeysAndPreservesArrayOrder() {
        val first = JSONObject().put("b", 2).put("a", 1)
        val second = JSONObject().put("a", 1).put("b", 2)
        assertEquals(
            QuizConfigDocumentEtag.canonicalize(first),
            QuizConfigDocumentEtag.canonicalize(second),
        )
        assertEquals("{\"a\":1,\"b\":2}", QuizConfigDocumentEtag.canonicalize(first))

        val ordered = JSONArray().put(1).put(2)
        val reversed = JSONArray().put(2).put(1)
        assertNotEquals(
            QuizConfigDocumentEtag.canonicalize(ordered),
            QuizConfigDocumentEtag.canonicalize(reversed),
        )
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
