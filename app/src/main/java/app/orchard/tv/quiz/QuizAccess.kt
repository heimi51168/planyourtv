package app.orchard.tv.quiz

import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.policy.PinResult
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId

/**
 * Web authorization for the quiz settings page.
 *
 * The setup URL is fixed for this TV. A parent enters the PIN once; the TV then
 * remembers the phone for the rest of the local day by storing only the SHA-256
 * of a random bearer token. The phone keeps the token in local storage and
 * reopens the page without scanning or entering the PIN again until the next
 * local midnight. [logout] on the page removes that token.
 *
 * Presence is separate from the remembered token: the page sends a heartbeat
 * only while it is visible, and the settings slot counts as occupied only while
 * that heartbeat is recent. Closing the page releases the slot immediately when
 * the browser delivers the unload request; when it does not (for example WeChat
 * kills the process) the idle timeout releases it. A phone that comes back
 * while nobody took over keeps its remembered token, but the token stops
 * working as soon as another phone takes the slot.
 *
 * This access only configures the quiz. It cannot unlock the TV, pause the
 * guard, or change system settings.
 */
object QuizAccess {
    private const val ADMIN_TTL_MS = 10 * 60 * 1000L
    private const val MAX_PIN_FAILURES = 5
    private const val PIN_LOCK_MS = 10 * 60 * 1000L

    /**
     * No heartbeat for this long means the parent left; another phone may enter.
     * Keep this comfortably above the page heartbeat (currently 3s) so a
     * dropped request does not release an active page.
     */
    internal const val SESSION_IDLE_MS = 10 * 1000L

    private val TOKEN_RE = Regex("[0-9a-f]{32}")

    sealed interface AuthResult {
        data class Ok(val adminToken: String, val expiresAt: Long) : AuthResult
        data object PinInvalid : AuthResult
        data object PinNotSet : AuthResult
        data class PinLocked(val retryAfterSeconds: Long) : AuthResult
        data object ParentInUse : AuthResult
    }

    private data class Proof(val digestHex: String, val expiresAt: Long)

    private var admin: Proof? = null
    private var pinFailures = 0
    private var pinLockedUntil = 0L
    private val random = SecureRandom()

    // Presence of the remembered token. Kept in memory on purpose: after a TV
    // restart nobody is actively editing until the page sends its next
    // heartbeat, so a stale process-local value must not block the slot.
    private var activeDigest: String? = null
    private var lastSeenAt = 0L

    @Synchronized
    fun authenticate(
        pin: String,
        nowMs: Long = System.currentTimeMillis(),
        takeover: Boolean = false,
    ): AuthResult {
        if (pinLockedUntil > nowMs) {
            return AuthResult.PinLocked(((pinLockedUntil - nowMs + 999) / 1000).coerceAtLeast(1))
        }
        return when (PolicyModeCoordinator.verifyPin(pin)) {
            PinResult.OK -> {
                pinFailures = 0
                // One parent edits at a time. A second phone can take over only
                // when it explicitly asks (the PIN proves it is a parent), or
                // when the first phone stopped sending heartbeats. Replacing
                // the remembered token is what disconnects the previous phone.
                if (!takeover && hasActiveSession(nowMs)) return AuthResult.ParentInUse
                val token = randomToken()
                val digestHex = sha256Hex(token)
                // Remember the phone for the rest of the local day. At local
                // midnight the parent must enter the PIN again.
                val wantedExpiry = nextLocalMidnight(nowMs)
                // Persisting the digest is what lets the phone skip the PIN after
                // a TV restart. When storage is unavailable, keep the old short
                // in-memory proof so this session still works.
                val persisted = QuizSessionStore.saveWebToken(digestHex, wantedExpiry, nowMs)
                val expiresAt = if (persisted) wantedExpiry else minOf(nowMs + ADMIN_TTL_MS, wantedExpiry)
                admin = Proof(digestHex, expiresAt)
                activeDigest = digestHex
                lastSeenAt = nowMs
                AuthResult.Ok(token, expiresAt)
            }
            PinResult.NOT_SET -> AuthResult.PinNotSet
            PinResult.WRONG -> {
                pinFailures++
                if (pinFailures >= MAX_PIN_FAILURES) {
                    pinFailures = 0
                    pinLockedUntil = nowMs + PIN_LOCK_MS
                    AuthResult.PinLocked(PIN_LOCK_MS / 1000)
                } else {
                    AuthResult.PinInvalid
                }
            }
            PinResult.LOCKED_OUT -> {
                pinLockedUntil = nowMs + PIN_LOCK_MS
                AuthResult.PinLocked(PIN_LOCK_MS / 1000)
            }
        }
    }

