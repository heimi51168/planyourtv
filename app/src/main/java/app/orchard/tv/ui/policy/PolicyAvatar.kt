package app.orchard.tv.ui.policy

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.orchard.tv.policy.PolicyAvatarStore
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The grant recipient's avatar, with the Doubao apple clip as the default avatar. The URL is
 * the same `userIcon` the phone app downloads for its gate. On the lock screen the apple runs
 * the blink sequence and then holds its eyes closed, so the waiting and rest screens both end
 * on the resting face.
 */
@Composable
fun PolicyAvatar(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
    resting: Boolean = false,
    /** Bump this when the screen wakes so the one-shot blink can play again. */
    blinkKey: Int = 0,
) {
    val context = LocalContext.current
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        image = if (url.isBlank()) null else PolicyAvatarStore.load(context, url)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Palette.FillStrong),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        when {
            // The lock screen always shows the sleeping apple: during rest it replaces a
            // custom icon, and while waiting it is the default avatar.
            resting || bitmap == null -> AppleSequenceAvatar(
                blinkKey = blinkKey,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val AvatarAssetDir = "policy/avatar"
private const val AvatarFrameCount = 73
private const val AvatarFrameMillis = 42L

/**
 * The Doubao apple clip is 720x720 at 24 fps. It ships as 256x256 WebP frames cropped around
 * the fruit: Android 5.0 has no AnimatedImageDrawable, and a video surface is not worth the
 * cost for a 74 dp avatar. Nine frames from the first closed hold are skipped so the second
 * blink finishes at three seconds; the sequence ends on that closed frame and holds it for
 * the whole lock screen, including the waiting state.
 */
private val AvatarSleepSequence: List<String> =
    List(AvatarFrameCount) { index ->
        "$AvatarAssetDir/frame_${index.toString().padStart(3, '0')}.webp"
    }
private val AvatarClosedFrame = AvatarSleepSequence.last()

/**
 * Plays the packaged apple frames once and holds the final closed one. The sequence restarts
 * when the lock screen resumes so the child sees the blink again. Decoding stays on the IO
 * dispatcher; the clip is short and each frame is displayed only once.
 */
@Composable
private fun AppleSequenceAvatar(
    blinkKey: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    val cache = remember { AvatarFrameCache(maxFrames = 4) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    // The drawn apple stands in only when the packaged frames are missing. Drawing it while
    // frame 0 decodes made the avatar change style right after the lock screen appeared.
    var unavailable by remember { mutableStateOf(false) }

    LaunchedEffect(blinkKey, reduced) {
        if (reduced) {
            val closed = cache.load(context, AvatarClosedFrame)
            if (closed != null) frame = closed else unavailable = true
            return@LaunchedEffect
        }
        val start = SystemClock.elapsedRealtime()
        AvatarSleepSequence.forEachIndexed { index, asset ->
            // Decode before waiting so a frame that finishes inside its interval does not
            // stretch the clip: the close lands at the same moment on every TV.
            val bitmap = cache.load(context, asset)
            if (bitmap == null && index == 0) {
                // The clip is unavailable, not merely slow. Hold the sleeping frame if it
                // exists so the fallback is one style for the whole pass.
                frame = cache.load(context, AvatarClosedFrame)
                unavailable = frame == null
                return@LaunchedEffect
            }
            val wait = start + index * AvatarFrameMillis - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            if (bitmap != null) {
                frame = bitmap
                unavailable = false
            }
        }
    }

    val bitmap = frame
    when {
        bitmap != null -> Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        // Only reached when the packaged frames are unavailable; keeps the lock screen
        // friendly on a partial build.
        unavailable -> AppleMascot(blinkKey = blinkKey, modifier = modifier)
    }
}

/**
 * A tiny LRU for the avatar frames. Decoding happens on the IO dispatcher; the map is
 * synchronized because a cancelled playback may still be finishing a decode.
 */
private class AvatarFrameCache(private val maxFrames: Int) {
    private val frames = LinkedHashMap<String, ImageBitmap>(maxFrames, 0.75f, true)

    suspend fun load(context: Context, asset: String): ImageBitmap? {
        synchronized(frames) { frames[asset] }?.let { return it }
        val decoded = withContext(Dispatchers.IO) {
            try {
                context.assets.open(asset).use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            } catch (_: IOException) {
                null
            }
        } ?: return null
        synchronized(frames) {
            frames[asset] = decoded
            while (frames.size > maxFrames) {
                val oldest = frames.keys.firstOrNull() ?: break
                frames.remove(oldest)
            }
        }
        return decoded
    }
}

/**
 * The vector fallback used only if the packaged frames cannot be decoded. It follows the
 * same open-close-open-close timeline and holds the eyes closed. TVs with "remove
 * animations" show the closed pose immediately.
 */
@Composable
private fun AppleMascot(
    blinkKey: Int,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val eyeOpen = remember { Animatable(1f) }
    LaunchedEffect(blinkKey, reduced) {
        eyeOpen.snapTo(1f)
        if (reduced) {
            eyeOpen.snapTo(0f)
            return@LaunchedEffect
        }
        // Open, slow blink, open, then close slowly at the 3-second mark and stay closed.
        delay(800)
        eyeOpen.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
        eyeOpen.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        delay(600)
        eyeOpen.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        // Leave a little air around the fruit so the stem and leaf stay inside the
        // circular avatar clip instead of being cut off by it.
        val u = size.minDimension * 0.9f
        val c = center
        val ink = Color(0xFF3B2314)
        val leafGreen = Color(0xFF66BB6A)
        val stemBrown = Color(0xFF7A4A21)
        val stemTop = Offset(c.x + 0.04f * u, c.y - 0.45f * u)

        drawCircle(
            color = Color(0xFFFF8A80).copy(alpha = 0.16f),
            radius = size.minDimension * 0.5f,
            center = c,
        )
        // Stem and leaf sit behind the body, so the body's top dip hides their roots.
        drawLine(
            color = stemBrown,
            start = Offset(c.x + 0.005f * u, c.y - 0.26f * u),
            end = stemTop,
            strokeWidth = u * 0.038f,
            cap = StrokeCap.Round,
        )
        drawPath(leafPath(stemTop, u), leafGreen)

        val body = applePath(c, u)
        drawPath(
            path = body,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFF7B72), Color(0xFFE63946)),
                startY = c.y - 0.46f * u,
                endY = c.y + 0.46f * u,
            ),
        )
        drawPath(body, Color(0xFF9E2233), style = Stroke(width = u * 0.024f))
        // A soft gloss on the upper-left quarter, kept inside the silhouette.
        clipPath(body) {
            drawOval(
                color = Color.White.copy(alpha = 0.20f),
                topLeft = Offset(c.x - 0.31f * u, c.y - 0.29f * u),
                size = Size(u * 0.23f, u * 0.40f),
            )
        }

        val blush = Color(0xFFFF8A80).copy(alpha = 0.48f)
        for (dx in listOf(-0.30f, 0.30f)) {
            drawCircle(
                color = blush,
                radius = u * 0.062f,
                center = Offset(c.x + dx * u, c.y + 0.11f * u),
            )
        }

        val open = eyeOpen.value
        val eyeY = c.y - 0.02f * u
        val eyeDx = u * 0.17f
        val eyeWidth = u * 0.23f
        val eyeHeight = u * 0.26f
        // The open eye is an oval whose height follows the lid; the closed eye fades in underneath.
        if (open > 0.01f) {
            val h = eyeHeight * open
            for (dx in listOf(-eyeDx, eyeDx)) {
                drawOval(
                    color = ink,
                    topLeft = Offset(c.x + dx - eyeWidth / 2f, eyeY - h / 2f),
                    size = Size(eyeWidth, h),
                )
                if (open > 0.72f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.95f),
                        radius = u * 0.050f,
                        center = Offset(
                            c.x + dx - eyeWidth * 0.20f,
                            eyeY - h * 0.22f,
                        ),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.75f),
                        radius = u * 0.022f,
                        center = Offset(
                            c.x + dx + eyeWidth * 0.22f,
                            eyeY + h * 0.18f,
                        ),
                    )
                }
            }
        }
        // Fade the closed arcs in only in the last part of the blink; otherwise the shrinking
        // open eye and the closed eye would both read as eyes at the same time.
        val lids = ((0.45f - open) / 0.45f).coerceIn(0f, 1f)
        if (lids > 0.01f) {
            for (dx in listOf(-eyeDx, eyeDx)) {
                drawArc(
                    color = ink.copy(alpha = lids),
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(c.x + dx - u * 0.14f, eyeY - u * 0.02f),
                    size = Size(u * 0.28f, u * 0.12f),
                    style = Stroke(width = u * 0.045f, cap = StrokeCap.Round),
                )
            }
        }
        drawArc(
            color = ink,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(c.x - 0.09f * u, c.y + 0.17f * u),
            size = Size(u * 0.18f, u * 0.11f),
            style = Stroke(width = u * 0.033f, cap = StrokeCap.Round),
        )
    }
}

