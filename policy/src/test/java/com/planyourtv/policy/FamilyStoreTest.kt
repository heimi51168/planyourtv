package com.planyourtv.policy

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

class FamilyStoreTest {
    private val now = 1_700_000_000_000L

    @Test
    fun createDefaultHasOneMigratedMemberAndLoadsBack() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)

        val created = store.createDefault(
            now = now,
            familyId = "fam_test",
            userId = "usr_test",
        )

        assertNotNull(created)
        assertEquals("大宝", created!!.document.members.single().displayName)
        assertEquals(MemberSource.MIGRATED, created.document.members.single().source)
        assertEquals("usr_test", created.index.defaultUserId)
        val loaded = store.load() as FamilyLoad.Loaded
        assertEquals(created.document, loaded.document)
        assertEquals(created.index, loaded.index)
    }

    @Test
    fun addMemberKeepsDefaultAndAppendsAStableLocalId() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        store.createDefault(now, familyId = "fam_test", userId = "usr_default")

        val second = store.addMember("二宝", now + 1)
        assertNotNull(second)
        assertTrue(second!!.userId.startsWith("usr_"))
        val loaded = store.load() as FamilyLoad.Loaded
        assertEquals(2, loaded.document.members.size)
        assertEquals("usr_default", loaded.index.defaultUserId)
        assertEquals("二宝", loaded.document.members.last().displayName)
    }

    @Test
    fun defaultMemberCannotBeDisabledOrRemoved() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        val created = store.createDefault(now, familyId = "fam_test", userId = "usr_default")!!
        val default = created.document.members.single()

        assertFalse(store.updateMember(default.copy(status = MemberStatus.REMOVED, updatedAt = now + 1)))
        assertTrue(store.updateMember(default.copy(displayName = "大宝改名", updatedAt = now + 1)))
        assertEquals(
            "大宝改名",
            (store.load() as FamilyLoad.Loaded).document.members.single().displayName,
        )
    }

    @Test
    fun nonDefaultMemberCanBeSoftRemoved() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        store.createDefault(now, familyId = "fam_test", userId = "usr_default")
        val second = store.addMember("二宝", now + 1)!!

        assertTrue(
            store.updateMember(
                second.copy(
                    status = MemberStatus.REMOVED,
                    avatarImage = null,
                    updatedAt = now + 2,
                ),
            ),
        )

        val loaded = store.load() as FamilyLoad.Loaded
        val removed = loaded.document.members.first { it.userId == second.userId }
        assertEquals(MemberStatus.REMOVED, removed.status)
        assertNull(removed.avatarImage)
        assertEquals("usr_default", loaded.index.defaultUserId)
        assertEquals(listOf("usr_default"), loaded.document.activeMembers().map { it.userId })
    }

    @Test
    fun memberGradeAndSemesterSurviveReload() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        store.createDefault(now, familyId = "fam_test", userId = "usr_default")

        val second = store.addMember("二宝", now + 1, grade = 4, semester = 2)
        assertNotNull(second)
        assertEquals(4, second!!.grade)
        assertEquals(2, second.semester)
        val loaded = (store.load() as FamilyLoad.Loaded).document.members
        assertEquals(4, loaded.last().grade)
        assertEquals(2, loaded.last().semester)
        assertNull(loaded.first().grade)
        assertNull(loaded.first().semester)
    }

    @Test
    fun addMemberRejectsInvalidGradeSelection() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        store.createDefault(now, familyId = "fam_test", userId = "usr_default")

        // A semester without a grade would hide every grade but select one semester.
        assertNull(store.addMember("二宝", now + 1, semester = 1))
        assertNull(store.addMember("二宝", now + 2, grade = 10))
        assertNull(store.addMember("二宝", now + 3, grade = 4, semester = 3))
        assertEquals(1, (store.load() as FamilyLoad.Loaded).document.members.size)
    }

    @Test
    fun documentWithoutGradeFieldsDecodesAsUnrestricted() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        store.createDefault(now, familyId = "fam_test", userId = "usr_default")
        // Strip the two new keys and refresh the CRC, reproducing a document written
        // before the member scope feature existed.
        val key = FamilyKeys.family("fam_test")
        val envelope = JSONObject(storage.values.getValue(key))
        val payload = JSONObject(envelope.getString("payload"))
        val members = payload.getJSONArray("members")
        for (index in 0 until members.length()) {
            members.getJSONObject(index).remove("grade")
            members.getJSONObject(index).remove("semester")
        }
        storage.values[key] = JSONObject()
            .put("v", envelope.getInt("v"))
            .put("crc", CRC32().apply { update(payload.toString().toByteArray()) }.value)
            .put("payload", payload.toString())
            .toString()

        val loaded = (store.load() as FamilyLoad.Loaded).document.members.single()
        assertNull(loaded.grade)
        assertNull(loaded.semester)
    }

    @Test
    fun membersWithoutStoredCredentialUseTheInitialSwitchPin() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        val created = store.createDefault(now, familyId = "fam_test", userId = "usr_default")!!
        val second = store.addMember("二宝", now + 1)!!

        val members = (store.load() as FamilyLoad.Loaded).document.members
        assertTrue(members.all { it.switchPinInitial })
        assertTrue(MemberPin.verify("123456", created.document.members.single().switchPinSalt, created.document.members.single().switchPinHash))
        assertTrue(MemberPin.verify("123456", second.switchPinSalt, second.switchPinHash))
        assertFalse(MemberPin.verify("000000", second.switchPinSalt, second.switchPinHash))
        assertFalse(MemberPin.verify("1234", second.switchPinSalt, second.switchPinHash))
    }

    @Test
    fun customSwitchPinSurvivesReload() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        val created = store.createDefault(now, familyId = "fam_test", userId = "usr_default")!!
        val member = created.document.members.single()
        val credential = MemberPin.create("246800")!!

        assertTrue(store.updateMember(member.copy(
            switchPinSalt = credential.salt,
            switchPinHash = credential.hash,
            updatedAt = now + 1,
        )))

        val loaded = (store.load() as FamilyLoad.Loaded).document.members.single()
        assertFalse(loaded.switchPinInitial)
        assertTrue(MemberPin.verify("246800", loaded.switchPinSalt, loaded.switchPinHash))
        assertFalse(MemberPin.verify("123456", loaded.switchPinSalt, loaded.switchPinHash))
    }

    @Test
    fun documentWithoutSwitchPinFieldsDecodesAsInitial() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        store.createDefault(now, familyId = "fam_test", userId = "usr_default")
        // A member written before the switch password existed has neither key.
        val key = FamilyKeys.family("fam_test")
        val envelope = JSONObject(storage.values.getValue(key))
        val payload = JSONObject(envelope.getString("payload"))
        val members = payload.getJSONArray("members")
        for (index in 0 until members.length()) {
            members.getJSONObject(index).remove("switchPinSalt")
            members.getJSONObject(index).remove("switchPinHash")
        }
        storage.values[key] = JSONObject()
            .put("v", envelope.getInt("v"))
            .put("crc", CRC32().apply { update(payload.toString().toByteArray()) }.value)
            .put("payload", payload.toString())
            .toString()

        val loaded = (store.load() as FamilyLoad.Loaded).document.members.single()
        assertTrue(loaded.switchPinInitial)
        assertTrue(MemberPin.verify("123456", loaded.switchPinSalt, loaded.switchPinHash))
    }

    @Test
    fun oneSidedSwitchPinCredentialIsRejected() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        store.createDefault(now, familyId = "fam_test", userId = "usr_default")
        val key = FamilyKeys.family("fam_test")
        val envelope = JSONObject(storage.values.getValue(key))
        val payload = JSONObject(envelope.getString("payload"))
        val member = payload.getJSONArray("members").getJSONObject(0)
        member.put("switchPinSalt", MemberPin.newSalt())
        storage.values[key] = JSONObject()
            .put("v", envelope.getInt("v"))
            .put("crc", CRC32().apply { update(payload.toString().toByteArray()) }.value)
            .put("payload", payload.toString())
            .toString()

        assertTrue(store.load() is FamilyLoad.Corrupt)
    }

    @Test
    fun memberPinRejectsWrongShapeAndFallsBackOnlyWhenBothFieldsAreAbsent() {
        assertTrue(MemberPin.isValid("000000"))
        assertFalse(MemberPin.isValid("00000"))
        assertFalse(MemberPin.isValid("0000000"))
        assertFalse(MemberPin.isValid("12a456"))
        assertNull(MemberPin.create("12a456"))

        assertTrue(MemberPin.verify("123456", null, null))
        assertFalse(MemberPin.verify("1234", null, null))
        assertFalse(MemberPin.verify("123456", "aa", null))
        assertFalse(MemberPin.verify("123456", null, "bb"))
    }

    @Test
    fun corruptIndexFailsClosedInsteadOfCreatingASecondFamily() {
        val storage = MemoryFamilyStorage()
        val store = FamilyStore(storage)
        store.createDefault(now, familyId = "fam_test", userId = "usr_default")
        storage.values[FamilyKeys.INDEX] = "not-json"

        assertTrue(store.load() is FamilyLoad.Corrupt)
        assertNull(store.createDefault(now + 1))
    }

    @Test
    fun storageUnavailableIsNotTreatedAsAFreshInstall() {
        val storage = MemoryFamilyStorage().apply { ready = false }
        val store = FamilyStore(storage)

        assertTrue(store.load() is FamilyLoad.Unavailable)
        assertNull(store.createDefault(now))
    }
}

