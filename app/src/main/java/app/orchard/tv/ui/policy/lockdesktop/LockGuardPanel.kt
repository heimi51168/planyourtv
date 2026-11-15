package app.orchard.tv.ui.policy.lockdesktop

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.orchard.tv.HomeViewModel
import app.orchard.tv.R
import app.orchard.tv.apps.SourceLooks
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.SourceEntrySettings
import app.orchard.tv.system.HisenseLaserPanel
import app.orchard.tv.system.OutputDevice
import app.orchard.tv.system.SourceKind
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.control.BrightnessRow
import app.orchard.tv.ui.control.PollBrightness
import app.orchard.tv.ui.control.RoundButton
import app.orchard.tv.ui.control.VolumeRow
import app.orchard.tv.ui.home.MenuRow
import app.orchard.tv.ui.home.dateText
import app.orchard.tv.ui.home.rememberNow
import app.orchard.tv.ui.home.timeText
import app.orchard.tv.ui.policy.ParentCenterQrHint
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Type

/**
 * The lock screen's parent panel, opened from the top-right status pill. It borrows Aerial's
 * Control Center model instead of re-implementing a menu: the same top-right drop-in, 300 dp
 * glass, 16 dp padding and scrim, and the same volume and brightness rows Control Center uses in
 * Kids mode. Network, settings and personalization stay off the lock screen. When the parent
 * turned on the TV-input switch, the same round input button Control Center puts at the panel's
 * top-left opens the same source list; the input itself runs outside Aerial, which is why the
 * parent page calls that switch a presentation choice rather than a lock. The last row leaves
 * child mode through the parent PIN,
 * and the exit is temporary: screen off or restart restores the lock.
 *
 * Rows are chained with focus properties so Up stops on the first row, Down on the last, and
 * Left/Right never walk out to the dock behind the scrim; the sliders keep Left/Right for their
 * own level. BACK and a tap outside close the panel; the host owns both and returns focus to the
 * pill.
 */
