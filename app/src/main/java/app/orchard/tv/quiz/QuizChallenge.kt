package app.orchard.tv.quiz

import app.orchard.tv.policy.PolicyCloud
import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.policy.Activation
import com.planyourtv.policy.LockReason
import com.planyourtv.policy.PolicyFlowTrace
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.PolicyUiState
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.RemotePolicyMode
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.quiz.QUIZ_MAX_EARNED_MINUTES
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizRun
import com.planyourtv.policy.quiz.QuizScoring
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizSubmission
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.min

/**
 * The exam-like challenge state machine. It deliberately lives outside the policy
 * engines and outside the Aerial UI: the only integration point is the same
 * [PolicyModeCoordinator.startLocalManualSession] call the manual lock-screen
 * flow already uses. Every question in the round is answered, including after a
 * wrong answer. After each answer the round stays RUNNING with [QuizRun.feedbackOpen]
 * set, which pauses the next question; only [closeFeedback] resumes it, and the
 * last answer becomes SETTLING at that point so the child reads the feedback
 * before the positive report. The earned session starts only after
 * [completeReview]. Every answer is persisted before the next question so a
 * process restart cannot re-roll the round.
 */
object QuizChallenge {
    private const val TAG = "QuizChallenge"
    const val LOCAL_BANK_SESSION_SOURCE = "local-bank"
    private const val LOCAL_BANK_SESSION_TTL_MS = 7L * 24 * 60 * 60 * 1000L

    /** Single-flight for the hosted HTTP settlement; @Synchronized cannot wrap suspend. */
    private val hostedReviewMutex = Mutex()

    sealed interface BeginResult {
        data object Started : BeginResult
        data class Rejected(val code: String) : BeginResult
    }

    sealed interface AnswerOutcome {
        /** The answer is stored; feedback is now open and the round is paused. */
        data object Answered : AnswerOutcome
        data class Failed(val code: String) : AnswerOutcome
    }

    sealed interface FeedbackOutcome {
        /** Feedback is closed and the next question's full time has started. */
        data object Resumed : FeedbackOutcome
        /** The last answer has settled; the report screen now owns the round. */
        data class Finished(val result: Result) : FeedbackOutcome
        data class Failed(val code: String) : FeedbackOutcome
    }

    sealed interface ReviewOutcome {
        /** The earned session is running and the quiz record has been cleared. */
        data object Started : ReviewOutcome
        /** No time was earned; the quiz record has been cleared. */
        data object Cleared : ReviewOutcome
        /** The review is still readable and the policy engine refused to start. */
        data class Failed(val code: String) : ReviewOutcome
        /**
         * Hosted settlement was refused with the backend's user-facing reason and
         * the review is still retryable. [message] is safe to display verbatim.
         */
        data class Rejected(val message: String) : ReviewOutcome

        /** Stable label for logs; R8 renames the classes themselves in release builds. */
        fun logLabel(): String = when (this) {
            Started -> "STARTED"
            Cleared -> "CLEARED"
            is Failed -> "FAILED($code)"
            is Rejected -> "REJECTED"
        }
    }

    data class Result(
        val correct: Boolean,
        val timedOut: Boolean,
        val correctIndex: Int,
        val explanation: String,
        val correctCount: Int,
        val earnedMinutes: Int,
    )

