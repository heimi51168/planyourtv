package app.orchard.tv.policy.web

import app.orchard.tv.policy.ManualSessionLimits
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.UserConfigPinSettings
import app.orchard.tv.quiz.QuizAccess
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.SessionState
import org.json.JSONObject
import java.util.Locale

/**
 * Command layer for the independent web "Control" page.
 *
 * The page sends a desired state, never a toggle: already being in that state is a successful
 * `NOOP`. Every action re-reads the live TV state under this object's monitor, and destructive
 * actions return a short-lived single-use confirmation token instead of letting the page decide
 * whether a confirmation is needed. Results are cached per editor + action + requestId so a
 * network retry cannot end a session twice.
 *
 * This deliberately stays separate from [ParentCenterActions]: that object owns configuration
 * writes and snapshots, while these are short commands with their own idempotency and
 * confirmation lifecycle.
 */
object ParentControlActions {
    enum class Action {
        TAKE_OVER,
        ENTER_CHILD_MODE,
        STOP_TIMER,
        START_MANUAL;

        companion object {
            fun parse(value: String): Action? = entries.firstOrNull {
                it.name == value.uppercase(Locale.ROOT)
            }
        }
    }

    data class Confirm(
        val token: String,
        val reason: String,
        val expiresAtMs: Long,
    )

    data class Outcome(
        val ok: Boolean,
        val action: String,
        val requestId: String,
        val result: String,
        val code: String,
        val messageKey: String,
        val retryAfterSeconds: Long = 0L,
        val confirm: Confirm? = null,
    )

    private const val RESULT_TTL_MS = 5 * 60_000L
    private const val CONFIRM_TTL_MS = 60_000L
    /** Keep an expired token around long enough to answer CONFIRM_EXPIRED, not STALE_CONFIRM. */
    private const val CONFIRM_RETAIN_MS = 10 * 60_000L
    private const val MAX_RESULTS = 64
    private const val MAX_REQUESTS = 256
    private const val MAX_CONFIRMS = 128
    private val REQUEST_ID_RE = Regex("[A-Za-z0-9_-]{16,64}")

    private data class RequestKey(val editorDigest: String, val requestId: String)
    private data class ResultKey(
        val editorDigest: String,
        val action: Action,
        val requestId: String,
    )

    private data class RequestRecord(val action: Action, val atMs: Long)
    private data class CachedResult(
        val result: String,
        val code: String,
        val messageKey: String,
        val atMs: Long,
    )
    private data class ConfirmRecord(
        val action: Action,
        val requestId: String,
        val editorDigest: String,
        val fingerprint: String,
        val reason: String,
        val expiresAtMs: Long,
    )

    private val results = LinkedHashMap<ResultKey, CachedResult>()
    private val requests = LinkedHashMap<RequestKey, RequestRecord>()
    private val confirms = LinkedHashMap<String, ConfirmRecord>()

