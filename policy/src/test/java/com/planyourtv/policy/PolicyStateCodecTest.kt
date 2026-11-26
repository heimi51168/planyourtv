package com.planyourtv.policy

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyStateCodecTest {
    private val boot = BootEvidence("boot-a", 1_000L)

    private fun session() = SessionSnapshot(
        sessionId = "rule-1",
        phase = SessionPhase.ACTIVE,
        totalSeconds = 600L,
        remainingSeconds = 420L,
        endReason = LocalSessionEnd.SESSION_LIMIT,
        usageDate = "2026-11-16",
        startedAtEpochMs = 1_700_000_000_000L,
        planDeadlineEpochMs = 1_700_003_600_000L,
        wallDeadlineEpochMs = 1_700_004_800_000L,
        checkpointWallMs = 1_700_000_180_000L,
        checkpointElapsedMs = 180_000L,
        checkpointAsleepMs = 4_000L,
        boot = boot,
        activation = PolicyActivation.MANUAL,
        accessMode = AccessMode.APPS,
        allowedPackages = setOf("com.example.video"),
        origin = PolicySessionOrigin.GRANT,
        remoteRecordId = 77,
        reportUsage = true,
        templateVersion = 12L,
        restAfterSeconds = 300L,
    )

    private fun runtime() = RuntimeSnapshot(
        session = session(),
        ledger = RuntimeLedger(
            days = mapOf("2026-11-16" to 180L, "2026-11-15" to 900L),
            breakUntilEpochMs = 1_700_001_000_000L,
            lastEndedAtEpochMs = 1_700_000_500_000L,
            settled = mapOf(
                "rule-old" to SettledSessionSnapshot(
                    consumedSeconds = 180L,
                    endedAtEpochMs = 1_700_000_400_000L,
                    reason = PolicyEndReason.SESSION_LIMIT,
                    usageDate = "2026-11-16",
                    remoteRecordId = 41,
                ),
            ),
            pendingGrant = PendingGrantSnapshot(
                recordId = 88,
                source = PendingGrantSource.GRANT,
                durationSeconds = 900L,
                expectedEpoch = 123L,
                requestedScope = setOf("com.example.video"),
                accessMode = AccessMode.APPS,
                allowedPackages = setOf("com.example.video"),
                restAfterSeconds = 300L,
                armedAtEpochMs = 1_700_000_000_000L,
                deadlineEpochMs = 1_700_000_600_000L,
                validUntilLabel = "20:00",
                userName = "parent",
                userIconUrl = "https://example.test/icon.png",
            ),
            routeUsage = RouteUsage(
                date = "2026-11-16",
                quizGranted = 1,
                studyRewardGranted = 2,
                directGranted = 3,
                fruitGranted = 4,
            ),
        ),
    )

    @Test
    fun runtimeRoundTripPreservesSessionLedgerAndPending() {
        val decoded = PolicyStateCodec.decodeRuntime(PolicyStateCodec.encodeRuntime(runtime()))
        assertTrue(decoded is PolicySnapshotDecode.Success)
        val value = (decoded as PolicySnapshotDecode.Success).value
        assertEquals(session(), value.session)
        assertEquals(2, value.ledger.days.size)
        assertEquals(180L, value.ledger.days["2026-11-16"])
        assertEquals(1, value.ledger.settled.size)
        assertEquals(41, value.ledger.settled["rule-old"]?.remoteRecordId)
        assertEquals(88, value.ledger.pendingGrant?.recordId)
        assertEquals(PendingGrantSource.GRANT, value.ledger.pendingGrant?.source)
        assertEquals(setOf("com.example.video"), value.ledger.pendingGrant?.requestedScope)
        assertEquals(
            RouteUsage(
                date = "2026-11-16",
                quizGranted = 1,
                studyRewardGranted = 2,
                directGranted = 3,
                fruitGranted = 4,
            ),
            value.ledger.routeUsage,
        )
    }

    @Test
    fun aV57RouteUsageWithoutFruitDecodesAsZeroFruit() {
        val root = JSONObject(PolicyStateCodec.encodeRuntime(runtime()))
        val payload = JSONObject(root.getString("payload"))
        payload.getJSONObject("ledger").getJSONObject("routeUsage").remove("fruit")
        root.put("payload", payload.toString()).put("crc", crc(payload))
        val decoded = PolicyStateCodec.decodeRuntime(root.toString())

        assertTrue(decoded is PolicySnapshotDecode.Success)
        val usage = (decoded as PolicySnapshotDecode.Success).value.ledger.routeUsage
        assertEquals(0, usage?.fruitGranted)
        assertEquals(1, usage?.quizGranted)
    }

    @Test
    fun aRuntimeDocumentWithoutRouteUsageStillLoads() {
        val root = JSONObject(PolicyStateCodec.encodeRuntime(runtime()))
        val payload = JSONObject(root.getString("payload"))
        payload.getJSONObject("ledger").remove("routeUsage")
        root.put("payload", payload.toString()).put("crc", crc(payload))
        val decoded = PolicyStateCodec.decodeRuntime(root.toString())
        assertTrue(decoded is PolicySnapshotDecode.Success)
        assertNull((decoded as PolicySnapshotDecode.Success).value.ledger.routeUsage)
    }

    @Test
    fun malformedRouteUsageFallsBackToNoCountsInsteadOfCorruptingTheSnapshot() {
        val root = JSONObject(PolicyStateCodec.encodeRuntime(runtime()))
        val payload = JSONObject(root.getString("payload"))
        payload.getJSONObject("ledger").put(
            "routeUsage",
            JSONObject().put("date", "2026-11-16").put("quiz", -1),
        )
        root.put("payload", payload.toString()).put("crc", crc(payload))
        val decoded = PolicyStateCodec.decodeRuntime(root.toString())
        assertTrue(decoded is PolicySnapshotDecode.Success)
        assertNull((decoded as PolicySnapshotDecode.Success).value.ledger.routeUsage)
    }

    private fun crc(payload: JSONObject): Long =
        java.util.zip.CRC32().apply {
            update(payload.toString().toByteArray(Charsets.UTF_8))
        }.value

    @Test
    fun nullAndEmptyRequestedScopeStayDistinct() {
        val base = runtime()
        val nullScope = base.copy(
            ledger = base.ledger.copy(
                pendingGrant = base.ledger.pendingGrant?.copy(requestedScope = null),
            ),
        )
        val emptyScope = base.copy(
            ledger = base.ledger.copy(
                pendingGrant = base.ledger.pendingGrant?.copy(requestedScope = emptySet()),
            ),
        )
        val decodedNull = PolicyStateCodec.decodeRuntime(
            PolicyStateCodec.encodeRuntime(nullScope),
        ) as PolicySnapshotDecode.Success
        val decodedEmpty = PolicyStateCodec.decodeRuntime(
            PolicyStateCodec.encodeRuntime(emptyScope),
        ) as PolicySnapshotDecode.Success
        assertNull(decodedNull.value.ledger.pendingGrant?.requestedScope)
        assertEquals(emptySet<String>(), decodedEmpty.value.ledger.pendingGrant?.requestedScope)
    }

    @Test
    fun corruptPayloadIsRejected() {
        val encoded = PolicyStateCodec.encodeRuntime(runtime())
        val root = JSONObject(encoded)
        val payload = root.getString("payload")
        root.put("payload", payload.replace("rule-1", "rule-2"))
        assertTrue(PolicyStateCodec.decodeRuntime(root.toString()) is PolicySnapshotDecode.Corrupt)
    }

    @Test
    fun unsupportedVersionIsNotInterpreted() {
        val root = JSONObject(PolicyStateCodec.encodeRuntime(runtime())).put("v", 99)
        val decoded = PolicyStateCodec.decodeRuntime(root.toString())
        assertEquals(
            99,
            (decoded as PolicySnapshotDecode.Unsupported).version,
        )
    }

    @Test
    fun blankSessionIdInvalidatesTheRuntimeSnapshot() {
        val root = JSONObject(PolicyStateCodec.encodeRuntime(runtime()))
        val payload = JSONObject(root.getString("payload"))
        payload.getJSONObject("session").put("sessionId", "")
        val crc = java.util.zip.CRC32().apply {
            update(payload.toString().toByteArray(Charsets.UTF_8))
        }.value
        root.put("payload", payload.toString()).put("crc", crc)
        assertTrue(PolicyStateCodec.decodeRuntime(root.toString()) is PolicySnapshotDecode.Corrupt)
    }

    @Test
    fun guardRoundTripAndPhaseConsistency() {
        val lease = OverrideLease(
            leaseId = "ovr-1",
            startedWallMs = 1_700_000_000_000L,
            startedElapsedMs = 100_000L,
            startedAsleepMs = 5_000L,
            untilWallMs = 0L,
            hardCapWallMs = 1_700_043_200_000L,
            hardCapElapsedMs = 43_300_000L,
            boot = boot,
            screenOffMarkerAtStartMs = 10_000L,
        )
        val guard = GuardSnapshot(
            phase = GuardStatus.PARENT_OVERRIDE,
            override = lease,
            screenOff = ScreenOffEvidence(120_000L, 1_700_000_020_000L, boot),
        )
        val decoded = PolicyStateCodec.decodeGuard(PolicyStateCodec.encodeGuard(guard))
        assertTrue(decoded is PolicySnapshotDecode.Success)
        val value = (decoded as PolicySnapshotDecode.Success).value
        assertEquals(GuardStatus.PARENT_OVERRIDE, value.phase)
        assertEquals(lease, value.override)
        assertEquals(120_000L, value.screenOff.elapsedMs)
        assertNull(value.study)

        val malformed = JSONObject(PolicyStateCodec.encodeGuard(guard))
        val payload = JSONObject(malformed.getString("payload"))
        payload.put("override", JSONObject.NULL)
        val crc = java.util.zip.CRC32().apply {
            update(payload.toString().toByteArray(Charsets.UTF_8))
        }.value
        malformed.put("payload", payload.toString()).put("crc", crc)
        assertTrue(PolicyStateCodec.decodeGuard(malformed.toString()) is PolicySnapshotDecode.Corrupt)
    }
}