    /**
     * Starts a round. Local rounds use [levelId] to pick one of the parent
     * designed levels; a null id falls back to the first level for old callers.
     * Cloud/hosted rounds ignore the id and keep their own scope.
     */
    @Synchronized
    fun start(
        nowMs: Long = System.currentTimeMillis(),
        levelId: String? = null,
    ): BeginResult {
        val policy = PolicyModeCoordinator.state.value
        // Unbound TV: locally produced bank. Bound TV: the cloud source. The
        // engine reserves only from the scope selected by the policy mode.
        // Legacy redemption is a manual grant too; it has no local window, so the
        // phone is the authority instead of LockReason.MANUAL_WAITING.
        val redeemManual = isBoundRedeem(policy)
        // A waiting manual grant (the phone precomputed the time for a redemption)
        // reports PENDING with lockReason NONE. It is still the same manual channel.
        val manualPending = policy.activation == Activation.MANUAL &&
            policy.sessionState == SessionState.PENDING
        val localMode = policy.mode == PolicyMode.LOCAL
        val hostedMode = policy.mode == PolicyMode.CLOUD &&
            (policy.source == PolicySource.HOSTED || redeemManual)
        val policyReady = (localMode || hostedMode) &&
            policy.activation == Activation.MANUAL &&
            !policy.noTimedApps &&
            (policy.lockReason == LockReason.MANUAL_WAITING || redeemManual ||
                manualPending) &&
            policy.sessionState != SessionState.ACTIVE
        PolicyFlowTrace.event(
            "quiz.start.check",
            "mode" to policy.mode,
            "source" to policy.source,
            "activation" to policy.activation,
            "session" to policy.sessionState,
            "lock" to policy.lockReason,
            "redeemManual" to redeemManual,
            "manualPending" to manualPending,
            "noTimedApps" to policy.noTimedApps,
            "ready" to policyReady,
        )
        if (!policyReady) {
            PolicyFlowTrace.event("quiz.start.reject", "reason" to "NOT_AVAILABLE")
            return BeginResult.Rejected("NOT_AVAILABLE")
        }

        if (localMode) {
            var state = QuizRuntime.refresh()
            val existing = state.session
            if (existing != null) {
                when (existing.status) {
                    QuizSessionStatus.READY -> {
                        if (existing.source == QbClient.CLOUD_SESSION_SOURCE) {
                            return BeginResult.Rejected("NOT_READY")
                        }
                        // A session left by the old publish flow becomes bank
                        // rows before the first bank-backed round.
                        if (!QuizRecovery.importLegacyLocalReady(nowMs)) {
                            return BeginResult.Rejected("NOT_READY")
                        }
                        state = QuizRuntime.refresh()
                    }
                    QuizSessionStatus.DRAFT -> {
                        QuizSessionStore.clearSession()
                        QuizRuntime.refresh()
                    }
                    QuizSessionStatus.RUNNING -> return BeginResult.Rejected("ALREADY_RUNNING")
                    else -> return BeginResult.Rejected("NOT_READY")
                }
            }
            return startLocalRound(nowMs, levelId)
        }

        val state = QuizRuntime.refresh()
        val session = state.session ?: return BeginResult.Rejected("NOT_READY")
        if (session.status != QuizSessionStatus.READY) {
            return BeginResult.Rejected(if (session.status == QuizSessionStatus.RUNNING) "ALREADY_RUNNING" else "NOT_READY")
        }
        if (session.expiresAt <= nowMs) {
            QuizQuestionCache.release(session.id)
            QuizSessionStore.clearSession()
            QuizRuntime.refresh()
            return BeginResult.Rejected("EXPIRED")
        }
        val scope = QuizRecovery.scopeFor(session, policy.deviceNo)
            ?: return BeginResult.Rejected("NOT_READY")

        // v2/v3 sessions predate the cache and keep the exact published list.
        // v4 cloud sessions are a pool: reserve the configured number from the
        // cloud scope without touching the local bank.
        val legacy = session.originSchemaVersion < 4
        val reserved: List<QuizQuestionCache.CachedQuestion> = if (legacy) {
            emptyList()
        } else {
            try {
                QuizQuestionCache.reserveForRound(
                    count = session.config.pullCount,
                    sessionId = session.id,
                    scopeKey = scope,
                    minRequired = 1,
                    nowMs = nowMs,
                )
            } catch (_: QuizQuestionCache.CacheException) {
                return BeginResult.Rejected("CACHE_UNAVAILABLE")
            }
        }
        if (!legacy && reserved.isEmpty()) return BeginResult.Rejected("CACHE_EMPTY")
        val publishedItems = if (legacy) {
            session.items
        } else {
            reserved.map { cached ->
                cached.question.copy(
                    cacheId = cached.cacheId,
                    sourceKind = cached.sourceKind,
                    sourceRef = cached.sourceRef.orEmpty(),
                )
            }
        }

        val today = QuizSessionStore.today(nowMs)
        val items = if (session.config.shuffleOptions) shuffleOptions(publishedItems) else publishedItems
        val startedRun = QuizRun(
            startedAt = nowMs,
            startedDate = today,
            index = 0,
            deadlineAt = nowMs + items[0].timeLimitSec * 1_000L,
            // Freeze the hosted config this round was started against. The
            // backend rejects a settlement whose version differs, so a parent
            // edit made mid-round cannot silently change the reward.
            configVersion = if (hostedMode) state.sourceConfig.configVersion else 0L,
        )
        val running = session.copy(
            status = QuizSessionStatus.RUNNING,
            items = items,
            run = startedRun,
            updatedAt = nowMs,
        )

        if (!QuizSessionStore.saveSession(running)) {
            if (!legacy) QuizQuestionCache.release(session.id)
            return BeginResult.Rejected("STORAGE")
        }
        // Cloud rounds must tell QB the attempt started (it burns the pull at
        // start). The queue persists first, so a lost process still closes it
        // on the next round instead of leaving a RUNNING attempt behind.
        cloudAttempt(session)?.let { attemptUid ->
            QuizCloudOutbox.markStarted(attemptUid)
            QuizCloudOutbox.flushAsync()
        }
        QuizRuntime.refresh()
        PolicyFlowTrace.event(
            "quiz.start.ok",
            "source" to (if (hostedMode) "CLOUD" else "LOCAL"),
            "questions" to items.size,
            "configVersion" to startedRun.configVersion,
        )
        return BeginResult.Started
    }

