package app.orchard.tv.quiz

/**
 * The report's score is gold only when the round actually unlocked time.
 * A zero score stays green even with no unlock threshold configured, and a
 * score below the threshold stays green even though it is above zero.
 */
object QuizResultTone {
    fun isGold(scoreMinutes: Int, unlockReached: Boolean): Boolean =
        unlockReached && scoreMinutes > 0
}
