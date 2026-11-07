package app.orchard.tv.policy

import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.orchard.tv.BuildConfig
import com.planyourtv.policy.Activation
import com.planyourtv.policy.PolicyFlowTrace
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.PolicyVoiceBus
import com.planyourtv.policy.PolicyVoiceCue
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.SessionStartResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection

/**
 * Command-only MQTT channel.
 *
 * Config and usage stay on [PolicyCloud]'s HTTP host: one authoritative request/response path.
 * MQTT exists only so a parent's "提前结束"/"撤销下发" reaches the TV immediately instead of
 * waiting for the next config poll. A queued QoS 1 command plus the recordId check makes a
 * reconnect safe; a command for an old record is ignored by PolicyRuntime.
 *
 * The broker URL/topic prefix are build-time values; the username/password are fetched per
 * device over the ACTIVE v2 HTTPS channel and never compiled into the APK. An empty URL
 * disables this channel and leaves the HTTP path working.
 */
object PolicyMqttClient {
    private const val TAG = "PolicyMqtt"
    private const val RECONNECT_MS = 5_000L
    private const val COMMAND_TTL_MS = 60_000L
    private const val KEY_REFRESH_MS = 5 * 60_000L
    private const val KEY_MAX_AGE_MS = 10 * 60_000L
    private const val KEY_REFRESH_RETRY_MS = 30_000L
    private const val DEFAULT_PARENT_OVERRIDE_MS = 10 * 60_000L
    private const val APPLIED_COMMAND_LIMIT = 64

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    private val subscribed = AtomicBoolean(false)
    private val preparingSubscribe = AtomicBoolean(false)
    private val clientRef = AtomicReference<MqttAsyncClient?>(null)
    private val keyRefreshNextAt = AtomicLong(0L)

    @Volatile
    private var keyRefreshJob: Job? = null

    private var currentDeviceNo = ""
    private var commandTopic = ""
    private var responseTopic = ""

