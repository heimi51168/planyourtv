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
 * This access only configures the quiz. It cannot unlock the TV, pause the
 * guard, or change system settings.
 */
object QuizAccess {
    private const val ADMIN_TTL_MS = 10 * 60 * 1000L
    private const val MAX_PIN_FAILURES = 5
    private const val PIN_LOCK_MS = 10 * 60 * 1000L
    private val TOKEN_RE = Regex("[0-9a-f]{32}")

    sealed interface AuthResult {
        data class Ok(val adminToken: String, val expiresAt: Long) : AuthResult
        data object PinInvalid : AuthResult
        data object PinNotSet : AuthResult
        data class PinLocked(val retryAfterSeconds: Long) : AuthResult
        data object ParentInUse : AuthResult
    }

    private data class Proof(val digest: ByteArray, val expiresAt: Long)

    private var admin: Proof? = null
    private var pinFailures = 0
    private var pinLockedUntil = 0L
    private val random = SecureRandom()

    @Synchronized
    fun authenticate(pin: String, nowMs: Long = System.currentTimeMillis()): AuthResult {
        if (pinLockedUntil > nowMs) {
            return AuthResult.PinLocked(((pinLockedUntil - nowMs + 999) / 1000).coerceAtLeast(1))
        }
        return when (PolicyModeCoordinator.verifyPin(pin)) {
            PinResult.OK -> {
                pinFailures = 0
                // One parent edits at a time. The first phone keeps the slot
                // until it logs out or the local day ends; a second phone must
                // not silently replace it in the middle of a change.
                if (hasActiveSession(nowMs)) return AuthResult.ParentInUse
                val token = randomToken()
                // Remember the phone for the rest of the local day. At local
                // midnight the parent must enter the PIN again.
                val wantedExpiry = nextLocalMidnight(nowMs)
                // Persisting the digest is what lets the phone skip the PIN after
                // a TV restart. When storage is unavailable, keep the old short
                // in-memory proof so this session still works.
                val persisted = QuizSessionStore.saveWebToken(sha256Hex(token), wantedExpiry, nowMs)
                val expiresAt = if (persisted) wantedExpiry else minOf(nowMs + ADMIN_TTL_MS, wantedExpiry)
                admin = Proof(sha256(token), expiresAt)
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

    /** True while a parent phone still owns the settings page. */
    @Synchronized
    fun hasActiveSession(nowMs: Long = System.currentTimeMillis()): Boolean {
        val current = admin
        if (current != null) {
            if (current.expiresAt <= nowMs) {
                admin = null
            } else {
                return true
            }
        }
        return QuizSessionStore.activeWebToken(nowMs) != null
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
            return MessageDigest.isEqual(
                remembered.digest.toByteArray(),
                sha256Hex(token).toByteArray(),
            )
        }
        // Storage was unavailable when the parent entered; keep the old short
        // in-memory proof so this session still works.
        return admin?.let { MessageDigest.isEqual(it.digest, sha256(token)) } == true
    }

    @Synchronized
    fun logout(token: String) {
        if (!TOKEN_RE.matches(token)) return
        val digest = sha256Hex(token)
        val active = QuizSessionStore.activeWebToken()
        val current = admin
        val ownsSlot = active?.digest == digest ||
            (current != null && MessageDigest.isEqual(current.digest, sha256(token)))
        if (!ownsSlot) return
        admin = null
        QuizSessionStore.removeWebToken(digest)
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
