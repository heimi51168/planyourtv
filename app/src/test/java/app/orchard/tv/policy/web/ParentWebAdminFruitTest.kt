package app.orchard.tv.policy.web

import android.app.Application
import android.os.PowerManager
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.UserConfigPinSettings
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
 * V58 HTTP contract: reading and writing the LOCAL fruit ledger is parent data, not child data.
 * It needs the editor lease plus the shared USER_EDIT proof while the V47 switch is on, writes
 * by expected revision, and fails closed outside LOCAL mode. V59 adds manual expenses.
 */
@RunWith(RobolectricTestRunner::class)
class ParentWebAdminFruitTest {
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
        restMinutes = 0,
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
        ParentCenterActions.install(app)
        UserConfigPinSettings.debugResetForTest()
        UserConfigPinSettings.install(app)
        // The PIN-path cases below assume protection is on; the pinless case flips it off.
        assertTrue(UserConfigPinSettings.setRequired(true))
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
        UserConfigPinSettings.debugResetForTest()
        PolicyModeCoordinator.debugForceMode(PolicyMode.NONE)
        LocalPolicyRuntime.debugResetForTest()
        ParentSensitiveAuth.resetForTest()
        QuizAccess.onPinReset()
    }

    private fun activeUserId(): String =
        FamilyHub.state.value.members.first { it.status == MemberStatus.ACTIVE }.userId

    private fun householdHeaders(connection: HttpURLConnection) {
        connection.setRequestProperty("X-Parent", "1")
        connection.setRequestProperty("X-Quiz-Admin-Token", quizToken)
        connection.setRequestProperty("X-Parent-Editor-Token", editorToken)
    }

    private fun get(path: String, sensitiveToken: String? = null): HttpResponse {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        householdHeaders(connection)
        connection.requestMethod = "GET"
        if (sensitiveToken != null) {
            connection.setRequestProperty("X-Parent-Sensitive-Token", sensitiveToken)
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpResponse(status, JSONObject(text))
    }

    private fun post(
        path: String,
        body: String = "{}",
        sensitiveToken: String? = null,
    ): HttpResponse {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        householdHeaders(connection)
        connection.requestMethod = "POST"
        connection.doOutput = true
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

    private fun userEditToken(): String {
        val result = ParentSensitiveAuth.verify(
            scopeValue = "USER_EDIT",
            pin = pin,
            editorToken = editorToken,
        )
        assertTrue(result is ParentSensitiveAuth.VerifyResult.Ok)
        return (result as ParentSensitiveAuth.VerifyResult.Ok).token
    }

    private fun ledger(userId: String, token: String): HttpResponse = get(
        "/api/parent/fruit/ledger?userId=" + userId,
        token,
    )

    @Test
    fun theLedgerNeedsTheSharedUserEditProofAndCreatesAnEmptyLocalAccount() {
        val userId = activeUserId()
        val denied = ledger(userId, token = "")
        assertEquals(403, denied.status)
        assertEquals("SENSITIVE_REQUIRED", denied.json.getString("error"))

        val allowed = ledger(userId, userEditToken())
        assertEquals(200, allowed.status)
        val account = allowed.json.getJSONObject("account")
        assertEquals(0L, account.getLong("availableFruitHundredths"))
        assertEquals(1, account.getInt("exchangeRateMinutesPerFruit"))
        assertEquals(1L, account.getLong("revision"))
        assertTrue(allowed.json.getBoolean("editable"))
    }

    @Test
    fun creditRequiresTheFruitProofAndAStaleRevisionConflicts() {
        val userId = activeUserId()
        val token = userEditToken()
        val body = JSONObject()
            .put("userId", userId)
            .put("expectedRevision", 1L)
            .put("fruits", 5)
            .put("title", "做家务")
            .put("note", "扫地")
            .put("requestId", "req-fruit-1")
            // Cached pages used to send this as JSON null even when no quick project was
            // requested; the server must treat null as an absent optional field.
            .put("saveQuickProject", false)
            .put("quickProjectDefaultFruits", JSONObject.NULL)
            .toString()

        val denied = post("/api/parent/fruit/credit", body)
        assertEquals(403, denied.status)
        assertEquals("SENSITIVE_REQUIRED", denied.json.getString("error"))

        val credited = post("/api/parent/fruit/credit", body, token)
        assertEquals(200, credited.status)
        assertEquals("FRUIT_CREDITED", credited.json.getString("messageKey"))
        val after = ledger(userId, token)
        val account = after.json.getJSONObject("account")
        assertEquals(500L, account.getLong("availableFruitHundredths"))
        assertEquals(2L, account.getLong("revision"))
        val summary = credited.json.getJSONObject("snapshot")
            .getJSONObject("fruitAccounts")
            .getJSONObject(userId)
        assertEquals(500L, summary.getLong("availableFruitHundredths"))

        // The same body again is a stale write, not a second credit.
        val stale = post("/api/parent/fruit/credit", body, token)
        assertEquals(409, stale.status)
        assertEquals("FRUIT_REVISION_CONFLICT", stale.json.getString("error"))
        assertEquals(
            500L,
            ledger(userId, token).json.getJSONObject("account")
                .getLong("availableFruitHundredths"),
        )
    }

    @Test
    fun voidRestoresAnUnspentCreditWithoutAllowingASecondVoid() {
        val userId = activeUserId()
        val token = userEditToken()
        val credited = post(
            "/api/parent/fruit/credit",
            JSONObject()
                .put("userId", userId)
                .put("expectedRevision", 1L)
                .put("fruits", 2)
                .put("title", "阅读")
                .toString(),
            token,
        )
        assertEquals(200, credited.status)
        val entryId = ledger(userId, token).json
            .getJSONObject("account")
            .getJSONArray("entries")
            .getJSONObject(0)
            .getString("id")

        val voided = post(
            "/api/parent/fruit/void",
            JSONObject()
                .put("userId", userId)
                .put("expectedRevision", 2L)
                .put("entryId", entryId)
                .toString(),
            token,
        )
        assertEquals(200, voided.status)
        assertEquals("FRUIT_VOIDED", voided.json.getString("messageKey"))
        val account = ledger(userId, token).json.getJSONObject("account")
        assertEquals(0L, account.getLong("availableFruitHundredths"))
        assertEquals(2, account.getJSONArray("entries").length())

        val again = post(
            "/api/parent/fruit/void",
            JSONObject()
                .put("userId", userId)
                .put("expectedRevision", account.getLong("revision"))
                .put("entryId", entryId)
                .toString(),
            token,
        )
        assertEquals(again.json.toString(), 409, again.status)
        assertEquals("FRUIT_ENTRY_ALREADY_VOIDED", again.json.getString("error"))
        assertFalse(again.json.getBoolean("ok"))
    }

    @Test
    fun turningTheUserConfigSwitchOffMakesTheWholeLedgerPinless() {
        assertTrue(UserConfigPinSettings.setRequired(false))
        val userId = activeUserId()

        val read = ledger(userId, token = "")
        assertEquals(200, read.status)
        assertEquals(0L, read.json.getJSONObject("account").getLong("availableFruitHundredths"))

        val credited = post(
            "/api/parent/fruit/credit",
            JSONObject()
                .put("userId", userId)
                .put("expectedRevision", 1L)
                .put("fruits", 1)
                .put("title", "免密收入")
                .toString(),
        )
        assertEquals(200, credited.status)
        assertEquals("FRUIT_CREDITED", credited.json.getString("messageKey"))

        val spent = post(
            "/api/parent/fruit/expense",
            JSONObject()
                .put("userId", userId)
                .put("expectedRevision", 2L)
                .put("fruits", 1)
                .put("title", "免密支出")
                .toString(),
        )
        assertEquals(200, spent.status)
        assertEquals("FRUIT_EXPENSE_RECORDED", spent.json.getString("messageKey"))
        assertEquals(
            0L,
            ledger(userId, token = "").json.getJSONObject("account")
                .getLong("availableFruitHundredths"),
        )
    }

    @Test
    fun manualExpenseDebitsTheLedgerAndCanBeVoidedWhileTvExchangesStayFinal() {
        val userId = activeUserId()
        val token = userEditToken()
        val credited = post(
            "/api/parent/fruit/credit",
            JSONObject()
                .put("userId", userId)
                .put("expectedRevision", 1L)
                .put("fruits", 5)
                .put("title", "做家务")
                .put("saveQuickProject", true)
                .put("quickProjectDefaultFruits", JSONObject.NULL)
                .toString(),
            token,
        )
        assertEquals(200, credited.status)

        val afterCredit = ledger(userId, token).json.getJSONObject("account")
        assertEquals(2L, afterCredit.getLong("revision"))
        assertEquals(500L, afterCredit.getLong("availableFruitHundredths"))
        val incomeProject = afterCredit.getJSONArray("quickProjects").getJSONObject(0)
        assertEquals("做家务", incomeProject.getString("name"))
        assertEquals("INCOME", incomeProject.getString("direction"))
        assertEquals(5, incomeProject.getInt("defaultFruits"))

        val spent = post(
            "/api/parent/fruit/expense",
            JSONObject()
                .put("userId", userId)
                .put("expectedRevision", 2L)
                .put("fruits", 2)
                .put("title", "买玩具")
                .put("saveQuickProject", true)
                .put("quickProjectDefaultFruits", 2)
                .toString(),
            token,
        )
        assertEquals(200, spent.status)
        assertEquals("FRUIT_EXPENSE_RECORDED", spent.json.getString("messageKey"))

        val afterSpend = ledger(userId, token).json.getJSONObject("account")
        assertEquals(3L, afterSpend.getLong("revision"))
        assertEquals(300L, afterSpend.getLong("availableFruitHundredths"))
        assertEquals(2, afterSpend.getJSONArray("quickProjects").length())
        val expenseProject = afterSpend.getJSONArray("quickProjects").getJSONObject(1)
        assertEquals("买玩具", expenseProject.getString("name"))
        assertEquals("EXPENSE", expenseProject.getString("direction"))
        assertEquals(2, expenseProject.getInt("defaultFruits"))
        assertEquals(2, afterSpend.getJSONArray("entries").length())
        val expenseEntry = afterSpend.getJSONArray("entries").getJSONObject(1)
        assertEquals("EXCHANGE", expenseEntry.getString("kind"))
        assertEquals("COMMITTED", expenseEntry.getString("status"))
        assertEquals(-200L, expenseEntry.getLong("fruitHundredths"))
        assertEquals(2, expenseEntry.getInt("minutes"))
        assertEquals(1, expenseEntry.getInt("minutesPerFruit"))
        assertTrue(expenseEntry.isNull("sessionId"))

        val voided = post(
            "/api/parent/fruit/expense/void",
            JSONObject()
                .put("userId", userId)
                .put("expectedRevision", 3L)
                .put("entryId", expenseEntry.getString("id"))
                .toString(),
            token,
        )
        assertEquals(200, voided.status)
        assertEquals("FRUIT_EXPENSE_VOIDED", voided.json.getString("messageKey"))
        val afterVoid = ledger(userId, token).json.getJSONObject("account")
        assertEquals(4L, afterVoid.getLong("revision"))
        assertEquals(500L, afterVoid.getLong("availableFruitHundredths"))
        assertEquals(
            "VOID",
            afterVoid.getJSONArray("entries").getJSONObject(1).getString("status"),
        )
    }

    @Test
    fun manualExpenseCannotSpendMoreThanTheWholeFruitBalance() {
        val userId = activeUserId()
        val token = userEditToken()
        assertEquals(
            200,
            post(
                "/api/parent/fruit/credit",
                JSONObject()
                    .put("userId", userId)
                    .put("expectedRevision", 1L)
                    .put("fruits", 1)
                    .put("title", "一颗果实")
                    .toString(),
                token,
            ).status,
        )
        val rejected = post(
            "/api/parent/fruit/expense",
            JSONObject()
                .put("userId", userId)
                .put("expectedRevision", 2L)
                .put("fruits", 2)
                .put("title", "买玩具")
                .toString(),
            token,
        )
        assertEquals(409, rejected.status)
        assertEquals("FRUIT_BALANCE_INSUFFICIENT", rejected.json.getString("error"))
        assertEquals(
            100L,
            ledger(userId, token).json.getJSONObject("account")
                .getLong("availableFruitHundredths"),
        )
    }
}