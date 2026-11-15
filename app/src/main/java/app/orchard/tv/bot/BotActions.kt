package app.orchard.tv.bot

import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.ManualSessionLimits
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.PolicyModeCoordinator.WebUnlockReason
import app.orchard.tv.quiz.QuizEarnedStore
import app.orchard.tv.quiz.QuizQuestionCache
import app.orchard.tv.quiz.QuizRuntime
import app.orchard.tv.quiz.QuizSessionStore
import app.orchard.tv.quiz.QuizSettings
import app.orchard.tv.system.Diagnostics
import com.planyourtv.policy.Activation
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyUiState
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thin mapping from a chat command to the coordinator's existing actions.
 *
 * Authentication is the binding, full stop: chat carries no PIN anywhere.
 * Controls still run the full policy admission the lock screen runs — those
 * are product rules, not authentication. What a command did is recorded in
 * BotAuditLog and broadcast to every bound parent.
 *
 * [audit] is non-null exactly when the command changed something; the audit
 * log stores it as the accepted flag.
 */
internal object BotActions {

    data class Outcome(val text: String, val audit: String? = null)

    fun handle(command: BotCommand, actorName: String): Outcome = when (command) {
        is BotCommand.AddTime -> addTime(command.minutes, actorName)
        BotCommand.Stop -> stop(actorName)
        BotCommand.Unlock -> unlock(actorName)
        BotCommand.Lock -> lock(actorName)
        BotCommand.StudyEnter -> studyEnter(actorName)
        BotCommand.StudyExit -> studyExit(actorName)
        BotCommand.QuizEnable -> quizEnabled(true)
        BotCommand.QuizDisable -> quizEnabled(false)
        is BotCommand.SetQuizCap -> setQuizCap(command.minutes, actorName)
        is BotCommand.SetQuizField -> setQuizField(command.field, command.value, actorName)
        is BotCommand.Remind -> setRemind(command.on, actorName)
        is BotCommand.ModeSwitch -> modeSwitch(command.auto, actorName)
        BotCommand.Status -> Outcome(statusText())
        BotCommand.Today -> Outcome(todayText())
        BotCommand.Rules -> Outcome(rulesText())
        BotCommand.WrongBook -> Outcome(wrongText())
        BotCommand.Levels -> Outcome(levelsText())
        BotCommand.Apps -> Outcome(appsText())
        BotCommand.Diag -> Outcome(diagText())
        BotCommand.DailyReport -> Outcome(BotDailyReport.reportText())
        BotCommand.Bank -> Outcome(bankText())
        BotCommand.Audit -> Outcome(auditText())
        BotCommand.Help -> Outcome(HELP_TEXT)
        BotCommand.Undo ->
            // The channel owns undo (it holds the last-action state); unreachable here.
            Outcome("撤销请发送：撤销")
        BotCommand.Unbind ->
            // The channel owns unbinding (farewell + channel stop); unreachable here.
            Outcome("解绑请发送：解绑")
    }

    // --- controls ---------------------------------------------------------------

    private fun addTime(minutes: Int, actorName: String): Outcome {
        if (!ManualSessionLimits.isValid(minutes)) {
            return Outcome("单次加时需在5–120分钟之间，如：加时20")
        }
        // The lock screen's own start path: full admission (window/rest/study/
        // activation/screen checks) without a PIN layer, which chat no longer adds.
        return when (
            val result = PolicyModeCoordinator.startLocalManualSession(
                durationSeconds = minutes * 60L,
                allowHostedRule = true,
            )
        ) {
            SessionStartResult.Started ->
                Outcome("✅ 已开始 ${minutes} 分钟观看", "✅ $actorName 加时 $minutes 分钟")
            SessionStartResult.Pending ->
                Outcome("电视上有等待确认的操作，请稍后再试")
            is SessionStartResult.Rejected ->
                Outcome(rejectText(result.reason))
        }
    }