/** A round apple silhouette with a dip at the top and a softly tapered bottom. */
private fun applePath(center: Offset, u: Float): Path {
    val x = center.x
    val y = center.y
    val path = Path()
    path.moveTo(x, y - 0.33f * u)
    path.cubicTo(
        x + 0.10f * u, y - 0.48f * u,
        x + 0.42f * u, y - 0.44f * u,
        x + 0.42f * u, y - 0.06f * u,
    )
    path.cubicTo(
        x + 0.42f * u, y + 0.25f * u,
        x + 0.23f * u, y + 0.44f * u,
        x, y + 0.44f * u,
    )
    path.cubicTo(
        x - 0.23f * u, y + 0.44f * u,
        x - 0.42f * u, y + 0.25f * u,
        x - 0.42f * u, y - 0.06f * u,
    )
    path.cubicTo(
        x - 0.42f * u, y - 0.44f * u,
        x - 0.10f * u, y - 0.48f * u,
        x, y - 0.33f * u,
    )
    path.close()
    return path
}

/** A small leaf pointing right from the stem, drawn behind the fruit. */
private fun leafPath(stemTop: Offset, u: Float): Path {
    val path = Path()
    path.moveTo(stemTop.x, stemTop.y)
    path.cubicTo(
        stemTop.x + 0.09f * u, stemTop.y - 0.10f * u,
        stemTop.x + 0.21f * u, stemTop.y - 0.08f * u,
        stemTop.x + 0.26f * u, stemTop.y,
    )
    path.cubicTo(
        stemTop.x + 0.18f * u, stemTop.y + 0.05f * u,
        stemTop.x + 0.07f * u, stemTop.y + 0.04f * u,
        stemTop.x, stemTop.y,
    )
    path.close()
    return path
}
