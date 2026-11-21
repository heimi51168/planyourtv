package app.orchard.tv.policy

import android.app.Application
import android.os.PowerManager
import com.planyourtv.policy.LocalPolicyStore
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.TvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Unlimited apps are launched with a one-shot permit, never a session or a ledger entry. While one
 * is in front, both entertainment and study timers freeze without charging their budgets; a
 * screen-off pause outranks that reason and is not undone by screen-on alone.
 */
@RunWith(RobolectricTestRunner::class)
class UnlimitedRuntimeTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
    private val studyPackage = "com.example.study"
    private val unlimitedPackage = "com.example.free"
    private val allDay = LocalTimeWindow(0, 24 * 60)
    private lateinit var app: Application

    private fun state() = LocalPolicyRuntime.state.value

    /** Seconds charged by the running session, not the plan budget. */
    private fun consumed(): Long = state().let {
        (it.totalSeconds - it.remainingSeconds).coerceAtLeast(0L)
    }

    private fun powerManager() = shadowOf(app.getSystemService(PowerManager::class.java))

    private fun rules() = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(allDay),
            weekendWindows = listOf(allDay),
            weekdayLimitMinutes = null,
            weekendLimitMinutes = null,
            studyWindows = listOf(allDay),
            studyLimitMinutes = 2,
        ),
        sessionLimitMinutes = 20,
        restMinutes = 0,
        allowedPackages = setOf(videoPackage),
        studyPackages = setOf(studyPackage),
        // The study round is opt-in since 2.0.001.
        studyEarnEnabled = true,
        unlimitedPackages = setOf(unlimitedPackage),
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        LockLaunchPermit.clearForeground()
        powerManager().setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        assertTrue(LocalPolicyRuntime.completeSetup(pin, rules()) != null)
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        LocalPolicyRuntime.activate()
    }

    private fun restartProcess() {
        LocalPolicyRuntime.debugSimulateProcessRestart()
        powerManager().setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        LocalPolicyRuntime.activate()
    }

    @Test
    fun anUnlimitedAppFreezesTheSessionWithoutChargingIt() {
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.startManualSession(10 * 60L))
        repeat(5) { LocalPolicyRuntime.onTick() }
        val charged = consumed()
        assertTrue(charged >= 5L)

        LocalPolicyRuntime.onForegroundChanged(unlimitedPackage)
        repeat(30) { LocalPolicyRuntime.onTick() }

        assertEquals(charged, consumed())
        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertTrue(unlimitedPackage in state().allowedPackages)

        // Returning to a timed app resumes exactly where the pause began.
        LocalPolicyRuntime.onForegroundChanged(videoPackage)
        LocalPolicyRuntime.onTick()
        assertEquals(charged + 1, consumed())
    }

    @Test
    fun screenOffOutranksUnlimitedAndScreenOnDoesNotResumeBehindIt() {
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.startManualSession(10 * 60L))
        repeat(3) { LocalPolicyRuntime.onTick() }
        val charged = consumed()

        LocalPolicyRuntime.onForegroundChanged(unlimitedPackage)
        powerManager().setIsInteractive(false)
        LocalPolicyRuntime.onScreenOff()
        repeat(10) { LocalPolicyRuntime.onTick() }
        assertEquals(charged, consumed())

        // The unlimited app is still in front, so lighting the screen must not spend budget.
        powerManager().setIsInteractive(true)
        LocalPolicyRuntime.onScreenOn()
        repeat(10) { LocalPolicyRuntime.onTick() }
        assertEquals(charged, consumed())

        LocalPolicyRuntime.onForegroundChanged(videoPackage)
        LocalPolicyRuntime.onTick()
        assertEquals(charged + 1, consumed())
    }

    @Test
    fun aRestartKeepsAnUnlimitedPauseFrozen() {
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.startManualSession(10 * 60L))
        repeat(2) { LocalPolicyRuntime.onTick() }
        val charged = consumed()
        LocalPolicyRuntime.onForegroundChanged(unlimitedPackage)

        restartProcess()
        repeat(20) { LocalPolicyRuntime.onTick() }
        assertEquals(charged, consumed())
        assertEquals(SessionState.ACTIVE, state().sessionState)

        LocalPolicyRuntime.onForegroundChanged(videoPackage)
        LocalPolicyRuntime.onTick()
        assertEquals(charged + 1, consumed())
    }

    @Test
    fun aStudyRoundAlsoFreezesBehindAnUnlimitedApp() {
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startStudyRound(studyPackage, null),
        )
        repeat(3) { LocalPolicyRuntime.onTick() }
        val studied = state().studyRound!!.studiedSeconds

        LocalPolicyRuntime.onForegroundChanged(unlimitedPackage)
        repeat(20) { LocalPolicyRuntime.onTick() }

        assertEquals(studied, state().studyRound!!.studiedSeconds)
        assertTrue(state().studyRound!!.paused)

        // Resume must stay explicit: a foreground change alone does not restart the round.
        LocalPolicyRuntime.onForegroundChanged(videoPackage)
        LocalPolicyRuntime.onTick()
        assertEquals(studied, state().studyRound!!.studiedSeconds)

        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.resumeStudyRound())
        LocalPolicyRuntime.onTick()
        assertEquals(studied + 1, state().studyRound!!.studiedSeconds)
    }

    @Test
    fun anUnlimitedLaunchUsesAPermitButNoSessionOrLedger() {
        LockLaunchPermit.grant(unlimitedPackage)
        assertTrue(LockLaunchPermit.consume(unlimitedPackage))
        assertTrue(LockLaunchPermit.allowsForeground(unlimitedPackage))
        // The permit is one-shot.
        assertFalse(LockLaunchPermit.consume(unlimitedPackage))

        LocalPolicyRuntime.onForegroundChanged(unlimitedPackage)
        repeat(30) { LocalPolicyRuntime.onTick() }

        assertEquals(SessionState.IDLE, state().sessionState)
        assertNull(LocalPolicyRuntime.currentPlan)
        assertEquals(0L, state().dailyUsedSeconds)
        assertEquals(0L, state().studyDailyUsedSeconds)
        assertNull(LocalPolicyStore.loadSession())
    }
}