class SubjectStoreTest {
    private val now = 1_700_000_000_000L

    @Test
    fun configRuntimeAndStudyRoundSurviveRoundTrip() {
        val storage = MemoryFamilyStorage()
        val store = SubjectStore(storage)
        val subject = SubjectRef("fam_test", "usr_test")
        val ruleSets = LocalRuleSets.from(LocalRules.DEFAULTS)
        val config = SubjectConfigDocument(
            familyId = subject.familyId,
            userId = subject.userId,
            controlOwner = ControlOwner.LOCAL,
            source = SubjectSource.MIGRATED,
            revision = 1L,
            activation = Activation.MANUAL,
            ruleSets = ruleSets,
            updatedAt = now,
        )
        assertTrue(store.saveConfig(config))
        val loadedConfig = (store.loadConfig(subject) as FamilyDecode.Success).value
        assertEquals(ruleSets, loadedConfig.ruleSets)

        val study = SavedStudyRound(
            studyPackage = "com.example.study",
            targetPackage = "com.example.game",
            totalSeconds = 600L,
            remainingSeconds = 300L,
            rewardSeconds = 120L,
            usageDate = "2026-11-18",
            startedAtEpochMs = now - 1000L,
            boot = BootEvidence(bootId = "boot-1"),
        )
        val runtime = SubjectRuntimeDocument(
            familyId = subject.familyId,
            userId = subject.userId,
            revision = 1L,
            runtime = RuntimeSnapshot(
                ledger = RuntimeLedger(days = mapOf("2026-11-18" to 120L)),
            ),
            studyRound = study,
            savedAt = now,
        )
        assertTrue(store.saveRuntime(runtime))
        val loadedRuntime = (store.loadRuntime(subject) as FamilyDecode.Success).value
        assertEquals(runtime.runtime, loadedRuntime.runtime)
        assertEquals(study, loadedRuntime.studyRound)
    }

