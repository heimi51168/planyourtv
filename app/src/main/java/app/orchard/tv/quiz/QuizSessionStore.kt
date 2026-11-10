package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizDecodeResult
import com.planyourtv.policy.quiz.QuizEngineConfig
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionCodec
import com.planyourtv.policy.quiz.QuizSourceConfig
import com.planyourtv.policy.quiz.QuizSyncState
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The one storage facade for quiz state. It intentionally exposes no bank,
 * history, or candidate APIs: there is exactly one session plus its feature
 * switches.
 */
object QuizSessionStore {
    private const val KEY_SESSION = "quiz.session"
    // Kept when a record cannot be decoded, so a later lenient decoder can
    // still recover it after the main slot is cleared for the child.
    private const val KEY_SESSION_UNREADABLE = "quiz.sessionUnreadable.v1"
    private const val KEY_ENABLED = "quiz.enabled"
    private const val KEY_PROVIDER_KEY = "quiz.providerKey"
    private const val KEY_PROVIDER_META = "quiz.providerMeta"
    private const val KEY_CHALLENGE_CONFIG = "quiz.challengeConfig"
    private const val KEY_SOURCE_CONFIG = "quiz.sourceConfig"
    private const val KEY_SYNC_STATE = "quiz.syncState"
    private const val KEY_GENERATION_PREFS = "quiz.generationPrefs"
    private const val KEY_ANSWER_SCOPE = "quiz.answerScope"
    // The ordered list the parent designs on the phone. The legacy single
    // scope above stays mirrored for cloud reporting and older readers.
    private const val KEY_ANSWER_SCOPES = "quiz.answerScopes"
    private const val KEY_CONSENT_V1 = "quiz.consent_v1"
    // v2 invalidates the old 365-day browser tokens once, without touching
    // any other quiz state. Older builds could remember up to five phones;
    // only the newest entry is the active parent session now.
    private const val KEY_WEB_TOKENS = "quiz.webTokens.v2"
    // A local day is normally 24h, but a DST fall-back day lasts 25h. Keep the
    // cap just above that so a same-day token is never rejected on those days.
    private const val MAX_WEB_TOKEN_TTL_MS = 25L * 60 * 60 * 1000L
    private val WEB_TOKEN_RE = Regex("[0-9a-f]{64}")

    /** A remembered phone keeps only the SHA-256 of its bearer token, never the token itself. */
    data class WebToken(val digest: String, val expiresAt: Long)

    private val lock = Any()

    /**
     * A decoded record plus why it failed, when it did. Errors are field names
     * from the codec; the decrypted payload is never returned or logged.
     */
    data class SessionInspection(
        val session: QuizSession?,
        val hasRecord: Boolean,
        val decrypted: Boolean,
        val errors: List<String>,
    )

    fun inspectSession(): SessionInspection = synchronized(lock) {
        val raw = TvStore.getString(KEY_SESSION)
            ?: return SessionInspection(null, false, false, emptyList())
        val json = QuizCrypto.decrypt(raw)
            ?: return SessionInspection(null, true, false, emptyList())
        when (val decoded = QuizSessionCodec.decode(json)) {
            is QuizDecodeResult.Success ->
                SessionInspection(decoded.session, true, true, emptyList())
            is QuizDecodeResult.Failure ->
                SessionInspection(null, true, true, decoded.errors)
        }
    }

    fun loadSession(): QuizSession? = inspectSession().session

    /** True when a record exists but none of the decode paths produced a session. */
    fun hasStoredSession(): Boolean = TvStore.getString(KEY_SESSION) != null

    /**
     * Moves an undecodable record out of the active slot without destroying it.
     * Returns false when the backup could not be written, so the caller keeps
     * the original rather than losing it to a failed clean-up.
     */
    fun quarantineUnreadableSession(): Boolean = synchronized(lock) {
        val raw = TvStore.getString(KEY_SESSION) ?: return false
        if (!TvStore.putStringDurable(KEY_SESSION_UNREADABLE, raw)) return false
        TvStore.removeDurable(KEY_SESSION)
    }

    fun saveSession(session: QuizSession): Boolean = synchronized(lock) {
        // createdAt comes from the phone, while runtime writes use the TV clock.
        // A few seconds of skew is normal, so never persist a record whose
        // updatedAt precedes createdAt: decoding treats that as corrupt.
        val normalized = session.copy(updatedAt = maxOf(session.updatedAt, session.createdAt))
        val encrypted = QuizCrypto.encrypt(QuizSessionCodec.encode(normalized)) ?: return false
        TvStore.putStringDurable(KEY_SESSION, encrypted)
    }

    fun clearSession(): Boolean = synchronized(lock) {
        TvStore.removeDurable(KEY_SESSION)
    }

