package com.planyourtv.policy

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.tencent.mmkv.MMKV

/**
 * Product-owned storage. Aerial keeps its own SharedPreferences; this store is
 * only for the :policy module, so an Aerial upgrade never has to know about it.
 *
 * Initialization needs a Context once. All callers then use put/get without a
 * Context. Before the user is unlocked (LOCKED_BOOT_COMPLETED) MMKV is not
 * available; init returns false and PolicyRuntime starts again after unlock.
 *
 * The existing "policy" SharedPreferences file is migrated once. If MMKV cannot
 * initialize on an old TV, the same facade falls back to that file so a device
 * does not become unusable.
 */
object TvStore {
    private const val TAG = "TvStore"
    private const val MMKV_ID = "uxb_policy"
    private const val LEGACY_PREFS = "policy"
    private const val KEY_MIGRATED = "__migrated_from_sp_v1"

    private var mmkv: MMKV? = null
    private var legacy: SharedPreferences? = null
    private var initialized = false
    private var secureTokens: DeviceTokenStore? = null

    val isReady: Boolean
        get() = initialized

    /** Debug aid: which backend this process ended up using. */
    val backend: String
        get() = when {
            mmkv != null -> "mmkv"
            legacy != null -> "shared_prefs"
            else -> "none"
        }

    /** Idempotent; returns false while credential-protected storage is unavailable. */
    @Synchronized
    fun init(context: Context): Boolean {
        if (initialized) return true
        if (!isUserUnlocked(context)) return false
        val app = context.applicationContext
        val old = runCatching {
            app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        }.getOrNull()
        val ready = runCatching {
            MMKV.initialize(app)
            val kv = MMKV.mmkvWithID(MMKV_ID, MMKV.SINGLE_PROCESS_MODE)
            migrateLegacy(old, kv)
            mmkv = kv
            legacy = null
            initialized = true
            true
        }.getOrElse { error ->
            // An old TV may reject the native library. Keep the product usable
            // with the exact storage it used before.
            Log.w(TAG, "MMKV unavailable; falling back to SharedPreferences", error)
            legacy = old
            initialized = old != null
            initialized
        }
        // Keystore availability is independent of MMKV. Keep it null on API 21-22 or
        // when a vendor Keystore fails the self-test; getDeviceToken then uses the
        // legacy value exactly as before.
        secureTokens = DeviceTokenStore.open(app)
        return ready
    }

    fun getString(key: String, default: String? = null): String? =
        mmkv?.decodeString(key, default) ?: legacy?.getString(key, default) ?: default

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        mmkv?.decodeBool(key, default) ?: legacy?.getBoolean(key, default) ?: default

    fun getInt(key: String, default: Int = 0): Int =
        mmkv?.decodeInt(key, default) ?: legacy?.getInt(key, default) ?: default

    fun getLong(key: String, default: Long = 0L): Long =
        mmkv?.decodeLong(key, default) ?: legacy?.getLong(key, default) ?: default

    fun getStringSet(key: String, default: Set<String> = emptySet()): Set<String> =
        mmkv?.decodeStringSet(key, default) ?: legacy?.getStringSet(key, default) ?: default

