package app.orchard.tv.ui.policy

import app.orchard.tv.policy.PolicyInstalledApp
import com.planyourtv.policy.ALL_VALID_APP
import com.planyourtv.policy.LocalRules
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyParentAppPickerTest {
    private fun app(pkg: String, label: String = pkg) =
        PolicyInstalledApp(packageName = pkg, label = label, stamp = 0L)

    private fun rules(vararg packages: String) =
        LocalRules.DEFAULTS.copy(allowedPackages = packages.toSet()).normalized()

    @Test
    fun installedAppsFollowTheAllAppsTile() {
        val entries = buildAppPickerEntries(
            listOf(app("com.a", "A"), app("com.b", "B")),
            LocalRules.DEFAULTS,
        )
        assertEquals(3, entries.size)
        assertEquals(AppPickerEntry.AllApps, entries[0])
        assertEquals(
            listOf("com.a", "com.b"),
            entries.drop(1).map { (it as AppPickerEntry.App).packageName },
        )
    }

    @Test
    fun uninstalledPackagesStayVisibleAndSorted() {
        val entries = buildAppPickerEntries(
            listOf(app("com.a", "A"), app("com.still", "Still")),
            rules("com.a", "com.still", "com.gone.z", "com.gone.a"),
        )
        val missing = entries.drop(1)
            .filterIsInstance<AppPickerEntry.App>()
            .filter { !it.installed }
        assertEquals(listOf("com.gone.a", "com.gone.z"), missing.map { it.packageName })
        // The label falls back to the package name so a stale tile is never blank.
        assertEquals("com.gone.a", missing.first().label)
    }

    @Test
    fun uninstalledPackagesStayHiddenWhileAllAppsIsSelected() {
        val entries = buildAppPickerEntries(listOf(app("com.a")), LocalRules.DEFAULTS)
        assertEquals(2, entries.size)
        assertEquals(ALL_VALID_APP, LocalRules.DEFAULTS.effectiveAllowedPackages.single())
    }

    @Test
    fun verticalMovementStaysInTheSameColumn() {
        // 2 -> 7 keeps the column; the last row is partial and never wraps into row 0.
        assertEquals(7, moveAppPickerIndex(2, 13, 0, 1))
        assertEquals(2, moveAppPickerIndex(7, 13, 0, -1))
        assertEquals(11, moveAppPickerIndex(11, 13, 0, 1))
    }

    @Test
    fun edgesNeverWrap() {
        assertEquals(0, moveAppPickerIndex(0, 7, -1, 0))
        assertEquals(4, moveAppPickerIndex(4, 7, 1, 0))
        assertEquals(0, moveAppPickerIndex(0, 7, 0, -1))
        assertEquals(3, moveAppPickerIndex(3, 4, 0, 1))
        assertEquals(0, moveAppPickerIndex(0, 0, 0, 1))
    }

    @Test
    fun pickerOpensOnTheFirstSelectedApp() {
        val entries = buildAppPickerEntries(listOf(app("com.a"), app("com.b")), rules("com.b"))
        assertEquals(2, appPickerInitialIndex(entries, rules("com.b")))
        assertEquals(0, appPickerInitialIndex(entries, LocalRules.DEFAULTS))
    }
}
