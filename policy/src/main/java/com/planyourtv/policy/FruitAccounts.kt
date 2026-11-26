package com.planyourtv.policy

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Which independent ledger a document belongs to. Routing never reads this field. */
enum class FruitAccountSource { LOCAL, HOSTED }

enum class FruitEntryKind { CREDIT, EXCHANGE, ADJUST }

/**
 * `PENDING` is a TV exchange whose session has not been established yet. `COMMITTED` is a
 * final credit/adjustment or a started exchange. `VOID` is an exchange that never started; its
 * debit was restored and the row no longer counts as an expense.
 */
enum class FruitEntryStatus { PENDING, COMMITTED, VOID }

/**
 * One append-only ledger row. [fruitHundredths] is signed: credits are positive, exchanges and
 * adjustments are negative. The account's [FruitAccountSnapshot.availableFruitHundredths] is the
 * authority for the balance; rows are the audit trail and are never re-summed to compute it.
 */
data class FruitEntry(
    val id: String,
    val kind: FruitEntryKind,
    val fruitHundredths: Long,
    val title: String,
    val note: String? = null,
    /** Granted entertainment length; set on exchange rows only. */
    val minutes: Int? = null,
    /** Rate snapshot taken when the exchange happened; later rate edits never rewrite it. */
    val minutesPerFruit: Int? = null,
    val sessionId: String? = null,
    val externalId: String? = null,
    val createdAtEpochMs: Long = 0L,
    val status: FruitEntryStatus = FruitEntryStatus.COMMITTED,
    /** Set on an `ADJUST` row: the credit row it reverses. Internal to the local ledger. */
    val sourceEntryId: String? = null,
)

/** Quick projects are direction-specific so an income name never overwrites an expense one. */
enum class FruitProjectDirection { INCOME, EXPENSE }

data class FruitQuickProject(
    val id: String,
    val name: String,
    val defaultFruits: Int,
    /** Missing in schema-1 documents written before manual expenses; read as [INCOME]. */
    val direction: FruitProjectDirection = FruitProjectDirection.INCOME,
)

/**
 * One member's fruit ledger, in the exact format shared by LOCAL and HOSTED. The two partitions
 * are independent accounts: this model only guarantees that either writer can read the same
 * shape, never that balances, rates or rows are merged.
 */
data class FruitAccountSnapshot(
    val schemaVersion: Int = FruitAccountRules.SCHEMA_VERSION,
    val source: FruitAccountSource = FruitAccountSource.LOCAL,
    val memberId: String,
    val enabled: Boolean = true,
    val exchangeRateMinutesPerFruit: Int = FruitAccountRules.DEFAULT_RATE,
    val availableFruitHundredths: Long = 0L,
    val revision: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
    val entries: List<FruitEntry> = emptyList(),
    val quickProjects: List<FruitQuickProject> = emptyList(),
    /**
     * Recent `credit` request IDs, so a Web retry cannot add the same fruits twice. It lives in
     * the account document to make the idempotency record and the balance one atomic write.
     * Other writers may ignore it; it never affects execution.
     */
    val recentRequestIds: List<String> = emptyList(),
) {
    val wholeFruits: Long get() = FruitAccountRules.wholeFruits(availableFruitHundredths)

    val rateValid: Boolean get() = FruitAccountRules.rateValid(exchangeRateMinutesPerFruit)

    val canExchange: Boolean get() = enabled && rateValid && wholeFruits >= 1L
}

/** All product limits for the fruit account. None of these invalidate the shared rule set. */
object FruitAccountRules {
    const val SCHEMA_VERSION = 1
    const val DEFAULT_RATE = 1
    const val MIN_RATE = 1
    const val MAX_RATE = 60
    const val HUNDREDTHS_PER_FRUIT = 100L
    // 999,999 fruits. Balances are whole hundredths in a Long; no Double is ever stored.
    const val MAX_AVAILABLE_HUNDREDTHS = 99_999_900L
    const val MIN_CREDIT_FRUITS = 1
    const val MAX_CREDIT_FRUITS = 999
    // Only a ceiling against overflow; the real exchange ceiling comes from balance/cap/quota.
    const val MAX_EXCHANGE_FRUITS = 999_999
    const val MAX_SETTLED_ENTRIES = 500
    const val MAX_QUICK_PROJECTS = 20
    const val MAX_TITLE_CODE_POINTS = 40
    const val MAX_EXCHANGE_TITLE_CODE_POINTS = 80
    const val MAX_NOTE_CODE_POINTS = 200
    const val MAX_QUICK_PROJECT_NAME_CODE_POINTS = 40
    const val MAX_REQUEST_IDS = 50

