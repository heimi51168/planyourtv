package com.planyourtv.policy

/**
 * Fixed announcements. :policy only decides when a cue happens; the app module maps each cue
 * to a pre-generated Chinese clip and owns audio playback.
 */
enum class PolicyVoiceCue {
    GRANT_READY,
    START,
    ONE_MINUTE,
    THIRTY_SECONDS,
    TWENTY_SECONDS,
    TEN_SECONDS,
    FIVE_SECONDS,
    /** Screen off: active counting is paused. The only cue allowed while the screen is off. */
    PAUSED,
    /** Screen on before the fixed wall deadline. */
    RESUMED,
    TIME_UP,
    PARENT_STOP,
    CANCELED,
    PIN_UPDATED,
    TEMP_UNLOCK,
    GUARD_RESUMED,
    CLOUD_MODE_RULE,
    CLOUD_MODE_REDEEM,
    /** Remote switch between automatic and manual, by SetActivation or the config pull. */
    REMOTE_ACTIVATION_AUTO,
    REMOTE_ACTIVATION_MANUAL,
    /** Quiz flow: start, one answer-feedback cue per answered question, and the report. */
    QUIZ_START,
    QUIZ_CORRECT,
    QUIZ_WRONG,
    QUIZ_TIMEOUT,
    /** Report with earned time. */
    QUIZ_SETTLE,
    /** Report with zero earned minutes; a neutral, encouraging line. */
    QUIZ_SETTLE_EMPTY,
}
