package app.orchard.tv.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser is the chat surface's safety boundary: only deterministic
 * transforms may produce an Exact, and every dialect a real parent types
 * ("加时二十", "再加20分钟吧", full-width digits) must land on the same command.
 */
class BotCommandParserTest {

    private fun exact(input: String): BotCommand =
        (BotCommandParser.parse(input) as ParseResult.Exact).command

    @Test
    fun canonicalCommandsParseExactly() {
        assertEquals(BotCommand.Status, exact("状态"))
        assertEquals(BotCommand.Status, exact("status"))
        assertEquals(BotCommand.Today, exact("今日"))
        assertEquals(BotCommand.Bank, exact("题库"))
        assertEquals(BotCommand.Audit, exact("审计"))
        assertEquals(BotCommand.Audit, exact("操作记录"))
        assertEquals(BotCommand.Help, exact("帮助"))
        assertEquals(BotCommand.Stop, exact("停止"))
        assertEquals(BotCommand.Unlock, exact("解锁"))
        assertEquals(BotCommand.Lock, exact("锁定"))
        assertEquals(BotCommand.StudyEnter, exact("学习进"))
        assertEquals(BotCommand.StudyExit, exact("学习出"))
        assertEquals(BotCommand.QuizEnable, exact("答题开"))
        assertEquals(BotCommand.QuizDisable, exact("答题关"))
        assertEquals(BotCommand.Unbind, exact("解绑"))
    }

    @Test
    fun v11QueriesParseExactly() {
        assertEquals(BotCommand.Rules, exact("规则"))
        assertEquals(BotCommand.Rules, exact("时间规则"))
        assertEquals(BotCommand.WrongBook, exact("错题"))
        assertEquals(BotCommand.WrongBook, exact("错题本"))
        assertEquals(BotCommand.Levels, exact("关卡"))
        assertEquals(BotCommand.Apps, exact("应用"))
        assertEquals(BotCommand.Diag, exact("诊断"))
        assertEquals(BotCommand.Exempt, exact("豁免"))
        assertEquals(BotCommand.Exempt, exact("豁免时段"))
        assertEquals(BotCommand.Exempt, exact("豁免规则"))
    }

    @Test
    fun quizCapSyntaxParsesWithAndWithoutPrefix() {
        assertEquals(BotCommand.SetQuizCap(30), exact("设置答题上限30"))
        assertEquals(BotCommand.SetQuizCap(30), exact("答题上限30"))
        assertEquals(BotCommand.SetQuizCap(30), exact("设置 答题上限 30"))
        assertEquals(BotCommand.SetQuizCap(0), exact("设置答题上限0"))
    }

    @Test
    fun quizCapWithoutMinutesAsksForThem() {
        assertTrue(BotCommandParser.parse("设置答题上限") is ParseResult.NeedArg)
    }

    @Test
    fun remindAndUndoParse() {
        assertEquals(BotCommand.Remind(true), exact("提醒开"))
        assertEquals(BotCommand.Remind(true), exact("打开提醒"))
        assertEquals(BotCommand.Remind(false), exact("提醒关"))
        assertEquals(BotCommand.Undo, exact("撤销"))
        assertEquals(BotCommand.Undo, exact("取消刚才"))
    }

    @Test
    fun v12QuizFieldsParseWithOptionalSetPrefix() {
        assertEquals(
            BotCommand.SetQuizField(BotCommand.QuizField.ROUND_COUNT, 10),
            exact("设置每轮题数10"),
        )
        assertEquals(
            BotCommand.SetQuizField(BotCommand.QuizField.ROUND_COUNT, 10),
            exact("每轮题数10"),
        )
        assertEquals(
            BotCommand.SetQuizField(BotCommand.QuizField.TIME_LIMIT, 45),
            exact("设置每题时限45"),
        )
        assertEquals(
            BotCommand.SetQuizField(BotCommand.QuizField.UNLOCK_GATE, 5),
            exact("设置门槛5"),
        )
        assertEquals(
            BotCommand.SetQuizField(BotCommand.QuizField.ROUND_CAP, 15),
            exact("设置单轮上限15"),
        )
        // Unit tails: minutes for minute fields, seconds-only for the timer.
        assertEquals(
            BotCommand.SetQuizField(BotCommand.QuizField.UNLOCK_GATE, 5),
            exact("设置门槛5分钟"),
        )
        assertEquals(
            BotCommand.SetQuizField(BotCommand.QuizField.TIME_LIMIT, 45),
            exact("设置每题时限45秒"),
        )
    }

    @Test
    fun minuteTailOnTheSecondFieldIsGuidedNotGuessed() {
        // "每题时限1分钟" must not silently become 1 second.
        assertTrue(BotCommandParser.parse("设置每题时限1分钟") is ParseResult.NeedArg)
    }

    @Test
    fun quizFieldWithoutValueAsksForIt() {
        assertTrue(BotCommandParser.parse("设置每轮题数") is ParseResult.NeedArg)
        assertTrue(BotCommandParser.parse("每题时限") is ParseResult.NeedArg)
    }

    @Test
    fun modeSwitchAndDailyReportParse() {
        assertEquals(BotCommand.ModeSwitch(auto = true), exact("模式自动"))
        assertEquals(BotCommand.ModeSwitch(auto = true), exact("自动模式"))
        assertEquals(BotCommand.ModeSwitch(auto = false), exact("模式手动"))
        assertEquals(BotCommand.ModeSwitch(auto = false), exact("切到手动"))
        assertEquals(BotCommand.DailyReport, exact("日报"))
        assertEquals(BotCommand.DailyReport, exact("今日报告"))
    }

