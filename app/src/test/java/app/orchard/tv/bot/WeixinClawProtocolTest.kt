package app.orchard.tv.bot

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Protocol fixtures follow the published spec document. The transport is a
 * recorder, so request shapes (headers, cursor, body fields) are asserted
 * without any network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WeixinClawProtocolTest {

    /** Captures calls and answers with a per-URL script. */
    private class RecordingTransport : HttpTransport {
        data class Call(
            val method: String,
            val url: String,
            val headers: Map<String, String>,
            val body: String?,
        )

        val calls = ArrayList<Call>()
        var script: (Call) -> JSONObject? = { null }

        override fun call(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String?,
            readTimeoutMs: Int,
        ): JSONObject? {
            val record = Call(method, url, headers, body)
            calls.add(record)
            return script(record)
        }
    }

    private lateinit var transport: RecordingTransport

    @Before
    fun setUp() {
        transport = RecordingTransport()
    }

    // --- QR ---------------------------------------------------------------------

    @Test
    fun qrTicketDecodesWithBothFields() {
        val ticket = WeixinClawCodec.decodeQrTicket(
            JSONObject().put("qrcode", "QR1").put("qrcode_img_content", "https://img"),
        )
        assertEquals("QR1", ticket?.qrcode)
        assertEquals("https://img", ticket?.imageContent)
    }

    @Test
    fun qrTicketWithoutContentIsRejected() {
        assertNull(WeixinClawCodec.decodeQrTicket(JSONObject().put("qrcode", "QR1")))
    }

    @Test
    fun qrRequestCarriesLocalTokensAndBotType() {
        transport.script = { JSONObject().put("qrcode", "q").put("qrcode_img_content", "i") }
        WeixinClawApi.requestQr(transport, knownTokens = listOf("t1", "t2"))
        val call = transport.calls.single()
        assertTrue(call.url.endsWith("/ilink/bot/get_bot_qrcode?bot_type=3"))
        val body = JSONObject(call.body!!)
        assertEquals(listOf("t1", "t2"), body.getJSONArray("local_token_list").let { list ->
            List(list.length()) { list.getString(it) }
        })
    }

    // --- QR status ----------------------------------------------------------------

    @Test
    fun confirmedStatusCarriesCredentials() {
        val json = JSONObject()
            .put("status", "confirmed")
            .put("bot_token", "BT")
            .put("ilink_bot_id", "BID")
            .put("ilink_user_id", "UID")
            .put("baseurl", "https://api2.example")
        val status = WeixinClawCodec.decodeQrStatus(json, "https://fallback")
        val confirmed = status as QrStatus.Confirmed
        assertEquals("BT", confirmed.botToken)
        assertEquals("UID", confirmed.userId)
        assertEquals("https://api2.example", confirmed.baseUrl)
    }

    @Test
    fun confirmedWithoutBaseurlFallsBack() {
        val json = JSONObject()
            .put("status", "confirmed")
            .put("bot_token", "BT")
            .put("ilink_user_id", "UID")
        val confirmed = WeixinClawCodec.decodeQrStatus(json, "https://fallback") as QrStatus.Confirmed
        assertEquals("https://fallback", confirmed.baseUrl)
    }

    @Test
    fun confirmedWithoutTokenStaysWaiting() {
        val status = WeixinClawCodec.decodeQrStatus(
            JSONObject().put("status", "confirmed").put("ilink_user_id", "UID"),
            "https://fallback",
        )
        assertTrue(status is QrStatus.Wait)
    }

    @Test
    fun verifyCodeStatusesSurfaceTheDigits() {
        val json = JSONObject().put("status", "need_verifycode").put("verify_code", "4321")
        val status = WeixinClawCodec.decodeQrStatus(json, "https://x")
        assertEquals("4321", (status as QrStatus.NeedVerifyCode).digits)
    }

    @Test
    fun redirectSwitchesThePollHost() {
        val status = WeixinClawCodec.decodeQrStatus(
            JSONObject().put("status", "scaned_but_redirect").put("redirect_host", "alt.example"),
            "https://x",
        )
        assertEquals("https://alt.example", (status as QrStatus.Redirect).baseUrl)
    }

    @Test
    fun unknownStatusIsWait() {
        val status = WeixinClawCodec.decodeQrStatus(JSONObject().put("status", "something_new"), "x")
        assertTrue(status is QrStatus.Wait)
    }

    // --- updates ---------------------------------------------------------------------

    private fun userTextMessage(id: String, text: String, from: String = "U1"): JSONObject =
        JSONObject()
            .put("message_id", id)
            .put("from_user_id", from)
            .put("to_user_id", "BOT")
            .put("message_type", 1)
            .put("context_token", "CTX")
            .put("create_time_ms", 777L)
            .put(
                "item_list",
                JSONArray().put(
                    JSONObject().put("type", 1).put("text_item", JSONObject().put("text", text)),
                ),
            )

    @Test
    fun updatesPageDecodesCursorAndUserText() {
        val json = JSONObject()
            .put("ret", 0)
            .put("get_updates_buf", "CURSOR2")
            .put("msgs", JSONArray().put(userTextMessage("m1", "状态")))
        val page = WeixinClawCodec.decodeUpdates(json) as UpdatesResult.Ok
        assertEquals("CURSOR2", page.cursor)
        assertEquals(1, page.messages.size)
        assertEquals("状态", page.messages[0].text)
        assertEquals("CTX", page.messages[0].contextToken)
    }

    @Test
    fun successEnvelopeWithoutRetFieldIsStillSuccess() {
        // Live-verified against the production backend: success carries msgs and
        // the cursor with no "ret" at all.
        val json = JSONObject()
            .put("get_updates_buf", "C1")
            .put("msgs", JSONArray().put(userTextMessage("m1", "帮助")))
        val page = WeixinClawCodec.decodeUpdates(json) as UpdatesResult.Ok
        assertEquals("C1", page.cursor)
        assertEquals(1, page.messages.size)
    }

    @Test
    fun botEchoesAndNonTextItemsAreIgnored() {
        val botMessage = userTextMessage("m2", "hi").put("message_type", 2)
        val noText = JSONObject()
            .put("message_id", "m3")
            .put("from_user_id", "U1")
            .put("message_type", 1)
            .put("item_list", JSONArray().put(JSONObject().put("type", 2)))
        val json = JSONObject()
            .put("ret", 0)
            .put("get_updates_buf", "C")
            .put("msgs", JSONArray().put(botMessage).put(noText))
        val page = WeixinClawCodec.decodeUpdates(json) as UpdatesResult.Ok
        assertTrue(page.messages.isEmpty())
    }

    @Test
    fun errcodeMinusFourteenPausesOneHour() {
        val result = WeixinClawCodec.decodeUpdates(JSONObject().put("ret", -1).put("errcode", -14))
        assertTrue(result is UpdatesResult.PauseOneHour)
    }

    @Test
    fun otherErrorsAreRejected() {
        val result = WeixinClawCodec.decodeUpdates(JSONObject().put("ret", -1).put("errcode", 5))
        assertTrue(result is UpdatesResult.Rejected)
    }

    @Test
    fun getUpdatesSendsCursorAndChannelVersion() {
        transport.script = { JSONObject().put("ret", 0).put("get_updates_buf", "NEXT") }
        WeixinClawApi.getUpdates(transport, "https://api", "TOKEN", "3.0.1/planyourtv-1", "CURSOR")
        val call = transport.calls.single()
        val body = JSONObject(call.body!!)
        assertEquals("CURSOR", body.getString("get_updates_buf"))
        assertEquals(
            "3.0.1/planyourtv-1",
            body.getJSONObject("base_info").getString("channel_version"),
        )
        assertEquals("Bearer TOKEN", call.headers["Authorization"])
        assertEquals("ilink_bot_token", call.headers["AuthorizationType"])
        assertEquals("bot", call.headers["iLink-App-Id"])
    }

    // --- send ------------------------------------------------------------------------

    @Test
    fun sendTextBuildsTheDocumentedMessageShape() {
        transport.script = { JSONObject().put("ret", 0) }
        val ok = WeixinClawApi.sendText(
            transport, "https://api", "TOKEN", "3.0.1/planyourtv-1",
            toUserId = "U1", contextToken = "CTX", text = "剩余30分钟",
        )
        assertTrue(ok)
        val body = JSONObject(transport.calls.single().body!!)
        val msg = body.getJSONObject("msg")
        assertEquals("U1", msg.getString("to_user_id"))
        assertEquals(2, msg.getInt("message_type"))
        assertEquals("CTX", msg.getString("context_token"))
        assertEquals(
            "剩余30分钟",
            msg.getJSONArray("item_list").getJSONObject(0)
                .getJSONObject("text_item").getString("text"),
        )
    }

    @Test
    fun sendFailureReturnsFalse() {
        transport.script = { JSONObject().put("ret", -1).put("errmsg", "bad context") }
        val ok = WeixinClawApi.sendText(
            transport, "https://api", "TOKEN", "v", "U1", "CTX", "text",
        )
        assertEquals(false, ok)
    }

    // --- binding store codec -----------------------------------------------------------

    @Test
    fun bindingRoundTripsThroughJson() {
        val binding = BotBinding(
            id = "b1",
            botToken = "BT",
            botId = "BID",
            userId = "UID",
            baseUrl = "https://api",
            nickname = "妈妈",
            boundAtMs = 1L,
            lastSeenMs = 2L,
            cursor = "C",
            contextToken = "CTX",
            pushTimer = false,
        )
        val decoded = BotBindingStore.decode(BotBindingStore.encode(binding))
        assertEquals(binding, decoded)
    }

    @Test
    fun bindingWithoutTokenIsDropped() {
        val json = BotBindingStore.encode(
            BotBinding("b1", "BT", "B", "U", "https://api", "n", 1, 1),
        ).put("bot_token", "")
        assertNull(BotBindingStore.decode(json))
    }

    // --- binding flow --------------------------------------------------------------------

    private fun ticketJson(n: Int) = JSONObject()
        .put("qrcode", "QR$n")
        .put("qrcode_img_content", "https://img/$n")

    @Test
    fun flowWalksWaitScannedConfirmed() {
        transport.script = { call ->
            when {
                call.url.contains("get_bot_qrcode") -> ticketJson(1)
                call.url.contains("qrcode_status") && call.url.contains("QR1") ->
                    JSONObject().put("status", "wait")
                else -> ticketJson(2)
            }
        }
        val flow = BotBindingFlow(transport)
        assertTrue(flow.begin(emptyList()) is BotBindingFlow.Event.Showing)
        assertTrue(flow.poll() is BotBindingFlow.Event.Waiting)
        transport.script = { JSONObject().put("status", "scaned") }
        assertTrue(flow.poll() is BotBindingFlow.Event.Scanned)
        transport.script = {
            JSONObject()
                .put("status", "confirmed")
                .put("bot_token", "BT")
                .put("ilink_bot_id", "BID")
                .put("ilink_user_id", "UID")
                .put("baseurl", "https://api2")
        }
        val bound = flow.poll() as BotBindingFlow.Event.Bound
        assertEquals("BT", bound.botToken)
        assertEquals("UID", bound.userId)
        // A finished flow is over for good.
        assertTrue(flow.poll() is BotBindingFlow.Event.Stopped)
    }

    @Test
    fun expiredQrRefreshesUpToThreeTimesThenStops() {
        var qrCount = 0
        transport.script = { call ->
            when {
                call.url.contains("get_bot_qrcode") -> ticketJson(++qrCount)
                else -> JSONObject().put("status", "expired")
            }
        }
        val flow = BotBindingFlow(transport)
        assertTrue(flow.begin(emptyList()) is BotBindingFlow.Event.Showing)
        assertTrue(flow.poll() is BotBindingFlow.Event.Showing)
        assertTrue(flow.poll() is BotBindingFlow.Event.Showing)
        assertTrue(flow.poll() is BotBindingFlow.Event.Showing)
        assertTrue(flow.poll() is BotBindingFlow.Event.Stopped)
        // begin() resets the refresh budget for a brand-new attempt.
        transport.script = { call ->
            when {
                call.url.contains("get_bot_qrcode") -> ticketJson(++qrCount)
                else -> JSONObject().put("status", "wait")
            }
        }
        assertTrue(flow.begin(emptyList()) is BotBindingFlow.Event.Showing)
        assertTrue(flow.poll() is BotBindingFlow.Event.Waiting)
    }

    @Test
    fun needVerifyCodeFeedsTheDigitsIntoTheNextPoll() {
        transport.script = { call ->
            when {
                call.url.contains("get_bot_qrcode") -> ticketJson(1)
                call.url.contains("verify_code") ->
                    JSONObject()
                        .put("status", "confirmed")
                        .put("bot_token", "BT")
                        .put("ilink_user_id", "UID")
                call.url.contains("qrcode_status") ->
                    JSONObject().put("status", "need_verifycode").put("verify_code", "4321")
                else -> null
            }
        }
        val flow = BotBindingFlow(transport)
        flow.begin(emptyList())
        assertTrue(flow.poll() is BotBindingFlow.Event.NeedCode)
        val bound = flow.poll() as BotBindingFlow.Event.Bound
        assertEquals("BT", bound.botToken)
        // The confirming poll really carried the digits.
        val confirmCall = transport.calls.last { it.url.contains("verify_code") }
        assertTrue(confirmCall.url.contains("verify_code=4321"))
    }
}
