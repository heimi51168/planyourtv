package app.orchard.tv.policy.web

import android.content.Context
import android.util.Log
import app.orchard.tv.BuildConfig
import app.orchard.tv.policy.FamilyAvatarStore
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.UserConfigPinSettings
import app.orchard.tv.policy.media.PolicyAudioLibrary
import app.orchard.tv.policy.media.PolicyVideoLibrary
import app.orchard.tv.policy.media.PolicyVideoValidation
import app.orchard.tv.quiz.QuizIntroVideo
import com.planyourtv.policy.LocalPin
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.FileInputStream

/**
 * HTTP surface of the web parent center.
 *
 * The page itself is the existing `/quiz` document (the parent center opens as an in-page
 * view), so this object only serves its scripts and the `/api/parent/…` endpoints. Every
 * request is same-origin, POSTs must carry `X-Parent: 1` and JSON, and everything except the
 * read-only gate/bootstrap also requires the single-editor lease. The actual policy checks live
 * in [ParentWebAccess] and [ParentCenterActions]; this layer only maps them to HTTP.
 */
object ParentWebAdmin {
    private const val TAG = "ParentWebAdmin"
    private const val MAX_JSON_BYTES = 32 * 1024
    private const val EDITOR_HEADER = "x-parent-editor-token"
    private const val QUIZ_HEADER = "x-quiz-admin-token"
    private const val TAKEOVER_HEADER = "x-parent-takeover"
    private const val SENSITIVE_HEADER = "x-parent-sensitive-token"
    private const val INTRO_VIDEO_PATH = "/api/parent/intro-video"
    private const val AUDIO_UPLOAD_PATH = "/api/parent/audio"
    private const val SUBJECT_AVATAR_PATH = "/api/parent/subjects/avatar"
    private const val PRESET_AVATAR_PATH = "/parent-avatar"

    /**
     * NanoHTTPD keeps one [IHTTPSession] for the whole keep-alive connection, so a POST body
     * that no handler reads stays on the stream and corrupts the next request
     * ("HTTP verb {}GET unhandled"). The page sends `{}` with acquire, heartbeat, and release;
     * cache every JSON POST body before a route can return early. The raw film upload reads
     * its own stream and stays uncached.
     */
    private val requestJson = ThreadLocal<JSONObject?>()
    private val requestJsonCached = ThreadLocal<Boolean>()

    /**
     * NanoHTTPD 2.3.1 has no 423 constant, and it writes the status line from
     * [Response.IStatus.getDescription] alone. The description must therefore carry the numeric
     * code, otherwise the line is `HTTP/1.1 Locked` and clients cannot parse a status at all.
     */
    private val WEB_LOCAL_ONLY_STATUS = object : Response.IStatus {
        override fun getRequestStatus(): Int = 423
        override fun getDescription(): String = "423 Locked"
    }