    fun wholeFruits(availableFruitHundredths: Long): Long =
        (availableFruitHundredths / HUNDREDTHS_PER_FRUIT).coerceAtLeast(0L)

    fun rateValid(rate: Int): Boolean = rate in MIN_RATE..MAX_RATE

    fun validEntryTitle(raw: String, maxCodePoints: Int = MAX_TITLE_CODE_POINTS): Boolean =
        raw.isNotBlank() && raw.codePointCount(0, raw.length) <= maxCodePoints &&
            raw.none { it == '\n' || it == '\r' || it.isISOControl() }

    fun validNote(raw: String?): Boolean =
        raw == null || raw.codePointCount(0, raw.length) <= MAX_NOTE_CODE_POINTS

    fun newEntryId(): String = "fe_" + uuid()

    fun newProjectId(): String = "fp_" + uuid()

    private fun uuid(): String = UUID.randomUUID().toString().replace("-", "")
}

enum class FruitError {
    ACCOUNT_MISSING,
    ACCOUNT_DISABLED,
    RATE_INVALID,
    FRUITS_INVALID,
    TITLE_INVALID,
    NOTE_INVALID,
    REQUEST_DUPLICATE,
    BALANCE_INSUFFICIENT,
    BALANCE_LIMIT,
    ENTRY_NOT_FOUND,
    ENTRY_NOT_PENDING,
    ENTRY_NOT_CREDIT,
    ENTRY_ALREADY_VOIDED,
    VOID_NOT_ALLOWED,
    PROJECT_LIMIT,
    PROJECT_NOT_FOUND,
    REVISION_CONFLICT,
    PERSIST_FAILED,
}

sealed interface FruitAccountResult {
    data class Ok(val account: FruitAccountSnapshot) : FruitAccountResult

    data class Rejected(val error: FruitError) : FruitAccountResult
}

data class FruitExchangeReservation(
    val account: FruitAccountSnapshot,
    val entry: FruitEntry,
)

sealed interface FruitReserveResult {
    data class Ok(val reservation: FruitExchangeReservation) : FruitReserveResult

    data class Rejected(val error: FruitError) : FruitReserveResult
}

data class FruitReconcileResult(
    val account: FruitAccountSnapshot,
    val committedEntryIds: List<String> = emptyList(),
    val voidedEntryIds: List<String> = emptyList(),
)

fun FruitAccountSnapshot.withUpdatedSnapshot(
    availableFruitHundredths: Long = this.availableFruitHundredths,
    exchangeRateMinutesPerFruit: Int = this.exchangeRateMinutesPerFruit,
    revision: Long = this.revision + 1L,
    updatedAtEpochMs: Long,
    entries: List<FruitEntry> = this.entries,
    quickProjects: List<FruitQuickProject> = this.quickProjects,
    recentRequestIds: List<String> = this.recentRequestIds,
): FruitAccountSnapshot = copy(
    availableFruitHundredths = availableFruitHundredths,
    exchangeRateMinutesPerFruit = exchangeRateMinutesPerFruit,
    revision = revision,
    updatedAtEpochMs = updatedAtEpochMs,
    entries = entries,
    quickProjects = quickProjects,
    recentRequestIds = recentRequestIds,
)

/**
 * Adds or updates one quick project in a single direction. A null return means the list is
 * full; the caller validates [defaultFruits] before calling this helper.
 */
private fun saveQuickProject(
    projects: List<FruitQuickProject>,
    name: String,
    defaultFruits: Int,
    direction: FruitProjectDirection,
    projectId: String,
): List<FruitQuickProject>? {
    val existing = projects.firstOrNull { it.name == name && it.direction == direction }
    return when {
        existing != null -> projects.map {
            if (it.id == existing.id) it.copy(defaultFruits = defaultFruits) else it
        }
        projects.size >= FruitAccountRules.MAX_QUICK_PROJECTS -> null
        else -> projects + FruitQuickProject(
            id = projectId,
            name = name,
            defaultFruits = defaultFruits,
            direction = direction,
        )
    }
}

/**
 * Parent credit. [fruits] is a whole number in 1..999, matching the Web form; the ledger stores
 * `fruits * 100`. A credit is allowed on a disabled account (the account switch only gates
 * exchange) and never touches the TV's active member or running session.
 */
