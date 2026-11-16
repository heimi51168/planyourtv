package app.orchard.tv.policy

import android.content.Context
import app.orchard.tv.bot.BotRuntime
import app.orchard.tv.policy.media.PolicyVideoLibrary
import app.orchard.tv.policy.web.ParentWebCenter
import app.orchard.tv.quiz.QuizQuestionCache
import app.orchard.tv.quiz.QuizRecovery
import app.orchard.tv.quiz.QuizRuntime
import app.orchard.tv.quiz.QuizSeedBank
import app.orchard.tv.BuildConfig
import com.planyourtv.log.TvLog
import com.planyourtv.policy.quiz.QuizUnitCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The only entry point Aerial needs. It starts the LOCAL/CLOUD coordinator and exposes the
 * parent-center overlay switch. Aerial imports only this object and one Control Center row.
 *
 * The community build is local-first: without a backend baked in, the remote adapter and MQTT
 * are not installed at all and the parent center hides the remote-management row.
 */
object PolicyProduct {
    val cloudEnabled: Boolean = BuildConfig.CLOUD_ENABLED

    private val _parentCenterVisible = MutableStateFlow(false)
    val parentCenterVisible: StateFlow<Boolean> = _parentCenterVisible.asStateFlow()

    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        // Structured logging is a debug/dev/preview aid. The official release and the
        // community build keep it off so field formatting and logcat writes cost nothing.
        TvLog.setEnabled(
            BuildConfig.DEBUG ||
                (BuildConfig.TV_API_BASE.isNotBlank() && !BuildConfig.OFFICIAL_BUILD),
        )
        // Voice cues are local policy feedback and must work without a backend too.
        PolicyVoicePlayer.install(context.applicationContext)
        // Read the source-entry display switch before Home builds its first list.
        SourceEntrySettings.install(context.applicationContext)
        // The quiz engine reads only this cache. A failure here is logged by the
        // caller at first use (CACHE_UNAVAILABLE); it is never silently replaced.
        QuizQuestionCache.init(context.applicationContext)
        // Finish or import whatever the previous process left behind before
        // the lock screen reads the quiz entry.
        QuizRecovery.reconcile()
        if (cloudEnabled) PolicyCloud.install(context)
        PolicyAppInventory.start(context)
        PolicyModeCoordinator.start(
            context,
            debugPin = if (BuildConfig.DEBUG) "123456" else null,
        )
        // Chat control is opt-in: BotRuntime opens no connection until the
        // parent enabled it in the parent center and bound a WeChat account.
        BotRuntime.start(context.applicationContext)
        ParentWebCenter.install(context.applicationContext)
        // Ship a usable local bank so the quiz works before an AI key or the
        // network exists. After the coordinator because the durable marker
        // lives in TvStore, which it initializes. The bundled set is now large
        // enough that parsing it on the main thread would delay Home, so import
        // once in the background; the quiz entry refreshes when it finishes.
        val appContext = context.applicationContext
        Thread({
            // Drop one-time legacy role values and stale media downloads. Role reads also
            // migrate on demand, so this thread never races the lock screen.
            runCatching { PolicyVideoLibrary.install(appContext) }
                .onFailure { error -> TvLog.w("PolicyProduct", "video library setup failed", error) }
            // The unit catalogue is display/prompt data only, but its JSON is
            // large enough that parsing it on the first scope card would be
            // visible. Warm it on this disposable thread.
            QuizUnitCatalog.current()
            runCatching { QuizSeedBank.install(appContext) }
                .onSuccess { added -> if (added > 0) QuizRuntime.refresh() }
                .onFailure { error -> TvLog.w("PolicyProduct", "quiz seed import failed", error) }
        }, "quiz-seed-import").apply {
            isDaemon = true
            start()
        }
    }

    fun openParentSettings() {
        // The V29 TV panel is read-only (switch, remote management, QR, recovery code), so it
        // neither takes the web editor lease nor blocks a phone from configuring the TV.
        _parentCenterVisible.value = true
    }

    fun closeParentSettings() {
        _parentCenterVisible.value = false
    }

    suspend fun syncOnStartup() {
        if (cloudEnabled && PolicyModeCoordinator.isCloud()) {
            withContext(Dispatchers.IO) { PolicyCloud.syncOnStartup() }
            PolicyAppInventory.requestSync("startup")
        }
    }
}
