package app.orchard.tv.policy.media

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

data class OfficialAudioLocal(
    val id: String,
    val track: OfficialAudioTrack,
    val file: File,
    val downloadedAtMs: Long,
)

/**
 * Downloads under `filesDir/policy-audio/official`. A directory becomes visible only after
 * [OfficialAudioDownloader] has verified the bytes and written a complete `info.json`; the
 * local-upload directories next to it keep their 32-hex names and are never touched here.
 */
object OfficialAudioStore {
    private const val TAG = "PolicyAudioStore"
    private const val STALE_MS = 24 * 60 * 60 * 1000L
    private val idPattern = Regex("official:[a-z0-9-]{1,48}")
    private val suffixPattern = Regex("[a-z0-9-]{1,48}")
    private val shaPattern = Regex("[a-f0-9]{64}")
    private const val MAX_ATTRIBUTION_LENGTH = 80

    fun root(context: Context): File =
        File(context.filesDir, "policy-audio").apply { mkdirs() }

    fun officialDir(context: Context): File =
        File(root(context), "official").apply { mkdirs() }

    fun downloadDir(context: Context): File =
        File(root(context), "download").apply { mkdirs() }

    fun dirFor(context: Context, id: String): File? {
        if (!idPattern.matches(id)) return null
        return File(officialDir(context), id.removePrefix("official:"))
    }

    fun read(context: Context, id: String): OfficialAudioLocal? {
        val dir = dirFor(context, id) ?: return null
        return readDir(id, dir)
    }

    /** All complete downloads, newest first. Call off the main thread. */
    fun list(context: Context): List<OfficialAudioLocal> =
        officialDir(context).listFiles().orEmpty()
            .filter { it.isDirectory && suffixPattern.matches(it.name) }
            .mapNotNull { dir -> readDir("official:${dir.name}", dir) }
            .sortedByDescending { it.downloadedAtMs }

    private fun readDir(id: String, dir: File): OfficialAudioLocal? {
        val infoFile = File(dir, "info.json")
        if (!infoFile.isFile) return null
        val info = runCatching { JSONObject(infoFile.readText()) }.getOrNull() ?: return null
        if (info.optString("id") != id) return null
        val file = File(dir, "audio.bin")
        if (!file.isFile || file.length() <= 0L) return null
        if (file.length() > PolicyAudioLibrary.MAX_AUDIO_BYTES) return null
        val sha = info.optString("sha256", "")
        if (!shaPattern.matches(sha)) return null
        val bytes = info.optLong("bytes", -1L)
        if (bytes <= 0L || bytes > PolicyAudioLibrary.MAX_AUDIO_BYTES) return null
        if (file.length() != bytes) return null
        val duration = info.optLong("durationMs", 0L)
        if (duration !in 1..PolicyAudioLibrary.MAX_AUDIO_DURATION_MS) return null
        val name = info.optString("name", "").trim().ifBlank { id }
        val nameEn = info.optString("nameEn", "").trim().ifBlank { name }
        val license = info.optString("license", "").trim()
            .takeIf { it.length in 1..MAX_ATTRIBUTION_LENGTH }
            ?: return null
        val artist = info.optString("artist", "").trim()
            .takeIf { it.length in 1..MAX_ATTRIBUTION_LENGTH }
        val sourceUrl = info.optString("sourceUrl", "").trim()
            .takeIf { it.startsWith("https://") }
        return OfficialAudioLocal(
            id = id,
            track = OfficialAudioTrack(
                id = id,
                name = name,
                nameEn = nameEn,
                artist = artist,
                license = license,
                sourceUrl = sourceUrl,
                durationMs = duration,
                bytes = bytes,
                sha256 = sha,
                // A downloaded file is local; no request URL or other network dependency remains.
                url = "",
            ),
            file = file,
            downloadedAtMs = info.optLong("downloadedAt", dir.lastModified()),
        )
    }

    fun delete(context: Context, id: String): Boolean {
        val dir = dirFor(context, id) ?: return false
        if (!dir.isDirectory) return false
        return dir.deleteRecursively()
    }

    /** A killed process may leave `.part`, `.stage` or `.old`; READY directories stay. */
    fun cleanupStale(context: Context) {
        val download = downloadDir(context)
        val before = System.currentTimeMillis() - STALE_MS
        runCatching {
            download.listFiles().orEmpty()
                .filter { it.lastModified() < before }
                .forEach { file ->
                    if (!file.deleteRecursively()) {
                        Log.w(TAG, "could not remove a stale audio download")
                    }
                }
        }.onFailure { Log.w(TAG, "could not clean the audio download folder", it) }
    }
}