fun FruitAccountSnapshot.credit(
    fruits: Int,
    title: String,
    note: String? = null,
    saveQuickProject: Boolean = false,
    quickProjectDefaultFruits: Int? = null,
    now: Long,
    requestId: String? = null,
    entryId: String = FruitAccountRules.newEntryId(),
    projectId: String = FruitAccountRules.newProjectId(),
): FruitAccountResult {
    val cleanTitle = title.trim()
    if (fruits !in FruitAccountRules.MIN_CREDIT_FRUITS..FruitAccountRules.MAX_CREDIT_FRUITS) {
        return FruitAccountResult.Rejected(FruitError.FRUITS_INVALID)
    }
    if (!FruitAccountRules.validEntryTitle(cleanTitle)) {
        return FruitAccountResult.Rejected(FruitError.TITLE_INVALID)
    }
    val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
    if (!FruitAccountRules.validNote(cleanNote)) {
        return FruitAccountResult.Rejected(FruitError.NOTE_INVALID)
    }
    if (!requestId.isNullOrBlank() && requestId in recentRequestIds) {
        return FruitAccountResult.Rejected(FruitError.REQUEST_DUPLICATE)
    }
    val amount = fruits * FruitAccountRules.HUNDREDTHS_PER_FRUIT
    val nextBalance = availableFruitHundredths + amount
    if (nextBalance > FruitAccountRules.MAX_AVAILABLE_HUNDREDTHS) {
        return FruitAccountResult.Rejected(FruitError.BALANCE_LIMIT)
    }
    val entry = FruitEntry(
        id = entryId,
        kind = FruitEntryKind.CREDIT,
        fruitHundredths = amount,
        title = cleanTitle,
        note = cleanNote,
        createdAtEpochMs = now,
        status = FruitEntryStatus.COMMITTED,
    )
    var projects = quickProjects
    if (saveQuickProject) {
        val defaultFruits = quickProjectDefaultFruits ?: fruits
        if (defaultFruits !in FruitAccountRules.MIN_CREDIT_FRUITS..
            FruitAccountRules.MAX_CREDIT_FRUITS
        ) {
            return FruitAccountResult.Rejected(FruitError.FRUITS_INVALID)
        }
        projects = saveQuickProject(
            projects = projects,
            name = cleanTitle,
            defaultFruits = defaultFruits,
            direction = FruitProjectDirection.INCOME,
            projectId = projectId,
        ) ?: return FruitAccountResult.Rejected(FruitError.PROJECT_LIMIT)
    }
    val requestIds = if (requestId.isNullOrBlank()) {
        recentRequestIds
    } else {
        (recentRequestIds + requestId).takeLast(FruitAccountRules.MAX_REQUEST_IDS)
    }
    return FruitAccountResult.Ok(
        withUpdatedSnapshot(
            availableFruitHundredths = nextBalance,
            updatedAtEpochMs = now,
            entries = trimEntries(entries + entry),
            quickProjects = projects,
            recentRequestIds = requestIds,
        ),
    )
}

/**
 * Parent bookkeeping for fruits spent outside the TV wheel. The row is an immediately
 * `COMMITTED` `EXCHANGE` with no session id, so the existing schema-1 decoder accepts it and the
 * TV's reservation/reconciliation paths ignore it. The current rate is snapshotted only for the
 * ledger; no minutes are granted and no timer starts. Manual spending is intentionally allowed
 * while [enabled] is false: the switch gates TV exchanges, not the parent's records.
 */
