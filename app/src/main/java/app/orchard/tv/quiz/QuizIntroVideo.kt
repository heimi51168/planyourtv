package app.orchard.tv.quiz

import android.content.Context
import android.util.Log
import app.orchard.tv.policy.media.PolicyImageValidation
import app.orchard.tv.policy.media.PolicyMediaKind
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
    val kind: PolicyMediaKind = PolicyMediaKind.VIDEO,
)

/**
 * The parent center's device-local library of policy films and pictures.
 *
 * This deliberately does not reuse [app.orchard.tv.wallpaper.ScreensaverVideos]: those films
 * are allowed to be 2 GB and are tied to the screensaver's own list. Here a video must be a
 * short 16:9 MP4/MOV that this TV can decode without transcoding, and a picture is normalised
 * to one 1920x1080 JPEG. Both live in a separate directory. Uploads are validated before they
 * are published, then moved into a complete directory so a failed upload can never become the
 * active wallpaper.
 *
 * V30 removed the two films that used to ship in the APK. The object remains the local-media
 * store behind [app.orchard.tv.policy.media.PolicyVideoLibrary]; official cloud media lives
 * under `policy-videos/` instead. The decode contracts are shared through
 * [PolicyVideoValidation] and [PolicyImageValidation] so local and cloud paths cannot drift.
 */
object QuizIntroVideo {
    /** A video is capped at 4 MiB; a raw picture may be larger and is normalised first. */
    const val MAX_FILE_BYTES = PolicyVideoValidation.MAX_FILE_BYTES
    const val MAX_UPLOAD_BYTES = PolicyImageValidation.MAX_UPLOAD_BYTES
    const val MAX_IMAGE_BYTES = PolicyImageValidation.MAX_STORED_BYTES
    const val MAX_VIDEOS = 6
    const val MAX_TOTAL_BYTES = 24L * 1024 * 1024
    private const val KEEP_FREE_BYTES = 64L * 1024 * 1024
    const val MAX_DURATION_MS = PolicyVideoValidation.MAX_DURATION_MS
    private const val MAX_NAME_LENGTH = 80
    private const val PREFS = "quiz-intro"
    private const val KEY_ACTIVE = "active_id"
    private const val KEY_LOCK = "lock_id"
    private const val TAG = "QuizIntroVideo"
    private val idPattern = Regex("[a-f0-9]{32}")
    private val videoExtensions = setOf(".mp4", ".mov")
    private val imageExtensions = setOf(".jpg", ".jpeg", ".png", ".webp")

    /** Known picture containers we refuse; naming them lets the page show the image error. */
    private val rejectedImageExtensions = setOf(".heic", ".heif", ".avif", ".gif", ".bmp")

    sealed class ReceiveResult {
        data class Ok(val entry: QuizIntroVideoEntry) : ReceiveResult()
        data class Rejected(val code: String) : ReceiveResult()
    }

