package app.orchard.tv.quiz

import android.content.Context
import app.orchard.tv.policy.FamilyAvatarStore
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.ManualSessionLimits
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.web.ParentWebAccess
import app.orchard.tv.policy.web.ParentWebCenter
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONObject
import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.MemberStatus
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.quiz.CourseOrigin
import com.planyourtv.policy.quiz.QuizAnswerKind
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizBankImport
import com.planyourtv.policy.quiz.QuizCollection
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizConfigDocumentCodec
import com.planyourtv.policy.quiz.QuizCurriculum
import com.planyourtv.policy.quiz.QuizDecodeResult
import com.planyourtv.policy.quiz.QuizDocDecodeResult
import com.planyourtv.policy.quiz.QuizDocIssue
import com.planyourtv.policy.quiz.QuizEngineConfig
import com.planyourtv.policy.quiz.QuizImportError
import com.planyourtv.policy.quiz.QuizImportResult
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizLevelPackage
import com.planyourtv.policy.quiz.QuizPackageSource
import com.planyourtv.policy.quiz.QuizPresetBank
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionCodec
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizSessionValidation
import com.planyourtv.policy.quiz.QuizUnitCatalog
import com.planyourtv.policy.quiz.SourceIssue
import org.json.JSONArray
import java.util.Locale
import java.util.UUID

/**
 * The quiz page and its small JSON API. PhoneServer hands over only paths below
 * its random page token; this object still checks the pairing secret + parent
 * PIN before it reads or changes any session.
 */
object QuizWebAdmin {
    private const val MAX_JSON_BYTES = 256 * 1024
    private const val MAX_IMPORT_BYTES = 512 * 1024
    private const val MAX_PROVIDER_KEY_LENGTH = 4096
    private const val IMPORT_PATH = "/api/quiz/admin/bank/import"
    /** The web level editor's shape; a cached older page saves through the legacy fields. */
    private const val LEVEL_MODEL = 2
    private const val MAX_LEVEL_COUNT_SCOPES = 64

    /** V41 bank subpages. `wrong` is a state view, not a content source. */
    private val BANK_SCOPES = setOf("builtin", "ai", "manual", "wrong")

    /** Device-capability failures share one 400 UNSUPPORTED contract. */
    private val CAPABILITY_ISSUE_CODES = setOf(
        "UNSUPPORTED_FILL",
        "UNSUPPORTED_AUTO_NEXT",
        "UNSUPPORTED_PINYIN",
        "TIME_LIMIT_OVER_MAX",
        "LEVEL_LIMIT_20",
    )

    /**
     * NanoHTTPD keeps one [IHTTPSession] for the whole keep-alive connection,
     * so caching bodies by session would return the first request's body for
     * every later request on that connection. Scope the parsed body to the
     * current request instead: [api] runs synchronously on the connection
     * thread, so a ThreadLocal is request-local here.
     */
    private val requestBody = ThreadLocal<JSONObject?>()

    fun handle(context: Context, session: IHTTPSession, path: String): Response? {
        // A remote-bound TV has no local parent center: child mode is owned by the cloud
        // app. The local web's only configuration surface there is Aerial's page at the
        // token root, so the QR/bookmark entry (/quiz) lands there instead of a dead
        // disabled card. Redirect only the HTML shell; /quiz.js, /quiz.css and the APIs
        // keep their paths so an already-loaded page is not affected.
        if (ParentWebCenter.ENABLED &&
            isBoundQuizShellRedirect(session.method, path, ParentWebAccess.gate().reason)
        ) {
            return redirectToAerialPage(session)
        }
        // PhoneServer still serves wallpaper/setup pages in cloud mode. The quiz API is a
        // local-mode feature. With V27 enabled the page shell is also the parent-center host,
        // so it stays available in NONE/CLOUD; its parent API has its own gate.
        if (isQuizApiPath(path) && PolicyModeCoordinator.mode != PolicyMode.LOCAL) {
            return error(Response.Status.GONE, "LOCAL_ONLY")
        }
        if (isQuizShellPath(path) && PolicyModeCoordinator.mode != PolicyMode.LOCAL &&
            !ParentWebCenter.ENABLED
        ) {
            return error(Response.Status.GONE, "LOCAL_ONLY")
        }
        return when {
            session.method == Method.GET && (path == "/quiz" || path == "/quiz/") ->
                asset(context, "web/quiz.html", "text/html; charset=utf-8")
            session.method == Method.GET && path == "/quiz.js" ->
                asset(context, "web/quiz.js", "application/javascript; charset=utf-8")
            session.method == Method.GET && path == "/quiz.css" ->
                asset(context, "web/quiz.css", "text/css; charset=utf-8")
            session.method == Method.GET && path == "/api/quiz/status" -> {
                json(Response.Status.OK, QuizWeb.statusJson())
            }
            path.startsWith("/api/quiz/") -> api(context, session, path)
            else -> null
        }
    }

    private fun isQuizShellPath(path: String): Boolean =
        path == "/quiz" || path == "/quiz/" || path == "/quiz.js" || path == "/quiz.css"

    private fun isQuizApiPath(path: String): Boolean = path.startsWith("/api/quiz/")

    private fun api(context: Context, session: IHTTPSession, path: String): Response {
        // NanoHTTPD 2.3.1 leaves a POST body on the keep-alive stream when the
        // handler never reads it, so the next request would start with "{}POST"
        // and fail with "HTTP verb {}POST unhandled". Read the body once per
        // request; jsonBody() serves it to the handler and clears it after.
        val bodyLimit = if (path == IMPORT_PATH) MAX_IMPORT_BYTES else MAX_JSON_BYTES
        requestBody.set(readJsonBody(session, bodyLimit))
        try {
            if (!sameOrigin(session)) return error(Response.Status.FORBIDDEN, "ORIGIN")
            return when {
                session.method == Method.POST && path == "/api/quiz/pair" -> pair(session)
                session.method == Method.POST && path == "/api/quiz/auth/pin" -> pin(session)
                session.method == Method.POST && path == "/api/quiz/auth/logout" -> logout(session)
                session.method == Method.POST && path == "/api/quiz/auth/release" -> release(session)
                session.method == Method.GET && path == "/api/quiz/admin/heartbeat" -> heartbeat(session)
                session.method == Method.POST && path == "/api/quiz/admin/unlock" -> unlock(session)
                session.method == Method.POST && path == "/api/quiz/admin/manual/start" -> manualStart(session)
                session.method == Method.GET && path == "/api/quiz/admin/session" -> readSession(session)
                session.method == Method.GET && path == "/api/quiz/admin/member/avatar" ->
                    memberAvatar(context, session)
                session.method == Method.GET && path == "/api/quiz/admin/units" -> readUnitCatalog(session)
                session.method == Method.PUT && path == "/api/quiz/admin/session" -> putSession(session)
                session.method == Method.DELETE && path == "/api/quiz/admin/session" -> deleteSession(session)
                session.method == Method.POST && path == "/api/quiz/admin/challenge-config" -> saveChallengeConfig(session)
                session.method == Method.POST && path == "/api/quiz/admin/level-counts" -> levelSourceCounts(session)
                session.method == Method.GET && path == "/api/quiz/admin/config-document" -> readConfigDocument(session)
                session.method == Method.PUT && path == "/api/quiz/admin/config-document" -> saveConfigDocument(session)
                session.method == Method.GET && path == "/api/quiz/admin/bank" -> readBank(session)
                session.method == Method.GET && path == "/api/quiz/admin/bank/facets" -> readBankFacets(session)
                session.method == Method.POST && path == IMPORT_PATH -> importBank(session)
                session.method == Method.POST &&
                    (path == "/api/quiz/admin/bank/clear" ||
                        path == "/api/quiz/admin/wrong-book/clear") -> clearBank(session)
                session.method == Method.GET && path == "/api/quiz/admin/collections" ->
                    readCollections(session)
                session.method == Method.POST && path == "/api/quiz/admin/collections" ->
                    createCollection(session)
                session.method == Method.PUT && path == "/api/quiz/admin/collections" ->
                    renameCollection(session)
                session.method == Method.DELETE && path == "/api/quiz/admin/collections" ->
                    deleteCollection(session)
                session.method == Method.GET && path == "/api/quiz/admin/cache" -> readCache(session)
                session.method == Method.POST && path == "/api/quiz/admin/cache/clear" -> clearCache(session)
                session.method == Method.POST && path == "/api/quiz/admin/cache/rebuild" -> rebuildCache(session)
                session.method == Method.GET && path == "/api/quiz/admin/provider" -> readProvider(session)
                session.method == Method.POST && path == "/api/quiz/admin/consent" -> setConsent(session)
                session.method == Method.POST && path == "/api/quiz/admin/provider" -> saveProvider(session)
                session.method == Method.POST && path == "/api/quiz/admin/provider/test" -> testProvider(session)
                session.method == Method.GET && path == "/api/quiz/admin/generate/prefs" -> readGenerationPrefs(session)
                session.method == Method.POST && path == "/api/quiz/admin/generate/prefs" -> saveGenerationPrefs(session)
                session.method == Method.POST && path == "/api/quiz/admin/generate" -> startGeneration(session)
                session.method == Method.GET && path == "/api/quiz/admin/generate/status" -> generationStatus(session)
                session.method == Method.POST && path == "/api/quiz/admin/generate/cancel" -> cancelGeneration(session)
                session.method == Method.POST && path == "/api/quiz/admin/generate/add" -> addGeneration(session)
                session.method == Method.POST && path == "/api/quiz/admin/generate/discard" -> discardGeneration(session)
                // The quiz feature switch was removed: local mode always has the quiz, and a
                // cached page must not be able to delete an unstarted challenge here.
                session.method == Method.POST &&
                    (path == "/api/quiz/admin/enable" || path == "/api/quiz/admin/disable") ->
                    error(Response.Status.GONE, "QUIZ_SWITCH_REMOVED")
                session.method == Method.GET && path == "/api/quiz/admin/sync" -> syncStatus(session)
                session.method == Method.POST && path == "/api/quiz/admin/sync/retry" -> syncRetry(session)
                session.method == Method.POST && path == "/api/quiz/admin/sync/use-remote" -> syncUseRemote(session)
                else -> error(Response.Status.NOT_FOUND, "NOT_FOUND")
            }
        } finally {
            requestBody.remove()
        }
    }

    /**
     * Kept only for pages cached before the fixed-URL change. There is no
     * per-scan secret anymore; the PIN is the only first-time gate.
     */
    private fun pair(session: IHTTPSession): Response {
        if (!jsonRequest(session)) return error(Response.Status.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE")
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("needsPin", true)
                .put("consentAccepted", QuizSessionStore.consentAccepted())
                .put("providerConfigured", QuizSessionStore.providerConfigured()),
        )
    }

    private fun pin(session: IHTTPSession): Response {
        if (!jsonRequest(session)) return error(Response.Status.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE")
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val pin = body.optString("pin", "")
        if (pin.length != 6 || pin.any { !it.isDigit() }) return error(Response.Status.BAD_REQUEST, "PIN_INVALID")
        // A parent who explicitly confirms takeover replaces the phone that
        // currently owns the page; the old token stops working immediately.
        val takeover = body.optBoolean("takeover", false)
        return when (val result = QuizAccess.authenticate(pin, takeover = takeover)) {
            is QuizAccess.AuthResult.Ok -> json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put("adminToken", result.adminToken)
                    .put("expiresAt", result.expiresAt),
            )
            QuizAccess.AuthResult.PinInvalid -> error(Response.Status.UNAUTHORIZED, "PIN_INVALID")
            QuizAccess.AuthResult.PinNotSet -> error(Response.Status.CONFLICT, "PIN_NOT_SET")
            QuizAccess.AuthResult.ParentInUse -> error(Response.Status.CONFLICT, "PARENT_IN_USE")
            is QuizAccess.AuthResult.PinLocked -> json(
                Response.Status.TOO_MANY_REQUESTS,
                JSONObject()
                    .put("ok", false)
                    .put("error", "PIN_LOCKED")
                    .put("retryAfterSeconds", result.retryAfterSeconds),
            )
        }
    }

    private fun logout(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        QuizAccess.logout(adminToken(session))
        return json(Response.Status.OK, JSONObject().put("ok", true))
    }

