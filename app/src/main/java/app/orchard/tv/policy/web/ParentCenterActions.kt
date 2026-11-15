package app.orchard.tv.policy.web

import android.content.Context
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyInstalledApps
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.PolicyProduct
import app.orchard.tv.policy.SourceEntrySettings
import app.orchard.tv.policy.media.OfficialMediaCatalog
import app.orchard.tv.policy.media.OfficialMediaDownloader
import app.orchard.tv.policy.media.PolicyVideoLibrary
import app.orchard.tv.policy.media.PolicyVideoRole
import app.orchard.tv.quiz.QuizAccess
import app.orchard.tv.quiz.QuizIntroVideo
import app.orchard.tv.quiz.QuizSettings
import app.orchard.tv.update.Updates
import com.planyourtv.policy.Activation
import com.planyourtv.policy.LocalPin
import com.planyourtv.policy.LocalRecovery
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalRulesError
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PinResult
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.RemoteHandoffState
import com.planyourtv.policy.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * State projection and the only write path of the web parent center.
 *
 * Every mutating method re-validates the same conditions the TV parent center enforces. The
 * HTTP layer is responsible for the editor lease, the request revisions and the body limit;
 * this object never trusts the page for mode, scope or read-only state.
 *
 * Stale writes are rejected with `expected*Revision` compared against the live revisions, so a
 * TV-side edit or a cloud mirror cannot be silently overwritten by an old tab.
 */
object ParentCenterActions {
    const val EDITOR_REQUIRED = "EDITOR_REQUIRED"

    data class Outcome(
        val ok: Boolean,
        val code: String,
        val messageKey: String,
        val retryAfterSeconds: Long = 0L,
        val recoveryCode: String? = null,
        val snapshot: JSONObject? = null,
    )

    private const val MODE_SWITCH_TIMEOUT_MS = 25_000L
    private const val DEFAULT_PIN_CACHE_MS = 60_000L

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var defaultPinCached = false
    @Volatile
    private var defaultPinCheckedAt = 0L

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun stop() {
        scope.cancel()
        appContext = null
    }

    // ---------------------------------------------------------------- snapshot

