package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyMigrationTest {
    private val now = 1_700_000_000_000L

    private fun legacy(): LegacyFamilySnapshot {
        return LegacyFamilySnapshot(
            activation = Activation.MANUAL,
            ruleSets = LocalRuleSets.from(LocalRules.DEFAULTS),
            runtime = RuntimeSnapshot(
                ledger = RuntimeLedger(days = mapOf("2026-11-18" to 540L)),
            ),
            studyRound = SavedStudyRound(
                studyPackage = "com.example.study",
                targetPackage = null,
                totalSeconds = 600L,
                remainingSeconds = 240L,
                rewardSeconds = 60L,
                usageDate = "2026-11-18",
                startedAtEpochMs = now - 10_000L,
                boot = BootEvidence(bootId = "boot-before-upgrade"),
            ),
        )
    }

    @Test
    fun freshInstallMigratesIntoOneDefaultMemberAndIsIdempotent() {
        val storage = MemoryFamilyStorage()
        storage.values["local_rules_v2"] = "legacy-rules-untouched"
        storage.values["local_runtime_v2"] = "legacy-runtime-untouched"
        val families = FamilyStore(storage)
        val subjects = SubjectStore(storage)

        val result = FamilyMigration.migrate(families, subjects, legacy(), now, "1.99.001")

        val migrated = result as FamilyMigrationResult.Migrated
        val loaded = families.load() as FamilyLoad.Loaded
        assertEquals(1, loaded.document.members.size)
        val member = loaded.document.members.single()
        assertEquals("大宝", member.displayName)
        assertEquals(MemberSource.MIGRATED, member.source)
        assertEquals(migrated.userId, member.userId)
        // A pre-V40 install has no roster at all. The first member keeps the documented
        // 123456 initial credential so the parent can still switch until they set one.
        assertTrue(member.switchPinInitial)
        assertTrue(MemberPin.verify("123456", member.switchPinSalt, member.switchPinHash))
        assertFalse(MemberPin.verify("000000", member.switchPinSalt, member.switchPinHash))

        val config = (subjects.loadConfig(SubjectRef(migrated.familyId, migrated.userId))
            as FamilyDecode.Success).value
        assertEquals(Activation.MANUAL, config.activation)
        assertEquals(SubjectSource.MIGRATED, config.source)
        assertEquals(legacy().ruleSets, config.ruleSets)

        val runtime = (subjects.loadRuntime(SubjectRef(migrated.familyId, migrated.userId))
            as FamilyDecode.Success).value
        assertEquals(540L, runtime.runtime.ledger.days["2026-11-18"])
        assertEquals("com.example.study", runtime.studyRound?.studyPackage)
        assertEquals(
            migrated.userId,
            (subjects.loadActiveSubject() as FamilyDecode.Success).value.userId,
        )
        assertEquals(
            FamilyMigrationStatus.DONE,
            (families.loadMigrationMarker() as FamilyDecode.Success).value.status,
        )
        assertEquals("legacy-rules-untouched", storage.values["local_rules_v2"])
        assertEquals("legacy-runtime-untouched", storage.values["local_runtime_v2"])

        val again = FamilyMigration.migrate(families, subjects, legacy(), now + 1, "1.99.001")
        assertEquals(
            FamilyMigrationResult.AlreadyDone(migrated.familyId, migrated.userId),
            again,
        )
        assertEquals(1, (families.load() as FamilyLoad.Loaded).document.members.size)
    }

    @Test
    fun interruptedMigrationResumesWithTheSameIds() {
        val storage = MemoryFamilyStorage()
        val families = FamilyStore(storage)
        val subjects = SubjectStore(storage)
        val marker = FamilyMigrationMarker(
            status = FamilyMigrationStatus.IN_PROGRESS,
            familyId = "fam_resume",
            defaultUserId = "usr_resume",
            migratedAt = now,
        )
        assertTrue(families.saveMigrationMarker(marker))

        val result = FamilyMigration.migrate(families, subjects, legacy(), now + 1)

        assertEquals(FamilyMigrationResult.Migrated("fam_resume", "usr_resume"), result)
        val loaded = families.load() as FamilyLoad.Loaded
        assertEquals("fam_resume", loaded.document.family.familyId)
        assertEquals("usr_resume", loaded.document.members.single().userId)
    }

    @Test
    fun failedWriteLeavesInProgressMarkerAndRetrySucceeds() {
        val storage = MemoryFamilyStorage()
        val families = FamilyStore(storage)
        val subjects = SubjectStore(storage)
        storage.failFromWrite = 1

        val failed = FamilyMigration.migrate(families, subjects, legacy(), now)

        assertTrue(failed is FamilyMigrationResult.Failed)
        val marker = (families.loadMigrationMarker() as FamilyDecode.Success).value
        assertEquals(FamilyMigrationStatus.IN_PROGRESS, marker.status)

        storage.failFromWrite = -1
        val retried = FamilyMigration.migrate(families, subjects, legacy(), now + 1)
        assertEquals(
            FamilyMigrationResult.Migrated(marker.familyId, marker.defaultUserId),
            retried,
        )
        assertNotNull(families.load() as? FamilyLoad.Loaded)
    }

    @Test
    fun corruptMigrationMarkerFailsClosed() {
        val storage = MemoryFamilyStorage()
        storage.values[FamilyKeys.MIGRATION] = "garbage"
        val result = FamilyMigration.migrate(
            FamilyStore(storage),
            SubjectStore(storage),
            legacy(),
            now,
        )

        assertTrue(result is FamilyMigrationResult.Corrupt)
        assertTrue(FamilyStore(storage).load() is FamilyLoad.Missing)
    }

    @Test
    fun existingRosterIsAdoptedWithoutDuplicatingTheDefaultMember() {
        val storage = MemoryFamilyStorage()
        val families = FamilyStore(storage)
        val subjects = SubjectStore(storage)
        families.createDefault(now, familyId = "fam_existing", userId = "usr_existing")
        families.addMember("二宝", now + 1)

        val result = FamilyMigration.migrate(families, subjects, legacy(), now + 2)

        val migrated = result as FamilyMigrationResult.Migrated
        assertEquals("fam_existing", migrated.familyId)
        assertEquals("usr_existing", migrated.userId)
        val loaded = families.load() as FamilyLoad.Loaded
        assertEquals(2, loaded.document.members.size)
        assertEquals("usr_existing", loaded.index.defaultUserId)
        assertEquals(1, loaded.document.members.count { it.userId == "usr_existing" })
    }

    @Test
    fun doneMarkerRepairsMissingActivePointer() {
        val storage = MemoryFamilyStorage()
        val families = FamilyStore(storage)
        val subjects = SubjectStore(storage)
        val migrated = FamilyMigration.migrate(families, subjects, legacy(), now)
            as FamilyMigrationResult.Migrated
        assertTrue(storage.remove(FamilyKeys.ACTIVE_SUBJECT))

        val again = FamilyMigration.migrate(families, subjects, legacy(), now + 1)

        assertEquals(FamilyMigrationResult.AlreadyDone(migrated.familyId, migrated.userId), again)
        assertEquals(
            migrated.userId,
            (subjects.loadActiveSubject() as FamilyDecode.Success).value.userId,
        )
    }
}
