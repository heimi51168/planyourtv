package app.orchard.tv.ui.policy

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.orchard.tv.policy.media.PolicyMediaKind
import app.orchard.tv.policy.media.PolicyVideoLibrary
import app.orchard.tv.policy.media.PolicyVideoSource
import app.orchard.tv.ui.policy.lockdesktop.PolicyLockFilm
import app.orchard.tv.wallpaper.Images
import app.orchard.tv.wallpaper.Wallpapers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The self-discipline wallpaper, used by the lock screen and by the active child session's Home.
 * Quiz pages deliberately keep their plain dark background: a moving picture there would pull
 * attention away from the questions. The lock role owns the picture: its poster is the still
 * frame the lock video was taken from, so the child surfaces show the same wallpaper even while
 * the video is paused or not yet rendering.
 *
 * The fallback is [PolicyDefaultWallpaper], never the owner's Home wallpaper. A missing or
 * unreadable poster therefore leaves child mode on the bundled kids picture instead of leaking the
 * parent wallpaper into the child's screens.
 *
 * The poster is decoded and shaded once per process. [PolicyChildWallpaper] can be returned before
 * that finishes ([loading]); the lock screen can draw the shared default meanwhile, while
 * [PolicyHomeGate] keeps Home back until the frame is ready.
 */
internal data class PolicyChildWallpaper(
    val lockId: String?,
    /** The loop the lock screen plays; null when the role's file is missing or unreadable. */
    val source: PolicyVideoSource?,
    val image: ImageBitmap?,
    val frosted: ImageBitmap?,
    val loading: Boolean,
)

/**
 * Wallpaper for Home while a child session is active. Null means Aerial's own wallpaper.
 *
 * [PolicyHomeFilm] draws the lock role's video under Home; [cover] is how far its first frame
 * has replaced the poster.
 */
internal class PolicyHomeWallpaper(
    val image: ImageBitmap?,
    val frosted: ImageBitmap?,
    internal val source: PolicyVideoSource?,
    internal val filmCover: Animatable<Float, *>,
) {
    val cover: () -> Float = { filmCover.value }
}

/** The child wallpaper's film under Home. [active] false lets the screensaver's decoder have it. */
@Composable
internal fun PolicyHomeFilm(wallpaper: PolicyHomeWallpaper, active: Boolean) {
    // A picture is drawn by HomeBackground; only a video may reach the player.
    val source = wallpaper.source?.takeIf { it.kind == PolicyMediaKind.VIDEO } ?: return
    PolicyLockFilm(source = source, cover = wallpaper.filmCover, active = active)
}

internal val LocalPolicyHomeWallpaper = compositionLocalOf<PolicyHomeWallpaper?> { null }

private data class CachedChildWallpaper(
    val lockId: String,
    val source: PolicyVideoSource?,
    val image: ImageBitmap?,
    val frosted: ImageBitmap?,
)

private val loadLock = Mutex()

@Volatile
private var cachedChildWallpaper: CachedChildWallpaper? = null

