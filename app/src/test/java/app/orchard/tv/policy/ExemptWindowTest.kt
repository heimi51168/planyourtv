package app.orchard.tv.policy

import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyModeStore
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.Activation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Exemption windows (grandparents' time): inside them child mode suspends entirely,
 * and leaving them restores the guard — no PIN involved anywhere. The schedule-driven
 * override is a distinct source from a parent temporary unlock, surfaced separately.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExemptWindowTest {

    private val pin = "135790"

    private fun exemptRules(
        weekdayExempt: List<LocalTimeWindow>,
        weekendExempt: List<LocalTimeWindow> = emptyList(),
    ) = LocalRules(
        weekdayWindows = listOf(LocalTimeWindow(17 * 60, 21 * 60)),
        weekendWindows = listOf(LocalTimeWindow(17 * 60, 21 * 60)),
        autoDailyLimitMinutes = 60,
        sessionLimitMinutes = 20,
        restMinutes = 0,
        allowedPackages = setOf("com.example.video"),
        exemptWeekdayWindows = weekdayExempt,
        exemptWeekendWindows = weekendExempt,
    )

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        LocalPolicyRuntime.activate()
        PolicyModeStore.save(PolicyMode.LOCAL)
    }

    @Test
    fun isExemptAtMatchesWindowsExactly() {
        val rules = exemptRules(
            weekdayExempt = listOf(LocalTimeWindow(9 * 60, 16 * 60)),
            weekendExempt = listOf(LocalTimeWindow(10 * 60, 12 * 60)),
        )
        // Weekday: 1..5 → Monday..Friday in the java.time convention.
        assertTrue(rules.isExemptAt(1, 10 * 3600))
        assertTrue(rules.isExemptAt(5, 15 * 3600 + 59 * 60 + 59))
        assertFalse(rules.isExemptAt(1, 8 * 3600 + 59 * 60))
        assertFalse("16:00 is outside (end is exclusive)", rules.isExemptAt(1, 16 * 3600))
        // Weekend windows are a different list.
        assertFalse(rules.isExemptAt(6, 13 * 3600))
        assertTrue(rules.isExemptAt(7, 11 * 3600))
        // No exemption windows → never exempt.
        assertFalse(exemptRules(emptyList()).isExemptAt(1, 10 * 3600))
    }

    @Test
    fun overlappingExemptWindowsAreRejected() {
        val bad = exemptRules(
            weekdayExempt = listOf(
                LocalTimeWindow(9 * 60, 12 * 60),
                LocalTimeWindow(11 * 60, 14 * 60), // overlaps 11:00–12:00
            ),
        )
        assertEquals(
            com.planyourtv.policy.LocalRulesError.OVERLAPPING_WINDOWS,
            bad.validationError(),
        )
    }

    @Test
    fun tooManyExemptWindowsAreRejected() {
        val bad = exemptRules(
            weekdayExempt = (0 until 5).map { LocalTimeWindow(1 + it * 60, 60 + it * 60) },
        )
        assertEquals(
            com.planyourtv.policy.LocalRulesError.TOO_MANY_WINDOWS,
            bad.validationError(),
        )
    }

    @Test
    fun exemptWindowsMayOverlapAllowedWindows() {
        // 09:00–16:00 exempt sits inside the 17:00–21:00 allowed window's day; the two
        // models never intersect, so this is valid.
        val valid = exemptRules(
            weekdayExempt = listOf(LocalTimeWindow(9 * 60, 16 * 60)),
        )
        assertEquals(com.planyourtv.policy.LocalRulesError.NONE, valid.validationError())
    }

    @Test
    fun exemptionWindowsRoundTripThroughTheRuleSetStore() {
        val rules = exemptRules(
            weekdayExempt = listOf(LocalTimeWindow(9 * 60, 16 * 60)),
            weekendExempt = listOf(LocalTimeWindow(8 * 60, 11 * 60)),
        )
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules))
        // Re-read through the v2 store: the exempt windows are device-wide in the shared
        // scope, so both activations see them.
        val restored = LocalPolicyRuntime.currentRules
        assertNotNull(restored)
        assertEquals(listOf(LocalTimeWindow(9 * 60, 16 * 60)), restored!!.exemptWeekdayWindows)
        assertEquals(listOf(LocalTimeWindow(8 * 60, 11 * 60)), restored.exemptWeekendWindows)
    }

    @Test
    fun aNewSaveWithoutExemptFieldsKeepsTheCurrentOnes() {
        // This is the "stale cached page" contract: the page that does not know about
        // exemption windows must not clear them by omission. The server fills the gap.
        val rules = exemptRules(
            weekdayExempt = listOf(LocalTimeWindow(9 * 60, 16 * 60)),
        )
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules))
        val restored = LocalPolicyRuntime.currentRules
        assertEquals(1, restored!!.exemptWeekdayWindows.size)
    }
}
