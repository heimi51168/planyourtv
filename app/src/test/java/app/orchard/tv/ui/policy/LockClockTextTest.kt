package app.orchard.tv.ui.policy

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LockClockTextTest {
    @Test
    fun padsSingleDigitHourAndMinute() {
        assertEquals("09:05", lockClockText(LocalDateTime.of(2026, 11, 6, 9, 5)))
    }

    @Test
    fun midnightIsZeroPadded() {
        assertEquals("00:00", lockClockText(LocalDateTime.of(2026, 11, 6, 0, 0)))
    }

    @Test
    fun lastMinuteOfDay() {
        assertEquals("23:59", lockClockText(LocalDateTime.of(2026, 11, 6, 23, 59)))
    }
}