    /**
     * Parses and executes one command. The HTTP layer must already have validated the editor
     * lease; the editor token is used only as an opaque cache/confirmation partition and is
     * never stored or logged.
     */
    @Synchronized
    internal fun handle(
        body: JSONObject,
        editorToken: String,
        nowMs: Long = System.currentTimeMillis(),
        sensitiveToken: String = "",
    ): Outcome {
        sweep(nowMs)
        val rawAction = body.optString("action", "").trim()
        val action = Action.parse(rawAction)
            ?: return error(rawAction, body.optString("requestId", ""), "INVALID_ACTION")
        val requestId = body.optString("requestId", "").trim()
        if (!REQUEST_ID_RE.matches(requestId)) {
            return error(action.name, requestId, "INVALID_REQUEST_ID")
        }
        val editorDigest = ParentWebCrypto.sha256Hex(editorToken)
        val requestKey = RequestKey(editorDigest, requestId)
        val previous = requests[requestKey]
        if (previous != null && previous.action != action) {
            return error(action.name, requestId, "REQUEST_ID_MISMATCH")
        }
        // Re-insert on every touch so eviction is least-recently-used, not first-written.
        requests.remove(requestKey)
        requests[requestKey] = RequestRecord(action, nowMs)
        trim(requests, MAX_REQUESTS)

        val resultKey = ResultKey(editorDigest, action, requestId)
        val cached = results.remove(resultKey)
        if (cached != null) {
            results[resultKey] = cached
            return Outcome(
                ok = true,
                action = action.name,
                requestId = requestId,
                result = cached.result,
                code = cached.code,
                messageKey = cached.messageKey,
            )
        }

        // Temporary exit is the one command that leaves the child guard, so it needs the
        // short single-use PIN proof. That PIN is also the confirmation: V48 removed the extra
        // dialog so an active timer or quiz round is discarded immediately. A duplicate
        // requestId returns its cached result above and does not ask again. Being in
        // PARENT_OVERRIDE is already the desired state and stays a no-op without a fresh proof.
        if (action == Action.TAKE_OVER &&
            PolicyModeCoordinator.state.value.guardState != GuardStatus.PARENT_OVERRIDE
        ) {
            when (ParentSensitiveAuth.check(
                token = sensitiveToken,
                scope = ParentSensitiveAuth.Scope.TAKE_OVER,
                editorToken = editorToken,
                nowMs = nowMs,
            )) {
                ParentSensitiveAuth.Status.OK -> Unit
                ParentSensitiveAuth.Status.REQUIRED ->
                    return error(Action.TAKE_OVER.name, requestId, "SENSITIVE_REQUIRED")
                ParentSensitiveAuth.Status.EXPIRED ->
                    return error(Action.TAKE_OVER.name, requestId, "SENSITIVE_EXPIRED")
                ParentSensitiveAuth.Status.SCOPE ->
                    return error(Action.TAKE_OVER.name, requestId, "SENSITIVE_SCOPE")
            }
        }

        return when (action) {
            Action.TAKE_OVER ->
                takeOver(editorToken, editorDigest, requestId, sensitiveToken, nowMs)
            Action.ENTER_CHILD_MODE -> enterChildMode(editorDigest, requestId, nowMs)
            Action.STOP_TIMER -> stopTimer(body, editorDigest, requestId, nowMs)
            Action.START_MANUAL -> startManual(body, editorDigest, requestId, nowMs)
        }
    }

