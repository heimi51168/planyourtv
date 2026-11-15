package app.orchard.tv.ui.policy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import app.orchard.tv.R
import app.orchard.tv.bot.BotBinding
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.wallpaper.PhoneServer

/**
 * The WeChat pane: the feature switch, the bound parents and the QR binding
 * view. The parent center owns the row keys ("wechat-*"); this pane only
 * renders them through the same row components as the local pane, so d-pad
 * behaviour matches the rest of the center.
 *
 * The QR view is a display state with no focusable rows: the poll loop runs
 * in the center's scope and Back leaves it, exactly like the quiz QR pane.
 */
@Composable
internal fun PolicyWeChatPane(
    enabled: Boolean,
    bindings: List<BotBinding>,
    qrActive: Boolean,
    qrPayload: String?,
    qrStatus: Int?,
    qrDigits: String?,
    armedRemoveKey: String?,
    focusedTarget: ParentFocusTarget?,
    focusFor: (String) -> androidx.compose.ui.focus.FocusRequester,
    onFocusRow: (String) -> Unit,
    onActivate: (String) -> Unit,
    onFocusRail: () -> Unit,
) {
    if (qrActive) {
        QrBindingView(qrPayload, qrStatus, qrDigits)
        return
    }
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
    ) {
        PolicySwitchRow(
            title = stringResource(R.string.policy_v6_wechat_enable),
            status = stringResource(
                if (enabled) R.string.policy_v6_wechat_on else R.string.policy_v6_wechat_off,
            ),
            on = enabled,
            focusRequester = focusFor("wechat-enable"),
            highlighted = focusedTarget == ParentFocusTarget.LocalRow("wechat-enable"),
            onClick = { onActivate("wechat-enable") },
            onUp = { onFocusRail() },
            onDown = { onFocusRow("wechat-remove-0".takeIf { bindings.isNotEmpty() } ?: "wechat-add") },
            onLeft = onFocusRail,
        )
        Spacer(Modifier.height(8.dp))
        bindings.forEachIndexed { index, binding ->
            val key = "wechat-remove-$index"
            PolicyActionRow(
                label = if (key == armedRemoveKey) {
                    stringResource(R.string.policy_v6_remove_confirm)
                } else {
                    stringResource(R.string.policy_v6_wechat_remove, binding.displayName(index))
                },
                focusRequester = focusFor(key),
                onClick = { onActivate(key) },
                onUp = { onFocusRow(if (index == 0) "wechat-enable" else "wechat-remove-${index - 1}") },
                onDown = {
                    onFocusRow(
                        if (index == bindings.size - 1) "wechat-add" else "wechat-remove-${index + 1}",
                    )
                },
                onLeft = onFocusRail,
                trailing = stringResource(R.string.policy_v6_wechat_bound),
                fontSize = 15.sp,
                highlighted = focusedTarget == ParentFocusTarget.LocalRow(key),
            )
            Spacer(Modifier.height(6.dp))
        }
        PolicyActionRow(
            label = stringResource(R.string.policy_v6_wechat_add),
            focusRequester = focusFor("wechat-add"),
            onClick = { onActivate("wechat-add") },
            onUp = {
                onFocusRow(
                    if (bindings.isEmpty()) "wechat-enable" else "wechat-remove-${bindings.size - 1}",
                )
            },
            onDown = { onFocusRail() },
            onLeft = onFocusRail,
            trailing = stringResource(R.string.policy_v6_wechat_count, bindings.size, BotBindingStoreMax),
            fontSize = 15.sp,
            highlighted = focusedTarget == ParentFocusTarget.LocalRow("wechat-add"),
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            stringResource(R.string.policy_v6_wechat_hint),
            style = Type.Caption.copy(fontSize = 11.sp, color = Palette.SecondaryLabel),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private const val BotBindingStoreMax = 4

/** Full-bleed QR: big code, one status line, the three-step instruction. */
@Composable
private fun QrBindingView(payload: String?, status: Int?, digits: String?) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QrPlate(payload)
        Spacer(Modifier.width(32.dp))
        Column(modifier = Modifier.width(360.dp)) {
            BasicText(
                stringResource(R.string.policy_v6_wechat_qr_title),
                style = Type.Headline.copy(fontSize = 20.sp),
            )
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(R.string.policy_v6_wechat_qr_hint),
                style = Type.Body.copy(fontSize = 14.sp, color = Palette.Label),
            )
            Spacer(Modifier.height(14.dp))
            val statusText = when {
                digits != null -> stringResource(R.string.policy_v6_wechat_qr_digits, digits)
                status != null -> stringResource(status)
                else -> stringResource(R.string.policy_v6_wechat_qr_waiting)
            }
            BasicText(
                statusText,
                style = Type.Body.copy(fontSize = 15.sp, color = Palette.Label, textAlign = TextAlign.Start),
            )
        }
    }
}

@Composable
private fun QrPlate(payload: String?) {
    var bitmap by remember(payload) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(payload) { mutableStateOf(false) }
    LaunchedEffect(payload) {
        if (payload != null) {
            bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { PhoneServer.qr(payload, scale = 16).asImageBitmap() }.getOrNull()
            }
            failed = bitmap == null
        }
    }
    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        when {
            current != null -> Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
            failed -> BasicText(
                stringResource(R.string.policy_v6_wechat_qr_failed),
                style = Type.Caption.copy(
                    fontSize = 12.sp,
                    color = Color(0xFF6E6E73),
                    textAlign = TextAlign.Center,
                ),
            )
            else -> BasicText(
                stringResource(R.string.policy_v6_wechat_qr_loading),
                style = Type.Caption.copy(
                    fontSize = 14.sp,
                    color = Color(0xFF6E6E73),
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
