package app.orchard.tv.quiz

import android.annotation.SuppressLint
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the single quiz session and the provider key at rest.
 *
 * API 23+ uses an Android Keystore AES/GCM key. API 21/22 has no usable
 * Keystore AES, so the record stays in app-private storage with an explicit
 * plaintext marker. None of this replaces transport security: the phone page is
 * still plain HTTP on a trusted LAN in v1.
 */
object QuizCrypto {
    private const val TAG = "QuizCrypto"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "planyourtv_quiz_v1"
    private const val ENCRYPTED_PREFIX = "enc:v1:"
    private const val PLAINTEXT_PREFIX = "plain:v1:"

    val isEncrypted: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    /** Returns null rather than storing an unencrypted record on API 23+. */
    fun encrypt(plain: String): String? {
        if (!isEncrypted) {
            return PLAINTEXT_PREFIX + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            ENCRYPTED_PREFIX + Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
        }.onFailure { Log.w(TAG, "quiz encryption failed", it) }.getOrNull()
    }

    /** Supports both v1 markers so an upgrade or a fallback device can still read its record. */
    fun decrypt(value: String): String? {
        return when {
            value.startsWith(PLAINTEXT_PREFIX) -> runCatching {
                String(Base64.decode(value.removePrefix(PLAINTEXT_PREFIX), Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrNull()
            value.startsWith(ENCRYPTED_PREFIX) -> runCatching {
                val bytes = Base64.decode(value.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
                if (bytes.size <= IV_BYTES) return null
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
                String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
            }.onFailure { Log.w(TAG, "quiz decryption failed", it) }.getOrNull()
            else -> null
        }
    }

    // Only reached through the isEncrypted branches above; lint cannot see
    // that relationship across the object-level guard.
    @SuppressLint("NewApi")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private const val IV_BYTES = 12
}
