package app.orchard.tv.ui.policy

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.orchard.tv.ui.components.isSelect
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.ui.wallpaper.latestBringIntoView
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizPresentation
import kotlinx.coroutines.launch

/**
 * Routes a question body by its presentation layer. The answer semantics stay
 * inside the choice renderer: answerKind only decides whether an option commits
 * immediately or joins a confirmed set. A future picture or geometry question
 * adds one [QuizPresentation] branch and reuses the same page chrome, timer and
 * feedback flow.
 */
@Composable
internal fun QuizQuestionBodyRouter(
    item: QuizItem,
    submitting: Boolean,
    selectedIndices: Set<Int>,
    promptFocus: FocusRequester,
    optionFocuses: List<FocusRequester>,
    confirmFocus: FocusRequester,
    exitFocus: FocusRequester,
    onOptionClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item.presentation) {
        QuizPresentation.CHOICE -> QuizChoiceQuestionBody(
            item = item,
            submitting = submitting,
            selectedIndices = selectedIndices,
            promptFocus = promptFocus,
            optionFocuses = optionFocuses,
            confirmFocus = confirmFocus,
            exitFocus = exitFocus,
            onOptionClick = onOptionClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun QuizChoiceQuestionBody(
    item: QuizItem,
    submitting: Boolean,
    selectedIndices: Set<Int>,
    promptFocus: FocusRequester,
    optionFocuses: List<FocusRequester>,
    confirmFocus: FocusRequester,
    exitFocus: FocusRequester,
    onOptionClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val promptScroll = remember(item.questionId) { ScrollState(0) }
    val optionsScroll = remember(item.questionId) { ScrollState(0) }
    var promptFocused by remember(item.questionId) { mutableStateOf(false) }
    val lastFocus = if (item.isMultipleChoice) confirmFocus else exitFocus
    // Exam layout for questions with a figure: the prompt gets its own
    // full-width strip, then the answers keep the left of the body and the
    // figure sits to their right. Keeping the figure beside the option rows
    // avoids a wide empty card next to a short prompt. Text-only questions
    // keep the original card, width and focus chain unchanged.
    val hasVisual = item.visual?.rich != null
    val visualHeight = 190.dp

    Column(modifier = modifier.fillMaxWidth()) {
        QuizPromptCard(
            item = item,
            promptFocus = promptFocus,
            promptScroll = promptScroll,
            promptFocused = promptFocused,
            optionFocuses = optionFocuses,
            onPromptFocusChanged = { promptFocused = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .quizScrollBar(optionsScroll, false),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(optionsScroll)
                        .latestBringIntoView(optionsScroll)
                        .padding(end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.options.forEachIndexed { index, option ->
                        QuizOptionButton(
                            label = optionLabel(index) + "  " + option,
                            onClick = { onOptionClick(index) },
                            focusRequester = optionFocuses[index],
                            enabled = !submitting,
                            selected = index in selectedIndices,
                            multiple = item.isMultipleChoice,
                            // Up/Left and Down/Right both walk the answer list.
                            // The first answer returns to the prompt; the last
                            // hands over to confirmation (multiple) or exit
                            // (single).
                            onKey = linearDpadFocus(
                                previous = if (index == 0) promptFocus else optionFocuses[index - 1],
                                next = if (index == item.options.lastIndex) {
                                    lastFocus
                                } else {
                                    optionFocuses[index + 1]
                                },
                            ),
                        )
                    }
                }
            }
            if (hasVisual) {
                QuizQuestionVisual(
                    item = item,
                    modifier = Modifier
                        .height(visualHeight)
                        .width(quizVisualWidth(item, visualHeight)),
                )
            }
        }
    }
}

/**
 * The focusable prompt card. It keeps Up/Down for reading and hands Left/Right
 * (or OK) to the answers, so a long prompt never blocks the D-pad path.
 */
@Composable
private fun QuizPromptCard(
    item: QuizItem,
    promptFocus: FocusRequester,
    promptScroll: ScrollState,
    promptFocused: Boolean,
    optionFocuses: List<FocusRequester>,
    onPromptFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 80.dp.toPx() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (promptFocused) Palette.FillStrong else Palette.Fill),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onPromptFocusChanged(it.isFocused) }
                .focusRequester(promptFocus)
                .onKeyEvent { event ->
                    val key = event.nativeKeyEvent.keyCode
                    when (key) {
                        NativeKeyEvent.KEYCODE_DPAD_UP,
                        NativeKeyEvent.KEYCODE_DPAD_DOWN -> {
                            val down = key == NativeKeyEvent.KEYCODE_DPAD_DOWN
                            if (event.type == KeyEventType.KeyDown) {
                                if (down && promptScroll.value >= promptScroll.maxValue) {
                                    if (event.nativeKeyEvent.repeatCount == 0) {
                                        optionFocuses.firstOrNull()?.let {
                                            runCatching { it.requestFocus() }
                                        }
                                    }
                                } else {
                                    val delta = when {
                                        down && promptScroll.value < promptScroll.maxValue -> scrollStep
                                        !down && promptScroll.value > 0 -> -scrollStep
                                        else -> 0f
                                    }
                                    if (delta != 0f) {
                                        scope.launch {
                                            promptScroll.animateScrollBy(
                                                delta,
                                                spring(dampingRatio = 1f, stiffness = 500f),
                                            )
                                        }
                                    }
                                }
                            }
                            true
                        }

                        // The prompt keeps Up/Down for reading. Left/Right skip
                        // straight to the answers, so the buttons are reachable
                        // without scrolling a long prompt to its end.
                        NativeKeyEvent.KEYCODE_DPAD_LEFT,
                        NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (event.type == KeyEventType.KeyDown &&
                                event.nativeKeyEvent.repeatCount == 0
                            ) {
                                optionFocuses.firstOrNull()?.let {
                                    runCatching { it.requestFocus() }
                                }
                            }
                            true
                        }

                        else -> if (event.isSelect()) {
                            if (event.type == KeyEventType.KeyDown) {
                                optionFocuses.firstOrNull()?.let {
                                    runCatching { it.requestFocus() }
                                }
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
                .focusProperties { canFocus = true }
                .focusable()
                .quizScrollBar(promptScroll, promptFocused)
                .verticalScroll(promptScroll)
                .padding(start = 18.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        ) {
            BasicText(
                item.prompt,
                style = Type.Title,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal fun optionLabel(index: Int): String =
    if (index in 0..5) ('A' + index).toString() else (index + 1).toString()
