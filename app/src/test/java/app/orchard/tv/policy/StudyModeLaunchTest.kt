package app.orchard.tv.policy

import android.app.Application
import android.os.PowerManager
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.TvStore
import org.junit.After
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
 * The study desktop is a grid, not an untimed bypass. A tile leaves the grid only after the study
 * window/quota admission succeeds; while the grid is open only study and unlimited apps are in
 * scope, and no other path may start a study app without a round.
 */
@RunWith(RobolectricTestRunner::class)
class StudyModeLaunchTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
    private val studyPackage = "com.example.study"
    private val unlimitedPackage = "com.example.free"
    private val allDay = LocalTimeWindow(0, 24 * 60)
    private lateinit var app: Application

    private fun state() = LocalPolicyRuntime.state.value

    private fun rules(
        studyLimit: Int? = 2,
        studyWindows: List<LocalTimeWindow> = listOf(allDay),
    ) = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(allDay),
            weekendWindows = listOf(allDay),
            weekdayLimitMinutes = null,
            weekendLimitMinutes = null,
            studyWindows = studyWindows,
            studyLimitMinutes = studyLimit,
        ),
        sessionLimitMinutes = 20,
        restMinutes = 0,
        allowedPackages = setOf(videoPackage),
        studyPackages = setOf(studyPackage),
        unlimitedPackages = setOf(unlimitedPackage),
    )

    private fun startRuntime(rules: LocalRules = rules()) {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        LockLaunchPermit.clearForeground()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules))
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        PolicyModeCoordinator.debugForceMode(PolicyMode.LOCAL)
    }

    @Before
    fun setUp() {
        startRuntime()
    }

    @After
    fun tearDown() {
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
        LockLaunchPermit.clearForeground()
    }

    @Test
    fun theGridOnlyExposesStudyAndUnlimitedApps() {
        assertTrue(PolicyModeCoordinator.enterStudyMode())
        assertEquals(GuardStatus.STUDY_MODE, state().guardState)
        assertTrue(state().studyModeActive)
        assertEquals(
            setOf(studyPackage, unlimitedPackage),
            state().allowedPackages,
        )

        assertFalse(PolicyModeCoordinator.shouldBlock(app, studyPackage))
        assertFalse(PolicyModeCoordinator.shouldBlock(app, unlimitedPackage))
        assertTrue(PolicyModeCoordinator.shouldBlock(app, videoPackage))
    }

    @Test
    fun aGridTileStartsATimedRoundAndLeavesTheGrid() {
        assertTrue(PolicyModeCoordinator.enterStudyMode())

        val result = PolicyModeCoordinator.startStudyFromStudyMode(studyPackage)

        assertEquals(SessionStartResult.Started, result)
        assertFalse(LocalPolicyRuntime.isStudyMode())
        assertEquals(GuardStatus.ENFORCING, state().guardState)
        assertEquals(studyPackage, state().studyRound?.studyPackage)
        assertEquals(SessionState.IDLE, state().sessionState)
        assertNull(LocalPolicyRuntime.currentPlan)
        // The allowlist keeps the study app in front for the duration of the round.
        assertTrue(LockLaunchPermit.allowsForeground(studyPackage))
    }

    @Test
    fun aRejectedAdmissionKeepsTheGridOpen() {
        startRuntime(rules(studyLimit = 0))
        assertTrue(PolicyModeCoordinator.enterStudyMode())

        assertEquals(
            SessionStartResult.Rejected(RejectReason.DAILY_LIMIT),
            PolicyModeCoordinator.startStudyFromStudyMode(studyPackage),
        )

        assertTrue(LocalPolicyRuntime.isStudyMode())
        assertEquals(GuardStatus.STUDY_MODE, state().guardState)
        assertNull(state().studyRound)
        assertNull(LocalPolicyRuntime.currentPlan)
    }

    @Test
    fun aClosedStudyWindowAlsoKeepsTheGridOpen() {
        startRuntime(rules(studyWindows = emptyList()))
        assertTrue(PolicyModeCoordinator.enterStudyMode())

        assertEquals(
            SessionStartResult.Rejected(RejectReason.OUTSIDE_WINDOW),
            PolicyModeCoordinator.startStudyFromStudyMode(studyPackage),
        )

        assertTrue(LocalPolicyRuntime.isStudyMode())
        assertNull(state().studyRound)
    }

    @Test
    fun aStudyPackageCannotStartARoundWhileTheGridIsOpen() {
        assertTrue(PolicyModeCoordinator.enterStudyMode())

        // A recents/external-style start must not create a second, untimed path around the grid.
        assertEquals(
            SessionStartResult.Rejected(RejectReason.BUSY),
            PolicyModeCoordinator.startStudyRound(studyPackage, targetPackage = null),
        )
        assertTrue(LocalPolicyRuntime.isStudyMode())
        assertNull(state().studyRound)
    }

    @Test
    fun anEntertainmentSessionBlocksStudyAppsButKeepsUnlimitedInScope() {
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.startManualSession(10 * 60L))

        assertFalse(PolicyModeCoordinator.shouldBlock(app, videoPackage))
        assertFalse(PolicyModeCoordinator.shouldBlock(app, unlimitedPackage))
        // Study apps are timed only by their own round; they cannot be opened from recents during
        // an entertainment session.
        assertTrue(PolicyModeCoordinator.shouldBlock(app, studyPackage))
    }

    @Test
    fun aNonStudyPackageCannotStartAStudyRound() {
        assertEquals(
            SessionStartResult.Rejected(RejectReason.INVALID_COMMAND),
            PolicyModeCoordinator.startStudyRound(videoPackage, targetPackage = null),
        )
        assertNull(state().studyRound)
    }
}