    fun putString(key: String, value: String) {
        val kv = mmkv
        if (kv != null) {
            if (!kv.encode(key, value)) Log.w(TAG, "encode failed: $key")
        } else {
            legacy?.edit()?.putString(key, value)?.apply()
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        val kv = mmkv
        if (kv != null) {
            if (!kv.encode(key, value)) Log.w(TAG, "encode failed: $key")
        } else {
            legacy?.edit()?.putBoolean(key, value)?.apply()
        }
    }

    fun putInt(key: String, value: Int) {
        val kv = mmkv
        if (kv != null) {
            if (!kv.encode(key, value)) Log.w(TAG, "encode failed: $key")
        } else {
            legacy?.edit()?.putInt(key, value)?.apply()
        }
    }

    fun putLong(key: String, value: Long) {
        val kv = mmkv
        if (kv != null) {
            if (!kv.encode(key, value)) Log.w(TAG, "encode failed: $key")
        } else {
            legacy?.edit()?.putLong(key, value)?.apply()
        }
    }

    fun putStringSet(key: String, value: Set<String>) {
        val kv = mmkv
        if (kv != null) {
            if (!kv.encode(key, value)) Log.w(TAG, "encode failed: $key")
        } else {
            legacy?.edit()?.putStringSet(key, value)?.apply()
        }
    }

    fun remove(key: String) {
        mmkv?.removeValueForKey(key) ?: legacy?.edit()?.remove(key)?.apply()
    }

    /**
     * Reads the TV Link token, migrating an existing plaintext value into Keystore on first
     * use. The legacy value is intentionally left in place for this release: rolling back to
     * the previous APK must still find the same binding. Fresh enrollments never create it.
     */
    @Synchronized
    fun getDeviceToken(): String? {
        val legacyToken = getString(PolicyKeys.DEVICE_TOKEN, null)
        val secure = secureTokens ?: return legacyToken
        val stored = secure.read()
        if (stored != null) {
            // Keystore is authoritative. If an older value is still mirrored, refresh it so
            // a rollback does not read a stale token after a rotation.
            if (!legacyToken.isNullOrEmpty() && legacyToken != stored) {
                putString(PolicyKeys.DEVICE_TOKEN, stored)
                sync()
            }
            return stored
        }
        if (legacyToken.isNullOrEmpty() || !legacyToken.startsWith("dt_")) return legacyToken
        if (!secure.write(legacyToken)) {
            Log.w(TAG, "Keystore token migration failed; keeping legacy value")
        }
        return legacyToken
    }

    /**
     * Writes the token through Keystore when available. Devices that already carry the
     * legacy plaintext value keep a mirror for one release; new enrollments are secure-only.
     * If Keystore write fails, fall back to the legacy store instead of losing the binding.
     */
    @Synchronized
    fun putDeviceToken(token: String): Boolean {
        if (!initialized) return false
        val clean = token.trim()
        if (clean.isEmpty()) return false
        val hadLegacyMirror = !getString(PolicyKeys.DEVICE_TOKEN, null).isNullOrEmpty()
        val secure = secureTokens
        if (secure != null && secure.write(clean)) {
            if (hadLegacyMirror) {
                putString(PolicyKeys.DEVICE_TOKEN, clean)
                sync()
            }
            return true
        }
        putString(PolicyKeys.DEVICE_TOKEN, clean)
        sync()
        return getString(PolicyKeys.DEVICE_TOKEN, null) == clean
    }

    /** Removes both copies; used for an explicit unbind, not for a normal app update. */
    @Synchronized
    fun clearDeviceToken() {
        secureTokens?.clear()
        remove(PolicyKeys.DEVICE_TOKEN)
        sync()
    }

    /**
     * A synchronous write for state that is the source of truth after a power
     * loss (a quiz challenge or its daily counter). MMKV is already synchronous;
     * the SharedPreferences fallback must use commit() rather than apply().
     */
    fun putStringDurable(key: String, value: String): Boolean {
        val kv = mmkv
        if (kv != null) {
            val saved = kv.encode(key, value)
            if (saved) kv.sync() else Log.w(TAG, "encode failed: $key")
            return saved
        }
        return legacy?.edit()?.putString(key, value)?.commit() ?: false
    }

    fun removeDurable(key: String): Boolean {
        val kv = mmkv
        if (kv != null) {
            kv.removeValueForKey(key)
            kv.sync()
            return true
        }
        return legacy?.edit()?.remove(key)?.commit() ?: false
    }

    /** Flush identity/PIN changes that must survive an abrupt power loss. */
    fun sync() {
        mmkv?.sync()
    }

    private fun migrateLegacy(old: SharedPreferences?, kv: MMKV) {
        if (kv.decodeBool(KEY_MIGRATED, false)) return
        old?.all?.forEach { (key, value) ->
            when (value) {
                is String -> kv.encode(key, value)
                is Boolean -> kv.encode(key, value)
                is Int -> kv.encode(key, value)
                is Long -> kv.encode(key, value)
                is Float -> kv.encode(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    kv.encode(key, value as Set<String>)
                }
            }
        }
        kv.encode(KEY_MIGRATED, true)
        kv.sync()
    }

    private fun isUserUnlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        return runCatching {
            (context.getSystemService("user") as? UserManager)?.isUserUnlocked ?: true
        }.getOrDefault(true)
    }
}
