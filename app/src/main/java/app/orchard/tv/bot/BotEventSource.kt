package app.orchard.tv.bot

import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.quiz.QuizRuntime
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LockReason
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Turns coordinator/quiz state transitions into chat pushes. The engines
 * publish every second; this source keys on the enum fields only (the same
 * discipline as PolicyFlowTrace), so a countdown tick never fires a push.
 *
 * A bot-caused transition produces both the command audit and its event push;
 * that is intentional: the audit says who asked, the event confirms the TV
 * actually changed state.
 */
internal object BotEventSource {

    fun start(scope: CoroutineScope, onTimer: (String) -> Unit, onQuiz: (String) -> Unit): Job {
        // A throw inside any push path must never kill its collector job, or
        // pushes go silent for the rest of the process.
        val safeTimer: (String) -> Unit = { text ->
            runCatching { onTimer(text) }.onFailure {
                com.planyourtv.log.TvLog.w("BotEvent", "timer push failed", it)
            }
        }
        val safeQuiz: (String) -> Unit = { text ->
            runCatching { onQuiz(text) }.onFailure {
                com.planyourtv.log.TvLog.w("BotEvent", "quiz push failed", it)
            }
        }
        val collector = scope.launch(Dispatchers.Default) {
            launch { runCatching { collectPolicy(safeTimer) }.onFailure {
                com.planyourtv.log.TvLog.w("BotEvent", "policy collector died", it)
            } }
            launch { runCatching { collectQuiz(safeQuiz) }.onFailure {
                com.planyourtv.log.TvLog.w("BotEvent", "quiz collector died", it)
            } }
        }
        return collector
    }

    private suspend fun collectPolicy(onTimer: (String) -> Unit) {
        var seen: Snapshot? = null
        var prevRemainingSec = Long.MAX_VALUE
        PolicyModeCoordinator.state.collect { state ->
            if (!state.loaded) return@collect
            val now = Snapshot(
                mode = state.mode,
                session = state.sessionState,
                guard = state.guardState,
                study = state.studyModeActive,
                lock = state.lockReason,
            )
            val before = seen
            seen = now
            // The "5 minutes left" heads-up rides the same per-second emissions:
            // fire once when the countdown crosses the threshold, not every tick.
            if (BotBindingStore.remindEnabled() &&
                state.sessionState == SessionState.ACTIVE &&
                prevRemainingSec > REMIND_THRESHOLD_SECONDS &&
                state.effectiveRemainingSeconds in 1..REMIND_THRESHOLD_SECONDS
            ) {
                onTimer("⏳ 提醒：观看还剩 ${state.effectiveRemainingSeconds / 60 + 1} 分钟")
            }
            prevRemainingSec =
                if (state.sessionState == SessionState.ACTIVE) state.effectiveRemainingSeconds else Long.MAX_VALUE
            if (before == null || before == now) return@collect
            // Feed the daily rollup behind 日报/21:00 push: the day's absolute
            // watched total and one line per settled quiz round.
            if (state.dailyUsedSeconds > 0) {
                BotDailyReport.recordWatchedSeconds(state.dailyUsedSeconds)
            }
            transitions(before, now, state).forEach(onTimer)
        }
    }

    private data class Snapshot(
        val mode: PolicyMode,
        val session: SessionState,
        val guard: GuardStatus,
        val study: Boolean,
        val lock: LockReason,
    )

    private fun transitions(before: Snapshot, now: Snapshot, state: com.planyourtv.policy.PolicyUiState): List<String> {
        val events = ArrayList<String>(2)
        if (before.mode == PolicyMode.NONE && now.mode != PolicyMode.NONE) {
            events.add("🛡 儿童模式已开启")
        }
        if (before.session != SessionState.ACTIVE && now.session == SessionState.ACTIVE) {
            events.add("📺 观看开始 · 剩余 ${state.effectiveRemainingSeconds / 60 + 1} 分钟")
        }
        if (before.session == SessionState.ACTIVE && now.session != SessionState.ACTIVE) {
            events.add("⏰ 本次观看结束")
        }
        if (before.lock != LockReason.RESTING && now.lock == LockReason.RESTING) {
            events.add("☕ 进入强制休息 · 剩 ${state.restRemainingSeconds / 60 + 1} 分钟")
        }
        if (before.guard != GuardStatus.PARENT_OVERRIDE && now.guard == GuardStatus.PARENT_OVERRIDE) {
            events.add("🔓 电视已临时解锁（家长使用）")
        }
        if (before.guard == GuardStatus.PARENT_OVERRIDE && now.guard != GuardStatus.PARENT_OVERRIDE) {
            events.add("🔒 临时解锁结束，恢复锁定")
        }
        if (!before.study && now.study) events.add("📚 进入学习模式")
        if (before.study && !now.study) events.add("📚 退出学习模式")
        return events
    }

    private suspend fun collectQuiz(onQuiz: (String) -> Unit) {
        var seen: String? = null
        QuizRuntime.state.collect { state ->
            val now = state.sessionState
            val before = seen
            seen = now
            if (before == null || before == now) return@collect
            when (now) {
                "running" -> onQuiz("✏️ 孩子开始了答题（${state.questionCount} 题）")
                "settling" -> {
                    state.session?.run?.earnedMinutes?.let { earned ->
                        BotDailyReport.recordQuizRound(earned)
                    }
                    onQuiz(settledText(state))
                }
                else -> Unit
            }
        }
    }

    /** The settling round carries the score the report screen will show. */
    private fun settledText(state: QuizRuntime.UiState): String {
        val session = state.session ?: return "🎯 答题结束"
        val run = session.run
        val correct = run?.correctCount ?: 0
        val total = session.items.size.coerceAtLeast(correct)
        val earned = run?.earnedMinutes ?: 0
        // The ledger records at the child's confirm, which happens after this
        // push; include this round so the running total matches what parents
        // will later read from 今日.
        val earnedToday = app.orchard.tv.quiz.QuizEarnedStore.earnedToday() + earned
        val cap = app.orchard.tv.quiz.QuizEarnedStore.capMinutes()
        val running = if (cap > 0) "$earnedToday/$cap" else "$earnedToday"
        return if (earned > 0) {
            "🎯 答题完成：$total 题对 $correct 题，净得 $earned 分钟（答完确认后生效）\n今日答题累计 $running 分钟"
        } else {
            "🎯 答题完成：$total 题对 $correct 题，未获奖励\n今日答题累计 $running 分钟"
        }
    }

    private const val REMIND_THRESHOLD_SECONDS = 5 * 60L
}
