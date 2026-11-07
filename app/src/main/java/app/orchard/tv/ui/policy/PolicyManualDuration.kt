package app.orchard.tv.ui.policy

/**
 * The one-off session length is a continuous 5-minute wheel rather than a fixed set of choices.
 * A parent can reach any length in the range without a new option being added to the UI.
 */
internal const val MANUAL_DURATION_MIN_MINUTES = 5
internal const val MANUAL_DURATION_MAX_MINUTES = 120
internal const val MANUAL_DURATION_STEP_MINUTES = 5
internal const val MANUAL_DURATION_DEFAULT_MINUTES = 20

internal fun stepManualDuration(current: Int, up: Boolean): Int =
    (current + if (up) MANUAL_DURATION_STEP_MINUTES else -MANUAL_DURATION_STEP_MINUTES)
        .coerceIn(MANUAL_DURATION_MIN_MINUTES, MANUAL_DURATION_MAX_MINUTES)
