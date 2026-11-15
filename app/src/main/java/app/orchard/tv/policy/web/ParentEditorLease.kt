package app.orchard.tv.policy.web

import android.os.SystemClock

/**
 * Single-editor lease for the web parent center.
 *
 * It only serializes two phones editing the same page: the first visible page owns the lease
 * and keeps it alive with a heartbeat, and a second phone must take over explicitly. The TV
 * parent center is read-only and is deliberately not a party to this lease, so opening it
 * neither blocks a phone nor is blocked by one.
 *
 * All timing uses elapsedRealtime so a wall-clock change cannot extend a lease; nothing is
 * persisted, so a process restart releases everything.
 *
 * This class is the testable engine; [ParentEditorLease] is the process-wide instance.
 */
class ParentEditorLeaseEngine(
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
    private val randomToken: () -> String = ParentWebCrypto::randomToken,
) {
    enum class Owner { IDLE, WEB }

    data class State(
        val owner: Owner,
        /** The web page's hard-cap deadline; zero unless [owner] is WEB. */
        val expiresAtMs: Long,
        val lastSeenMs: Long,
    )

    sealed interface AcquireResult {
        data class Ok(val token: String, val expiresAtMs: Long) : AcquireResult
        data object WebEditing : AcquireResult
    }

    sealed interface HeartbeatResult {
        data class Ok(val remainingMs: Long) : HeartbeatResult
        data object Expired : HeartbeatResult
        data object TakenOver : HeartbeatResult
    }

    private var owner = Owner.IDLE
    private var tokenDigest: String? = null
    private var acquiredAtMs = 0L
    private var lastSeenMs = 0L

    @Synchronized
    fun state(): State {
        expireWebIfNeeded()
        return State(
            owner = owner,
            expiresAtMs = if (owner == Owner.WEB) acquiredAtMs + WEB_HARD_CAP_MS else 0L,
            lastSeenMs = lastSeenMs,
        )
    }

    /** True when the web token is valid right now; does not renew the heartbeat. */
    @Synchronized
    fun webTokenValid(digestHex: String): Boolean {
        expireWebIfNeeded()
        return owner == Owner.WEB && ParentWebCrypto.digestEquals(tokenDigest, digestHex)
    }

    /** Web acquired with a valid parent proof (LOCAL/CLOUD entry already verified). */
    @Synchronized
    fun acquireWeb(): AcquireResult {
        expireWebIfNeeded()
        return when (owner) {
            Owner.WEB -> AcquireResult.WebEditing
            Owner.IDLE -> mintWebLocked()
        }
    }

    @Synchronized
    fun heartbeat(digestHex: String): HeartbeatResult {
        if (owner != Owner.WEB) return HeartbeatResult.TakenOver
        if (!ParentWebCrypto.digestEquals(tokenDigest, digestHex)) {
            return HeartbeatResult.TakenOver
        }
        val current = now()
        if (current - lastSeenMs >= WEB_TTL_MS || current - acquiredAtMs >= WEB_HARD_CAP_MS) {
            invalidateLocked()
            return HeartbeatResult.Expired
        }
        lastSeenMs = current
        return HeartbeatResult.Ok((acquiredAtMs + WEB_HARD_CAP_MS - current).coerceAtLeast(0L))
    }

    /** Best-effort page close. A missing release is covered by [WEB_TTL_MS]. */
    @Synchronized
    fun releaseWeb(digestHex: String) {
        if (owner == Owner.WEB && ParentWebCrypto.digestEquals(tokenDigest, digestHex)) {
            invalidateLocked()
        }
    }

    /** External invalidation (screen off, process stop, remote lock, quiz takeover). */
    @Synchronized
    fun invalidate(reason: String) {
        invalidateLocked()
    }

    /** Invalidate only a phone-held page, e.g. when another phone explicitly takes over. */
    @Synchronized
    fun invalidateWeb(reason: String) {
        if (owner == Owner.WEB) invalidateLocked()
    }

    private fun mintWebLocked(): AcquireResult {
        val token = randomToken()
        owner = Owner.WEB
        tokenDigest = ParentWebCrypto.sha256Hex(token)
        acquiredAtMs = now()
        lastSeenMs = acquiredAtMs
        return AcquireResult.Ok(token, acquiredAtMs + WEB_HARD_CAP_MS)
    }

    private fun expireWebIfNeeded() {
        if (owner != Owner.WEB) return
        val current = now()
        if (current - lastSeenMs >= WEB_TTL_MS || current - acquiredAtMs >= WEB_HARD_CAP_MS) {
            invalidateLocked()
        }
    }

    private fun invalidateLocked() {
        owner = Owner.IDLE
        tokenDigest = null
        acquiredAtMs = 0L
        lastSeenMs = 0L
    }

    companion object {
        /** A page sends a heartbeat every 5 s while visible. */
        const val HEARTBEAT_MS = 5_000L

        /** Two missed heartbeats plus one retry, so browser throttling does not drop a page. */
        const val WEB_TTL_MS = 20_000L

        /** A web editor must re-enter after this long, even while active. */
        const val WEB_HARD_CAP_MS = 30 * 60_000L
    }
}

/** Process-wide lease shared by web parent-center pages; the TV never takes it. */
object ParentEditorLease {
    private val engine = ParentEditorLeaseEngine()

    fun state(): ParentEditorLeaseEngine.State = engine.state()

    fun acquireWeb(): ParentEditorLeaseEngine.AcquireResult = engine.acquireWeb()

    fun heartbeat(tokenDigestHex: String): ParentEditorLeaseEngine.HeartbeatResult =
        engine.heartbeat(tokenDigestHex)

    fun releaseWeb(tokenDigestHex: String) = engine.releaseWeb(tokenDigestHex)

    fun webTokenValid(tokenDigestHex: String): Boolean = engine.webTokenValid(tokenDigestHex)

    fun invalidate(reason: String) = engine.invalidate(reason)

    fun invalidateWeb(reason: String) = engine.invalidateWeb(reason)
}
