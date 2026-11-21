package app.orchard.tv.policy

import android.content.Context
import android.util.Log
import app.orchard.tv.quiz.QuizConfigDocumentStore
import app.orchard.tv.quiz.QuizDocLoadResult
import app.orchard.tv.quiz.QuizDocSaveResult
import app.orchard.tv.quiz.QuizLevelPackageStore
import app.orchard.tv.quiz.QuizSubjectScope
import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.FamilyStore
import com.planyourtv.policy.MemberPin
import com.planyourtv.policy.MemberRole
import com.planyourtv.policy.MemberSource
import com.planyourtv.policy.MemberStatus
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizLevelPackage
import com.planyourtv.policy.quiz.QuizPackageSource
import com.planyourtv.policy.quiz.QuizPresetBank

/**
 * One-time, data-empty fresh-install roster seed.
 *
 * A brand-new TV starts with the migration-created default member and nothing else. That is
 * correct for an upgrade (the old single-child data must stay untouched) but gives a new parent
 * no example of how family members and quiz levels fit together. On a fresh install only, this
 * renames the untouched default to 儿子 and adds an editable 爸爸 member with one preset-bank
 * level, 生活技能提升. The active pointer never moves: the desktop keeps showing 儿子 until the
 * parent opens the member list.
 *
 * The seed is not a migration. It refuses to run when the app has ever been updated, when the
 * roster is not the pristine migration default, or when any other member exists. Every step is
 * idempotent and the completion marker is written only after the member and level are durable,
 * so a crash between steps can be resumed without duplicating data.
 */
object FamilyDefaultSeed {
    private const val TAG = "FamilyDefaultSeed"
    private const val SEED_VERSION = 1
    private const val KEY_VERSION = "family.seed.version"
    private const val KEY_DAD_USER = "family.seed.dadUserId"

    // Data defaults, not UI strings: the Web/TV editor can rename every seeded member and level.
    internal const val DEFAULT_NAME = "儿子"
    internal const val DAD_NAME = "爸爸"
    internal const val DAD_AVATAR = "dad"
    internal const val DAD_LEVEL_ID = "seed-dad-life"
    internal const val DAD_LEVEL_NAME = "生活技能提升"

    /** Side effects used by the seed rules, kept separate for deterministic unit tests. */
    internal interface Port {
        val family: FamilyHub.UiState
        fun renameDefault(userId: String, displayName: String): Boolean
        fun addDad(): FamilyMember?
        fun verifySwitchPin(userId: String, candidate: String): Boolean
        fun ensureDadLevel(userId: String): Boolean
    }

    /** Durable seed bookkeeping. [version] is 0 until the whole seed has succeeded. */
    internal interface Memory {
        fun version(): Int
        fun dadUserId(): String?
        fun saveDadUserId(userId: String): Boolean
        fun markDone(): Boolean
    }

