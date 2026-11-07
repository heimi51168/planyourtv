package app.orchard.tv.policy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * One small cache for the grant recipient's avatar. The phone app downloads `userIcon` to a
 * local file and the gate renders that file; the TV does the same with a URL-keyed memory and
 * disk cache, so the gate shows the avatar immediately on later grants and falls back to the
 * kid glyph when the TV is offline.
 */
object PolicyAvatarStore {
    private const val MAX_ENTRIES = 8
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val TARGET_PX = 256

    private val memory = object : LruCache<String, ImageBitmap>(MAX_ENTRIES) {}

    suspend fun load(context: Context, url: String): ImageBitmap? {
        val key = url.trim()
        if (key.isEmpty()) return null
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            memory.get(key)?.let { return@withContext it }
            val file = File(cacheDir(context), hash(key))
            val bytes = if (file.exists()) {
                runCatching { file.readBytes() }.getOrNull()
            } else {
                download(key)?.also { data -> runCatching { file.writeBytes(data) } }
            }
            val bitmap = bytes?.let(::decode) ?: return@withContext null
            bitmap.asImageBitmap().also { memory.put(key, it) }
        }
    }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "policy-avatars").apply { mkdirs() }

    private fun download(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_BYTES) return null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > TARGET_PX || bounds.outHeight / sample > TARGET_PX) {
            sample *= 2
        }
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
