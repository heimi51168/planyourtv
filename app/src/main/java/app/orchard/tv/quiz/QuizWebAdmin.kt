package app.orchard.tv.quiz

import android.content.Context
import app.orchard.tv.policy.PolicyModeCoordinator
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONObject
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizDecodeResult
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionCodec
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizSessionValidation
import org.json.JSONArray
import java.util.WeakHashMap

/**
 * The quiz page and its small JSON API. PhoneServer hands over only paths below
 * its random page token; this object still checks the pairing secret + parent
 * PIN before it reads or changes any session.
 */
object QuizWebAdmin {
    private const val MAX_JSON_BYTES = 256 * 1024
    private val requestBodies = WeakHashMap<IHTTPSession, JSONObject?>()

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
        // and fail with "HTTP verb {}POST unhandled". Read the body once here
        // for every request; jsonBody() serves the cached value to the handler.
        jsonBody(session)
        if (!sameOrigin(session)) return error(Response.Status.FORBIDDEN, "ORIGIN")
        return when {
            session.method == Method.POST && path == "/api/quiz/pair" -> pair(session)
            session.method == Method.POST && path == "/api/quiz/auth/pin" -> pin(session)
            session.method == Method.POST && path == "/api/quiz/auth/logout" -> logout(session)
            session.method == Method.GET && path == "/api/quiz/admin/session" -> readSession(session)
            session.method == Method.PUT && path == "/api/quiz/admin/session" -> putSession(session)
            session.method == Method.DELETE && path == "/api/quiz/admin/session" -> deleteSession(session)
            session.method == Method.POST && path == "/api/quiz/admin/challenge-config" -> saveChallengeConfig(session)
            session.method == Method.GET && path == "/api/quiz/admin/bank" -> readBank(session)
            session.method == Method.GET && path == "/api/quiz/admin/bank/facets" -> readBankFacets(session)
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
        return when (val result = QuizAccess.authenticate(pin)) {
            is QuizAccess.AuthResult.Ok -> json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put("adminToken", result.adminToken)
                    .put("expiresAt", result.expiresAt),
            )
            QuizAccess.AuthResult.PinInvalid -> error(Response.Status.UNAUTHORIZED, "PIN_INVALID")
            QuizAccess.AuthResult.PinNotSet -> error(Response.Status.CONFLICT, "PIN_NOT_SET")
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
        body.put("consentAccepted", QuizSessionStore.consentAccepted())
        body.put("providerConfigured", QuizSessionStore.providerConfigured())
        body.put("challengeConfig", QuizSessionCodec.encodeConfig(challengeConfig))
        val answerScope = QuizAnswerScopeStore.loadOrInitialize()
        body.put("answerScope", answerScope?.encode() ?: JSONObject.NULL)
        body.put(
            "answerScopeMode",
            if (QuizAnswerScopeStore.mode() == QuizAnswerScopeStore.Mode.BOUND) {
                "bound"
            } else {
                "custom"
            },
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
        val now = System.currentTimeMillis()
        val rows = QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, filter, offset, limit, now)
        val items = JSONArray()
        rows.forEach { row ->
            val question = row.question
            items.put(
                JSONObject()
                    .put("cacheId", row.cacheId)
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
            it.toIntOrNull()?.takeIf { value -> value in 1..6 }
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
        val topic = param("topic")?.let { raw ->
            val normalized = raw.uppercase()
            when {
                normalized.startsWith("UNIT:") -> {
                    val unit = normalized.removePrefix("UNIT:").toIntOrNull()
                        ?.takeIf { it in 1..8 }
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
                !item.scopeSubject.equals(filter.subject, ignoreCase = true)
            ) {
                return false
            }
            if (skip != "textbook" && filter.textbook != null &&
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
        if (grade !in 1..6 || semester !in 1..2) return null
        if (!QuizAnswerScope.isSupportedSubject(item.scopeSubject)) return null
        if (!QuizAnswerScope.isSupportedTextbook(item.scopeTextbook)) return null
        val type = item.scopeType.trim().uppercase()
        if (!QuizAnswerScope.isSupportedScopeType(type)) return null
        val topic = if (type == "UNIT") {
            val unit = item.scopeUnitNo ?: return null
            if (unit !in 1..8) return null
            "UNIT:$unit"
        } else {
            if (item.scopeUnitNo != null) return null
            type
        }
        return FacetRow(item, topic)
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
        if (!QuizQuestionCache.clearBank(QuizQuestionCache.LOCAL_SCOPE, mode)) {
            return error(Response.Status.INTERNAL_ERROR, "CACHE_UNAVAILABLE")
        }
        val state = QuizRuntime.refresh()
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("counts", bankCountsJson(state.bankCounts))
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
        if (config.maxEarnedMinutes < config.minutesPerPoint ||
            config.minCorrectCount > config.pullCount
        ) {
            return error(Response.Status.BAD_REQUEST, "INVALID_CONFIG")
        }
        // 答题形式与答题范围一次保存、一次同步。旧页面不带 scopeMode 时按
        // scopeProvided/null 兼容：null = 绑定出题范围，对象 = 自定义范围。
        val scopeMode = body.optString("scopeMode", "").trim().lowercase()
        val scopeProvided = body.optBoolean("scopeProvided", false) || scopeMode.isNotEmpty()
        var scopeBound = false
        val scope = when (scopeMode) {
            "bound" -> {
                scopeBound = true
                null
            }
            "custom" -> {
                if (!body.has("scope") || body.isNull("scope")) {
                    return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
                }
                QuizAnswerScope.parse(body.optJSONObject("scope"))
                    ?: return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
            }
            "" -> {
                if (!scopeProvided) {
                    null
                } else if (!body.has("scope")) {
                    return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
                } else if (body.isNull("scope")) {
                    scopeBound = true
                    null
                } else {
                    QuizAnswerScope.parse(body.optJSONObject("scope"))
                        ?: return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
                }
            }
            else -> return error(Response.Status.BAD_REQUEST, "INVALID_SCOPE")
        }
        val previousScopeSignature = if (scopeProvided) {
            QuizAnswerScopeStore.currentSignature()
        } else {
            null
        }
        if (!QuizSessionStore.saveChallengeConfig(config)) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        if (scopeProvided && !QuizSessionStore.saveAnswerScope(scope)) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        QuizConfigSync.afterLocalEdit()
        val effectiveScope = if (scopeProvided && !scopeBound) {
            scope
        } else {
            QuizAnswerScopeStore.loadOrInitialize()
        }
        if (previousScopeSignature != null &&
            previousScopeSignature != QuizAnswerScopeStore.signature(effectiveScope)
        ) {
            QuizConfigSync.afterLocalScopeChange()
        }
        val effectiveBound = if (scopeProvided) {
            scopeBound
        } else {
            QuizAnswerScopeStore.mode() == QuizAnswerScopeStore.Mode.BOUND
        }
        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("config", QuizSessionCodec.encodeConfig(config))
                .put("answerScope", effectiveScope?.encode() ?: JSONObject.NULL)
                .put("answerScopeMode", if (effectiveBound) "bound" else "custom")
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
        if (!QuizSessionStore.saveGenerationPrefs(prefs)) {
            return error(Response.Status.INTERNAL_ERROR, "STORAGE_ERROR")
        }
        // 绑定模式下答题范围实时跟随出题配置，因此出题配置变化也要同步云端；
        // 自定义模式仍保持两者解耦。
        if (QuizAnswerScopeStore.mode() == QuizAnswerScopeStore.Mode.BOUND) {
            QuizConfigSync.afterLocalScopeChange()
            return json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put("syncState", QuizSessionStore.loadSyncState().encode()),
            )
        }
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
        if (key.length !in 8..512) return null
        if (key.any { it.code < 0x21 || it.code > 0x7E }) return null
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

    /** The pre-V17 `cache` shape, kept so a cached page still renders counts. */
    private fun legacyCacheJson(counts: QuizQuestionCache.BankCounts): JSONObject =
        JSONObject()
            .put("available", counts.unused)
            .put("reserved", counts.inRound)
            .put("consumed", counts.answered)
            .put("expired", counts.expired)

    private fun generationFailure(code: String): Response = when (code) {
        "CONSENT_REQUIRED", "PROVIDER_NOT_CONFIGURED", "BUSY", "BANK_FULL",
        "CACHE_UNAVAILABLE" ->
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

    private fun jsonBody(session: IHTTPSession): JSONObject? =
        synchronized(requestBodies) {
            if (requestBodies.containsKey(session)) {
                requestBodies[session]
            } else {
                readJsonBody(session).also { requestBodies[session] = it }
            }
        }

    private fun readJsonBody(session: IHTTPSession): JSONObject? {
        // NanoHTTPD 2.3.1 only exposes parseBody() results for POST, so a PUT
        // body would disappear. Read exactly Content-Length bytes for every
        // method instead; a missing length is rejected rather than blocking on
        // a keep-alive stream.
        val declared = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (declared <= 0 || declared > MAX_JSON_BYTES) return null
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
