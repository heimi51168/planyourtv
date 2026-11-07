package app.orchard.tv.quiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizGenerationRequestTest {
    @Test fun defaultsAreSafeForTheWebEditor() {
        val parsed = QuizGeneration.parseRequest(JSONObject())
        assertTrue(parsed is QuizGeneration.ParseResult.Ok)
        val request = (parsed as QuizGeneration.ParseResult.Ok).request
        assertEquals(20, request.targetCount)
        assertEquals(4, request.options)
        assertEquals("zh", request.language)
        assertEquals("", request.prompt)
    }

    @Test fun acceptsTheFullParentRange() {
        val parsed = QuizGeneration.parseRequest(
            JSONObject()
                .put("targetCount", 50)
                .put("options", 6)
                .put("language", "zh-Hans")
                .put("subject", "animals")
                .put("grade", "Grade 4 · Semester 2")
                .put("prompt", "Grade 4 science: animals and their habitats, unit 3.")
                .put("difficulty", "hard")
                .put("points", 5)
                .put("timeLimitSec", 300),
        )
        assertTrue(parsed is QuizGeneration.ParseResult.Ok)
        assertEquals(
            50,
            (parsed as QuizGeneration.ParseResult.Ok).request.targetCount,
        )
    }

    @Test fun legacySingleBatchCountStillWorks() {
        val parsed = QuizGeneration.parseRequest(JSONObject().put("count", 10))
        assertTrue(parsed is QuizGeneration.ParseResult.Ok)
        assertEquals(10, (parsed as QuizGeneration.ParseResult.Ok).request.targetCount)
    }

    @Test fun acceptsCanonicalScopeMetadata() {
        val parsed = QuizGeneration.parseRequest(
            JSONObject()
                .put("subjectId", "math")
                .put("gradeNo", 4)
                .put("semester", 2)
                .put("textbook", "renjiao")
                .put("scopeType", "unit")
                .put("unitNo", 3)
                .put("difficulty", "medium"),
        )
        assertTrue(parsed is QuizGeneration.ParseResult.Ok)
        val scope = (parsed as QuizGeneration.ParseResult.Ok).request.scope()
        assertNotNull(scope)
        assertEquals(4, scope!!.grade)
        assertEquals(2, scope.semester)
        assertEquals("MATH", scope.subject)
        assertEquals("RENJIAO", scope.textbook)
        assertEquals("UNIT", scope.scopeType)
        assertEquals(3, scope.unitNo)
        assertEquals("MEDIUM", scope.difficulty)
    }

    @Test fun rejectsIncompleteOrInconsistentScopeMetadata() {
        val incomplete = QuizGeneration.parseRequest(
            JSONObject().put("subjectId", "math"),
        )
        assertTrue(incomplete is QuizGeneration.ParseResult.Invalid)

        val unitOutsideUnitScope = QuizGeneration.parseRequest(
            JSONObject()
                .put("subjectId", "math")
                .put("gradeNo", 4)
                .put("semester", 1)
                .put("textbook", "renjiao")
                .put("scopeType", "final")
                .put("unitNo", 3),
        )
        assertTrue(unitOutsideUnitScope is QuizGeneration.ParseResult.Invalid)

        val missingUnit = QuizGeneration.parseRequest(
            JSONObject()
                .put("subjectId", "math")
                .put("gradeNo", 4)
                .put("semester", 1)
                .put("textbook", "renjiao")
                .put("scopeType", "unit"),
        )
        assertTrue(missingUnit is QuizGeneration.ParseResult.Invalid)
    }

    @Test fun rememberedGenerationPrefsAreWhitelisted() {
        val prefs = JSONObject()
            .put("grade", "4b")
            .put("subject", "math")
            .put("textbook", "renjiao")
            .put("scope", "unit")
            .put("unit", 3)
            .put("targetCount", 30)
            .put("options", 4)
            .put("difficulty", "medium")
            .put("customTopic", "  分数  应用题  ")
        val parsed = QuizGeneration.parsePreferences(prefs)
        assertTrue(parsed != null)
        assertEquals("4b", parsed!!.optString("grade"))
        assertEquals(30, parsed.optInt("targetCount"))
        assertEquals("分数 应用题", parsed.optString("customTopic"))
        assertTrue(parsed.optBoolean("autoAdd", false))
        val noAutoAdd = JSONObject(prefs.toString())
        noAutoAdd.remove("autoAdd")
        assertTrue(QuizGeneration.parsePreferences(noAutoAdd)!!.optBoolean("autoAdd", false))
        assertFalse(
            QuizGeneration.parsePreferences(
                JSONObject(prefs.toString()).put("autoAdd", false),
            )!!.optBoolean("autoAdd", true),
        )
        assertNull(
            QuizGeneration.parsePreferences(
                JSONObject(prefs.toString()).put("autoAdd", "yes"),
            ),
        )
        assertEquals(0, parsed.optInt("presetVersion", -1))
        assertEquals(
            2,
            QuizGeneration.parsePreferences(
                JSONObject(prefs.toString()).put("presetVersion", 2),
            )!!.optInt("presetVersion", -1),
        )
        assertEquals(
            3,
            QuizGeneration.parsePreferences(
                JSONObject(prefs.toString()).put("presetVersion", 3),
            )!!.optInt("presetVersion", -1),
        )
        assertNull(
            QuizGeneration.parsePreferences(
                JSONObject(prefs.toString()).put("presetVersion", 4),
            ),
        )
        assertNull(
            QuizGeneration.parsePreferences(
                JSONObject(prefs.toString()).put("presetVersion", "2"),
            ),
        )
        assertEquals(
            "",
            QuizGeneration.parsePreferences(
                JSONObject(prefs.toString()).put("customTopic", ""),
            )!!.optString("customTopic"),
        )
        assertNull(QuizGeneration.parsePreferences(prefs.put("subject", "unsafe")))
        assertNull(QuizGeneration.parsePreferences(prefs.put("subject", "math").put("grade", "7a")))
        assertNull(QuizGeneration.parsePreferences(prefs.put("targetCount", 25)))
        prefs.put("subject", "math").put("grade", "4b").put("targetCount", 30)
        assertNull(
            QuizGeneration.parsePreferences(
                prefs.put("customTopic", "x".repeat(QuizGeneration.MAX_CUSTOM_TOPIC_CHARS + 1)),
            ),
        )
        assertNull(QuizGeneration.parsePreferences(prefs.put("customTopic", 42)))
    }

    @Test fun rejectsValuesOutsideTheDocumentedLimits() {
        val parsed = QuizGeneration.parseRequest(
            JSONObject()
                .put("targetCount", 25)
                .put("options", 7)
                .put("language", "zh<script>")
                .put("subject", "x".repeat(33))
                .put("grade", "x".repeat(QuizGeneration.MAX_GRADE_CHARS + 1))
                .put("prompt", "x".repeat(401))
                .put("difficulty", "extreme"),
        )
        assertTrue(parsed is QuizGeneration.ParseResult.Invalid)
        assertEquals(7, (parsed as QuizGeneration.ParseResult.Invalid).fields.size)
    }

    @Test fun providerMetaOnlyAllowsHttpsOriginsWithoutSecrets() {
        assertEquals(
            "https://api.deepseek.com",
            QuizProviderMeta.normalizeBaseUrl(" https://api.deepseek.com/ "),
        )
        assertNull(QuizProviderMeta.normalizeBaseUrl("http://api.deepseek.com"))
        assertNull(QuizProviderMeta.normalizeBaseUrl("https://user:pass@api.deepseek.com"))
        assertNull(QuizProviderMeta.normalizeBaseUrl("https://api.deepseek.com?x=1"))
        assertNull(QuizProviderMeta.normalizeBaseUrl("https://api.deepseek.com#x"))
        assertEquals("deepseek-chat", QuizProviderMeta.normalizeModel("deepseek-chat"))
        assertNull(QuizProviderMeta.normalizeModel("deepseek chat"))
        assertNull(QuizProviderMeta.normalizeModel("../../key"))
        assertEquals(QuizProviderMeta(), QuizProviderMeta.fromJson("not json"))
    }
}