    private fun rejectText(reason: RejectReason): String = when (reason) {
        RejectReason.GUARD_DISABLED -> "儿童模式未开启"
        RejectReason.HOSTED_MANUAL_REMOTE_ONLY -> "当前模式不支持远程加时"
        RejectReason.ACTIVATION_MISMATCH -> "当前是自动模式，孩子到点自动开始；切手动模式需在电视上操作"
        RejectReason.PARENT_OVERRIDE -> "电视已处于临时解锁状态"
        RejectReason.BUSY -> "已有观看进行中，可发「状态」看剩余时间"
        RejectReason.RESTING -> "强制休息中，休息结束后才能开始"
        RejectReason.DAILY_LIMIT -> "今日额度已用完，明天再试"
        RejectReason.SCREEN_OFF -> "电视处于息屏状态"
        RejectReason.OUTSIDE_WINDOW -> "不在允许观看时段内"
        RejectReason.NO_TIMED_APPS -> "尚未配置计时应用，请先在家长中心选择"
        RejectReason.STALE_EPOCH, RejectReason.STALE_VERSION, RejectReason.EXPIRED,
        RejectReason.INVALID_COMMAND, RejectReason.INVALID_SCOPE,
        -> "电视状态刚刚变化，请稍后再试或发「状态」查看"
    }

    private fun stop(actorName: String): Outcome {
        // Parent stop, like the parent center's own button: usage settles with
        // the parent-stop reason instead of the child exit's rest semantics.
        if (!LocalPolicyRuntime.isActive) {
            return Outcome("当前没有进行中的观看会话")
        }
        LocalPolicyRuntime.endSessionByParent()
        return Outcome("✅ 已结束本次观看", "⛔ $actorName 远程结束了观看")
    }

    private fun unlock(actorName: String): Outcome {
        // Same eligibility set as the phone web page (lock surface, no session,
        // no study mode, no quiz, screen on). The web's default-PIN refusal is
        // its own authentication layer; chat authenticates by binding, so that
        // one reason is passed through instead of blocking.
        val status = PolicyModeCoordinator.webUnlockStatus()
        if (!status.eligible && status.reason != WebUnlockReason.DEFAULT_PIN) {
            return Outcome(unlockRejectedText(status.reason))
        }
        val began = LocalPolicyRuntime.tryBeginParentOverrideFromLockScreen()
        return if (began) {
            Outcome("✅ 已临时解锁，家长使用不计入孩子额度", "🔓 $actorName 临时解锁了电视")
        } else {
            Outcome("电视状态刚刚变化，未能解锁，请发「状态」查看")
        }
    }

    private fun unlockRejectedText(reason: WebUnlockReason): String = when (reason) {
        WebUnlockReason.NOT_LOCAL -> "当前不是本地儿童模式"
        WebUnlockReason.NOT_READY, WebUnlockReason.STATE_CHANGED -> "电视状态刚刚变化，请发「状态」查看"
        WebUnlockReason.SETUP_REQUIRED, WebUnlockReason.PIN_NOT_SET -> "请先在电视家长中心完成设置"
        WebUnlockReason.STUDY_MODE -> "学习模式进行中，不能临时解锁"
        WebUnlockReason.ALREADY_UNLOCKED -> "电视已经是解锁状态"
        WebUnlockReason.SESSION_ACTIVE -> "观看进行中，不能重复解锁"
        WebUnlockReason.QUIZ_ACTIVE -> "孩子正在答题，不能打断"
        WebUnlockReason.SCREEN_OFF -> "电视处于息屏状态"
        WebUnlockReason.NOT_ON_LOCK_SCREEN -> "电视当前不在锁屏界面"
        WebUnlockReason.DEFAULT_PIN -> "电视还在使用默认PIN，请先修改"
        WebUnlockReason.OK -> "现在可以解锁" // unreachable on the rejected path
    }

    private fun lock(actorName: String): Outcome {
        if (!PolicyModeCoordinator.isTemporaryUnlocked()) {
            return Outcome("电视当前不在临时解锁状态")
        }
        PolicyModeCoordinator.resumeChildGuard()
        return Outcome("✅ 已恢复儿童锁定", "🔒 $actorName 恢复了锁定")
    }

    private fun studyEnter(actorName: String): Outcome {
        val entered = PolicyModeCoordinator.enterStudyMode()
        return if (entered) {
            Outcome("✅ 已进入学习模式（不计时）", "📚 $actorName 开启了学习模式")
        } else {
            Outcome("现在不能进入学习模式，请发「状态」查看当前情况")
        }
    }

    private fun studyExit(actorName: String): Outcome {
        PolicyModeCoordinator.exitStudyMode()
        return Outcome("✅ 已退出学习模式", "📚 $actorName 关闭了学习模式")
    }

