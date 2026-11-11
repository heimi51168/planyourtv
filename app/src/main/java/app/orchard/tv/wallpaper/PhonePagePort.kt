package app.orchard.tv.wallpaper

/**
 * Which port the phone page tries, in order.
 *
 * 8790 is Aerial's historical port. PlanYourTV can be installed beside Aerial, and Aerial may
 * still hold 8790 while its process is alive, so a fixed bind made the quiz QR disappear. A saved
 * port comes first to keep an already-bookmarked page working; the remaining ports are a stable
 * fallback, and 0 lets the system pick any free port.
 */
internal object PhonePagePort {
    const val PREFERRED = 8790

    fun candidates(saved: Int?): List<Int> = buildList {
        if (saved != null && saved in 1024..65535) add(saved)
        add(PREFERRED)
        for (port in (PREFERRED + 1)..(PREFERRED + 9)) add(port)
        add(0)
    }.distinct()
}
