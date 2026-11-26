package com.planyourtv.policy

import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.CRC32

sealed interface PolicySnapshotDecode<out T> {
    data class Success<T>(val value: T) : PolicySnapshotDecode<T>

    data class Corrupt(val reason: String) : PolicySnapshotDecode<Nothing>

    data class Unsupported(val version: Int) : PolicySnapshotDecode<Nothing>
}

/**
 * JSON contract for the two durable execution partitions. The payload is stored as a string and
 * protected by a CRC32 over its exact bytes; the envelope itself only carries the schema version
 * and checksum. This avoids depending on JSON key ordering for the checksum.
 */
object PolicyStateCodec {
    const val VERSION = 2

    fun encodeRuntime(snapshot: RuntimeSnapshot): String =
        envelope(JSONObject().apply {
            put("session", snapshot.session?.toJson() ?: JSONObject.NULL)
            put("ledger", snapshot.ledger.toJson())
        }.toString())

    fun decodeRuntime(raw: String?): PolicySnapshotDecode<RuntimeSnapshot> =
        decodeEnvelope(raw) { payload ->
            val session = if (payload.isNull("session")) {
                null
            } else {
                payload.optJSONObject("session")?.toSession() ?: return@decodeEnvelope null
            }
            val ledger = payload.optJSONObject("ledger")?.toLedger() ?: return@decodeEnvelope null
            RuntimeSnapshot(session = session, ledger = ledger)
        }

    fun encodeGuard(snapshot: GuardSnapshot): String =
        envelope(JSONObject().apply {
            put("phase", snapshot.phase.name)
            put("override", snapshot.override?.toJson() ?: JSONObject.NULL)
            put("study", snapshot.study?.toJson() ?: JSONObject.NULL)
            put("screenOff", snapshot.screenOff.toJson())
        }.toString())

    fun decodeGuard(raw: String?): PolicySnapshotDecode<GuardSnapshot> =
        decodeEnvelope(raw) { payload ->
            val phase = runCatching {
                GuardStatus.valueOf(payload.getString("phase"))
            }.getOrNull() ?: return@decodeEnvelope null
            val override = if (payload.isNull("override")) {
                null
            } else {
                payload.optJSONObject("override")?.toOverrideLease()
                    ?: return@decodeEnvelope null
            }
            val study = if (payload.isNull("study")) {
                null
            } else {
                payload.optJSONObject("study")?.toStudyLease()
                    ?: return@decodeEnvelope null
            }
            val screenOff = payload.optJSONObject("screenOff")?.toScreenOff()
                ?: return@decodeEnvelope null
            when (phase) {
                GuardStatus.PARENT_OVERRIDE -> if (override == null) return@decodeEnvelope null
                GuardStatus.STUDY_MODE -> if (study == null) return@decodeEnvelope null
                GuardStatus.ENFORCING -> Unit
            }
            GuardSnapshot(
                phase = phase,
                override = if (phase == GuardStatus.PARENT_OVERRIDE) override else null,
                study = if (phase == GuardStatus.STUDY_MODE) study else null,
                screenOff = screenOff,
            )
        }

    private fun envelope(payload: String): String {
        val crc = CRC32().apply { update(payload.toByteArray(Charsets.UTF_8)) }.value
        return JSONObject()
            .put("v", VERSION)
            .put("crc", crc)
            .put("payload", payload)
            .toString()
    }

    private fun <T> decodeEnvelope(
        raw: String?,
        parse: (JSONObject) -> T?,
    ): PolicySnapshotDecode<T> {
        if (raw.isNullOrBlank()) return PolicySnapshotDecode.Corrupt("empty")
        return try {
            val root = JSONObject(raw)
            val version = root.optInt("v", -1)
            if (version != VERSION) {
                return if (version >= 0) {
                    PolicySnapshotDecode.Unsupported(version)
                } else {
                    PolicySnapshotDecode.Corrupt("missing version")
                }
            }
            val payload = root.optString("payload", "")
            if (payload.isBlank() || !root.has("crc")) {
                return PolicySnapshotDecode.Corrupt("missing payload")
            }
            val actual = CRC32().apply { update(payload.toByteArray(Charsets.UTF_8)) }.value
            if (actual != root.optLong("crc", -1L)) {
                return PolicySnapshotDecode.Corrupt("crc mismatch")
            }
            val json = JSONObject(payload)
            val decoded = parse(json)
                ?: return PolicySnapshotDecode.Corrupt("invalid payload")
            PolicySnapshotDecode.Success(decoded)
        } catch (error: Exception) {
            PolicySnapshotDecode.Corrupt(error.javaClass.simpleName)
        }
    }

