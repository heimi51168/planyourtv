package com.planyourtv.policy

/**
 * One-way, idempotent migration from the pre-V40 single-user local data into a default family
 * member. The legacy keys are never modified, so rolling back to an older APK still finds the
 * same rules, runtime and study round.
 *
 * The marker is written before any family document. A crash halfway leaves `IN_PROGRESS` with
 * both generated IDs, and the next boot resumes instead of creating a second family. `DONE` is
 * written last, only after the family, subject config/runtime and active pointer all exist.
 */
object FamilyMigration {
    fun migrate(
        families: FamilyStore,
        subjects: SubjectStore,
        legacy: LegacyFamilySnapshot,
        now: Long,
        sourceVersion: String? = null,
    ): FamilyMigrationResult {
        if (!families.isReady() || !subjects.isReady()) {
            return FamilyMigrationResult.Failed("storage unavailable")
        }
        val marker = when (val decoded = families.loadMigrationMarker()) {
            is FamilyDecode.Success -> decoded.value
            is FamilyDecode.Missing -> null
            is FamilyDecode.Corrupt -> return FamilyMigrationResult.Corrupt(decoded.reason)
            is FamilyDecode.Unsupported -> {
                return FamilyMigrationResult.Corrupt("unsupported migration marker")
            }
            FamilyDecode.Unavailable -> return FamilyMigrationResult.Failed("storage unavailable")
        }
        if (marker?.status == FamilyMigrationStatus.CORRUPT) {
            return FamilyMigrationResult.Corrupt("migration marked corrupt")
        }
        if (marker?.status == FamilyMigrationStatus.DONE) {
            return finishAlreadyDone(families, subjects, marker)
        }

        val identity = if (marker?.status == FamilyMigrationStatus.IN_PROGRESS) {
            requireValidIds(marker.familyId, marker.defaultUserId)
                ?: return FamilyMigrationResult.Corrupt("invalid migration marker ids")
        } else {
            resolveExistingIdentity(families)
                ?: return FamilyMigrationResult.Corrupt("family index is corrupt")
        }
        val familyId = identity.first
        val userId = identity.second

        if (marker == null || marker.status != FamilyMigrationStatus.IN_PROGRESS) {
            val started = FamilyMigrationMarker(
                status = FamilyMigrationStatus.IN_PROGRESS,
                familyId = familyId,
                defaultUserId = userId,
                migratedAt = now,
                sourceVersion = sourceVersion,
            )
            if (!families.saveMigrationMarker(started)) {
                return FamilyMigrationResult.Failed("cannot write migration marker")
            }
        }

        val loaded = ensureFamily(families, familyId, userId, now)
            ?: return FamilyMigrationResult.Failed("cannot write family document")
        val subject = SubjectRef(familyId, userId)

        if (!ensureSubjectConfig(subjects, subject, legacy, now, sourceVersion)) {
            return FamilyMigrationResult.Failed("cannot write subject config")
        }
        if (!ensureSubjectRuntime(subjects, subject, legacy, now)) {
            return FamilyMigrationResult.Failed("cannot write subject runtime")
        }
        if (!ensureActiveSubject(families, subjects, loaded, userId, now)) {
            return FamilyMigrationResult.Failed("cannot write active subject")
        }

        val done = FamilyMigrationMarker(
            status = FamilyMigrationStatus.DONE,
            familyId = familyId,
            defaultUserId = userId,
            migratedAt = now,
            sourceVersion = sourceVersion,
        )
        if (!families.saveMigrationMarker(done)) {
            return FamilyMigrationResult.Failed("cannot finish migration marker")
        }
        return FamilyMigrationResult.Migrated(familyId, userId)
    }

