package app.orchard.tv.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownRingStyleTest {
    @Test
    fun toneChangesAtTheExactMinuteAndThirtySecondBoundaries() {
        assertEquals(CountdownTone.GREEN, countdownTone(61))
        assertEquals(CountdownTone.ORANGE, countdownTone(60))
        assertEquals(CountdownTone.ORANGE, countdownTone(31))
        assertEquals(CountdownTone.RED, countdownTone(30))
        assertEquals(CountdownTone.RED, countdownTone(1))
        assertEquals(CountdownTone.RED, countdownTone(0))
    }

    @Test
    fun lastMinuteUsesTheVisibleOneMinuteGauge() {
        assertEquals(1f, countdownProgress(60, 60 * 60), 0.0001f)
        assertEquals(0.5f, countdownProgress(30, 60 * 60), 0.0001f)
        assertEquals(0f, countdownProgress(0, 60 * 60), 0.0001f)
    }

    @Test
    fun shortSessionsStartWithAFullRing() {
        assertEquals(1f, countdownProgress(45, 45), 0.0001f)
        assertEquals(1f, countdownProgress(30, 30), 0.0001f)
        assertEquals(0.5f, countdownProgress(15, 30), 0.0001f)
    }

    @Test
    fun normalSessionsKeepTheirProgressBeforeTheLastMinute() {
        assertEquals(0.5f, countdownProgress(30 * 60, 60 * 60), 0.0001f)
    }
}