    private fun SessionSnapshot.toJson(): JSONObject = JSONObject()
        .put("sessionId", sessionId)
        .put("phase", phase.name)
        .put("total", totalSeconds)
        .put("remaining", remainingSeconds)
        .put("endReason", endReason.name)
        .put("usageDate", usageDate)
        .put("startedWall", startedAtEpochMs)
        .put("planDeadlineWall", planDeadlineEpochMs)
        .put("wallDeadlineWall", wallDeadlineEpochMs)
        .put("pausedAtWall", pausedAtEpochMs)
        .put("pauseReason", pauseReason?.name ?: JSONObject.NULL)
        .put("checkpointWall", checkpointWallMs)
        .put("checkpointElapsed", checkpointElapsedMs)
        .put("checkpointAsleep", checkpointAsleepMs)
        .put("bootId", boot.bootId)
        .put("bootStamp", boot.bootStampMs)
        .put("activation", activation.name)
        .put("accessMode", accessMode.name)
        .put("allowedPackages", allowedPackages.toJsonArray())
        .put("origin", origin?.name ?: JSONObject.NULL)
        .put("remoteRecordId", remoteRecordId)
        .put("reportUsage", reportUsage)
        .put("templateVersion", templateVersion)
        .put("restAfter", restAfterSeconds)
        .put("outsidePlan", outsidePlan)

    private fun JSONObject.toSession(): SessionSnapshot? {
        val sessionId = optString("sessionId", "")
        if (sessionId.isBlank()) return null
        val phase = enumValue<SessionPhase>("phase") ?: return null
        val endReason = enumValue<LocalSessionEnd>("endReason") ?: return null
        val activation = enumValue<PolicyActivation>("activation") ?: return null
        val accessMode = enumValue<AccessMode>("accessMode") ?: return null
        val origin = stringOrNull("origin")?.let {
            runCatching { PolicySessionOrigin.valueOf(it) }.getOrNull()
        }
        if (stringOrNull("origin") != null && origin == null) return null
        val pauseReason = stringOrNull("pauseReason")?.let {
            runCatching { SessionPauseReason.valueOf(it) }.getOrNull()
        }
        if (stringOrNull("pauseReason") != null && pauseReason == null) return null
        val total = optLong("total", 0L)
        val remaining = optLong("remaining", -1L)
        if (total <= 0L || remaining < 0L || remaining > total) return null
        return SessionSnapshot(
            sessionId = sessionId,
            phase = phase,
            totalSeconds = total,
            remainingSeconds = remaining,
            endReason = endReason,
            usageDate = optString("usageDate", ""),
            startedAtEpochMs = optLong("startedWall", 0L),
            planDeadlineEpochMs = optLong("planDeadlineWall", 0L),
            wallDeadlineEpochMs = optLong("wallDeadlineWall", 0L),
            pausedAtEpochMs = optLong("pausedAtWall", 0L),
            pauseReason = pauseReason,
            checkpointWallMs = optLong("checkpointWall", 0L),
            checkpointElapsedMs = optLong("checkpointElapsed", 0L),
            checkpointAsleepMs = optLong("checkpointAsleep", 0L),
            boot = BootEvidence(
                bootId = optString("bootId", ""),
                bootStampMs = optLong("bootStamp", 0L),
            ),
            activation = activation,
            accessMode = accessMode,
            allowedPackages = optJSONArray("allowedPackages")?.toStringSet() ?: emptySet(),
            origin = origin,
            remoteRecordId = optInt("remoteRecordId", 0),
            reportUsage = optBoolean("reportUsage", false),
            templateVersion = optLong("templateVersion", 0L),
            restAfterSeconds = optLong("restAfter", 0L),
            outsidePlan = optBoolean("outsidePlan", false),
        )
    }

    private fun RuntimeLedger.toJson(): JSONObject = JSONObject().apply {
        put("days", JSONObject().also { daysJson ->
            days.forEach { (date, seconds) ->
                if (date.isNotBlank() && seconds >= 0L) daysJson.put(date, seconds)
            }
        })
        put("studyDays", JSONObject().also { daysJson ->
            studyDays.forEach { (date, seconds) ->
                if (date.isNotBlank() && seconds >= 0L) daysJson.put(date, seconds)
            }
        })
        put("breakUntil", breakUntilEpochMs)
        put("lastEndedAt", lastEndedAtEpochMs)
        put("settled", JSONObject().also { settledJson ->
            settled.forEach { (id, entry) ->
                if (id.isNotBlank()) settledJson.put(id, entry.toJson())
            }
        })
        put("pendingGrant", pendingGrant?.toJson() ?: JSONObject.NULL)
        put("routeUsage", routeUsage?.toJson() ?: JSONObject.NULL)
    }

