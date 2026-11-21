package com.planyourtv.policy

import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.CRC32

/**
 * JSON contract for the family cache documents. Every document is one atomic string protected
 * by a schema version and a CRC32 over the exact payload, matching [PolicyStateCodec]. A decode
 * never substitutes defaults for a damaged document: callers must fail closed and let the
 * migration/repair path decide whether recreating a default is safe.
 */
internal object FamilyCodecs {
    private const val ENVELOPE_VERSION = 1
    private const val MAX_SOURCE_VERSION_LENGTH = 80
    private const val MAX_AVATAR_PRESET_LENGTH = 32

    fun encodeDocument(document: FamilyDocument): String =
        envelope(JSONObject().apply {
            put("schemaVersion", document.schemaVersion)
            put("revision", document.revision)
            put("family", document.family.toJson())
            put("members", JSONArray().also { array ->
                document.members.forEach { array.put(it.toJson()) }
            })
        }.toString())

    fun decodeDocument(raw: String?, key: String): FamilyDecode<FamilyDocument> =
        decode(raw, key) { payload ->
            if (payload.optInt("schemaVersion", -1) != FamilyIds.SCHEMA_VERSION) return@decode null
            val revision = payload.optLong("revision", -1L)
            if (revision < 0L) return@decode null
            val family = payload.optJSONObject("family")?.toFamilyRecord() ?: return@decode null
            val memberArray = payload.optJSONArray("members") ?: return@decode null
            val members = buildList {
                for (index in 0 until memberArray.length()) {
                    val member = memberArray.optJSONObject(index)?.toMember() ?: return@decode null
                    if (member.familyId != family.familyId) return@decode null
                    add(member)
                }
            }
            if (members.isEmpty() || members.map { it.userId }.toSet().size != members.size) {
                return@decode null
            }
            FamilyDocument(
                schemaVersion = FamilyIds.SCHEMA_VERSION,
                family = family,
                members = members,
                revision = revision,
            )
        }

    fun encodeIndex(index: FamilyIndex): String =
        envelope(JSONObject().apply {
            put("schemaVersion", index.schemaVersion)
            put("familyId", index.familyId)
            put("defaultUserId", index.defaultUserId ?: JSONObject.NULL)
            put("revision", index.revision)
        }.toString())

    fun decodeIndex(raw: String?, key: String): FamilyDecode<FamilyIndex> =
        decode(raw, key) { payload ->
            if (payload.optInt("schemaVersion", -1) != FamilyIds.SCHEMA_VERSION) return@decode null
            val familyId = payload.requiredString("familyId") ?: return@decode null
            if (!FamilyKeys.validFamily(familyId)) return@decode null
            val defaultUserId = if (payload.isNull("defaultUserId")) {
                null
            } else {
                val value = payload.requiredString("defaultUserId") ?: return@decode null
                if (!FamilyIds.isValidUserId(value)) return@decode null
                value
            }
            val revision = payload.optLong("revision", -1L)
            if (revision < 0L) return@decode null
            FamilyIndex(
                familyId = familyId,
                defaultUserId = defaultUserId,
                revision = revision,
            )
        }

    fun encodeActiveSubject(record: ActiveSubjectRecord): String =
        envelope(JSONObject().apply {
            put("schemaVersion", record.schemaVersion)
            put("familyId", record.familyId)
            put("userId", record.userId)
            put("epoch", record.epoch)
            put("updatedAt", record.updatedAt)
        }.toString())

    fun decodeActiveSubject(raw: String?, key: String): FamilyDecode<ActiveSubjectRecord> =
        decode(raw, key) { payload ->
            if (payload.optInt("schemaVersion", -1) != FamilyIds.SCHEMA_VERSION) return@decode null
            val familyId = payload.requiredString("familyId") ?: return@decode null
            val userId = payload.requiredString("userId") ?: return@decode null
            if (!FamilyKeys.validSubject(familyId, userId)) return@decode null
            val epoch = payload.optLong("epoch", -1L)
            val updatedAt = payload.optLong("updatedAt", -1L)
            if (epoch < 0L || updatedAt <= 0L) return@decode null
            ActiveSubjectRecord(
                familyId = familyId,
                userId = userId,
                epoch = epoch,
                updatedAt = updatedAt,
            )
        }

    fun encodeConfig(document: SubjectConfigDocument): String =
        envelope(JSONObject().apply {
            put("schemaVersion", document.schemaVersion)
            put("familyId", document.familyId)
            put("userId", document.userId)
            put("controlOwner", document.controlOwner.name)
            put("source", document.source.name)
            put("revision", document.revision)
            put("activation", document.activation.name)
            put(
                "ruleSets",
                JSONObject(
                    LocalPolicyStore.encodeRuleSetsForSubject(document.ruleSets),
                ),
            )
            put("sourceVersion", document.sourceVersion ?: JSONObject.NULL)
            put("updatedAt", document.updatedAt)
        }.toString())