    /** True while a parent phone is actively using the settings page. */
    @Synchronized
    fun hasActiveSession(nowMs: Long = System.currentTimeMillis()): Boolean {
        val remembered = QuizSessionStore.activeWebToken(nowMs)
        val digest = if (remembered != null) {
            remembered.digest
        } else {
            val proof = admin
            if (proof != null && proof.expiresAt <= nowMs) {
                admin = null
                null
            } else {
                proof?.digestHex
            }
        }
        if (digest == null) return false
        if (activeDigest != digest) return false
        return nowMs - lastSeenAt < SESSION_IDLE_MS
    }

    @Synchronized
    fun adminValid(token: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!TOKEN_RE.matches(token)) return false
        val current = admin
        if (current != null && current.expiresAt <= nowMs) admin = null
        val remembered = QuizSessionStore.activeWebToken(nowMs)
        if (remembered != null) {
            // Single-owner semantics: after a restart only the remembered
            // token is accepted, even if this process minted an older one.
            // A valid request also resumes a dormant session if nobody took the
            // slot while this phone was away.
            if (!MessageDigest.isEqual(
                    remembered.digest.toByteArray(),
                    sha256Hex(token).toByteArray(),
                )
            ) {
                return false
            }
            activeDigest = remembered.digest
            lastSeenAt = nowMs
            return true
        }
        // Storage was unavailable when the parent entered; keep the old short
        // in-memory proof so this session still works.
        val proof = admin ?: return false
        if (!MessageDigest.isEqual(proof.digestHex.toByteArray(), sha256Hex(token).toByteArray())) {
            return false
        }
        activeDigest = proof.digestHex
        lastSeenAt = nowMs
        return true
    }

    /**
     * Marks the current phone as inactive without forgetting it. The page calls
     * this when it is unloaded; the phone resumes without the PIN if no other
     * phone takes the slot. Explicit [logout] removes the token instead.
     */
    @Synchronized
    fun release(token: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!TOKEN_RE.matches(token)) return false
        val digestHex = sha256Hex(token)
        if (!ownsSlot(digestHex, nowMs)) return false
        activeDigest = digestHex
        lastSeenAt = 0L
        return true
    }

    @Synchronized
    fun logout(token: String, nowMs: Long = System.currentTimeMillis()) {
        if (!TOKEN_RE.matches(token)) return
        val digest = sha256Hex(token)
        if (!ownsSlot(digest, nowMs)) return
        admin = null
        activeDigest = null
        lastSeenAt = 0L
        QuizSessionStore.removeWebToken(digest, nowMs)
    }

    private fun ownsSlot(digestHex: String, nowMs: Long): Boolean {
        val remembered = QuizSessionStore.activeWebToken(nowMs)
        if (remembered != null) return remembered.digest == digestHex
        val proof = admin ?: return false
        if (proof.expiresAt <= nowMs) return false
        return MessageDigest.isEqual(proof.digestHex.toByteArray(), digestHex.toByteArray())
    }

    /** Stable, non-reversible job-owner key; the admin token itself is never stored in a job. */
    fun fingerprint(token: String): String =
        sha256Hex(token)

    /**
     * First instant of the next local day. Uses the TV timezone and java.time,
     * so DST changes shorten or lengthen the day correctly.
     */
    internal fun nextLocalMidnight(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs)
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    private fun randomToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    private fun sha256Hex(value: String): String =
        sha256(value).joinToString("") { "%02x".format(it) }
}
