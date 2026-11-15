package app.orchard.tv.policy.web

import android.os.PowerManager
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.quiz.QuizAccess
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.TvStore
import org.json.JSONObject
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
 * V31 control commands. The page only sends a desired state; these tests pin the server-side
 * state checks, confirmation lifecycle and requestId idempotency that keep a retry from ending
 * the same session twice.
 */
@RunWith(RobolectricTestRunner::class)
class ParentControlActionsTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
    private val editor = "test-editor-token"
    private lateinit var app: android.app.Application

    private fun rules() = LocalRules(
        weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        autoDailyLimitMinutes = 60,
        sessionLimitMinutes = 20,
        restMinutes = 5,
        allowedPackages = setOf(videoPackage),
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules()))
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        PolicyModeCoordinator.debugForceMode(PolicyMode.LOCAL)
        ParentControlActions.resetForTest()
        ParentSensitiveAuth.resetForTest()
        QuizAccess.onPinReset()
    }

    @After
    fun tearDown() {
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
        ParentControlActions.resetForTest()
        ParentSensitiveAuth.resetForTest()
        QuizAccess.onPinReset()
    }

    private fun body(action: String, requestId: String, block: JSONObject.() -> Unit = {}): JSONObject =
        JSONObject()
            .put("action", action)
            .put("requestId", requestId)
            .apply(block)

    private fun handle(
        body: JSONObject,
        nowMs: Long = System.currentTimeMillis(),
        sensitiveToken: String = "",
    ): ParentControlActions.Outcome =
        ParentControlActions.handle(body, editor, nowMs, sensitiveToken)

    /** Issues a real proof through the same verification path the web endpoint uses. */
    private fun takeOverToken(nowMs: Long = System.currentTimeMillis()): String {
        val result = ParentSensitiveAuth.verify(
            scopeValue = "TAKE_OVER",
            pin = pin,
            editorToken = editor,
            nowMs = nowMs,
        )
        assertTrue(result is ParentSensitiveAuth.VerifyResult.Ok)
        return (result as ParentSensitiveAuth.VerifyResult.Ok).token
    }

    /** Mirror the runtime change the way the real collector would before the API reads it. */
    private fun publishRuntime() {
        PolicyModeCoordinator.debugForceMode(PolicyMode.LOCAL)
    }

    private fun startTimer(seconds: Long = 10 * 60L) {
        assertEquals(SessionStartResult.Started, LocalPolicyRuntime.startManualSession(seconds))
        publishRuntime()
        assertEquals(SessionState.ACTIVE, PolicyModeCoordinator.state.value.sessionState)
    }

    @Test
    fun requestValidationRejectsUnknownActionsAndBadIds() {
        val unknown = handle(body("REBOOT", "0123456789abcdef"))
        assertFalse(unknown.ok)
        assertEquals("INVALID_ACTION", unknown.code)

        val shortId = handle(body("TAKE_OVER", "short"))
        assertFalse(shortId.ok)
        assertEquals("INVALID_REQUEST_ID", shortId.code)
    }

    @Test
    fun oneRequestIdCannotBeReusedForAnotherAction() {
        val requestId = "request-mismatch-0001"
        assertEquals(
            "TAKE_OVER_APPLIED",
            handle(body("TAKE_OVER", requestId), sensitiveToken = takeOverToken()).code,
        )

        val other = handle(body("STOP_TIMER", requestId))
        assertFalse(other.ok)
        assertEquals("REQUEST_ID_MISMATCH", other.code)
    }

    @Test
    fun takeOverDuringTimingRequiresConfirmAndSkipsRest() {
        startTimer()
        val requestId = "take-over-timer-0001"
        val sensitive = takeOverToken()
        val first = handle(body("TAKE_OVER", requestId), sensitiveToken = sensitive)

        assertEquals("CONFIRM_REQUIRED", first.result)
        assertEquals("TAKE_OVER_TIMER", first.confirm?.reason)
        assertNotNull(first.confirm?.token)
        assertEquals(SessionState.ACTIVE, LocalPolicyRuntime.state.value.sessionState)

        // The confirmation challenge must not burn the single-use proof.
        val applied = handle(
            body("TAKE_OVER", requestId) {
                put("confirmToken", first.confirm!!.token)
            },
            sensitiveToken = sensitive,
        )
        assertEquals("TAKE_OVER_APPLIED", applied.code)
        assertEquals(GuardStatus.PARENT_OVERRIDE, LocalPolicyRuntime.state.value.guardState)
        assertEquals(SessionState.IDLE, LocalPolicyRuntime.state.value.sessionState)
        assertEquals(0L, LocalPolicyRuntime.state.value.restRemainingSeconds)
        assertEquals(0L, LocalPolicyRuntime.state.value.parentUnlockRemainingSeconds)

        // A retry of the same command returns the cached APPLIED and must not refresh the
        // override deadline or start another session/rest.
        val retry = handle(body("TAKE_OVER", requestId))
        assertEquals("TAKE_OVER_APPLIED", retry.code)
        assertEquals(0L, LocalPolicyRuntime.state.value.parentUnlockRemainingSeconds)
        assertEquals(SessionState.IDLE, LocalPolicyRuntime.state.value.sessionState)

        // A new command is a real idempotent NOOP, not another execution.
        val noop = handle(body("TAKE_OVER", "take-over-timer-0002"))
        assertEquals("TAKE_OVER_NOOP", noop.code)
        assertEquals(0L, LocalPolicyRuntime.state.value.parentUnlockRemainingSeconds)
    }

    @Test
    fun enterChildModeOnlyActsFromParentOverride() {
        val before = handle(body("ENTER_CHILD_MODE", "enter-child-0001"))
        assertEquals("ENTER_CHILD_NOOP", before.code)
        assertEquals(GuardStatus.ENFORCING, LocalPolicyRuntime.state.value.guardState)

        assertEquals(
            "TAKE_OVER_APPLIED",
            handle(
                body("TAKE_OVER", "enter-child-0002"),
                sensitiveToken = takeOverToken(),
            ).code,
        )
        assertEquals(
            "ENTER_CHILD_APPLIED",
            handle(body("ENTER_CHILD_MODE", "enter-child-0003")).code,
        )
        assertEquals(GuardStatus.ENFORCING, LocalPolicyRuntime.state.value.guardState)

        assertEquals(
            "ENTER_CHILD_NOOP",
            handle(body("ENTER_CHILD_MODE", "enter-child-0004")).code,
        )
    }

    @Test
    fun stopTimerRequiresConfirmAndEndsOnce() {
        startTimer()
        val requestId = "stop-timer-request-01"
        val first = handle(body("STOP_TIMER", requestId))
        assertEquals("CONFIRM_REQUIRED", first.result)
        assertEquals("END_TIMER", first.confirm?.reason)
        assertEquals(SessionState.ACTIVE, LocalPolicyRuntime.state.value.sessionState)

        val applied = handle(
            body("STOP_TIMER", requestId) {
                put("confirmToken", first.confirm!!.token)
            },
        )
        assertEquals("STOP_TIMER_APPLIED", applied.code)
        assertEquals(SessionState.IDLE, LocalPolicyRuntime.state.value.sessionState)

        val retry = handle(body("STOP_TIMER", requestId))
        assertEquals("STOP_TIMER_APPLIED", retry.code)
        assertEquals(SessionState.IDLE, LocalPolicyRuntime.state.value.sessionState)

        val noop = handle(body("STOP_TIMER", "stop-timer-request-02"))
        assertEquals("STOP_TIMER_NOOP", noop.code)
    }

    @Test
    fun confirmTokenDoesNotSurviveTheTimerEnding() {
        startTimer()
        val requestId = "stale-confirm-0001"
        val confirmation = handle(body("STOP_TIMER", requestId)).confirm
        assertNotNull(confirmation)

        // The timer ends before the parent confirms. The old popup must not start another rest.
        assertTrue(LocalPolicyRuntime.endSessionByUser())
        publishRuntime()
        assertEquals(SessionState.IDLE, LocalPolicyRuntime.state.value.sessionState)

        val stale = handle(
            body("STOP_TIMER", requestId) {
                put("confirmToken", confirmation!!.token)
            },
        )
        assertEquals("STALE_CONFIRM", stale.code)
        assertEquals(SessionState.IDLE, LocalPolicyRuntime.state.value.sessionState)
    }

    @Test
    fun expiredConfirmTokenIsRejected() {
        startTimer()
        val now = System.currentTimeMillis()
        val requestId = "expired-confirm-0001"
        val confirmation = handle(body("STOP_TIMER", requestId), now).confirm
        assertNotNull(confirmation)
        assertTrue(confirmation!!.expiresAtMs > now)

        val expired = handle(
            body("STOP_TIMER", requestId) {
                put("confirmToken", confirmation.token)
            },
            now + 61_000L,
        )
        assertEquals("CONFIRM_EXPIRED", expired.code)
        assertEquals(SessionState.ACTIVE, LocalPolicyRuntime.state.value.sessionState)
    }

    @Test
    fun temporaryExitRequiresASingleUseSensitiveProof() {
        val missing = handle(body("TAKE_OVER", "take-over-missing-0001"))
        assertFalse(missing.ok)
        assertEquals("SENSITIVE_REQUIRED", missing.code)
        assertEquals(GuardStatus.ENFORCING, LocalPolicyRuntime.state.value.guardState)

        val now = System.currentTimeMillis()
        val token = takeOverToken(now)
        assertEquals(
            "TAKE_OVER_APPLIED",
            handle(body("TAKE_OVER", "take-over-proof-0001"), now, token).code,
        )

        // Re-enter child mode, then try the same proof again. It was consumed by the first
        // successful command and must not leave the guard a second time.
        assertEquals(
            "ENTER_CHILD_APPLIED",
            handle(body("ENTER_CHILD_MODE", "enter-after-proof-0001"), now).code,
        )
        val reused = handle(
            body("TAKE_OVER", "take-over-proof-0002"),
            now,
            token,
        )
        assertFalse(reused.ok)
        assertEquals("SENSITIVE_EXPIRED", reused.code)
        assertEquals(GuardStatus.ENFORCING, LocalPolicyRuntime.state.value.guardState)
    }

    @Test
    fun expiredTemporaryExitProofIsRejectedBeforeTouchingTheGuard() {
        val now = System.currentTimeMillis()
        val token = takeOverToken(now)
        val expired = handle(
            body("TAKE_OVER", "take-over-expired-0001"),
            now + 61_000L,
            token,
        )
        assertFalse(expired.ok)
        assertEquals("SENSITIVE_EXPIRED", expired.code)
        assertEquals(GuardStatus.ENFORCING, LocalPolicyRuntime.state.value.guardState)
    }

    @Test
    fun alreadyInParentOverrideNeedsNoProof() {
        assertEquals(
            "TAKE_OVER_APPLIED",
            handle(
                body("TAKE_OVER", "take-over-first-0001"),
                sensitiveToken = takeOverToken(),
            ).code,
        )
        val noop = handle(body("TAKE_OVER", "take-over-second-0001"))
        assertEquals("TAKE_OVER_NOOP", noop.code)
    }

    @Test
    fun statusReportsTheThreeAlwaysOnActionsAndTheGatedManualStart() {
        val status = ParentControlActions.statusJson()
        assertTrue(status.getBoolean("childModeOn"))
        assertEquals("ENFORCING", status.getString("guardState"))
        assertEquals("IDLE", status.getString("sessionState"))

        val actions = status.getJSONObject("actions")
        assertTrue(actions.getJSONObject("takeOver").getBoolean("enabled"))
        assertFalse(actions.getJSONObject("takeOver").getBoolean("requiresConfirm"))
        assertFalse(actions.getJSONObject("enterChildMode").getBoolean("enabled"))
        assertEquals(
            "ALREADY_CHILD_MODE",
            actions.getJSONObject("enterChildMode").getString("reason"),
        )
        assertFalse(actions.getJSONObject("stopTimer").getBoolean("enabled"))
        assertTrue(actions.getJSONObject("stopTimer").getBoolean("requiresConfirm"))
        assertEquals("NO_TIMER", actions.getJSONObject("stopTimer").getString("reason"))
        assertTrue(actions.getJSONObject("startManual").has("reason"))

        assertTrue(status.getJSONObject("timer").has("remainingSeconds"))
        assertTrue(status.getJSONObject("quiz").has("roundActive"))
        assertTrue(status.getJSONObject("manualStart").has("minMinutes"))
    }

    @Test
    fun manualStartValidatesDurationBeforeTouchingTheRuntime() {
        val invalid = handle(
            body("START_MANUAL", "manual-invalid-duration-1") {
                put("pin", pin)
                put("minutes", 7)
            },
        )
        assertFalse(invalid.ok)
        assertEquals("INVALID_DURATION", invalid.code)
        assertEquals(SessionState.IDLE, LocalPolicyRuntime.state.value.sessionState)

        val shortPin = handle(
            body("START_MANUAL", "manual-invalid-pin-0001") {
                put("pin", "123")
                put("minutes", 20)
            },
        )
        assertFalse(shortPin.ok)
        assertEquals("WRONG_PIN", shortPin.code)
    }

    @Test
    fun lockedPinLimiterBlocksManualStart() {
        QuizAccess.lockSensitiveActionOut()
        val locked = handle(
            body("START_MANUAL", "manual-locked-0001") {
                put("pin", pin)
                put("minutes", 20)
            },
        )
        assertFalse(locked.ok)
        assertEquals("PIN_LOCKED", locked.code)
        assertTrue(locked.retryAfterSeconds > 0L)
        assertNull(locked.confirm)
    }
}