@Composable
fun LockGuardPanel(
    visible: Boolean,
    vm: HomeViewModel,
    onDismiss: () -> Unit,
    onExitChildMode: () -> Unit,
    onOpenSource: (Intent) -> Unit,
) {
    val context = LocalContext.current
    val laser = remember { HisenseLaserPanel.usable(context) }
    val showsVolume = remember { OutputDevice.showsVolume(context) }
    val showsBrightness = remember { OutputDevice.showsBrightness() }
    // A null level means Aerial cannot change this panel's brightness; its row would fall back to
    // the TV's picture settings, which a child must not reach from the lock screen. Leave it out.
    val hasBrightness = showsBrightness && vm.brightness != null
    val volumeFocus = remember { FocusRequester() }
    val brightnessFocus = remember { FocusRequester() }
    val inputsFocus = remember { FocusRequester() }
    val exitFocus = remember { FocusRequester() }
    var inputsOpen by remember { mutableStateOf(false) }
    // The switch is the parent's decision; sourceEntriesAllowed() is the same gate Control
    // Center uses, and it no longer requires a running session (the lock screen itself is not
    // Home, so this only reaches the parent panel).
    val policy by PolicyModeCoordinator.state.collectAsState()
    val switchOn by SourceEntrySettings.visible.collectAsState()
    val sources = if (policy.loaded && switchOn && PolicyModeCoordinator.sourceEntriesAllowed()) {
        vm.shownSources.filter { PolicyModeCoordinator.allowsSourceEntry(it.id) }
    } else {
        emptyList()
    }
    val showInputs = sources.isNotEmpty()
    val backFocus = remember { FocusRequester() }
    val sourceFocus = remember(sources) { sources.map { FocusRequester() } }
    PollBrightness(vm, laser, enabled = visible)
    // The level is not polled on every TV, and the lock screen keeps the view model alive, so read
    // it again when the panel opens rather than showing a stale or missing row.
    LaunchedEffect(visible) {
        if (!visible) {
            inputsOpen = false
            return@LaunchedEffect
        }
        vm.refreshBrightness()
    }
    // Sources can change while the page is open (CEC, the switch, the parent page), so follow
    // the list rather than only its size.
    LaunchedEffect(showInputs) {
        if (!showInputs) inputsOpen = false
    }
    // BACK goes one page up, as it does in Control Center; the lock screen closes the panel
    // itself when this page is not open.
    BackHandler(enabled = visible && inputsOpen) { inputsOpen = false }
    // Open on the first control, and move focus into the input list (or back) when that page
    // opens. Sources can arrive a moment after the panel.
    LaunchedEffect(visible, inputsOpen, showInputs, sources) {
        if (!visible) return@LaunchedEffect
        withFrameNanos { }
        val target = when {
            inputsOpen -> backFocus
            // Same first control as Control Center's main page: the input button at top-left.
            showInputs -> inputsFocus
            showsVolume -> volumeFocus
            hasBrightness -> brightnessFocus
            else -> exitFocus
        }
        runCatching { target.requestFocus() }
    }
    // The scrim and the panel animate separately, as Control Center's do: it fades in over the
    // dock while the glass grows out of the corner.
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, easing = Motion.EaseOut)),
        exit = fadeOut(tween(180, easing = Motion.EaseOut)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .dismissOnTap(onDismiss)
                .background(Color.Black.copy(alpha = 0.28f)),
        )
    }
    Box(
        Modifier.fillMaxSize().padding(top = 24.dp, end = 32.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        AnimatedVisibility(
            visible = visible,
            // Grows out of the corner where the pill lives; leaves faster than it came.
            enter = fadeIn(tween(200, easing = Motion.EaseOut)) +
                scaleIn(
                    tween(280, easing = Motion.Drawer),
                    initialScale = 0.9f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
            exit = fadeOut(tween(160, easing = Motion.EaseOut)) +
                scaleOut(
                    tween(160, easing = Motion.EaseOut),
                    targetScale = 0.95f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
        ) {
            val now by rememberNow()
            Column(
                Modifier
                    .width(300.dp)
                    .consumeTaps()
                    // The lock screen's panel sits over the wallpaper alone, the case where
                    // Control Center uses its see-through film glass.
                    .liquidGlass(24.dp, seeThroughFilm = true)
                    .padding(16.dp),
            ) {
                BasicText(
                    timeText(now),
                    style = Type.SmallClock.copy(fontSize = Type.LargeTitle.fontSize * 1.05f),
                )
                BasicText(dateText(now), style = Type.Callout)
                Spacer(Modifier.height(12.dp))
                if (inputsOpen) {
                    MenuRow(
                        glyph = Glyph.ChevronLeft,
                        text = stringResource(R.string.cc_inputs),
                        onClick = { inputsOpen = false },
                        focusRequester = backFocus,
                        modifier = Modifier.focusProperties {
                            up = backFocus
                            down = sourceFocus.firstOrNull() ?: backFocus
                            left = backFocus
                            right = backFocus
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    val connected = stringResource(R.string.input_connected)
                    // A TV can report a long list; keep the panel inside the canvas and let the
                    // list scroll with focus, as Control Center's input page does.
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        sources.forEachIndexed { i, source ->
                            MenuRow(
                                glyph = inputGlyph(source.kind),
                                text = source.label,
                                // What the owner named the input on its tile, else what HDMI-CEC
                                // reports, the same trailing text Control Center shows.
                                trailing = SourceLooks.of(source.id)?.let { it.name ?: it.device?.let { d -> stringResource(d.label) } }
                                    ?: source.deviceName ?: if (source.connected) connected else null,
                                onClick = {
                                    inputsOpen = false
                                    onDismiss()
                                    onOpenSource(source.intent)
                                },
                                focusRequester = sourceFocus[i],
                                modifier = Modifier.focusProperties {
                                    up = if (i == 0) backFocus else sourceFocus[i - 1]
                                    down = sourceFocus.getOrNull(i + 1) ?: backFocus
                                    left = sourceFocus[i]
                                    right = sourceFocus[i]
                                },
                            )
                            if (i != sources.lastIndex) Spacer(Modifier.height(6.dp))
                        }
                    }
                } else {
                    // The input button sits where Aerial's Control Center puts it: top-left,
                    // under the clock, above the volume slider.
                    if (showInputs) {
                        Row(Modifier.fillMaxWidth()) {
                            RoundButton(
                                glyph = Glyph.Input,
                                label = stringResource(R.string.cc_inputs),
                                focusRequester = inputsFocus,
                                modifier = Modifier.focusProperties {
                                    up = inputsFocus
                                    down = when {
                                        showsVolume -> volumeFocus
                                        hasBrightness -> brightnessFocus
                                        else -> exitFocus
                                    }
                                    left = inputsFocus
                                    right = inputsFocus
                                },
                            ) { inputsOpen = true }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (showsVolume) {
                        VolumeRow(
                            vm = vm,
                            returnTo = volumeFocus,
                            focusRequester = volumeFocus,
                            modifier = Modifier.focusProperties {
                                up = if (showInputs) inputsFocus else volumeFocus
                                down = if (hasBrightness) brightnessFocus else exitFocus
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (hasBrightness) {
                        BrightnessRow(
                            vm = vm,
                            laser = laser,
                            focusRequester = brightnessFocus,
                            modifier = Modifier.focusProperties {
                                up = when {
                                    showsVolume -> volumeFocus
                                    showInputs -> inputsFocus
                                    else -> brightnessFocus
                                }
                                down = exitFocus
                            },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    MenuRow(
                        glyph = Glyph.Shield,
                        text = stringResource(R.string.policy_exit_child_mode),
                        onClick = onExitChildMode,
                        focusRequester = exitFocus,
                        modifier = Modifier.focusProperties {
                            up = when {
                                hasBrightness -> brightnessFocus
                                showsVolume -> volumeFocus
                                showInputs -> inputsFocus
                                else -> exitFocus
                            }
                            down = exitFocus
                            left = exitFocus
                            right = exitFocus
                        },
                    )
                    // The same small parent-center code the timer panel shows: a parent passing by
                    // can scan without first entering the quiz gate. It never takes focus.
                    Spacer(Modifier.height(12.dp))
                    ParentCenterQrHint(load = visible)
                }
            }
        }
    }
}

/** The same glyph Control Center gives each source kind. */
private fun inputGlyph(kind: SourceKind): Glyph = when (kind) {
    SourceKind.Hdmi, SourceKind.DisplayPort -> Glyph.Hdmi
    SourceKind.Tuner -> Glyph.Antenna
    SourceKind.Av -> Glyph.Av
    SourceKind.Usb -> Glyph.Usb
    SourceKind.Other -> Glyph.Input
}
