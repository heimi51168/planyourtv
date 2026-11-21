package app.orchard.tv.policy

import android.app.Application
import android.os.PowerManager
import app.orchard.tv.quiz.QuizLaunchTarget
import com.planyourtv.policy.BootEvidence
import com.planyourtv.policy.LocalPolicyStore
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.SavedStudyRound
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.TvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * A focus round is only a timer: it starts no entertainment session and writes no ledger until
 * it completes. Every study app is timed by the study window/quota; only the reward switch
 * decides whether a full focus round turns into entertainment time.
 */
@RunWith(RobolectricTestRunner::class)
class StudyRoundRuntimeTest {
    private val pin = "246810"
    private val studyPackage = "com.example.study"
    private val videoPackage = "com.example.video"
    private lateinit var app: Application

    private fun state() = LocalPolicyRuntime.state.value

    private fun powerManager() = shadowOf(app.getSystemService(PowerManager::class.java))

    private fun rules(
        entertainmentLimit: Int? = 60,
        studyLimit: Int? = 2,
        studyMinutes: Int = 1,
        rewardMinutes: Int = 1,
        studyWindows: List<LocalTimeWindow> = listOf(LocalTimeWindow(0, 24 * 60)),
    ) = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekdayLimitMinutes = entertainmentLimit,
            weekendLimitMinutes = entertainmentLimit,
            studyWindows = studyWindows,
            studyLimitMinutes = studyLimit,
        ),
        sessionLimitMinutes = 20,
        restMinutes = 0,
        allowedPackages = setOf(videoPackage),
        studyPackages = setOf(studyPackage),
        studyEarnPackages = setOf(studyPackage),
        studyEarnStudyMinutes = studyMinutes,
        studyEarnRewardMinutes = rewardMinutes,
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        QuizLaunchTarget.clear()
        powerManager().setIsInteractive(true)
    }

    private fun startRuntime(rules: LocalRules = rules()) {
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules))
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
    fun aRoundStartsWithoutASessionAndCompletesIntoTheReward() {
        startRuntime()

        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startStudyRound(studyPackage, videoPackage),
        )
        assertEquals(SessionState.IDLE, state().sessionState)
        // Two-minute study quota, so the reward fires with study time still on the clock.
        assertEquals(120L, state().studyRound?.remainingSeconds)
        assertEquals(videoPackage, state().studyRound?.targetPackage)
        assertEquals(0L, state().dailyUsedSeconds)

        repeat(60) { LocalPolicyRuntime.onTick() }

        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertEquals(60L, state().totalSeconds)
        assertNull(state().studyRound)
        assertEquals(60L, state().studyDailyUsedSeconds)
        assertEquals(
            QuizLaunchTarget.Target.App(videoPackage),
            QuizLaunchTarget.confirmed.value,
        )
    }

    @Test
    fun theRewardGrantsEveryAuthorizedAppNotJustTheTarget() {
        val secondVideoPackage = "com.example.video2"
        startRuntime(rules().copy(allowedPackages = setOf(videoPackage, secondVideoPackage)))

        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startStudyRound(studyPackage, videoPackage),
        )
        repeat(60) { LocalPolicyRuntime.onTick() }

        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertEquals(
            setOf(videoPackage, secondVideoPackage, studyPackage),
            state().allowedPackages,
        )
    }

    @Test
    fun everySelectedStudyAppEarnsEvenFromAnOldPerAppSubset() {
        // V45 removed the per-app switches. An old empty subset normalizes to the study scope,
        // so the round still earns instead of silently studying without a reward.
        startRuntime(rules().copy(studyEarnPackages = emptySet()))

        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startStudyRound(studyPackage, null),
        )
        assertTrue(state().studyRound!!.rewardEnabled)
    }

    @Test
    fun theStudyMasterSwitchRejectsANewFocusRound() {
        startRuntime(rules().copy(studyEarnEnabled = false))

        assertEquals(
            SessionStartResult.Rejected(RejectReason.INVALID_COMMAND),
            LocalPolicyRuntime.startStudyRound(studyPackage, null),
        )
        assertNull(state().studyRound)
        assertEquals(SessionState.IDLE, state().sessionState)
    }

    @Test
    fun anUnavailableRewardDoesNotStopTheStudyRound() {
        startRuntime(rules(entertainmentLimit = 0))

        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startStudyRound(studyPackage, videoPackage),
        )
        repeat(60) { LocalPolicyRuntime.onTick() }

        assertEquals(SessionState.IDLE, state().sessionState)
        assertNotNull(state().studyRound)
        assertTrue(state().studyRound!!.rewardAttempted)
        assertFalse(state().studyRound!!.rewardGranted)

        repeat(60) { LocalPolicyRuntime.onTick() }
        assertNull(state().studyRound)
    }

    @Test
    fun leavingTheStudyAppPausesUntilTheChildResumes() {
        startRuntime()
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startStudyRound(studyPackage, videoPackage),
        )
        repeat(5) { LocalPolicyRuntime.onTick() }
        assertEquals(115L, state().studyRound?.remainingSeconds)
        assertFalse(state().studyRound!!.paused)

        // Aerial is back in front: the child is on the focus desktop, not studying.
        assertTrue(LocalPolicyRuntime.pauseStudyRound("AERIAL_RESUMED"))
        repeat(20) { LocalPolicyRuntime.onTick() }
        assertEquals(115L, state().studyRound?.remainingSeconds)
        assertTrue(state().studyRound!!.paused)
        // The pause is on disk at once: a process killed in standby cannot add dead time.
        assertEquals(115L, LocalPolicyStore.loadStudyRound()?.remainingSeconds)

        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.resumeStudyRound())
        assertFalse(state().studyRound!!.paused)
        LocalPolicyRuntime.onTick()
        assertEquals(114L, state().studyRound?.remainingSeconds)
    }

    @Test
    fun screenOffPausesAndScreenOnNeverResumesByItself() {
        startRuntime()
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startStudyRound(studyPackage, videoPackage),
        )
        repeat(5) { LocalPolicyRuntime.onTick() }
        assertEquals(115L, state().studyRound?.remainingSeconds)

        powerManager().setIsInteractive(false)
        LocalPolicyRuntime.onScreenOff()
        repeat(30) { LocalPolicyRuntime.onTick() }
        assertEquals(115L, state().studyRound?.remainingSeconds)
        assertTrue(state().studyRound!!.paused)

        powerManager().setIsInteractive(true)
        LocalPolicyRuntime.onScreenOn()
        repeat(10) { LocalPolicyRuntime.onTick() }
        assertEquals(115L, state().studyRound?.remainingSeconds)
        assertTrue(state().studyRound!!.paused)

        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.resumeStudyRound())
        LocalPolicyRuntime.onTick()
        assertEquals(114L, state().studyRound?.remainingSeconds)
    }

    @Test
    fun cancelClearsTheRoundAndThePersistedCopyWithoutReward() {
        startRuntime()
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startStudyRound(studyPackage, videoPackage),
        )

        assertTrue(LocalPolicyRuntime.cancelStudyRound("CHILD_EXIT"))
        assertNull(state().studyRound)
        assertNull(LocalPolicyStore.loadStudyRound())
        assertEquals(SessionState.IDLE, state().sessionState)
        assertEquals(0L, state().dailyUsedSeconds)
    }

    @Test
    fun aSameBootRestoreKeepsTheSavedTimeAndAlwaysStaysPaused() {
        startRuntime()
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startStudyRound(studyPackage, videoPackage),
        )
        repeat(5) { LocalPolicyRuntime.onTick() }

        restartProcess()

        // activate() runs one immediate tick after the restore, but a restored round is paused:
        // the process cannot prove the child was still studying while it was dead.
        assertEquals(115L, state().studyRound?.remainingSeconds)
        assertEquals(videoPackage, state().studyRound?.targetPackage)
        assertTrue(state().studyRound!!.paused)

        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.resumeStudyRound())
        LocalPolicyRuntime.onTick()
        assertEquals(114L, state().studyRound?.remainingSeconds)
    }

    @Test
    fun aRoundFromAnotherBootIsDropped() {
        startRuntime()
        LocalPolicyStore.saveStudyRound(
            SavedStudyRound(
                studyPackage = studyPackage,
                targetPackage = videoPackage,
                totalSeconds = 600L,
                remainingSeconds = 300L,
                rewardSeconds = 300L,
                startedAtEpochMs = System.currentTimeMillis(),
                boot = BootEvidence(bootId = "another-boot", bootStampMs = 1L),
            ),
        )

        restartProcess()

        assertNull(state().studyRound)
        assertNull(LocalPolicyStore.loadStudyRound())
    }

    @Test
    fun anInsufficientStudyQuotaIsRejectedBeforeStarting() {
        startRuntime(rules(studyLimit = 0))

        val result = LocalPolicyRuntime.startStudyRound(studyPackage, videoPackage)

        assertEquals(
            SessionStartResult.Rejected(RejectReason.DAILY_LIMIT),
            result,
        )
        assertNull(state().studyRound)
        assertNull(LocalPolicyStore.loadStudyRound())
    }

    @Test
    fun aClosedStudyWindowIsRejectedBeforeStarting() {
        startRuntime(rules(studyWindows = emptyList()))

        val result = LocalPolicyRuntime.startStudyRound(studyPackage, videoPackage)

        assertEquals(
            SessionStartResult.Rejected(RejectReason.OUTSIDE_WINDOW),
            result,
        )
        assertNull(state().studyRound)
    }

    @Test
    fun aNonStudyAppCannotStartARound() {
        startRuntime()

        val result = LocalPolicyRuntime.startStudyRound(videoPackage, videoPackage)

        assertEquals(
            SessionStartResult.Rejected(RejectReason.INVALID_COMMAND),
            result,
        )
        assertNull(state().studyRound)
    }
}