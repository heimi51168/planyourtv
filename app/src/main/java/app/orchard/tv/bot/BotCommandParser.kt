package app.orchard.tv.bot

/**
 * Turns one chat message into a [BotCommand].
 *
 * Four layers, in order, mirroring the design's degradation ladder:
 *
 * 1. exact match on the whitelist (including synonyms),
 * 2. deterministic normalization: full/half width, polite suffixes, Chinese
 *    numerals ("加时二十" -> AddTime(20)), minute/hour units ("半小时" -> 30),
 * 3. fuzzy suggestion: close-but-unrecognized text is answered with the likely
 *    command and never executed,
 * 4. unknown -> the caller replies with the help menu.
 *
 * Only layer 1 and 2 can produce [ParseResult.Exact]; both are deterministic
 * string transforms, so a typo can never execute a different command. Layer 3
 * deliberately has no execution path: a suggestion the parent must retype is
 * the safety margin for probabilistic guessing.
 */
internal object BotCommandParser {
    /** Chat commands a novice will type; used verbatim in help and suggestions. */
    val CANONICAL: List<String> = listOf(
        "状态", "今日", "规则", "应用", "题库", "错题", "关卡", "诊断", "审计", "帮助",
        "加时20", "停止", "解锁", "锁定",
        "学习进", "学习出", "答题开", "答题关",
        "设置答题上限30", "提醒开", "提醒关", "撤销", "解绑",
        "模式自动", "模式手动", "日报",
    )

    // --- layer 1 + 2: exact commands after normalization --------------------

    /** Display form of a command, used in help, suggestions and AI confirmations. */
    fun canonical(command: BotCommand): String = when (command) {
        BotCommand.Status -> "状态"
        BotCommand.Today -> "今日"
        BotCommand.Rules -> "规则"
        BotCommand.WrongBook -> "错题"
        BotCommand.Levels -> "关卡"
        BotCommand.Apps -> "应用"
        BotCommand.Diag -> "诊断"
        BotCommand.Bank -> "题库"
        BotCommand.Audit -> "审计"
        BotCommand.Help -> "帮助"
        is BotCommand.AddTime -> "加时${command.minutes}"
        BotCommand.Stop -> "停止"
        BotCommand.Unlock -> "解锁"
        BotCommand.Lock -> "锁定"
        BotCommand.StudyEnter -> "学习进"
        BotCommand.StudyExit -> "学习出"
        BotCommand.QuizEnable -> "答题开"
        BotCommand.QuizDisable -> "答题关"
        is BotCommand.SetQuizCap ->
            if (command.minutes == 0) "设置答题上限0（不限）" else "设置答题上限${command.minutes}"
        is BotCommand.SetQuizField -> "设置${command.field.label}${command.value}"
        is BotCommand.ModeSwitch -> if (command.auto) "模式自动" else "模式手动"
        BotCommand.DailyReport -> "日报"
        is BotCommand.Remind -> if (command.on) "提醒开" else "提醒关"
        BotCommand.Undo -> "撤销"
        BotCommand.Unbind -> "解绑"
    }

    fun parse(raw: String): ParseResult {
        val text = normalize(raw)
        if (text.isEmpty()) return ParseResult.Unknown

        settingOf(text)?.let { return it }
        val addTime = addTimeOf(text)
        if (addTime == ADD_TIME_NEED_ARG) {
            return ParseResult.NeedArg("加时", "请带上分钟数（5–120），如：加时20")
        }
        addTime?.let { return ParseResult.Exact(BotCommand.AddTime(it)) }

        exactOf(text)?.let { return ParseResult.Exact(it) }
        return fuzzyOf(raw, text)
    }

