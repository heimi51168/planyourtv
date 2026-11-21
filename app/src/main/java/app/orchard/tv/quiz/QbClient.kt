package app.orchard.tv.quiz

import android.os.SystemClock
import android.util.Log
import app.orchard.tv.BuildConfig
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.quiz.QUIZ_MAX_EXPLANATION_CHARS
import com.planyourtv.policy.quiz.QUIZ_MAX_OPTION_CHARS
import com.planyourtv.policy.quiz.QUIZ_MAX_PROMPT_CHARS
import com.planyourtv.policy.quiz.QUIZ_MAX_TIME_LIMIT_SEC
import com.planyourtv.policy.quiz.QUIZ_MIN_TIME_LIMIT_SEC
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizSubmission
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

/**
 * 电视端的题库（QB）客户端。
 *
 * 鉴权与 uniapp 的家长票据同构，只是作用域换成设备：
 *   1. 用设备 v2 凭证向主后端换一次性设备票据（60 秒）；
 *   2. 用票据向 QB 换 qbs_ 设备会话（30 分钟，内存缓存，401 时整体重换）。
 *
 * 地址推导必须与 `uxb_garden_uniapp/src/api/qb.ts` 保持一致：
 * dev（主后端带显式端口）→ 同主机 :8091；preview/pro（域名）→ 同域名 /qb。
 *
 * 这里只做协议翻译：取题 / 开始 / 作答 / 提交 / 放弃。题目落缓存由
 * [CloudQuizPreparer] 负责；答题引擎永远只读缓存。
 */
object QbClient {
    private const val TAG = "QbClient"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val TIMEOUT_MS = 10_000
    private const val NEXT_TIMEOUT_MS = 20_000
    private const val QB_PORT = 8091
    private const val QB_PATH_PREFIX = "/qb"
    private const val TOKEN_MARGIN_MS = 120_000L

    /**
     * TV 的 session/question id 校验比 QB 的 ULID 更严（首字符必须是字母），
     * 而 ULID 以数字开头。所以云端 id 在电视内部统一加前缀，回传 QB 时去掉。
     */
    const val QUESTION_ID_PREFIX = "qb_"
    const val SESSION_ID_PREFIX = "sess_"
    const val CLOUD_SESSION_SOURCE = "cloud"

    private val ATTEMPT_UID = Regex("[0-9A-Za-z]{26}")

    /**
     * QB 云端题号：公共/年级题是 26 位 ULID；错题/我的题库由 QB 按快照合成
     * `WRONG-<id>` / `PERSONAL-<id>`（三题库出题策略，2026-11-12）。
     * 两种形态都原样回传 QB，电视内部统一再加 [QUESTION_ID_PREFIX]。
     */
    private val CLOUD_QUESTION_ID = Regex("(?:[0-9A-Za-z]{26}|(?:WRONG|PERSONAL)-[0-9]+)")
    private val DIFFICULTIES = setOf("easy", "medium", "hard")
    private const val MAX_CLOUD_LEVELS = 20
    private const val MAX_CLOUD_LEVEL_QUESTIONS = 20

    /** QB/主后端的业务拒绝：HTTP 200 + 非 200 code，message 是可直接展示的人话。 */
    class QbException(
        val httpCode: Int,
        val apiCode: Int,
        message: String,
        val businessMessage: String? = null,
    ) : IOException(message)

    /**
     * QB 返回体不符合契约（缺字段/类型不对/id 非法）。
     *
     * 与网络错误区分开：这是确定性失败，重试不会变好；上层据此置 INVALID_DOCUMENT
     * 并保留上一版文档，而不是无限退避重试。
     */
    class QbContractException(message: String) : IOException(message)

    data class CloudQuestionSet(
        val attemptUid: String,
        val items: List<QuizItem>,
        val expiresAt: Long,
        val requested: Int,
        val available: Int,
        val insufficient: Boolean,
    )

    data class DeviceLearnerSelection(
        val selectedLearnerId: Long?,
        val learners: List<CloudLearner>,
    )

