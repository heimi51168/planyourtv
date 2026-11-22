package app.orchard.tv.ui.policy.lockdesktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.ui.home.AppTile
import app.orchard.tv.ui.home.PlatePadH
import app.orchard.tv.ui.home.PlatePadV
import app.orchard.tv.ui.home.gridGap
import app.orchard.tv.ui.home.restTileWidth
import app.orchard.tv.ui.theme.LocalReducedMotion
import kotlin.math.roundToInt

/**
 * The lock desktop's shared left edge: the clock, the estimate band, the dock row and its hint all
 * start on this margin, which is Aerial's own grid margin.
 */
internal val LockDesktopStart = 56.dp

/** Home's shelf constants (HomeScreen.GridMetrics), for the lock screen's dock row. */
private val LockDockScreenW = 960.dp
private val LockDockScreenH = 540.dp
private val LockDockRowNameRoom = 50.dp
private val LockDockPlateMarginBottom = 30.dp
private val LockDockPeekClearance = 12.dp
private val LockDockHintGap = 4.dp
private val LockDockRowAboveTop = 72.dp

/**
 * Aerial's one-row grid, reproduced for the lock desktop: the dock is the top row on the glass
 * plate, and the parent's study apps continue in the rows below, scrolling up as on Home. Real
 * app tiles name the focused app under it, as Home does; the fixed action cards already carry
 * their names in their artwork and draw no second label. The rows already reserve name room, so
 * Home's two-row shelf and the extra name room on its plate do not apply here.
 */
internal data class LockDockMetrics(
    val columns: Int,
    val tileCount: Int,
    val rows: Int,
    val tileW: Dp,
    val tileH: Dp,
    val gap: Dp,
    val left: Dp,
    val top: Dp,
    val rowWidth: Dp,
    val plateOn: Boolean,
    val peek: Boolean,
    val pitchX: Dp,
    val pitchY: Dp,
    val restY: Dp,
    val collapse: Dp,
) {
    /** The plate's bottom, or the top row's bottom when Home draws no plate. */
    val shelfBottom: Dp
        get() = top + tileH + if (plateOn) PlatePadV else 0.dp

    /** Just under the shelf, in the little space Home leaves below its top row. */
    val hintTop: Dp get() = shelfBottom + LockDockHintGap

    val plateLeft: Dp get() = left - PlatePadH
    val plateTop: Dp get() = top - PlatePadV
    val plateRight: Dp get() = left + rowWidth + PlatePadH

    /** Rows below the shelf wait for the scroll unless the next one peeks under it. */
    val hiddenFrom: Int get() = if (peek) Int.MAX_VALUE else columns

    fun slot(index: Int): Pair<Dp, Dp> =
        (left + pitchX * (index % columns)) to (top + pitchY * (index / columns))

    /**
     * How far the grid scrolls for a row: Home's own limit, keeping the row above in view and the
     * bottom of the last row clear of the screen edge.
     */
    fun scrollFor(row: Int): Dp {
        if (row <= 0) return 0.dp
        val wanted = top + pitchY * row - restY
        val bottom = top + pitchY * rows + 36.dp - LockDockScreenH
        return minOf(wanted, maxOf(bottom, top + pitchY - restY))
    }

    val maxScroll: Dp get() = scrollFor(rows - 1).coerceAtLeast(0.dp)
}

/**
 * Aerial's desktop geometry for the lock's dock row and the grid below it, on the 960×540 dp
 * canvas. The same `peekNextRow`, `homePlate` and `homeColumns` settings move both screens.
 */
internal fun lockDockMetrics(columns: Int, tiles: Int): LockDockMetrics {
    val cols = columns.coerceAtLeast(1)
    val count = tiles.coerceAtLeast(0)
    val rows = ((count + cols - 1) / cols).coerceAtLeast(1)
    val tileW = restTileWidth(LockDockScreenW, cols)
    val tileH = tileW * 9f / 16f
    val gap = gridGap(cols)
    val pitchX = tileW + gap
    val pitchY = tileH + LockDockRowNameRoom
    val plateOn = AerialSettings.homePlate
    val peek = AerialSettings.peekNextRow
    val top = when {
        peek -> LockDockScreenH - pitchY - LockDockPeekClearance
        plateOn -> LockDockScreenH - tileH - (LockDockPlateMarginBottom + PlatePadV)
        else -> LockDockScreenH - tileH - 70.dp
    }
    val restY = maxOf(LockDockScreenH * 0.37f, LockDockRowAboveTop + pitchY)
    val collapse = (top - restY).coerceAtLeast(pitchY / 2)
    val rowWidth = tileW * cols + gap * (cols - 1).coerceAtLeast(0)
    return LockDockMetrics(
        columns = cols,
        tileCount = count,
        rows = rows,
        tileW = tileW,
        tileH = tileH,
        gap = gap,
        left = LockDesktopStart,
        top = top,
        rowWidth = rowWidth,
        plateOn = plateOn,
        peek = peek,
        pitchX = pitchX,
        pitchY = pitchY,
        restY = restY,
        collapse = collapse,
    )
}

