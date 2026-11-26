package app.orchard.tv.policy.web

import android.os.PowerManager
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.quiz.QuizAccess
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.TvStore
import org.junit.After
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
 * V32 sensitive PIN proofs. The page login already proves the PIN, so these tests focus on
 * the narrow extra layer: scope and editor binding, expiry, single use, the default-PIN
 * rejection and the limiter shared with the single timer.
 */
@RunWith(RobolectricTestRunner::class)
class ParentSensitiveAuthTest {
    private val pin = "246810"
    private val editor = "test-editor-token"
    private lateinit var app: android.app.Application

    private fun rules() = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekdayLimitMinutes = 60,
            weekendLimitMinutes = 60,
        ),
        sessionLimitMinutes = 20,
        restMinutes = 5,
        allowedPackages = setOf("com.example.video"),
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
        ParentSensitiveAuth.resetForTest()
        QuizAccess.onPinReset()
    }

    @After
    fun tearDown() {
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
        ParentSensitiveAuth.resetForTest()
        QuizAccess.onPinReset()
    }

    private fun verify(
        scope: String = "USER_EDIT",
        pinValue: String = pin,
        editorToken: String = editor,
        nowMs: Long = System.currentTimeMillis(),
    ): ParentSensitiveAuth.VerifyResult =
        ParentSensitiveAuth.verify(scope, pinValue, editorToken, nowMs)

    private fun token(result: ParentSensitiveAuth.VerifyResult): String {
        assertTrue(result is ParentSensitiveAuth.VerifyResult.Ok)
        return (result as ParentSensitiveAuth.VerifyResult.Ok).token
    }

    private fun failure(result: ParentSensitiveAuth.VerifyResult): ParentSensitiveAuth.VerifyResult.Failed {
        assertTrue(result is ParentSensitiveAuth.VerifyResult.Failed)
        return result as ParentSensitiveAuth.VerifyResult.Failed
    }

    @Test
    fun correctPinIssuesAScopedUserEditProofThatCanSaveRepeatedly() {
        val now = System.currentTimeMillis()
        val result = verify(nowMs = now) as ParentSensitiveAuth.VerifyResult.Ok
        assertEquals(ParentSensitiveAuth.Scope.USER_EDIT, result.scope)
        assertEquals(now + 5 * 60_000L, result.expiresAtMs)

        assertEquals(
            ParentSensitiveAuth.Status.OK,
            ParentSensitiveAuth.check(result.token, ParentSensitiveAuth.Scope.USER_EDIT, editor, now),
        )
        assertEquals(
            ParentSensitiveAuth.Status.OK,
            ParentSensitiveAuth.check(
                result.token,
                ParentSensitiveAuth.Scope.USER_EDIT,
                editor,
                now + 60_000L,
            ),
        )
        // A proof for one scope is not a proof for the other.
        assertEquals(
            ParentSensitiveAuth.Status.SCOPE,
            ParentSensitiveAuth.check(result.token, ParentSensitiveAuth.Scope.TAKE_OVER, editor, now),
        )
    }

    @Test
    fun expiredProofIsRejected() {
        val now = System.currentTimeMillis()
        val token = token(verify("TAKE_OVER", nowMs = now))
        assertEquals(
            ParentSensitiveAuth.Status.OK,
            ParentSensitiveAuth.check(token, ParentSensitiveAuth.Scope.TAKE_OVER, editor, now),
        )
        assertEquals(
            ParentSensitiveAuth.Status.EXPIRED,
            ParentSensitiveAuth.check(
                token,
                ParentSensitiveAuth.Scope.TAKE_OVER,
                editor,
                now + 61_000L,
            ),
        )
    }

    @Test
    fun proofIsBoundToTheEditorThatRequestedIt() {
        val now = System.currentTimeMillis()
        val token = token(verify(nowMs = now))
        assertEquals(
            ParentSensitiveAuth.Status.EXPIRED,
            ParentSensitiveAuth.check(
                token,
                ParentSensitiveAuth.Scope.USER_EDIT,
                "another-editor-token",
                now,
            ),
        )
    }

    @Test
    fun consumeMakesATakeOverProofSingleUse() {
        val now = System.currentTimeMillis()
        val token = token(verify("TAKE_OVER", nowMs = now))
        assertEquals(
            ParentSensitiveAuth.Status.OK,
            ParentSensitiveAuth.consume(
                token,
                ParentSensitiveAuth.Scope.TAKE_OVER,
                editor,
                now,
            ),
        )
        assertEquals(
            ParentSensitiveAuth.Status.EXPIRED,
            ParentSensitiveAuth.check(
                token,
                ParentSensitiveAuth.Scope.TAKE_OVER,
                editor,
                now,
            ),
        )
    }

    @Test
    fun userConfigProofIsSingleUseAndExpiresAfterSixtySeconds() {
        val now = System.currentTimeMillis()
        val token = token(verify("USER_CONFIG", nowMs = now))
        assertEquals(
            ParentSensitiveAuth.Status.OK,
            ParentSensitiveAuth.check(
                token,
                ParentSensitiveAuth.Scope.USER_CONFIG,
                editor,
                now,
            ),
        )
        assertEquals(
            ParentSensitiveAuth.Status.SCOPE,
            ParentSensitiveAuth.check(token, ParentSensitiveAuth.Scope.USER_EDIT, editor, now),
        )
        assertEquals(
            ParentSensitiveAuth.Status.OK,
            ParentSensitiveAuth.consume(
                token,
                ParentSensitiveAuth.Scope.USER_CONFIG,
                editor,
                now,
            ),
        )
        assertEquals(
            ParentSensitiveAuth.Status.EXPIRED,
            ParentSensitiveAuth.check(
                token,
                ParentSensitiveAuth.Scope.USER_CONFIG,
                editor,
                now,
            ),
        )

        val second = token(verify("USER_CONFIG", nowMs = now))
        assertEquals(
            ParentSensitiveAuth.Status.EXPIRED,
            ParentSensitiveAuth.check(
                second,
                ParentSensitiveAuth.Scope.USER_CONFIG,
                editor,
                now + 61_000L,
            ),
        )
    }

    @Test
    fun releaseDropsOnlyTheCallersProof() {
        val now = System.currentTimeMillis()
        val token = token(verify(nowMs = now))
        ParentSensitiveAuth.release(token, "another-editor-token")
        assertEquals(
            ParentSensitiveAuth.Status.OK,
            ParentSensitiveAuth.check(token, ParentSensitiveAuth.Scope.USER_EDIT, editor, now),
        )

        ParentSensitiveAuth.release(token, editor)
        assertEquals(
            ParentSensitiveAuth.Status.EXPIRED,
            ParentSensitiveAuth.check(token, ParentSensitiveAuth.Scope.USER_EDIT, editor, now),
        )
    }

    @Test
    fun invalidScopeAndMalformedPinAreRejected() {
        assertEquals("INVALID_SCOPE", failure(verify("UNKNOWN")).code)
        assertEquals("WRONG_PIN", failure(verify(pinValue = "123")).code)
        assertEquals(0L, QuizAccess.sensitiveActionLockedSeconds())
    }

    @Test
    fun defaultPinIsRejectedWithoutIssuingAProofOrCountingAFailure() {
        LocalPolicyRuntime.setPin("123456")
        val now = System.currentTimeMillis()
        // The correct PIN is still the built-in value, so it must not become a proof.
        val result = failure(verify(pinValue = "123456", nowMs = now))
        assertEquals("DEFAULT_PIN", result.code)
        assertEquals(0L, QuizAccess.sensitiveActionLockedSeconds())
        assertEquals(
            ParentSensitiveAuth.Status.REQUIRED,
            ParentSensitiveAuth.check(null, ParentSensitiveAuth.Scope.USER_EDIT, editor, now),
        )
    }

    @Test
    fun fiveWrongPinsLockTheSharedSensitiveActionsForTenMinutes() {
        val now = System.currentTimeMillis()
        repeat(4) {
            assertEquals("WRONG_PIN", failure(verify(pinValue = "999999", nowMs = now)).code)
        }
        val locked = failure(verify(pinValue = "999999", nowMs = now))
        assertEquals("PIN_LOCKED", locked.code)
        assertTrue(locked.retryAfterSeconds > 0L)

        // A correct PIN cannot bypass an active lock, and the single-timer action shares it.
        assertEquals("PIN_LOCKED", failure(verify(nowMs = now)).code)
        assertTrue(QuizAccess.sensitiveActionLockedSeconds(now) > 0L)
    }

    @Test
    fun aSuccessfulVerificationClearsEarlierFailures() {
        val now = System.currentTimeMillis()
        repeat(4) {
            assertEquals("WRONG_PIN", failure(verify(pinValue = "999999", nowMs = now)).code)
        }
        assertTrue(verify(nowMs = now) is ParentSensitiveAuth.VerifyResult.Ok)
        assertEquals(0L, QuizAccess.sensitiveActionLockedSeconds(now))

        repeat(4) {
            assertEquals("WRONG_PIN", failure(verify(pinValue = "999999", nowMs = now)).code)
        }
        assertEquals(0L, QuizAccess.sensitiveActionLockedSeconds(now))
    }

    @Test
    fun invalidateDropsEveryProof() {
        val now = System.currentTimeMillis()
        val token = token(verify(nowMs = now))
        ParentSensitiveAuth.invalidate()
        assertEquals(
            ParentSensitiveAuth.Status.EXPIRED,
            ParentSensitiveAuth.check(token, ParentSensitiveAuth.Scope.USER_EDIT, editor, now),
        )
    }

    @Test
    fun missingProofIsReportedAsRequired() {
        assertEquals(
            ParentSensitiveAuth.Status.REQUIRED,
            ParentSensitiveAuth.check("", ParentSensitiveAuth.Scope.USER_EDIT, editor),
        )
        assertFalse(
            ParentSensitiveAuth.check(null, ParentSensitiveAuth.Scope.TAKE_OVER, editor) ==
                ParentSensitiveAuth.Status.OK,
        )
    }
}
