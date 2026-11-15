package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore

/**
 * Daily ledger and parent-set cap for quiz-earned viewing minutes.
 *
 * The rule engine's daily quota only covers automatic sessions, so quiz
 * rewards had no ceiling: a child could chain rounds all window long. This
 * store closes that gap — one counter per local day, one cap (0 = unlimited)
 * — and both the TV quiz path and the chat commands read the same object.
 *
 * Recording happens only when a reward session actually started
 * (QuizChallenge.completeReview), so an abandoned settlement never eats the
 * day's allowance.
 */
object QuizEarnedStore {
    private const val KEY_DATE = "quiz.earn.date"
    private const val KEY_MINUTES = "quiz.earn.minutes"
    private const val KEY_CAP = "quiz.earn.cap.minutes"

    /** Anything above this is nonsense; 240 minutes of earned TV per day is already extreme. */
    const val MAX_CAP_MINUTES = 240

    /** The daily cap in minutes; 0 means unlimited (the pre-cap behaviour). */
    fun capMinutes(): Int = TvStore.getInt(KEY_CAP, 0).coerceIn(0, MAX_CAP_MINUTES)

    fun setCapMinutes(value: Int): Boolean {
        if (value !in 0..MAX_CAP_MINUTES) return false
        TvStore.putInt(KEY_CAP, value)
        return true
    }

    fun earnedToday(nowMs: Long = System.currentTimeMillis()): Int {
        if (TvStore.getString(KEY_DATE, null) != todayKey(nowMs)) return 0
        return TvStore.getInt(KEY_MINUTES, 0).coerceAtLeast(0)
    }

    /** Unlimited when the cap is 0; otherwise what is left of today's allowance. */
    fun remainingToday(nowMs: Long = System.currentTimeMillis()): Int {
        val cap = capMinutes()
        if (cap == 0) return Int.MAX_VALUE
        return (cap - earnedToday(nowMs)).coerceAtLeast(0)
    }

    /**
     * Adds granted minutes, clamped to the remaining allowance. Returns the
     * amount actually recorded so the caller can stay honest in its audit.
     */
    fun recordEarned(minutes: Int, nowMs: Long = System.currentTimeMillis()): Int {
        if (minutes <= 0) return 0
        val recorded = minutes.coerceAtMost(remainingToday(nowMs))
        if (recorded <= 0) return 0
        TvStore.putString(KEY_DATE, todayKey(nowMs))
        TvStore.putInt(KEY_MINUTES, earnedToday(nowMs) + recorded)
        TvStore.sync()
        return recorded
    }

    private fun todayKey(nowMs: Long): String = QuizSessionStore.today(nowMs)
}
