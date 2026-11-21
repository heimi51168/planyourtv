package com.planyourtv.policy

/**
 * Storage seam for the family cache. Production uses the same durable [TvStore] as the legacy
 * policy data; unit tests use an in-memory implementation. Writes must be synchronous because a
 * subject switch or a migration marker must survive an abrupt power loss.
 */
interface FamilyStorage {
    fun isReady(): Boolean

    fun read(key: String): String?

    fun write(key: String, value: String): Boolean

    fun remove(key: String): Boolean
}

class TvFamilyStorage : FamilyStorage {
    override fun isReady(): Boolean = TvStore.isReady

    override fun read(key: String): String? =
        if (TvStore.isReady) TvStore.getString(key, null) else null

    override fun write(key: String, value: String): Boolean =
        if (TvStore.isReady) TvStore.putStringDurable(key, value) else false

    override fun remove(key: String): Boolean =
        if (TvStore.isReady) TvStore.removeDurable(key) else false
}

sealed interface FamilyDecode<out T> {
    data object Unavailable : FamilyDecode<Nothing>

    data class Missing(val key: String) : FamilyDecode<Nothing>

    data class Success<T>(val value: T) : FamilyDecode<T>

    data class Corrupt(val reason: String) : FamilyDecode<Nothing>

    data class Unsupported(val version: Int) : FamilyDecode<Nothing>
}

internal object FamilyKeys {
    const val INDEX = PolicyKeys.FAMILY_INDEX_V1
    const val ACTIVE_SUBJECT = PolicyKeys.SUBJECT_ACTIVE_V1
    const val IDENTITY_LINKS = PolicyKeys.IDENTITY_LINKS_V1
    const val MIGRATION = PolicyKeys.SUBJECT_MIGRATION_V1

    fun family(familyId: String): String = "family.$familyId.v1"

    fun subjectConfig(familyId: String, userId: String): String =
        "subject.$familyId.$userId.config.v1"

    fun subjectRuntime(familyId: String, userId: String): String =
        "subject.$familyId.$userId.runtime.v1"

    fun subjectQuiz(familyId: String, userId: String): String =
        "subject.$familyId.$userId.quiz.v1"

    /**
     * Every key that contains an ID must refuse to build a malformed key. This is the boundary
     * that keeps a cloud or Web string from ever becoming a cache key.
     */
    fun validFamily(familyId: String): Boolean =
        FamilyIds.isValidFamilyId(familyId) && familyId != "index"

    fun validSubject(familyId: String, userId: String): Boolean =
        validFamily(familyId) && FamilyIds.isValidUserId(userId)
}