    fun handle(context: Context, session: IHTTPSession, path: String): Response? {
        if (!ParentWebCenter.ENABLED) return null
        if (!path.startsWith("/api/parent/") && path != "/parent.js" && path != "/parent.css" &&
            path != "/parent-kids-default.jpg" && path != PRESET_AVATAR_PATH
        ) {
            return null
        }
        return try {
            if (session.method == Method.POST && path != INTRO_VIDEO_PATH &&
                path != AUDIO_UPLOAD_PATH && path != SUBJECT_AVATAR_PATH
            ) {
                requestJson.set(readJsonBody(session))
                requestJsonCached.set(true)
            }
            if (!sameOrigin(session)) return error(Response.Status.FORBIDDEN, "ORIGIN")
            when {
                session.method == Method.GET && path == "/parent.js" ->
                    asset(context, "web/parent.js", "application/javascript; charset=utf-8")
                session.method == Method.GET && path == "/parent.css" ->
                    asset(context, "web/parent.css", "text/css; charset=utf-8")
                // A bundled JPEG must be served as bytes; reading it through the text asset
                // helper would corrupt it.
                session.method == Method.GET && path == "/parent-kids-default.jpg" ->
                    bytes(
                        Response.Status.OK,
                        "image/jpeg",
                        context.assets.open("web/parent-kids-default.jpg").use { it.readBytes() },
                    )
                // Built-in portraits are public artwork, not family data. Serving them from
                // the same bundled drawables keeps the phone and TV faces identical.
                session.method == Method.GET && path == PRESET_AVATAR_PATH ->
                    presetAvatar(context, session)
                session.method == Method.GET && path == "/api/parent/bootstrap" -> bootstrap()
                session.method == Method.GET && path == "/api/parent/gate" -> gate()
                // V29 removes the old entry flows. Cached pages get a clear terminal answer
                // instead of a generic 404.
                session.method == Method.POST && path == "/api/parent/unlock" ->
                    jsonError(Response.Status.GONE, "LOCAL_WEB_LOGIN")
                session.method == Method.POST && path == "/api/parent/verify" ->
                    jsonError(Response.Status.GONE, "LOCAL_WEB_LOGIN")
                session.method == Method.POST && path == "/api/parent/pair" ->
                    jsonError(Response.Status.GONE, "LOCAL_WEB_LOGIN")
                // Every remaining API route is web-only. The body was already cached above so
                // the keep-alive stream stays clean even when this gate rejects the request.
                path.startsWith("/api/parent/") &&
                    path != "/api/parent/release" &&
                    !ParentWebAccess.webAllowed() -> webLocalOnly()
                session.method == Method.POST && path == "/api/parent/acquire" -> acquire(session)
                session.method == Method.POST && path == "/api/parent/heartbeat" -> heartbeat(session)
                session.method == Method.POST && path == "/api/parent/release" -> release(session)
                session.method == Method.POST && path == "/api/parent/sensitive/verify" ->
                    sensitiveVerify(session)
                session.method == Method.POST && path == "/api/parent/sensitive/release" ->
                    sensitiveRelease(session)
                session.method == Method.POST && path == "/api/parent/control" -> control(session)
                // Forgot password: the only write reachable before login, gated by the same
                // LOCAL/unbound check and by the recovery code's own persisted lockout.
                session.method == Method.POST && path == "/api/parent/recover" -> recover(session)
                session.method == Method.GET && path == "/api/parent/snapshot" -> snapshot(context, session)
                session.method == Method.GET && path == "/api/parent/device" ->
                    deviceStatus(context, session)
                session.method == Method.GET && path == "/api/parent/intro-video" ->
                    introVideoStatus(context, session)
                session.method == Method.POST && path == "/api/parent/intro-video" ->
                    uploadIntroVideo(context, session)
                session.method == Method.POST && path == "/api/parent/intro-video/select" ->
                    write(context, session) { ParentCenterActions.selectIntroVideo(context, it) }
                session.method == Method.POST && path == "/api/parent/intro-video/delete" ->
                    write(context, session) { ParentCenterActions.deleteIntroVideo(context, it) }
                session.method == Method.GET && path == "/api/parent/media" ->
                    media(context, session)
                session.method == Method.GET && path == "/api/parent/media/poster" ->
                    mediaPoster(context, session)
                // A ticket is the only header-free way for a <video> element to prove the
                // editor lease; the stream route then serves the local file with Range.
                session.method == Method.POST && path == "/api/parent/media/ticket" ->
                    mediaTicket(context, session)
                session.method == Method.GET && path == "/api/parent/media/file" ->
                    mediaFile(context, session)
                // Audio ids can collide with a local video id, so music has its own ticket
                // kind and stream route instead of sharing the video namespace.
                session.method == Method.POST && path == "/api/parent/media/audio/ticket" ->
                    audioTicket(context, session)
                session.method == Method.GET && path == "/api/parent/media/audio/file" ->
                    audioFile(context, session)
                session.method == Method.POST && path == "/api/parent/media/refresh" ->
                    write(context, session) { ParentCenterActions.refreshMedia(context) }
                session.method == Method.POST && path == "/api/parent/media/download" ->
                    write(context, session) { ParentCenterActions.downloadMedia(context, it) }
                session.method == Method.POST && path == "/api/parent/media/cancel" ->
                    write(context, session) { ParentCenterActions.cancelMediaDownload(it) }
                session.method == Method.POST && path == "/api/parent/media/select" ->
                    write(context, session) { ParentCenterActions.selectMedia(context, it) }
                session.method == Method.POST && path == "/api/parent/media/playlist" ->
                    write(context, session) { ParentCenterActions.saveMediaPlaylist(context, it) }
                // V56 one-save pages: the wallpaper draft and quiz-intro draft each commit as
                // one request, so the page does not need a chain of selections.
                session.method == Method.POST && path == "/api/parent/media/wallpaper" ->
                    write(context, session) { ParentCenterActions.saveMediaWallpaper(context, it) }
                session.method == Method.POST && path == "/api/parent/media/quiz" ->
                    write(context, session) { ParentCenterActions.saveQuizIntro(context, it) }
                session.method == Method.POST && path == "/api/parent/media/delete" ->
                    write(context, session) { ParentCenterActions.deleteMedia(context, it) }
                session.method == Method.POST && path == "/api/parent/media/sound" ->
                    write(context, session) { ParentCenterActions.setMediaSound(context, it) }
                // A raw audio body, streamed into the local music library like a wallpaper.
                session.method == Method.POST && path == AUDIO_UPLOAD_PATH ->
                    uploadAudio(context, session)
                session.method == Method.POST && path == "/api/parent/media/audio/refresh" ->
                    write(context, session) { ParentCenterActions.refreshAudio(context) }
                session.method == Method.POST && path == "/api/parent/media/audio/download" ->
                    write(context, session) { ParentCenterActions.downloadAudio(context, it) }
                session.method == Method.POST && path == "/api/parent/media/audio/cancel" ->
                    write(context, session) { ParentCenterActions.cancelAudioDownload(it) }
                session.method == Method.POST && path == "/api/parent/media/audio/select" ->
                    write(context, session) { ParentCenterActions.selectAudio(context, it) }
                session.method == Method.POST && path == "/api/parent/media/audio/delete" ->
                    write(context, session) { ParentCenterActions.deleteAudio(context, it) }
                session.method == Method.GET && path == "/api/parent/apps" -> apps(context, session)
                // V58 full fruit ledger. The snapshot only carries per-member summaries; the
                // history can be hundreds of rows, so the page fetches one member on demand.
                session.method == Method.GET && path == "/api/parent/fruit/ledger" ->
                    fruitLedger(session)
                session.method == Method.GET && path == "/api/parent/icon" -> icon(context, session)
                // A family photo is editor-only data, so this route requires the same lease as
                // the rest of the page. The <img> tag cannot send that header; the page fetches
                // the bytes through host.blob() and renders them from an object URL.
                session.method == Method.GET && path == "/api/parent/avatar" ->
                    memberAvatar(context, session)
                // Family management is behind its own fresh parent-PIN proof. Hiding the entry in
                // the page is not an authorization boundary: add, edit, photo and delete all
                // repeat the check here, including the raw avatar upload below.
                session.method == Method.POST && path == "/api/parent/subjects/add" ->
                    write(
                        context,
                        session,
                        sensitiveScope = ParentSensitiveAuth.Scope.FAMILY_EDIT,
                    ) { ParentCenterActions.addMember(it) }
                session.method == Method.POST && path == "/api/parent/subjects/profile" ->
                    write(
                        context,
                        session,
                        sensitiveScope = ParentSensitiveAuth.Scope.FAMILY_EDIT,
                    ) { ParentCenterActions.updateMemberProfile(it) }
                session.method == Method.POST && path == SUBJECT_AVATAR_PATH ->
                    uploadMemberAvatar(context, session)
                session.method == Method.POST && path == "/api/parent/subjects/avatar/delete" ->
                    write(
                        context,
                        session,
                        sensitiveScope = ParentSensitiveAuth.Scope.FAMILY_EDIT,
                    ) { ParentCenterActions.deleteMemberAvatar(it) }
                // Removing a child is destructive and irreversible from the page. The action
                // still repeats the active/default/cloud guards.
                session.method == Method.POST && path == "/api/parent/subjects/delete" ->
                    write(
                        context,
                        session,
                        sensitiveScope = ParentSensitiveAuth.Scope.FAMILY_EDIT,
                    ) { ParentCenterActions.deleteMember(it) }
                // Remote member switching shares the TV's lock-screen/busy gate; the parent
                // center login plus editor lease is the authentication on this side.
                session.method == Method.POST && path == "/api/parent/subjects/activate" ->
                    write(context, session) { ParentCenterActions.activateSubject(it) }
                // V59: user-configuration and fruit-ledger edits share one USER_EDIT proof.
                // The V47 switch decides whether that proof is asked for at all; when it is
                // on, one PIN per visit covers every save inside the page. System routes
                // below keep their scope unconditionally.
                session.method == Method.POST && path == "/api/parent/rules" ->
                    write(
                        context,
                        session,
                        sensitiveScope = userEditSensitiveScope(),
                    ) { ParentCenterActions.setRules(it) }
                // V58 fruit bookkeeping is user configuration too, so it follows the same
                // switch instead of keeping a separate always-on PIN. Every route below also
                // refuses a non-LOCAL data source.
                session.method == Method.POST && path == "/api/parent/fruit/credit" ->
                    write(
                        context,
                        session,
                        sensitiveScope = userEditSensitiveScope(),
                    ) { ParentCenterActions.creditFruit(it) }
                session.method == Method.POST && path == "/api/parent/fruit/expense" ->
                    write(
                        context,
                        session,
                        sensitiveScope = userEditSensitiveScope(),
                    ) { ParentCenterActions.expenseFruit(it) }
                session.method == Method.POST && path == "/api/parent/fruit/expense/void" ->
                    write(
                        context,
                        session,
                        sensitiveScope = userEditSensitiveScope(),
                    ) { ParentCenterActions.voidFruitExpense(it) }
                session.method == Method.POST && path == "/api/parent/fruit/void" ->
                    write(
                        context,
                        session,
                        sensitiveScope = userEditSensitiveScope(),
                    ) { ParentCenterActions.voidFruit(it) }
                session.method == Method.POST && path == "/api/parent/fruit/settings" ->
                    write(
                        context,
                        session,
                        sensitiveScope = userEditSensitiveScope(),
                    ) { ParentCenterActions.setFruitSettings(it) }
                session.method == Method.POST &&
                    path == "/api/parent/fruit/quick-projects/delete" ->
                    write(
                        context,
                        session,
                        sensitiveScope = userEditSensitiveScope(),
                    ) { ParentCenterActions.deleteFruitQuickProject(it) }
                // Turning the extra user-config prompts off is a deliberate reduction of
                // protection, so it needs a single-use USER_CONFIG proof. Turning them back
                // on only restores protection and needs no proof. Two explicit routes keep
                // the scope independent of a request body.
                session.method == Method.POST && path == "/api/parent/user-config-pin/disable" ->
                    write(
                        context,
                        session,
                        sensitiveScope = ParentSensitiveAuth.Scope.USER_CONFIG,
                        consumeSensitive = true,
                    ) { ParentCenterActions.setUserConfigPin(false) }
                session.method == Method.POST && path == "/api/parent/user-config-pin/enable" ->
                    write(context, session) { ParentCenterActions.setUserConfigPin(true) }
                // V57: enabling the device-wide TV member-switch policy (and editing its waiver
                // window) only adds protection, so the page login plus editor lease authorize
                // it. Refusing this behind FAMILY_EDIT made turning the switch on require an
                // extra PIN even though the parent was already editing the device settings.
                session.method == Method.POST && path == "/api/parent/switch-protection" ->
                    write(context, session) { ParentCenterActions.enableSwitchProtection(it) }
                // Turning protection off is the one direction that needs the same fresh parent
                // proof as family edits; the unprotected route above rejects `required=false`.
                session.method == Method.POST && path == "/api/parent/switch-protection/disable" ->
                    write(
                        context,
                        session,
                        sensitiveScope = ParentSensitiveAuth.Scope.FAMILY_EDIT,
                    ) { ParentCenterActions.disableSwitchProtection() }
                // Turning child mode off or creating its first PIN changes what the web page
                // itself is authorized by, so those stay TV-only.
                session.method == Method.POST && path == "/api/parent/child-mode" ->
                    jsonError(Response.Status.FORBIDDEN, "TV_ONLY")
                session.method == Method.POST && path == "/api/parent/setup-pin" ->
                    jsonError(Response.Status.FORBIDDEN, "TV_ONLY")
                session.method == Method.POST && path == "/api/parent/pin" ->
                    write(context, session, pinRateLimit = true) {
                        ParentCenterActions.changePin(it)
                    }
                session.method == Method.POST && path == "/api/parent/auto-update" ->
                    write(context, session) { ParentCenterActions.setAutoUpdate(it) }
                // The version row in "More" shows the TV build and lets a parent start the
                // same download/install the TV update page uses. No extra PIN: both actions
                // are reversible until the system installer's own confirmation.
                session.method == Method.POST && path == "/api/parent/update/check" ->
                    write(context, session) { ParentCenterActions.requestUpdateCheck() }
                session.method == Method.POST && path == "/api/parent/update/install" ->
                    write(context, session) { ParentCenterActions.installUpdate() }
                // Rules, source visibility, the quiz-entry display switch and the
                // system-entry/uninstall switch are user configuration. They follow the same V47
                // switch, so turning the prompts off really is all-or-none for this area; the
                // server still repeats that check because hiding the UI entry is not an
                // authorization boundary.
                session.method == Method.POST && path == "/api/parent/source-entries" ->
                    write(
                        context,
                        session,
                        sensitiveScope = userEditSensitiveScope(),
                    ) { ParentCenterActions.setSourceEntries(it) }
                session.method == Method.POST && path == "/api/parent/quiz-entry" ->
                    write(
                        context,
                        session,
                        sensitiveScope = userEditSensitiveScope(),
                    ) { ParentCenterActions.setQuizEntry(it) }
                session.method == Method.POST && path == "/api/parent/system-entries" ->
                    write(
                        context,
                        session,
                        sensitiveScope = userEditSensitiveScope(),
                    ) { ParentCenterActions.setSystemEntries(it) }
                // The old switch deleted an unstarted challenge; a cached page must not
                // reach that behavior through a deprecated route.
                session.method == Method.POST && path == "/api/parent/quiz" ->
                    jsonError(Response.Status.GONE, "QUIZ_SWITCH_REMOVED")
                session.method == Method.POST && path == "/api/parent/end-session" ->
                    write(context, session) { ParentCenterActions.endSession(it) }
                else -> error(Response.Status.NOT_FOUND, "NOT_FOUND")
            }
        } catch (error: Throwable) {
            Log.w(TAG, "parent request failed: ${error.javaClass.simpleName}")
            this.error(Response.Status.INTERNAL_ERROR, "INTERNAL")
        } finally {
            requestJson.remove()
            requestJsonCached.remove()
        }
    }

