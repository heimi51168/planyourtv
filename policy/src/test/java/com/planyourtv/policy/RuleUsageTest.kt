package com.planyourtv.policy

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleUsageTest {
    private fun event(
        sessionId: String = "rule-session-1",
        type: RuleUsageEventType = RuleUsageEventType.PROGRESS,
        plannedSeconds: Long = 600L,
        sessionUsedSeconds: Long = 0L,
        usedSecondsToday: Long = 0L,
        endReason: PolicyEndReason? = null,
        endedAtEpochMs: Long? = null,
        seq: Long = 0L,
    ) = RuleUsageEvent(
        sessionId = sessionId,
        type = type,
        usageDate = "2026-10-30",
        startedAtEpochMs = 1_000L,
        endedAtEpochMs = endedAtEpochMs,
        plannedSeconds = plannedSeconds,
        sessionUsedSeconds = sessionUsedSeconds,
        usedSecondsToday = usedSecondsToday,
        endReason = endReason,
        templateVersion = 12L,
        accessMode = RuleUsageAccessMode.APPS,
        appScopeHash = "abc123",
        seq = seq,
    )

    private fun endEvent(
        sessionId: String = "rule-session-1",
        used: Long = 120L,
        today: Long = 120L,
    ) = event(
        sessionId = sessionId,
        type = RuleUsageEventType.END,
        sessionUsedSeconds = used,
        usedSecondsToday = today,
        endReason = PolicyEndReason.SESSION_LIMIT,
        endedAtEpochMs = 2_000L,
    )

    @Test
    fun progressMergesIntoOnePendingEventPerSession() {
        val queue = RuleUsageQueue.empty()
        queue.enqueue(event(type = RuleUsageEventType.START))
        queue.enqueue(event(sessionUsedSeconds = 60L, usedSecondsToday = 60L))
        queue.enqueue(event(sessionUsedSeconds = 120L, usedSecondsToday = 120L))
        assertEquals(1, queue.size)
        val pending = queue.snapshot().single()
        assertEquals(RuleUsageEventType.PROGRESS, pending.type)
        assertEquals(120L, pending.sessionUsedSeconds)
        assertEquals(3L, pending.seq)
    }

    @Test
    fun endReplacesProgressAndBlocksLateProgress() {
        val queue = RuleUsageQueue.empty()
        queue.enqueue(event(sessionUsedSeconds = 60L, usedSecondsToday = 60L))
        queue.enqueue(endEvent())
        val blocked = queue.enqueue(event(sessionUsedSeconds = 180L, usedSecondsToday = 180L))
        assertEquals(RuleUsageEventType.END, blocked.type)
        assertEquals(RuleUsageEventType.END, queue.snapshot().single().type)
    }

    @Test
    fun acknowledgeKeepsUpdatesArrivedWhileInFlight() {
        val queue = RuleUsageQueue.empty()
        queue.enqueue(event(sessionUsedSeconds = 60L, usedSecondsToday = 60L))
        val delivered = queue.snapshot()
        queue.enqueue(event(sessionUsedSeconds = 120L, usedSecondsToday = 120L))
        queue.acknowledge(delivered)
        assertEquals(120L, queue.snapshot().single().sessionUsedSeconds)

        queue.acknowledge(queue.snapshot())
        assertTrue(queue.isEmpty)
    }

    @Test
    fun sessionsKeepTheirEnqueueOrder() {
        val queue = RuleUsageQueue.empty()
        queue.enqueue(event(sessionId = "a"))
        queue.enqueue(event(sessionId = "b"))
        queue.enqueue(event(sessionId = "a", sessionUsedSeconds = 60L, usedSecondsToday = 60L))
        assertEquals(listOf("b", "a"), queue.snapshot().map { it.sessionId })
    }

    @Test
    fun restoreContinuesTheSequence() {
        val queue = RuleUsageQueue.restore(
            listOf(event(sessionId = "old", seq = 9L)),
        )
        val next = queue.enqueue(event(sessionId = "new"))
        assertEquals(10L, next.seq)
        assertEquals(2, queue.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun endRequiresAReason() {
        event(type = RuleUsageEventType.END, endedAtEpochMs = 2_000L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun progressCannotCarryAnEndReason() {
        event(endReason = PolicyEndReason.PARENT_STOP)
    }

    @Test(expected = IllegalArgumentException::class)
    fun dailyTotalCannotBeSmallerThanTheSession() {
        event(sessionUsedSeconds = 120L, usedSecondsToday = 60L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sessionCannotExceedThePlanBeyondTheGrace() {
        event(plannedSeconds = 600L, sessionUsedSeconds = 901L, usedSecondsToday = 901L)
    }

    @Test
    fun jsonRoundTripKeepsAllFields() {
        val original = endEvent().copy(seq = 7L)
        val restored = RuleUsageCodec.fromJson(RuleUsageCodec.toJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun jsonArraySkipsInvalidEntriesButKeepsValidOnes() {
        val valid = RuleUsageCodec.toJson(endEvent().copy(seq = 1L))
        val array = JSONArray().put(valid).put("not-an-event").put(42)
        val restored = RuleUsageCodec.fromJsonArray(array.toString())
        assertEquals(1, restored.size)
        assertEquals("rule-session-1", restored.single().sessionId)
    }

    @Test
    fun malformedRawJsonLoadsAsEmpty() {
        assertTrue(RuleUsageCodec.fromJsonArray("{not json").isEmpty())
        assertTrue(RuleUsageCodec.fromJsonArray(null).isEmpty())
    }

    @Test
    fun accessModeDefaultsToAllValidAppsWhenAbsent() {
        val json = RuleUsageCodec.toJson(endEvent())
        json.remove("accessMode")
        assertEquals(
            RuleUsageAccessMode.ALL_VALID_APP,
            RuleUsageCodec.fromJson(json)!!.accessMode,
        )
    }

    @Test
    fun endWithoutEndTimeIsRejectedByTheCodec() {
        val json = RuleUsageCodec.toJson(endEvent()).put("endedAt", 0)
        assertEquals(null, RuleUsageCodec.fromJson(json))
    }

    @Test
    fun emptyQueueIsEmpty() {
        assertTrue(RuleUsageQueue.empty().isEmpty)
        assertEquals(0, RuleUsageQueue.empty().size)
    }
}
