package app.orchard.tv.policy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.DrawableRes
import app.orchard.tv.R
import com.planyourtv.policy.FamilyIds
import com.planyourtv.policy.FamilyMember
import java.io.File
import java.io.FileOutputStream

/**
 * App-private storage for uploaded member avatars.
 *
 * Only a square JPEG is kept: the client never controls EXIF, metadata, the stored size or the
 * path. Writes go to a temporary file and are renamed into place, so a power loss leaves either
 * the previous avatar or the new one, never a half file.
 */
object FamilyAvatarStore {
    private const val TAG = "FamilyAvatarStore"

    /** Wire cap for one raw upload. The normalized file is far smaller (256x256 JPEG). */
    const val MAX_UPLOAD_BYTES = 2 * 1024 * 1024L
    private const val MAX_DECODE_EDGE = 2048
    private const val MAX_SOURCE_EDGE = 16384
    private const val MAX_SAMPLE = 64
    private const val TARGET_PX = 256
    private const val JPEG_QUALITY = 88
    private const val AVATAR_ROOT = "avatars"

    /** Built-in family portraits; both Web and TV share these IDs. */
    const val DEFAULT_PRESET = "boy"
    val PRESETS: Set<String> = linkedSetOf(
        "dad", "mom", "grandpa", "grandma", "boy", "girl",
    )

    /**
     * The retired animal presets stay readable without rewriting stored documents. A member's
     * next profile save writes the canonical portrait ID.
     */
    private val LEGACY_PRESETS = mapOf(
        "rabbit" to "boy",
        "fox" to "girl",
        "bear" to "dad",
        "panda" to "mom",
    )

    /** Returns the canonical portrait ID, or null for an unknown value. */
    fun resolvePreset(preset: String?): String? {
        val key = preset?.trim()?.lowercase().orEmpty()
        return when {
            key in PRESETS -> key
            key in LEGACY_PRESETS -> LEGACY_PRESETS[key]
            else -> null
        }
    }

    fun canonicalPreset(preset: String?): String = resolvePreset(preset) ?: DEFAULT_PRESET

    @DrawableRes
    fun presetDrawable(preset: String?): Int = when (canonicalPreset(preset)) {
        "dad" -> R.drawable.family_avatar_dad
        "mom" -> R.drawable.family_avatar_mom
        "grandpa" -> R.drawable.family_avatar_grandpa
        "grandma" -> R.drawable.family_avatar_grandma
        "girl" -> R.drawable.family_avatar_girl
        else -> R.drawable.family_avatar_boy
    }

    /** Serves the same bundled bytes to the TV and the parent-center Web page. */
    fun presetBytes(context: Context, preset: String?): ByteArray? =
        runCatching {
            context.resources.openRawResource(presetDrawable(preset)).use { it.readBytes() }
        }.getOrNull()

    sealed interface ReceiveResult {
        data class Ok(val relativePath: String) : ReceiveResult

        data class Rejected(val code: String) : ReceiveResult
    }