/** Loads the lock role's poster, or the shared default when the role or its file is missing. */
@Composable
internal fun rememberPolicyChildWallpaperState(): PolicyChildWallpaper {
    val context = LocalContext.current.applicationContext
    // The parent center is an overlay above the lock screen: choosing a wallpaper there must
    // update the picture underneath it without waiting for the next lock entry.
    var roleRevision by remember(context) { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("quiz-intro", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "lock_id") roleRevision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val initial = remember(context, roleRevision) { initialChildWallpaper(context) }
    val state by produceState(initialValue = initial, context, roleRevision) {
        // produceState keeps its state across key changes; show the new role's initial picture
        // (often the bundled default) before any slower decode replaces it.
        value = initial
        if (!initial.loading) return@produceState
        value = loadPolicyChildWallpaper(context, initial)
    }
    return state
}

private fun initialChildWallpaper(context: Context): PolicyChildWallpaper {
    val lockId = runCatching { PolicyVideoLibrary.lockRoleId(context) }.getOrNull()
    if (lockId == null) {
        val fallback = cachedPolicyDefaultWallpaper()
        // A fresh install has no lock role: the bundled default still has to load, and Home waits
        // for it instead of showing a blank policy backdrop.
        return PolicyChildWallpaper(
            null,
            null,
            fallback?.image,
            fallback?.frosted,
            loading = fallback == null,
        )
    }
    val hit = cachedChildWallpaper?.takeIf { it.lockId == lockId }
    if (hit != null) {
        return PolicyChildWallpaper(lockId, hit.source, hit.image, hit.frosted, loading = false)
    }
    val fallback = cachedPolicyDefaultWallpaper()
    return PolicyChildWallpaper(lockId, null, fallback?.image, fallback?.frosted, loading = true)
}

private suspend fun loadPolicyChildWallpaper(
    context: Context,
    initial: PolicyChildWallpaper,
): PolicyChildWallpaper {
    val lockId = initial.lockId
    if (lockId == null) {
        val fallback = loadPolicyDefaultWallpaper(context)
        return PolicyChildWallpaper(
            null,
            null,
            fallback?.image ?: initial.image,
            fallback?.frosted ?: initial.frosted,
            loading = false,
        )
    }
    cachedChildWallpaper?.takeIf { it.lockId == lockId }?.let {
        return PolicyChildWallpaper(lockId, it.source, it.image, it.frosted, loading = false)
    }
    val loaded = loadLock.withLock {
        cachedChildWallpaper?.takeIf { it.lockId == lockId } ?: withContext(Dispatchers.IO) {
            // The lock role can be selected while its file is still unavailable (deleted outside
            // the app). In that case the default wallpaper is the child backdrop, not Home's.
            val source = PolicyVideoLibrary.lockSource(context)
            val poster = source
                ?.poster
                ?.takeIf { it.isFile && it.length() > 0 }
            val decoded = poster?.let {
                runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull()
            }
            val wallpaper = if (decoded == null) {
                val fallback = loadPolicyDefaultWallpaper(context)
                CachedChildWallpaper(lockId, source, fallback?.image, fallback?.frosted)
            } else {
                val frosted = runCatching { Images.frost(decoded) }.getOrNull()
                // Shade after frosting, as the shared default does; Home expects the same shading
                // on this picture as on any other static wallpaper.
                val shaded = runCatching { Images.shadeForHome(decoded) }.getOrNull() ?: decoded
                CachedChildWallpaper(lockId, source, shaded.asImageBitmap(), frosted?.asImageBitmap())
            }
            wallpaper.also { cachedChildWallpaper = it }
        }
    }
    return PolicyChildWallpaper(lockId, loaded.source, loaded.image, loaded.frosted, loading = false)
}

/**
 * The background for the short "preparing desktop" wait. A child transition keeps the child
 * wallpaper; a parent unlock shows the owner's wallpaper, so leaving the session does not flash
 * black or the child's picture.
 */
@Composable
internal fun PolicyPreparingBackdrop(
    wallpaper: PolicyChildWallpaper?,
    childActive: Boolean,
    modifier: Modifier = Modifier,
) {
    if (childActive) {
        val fallback = rememberPolicyDefaultWallpaperState()
        PreparingBackdrop(wallpaper?.image ?: fallback?.image, modifier)
        return
    }
    PreparingBackdrop(Wallpapers.backdrop, modifier)
}

@Composable
private fun PreparingBackdrop(image: ImageBitmap?, modifier: Modifier) {
    Box(modifier.fillMaxSize().background(PolicyBackdropBase)) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(PolicyPreparingScrim))
    }
}

private val PolicyBackdropBase = Color(0xFF03060C)

private val PolicyPreparingScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.26f),
    0.5f to Color.Black.copy(alpha = 0.16f),
    1f to Color.Black.copy(alpha = 0.42f),
)
