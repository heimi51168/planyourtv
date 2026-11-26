package com.planyourtv.policy

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FruitAccountsTest {
    private val now = 1_760_000_000_000L
    private val member = "usr_abc123"

    private fun account(
        balance: Long = 0L,
        rate: Int = 1,
        enabled: Boolean = true,
        entries: List<FruitEntry> = emptyList(),
        projects: List<FruitQuickProject> = emptyList(),
        revision: Long = 1L,
    ) = FruitAccountSnapshot(
        source = FruitAccountSource.LOCAL,
        memberId = member,
        enabled = enabled,
        exchangeRateMinutesPerFruit = rate,
        availableFruitHundredths = balance,
        revision = revision,
        updatedAtEpochMs = now,
        entries = entries,
        quickProjects = projects,
    )

    private fun FruitAccountResult.ok(): FruitAccountSnapshot {
        assertTrue("expected Ok, was $this", this is FruitAccountResult.Ok)
        return (this as FruitAccountResult.Ok).account
    }

    private fun FruitReserveResult.ok(): FruitExchangeReservation {
        assertTrue("expected Ok, was $this", this is FruitReserveResult.Ok)
        return (this as FruitReserveResult.Ok).reservation
    }

    @Test
    fun creditAddsWholeFruitsAndAppendsOneCommittedRow() {
        val credited = account()
            .credit(fruits = 5, title = "做家务", note = "扫地", now = now)
            .ok()

        assertEquals(500L, credited.availableFruitHundredths)
        assertEquals(5.0, credited.availableFruitHundredths / 100.0, 0.0)
        assertEquals(1, credited.entries.size)
        assertEquals(FruitEntryKind.CREDIT, credited.entries.single().kind)
        assertEquals(FruitEntryStatus.COMMITTED, credited.entries.single().status)
        assertEquals(2L, credited.revision)
    }

    @Test
    fun aRepeatedRequestIdCannotCreditTwice() {
        val first = account()
            .credit(fruits = 5, title = "做家务", now = now, requestId = "req-1")
            .ok()
        val second = first.credit(
            fruits = 5,
            title = "做家务",
            now = now + 1L,
            requestId = "req-1",
        )

        assertTrue(second is FruitAccountResult.Rejected)
        assertEquals(FruitError.REQUEST_DUPLICATE, (second as FruitAccountResult.Rejected).error)
    }

    @Test
    fun creditRejectsInvalidFruitCountsTitlesAndNotes() {
        assertEquals(
            FruitError.FRUITS_INVALID,
            (account().credit(0, "x", now = now) as FruitAccountResult.Rejected).error,
        )
        assertEquals(
            FruitError.FRUITS_INVALID,
            (account().credit(1000, "x", now = now) as FruitAccountResult.Rejected).error,
        )
        assertEquals(
            FruitError.TITLE_INVALID,
            (account().credit(1, "  ", now = now) as FruitAccountResult.Rejected).error,
        )
        val longTitle = "果".repeat(41)
        assertEquals(
            FruitError.TITLE_INVALID,
            (account().credit(1, longTitle, now = now) as FruitAccountResult.Rejected).error,
        )
        assertEquals(
            FruitError.NOTE_INVALID,
            (account().credit(1, "x", note = "a".repeat(201), now = now)
                as FruitAccountResult.Rejected).error,
        )
    }

    @Test
    fun quickProjectsSaveUpdateAndDelete() {
        val first = account()
            .credit(
                fruits = 5,
                title = "做家务",
                saveQuickProject = true,
                quickProjectDefaultFruits = 5,
                now = now,
            )
            .ok()
        assertEquals(1, first.quickProjects.size)
        assertEquals("做家务", first.quickProjects.single().name)
        assertEquals(FruitProjectDirection.INCOME, first.quickProjects.single().direction)

        val updated = first
            .credit(
                fruits = 3,
                title = "做家务",
                saveQuickProject = true,
                quickProjectDefaultFruits = 3,
                now = now + 1L,
            )
            .ok()
        assertEquals(1, updated.quickProjects.size)
        assertEquals(3, updated.quickProjects.single().defaultFruits)

        val removed = updated
            .deleteQuickProject(updated.quickProjects.single().id, now + 2L)
            .ok()
        assertTrue(removed.quickProjects.isEmpty())
        assertEquals(
            FruitError.PROJECT_NOT_FOUND,
            (removed.deleteQuickProject("missing", now + 3L) as FruitAccountResult.Rejected).error,
        )
    }

    @Test
    fun quickProjectsKeepIncomeAndExpenseDirectionsSeparate() {
        val first = account()
            .credit(
                fruits = 5,
                title = "阅读",
                saveQuickProject = true,
                now = now,
            )
            .ok()
        val second = first
            .credit(
                fruits = 5,
                title = "阅读",
                saveQuickProject = true,
                now = now + 1L,
            )
            .ok()
        assertEquals(1, second.quickProjects.size)

        val third = second
            .recordManualExpense(
                fruits = 2,
                title = "阅读",
                saveQuickProject = true,
                now = now + 2L,
            )
            .ok()
        assertEquals(2, third.quickProjects.size)
        assertEquals(
            FruitProjectDirection.INCOME,
            third.quickProjects.first { it.name == "阅读" && it.direction == FruitProjectDirection.INCOME }
                .direction,
        )
        assertEquals(
            2,
            third.quickProjects.first { it.direction == FruitProjectDirection.EXPENSE }.defaultFruits,
        )

        val fourth = third
            .recordManualExpense(
                fruits = 1,
                title = "阅读",
                saveQuickProject = true,
                now = now + 3L,
            )
            .ok()
        assertEquals(2, fourth.quickProjects.size)
        assertEquals(
            1,
            fourth.quickProjects.first { it.direction == FruitProjectDirection.EXPENSE }.defaultFruits,
        )
    }

    @Test
    fun manualExpenseDebitsImmediatelyAndOnlyItsOwnRowsCanBeVoided() {
        val credited = account()
            .credit(fruits = 5, title = "做家务", now = now)
            .ok()
        val spent = credited
            .recordManualExpense(
                fruits = 2,
                title = "买玩具",
                note = "超市",
                now = now + 1L,
                requestId = "expense-1",
            )
            .ok()

        assertEquals(300L, spent.availableFruitHundredths)
        assertEquals(3L, spent.revision)
        val entry = spent.entries.last()
        assertEquals(FruitEntryKind.EXCHANGE, entry.kind)
        assertEquals(FruitEntryStatus.COMMITTED, entry.status)
        assertEquals(-200L, entry.fruitHundredths)
        assertEquals(2, entry.minutes)
        assertEquals(1, entry.minutesPerFruit)
        assertNull(entry.sessionId)

        // The request id is part of the same atomic document as the balance.
        assertEquals(
            FruitError.REQUEST_DUPLICATE,
            (spent.recordManualExpense(
                fruits = 2,
                title = "买玩具",
                now = now + 2L,
                requestId = "expense-1",
            ) as FruitAccountResult.Rejected).error,
        )
        assertEquals(
            FruitError.BALANCE_INSUFFICIENT,
            (spent.recordManualExpense(
                fruits = 4,
                title = "买玩具",
                now = now + 2L,
            ) as FruitAccountResult.Rejected).error,
        )

        val voided = spent.voidManualExpense(entry.id, now + 3L).ok()
        assertEquals(500L, voided.availableFruitHundredths)
        assertEquals(
            FruitEntryStatus.VOID,
            voided.entries.first { it.id == entry.id }.status,
        )
        // Idempotent: a retried void does not restore the balance twice or bump the revision.
        assertEquals(voided, voided.voidManualExpense(entry.id, now + 4L).ok())

        val creditId = credited.entries.single().id
        assertEquals(
            FruitError.VOID_NOT_ALLOWED,
            (credited.voidManualExpense(creditId, now + 5L) as FruitAccountResult.Rejected).error,
        )

        val tvExchange = account(balance = 500L)
            .reserveExchange(1, "session-manual-guard", "哔哩哔哩", now)
            .ok()
        assertEquals(
            FruitError.VOID_NOT_ALLOWED,
            (tvExchange.account.voidManualExpense(tvExchange.entry.id, now + 6L)
                as FruitAccountResult.Rejected).error,
        )
    }

    @Test
    fun manualExpenseRejectsInvalidRatesAndOverspending() {
        assertEquals(
            FruitError.RATE_INVALID,
            (account(balance = 500L, rate = 61)
                .recordManualExpense(1, "x", now = now) as FruitAccountResult.Rejected).error,
        )
        assertEquals(
            FruitError.BALANCE_INSUFFICIENT,
            (account(balance = 199L)
                .recordManualExpense(2, "x", now = now) as FruitAccountResult.Rejected).error,
        )
        assertEquals(
            FruitError.TITLE_INVALID,
            (account(balance = 500L)
                .recordManualExpense(1, " ", now = now) as FruitAccountResult.Rejected).error,
        )
        // Manual spending is bookkeeping, not a TV session, so the exchange switch does not
        // block the parent from recording it.
        assertEquals(
            400L,
            account(balance = 500L, enabled = false)
                .recordManualExpense(1, "线下使用", now = now)
                .ok()
                .availableFruitHundredths,
        )
    }

    @Test
    fun theTwentyFirstQuickProjectIsRejected() {
        val projects = (1..20).map {
            FruitQuickProject(id = "fp_$it", name = "项目$it", defaultFruits = 1)
        }
        val result = account(projects = projects).credit(
            fruits = 1,
            title = "第二十一个",
            saveQuickProject = true,
            now = now,
        )

        assertEquals(FruitError.PROJECT_LIMIT, (result as FruitAccountResult.Rejected).error)
    }

    @Test
    fun reserveDebitsOnlyWholeFruitsAndKeepsTheFractionalTail() {
        val reservation = account(balance = 1234L)
            .reserveExchange(fruits = 3, sessionId = "session-1", title = "哔哩哔哩", now = now)
            .ok()

        assertEquals(934L, reservation.account.availableFruitHundredths)
        assertEquals(9.34, reservation.account.availableFruitHundredths / 100.0, 0.0)
        assertEquals(FruitEntryKind.EXCHANGE, reservation.entry.kind)
        assertEquals(FruitEntryStatus.PENDING, reservation.entry.status)
        assertEquals(-300L, reservation.entry.fruitHundredths)
        assertEquals(3, reservation.entry.minutes)
        assertEquals(1, reservation.entry.minutesPerFruit)
        assertEquals("session-1", reservation.entry.sessionId)
        assertEquals(2L, reservation.account.revision)
    }

    @Test
    fun reserveRejectsFractionalBalanceAndOverspending() {
        assertEquals(
            FruitError.BALANCE_INSUFFICIENT,
            (account(balance = 99L).reserveExchange(1, "s", "app", now)
                as FruitReserveResult.Rejected).error,
        )
        assertEquals(
            FruitError.BALANCE_INSUFFICIENT,
            (account(balance = 200L).reserveExchange(3, "s", "app", now)
                as FruitReserveResult.Rejected).error,
        )
    }

    @Test
    fun reserveRejectsDisabledAccountsAndInvalidRates() {
        assertEquals(
            FruitError.ACCOUNT_DISABLED,
            (account(balance = 500L, enabled = false)
                .reserveExchange(1, "s", "app", now) as FruitReserveResult.Rejected).error,
        )
        assertEquals(
            FruitError.RATE_INVALID,
            (account(balance = 500L, rate = 61)
                .reserveExchange(1, "s", "app", now) as FruitReserveResult.Rejected).error,
        )
    }

    @Test
    fun commitAndVoidAreIdempotentButNeverResurrectEachOther() {
        val reserved = account(balance = 500L)
            .reserveExchange(2, "session-2", "app", now)
            .ok()
        val pending = reserved.account
        val entryId = reserved.entry.id

        val committed = pending.commitExchange(entryId, now + 1L).ok()
        assertEquals(FruitEntryStatus.COMMITTED, committed.entries.single().status)
        assertEquals(committed, committed.commitExchange(entryId, now + 2L).ok())
        assertEquals(
            FruitEntryStatus.COMMITTED,
            committed.entries.single().status,
        )
        assertEquals(
            FruitError.ENTRY_NOT_PENDING,
            (committed.voidExchange(entryId, now + 2L) as FruitAccountResult.Rejected).error,
        )

        val voided = pending.voidExchange(entryId, now + 1L).ok()
        assertEquals(500L, voided.availableFruitHundredths)
        assertEquals(FruitEntryStatus.VOID, voided.entries.single().status)
        assertEquals(voided, voided.voidExchange(entryId, now + 2L).ok())
        assertEquals(
            FruitError.ENTRY_NOT_PENDING,
            (voided.commitExchange(entryId, now + 2L) as FruitAccountResult.Rejected).error,
        )
    }

    @Test
    fun reconciliationCommitsStartedSessionsAndRestoresTheOthers() {
        val first = account(balance = 1000L)
            .reserveExchange(2, "live-session", "app", now)
            .ok()
        val second = first.account
            .reserveExchange(3, "dead-session", "app", now + 1L)
            .ok()

        val result = second.account.reconcilePendingExchanges(now + 2L) {
            it == "live-session"
        }

        assertEquals(1, result.committedEntryIds.size)
        assertEquals(1, result.voidedEntryIds.size)
        // The live exchange stays charged (200), the failed one is restored (300).
        assertEquals(800L, result.account.availableFruitHundredths)
        assertEquals(
            FruitEntryStatus.COMMITTED,
            result.account.entries.first { it.sessionId == "live-session" }.status,
        )
        assertEquals(
            FruitEntryStatus.VOID,
            result.account.entries.first { it.sessionId == "dead-session" }.status,
        )
        // Two reservations and one reconciliation are three document writes.
        assertEquals(4L, result.account.revision)
    }

    @Test
    fun reconciliationWithoutPendingRowsDoesNotBumpTheRevision() {
        val result = account(balance = 500L).reconcilePendingExchanges(now) { true }

        assertEquals(500L, result.account.availableFruitHundredths)
        assertEquals(1L, result.account.revision)
        assertTrue(result.committedEntryIds.isEmpty())
        assertTrue(result.voidedEntryIds.isEmpty())
    }

    @Test
    fun changingTheRateNeverRewritesHistory() {
        val reserved = account(balance = 1000L, rate = 1)
            .reserveExchange(2, "session-3", "app", now)
            .ok()
        val changed = reserved.account.setExchangeRate(3, now + 1L).ok()

        assertEquals(3, changed.exchangeRateMinutesPerFruit)
        assertEquals(1, changed.entries.single().minutesPerFruit)
        assertEquals(2, changed.entries.single().minutes)
        assertEquals(
            FruitError.RATE_INVALID,
            (changed.setExchangeRate(61, now + 2L) as FruitAccountResult.Rejected).error,
        )
    }

    @Test
    fun voidingACreditAppendsANegativeAdjustment() {
        val credited = account()
            .credit(fruits = 5, title = "做家务", now = now)
            .ok()
        val entryId = credited.entries.single().id

        val voided = credited.voidCredit(entryId, now + 1L).ok()

        assertEquals(0L, voided.availableFruitHundredths)
        assertEquals(2, voided.entries.size)
        val adjustment = voided.entries.last()
        assertEquals(FruitEntryKind.ADJUST, adjustment.kind)
        assertEquals(-500L, adjustment.fruitHundredths)
        assertEquals(entryId, adjustment.sourceEntryId)
        assertEquals(
            FruitError.ENTRY_ALREADY_VOIDED,
            (voided.voidCredit(entryId, now + 2L) as FruitAccountResult.Rejected).error,
        )
    }

    @Test
    fun aCreditWhoseFruitsWereSpentCannotBeVoided() {
        val credited = account()
            .credit(fruits = 5, title = "做家务", now = now)
            .ok()
        val spent = credited
            .reserveExchange(2, "session-4", "app", now + 1L)
            .ok()
            .account
        val entryId = credited.entries.single().id

        assertEquals(
            FruitError.VOID_NOT_ALLOWED,
            (spent.voidCredit(entryId, now + 2L) as FruitAccountResult.Rejected).error,
        )
    }

    @Test
    fun historyTrimmingKeepsPendingRowsAndTheNewestSettledRows() {
        val entries = (1..510).map { index ->
            FruitEntry(
                id = "e_$index",
                kind = FruitEntryKind.CREDIT,
                fruitHundredths = 100L,
                title = "credit",
                createdAtEpochMs = index.toLong(),
            )
        } + FruitEntry(
            id = "pending",
            kind = FruitEntryKind.EXCHANGE,
            fruitHundredths = -100L,
            title = "app",
            minutes = 1,
            minutesPerFruit = 1,
            sessionId = "session-5",
            createdAtEpochMs = 1L,
            status = FruitEntryStatus.PENDING,
        )

        val trimmed = trimEntries(entries)

        assertEquals(FruitAccountRules.MAX_SETTLED_ENTRIES + 1, trimmed.size)
        assertTrue(trimmed.any { it.id == "pending" })
        assertEquals("e_510", trimmed.filter { it.status != FruitEntryStatus.PENDING }.last().id)
        assertFalse(trimmed.any { it.id == "e_1" })
    }

    @Test
    fun canonicalJsonRoundTripsTheSharedFormat() {
        val original = account(balance = 1234L, rate = 2)
            .credit(fruits = 5, title = "做家务", saveQuickProject = true, now = now)
            .ok()
            .reserveExchange(3, "session-6", "哔哩哔哩", now + 1L)
            .ok()
            .account
            .let { it.commitExchange(it.entries.last().id, now + 2L).ok() }

        val encoded = FruitAccountCodec.encode(original)
        val decodedLocal = FruitAccountCodec.decode(encoded, FruitAccountSource.LOCAL)
        val decodedHosted = FruitAccountCodec.decode(encoded, FruitAccountSource.LOCAL)
            ?.copy(source = FruitAccountSource.HOSTED)
            ?.let { FruitAccountCodec.decode(FruitAccountCodec.encode(it)) }

        assertEquals(original, decodedLocal)
        assertEquals(original.memberId, decodedHosted?.memberId)
        assertEquals(original.availableFruitHundredths, decodedHosted?.availableFruitHundredths)
        assertEquals(original.exchangeRateMinutesPerFruit, decodedHosted?.exchangeRateMinutesPerFruit)
        assertEquals(original.entries, decodedHosted?.entries)
        assertEquals(original.quickProjects, decodedHosted?.quickProjects)
        // Credit is one row; committing the reservation updates that row instead of adding one.
        assertEquals(2, decodedLocal?.entries?.size)
        val exchange = decodedLocal?.entries?.first { it.kind == FruitEntryKind.EXCHANGE }
        assertEquals(6, exchange?.minutes)
        assertEquals(2, exchange?.minutesPerFruit)
        assertEquals(-300L, exchange?.fruitHundredths)
    }

    @Test
    fun decodingToleratesMissingOptionalSectionsAndRate() {
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("memberId", member)
            .put("availableFruitHundredths", 500)
        val decoded = FruitAccountCodec.decode(json.toString())

        assertNotNull(decoded)
        assertEquals(FruitAccountRules.DEFAULT_RATE, decoded?.exchangeRateMinutesPerFruit)
        assertTrue(decoded?.entries?.isEmpty() == true)
        assertTrue(decoded?.quickProjects?.isEmpty() == true)
        assertTrue(decoded?.enabled == true)
    }

    @Test
    fun projectsWithoutDirectionDecodeAsIncomeForSchemaOneCompatibility() {
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("memberId", member)
            .put("availableFruitHundredths", 500)
            .put(
                "quickProjects",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("id", "fp_legacy")
                        .put("name", "旧项目")
                        .put("defaultFruits", 2),
                ).put(
                    JSONObject()
                        .put("id", "fp_unknown")
                        .put("name", "未知方向")
                        .put("defaultFruits", 3)
                        .put("direction", "SOMEDAY"),
                ),
            )
        val decoded = FruitAccountCodec.decode(json.toString())

        assertEquals(2, decoded?.quickProjects?.size)
        assertTrue(
            decoded?.quickProjects?.all { it.direction == FruitProjectDirection.INCOME } == true,
        )
    }

    @Test
    fun decodingRejectsInvalidBalancesRatesAndDuplicateIds() {
        fun base() = JSONObject()
            .put("schemaVersion", 1)
            .put("memberId", member)
            .put("availableFruitHundredths", 500)

        assertNull(FruitAccountCodec.decode(base().put("availableFruitHundredths", -1).toString()))
        assertNull(FruitAccountCodec.decode(base().put("exchangeRateMinutesPerFruit", 0).toString()))
        assertNull(FruitAccountCodec.decode(base().put("exchangeRateMinutesPerFruit", 61).toString()))
        assertNull(FruitAccountCodec.decode(base().put("schemaVersion", 2).toString()))

        val duplicate = base().put(
            "entries",
            org.json.JSONArray()
                .put(
                    JSONObject()
                        .put("id", "same")
                        .put("kind", "CREDIT")
                        .put("fruitHundredths", 100)
                        .put("title", "a")
                        .put("createdAtEpochMs", 1)
                        .put("status", "COMMITTED"),
                )
                .put(
                    JSONObject()
                        .put("id", "same")
                        .put("kind", "CREDIT")
                        .put("fruitHundredths", 100)
                        .put("title", "b")
                        .put("createdAtEpochMs", 2)
                        .put("status", "COMMITTED"),
                ),
        )
        assertNull(FruitAccountCodec.decode(duplicate.toString()))
    }

    @Test
    fun unknownFieldsFromAnotherWriterAreIgnored() {
        val json = JSONObject(FruitAccountCodec.encode(account(balance = 500L)))
            .put("balanceType", "TOTAL")
            .put("checkin", true)
            .put("usageRecordId", 99)

        val decoded = FruitAccountCodec.decode(json.toString())

        assertEquals(500L, decoded?.availableFruitHundredths)
    }
}