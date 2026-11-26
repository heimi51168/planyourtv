package app.orchard.tv.policy.media

import android.content.Context
import app.orchard.tv.policy.PolicyCloud
import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.log.TvLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * V56: keeps [CustomMediaStore] aligned with the backend's device-level upload library.
 *
 * The remote page uploads to the cloud; the TV pulls the signed library and downloads every
 * missing or changed entry in the background. This is the only trigger for the automatic
 * “upload then the TV downloads it” flow. It is enabled only after a schema-5 `configured-apps`
 * payload has proven that the backend supports media center; an old backend is never polled.
 *
 * A failed or cancelled download is not retried by the periodic tick for the same version.
 * Only an explicit command (refreshCustomLibrary / a retry action), a new version or a process
 * restart clears that block, so a broken upload cannot loop forever consuming traffic.
 */
object CustomMediaLibraryClient {
    private const val TAG = "CustomMediaLibrary"
    private const val LIBRARY_SCHEMA = 1
    private const val TICK_MS = 60_000L
    private const val MAX_NAME_LENGTH = 120
    private val shaPattern = Regex("[a-f0-9]{64}")
    private val idPattern = Regex("[1-9][0-9]{0,18}")
    private val extPattern = Regex("[a-z0-9]{1,8}")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    @Volatile private var enabled = false
    @Volatile private var loop: Job? = null
    @Volatile private var latestByKey: Map<String, CustomMediaRemote> = emptyMap()

    fun enable(context: Context) {
        val app = context.applicationContext
        if (enabled) return
        enabled = true
        loop = scope.launch {
            while (isActive) {
                if (PolicyModeCoordinator.isCloud()) {
                    runCatching {
                        refreshNow(app, downloadMissing = true, forceRetry = false)
                    }.onFailure { TvLog.w(TAG, "periodic library refresh failed", it) }
                }
                delay(TICK_MS)
            }
        }
        TvLog.i(TAG, "custom upload library enabled")
    }

    fun disable() {
        if (!enabled) return
        enabled = false
        loop?.cancel()
        loop = null
        TvLog.i(TAG, "custom upload library disabled")
    }

    fun isEnabled(): Boolean = enabled

    fun latest(kind: CustomMediaKind, id: Long): CustomMediaRemote? =
        latestByKey[keyOf(kind, id)]

    fun latestAll(): List<CustomMediaRemote> = latestByKey.values.toList()

    /**
     * Fetches the signed library and reconciles the device cache. A malformed response keeps the
     * previous library and cache untouched: a partial parse must never look like “the parent
     * deleted everything” and trigger a mass GC.
     */
    suspend fun refreshNow(
        context: Context,
        downloadMissing: Boolean,
        forceRetry: Boolean,
    ) {
        val app = context.applicationContext
        lock.withLock {
            val payload = PolicyCloud.fetchWallpaperCustomLibrary().getOrElse { error ->
                TvLog.w(TAG, "custom library fetch failed", error)
                return
            }
            val parsed = parseLibrary(payload) ?: return
            latestByKey = parsed.associateBy { keyOf(it.kind, it.id) }

            if (downloadMissing) {
                parsed.forEach { remote -> reconcileOne(app, remote, forceRetry) }
                collectGarbage(app, parsed)
            }
            CloudMediaStateReporter.requestSoon()
            TvLog.i(TAG, "custom library refreshed items=${parsed.size}")
        }
    }

    private fun reconcileOne(context: Context, remote: CustomMediaRemote, forceRetry: Boolean) {
        val installed = CustomMediaStore.read(context, remote.kind, remote.id)
        if (installed != null && installed.sha256 == remote.sha256 &&
            installed.version == remote.version
        ) {
            CustomMediaDownloader.unblock(remote.kind, remote.id)
            return
        }
        if (CustomMediaDownloader.isRunning(remote.kind, remote.id)) return
        if (forceRetry) {
            CustomMediaDownloader.unblock(remote.kind, remote.id)
        } else if (CustomMediaDownloader.isBlocked(remote.kind, remote.id, remote.version)) {
            TvLog.i(TAG, "skip blocked upload id=${remote.id} version=${remote.version}")
            return
        }
        when (val result = CustomMediaDownloader.start(context, remote)) {
            is CustomMediaDownloader.StartResult.Started,
            is CustomMediaDownloader.StartResult.Running,
            is CustomMediaDownloader.StartResult.AlreadyReady,
            -> TvLog.i(
                TAG,
                "download upload id=${remote.id} kind=${remote.kind.wire} " +
                    "version=${remote.version}",
            )
            CustomMediaDownloader.StartResult.NoSpace ->
                TvLog.w(TAG, "no space for upload id=${remote.id} bytes=${remote.sizeBytes}")
            CustomMediaDownloader.StartResult.Invalid ->
                TvLog.w(TAG, "invalid upload entry id=${remote.id}")
        }
    }

