package com.planyourtv.policy

/**
 * Canonical per-subject cache. The runtime, timer, quiz and Web editors must read these
 * documents (or a snapshot derived from them), never a cloud response or a Web request body.
 * Exactly one subject is active; the other documents stay on disk untouched.
 */
class SubjectStore(
    private val storage: FamilyStorage = TvFamilyStorage(),
) {
    fun isReady(): Boolean = storage.isReady()

    fun loadConfig(subject: SubjectRef): FamilyDecode<SubjectConfigDocument> {
        if (!storage.isReady()) return FamilyDecode.Unavailable
        if (!subject.valid) return FamilyDecode.Corrupt("invalid subject")
        val key = FamilyKeys.subjectConfig(subject.familyId, subject.userId)
        val decoded = FamilyCodecs.decodeConfig(storage.read(key), key)
        if (decoded is FamilyDecode.Success && decoded.value.subject != subject) {
            return FamilyDecode.Corrupt("config subject mismatch")
        }
        return decoded
    }

    fun saveConfig(document: SubjectConfigDocument): Boolean {
        if (!storage.isReady() || !document.subject.valid) return false
        val key = FamilyKeys.subjectConfig(document.familyId, document.userId)
        val raw = FamilyCodecs.encodeConfig(document)
        if (FamilyCodecs.decodeConfig(raw, key) !is FamilyDecode.Success) return false
        return storage.write(key, raw)
    }

    fun loadRuntime(subject: SubjectRef): FamilyDecode<SubjectRuntimeDocument> {
        if (!storage.isReady()) return FamilyDecode.Unavailable
        if (!subject.valid) return FamilyDecode.Corrupt("invalid subject")
        val key = FamilyKeys.subjectRuntime(subject.familyId, subject.userId)
        val decoded = FamilyCodecs.decodeRuntime(storage.read(key), key)
        if (decoded is FamilyDecode.Success && decoded.value.subject != subject) {
            return FamilyDecode.Corrupt("runtime subject mismatch")
        }
        return decoded
    }

    fun saveRuntime(document: SubjectRuntimeDocument): Boolean {
        if (!storage.isReady() || !document.subject.valid) return false
        val key = FamilyKeys.subjectRuntime(document.familyId, document.userId)
        val raw = FamilyCodecs.encodeRuntime(document)
        if (FamilyCodecs.decodeRuntime(raw, key) !is FamilyDecode.Success) return false
        return storage.write(key, raw)
    }

    fun loadActiveSubject(): FamilyDecode<ActiveSubjectRecord> {
        if (!storage.isReady()) return FamilyDecode.Unavailable
        return FamilyCodecs.decodeActiveSubject(
            storage.read(FamilyKeys.ACTIVE_SUBJECT),
            FamilyKeys.ACTIVE_SUBJECT,
        )
    }

    fun saveActiveSubject(record: ActiveSubjectRecord): Boolean {
        if (!storage.isReady() || !record.subject.valid) return false
        val raw = FamilyCodecs.encodeActiveSubject(record)
        if (FamilyCodecs.decodeActiveSubject(raw, FamilyKeys.ACTIVE_SUBJECT)
            !is FamilyDecode.Success
        ) {
            return false
        }
        return storage.write(FamilyKeys.ACTIVE_SUBJECT, raw)
    }

    /**
     * Switches the active subject and bumps the epoch. A corrupt active pointer is not replaced
     * silently: the caller must show a repair path first, because it may belong to a subject
     * whose timer is still running.
     */
    fun activate(subject: SubjectRef, now: Long): ActiveSubjectRecord? {
        if (!subject.valid) return null
        val previous = when (val decoded = loadActiveSubject()) {
            is FamilyDecode.Success -> decoded.value.epoch
            is FamilyDecode.Missing -> 0L
            else -> return null
        }
        val record = ActiveSubjectRecord(
            familyId = subject.familyId,
            userId = subject.userId,
            epoch = previous + 1L,
            updatedAt = now,
        )
        return if (saveActiveSubject(record)) record else null
    }

    /** Deletes only this subject's cached documents; the family roster is owned by FamilyStore. */
    fun removeSubject(subject: SubjectRef): Boolean {
        if (!storage.isReady() || !subject.valid) return false
        val removedConfig = storage.remove(
            FamilyKeys.subjectConfig(subject.familyId, subject.userId),
        )
        val removedRuntime = storage.remove(
            FamilyKeys.subjectRuntime(subject.familyId, subject.userId),
        )
        return removedConfig && removedRuntime
    }
}
