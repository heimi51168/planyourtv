package app.orchard.tv.quiz

import android.util.Log
import com.planyourtv.policy.PolicyFlowTrace
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizVisual
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
 * The reuse also requires the same QCD revision, day profile and level, so a
 * parent edit made before the first tap is never executed with stale rules.
 */
object CloudQuizPreparer {
    private const val TAG = "CloudQuizPreparer"
    private const val VISUAL_DOWNLOAD_CONCURRENCY = 4
    private val prepareMutex = Mutex()

    sealed interface Outcome {
        data object Ready : Outcome
        /** [message] is safe to show verbatim; never contains server internals. */
        data class Unavailable(val message: String) : Outcome
    }

    /**
     * Fetches one round from the cloud bank for [levelId] (a QCD `cloud:<id>`).
     * The active QCD supplies the rules and the reward profile; the QB level id
     * provides the question scope. There is deliberately no fallback to the
     * local document or the legacy device config.
     */
    suspend fun prepare(
        levelId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Outcome {
        PolicyFlowTrace.event("quiz.prepare.begin")
        val outcome = prepareMutex.withLock {
            withContext(Dispatchers.IO) {
                prepareLocked(levelId, nowMs)
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
        if (cloudSession != null) {
            // Stop the current cloud utterance before its attempt disappears.
            QuizSpeechPlayer.stop()
            QuizSessionStore.clearSession()
        }
        QuizVisualAssetStore.clearNamespace(QuizVisualAssetStore.QB_FIGURE_ASSET_ID)
        // Cloud audio is derived from the cloud attempt; a local bank never
        // writes here, so clearing it cannot affect local speech.
        QuizTtsCache.clear()
        // Release reservations left by a removed cloud attempt, but keep the
        // active local round's rows reserved.
        QuizQuestionCache.releaseOrphans(QuizSessionStore.loadSession()?.id)
        QbClient.invalidateSession()
        QuizRuntime.refresh()
    }

    private suspend fun prepareLocked(levelId: String, nowMs: Long): Outcome {
        val cloud = PolicyRuntime.state.value
        if (!cloud.bound || cloud.deviceNo.isBlank()) {
            return Outcome.Unavailable("这台电视还没有绑定到家庭，请先在 App 里绑定")
        }

        // The active cloud QCD is the only rule source once bound. A missing or
        // unsupported document is an explicit refusal, never a local fallback.
        val plan = when (val planned = QuizCloudRoundPlanner.plan(nowMs)) {
            is QuizCloudPlanResult.Ready -> planned.plan
            is QuizCloudPlanResult.Rejected -> {
                Log.w(TAG, "cloud quiz plan rejected: ${planned.code}")
                return Outcome.Unavailable(cloudPlanMessage(planned.code))
            }
        }
        if (plan.checkinBlocked) {
            return Outcome.Unavailable(
                plan.checkinMessage ?: "今天的打卡任务还没有完成，先完成打卡再来答题吧",
            )
        }
        val selected = plan.levels.firstOrNull { it.id == levelId }
            ?: return Outcome.Unavailable("这个关卡已经更新，请返回重新选择")
        if (selected.available <= 0) {
            return Outcome.Unavailable(
                selected.unavailableReason ?: "这个关卡暂时没有可用的题目，请换一关试试",
            )
        }

        // A lost round must be finished before QB accepts another pull.
        if (!QuizCloudOutbox.flushNow()) {
            return Outcome.Unavailable("正在同步上一轮答题，请稍后重试")
        }

        val document = plan.document
        val roundConfig = plan.config.copy(pullCount = selected.questionCount)

        val current = QuizRuntime.refresh()
        val session = current.session
        if (session != null && session.status != QuizSessionStatus.READY) {
            return Outcome.Unavailable("本轮答题还没有结束，请先完成或在电视上结束它")
        }

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
            QuizQuestionCache.availableCount(scope, nowMs) >= session.items.size &&
            QuizVisualAssetStore.hasAllAssets(session.items) &&
            // Freeze match: a parent edit or a different level fetches anew so
            // the round never executes with rules the child did not see.
            session.cloudMemberId == plan.memberId &&
            session.qbLearnerId == plan.qbLearnerId &&
            session.preparedLevelId == selected.id &&
            session.docRevision == document.revision &&
            session.docEtag == document.etag &&
            session.levelsEtag == document.levelsEtag &&
            session.dayType == plan.dayType.name &&
            session.rewardProfile == plan.profile &&
            session.config == roundConfig
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
            QbClient.fetchNext(selected.questionCount, selected.qbLevelId)
        } catch (error: QbClient.QbException) {
            val message = error.businessMessage
            Log.w(TAG, "QB refused the next set: ${error.message}")
            return Outcome.Unavailable(message ?: "题目获取失败，请稍后重试")
        } catch (error: IOException) {
            // Protocol or transport failure: details stay in the log, never on screen.
            Log.w(TAG, "QB next failed", error)
            return Outcome.Unavailable("题目获取失败，请检查网络后重试")
        }

        // Figures are prefetched before the cache/session are written. A failed
        // picture fails the whole round explicitly: a "如图" question without
        // its figure must never reach the child.
        val visualItems = when (val result = attachVisuals(set)) {
            is VisualAttachResult.Ready -> result.items
            is VisualAttachResult.Failed -> {
                Log.w(TAG, "question figure failed: ${result.reason}")
                PolicyFlowTrace.event("quiz.prepare.visual_failed", "reason" to result.reason)
                return Outcome.Unavailable("题目图片下载失败，请检查网络后重试")
            }
        }

        // The member config owns the answer window. QB's per-question timer is
        // the question's own default and must not override the parent setting.
        val timeLimitSec = document.rules.timeLimitSec
        val items = visualItems.map {
            it.copy(timeLimitSec = timeLimitSec, recommendedTimeSec = timeLimitSec)
        }
        if (!QuizQuestionCache.replaceCloudSet(set.attemptUid, items, set.expiresAt, scope, nowMs)) {
            return Outcome.Unavailable("题目保存失败，请重试")
        }
        val prepared = QuizSession(
            id = QbClient.SESSION_ID_PREFIX + set.attemptUid,
            source = QbClient.CLOUD_SESSION_SOURCE,
            createdAt = nowMs,
            updatedAt = nowMs,
            expiresAt = set.expiresAt,
            status = QuizSessionStatus.READY,
            config = roundConfig,
            docRevision = document.revision,
            docEtag = document.etag,
            levelsEtag = document.levelsEtag,
            dayType = plan.dayType.name,
            rewardProfile = plan.profile,
            cloudMemberId = plan.memberId,
            qbLearnerId = plan.qbLearnerId,
            preparedLevelId = selected.id,
            items = items,
        )
        if (!QuizSessionStore.saveSession(prepared)) {
            // Compensation: the cache must not outlive a session that cannot be read.
            QuizQuestionCache.clearUnconsumed(scope)
            return Outcome.Unavailable("题目保存失败，请重试")
        }
        QuizCloudOutbox.begin(set.attemptUid)
        runCatching {
            QuizVisualAssetStore.prune(QuizVisualAssetStore.protectedShas(items))
        }.onFailure { Log.w(TAG, "question figure cache cleanup failed", it) }
        QuizRuntime.refresh()
        PolicyFlowTrace.event(
            "quiz.prepare.fetched",
            "attempt" to set.attemptUid,
            "level" to selected.id,
            "questions" to items.size,
            "requested" to set.requested,
            "available" to set.available,
        )
        Log.i(
            TAG,
            "cloud round prepared: attempt=${set.attemptUid} level=${selected.id} " +
                "questions=${items.size} requested=${set.requested} " +
                "available=${set.available} revision=${document.revision}",
        )
        return Outcome.Ready
    }

    private sealed interface VisualAttachResult {
        data class Ready(val items: List<QuizItem>) : VisualAttachResult
        data class Failed(val reason: String) : VisualAttachResult
    }

    private suspend fun attachVisuals(
        set: QbClient.CloudQuestionSet,
    ): VisualAttachResult {
        if (set.imageUrls.isEmpty()) return VisualAttachResult.Ready(set.items)
        val semaphore = Semaphore(VISUAL_DOWNLOAD_CONCURRENCY)
        val outcomes = coroutineScope {
            set.items.map { item ->
                async {
                    val url = set.imageUrls[item.questionId]
                        ?: return@async Result.success(item)
                    semaphore.withPermit { downloadVisual(item, url) }
                }
            }.awaitAll()
        }
        val failure = outcomes.firstOrNull { it.isFailure }?.exceptionOrNull()
        if (failure != null) {
            // Never put a URL or a signature into the log or the trace.
            return VisualAttachResult.Failed(failure.javaClass.simpleName)
        }
        return VisualAttachResult.Ready(outcomes.mapNotNull { it.getOrNull() })
    }

    private fun downloadVisual(item: QuizItem, url: String): Result<QuizItem> = try {
        QuizVisualAssetStore.fetchImage(QuizVisualAssetStore.QB_FIGURE_ASSET_ID, url)
            .map { stored -> item.copy(visual = QuizVisual(rich = stored.visual)) }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
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

/**
 * Human copy for a rejected cloud plan. The code itself stays internal; screens
 * show this text and never the raw code.
 */
fun cloudPlanMessage(code: String): String = when (code) {
    QuizCloudRoundPlanner.NO_ACTIVE_DOCUMENT,
    QuizCloudRoundPlanner.CLOUD_ENVELOPE_MISSING,
    -> "答题设置还没有同步到电视，请连接网络稍等一会儿"

    QuizCloudRoundPlanner.DISABLED -> "家长还没有开启答题闯关"

    QuizCloudRoundPlanner.QUESTION_COUNT_UNSUPPORTED ->
        "答题题目数量超出电视支持范围，请让家长调整"

    QuizCloudRoundPlanner.LEVEL_STATUS_MISSING,
    QuizCloudRoundPlanner.LEVEL_ORIGIN_INVALID,
    -> "关卡信息还没有同步完成，请稍后重试"

    QuizCloudRoundPlanner.MEMBER_UNKNOWN -> "答题设置缺少孩子信息，请家长先在 App 里选择孩子"

    QuizCloudRoundPlanner.MEMBER_SWITCH_PENDING ->
        "正在切换孩子的答题题库，请稍等几秒再试"

    QuizCloudRoundPlanner.ADULT_UNSUPPORTED ->
        "家长账号暂时没有电视题库，请切换到孩子后再答题"

    else -> "答题设置暂时不可用，请让家长在 App 里重新保存一次"
}