fun FruitAccountSnapshot.recordManualExpense(
    fruits: Int,
    title: String,
    note: String? = null,
    saveQuickProject: Boolean = false,
    quickProjectDefaultFruits: Int? = null,
    now: Long,
    requestId: String? = null,
    entryId: String = FruitAccountRules.newEntryId(),
    projectId: String = FruitAccountRules.newProjectId(),
): FruitAccountResult {
    if (!rateValid) return FruitAccountResult.Rejected(FruitError.RATE_INVALID)
    if (fruits !in FruitAccountRules.MIN_CREDIT_FRUITS..FruitAccountRules.MAX_CREDIT_FRUITS) {
        return FruitAccountResult.Rejected(FruitError.FRUITS_INVALID)
    }
    val cleanTitle = title.trim()
    if (!FruitAccountRules.validEntryTitle(cleanTitle)) {
        return FruitAccountResult.Rejected(FruitError.TITLE_INVALID)
    }
    val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
    if (!FruitAccountRules.validNote(cleanNote)) {
        return FruitAccountResult.Rejected(FruitError.NOTE_INVALID)
    }
    if (!requestId.isNullOrBlank() && requestId in recentRequestIds) {
        return FruitAccountResult.Rejected(FruitError.REQUEST_DUPLICATE)
    }
    val amount = fruits * FruitAccountRules.HUNDREDTHS_PER_FRUIT
    if (wholeFruits < fruits || availableFruitHundredths < amount) {
        return FruitAccountResult.Rejected(FruitError.BALANCE_INSUFFICIENT)
    }
    val minutes = fruits.toLong() * exchangeRateMinutesPerFruit
    if (minutes !in 1L..Int.MAX_VALUE.toLong()) {
        return FruitAccountResult.Rejected(FruitError.FRUITS_INVALID)
    }
    val entry = FruitEntry(
        id = entryId,
        kind = FruitEntryKind.EXCHANGE,
        fruitHundredths = -amount,
        title = cleanTitle,
        note = cleanNote,
        minutes = minutes.toInt(),
        minutesPerFruit = exchangeRateMinutesPerFruit,
        sessionId = null,
        createdAtEpochMs = now,
        status = FruitEntryStatus.COMMITTED,
    )
    var projects = quickProjects
    if (saveQuickProject) {
        val defaultFruits = quickProjectDefaultFruits ?: fruits
        if (defaultFruits !in FruitAccountRules.MIN_CREDIT_FRUITS..
            FruitAccountRules.MAX_CREDIT_FRUITS
        ) {
            return FruitAccountResult.Rejected(FruitError.FRUITS_INVALID)
        }
        projects = saveQuickProject(
            projects = projects,
            name = cleanTitle,
            defaultFruits = defaultFruits,
            direction = FruitProjectDirection.EXPENSE,
            projectId = projectId,
        ) ?: return FruitAccountResult.Rejected(FruitError.PROJECT_LIMIT)
    }
    val requestIds = if (requestId.isNullOrBlank()) {
        recentRequestIds
    } else {
        (recentRequestIds + requestId).takeLast(FruitAccountRules.MAX_REQUEST_IDS)
    }
    return FruitAccountResult.Ok(
        withUpdatedSnapshot(
            availableFruitHundredths = availableFruitHundredths - amount,
            updatedAtEpochMs = now,
            entries = trimEntries(entries + entry),
            quickProjects = projects,
            recentRequestIds = requestIds,
        ),
    )
}

/**
 * Voids a manual expense and restores its debit. A TV exchange always has a session id and is
 * rejected here; the existing no-refund rule for started sessions stays intact. Idempotent.
 */
fun FruitAccountSnapshot.voidManualExpense(entryId: String, now: Long): FruitAccountResult {
    val entry = entries.firstOrNull { it.id == entryId }
        ?: return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_FOUND)
    if (entry.kind != FruitEntryKind.EXCHANGE || entry.sessionId != null) {
        return FruitAccountResult.Rejected(FruitError.VOID_NOT_ALLOWED)
    }
    if (entry.status == FruitEntryStatus.VOID) return FruitAccountResult.Ok(this)
    if (entry.status != FruitEntryStatus.COMMITTED || entry.fruitHundredths >= 0L) {
        return FruitAccountResult.Rejected(FruitError.VOID_NOT_ALLOWED)
    }
    val restored = (availableFruitHundredths - entry.fruitHundredths)
        .coerceAtMost(FruitAccountRules.MAX_AVAILABLE_HUNDREDTHS)
    val updated = entries.map {
        if (it.id == entryId) it.copy(status = FruitEntryStatus.VOID) else it
    }
    return FruitAccountResult.Ok(
        withUpdatedSnapshot(
            availableFruitHundredths = restored,
            updatedAtEpochMs = now,
            entries = updated,
        ),
    )
}

/**
 * Debits a whole-fruit exchange and appends the matching `PENDING` row. The caller must already
 * have passed the route planner for the same fruit count; this method owns only the balance and
 * ledger invariants. It never grants a partial minute: the row is always `fruits * rate`.
 */
