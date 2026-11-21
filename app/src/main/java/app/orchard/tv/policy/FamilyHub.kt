package app.orchard.tv.policy

import android.content.Context
import android.util.Log
import app.orchard.tv.BuildConfig
import com.planyourtv.policy.Activation
import com.planyourtv.policy.ActiveSubjectRecord
import com.planyourtv.policy.FamilyDecode
import com.planyourtv.policy.FamilyIds
import com.planyourtv.policy.FamilyLoad
import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.FamilyMigration
import com.planyourtv.policy.FamilyMigrationResult
import com.planyourtv.policy.FamilyStore
import com.planyourtv.policy.LegacyFamilySource
import com.planyourtv.policy.LocalRuleSets
import com.planyourtv.policy.SubjectProvisioning
import com.planyourtv.policy.MemberPin
import com.planyourtv.policy.MemberRole
import com.planyourtv.policy.MemberStatus
import com.planyourtv.policy.RuntimeSnapshot
import com.planyourtv.policy.SavedStudyRound
import com.planyourtv.policy.SubjectConfigDocument
import com.planyourtv.policy.SubjectRef
import com.planyourtv.policy.SubjectRuntimeDocument
import com.planyourtv.policy.SubjectSource
import com.planyourtv.policy.SubjectStore
import com.planyourtv.policy.SwitchPinExempt
import com.planyourtv.policy.ControlOwner
import com.planyourtv.policy.TvStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production owner of the V40 family roster and the per-subject cache.
 *
 * [LocalPolicyRuntime] initializes this object right after [TvStore]; migration then runs before
 * any policy state is read, so an upgraded TV always has one default member. The runtime keeps
 * using its legacy names only as a migration source; every new write lands in the subject cache.
 *
 * A corrupt family document is a visible error, not a silent reset: the runtime falls back to the
 * legacy single-user state and the parent can still repair or reinstall.
 */
object FamilyHub {
    private const val TAG = "FamilyHub"

    data class UiState(
        val ready: Boolean = false,
        val familyId: String = "",
        val familyName: String = "",
        val revision: Long = 0L,
        val defaultUserId: String = "",
        val activeUserId: String = "",
        val activeEpoch: Long = 0L,
        val members: List<FamilyMember> = emptyList(),
        val error: String = "",
    ) {
        val activeMember: FamilyMember?
            get() = members.firstOrNull { it.userId == activeUserId }

        val activeMembers: List<FamilyMember>
            get() = members.filter { it.status == MemberStatus.ACTIVE }
                .sortedWith(compareBy<FamilyMember> { it.sortOrder }.thenBy { it.createdAt })

        val canSwitchMember: Boolean get() = ready && activeMembers.size > 1

        /**
         * Adding a child is a local roster write. Cloud mode is refused earlier by the web
         * action and the TV switch, because a second cloud member would still execute the
         * first member's policy until the backend carries per-member data.
         */
        val canAddMember: Boolean get() = ready
    }

    enum class ActivateResult { OK, UNKNOWN_MEMBER, NOT_READY, FAILED }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var families: FamilyStore? = null
    private var subjects: SubjectStore? = null
    private var initialized = false

    val isReady: Boolean get() = _state.value.ready

    @Synchronized
    fun initialize(context: Context): Boolean {
        if (initialized) return _state.value.ready
        val app = context.applicationContext
        if (!TvStore.isReady && !TvStore.init(app)) return false
        val familyStore = FamilyStore()
        val subjectStore = SubjectStore()
        families = familyStore
        subjects = subjectStore
        when (
            val migrated = FamilyMigration.migrate(
                families = familyStore,
                subjects = subjectStore,
                legacy = LegacyFamilySource.capture(),
                now = System.currentTimeMillis(),
                sourceVersion = BuildConfig.VERSION_NAME,
            )
        ) {
            is FamilyMigrationResult.Migrated -> Log.i(TAG, "family cache migrated")
            is FamilyMigrationResult.AlreadyDone -> Unit
            is FamilyMigrationResult.Corrupt -> {
                Log.w(TAG, "family migration refused: ${migrated.reason}")
                _state.value = UiState(error = "FAMILY_CORRUPT")
                initialized = true
                return false
            }
            is FamilyMigrationResult.Failed -> {
                Log.w(TAG, "family migration failed: ${migrated.reason}")
                _state.value = UiState(error = "FAMILY_UNAVAILABLE")
                initialized = true
                return false
            }
        }
        initialized = true
        refresh()
        return _state.value.ready
    }

