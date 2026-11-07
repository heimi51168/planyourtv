package app.orchard.tv.ui.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.orchard.tv.ui.home.PanelBackdrop
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.wallpaper.Wallpapers

// The parent center and its picker use a near-full-screen glass sheet. (The lock screen's glass
// reaches the screen edges without a rim, but its controls keep this same sheet geometry.)
// Keeping the size here means every floating parent surface has one rim and one margin.
internal val ParentSheetWidth = 920.dp
internal val ParentSheetHeight = 508.dp
internal val ParentSheetCorner = 28.dp
internal val ParentSheetPaddingH = 28.dp
internal val ParentSheetPaddingV = 16.dp

/**
 * The parent center's full-screen backdrop: the frosted wallpaper under a dark veil, as folders
 * and Hidden Apps open on Home. A black base keeps the page opaque when no wallpaper is ready
 * (the frosted picture is missing while a film is still starting, for one).
 */
@Composable
internal fun PolicyParentBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Palette.Background)) {
        PanelBackdrop(
            frosted = Wallpapers.frosted.takeIf { Wallpapers.backdrop != null },
            frost = { 1f },
            veil = { 1f },
        )
    }
}