    /**
     * 设置类语法：答题上限N（日封顶）加上四个挑战配置单字段，可选「设置」前缀。
     * 空参数回引导文案，数字接受阿拉伯或中文。单位陷阱在这里处理：分钟字段
     * 吃「分钟/分」，秒字段只吃「秒」——「每题时限1分钟」必须引导而不是静默
     * 理解成 1 秒。
     */
    private fun settingOf(text: String): ParseResult? {
        val body = text.removePrefix("设置")
        val cap = prefixNumber(body, "答题上限")
        if (cap != null) {
            val cleaned = settingMinutes(cap)
                ?: return ParseResult.NeedArg(
                    "答题上限",
                    "请带上分钟数（0=不限，1–240），如：设置答题上限30",
                )
            return ParseResult.Exact(BotCommand.SetQuizCap(cleaned))
        }
        for (field in BotCommand.QuizField.entries) {
            val rest = prefixNumber(body, field.label)
            if (rest != null) return settingFieldResult(field, rest)
        }
        return null
    }

    private fun settingFieldResult(field: BotCommand.QuizField, rest: String): ParseResult {
        val hint = "请带上数值（${field.range}），如：设置${field.label}10"
        if (rest.isEmpty()) return ParseResult.NeedArg(field.label, hint)
        val value: Int? = if (field == BotCommand.QuizField.TIME_LIMIT) {
            settingSeconds(rest)
        } else {
            settingMinutes(rest)
        }
        if (value != null) return ParseResult.Exact(BotCommand.SetQuizField(field, value))
        return if (field == BotCommand.QuizField.TIME_LIMIT) {
            ParseResult.NeedArg("每题时限", "每题时限的单位是秒，如：设置每题时限45")
        } else {
            ParseResult.NeedArg(field.label, hint)
        }
    }

    /** Returns the text after [keyword] when it leads, "" for the bare keyword, null otherwise. */
    private fun prefixNumber(body: String, keyword: String): String? =
        if (body.startsWith(keyword)) body.removePrefix(keyword) else null

    /** Minute-style values: bare numbers, 中文数字, optional 分钟/分 tail. */
    private fun settingMinutes(rest: String): Int? {
        val body = rest.removeSuffix("分钟").removeSuffix("分")
        if (body.isEmpty()) return null
        return numberOrNull(body)
    }

    /** Second-style values: bare numbers or 秒 tail only; a 分钟 tail is rejected. */
    private fun settingSeconds(rest: String): Int? {
        if (rest.endsWith("分钟") || rest.endsWith("分")) return null
        val body = rest.removeSuffix("秒")
        if (body.isEmpty()) return null
        return numberOrNull(body)
    }

    private fun numberOrNull(text: String): Int? {
        val digits = text.filter { it.isDigit() }
        if (digits.isNotEmpty()) return digits.toIntOrNull()
        return chineseNumber(text)
    }

    private fun exactOf(text: String): BotCommand? = when (text) {
        "状态", "status", "查询", "情况", "现在", "看看", "当前" -> BotCommand.Status
        "今日", "今天", "今天用量", "用量", "today" -> BotCommand.Today
        "规则", "时间规则", "当前规则", "rules" -> BotCommand.Rules
        "错题", "错题本", "wrong" -> BotCommand.WrongBook
        "关卡", "答题关卡", "levels" -> BotCommand.Levels
        "应用", "应用列表", "apps" -> BotCommand.Apps
        "诊断", "诊断信息", "diag" -> BotCommand.Diag
        "题库", "题目", "bank" -> BotCommand.Bank
        "审计", "记录", "操作记录", "日志", "audit" -> BotCommand.Audit
        "帮助", "菜单", "指令", "命令", "help" -> BotCommand.Help
        "停止", "停", "别看", "别看了", "关掉", "停止观看", "stop" -> BotCommand.Stop
        "解锁", "临时解锁", "unlock" -> BotCommand.Unlock
        "锁定", "恢复", "恢复锁定", "重新锁定", "lock" -> BotCommand.Lock
        "学习进", "进入学习", "学习模式进", "学习模式进入", "开始学习" -> BotCommand.StudyEnter
        "学习出", "退出学习", "学习模式出", "学习模式退出", "结束学习" -> BotCommand.StudyExit
        "答题开", "开答题", "打开答题", "开启答题" -> BotCommand.QuizEnable
        "答题关", "关答题", "关闭答题" -> BotCommand.QuizDisable
        "提醒开", "开提醒", "打开提醒", "开启提醒", "要提醒" -> BotCommand.Remind(true)
        "提醒关", "关提醒", "关闭提醒", "别提醒", "五分钟提醒关" -> BotCommand.Remind(false)
        "撤销", "取消刚才", "undo" -> BotCommand.Undo
        "解绑", "unbind" -> BotCommand.Unbind
        "模式自动", "自动模式", "切到自动", "切换自动", "改成自动" -> BotCommand.ModeSwitch(auto = true)
        "模式手动", "手动模式", "切到手动", "切换手动", "改成手动" -> BotCommand.ModeSwitch(auto = false)
        "日报", "报告", "今日报告", "每日报告", "report" -> BotCommand.DailyReport
        else -> null
    }

