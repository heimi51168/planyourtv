package app.orchard.tv.policy.media

import android.content.Context
import android.util.Log
import app.orchard.tv.update.LegacyUpdateTls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Downloads the parent's remote uploads into [CustomMediaStore].
 *
 * Two downloads may run at once; the rest queue and keep reporting DOWNLOADING 0% so the remote
 * page never shows “READY” before the bytes exist. Every file is verified against the backend's
 * SHA-256, byte count and the same decode contract as a local upload, then swapped in atomically.
 * A cancelled or failed download blocks the same version from being retried by the 60s library
 * tick; only an explicit command (or a new version) starts it again.
 */
object CustomMediaDownloader {
    const val KEEP_FREE_BYTES = 64L * 1024 * 1024
    private const val TAG = "CustomMediaDownloader"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 25_000
    private const val STALL_MS = 30_000L
    private const val STALL_BYTES = 64 * 1024L
    private const val PROGRESS_STEP = 256 * 1024L
    private const val DURATION_TOLERANCE_MS = 2_000L
    private const val MAX_CONCURRENT = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(MAX_CONCURRENT)
    private val states = ConcurrentHashMap<String, Progress>()
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val connections = ConcurrentHashMap<String, HttpURLConnection>()
    private val activeVersions = ConcurrentHashMap<String, Long>()
    private val blockedVersions = ConcurrentHashMap<String, Long>()

