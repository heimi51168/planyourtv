package app.orchard.tv.ui.policy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

// The lock screen's floating-sheet corner; its own constant is private to PolicyLockScreen.
private val StudyEarnCorner = 24.dp

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
    messageRes: Int?,
    onQuiz: () -> Unit,
    onStudy: (AppEntry) -> Unit,
    onDirect: () -> Unit,
    onBack: () -> Unit,
) {
    val quiz = plan.quiz
    val study = plan.studyReward
    val direct = plan.direct
    // A TV input is not a study target: the route stays visible so the child sees why, but it
    // can never start from here.
    val studyApplicable = target != null
    val quizEnabled = quiz.available && quizFeatureAvailable
    val studyEnabled = study.available && studyApplicable && studyApps.isNotEmpty()
    val directEnabled = direct.available
    val visibleRoutes = plan.decisions.filter { it.visible }
    val inlineStudyApps = studyApps.size > 1
    val quizFocus = remember { FocusRequester() }
    val studyFocus = remember { FocusRequester() }
    val directFocus = remember { FocusRequester() }
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
    val quizHint = hint(
        if (!quizEnabled) {
            if (quiz.available) {
                stringResource(R.string.policy_route_quiz_unavailable)
            } else {
                unlockRouteReasonText(quiz.reason)
            }
        } else {
            null
        },
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
    val studyHint = hint(
        studyReason,
        unlockGrantText(study),
        studyMeta,
        studyBase,
        if (studyApps.size > 1) stringResource(R.string.policy_route_study_pick) else null,
    )
    val studyRowHint = hint(studyReason, unlockGrantText(study), studyBase)
    val directMeta = unlockCountText(direct)
    val directHint = hint(
        if (!directEnabled) unlockRouteReasonText(direct.reason) else null,
        unlockGrantText(direct),
        stringResource(
            R.string.policy_route_direct_hint,
            (direct.requestedSeconds / 60L).toInt(),
        ),
    )
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
            // The same shade and drop-in as Aerial's Control Center panel, centred so the choice
            // reads as a modal: the desktop stays legible, but the dock cannot be read as the
            // active surface. A denser scrim keeps bright wallpapers from washing out the rows.
            .background(Color.Black.copy(alpha = 0.36f))
            .padding(top = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .width(360.dp)
                .heightIn(max = 500.dp)
                // A tap on the card must not fall through to the scrim and dismiss the choice.
                .consumeTaps()
                .graphicsLayer {
                    alpha = cardEnter
                    scaleX = 0.94f + 0.06f * cardEnter
                    scaleY = 0.94f + 0.06f * cardEnter
                    transformOrigin = TransformOrigin(0.5f, 0f)
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
                        // viewport is tall enough for the four child routes plus the inline
                        // study apps, so the direct row is not clipped behind the Back row.
                        .heightIn(max = 340.dp)
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
                                    meta = directMeta,
                                    enabled = directEnabled,
                                    focusRequester = directFocus,
                                    onKey = focusKeys(directFocus),
                                    onClick = onDirect,
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
}

private fun hint(vararg parts: String?): String =
    parts.filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ")

/**
 * The modal shell is deliberately weaker than the option cards. It keeps the title readable
 * over a bright wallpaper without turning the whole panel into one solid glass block.
 */
private fun Modifier.choiceShellGlass(corner: Dp): Modifier = this
    .clip(RoundedCornerShape(corner))
    .drawWithCache {
        val radius = corner.toPx()
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        val edge = glassEdge(w, h, radius, 1.2.dp.toPx())
        val shell = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.46f),
            1f to Color.Black.copy(alpha = 0.60f),
        )
        onDrawBehind {
            drawRoundRect(brush = shell, cornerRadius = CornerRadius(radius, radius))
            drawImage(edge, dstSize = IntSize(w, h), alpha = 0.72f)
        }
    }

/**
 * One selectable row. The card is always visible, so a route reads as a choice before it is
 * focused; focus adds a rim and a slightly denser fill instead of turning the whole row white.
 */
private fun Modifier.choiceOptionGlass(
    corner: Dp,
    enabled: Boolean,
    focusProgress: Float,
): Modifier = this
    .clip(RoundedCornerShape(corner))
    .drawWithCache {
        val radius = corner.toPx()
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        val edge = glassEdge(w, h, radius, 1.2.dp.toPx())
        onDrawBehind {
            val focus = focusProgress.coerceIn(0f, 1f)
            val topAlpha = if (enabled) 0.58f + 0.05f * focus else 0.30f
            val bottomAlpha = if (enabled) 0.72f + 0.06f * focus else 0.38f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = topAlpha),
                    1f to Color.Black.copy(alpha = bottomAlpha),
                ),
                cornerRadius = CornerRadius(radius, radius),
            )
            drawImage(
                edge,
                dstSize = IntSize(w, h),
                alpha = if (enabled) 0.90f else 0.38f,
            )
            if (enabled && focus > 0.01f) {
                val stroke = 1.6.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.85f * focus),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(w - stroke, h - stroke),
                    cornerRadius = CornerRadius(radius - stroke / 2f, radius - stroke / 2f),
                    style = Stroke(width = stroke),
                )
            }
        }
    }

