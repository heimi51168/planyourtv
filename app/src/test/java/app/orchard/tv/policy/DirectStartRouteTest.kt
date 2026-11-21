package app.orchard.tv.policy

import android.app.Application
import android.os.PowerManager
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
 * V44 routes: direct start begins one session without a quiz or study round and respects the
 * direct "per viewing" value, the public single-session cap and the day's remaining quota.
 * The three routes are independent; the study master switch only hides its own launcher.
 */
@RunWith(RobolectricTestRunner::class)
class DirectStartRouteTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
    private val studyPackage = "com.example.study"
    private val allDay = LocalTimeWindow(0, 24 * 60)
    private lateinit var app: Application

    private fun state() = LocalPolicyRuntime.state.value

    private fun rules(
        directStart: Boolean = true,
        studyEnabled: Boolean = true,
        quizEnabled: Boolean = true,
        dailyLimit: Int? = 60,
        directMinutes: Int = 30,
        capMinutes: Int = 30,
        directCount: Int = 3,
    ) = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(allDay),
            weekendWindows = listOf(allDay),
            weekdayLimitMinutes = dailyLimit,
            weekendLimitMinutes = dailyLimit,
        ),
        sessionLimitMinutes = directMinutes,
        sessionCapMinutes = capMinutes,
        directDailyFreeCount = directCount,
        restMinutes = 0,
        quizGateEnabled = quizEnabled,
        studyEarnEnabled = studyEnabled,
        directStartEnabled = directStart,
        allowedPackages = setOf(videoPackage),
        studyPackages = setOf(studyPackage),
        studyEarnPackages = setOf(studyPackage),
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
    fun directStartUsesTheConfiguredSingleSessionDuration() {
        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalDirectSession())

        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertEquals(30 * 60L, state().totalSeconds)
        assertEquals(30 * 60L, state().sessionLimitSeconds)
        assertEquals(30 * 60L, state().sessionCapSeconds)
        assertTrue(state().directStartEnabled)
        // V44 routes are independent: turning direct on does not pause quiz or study.
        assertTrue(state().quizGateEnabled)
        assertTrue(state().studyModeAvailable)
        assertEquals(setOf(studyPackage), state().studyPackages)
        // A local session grants every authorized app, not only entertainment.
        assertEquals(setOf(videoPackage, studyPackage), state().allowedPackages)
        val plan = requireNotNull(state().unlockRoutePlan)
        assertEquals(1, plan.direct.used)
        assertTrue(plan.quiz.visible)
        assertTrue(plan.studyReward.visible)
    }

    @Test
    fun directStartKeepsStudyAppsBlockedWhenTheStudySwitchIsOff() {
        startRuntime(rules(studyEnabled = false))

        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalDirectSession())

        assertEquals(setOf(videoPackage), state().allowedPackages)
    }

    @Test
    fun directUsesItsOwnPerViewingDurationBelowThePublicCap() {
        startRuntime(rules(directMinutes = 20, capMinutes = 30))

        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalDirectSession())

        assertEquals(20 * 60L, state().totalSeconds)
    }

    @Test
    fun legacyDirectValueAboveTheCapIsTruncatedAtRuntime() {
        startRuntime(rules(directMinutes = 40, capMinutes = 30))

        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalDirectSession())

        assertEquals(30 * 60L, state().totalSeconds)
    }

    @Test
    fun aZeroDirectCountKeepsTheEntryVisibleButUnavailable() {
        startRuntime(rules(directCount = 0))

        val plan = requireNotNull(state().unlockRoutePlan)
        assertTrue(plan.direct.visible)
        assertFalse(plan.direct.available)
        assertEquals(
            com.planyourtv.policy.UnlockRouteReason.COUNT_ZERO,
            plan.direct.reason,
        )
        assertEquals(
            SessionStartResult.Rejected(RejectReason.ROUTE_LIMIT),
            PolicyModeCoordinator.startLocalDirectSession(),
        )
        assertNull(LocalPolicyRuntime.currentPlan)
    }

    @Test
    fun earlyExitDoesNotRefundTheRouteCount() {
        startRuntime(rules(directMinutes = 20))

        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalDirectSession())
        repeat(5) { LocalPolicyRuntime.onTick() }
        assertTrue(PolicyModeCoordinator.endSessionByUser())

        val plan = requireNotNull(state().unlockRoutePlan)
        assertEquals(1, plan.direct.used)
        assertEquals(2, plan.direct.remaining)
    }

    @Test
    fun aParentManualSessionDoesNotConsumeAChildRouteCount() {
        startRuntime(rules(directStart = false))

        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startManualSession(10 * 60L),
        )

        val plan = requireNotNull(state().unlockRoutePlan)
        assertEquals(0, plan.direct.used)
        assertEquals(0, plan.quiz.used)
        assertEquals(0, plan.studyReward.used)
    }

    @Test
    fun theDailyQuotaStillCapsADirectSession() {
        startRuntime(rules(dailyLimit = 10))

        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalDirectSession())

        assertEquals(10 * 60L, state().totalSeconds)
    }

    @Test
    fun directStartIsRefusedWhenTheSwitchIsOff() {
        startRuntime(rules(directStart = false))

        assertEquals(
            SessionStartResult.Rejected(RejectReason.INVALID_COMMAND),
            PolicyModeCoordinator.startLocalDirectSession(),
        )
        assertNull(LocalPolicyRuntime.currentPlan)
        assertEquals(SessionState.IDLE, state().sessionState)
    }

    @Test
    fun theStudyMasterSwitchClosesStudyAndHidesItsTiles() {
        startRuntime(rules(directStart = false, studyEnabled = false))

        assertFalse(state().studyModeAvailable)
        assertEquals(emptySet<String>(), state().studyPackages)
        assertFalse(PolicyModeCoordinator.enterStudyMode())
        assertEquals(
            SessionStartResult.Rejected(RejectReason.INVALID_COMMAND),
            LocalPolicyRuntime.startStudyRound(studyPackage, targetPackage = null),
        )
        // The document keeps the lists so turning the switch back on restores them.
        assertEquals(setOf(studyPackage), LocalPolicyRuntime.currentRules!!.studyPackages)
        assertEquals(
            setOf(studyPackage),
            LocalPolicyRuntime.currentRules!!.effectiveStudyEarnPackages,
        )
    }

    @Test
    fun quizRouteFollowsItsOwnSwitch() {
        startRuntime(rules(directStart = false, quizEnabled = false))

        assertFalse(state().quizGateEnabled)
        assertTrue(state().studyModeAvailable)
        assertTrue(state().directStartEnabled.not())
    }
}