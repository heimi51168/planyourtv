package app.orchard.tv.policy.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.createBitmap
import app.orchard.tv.wallpaper.Images
import java.io.File
import kotlin.math.abs

/**
 * The acceptance contract for policy pictures.
 *
 * A local upload may be JPEG, PNG or WebP. It is decoded with bounds checks first, turned
 * upright, centre-cropped to the TV's 16:9 frame and flattened onto white for transparency, so
 * the stored file is always a single 1920x1080 JPEG without EXIF, GPS or an original filename.
 * Cloud pictures are prepared by the upload tool and are only inspected, never re-encoded: a
 * re-encode would change the SHA-256 the manifest publishes.
 *
 * Nothing here trusts an extension or the manifest alone. The magic bytes, dimensions, pixel
 * count and 16:9 frame are all checked before a picture reaches the child wallpaper.
 */
object PolicyImageValidation {
    /** Raw uploads are allowed to be larger than the stored, normalised picture. */
    const val MAX_UPLOAD_BYTES = 12L * 1024 * 1024
    const val MAX_STORED_BYTES = 4L * 1024 * 1024
    const val WIDTH = 1920
    const val HEIGHT = 1080
    const val MAX_PIXELS = 40_000_000L
    const val MAX_SOURCE_DIMENSION = 8192
    const val JPEG_QUALITY = 88
    private const val FALLBACK_QUALITY = 80
    private const val ASPECT_TOLERANCE = 0.03
    private const val HEADER_BYTES = 16
    private const val TAG = "PolicyImageValidation"

    sealed class Result {
        data class Ok(val width: Int, val height: Int, val bytes: Long) : Result()
        data class Rejected(val code: String) : Result()
    }

