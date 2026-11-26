package app.orchard.tv.policy.media

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * V56 device-level cache of the parent's remote uploads.
 *
 * The remote wallpaper page cannot write the TV's disk. An upload becomes usable only after the
 * TV downloads it here, verifies the declared byte count and SHA-256, and reports READY. The
 * cache is device level (not per member), because the local V56 page's upload library is shared
 * by every member of the TV; the remote page still only lists the current member's uploads.
 *
 * This directory is separate from `quiz-intro/`, `policy-videos/`, `policy-audio/` and
 * `hosted-media/`. Deleting a cached upload never touches a published hosted file, and the
 * hosted sync copies from here instead of re-downloading the same bytes.
 */
enum class CustomMediaKind(val wire: String) {
    VIDEO("VIDEO"),
    IMAGE("IMAGE"),
    AUDIO("AUDIO");

    val isAudio: Boolean get() = this == AUDIO

    companion object {
        fun fromWire(value: String?): CustomMediaKind? = when (value?.trim()?.uppercase()) {
            "VIDEO" -> VIDEO
            "IMAGE" -> IMAGE
            "AUDIO" -> AUDIO
            else -> null
        }
    }
}

/** One library entry as the backend signs it; [url] is used for the fetch only, never stored. */
data class CustomMediaRemote(
    val id: Long,
    val kind: CustomMediaKind,
    val name: String,
    val ext: String,
    val version: Long,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val hasAudio: Boolean?,
) {
    val key: String get() = "custom:$id"
}

/** One complete cached upload. Only files that pass every check below become visible. */
data class CustomMediaLocal(
    val id: Long,
    val kind: CustomMediaKind,
    val file: File,
    val sha256: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val hasAudio: Boolean?,
    val version: Long,
    val name: String,
    val ext: String,
    val downloadedAtMs: Long,
) {
    val key: String get() = "custom:$id"
}

object CustomMediaStore {
    private const val TAG = "CustomMediaStore"
    private const val ROOT_DIR = "policy-uploads"
    private const val DATA_FILE = "data.bin"
    private const val INFO_FILE = "info.json"
    private const val STALE_MS = 24 * 60 * 60 * 1000L
    private val idPattern = Regex("[1-9][0-9]{0,18}")
    private val shaPattern = Regex("[a-f0-9]{64}")
    private val extPattern = Regex("[a-z0-9]{1,8}")

    fun root(context: Context): File =
        File(context.filesDir, ROOT_DIR).apply { mkdirs() }

    fun bucketDir(context: Context, kind: CustomMediaKind): File =
        File(root(context), if (kind.isAudio) "audio" else "media").apply { mkdirs() }

    fun downloadDir(context: Context): File =
        File(root(context), "download").apply { mkdirs() }

    fun dirFor(context: Context, kind: CustomMediaKind, id: Long): File? {
        if (id <= 0L) return null
        return File(bucketDir(context, kind), id.toString())
    }

    fun read(context: Context, kind: CustomMediaKind, id: Long): CustomMediaLocal? {
        val dir = dirFor(context, kind, id) ?: return null
        return readDir(kind, id, dir)
    }

    fun readByKey(context: Context, key: String): CustomMediaLocal? {
        val id = idOfKey(key) ?: return null
        val media = read(context, CustomMediaKind.VIDEO, id)
            ?: read(context, CustomMediaKind.IMAGE, id)
        return media ?: read(context, CustomMediaKind.AUDIO, id)
    }

    /** Every complete cached upload. Call off the main thread. */
    fun list(context: Context): List<CustomMediaLocal> =
        CustomMediaKind.entries.flatMap { kind ->
            bucketDir(context, kind).listFiles().orEmpty()
                .filter { it.isDirectory && idPattern.matches(it.name) }
                .mapNotNull { dir -> readDir(kind, dir.name.toLongOrNull() ?: return@mapNotNull null, dir) }
        }.sortedByDescending { it.downloadedAtMs }

    fun delete(context: Context, kind: CustomMediaKind, id: Long): Boolean {
        val dir = dirFor(context, kind, id) ?: return false
        if (!dir.isDirectory) return false
        return dir.deleteRecursively()
    }

    /** Unbind: the uploads belong to the removed family and must not stay on the device. */
    fun clearAll(context: Context) {
        val root = root(context)
        if (root.isDirectory && !root.deleteRecursively()) {
            Log.w(TAG, "could not clear the upload cache after unbind")
        }
    }