    /** Re-reads the roster and active pointer. Repairs a missing pointer, never a corrupt one. */
    @Synchronized
    fun refresh(): UiState {
        val familyStore = families
        val subjectStore = subjects
        if (familyStore == null || subjectStore == null) {
            _state.value = UiState(error = "FAMILY_UNAVAILABLE")
            return _state.value
        }
        val loaded = when (val result = familyStore.load()) {
            is FamilyLoad.Loaded -> result
            is FamilyLoad.Corrupt -> {
                _state.value = UiState(error = "FAMILY_CORRUPT")
                return _state.value
            }
            is FamilyLoad.Unsupported -> {
                _state.value = UiState(error = "FAMILY_UNSUPPORTED")
                return _state.value
            }
            FamilyLoad.Missing, FamilyLoad.Unavailable -> {
                _state.value = UiState(error = "FAMILY_UNAVAILABLE")
                return _state.value
            }
        }
        val defaultUserId = loaded.index.defaultUserId.orEmpty()
        val active: ActiveSubjectRecord = when (val decoded = subjectStore.loadActiveSubject()) {
            is FamilyDecode.Success -> decoded.value
            is FamilyDecode.Missing -> repairActivePointer(loaded)
                ?: return unready(loaded, defaultUserId, "SUBJECT_POINTER_UNREPAIRABLE")
            else -> return unready(loaded, defaultUserId, "SUBJECT_POINTER_CORRUPT")
        }
        if (active.familyId != loaded.document.family.familyId ||
            loaded.document.members.none {
                it.userId == active.userId && it.status == MemberStatus.ACTIVE
            }
        ) {
            return unready(loaded, defaultUserId, "SUBJECT_POINTER_INVALID")
        }
        _state.value = UiState(
            ready = true,
            familyId = loaded.document.family.familyId,
            familyName = loaded.document.family.displayName,
            revision = loaded.document.revision,
            defaultUserId = defaultUserId,
            activeUserId = active.userId,
            activeEpoch = active.epoch,
            members = loaded.document.members,
        )
        return _state.value
    }

    private fun unready(
        loaded: FamilyLoad.Loaded,
        defaultUserId: String,
        error: String,
    ): UiState {
        _state.value = UiState(
            familyId = loaded.document.family.familyId,
            familyName = loaded.document.family.displayName,
            revision = loaded.document.revision,
            defaultUserId = defaultUserId,
            members = loaded.document.members,
            error = error,
        )
        return _state.value
    }

    private fun repairActivePointer(loaded: FamilyLoad.Loaded): ActiveSubjectRecord? {
        val subjectStore = subjects ?: return null
        val fallback = loaded.index.defaultUserId
            ?.takeIf { id -> loaded.document.members.any { it.userId == id && it.status == MemberStatus.ACTIVE } }
            ?: loaded.document.activeMembers().firstOrNull()?.userId
            ?: return null
        return subjectStore.activate(
            SubjectRef(loaded.document.family.familyId, fallback),
            System.currentTimeMillis(),
        )
    }

    fun activeSubject(): SubjectRef? {
        val current = _state.value
        if (!current.ready) return null
        return SubjectRef(current.familyId, current.activeUserId)
    }

    fun activeMember(): FamilyMember? = _state.value.activeMember

