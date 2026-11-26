package app.orchard.tv.quiz

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/**
 * Local cache for cloud quiz speech.
 *
 * Only the cloud speech path fetches here. Playback reads a local file and
 * never sees a URL or a token. The cache key comes from the part name and its
 * source text, not from the URL, so a downloaded clip replays offline. A
 * server-side voice change yields a different URL on a future cache miss; the
 * old clip remains harmless until LRU cleanup or unbind.
 */
object QuizTtsCache {
    private const val TAG = "QuizTtsCache"
    private const val ROOT_DIR = "quiz-tts"
    private const val CACHE_VERSION = "v1"
    private const val EXT = ".mp3"
    private const val PART_EXT = ".part"
    private const val MAX_AUDIO_BYTES = 2 * 1024 * 1024
    private const val DEFAULT_QUOTA_BYTES = 64L * 1024 * 1024
    private const val MAX_URL_CHARS = 2048
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val ACCEPT_AUDIO = "audio/mpeg"

    private val KEY_RE = Regex("[0-9a-f]{64}")
    private val lock = Any()

    @Volatile private var root: File? = null

    /**
     * Bumped by [clear]. Downloads that outlive an unbind must not repopulate
     * the cache after it has been wiped.
     */
    @Volatile private var epoch = 0

    /**
     * One utterance chunk. [name] is the QB part (`stem`, `optionN`, ...) and
     * [cacheText] is the local source text used only for the cache key.
     */
    data class Part(val name: String, val cacheText: String)