    /**
     * Local rounds are created at start time from the bank; there is no READY
     * session to publish. The reservation and the session write are paired so a
     * failed save cannot leave questions stranded in RESERVED.
     */
    private fun startLocalRound(nowMs: Long, levelId: String?): BeginResult {
        val config = QuizSessionStore.loadChallengeConfig() ?: QuizConfig()
        val level = QuizAnswerScopeStore.level(levelId)
        // An explicit level that disappeared while the dialog was open must not
        // silently start a different one.
        if (level == null) return BeginResult.Rejected("NO_LEVELS")
        val scope = QuizQuestionCache.LOCAL_SCOPE
        val answerScope = level.scope
        val unused = QuizQuestionCache.availableCount(scope, nowMs, answerScope)
        if (unused <= 0) return BeginResult.Rejected("LOCAL_BANK_EMPTY")
        val count = min(config.pullCount, unused)
        val roundId = QbClient.SESSION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "")
        val reserved = try {
            QuizQuestionCache.reserveForRound(
                count = count,
                sessionId = roundId,
                scopeKey = scope,
                minRequired = 1,
                nowMs = nowMs,
                answerScope = answerScope,
            )
        } catch (_: QuizQuestionCache.CacheException) {
            return BeginResult.Rejected("CACHE_UNAVAILABLE")
        }
        if (reserved.isEmpty()) return BeginResult.Rejected("LOCAL_BANK_INSUFFICIENT")

