package app.orchard.tv.policy.web

import android.content.Context
import android.util.Log
import app.orchard.tv.policy.media.PolicyVideoLibrary
import app.orchard.tv.quiz.QuizIntroVideo
import com.planyourtv.policy.LocalPin
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONObject
import java.io.ByteArrayInputStream

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
        if (!path.startsWith("/api/parent/") && path != "/parent.js" && path != "/parent.css") {
            return null
        }
        return try {
            if (session.method == Method.POST && path != INTRO_VIDEO_PATH) {
                requestJson.set(readJsonBody(session))
                requestJsonCached.set(true)
            }
            if (!sameOrigin(session)) return error(Response.Status.FORBIDDEN, "ORIGIN")
            when {
                session.method == Method.GET && path == "/parent.js" ->
                    asset(context, "web/parent.js", "application/javascript; charset=utf-8")
                session.method == Method.GET && path == "/parent.css" ->
                    asset(context, "web/parent.css", "text/css; charset=utf-8")
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
                session.method == Method.POST && path == "/api/parent/media/refresh" ->
                    write(context, session) { ParentCenterActions.refreshMedia(context) }
                session.method == Method.POST && path == "/api/parent/media/download" ->
                    write(context, session) { ParentCenterActions.downloadMedia(context, it) }
                session.method == Method.POST && path == "/api/parent/media/cancel" ->
                    write(context, session) { ParentCenterActions.cancelMediaDownload(it) }
                session.method == Method.POST && path == "/api/parent/media/select" ->
                    write(context, session) { ParentCenterActions.selectMedia(context, it) }
                session.method == Method.POST && path == "/api/parent/media/delete" ->
                    write(context, session) { ParentCenterActions.deleteMedia(context, it) }
                session.method == Method.GET && path == "/api/parent/apps" -> apps(context, session)
                session.method == Method.GET && path == "/api/parent/icon" -> icon(context, session)
                session.method == Method.POST && path == "/api/parent/rules" ->
                    write(
                        context,
                        session,
                        sensitiveScope = ParentSensitiveAuth.Scope.RULES_EDIT,
                    ) { ParentCenterActions.setRules(it) }
                // Activation is fixed to MANUAL while the web center owns local config.
                session.method == Method.POST && path == "/api/parent/activation" ->
                    jsonError(Response.Status.GONE, "MANUAL_MODE_FIXED")
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
                // Rules, source visibility and the quiz switch are only reachable from the
                // PIN-gated Time & Apps page; the server repeats that check because hiding
                // the UI entry is not an authorization boundary.
                session.method == Method.POST && path == "/api/parent/source-entries" ->
                    write(
                        context,
                        session,
                        sensitiveScope = ParentSensitiveAuth.Scope.RULES_EDIT,
                    ) { ParentCenterActions.setSourceEntries(it) }
                session.method == Method.POST && path == "/api/parent/quiz" ->
                    write(
                        context,
                        session,
                        sensitiveScope = ParentSensitiveAuth.Scope.RULES_EDIT,
                    ) { ParentCenterActions.setQuiz(it) }
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
            is ParentWebAccess.AcquireOutcome.Ok -> json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put("editorToken", result.editorToken)
                    .put("expiresAtMs", result.expiresAtMs)
                    .put("quizBound", result.quizBound)
                    .put("gate", gateJson()),
            )
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
     * A raw MP4 body, streamed straight into the local library instead of being buffered as
     * JSON. The declared length is checked before any bytes are allocated or read; the editor
     * lease is checked before the body is consumed, so an unauthorized client never gets to
     * fill the TV's storage.
     */
    private fun uploadIntroVideo(context: Context, session: IHTTPSession): Response {
        if (!requirePostHeader(session)) return jsonError(Response.Status.FORBIDDEN, "REQUEST_HEADER")
        val check = editorCheck(session)
        val editor = check.second
        if (editor is ParentWebAccess.EditorCheck.Failed) return editorError(editor.code)

        val declared = session.headers["content-length"]?.toLongOrNull()
            ?: return jsonError(Response.Status.LENGTH_REQUIRED, "LENGTH_REQUIRED")
        if (declared <= 0L) return jsonError(Response.Status.BAD_REQUEST, "INTRO_VIDEO_INVALID")
        if (declared > QuizIntroVideo.MAX_FILE_BYTES) {
            return jsonError(Response.Status.PAYLOAD_TOO_LARGE, "INTRO_VIDEO_TOO_LARGE")
        }
        val name = session.parameters["name"]?.firstOrNull().orEmpty()
        return when (val result = QuizIntroVideo.receive(context, session.inputStream, declared, name)) {
            is QuizIntroVideo.ReceiveResult.Ok -> {
                Log.i(TAG, "intro video stored: ${result.entry.sizeBytes} bytes, ${result.entry.durationMs} ms")
                json(
                    Response.Status.OK,
                    JSONObject()
                        .put("ok", true)
                        .put("uploadedId", result.entry.id)
                        .put("snapshot", ParentCenterActions.snapshot(context)),
                )
            }
            is QuizIntroVideo.ReceiveResult.Rejected -> {
                // The code is enough to diagnose a failed upload from logcat; never log the name.
                Log.i(TAG, "intro video rejected: ${result.code}")
                introVideoError(result.code)
            }
        }
    }

    private fun introVideoError(code: String): Response {
        val status = when (code) {
            "INTRO_VIDEO_TOO_LARGE" -> Response.Status.PAYLOAD_TOO_LARGE
            "INTRO_VIDEO_LIBRARY_FULL" -> Response.Status.CONFLICT
            "INTRO_VIDEO_NO_SPACE" -> Response.Status.SERVICE_UNAVAILABLE
            "INTRO_VIDEO_BAD_FORMAT", "INTRO_VIDEO_BAD_CODEC", "INTRO_VIDEO_HEVC",
            "INTRO_VIDEO_BAD_RATIO", "INTRO_VIDEO_BAD_FPS", "INTRO_VIDEO_BAD_RESOLUTION" ->
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

    private fun write(
        context: Context,
        session: IHTTPSession,
        pinRateLimit: Boolean = false,
        sensitiveScope: ParentSensitiveAuth.Scope? = null,
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
        if (sensitiveScope != null) {
            val editor = check.first ?: return jsonError(Response.Status.UNAUTHORIZED, "EDITOR_REQUIRED")
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
        "MEDIA_BUSY", "MEDIA_NOT_READY", "MEDIA_IN_USE", "MEDIA_LIBRARY_FULL",
        "RECOVERY_NOT_SET", "RECOVERY_FAILED", "LOCAL_ONLY" ->
            Response.Status.CONFLICT
        "RECOVERY_LOCKED" -> Response.Status.TOO_MANY_REQUESTS
        "MEDIA_NOT_FOUND" -> Response.Status.NOT_FOUND
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
            "default-src 'self'; img-src 'self' data: blob:; script-src 'self'; " +
                "style-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'",
        )
        return response
    }
}