    private fun finishAlreadyDone(
        families: FamilyStore,
        subjects: SubjectStore,
        marker: FamilyMigrationMarker,
    ): FamilyMigrationResult {
        val loaded = when (val result = families.load()) {
            is FamilyLoad.Loaded -> result
            is FamilyLoad.Corrupt -> return FamilyMigrationResult.Corrupt(result.reason)
            is FamilyLoad.Unsupported -> {
                return FamilyMigrationResult.Corrupt("unsupported family document")
            }
            FamilyLoad.Missing, FamilyLoad.Unavailable -> {
                return FamilyMigrationResult.Corrupt("migration done but family is missing")
            }
        }
        val default = loaded.document.members.firstOrNull { it.userId == marker.defaultUserId }
            ?: return FamilyMigrationResult.Corrupt("default member missing")
        if (default.status != MemberStatus.ACTIVE) {
            return FamilyMigrationResult.Corrupt("default member is not active")
        }
        when (val active = subjects.loadActiveSubject()) {
            is FamilyDecode.Success -> {
                if (active.value.familyId != loaded.document.family.familyId) {
                    return FamilyMigrationResult.Corrupt("active subject family mismatch")
                }
                if (loaded.document.members.none { it.userId == active.value.userId }) {
                    return FamilyMigrationResult.Corrupt("active subject member missing")
                }
            }
            is FamilyDecode.Missing -> {
                val record = ActiveSubjectRecord(
                    familyId = marker.familyId,
                    userId = marker.defaultUserId,
                    epoch = 1L,
                    updatedAt = marker.migratedAt,
                )
                if (!subjects.saveActiveSubject(record)) {
                    return FamilyMigrationResult.Failed("cannot repair active subject")
                }
            }
            is FamilyDecode.Corrupt -> return FamilyMigrationResult.Corrupt(active.reason)
            is FamilyDecode.Unsupported -> {
                return FamilyMigrationResult.Corrupt("unsupported active subject")
            }
            FamilyDecode.Unavailable -> return FamilyMigrationResult.Failed("storage unavailable")
        }
        return FamilyMigrationResult.AlreadyDone(marker.familyId, marker.defaultUserId)
    }

    /** Returns null when an existing index is corrupt; first boot returns fresh IDs. */
    private fun resolveExistingIdentity(families: FamilyStore): Pair<String, String>? {
        return when (val loaded = families.load()) {
            is FamilyLoad.Loaded -> {
                val familyId = loaded.document.family.familyId
                val userId = loaded.index.defaultUserId
                    ?: loaded.document.members.firstOrNull { it.status == MemberStatus.ACTIVE }?.userId
                    ?: return null
                val valid = requireValidIds(familyId, userId) ?: return null
                if (loaded.document.members.none { it.userId == valid.second }) return null
                valid
            }
            FamilyLoad.Missing -> {
                val familyId = FamilyIds.newFamilyId()
                val userId = FamilyIds.newUserId()
                requireValidIds(familyId, userId)
            }
            is FamilyLoad.Corrupt, is FamilyLoad.Unsupported, FamilyLoad.Unavailable -> null
        }
    }