    @Volatile private var sessionToken: String? = null
    @Volatile private var sessionRefreshAtElapsed: Long = 0L

    /** 取一轮题（默认 BANK 来源）。QB 复用未开始的旧题集，所以重复调用不额外烧配额。 */
    fun fetchNext(count: Int, levelId: Long? = null): CloudQuestionSet {
        // 负数 id 是 QB 内置默认关卡（-1..-4），不是非法值；只有 0 不存在。
        if (levelId != null && levelId == 0L) {
            throw QbContractException("QB level id is invalid")
        }
        val query = buildString {
            append("/api/v1/quiz/next?count=")
            append(count)
            if (levelId != null) {
                append("&levelId=")
                append(levelId)
            }
        }
        val json = authorized("GET", query, null, NEXT_TIMEOUT_MS)
        val result = json.optJSONObject("result")
            ?: throw IOException("QB next response missing result")
        val attemptUid = result.optString("attemptUid", "").trim()
        if (!ATTEMPT_UID.matches(attemptUid)) {
            throw IOException("QB next returned an invalid attemptUid")
        }
        val expiresAt = result.optLong("expiresAt", 0L)
        if (expiresAt <= 0L) throw IOException("QB next returned an invalid expiresAt")
        val array = result.optJSONArray("items")
            ?: throw IOException("QB next response missing items")
        if (array.length() == 0) throw IOException("QB next returned no questions")
        val items = (0 until array.length()).map { index ->
            val raw = array.optJSONObject(index)
                ?: throw IOException("QB next item is not an object")
            parseItem(raw)
        }
        if (items.map { it.questionId }.toSet().size != items.size) {
            throw IOException("QB next returned duplicate question ids")
        }
        return CloudQuestionSet(
            attemptUid = attemptUid,
            items = items,
            expiresAt = expiresAt,
            requested = result.optInt("requested", count),
            available = result.optInt("available", items.size),
            insufficient = result.optBoolean("insufficient", false),
        )
    }

    /** 家庭孩子列表；`selected` 是 QB 的 qb_device_learner 当前选择。 */
    fun deviceLearners(): DeviceLearnerSelection {
        val json = authorized("GET", "/api/v1/quiz/learners", null, TIMEOUT_MS)
        val result = json.optJSONObject("result")
            ?: throw QbContractException("QB learners response missing result")
        val array = result.optJSONArray("learners")
            ?: throw QbContractException("QB learners response missing learners")
        val learners = (0 until array.length()).map { index ->
            val raw = array.optJSONObject(index)
                ?: throw QbContractException("QB learner is not an object")
            val learnerId = raw.optLong("learnerId", 0L)
            if (learnerId <= 0L) throw QbContractException("QB learner id is invalid")
            val memberId = raw.optLong("memberId", 0L).takeIf { it > 0L }
            val name = raw.optString("name", "").trim().take(64)
            CloudLearner(
                learnerId = learnerId,
                memberId = memberId,
                name = name,
                selected = raw.optBoolean("selected", false),
            )
        }
        if (learners.map { it.learnerId }.toSet().size != learners.size) {
            throw QbContractException("QB learners contain duplicate ids")
        }
        val selectedLearnerId = result.optLong("selectedLearnerId", 0L).takeIf { it > 0L }
        return DeviceLearnerSelection(selectedLearnerId, learners)
    }

    /**
     * 把 QB 的「当前孩子」切到 [learnerId]（`qb_device_learner`）；之后 `/next` 按该孩子取题。
     *
     * 电视跟随果园当前用户（`device_list.active_member_id`）调用：用户切换后，QB 上次的
     * 选择不能继续生效，否则会把上一个孩子的题库下发给新用户。
     */
    fun selectDeviceLearner(learnerId: Long) {
        if (learnerId <= 0L) throw QbContractException("QB learner id is invalid")
        val json = authorized(
            "POST",
            "/api/v1/quiz/learners/$learnerId/select",
            null,
            TIMEOUT_MS,
        )
        val result = json.optJSONObject("result")
            ?: throw QbContractException("QB select response missing result")
        val selected = result.optLong("selectedLearnerId", 0L)
        if (selected != learnerId) {
            throw QbContractException("QB select did not apply the learner")
        }
    }