    data class Progress(
        val state: String,
        val bytes: Long,
        val totalBytes: Long,
        val error: String?,
    ) {
        fun percent(): Int =
            if (totalBytes > 0) ((bytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

        fun toJson(): JSONObject = JSONObject()
            .put("state", state)
            .put("bytes", bytes)
            .put("totalBytes", totalBytes)
            .put("percent", percent())
            .put("error", error ?: JSONObject.NULL)
    }

    sealed class StartResult {
        data class Started(val progress: Progress) : StartResult()
        data class AlreadyReady(val local: CustomMediaLocal) : StartResult()
        data class Running(val progress: Progress) : StartResult()
        data object Invalid : StartResult()
        data object NoSpace : StartResult()
    }

    fun key(kind: CustomMediaKind, id: Long): String =
        if (kind.isAudio) "audio:$id" else "media:$id"

    fun progress(kind: CustomMediaKind, id: Long): Progress? = states[key(kind, id)]

    fun isRunning(kind: CustomMediaKind, id: Long): Boolean = running.contains(key(kind, id))

    fun hasActiveDownloads(): Boolean = states.values.any {
        it.state == STATE_DOWNLOADING || it.state == STATE_VERIFYING
    }

    fun isBlocked(kind: CustomMediaKind, id: Long, version: Long): Boolean =
        blockedVersions[key(kind, id)] == version

    /** Clears a recorded failure so the next tick (or an explicit retry) starts again. */
    fun unblock(kind: CustomMediaKind, id: Long) {
        blockedVersions.remove(key(kind, id))
    }

    /**
     * The 60s library tick and an MQTT `download` command can call this at the same time; the
     * whole check-then-launch sequence is synchronized so the two never share a staging path.
     */
    @Synchronized
    fun start(context: Context, remote: CustomMediaRemote): StartResult {
        val app = context.applicationContext
        if (!validRemote(remote)) return StartResult.Invalid
        val cacheKey = key(remote.kind, remote.id)

        val installed = CustomMediaStore.read(app, remote.kind, remote.id)
        if (installed != null && installed.sha256 == remote.sha256 &&
            installed.version == remote.version
        ) {
            states.remove(cacheKey)
            blockedVersions.remove(cacheKey)
            return StartResult.AlreadyReady(installed)
        }

        states[cacheKey]?.takeIf {
            it.state == STATE_DOWNLOADING || it.state == STATE_VERIFYING
        }?.let { return StartResult.Running(it) }
        if (running.contains(cacheKey)) {
            return StartResult.Running(
                states[cacheKey] ?: Progress(STATE_DOWNLOADING, 0L, remote.sizeBytes, null),
            )
        }
        if (!hasFreeSpace(app, remote.sizeBytes)) return StartResult.NoSpace

        val progress = Progress(STATE_DOWNLOADING, 0L, remote.sizeBytes, null)
        states[cacheKey] = progress
        running.add(cacheKey)
        activeVersions[cacheKey] = remote.version
        cancelled.remove(cacheKey)
        blockedVersions.remove(cacheKey)
        CustomMediaStore.cleanupStale(app)
        CloudMediaStateReporter.requestSoon()
        scope.launch {
            permits.withPermit {
                try {
                    if (cancelled.contains(cacheKey)) {
                        fail(cacheKey, remote, "CANCELLED")
                    } else {
                        download(app, remote, cacheKey)
                    }
                } catch (failure: DownloadFailure) {
                    if (cancelled.remove(cacheKey)) {
                        fail(cacheKey, remote, "CANCELLED")
                    } else {
                        Log.w(TAG, "upload download failed id=${remote.id} code=${failure.code}")
                        fail(cacheKey, remote, failure.code)
                    }
                } catch (error: Exception) {
                    if (cancelled.remove(cacheKey)) {
                        fail(cacheKey, remote, "CANCELLED")
                    } else {
                        Log.w(TAG, "upload download failed id=${remote.id}", error)
                        fail(cacheKey, remote, "MEDIA_DOWNLOAD_FAILED")
                    }
                } finally {
                    connections.remove(cacheKey)?.disconnect()
                    running.remove(cacheKey)
                    cleanTemporaryFiles(app, remote)
                    activeVersions.remove(cacheKey)
                    CloudMediaStateReporter.requestSoon()
                }
            }
        }
        return StartResult.Started(progress)
    }

    /**
     * Stops one queued or running download. The state stays FAILED/CANCELLED until the parent
     * retries, so the library tick does not immediately start the same bytes again.
     */
    @Synchronized
    fun cancel(context: Context, kind: CustomMediaKind, id: Long): Boolean {
        val cacheKey = key(kind, id)
        if (!running.contains(cacheKey)) return false
        cancelled.add(cacheKey)
        connections.remove(cacheKey)?.disconnect()
        val known = states[cacheKey]
        val version = activeVersions[cacheKey]
            ?: blockedVersions[cacheKey]
            ?: versionOf(context, kind, id)
            ?: 0L
        states[cacheKey] = Progress(
            STATE_FAILED,
            known?.bytes ?: 0L,
            known?.totalBytes ?: 0L,
            "CANCELLED",
        )
        blockedVersions[cacheKey] = version
        CloudMediaStateReporter.requestSoon()
        return true
    }

    fun forget(context: Context, kind: CustomMediaKind, id: Long) {
        val cacheKey = key(kind, id)
        if (!running.contains(cacheKey)) {
            states.remove(cacheKey)
            blockedVersions.remove(cacheKey)
        }
    }

    private suspend fun download(context: Context, remote: CustomMediaRemote, cacheKey: String) {
        if (!hasFreeSpace(context, remote.sizeBytes)) throw DownloadFailure("NO_SPACE")
        val downloadDir = CustomMediaStore.downloadDir(context)
        val prefix = "custom-${remote.kind.wire.lowercase()}-${remote.id}"
        val part = File(downloadDir, "$prefix.part")
        val staging = File(downloadDir, "$prefix.stage")
        part.delete()
        staging.deleteRecursively()

        try {
            val digest = downloadObject(
                context = context,
                remote = remote,
                target = part,
                cacheKey = cacheKey,
            )
            if (digest != remote.sha256) throw DownloadFailure("MEDIA_CHECKSUM_MISMATCH")
            if (part.length() != remote.sizeBytes) {
                throw DownloadFailure("MEDIA_CHECKSUM_MISMATCH")
            }
            states[cacheKey] = Progress(
                STATE_VERIFYING,
                remote.sizeBytes,
                remote.sizeBytes,
                null,
            )

            check(staging.mkdirs()) { "could not create staging directory" }
            val info = when (remote.kind) {
                CustomMediaKind.VIDEO -> verifyVideo(part, remote)
                CustomMediaKind.IMAGE -> verifyImage(part, remote)
                CustomMediaKind.AUDIO -> verifyAudio(part, remote)
            }
            val staged = File(staging, "data.bin")
            if (!part.renameTo(staged)) {
                part.copyTo(staged, overwrite = true)
                part.delete()
            }
            File(staging, "info.json").writeText(
                JSONObject()
                    .put("id", remote.id)
                    .put("kind", remote.kind.wire)
                    .put("name", remote.name)
                    .put("ext", remote.ext)
                    .put("sha256", remote.sha256)
                    .put("bytes", remote.sizeBytes)
                    .put("durationMs", info.durationMs)
                    .put("width", info.width)
                    .put("height", info.height)
                    .put("fps", info.fps)
                    .put("hasAudio", info.hasAudio ?: JSONObject.NULL)
                    .put("version", remote.version)
                    .put("downloadedAt", System.currentTimeMillis())
                    .toString(),
            )
            install(staging, context, remote.kind, remote.id)
            states.remove(cacheKey)
            blockedVersions.remove(cacheKey)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    private data class Verified(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val fps: Int,
        val hasAudio: Boolean?,
    )

    private fun verifyVideo(file: File, remote: CustomMediaRemote): Verified {
        val info = when (val checked = PolicyVideoValidation.validateLocalUpload(file, null)) {
            is PolicyVideoValidation.Result.Rejected -> {
                throw DownloadFailure("MEDIA_BAD_FORMAT")
            }
            is PolicyVideoValidation.Result.Ok -> checked.info
        }
        // A custom wallpaper may run as long as its byte cap allows; only the backend's probed
        // duration is compared, never the retired 15-second quiz cap.
        if (remote.durationMs > 0L && abs(info.durationMs - remote.durationMs) >
            DURATION_TOLERANCE_MS
        ) {
            throw DownloadFailure("MEDIA_BAD_FORMAT")
        }
        if (remote.hasAudio != null && remote.hasAudio != info.hasAudio) {
            throw DownloadFailure("MEDIA_BAD_FORMAT")
        }
        return Verified(info.durationMs, info.width, info.height, info.fps, info.hasAudio)
    }

    private fun verifyImage(file: File, remote: CustomMediaRemote): Verified {
        val checked = when (val result = PolicyImageValidation.inspectStored(file)) {
            is PolicyImageValidation.Result.Rejected -> throw DownloadFailure("MEDIA_BAD_FORMAT")
            is PolicyImageValidation.Result.Ok -> result
        }
        if (checked.width != PolicyImageValidation.WIDTH ||
            checked.height != PolicyImageValidation.HEIGHT ||
            checked.bytes != remote.sizeBytes
        ) {
            throw DownloadFailure("MEDIA_BAD_FORMAT")
        }
        return Verified(
            durationMs = 0L,
            width = checked.width,
            height = checked.height,
            fps = 0,
            hasAudio = false,
        )
    }

    private fun verifyAudio(file: File, remote: CustomMediaRemote): Verified {
        if (!PolicyAudioLibrary.looksLikeAudio(file)) {
            throw DownloadFailure("AUDIO_BAD_FORMAT")
        }
        val duration = when (val probe = PolicyAudioLibrary.probe(file)) {
            is PolicyAudioLibrary.ProbeResult.Rejected -> throw DownloadFailure(probe.code)
            is PolicyAudioLibrary.ProbeResult.Ok -> probe.durationMs
        }
        if (remote.durationMs > 0L && abs(duration - remote.durationMs) > DURATION_TOLERANCE_MS) {
            throw DownloadFailure("AUDIO_BAD_FORMAT")
        }
        return Verified(duration, 0, 0, 0, null)
    }

    /** Downloads one object with progress, stall detection and a hard byte ceiling. */
    private fun downloadObject(
        context: Context,
        remote: CustomMediaRemote,
        target: File,
        cacheKey: String,
    ): String {
        val connection = try {
            val opened = URL(remote.url).openConnection() as HttpURLConnection
            LegacyUpdateTls.configure(context, opened)
            opened.instanceFollowRedirects = false
            opened.connectTimeout = CONNECT_TIMEOUT_MS
            opened.readTimeout = READ_TIMEOUT_MS
            opened.setRequestProperty("Accept-Encoding", "identity")
            opened.setRequestProperty(
                "Accept",
                when (remote.kind) {
                    CustomMediaKind.VIDEO -> "video/mp4"
                    CustomMediaKind.IMAGE -> "image/jpeg"
                    CustomMediaKind.AUDIO -> "audio/*"
                },
            )
            opened
        } catch (error: Exception) {
            throw DownloadFailure("NETWORK")
        }
        connections[cacheKey] = connection
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw DownloadFailure("NETWORK")
            }
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
                ?: throw DownloadFailure("LENGTH_REQUIRED")
            if (declared != remote.sizeBytes) throw DownloadFailure("MEDIA_CHECKSUM_MISMATCH")

            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var shown = 0L
            var windowAt = System.currentTimeMillis()
            var windowFrom = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled.contains(cacheKey)) throw DownloadFailure("CANCELLED")
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        done += read
                        if (done > remote.sizeBytes) throw DownloadFailure("MEDIA_TOO_LARGE")
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        val now = System.currentTimeMillis()
                        if (now - windowAt >= STALL_MS) {
                            if (done - windowFrom < STALL_BYTES) throw DownloadFailure("NETWORK")
                            windowAt = now
                            windowFrom = done
                        }
                        if (done - shown >= PROGRESS_STEP) {
                            shown = done
                            states[cacheKey] = Progress(
                                STATE_DOWNLOADING,
                                done,
                                remote.sizeBytes,
                                null,
                            )
                        }
                    }
                }
            }
            if (done != remote.sizeBytes) throw DownloadFailure("MEDIA_CHECKSUM_MISMATCH")
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            connections.remove(cacheKey)
            connection.disconnect()
        }
    }

    /** Swap the staged directory in without ever leaving the target half-written. */
    private fun install(
        staging: File,
        context: Context,
        kind: CustomMediaKind,
        id: Long,
    ) {
        val target = CustomMediaStore.dirFor(context, kind, id)
            ?: throw DownloadFailure("MEDIA_NOT_FOUND")
        val downloadDir = CustomMediaStore.downloadDir(context)
        val old = File(downloadDir, "custom-${kind.wire.lowercase()}-$id.old")
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

    private fun fail(cacheKey: String, remote: CustomMediaRemote, code: String) {
        states[cacheKey] = Progress(STATE_FAILED, 0L, remote.sizeBytes, code)
        // The 60s tick must not hammer the same bytes; a new version or an explicit retry
        // clears this. Log the code only, never the signed URL.
        blockedVersions[cacheKey] = remote.version
    }

    private fun versionOf(context: Context, kind: CustomMediaKind, id: Long): Long? =
        CustomMediaStore.read(context, kind, id)?.version

    private fun validRemote(remote: CustomMediaRemote): Boolean {
        if (remote.id <= 0L) return false
        if (remote.version < 0L) return false
        if (remote.sizeBytes <= 0L || remote.sizeBytes > CustomMediaStore.maxBytes(remote.kind)) {
            return false
        }
        if (!remote.sha256.matches(Regex("[a-f0-9]{64}"))) return false
        if (!remote.url.startsWith("https://")) return false
        if (!remote.ext.matches(Regex("[a-zA-Z0-9]{1,8}"))) return false
        if (remote.durationMs < 0L) return false
        return true
    }

    private fun hasFreeSpace(context: Context, bytes: Long): Boolean {
        val customRoot = CustomMediaStore.root(context)
        val used = directoryBytes(HostedMediaStore.root(context)) + directoryBytes(customRoot)
        if (used + bytes > HostedMediaStore.MAX_TOTAL_BYTES) return false
        return customRoot.usableSpace >= bytes + KEEP_FREE_BYTES + HostedMediaStore.RESERVED_BYTES
    }

    private fun directoryBytes(dir: File): Long =
        if (!dir.isDirectory) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun cleanTemporaryFiles(context: Context, remote: CustomMediaRemote) {
        val prefix = "custom-${remote.kind.wire.lowercase()}-${remote.id}"
        val downloadDir = CustomMediaStore.downloadDir(context)
        File(downloadDir, "$prefix.part").delete()
        File(downloadDir, "$prefix.stage").deleteRecursively()
    }

    const val STATE_DOWNLOADING = "DOWNLOADING"
    const val STATE_VERIFYING = "VERIFYING"
    const val STATE_FAILED = "FAILED"

    private class DownloadFailure(val code: String) : IOException(code)
}