    fun decodeConfig(raw: String?, key: String): FamilyDecode<SubjectConfigDocument> =
        decode(raw, key) { payload ->
            if (payload.optInt("schemaVersion", -1) != FamilyIds.SCHEMA_VERSION) return@decode null
            val familyId = payload.requiredString("familyId") ?: return@decode null
            val userId = payload.requiredString("userId") ?: return@decode null
            if (!FamilyKeys.validSubject(familyId, userId)) return@decode null
            val controlOwner = payload.enumValue<ControlOwner>("controlOwner") ?: return@decode null
            val source = payload.enumValue<SubjectSource>("source") ?: return@decode null
            val activation = payload.enumValue<Activation>("activation") ?: return@decode null
            val revision = payload.optLong("revision", -1L)
            val updatedAt = payload.optLong("updatedAt", -1L)
            if (revision < 0L || updatedAt <= 0L) return@decode null
            val ruleSetsJson = payload.optJSONObject("ruleSets")?.toString() ?: return@decode null
            val ruleSets = LocalPolicyStore.decodeRuleSetsForSubject(ruleSetsJson)
                ?: return@decode null
            if (!ruleSets.isUsable) return@decode null
            val sourceVersion = payload.optionalString("sourceVersion")?.also {
                if (it.length > MAX_SOURCE_VERSION_LENGTH) return@decode null
            }
            SubjectConfigDocument(
                familyId = familyId,
                userId = userId,
                controlOwner = controlOwner,
                source = source,
                revision = revision,
                activation = activation,
                ruleSets = ruleSets,
                sourceVersion = sourceVersion,
                updatedAt = updatedAt,
            )
        }

    fun encodeRuntime(document: SubjectRuntimeDocument): String =
        envelope(JSONObject().apply {
            put("schemaVersion", document.schemaVersion)
            put("familyId", document.familyId)
            put("userId", document.userId)
            put("revision", document.revision)
            put("runtime", PolicyStateCodec.encodeRuntime(document.runtime))
            put("studyRound", document.studyRound?.let { LocalPolicyStore.encodeStudyRound(it) }
                ?: JSONObject.NULL)
            put("savedAt", document.savedAt)
        }.toString())

    fun decodeRuntime(raw: String?, key: String): FamilyDecode<SubjectRuntimeDocument> =
        decode(raw, key) { payload ->
            if (payload.optInt("schemaVersion", -1) != FamilyIds.SCHEMA_VERSION) return@decode null
            val familyId = payload.requiredString("familyId") ?: return@decode null
            val userId = payload.requiredString("userId") ?: return@decode null
            if (!FamilyKeys.validSubject(familyId, userId)) return@decode null
            val revision = payload.optLong("revision", -1L)
            val savedAt = payload.optLong("savedAt", -1L)
            if (revision < 0L || savedAt <= 0L) return@decode null
            val runtime = when (
                val decoded = PolicyStateCodec.decodeRuntime(
                    payload.optionalString("runtime"),
                )
            ) {
                is PolicySnapshotDecode.Success -> decoded.value
                else -> return@decode null
            }
            val studyRound = if (payload.isNull("studyRound")) {
                null
            } else {
                LocalPolicyStore.decodeStudyRound(
                    payload.optJSONObject("studyRound")?.toString(),
                ) ?: return@decode null
            }
            SubjectRuntimeDocument(
                familyId = familyId,
                userId = userId,
                revision = revision,
                runtime = runtime,
                studyRound = studyRound,
                savedAt = savedAt,
            )
        }

    fun encodeIdentityLinks(links: List<IdentityLink>): String =
        envelope(JSONObject().apply {
            put("schemaVersion", FamilyIds.SCHEMA_VERSION)
            put("links", JSONArray().also { array ->
                links.forEach { array.put(it.toJson()) }
            })
        }.toString())

    fun decodeIdentityLinks(raw: String?, key: String): FamilyDecode<List<IdentityLink>> =
        decode(raw, key) { payload ->
            if (payload.optInt("schemaVersion", -1) != FamilyIds.SCHEMA_VERSION) return@decode null
            val array = payload.optJSONArray("links") ?: return@decode null
            val links = buildList {
                for (index in 0 until array.length()) {
                    add(array.optJSONObject(index)?.toIdentityLink() ?: return@decode null)
                }
            }
            if (links.map { it.localFamilyId + "/" + (it.localUserId ?: "") }.toSet().size !=
                links.size
            ) {
                return@decode null
            }
            links
        }

    fun encodeMigrationMarker(marker: FamilyMigrationMarker): String =
        envelope(JSONObject().apply {
            put("schemaVersion", marker.schemaVersion)
            put("status", marker.status.name)
            put("familyId", marker.familyId)
            put("defaultUserId", marker.defaultUserId)
            put("migratedAt", marker.migratedAt)
            put("sourceVersion", marker.sourceVersion ?: JSONObject.NULL)
        }.toString())

