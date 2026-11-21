package app.orchard.tv.policy.web

import android.app.Application
import android.os.PowerManager
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.quiz.QuizAccess
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.MemberStatus
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.TvStore
import fi.iki.elonen.NanoHTTPD
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * V41 HTTP contract: entering Family Members needs a fresh parent-PIN proof. The page entry is
 * only a convenience; every add, edit, photo and delete route repeats the FAMILY_EDIT check so
 * a child cannot change member settings by calling the local API directly.
 */
@RunWith(RobolectricTestRunner::class)
class ParentWebAdminFamilyEditTest {
    private val pin = "246810"
    private val addedName = "三宝" + (System.nanoTime() % 100000L).toString().padStart(5, '0')
    private lateinit var app: Application
    private lateinit var server: NanoHTTPD
    private lateinit var quizToken: String
    private lateinit var editorToken: String
    private var port = 0

    private data class HttpResponse(val status: Int, val json: JSONObject)

    private fun rules() = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
            weekdayLimitMinutes = 60,
            weekendLimitMinutes = 60,
        ),
        sessionLimitMinutes = 20,
        restMinutes = 0,
        allowedPackages = emptySet(),
    )

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        LocalPolicyRuntime.initialize(app)
        assertNotNull(LocalPolicyRuntime.completeSetup(pin, rules()))
        LocalPolicyRuntime.setSource(PolicySource.LOCAL)
        PolicyModeCoordinator.debugForceMode(PolicyMode.LOCAL)
        ParentCenterActions.install(app)
        ParentSensitiveAuth.resetForTest()
        ParentWebAccess.invalidate("test_setup")
        QuizAccess.invalidateWebSession("test_setup")
        QuizAccess.onPinReset()

        val login = QuizAccess.authenticate(pin)
        assertTrue(login is QuizAccess.AuthResult.Ok)
        quizToken = (login as QuizAccess.AuthResult.Ok).adminToken
        val acquired = ParentWebAccess.acquire(quizToken, takeover = false)
        assertTrue(acquired is ParentWebAccess.AcquireOutcome.Ok)
        editorToken = (acquired as ParentWebAccess.AcquireOutcome.Ok).editorToken

        server = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response =
                ParentWebAdmin.handle(app, session, session.uri)
                    ?: newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT,
                        "",
                    )
        }
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        port = server.listeningPort
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) server.stop()
        ParentWebAccess.invalidate("test_teardown")
        QuizAccess.invalidateWebSession("test_teardown")
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
        ParentSensitiveAuth.resetForTest()
        QuizAccess.onPinReset()
    }

    private fun householdHeaders(connection: HttpURLConnection) {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("X-Parent", "1")
        connection.setRequestProperty("X-Quiz-Admin-Token", quizToken)
        connection.setRequestProperty("X-Parent-Editor-Token", editorToken)
    }

    private fun post(
        path: String,
        body: String = "{}",
        sensitiveToken: String? = null,
    ): HttpResponse {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        householdHeaders(connection)
        connection.setRequestProperty("Content-Type", "application/json")
        if (sensitiveToken != null) {
            connection.setRequestProperty("X-Parent-Sensitive-Token", sensitiveToken)
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(status, JSONObject(text))
    }

    private fun postEmptyAvatar(userId: String, sensitiveToken: String? = null): HttpResponse {
        val connection =
            URL("http://127.0.0.1:$port/api/parent/subjects/avatar?userId=$userId")
                .openConnection() as HttpURLConnection
        householdHeaders(connection)
        connection.setRequestProperty("Content-Type", "image/jpeg")
        if (sensitiveToken != null) {
            connection.setRequestProperty("X-Parent-Sensitive-Token", sensitiveToken)
        }
        connection.setFixedLengthStreamingMode(0)
        connection.outputStream.close()
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(status, JSONObject(text))
    }

    private fun familyToken(): String {
        val result = ParentSensitiveAuth.verify(
            scopeValue = "FAMILY_EDIT",
            pin = pin,
            editorToken = editorToken,
        )
        assertTrue(result is ParentSensitiveAuth.VerifyResult.Ok)
        return (result as ParentSensitiveAuth.VerifyResult.Ok).token
    }

    private fun rulesToken(): String {
        val result = ParentSensitiveAuth.verify(
            scopeValue = "RULES_EDIT",
            pin = pin,
            editorToken = editorToken,
        )
        assertTrue(result is ParentSensitiveAuth.VerifyResult.Ok)
        return (result as ParentSensitiveAuth.VerifyResult.Ok).token
    }

    private fun assertRequired(result: HttpResponse) {
        assertEquals(403, result.status)
        assertEquals("SENSITIVE_REQUIRED", result.json.getString("error"))
    }

    @Test
    fun everyFamilyWriteRouteRejectsAMissingProof() {
        assertRequired(post("/api/parent/subjects/add", """{"name":"$addedName"}"""))
        assertRequired(
            post(
                "/api/parent/subjects/profile",
                """{"userId":"usr_missing","name":"$addedName"}""",
            ),
        )
        assertRequired(
            post("/api/parent/subjects/avatar/delete", """{"userId":"usr_missing"}"""),
        )
        assertRequired(
            post("/api/parent/subjects/delete", """{"userId":"usr_missing"}"""),
        )
        assertRequired(postEmptyAvatar("usr_missing"))
    }

    @Test
    fun aRulesProofIsNotAFamilyProof() {
        val rules = rulesToken()
        val rejected = post(
            "/api/parent/subjects/add",
            """{"name":"$addedName"}""",
            sensitiveToken = rules,
        )
        assertEquals(403, rejected.status)
        assertEquals("SENSITIVE_SCOPE", rejected.json.getString("error"))
    }

    @Test
    fun aFamilyProofAllowsAddEditAndDelete() {
        val token = familyToken()
        val added = post(
            "/api/parent/subjects/add",
            """{"name":"$addedName","avatarPreset":"boy","switchPinRequired":false}""",
            sensitiveToken = token,
        )
        assertEquals(200, added.status)
        val userId = added.json
            .getJSONObject("snapshot")
            .getJSONArray("members")
            .let { members ->
                (0 until members.length())
                    .map { members.getJSONObject(it) }
                    .first { it.getString("name") == addedName }
                    .getString("userId")
            }

        val profile = post(
            "/api/parent/subjects/profile",
            JSONObject()
                .put("userId", userId)
                .put("name", "二宝改")
                .put("avatarPreset", "boy")
                .toString(),
            sensitiveToken = token,
        )
        assertEquals(200, profile.status)

        val deleted = post(
            "/api/parent/subjects/delete",
            JSONObject().put("userId", userId).toString(),
            sensitiveToken = token,
        )
        assertEquals(200, deleted.status)
    }

    @Test
    fun theAvatarUploadChecksTheProofBeforeConsumingTheBody() {
        val family = FamilyHub.state.value
        val userId = family.members.first { it.status == MemberStatus.ACTIVE }.userId
        assertRequired(postEmptyAvatar(userId))
        // With a proof the empty body reaches the length check instead of the PIN gate.
        val withProof = postEmptyAvatar(userId, familyToken())
        assertEquals(400, withProof.status)
        assertEquals("SUBJECT_AVATAR_EMPTY", withProof.json.getString("error"))
    }
}