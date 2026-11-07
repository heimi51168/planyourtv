package app.orchard.tv.ui.policy

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.policy.PolicyCloud
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.PolicyUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val TAG = "PolicyBinding"

/**
 * Parent binding for the Android TV app.
 *
 * The QR payload is a v2 binding URI carrying the TV's public deviceNo and the
 * one-time pair code. It is shown only after the parent PIN, and disappears as
 * soon as the backend reports the device bound. deviceType=TV_APP is reported
 * by PolicyCloud.register; the parent app uses it to distinguish this from the
 * TVCASE timing box.
 */
@Composable
fun PolicyBindingScreen(state: PolicyUiState) {
    val deviceNo = state.deviceNo
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var pairCode by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(R.string.policy_bind_connecting) }
    var detail by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val refreshFocus = remember { FocusRequester() }
    // The full deviceNo stays in the QR payload; the label is masked for privacy.
    val displayId = remember(deviceNo) { displayDeviceNo(deviceNo) }

    BackHandler { PolicyModeCoordinator.cancelRemoteBinding() }

    LaunchedEffect(deviceNo, refresh) {
        qr = null
        pairCode = null
        status = R.string.policy_bind_connecting
        detail = null
        if (deviceNo.isBlank()) {
            status = R.string.policy_bind_retrying
            return@LaunchedEffect
        }
        var registered = false
        var qrPayload: String? = null
        while (isActive) {
            val result = if (!registered) {
                PolicyCloud.register(deviceNo).also { if (it.isSuccess) registered = true }
            } else {
                PolicyCloud.refreshLinkState(deviceNo)
            }
            if (result.isFailure) {
                Log.w(TAG, "device registration failed", result.exceptionOrNull())
                status = R.string.policy_bind_retrying
                detail = result.exceptionOrNull()?.message
                delay(3_000)
                continue
            }
            val link = result.getOrThrow()
            if (link.bound) {
                status = R.string.policy_bind_success
                // Reports guard status and logs the first config; the gate moves
                // to Home as soon as PolicyRuntime.onCloudBound runs.
                PolicyCloud.onBound(deviceNo)
                return@LaunchedEffect
            }
            // The code is only a proof that the parent can see this screen; it is not the
            // device secret and must not be logged.
            val code = link.pairCode?.code
            pairCode = code
            if (code == null) {
                qr = null
                qrPayload = null
                status = R.string.policy_bind_retrying
                detail = "pair code is not ready"
                delay(2_000)
                continue
            }
            val payload = PolicyCloud.bindingUri(deviceNo, code)
            if (payload != qrPayload) {
                // On a cold start ZXing can wait behind Aerial's app-list load. Show the
                // waiting state; the QR is only rendered once it carries the pair code.
                qr = withContext(Dispatchers.Default) {
                    runCatching {
                        app.orchard.tv.wallpaper.PhoneServer.qr(payload).asImageBitmap()
                    }.getOrNull()
                }
                qrPayload = payload
            }
            if (qr == null) {
                status = R.string.policy_bind_retrying
                detail = "QR generation failed"
                delay(1_500)
                continue
            }
            status = R.string.policy_bind_waiting
            detail = null
            delay(2_000)
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { refreshFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101827), Color(0xFF03060C)))),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .liquidGlass(28.dp)
                .padding(horizontal = 40.dp, vertical = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(252.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    qr?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } ?: BasicText(
                        stringResource(
                            if (status == R.string.policy_bind_retrying) status
                            else R.string.policy_bind_qr_loading,
                        ),
                        style = Type.Caption.copy(color = Color(0xFF444B57), textAlign = TextAlign.Center),
                    )
                }
                Spacer(Modifier.height(12.dp))
                BasicText(
                    stringResource(status),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel, textAlign = TextAlign.Center),
                )
                detail?.let {
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        it.take(120),
                        style = Type.Caption.copy(color = Palette.Orange, textAlign = TextAlign.Center),
                        modifier = Modifier.widthIn(max = 252.dp),
                    )
                }
            }

            Column(modifier = Modifier.width(470.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Shield, Palette.Label, 22.dp)
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        stringResource(R.string.policy_bind_kicker),
                        style = Type.Caption.copy(color = Palette.SecondaryLabel),
                    )
                }
                Spacer(Modifier.height(10.dp))
                BasicText(stringResource(R.string.policy_bind_title), style = Type.LargeTitle)
                Spacer(Modifier.height(6.dp))
                BasicText(
                    stringResource(R.string.policy_bind_desc),
                    style = Type.Body.copy(color = Palette.SecondaryLabel),
                )
                Spacer(Modifier.height(20.dp))
                Step(stringResource(R.string.policy_bind_step1))
                Step(stringResource(R.string.policy_bind_step2))
                Step(stringResource(R.string.policy_bind_step3))
                Spacer(Modifier.height(18.dp))
                BasicText(
                    stringResource(R.string.policy_bind_device_id),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    displayId,
                    style = Type.Body.copy(color = Palette.Label),
                    modifier = Modifier.widthIn(max = 470.dp),
                )
                pairCode?.let { code ->
                    Spacer(Modifier.height(12.dp))
                    BasicText(
                        stringResource(R.string.policy_bind_pair_code),
                        style = Type.Caption.copy(color = Palette.SecondaryLabel),
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        code.chunked(3).joinToString(" "),
                        style = Type.LargeTitle.copy(color = Palette.Label),
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        stringResource(R.string.policy_bind_pair_code_hint),
                        style = Type.Caption.copy(color = Palette.SecondaryLabel),
                    )
                }
                Spacer(Modifier.height(10.dp))
                BasicText(
                    stringResource(R.string.policy_bind_expiry),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    stringResource(R.string.policy_bind_demo),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LockButton(
                        label = stringResource(R.string.policy_bind_retry),
                        onClick = { refresh += 1 },
                        focusRequester = refreshFocus,
                        primary = true,
                        width = 210.dp,
                    )
                    LockButton(
                        label = stringResource(R.string.policy_bind_later),
                        onClick = { PolicyModeCoordinator.cancelRemoteBinding() },
                        width = 170.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Step(text: String) {
    BasicText(
        text,
        style = Type.Body.copy(color = Palette.Label),
        modifier = Modifier.padding(vertical = 3.dp),
    )
}
