package app.orchard.tv.bot

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID

/**
 * Client for the open WeChat ClawBot backend protocol
 * (https://github.com/Tencent/openclaw-weixin/blob/main/docs/protocol.md).
 *
 * Transport is plain HTTPS + JSON with a long-polling receive loop, so the TV
 * needs no persistent connection and no inbound reachability. The protocol
 * doc is written for third-party implementers: [CHANNEL_VERSION] carries the
 * required "major.minor.patch/impl-suffix" form so the server can gate us.
 *
 * Split in two so the JVM tests cover everything without a network:
 * [WeixinClawCodec] builds/parses JSON only; [HttpTransport] performs calls.
 */
internal object WeixinClawApi {

    /** Protocol floor we implement; the suffix identifies this TV app per the doc. */
    const val CHANNEL_VERSION_BASE = "3.0.1"

    const val DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com"

    private const val PATH_QRCODE = "/ilink/bot/get_bot_qrcode"
    private const val PATH_QRCODE_STATUS = "/ilink/bot/get_qrcode_status"
    private const val PATH_GET_UPDATES = "/ilink/bot/getupdates"
    private const val PATH_SEND_MESSAGE = "/ilink/bot/sendmessage"

    /** Long-poll server hang is 35s; leave headroom for a slow radio. */
    private const val POLL_READ_TIMEOUT_MS = 45_000

    // --- binding -------------------------------------------------------------

    /** Requests a self-binding QR. [knownTokens] lets the backend dedupe re-binds. */
    fun requestQr(
        transport: HttpTransport,
        baseUrl: String = DEFAULT_BASE_URL,
        knownTokens: List<String>,
        channelVersion: String = CHANNEL_VERSION_BASE,
    ): QrTicket? {
        val body = JSONObject()
            .put("local_token_list", JSONArray(knownTokens))
            // Register our product name at bind time: the backend's offline
            // auto-reply names the client, and its default is "OpenClaw".
            .put("base_info", WeixinClawCodec.baseInfo(channelVersion))
        val json = transport.call(
            method = "POST",
            url = "$baseUrl$PATH_QRCODE?bot_type=3",
            headers = postHeaders(appHeaders()),
            body = body.toString(),
            readTimeoutMs = CONNECT_TIMEOUT_MS_DEFAULT,
        ) ?: return null
        return WeixinClawCodec.decodeQrTicket(json)
    }

    /**
     * Polls the QR state. The server holds the request up to 35s; "wait" (and a
     * failed call, by the caller's policy) simply means "ask again".
     */
    fun pollQrStatus(
        transport: HttpTransport,
        baseUrl: String,
        qrcode: String,
        verifyCode: String? = null,
    ): QrStatus {
        val url = buildString {
            append(baseUrl).append(PATH_QRCODE_STATUS)
            append("?qrcode=").append(urlEncode(qrcode))
            if (!verifyCode.isNullOrEmpty()) append("&verify_code=").append(urlEncode(verifyCode))
        }
        val json = transport.call("GET", url, appHeaders(), null, CONNECT_TIMEOUT_MS_DEFAULT)
            ?: return QrStatus.Wait
        return WeixinClawCodec.decodeQrStatus(json, baseUrl)
    }

    // --- messaging -----------------------------------------------------------

    /**
     * One long-poll turn. [cursor] is the previous page's get_updates_buf;
     * empty on the first call. Never blocks longer than [POLL_READ_TIMEOUT_MS].
     */
    fun getUpdates(
        transport: HttpTransport,
        baseUrl: String,
        botToken: String,
        channelVersion: String,
        cursor: String,
    ): UpdatesResult {
        val body = JSONObject()
            .put("get_updates_buf", cursor)
            .put("base_info", WeixinClawCodec.baseInfo(channelVersion))
        val json = transport.call(
            method = "POST",
            url = "$baseUrl$PATH_GET_UPDATES",
            headers = authHeaders(botToken),
            body = body.toString(),
            readTimeoutMs = POLL_READ_TIMEOUT_MS,
        ) ?: return UpdatesResult.NetworkError
        val result = WeixinClawCodec.decodeUpdates(json)
        if (result is UpdatesResult.Rejected) {
            // Full envelope (no bearer in responses); shows fields the doc did not name.
            com.planyourtv.log.TvLog.w(
                "WeixinClaw",
                "getupdates refused host=${java.net.URI(baseUrl).host.takeIf { it.isNotBlank() } ?: baseUrl} " +
                    "body=${json.toString().take(300)}",
            )
        }
        return result
    }

