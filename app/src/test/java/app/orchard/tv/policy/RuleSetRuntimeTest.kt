package app.orchard.tv.policy

import android.os.PowerManager
import com.planyourtv.policy.Activation
import com.planyourtv.policy.LocalRuleSet
import com.planyourtv.policy.LocalRuleSets
import com.planyourtv.policy.LocalPolicyStore
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalSharedAppScope
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.LockReason
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.TvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The runtime keeps one materialized [LocalRules] for the executor, but AUTO and MANUAL persist
 * independent time sets. Switching swaps the stored rules without rewriting either set, ends the
 * rest cursor earned by the old mode, and must not interrupt a session for an inactive-set edit.
 */
@RunWith(RobolectricTestRunner::class)
class RuleSetRuntimeTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"

    private fun state() = LocalPolicyRuntime.state.value

    private fun rules() = LocalRules(
        weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        autoDailyLimitMinutes = 60,
        sessionLimitMinutes = 20,
        restMinutes = 5,
        allowedPackages = setOf(videoPackage),
    )

    private fun sets(
        auto: LocalRuleSet = LocalRuleSet(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            autoDailyLimitMinutes = 60,
            sessionLimitMinutes = 20,
            restMinutes = 5,
        ),
        manual: LocalRuleSet = LocalRuleSet(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            autoDailyLimitMinutes = 120,
            sessionLimitMinutes = 30,
            restMinutes = 0,
        ),
    ) = LocalRuleSets(
        auto = auto,
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
    fun inactiveSetEditDoesNotBumpEpochOrInterruptTheExecutor() {
        assertTrue(LocalPolicyRuntime.setRuleSetsWithVersion(sets(), 1L))
        val activeEpoch = LocalPolicyRuntime.state.value.policyEpoch
        assertEquals(Activation.MANUAL, LocalPolicyRuntime.state.value.activation)
        assertEquals(120, LocalPolicyRuntime.currentRules!!.autoDailyLimitMinutes)
        assertEquals(0, LocalPolicyRuntime.currentRules!!.restMinutes)

        // Only AUTO (the inactive set) changes: store it for the next switch without an epoch
        // bump, so the running manual configuration and its grants stay valid.
        val inactiveEdited = sets(
            auto = sets().auto.copy(autoDailyLimitMinutes = 90, restMinutes = 8),
        )
        assertTrue(LocalPolicyRuntime.setRuleSetsWithVersion(inactiveEdited, 2L))
        assertEquals(activeEpoch, LocalPolicyRuntime.state.value.policyEpoch)
        assertEquals(120, LocalPolicyRuntime.currentRules!!.autoDailyLimitMinutes)
        assertEquals(0, LocalPolicyRuntime.currentRules!!.restMinutes)
        assertEquals(90, LocalPolicyRuntime.currentRuleSets!!.auto.autoDailyLimitMinutes)
    }

    @Test
    fun switchingSwapsTheStoredSetWithoutRewritingEitherRest() {
        assertTrue(LocalPolicyRuntime.setRuleSetsWithVersion(sets(), 1L))
        assertTrue(LocalPolicyRuntime.setActivation(Activation.AUTO))

        // AUTO runs its own quota and rest, exactly as stored.
        assertEquals(60, LocalPolicyRuntime.currentRules!!.autoDailyLimitMinutes)
        assertEquals(5, LocalPolicyRuntime.currentRules!!.restMinutes)

        assertTrue(LocalPolicyRuntime.setActivation(Activation.MANUAL))

        // MANUAL still has its own rest; the switch did not adopt AUTO's default.
        assertEquals(120, LocalPolicyRuntime.currentRules!!.autoDailyLimitMinutes)
        assertEquals(0, LocalPolicyRuntime.currentRules!!.restMinutes)
        assertEquals(5, LocalPolicyRuntime.currentRuleSets!!.auto.restMinutes)
    }

    @Test
    fun switchingFromAutoEndsTheRestEarnedByAuto() {
        assertTrue(LocalPolicyRuntime.setActivation(Activation.AUTO))
        LocalPolicyRuntime.debugStartSession(60L)
        assertTrue(LocalPolicyRuntime.endSessionByUser())
        assertEquals(LockReason.RESTING, state().lockReason)
        assertTrue(state().restRemainingSeconds > 0L)
        assertTrue(LocalPolicyStore.breakUntil() > System.currentTimeMillis())
        assertTrue(LocalPolicyStore.lastSessionEndedAt() > 0L)

        assertTrue(LocalPolicyRuntime.setActivation(Activation.MANUAL))

        assertEquals(Activation.MANUAL, state().activation)
        assertEquals(LockReason.MANUAL_WAITING, state().lockReason)
        assertEquals(0L, state().restRemainingSeconds)
        assertEquals(0L, LocalPolicyStore.breakUntil())
        // The recovery timestamp must go too, or initialize() would rebuild the old rest.
        assertEquals(0L, LocalPolicyStore.lastSessionEndedAt())
    }

    @Test
    fun switchingToAutoEndsTheRestEarnedByManual() {
        LocalPolicyRuntime.debugStartSession(60L)
        assertTrue(LocalPolicyRuntime.endSessionByUser())
        assertEquals(LockReason.RESTING, state().lockReason)
        assertTrue(state().restRemainingSeconds > 0L)

        assertTrue(LocalPolicyRuntime.setActivation(Activation.AUTO))

        assertEquals(Activation.AUTO, state().activation)
        assertEquals(LockReason.AUTO_READY, state().lockReason)
        assertTrue(state().autoReady)
        assertEquals(0L, state().restRemainingSeconds)
        assertEquals(0L, LocalPolicyStore.breakUntil())
        assertEquals(0L, LocalPolicyStore.lastSessionEndedAt())
    }

    @Test
    fun switchingModeSettlesARunningSessionWithoutStartingANewRest() {
        assertTrue(LocalPolicyRuntime.setActivation(Activation.AUTO))
        LocalPolicyRuntime.debugStartSession(10 * 60L)
        assertEquals(SessionState.ACTIVE, state().sessionState)

        assertTrue(LocalPolicyRuntime.setActivation(Activation.MANUAL))

        assertEquals(SessionState.IDLE, state().sessionState)
        assertEquals(LockReason.MANUAL_WAITING, state().lockReason)
        assertEquals(0L, LocalPolicyStore.breakUntil())
        assertEquals(0L, LocalPolicyStore.lastSessionEndedAt())
    }
}
