package app.orchard.tv.ui.home

import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.orchard.tv.BuildConfig
import app.orchard.tv.R
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.components.smoothBackground
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the app is and where it comes from: its version, the Aerial project it is built on, and the
 * MIT license that came with that project. Reached from the bottom of Personalize; Back returns
 * there. The license is read from the APK's assets, so the copy shown is the one shipped with it.
 */
@Composable
fun AboutSheet(visible: Boolean, onDismiss: () -> Unit) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val context = LocalContext.current
    var license by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible) {
        if (!visible || license != null) return@LaunchedEffect
        license = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("licenses/aerial-LICENSE.txt").bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200, easing = Motion.EaseOut)) + scaleIn(tween(300, easing = Motion.Drawer), initialScale = 0.94f),
        exit = fadeOut(tween(150, easing = Motion.EaseOut)) + scaleOut(tween(150, easing = Motion.EaseOut), targetScale = 0.97f),
        modifier = Modifier.fillMaxSize(),
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        Box(
            Modifier.fillMaxSize().dismissOnTap(onDismiss).background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .width(900.dp)
                    .height(500.dp)
                    .consumeTaps()
                    .liquidGlass(28.dp)
                    .padding(28.dp),
            ) {
                Column(Modifier.width(400.dp).fillMaxHeight()) {
                    BasicText(stringResource(R.string.about_title), style = Type.LargeTitle)
                    BasicText(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = Type.Callout,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    BasicText(stringResource(R.string.about_built_on), style = Type.Body)
                    Spacer(Modifier.height(14.dp))
                    BasicText(stringResource(R.string.about_not_official), style = Type.Body)
                    Spacer(Modifier.height(14.dp))
                    BasicText(
                        stringResource(R.string.about_project),
                        style = Type.Caption.copy(color = Palette.SecondaryLabel),
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        stringResource(R.string.about_hint),
                        style = Type.Caption.copy(color = Palette.TertiaryLabel),
                    )
                }
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    BasicText(stringResource(R.string.about_license_title), style = Type.Headline)
                    BasicText(
                        stringResource(R.string.about_license_hint),
                        style = Type.Caption.copy(color = Palette.SecondaryLabel),
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                    LicenseText(license, first, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/**
 * The license text, a few lines at a time. It is not a control: Up and Down read it, as the
 * release notes do in Control Center, and the bar on the right shows how much is left.
 */
@Composable
private fun LicenseText(text: String?, focusRequester: FocusRequester, modifier: Modifier) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val step = with(LocalDensity.current) { 64.dp.toPx() }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(140, easing = Motion.EaseOut), label = "about-license-focus")
    Box(modifier) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = glow }.smoothBackground(Palette.Fill, 12.dp))
        Column(
            Modifier
                .fillMaxSize()
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    val down = when (e.nativeKeyEvent.keyCode) {
                        NativeKeyEvent.KEYCODE_DPAD_DOWN -> true
                        NativeKeyEvent.KEYCODE_DPAD_UP -> false
                        else -> return@onKeyEvent false
                    }
                    if (if (down) scroll.value >= scroll.maxValue else scroll.value <= 0) return@onKeyEvent false
                    if (e.type == KeyEventType.KeyDown) {
                        scope.launch { scroll.animateScrollBy(if (down) step else -step, spring(dampingRatio = 1f, stiffness = 500f)) }
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
                .drawWithContent {
                    drawContent()
                    if (scroll.maxValue <= 0) return@drawWithContent
                    val track = size.height - 16.dp.toPx()
                    val thumb = (track * size.height / (size.height + scroll.maxValue)).coerceAtLeast(12.dp.toPx())
                    val top = 8.dp.toPx() + (track - thumb) * scroll.value / scroll.maxValue
                    drawRoundRect(
                        if (focused) Palette.SecondaryLabel else Palette.TertiaryLabel,
                        topLeft = Offset(size.width - 6.dp.toPx(), top),
                        size = Size(2.5.dp.toPx(), thumb),
                        cornerRadius = CornerRadius(1.25.dp.toPx()),
                    )
                }
                .verticalScroll(scroll)
                .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            BasicText(
                text?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.about_license_missing),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
            )
        }
    }
}
