package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuizCollectionTest {
    @Test
    fun namesAreTrimmedAndBoundedByCodePoints() {
        assertEquals("成语专项", QuizCollection.normalizeName("  成语专项  "))
        assertEquals("一二三四五六七八九十一二三四五六七八九十", QuizCollection.normalizeName("一二三四五六七八九十一二三四五六七八九十"))
        assertNull(QuizCollection.normalizeName("   "))
        assertNull(QuizCollection.normalizeName("一二三四五六七八九十一二三四五六七八九十一"))
    }

    @Test
    fun parseListRoundTripsAndRejectsDuplicateNames() {
        val collections = listOf(
            QuizCollection("col-a", "成语专项", 1000),
            QuizCollection("col-b", "英语单词", 2000),
        )
        val parsed = QuizCollection.parseList(QuizCollection.encodeList(collections))
        assertEquals(collections, parsed)

        val duplicateName = JSONArray()
            .put(QuizCollection("col-a", "Tag", 1).encode())
            .put(QuizCollection("col-b", "tag", 2).encode())
        assertNull(QuizCollection.parseList(duplicateName))
    }

    @Test
    fun parseListRejectsBadIdsAndTheCap() {
        assertNull(QuizCollection.parseList(null))
        assertNull(QuizCollection.parseList(JSONArray().put(JSONObject().put("id", "bad id"))))

        val tooMany = JSONArray()
        repeat(QuizCollection.MAX_COLLECTIONS + 1) { index ->
            tooMany.put(QuizCollection("col-$index", "tag-$index", 1).encode())
        }
        assertNull(QuizCollection.parseList(tooMany))
    }

    @Test
    fun createRejectsInvalidNamesAndIds() {
        assertNotNull(QuizCollection.create("成语专项"))
        assertNull(QuizCollection.create(" "))
        assertNull(QuizCollection.create("成语专项", id = "bad id"))
    }

    @Test
    fun parseRejectsMissingCreatedAt() {
        assertNull(
            QuizCollection.parse(
                JSONObject()
                    .put("id", "col-a")
                    .put("name", "tag"),
            ),
        )
    }
}
