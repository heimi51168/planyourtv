package com.planyourtv.policy.quiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizAnswerScopeTest {
    @Test
    fun allSubjectAndTextbookAreSupportedWildcards() {
        assertTrue(QuizAnswerScope.isSupportedSubject("all"))
        assertTrue(QuizAnswerScope.isSupportedTextbook(" ALL "))
        val scope = QuizAnswerScope.create(
            grade = 4,
            semester = 1,
            subject = "all",
            textbook = "all",
            scopeType = "review",
            unitNo = null,
            difficulty = null,
        )
        assertNotNull(scope)
        assertEquals(QuizAnswerScope.SUBJECT_ALL, scope!!.subject)
        assertEquals(QuizAnswerScope.TEXTBOOK_ALL, scope.textbook)
    }

    @Test
    fun allWildcardsRoundTripThroughTheProtocol() {
        val encoded = QuizAnswerScope.create(
            grade = 4,
            semester = 2,
            subject = "ALL",
            textbook = "ALL",
            scopeType = "UNIT",
            unitNo = 3,
            difficulty = "HARD",
        )!!.encode()
        val parsed = QuizAnswerScope.parse(encoded)
        assertNotNull(parsed)
        assertEquals("ALL", parsed!!.subject)
        assertEquals("ALL", parsed.textbook)
        assertEquals(3, parsed.unitNo)
        assertEquals("HARD", parsed.difficulty)
    }

    @Test
    fun nearMissWildcardValuesAreStillRejected() {
        assertNull(
            QuizAnswerScope.create(
                grade = 4,
                semester = 1,
                subject = "ALL-SUBJECTS",
                textbook = "RENJIAO",
                scopeType = "review",
                unitNo = null,
                difficulty = null,
            ),
        )
        assertNull(
            QuizAnswerScope.parse(
                JSONObject()
                    .put("grade", 4)
                    .put("semester", 1)
                    .put("subject", "ALL")
                    .put("textbook", "EVERYTHING")
                    .put("scopeType", "REVIEW"),
            ),
        )
    }
}
