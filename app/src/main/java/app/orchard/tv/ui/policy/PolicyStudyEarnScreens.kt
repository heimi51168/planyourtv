package app.orchard.tv.ui.policy

import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.dismissOnTap
import app.orchard.tv.ui.components.glassEdge
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.home.MiniTile
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.StudyRoundUi
import com.planyourtv.policy.UnlockRoute
import com.planyourtv.policy.UnlockRouteDecision
import com.planyourtv.policy.UnlockRoutePlan
import com.planyourtv.policy.UnlockRouteReason
import java.util.Locale
import kotlin.math.roundToInt

// The member sheet's corner, so a route choice and the face sheet read as the same surface.
private val StudyEarnCorner = 26.dp

/** The same reasons, for a rejected start where time passed between drawing the row and OK. */
internal fun studyEarnRejectMessage(reason: RejectReason): Int = when (reason) {
    RejectReason.RESTING -> R.string.policy_resting_title
    RejectReason.OUTSIDE_WINDOW -> R.string.policy_outside_window
    RejectReason.DAILY_LIMIT -> R.string.policy_focus_study_daily_limit
    RejectReason.NO_TIMED_APPS, RejectReason.INVALID_SCOPE -> R.string.policy_no_timed_apps_title
    else -> R.string.policy_action_rejected
}

/**
 * Resume failures: the round already exists, so the common refusal is that the window can no
 * longer hold the remaining study time plus the reward. Say that instead of the generic
 * "outside the allowed time", which reads wrong while the window is still open.
 */
internal fun studyEarnResumeMessage(reason: RejectReason): Int = when (reason) {
    RejectReason.OUTSIDE_WINDOW -> R.string.policy_focus_study_window_short
    else -> studyEarnRejectMessage(reason)
}

/**
 * The unlock choice, drawn as a modal over the live child desktop. The dock, clock, icons and
 * wallpaper stay composed underneath, including the same film player that was already running,
 * so opening and closing the choice never restarts the wallpaper or needs a second copy of its
 * state. The caller pauses the rotation while this is open; the current picture keeps showing.
 *
 * The scrim blocks taps on the dock, and the rows plus Back form a closed D-pad chain, so focus
 * cannot walk out to the tiles behind the glass. The card uses the same smoked pane as the quiz
 * level picker, so the live desktop reads through it instead of being covered by a solid sheet.
 */