    /**
     * Normalises a local upload into [output] as a 1920x1080 JPEG. The caller owns [output] on
     * success; a partial file is removed on failure.
     */
    fun normalize(input: File, output: File): Result {
        val length = input.length()
        if (length <= 0L) return Result.Rejected("INTRO_IMAGE_INVALID")
        if (length > MAX_UPLOAD_BYTES) return Result.Rejected("INTRO_IMAGE_TOO_LARGE")
        if (!sniffImage(input)) return Result.Rejected("INTRO_IMAGE_BAD_FORMAT")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(input.path, bounds) }
            .getOrElse {
                Log.w(TAG, "image bounds could not be read: ${it.javaClass.simpleName}")
                return Result.Rejected("INTRO_IMAGE_INVALID")
            }
        val sourceWidth = bounds.outWidth
        val sourceHeight = bounds.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) return Result.Rejected("INTRO_IMAGE_INVALID")
        if (sourceWidth > MAX_SOURCE_DIMENSION || sourceHeight > MAX_SOURCE_DIMENSION) {
            return Result.Rejected("INTRO_IMAGE_INVALID")
        }
        if (sourceWidth.toLong() * sourceHeight > MAX_PIXELS) {
            return Result.Rejected("INTRO_IMAGE_INVALID")
        }

        val cover = runCatching { Images.decodeCover(input, WIDTH, HEIGHT) }
            .getOrNull()
            ?: return Result.Rejected("INTRO_IMAGE_INVALID")
        var flat: Bitmap? = null
        return try {
            // A transparent PNG/WebP would otherwise turn black in the JPEG. The child screen
            // always has an opaque wallpaper, so white is the least surprising backdrop.
            val bitmap = createBitmap(WIDTH, HEIGHT)
            flat = bitmap
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawBitmap(cover, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            if (!writeJpeg(bitmap, output, JPEG_QUALITY) || output.length() > MAX_STORED_BYTES) {
                if (!writeJpeg(bitmap, output, FALLBACK_QUALITY) || output.length() > MAX_STORED_BYTES) {
                    return Result.Rejected("INTRO_IMAGE_TOO_LARGE")
                }
            }
            if (!isJpeg(output)) return Result.Rejected("INTRO_IMAGE_INVALID")
            val storedBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeFile returns null in bounds-only mode even on success; the options carry the size.
            runCatching { BitmapFactory.decodeFile(output.path, storedBounds) }
                .getOrElse { return Result.Rejected("INTRO_IMAGE_INVALID") }
            if (storedBounds.outWidth != WIDTH || storedBounds.outHeight != HEIGHT) {
                return Result.Rejected("INTRO_IMAGE_INVALID")
            }
            Result.Ok(WIDTH, HEIGHT, output.length())
        } catch (error: Exception) {
            Log.w(TAG, "image normalise failed: ${error.javaClass.simpleName}")
            Result.Rejected("INTRO_IMAGE_INVALID")
        } finally {
            cover.recycle()
            flat?.recycle()
            if (output.exists() && output.length() > MAX_STORED_BYTES) output.delete()
        }
    }

    /**
     * Checks a cloud picture without changing its bytes. The manifest's SHA-256 is checked
     * against exactly this file, so this never re-encodes.
     */
    fun inspectStored(file: File): Result {
        val length = file.length()
        if (length <= 0L) return Result.Rejected("INTRO_IMAGE_INVALID")
        if (length > MAX_STORED_BYTES) return Result.Rejected("INTRO_IMAGE_TOO_LARGE")
        if (!isJpeg(file)) return Result.Rejected("INTRO_IMAGE_INVALID")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.path, bounds) }
            .getOrElse {
                Log.w(TAG, "stored image bounds could not be read: ${it.javaClass.simpleName}")
                return Result.Rejected("INTRO_IMAGE_INVALID")
            }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width !in 1..WIDTH || height !in 1..HEIGHT) {
            return Result.Rejected("INTRO_IMAGE_INVALID")
        }
        val ratio = width.toDouble() / height
        if (abs(ratio - 16.0 / 9.0) > ASPECT_TOLERANCE) {
            return Result.Rejected("INTRO_IMAGE_BAD_RATIO")
        }

        val sample = runCatching {
            BitmapFactory.decodeFile(
                file.path,
                BitmapFactory.Options().apply { inSampleSize = 4 },
            )
        }.getOrNull() ?: return Result.Rejected("INTRO_IMAGE_INVALID")
        sample.recycle()
        return Result.Ok(width, height, length)
    }

    /** True when [file] starts with a JPEG, PNG or WebP signature. */
    fun sniffImage(file: File): Boolean = sniffImage(readHeader(file))

    /** True when [bytes] start with a JPEG, PNG or WebP signature. */
    fun sniffImage(bytes: ByteArray): Boolean = when {
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> true
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte() -> true
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte() -> true
        else -> false
    }

    /** Both JPEG markers, without reading the whole picture into memory. */
    fun isJpeg(file: File): Boolean {
        val length = file.length()
        if (length < 4) return false
        return runCatching {
            val head = java.io.RandomAccessFile(file, "r").use { raf ->
                ByteArray(2).also { raf.readFully(it) }
            }
            if (head[0] != 0xFF.toByte() || head[1] != 0xD8.toByte()) return false
            val tail = java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(length - 2)
                ByteArray(2).also { raf.readFully(it) }
            }
            tail[0] == 0xFF.toByte() && tail[1] == 0xD9.toByte()
        }.getOrDefault(false)
    }

    private fun writeJpeg(bitmap: Bitmap, output: File, quality: Int): Boolean {
        output.delete()
        output.parentFile?.mkdirs()
        return runCatching {
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        }.getOrDefault(false)
    }

    /** Only the first bytes; a file is never read whole just to identify it. */
    private fun readHeader(file: File): ByteArray = runCatching {
        file.inputStream().use { input ->
            val bytes = ByteArray(HEADER_BYTES)
            val read = input.read(bytes)
            if (read <= 0) ByteArray(0) else bytes.copyOf(read)
        }
    }.getOrDefault(ByteArray(0))
}
