package app.orchard.tv.quiz

import android.util.Log
import app.orchard.tv.policy.PolicyCloud
import com.planyourtv.policy.PolicyFlowTrace
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Prepares one cloud round for the source-agnostic cache.
 *
 * The engine never calls QB. This is the only cloud question source: it makes
 * sure the previous attempt is closed, pulls the next set, writes it to
 * [QuizQuestionCache] and leaves a READY session whose id encodes the attempt.
 * A bound TV always goes through here; an unbound TV keeps the local QR flow.
 *
 * Reuse rule: if the existing READY session and the cache both belong to the
 * same unexpired attempt, the round is returned as-is. That makes retrying the
 * start button (or reopening the page) free instead of burning another pull.
 */
object CloudQuizPreparer {
    private const val TAG = "CloudQuizPreparer"
    private val prepareMutex = Mutex()

    sealed interface Outcome {
        data object Ready : Outcome
        /** [message] is safe to show verbatim; never contains server internals. */
        data class Unavailable(val message: String) : Outcome
    }

    suspend fun prepare(nowMs: Long = System.currentTimeMillis()): Outcome {
        PolicyFlowTrace.event("quiz.prepare.begin")
        val outcome = prepareMutex.withLock {
            withContext(Dispatchers.IO) {
                prepareLocked(nowMs)
            }
        }
        PolicyFlowTrace.event(
            "quiz.prepare.result",
            "result" to when (outcome) {
                CloudQuizPreparer.Outcome.Ready -> "READY"
                is CloudQuizPreparer.Outcome.Unavailable -> "UNAVAILABLE"
            },
        )
        return outcome
    }

    /**
     * Drops every cloud trace on this TV. Used when the family binding changes
     * or disappears. Only cloud scopes and a cloud session are touched: a local
     * bank (and a local round in progress) must survive binding changes.
     */
    fun discardCloudState(deviceNo: String? = null) {
        val session = QuizSessionStore.loadSession()
        val cloudSession = session?.takeIf { it.source == QbClient.CLOUD_SESSION_SOURCE }
        cloudSession?.let { QuizCloudOutbox.attemptUidOf(it.id)?.let(QuizCloudOutbox::reset) }
        QuizCloudOutbox.reset()
        if (deviceNo.isNullOrBlank()) {
            QuizQuestionCache.clearCloudScopes()
        } else {
            QuizQuestionCache.migrateLegacyCloudScope(deviceNo)
            QuizQuestionCache.clearBank(
                QuizQuestionCache.cloudScope(deviceNo),
                QuizQuestionCache.ClearMode.ALL,
            )
        }
        if (cloudSession != null) QuizSessionStore.clearSession()
        // Release reservations left by a removed cloud attempt, but keep the
        // active local round's rows reserved.
        QuizQuestionCache.releaseOrphans(QuizSessionStore.loadSession()?.id)
        QbClient.invalidateSession()
        QuizRuntime.refresh()
    }

