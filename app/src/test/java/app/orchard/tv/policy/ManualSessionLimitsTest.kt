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
}
