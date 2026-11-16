package app.orchard.tv.bot

import android.content.Context
import com.planyourtv.log.TvLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lifecycle owner of the ClawBot feature. Started from PolicyProduct after the
 * policy coordinator; it opens no connection until the parent has both turned
 * the feature on in the parent center and bound at least one WeChat account.
 *
 * Everything here is additive to Aerial: one call in PolicyProduct.install and
 * one parent-center page own this whole feature.
 */
internal object BotRuntime {
    private const val TAG = "BotRuntime"

    /** Boot-window store readiness: 24 tries x 5s = two minutes of patience. */
    private const val READINESS_ATTEMPTS = 24
    private const val READINESS_INTERVAL_MS = 5_000L

    /** Injectable for tests; the default is the project's HttpURLConnection shell. */
    var transport: HttpTransport = UrlConnectionTransport()

    /**
     * Application context for the few commands that need PackageManager or a
     * diagnostics report; set once at start, never holds an activity.
     */
    @Volatile
    var appContext: android.content.Context? = null
        private set

    val gate = BotCommandGate()
    val channelVersion: String by lazy { BotChannel.channelVersion() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channels = LinkedHashMap<String, Job>()
    private var eventJob: Job? = null
    private var reportJob: Job? = null
    private var readinessJob: Job? = null

    /** Pushes that arrived before the parent's first message, flushed on first contact. */
    private val pendingPushes = LinkedHashMap<String, MutableList<String>>()
    private const val MAX_PENDING_PER_BINDING = 10

    @Volatile
    private var started = false

    /** Idempotent; called from PolicyProduct and again from later entry points. */
    @Synchronized
    fun start(context: Context) {
        appContext = context.applicationContext
        started = true
        if (com.planyourtv.policy.TvStore.isReady) {
            restartChannels()
            return
        }
        // Boot can reach us before credential-protected storage unlocks, when
        // binding reads return empty and no channel would ever start. Wait for
        // the store (bounded), then bring the channels up ourselves instead of
        // hoping a later install() call arrives.
        readinessJob?.cancel()
        readinessJob = scope.launch(Dispatchers.IO) {
            repeat(READINESS_ATTEMPTS) {
                if (com.planyourtv.policy.TvStore.isReady) return@launch
                runCatching { com.planyourtv.policy.TvStore.init(appContext ?: return@launch) }
                if (com.planyourtv.policy.TvStore.isReady) return@launch
                kotlinx.coroutines.delay(READINESS_INTERVAL_MS)
            }
            if (com.planyourtv.policy.TvStore.isReady) {
                TvLog.i(TAG, "store became ready; starting bot channels")
                restartChannels()
            } else {
                TvLog.w(TAG, "store never became ready; bot channels not started")
            }
        }
    }

    /** Starts/stops loop jobs to match the persisted bindings; safe to call on any change. */
    @Synchronized
    fun restartChannels() {
        if (!started) return
        if (!BotBindingStore.enabled()) {
            stopChannels()
            return
        }
        if (eventJob == null) {
            // Pushes hop to IO per message, so the collectors never block on HTTP.
            eventJob = BotEventSource.start(
                scope,
                onTimer = { text -> scope.launch(Dispatchers.IO) { pushCategory(timer = true, quiz = false, text = text) } },
                onQuiz = { text -> scope.launch(Dispatchers.IO) { pushCategory(timer = false, quiz = true, text = text) } },
            )
        }
        if (reportJob == null) {
            reportJob = scope.launch(Dispatchers.IO) {
                // One check a minute is plenty: the push goes out once per local
                // day after BotDailyReport.AUTO_PUSH_HOUR, marked when sent.
                // Cancellation lands on delay(), which ends the loop.
                while (true) {
                    kotlinx.coroutines.delay(60_000L)
                    if (!BotBindingStore.enabled()) continue
                    if (BotDailyReport.autoPushDue()) {
                        val text = "📊 每日报告\n" + BotDailyReport.reportText()
                        pushCategory(timer = true, quiz = false, text = text)
                        BotDailyReport.markAutoPushed()
                    }
                }
            }
        }
        val wanted = BotBindingStore.list().associateBy { it.id }
        // Drop jobs whose binding vanished and duplicates from repeated calls.
        val iterator = channels.entries.iterator()
        while (iterator.hasNext()) {
            val (id, job) = iterator.next()
            if (!wanted.containsKey(id) || !job.isActive) {
                job.cancel()
                iterator.remove()
            }
        }
        for ((id, binding) in wanted) {
            if (!channels.containsKey(id)) {
                channels[id] = BotChannel(this, binding, scope).start()
            }
        }
    }

    /** Called by the parent-center QR flow after a binding is persisted. */
    fun onBindingAdded(actorName: String) {
        BotBindingStore.setEnabled(true)
        // Bindings are part of the audit trail like commands.
        BotAuditLog.append(
            BotAuditLog.Entry(
                atMs = System.currentTimeMillis(),
                actor = actorName,
                command = "绑定",
                accepted = true,
                detail = "电视扫码绑定",
            ),
        )
        restartChannels()
        broadcastAudit("🔗 新家长绑定：$actorName")
    }

    /**
     * Removes a binding and stops its loop. [notify] sends a farewell first;
     * the self-unbind path has already replied, so it passes false. The parent
     * center calls this from the UI thread, so the farewell HTTP hops to IO —
     * a synchronous send there is a NetworkOnMainThreadException swallowed by
     * sendText, and the goodbye never went out.
     */
    fun unbind(id: String, notify: Boolean) {
        val binding = BotBindingStore.find(id) ?: return
        if (notify && binding.contextToken.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                sendText(binding, binding.contextToken, "你已与本电视解绑。")
            }
        }
        synchronized(this) {
            channels.remove(id)?.cancel()
            pendingPushes.remove(id)
        }
        BotBindingStore.remove(id)
        TvLog.i(TAG, "binding removed: $id")
    }

