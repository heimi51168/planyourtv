package app.orchard.tv.policy.web

import android.app.Application
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.MemberPin
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.TvStore
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

/**
 * A Web-added member must carry an explicit switch password. The migration-created default
 * member keeps the documented initial 123456, which is what makes old no-roster installs
 * upgrade without losing access to the family switch.
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

    private fun addMember(switchPin: String?, name: String = "二宝"): ParentCenterActions.Outcome {
        val body = JSONObject()
            .put("name", name)
            .put("avatarPreset", "rabbit")
        if (switchPin != null) body.put("switchPin", switchPin)
        return ParentCenterActions.addMember(body)
    }

    @Test
    fun defaultMemberKeepsTheInitialSwitchPin() {
        val family = FamilyHub.state.value
        val member = family.members.first { it.userId == family.defaultUserId }

        assertEquals("大宝", member.displayName)
        assertTrue(member.switchPinInitial)
        assertTrue(MemberPin.verify("123456", member.switchPinSalt, member.switchPinHash))
        assertFalse(MemberPin.verify("000000", member.switchPinSalt, member.switchPinHash))
    }

    @Test
    fun webAddRejectsMissingOrMalformedSwitchPin() {
        val before = FamilyHub.state.value.members.size

        assertEquals("SUBJECT_PIN_REQUIRED", addMember(null).code)
        assertEquals("SUBJECT_PIN_REQUIRED", addMember("").code)
        assertEquals("SUBJECT_PIN_INVALID", addMember("12345").code)
        assertEquals("SUBJECT_PIN_INVALID", addMember("12345a").code)
        assertEquals(before, FamilyHub.state.value.members.size)
    }

    @Test
    fun webAddedMemberStoresItsOwnSwitchPin() {
        // Robolectric keeps the MMKV family roster between methods; a per-run name keeps
        // this assertion independent of test order and earlier second members.
        val name = "二宝" + (System.nanoTime() % 1000L).toString().padStart(3, '0')
        val outcome = addMember("246800", name)

        assertTrue(outcome.ok)
        assertEquals("SUBJECT_ADDED", outcome.messageKey)
        val added = FamilyHub.state.value.members.first { it.displayName == name }
        assertFalse(added.switchPinInitial)
        assertTrue(MemberPin.verify("246800", added.switchPinSalt, added.switchPinHash))
        assertFalse(MemberPin.verify("123456", added.switchPinSalt, added.switchPinHash))
    }
}