    /** Sends one text message as the bot. Needs the chat's context_token. */
    fun sendText(
        transport: HttpTransport,
        baseUrl: String,
        botToken: String,
        channelVersion: String,
        toUserId: String,
        contextToken: String,
        text: String,
    ): Boolean {
        val msg = JSONObject()
            .put("from_user_id", "")
            .put("to_user_id", toUserId)
            .put("client_id", UUID.randomUUID().toString())
            .put("message_type", 2)
            .put("message_state", 2)
            .put("context_token", contextToken)
            .put(
                "item_list",
                JSONArray().put(
                    JSONObject().put("type", 1).put("text_item", JSONObject().put("text", text)),
                ),
            )
        val body = JSONObject()
            .put("msg", msg)
            .put("base_info", WeixinClawCodec.baseInfo(channelVersion))
        val json = transport.call(
            method = "POST",
            url = "$baseUrl$PATH_SEND_MESSAGE",
            headers = authHeaders(botToken),
            body = body.toString(),
            readTimeoutMs = CONNECT_TIMEOUT_MS_DEFAULT,
        ) ?: return false
        return json.optInt("ret", -1) == 0
    }

    // --- headers -------------------------------------------------------------

    /**
     * Authenticated POST headers, per the protocol. X-WECHAT-UIN is a random
     * uint32, Base64-encoded, exactly as the spec describes.
     */
    private fun authHeaders(botToken: String): Map<String, String> {
        val random = SecureRandom()
        val uin = random.nextInt(Int.MAX_VALUE)
        val encoded = Base64.encodeToString(uin.toString().toByteArray(), Base64.NO_WRAP)
        return postHeaders(
            appHeaders() + mapOf(
                "AuthorizationType" to "ilink_bot_token",
                "Authorization" to "Bearer $botToken",
                "X-WECHAT-UIN" to encoded,
            ),
        )
    }

    /**
     * Headers every call carries; the QR GET must not include auth fields, and
     * Content-Type is added only where a body goes out.
     */
    private fun appHeaders(): Map<String, String> = mapOf(
        "iLink-App-Id" to "bot",
        "iLink-App-ClientVersion" to clientVersionHeader(),
    )

    private fun postHeaders(extra: Map<String, String>): Map<String, String> =
        extra + mapOf("Content-Type" to "application/json")

