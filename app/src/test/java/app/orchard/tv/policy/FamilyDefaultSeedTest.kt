package app.orchard.tv.policy

import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.MemberPin
import com.planyourtv.policy.MemberRole
import com.planyourtv.policy.MemberSource
import com.planyourtv.policy.MemberStatus
import com.planyourtv.policy.quiz.QuizPackageSource
import com.planyourtv.policy.quiz.QuizPresetBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules for the one-time fresh-install seed. The port keeps Android storage and FamilyHub out
 * of these tests, so every guard (upgrade, edited roster, resume after a crash) is deterministic.
 */
class FamilyDefaultSeedTest {
    private val familyId = "fam_test"
    private val defaultId = "usr_default"
    private val dadId = "usr_dad"
    private val now = 1_000L

    private fun member(
        userId: String,
        name: String,
        preset: String = "boy",
        source: MemberSource = MemberSource.MIGRATED,
        sortOrder: Int = 0,
        createdAt: Long = now,
        updatedAt: Long = now,
        status: MemberStatus = MemberStatus.ACTIVE,
        storedPin: Boolean = false,
    ) = FamilyMember(
        familyId = familyId,
        userId = userId,
        displayName = name,
        avatarPreset = preset,
        role = MemberRole.CHILD,
        source = source,
        status = status,
        sortOrder = sortOrder,
        switchPinSalt = if (storedPin) "salt" else null,
        switchPinHash = if (storedPin) "hash" else null,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun pristineDefault() = member(defaultId, "大宝")

    private fun seededDad() = member(
        userId = dadId,
        name = FamilyDefaultSeed.DAD_NAME,
        preset = FamilyDefaultSeed.DAD_AVATAR,
        source = MemberSource.LOCAL_WEB,
        sortOrder = 1,
        storedPin = true,
    )

    private class FakePort(
        private val familyId: String,
        base: FamilyMember,
        dad: FamilyMember? = null,
    ) : FamilyDefaultSeed.Port {
        val members = mutableListOf(base).apply { if (dad != null) add(dad) }
        var activeUserId = base.userId
        val renamed = mutableListOf<String>()
        var addedDad = 0
        var levelSeeds = mutableListOf<String>()

        override val family: FamilyHub.UiState
            get() = FamilyHub.UiState(
                ready = true,
                familyId = familyId,
                defaultUserId = members.first().userId,
                activeUserId = activeUserId,
                members = members.toList(),
            )

        override fun renameDefault(userId: String, displayName: String): Boolean {
            val index = members.indexOfFirst { it.userId == userId }
            if (index < 0) return false
            members[index] = members[index].copy(displayName = displayName, updatedAt = 2_000L)
            renamed += userId
            return true
        }

        override fun addDad(): FamilyMember? {
            addedDad++
            val added = members.first().copy(
                userId = "usr_added_dad",
                displayName = FamilyDefaultSeed.DAD_NAME,
                avatarPreset = FamilyDefaultSeed.DAD_AVATAR,
                source = MemberSource.LOCAL_WEB,
                sortOrder = members.size,
                switchPinSalt = "salt",
                switchPinHash = "hash",
                createdAt = 2_000L,
                updatedAt = 2_000L,
            )
            members += added
            return added
        }

        override fun verifySwitchPin(userId: String, candidate: String): Boolean {
            val dad = members.firstOrNull { it.userId == userId } ?: return false
            return !dad.switchPinInitial && candidate == MemberPin.INITIAL
        }

        override fun ensureDadLevel(userId: String): Boolean {
            levelSeeds += userId
            return true
        }
    }

    private class FakeMemory(
        var version: Int = 0,
        var dadId: String? = null,
        var canMarkDone: Boolean = true,
    ) : FamilyDefaultSeed.Memory {
        val savedDadIds = mutableListOf<String>()

        override fun version(): Int = version

        override fun dadUserId(): String? = dadId

        override fun saveDadUserId(userId: String): Boolean {
            savedDadIds += userId
            dadId = userId
            return true
        }

        override fun markDone(): Boolean {
            if (!canMarkDone) return false
            version = 1
            return true
        }
    }

    @Test
    fun freshInstallRenamesDefaultAndAddsDadWithPresetLevel() {
        val port = FakePort(familyId, pristineDefault())
        val memory = FakeMemory()

        assertTrue(FamilyDefaultSeed.run(true, port, memory))

        assertEquals(FamilyDefaultSeed.DEFAULT_NAME, port.members.first().displayName)
        assertEquals(1, port.addedDad)
        assertEquals(listOf(defaultId), port.renamed)
        assertEquals(1, port.levelSeeds.size)
        assertEquals(port.members.last().userId, port.levelSeeds.single())
        assertEquals(defaultId, port.family.activeUserId)
        assertEquals(1, memory.version)
        assertEquals(port.members.last().userId, memory.dadId)
        assertEquals("boy", port.members.first().avatarPreset)
        assertTrue(port.members.first().switchPinInitial)
    }

    @Test
    fun upgradeNeverTouchesTheRoster() {
        val port = FakePort(familyId, pristineDefault())
        val memory = FakeMemory()

        assertFalse(FamilyDefaultSeed.run(false, port, memory))

        assertEquals("大宝", port.members.single().displayName)
        assertEquals(0, port.addedDad)
        assertTrue(port.renamed.isEmpty())
        assertTrue(port.levelSeeds.isEmpty())
        assertEquals(0, memory.version)
    }

    @Test
    fun completedSeedIsANoOp() {
        val port = FakePort(familyId, pristineDefault(), seededDad())
        val memory = FakeMemory(version = 1, dadId = dadId)

        assertFalse(FamilyDefaultSeed.run(true, port, memory))

        assertEquals(0, port.addedDad)
        assertTrue(port.renamed.isEmpty())
        assertTrue(port.levelSeeds.isEmpty())
    }

    @Test
    fun foreignMemberLeavesTheRosterAlone() {
        val daughter = member(
            userId = "usr_daughter",
            name = "女儿",
            source = MemberSource.LOCAL_WEB,
            sortOrder = 1,
            storedPin = true,
        )
        val port = FakePort(familyId, pristineDefault(), daughter)
        val memory = FakeMemory()

        assertTrue(FamilyDefaultSeed.run(true, port, memory))

        assertEquals("大宝", port.members.first().displayName)
        assertEquals(0, port.addedDad)
        assertTrue(port.renamed.isEmpty())
        assertTrue(port.levelSeeds.isEmpty())
        assertEquals(1, memory.version)
    }

    @Test
    fun editedDefaultLeavesTheRosterAlone() {
        val edited = pristineDefault().copy(updatedAt = now + 5L)
        val port = FakePort(familyId, edited)
        val memory = FakeMemory()

        assertTrue(FamilyDefaultSeed.run(true, port, memory))

        assertEquals("大宝", port.members.first().displayName)
        assertEquals(0, port.addedDad)
        assertTrue(port.renamed.isEmpty())
        assertTrue(port.levelSeeds.isEmpty())
        assertEquals(1, memory.version)
    }

    @Test
    fun partialSeedResumesWithoutAddingASecondDad() {
        val renamed = pristineDefault().copy(
            displayName = FamilyDefaultSeed.DEFAULT_NAME,
            updatedAt = 2_000L,
        )
        val port = FakePort(familyId, renamed, seededDad())
        val memory = FakeMemory()

        assertTrue(FamilyDefaultSeed.run(true, port, memory))

        assertEquals(0, port.addedDad)
        assertTrue(port.renamed.isEmpty())
        assertEquals(listOf(dadId), port.levelSeeds)
        assertEquals(dadId, memory.dadId)
        assertEquals(1, memory.version)
    }

    @Test
    fun deletedDadIsNotResurrected() {
        val port = FakePort(familyId, pristineDefault())
        val memory = FakeMemory(dadId = dadId)

        assertTrue(FamilyDefaultSeed.run(true, port, memory))

        assertEquals(0, port.addedDad)
        assertTrue(port.levelSeeds.isEmpty())
        assertEquals(1, memory.version)
    }

    @Test
    fun failedCompletionMarkerRetriesIdempotently() {
        val port = FakePort(familyId, pristineDefault())
        val memory = FakeMemory(canMarkDone = false)

        assertFalse(FamilyDefaultSeed.run(true, port, memory))
        assertEquals(0, memory.version)
        assertEquals(1, port.addedDad)
        assertEquals(1, port.levelSeeds.size)

        memory.canMarkDone = true
        assertTrue(FamilyDefaultSeed.run(true, port, memory))

        // The earlier rename/add is adopted instead of repeated, and the level is re-applied
        // idempotently by the real store.
        assertEquals(1, port.addedDad)
        assertEquals(2, port.levelSeeds.size)
        assertEquals(1, memory.version)
    }

    @Test
    fun dadLevelUsesOnlyTheParentPresetBank() {
        val level = FamilyDefaultSeed.dadLevel()

        assertEquals(FamilyDefaultSeed.DAD_LEVEL_ID, level?.id)
        assertEquals(FamilyDefaultSeed.DAD_LEVEL_NAME, level?.name)
        assertTrue(level?.enabled == true)
        assertEquals(
            listOf(QuizPackageSource.Preset(QuizPresetBank.PARENT_LIFE)),
            level?.sources,
        )
    }
}