    @Test
    fun activeSubjectEpochIncreasesOnSwitch() {
        val storage = MemoryFamilyStorage()
        val store = SubjectStore(storage)
        val family = "fam_test"
        val first = SubjectRef(family, "usr_first")
        val second = SubjectRef(family, "usr_second")

        val one = store.activate(first, now)!!
        assertEquals(1L, one.epoch)
        val two = store.activate(second, now + 1)!!
        assertEquals(2L, two.epoch)
        assertEquals(second, two.subject)
        assertEquals(two, (store.loadActiveSubject() as FamilyDecode.Success).value)
    }

    @Test
    fun malformedSubjectIdCannotBecomeACacheKey() {
        val storage = MemoryFamilyStorage()
        val store = SubjectStore(storage)
        val subject = SubjectRef("fam_test", "../../other")

        assertFalse(store.saveConfig(SubjectConfigDocument(
            familyId = subject.familyId,
            userId = subject.userId,
            controlOwner = ControlOwner.LOCAL,
            source = SubjectSource.LOCAL_WEB,
            revision = 1L,
            activation = Activation.MANUAL,
            ruleSets = LocalRuleSets.from(LocalRules.DEFAULTS),
            updatedAt = now,
        )))
        assertTrue(store.loadConfig(subject) is FamilyDecode.Corrupt)
        assertTrue(storage.values.isEmpty())
    }
}
