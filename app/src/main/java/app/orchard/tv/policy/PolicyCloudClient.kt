package app.orchard.tv.policy

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import app.orchard.tv.BuildConfig
import app.orchard.tv.R
import app.orchard.tv.quiz.QuizConfigSync
import com.planyourtv.policy.AccessMode
import com.planyourtv.policy.Activation
import com.planyourtv.policy.CloudPinApplyResult
import com.planyourtv.policy.LocalRuleSets
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.PolicyConfig
import com.planyourtv.policy.PolicyFlowTrace
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.PolicySyncHost
import com.planyourtv.policy.PolicySyncSnapshot
import com.planyourtv.policy.RemotePolicy
import com.planyourtv.policy.RemotePolicyCodec
import com.planyourtv.policy.RemotePolicyMode
import com.planyourtv.policy.RuleUsageCodec
import com.planyourtv.policy.RuleUsageEvent
import com.planyourtv.policy.quiz.RemoteQuizConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One row of the TV app inventory sent to the phone/parent backend. */
data class PolicyAppUpload(
    val appName: String,
    val packageName: String,
    val appIcon: String?,
)

/** A short-lived code shown on the TV so the parent can prove they can see this screen. */
data class LinkPairCode(
    val code: String,
    val expiresAtElapsedMs: Long,
)

/** Credential and pairing state owned by the backend. PENDING may only show a pair code. */
data class LinkState(
    val active: Boolean,
    val bound: Boolean,
    val pairCode: LinkPairCode?,
)

/** Per-device broker credentials; never use a shared account and never persist these. */
data class MqttCredentials(
    val username: String,
    val password: String,
)

/**
 * Server-computed quiz reward. The TV sends only the score; the backend recomputes
 * the minutes from the config it owns and dispatches the trusted start_session.
 */
data class QuizSettlement(
    val earnedMinutes: Int,
    val alreadyGranted: Boolean,
)

/**
 * The TV side of the guard-device protocol.
 *
 * The stable deviceNo is a public identifier and the value the parent scans. On a fresh
 * install the TV exchanges it for a v2 deviceToken and stores that secret in product storage;
 * every authenticated request then uses the token. There is no public-deviceNo bearer fallback:
 * without an ACTIVE v2 credential the cloud path stays closed.
 */
/**
 * v2 reports a revoked credential two ways: interceptor-protected endpoints use HTTP 401,
 * while `/enroll/status` returns HTTP 200 with business code 401. Treat either as lost.
 */
internal fun isCredentialUnauthorized(httpCode: Int, apiCode: Int): Boolean =
    httpCode == 401 || apiCode == 401

object PolicyCloud : PolicySyncHost {
    private const val TAG = "PolicyCloud"
    private const val BINDING_CHECK_MS = 60_000L
    private const val STATUS_PENDING = "PENDING"
    private const val STATUS_ACTIVE = "ACTIVE"
    const val DEVICE_TYPE = "TV_APP"
    private const val TIMEOUT_MS = 5_000
    /** The settlement dispatch waits up to 12 s for the TV ACK before it answers. */
    private const val SETTLE_TIMEOUT_MS = 25_000
    private const val ENROLL_RETRY_MS = 10_000L
    @Volatile
    private var appContext: Context? = null
    /** Only emit a grant trace when the backend snapshot actually changed. */
    private val lastGrantTrace = AtomicReference("")
    private val watchStarted = AtomicBoolean(false)
    private val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enrollMutex = Mutex()
    @Volatile
    private var enrollNextAttemptAt = 0L

    /** Installs the HTTP host and the MQTT command listener. */
    fun install(context: Context) {
        appContext = context.applicationContext
        PolicyRuntime.attachSyncHost(this)
        PolicyMqttClient.install(context.applicationContext)
        startBindingWatch()
    }

    /**
     * MQTT is the immediate path; this poll covers a command the TV never received. Only a
     * confirmed unbound while locally bound changes the mode. Network errors leave it alone.
     */
    private fun startBindingWatch() {
        if (!watchStarted.compareAndSet(false, true)) return
        watchScope.launch {
            combine(
                PolicyRuntime.cloudActive,
                PolicyModeCoordinator.state.map { it.mode }.distinctUntilChanged(),
            ) { active, mode -> active && mode == PolicyMode.CLOUD }
                .distinctUntilChanged()
                .collectLatest { shouldWatch ->
                    if (!shouldWatch) return@collectLatest
                    while (isActive && PolicyRuntime.cloudActive.value &&
                        PolicyModeCoordinator.mode == PolicyMode.CLOUD
                    ) {
                        delay(BINDING_CHECK_MS)
                        val state = PolicyRuntime.state.value
                        if (!state.bound || state.deviceNo.isBlank()) continue
                        val result = checkBinding(state.deviceNo)
                        if (result.isSuccess && !result.getOrThrow()) {
                            PolicyModeCoordinator.onCloudUnboundFromBackend()
                            return@collectLatest
                        }
                        if (result.isFailure) {
                            Log.w(TAG, "binding poll failed; keeping the current mode", result.exceptionOrNull())
                        }
                    }
                }
        }
    }

    private val apiBase: String
        get() {
            val raw = BuildConfig.TV_API_BASE.trimEnd('/')
            // Accept both the origin and the full /api/v1 base; the paths below are relative.
            return if (raw.endsWith("/api/v1")) raw else "$raw/api/v1"
        }