    /** Production entry point. Does nothing on an install that has received an update. */
    fun install(context: Context) {
        if (TvStore.getString(KEY_VERSION)?.toIntOrNull() == SEED_VERSION) return
        val freshInstall = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.firstInstallTime > 0L && info.firstInstallTime == info.lastUpdateTime
        }.getOrDefault(false)
        if (!freshInstall) return
        if (!FamilyHub.isReady) return
        if (run(
                freshInstall = true,
                port = HubPort,
                memory = TvMemory,
            )
        ) {
            Log.i(TAG, "fresh-install family seed finished")
        }
    }

    /** Returns true only when the seed is known to be complete. */
    internal fun run(
        freshInstall: Boolean,
        port: Port,
        memory: Memory,
    ): Boolean {
        if (!freshInstall) return false
        if (memory.version() >= SEED_VERSION) return false
        val family = port.family
        if (!family.ready) return false
        val default = family.members.firstOrNull {
            it.userId == family.defaultUserId && it.status == MemberStatus.ACTIVE
        } ?: return false

        val storedDadId = memory.dadUserId()
        val storedDad = storedDadId?.let { id ->
            family.members.firstOrNull { it.userId == id }
        }
        if (storedDadId != null && storedDad == null) {
            // The parent deleted the seeded member. Never resurrect user-deleted data.
            memory.markDone()
            return true
        }
        if (storedDad != null && storedDad.status != MemberStatus.ACTIVE) {
            memory.markDone()
            return true
        }
        if (storedDad?.userId == default.userId) {
            // A malformed marker must never turn the protected default into the seeded parent.
            memory.markDone()
            return true
        }

        val others = family.activeMembers.filter { it.userId != default.userId }
        var dad = storedDad
        if (dad == null) {
            if (others.size > 1) {
                // A restored/imported roster is not a data-empty install. Leave it alone.
                memory.markDone()
                return true
            }
            val candidate = others.singleOrNull()
            if (candidate != null) {
                if (!isSeedDad(candidate, port)) {
                    memory.markDone()
                    return true
                }
                if (!memory.saveDadUserId(candidate.userId)) return false
                dad = candidate
            }
        }

        when {
            default.displayName == DEFAULT_NAME -> Unit
            isPristineDefault(default) -> {
                if (!port.renameDefault(default.userId, DEFAULT_NAME)) return false
            }
            else -> {
                // The parent already edited the default member; this is no longer a pristine
                // first launch. Do not change names, members or levels.
                memory.markDone()
                return true
            }
        }

        if (dad == null) {
            dad = port.addDad() ?: return false
            if (!memory.saveDadUserId(dad.userId)) return false
        }

        if (!port.ensureDadLevel(dad.userId)) return false
        return memory.markDone()
    }

    private fun isPristineDefault(member: FamilyMember): Boolean =
        member.displayName == FamilyStore.DEFAULT_MEMBER_NAME &&
            member.source == MemberSource.MIGRATED &&
            member.role == MemberRole.CHILD &&
            member.avatarImage == null &&
            member.avatarPreset == FamilyAvatarStore.DEFAULT_PRESET &&
            member.grade == null &&
            member.semester == null &&
            member.switchPinInitial &&
            member.createdAt == member.updatedAt

    /** Recognizes the 爸爸 member created by an earlier, partially interrupted seed run. */
    private fun isSeedDad(
        member: FamilyMember,
        port: Port,
    ): Boolean =
        member.displayName == DAD_NAME &&
            member.avatarPreset == DAD_AVATAR &&
            member.grade == null &&
            member.semester == null &&
            member.source == MemberSource.LOCAL_WEB &&
            !member.switchPinInitial &&
            port.verifySwitchPin(member.userId, MemberPin.INITIAL)

    internal fun dadLevel(): QuizLevelPackage? = QuizLevelPackage.create(
        sources = listOf(QuizPackageSource.Preset(QuizPresetBank.PARENT_LIFE)),
        name = DAD_LEVEL_NAME,
        id = DAD_LEVEL_ID,
    )

    private object TvMemory : Memory {
        override fun version(): Int = TvStore.getString(KEY_VERSION)?.toIntOrNull() ?: 0

        override fun dadUserId(): String? =
            TvStore.getString(KEY_DAD_USER)?.trim()?.takeIf { it.isNotEmpty() }

        override fun saveDadUserId(userId: String): Boolean =
            TvStore.putStringDurable(KEY_DAD_USER, userId)

        override fun markDone(): Boolean =
            TvStore.putStringDurable(KEY_VERSION, SEED_VERSION.toString())
    }

    private object HubPort : Port {
        override val family: FamilyHub.UiState get() = FamilyHub.state.value

        override fun renameDefault(userId: String, displayName: String): Boolean {
            val member = FamilyHub.member(userId) ?: return false
            return FamilyHub.updateProfile(
                userId = userId,
                displayName = displayName,
                avatarPreset = FamilyAvatarStore.canonicalPreset(member.avatarPreset),
                grade = member.grade,
                semester = member.semester,
            ) == null
        }

        override fun addDad(): FamilyMember? = FamilyHub.addMember(
            displayName = DAD_NAME,
            avatarPreset = DAD_AVATAR,
            grade = null,
            semester = null,
            switchPin = MemberPin.INITIAL,
        )

        override fun verifySwitchPin(userId: String, candidate: String): Boolean =
            FamilyHub.verifySwitchPin(userId, candidate)

        override fun ensureDadLevel(userId: String): Boolean = seedDadLevel(userId)
    }

    /**
     * Creates 爸爸's own QCD and adds one enabled preset level without touching the active
     * member. A document whose package extension is corrupt is left alone: replacing it would
     * hide a data problem and could drop parent-created levels.
     */
    private fun seedDadLevel(userId: String): Boolean {
        if (!QuizSubjectScope.provisionLocalDocument(userId, sourceUserId = null)) return false
        val document = when (val loaded = QuizConfigDocumentStore.localDocumentFor(userId)) {
            is QuizDocLoadResult.Ok -> loaded.document
            is QuizDocLoadResult.Unreadable, QuizDocLoadResult.Storage -> return false
        }
        val packages = when (val extension = QuizLevelPackageStore.read(document)) {
            is QuizLevelPackageStore.Extension.Present -> extension.packages
            QuizLevelPackageStore.Extension.Absent -> emptyList()
            is QuizLevelPackageStore.Extension.Broken -> return false
        }
        if (packages.any { it.id == DAD_LEVEL_ID }) return true
        if (packages.any { it.name == DAD_LEVEL_NAME && it.usesParentLifeBank() }) return true
        if (packages.size >= QuizLevelPackage.MAX_LEVELS) return true
        val level = dadLevel() ?: return false
        return QuizConfigDocumentStore.updateLevelPackagesFor(
            userId = userId,
            packages = packages + level,
        ) is QuizDocSaveResult.Ok
    }

    private fun QuizLevelPackage.usesParentLifeBank(): Boolean = sources.any {
        it is QuizPackageSource.Preset && it.bank == QuizPresetBank.PARENT_LIFE
    }
}