    /**
     * Receives a raw request body. [declaredLength] is checked before allocating, and the real
     * stream is capped again so a lying Content-Length cannot exceed the buffer.
     */
    fun receive(
        context: Context,
        familyId: String,
        userId: String,
        input: java.io.InputStream,
        declaredLength: Long,
        now: Long = System.currentTimeMillis(),
    ): ReceiveResult {
        if (!FamilyIds.isValidFamilyId(familyId) || !FamilyIds.isValidUserId(userId)) {
            return ReceiveResult.Rejected("SUBJECT_NOT_FOUND")
        }
        if (declaredLength <= 0L) return ReceiveResult.Rejected("SUBJECT_AVATAR_EMPTY")
        if (declaredLength > MAX_UPLOAD_BYTES) {
            return ReceiveResult.Rejected("SUBJECT_AVATAR_TOO_LARGE")
        }
        val bytes = ByteArray(declaredLength.toInt())
        var offset = 0
        try {
            while (offset < bytes.size) {
                val read = input.read(bytes, offset, bytes.size - offset)
                if (read < 0) break
                offset += read
            }
        } catch (error: Throwable) {
            Log.w(TAG, "avatar read failed")
            return ReceiveResult.Rejected("SUBJECT_AVATAR_INVALID")
        }
        if (offset <= 0) return ReceiveResult.Rejected("SUBJECT_AVATAR_EMPTY")
        if (offset != bytes.size) return ReceiveResult.Rejected("SUBJECT_AVATAR_INVALID")
        if (!hasSupportedHeader(bytes)) return ReceiveResult.Rejected("SUBJECT_AVATAR_INVALID")

        val bitmap = decodeBounded(bytes) ?: return ReceiveResult.Rejected("SUBJECT_AVATAR_INVALID")
        val square = try {
            centerSquare(bitmap)
        } catch (error: Throwable) {
            bitmap.recycle()
            return ReceiveResult.Rejected("SUBJECT_AVATAR_INVALID")
        }
        if (square !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return try {
            val relative = writeAvatar(context, familyId, userId, square, now)
            if (relative == null) {
                ReceiveResult.Rejected("SUBJECT_AVATAR_WRITE_FAILED")
            } else {
                FamilyHub.setAvatarImage(userId, relative)
                if (FamilyHub.state.value.members.none { it.avatarImage == relative }) {
                    // The roster still points at the old file; drop only the unregistered copy.
                    deleteFile(context, relative)
                    ReceiveResult.Rejected("SUBJECT_UPDATE_FAILED")
                } else {
                    deleteOtherFiles(context, familyId, userId, relative.substringAfterLast('/'))
                    ReceiveResult.Ok(relative)
                }
            }
        } finally {
            if (!square.isRecycled) square.recycle()
        }
    }

    /** Keeps the photo private to the app; there is no content:// or external-storage copy. */
    fun read(context: Context, member: FamilyMember): ByteArray? {
        val relative = member.avatarImage ?: return null
        if (!FamilyIds.isSafeAvatarImage(relative)) return null
        val file = resolve(context, relative) ?: return null
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun deleteForMember(context: Context, member: FamilyMember): Boolean {
        val relative = member.avatarImage ?: return true
        // Clear the roster first: a file delete that fails only leaves an orphan, while the
        // reverse order would leave a member pointing at a photo that is already gone.
        if (FamilyHub.clearAvatarImage(member.userId) != null) return false
        return deleteFile(context, relative)
    }

    /** Called when a member is removed; keeps only this user's files. */
    fun deleteAllForMember(context: Context, familyId: String, userId: String): Boolean {
        if (!FamilyIds.isValidFamilyId(familyId) || !FamilyIds.isValidUserId(userId)) return false
        val dir = File(avatarRoot(context), familyId)
        if (!dir.isDirectory) return true
        return dir.listFiles()
            ?.filter { it.name.startsWith("$userId-") }
            ?.all { it.delete() }
            ?: true
    }

    /**
     * Accepts only the documented still-image types. The magic check runs before decoding so a
     * GIF, SVG or arbitrary container never reaches BitmapFactory, and keeps the error stable.
     */
    private fun hasSupportedHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val jpeg = bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        if (jpeg) return true
        val png = bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
        if (png) return true
        return bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
    }

    private fun decodeBounded(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        // Reject absurd declared dimensions before decoding: a small compressed upload must not
        // turn into an allocation the TV cannot serve just because its header claims a huge image.
        if (width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE) return null
        var sample = 1
        while (width / sample > MAX_DECODE_EDGE || height / sample > MAX_DECODE_EDGE) {
            if (sample >= MAX_SAMPLE) break
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
            .getOrNull()
    }

    private fun centerSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) throw IllegalArgumentException("empty bitmap")
        val side = minOf(width, height)
        if (width == side && height == side && width <= TARGET_PX) return bitmap
        val cropped = if (width == side && height == side) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, (width - side) / 2, (height - side) / 2, side, side)
        }
        if (cropped.width <= TARGET_PX) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, TARGET_PX, TARGET_PX, true)
        if (scaled !== cropped && cropped !== bitmap) cropped.recycle()
        return scaled
    }

    private fun writeAvatar(
        context: Context,
        familyId: String,
        userId: String,
        bitmap: Bitmap,
        now: Long,
    ): String? {
        val dir = File(avatarRoot(context), familyId)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val name = "$userId-$now.jpg"
        val target = File(dir, name)
        val temp = File(dir, "$name.tmp")
        val written = runCatching {
            FileOutputStream(temp).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    return@use false
                }
                output.flush()
                output.fd.sync()
                true
            }
        }.getOrDefault(false)
        if (!written) {
            temp.delete()
            return null
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            return null
        }
        // The previous file is removed only after FamilyHub records this one; see receive().
        val relative = "$AVATAR_ROOT/$familyId/$name"
        return relative.takeIf { FamilyIds.isSafeAvatarImage(it) }
    }

    private fun deleteOtherFiles(
        context: Context,
        familyId: String,
        userId: String,
        keepName: String,
    ) {
        val dir = File(avatarRoot(context), familyId)
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$userId-") && it.name != keepName }
            ?.forEach { it.delete() }
    }

    private fun deleteFile(context: Context, relative: String): Boolean {
        val file = resolve(context, relative) ?: return false
        return !file.exists() || file.delete()
    }

    private fun resolve(context: Context, relative: String): File? {
        if (!FamilyIds.isSafeAvatarImage(relative)) return null
        val base = File(context.filesDir, AVATAR_ROOT).canonicalFile
        val file = File(context.filesDir, relative).canonicalFile
        return file.takeIf { it.path.startsWith(base.path + File.separator) }
    }

    private fun avatarRoot(context: Context): File {
        val root = File(context.filesDir, AVATAR_ROOT)
        if (!root.isDirectory) root.mkdirs()
        return root
    }
}
