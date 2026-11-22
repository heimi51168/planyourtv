package app.orchard.tv.policy.web

import android.os.PowerManager
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.UserConfigPinSettings
import app.orchard.tv.quiz.QuizAccess
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalDayGroup
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.TvStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekdayLimitMinutes = 60,
            weekendLimitMinutes = 60,
        ),
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
        UserConfigPinSettings.debugResetForTest()
        UserConfigPinSettings.install(app)
        // These cases exercise the protected path; the product default is now pinless.
        assertTrue(UserConfigPinSettings.setRequired(true))
    }

    @After
    fun tearDown() {
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
        ParentControlActions.resetForTest()
        ParentSensitiveAuth.resetForTest()
        QuizAccess.onPinReset()
        UserConfigPinSettings.debugResetForTest()
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

    private fun windowJson(start: Int = 0, end: Int = 24 * 60): JSONObject =
        JSONObject().put("start", start).put("end", end)

    private fun groupJson(
        days: List<Int>,
        studyLimit: Int? = null,
        entertainmentLimit: Int? = 60,
        studyWindows: List<JSONObject> = listOf(windowJson()),
        entertainmentWindows: List<JSONObject> = listOf(windowJson()),
    ): JSONObject = JSONObject()
        .put("days", JSONArray(days))
        .put("studyWindows", JSONArray(studyWindows))
        .put("studyDailyLimitMinutes", studyLimit ?: JSONObject.NULL)
        .put("entertainmentWindows", JSONArray(entertainmentWindows))
        .put("entertainmentDailyLimitMinutes", entertainmentLimit ?: JSONObject.NULL)

    private fun schemaFiveRules(
        groups: JSONArray,
        block: JSONObject.() -> Unit = {},
    ): JSONObject = JSONObject()
        .put("rulesContractVersion", ParentCenterActions.RULES_CONTRACT_VERSION)
        .put("dayGroups", groups)
        .put("sessionCapMinutes", 30)
        // V5 sends both direct names; the server refuses a draft where they disagree.
        .put("directSessionMinutes", 20)
        .put("sessionLimitMinutes", 20)
        .put("quizDailyLimitCount", 3)
        .put("studyRewardDailyLimitCount", 3)
        .put("directDailyFreeCount", 3)
        .put("studyEarnStudyMinutes", 10)
        .put("studyEarnRewardMinutes", 5)
        .put("restMinutes", 5)
        .put("sessionScreenOffBufferMinutes", 20)
        .put("allApps", false)
        .put("allowedPackages", JSONArray().put(videoPackage))
        .put("studyPackages", JSONArray())
        .put("unlimitedPackages", JSONArray())
        .put("studyEarnPackages", JSONArray())
        .apply(block)

    private fun saveRules(
        rules: JSONObject,
        expectedRevision: Long = LocalPolicyRuntime.rulesEpoch,
    ): ParentCenterActions.Outcome = ParentCenterActions.setRules(
        JSONObject()
            .put("expectedRulesRevision", expectedRevision)
            .put("rules", rules),
    )

    private val defaultGroups: JSONArray = JSONArray()
        .put(groupJson(listOf(1, 2, 3, 4, 5)))
        .put(groupJson(listOf(6, 7), entertainmentLimit = 120))

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
    fun takeOverDuringTimingAppliesImmediatelyAndSkipsRest() {
        startTimer()
        assertEquals(SessionState.ACTIVE, LocalPolicyRuntime.state.value.sessionState)

        // V48: the parent PIN is the confirmation. The status hint must not promise a second
        // dialog even while a timer is running.
        val timerStatus = ParentControlActions.statusJson()
        assertTrue(timerStatus.getJSONObject("timer").getBoolean("active"))
        assertFalse(
            timerStatus.getJSONObject("actions")
                .getJSONObject("takeOver")
                .getBoolean("requiresConfirm"),
        )

        val requestId = "take-over-timer-0001"
        val applied = handle(body("TAKE_OVER", requestId), sensitiveToken = takeOverToken())
        assertEquals("TAKE_OVER_APPLIED", applied.code)
        assertNull(applied.confirm)
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

        val timer = status.getJSONObject("timer")
        assertTrue(timer.has("remainingSeconds"))
        assertFalse(timer.getBoolean("outsidePlan"))
        assertTrue(status.getJSONObject("quiz").has("roundActive"))
        val manual = status.getJSONObject("manualStart")
        assertTrue(manual.has("minMinutes"))
        assertTrue(manual.getBoolean("requiresPin"))
        // V45: the timer sheet shows the configured scope, including the study reward only
        // while that route is enabled.
        val scope = manual.getJSONObject("scope")
        assertEquals(1, scope.getInt("entertainmentCount"))
        assertFalse(scope.getBoolean("entertainmentAll"))
        assertEquals(0, scope.getInt("unlimitedCount"))
        assertEquals(0, scope.getInt("studyRewardCount"))
        assertFalse(scope.getBoolean("studyRewardIncluded"))
    }

    @Test
    fun statusMarksAnOutOfPlanParentTimer() {
        assertEquals(
            SessionStartResult.Started,
            LocalPolicyRuntime.startManualSession(10 * 60L, outsidePlan = true),
        )
        publishRuntime()

        val status = ParentControlActions.statusJson()
        val timer = status.getJSONObject("timer")
        assertTrue(timer.getBoolean("active"))
        assertTrue(timer.getBoolean("outsidePlan"))
        val manual = status.getJSONObject("manualStart")
        assertEquals("SESSION_ACTIVE", manual.getString("reason"))
        assertFalse(manual.getBoolean("eligible"))
        assertTrue(manual.getBoolean("outsidePlan"))
        assertTrue(manual.getLong("remainingSeconds") > 0L)
    }

    @Test
    fun statusKeepsAnInPlanTimerOutOfTheManualAction() {
        startTimer()

        val status = ParentControlActions.statusJson()
        val timer = status.getJSONObject("timer")
        assertTrue(timer.getBoolean("active"))
        assertFalse(timer.getBoolean("outsidePlan"))
        // The system status still owns the plan countdown.
        assertTrue(timer.getLong("remainingSeconds") > 0L)

        // The out-of-plan action must not expose or display the plan timer's progress.
        val manual = status.getJSONObject("manualStart")
        assertEquals("SESSION_ACTIVE", manual.getString("reason"))
        assertFalse(manual.getBoolean("eligible"))
        assertFalse(manual.getBoolean("outsidePlan"))
        assertEquals(0L, manual.getLong("remainingSeconds"))
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

    @Test
    fun pinlessManualStartSkipsThePinGateAndKeepsAnExistingLockout() {
        assertTrue(UserConfigPinSettings.setRequired(false))
        assertFalse(
            ParentControlActions.statusJson()
                .getJSONObject("manualStart")
                .getBoolean("requiresPin"),
        )
        // A lockout created by another phone must neither block a pinless start nor be cleared
        // by it.
        QuizAccess.lockSensitiveActionOut()
        val result = handle(
            body("START_MANUAL", "manual-pinless-0001") {
                put("minutes", 20)
            },
        )
        assertNotEquals("WRONG_PIN", result.code)
        assertNotEquals("PIN_LOCKED", result.code)
        assertTrue(QuizAccess.sensitiveActionLockedSeconds() > 0L)
    }

    @Test
    fun rulesJsonPublishesEveryDayGroupAndTheUnlimitedCategory() {
        val allDay = listOf(LocalTimeWindow(0, 24 * 60))
        val schedule = LocalRules(
            dayGroups = listOf(
                LocalDayGroup(
                    days = listOf(1, 2),
                    studyWindows = allDay,
                    studyDailyLimitMinutes = null,
                    entertainmentWindows = listOf(LocalTimeWindow(600, 1200)),
                    entertainmentDailyLimitMinutes = 30,
                ),
                LocalDayGroup(
                    days = listOf(3, 4, 5),
                    studyWindows = allDay,
                    studyDailyLimitMinutes = 45,
                    entertainmentWindows = allDay,
                    entertainmentDailyLimitMinutes = 60,
                ),
                LocalDayGroup(
                    days = listOf(6, 7),
                    studyWindows = allDay,
                    studyDailyLimitMinutes = 0,
                    entertainmentWindows = emptyList(),
                    entertainmentDailyLimitMinutes = null,
                ),
            ),
            sessionLimitMinutes = 20,
            restMinutes = 5,
            allowedPackages = setOf(videoPackage, "com.example.free"),
            unlimitedPackages = setOf("com.example.free"),
        ).normalized()

        val json = ParentCenterActions.rulesJson(schedule)

        assertEquals(ParentCenterActions.RULES_CONTRACT_VERSION, json.getInt("rulesContractVersion"))
        // The web page needs the stored switches and the V5 limit fields. Switches are
        // independent: none of them implies pausing another route.
        assertTrue(json.getBoolean("quizGateEnabled"))
        assertFalse(json.getBoolean("studyEarnEnabled"))
        assertFalse(json.getBoolean("directStartEnabled"))
        assertEquals(600, json.getInt("sessionCapMinutes"))
        assertEquals(20, json.getInt("sessionLimitMinutes"))
        assertEquals(20, json.getInt("directSessionMinutes"))
        assertEquals(1000, json.getInt("quizDailyLimitCount"))
        assertEquals(1000, json.getInt("studyRewardDailyLimitCount"))
        assertEquals(1000, json.getInt("directDailyFreeCount"))
        val groups = json.getJSONArray("dayGroups")
        assertEquals(3, groups.length())
        val firstDays = groups.getJSONObject(0).getJSONArray("days")
        assertEquals(2, firstDays.length())
        assertEquals(1, firstDays.getInt(0))
        assertEquals(2, firstDays.getInt(1))
        // null is the wire form of "unlimited"; 0 remains a finite, locked quota.
        assertTrue(groups.getJSONObject(0).isNull("studyDailyLimitMinutes"))
        assertEquals(30, groups.getJSONObject(0).getInt("entertainmentDailyLimitMinutes"))
        assertEquals(45, groups.getJSONObject(1).getInt("studyDailyLimitMinutes"))
        assertEquals(0, groups.getJSONObject(2).getInt("studyDailyLimitMinutes"))
        assertTrue(groups.getJSONObject(2).isNull("entertainmentDailyLimitMinutes"))
        assertEquals(0, groups.getJSONObject(2).getJSONArray("entertainmentWindows").length())

        val unlimited = json.getJSONArray("unlimitedPackages")
        assertEquals(1, unlimited.length())
        assertEquals("com.example.free", unlimited.getString(0))
        // The legacy weekday/weekend fields are projections of the Monday/Saturday groups.
        assertEquals(600, json.getJSONArray("weekdayWindows").getJSONObject(0).getInt("start"))
        assertEquals(30, json.getInt("autoDailyLimitMinutes"))
        assertEquals(1440, json.getInt("weekendDailyLimitMinutes"))
    }

    @Test
    fun independentRouteSwitchesAreSavedAndAnOldContractMustReload() {
        val v5 = saveRules(
            schemaFiveRules(defaultGroups)
                .put("quizGateEnabled", false)
                .put("studyEarnEnabled", false)
                .put("directStartEnabled", true),
        )
        assertEquals("OK", v5.code)
        val saved = LocalPolicyRuntime.currentRules!!
        assertFalse(saved.quizGateEnabled)
        assertFalse(saved.studyEarnEnabled)
        assertTrue(saved.directStartEnabled)
        // Turning direct on must not pause the other two entries in the stored rules.
        assertEquals(3, saved.quizDailyLimitCount)
        assertEquals(3, saved.studyRewardDailyLimitCount)

        // A cached V4 page cannot express the route counts or the shared cap. The server asks
        // for a reload instead of merging a partial rule set.
        val v4 = saveRules(
            schemaFiveRules(defaultGroups)
                .put("rulesContractVersion", 4)
                .put("restMinutes", 7),
        )
        assertEquals("RULES_CLIENT_OUTDATED", v4.code)
        assertEquals(5, LocalPolicyRuntime.currentRules!!.restMinutes)
    }

    @Test
    fun allThreeRoutesOffIsSavedWithoutFallback() {
        val outcome = saveRules(
            schemaFiveRules(defaultGroups)
                .put("quizGateEnabled", false)
                .put("studyEarnEnabled", false)
                .put("directStartEnabled", false),
        )

        assertEquals("OK", outcome.code)
        val saved = LocalPolicyRuntime.currentRules!!
        assertFalse(saved.quizGateEnabled)
        assertFalse(saved.studyEarnEnabled)
        assertFalse(saved.directStartEnabled)
        assertFalse(saved.hasUnlockRoute)
    }

    @Test
    fun v5SavesValidateTheCapDirectDurationCountsAndStudyReward() {
        val capTooLow = saveRules(schemaFiveRules(defaultGroups).put("sessionCapMinutes", 0))
        assertEquals("RULES_INVALID_SESSION_CAP", capTooLow.code)
        val capAboveTheOldCeiling = saveRules(
            schemaFiveRules(defaultGroups).put("sessionCapMinutes", 121),
        )
        assertEquals("OK", capAboveTheOldCeiling.code)
        assertEquals(121, LocalPolicyRuntime.currentRules!!.sessionCapMinutes)
        val largeCap = saveRules(
            schemaFiveRules(defaultGroups).put("sessionCapMinutes", 100000),
        )
        assertEquals("OK", largeCap.code)
        assertEquals(100000, LocalPolicyRuntime.currentRules!!.sessionCapMinutes)
        val capTooHigh = saveRules(
            schemaFiveRules(defaultGroups).put("sessionCapMinutes", 2147483648L),
        )
        assertEquals("RULES_INVALID_SESSION_CAP", capTooHigh.code)
        val missingCap = saveRules(schemaFiveRules(defaultGroups).apply {
            remove("sessionCapMinutes")
        })
        assertEquals("RULES_INVALID_SESSION_CAP", missingCap.code)

        val directAboveCap = saveRules(
            schemaFiveRules(defaultGroups)
                .put("sessionCapMinutes", 10)
                .put("directSessionMinutes", 15)
                .put("sessionLimitMinutes", 15),
        )
        assertEquals("RULES_INVALID_DIRECT_SESSION", directAboveCap.code)
        val aliasesDisagree = saveRules(
            schemaFiveRules(defaultGroups)
                .put("directSessionMinutes", 10)
                .put("sessionLimitMinutes", 20),
        )
        assertEquals("RULES_INVALID_DIRECT_SESSION", aliasesDisagree.code)
        val directTooShort = saveRules(
            schemaFiveRules(defaultGroups)
                .put("directSessionMinutes", 0)
                .put("sessionLimitMinutes", 0),
        )
        assertEquals("RULES_INVALID_DIRECT_SESSION", directTooShort.code)
        // 1 minute is the short-session/test floor for all three route durations.
        val oneMinute = saveRules(
            schemaFiveRules(defaultGroups)
                .put("sessionCapMinutes", 1)
                .put("directSessionMinutes", 1)
                .put("sessionLimitMinutes", 1)
                .put("studyEarnStudyMinutes", 1)
                .put("studyEarnRewardMinutes", 1),
        )
        assertEquals("OK", oneMinute.code)
        LocalPolicyRuntime.currentRules!!.let { saved ->
            assertEquals(1, saved.sessionCapMinutes)
            assertEquals(1, saved.sessionLimitMinutes)
            assertEquals(1, saved.studyEarnStudyMinutes)
            assertEquals(1, saved.studyEarnRewardMinutes)
        }

        val countLarge = saveRules(
            schemaFiveRules(defaultGroups).put("quizDailyLimitCount", 100000),
        )
        assertEquals("OK", countLarge.code)
        assertEquals(100000, LocalPolicyRuntime.currentRules!!.quizDailyLimitCount)
        val countTooHigh = saveRules(
            schemaFiveRules(defaultGroups).put("quizDailyLimitCount", 2147483648L),
        )
        assertEquals("RULES_INVALID_ROUTE_COUNTS", countTooHigh.code)
        val countMissing = saveRules(schemaFiveRules(defaultGroups).apply {
            remove("directDailyFreeCount")
        })
        assertEquals("RULES_INVALID_ROUTE_COUNTS", countMissing.code)

        val rewardAboveCap = saveRules(
            schemaFiveRules(defaultGroups)
                .put("sessionCapMinutes", 20)
                .put("studyEarnStudyMinutes", 60)
                .put("studyEarnRewardMinutes", 25),
        )
        assertEquals("RULES_INVALID_STUDY_EARN", rewardAboveCap.code)
        // X and Y are independent inputs; Y may exceed X as long as it fits the public cap.
        val rewardAboveStudy = saveRules(
            schemaFiveRules(defaultGroups)
                .put("studyEarnStudyMinutes", 10)
                .put("studyEarnRewardMinutes", 15),
        )
        assertEquals("OK", rewardAboveStudy.code)
        assertEquals(15, LocalPolicyRuntime.currentRules!!.studyEarnRewardMinutes)

        // Boundary values are accepted and stored unchanged.
        val atBounds = saveRules(
            schemaFiveRules(defaultGroups)
                .put("sessionCapMinutes", 10)
                .put("directSessionMinutes", 10)
                .put("sessionLimitMinutes", 10)
                .put("quizDailyLimitCount", 0)
                .put("studyRewardDailyLimitCount", 10)
                .put("directDailyFreeCount", 0)
                .put("studyEarnStudyMinutes", 10)
                .put("studyEarnRewardMinutes", 10),
        )
        assertEquals("OK", atBounds.code)
        val saved = LocalPolicyRuntime.currentRules!!
        assertEquals(10, saved.sessionCapMinutes)
        assertEquals(10, saved.sessionLimitMinutes)
        assertEquals(0, saved.quizDailyLimitCount)
        assertEquals(10, saved.studyRewardDailyLimitCount)
        assertEquals(0, saved.directDailyFreeCount)
        assertEquals(10, saved.studyEarnRewardMinutes)
    }

    @Test
    fun snapshotPublishesTheSessionCapAndTodaysRouteCounts() {
        val snapshot = ParentCenterActions.snapshot(app)
        val rules = snapshot.getJSONObject("rules")
        assertEquals(5, rules.getInt("rulesContractVersion"))
        assertEquals(600, rules.getInt("sessionCapMinutes"))
        assertEquals(20, rules.getInt("directSessionMinutes"))
        assertEquals(20, rules.getInt("sessionLimitMinutes"))
        assertEquals(1000, rules.getInt("directDailyFreeCount"))

        val usage = snapshot.getJSONObject("usage")
        assertEquals(600 * 60, usage.getInt("sessionCapSeconds"))
        val counts = usage.getJSONObject("routeCounts")
        assertTrue(counts.getString("date").isNotEmpty())
        val quiz = counts.getJSONObject("quiz")
        assertEquals(0, quiz.getInt("used"))
        assertEquals(1000, quiz.getInt("limit"))
        assertEquals(1000, quiz.getInt("remaining"))
        val direct = counts.getJSONObject("directFree")
        assertEquals(0, direct.getInt("used"))
        assertEquals(1000, direct.getInt("remaining"))
        assertEquals(1000, counts.getJSONObject("studyReward").getInt("remaining"))
    }

    @Test
    fun aPageWithoutTheV5ContractMustReloadInsteadOfMerging() {
        val missingVersion = saveRules(schemaFiveRules(defaultGroups).apply {
            remove("rulesContractVersion")
        })
        assertEquals("RULES_CLIENT_OUTDATED", missingVersion.code)

        val missingGroups = saveRules(schemaFiveRules(JSONArray()).apply {
            remove("dayGroups")
        })
        assertEquals("RULES_CLIENT_OUTDATED", missingGroups.code)

        val oldNumber = saveRules(schemaFiveRules(defaultGroups).put("rulesContractVersion", 2))
        assertEquals("RULES_CLIENT_OUTDATED", oldNumber.code)
        assertEquals(2, LocalPolicyRuntime.currentRules!!.dayGroups.size)
    }

    @Test
    fun incompleteDayCoverageIsRejectedAsADayGroupError() {
        val incomplete = JSONArray().put(groupJson(listOf(1, 2, 3, 4, 5)))

        val outcome = saveRules(schemaFiveRules(incomplete))

        assertFalse(outcome.ok)
        assertEquals("RULES_INVALID_DAY_GROUPS", outcome.code)
        // The previous schedule is untouched.
        assertEquals(2, LocalPolicyRuntime.currentRules!!.dayGroups.size)
    }

    @Test
    fun theUnlimitedCategoryCannotUseTheAllAppsSentinel() {
        val outcome = saveRules(
            schemaFiveRules(defaultGroups).put(
                "unlimitedPackages",
                JSONArray().put("all_valid_app"),
            ),
        )

        assertFalse(outcome.ok)
        assertEquals("RULES_INVALID_UNLIMITED_APPS", outcome.code)
        assertTrue(LocalPolicyRuntime.currentRules!!.effectiveUnlimitedPackages.isEmpty())
    }

    @Test
    fun aStaleRulesRevisionIsRejectedBeforeParsing() {
        val stale = saveRules(
            schemaFiveRules(defaultGroups),
            expectedRevision = LocalPolicyRuntime.rulesEpoch - 1,
        )
        assertEquals("STATE_CHANGED", stale.code)

        val missingRevision = ParentCenterActions.setRules(
            JSONObject().put("rules", schemaFiveRules(defaultGroups)),
        )
        assertEquals("STATE_CHANGED", missingRevision.code)
    }

    @Test
    fun aValidSaveAppliesCustomGroupsAndTheUnlimitedCategory() {
        val groups = JSONArray()
            .put(
                groupJson(
                    listOf(1, 2, 3, 4),
                    studyLimit = 30,
                    entertainmentLimit = 45,
                    entertainmentWindows = listOf(windowJson(600, 1260)),
                ),
            )
            .put(groupJson(listOf(5), studyLimit = null, entertainmentLimit = null))
            .put(groupJson(listOf(6, 7), studyLimit = 60, entertainmentLimit = 0))

        val outcome = saveRules(
            schemaFiveRules(groups).put(
                "unlimitedPackages",
                JSONArray().put("com.example.free"),
            ),
        )

        assertEquals("OK", outcome.code)
        // The schedule changed, so the save is announced as a next-session snapshot.
        assertEquals("SAVED_NEXT_SESSION", outcome.messageKey)
        val saved = LocalPolicyRuntime.currentRules!!
        assertEquals(3, saved.dayGroups.size)
        assertEquals(listOf(1, 2, 3, 4), saved.dayGroups[0].days)
        assertEquals(45, saved.entertainmentDailyLimitFor(2))
        assertNull(saved.studyDailyLimitFor(5))
        assertEquals(0, saved.entertainmentDailyLimitFor(6))
        assertEquals(setOf("com.example.free"), saved.effectiveUnlimitedPackages)
    }
}