@Composable
private fun UnlockChoiceRow(
    title: String,
    hint: String,
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
        focusedScale = 1.015f,
        modifier = Modifier.fillMaxWidth(),
    ) { state ->
        val focusProgress by animateFloatAsState(
            targetValue = if (state.focused && enabled) 1f else 0f,
            animationSpec = tween(140, easing = Motion.EaseOut),
            label = "choice-card-focus",
        )
        val foreground = if (enabled) Palette.Label
        else Palette.SecondaryLabel.copy(alpha = 0.72f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .choiceOptionGlass(12.dp, enabled, focusProgress)
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
                                color = if (enabled) {
                                    Palette.Label.copy(alpha = 0.92f)
                                } else {
                                    Palette.SecondaryLabel.copy(alpha = 0.65f)
                                },
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                BasicText(
                    hint,
                    style = Type.Caption.copy(
                        lineHeight = 15.sp,
                        color = if (enabled) {
                            Palette.Label.copy(alpha = 0.82f)
                        } else {
                            Palette.SecondaryLabel.copy(alpha = 0.58f)
                        },
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
    enabled: Boolean,
) {
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
        BasicText(
            hint,
            style = Type.Caption.copy(
                lineHeight = 15.sp,
                color = if (enabled) {
                    Palette.Label.copy(alpha = 0.82f)
                } else {
                    Palette.SecondaryLabel.copy(alpha = 0.58f)
                },
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
        focusedScale = 1.015f,
        modifier = Modifier.fillMaxWidth(),
    ) { state ->
        val focusProgress by animateFloatAsState(
            targetValue = if (state.focused && enabled) 1f else 0f,
            animationSpec = tween(140, easing = Motion.EaseOut),
            label = "study-app-card-focus",
        )
        val foreground = if (enabled) Palette.Label
        else Palette.SecondaryLabel.copy(alpha = 0.72f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .choiceOptionGlass(12.dp, enabled, focusProgress)
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniTile(app, focused = state.focused && enabled, modifier = Modifier.size(28.dp))
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

/** The panel's dismiss row, in the same 44 dp MenuRow shape Aerial's sheet uses. */
@Composable
private fun ChoiceBackRow(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onKey: (KeyEvent) -> Boolean,
) {
    Pressable(
        onClick = onClick,
        focusRequester = focusRequester,
        onKey = onKey,
        focusedScale = 1.02f,
        modifier = Modifier.fillMaxWidth(),
    ) { state ->
        // A dark chip is always visible over the bright child wallpaper; focus adds a rim
        // rather than turning the row into a solid white block.
        val background by animateColorAsState(
            if (state.focused) Color.Black.copy(alpha = 0.30f)
            else Color.Black.copy(alpha = 0.20f),
            tween(140, easing = Motion.EaseOut),
            label = "choice-back-bg",
        )
        val borderColor by animateColorAsState(
            if (state.focused) Color.White.copy(alpha = 0.72f)
            else Color.White.copy(alpha = 0.22f),
            tween(140, easing = Motion.EaseOut),
            label = "choice-back-border",
        )
        val foreground = if (state.focused) Palette.Label else Palette.Label.copy(alpha = 0.82f)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .widthIn(min = 96.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(background)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                BasicText(
                    stringResource(R.string.policy_back),
                    style = Type.Body.copy(color = foreground),
                )
            }
        }
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
