package app.orchard.tv.bot

/**
 * Drives one QR binding session for the parent-center page. The page renders
 * whatever this state machine says and calls [poll] every two seconds; all
 * protocol sequencing — QR fetch, the three allowed refreshes, the PIN-verify
 * intermediate step and the redirect host switch — lives here so the UI stays
 * dumb and the flow is testable against scripted responses.
 *
 * The session never persists anything: on [Event.Bound] the caller upserts the
 * credentials and notifies [BotRuntime].
 */
internal class BotBindingFlow(
    private val transport: HttpTransport,
    private val baseUrl: String = WeixinClawApi.DEFAULT_BASE_URL,
    private val channelVersion: String = WeixinClawApi.CHANNEL_VERSION_BASE,
) {
    /** Events the QR page renders; every poll returns exactly one. */
    sealed interface Event {
        /** A QR is on screen (fresh or refreshed). */
        data class Showing(val ticket: QrTicket) : Event
        data object Scanned : Event
        /** The backend wants the on-screen digits next round. */
        data class NeedCode(val digits: String) : Event
        data object Waiting : Event
        data class Bound(
            val botToken: String,
            val botId: String,
            val userId: String,
            val baseUrl: String,
        ) : Event

        /** All refreshes spent or backend refused; the page must restart or exit. */
        data object Stopped : Event
    }

    private var ticket: QrTicket? = null
    private var refreshes = 0
    private var pendingCode: String? = null
    private var effectiveBase: String = baseUrl
    private var stopped = false

    /** Starts a session and returns the first QR, or Stopped when offline. */
    fun begin(knownTokens: List<String>): Event {
        stopped = false
        refreshes = 0
        pendingCode = null
        effectiveBase = baseUrl
        ticket = WeixinClawApi.requestQr(transport, baseUrl, knownTokens)
        ticket?.let { logTicket("begin", it) }
        return ticket?.let(Event::Showing) ?: Event.Stopped
    }

    /**
     * One poll turn. Returns the newest state; internally handles expiry by
     * refreshing (up to the protocol's three refreshes) before giving up.
     */
    fun poll(): Event {
        if (stopped) return Event.Stopped
        val current = ticket ?: return Event.Stopped
        return when (val status = WeixinClawApi.pollQrStatus(transport, effectiveBase, current.qrcode, pendingCode)) {
            QrStatus.Wait -> Event.Waiting
            QrStatus.Scanned -> Event.Scanned
            is QrStatus.NeedVerifyCode -> {
                pendingCode = status.digits
                Event.NeedCode(status.digits)
            }
            is QrStatus.Confirmed -> {
                stopped = true
                Event.Bound(
                    botToken = status.botToken,
                    botId = status.botId,
                    userId = status.userId,
                    baseUrl = status.baseUrl,
                )
            }
            QrStatus.Expired, QrStatus.VerifyBlocked -> refresh()
            is QrStatus.Redirect -> {
                effectiveBase = status.baseUrl
                Event.Waiting
            }
            QrStatus.AlreadyBound -> {
                stopped = true
                Event.Stopped
            }
        }
    }

    private fun refresh(): Event {
        if (refreshes >= MAX_REFRESHES) {
            stopped = true
            return Event.Stopped
        }
        refreshes++
        pendingCode = null
        val next = WeixinClawApi.requestQr(transport, effectiveBase, emptyList())
        next?.let { logTicket("refresh", it) }
        return if (next == null) {
            stopped = true
            Event.Stopped
        } else {
            ticket = next
            Event.Showing(next)
        }
    }

    /** Field-level trace: with the live backend these two fields decide what renders. */
    private fun logTicket(where: String, ticket: QrTicket) {
        com.planyourtv.log.TvLog.i(
            "WeChatQR",
            "$where qrcode=${ticket.qrcode.take(48)} img=${ticket.imageContent.take(160)}",
        )
    }

    companion object {
        /** The protocol caps QR refreshes at three before the flow must stop. */
        const val MAX_REFRESHES = 3
    }
}