    /** Audit lines go to every binding; they are the multi-parent transparency log. */
    fun broadcastAudit(text: String) {
        for (binding in BotBindingStore.list()) {
            sendOrQueue(binding, text)
        }
    }

    /**
     * Category pushes respect each binding's toggles. A binding matching
     * either requested category gets the text exactly once — callers may pass
     * both categories for one message without double-sending.
     */
    fun pushCategory(timer: Boolean, quiz: Boolean, text: String) {
        for (binding in BotBindingStore.list()) {
            val wanted = (timer && binding.pushTimer) || (quiz && binding.pushQuiz)
            if (wanted) sendOrQueue(binding, text)
        }
    }

    private fun sendOrQueue(binding: BotBinding, text: String) {
        if (binding.contextToken.isEmpty()) {
            // No reply context yet: keep the newest few, drop the oldest.
            val queue = pendingPushes.getOrPut(binding.id) { ArrayList() }
            if (queue.size >= MAX_PENDING_PER_BINDING) queue.removeAt(0)
            queue.add(text)
            return
        }
        sendText(binding, binding.contextToken, text)
    }

    internal fun flushPendingPushes(id: String, contextToken: String) {
        val queued = synchronized(this) { pendingPushes.remove(id) } ?: return
        val binding = BotBindingStore.find(id) ?: return
        for (text in queued) {
            sendText(binding, contextToken, text)
        }
    }

    /** Fire-and-forget send; a failure only costs this one message. */
    fun sendText(binding: BotBinding, contextToken: String, text: String) {
        try {
            val ok = WeixinClawApi.sendText(
                transport = transport,
                baseUrl = binding.baseUrl,
                botToken = binding.botToken,
                channelVersion = channelVersion,
                toUserId = binding.userId,
                contextToken = contextToken,
                text = text,
            )
            if (!ok) TvLog.w(TAG, "sendText failed for ${binding.id}")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            TvLog.w(TAG, "sendText error for ${binding.id}", error)
        }
    }

    private fun stopChannels() {
        for ((_, job) in channels) job.cancel()
        channels.clear()
        eventJob?.cancel()
        eventJob = null
        reportJob?.cancel()
        reportJob = null
        readinessJob?.cancel()
        readinessJob = null
    }

    /** Test/teardown hook; the TV process never calls this in production. */
    fun shutdown() {
        stopChannels()
        synchronized(this) { pendingPushes.clear() }
        scope.cancel()
        started = false
    }
}
