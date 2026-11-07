package com.planyourtv.policy

import android.util.Log

/**
 * One logical parent PIN shared by the cloud and local engines.
 *
 * The two engines still verify against their own key pairs (so a mode switch cannot leave a
 * half-migrated runtime), but every write goes through this object and updates both copies.
 * Nothing here touches the network: the local mode remains fully offline.
 */
internal object TvPinStore {
    private const val TAG = "TvPinStore"
    const val DEFAULT_PIN = "123456"
    private val DEFAULT_PINS = setOf("123456", "666666")
    private const val PIN_LENGTH = 6

    fun isValid(pin: String?): Boolean =
        pin != null && pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun isDefault(pin: String): Boolean = pin in DEFAULT_PINS

    fun cloudConfigured(): Boolean =
        TvStore.isReady &&
            !TvStore.getString(PolicyKeys.PIN_SALT, null).isNullOrEmpty() &&
            !TvStore.getString(PolicyKeys.PIN_HASH, null).isNullOrEmpty()

    fun localConfigured(): Boolean =
        TvStore.isReady &&
            !TvStore.getString(PolicyKeys.LOCAL_PIN_SALT, null).isNullOrEmpty() &&
            !TvStore.getString(PolicyKeys.LOCAL_PIN_HASH, null).isNullOrEmpty()

    /**
     * Non-mutating first-run hint. Two fresh installs start with 123456; the parent center
     * should suggest changing it without incrementing a failed-attempt counter. The two
     * engines use different iteration counts, so check each store with its own verifier.
     */
    fun isDefaultConfigured(local: Boolean = false): Boolean {
        if (!TvStore.isReady) return false
        fun matches(saltKey: String, hashKey: String, iterations: Int): Boolean {
            val salt = TvStore.getString(saltKey, null) ?: return false
            val hash = TvStore.getString(hashKey, null) ?: return false
            return PolicyPin.verify(DEFAULT_PIN, salt, hash, iterations)
        }
        return if (local) {
            matches(PolicyKeys.LOCAL_PIN_SALT, PolicyKeys.LOCAL_PIN_HASH, PolicyPin.DEFAULT_ITERATIONS)
        } else {
            matches(
                PolicyKeys.PIN_SALT,
                PolicyKeys.PIN_HASH,
                TvStore.getInt(PolicyKeys.PIN_ITERATIONS, PolicyPin.DEFAULT_ITERATIONS),
            )
        }
    }

    /** Writes the same PIN into both stores and clears both failure counters. */
    fun set(pin: String) {
        require(isValid(pin)) { "PIN must be six digits" }
        if (!TvStore.isReady) return

        val cloudSalt = PolicyPin.newSalt()
        TvStore.putString(PolicyKeys.PIN_SALT, cloudSalt)
        TvStore.putString(PolicyKeys.PIN_HASH, PolicyPin.hash(pin, cloudSalt))
        TvStore.putInt(PolicyKeys.PIN_ITERATIONS, PolicyPin.DEFAULT_ITERATIONS)
        TvStore.putInt(PolicyKeys.PIN_FAILED, 0)
        TvStore.putLong(PolicyKeys.PIN_LOCKOUT, 0L)

        val localSalt = PolicyPin.newSalt()
        TvStore.putString(PolicyKeys.LOCAL_PIN_SALT, localSalt)
        TvStore.putString(PolicyKeys.LOCAL_PIN_HASH, PolicyPin.hash(pin, localSalt))
        TvStore.putInt(PolicyKeys.LOCAL_PIN_FAILED, 0)
        TvStore.putLong(PolicyKeys.LOCAL_PIN_LOCKOUT, 0L)

        TvStore.sync()
    }

    /**
     * Fresh installs start with 123456 so the first unlock always has a way in. The UI still
     * asks the parent to change it; this method only writes when neither store has a PIN.
     */
    fun ensureDefaultIfUnset(): Boolean {
        if (!TvStore.isReady) return false
        if (cloudConfigured() || localConfigured()) return false
        set(DEFAULT_PIN)
        Log.i(TAG, "initialized default PIN")
        return true
    }

    /**
     * Old builds may have only one copy. Copy the configured pair to the missing side.
     *
     * The cloud store may carry a legacy 100k-iteration hash that the local verifier (10k) cannot
     * read. In that case we intentionally skip the migration; the next explicit PIN write (phone,
     * parent center, setup) writes both sides with the current iteration count.
     */
    fun migrateIfNeeded() {
        if (!TvStore.isReady) return
        val cloud = cloudConfigured()
        val local = localConfigured()
        if (cloud == local) return

        if (cloud) {
            val iterations = TvStore.getInt(
                PolicyKeys.PIN_ITERATIONS,
                PolicyPin.DEFAULT_ITERATIONS,
            )
            if (iterations != PolicyPin.DEFAULT_ITERATIONS) {
                Log.i(TAG, "skip legacy cloud PIN migration (iterations=$iterations)")
                return
            }
            val salt = TvStore.getString(PolicyKeys.PIN_SALT, null) ?: return
            val hash = TvStore.getString(PolicyKeys.PIN_HASH, null) ?: return
            TvStore.putString(PolicyKeys.LOCAL_PIN_SALT, salt)
            TvStore.putString(PolicyKeys.LOCAL_PIN_HASH, hash)
            TvStore.putInt(PolicyKeys.LOCAL_PIN_FAILED, 0)
            TvStore.putLong(PolicyKeys.LOCAL_PIN_LOCKOUT, 0L)
        } else {
            val salt = TvStore.getString(PolicyKeys.LOCAL_PIN_SALT, null) ?: return
            val hash = TvStore.getString(PolicyKeys.LOCAL_PIN_HASH, null) ?: return
            TvStore.putString(PolicyKeys.PIN_SALT, salt)
            TvStore.putString(PolicyKeys.PIN_HASH, hash)
            TvStore.putInt(PolicyKeys.PIN_ITERATIONS, PolicyPin.DEFAULT_ITERATIONS)
            TvStore.putInt(PolicyKeys.PIN_FAILED, 0)
            TvStore.putLong(PolicyKeys.PIN_LOCKOUT, 0L)
        }
        TvStore.sync()
        Log.i(TAG, "migrated PIN store cloud=$cloud local=$local")
    }

    /**
     * A local change while offline cannot be pushed later without retaining the clear PIN, so
     * this flag only records that the parent must re-enter it once when the TV is online again.
     */
    val pendingSync: Boolean
        get() = TvStore.isReady && TvStore.getBoolean(PolicyKeys.PENDING_PASSWORD_SYNC, false)

    fun markPendingSync() {
        if (!TvStore.isReady) return
        TvStore.putBoolean(PolicyKeys.PENDING_PASSWORD_SYNC, true)
        TvStore.sync()
    }

    fun clearPendingSync() {
        if (!TvStore.isReady) return
        TvStore.putBoolean(PolicyKeys.PENDING_PASSWORD_SYNC, false)
        TvStore.sync()
    }
}
