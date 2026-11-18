package app.orchard.tv.bot

/**
 * One WeChat parent bound to this TV through the ClawBot channel. A binding is
 * created by the TV-side QR flow and is the only identity a chat command may
 * come from; every other WeChat user is ignored by the poll loop.
 *
 * The protocol credentials ([botToken], [baseUrl]) and the reply context
 * ([contextToken]) are secrets: they live in app-private storage, never in UI
 * state or logs. [contextToken] arrives with inbound messages only, so pushes
 * queue until the parent sends their first message.
 */
data class BotBinding(
    val id: String,
    val botToken: String,
    val botId: String,
    val userId: String,
    val baseUrl: String,
    val nickname: String,
    val boundAtMs: Long,
    val lastSeenMs: Long,
    /** getupdates cursor; empty until the first successful poll. */
    val cursor: String = "",
    /** context_token of the latest inbound message; empty until the parent writes. */
    val contextToken: String = "",
    val pushTimer: Boolean = true,
    val pushQuiz: Boolean = true,
    /** Audit pushes cannot be muted: they are the multi-parent transparency log. */
    val pushAudit: Boolean = true,
) {
    fun displayName(fallbackIndex: Int): String =
        nickname.ifBlank { "家长${fallbackIndex + 1}" }
}

/**
 * The chat surface a command runs on. Authentication is the binding itself:
 * to bind, a parent already passed the TV parent center's PIN gate, so chat
 * commands carry no PIN. Accountability comes from the audit log and the
 * broadcast to every bound parent.
 */
sealed interface BotCommand {
    /** Read-only commands answer from published state. */
    data object Status : BotCommand
    data object Today : BotCommand
    data object Bank : BotCommand
    data object Rules : BotCommand
    data object WrongBook : BotCommand
    data object Levels : BotCommand
    data object Apps : BotCommand
    data object Diag : BotCommand
    data object Help : BotCommand
    /** The accountability query: who ran what and did the TV accept it. */
    data object Audit : BotCommand
    /** The exemption-windows query: what they are and whether one is active now. */
    data object Exempt : BotCommand
    /** Today-so-far / yesterday usage rollup. */
    data object DailyReport : BotCommand

    /**
     * Control commands still re-run every policy check the lock screen runs;
     * they carry no authentication of their own.
     */
    data class AddTime(val minutes: Int) : BotCommand
    data object Stop : BotCommand
    data object Unlock : BotCommand
    data object Lock : BotCommand
    data object StudyEnter : BotCommand
    data object StudyExit : BotCommand
    data object QuizEnable : BotCommand
    data object QuizDisable : BotCommand

    /** Daily cap on quiz-earned minutes; 0 lifts the cap. */
    data class SetQuizCap(val minutes: Int) : BotCommand

    /**
     * One challenge-config field; ranges mirror the v1.97 codec (the round
     * count widened to the quiz-doc ceiling) and are re-validated by it.
     */
    enum class QuizField(val label: String, val range: String) {
        ROUND_COUNT("每轮题数", "1–1000"),
        TIME_LIMIT("每题时限", "5–1800秒"),
        UNLOCK_GATE("门槛", "0–180分钟"),
        ROUND_CAP("单轮上限", "1–180分钟"),
    }

    data class SetQuizField(val field: QuizField, val value: Int) : BotCommand
    data class Remind(val on: Boolean) : BotCommand

    /** AUTO ↔ MANUAL; mirrors the TV's confirm dialog with a chat Y round. */
    data class ModeSwitch(val auto: Boolean) : BotCommand

    /** Reverts the sender's last successful undoable control within its TTL. */
    data object Undo : BotCommand

    /** Self-service removal of the sender's own binding; the audit keeps the trace. */
    data object Unbind : BotCommand
}

/** True for commands that change state and are therefore audited. */
val BotCommand.isControl: Boolean
    get() = this is BotCommand.AddTime || this is BotCommand.Stop || this is BotCommand.Unlock ||
        this is BotCommand.Lock || this is BotCommand.StudyEnter || this is BotCommand.StudyExit ||
        this is BotCommand.QuizEnable || this is BotCommand.QuizDisable ||
        this is BotCommand.SetQuizCap || this is BotCommand.SetQuizField ||
        this is BotCommand.Remind || this is BotCommand.ModeSwitch || this is BotCommand.Undo ||
        this is BotCommand.Unbind

/**
 * Commands whose effect outlasts the moment (timing semantics change): they
 * ask for one Y, exactly like the TV's confirm dialog for the same switch.
 */
val BotCommand.needsConfirm: Boolean
    get() = this is BotCommand.ModeSwitch

/**
 * Parser outcome. Only [Exact] may execute anything: normalization is
 * deterministic, so "加时二十" is safe to run, while a fuzzy guess is a
 * suggestion the parent must confirm by typing the real command.
 */
sealed interface ParseResult {
    data class Exact(val command: BotCommand) : ParseResult
    data class Suggest(val echo: String, val candidates: List<String>) : ParseResult
    data class NeedArg(val echo: String, val hint: String) : ParseResult
    data object Unknown : ParseResult
}
