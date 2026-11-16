package app.orchard.tv.policy.media

/**
 * What a policy media item is. The role still decides where it may be used: a picture can be a
 * child-mode wallpaper, but it has no playback semantics and can never be a quiz intro.
 *
 * Wire names are stored in `info.json` and sent to the parent page; an old item without a kind is
 * a video, which is what every pre-V34 item was.
 */
enum class PolicyMediaKind(val wire: String) {
    VIDEO("video"),
    IMAGE("image");

    companion object {
        fun fromWire(value: String?): PolicyMediaKind? =
            entries.firstOrNull { it.wire == value }

        fun fromWireOrDefault(value: String?): PolicyMediaKind =
            fromWire(value) ?: VIDEO
    }
}

/** Images are lock-screen wallpapers only; videos still fill both policy roles. */
fun PolicyVideoRole.supports(kind: PolicyMediaKind): Boolean = when (this) {
    PolicyVideoRole.QUIZ_INTRO -> kind == PolicyMediaKind.VIDEO
    PolicyVideoRole.LOCK_WALLPAPER -> true
}
