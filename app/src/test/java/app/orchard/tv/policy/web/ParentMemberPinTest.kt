package app.orchard.tv.policy.web

import android.app.Application
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.MemberSwitchAuth
import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.MemberPin
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.TvStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDateTime

/**
 * Switch protection is per member and off by default. A member that opts in must choose a
 * custom password; a member that stays open keeps the documented initial 123456 credential
 * until the parent turns protection on and sets a new one.
 */
@RunWith(RobolectricTestRunner::class)
class ParentMemberPinTest {
    private val pin = "246810"
    private lateinit var app: Application

    private fun rules() = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekdayLimitMinutes = 60,
            weekendLimitMinutes = 60,
        ),
        sessionLimitMinutes = 20,
        restMinutes = 0,
        allowedPackages = emptySet(),
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules()))
        PolicyModeCoordinator.debugForceMode(PolicyMode.LOCAL)
    }

    @After
    fun tearDown() {
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
    }

    private fun uniqueName(): String =
        "二宝" + (System.nanoTime() % 100000L).toString().padStart(5, '0')

    private fun addMember(
        switchPin: String?,
        required: Boolean = false,
        name: String = uniqueName(),
        preset: String = "boy",
        exemptDays: List<Int>? = null,
        exemptStart: Int? = null,
        exemptEnd: Int? = null,
    ): ParentCenterActions.Outcome {
        val body = JSONObject()
            .put("name", name)
            .put("avatarPreset", preset)
            .put("switchPinRequired", required)
        if (switchPin != null) body.put("switchPin", switchPin)
        if (exemptDays != null) body.put("switchPinExemptDays", JSONArray(exemptDays))
        if (exemptStart != null) body.put("switchPinExemptStartMinute", exemptStart)
        if (exemptEnd != null) body.put("switchPinExemptEndMinute", exemptEnd)
        return ParentCenterActions.addMember(body)
    }

    private fun profileBody(
        member: FamilyMember,
        switchPin: String? = null,
        required: Boolean? = null,
        exemptDays: List<Int>? = null,
        exemptStart: Int? = null,
        exemptEnd: Int? = null,
    ): JSONObject {
        val body = JSONObject()
            .put("userId", member.userId)
            .put("name", member.displayName)
            .put("avatarPreset", member.avatarPreset)
        if (switchPin != null) body.put("switchPin", switchPin)
        if (required != null) body.put("switchPinRequired", required)
        if (exemptDays != null) body.put("switchPinExemptDays", JSONArray(exemptDays))
        if (exemptStart != null) body.put("switchPinExemptStartMinute", exemptStart)
        if (exemptEnd != null) body.put("switchPinExemptEndMinute", exemptEnd)
        return body
    }

    private fun memberNamed(name: String): FamilyMember =
        FamilyHub.state.value.members.first { it.displayName == name }

    @Test
    fun defaultMemberStartsWithSwitchProtectionOff() {
        val family = FamilyHub.state.value
        val member = family.members.first { it.userId == family.defaultUserId }

        assertEquals("大宝", member.displayName)
        assertFalse(member.switchPinRequired)
        assertTrue(member.switchPinInitial)
        assertTrue(MemberPin.verify("123456", member.switchPinSalt, member.switchPinHash))
        assertFalse(MemberPin.verify("000000", member.switchPinSalt, member.switchPinHash))
    }

    @Test
    fun webAddWithoutPinDefaultsToOpenSwitch() {
        val name = uniqueName()
        val outcome = addMember(null, name = name)

        assertTrue(outcome.ok)
        assertEquals("SUBJECT_ADDED", outcome.messageKey)
        val added = memberNamed(name)
        assertFalse(added.switchPinRequired)
        assertTrue(added.switchPinInitial)
        assertTrue(MemberPin.verify("123456", added.switchPinSalt, added.switchPinHash))
    }

    @Test
    fun webAddWithProtectionRejectsMissingOrMalformedPin() {
        val before = FamilyHub.state.value.members.size

        assertEquals("SUBJECT_PIN_REQUIRED", addMember(null, required = true).code)
        assertEquals("SUBJECT_PIN_REQUIRED", addMember("", required = true).code)
        assertEquals("SUBJECT_PIN_INVALID", addMember("12345", required = true).code)
        assertEquals("SUBJECT_PIN_INVALID", addMember("12345a", required = true).code)
        assertEquals("SUBJECT_PIN_WEAK", addMember("123456", required = true).code)
        assertEquals(before, FamilyHub.state.value.members.size)
    }

    @Test
    fun webAddedProtectedMemberStoresItsOwnSwitchPin() {
        val name = uniqueName()
        val outcome = addMember("246800", required = true, name = name)

        assertTrue(outcome.ok)
        assertEquals("SUBJECT_ADDED", outcome.messageKey)
        val added = memberNamed(name)
        assertTrue(added.switchPinRequired)
        assertFalse(added.switchPinInitial)
        assertTrue(MemberPin.verify("246800", added.switchPinSalt, added.switchPinHash))
        assertFalse(MemberPin.verify("123456", added.switchPinSalt, added.switchPinHash))
        assertEquals("boy", added.avatarPreset)
    }

    @Test
    fun webAddedMemberMigratesLegacyAnimalPreset() {
        val name = uniqueName()
        val outcome = addMember("246801", name = name, preset = "rabbit")

        assertTrue(outcome.ok)
        val added = memberNamed(name)
        assertFalse(added.switchPinRequired)
        assertEquals("boy", added.avatarPreset)
    }

    @Test
    fun enablingProtectionRequiresANewPin() {
        val name = uniqueName()
        assertTrue(addMember(null, name = name).ok)
        val member = memberNamed(name)

        val outcome = ParentCenterActions.updateMemberProfile(
            profileBody(member, required = true),
        )

        assertEquals("SUBJECT_PIN_REQUIRED", outcome.code)
        assertFalse(memberNamed(name).switchPinRequired)
    }

    @Test
    fun enablingProtectionRejectsTheInitialPin() {
        val name = uniqueName()
        assertTrue(addMember(null, name = name).ok)
        val member = memberNamed(name)

        val outcome = ParentCenterActions.updateMemberProfile(
            profileBody(member, switchPin = "123456", required = true),
        )

        assertEquals("SUBJECT_PIN_WEAK", outcome.code)
        assertFalse(memberNamed(name).switchPinRequired)
    }

    @Test
    fun enablingProtectionWithCustomPinTurnsItOn() {
        val name = uniqueName()
        assertTrue(addMember(null, name = name).ok)
        val member = memberNamed(name)

        val outcome = ParentCenterActions.updateMemberProfile(
            profileBody(member, switchPin = "246802", required = true),
        )

        assertTrue(outcome.ok)
        val updated = memberNamed(name)
        assertTrue(updated.switchPinRequired)
        assertFalse(updated.switchPinInitial)
        assertTrue(MemberPin.verify("246802", updated.switchPinSalt, updated.switchPinHash))
    }

    @Test
    fun disablingProtectionKeepsTheStoredCredential() {
        val name = uniqueName()
        assertTrue(addMember("246803", required = true, name = name).ok)
        val member = memberNamed(name)

        val outcome = ParentCenterActions.updateMemberProfile(
            profileBody(member, required = false),
        )

        assertTrue(outcome.ok)
        val updated = memberNamed(name)
        assertFalse(updated.switchPinRequired)
        assertFalse(updated.switchPinInitial)
        assertTrue(MemberPin.verify("246803", updated.switchPinSalt, updated.switchPinHash))
    }

    @Test
    fun profileUpdateWithoutTheSwitchFieldKeepsProtection() {
        val name = uniqueName()
        assertTrue(addMember("246804", required = true, name = name).ok)
        val member = memberNamed(name)

        val outcome = ParentCenterActions.updateMemberProfile(profileBody(member))

        assertTrue(outcome.ok)
        val updated = memberNamed(name)
        assertTrue(updated.switchPinRequired)
        assertTrue(MemberPin.verify("246804", updated.switchPinSalt, updated.switchPinHash))
    }

    @Test
    fun webAddStoresOnlySortedSelectedExemptDays() {
        val name = uniqueName()
        val outcome = addMember(
            "246805",
            required = true,
            name = name,
            exemptDays = listOf(7, 1, 1),
            exemptStart = 9 * 60,
            exemptEnd = 15 * 60,
        )

        assertTrue(outcome.ok)
        val added = memberNamed(name)
        assertEquals(listOf(1, 7), added.switchPinExemptDays)
        assertEquals(9 * 60, added.switchPinExemptStartMinute)
        assertEquals(15 * 60, added.switchPinExemptEndMinute)
    }

    @Test
    fun webAddRejectsMalformedExemption() {
        val before = FamilyHub.state.value.members.size

        assertEquals(
            "SUBJECT_EXEMPT_INVALID",
            addMember("246806", required = true, exemptDays = listOf(0)).code,
        )
        assertEquals(
            "SUBJECT_EXEMPT_INVALID",
            addMember(
                "246807",
                required = true,
                exemptDays = listOf(1),
                exemptStart = 15 * 60,
                exemptEnd = 9 * 60,
            ).code,
        )
        assertEquals(before, FamilyHub.state.value.members.size)
    }

    @Test
    fun profileUpdateSavesAndClearsTheExemption() {
        val name = uniqueName()
        assertTrue(addMember("246808", required = true, name = name).ok)
        val member = memberNamed(name)

        val saved = ParentCenterActions.updateMemberProfile(
            profileBody(
                member,
                exemptDays = listOf(1, 2, 3, 4, 5),
                exemptStart = 9 * 60,
                exemptEnd = 15 * 60,
            ),
        )
        assertTrue(saved.ok)
        val withWindow = memberNamed(name)
        assertEquals(listOf(1, 2, 3, 4, 5), withWindow.switchPinExemptDays)

        // Leaving protection on but sending an empty day list turns the waiver off without
        // touching the stored password.
        val cleared = ParentCenterActions.updateMemberProfile(
            profileBody(withWindow, exemptDays = emptyList()),
        )
        assertTrue(cleared.ok)
        val withoutWindow = memberNamed(name)
        assertTrue(withoutWindow.switchPinExemptDays.isEmpty())
        assertTrue(MemberPin.verify("246808", withoutWindow.switchPinSalt, withoutWindow.switchPinHash))

        val disabled = ParentCenterActions.updateMemberProfile(
            profileBody(withoutWindow, required = false),
        )
        assertTrue(disabled.ok)
        assertFalse(memberNamed(name).switchPinRequired)
    }

    @Test
    fun profileUpdateWithoutExemptionFieldsKeepsTheWindow() {
        val name = uniqueName()
        assertTrue(
            addMember(
                "246809",
                required = true,
                name = name,
                exemptDays = listOf(6),
                exemptStart = 10 * 60,
                exemptEnd = 12 * 60,
            ).ok,
        )
        val member = memberNamed(name)

        assertTrue(ParentCenterActions.updateMemberProfile(profileBody(member)).ok)
        val updated = memberNamed(name)
        assertEquals(listOf(6), updated.switchPinExemptDays)
        assertEquals(10 * 60, updated.switchPinExemptStartMinute)
        assertEquals(12 * 60, updated.switchPinExemptEndMinute)
    }

    @Test
    fun profileUpdateRejectsMalformedExemption() {
        val name = uniqueName()
        assertTrue(addMember("246810", required = true, name = name).ok)
        val member = memberNamed(name)

        assertEquals(
            "SUBJECT_EXEMPT_INVALID",
            ParentCenterActions.updateMemberProfile(
                profileBody(member, exemptDays = listOf(8)),
            ).code,
        )
        assertEquals(
            "SUBJECT_EXEMPT_INVALID",
            ParentCenterActions.updateMemberProfile(
                profileBody(
                    member,
                    exemptDays = listOf(1),
                    exemptStart = 0,
                    exemptEnd = 24 * 60 + 1,
                ),
            ).code,
        )
        assertTrue(memberNamed(name).switchPinExemptDays.isEmpty())
    }

    @Test
    fun memberSwitchAuthUsesTheSelectedWeekdayAndHalfOpenWindow() {
        val member = FamilyMember(
            familyId = "fam_test",
            userId = "usr_exempt",
            displayName = "二宝",
            switchPinRequired = true,
            switchPinExemptDays = listOf(1),
            switchPinExemptStartMinute = 9 * 60,
            switchPinExemptEndMinute = 15 * 60,
            createdAt = 1L,
            updatedAt = 1L,
        )

        // 2026-11-23 is a Monday.
        assertTrue(MemberSwitchAuth.requiresPin(member, LocalDateTime.of(2026, 11, 23, 8, 59)))
        assertFalse(MemberSwitchAuth.requiresPin(member, LocalDateTime.of(2026, 11, 23, 9, 0)))
        assertFalse(MemberSwitchAuth.requiresPin(member, LocalDateTime.of(2026, 11, 23, 14, 59)))
        assertTrue(MemberSwitchAuth.requiresPin(member, LocalDateTime.of(2026, 11, 23, 15, 0)))
        // Tuesday is not selected.
        assertTrue(MemberSwitchAuth.requiresPin(member, LocalDateTime.of(2026, 11, 24, 12, 0)))
        // Protection off wins over any leftover selection.
        assertFalse(
            MemberSwitchAuth.requiresPin(
                member.copy(switchPinRequired = false, switchPinExemptDays = listOf(1)),
                LocalDateTime.of(2026, 11, 23, 12, 0),
            ),
        )
    }
}