    private fun quizEnabled(on: Boolean): Outcome {
        // The quiz admin page is local-mode only; keep the chat surface identical.
        if (PolicyModeCoordinator.mode != PolicyMode.LOCAL) {
            return Outcome("答题功能属于本地儿童模式，当前模式不可用")
        }
        // QuizSettings owns the guards the web page also goes through: refusing
        // to disable during a live round, settling/cleaning stored sessions and
        // refreshing the lock-screen entry.
        return when (QuizSettings.setEnabled(on)) {
            QuizSettings.SetResult.Changed ->
                Outcome(if (on) "✅ 答题已开启" else "✅ 答题已关闭（时间用完将直接锁定）")
            QuizSettings.SetResult.SessionRunning ->
                Outcome("孩子正在答题，不能现在关闭答题功能")
            QuizSettings.SetResult.StorageError ->
                Outcome("电视存储异常，未能保存，请稍后再试")
        }
    }

    // --- settings ------------------------------------------------------------

    /**
     * One challenge-config field. The codec is the range authority: a changed
     * config that fails the decode round-trip is refused with the field's
     * advertised range, exactly like the web page's validation.
     */
    private fun setQuizField(field: BotCommand.QuizField, value: Int, actorName: String): Outcome {
        val current = QuizSessionStore.loadChallengeConfig() ?: com.planyourtv.policy.quiz.QuizConfig()
        val updated = when (field) {
            BotCommand.QuizField.ROUND_COUNT -> current.copy(pullCount = value)
            BotCommand.QuizField.TIME_LIMIT -> current.copy(defaultTimeLimitSec = value)
            BotCommand.QuizField.UNLOCK_GATE -> current.copy(minUnlockMinutes = value)
            BotCommand.QuizField.ROUND_CAP -> current.copy(maxEarnedMinutes = value)
        }
        val valid = com.planyourtv.policy.quiz.QuizSessionCodec.decodeConfig(
            com.planyourtv.policy.quiz.QuizSessionCodec.encodeConfig(updated),
        )
        if (valid == null) {
            return Outcome("${field.label}需在 ${field.range} 之间，如：设置${field.label}10")
        }
        if (!QuizSessionStore.saveChallengeConfig(valid)) {
            return Outcome("电视存储异常，未能保存，请稍后再试")
        }
        // Parity with the web page: a bound TV uploads local config edits so the
        // next cloud pull cannot silently clobber what the parent just set.
        app.orchard.tv.quiz.QuizConfigSync.afterLocalEdit()
        return Outcome(
            "✅ ${field.label}已设为 $value（${field.range}）",
            "⚙️ $actorName 设置${field.label} $value",
        )
    }

    /** The TV asks for a confirm dialog on this switch; the channel asks for a Y. */
    private fun modeSwitch(auto: Boolean, actorName: String): Outcome {
        val state = PolicyModeCoordinator.state.value
        if (state.mode != PolicyMode.LOCAL) {
            return Outcome("只有本地儿童模式支持自动/手动切换（当前：${modeLine(state)}）")
        }
        if (state.activation == if (auto) Activation.AUTO else Activation.MANUAL) {
            return Outcome("已经在${if (auto) "自动" else "手动"}模式了")
        }
        val changed = PolicyModeCoordinator.setLocalActivation(
            if (auto) Activation.AUTO else Activation.MANUAL,
        )
        if (!changed) {
            return Outcome("切换没有成功，孩子可能正在使用，请稍后再试或发「状态」查看")
        }
        val semantics = if (auto) {
            "到点自动开始，受每日额度和单次上限约束"
        } else {
            "每次观看都需要家长开启或孩子答题换取"
        }
        return Outcome(
            "✅ 已切换到${if (auto) "自动" else "手动"}模式：$semantics",
            "🔁 $actorName 切换到${if (auto) "自动" else "手动"}模式",
        )
    }

    private fun setQuizCap(minutes: Int, actorName: String): Outcome {
        if (!QuizEarnedStore.setCapMinutes(minutes)) {
            return Outcome("答题上限需在0–240之间（0=不限），如：设置答题上限30")
        }
        val capLine = if (minutes == 0) "不限" else "$minutes 分钟/天"
        return Outcome(
            "✅ 答题所得上限已设为 $capLine（今日已得 ${QuizEarnedStore.earnedToday()} 分钟）",
            "⚙️ $actorName 设置答题上限 $capLine",
        )
    }

