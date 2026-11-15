package app.orchard.tv.ui.policy

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
import app.orchard.tv.wallpaper.Images
import app.orchard.tv.wallpaper.Wallpapers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Aerial's own default wallpaper, used wherever a policy film has not been chosen.
 *
 * The quiz backdrop and the child-mode desktop deliberately share this one picture: an install
 * that has not configured a film looks exactly like Aerial's stock Home, whichever surface it
 * opens. It follows Aerial's "latest daily" default and its `grad:aurora` fallback, rather than
 * the wallpaper the owner later selected for Home, so changing the Home picture never changes
 * the child-mode background by surprise.
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

internal suspend fun loadPolicyDefaultWallpaper(): PolicyDefaultWallpaper? {
    defaultWallpaper?.let { return it }
    return loadLock.withLock {
        defaultWallpaper?.let { return@withLock it }
        withContext(Dispatchers.Default) {
            val width = Wallpapers.screenW.coerceAtLeast(2)
            val height = Wallpapers.screenH.coerceAtLeast(2)
            // The daily item can exist while its file has not been downloaded yet (or was
            // cleaned up), so fall back on the picture itself, not just on resolve().
            val picture = Wallpapers.resolve(Wallpapers.LATEST_DAILY)
                ?.let { Wallpapers.picture(it, width, height) }
                ?: Wallpapers.resolve("grad:aurora")
                    ?.let { Wallpapers.picture(it, width, height) }
            if (picture == null) return@withContext null
            val frost = Images.frost(picture)
            val shaded = Images.shadeForHome(picture)
            PolicyDefaultWallpaper(shaded.asImageBitmap(), frost.asImageBitmap())
                .also { defaultWallpaper = it }
        }
    }
}

/** The shared default, loading in the background on first use. */
@Composable
internal fun rememberPolicyDefaultWallpaperState(): PolicyDefaultWallpaper? {
    val wallpaper by produceState(initialValue = cachedPolicyDefaultWallpaper()) {
        if (value == null) value = loadPolicyDefaultWallpaper()
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