/**
 * Shared by the dock, the backdrop's plate and the identity above it: the grid's scroll and the
 * index of the dock tile holding focus. The scroll is read in draw or layout only, so entering
 * the grid does not recompose the lock screen frame by frame.
 */
@Stable
internal class LockDockState {
    val scroll = Animatable(0f)
    var focusedIndex by mutableStateOf<Int?>(null)
}

@Composable
internal fun rememberLockDockState(): LockDockState = remember { LockDockState() }

/** One position in the dock: a fixed action card, a quiz trigger, a study app or an unlimited app. */
private sealed interface DockTile {
    val key: String
    val app: AppEntry
    val enabled: Boolean

    data class Action(val slot: LockDockSlot, override val app: AppEntry) : DockTile {
        override val key: String get() = "action:${slot.id.name}"
        override val enabled: Boolean get() = slot.enabled
    }

    /**
     * A configured entertainment app. It is the quiz entry, not a launch shortcut: selecting it
     * opens the challenge, and the host never starts [app] from the lock desktop.
     */
    data class Gateway(
        override val app: AppEntry,
        override val enabled: Boolean,
    ) : DockTile {
        override val key: String get() = "gateway:${app.packageName}"
    }

    data class StudyApp(override val app: AppEntry) : DockTile {
        override val key: String get() = "app:${app.packageName}"
        override val enabled: Boolean get() = true
    }

    /** A parent-configured app with no timer. Selecting it launches it directly. */
    data class UnlimitedApp(override val app: AppEntry) : DockTile {
        override val key: String get() = "unlimited:${app.packageName}"
        override val enabled: Boolean get() = true
    }
}

/**
 * The lock screen's action row and the grid under it, in Aerial's own desktop grammar. Geometry,
 * focus zoom, press bounce, ring, shadow and sounds all come from [AppTile]; this file only places
 * the tiles and forwards the host's focus and click callbacks.
 *
 * The fixed actions come first, then the entertainment apps that open the quiz, then the parent's
 * study apps and finally the unlimited apps, [LockDockMetrics.columns] a row. The first row sits
 * where the dock always sat, on its
 * glass plate; what does not fit continues in the rows below. With [AerialSettings.peekNextRow] the
 * second row shows its top edge under the plate, and moving down scrolls the grid up exactly as
 * Home does. Focus moves through the rows by AppTile's own focus search; [LockDockState.scroll]
 * only follows which row holds focus.
 */