    private fun setRemind(on: Boolean, actorName: String): Outcome {
        BotBindingStore.setRemindEnabled(on)
        return if (on) {
            Outcome("✅ 已开启结束前5分钟提醒", "⚙️ $actorName 开启了结束提醒")
        } else {
            Outcome("✅ 已关闭结束前5分钟提醒", "⚙️ $actorName 关闭了结束提醒")
        }
    }

    // --- queries ------------------------------------------------------------------

    private fun rulesText(): String {
        val state = PolicyModeCoordinator.state.value
        val rules = LocalPolicyRuntime.currentRules
        if (state.mode == PolicyMode.NONE || rules == null) return "儿童模式未开启，没有生效的规则。"
        val activation = if (state.activation == Activation.MANUAL) "手动" else "自动"
        return buildString {
            append("当前规则（$activation 模式）：\n")
            append("工作日时段：${windowsLabel(rules.weekdayWindows)}\n")
            append("周末时段：${windowsLabel(rules.weekendWindows)}\n")
            append("每日额度（自动）：${rules.autoDailyLimitMinutes} 分钟\n")
            append("单次上限（自动）：${rules.sessionLimitMinutes} 分钟\n")
            append("休息间隔：${rules.restMinutes} 分钟\n")
            val cap = QuizEarnedStore.capMinutes()
            append(
                "答题所得上限：" + (
                    if (cap == 0) "不限" else "$cap 分钟/天（今日已得 ${QuizEarnedStore.earnedToday()} 分钟）"
                    ),
            )
        }
    }

    private fun windowsLabel(windows: List<com.planyourtv.policy.LocalTimeWindow>): String {
        if (windows.isEmpty()) return "无"
        return windows.joinToString("、") { "${minuteOfDay(it.startMinute)}-${minuteOfDay(it.endMinute)}" }
    }

    private fun minuteOfDay(minute: Int): String =
        "%02d:%02d".format(Locale.CHINA, minute / 60, minute % 60)

