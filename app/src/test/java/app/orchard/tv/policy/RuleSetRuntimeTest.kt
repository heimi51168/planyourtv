package app.orchard.tv.policy

import android.os.PowerManager
import com.planyourtv.policy.Activation
import com.planyourtv.policy.LocalRuleSet
import com.planyourtv.policy.LocalRuleSets
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalSharedAppScope
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.TvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The runtime keeps one materialized [LocalRules] for the executor. Applying a stored manual set
 * replaces that snapshot, while rewriting it with identical content must neither bump the epoch
 * nor disturb the running configuration.
 */
@RunWith(RobolectricTestRunner::class)
class RuleSetRuntimeTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"

    private fun state() = LocalPolicyRuntime.state.value

    private fun allDayGroups(limitMinutes: Int?) = LocalRules.fromLegacyWeekdayWeekend(
        weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        weekdayLimitMinutes = limitMinutes,
        weekendLimitMinutes = limitMinutes,
    )

    private fun rules() = LocalRules(
        dayGroups = allDayGroups(60),
        sessionLimitMinutes = 20,
        restMinutes = 5,
        allowedPackages = setOf(videoPackage),
    )

    private fun sets(
        manual: LocalRuleSet = LocalRuleSet(
            dayGroups = allDayGroups(120),
            sessionLimitMinutes = 30,
            restMinutes = 0,
        ),
    ) = LocalRuleSets(
        manual = manual,
        shared = LocalSharedAppScope(allowedPackages = setOf(videoPackage)),
    )

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules()))
        LocalPolicyRuntime.setSource(PolicySource.HOSTED)
        LocalPolicyRuntime.activate()
    }

    @Test
    fun applyingTheStoredManualSetUpdatesTheExecutor() {
        val stored = sets()
        assertTrue(LocalPolicyRuntime.setRuleSetsWithVersion(stored, 1L))

        assertEquals(Activation.MANUAL, state().activation)
        assertEquals(120, LocalPolicyRuntime.currentRules!!.autoDailyLimitMinutes)
        assertEquals(0, LocalPolicyRuntime.currentRules!!.restMinutes)
        assertEquals(stored, LocalPolicyRuntime.currentRuleSets)
    }

    @Test
    fun rewritingTheSameSetDoesNotBumpTheEpoch() {
        assertTrue(LocalPolicyRuntime.setRuleSetsWithVersion(sets(), 1L))
        val appliedEpoch = state().policyEpoch

        // A cloud mirror or a web write may repeat the stored set; unchanged content must not
        // invalidate the grants and pending confirmations of the current configuration.
        assertTrue(LocalPolicyRuntime.setRuleSetsWithVersion(sets(), 2L))

        assertEquals(appliedEpoch, state().policyEpoch)
        assertEquals(120, LocalPolicyRuntime.currentRules!!.autoDailyLimitMinutes)
    }

    @Test
    fun anUnusableSetIsRejectedWithoutTouchingTheExecutor() {
        val before = LocalPolicyRuntime.currentRules

        assertFalse(
            LocalPolicyRuntime.setRuleSetsWithVersion(
                sets(
                    manual = LocalRuleSet(
                        dayGroups = allDayGroups(120).map {
                            it.copy(studyWindows = emptyList(), entertainmentWindows = emptyList())
                        },
                    ),
                ),
                1L,
            ),
        )

        assertEquals(before, LocalPolicyRuntime.currentRules)
    }
}