    private fun JSONObject.toLedger(): RuntimeLedger? {
        val days = optJSONObject("days")?.let { json ->
            buildMap {
                json.keys().forEach { date ->
                    val seconds = json.optLong(date, -1L)
                    if (date.isNotBlank() && seconds >= 0L) put(date, seconds)
                }
            }
        } ?: emptyMap()
        val studyDays = optJSONObject("studyDays")?.let { json ->
            buildMap {
                json.keys().forEach { date ->
                    val seconds = json.optLong(date, -1L)
                    if (date.isNotBlank() && seconds >= 0L) put(date, seconds)
                }
            }
        } ?: emptyMap()
        val settled = optJSONObject("settled")?.let { json ->
            buildMap {
                json.keys().forEach { id ->
                    val entry = json.optJSONObject(id)?.toSettled() ?: return null
                    put(id, entry)
                }
            }
        } ?: emptyMap()
        val pending = if (isNull("pendingGrant")) {
            null
        } else {
            optJSONObject("pendingGrant")?.toPendingGrant() ?: return null
        }
        // Missing or malformed V44 counts mean "no route usage yet"; they must not make an
        // otherwise valid session/ledger snapshot unreadable after an upgrade.
        val routeUsage = if (isNull("routeUsage")) {
            null
        } else {
            optJSONObject("routeUsage")?.toRouteUsage()
        }
        return RuntimeLedger(
            days = days,
            studyDays = studyDays,
            breakUntilEpochMs = optLong("breakUntil", 0L),
            lastEndedAtEpochMs = optLong("lastEndedAt", 0L),
            settled = settled,
            pendingGrant = pending,
            routeUsage = routeUsage,
        )
    }

    private fun RouteUsage.toJson(): JSONObject = JSONObject()
        .put("date", date)
        .put("quiz", quizGranted)
        .put("studyReward", studyRewardGranted)
        .put("directFree", directGranted)
        .put("fruit", fruitGranted)

    private fun JSONObject.toRouteUsage(): RouteUsage? {
        val date = optString("date", "")
        if (date.isBlank()) return null
        val quiz = optInt("quiz", -1)
        val study = optInt("studyReward", -1)
        val direct = optInt("directFree", -1)
        if (quiz < 0 || study < 0 || direct < 0) return null
        // A snapshot written before V58 has no fruit count. Treat it as zero instead of
        // corrupting the whole day's ledger; the other three counts stay strict.
        val fruit = optInt("fruit", 0).coerceAtLeast(0)
        return RouteUsage(
            date = date,
            quizGranted = quiz,
            studyRewardGranted = study,
            directGranted = direct,
            fruitGranted = fruit,
        )
    }

    private fun SettledSessionSnapshot.toJson(): JSONObject = JSONObject()
        .put("consumed", consumedSeconds)
        .put("endedAt", endedAtEpochMs)
        .put("reason", reason.name)
        .put("usageDate", usageDate)
        .put("remoteRecordId", remoteRecordId)

    private fun JSONObject.toSettled(): SettledSessionSnapshot? {
        val reason = enumValue<PolicyEndReason>("reason") ?: return null
        val consumed = optLong("consumed", -1L)
        if (consumed < 0L) return null
        return SettledSessionSnapshot(
            consumedSeconds = consumed,
            endedAtEpochMs = optLong("endedAt", 0L),
            reason = reason,
            usageDate = optString("usageDate", ""),
            remoteRecordId = optInt("remoteRecordId", 0),
        )
    }

    private fun PendingGrantSnapshot.toJson(): JSONObject = JSONObject()
        .put("recordId", recordId)
        .put("source", source.name)
        .put("duration", durationSeconds)
        .put("expectedEpoch", expectedEpoch)
        .put("requestedScope", requestedScope?.toJsonArray() ?: JSONObject.NULL)
        .put("accessMode", accessMode.name)
        .put("allowedPackages", allowedPackages.toJsonArray())
        .put("restAfter", restAfterSeconds)
        .put("armedAt", armedAtEpochMs)
        .put("deadline", deadlineEpochMs)
        .put("validUntil", validUntilLabel)
        .put("userName", userName)
        .put("userIconUrl", userIconUrl)

