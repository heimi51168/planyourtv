package app.orchard.tv.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyCountdownOverlayTest {
    @Test
    fun countdownLabelUsesSecondsInsteadOfClockFormat() {
        // The ball only ever shows these 10-second buckets in its final 30 seconds.
        assertEquals("30s", countdownLabel(30))
        assertEquals("20s", countdownLabel(20))
        assertEquals("10s", countdownLabel(10))
    }

    @Test
    fun countdownLabelClampsNegativeSeconds() {
        assertEquals("0s", countdownLabel(-1))
    }
}
