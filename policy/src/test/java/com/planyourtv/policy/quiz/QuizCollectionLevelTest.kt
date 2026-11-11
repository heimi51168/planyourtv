package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuizCollectionLevelTest {
    @Test
    fun parseListRoundTripsAndRejectsDuplicateTags() {
        val levels = listOf(
            QuizCollectionLevel("lvl-a", "成语闯关", "col-a"),
            QuizCollectionLevel("lvl-b", "", "col-b"),
        )
        assertEquals(levels, QuizCollectionLevel.parseList(QuizCollectionLevel.encodeList(levels)))

        val duplicateTag = JSONArray()
            .put(QuizCollectionLevel("lvl-a", "", "col-a").encode())
            .put(QuizCollectionLevel("lvl-b", "", "col-a").encode())
        assertNull(QuizCollectionLevel.parseList(duplicateTag))
    }

    @Test
    fun missingIdsAreAssignedAndMalformedIdsRejected() {
        val parsed = QuizCollectionLevel.parse(
            JSONObject()
                .put("name", "成语闯关")
                .put("collectionId", "col-a"),
        )
        assertNotNull(parsed)
        assertNotNull(parsed!!.id)

        assertNull(
            QuizCollectionLevel.parse(
                JSONObject()
                    .put("id", "bad id")
                    .put("collectionId", "col-a"),
            ),
        )
        assertNull(
            QuizCollectionLevel.parse(
                JSONObject()
                    .put("id", "lvl-a")
                    .put("collectionId", "not-a-collection"),
            ),
        )
    }

    @Test
    fun namesAllowEmptyAndAreCappedAtTenCodePoints() {
        assertEquals("", QuizCollectionLevel.normalizeName("   "))
        assertNull(QuizCollectionLevel.normalizeName("一二三四五六七八九十一"))
    }

    @Test
    fun parseListRejectsDuplicateIds() {
        val array = JSONArray()
            .put(QuizCollectionLevel("lvl-a", "", "col-a").encode())
            .put(QuizCollectionLevel("lvl-a", "", "col-b").encode())
        assertNull(QuizCollectionLevel.parseList(array))
    }
}
