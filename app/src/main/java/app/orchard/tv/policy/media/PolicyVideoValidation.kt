package app.orchard.tv.policy.media

import android.graphics.Bitmap
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import app.orchard.tv.wallpaper.Images
import java.io.File
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The decode contract for policy videos.
 *
 * Official cloud media deliberately stays H.264 + AAC-LC (or no audio track),
 * 16:9, at most 1080p30 and 15 seconds: that is the format every Android 5.0
 * TV in the compatibility floor can play. Local uploads follow Aerial's owner
 * video acceptance (MP4/MOV, any codec) but are checked against this TV's
 * decoders, because the policy library plays them in the same process. A local
 * upload is accepted only when a regular decoder exists for its actual codec,
 * size and frame rate; the TV never transcodes. HEVC without a decoder is
 * rejected with `INTRO_VIDEO_HEVC`, other codecs with
 * `INTRO_VIDEO_UNSUPPORTED_CODEC`.
 *
 * Rejection codes stay in the `INTRO_VIDEO_*` namespace because the old
 * parent page already maps them. Callers that need a library-level error wrap
 * them as `MEDIA_BAD_FORMAT` instead of showing the raw code.
 */
object PolicyVideoValidation {
    /** Local uploads stay small; official films keep their source bitrate. */
    const val MAX_FILE_BYTES = 4L * 1024 * 1024
    const val MAX_OFFICIAL_FILE_BYTES = 20L * 1024 * 1024
    const val MAX_DURATION_MS = 15_000L
    const val MAX_WIDTH = 1920
    const val MAX_HEIGHT = 1080
    const val MAX_FPS = 30
    const val POSTER_WIDTH = 960
    const val POSTER_HEIGHT = 540
    const val POSTER_AT_MS = 2_000L
    const val MAX_POSTER_BYTES = 512L * 1024

    /** The film may be 16:9 within roughly 1.5%, so 1920x1088 still passes. */
    private const val ASPECT_TOLERANCE = 0.03
    private const val TAG = "PolicyVideoValidation"

    /** Official media stays H.264; local uploads accept any video codec this TV can decode. */
    private val OFFICIAL_VIDEO_MIMES = setOf("video/avc")

    data class Info(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val fps: Int,
        val hasAudio: Boolean,
    )

    sealed class Result {
        data class Ok(val info: Info) : Result()
        data class Rejected(val code: String) : Result()
    }

    /** Local uploads may use any codec this TV can decode at the actual size and rate. */
    fun validateLocalUpload(file: File): Result = validate(file, localUpload = true)

    /** Official cloud media stays H.264-only so Android 5.0 TVs remain supported. */
    fun validateOfficialDownload(file: File): Result = validate(file, localUpload = false)

    private fun validate(file: File, localUpload: Boolean): Result {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") {
                return Result.Rejected("INTRO_VIDEO_INVALID")
            }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return Result.Rejected("INTRO_VIDEO_INVALID")
            if (duration <= 0L) return Result.Rejected("INTRO_VIDEO_INVALID")
            if (duration > MAX_DURATION_MS) return Result.Rejected("INTRO_VIDEO_BAD_DURATION")