    fun decodeMigrationMarker(raw: String?, key: String): FamilyDecode<FamilyMigrationMarker> =
        decode(raw, key) { payload ->
            if (payload.optInt("schemaVersion", -1) != FamilyIds.SCHEMA_VERSION) return@decode null
            val status = payload.enumValue<FamilyMigrationStatus>("status") ?: return@decode null
            val familyId = payload.requiredString("familyId") ?: return@decode null
            val defaultUserId = payload.requiredString("defaultUserId") ?: return@decode null
            if (!FamilyKeys.validSubject(familyId, defaultUserId)) return@decode null
            val migratedAt = payload.optLong("migratedAt", -1L)
            if (migratedAt <= 0L) return@decode null
            val sourceVersion = payload.optionalString("sourceVersion")?.also {
                if (it.length > MAX_SOURCE_VERSION_LENGTH) return@decode null
            }
            FamilyMigrationMarker(
                status = status,
                familyId = familyId,
                defaultUserId = defaultUserId,
                migratedAt = migratedAt,
                sourceVersion = sourceVersion,
            )
        }

    private fun envelope(payload: String): String {
        val crc = CRC32().apply { update(payload.toByteArray(Charsets.UTF_8)) }.value
        return JSONObject()
            .put("v", ENVELOPE_VERSION)
            .put("crc", crc)
            .put("payload", payload)
            .toString()
    }

    private fun <T> decode(
        raw: String?,
        key: String,
        parse: (JSONObject) -> T?,
    ): FamilyDecode<T> {
        if (raw == null) return FamilyDecode.Missing(key)
        if (raw.isBlank()) return FamilyDecode.Corrupt("empty")
        return try {
            val root = JSONObject(raw)
            val version = root.optInt("v", -1)
            if (version != ENVELOPE_VERSION) {
                return if (version >= 0) {
                    FamilyDecode.Unsupported(version)
                } else {
                    FamilyDecode.Corrupt("missing version")
                }
            }
            val payload = root.optString("payload", "")
            if (payload.isBlank() || !root.has("crc")) return FamilyDecode.Corrupt("missing payload")
            val actual = CRC32().apply { update(payload.toByteArray(Charsets.UTF_8)) }.value
            if (actual != root.optLong("crc", -1L)) return FamilyDecode.Corrupt("crc mismatch")
            val decoded = parse(JSONObject(payload)) ?: return FamilyDecode.Corrupt("invalid payload")
            FamilyDecode.Success(decoded)
        } catch (error: Exception) {
            FamilyDecode.Corrupt(error.javaClass.simpleName)
        }
    }

