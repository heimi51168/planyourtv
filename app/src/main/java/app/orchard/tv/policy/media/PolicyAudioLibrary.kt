package app.orchard.tv.policy.media

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * One background-music track. [source] is `local` for a file uploaded through the parent page
 * and `official` for a verified copy downloaded from the official catalogue. The player only
 * needs [file], so both sources behave identically once the bytes are on the TV.
 */
data class PolicyAudioEntry(
    val id: String,
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val durationMs: Long,
    val createdAt: Long,
    val source: String = SOURCE_LOCAL,
    val nameEn: String? = null,
    val artist: String? = null,
    val license: String? = null,
    val sourceUrl: String? = null,
) {
    companion object {
        const val SOURCE_LOCAL = "local"
        const val SOURCE_OFFICIAL = "official"
    }
}

/**
 * The parent center's device-local background-music library.
 *
 * A track is accepted only when this TV can decode it without transcoding: MP3, AAC in an
 * M4A/ADTS container, or Ogg. Video files and remote URLs are never accepted here; the parent
 * uploads a file through the same raw-body route as wallpapers. The track is published as one
 * complete directory, so an interrupted upload can never become the selected music.
 *
 * The library is shared by all child members; the selection is scoped by
 * [PolicyVideoLibrary], which also owns the delete cleanup.
 */
object PolicyAudioLibrary {
    /** 20 MiB is far beyond a 30-minute speech/music track at normal bit rates. */
    const val MAX_AUDIO_BYTES = 20L * 1024 * 1024
    const val MAX_AUDIO_DURATION_MS = 30L * 60 * 1000
    const val KEEP_FREE_BYTES = 256L * 1024 * 1024
    private const val MAX_NAME_LENGTH = 80
    private const val TAG = "PolicyAudioLibrary"
    private val idPattern = Regex("[a-f0-9]{32}")
    private val officialIdPattern = Regex("official:[a-z0-9-]{1,48}")
    private val extensions = setOf(".mp3", ".m4a", ".aac", ".ogg", ".oga")

    sealed class ReceiveResult {
        data class Ok(val entry: PolicyAudioEntry) : ReceiveResult()
        data class Rejected(val code: String) : ReceiveResult()
    }

    internal sealed class ProbeResult {
        data class Ok(val durationMs: Long) : ProbeResult()
        data class Rejected(val code: String) : ProbeResult()
    }

    private fun directory(context: Context): File =
        File(context.filesDir, "policy-audio").apply { mkdirs() }

    /** Reads the library from disk. Call off the main thread. */
    @Synchronized
    fun all(context: Context): List<PolicyAudioEntry> = directory(context).listFiles().orEmpty()
        .filter { it.isDirectory && idPattern.matches(it.name) }
        .mapNotNull { dir -> runCatching { readEntry(dir) }.getOrNull() }
        .sortedWith(compareByDescending<PolicyAudioEntry> { it.createdAt }.thenBy { it.id })

    /**
     * Resolves a local upload or a verified official download. A cloud id that has not been
     * downloaded is deliberately unresolved, so no player can ever reach the network.
     */
    fun entry(context: Context, id: String): PolicyAudioEntry? = when {
        idPattern.matches(id) -> all(context).firstOrNull { it.id == id }
        officialIdPattern.matches(id) -> OfficialAudioStore.read(context, id)?.let(::officialEntry)
        else -> null
    }

    fun isAudioId(id: String): Boolean =
        idPattern.matches(id) || officialIdPattern.matches(id)

    fun isOfficialId(id: String): Boolean = officialIdPattern.matches(id)

    private fun officialEntry(local: OfficialAudioLocal): PolicyAudioEntry = PolicyAudioEntry(
        id = local.id,
        name = local.track.name,
        file = local.file,
        sizeBytes = local.track.bytes,
        durationMs = local.track.durationMs,
        createdAt = local.downloadedAtMs,
        source = PolicyAudioEntry.SOURCE_OFFICIAL,
        nameEn = local.track.nameEn,
        artist = local.track.artist,
        license = local.track.license,
        sourceUrl = local.track.sourceUrl,
    )

    private fun readEntry(dir: File): PolicyAudioEntry {
        val info = JSONObject(File(dir, "info.json").readText())
        val file = File(dir, "audio.bin")
            .takeIf { it.isFile && it.length() > 0 }
            ?: error("missing audio")
        return PolicyAudioEntry(
            id = dir.name,
            name = info.optString("name", "").ifBlank { "audio.mp3" },
            file = file,
            sizeBytes = file.length(),
            durationMs = info.optLong("durationMs", 0L),
            createdAt = info.optLong("createdAt", dir.lastModified()),
        )
    }

