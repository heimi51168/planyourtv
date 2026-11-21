package app.orchard.tv.ui.policy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.orchard.tv.wallpaper.PhoneServer
import app.orchard.tv.wallpaper.PhoneWebState
import app.orchard.tv.wallpaper.PhoneWebStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class ParentWebQrState(
    val qr: QuizQr = QuizQr(null, null, true),
    val status: PhoneWebStatus = PhoneWebStatus(),
)

private const val STATUS_POLL_MS = 12_000L

/**
 * The parent-center QR and its service status. Polling only runs while the caller is visible;
 * the caller passes [enabled] for warmed-but-hidden panels. A status poll is also the retry:
 * [PhoneServer.parentWebStatus] starts a server that failed at app launch and the QR is rebuilt
 * when the TV's address or port changes.
 */
@Composable
internal fun rememberParentWebQr(
    scale: Int,
    enabled: Boolean = true,
    fragment: String = "#parent",
): ParentWebQrState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ParentWebQrState()) }
    LaunchedEffect(scale, enabled, fragment) {
        if (!enabled) return@LaunchedEffect
        var lastLink: String? = null
        var lastBitmap: ImageBitmap? = null
        while (true) {
            val status = withContext(Dispatchers.IO) { PhoneServer.parentWebStatus(context) }
            val link = if (status.state == PhoneWebState.OK) {
                withContext(Dispatchers.IO) {
                    PhoneServer.start(context)?.let { "${it}quiz${fragment.orEmpty()}" }
                }
            } else {
                null
            }
            val shownStatus = if (link == null && status.state == PhoneWebState.OK) {
                status.copy(state = PhoneWebState.NOT_STARTED)
            } else {
                status
            }
            val bitmap = when {
                link == null -> null
                link == lastLink -> lastBitmap
                else -> withContext(Dispatchers.Default) {
                    runCatching { PhoneServer.qr(link, scale = scale).asImageBitmap() }.getOrNull()
                }
            }
            lastLink = link
            lastBitmap = bitmap
            state = ParentWebQrState(QuizQr(link, bitmap, false), shownStatus)
            delay(STATUS_POLL_MS)
        }
    }
    return state
}