        val published = reserved.map { cached ->
            cached.question.copy(
                cacheId = cached.cacheId,
                sourceKind = cached.sourceKind,
                sourceRef = cached.sourceRef.orEmpty(),
                timeLimitSec = config.defaultTimeLimitSec,
                recommendedTimeSec = config.defaultTimeLimitSec,
            )
        }
        val items = if (config.shuffleOptions) shuffleOptions(published) else published
        if (items.isEmpty()) {
            QuizQuestionCache.settleRound(roundId, scope, emptyList(), nowMs)
            return BeginResult.Rejected("LOCAL_BANK_EMPTY")
        }
        // Keep the configured legacy field untouched; v5 ignores it. Only the
        // reserved count is frozen into the round snapshot.
        val runtimeConfig = config.copy(pullCount = items.size)
        val run = QuizRun(
            startedAt = nowMs,
            startedDate = QuizSessionStore.today(nowMs),
            index = 0,
            deadlineAt = nowMs + items[0].timeLimitSec * 1_000L,
        )
        val session = QuizSession(
            id = roundId,
            source = LOCAL_BANK_SESSION_SOURCE,
            createdAt = nowMs,
            updatedAt = nowMs,
            expiresAt = nowMs + LOCAL_BANK_SESSION_TTL_MS,
            status = QuizSessionStatus.RUNNING,
            language = "zh",
            config = runtimeConfig,
            items = items,
            run = run,
        )
        if (!QuizSessionStore.saveSession(session)) {
            QuizQuestionCache.settleRound(roundId, scope, emptyList(), nowMs)
            return BeginResult.Rejected("STORAGE")
        }
        QuizRuntime.refresh()
        PolicyFlowTrace.event(
            "quiz.start.ok",
            "source" to "LOCAL",
            "questions" to items.size,
        )
        return BeginResult.Started
    }

    @Synchronized
    fun answer(
        selectedIndex: Int?,
        timedOut: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): AnswerOutcome {
        val state = QuizRuntime.refresh()
        val session = state.session ?: return AnswerOutcome.Failed("NOT_RUNNING")
        if (session.status != QuizSessionStatus.RUNNING) return AnswerOutcome.Failed("NOT_RUNNING")
        val run = session.run ?: return AnswerOutcome.Failed("NOT_RUNNING")
        if (run.feedbackOpen) return AnswerOutcome.Failed("FEEDBACK_OPEN")
        val item = session.items.getOrNull(run.index) ?: return AnswerOutcome.Failed("STATE")
        // One answer per question, ever. This is the final guard behind the UI's
        // submitting flag: a timeout and a tap can race, but only the first call
        // can append a submission and therefore only one score applies.
        if (run.submissions.any { it.questionId == item.questionId }) {
            return AnswerOutcome.Failed("DUPLICATE_ANSWER")
        }
        // A tap that lands after the deadline must never beat the clock, even if
        // the 1 Hz UI timer has not fired yet.
        val expired = timedOut || nowMs >= run.deadlineAt
        if (!expired && (selectedIndex == null || selectedIndex !in item.options.indices)) {
            return AnswerOutcome.Failed("INVALID_ANSWER")
        }

        val correct = !expired && selectedIndex == item.answerIndex
        val submission = QuizSubmission(
            questionId = item.questionId,
            selectedIndex = if (expired) null else selectedIndex,
            correct = correct,
            timedOut = expired,
            answeredAt = nowMs,
        )
        // A wrong answer or timeout never ends the round. The child still gets
        // every remaining question, and the score is settled only after the last
        // feedback page closes. This keeps one mistake from throwing away the
        // whole attempt.
        val nextIndex = run.index + 1
        val nextItem = session.items.getOrNull(nextIndex)
        val nextRun = run.copy(
            index = nextIndex,
            correctCount = run.correctCount + if (correct) 1 else 0,
            earnedPoints = run.earnedPoints + if (correct) item.points else 0,
            // The whole next question's time is saved while feedback is open;
            // closeFeedback turns it into a fresh deadline starting then, so
            // reading an explanation never eats the next question's clock.
            feedbackOpen = true,
            feedbackRemainingMs = nextItem?.timeLimitSec?.times(1_000L) ?: 0L,
            submissions = run.submissions + submission,
        )
        val answered = session.copy(run = nextRun, updatedAt = nowMs)
        if (!QuizSessionStore.saveSession(answered)) return AnswerOutcome.Failed("STORAGE")
        // Local bank rows keep the per-question outcome immediately. Cloud
        // rows are updated in their own scope too, while the outbox uploads the
        // same submission to QB.
        QuizRecovery.scopeFor(session, PolicyModeCoordinator.state.value.deviceNo)?.let { cacheScope ->
            val storedIndex = submission.selectedIndex?.let { displayIndex ->
                if (item.optionOrder.isEmpty()) displayIndex else item.optionOrder.getOrNull(displayIndex)
            }
            QuizQuestionCache.writeAnswers(
                session.id,
                cacheScope,
                listOf(
                    QuizQuestionCache.AnswerWrite(
                        cacheId = item.cacheId,
                        questionId = item.questionId,
                        selectedIndex = storedIndex,
                        correct = correct,
                        timedOut = expired,
                        answeredAt = nowMs,
                    ),
                ),
                nowMs,
            )
        }
        // Queue the answer before showing feedback. The queue uploads in the
        // background; settlement flushes it again, so a slow network never eats
        // the child's earned time.
        cloudAttempt(session)?.let { attemptUid ->
            // QB judges in the published option order, while the TV may have
            // shown a shuffled order. Upload the original index; the submission
            // kept in the run stays in display space so the feedback page keeps
            // reading "你的选择" correctly.
            val displayIndex = submission.selectedIndex
            val cloudSubmission = submission.copy(
                selectedIndex = if (displayIndex == null || submission.timedOut) {
                    null
                } else if (item.optionOrder.isEmpty()) {
                    displayIndex
                } else {
                    item.optionOrder[displayIndex]
                },
            )
            QuizCloudOutbox.appendAnswer(attemptUid, cloudSubmission)
            QuizCloudOutbox.flushAsync()
        }
        QuizRuntime.refresh()
        return AnswerOutcome.Answered
    }

    /**
     * Closes the answer-feedback page. The next question is resumed only here,
     * after its full saved time is restored. On the last answer this settles the
     * round instead, which is what makes the report appear only after the child
     * has read the final explanation.
     */
    @Synchronized
    fun closeFeedback(nowMs: Long = System.currentTimeMillis()): FeedbackOutcome {
        val state = QuizRuntime.refresh()
        val session = state.session ?: return FeedbackOutcome.Failed("NOT_RUNNING")
        if (session.status != QuizSessionStatus.RUNNING) {
            return FeedbackOutcome.Failed("NOT_RUNNING")
        }
        val run = session.run ?: return FeedbackOutcome.Failed("NOT_RUNNING")
        if (!run.feedbackOpen) return FeedbackOutcome.Failed("STATE")
        val lastSubmission = run.submissions.lastOrNull()
            ?: return FeedbackOutcome.Failed("STATE")

        val nextItem = session.items.getOrNull(run.index)
        if (nextItem != null) {
            val remainingMs = run.feedbackRemainingMs.takeIf { it > 0L }
                ?: (nextItem.timeLimitSec * 1_000L)
            val resumed = session.copy(
                run = run.copy(
                    deadlineAt = nowMs + remainingMs,
                    feedbackOpen = false,
                    feedbackRemainingMs = 0L,
                ),
                updatedAt = nowMs,
            )
            if (!QuizSessionStore.saveSession(resumed)) return FeedbackOutcome.Failed("STORAGE")
            QuizRuntime.refresh()
            return FeedbackOutcome.Resumed
        }

        val config = session.config
        val earnedMinutes = when {
            // v5 local rounds score by difficulty and use a net-minute threshold.
            // Cloud rounds keep their backend-authoritative v4 formula; they may
            // carry schema 5 but must never be settled with local scoring.
            session.originSchemaVersion >= 5 && !isCloudSession(session) -> {
                QuizScoring.score(session.items, run.submissions, config).earnedMinutes
            }
            session.originSchemaVersion >= 4 -> {
                if (run.correctCount >= config.minCorrectCount) {
                    min(
                        run.correctCount * config.minutesPerQuestion,
                        min(config.maxEarnedMinutes, QUIZ_MAX_EARNED_MINUTES),
                    )
                } else {
                    0
                }
            }
            run.correctCount >= config.minCorrectCount -> {
                min(run.earnedPoints * config.minutesPerPoint, config.maxEarnedMinutes)
            }
            else -> 0
        }
        val settling = session.copy(
            status = QuizSessionStatus.SETTLING,
            run = run.copy(
                earnedMinutes = earnedMinutes,
                feedbackOpen = false,
                feedbackRemainingMs = 0L,
            ),
            updatedAt = nowMs,
        )
        if (!QuizSessionStore.saveSession(settling)) return FeedbackOutcome.Failed("STORAGE")
        QuizRecovery.scopeFor(session, PolicyModeCoordinator.state.value.deviceNo)?.let { cacheScope ->
            QuizQuestionCache.settleRound(
                roundId = session.id,
                scopeKey = cacheScope,
                answers = QuizRecovery.answerWrites(session),
                nowMs = nowMs,
            )
        }
        // All answers are in: close the cloud attempt now, so the wrong book is
        // written while the child reads the report and QB never keeps it RUNNING.
        cloudAttempt(session)?.let { attemptUid ->
            QuizCloudOutbox.markSubmit(attemptUid)
            QuizCloudOutbox.flushAsync()
        }
        QuizRuntime.refresh()
        val answeredItem = session.items.firstOrNull {
            it.questionId == lastSubmission.questionId
        } ?: session.items.last()
        return FeedbackOutcome.Finished(
            result(
                answeredItem,
                lastSubmission.correct,
                lastSubmission.timedOut,
                run.correctCount,
                earnedMinutes,
            ),
        )
    }

    /** Clears the round. The next tap starts from the bank again. */
    @Synchronized
    fun abandon(nowMs: Long = System.currentTimeMillis()): Boolean {
        val session = QuizRuntime.state.value.session
        session?.let {
            val cacheScope = QuizRecovery.scopeFor(it, PolicyModeCoordinator.state.value.deviceNo)
            if (cacheScope != null) {
                // Keep answers already given, return unreached questions to the
                // bank. This is the same rule as a normal finish.
                QuizQuestionCache.settleRound(
                    roundId = it.id,
                    scopeKey = cacheScope,
                    answers = QuizRecovery.answerWrites(it),
                    nowMs = nowMs,
                )
            }
            cloudAttempt(it)?.let { attemptUid ->
                QuizCloudOutbox.markAbandon(attemptUid)
                QuizCloudOutbox.flushAsync()
            }
            if (it.source == QbClient.CLOUD_SESSION_SOURCE) {
                // A cloud round is one cached attempt; never let its unused
                // questions leak into the next round. Local bank rows stay.
                if (cacheScope != null) {
                    QuizQuestionCache.clearUnconsumed(cacheScope)
                } else {
                    QuizQuestionCache.clearCloudScopes()
                }
            }
        }
        val cleared = QuizSessionStore.clearSession()
        QuizRuntime.refresh()
        return cleared
    }

    fun remainingSeconds(nowMs: Long = System.currentTimeMillis()): Long {
        val run = QuizRuntime.state.value.session?.run ?: return 0L
        return ((run.deadlineAt - nowMs + 999) / 1_000).coerceAtLeast(0L)
    }

    /**
     * Called by the report screen when the child is ready to continue. SETTLING
     * is first persisted as STARTING so a process death between the policy start
     * and clearing the record can be reconciled on the next launch.
     */
    @Synchronized
    fun completeReview(nowMs: Long = System.currentTimeMillis()): ReviewOutcome {
        val state = QuizRuntime.refresh()
        val session = state.session ?: return ReviewOutcome.Failed("NOT_READY")
        if (session.status != QuizSessionStatus.SETTLING &&
            session.status != QuizSessionStatus.STARTING &&
            session.status != QuizSessionStatus.START_FAILED
        ) {
            return ReviewOutcome.Failed("STATE")
        }
        val run = session.run ?: return ReviewOutcome.Failed("STATE")
        if (session.status == QuizSessionStatus.START_FAILED) {
            return ReviewOutcome.Failed(run.startErrorCode ?: "NOT_AVAILABLE")
        }
        if (run.earnedMinutes <= 0) {
            QuizSessionStore.clearSession()
            QuizRuntime.refresh()
            return ReviewOutcome.Cleared
        }

        val recovered = session.status == QuizSessionStatus.STARTING
        if (!recovered) {
            val starting = session.copy(
                status = QuizSessionStatus.STARTING,
                updatedAt = nowMs,
            )
            if (!QuizSessionStore.saveSession(starting)) return ReviewOutcome.Failed("STORAGE")
            // Do not refresh here: the UI is already showing the review, and
            // publishing STARTING would make it try to resume concurrently.
        }

        val startResult = runCatching {
            PolicyModeCoordinator.startLocalManualSession(run.earnedMinutes * 60L)
        }.getOrElse { SessionStartResult.Rejected(RejectReason.INVALID_COMMAND) }
        return when (startResult) {
            SessionStartResult.Started -> {
                QuizSessionStore.clearSession()
                QuizRuntime.refresh()
                ReviewOutcome.Started
            }
            // Local quiz reward starts are immediate; a pending remote arm is not a valid
            // outcome for this path, but keep the when exhaustive.
            SessionStartResult.Pending -> {
                QuizSessionStore.saveSession(
                    session.copy(
                        status = QuizSessionStatus.START_FAILED,
                        run = run.copy(startErrorCode = RejectReason.INVALID_COMMAND.name),
                        updatedAt = nowMs,
                    ),
                )
                QuizRuntime.refresh()
                ReviewOutcome.Failed(RejectReason.INVALID_COMMAND.name)
            }
            is SessionStartResult.Rejected -> {
                // A recovered STARTING record with BUSY means the previous
                // process already started the policy session and died before
                // clearing the quiz record. Treat it as success.
                if (recovered && startResult.reason == RejectReason.BUSY) {
                    QuizSessionStore.clearSession()
                    QuizRuntime.refresh()
                    return ReviewOutcome.Started
                }
                val code = startResult.reason.name
                QuizSessionStore.saveSession(
                    session.copy(
                        status = QuizSessionStatus.START_FAILED,
                        run = run.copy(startErrorCode = code),
                        updatedAt = nowMs,
                    ),
                )
                QuizRuntime.refresh()
                ReviewOutcome.Failed(code)
            }
        }
    }

    /** True when the TV is bound and its policy comes from the phone. */
    fun isHosted(): Boolean {
        val policy = PolicyModeCoordinator.state.value
        return policy.mode == PolicyMode.CLOUD &&
            (policy.source == PolicySource.HOSTED || isBoundRedeem(policy))
    }

    private fun isBoundRedeem(policy: PolicyUiState): Boolean =
        policy.mode == PolicyMode.CLOUD &&
            policy.bound &&
            policy.remoteMode == RemotePolicyMode.REDEEM

    /**
     * Settlement follows the round's own source, not the current binding state:
     * binding can change while a local round is still on screen.
     */
    fun isCloudSession(session: QuizSession): Boolean =
        session.source == QbClient.CLOUD_SESSION_SOURCE

    /**
     * Hosted counterpart of [completeReview]. The TV sends only the score and the
     * config version frozen at [start]; the backend recomputes the minutes from the
     * config it owns and dispatches the trusted manual session over MQTT. The
     * unbound path above is untouched: it has no cloud account to settle against.
     *
     * <p>Settlement is idempotent by session id, so a lost response is safe to
     * retry; only a definitive business refusal marks the round failed.
     */
    suspend fun completeReviewHosted(nowMs: Long = System.currentTimeMillis()): ReviewOutcome {
        PolicyFlowTrace.event("quiz.settle.begin")
        val outcome = hostedReviewMutex.withLock { settleHostedLocked(nowMs) }
        PolicyFlowTrace.event(
            "quiz.settle.result",
            "result" to outcome.logLabel(),
        )
        return outcome
    }

    private suspend fun settleHostedLocked(nowMs: Long): ReviewOutcome {
        val state = QuizRuntime.refresh()
        val session = state.session
        if (session == null) {
            PolicyFlowTrace.event("quiz.settle.skip", "reason" to "NO_SESSION")
            return ReviewOutcome.Failed("NOT_READY")
        }
        if (session.status != QuizSessionStatus.SETTLING &&
            session.status != QuizSessionStatus.STARTING &&
            session.status != QuizSessionStatus.START_FAILED
        ) {
            PolicyFlowTrace.event("quiz.settle.skip", "reason" to "STATE", "status" to session.status)
            return ReviewOutcome.Failed("STATE")
        }
        val run = session.run
        if (run == null) {
            PolicyFlowTrace.event("quiz.settle.skip", "reason" to "NO_RUN", "status" to session.status)
            return ReviewOutcome.Failed("STATE")
        }
        if (session.status == QuizSessionStatus.START_FAILED) {
            val code = run.startErrorCode ?: "NOT_AVAILABLE"
            PolicyFlowTrace.event(
                "quiz.settle.skip",
                "reason" to "ALREADY_FAILED",
                "code" to code,
                "detail" to run.startErrorMessage.orEmpty().take(120),
            )
            return ReviewOutcome.Failed(code)
        }
        PolicyFlowTrace.event(
            "quiz.settle.state",
            "status" to session.status,
            "correct" to run.correctCount,
            "total" to session.items.size,
            "earnedMinutes" to run.earnedMinutes,
            "configVersion" to run.configVersion,
        )
        if (run.earnedMinutes <= 0) {
            PolicyFlowTrace.event("quiz.settle.skip", "reason" to "NO_EARNED_TIME")
            QuizSessionStore.clearSession()
            QuizRuntime.refresh()
            return ReviewOutcome.Cleared
        }

        val deviceNo = PolicyModeCoordinator.state.value.deviceNo
        if (deviceNo.isBlank()) {
            PolicyFlowTrace.event("quiz.settle.skip", "reason" to "NO_DEVICE_BINDING")
            return ReviewOutcome.Failed("NOT_AVAILABLE")
        }

        // Finish the QB write-behind before paying out. The reward must not be
        // blocked by a slow QB, so a transport failure is retried in background.
        if (QuizCloudOutbox.hasPending()) {
            val flushed = QuizCloudOutbox.flushNow()
            if (!flushed) {
                Log.w(TAG, "cloud outbox incomplete before settlement; will retry")
                QuizCloudOutbox.flushAsync()
            }
        }

        if (session.status != QuizSessionStatus.STARTING) {
            val starting = session.copy(status = QuizSessionStatus.STARTING, updatedAt = nowMs)
            if (!QuizSessionStore.saveSession(starting)) return ReviewOutcome.Failed("STORAGE")
        }

        val settled = PolicyCloud.settleQuizEarned(
            deviceNo = deviceNo,
            sessionId = session.id,
            totalCount = session.items.size,
            correctCount = run.correctCount,
            configVersion = run.configVersion,
        )
        return settled.fold(
            onSuccess = { reward ->
                PolicyFlowTrace.event(
                    "quiz.settle.ok",
                    "correct" to run.correctCount,
                    "total" to session.items.size,
                    "earnedMinutes" to reward.earnedMinutes,
                    "alreadyGranted" to reward.alreadyGranted,
                )
                // The backend dispatches the trusted manual grant over MQTT.
                // Confirm it here so pressing the report's start button enters
                // timing directly instead of returning to the lock screen for
                // a second confirmation.
                confirmHostedStart()
                QuizSessionStore.clearSession()
                QuizRuntime.refresh()
                ReviewOutcome.Started
            },
            onFailure = { error ->
                PolicyFlowTrace.event(
                    "quiz.settle.fail",
                    "error" to PolicyCloud.errorDetail(error),
                    "retryable" to PolicyCloud.isRetryableSettlement(error),
                )
                val message = PolicyCloud.businessMessage(error)
                when {
                    message != null && PolicyCloud.isRetryableSettlement(error) -> {
                        // Another settlement is in flight; keep the round readable.
                        QuizSessionStore.saveSession(
                            session.copy(status = QuizSessionStatus.SETTLING, updatedAt = nowMs),
                        )
                        QuizRuntime.refresh()
                        ReviewOutcome.Rejected(message)
                    }
                    message != null -> {
                        val code = "SERVER_REJECTED"
                        QuizSessionStore.saveSession(
                            session.copy(
                                status = QuizSessionStatus.START_FAILED,
                                run = run.copy(
                                    startErrorCode = code,
                                    startErrorMessage = message.take(160),
                                ),
                                updatedAt = nowMs,
                            ),
                        )
                        QuizRuntime.refresh()
                        ReviewOutcome.Failed(code)
                    }
                    else -> {
                        // Transport/HTTP failure: the request may or may not have
                        // reached the backend, so retry with the same id is safe.
                        QuizSessionStore.saveSession(
                            session.copy(status = QuizSessionStatus.SETTLING, updatedAt = nowMs),
                        )
                        QuizRuntime.refresh()
                        ReviewOutcome.Failed("SETTLE_RETRY")
                    }
                }
            },
        )
    }

    /**
     * Confirms the hosted grant that this settlement just earned. The backend
     * dispatches the start command over MQTT and normally arms it before the
     * settlement response arrives, but the two channels can race; retry briefly
     * so one press on the report enters timing directly. A definitive rejection
     * is not retried: the lock screen keeps the pending gate for a manual start.
     */
    private suspend fun confirmHostedStart(): Boolean {
        repeat(20) {
            val result = runCatching { PolicyModeCoordinator.startSession() }
                .getOrElse {
                    SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
                }
            when (result) {
                SessionStartResult.Started -> {
                    PolicyFlowTrace.event("quiz.start.confirm", "result" to "STARTED")
                    return true
                }
                SessionStartResult.Pending -> Unit
                is SessionStartResult.Rejected -> {
                    if (result.reason != RejectReason.INVALID_COMMAND) {
                        PolicyFlowTrace.event(
                            "quiz.start.confirm",
                            "result" to "REJECTED",
                            "reason" to result.reason,
                        )
                        return false
                    }
                }
            }
            delay(100)
        }
        PolicyFlowTrace.event("quiz.start.confirm", "result" to "NO_PENDING")
        return false
    }

    /** Attempt id of a cloud round; null for locally published sessions. */
    private fun cloudAttempt(session: QuizSession): String? =
        if (session.source == QbClient.CLOUD_SESSION_SOURCE) {
            QuizCloudOutbox.attemptUidOf(session.id)
        } else {
            null
        }

    private fun result(
        item: QuizItem,
        correct: Boolean,
        timedOut: Boolean,
        correctCount: Int,
        earnedMinutes: Int,
    ) = Result(
        correct = correct,
        timedOut = timedOut,
        correctIndex = item.answerIndex,
        explanation = item.explanation,
        correctCount = correctCount,
        earnedMinutes = earnedMinutes,
    )

    private fun shuffleOptions(items: List<QuizItem>): List<QuizItem> = items.map { item ->
        val order = item.options.indices.shuffled()
        item.copy(
            options = order.map { item.options[it] },
            answerIndex = order.indexOf(item.answerIndex),
            // Display position -> published position, persisted with the session
            // so a process restart cannot lose the cloud upload mapping.
            optionOrder = order,
        )
    }
}
