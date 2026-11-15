package app.orchard.tv.policy.web

import java.security.MessageDigest
import java.security.SecureRandom

/** Token and digest primitives shared by the web access gate and the editor lease. */
internal object ParentWebCrypto {
    private val TOKEN_RE = Regex("[0-9a-f]{32}")
    private val random = SecureRandom()

    fun randomToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isToken(value: String?): Boolean = value != null && TOKEN_RE.matches(value)

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun digestEquals(left: String?, right: String?): Boolean {
        if (left == null || right == null) return false
        return MessageDigest.isEqual(
            left.toByteArray(Charsets.UTF_8),
            right.toByteArray(Charsets.UTF_8),
        )
    }
}
