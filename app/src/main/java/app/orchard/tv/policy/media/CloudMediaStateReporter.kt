package app.orchard.tv.policy.media

import android.content.Context
import app.orchard.tv.policy.PolicyCloud
import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.log.TvLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * V56: the TV's only truthful source of media-center download state.
 *
 * The remote page never guesses: it renders what this reporter last sent. Every report is a full
 * replacement snapshot carrying a process-scoped [bootId], a strictly increasing
 * `snapshotVersion` and the generation time, so the backend can drop a delayed older snapshot
 * instead of moving the page backwards. Nothing here writes configuration or starts downloads.
 *
 * The reporter only runs after a schema-5 `configured-apps` payload has proved that the backend
 * supports media center. It reports every 2s while any download is active and every 4 minutes
 * when idle (the backend marks a snapshot stale after 10 minutes).
 */
object CloudMediaStateReporter {
    private const val TAG = "MediaStateReport"
    private const val REPORT_SCHEMA = 1
    private const val ACTIVE_INTERVAL_MS = 2_000L
    private const val IDLE_INTERVAL_MS = 4 * 60_000L
    private const val RETRY_INTERVAL_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val lock = Mutex()
    private val bootId: String = UUID.randomUUID().toString()
    private val snapshotVersion = AtomicLong(0L)

    @Volatile private var enabled = false
    @Volatile private var loop: Job? = null
    @Volatile private var appContext: Context? = null

    fun enable(context: Context) {
        appContext = context.applicationContext
        if (enabled) return
        enabled = true
        loop = scope.launch {
            while (isActive) {
                val ctx = appContext
                val ok = if (enabled && ctx != null && PolicyModeCoordinator.isCloud()) {
                    reportNow(ctx)
                } else {
                    true
                }
                val busy = CustomMediaDownloader.hasActiveDownloads() ||
                    OfficialMediaDownloader.hasActiveDownloads() ||
                    OfficialAudioDownloader.hasActiveDownloads()
                val wait = when {
                    !ok -> RETRY_INTERVAL_MS
                    busy -> ACTIVE_INTERVAL_MS
                    else -> IDLE_INTERVAL_MS
                }
                withTimeoutOrNull(wait) { wake.receive() }
            }
        }
        TvLog.i(TAG, "media state reporter enabled")
    }

    fun disable() {
        if (!enabled) return
        enabled = false
        loop?.cancel()
        loop = null
        TvLog.i(TAG, "media state reporter disabled")
    }

    fun isEnabled(): Boolean = enabled

    /** Coalesced immediate report; safe before the reporter is enabled (the call is ignored). */
    fun requestSoon() {
        if (enabled) wake.trySend(Unit)
    }

    /** MQTT `reportState`: one extra snapshot now instead of waiting for the next tick. */
    suspend fun reportNow(context: Context): Boolean {
        if (!enabled) return true
        return lock.withLock {
            reportOnce(context.applicationContext)
        }
    }

    /** Returns true when the backend accepted the snapshot (or there is nothing to do). */
    private suspend fun reportOnce(context: Context): Boolean {
        if (!PolicyModeCoordinator.isCloud()) return true
        val snapshot = runCatching { buildSnapshot(context) }.getOrElse { error ->
            TvLog.w(TAG, "could not build media state snapshot", error)
            return false
        }
        return PolicyCloud.reportWallpaperMediaState(snapshot).fold(
            onSuccess = { true },
            onFailure = { error ->
                // Never log the snapshot body; it carries file SHAs but no signed URLs.
                TvLog.w(TAG, "media state report failed", error)
                false
            },
        )
    }

