package app.orchard.tv.bot

import com.planyourtv.policy.TvStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable home of the ClawBot bindings, kept in app-private storage through
 * [TvStore] exactly like the quiz session: it is the source of truth across
 * power loss, because the poll loops resume from these rows on every start.
 *
 * The feature switch is off until the parent turns it on in the parent
 * center; a fresh install therefore opens no connection to WeChat at all.
 *
 * Keys follow the quiz store's flat-name convention ("bot.*"), not the
 * internal PolicyKeys file, because this is :app-owned data.
 */
internal object BotBindingStore {
    /** Two parents plus two grandparents covers the households that asked for this. */
    const val MAX_BINDINGS = 4

    private const val KEY_ENABLED = "bot.enabled"
    private const val KEY_BINDINGS = "bot.bindings.v1"
    private const val KEY_REMIND = "bot.remind5"

    fun enabled(): Boolean = TvStore.getBoolean(KEY_ENABLED, false)

    fun setEnabled(value: Boolean) {
        TvStore.putBoolean(KEY_ENABLED, value)
    }

    /** TV-level "5 minutes left" push toggle; on by default. */
    fun remindEnabled(): Boolean = TvStore.getBoolean(KEY_REMIND, true)

    fun setRemindEnabled(value: Boolean) {
        TvStore.putBoolean(KEY_REMIND, value)
    }

    @Synchronized
    fun list(): List<BotBinding> {
        val raw = TvStore.getString(KEY_BINDINGS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val bindings = ArrayList<BotBinding>(array.length())
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            decode(json)?.let(bindings::add)
        }
        return bindings
    }

    fun find(id: String): BotBinding? = list().firstOrNull { it.id == id }

    fun findByUser(userId: String): BotBinding? = list().firstOrNull { it.userId == userId }

    /**
     * Adds or refreshes a binding. A WeChat user who re-binds replaces their old
     * row (the backend may have rotated the token), keeping one row per parent.
     * Returns false when the cap would be exceeded by a new user.
     */
    @Synchronized
    fun upsert(
        botToken: String,
        botId: String,
        userId: String,
        baseUrl: String,
        nickname: String,
        nowMs: Long,
    ): BotBinding? {
        var bindings = list()
        bindings.firstOrNull { it.userId == userId }?.let { existing ->
            val refreshed = existing.copy(
                botToken = botToken,
                botId = botId,
                baseUrl = baseUrl,
                nickname = nickname.ifBlank { existing.nickname },
                boundAtMs = nowMs,
            )
            save(bindings.map { if (it.id == existing.id) refreshed else it })
            return refreshed
        }
        if (bindings.size >= MAX_BINDINGS) return null
        val binding = BotBinding(
            id = "b" + nowMs.toString(36) + userId.hashCode().and(0xFFFF).toString(36),
            botToken = botToken,
            botId = botId,
            userId = userId,
            baseUrl = baseUrl.ifBlank { WeixinClawApi.DEFAULT_BASE_URL },
            nickname = nickname,
            boundAtMs = nowMs,
            lastSeenMs = nowMs,
        )
        save(bindings + binding)
        return binding
    }

    @Synchronized
    fun remove(id: String): Boolean {
        val bindings = list()
        if (bindings.none { it.id == id }) return false
        save(bindings.filterNot { it.id == id })
        return true
    }

    @Synchronized
    fun rename(id: String, nickname: String): Boolean {
        val clean = nickname.trim().take(12)
        if (clean.isEmpty()) return false
        val bindings = list()
        if (bindings.none { it.id == id }) return false
        save(bindings.map { if (it.id == id) it.copy(nickname = clean) else it })
        return true
    }

    /** Persists the poll cursor after a successful getupdates page. */
    @Synchronized
    fun updateCursor(id: String, cursor: String) = mutate(id) { it.copy(cursor = cursor) }

    /** Persists the latest reply context so pushes can be sent without an inbound message. */
    @Synchronized
    fun updateContextToken(id: String, contextToken: String) =
        mutate(id) { it.copy(contextToken = contextToken) }

    @Synchronized
    fun updateLastSeen(id: String, nowMs: Long) =
        mutate(id) { it.copy(lastSeenMs = nowMs) }

    @Synchronized
    fun setPushToggles(id: String, timer: Boolean, quiz: Boolean): Boolean {
        var changed = false
        val bindings = list()
        if (bindings.none { it.id == id }) return false
        save(
            bindings.map {
                if (it.id == id) {
                    changed = true
                    it.copy(pushTimer = timer, pushQuiz = quiz)
                } else {
                    it
                }
            },
        )
        return changed
    }

    private inline fun mutate(id: String, transform: (BotBinding) -> BotBinding) {
        val bindings = list()
        if (bindings.none { it.id == id }) return
        save(bindings.map { if (it.id == id) transform(it) else it })
    }

    /** Durable: a lost cursor re-delivers messages; dedup in the gate absorbs them. */
    private fun save(bindings: List<BotBinding>) {
        val array = JSONArray()
        bindings.forEach { array.put(encode(it)) }
        TvStore.putStringDurable(KEY_BINDINGS, array.toString())
    }

    fun encode(binding: BotBinding): JSONObject = JSONObject()
        .put("id", binding.id)
        .put("bot_token", binding.botToken)
        .put("bot_id", binding.botId)
        .put("user_id", binding.userId)
        .put("base_url", binding.baseUrl)
        .put("nickname", binding.nickname)
        .put("bound_at", binding.boundAtMs)
        .put("last_seen", binding.lastSeenMs)
        .put("cursor", binding.cursor)
        .put("context_token", binding.contextToken)
        .put("push_timer", binding.pushTimer)
        .put("push_quiz", binding.pushQuiz)

    fun decode(json: JSONObject): BotBinding? {
        val id = json.optString("id", "")
        val token = json.optString("bot_token", "")
        val userId = json.optString("user_id", "")
        if (id.isEmpty() || token.isEmpty() || userId.isEmpty()) return null
        return BotBinding(
            id = id,
            botToken = token,
            botId = json.optString("bot_id", ""),
            userId = userId,
            baseUrl = json.optString("base_url", "").ifBlank { WeixinClawApi.DEFAULT_BASE_URL },
            nickname = json.optString("nickname", ""),
            boundAtMs = json.optLong("bound_at", 0L),
            lastSeenMs = json.optLong("last_seen", 0L),
            cursor = json.optString("cursor", ""),
            contextToken = json.optString("context_token", ""),
            pushTimer = json.optBoolean("push_timer", true),
            pushQuiz = json.optBoolean("push_quiz", true),
        )
    }
}