            var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            if (width <= 0 || height <= 0) return Result.Rejected("INTRO_VIDEO_INVALID")
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation % 180 != 0) {
                val turned = width
                width = height
                height = turned
            }
            if (width > MAX_WIDTH || height > MAX_HEIGHT) {
                return Result.Rejected("INTRO_VIDEO_BAD_RESOLUTION")
            }
            val ratio = width.toDouble() / height
            if (abs(ratio - 16.0 / 9.0) > ASPECT_TOLERANCE) {
                return Result.Rejected("INTRO_VIDEO_BAD_RATIO")
            }

            var fps = 0
            var hasAudio = false
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                var videoTracks = 0
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    when {
                        mime.startsWith("video/") -> {
                            videoTracks += 1
                            if (!localUpload && mime !in OFFICIAL_VIDEO_MIMES) {
                                return Result.Rejected("INTRO_VIDEO_BAD_CODEC")
                            }
                            val trackFps = frameRateOf(format)
                            if (trackFps != null) {
                                if (trackFps > MAX_FPS) return Result.Rejected("INTRO_VIDEO_BAD_FPS")
                                fps = trackFps
                            }
                            if (localUpload && mime != "video/avc") {
                                val trackWidth = runCatching {
                                    format.getInteger(MediaFormat.KEY_WIDTH)
                                }.getOrDefault(width)
                                val trackHeight = runCatching {
                                    format.getInteger(MediaFormat.KEY_HEIGHT)
                                }.getOrDefault(height)
                                val hevcProfile = if (isHevc(mime)) {
                                    runCatching {
                                        format.getInteger(MediaFormat.KEY_PROFILE)
                                    }.getOrNull()
                                } else {
                                    null
                                }
                                if (!hasDecoder(
                                        mime = mime,
                                        width = trackWidth,
                                        height = trackHeight,
                                        fps = (trackFps ?: MAX_FPS).takeIf { it > 0 } ?: MAX_FPS,
                                        hevcProfile = hevcProfile,
                                    )) {
                                    return Result.Rejected(
                                        if (isHevc(mime)) {
                                            "INTRO_VIDEO_HEVC"
                                        } else {
                                            "INTRO_VIDEO_UNSUPPORTED_CODEC"
                                        },
                                    )
                                }
                            }
                        }
                        mime.startsWith("audio/") -> {
                            // Aerial accepts any owner-video audio track. ExoPlayer selects a
                            // supported one and the lock wallpaper disables audio entirely;
                            // official media keeps AAC-LC for the Android 5.0 floor.
                            if (!localUpload) {
                                if (mime != "audio/mp4a-latm") {
                                    return Result.Rejected("INTRO_VIDEO_BAD_CODEC")
                                }
                                val profile = runCatching {
                                    format.getInteger(MediaFormat.KEY_AAC_PROFILE)
                                }.getOrNull()
                                if (profile != null &&
                                    profile != MediaCodecInfo.CodecProfileLevel.AACObjectLC
                                ) {
                                    return Result.Rejected("INTRO_VIDEO_BAD_CODEC")
                                }
                            }
                            hasAudio = true
                        }
                    }
                }
                if (videoTracks != 1) return Result.Rejected("INTRO_VIDEO_INVALID")
            } finally {
                extractor.release()
            }
            return Result.Ok(Info(duration, width, height, fps, hasAudio))
        } catch (error: Exception) {
            Log.w(TAG, "could not read a policy video", error)
            return Result.Rejected("INTRO_VIDEO_INVALID")
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun isHevc(mime: String): Boolean = mime == "video/hevc" || mime == "video/h265"

    private fun frameRateOf(format: MediaFormat): Int? =
        runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE) }.getOrNull()
            ?: runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE).roundToInt() }.getOrNull()

    /**
     * Whether a regular (non-encoder) codec on this TV can decode [mime] at the
     * source size and frame rate. HEVC Main10 is rejected where the decoder
     * only advertises Main; other profiles are left to the codec itself.
     */
    private fun hasDecoder(
        mime: String,
        width: Int,
        height: Int,
        fps: Int,
        hevcProfile: Int?,
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codec ->
            if (codec.isEncoder) return@any false
            val type = codec.supportedTypes.firstOrNull { it.equals(mime, ignoreCase = true) }
                ?: return@any false
            runCatching {
                val capabilities = codec.getCapabilitiesForType(type)
                val video = capabilities.videoCapabilities ?: return@runCatching false
                val profiles = capabilities.profileLevels
                if (hevcProfile != null && hevcProfile > 0 && profiles != null &&
                    profiles.isNotEmpty() && profiles.none { it.profile == hevcProfile }
                ) {
                    return@runCatching false
                }
                if (fps > 0) {
                    video.areSizeAndRateSupported(width, height, fps.toDouble())
                } else {
                    video.isSizeSupported(width, height)
                }
            }.getOrDefault(false)
        }
    }

    /**
     * Writes one representative cover frame. Official media is prepared by the
     * upload script, but a local upload needs its own cover for the parent page.
     * Returns true only when a decodable JPEG was written.
     */
    fun writePoster(video: File, atMs: Long, out: OutputStream): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: return false
            val stampMs = atMs.coerceIn(0L, (duration - 1L).coerceAtLeast(0L))
            val frame = runCatching {
                retriever.getFrameAtTime(
                    stampMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                )
            }.getOrNull() ?: runCatching {
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull() ?: return false
            val cover: Bitmap = Images.cover(frame, POSTER_WIDTH, POSTER_HEIGHT)
            try {
                cover.compress(Bitmap.CompressFormat.JPEG, 85, out)
            } finally {
                if (cover !== frame) cover.recycle()
                frame.recycle()
            }
        } catch (error: Exception) {
            Log.w(TAG, "could not keep a policy video poster", error)
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun isJpeg(file: File): Boolean = isJpeg(file.readBytes())

    fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[bytes.size - 2] == 0xFF.toByte() &&
            bytes[bytes.size - 1] == 0xD9.toByte()

    fun isSha256(value: String): Boolean = value.matches(Regex("[a-f0-9]{64}"))
}
