package app.orchard.tv.ui.policy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
import com.planyourtv.policy.LocalRules

private val WebQrSize = 280.dp
private val QrPlaceholder = Color(0xFF6E6E73)

/**
 * V29 local pane: the one phone QR for the web parent center.
 *
 * The page is the only local configuration surface. This pane is display-only; the TV does not
 * offer an editor next to it. The same code disappears once remote management is bound.
 */
@Composable
internal fun V6ParentWebQrPane(
    rules: LocalRules,
    dailyUsedSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val web = rememberQuizQr(scale = 12, fragment = "#parent")
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        QrPlate(
            bitmap = web.bitmap,
            loading = web.loading,
            size = WebQrSize,
        ) {
            OfflineQr()
        }
        Spacer(Modifier.height(16.dp))
        BasicText(
            stringResource(R.string.policy_v29_qr_title),
            style = Type.Headline.copy(fontSize = 19.sp, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            stringResource(R.string.policy_v29_qr_same_wifi),
            style = Type.Body.copy(
                fontSize = 14.sp,
                color = Palette.Label,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(14.dp))
        BasicText(
            stringResource(R.string.policy_v29_ro_rules),
            style = Type.Caption.copy(
                fontSize = 12.sp,
                color = Palette.SecondaryLabel,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            stringResource(
                R.string.policy_v29_ro_windows,
                rules.weekdayWindows.size,
                rules.weekendWindows.size,
            ),
            style = Type.Caption.copy(
                fontSize = 12.sp,
                color = Palette.Label,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(2.dp))
        BasicText(
            stringResource(
                R.string.policy_v29_ro_used,
                (dailyUsedSeconds / 60L).toInt().coerceAtLeast(0),
            ),
            style = Type.Caption.copy(
                fontSize = 12.sp,
                color = Palette.Label,
                textAlign = TextAlign.Center,
            ),
        )
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