    private fun buildSnapshot(context: Context): JSONObject {
        val mediaCatalog = OfficialMediaCatalog.cached(context)
        val downloadedMedia = OfficialMediaStore.list(context).associateBy { it.id }
        val media = JSONArray()
        mediaCatalog?.items?.forEach { item ->
            val local = downloadedMedia[item.id]
            val progress = OfficialMediaDownloader.progress(item.id)
            val state = mediaState(item, local, progress)
            media.put(
                JSONObject()
                    .put("officialId", item.id)
                    .put("kind", item.kind.wire.uppercase())
                    .put("state", state)
                    .put("percent", percentOf(progress, state))
                    .put("roles", JSONArray(item.roles.map { it.wire }))
                    .put(
                        "capabilityRoles",
                        JSONArray(
                            capabilityRoles(item, local).map { it.wire },
                        ),
                    )
                    .put("hasAudio", local?.item?.hasAudio ?: item.hasAudio)
                    .put("sha256", local?.item?.sha256 ?: item.sha256)
                    .put("error", errorOf(progress, state) ?: JSONObject.NULL),
            )
        }

        val audioCatalog = OfficialAudioCatalog.cached(context)
        val downloadedAudio = OfficialAudioStore.list(context).associateBy { it.id }
        val audio = JSONArray()
        audioCatalog?.tracks?.forEach { track ->
            val local = downloadedAudio[track.id]
            val progress = OfficialAudioDownloader.progress(track.id)
            val state = audioState(track, local, progress)
            audio.put(
                JSONObject()
                    .put("officialId", track.id)
                    .put("state", state)
                    .put("percent", percentOf(progress, state))
                    .put("error", errorOf(progress, state) ?: JSONObject.NULL),
            )
        }

        val remotes = CustomMediaLibraryClient.latestAll().associateBy { it.kind to it.id }
        val locals = CustomMediaStore.list(context).associateBy { it.kind to it.id }
        val customMedia = JSONArray()
        val customAudio = JSONArray()
        val keys = LinkedHashSet<Pair<CustomMediaKind, Long>>()
        keys += remotes.keys
        keys += locals.keys
        keys.forEach { (kind, id) ->
            val remote = remotes[kind to id]
            val local = locals[kind to id]
            val progress = CustomMediaDownloader.progress(kind, id)
            val state = customState(remote, local, progress)
            val item = JSONObject()
                .put("id", id)
                .put("kind", kind.wire)
                .put("state", state)
                .put("percent", percentOf(progress, state))
                .put("sha256", local?.sha256 ?: JSONObject.NULL)
                .put("error", errorOf(progress, state) ?: JSONObject.NULL)
            if (kind.isAudio) customAudio.put(item) else customMedia.put(item)
        }

        val packs = JSONObject()
        mediaCatalog?.pack?.takeIf { it.isNotBlank() }?.let { packs.put("media", it) }
        audioCatalog?.pack?.takeIf { it.isNotBlank() }?.let { packs.put("audio", it) }

        return JSONObject()
            .put("schema", REPORT_SCHEMA)
            .put("bootId", bootId)
            .put("snapshotVersion", snapshotVersion.incrementAndGet())
            .put("sentAtMs", System.currentTimeMillis())
            .put("full", true)
            .put("catalogPacks", packs)
            .put("media", media)
            .put("audio", audio)
            .put("customMedia", customMedia)
            .put("customAudio", customAudio)
    }

    private fun mediaState(
        item: OfficialMediaItem,
        local: OfficialMediaLocal?,
        progress: OfficialMediaDownloader.Progress?,
    ): String = when {
        progress?.state == OfficialMediaDownloader.STATE_DOWNLOADING -> STATE_DOWNLOADING
        progress?.state == OfficialMediaDownloader.STATE_VERIFYING -> STATE_VERIFYING
        progress?.state == OfficialMediaDownloader.STATE_FAILED -> STATE_FAILED
        local == null -> STATE_NOT_DOWNLOADED
        local.item.sha256 == item.sha256 -> STATE_READY
        else -> STATE_UPDATE_AVAILABLE
    }

