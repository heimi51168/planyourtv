package com.planyourtv.policy

/**
 * Local-mode parent PIN rules. The local and cloud engines keep separate storage pairs for
 * migration safety, but TvPinStore writes the same logical PIN into both.
 */
object LocalPin {
    const val LENGTH = 6
    const val MAX_FAILED = 5
    const val LOCKOUT_MS = 30_000L

    fun isValid(pin: String): Boolean =
        pin.length == LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): String = PolicyPin.newSalt()

    fun hash(pin: String, salt: String): String =
        PolicyPin.hash(pin, salt, PolicyPin.DEFAULT_ITERATIONS)

    fun verify(pin: String, salt: String, expectedHash: String): Boolean =
        isValid(pin) && PolicyPin.verify(pin, salt, expectedHash, PolicyPin.DEFAULT_ITERATIONS)
}

/**
 * One-time offline recovery code. Only its PBKDF2 hash is stored on the TV after setup; the
 * clear code is shown to the parent once and never written to disk.
 */
object LocalRecovery {
    const val CODE_LENGTH = 12
    // Digits only: a parent can enter it with the TV's D-pad, without a soft keyboard.
    private const val ALPHABET = "0123456789"

    /** Generates a human-transcribable code in the form XXXX-XXXX-XXXX. */
    fun newCode(): String {
        val random = java.security.SecureRandom()
        val raw = buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        return format(raw)
    }

    /** Uppercases and removes separators; returns null when the result is not a valid code. */
    fun normalize(input: String): String? {
        val raw = input.uppercase().filter { it.isLetterOrDigit() }
        if (raw.length != CODE_LENGTH || raw.any { it !in ALPHABET }) return null
        return raw
    }

    fun format(raw: String): String {
        val clean = raw.filter { it.isLetterOrDigit() }.uppercase()
        return clean.chunked(4).joinToString("-")
    }

    fun newSalt(): String = PolicyPin.newSalt()

    fun hash(code: String, salt: String): String? {
        val normalized = normalize(code) ?: return null
        return PolicyPin.hash(normalized, salt, PolicyPin.DEFAULT_ITERATIONS)
    }

    fun verify(code: String, salt: String, expectedHash: String): Boolean {
        val normalized = normalize(code) ?: return false
        if (salt.isEmpty() || expectedHash.isEmpty()) return false
        return PolicyPin.verify(normalized, salt, expectedHash, PolicyPin.DEFAULT_ITERATIONS)
    }
}