@Composable
internal fun LockDesktopDock(
    slots: List<LockDockSlot>,
    gatewayApps: List<AppEntry>,
    gatewayEnabled: Boolean,
    studyApps: List<AppEntry>,
    unlimitedApps: List<AppEntry>,
    onFocused: (LockDockId) -> Unit,
    onSelect: (LockDockId) -> Unit,
    onGatewayAppFocused: (AppEntry) -> Unit,
    onGatewayAppSelected: (AppEntry) -> Unit,
    onStudyAppFocused: (AppEntry) -> Unit,
    onStudyAppSelected: (AppEntry, Rect?) -> Unit,
    onUnlimitedAppFocused: (AppEntry) -> Unit,
    onUnlimitedAppSelected: (AppEntry, Rect?) -> Unit,
    firstFocus: FocusRequester,
    startFocus: FocusRequester,
    state: LockDockState,
    resetFocus: Boolean = false,
    /** False while a modal sheet owns the screen; keeps every tile out of focus search. */
    interactive: Boolean = true,
    /** False while an action message uses the line under the dock, so a name cannot overlap it. */
    showNames: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (slots.isEmpty() && gatewayApps.isEmpty() && studyApps.isEmpty() &&
        unlimitedApps.isEmpty()
    ) {
        return
    }
    val context = LocalContext.current
    val columns = AerialSettings.homeColumns
    val metrics = lockDockMetrics(
        columns,
        slots.size + gatewayApps.size + studyApps.size + unlimitedApps.size,
    )
    // Actions and apps share one grid; keying by tile keeps its focus and press state when an
    // action card appears, disappears, or the app lists are edited.
    val tiles = remember(slots, gatewayApps, gatewayEnabled, studyApps, unlimitedApps, context) {
        slots.map { DockTile.Action(it, LockDockArt.entry(context, it)) } +
            gatewayApps.map { DockTile.Gateway(it, gatewayEnabled) } +
            studyApps.map { DockTile.StudyApp(it) } +
            unlimitedApps.map { DockTile.UnlimitedApp(it) }
    }
    val requesters = remember(tiles.map { it.key }) {
        tiles.associate { it.key to FocusRequester() }
    }
    val firstEnabledKey = tiles.firstOrNull { it.enabled }?.key

    val density = LocalDensity.current
    val reducedMotion = LocalReducedMotion.current
    val rowEnd = tiles.lastIndex
    val collapsePx = with(density) { metrics.collapse.toPx() }

    // The row the scroll follows. Only a row change recomposes the dock; moves within a row only
    // update the stored index. Focusing the status badge above the grid (resetFocus) returns to
    // the shelf, as Up on Home does.
    val targetRow by remember(metrics, resetFocus) {
        derivedStateOf {
            if (resetFocus) return@derivedStateOf 0
            val index = state.focusedIndex ?: return@derivedStateOf 0
            if (index !in 0 until metrics.tileCount) return@derivedStateOf 0
            index / columns
        }
    }
    val maxScrollPx = with(density) { metrics.maxScroll.toPx() }.coerceAtLeast(0f)
    val targetPx = with(density) { metrics.scrollFor(targetRow).toPx() }
    LaunchedEffect(targetPx, maxScrollPx) {
        state.scroll.updateBounds(0f, maxScrollPx)
        if (reducedMotion) {
            state.scroll.snapTo(targetPx)
        } else {
            // Home's own scroll: a quick second press retargets the spring mid-flight.
            state.scroll.animateTo(targetPx, spring(dampingRatio = 1f, stiffness = 280f))
        }
    }

    // One full-canvas layer for the grid, scrolled as Home's own content is. Tiles keep their
    // grid slots and the whole layer moves, so focus search and the scroll travel together.
    Box(
        modifier
            .fillMaxSize()
            .offset { IntOffset(0, -state.scroll.value.roundToInt()) },
    ) {
        tiles.forEachIndexed { index, tile ->
            key(tile.key) {
                val (x, y) = metrics.slot(index)
                val slot = with(density) { IntOffset(x.roundToPx(), y.roundToPx()) }
                val requester = when {
                    tile.key == firstEnabledKey -> firstFocus
                    tile is DockTile.Action && tile.slot.id == LockDockId.START -> startFocus
                    else -> requesters.getValue(tile.key)
                }
                AppTile(
                    app = tile.app,
                    width = metrics.tileW,
                    height = metrics.tileH,
                    focusRequester = requester,
                    enabled = tile.enabled && interactive,
                    moving = false,
                    // Disabled action cards already use the supplied gray artwork; dimming again
                    // would double-gray the picture. Real app icons have no gray variant, so the
                    // quiz triggers dim themselves while the quiz cannot be opened.
                    opacity = {
                        if (tile is DockTile.Gateway && !tile.enabled) 0.35f else 1f
                    },
                    onFocused = {
                        state.focusedIndex = index
                        when (tile) {
                            is DockTile.Action -> onFocused(tile.slot.id)
                            is DockTile.Gateway -> onGatewayAppFocused(tile.app)
                            is DockTile.StudyApp -> onStudyAppFocused(tile.app)
                            is DockTile.UnlimitedApp -> onUnlimitedAppFocused(tile.app)
                        }
                    },
                    onClick = { bounds ->
                        when (tile) {
                            is DockTile.Action -> onSelect(tile.slot.id)
                            is DockTile.Gateway -> onGatewayAppSelected(tile.app)
                            is DockTile.StudyApp -> onStudyAppSelected(tile.app, bounds)
                            is DockTile.UnlimitedApp -> onUnlimitedAppSelected(tile.app, bounds)
                        }
                    },
                    onLongPress = {},
                    modifier = Modifier
                        .offset { slot }
                        .then(
                            if (index >= metrics.hiddenFrom) {
                                // Rows below the shelf stay out of sight until the grid scrolls
                                // to them, as Home's do. No offscreen buffer: it would clip the
                                // focus frame to the tile.
                                Modifier.graphicsLayer {
                                    alpha = (state.scroll.value / collapsePx * 2.5f)
                                        .coerceIn(0f, 1f)
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }
                            } else {
                                Modifier
                            },
                        ),
                    // Fixed action cards are whole pictures whose names are already in the art.
                    showName = showNames && tile !is DockTile.Action,
                    // The dock row sits on the plate; its name must not step down past the frame.
                    nameOnPlate = metrics.plateOn && index < metrics.columns,
                    rowEnd = index % columns == columns - 1 || index == rowEnd,
                )
            }
        }
    }
}