    fun member(userId: String): FamilyMember? =
        _state.value.members.firstOrNull { it.userId == userId }

    /**
     * Checks a candidate against the member's 6-digit switch password. Members without a stored
     * credential still use [MemberPin.INITIAL]; a half-written credential never falls back.
     */
    fun verifySwitchPin(userId: String, candidate: String): Boolean {
        val member = member(userId) ?: return false
        return MemberPin.verify(candidate, member.switchPinSalt, member.switchPinHash)
    }

    /**
     * One-time upgrade from the old device-level switch. Every existing member inherits the
     * old "on" state in a single roster write; members added later keep the default off.
     */
    @Synchronized
    fun migrateLegacySwitchPinRequired(required: Boolean): Boolean {
        val current = _state.value
        if (!current.ready) return false
        if (!required) return true
        val familyStore = families ?: return false
        if (!familyStore.setSwitchPinRequiredForAll(true, System.currentTimeMillis())) {
            return false
        }
        refresh()
        return true
    }

    /** The active subject's config, or null when the document is missing or unsafe to use. */
    @Synchronized
    fun activeConfig(): SubjectConfigDocument? {
        val subjectStore = subjects ?: return null
        val subject = activeSubject() ?: return null
        return (subjectStore.loadConfig(subject) as? FamilyDecode.Success)?.value
    }

    @Synchronized
    fun activeRuntime(): SubjectRuntimeDocument? {
        val subjectStore = subjects ?: return null
        val subject = activeSubject() ?: return null
        return (subjectStore.loadRuntime(subject) as? FamilyDecode.Success)?.value
    }

    /**
     * Writes the materialized rule sets of the active subject. A corrupt document is left alone
     * so the next boot still fails closed instead of silently replacing recoverable data.
     */
    @Synchronized
    fun saveActiveConfig(activation: Activation, ruleSets: LocalRuleSets): Boolean {
        val subjectStore = subjects ?: return false
        val subject = activeSubject() ?: return false
        val now = System.currentTimeMillis()
        val current = when (val decoded = subjectStore.loadConfig(subject)) {
            is FamilyDecode.Success -> decoded.value
            is FamilyDecode.Missing -> null
            else -> return false
        }
        val document = SubjectConfigDocument(
            familyId = subject.familyId,
            userId = subject.userId,
            controlOwner = current?.controlOwner ?: ControlOwner.LOCAL,
            source = current?.source ?: SubjectSource.MIGRATED,
            revision = (current?.revision ?: 0L) + 1L,
            activation = activation,
            ruleSets = ruleSets,
            sourceVersion = current?.sourceVersion,
            updatedAt = now,
        )
        return subjectStore.saveConfig(document)
    }

    @Synchronized
    fun saveActiveRuntime(
        runtime: RuntimeSnapshot,
        studyRound: SavedStudyRound?,
    ): Boolean {
        val subjectStore = subjects ?: return false
        val subject = activeSubject() ?: return false
        val current = when (val decoded = subjectStore.loadRuntime(subject)) {
            is FamilyDecode.Success -> decoded.value
            is FamilyDecode.Missing -> null
            else -> return false
        }
        val document = SubjectRuntimeDocument(
            familyId = subject.familyId,
            userId = subject.userId,
            revision = (current?.revision ?: 0L) + 1L,
            runtime = runtime,
            studyRound = studyRound,
            savedAt = System.currentTimeMillis(),
        )
        return subjectStore.saveRuntime(document)
    }

    /**
     * Test-only: drops every subject's config and runtime so the next runtime initialize()
     * rebuilds from the legacy single-user keys. The roster and active pointer stay, mirroring a
     * device whose policy documents were cleared but whose family cache is intact.
     */
    @Synchronized
    internal fun resetSubjectDocumentsForTest() {
        val familyStore = families ?: return
        val subjectStore = subjects ?: return
        val loaded = familyStore.load() as? FamilyLoad.Loaded ?: return
        val familyId = loaded.document.family.familyId
        loaded.document.members.forEach { member ->
            subjectStore.removeSubject(SubjectRef(familyId, member.userId))
        }
    }