    /**
     * Presence probe. The page sends this only while it is visible; [adminError]
     * refreshes the owner's last-seen time. The lock and manualStart objects let the
     * page enable or disable both lock-screen actions without polling extra endpoints.
     */
    private fun heartbeat(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("lock", lockJson(PolicyModeCoordinator.webUnlockStatus()))
                .put("manualStart", manualStartJson(PolicyModeCoordinator.webManualStartStatus())),
        )
    }

    /**
     * The lock-screen-only temporary unlock. The coordinator performs the final atomic
     * check; this handler adds the page's own PIN-attempt limiter and never logs the PIN.
     */
    private fun unlock(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val currentLock = { PolicyModeCoordinator.webUnlockStatus() }
        val wait = QuizAccess.sensitiveActionLockedSeconds()
        if (wait > 0) {
            return json(
                Response.Status.TOO_MANY_REQUESTS,
                JSONObject()
                    .put("ok", false)
                    .put("error", "PIN_LOCKED")
                    .put("retryAfterSeconds", wait)
                    .put("lock", lockJson(currentLock())),
            )
        }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val pin = body.optString("pin", "")
        if (pin.length != 6 || pin.any { !it.isDigit() }) {
            return error(Response.Status.BAD_REQUEST, "PIN_INVALID")
        }
        return when (val result = PolicyModeCoordinator.unlockFromWeb(pin)) {
            PolicyModeCoordinator.WebUnlockResult.Ok -> {
                QuizAccess.onSensitiveActionResult(success = true)
                json(
                    Response.Status.OK,
                    JSONObject().put("ok", true).put("lock", lockJson(currentLock())),
                )
            }
            is PolicyModeCoordinator.WebUnlockResult.PinInvalid -> {
                if (result.locked) {
                    QuizAccess.lockSensitiveActionOut()
                } else {
                    QuizAccess.onSensitiveActionResult(success = false)
                }
                val retrySeconds = QuizAccess.sensitiveActionLockedSeconds()
                if (retrySeconds > 0) {
                    json(
                        Response.Status.TOO_MANY_REQUESTS,
                        JSONObject()
                            .put("ok", false)
                            .put("error", "PIN_LOCKED")
                            .put("retryAfterSeconds", retrySeconds)
                            .put("lock", lockJson(currentLock())),
                    )
                } else {
                    json(
                        Response.Status.UNAUTHORIZED,
                        JSONObject()
                            .put("ok", false)
                            .put("error", "PIN_INVALID")
                            .put("lock", lockJson(currentLock())),
                    )
                }
            }
            is PolicyModeCoordinator.WebUnlockResult.Rejected -> json(
                Response.Status.CONFLICT,
                JSONObject()
                    .put("ok", false)
                    .put("error", result.reason.name)
                    .put("lock", lockJson(currentLock())),
            )
        }
    }

    /**
     * One lock-screen manual session from the parent page. This is not the temporary
     * unlock: it starts a timed session, requires the parent PIN for every start, and
     * shares the sensitive-action limiter with the unlock endpoint. V45 treats it as an
     * out-of-plan parent authorization: the plan's windows, quota, route counts and rest do
     * not block it, but single-session, screen-on, study-mode and pause-cap boundaries still
     * apply. The coordinator and runtime perform the final atomic checks; this handler only
     * maps typed results.
     */
    private fun manualStart(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val currentStatus = { PolicyModeCoordinator.webManualStartStatus() }
        val wait = QuizAccess.sensitiveActionLockedSeconds()
        if (wait > 0) {
            return json(
                Response.Status.TOO_MANY_REQUESTS,
                JSONObject()
                    .put("ok", false)
                    .put("error", "PIN_LOCKED")
                    .put("retryAfterSeconds", wait)
                    .put("manualStart", manualStartJson(currentStatus())),
            )
        }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val pin = body.optString("pin", "")
        if (pin.length != 6 || pin.any { !it.isDigit() }) {
            return error(Response.Status.BAD_REQUEST, "PIN_INVALID")
        }
        val minutes = body.optInt("minutes", -1)
        if (!ManualSessionLimits.isValid(minutes)) {
            return error(Response.Status.BAD_REQUEST, "INVALID_DURATION")
        }
        return when (val result = PolicyModeCoordinator.startManualFromWeb(pin, minutes)) {
            PolicyModeCoordinator.WebManualStartResult.Ok -> {
                QuizAccess.onSensitiveActionResult(success = true)
                json(
                    Response.Status.OK,
                    JSONObject()
                        .put("ok", true)
                        .put("manualStart", manualStartJson(currentStatus())),
                )
            }
            PolicyModeCoordinator.WebManualStartResult.InvalidDuration ->
                error(Response.Status.BAD_REQUEST, "INVALID_DURATION")
            is PolicyModeCoordinator.WebManualStartResult.PinInvalid -> {
                if (result.locked) {
                    QuizAccess.lockSensitiveActionOut()
                } else {
                    QuizAccess.onSensitiveActionResult(success = false)
                }
                val retrySeconds = QuizAccess.sensitiveActionLockedSeconds()
                if (retrySeconds > 0) {
                    json(
                        Response.Status.TOO_MANY_REQUESTS,
                        JSONObject()
                            .put("ok", false)
                            .put("error", "PIN_LOCKED")
                            .put("retryAfterSeconds", retrySeconds)
                            .put("manualStart", manualStartJson(currentStatus())),
                    )
                } else {
                    json(
                        Response.Status.UNAUTHORIZED,
                        JSONObject()
                            .put("ok", false)
                            .put("error", "PIN_INVALID")
                            .put("manualStart", manualStartJson(currentStatus())),
                    )
                }
            }
            is PolicyModeCoordinator.WebManualStartResult.Rejected -> json(
                Response.Status.CONFLICT,
                JSONObject()
                    .put("ok", false)
                    .put("error", result.reason.name)
                    .put("manualStart", manualStartJson(currentStatus())),
            )
        }
    }

    /**
     * Best-effort unload release. Unlike [logout] this keeps the remembered
     * token, so the same phone can resume without the PIN when nobody else
     * took the slot. If the browser never sends this request, the idle timeout
     * in [QuizAccess] has the same effect about ten seconds later.
     */
    private fun release(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        QuizAccess.release(adminToken(session))
        return json(Response.Status.OK, JSONObject().put("ok", true))
    }

    private fun readSession(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val context = subjectContext(session) ?: return invalidSubject()
        // Entering the local settings page is a documented cloud->TV apply point.
        QuizConfigSync.refreshFromCloudAsync()
        val state = QuizRuntime.refresh()
        val current = state.session
        val editingUserId = context.editingUserId
        val activeUserId = context.activeUserId
        // Configuration, tags, packages and bank counts follow the phone's
        // editing member. Execution fields (the session above, active scope
        // availability) still describe the member the TV is running.
        var documentUnreadable = state.documentUnreadable
        var documentState = if (documentUnreadable) "UNREADABLE" else "OK"
        var challengeConfig = state.defaultChallengeConfig
        var packages = state.levelPackages
        var levelPackagesUnreadable = state.levelPackagesUnreadable
        if (editingUserId != null && !context.editingIsActive) {
            when (val loaded = QuizConfigDocumentStore.localDocumentFor(editingUserId)) {
                is QuizDocLoadResult.Ok -> {
                    documentUnreadable = false
                    documentState = "OK"
                    challengeConfig = loaded.document.toLegacyConfig() ?: QuizConfig()
                    when (val extension = QuizLevelPackageStore.read(loaded.document)) {
                        is QuizLevelPackageStore.Extension.Present ->
                            packages = extension.packages
                        QuizLevelPackageStore.Extension.Absent ->
                            packages = storedPackages(loaded.document, editingUserId)
                        is QuizLevelPackageStore.Extension.Broken -> {
                            packages = emptyList()
                            levelPackagesUnreadable = true
                        }
                    }
                }
                is QuizDocLoadResult.Unreadable -> {
                    val absent = loaded.errors.singleOrNull() == "quiz.doc.local:ABSENT"
                    documentUnreadable = !absent
                    documentState = if (absent) "ABSENT" else "UNREADABLE"
                    challengeConfig = QuizConfig()
                    packages = emptyList()
                    levelPackagesUnreadable = false
                }
                QuizDocLoadResult.Storage ->
                    return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
            }
        }
        val body = JSONObject()
            .put("ok", true)
            .put("enabled", state.enabled)
            .put("documentUnreadable", documentUnreadable)
            .put("documentState", documentState)
            .put("subjectUserId", editingUserId ?: JSONObject.NULL)
            .put("editingUserId", editingUserId ?: JSONObject.NULL)
            .put("activeUserId", activeUserId ?: JSONObject.NULL)
            .put("members", activeMembersJson())
            .put("privateCapacity", QuizQuestionCache.PRIVATE_CAPACITY)
            .put("privateRemaining", QuizQuestionCache.remainingCapacity(editingUserId))
        if (current == null) {
            body.put("session", JSONObject.NULL)
        } else if (current.status in ACTIVE_STATUSES) {
            body.put("session", summary(current))
        } else {
            body.put("session", JSONObject(QuizSessionCodec.encode(current)))
        }
        body.put("sessionActive", current?.status in ACTIVE_STATUSES)
        body.put("curriculum", QuizCurriculum.current()?.descriptor() ?: JSONObject.NULL)
        body.put("cloudJuniorReady", QuizCurriculum.CLOUD_JUNIOR_READY)
        body.put("consentAccepted", QuizSessionStore.consentAccepted())
        body.put("providerConfigured", QuizSessionStore.providerConfigured())
        body.put("challengeConfig", QuizSessionCodec.encodeConfig(challengeConfig))
        // Level packages are the only editable model. The legacy fields below
        // are a read-only projection for a page cached before V37; a save from
        // that page is rejected with LEVEL_MODEL_UPGRADED.
        val projection = legacyLevelProjection(packages, editingUserId, activeUserId)
        body.put("levelModel", LEVEL_MODEL)
        body.put("levelPackagesUnreadable", levelPackagesUnreadable)
        body.put("levelPackages", levelPackagesJson(packages, editingUserId, activeUserId))
        body.put(
            "levelLimits",
            JSONObject()
                .put("maxLevels", QuizLevelPackage.MAX_LEVELS)
                .put("maxSources", QuizLevelPackage.MAX_SOURCES)
                .put("maxNameCodePoints", QuizLevelPackage.MAX_NAME_CODE_POINTS),
        )
        body.put("answerScope", projection.answerScope?.encode() ?: JSONObject.NULL)
        // Kept for a page cached before the level list existed; it only knows
        // one scope and reads the first level.
        body.put(
            "answerScopeMode",
            if (projection.answerScope == null) "bound" else "custom",
        )
        body.put("answerScopes", projection.answerScopes)
        body.put("customLevels", projection.customLevels)
        body.put("wrongLevel", projection.wrongLevel ?: JSONObject.NULL)
        body.put("collections", collectionsJson(editingUserId, activeUserId, packages))
        val sourceCounts = QuizQuestionCache.sourceCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            subjectUserId = editingUserId,
        )
        body.put(
            "bankSourceCounts",
            JSONObject()
                .put("course", sourceCounts.course)
                .put("custom", sourceCounts.custom),
        )
        val scopeCounts = QuizQuestionCache.ownerSourceCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            subjectUserId = editingUserId,
            activeSubjectUserId = activeUserId,
        )
        body.put(
            "bankScopeCounts",
            JSONObject()
                .put("builtin", scopeCounts.builtin)
                .put("ai", scopeCounts.ai)
                .put("manual", scopeCounts.manual)
                .put("wrong", scopeCounts.wrong),
        )
        body.put(
            "answerScopeAvailable",
            QuizQuestionCache.availableCount(
                QuizQuestionCache.LOCAL_SCOPE,
                answerScope = projection.answerScope,
                subjectUserId = editingUserId,
                activeSubjectUserId = activeUserId,
            ),
        )
        body.put("engineConfig", QuizEngineConfig.from(challengeConfig).let {
            JSONObject()
                .put("pullCount", it.pullCount)
                .put("defaultTimeLimitSec", it.defaultTimeLimitSec)
                .put("answerDisplaySec", it.answerDisplaySec)
                .put("minutesPerQuestion", it.minutesPerQuestion)
                .put("shuffleOptions", it.shuffleOptions)
        })
        body.put("sourceConfig", QuizSessionStore.loadSourceConfig().encode())
        body.put("source", state.source.name)
        body.put("localBankActive", state.localBankActive)
        val bankCounts = QuizQuestionCache.bankCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            subjectUserId = editingUserId,
            activeSubjectUserId = activeUserId,
        )
        body.put(
            "bankState",
            editingBankState(
                packages = packages,
                documentUnreadable = documentUnreadable,
                packagesUnreadable = levelPackagesUnreadable,
                subjectUserId = editingUserId,
                activeSubjectUserId = activeUserId,
                nowMs = System.currentTimeMillis(),
            ),
        )
        body.put("bank", bankCountsJson(bankCounts))
        body.put("presetBank", presetBankJson(editingUserId, activeUserId))
        body.put("activeScopeAvailable", state.activeScopeAvailable)
        body.put("syncState", QuizSessionStore.loadSyncState().encode())
        body.put("cloudManaged", QuizConfigSync.isCloudManaged())
        body.put("cache", legacyCacheJson(bankCounts))
        body.put("bankCapacity", QuizQuestionCache.BANK_CAPACITY)
        return json(Response.Status.OK, body)
    }

    /**
     * Stock for candidate course scopes while the parent builds a package.
     * Counts stay separate from `packageAvailability` so an unsaved source can
     * show "mixed / single / multiple" before it joins the package.
     */
    private fun levelSourceCounts(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val array = body.optJSONArray("scopes")
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
        if (array.length() > MAX_LEVEL_COUNT_SCOPES) {
            return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
        }
        // The draft source picks preset or AI before its scope exists, so the
        // request carries one origin for every candidate scope. Missing means
        // the pre-split combined behavior.
        val origin = if (body.has("origin") && !body.isNull("origin")) {
            CourseOrigin.fromWire(body.optString("origin", ""))
                ?: return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
        } else {
            CourseOrigin.ALL
        }
        val now = System.currentTimeMillis()
        val counts = JSONArray()
        for (index in 0 until array.length()) {
            val scope = QuizAnswerScope.parse(array.optJSONObject(index))
                ?: return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
            val typeCounts = QuizQuestionCache.availableTypeCounts(
                nowMs = now,
                answerScope = scope,
                subjectUserId = context.editingUserId,
                activeSubjectUserId = context.activeUserId,
                origin = origin,
            )
            counts.put(
                JSONObject()
                    .put("total", typeCounts.total)
                    .put("single", typeCounts.single)
                    .put("multiple", typeCounts.multiple),
            )
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", context.editingUserId ?: JSONObject.NULL)
                .put("counts", counts)
                .put(
                    "wrong",
                    QuizQuestionCache.wrongReviewCount(
                        subjectUserId = context.editingUserId,
                        activeSubjectUserId = context.activeUserId,
                    ),
                ),
        )
    }

    /**
     * Display-only unit titles for the phone page. The endpoint stays separate
     * from the session so a missing catalogue cannot break editing; callers
     * fall back to the stage unit limit and the numeric label.
     */
    private fun readUnitCatalog(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("catalog", QuizUnitCatalog.current()?.descriptor() ?: JSONObject.NULL),
        )
    }

    /**
     * Compatibility path for a page cached before V17. A publish no longer
     * creates a READY session; its items are appended to the local bank, and
     * the TV consumes them only when the child starts a round.
     */
    private fun putSession(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        // This pre-V17 compatibility path writes the active challenge config;
        // a sibling's document must be edited through the document endpoints.
        if (!context.editingIsActive) {
            return error(Response.Status.CONFLICT, "SWITCH_REQUIRED")
        }
        val publish = body.optBoolean("publish", true)
        val sessionObject = body.optJSONObject("session") ?: return error(Response.Status.BAD_REQUEST, "INVALID_SESSION")
        val parsed = when (val decoded = QuizSessionCodec.decode(sessionObject.toString())) {
            is QuizDecodeResult.Success -> decoded.session
            is QuizDecodeResult.Failure -> return invalidSession(decoded.errors)
        }
        val now = System.currentTimeMillis()
        val errors = QuizSessionValidation.validateForPublish(parsed, now).toMutableList()
        if (publish && parsed.status != QuizSessionStatus.READY) errors += "session.status: must be READY when publish=true"
        if (!publish && parsed.status != QuizSessionStatus.DRAFT) errors += "session.status: must be DRAFT when publish=false"
        if (parsed.config.minCorrectCount > parsed.config.pullCount) {
            errors += "config.minCorrectCount: must not exceed pullCount"
        }
        if (errors.isNotEmpty()) return invalidSession(errors)

        if (publish && !QuizSessionStore.saveChallengeConfig(parsed.config)) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        if (publish) QuizConfigSync.afterLocalEdit()
        val append = if (publish) {
            QuizQuestionCache.appendLocalQuestions(
                parsed.id,
                parsed.items,
                now,
                ownerUserId = context.editingUserId,
            )
        } else {
            QuizQuestionCache.AppendResult()
        }
        if (publish && append.appended == 0 && append.capacityReached) {
            return error(Response.Status.CONFLICT, "BANK_FULL")
        }
        if (publish && append.appended == 0 && append.duplicates == 0 && append.rejected > 0) {
            return error(Response.Status.INTERNAL_ERROR, "CACHE_UNAVAILABLE")
        }
        val state = QuizRuntime.refresh()
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("sessionState", if (publish) "bank" else "draft")
                .put("questionCount", parsed.items.size)
                .put("appended", append.appended)
                .put("duplicates", append.duplicates)
                .put("rejected", append.rejected)
                .put("capacityReached", append.capacityReached)
                .put("counts", bankCountsJson(state.bankCounts))
                .put("expiresAt", parsed.expiresAt)
                .put("syncState", QuizSessionStore.loadSyncState().encode())
                .put("cloudManaged", QuizConfigSync.isCloudManaged()),
        )
    }

    private fun readBank(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val context = subjectContext(session) ?: return invalidSubject()
        val filter = session.parameters["filter"]?.firstOrNull().orEmpty()
            .takeIf { it in BANK_FILTERS } ?: "all"
        val scope = session.parameters["scope"]?.firstOrNull()?.trim()?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
        if (scope != null && scope !in BANK_SCOPES) {
            return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        }
        val offset = session.parameters["offset"]?.firstOrNull()?.toIntOrNull()
            ?.coerceIn(0, 100_000) ?: 0
        val limit = session.parameters["limit"]?.firstOrNull()?.toIntOrNull()
            ?.coerceIn(1, 100) ?: 50
        val legacySource =
            when (session.parameters["source"]?.firstOrNull()?.trim()?.lowercase()) {
                null, "", "all" -> null
                "course", "course_ai", "ai" -> QuizQuestionCache.BankSource.COURSE_AI
                "custom" -> QuizQuestionCache.BankSource.CUSTOM
                else -> return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
            }
        val source = when (scope) {
            "builtin" -> QuizQuestionCache.BankSource.BUILT_IN
            "ai" -> QuizQuestionCache.BankSource.PRIVATE_AI
            "manual" -> QuizQuestionCache.BankSource.CUSTOM
            else -> legacySource
        }
        val collectionId = session.parameters["collectionId"]?.firstOrNull()
            ?.trim()?.takeIf { it.isNotEmpty() }
        if (collectionId != null &&
            QuizCollectionStore.find(collectionId, context.editingUserId) == null
        ) {
            return error(Response.Status.NOT_FOUND, "COLLECTION_NOT_FOUND")
        }
        // The wrong tab is a state view over every source, never a content
        // filter. The built-in tab hides private rows even for the viewer.
        val effectiveFilter = if (scope == "wrong" && filter == "all") "wrong" else filter
        val publicOnly = scope == "builtin"
        val now = System.currentTimeMillis()
        val rows = QuizQuestionCache.listBank(
            QuizQuestionCache.LOCAL_SCOPE,
            effectiveFilter,
            offset,
            limit,
            now,
            source = source,
            collectionId = collectionId,
            subjectUserId = context.editingUserId,
            publicOnly = publicOnly,
            activeSubjectUserId = context.activeUserId,
        )
        val tagNames = QuizCollectionStore.collections(context.editingUserId)
            .associate { it.id to it.name }
        val items = JSONArray()
        rows.forEach { row ->
            val question = row.question
            items.put(
                JSONObject()
                    .put("cacheId", row.cacheId)
                    .put("questionId", question.questionId)
                    .put("collectionId", row.collectionId ?: JSONObject.NULL)
                    .put(
                        "collectionName",
                        row.collectionId?.let { tagNames[it] } ?: JSONObject.NULL,
                    )
                    .put(
                        "scopeSource",
                        when {
                            row.ownerUserId == null && row.collectionId == null -> "builtin"
                            row.ownerUserId != null && row.collectionId == null -> "ai"
                            else -> "manual"
                        },
                    )
                    .put("prompt", question.prompt)
                    .put("options", JSONArray(question.options))
                    .put("type", question.answerKind.wire)
                    .put("presentation", question.presentation.wire)
                    .put("answerIndex", question.answerIndex)
                    .put("answerText", question.options.getOrNull(question.answerIndex).orEmpty())
                    .put("answerIndices", JSONArray(question.correctIndices))
                    .put(
                        "answerTexts",
                        JSONArray(
                            question.correctIndices.mapNotNull { question.options.getOrNull(it) },
                        ),
                    )
                    .put("explanation", question.explanation)
                    .put("subject", question.subject)
                    .put("grade", question.grade)
                    .put("difficulty", question.difficulty)
                    .put("scopeGrade", question.scopeGrade ?: JSONObject.NULL)
                    .put("scopeSemester", question.scopeSemester ?: JSONObject.NULL)
                    .put("scopeSubject", question.scopeSubject)
                    .put("scopeTextbook", question.scopeTextbook)
                    .put("scopeType", question.scopeType)
                    .put("scopeUnitNo", question.scopeUnitNo ?: JSONObject.NULL)
                    .put("points", question.points)
                    .put("timeLimitSec", question.timeLimitSec)
                    .put("status", row.status(now))
                    .put("wrongCount", row.wrongCount)
                    .put("selectedIndex", row.selectedIndex ?: JSONObject.NULL)
                    .put("selectedText", row.selectedIndex?.let { question.options.getOrNull(it) } ?: JSONObject.NULL)
                    .put("selectedIndices", JSONArray(row.selectedIndices))
                    .put(
                        "selectedTexts",
                        JSONArray(
                            row.selectedIndices.mapNotNull { question.options.getOrNull(it) },
                        ),
                    )
                    .put("timedOut", row.timedOut)
                    .put("answeredAt", row.answeredAt ?: 0L)
                    .put("receivedAt", row.receivedAt),
            )
        }
        val counts = QuizQuestionCache.bankCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            now,
            subjectUserId = context.editingUserId,
            activeSubjectUserId = context.activeUserId,
            source = source,
            collectionId = collectionId,
        )
        val scopeCounts = QuizQuestionCache.ownerSourceCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            now,
            subjectUserId = context.editingUserId,
            activeSubjectUserId = context.activeUserId,
        )
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("filter", effectiveFilter)
                .put("scope", scope ?: JSONObject.NULL)
                .put("subjectUserId", context.editingUserId ?: JSONObject.NULL)
                .put("activeUserId", context.activeUserId ?: JSONObject.NULL)
                .put("offset", offset)
                .put("limit", limit)
                .put("items", items)
                .put("counts", bankCountsJson(counts))
                .put(
                    "scopeCounts",
                    JSONObject()
                        .put("builtin", scopeCounts.builtin)
                        .put("ai", scopeCounts.ai)
                        .put("manual", scopeCounts.manual)
                        .put("wrong", scopeCounts.wrong),
                )
                .put(
                    "sourceCounts",
                    QuizQuestionCache.sourceCounts(
                        QuizQuestionCache.LOCAL_SCOPE,
                        subjectUserId = context.editingUserId,
                        publicOnly = publicOnly,
                    ).let {
                        JSONObject().put("course", it.course).put("custom", it.custom)
                    },
                )
                .put("capacity", QuizQuestionCache.BANK_CAPACITY)
                .put("privateCapacity", QuizQuestionCache.PRIVATE_CAPACITY)
                .put(
                    "privateRemaining",
                    QuizQuestionCache.remainingCapacity(context.editingUserId),
                ),
        )
    }

    /**
     * Faceted picker for the custom answer range. Only rows with a complete
     * six-field scope are counted; legacy rows are reported separately as
     * `noScope` instead of silently widening (or emptying) a custom range.
     *
     * Counts for each dimension apply every other active filter, so choosing
     * one field narrows the remaining options like a normal faceted search.
     */
    private fun readBankFacets(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val context = subjectContext(session) ?: return invalidSubject()
        val scope = session.parameters["scope"]?.firstOrNull()?.trim()?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
        if (scope != null && scope !in BANK_SCOPES) {
            return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        }
        fun param(name: String): String? =
            session.parameters[name]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val grade = param("grade")?.let {
            it.toIntOrNull()?.takeIf { value -> QuizAnswerScope.isSupportedGrade(value) }
                ?: return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        }
        val semester = param("semester")?.let {
            it.toIntOrNull()?.takeIf { value -> value in 1..2 }
                ?: return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        }
        val subject = param("subject")?.let {
            val normalized = it.uppercase()
            if (!QuizAnswerScope.isSupportedSubject(normalized)) {
                return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
            }
            normalized
        }
        val textbook = param("textbook")?.let {
            val normalized = it.uppercase()
            if (!QuizAnswerScope.isSupportedTextbook(normalized)) {
                return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
            }
            normalized
        }
        val unitMax = unitMaxForFacets(grade)
        val topic = param("topic")?.let { raw ->
            val normalized = raw.uppercase()
            when {
                normalized.startsWith("UNIT:") -> {
                    val unit = normalized.removePrefix("UNIT:").toIntOrNull()
                        ?.takeIf { unitMax > 0 && it in 1..unitMax }
                        ?: return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
                    "UNIT:$unit"
                }
                QuizAnswerScope.isSupportedScopeType(normalized) &&
                    normalized != "UNIT" -> normalized
                else -> return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
            }
        }
        val difficulty = param("difficulty")?.let {
            val normalized = it.uppercase()
            if (!QuizAnswerScope.isSupportedDifficulty(normalized)) {
                return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
            }
            normalized.ifEmpty { null }
        }
        // "any" means no type filter. The type chips themselves are driven by
        // the answerKind facet, which ignores this filter so every backed type
        // stays selectable while the hierarchy narrows to the chosen type.
        val answerKind = param("answerKind")?.let { raw ->
            if (raw.equals("any", ignoreCase = true)) return@let null
            QuizAnswerKind.fromWire(raw)?.wire
                ?: return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        }
        // The level source editor asks for one provenance bucket so its grade
        // and subject chips only offer branches that bucket can fill. The bank
        // tab omits it and keeps the combined view.
        val origin = param("origin")?.let { raw ->
            CourseOrigin.fromWire(raw)
                ?: return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        } ?: CourseOrigin.ALL
        val filter = FacetFilters(
            grade, semester, subject, textbook, topic, difficulty, answerKind,
        )
        val now = System.currentTimeMillis()
        val snapshot = QuizQuestionCache.facetSnapshot(
            QuizQuestionCache.LOCAL_SCOPE,
            now,
            subjectUserId = context.editingUserId,
            publicOnly = scope == "builtin",
            origin = origin,
        )
        val scopedRows = snapshot.groups.mapNotNull(::facetRow)
        val scoped = scopedRows.sumOf { it.count }
        val matched = scopedRows.filter { it.matches(filter) }.sumOf { it.count }
        val withoutDifficulty = scopedRows
            .filter { it.matches(filter, skip = "difficulty") }
            .sumOf { it.count }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", context.editingUserId ?: JSONObject.NULL)
                .put("total", snapshot.total)
                .put("scoped", scoped)
                .put("noScope", snapshot.total - scoped)
                .put("matched", matched)
                .put("unrestricted", withoutDifficulty)
                .put("filters", filter.toJson())
                .put(
                    "facets",
                    JSONObject()
                        .put("grade", facetCounts(scopedRows, filter, "grade") {
                            it.item.scopeGrade?.toString()
                        })
                        .put("semester", facetCounts(scopedRows, filter, "semester") {
                            it.item.scopeSemester?.toString()
                        })
                        .put("subject", facetCounts(scopedRows, filter, "subject") {
                            it.item.scopeSubject.uppercase()
                        })
                        .put("textbook", facetCounts(scopedRows, filter, "textbook") {
                            it.item.scopeTextbook.uppercase()
                        })
                        .put("topic", facetCounts(scopedRows, filter, "topic") { it.topic })
                        .put("difficulty", facetCounts(scopedRows, filter, "difficulty") {
                            it.item.difficulty.trim().uppercase()
                                .takeIf { value -> value in FACET_DIFFICULTIES }
                        })
                        // No-op when the viewer is already filtering by type:
                        // matches() skips this dimension for its own facet.
                        .put("answerKind", facetCounts(scopedRows, filter, "answerKind") {
                            it.item.answerKind.wire
                        }),
                ),
        )
    }

    private data class FacetFilters(
        val grade: Int?,
        val semester: Int?,
        val subject: String?,
        val textbook: String?,
        val topic: String?,
        val difficulty: String?,
        val answerKind: String? = null,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("grade", grade ?: JSONObject.NULL)
            .put("semester", semester ?: JSONObject.NULL)
            .put("subject", subject ?: JSONObject.NULL)
            .put("textbook", textbook ?: JSONObject.NULL)
            .put("topic", topic ?: JSONObject.NULL)
            .put("difficulty", difficulty ?: JSONObject.NULL)
            .put("answerKind", answerKind ?: JSONObject.NULL)
    }

    private data class FacetRow(val item: QuizItem, val topic: String, val count: Int) {
        /** [skip] excludes one dimension from the match, for its own facet counts. */
        fun matches(filter: FacetFilters, skip: String? = null): Boolean {
            if (skip != "grade" && filter.grade != null && item.scopeGrade != filter.grade) return false
            if (skip != "semester" && filter.semester != null &&
                item.scopeSemester != filter.semester
            ) {
                return false
            }
            if (skip != "subject" && filter.subject != null &&
                !filter.subject.equals(QuizAnswerScope.SUBJECT_ALL, ignoreCase = true) &&
                !item.scopeSubject.equals(filter.subject, ignoreCase = true)
            ) {
                return false
            }
            if (skip != "textbook" && filter.textbook != null &&
                !filter.textbook.equals(QuizAnswerScope.TEXTBOOK_ALL, ignoreCase = true) &&
                !item.scopeTextbook.equals(filter.textbook, ignoreCase = true)
            ) {
                return false
            }
            if (skip != "topic" && filter.topic != null && topic != filter.topic) return false
            if (skip != "difficulty" && filter.difficulty != null &&
                !item.difficulty.equals(filter.difficulty, ignoreCase = true)
            ) {
                return false
            }
            if (skip != "answerKind" && filter.answerKind != null &&
                !item.answerKind.wire.equals(filter.answerKind, ignoreCase = true)
            ) {
                return false
            }
            return true
        }
    }

    private fun facetRow(group: QuizQuestionCache.FacetGroup): FacetRow? =
        facetRow(
            QuizItem(
                questionId = "",
                prompt = "",
                options = emptyList(),
                answerIndex = 0,
                difficulty = group.difficulty,
                scopeGrade = group.grade,
                scopeSemester = group.semester,
                scopeSubject = group.subject,
                scopeTextbook = group.textbook,
                scopeType = group.scopeType,
                scopeUnitNo = group.unitNo,
                // Pre-v7 rows have no answer_kind column value until the
                // backfill runs; they decode as single-choice everywhere else.
                answerKind = QuizAnswerKind.fromWire(group.answerKind)
                    ?: QuizAnswerKind.SINGLE,
            ),
            group.count,
        )

    private fun facetRow(item: QuizItem, count: Int = 1): FacetRow? {
        val grade = item.scopeGrade ?: return null
        val semester = item.scopeSemester ?: return null
        val curriculum = QuizCurriculum.current() ?: return null
        if (!curriculum.isGradeSupported(grade) || semester !in 1..2) return null
        if (isCloudJuniorBlocked(grade)) return null
        if (!curriculum.isSubjectSupported(grade, item.scopeSubject)) return null
        if (!item.scopeTextbook.equals(QuizAnswerScope.TEXTBOOK_ALL, ignoreCase = true) &&
            !curriculum.isTextbookSupported(grade, item.scopeSubject, item.scopeTextbook)
        ) {
            return null
        }
        val type = item.scopeType.trim().uppercase()
        if (!QuizAnswerScope.isSupportedScopeType(type)) return null
        val topic = if (type == "UNIT") {
            val unit = item.scopeUnitNo ?: return null
            val unitMax = QuizUnitCatalog.current()
                ?.unitMax(grade, semester, item.scopeSubject, item.scopeTextbook)
                ?: curriculum.unitMax(grade)
                ?: return null
            if (unit !in 1..unitMax) return null
            "UNIT:$unit"
        } else {
            if (item.scopeUnitNo != null) return null
            type
        }
        return FacetRow(item, topic, count)
    }

    /** Facet queries may omit subject/textbook, so use the widest available limit. */
    private fun unitMaxForFacets(grade: Int?): Int {
        val curriculumMax = if (grade == null) {
            QuizCurriculum.current()?.maxUnit()
        } else {
            QuizCurriculum.current()?.unitMax(grade)
        } ?: 0
        val catalogMax = if (grade == null) {
            QuizUnitCatalog.current()?.maxUnit()
        } else {
            QuizUnitCatalog.current()?.unitMaxForGrade(grade)
        } ?: 0
        return maxOf(curriculumMax, catalogMax)
    }

    private fun facetCounts(
        rows: List<FacetRow>,
        filter: FacetFilters,
        skip: String,
        key: (FacetRow) -> String?,
    ): JSONObject {
        val counts = sortedMapOf<String, Int>()
        rows.asSequence()
            .filter { it.matches(filter, skip) }
            .forEach { row ->
                key(row)?.let { counts[it] = (counts[it] ?: 0) + row.count }
            }
        return JSONObject().apply { counts.forEach { (value, count) -> put(value, count) } }
    }

    /**
     * Strict manual import into one tag. Existing tags append; a new tag is
     * created only for a real import and removed again when the write fails,
     * so a failed batch never leaves an empty tag behind. A dry run never
     * writes either store.
     */
    private fun importBank(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        // Import only appends AVAILABLE rows under a new batch/tag. A running
        // round has already reserved its rows and copied them into the session,
        // so adding questions here cannot change the round on screen; the new
        // rows become candidates for the next round. Destructive maintenance
        // (clear/delete) keeps the SESSION_RUNNING gate.
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val batchMode = body.optBoolean("batchMode", false)
        val collectionId = body.optString("collectionId", "").trim()
        val newCollectionName = body.optString("newCollectionName", "").trim()
        if (collectionId.isEmpty() && newCollectionName.isEmpty()) {
            return error(Response.Status.BAD_REQUEST, "COLLECTION_REQUIRED")
        }
        if (collectionId.isNotEmpty() && newCollectionName.isNotEmpty()) {
            return error(Response.Status.BAD_REQUEST, "COLLECTION_CONFLICT")
        }
        val dryRun = body.optBoolean("dryRun", false)
        val defaultDifficulty = if (body.has("defaultDifficulty") && !body.isNull("defaultDifficulty")) {
            body.optString("defaultDifficulty", "")
        } else {
            null
        }
        val format = when (val raw = body.optString("format", "").trim().lowercase(Locale.ROOT)) {
            "" -> when {
                body.has("items") -> "json"
                body.has("text") -> "text"
                else -> return error(Response.Status.BAD_REQUEST, "INVALID_IMPORT")
            }
            "json", "text" -> raw
            else -> return error(Response.Status.BAD_REQUEST, "INVALID_IMPORT")
        }
        val parsed = if (format == "json") {
            val items = body.optJSONArray("items")
                ?: return error(Response.Status.BAD_REQUEST, "INVALID_IMPORT")
            if (items.length() > QuizBankImport.MAX_ITEMS) {
                return error(Response.Status.BAD_REQUEST, "TOO_MANY_ITEMS")
            }
            QuizBankImport.parseJsonItems(items, defaultDifficulty)
        } else {
            val text = body.optString("text", "")
            if (text.isBlank()) return error(Response.Status.BAD_REQUEST, "INVALID_IMPORT")
            QuizBankImport.parseText(text, defaultDifficulty)
        }
        val items = when (parsed) {
            is QuizImportResult.Success -> parsed.items
            is QuizImportResult.Failure -> return importFailure(parsed.errors)
        }
        if (items.size > QuizBankImport.MAX_ITEMS) {
            return error(Response.Status.BAD_REQUEST, "TOO_MANY_ITEMS")
        }
        if (!QuizQuestionCache.isReady()) {
            return error(Response.Status.INTERNAL_ERROR, "CACHE_UNAVAILABLE")
        }

        val editingUserId = context.editingUserId
        val existing = if (collectionId.isNotEmpty()) {
            QuizCollectionStore.find(collectionId, editingUserId)
        } else {
            null
        }
        if (collectionId.isNotEmpty() && existing == null) {
            return error(Response.Status.NOT_FOUND, "COLLECTION_NOT_FOUND")
        }
        var created: QuizCollection? = null
        val collection = existing ?: run {
            val cleanName = QuizCollection.normalizeName(newCollectionName)
                ?: return error(Response.Status.BAD_REQUEST, "INVALID_COLLECTION_NAME")
            if (QuizCollectionStore.nameExists(cleanName, subjectUserId = editingUserId)) {
                return error(Response.Status.CONFLICT, "COLLECTION_EXISTS")
            }
            if (QuizCollectionStore.collections(editingUserId).size >=
                QuizCollection.MAX_COLLECTIONS
            ) {
                return error(Response.Status.CONFLICT, "COLLECTION_LIMIT")
            }
            if (dryRun) {
                QuizCollection.create(cleanName)
                    ?: return error(Response.Status.BAD_REQUEST, "INVALID_COLLECTION_NAME")
            } else {
                QuizCollectionStore.addCollection(cleanName, editingUserId)
                    ?.also { created = it }
                    ?: return error(
                        Response.Status.CONFLICT,
                        if (QuizCollectionStore.nameExists(
                                cleanName,
                                subjectUserId = editingUserId,
                            )
                        ) {
                            "COLLECTION_EXISTS"
                        } else {
                            "COLLECTION_LIMIT"
                        },
                    )
            }
        }

        // Capacity, dedupe and owner all follow the editing member; an active
        // sibling's private rows never count as duplicates or free slots.
        val privateRemaining = QuizQuestionCache.remainingCapacity(editingUserId)
        if (dryRun) {
            val plan = QuizQuestionCache.previewLocalImport(
                collection.id,
                items,
                ownerUserId = editingUserId,
            )
            val canImport = plan.rejected == 0 &&
                plan.wouldAppend > 0 &&
                plan.wouldAppend <= plan.remaining
            return json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put("dryRun", true)
                    .put("subjectUserId", editingUserId ?: JSONObject.NULL)
                    .put("collection", collectionJson(collection))
                    .put("total", plan.total)
                    .put("fileDuplicates", plan.fileDuplicates)
                    .put("collectionDuplicates", plan.collectionDuplicates)
                    .put("crossSourceDuplicates", plan.crossSourceDuplicates)
                    .put("rejected", plan.rejected)
                    .put("wouldAppend", plan.wouldAppend)
                    .put("capacity", QuizQuestionCache.BANK_CAPACITY)
                    .put("remaining", plan.remaining)
                    .put("privateCapacity", QuizQuestionCache.PRIVATE_CAPACITY)
                    .put("privateRemaining", privateRemaining)
                    .put("canImport", canImport)
                    .put("preview", importPreviewJson(plan.preview)),
            )
        }

        val result = QuizQuestionCache.importLocalQuestions(
            collection.id,
            "import-" + UUID.randomUUID().toString().replace("-", "").take(16),
            items,
            ownerUserId = editingUserId,
        )
        if (result.storageError || result.rejected > 0 || result.capacityReached) {
            created?.let { QuizCollectionStore.removeCollection(it.id, editingUserId) }
            return when {
                result.storageError ->
                    error(Response.Status.INTERNAL_ERROR, "CACHE_UNAVAILABLE")
                result.capacityReached -> json(
                    Response.Status.CONFLICT,
                    JSONObject()
                        .put("ok", false)
                        .put("error", "BANK_FULL")
                        .put("remaining", result.remaining)
                        .put("capacity", QuizQuestionCache.BANK_CAPACITY),
                )
                else -> error(Response.Status.BAD_REQUEST, "INVALID_IMPORT")
            }
        }
        val response = JSONObject()
            .put("ok", true)
            .put("dryRun", false)
            .put("subjectUserId", editingUserId ?: JSONObject.NULL)
            .put("collection", collectionJson(collection))
            .put("appended", result.appended)
            .put("fileDuplicates", result.fileDuplicates)
            .put("collectionDuplicates", result.collectionDuplicates)
            .put("crossSourceDuplicates", result.crossSourceDuplicates)
            .put("rejected", result.rejected)
            .put("capacity", QuizQuestionCache.BANK_CAPACITY)
            .put("remaining", result.remaining)
            .put("privateCapacity", QuizQuestionCache.PRIVATE_CAPACITY)
            .put("privateRemaining", QuizQuestionCache.remainingCapacity(editingUserId))
        // A batched import refreshes the whole status once at the end. Counting
        // every tag and source after each 200-row batch would multiply the
        // same work by the number of batches.
        if (!batchMode) {
            val collectionCounts = QuizQuestionCache.collectionCounts(
                QuizQuestionCache.LOCAL_SCOPE,
                subjectUserId = editingUserId,
                activeSubjectUserId = context.activeUserId,
            ).firstOrNull { it.collectionId == collection.id }?.counts
                ?: QuizQuestionCache.BankCounts()
            val sourceCounts = QuizQuestionCache.sourceCounts(
                QuizQuestionCache.LOCAL_SCOPE,
                subjectUserId = editingUserId,
            )
            val counts = QuizQuestionCache.bankCounts(
                QuizQuestionCache.LOCAL_SCOPE,
                subjectUserId = editingUserId,
                activeSubjectUserId = context.activeUserId,
            )
            response
                .put("counts", bankCountsJson(counts))
                .put(
                    "sourceCounts",
                    JSONObject()
                        .put("course", sourceCounts.course)
                        .put("custom", sourceCounts.custom),
                )
                .put(
                    "collectionCounts",
                    JSONObject()
                        .put("total", collectionCounts.total)
                        .put("unused", collectionCounts.unused),
                )
        }
        return json(Response.Status.OK, response)
    }

    private fun importFailure(errors: List<QuizImportError>): Response {
        val only = errors.singleOrNull()
        if (only != null && only.code == "TOO_MANY_ITEMS") {
            return error(Response.Status.BAD_REQUEST, "TOO_MANY_ITEMS")
        }
        return json(
            Response.Status.BAD_REQUEST,
            JSONObject()
                .put("ok", false)
                .put("error", "INVALID_IMPORT")
                .put(
                    "errors",
                    JSONArray().apply {
                        errors.take(QuizBankImport.MAX_ERRORS).forEach { error ->
                            put(
                                JSONObject()
                                    .put("index", error.index)
                                    .put("field", error.field)
                                    .put("code", error.code),
                            )
                        }
                    },
                ),
        )
    }

    private fun collectionJson(collection: QuizCollection): JSONObject =
        JSONObject().put("id", collection.id).put("name", collection.name)

    private fun readCollections(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val context = subjectContext(session) ?: return invalidSubject()
        val packages = levelPackagesFor(context.editingUserId)
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", context.editingUserId ?: JSONObject.NULL)
                .put("activeUserId", context.activeUserId ?: JSONObject.NULL)
                .put("maxCollections", QuizCollection.MAX_COLLECTIONS)
                .put(
                    "collections",
                    collectionsJson(context.editingUserId, context.activeUserId, packages),
                ),
        )
    }

    private fun createCollection(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val subject = context.editingUserId
        val name = QuizCollection.normalizeName(body.optString("name", ""))
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_COLLECTION_NAME")
        if (QuizCollectionStore.nameExists(name, subjectUserId = subject)) {
            return error(Response.Status.CONFLICT, "COLLECTION_EXISTS")
        }
        if (QuizCollectionStore.collections(subject).size >= QuizCollection.MAX_COLLECTIONS) {
            return error(Response.Status.CONFLICT, "COLLECTION_LIMIT")
        }
        val created = QuizCollectionStore.addCollection(name, subject)
            ?: return error(
                Response.Status.CONFLICT,
                if (QuizCollectionStore.nameExists(name, subjectUserId = subject)) {
                    "COLLECTION_EXISTS"
                } else {
                    "COLLECTION_LIMIT"
                },
            )
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", subject ?: JSONObject.NULL)
                .put("collection", collectionJson(created))
                .put("maxCollections", QuizCollection.MAX_COLLECTIONS),
        )
    }

    private fun renameCollection(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val subject = context.editingUserId
        val id = body.optString("id", "").trim().takeIf { it.isNotEmpty() }
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        if (QuizCollectionStore.find(id, subject) == null) {
            return error(Response.Status.NOT_FOUND, "COLLECTION_NOT_FOUND")
        }
        val name = QuizCollection.normalizeName(body.optString("name", ""))
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_COLLECTION_NAME")
        if (QuizCollectionStore.nameExists(name, excludingId = id, subjectUserId = subject)) {
            return error(Response.Status.CONFLICT, "COLLECTION_EXISTS")
        }
        val renamed = QuizCollectionStore.renameCollection(id, name, subject)
            ?: return error(Response.Status.CONFLICT, "COLLECTION_EXISTS")
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", subject ?: JSONObject.NULL)
                .put("collection", collectionJson(renamed)),
        )
    }

    private fun deleteCollection(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session)
        val context = subjectContext(session, body) ?: return invalidSubject()
        val subject = context.editingUserId
        val id = body?.optString("id", "")?.trim()?.takeIf { it.isNotEmpty() }
            ?: session.parameters["id"]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        QuizCollectionStore.find(id, subject)
            ?: return error(Response.Status.NOT_FOUND, "COLLECTION_NOT_FOUND")
        val usedByPackage = levelPackagesFor(subject).any { levelPackage ->
            levelPackage.sources.any {
                it is QuizPackageSource.Collection && it.collectionId == id
            }
        }
        val usedByLegacyLevel = QuizCollectionStore.customLevels(subject)
            .any { it.collectionId == id }
        if (usedByPackage || usedByLegacyLevel) {
            return error(Response.Status.CONFLICT, "COLLECTION_IN_USE")
        }
        val counts = QuizQuestionCache.collectionCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            subjectUserId = subject,
            activeSubjectUserId = context.activeUserId,
        ).firstOrNull { it.collectionId == id }?.counts
        if (counts != null && counts.total > 0) {
            return error(Response.Status.CONFLICT, "COLLECTION_NOT_EMPTY")
        }
        if (!QuizCollectionStore.removeCollection(id, subject)) {
            return error(Response.Status.CONFLICT, "COLLECTION_IN_USE")
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", subject ?: JSONObject.NULL)
                .put("id", id),
        )
    }

    private fun importPreviewJson(items: List<QuizItem>): JSONArray = JSONArray().apply {
        items.take(20).forEachIndexed { index, item ->
            put(
                JSONObject()
                    .put("index", index)
                    .put("prompt", item.prompt)
                    .put("options", JSONArray(item.options))
                    .put("type", item.answerKind.wire)
                    .put("answerIndex", item.answerIndex)
                    .put("answerIndices", JSONArray(item.correctIndices))
                    .put(
                        "answerTexts",
                        JSONArray(item.correctIndices.mapNotNull { item.options.getOrNull(it) }),
                    )
                    .put("explanation", item.explanation)
                    .put("difficulty", item.difficulty),
            )
        }
    }

    /**
     * V41 destructive actions. Content deletion is always narrowed to one
     * owner scope: public built-in rows, the editing member's AI rows, or one
     * of the editing member's tags. The wrong book is a state-only reset and
     * may optionally name one `cacheId`.
     */
    private fun clearBank(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        if (QuizRuntime.refresh().session?.status in ACTIVE_STATUSES) {
            return error(Response.Status.CONFLICT, "SESSION_RUNNING")
        }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val editingUserId = context.editingUserId
        val activeUserId = context.activeUserId
        val wrongBook = session.uri.endsWith("/wrong-book/clear")
        val scope = body.optString("scope", "").trim().lowercase(Locale.ROOT)
            .takeIf { it.isNotEmpty() }
        if (scope != null && scope !in BANK_SCOPES) {
            return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        }
        val cacheId = body.optString("cacheId", "").trim().takeIf { it.isNotEmpty() }
        if (cacheId != null) {
            if (!wrongBook && body.optString("mode", "") != "wrong") {
                return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
            }
            if (!QuizQuestionCache.isVisibleContent(cacheId, subjectUserId = editingUserId)) {
                return error(Response.Status.NOT_FOUND, "NOT_FOUND")
            }
            if (!QuizQuestionCache.clearWrongState(
                    cacheId,
                    subjectUserId = editingUserId,
                    activeSubjectUserId = activeUserId,
                )
            ) {
                return error(Response.Status.INTERNAL_ERROR, "CACHE_UNAVAILABLE")
            }
            return clearResponse(context, null)
        }
        if (scope == "wrong" || wrongBook) {
            if (wrongBook && scope != null) {
                return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
            }
            val mode = body.optString("mode", "wrong")
            if (mode != "wrong") return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
            if (!QuizQuestionCache.clearBank(
                    QuizQuestionCache.LOCAL_SCOPE,
                    QuizQuestionCache.ClearMode.WRONG,
                    source = QuizQuestionCache.BankSource.ALL,
                    subjectUserId = editingUserId,
                    activeSubjectUserId = activeUserId,
                )
            ) {
                return error(Response.Status.INTERNAL_ERROR, "CACHE_UNAVAILABLE")
            }
            return clearResponse(context, "wrong")
        }
        // The built-in bank is public content: only a whole clear is allowed,
        // and it can never be narrowed by one child's answer state.
        if (scope == "builtin") {
            val mode = body.optString("mode", "all").trim().lowercase(Locale.ROOT)
            if (mode != "all") return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
            if (!QuizQuestionCache.clearBank(
                    QuizQuestionCache.LOCAL_SCOPE,
                    QuizQuestionCache.ClearMode.ALL,
                    source = QuizQuestionCache.BankSource.BUILT_IN,
                    subjectUserId = editingUserId,
                    publicOnly = true,
                    activeSubjectUserId = activeUserId,
                )
            ) {
                return error(Response.Status.INTERNAL_ERROR, "CACHE_UNAVAILABLE")
            }
            return clearResponse(context, "builtin")
        }
        val legacySource = body.optString("source", "").trim().lowercase(Locale.ROOT)
        val source = when (scope) {
            "ai" -> QuizQuestionCache.BankSource.PRIVATE_AI
            "manual" -> QuizQuestionCache.BankSource.CUSTOM
            else -> when (legacySource) {
                "course", "course_ai", "ai" -> if (editingUserId == null) {
                    QuizQuestionCache.BankSource.COURSE_AI
                } else {
                    QuizQuestionCache.BankSource.PRIVATE_AI
                }
                "custom" -> QuizQuestionCache.BankSource.CUSTOM
                "all" -> return error(Response.Status.BAD_REQUEST, "SCOPE_REQUIRED")
                "" -> if (editingUserId == null) {
                    QuizQuestionCache.BankSource.COURSE_AI
                } else {
                    QuizQuestionCache.BankSource.PRIVATE_AI
                }
                else -> return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
            }
        }
        val modeValue = body.optString("mode", "unused")
        val mode = when (modeValue) {
            "unused" -> QuizQuestionCache.ClearMode.UNUSED
            "answered" -> QuizQuestionCache.ClearMode.ANSWERED
            "correct" -> QuizQuestionCache.ClearMode.CORRECT
            "all" -> QuizQuestionCache.ClearMode.ALL
            else -> return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        }
        val collectionId = body.optString("collectionId", "").trim().takeIf { it.isNotEmpty() }
        if (collectionId != null &&
            QuizCollectionStore.find(collectionId, editingUserId) == null
        ) {
            return error(Response.Status.NOT_FOUND, "COLLECTION_NOT_FOUND")
        }
        // A family with no roster is the pre-V41 single-member TV; there the
        // legacy namespace and its old clear semantics still apply.
        val legacy = editingUserId == null
        if (!QuizQuestionCache.clearBank(
                QuizQuestionCache.LOCAL_SCOPE,
                mode,
                source = if (legacy && legacySource == "all") {
                    QuizQuestionCache.BankSource.ALL
                } else {
                    source
                },
                collectionId = collectionId,
                subjectUserId = editingUserId,
                activeSubjectUserId = activeUserId,
                exactOwner = !legacy,
            )
        ) {
            return error(Response.Status.INTERNAL_ERROR, "CACHE_UNAVAILABLE")
        }
        return clearResponse(context, scope ?: "legacy")
    }

    private fun clearResponse(
        context: SubjectContext,
        scope: String?,
    ): Response {
        val editingUserId = context.editingUserId
        val activeUserId = context.activeUserId
        val state = if (context.editingIsActive) QuizRuntime.refresh() else null
        val counts = state?.bankCounts ?: QuizQuestionCache.bankCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            subjectUserId = editingUserId,
            activeSubjectUserId = activeUserId,
        )
        val sourceCounts = QuizQuestionCache.sourceCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            subjectUserId = editingUserId,
        )
        val scopeCounts = QuizQuestionCache.ownerSourceCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            subjectUserId = editingUserId,
            activeSubjectUserId = activeUserId,
        )
        val bankState = state?.bankState ?: editingBankState(
            packages = levelPackagesFor(editingUserId),
            documentUnreadable = false,
            packagesUnreadable = false,
            subjectUserId = editingUserId,
            activeSubjectUserId = activeUserId,
            nowMs = System.currentTimeMillis(),
        )
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("scope", scope ?: JSONObject.NULL)
                .put("subjectUserId", editingUserId ?: JSONObject.NULL)
                .put("activeUserId", activeUserId ?: JSONObject.NULL)
                .put("counts", bankCountsJson(counts))
                .put(
                    "sourceCounts",
                    JSONObject()
                        .put("course", sourceCounts.course)
                        .put("custom", sourceCounts.custom),
                )
                .put(
                    "scopeCounts",
                    JSONObject()
                        .put("builtin", scopeCounts.builtin)
                        .put("ai", scopeCounts.ai)
                        .put("manual", scopeCounts.manual)
                        .put("wrong", scopeCounts.wrong),
                )
                .put("privateCapacity", QuizQuestionCache.PRIVATE_CAPACITY)
                .put(
                    "privateRemaining",
                    QuizQuestionCache.remainingCapacity(editingUserId),
                )
                .put("bankState", bankState)
                .put("presetBank", presetBankJson(editingUserId, activeUserId)),
        )
    }

    /**
     * Stock of the bundled, non-curriculum bank. It lives outside the course
     * scopes, so the page needs its own count instead of the builtin tab total
     * (which also contains curriculum rows).
     */
    private fun presetBankJson(
        subjectUserId: String?,
        activeSubjectUserId: String?,
    ): JSONObject {
        val counts = QuizQuestionCache.bankCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            subjectUserId = subjectUserId,
            activeSubjectUserId = activeSubjectUserId,
            presetBank = QuizPresetBank.PARENT_LIFE,
        )
        return JSONObject()
            .put("bankId", QuizPresetBank.PARENT_LIFE.wire)
            .put("total", counts.total)
            .put("available", counts.unused)
    }

    private fun readCache(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        return json(Response.Status.OK, cacheJson())
    }

    private fun clearCache(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        if (QuizRuntime.refresh().session?.status in ACTIVE_STATUSES) {
            return error(Response.Status.CONFLICT, "SESSION_RUNNING")
        }
        // The legacy "unused" sweep used to delete every untagged row,
        // including public seeds and a sibling's AI questions. Under V41 it
        // only touches the active member's own AI content.
        val family = FamilyHub.state.value
        val owner = family.activeUserId.takeIf { family.ready }
        val cleared = if (owner == null) {
            QuizQuestionCache.clearBank(
                QuizQuestionCache.LOCAL_SCOPE,
                QuizQuestionCache.ClearMode.UNUSED,
                source = QuizQuestionCache.BankSource.COURSE_AI,
            )
        } else {
            QuizQuestionCache.clearBank(
                QuizQuestionCache.LOCAL_SCOPE,
                QuizQuestionCache.ClearMode.UNUSED,
                source = QuizQuestionCache.BankSource.PRIVATE_AI,
                subjectUserId = owner,
                activeSubjectUserId = owner,
                exactOwner = true,
            )
        }
        if (!cleared) {
            return error(Response.Status.INTERNAL_ERROR, "CACHE_UNAVAILABLE")
        }
        return json(Response.Status.OK, cacheJson())
    }

    private fun rebuildCache(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        // V17 has no published session to rebuild from; the bank is the source
        // of truth. A cached old page gets an explicit answer instead of a
        // silent replacement of the local bank.
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("deprecated", true)
                .put("reason", "BANK_MODEL"),
        )
    }

    private fun cacheJson(): JSONObject {
        val state = QuizRuntime.refresh()
        val source = QuizSessionStore.loadSourceConfig()
        // LOCAL-only surface: project the active document, never the old key.
        val engine = QuizEngineConfig.from(state.defaultChallengeConfig)
        val counts = state.bankCounts
        return JSONObject()
            .put("ok", true)
            .put("ready", QuizQuestionCache.isReady())
            .put("enabled", state.enabled)
            .put("bankState", state.bankState)
            .put("localBankActive", state.localBankActive)
            .put("source", source.source.name)
            .put("sourceConfig", source.encode())
            .put("engineConfig", JSONObject()
                .put("pullCount", engine.pullCount)
                .put("defaultTimeLimitSec", engine.defaultTimeLimitSec)
                .put("answerDisplaySec", engine.answerDisplaySec)
                .put("minutesPerQuestion", engine.minutesPerQuestion)
                .put("shuffleOptions", engine.shuffleOptions))
            .put("counts", bankCountsJson(counts))
            .put("cache", legacyCacheJson(counts))
    }

    private fun saveChallengeConfig(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        // Challenge rules are editable even on a bound TV: they are uploaded by
        // afterLocalEdit and the backend normalizes the source to CLOUD.
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val editingUserId = context.editingUserId
        val configObject = body.optJSONObject("config")
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_CONFIG")
        val config = QuizSessionCodec.decodeConfig(configObject)
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_CONFIG")
        // Field ranges are enforced by decodeConfig. Cross-field combinations
        // (cap below one reward, threshold above cap) are intentional parent
        // choices and only produce soft warnings on the web page.
        // Level packages (V37) are the only writable level model: one package
        // can mix course, tag and wrong-review sources. A page cached before
        // V37 still posts the three old fields; reject it so it reloads instead
        // of silently writing a list that cannot represent mixed sources.
        // Rules-only saves omit every level field and leave packages untouched.
        val legacyLevelField = LEGACY_LEVEL_FIELDS.firstOrNull { body.has(it) }
        if (legacyLevelField != null) {
            return error(Response.Status.CONFLICT, "LEVEL_MODEL_UPGRADED", legacyLevelField)
        }
        val packages: List<QuizLevelPackage>? = when {
            !body.has("levelPackages") -> null
            // An explicit null clears every level, matching the old sections.
            body.isNull("levelPackages") -> emptyList()
            else -> when (
                val parsed = parseLevelPackages(
                    body.optJSONArray("levelPackages"),
                    editingUserId,
                )
            ) {
                is LevelPackagesResult.Ok -> parsed.packages
                is LevelPackagesResult.Invalid ->
                    return error(Response.Status.BAD_REQUEST, parsed.code, parsed.detail)
            }
        }
        if (packages != null && packages.any { levelPackage ->
                levelPackage.sources.any {
                    it is QuizPackageSource.Course && isCloudJuniorBlocked(it.scope.grade)
                }
            }
        ) {
            return error(Response.Status.CONFLICT, "CLOUD_JUNIOR_UNSUPPORTED")
        }
        // Phase 1 adapter: the old form edits the selected member's QCD in
        // place. The active child's document is only touched when that member
        // is the one being edited.
        val current = when (
            val loaded = if (context.editingIsActive) {
                QuizConfigDocumentStore.ensureLocalDocument()
            } else {
                documentForEditing(editingUserId!!)
            }
        ) {
            is QuizDocLoadResult.Ok -> loaded.document
            is QuizDocLoadResult.Unreadable -> return error(
                Response.Status.CONFLICT,
                "DOCUMENT_UNREADABLE",
            )
            QuizDocLoadResult.Storage -> return error(
                Response.Status.INTERNAL_ERROR,
                "STORAGE_ERROR",
            )
        }
        // A cached page has no feedback object and no feedbackExtraHoldSec key,
        // so simply opening and saving it must not wipe a JSON-set value.
        val feedbackRequest = body.optJSONObject("feedback")
        val feedback = QuizLegacyConfigAdapter.FeedbackOverride(
            extraHoldSec = when {
                feedbackRequest?.has("extraHoldSec") == true ->
                    feedbackRequest.optInt("extraHoldSec")
                configObject.has("feedbackExtraHoldSec") -> config.feedbackExtraHoldSec
                else -> null
            },
            answerRevealSec = if (feedbackRequest?.has("answerRevealSec") == true) {
                feedbackRequest.optInt("answerRevealSec")
            } else {
                null
            },
            autoNext = if (feedbackRequest?.has("autoNext") == true) {
                feedbackRequest.optBoolean("autoNext")
            } else {
                null
            },
        )
        val updated = QuizLegacyConfigAdapter.apply(
            current = current,
            config = config,
            feedback = feedback,
            hasAnswerDisplaySec = configObject.has("answerDisplaySec"),
            // Packages now own the level list; the legacy QCD levels stay as
            // the last pre-V37 snapshot for a downgrade.
            levels = null,
        )
        val next = if (packages != null) {
            QuizLevelPackageStore.withPackages(updated, packages)
        } else {
            updated
        }
        // A package save does not touch the legacy scope store, so compare the
        // package-derived first course scope before and after this edit.
        val previousScopeSignature = if (packages != null) {
            firstCourseScope(storedPackages(current, editingUserId))?.signature()
        } else {
            null
        }
        // One write: rules, default profile and packages land in the same revision.
        val saved = when (
            val result = if (context.editingIsActive) {
                QuizConfigDocumentStore.saveLocal(next, current.revision)
            } else {
                // editingIsActive is false only for a named sibling, so this
                // context always carries an editing member here.
                QuizConfigDocumentStore.saveLocalFor(editingUserId!!, next, current.revision)
            }
        ) {
            is QuizDocSaveResult.Ok -> result.document
            is QuizDocSaveResult.Conflict -> return error(
                Response.Status.CONFLICT,
                "REVISION_CONFLICT",
            )
            is QuizDocSaveResult.Invalid -> return invalidDocument("INVALID_DOCUMENT", result.issues)
            QuizDocSaveResult.Storage -> return error(
                Response.Status.INTERNAL_ERROR,
                "STORAGE_ERROR",
            )
        }
        val effectiveConfig = saved.toLegacyConfig()
            ?: return error(Response.Status.INTERNAL_ERROR, "DOCUMENT_PROJECTION_FAILED")
        val effectivePackages = if (packages != null) {
            packages
        } else {
            storedPackages(saved, editingUserId)
        }
        // Cloud sync and the TV state belong to the active child. A sibling's
        // document is written directly and picked up when that child is
        // switched on the TV, never pushed as the active document.
        if (context.editingIsActive) {
            QuizConfigSync.afterLocalEdit()
            if (previousScopeSignature != null &&
                previousScopeSignature != firstCourseScope(effectivePackages)?.signature()
            ) {
                QuizConfigSync.afterLocalScopeChange()
            }
        }
        val projection = legacyLevelProjection(
            effectivePackages,
            editingUserId,
            context.activeUserId,
        )
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", editingUserId ?: JSONObject.NULL)
                .put("activeUserId", context.activeUserId ?: JSONObject.NULL)
                .put("editingIsActive", context.editingIsActive)
                .put("config", QuizSessionCodec.encodeConfig(effectiveConfig))
                .put("levelModel", LEVEL_MODEL)
                .put(
                    "levelPackages",
                    levelPackagesJson(
                        effectivePackages,
                        editingUserId,
                        context.activeUserId,
                    ),
                )
                .put("levelPackagesUnreadable", false)
                .put("answerScope", projection.answerScope?.encode() ?: JSONObject.NULL)
                .put(
                    "answerScopeMode",
                    if (projection.answerScope == null) "bound" else "custom",
                )
                .put("answerScopes", projection.answerScopes)
                .put("customLevels", projection.customLevels)
                .put("wrongLevel", projection.wrongLevel ?: JSONObject.NULL)
                .put(
                    "answerScopeAvailable",
                    QuizQuestionCache.availableCount(
                        QuizQuestionCache.LOCAL_SCOPE,
                        answerScope = projection.answerScope,
                        subjectUserId = editingUserId,
                        activeSubjectUserId = context.activeUserId,
                    ),
                )
                .put("syncState", QuizSessionStore.loadSyncState().encode()),
        )
    }

    /**
     * The full QCD of this TV. [docState] explains why [document] is null: an
     * unreadable document is an explicit state, never an empty config.
     */
    private fun readConfigDocument(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val context = subjectContext(session) ?: return invalidSubject()
        val editingUserId = context.editingUserId
        val loaded = if (context.editingIsActive) {
            QuizConfigDocumentStore.ensureLocalDocument()
        } else {
            QuizConfigDocumentStore.localDocumentFor(editingUserId!!)
        }
        return when (loaded) {
            is QuizDocLoadResult.Ok ->
                json(
                    Response.Status.OK,
                    documentJson(loaded.document)
                        .put("docState", "OK")
                        .put("subjectUserId", editingUserId ?: JSONObject.NULL)
                        .put("activeUserId", context.activeUserId ?: JSONObject.NULL),
                )
            is QuizDocLoadResult.Unreadable -> {
                val absent = !context.editingIsActive &&
                    loaded.errors.singleOrNull() == "quiz.doc.local:ABSENT"
                val migrationInvalid = loaded.errors.any { it.startsWith("legacyConfig:") }
                json(
                    Response.Status.OK,
                    JSONObject()
                        .put("ok", true)
                        .put("subjectUserId", editingUserId ?: JSONObject.NULL)
                        .put("activeUserId", context.activeUserId ?: JSONObject.NULL)
                        .put(
                            "docState",
                            when {
                                absent -> "ABSENT"
                                migrationInvalid -> "MIGRATION_INVALID"
                                else -> "UNREADABLE"
                            },
                        )
                        .put("document", JSONObject.NULL)
                        .put("issues", JSONArray(loaded.errors)),
                )
            }
            QuizDocLoadResult.Storage -> error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
    }

    /**
     * Whole-document replace. The TV owns the revision counter: [baseRevision]
     * is only a compare-and-swap guard, and the stored revision always advances
     * by one. A running round keeps the snapshot it froze at start.
     */
    private fun saveConfigDocument(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val editingUserId = context.editingUserId
        val documentObject = body.optJSONObject("document")
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_DOCUMENT")
        val baseRevision = if (body.has("baseRevision") && !body.isNull("baseRevision")) {
            val raw = body.optLong("baseRevision", Long.MIN_VALUE)
            if (raw == Long.MIN_VALUE) {
                return error(Response.Status.BAD_REQUEST, "INVALID_DOCUMENT")
            }
            raw
        } else {
            null
        }
        val document = when (val decoded = QuizConfigDocumentCodec.decode(documentObject.toString())) {
            is QuizDocDecodeResult.Success -> decoded.document
            is QuizDocDecodeResult.Failure ->
                return invalidDocument("INVALID_DOCUMENT", decoded.issues)
        }
        val issues = QuizLocalConfigDocument.localWriteIssues(document)
        if (issues.isNotEmpty()) {
            return invalidDocument(
                if (issues.all { it.code in CAPABILITY_ISSUE_CODES }) "UNSUPPORTED" else "INVALID_DOCUMENT",
                issues,
            )
        }
        val result = if (context.editingIsActive) {
            QuizConfigDocumentStore.saveLocal(document, baseRevision)
        } else {
            QuizConfigDocumentStore.saveLocalFor(editingUserId!!, document, baseRevision)
        }
        return when (val saved = result) {
            is QuizDocSaveResult.Ok -> {
                // A sibling's document is only written; it must not become the
                // active/cloud document until the TV switches to that child.
                if (context.editingIsActive) {
                    QuizConfigSync.afterLocalEdit()
                    QuizRuntime.refresh()
                }
                json(
                    Response.Status.OK,
                    documentJson(saved.document)
                        .put("docState", "OK")
                        .put("subjectUserId", editingUserId ?: JSONObject.NULL)
                        .put("activeUserId", context.activeUserId ?: JSONObject.NULL),
                )
            }
            is QuizDocSaveResult.Conflict -> json(
                Response.Status.CONFLICT,
                JSONObject()
                    .put("ok", false)
                    .put("error", "REVISION_CONFLICT")
                    .put("revision", saved.current?.revision ?: 0L)
                    .put("etag", saved.current?.etag ?: "")
                    .put(
                        "document",
                        saved.current?.let { QuizConfigDocumentCodec.encodeDocument(it) }
                            ?: JSONObject.NULL,
                    ),
            )
            is QuizDocSaveResult.Invalid -> invalidDocument(
                if (saved.issues.all { it.code in CAPABILITY_ISSUE_CODES }) {
                    "UNSUPPORTED"
                } else {
                    "INVALID_DOCUMENT"
                },
                saved.issues,
            )
            QuizDocSaveResult.Storage -> error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
    }

    private fun documentJson(document: QuizConfigDocument): JSONObject = JSONObject()
        .put("ok", true)
        .put("document", QuizConfigDocumentCodec.encodeDocument(document))
        .put("revision", document.revision)
        .put("etag", document.etag)
        .put("levelsEtag", document.levelsEtag)
        .put("origin", document.origin.name)

    private fun invalidDocument(errorCode: String, issues: List<QuizDocIssue>): Response = json(
        Response.Status.BAD_REQUEST,
        JSONObject()
            .put("ok", false)
            .put("error", errorCode)
            .put("issues", JSONArray().apply {
                issues.forEach {
                    put(JSONObject().put("field", it.field).put("code", it.code))
                }
            }),
    )

    private fun deleteSession(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val state = QuizRuntime.refresh()
        val current = state.session
        if (current?.status in ACTIVE_STATUSES) return error(Response.Status.CONFLICT, "SESSION_RUNNING")
        if (current != null) {
            if (QuizChallenge.isCloudSession(current)) {
                CloudQuizPreparer.discardCloudState(PolicyModeCoordinator.state.value.deviceNo)
            } else {
                val scope = QuizRecovery.scopeFor(current, PolicyModeCoordinator.state.value.deviceNo)
                if (scope != null) {
                    QuizQuestionCache.settleRound(
                        current.id,
                        scope,
                        QuizRecovery.answerWrites(current),
                    )
                }
                QuizQuestionCache.release(current.id)
                if (!QuizSessionStore.clearSession()) {
                    return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
                }
            }
        } else if (QuizSessionStore.hasStoredSession()) {
            // The parent asked to delete a record the current codec cannot
            // read; without this branch the setup screen could never be reset.
            QuizQuestionCache.releaseOrphans(null)
            if (!QuizSessionStore.quarantineUnreadableSession()) {
                return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
            }
        }
        QuizRuntime.refresh()
        return json(Response.Status.OK, JSONObject().put("ok", true))
    }

    private fun syncStatus(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        QuizConfigSync.refreshFromCloudAsync()
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("syncState", QuizSessionStore.loadSyncState().encode())
                .put("cloudManaged", QuizConfigSync.isCloudManaged()),
        )
    }

    /** Explicit user retry for a pending upload or a failed remote refresh. */
    private fun syncRetry(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        QuizConfigSync.retryNow()
        QuizConfigSync.refreshFromCloudAsync(force = true)
        return json(
            Response.Status.OK,
            JSONObject().put("ok", true).put("syncState", QuizSessionStore.loadSyncState().encode()),
        )
    }

    /**
     * Conflict resolution chosen by the parent. This is the only path that discards an unsent
     * local draft; it never runs automatically.
     */
    private fun syncUseRemote(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        QuizConfigSync.useRemoteAfterConflictAsync()
        return json(
            Response.Status.OK,
            JSONObject().put("ok", true).put("syncState", QuizSessionStore.loadSyncState().encode()),
        )
    }

    private fun readProvider(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        return json(Response.Status.OK, providerJson())
    }

    private fun setConsent(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        if (body.optBoolean("accepted", false)) {
            QuizSessionStore.acceptConsent()
        } else {
            QuizSessionStore.revokeConsent()
        }
        return json(Response.Status.OK, providerJson())
    }

    private fun saveProvider(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        if (!QuizCrypto.isEncrypted) return error(Response.Status.CONFLICT, "AI_UNSUPPORTED")
        if (!QuizSessionStore.consentAccepted()) return error(Response.Status.CONFLICT, "CONSENT_REQUIRED")
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val current = QuizSessionStore.loadProviderMeta()
        val base = QuizProviderMeta.normalizeBaseUrl(
            body.optString("baseUrl", current.baseUrl),
        ) ?: return error(Response.Status.BAD_REQUEST, "PROVIDER_CONFIG")
        val model = QuizProviderMeta.normalizeModel(
            body.optString("model", current.model),
        ) ?: return error(Response.Status.BAD_REQUEST, "PROVIDER_CONFIG")
        val supplied = body.optString("apiKey", "").trim()
        val key = if (supplied.isEmpty()) QuizSessionStore.providerKey() else normalizeKey(supplied)
        if (key == null) {
            return error(
                Response.Status.BAD_REQUEST,
                if (supplied.isEmpty()) "PROVIDER_KEY_REQUIRED" else "PROVIDER_KEY_INVALID",
            )
        }
        val speech = body.optJSONObject("speech")?.let { raw ->
            parseSpeechSettings(raw, QuizSessionStore.loadSpeechSettings())
                ?: return error(Response.Status.BAD_REQUEST, "INVALID_SPEECH_SETTINGS")
        }
        if (speech != null && speech.enabled && !speech.hasContent) {
            return error(Response.Status.BAD_REQUEST, "SPEECH_CONTENT_REQUIRED")
        }
        if (!QuizSessionStore.saveProviderMeta(QuizProviderMeta(base, model))) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        if (supplied.isNotEmpty() && !QuizSessionStore.saveProviderKey(key)) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        if (speech != null && !QuizSessionStore.saveSpeechSettings(speech)) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        return json(Response.Status.OK, providerJson())
    }

    private fun testProvider(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        if (!QuizCrypto.isEncrypted) return error(Response.Status.CONFLICT, "AI_UNSUPPORTED")
        if (!QuizSessionStore.consentAccepted()) return error(Response.Status.CONFLICT, "CONSENT_REQUIRED")
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val current = QuizSessionStore.loadProviderMeta()
        val base = QuizProviderMeta.normalizeBaseUrl(
            body.optString("baseUrl", current.baseUrl),
        ) ?: return error(Response.Status.BAD_REQUEST, "PROVIDER_CONFIG")
        val model = QuizProviderMeta.normalizeModel(
            body.optString("model", current.model),
        ) ?: return error(Response.Status.BAD_REQUEST, "PROVIDER_CONFIG")
        val supplied = body.optString("apiKey", "").trim()
        val key = if (supplied.isEmpty()) QuizSessionStore.providerKey() else normalizeKey(supplied)
        if (key == null) {
            return error(
                Response.Status.BAD_REQUEST,
                if (supplied.isEmpty()) "PROVIDER_KEY_REQUIRED" else "PROVIDER_KEY_INVALID",
            )
        }
        val failure = QuizAiClient.testConnection(QuizProviderMeta(base, model), key)
        if (failure != null) return error(Response.Status.BAD_REQUEST, failure)
        return json(
            Response.Status.OK,
            JSONObject().put("ok", true).put("baseUrl", base).put("model", model),
        )
    }

    private fun readGenerationPrefs(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val context = subjectContext(session) ?: return invalidSubject()
        val prefs = if (context.editingUserId == null) {
            QuizSessionStore.loadGenerationPrefs()
        } else {
            QuizSessionStore.loadGenerationPrefsFor(context.editingUserId)
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", context.editingUserId ?: JSONObject.NULL)
                .put("prefs", prefs ?: JSONObject.NULL),
        )
    }

    private fun saveGenerationPrefs(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val prefs = QuizGeneration.parsePreferences(body)
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        val gradeNo = QuizCurriculum.current()?.gradeNoForCode(prefs.optString("grade", ""))
        if (isCloudJuniorBlocked(gradeNo)) {
            return error(Response.Status.CONFLICT, "CLOUD_JUNIOR_UNSUPPORTED")
        }
        val saved = if (context.editingUserId == null) {
            QuizSessionStore.saveGenerationPrefs(prefs)
        } else {
            QuizSessionStore.saveGenerationPrefsFor(context.editingUserId, prefs)
        }
        if (!saved) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        // Levels are a frozen parent-designed list now; changing the generation
        // settings no longer moves the answer scope.
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", context.editingUserId ?: JSONObject.NULL),
        )
    }

    private fun startGeneration(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        if (!QuizCrypto.isEncrypted) return error(Response.Status.CONFLICT, "AI_UNSUPPORTED")
        if (!QuizSessionStore.consentAccepted()) return error(Response.Status.CONFLICT, "CONSENT_REQUIRED")
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val request = when (val parsed = QuizGeneration.parseRequest(body)) {
            is QuizGeneration.ParseResult.Ok -> parsed.request
            is QuizGeneration.ParseResult.Invalid -> return invalidRequest(parsed.fields)
        }
        if (isCloudJuniorBlocked(request.gradeNo)) {
            return generationFailure("CLOUD_JUNIOR_UNSUPPORTED")
        }
        return when (
            val started = QuizGeneration.start(
                adminToken(session),
                request,
                context.editingUserId,
            )
        ) {
            is QuizGeneration.StartResult.Started -> json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put("subjectUserId", context.editingUserId ?: JSONObject.NULL)
                    .put("jobId", started.jobId)
                    .put("batchId", started.jobId)
                    .put("target", request.targetCount)
                    .put("expiresAt", started.expiresAt),
            )
            is QuizGeneration.StartResult.Failure -> generationFailure(started.code)
        }
    }

    private fun generationStatus(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val context = subjectContext(session) ?: return invalidSubject()
        val jobId = session.parameters["jobId"]?.firstOrNull().orEmpty()
        if (!JOB_ID_RE.matches(jobId)) return error(Response.Status.BAD_REQUEST, "INVALID_JOB")
        return when (
            val result = QuizGeneration.poll(
                adminToken(session),
                jobId,
                context.editingUserId,
            )
        ) {
            is QuizGeneration.PollResult.Running -> json(
                Response.Status.OK,
                generationJson("running", result.progress, jobId, context.editingUserId),
            )
            is QuizGeneration.PollResult.Ready -> json(
                Response.Status.OK,
                generationJson("ready", result.progress, jobId, context.editingUserId)
                    .put("batch", stagedBatchJson(result.batch)),
            )
            is QuizGeneration.PollResult.Failed -> json(
                Response.Status.OK,
                generationJson("failed", result.progress, jobId, context.editingUserId)
                    .put("error", result.errorCode)
                    .put("batch", stagedBatchJson(result.batch)),
            )
            QuizGeneration.PollResult.Lost -> error(Response.Status.NOT_FOUND, "JOB_LOST")
            QuizGeneration.PollResult.Expired -> error(Response.Status.CONFLICT, "JOB_EXPIRED")
        }
    }

    private fun cancelGeneration(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val jobId = body.optString("jobId", "")
        if (!JOB_ID_RE.matches(jobId)) return error(Response.Status.BAD_REQUEST, "INVALID_JOB")
        if (!QuizGeneration.cancel(adminToken(session), jobId, context.editingUserId)) {
            return error(Response.Status.NOT_FOUND, "JOB_LOST")
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", context.editingUserId ?: JSONObject.NULL),
        )
    }

    /** Commits only the rows the parent selected on the review page. */
    private fun addGeneration(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val jobId = body.optString("jobId", body.optString("batchId", ""))
        if (!JOB_ID_RE.matches(jobId)) return error(Response.Status.BAD_REQUEST, "INVALID_JOB")
        val array = body.optJSONArray("indexes")
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_INDEX")
        if (array.length() !in 1..QuizQuestionCache.BANK_CAPACITY) {
            return error(Response.Status.BAD_REQUEST, "INVALID_INDEX")
        }
        val indexes = mutableListOf<Int>()
        for (i in 0 until array.length()) {
            val raw = array.opt(i)
            if (raw !is Number) return error(Response.Status.BAD_REQUEST, "INVALID_INDEX")
            val index = raw.toInt()
            if (raw.toDouble() != index.toDouble() || index < 0) {
                return error(Response.Status.BAD_REQUEST, "INVALID_INDEX")
            }
            indexes += index
        }
        return when (
            val result = QuizGeneration.add(
                adminToken(session),
                jobId,
                indexes,
                context.editingUserId,
            )
        ) {
            is QuizGeneration.CommitResult.Ok -> {
                // A sibling's generation only commits rows for that member; the
                // TV runtime still runs the active child, so refresh it only
                // when the edited member is the one on screen.
                val state = if (context.editingIsActive) QuizRuntime.refresh() else null
                val counts = QuizQuestionCache.bankCounts(
                    QuizQuestionCache.LOCAL_SCOPE,
                    subjectUserId = context.editingUserId,
                    activeSubjectUserId = context.activeUserId,
                )
                val packages = levelPackagesFor(context.editingUserId)
                json(
                    Response.Status.OK,
                    JSONObject()
                        .put("ok", true)
                        .put("subjectUserId", context.editingUserId ?: JSONObject.NULL)
                        .put("appended", result.appended)
                        .put("duplicates", result.duplicates)
                        .put("rejected", result.rejected)
                        .put("capacityReached", result.capacityReached)
                        .put("counts", bankCountsJson(counts))
                        .put(
                            "bankState",
                            state?.bankState ?: editingBankState(
                                packages = packages,
                                documentUnreadable = false,
                                packagesUnreadable = false,
                                subjectUserId = context.editingUserId,
                                activeSubjectUserId = context.activeUserId,
                                nowMs = System.currentTimeMillis(),
                            ),
                        ),
                )
            }
            QuizGeneration.CommitResult.Lost -> error(Response.Status.NOT_FOUND, "JOB_LOST")
            QuizGeneration.CommitResult.NotReady -> error(Response.Status.CONFLICT, "JOB_RUNNING")
            is QuizGeneration.CommitResult.BadIndexes ->
                error(Response.Status.BAD_REQUEST, "INVALID_INDEX")
        }
    }

    private fun discardGeneration(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val context = subjectContext(session, body) ?: return invalidSubject()
        val jobId = body.optString("jobId", body.optString("batchId", ""))
        if (!JOB_ID_RE.matches(jobId)) return error(Response.Status.BAD_REQUEST, "INVALID_JOB")
        if (!QuizGeneration.discard(adminToken(session), jobId, context.editingUserId)) {
            return error(Response.Status.NOT_FOUND, "JOB_LOST")
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("subjectUserId", context.editingUserId ?: JSONObject.NULL),
        )
    }

    private fun normalizeKey(value: String): String? {
        val key = value.trim()
        // Providers use different key shapes (sk-..., id.secret, APIKey:APISecret, JWT),
        // so do not impose a format. Only reject values that cannot be sent safely in an
        // HTTP Authorization header: empty, oversized, or containing control characters.
        if (key.isEmpty() || key.length > MAX_PROVIDER_KEY_LENGTH) return null
        if (key.any { it.isISOControl() }) return null
        return key
    }

    /**
     * Strict boolean parsing for the speech settings. A malformed field must not
     * silently become a default-on value that starts uploading quiz text.
     */
    private fun parseSpeechSettings(raw: JSONObject, current: QuizSpeechSettings): QuizSpeechSettings? {
        fun value(key: String, fallback: Boolean): Boolean? = when {
            !raw.has(key) || raw.isNull(key) -> fallback
            raw.opt(key) is Boolean -> raw.getBoolean(key)
            else -> null
        }
        val enabled = value("enabled", current.enabled) ?: return null
        val prompt = value("readPrompt", current.readPrompt) ?: return null
        val options = value("readOptions", current.readOptions) ?: return null
        val answer = value("readAnswer", current.readAnswer) ?: return null
        return QuizSpeechSettings(
            initialized = true,
            enabled = enabled,
            readPrompt = prompt,
            readOptions = options,
            readAnswer = answer,
        )
    }

    private fun providerJson(): JSONObject {
        val meta = QuizSessionStore.loadProviderMeta()
        return JSONObject()
            .put("ok", true)
            .put("aiSupported", QuizCrypto.isEncrypted)
            .put("consentAccepted", QuizSessionStore.consentAccepted())
            .put("configured", QuizSessionStore.providerConfigured())
            .put("baseUrl", meta.baseUrl)
            .put("model", meta.model)
            .put("speech", QuizSessionStore.loadSpeechSettings().toJson())
    }

    private fun generationJson(
        state: String,
        progress: QuizGeneration.Progress,
        jobId: String,
        subjectUserId: String?,
    ): JSONObject =
        JSONObject()
            .put("ok", true)
            .put("state", state)
            .put("subjectUserId", subjectUserId ?: JSONObject.NULL)
            .put("jobId", jobId)
            .put("batchId", jobId)
            .put("target", progress.target)
            .put("generated", progress.generated)
            .put("unique", progress.unique)
            .put("duplicates", progress.duplicates)
            .put("rejected", progress.rejected)
            .put("remaining", progress.remaining)
            .put("batchIndex", progress.batchIndex)
            .put("warnings", JSONArray(progress.warnings))
            .put("counts", bankCountsJson(progress.counts))
            .put("expiresAt", progress.expiresAt)

    private fun stagedBatchJson(batch: List<QuizGeneration.StagedItem>): JSONArray =
        JSONArray().apply {
            batch.forEachIndexed { index, staged ->
                val question = staged.item
                put(
                    JSONObject()
                        .put("index", index)
                        .put("duplicate", staged.duplicate)
                        .put("questionId", question.questionId)
                        .put("prompt", question.prompt)
                        .put("options", JSONArray(question.options))
                        .put("type", question.answerKind.wire)
                        .put("answerIndex", question.answerIndex)
                        .put("answerText", question.options.getOrNull(question.answerIndex).orEmpty())
                        .put("answerIndices", JSONArray(question.correctIndices))
                        .put(
                            "answerTexts",
                            JSONArray(
                                question.correctIndices.mapNotNull { question.options.getOrNull(it) },
                            ),
                        )
                        .put("explanation", question.explanation)
                        .put("subject", question.subject)
                        .put("grade", question.grade)
                        .put("difficulty", question.difficulty)
                        .put("scopeGrade", question.scopeGrade ?: JSONObject.NULL)
                        .put("scopeSemester", question.scopeSemester ?: JSONObject.NULL)
                        .put("scopeSubject", question.scopeSubject)
                        .put("scopeTextbook", question.scopeTextbook)
                        .put("scopeType", question.scopeType)
                        .put("scopeUnitNo", question.scopeUnitNo ?: JSONObject.NULL)
                        .put("points", question.points)
                        .put("timeLimitSec", question.timeLimitSec),
                )
            }
        }

    /**
     * V41 separates the member the phone edits ([editingUserId]) from the
     * member the TV runs ([activeUserId]). Every user-level endpoint accepts
     * `subjectUserId` in the query (GET) or JSON body (mutations); an unknown or
     * disabled member is a hard 400 instead of silently falling back to the
     * active child.
     */
    private data class SubjectContext(
        val editingUserId: String?,
        val activeUserId: String?,
    ) {
        val editingIsActive: Boolean
            get() = editingUserId == null ||
                activeUserId == null ||
                editingUserId == activeUserId
    }

    private fun subjectContext(
        session: IHTTPSession,
        body: JSONObject? = null,
    ): SubjectContext? {
        val requested = body?.optString("subjectUserId", "")
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: session.parameters["subjectUserId"]?.firstOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() }
        val family = FamilyHub.state.value
        if (!family.ready) return if (requested == null) SubjectContext(null, null) else null
        val active = family.activeUserId.takeIf { it.isNotBlank() }
        val target = requested ?: active
        val valid = target == null || family.members.any {
            it.userId == target && it.status == MemberStatus.ACTIVE
        }
        return if (valid) SubjectContext(target, active) else null
    }

    private fun invalidSubject(): Response =
        error(Response.Status.BAD_REQUEST, "INVALID_SUBJECT")

    /**
     * Uploaded avatar for the quiz page's member header/picker.
     *
     * The parent center serves `/api/parent/avatar` behind its editor lease; the quiz page
     * has a quiz-admin session instead. Only ACTIVE members are served, matching the roster
     * the page receives. Bytes are the same normalized JPEG stored by FamilyAvatarStore.
     */
    private fun memberAvatar(context: Context, session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val userId = session.parameters["userId"]?.firstOrNull().orEmpty()
        val member = FamilyHub.state.value.members.firstOrNull {
            it.userId == userId && it.status == MemberStatus.ACTIVE
        } ?: return error(Response.Status.NOT_FOUND, "SUBJECT_NOT_FOUND")
        val bytes = FamilyAvatarStore.read(context, member)
            ?: return error(Response.Status.NOT_FOUND, "SUBJECT_AVATAR_NOT_FOUND")
        return NanoHTTPD.newFixedLengthResponse(
            Response.Status.OK,
            "image/jpeg",
            java.io.ByteArrayInputStream(bytes),
            bytes.size.toLong(),
        ).apply {
            addHeader("Cache-Control", "no-store")
        }
    }

    private fun activeMembersJson(): JSONArray = JSONArray().apply {
        val family = FamilyHub.state.value
        if (!family.ready) return@apply
        family.activeMembers.forEach { put(memberJson(it, family)) }
    }

    private fun memberJson(member: FamilyMember, family: FamilyHub.UiState): JSONObject {
        val avatarUrl = member.avatarImage?.let { path ->
            "/api/parent/avatar?userId=" + member.userId +
                "&rev=" + member.avatarRevision +
                "&name=" + path.substringAfterLast('/')
        }
        return JSONObject()
            .put("userId", member.userId)
            .put("name", member.displayName)
            .put("avatarPreset", member.avatarPreset)
            .put("avatarRevision", member.avatarRevision)
            .put("avatarUrl", avatarUrl ?: JSONObject.NULL)
            // Optional study scope. Null means every grade / every semester; the quiz page
            // uses this as the default and hides the values that cannot apply.
            .put("grade", member.grade ?: JSONObject.NULL)
            .put("semester", member.semester ?: JSONObject.NULL)
            .put("isDefault", member.userId == family.defaultUserId)
            .put("isActive", member.userId == family.activeUserId)
    }

    private fun collectionsJson(
        subjectUserId: String?,
        activeSubjectUserId: String?,
        packages: List<QuizLevelPackage>,
    ): JSONArray {
        val counts = QuizQuestionCache.collectionCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            subjectUserId = subjectUserId,
            activeSubjectUserId = activeSubjectUserId,
        ).associateBy { it.collectionId }
        val usedTags = packages.asSequence()
            .flatMap { it.sources.asSequence() }
            .filterIsInstance<QuizPackageSource.Collection>()
            .mapTo(HashSet()) { it.collectionId }
        return JSONArray().apply {
            QuizCollectionStore.collections(subjectUserId).forEach { collection ->
                val tagCounts = counts[collection.id]?.counts ?: QuizQuestionCache.BankCounts()
                put(
                    JSONObject()
                        .put("id", collection.id)
                        .put("name", collection.name)
                        .put("total", tagCounts.total)
                        .put("unused", tagCounts.unused)
                        .put("inUse", collection.id in usedTags),
                )
            }
        }
    }

    private fun editingBankState(
        packages: List<QuizLevelPackage>,
        documentUnreadable: Boolean,
        packagesUnreadable: Boolean,
        subjectUserId: String?,
        activeSubjectUserId: String?,
        nowMs: Long,
    ): String {
        if (documentUnreadable || packagesUnreadable) return "blocked"
        val enabled = packages.filter { it.enabled }
        if (packages.isNotEmpty() && enabled.isEmpty()) return "paused"
        val playable = enabled.maxOfOrNull {
            QuizQuestionCache.packageAvailability(
                it,
                nowMs,
                subjectUserId = subjectUserId,
                activeSubjectUserId = activeSubjectUserId,
            ).available
        } ?: 0
        return if (playable > 0) "ready" else "empty"
    }

    /**
     * A sibling's QCD. V41 members created before this change may still lack
     * one; the first edit provisions a clean document in that member's own
     * namespace instead of cloning the active child's legacy state.
     */
    private fun documentForEditing(userId: String): QuizDocLoadResult {
        val loaded = QuizConfigDocumentStore.localDocumentFor(userId)
        if (loaded is QuizDocLoadResult.Unreadable &&
            loaded.errors.singleOrNull() == "quiz.doc.local:ABSENT"
        ) {
            if (QuizConfigDocumentStore.provisionLocalDocument(userId, null)) {
                return QuizConfigDocumentStore.localDocumentFor(userId)
            }
        }
        return loaded
    }

    /** Packages of the member being edited, for endpoints that do not load a session. */
    private fun levelPackagesFor(userId: String?): List<QuizLevelPackage> {
        val document = if (userId.isNullOrBlank()) {
            QuizConfigDocumentStore.localDocument()
        } else {
            (QuizConfigDocumentStore.localDocumentFor(userId) as? QuizDocLoadResult.Ok)
                ?.document
        } ?: return emptyList()
        return storedPackages(document, userId)
    }

    private fun bankCountsJson(counts: QuizQuestionCache.BankCounts): JSONObject =
        JSONObject()
            .put("total", counts.total)
            .put("unused", counts.unused)
            .put("inRound", counts.inRound)
            .put("answered", counts.answered)
            .put("correct", counts.correct)
            .put("wrong", counts.wrong)
            .put("legacy", counts.legacy)
            .put("expired", counts.expired)

    /**
     * The editable level packages plus per-source stock. The phone list warns
     * before a child picks a package the bank cannot fill, and the source
     * picker shows the single/multiple split before a parent adds a course.
     */
    private fun levelPackagesJson(
        packages: List<QuizLevelPackage>,
        subjectUserId: String?,
        activeSubjectUserId: String?,
    ): JSONArray {
        val now = System.currentTimeMillis()
        return JSONArray().apply {
            packages.forEach { levelPackage ->
                val availability = QuizQuestionCache.packageAvailability(
                    levelPackage,
                    now,
                    subjectUserId = subjectUserId,
                    activeSubjectUserId = activeSubjectUserId,
                )
                put(
                    JSONObject()
                        .put("id", levelPackage.id)
                        .put("name", levelPackage.name)
                        .put("enabled", levelPackage.enabled)
                        .put("available", availability.available)
                        .put(
                            "sources",
                            JSONArray().apply {
                                levelPackage.sources.forEachIndexed { index, source ->
                                    put(
                                        packageSourceJson(
                                            source,
                                            availability.sourceCounts.getOrElse(index) { 0 },
                                            subjectUserId,
                                            activeSubjectUserId,
                                        ),
                                    )
                                }
                            },
                        ),
                )
            }
        }
    }

    private fun packageSourceJson(
        source: QuizPackageSource,
        available: Int,
        subjectUserId: String?,
        activeSubjectUserId: String?,
    ): JSONObject =
        when (source) {
            is QuizPackageSource.Course -> {
                val typeCounts = QuizQuestionCache.availableTypeCounts(
                    answerScope = source.scope,
                    subjectUserId = subjectUserId,
                    activeSubjectUserId = activeSubjectUserId,
                    origin = source.origin,
                )
                JSONObject()
                    .put("type", "course")
                    .put("scope", source.scope.encode())
                    .put("answerKind", source.answerKind.wire)
                    .put("origin", source.origin.wire)
                    .put("available", available)
                    .put(
                        "typeCounts",
                        JSONObject()
                            .put("total", typeCounts.total)
                            .put("single", typeCounts.single)
                            .put("multiple", typeCounts.multiple),
                    )
            }
            is QuizPackageSource.Collection -> JSONObject()
                .put("type", "collection")
                .put("collectionId", source.collectionId)
                .put(
                    "collectionName",
                    QuizCollectionStore.tagName(source.collectionId, subjectUserId),
                )
                .put("available", available)
            is QuizPackageSource.Preset -> {
                val counts = QuizQuestionCache.bankCounts(
                    QuizQuestionCache.LOCAL_SCOPE,
                    subjectUserId = subjectUserId,
                    activeSubjectUserId = activeSubjectUserId,
                    presetBank = source.bank,
                )
                JSONObject()
                    .put("type", "preset")
                    .put("bankId", source.bank.wire)
                    .put("available", available)
                    .put("total", counts.total)
            }
            QuizPackageSource.WrongReview -> JSONObject()
                .put("type", "wrong")
                .put("available", available)
        }

    private sealed interface LevelPackagesResult {
        data class Ok(val packages: List<QuizLevelPackage>) : LevelPackagesResult
        data class Invalid(val code: String, val detail: String? = null) : LevelPackagesResult
    }

    /**
     * Strict package parse with the plan's differentiated error codes. A
     * missing id is assigned by [QuizLevelPackage.parse]; any other malformed
     * row rejects the whole save instead of dropping part of a parent's list.
     */
    private fun parseLevelPackages(
        raw: JSONArray?,
        subjectUserId: String?,
    ): LevelPackagesResult {
        if (raw == null) return LevelPackagesResult.Invalid("INVALID_LEVEL_PACKAGES")
        if (raw.length() > QuizLevelPackage.MAX_LEVELS) {
            return LevelPackagesResult.Invalid("TOO_MANY_LEVELS")
        }
        val knownTags = QuizCollectionStore.collections(subjectUserId).mapTo(HashSet()) { it.id }
        val packages = ArrayList<QuizLevelPackage>(raw.length())
        val ids = HashSet<String>(raw.length())
        for (index in 0 until raw.length()) {
            val obj = raw.optJSONObject(index)
                ?: return LevelPackagesResult.Invalid("INVALID_LEVEL_PACKAGES", "index:$index")
            val sourceArray = obj.optJSONArray("sources")
                ?: return LevelPackagesResult.Invalid("INVALID_LEVEL_PACKAGES", "sources:$index")
            if (sourceArray.length() == 0) {
                return LevelPackagesResult.Invalid("INVALID_LEVEL_SOURCE", "EMPTY")
            }
            if (sourceArray.length() > QuizLevelPackage.MAX_SOURCES) {
                return LevelPackagesResult.Invalid("LEVEL_SOURCE_LIMIT")
            }
            val sources = ArrayList<QuizPackageSource>(sourceArray.length())
            for (sourceIndex in 0 until sourceArray.length()) {
                val source = QuizLevelPackage.parseSource(sourceArray.optJSONObject(sourceIndex))
                    ?: return LevelPackagesResult.Invalid("INVALID_LEVEL_SOURCE", "MALFORMED")
                if (source is QuizPackageSource.Collection && source.collectionId !in knownTags) {
                    return LevelPackagesResult.Invalid(
                        "INVALID_LEVEL_SOURCE",
                        "UNKNOWN_COLLECTION",
                    )
                }
                sources += source
            }
            when (val issue = QuizLevelPackage.sourceIssue(sources)) {
                null -> Unit
                SourceIssue.TOO_MANY ->
                    return LevelPackagesResult.Invalid("LEVEL_SOURCE_LIMIT")
                SourceIssue.EMPTY ->
                    return LevelPackagesResult.Invalid("INVALID_LEVEL_SOURCE", "EMPTY")
                else ->
                    return LevelPackagesResult.Invalid("INVALID_LEVEL_SOURCE", issue.name)
            }
            val levelPackage = QuizLevelPackage.parse(obj)
                ?: return LevelPackagesResult.Invalid("INVALID_LEVEL_PACKAGES", "level:$index")
            if (!ids.add(levelPackage.id)) {
                return LevelPackagesResult.Invalid("INVALID_LEVEL_PACKAGES", "DUPLICATE_ID")
            }
            packages += levelPackage
        }
        return LevelPackagesResult.Ok(packages)
    }

    /**
     * Packages currently owned by [document]. An absent extension is migrated
     * in memory for comparisons; this never writes, so a rules-only save stays
     * a single revision.
     */
    private fun storedPackages(
        document: QuizConfigDocument,
        subjectUserId: String?,
    ): List<QuizLevelPackage> =
        when (val extension = QuizLevelPackageStore.read(document)) {
            is QuizLevelPackageStore.Extension.Present -> extension.packages
            QuizLevelPackageStore.Extension.Absent -> QuizLevelPackageStore.migrate(
                courseLevels = document.localLevels().orEmpty(),
                customLevels = QuizCollectionStore.customLevels(subjectUserId),
                wrongLevel = wrongLevelFor(subjectUserId),
            )
            is QuizLevelPackageStore.Extension.Broken -> emptyList()
        }

    private fun wrongLevelFor(subjectUserId: String?)
        : com.planyourtv.policy.quiz.QuizWrongReviewLevel? =
        if (subjectUserId.isNullOrBlank()) {
            QuizWrongReviewLevelStore.level()
        } else {
            QuizWrongReviewLevelStore.levelFor(subjectUserId)
        }

    private data class LegacyLevelProjection(
        val answerScope: QuizAnswerScope?,
        val answerScopes: JSONArray,
        val customLevels: JSONArray,
        val wrongLevel: JSONObject?,
    )

    /**
     * Projects packages into the three old read-only shapes. A mixed package
     * has no exact legacy equivalent, so it only contributes its first course
     * scope to the single-scope fields. Saving through those old shapes is
     * rejected; this keeps an old page readable without letting it edit.
     */
    private fun legacyLevelProjection(
        packages: List<QuizLevelPackage>,
        subjectUserId: String?,
        activeSubjectUserId: String?,
    ): LegacyLevelProjection {
        val answerScopes = JSONArray()
        val customLevels = JSONArray()
        var wrongLevel: JSONObject? = null
        var answerScope: QuizAnswerScope? = null
        packages.forEach { levelPackage ->
            if (answerScope == null) {
                answerScope = firstCourseIn(levelPackage.sources)
            }
            when (val only = levelPackage.sources.singleOrNull()) {
                is QuizPackageSource.Course -> answerScopes.put(
                    JSONObject()
                        .put("id", levelPackage.id)
                        .put("name", levelPackage.name)
                        .put("scope", only.scope.encode())
                        .put(
                            "available",
                            QuizQuestionCache.availableCount(
                                QuizQuestionCache.LOCAL_SCOPE,
                                answerScope = only.scope,
                                answerKind = only.answerKind,
                                subjectUserId = subjectUserId,
                                activeSubjectUserId = activeSubjectUserId,
                            ),
                        ),
                )
                is QuizPackageSource.Collection -> customLevels.put(
                    JSONObject()
                        .put("id", levelPackage.id)
                        .put("name", levelPackage.name)
                        .put("collectionId", only.collectionId)
                        .put(
                            "collectionName",
                            QuizCollectionStore.tagName(only.collectionId, subjectUserId),
                        )
                        .put(
                            "available",
                            QuizQuestionCache.availableCollectionCount(
                                only.collectionId,
                                subjectUserId = subjectUserId,
                                activeSubjectUserId = activeSubjectUserId,
                            ),
                        ),
                )
                QuizPackageSource.WrongReview -> if (wrongLevel == null) {
                    wrongLevel = JSONObject()
                        .put("id", levelPackage.id)
                        .put("name", levelPackage.name)
                        .put(
                            "available",
                            QuizQuestionCache.wrongReviewCount(
                                subjectUserId = subjectUserId,
                                activeSubjectUserId = activeSubjectUserId,
                            ),
                        )
                }
                // The pre-V37 page has no concept of a bundled non-curriculum
                // bank, so it only sees the level name.
                is QuizPackageSource.Preset -> Unit
                null -> Unit
            }
        }
        return LegacyLevelProjection(answerScope, answerScopes, customLevels, wrongLevel)
    }

    private fun firstCourseIn(sources: List<QuizPackageSource>): QuizAnswerScope? =
        sources.firstNotNullOfOrNull { (it as? QuizPackageSource.Course)?.scope }

    private fun firstCourseScope(packages: List<QuizLevelPackage>): QuizAnswerScope? =
        packages.firstNotNullOfOrNull { firstCourseIn(it.sources) }

    /** The pre-V17 `cache` shape, kept so a cached page still renders counts. */
    private fun legacyCacheJson(counts: QuizQuestionCache.BankCounts): JSONObject =
        JSONObject()
            .put("available", counts.unused)
            .put("reserved", counts.inRound)
            .put("consumed", counts.answered)
            .put("expired", counts.expired)

    private fun isCloudJuniorBlocked(gradeNo: Int?): Boolean =
        gradeNo != null && !QuizCurriculum.CLOUD_JUNIOR_READY &&
            QuizCurriculum.current()?.stageFor(gradeNo)?.id == "junior" &&
            QuizConfigSync.isCloudManaged()

    private fun generationFailure(code: String): Response = when (code) {
        "CONSENT_REQUIRED", "PROVIDER_NOT_CONFIGURED", "BUSY", "BANK_FULL",
        "CACHE_UNAVAILABLE", "CLOUD_JUNIOR_UNSUPPORTED" ->
            error(Response.Status.CONFLICT, code)
        else -> error(Response.Status.BAD_REQUEST, code)
    }

    private fun invalidRequest(fields: List<String>): Response = json(
        Response.Status.BAD_REQUEST,
        JSONObject()
            .put("ok", false)
            .put("error", "INVALID_REQUEST")
            .put("fields", JSONArray(fields.take(8).map { it.take(80) })),
    )

    private fun adminError(session: IHTTPSession): Response? {
        if (session.headers["x-quiz-admin"] != "1") return error(Response.Status.FORBIDDEN, "ADMIN_REQUIRED")
        if (session.headers["content-type"]?.startsWith("application/json") != true &&
            session.method != Method.GET
        ) {
            return error(Response.Status.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE")
        }
        val token = adminToken(session)
        if (!QuizAccess.adminValid(token)) return error(Response.Status.FORBIDDEN, "ADMIN_EXPIRED")
        return null
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

    private fun jsonRequest(session: IHTTPSession): Boolean =
        session.headers["content-type"]?.startsWith("application/json") == true

    @Suppress("UNUSED_PARAMETER")
    private fun jsonBody(session: IHTTPSession): JSONObject? = requestBody.get()

    private fun readJsonBody(session: IHTTPSession, limit: Int = MAX_JSON_BYTES): JSONObject? {
        // NanoHTTPD 2.3.1 only exposes parseBody() results for POST, so a PUT
        // body would disappear. Read exactly Content-Length bytes for every
        // method instead; a missing length is rejected rather than blocking on
        // a keep-alive stream.
        val declared = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (declared <= 0 || declared > limit) return null
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

    private fun adminToken(session: IHTTPSession): String =
        session.headers["x-quiz-admin-token"].orEmpty()

    private fun summary(session: QuizSession): JSONObject = JSONObject()
        .put("id", session.id)
        .put("status", session.status.name)
        .put("questionCount", session.items.size)
        .put("expiresAt", session.expiresAt)

    private fun invalidSession(errors: List<String>): Response {
        val clean = errors.take(8).map { it.take(120) }
        return json(
            Response.Status.BAD_REQUEST,
            JSONObject()
                .put("ok", false)
                .put("error", "INVALID_SESSION")
                .put("fields", org.json.JSONArray(clean)),
        )
    }

    private fun asset(context: Context, name: String, type: String): Response {
        val body = context.assets.open(name).bufferedReader().use { it.readText() }
        return newResponse(Response.Status.OK, type, body)
    }

    /** The Aerial page is served at the token root; `/quiz` is the only entry to redirect. */
    private fun redirectToAerialPage(session: IHTTPSession): Response {
        val root = aerialRootForQuizUri(session.uri)
        if (root == null || root.isEmpty() || !root.endsWith("/")) {
            return error(Response.Status.NOT_FOUND, "NOT_FOUND")
        }
        return newResponse(Response.Status.REDIRECT, "text/html; charset=utf-8", "").apply {
            addHeader("Location", root)
        }
    }

    /**
     * Only the HTML shell moves; assets, APIs and a local/ready gate keep their normal page.
     * Kept pure so the bound-mode condition is testable without a bound TV.
     */
    internal fun isBoundQuizShellRedirect(method: Method, path: String, gateReason: String): Boolean =
        method == Method.GET &&
            (path == "/quiz" || path == "/quiz/") &&
            gateReason == "REMOTE_BOUND"

    /**
     * Maps `/token/quiz` and `/token/quiz/` to `/token/`, leaving query/fragment behind.
     * Pure so the shell/asset boundary is testable without starting the HTTP server.
     */
    internal fun aerialRootForQuizUri(uri: String): String? {
        val path = uri.substringBefore('?').substringBefore('#')
        return when {
            path.endsWith("/quiz/") -> path.dropLast("/quiz/".length) + "/"
            path.endsWith("/quiz") -> path.dropLast("/quiz".length) + "/"
            else -> null
        }
    }

    private fun lockJson(status: PolicyModeCoordinator.WebUnlockStatus): JSONObject =
        JSONObject()
            .put("eligible", status.eligible)
            .put("reason", status.reason.name)
            .put("unlocked", status.unlocked)

    private fun manualStartJson(status: PolicyModeCoordinator.WebManualStartStatus): JSONObject =
        JSONObject()
            .put("eligible", status.eligible)
            .put("reason", status.reason.name)
            .put("minMinutes", ManualSessionLimits.MIN_MINUTES)
            .put("maxMinutes", ManualSessionLimits.MAX_MINUTES)
            .put("stepMinutes", ManualSessionLimits.STEP_MINUTES)
            .put("defaultMinutes", ManualSessionLimits.DEFAULT_MINUTES)
            .put("restRemainingSeconds", status.restRemainingSeconds)
            .put("remainingSeconds", status.remainingSeconds)
            .put("outsidePlan", status.outsidePlan)

    private fun json(status: Response.Status, body: JSONObject): Response =
        newResponse(status, "application/json; charset=utf-8", body.toString())

    private fun error(status: Response.Status, code: String, detail: String? = null): Response =
        json(
            status,
            JSONObject().put("ok", false).put("error", code).apply {
                if (detail != null) put("detail", detail)
            },
        )

    private fun newResponse(status: Response.Status, type: String, body: String): Response =
        NanoHTTPD.newFixedLengthResponse(status, type, body).apply {
            addHeader("Cache-Control", "no-store")
        }

    /** States where the session is owned by the TV and must not be edited remotely. */
    private val ACTIVE_STATUSES = setOf(
        QuizSessionStatus.RUNNING,
        QuizSessionStatus.SETTLING,
        QuizSessionStatus.STARTING,
        QuizSessionStatus.START_FAILED,
    )
    private val JOB_ID_RE = Regex("job_[0-9a-f]{20}")
    /** The pre-V37 level editors; any of these means the page must reload. */
    private val LEGACY_LEVEL_FIELDS = listOf(
        "answerScopes",
        "scopeMode",
        "scopeProvided",
        "scope",
        "customLevels",
        "wrongLevel",
    )
    private val BANK_FILTERS = setOf("all", "unused", "answered", "wrong", "correct")
    private val FACET_DIFFICULTIES = setOf("EASY", "MEDIUM", "HARD")
}
