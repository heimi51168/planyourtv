package app.orchard.tv.policy

import android.util.Base64
import com.planyourtv.policy.CommandSigningState
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies server-signed MQTT commands.
 *
 * Every device has its own broker account, but a device APK is open and modifiable: an attacker
 * who extracts one account can publish to that device's topic. The ECDSA signature over
 * `signedPayload` is the part such a publisher cannot produce. The decoded signed object, not
 * the outer envelope, is authoritative.
 *
 * No payload, key, signature, or command id is logged here. A failed parse or verification is
 * simply a rejected command; the caller decides whether to refresh the key.
 */
internal object MqttCommandVerifier {
    private const val ALGORITHM = "SHA256withECDSA"
    private const val MAX_REPLAY_ENTRIES = 512
    private const val MAX_VALIDITY_MS = 10 * 60_000L
    private const val MAX_CLOCK_AHEAD_MS = 2 * 60_000L
    private const val MAX_ISSUED_IN_FUTURE_MS = 2 * 60_000L

    /** commandId -> expiresAt. Entries are only added after the signature verifies. */
    private val seenCommandIds = object : LinkedHashMap<String, Long>(MAX_REPLAY_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_REPLAY_ENTRIES
    }

    /**
     * Returns the signed JSON object when the outer envelope carries a valid signature for
     * [deviceNo]. Any missing field, key mismatch, bad signature, wrong device, invalid validity
     * window, inconsistent outer/inner values, or replayed commandId returns null.
     */
    fun verify(json: JSONObject, deviceNo: String, state: CommandSigningState): JSONObject? {
        if (!state.enabled || state.keyId.isBlank() || state.publicKey.isBlank()) return null
        if (deviceNo.isBlank()) return null
        if (json.optString("signatureAlg", "") != ALGORITHM) return null
        if (json.optString("keyId", "") != state.keyId) return null
        val signedPayload = json.optString("signedPayload", "")
        val signature = json.optString("sig", "")
        if (signedPayload.isEmpty() || signature.isEmpty()) return null

        val outerCommandId = requireString(json, "commandId") ?: return null
        if (json.optString("deviceNo", "") != deviceNo) return null
        val outerIssuedAt = requireLong(json, "issuedAt") ?: return null
        val outerExpiresAt = requireLong(json, "expiresAt") ?: return null
        if (!withinValidity(outerIssuedAt, outerExpiresAt)) return null

        return try {
            val payloadBytes = Base64.decode(signedPayload, Base64.DEFAULT)
            val signatureBytes = Base64.decode(signature, Base64.DEFAULT)
            val publicKey = KeyFactory.getInstance("EC").generatePublic(
                X509EncodedKeySpec(Base64.decode(state.publicKey, Base64.DEFAULT)),
            )
            val verifier = Signature.getInstance(ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(payloadBytes)
            if (!verifier.verify(signatureBytes)) return null

            val payload = JSONObject(String(payloadBytes, Charsets.UTF_8))
            if (payload.optString("deviceNo", "") != deviceNo) return null
            if (requireString(payload, "commandId") != outerCommandId) return null
            if (requireLong(payload, "issuedAt") != outerIssuedAt) return null
            if (requireLong(payload, "expiresAt") != outerExpiresAt) return null

            synchronized(seenCommandIds) {
                if (seenCommandIds.containsKey(outerCommandId)) return null
                seenCommandIds[outerCommandId] = outerExpiresAt
            }
            payload
        } catch (_: Exception) {
            // Invalid Base64/key/signature all mean the same thing: do not trust this command.
            null
        }
    }

    private fun withinValidity(issuedAt: Long, expiresAt: Long): Boolean {
        val now = System.currentTimeMillis()
        if (issuedAt <= 0L || expiresAt <= issuedAt) return false
        if (expiresAt - issuedAt > MAX_VALIDITY_MS) return false
        if (expiresAt < now) return false
        if (issuedAt > now + MAX_ISSUED_IN_FUTURE_MS) return false
        if (now - issuedAt > MAX_VALIDITY_MS + MAX_CLOCK_AHEAD_MS) return false
        return true
    }

    private fun requireString(json: JSONObject, name: String): String? =
        json.optString(name, "").trim().takeIf { it.isNotEmpty() }

    /** org.json returns 0 for both a missing key and a non-numeric one; both are invalid here. */
    private fun requireLong(json: JSONObject, name: String): Long? =
        json.optLong(name, 0L).takeIf { it > 0L }
}