    fun install(context: Context) {
        val url = BuildConfig.TV_MQTT_URL.trim()
        if (url.isEmpty()) {
            Log.i(TAG, "TV_MQTT_URL is empty; MQTT command channel disabled")
            return
        }
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(
                PolicyRuntime.state.map { it.bound to it.deviceNo },
                PolicyRuntime.cloudActive,
            ) { (bound, deviceNo), active -> Triple(active, bound, deviceNo) }
                .distinctUntilChanged()
                .collect { (active, bound, deviceNo) ->
                    if (active && bound && deviceNo.isNotBlank()) {
                        ensureConnected(deviceNo)
                    } else {
                        disconnect()
                    }
                }
        }
    }

    @Synchronized
    private fun ensureConnected(deviceNo: String) {
        val existing = clientRef.get()
        if (existing != null && existing.isConnected && currentDeviceNo == deviceNo) {
            // The connection may be up while the signing-key fetch is still pending. Retry the
            // subscription instead of returning without ever subscribing.
            if (!subscribed.get()) subscribeWhenReady(existing, deviceNo)
            return
        }
        if (connecting.get() && currentDeviceNo == deviceNo) return
        disconnect()
        connect(deviceNo)
    }

    @Synchronized
    private fun connect(deviceNo: String) {
        val url = BuildConfig.TV_MQTT_URL.trim()
        if (url.isEmpty()) return
        if (PolicyRuntime.deviceToken().isBlank()) {
            Log.w(TAG, "no ACTIVE device credential; refusing MQTT connection")
            return
        }
        // A production build that talks to HTTPS must also use TLS for MQTT. The dev broker
        // may stay on tcp:// while TV_API_BASE is plainly HTTP.
        if (BuildConfig.TV_API_BASE.startsWith("https://") && !url.startsWith("ssl://")) {
            Log.e(TAG, "refusing plaintext MQTT while the API uses HTTPS")
            return
        }
        val clientId = BuildConfig.TV_MQTT_CLIENT_PREFIX + deviceNo
        val topicPrefix = BuildConfig.TV_MQTT_TOPIC_PREFIX
        commandTopic = topicPrefix + "command/device/" + deviceNo
        responseTopic = topicPrefix + "response/device/" + deviceNo
        currentDeviceNo = deviceNo
        connecting.set(true)
        scope.launch {
            try {
                // Per-device account fetched over the ACTIVE v2 channel. Fail closed: never
                // fall back to a shared build-time username/password.
                val credentials = PolicyCloud.fetchMqttCredentials(deviceNo).getOrElse { error ->
                    connecting.set(false)
                    Log.w(TAG, "mqtt credentials unavailable; not connecting", error)
                    scheduleReconnect(deviceNo)
                    return@launch
                }
                val client = MqttAsyncClient(url, clientId, MemoryPersistence())
                client.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        if (clientRef.get() !== client) return
                        val state = PolicyRuntime.state.value
                        if (!PolicyRuntime.cloudActive.value || !state.bound || state.deviceNo != deviceNo) {
                            disconnect()
                            return
                        }
                        Log.i(TAG, "connected=$reconnect $serverURI")
                        subscribed.set(false)
                        subscribeWhenReady(client, deviceNo)
                    }

                    override fun connectionLost(cause: Throwable?) {
                        if (clientRef.get() !== client) return
                        subscribed.set(false)
                        Log.w(TAG, "connection lost", cause)
                        // Reconnect ourselves so a rotated credential or revoked account is
                        // re-evaluated by the backend instead of being retried by Paho forever.
                        scheduleReconnect(deviceNo)
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (clientRef.get() !== client) return
                        val payload = message?.payload?.toString(Charsets.UTF_8) ?: return
                        onMessage(payload)
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
                })
                clientRef.set(client)

                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = false
                    // V10 remote control is online-only: the server does not queue commands, and
                    // the TV must not replay a stop/start that was issued while it was offline.
                    // A clean session also drops the old persistent subscription state after a
                    // credential rotation. The parent re-reads state and re-issues if needed.
                    isCleanSession = true
                    keepAliveInterval = 20
                    connectionTimeout = 10
                    maxInflight = 10
                    // Paho 1.2.x leaves sslHostnameVerifier null by default, which disables
                    // hostname checks even when the certificate chain is valid. Use the
                    // platform verifier: a certificate issued for another host must never be
                    // accepted as this broker. There is no insecure fallback; a mismatch
                    // fails the connection like any other TLS error.
                    setSSLHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier())
                    userName = credentials.username
                    password = credentials.password.toCharArray()
                }
                client.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(token: IMqttToken?) {
                        connecting.set(false)
                        Log.i(TAG, "connect requested: $clientId")
                    }

                    override fun onFailure(token: IMqttToken?, error: Throwable?) {
                        connecting.set(false)
                        Log.w(TAG, "connect failed: $clientId", error)
                        scheduleReconnect(deviceNo)
                    }
                })
            } catch (error: Throwable) {
                connecting.set(false)
                Log.w(TAG, "client setup failed", error)
                scheduleReconnect(deviceNo)
            }
        }
    }

    /**
     * The signing key is fetched before subscribing, so queued QoS 1 commands cannot arrive
     * while the TV still has an unknown/stale signing state. There is no unsigned fallback:
     * without an enabled, verified key the device never subscribes.
     */
    private fun subscribeWhenReady(client: MqttAsyncClient, deviceNo: String) {
        if (!preparingSubscribe.compareAndSet(false, true)) return
        scope.launch {
            try {
                if (!prepareCommandSigning(deviceNo)) {
                    Log.w(TAG, "command signing state unavailable; delaying subscribe")
                    scheduleReconnect(deviceNo)
                    return@launch
                }
                if (clientRef.get() !== client || !client.isConnected) return@launch
                Log.i(TAG, "subscribe $commandTopic")
                subscribe(client)
            } finally {
                preparingSubscribe.set(false)
            }
        }
    }

    private suspend fun prepareCommandSigning(deviceNo: String): Boolean {
        if (PolicyRuntime.deviceToken().isBlank()) return false
        val state = PolicyRuntime.commandSigning()
        // A recently fetched state is good enough for a reconnect. Fetching on every MQTT flap
        // would turn a flaky network into a key-endpoint rate-limit lockout; the periodic
        // refresh and keyId-mismatch path still pick up rotations and backend changes.
        if (state.known && state.enabled && state.fetchedAtMs > 0L &&
            System.currentTimeMillis() - state.fetchedAtMs <= KEY_MAX_AGE_MS
        ) {
            return true
        }
        val result = PolicyCloud.refreshCommandKey(deviceNo)
        if (result.isSuccess && result.getOrDefault(false)) return true
        Log.w(TAG, "command signing key refresh failed", result.exceptionOrNull())
        return false
    }

    private fun subscribe(client: MqttAsyncClient) {
        try {
            client.subscribe(commandTopic, 1, null, object : IMqttActionListener {
                override fun onSuccess(token: IMqttToken?) {
                    if (clientRef.get() !== client) return
                    subscribed.set(true)
                    startKeyRefresh(currentDeviceNo)
                }

                override fun onFailure(token: IMqttToken?, error: Throwable?) {
                    if (clientRef.get() !== client) return
                    subscribed.set(false)
                    Log.w(TAG, "subscribe failed: $commandTopic", error)
                    scheduleReconnect(currentDeviceNo)
                }
            })
        } catch (error: Throwable) {
            subscribed.set(false)
            Log.w(TAG, "subscribe threw", error)
            scheduleReconnect(currentDeviceNo)
        }
    }

    /** Picks up a backend key rotation without waiting for a reconnect. */
    private fun startKeyRefresh(deviceNo: String) {
        keyRefreshJob?.cancel()
        keyRefreshJob = scope.launch {
            while (isActive) {
                delay(KEY_REFRESH_MS)
                if (!subscribed.get() || currentDeviceNo != deviceNo) return@launch
                PolicyCloud.refreshCommandKey(deviceNo)
                    .onFailure { Log.w(TAG, "periodic command signing key refresh failed", it) }
            }
        }
    }

    private fun scheduleReconnect(deviceNo: String) {
        scope.launch {
            delay(RECONNECT_MS)
            val state = PolicyRuntime.state.value
            if (PolicyRuntime.cloudActive.value && state.bound && state.deviceNo == deviceNo) {
                ensureConnected(deviceNo)
            }
        }
    }

    private fun onMessage(payload: String) {
        val outer = runCatching { JSONObject(payload) }.getOrElse {
            // JSONException messages can include the raw payload; log the shape, not the body.
            Log.w(TAG, "invalid json")
            return
        }
        // Everything on the command topic must carry a backend signature. The decoded signed
        // payload, not the outer envelope, is authoritative.
        val json = requireSignedCommand(outer) ?: return
        val method = json.optString("method", "")
        if (method == "config_changed" && json.optInt("isEnabled", 1) == 0) {
            verifyUnbindThenApply("", "")
            return
        }
        if (method == "config_changed") {
            // Parent edits (guard mode, whitelist, password) arrive as a notice; pull the config
            // now so the TV reacts immediately instead of waiting for the next poll. Concurrent
            // fetches are deduped inside requestSync.
            PolicyRuntime.requestSync()
            if (json.optString("command", "").isBlank()) return
        }

        val command = json.optString("command", "")
        val commandId = json.optString("commandId", "")
        val correlationId = json.optString("correlationId", "")
        val bindStatus = json.optString("bind_status", "")
        val data = json.optJSONObject("data")
        val recordId = data?.optInt("recordId", 0) ?: 0
        // The signer puts issuedAt in the signed payload root. Older payloads may carry it in
        // data; prefer data when present so a delayed temporary unlock cannot outlive its TTL.
        val dataIssuedAt = data?.optLong("issuedAt", 0L) ?: 0L
        val issuedAt = if (dataIssuedAt > 0L) dataIssuedAt else json.optLong("issuedAt", 0L)
        val stale = issuedAt > 0 && System.currentTimeMillis() - issuedAt > COMMAND_TTL_MS
        PolicyFlowTrace.event(
            "mqtt.command",
            "method" to method,
            "command" to command,
            "commandId" to commandId,
            "correlationId" to correlationId,
            "recordId" to recordId,
            "bindStatus" to bindStatus,
            "stale" to stale,
            "hostedRule" to PolicyModeCoordinator.isHostedRuleActive(),
        )

        // QoS 1 can redeliver on the same connection. Replaying "start" would now hit BUSY and
        // look like a failure, so return the remembered result for the same commandId.
        if (commandId.isNotBlank()) {
            previousResult(commandId)?.let { handled ->
                ack(correlationId, handled, command)
                return
            }
        }

        // V10 remote control is online-only and short-lived. An untimestamped or expired V10
        // command is never executed; the phone must re-read state and re-issue.
        val hostedRule = PolicyModeCoordinator.isHostedRuleActive()
        val v10Command = command == "set_activation" ||
            command == "start_session" ||
            command == "start_parent_override"
        if (hostedRule && v10Command && (issuedAt <= 0L || stale)) {
            Log.i(TAG, "ignore stale or untimestamped command: $command")
            ack(correlationId, false, command, if (stale) "EXPIRED" else "INVALID_COMMAND")
            return
        }
        if (hostedRule && command == "stop_session" && stale) {
            // An old stop must not end a segment that was started after it was issued.
            Log.i(TAG, "ignore stale stop_session")
            ack(correlationId, false, command, "EXPIRED")
            return
        }

        // Unbind commands can be queued (QoS 1, non-clean session) from an old unbind and be
        // delivered after the parent has already re-bound. Verify the backend before dropping a
        // fresh binding; the periodic binding check remains the fallback.
        if (command == "bind_status_changed" && bindStatus == "unbind") {
            verifyUnbindThenApply(correlationId, command)
            return
        }

        val handled = when (command) {
            "set_activation" -> {
                val activation = parseActivation(data?.optString("activation", ""))
                val expectedVersion = data?.optLong("expectedVersion", -1L) ?: -1L
                val expectedEpoch = data?.optLong("expectedEpoch", Long.MIN_VALUE) ?: Long.MIN_VALUE
                val ok = hostedRule &&
                    activation != null &&
                    expectedVersion >= 0L &&
                    expectedEpoch != Long.MIN_VALUE &&
                    PolicyModeCoordinator.applyRemoteActivation(
                        activation,
                        expectedVersion,
                        expectedEpoch,
                    )
                if (hostedRule && activation != null) {
                    // SetActivation only flips the activation. Pull the mode-specific rules,
                    // windows and app lists as well so the TV runs the same snapshot the phone
                    // has just committed, including when this command was stale and was refused.
                    PolicyRuntime.requestSync()
                }
                ack(correlationId, ok, command)
                ok
            }
            "start_session" -> {
                val durationSeconds = data?.optLong("durationSeconds", 0L) ?: 0L
                val expectedEpoch = data?.optLong("expectedEpoch", Long.MIN_VALUE) ?: Long.MIN_VALUE
                val expiresAt = (data?.optLong("expiresAtEpochMs", 0L) ?: 0L).takeIf { it > 0L }
                val hasScope = data?.has("allowedPackages") == true
                val scope = if (hasScope) parsePackages(data?.optJSONArray("allowedPackages")) else null
                // A0: a record-backed start is only an acceleration hint. The HTTP snapshot is
                // the sole authority for the recipient identity and the pending gate; arming
                // from MQTT here would show the button/voice before the avatar arrives. A direct
                // PTV dispatch (recordId <= 0) has no record to poll, so it keeps the direct arm.
                val recordBacked = recordId > 0
                val result = when {
                    !hostedRule -> SessionStartResult.Rejected(RejectReason.GUARD_DISABLED)
                    recordBacked -> {
                        PolicyRuntime.requestSync()
                        SessionStartResult.Pending
                    }
                    hasScope && scope == null ->
                        SessionStartResult.Rejected(RejectReason.GUARD_DISABLED)
                    else -> PolicyModeCoordinator.startRemoteManualSession(
                        durationSeconds = durationSeconds,
                        expectedEpoch = expectedEpoch,
                        allowedPackages = scope,
                        expiresAtEpochMs = expiresAt,
                        recordId = recordId,
                    )
                }
                val ok = result is SessionStartResult.Started ||
                    result is SessionStartResult.Pending
                val rejectReason = (result as? SessionStartResult.Rejected)?.reason?.name
                if (rejectReason != null) {
                    Log.i(TAG, "start_session rejected: $rejectReason")
                }
                val status = when (result) {
                    SessionStartResult.Started -> "SESSION_ACTIVE"
                    SessionStartResult.Pending -> "SESSION_PENDING"
                    is SessionStartResult.Rejected -> null
                }
                PolicyFlowTrace.event(
                    "mqtt.start_session",
                    "recordId" to recordId,
                    "recordBacked" to recordBacked,
                    "duration" to durationSeconds,
                    "result" to (status ?: "REJECTED"),
                    "reason" to rejectReason,
                )
                ack(correlationId, ok, command, rejectReason, status)
                ok
            }
            "start_parent_override" -> {
                val requested = data?.optLong("durationSeconds", 0L) ?: 0L
                val durationMs = if (requested > 0L) {
                    requested.coerceIn(60L, 24L * 60L * 60L) * 1_000L
                } else {
                    DEFAULT_PARENT_OVERRIDE_MS
                }
                val ok = hostedRule &&
                    PolicyModeCoordinator.setTemporaryUnlock(true, durationMs)
                ack(correlationId, ok, command)
                ok
            }
            "stop_session" -> if (hostedRule) {
                PolicyModeCoordinator.stopRemoteSession(recordId)
            } else {
                PolicyRuntime.onRemoteStop(recordId)
            }
            "cancel_session" -> if (hostedRule) {
                PolicyModeCoordinator.cancelRemoteSession(recordId)
            } else {
                PolicyRuntime.onRemoteCancel(recordId)
            }
            "modify_password" -> {
                // The payload carries no password. The TV pulls it over HTTPS with its device
                // token, so a queued command cannot leak a stale plaintext password.
                scope.launch {
                    // An explicit parent save is authoritative even if the TV still has a
                    // pending local change; the backend value wins.
                    val ok = PolicyCloud.syncPassword(currentDeviceNo, force = true)
                        .getOrDefault(false)
                    ack(correlationId, ok, command)
                }
                true
            }
            "tmp_unlock_monitor", "pause_guard" -> {
                if (stale) {
                    // The verifier already rejected expired envelopes; this also covers a
                    // delayed unlock whose signed data.issuedAt is older than the UI TTL.
                    Log.i(TAG, "ignore stale temporary unlock")
                    false
                } else {
                    val wasPaused = PolicyModeCoordinator.isTemporaryUnlocked()
                    val ok = PolicyModeCoordinator.setTemporaryUnlock(true)
                    if (ok && !wasPaused) {
                        PolicyVoiceBus.emit(PolicyVoiceCue.TEMP_UNLOCK)
                    }
                    ack(correlationId, ok, command)
                    ok
                }
            }
            "tmp_lock_monitor", "start_guard" -> {
                // LockSurfaceRelay fronts Aerial from the guard-state edge; do not also
                // bring Home forward here, or countdown and remote restore drift apart.
                val wasPaused = PolicyModeCoordinator.isTemporaryUnlocked()
                val ok = PolicyModeCoordinator.setTemporaryUnlock(false)
                if (ok && wasPaused) {
                    PolicyVoiceBus.emit(PolicyVoiceCue.GUARD_RESUMED)
                }
                ack(correlationId, ok, command)
                ok
            }
            else -> {
                Log.i(TAG, "ignore command: $command")
                false
            }
        }
        PolicyFlowTrace.event(
            "mqtt.result",
            "command" to command,
            "recordId" to recordId,
            "handled" to handled,
        )
        if (commandId.isNotBlank()) rememberResult(commandId, handled)
        if (command == "stop_session" || command == "cancel_session") {
            ack(correlationId, handled, command)
        }
    }

    private fun parseActivation(raw: String?): Activation? = when (raw?.uppercase(java.util.Locale.ROOT)) {
        Activation.AUTO.name -> Activation.AUTO
        Activation.MANUAL.name -> Activation.MANUAL
        else -> null
    }

    /** Strict: a malformed scope rejects the command instead of widening it to all apps. */
    private fun parsePackages(array: org.json.JSONArray?): Set<String>? {
        if (array == null) return null
        val packages = LinkedHashSet<String>()
        for (index in 0 until array.length()) {
            val raw = array.opt(index) as? String ?: return null
            val value = raw.trim()
            if (value.isEmpty()) return null
            packages.add(value)
        }
        return packages
    }

    private val appliedCommands = object :
        LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Boolean>?,
        ): Boolean = size > APPLIED_COMMAND_LIMIT
    }

    private fun previousResult(commandId: String): Boolean? =
        synchronized(appliedCommands) { appliedCommands[commandId] }

    private fun rememberResult(commandId: String, handled: Boolean) {
        synchronized(appliedCommands) { appliedCommands[commandId] = handled }
    }

    /**
     * Returns the signed payload, or null when the TV must ignore the message. Without an ACTIVE
     * credential, a fetched key, and a valid ECDSA signature there is no execution path.
     */
    private fun requireSignedCommand(json: JSONObject): JSONObject? {
        if (PolicyRuntime.deviceToken().isBlank()) return null
        val state = PolicyRuntime.commandSigning()
        if (!state.known || !state.enabled) {
            Log.w(TAG, "drop command: command signing state unavailable")
            refreshCommandSigningKey()
            return null
        }
        val verified = MqttCommandVerifier.verify(json, currentDeviceNo, state)
        if (verified == null) {
            Log.w(TAG, "drop command: signature rejected")
            if (json.optString("keyId", "") != state.keyId) refreshCommandSigningKey()
            return null
        }
        return verified
    }

    private fun refreshCommandSigningKey() {
        val now = SystemClock.elapsedRealtime()
        val next = keyRefreshNextAt.get()
        if (now < next || !keyRefreshNextAt.compareAndSet(next, now + KEY_REFRESH_RETRY_MS)) return
        val deviceNo = currentDeviceNo
        if (deviceNo.isBlank() || PolicyRuntime.deviceToken().isBlank()) return
        scope.launch {
            PolicyCloud.refreshCommandKey(deviceNo)
                .onFailure { Log.w(TAG, "command signing key refresh failed", it) }
        }
    }

    /**
     * A queued MQTT unbind may belong to an older binding generation. Only apply it when the
     * backend currently says the device is unbound; a network failure keeps the local binding
     * and lets the periodic binding watch decide later.
     */
    private fun verifyUnbindThenApply(correlationId: String, command: String) {
        scope.launch {
            val state = PolicyRuntime.state.value
            if (!state.bound || state.deviceNo.isBlank()) return@launch
            val binding = PolicyCloud.checkBinding(state.deviceNo)
            if (binding.isSuccess && !binding.getOrThrow()) {
                val handled = PolicyModeCoordinator.onCloudUnboundFromBackend()
                ack(correlationId, handled, command)
            } else {
                Log.w(
                    TAG,
                    "ignoring unbind command; backend binding is still ${binding.getOrNull() ?: "unknown"}",
                )
            }
        }
    }

    private fun ack(
        correlationId: String,
        handled: Boolean,
        command: String,
        reason: String? = null,
        status: String? = null,
    ) {
        val client = clientRef.get() ?: return
        if (!client.isConnected || correlationId.isBlank()) return
        val payload = JSONObject().apply {
            put("correlationId", correlationId)
            put("success", true)
            put("code", 200)
            put("message", if (handled) "已处理" else "已收到但忽略")
            // The backend and phone must be able to distinguish "executed" from "received
            // but rejected"; success=true only means the envelope was processed.
            val data = JSONObject().put("accepted", handled)
            if (reason != null) data.put("reason", reason)
            if (status != null) {
                data.put("status", status)
            } else if (command == "cancel_session" && handled) {
                data.put("status", "SESSION_CANCELED")
            }
            put("data", data)
        }
        val message = MqttMessage(payload.toString().toByteArray(Charsets.UTF_8)).apply { qos = 1 }
        runCatching { client.publish(responseTopic, message) }
            .onFailure { Log.w(TAG, "ack failed", it) }
    }

    @Synchronized
    private fun disconnect() {
        keyRefreshJob?.cancel()
        keyRefreshJob = null
        subscribed.set(false)
        preparingSubscribe.set(false)
        currentDeviceNo = ""
        connecting.set(false)
        val client = clientRef.getAndSet(null) ?: return
        scope.launch {
            runCatching {
                if (client.isConnected) client.disconnectForcibly(0, 1_000)
                client.close()
            }.onFailure { Log.w(TAG, "disconnect failed", it) }
        }
    }
}
