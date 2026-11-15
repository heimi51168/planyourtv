package app.orchard.tv.policy.web

import android.app.Application
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.quiz.QuizAccess
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.PinResult
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.TvStore
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.net.Socket
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The pre-login forgot-password flow. It must accept only the one-time recovery code, work
 * without a quiz session or editor lease, and leave the page able to log in immediately with
 * the new PIN even when the old PIN's login is still in its in-memory lockout.
 */
@RunWith(RobolectricTestRunner::class)
class ParentWebRecoverTest {
    private val oldPin = "246810"
    private val newPin = "135790"
    private val recoveryCode = "8642-9753-1086"
    private lateinit var app: Application
    private var server: NanoHTTPD? = null
    private var port = 0

    private fun rules() = LocalRules(
        weekdayWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        weekendWindows = listOf(LocalTimeWindow(0, 24 * 60)),
        autoDailyLimitMinutes = 60,
        sessionLimitMinutes = 20,
        restMinutes = 0,
        allowedPackages = emptySet(),
    )

    private fun body(code: String = recoveryCode, pin: String = newPin) =
        JSONObject().put("recoveryCode", code).put("newPin", pin)

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        assertTrue(TvStore.init(app))
        LocalPolicyRuntime.debugResetForTest()
        LocalPolicyRuntime.initialize(app)
        assertEquals(recoveryCode, LocalPolicyRuntime.completeSetup(oldPin, rules(), recoveryCode))
        PolicyModeCoordinator.debugForceMode(PolicyMode.LOCAL)
        QuizAccess.onPinReset()
        QuizAccess.invalidateWebSession("test_setup")
    }

    @After
    fun tearDown() {
        server?.stop()
        server = null
        LocalPolicyRuntime.debugResetForTest()
    }

    @Test
    fun recoveryCodeResetsPinWithoutLogin() {
        val outcome = ParentCenterActions.recoverPin(body())

        assertTrue(outcome.ok)
        assertEquals("PIN_RESET", outcome.messageKey)
        assertEquals(PinResult.OK, LocalPolicyRuntime.verifyPin(newPin))
        assertEquals(PinResult.WRONG, LocalPolicyRuntime.verifyPin(oldPin))
    }

    @Test
    fun wrongRecoveryCodeLeavesThePinAlone() {
        val outcome = ParentCenterActions.recoverPin(body(code = "1111-2222-3333"))

        assertFalse(outcome.ok)
        assertEquals("RECOVERY_INVALID", outcome.code)
        assertEquals(PinResult.OK, LocalPolicyRuntime.verifyPin(oldPin))
    }

    @Test
    fun malformedNewPinDoesNotConsumeARecoveryAttempt() {
        assertEquals("NEW_PIN_INVALID", ParentCenterActions.recoverPin(body(pin = "12345")).code)
        assertEquals("PIN_TOO_SIMPLE", ParentCenterActions.recoverPin(body(pin = "123456")).code)

        // A valid request still succeeds, proving the two rejected requests above did not
        // increment the persisted recovery counter.
        assertTrue(ParentCenterActions.recoverPin(body()).ok)
        assertEquals(PinResult.OK, LocalPolicyRuntime.verifyPin(newPin))
    }

    @Test
    fun fiveWrongCodesLockRecoveryWithoutTouchingThePin() {
        repeat(4) {
            assertEquals(
                "RECOVERY_INVALID",
                ParentCenterActions.recoverPin(body(code = "1111-2222-3333")).code,
            )
        }
        val locked = ParentCenterActions.recoverPin(body(code = "1111-2222-3333"))

        assertEquals("RECOVERY_LOCKED", locked.code)
        assertTrue(locked.retryAfterSeconds > 0)
        assertEquals(PinResult.OK, LocalPolicyRuntime.verifyPin(oldPin))
    }

    @Test
    fun recoveryClearsTheQuizLoginLockoutForTheNewPin() {
        // Five wrong logins lock the in-memory page login for ten minutes even though the
        // persisted PIN lockout is much shorter. Reset must clear both.
        repeat(5) { QuizAccess.authenticate("000000") }
        assertTrue(QuizAccess.authenticate(oldPin) is QuizAccess.AuthResult.PinLocked)

        assertTrue(ParentCenterActions.recoverPin(body()).ok)

        val login = QuizAccess.authenticate(newPin)
        assertTrue(login is QuizAccess.AuthResult.Ok)
    }

    @Test
    fun preLoginPostNeedsNoEditorLeaseAndReturnsNoSnapshot() {
        startServer()
        val response = post(
            "/api/parent/recover",
            body().toString(),
            mapOf("X-Parent" to "1", "Content-Type" to "application/json"),
        )

        assertEquals(200, response.code)
        assertTrue(response.body.contains("\"ok\":true"))
        assertFalse(response.body.contains("snapshot"))
        assertEquals(PinResult.OK, LocalPolicyRuntime.verifyPin(newPin))
    }

    @Test
    fun preLoginRecoverReportsWrongCodeAndRecoveryLockout() {
        startServer()
        val headers = mapOf("X-Parent" to "1", "Content-Type" to "application/json")
        val wrong = post(
            "/api/parent/recover",
            body(code = "1111-2222-3333").toString(),
            headers,
        )
        assertEquals(401, wrong.code)
        assertTrue(wrong.body.contains("RECOVERY_INVALID"))

        // The first request above already spent one attempt; four more spend the rest.
        repeat(3) { ParentCenterActions.recoverPin(body(code = "1111-2222-3333")) }
        val locked = post(
            "/api/parent/recover",
            body(code = "1111-2222-3333").toString(),
            headers,
        )
        assertEquals(429, locked.code)
        assertTrue(locked.body.contains("RECOVERY_LOCKED"))
        assertTrue(locked.body.contains("retryAfterSeconds"))
        assertEquals(PinResult.OK, LocalPolicyRuntime.verifyPin(oldPin))
    }

    @Test
    fun recoverIsLockedWhileWebConfigurationIsUnavailable() {
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        startServer()
        val response = post(
            "/api/parent/recover",
            body().toString(),
            mapOf("X-Parent" to "1", "Content-Type" to "application/json"),
        )

        assertEquals(423, response.code)
        assertTrue(response.body.contains("WEB_LOCAL_ONLY"))
        assertEquals(PinResult.OK, LocalPolicyRuntime.verifyPin(oldPin))
    }

    private data class HttpResponse(val code: Int, val body: String)

    private fun startServer() {
        val http = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response {
                val routed = ParentWebAdmin.handle(app, session, session.uri)
                return routed ?: newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
            }
        }
        http.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        server = http
        port = http.listeningPort
    }

    private fun post(path: String, body: String, headers: Map<String, String>): HttpResponse {
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 5_000
            val payload = body.toByteArray()
            val head = buildString {
                append("POST $path HTTP/1.1\r\n")
                append("Host: 127.0.0.1:$port\r\n")
                append("Connection: close\r\n")
                for ((name, value) in headers) append("$name: $value\r\n")
                append("Content-Length: ${payload.size}\r\n")
                append("\r\n")
            }
            socket.getOutputStream().apply {
                write(head.toByteArray())
                write(payload)
                flush()
            }
            return readResponse(socket.getInputStream())
        }
    }

    private fun readResponse(input: InputStream): HttpResponse {
        val statusLine = readLine(input)
        val code = statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: -1
        var length = 0
        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
            if (line.substringBefore(':').trim().equals("Content-Length", ignoreCase = true)) {
                length = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(body, offset, length - offset)
            if (read < 0) break
            offset += read
        }
        return HttpResponse(code, String(body, 0, offset, Charsets.UTF_8))
    }

    private fun readLine(input: InputStream): String {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) break
            if (byte == '\n'.code) break
            if (byte != '\r'.code) line.append(byte.toChar())
        }
        return line.toString()
    }
}
