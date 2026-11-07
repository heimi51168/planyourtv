package app.orchard.tv.quiz

import com.planyourtv.policy.quiz.QuizSource

/**
 * A source only knows how to make questions available in the cache. The engine
 * never calls this interface and never sees [QuizSource].
 */
interface QuizQuestionSource {
    val kind: QuizSource

    /** Called from the quiz entry point, never from the answer loop. */
    fun ensureAvailable(count: Int, nowMs: Long): SourceResult
}

sealed interface SourceResult {
    /** The cache already has at least [SourceResult.Ready.available] questions. */
    data class Ready(val available: Int) : SourceResult
    /** The source added questions but the cache still has fewer than requested. */
    data class Partial(val available: Int) : SourceResult
    /** The source is on and reachable, but has no usable question. */
    data class Empty(val code: String) : SourceResult
    /** The source cannot be used right now; the caller shows [code] verbatim as a reason key. */
    data class Unavailable(val code: String) : SourceResult
}

/**
 * Local questions are pushed by the local web publisher. This source never
 * generates or fetches anything on its own: opening the quiz must not spend an
 * AI key or hide an empty local bank behind automatic generation.
 */
object LocalQuestionSource : QuizQuestionSource {
    override val kind: QuizSource = QuizSource.LOCAL

    override fun ensureAvailable(count: Int, nowMs: Long): SourceResult {
        val available = QuizQuestionCache.availableCount(nowMs = nowMs)
        return when {
            available <= 0 -> SourceResult.Empty("LOCAL_BANK_EMPTY")
            available < count -> SourceResult.Partial(available)
            else -> SourceResult.Ready(available)
        }
    }
}

object QuizSourceCoordinator {
    fun ensureAvailable(count: Int, nowMs: Long = System.currentTimeMillis()): SourceResult {
        val config = QuizSessionStore.loadSourceConfig()
        if (!config.enabled) return SourceResult.Unavailable("SOURCE_DISABLED")
        return when (config.source) {
            QuizSource.LOCAL -> LocalQuestionSource.ensureAvailable(count, nowMs)
            // The cloud adapter is an optional module. A build without it must
            // say so explicitly instead of silently using the local bank.
            QuizSource.CLOUD -> SourceResult.Unavailable("SOURCE_UNSUPPORTED")
        }
    }

    fun currentSource(): QuizSource = QuizSessionStore.loadSourceConfig().source
}