    /** QB 的已启用关卡（QCD 关卡部分）。字段缺失/形态不对一律拒绝，不静默补默认值。 */
    fun deviceLevels(learnerId: Long): CloudLevelsBundle {
        if (learnerId <= 0L) throw QbContractException("QB learner id is invalid")
        val json = authorized(
            "GET",
            "/api/v1/quiz/learners/$learnerId/levels",
            null,
            TIMEOUT_MS,
        )
        return parseLevelsResult(
            json.optJSONObject("result")
                ?: throw QbContractException("QB levels response missing result"),
            learnerId,
        )
    }

    internal fun parseLevelsResult(result: JSONObject, requestedLearnerId: Long): CloudLevelsBundle {
        val learner = result.optJSONObject("learner")
            ?: throw QbContractException("QB levels response missing learner")
        val responseLearnerId = learner.optLong("learnerId", 0L)
        if (responseLearnerId != requestedLearnerId) {
            throw QbContractException("QB levels learner does not match the request")
        }
        val memberId = learner.optLong("memberId", 0L).takeIf { it > 0L }
        val array = result.optJSONArray("levels")
            ?: throw QbContractException("QB levels response missing levels")
        if (array.length() > MAX_CLOUD_LEVELS) {
            throw QbContractException("QB levels exceed the TV limit")
        }
        val levels = (0 until array.length()).map { index ->
            parseLevel(array.optJSONObject(index)
                ?: throw QbContractException("QB level is not an object"))
        }
        if (levels.map { it.id }.toSet().size != levels.size) {
            throw QbContractException("QB levels contain duplicate ids")
        }
        val revision = result.optString("revision", "").trim()
        if (!CloudLevelsClient.revisionIsValid(revision)) {
            throw QbContractException("QB levels revision is invalid")
        }
        val levelsSource = result.optString("levelsSource", "").trim()
        if (levelsSource != "DEFAULT" && levelsSource != "CUSTOM") {
            throw QbContractException("QB levels source is invalid")
        }
        val checkin = result.optJSONObject("checkin")
            ?: throw QbContractException("QB levels response missing checkin")
        return CloudLevelsBundle(
            learnerId = responseLearnerId,
            memberId = memberId,
            levels = levels,
            checkin = checkin,
            revision = revision,
            levelsSource = levelsSource,
        )
    }

    private fun parseLevel(raw: JSONObject): CloudLevel {
        val id = raw.optLong("id", 0L)
        // QB 内置默认关卡用负数 id（DEFAULT_LEVEL_MIXED=-4 等），0 才是非法值。
        if (id == 0L) throw QbContractException("QB level id is invalid")
        val sources = stringList(raw.optJSONArray("sources"), "sources")
        if (sources.isEmpty()) throw QbContractException("QB level sources is empty")
        val questionTypes = stringList(raw.optJSONArray("questionTypes"), "questionTypes")
        if (questionTypes.isEmpty()) throw QbContractException("QB level questionTypes is empty")
        val origin = raw.optString("origin", "").trim()
        if (origin != "CLOUD") throw QbContractException("QB level origin is invalid")
        val sourceScopes = raw.optJSONObject("sourceScopes")
            ?: throw QbContractException("QB level sourceScopes is missing")
        val sortNo = raw.optInt("sortNo", -1)
        if (sortNo < 0) throw QbContractException("QB level sortNo is invalid")
        val questionCount = raw.optInt("questionCount", 0)
        if (questionCount !in 1..MAX_CLOUD_LEVEL_QUESTIONS) {
            throw QbContractException("QB level questionCount is invalid")
        }
        val advisory = optionalInt(raw, "advisoryQuestionCount")
        if (advisory != null && advisory !in 1..MAX_CLOUD_LEVEL_QUESTIONS) {
            throw QbContractException("QB level advisoryQuestionCount is invalid")
        }
        val grade = optionalInt(raw, "grade")
        val semester = optionalInt(raw, "semester")
        val textbookId = raw.optLong("textbookId", 0L).takeIf { it > 0L }
        return CloudLevel(
            id = id,
            name = optionalText(raw, "name")?.take(40).orEmpty(),
            enabled = raw.optBoolean("enabled", false),
            sortNo = sortNo,
            sources = sources,
            subjects = stringList(raw.optJSONArray("subjects"), "subjects"),
            scopeType = optionalText(raw, "scopeType"),
            unitNos = intList(raw.optJSONArray("unitNos"), "unitNos"),
            unitGroups = intList(raw.optJSONArray("unitGroups"), "unitGroups"),
            textbookId = textbookId,
            grade = grade,
            semester = semester,
            difficulty = optionalText(raw, "difficulty")?.uppercase(Locale.ROOT),
            advisoryQuestionCount = advisory,
            questionTypes = questionTypes,
            sourceScopes = sourceScopes,
            origin = origin,
            questionCount = questionCount,
            available = raw.optBoolean("available", false),
            availableCount = raw.optInt("availableCount", 0).coerceAtLeast(0),
            unavailableReason = optionalText(raw, "unavailableReason")?.take(160),
        )
    }

