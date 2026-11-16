package app.orchard.tv.bot

import com.planyourtv.policy.TvStore
import java.util.Calendar

/**
 * One-day usage rollup behind the 日报 command and the 21:00 auto push.
 *
 * The rule engine keeps only the current day's running totals, so this store
 * snapshots them as events pass and keeps exactly one yesterday slot: enough
 * for "today so far / yesterday" without growing a database. Watched seconds
 * arrive as the day's absolute total (monotonic by construction), quiz rounds
 * and earned minutes accumulate per event.
 */
internal object BotDailyReport {
    private const val KEY_DATE = "bot.report.date"
    private const val KEY_TODAY_SEC = "bot.report.today.sec"
    private const val KEY_TODAY_ROUNDS = "bot.report.today.rounds"
    private const val KEY_TODAY_EARNED = "bot.report.today.earned"
    private const val KEY_YESTERDAY_SEC = "bot.report.yesterday.sec"
    private const val KEY_YESTERDAY_ROUNDS = "bot.report.yesterday.rounds"
    private const val KEY_YESTERDAY_EARNED = "bot.report.yesterday.earned"
    private const val KEY_AUTOPUSH_DATE = "bot.report.autopush.date"

    /** Local-time hour when the auto push goes out. */
    const val AUTO_PUSH_HOUR = 21

    @Synchronized
    fun ensure(nowMs: Long = System.currentTimeMillis()) {
        val today = dayKey(nowMs)
        if (TvStore.getString(KEY_DATE, null) == today) return
        // Today's numbers become yesterday's; the new day starts at zero.
        TvStore.putLong(KEY_YESTERDAY_SEC, TvStore.getLong(KEY_TODAY_SEC, 0L))
        TvStore.putInt(KEY_YESTERDAY_ROUNDS, TvStore.getInt(KEY_TODAY_ROUNDS, 0))
        TvStore.putInt(KEY_YESTERDAY_EARNED, TvStore.getInt(KEY_TODAY_EARNED, 0))
        TvStore.putString(KEY_DATE, today)
        TvStore.putLong(KEY_TODAY_SEC, 0L)
        TvStore.putInt(KEY_TODAY_ROUNDS, 0)
        TvStore.putInt(KEY_TODAY_EARNED, 0)
    }

    /** [secondsToday] is the engine's absolute local-day total. */
    @Synchronized
    fun recordWatchedSeconds(secondsToday: Long, nowMs: Long = System.currentTimeMillis()) {
        if (secondsToday <= 0) return
        ensure(nowMs)
        if (secondsToday > TvStore.getLong(KEY_TODAY_SEC, 0L)) {
            TvStore.putLong(KEY_TODAY_SEC, secondsToday)
        }
    }

    @Synchronized
    fun recordQuizRound(earnedMinutes: Int, nowMs: Long = System.currentTimeMillis()) {
        ensure(nowMs)
        TvStore.putInt(KEY_TODAY_ROUNDS, TvStore.getInt(KEY_TODAY_ROUNDS, 0) + 1)
        if (earnedMinutes > 0) {
            TvStore.putInt(KEY_TODAY_EARNED, TvStore.getInt(KEY_TODAY_EARNED, 0) + earnedMinutes)
        }
    }

    fun reportText(nowMs: Long = System.currentTimeMillis()): String {
        ensure(nowMs)
        val todayWatched = TvStore.getLong(KEY_TODAY_SEC, 0L) / 60
        val todayRounds = TvStore.getInt(KEY_TODAY_ROUNDS, 0)
        val todayEarned = TvStore.getInt(KEY_TODAY_EARNED, 0)
        val yWatched = TvStore.getLong(KEY_YESTERDAY_SEC, 0L) / 60
        val yRounds = TvStore.getInt(KEY_YESTERDAY_ROUNDS, 0)
        val yEarned = TvStore.getInt(KEY_YESTERDAY_EARNED, 0)
        val yesterdayLine = if (yWatched == 0L && yRounds == 0) {
            "昨日：无记录"
        } else {
            "昨日：看 $yWatched 分钟 · 答题 $yRounds 轮 · 所得 $yEarned 分钟"
        }
        return "今日：看 $todayWatched 分钟 · 答题 $todayRounds 轮 · 所得 $todayEarned 分钟\n$yesterdayLine"
    }

    /** True once per local day, after [AUTO_PUSH_HOUR], until [markAutoPushed]. */
    fun autoPushDue(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (TvStore.getString(KEY_AUTOPUSH_DATE, null) == dayKey(nowMs)) return false
        return localHour(nowMs) >= AUTO_PUSH_HOUR
    }

    fun markAutoPushed(nowMs: Long = System.currentTimeMillis()) {
        TvStore.putString(KEY_AUTOPUSH_DATE, dayKey(nowMs))
    }

    private fun dayKey(nowMs: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun localHour(nowMs: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        return cal.get(Calendar.HOUR_OF_DAY)
    }
}
