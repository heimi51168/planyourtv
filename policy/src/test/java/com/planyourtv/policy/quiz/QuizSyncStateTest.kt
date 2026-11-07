package com.planyourtv.policy.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSyncStateTest {
    @Test
    fun roundTripsEveryField() {
        val state = QuizSyncState(
            pendingLocal = true,
            conflict = true,
            pendingKind = QuizPendingKind.EDIT,
            lastError = "CONFLICT",
            updatedAt = 123L,
        )
        val decoded = QuizSyncState.decode(state.encode().toString())
        assertEquals(state, decoded)
    }

    @Test
    fun emptyRawIsNoRecord() {
        assertNull(QuizSyncState.decode(null))
        assertNull(QuizSyncState.decode(""))
    }

    @Test
    fun malformedRawIsNoRecord() {
        assertNull(QuizSyncState.decode("not json"))
    }

    @Test
    fun unknownPendingKindFallsBackToNone() {
        val decoded = QuizSyncState.decode(
            """{"pendingLocal":true,"pendingKind":"FUTURE","lastError":"NETWORK"}""",
        )
        requireNotNull(decoded)
        assertEquals(QuizPendingKind.NONE, decoded.pendingKind)
        assertTrue(decoded.pendingLocal)
        assertEquals("NETWORK", decoded.lastError)
    }

    @Test
    fun defaultsAreAnEmptyState() {
        val decoded = QuizSyncState.decode("{}")
        requireNotNull(decoded)
        assertFalse(decoded.pendingLocal)
        assertFalse(decoded.conflict)
        assertEquals(QuizPendingKind.NONE, decoded.pendingKind)
        assertEquals("", decoded.lastError)
        assertEquals(0L, decoded.updatedAt)
    }
}
