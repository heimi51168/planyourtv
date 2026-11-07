package com.planyourtv.policy

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Parent PIN hashing. Kept pure so the policy tests do not need Android.
 *
 * Phase 0 uses PBKDF2-HMAC-SHA1, which exists on every Android version Aerial
 * supports (minSdk 21). Before shipping, move the verifier key into Android
 * Keystore and add the binding/account recovery path.
 */
internal object PolicyPin {
    private const val SALT_BYTES = 16
    // Kept low enough that a 6-digit PIN check does not freeze a TV's main
    // thread for seconds. Move verification off the main thread before raising
    // this, or use Keystore-backed verification for the production build.
    const val DEFAULT_ITERATIONS = 10_000
    private const val KEY_BITS = 256

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(pin: String, salt: String, iterations: Int = DEFAULT_ITERATIONS): String {
        require(pin.isNotEmpty())
        require(salt.isNotEmpty())
        val spec = PBEKeySpec(pin.toCharArray(), salt.hexToBytes(), iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec)
                .encoded
                .toHex()
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: String, expectedHash: String, iterations: Int = DEFAULT_ITERATIONS): Boolean {
        if (salt.isEmpty() || expectedHash.isEmpty()) return false
        val actual = runCatching { hash(pin, salt, iterations) }.getOrNull() ?: return false
        return MessageDigest.isEqual(actual.toByteArray(Charsets.UTF_8), expectedHash.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "invalid hex" }
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
