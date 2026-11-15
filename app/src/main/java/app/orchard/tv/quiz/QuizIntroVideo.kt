package app.orchard.tv.quiz

import android.content.Context
import android.util.Log
import app.orchard.tv.policy.media.PolicyVideoValidation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

data class QuizIntroVideoEntry(
    val id: String,
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val poster: File? = null,
)

/**
 * The parent center's device-local library of quiz intro films.
 *
 * This deliberately does not reuse [app.orchard.tv.wallpaper.ScreensaverVideos]: those films
 * are allowed to be 2 GB and are tied to the screensaver's own list. Here every film must be a
 * short 16:9 H.264 MP4 that Android 5.0 can decode without transcoding, and the files live in
 * a separate directory. Uploads are validated before they are published, then moved into a
 * complete directory so a failed upload can never become the active film.
 *
 * V30 removed the two films that used to ship in the APK. The object remains the local-upload
 * store behind [app.orchard.tv.policy.media.PolicyVideoLibrary]; official cloud films live under
 * `policy-videos/` instead. The decode contract is shared through
 * [app.orchard.tv.policy.media.PolicyVideoValidation] so the two paths cannot drift apart.
 */
object QuizIntroVideo {
    const val MAX_FILE_BYTES = PolicyVideoValidation.MAX_FILE_BYTES
    const val MAX_VIDEOS = 6
    const val MAX_TOTAL_BYTES = 24L * 1024 * 1024
    private const val KEEP_FREE_BYTES = 64L * 1024 * 1024
    const val MAX_DURATION_MS = PolicyVideoValidation.MAX_DURATION_MS
    private const val MAX_NAME_LENGTH = 80
    private const val PREFS = "quiz-intro"
    private const val KEY_ACTIVE = "active_id"
    private const val TAG = "QuizIntroVideo"
    private val idPattern = Regex("[a-f0-9]{32}")

    sealed class ReceiveResult {
        data class Ok(val entry: QuizIntroVideoEntry) : ReceiveResult()
        data class Rejected(val code: String) : ReceiveResult()
    }

    private fun directory(context: Context): File =
        File(context.filesDir, "quiz-intro").apply { mkdirs() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the library from disk. Call off the main thread. */
    @Synchronized
    fun all(context: Context): List<QuizIntroVideoEntry> = directory(context).listFiles().orEmpty()
        .filter { it.isDirectory && idPattern.matches(it.name) }
        .mapNotNull { dir -> runCatching { readEntry(dir) }.getOrNull() }
        .sortedWith(compareByDescending<QuizIntroVideoEntry> { it.createdAt }.thenBy { it.id })

    private fun readEntry(dir: File): QuizIntroVideoEntry {
        val file = File(dir, "video.mp4").takeIf { it.isFile && it.length() > 0 } ?: error("missing video")
        val info = JSONObject(File(dir, "info.json").readText())
        val name = info.optString("name", "").ifBlank { "video.mp4" }
        return QuizIntroVideoEntry(
            id = dir.name,
            name = name,
            file = file,
            sizeBytes = file.length(),
            durationMs = info.optLong("durationMs", 0L),
            width = info.optInt("width", 0),
            height = info.optInt("height", 0),
            createdAt = info.optLong("createdAt", dir.lastModified()),
            poster = File(dir, "poster.jpg").takeIf { it.isFile && it.length() > 0 },
        )
    }

    fun posterOf(context: Context, id: String): File? {
        if (!idPattern.matches(id)) return null
        return File(directory(context), "$id/poster.jpg")
            .takeIf { it.isFile && it.length() > 0 }
    }

    /** [name] is the uploaded file's name, only used to derive a display title and check MP4. */
    fun receive(context: Context, input: InputStream, length: Long, name: String): ReceiveResult {
        val title = titleOf(name) ?: return ReceiveResult.Rejected("INTRO_VIDEO_BAD_FORMAT")
        if (length <= 0L) return ReceiveResult.Rejected("INTRO_VIDEO_INVALID")
        if (length > MAX_FILE_BYTES) return ReceiveResult.Rejected("INTRO_VIDEO_TOO_LARGE")

        val stored = all(context)
        if (stored.size >= MAX_VIDEOS) {
            return ReceiveResult.Rejected("INTRO_VIDEO_LIBRARY_FULL")
        }
        if (stored.sumOf { it.sizeBytes } + length > MAX_TOTAL_BYTES) {
            return ReceiveResult.Rejected("INTRO_VIDEO_LIBRARY_FULL")
        }
        val root = directory(context)
        if (root.usableSpace < length + KEEP_FREE_BYTES) {
            return ReceiveResult.Rejected("INTRO_VIDEO_NO_SPACE")
        }

        val part = File(root, "import-${UUID.randomUUID().toString().replace("-", "")}.part")
        return try {
            part.outputStream().use { out ->
                val buffer = ByteArray(128 * 1024)
                var left = length
                while (left > 0L) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (read < 0) return ReceiveResult.Rejected("INTRO_VIDEO_INVALID")
                    if (read == 0) continue
                    out.write(buffer, 0, read)
                    left -= read
                }
            }
            publish(context, part, title)
        } catch (error: IOException) {
            Log.w(TAG, "intro video upload broke off", error)
            ReceiveResult.Rejected("INTRO_VIDEO_INVALID")
        } finally {
            part.delete()
        }
    }

