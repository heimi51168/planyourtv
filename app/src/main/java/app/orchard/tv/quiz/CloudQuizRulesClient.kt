package app.orchard.tv.quiz

import android.util.Log
import app.orchard.tv.BuildConfig
import com.planyourtv.policy.PolicyRuntime
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONException
import org.json.JSONObject

/**
 * 主后端 `/devices/current/quiz-rules` 客户端（只读规则部分）。
 *
 * 关卡部分来自 QB，由 [CloudLevelsClient] 获取；本文件只负责鉴权、HTTP 状态映射和
 * ETag 缓存协商。任何错误都只映射为可重试/凭证/内容三类，不向 UI 透出服务器内部信息。
 */
object CloudQuizRulesClient {
    private const val TAG = "QuizRulesClient"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val TIMEOUT_MS = 10_000
    private val ETAG_RE = Regex("^sha256:[0-9a-f]{64}$")

    data class Response(
        val document: JSONObject,
        val etag: String?,
    )

    fun fetch(memberId: Long, ifNoneMatch: String?): CloudRulesResult {
        if (memberId <= 0L) return CloudRulesResult.Invalid("memberId")
        val token = PolicyRuntime.deviceToken().trim()
        if (token.isEmpty()) {
            return CloudRulesResult.Unauthorized("device credential missing")
        }
        val base = apiBase() ?: return CloudRulesResult.Invalid("TV_API_BASE")
        val url = "$base/devices/current/quiz-rules?learnerId=$memberId"
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                if (!ifNoneMatch.isNullOrBlank()) {
                    setRequestProperty("If-None-Match", "\"$ifNoneMatch\"")
                }
            }
            try {
                val httpCode = connection.responseCode
                val etag = normalizeEtag(connection.getHeaderField("ETag"))
                val stream = if (httpCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                interpret(httpCode, etag, text)
            } finally {
                connection.disconnect()
            }
        } catch (error: IOException) {
            CloudRulesResult.Retryable(error.message.orEmpty())
        } catch (error: Exception) {
            Log.w(TAG, "quiz rules request failed", error)
            CloudRulesResult.Retryable(error.javaClass.simpleName)
        }
    }

    /** 纯解析入口，测试不需要真实网络。 */
    internal fun interpret(httpCode: Int, etag: String?, bodyText: String): CloudRulesResult {
        if (httpCode == 304) return CloudRulesResult.NotModified(etag)
        if (httpCode == 401 || httpCode == 403) {
            return CloudRulesResult.Unauthorized("HTTP $httpCode")
        }
        if (httpCode >= 500) return CloudRulesResult.Retryable("HTTP $httpCode")
        val obj = try {
            JSONObject(bodyText)
        } catch (_: JSONException) {
            return if (httpCode in 200..299) {
                CloudRulesResult.Invalid("INVALID_JSON")
            } else {
                CloudRulesResult.Rejected("HTTP $httpCode", null)
            }
        }
        val code = obj.optInt("code", httpCode)
        val message = obj.optString("message", "").trim().take(160)
        if (httpCode !in 200..299) {
            return if (code == 401 || code == 403) {
                CloudRulesResult.Unauthorized(message.ifEmpty { "API $code" })
            } else {
                CloudRulesResult.Rejected(message.ifEmpty { "HTTP $httpCode" }, code)
            }
        }
        if (code != 200) {
            return if (code == 401 || code == 403) {
                CloudRulesResult.Unauthorized(message.ifEmpty { "API $code" })
            } else {
                CloudRulesResult.Rejected(message.ifEmpty { "API $code" }, code)
            }
        }
        val result = obj.optJSONObject("result")
            ?: return CloudRulesResult.Invalid("MISSING_RESULT")
        return CloudRulesResult.Ok(Response(result, etag))
    }

    internal fun normalizeEtag(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var value = raw.trim()
        if (value.startsWith("W/")) value = value.substring(2).trim()
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length - 1)
        }
        return value.takeIf { ETAG_RE.matches(it) }
    }

    private fun apiBase(): String? {
        val raw = BuildConfig.TV_API_BASE.trim().trimEnd('/')
        if (raw.isEmpty()) return null
        return if (raw.endsWith("/api/v1")) raw else "$raw/api/v1"
    }
}

sealed interface CloudRulesResult {
    data class Ok(val response: CloudQuizRulesClient.Response) : CloudRulesResult

    data class NotModified(val etag: String?) : CloudRulesResult

    data class Unauthorized(val message: String) : CloudRulesResult

    data class Retryable(val message: String) : CloudRulesResult

    data class Rejected(val message: String, val apiCode: Int?) : CloudRulesResult

    data class Invalid(val message: String) : CloudRulesResult
}
