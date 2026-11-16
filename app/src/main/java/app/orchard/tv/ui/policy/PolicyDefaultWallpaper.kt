package app.orchard.tv.ui.policy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.orchard.tv.R
import app.orchard.tv.wallpaper.Images
import app.orchard.tv.wallpaper.Wallpapers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The child-mode default wallpaper, used wherever a policy film has not been chosen.
 *
 * The quiz backdrop and the child-mode desktop deliberately share this one picture. A fresh
 * install shows the bundled kids picture, not the owner's Home wallpaper and not Aerial's daily
 * picture, so child mode is visibly distinct from parent mode from the first launch. If the
 * bundled resource is missing or cannot be decoded, the old Aerial default (daily, then
 * `grad:aurora`) is the fallback rather than a black screen.
 *
 * Baking happens once per process on a background thread: decode, frost from the unshaded
 * picture (as Aerial does for its glass), then bake the Home shading. Both call sites receive
 * the same bitmaps, so the two surfaces cannot drift apart.
 */
internal data class PolicyDefaultWallpaper(
    val image: ImageBitmap,
    val frosted: ImageBitmap,
)

private val loadLock = Mutex()

@Volatile
private var defaultWallpaper: PolicyDefaultWallpaper? = null

/** The baked default, or null until [loadPolicyDefaultWallpaper] finishes. */
internal fun cachedPolicyDefaultWallpaper(): PolicyDefaultWallpaper? = defaultWallpaper

internal suspend fun loadPolicyDefaultWallpaper(context: Context): PolicyDefaultWallpaper? {
    defaultWallpaper?.let { return it }
    val appContext = context.applicationContext
    return loadLock.withLock {
        defaultWallpaper?.let { return@withLock it }
        withContext(Dispatchers.Default) {
            val width = Wallpapers.screenW.coerceAtLeast(2)
            val height = Wallpapers.screenH.coerceAtLeast(2)
            val picture = bundledOrDefault(appContext, width, height)
                ?: return@withContext null
            val frost = Images.frost(picture)
            val shaded = Images.shadeForHome(picture)
            PolicyDefaultWallpaper(shaded.asImageBitmap(), frost.asImageBitmap())
                .also { defaultWallpaper = it }
        }
    }
}

/**
 * The bundled kids picture, centre-cropped to the current screen. Aerial's old default stays as
 * a last resort so a damaged resource never leaves child mode without a background.
 */
private fun bundledOrDefault(context: Context, width: Int, height: Int): Bitmap? {
    val bundled = runCatching {
        BitmapFactory.decodeResource(context.resources, R.drawable.policy_kids_default)
    }.getOrNull()
    if (bundled != null) {
        return Images.cover(bundled, width, height).also {
            if (it !== bundled) bundled.recycle()
        }
    }
    // The daily item can exist while its file has not been downloaded yet (or was cleaned up),
    // so fall back on the picture itself, not just on resolve().
    return Wallpapers.resolve(Wallpapers.LATEST_DAILY)
        ?.let { Wallpapers.picture(it, width, height) }
        ?: Wallpapers.resolve("grad:aurora")
            ?.let { Wallpapers.picture(it, width, height) }
}

/** The shared default, loading in the background on first use. */
@Composable
internal fun rememberPolicyDefaultWallpaperState(): PolicyDefaultWallpaper? {
    val context = LocalContext.current.applicationContext
    val wallpaper by produceState(initialValue = cachedPolicyDefaultWallpaper(), context) {
        if (value == null) value = loadPolicyDefaultWallpaper(context)
    }
    return wallpaper
}

/**
 * The shared default picture as a Compose backdrop. Used when the quiz has no film, and under
 * the start page while the film's poster or first frame is still missing.
 */
@Composable
internal fun PolicyDefaultBackdrop(
    modifier: Modifier = Modifier,
    dim: Boolean = true,
) {
    val wallpaper = rememberPolicyDefaultWallpaperState()
    Box(modifier.fillMaxSize().background(Color.Black)) {
        wallpaper?.let { state ->
            Image(
                bitmap = state.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (dim) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.18f),
                            0.5f to Color.Black.copy(alpha = 0.06f),
                            1f to Color.Black.copy(alpha = 0.42f),
                        ),
                    ),
                )
            }
        }
    }
}