fun FruitAccountSnapshot.reserveExchange(
    fruits: Int,
    sessionId: String,
    title: String,
    now: Long,
    entryId: String = FruitAccountRules.newEntryId(),
): FruitReserveResult {
    if (!enabled) return FruitReserveResult.Rejected(FruitError.ACCOUNT_DISABLED)
    if (!rateValid) return FruitReserveResult.Rejected(FruitError.RATE_INVALID)
    if (fruits !in 1..FruitAccountRules.MAX_EXCHANGE_FRUITS) {
        return FruitReserveResult.Rejected(FruitError.FRUITS_INVALID)
    }
    val cleanTitle = title.trim()
    if (!FruitAccountRules.validEntryTitle(
            cleanTitle,
            FruitAccountRules.MAX_EXCHANGE_TITLE_CODE_POINTS,
        )
    ) {
        return FruitReserveResult.Rejected(FruitError.TITLE_INVALID)
    }
    if (sessionId.isBlank()) return FruitReserveResult.Rejected(FruitError.FRUITS_INVALID)
    if (wholeFruits < fruits) return FruitReserveResult.Rejected(FruitError.BALANCE_INSUFFICIENT)
    val amount = fruits * FruitAccountRules.HUNDREDTHS_PER_FRUIT
    if (availableFruitHundredths < amount) {
        return FruitReserveResult.Rejected(FruitError.BALANCE_INSUFFICIENT)
    }
    val minutes = fruits.toLong() * exchangeRateMinutesPerFruit
    if (minutes !in 1L..Int.MAX_VALUE.toLong()) {
        return FruitReserveResult.Rejected(FruitError.FRUITS_INVALID)
    }
    val entry = FruitEntry(
        id = entryId,
        kind = FruitEntryKind.EXCHANGE,
        fruitHundredths = -amount,
        title = cleanTitle,
        minutes = minutes.toInt(),
        minutesPerFruit = exchangeRateMinutesPerFruit,
        sessionId = sessionId,
        createdAtEpochMs = now,
        status = FruitEntryStatus.PENDING,
    )
    return FruitReserveResult.Ok(
        FruitExchangeReservation(
            account = withUpdatedSnapshot(
                availableFruitHundredths = availableFruitHundredths - amount,
                updatedAtEpochMs = now,
                entries = trimEntries(entries + entry),
            ),
            entry = entry,
        ),
    )
}

/** Marks a started exchange final. Idempotent; a `VOID` row is never resurrected. */
fun FruitAccountSnapshot.commitExchange(entryId: String, now: Long): FruitAccountResult {
    val entry = entries.firstOrNull { it.id == entryId }
        ?: return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_FOUND)
    if (entry.kind != FruitEntryKind.EXCHANGE) {
        return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_CREDIT)
    }
    if (entry.status == FruitEntryStatus.COMMITTED) return FruitAccountResult.Ok(this)
    if (entry.status != FruitEntryStatus.PENDING) {
        return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_PENDING)
    }
    val updated = entries.map {
        if (it.id == entryId) it.copy(status = FruitEntryStatus.COMMITTED) else it
    }
    return FruitAccountResult.Ok(
        withUpdatedSnapshot(updatedAtEpochMs = now, entries = updated),
    )
}

/** Restores a reservation whose session was never established. Idempotent; no refunds later. */
fun FruitAccountSnapshot.voidExchange(entryId: String, now: Long): FruitAccountResult {
    val entry = entries.firstOrNull { it.id == entryId }
        ?: return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_FOUND)
    if (entry.kind != FruitEntryKind.EXCHANGE) {
        return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_CREDIT)
    }
    if (entry.status == FruitEntryStatus.VOID) return FruitAccountResult.Ok(this)
    if (entry.status != FruitEntryStatus.PENDING) {
        return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_PENDING)
    }
    val restored = (availableFruitHundredths - entry.fruitHundredths)
        .coerceAtMost(FruitAccountRules.MAX_AVAILABLE_HUNDREDTHS)
    val updated = entries.map {
        if (it.id == entryId) it.copy(status = FruitEntryStatus.VOID) else it
    }
    return FruitAccountResult.Ok(
        withUpdatedSnapshot(
            availableFruitHundredths = restored,
            updatedAtEpochMs = now,
            entries = updated,
        ),
    )
}

/**
 * Startup reconciliation. A reservation is committed when its session still exists (running or
 * settled) and restored otherwise. The caller supplies the predicate so this stays pure.
 */