    /** 0x00MMNNPP in decimal: 3.0.1 -> 0x00030001 = 196609. */
    private fun clientVersionHeader(): String {
        val parts = CHANNEL_VERSION_BASE.split(".").map { it.toIntOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return ((major shl 16) or (minor shl 8) or patch).toString()
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}

// --- protocol models ---------------------------------------------------------

/** A QR the parent scans with WeChat. */
data class QrTicket(
    /** Poll key; not the image content. */
    val qrcode: String,
    /** URL/content to render as the on-screen QR image. */
    val imageContent: String,
)

sealed interface QrStatus {
    data object Wait : QrStatus
    data object Scanned : QrStatus
    /** The backend needs the on-screen digits next poll round. */
    data class NeedVerifyCode(val digits: String) : QrStatus
    /** Login done; carries the binding credentials. */
    data class Confirmed(
        val botToken: String,
        val botId: String,
        val userId: String,
        val baseUrl: String,
    ) : QrStatus
    data object Expired : QrStatus
    data object VerifyBlocked : QrStatus
    data object AlreadyBound : QrStatus
    /** Backend-driven host switch; keep polling on [baseUrl]. */
    data class Redirect(val baseUrl: String) : QrStatus
}

sealed interface UpdatesResult {
    data class Ok(
        val messages: List<InboundMessage>,
        val cursor: String,
    ) : UpdatesResult

    /** errcode -14: this binding must pause polling for one hour. */
    data object PauseOneHour : UpdatesResult

    data object Rejected : UpdatesResult
    data object NetworkError : UpdatesResult
}

/** One user text message, already reduced to what a command needs. */
data class InboundMessage(
    val messageId: String,
    val fromUserId: String,
    val text: String,
    val contextToken: String,
    val createTimeMs: Long,
)

// --- codec --------------------------------------------------------------------

/**
 * Pure JSON layer against the documented field names. Kept separate so tests
 * can feed fixtures from the protocol doc without any transport.
 */
internal object WeixinClawCodec {

    /** The product identity the backend shows for this client; defaults to "OpenClaw". */
    const val BOT_AGENT = "PlanYourTV"

    fun baseInfo(channelVersion: String): JSONObject =
        JSONObject().put("channel_version", channelVersion).put("bot_agent", BOT_AGENT)

    fun decodeQrTicket(json: JSONObject): QrTicket? {
        val qrcode = json.optString("qrcode", "")
        val image = json.optString("qrcode_img_content", "")
        if (qrcode.isEmpty() || image.isEmpty()) return null
        return QrTicket(qrcode = qrcode, imageContent = image)
    }

    /**
     * The doc names the states but not one status field, so accept the three
     * plausible keys. An unrecognized value is [QrStatus.Wait]: the caller
     * polls again, which is always the safe next step for this endpoint.
     */
    fun decodeQrStatus(json: JSONObject, currentBaseUrl: String): QrStatus {
        val raw = json.optString("status", json.optString("qrcode_status", json.optString("state", "")))
        val digits = json.optString("verify_code", "")
        return when {
            raw == "confirmed" -> confirmedOf(json, currentBaseUrl)
            raw == "confirmed_but_need_verifycode" && digits.isNotEmpty() ->
                QrStatus.NeedVerifyCode(digits)
            raw.contains("need_verifycode") && digits.isNotEmpty() -> QrStatus.NeedVerifyCode(digits)
            raw.contains("need_verifycode") -> QrStatus.Wait
            raw == "scaned" || raw == "scanned" -> QrStatus.Scanned
            raw == "expired" -> QrStatus.Expired
            raw == "verify_code_blocked" -> QrStatus.VerifyBlocked
            raw == "binded_redirect" -> QrStatus.AlreadyBound
            raw == "scaned_but_redirect" -> {
                val host = json.optString("redirect_host", "")
                if (host.isBlank()) QrStatus.Wait else QrStatus.Redirect("https://$host")
            }
            else -> QrStatus.Wait
        }
    }

    private fun confirmedOf(json: JSONObject, fallbackBase: String): QrStatus {
        val token = json.optString("bot_token", "")
        val botId = json.optString("ilink_bot_id", "")
        val userId = json.optString("ilink_user_id", "")
        val base = json.optString("baseurl", "").ifBlank { fallbackBase }
        if (token.isEmpty() || userId.isEmpty()) return QrStatus.Wait
        return QrStatus.Confirmed(botToken = token, botId = botId, userId = userId, baseUrl = base)
    }

    fun decodeUpdates(json: JSONObject): UpdatesResult {
        // Live-verified 2026-11-15: the success envelope carries msgs/cursor and
        // NO "ret" at all, so a missing ret means success; only an explicit
        // non-zero ret is a refusal.
        val ret = json.optInt("ret", 0)
        val errcode = json.optInt("errcode", 0)
        if (ret != 0) {
            return if (errcode == -14) UpdatesResult.PauseOneHour else UpdatesResult.Rejected
        }
        val cursor = json.optString("get_updates_buf", "")
        val messages = ArrayList<InboundMessage>()
        val msgs = json.optJSONArray("msgs")
        if (msgs != null) {
            for (i in 0 until msgs.length()) {
                decodeMessage(msgs.optJSONObject(i) ?: continue)?.let(messages::add)
            }
        }
        return UpdatesResult.Ok(messages = messages, cursor = cursor)
    }

    /** Only user text (message_type 1, text item) matters for commands. */
    private fun decodeMessage(json: JSONObject): InboundMessage? {
        if (json.optInt("message_type", 1) != 1) return null
        val text = firstText(json.optJSONArray("item_list")) ?: return null
        return InboundMessage(
            messageId = json.optString("message_id", ""),
            fromUserId = json.optString("from_user_id", ""),
            text = text,
            contextToken = json.optString("context_token", ""),
            createTimeMs = json.optLong("create_time_ms", 0L),
        )
    }

    private fun firstText(items: JSONArray?): String? {
        if (items == null) return null
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (item.optInt("type", 0) == 1) {
                val text = item.optJSONObject("text_item")?.optString("text", "").orEmpty()
                if (text.isNotBlank()) return text
            }
        }
        return null
    }
}

// --- transport -----------------------------------------------------------------

/** One HTTP call; injectable so tests and a future proxy stay off the wire. */
fun interface HttpTransport {
    /** Returns the response body as JSON, or null for any transport failure. */
    fun call(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        readTimeoutMs: Int,
    ): JSONObject?
}

internal const val CONNECT_TIMEOUT_MS_DEFAULT = 15_000

/** HttpURLConnection implementation; the whole project avoids an HTTP dependency. */
class UrlConnectionTransport : HttpTransport {
    override fun call(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        readTimeoutMs: Int,
    ): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            connection = c
            c.requestMethod = method
            c.connectTimeout = CONNECT_TIMEOUT_MS_DEFAULT
            c.readTimeout = readTimeoutMs
            c.doInput = true
            if (body != null) c.doOutput = true
            for ((name, value) in headers) c.setRequestProperty(name, value)
            if (body != null) {
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || text.isBlank()) null else JSONObject(text)
        } catch (_: IOException) {
            // A timeout or dropped route is an ordinary poll outcome, not an error state.
            null
        } catch (_: org.json.JSONException) {
            // A non-JSON body is as unusable as no body; the caller backs off.
            null
        } finally {
            // Long-poll connections never reuse the pool; always drop them.
            runCatching { connection?.disconnect() }
        }
    }
}
