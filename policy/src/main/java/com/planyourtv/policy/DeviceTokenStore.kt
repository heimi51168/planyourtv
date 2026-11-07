package com.planyourtv.policy

import android.annotation.TargetApi
import android.content.Context
import android.content.SharedPreferences
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
 * Keystore-backed storage for the TV Link device token.
 *
 * The token has to be readable as plaintext for HTTP and MQTT, so it cannot live only in
 * hardware. Instead the AES key stays in Android Keystore and only the ciphertext and IV are
 * persisted in a private SharedPreferences file. Extracting the app data then yields no
 * usable bearer.
 *
 * [open] returns null on API 21-22 (no reliable AES/GCM Keystore) and on TVs whose Keystore
 * implementation fails a round-trip self-test. In both cases [TvStore] keeps using its legacy
 * storage instead of losing the binding.
 */
@TargetApi(Build.VERSION_CODES.M)
internal class DeviceTokenStore private constructor(
    private val prefs: SharedPreferences,
) {
    private val lock = Any()

    @Volatile
    private var key: SecretKey? = null

    @Volatile
    private var loaded = false

    @Volatile
    private var cached: String? = null

    /** Returns the decrypted token, or null when nothing (readable) is stored. */
    fun read(): String? {
        if (loaded) return cached
        synchronized(lock) {
            if (!loaded) {
                cached = decodeStored()
                loaded = true
            }
            return cached
        }
    }

    /** Encrypts, commits synchronously, then verifies by decrypting the committed value. */
    fun write(token: String): Boolean {
        if (token.isEmpty()) return false
        synchronized(lock) {
            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                val body = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
                val committed = prefs.edit()
                    .putString(KEY_CIPHERTEXT, Base64.encodeToString(body, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .commit()
                if (!committed) {
                    Log.w(TAG, "could not commit device token")
                    false
                } else {
                    val verified = decodeStored() == token
                    if (verified) {
                        cached = token
                        loaded = true
                    } else {
                        Log.w(TAG, "device token write verification failed")
                    }
                    verified
                }
            } catch (error: Throwable) {
                Log.w(TAG, "could not encrypt device token", error)
                false
            }
        }
    }

    /** Removes the ciphertext and the key. Used only for an explicit unbind. */
    fun clear() {
        synchronized(lock) {
            prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).commit()
            cached = null
            loaded = true
            runCatching {
                val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
            }.onFailure { Log.w(TAG, "could not delete device token key", it) }
            key = null
        }
    }

    private fun decodeStored(): String? {
        if (!prefs.contains(KEY_CIPHERTEXT) && !prefs.contains(KEY_IV)) return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val body = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(body, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (error: Throwable) {
            // A restored prefs file without the Keystore key, or an invalidated key.
            // The caller falls back to the legacy mirror when one exists.
            Log.w(TAG, "could not decrypt device token", error)
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        key?.let { return it }
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            key = existing
            return existing
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // The token is needed in the background, so no user-authentication gate.
                // This also avoids invalidation when the TV's lock screen changes.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey().also { key = it }
    }

    /** Encrypt/decrypt a probe so a broken vendor Keystore falls back before any token write. */
    private fun selfTest(): Boolean {
        val probe = "planyourtv-device-token-probe"
        val encrypt = Cipher.getInstance(TRANSFORMATION)
        encrypt.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val body = encrypt.doFinal(probe.toByteArray(Charsets.UTF_8))
        val decrypt = Cipher.getInstance(TRANSFORMATION)
        decrypt.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, encrypt.iv),
        )
        return String(decrypt.doFinal(body), Charsets.UTF_8) == probe
    }

    companion object {
        private const val TAG = "DeviceTokenStore"
        private const val PREFS = "policy_secure_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "planyourtv_device_token_v1"
        private const val KEY_CIPHERTEXT = "device_token_ciphertext_v1"
        private const val KEY_IV = "device_token_iv_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        fun open(context: Context): DeviceTokenStore? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
            return try {
                val prefs = context.applicationContext.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE,
                )
                val store = DeviceTokenStore(prefs)
                if (store.selfTest()) store else null
            } catch (error: Throwable) {
                Log.w(TAG, "Keystore unavailable; keeping legacy token storage", error)
                null
            }
        }
    }
}