fun FruitAccountSnapshot.reconcilePendingExchanges(
    now: Long,
    sessionExists: (String) -> Boolean,
): FruitReconcileResult {
    val pending = entries.filter {
        it.kind == FruitEntryKind.EXCHANGE && it.status == FruitEntryStatus.PENDING
    }
    if (pending.isEmpty()) return FruitReconcileResult(this)
    val commit = mutableSetOf<String>()
    val void = mutableSetOf<String>()
    var balance = availableFruitHundredths
    pending.forEach { entry ->
        val sessionId = entry.sessionId
        if (!sessionId.isNullOrBlank() && sessionExists(sessionId)) {
            commit += entry.id
        } else {
            void += entry.id
            balance = (balance - entry.fruitHundredths)
                .coerceAtMost(FruitAccountRules.MAX_AVAILABLE_HUNDREDTHS)
        }
    }
    val updated = entries.map { entry ->
        when {
            entry.id in commit -> entry.copy(status = FruitEntryStatus.COMMITTED)
            entry.id in void -> entry.copy(status = FruitEntryStatus.VOID)
            else -> entry
        }
    }
    return FruitReconcileResult(
        account = withUpdatedSnapshot(
            availableFruitHundredths = balance,
            updatedAtEpochMs = now,
            entries = updated,
        ),
        committedEntryIds = commit.toList(),
        voidedEntryIds = void.toList(),
    )
}

/** Local Web rate edit. Only future exchanges use the new rate; rows keep their snapshot. */
fun FruitAccountSnapshot.setExchangeRate(rate: Int, now: Long): FruitAccountResult {
    if (!FruitAccountRules.rateValid(rate)) {
        return FruitAccountResult.Rejected(FruitError.RATE_INVALID)
    }
    if (rate == exchangeRateMinutesPerFruit) return FruitAccountResult.Ok(this)
    return FruitAccountResult.Ok(
        withUpdatedSnapshot(
            updatedAtEpochMs = now,
            exchangeRateMinutesPerFruit = rate,
        ),
    )
}

/**
 * Reverses an unused credit by appending a negative `ADJUST` row. It never deletes history and
 * refuses when the current balance is lower than the credit, i.e. the fruits were already used.
 */
fun FruitAccountSnapshot.voidCredit(
    entryId: String,
    now: Long,
    adjustmentId: String = FruitAccountRules.newEntryId(),
): FruitAccountResult {
    val entry = entries.firstOrNull { it.id == entryId }
        ?: return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_FOUND)
    if (entry.kind != FruitEntryKind.CREDIT) {
        return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_CREDIT)
    }
    if (entry.status != FruitEntryStatus.COMMITTED) {
        return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_CREDIT)
    }
    if (entries.any {
            it.kind == FruitEntryKind.ADJUST && it.sourceEntryId == entryId
        }
    ) {
        return FruitAccountResult.Rejected(FruitError.ENTRY_ALREADY_VOIDED)
    }
    val amount = entry.fruitHundredths
    if (amount <= 0L) return FruitAccountResult.Rejected(FruitError.ENTRY_NOT_CREDIT)
    if (availableFruitHundredths < amount) {
        return FruitAccountResult.Rejected(FruitError.VOID_NOT_ALLOWED)
    }
    val adjustment = FruitEntry(
        id = adjustmentId,
        kind = FruitEntryKind.ADJUST,
        fruitHundredths = -amount,
        title = entry.title,
        note = entry.note,
        createdAtEpochMs = now,
        status = FruitEntryStatus.COMMITTED,
        sourceEntryId = entryId,
    )
    return FruitAccountResult.Ok(
        withUpdatedSnapshot(
            availableFruitHundredths = availableFruitHundredths - amount,
            updatedAtEpochMs = now,
            entries = trimEntries(entries + adjustment),
        ),
    )
}

fun FruitAccountSnapshot.deleteQuickProject(projectId: String, now: Long): FruitAccountResult {
    if (quickProjects.none { it.id == projectId }) {
        return FruitAccountResult.Rejected(FruitError.PROJECT_NOT_FOUND)
    }
    return FruitAccountResult.Ok(
        withUpdatedSnapshot(
            updatedAtEpochMs = now,
            quickProjects = quickProjects.filterNot { it.id == projectId },
        ),
    )
}

/**
 * Keeps every `PENDING` row and the newest 500 settled rows. Trimming is pure history
 * retention; the stored balance is never recomputed from the rows.
 */
