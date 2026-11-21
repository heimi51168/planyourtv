package com.planyourtv.policy

/**
 * The 6-digit password a child enters on the TV lock screen to switch to their own member.
 *
 * This is deliberately separate from the parent PIN: it gates member switching only and is
 * never accepted as parent authorization. Members migrated from older builds have no stored
 * credential and use [INITIAL]; a custom password is stored as a salted PBKDF2 hash.
 */
object MemberPin {
    const val LENGTH = 6
    const val INITIAL = "123456"
    const val MAX_FAILED = 5
    const val LOCKOUT_MS = 30_000L

    data class Credential(
        val salt: String,
        val hash: String,
    )

    fun isValid(pin: String): Boolean =
        pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): String = PolicyPin.newSalt()

    fun hash(pin: String, salt: String): String? =
        if (isValid(pin) && salt.isNotEmpty()) {
            PolicyPin.hash(pin, salt, PolicyPin.DEFAULT_ITERATIONS)
        } else {
            null
        }

    /** Builds a fresh salt/hash pair, or null when the input is not six digits. */
    fun create(pin: String): Credential? {
        if (!isValid(pin)) return null
        val salt = newSalt()
        val hash = hash(pin, salt) ?: return null
        return Credential(salt, hash)
    }

    /**
     * Both null means the member still uses [INITIAL]. A half-written pair is corruption and
     * must never silently fall back to the initial password.
     */
    fun verify(pin: String, salt: String?, hash: String?): Boolean {
        if (!isValid(pin)) return false
        if (salt == null && hash == null) return pin == INITIAL
        if (salt == null || hash == null) return false
        return PolicyPin.verify(pin, salt, hash, PolicyPin.DEFAULT_ITERATIONS)
    }

    internal fun isStoredCredential(salt: String?, hash: String?): Boolean {
        if (salt == null && hash == null) return true
        if (salt == null || hash == null) return false
        return salt.length == SALT_HEX_LENGTH &&
            hash.length == HASH_HEX_LENGTH &&
            salt.all { it.isHexDigit() } &&
            hash.all { it.isHexDigit() }
    }

    private const val SALT_HEX_LENGTH = 32
    private const val HASH_HEX_LENGTH = 64

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f'
}
