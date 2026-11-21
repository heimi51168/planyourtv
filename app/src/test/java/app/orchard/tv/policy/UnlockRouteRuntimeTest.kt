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
import com.planyourtv.policy.UnlockRouteReason
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
 * V44 runtime accounting: only a successfully established child session consumes one route
 * count; the shared quota is debited at settlement, so a partially available quota grants the
 * remainder and still consumes one count. Early exit never refunds the count.
 */
@RunWith(RobolectricTestRunner::class)
class UnlockRouteRuntimeTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
    private val studyPackage = "com.example.study"
    private val allDay = LocalTimeWindow(0, 24 * 60)
    private lateinit var app: Application

    private fun state() = LocalPolicyRuntime.state.value

    private fun rules(
        quiz: Boolean = true,
        study: Boolean = true,
        direct: Boolean = true,
        quotaMinutes: Int? = 60,
        directMinutes: Int = 20,
        capMinutes: Int = 30,
        quizCount: Int = 3,
        studyCount: Int = 3,
        directCount: Int = 3,
        studyMinutes: Int = 5,
        rewardMinutes: Int = 5,
    ) = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(allDay),
            weekendWindows = listOf(allDay),
            weekdayLimitMinutes = quotaMinutes,
            weekendLimitMinutes = quotaMinutes,
            studyWindows = listOf(allDay),
            studyLimitMinutes = studyMinutes,
        ),
        sessionLimitMinutes = directMinutes,
        sessionCapMinutes = capMinutes,
        quizDailyLimitCount = quizCount,
        studyRewardDailyLimitCount = studyCount,
        directDailyFreeCount = directCount,
        restMinutes = 0,
        quizGateEnabled = quiz,
        studyEarnEnabled = study,
        directStartEnabled = direct,
        allowedPackages = setOf(videoPackage),
        studyPackages = setOf(studyPackage),
        studyEarnPackages = setOf(studyPackage),
        studyEarnStudyMinutes = studyMinutes,
        studyEarnRewardMinutes = rewardMinutes,
    )

    private fun startRuntime(rules: LocalRules = rules()) {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules))
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        PolicyModeCoordinator.debugForceMode(PolicyMode.LOCAL)
        LocalPolicyRuntime.activate()
    }

    @Before
    fun setUp() {
        startRuntime()
    }

    @After
    fun tearDown() {
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
    }

    @Test
    fun quizRewardIsCappedByThePublicCapAndConsumesOneCount() {
        startRuntime(rules(study = false, direct = false, capMinutes = 30))

        assertEquals(30 * 60L, LocalPolicyRuntime.quizGrantSeconds(60 * 60L))
        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalQuizSession(60 * 60L))

        assertEquals(30 * 60L, state().totalSeconds)
        val plan = requireNotNull(state().unlockRoutePlan)
        assertEquals(1, plan.quiz.used)
        assertEquals(2, plan.quiz.remaining)
        assertEquals(0, plan.direct.used)
        assertEquals(0, plan.studyReward.used)
    }

    @Test
    fun aQuizSessionGrantsEveryAuthorizedApp() {
        startRuntime()

        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalQuizSession(60L))

        assertEquals(setOf(videoPackage, studyPackage), state().allowedPackages)
    }

    @Test
    fun aQuizSessionKeepsStudyAppsBlockedWhenTheStudySwitchIsOff() {
        startRuntime(rules(study = false))

        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalQuizSession(60L))

        assertEquals(setOf(videoPackage), state().allowedPackages)
    }

    @Test
    fun quizUsesOnlyTheRemainingQuotaAndStillConsumesOneCount() {
        startRuntime(rules(study = false, direct = false, quotaMinutes = 5))

        assertEquals(5 * 60L, LocalPolicyRuntime.quizGrantSeconds(60 * 60L))
        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalQuizSession(60 * 60L))

        assertEquals(5 * 60L, state().totalSeconds)
        assertEquals(1, requireNotNull(state().unlockRoutePlan).quiz.used)
    }

    @Test
    fun anExhaustedQuizCountRejectsWithoutStartingASession() {
        startRuntime(rules(study = false, direct = false, quizCount = 1))

        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalQuizSession(60L))
        assertTrue(PolicyModeCoordinator.endSessionByUser())

        val plan = requireNotNull(state().unlockRoutePlan)
        assertEquals(1, plan.quiz.used)
        assertEquals(UnlockRouteReason.COUNT_EXHAUSTED, plan.quiz.reason)
        assertEquals(
            SessionStartResult.Rejected(RejectReason.ROUTE_LIMIT),
            PolicyModeCoordinator.startLocalQuizSession(60L),
        )
        assertEquals(SessionState.IDLE, state().sessionState)
        assertEquals(1, requireNotNull(state().unlockRoutePlan).quiz.used)
    }

    @Test
    fun directUsesTheRemainingQuotaAndOneCount() {
        startRuntime(rules(quiz = false, study = false, quotaMinutes = 5, directMinutes = 20))

        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalDirectSession())

        assertEquals(5 * 60L, state().totalSeconds)
        assertEquals(1, requireNotNull(state().unlockRoutePlan).direct.used)
    }

    @Test
    fun studyRewardUsesTheRemainingQuotaAndOneCount() {
        startRuntime(
            rules(
                quiz = false,
                direct = false,
                quotaMinutes = 3,
                studyMinutes = 5,
                rewardMinutes = 5,
            ),
        )
        assertEquals(
            SessionStartResult.Started,
            PolicyModeCoordinator.startStudyRound(studyPackage, videoPackage),
        )

        repeat(5 * 60) { LocalPolicyRuntime.onTick() }

        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertEquals(3 * 60L, state().totalSeconds)
        assertEquals(1, requireNotNull(state().unlockRoutePlan).studyReward.used)
    }

    @Test
    fun routeCountsSurviveAProcessRestart() {
        startRuntime(rules(study = false, quiz = false, directMinutes = 20))
        assertEquals(SessionStartResult.Started, PolicyModeCoordinator.startLocalDirectSession())

        LocalPolicyRuntime.debugSimulateProcessRestart()
        LocalPolicyRuntime.initialize(app)

        val plan = requireNotNull(state().unlockRoutePlan)
        assertEquals(1, plan.direct.used)
        assertFalse(plan.direct.available)
        assertEquals(UnlockRouteReason.BUSY, plan.direct.reason)
    }

    @Test
    fun allRoutesOffLeavesAVisibleEmptyPlanWithoutCrashes() {
        startRuntime(rules(quiz = false, study = false, direct = false))

        val plan = requireNotNull(state().unlockRoutePlan)
        assertFalse(plan.anyEnabled)
        assertFalse(plan.anyAvailable)
        assertTrue(plan.decisions.all { !it.visible })
        assertEquals(SessionState.IDLE, state().sessionState)
    }

    @Test
    fun directAndQuizAreIndependentWhileStudyIsUnconfigured() {
        // V45: the legacy per-app subset no longer disables the reward; "unconfigured" means
        // there is no selected study app at all.
        startRuntime(rules(study = true, direct = true).copy(studyPackages = emptySet()))

        val plan = requireNotNull(state().unlockRoutePlan)
        assertTrue(plan.quiz.available)
        assertTrue(plan.direct.available)
        assertTrue(plan.studyReward.visible)
        assertFalse(plan.studyReward.available)
        assertEquals(UnlockRouteReason.STUDY_NOT_CONFIGURED, plan.studyReward.reason)
    }

    @Test
    fun aZeroRouteCountKeepsTheEntryVisibleAndUnavailable() {
        startRuntime(rules(quiz = true, study = false, direct = false, quizCount = 0))

        val plan = requireNotNull(state().unlockRoutePlan)
        assertTrue(plan.quiz.visible)
        assertFalse(plan.quiz.available)
        assertEquals(UnlockRouteReason.COUNT_ZERO, plan.quiz.reason)
        assertEquals(
            SessionStartResult.Rejected(RejectReason.ROUTE_LIMIT),
            PolicyModeCoordinator.startLocalQuizSession(60L),
        )
        assertNull(LocalPolicyRuntime.currentPlan)
    }
}