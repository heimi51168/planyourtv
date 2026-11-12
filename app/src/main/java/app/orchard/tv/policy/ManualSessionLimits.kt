package app.orchard.tv.policy

/**
 * One-off manual session length. The lock-screen picker and the parent web page must offer the
 * same range; the server validates the web request against these values instead of trusting the
 * browser.
 */
object ManualSessionLimits {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 120
    const val STEP_MINUTES = 5
    const val DEFAULT_MINUTES = 20

    fun isValid(minutes: Int): Boolean =
        minutes in MIN_MINUTES..MAX_MINUTES && minutes % STEP_MINUTES == 0
}
