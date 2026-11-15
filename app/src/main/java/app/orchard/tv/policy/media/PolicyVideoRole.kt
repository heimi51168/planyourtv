package app.orchard.tv.policy.media

/**
 * The two places a policy film can play. The timer and every other child-mode
 * surface follow the lock role; they never get a separate file of their own.
 *
 * Wire names are the only values the parent-center API accepts. Preference
 * keys are fixed here so a request can never choose an arbitrary key.
 */
enum class PolicyVideoRole(val wire: String, val prefKey: String) {
    QUIZ_INTRO("quiz", "active_id"),
    LOCK_WALLPAPER("lock", "lock_id");

    companion object {
        fun fromWire(value: String?): PolicyVideoRole? =
            entries.firstOrNull { it.wire == value }

        /** `both` is a request-only convenience; it never names a stored slot. */
        fun fromRequest(value: String?): Set<PolicyVideoRole>? = when (value) {
            "quiz" -> setOf(QUIZ_INTRO)
            "lock" -> setOf(LOCK_WALLPAPER)
            "both" -> entries.toSet()
            else -> null
        }

        /** A download without a role is a debug/compat action and assigns nothing. */
        fun fromOptionalRequest(value: String?): Set<PolicyVideoRole>? = when (value) {
            null, "" -> emptySet()
            else -> fromRequest(value)
        }
    }
}
