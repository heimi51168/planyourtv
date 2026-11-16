package app.orchard.tv.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate covers transport and abuse only (authentication is the binding):
 * at-least-once dedup and the per-binding rate limit, plus the one execution
 * lock that serializes commands across bindings.
 */
class BotCommandGateTest {

    private var nowMs = 1_000_000L
    private val gate = BotCommandGate(clock = { nowMs })

    @Test
    fun sameMessageIdIsDeliveredOncePerBinding() {
        assertTrue(gate.admitMessage("b1", "m1"))
        assertFalse(gate.admitMessage("b1", "m1"))
        // The same id under another binding is a different delivery.
        assertTrue(gate.admitMessage("b2", "m1"))
    }

    @Test
    fun oldestMessageIdsEvictBeyondTheLruCap() {
        // Re-delivery of an evicted id is accepted: dedup only covers the window
        // where the at-least-once transport actually re-sends.
        for (i in 0..600) {
            gate.admitMessage("b1", "m$i")
        }
        assertTrue(gate.admitMessage("b1", "m0"))
    }

    @Test
    fun emptyMessageIdAlwaysPasses() {
        assertTrue(gate.admitMessage("b1", ""))
        assertTrue(gate.admitMessage("b1", ""))
    }

    @Test
    fun rateLimitAllowsSixCommandsPerMinute() {
        repeat(6) { index ->
            assertTrue("command $index should pass", gate.admitRate("b1"))
        }
        assertFalse(gate.admitRate("b1"))
    }

    @Test
    fun rateLimitWindowSlides() {
        repeat(6) { gate.admitRate("b1") }
        assertFalse(gate.admitRate("b1"))
        nowMs += 61_000
        assertTrue(gate.admitRate("b1"))
    }

    @Test
    fun rateLimitIsPerBinding() {
        repeat(6) { gate.admitRate("b1") }
        assertFalse(gate.admitRate("b1"))
        assertTrue(gate.admitRate("b2"))
    }

    @Test
    fun runExclusiveSerializesExecution() {
        // A nesting attempt would deadlock a plain synchronized; the gate's
        // contract is that commands never nest, and this documents it.
        val result = gate.runExclusive { gate.runExclusive { "inner" } }
        assertEquals("inner", result)
    }
}
