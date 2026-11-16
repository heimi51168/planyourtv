package app.orchard.tv.bot

import app.orchard.tv.BuildConfig
import com.planyourtv.log.TvLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One poll loop per binding. Each binding keeps its own cursor and its own
 * backoff, so one parent's bad network never slows another's commands; the
 * protocol's -14 pause is likewise per binding.
 *
 * Inbound flow per message: dedup -> whitelist (the bound user only) ->
 * parse -> gate -> run under the one global command lock -> reply. Replies
 * reuse the message's context_token, which is also persisted so later pushes
 * can be sent without waiting for the parent to write again.
 */
internal class BotChannel(
    private val runtime: BotRuntime,
    val binding: BotBinding,
    private val scope: CoroutineScope,
) {
    private val api = WeixinClawApi
    private var pausedUntilMs = 0L

    /**
     * An AI-parsed control command waiting for the parent's Y. Memory-only and
     * short-lived: any other message or the TTL drops it.
     */
    private val pendingConfirm = HashMap<String, Pair<BotCommand, Long>>()

    /**
     * The sender's last undoable action: the command, a closure that reverses
     * it (capturing any previous value it overwrote) and when it ran. 60
     * seconds of grace for a misfired 加时/解锁; a new action replaces it.
     */
    private data class Undoable(
        val command: BotCommand,
        val inverse: (() -> BotActions.Outcome)?,
        val atMs: Long,
    )

    private var lastUndoable: Undoable? = null

    fun start(): Job = scope.launch(Dispatchers.IO) {
        var backoffMs = INITIAL_BACKOFF_MS
        var consecutiveRejected = 0
        while (isActive) {
            if (System.currentTimeMillis() < pausedUntilMs) {
                delay(POLL_PAUSE_CHECK_MS)
                continue
            }
            val current = BotBindingStore.find(binding.id) ?: break
            // The loop must survive anything one iteration can throw (storage,
            // an odd response, a crash inside a command): catch, log, keep
            // polling. Before this guard a single unexpected exception killed
            // the channel coroutine and the bot stayed silent until the next
            // app restart — the "no reply after boot" symptom.
            try {
                when (
                    val page = api.getUpdates(
                        transport = runtime.transport,
                        baseUrl = current.baseUrl,
                        botToken = current.botToken,
                        channelVersion = runtime.channelVersion,
                        cursor = current.cursor,
                    )
                ) {
                    is UpdatesResult.Ok -> {
                        backoffMs = INITIAL_BACKOFF_MS
                        consecutiveRejected = 0
                        BotBindingStore.updateCursor(binding.id, page.cursor)
                        for (message in page.messages) {
                            // One poison message must not stop the rest of the page.
                            runCatching { deliver(current, message) }
                                .onFailure { TvLog.w(TAG, "deliver failed for ${binding.id}", it) }
                        }
                        // Loop immediately: the server holds the next call ~35s.
                    }
                    UpdatesResult.PauseOneHour -> {
                        // Per the protocol, keep this binding quiet for an hour.
                        pausedUntilMs = System.currentTimeMillis() + PAUSE_ONE_HOUR_MS
                        consecutiveRejected = 0
                    }
                    UpdatesResult.Rejected -> {
                        consecutiveRejected++
                        TvLog.w(TAG, "getupdates rejected for ${binding.id} (#$consecutiveRejected)")
                        // A stale cursor can be rejected forever; after a streak,
                        // drop it and re-sync from now — the gate's dedup absorbs
                        // whatever the server redelivers.
                        if (consecutiveRejected >= REJECTED_BEFORE_RESYNC) {
                            TvLog.w(TAG, "resetting cursor for ${binding.id} after rejection streak")
                            BotBindingStore.updateCursor(binding.id, "")
                            consecutiveRejected = 0
                            backoffMs = INITIAL_BACKOFF_MS
                            continue
                        }
                        delay(backoffMs)
                        backoffMs = nextBackoffMs(UpdatesResult.Rejected, backoffMs)
                    }
                    UpdatesResult.NetworkError -> {
                        // Ordinary while the TV's network comes up after boot.
                        delay(backoffMs)
                        backoffMs = nextBackoffMs(UpdatesResult.NetworkError, backoffMs)
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                TvLog.w(TAG, "poll iteration crashed for ${binding.id}; continuing", error)
                delay(EXCEPTION_BACKOFF_MS)
                backoffMs = INITIAL_BACKOFF_MS
            }
        }
    }

    /** One inbound message through the admission pipeline. */
    private fun deliver(binding: BotBinding, message: InboundMessage) {
        // Hard whitelist: only the bound account's messages are ever parsed.
        if (message.fromUserId != binding.userId) return
        if (!runtime.gate.admitMessage(binding.id, message.messageId)) return

        val firstContact = binding.contextToken.isEmpty() && message.contextToken.isNotEmpty()
        if (message.contextToken.isNotEmpty()) {
            BotBindingStore.updateContextToken(binding.id, message.contextToken)
        }
        BotBindingStore.updateLastSeen(binding.id, message.createTimeMs.let { if (it > 0) it else System.currentTimeMillis() })
        if (firstContact) {
            sendReply(message.contextToken, WELCOME_TEXT)
            runtime.flushPendingPushes(binding.id, message.contextToken)
        }
        if (message.text.isBlank()) return
        if (!runtime.gate.admitRate(binding.id)) {
            sendReply(message.contextToken, "操作太频繁了，请稍后再试。")
            return
        }

        if (handleConfirm(binding, message)) return

        when (val parsed = BotCommandParser.parse(message.text)) {
            is ParseResult.Exact -> {
                if (parsed.command is BotCommand.Unbind) {
                    handleUnbind(binding, message)
                    return
                }
                if (parsed.command is BotCommand.Undo) {
                    handleUndo(binding, message)
                    return
                }
                // Commands with outlasting effects (mode switch) mirror the TV's
                // confirm dialog: one Y here, through the same pending flow.
                if (parsed.command.needsConfirm) {
                    pendingConfirm[binding.id] = parsed.command to
                        System.currentTimeMillis() + CONFIRM_TTL_MS
                    sendReply(
                        message.contextToken,
                        "即将执行：${BotCommandParser.canonical(parsed.command)}。" +
                            "切换会结束当前观看段落并改变计时方式。回复 Y 确认，其他消息取消。",
                    )
                    return
                }
                val outcome = dispatch(binding, parsed.command)
                sendReply(message.contextToken, outcome.text)
                outcome.audit?.let { runtime.broadcastAudit(it) }
            }
            is ParseResult.Suggest -> sendReply(
                message.contextToken,
                "没听懂「${parsed.echo}」。你是不是想：${parsed.candidates.joinToString(" / ")}？请直接回复对应指令",
            )
            is ParseResult.NeedArg -> sendReply(message.contextToken, parsed.hint)
            ParseResult.Unknown -> handleUnknown(binding, message)
        }
    }

    /**
     * Y/N for a pending AI proposal. Consumes the message when a proposal was
     * waiting; otherwise falls through to normal parsing.
     */
    private fun handleConfirm(binding: BotBinding, message: InboundMessage): Boolean {
        val pending = pendingConfirm[binding.id] ?: return false
        val (command, expiresAt) = pending
        if (System.currentTimeMillis() > expiresAt) {
            pendingConfirm.remove(binding.id)
            return false
        }
        return when (BotCommandParser.normalize(message.text)) {
            in CONFIRM_WORDS -> {
                pendingConfirm.remove(binding.id)
                val outcome = dispatch(binding, command)
                sendReply(message.contextToken, outcome.text)
                outcome.audit?.let { runtime.broadcastAudit(it) }
                true
            }
            in CANCEL_WORDS -> {
                pendingConfirm.remove(binding.id)
                sendReply(message.contextToken, "已取消。")
                true
            }
            else -> {
                // A new message replaces the stale proposal.
                pendingConfirm.remove(binding.id)
                false
            }
        }
    }

    /**
     * Deterministic layers produced nothing: try the parent's own model, and
     * gate the result the design requires — queries run at once, controls need
     * one Y, and anything off-whitelist stays a help reply.
     */
    private fun handleUnknown(binding: BotBinding, message: InboundMessage) {
        val aiCommand = if (BotAiParser.available()) {
            runCatching { BotAiParser.parse(message.text) }.getOrNull()
        } else {
            null
        }
        if (aiCommand == null) {
            sendReply(message.contextToken, BotActions.HELP_TEXT)
            return
        }
        if (!aiCommand.isControl) {
            val outcome = dispatch(binding, aiCommand)
            sendReply(message.contextToken, outcome.text)
            outcome.audit?.let { runtime.broadcastAudit(it) }
            return
        }
        pendingConfirm[binding.id] = aiCommand to System.currentTimeMillis() + CONFIRM_TTL_MS
        sendReply(
            message.contextToken,
            "我理解你想：${BotCommandParser.canonical(aiCommand)}。回复 Y 确认执行，其他消息取消。",
        )
    }

    /**
     * Executes one command under the global lock. Control commands are audited
     * whether the TV accepted them or not; a successful undoable action is kept
     * for [handleUndo].
     */
    private fun dispatch(binding: BotBinding, command: BotCommand): BotActions.Outcome {
        val actor = nameOf(binding)
        val outcome = runtime.gate.runExclusive {
            BotActions.handle(command, actor)
        }
        if (command.isControl) {
            BotAuditLog.append(
                BotAuditLog.Entry(
                    atMs = System.currentTimeMillis(),
                    actor = actor,
                    command = BotCommandParser.canonical(command),
                    accepted = outcome.audit != null,
                    detail = outcome.text.lineSequence().firstOrNull().orEmpty(),
                ),
            )
        }
        if (outcome.audit != null) {
            lastUndoable = Undoable(command, inverseOf(command, actor), System.currentTimeMillis())
        } else {
            // A failed action never becomes undoable, and a failed retry of a
            // new action invalidates an older undo target.
            lastUndoable = null
        }
        return outcome
    }

    /** Builds the reverse action, capturing overwritten values; null = not undoable. */
    private fun inverseOf(command: BotCommand, actor: String): (() -> BotActions.Outcome)? {
        return when (command) {
            is BotCommand.AddTime -> ({ BotActions.handle(BotCommand.Stop, actor) })
            BotCommand.Unlock -> ({ BotActions.handle(BotCommand.Lock, actor) })
            BotCommand.Lock -> ({ BotActions.handle(BotCommand.Unlock, actor) })
            BotCommand.StudyEnter -> ({ BotActions.handle(BotCommand.StudyExit, actor) })
            BotCommand.StudyExit -> ({ BotActions.handle(BotCommand.StudyEnter, actor) })
            BotCommand.QuizEnable -> ({ BotActions.handle(BotCommand.QuizDisable, actor) })
            BotCommand.QuizDisable -> ({ BotActions.handle(BotCommand.QuizEnable, actor) })
            is BotCommand.SetQuizCap -> {
                val previous = app.orchard.tv.quiz.QuizEarnedStore.capMinutes()
                ({ BotActions.handle(BotCommand.SetQuizCap(previous), actor) })
            }
            is BotCommand.ModeSwitch -> {
                val previous = !command.auto
                ({ BotActions.handle(BotCommand.ModeSwitch(previous), actor) })
            }
            is BotCommand.Remind -> {
                val previous = !command.on
                ({ BotActions.handle(BotCommand.Remind(previous), actor) })
            }
            // 停止/撤销/解绑 cannot be reverted from chat.
            else -> null
        }
    }

    /** Reverts the sender's last successful undoable action within 60 seconds. */
    private fun handleUndo(binding: BotBinding, message: InboundMessage) {
        val actor = nameOf(binding)
        val undoable = lastUndoable
        val expired = undoable != null && System.currentTimeMillis() - undoable.atMs > UNDO_TTL_MS
        when {
            undoable == null || expired ->
                sendReply(
                    message.contextToken,
                    "没有可撤销的操作（撤销需在60秒内，且仅支持加时/解锁/锁定/学习/答题开关/设置类指令）",
                )
            undoable.inverse == null ->
                sendReply(
                    message.contextToken,
                    "「${BotCommandParser.canonical(undoable.command)}」不支持撤销",
                )
            else -> {
                val original = undoable.command
                val inverse = undoable.inverse
                lastUndoable = null
                val outcome = runtime.gate.runExclusive { inverse() }
                BotAuditLog.append(
                    BotAuditLog.Entry(
                        atMs = System.currentTimeMillis(),
                        actor = actor,
                        command = "撤销(${BotCommandParser.canonical(original)})",
                        accepted = outcome.audit != null,
                        detail = outcome.text.lineSequence().firstOrNull().orEmpty(),
                    ),
                )
                sendReply(message.contextToken, "↩️ 已撤销「${BotCommandParser.canonical(original)}」：${outcome.text}")
                outcome.audit?.let {
                    runtime.broadcastAudit("↩️ $actor 撤销了「${BotCommandParser.canonical(original)}」")
                }
            }
        }
    }

    /** Self-service removal of the sender's own binding; the audit keeps the trace. */
    private fun handleUnbind(binding: BotBinding, message: InboundMessage) {
        val actor = nameOf(binding)
        BotAuditLog.append(
            BotAuditLog.Entry(
                atMs = System.currentTimeMillis(),
                actor = actor,
                command = "解绑",
                accepted = true,
                detail = "家长自助解绑",
            ),
        )
        sendReply(message.contextToken, "✅ 已解绑本电视。再见！")
        runtime.broadcastAudit("👋 $actor 解除了自己手机的绑定")
        runtime.unbind(binding.id, notify = false)
    }

    private fun nameOf(binding: BotBinding): String {
        val index = BotBindingStore.list().indexOfFirst { it.id == binding.id }
        return binding.displayName(index.coerceAtLeast(0))
    }

    private fun sendReply(contextToken: String, text: String) {
        // Without a reply context the backend refuses the send; there is
        // nothing useful to do for this message until the parent writes again.
        if (contextToken.isEmpty()) return
        val current = BotBindingStore.find(binding.id) ?: return
        runtime.sendText(current, contextToken, text)
    }

    companion object {
        private const val TAG = "BotChannel"
        private const val INITIAL_BACKOFF_MS = 1_000L

        /** Boot flakiness: a TV's network can take a minute to come up; stay nimble. */
        private const val NETWORK_ERROR_BACKOFF_CAP_MS = 15_000L

        /** Protocol refusals deserve a calmer rhythm, but not five silent minutes. */
        private const val REJECTED_BACKOFF_CAP_MS = 60_000L
        private const val EXCEPTION_BACKOFF_MS = 5_000L
        private const val REJECTED_BEFORE_RESYNC = 10
        private const val POLL_PAUSE_CHECK_MS = 60_000L
        private const val PAUSE_ONE_HOUR_MS = 60 * 60_000L
        private const val CONFIRM_TTL_MS = 120_000L
        private const val UNDO_TTL_MS = 60_000L

        /** Pure backoff step so the policy is unit-testable. */
        fun nextBackoffMs(result: UpdatesResult, current: Long): Long {
            val cap = if (result is UpdatesResult.NetworkError) {
                NETWORK_ERROR_BACKOFF_CAP_MS
            } else {
                REJECTED_BACKOFF_CAP_MS
            }
            return (current * 2).coerceIn(INITIAL_BACKOFF_MS, cap)
        }

        private val CONFIRM_WORDS = setOf("y", "yes", "是", "确认", "好", "好的", "确定")
        private val CANCEL_WORDS = setOf("n", "no", "否", "取消", "不", "算了")

        val WELCOME_TEXT: String = "✅ 绑定成功！我是电视助手。\n发送「帮助」看全部指令；常用：状态 / 加时20 / 停止"

        /** Version suffix the protocol reserves for third-party implementations. */
        fun channelVersion(): String =
            "${WeixinClawApi.CHANNEL_VERSION_BASE}/planyourtv-${BuildConfig.VERSION_NAME}"
    }
}