    private fun wrongText(): String {
        val counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE)
        if (counts.wrong == 0) return "错题本是空的，孩子最近没有答错的题。"
        val rows = QuizQuestionCache.listBank(
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            filter = "wrong",
            limit = 5,
        )
        return buildString {
            append("错题共 ${counts.wrong} 题，最近：\n")
            rows.forEachIndexed { index, row ->
                append("${index + 1}. ${row.question.prompt.take(30)}（错 ${row.wrongCount} 次）\n")
            }
            append("完整错题本请在手机设置页查看")
        }
    }

    private fun levelsText(): String {
        val quiz = QuizRuntime.refresh()
        val levels = quiz.answerLevels
        if (levels.isEmpty()) return "还没有配置答题关卡，请在电视家长中心设置。"
        return buildString {
            append("答题关卡（${levels.size} 个）：\n")
            levels.forEach { level ->
                append("· ${level.name}（可用 ${level.available} 题）\n")
            }
        }
    }

    private fun appsText(): String {
        val state = LocalPolicyRuntime.state.value
        val context = BotRuntime.appContext ?: return "应用清单暂不可用，请稍后再试。"
        val pm = context.packageManager
        fun label(pkg: String): String = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg.substringAfterLast('.'))
        val entertainment = state.allowedPackages
        val study = state.studyPackages
        return buildString {
            if (state.accessMode != com.planyourtv.policy.AccessMode.APPS) {
                append("娱乐范围：全部应用\n")
            } else {
                append("娱乐应用（${entertainment.size} 个，计时）：\n")
                entertainment.take(12).forEach { append("· ${label(it)}\n") }
                if (entertainment.size > 12) append("… 共 ${entertainment.size} 个\n")
            }
            if (study.isEmpty()) {
                append("学习应用：未配置")
            } else {
                append("学习应用（${study.size} 个，不计时）：\n")
                study.take(8).forEach { append("· ${label(it)}\n") }
                if (study.size > 8) append("… 共 ${study.size} 个")
            }
        }
    }

    private fun diagText(): String {
        val context = BotRuntime.appContext ?: return "诊断信息暂不可用。"
        val report = Diagnostics.report(context)
        // The full report is long; the top carries the identity/network lines
        // a parent ever needs when asked to send diagnostics.
        return report.lineSequence().take(25).joinToString("\n")
    }

    private fun statusText(): String {
        val state = PolicyModeCoordinator.state.value
        if (!state.loaded) return "电视启动中，请稍后再查。"
        return buildString {
            append(modeLine(state))
            appendSession(state)
            appendQuota(state)
            appendWindow(state)
            if (state.studyModeActive) append("\n📚 学习模式进行中（不计时）")
        }
    }

    private fun todayText(): String {
        val state = PolicyModeCoordinator.state.value
        if (!state.loaded) return "电视启动中，请稍后再查。"
        val used = state.dailyUsedSeconds / 60
        return buildString {
            append("今日已看 ${used} 分钟")
            if (state.dailyLimitSeconds > 0) append(" / 额度 ${state.dailyLimitSeconds / 60} 分钟")
            val earned = QuizEarnedStore.earnedToday()
            if (earned > 0) {
                val cap = QuizEarnedStore.capMinutes()
                append(" · 答题所得 $earned" + if (cap > 0) "/$cap" else "")
            }
            appendWindow(state)
        }
    }

    private fun modeLine(state: PolicyUiState): String = when (state.mode) {
        PolicyMode.NONE -> "儿童模式未开启"
        PolicyMode.LOCAL ->
            if (state.activation == Activation.MANUAL) "儿童模式 · 手动（家长开启每次观看）" else "儿童模式 · 自动（到点自动开始）"
        PolicyMode.CLOUD -> "儿童模式 · 远程托管"
    }

    private fun StringBuilder.appendSession(state: PolicyUiState) {
        when {
            state.sessionState == SessionState.ACTIVE ->
                append("\n📺 观看中 · 剩余 ${state.effectiveRemainingSeconds / 60} 分钟")
            state.sessionState == SessionState.PENDING ->
                append("\n⏳ 有一段观看等待孩子在电视上确认")
            state.restRemainingSeconds > 0 ->
                append("\n☕ 强制休息中 · 剩 ${state.restRemainingSeconds / 60 + 1} 分钟")
            state.guardState == GuardStatus.PARENT_OVERRIDE ->
                append("\n🔓 临时解锁中（家长使用）")
        }
    }

    private fun StringBuilder.appendQuota(state: PolicyUiState) {
        if (state.dailyLimitSeconds > 0 && state.mode != PolicyMode.NONE) {
            append("\n今日已用 ${state.dailyUsedSeconds / 60}/${state.dailyLimitSeconds / 60} 分钟")
        }
    }

    private fun StringBuilder.appendWindow(state: PolicyUiState) {
        val next = state.nextWindowLabel
        if (!next.isNullOrBlank()) append("\n⏰ 下次可看：$next")
    }

    private fun bankText(): String {
        // QuizRuntime.refresh() may touch Keystore; the channel calls on IO.
        val quiz = QuizRuntime.refresh()
        if (!quiz.enabled) return "答题功能当前已关闭"
        if (quiz.bankState == "empty") return "题库为空，请在手机设置页或电视家长中心配置"
        val ai = if (quiz.providerConfigured) "已配置" else "未配置"
        return "题库 ${quiz.bankTotal} 题 · 未使用 ${quiz.bankUnused} 题\nAI出题：$ai（题库管理请在手机设置页操作）"
    }

    private fun auditText(): String {
        val entries = BotAuditLog.recent(limit = 20)
        if (entries.isEmpty()) return "暂无操作记录。所有控制指令（成功或被拒绝）都会记录在这里。"
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        return buildString {
            append("最近${entries.size}条操作记录：\n")
            entries.forEach { entry ->
                append(
                    "${format.format(Date(entry.atMs))} ${entry.actor} ${entry.command}" +
                        if (entry.accepted) " ✅" else " ✗ ${entry.detail}",
                )
                append("\n")
            }
        }
    }

    val HELP_TEXT: String = buildString {
        append("📺 电视遥控命令（无需密码，所有操作有记录并通知每位家长）\n")
        append("查询：状态 / 今日 / 日报 / 规则 / 应用 / 题库 / 错题 / 关卡 / 诊断 / 审计\n")
        append("控制：加时20（5–120）/ 停止 / 解锁 / 锁定 / 学习进出 / 答题开关\n")
        append("设置：设置答题上限30（0=不限）/ 设置每轮题数10 / 设置每题时限45 / 设置门槛5 / 设置单轮上限15\n")
        append("设置：模式自动 / 模式手动 / 提醒开 / 提醒关\n")
        append("其他：撤销（60秒内撤回上一条）/ 帮助 / 解绑\n")
        append("说明：支持「加时二十」等写法；「审计」可查谁操作了什么")
    }
}
