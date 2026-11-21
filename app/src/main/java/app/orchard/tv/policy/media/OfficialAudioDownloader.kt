package app.orchard.tv.policy.media

import android.content.Context
import android.util.Log
import app.orchard.tv.update.LegacyUpdateTls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * One official track at a time, only after the parent taps “download and use”.
 *
 * The flow mirrors [OfficialMediaDownloader]: download into a `.part`, verify the declared
 * byte count and SHA-256, decode the exact stored bytes with the same codec gate as a local
 * upload, then swap a complete staging directory into place. The catalogue itself is never
 * copied here, so deleting a download does not remove the cloud entry and an offline TV keeps
 * showing whatever is already on disk.
 */
object OfficialAudioDownloader {
    const val KEEP_FREE_BYTES = 64L * 1024 * 1024
    private const val TAG = "PolicyAudioDownloader"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 25_000
    private const val STALL_MS = 30_000L
    private const val STALL_BYTES = 64 * 1024L
    private const val PROGRESS_STEP = 128 * 1024L
    private const val DURATION_TOLERANCE_MS = 2_000L

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
            .put(
                "percent",
                if (totalBytes > 0) ((bytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0,
            )
            .put("error", error ?: JSONObject.NULL)
    }

    sealed class StartResult {
        data class Started(val progress: Progress) : StartResult()
        data class AlreadyReady(val local: OfficialAudioLocal) : StartResult()
        data class Running(val progress: Progress) : StartResult()
        data object Busy : StartResult()
        data object NotFound : StartResult()
        data object NoSpace : StartResult()
    }

    fun progress(id: String): Progress? = states[id]

    /** Every in-memory download state, keyed by official id. */
    fun snapshot(): JSONObject = JSONObject().apply {
        states.forEach { (id, progress) -> put(id, progress.toJson()) }
    }

    @Synchronized
    fun start(context: Context, id: String): StartResult {
        val app = context.applicationContext
        val track = OfficialAudioCatalog.find(app, id) ?: return StartResult.NotFound

        val installed = OfficialAudioStore.read(app, id)
        if (installed != null && installed.track.sha256 == track.sha256) {
            states.remove(id)
            return StartResult.AlreadyReady(installed)
        }

        states[id]?.takeIf { it.state == STATE_DOWNLOADING || it.state == STATE_VERIFYING }?.let {
            return StartResult.Running(it)
        }
        val active = activeId
        if (active != null && active != id) return StartResult.Busy
        if (!hasFreeSpace(app, track)) return StartResult.NoSpace

        OfficialAudioStore.cleanupStale(app)
        val progress = Progress(STATE_DOWNLOADING, 0L, track.bytes, null)
        states[id] = progress
        activeId = id
        cancelled.remove(id)
        scope.launch {
            try {
                download(app, track)
            } catch (failure: DownloadFailure) {
                if (!cancelled.remove(id)) {
                    states[id] = Progress(STATE_FAILED, 0L, track.bytes, failure.code)
                } else {
                    states.remove(id)
                }
            } catch (error: Exception) {
                if (cancelled.remove(id)) {
                    states.remove(id)
                } else {
                    Log.w(TAG, "download failed for $id", error)
                    states[id] = Progress(STATE_FAILED, 0L, track.bytes, "AUDIO_DOWNLOAD_FAILED")
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

    /** Drops a failure state after the parent deletes the local copy. */
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

    private suspend fun download(context: Context, track: OfficialAudioTrack) {
        val downloadDir = OfficialAudioStore.downloadDir(context)
        val suffix = track.id.removePrefix("official:")
        val mediaPart = File(downloadDir, "$suffix.mp3.part")
        val staging = File(downloadDir, "$suffix.stage")
        mediaPart.delete()
        staging.deleteRecursively()

        try {
            val digest = downloadObject(
                context = context,
                url = track.url,
                expectedBytes = track.bytes,
                target = mediaPart,
                id = track.id,
            )
            if (digest != track.sha256) throw DownloadFailure("AUDIO_CHECKSUM_MISMATCH")
            if (mediaPart.length() != track.bytes) {
                throw DownloadFailure("AUDIO_CHECKSUM_MISMATCH")
            }
            states[track.id] = Progress(STATE_VERIFYING, track.bytes, track.bytes, null)

            if (!PolicyAudioLibrary.looksLikeAudio(mediaPart)) {
                throw DownloadFailure("AUDIO_BAD_FORMAT")
            }
            val duration = when (val probe = PolicyAudioLibrary.probe(mediaPart)) {
                is PolicyAudioLibrary.ProbeResult.Rejected -> throw DownloadFailure(probe.code)
                is PolicyAudioLibrary.ProbeResult.Ok -> probe.durationMs
            }
            // The catalogue's duration is metadata, not a decoder input; a large mismatch means
            // the object is not the track the parent chose.
            if (abs(duration - track.durationMs) > DURATION_TOLERANCE_MS) {
                throw DownloadFailure("AUDIO_DOWNLOAD_FAILED")
            }

            check(staging.mkdirs()) { "could not create audio staging directory" }
            val staged = File(staging, "audio.bin")
            if (!mediaPart.renameTo(staged)) {
                mediaPart.copyTo(staged, overwrite = true)
                mediaPart.delete()
            }
            File(staging, "info.json").writeText(
                JSONObject()
                    .put("id", track.id)
                    .put("name", track.name)
                    .put("nameEn", track.nameEn)
                    .put("artist", track.artist ?: JSONObject.NULL)
                    .put("license", track.license)
                    .put("sourceUrl", track.sourceUrl ?: JSONObject.NULL)
                    .put("sha256", track.sha256)
                    .put("bytes", track.bytes)
                    .put("durationMs", duration)
                    .put("downloadedAt", System.currentTimeMillis())
                    .toString(),
            )
            install(staging, context, track.id)
            states.remove(track.id)
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
    ): String {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (error: Exception) {
            throw DownloadFailure("AUDIO_NETWORK")
        }
        try {
            LegacyUpdateTls.configure(context, connection)
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Accept", "audio/mpeg")
            activeConnection = connection
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw DownloadFailure("AUDIO_NETWORK")
            }
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
                ?: throw DownloadFailure("AUDIO_LENGTH_REQUIRED")
            if (declared != expectedBytes) throw DownloadFailure("AUDIO_CHECKSUM_MISMATCH")

            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var shown = 0L
            var windowAt = System.currentTimeMillis()
            var windowFrom = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled.contains(id)) throw DownloadFailure("AUDIO_CANCELLED")
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        done += read
                        if (done > expectedBytes) throw DownloadFailure("AUDIO_TOO_LARGE")
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        val now = System.currentTimeMillis()
                        if (now - windowAt >= STALL_MS) {
                            if (done - windowFrom < STALL_BYTES) {
                                throw DownloadFailure("AUDIO_NETWORK")
                            }
                            windowAt = now
                            windowFrom = done
                        }
                        if (done - shown >= PROGRESS_STEP) {
                            shown = done
                            states[id] = Progress(STATE_DOWNLOADING, done, expectedBytes, null)
                        }
                    }
                }
            }
            if (done != expectedBytes) throw DownloadFailure("AUDIO_CHECKSUM_MISMATCH")
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            activeConnection = null
            connection.disconnect()
        }
    }

    /** Swap the staged directory in without ever leaving the target half-written. */
    private fun install(staging: File, context: Context, id: String) {
        val target = OfficialAudioStore.dirFor(context, id)
            ?: throw DownloadFailure("AUDIO_NOT_FOUND")
        val downloadDir = OfficialAudioStore.downloadDir(context)
        val old = File(downloadDir, id.removePrefix("official:") + ".old")
        old.deleteRecursively()
        if (target.exists() && !target.renameTo(old)) {
            throw DownloadFailure("AUDIO_DOWNLOAD_FAILED")
        }
        if (!staging.renameTo(target)) {
            old.renameTo(target)
            throw DownloadFailure("AUDIO_DOWNLOAD_FAILED")
        }
        old.deleteRecursively()
    }

    private fun hasFreeSpace(context: Context, track: OfficialAudioTrack): Boolean =
        OfficialAudioStore.officialDir(context).usableSpace >= track.bytes + KEEP_FREE_BYTES

    private fun cleanTemporaryFiles(context: Context, id: String) {
        val suffix = id.removePrefix("official:")
        val downloadDir = OfficialAudioStore.downloadDir(context)
        File(downloadDir, "$suffix.mp3.part").delete()
        File(downloadDir, "$suffix.stage").deleteRecursively()
        File(downloadDir, "$suffix.old").deleteRecursively()
    }

    const val STATE_DOWNLOADING = "DOWNLOADING"
    const val STATE_VERIFYING = "VERIFYING"
    const val STATE_FAILED = "FAILED"

    private class DownloadFailure(val code: String) : IOException(code)
}