    /** Idempotently creates the cache root and removes interrupted downloads. */
    @Synchronized
    fun install(context: Context) {
        val dir = File(context.applicationContext.filesDir, ROOT_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "cannot create quiz TTS directory")
            root = null
            return
        }
        root = dir
        runCatching {
            dir.listFiles()?.filter { it.isFile && it.name.endsWith(PART_EXT) }
                ?.forEach { it.delete() }
        }.onFailure { Log.w(TAG, "cannot clean partial quiz TTS", it) }
    }

    /**
     * Returns a playable local clip, downloading it on a cache miss. Returns
     * null on any failure; voice is an aid and never gates answering.
     *
     * [protectedKeys] is every clip of the current utterance, so quota cleanup
     * cannot evict a part that is still playing or queued to play next.
     */
    fun ensure(
        attemptUid: String,
        questionId: String,
        part: Part,
        protectedKeys: Set<String> = emptySet(),
    ): File? {
        val key = keyFor(part)
        fileFor(key)?.let { cached ->
            if (isPlayable(cached)) {
                // Touch on hit so prune is a real LRU, not creation order.
                runCatching { cached.setLastModified(System.currentTimeMillis()) }
                return cached
            }
            runCatching { cached.delete() }
        }
        val epochAtStart = epoch
        val clip = try {
            QbClient.fetchTts(attemptUid, questionId, part.name)
        } catch (e: Exception) {
            Log.w(TAG, "tts url failed: ${e.javaClass.simpleName}")
            return null
        }
        val bytes = try {
            download(clip.url)
        } catch (e: Exception) {
            Log.w(TAG, "tts download failed: ${e.javaClass.simpleName}")
            return null
        }
        if (epochAtStart != epoch) return null
        val file = try {
            writeAtomically(key, bytes, epochAtStart)
        } catch (e: Exception) {
            Log.w(TAG, "tts store failed: ${e.javaClass.simpleName}")
            return null
        } ?: return null
        runCatching { prune(protectedKeys + key) }
        return file
    }

    internal fun keyFor(part: Part): String = keyFor(part.name, part.cacheText)

    internal fun keyFor(name: String, text: String): String =
        sha256("$CACHE_VERSION|$name|$text".toByteArray(Charsets.UTF_8))

    internal fun fileFor(key: String): File? {
        if (!KEY_RE.matches(key)) return null
        val base = root ?: return null
        return runCatching {
            val baseCanonical = base.canonicalFile
            val file = File(base, key + EXT).canonicalFile
            if (file.parentFile != baseCanonical) return null
            file.takeIf { it.isFile && it.length() in 1..MAX_AUDIO_BYTES.toLong() }
        }.getOrNull()
    }

    internal fun isAllowedAudioUrl(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty() || text.length > MAX_URL_CHARS) return false
        val uri = runCatching { URI(text) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.path.orEmpty().lowercase(Locale.ROOT).endsWith(EXT)
    }

    internal fun validateMp3(bytes: ByteArray) {
        if (bytes.size < 3) throw IOException("audio is too short")
        if (bytes.size > MAX_AUDIO_BYTES) throw IOException("audio is too large")
        val id3 = bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
        val frame = bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0
        if (!id3 && !frame) throw IOException("not an MP3")
    }

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** Drops every cloud audio file; the local bank and its uploads are untouched. */
    @Synchronized
    fun clear() {
        synchronized(lock) {
            epoch++
            val base = root ?: return
            runCatching {
                base.listFiles()?.forEach { it.deleteRecursively() }
            }.onFailure { Log.w(TAG, "cannot clear quiz TTS cache", it) }
        }
    }

    /** LRU cleanup. [protectedKeys] must contain the clip currently in use. */
    @Synchronized
    fun prune(protectedKeys: Set<String>, maxBytes: Long = DEFAULT_QUOTA_BYTES) {
        synchronized(lock) {
            val base = root ?: return
            runCatching {
                val files = base.listFiles()?.filter { it.isFile } ?: return
                files.filter { it.name.endsWith(PART_EXT) }.forEach { it.delete() }
                val audio = files.filter { it.name.endsWith(EXT) && it.length() > 0L }
                var total = audio.sumOf { it.length() }
                if (total <= maxBytes) return
                audio.sortedBy { it.lastModified() }.forEach { file ->
                    if (total <= maxBytes) return@forEach
                    if (file.name.removeSuffix(EXT) in protectedKeys) return@forEach
                    val length = file.length()
                    if (file.delete()) total -= length
                }
            }.onFailure { Log.w(TAG, "cannot prune quiz TTS cache", it) }
        }
    }

    private fun download(rawUrl: String): ByteArray {
        if (!isAllowedAudioUrl(rawUrl)) throw IOException("unsafe audio URL")
        val uri = URI(rawUrl)
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            useCaches = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", ACCEPT_AUDIO)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("audio HTTP $code")
            val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            if (contentType.isNotEmpty() &&
                !contentType.startsWith(ACCEPT_AUDIO) &&
                !contentType.startsWith("audio/mp3")
            ) {
                throw IOException("audio content type is not MP3")
            }
            val declaredLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
            if (declaredLength != null && declaredLength > MAX_AUDIO_BYTES) {
                throw IOException("audio is too large")
            }
            val bytes = connection.inputStream.use(::readLimited)
            validateMp3(bytes)
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_AUDIO_BYTES) throw IOException("audio is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun writeAtomically(key: String, bytes: ByteArray, epochAtStart: Int): File? {
        val base = root ?: throw IOException("quiz TTS cache is not installed")
        synchronized(lock) {
            // An unbind wiped the cache while this clip was downloading.
            if (epochAtStart != epoch) return null
            val target = File(base, key + EXT)
            if (target.isFile && target.length() == bytes.size.toLong() && isPlayable(target)) {
                return target
            }
            val temp = File.createTempFile("tts-", PART_EXT, base)
            try {
                FileOutputStream(temp).use { out ->
                    out.write(bytes)
                    out.fd.sync()
                }
                if (!temp.renameTo(target)) {
                    target.delete()
                    if (!temp.renameTo(target)) throw IOException("cannot publish audio")
                }
                return target
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    private fun isPlayable(file: File): Boolean = runCatching {
        if (file.length() !in 1..MAX_AUDIO_BYTES.toLong()) return false
        val head = ByteArray(3)
        file.inputStream().use { input ->
            if (input.read(head) != head.size) return false
        }
        validateMp3(head)
        true
    }.getOrDefault(false)
}
