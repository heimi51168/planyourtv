package com.planyourtv.policy

import java.util.UUID

enum class FamilySource { LOCAL, CLOUD }

enum class MemberSource { MIGRATED, LOCAL_WEB, CLOUD }

enum class MemberRole { CHILD, PARENT, UNKNOWN }

enum class MemberStatus { ACTIVE, DISABLED, REMOVED }

enum class ControlOwner { LOCAL, CLOUD }

enum class SubjectSource { LOCAL_WEB, CLOUD, MIGRATED }

enum class LinkStatus { PENDING, LINKED, CONFLICT, REVOKED }

enum class FamilyMigrationStatus { IN_PROGRESS, DONE, CORRUPT }

/**
 * Local family identity. [familyId] and every [FamilyMember.userId] are generated and owned by
 * this TV; cloud numeric IDs are aliases in [IdentityLink] and never become cache keys.
 */
data class FamilyRecord(
    val familyId: String,
    val displayName: String,
    val source: FamilySource,
    val cloudFamilyId: Long?,
    val controlOwner: ControlOwner,
    val revision: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class FamilyMember(
    val familyId: String,
    val userId: String,
    val displayName: String,
    val avatarPreset: String = "boy",
    /** App-private relative name, for example `avatars/<familyId>/<userId>.webp`. */
    val avatarImage: String? = null,
    val avatarRevision: Long = 0L,
    val role: MemberRole = MemberRole.CHILD,
    val source: MemberSource = MemberSource.MIGRATED,
    val cloudUserId: Long? = null,
    val status: MemberStatus = MemberStatus.ACTIVE,
    val sortOrder: Int = 0,
    /**
     * The child's study grade and semester, used as the default scope for AI generation and
     * level sources. Null means "all grades" / "all semesters", which is also what every
     * member created before these fields existed decodes to.
     */
    val grade: Int? = null,
    val semester: Int? = null,
    /**
     * Salted PBKDF2 credential for the 6-digit member switch password. Both fields null means
     * the member still uses [MemberPin.INITIAL]; members created before this field existed
     * decode to the same state, so no migration write is needed.
     */
    val switchPinSalt: String? = null,
    val switchPinHash: String? = null,
    /**
     * Whether the TV lock screen asks for this member's switch password before entering it.
     * Missing on documents saved before the per-member switch existed, which decodes to false.
     */
    val switchPinRequired: Boolean = false,
    /**
     * Weekdays (`1` = Monday … `7` = Sunday) on which the switch password is waived inside
     * [switchPinExemptStartMinute] until [switchPinExemptEndMinute]. An empty list means the
     * exemption is off; this is also what every document saved before the field existed means.
     * The same window applies to every selected day.
     */
    val switchPinExemptDays: List<Int> = emptyList(),
    val switchPinExemptStartMinute: Int = SwitchPinExempt.DEFAULT_START_MINUTE,
    val switchPinExemptEndMinute: Int = SwitchPinExempt.DEFAULT_END_MINUTE,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val switchPinInitial: Boolean
        get() = switchPinSalt == null && switchPinHash == null
}

/**
 * The constrained shape of the per-member switch-password exemption. The window is same-day,
 * start-inclusive and end-exclusive, and every selected weekday shares it. Invalid data is never
 * coerced into an exemption: callers must reject it or fall back to no selected days.
 */
object SwitchPinExempt {
    const val DEFAULT_START_MINUTE = 9 * 60
    const val DEFAULT_END_MINUTE = 15 * 60
    const val END_OF_DAY_MINUTE = 24 * 60

    /** Returns distinct, sorted weekdays, or null when any value is outside 1..7. */
    fun normalizeDays(days: Collection<Int>): List<Int>? {
        if (days.any { it !in 1..7 }) return null
        return days.distinct().sorted()
    }

    /** Same-day window; 24:00 is allowed only as an end and must be later than the start. */
    fun isValidWindow(startMinute: Int, endMinute: Int): Boolean =
        startMinute in 0 until END_OF_DAY_MINUTE &&
            endMinute in 1..END_OF_DAY_MINUTE &&
            startMinute < endMinute
}

/**
 * True when [this] member currently waives its switch password.
 *
 * [dayOfWeek] is 1..7 (Monday..Sunday) and [minuteOfDay] is 0..1439. Protection must be on,
 * the day must be selected, the stored window must still be valid, and the minute must fall in
 * the half-open `[start, end)` range. Anything unexpected fails closed and asks for the PIN.
 */
fun FamilyMember.switchPinExemptAt(dayOfWeek: Int, minuteOfDay: Int): Boolean =
    switchPinRequired &&
        dayOfWeek in 1..7 &&
        dayOfWeek in switchPinExemptDays &&
        SwitchPinExempt.isValidWindow(
            switchPinExemptStartMinute,
            switchPinExemptEndMinute,
        ) &&
        minuteOfDay >= switchPinExemptStartMinute &&
        minuteOfDay < switchPinExemptEndMinute

data class FamilyDocument(
    val schemaVersion: Int = FamilyIds.SCHEMA_VERSION,
    val family: FamilyRecord,
    val members: List<FamilyMember>,
    val revision: Long,
) {
    fun activeMembers(): List<FamilyMember> =
        members.filter { it.status == MemberStatus.ACTIVE }.sortedBy { it.sortOrder }
}

/**
 * The single-family pointer. `defaultUserId` is the member legacy data migrated into and must
 * never be deleted. The active subject lives in its own key so switching it does not rewrite
 * the whole family document.
 */
data class FamilyIndex(
    val schemaVersion: Int = FamilyIds.SCHEMA_VERSION,
    val familyId: String,
    val defaultUserId: String?,
    val revision: Long,
)

/**
 * The one subject the TV may execute. [epoch] is bumped on a parent-confirmed switch so an
 * in-flight timer cannot keep writing to the previous subject.
 */
data class ActiveSubjectRecord(
    val schemaVersion: Int = FamilyIds.SCHEMA_VERSION,
    val familyId: String,
    val userId: String,
    val epoch: Long,
    val updatedAt: Long,
) {
    val subject: SubjectRef get() = SubjectRef(familyId, userId)
}

data class SubjectRef(
    val familyId: String,
    val userId: String,
) {
    val valid: Boolean get() = FamilyIds.isValidFamilyId(familyId) && FamilyIds.isValidUserId(userId)
}

data class SubjectConfigDocument(
    val schemaVersion: Int = FamilyIds.SCHEMA_VERSION,
    val familyId: String,
    val userId: String,
    val controlOwner: ControlOwner,
    val source: SubjectSource,
    val revision: Long,
    val activation: Activation,
    val ruleSets: LocalRuleSets,
    val sourceVersion: String? = null,
    val updatedAt: Long,
) {
    val subject: SubjectRef get() = SubjectRef(familyId, userId)
    val rules: LocalRules get() = ruleSets.rules()
}

data class SubjectRuntimeDocument(
    val schemaVersion: Int = FamilyIds.SCHEMA_VERSION,
    val familyId: String,
    val userId: String,
    val revision: Long,
    val runtime: RuntimeSnapshot,
    val studyRound: SavedStudyRound? = null,
    val savedAt: Long,
) {
    val subject: SubjectRef get() = SubjectRef(familyId, userId)
}

data class IdentityLink(
    val localFamilyId: String,
    val localUserId: String?,
    val cloudFamilyId: Long,
    val cloudUserId: Long?,
    val status: LinkStatus,
    val linkedAt: Long,
    val updatedAt: Long,
)

data class FamilyMigrationMarker(
    val schemaVersion: Int = FamilyIds.SCHEMA_VERSION,
    val status: FamilyMigrationStatus,
    val familyId: String,
    val defaultUserId: String,
    val migratedAt: Long,
    val sourceVersion: String? = null,
)

/**
 * A pure snapshot of the pre-V40 single-user state. Keeping the migration input separate from
 * the stores makes the migration deterministic and testable without Android storage.
 */
data class LegacyFamilySnapshot(
    val activation: Activation,
    val ruleSets: LocalRuleSets?,
    val rules: LocalRules? = null,
    val runtime: RuntimeSnapshot = RuntimeSnapshot(),
    val studyRound: SavedStudyRound? = null,
    val configError: String? = null,
)

sealed interface FamilyMigrationResult {
    data class AlreadyDone(val familyId: String, val userId: String) : FamilyMigrationResult

    data class Migrated(val familyId: String, val userId: String) : FamilyMigrationResult

    data class Corrupt(val reason: String) : FamilyMigrationResult

    data class Failed(val reason: String) : FamilyMigrationResult
}

object FamilyIds {
    const val SCHEMA_VERSION = 1

    private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,80}$")
    private val SAFE_AVATAR = Regex("^[A-Za-z0-9._/-]{1,160}$")

    fun newFamilyId(): String = "fam_" + uuid()

    fun newUserId(): String = "usr_" + uuid()

    fun isValidFamilyId(value: String): Boolean = SAFE_ID.matches(value)

    fun isValidUserId(value: String): Boolean = SAFE_ID.matches(value)

    /**
     * [name] is relative to the app-private avatar directory. Reject absolute paths, parent
     * traversal, backslashes and empty names; actual file creation is owned by the avatar store.
     */
    fun isSafeAvatarImage(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        if (name.startsWith("/") || name.contains("..") || name.contains('\\')) return false
        return SAFE_AVATAR.matches(name)
    }

    fun normalizeDisplayName(raw: String): String? {
        val clean = raw.trim()
        if (clean.isEmpty()) return null
        if (clean.codePointCount(0, clean.length) > 12) return null
        if (clean.any { it == '\n' || it == '\r' || it.isISOControl() }) return null
        return clean
    }

    private fun uuid(): String = UUID.randomUUID().toString().replace("-", "")
}