    // --- normalization -------------------------------------------------------

    /** Lowercase, full-width -> half-width, drop spaces/punctuation/politeness. */
    fun normalize(raw: String): String {
        val squashed = StringBuilder(raw.length)
        for (ch in raw.trim().lowercase()) {
            val half = when {
                ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
                ch == '　' -> ' '
                else -> ch
            }
            if (half.isWhitespace()) continue
            if (half.isLetterOrDigit()) squashed.append(half)
        }
        var text = squashed.toString()
        // Politeness/padding tails, longest first so "谢谢了" strips in one pass.
        for (tail in listOf("谢谢了", "谢谢你", "谢谢", "麻烦了", "麻烦", "please", "吧", "呢", "啊", "了", "哈")) {
            if (text.endsWith(tail) && text.length > tail.length) {
                text = text.removeSuffix(tail)
                break
            }
        }
        return text
    }

    private const val ADD_TIME_NEED_ARG = -1

    /** Minutes for an add-time command, [ADD_TIME_NEED_ARG] when the count is missing. */
    private fun addTimeOf(text: String): Int? {
        val keyword = ADD_TIME_KEYWORDS.firstOrNull { text.startsWith(it) } ?: return null
        val rest = text.removePrefix(keyword)
        if (rest.isEmpty()) return ADD_TIME_NEED_ARG
        // Special spoken units before plain numbers: "半小时" / "一刻钟" / "一小时".
        when (rest) {
            "半小时", "半个钟头" -> return 30
            "一刻钟" -> return 15
            "一小时", "一个小时", "一个钟头" -> return 60
        }
        val minutes = minutesOf(rest) ?: return ADD_TIME_NEED_ARG
        // Out-of-range values still parse; the admission layer owns the 5-120 rule
        // so the parent gets the real range message instead of a bare "没听懂".
        return if (minutes in 1..999) minutes else ADD_TIME_NEED_ARG
    }

    private val ADD_TIME_KEYWORDS = listOf("加时", "续时", "延长", "再加", "加多", "addtime", "add")

    /** "20分钟" / "20" / "二十分钟" / "四十五" / "半小时" styles -> minutes; "1小时30分" is not v1. */
    private fun minutesOf(rest: String): Int? {
        val body = rest.removeSuffix("分钟").removeSuffix("分")
        val hours = rest.endsWith("小时") || rest.endsWith("个钟头") || rest.endsWith("钟头")
        val digits = body.filter { it.isDigit() }
        val number = when {
            digits.isNotEmpty() -> digits.toIntOrNull()
            else -> chineseNumber(body)
        } ?: return null
        return if (hours) number * 60 else number
    }

