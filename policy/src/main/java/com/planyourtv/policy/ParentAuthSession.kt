package com.planyourtv.policy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PROOF_TTL_MS = 10 * 60 * 1000L

/**
 * Proof that a parent verified the PIN during the current temporary unlock.
 *
 * The proof is shared by the lock screen, the parent center and every action that only needs
 * "the parent is already here" instead of asking for the same PIN again. The plaintext PIN is
 * kept in memory so actions that must send it (disable the guard, complete the sync) do not
 * prompt again; only the expiry timestamp is written to MMKV. A process restart can therefore
 * restore the "already verified" state without persisting the secret, and the coordinator
 * invalidates the restored state unless the live guard is still temporarily unlocked.
 *
 * Screen-off, guard resume, mode switch, unbind and expiry invalidate the proof.
 */
class ParentAuthSession(
    private val now: () -> Long = { System.currentTimeMillis() },
    private val loadExpiresAt: () -> Long = {
        TvStore.getLong(PolicyKeys.PARENT_AUTH_EXPIRES_AT, 0L)
    },
    private val saveExpiresAt: (Long) -> Unit = { value ->
        TvStore.putLong(PolicyKeys.PARENT_AUTH_EXPIRES_AT, value)
        TvStore.sync()
    },
    private val clearExpiresAt: () -> Unit = {
        TvStore.remove(PolicyKeys.PARENT_AUTH_EXPIRES_AT)
        TvStore.sync()
    },
) {
    private val _authenticated = MutableStateFlow(false)
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    @Volatile
    private var expiresAtMs = 0L

    /**
     * The PIN that created the proof. Sensitive parent-center actions need the value itself,
     * not just a boolean; keeping it in memory is what avoids a second PIN prompt. It is never
     * written to disk, so a restored proof has no [verifiedPin].
     */
    @Volatile
    var verifiedPin: String? = null
        private set

    init {
        reloadFromStore()
    }

    /**
     * Restores the persisted expiry once [TvStore] is actually available. The session may be
     * constructed before the store is initialized (the coordinator object is touched first);
     * that first read returns 0 and must not be treated as "no proof". A proof already created
     * in this process is never overwritten by a stale on-disk value.
     */
    fun reloadFromStore() {
        if (_authenticated.value) return
        val stored = loadExpiresAt()
        if (stored > now()) {
            expiresAtMs = stored
            _authenticated.value = true
        } else if (stored != 0L) {
            clearExpiresAt()
        }
    }

    /** Only a successful lock-screen PIN counts; the parent center reports its own proof. */
    fun onLockScreenPin(pin: String, result: PinResult) {
        if (result == PinResult.OK) onParentVerified(pin)
    }

    /** The parent center verified this PIN; share it with temporary-unlock actions. */
    fun onParentVerified(pin: String) {
        if (pin.length != 6 || pin.any { it !in '0'..'9' }) return
        verifiedPin = pin
        expiresAtMs = now() + PROOF_TTL_MS
        saveExpiresAt(expiresAtMs)
        _authenticated.value = true
    }

    /** The proof expires as soon as the guard is no longer temporarily paused or its TTL ends. */
    fun onGuardStatus(guardState: GuardStatus) {
        if (guardState != GuardStatus.PARENT_OVERRIDE || isExpired()) clear()
    }

    fun clear() {
        verifiedPin = null
        expiresAtMs = 0L
        _authenticated.value = false
        clearExpiresAt()
    }

    private fun isExpired(): Boolean = expiresAtMs > 0L && now() >= expiresAtMs
}
