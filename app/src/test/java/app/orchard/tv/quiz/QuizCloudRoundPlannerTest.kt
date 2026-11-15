package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizConfigDocumentCodec
import com.planyourtv.policy.quiz.QuizDayType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/** Pins the read side of a cloud round: the active QCD alone selects levels and payout. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizCloudRoundPlannerTest {
    private val now = 1_760_000_000_000L

    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun plansTheFrozenWeekendProfileAndItsLevelAvailability() {
        val result = QuizCloudRoundPlanner.plan(active(), at(2026, Calendar.NOVEMBER, 14, 10))

        assertTrue("expected Ready, got $result", result is QuizCloudPlanResult.Ready)
        val plan = (result as QuizCloudPlanResult.Ready).plan
        assertEquals(QuizDayType.WEEKEND, plan.dayType)
        // The local defaults are frozen into the active document by the assembler.
        assertEquals(60, plan.profile.correctSeconds.easy)
        assertEquals(120, plan.profile.correctSeconds.medium)
        assertEquals(300, plan.profile.correctSeconds.hard)
        assertEquals(3600, plan.profile.maxEarnedSeconds)
        assertEquals(9L, plan.memberId)
        assertEquals(7L, plan.qbLearnerId)
        assertEquals(5, plan.config.pullCount)
        assertFalse(plan.checkinBlocked)

        val level = plan.levels.single()
        assertEquals("cloud:11", level.id)
        assertEquals(11L, level.qbLevelId)
        assertEquals(5, level.questionCount)
        assertEquals(5, level.available)
    }

    @Test
    fun aLevelWithoutItsDailyStatusIsRejectedInsteadOfAddedBlindly() {
        val result = QuizCloudRoundPlanner.plan(
            active(levelStatus = JSONObject()),
            at(2026, Calendar.NOVEMBER, 14, 10),
        )

        assertEquals(
            QuizCloudPlanResult.Rejected(QuizCloudRoundPlanner.LEVEL_STATUS_MISSING),
            result,
        )
    }

    @Test
    fun aDisabledDocumentRejectsTheRound() {
        val active = active()
        val disabled = active.copy(
            cloud = active.cloud.copy(document = active.cloud.document.copy(enabled = false)),
        )

        assertEquals(
            QuizCloudPlanResult.Rejected(QuizCloudRoundPlanner.DISABLED),
            QuizCloudRoundPlanner.plan(disabled, at(2026, Calendar.NOVEMBER, 14, 10)),
        )
    }

    private fun active(levelStatus: JSONObject = status()): ActiveCloudQuizDocument {
        val assembled = QuizConfigDocumentAssembler.assemble(rulesRoot(), levelsBundle())
        assertTrue("fixture must assemble", assembled is QuizConfigDocumentAssembler.Result.Ok)
        val bundle = (assembled as QuizConfigDocumentAssembler.Result.Ok).bundle
        return ActiveCloudQuizDocument(
            learnerId = bundle.qbLearnerId,
            cloud = CloudQuizDocument(
                document = bundle.document,
                fetchedAt = now,
                qbRevision = bundle.qbRevision,
                levelsSource = bundle.levelsSource,
                checkin = JSONObject().put("blocked", false),
                levelStatus = levelStatus,
            ),
        )
    }

    private fun status(): JSONObject = JSONObject().put(
        "cloud:11",
        JSONObject()
            .put("qbLevelId", 11L)
            .put("available", true)
            .put("availableCount", 5)
            .put("questionCount", 5),
    )

    private fun rulesRoot(): JSONObject {
        val local: QuizConfigDocument = QuizLocalConfigDocument.create(
            installId = "planner-test",
            nowMs = now,
            config = QuizConfig(pullCount = 5),
            legacyAnswerDisplaySec = 3,
            levels = emptyList(),
            migrated = false,
        )
        val root = QuizConfigDocumentCodec.encodeDocument(local)
        root.put("origin", "CLOUD")
        root.put("documentId", "quizcfg:family:3:member:9")
        root.put("updatedBy", "PARENT_APP")
        root.put("revision", 4L)
        root.put("etag", "")
        root.put("levelsEtag", "")
        root.put("levels", JSONArray())
        return root
    }

    private fun levelsBundle(): CloudLevelsBundle = CloudLevelsBundle(
        learnerId = 7,
        memberId = 9,
        levels = listOf(
            CloudLevel(
                id = 11,
                name = "第一关",
                enabled = true,
                sortNo = 0,
                sources = listOf("BANK"),
                subjects = listOf("MATH"),
                scopeType = "MIDTERM",
                unitNos = listOf(1, 2),
                unitGroups = emptyList(),
                textbookId = 3,
                grade = 3,
                semester = 1,
                difficulty = "easy",
                advisoryQuestionCount = 5,
                questionTypes = listOf("SINGLE"),
                sourceScopes = JSONObject()
                    .put("subjects", JSONArray(listOf("MATH")))
                    .put("units", JSONObject().put("MATH", JSONArray(listOf(1, 2))))
                    .put("categories", JSONObject())
                    .put("knowledgePoints", JSONObject()),
                origin = "CLOUD",
                questionCount = 5,
                available = true,
                availableCount = 5,
                unavailableReason = null,
            ),
        ),
        checkin = JSONObject().put("checkedToday", true),
        revision = "sha256:" + "a".repeat(64),
        levelsSource = "CUSTOM",
    )

    /** 10:00 local time in the QCD's Shanghai timezone. */
    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis
}