    private fun bootstrap(): Response {
        val gate = ParentWebAccess.gate()
        val body = JSONObject()
            .put("ok", true)
            .put("enabled", true)
            .put("webAvailable", gate.webAvailable)
            .put("mode", gate.mode.name)
            .put("reason", gate.reason)
            .put("gate", gateJson())
        // A blocked page only needs the disabled state. Do not expose lease details it has no
        // way to use, and never include an editor token here.
        if (gate.webAvailable) {
            body.put("editorOwner", ParentEditorLease.state().owner.name)
        }
        return json(Response.Status.OK, body)
    }

    private fun gate(): Response = json(
        Response.Status.OK,
        JSONObject().put("ok", true).put("gate", gateJson()),
    )

    private fun gateJson(): JSONObject {
        val gate = ParentWebAccess.gate()
        return JSONObject()
            .put("status", gate.status.name)
            .put("mode", gate.mode.name)
            .put("reason", gate.reason)
            .put("webAvailable", gate.webAvailable)
            .put("quizAdminRequired", gate.quizAdminRequired)
            // Kept false for a cached V27 page that still reads the old fields.
            .put("canUnlock", false)
            .put("canVerify", false)
            .put("needsPair", false)
            .put("proofRemainingMs", 0L)
    }

    private fun acquire(session: IHTTPSession): Response {
        if (!requirePostHeader(session)) {
            return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        }
        val takeover = session.headers[TAKEOVER_HEADER] == "1"
        return when (val result = ParentWebAccess.acquire(quizToken(session), takeover)) {
            is ParentWebAccess.AcquireOutcome.Ok -> {
                // Only one page edits at a time. A takeover must also end any stream the
                // previous editor left running, not just its JSON access.
                ParentMediaTickets.revokeAll()
                json(
                    Response.Status.OK,
                    JSONObject()
                        .put("ok", true)
                        .put("editorToken", result.editorToken)
                        .put("expiresAtMs", result.expiresAtMs)
                        .put("quizBound", result.quizBound)
                        .put("gate", gateJson()),
                )
            }
            is ParentWebAccess.AcquireOutcome.Rejected -> when (result.code) {
                "WEB_LOCAL_ONLY" -> webLocalOnly()
                "WEB_EDITING" -> jsonError(Response.Status.CONFLICT, result.code)
                else -> jsonError(Response.Status.FORBIDDEN, result.code)
            }
        }
    }

