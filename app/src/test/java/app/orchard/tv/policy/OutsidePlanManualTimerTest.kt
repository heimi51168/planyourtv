package app.orchard.tv.policy

import android.app.Application
import android.os.PowerManager
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.LockReason
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.SessionStartResult
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
 * V45: the parent Web timer is a deliberate out-of-plan authorization. It launches the manual
 * visible scope (entertainment + unlimited + enabled study reward), bypasses rest/window/quota,
 * and never writes the daily ledger, route counts or rest.
 */
@RunWith(RobolectricTestRunner::class)
class OutsidePlanManualTimerTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
    private val studyPackage = "com.example.study"
    private val freePackage = "com.example.free"
    private lateinit var app: Application

    private fun rules(
        allowed: Set<String> = setOf(videoPackage),
        study: Set<String> = setOf(studyPackage),
        unlimited: Set<String> = setOf(freePackage),
        studyEarnEnabled: Boolean = true,
    ) = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekdayLimitMinutes = 60,
            weekendLimitMinutes = 60,
        ),
        sessionLimitMinutes = 20,
        restMinutes = 10,
        allowedPackages = allowed,
        studyPackages = study,
        unlimitedPackages = unlimited,
        studyEarnEnabled = studyEarnEnabled,
    )

    private fun state() = LocalPolicyRuntime.state.value

    private fun startRuntime(rules: LocalRules = rules()) {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules))
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        LocalPolicyRuntime.activate()
    }

    @Before
    fun setUp() {
        startRuntime()
    }

    @Test
    fun theManualTimerLaunchesTheFullManualScopeAndWritesNoLedger() {
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startManualSession(10 * 60L, outsidePlan = true),
        )
        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertEquals(10 * 60L, state().totalSeconds)
        // Entertainment + unlimited + the enabled study-reward app, regardless of ordering in
        // the configuration.
        assertEquals(
            setOf(videoPackage, freePackage, studyPackage),
            state().allowedPackages,
        )

        repeat(5) { LocalPolicyRuntime.onTick() }
        assertTrue(LocalPolicyRuntime.endSessionByUser())

        assertEquals(SessionState.IDLE, state().sessionState)
        // Out-of-plan time never lands in the daily quota, and exiting starts no rest.
        assertEquals(0L, state().dailyUsedSeconds)
        assertEquals(0L, state().restRemainingSeconds)
        assertEquals(LockReason.MANUAL_WAITING, state().lockReason)
        val plan = requireNotNull(state().unlockRoutePlan)
        assertEquals(0, plan.quiz.used)
        assertEquals(0, plan.studyReward.used)
        assertEquals(0, plan.direct.used)
    }

    @Test
    fun theManualTimerStartsWhenThePlanHasNoTimedApps() {
        // Only a study app is configured: the child plan has no timed entertainment, yet the
        // parent may still hand out a plan-exempt session on that study-reward app.
        startRuntime(rules(allowed = setOf(studyPackage), unlimited = emptySet()))

        assertTrue(state().noTimedApps)
        assertEquals(
            SessionStartResult.Rejected(RejectReason.NO_TIMED_APPS),
            LocalPolicyRuntime.startManualSession(10 * 60L),
        )
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startManualSession(10 * 60L, outsidePlan = true),
        )
        assertEquals(setOf(studyPackage), state().allowedPackages)
    }

    @Test
    fun anUnlimitedOnlyScopeNeverStartsACountdown() {
        // The same app is both the entertainment list and unlimited: unlimited wins. With no
        // study-reward app either, there is nothing the timer could count down.
        startRuntime(
            rules(
                allowed = setOf(videoPackage),
                study = emptySet(),
                unlimited = setOf(videoPackage),
            ),
        )

        assertEquals(
            SessionStartResult.Rejected(RejectReason.NO_TIMED_APPS),
            LocalPolicyRuntime.startManualSession(10 * 60L, outsidePlan = true),
        )
        assertEquals(SessionState.IDLE, state().sessionState)
    }

    @Test
    fun theStudySwitchExcludesTheRewardAppFromTheManualScope() {
        startRuntime(
            rules(
                allowed = setOf(studyPackage),
                unlimited = emptySet(),
                studyEarnEnabled = false,
            ),
        )

        assertEquals(
            SessionStartResult.Rejected(RejectReason.NO_TIMED_APPS),
            LocalPolicyRuntime.startManualSession(10 * 60L, outsidePlan = true),
        )
        assertEquals(SessionState.IDLE, state().sessionState)
    }

    @Test
    fun anInPlanManualStartUsesTheSessionScopeAndEarnsRest() {
        // The lock-screen timer keeps its V44 ledger/rest behavior but shares the session scope.
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startManualSession(10 * 60L),
        )
        assertEquals(SessionState.ACTIVE, state().sessionState)
        // Unlimited apps stay launchable during an in-plan session; time from them is not counted.
        assertEquals(
            setOf(videoPackage, freePackage, studyPackage),
            state().allowedPackages,
        )

        repeat(5) { LocalPolicyRuntime.onTick() }
        assertTrue(LocalPolicyRuntime.endSessionByUser())

        assertEquals(5L, state().dailyUsedSeconds)
        assertTrue(state().restRemainingSeconds > 0L)
        assertEquals(LockReason.RESTING, state().lockReason)
    }
}