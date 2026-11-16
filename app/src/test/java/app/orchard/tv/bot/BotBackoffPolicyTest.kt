package app.orchard.tv.bot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Boot hardening: network hiccups must stay nimble (a TV's WiFi can take a
 * minute after power-on) while protocol refusals keep a calmer rhythm, and no
 * path may return a five-minute silent window again.
 */
class BotBackoffPolicyTest {

    @Test
    fun networkErrorsDoubleUpToABootFriendlyCap() {
        var backoff = 1_000L
        backoff = BotChannel.nextBackoffMs(UpdatesResult.NetworkError, backoff)
        assertEquals(2_000L, backoff)
        backoff = BotChannel.nextBackoffMs(UpdatesResult.NetworkError, backoff)
        assertEquals(4_000L, backoff)
        backoff = BotChannel.nextBackoffMs(UpdatesResult.NetworkError, backoff)
        assertEquals(8_000L, backoff)
        backoff = BotChannel.nextBackoffMs(UpdatesResult.NetworkError, backoff)
        assertEquals(15_000L, backoff)
        // And it never grows past the cap, however long the outage.
        repeat(10) { backoff = BotChannel.nextBackoffMs(UpdatesResult.NetworkError, backoff) }
        assertEquals(15_000L, backoff)
    }

    @Test
    fun rejectionsCapAtOneMinuteNotFive() {
        var backoff = 30_000L
        backoff = BotChannel.nextBackoffMs(UpdatesResult.Rejected, backoff)
        assertEquals(60_000L, backoff)
        repeat(10) { backoff = BotChannel.nextBackoffMs(UpdatesResult.Rejected, backoff) }
        assertEquals(60_000L, backoff)
    }

    @Test
    fun floorIsTheInitialBackoff() {
        assertEquals(1_000L, BotChannel.nextBackoffMs(UpdatesResult.NetworkError, 0L))
        assertEquals(1_000L, BotChannel.nextBackoffMs(UpdatesResult.Rejected, 0L))
    }
}
