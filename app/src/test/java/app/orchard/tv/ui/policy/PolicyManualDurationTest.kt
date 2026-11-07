package app.orchard.tv.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyManualDurationTest {
    @Test
    fun defaultIsTwentyMinutes() {
        assertEquals(20, MANUAL_DURATION_DEFAULT_MINUTES)
    }

    @Test
    fun stepUpAddsFiveMinutes() {
        assertEquals(25, stepManualDuration(20, up = true))
    }

    @Test
    fun stepDownSubtractsFiveMinutes() {
        assertEquals(15, stepManualDuration(20, up = false))
    }

    @Test
    fun stepClampsAtMinimum() {
        assertEquals(5, stepManualDuration(5, up = false))
    }

    @Test
    fun stepClampsAtMaximum() {
        assertEquals(120, stepManualDuration(120, up = true))
    }
}
