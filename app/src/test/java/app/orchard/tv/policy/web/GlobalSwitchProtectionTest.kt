package app.orchard.tv.policy.web

import android.app.Application
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.MemberSwitchAuth
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.SwitchProtectionSettings
import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.SwitchProtection
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
 * V57: one device-wide switch policy replaces the per-member flags. The old fields stay in the
 * document for rollback, but once the global migration is written they must not be able to turn
 * protection off or change the waiver window.
 */
@RunWith(RobolectricTestRunner::class)
class GlobalSwitchProtectionTest {
    private val parentPin = "246810"
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
        SwitchProtectionSettings.debugResetForTest()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(parentPin, rules()))
        PolicyModeCoordinator.debugForceMode(PolicyMode.LOCAL)
        ParentCenterActions.install(app)
        SwitchProtectionSettings.install(app)
    }

    @After
    fun tearDown() {
        SwitchProtectionSettings.debugResetForTest()
        ParentCenterActions.stop()
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
    }

    private fun defaultMember(): FamilyMember {
        val family = FamilyHub.state.value
        return family.members.first { it.userId == family.defaultUserId }
    }

    private fun uniqueName(): String =
        "二宝" + (System.nanoTime() % 100000L).toString().padStart(5, '0')

    /**
     * Writes the global policy directly so consumer tests start from a known policy without
     * going through the Web action and its snapshot.
     */
    private fun enableProtection(
        days: List<Int> = emptyList(),
        startMinute: Int = 9 * 60,
        endMinute: Int = 15 * 60,
    ) {
        assertTrue(
            SwitchProtectionSettings.setPolicy(
                SwitchProtection(
                    required = true,
                    exemptDays = days,
                    exemptStartMinute = startMinute,
                    exemptEndMinute = endMinute,
                ),
            ),
        )
    }

    @Test
    fun explicitEnableSucceedsWhileTheDefaultMemberUsesTheInitialPassword() {
        val outcome = ParentCenterActions.enableSwitchProtection(
            JSONObject().put("required", true),
        )

        assertTrue(outcome.ok)
        assertEquals("SWITCH_PROTECTION_SAVED", outcome.messageKey)
        assertTrue(SwitchProtectionSettings.current().required)
        // The page still sees which members need a stronger user password.
        assertTrue(defaultMember().switchPinInitial)
    }

    @Test
    fun unprotectedEnableActionCannotDisableTheSwitch() {
        val outcome = ParentCenterActions.enableSwitchProtection(
            JSONObject().put("required", false),
        )

        assertEquals("SWITCH_PROTECTION_INVALID", outcome.code)
        assertFalse(SwitchProtectionSettings.current().required)
    }

    @Test
    fun globalPolicyOverridesAnUnprotectedMember() {
        enableProtection(days = listOf(1))

        val member = defaultMember()
        assertFalse(member.switchPinRequired)
        // 2026-11-23 is a Monday.
        assertFalse(
            MemberSwitchAuth.requiresPin(member, LocalDateTime.of(2026, 11, 23, 10, 0)),
        )
        assertTrue(
            MemberSwitchAuth.requiresPin(member, LocalDateTime.of(2026, 11, 23, 15, 0)),
        )
        assertTrue(
            MemberSwitchAuth.requiresPin(member, LocalDateTime.of(2026, 11, 24, 10, 0)),
        )
    }

    @Test
    fun webAddRequiresAPasswordWhileGlobalProtectionIsOn() {
        enableProtection()
        val name = uniqueName()

        val missing = ParentCenterActions.addMember(
            JSONObject().put("name", name).put("avatarPreset", "boy"),
        )
        assertEquals("SUBJECT_PIN_REQUIRED", missing.code)

        val added = ParentCenterActions.addMember(
            JSONObject()
                .put("name", name)
                .put("avatarPreset", "boy")
                .put("switchPin", "975310"),
        )
        assertTrue(added.ok)
        val member = FamilyHub.state.value.members.first { it.displayName == name }
        assertTrue(member.switchPinRequired)
        assertFalse(member.switchPinInitial)
        assertTrue(
            MemberSwitchAuth.requiresPin(member, LocalDateTime.of(2026, 11, 24, 10, 0)),
        )
    }

    @Test
    fun aLegacyPerMemberWriteCannotDisableGlobalProtection() {
        enableProtection()
        val name = uniqueName()
        assertTrue(
            ParentCenterActions.addMember(
                JSONObject()
                    .put("name", name)
                    .put("avatarPreset", "boy")
                    .put("switchPin", "975310"),
            ).ok,
        )
        val member = FamilyHub.state.value.members.first { it.displayName == name }

        val updated = ParentCenterActions.updateMemberProfile(
            JSONObject()
                .put("userId", member.userId)
                .put("name", member.displayName)
                .put("avatarPreset", member.avatarPreset)
                .put("switchPinRequired", false),
        )
        assertTrue(updated.ok)
        assertFalse(
            FamilyHub.state.value.members.first { it.userId == member.userId }.switchPinRequired,
        )
        assertTrue(
            MemberSwitchAuth.requiresPin(
                FamilyHub.state.value.members.first { it.userId == member.userId },
                LocalDateTime.of(2026, 11, 24, 10, 0),
            ),
        )
    }

    @Test
    fun migrationKeepsProtectionWhenTheOldDeviceSwitchWasOn() {
        val member = defaultMember().copy(
            switchPinRequired = false,
            switchPinExemptDays = emptyList(),
        )

        assertTrue(
            SwitchProtectionSettings.migrateFromMembers(
                members = listOf(member),
                legacyFallbackRequired = true,
            ),
        )
        assertTrue(SwitchProtectionSettings.current().required)
        assertFalse(SwitchProtectionSettings.current().hasExemption)
    }

    @Test
    fun migrationInheritsTheWaiverOnlyWhenEveryProtectedMemberAgrees() {
        val base = defaultMember()
        val first = base.copy(
            userId = "usr_a",
            switchPinRequired = true,
            switchPinExemptDays = listOf(2, 1),
            switchPinExemptStartMinute = 9 * 60,
            switchPinExemptEndMinute = 15 * 60,
        )
        val same = first.copy(userId = "usr_b", switchPinExemptDays = listOf(1, 2))
        val different = first.copy(userId = "usr_c", switchPinExemptDays = listOf(1))

        assertTrue(
            SwitchProtectionSettings.migrateFromMembers(
                members = listOf(first, same),
                legacyFallbackRequired = false,
            ),
        )
        assertEquals(
            SwitchProtection(
                required = true,
                exemptDays = listOf(1, 2),
                exemptStartMinute = 9 * 60,
                exemptEndMinute = 15 * 60,
            ),
            SwitchProtectionSettings.current(),
        )

        SwitchProtectionSettings.debugResetForTest()
        SwitchProtectionSettings.install(app)
        assertTrue(
            SwitchProtectionSettings.migrateFromMembers(
                members = listOf(first, same, different),
                legacyFallbackRequired = false,
            ),
        )
        val merged = SwitchProtectionSettings.current()
        assertTrue(merged.required)
        assertFalse(merged.hasExemption)
    }
}