package app.orchard.tv.ui.policy.lockdesktop

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.ui.home.HomeBackground
import app.orchard.tv.ui.home.HomeVideo
import app.orchard.tv.ui.home.VideoWallpaper
import app.orchard.tv.ui.policy.rememberPolicyDefaultWallpaperState
import app.orchard.tv.wallpaper.Wallpapers

/**
 * The owner's own video wallpaper behind the lock screen, played by the same [VideoWallpaper] as
 * Home so both show one wallpaper. It sits under the menu rather than in [LockDesktopBackdrop], so
 * opening the PIN/duration panel over the menu does not restart the film.
 *
 * [cover] is how far the film shows through: it rises once the player has a frame, and the
 * backdrop's picture gives way to the film, as Home's does. Nothing is composed for a still
 * wallpaper.
 */
@Composable
fun LockDesktopFilm(cover: Animatable<Float, *>) {
    if (!Wallpapers.isVideo()) return
    // Anew for each choice, so another of the owner's videos does not keep the last one's player.
    key(Wallpapers.selectedId) {
        val home = remember { HomeVideo() }
        VideoWallpaper(
            moving = true,
            // The lock screen is in front for as long as this is composed: the decoder stays.
            keep = true,
            // No idle wait and no screensaver carry here: a wallpaper on the lock screen moves.
            hold = true,
            home = home,
            standIn = false,
            lastInputAt = 0L,
            cover = cover,
        )
    }
}

/**
 * The lock screen's wallpaper, read from the same [Wallpapers] state as Home. P0 draws the
 * chosen picture (photo, daily rotation, or the frame a moving wallpaper stopped on); Auto and
 * an HDMI input fall back to the neutral gradient rather than borrowing another app's artwork.
 * No live blur and no full-screen glass: a still picture is one full-screen draw, and focus
 * movement on the dock does not repaint it. A video wallpaper is the film [LockDesktopFilm]
 * draws under this; its picture stays until the film's first frame, then fades out.
 *
 * [dockSlots] draws Aerial's own glass plate under the dock row, with Home's padding and corners;
 * zero hides it for the PIN/duration overlay, where the dock is gone. The plate is baked from the
 * same frosted picture Home uses, so the dock reads as Aerial's first row rather than floating.
 *
 * [scroll], [collapse] and [lift] are the dock grid's own, so the plate rides up and fades into
 * the grid's glass exactly as it does when Home scrolls down to its rows.
 *
 * [policyDefault] replaces the Home wallpaper with Aerial's stock default (the V30 behavior),
 * while keeping the same plate, frost and veil; [cover] then belongs to a policy film.
 */
@Composable
fun LockDesktopBackdrop(
    dockSlots: Int = 0,
    modifier: Modifier = Modifier,
    /** How far the policy film shows through ([PolicyLockFilm]); 0 for the default picture. */
    cover: () -> Float = { 0f },
    /** The dock grid's scroll in px, and how far it scrolls before the shelf has given way. */
    scroll: () -> Float = { 0f },
    collapse: Float = 1f,
    lift: () -> Float = { 0f },
    /**
     * Child mode uses Aerial's stock default picture instead of following the owner's Home
     * wallpaper, so an unconfigured TV looks exactly like stock Aerial and selecting a Home
     * picture never changes the child-mode background.
     */
    policyDefault: Boolean = false,
) {
    val columns = AerialSettings.homeColumns
    val plateEnabled = AerialSettings.homePlate
    val peek = AerialSettings.peekNextRow
    val policyWallpaper = if (policyDefault) rememberPolicyDefaultWallpaperState() else null
    val backdrop = if (policyDefault) policyWallpaper?.image else Wallpapers.backdrop
    val frosted = if (policyDefault) {
        policyWallpaper?.frosted
    } else {
        Wallpapers.frosted.takeIf { Wallpapers.backdrop != null }
    }
    val density = LocalDensity.current
    val plate = remember(dockSlots, columns, plateEnabled, peek, density) {
        if (dockSlots <= 0 || !plateEnabled) return@remember null
        val metrics = lockDockMetrics(columns, dockSlots)
        with(density) {
            Rect(
                metrics.plateLeft.toPx(),
                metrics.plateTop.toPx(),
                metrics.plateRight.toPx(),
                metrics.shelfBottom.toPx(),
            )
        }
    }
    Box(modifier.fillMaxSize()) {
        // Behind a picture that has not loaded, or a film that has not shown its first frame.
        // It fades out with the film: an opaque full-screen draw in the window would hide the
        // film's SurfaceView, which is behind it.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - cover() }
                .background(Brush.verticalGradient(listOf(Color(0xFF14212D), Color(0xFF04070B)))),
        )
        HomeBackground(
            backdrop = backdrop,
            frosted = frosted,
            plate = plate,
            scroll = scroll,
            collapse = collapse,
            cover = cover,
            // The dock plate's glass follows the moving film, as Home's does: the frame the film
            // started from would show another shot's colours through it. A policy film has no
            // Home glass; the default wallpaper's frost stays under its plate.
            filmGlass = if (policyDefault) {
                null
            } else {
                (Wallpapers.filmFrost ?: Wallpapers.frosted)
                    .takeIf { Wallpapers.moves() && Wallpapers.backdrop != null }
            },
            lift = lift,
        )
        // One static veil for the text and the dock: darker at the top behind the status line and
        // clock, darker at the bottom behind the dock and its hint. The wallpaper stays sharp.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.22f),
                        0.33f to Color.Transparent,
                        0.66f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.50f),
                    ),
                ),
        )
    }
}
