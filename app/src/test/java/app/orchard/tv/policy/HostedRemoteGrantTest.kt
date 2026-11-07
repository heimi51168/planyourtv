package app.orchard.tv.policy

import android.os.PowerManager
import com.planyourtv.policy.AccessMode
import com.planyourtv.policy.Activation
import com.planyourtv.policy.LocalPolicyStore
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyConfig
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.RuleUsageEvent
import com.planyourtv.policy.RuleUsageEventType
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
import java.time.LocalDateTime

/**
 * The hosted remote grant is two-phase: MQTT and the REST poll only arm a pending gate, and the
 * timer starts only when the child confirms on the lock screen.
 */
@RunWith(RobolectricTestRunner::class)
class HostedRemoteGrantTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
    private var epoch = 0L

    private fun rules() = LocalRules(
        weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        restMinutes = 0,
        allowedPackages = setOf(videoPackage),
    )

    private fun grant(
        recordId: Int,
        totalSeconds: Long = 600L,
        costSeconds: Long = 0L,
        windowSeconds: Long? = 60 * 60L,
        userIconUrl: String = "https://example.test/icon/61.png",
    ) = PolicyConfig(
        recordId = recordId,
        totalSeconds = totalSeconds,
        costSeconds = costSeconds,
        status = 1,
        userName = "小明",
        userIconUrl = userIconUrl,
        validWindowSeconds = windowSeconds,
        validUntilLabel = "20:00",
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
        epoch = LocalPolicyRuntime.state.value.policyEpoch
    }

    private fun state() = LocalPolicyRuntime.state.value

    @Test
    fun mqttDispatchOnlyArmsUntilChildConfirms() {
        val armed = LocalPolicyRuntime.startRemoteManualSession(
            durationSeconds = 600L,
            expectedEpoch = epoch,
            allowedPackages = null,
            recordId = 41,
        )
        assertEquals(SessionStartResult.Pending, armed)
        assertEquals(SessionState.PENDING, state().sessionState)
        assertEquals(600L, state().remainingSeconds)
        assertNull(LocalPolicyRuntime.currentPlan)
        assertFalse(LocalPolicyRuntime.isActive)

        // The same command (QoS redelivery or uniapp retry) must not reset or duplicate the gate.
        val retried = LocalPolicyRuntime.startRemoteManualSession(
            durationSeconds = 600L,
            expectedEpoch = epoch,
            allowedPackages = null,
            recordId = 41,
        )
        assertEquals(SessionStartResult.Pending, retried)
        assertEquals(SessionState.PENDING, state().sessionState)
        assertEquals(600L, state().remainingSeconds)

        // A different duration while the gate is armed is a real conflict.
        val conflict = LocalPolicyRuntime.startRemoteManualSession(
            durationSeconds = 900L,
            expectedEpoch = epoch,
            allowedPackages = null,
            recordId = 42,
        )
        assertEquals(SessionStartResult.Rejected(RejectReason.BUSY), conflict)

        val started = LocalPolicyRuntime.confirmRemoteManualSession()
        assertEquals(SessionStartResult.Started, started)
        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertTrue(LocalPolicyRuntime.isActive)
        val plan = requireNotNull(LocalPolicyRuntime.currentPlan)
        assertEquals(600L, plan.budgetSeconds)
        assertEquals(AccessMode.APPS, plan.accessMode)
        assertEquals(setOf(videoPackage), plan.allowedPackages)

        // Confirming twice must not create a second session or consume more budget.
        assertEquals(
            SessionStartResult.Rejected(RejectReason.INVALID_COMMAND),
            LocalPolicyRuntime.confirmRemoteManualSession(),
        )
        assertEquals(SessionState.ACTIVE, state().sessionState)
    }

    @Test
    fun restGrantArmsTheSameGateAndIsNeverReplayedAfterStart() {
        val armed = LocalPolicyRuntime.armRemoteManualSessionFromGrant(
            grant(recordId = 77, totalSeconds = 900L),
        )
        assertEquals(SessionStartResult.Pending, armed)
        assertEquals(SessionState.PENDING, state().sessionState)
        assertEquals(900L, state().remainingSeconds)

        // The poll repeats the same record; it must merge, not arm a second gate.
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(
                grant(recordId = 77, totalSeconds = 900L, windowSeconds = 3590L),
            ),
        )
        assertEquals(900L, state().remainingSeconds)

        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.confirmRemoteManualSession())
        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertEquals(900L, LocalPolicyRuntime.currentPlan?.budgetSeconds)
        assertTrue(77 in LocalPolicyStore.settledGrantRecordIds())

        // The grant may still report costTime=0 while the segment runs; it must not re-arm.
        assertEquals(
            SessionStartResult.Rejected(RejectReason.EXPIRED),
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 77)),
        )
        assertTrue(LocalPolicyRuntime.endSessionByUser())
        assertEquals(SessionState.IDLE, state().sessionState)
        assertEquals(
            SessionStartResult.Rejected(RejectReason.EXPIRED),
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 77)),
        )
    }

    @Test
    fun directMqttArmSurvivesAnEmptyGrantPoll() {
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.startRemoteManualSession(
                durationSeconds = 600L,
                expectedEpoch = epoch,
                allowedPackages = null,
                recordId = 0,
            ),
        )

        // Direct manual dispatch has no exchange record; a null REST poll must not cancel it.
        LocalPolicyRuntime.onHostedGrantRemoved()
        assertEquals(SessionState.PENDING, state().sessionState)
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.confirmRemoteManualSession())
        assertEquals(SessionState.ACTIVE, state().sessionState)
    }

    @Test
    fun restGrantCompletesAnMqttArmAndCanBeCanceledByItsRecord() {
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.startRemoteManualSession(
                durationSeconds = 600L,
                expectedEpoch = epoch,
                allowedPackages = null,
                recordId = 0,
            ),
        )

        // The exchange record arrives over the poll and attaches to the already-armed gate.
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(
                grant(recordId = 91, totalSeconds = 600L).copy(
                    accessMode = AccessMode.APPS,
                    allowedPackages = setOf(videoPackage),
                ),
            ),
        )
        assertEquals("20:00", state().validUntil)

        // The attached record now owns the gate, so a phone cancel can match it.
        assertTrue(LocalPolicyRuntime.cancelRemotePending(91))
        assertEquals(SessionState.IDLE, state().sessionState)
        assertTrue(91 in LocalPolicyStore.settledGrantRecordIds())
    }

    @Test
    fun grantAlreadyBilledElsewhereIsNotArmed() {
        assertEquals(
            SessionStartResult.Rejected(RejectReason.INVALID_COMMAND),
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(
                grant(recordId = 88, costSeconds = 60L),
            ),
        )
        assertEquals(SessionState.IDLE, state().sessionState)
        assertNull(LocalPolicyRuntime.currentPlan)
    }

    @Test
    fun cancelAndStopOnlyMatchTheirOwnRecord() {
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 51)),
        )

        assertFalse(LocalPolicyRuntime.cancelRemotePending(99))
        assertEquals(SessionState.PENDING, state().sessionState)

        assertTrue(LocalPolicyRuntime.cancelRemotePending(51))
        assertEquals(SessionState.IDLE, state().sessionState)
        assertTrue(51 in LocalPolicyStore.settledGrantRecordIds())

        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.startRemoteManualSession(
                durationSeconds = 300L,
                expectedEpoch = epoch,
                allowedPackages = null,
                recordId = 52,
            ),
        )
        assertFalse(LocalPolicyRuntime.stopRemoteSessionByParent(commandRecordId = 51))
        assertTrue(LocalPolicyRuntime.stopRemoteSessionByParent(commandRecordId = 52))
        assertEquals(SessionState.IDLE, state().sessionState)
        assertTrue(52 in LocalPolicyStore.settledGrantRecordIds())
    }

    @Test
    fun grantRemovalCancelsTheArmedGate() {
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 61)),
        )

        LocalPolicyRuntime.onHostedGrantRemoved()
        assertEquals(SessionState.IDLE, state().sessionState)
        assertTrue(61 in LocalPolicyStore.settledGrantRecordIds())
        assertEquals(
            SessionStartResult.Rejected(RejectReason.INVALID_COMMAND),
            LocalPolicyRuntime.confirmRemoteManualSession(),
        )
    }

    @Test
    fun restGrantPublishesRecipientIdentityWithThePendingGate() {
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 63)),
        )

        // A0: the REST snapshot is the only source of the recipient identity. It must be
        // visible in the same published state as the pending gate (and its button/voice).
        assertEquals(SessionState.PENDING, state().sessionState)
        assertEquals("小明", state().userName)
        assertEquals("https://example.test/icon/61.png", state().userIconUrl)
    }

    @Test
    fun grantRemovalStopsAnActiveRecordBackedSession() {
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 64)),
        )
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.confirmRemoteManualSession())
        assertEquals(SessionState.ACTIVE, state().sessionState)

        // MQTT may be down; a snapshot that no longer carries the started record is the stop
        // fallback for a remote hosted segment.
        LocalPolicyRuntime.onHostedGrantRemoved()
        assertEquals(SessionState.IDLE, state().sessionState)
        assertNull(LocalPolicyRuntime.currentPlan)
    }

    @Test
    fun grantRemovalLeavesARecordlessDirectSessionRunning() {
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.startRemoteManualSession(
                durationSeconds = 600L,
                expectedEpoch = epoch,
                allowedPackages = null,
                recordId = 0,
            ),
        )
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.confirmRemoteManualSession())
        assertEquals(SessionState.ACTIVE, state().sessionState)

        // PTV has no exchange record, so an empty poll must not stop the segment.
        LocalPolicyRuntime.onHostedGrantRemoved()
        assertEquals(SessionState.ACTIVE, state().sessionState)
        assertNotNull(LocalPolicyRuntime.currentPlan)
    }

    @Test
    fun aGrantSeenDuringAnActiveSegmentCannotArmAgainLater() {
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 71)),
        )
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.confirmRemoteManualSession())

        // This is the defensive path for a grant that arrived while the child was already
        // watching; it must be consumed with the running segment, not saved for later.
        assertEquals(
            SessionStartResult.Rejected(RejectReason.BUSY),
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 72)),
        )
        assertTrue(72 in LocalPolicyStore.settledGrantRecordIds())
        assertTrue(LocalPolicyRuntime.endSessionByUser())
        assertEquals(
            SessionStartResult.Rejected(RejectReason.EXPIRED),
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 72)),
        )
    }

    @Test
    fun confirmedGrantKeepsItsScopeWhenBaseRulesAllowMoreApps() {
        val musicPackage = "com.example.music"
        val twoApps = rules().copy(allowedPackages = setOf(videoPackage, musicPackage))
        assertTrue(LocalPolicyRuntime.setRulesWithVersion(twoApps, 1L))
        assertEquals(setOf(videoPackage, musicPackage), state().allowedPackages)
        // Applying rules refreshes the epoch, so the grant must carry the updated one.
        val updatedEpoch = state().policyEpoch

        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.startRemoteManualSession(
                durationSeconds = 300L,
                expectedEpoch = updatedEpoch,
                allowedPackages = setOf(videoPackage),
                recordId = 55,
            ),
        )
        assertEquals(setOf(videoPackage), state().allowedPackages)

        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.confirmRemoteManualSession())
        assertEquals(SessionState.ACTIVE, state().sessionState)
        // Regression: confirming used to republish the whole base entertainment list, so the
        // child saw every app even though the grant named only one.
        assertEquals(setOf(videoPackage), state().allowedPackages)
        assertEquals(setOf(videoPackage), LocalPolicyRuntime.currentPlan?.allowedPackages)

        assertTrue(LocalPolicyRuntime.endSessionByUser())
        assertEquals(SessionState.IDLE, state().sessionState)
        assertEquals(setOf(videoPackage, musicPackage), state().allowedPackages)
    }

    @Test
    fun localManualStartIsAcceptedInHostedRuleMode() {
        val events = mutableListOf<RuleUsageEvent>()
        LocalPolicyRuntime.setUsageSink { events += it }
        try {
            assertEquals(
                SessionStartResult.Started,
                LocalPolicyRuntime.startManualSession(20 * 60L),
            )
            assertEquals(SessionState.ACTIVE, state().sessionState)
            assertTrue(LocalPolicyRuntime.isActive)
            assertEquals(20 * 60L, LocalPolicyRuntime.currentPlan?.budgetSeconds)
            assertEquals(setOf(videoPackage), state().allowedPackages)
            assertTrue(events.any { it.type == RuleUsageEventType.START })

            // A hosted local start has no exchange record. Persisting its origin is what keeps
            // a raced grant unconsumed after the process restarts.
            assertEquals(true, LocalPolicyStore.loadSession()?.startedLocally)
        } finally {
            LocalPolicyRuntime.setUsageSink(null)
        }
    }

    @Test
    fun localManualStartIsStillRefusedWhenHostedActivationIsAuto() {
        assertTrue(LocalPolicyRuntime.setActivation(Activation.AUTO))
        assertEquals(
            SessionStartResult.Rejected(RejectReason.ACTIVATION_MISMATCH),
            LocalPolicyRuntime.startManualSession(20 * 60L),
        )
        assertNull(LocalPolicyRuntime.currentPlan)
    }

    @Test
    fun localManualStartStillHonorsTheConfiguredWindow() {
        // Pick a window that cannot contain the current minute so the check is deterministic.
        val minute = LocalDateTime.now().let { it.hour * 60 + it.minute }
        val start = when {
            minute < 60 -> 60
            minute < 13 * 60 -> minute + 60
            else -> 0
        }
        val window = LocalTimeWindow(start, start + 60)
        val closed = rules().copy(
            weekdayWindows = listOf(window),
            weekendWindows = listOf(window),
        )
        assertTrue(LocalPolicyRuntime.setRulesWithVersion(closed, 2L))
        assertEquals(
            SessionStartResult.Rejected(RejectReason.OUTSIDE_WINDOW),
            LocalPolicyRuntime.startManualSession(20 * 60L),
        )
        assertNull(LocalPolicyRuntime.currentPlan)
    }

    @Test
    fun grantSeenDuringALocalSessionIsArmedAfterTheLocalSessionEnds() {
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startManualSession(10 * 60L),
        )
        assertEquals(
            SessionStartResult.Rejected(RejectReason.BUSY),
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 73)),
        )
        // The running local segment owns no grant, so this arrival is a new authorization.
        // Consuming it here would lose the phone's request permanently.
        assertFalse(73 in LocalPolicyStore.settledGrantRecordIds())

        assertTrue(LocalPolicyRuntime.endSessionByUser())
        assertEquals(SessionState.IDLE, state().sessionState)
        assertEquals(
            SessionStartResult.Pending,
            LocalPolicyRuntime.armRemoteManualSessionFromGrant(grant(recordId = 73)),
        )
        assertEquals(SessionState.PENDING, state().sessionState)
    }

    @Test
    fun parentStopEndsAHostedLocalSession() {
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startManualSession(10 * 60L),
        )
        assertTrue(LocalPolicyRuntime.isActive)

        assertTrue(LocalPolicyRuntime.stopRemoteSessionByParent(commandRecordId = 123))
        assertFalse(LocalPolicyRuntime.isActive)
        assertNull(LocalPolicyRuntime.currentPlan)
        assertEquals(SessionState.IDLE, state().sessionState)
    }
}
