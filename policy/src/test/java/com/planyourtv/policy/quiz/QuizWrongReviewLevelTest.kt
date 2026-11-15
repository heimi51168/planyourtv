package com.planyourtv.policy.quiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuizWrongReviewLevelTest {
    @Test
    fun createTrimsTheNameAndKeepsAStableId() {
        val created = requireNotNull(
            QuizWrongReviewLevel.create("  错题复习  ", id = "wrong-fixed"),
        )
        assertEquals("wrong-fixed", created.id)
        assertEquals("错题复习", created.name)
        assertEquals("wrong-fixed", QuizWrongReviewLevel.parse(created.encode())?.id)
    }

    @Test
    fun missingNamesAndIdsFallBackSafely() {
        val first = requireNotNull(QuizWrongReviewLevel.create())
        val second = requireNotNull(QuizWrongReviewLevel.create())
        assertEquals("", first.name)
        assertEquals(QuizWrongReviewLevel.ID_PREFIX, first.id.take(QuizWrongReviewLevel.ID_PREFIX.length))
        assertNotEquals(first.id, second.id)

        val parsed = requireNotNull(QuizWrongReviewLevel.parse(JSONObject()))
        assertEquals("", parsed.name)
        assertNotNull(parsed.id)
    }

    @Test
    fun malformedIdsAndLongNamesAreRejected() {
        assertNull(
            QuizWrongReviewLevel.parse(
                JSONObject().put("id", "bad id").put("name", "x"),
            ),
        )
        assertNull(
            QuizWrongReviewLevel.parse(
                JSONObject().put("id", "lvl-fixed").put("name", "x"),
            ),
        )
        assertNull(QuizWrongReviewLevel.create("一二三四五六七八九十一"))
        assertNull(QuizWrongReviewLevel.create("x", id = "wrong-"))
    }

    @Test
    fun encodedRecordToleratesCorruptJson() {
        assertNull(QuizWrongReviewLevel.parseEncoded(null))
        assertNull(QuizWrongReviewLevel.parseEncoded(""))
        assertNull(QuizWrongReviewLevel.parseEncoded("not json"))
        assertNull(QuizWrongReviewLevel.parseEncoded("{\"id\":\"bad id\"}"))
    }
}