    @Test
    fun v12CommandsCarryControlAndConfirmFlags() {
        assertTrue(BotCommand.SetQuizField(BotCommand.QuizField.ROUND_COUNT, 10).isControl)
        assertTrue(BotCommand.ModeSwitch(auto = true).isControl)
        // The mode switch mirrors the TV's confirm dialog in chat.
        assertTrue(BotCommand.ModeSwitch(auto = true).needsConfirm)
        assertFalse(BotCommand.SetQuizField(BotCommand.QuizField.ROUND_COUNT, 10).needsConfirm)
        assertFalse(BotCommand.DailyReport.isControl)
    }

    @Test
    fun politeAndWidthVariantsNormalize() {
        assertEquals(BotCommand.Stop, exact("停止吧"))
        assertEquals(BotCommand.Stop, exact(" 别看了 "))
        assertEquals(BotCommand.Status, exact("ＳＴＡＴＵＳ"))
        assertEquals(BotCommand.AddTime(20), exact("加时２０"))
    }

    @Test
    fun pinCarryingTextIsNoLongerACommand() {
        // Authentication is the binding: PIN forms are gone and must not parse.
        assertTrue(BotCommandParser.parse("验证123456") !is ParseResult.Exact)
        assertTrue(BotCommandParser.parse("解绑123456") !is ParseResult.Exact)
    }

    @Test
    fun addTimeAcceptsDigitsChineseNumeralsAndUnits() {
        assertEquals(BotCommand.AddTime(20), exact("加时20"))
        assertEquals(BotCommand.AddTime(20), exact("加时20分钟"))
        assertEquals(BotCommand.AddTime(20), exact("加时二十"))
        assertEquals(BotCommand.AddTime(45), exact("加时四十五"))
        assertEquals(BotCommand.AddTime(120), exact("加时一百二十分钟"))
        assertEquals(BotCommand.AddTime(30), exact("加时半小时"))
        assertEquals(BotCommand.AddTime(15), exact("加时一刻钟"))
        assertEquals(BotCommand.AddTime(60), exact("加时一小时"))
        assertEquals(BotCommand.AddTime(20), exact("再加20分钟"))
        assertEquals(BotCommand.AddTime(20), exact("延长２０"))
    }

    @Test
    fun addTimeWithoutMinutesAsksForThem() {
        val result = BotCommandParser.parse("加时")
        assertTrue(result is ParseResult.NeedArg)
    }

    @Test
    fun outOfRangeMinutesStillParseForTheRangeMessage() {
        // The admission layer owns the 5-120 rule so the parent gets the real
        // range hint instead of a bare "没听懂".
        assertEquals(BotCommand.AddTime(300), exact("加时300"))
    }

    @Test
    fun unbindIsPlain() {
        assertEquals(BotCommand.Unbind, exact("解绑"))
        assertEquals(BotCommand.Unbind, exact("unbind"))
    }

    @Test
    fun chineseNumbersCoverZeroToNineHundredNinetyNine() {
        assertEquals(0, BotCommandParser.chineseNumber("零"))
        assertEquals(2, BotCommandParser.chineseNumber("两"))
        assertEquals(10, BotCommandParser.chineseNumber("十"))
        assertEquals(15, BotCommandParser.chineseNumber("十五"))
        assertEquals(20, BotCommandParser.chineseNumber("二十"))
        assertEquals(99, BotCommandParser.chineseNumber("九十九"))
        assertEquals(120, BotCommandParser.chineseNumber("一百二十"))
        assertEquals(999, BotCommandParser.chineseNumber("九百九十九"))
        assertNull(BotCommandParser.chineseNumber("分钟"))
        assertNull(BotCommandParser.chineseNumber(""))
    }

    @Test
    fun closeTyposGetSuggestionsNeverExecution() {
        val result = BotCommandParser.parse("停看")
        assertTrue(result is ParseResult.Suggest)
        assertTrue((result as ParseResult.Suggest).candidates.contains("停止"))
    }

    @Test
    fun pinyinInitialsSuggestCommands() {
        val result = BotCommandParser.parse("zt")
        assertTrue(result is ParseResult.Suggest)
        assertTrue((result as ParseResult.Suggest).candidates.contains("状态"))
    }

    @Test
    fun ambiguousInitialsSuggestEveryMatch() {
        // "js" is both 加时 and 解锁; the parent must pick.
        val result = BotCommandParser.parse("js")
        assertTrue(result is ParseResult.Suggest)
        val candidates = (result as ParseResult.Suggest).candidates
        assertTrue(candidates.contains("加时"))
        assertTrue(candidates.contains("解锁"))
    }

    @Test
    fun smalltalkFallsThroughToHelp() {
        assertTrue(BotCommandParser.parse("你好") is ParseResult.Unknown)
        assertTrue(BotCommandParser.parse("") is ParseResult.Unknown)
    }

    @Test
    fun editDistanceBasics() {
        assertEquals(0, BotCommandParser.distance("停止", "停止"))
        assertEquals(1, BotCommandParser.distance("停看", "停止"))
        assertEquals(2, BotCommandParser.distance("ab", ""))
    }
}