    /**
     * True when any published hosted manifest (or its retired preload index) still names this
     * upload. A cached file that is still referenced must not be deleted before the next hosted
     * sync has removed it; otherwise an execution surface could lose its only complete file.
     */
    fun isReferencedByHosted(context: Context, id: Long): Boolean {
        val key = "custom:$id"
        val root = HostedMediaStore.root(context)
        val members = root.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.matches(Regex("[1-9][0-9]{0,18}")) }
        for (dir in members) {
            val memberId = dir.name.toLongOrNull() ?: continue
            // A V52 directory can hold a preload index without a manifest; both name cached
            // files independently, so neither lookup may short-circuit the other.
            val manifest = HostedMediaStore.readManifest(context, memberId)
            if (manifest != null) {
                if (manifest.wallpaper.items.any { it.key == key }) return true
                if (manifest.quizIntro?.key == key) return true
                if (manifest.audio?.key == key) return true
                if (manifest.quizAudio?.key == key) return true
            }
            val preload = HostedMediaStore.readPreloadIndex(context, memberId) ?: continue
            if (preload.items.any { it.key == key }) return true
        }
        return false
    }

    /** A killed download leaves only `download/` files; READY directories are never touched. */
    fun cleanupStale(context: Context) {
        val download = downloadDir(context)
        val before = System.currentTimeMillis() - STALE_MS
        runCatching {
            download.listFiles().orEmpty()
                .filter { it.lastModified() < before }
                .forEach { file ->
                    if (!file.deleteRecursively()) {
                        Log.w(TAG, "could not remove a stale upload download")
                    }
                }
        }.onFailure { Log.w(TAG, "could not clean the upload download folder", it) }
    }

    fun idOfKey(key: String): Long? {
        if (!key.startsWith("custom:")) return null
        val raw = key.removePrefix("custom:")
        if (!idPattern.matches(raw)) return null
        return raw.toLongOrNull()
    }

    fun maxBytes(kind: CustomMediaKind): Long = when (kind) {
        CustomMediaKind.AUDIO -> PolicyAudioLibrary.MAX_AUDIO_BYTES
        CustomMediaKind.IMAGE -> PolicyImageValidation.MAX_STORED_BYTES
        CustomMediaKind.VIDEO -> PolicyVideoValidation.MAX_WALLPAPER_FILE_BYTES
    }

    private fun readDir(kind: CustomMediaKind, id: Long, dir: File): CustomMediaLocal? {
        if (dir.name != id.toString()) return null
        val infoFile = File(dir, INFO_FILE)
        if (!infoFile.isFile) return null
        val info = runCatching { JSONObject(infoFile.readText()) }.getOrNull() ?: return null
        if (info.optLong("id", -1L) != id) return null
        val storedKind = CustomMediaKind.fromWire(info.optString("kind", "")) ?: return null
        if (storedKind != kind) return null
        val file = File(dir, DATA_FILE)
        if (!file.isFile) return null
        val bytes = info.optLong("bytes", -1L)
        if (bytes <= 0L || bytes > maxBytes(kind)) return null
        if (file.length() != bytes) return null
        val sha = info.optString("sha256", "")
        if (!shaPattern.matches(sha)) return null
        val ext = info.optString("ext", "").lowercase()
        if (!extPattern.matches(ext)) return null
        val name = info.optString("name", "").trim().ifBlank { "custom:$id" }
        val version = info.optLong("version", -1L)
        if (version < 0L) return null
        val duration = info.optLong("durationMs", -1L)
        val width = info.optInt("width", 0)
        val height = info.optInt("height", 0)
        val fps = info.optInt("fps", 0)
        val hasAudio = if (info.has("hasAudio") && !info.isNull("hasAudio")) {
            info.optBoolean("hasAudio")
        } else {
            null
        }
        when (kind) {
            CustomMediaKind.IMAGE -> {
                if (duration < 0L) return null
                if (width != PolicyImageValidation.WIDTH ||
                    height != PolicyImageValidation.HEIGHT
                ) {
                    return null
                }
            }
            CustomMediaKind.VIDEO -> {
                if (duration <= 0L) return null
                if (width <= 0 || height <= 0) return null
                if (width > PolicyVideoValidation.MAX_WIDTH ||
                    height > PolicyVideoValidation.MAX_HEIGHT
                ) {
                    return null
                }
                val aspect = width.toDouble() / height
                if (kotlin.math.abs(aspect - 16.0 / 9.0) > 0.03) return null
            }
            CustomMediaKind.AUDIO -> {
                if (duration !in 1..PolicyAudioLibrary.MAX_AUDIO_DURATION_MS) return null
            }
        }
        return CustomMediaLocal(
            id = id,
            kind = kind,
            file = file,
            sha256 = sha,
            sizeBytes = bytes,
            durationMs = duration,
            width = width,
            height = height,
            fps = fps,
            hasAudio = hasAudio,
            version = version,
            name = name,
            ext = ext,
            downloadedAtMs = info.optLong("downloadedAt", dir.lastModified()),
        )
    }
}