    /**
     * [name] is the uploaded file's name, used for the display title and to choose the
     * container family. The actual bytes are sniffed again before publishing.
     */
    fun receive(context: Context, input: InputStream, length: Long, name: String): ReceiveResult {
        val title = titleOf(name) ?: return ReceiveResult.Rejected("AUDIO_BAD_FORMAT")
        if (length <= 0L) return ReceiveResult.Rejected("AUDIO_INVALID")
        if (length > MAX_AUDIO_BYTES) return ReceiveResult.Rejected("AUDIO_TOO_LARGE")

        val root = directory(context)
        if (root.usableSpace < length + KEEP_FREE_BYTES) {
            return ReceiveResult.Rejected("AUDIO_NO_SPACE")
        }

        val part = File(root, "import-${UUID.randomUUID().toString().replace("-", "")}.part")
        return try {
            part.outputStream().use { out ->
                val buffer = ByteArray(128 * 1024)
                var left = length
                while (left > 0L) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (read < 0) return ReceiveResult.Rejected("AUDIO_INVALID")
                    if (read == 0) continue
                    out.write(buffer, 0, read)
                    left -= read
                }
            }
            publish(context, part, title)
        } catch (error: IOException) {
            Log.w(TAG, "audio upload broke off", error)
            ReceiveResult.Rejected("AUDIO_INVALID")
        } finally {
            part.delete()
        }
    }

    @Synchronized
    private fun publish(context: Context, upload: File, title: String): ReceiveResult {
        val root = directory(context)
        root.listFiles()
            ?.filter { it.name.startsWith("import-") && it.lastModified() < System.currentTimeMillis() - 3_600_000L }
            ?.forEach { it.deleteRecursively() }

        val size = upload.length()
        if (size <= 0L) return ReceiveResult.Rejected("AUDIO_INVALID")
        if (size > MAX_AUDIO_BYTES) return ReceiveResult.Rejected("AUDIO_TOO_LARGE")
        if (!looksLikeAudio(upload)) return ReceiveResult.Rejected("AUDIO_BAD_FORMAT")
        val durationMs = when (val probe = probe(upload)) {
            is ProbeResult.Rejected -> return ReceiveResult.Rejected(probe.code)
            is ProbeResult.Ok -> probe.durationMs
        }
        if (root.usableSpace < size + KEEP_FREE_BYTES) {
            return ReceiveResult.Rejected("AUDIO_NO_SPACE")
        }

        val id = UUID.randomUUID().toString().replace("-", "")
        val staging = File(root, "import-$id")
        val targetDir = File(root, id)
        return try {
            check(staging.mkdir())
            val file = File(staging, "audio.bin")
            if (!upload.renameTo(file)) upload.copyTo(file)
            // Read the size before the staging directory is renamed: afterwards this path no
            // longer exists and length() would report zero for the uploaded entry.
            val storedSize = file.length()
            val createdAt = System.currentTimeMillis()
            File(staging, "info.json").writeText(
                JSONObject()
                    .put("name", title)
                    .put("sizeBytes", storedSize)
                    .put("durationMs", durationMs)
                    .put("createdAt", createdAt)
                    .toString(),
            )
            check(staging.renameTo(targetDir))
            ReceiveResult.Ok(
                PolicyAudioEntry(
                    id = id,
                    name = title,
                    file = File(targetDir, "audio.bin"),
                    sizeBytes = storedSize,
                    durationMs = durationMs,
                    createdAt = createdAt,
                ),
            )
        } catch (error: Exception) {
            Log.w(TAG, "could not store audio", error)
            ReceiveResult.Rejected("AUDIO_INVALID")
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Deletes one track. Selection cleanup across members belongs to [PolicyVideoLibrary]. */
    @Synchronized
    fun delete(context: Context, id: String): Boolean {
        if (!idPattern.matches(id)) return false
        val dir = File(directory(context), id)
        if (!dir.isDirectory) return false
        return dir.deleteRecursively()
    }

    /**
     * Known audio containers. A sync word also covers MP3/ADTS without an ID3 tag; the codec
     * probe below is the real gate, this only rejects obvious non-audio uploads early.
     */
    internal fun looksLikeAudio(file: File): Boolean {
        val head = ByteArray(16)
        val read = file.inputStream().use { it.read(head) }
        if (read < 4) return false
        val ascii = String(head, 0, read, Charsets.ISO_8859_1)
        return when {
            ascii.startsWith("ID3") -> true
            ascii.startsWith("OggS") -> true
            read >= 8 && ascii.substring(4, 8) == "ftyp" -> true
            (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xE0) == 0xE0 -> true
            else -> false
        }
    }

    internal fun probe(file: File): ProbeResult {
        var audioMime: String? = null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(android.media.MediaFormat.KEY_MIME)
                    ?: continue
                if (mime.startsWith("video/")) return ProbeResult.Rejected("AUDIO_BAD_FORMAT")
                if (audioMime == null && mime.startsWith("audio/")) audioMime = mime
            }
        } catch (error: Exception) {
            Log.w(TAG, "could not read an audio upload", error)
            return ProbeResult.Rejected("AUDIO_INVALID")
        } finally {
            runCatching { extractor.release() }
        }
        val mime = audioMime ?: return ProbeResult.Rejected("AUDIO_BAD_FORMAT")
        if (!hasAudioDecoder(mime)) return ProbeResult.Rejected("AUDIO_UNSUPPORTED_CODEC")

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") {
                return ProbeResult.Rejected("AUDIO_BAD_FORMAT")
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return ProbeResult.Rejected("AUDIO_INVALID")
            when {
                duration <= 0L -> ProbeResult.Rejected("AUDIO_INVALID")
                duration > MAX_AUDIO_DURATION_MS -> ProbeResult.Rejected("AUDIO_BAD_DURATION")
                else -> ProbeResult.Ok(duration)
            }
        } catch (error: Exception) {
            Log.w(TAG, "could not probe an audio upload", error)
            ProbeResult.Rejected("AUDIO_INVALID")
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun hasAudioDecoder(mime: String): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
            !codec.isEncoder && codec.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }

    /** The display title from the uploaded name; null for an unsupported container. */
    internal fun titleOf(name: String): String? {
        val fileName = name.substringAfterLast('/').substringAfterLast('\\').trim()
        if (fileName.isEmpty()) return null
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return null
        val extension = fileName.substring(dot)
        if (extension.lowercase() !in extensions) return null
        var end = minOf(dot, MAX_NAME_LENGTH - extension.length)
        if (end in 1 until dot && Character.isHighSurrogate(fileName[end - 1])) end--
        if (end <= 0) return null
        return fileName.substring(0, end) + extension
    }

    /** The title shown in the parent center: the stored file name without its extension. */
    fun displayName(name: String): String = name.substringBeforeLast('.', name)
}