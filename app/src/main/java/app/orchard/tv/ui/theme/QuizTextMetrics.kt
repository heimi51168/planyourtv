package app.orchard.tv.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planyourtv.policy.quiz.QuizConfig
import kotlin.math.sqrt

/**
 * The live quiz text sizes. The rest of the launcher keeps [Type] untouched;
 * only the question card and answer rows read this local.
 */
data class QuizTextMetrics(
    val prompt: TextStyle,
    val option: TextStyle,
    val review: TextStyle,
    val timer: TextStyle,
    val optionMinHeight: Dp,
    val markerSize: Dp,
)

/** 42-inch 1080p reference: 38sp prompt and 32sp answers at 3.5m. */
private const val REFERENCE_TV_INCH = 42f
private const val REFERENCE_PROMPT_PX = 76f
private const val REFERENCE_OPTION_PX = 64f

internal fun quizTextMetrics(
    config: QuizConfig?,
    screenHeightPx: Int,
    density: Float,
): QuizTextMetrics {
    val cfg = config ?: QuizConfig()
    val height = screenHeightPx.takeIf { it > 0 } ?: 1080
    val densitySafe = density.takeIf { it > 0f } ?: 2f
    // A 4K panel normally raises the Compose density to 4, so this ratio keeps
    // the physical size steady while still honouring unusual TV densities.
    val resolutionScale = (height / 1080f).coerceIn(0.5f, 4f)
    val tvInch = cfg.quizTvSizeInch.takeIf { it in 24..100 } ?: 42
    // At a roughly fixed living-room distance a larger panel needs a smaller
    // relative font. The square root keeps the correction gentle.
    val sizeScale = if (cfg.quizTextAuto) {
        sqrt(REFERENCE_TV_INCH / tvInch).coerceIn(0.70f, 1.35f)
    } else {
        1f
    }
    val manualScale = (cfg.quizTextScalePct.coerceIn(60, 240) / 100f)
    val scale = if (cfg.quizTextAuto) 1f else manualScale

    val promptSp = (REFERENCE_PROMPT_PX * resolutionScale * sizeScale * scale / densitySafe)
        .coerceIn(20f, 72f)
    val optionSp = (REFERENCE_OPTION_PX * resolutionScale * sizeScale * scale / densitySafe)
        .coerceIn(18f, 60f)
    // Review/explanation text is read at the same distance but can be long; keep it
    // slightly below the answer size so a long solution still fits the card.
    val reviewSp = (optionSp * 0.9f).coerceIn(16f, 56f)
    // The countdown is read from across the room; keep it clearly larger than an answer.
    val timerSp = (optionSp * 1.25f).coerceIn(24f, 64f)

    return QuizTextMetrics(
        prompt = Type.Title.copy(
            fontSize = promptSp.sp,
            lineHeight = (promptSp * 1.21f).sp,
        ),
        option = Type.Body.copy(
            fontSize = optionSp.sp,
            lineHeight = (optionSp * 1.25f).sp,
        ),
        review = Type.Body.copy(
            fontSize = reviewSp.sp,
            lineHeight = (reviewSp * 1.35f).sp,
        ),
        timer = Type.Title.copy(
            fontSize = timerSp.sp,
            lineHeight = (timerSp * 1.1f).sp,
        ),
        optionMinHeight = (optionSp * 2.625f).dp,
        markerSize = (optionSp * 0.875f).dp,
    )
}

val LocalQuizTextMetrics = staticCompositionLocalOf {
    quizTextMetrics(QuizConfig(), screenHeightPx = 1080, density = 2f)
}
