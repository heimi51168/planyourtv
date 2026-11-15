package app.orchard.tv.bot

import com.planyourtv.policy.TvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * The daily report keeps one yesterday slot and pushes once per local day
 * after 21:00; these are the two invariants parents actually observe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BotDailyReportTest {

    @Before
    fun resetStore() {
        TvStore.init(RuntimeEnvironment.getApplication())
        for (key in listOf(
            "bot.report.date", "bot.report.today.sec", "bot.report.today.rounds",
            "bot.report.today.earned", "bot.report.yesterday.sec", "bot.report.yesterday.rounds",
            "bot.report.yesterday.earned", "bot.report.autopush.date",
        )) {
            TvStore.remove(key)
        }
    }

    private fun ms(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun watchedSecondsOnlyMoveForward() {
        BotDailyReport.recordWatchedSeconds(1200, ms(14, 10))
        BotDailyReport.recordWatchedSeconds(900, ms(14, 11))
        assertEquals(1200, TvStore.getLong("bot.report.today.sec", 0L))
        BotDailyReport.recordWatchedSeconds(1800, ms(14, 12))
        assertEquals(1800, TvStore.getLong("bot.report.today.sec", 0L))
    }

    @Test
    fun quizRoundsAndEarnedAccumulate() {
        BotDailyReport.recordQuizRound(8, ms(14, 10))
        BotDailyReport.recordQuizRound(0, ms(14, 12))
        assertEquals(2, TvStore.getInt("bot.report.today.rounds", 0))
        assertEquals(8, TvStore.getInt("bot.report.today.earned", 0))
    }

    @Test
    fun dayRolloverArchivesTodayIntoYesterday() {
        BotDailyReport.recordWatchedSeconds(3600, ms(14, 20))
        BotDailyReport.recordQuizRound(10, ms(14, 20))
        BotDailyReport.ensure(ms(15, 8))
        assertEquals(3600, TvStore.getLong("bot.report.yesterday.sec", 0L))
        assertEquals(1, TvStore.getInt("bot.report.yesterday.rounds", 0))
        assertEquals(10, TvStore.getInt("bot.report.yesterday.earned", 0))
        assertEquals(0, TvStore.getLong("bot.report.today.sec", 0L))
    }

    @Test
    fun autoPushDueOnlyAfterNinePmAndOnceADay() {
        assertFalse(BotDailyReport.autoPushDue(ms(14, 20)))
        assertFalse(BotDailyReport.autoPushDue(ms(14, 20, 59)))
        assertTrue(BotDailyReport.autoPushDue(ms(14, 21, 0)))
        BotDailyReport.markAutoPushed(ms(14, 21, 5))
        assertFalse("already pushed today", BotDailyReport.autoPushDue(ms(14, 22)))
        assertTrue("next day opens a new slot", BotDailyReport.autoPushDue(ms(15, 21, 0)))
    }

    @Test
    fun reportTextMentionsBothDays() {
        BotDailyReport.recordWatchedSeconds(1800, ms(14, 20))
        BotDailyReport.recordQuizRound(8, ms(14, 20))
        val text = BotDailyReport.reportText(ms(14, 21))
        assertTrue(text.contains("今日：看 30 分钟"))
        assertTrue(text.contains("答题 1 轮"))
        assertTrue(text.contains("昨日：无记录"))
    }
}