    /**
     * Moves the active pointer. [SubjectSwitcher] owns the state gate; TV callers must also prove
     * the target member's switch password first (Web callers act under the parent-center login).
     */
    @Synchronized
    fun activate(userId: String, now: Long = System.currentTimeMillis()): ActivateResult {
        val current = _state.value
        if (!current.ready) return ActivateResult.NOT_READY
        val member = current.members.firstOrNull {
            it.userId == userId && it.status == MemberStatus.ACTIVE
        } ?: return ActivateResult.UNKNOWN_MEMBER
        val subjectStore = subjects ?: return ActivateResult.NOT_READY
        val subject = SubjectRef(current.familyId, member.userId)
        val record = subjectStore.activate(subject, now) ?: return ActivateResult.FAILED
        refresh()
        return if (record.userId == _state.value.activeUserId) {
            ActivateResult.OK
        } else {
            ActivateResult.FAILED
        }
    }

    /**
     * Renames a member or changes the preset and optional study scope. Uploaded images are
     * owned by [FamilyAvatarStore].
     */
    @Synchronized
    fun updateProfile(
        userId: String,
        displayName: String,
        avatarPreset: String,
        grade: Int? = null,
        semester: Int? = null,
        switchPin: String? = null,
        switchPinRequired: Boolean? = null,
        switchPinExemptDays: List<Int>? = null,
        switchPinExemptStartMinute: Int? = null,
        switchPinExemptEndMinute: Int? = null,
    ): String? {
        val current = _state.value
        if (!current.ready) return "FAMILY_NOT_READY"
        val member = current.members.firstOrNull { it.userId == userId }
            ?: return "SUBJECT_NOT_FOUND"
        val name = FamilyIds.normalizeDisplayName(displayName) ?: return "SUBJECT_NAME_INVALID"
        val preset = FamilyAvatarStore.resolvePreset(avatarPreset)
            ?: return "SUBJECT_AVATAR_INVALID"
        if (!validGradeSelection(grade, semester)) return "SUBJECT_GRADE_INVALID"
        val requestedPin = switchPin?.takeIf { it.isNotBlank() }
        if (requestedPin != null && !MemberPin.isValid(requestedPin)) {
            return "SUBJECT_PIN_INVALID"
        }
        // The initial credential is public first-run data, not protection. A parent may keep
        // it on a member that has never opted in, but cannot choose it as a new password.
        if (requestedPin == MemberPin.INITIAL) return "SUBJECT_PIN_WEAK"
        val credential = requestedPin?.let {
            MemberPin.create(it) ?: return "SUBJECT_PIN_INVALID"
        }
        val required = switchPinRequired ?: member.switchPinRequired
        // Enabling protection must set a password in the same save, even if the member had a
        // custom one before. This keeps the parent deliberate and prevents an old 123456
        // credential from silently becoming the protection. Already-on members may keep it.
        if (required && !member.switchPinRequired && requestedPin == null) {
            return "SUBJECT_PIN_REQUIRED"
        }
        // Absent fields keep what the member already has, so a rename never clears a waiver.
        val exemptDays = switchPinExemptDays?.let {
            SwitchPinExempt.normalizeDays(it) ?: return "SUBJECT_EXEMPT_INVALID"
        } ?: member.switchPinExemptDays
        val exemptStart = switchPinExemptStartMinute ?: member.switchPinExemptStartMinute
        val exemptEnd = switchPinExemptEndMinute ?: member.switchPinExemptEndMinute
        if (!SwitchPinExempt.isValidWindow(exemptStart, exemptEnd)) {
            return "SUBJECT_EXEMPT_INVALID"
        }
        val familyStore = families ?: return "FAMILY_NOT_READY"
        val updated = member.copy(
            displayName = name,
            avatarPreset = preset,
            grade = grade,
            semester = semester,
            switchPinSalt = credential?.salt ?: member.switchPinSalt,
            switchPinHash = credential?.hash ?: member.switchPinHash,
            switchPinRequired = required,
            // A waiver without protection is dead data; clearing also makes the Web display
            // deterministic when the parent turns protection back on.
            switchPinExemptDays = if (required) exemptDays else emptyList(),
            switchPinExemptStartMinute = exemptStart,
            switchPinExemptEndMinute = exemptEnd,
            updatedAt = System.currentTimeMillis(),
        )
        if (!familyStore.updateMember(updated)) return "SUBJECT_UPDATE_FAILED"
        refresh()
        return null
    }

