package com.planyourtv.policy

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Durable LOCAL fruit ledgers, one document per local family member. The store is the only
 * writer of these keys: the Web backend, the TV exchange transaction and startup reconciliation
 * all go through here, so a read-modify-write is serialized in-process and every mutation is one
 * atomic document write.
 *
 * The ledger is deliberately independent of `LocalPolicyStore.resetLocalSetup`: resetting the
 * PIN or rules must not erase a child's fruits. Deleting a member deletes that member's document
 * through [deleteMember].
 */
object LocalFruitAccountStore {
    private val lock = Any()
    private val cache = HashMap<String, FruitAccountSnapshot?>()
    private val listeners = CopyOnWriteArrayList<(String, FruitAccountSnapshot) -> Unit>()

    fun addListener(listener: (String, FruitAccountSnapshot) -> Unit) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: (String, FruitAccountSnapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun isReady(): Boolean = TvStore.isReady

    fun load(memberId: String): FruitAccountSnapshot? {
        if (!validMember(memberId)) return null
        return synchronized(lock) { loadLocked(memberId) }
    }

    /**
     * Creates an empty LOCAL account on first use. The Web ledger and the credit API use this so
     * a parent sees a usable account immediately; the TV execution path only uses [load] and
     * reports a missing account as unavailable until the parent has opened or credited it.
     */
    fun loadOrCreate(memberId: String, now: Long): FruitAccountSnapshot? {
        if (!validMember(memberId) || !TvStore.isReady) return null
        val result = synchronized(lock) {
            loadLocked(memberId)?.let { return@synchronized it }
            val created = FruitAccountSnapshot(
                source = FruitAccountSource.LOCAL,
                memberId = memberId,
                enabled = true,
                exchangeRateMinutesPerFruit = FruitAccountRules.DEFAULT_RATE,
                availableFruitHundredths = 0L,
                revision = 1L,
                updatedAtEpochMs = now,
            )
            if (!persistLocked(created)) return@synchronized null
            created
        } ?: return null
        notifyChanged(memberId, result)
        return result
    }

    /**
     * Applies a pure ledger operation under the store lock. [expectedRevision] is the Web
     * optimistic-concurrency check; the TV transaction passes `null`.
     */
    fun update(
        memberId: String,
        expectedRevision: Long? = null,
        transform: (FruitAccountSnapshot) -> FruitAccountResult,
    ): FruitAccountResult {
        if (!validMember(memberId)) return FruitAccountResult.Rejected(FruitError.ACCOUNT_MISSING)
        if (!TvStore.isReady) return FruitAccountResult.Rejected(FruitError.ACCOUNT_MISSING)
        val outcome = synchronized(lock) {
            val current = loadLocked(memberId)
                ?: return@synchronized FruitAccountResult.Rejected(FruitError.ACCOUNT_MISSING)
            if (expectedRevision != null && expectedRevision != current.revision) {
                return@synchronized FruitAccountResult.Rejected(FruitError.REVISION_CONFLICT)
            }
            when (val result = transform(current)) {
                is FruitAccountResult.Rejected -> result
                is FruitAccountResult.Ok -> {
                    if (result.account == current) {
                        result
                    } else if (persistLocked(result.account)) {
                        result
                    } else {
                        // Roll the cache back to the persisted state; a failed write must never
                        // look like a successful debit or credit.
                        cache[memberId] = current
                        FruitAccountResult.Rejected(FruitError.PERSIST_FAILED)
                    }
                }
            }
        }
        if (outcome is FruitAccountResult.Ok) notifyChanged(memberId, outcome.account)
        return outcome
    }

    fun reserveExchange(
        memberId: String,
        fruits: Int,
        sessionId: String,
        title: String,
        now: Long,
    ): FruitReserveResult {
        if (!validMember(memberId)) {
            return FruitReserveResult.Rejected(FruitError.ACCOUNT_MISSING)
        }
        if (!TvStore.isReady) {
            return FruitReserveResult.Rejected(FruitError.ACCOUNT_MISSING)
        }
        val outcome = synchronized(lock) {
            val current = loadLocked(memberId)
                ?: return@synchronized FruitReserveResult.Rejected(FruitError.ACCOUNT_MISSING)
            when (val result = current.reserveExchange(fruits, sessionId, title, now)) {
                is FruitReserveResult.Rejected -> result
                is FruitReserveResult.Ok ->
                    if (persistLocked(result.reservation.account)) {
                        result
                    } else {
                        cache[memberId] = current
                        FruitReserveResult.Rejected(FruitError.PERSIST_FAILED)
                    }
            }
        }
        if (outcome is FruitReserveResult.Ok) {
            notifyChanged(memberId, outcome.reservation.account)
        }
        return outcome
    }

    fun commitExchange(memberId: String, entryId: String, now: Long): FruitAccountResult =
        update(memberId) { it.commitExchange(entryId, now) }

    fun voidExchange(memberId: String, entryId: String, now: Long): FruitAccountResult =
        update(memberId) { it.voidExchange(entryId, now) }

    /**
     * Reconciles reservations left by a crash. Returns null when there is no document, so the
     * caller can distinguish "nothing to reconcile" from "reconciled to an empty result".
     */
    fun reconcilePending(
        memberId: String,
        now: Long,
        sessionExists: (String) -> Boolean,
    ): FruitReconcileResult? {
        if (!validMember(memberId) || !TvStore.isReady) return null
        var changed = false
        val outcome = synchronized(lock) {
            val current = loadLocked(memberId) ?: return@synchronized null
            val result = current.reconcilePendingExchanges(now, sessionExists)
            if (result.committedEntryIds.isEmpty() && result.voidedEntryIds.isEmpty()) {
                return@synchronized result
            }
            if (!persistLocked(result.account)) {
                cache[memberId] = current
                return@synchronized null
            }
            changed = true
            result
        }
        if (changed && outcome != null) notifyChanged(memberId, outcome.account)
        return outcome
    }

    fun save(account: FruitAccountSnapshot): Boolean {
        if (!validMember(account.memberId) || !TvStore.isReady) return false
        val saved = synchronized(lock) { persistLocked(account) }
        if (saved) notifyChanged(account.memberId, account)
        return saved
    }

    /** Removes one member's ledger; used only when the family member itself is deleted. */
    fun deleteMember(memberId: String): Boolean {
        if (!validMember(memberId)) return false
        val removed = synchronized(lock) {
            cache.remove(memberId)
            if (!TvStore.isReady) return@synchronized false
            TvStore.removeDurable(key(memberId))
        }
        return removed
    }

    private fun loadLocked(memberId: String): FruitAccountSnapshot? {
        if (!TvStore.isReady) return null
        if (cache.containsKey(memberId)) return cache[memberId]
        val decoded = FruitAccountCodec.decode(
            TvStore.getString(key(memberId), null),
            FruitAccountSource.LOCAL,
        )?.takeIf { it.memberId == memberId }
        cache[memberId] = decoded
        return decoded
    }

    private fun persistLocked(account: FruitAccountSnapshot): Boolean {
        if (!TvStore.isReady) return false
        val saved = TvStore.putStringDurable(
            key(account.memberId),
            FruitAccountCodec.encode(account),
        )
        if (saved) cache[account.memberId] = account
        return saved
    }

    private fun notifyChanged(memberId: String, account: FruitAccountSnapshot) {
        listeners.forEach { listener ->
            runCatching { listener(memberId, account) }
        }
    }

    private fun key(memberId: String): String =
        "${PolicyKeys.LOCAL_FRUIT_ACCOUNT_V1}.$memberId"

    private fun validMember(memberId: String): Boolean = FamilyIds.isValidUserId(memberId)
}