    /** Validates the completed upload and publishes it as one complete directory. */
    @Synchronized
    private fun publish(context: Context, upload: File, title: String): ReceiveResult {
        val root = directory(context)
        root.listFiles()
            ?.filter { it.name.startsWith("import-") && it.lastModified() < System.currentTimeMillis() - 3_600_000L }
            ?.forEach { it.deleteRecursively() }

        val size = upload.length()
        if (size <= 0L || size > MAX_FILE_BYTES) {
            return ReceiveResult.Rejected("INTRO_VIDEO_INVALID")
        }
        val stored = all(context)
        if (stored.size >= MAX_VIDEOS || stored.sumOf { it.sizeBytes } + size > MAX_TOTAL_BYTES) {
            return ReceiveResult.Rejected("INTRO_VIDEO_LIBRARY_FULL")
        }
        if (root.usableSpace < size + KEEP_FREE_BYTES) {
            return ReceiveResult.Rejected("INTRO_VIDEO_NO_SPACE")
        }
        val validated = when (val result = PolicyVideoValidation.validate(upload)) {
            is PolicyVideoValidation.Result.Rejected -> return ReceiveResult.Rejected(result.code)
            is PolicyVideoValidation.Result.Ok -> result.info
        }

        val id = UUID.randomUUID().toString().replace("-", "")
        val staging = File(root, "import-$id")
        val target = File(root, id)
        return try {
            check(staging.mkdir())
            val film = File(staging, "video.mp4")
            // The upload is on the same volume, so a rename avoids writing the bytes again.
            if (!upload.renameTo(film)) upload.copyTo(film)
            val poster = File(staging, "poster.jpg")
            val hasPoster = runCatching {
                poster.outputStream().use {
                    PolicyVideoValidation.writePoster(film, PolicyVideoValidation.POSTER_AT_MS, it)
                }
            }.getOrDefault(false)
            if (!hasPoster) poster.delete()
            File(staging, "info.json").writeText(
                JSONObject()
                    .put("name", title)
                    .put("sizeBytes", film.length())
                    .put("durationMs", validated.durationMs)
                    .put("width", validated.width)
                    .put("height", validated.height)
                    .put("createdAt", System.currentTimeMillis())
                    .toString(),
            )
            check(staging.renameTo(target))
            val entry = QuizIntroVideoEntry(
                id = id,
                name = title,
                file = File(target, "video.mp4"),
                sizeBytes = size,
                durationMs = validated.durationMs,
                width = validated.width,
                height = validated.height,
                createdAt = System.currentTimeMillis(),
                poster = File(target, "poster.jpg").takeIf { it.isFile },
            )
            ReceiveResult.Ok(entry)
        } catch (error: Exception) {
            Log.w(TAG, "could not store an intro video", error)
            ReceiveResult.Rejected("INTRO_VIDEO_INVALID")
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Selects a local upload for the quiz role; an empty id clears only that role. */
    @Synchronized
    fun select(context: Context, id: String): Boolean {
        val target = id.trim()
        if (target.isEmpty()) {
            clearActive(context)
            return true
        }
        if (!idPattern.matches(target)) return false
        val file = File(directory(context), "$target/video.mp4")
        if (!file.isFile || file.length() <= 0L) return false
        prefs(context).edit().putString(KEY_ACTIVE, target).apply()
        return true
    }

    @Synchronized
    fun clearActive(context: Context) {
        prefs(context).edit().remove(KEY_ACTIVE).apply()
    }

    @Synchronized
    fun delete(context: Context, id: String): Boolean {
        if (!idPattern.matches(id)) return false
        val dir = File(directory(context), id)
        if (!dir.isDirectory) return false
        val deleted = dir.deleteRecursively()
        if (deleted) {
            prefs(context).edit()
                .remove(KEY_ACTIVE)
                .remove("lock_id")
                .apply()
        }
        return deleted
    }

    /** Snapshot for pages cached before V30. New pages use `media`. */
    fun snapshot(context: Context): JSONObject {
        val videos = all(context)
        val activeId = activeId(context)
        val array = JSONArray()
        videos.forEach { video ->
            array.put(
                JSONObject()
                    .put("id", video.id)
                    .put("name", video.name)
                    .put("sizeBytes", video.sizeBytes)
                    .put("durationMs", video.durationMs)
                    .put("width", video.width)
                    .put("height", video.height)
                    .put("createdAt", video.createdAt)
                    .put("active", video.id == activeId),
            )
        }
        return JSONObject()
            .put("activeId", activeId)
            .put("builtins", JSONArray())
            .put("videos", array)
            .put("maxVideos", MAX_VIDEOS)
            .put("maxFileBytes", MAX_FILE_BYTES)
            .put("maxTotalBytes", MAX_TOTAL_BYTES)
            .put("maxDurationMs", MAX_DURATION_MS)
    }

    private fun activeId(context: Context): String {
        val stored = prefs(context).getString(KEY_ACTIVE, null) ?: return ""
        if (!idPattern.matches(stored)) return ""
        val file = File(directory(context), "$stored/video.mp4")
        return stored.takeIf { file.isFile && file.length() > 0L }.orEmpty()
    }

    /**
     * The film's title from the uploaded file's [name], or null if it is not an MP4. Like the
     * screensaver library, the extension is read before the title is cut to length.
     */
    internal fun titleOf(name: String): String? {
        val fileName = name.substringAfterLast('/').substringAfterLast('\\').trim()
        if (fileName.isEmpty()) return null
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return null
        val extension = fileName.substring(dot)
        if (extension.lowercase() != ".mp4") return null
        var end = minOf(dot, MAX_NAME_LENGTH - extension.length)
        // Never cut through a surrogate pair: half a character cannot be stored.
        if (end in 1 until dot && Character.isHighSurrogate(fileName[end - 1])) end--
        if (end <= 0) return null
        return fileName.substring(0, end) + extension
    }
}