@Composable
internal fun PolicyUnlockChoiceOverlay(
    target: AppEntry?,
    plan: UnlockRoutePlan,
    studyApps: List<AppEntry>,
    quizFeatureAvailable: Boolean,
    studyMinutes: Int,
    fruitBalanceHundredths: Long,
    fruitRateMinutes: Int,
    messageRes: Int?,
    onQuiz: () -> Unit,
    onStudy: (AppEntry) -> Unit,
    onDirect: () -> Unit,
    onFruit: () -> Unit,
    onBack: () -> Unit,
) {
    val quiz = plan.quiz
    val study = plan.studyReward
    val direct = plan.direct
    val fruit = plan.fruit
    // A TV input is not a study target: the route stays visible so the child sees why, but it
    // can never start from here.
    val studyApplicable = target != null
    val quizEnabled = quiz.available && quizFeatureAvailable
    val studyEnabled = study.available && studyApplicable && studyApps.isNotEmpty()
    val directEnabled = direct.available
    val fruitEnabled = fruit.available
    val visibleRoutes = plan.decisions.filter { it.visible }
    val inlineStudyApps = studyApps.size > 1
    val quizFocus = remember { FocusRequester() }
    val studyFocus = remember { FocusRequester() }
    val directFocus = remember { FocusRequester() }
    val fruitFocus = remember { FocusRequester() }
    val studyAppFocuses = remember(studyApps.size) {
        List(studyApps.size) { FocusRequester() }
    }
    val backFocus = remember { FocusRequester() }
    // Multiple study apps are listed directly in the card. Only the actionable rows take
    // focus, so Up/Down never stops on the grey header or a disabled route.
    val enabledFocuses = buildList {
        if (quizEnabled) add(quizFocus)
        if (studyEnabled) {
            if (inlineStudyApps) addAll(studyAppFocuses) else add(studyFocus)
        }
        if (directEnabled) add(directFocus)
        if (fruitEnabled) add(fruitFocus)
    }
    fun focusKeys(focus: FocusRequester): ((KeyEvent) -> Boolean)? {
        val index = enabledFocuses.indexOf(focus)
        if (index < 0) return null
        return linearDpadFocus(
            previous = enabledFocuses.getOrNull(index - 1),
            next = enabledFocuses.getOrNull(index + 1) ?: backFocus,
        )
    }
    val backKeys = linearDpadFocus(
        previous = enabledFocuses.lastOrNull(),
        next = enabledFocuses.lastOrNull(),
    )
    val quizMeta = unlockCountText(quiz)
    // A disabled row keeps only its explaining facts in the grey hint; the blocking reason
    // itself is lifted into an orange notice so it cannot be mistaken for a description.
    val quizNotice = if (!quizEnabled) {
        if (quiz.available) {
            stringResource(R.string.policy_route_quiz_unavailable)
        } else {
            unlockRouteReasonText(quiz.reason)
        }
    } else {
        null
    }
    val quizHint = hint(
        unlockGrantText(quiz),
        stringResource(R.string.policy_route_quiz_hint),
    )
    val studyBase = stringResource(
        R.string.policy_route_study_hint,
        studyMinutes,
        (study.requestedSeconds / 60L).toInt(),
    )
    val studyReason = when {
        !studyApplicable -> stringResource(R.string.policy_route_study_inputs_unavailable)
        !study.available -> unlockRouteReasonText(study.reason)
        studyApps.isEmpty() -> stringResource(R.string.policy_route_study_not_configured)
        else -> null
    }
    val studyMeta = unlockCountText(study)
    val studyNotice = studyReason
    val studyHint = hint(
        unlockGrantText(study),
        studyMeta,
        studyBase,
        if (studyApps.size > 1) stringResource(R.string.policy_route_study_pick) else null,
    )
    val studyRowHint = hint(unlockGrantText(study), studyBase)
    val directMeta = unlockCountText(direct)
    val directNotice = if (!directEnabled) unlockRouteReasonText(direct.reason) else null
    val directHint = hint(
        unlockGrantText(direct),
        stringResource(
            R.string.policy_route_direct_hint,
            (direct.requestedSeconds / 60L).toInt(),
        ),
    )
    // The TV only ever offers whole fruits; the fractional tail stays in the Web ledger.
    val fruitNotice = if (!fruitEnabled) unlockRouteReasonText(fruit.reason) else null
    val fruitHint = hint(
        if (fruitRateMinutes > 0) {
            stringResource(
                R.string.policy_fruit_available,
                (fruitBalanceHundredths / 100L).coerceAtLeast(0L).toInt(),
                fruitRateMinutes,
            )
        } else {
            null
        },
    )
    val fruitMeta = unlockCountText(fruit)
    // Only the card enters; the desktop and its film keep running behind the static shade.
    val reducedMotion = LocalReducedMotion.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enter by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 240, easing = Motion.EaseOut),
        label = "unlock-choice-enter",
    )
    val cardEnter = if (reducedMotion) 1f else enter
    BackHandler { onBack() }
    LaunchedEffect(
        quizEnabled,
        studyEnabled,
        directEnabled,
        fruitEnabled,
        visibleRoutes.size,
        studyApps.size,
    ) {
        withFrameNanos { }
        runCatching { (enabledFocuses.firstOrNull() ?: backFocus).requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .dismissOnTap { onBack() }
            // The member sheet's near-black scrim, so the focused white plate keeps its contrast
            // over a bright wallpaper. The desktop stays composed behind it and the film keeps
            // running, but the shade makes the choice the only readable surface.
            .background(Palette.OnFocus.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .widthIn(max = 520.dp)
                .heightIn(max = 500.dp)
                // A tap on the card must not fall through to the scrim and dismiss the choice.
                .consumeTaps()
                .graphicsLayer {
                    alpha = cardEnter
                    scaleX = 0.94f + 0.06f * cardEnter
                    scaleY = 0.94f + 0.06f * cardEnter
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .choiceShellGlass(StudyEarnCorner),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        stringResource(R.string.policy_unlock_choice_title),
                        style = Type.LargeTitle.copy(
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(1.dp))
                    BasicText(
                        target?.let {
                            stringResource(R.string.policy_unlock_choice_for, it.label)
                        } ?: stringResource(R.string.cc_inputs),
                        style = Type.Caption.copy(
                            textAlign = TextAlign.Center,
                            color = Palette.Label.copy(alpha = 0.82f),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        // The rows keep the height they need; only a long list scrolls. The
                        // centred card is 500 dp tall at most, leaving room for the title and
                        // the Back button even when an error line is showing.
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        visibleRoutes.isEmpty() -> {
                            BasicText(
                                stringResource(R.string.policy_no_unlock_route),
                                style = Type.Body.copy(
                                    lineHeight = 20.sp,
                                    color = Palette.SecondaryLabel,
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                        else -> {
                            if (quiz.visible) {
                                UnlockChoiceRow(
                                    title = stringResource(R.string.policy_unlock_choice_quiz),
                                    hint = quizHint,
                                    notice = quizNotice,
                                    meta = quizMeta,
                                    enabled = quizEnabled,
                                    focusRequester = quizFocus,
                                    onKey = focusKeys(quizFocus),
                                    onClick = onQuiz,
                                )
                            }
                            if (study.visible) {
                                if (inlineStudyApps) {
                                    StudyRouteHeader(
                                        title = stringResource(R.string.policy_route_study_title),
                                        hint = studyHint,
                                        notice = studyNotice,
                                        enabled = studyEnabled,
                                    )
                                    studyApps.forEachIndexed { index, app ->
                                        StudyAppChoiceRow(
                                            app = app,
                                            enabled = studyEnabled,
                                            focusRequester = studyAppFocuses[index],
                                            onKey = focusKeys(studyAppFocuses[index]),
                                            onClick = { onStudy(app) },
                                        )
                                    }
                                } else {
                                    UnlockChoiceRow(
                                        title = stringResource(R.string.policy_route_study_title),
                                        hint = studyRowHint,
                                        notice = studyNotice,
                                        meta = studyMeta,
                                        enabled = studyEnabled,
                                        focusRequester = studyFocus,
                                        onKey = focusKeys(studyFocus),
                                        onClick = { studyApps.firstOrNull()?.let(onStudy) },
                                        icon = { focused ->
                                            studyApps.firstOrNull()?.let { app ->
                                                MiniTile(
                                                    app,
                                                    focused = focused,
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                            if (direct.visible) {
                                UnlockChoiceRow(
                                    title = stringResource(R.string.policy_route_direct_title),
                                    hint = directHint,
                                    notice = directNotice,
                                    meta = directMeta,
                                    enabled = directEnabled,
                                    focusRequester = directFocus,
                                    onKey = focusKeys(directFocus),
                                    onClick = onDirect,
                                )
                            }
                            if (fruit.visible) {
                                UnlockChoiceRow(
                                    title = stringResource(R.string.policy_fruit_title),
                                    hint = fruitHint,
                                    notice = fruitNotice,
                                    meta = fruitMeta,
                                    enabled = fruitEnabled,
                                    focusRequester = fruitFocus,
                                    onKey = focusKeys(fruitFocus),
                                    onClick = onFruit,
                                )
                            }
                        }
                    }
                }
                messageRes?.let {
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        stringResource(it),
                        style = Type.Caption.copy(color = Palette.Orange),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                ChoiceBackRow(
                    onClick = onBack,
                    focusRequester = backFocus,
                    onKey = backKeys,
                )
            }
        }
    }
}

@Composable
private fun unlockGrantText(decision: UnlockRouteDecision): String = when {
    decision.grantSeconds <= 0L -> stringResource(R.string.policy_route_no_minutes)
    decision.grantSeconds < 60L -> stringResource(R.string.policy_route_less_than_minute)
    else -> stringResource(
        R.string.policy_route_max_minutes,
        (decision.grantSeconds / 60L).toInt(),
    )
}

@Composable
private fun unlockCountText(decision: UnlockRouteDecision): String =
    if (decision.remaining < 0) {
        stringResource(R.string.policy_route_remaining_unlimited)
    } else {
        stringResource(R.string.policy_route_remaining_count, decision.remaining)
    }

@Composable
private fun unlockRouteReasonText(reason: UnlockRouteReason): String? = when (reason) {
    UnlockRouteReason.READY,
    UnlockRouteReason.SWITCH_OFF,
    -> null
    UnlockRouteReason.INVALID_RULES -> stringResource(R.string.policy_route_invalid_rules)
    UnlockRouteReason.STUDY_NOT_CONFIGURED ->
        stringResource(R.string.policy_route_study_not_configured)
    UnlockRouteReason.OUTSIDE_WINDOW -> stringResource(R.string.policy_outside_window)
    UnlockRouteReason.RESTING -> stringResource(R.string.policy_resting_title)
    UnlockRouteReason.BUSY -> stringResource(R.string.policy_route_busy)
    UnlockRouteReason.COUNT_ZERO -> stringResource(R.string.policy_route_count_zero)
    UnlockRouteReason.COUNT_EXHAUSTED -> stringResource(R.string.policy_route_count_exhausted)
    UnlockRouteReason.QUOTA_EXHAUSTED -> stringResource(R.string.policy_route_quota_exhausted)
    UnlockRouteReason.NOT_EARNED -> stringResource(R.string.policy_route_not_earned)
    UnlockRouteReason.ACCOUNT_DISABLED ->
        stringResource(R.string.policy_fruit_account_disabled)
    UnlockRouteReason.BALANCE_EMPTY -> stringResource(R.string.policy_fruit_balance_empty)
    UnlockRouteReason.BALANCE_BELOW_ONE ->
        stringResource(R.string.policy_fruit_balance_below_one)
    UnlockRouteReason.CAP_TOO_SMALL -> stringResource(R.string.policy_fruit_cap_too_small)
    UnlockRouteReason.WINDOW_TOO_SHORT ->
        stringResource(R.string.policy_fruit_window_too_short)
    UnlockRouteReason.EXCHANGE_TOO_LARGE ->
        stringResource(R.string.policy_fruit_exchange_too_large)
}

private fun hint(vararg parts: String?): String =
    parts.filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ")

/**
 * V58 whole-fruit wheel. [maxFruits] already carries every ceiling (balance, session cap, daily
 * count, remaining window), so this panel never recomputes policy. OK exchanges and starts in
 * one step; the warning says explicitly that ending early does not refund the fruits.
 */
@Composable
internal fun PolicyFruitExchangeOverlay(
    targetLabel: String?,
    maxFruits: Int,
    remainingTries: Int,
    balanceFruitHundredths: Long,
    rateMinutesPerFruit: Int,
    messageRes: Int?,
    /** Hosted-only state: the orchard is processing the exchange; OK is ignored meanwhile. */
    submitting: Boolean = false,
    /** The orchard's own rejection text, shown instead of the generic [messageRes]. */
    messageText: String? = null,
    onExchange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val limit = maxFruits.coerceAtLeast(1)
    var selected by remember { mutableStateOf(1) }
    LaunchedEffect(maxFruits) {
        selected = selected.coerceIn(1, limit)
    }
    val focus = remember { FocusRequester() }
    var wheelFocused by remember { mutableStateOf(false) }
    val canExchange = maxFruits >= 1
    val selectedLabel = stringResource(R.string.policy_fruit_count, selected)
    val selectedMinutes = (selected.toLong() * rateMinutesPerFruit.toLong())
        .coerceAtLeast(0L)
        .toInt()
    val wholeBalance = (balanceFruitHundredths / 100L).coerceAtLeast(0L).toInt()
    val maxMinutes = (maxFruits.toLong() * rateMinutesPerFruit.toLong())
        .coerceAtLeast(0L)
        .toInt()
    val remainingText = if (remainingTries < 0) {
        stringResource(R.string.policy_route_remaining_unlimited)
    } else {
        stringResource(R.string.policy_route_remaining_count, remainingTries)
    }
    val reducedMotion = LocalReducedMotion.current
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val enter by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 240, easing = Motion.EaseOut),
        label = "fruit-exchange-enter",
    )
    val cardEnter = if (reducedMotion) 1f else enter
    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .dismissOnTap { onBack() }
            .background(Palette.OnFocus.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .widthIn(max = 460.dp)
                .consumeTaps()
                .graphicsLayer {
                    alpha = cardEnter
                    scaleX = 0.94f + 0.06f * cardEnter
                    scaleY = 0.94f + 0.06f * cardEnter
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .choiceShellGlass(StudyEarnCorner),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp)
                    .focusRequester(focus)
                    .onFocusChanged { wheelFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        // Consume both key-down and key-up for every D-pad direction: Compose's
                        // default focus search otherwise runs on the key-up and hands focus to
                        // the desktop behind this modal.
                        when (event.nativeKeyEvent.keyCode) {
                            NativeKeyEvent.KEYCODE_DPAD_UP,
                            NativeKeyEvent.KEYCODE_DPAD_RIGHT,
                            -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    val step = when {
                                        event.nativeKeyEvent.repeatCount >= 16 -> 10
                                        event.nativeKeyEvent.repeatCount >= 8 -> 5
                                        event.nativeKeyEvent.repeatCount >= 3 -> 2
                                        else -> 1
                                    }
                                    selected = (selected + step).coerceAtMost(limit)
                                }
                                true
                            }
                            NativeKeyEvent.KEYCODE_DPAD_DOWN,
                            NativeKeyEvent.KEYCODE_DPAD_LEFT,
                            -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    val step = when {
                                        event.nativeKeyEvent.repeatCount >= 16 -> 10
                                        event.nativeKeyEvent.repeatCount >= 8 -> 5
                                        event.nativeKeyEvent.repeatCount >= 3 -> 2
                                        else -> 1
                                    }
                                    selected = (selected - step).coerceAtLeast(1)
                                }
                                true
                            }
                            NativeKeyEvent.KEYCODE_DPAD_CENTER,
                            NativeKeyEvent.KEYCODE_ENTER,
                            NativeKeyEvent.KEYCODE_NUMPAD_ENTER,
                            -> {
                                if (event.type == KeyEventType.KeyDown &&
                                    event.nativeKeyEvent.repeatCount == 0 &&
                                    canExchange && !submitting
                                ) {
                                    onExchange(selected)
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .semantics { contentDescription = selectedLabel }
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    stringResource(R.string.policy_fruit_title),
                    style = Type.LargeTitle.copy(fontSize = 20.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    targetLabel?.let {
                        stringResource(R.string.policy_unlock_choice_for, it)
                    } ?: stringResource(R.string.cc_inputs),
                    style = Type.Caption.copy(
                        textAlign = TextAlign.Center,
                        color = Palette.Label.copy(alpha = 0.82f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                BasicText(
                    stringResource(
                        R.string.policy_fruit_available,
                        wholeBalance,
                        rateMinutesPerFruit,
                    ),
                    style = Type.Caption.copy(color = Palette.Label.copy(alpha = 0.86f)),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "▲",
                    style = Type.Caption.copy(color = Palette.Label.copy(alpha = 0.35f)),
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        for (offset in -2..2) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (offset == 0) {
                                    Box(
                                        Modifier
                                            .width(230.dp)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            // The plate is the wheel's focus feedback too: a
                                            // lost focus dims it instead of looking active.
                                            .background(
                                                if (wheelFocused) Palette.Focus else Palette.FillStrong
                                            ),
                                    )
                                }
                                val item = selected + offset
                                if (item in 1..limit) {
                                    BasicText(
                                        stringResource(R.string.policy_fruit_count, item),
                                        style = when (offset) {
                                            0 -> Type.LargeTitle.copy(
                                                fontSize = 26.sp,
                                                color = if (wheelFocused) {
                                                    Palette.OnFocus
                                                } else {
                                                    Palette.Label
                                                },
                                            )
                                            -1, 1 -> Type.Title.copy(
                                                fontSize = 17.sp,
                                                color = Palette.Label.copy(alpha = 0.55f),
                                            )
                                            else -> Type.Title.copy(
                                                fontSize = 15.sp,
                                                color = Palette.Label.copy(alpha = 0.28f),
                                            )
                                        },
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
                BasicText(
                    "▼",
                    style = Type.Caption.copy(color = Palette.Label.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(R.string.policy_fruit_minutes, selectedMinutes),
                    style = Type.Title.copy(
                        color = Palette.Label,
                        textAlign = TextAlign.Center,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    hint(
                        if (canExchange) {
                            stringResource(R.string.policy_fruit_max, maxFruits, maxMinutes)
                        } else {
                            stringResource(R.string.policy_fruit_changed)
                        },
                        remainingText,
                    ),
                    style = Type.Caption.copy(
                        color = Palette.SecondaryLabel,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val messageLine = when {
                    submitting -> stringResource(R.string.policy_fruit_submitting)
                    messageText != null -> messageText
                    messageRes != null -> stringResource(messageRes)
                    !canExchange -> stringResource(R.string.policy_fruit_changed)
                    else -> null
                }
                messageLine?.let {
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        it,
                        style = Type.Caption.copy(
                            color = if (submitting) Palette.SecondaryLabel else Palette.Orange,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                BasicText(
                    stringResource(R.string.policy_fruit_no_refund),
                    style = Type.Caption.copy(
                        color = Palette.SecondaryLabel,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        stringResource(
                            if (submitting) {
                                R.string.policy_fruit_submitting_hint
                            } else {
                                R.string.policy_fruit_ok
                            },
                        ),
                        style = Type.Caption.copy(
                            color = if (submitting) {
                                Palette.SecondaryLabel
                            } else {
                                Palette.Label
                            },
                        ),
                        maxLines = 1,
                    )
                    BasicText(
                        stringResource(R.string.policy_fruit_cancel),
                        style = Type.Caption.copy(color = Palette.SecondaryLabel),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}


/**
 * The same smoked pane as the member sheet, kept self-contained: the child wallpaper can be a
 * film rendered by a SurfaceView behind the window, so the Aerial glass that samples a frosted
 * wallpaper would show the owner's picture instead. A fixed gradient stays correct over both a
 * picture and a film.
 */
private fun Modifier.choiceShellGlass(corner: Dp): Modifier = this
    .clip(RoundedCornerShape(corner))
    .drawWithCache {
        val radius = corner.toPx()
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        val edge = glassEdge(w, h, radius, 1.2.dp.toPx())
        val shell = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.56f),
            1f to Color.Black.copy(alpha = 0.70f),
        )
        onDrawBehind {
            drawRoundRect(brush = shell, cornerRadius = CornerRadius(radius, radius))
            drawImage(edge, dstSize = IntSize(w, h), alpha = 0.85f)
        }
    }

@Composable
private fun UnlockChoiceRow(
    title: String,
    hint: String,
    /**
     * The blocking reason of a disabled route. It is rendered in the palette's orange ahead of
     * the grey explanation: grey is a description, orange is the part the child must notice.
     */
    notice: String? = null,
    meta: String? = null,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onKey: ((KeyEvent) -> Boolean)? = null,
    onClick: () -> Unit,
    icon: (@Composable (Boolean) -> Unit)? = null,
) {
    Pressable(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        onKey = onKey,
        focusedScale = 1.02f,
        modifier = Modifier.fillMaxWidth(),
    ) { state ->
        // The member sheet's selection: the whole plate turns near-white and its text flips to
        // the dark content colour. A rim alone was hard to read now that the scrim behind the
        // card is the darker one.
        val focused = state.focused && enabled
        val background by animateColorAsState(
            when {
                !enabled -> Color.White.copy(alpha = 0.06f)
                focused -> Palette.Focus
                else -> Color.White.copy(alpha = 0.10f)
            },
            tween(160, easing = Motion.EaseOut),
            label = "choice-card-bg",
        )
        val foreground = when {
            !enabled -> Palette.SecondaryLabel.copy(alpha = 0.72f)
            focused -> Palette.OnFocus
            else -> Palette.Label
        }
        val metaColor = when {
            !enabled -> Palette.SecondaryLabel.copy(alpha = 0.65f)
            focused -> Palette.OnFocus.copy(alpha = 0.92f)
            else -> Palette.Label.copy(alpha = 0.92f)
        }
        val hintColor = when {
            !enabled -> Palette.SecondaryLabel.copy(alpha = 0.58f)
            focused -> Palette.OnFocus.copy(alpha = 0.72f)
            else -> Palette.Label.copy(alpha = 0.82f)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon(state.focused && enabled)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        title,
                        style = Type.Body.copy(
                            color = foreground,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (meta != null) {
                        Spacer(Modifier.width(8.dp))
                        BasicText(
                            meta,
                            style = Type.Caption.copy(
                                color = metaColor,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                val hintText = if (notice.isNullOrEmpty()) {
                    AnnotatedString(hint)
                } else {
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = Palette.Orange,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append(notice)
                        }
                        if (hint.isNotEmpty()) {
                            withStyle(SpanStyle(color = hintColor)) {
                                append(" · ")
                                append(hint)
                            }
                        }
                    }
                }
                BasicText(
                    hintText,
                    style = Type.Caption.copy(
                        lineHeight = 15.sp,
                        color = hintColor,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The study route's own header when several study apps are configured. The apps are listed
 * directly below it, so the child does not have to open a second sheet just to see the choices.
 */
@Composable
private fun StudyRouteHeader(
    title: String,
    hint: String,
    notice: String? = null,
    enabled: Boolean,
) {
    val hintColor = if (enabled) {
        Palette.Label.copy(alpha = 0.82f)
    } else {
        Palette.SecondaryLabel.copy(alpha = 0.58f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp),
    ) {
        BasicText(
            title,
            style = Type.Body.copy(
                color = if (enabled) Palette.Label
                else Palette.SecondaryLabel.copy(alpha = 0.72f),
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        val hintText = if (notice.isNullOrEmpty()) {
            AnnotatedString(hint)
        } else {
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = Palette.Orange,
                        fontWeight = FontWeight.SemiBold,
                    ),
                ) {
                    append(notice)
                }
                if (hint.isNotEmpty()) {
                    withStyle(SpanStyle(color = hintColor)) {
                        append(" · ")
                        append(hint)
                    }
                }
            }
        }
        BasicText(
            hintText,
            style = Type.Caption.copy(
                lineHeight = 15.sp,
                color = hintColor,
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One study app in the inline list: icon first so the child recognises it without reading. */
@Composable
private fun StudyAppChoiceRow(
    app: AppEntry,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onKey: ((KeyEvent) -> Boolean)?,
    onClick: () -> Unit,
) {
    Pressable(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        onKey = onKey,
        focusedScale = 1.02f,
        modifier = Modifier.fillMaxWidth(),
    ) { state ->
        val focused = state.focused && enabled
        val background by animateColorAsState(
            when {
                !enabled -> Color.White.copy(alpha = 0.06f)
                focused -> Palette.Focus
                else -> Color.White.copy(alpha = 0.10f)
            },
            tween(160, easing = Motion.EaseOut),
            label = "study-app-card-bg",
        )
        val foreground = when {
            !enabled -> Palette.SecondaryLabel.copy(alpha = 0.72f)
            focused -> Palette.OnFocus
            else -> Palette.Label
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniTile(app, focused = focused, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            BasicText(
                app.label,
                style = Type.Body.copy(
                    color = foreground,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The member sheet's Close button, so both sheets dismiss with the same white-on-focus pill. */
@Composable
private fun ChoiceBackRow(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onKey: (KeyEvent) -> Boolean,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        LockButton(
            label = stringResource(R.string.policy_back),
            onClick = onClick,
            width = 200.dp,
            focusRequester = focusRequester,
            primary = true,
            onKey = onKey,
        )
    }
}

/** The ring is either the reward countdown or, when a reward is impossible, the study session. */
internal data class FocusRingCountdown(
    val remainingSeconds: Long,
    val totalSeconds: Long,
    val reward: Boolean,
)

internal fun focusRingCountdown(round: StudyRoundUi): FocusRingCountdown {
    val rewardCountdown = round.rewardReachable && !round.rewardAttempted
    return if (rewardCountdown) {
        FocusRingCountdown(
            remainingSeconds = (round.rewardAtSeconds - round.studiedSeconds).coerceAtLeast(0L),
            totalSeconds = round.rewardAtSeconds,
            reward = true,
        )
    } else {
        FocusRingCountdown(
            remainingSeconds = round.remainingSeconds.coerceAtLeast(0L),
            totalSeconds = round.totalSeconds.coerceAtLeast(0L),
            reward = false,
        )
    }
}

/**
 * One line under the ring. A plain study app only reports its counted time; a reward-enabled
 * app shows progress toward the single reward attempt, and says plainly when that attempt found
 * entertainment unavailable and studying continued without a reward. A round started too close
 * to the end of its study window says that the reward cannot be reached instead of promising it.
 */
@Composable
private fun studyRoundProgressText(round: StudyRoundUi): String {
    val studiedMinutes = (round.studiedSeconds / 60L).toInt()
    return when {
        !round.rewardEnabled ->
            stringResource(R.string.policy_focus_study_counted, studiedMinutes)
        !round.rewardReachable ->
            stringResource(R.string.policy_focus_study_reward_unreachable)
        round.rewardAttempted ->
            stringResource(R.string.policy_focus_study_reward_missed)
        else -> {
            val rewardAt = (round.rewardAtSeconds / 60L).toInt().coerceAtLeast(1)
            val rewardMinutes = (round.rewardSeconds / 60L).toInt()
            stringResource(
                R.string.policy_focus_study_reward_progress,
                studiedMinutes.coerceAtMost(rewardAt),
                rewardAt,
                rewardMinutes,
            )
        }
    }
}

/**
 * The focus desktop shown while Aerial is in front during a study round. The round is only a
 * timer: leaving the study app returns here and the runtime freezes the countdown, so the ring
 * stands still until 继续学习. Screen off also pauses and never resumes by itself.
 *
 * [onContinue] resumes and launches; it returns a message resource when the runtime refuses
 * (window closed or the day's quota is gone), or null once the study app was launched.
 */
@Composable
internal fun PolicyFocusStudyScreen(
    round: StudyRoundUi,
    studyApp: AppEntry?,
    childWallpaper: PolicyChildWallpaper?,
    onContinue: () -> Int?,
    onExit: () -> Unit,
) {
    var confirmExit by remember { mutableStateOf(false) }
    var messageRes by remember { mutableStateOf<Int?>(null) }
    // A successful resume clears the refusal from the previous press.
    LaunchedEffect(round.paused) {
        if (!round.paused) messageRes = null
    }
    if (confirmExit) {
        FocusExitPrompt(
            onCancel = { confirmExit = false },
            onConfirm = onExit,
        )
        return
    }
    val continueFocus = remember { FocusRequester() }
    val exitFocus = remember { FocusRequester() }
    // Aerial resumes with the round already paused; ask for 继续学习 again so the first OK after
    // a return or a wake lands on the button the child expects.
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(studyApp?.packageName, round.paused, resumeTick) {
        withFrameNanos { }
        runCatching {
            if (studyApp == null) exitFocus.requestFocus() else continueFocus.requestFocus()
        }
    }
    BackHandler { confirmExit = true }
    val ring = focusRingCountdown(round)
    Box(Modifier.fillMaxSize()) {
        PolicyPreparingBackdrop(childWallpaper, childActive = true)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)))
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 56.dp, top = 40.dp),
        ) {
            BasicText(
                stringResource(R.string.policy_focus_study_title),
                style = Type.Title.copy(
                    fontSize = 21.sp,
                    color = Palette.SecondaryLabel,
                    shadow = FocusTextShadow,
                ),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 56.dp, top = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LockButton(
                label = stringResource(R.string.policy_focus_study_exit),
                onClick = { confirmExit = true },
                width = 168.dp,
                focusRequester = exitFocus,
                labelStyle = Type.Headline.copy(fontSize = 16.sp),
                onKey = linearDpadFocus(previous = continueFocus, next = continueFocus),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StudyCountdownRing(remaining = ring.remainingSeconds, total = ring.totalSeconds)
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(
                    if (ring.reward) {
                        R.string.policy_focus_study_reward_countdown_label
                    } else {
                        R.string.policy_focus_study_session_countdown_label
                    },
                ),
                style = Type.Caption.copy(
                    fontSize = 13.sp,
                    color = Palette.SecondaryLabel,
                    shadow = FocusTextShadow,
                ),
            )
            if (round.paused) {
                Spacer(Modifier.height(12.dp))
                BasicText(
                    stringResource(R.string.policy_focus_study_paused),
                    style = Type.Headline.copy(
                        fontSize = 15.sp,
                        color = Palette.Orange,
                        shadow = FocusTextShadow,
                    ),
                )
            }
            Spacer(Modifier.height(14.dp))
            BasicText(
                studyRoundProgressText(round),
                style = Type.Body.copy(
                    fontSize = 14.sp,
                    color = Palette.SecondaryLabel,
                    textAlign = TextAlign.Center,
                    shadow = FocusTextShadow,
                ),
                modifier = Modifier.widthIn(max = 640.dp),
            )
            Spacer(Modifier.height(24.dp))
            LockButton(
                label = stringResource(R.string.policy_focus_study_continue),
                onClick = { messageRes = onContinue() },
                width = 220.dp,
                focusRequester = continueFocus,
                primary = true,
                enabled = studyApp != null,
                height = 52.dp,
                labelStyle = Type.Headline.copy(fontSize = 18.sp),
                onKey = linearDpadFocus(previous = exitFocus, next = exitFocus),
            )
        }
        BasicText(
            text = messageRes?.let { stringResource(it) }
                ?: stringResource(R.string.policy_focus_study_hint),
            style = Type.Caption.copy(
                fontSize = 13.sp,
                color = if (messageRes != null) Palette.Orange else Palette.SecondaryLabel,
                textAlign = TextAlign.Center,
                shadow = FocusTextShadow,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp)
                .widthIn(max = 700.dp),
        )
    }
}

/**
 * The deliberate first step of a study-to-earn round. No round exists yet: it starts only when
 * the child presses 开始专注, so a stray OK on the study tile cannot launch the study app or arm
 * a timer. 退出 goes back to the lock desktop without starting anything. The route from an
 * entertainment app keeps its immediate start and never shows this page.
 *
 * [onStart] starts the round and launches the app; it returns a message resource when the
 * runtime refuses, or null once the launch was made.
 */
@Composable
internal fun PolicyStudyFocusIntroScreen(
    studyApp: AppEntry,
    studyMinutes: Int,
    rewardMinutes: Int,
    childWallpaper: PolicyChildWallpaper?,
    onStart: () -> Int?,
    onExit: () -> Unit,
) {
    var messageRes by remember { mutableStateOf<Int?>(null) }
    val startFocus = remember { FocusRequester() }
    val exitFocus = remember { FocusRequester() }
    // A wake may leave the panel with no key focus; ask for 开始专注 again.
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(studyApp.packageName, resumeTick) {
        withFrameNanos { }
        runCatching { startFocus.requestFocus() }
    }
    BackHandler { onExit() }
    Box(Modifier.fillMaxSize()) {
        PolicyPreparingBackdrop(childWallpaper, childActive = true)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)))
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 56.dp, top = 40.dp),
        ) {
            BasicText(
                stringResource(R.string.policy_focus_study_title),
                style = Type.Title.copy(
                    fontSize = 21.sp,
                    color = Palette.SecondaryLabel,
                    shadow = FocusTextShadow,
                ),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 56.dp, top = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LockButton(
                label = stringResource(R.string.policy_focus_study_leave),
                onClick = onExit,
                width = 140.dp,
                focusRequester = exitFocus,
                labelStyle = Type.Headline.copy(fontSize = 16.sp),
                onKey = linearDpadFocus(previous = startFocus, next = startFocus),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                studyClockText(studyMinutes * 60L),
                style = Type.Clock.copy(
                    fontSize = 58.sp,
                    fontWeight = FontWeight.W600,
                    fontFeatureSettings = "tnum",
                    shadow = FocusTextShadow,
                ),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                stringResource(R.string.policy_focus_study_reward_countdown_label),
                style = Type.Caption.copy(
                    fontSize = 13.sp,
                    color = Palette.SecondaryLabel,
                    shadow = FocusTextShadow,
                ),
            )
            Spacer(Modifier.height(16.dp))
            BasicText(
                stringResource(
                    R.string.policy_lock_study_earn_footer,
                    studyApp.label,
                    studyMinutes,
                    rewardMinutes,
                ),
                style = Type.Headline.copy(
                    fontSize = 17.sp,
                    color = Palette.Label,
                    textAlign = TextAlign.Center,
                    shadow = FocusTextShadow,
                ),
                modifier = Modifier.widthIn(max = 660.dp),
            )
            Spacer(Modifier.height(30.dp))
            LockButton(
                label = stringResource(R.string.policy_focus_study_start),
                onClick = { messageRes = onStart() },
                width = 220.dp,
                focusRequester = startFocus,
                primary = true,
                height = 52.dp,
                labelStyle = Type.Headline.copy(fontSize = 18.sp),
                onKey = linearDpadFocus(previous = exitFocus, next = exitFocus),
            )
        }
        BasicText(
            text = messageRes?.let { stringResource(it) }
                ?: stringResource(R.string.policy_focus_study_intro_hint),
            style = Type.Caption.copy(
                fontSize = 13.sp,
                color = if (messageRes != null) Palette.Orange else Palette.SecondaryLabel,
                textAlign = TextAlign.Center,
                shadow = FocusTextShadow,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp)
                .widthIn(max = 700.dp),
        )
    }
}

/** The exit confirmation replaces the desktop, so no underlying button can take focus. */
@Composable
private fun FocusExitPrompt(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    BackHandler { onCancel() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancelFocus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        PolicyParentBackdrop()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        Column(
            modifier = Modifier
                .width(640.dp)
                .liquidGlass(StudyEarnCorner)
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                stringResource(R.string.policy_focus_study_exit_title),
                style = Type.Title.copy(fontSize = 24.sp, textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(12.dp))
            BasicText(
                stringResource(R.string.policy_focus_study_exit_body),
                style = Type.Body.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.widthIn(max = 540.dp),
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LockButton(
                    label = stringResource(R.string.policy_focus_study_continue),
                    onClick = onCancel,
                    width = 180.dp,
                    focusRequester = cancelFocus,
                    primary = true,
                    onKey = linearDpadFocus(previous = confirmFocus, next = confirmFocus),
                )
                LockButton(
                    label = stringResource(R.string.policy_focus_study_exit_confirm),
                    onClick = onConfirm,
                    width = 168.dp,
                    accent = true,
                    focusRequester = confirmFocus,
                    onKey = linearDpadFocus(previous = cancelFocus, next = cancelFocus),
                )
            }
        }
    }
}

@Composable
private fun StudyCountdownRing(remaining: Long, total: Long) {
    val color = when (countdownTone(remaining)) {
        CountdownTone.GREEN -> Palette.Green
        CountdownTone.ORANGE -> Palette.Orange
        CountdownTone.RED -> Palette.Red
    }
    val progress = countdownProgress(remaining, total)
    Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.20f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        BasicText(
            studyClockText(remaining),
            style = Type.Clock.copy(
                fontSize = 50.sp,
                fontWeight = FontWeight.W600,
                fontFeatureSettings = "tnum",
                shadow = FocusTextShadow,
            ),
        )
    }
}

private val FocusTextShadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 2f), 8f)

private fun studyClockText(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%02d:%02d", safe / 60L, safe % 60L)
}
