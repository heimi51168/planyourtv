package app.orchard.tv.quiz

import android.content.Context
import app.orchard.tv.policy.PolicyModeCoordinator
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONObject
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizBankImport
import com.planyourtv.policy.quiz.QuizCollection
import com.planyourtv.policy.quiz.QuizCollectionLevel
import com.planyourtv.policy.quiz.QuizCurriculum
import com.planyourtv.policy.quiz.QuizDecodeResult
import com.planyourtv.policy.quiz.QuizImportError
import com.planyourtv.policy.quiz.QuizImportResult
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionCodec
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizSessionValidation
import com.planyourtv.policy.quiz.QuizUnitCatalog
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

    /**
     * NanoHTTPD keeps one [IHTTPSession] for the whole keep-alive connection,
     * so caching bodies by session would return the first request's body for
     * every later request on that connection. Scope the parsed body to the
     * current request instead: [api] runs synchronously on the connection
     * thread, so a ThreadLocal is request-local here.
     */
    private val requestBody = ThreadLocal<JSONObject?>()

    fun handle(context: Context, session: IHTTPSession, path: String): Response? {
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
            path.startsWith("/api/quiz/") -> api(session, path)
            else -> null
        }
    }

    private fun api(session: IHTTPSession, path: String): Response {
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
                session.method == Method.GET && path == "/api/quiz/admin/session" -> readSession(session)
                session.method == Method.GET && path == "/api/quiz/admin/units" -> readUnitCatalog(session)
                session.method == Method.PUT && path == "/api/quiz/admin/session" -> putSession(session)
                session.method == Method.DELETE && path == "/api/quiz/admin/session" -> deleteSession(session)
                session.method == Method.POST && path == "/api/quiz/admin/challenge-config" -> saveChallengeConfig(session)
                session.method == Method.GET && path == "/api/quiz/admin/bank" -> readBank(session)
                session.method == Method.GET && path == "/api/quiz/admin/bank/facets" -> readBankFacets(session)
                session.method == Method.POST && path == IMPORT_PATH -> importBank(session)
                session.method == Method.POST && path == "/api/quiz/admin/bank/clear" -> clearBank(session)
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
                session.method == Method.POST && path == "/api/quiz/admin/enable" -> setEnabled(session, true)
                session.method == Method.POST && path == "/api/quiz/admin/disable" -> setEnabled(session, false)
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
     * refreshes the owner's last-seen time, so the body is intentionally small.
     */
    private fun heartbeat(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        return json(Response.Status.OK, JSONObject().put("ok", true))
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
        // Entering the local settings page is a documented cloud->TV apply point.
        QuizConfigSync.refreshFromCloudAsync()
        val state = QuizRuntime.refresh()
        val current = state.session
        val challengeConfig = QuizSessionStore.loadChallengeConfig()
            ?: com.planyourtv.policy.quiz.QuizConfig()
        val body = JSONObject()
            .put("ok", true)
            .put("enabled", state.enabled)
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
        val answerLevels = QuizAnswerScopeStore.levels()
        val answerScope = answerLevels.firstOrNull()?.scope
        body.put("answerScope", answerScope?.encode() ?: JSONObject.NULL)
        // Kept for a page cached before the level list existed; it only knows
        // one scope and reads the first level.
        body.put("answerScopeMode", if (answerScope == null) "bound" else "custom")
        body.put("answerScopes", answerLevelsJson(answerLevels))
        val collections = QuizCollectionStore.collections()
        val countsByCollection = QuizQuestionCache.collectionCounts(QuizQuestionCache.LOCAL_SCOPE)
            .associateBy { it.collectionId }
        body.put(
            "customLevels",
            JSONArray().apply {
                QuizCollectionStore.customLevels().forEach { level ->
                    put(
                        JSONObject()
                            .put("id", level.id)
                            .put("name", level.name)
                            .put("collectionId", level.collectionId)
                            .put("collectionName", QuizCollectionStore.tagName(level.collectionId))
                            .put(
                                "available",
                                QuizQuestionCache.availableCollectionCount(level.collectionId),
                            ),
                    )
                }
            },
        )
        body.put(
            "collections",
            JSONArray().apply {
                collections.forEach { collection ->
                    val counts = countsByCollection[collection.id]?.counts
                    put(
                        JSONObject()
                            .put("id", collection.id)
                            .put("name", collection.name)
                            .put("total", counts?.total ?: 0)
                            .put(
                                "unused",
                                QuizQuestionCache.availableCollectionCount(collection.id),
                            ),
                    )
                }
            },
        )
        val sourceCounts = QuizQuestionCache.sourceCounts(QuizQuestionCache.LOCAL_SCOPE)
        body.put(
            "bankSourceCounts",
            JSONObject()
                .put("course", sourceCounts.course)
                .put("custom", sourceCounts.custom),
        )
        body.put(
            "answerScopeAvailable",
            QuizQuestionCache.availableCount(
                QuizQuestionCache.LOCAL_SCOPE,
                answerScope = answerScope,
            ),
        )
        body.put("engineConfig", QuizSessionStore.loadEngineConfig().let {
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
        body.put("bankState", state.bankState)
        body.put("bank", bankCountsJson(state.bankCounts))
        body.put("activeScopeAvailable", state.activeScopeAvailable)
        body.put("syncState", QuizSessionStore.loadSyncState().encode())
        body.put("cloudManaged", QuizConfigSync.isCloudManaged())
        body.put("cache", legacyCacheJson(state.bankCounts))
        body.put("bankCapacity", QuizQuestionCache.BANK_CAPACITY)
        return json(Response.Status.OK, body)
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
            QuizQuestionCache.appendLocalQuestions(parsed.id, parsed.items, now)
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
        val filter = session.parameters["filter"]?.firstOrNull().orEmpty()
            .takeIf { it in BANK_FILTERS } ?: "all"
        val offset = session.parameters["offset"]?.firstOrNull()?.toIntOrNull()
            ?.coerceIn(0, 100_000) ?: 0
        val limit = session.parameters["limit"]?.firstOrNull()?.toIntOrNull()
            ?.coerceIn(1, 100) ?: 50
        val source = when (session.parameters["source"]?.firstOrNull()?.trim()?.lowercase()) {
            null, "", "all" -> null
            "course", "course_ai", "ai" -> QuizQuestionCache.BankSource.COURSE_AI
            "custom" -> QuizQuestionCache.BankSource.CUSTOM
            else -> return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        }
        val collectionId = session.parameters["collectionId"]?.firstOrNull()
            ?.trim()?.takeIf { it.isNotEmpty() }
        val now = System.currentTimeMillis()
        val rows = QuizQuestionCache.listBank(
            QuizQuestionCache.LOCAL_SCOPE,
            filter,
            offset,
            limit,
            now,
            source = source,
            collectionId = collectionId,
        )
        val tagNames = QuizCollectionStore.collections().associate { it.id to it.name }
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
                    .put("prompt", question.prompt)
                    .put("options", JSONArray(question.options))
                    .put("answerIndex", question.answerIndex)
                    .put("answerText", question.options.getOrNull(question.answerIndex).orEmpty())
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
                    .put("timedOut", row.timedOut)
                    .put("answeredAt", row.answeredAt ?: 0L)
                    .put("receivedAt", row.receivedAt),
            )
        }
        val counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now)
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("filter", filter)
                .put("offset", offset)
                .put("limit", limit)
                .put("items", items)
                .put("counts", bankCountsJson(counts))
                .put(
                    "sourceCounts",
                    QuizQuestionCache.sourceCounts(QuizQuestionCache.LOCAL_SCOPE).let {
                        JSONObject().put("course", it.course).put("custom", it.custom)
                    },
                )
                .put("capacity", QuizQuestionCache.BANK_CAPACITY),
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
        val filter = FacetFilters(grade, semester, subject, textbook, topic, difficulty)
        val now = System.currentTimeMillis()
        val allRows = QuizQuestionCache.listAllBank(QuizQuestionCache.LOCAL_SCOPE, now)
        val scopedRows = allRows.mapNotNull { row -> facetRow(row.question) }
        val matched = scopedRows.count { it.matches(filter) }
        val withoutDifficulty = scopedRows.count { it.matches(filter, skip = "difficulty") }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("total", allRows.size)
                .put("scoped", scopedRows.size)
                .put("noScope", allRows.size - scopedRows.size)
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
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("grade", grade ?: JSONObject.NULL)
            .put("semester", semester ?: JSONObject.NULL)
            .put("subject", subject ?: JSONObject.NULL)
            .put("textbook", textbook ?: JSONObject.NULL)
            .put("topic", topic ?: JSONObject.NULL)
            .put("difficulty", difficulty ?: JSONObject.NULL)
    }

    private data class FacetRow(val item: QuizItem, val topic: String) {
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
            return true
        }
    }

    private fun facetRow(item: QuizItem): FacetRow? {
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
        return FacetRow(item, topic)
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
            .forEach { row -> key(row)?.let { counts[it] = (counts[it] ?: 0) + 1 } }
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
        if (QuizRuntime.refresh().session?.status in ACTIVE_STATUSES) {
            return error(Response.Status.CONFLICT, "SESSION_RUNNING")
        }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
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

        val existing = if (collectionId.isNotEmpty()) QuizCollectionStore.find(collectionId) else null
        if (collectionId.isNotEmpty() && existing == null) {
            return error(Response.Status.NOT_FOUND, "COLLECTION_NOT_FOUND")
        }
        var created: QuizCollection? = null
        val collection = existing ?: run {
            val cleanName = QuizCollection.normalizeName(newCollectionName)
                ?: return error(Response.Status.BAD_REQUEST, "INVALID_COLLECTION_NAME")
            if (QuizCollectionStore.nameExists(cleanName)) {
                return error(Response.Status.CONFLICT, "COLLECTION_EXISTS")
            }
            if (QuizCollectionStore.collections().size >= QuizCollection.MAX_COLLECTIONS) {
                return error(Response.Status.CONFLICT, "COLLECTION_LIMIT")
            }
            if (dryRun) {
                QuizCollection.create(cleanName)
                    ?: return error(Response.Status.BAD_REQUEST, "INVALID_COLLECTION_NAME")
            } else {
                QuizCollectionStore.addCollection(cleanName)?.also { created = it }
                    ?: return error(
                        Response.Status.CONFLICT,
                        if (QuizCollectionStore.nameExists(cleanName)) {
                            "COLLECTION_EXISTS"
                        } else {
                            "COLLECTION_LIMIT"
                        },
                    )
            }
        }

        if (dryRun) {
            val plan = QuizQuestionCache.previewLocalImport(collection.id, items)
            val canImport = plan.rejected == 0 &&
                plan.wouldAppend > 0 &&
                plan.wouldAppend <= plan.remaining
            return json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put("dryRun", true)
                    .put("collection", collectionJson(collection))
                    .put("total", plan.total)
                    .put("fileDuplicates", plan.fileDuplicates)
                    .put("collectionDuplicates", plan.collectionDuplicates)
                    .put("crossSourceDuplicates", plan.crossSourceDuplicates)
                    .put("rejected", plan.rejected)
                    .put("wouldAppend", plan.wouldAppend)
                    .put("capacity", QuizQuestionCache.BANK_CAPACITY)
                    .put("remaining", plan.remaining)
                    .put("canImport", canImport)
                    .put("preview", importPreviewJson(plan.preview)),
            )
        }

        val result = QuizQuestionCache.importLocalQuestions(
            collection.id,
            "import-" + UUID.randomUUID().toString().replace("-", "").take(16),
            items,
        )
        if (result.storageError || result.rejected > 0 || result.capacityReached) {
            created?.let { QuizCollectionStore.removeCollection(it.id) }
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
        val state = QuizRuntime.refresh()
        val collectionCounts = QuizQuestionCache.collectionCounts(QuizQuestionCache.LOCAL_SCOPE)
            .firstOrNull { it.collectionId == collection.id }?.counts
            ?: QuizQuestionCache.BankCounts()
        val sourceCounts = QuizQuestionCache.sourceCounts(QuizQuestionCache.LOCAL_SCOPE)
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("dryRun", false)
                .put("collection", collectionJson(collection))
                .put("appended", result.appended)
                .put("fileDuplicates", result.fileDuplicates)
                .put("collectionDuplicates", result.collectionDuplicates)
                .put("crossSourceDuplicates", result.crossSourceDuplicates)
                .put("rejected", result.rejected)
                .put("capacity", QuizQuestionCache.BANK_CAPACITY)
                .put("remaining", result.remaining)
                .put("counts", bankCountsJson(state.bankCounts))
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
                ),
        )
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

    private fun importPreviewJson(items: List<QuizItem>): JSONArray = JSONArray().apply {
        items.take(20).forEachIndexed { index, item ->
            put(
                JSONObject()
                    .put("index", index)
                    .put("prompt", item.prompt)
                    .put("options", JSONArray(item.options))
                    .put("answerIndex", item.answerIndex)
                    .put("explanation", item.explanation)
                    .put("difficulty", item.difficulty),
            )
        }
    }

    private fun clearBank(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        if (QuizRuntime.refresh().session?.status in ACTIVE_STATUSES) {
            return error(Response.Status.CONFLICT, "SESSION_RUNNING")
        }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val mode = when (body.optString("mode", "unused")) {
            "unused" -> QuizQuestionCache.ClearMode.UNUSED
            "answered" -> QuizQuestionCache.ClearMode.ANSWERED
            "correct" -> QuizQuestionCache.ClearMode.CORRECT
            "wrong" -> QuizQuestionCache.ClearMode.WRONG
            "all" -> QuizQuestionCache.ClearMode.ALL
            else -> return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        }
        // The default must stay the AI/seed namespace so a cached page or a
        // habitual cleanup can never delete imported tag rows.
        val source = when (body.optString("source", "course").trim().lowercase(Locale.ROOT)) {
            "course", "course_ai", "ai" -> QuizQuestionCache.BankSource.COURSE_AI
            "custom" -> QuizQuestionCache.BankSource.CUSTOM
            "all" -> QuizQuestionCache.BankSource.ALL
            else -> return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        }
        val collectionId = body.optString("collectionId", "").trim().takeIf { it.isNotEmpty() }
        if (!QuizQuestionCache.clearBank(
                QuizQuestionCache.LOCAL_SCOPE,
                mode,
                source = source,
                collectionId = collectionId,
            )
        ) {
            return error(Response.Status.INTERNAL_ERROR, "CACHE_UNAVAILABLE")
        }
        val state = QuizRuntime.refresh()
        val sourceCounts = QuizQuestionCache.sourceCounts(QuizQuestionCache.LOCAL_SCOPE)
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("counts", bankCountsJson(state.bankCounts))
                .put(
                    "sourceCounts",
                    JSONObject()
                        .put("course", sourceCounts.course)
                        .put("custom", sourceCounts.custom),
                )
                .put("bankState", state.bankState),
        )
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
        if (!QuizQuestionCache.clearBank(QuizQuestionCache.LOCAL_SCOPE, QuizQuestionCache.ClearMode.UNUSED)) {
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
        val engine = QuizSessionStore.loadEngineConfig()
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
        val configObject = body.optJSONObject("config")
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_CONFIG")
        val config = QuizSessionCodec.decodeConfig(configObject)
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_CONFIG")
        // Field ranges are enforced by decodeConfig. Cross-field combinations
        // (cap below one reward, threshold above cap) are intentional parent
        // choices and only produce soft warnings on the web page.
        // The new page sends the whole ordered list. A page cached before the
        // level list keeps sending one scope; it is mapped to a one-level list.
        // Rules-only saves leave the levels untouched.
        val levelsProvided = body.has("answerScopes")
        val levels: List<QuizLevel>? = if (levelsProvided) {
            if (body.isNull("answerScopes")) {
                emptyList()
            } else {
                QuizLevel.parseList(body.optJSONArray("answerScopes"))
                    ?: return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
            }
        } else {
            val scopeMode = body.optString("scopeMode", "").trim().lowercase()
            val scopeProvided = body.optBoolean("scopeProvided", false) || scopeMode.isNotEmpty()
            when {
                !scopeProvided -> null
                scopeMode == "bound" -> QuizAnswerScopeStore.generationScope()?.let {
                    listOf(QuizLevel(id = "legacy-1", name = "", scope = it))
                }.orEmpty()
                scopeMode == "custom" -> {
                    if (!body.has("scope") || body.isNull("scope")) {
                        return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
                    }
                    val scope = QuizAnswerScope.parse(body.optJSONObject("scope"))
                        ?: return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
                    listOf(QuizLevel(id = "legacy-1", name = "", scope = scope))
                }
                scopeMode.isEmpty() -> {
                    when {
                        !body.has("scope") ->
                            return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
                        body.isNull("scope") -> QuizAnswerScopeStore.generationScope()?.let {
                            listOf(QuizLevel(id = "legacy-1", name = "", scope = it))
                        }.orEmpty()
                        else -> {
                            val scope = QuizAnswerScope.parse(body.optJSONObject("scope"))
                                ?: return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
                            listOf(QuizLevel(id = "legacy-1", name = "", scope = scope))
                        }
                    }
                }
                else -> return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
            }
        }
        if (levels?.any { isCloudJuniorBlocked(it.scope.grade) } == true) {
            return error(Response.Status.CONFLICT, "CLOUD_JUNIOR_UNSUPPORTED")
        }
        // Custom tag levels are saved under their own key. Either list may be
        // omitted independently; an explicit null clears that section.
        val customLevels: List<QuizCollectionLevel>? = if (body.has("customLevels")) {
            if (body.isNull("customLevels")) {
                emptyList()
            } else {
                QuizCollectionLevel.parseList(body.optJSONArray("customLevels"))
                    ?: return error(Response.Status.BAD_REQUEST, "INVALID_CUSTOM_LEVEL")
            }
        } else {
            null
        }
        if (levels != null || customLevels != null) {
            val knownTags = QuizCollectionStore.collections().mapTo(HashSet()) { it.id }
            if (customLevels?.any { it.collectionId !in knownTags } == true) {
                return error(Response.Status.BAD_REQUEST, "INVALID_CUSTOM_LEVEL")
            }
            val courseCount = levels?.size ?: QuizAnswerScopeStore.levels().size
            val customCount = customLevels?.size ?: QuizCollectionStore.customLevels().size
            if (courseCount + customCount > QuizLevel.MAX_LEVELS) {
                return error(Response.Status.BAD_REQUEST, "TOO_MANY_LEVELS")
            }
        }
        val previousScopeSignature = if (levels != null) {
            QuizAnswerScopeStore.currentSignature()
        } else {
            null
        }
        if (!QuizSessionStore.saveChallengeConfig(config)) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        if (levels != null && !QuizAnswerScopeStore.save(levels)) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        if (customLevels != null && !QuizCollectionStore.saveCustomLevels(customLevels)) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        QuizConfigSync.afterLocalEdit()
        val effectiveLevels = QuizAnswerScopeStore.levels()
        val effectiveScope = effectiveLevels.firstOrNull()?.scope
        if (previousScopeSignature != null &&
            previousScopeSignature != QuizAnswerScopeStore.currentSignature()
        ) {
            QuizConfigSync.afterLocalScopeChange()
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("config", QuizSessionCodec.encodeConfig(config))
                .put("answerScope", effectiveScope?.encode() ?: JSONObject.NULL)
                .put("answerScopeMode", if (effectiveScope == null) "bound" else "custom")
                .put("answerScopes", answerLevelsJson(effectiveLevels))
                .put(
                    "answerScopeAvailable",
                    QuizQuestionCache.availableCount(
                        QuizQuestionCache.LOCAL_SCOPE,
                        answerScope = effectiveScope,
                    ),
                )
                .put("syncState", QuizSessionStore.loadSyncState().encode()),
        )
    }

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

    private fun setEnabled(session: IHTTPSession, enabled: Boolean): Response {
        adminError(session)?.let { return it }
        when (QuizSettings.setEnabled(enabled)) {
            QuizSettings.SetResult.SessionRunning ->
                return error(Response.Status.CONFLICT, "SESSION_RUNNING")
            QuizSettings.SetResult.StorageError ->
                return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
            QuizSettings.SetResult.Changed -> Unit
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("enabled", enabled)
                .put("syncState", QuizSessionStore.loadSyncState().encode()),
        )
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
        if (!QuizSessionStore.saveProviderMeta(QuizProviderMeta(base, model))) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        if (supplied.isNotEmpty() && !QuizSessionStore.saveProviderKey(key)) {
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
        val prefs = QuizSessionStore.loadGenerationPrefs()
        return json(
            Response.Status.OK,
            JSONObject().put("ok", true).put("prefs", prefs ?: JSONObject.NULL),
        )
    }

    private fun saveGenerationPrefs(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val prefs = QuizGeneration.parsePreferences(body)
            ?: return error(Response.Status.BAD_REQUEST, "INVALID_REQUEST")
        val gradeNo = QuizCurriculum.current()?.gradeNoForCode(prefs.optString("grade", ""))
        if (isCloudJuniorBlocked(gradeNo)) {
            return error(Response.Status.CONFLICT, "CLOUD_JUNIOR_UNSUPPORTED")
        }
        if (!QuizSessionStore.saveGenerationPrefs(prefs)) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        // Levels are a frozen parent-designed list now; changing the generation
        // settings no longer moves the answer scope.
        return json(Response.Status.OK, JSONObject().put("ok", true))
    }

    private fun startGeneration(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        if (!QuizCrypto.isEncrypted) return error(Response.Status.CONFLICT, "AI_UNSUPPORTED")
        if (!QuizSessionStore.consentAccepted()) return error(Response.Status.CONFLICT, "CONSENT_REQUIRED")
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
        val request = when (val parsed = QuizGeneration.parseRequest(body)) {
            is QuizGeneration.ParseResult.Ok -> parsed.request
            is QuizGeneration.ParseResult.Invalid -> return invalidRequest(parsed.fields)
        }
        if (isCloudJuniorBlocked(request.gradeNo)) {
            return generationFailure("CLOUD_JUNIOR_UNSUPPORTED")
        }
        return when (val started = QuizGeneration.start(adminToken(session), request)) {
            is QuizGeneration.StartResult.Started -> json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
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
        val jobId = session.parameters["jobId"]?.firstOrNull().orEmpty()
        if (!JOB_ID_RE.matches(jobId)) return error(Response.Status.BAD_REQUEST, "INVALID_JOB")
        return when (val result = QuizGeneration.poll(adminToken(session), jobId)) {
            is QuizGeneration.PollResult.Running -> json(
                Response.Status.OK,
                generationJson("running", result.progress, jobId),
            )
            is QuizGeneration.PollResult.Ready -> json(
                Response.Status.OK,
                generationJson("ready", result.progress, jobId)
                    .put("batch", stagedBatchJson(result.batch)),
            )
            is QuizGeneration.PollResult.Failed -> json(
                Response.Status.OK,
                generationJson("failed", result.progress, jobId)
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
        val jobId = body.optString("jobId", "")
        if (!JOB_ID_RE.matches(jobId)) return error(Response.Status.BAD_REQUEST, "INVALID_JOB")
        if (!QuizGeneration.cancel(adminToken(session), jobId)) {
            return error(Response.Status.NOT_FOUND, "JOB_LOST")
        }
        return json(Response.Status.OK, JSONObject().put("ok", true))
    }

    /** Commits only the rows the parent selected on the review page. */
    private fun addGeneration(session: IHTTPSession): Response {
        adminError(session)?.let { return it }
        val body = jsonBody(session) ?: return error(Response.Status.BAD_REQUEST, "INVALID_JSON")
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
        return when (val result = QuizGeneration.add(adminToken(session), jobId, indexes)) {
            is QuizGeneration.CommitResult.Ok -> {
                val state = QuizRuntime.refresh()
                json(
                    Response.Status.OK,
                    JSONObject()
                        .put("ok", true)
                        .put("appended", result.appended)
                        .put("duplicates", result.duplicates)
                        .put("rejected", result.rejected)
                        .put("capacityReached", result.capacityReached)
                        .put("counts", bankCountsJson(state.bankCounts))
                        .put("bankState", state.bankState),
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
        val jobId = body.optString("jobId", body.optString("batchId", ""))
        if (!JOB_ID_RE.matches(jobId)) return error(Response.Status.BAD_REQUEST, "INVALID_JOB")
        if (!QuizGeneration.discard(adminToken(session), jobId)) {
            return error(Response.Status.NOT_FOUND, "JOB_LOST")
        }
        return json(Response.Status.OK, JSONObject().put("ok", true))
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

    private fun providerJson(): JSONObject {
        val meta = QuizSessionStore.loadProviderMeta()
        return JSONObject()
            .put("ok", true)
            .put("aiSupported", QuizCrypto.isEncrypted)
            .put("consentAccepted", QuizSessionStore.consentAccepted())
            .put("configured", QuizSessionStore.providerConfigured())
            .put("baseUrl", meta.baseUrl)
            .put("model", meta.model)
    }

    private fun generationJson(
        state: String,
        progress: QuizGeneration.Progress,
        jobId: String,
    ): JSONObject =
        JSONObject()
            .put("ok", true)
            .put("state", state)
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
                        .put("answerIndex", question.answerIndex)
                        .put("answerText", question.options.getOrNull(question.answerIndex).orEmpty())
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
     * The ordered levels plus a per-level availability count. The phone list
     * uses the count to warn before a child picks a level the bank cannot fill.
     */
    private fun answerLevelsJson(levels: List<QuizLevel>): JSONArray =
        JSONArray().apply {
            levels.forEach { level ->
                put(
                    JSONObject()
                        .put("id", level.id)
                        .put("name", level.name)
                        .put("scope", level.scope.encode())
                        .put(
                            "available",
                            QuizQuestionCache.availableCount(
                                QuizQuestionCache.LOCAL_SCOPE,
                                answerScope = level.scope,
                            ),
                        ),
                )
            }
        }

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

    private fun json(status: Response.Status, body: JSONObject): Response =
        newResponse(status, "application/json; charset=utf-8", body.toString())

    private fun error(status: Response.Status, code: String): Response =
        json(status, JSONObject().put("ok", false).put("error", code))

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
    private val BANK_FILTERS = setOf("all", "unused", "answered", "wrong", "correct")
    private val FACET_DIFFICULTIES = setOf("EASY", "MEDIUM", "HARD")
}