    private fun ensureFamily(
        families: FamilyStore,
        familyId: String,
        userId: String,
        now: Long,
    ): FamilyLoad.Loaded? {
        val existing = when (val loaded = families.load()) {
            is FamilyLoad.Loaded -> loaded
            FamilyLoad.Missing -> return families.createDefault(
                now = now,
                familyId = familyId,
                userId = userId,
            )?.takeIf { it.document.family.familyId == familyId }
            else -> return null
        }
        if (existing.document.family.familyId != familyId) return null
        val existingDefault = existing.index.defaultUserId
        if (existingDefault != null && existingDefault != userId) return null
        val hasMember = existing.document.members.any { it.userId == userId }
        if (hasMember && existingDefault == userId) return existing
        val member = if (hasMember) {
            null
        } else {
            FamilyMember(
                familyId = familyId,
                userId = userId,
                displayName = FamilyStore.DEFAULT_MEMBER_NAME,
                role = MemberRole.CHILD,
                source = MemberSource.MIGRATED,
                status = MemberStatus.ACTIVE,
                sortOrder = (existing.document.members.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                createdAt = now,
                updatedAt = now,
            )
        }
        val document = existing.document.copy(
            members = if (member == null) {
                existing.document.members
            } else {
                existing.document.members + member
            },
            revision = existing.document.revision + 1L,
            family = existing.document.family.copy(
                revision = existing.document.family.revision + 1L,
                updatedAt = now,
            ),
        )
        val index = existing.index.copy(
            defaultUserId = userId,
            revision = existing.index.revision + 1L,
        )
        return if (families.save(document, index)) FamilyLoad.Loaded(document, index) else null
    }

    private fun ensureSubjectConfig(
        subjects: SubjectStore,
        subject: SubjectRef,
        legacy: LegacyFamilySnapshot,
        now: Long,
        sourceVersion: String?,
    ): Boolean {
        return when (val existing = subjects.loadConfig(subject)) {
            is FamilyDecode.Success -> true
            is FamilyDecode.Missing -> {
                val ruleSets = legacy.ruleSets
                    ?.takeIf { it.isUsable }
                    ?: legacy.rules
                        ?.takeIf { it.isUsable }
                        ?.let { LocalRuleSets.from(it) }
                    ?: LocalRuleSets.from(LocalRules.DEFAULTS)
                subjects.saveConfig(
                    SubjectConfigDocument(
                        familyId = subject.familyId,
                        userId = subject.userId,
                        controlOwner = ControlOwner.LOCAL,
                        source = SubjectSource.MIGRATED,
                        revision = 1L,
                        activation = legacy.activation,
                        ruleSets = ruleSets,
                        sourceVersion = sourceVersion,
                        updatedAt = now,
                    ),
                )
            }
            is FamilyDecode.Corrupt,
            is FamilyDecode.Unsupported,
            FamilyDecode.Unavailable,
            -> false
        }
    }

    private fun ensureSubjectRuntime(
        subjects: SubjectStore,
        subject: SubjectRef,
        legacy: LegacyFamilySnapshot,
        now: Long,
    ): Boolean {
        return when (subjects.loadRuntime(subject)) {
            is FamilyDecode.Success -> true
            is FamilyDecode.Missing -> subjects.saveRuntime(
                SubjectRuntimeDocument(
                    familyId = subject.familyId,
                    userId = subject.userId,
                    revision = 1L,
                    runtime = legacy.runtime,
                    studyRound = legacy.studyRound,
                    savedAt = now,
                ),
            )
            is FamilyDecode.Corrupt,
            is FamilyDecode.Unsupported,
            FamilyDecode.Unavailable,
            -> false
        }
    }

    private fun ensureActiveSubject(
        families: FamilyStore,
        subjects: SubjectStore,
        loaded: FamilyLoad.Loaded,
        defaultUserId: String,
        now: Long,
    ): Boolean {
        return when (val active = subjects.loadActiveSubject()) {
            is FamilyDecode.Success -> {
                active.value.familyId == loaded.document.family.familyId &&
                    loaded.document.members.any { it.userId == active.value.userId }
            }
            is FamilyDecode.Missing -> {
                val fallback = loaded.index.defaultUserId
                    ?.takeIf { id -> loaded.document.members.any { it.userId == id } }
                    ?: defaultUserId
                val record = ActiveSubjectRecord(
                    familyId = loaded.document.family.familyId,
                    userId = fallback,
                    epoch = 1L,
                    updatedAt = now,
                )
                subjects.saveActiveSubject(record)
            }
            is FamilyDecode.Corrupt,
            is FamilyDecode.Unsupported,
            FamilyDecode.Unavailable,
            -> false
        }
    }

    private fun requireValidIds(familyId: String, userId: String): Pair<String, String>? {
        if (!FamilyKeys.validSubject(familyId, userId)) return null
        return familyId to userId
    }
}

/**
 * Reads the legacy single-user state without writing it. Call this after [TvStore] init and
 * before enabling a second member; the store remains the sole owner of each source key.
 */
object LegacyFamilySource {
    fun capture(): LegacyFamilySnapshot {
        // Legacy snapshots always migrate as MANUAL; AUTO is no longer a valid activation.
        val activation = Activation.MANUAL
        val ruleSetsLoad = LocalPolicyStore.loadRuleSets()
        val ruleSets = (ruleSetsLoad as? LocalRuleSetsLoad.Loaded)?.ruleSets
        val rules = if (ruleSets == null) LocalPolicyStore.loadRules() else null
        val configError = (ruleSetsLoad as? LocalRuleSetsLoad.Corrupt)?.reason
        return LegacyFamilySnapshot(
            activation = activation,
            ruleSets = ruleSets,
            rules = rules,
            runtime = PolicyStateRepository.loadRuntimeOrMigrate().snapshot,
            studyRound = LocalPolicyStore.loadStudyRound(),
            configError = configError,
        )
    }
}
