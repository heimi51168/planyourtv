package app.orchard.tv.quiz

import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocumentCodec
import com.planyourtv.policy.quiz.QuizConfigDocumentEtag
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizConfigDocumentAssemblerTest {
    private val now = 1_760_000_000_000L
    private val revision = "sha256:" + "a".repeat(64)

    @Before
    fun setUp() {
        com.planyourtv.policy.TvStore.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun assemblesCloudLevelsIntoSingleExecutableDocument() {
        val bundle = levelsBundle()

        val result = QuizConfigDocumentAssembler.assemble(rulesRoot(), bundle)
        assertTrue(result is QuizConfigDocumentAssembler.Result.Ok)
        val assembled = (result as QuizConfigDocumentAssembler.Result.Ok).bundle
        val document = assembled.document

        assertEquals(1, document.levels.size)
        val level = document.levels[0]
        assertEquals("cloud:11", level.levelId)
        assertEquals("第一关", level.name)
        assertEquals("BANK", level.sources.single().name)
        assertEquals("EASY", level.difficulty)
        assertEquals(listOf("SINGLE"), level.questionTypes.map { it.name })
        assertTrue(document.levelsEtag.startsWith("sha256:"))
        assertEquals(
            QuizConfigDocumentEtag.levelsEtag(document.levels),
            document.levelsEtag,
        )
        assertTrue(document.etag.startsWith("sha256:"))
        assertEquals(revision, document.extensions.getJSONObject("cloud").getString("levelsRevision"))
        assertEquals("CUSTOM", document.extensions.getJSONObject("cloud").getString("levelsSource"))
        // 打卡/可用题数只进信封，不进 QCD。
        assertFalse(document.extensions.has("checkin"))
        assertFalse(document.extensions.has("levelStatus"))

        assertEquals(7L, assembled.qbLearnerId)
        assertEquals(9L, assembled.memberId)
        assertEquals(revision, assembled.qbRevision)
        assertEquals(5, assembled.levelStatus.getJSONObject("cloud:11").getInt("availableCount"))
        assertEquals(11, assembled.levelStatus.getJSONObject("cloud:11").getInt("qbLevelId"))
    }

    @Test
    fun builtInDefaultLevelKeepsItsNegativeIdUnderCloudPrefix() {
        val result = QuizConfigDocumentAssembler.assemble(
            rulesRoot(),
            levelsBundle(levels = listOf(level(-4))),
        )

        assertTrue(result is QuizConfigDocumentAssembler.Result.Ok)
        val bundle = (result as QuizConfigDocumentAssembler.Result.Ok).bundle
        assertEquals("cloud:-4", bundle.document.levels.single().levelId)
        assertEquals(-4L, bundle.levelStatus.getJSONObject("cloud:-4").getLong("qbLevelId"))
    }

    @Test
    fun assemblesBackendShapedRulesAndPreservesUnknownTopLevelFields() {
        // 与后端 DeviceQuizRuleService.load() 的实际字段一致：顶层多出 learner/serverDayType/serverDate。
        val rules = JSONObject()
            .put("schemaVersion", 1)
            .put("documentId", "quizcfg:family:3:member:9")
            .put("revision", 4L)
            .put("origin", "CLOUD")
            .put("updatedAt", "2026-11-13T09:30:00+08:00")
            .put("updatedBy", "PARENT_APP")
            .put(
                "learner",
                JSONObject().put("id", 9).put("name", "小明").put("grade", 3).put("semester", 1),
            )
            .put("enabled", true)
            .put(
                "rules",
                JSONObject()
                    .put("questionCount", 5)
                    .put("timeLimitSec", 180)
                    .put(
                        "feedback",
                        JSONObject()
                            .put("extraHoldSec", 0)
                            .put("answerRevealSec", 30)
                            .put("autoNext", false),
                    )
                    .put("shuffleOptions", true)
                    .put("showPinyin", false)
                    .put("sourceOrder", JSONArray(listOf("WRONG", "PUBLIC", "PERSONAL")))
                    .put("subjects", JSONArray())
                    .put(
                        "legacy",
                        JSONObject()
                            .put("minCorrectCount", 1)
                            .put("minutesPerPoint", 5)
                            .put("minutesPerQuestion", 5)
                            .put("pointsPerQuestion", 1),
                    ),
            )
            .put(
                "reward",
                JSONObject()
                    .put("mode", "DIFFICULTY_SECONDS")
                    .put(
                        "profiles",
                        JSONObject().put(
                            "default",
                            JSONObject()
                                .put(
                                    "correctSeconds",
                                    JSONObject().put("EASY", 120)
                                        .put("MEDIUM", 180).put("HARD", 420),
                                )
                                .put(
                                    "penaltySeconds",
                                    JSONObject().put("EASY", 60)
                                        .put("MEDIUM", 120).put("HARD", 180),
                                )
                                .put("maxEarnedSeconds", 2700)
                                .put("minUnlockSeconds", 240),
                        ),
                    ),
            )
            .put(
                "quota",
                JSONObject().put("dailyMaxSeconds", 7200).put("enforcedBy", "CLOUD"),
            )
            .put(
                "calendar",
                JSONObject().put("timezone", "Asia/Shanghai")
                    .put("weekendDays", JSONArray(listOf(6, 7))),
            )
            .put(
                "questionPolicy",
                JSONObject().put("allowedTypes", JSONArray(listOf("SINGLE")))
                    .put("judgeMode", "LOCAL_OR_SERVER"),
            )
            .put(
                "requirements",
                JSONObject().put(
                    "required",
                    JSONArray(listOf("SINGLE", "REWARD_PROFILES")),
                ).put("optional", JSONArray(listOf("CHECKIN_GATE"))),
            )
            .put("serverDayType", "WEEKDAY")
            .put("serverDate", "2026-11-13")
            .put("etag", "")

        val result = QuizConfigDocumentAssembler.assemble(rules, levelsBundle())
        assertTrue(result is QuizConfigDocumentAssembler.Result.Ok)
        val document = (result as QuizConfigDocumentAssembler.Result.Ok).bundle.document

        assertEquals("WEEKDAY", document.extraTopLevel.getString("serverDayType"))
        assertEquals("2026-11-13", document.extraTopLevel.getString("serverDate"))
        assertEquals(9, document.extraTopLevel.getJSONObject("learner").getInt("id"))
        assertEquals(2700, document.reward.profiles.default!!.maxEarnedSeconds)
        assertEquals(7200, document.quota.dailyMaxSeconds)
        assertEquals(1, document.levels.size)
        assertEquals("cloud:11", document.levels.single().levelId)
    }

    @Test
    fun fillQuestionTypeIsRejectedAsUnsupportedWithoutPartialDocument() {
        val bundle = levelsBundle(questionTypes = listOf("FILL"))

        val result = QuizConfigDocumentAssembler.assemble(rulesRoot(), bundle)
        assertTrue(result is QuizConfigDocumentAssembler.Result.Unsupported)
        val issues = (result as QuizConfigDocumentAssembler.Result.Unsupported).issues
        assertTrue(issues.any { it.code == "UNSUPPORTED_FILL" })
    }

    @Test
    fun unknownSourceEnumIsInvalid() {
        val bundle = levelsBundle(sources = listOf("NOT_A_SOURCE"))

        val result = QuizConfigDocumentAssembler.assemble(rulesRoot(), bundle)
        assertTrue(result is QuizConfigDocumentAssembler.Result.Invalid)
    }

    @Test
    fun moreThanTwentyLevelsIsInvalid() {
        val levels = (1..21).map { id -> level(id.toLong()) }
        val bundle = levelsBundle(levels = levels)

        val result = QuizConfigDocumentAssembler.assemble(rulesRoot(), bundle)
        assertTrue(result is QuizConfigDocumentAssembler.Result.Invalid)
    }

    private fun rulesRoot(): JSONObject {
        val local = QuizLocalConfigDocument.create(
            installId = "install-id-test",
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

    private fun levelsBundle(
        levels: List<CloudLevel> = listOf(level(11)),
        questionTypes: List<String> = listOf("SINGLE"),
        sources: List<String> = listOf("BANK"),
    ): CloudLevelsBundle = CloudLevelsBundle(
        learnerId = 7,
        memberId = 9,
        levels = levels.map {
            if (questionTypes == listOf("SINGLE") && sources == listOf("BANK")) {
                it
            } else {
                it.copy(questionTypes = questionTypes, sources = sources)
            }
        },
        checkin = JSONObject().put("checkedToday", true),
        revision = revision,
        levelsSource = "CUSTOM",
    )

    private fun level(id: Long): CloudLevel = CloudLevel(
        id = id,
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
    )
}
