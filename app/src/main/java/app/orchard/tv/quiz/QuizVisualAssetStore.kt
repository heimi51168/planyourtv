package app.orchard.tv.quiz

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizRichVisual
import com.planyourtv.policy.quiz.QuizVisualCodec
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
 * Content-addressed cache for question figures.
 *
 * Only [CloudQuizPreparer] downloads here. The execution path calls [fileFor]
 * and reads a local file; it never sees a URL, a token or a network client.
 */
object QuizVisualAssetStore {
    const val QB_FIGURE_ASSET_ID = "qb/figure"

    private const val TAG = "QuizVisualAssetStore"
    private const val ROOT_DIR = "quiz-assets"
    private const val MEDIA_FILE = "media.png"
    private const val MAX_IMAGE_BYTES = 512 * 1024
    private const val DEFAULT_QUOTA_BYTES = 64L * 1024 * 1024
    private const val MAX_URL_CHARS = 2048
    private const val MAX_DIMENSION = 4096
    private const val MAX_PIXELS = 4_000_000L
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val ACCEPT_IMAGE_PNG = "image/png"

    private val lock = Any()
    @Volatile private var root: File? = null

    data class StoredImage(val visual: QuizRichVisual, val file: File)

    /** Idempotently creates the cache root and removes interrupted downloads. */
    @Synchronized
    fun install(context: Context) {
        val dir = File(context.applicationContext.filesDir, ROOT_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "cannot create quiz asset directory")
            root = null
            return
        }
        root = dir
        runCatching {
            dir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".part") }
                .forEach { it.delete() }
        }.onFailure { Log.w(TAG, "cannot clean partial quiz assets", it) }
    }

    /**
     * The only execution-side accessor. Returns null when the asset is absent
     * or the reference is not a safe, supported PNG.
     */
    fun fileFor(rich: QuizRichVisual): File? {
        if (rich.kind != QuizVisualCodec.KIND_IMAGE ||
            rich.mime != QuizVisualCodec.MIME_PNG ||
            !QuizVisualCodec.isValidAssetId(rich.assetId)
        ) {
            return null
        }
        val hex = QuizVisualCodec.shaHex(rich.sha256) ?: return null
        val base = root ?: return null
        return runCatching {
            val baseCanonical = base.canonicalFile
            val dir = File(File(base, rich.assetId), hex).canonicalFile
            if (!dir.path.startsWith(baseCanonical.path + File.separator)) return null
            File(dir, MEDIA_FILE).takeIf { it.isFile && it.length() > 0L }
        }.getOrNull()
    }

    /**
     * Reuse is allowed only when every picture is still present and still
     * hashes to the reference recorded in the cached question.
     */
    fun hasAllAssets(items: List<QuizItem>): Boolean = items.all { item ->
        val rich = item.visual?.rich ?: return@all true
        hasAsset(rich)
    }

    fun protectedShas(items: List<QuizItem>): Set<String> = items.mapNotNull { item ->
        item.visual?.rich?.let { rich -> QuizVisualCodec.shaHex(rich.sha256) }
    }.toSet()

    /**
     * Downloads and validates one figure. The URL is transient and is never
     * written to the cache or to a log line.
     */
    fun fetchImage(assetId: String, rawUrl: String): Result<StoredImage> = runCatching {
        if (!QuizVisualCodec.isValidAssetId(assetId)) {
            throw IOException("invalid asset id")
        }
        if (!isAllowedImageUrl(rawUrl)) throw IOException("unsafe image URL")
        val uri = URI(rawUrl)
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            useCaches = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", ACCEPT_IMAGE_PNG)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("image HTTP $code")
            val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            if (contentType.isNotEmpty() && !contentType.startsWith(QuizVisualCodec.MIME_PNG)) {
                throw IOException("image content type is not PNG")
            }
            val declaredLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
            if (declaredLength != null && declaredLength > MAX_IMAGE_BYTES) {
                throw IOException("image is too large")
            }
            val bytes = connection.inputStream.use(::readLimited)
            validatePng(bytes)
            val hex = sha256(bytes)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION ||
                width.toLong() * height > MAX_PIXELS
            ) {
                throw IOException("image dimensions are out of range")
            }
            val file = writeAtomically(assetId, hex, bytes)
            val visual = QuizRichVisual(
                assetId = assetId,
                sha256 = "sha256:$hex",
                kind = QuizVisualCodec.KIND_IMAGE,
                mime = QuizVisualCodec.MIME_PNG,
                width = width,
                height = height,
                altText = "",
                essential = true,
            )
            StoredImage(visual, file)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Drops one namespace without touching the local bank, local uploads or
     * any other visual namespace.
     */
    @Synchronized
    fun clearNamespace(assetId: String) {
        if (!QuizVisualCodec.isValidAssetId(assetId)) return
        val base = root ?: return
        runCatching {
            val baseCanonical = base.canonicalFile
            val dir = File(base, assetId).canonicalFile
            if (!dir.path.startsWith(baseCanonical.path + File.separator)) return
            dir.deleteRecursively()
            removeEmptyParents(dir.parentFile, baseCanonical)
        }.onFailure { Log.w(TAG, "cannot clear quiz visual namespace", it) }
    }

    /**
     * LRU cleanup for orphaned figures. [protectedShas] must contain every
     * content hash used by the round being prepared.
     */
    @Synchronized
    fun prune(
        protectedShas: Set<String>,
        maxBytes: Long = DEFAULT_QUOTA_BYTES,
    ) {
        val base = root ?: return
        runCatching {
            val files = base.walkTopDown()
                .filter { it.isFile && (it.name == MEDIA_FILE || it.name.endsWith(".part")) }
                .toList()
            files.filter { it.name.endsWith(".part") }.forEach { it.delete() }
            val media = files.filter { it.name == MEDIA_FILE && it.length() > 0L }
            var total = media.sumOf { it.length() }
            if (total <= maxBytes) return
            media.sortedBy { it.lastModified() }.forEach { file ->
                if (total <= maxBytes) return@forEach
                val sha = file.parentFile?.name
                if (sha != null && sha in protectedShas) return@forEach
                val length = file.length()
                if (file.delete()) total -= length
            }
            base.walkTopDown()
                .filter { it.isDirectory && it != base && it.list().isNullOrEmpty() }
                .sortedByDescending { it.path.length }
                .forEach { it.delete() }
        }.onFailure { Log.w(TAG, "cannot prune quiz visual cache", it) }
    }

    internal fun isAllowedImageUrl(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty() || text.length > MAX_URL_CHARS) return false
        val uri = runCatching { URI(text) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.path.orEmpty().lowercase(Locale.ROOT).endsWith(".png")
    }

    internal fun validatePng(bytes: ByteArray) {
        val magic = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        )
        if (bytes.size < magic.size || !bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            throw IOException("not a PNG")
        }
        if (bytes.size > MAX_IMAGE_BYTES) throw IOException("image is too large")
    }

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun hasAsset(rich: QuizRichVisual): Boolean {
        val file = fileFor(rich) ?: return false
        val expected = QuizVisualCodec.shaHex(rich.sha256) ?: return false
        return runCatching {
            file.length() <= MAX_IMAGE_BYTES && sha256(file.readBytes()) == expected
        }.getOrDefault(false)
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_IMAGE_BYTES) throw IOException("image is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun writeAtomically(assetId: String, hex: String, bytes: ByteArray): File {
        val base = root ?: throw IOException("quiz asset store is not installed")
        val dir = File(File(base, assetId), hex)
        synchronized(lock) {
            if (!dir.exists() && !dir.mkdirs()) throw IOException("cannot create asset directory")
            val target = File(dir, MEDIA_FILE)
            if (target.isFile && target.length() == bytes.size.toLong() &&
                sha256(target.readBytes()) == hex
            ) {
                // Content-addressed path: an intact existing file is the same object.
                return target
            }
            val temp = File.createTempFile("media-", ".part", dir)
            try {
                FileOutputStream(temp).use { out ->
                    out.write(bytes)
                    out.fd.sync()
                }
                if (!temp.renameTo(target)) {
                    target.delete()
                    if (!temp.renameTo(target)) throw IOException("cannot publish asset")
                }
                return target
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    private fun removeEmptyParents(start: File?, stopAt: File) {
        var current = start
        while (current != null && current != stopAt && current.path.startsWith(stopAt.path)) {
            if (!current.delete()) return
            current = current.parentFile
        }
    }
}