    /** Records the file [FamilyAvatarStore] already wrote atomically, then bumps the revision. */
    @Synchronized
    fun setAvatarImage(userId: String, relativePath: String): String? {
        val current = _state.value
        if (!current.ready) return "FAMILY_NOT_READY"
        val member = current.members.firstOrNull { it.userId == userId }
            ?: return "SUBJECT_NOT_FOUND"
        if (!FamilyIds.isSafeAvatarImage(relativePath)) return "SUBJECT_AVATAR_INVALID"
        val now = System.currentTimeMillis()
        val updated = member.copy(
            avatarImage = relativePath,
            avatarRevision = member.avatarRevision + 1L,
            updatedAt = now,
        )
        val familyStore = families ?: return "FAMILY_NOT_READY"
        if (!familyStore.updateMember(updated)) return "SUBJECT_UPDATE_FAILED"
        refresh()
        return null
    }

    @Synchronized
    fun clearAvatarImage(userId: String): String? {
        val current = _state.value
        if (!current.ready) return "FAMILY_NOT_READY"
        val member = current.members.firstOrNull { it.userId == userId }
            ?: return "SUBJECT_NOT_FOUND"
        if (member.avatarImage == null) return null
        val now = System.currentTimeMillis()
        val updated = member.copy(
            avatarImage = null,
            avatarRevision = member.avatarRevision + 1L,
            updatedAt = now,
        )
        val familyStore = families ?: return "FAMILY_NOT_READY"
        if (!familyStore.updateMember(updated)) return "SUBJECT_UPDATE_FAILED"
        refresh()
        return null
    }

    /**
     * Adds a local child. The new member starts with an empty runtime and ledger; it never
     * becomes the TV's active member and it never inherits a sibling's rules, quiz settings or
     * AI generation parameters. The config is local first-use defaults, so switching to it
     * never falls into the first-run setup wizard. The default config always starts in MANUAL
     * activation: V40 only covers the parent-start mode, and AUTO is a deprecated legacy flow.
     * Usage, the date ledger, sessions, study rounds and quiz progress are never copied — the
     * quiz document is provisioned separately by the caller, and its progress lives outside
     * the document cache.
     */
    @Synchronized
    fun addMember(
        displayName: String,
        avatarPreset: String = FamilyAvatarStore.DEFAULT_PRESET,
        grade: Int? = null,
        semester: Int? = null,
        switchPin: String? = null,
        switchPinRequired: Boolean = false,
        switchPinExemptDays: List<Int> = emptyList(),
        switchPinExemptStartMinute: Int = SwitchPinExempt.DEFAULT_START_MINUTE,
        switchPinExemptEndMinute: Int = SwitchPinExempt.DEFAULT_END_MINUTE,
    ): FamilyMember? {
        val current = _state.value
        if (!current.ready) return null
        if (FamilyIds.normalizeDisplayName(displayName) == null) return null
        val preset = FamilyAvatarStore.resolvePreset(avatarPreset) ?: return null
        if (!validGradeSelection(grade, semester)) return null
        if (!SwitchPinExempt.isValidWindow(
                switchPinExemptStartMinute,
                switchPinExemptEndMinute,
            )
        ) {
            return null
        }
        val exemptDays = SwitchPinExempt.normalizeDays(switchPinExemptDays) ?: return null
        val pin = switchPin?.takeIf { it.isNotBlank() }
        if (pin != null && !MemberPin.isValid(pin)) return null
        // Opted-in members must start with their own custom password. Members that keep the
        // switch off may stay on the initial credential until a parent enables protection.
        if (switchPinRequired && (pin == null || pin == MemberPin.INITIAL)) return null
        val credential = pin?.let { MemberPin.create(it) ?: return null }
        val familyStore = families ?: return null
        val member = familyStore.addMember(
            displayName = displayName,
            now = System.currentTimeMillis(),
            role = MemberRole.CHILD,
            avatarPreset = preset,
            grade = grade,
            semester = semester,
            switchPinSalt = credential?.salt,
            switchPinHash = credential?.hash,
            switchPinRequired = switchPinRequired,
            switchPinExemptDays = if (switchPinRequired) exemptDays else emptyList(),
            switchPinExemptStartMinute = switchPinExemptStartMinute,
            switchPinExemptEndMinute = switchPinExemptEndMinute,
        ) ?: return null
        refresh()
        val added = _state.value.members.firstOrNull { it.userId == member.userId } ?: return null
        provisionConfig(current.familyId, added.userId)
        return added
    }

