package app.orchard.tv.wallpaper

/**
 * The TV's own view of the phone page. It says whether the server is listening and answers on
 * loopback; it cannot prove that a phone on the same Wi-Fi can reach the TV (guest networks and
 * AP isolation are invisible from here), which is why [lastAccessAtMs] is part of the status.
 */
internal enum class PhoneWebState {
    CHECKING,
    OK,
    OFFLINE,
    NO_ADDRESS,
    NOT_STARTED,
    UNRESPONSIVE,
}

internal data class PhoneWebStatus(
    val state: PhoneWebState = PhoneWebState.CHECKING,
    /** [ip:port] while [state] is OK; the token stays inside PhoneServer. */
    val address: String? = null,
    /** The last non-loopback request for the parent page, or 0 when none since app start. */
    val lastAccessAtMs: Long = 0L,
    /** Lets Compose refresh relative "just now" copy even when the state itself is unchanged. */
    val checkedAtMs: Long = 0L,
)