internal fun trimEntries(entries: List<FruitEntry>): List<FruitEntry> {
    val pending = entries.filter { it.status == FruitEntryStatus.PENDING }
    val settled = entries.filter { it.status != FruitEntryStatus.PENDING }
    if (settled.size <= FruitAccountRules.MAX_SETTLED_ENTRIES) return entries
    val keep = settled.takeLast(FruitAccountRules.MAX_SETTLED_ENTRIES).toSet()
    return entries.filter { it.status == FruitEntryStatus.PENDING || it in keep }
}

/** Canonical JSON for the shared LOCAL/HOSTED account format (schema 1). */
object FruitAccountCodec {
    fun encode(account: FruitAccountSnapshot): String = toJson(account).toString()

    fun decode(
        raw: String?,
        fallbackSource: FruitAccountSource = FruitAccountSource.LOCAL,
    ): FruitAccountSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching { JSONObject(raw).toAccount(fallbackSource) }.getOrNull()
    }

    fun toJson(account: FruitAccountSnapshot): JSONObject = JSONObject()
        .put("schemaVersion", FruitAccountRules.SCHEMA_VERSION)
        .put("source", account.source.name)
        .put("memberId", account.memberId)
        .put("enabled", account.enabled)
        .put("exchangeRateMinutesPerFruit", account.exchangeRateMinutesPerFruit)
        .put("availableFruitHundredths", account.availableFruitHundredths)
        .put("revision", account.revision)
        .put("updatedAtEpochMs", account.updatedAtEpochMs)
        .put(
            "entries",
            JSONArray().also { array -> account.entries.forEach { array.put(it.toJson()) } },
        )
        .put(
            "quickProjects",
            JSONArray().also { array ->
                account.quickProjects.forEach { array.put(it.toJson()) }
            },
        )
        .put(
            "recentRequestIds",
            JSONArray().also { array ->
                account.recentRequestIds.forEach { array.put(it) }
            },
        )

    private fun FruitEntry.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind.name)
        .put("fruitHundredths", fruitHundredths)
        .put("title", title)
        .put("note", note ?: JSONObject.NULL)
        .put("minutes", minutes ?: JSONObject.NULL)
        .put("minutesPerFruit", minutesPerFruit ?: JSONObject.NULL)
        .put("sessionId", sessionId ?: JSONObject.NULL)
        .put("externalId", externalId ?: JSONObject.NULL)
        .put("createdAtEpochMs", createdAtEpochMs)
        .put("status", status.name)
        .put("sourceEntryId", sourceEntryId ?: JSONObject.NULL)

    private fun FruitQuickProject.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("defaultFruits", defaultFruits)
        .put("direction", direction.name)

    private fun JSONObject.toAccount(fallbackSource: FruitAccountSource): FruitAccountSnapshot? {
        if (optInt("schemaVersion", -1) != FruitAccountRules.SCHEMA_VERSION) return null
        val source = optString("source", "")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { FruitAccountSource.valueOf(it) }.getOrNull() }
            ?: fallbackSource
        val memberId = optString("memberId", "")
        if (memberId.isBlank() || memberId.length > 120) return null
        val rate = if (has("exchangeRateMinutesPerFruit") && !isNull("exchangeRateMinutesPerFruit")) {
            exactInt("exchangeRateMinutesPerFruit") ?: return null
        } else {
            FruitAccountRules.DEFAULT_RATE
        }
        if (!FruitAccountRules.rateValid(rate)) return null
        val available = exactLong("availableFruitHundredths") ?: return null
        if (available !in 0L..FruitAccountRules.MAX_AVAILABLE_HUNDREDTHS) return null
        val revision = if (has("revision") && !isNull("revision")) {
            exactLong("revision")?.takeIf { it >= 0L } ?: return null
        } else {
            0L
        }
        val updatedAt = if (has("updatedAtEpochMs") && !isNull("updatedAtEpochMs")) {
            exactLong("updatedAtEpochMs")?.takeIf { it >= 0L } ?: return null
        } else {
            0L
        }
        val entries = if (!has("entries") || isNull("entries")) {
            emptyList()
        } else {
            val array = optJSONArray("entries") ?: return null
            buildList {
                for (index in 0 until array.length()) {
                    add(array.optJSONObject(index)?.toEntry() ?: return null)
                }
            }.also { list ->
                if (list.map { it.id }.toSet().size != list.size) return null
            }
        }
        val projects = if (!has("quickProjects") || isNull("quickProjects")) {
            emptyList()
        } else {
            val array = optJSONArray("quickProjects") ?: return null
            buildList {
                for (index in 0 until array.length()) {
                    add(array.optJSONObject(index)?.toProject() ?: return null)
                }
            }.also { list ->
                if (list.map { it.id }.toSet().size != list.size) return null
            }
        }
        val requestIds = if (!has("recentRequestIds") || isNull("recentRequestIds")) {
            emptyList()
        } else {
            val array = optJSONArray("recentRequestIds") ?: return null
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index, "")
                    if (value.isBlank()) return null
                    add(value)
                }
            }.takeLast(FruitAccountRules.MAX_REQUEST_IDS)
        }
        return FruitAccountSnapshot(
            source = source,
            memberId = memberId,
            enabled = optBoolean("enabled", true),
            exchangeRateMinutesPerFruit = rate,
            availableFruitHundredths = available,
            revision = revision,
            updatedAtEpochMs = updatedAt,
            entries = entries,
            quickProjects = projects,
            recentRequestIds = requestIds,
        )
    }

    private fun JSONObject.toEntry(): FruitEntry? {
        val id = optString("id", "")
        if (id.isBlank()) return null
        val kind = optString("kind", "")
            .let { runCatching { FruitEntryKind.valueOf(it) }.getOrNull() } ?: return null
        val status = optString("status", "")
            .let { runCatching { FruitEntryStatus.valueOf(it) }.getOrNull() } ?: return null
        val amount = exactLong("fruitHundredths") ?: return null
        val title = optString("title", "")
        if (title.isBlank()) return null
        when (kind) {
            FruitEntryKind.CREDIT -> if (amount <= 0L || status != FruitEntryStatus.COMMITTED) {
                return null
            }
            FruitEntryKind.ADJUST -> if (amount >= 0L || status != FruitEntryStatus.COMMITTED) {
                return null
            }
            FruitEntryKind.EXCHANGE -> if (amount >= 0L) return null
        }
        val minutes = nullableExactInt("minutes")
        val minutesPerFruit = nullableExactInt("minutesPerFruit")
        if (kind == FruitEntryKind.EXCHANGE) {
            if (minutes == null || minutes <= 0) return null
            if (minutesPerFruit == null || !FruitAccountRules.rateValid(minutesPerFruit)) {
                return null
            }
        }
        val created = exactLong("createdAtEpochMs") ?: return null
        if (created < 0L) return null
        return FruitEntry(
            id = id,
            kind = kind,
            fruitHundredths = amount,
            title = title,
            note = stringOrNull("note"),
            minutes = minutes,
            minutesPerFruit = minutesPerFruit,
            sessionId = stringOrNull("sessionId"),
            externalId = stringOrNull("externalId"),
            createdAtEpochMs = created,
            status = status,
            sourceEntryId = stringOrNull("sourceEntryId"),
        )
    }

    private fun JSONObject.toProject(): FruitQuickProject? {
        val id = optString("id", "")
        val name = optString("name", "")
        if (id.isBlank() || name.isBlank()) return null
        val fruits = exactInt("defaultFruits") ?: return null
        if (fruits !in FruitAccountRules.MIN_CREDIT_FRUITS..FruitAccountRules.MAX_CREDIT_FRUITS) {
            return null
        }
        // Schema-1 documents written before manual expenses have no direction. Treat a missing
        // or unrecognized value as income so an older ledger keeps decoding instead of dropping.
        val direction = optString("direction", "")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { FruitProjectDirection.valueOf(it) }.getOrNull() }
            ?: FruitProjectDirection.INCOME
        return FruitQuickProject(
            id = id,
            name = name,
            defaultFruits = fruits,
            direction = direction,
        )
    }

    private fun JSONObject.exactInt(key: String): Int? {
        val value = exactLong(key) ?: return null
        if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) return null
        return value.toInt()
    }

    private fun JSONObject.nullableExactInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return exactInt(key)
    }

    /**
     * Reads a JSON number without `optLong`'s string coercion or overflow wrapping. Fractional
     * values are rejected; the ledger only stores whole hundredths.
     */
    private fun JSONObject.exactLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        val raw = opt(key)
        if (raw !is Number) return null
        val value = raw.toDouble()
        if (!value.isFinite() || value != Math.floor(value)) return null
        if (value < Long.MIN_VALUE.toDouble() || value > Long.MAX_VALUE.toDouble()) return null
        return raw.toLong()
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key, "")
        return value.takeIf { it.isNotBlank() }
    }
}