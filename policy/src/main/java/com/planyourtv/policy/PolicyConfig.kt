package com.planyourtv.policy

/** Access scope of a grant. SCREEN is the current product; APPS is the future allowlist mode. */
enum class AccessMode {
    SCREEN,
    APPS,
}

/**
 * A cloud-issued time grant, mapped from `GET /devices/current/config` by the app module.
 * This class is pure data: no JSON, HTTP or Android types, so it can be unit tested.
 */
data class PolicyConfig(
    val recordId: Int,
    val totalSeconds: Long,
    val costSeconds: Long,
    val status: Int,
    val userName: String = "",
    /** The grant recipient's icon URL, cached locally by the app adapter. */
    val userIconUrl: String = "",
    /** Remaining physical window in seconds, computed from validEndTime/currentTime by the adapter. */
    val validWindowSeconds: Long? = null,
    /** Short label for the gate, for example "22:45". Empty when the server gave no window. */
    val validUntilLabel: String = "",
    val accessMode: AccessMode = AccessMode.SCREEN,
    val allowedPackages: Set<String> = emptySet(),
) {
    val remainingSeconds: Long
        get() = (totalSeconds - costSeconds).coerceAtLeast(0L)

    val usable: Boolean
        get() = status == 1 &&
            recordId > 0 &&
            remainingSeconds > 0 &&
            (validWindowSeconds == null || validWindowSeconds > 0)
}
