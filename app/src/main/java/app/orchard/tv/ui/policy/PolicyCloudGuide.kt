package app.orchard.tv.ui.policy

import android.util.Log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.orchard.tv.policy.PolicyCloud
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.wallpaper.PhoneServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val TAG = "PolicyCloudGuide"

internal enum class CloudGuideStatus {
    CONNECTING,
    WAITING,
    RETRYING,
    SUCCESS,
}

internal class CloudGuideState(
    val status: CloudGuideStatus,
    val qr: ImageBitmap?,
    val pairCode: String?,
    val error: String?,
    val retry: () -> Unit,
)

/**
 * Registers this TV and polls the backend without changing the guard mode. The TV keeps its
 * current LOCAL (or NONE) state until the phone has actually bound it; only then does the
 * coordinator switch to CLOUD. The effect is scoped to the visible guide, so leaving the pane
 * stops the poll rather than switching a mode the parent can no longer see.
 */
@Composable
internal fun rememberCloudGuideState(
    deviceNo: String,
    active: Boolean,
    onBound: () -> Unit,
): CloudGuideState {
    var status by remember { mutableStateOf(CloudGuideStatus.CONNECTING) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var pairCode by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableIntStateOf(0) }
    val latestOnBound by rememberUpdatedState(onBound)

    LaunchedEffect(deviceNo, active, retryTick) {
        if (!active) return@LaunchedEffect
        if (deviceNo.isBlank()) {
            status = CloudGuideStatus.RETRYING
            error = "deviceNo is empty"
            return@LaunchedEffect
        }
        qr = null
        pairCode = null
        status = CloudGuideStatus.CONNECTING
        error = null
        var registered = false
        var qrPayload: String? = null
        while (isActive) {
            val result = if (!registered) {
                PolicyCloud.register(deviceNo).also { if (it.isSuccess) registered = true }
            } else {
                PolicyCloud.refreshLinkState(deviceNo)
            }
            if (result.isFailure) {
                status = CloudGuideStatus.RETRYING
                error = result.exceptionOrNull()?.message
                Log.w(TAG, "enrollment refresh failed", result.exceptionOrNull())
                delay(3_000)
                continue
            }
            val link = result.getOrThrow()
            if (link.bound) {
                status = CloudGuideStatus.SUCCESS
                PolicyModeCoordinator.confirmCloudBindingFromGuide(deviceNo)
                latestOnBound()
                return@LaunchedEffect
            }
            val code = link.pairCode?.code
            pairCode = code
            if (code == null) {
                // The backend generates the code shortly after enrollment; retry the status
                // poll instead of showing a QR that only contains the public deviceNo.
                qr = null
                qrPayload = null
                status = CloudGuideStatus.RETRYING
                error = "pair code is not ready"
                delay(2_000)
                continue
            }
            val payload = PolicyCloud.bindingUri(deviceNo, code)
            if (payload != qrPayload) {
                // PhoneServer.qr is CPU-bound; keep it off the frame thread. A 16x module
                // scale stays crisp on 4K panels and is still cheap to generate.
                qr = withContext(Dispatchers.Default) {
                    runCatching { PhoneServer.qr(payload, scale = 16).asImageBitmap() }
                        .getOrNull()
                }
                qrPayload = payload
            }
            if (qr == null) {
                status = CloudGuideStatus.RETRYING
                error = "QR generation failed"
                delay(1_500)
                continue
            }
            status = CloudGuideStatus.WAITING
            error = null
            delay(2_000)
        }
    }

    return CloudGuideState(
        status = status,
        qr = qr,
        pairCode = pairCode,
        error = error,
        retry = { retryTick += 1 },
    )
}
