package app.orchard.tv.policy

import android.os.PowerManager
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.RejectReason
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

/**
 * The web parent page may reproduce the lock-screen PIN and start one manual session. It must
 * never end a running session, leave study mode, or unlock a dark screen as a side effect.
 */
@RunWith(RobolectricTestRunner::class)
class LocalPolicyRuntimeWebUnlockTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
    private lateinit var app: android.app.Application

    private fun rules() = LocalRules(
        weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        autoDailyLimitMinutes = 60,
        sessionLimitMinutes = 20,
        restMinutes = 0,
        allowedPackages = setOf(videoPackage),
    )

    private fun state() = LocalPolicyRuntime.state.value

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules()))
        LocalPolicyRuntime.setSource(PolicySource.HOSTED)
        LocalPolicyRuntime.activate()
    }

    private fun screenInteractive(interactive: Boolean) {
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(interactive)
    }

    @Test
    fun lockScreenUnlockMirrorsThePinWithoutStartingATimer() {
        assertEquals(GuardStatus.ENFORCING, state().guardState)
        assertEquals(SessionState.IDLE, state().sessionState)
        assertTrue(LocalPolicyRuntime.canBeginParentOverrideFromLockScreen())

        assertTrue(LocalPolicyRuntime.tryBeginParentOverrideFromLockScreen())
        assertEquals(GuardStatus.PARENT_OVERRIDE, state().guardState)
        assertEquals(SessionState.IDLE, state().sessionState)
        assertFalse(LocalPolicyRuntime.isActive)
        assertTrue(LocalPolicyRuntime.isTemporaryUnlocked())
    }

    @Test
    fun activeSessionIsNeverEndedFromTheWeb() {
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.startManualSession(10 * 60L))
        assertEquals(SessionState.ACTIVE, state().sessionState)

        assertFalse(LocalPolicyRuntime.canBeginParentOverrideFromLockScreen())
        assertFalse(LocalPolicyRuntime.tryBeginParentOverrideFromLockScreen())
        assertEquals(GuardStatus.ENFORCING, state().guardState)
        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertTrue(LocalPolicyRuntime.isActive)
    }

    @Test
    fun studyModeIsNeverLeftFromTheWeb() {
        assertTrue(LocalPolicyRuntime.enterStudyMode())
        assertEquals(GuardStatus.STUDY_MODE, state().guardState)

        assertFalse(LocalPolicyRuntime.canBeginParentOverrideFromLockScreen())
        assertFalse(LocalPolicyRuntime.tryBeginParentOverrideFromLockScreen())
        assertEquals(
            SessionStartResult.Rejected(RejectReason.BUSY),
            LocalPolicyRuntime.startManualSession(10 * 60L),
        )
        assertEquals(GuardStatus.STUDY_MODE, state().guardState)
        assertEquals(SessionState.IDLE, state().sessionState)
        assertTrue(LocalPolicyRuntime.isStudyMode())
    }

    @Test
    fun darkScreenIsNotUnlockedRemotely() {
        screenInteractive(false)

        assertFalse(LocalPolicyRuntime.canBeginParentOverrideFromLockScreen())
        assertFalse(LocalPolicyRuntime.tryBeginParentOverrideFromLockScreen())
        assertEquals(GuardStatus.ENFORCING, state().guardState)
        assertFalse(LocalPolicyRuntime.isTemporaryUnlocked())
    }

    @Test
    fun setupRequiredRefusesTheWebUnlock() {
        LocalPolicyRuntime.debugResetForTest()
        LocalPolicyRuntime.initialize(app)
        assertTrue(LocalPolicyRuntime.isSetupRequired)

        assertFalse(LocalPolicyRuntime.canBeginParentOverrideFromLockScreen())
        assertFalse(LocalPolicyRuntime.tryBeginParentOverrideFromLockScreen())
        assertFalse(LocalPolicyRuntime.isTemporaryUnlocked())
    }
}
