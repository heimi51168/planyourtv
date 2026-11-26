package app.orchard.tv.policy.web

import java.security.SecureRandom

/**
 * Short-lived playback grants for the parent page.
 *
 * A <video> element cannot send the editor header, and putting the editor lease itself in a
 * URL would expose the whole management token to history and logs. The page instead trades its
 * lease for a random ticket bound to one media id; the ticket is accepted only by the stream
 * route. Grants are memory-only, expire after an hour and are revoked when that editor releases
 * the lease, so a closed page cannot keep streaming.
 */
internal object ParentMediaTickets {
    const val KIND_MEDIA = "media"
    const val KIND_AUDIO = "audio"

    private const val TTL_MS = 60 * 60 * 1000L
    private const val MAX_TICKETS = 32
    private val hex = "0123456789abcdef".toCharArray()

    private data class Grant(
        val editorToken: String,
        val mediaId: String,
        val kind: String,
        val expiresAtMs: Long,
    )

    private val grants = LinkedHashMap<String, Grant>()
    private val random = SecureRandom()

    @Synchronized
    fun mint(
        editorToken: String,
        mediaId: String,
        now: Long = System.currentTimeMillis(),
        kind: String = KIND_MEDIA,
    ): String {
        purge(now)
        while (grants.size >= MAX_TICKETS) {
            val oldest = grants.keys.iterator()
            if (!oldest.hasNext()) break
            oldest.next()
            oldest.remove()
        }
        val token = ByteArray(24).also(random::nextBytes).toHex()
        grants[token] = Grant(editorToken, mediaId, kind, now + TTL_MS)
        return token
    }

    @Synchronized
    fun check(
        token: String?,
        mediaId: String,
        now: Long = System.currentTimeMillis(),
        kind: String = KIND_MEDIA,
    ): Boolean {
        if (token.isNullOrEmpty()) return false
        val grant = grants[token] ?: return false
        if (grant.expiresAtMs <= now) {
            grants.remove(token)
            return false
        }
        // A 32-hex id can name both a video and an audio track. The kind keeps a stream grant
        // from crossing libraries even though the editor lease itself would allow both.
        return grant.mediaId == mediaId && grant.kind == kind
    }

    /** Drops every grant minted by this editor; called when its lease is released. */
    @Synchronized
    fun revokeEditor(editorToken: String?) {
        if (editorToken.isNullOrEmpty()) return
        val iterator = grants.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.editorToken == editorToken) iterator.remove()
        }
    }

    /** A new editor (including a takeover) invalidates any stream left by the previous page. */
    @Synchronized
    fun revokeAll() {
        grants.clear()
    }

    private fun purge(now: Long) {
        val iterator = grants.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.expiresAtMs <= now) iterator.remove()
        }
    }

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        forEachIndexed { index, value ->
            val byte = value.toInt() and 0xFF
            out[index * 2] = hex[byte ushr 4]
            out[index * 2 + 1] = hex[byte and 0x0F]
        }
        return String(out)
    }
}