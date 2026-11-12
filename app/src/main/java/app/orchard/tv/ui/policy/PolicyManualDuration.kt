package app.orchard.tv.ui.policy

import app.orchard.tv.policy.ManualSessionLimits

/**
 * The one-off session length is a continuous 5-minute wheel rather than a fixed set of choices.
 * A parent can reach any length in the range without a new option being added to the UI.
 */
internal const val MANUAL_DURATION_MIN_MINUTES = ManualSessionLimits.MIN_MINUTES
internal const val MANUAL_DURATION_MAX_MINUTES = ManualSessionLimits.MAX_MINUTES
internal const val MANUAL_DURATION_STEP_MINUTES = ManualSessionLimits.STEP_MINUTES
internal const val MANUAL_DURATION_DEFAULT_MINUTES = ManualSessionLimits.DEFAULT_MINUTES

internal fun stepManualDuration(current: Int, up: Boolean): Int =
    (current + if (up) MANUAL_DURATION_STEP_MINUTES else -MANUAL_DURATION_STEP_MINUTES)
        .coerceIn(MANUAL_DURATION_MIN_MINUTES, MANUAL_DURATION_MAX_MINUTES)