    private fun stringList(array: JSONArray?, field: String): List<String> {
        if (array == null) throw QbContractException("QB level $field is missing")
        return (0 until array.length()).map { index ->
            val value = array.optString(index, "").trim().uppercase(Locale.ROOT)
            if (value.isEmpty()) throw QbContractException("QB level $field contains an empty value")
            value
        }
    }

    private fun intList(array: JSONArray?, field: String): List<Int> {
        if (array == null) throw QbContractException("QB level $field is missing")
        return (0 until array.length()).map { index ->
            val value = array.optInt(index, -1)
            if (value < 0) throw QbContractException("QB level $field contains an invalid value")
            value
        }
    }

    private fun optionalInt(raw: JSONObject, key: String): Int? =
        if (!raw.has(key) || raw.isNull(key)) null else raw.optInt(key, -1).takeIf { it >= 0 }

    private fun optionalText(raw: JSONObject, key: String): String? =
        if (!raw.has(key) || raw.isNull(key)) null else raw.optString(key, "").trim().ifEmpty { null }

    /** 幂等；QB 在作答/提交时也会补做，这里提前烧掉配额并留下开始时间。 */
    fun startAttempt(attemptUid: String) {
        authorized("POST", "/api/v1/quiz/attempts/$attemptUid/start", "{}", TIMEOUT_MS)
    }

    fun saveAnswers(attemptUid: String, answers: List<QuizSubmission>) {
        if (answers.isEmpty()) return
        val array = JSONArray()
        answers.forEach { submission ->
            array.put(
                JSONObject()
                    .put("questionId", qbQuestionId(submission.questionId))
                    .put(
                        "selectedIndex",
                        submission.selectedIndex?.let { it } ?: JSONObject.NULL,
                    )
                    .put("timedOut", submission.timedOut)
                    .put("timeSpentMs", 0),
            )
        }
        authorized(
            "POST",
            "/api/v1/quiz/attempts/$attemptUid/answers",
            JSONObject().put("answers", array).toString(),
            TIMEOUT_MS,
        )
    }

    /** 提交会写错题本并返回服务端判定的答对数。 */
    fun submitAttempt(attemptUid: String): Int? {
        val json = authorized(
            "POST",
            "/api/v1/quiz/attempts/$attemptUid/submit",
            "{}",
            TIMEOUT_MS,
        )
        return json.optJSONObject("result")?.optInt("correctCount")
    }

    fun abandonAttempt(attemptUid: String) {
        authorized("POST", "/api/v1/quiz/attempts/$attemptUid/abandon", "{}", TIMEOUT_MS)
    }

