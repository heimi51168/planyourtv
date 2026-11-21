package app.orchard.tv.policy

import android.app.Application
import android.os.PowerManager
import app.orchard.tv.system.BootOpen
import com.planyourtv.policy.AccessMode
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalSessionEnd
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyActivation
import com.planyourtv.policy.PolicySessionOrigin
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.PolicyStateRepository
import com.planyourtv.policy.RuntimeLedger
import com.planyourtv.policy.RuntimeSnapshot
import com.planyourtv.policy.SessionPhase
import com.planyourtv.policy.SessionSnapshot
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
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
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.LocalDate

/**
 * V37: the process is assumed to die at any time. These tests rebuild the engine from storage the
 * same way a cold start would, and check that guard leases, budgets and settlements survive.
 */
@RunWith(RobolectricTestRunner::class)
class PolicyRestartRecoveryTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
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
        allowedPackages = setOf(videoPackage),
    )

    private fun state() = LocalPolicyRuntime.state.value

    private fun powerManager() = shadowOf(app.getSystemService(PowerManager::class.java))

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        powerManager().setIsInteractive(true)
    }

    private fun startRuntime() {
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules()))
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        LocalPolicyRuntime.activate()
    }

    /** Drops the singleton's memory and re-runs initialize(), like a killed process restarting. */
    private fun restartProcess() {
        LocalPolicyRuntime.debugSimulateProcessRestart()
        powerManager().setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
    }

    @Test
    fun parentOverrideSurvivesAProcessRestartInTheSameBoot() {
        startRuntime()
        assertTrue(LocalPolicyRuntime.tryBeginParentOverrideFromLockScreen())
        assertEquals(GuardStatus.PARENT_OVERRIDE, state().guardState)

        restartProcess()

        assertEquals(GuardStatus.PARENT_OVERRIDE, state().guardState)
        assertTrue(LocalPolicyRuntime.isTemporaryUnlocked())
        assertFalse(LocalPolicyRuntime.isLockScreen())
    }

    @Test
    fun parentOverrideEndsWhenTheScreenWentOffWhileTheProcessWasDead() {
        startRuntime()
        assertTrue(LocalPolicyRuntime.tryBeginParentOverrideFromLockScreen())

        // The screen-off broadcast is handled by another component while the guard process is
        // dead; only the shared BootOpen marker survives.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5))
        powerManager().setIsInteractive(false)
        BootOpen.noteScreenOff(app)
        powerManager().setIsInteractive(true)
        restartProcess()

        assertEquals(GuardStatus.ENFORCING, state().guardState)
        assertTrue(LocalPolicyRuntime.isLockScreen())
    }

    @Test
    fun aTimedParentOverrideDoesNotSurviveItsDeadline() {
        startRuntime()
        LocalPolicyRuntime.setTemporaryUnlock(true, durationMs = 100L)
        assertEquals(GuardStatus.PARENT_OVERRIDE, state().guardState)

        // Wall time is real in this harness; the lease deadline is what must decide.
        Thread.sleep(250L)
        restartProcess()

        assertEquals(GuardStatus.ENFORCING, state().guardState)
        assertFalse(LocalPolicyRuntime.isTemporaryUnlocked())
        assertTrue(LocalPolicyRuntime.isLockScreen())
    }

    @Test
    fun anArmedRemoteGrantStillWaitsForConfirmationAfterARestart() {
        startRuntime()
        LocalPolicyRuntime.setSource(PolicySource.HOSTED)
        LocalPolicyRuntime.activate()
        val now = System.currentTimeMillis()
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.startRemoteManualSession(
                durationSeconds = 600L,
                expectedEpoch = LocalPolicyRuntime.rulesEpoch,
                allowedPackages = setOf(videoPackage),
                expiresAtEpochMs = now + 300_000L,
                recordId = 42,
            ),
        )
        assertEquals(SessionState.PENDING, state().sessionState)

        restartProcess()
        LocalPolicyRuntime.setSource(PolicySource.HOSTED)

        assertEquals(SessionState.PENDING, state().sessionState)
        assertEquals(600L, state().totalSeconds)
    }

    @Test
    fun runningSessionChargesTheProvableDeadGapAndKeepsItsBudget() {
        startRuntime()
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.startManualSession(600L))
        repeat(30) { LocalPolicyRuntime.onTick() }
        assertEquals(570L, state().remainingSeconds)

        // The screen stayed on and the kernel did not suspend: elapsed and wall time advanced
        // together, so the 75 seconds the process was dead must be charged to the child.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(75))
        restartProcess()

        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertEquals(600L, state().totalSeconds)
        assertEquals(495L, state().remainingSeconds)
    }

    @Test
    fun aRestoredSessionIsPausedWhenTheScreenIsOff() {
        startRuntime()
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.startManualSession(600L))
        repeat(30) { LocalPolicyRuntime.onTick() }

        powerManager().setIsInteractive(false)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))
        restartProcessWithoutLiftingTheScreen()

        assertEquals(SessionState.ACTIVE, state().sessionState)
        // The inactive interval is not charged: a dark screen keeps the budget untouched.
        assertEquals(570L, state().remainingSeconds)
    }

    private fun restartProcessWithoutLiftingTheScreen() {
        LocalPolicyRuntime.debugSimulateProcessRestart()
        powerManager().setIsInteractive(false)
        LocalPolicyRuntime.initialize(app)
    }

    @Test
    fun anExpiredSessionIsSettledExactlyOnceAcrossRestarts() {
        startRuntime()
        // A snapshot exactly as the engine would have checkpointed it, except its wall deadline
        // is already in the past: the process came back too late for this segment.
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val saved = RuntimeSnapshot(
            session = SessionSnapshot(
                sessionId = "rule-expired-test",
                phase = SessionPhase.ACTIVE,
                totalSeconds = 600L,
                remainingSeconds = 570L,
                endReason = LocalSessionEnd.SESSION_LIMIT,
                usageDate = today,
                startedAtEpochMs = now - 30_000L,
                wallDeadlineEpochMs = now - 1_000L,
                activation = PolicyActivation.MANUAL,
                accessMode = AccessMode.APPS,
                allowedPackages = setOf(videoPackage),
                origin = PolicySessionOrigin.LOCAL,
            ),
            ledger = RuntimeLedger(days = mapOf(today to 0L)),
        )
        assertTrue(PolicyStateRepository.saveRuntime(saved, durable = true, forceSync = true))

        restartProcess()
        assertEquals(SessionState.IDLE, state().sessionState)
        assertEquals(30L, state().dailyUsedSeconds)

        restartProcess()
        assertEquals(SessionState.IDLE, state().sessionState)
        assertEquals(30L, state().dailyUsedSeconds)
    }
}