    private fun heartbeat(session: IHTTPSession): Response {
        if (!requirePostHeader(session)) {
            return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        }
        val check = editorCheck(session)
        val editorResult = check.second
        if (editorResult is ParentWebAccess.EditorCheck.Failed) {
            return editorError(editorResult.code)
        }
        val ok = editorResult as ParentWebAccess.EditorCheck.Ok
        val ui = app.orchard.tv.policy.PolicyModeCoordinator.state.value
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("remainingMs", ok.remainingMs)
                .put("mode", ui.mode.name)
                .put("sessionState", ui.sessionState.name)
                .put("guardState", ui.guardState.name)
                .put("rulesRevision", app.orchard.tv.policy.LocalPolicyRuntime.rulesEpoch)
                .put("modeRevision", ParentCenterActions.modeRevision())
                .put("control", ParentControlActions.statusJson()),
        )
    }

    /**
     * Desired-state commands from the control page. The editor token is only a cache and
     * confirmation partition; [ParentControlActions] does the live-state checks itself. A
     * confirmation challenge is an HTTP 409 but a well-formed `ok: true` body, so the page can
     * tell "ask the parent to confirm" apart from a transport error.
     */
    private fun control(session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        if (session.headers["content-type"]?.startsWith("application/json") != true) {
            return jsonError(Response.Status.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE")
        }
        val check = editorCheck(session)
        val editorResult = check.second
        if (editorResult is ParentWebAccess.EditorCheck.Failed) return editorError(editorResult.code)
        val editor = check.first ?: return jsonError(Response.Status.UNAUTHORIZED, "EDITOR_REQUIRED")
        val body = cachedJsonBody(session)
            ?: return jsonError(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val outcome = ParentControlActions.handle(
            body = body,
            editorToken = editor,
            sensitiveToken = session.headers[SENSITIVE_HEADER].orEmpty(),
        )
        val status = when {
            outcome.result == "CONFIRM_REQUIRED" -> Response.Status.CONFLICT
            outcome.ok -> Response.Status.OK
            else -> when (outcome.code) {
                "STALE_CONFIRM", "CONFIRM_EXPIRED", "STATE_CHANGED" ->
                    Response.Status.CONFLICT
                "SENSITIVE_REQUIRED", "SENSITIVE_EXPIRED", "SENSITIVE_SCOPE" ->
                    Response.Status.FORBIDDEN
                "PIN_LOCKED" -> Response.Status.TOO_MANY_REQUESTS
                else -> Response.Status.BAD_REQUEST
            }
        }
        return json(status, ParentControlActions.responseJson(outcome))
    }

    /**
     * Re-verifies the parent PIN for one scope. This is intentionally separate from the quiz
     * login: a phone left logged in still proves the PIN before time-rule edits or a temporary
     * exit. The PIN and token never appear in logs or the response snapshot.
     */
    private fun sensitiveVerify(session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        if (session.headers["content-type"]?.startsWith("application/json") != true) {
            return jsonError(Response.Status.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE")
        }
        val check = editorCheck(session)
        val editorResult = check.second
        if (editorResult is ParentWebAccess.EditorCheck.Failed) return editorError(editorResult.code)
        val editor = check.first ?: return jsonError(Response.Status.UNAUTHORIZED, "EDITOR_REQUIRED")
        val body = cachedJsonBody(session)
            ?: return jsonError(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val outcome = ParentSensitiveAuth.verify(
            scopeValue = body.optString("scope", ""),
            pin = body.optString("pin", ""),
            editorToken = editor,
        )
        return when (outcome) {
            is ParentSensitiveAuth.VerifyResult.Ok -> json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put("scope", outcome.scope.name)
                    .put("token", outcome.token)
                    .put("expiresAtMs", outcome.expiresAtMs),
            )
            is ParentSensitiveAuth.VerifyResult.Failed -> {
                val response = JSONObject()
                    .put("ok", false)
                    .put("error", outcome.code)
                    .put("messageKey", outcome.code)
                if (outcome.retryAfterSeconds > 0L) {
                    response.put("retryAfterSeconds", outcome.retryAfterSeconds)
                }
                val status = if (outcome.code == "PIN_LOCKED") {
                    Response.Status.TOO_MANY_REQUESTS
                } else {
                    Response.Status.BAD_REQUEST
                }
                json(status, response)
            }
        }
    }

    /**
     * Drops one page-held proof, or all of them for the editor on logout. Deliberately does
     * not require a live lease: the page calls this while closing, and the TTL is the fallback.
     */
    private fun sensitiveRelease(session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        val editor = session.headers[EDITOR_HEADER]
        val body = cachedJsonBody(session)
        val token = body?.optString("token", "").orEmpty()
        if (token.isNotBlank()) ParentSensitiveAuth.release(token, editor)
        return json(Response.Status.OK, JSONObject().put("ok", true))
    }

    private fun release(session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        // A release may arrive after the lease already expired; it is best effort.
        val editor = session.headers[EDITOR_HEADER]
        ParentSensitiveAuth.releaseAll(editor)
        ParentMediaTickets.revokeEditor(editor)
        ParentWebAccess.release(editor)
        return json(Response.Status.OK, JSONObject().put("ok", true))
    }

    /**
     * Pre-login password reset. Deliberately does not take an editor lease or a quiz token:
     * the parent may be here precisely because the PIN is forgotten or locked. The action
     * verifies the one-time recovery code itself and returns no snapshot, so an unauthenticated
     * caller learns nothing about the family's rules.
     */
    private fun recover(session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        if (session.headers["content-type"]?.startsWith("application/json") != true) {
            return jsonError(Response.Status.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE")
        }
        val body = cachedJsonBody(session)
            ?: return jsonError(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val outcome = ParentCenterActions.recoverPin(body)
        val response = JSONObject()
            .put("ok", outcome.ok)
            .put("code", outcome.code)
            .put("messageKey", outcome.messageKey)
        if (!outcome.ok) {
            response.put("error", outcome.code)
            if (outcome.retryAfterSeconds > 0) {
                response.put("retryAfterSeconds", outcome.retryAfterSeconds)
            }
            return json(outcomeStatus(outcome.code), response)
        }
        return json(Response.Status.OK, response)
    }

    private fun snapshot(context: Context, session: IHTTPSession): Response {
        val check = editorCheck(session)
        val result = check.second
        if (result is ParentWebAccess.EditorCheck.Failed) return editorError(result.code)
        val remaining = (result as ParentWebAccess.EditorCheck.Ok).remainingMs
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("editorRemainingMs", remaining)
                .put("snapshot", ParentCenterActions.snapshot(context)),
        )
    }

    /**
     * Read-only diagnostics for the device card on the Control page. It requires the editor
     * lease like the snapshot: a second phone that only holds the quiz login must not be able
     * to poll this TV's memory and permission state.
     */
    private fun deviceStatus(context: Context, session: IHTTPSession): Response {
        val check = editorCheck(session)
        val result = check.second
        if (result is ParentWebAccess.EditorCheck.Failed) return editorError(result.code)
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("device", ParentDeviceStatus.snapshot(context)),
        )
    }

    private fun introVideoStatus(context: Context, session: IHTTPSession): Response {
        val check = editorCheck(session)
        val result = check.second
        if (result is ParentWebAccess.EditorCheck.Failed) return editorError(result.code)
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("introVideo", QuizIntroVideo.snapshot(context)),
        )
    }

    /** The cloud catalogue, local films and download progress for the media page's poll. */
    private fun media(context: Context, session: IHTTPSession): Response {
        val check = editorCheck(session)
        val result = check.second
        if (result is ParentWebAccess.EditorCheck.Failed) return editorError(result.code)
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("media", PolicyVideoLibrary.snapshot(context)),
        )
    }

    /**
     * Covers of local uploads/downloads. The page loads them with the editor header and makes
     * a blob URL, so they stay behind the same lease as the rest of the parent center and the
     * snapshot does not carry tens of kilobytes of base64 on every progress poll.
     */
    private fun mediaPoster(context: Context, session: IHTTPSession): Response {
        val check = editorCheck(session)
        val result = check.second
        if (result is ParentWebAccess.EditorCheck.Failed) return editorError(result.code)
        val id = session.parameters["id"]?.firstOrNull().orEmpty()
        val bytes = PolicyVideoLibrary.poster(context, id)
            ?: return jsonError(Response.Status.NOT_FOUND, "MEDIA_NOT_FOUND")
        return bytes(Response.Status.OK, "image/jpeg", bytes)
    }

    /**
     * Exchanges the editor lease for a short-lived ticket that a native <video> can carry in
     * its URL. Only complete local files qualify; a cloud film that has not been downloaded is
     * previewed by the phone directly from the catalogue URL and gets no ticket.
     */
    private fun mediaTicket(context: Context, session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        if (session.headers["content-type"]?.startsWith("application/json") != true) {
            return jsonError(Response.Status.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE")
        }
        val check = editorCheck(session)
        val result = check.second
        if (result is ParentWebAccess.EditorCheck.Failed) return editorError(result.code)
        val editor = check.first ?: return jsonError(Response.Status.UNAUTHORIZED, "EDITOR_REQUIRED")
        val id = cachedJsonBody(session)?.optString("id", "").orEmpty()
        if (PolicyVideoLibrary.playbackFile(context, id) == null) {
            return jsonError(Response.Status.NOT_FOUND, "MEDIA_NOT_FOUND")
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("ticket", ParentMediaTickets.mint(editor, id)),
        )
    }

    /**
     * Streams one published local film to the parent page. Browsers seek with Range requests and
     * mobile Safari will not play without 206 support. This route never contacts the cloud: an
     * un-downloaded catalog film is played by the phone from its own URL, and the TV keeps the
     * cache-only execution rule intact.
     */
    private fun mediaFile(context: Context, session: IHTTPSession): Response {
        val id = session.parameters["id"]?.firstOrNull().orEmpty()
        val ticket = session.parameters["ticket"]?.firstOrNull().orEmpty()
        if (!ParentMediaTickets.check(ticket, id, kind = ParentMediaTickets.KIND_MEDIA)) {
            return jsonError(Response.Status.FORBIDDEN, "MEDIA_TICKET_REQUIRED")
        }
        val playback = PolicyVideoLibrary.playbackFile(context, id)
            ?: return jsonError(Response.Status.NOT_FOUND, "MEDIA_NOT_FOUND")
        return streamLocalFile(session, playback.file, playback.mime)
    }

    /**
     * The same header-free ticket trade as a film, bound to the audio namespace. Only complete
     * local uploads and verified downloads qualify; an un-downloaded cloud track is downloaded
     * first and can then be previewed from the TV.
     */
    private fun audioTicket(context: Context, session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        if (session.headers["content-type"]?.startsWith("application/json") != true) {
            return jsonError(Response.Status.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE")
        }
        val check = editorCheck(session)
        val result = check.second
        if (result is ParentWebAccess.EditorCheck.Failed) return editorError(result.code)
        val editor = check.first ?: return jsonError(Response.Status.UNAUTHORIZED, "EDITOR_REQUIRED")
        val id = cachedJsonBody(session)?.optString("id", "").orEmpty()
        if (PolicyAudioLibrary.playbackFile(context, id) == null) {
            return jsonError(Response.Status.NOT_FOUND, "AUDIO_NOT_FOUND")
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("ticket", ParentMediaTickets.mint(
                    editor,
                    id,
                    kind = ParentMediaTickets.KIND_AUDIO,
                )),
        )
    }

    private fun audioFile(context: Context, session: IHTTPSession): Response {
        val id = session.parameters["id"]?.firstOrNull().orEmpty()
        val ticket = session.parameters["ticket"]?.firstOrNull().orEmpty()
        if (!ParentMediaTickets.check(ticket, id, kind = ParentMediaTickets.KIND_AUDIO)) {
            return jsonError(Response.Status.FORBIDDEN, "AUDIO_TICKET_REQUIRED")
        }
        val playback = PolicyAudioLibrary.playbackFile(context, id)
            ?: return jsonError(Response.Status.NOT_FOUND, "AUDIO_NOT_FOUND")
        return streamLocalFile(session, playback.file, playback.mime)
    }

    /** Range-aware local file streaming shared by video and audio previews. */
    private fun streamLocalFile(session: IHTTPSession, file: java.io.File, mime: String): Response {
        val total = file.length()
        if (total <= 0L) return jsonError(Response.Status.NOT_FOUND, "MEDIA_NOT_FOUND")
        return when (val range = parseByteRange(session.headers["range"], total)) {
            ByteRange.Unsatisfiable -> bytes(
                Response.Status.RANGE_NOT_SATISFIABLE,
                mime,
                ByteArray(0),
            ).apply { addHeader("Content-Range", "bytes */$total") }
            is ByteRange.Partial -> {
                val input = FileInputStream(file).apply { channel.position(range.start) }
                NanoHTTPD.newFixedLengthResponse(
                    Response.Status.PARTIAL_CONTENT,
                    mime,
                    BoundedInputStream(input, range.length),
                    range.length,
                ).apply {
                    addHeader("Content-Range", "bytes ${range.start}-${range.endInclusive}/$total")
                    addHeader("Accept-Ranges", "bytes")
                }.let(::decorate)
            }
            ByteRange.Whole -> NanoHTTPD.newFixedLengthResponse(
                Response.Status.OK,
                mime,
                FileInputStream(file),
                total,
            ).apply {
                addHeader("Accept-Ranges", "bytes")
            }.let(::decorate)
        }
    }

    /**
     * A raw video or picture body, streamed straight into the local library instead of being
     * buffered as JSON. The declared length is checked before any bytes are allocated or read;
     * the editor lease is checked before the body is consumed, so an unauthorized client never
     * gets to fill the TV's storage. Pictures may be up to 12 MiB on the wire and are normalised
     * to one 4 MiB JPEG before publishing; a video may be up to 128 MiB and has no duration cap.
     */
    private fun uploadIntroVideo(context: Context, session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        val check = editorCheck(session)
        val editor = check.second
        if (editor is ParentWebAccess.EditorCheck.Failed) return editorError(editor.code)

        val declared = session.headers["content-length"]?.toLongOrNull()
            ?: return jsonError(Response.Status.LENGTH_REQUIRED, "LENGTH_REQUIRED")
        if (declared <= 0L) return jsonError(Response.Status.BAD_REQUEST, "INTRO_VIDEO_INVALID")
        // One raw cap for both kinds. QuizIntroVideo.receive() then applies the narrower image
        // and video limits from the extension, so a large picture still gets its own error.
        if (declared > PolicyVideoValidation.MAX_REQUEST_BYTES) {
            return jsonError(Response.Status.PAYLOAD_TOO_LARGE, "MEDIA_TOO_LARGE")
        }
        val name = session.parameters["name"]?.firstOrNull().orEmpty()
        return when (val result = QuizIntroVideo.receive(context, session.inputStream, declared, name)) {
            is QuizIntroVideo.ReceiveResult.Ok -> {
                Log.i(
                    TAG,
                    "local media stored: ${result.entry.kind.wire}, ${result.entry.sizeBytes} bytes",
                )
                json(
                    Response.Status.OK,
                    JSONObject()
                        .put("ok", true)
                        .put("uploadedId", result.entry.id)
                        .put("kind", result.entry.kind.wire)
                        .put("snapshot", ParentCenterActions.snapshot(context)),
                )
            }
            is QuizIntroVideo.ReceiveResult.Rejected -> {
                // The code is enough to diagnose a failed upload from logcat; never log the name.
                Log.i(TAG, "local media rejected: ${result.code}")
                introVideoError(result.code)
            }
        }
    }

    private fun introVideoError(code: String): Response {
        val status = when (code) {
            "INTRO_VIDEO_TOO_LARGE", "INTRO_IMAGE_TOO_LARGE" -> Response.Status.PAYLOAD_TOO_LARGE
            "INTRO_VIDEO_NO_SPACE" -> Response.Status.SERVICE_UNAVAILABLE
            "INTRO_VIDEO_BAD_FORMAT", "INTRO_VIDEO_BAD_CODEC", "INTRO_VIDEO_HEVC",
            "INTRO_VIDEO_UNSUPPORTED_CODEC", "INTRO_VIDEO_BAD_RATIO", "INTRO_VIDEO_BAD_FPS",
            "INTRO_VIDEO_BAD_RESOLUTION", "INTRO_IMAGE_BAD_FORMAT", "INTRO_IMAGE_BAD_RATIO" ->
                Response.Status.UNSUPPORTED_MEDIA_TYPE
            else -> Response.Status.BAD_REQUEST
        }
        return jsonError(status, code)
    }

    /**
     * A raw audio body for the child wallpaper's background music. The declared length is
     * checked before the stream is consumed; the library repeats the byte, format, duration
     * and decoder checks before publishing the track.
     */
    private fun uploadAudio(context: Context, session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        val check = editorCheck(session)
        val editor = check.second
        if (editor is ParentWebAccess.EditorCheck.Failed) return editorError(editor.code)

        val declared = session.headers["content-length"]?.toLongOrNull()
            ?: return jsonError(Response.Status.LENGTH_REQUIRED, "LENGTH_REQUIRED")
        if (declared <= 0L) return jsonError(Response.Status.BAD_REQUEST, "AUDIO_INVALID")
        if (declared > PolicyAudioLibrary.MAX_AUDIO_BYTES) {
            return jsonError(Response.Status.PAYLOAD_TOO_LARGE, "AUDIO_TOO_LARGE")
        }
        val name = session.parameters["name"]?.firstOrNull().orEmpty()
        return when (val result = PolicyAudioLibrary.receive(context, session.inputStream, declared, name)) {
            is PolicyAudioLibrary.ReceiveResult.Ok -> {
                Log.i(TAG, "audio stored: ${result.entry.sizeBytes} bytes")
                json(
                    Response.Status.OK,
                    JSONObject()
                        .put("ok", true)
                        .put("uploadedId", result.entry.id)
                        .put("snapshot", ParentCenterActions.snapshot(context)),
                )
            }
            is PolicyAudioLibrary.ReceiveResult.Rejected -> {
                Log.i(TAG, "audio rejected: ${result.code}")
                audioError(result.code)
            }
        }
    }

    private fun audioError(code: String): Response {
        val status = when (code) {
            "AUDIO_TOO_LARGE" -> Response.Status.PAYLOAD_TOO_LARGE
            "AUDIO_NO_SPACE" -> Response.Status.SERVICE_UNAVAILABLE
            "AUDIO_BAD_FORMAT", "AUDIO_UNSUPPORTED_CODEC", "AUDIO_BAD_DURATION" ->
                Response.Status.UNSUPPORTED_MEDIA_TYPE
            else -> Response.Status.BAD_REQUEST
        }
        return jsonError(status, code)
    }

    private fun apps(context: Context, session: IHTTPSession): Response {
        val check = editorCheck(session)
        if (check.second is ParentWebAccess.EditorCheck.Failed) {
            return editorError((check.second as ParentWebAccess.EditorCheck.Failed).code)
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("apps", ParentCenterActions.appsJson(context)),
        )
    }

    /**
     * One member's full LOCAL fruit ledger. The page asks for the shared
     * [ParentSensitiveAuth.Scope.USER_EDIT] proof when the parent enabled the user-config PIN
     * prompts; with the switch off this is a normal editor-only read.
     */
    private fun fruitLedger(session: IHTTPSession): Response {
        val check = editorCheck(session)
        if (check.second is ParentWebAccess.EditorCheck.Failed) {
            return editorError((check.second as ParentWebAccess.EditorCheck.Failed).code)
        }
        val scope = userEditSensitiveScope()
        if (scope != null) {
            val editor = check.first
                ?: return jsonError(Response.Status.UNAUTHORIZED, "EDITOR_REQUIRED")
            val sensitive = ParentSensitiveAuth.check(
                token = session.headers[SENSITIVE_HEADER],
                scope = scope,
                editorToken = editor,
            )
            if (sensitive != ParentSensitiveAuth.Status.OK) return sensitiveError(sensitive)
        }
        val userId = session.parameters["userId"]?.firstOrNull().orEmpty()
        val outcome = ParentCenterActions.fruitLedger(userId)
        if (!outcome.ok) return jsonError(outcomeStatus(outcome.code), outcome.code)
        val ledger = outcome.snapshot ?: return jsonError(Response.Status.INTERNAL_ERROR, "INTERNAL")
        ledger.put(
            "editorRemainingMs",
            (check.second as ParentWebAccess.EditorCheck.Ok).remainingMs,
        )
        return json(Response.Status.OK, ledger)
    }

    private fun icon(context: Context, session: IHTTPSession): Response {
        val check = editorCheck(session)
        if (check.second is ParentWebAccess.EditorCheck.Failed) {
            return editorError((check.second as ParentWebAccess.EditorCheck.Failed).code)
        }
        val packageName = session.parameters["package"]?.firstOrNull().orEmpty()
        val bytes = ParentCenterActions.icon(context, packageName)
            ?: return jsonError(Response.Status.NOT_FOUND, "NOT_FOUND")
        return bytes(Response.Status.OK, "image/png", bytes)
    }

    /**
     * Serves one member's uploaded avatar. The response is a private app file, never a path
     * supplied by the page; the query name is only a cache-busting hint and is ignored.
     * The editor lease is required, so the page fetches this through host.blob() instead of
     * an <img> tag; a family photo is not readable by another LAN page.
     */
    private fun memberAvatar(context: Context, session: IHTTPSession): Response {
        val check = editorCheck(session)
        if (check.second is ParentWebAccess.EditorCheck.Failed) {
            return editorError((check.second as ParentWebAccess.EditorCheck.Failed).code)
        }
        val userId = session.parameters["userId"]?.firstOrNull().orEmpty()
        val member = FamilyHub.state.value.members.firstOrNull { it.userId == userId }
            ?: return jsonError(Response.Status.NOT_FOUND, "SUBJECT_NOT_FOUND")
        val bytes = FamilyAvatarStore.read(context, member)
            ?: return jsonError(Response.Status.NOT_FOUND, "SUBJECT_AVATAR_NOT_FOUND")
        return bytes(Response.Status.OK, "image/jpeg", bytes)
    }

    /**
     * Raw avatar upload, capped before allocation. The editor lease and the LOCAL/unbound check
     * run before the body is consumed, so an unauthorized client cannot fill the TV's storage.
     */
    private fun uploadMemberAvatar(context: Context, session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        if (session.headers["content-type"]?.startsWith("image/") != true) {
            return jsonError(Response.Status.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE")
        }
        val check = editorCheck(session)
        val editorResult = check.second
        if (editorResult is ParentWebAccess.EditorCheck.Failed) {
            return editorError(editorResult.code)
        }
        val editor = check.first ?: return jsonError(Response.Status.UNAUTHORIZED, "EDITOR_REQUIRED")
        val sensitive = ParentSensitiveAuth.check(
            token = session.headers[SENSITIVE_HEADER],
            scope = ParentSensitiveAuth.Scope.FAMILY_EDIT,
            editorToken = editor,
        )
        if (sensitive != ParentSensitiveAuth.Status.OK) return sensitiveError(sensitive)
        if (!ParentCenterActions.canEditFamily()) {
            return jsonError(
                Response.Status.CONFLICT,
                if (FamilyHub.isReady) "READ_ONLY" else "FAMILY_NOT_READY",
            )
        }
        val userId = session.parameters["userId"]?.firstOrNull().orEmpty()
        val family = FamilyHub.state.value
        val member = family.members.firstOrNull {
            it.userId == userId && it.status == com.planyourtv.policy.MemberStatus.ACTIVE
        } ?: return jsonError(Response.Status.NOT_FOUND, "SUBJECT_NOT_FOUND")
        val declared = session.headers["content-length"]?.toLongOrNull()
            ?: return jsonError(Response.Status.LENGTH_REQUIRED, "LENGTH_REQUIRED")
        if (declared <= 0L) return jsonError(Response.Status.BAD_REQUEST, "SUBJECT_AVATAR_EMPTY")
        if (declared > FamilyAvatarStore.MAX_UPLOAD_BYTES) {
            return jsonError(Response.Status.PAYLOAD_TOO_LARGE, "SUBJECT_AVATAR_TOO_LARGE")
        }
        return when (
            val result = FamilyAvatarStore.receive(
                context = context,
                familyId = member.familyId,
                userId = member.userId,
                input = session.inputStream,
                declaredLength = declared,
            )
        ) {
            is FamilyAvatarStore.ReceiveResult.Ok -> json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put(
                        "avatarRevision",
                        FamilyHub.state.value.members
                            .firstOrNull { it.userId == member.userId }
                            ?.avatarRevision ?: 0L,
                    )
                    .put("snapshot", ParentCenterActions.snapshot(context)),
            )
            is FamilyAvatarStore.ReceiveResult.Rejected -> {
                Log.i(TAG, "member avatar rejected: ${result.code}")
                jsonError(Response.Status.BAD_REQUEST, result.code)
            }
        }
    }

    /**
     * The one switch for every user-configuration write: time rules, the Time & Apps
     * switches, the shared fruit ledger and the source/quiz display toggles. It disappears
     * only when the parent turned those PIN prompts off; every system-configuration route
     * keeps its fixed scope.
     */
    internal fun userEditSensitiveScope(): ParentSensitiveAuth.Scope? =
        if (UserConfigPinSettings.isRequired()) ParentSensitiveAuth.Scope.USER_EDIT else null

    private fun write(
        context: Context,
        session: IHTTPSession,
        pinRateLimit: Boolean = false,
        sensitiveScope: ParentSensitiveAuth.Scope? = null,
        consumeSensitive: Boolean = false,
        action: (JSONObject) -> ParentCenterActions.Outcome,
    ): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        if (session.headers["content-type"]?.startsWith("application/json") != true) {
            return jsonError(Response.Status.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE")
        }
        val check = editorCheck(session)
        if (check.second is ParentWebAccess.EditorCheck.Failed) {
            return editorError((check.second as ParentWebAccess.EditorCheck.Failed).code)
        }
        var sensitiveEditor: String? = null
        if (sensitiveScope != null) {
            val editor = check.first ?: return jsonError(Response.Status.UNAUTHORIZED, "EDITOR_REQUIRED")
            sensitiveEditor = editor
            val status = ParentSensitiveAuth.check(
                token = session.headers[SENSITIVE_HEADER],
                scope = sensitiveScope,
                editorToken = editor,
            )
            if (status != ParentSensitiveAuth.Status.OK) {
                return sensitiveError(status)
            }
        }
        val clientIp = if (pinRateLimit) clientIp(session) else ""
        if (pinRateLimit) {
            val wait = ParentWebAccess.changePinLockSeconds(clientIp)
            if (wait > 0) return locked(wait)
        }
        val body = cachedJsonBody(session)
            ?: return jsonError(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val outcome = action(body)
        // Single-use scopes burn only after the guarded action actually succeeded, so a
        // cancelled confirmation or a retried command does not waste the parent's window.
        if (outcome.ok && consumeSensitive && sensitiveScope != null) {
            sensitiveEditor?.let { editor ->
                ParentSensitiveAuth.consume(
                    token = session.headers[SENSITIVE_HEADER],
                    scope = sensitiveScope,
                    editorToken = editor,
                )
            }
        }
        if (pinRateLimit) {
            ParentWebAccess.onChangePinAttempt(
                clientIp = clientIp,
                success = outcome.ok,
                wrongOldPin = outcome.code == "WRONG_PIN",
            )
        }
        if (!outcome.ok) {
            val response = JSONObject()
                .put("ok", false)
                .put("error", outcome.code)
                .put("messageKey", outcome.messageKey)
            if (outcome.retryAfterSeconds > 0) {
                response.put("retryAfterSeconds", outcome.retryAfterSeconds)
            }
            runCatching { response.put("snapshot", ParentCenterActions.snapshot(context)) }
            return json(outcomeStatus(outcome.code), response)
        }
        val response = JSONObject()
            .put("ok", true)
            .put("code", outcome.code)
            .put("messageKey", outcome.messageKey)
            .put("snapshot", ParentCenterActions.snapshot(context))
        outcome.recoveryCode?.let { response.put("recoveryCode", it) }
        return json(Response.Status.OK, response)
    }

    private fun editorCheck(
        session: IHTTPSession,
    ): Pair<String?, ParentWebAccess.EditorCheck> {
        val editor = session.headers[EDITOR_HEADER]
        return editor to ParentWebAccess.checkEditor(editor, quizToken(session))
    }

    private fun editorError(code: String): Response {
        if (code == "WEB_LOCAL_ONLY") return webLocalOnly()
        val status = when (code) {
            "EDITOR_REQUIRED", "LEASE_EXPIRED" -> Response.Status.UNAUTHORIZED
            "QUIZ_TAKEN_OVER", "LEASE_TAKEN_OVER" -> Response.Status.CONFLICT
            else -> Response.Status.CONFLICT
        }
        return jsonError(status, code)
    }

    /** The one locked answer every parent route uses while web configuration is unavailable. */
    private fun webLocalOnly(): Response {
        val gate = ParentWebAccess.gate()
        return jsonStatus(
            WEB_LOCAL_ONLY_STATUS,
            JSONObject()
                .put("ok", false)
                .put("error", "WEB_LOCAL_ONLY")
                .put("messageKey", "WEB_LOCAL_ONLY")
                .put("webAvailable", false)
                .put("mode", gate.mode.name)
                .put("reason", gate.reason),
        )
    }

    private fun outcomeStatus(code: String): Response.Status = when (code) {
        "STATE_CHANGED", "READ_ONLY", "NOT_READY", "SESSION_NOT_ACTIVE",
        "FAMILY_NOT_READY", "SUBJECT_UPDATE_FAILED", "TV_BUSY",
        "MEDIA_BUSY", "MEDIA_NOT_READY", "MEDIA_IN_USE",
        "MEDIA_SOUND_UNAVAILABLE", "RECOVERY_NOT_SET", "RECOVERY_FAILED", "LOCAL_ONLY",
        "FRUIT_LOCAL_ONLY", "FRUIT_ACCOUNT_MISSING", "FRUIT_REVISION_CONFLICT",
        "FRUIT_VOID_NOT_ALLOWED", "FRUIT_ENTRY_ALREADY_VOIDED",
        "FRUIT_BALANCE_INSUFFICIENT" ->
            Response.Status.CONFLICT
        "RECOVERY_LOCKED" -> Response.Status.TOO_MANY_REQUESTS
        "MEDIA_NOT_FOUND", "SUBJECT_NOT_FOUND", "FRUIT_MEMBER_NOT_FOUND",
        "FRUIT_ENTRY_NOT_FOUND", "FRUIT_PROJECT_NOT_FOUND" ->
            Response.Status.NOT_FOUND
        "MEDIA_NO_SPACE", "CATALOG_UNAVAILABLE" -> Response.Status.SERVICE_UNAVAILABLE
        "MEDIA_BAD_FORMAT" -> Response.Status.UNSUPPORTED_MEDIA_TYPE
        "MEDIA_TOO_LARGE" -> Response.Status.PAYLOAD_TOO_LARGE
        "WRONG_PIN", "PIN_REQUIRED", "OLD_PIN_REQUIRED", "SETUP_PIN_REQUIRED",
        "RECOVERY_INVALID" ->
            Response.Status.UNAUTHORIZED
        "TIMEOUT" -> Response.Status.INTERNAL_ERROR
        else -> Response.Status.BAD_REQUEST
    }

    private fun requirePostHeader(session: IHTTPSession): Boolean =
        session.method == Method.POST && session.headers["x-parent"] == "1"

    /** Reads the JSON body cached at the start of [handle]; a direct call still works. */
    private fun cachedJsonBody(session: IHTTPSession): JSONObject? =
        if (requestJsonCached.get() == true) requestJson.get() else readJsonBody(session)

    private fun readJsonBody(session: IHTTPSession): JSONObject? {
        // NanoHTTPD 2.3.1 does not expose PUT bodies through parseBody; read the declared
        // length directly. A missing or oversized length is rejected before allocation.
        val declared = session.headers["content-length"]?.toLongOrNull() ?: return null
        if (declared <= 0L || declared > MAX_JSON_BYTES) return null
        return runCatching {
            val bytes = ByteArray(declared.toInt())
            var offset = 0
            while (offset < bytes.size) {
                val read = session.inputStream.read(bytes, offset, bytes.size - offset)
                if (read < 0) break
                offset += read
            }
            if (offset == 0) null else JSONObject(String(bytes, 0, offset, Charsets.UTF_8))
        }.getOrNull()
    }

    private fun sameOrigin(session: IHTTPSession): Boolean {
        val host = session.headers["host"] ?: return false
        val base = "http://$host"
        val origin = session.headers["origin"]
        val referer = session.headers["referer"]
        if (origin != null && origin != base) return false
        if (referer != null && !referer.startsWith("$base/")) return false
        return true
    }

    private fun quizToken(session: IHTTPSession): String {
        val header = session.headers[QUIZ_HEADER].orEmpty()
        if (header.isNotBlank()) return header
        // The page always sends the header; the body copy exists only for compatibility with
        // an already-open older tab during a rolling TV update.
        return ""
    }

    private fun clientIp(session: IHTTPSession): String =
        session.remoteIpAddress?.takeIf { it.isNotBlank() } ?: "unknown"

    private fun sensitiveError(status: ParentSensitiveAuth.Status): Response {
        val code = when (status) {
            ParentSensitiveAuth.Status.REQUIRED -> "SENSITIVE_REQUIRED"
            ParentSensitiveAuth.Status.EXPIRED -> "SENSITIVE_EXPIRED"
            ParentSensitiveAuth.Status.SCOPE -> "SENSITIVE_SCOPE"
            ParentSensitiveAuth.Status.OK -> "SENSITIVE_REQUIRED"
        }
        return jsonError(Response.Status.FORBIDDEN, code)
    }

    private fun locked(seconds: Long): Response = json(
        Response.Status.TOO_MANY_REQUESTS,
        JSONObject()
            .put("ok", false)
            .put("error", "PIN_LOCKED")
            .put("messageKey", "PIN_LOCKED")
            .put("retryAfterSeconds", seconds),
    )

    private fun presetAvatar(context: Context, session: IHTTPSession): Response {
        val preset = session.parameters["preset"]?.firstOrNull()
        val body = FamilyAvatarStore.presetBytes(context, preset)
            ?: return error(Response.Status.NOT_FOUND, "AVATAR_NOT_FOUND")
        return bytes(Response.Status.OK, "image/jpeg", body)
    }

    private fun asset(context: Context, name: String, type: String): Response {
        val body = context.assets.open(name).bufferedReader().use { it.readText() }
        return decorate(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, type, body))
    }

    private fun json(status: Response.Status, body: JSONObject): Response =
        jsonStatus(status, body)

    private fun jsonStatus(status: Response.IStatus, body: JSONObject): Response =
        decorate(
            NanoHTTPD.newFixedLengthResponse(
                status,
                "application/json; charset=utf-8",
                body.toString(),
            ),
        )

    private fun jsonError(status: Response.Status, code: String): Response = json(
        status,
        JSONObject()
            .put("ok", false)
            .put("error", code)
            .put("messageKey", code),
    )

    private fun error(status: Response.Status, code: String): Response = jsonError(status, code)

    private fun bytes(status: Response.Status, type: String, body: ByteArray): Response {
        val response = NanoHTTPD.newFixedLengthResponse(
            status,
            type,
            ByteArrayInputStream(body),
            body.size.toLong(),
        )
        return decorate(response)
    }

    private fun decorate(response: Response): Response {
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("X-Content-Type-Options", "nosniff")
        response.addHeader("Referrer-Policy", "no-referrer")
        response.addHeader(
            "Content-Security-Policy",
            "default-src 'self'; img-src 'self' data: blob:" + mediaImageOrigin() + "; " +
                // Local films come from this TV; an un-downloaded catalog film is previewed
                // by the phone from the same media host that already serves its cover.
                "media-src 'self' blob:" + mediaImageOrigin() + "; " +
                "script-src 'self'; style-src 'self'; connect-src 'self'; " +
                "base-uri 'none'; frame-ancestors 'none'",
        )
        return response
    }

    /**
     * Covers and un-downloaded previews are loaded straight from the compile-time media host;
     * the catalogue parser already rejects every other host. The page itself stays same-origin.
     */
    private fun mediaImageOrigin(): String {
        val raw = BuildConfig.TV_MEDIA_CATALOG_URL.trim()
        if (raw.isEmpty()) return ""
        val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return ""
        val host = uri.host ?: return ""
        if (uri.scheme != "https" || (uri.port != -1 && uri.port != 443) || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null ||
            !host.matches(Regex("[A-Za-z0-9.-]+"))
        ) {
            return ""
        }
        return " https://" + host
    }
}