    private fun audioState(
        track: OfficialAudioTrack,
        local: OfficialAudioLocal?,
        progress: OfficialAudioDownloader.Progress?,
    ): String = when {
        progress?.state == OfficialAudioDownloader.STATE_DOWNLOADING -> STATE_DOWNLOADING
        progress?.state == OfficialAudioDownloader.STATE_VERIFYING -> STATE_VERIFYING
        progress?.state == OfficialAudioDownloader.STATE_FAILED -> STATE_FAILED
        local == null -> STATE_NOT_DOWNLOADED
        local.track.sha256 == track.sha256 -> STATE_READY
        else -> STATE_UPDATE_AVAILABLE
    }

    private fun customState(
        remote: CustomMediaRemote?,
        local: CustomMediaLocal?,
        progress: CustomMediaDownloader.Progress?,
    ): String = when {
        progress?.state == CustomMediaDownloader.STATE_DOWNLOADING -> STATE_DOWNLOADING
        progress?.state == CustomMediaDownloader.STATE_VERIFYING -> STATE_VERIFYING
        progress?.state == CustomMediaDownloader.STATE_FAILED -> STATE_FAILED
        local == null -> STATE_NOT_DOWNLOADED
        remote != null && local.sha256 != remote.sha256 -> STATE_UPDATE_AVAILABLE
        else -> STATE_READY
    }

    private fun percentOf(
        progress: OfficialMediaDownloader.Progress?,
        state: String,
    ): Int = when {
        progress?.state == OfficialMediaDownloader.STATE_DOWNLOADING ->
            progress.percent().coerceIn(0, 100)
        state == STATE_DOWNLOADING || state == STATE_VERIFYING -> 100
        state == STATE_READY || state == STATE_UPDATE_AVAILABLE -> 100
        else -> 0
    }

    private fun percentOf(
        progress: OfficialAudioDownloader.Progress?,
        state: String,
    ): Int = when {
        progress?.state == OfficialAudioDownloader.STATE_DOWNLOADING ->
            progress.percent().coerceIn(0, 100)
        state == STATE_DOWNLOADING || state == STATE_VERIFYING -> 100
        state == STATE_READY || state == STATE_UPDATE_AVAILABLE -> 100
        else -> 0
    }

    private fun percentOf(
        progress: CustomMediaDownloader.Progress?,
        state: String,
    ): Int = when {
        progress?.state == CustomMediaDownloader.STATE_DOWNLOADING -> progress.percent()
        state == STATE_DOWNLOADING || state == STATE_VERIFYING -> 100
        state == STATE_READY || state == STATE_UPDATE_AVAILABLE -> 100
        else -> 0
    }

    private fun errorOf(
        progress: OfficialMediaDownloader.Progress?,
        state: String,
    ): String? = if (state == STATE_FAILED) progress?.error else null

    private fun errorOf(
        progress: OfficialAudioDownloader.Progress?,
        state: String,
    ): String? = if (state == STATE_FAILED) progress?.error else null

    private fun errorOf(
        progress: CustomMediaDownloader.Progress?,
        state: String,
    ): String? = if (state == STATE_FAILED) progress?.error else null

    /** A downloaded official image can also serve as the quiz intro; catalogue images cannot. */
    private fun capabilityRoles(
        item: OfficialMediaItem,
        local: OfficialMediaLocal?,
    ): Set<PolicyVideoRole> =
        if (local?.item?.kind == PolicyMediaKind.IMAGE) {
            PolicyVideoRole.entries.toSet()
        } else {
            item.roles
        }

    const val STATE_NOT_DOWNLOADED = "NOT_DOWNLOADED"
    const val STATE_DOWNLOADING = "DOWNLOADING"
    const val STATE_VERIFYING = "VERIFYING"
    const val STATE_FAILED = "FAILED"
    const val STATE_UPDATE_AVAILABLE = "UPDATE_AVAILABLE"
    const val STATE_READY = "READY"
}