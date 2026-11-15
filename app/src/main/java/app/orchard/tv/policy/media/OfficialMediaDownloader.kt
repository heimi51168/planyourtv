package app.orchard.tv.policy.media

import android.content.Context
import android.util.Log
import app.orchard.tv.update.LegacyUpdateTls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * One film at a time, only after the parent taps “download and use”.
 *
 * A download writes `.part` files under `policy-videos/download`, verifies the
 * byte count, SHA-256 and the same decode contract as a local upload, then
 * swaps a complete staging directory into place. The old READY file is never
 * deleted before the new one is verified, and no player ever sees a temporary
 * path. A process death loses the task (the next start removes stale parts),
 * and the page simply shows “not downloaded” and offers a retry.
 */
object OfficialMediaDownloader {
    const val MAX_OFFICIAL_ITEMS = 8
    // The library is capped by item count, not by a byte budget: when it is full the parent must
    // delete a local copy before downloading another. Real storage pressure is handled separately
    // by [KEEP_FREE_BYTES], so a short film never fills the TV on its own.
    const val KEEP_FREE_BYTES = 64L * 1024 * 1024
    private const val TAG = "PolicyMediaDownloader"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 25_000
    private const val STALL_MS = 30_000L
    private const val STALL_BYTES = 64 * 1024L
    private const val PROGRESS_STEP = 256 * 1024L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = ConcurrentHashMap<String, Progress>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var activeId: String? = null

    @Volatile private var activeConnection: HttpURLConnection? = null