    private fun takeOver(
        editorToken: String,
        editorDigest: String,
        requestId: String,
        sensitiveToken: String,
        nowMs: Long,
    ): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.guardState == GuardStatus.PARENT_OVERRIDE) {
            return finish(Action.TAKE_OVER, requestId, "NOOP", "TAKE_OVER_NOOP", editorDigest, nowMs)
        }
        // V48: the sensitive PIN already confirms ending the current timer or quiz round, so
        // take-over executes in one step. `takeOverFromWeb()` keeps the used time and skips rest.
        val applied = PolicyModeCoordinator.takeOverFromWeb()
        if (applied) clearConfirms(editorDigest)
        // The proof is single-use and is consumed after the action is applied or found to be a
        // no-op.
        ParentSensitiveAuth.consume(
            token = sensitiveToken,
            scope = ParentSensitiveAuth.Scope.TAKE_OVER,
            editorToken = editorToken,
            nowMs = nowMs,
        )
        return finish(
            Action.TAKE_OVER,
            requestId,
            if (applied) "APPLIED" else "NOOP",
            if (applied) "TAKE_OVER_APPLIED" else "TAKE_OVER_NOOP",
            editorDigest,
            nowMs,
        )
    }

    private fun enterChildMode(
        editorDigest: String,
        requestId: String,
        nowMs: Long,
    ): Outcome {
        if (PolicyModeCoordinator.state.value.guardState != GuardStatus.PARENT_OVERRIDE) {
            return finish(
                Action.ENTER_CHILD_MODE,
                requestId,
                "NOOP",
                "ENTER_CHILD_NOOP",
                editorDigest,
                nowMs,
            )
        }
        val applied = PolicyModeCoordinator.enterChildModeFromWeb()
        if (applied) clearConfirms(editorDigest)
        return finish(
            Action.ENTER_CHILD_MODE,
            requestId,
            if (applied) "APPLIED" else "NOOP",
            if (applied) "ENTER_CHILD_APPLIED" else "ENTER_CHILD_NOOP",
            editorDigest,
            nowMs,
        )
    }

    private fun stopTimer(
        body: JSONObject,
        editorDigest: String,
        requestId: String,
        nowMs: Long,
    ): Outcome {
        val active = PolicyModeCoordinator.state.value.sessionState == SessionState.ACTIVE
        val supplied = body.optString("confirmToken", "").trim()
        if (supplied.isNotEmpty()) {
            val failure = validateConfirm(
                token = supplied,
                action = Action.STOP_TIMER,
                requestId = requestId,
                editorDigest = editorDigest,
                nowMs = nowMs,
            )
            if (failure != null) return failure
        } else if (active) {
            // The child's exit timer always asks first; the page must not decide that itself.
            return confirmRequired(
                Action.STOP_TIMER,
                requestId,
                editorDigest,
                "END_TIMER",
                nowMs,
            )
        }
        if (!active) {
            return finish(
                Action.STOP_TIMER,
                requestId,
                "NOOP",
                "STOP_TIMER_NOOP",
                editorDigest,
                nowMs,
            )
        }
        val applied = PolicyModeCoordinator.stopTimerFromWeb()
        if (applied) clearConfirms(editorDigest)
        return finish(
            Action.STOP_TIMER,
            requestId,
            if (applied) "APPLIED" else "NOOP",
            if (applied) "STOP_TIMER_APPLIED" else "STOP_TIMER_NOOP",
            editorDigest,
            nowMs,
        )
    }

    private fun startManual(
        body: JSONObject,
        editorDigest: String,
        requestId: String,
        nowMs: Long,
    ): Outcome {
        val requiresPin = UserConfigPinSettings.isRequired()
        var pin = ""
        if (requiresPin) {
            val wait = QuizAccess.sensitiveActionLockedSeconds(nowMs)
            if (wait > 0L) {
                return error(
                    Action.START_MANUAL.name,
                    requestId,
                    "PIN_LOCKED",
                    retryAfterSeconds = wait,
                )
            }
            pin = body.optString("pin", "")
            if (pin.length != 6 || pin.any { !it.isDigit() }) {
                return error(Action.START_MANUAL.name, requestId, "WRONG_PIN")
            }
        }
        val minutes = body.optInt("minutes", -1)
        if (!ManualSessionLimits.isValidWeb(minutes)) {
            return error(Action.START_MANUAL.name, requestId, "INVALID_DURATION")
        }
        val before = PolicyModeCoordinator.webManualStartStatus()
        if (!before.eligible) {
            return error(Action.START_MANUAL.name, requestId, "START_BLOCKED")
        }
        return when (
            val result = PolicyModeCoordinator.startManualFromWeb(
                pin,
                minutes,
                requirePin = requiresPin,
            )
        ) {
            PolicyModeCoordinator.WebManualStartResult.Ok -> {
                // A pinless start is not a PIN success, so it must not clear another phone's
                // shared sensitive-action lockout.
                if (requiresPin) QuizAccess.onSensitiveActionResult(success = true)
                finish(
                    Action.START_MANUAL,
                    requestId,
                    "APPLIED",
                    "START_MANUAL_APPLIED",
                    editorDigest,
                    nowMs,
                )
            }
            PolicyModeCoordinator.WebManualStartResult.InvalidDuration ->
                error(Action.START_MANUAL.name, requestId, "INVALID_DURATION")
            is PolicyModeCoordinator.WebManualStartResult.PinInvalid -> {
                if (!requiresPin) {
                    // Defensive: a pinless call cannot fail PIN validation, but never touch
                    // the shared limiter if it somehow does.
                    error(Action.START_MANUAL.name, requestId, "WRONG_PIN")
                } else {
                    if (result.locked) {
                        QuizAccess.lockSensitiveActionOut()
                    } else {
                        QuizAccess.onSensitiveActionResult(success = false)
                    }
                    val retry = QuizAccess.sensitiveActionLockedSeconds()
                    if (retry > 0L) {
                        error(
                            Action.START_MANUAL.name,
                            requestId,
                            "PIN_LOCKED",
                            retryAfterSeconds = retry,
                        )
                    } else {
                        error(Action.START_MANUAL.name, requestId, "WRONG_PIN")
                    }
                }
            }
            is PolicyModeCoordinator.WebManualStartResult.Rejected -> {
                val code = if (
                    result.reason == PolicyModeCoordinator.WebManualStartReason.DEFAULT_PIN
                ) {
                    "DEFAULT_PIN"
                } else {
                    "START_BLOCKED"
                }
                error(Action.START_MANUAL.name, requestId, code)
            }
        }
    }

    /**
     * Compact live state for the control page. All action availability here is advisory; the
     * command handlers repeat the checks under their own lock.
     */
    fun statusJson(): JSONObject {
        val ui = PolicyModeCoordinator.state.value
        val manual = PolicyModeCoordinator.webManualStartStatus()
        val quizActive = quizRoundActive()
        val timerActive = ui.sessionState == SessionState.ACTIVE
        val override = ui.guardState == GuardStatus.PARENT_OVERRIDE
        val manualStart = JSONObject()
            .put("eligible", manual.eligible)
            .put("reason", manual.reason.name)
            .put("requiresPin", UserConfigPinSettings.isRequired())
            .put("remainingSeconds", manual.remainingSeconds)
            .put("restRemainingSeconds", manual.restRemainingSeconds)
            .put("outsidePlan", manual.outsidePlan)
            .put("minMinutes", ManualSessionLimits.WEB_MIN_MINUTES)
            .put("maxMinutes", ManualSessionLimits.MAX_MINUTES)
            .put("stepMinutes", ManualSessionLimits.STEP_MINUTES)
            .put("defaultMinutes", ManualSessionLimits.DEFAULT_MINUTES)
        // V45: the parent timer is plan-exempt and launches every configured entertainment,
        // unlimited and (when enabled) study-reward app. The sheet explains that scope; these
        // counts are what the configuration page actually resolved.
        PolicyModeCoordinator.manualTimerScope()?.let { scope ->
            manualStart.put(
                "scope",
                JSONObject()
                    .put("entertainmentCount", scope.entertainmentCount)
                    .put("entertainmentAll", scope.entertainmentAll)
                    .put("unlimitedCount", scope.unlimitedCount)
                    .put("studyRewardCount", scope.studyRewardCount)
                    .put("studyRewardIncluded", scope.studyRewardIncluded),
            )
        }
        return JSONObject()
            .put("childModeOn", ui.mode != PolicyMode.NONE)
            .put("guardState", ui.guardState.name)
            .put("sessionState", ui.sessionState.name)
            .put("parentUnlockRemainingSeconds", ui.parentUnlockRemainingSeconds.coerceAtLeast(0L))
            .put(
                "timer",
                JSONObject()
                    .put("active", timerActive)
                    .put(
                        "remainingSeconds",
                        if (timerActive) ui.effectiveRemainingSeconds.coerceAtLeast(0L) else 0L,
                    )
                    .put("restRemainingSeconds", ui.restRemainingSeconds.coerceAtLeast(0L))
                    // V45: the configuration page labels a parent-started timer as
                    // "out-of-plan" instead of the generic countdown.
                    .put("outsidePlan", timerActive && ui.activeSessionOutsidePlan),
            )
            .put("quiz", JSONObject().put("roundActive", quizActive))
            .put("manualStart", manualStart)
            .put(
                "actions",
                JSONObject()
                    .put(
                        "takeOver",
                        JSONObject()
                            .put("enabled", !override)
                            // V48: the parent PIN is the confirmation; no second dialog.
                            .put("requiresConfirm", false)
                            .put("reason", if (override) "ALREADY_OVERRIDE" else "OK"),
                    )
                    .put(
                        "enterChildMode",
                        JSONObject()
                            .put("enabled", override)
                            .put("requiresConfirm", false)
                            .put("reason", if (override) "OK" else "ALREADY_CHILD_MODE"),
                    )
                    .put(
                        "stopTimer",
                        JSONObject()
                            .put("enabled", timerActive)
                            // This action always asks on the TV side, even while the button is
                            // disabled because there is nothing to stop yet.
                            .put("requiresConfirm", true)
                            .put("reason", if (timerActive) "OK" else "NO_TIMER"),
                    )
                    .put(
                        "startManual",
                        JSONObject()
                            .put("enabled", manual.eligible)
                            .put("requiresConfirm", false)
                            .put("reason", manual.reason.name),
                    ),
            )
    }

    /** JSON body for an outcome, including the latest control state on every response. */
    fun responseJson(outcome: Outcome): JSONObject {
        val response = JSONObject()
            .put("ok", outcome.ok)
            .put("action", outcome.action)
            .put("requestId", outcome.requestId)
            .put("result", outcome.result)
            .put("code", outcome.code)
            .put("messageKey", outcome.messageKey)
        if (!outcome.ok) response.put("error", outcome.code)
        if (outcome.retryAfterSeconds > 0L) {
            response.put("retryAfterSeconds", outcome.retryAfterSeconds)
        }
        outcome.confirm?.let {
            response.put(
                "confirm",
                JSONObject()
                    .put("token", it.token)
                    .put("reason", it.reason)
                    .put("expiresAtMs", it.expiresAtMs),
            )
        }
        // Best effort: a control response is still useful even if the page is being disabled
        // in the same instant.
        runCatching { response.put("control", statusJson()) }
        return response
    }

    @Synchronized
    internal fun resetForTest() {
        results.clear()
        requests.clear()
        confirms.clear()
    }

    private fun finish(
        action: Action,
        requestId: String,
        result: String,
        code: String,
        editorDigest: String,
        nowMs: Long,
    ): Outcome {
        results[ResultKey(editorDigest, action, requestId)] = CachedResult(
            result = result,
            code = code,
            messageKey = code,
            atMs = nowMs,
        )
        trim(results, MAX_RESULTS)
        return Outcome(
            ok = true,
            action = action.name,
            requestId = requestId,
            result = result,
            code = code,
            messageKey = code,
        )
    }

    private fun error(
        action: String,
        requestId: String,
        code: String,
        retryAfterSeconds: Long = 0L,
    ): Outcome = Outcome(
        ok = false,
        action = action,
        requestId = requestId,
        result = "ERROR",
        code = code,
        messageKey = code,
        retryAfterSeconds = retryAfterSeconds,
    )

    private fun confirmRequired(
        action: Action,
        requestId: String,
        editorDigest: String,
        reason: String,
        nowMs: Long,
    ): Outcome {
        // A retry that arrives without the token gets a fresh token, but the same requestId
        // never accumulates more than one live confirmation.
        confirms.entries.removeAll { (_, record) ->
            record.action == action &&
                record.requestId == requestId &&
                record.editorDigest == editorDigest
        }
        val token = ParentWebCrypto.randomToken()
        val expiresAt = nowMs + CONFIRM_TTL_MS
        confirms[token] = ConfirmRecord(
            action = action,
            requestId = requestId,
            editorDigest = editorDigest,
            fingerprint = currentFingerprint(),
            reason = reason,
            expiresAtMs = expiresAt,
        )
        trim(confirms, MAX_CONFIRMS)
        return Outcome(
            ok = true,
            action = action.name,
            requestId = requestId,
            result = "CONFIRM_REQUIRED",
            code = "CONFIRM_REQUIRED",
            messageKey = "CONFIRM_REQUIRED",
            confirm = Confirm(token, reason, expiresAt),
        )
    }

    /**
     * Returns null when the token is valid for this exact command and live state. All failures
     * are 409-class errors; a caller with a stale popup must refresh and issue a new request.
     */
    private fun validateConfirm(
        token: String,
        action: Action,
        requestId: String,
        editorDigest: String,
        nowMs: Long,
    ): Outcome? {
        val record = confirms[token]
            ?: return error(action.name, requestId, "STALE_CONFIRM")
        if (record.expiresAtMs <= nowMs) {
            confirms.remove(token)
            return error(action.name, requestId, "CONFIRM_EXPIRED")
        }
        if (record.action != action ||
            record.requestId != requestId ||
            record.editorDigest != editorDigest ||
            record.fingerprint != currentFingerprint()
        ) {
            // A used/foreign token or a state that moved on cannot be retried: make sure it is
            // gone so a second request with the same value does not accidentally match again.
            confirms.remove(token)
            return error(action.name, requestId, "STALE_CONFIRM")
        }
        confirms.remove(token)
        return null
    }

    private fun clearConfirms(editorDigest: String) {
        confirms.entries.removeAll { (_, record) -> record.editorDigest == editorDigest }
    }

    private fun currentFingerprint(): String =
        ParentWebCrypto.sha256Hex(PolicyModeCoordinator.controlStateFingerprint())

    private fun quizRoundActive(): Boolean =
        runCatching { PolicyModeCoordinator.quizRoundActive() }.getOrDefault(false)

    private fun sweep(nowMs: Long) {
        results.entries.removeAll { nowMs - it.value.atMs > RESULT_TTL_MS }
        requests.entries.removeAll { nowMs - it.value.atMs > RESULT_TTL_MS }
        confirms.entries.removeAll { nowMs - it.value.expiresAtMs > CONFIRM_RETAIN_MS }
    }

    private fun <K, V> trim(map: LinkedHashMap<K, V>, max: Int) {
        while (map.size > max) {
            val first = map.keys.firstOrNull() ?: break
            map.remove(first)
        }
    }
}