    private fun JSONObject.toPendingGrant(): PendingGrantSnapshot? {
        val source = enumValue<PendingGrantSource>("source") ?: return null
        val accessMode = enumValue<AccessMode>("accessMode") ?: return null
        val duration = optLong("duration", 0L)
        if (duration <= 0L) return null
        val requested = if (isNull("requestedScope")) {
            null
        } else {
            optJSONArray("requestedScope")?.toStringSet() ?: return null
        }
        return PendingGrantSnapshot(
            recordId = optInt("recordId", 0),
            source = source,
            durationSeconds = duration,
            expectedEpoch = optLong("expectedEpoch", 0L),
            requestedScope = requested,
            accessMode = accessMode,
            allowedPackages = optJSONArray("allowedPackages")?.toStringSet() ?: emptySet(),
            restAfterSeconds = optLong("restAfter", 0L),
            armedAtEpochMs = optLong("armedAt", 0L),
            deadlineEpochMs = optLong("deadline", 0L),
            validUntilLabel = optString("validUntil", ""),
            userName = optString("userName", ""),
            userIconUrl = optString("userIconUrl", ""),
        )
    }

    private fun OverrideLease.toJson(): JSONObject = JSONObject()
        .put("id", leaseId)
        .put("startedWall", startedWallMs)
        .put("startedElapsed", startedElapsedMs)
        .put("startedAsleep", startedAsleepMs)
        .put("untilWall", untilWallMs)
        .put("hardCapWall", hardCapWallMs)
        .put("hardCapElapsed", hardCapElapsedMs)
        .put("bootId", boot.bootId)
        .put("bootStamp", boot.bootStampMs)
        .put("screenOffAtStart", screenOffMarkerAtStartMs)

    private fun JSONObject.toOverrideLease(): OverrideLease? {
        val id = optString("id", "")
        if (id.isBlank()) return null
        val startedWall = optLong("startedWall", 0L)
        val startedElapsed = optLong("startedElapsed", 0L)
        if (startedWall <= 0L || startedElapsed <= 0L) return null
        return OverrideLease(
            leaseId = id,
            startedWallMs = startedWall,
            startedElapsedMs = startedElapsed,
            startedAsleepMs = optLong("startedAsleep", 0L),
            untilWallMs = optLong("untilWall", 0L),
            hardCapWallMs = optLong("hardCapWall", 0L),
            hardCapElapsedMs = optLong("hardCapElapsed", 0L),
            boot = BootEvidence(
                bootId = optString("bootId", ""),
                bootStampMs = optLong("bootStamp", 0L),
            ),
            screenOffMarkerAtStartMs = optLong("screenOffAtStart", 0L),
        )
    }

    private fun StudyLease.toJson(): JSONObject = JSONObject()
        .put("startedWall", startedWallMs)
        .put("startedElapsed", startedElapsedMs)
        .put("startedAsleep", startedAsleepMs)
        .put("hardCapWall", hardCapWallMs)
        .put("hardCapElapsed", hardCapElapsedMs)
        .put("bootId", boot.bootId)
        .put("bootStamp", boot.bootStampMs)

    private fun JSONObject.toStudyLease(): StudyLease? {
        val startedWall = optLong("startedWall", 0L)
        val startedElapsed = optLong("startedElapsed", 0L)
        if (startedWall <= 0L || startedElapsed <= 0L) return null
        return StudyLease(
            startedWallMs = startedWall,
            startedElapsedMs = startedElapsed,
            startedAsleepMs = optLong("startedAsleep", 0L),
            hardCapWallMs = optLong("hardCapWall", 0L),
            hardCapElapsedMs = optLong("hardCapElapsed", 0L),
            boot = BootEvidence(
                bootId = optString("bootId", ""),
                bootStampMs = optLong("bootStamp", 0L),
            ),
        )
    }

    private fun ScreenOffEvidence.toJson(): JSONObject = JSONObject()
        .put("elapsed", elapsedMs)
        .put("wall", wallMs)
        .put("bootId", boot.bootId)
        .put("bootStamp", boot.bootStampMs)

    private fun JSONObject.toScreenOff(): ScreenOffEvidence = ScreenOffEvidence(
        elapsedMs = optLong("elapsed", 0L),
        wallMs = optLong("wall", 0L),
        boot = BootEvidence(
            bootId = optString("bootId", ""),
            bootStampMs = optLong("bootStamp", 0L),
        ),
    )

    private inline fun <reified T : Enum<T>> JSONObject.enumValue(name: String): T? =
        stringOrNull(name)?.let { raw ->
            runCatching { enumValueOf<T>(raw) }.getOrNull()
        }

    private fun JSONObject.stringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name, "").takeIf { it.isNotBlank() }
    }

    private fun Set<String>.toJsonArray(): JSONArray = JSONArray().also { array ->
        forEach { array.put(it) }
    }

    private fun JSONArray.toStringSet(): Set<String> {
        val values = LinkedHashSet<String>()
        for (index in 0 until length()) {
            val value = optString(index, "")
            if (value.isNotBlank()) values.add(value)
        }
        return values
    }
}
