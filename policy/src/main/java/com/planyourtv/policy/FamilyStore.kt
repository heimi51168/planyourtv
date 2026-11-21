package com.planyourtv.policy

sealed interface FamilyLoad {
    data object Unavailable : FamilyLoad

    data object Missing : FamilyLoad

    data class Loaded(
        val document: FamilyDocument,
        val index: FamilyIndex,
    ) : FamilyLoad

    data class Corrupt(val reason: String) : FamilyLoad

    data class Unsupported(val version: Int) : FamilyLoad
}

/**
 * Reads and writes the family roster. This store never interprets policy or runtime state; it
 * only guarantees that every member in a roster has a valid local ID and that the default member
 * (the one legacy data migrated into) cannot disappear.
 */
class FamilyStore(
    private val storage: FamilyStorage = TvFamilyStorage(),
) {
    fun isReady(): Boolean = storage.isReady()

    fun load(): FamilyLoad {
        if (!storage.isReady()) return FamilyLoad.Unavailable
        val index = when (val decoded = loadIndex()) {
            is FamilyDecode.Success -> decoded.value
            is FamilyDecode.Missing -> return FamilyLoad.Missing
            is FamilyDecode.Corrupt -> return FamilyLoad.Corrupt(decoded.reason)
            is FamilyDecode.Unsupported -> return FamilyLoad.Unsupported(decoded.version)
            FamilyDecode.Unavailable -> return FamilyLoad.Unavailable
        }
        val document = when (val decoded = loadDocument(index.familyId)) {
            is FamilyDecode.Success -> decoded.value
            is FamilyDecode.Missing -> return FamilyLoad.Corrupt("missing family document")
            is FamilyDecode.Corrupt -> return FamilyLoad.Corrupt(decoded.reason)
            is FamilyDecode.Unsupported -> return FamilyLoad.Unsupported(decoded.version)
            FamilyDecode.Unavailable -> return FamilyLoad.Unavailable
        }
        if (document.family.familyId != index.familyId) {
            return FamilyLoad.Corrupt("index/document family mismatch")
        }
        val defaultUserId = index.defaultUserId
        if (defaultUserId != null) {
            val defaultMember = document.members.firstOrNull { it.userId == defaultUserId }
                ?: return FamilyLoad.Corrupt("missing default member")
            if (defaultMember.status != MemberStatus.ACTIVE) {
                return FamilyLoad.Corrupt("default member is not active")
            }
        }
        return FamilyLoad.Loaded(document, index)
    }

    fun loadIndex(): FamilyDecode<FamilyIndex> {
        if (!storage.isReady()) return FamilyDecode.Unavailable
        return FamilyCodecs.decodeIndex(storage.read(FamilyKeys.INDEX), FamilyKeys.INDEX)
    }

    fun loadDocument(familyId: String): FamilyDecode<FamilyDocument> {
        if (!storage.isReady()) return FamilyDecode.Unavailable
        if (!FamilyKeys.validFamily(familyId)) return FamilyDecode.Corrupt("invalid family id")
        val key = FamilyKeys.family(familyId)
        val decoded = FamilyCodecs.decodeDocument(storage.read(key), key)
        if (decoded is FamilyDecode.Success && decoded.value.family.familyId != familyId) {
            return FamilyDecode.Corrupt("document family mismatch")
        }
        return decoded
    }

    /**
     * Creates the default family only when no index exists. The caller decides whether this is a
     * fresh install; a missing document under an existing index is corruption, not a new family.
     */
    fun createDefault(
        now: Long,
        displayName: String = DEFAULT_MEMBER_NAME,
        familyDisplayName: String = DEFAULT_FAMILY_NAME,
        familyId: String = FamilyIds.newFamilyId(),
        userId: String = FamilyIds.newUserId(),
    ): FamilyLoad.Loaded? {
        if (!storage.isReady()) return null
        if (!FamilyKeys.validSubject(familyId, userId)) return null
        if (loadIndex() !is FamilyDecode.Missing) return null
        val member = FamilyMember(
            familyId = familyId,
            userId = userId,
            displayName = FamilyIds.normalizeDisplayName(displayName) ?: DEFAULT_MEMBER_NAME,
            role = MemberRole.CHILD,
            source = MemberSource.MIGRATED,
            status = MemberStatus.ACTIVE,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
        )
        val family = FamilyRecord(
            familyId = familyId,
            displayName = FamilyIds.normalizeDisplayName(familyDisplayName)
                ?: DEFAULT_FAMILY_NAME,
            source = FamilySource.LOCAL,
            cloudFamilyId = null,
            controlOwner = ControlOwner.LOCAL,
            revision = 1L,
            createdAt = now,
            updatedAt = now,
        )
        val document = FamilyDocument(family = family, members = listOf(member), revision = 1L)
        val index = FamilyIndex(
            familyId = familyId,
            defaultUserId = userId,
            revision = 1L,
        )
        return if (save(document, index)) FamilyLoad.Loaded(document, index) else null
    }

    /**
     * Adds a locally created child. Add-member is P1; keeping the mutation here means the Web
     * adapter never hand-builds a document or invents a user ID.
     */
    fun addMember(
        displayName: String,
        now: Long,
        role: MemberRole = MemberRole.CHILD,
        avatarPreset: String = "rabbit",
        grade: Int? = null,
        semester: Int? = null,
        switchPinSalt: String? = null,
        switchPinHash: String? = null,
    ): FamilyMember? {
        val loaded = load() as? FamilyLoad.Loaded ?: return null
        val name = FamilyIds.normalizeDisplayName(displayName) ?: return null
        if (avatarPreset.isBlank() || avatarPreset.length > 32) return null
        if (grade != null && grade !in 1..9) return null
        if (semester != null && (grade == null || semester !in 1..2)) return null
        if (!MemberPin.isStoredCredential(switchPinSalt, switchPinHash)) return null
        val used = loaded.document.members.map { it.userId }.toSet()
        var userId = FamilyIds.newUserId()
        while (userId in used) userId = FamilyIds.newUserId()
        val member = FamilyMember(
            familyId = loaded.document.family.familyId,
            userId = userId,
            displayName = name,
            avatarPreset = avatarPreset,
            role = role,
            source = MemberSource.LOCAL_WEB,
            status = MemberStatus.ACTIVE,
            sortOrder = (loaded.document.members.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            grade = grade,
            semester = semester,
            switchPinSalt = switchPinSalt,
            switchPinHash = switchPinHash,
            createdAt = now,
            updatedAt = now,
        )
        val document = loaded.document.copy(
            members = loaded.document.members + member,
            revision = loaded.document.revision + 1L,
            family = loaded.document.family.copy(
                revision = loaded.document.family.revision + 1L,
                updatedAt = now,
            ),
        )
        val index = loaded.index.copy(
            defaultUserId = loaded.index.defaultUserId ?: member.userId,
            revision = loaded.index.revision + 1L,
        )
        return if (save(document, index)) member else null
    }

    /**
     * Applies an edit to an existing member. The default member must stay ACTIVE, and a family
     * must keep at least one ACTIVE member so the TV always has a subject to execute.
     */
    fun updateMember(updated: FamilyMember): Boolean {
        val loaded = load() as? FamilyLoad.Loaded ?: return false
        val current = loaded.document.members.firstOrNull { it.userId == updated.userId }
            ?: return false
        if (current.familyId != updated.familyId ||
            updated.familyId != loaded.document.family.familyId ||
            updated.displayName != FamilyIds.normalizeDisplayName(updated.displayName) ||
            !FamilyIds.isSafeAvatarImage(updated.avatarImage) ||
            !MemberPin.isStoredCredential(updated.switchPinSalt, updated.switchPinHash)
        ) {
            return false
        }
        if (current.userId == loaded.index.defaultUserId &&
            updated.status != MemberStatus.ACTIVE
        ) {
            return false
        }
        val members = loaded.document.members.map {
            if (it.userId == updated.userId) updated else it
        }
        if (members.none { it.status == MemberStatus.ACTIVE }) return false
        val document = loaded.document.copy(
            members = members,
            revision = loaded.document.revision + 1L,
            family = loaded.document.family.copy(
                revision = loaded.document.family.revision + 1L,
                updatedAt = updated.updatedAt,
            ),
        )
        val index = loaded.index.copy(revision = loaded.index.revision + 1L)
        return save(document, index)
    }

    fun loadIdentityLinks(): FamilyDecode<List<IdentityLink>> {
        if (!storage.isReady()) return FamilyDecode.Unavailable
        return FamilyCodecs.decodeIdentityLinks(
            storage.read(FamilyKeys.IDENTITY_LINKS),
            FamilyKeys.IDENTITY_LINKS,
        )
    }

    fun saveIdentityLinks(links: List<IdentityLink>): Boolean {
        if (!storage.isReady()) return false
        val raw = FamilyCodecs.encodeIdentityLinks(links)
        if (FamilyCodecs.decodeIdentityLinks(raw, FamilyKeys.IDENTITY_LINKS)
            !is FamilyDecode.Success
        ) {
            return false
        }
        return storage.write(FamilyKeys.IDENTITY_LINKS, raw)
    }

    fun loadMigrationMarker(): FamilyDecode<FamilyMigrationMarker> {
        if (!storage.isReady()) return FamilyDecode.Unavailable
        return FamilyCodecs.decodeMigrationMarker(
            storage.read(FamilyKeys.MIGRATION),
            FamilyKeys.MIGRATION,
        )
    }

    fun saveMigrationMarker(marker: FamilyMigrationMarker): Boolean {
        if (!storage.isReady()) return false
        val raw = FamilyCodecs.encodeMigrationMarker(marker)
        if (FamilyCodecs.decodeMigrationMarker(raw, FamilyKeys.MIGRATION)
            !is FamilyDecode.Success
        ) {
            return false
        }
        return storage.write(FamilyKeys.MIGRATION, raw)
    }

    /**
     * Writes the family document first and the index second. If the second write fails, the
     * migration marker still names the family, so the next boot can rebuild the index instead
     * of discarding the document.
     */
    fun save(document: FamilyDocument, index: FamilyIndex): Boolean {
        if (!storage.isReady()) return false
        if (index.familyId != document.family.familyId) return false
        val rawDocument = FamilyCodecs.encodeDocument(document)
        if (FamilyCodecs.decodeDocument(rawDocument, FamilyKeys.family(index.familyId))
            !is FamilyDecode.Success
        ) {
            return false
        }
        if (index.defaultUserId != null &&
            document.members.none { it.userId == index.defaultUserId }
        ) {
            return false
        }
        val rawIndex = FamilyCodecs.encodeIndex(index)
        if (FamilyCodecs.decodeIndex(rawIndex, FamilyKeys.INDEX) !is FamilyDecode.Success) {
            return false
        }
        if (!storage.write(FamilyKeys.family(index.familyId), rawDocument)) return false
        return storage.write(FamilyKeys.INDEX, rawIndex)
    }

    companion object {
        // Data defaults; the Web/TV editor can rename both. They are not UI strings.
        const val DEFAULT_MEMBER_NAME = "大宝"
        const val DEFAULT_FAMILY_NAME = "家庭"
    }
}