    data class Progress(
        val state: String,
        val bytes: Long,
        val totalBytes: Long,
        val error: String?,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("state", state)
            .put("bytes", bytes)
            .put("totalBytes", totalBytes)
            .put("percent", if (totalBytes > 0) ((bytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0)
            .put("error", error ?: JSONObject.NULL)
    }

    sealed class StartResult {
        data class Started(val progress: Progress) : StartResult()
        data class AlreadyReady(val local: OfficialMediaLocal) : StartResult()
        data class Running(val progress: Progress) : StartResult()
        data object Busy : StartResult()
        data object NotFound : StartResult()
        data object RoleInvalid : StartResult()
        data object NoSpace : StartResult()
        data object LibraryFull : StartResult()
    }

    fun progress(id: String): Progress? = states[id]

    /** Every in-memory download state, keyed by official id. */
    fun snapshot(): JSONObject = JSONObject().apply {
        states.forEach { (id, progress) -> put(id, progress.toJson()) }
    }

    @Synchronized
    fun start(context: Context, id: String, roles: Set<PolicyVideoRole>): StartResult {
        val app = context.applicationContext
        val item = OfficialMediaCatalog.find(app, id) ?: return StartResult.NotFound
        if (!item.roles.containsAll(roles)) return StartResult.RoleInvalid

        val installed = OfficialMediaStore.read(app, id)
        if (installed != null && installed.item.sha256 == item.sha256) {
            assignRoles(app, id, roles)
            states.remove(id)
            return StartResult.AlreadyReady(installed)
        }

        states[id]?.takeIf { it.state == STATE_DOWNLOADING || it.state == STATE_VERIFYING }?.let {
            return StartResult.Running(it)
        }
        val active = activeId
        if (active != null && active != id) return StartResult.Busy

        if (!hasRoom(app, item)) return StartResult.LibraryFull
        if (!hasFreeSpace(app, item)) return StartResult.NoSpace

        val progress = Progress(STATE_DOWNLOADING, 0L, item.bytes, null)
        states[id] = progress
        activeId = id
        cancelled.remove(id)
        scope.launch {
            try {
                download(app, item, roles)
            } catch (failure: DownloadFailure) {
                if (!cancelled.remove(id)) {
                    states[id] = Progress(STATE_FAILED, 0L, item.bytes, failure.code)
                } else {
                    states.remove(id)
                }
            } catch (error: Exception) {
                if (cancelled.remove(id)) {
                    states.remove(id)
                } else {
                    Log.w(TAG, "download failed for $id", error)
                    states[id] = Progress(STATE_FAILED, 0L, item.bytes, "MEDIA_DOWNLOAD_FAILED")
                }
            } finally {
                activeConnection?.disconnect()
                activeConnection = null
                activeId = null
                cleanTemporaryFiles(app, id)
            }
        }
        return StartResult.Started(progress)
    }

    /** Drops a failure/empty state after the parent deletes the local copy. */
    fun forget(id: String) {
        if (activeId != id) states.remove(id)
    }

    /** Stops the current download; returns false when [id] is not the active one. */
    fun cancel(id: String): Boolean {
        if (activeId != id) return false
        cancelled.add(id)
        activeConnection?.disconnect()
        return true
    }

    private suspend fun download(
        context: Context,
        item: OfficialMediaItem,
        roles: Set<PolicyVideoRole>,
    ) {
        val downloadDir = OfficialMediaStore.downloadDir(context)
        val suffix = item.id.removePrefix("official:")
        val videoPart = File(downloadDir, "$suffix.mp4.part")
        val posterPart = File(downloadDir, "$suffix.jpg.part")
        val staging = File(downloadDir, "$suffix.stage")
        videoPart.delete()
        posterPart.delete()
        staging.deleteRecursively()

        try {
            val digest = downloadObject(context, item.url, item.bytes, videoPart, item.id, expectVideo = true)
            if (digest != item.sha256) throw DownloadFailure("MEDIA_CHECKSUM_MISMATCH")
            if (videoPart.length() != item.bytes) throw DownloadFailure("MEDIA_CHECKSUM_MISMATCH")
            states[item.id] = Progress(STATE_VERIFYING, item.bytes, item.bytes, null)

            val info = when (val checked = PolicyVideoValidation.validate(videoPart)) {
                is PolicyVideoValidation.Result.Rejected ->
                    throw DownloadFailure("MEDIA_BAD_FORMAT")
                is PolicyVideoValidation.Result.Ok -> checked.info
            }
            if (!matchesManifest(info, item)) throw DownloadFailure("MEDIA_BAD_FORMAT")

            val posterDigest = downloadObject(
                context,
                item.poster.url,
                item.poster.bytes,
                posterPart,
                item.id,
                expectVideo = false,
            )
            if (posterDigest != item.poster.sha256 ||
                posterPart.length() != item.poster.bytes ||
                !PolicyVideoValidation.isJpeg(posterPart)
            ) {
                throw DownloadFailure("MEDIA_CHECKSUM_MISMATCH")
            }

            check(staging.mkdirs()) { "could not create staging directory" }
            val stagedVideo = File(staging, "video.mp4")
            val stagedPoster = File(staging, "poster.jpg")
            if (!videoPart.renameTo(stagedVideo)) {
                videoPart.copyTo(stagedVideo, overwrite = true)
                videoPart.delete()
            }
            if (!posterPart.renameTo(stagedPoster)) {
                posterPart.copyTo(stagedPoster, overwrite = true)
                posterPart.delete()
            }
            File(staging, "info.json").writeText(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("nameEn", item.nameEn)
                    .put("roles", JSONArray(item.roles.map { it.wire }))
                    .put("sha256", item.sha256)
                    .put("bytes", item.bytes)
                    .put("durationMs", info.durationMs)
                    .put("width", info.width)
                    .put("height", info.height)
                    .put("fps", info.fps)
                    .put("hasAudio", info.hasAudio)
                    .put("posterSha256", item.poster.sha256)
                    .put("posterAtMs", item.poster.atMs)
                    .put("downloadedAt", System.currentTimeMillis())
                    .toString(),
            )
            install(staging, context, item.id)

            assignRoles(context, item.id, roles)
            states.remove(item.id)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    /** Downloads one object with progress, stall detection and a hard byte ceiling. */
    private fun downloadObject(
        context: Context,
        url: String,
        expectedBytes: Long,
        target: File,
        id: String,
        expectVideo: Boolean,
    ): String {
        val connection = try {
            val opened = URL(url).openConnection() as HttpURLConnection
            LegacyUpdateTls.configure(context, opened)
            opened.instanceFollowRedirects = false
            opened.connectTimeout = CONNECT_TIMEOUT_MS
            opened.readTimeout = READ_TIMEOUT_MS
            opened.setRequestProperty("Accept-Encoding", "identity")
            opened.setRequestProperty("Accept", if (expectVideo) "video/mp4" else "image/jpeg")
            opened
        } catch (error: Exception) {
            throw DownloadFailure("NETWORK")
        }
        activeConnection = connection
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw DownloadFailure("NETWORK")
            }
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
                ?: throw DownloadFailure("LENGTH_REQUIRED")
            if (declared != expectedBytes) throw DownloadFailure("MEDIA_CHECKSUM_MISMATCH")

            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var shown = 0L
            var windowAt = System.currentTimeMillis()
            var windowFrom = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled.contains(id)) throw DownloadFailure("CANCELLED")
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        done += read
                        if (done > expectedBytes) throw DownloadFailure("MEDIA_TOO_LARGE")
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        val now = System.currentTimeMillis()
                        if (now - windowAt >= STALL_MS) {
                            if (done - windowFrom < STALL_BYTES) throw DownloadFailure("NETWORK")
                            windowAt = now
                            windowFrom = done
                        }
                        if (expectVideo && done - shown >= PROGRESS_STEP) {
                            shown = done
                            states[id] = Progress(STATE_DOWNLOADING, done, expectedBytes, null)
                        }
                    }
                }
            }
            if (done != expectedBytes) throw DownloadFailure("MEDIA_CHECKSUM_MISMATCH")
            if (expectVideo) {
                states[id] = Progress(STATE_VERIFYING, expectedBytes, expectedBytes, null)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            activeConnection = null
            connection.disconnect()
        }
    }

    private fun matchesManifest(actual: PolicyVideoValidation.Info, item: OfficialMediaItem): Boolean {
        if (actual.width != item.width || actual.height != item.height) return false
        if (actual.hasAudio != item.hasAudio) return false
        val frameMs = if (item.fps > 0) 1000L / item.fps else 40L
        val tolerance = maxOf(150L, frameMs + 50L)
        if (abs(actual.durationMs - item.durationMs) > tolerance) return false
        if (item.fps > 0 && actual.fps > 0 && abs(actual.fps - item.fps) > 1) return false
        return true
    }

    /** Swap the staged directory in without ever leaving the target half-written. */
    private fun install(staging: File, context: Context, id: String) {
        val target = OfficialMediaStore.dirFor(context, id)
            ?: throw DownloadFailure("MEDIA_NOT_FOUND")
        val downloadDir = OfficialMediaStore.downloadDir(context)
        val old = File(downloadDir, id.removePrefix("official:") + ".old")
        old.deleteRecursively()
        if (target.exists() && !target.renameTo(old)) {
            throw DownloadFailure("MEDIA_DOWNLOAD_FAILED")
        }
        if (!staging.renameTo(target)) {
            old.renameTo(target)
            throw DownloadFailure("MEDIA_DOWNLOAD_FAILED")
        }
        old.deleteRecursively()
    }

    private fun hasRoom(context: Context, item: OfficialMediaItem): Boolean {
        val existing = OfficialMediaStore.list(context)
        // Re-downloading a film the library already holds replaces it and does not need a free slot.
        if (existing.any { it.id == item.id }) return true
        return existing.size < MAX_OFFICIAL_ITEMS
    }

    private fun hasFreeSpace(context: Context, item: OfficialMediaItem): Boolean {
        val need = item.bytes + item.poster.bytes + KEEP_FREE_BYTES
        return OfficialMediaStore.officialDir(context).usableSpace >= need
    }

    private fun assignRoles(context: Context, id: String, roles: Set<PolicyVideoRole>) {
        if (roles.isEmpty()) return
        val prefs = context.getSharedPreferences(QUIZ_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        roles.forEach { editor.putString(it.prefKey, id) }
        editor.apply()
    }

    private fun cleanTemporaryFiles(context: Context, id: String) {
        val suffix = id.removePrefix("official:")
        val downloadDir = OfficialMediaStore.downloadDir(context)
        File(downloadDir, "$suffix.mp4.part").delete()
        File(downloadDir, "$suffix.jpg.part").delete()
        File(downloadDir, "$suffix.stage").deleteRecursively()
    }

    const val STATE_DOWNLOADING = "DOWNLOADING"
    const val STATE_VERIFYING = "VERIFYING"
    const val STATE_FAILED = "FAILED"
    private const val QUIZ_PREFS = "quiz-intro"

    private class DownloadFailure(val code: String) : IOException(code)
}