    private suspend fun prepareLocked(nowMs: Long): Outcome {
        val cloud = PolicyRuntime.state.value
        if (!cloud.bound || cloud.deviceNo.isBlank()) {
            return Outcome.Unavailable("这台电视还没有绑定到家庭，请先在 App 里绑定")
        }

        // A lost round must be finished before QB accepts another pull.
        if (!QuizCloudOutbox.flushNow()) {
            return Outcome.Unavailable("正在同步上一轮答题，请稍后重试")
        }

        val snapshot = PolicyCloud.fetchConfig(cloud.deviceNo).getOrElse { error ->
            Log.w(TAG, "quiz config fetch failed", error)
            return Outcome.Unavailable("网络连接失败，请稍后重试")
        }
        // Challenge rules and the on/off switch live in the backend once bound.
        val applied = QuizConfigSync.applyRemote(snapshot.quiz)
        if (applied is QuizConfigSync.ApplyResult.Rejected) {
            Log.w(TAG, "remote quiz config rejected: ${applied.code}")
            return Outcome.Unavailable("答题设置暂时无法同步，请稍后重试")
        }
        if (!QuizSessionStore.enabled()) {
            return Outcome.Unavailable("家长还没有开启答题挑战")
        }

        val current = QuizRuntime.refresh()
        val session = current.session
        if (session != null && session.status != QuizSessionStatus.READY) {
            return Outcome.Unavailable("本轮答题还没有结束，请先完成或在电视上结束它")
        }

        val config = QuizSessionStore.loadChallengeConfig() ?: QuizConfig()
        val scope = QuizQuestionCache.cloudScope(cloud.deviceNo)
        QuizQuestionCache.migrateLegacyCloudScope(cloud.deviceNo)
        val cachedAttempt = QuizQuestionCache.cloudAttemptUid(scope)
        val sessionAttempt = session?.let { QuizCloudOutbox.attemptUidOf(it.id) }
        val reusable = session != null &&
            session.status == QuizSessionStatus.READY &&
            session.source == QbClient.CLOUD_SESSION_SOURCE &&
            cachedAttempt != null &&
            sessionAttempt == cachedAttempt &&
            session.expiresAt > nowMs &&
            QuizQuestionCache.availableCount(scope, nowMs) >= session.items.size
        if (reusable) {
            PolicyFlowTrace.event(
                "quiz.prepare.reuse",
                "questions" to session.items.size,
                "expiresIn" to ((session.expiresAt - nowMs) / 1_000L),
            )
            return Outcome.Ready
        }

        val previousAttempt = sessionAttempt ?: cachedAttempt
        if (previousAttempt != null) {
            val failure = abandonPrevious(previousAttempt)
            if (failure != null) {
                Log.w(TAG, "previous cloud attempt could not be closed: ${failure.message}")
                return Outcome.Unavailable("上一轮答题还没有结束，请检查网络后重试")
            }
        }
        QuizCloudOutbox.reset()
        QuizQuestionCache.clearUnconsumed(scope)
        QuizSessionStore.clearSession()

        val set = try {
            QbClient.fetchNext(config.pullCount)
        } catch (error: QbClient.QbException) {
            val message = error.businessMessage
            Log.w(TAG, "QB refused the next set: ${error.message}")
            return Outcome.Unavailable(message ?: "题目获取失败，请稍后重试")
        } catch (error: IOException) {
            // Protocol or transport failure: details stay in the log, never on screen.
            Log.w(TAG, "QB next failed", error)
            return Outcome.Unavailable("题目获取失败，请检查网络后重试")
        }

        if (!QuizQuestionCache.replaceCloudSet(set.attemptUid, set.items, set.expiresAt, scope, nowMs)) {
            return Outcome.Unavailable("题目保存失败，请重试")
        }
        val prepared = QuizSession(
            id = QbClient.SESSION_ID_PREFIX + set.attemptUid,
            source = QbClient.CLOUD_SESSION_SOURCE,
            createdAt = nowMs,
            updatedAt = nowMs,
            expiresAt = set.expiresAt,
            status = QuizSessionStatus.READY,
            config = config,
            items = set.items,
        )
        if (!QuizSessionStore.saveSession(prepared)) {
            // Compensation: the cache must not outlive a session that cannot be read.
            QuizQuestionCache.clearUnconsumed(scope)
            return Outcome.Unavailable("题目保存失败，请重试")
        }
        QuizCloudOutbox.begin(set.attemptUid)
        QuizRuntime.refresh()
        PolicyFlowTrace.event(
            "quiz.prepare.fetched",
            "attempt" to set.attemptUid,
            "questions" to set.items.size,
            "requested" to set.requested,
            "available" to set.available,
        )
        Log.i(
            TAG,
            "cloud round prepared: attempt=${set.attemptUid} questions=${set.items.size} " +
                "requested=${set.requested} available=${set.available}",
        )
        return Outcome.Ready
    }

    /**
     * Closes the cached attempt. A terminal rejection means the server already
     * considers it finished; only a transport error is worth retrying.
     */
    private fun abandonPrevious(attemptUid: String): QbClient.QbException? {
        return try {
            QbClient.abandonAttempt(attemptUid)
            null
        } catch (error: QbClient.QbException) {
            val terminal = error.httpCode == 404 || error.apiCode == 404 ||
                error.httpCode == 409 || error.apiCode == 409
            if (terminal) {
                Log.i(TAG, "cached attempt $attemptUid already closed: ${error.message}")
                null
            } else {
                error
            }
        } catch (error: IOException) {
            Log.w(TAG, "cached attempt $attemptUid abandon failed", error)
            // Surface a retryable state; a plain IOException cannot carry a message
            // to the UI, so reuse the same neutral wording.
            QbClient.QbException(0, 0, error.message.orEmpty())
        }
    }
}
