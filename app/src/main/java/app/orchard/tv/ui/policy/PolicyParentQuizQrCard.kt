package app.orchard.tv.ui.policy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.Dp
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

private val PrimaryQrSize = 156.dp
private val SecondaryQrSize = 132.dp
private val QrPlaceholder = Color(0xFF6E6E73)

/**
 * The right-hand quiz setup pane: the local parent web page and the optional phone parent app.
 *
 * The two codes sit in opposite corners of the pane (primary top-left, secondary bottom-right)
 * so one camera frame is unlikely to comfortably contain both. Their captions say what each
 * code is for and whether the phone must share the TV's Wi-Fi.
 */
@Composable
internal fun V6QuizSetupPane(
    localMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val web = rememberQuizQr(scale = 12)
    val app = rememberStaticQr(PARENT_APP_DOWNLOAD_URL, scale = 12)

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QrPlate(
                bitmap = web.bitmap,
                loading = web.loading,
                size = PrimaryQrSize,
            ) {
                OfflineQr()
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.widthIn(max = 340.dp)) {
                BasicText(
                    stringResource(R.string.policy_v6_quiz_web_title),
                    style = Type.Headline.copy(fontSize = 18.sp),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    stringResource(R.string.policy_v6_quiz_web_same_wifi),
                    style = Type.Body.copy(fontSize = 14.sp, color = Palette.Label),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(R.string.policy_v6_quiz_web_purpose),
                    style = Type.Caption.copy(fontSize = 12.sp, color = Palette.SecondaryLabel),
                )
                if (localMode) {
                    BasicText(
                        stringResource(R.string.policy_v6_quiz_web_unlock),
                        style = Type.Caption.copy(fontSize = 12.sp, color = Palette.SecondaryLabel),
                    )
                }
            }
        }

        // Keep the download code away from the web code: bottom-right against top-left.
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 300.dp),
                horizontalAlignment = Alignment.End,
            ) {
                BasicText(
                    stringResource(R.string.policy_v6_parent_app_title),
                    style = Type.Headline.copy(fontSize = 17.sp, textAlign = TextAlign.End),
                )
                Spacer(Modifier.height(5.dp))
                BasicText(
                    stringResource(R.string.policy_v6_parent_app_hint),
                    style = Type.Caption.copy(
                        fontSize = 12.sp,
                        color = Palette.SecondaryLabel,
                        textAlign = TextAlign.End,
                    ),
                )
                Spacer(Modifier.height(3.dp))
                BasicText(
                    stringResource(R.string.policy_v6_parent_app_no_wifi),
                    style = Type.Caption.copy(
                        fontSize = 12.sp,
                        color = Palette.SecondaryLabel,
                        textAlign = TextAlign.End,
                    ),
                )
            }
            Spacer(Modifier.width(18.dp))
            QrPlate(
                bitmap = app.bitmap,
                loading = app.loading,
                size = SecondaryQrSize,
            ) {
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
            }
        }
    }
}

@Composable
private fun QrPlate(
    bitmap: ImageBitmap?,
    loading: Boolean,
    size: Dp,
    failed: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(10.dp),
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
                style = Type.Caption.copy(fontSize = 16.sp, color = QrPlaceholder),
            )
            else -> failed()
        }
    }
}

@Composable
private fun OfflineQr() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(Glyph.Offline, QrPlaceholder, 30.dp)
        BasicText(
            stringResource(R.string.policy_quiz_offline),
            style = Type.Caption.copy(
                fontSize = 10.sp,
                color = QrPlaceholder,
                textAlign = TextAlign.Center,
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
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
