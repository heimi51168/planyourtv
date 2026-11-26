package app.orchard.tv.policy

import android.app.Application
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.TvStore
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
 * The LOCAL-only child-mode gate around maker Settings/security/market entries and the desktop
 * "App info"/"Uninstall" rows. Study mode and the parent override are read from the same guard
 * state the Home and guard paths use.
 */
@RunWith(RobolectricTestRunner::class)
class SystemEntryGateTest {
    private val pin = "246810"
    private val settingsPackage = "com.xiaomi.mitv.settings"
    private val tclGuardPackage = "com.tcl.guard"
    private lateinit var app: Application

    private fun rules() = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekdayLimitMinutes = 60,
            weekendLimitMinutes = 60,
        ),
        sessionLimitMinutes = 20,
        restMinutes = 5,
        allowedPackages = setOf("com.example.video"),
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules()))
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        PolicyModeCoordinator.debugForceMode(PolicyMode.LOCAL)
        SystemEntrySettings.install(app)
        SystemEntrySettings.debugResetForTest()
        SystemEntrySettings.install(app)
        SystemEntryCatalog.debugSetForTest(
            setOf(settingsPackage, tclGuardPackage),
        )
    }

    @After
    fun tearDown() {
        SystemEntrySettings.debugResetForTest()
        SystemEntryCatalog.debugSetForTest(emptySet())
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
    }

    @Test
    fun defaultOffHidesAndBlocksLaunchableEntries() {
        assertEquals(GuardStatus.ENFORCING, LocalPolicyRuntime.state.value.guardState)
        assertTrue(PolicyModeCoordinator.systemEntriesRestricted())
        assertFalse(PolicyModeCoordinator.appManagementAllowed())
        assertFalse(PolicyModeCoordinator.allowsHomePackage(settingsPackage))
        assertTrue(PolicyModeCoordinator.shouldBlock(app, settingsPackage))
        // The TCL security app is also the installer: hide its tile, never foreground-block it.
        assertFalse(PolicyModeCoordinator.allowsHomePackage(tclGuardPackage))
        assertFalse(PolicyModeCoordinator.shouldBlock(app, tclGuardPackage))
        // Ordinary apps keep the existing session gate.
        assertTrue(PolicyModeCoordinator.allowsHomePackage("com.example.video"))
    }

    @Test
    fun switchOnReleasesEntriesInNormalChildMode() {
        assertTrue(SystemEntrySettings.setVisible(true))
        assertFalse(PolicyModeCoordinator.systemEntriesRestricted())
        assertTrue(PolicyModeCoordinator.systemEntriesReleased())
        assertTrue(PolicyModeCoordinator.appManagementAllowed())
        assertTrue(PolicyModeCoordinator.allowsHomePackage(settingsPackage))
        assertFalse(PolicyModeCoordinator.shouldBlock(app, settingsPackage))
    }

    @Test
    fun parentOverrideShowsEverythingWithoutReleasingTheSwitch() {
        assertTrue(SystemEntrySettings.setVisible(false))
        LocalPolicyRuntime.setTemporaryUnlock(true)
        assertEquals(GuardStatus.PARENT_OVERRIDE, LocalPolicyRuntime.state.value.guardState)
        assertFalse(PolicyModeCoordinator.systemEntriesRestricted())
        assertFalse(PolicyModeCoordinator.systemEntriesReleased())
        assertTrue(PolicyModeCoordinator.allowsHomePackage(settingsPackage))
        assertFalse(PolicyModeCoordinator.shouldBlock(app, settingsPackage))
    }
}