    private val DIGITS = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )

    /** Chinese numerals 0-999 ("二十"=20, "四十五"=45, "一百二十"=120). */
    fun chineseNumber(text: String): Int? {
        if (text.isEmpty()) return null
        var total = 0
        var current = 0
        var sawDigit = false
        for (ch in text) {
            when {
                DIGITS.containsKey(ch) -> {
                    current = current * 10 + DIGITS.getValue(ch)
                    sawDigit = true
                }
                ch == '十' -> {
                    // "十" alone is 10; "二十" is 2*10; sequential tens ("二十十") are invalid.
                    total += (if (sawDigit && current > 0) current else 1) * 10
                    current = 0
                    sawDigit = false
                }
                ch == '百' -> {
                    if (!sawDigit || current <= 0) return null
                    total += current * 100
                    current = 0
                    sawDigit = false
                }
                else -> return null
            }
        }
        if (total == 0 && !sawDigit) return null
        return total + current
    }

    // --- layer 3: fuzzy suggestions ------------------------------------------

    /**
     * Edit-distance guess over canonical names, synonyms and pinyin initials.
     * Returns suggestions only; the caller must never execute them.
     */
    private fun fuzzyOf(raw: String, normalized: String): ParseResult {
        val candidates = LinkedHashSet<String>()
        for (candidate in CANONICAL) {
            val plain = candidate.filter { it.isDigit().not() }
            if (distance(normalized, normalize(plain)) <= FUZZY_LIMIT(plain)) candidates.add(candidate)
        }
        // Pinyin initials: "zt" -> 状态, "js" -> 加时/解锁. Both surface; the parent picks.
        // The parent may type initials directly, so match ascii input as-is; a
        // Chinese phrase is reduced to its initials first.
        val asciiOnly = normalized.length in 2..4 && normalized.all { it in 'a'..'z' }
        val initials = if (asciiOnly) normalized else pinyinInitials(normalized)
        if (initials.length >= 2) {
            for ((word, initial) in PINYIN_INITIAL_WORDS) {
                if (initial == initials) candidates.add(word)
            }
        }
        if (candidates.isEmpty()) return ParseResult.Unknown
        return ParseResult.Suggest(raw.take(24), candidates.toList())
    }

    private fun FUZZY_LIMIT(candidate: String): Int = when {
        candidate.length <= 2 -> 1
        candidate.length <= 4 -> 1
        else -> 2
    }

    /** Levenshtein with an early exit once the distance passes [max]. */
    fun distance(a: String, b: String, max: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                rowMin = minOf(rowMin, curr[j])
            }
            if (rowMin > max) return max + 1
            val swap = prev
            prev = curr
            curr = swap
        }
        return prev[b.length]
    }

    /** First letters of the small keyword vocabulary, enough for command initials. */
    private val PINYIN_INITIALS: Map<Char, Char> = mapOf(
        '状' to 'z', '态' to 't', '查' to 'c', '询' to 'x', '今' to 'j', '天' to 't',
        '题' to 't', '库' to 'k', '帮' to 'b', '助' to 'z', '菜' to 'c', '单' to 'd',
        '验' to 'y', '证' to 'z', '加' to 'j', '时' to 's', '续' to 'x', '停' to 't',
        '止' to 'z', '解' to 'j', '锁' to 's', '定' to 'd', '学' to 'x', '习' to 'x',
        '进' to 'j', '出' to 'c', '答' to 'd', '开' to 'k', '关' to 'g', '绑' to 'b',
    )

    private val PINYIN_INITIAL_WORDS: List<Pair<String, String>> = listOf(
        "状态" to "zt", "查询" to "cx", "今日" to "jr", "题库" to "tk", "帮助" to "bz",
        "菜单" to "cd", "加时" to "js", "续时" to "xs", "停止" to "tz",
        "解锁" to "js", "锁定" to "sd", "学习进" to "xxj", "学习出" to "xxc",
        "答题开" to "dtk", "答题关" to "dtg", "解绑" to "jb",
    )

    private fun pinyinInitials(text: String): String =
        text.mapNotNull { PINYIN_INITIALS[it] }.joinToString("")
}
