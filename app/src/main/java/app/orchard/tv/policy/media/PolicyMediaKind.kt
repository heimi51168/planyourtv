package app.orchard.tv.policy.media

/**
 * What a policy media item is. Locally stored pictures can be both a child-mode wallpaper and a
 * quiz intro (the intro then holds the still); hosted/cloud plans keep their own video-only
 * contract in [HostedMediaPlanner].
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

/** Local policy media fills both roles; the hosted planner applies its stricter video rule. */
fun PolicyVideoRole.supports(kind: PolicyMediaKind): Boolean {
    // Both kinds are valid locally; mention them explicitly so a future third kind must decide.
    return kind == PolicyMediaKind.VIDEO || kind == PolicyMediaKind.IMAGE
}
