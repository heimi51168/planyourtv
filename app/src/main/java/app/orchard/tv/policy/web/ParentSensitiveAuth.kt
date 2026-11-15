package app.orchard.tv.policy.web

import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.quiz.QuizAccess
import com.planyourtv.policy.PinResult
import java.util.Locale

/**
 * Short-lived proof that the parent re-entered the PIN for one sensitive page action.
 *
 * This is a third, deliberately narrow layer on top of the quiz login and the single-editor
 * lease: a phone that is already logged in can still be picked up by a child, so editing the
 * time rules and temporarily leaving child mode ask for the PIN again. The proof is not an
 * account or a second password; it is an in-memory capability:
 *
 * - only the SHA-256 digest of the token is stored, never the token itself;
 * - it is bound to the editor token digest and to one scope;
 * - RULES_EDIT lives five minutes and allows multiple saves, TAKE_OVER is single-use and
 *   expires after sixty seconds;
 * - logout, a new editor lease, or a mode change drops the stored proofs.
 *
 * PIN failures share [QuizAccess]'s sensitive-action limiter with the single timer, so five
 * wrong PINs lock every sensitive web action for ten minutes.
 */
internal object ParentSensitiveAuth {
    enum class Scope(val ttlMs: Long) {
        RULES_EDIT(5 * 60_000L),
        TAKE_OVER(60_000L);

        companion object {
            fun parse(value: String?): Scope? = entries.firstOrNull {
                it.name == value?.trim()?.uppercase(Locale.ROOT)
            }
        }
    }

    enum class Status { OK, REQUIRED, EXPIRED, SCOPE }

    sealed interface VerifyResult {
        data class Ok(
            val scope: Scope,
            val token: String,
            val expiresAtMs: Long,
        ) : VerifyResult

        data class Failed(
            val code: String,
            val retryAfterSeconds: Long = 0L,
        ) : VerifyResult
    }

    private data class TokenRecord(
        val scope: Scope,
        val editorDigest: String,
        val expiresAtMs: Long,
    )

    private const val MAX_TOKENS = 64
    private val tokens = LinkedHashMap<String, TokenRecord>()

    /**
     * Verifies [pin] for [scope] and issues a fresh token. The HTTP layer must already have
     * validated the editor lease; [editorToken] is only hashed for binding.
     */
    @Synchronized
    fun verify(
        scopeValue: String?,
        pin: String,
        editorToken: String,
        nowMs: Long = System.currentTimeMillis(),
    ): VerifyResult {
        val scope = Scope.parse(scopeValue)
            ?: return VerifyResult.Failed("INVALID_SCOPE")
        val wait = QuizAccess.sensitiveActionLockedSeconds(nowMs)
        if (wait > 0L) return VerifyResult.Failed("PIN_LOCKED", wait)
        if (pin.length != 6 || pin.any { !it.isDigit() }) {
            return VerifyResult.Failed("WRONG_PIN")
        }
        when (PolicyModeCoordinator.verifyPin(pin)) {
            PinResult.OK -> Unit
            PinResult.LOCKED_OUT -> {
                QuizAccess.lockSensitiveActionOut(nowMs)
                return VerifyResult.Failed(
                    "PIN_LOCKED",
                    QuizAccess.sensitiveActionLockedSeconds(nowMs),
                )
            }
            PinResult.WRONG, PinResult.NOT_SET -> {
                QuizAccess.onSensitiveActionResult(success = false, nowMs = nowMs)
                val locked = QuizAccess.sensitiveActionLockedSeconds(nowMs)
                return if (locked > 0L) {
                    VerifyResult.Failed("PIN_LOCKED", locked)
                } else {
                    VerifyResult.Failed("WRONG_PIN")
                }
            }
        }
        // A built-in PIN is correct but must not become a remote parent credential; the first
        // use has to change it on the TV. Do not touch the limiter: this is not a wrong guess.
        if (PolicyModeCoordinator.isDefaultPinValue(pin)) {
            return VerifyResult.Failed("DEFAULT_PIN")
        }
        QuizAccess.onSensitiveActionResult(success = true, nowMs = nowMs)
        sweep(nowMs)
        val token = ParentWebCrypto.randomToken()
        val expiresAt = nowMs + scope.ttlMs
        tokens[ParentWebCrypto.sha256Hex(token)] = TokenRecord(
            scope = scope,
            editorDigest = ParentWebCrypto.sha256Hex(editorToken),
            expiresAtMs = expiresAt,
        )
        trim()
        return VerifyResult.Ok(scope, token, expiresAt)
    }

    /** Checks a proof without consuming it; rules edits may save repeatedly inside the TTL. */
    @Synchronized
    fun check(
        token: String?,
        scope: Scope,
        editorToken: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Status = validate(token, scope, editorToken, nowMs)

    /**
     * Validates and removes a single-use proof. Call only after the guarded action succeeded,
     * so a cancelled confirmation or a retried command does not burn the parent's window.
     */
    @Synchronized
    fun consume(
        token: String?,
        scope: Scope,
        editorToken: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Status {
        val status = validate(token, scope, editorToken, nowMs)
        if (status == Status.OK && token != null) {
            tokens.remove(ParentWebCrypto.sha256Hex(token))
        }
        return status
    }

    /** Best-effort release of one proof from the page; wrong editor or token is a no-op. */
    @Synchronized
    fun release(token: String?, editorToken: String?) {
        if (token.isNullOrBlank() || editorToken.isNullOrBlank()) return
        val key = ParentWebCrypto.sha256Hex(token)
        val record = tokens[key] ?: return
        if (!ParentWebCrypto.digestEquals(
                record.editorDigest,
                ParentWebCrypto.sha256Hex(editorToken),
            )
        ) {
            return
        }
        tokens.remove(key)
    }

    /** Logout or an editor-lease change: every proof belonged to the old session. */
    @Synchronized
    fun releaseAll(editorToken: String?) {
        if (editorToken.isNullOrBlank()) return
        val digest = ParentWebCrypto.sha256Hex(editorToken)
        tokens.entries.removeAll { (_, record) ->
            ParentWebCrypto.digestEquals(record.editorDigest, digest)
        }
    }

    /** Mode or lease invalidation: there is no editor left that could use a proof. */
    @Synchronized
    fun invalidate() {
        tokens.clear()
    }

    @Synchronized
    fun resetForTest() {
        tokens.clear()
    }

    private fun validate(
        token: String?,
        scope: Scope,
        editorToken: String,
        nowMs: Long,
    ): Status {
        if (token.isNullOrBlank()) return Status.REQUIRED
        sweep(nowMs)
        val record = tokens[ParentWebCrypto.sha256Hex(token)] ?: return Status.EXPIRED
        if (record.scope != scope) return Status.SCOPE
        // A different editor token in the same browser is still a different session.
        if (!ParentWebCrypto.digestEquals(
                record.editorDigest,
                ParentWebCrypto.sha256Hex(editorToken),
            )
        ) {
            return Status.EXPIRED
        }
        return Status.OK
    }

    private fun sweep(nowMs: Long) {
        tokens.entries.removeAll { (_, record) -> record.expiresAtMs <= nowMs }
    }

    private fun trim() {
        while (tokens.size > MAX_TOKENS) {
            val first = tokens.keys.firstOrNull() ?: break
            tokens.remove(first)
        }
    }
}
