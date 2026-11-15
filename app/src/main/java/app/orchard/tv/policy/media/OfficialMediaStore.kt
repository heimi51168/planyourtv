package app.orchard.tv.policy.media

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

data class OfficialMediaLocal(
    val id: String,
    val item: OfficialMediaItem,
    val file: File,
    val poster: File?,
    val downloadedAtMs: Long,
)

/**
 * Files under `filesDir/policy-videos`. Only complete directories with a
 * matching `info.json` are visible; a `.part`, `.new` or half-swapped directory
 * is never handed to a player.
 */
object OfficialMediaStore {
    private const val TAG = "PolicyMediaStore"
    private const val STALE_MS = 24 * 60 * 60 * 1000L
    private val idPattern = Regex("official:[a-z0-9-]{1,48}")
    private val shaPattern = Regex("[a-f0-9]{64}")

    fun root(context: Context): File =
        File(context.filesDir, "policy-videos").apply { mkdirs() }

    fun officialDir(context: Context): File =
        File(root(context), "official").apply { mkdirs() }

    fun downloadDir(context: Context): File =
        File(root(context), "download").apply { mkdirs() }

    fun dirFor(context: Context, id: String): File? {
        if (!idPattern.matches(id)) return null
        return File(officialDir(context), id.removePrefix("official:"))
    }

    fun read(context: Context, id: String): OfficialMediaLocal? {
        val dir = dirFor(context, id) ?: return null
        return readDir(id, dir)
    }

    /** All complete downloads, newest first. Call off the main thread. */
    fun list(context: Context): List<OfficialMediaLocal> =
        officialDir(context).listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val suffix = dir.name
                if (!suffix.matches(Regex("[a-z0-9-]{1,48}"))) return@mapNotNull null
                readDir("official:$suffix", dir)
            }
            .sortedByDescending { it.downloadedAtMs }

    private fun readDir(id: String, dir: File): OfficialMediaLocal? {
        val video = File(dir, "video.mp4")
        val infoFile = File(dir, "info.json")
        if (!video.isFile || video.length() <= 0L || !infoFile.isFile) return null
        val info = runCatching { JSONObject(infoFile.readText()) }.getOrNull() ?: return null
        if (info.optString("id") != id) return null
        val sha = info.optString("sha256", "")
        if (!shaPattern.matches(sha)) return null
        val bytes = info.optLong("bytes", -1L)
        if (bytes <= 0L || video.length() != bytes) return null
        val duration = info.optLong("durationMs", 0L)
        val width = info.optInt("width", 0)
        val height = info.optInt("height", 0)
        if (duration !in 1..PolicyVideoValidation.MAX_DURATION_MS) return null
        if (width !in 1..PolicyVideoValidation.MAX_WIDTH ||
            height !in 1..PolicyVideoValidation.MAX_HEIGHT
        ) {
            return null
        }
        val name = info.optString("name", "").ifBlank { id }
        val nameEn = info.optString("nameEn", "").ifBlank { name }
        val roles = LinkedHashSet<PolicyVideoRole>()
        info.optJSONArray("roles")?.let { array ->
            for (index in 0 until array.length()) {
                PolicyVideoRole.fromWire(array.optString(index))?.let(roles::add)
            }
        }
        val posterFile = File(dir, "poster.jpg").takeIf { it.isFile && it.length() > 0L }
        val poster = posterFile?.let {
            PolicyVideoPoster(
                url = "",
                sha256 = info.optString("posterSha256", ""),
                bytes = it.length(),
                atMs = info.optLong("posterAtMs", PolicyVideoValidation.POSTER_AT_MS),
            )
        } ?: PolicyVideoPoster("", "", 0L, PolicyVideoValidation.POSTER_AT_MS)
        return OfficialMediaLocal(
            id = id,
            item = OfficialMediaItem(
                id = id,
                name = name,
                nameEn = nameEn,
                roles = roles,
                durationMs = duration,
                width = width,
                height = height,
                fps = info.optInt("fps", 0),
                bytes = bytes,
                sha256 = sha,
                hasAudio = info.optBoolean("hasAudio", false),
                url = "",
                poster = poster,
            ),
            file = video,
            poster = posterFile,
            downloadedAtMs = info.optLong("downloadedAt", dir.lastModified()),
        )
    }

    fun delete(context: Context, id: String): Boolean {
        val dir = dirFor(context, id) ?: return false
        if (!dir.isDirectory) return false
        return dir.deleteRecursively()
    }

    /**
     * A killed process leaves `.part`, `.new`, `.stage` and half-swapped `.old`
     * directories. READY directories are never touched.
     */
    fun cleanupStale(context: Context) {
        val download = downloadDir(context)
        val before = System.currentTimeMillis() - STALE_MS
        runCatching {
            download.listFiles().orEmpty()
                .filter { it.lastModified() < before }
                .forEach { file ->
                    val deleted = file.deleteRecursively()
                    if (!deleted) Log.w(TAG, "could not remove a stale media download")
                }
        }.onFailure { Log.w(TAG, "could not clean the media download folder", it) }
    }
}
