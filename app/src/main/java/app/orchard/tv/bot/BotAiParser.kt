package app.orchard.tv.bot

import app.orchard.tv.quiz.QuizSessionStore
import com.planyourtv.policy.TvStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional natural-language parsing through the parent's own OpenAI-compatible
 * model (the one configured for AI quiz generation). Runs only when the
 * deterministic layers of [BotCommandParser] produced nothing.
 *
 * Safety contract: the model is a translator, never an executor.
 * - its output is one JSON object with a cmd from a fixed whitelist;
 * - Unbind is unreachable from this path (it must be typed deliberately);
 * - AddTime minutes must already be inside 5-120 or the parse is discarded;
 * - a daily call cap keeps a chatty evening off the parent's model bill.
 */
internal object BotAiParser {
    const val DAILY_LIMIT = 30

    private const val KEY_AI_DATE = "bot.ai.date"
    private const val KEY_AI_COUNT = "bot.ai.count"
    private const val DAY_MS = 24 * 60 * 60_000L
    private const val TIMEOUT_MS = 8_000

    fun available(): Boolean = QuizSessionStore.providerConfigured() && usedToday() < DAILY_LIMIT

    fun usedToday(nowMs: Long = System.currentTimeMillis()): Int {
        val day = TvStore.getLong(KEY_AI_DATE, 0L)
        if (day != dayBucket(nowMs)) return 0
        return TvStore.getInt(KEY_AI_COUNT, 0)
    }

    /** Returns the mapped command, or null for "none", off-whitelist, malformed, or over the cap. */
    fun parse(text: String): BotCommand? {
        if (!available()) return null
        val meta = QuizSessionStore.loadProviderMeta()
        val key = QuizSessionStore.providerKey() ?: return null
        val content = chat(meta.baseUrl, meta.model, key, text) ?: return null
        countUse()
        return mapCommand(content)
    }

    // --- model call -----------------------------------------------------------

    private val SYSTEM_PROMPT = """
        你是电视遥控指令解析器。把家长的自然语言映射为JSON命令。
        只输出一个JSON对象，不要任何其他文字。格式：{"cmd":"...","minutes":N}
        cmd 只能是以下之一：status,today,rules,apps,bank,wrong,levels,diag,audit,report,help,addtime,stop,unlock,lock,study_enter,study_exit,quiz_on,quiz_off
        addtime 必须带 minutes（5到120的整数）。minutes单位是分钟。
        设置类（答题上限、每轮题数等配置、模式切换、提醒开关）和撤销必须由家长手动输入，永远输出 none。
        无法确定、超出上述范围、或涉及别的事情时，输出 {"cmd":"none"}
        示例：
        "让孩子再看半小时" -> {"cmd":"addtime","minutes":30}
        "别看了" -> {"cmd":"stop"}
        "电视还能看多久" -> {"cmd":"status"}
        "今天看了多少" -> {"cmd":"today"}
        "现在是什么规则" -> {"cmd":"rules"}
        "孩子最近错了哪些题" -> {"cmd":"wrong"}
        "写作业模式" -> {"cmd":"study_enter"}
        "帮我点个外卖" -> {"cmd":"none"}
    """.trimIndent()

    /** Minimal OpenAI-compatible chat call; failures are an ordinary "no answer". */
    private fun chat(baseUrl: String, model: String, key: String, userText: String): String? {
        return try {
            val connection = URL("${baseUrl.trimEnd('/')}/chat/completions").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $key")
            val body = JSONObject()
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                        .put(JSONObject().put("role", "user").put("content", userText.take(200))),
                )
                .put("model", model)
                .put("temperature", 0)
                .put("max_tokens", 200)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text)
                .getJSONArray("choices")
                .optJSONObject(0)
                ?.getJSONObject("message")
                ?.optString("content", "")
                ?.ifBlank { null }
        } catch (_: IOException) {
            null
        } catch (_: org.json.JSONException) {
            null
        }
    }

    // --- mapping ----------------------------------------------------------------

    /** Pure: content JSON -> command. Whitespace and markdown fences tolerated. */
    fun mapCommand(content: String): BotCommand? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = runCatching { JSONObject(content.substring(start, end + 1)) }.getOrNull() ?: return null
        return when (val cmd = json.optString("cmd", "")) {
            "status" -> BotCommand.Status
            "today" -> BotCommand.Today
            "rules" -> BotCommand.Rules
            "report" -> BotCommand.DailyReport
            "apps" -> BotCommand.Apps
            "bank" -> BotCommand.Bank
            "wrong" -> BotCommand.WrongBook
            "levels" -> BotCommand.Levels
            "diag" -> BotCommand.Diag
            "help" -> BotCommand.Help
            "stop" -> BotCommand.Stop
            "unlock" -> BotCommand.Unlock
            "lock" -> BotCommand.Lock
            "study_enter" -> BotCommand.StudyEnter
            "study_exit" -> BotCommand.StudyExit
            "quiz_on" -> BotCommand.QuizEnable
            "quiz_off" -> BotCommand.QuizDisable
            "addtime" -> {
                val minutes = json.optInt("minutes", 0)
                if (minutes in 5..120) BotCommand.AddTime(minutes) else null
            }
            // "none", unknown values, and anything off the whitelist (settings,
            // undo, unbind): not executable.
            else -> null
        }
    }

    private fun countUse(nowMs: Long = System.currentTimeMillis()) {
        val bucket = dayBucket(nowMs)
        val current = if (TvStore.getLong(KEY_AI_DATE, 0L) == bucket) TvStore.getInt(KEY_AI_COUNT, 0) else 0
        TvStore.putLong(KEY_AI_DATE, bucket)
        TvStore.putInt(KEY_AI_COUNT, current + 1)
    }

    private fun dayBucket(nowMs: Long): Long = nowMs / DAY_MS
}
