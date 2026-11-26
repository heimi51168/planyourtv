package app.orchard.tv.ui.policy

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.orchard.tv.quiz.QuizVisualAssetStore
import com.planyourtv.policy.quiz.QuizItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a cached question figure. This is deliberately not focusable: the
 * remote's D-pad path must stay prompt -> options -> confirm/exit, and a
 * picture is information rather than an interactive control.
 *
 * A missing or undecodable file simply renders nothing; the text question
 * remains fully usable.
 */
@Composable
internal fun QuizQuestionVisual(
    item: QuizItem,
    modifier: Modifier = Modifier,
) {
    val rich = item.visual?.rich ?: return
    val file = remember(rich.assetId, rich.sha256) {
        QuizVisualAssetStore.fileFor(rich)
    }
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        rich.assetId,
        rich.sha256,
        file?.path,
    ) {
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(file.path)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val image = bitmap ?: return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = image,
            contentDescription = rich.altText.ifBlank { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        )
    }
}

/**
 * The card width that shows the cached PNG at its own aspect ratio inside
 * [height]. Portrait figures stay wide enough to read; very wide ones leave
 * the question text at least half of the row.
 */
internal fun quizVisualWidth(item: QuizItem, height: Dp, maxWidth: Dp = 380.dp): Dp {
    val rich = item.visual?.rich ?: return 0.dp
    val ratio = rich.width.toFloat() / rich.height.toFloat()
    return (height * ratio).coerceIn(160.dp, maxWidth)
}