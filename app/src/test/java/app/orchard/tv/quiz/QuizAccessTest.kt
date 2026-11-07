package app.orchard.tv.quiz

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class QuizAccessTest {
    private fun ms(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: ZoneId,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun remembersThePhoneUntilTheNextLocalMidnight() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ms(2026, 11, 3, 10, 30, zone)
        val expected = ms(2026, 11, 4, 0, 0, zone)
        assertEquals(expected, QuizAccess.nextLocalMidnight(now, zone))
    }

    @Test
    fun lateEveningStillExpiresAtMidnight() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ms(2026, 11, 3, 23, 59, zone)
        val expected = ms(2026, 11, 4, 0, 0, zone)
        assertEquals(expected, QuizAccess.nextLocalMidnight(now, zone))
    }

    @Test
    fun dstDayStillEndsAtLocalMidnight() {
        val zone = ZoneId.of("America/New_York")
        val now = ms(2026, 3, 8, 1, 30, zone)
        val expected = ms(2026, 3, 9, 0, 0, zone)
        assertEquals(expected, QuizAccess.nextLocalMidnight(now, zone))
    }

    @Test
    fun fallBackDayCanLastTwentyFiveHours() {
        val zone = ZoneId.of("America/New_York")
        val now = ms(2026, 11, 1, 0, 0, zone)
        val expected = ms(2026, 11, 2, 0, 0, zone)
        assertEquals(expected, QuizAccess.nextLocalMidnight(now, zone))
        assertEquals(25L * 60 * 60 * 1000, expected - now)
    }
}