    private fun collectGarbage(context: Context, remote: List<CustomMediaRemote>) {
        val remoteKeys = remote.map { keyOf(it.kind, it.id) }.toSet()
        CustomMediaStore.list(context).forEach { local ->
            if (keyOf(local.kind, local.id) in remoteKeys) return@forEach
            if (CustomMediaDownloader.isRunning(local.kind, local.id)) return@forEach
            if (CustomMediaStore.isReferencedByHosted(context, local.id)) {
                TvLog.i(TAG, "keep upload id=${local.id}; still referenced by hosted media")
                return@forEach
            }
            if (CustomMediaStore.delete(context, local.kind, local.id)) {
                CustomMediaDownloader.forget(context, local.kind, local.id)
                TvLog.i(TAG, "removed cached upload id=${local.id} kind=${local.kind.wire}")
            } else {
                TvLog.w(TAG, "could not remove cached upload id=${local.id}")
            }
        }
    }

    /**
     * Strict parse: any malformed entry rejects the whole response. The backend only returns its
     * own uploads, but the TV never trusts that when the payload decides what may be deleted.
     */
    private fun parseLibrary(payload: JSONObject): List<CustomMediaRemote>? {
        if (payload.optInt("schema", 0) != LIBRARY_SCHEMA) {
            TvLog.w(TAG, "unsupported custom library schema=${payload.optInt("schema", 0)}")
            return null
        }
        val media = payload.optJSONArray("media") ?: run {
            TvLog.w(TAG, "custom library missing media")
            return null
        }
        val audio = payload.optJSONArray("audio") ?: run {
            TvLog.w(TAG, "custom library missing audio")
            return null
        }
        val out = ArrayList<CustomMediaRemote>(media.length() + audio.length())
        if (!parseEntries(media, expectAudio = false, out)) return null
        if (!parseEntries(audio, expectAudio = true, out)) return null
        return out
    }

    private fun parseEntries(
        array: JSONArray,
        expectAudio: Boolean,
        out: MutableList<CustomMediaRemote>,
    ): Boolean {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return false
            val kind = CustomMediaKind.fromWire(item.optString("kind")) ?: return false
            if (kind.isAudio != expectAudio) return false
            val remote = parseEntry(item, kind) ?: return false
            out += remote
        }
        return true
    }

    private fun parseEntry(item: JSONObject, kind: CustomMediaKind): CustomMediaRemote? {
        val idRaw = item.optString("id", "").trim()
        if (!idPattern.matches(idRaw)) return null
        val id = idRaw.toLongOrNull() ?: return null
        val name = item.optString("name", "").trim().take(MAX_NAME_LENGTH)
            .ifBlank { "custom:$id" }
        val ext = item.optString("ext", "").trim().lowercase()
        if (!extPattern.matches(ext)) return null
        val version = item.optLong("version", -1L)
        if (version < 0L) return null
        val url = item.optString("url", "").trim()
        if (!url.startsWith("https://")) return null
        val sha = item.optString("sha256", "").trim().lowercase()
        if (!shaPattern.matches(sha)) return null
        val size = item.optLong("sizeBytes", 0L)
        if (size <= 0L || size > CustomMediaStore.maxBytes(kind)) return null
        val duration = item.optLong("durationMs", -1L)
        if (duration < 0L) return null
        if (kind == CustomMediaKind.VIDEO && duration <= 0L) return null
        if (kind == CustomMediaKind.AUDIO && duration <= 0L) return null
        val hasAudio = if (item.has("hasAudio") && !item.isNull("hasAudio")) {
            item.optBoolean("hasAudio")
        } else {
            null
        }
        return CustomMediaRemote(
            id = id,
            kind = kind,
            name = name,
            ext = ext,
            version = version,
            url = url,
            sha256 = sha,
            sizeBytes = size,
            durationMs = duration,
            hasAudio = hasAudio,
        )
    }

    private fun keyOf(kind: CustomMediaKind, id: Long): String =
        if (kind.isAudio) "audio:$id" else "media:$id"
}