    /** Enrollment lives under /api/v2 while the remaining device endpoints stay /api/v1. */
    private val linkBase: String
        get() {
            val raw = BuildConfig.TV_API_BASE.trimEnd('/')
            val origin = when {
                raw.endsWith("/api/v1") -> raw.dropLast("/api/v1".length)
                raw.endsWith("/api/v2") -> raw.dropLast("/api/v2".length)
                else -> raw
            }
            return "$origin/api/v2"
        }

    /**
     * Registers or refreshes this TV. A fresh device gets a PENDING credential and the pair code
     * the parent must type; an ACTIVE device also reports its metadata. There is no v1 fallback:
     * if enrollment cannot produce a usable credential, binding stops here.
     */
    suspend fun register(deviceNo: String): Result<LinkState> = withContext(Dispatchers.IO) {
        runCatching {
            require(deviceNo.isNotBlank()) { "deviceNo is empty" }
            val link = refreshLinkState(deviceNo).getOrThrow()
            if (!link.active) return@runCatching link
            reportDeviceInfo(deviceNo).getOrThrow()
            link
        }
    }

    /**
     * Uploads device_info. The backend only accepts this with an ACTIVE credential, so it runs
     * after binding. The first report after binding also tells the backend to start the
     * local-rules -> cloud handoff, which is why it must precede fetchConfig.
     */
    private suspend fun reportDeviceInfo(deviceNo: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(deviceNo.isNotBlank()) { "deviceNo is empty" }
                val body = JSONObject()
                    .put("deviceNo", deviceNo)
                    .put("macAddress", macFor(deviceNo))
                    .put("deviceType", DEVICE_TYPE)
                    .put("model", Build.MODEL.orEmpty())
                    .put("osVersion", Build.VERSION.RELEASE.orEmpty())
                    .put("appVersion", BuildConfig.VERSION_NAME)
                    .put("brandClean", Build.BRAND.orEmpty().lowercase(Locale.ROOT))
                    .put("androidApi", Build.VERSION.SDK_INT)
                request("POST", "/devices/report/info", authToken(), body = body)
                Unit
            }
        }

    /**
     * Refreshes the backend-owned credential/pairing state. A stored token that the backend
     * rejects is kept and sent as `previousToken`: only the original installation can re-enroll
     * a REVOKED credential, and an ACTIVE credential cannot be enrolled again at all.
     */
    suspend fun refreshLinkState(deviceNo: String): Result<LinkState> = withContext(Dispatchers.IO) {
        enrollMutex.withLock {
            if (deviceNo.isBlank()) {
                return@withLock Result.failure(IOException("deviceNo is empty"))
            }
            val token = PolicyRuntime.deviceToken()
            if (token.isBlank()) return@withLock enroll(deviceNo, null)
            val state = queryEnrollmentStatus(deviceNo, token)
            val error = state.exceptionOrNull()
            if (error.isUnauthorized()) {
                Log.w(TAG, "stored device credential was revoked; re-enrolling with previous token")
                return@withLock enroll(deviceNo, token)
            }
            state
        }
    }

    private suspend fun enroll(deviceNo: String, previousToken: String?): Result<LinkState> {
        if (SystemClock.elapsedRealtime() < enrollNextAttemptAt) {
            return Result.failure(IOException("device credential enrollment is cooling down"))
        }
        return try {
            val body = JSONObject().put("deviceNo", deviceNo)
            if (!previousToken.isNullOrBlank()) body.put("previousToken", previousToken)
            val json = requestRaw(
                method = "POST",
                path = "/devices/enroll",
                token = null,
                bodyText = body.toString(),
                base = linkBase,
            )
            val data = json.optJSONObject("result")
                ?: throw IOException("enroll response missing result")
            val returnedDeviceNo = data.optString("deviceNo", "").trim()
            val token = data.optString("deviceToken", "").trim()
            val status = data.optString("status", "").trim()
            if (returnedDeviceNo != deviceNo) {
                throw IOException("enroll returned a different deviceNo")
            }
            if (status != STATUS_PENDING && status != STATUS_ACTIVE) {
                throw IOException("enroll returned an invalid status")
            }
            if (!PolicyRuntime.storeDeviceToken(token)) {
                throw IOException("device credential could not be stored")
            }
            val now = SystemClock.elapsedRealtime()
            Log.i(TAG, "link v2 credential enrolled: status=$status")
            Result.success(
                LinkState(
                    active = status == STATUS_ACTIVE,
                    bound = status == STATUS_ACTIVE,
                    pairCode = parsePairCode(data, now),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            enrollNextAttemptAt = SystemClock.elapsedRealtime() + ENROLL_RETRY_MS
            Log.w(TAG, "link v2 enrollment failed", error)
            Result.failure(error)
        }
    }

    /**
     * PENDING and ACTIVE are both accepted by `/enroll/status`; the token is never changed here.
     * A 401 is returned as [ApiException] so the caller can decide whether to re-enroll.
     */
    private fun queryEnrollmentStatus(deviceNo: String, token: String): Result<LinkState> {
        return try {
            val json = requestRaw(
                method = "POST",
                path = "/devices/enroll/status",
                token = token,
                bodyText = "{}",
                base = linkBase,
            )
            val data = json.optJSONObject("result")
                ?: throw IOException("enroll status response missing result")
            val returnedDeviceNo = data.optString("deviceNo", "").trim()
            val status = data.optString("status", "").trim()
            if (returnedDeviceNo != deviceNo) {
                throw IOException("enroll status returned a different deviceNo")
            }
            if (status != STATUS_PENDING && status != STATUS_ACTIVE) {
                throw IOException("enroll status returned an invalid status")
            }
            val now = SystemClock.elapsedRealtime()
            Result.success(
                LinkState(
                    active = status == STATUS_ACTIVE,
                    bound = data.optBoolean("bound", false),
                    pairCode = if (status == STATUS_PENDING) parsePairCode(data, now) else null,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /** The backend returns the pair code under different field names in enroll/status. */
    private fun parsePairCode(data: JSONObject, nowElapsedMs: Long): LinkPairCode? {
        val code = data.optString("pairCode", "").trim()
        val expiresIn = data.optLong(
            "pairCodeExpiresInSeconds",
            data.optLong("expiresInSeconds", 0L),
        )
        if (!code.matches(Regex("^\\d{6}$")) || expiresIn <= 0L) return null
        return LinkPairCode(code, nowElapsedMs + expiresIn * 1_000L)
    }

    /**
     * QR payload understood by the uniapp parent app. The pair code is not a secret on its own,
     * but including it makes the scan one gesture and still requires the logged-in parent.
     * Keep the uxb scheme: the released parent app only parses uxb://bind, and changing it
     * would break every TV/parent pair until the app ships an update.
     */
    fun bindingUri(deviceNo: String, pairCode: String): String {
        val encoded = URLEncoder.encode(deviceNo, Charsets.UTF_8.name())
        return "uxb://bind?deviceNo=$encoded&pairCode=$pairCode&v=2"
    }

    /** Business endpoints require the stored secret; a missing token is a hard failure. */
    private fun authToken(): String =
        PolicyRuntime.deviceToken().ifBlank { throw IOException("device credential missing") }

    /**
     * Binding watch and unbind checks. `/enroll/status` answers for both PENDING and ACTIVE, so
     * this does not mistake "waiting for the parent" for a network failure. A 401 means the
     * credential was revoked; only then is "not bound" returned without a re-enroll side effect.
     */
    suspend fun checkBinding(deviceNo: String): Result<Boolean> = withContext(Dispatchers.IO) {
        enrollMutex.withLock {
            val token = PolicyRuntime.deviceToken()
            if (token.isBlank()) return@withLock Result.success(false)
            val state = queryEnrollmentStatus(deviceNo, token)
            state.fold(
                onSuccess = { Result.success(it.bound) },
                onFailure = { error ->
                    if (error.isUnauthorized()) {
                        Result.success(false)
                    } else {
                        Result.failure(error)
                    }
                },
            )
        }
    }

    /**
     * Fetches the backend's MQTT command-signing public key over the authenticated v2 channel.
     *
     * Signing is mandatory on the hard-cut path. A missing endpoint, a disabled signer, or a
     * malformed key is a failure: the MQTT client must not subscribe and must not accept
     * unsigned commands as a fallback.
     */
    suspend fun refreshCommandKey(deviceNo: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (deviceNo.isBlank()) throw IOException("deviceNo is empty")
            val token = PolicyRuntime.deviceToken()
            if (token.isBlank()) throw IOException("device credential missing")
            val json = requestRaw(
                method = "GET",
                path = "/devices/current/command-key",
                token = token,
                base = linkBase,
            )
            val data = json.optJSONObject("result")
                ?: throw IOException("command-key response missing result")
            if (!data.optBoolean("enabled", false)) {
                throw IOException("command signing is disabled on the backend")
            }
            val algorithm = data.optString("algorithm", "").trim()
            val keyId = data.optString("keyId", "").trim()
            val publicKey = data.optString("publicKey", "").trim()
            if (algorithm != "SHA256withECDSA") {
                throw IOException("unsupported command signing algorithm")
            }
            if (!PolicyRuntime.storeCommandSigning(true, keyId, publicKey)) {
                throw IOException("command signing key could not be stored")
            }
            Log.i(TAG, "command signing key updated")
            true
        }
    }

    /**
     * Fetches the per-device broker account. The username must equal the public deviceNo; a
     * shared account or any other username is rejected rather than used.
     */
    suspend fun fetchMqttCredentials(deviceNo: String): Result<MqttCredentials> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (deviceNo.isBlank()) throw IOException("deviceNo is empty")
                val token = PolicyRuntime.deviceToken()
                if (token.isBlank()) throw IOException("device credential missing")
                val json = requestRaw(
                    method = "POST",
                    path = "/devices/current/mqtt-credentials",
                    token = token,
                    bodyText = "{}",
                    base = linkBase,
                )
                val data = json.optJSONObject("result")
                    ?: throw IOException("mqtt-credentials response missing result")
                val username = data.optString("username", "").trim()
                val password = data.optString("password", "").trim()
                if (username != deviceNo || !password.startsWith("gm_") || password.length < 24) {
                    throw IOException("invalid mqtt credentials")
                }
                MqttCredentials(username, password)
            }
        }

    /** The parent list maps guardStatus=1 to the green "守护中" state. */
    suspend fun reportGuardStatus(deviceNo: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("guardStatus", 1)
            request("POST", "/devices/report/guard-status", authToken(), body)
            Unit
        }
    }

    /**
     * Reports the locally selected NONE/LOCAL/CLOUD mode. Only called for a bound TV; an
     * unbound device has no owner to report to. The backend ignores stale versions, so a
     * retry cannot move the cloud state back from LOCAL to CLOUD.
     */
    suspend fun reportPolicyMode(
        deviceNo: String,
        mode: PolicyMode,
        version: Long,
        bound: Boolean,
        sessionState: String,
        supportsUnifiedPolicy: Boolean? = null,
        appliedMode: RemotePolicyMode? = null,
        appliedConfigVersion: Long? = null,
        policyEpoch: Long? = null,
        supportsQuizConfig: Boolean? = null,
        supportsCloudQuiz: Boolean? = null,
        appliedQuizVersion: Long? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("policyMode", mode.name)
                .put("modeVersion", version)
                .put("bound", bound)
                .put("sessionState", sessionState)
                .put("timestamp", System.currentTimeMillis())
            // Never send a default capability here: an old build must not overwrite the
            // backend's handoff/capability state with a guess.
            if (supportsUnifiedPolicy != null) {
                body.put("supportsUnifiedPolicy", supportsUnifiedPolicy)
            }
            if (appliedMode != null) body.put("appliedMode", appliedMode.name)
            if (appliedConfigVersion != null) {
                body.put("appliedConfigVersion", appliedConfigVersion)
            }
            // Remote start/activation commands carry this epoch; without a fresh report the
            // backend rejects them rather than guessing which local policy generation is live.
            if (policyEpoch != null) body.put("policyEpoch", policyEpoch)
            if (appliedQuizVersion != null) {
                body.put("appliedQuizVersion", appliedQuizVersion)
            }
            if (supportsQuizConfig != null || supportsCloudQuiz != null) {
                val capabilities = JSONObject()
                if (supportsQuizConfig != null) {
                    capabilities.put("supportsQuizConfig", supportsQuizConfig)
                }
                if (supportsCloudQuiz != null) {
                    capabilities.put("supportsCloudQuiz", supportsCloudQuiz)
                }
                body.put("capabilities", capabilities)
            }
            request("POST", "/devices/report/policy-mode", authToken(), body)
            Unit
        }
    }

    /**
     * Uploads the current local rules once, turning the binding into a completed V6 handoff.
     * The password uses the existing password endpoint; this call only carries LocalRules.
     */
    suspend fun handoff(
        deviceNo: String,
        rules: LocalRules,
        activation: Activation,
        /** Only sent to a backend that advertises two-set support; old backends seed both. */
        ruleSets: LocalRuleSets? = null,
        quiz: JSONObject? = null,
    ): Result<RemotePolicy?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("rules", RemotePolicyCodec.rulesToJson(rules))
                    .put("activation", activation.name)
                ruleSets?.let { sets ->
                    body.put("autoRules", RemotePolicyCodec.ruleSetToJson(sets.auto))
                    body.put("manualRules", RemotePolicyCodec.ruleSetToJson(sets.manual))
                }
                // The same snapshot as the binding report; the backend merges by localVersion.
                if (quiz != null) body.put("quiz", quiz)
                val result = request("POST", "/devices/current/policy-handoff", authToken(), body)
                    .optJSONObject("result")
                RemotePolicyCodec.parse(result)
            }
        }

    /** Full replacement of this TV's launchable-app inventory. */
    suspend fun uploadApps(deviceNo: String, apps: List<PolicyAppUpload>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val array = JSONArray()
                apps.forEach { app ->
                    array.put(
                        JSONObject()
                            .put("appName", app.appName)
                            .put("packageName", app.packageName)
                            .put("appIcon", app.appIcon.orEmpty()),
                    )
                }
                val body = JSONObject().put("deviceId", deviceNo).put("apps", array)
                request("PUT", "/devices/current/apps", authToken(), body)
                Unit
            }
        }

    /** Returns the subset of hashes the backend does not have yet. */
    suspend fun missingIcons(deviceNo: String, hashes: List<String>): Result<Set<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (hashes.isEmpty()) return@runCatching emptySet()
                val body = JSONArray().apply { hashes.forEach { put(it) } }
                val json = requestRaw("POST", "/icons/check", authToken(), body.toString())
                val result = json.optJSONArray("result") ?: JSONArray()
                buildSet {
                    for (i in 0 until result.length()) {
                        result.optString(i, "").takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
        }

    /** Uploads one PNG icon; the backend keys it by its content hash. */
    suspend fun uploadIcon(deviceNo: String, hash: String, base64Data: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("iconHash", hash)
                    .put("base64Data", base64Data)
                request("POST", "/icons/upload", authToken(), body)
                Unit
            }
        }

    /**
     * Phase 1 reads this in PolicyRuntime. For now it proves the downlink after
     * binding: recordId/status arrive from the same deviceNo.
     */
    suspend fun fetchConfigJson(deviceNo: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val json = request("GET", "/devices/current/config", authToken())
            json.optJSONObject("result") ?: JSONObject()
        }
    }

    /**
     * PolicySyncHost: the adapter owns JSON and turns it into the pure snapshot. The V6 policy
     * node is parsed even when there is no active redemption grant.
     */
    override suspend fun fetchConfig(deviceNo: String): Result<PolicySyncSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                // HTTP 200 with no result is not "no grant": treating it as one would stop a
                // running remote session. Keep the last valid snapshot and let the poll retry.
                val result = request("GET", "/devices/current/config", authToken())
                    .optJSONObject("result")
                    ?: throw IOException("config snapshot missing result")
                // The config poll doubles as the password self-heal channel: a fingerprint
                // changes only when the cloud PIN changes, so no extra request is made while
                // it is stable. MQTT remains the immediate path.
                val passwordVersion = result.optString("passwordVersion", "").trim()
                    .takeIf { it.isNotEmpty() }
                if (passwordVersion != null &&
                    passwordVersion != PolicyRuntime.cloudPinVersion() &&
                    !PolicyRuntime.isPasswordSyncPending()
                ) {
                    syncPassword(deviceNo, passwordVersion)
                        .onFailure { Log.w(TAG, "password sync from config failed", it) }
                }
                val grant = parseGrant(result)
                if (PolicyFlowTrace.enabled()) {
                    val rawRecordId = result.optInt("recordId", 0)
                    val rawStatus = result.optInt("status", 0)
                    val rawTotal = result.optLong("totalOrderTime", 0L)
                    val rawCost = result.optLong("costTime", 0L)
                    // Polls run every few seconds while a grant is pending. Trace the snapshot
                    // only when its grant fields change, so one redemption is one readable line.
                    val grantTrace = listOf(
                        rawRecordId,
                        rawStatus,
                        rawTotal,
                        rawCost,
                        grant?.validWindowSeconds,
                        grant?.accessMode,
                        grant?.allowedPackages?.size,
                        grant?.userName?.isNotEmpty(),
                        grant?.userIconUrl?.isNotEmpty(),
                    ).joinToString("|")
                    if (lastGrantTrace.getAndSet(grantTrace) != grantTrace) {
                        PolicyFlowTrace.event(
                            "config.grant",
                            "recordId" to rawRecordId,
                            "status" to rawStatus,
                            "total" to rawTotal,
                            "cost" to rawCost,
                            "remaining" to grant?.remainingSeconds,
                            "window" to grant?.validWindowSeconds,
                            "access" to grant?.accessMode,
                            "pkgs" to grant?.allowedPackages?.size,
                            "user" to (grant?.userName?.isNotEmpty() == true),
                            "icon" to (grant?.userIconUrl?.isNotEmpty() == true),
                        )
                    }
                }
                val snapshot = PolicySyncSnapshot(
                    grant = grant,
                    remotePolicy = RemotePolicyCodec.parse(result.optJSONObject("policy")),
                    quiz = RemoteQuizConfig.parse(result.optJSONObject("quiz")),
                )
                // The periodic policy poll and the MQTT-triggered sync both go through this
                // fetch. Apply the quiz node here so a remote switch reaches the lock screen
                // without reopening the local quiz page; callers that apply it again are
                // idempotent (applyRemote ignores equal versions).
                QuizConfigSync.applyRemote(snapshot.quiz)
                snapshot
            }.onFailure {
                if (PolicyFlowTrace.enabled()) {
                    PolicyFlowTrace.event(
                        "config.fetch.fail",
                        "error" to it.javaClass.simpleName,
                    )
                }
            }
        }

    /**
     * Binding/retry upload. The backend deduplicates by localVersion, so a lost response can be
     * retried safely. A CLOUD result means the parent already owns this config; the TV must take
     * that response rather than overwrite it.
     */
    suspend fun reportQuizConfig(
        deviceNo: String,
        quiz: JSONObject,
    ): Result<RemoteQuizConfig?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("quiz", quiz)
            val result = request("POST", "/devices/current/quiz-config/report", authToken(), body)
                .optJSONObject("result")
                ?: throw IOException("quiz config report returned no result")
            RemoteQuizConfig.parse(result)
                ?: throw IOException("quiz config report returned an invalid config")
        }
    }

    /** Local parent edit upload; baseVersion is the configVersion this edit was made against. */
    suspend fun updateQuizConfig(
        deviceNo: String,
        config: JSONObject,
        localVersion: Long,
        baseVersion: Long,
    ): Result<RemoteQuizConfig?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("config", config)
                .put("localVersion", localVersion)
                .put("baseVersion", baseVersion)
            val result = request("POST", "/devices/current/quiz-config", authToken(), body)
                .optJSONObject("result")
                ?: throw IOException("quiz config update returned no result")
            RemoteQuizConfig.parse(result)
                ?: throw IOException("quiz config update returned an invalid config")
        }
    }

    /**
     * Hosted quiz settlement: the TV reports the score and the config version the
     * round started with, and the backend recomputes the minutes. The trusted
     * start_session is dispatched while this call waits for the TV ACK, so the
     * read timeout must outlast the backend's 12 s command wait.
     */
    suspend fun settleQuizEarned(
        deviceNo: String,
        sessionId: String,
        totalCount: Int,
        correctCount: Int,
        configVersion: Long,
    ): Result<QuizSettlement> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("sessionId", sessionId)
                .put("totalCount", totalCount)
                .put("correctCount", correctCount)
                .put("configVersion", configVersion)
            val result = request(
                "POST",
                "/devices/current/quiz/earned-session",
                authToken(),
                body,
                readTimeoutMs = SETTLE_TIMEOUT_MS,
                // OWASP CRS 943120 treats a JSON field named sessionId with no
                // Referer as a session-fixation attempt. This is a native API
                // client, not a browser session, so send the API origin to keep
                // the preview WAF from rejecting the settlement with a bare 403.
                referer = apiBase,
            ).optJSONObject("result")
                ?: throw IOException("quiz settlement returned no result")
            QuizSettlement(
                earnedMinutes = result.optInt("earnedMinutes", 0),
                alreadyGranted = result.optBoolean("alreadyGranted", false),
            )
        }
    }

    /**
     * Business rejections (HTTP 200 with a non-200 API code) are retried only when the caller
     * decides they are transient. Transport failures must never be mistaken for a version
     * conflict, so keep the distinction available to [QuizConfigSync].
     */
    fun isServerRejection(error: Throwable): Boolean = error is ApiException

    /**
     * The backend's user-facing rejection text for a business failure. Returns null
     * for transport/HTTP failures so the UI can show its own neutral message
     * instead of leaking infrastructure details.
     */
    fun businessMessage(error: Throwable): String? = (error as? ApiException)?.businessMessage

    /**
     * 409 = the same settlement is already in flight. That is a wait-and-retry,
     * not a reason to mark the round permanently failed.
     */
    fun isRetryableSettlement(error: Throwable): Boolean =
        (error as? ApiException)?.apiCode == 409

    /**
     * A short, safe description of a failure for diagnostics: status codes and the
     * server's message are enough to tell a 401 from a 5xx without logging bodies,
     * which may contain tokens. R8 renames exception classes, so the class name
     * alone is useless in release builds.
     */
    fun errorDetail(error: Throwable): String {
        val cause = error.cause?.let { " cause=${it.message.orEmpty().take(80)}" }.orEmpty()
        return if (error is ApiException) {
            "http=${error.httpCode} api=${error.apiCode} msg=${error.message.orEmpty().take(120)}$cause"
        } else {
            "${error.javaClass.name}: ${error.message.orEmpty().take(120)}$cause"
        }
    }

    private fun parseGrant(result: JSONObject): PolicyConfig? {
        // The grant poll is also the stop fallback, so absence must be explicit. A truncated
        // 200 body must fail the fetch, not look like a terminal record that ends the session.
        if (!result.has("recordId") || !result.has("status")) {
            throw IOException("config snapshot missing recordId/status")
        }
        val recordId = result.optInt("recordId", -1)
        val status = result.optInt("status", 0)
        val total = result.optLong("totalOrderTime", 0L)
        val cost = result.optLong("costTime", 0L)
        val remaining = (total - cost).coerceAtLeast(0L)
        if (recordId > 0 && status == 1 &&
            (!result.has("totalOrderTime") || !result.has("costTime"))
        ) {
            throw IOException("active grant missing duration fields")
        }
        if (recordId <= 0 || status != 1 || remaining <= 0) return null
        val (windowSeconds, validUntil) = windowInfo(
            result.optString("validEndTime", ""),
            result.optString("currentTime", ""),
        )
        return PolicyConfig(
            recordId = recordId,
            totalSeconds = total,
            costSeconds = cost,
            status = status,
            userName = result.optString("userName", ""),
            userIconUrl = result.optString("userIcon", ""),
            validWindowSeconds = windowSeconds,
            validUntilLabel = validUntil,
            accessMode = parseAccessMode(result.optString("accessMode", "")),
            allowedPackages = parsePackages(result),
        )
    }

    override suspend fun reportUsage(
        deviceNo: String,
        recordId: Int,
        durationSeconds: Long,
        timeout: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("recordId", recordId)
                .put("duration", durationSeconds)
                .put("packageName", DEVICE_TYPE)
                .put("isTimeout", timeout)
            request("POST", "/usage-records", authToken(), body)
            Unit
        }
    }

    /**
     * V6 rule usage batch. Statistics only: a failure never blocks the local rule engine, and
     * the caller keeps the events queued for retry.
     */
    suspend fun reportRuleUsage(
        deviceNo: String,
        events: List<RuleUsageEvent>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (events.isEmpty()) return@runCatching Unit
            val body = JSONObject().put("events", RuleUsageCodec.toJsonArray(events))
            request("POST", "/devices/current/rule-usage", authToken(), body)
            Unit
        }
    }

    /** Reads the per-device admin/unlock password. The MQTT command carries no plaintext. */
    suspend fun fetchPassword(deviceNo: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val json = request("GET", "/devices/current/password", authToken())
            json.optString("result", "").trim().ifEmpty { null }
        }
    }

    /** TV parent-center -> cloud password sync. Uses the device token, never a user token. */
    suspend fun updatePassword(deviceNo: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                request(
                    "PUT",
                    "/devices/current/password",
                    authToken(),
                    JSONObject().put("password", password),
                )
                Unit
            }
        }

    /**
     * Applies the cloud password to the local PIN stores. If a local change is waiting for a
     * one-time sync, keep the local value; the parent center asks for the current PIN instead.
     * [force] is used for an explicit parent action (MQTT modify_password), which supersedes a
     * pending offline local change. [passwordVersion] is stored only after the cloud value has
     * been applied or already matches, so a failed sync is retried by the next config poll.
     */
    suspend fun syncPassword(
        deviceNo: String,
        passwordVersion: String? = null,
        force: Boolean = false,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!force && PolicyRuntime.isPasswordSyncPending()) {
            Log.i(TAG, "password sync pending; keeping local PIN")
            return@withContext Result.success(false)
        }
        fetchPassword(deviceNo).map { password ->
            if (password != null) {
                when (PolicyRuntime.applyCloudPin(password)) {
                    CloudPinApplyResult.APPLIED,
                    CloudPinApplyResult.UNCHANGED,
                    -> {
                        if (!passwordVersion.isNullOrBlank()) {
                            PolicyRuntime.setCloudPinVersion(passwordVersion)
                        }
                        if (force) PolicyRuntime.clearPasswordSyncPending()
                    }
                    // Legacy default before handoff: remember the fingerprint to avoid
                    // refetching it every poll. The handoff upload changes the fingerprint.
                    CloudPinApplyResult.IGNORED -> {
                        if (!passwordVersion.isNullOrBlank()) {
                            PolicyRuntime.setCloudPinVersion(passwordVersion)
                        }
                    }
                    CloudPinApplyResult.INVALID,
                    CloudPinApplyResult.NOT_READY,
                    -> Unit
                }
            }
            true
        }
    }

    /**
     * Called once per process start. A cloud binding is authoritative: an unbound TV returns to
     * NONE, while a still-bound TV keeps CLOUD. A network failure leaves the current mode alone.
     */
    suspend fun syncOnStartup() {
        if (PolicyModeCoordinator.mode != PolicyMode.CLOUD) return
        val state = PolicyRuntime.state.value
        val deviceNo = state.deviceNo
        if (deviceNo.isBlank()) return
        if (!state.bound) {
            // CLOUD without a local binding is a pending binding screen, not an unbind. Keep
            // the screen alive; the binding poll decides when it completes.
            return
        }
        checkBinding(deviceNo)
            .onSuccess { bound ->
                if (!bound) {
                    PolicyModeCoordinator.onCloudUnboundFromBackend()
                } else {
                    // Also repairs devices bound before the first report reached the backend:
                    // the report gives the backend the TV_APP device_info it needs to seed the
                    // local-rules handoff instead of the legacy redeem configuration.
                    reportDeviceInfo(deviceNo)
                        .onFailure { Log.w(TAG, "startup device info report failed", it) }
                    syncPassword(deviceNo)
                        .onFailure { Log.w(TAG, "startup password sync failed", it) }
                }
            }
            .onFailure { Log.w(TAG, "startup binding check failed", it) }
    }

    /**
     * Called by the binding screen after the parent app binds this deviceNo. The cloud guard
     * becomes RUNNING immediately; the first config check decides between PENDING/ACTIVE and
     * the "no grant yet" mini-program guide. The follow-up writes must finish even if the
     * binding screen leaves composition.
     */
    suspend fun onBound(deviceNo: String) {
        // Binding success is the mode switch. This also makes Back on the binding screen unable
        // to drop a confirmed TV back to NONE.
        Log.i(TAG, "binding confirmed by backend; switching to CLOUD")
        PolicyModeCoordinator.confirmCloudBinding()
        withContext(NonCancellable) {
            // A new binding is a new family: drop any previous family's CLOUD quiz source
            // before the backend resets its row. The local challenge config is the payload.
            QuizConfigSync.onBindingConfirmed(deviceNo)
            // Must precede reportGuardStatus/fetchConfig: this is what creates device_info for a
            // TV that was bound before it ever reported, and the backend seeds the PENDING
            // local-rules handoff on this first report.
            reportDeviceInfo(deviceNo)
                .onFailure { Log.w(TAG, "device info report after bind failed", it) }
            reportGuardStatus(deviceNo)
                .onFailure { Log.w(TAG, "guard status report failed", it) }
            // Upload the local quiz config before fetching: the cloud row is reset on binding,
            // and a failed report is retried in the background without blocking this screen.
            QuizConfigSync.reportBinding(deviceNo)
            fetchConfig(deviceNo)
                .onSuccess { snapshot ->
                    Log.i(
                        TAG,
                        "config after bind: recordId=${snapshot.grant?.recordId ?: 0} " +
                            "status=${snapshot.grant?.status ?: 0} " +
                            "mode=${snapshot.remotePolicy?.mode} " +
                            "handoff=${snapshot.remotePolicy?.handoffState} " +
                            "quizVersion=${snapshot.quiz?.version ?: 0}",
                    )
                    PolicyRuntime.onRemotePolicy(snapshot.remotePolicy)
                    PolicyRuntime.onCloudConfig(snapshot.grant)
                    QuizConfigSync.applyRemote(snapshot.quiz)
                }
                .onFailure {
                    Log.w(TAG, "config fetch failed", it)
                    // Keep the TV guarded and show the mini-program guide rather than Home.
                    PolicyRuntime.onCloudConfig(null)
                }
            syncPassword(deviceNo).onFailure {
                Log.w(TAG, "password sync after bind failed", it)
            }
        }
    }

    /** device_info.mac_address is not a real MAC on a TV app; it only needs to be unique/stable. */
    private fun macFor(deviceNo: String): String = "tv_app-$deviceNo"

    private fun parseAccessMode(raw: String): AccessMode =
        if (raw.equals("APPS", ignoreCase = true)) AccessMode.APPS else AccessMode.SCREEN

    private fun parsePackages(result: JSONObject): Set<String> {
        val out = linkedSetOf<String>()
        val list = result.optJSONArray("packageNameList")
        if (list != null) {
            for (i in 0 until list.length()) {
                list.optString(i, "").takeIf { it.isNotBlank() }?.let { out += it }
            }
            return out
        }
        val raw = result.optString("packageName", "")
        if (raw.isBlank()) return out
        if (raw.trim().startsWith("[")) {
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    array.optString(i, "").takeIf { it.isNotBlank() }?.let { out += it }
                }
            }.onFailure { Log.w(TAG, "packageNameList parse failed", it) }
        } else {
            raw.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { out += it }
        }
        return out
    }

    /**
     * The backend returns validEndTime and currentTime in server-local time. Only the delta
     * matters, so parse the clock part and count this window from the fetch instant. A
     * negative delta is treated as expired rather than adding a day: the backend's own
     * lazy settlement is the authority, and a just-expired record must not gain 24 hours.
     */
    private fun windowInfo(validEndTime: String, currentTime: String): Pair<Long?, String> {
        val label = endDisplay(validEndTime)
        val end = clockSeconds(validEndTime) ?: return null to label
        val now = clockSeconds(currentTime) ?: return null to label
        return (end - now).coerceAtLeast(0L) to label
    }

    /**
     * The phone app's format: "最晚今天HH:mm结束" / "最晚明天HH:mm结束" /
     * "最晚<date> HH:mm结束". Kept in the adapter because it needs app resources.
     */
    private fun endDisplay(raw: String): String {
        val text = raw.trim()
        val fallback = text.substringAfter(' ', text).take(5)
        val context = appContext ?: return fallback
        val clean = text.substringBefore('.')
        if (clean.isBlank()) return fallback
        val end = if (clean.contains('-')) {
            runCatching { LocalDateTime.parse(clean.replace(' ', 'T')) }.getOrNull()
        } else {
            runCatching { LocalDateTime.of(LocalDate.now(), LocalTime.parse(clean)) }.getOrNull()
        } ?: return fallback
        val time = String.format(Locale.getDefault(), "%02d:%02d", end.hour, end.minute)
        val today = LocalDate.now()
        return when (end.toLocalDate()) {
            today -> context.getString(R.string.policy_end_today, time)
            today.plusDays(1) -> context.getString(R.string.policy_end_tomorrow, time)
            else -> {
                val date = end.toLocalDate().format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(Locale.getDefault()),
                )
                context.getString(R.string.policy_end_date, "$date $time")
            }
        }
    }

    private fun clockSeconds(raw: String): Long? {
        val text = raw.substringAfter(' ', raw).trim()
        val parts = text.split(':')
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        val second = parts.getOrNull(2)?.substringBefore('.')?.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
        return hour.toLong() * 3600 + minute * 60 + second
    }

    private fun request(
        method: String,
        path: String,
        token: String?,
        body: JSONObject? = null,
        readTimeoutMs: Int = TIMEOUT_MS,
        referer: String? = null,
    ): JSONObject = requestRaw(
        method,
        path,
        token,
        body?.toString(),
        readTimeoutMs = readTimeoutMs,
        referer = referer,
    )

    /** Same request path, for endpoints that take a JSON array (icons/check). */
    private fun requestRaw(
        method: String,
        path: String,
        token: String?,
        bodyText: String? = null,
        base: String = apiBase,
        readTimeoutMs: Int = TIMEOUT_MS,
        referer: String? = null,
    ): JSONObject {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (referer != null) setRequestProperty("Referer", referer)
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
            // Never put the raw body in an exception: a malformed enroll/pair-code response can
            // contain the device token or pair code, and these exceptions reach Logcat and the UI.
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
                throw ApiException(
                    httpCode,
                    apiCode,
                    if (message.isEmpty()) "HTTP $httpCode" else "HTTP $httpCode: $message",
                )
            }
            if (json.length() == 0) {
                throw IOException("invalid server response (HTTP $httpCode)")
            }
            val code = json.optInt("code", 200)
            if (code != 200) {
                val message = json.optString("message", "").trim().take(160)
                throw ApiException(
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

    /**
     * Both signals mean "this installation lost its credential" and must drive re-enroll or
     * unbind, not a retry-forever failure. See [isCredentialUnauthorized].
     */
    private fun Throwable?.isUnauthorized(): Boolean =
        this is ApiException && isCredentialUnauthorized(httpCode, apiCode)

    /** The business code matters to callers: 409 means "already enrolled/bound", not a failure. */
    private class ApiException(
        val httpCode: Int,
        val apiCode: Int,
        message: String,
        /** Only set for an HTTP 200 response carrying a business code: the backend's
         *  user-facing rejection text, already free of server internals. */
        val businessMessage: String? = null,
    ) : IOException(message)
}