    /**
     * The first version defaults the entry on. The parent page can turn it off;
     * when off, the lock screen does not show the quiz row at all.
     */
    fun enabled(): Boolean = TvStore.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        TvStore.putBoolean(KEY_ENABLED, enabled)
        TvStore.sync()
    }

    /** True only when the stored key can still be decrypted; a dead Keystore entry is not configured. */
    fun providerConfigured(): Boolean = providerKey() != null

    fun providerKey(): String? {
        // API 21/22 has no Keystore AES/GCM. The provider key is not offered there,
        // and a plaintext marker left by an old build must never count as configured.
        if (!QuizCrypto.isEncrypted) return null
        val raw = TvStore.getString(KEY_PROVIDER_KEY) ?: return null
        return QuizCrypto.decrypt(raw)
    }

    fun saveProviderKey(key: String): Boolean {
        if (!QuizCrypto.isEncrypted) return false
        val encrypted = QuizCrypto.encrypt(key) ?: return false
        return TvStore.putStringDurable(KEY_PROVIDER_KEY, encrypted)
    }

    fun loadProviderMeta(): QuizProviderMeta = QuizProviderMeta.fromJson(TvStore.getString(KEY_PROVIDER_META))

    fun saveProviderMeta(meta: QuizProviderMeta): Boolean =
        TvStore.putStringDurable(KEY_PROVIDER_META, meta.toJson())

    /**
     * Challenge rules survive session deletion so the parent sets them once.
     * This contains no questions, keys, or household data.
     */
    fun loadChallengeConfig(): QuizConfig? = synchronized(lock) {
        val raw = TvStore.getString(KEY_CHALLENGE_CONFIG) ?: return null
        runCatching { QuizSessionCodec.decodeConfig(JSONObject(raw)) }.getOrNull()
    }

    fun saveChallengeConfig(config: QuizConfig): Boolean = synchronized(lock) {
        TvStore.putStringDurable(KEY_CHALLENGE_CONFIG, QuizSessionCodec.encodeConfig(config).toString())
    }

    /**
     * Engine configuration is the subset of the challenge config the engine
     * consumes. It is derived rather than duplicated, so a legacy config always
     * yields a valid engine snapshot.
     */
    fun loadEngineConfig(): QuizEngineConfig = synchronized(lock) {
        QuizEngineConfig.from(loadChallengeConfig() ?: QuizConfig())
    }

    fun saveEngineConfig(config: QuizEngineConfig): Boolean = synchronized(lock) {
        val legacy = loadChallengeConfig() ?: QuizConfig()
        saveChallengeConfig(config.toQuizConfig(legacy))
    }

    fun loadSourceConfig(): QuizSourceConfig = synchronized(lock) {
        QuizSourceConfig.decode(TvStore.getString(KEY_SOURCE_CONFIG)) ?: QuizSourceConfig()
    }

    fun saveSourceConfig(config: QuizSourceConfig): Boolean = synchronized(lock) {
        TvStore.putStringDurable(KEY_SOURCE_CONFIG, config.encode().toString())
    }

    fun loadSyncState(): QuizSyncState = synchronized(lock) {
        QuizSyncState.decode(TvStore.getString(KEY_SYNC_STATE)) ?: QuizSyncState()
    }

    fun saveSyncState(state: QuizSyncState): Boolean = synchronized(lock) {
        TvStore.putStringDurable(KEY_SYNC_STATE, state.encode().toString())
    }

    /**
     * The last AI-generation choices (grade, subject, scope, output settings).
     * Plain JSON: it contains no questions, keys, or household data, and the
     * phone should get the same form back after clearing browser storage.
     */
    fun loadGenerationPrefs(): JSONObject? = synchronized(lock) {
        val raw = TvStore.getString(KEY_GENERATION_PREFS) ?: return null
        runCatching { JSONObject(raw) }.getOrNull()
    }

    fun saveGenerationPrefs(prefs: JSONObject): Boolean = synchronized(lock) {
        TvStore.putStringDurable(KEY_GENERATION_PREFS, prefs.toString())
    }

    /**
     * 答题范围与出题配置相互独立。保存字面量 "null" 表示用户显式选择“绑定出题范围”，
     * 与“从未初始化”区分开；后者在首次读取时同样按绑定出题范围处理。
     */
    fun hasAnswerScope(): Boolean = TvStore.getString(KEY_ANSWER_SCOPE) != null

    fun answerScopeIsBound(): Boolean = TvStore.getString(KEY_ANSWER_SCOPE) == "null"

    fun loadAnswerScope(): QuizAnswerScope? = synchronized(lock) {
        val raw = TvStore.getString(KEY_ANSWER_SCOPE) ?: return null
        if (raw == "null") return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        QuizAnswerScope.parse(json)
    }

    fun saveAnswerScope(scope: QuizAnswerScope?): Boolean = synchronized(lock) {
        TvStore.putStringDurable(KEY_ANSWER_SCOPE, scope?.encode()?.toString() ?: "null")
    }

    /**
     * The ordered answer levels saved by the parent. Null means the key is
     * absent or unreadable; callers use [hasAnswerScopes] to tell those apart
     * and only migrate when the list was never written.
     */
    fun loadAnswerScopes(): List<QuizLevel>? = synchronized(lock) {
        val raw = TvStore.getString(KEY_ANSWER_SCOPES) ?: return null
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        QuizLevel.parseList(array)
    }

    fun hasAnswerScopes(): Boolean = TvStore.getString(KEY_ANSWER_SCOPES) != null

    /**
     * Saves the list and mirrors its first scope into the legacy single-scope
     * key. Cloud reporting and old readers still see one scope; the new list
     * remains the source of truth for the TV and the web page.
     */
    fun saveAnswerScopes(levels: List<QuizLevel>): Boolean = synchronized(lock) {
        if (levels.size > QuizLevel.MAX_LEVELS) return false
        val listOk = TvStore.putStringDurable(
            KEY_ANSWER_SCOPES,
            QuizLevel.encodeList(levels).toString(),
        )
        val legacyOk = TvStore.putStringDurable(
            KEY_ANSWER_SCOPE,
            levels.firstOrNull()?.scope?.encode()?.toString() ?: "null",
        )
        listOk && legacyOk
    }

    fun clearProviderKey() {
        TvStore.removeDurable(KEY_PROVIDER_KEY)
        TvStore.removeDurable(KEY_PROVIDER_META)
    }

    fun readWebTokens(nowMs: Long = System.currentTimeMillis()): List<WebToken> {
        synchronized(lock) {
            val raw = TvStore.getString(KEY_WEB_TOKENS) ?: return emptyList()
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val digest = item.optString("digest", "")
                val expiresAt = item.optLong("expiresAt", 0L)
                val remaining = expiresAt - nowMs
                if (!WEB_TOKEN_RE.matches(digest) ||
                    remaining <= 0 || remaining > MAX_WEB_TOKEN_TTL_MS
                ) {
                    null
                } else {
                    WebToken(digest, expiresAt)
                }
            }
        }
    }

    /**
     * The single remembered parent session. Older builds could store several
     * tokens; the newest one owns the page now, so a second phone cannot edit
     * alongside the parent who entered first.
     */
    fun activeWebToken(nowMs: Long = System.currentTimeMillis()): WebToken? =
        readWebTokens(nowMs).lastOrNull()

    fun webTokenValid(digest: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!WEB_TOKEN_RE.matches(digest)) return false
        val active = activeWebToken(nowMs) ?: return false
        return MessageDigest.isEqual(active.digest.toByteArray(), digest.toByteArray())
    }

    fun saveWebToken(
        digest: String,
        expiresAt: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        synchronized(lock) {
            val remaining = expiresAt - nowMs
            if (!WEB_TOKEN_RE.matches(digest) ||
                remaining <= 0 || remaining > MAX_WEB_TOKEN_TTL_MS
            ) {
                return false
            }
            // One parent session at a time: a new login replaces the previous
            // remembered phone instead of adding a second editor.
            return persistWebTokens(listOf(WebToken(digest, expiresAt)))
        }
    }

    fun removeWebToken(
        digest: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        synchronized(lock) {
            val active = activeWebToken(nowMs) ?: return false
            if (!MessageDigest.isEqual(active.digest.toByteArray(), digest.toByteArray())) {
                return false
            }
            // Drop legacy extra tokens too; only this parent session was valid.
            return persistWebTokens(emptyList())
        }
    }

    private fun persistWebTokens(tokens: List<WebToken>): Boolean {
        val array = JSONArray()
        tokens.forEach { token ->
            array.put(JSONObject().put("digest", token.digest).put("expiresAt", token.expiresAt))
        }
        return TvStore.putStringDurable(KEY_WEB_TOKENS, array.toString())
    }

    fun consentAccepted(): Boolean = TvStore.getBoolean(KEY_CONSENT_V1, false)

    fun acceptConsent() {
        TvStore.putBoolean(KEY_CONSENT_V1, true)
        TvStore.sync()
    }

    /** Revoking consent also removes the provider key: it has no purpose without it. */
    fun revokeConsent() {
        TvStore.putBoolean(KEY_CONSENT_V1, false)
        clearProviderKey()
        TvStore.sync()
    }

    fun today(nowMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
}