    fun snapshot(context: Context): JSONObject {
        val ui = PolicyModeCoordinator.state.value
        val cloud = PolicyRuntime.state.value
        val remote = PolicyRuntime.remotePolicy.value
        val bound = PolicyProduct.cloudEnabled && cloud.bound
        val handoffDone = bound && remote?.handoffState == RemoteHandoffState.DONE
        val readOnlyLocal = bound
        val childOn = ui.mode != PolicyMode.NONE
        val localSessionActive =
            ui.mode == PolicyMode.LOCAL && ui.sessionState == SessionState.ACTIVE
        val rules = LocalPolicyRuntime.currentRules
            ?: LocalRules.defaultsFor(ui.activation)
        val ruleSets = LocalPolicyRuntime.currentRuleSets

        val result = JSONObject()
            .put("ok", true)
            .put("mode", ui.mode.name)
            .put("loaded", ui.loaded)
            .put("guardState", ui.guardState.name)
            .put("sessionState", ui.sessionState.name)
            .put("activation", ui.activation.name)
            .put("source", ui.source.name)
            .put("setupRequired", ui.setupRequired)
            .put("pinConfigured", ui.pinConfigured)
            .put("defaultPin", defaultPinCached())
            .put(
                "parentUnlockRemainingSeconds",
                PolicyModeCoordinator.parentProofRemainingMs() / 1000,
            )
            .put("rulesRevision", LocalPolicyRuntime.rulesEpoch)
            .put("modeRevision", modeRevision())
            .put("autoUpdate", Updates.auto)
            .put("autoUpdateOffer", !Updates.auto && !Updates.autoChoiceMade())
            .put("sourceEntriesVisible", SourceEntrySettings.isVisible())
            .put("quizEnabled", QuizSettings.isEnabled())
            .put("introVideo", QuizIntroVideo.snapshot(context))
            // The main page only needs the current roles. Opening "Videos & wallpapers"
            // triggers GET /api/parent/media, which is the one place that refreshes the
            // catalogue, so merely opening the parent center never contacts the media host.
            .put("media", PolicyVideoLibrary.snapshot(context, autoRefreshCatalog = false))
            .put("pinSyncPending", PolicyRuntime.isPasswordSyncPending())
            .put("cloudEnabled", PolicyProduct.cloudEnabled)
            .put("deviceNo", cloud.deviceNo)
            .put("bound", bound)
            .put("readOnlyLocal", readOnlyLocal)
            .put("handoffDone", handoffDone)
            .put("childModeOn", childOn)
            .put("localSessionActive", localSessionActive)
            .put("rules", rulesJson(rules))
            .put(
                "capabilities",
                JSONObject()
                    .put("canEditRules", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    // Child-mode off and first-PIN setup stay on the TV: they change the
                    // authorization the web page itself depends on.
                    .put("canToggleChildMode", false)
                    .put("canChangePin", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    .put("canToggleQuiz", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    .put("canToggleSources", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    .put("canEditIntroVideos", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    .put("canEndSession", localSessionActive)
                    .put("canSetupPin", false)
                    .put("cloudEnabled", PolicyProduct.cloudEnabled),
            )
            .put(
                "usage",
                JSONObject()
                    .put("totalSeconds", ui.totalSeconds)
                    .put("remainingSeconds", ui.remainingSeconds)
                    .put("effectiveRemainingSeconds", ui.effectiveRemainingSeconds)
                    .put("dailyUsedSeconds", ui.dailyUsedSeconds)
                    .put("dailyLimitSeconds", ui.dailyLimitSeconds)
                    .put("sessionLimitSeconds", ui.sessionLimitSeconds)
                    .put("configuredRestMinutes", ui.configuredRestMinutes)
                    .put("configuredBufferMinutes", ui.configuredBufferMinutes)
                    .put("restRemainingSeconds", ui.restRemainingSeconds)
                    .put("autoReady", ui.autoReady)
                    .put("lockReason", ui.lockReason.name)
                    .put("nextWindowLabel", ui.nextWindowLabel)
                    .put("todayWindowsLabel", ui.todayWindowsLabel)
                    .put("windowRemainingSeconds", ui.windowRemainingSeconds)
                    .put("nextWindowInSeconds", ui.nextWindowInSeconds),
            )
            .put("control", ParentControlActions.statusJson())
        if (ruleSets != null) {
            result.put(
                "ruleSets",
                JSONObject()
                    .put("auto", ruleSetJson(ruleSets.auto))
                    .put("manual", ruleSetJson(ruleSets.manual)),
            )
        }
        return result
    }

    private fun ruleSetJson(set: com.planyourtv.policy.LocalRuleSet): JSONObject = JSONObject()
        .put("weekdayWindows", windowsJson(set.weekdayWindows))
        .put("weekendWindows", windowsJson(set.weekendWindows))
        .put("autoDailyLimitMinutes", set.autoDailyLimitMinutes)
        .put("sessionLimitMinutes", set.sessionLimitMinutes)
        .put("restMinutes", set.restMinutes)
        .put("sessionScreenOffBufferMinutes", set.sessionScreenOffBufferMinutes ?: JSONObject.NULL)

    fun rulesJson(rules: LocalRules): JSONObject = JSONObject()
        .put("weekdayWindows", windowsJson(rules.weekdayWindows))
        .put("weekendWindows", windowsJson(rules.weekendWindows))
        .put("autoDailyLimitMinutes", rules.autoDailyLimitMinutes)
        .put("sessionLimitMinutes", rules.sessionLimitMinutes)
        .put("restMinutes", rules.restMinutes)
        .put("sessionScreenOffBufferMinutes", rules.sessionScreenOffBufferMinutes)
        .put("allApps", rules.allowsAllEntertainmentApps)
        .put(
            "allowedPackages",
            JSONArray(rules.effectiveAllowedPackages - ALL_VALID_APP_SENTINEL),
        )
        .put("studyPackages", JSONArray(rules.effectiveStudyPackages))

    private fun windowsJson(windows: List<LocalTimeWindow>): JSONArray {
        val array = JSONArray()
        windows.forEach { window ->
            array.put(JSONObject().put("start", window.startMinute).put("end", window.endMinute))
        }
        return array
    }

    fun modeRevision(): String {
        val ui = PolicyModeCoordinator.state.value
        val cloud = PolicyRuntime.state.value
        val remote = PolicyRuntime.remotePolicy.value
        val seed = listOf(
            ui.mode.name,
            ui.guardState.name,
            ui.sessionState.name,
            ui.activation.name,
            ui.source.name,
            ui.setupRequired.toString(),
            ui.pinConfigured.toString(),
            cloud.bound.toString(),
            remote?.handoffState?.name ?: "NONE",
            remote?.mode?.name ?: "NONE",
            (remote?.configVersion ?: 0L).toString(),
            ui.lockReason.name,
        ).joinToString("|")
        return ParentWebCrypto.sha256Hex(seed).take(16)
    }

    // ------------------------------------------------------------------- writes

    fun setRules(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode != PolicyMode.LOCAL) return error("LOCAL_ONLY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val expected = body.optLong("expectedRulesRevision", -1L)
        if (expected < 0L || expected != LocalPolicyRuntime.rulesEpoch) {
            return conflict("STATE_CHANGED")
        }
        val next = parseRules(body.optJSONObject("rules") ?: return error("RULES_INVALID"))
            ?: return error("RULES_INVALID")
        val validation = next.validationError()
        if (validation != LocalRulesError.NONE) {
            val code = when (validation) {
                LocalRulesError.OVERLAPPING_WINDOWS -> "RULES_OVERLAP"
                LocalRulesError.TOO_MANY_WINDOWS -> "RULES_TOO_MANY_WINDOWS"
                LocalRulesError.INVALID_ALLOWED_PACKAGES -> "RULES_INVALID_APPS"
                LocalRulesError.INVALID_STUDY_PACKAGES -> "RULES_INVALID_STUDY_APPS"
                else -> "RULES_INVALID"
            }
            return error(code)
        }
        val before = LocalPolicyRuntime.currentRules
        if (!LocalPolicyRuntime.setRules(next)) return error("RULES_INVALID")
        val scopeChanged = before == null ||
            before.effectiveAllowedPackages != next.effectiveAllowedPackages ||
            before.effectiveStudyPackages != next.effectiveStudyPackages
        // Time rules are a next-session snapshot. Check them first: when one save mixes both
        // kinds, the deferred half is the part a parent must not misread as already live.
        val timeRulesChanged = before != null && (
            before.weekdayWindows != next.weekdayWindows ||
            before.weekendWindows != next.weekendWindows ||
            before.autoDailyLimitMinutes != next.autoDailyLimitMinutes ||
            before.sessionLimitMinutes != next.sessionLimitMinutes ||
            before.restMinutes != next.restMinutes ||
            before.sessionScreenOffBufferMinutes != next.sessionScreenOffBufferMinutes
        )
        val messageKey = when {
            timeRulesChanged -> "SAVED_NEXT_SESSION"
            scopeChanged -> "APP_SCOPE_SAVED"
            else -> "SAVED"
        }
        return ok(messageKey)
    }

    fun setActivation(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode != PolicyMode.LOCAL) return error("LOCAL_ONLY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val expectedMode = body.optString("expectedModeRevision", "")
        if (expectedMode.isBlank() || expectedMode != modeRevision()) {
            return conflict("STATE_CHANGED")
        }
        // V29 removes the local automatic scheduler from the configuration surface. A stale
        // page may only confirm the fixed manual mode; it can never turn AUTO back on.
        if (body.optString("activation", "").uppercase(Locale.ROOT) != Activation.MANUAL.name) {
            return error("MANUAL_MODE_FIXED")
        }
        if (!PolicyModeCoordinator.setLocalActivation(Activation.MANUAL)) {
            return error("SAVE_FAILED")
        }
        return ok("SAVED")
    }

    fun setChildMode(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        val expectedMode = body.optString("expectedModeRevision", "")
        if (expectedMode.isNotBlank() && expectedMode != modeRevision()) {
            return conflict("STATE_CHANGED")
        }
        val enabled = body.optBoolean("enabled", false)
        if (enabled) return enableChildMode(body)
        return disableChildMode(body)
    }

    private fun enableChildMode(body: JSONObject): Outcome {
        if (!PolicyModeCoordinator.state.value.loaded) return error("NOT_READY")
        if (PolicyModeCoordinator.mode != PolicyMode.NONE) {
            return ok("CHILD_MODE_ON")
        }
        val needsPin = runCatching { PolicyModeCoordinator.needsLocalPinSetup() }
            .getOrDefault(true)
        var recoveryCode: String? = null
        if (needsPin) {
            val pin = body.optString("pin", "")
            if (!LocalPin.isValid(pin)) return error("SETUP_PIN_REQUIRED")
            if (PolicyModeCoordinator.isDefaultPinValue(pin)) return error("PIN_TOO_SIMPLE")
            recoveryCode = PolicyModeCoordinator.completeLocalSetup(pin)
                ?: return error("PIN_SETUP_FAILED")
            invalidateDefaultPinCache()
        }
        val bound = PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound
        val target = if (bound) PolicyMode.CLOUD else PolicyMode.LOCAL
        val result = runBlocking {
            withTimeoutOrNull(MODE_SWITCH_TIMEOUT_MS) {
                PolicyModeCoordinator.requestModeSwitch(target, "", invalidateWeb = false)
            }
        } ?: return error("TIMEOUT")
        return switchOutcome(result, "CHILD_MODE_ON", recoveryCode)
    }

    private fun disableChildMode(body: JSONObject): Outcome {
        val mode = PolicyModeCoordinator.mode
        if (mode == PolicyMode.NONE) return ok("CHILD_MODE_OFF")
        val supplied = body.optString("pin", "")
        val pin = supplied.ifBlank { PolicyModeCoordinator.sharedParentPin().orEmpty() }
        if (!LocalPin.isValid(pin)) return error("PIN_REQUIRED")
        val result = runBlocking {
            withTimeoutOrNull(MODE_SWITCH_TIMEOUT_MS) {
                PolicyModeCoordinator.requestModeSwitch(
                    PolicyMode.NONE,
                    pin,
                    invalidateWeb = false,
                )
            }
        } ?: return error("TIMEOUT")
        return switchOutcome(result, "CHILD_MODE_OFF", null)
    }

    private fun switchOutcome(
        result: PolicyModeCoordinator.SwitchResult,
        successKey: String,
        recoveryCode: String?,
    ): Outcome = when (result) {
        PolicyModeCoordinator.SwitchResult.Success ->
            ok(successKey).copy(recoveryCode = recoveryCode)
        is PolicyModeCoordinator.SwitchResult.WrongPin -> error("WRONG_PIN")
        PolicyModeCoordinator.SwitchResult.PendingMustBeCanceled -> error("PENDING_MUST_BE_CANCELED")
        is PolicyModeCoordinator.SwitchResult.CloudSettlementFailed ->
            error("CLOUD_SETTLEMENT_FAILED")
        PolicyModeCoordinator.SwitchResult.NotReady -> error("NOT_READY")
    }

    /** First-run PIN setup without switching the guard on. */
    fun setupPin(body: JSONObject): Outcome {
        if (PolicyModeCoordinator.mode != PolicyMode.NONE) return error("MODE_NOT_NONE")
        val pin = body.optString("pin", "")
        if (!LocalPin.isValid(pin)) return error("PIN_INVALID")
        if (PolicyModeCoordinator.isDefaultPinValue(pin)) return error("PIN_TOO_SIMPLE")
        val recovery = PolicyModeCoordinator.completeLocalSetup(pin)
            ?: return error("PIN_SETUP_FAILED")
        invalidateDefaultPinCache()
        return ok("PIN_SAVED").copy(recoveryCode = recovery)
    }

    fun changePin(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode != PolicyMode.LOCAL) return error("LOCAL_ONLY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val newPin = body.optString("newPin", "")
        if (!LocalPin.isValid(newPin)) return error("PIN_INVALID")
        if (PolicyModeCoordinator.isDefaultPinValue(newPin)) return error("PIN_TOO_SIMPLE")
        // The login is the identity; only a password change asks for the current password
        // again. There is no shared/blank fallback.
        val oldPin = body.optString("oldPin", "")
        if (!LocalPin.isValid(oldPin)) return error("OLD_PIN_REQUIRED")
        if (oldPin == newPin) return error("PIN_UNCHANGED")
        if (!PolicyModeCoordinator.changePin(oldPin, newPin)) return error("WRONG_PIN")
        invalidateDefaultPinCache()
        return ok("PIN_SAVED")
    }

    /**
     * Forgot-password reset from the pre-login page. The one-time recovery code shown at setup
     * is the only proof; it never replaces the normal login after the PIN is known. A successful
     * reset also clears both in-memory and persisted PIN lockouts so the parent can log in with
     * the new PIN immediately, and revokes every web session opened under the old PIN.
     */
    fun recoverPin(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode != PolicyMode.LOCAL) return error("LOCAL_ONLY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val newPin = body.optString("newPin", "")
        // Validate the replacement before spending a recovery attempt on a malformed request.
        if (!LocalPin.isValid(newPin)) return error("NEW_PIN_INVALID")
        if (PolicyModeCoordinator.isDefaultPinValue(newPin)) return error("PIN_TOO_SIMPLE")
        val code = LocalRecovery.normalize(body.optString("recoveryCode", ""))
            ?: return error("RECOVERY_INVALID")
        return when (LocalPolicyRuntime.verifyRecovery(code)) {
            PinResult.OK -> {
                if (!LocalPolicyRuntime.resetWithRecovery(code, newPin)) {
                    return error("RECOVERY_FAILED")
                }
                QuizAccess.onPinReset()
                QuizAccess.invalidateWebSession("pin_reset")
                ParentWebAccess.invalidate("pin_reset")
                invalidateDefaultPinCache()
                ok("PIN_RESET")
            }
            PinResult.LOCKED_OUT -> Outcome(
                ok = false,
                code = "RECOVERY_LOCKED",
                messageKey = "RECOVERY_LOCKED",
                retryAfterSeconds = LocalPolicyRuntime.recoveryLockSeconds(),
            )
            PinResult.NOT_SET -> error("RECOVERY_NOT_SET")
            PinResult.WRONG -> error("RECOVERY_INVALID")
        }
    }

    fun setAutoUpdate(body: JSONObject): Outcome {
        val context = appContext ?: return error("NOT_READY")
        val enabled = body.optBoolean("enabled", false)
        Updates.setAuto(context, enabled)
        return ok("AUTO_UPDATE_SAVED")
    }

    fun setSourceEntries(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode == PolicyMode.NONE) return error("NOT_READY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val enabled = body.optBoolean("enabled", !SourceEntrySettings.isVisible())
        if (!SourceEntrySettings.setVisible(enabled)) return error("SAVE_FAILED")
        return ok("SOURCE_SAVED")
    }

    fun setQuiz(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode == PolicyMode.NONE) return error("NOT_READY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val enabled = body.optBoolean("enabled", false)
        if (QuizSettings.isEnabled() == enabled) return ok("QUIZ_SAVED")
        val result = runBlocking {
            withTimeoutOrNull(10_000L) {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    QuizSettings.setEnabled(enabled)
                }
            }
        } ?: return error("TIMEOUT")
        return when (result) {
            QuizSettings.SetResult.Changed -> ok("QUIZ_SAVED")
            QuizSettings.SetResult.SessionRunning -> error("QUIZ_SESSION_RUNNING")
            QuizSettings.SetResult.StorageError -> error("SAVE_FAILED")
        }
    }

    // ----------------------------------------------------------------- intro videos

    /**
     * Chooses a film from the local intro library. An empty id restores the built-in film.
     * These writes intentionally do not check the cloud read-only state: the films never
     * leave this TV, and the HTTP layer already requires the single parent-center editor lease.
     */
    fun selectIntroVideo(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (id.isEmpty()) {
            QuizIntroVideo.clearActive(context)
            return ok("INTRO_VIDEO_RESTORED")
        }
        if (!QuizIntroVideo.select(context, id)) return error("INTRO_VIDEO_NOT_FOUND")
        return ok("INTRO_VIDEO_SELECTED")
    }

    fun deleteIntroVideo(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (!QuizIntroVideo.delete(context, id)) return error("INTRO_VIDEO_NOT_FOUND")
        return ok("INTRO_VIDEO_DELETED")
    }

    // ----------------------------------------------------------------- cloud media
    //
    // The catalogue is read-only and a film is fetched only when the parent asks for this one
    // id. These writes are device-local like the intro upload: a bound TV still lets the holder
    // of the editor lease choose its own wallpaper, and the lease is checked by the HTTP layer.

    fun refreshMedia(context: Context): Outcome {
        if (!OfficialMediaCatalog.isConfigured()) return error("CATALOG_UNAVAILABLE")
        OfficialMediaCatalog.requestRefresh(context.applicationContext, force = true)
        return ok("MEDIA_REFRESHING")
    }

    fun downloadMedia(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (id.isEmpty()) return error("MEDIA_NOT_FOUND")
        val roles = PolicyVideoRole.fromOptionalRequest(body.optString("role", ""))
            ?: return error("MEDIA_ROLE_INVALID")
        return when (OfficialMediaDownloader.start(context.applicationContext, id, roles)) {
            is OfficialMediaDownloader.StartResult.Started -> ok("MEDIA_DOWNLOAD_STARTED")
            is OfficialMediaDownloader.StartResult.AlreadyReady -> ok("MEDIA_ALREADY_READY")
            is OfficialMediaDownloader.StartResult.Running -> ok("MEDIA_DOWNLOADING")
            OfficialMediaDownloader.StartResult.Busy -> error("MEDIA_BUSY")
            OfficialMediaDownloader.StartResult.NotFound -> error("MEDIA_NOT_FOUND")
            OfficialMediaDownloader.StartResult.RoleInvalid -> error("MEDIA_ROLE_INVALID")
            OfficialMediaDownloader.StartResult.NoSpace -> error("MEDIA_NO_SPACE")
            OfficialMediaDownloader.StartResult.LibraryFull -> error("MEDIA_LIBRARY_FULL")
        }
    }

    fun cancelMediaDownload(body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (id.isEmpty() || !OfficialMediaDownloader.cancel(id)) {
            return error("MEDIA_NOT_FOUND")
        }
        return ok("MEDIA_CANCELLED")
    }

    fun selectMedia(context: Context, body: JSONObject): Outcome {
        val roles = PolicyVideoRole.fromRequest(body.optString("role", ""))
            ?: return error("MEDIA_ROLE_INVALID")
        val id = body.optString("id", "").trim()
        return when (PolicyVideoLibrary.select(context.applicationContext, roles, id)) {
            PolicyVideoLibrary.EditResult.Ok -> ok("MEDIA_SELECTED")
            PolicyVideoLibrary.EditResult.NotFound -> error("MEDIA_NOT_FOUND")
            PolicyVideoLibrary.EditResult.NotReady -> error("MEDIA_NOT_READY")
            PolicyVideoLibrary.EditResult.Busy -> error("MEDIA_BUSY")
            PolicyVideoLibrary.EditResult.RoleInvalid -> error("MEDIA_ROLE_INVALID")
            PolicyVideoLibrary.EditResult.DeleteFailed -> error("MEDIA_DOWNLOAD_FAILED")
        }
    }

    fun deleteMedia(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (id.isEmpty()) return error("MEDIA_NOT_FOUND")
        return when (PolicyVideoLibrary.delete(context.applicationContext, id)) {
            PolicyVideoLibrary.EditResult.Ok -> ok("MEDIA_DELETED")
            PolicyVideoLibrary.EditResult.NotFound -> error("MEDIA_NOT_FOUND")
            PolicyVideoLibrary.EditResult.Busy -> error("MEDIA_BUSY")
            PolicyVideoLibrary.EditResult.DeleteFailed -> error("MEDIA_DELETE_FAILED")
            PolicyVideoLibrary.EditResult.NotReady,
            PolicyVideoLibrary.EditResult.RoleInvalid -> error("MEDIA_NOT_FOUND")
        }
    }

    fun endSession(body: JSONObject): Outcome {
        if (PolicyModeCoordinator.mode != PolicyMode.LOCAL) return error("LOCAL_ONLY")
        if (!body.optBoolean("confirm", false)) return error("CONFIRM_REQUIRED")
        if (LocalPolicyRuntime.state.value.sessionState != SessionState.ACTIVE) {
            return error("SESSION_NOT_ACTIVE")
        }
        LocalPolicyRuntime.endSessionByParent()
        return ok("SESSION_ENDED")
    }

    // -------------------------------------------------------------------- apps

    fun appsJson(context: Context): JSONArray {
        val array = JSONArray()
        PolicyInstalledApps.scan(context)
            .take(300)
            .forEach { app ->
                array.put(
                    JSONObject()
                        .put("packageName", app.packageName)
                        .put("label", app.label),
                )
            }
        return array
    }

    fun icon(context: Context, packageName: String): ByteArray? {
        if (packageName.length !in 1..255) return null
        return PolicyInstalledApps.iconBytes(context, packageName, 96, 64 * 1024)
    }

    // ------------------------------------------------------------------ helpers

    fun ok(messageKey: String): Outcome =
        Outcome(ok = true, code = "OK", messageKey = messageKey)

    fun error(code: String): Outcome =
        Outcome(ok = false, code = code, messageKey = code)

    private fun conflict(code: String): Outcome = Outcome(
        ok = false,
        code = code,
        messageKey = code,
        retryAfterSeconds = 0L,
    )

    private fun defaultPinCached(): Boolean {
        val now = System.currentTimeMillis()
        if (now - defaultPinCheckedAt < DEFAULT_PIN_CACHE_MS) return defaultPinCached
        defaultPinCheckedAt = now
        defaultPinCached = runCatching { PolicyModeCoordinator.isDefaultPin() }
            .getOrDefault(false)
        return defaultPinCached
    }

    private fun invalidateDefaultPinCache() {
        defaultPinCheckedAt = 0L
    }

    private fun parseRules(json: JSONObject): LocalRules? {
        val weekday = parseWindows(json.optJSONArray("weekdayWindows")) ?: return null
        val weekend = parseWindows(json.optJSONArray("weekendWindows")) ?: return null
        // V29 has exactly one window per day class. Reject a stale page trying to save a
        // second one instead of silently dropping it.
        if (weekday.size != 1 || weekend.size != 1) return null
        val daily = json.optInt("autoDailyLimitMinutes", -1)
        val session = json.optInt("sessionLimitMinutes", -1)
        val rest = json.optInt("restMinutes", -1)
        val buffer = json.optInt("sessionScreenOffBufferMinutes", -1)
        if (daily < 0 || session < 0 || rest < 0 || buffer < 0) return null
        val allApps = json.optBoolean("allApps", false)
        val allowed = LinkedHashSet<String>()
        if (allApps) {
            allowed.add(ALL_VALID_APP_SENTINEL)
        } else {
            val array = json.optJSONArray("allowedPackages") ?: JSONArray()
            for (i in 0 until minOf(array.length(), 500)) {
                val value = array.optString(i, "").trim()
                if (value.isNotEmpty()) allowed.add(value)
            }
        }
        val study = LinkedHashSet<String>()
        val studyArray = json.optJSONArray("studyPackages") ?: JSONArray()
        for (i in 0 until minOf(studyArray.length(), 500)) {
            val value = studyArray.optString(i, "").trim()
            if (value.isNotEmpty()) study.add(value)
        }
        return LocalRules(
            weekdayWindows = weekday,
            weekendWindows = weekend,
            autoDailyLimitMinutes = daily,
            sessionLimitMinutes = session,
            restMinutes = rest,
            sessionScreenOffBufferMinutes = buffer,
            allowedPackages = allowed,
            studyPackages = study,
        ).normalized()
    }

    private fun parseWindows(array: JSONArray?): List<LocalTimeWindow>? {
        if (array == null) return null
        if (array.length() > LocalRules.MAX_WINDOWS_PER_DAY) return null
        val windows = ArrayList<LocalTimeWindow>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: return null
            val start = item.optInt("start", -1)
            val end = item.optInt("end", -1)
            if (start < 0 || end < 0) return null
            windows.add(LocalTimeWindow(start, end))
        }
        return windows
    }

    private const val ALL_VALID_APP_SENTINEL = "all_valid_app"
}