    private fun FamilyRecord.toJson(): JSONObject = JSONObject()
        .put("familyId", familyId)
        .put("displayName", displayName)
        .put("source", source.name)
        .put("cloudFamilyId", cloudFamilyId ?: JSONObject.NULL)
        .put("controlOwner", controlOwner.name)
        .put("revision", revision)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toFamilyRecord(): FamilyRecord? {
        val familyId = requiredString("familyId") ?: return null
        if (!FamilyKeys.validFamily(familyId)) return null
        val displayName = requiredString("displayName")
            ?.let { FamilyIds.normalizeDisplayName(it) }
            ?: return null
        val source = enumValue<FamilySource>("source") ?: return null
        val cloudFamilyId = if (isNull("cloudFamilyId")) {
            null
        } else {
            optLong("cloudFamilyId", 0L).takeIf { it > 0L } ?: return null
        }
        val controlOwner = enumValue<ControlOwner>("controlOwner") ?: return null
        val revision = optLong("revision", -1L)
        val createdAt = optLong("createdAt", -1L)
        val updatedAt = optLong("updatedAt", -1L)
        if (revision < 0L || createdAt <= 0L || updatedAt <= 0L) return null
        return FamilyRecord(
            familyId = familyId,
            displayName = displayName,
            source = source,
            cloudFamilyId = cloudFamilyId,
            controlOwner = controlOwner,
            revision = revision,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun FamilyMember.toJson(): JSONObject = JSONObject()
        .put("familyId", familyId)
        .put("userId", userId)
        .put("displayName", displayName)
        .put("avatarPreset", avatarPreset)
        .put("avatarImage", avatarImage ?: JSONObject.NULL)
        .put("avatarRevision", avatarRevision)
        .put("role", role.name)
        .put("source", source.name)
        .put("cloudUserId", cloudUserId ?: JSONObject.NULL)
        .put("status", status.name)
        .put("sortOrder", sortOrder)
        // Missing on members created before these fields existed; both mean "all".
        .put("grade", grade ?: JSONObject.NULL)
        .put("semester", semester ?: JSONObject.NULL)
        // Both null means the member still uses the initial switch password.
        .put("switchPinSalt", switchPinSalt ?: JSONObject.NULL)
        .put("switchPinHash", switchPinHash ?: JSONObject.NULL)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toMember(): FamilyMember? {
        val familyId = requiredString("familyId") ?: return null
        val userId = requiredString("userId") ?: return null
        if (!FamilyKeys.validSubject(familyId, userId)) return null
        val displayName = requiredString("displayName")
            ?.let { FamilyIds.normalizeDisplayName(it) }
            ?: return null
        val avatarPreset = optionalString("avatarPreset") ?: "rabbit"
        if (avatarPreset.length !in 1..MAX_AVATAR_PRESET_LENGTH ||
            avatarPreset.any { it.isISOControl() }
        ) {
            return null
        }
        val avatarImage = optionalString("avatarImage")?.also {
            if (!FamilyIds.isSafeAvatarImage(it)) return null
        }
        val avatarRevision = optLong("avatarRevision", 0L)
        if (avatarRevision < 0L) return null
        val role = enumValue<MemberRole>("role") ?: return null
        val source = enumValue<MemberSource>("source") ?: return null
        val cloudUserId = if (isNull("cloudUserId")) {
            null
        } else {
            optLong("cloudUserId", 0L).takeIf { it > 0L } ?: return null
        }
        val status = enumValue<MemberStatus>("status") ?: return null
        val sortOrder = optInt("sortOrder", Int.MIN_VALUE)
        if (sortOrder == Int.MIN_VALUE) return null
        // A member saved before the grade fields existed has neither key; both stay null.
        val grade = if (!has("grade") || isNull("grade")) {
            null
        } else {
            optInt("grade", 0).takeIf { it in 1..9 } ?: return null
        }
        val semester = if (!has("semester") || isNull("semester")) {
            null
        } else {
            optInt("semester", 0).takeIf { it in 1..2 } ?: return null
        }
        if (semester != null && grade == null) return null
        // Absent on members saved before the switch password existed: initial credential.
        val switchPinSalt = optionalString("switchPinSalt")
        val switchPinHash = optionalString("switchPinHash")
        if (!MemberPin.isStoredCredential(switchPinSalt, switchPinHash)) return null
        val createdAt = optLong("createdAt", -1L)
        val updatedAt = optLong("updatedAt", -1L)
        if (createdAt <= 0L || updatedAt <= 0L) return null
        return FamilyMember(
            familyId = familyId,
            userId = userId,
            displayName = displayName,
            avatarPreset = avatarPreset,
            avatarImage = avatarImage,
            avatarRevision = avatarRevision,
            role = role,
            source = source,
            cloudUserId = cloudUserId,
            status = status,
            sortOrder = sortOrder,
            grade = grade,
            semester = semester,
            switchPinSalt = switchPinSalt,
            switchPinHash = switchPinHash,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun IdentityLink.toJson(): JSONObject = JSONObject()
        .put("localFamilyId", localFamilyId)
        .put("localUserId", localUserId ?: JSONObject.NULL)
        .put("cloudFamilyId", cloudFamilyId)
        .put("cloudUserId", cloudUserId ?: JSONObject.NULL)
        .put("status", status.name)
        .put("linkedAt", linkedAt)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toIdentityLink(): IdentityLink? {
        val localFamilyId = requiredString("localFamilyId") ?: return null
        if (!FamilyKeys.validFamily(localFamilyId)) return null
        val localUserId = optionalString("localUserId")?.also {
            if (!FamilyIds.isValidUserId(it)) return null
        }
        val cloudFamilyId = optLong("cloudFamilyId", 0L).takeIf { it > 0L } ?: return null
        val cloudUserId = if (isNull("cloudUserId")) {
            null
        } else {
            optLong("cloudUserId", 0L).takeIf { it > 0L } ?: return null
        }
        val status = enumValue<LinkStatus>("status") ?: return null
        val linkedAt = optLong("linkedAt", -1L)
        val updatedAt = optLong("updatedAt", -1L)
        if (linkedAt <= 0L || updatedAt <= 0L) return null
        return IdentityLink(
            localFamilyId = localFamilyId,
            localUserId = localUserId,
            cloudFamilyId = cloudFamilyId,
            cloudUserId = cloudUserId,
            status = status,
            linkedAt = linkedAt,
            updatedAt = updatedAt,
        )
    }

    private fun JSONObject.requiredString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name, "").takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optionalString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name, "").takeIf { it.isNotBlank() }
    }

    private inline fun <reified T : Enum<T>> JSONObject.enumValue(name: String): T? {
        val raw = optString(name, "")
        if (raw.isBlank()) return null
        return enumValues<T>().firstOrNull { it.name == raw }
    }
}