    /**
     * Soft-removes a local member from the roster.
     *
     * The default member is the anchor for the pre-V40 single-child keys and cannot be
     * removed, and the active member must be switched away first so the runtime never loses
     * the subject it is executing. The roster write happens before the subject-document
     * cleanup, so a failed write leaves a fully usable member behind. The caller removes the
     * member's avatar files after this returns successfully.
     */
    @Synchronized
    fun removeMember(userId: String): String? {
        val current = _state.value
        if (!current.ready) return "FAMILY_NOT_READY"
        val member = current.members.firstOrNull {
            it.userId == userId && it.status == MemberStatus.ACTIVE
        } ?: return "SUBJECT_NOT_FOUND"
        if (member.userId == current.defaultUserId) return "SUBJECT_DEFAULT"
        if (member.userId == current.activeUserId) return "SUBJECT_ACTIVE"
        if (current.activeMembers.size <= 1) return "SUBJECT_LAST"
        val familyStore = families ?: return "FAMILY_NOT_READY"
        val removed = member.copy(
            status = MemberStatus.REMOVED,
            avatarImage = null,
            updatedAt = System.currentTimeMillis(),
        )
        if (!familyStore.updateMember(removed)) return "SUBJECT_UPDATE_FAILED"
        subjects?.removeSubject(SubjectRef(current.familyId, member.userId))
        refresh()
        return null
    }

    /**
     * Grade and semester are optional defaults for the quiz scope. An empty grade means every
     * grade, so a semester without a grade is meaningless and rejected instead of stored.
     */
    private fun validGradeSelection(grade: Int?, semester: Int?): Boolean =
        (grade == null || grade in 1..9) &&
            (semester == null || (grade != null && semester in 1..2))

    /**
     * Writes the new member's initial config document. A usable source config is copied; a
     * missing or unusable one falls back to local first-use defaults instead of leaving the
     * member unconfigured. The runtime's setup wizard must stay reachable only for the device
     * default member, because that wizard also creates the family PIN.
     */
    private fun provisionConfig(
        familyId: String,
        targetUserId: String,
    ) {
        val subjectStore = subjects ?: return
        val document = SubjectProvisioning.initialConfig(
            familyId = familyId,
            userId = targetUserId,
            source = null,
            sourceVersion = BuildConfig.VERSION_NAME,
            now = System.currentTimeMillis(),
        )
        if (!subjectStore.saveConfig(document)) {
            Log.w(TAG, "initial config write failed for the new member")
        }
    }
}
