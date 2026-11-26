package app.orchard.tv.policy.web

import android.app.Application
import android.os.PowerManager
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.SourceEntrySettings
import app.orchard.tv.policy.SystemEntrySettings
import app.orchard.tv.policy.UserConfigPinSettings
import app.orchard.tv.quiz.QuizAccess
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.TvStore
import fi.iki.elonen.NanoHTTPD
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * V47 HTTP contract, unified in V59: the user-configuration switch covers the time rules, the
 * one-time timer, the source/quiz display toggles and the V58 fruit ledger. With it on, those
 * routes need one shared USER_EDIT proof per visit; with it off they need none. Turning the
 * protection off needs a single-use USER_CONFIG proof; every system-configuration route keeps
 * its own fixed scope either way.
 */
@RunWith(RobolectricTestRunner::class)
class ParentWebAdminUserConfigPinTest {
    private val pin = "246810"
    private val videoPackage = "com.example.video"
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
        restMinutes = 5,
        allowedPackages = setOf(videoPackage),
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
        ParentSensitiveAuth.resetForTest()
        ParentWebAccess.invalidate("test_setup")
        // Robolectric shares process-wide objects across methods; a remembered phone from an
        // earlier method would otherwise answer ParentInUse instead of issuing a token.
        QuizAccess.invalidateWebSession("test_setup")
        QuizAccess.onPinReset()
        UserConfigPinSettings.debugResetForTest()
        UserConfigPinSettings.install(app)
        SourceEntrySettings.install(app)
        SystemEntrySettings.install(app)
        SystemEntrySettings.debugResetForTest()
        SystemEntrySettings.install(app)
        // Route-contract cases below assume protection is on; default-off is covered by
        // UserConfigPinSettingsTest.
        assertTrue(UserConfigPinSettings.setRequired(true))

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
        UserConfigPinSettings.debugResetForTest()
        SystemEntrySettings.debugResetForTest()
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
        ParentSensitiveAuth.resetForTest()
        QuizAccess.onPinReset()
    }

    private fun post(
        path: String,
        body: String = "{}",
        sensitiveToken: String? = null,
    ): HttpResponse {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("X-Parent", "1")
        connection.setRequestProperty("X-Quiz-Admin-Token", quizToken)
        connection.setRequestProperty("X-Parent-Editor-Token", editorToken)
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

    private fun userConfigToken(): String {
        val result = ParentSensitiveAuth.verify(
            scopeValue = "USER_CONFIG",
            pin = pin,
            editorToken = editorToken,
        )
        assertTrue(result is ParentSensitiveAuth.VerifyResult.Ok)
        return (result as ParentSensitiveAuth.VerifyResult.Ok).token
    }

    @Test
    fun rulesRequiresAProofOnlyWhileTheSwitchIsOn() {
        val locked = post("/api/parent/rules")
        assertEquals(403, locked.status)
        assertEquals("SENSITIVE_REQUIRED", locked.json.getString("error"))

        assertTrue(UserConfigPinSettings.setRequired(false))
        val pinless = post("/api/parent/rules")
        // The empty body is rejected by the rules action, not by the missing PIN proof.
        assertEquals(409, pinless.status)
        assertEquals("STATE_CHANGED", pinless.json.getString("error"))
    }

    @Test
    fun disablingUsesASingleUseProofAndEnablingNeedsNone() {
        val missing = post("/api/parent/user-config-pin/disable")
        assertEquals(403, missing.status)
        assertEquals("SENSITIVE_REQUIRED", missing.json.getString("error"))

        val token = userConfigToken()
        val disabled = post("/api/parent/user-config-pin/disable", sensitiveToken = token)
        assertEquals(200, disabled.status)
        assertEquals("USER_CONFIG_PIN_SAVED", disabled.json.getString("messageKey"))
        assertFalse(
            disabled.json
                .getJSONObject("snapshot")
                .getBoolean("userConfigRequirePin"),
        )
        assertFalse(UserConfigPinSettings.isRequired())

        // The same proof cannot be replayed after the action succeeded.
        val reused = post("/api/parent/user-config-pin/disable", sensitiveToken = token)
        assertEquals(403, reused.status)
        assertEquals("SENSITIVE_EXPIRED", reused.json.getString("error"))

        val enabled = post("/api/parent/user-config-pin/enable")
        assertEquals(200, enabled.status)
        assertTrue(
            enabled.json
                .getJSONObject("snapshot")
                .getBoolean("userConfigRequirePin"),
        )
        assertTrue(UserConfigPinSettings.isRequired())
    }

    @Test
    fun displayTogglesFollowTheSameUserConfigSwitch() {
        assertTrue(UserConfigPinSettings.setRequired(false))
        val pinless = post("/api/parent/source-entries", """{"enabled":true}""")
        assertEquals(200, pinless.status)
        assertEquals("SOURCE_SAVED", pinless.json.getString("messageKey"))

        assertTrue(UserConfigPinSettings.setRequired(true))
        val locked = post("/api/parent/quiz-entry", """{"visible":true}""")
        assertEquals(403, locked.status)
        assertEquals("SENSITIVE_REQUIRED", locked.json.getString("error"))

        val token = ParentSensitiveAuth.verify(
            scopeValue = "USER_EDIT",
            pin = pin,
            editorToken = editorToken,
        )
        assertTrue(token is ParentSensitiveAuth.VerifyResult.Ok)
        val allowed = post(
            "/api/parent/quiz-entry",
            """{"visible":true}""",
            sensitiveToken = (token as ParentSensitiveAuth.VerifyResult.Ok).token,
        )
        assertEquals(200, allowed.status)
        assertEquals("QUIZ_ENTRY_SAVED", allowed.json.getString("messageKey"))
    }

    @Test
    fun systemEntrySwitchIsUserConfigAndDefaultsOff() {
        // The snapshot exposes the LOCAL-only release switch as off and editable in LOCAL mode.
        assertFalse(SystemEntrySettings.isVisible())
        // Exercise the off path first; setUp leaves the V59 user-config PIN on for the other
        // route-contract cases.
        assertTrue(UserConfigPinSettings.setRequired(false))
        val enabled = post("/api/parent/system-entries", """{"visible":true}""")
        assertEquals(200, enabled.status)
        assertEquals("SYSTEM_ENTRY_SAVED", enabled.json.getString("messageKey"))
        assertTrue(SystemEntrySettings.isVisible())
        val snapshot = enabled.json.getJSONObject("snapshot")
        assertTrue(snapshot.getBoolean("showUninstallEntry"))
        assertTrue(
            snapshot.getJSONObject("capabilities").getBoolean("canToggleSystemEntries"),
        )

        val hidden = post("/api/parent/system-entries", """{"visible":false}""")
        assertEquals(200, hidden.status)
        assertFalse(SystemEntrySettings.isVisible())
        assertFalse(hidden.json.getJSONObject("snapshot").getBoolean("showUninstallEntry"))

        // The V47 switch still gates the release route once it is turned back on.
        assertTrue(UserConfigPinSettings.setRequired(true))
        val locked = post("/api/parent/system-entries", """{"visible":true}""")
        assertEquals(403, locked.status)
        assertEquals("SENSITIVE_REQUIRED", locked.json.getString("error"))

        val token = ParentSensitiveAuth.verify(
            scopeValue = "USER_EDIT",
            pin = pin,
            editorToken = editorToken,
        )
        assertTrue(token is ParentSensitiveAuth.VerifyResult.Ok)
        val allowed = post(
            "/api/parent/system-entries",
            """{"visible":true}""",
            sensitiveToken = (token as ParentSensitiveAuth.VerifyResult.Ok).token,
        )
        assertEquals(200, allowed.status)
        assertTrue(SystemEntrySettings.isVisible())
    }
}