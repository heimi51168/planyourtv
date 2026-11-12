package app.orchard.tv.ui.policy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.wallpaper.PhoneServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val PARENT_APP_DOWNLOAD_URL = "https://planyourtv.me/download/parent"

private val ParentQrSize = 92.dp
private val QrPlaceholder = Color(0xFF6E6E73)

/**
 * The parent web page is also the local-bank setup page and the lock-screen-only
 * temporary-unlock / one-session entry. It exists only while the local engine owns
 * the guard; automatic timing and a disabled quiz keep the bank editable, so neither
 * hides it. The bind pane is excluded because that pane already owns the QR slot.
 */
internal fun quizWebQrVisible(
    childOn: Boolean,
    localMode: Boolean,
    bindPaneOpen: Boolean,
): Boolean = childOn &&
    localMode &&
    !bindPaneOpen

/**
 * The small, non-focusable QR pair below auto update. The first code uses the
 * child quiz screen's loader, so it points at the same stable local page and
 * shows the same offline state. The second is a static download link for the
 * optional phone parent app and stays available without the local server.
 */
@Composable
internal fun QuizWebQrCard(modifier: Modifier = Modifier) {
    val web = rememberQuizQr(scale = 8)
    val app = rememberStaticQr(PARENT_APP_DOWNLOAD_URL, scale = 8)
    val webHint = when {
        web.bitmap != null -> R.string.policy_v6_quiz_web_hint
        web.loading -> R.string.policy_quiz_qr_loading
        else -> R.string.policy_quiz_offline
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Fill.copy(alpha = 0.55f))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QrEntry(
            bitmap = web.bitmap,
            loading = web.loading,
            title = stringResource(R.string.policy_v6_quiz_web_title),
            hint = stringResource(webHint),
            failed = {
                GlyphIcon(Glyph.Offline, Palette.SecondaryLabel, 24.dp)
            },
        )
        QrEntry(
            bitmap = app.bitmap,
            loading = app.loading,
            title = stringResource(R.string.policy_v6_parent_app_title),
            hint = stringResource(R.string.policy_v6_parent_app_hint),
            failed = {
                BasicText(
                    stringResource(R.string.policy_v6_parent_app_qr_failed),
                    style = Type.Caption.copy(
                        fontSize = 9.sp,
                        color = QrPlaceholder,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

@Composable
private fun QrEntry(
    bitmap: ImageBitmap?,
    loading: Boolean,
    title: String,
    hint: String,
    failed: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ParentQrSize)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
                loading -> BasicText(
                    "…",
                    style = Type.Caption.copy(fontSize = 14.sp, color = QrPlaceholder),
                )
                else -> failed()
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(
                title,
                style = Type.Headline.copy(fontSize = 14.sp, color = Palette.Label),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                hint,
                style = Type.Caption.copy(fontSize = 11.sp, color = Palette.SecondaryLabel),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class StaticQr(
    val bitmap: ImageBitmap?,
    val loading: Boolean,
)

@Composable
private fun rememberStaticQr(text: String, scale: Int): StaticQr {
    var qr by remember(text, scale) { mutableStateOf(StaticQr(null, true)) }
    LaunchedEffect(text, scale) {
        val bitmap = withContext(Dispatchers.Default) {
            runCatching { PhoneServer.qr(text, scale = scale).asImageBitmap() }.getOrNull()
        }
        qr = StaticQr(bitmap, false)
    }
    return qr
}
