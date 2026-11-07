package app.orchard.tv.policy

import com.planyourtv.policy.AccessMode
import com.planyourtv.policy.Activation
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyConfig
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.RemoteHandoffState
import com.planyourtv.policy.RemotePolicy
import com.planyourtv.policy.RemotePolicyMode
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.TvStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Redemption mode uses the legacy cloud engine, but the lock screen keeps the fixed Study entry
 * in every mode. Study must therefore scope the desktop and the foreground gate on that engine
 * too, without touching the armed grant.
 */
@RunWith(RobolectricTestRunner::class)
class RedemptionStudyModeTest {
    private val studyApp = "com.example.study"
    private val videoApp = "com.example.video"

    private fun remote(study: Set<String> = setOf(studyApp)) = RemotePolicy(
        mode = RemotePolicyMode.REDEEM,
        handoffState = RemoteHandoffState.DONE,
        configVersion = 3,
        appliedVersion = 3,
        appliedMode = RemotePolicyMode.REDEEM,
        supportsUnifiedPolicy = true,
        rules = LocalRules(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            allowedPackages = setOf(videoApp),
            studyPackages = study,
        ),
        activation = Activation.MANUAL,
    )

    private fun grant(recordId: Int = 11, totalSeconds: Long = 300L) = PolicyConfig(
        recordId = recordId,
        totalSeconds = totalSeconds,
        costSeconds = 0L,
        status = 1,
        userName = "小明",
        validWindowSeconds = 60 * 60L,
        validUntilLabel = "20:00",
    )

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        PolicyRuntime.start(app, active = true)
        PolicyRuntime.setHostedRuleActive(false)
        PolicyRuntime.exitStudyMode()
        PolicyRuntime.debugUnbind()
        PolicyRuntime.onCloudBound()
        PolicyRuntime.onRemotePolicy(remote())
    }

    @Test
    fun studyScopeComesFromTheCloudListAndBlocksOtherApps() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(PolicyRuntime.state.value.studyModeAvailable)
        assertEquals(setOf(studyApp), PolicyRuntime.state.value.studyPackages)

        assertTrue(PolicyRuntime.enterStudyMode())
        val state = PolicyRuntime.state.value
        assertEquals(GuardStatus.STUDY_MODE, state.guardState)
        assertTrue(state.studyModeActive)
        assertEquals(AccessMode.APPS, state.accessMode)
        assertEquals(setOf(studyApp), state.allowedPackages)
        assertTrue(PolicyRuntime.allowsPackage(studyApp))
        assertFalse(PolicyRuntime.allowsPackage(videoApp))
        assertTrue(PolicyRuntime.shouldBlock(context, videoApp))
        assertFalse(PolicyRuntime.shouldBlock(context, studyApp))
        assertTrue(PolicyRuntime.isEnabled())
        assertFalse(PolicyRuntime.allowsSource("hdmi1"))

        PolicyRuntime.exitStudyMode()
        assertEquals(GuardStatus.ENFORCING, PolicyRuntime.state.value.guardState)
        assertFalse(PolicyRuntime.state.value.studyModeActive)
        assertTrue(PolicyRuntime.allowsPackage(videoApp))
        assertFalse(PolicyRuntime.shouldBlock(context, videoApp))
    }

    @Test
    fun anArmedGrantWaitsUnderStudyAndReturnsAfterIt() {
        PolicyRuntime.onCloudConfig(grant())
        assertEquals(SessionState.PENDING, PolicyRuntime.state.value.sessionState)

        assertTrue(PolicyRuntime.enterStudyMode())
        assertEquals(SessionState.PENDING, PolicyRuntime.state.value.sessionState)
        assertEquals(GuardStatus.STUDY_MODE, PolicyRuntime.state.value.guardState)

        PolicyRuntime.exitStudyMode()
        assertEquals(SessionState.PENDING, PolicyRuntime.state.value.sessionState)
        assertEquals(GuardStatus.ENFORCING, PolicyRuntime.state.value.guardState)
    }

    @Test
    fun anEmptyCloudStudyListDoesNotOpenStudy() {
        PolicyRuntime.onRemotePolicy(remote(emptySet()))
        assertFalse(PolicyRuntime.state.value.studyModeAvailable)
        assertFalse(PolicyRuntime.enterStudyMode())
        assertEquals(GuardStatus.ENFORCING, PolicyRuntime.state.value.guardState)
    }

    @Test
    fun screenOffLeavesStudyMode() {
        assertTrue(PolicyRuntime.enterStudyMode())
        PolicyRuntime.onScreenOff()
        assertEquals(GuardStatus.ENFORCING, PolicyRuntime.state.value.guardState)
        assertFalse(PolicyRuntime.isStudyMode())
    }
}
