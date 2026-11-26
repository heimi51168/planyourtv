package app.orchard.tv.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSessionLimitsTest {
    @Test
    fun acceptsTheSameRangeAndStepAsTheTvPicker() {
        assertTrue(ManualSessionLimits.isValid(ManualSessionLimits.MIN_MINUTES))
        assertTrue(ManualSessionLimits.isValid(ManualSessionLimits.DEFAULT_MINUTES))
        assertTrue(ManualSessionLimits.isValid(ManualSessionLimits.MAX_MINUTES))
    }

    @Test
    fun rejectsOutOfRangeAndOffStepDurations() {
        assertFalse(ManualSessionLimits.isValid(ManualSessionLimits.MIN_MINUTES - 5))
        assertFalse(ManualSessionLimits.isValid(ManualSessionLimits.MAX_MINUTES + 5))
        assertFalse(ManualSessionLimits.isValid(ManualSessionLimits.MIN_MINUTES + 1))
    }

    @Test
    fun webAcceptsEveryWholeMinuteDownToOne() {
        assertTrue(ManualSessionLimits.isValidWeb(ManualSessionLimits.WEB_MIN_MINUTES))
        assertTrue(ManualSessionLimits.isValidWeb(7))
        assertTrue(ManualSessionLimits.isValidWeb(ManualSessionLimits.MAX_MINUTES))
    }

    @Test
    fun webRejectsZeroAndAboveTheCap() {
        assertFalse(ManualSessionLimits.isValidWeb(ManualSessionLimits.WEB_MIN_MINUTES - 1))
        assertFalse(ManualSessionLimits.isValidWeb(ManualSessionLimits.MAX_MINUTES + 1))
    }
}
