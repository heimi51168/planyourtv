package app.orchard.tv.policy

/**
 * One-off manual session length shared by the TV lock screen and the parent web surfaces.
 *
 * The TV wheel only offers five-minute values from 5 to 120. The parent-center web sheet keeps
 * the same cap and step buttons, but its numeric field accepts any whole minute down to 1,
 * which is useful for quick tests. Each surface validates against its own range instead of
 * trusting the browser.
 */
object ManualSessionLimits {
    /** TV lock-screen wheel minimum; the stricter web route shares it. */
    const val MIN_MINUTES = 5

    /** Parent-center web typed-field minimum. */
    const val WEB_MIN_MINUTES = 1

    const val MAX_MINUTES = 120
    const val STEP_MINUTES = 5
    const val DEFAULT_MINUTES = 20

    /** Five-minute values only; the legacy quiz-page manual route keeps this contract. */
    fun isValid(minutes: Int): Boolean =
        minutes in MIN_MINUTES..MAX_MINUTES && minutes % STEP_MINUTES == 0

    /** Parent-center web typed field: every whole minute from 1 through the shared cap. */
    fun isValidWeb(minutes: Int): Boolean =
        minutes in WEB_MIN_MINUTES..MAX_MINUTES
}