    /** 会话过期时丢弃缓存并重新换票据；权限仍由设备凭证决定。 */
    @Synchronized
    fun invalidateSession() {
        sessionToken = null
        sessionRefreshAtElapsed = 0L
    }

    private fun qbQuestionId(questionId: String): String {
        val raw = questionId.removePrefix(QUESTION_ID_PREFIX)
        if (!CLOUD_QUESTION_ID.matches(raw)) {
            throw IOException("cached cloud question id is invalid")
        }
        return raw
    }

    internal fun parseItem(raw: JSONObject): QuizItem {
        val uid = raw.optString("questionId", "").trim()
        if (!CLOUD_QUESTION_ID.matches(uid)) throw IOException("QB question id is invalid")
        val prompt = raw.optString("prompt", "").trim()
        if (prompt.isEmpty() || prompt.length > QUIZ_MAX_PROMPT_CHARS) {
            throw IOException("QB question prompt is out of range")
        }
        val optionsArray = raw.optJSONArray("options")
            ?: throw IOException("QB question options missing")
        if (optionsArray.length() !in 2..6) {
            throw IOException("QB question option count is out of range")
        }
        val options = (0 until optionsArray.length()).map { index ->
            val text = optionsArray.optString(index, "").trim()
            if (text.isEmpty() || text.length > QUIZ_MAX_OPTION_CHARS) {
                throw IOException("QB question option is out of range")
            }
            text
        }
        if (options.map { it.lowercase(Locale.ROOT) }.toSet().size != options.size) {
            throw IOException("QB question options are duplicated")
        }
        val answerIndex = raw.optInt("answerIndex", -1)
        if (answerIndex !in options.indices) {
            throw IOException("QB question answerIndex is out of range")
        }
        val explanation = raw.optString("explanation", "").trim()
        if (explanation.length > QUIZ_MAX_EXPLANATION_CHARS) {
            throw IOException("QB question explanation is too long")
        }
        val subject = raw.optString("subject", "").trim()
        val grade = raw.optString("grade", "").trim()
        if (subject.length > 32 || grade.length > 32) {
            throw IOException("QB question metadata is too long")
        }
        val difficulty = raw.optString("difficulty", "").trim().lowercase(Locale.ROOT)
        if (difficulty.isNotEmpty() && difficulty !in DIFFICULTIES) {
            throw IOException("QB question difficulty is invalid")
        }
        val points = raw.optInt("points", 1)
        if (points !in 1..5) throw IOException("QB question points is out of range")
        val timeLimit = raw.optInt("timeLimitSec", 60)
        if (timeLimit !in QUIZ_MIN_TIME_LIMIT_SEC..QUIZ_MAX_TIME_LIMIT_SEC) {
            throw IOException("QB question time limit is out of range")
        }
        return QuizItem(
            questionId = QUESTION_ID_PREFIX + uid,
            prompt = prompt,
            options = options,
            answerIndex = answerIndex,
            explanation = explanation,
            subject = subject,
            grade = grade,
            difficulty = difficulty,
            points = points,
            timeLimitSec = timeLimit,
            recommendedTimeSec = timeLimit,
        )
    }

    private fun authorized(
        method: String,
        path: String,
        bodyText: String?,
        readTimeoutMs: Int,
    ): JSONObject {
        val token = ensureSession()
        return try {
            requestRaw(method, qbBase + path, token, bodyText, readTimeoutMs)
        } catch (error: QbException) {
            if (error.httpCode != 401 && error.apiCode != 401) throw error
            // qbs_ 会话失效（30 分钟或重启）：重换票据与会话后重试一次。
            Log.w(TAG, "QB session rejected; exchanging a fresh device ticket")
            val fresh = ensureSession(force = true)
            requestRaw(method, qbBase + path, fresh, bodyText, readTimeoutMs)
        }
    }