    internal data class Upload(val title: String, val kind: PolicyMediaKind)

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
        val info = JSONObject(File(dir, "info.json").readText())
        val kind = PolicyMediaKind.fromWireOrDefault(info.optString("kind", ""))
        val file = when (kind) {
            PolicyMediaKind.IMAGE -> File(dir, "image.jpg")
            PolicyMediaKind.VIDEO -> File(dir, "video.mp4")
        }.takeIf { it.isFile && it.length() > 0 } ?: error("missing media")
        val name = info.optString("name", "").ifBlank {
            if (kind == PolicyMediaKind.IMAGE) "image.jpg" else "video.mp4"
        }
        return QuizIntroVideoEntry(
            id = dir.name,
            name = name,
            file = file,
            sizeBytes = file.length(),
            durationMs = info.optLong("durationMs", 0L),
            width = info.optInt("width", 0),
            height = info.optInt("height", 0),
            createdAt = info.optLong("createdAt", dir.lastModified()),
            poster = when (kind) {
                PolicyMediaKind.IMAGE -> null
                PolicyMediaKind.VIDEO -> File(dir, "poster.jpg")
                    .takeIf { it.isFile && it.length() > 0 }
            },
            kind = kind,
        )
    }

    /**
     * The cover of a local item. A picture is its own cover; a video keeps the extracted frame.
     * The caller serves this through the same poster route for both kinds.
     */
    fun posterOf(context: Context, id: String): File? {
        if (!idPattern.matches(id)) return null
        val dir = File(directory(context), id)
        val kind = runCatching {
            PolicyMediaKind.fromWireOrDefault(
                JSONObject(File(dir, "info.json").readText()).optString("kind", ""),
            )
        }.getOrDefault(PolicyMediaKind.VIDEO)
        val poster = when (kind) {
            PolicyMediaKind.IMAGE -> File(dir, "image.jpg")
            PolicyMediaKind.VIDEO -> File(dir, "poster.jpg")
        }
        return poster.takeIf { it.isFile && it.length() > 0 }
    }

    /**
     * [name] is the uploaded file's name, used to derive a display title and to choose the
     * upload limit. The actual format is sniffed again from the bytes before publishing.
     */
    fun receive(context: Context, input: InputStream, length: Long, name: String): ReceiveResult {
        val upload = mediaTitleOf(name) ?: return ReceiveResult.Rejected("INTRO_VIDEO_BAD_FORMAT")
        if (length <= 0L) {
            return ReceiveResult.Rejected(
                if (upload.kind == PolicyMediaKind.IMAGE) "INTRO_IMAGE_INVALID" else "INTRO_VIDEO_INVALID",
            )
        }
        if (upload.kind == PolicyMediaKind.IMAGE) {
            if (length > MAX_UPLOAD_BYTES) return ReceiveResult.Rejected("INTRO_IMAGE_TOO_LARGE")
        } else if (length > MAX_FILE_BYTES) {
            return ReceiveResult.Rejected("INTRO_VIDEO_TOO_LARGE")
        }

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
            publish(context, part, upload)
        } catch (error: IOException) {
            Log.w(TAG, "local media upload broke off", error)
            ReceiveResult.Rejected("INTRO_VIDEO_INVALID")
        } finally {
            part.delete()
        }
    }

    /** Validates the completed upload and publishes it as one complete directory. */
    @Synchronized
    private fun publish(context: Context, upload: File, target: Upload): ReceiveResult {
        val root = directory(context)
        root.listFiles()
            ?.filter { it.name.startsWith("import-") && it.lastModified() < System.currentTimeMillis() - 3_600_000L }
            ?.forEach { it.deleteRecursively() }

        val size = upload.length()
        if (size <= 0L) return ReceiveResult.Rejected("INTRO_VIDEO_INVALID")
        if (target.kind == PolicyMediaKind.IMAGE) {
            if (size > MAX_UPLOAD_BYTES) return ReceiveResult.Rejected("INTRO_IMAGE_TOO_LARGE")
        } else if (size > MAX_FILE_BYTES) {
            return ReceiveResult.Rejected("INTRO_VIDEO_TOO_LARGE")
        }
        val looksLikeImage = PolicyImageValidation.sniffImage(upload)
        if (target.kind == PolicyMediaKind.IMAGE && !looksLikeImage) {
            return ReceiveResult.Rejected("INTRO_IMAGE_BAD_FORMAT")
        }
        if (target.kind == PolicyMediaKind.VIDEO && looksLikeImage) {
            return ReceiveResult.Rejected("INTRO_VIDEO_BAD_FORMAT")
        }

        val stored = all(context)
        if (stored.size >= MAX_VIDEOS) {
            return ReceiveResult.Rejected("INTRO_VIDEO_LIBRARY_FULL")
        }
        if (root.usableSpace < size + KEEP_FREE_BYTES) {
            return ReceiveResult.Rejected("INTRO_VIDEO_NO_SPACE")
        }
        val videoInfo = if (target.kind == PolicyMediaKind.VIDEO) {
            when (val result = PolicyVideoValidation.validateLocalUpload(upload)) {
                is PolicyVideoValidation.Result.Rejected -> return ReceiveResult.Rejected(result.code)
                is PolicyVideoValidation.Result.Ok -> result.info
            }
        } else {
            null
        }

        val id = UUID.randomUUID().toString().replace("-", "")
        val staging = File(root, "import-$id")
        val targetDir = File(root, id)
        return try {
            check(staging.mkdir())
            val file: File
            val durationMs: Long
            val width: Int
            val height: Int
            val poster: File?
            when (target.kind) {
                PolicyMediaKind.VIDEO -> {
                    file = File(staging, "video.mp4")
                    // The upload is on the same volume, so a rename avoids writing the bytes again.
                    if (!upload.renameTo(file)) upload.copyTo(file)
                    val cover = File(staging, "poster.jpg")
                    val hasPoster = runCatching {
                        cover.outputStream().use {
                            PolicyVideoValidation.writePoster(
                                file,
                                PolicyVideoValidation.POSTER_AT_MS,
                                it,
                            )
                        }
                    }.getOrDefault(false)
                    if (!hasPoster) cover.delete()
                    poster = cover.takeIf { it.isFile }
                    durationMs = videoInfo?.durationMs ?: 0L
                    width = videoInfo?.width ?: 0
                    height = videoInfo?.height ?: 0
                }
                PolicyMediaKind.IMAGE -> {
                    file = File(staging, "image.jpg")
                    val normalized = when (val result = PolicyImageValidation.normalize(upload, file)) {
                        is PolicyImageValidation.Result.Rejected -> {
                            return ReceiveResult.Rejected(result.code)
                        }
                        is PolicyImageValidation.Result.Ok -> result
                    }
                    val storedBytes = file.length()
                    if (storedBytes <= 0L || storedBytes > MAX_IMAGE_BYTES) {
                        return ReceiveResult.Rejected("INTRO_IMAGE_TOO_LARGE")
                    }
                    if (stored.sumOf { it.sizeBytes } + storedBytes > MAX_TOTAL_BYTES) {
                        return ReceiveResult.Rejected("INTRO_VIDEO_LIBRARY_FULL")
                    }
                    if (root.usableSpace < storedBytes + KEEP_FREE_BYTES) {
                        return ReceiveResult.Rejected("INTRO_VIDEO_NO_SPACE")
                    }
                    poster = null
                    durationMs = 0L
                    width = normalized.width
                    height = normalized.height
                }
            }
            File(staging, "info.json").writeText(
                JSONObject()
                    .put("kind", target.kind.wire)
                    .put("name", target.title)
                    .put("sizeBytes", file.length())
                    .put("durationMs", durationMs)
                    .put("width", width)
                    .put("height", height)
                    .put("createdAt", System.currentTimeMillis())
                    .toString(),
            )
            check(staging.renameTo(targetDir))
            val entry = QuizIntroVideoEntry(
                id = id,
                name = target.title,
                file = File(targetDir, file.name),
                sizeBytes = file.length(),
                durationMs = durationMs,
                width = width,
                height = height,
                createdAt = System.currentTimeMillis(),
                poster = poster?.let { File(targetDir, it.name) }
                    ?.takeIf { it.isFile },
                kind = target.kind,
            )
            ReceiveResult.Ok(entry)
        } catch (error: Exception) {
            Log.w(TAG, "could not store local media", error)
            ReceiveResult.Rejected(
                if (target.kind == PolicyMediaKind.IMAGE) "INTRO_IMAGE_INVALID" else "INTRO_VIDEO_INVALID",
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Selects a local video for the legacy quiz role; an empty id clears only that role. */
    @Synchronized
    fun select(context: Context, id: String): Boolean {
        val target = id.trim()
        if (target.isEmpty()) {
            clearActive(context)
            return true
        }
        if (!idPattern.matches(target)) return false
        val entry = all(context).firstOrNull { it.id == target } ?: return false
        // Pictures have no playback semantics and must never reach the quiz intro.
        if (entry.kind != PolicyMediaKind.VIDEO) return false
        if (!entry.file.isFile || entry.file.length() <= 0L) return false
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
            val preferences = prefs(context)
            val editor = preferences.edit()
            // Only clear the slots that pointed at this item; another kind's role must survive.
            if (preferences.getString(KEY_ACTIVE, null) == id) editor.remove(KEY_ACTIVE)
            if (preferences.getString(KEY_LOCK, null) == id) editor.remove(KEY_LOCK)
            editor.apply()
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
                    .put("kind", video.kind.wire)
                    .put("name", video.name)
                    .put("sizeBytes", video.sizeBytes)
                    .put("durationMs", video.durationMs)
                    .put("width", video.width)
                    .put("height", video.height)
                    .put("createdAt", video.createdAt)
                    .put("active", video.id == activeId && video.kind == PolicyMediaKind.VIDEO),
            )
        }
        return JSONObject()
            .put("activeId", activeId)
            .put("builtins", JSONArray())
            .put("videos", array)
            .put("maxVideos", MAX_VIDEOS)
            .put("maxFileBytes", MAX_FILE_BYTES)
            .put("maxUploadBytes", MAX_UPLOAD_BYTES)
            .put("maxTotalBytes", MAX_TOTAL_BYTES)
            .put("maxDurationMs", MAX_DURATION_MS)
    }

    private fun activeId(context: Context): String {
        val stored = prefs(context).getString(KEY_ACTIVE, null) ?: return ""
        if (!idPattern.matches(stored)) return ""
        val entry = all(context).firstOrNull { it.id == stored } ?: return ""
        return stored.takeIf { entry.kind == PolicyMediaKind.VIDEO && entry.file.length() > 0L }.orEmpty()
    }

    /**
     * The film's title from the uploaded file's [name], or null if it is not an MP4/MOV. Like
     * the screensaver library, the extension is read before the title is cut to length.
     */
    internal fun titleOf(name: String): String? =
        mediaTitleOf(name)?.takeIf { it.kind == PolicyMediaKind.VIDEO }?.title

    /** The title and media kind from the uploaded name; unknown containers are rejected. */
    internal fun mediaTitleOf(name: String): Upload? {
        val fileName = name.substringAfterLast('/').substringAfterLast('\\').trim()
        if (fileName.isEmpty()) return null
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return null
        val extension = fileName.substring(dot)
        val lower = extension.lowercase()
        val kind = when (lower) {
            in videoExtensions -> PolicyMediaKind.VIDEO
            in imageExtensions, in rejectedImageExtensions -> PolicyMediaKind.IMAGE
            else -> return null
        }
        var end = minOf(dot, MAX_NAME_LENGTH - extension.length)
        // Never cut through a surrogate pair: half a character cannot be stored.
        if (end in 1 until dot && Character.isHighSurrogate(fileName[end - 1])) end--
        if (end <= 0) return null
        return Upload(fileName.substring(0, end) + extension, kind)
    }
}