    @Synchronized
    private fun ensureSession(force: Boolean = false): String {
        val now = SystemClock.elapsedRealtime()
        val cached = sessionToken
        if (!force && cached != null && now < sessionRefreshAtElapsed - TOKEN_MARGIN_MS) {
            return cached
        }
        val ticket = requestDeviceTicket()
        val json = requestRaw(
            "POST",
            "$qbBase/api/v1/auth/sessions",
            token = null,
            bodyText = JSONObject().put("ticket", ticket).toString(),
            readTimeoutMs = TIMEOUT_MS,
        )
        val result = json.optJSONObject("result")
            ?: throw IOException("QB session response missing result")
        val token = result.optString("token", "").trim()
        if (!token.startsWith("qbs_")) throw IOException("QB session token is invalid")
        val expiresIn = result.optLong("expiresIn", 0L)
        if (expiresIn <= 0L) throw IOException("QB session ttl is invalid")
        sessionToken = token
        sessionRefreshAtElapsed = now + expiresIn * 1_000L
        return token
    }

    private fun requestDeviceTicket(): String {
        val deviceToken = PolicyRuntime.deviceToken()
        if (deviceToken.isBlank()) throw IOException("device credential missing")
        // 答题范围由主后端从 device_quiz_config 注入；电视不再上传出题配置。
        val json = requestRaw(
            "POST",
            "${mainApiBase()}/qb/device-tickets",
            deviceToken,
            "{}",
            TIMEOUT_MS,
        )
        val ticket = json.optJSONObject("result")?.optString("ticket", "")?.trim().orEmpty()
        if (ticket.isEmpty()) throw IOException("device ticket is empty")
        return ticket
    }

    private fun mainApiBase(): String {
        val raw = BuildConfig.TV_API_BASE.trim().trimEnd('/')
        if (raw.isEmpty()) throw IOException("TV_API_BASE is empty")
        return if (raw.endsWith("/api/v1")) raw else "$raw/api/v1"
    }

    private fun mainOrigin(): String {
        val raw = BuildConfig.TV_API_BASE.trim().trimEnd('/')
        if (raw.isEmpty()) throw IOException("TV_API_BASE is empty")
        return when {
            raw.endsWith("/api/v1") -> raw.dropLast("/api/v1".length)
            raw.endsWith("/api/v2") -> raw.dropLast("/api/v2".length)
            else -> raw
        }
    }

    /** 与 uniapp qbBaseUrl() 同一规则：显式端口 = dev → :8091；域名 → /qb 前缀。 */
    private val qbBase: String
        get() {
            val origin = mainOrigin()
            val uri = try {
                URI(origin)
            } catch (error: Exception) {
                throw IOException("TV_API_BASE is not a valid origin", error)
            }
            val host = uri.host ?: throw IOException("TV_API_BASE has no host")
            val scheme = uri.scheme ?: throw IOException("TV_API_BASE has no scheme")
            return if (uri.port > 0) {
                "$scheme://$host:$QB_PORT"
            } else {
                "$scheme://$host$QB_PATH_PREFIX"
            }
        }

    private fun requestRaw(
        method: String,
        url: String,
        token: String?,
        bodyText: String?,
        readTimeoutMs: Int,
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (bodyText != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(bodyText.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val httpCode = connection.responseCode
            val stream = if (httpCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) {
                JSONObject()
            } else {
                try {
                    JSONObject(text)
                } catch (_: JSONException) {
                    JSONObject()
                }
            }
            if (httpCode !in 200..299) {
                val apiCode = json.optInt("code", httpCode)
                val message = json.optString("message", "").trim().take(160)
                throw QbException(
                    httpCode,
                    apiCode,
                    if (message.isEmpty()) "HTTP $httpCode" else "HTTP $httpCode: $message",
                    businessMessage = message.ifEmpty { null },
                )
            }
            if (json.length() == 0) {
                throw IOException("invalid QB response (HTTP $httpCode)")
            }
            val code = json.optInt("code", 200)
            if (code != 200) {
                val message = json.optString("message", "").trim().take(160)
                throw QbException(
                    httpCode,
                    code,
                    if (message.isEmpty()) "API $code" else "API $code: $message",
                    businessMessage = message.ifEmpty { null },
                )
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}
