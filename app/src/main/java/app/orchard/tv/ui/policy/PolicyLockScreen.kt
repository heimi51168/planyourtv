package app.orchard.tv.ui.policy

import android.text.format.DateFormat
import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import app.orchard.tv.policy.PolicyPanelRefresh
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.orchard.tv.R
import app.orchard.tv.policy.PolicyHomeReady
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.quiz.QuizRuntime
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.home.LocalStatusPillTrailing
import app.orchard.tv.ui.home.rememberNow
import app.orchard.tv.ui.home.timeText
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.Activation
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LockReason
import com.planyourtv.policy.PinResult
import com.planyourtv.policy.PolicyFlowTrace
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.PolicyUiState
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.RemotePolicyMode
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.quiz.QuizSource
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val PIN_LENGTH = 6

// The lock screen's glass is the screen itself: edge to edge, with no margin and no corner.
// The controls keep their original 920x508 dp sheet, centred, so no control moves: the sheet's
// outer margin and inner padding already put the 864 dp content at the screen's 48 dp side
// margins. The inner area is two 400 dp columns and their 64 dp gap.
private val LockStageWidth = 920.dp
private val LockStageHeight = 508.dp
// The prompt modal is still a floating sheet; only the lock screen's own glass is full screen.
private val LockStageCorner = 28.dp
private val LockScreenGlassCorner = 0.dp
private val LockStagePaddingH = 28.dp
private val LockStagePaddingV = 16.dp
private val LockColumnWidth = 400.dp
private val LockColumnGap = 64.dp
private val LockActionWidth = 320.dp
private val LockActionHeight = 56.dp
private val LockAvatarSize = 96.dp
private val LockHintAvatarSize = 36.dp
private val LockGlyphSize = 36.dp
private val LockClockHeight = 160.dp
private val LockClockDateGap = 12.dp
private val LockClockLineGap = 2.dp
// The spec rows put their gap on the clock's colon axis: 194 + 12 + 194 = the 400 dp identity
// column. Labels end at the axis, values start after it, so every row reads around the colon.
private val LockSpecLabelWidth = 194.dp
private val LockSpecGap = 12.dp
private val LockSpecValueWidth = 194.dp
private val LockSpecRowHeight = 20.dp
private val LockSpecRowGap = 4.dp
private val LockFooterHeight = 40.dp
private val LockActionText = Type.Headline.copy(fontSize = 18.sp)
private val LockRecoveryKeyText = Type.Headline.copy(fontSize = 14.sp)
private val LockTitleText = Type.LargeTitle.copy(fontSize = 30.sp)
private val LockBodyText = Type.Body.copy(fontSize = 16.sp)
private val LockCaptionText = Type.Caption.copy(fontSize = 13.sp)
private val LockHeadingText = Type.Title.copy(fontSize = 21.sp)
// Tabular figures keep the width steady as the minute changes; tracking is loosened a little
// from the Home clock so a 96 sp "23:59" still leaves room for the date block beside it.
private val LockClockText = Type.Clock.copy(
    fontWeight = FontWeight.W600,
    fontSize = 96.sp,
    letterSpacing = (-0.03).em,
    fontFeatureSettings = "tnum",
)
// The two-line date block sits to the right of the digits: the month/day is the reading line,
// the weekday is quieter, like a phone lock screen. The block's weekday baseline aligns with
// the digits' bottom.
private val LockClockDateText = Type.ClockDetail.copy(
    color = Palette.Label.copy(alpha = 0.9f),
)
private val LockClockWeekdayText = Type.Callout
// "预计可用" grew by half; it is the first line a child reads after the clock.
private val LockExpectedLabelText = Type.Body.copy(
    fontSize = 20.sp,
    lineHeight = 26.sp,
    color = Palette.SecondaryLabel,
)
private val LockExpectedValueText = Type.Title.copy(
    fontSize = 24.sp,
    lineHeight = 30.sp,
    color = Palette.Label,
)
private val LockDetailLabelText = Type.Body.copy(
    fontSize = 15.sp,
    lineHeight = 20.sp,
    color = Palette.SecondaryLabel,
)
private val LockDetailValueText = Type.Body.copy(
    fontSize = 15.sp,
    lineHeight = 20.sp,
    color = Palette.Label,
)
private val LockDetailReasonText = Type.Body.copy(
    fontSize = 15.sp,
    lineHeight = 20.sp,
    color = Palette.SecondaryLabel,
)
private val LockWheelSelectedText = Type.LargeTitle.copy(fontSize = 28.sp, color = Palette.OnFocus)
private val LockWheelNeighborText = Type.Title.copy(fontSize = 18.sp)
private val LockWheelFarText = Type.Title.copy(fontSize = 16.sp)
private val LockWheelRowHeight = 48.dp
private val LockWheelHeight = 240.dp
private val LockWheelPillWidth = 240.dp
private val LockWheelPillHeight = 48.dp

/** What the PIN is proving. A manual start must verify only; it must not begin an override. */
private enum class PinPurpose { UNLOCK, MANUAL_START }

/** The main-menu action whose hint sits under the action list. */
private enum class LockAction { STUDY, QUIZ, MANUAL, AUTO_START, CONFIRM_START, PARENT_UNLOCK }

/**
 * The whole TV becomes this screen whenever the session is not ACTIVE and the
 * guard is RUNNING. It is a normal Compose surface, not an overlay window.
 */
@Composable
fun PolicyHomeGate(state: PolicyUiState, home: @Composable () -> Unit) {
    val homeVisible = showsHome(state)
    val desired = PolicyModeCoordinator.accessFingerprint(state)
    val applied by PolicyHomeReady.fingerprint.collectAsState()
    var preparing by remember { mutableStateOf(false) }
    var wasGate by remember { mutableStateOf(!homeVisible) }
    var lastDesired by remember { mutableStateOf(desired) }

    LaunchedEffect(
        homeVisible,
        desired,
        state.mode,
        state.guardState,
        state.sessionState,
        state.accessMode,
        state.allowedPackages,
    ) {
        if (!homeVisible) {
            wasGate = true
            preparing = false
            lastDesired = desired
            return@LaunchedEffect
        }
        val enteredFromGate = wasGate
        wasGate = false
        val changed = desired != lastDesired
        lastDesired = desired
        if ((enteredFromGate || changed) && desired != applied) {
            preparing = true
            withTimeoutOrNull(1_500) {
                PolicyHomeReady.fingerprint.first { it == desired }
            }
            preparing = false
        }
    }

    when {
        !state.loaded -> Box(Modifier.fillMaxSize().background(Color.Black))
        preparing -> DesktopPreparingScreen()
        state.mode == PolicyMode.NONE -> PolicyHomeWithBadge(home)
        state.mode == PolicyMode.LOCAL && state.setupRequired -> PolicySetupScreen(state)
        state.mode == PolicyMode.CLOUD &&
            state.guardState == GuardStatus.PARENT_OVERRIDE && !state.bound && !state.bindingDeferred ->
            PolicyBindingScreen(state)
        state.guardState == GuardStatus.ENFORCING && state.sessionState != SessionState.ACTIVE ->
            PolicyLockScreen(state)
        // Study mode also lands here. The executor publishes the study list as the APPS scope,
        // so HomeViewModel hands Home only those tiles; a dedicated launcher would duplicate the
        // grid and break the desktop's look, focus and navigation. Control Center carries the
        // study-mode exit row, so the child is not stuck on the study desktop.
        else -> PolicyHomeWithBadge(home)
    }
}

/** Mirrors the branches in [PolicyHomeGate] that show Home rather than a gate. */
private fun showsHome(state: PolicyUiState): Boolean = when {
    !state.loaded -> false
    state.mode == PolicyMode.NONE -> true
    state.mode == PolicyMode.LOCAL && state.setupRequired -> false
    state.mode == PolicyMode.CLOUD &&
        state.guardState == GuardStatus.PARENT_OVERRIDE && !state.bound && !state.bindingDeferred -> false
    state.guardState == GuardStatus.STUDY_MODE -> true
    state.guardState == GuardStatus.ENFORCING && state.sessionState != SessionState.ACTIVE -> false
    else -> true
}

@Composable
private fun DesktopPreparingScreen() {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = stringResource(R.string.policy_desktop_preparing),
            style = Type.Title.copy(color = Color.White),
        )
    }
}

@Composable
private fun PolicyHomeWithBadge(home: @Composable () -> Unit) {
    val badge: @Composable (Color) -> Unit = { foreground ->
        PolicyStatusPillBadge(foreground)
    }
    CompositionLocalProvider(LocalStatusPillTrailing provides badge) {
        home()
    }
}

@Composable
private fun PolicyLockScreen(state: PolicyUiState) {
    // Read in the draw phase below: a remote AUTO/MANUAL switch during the TV's idle standby
    // needs one more frame after the injected BACK that wakes the panel (PolicyPanelRefresh).
    val panelRefresh = PolicyPanelRefresh.revision.collectAsState()
    // The keypad is a low-frequency maintenance entry, not the lock screen's default. A manual
    // segment asks for its length first, then for the parent PIN; "Temporary unlock" goes straight
    // to the PIN pad.
    var pinMode by remember { mutableStateOf(false) }
    var pinPurpose by remember { mutableStateOf(PinPurpose.UNLOCK) }
    var durationMode by remember { mutableStateOf(false) }
    // The wheel value, reset to the default each time the parent opens the picker.
    var selectedMinutes by remember { mutableStateOf(MANUAL_DURATION_DEFAULT_MINUTES) }
    // The chosen manual length, held while the parent enters the PIN that starts it.
    var pendingMinutes by remember { mutableStateOf<Int?>(null) }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var actionMessage by remember { mutableStateOf<Int?>(null) }
    // A bound TV answers the manual entries on the phone; this holds the explanation shown
    // instead of opening a local flow that the cloud engine would reject.
    var promptMessage by remember { mutableStateOf<Int?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var recoveryMode by remember { mutableStateOf(false) }
    var quizMode by remember { mutableStateOf(false) }
    // The main-menu footer follows the focused button. Null means the default action owns focus.
    var focusedAction by remember { mutableStateOf<LockAction?>(null) }
    val quizState by QuizRuntime.state.collectAsState()
    // Compose focuses the first focusable child when the screen appears. This target sits on
    // the sheet itself so the lock screen opens with no action selected; a redemption then
    // moves focus to the start entry.
    val idleFocus = remember { FocusRequester() }
    // The first remote direction press enters the list at Study Mode. This never fires on
    // its own; only an explicit key from the idle state uses it.
    val firstActionFocus = remember { FocusRequester() }
    val startFocus = remember { FocusRequester() }
    val firstKeyFocus = remember { FocusRequester() }
    val durationFocus = remember { FocusRequester() }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // The window loses Compose focus while the screen is off. Ask for the default again
    // after standby so the lock screen never returns with no button selected.
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val v10 = state.mode == PolicyMode.LOCAL || state.source == PolicySource.HOSTED
    val hosted = state.source == PolicySource.HOSTED
    // Redemption is a manual grant, not a separate unlock mode. The legacy cloud engine does
    // not publish the V10 window or MANUAL_WAITING, so treat a bound REDEEM policy as manual
    // here and let the existing manual entries use their normal cloud path.
    val redeemManual = state.mode == PolicyMode.CLOUD && state.bound &&
        state.remoteMode == RemotePolicyMode.REDEEM &&
        state.activation == Activation.MANUAL
    val localManual = v10 && state.activation == Activation.MANUAL &&
        state.mode == PolicyMode.LOCAL
    val hostedManual = v10 && state.activation == Activation.MANUAL && hosted
    // Product rule: Study mode and parent temporary unlock are fixed entries on every lock
    // screen, whatever engine or activation is running. An unavailable/empty study list keeps
    // the entry visible as a no-op and the focused footer names the missing setup.
    val studyEntryVisible = true
    val autoReady = v10 && state.activation == Activation.AUTO && state.autoReady
    // The runtime checks the window again when a manual session starts. Keep the lock screen
    // honest here: outside a window the start and quiz entries must be frozen, not ask for a
    // PIN only to reject the start afterwards.
    val manualWindowOpen = state.windowRemainingSeconds > 0L || redeemManual
    // Manual entries follow the rule engine's own window and rest state. V10 hosted manual
    // opens the same parent-confirmed picker as local manual; only legacy redemption asks the
    // phone, because it has no local window and the phone decides when it may grant time.
    val manualWaiting = (v10 || redeemManual) &&
        state.activation == Activation.MANUAL && !state.noTimedApps
    // Mirror the entry gates for the trace so one log line explains what the child can see
    // after a redemption. The branch below stays the single source of truth for the UI.
    val restingNow = state.lockReason == LockReason.RESTING &&
        state.restRemainingSeconds > 0L
    // A pending start is the one entry that receives focus without a key press. While the
    // rest interval hides it, fall back to the idle sheet target.
    val pendingStartVisible = state.sessionState == SessionState.PENDING && !restingNow
    val traceManualAvailable = manualWaiting && !restingNow && manualWindowOpen &&
        (state.lockReason == LockReason.MANUAL_WAITING || redeemManual)
    val tracePending = manualWaiting &&
        state.sessionState == SessionState.PENDING && !restingNow
    val traceQuizVisible = quizState.enabled && quizState.entry != "blocked"
    val traceQuizAvailable = (traceManualAvailable || tracePending) && traceQuizVisible
    LaunchedEffect(
        state.sessionState,
        state.guardState,
        state.activation,
        state.lockReason,
        manualWindowOpen,
        traceManualAvailable,
        tracePending,
        traceQuizVisible,
        traceQuizAvailable,
        state.studyModeAvailable,
        state.userName.isNotBlank(),
        state.userIconUrl.isNotBlank(),
    ) {
        PolicyFlowTrace.event(
            "lock.entries",
            "study" to studyEntryVisible,
            "studyAvailable" to state.studyModeAvailable,
            "quizVisible" to traceQuizVisible,
            "quizAvailable" to traceQuizAvailable,
            "manualAvailable" to traceManualAvailable,
            "pending" to tracePending,
            "windowOpen" to manualWindowOpen,
            "resting" to restingNow,
            "session" to state.sessionState,
            "guard" to state.guardState,
            "lock" to state.lockReason,
            "user" to state.userName.isNotBlank(),
            "icon" to state.userIconUrl.isNotBlank(),
        )
    }
    LaunchedEffect(state.sessionState, state.guardState) {
        // Any phase change collapses the PIN pad and the duration picker. A parent-initiated
        // "temporary unlock" does not change the state, so this effect does not close the pad
        // while it is being used.
        if (state.sessionState == SessionState.ACTIVE ||
            state.guardState != GuardStatus.ENFORCING
        ) {
            pinMode = false
            durationMode = false
            pendingMinutes = null
        }
    }
    LaunchedEffect(state.sessionState) {
        // A phone grant can arrive while the local picker or PIN pad is open. The grant owns
        // the single start gate, so close the local flow instead of leaving a confirm that
        // can only come back BUSY.
        if (state.sessionState == SessionState.PENDING &&
            (durationMode || (pinMode && pinPurpose == PinPurpose.MANUAL_START))
        ) {
            pinMode = false
            durationMode = false
            pendingMinutes = null
            entered = ""
            error = null
            actionMessage = null
        }
    }
    LaunchedEffect(
        pinMode,
        durationMode,
        quizMode,
        recoveryMode,
        state.lockReason,
        state.autoReady,
        state.activation,
        // A redemption can move the session to PENDING without changing the lock reason
        // (it stays NONE), so follow the session too and hand focus to the start entry.
        state.sessionState,
        studyEntryVisible,
        // Re-anchor focus when a window opens or closes and the start entries change
        // between enabled and frozen. [promptMessage] brings the sheet back after the
        // prompt closes, so it re-applies the idle (or pending) focus.
        state.windowRemainingSeconds > 0L,
        promptMessage,
        resumeTick,
    ) {
        withFrameNanos { }
        runCatching {
            val target = when {
                pinMode -> firstKeyFocus
                durationMode -> durationFocus
                // The lock screen has no default selection. Only a granted redemption
                // hands focus to the start entry that confirms it.
                pendingStartVisible -> startFocus
                else -> idleFocus
            }
            PolicyFlowTrace.event(
                "focus.request",
                "target" to when (target) {
                    idleFocus -> "IDLE"
                    startFocus -> "START"
                    firstKeyFocus -> "PIN"
                    else -> "DURATION"
                },
                "session" to state.sessionState,
            )
            target.requestFocus()
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { QuizRuntime.refresh() }
    }
    LaunchedEffect(error) {
        if (error != null) {
            shake.snapTo(0f)
            shake.animateTo(
                0f,
                animationSpec = keyframes {
                    durationMillis = 340
                    -12f at 40
                    12f at 80
                    -8f at 130
                    8f at 180
                    -4f at 240
                    0f at 300
                },
            )
        }
    }

    fun actionFocused(action: LockAction, focused: Boolean) {
        if (focused) {
            focusedAction = action
        } else if (focusedAction == action) {
            focusedAction = null
        }
        PolicyFlowTrace.event("focus.action", "action" to action, "focused" to focused)
    }

    fun startManual(minutes: Int) {
        actionMessage = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                PolicyModeCoordinator.startLocalManualSession(
                    minutes * 60L,
                    allowHostedRule = true,
                )
            }
            when (result) {
                SessionStartResult.Started, SessionStartResult.Pending -> Unit
                is SessionStartResult.Rejected -> {
                    durationMode = false
                    actionMessage = rejectMessage(result.reason)
                }
            }
        }
    }

    // Six digits are checked as soon as the last one is entered; there is no
    // confirm key. Verification runs off the main thread so the keypad stays
    // responsive on a TV.
    fun submit() {
        if (entered.length != PIN_LENGTH || verifying) return
        val candidate = entered
        val purpose = pinPurpose
        val chosenMinutes = pendingMinutes
        verifying = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                if (purpose == PinPurpose.MANUAL_START) {
                    // Verify only, then start the segment the parent already chose. Beginning an
                    // override here would unlock the whole TV instead.
                    PolicyModeCoordinator.verifyPin(candidate)
                } else {
                    PolicyModeCoordinator.submitPin(candidate)
                }
            }
            verifying = false
            when (result) {
                PinResult.OK -> {
                    error = null
                    entered = ""
                    pinMode = false
                    if (purpose == PinPurpose.MANUAL_START) {
                        pendingMinutes = null
                        actionMessage = null
                        // Fall back to the picker if the length was somehow lost.
                        if (chosenMinutes != null) startManual(chosenMinutes) else durationMode = true
                    }
                }
                PinResult.WRONG, PinResult.NOT_SET -> {
                    error = R.string.policy_wrong_pin
                    entered = ""
                }
                PinResult.LOCKED_OUT -> {
                    error = R.string.policy_locked_out
                    entered = ""
                }
            }
        }
    }

    fun type(digit: Char) {
        if (!pinMode || verifying || entered.length >= PIN_LENGTH) return
        entered += digit
        error = null
        if (entered.length == PIN_LENGTH) submit()
    }

    fun erase() {
        if (verifying) return
        if (entered.isNotEmpty()) {
            entered = entered.dropLast(1)
            error = null
        }
    }

    fun closePinPad() {
        pinMode = false
        entered = ""
        error = null
        // A manual start already picked its length; Back returns to that picker, not the menu.
        if (pinPurpose == PinPurpose.MANUAL_START) durationMode = true
    }

    fun startAuto() {
        actionMessage = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                PolicyModeCoordinator.startAutoSession()
            }
            if (result is SessionStartResult.Rejected) {
                actionMessage = rejectMessage(result.reason)
            }
        }
    }

    fun enterStudy() {
        actionMessage = null
        // An empty study list stays visible for the explanation, but must not open an empty
        // launcher: the footer already tells the child to ask a parent to add apps.
        if (!state.studyModeAvailable || state.studyPackages.isEmpty()) return
        if (!PolicyModeCoordinator.enterStudyMode()) {
            actionMessage = R.string.policy_action_rejected
        }
    }

    // A remote switch must not inject BACK into a flow that BACK would dismiss; PolicyPanelRefresh
    // wakes the panel with BACK only when the lock screen's normal sheet is showing.
    SideEffect {
        PolicyPanelRefresh.setOverlayOpen(
            recoveryMode || quizMode || promptMessage != null || durationMode || pinMode,
        )
    }
    DisposableEffect(Unit) {
        onDispose { PolicyPanelRefresh.setOverlayOpen(false) }
    }
    if (recoveryMode) {
        PolicyRecoveryScreen(
            onCancel = { recoveryMode = false },
            onDone = { recoveryMode = false },
        )
        return
    }
    if (quizMode) {
        PolicyQuizScreen(onClose = { quizMode = false })
        return
    }
    // Replace the sheet instead of drawing over it: the buttons underneath must not keep
    // focus while the prompt is up.
    val prompt = promptMessage
    if (prompt != null) {
        PolicyLockPrompt(message = prompt, onDismiss = { promptMessage = null })
        return
    }

    BackHandler(enabled = true) {
        when {
            durationMode -> {
                pendingMinutes = null
                durationMode = false
            }
            pinMode -> closePinPad()
        }
    }

    val minutes = ((state.effectiveRemainingSeconds.coerceAtLeast(0) + 59) / 60).toInt()
    val defaultChild = stringResource(R.string.policy_default_child)
    val userName = state.userName.ifBlank { defaultChild }
    val errorText = error?.let { stringResource(it) }
    val actionText = actionMessage?.let { stringResource(it) }
    val cloudNeedsGrant = state.mode == PolicyMode.CLOUD && state.cloudNeedsGrant
    val title = stringResource(
        when {
            state.sessionState == SessionState.PENDING -> R.string.policy_pending_title
            cloudNeedsGrant && state.lockReason == LockReason.NONE ->
                R.string.policy_cloud_need_grant_title
            state.lockReason == LockReason.PARENT_STOP -> R.string.policy_parent_stopped_title
            state.lockReason == LockReason.CANCELED -> R.string.policy_canceled_title
            state.lockReason == LockReason.TIME_UP -> R.string.policy_time_up
            state.lockReason == LockReason.OUTSIDE_WINDOW -> R.string.policy_outside_window_title
            state.lockReason == LockReason.RESTING -> R.string.policy_resting_title
            state.lockReason == LockReason.AUTO_READY -> R.string.policy_auto_ready_title
            state.lockReason == LockReason.MANUAL_WAITING ->
                if (localManual || hostedManual) {
                    R.string.policy_manual_waiting_title
                } else {
                    R.string.policy_hosted_manual_title
                }
            state.lockReason == LockReason.AUTO_BLOCKED -> R.string.policy_auto_blocked_title
            state.lockReason == LockReason.NO_TIMED_APPS -> R.string.policy_no_timed_apps_title
            state.lockReason == LockReason.SETUP_REQUIRED -> R.string.policy_setup_required_title
            else -> R.string.policy_locked_title
        },
    )
    val body = when {
        state.lockReason == LockReason.AUTO_READY -> stringResource(R.string.policy_auto_ready_body)
        state.lockReason == LockReason.TIME_UP -> stringResource(R.string.policy_time_up_body)
        state.lockReason == LockReason.AUTO_BLOCKED -> stringResource(R.string.policy_auto_blocked_body)
        state.lockReason == LockReason.NO_TIMED_APPS -> stringResource(R.string.policy_no_timed_apps_body)
        state.lockReason == LockReason.MANUAL_WAITING && (hostedManual || localManual) ->
            stringResource(R.string.policy_manual_waiting_body)
        else -> ""
    }
    // The source badge is the only place the lock screen names local vs remote. It carries
    // "本地=未绑定 / 远程=绑定" by product definition, so the footer never repeats binding.
    val identityModeLabel = if (v10) {
        stringResource(
            when {
                hosted && state.activation == Activation.MANUAL -> R.string.policy_mode_remote_manual
                hosted -> R.string.policy_mode_remote_auto
                state.activation == Activation.MANUAL -> R.string.policy_mode_local_manual
                else -> R.string.policy_mode_local_auto
            },
        )
    } else {
        stringResource(
            when {
                state.mode == PolicyMode.LOCAL -> R.string.policy_mode_local
                state.remoteMode == RemotePolicyMode.RULE -> R.string.policy_mode_cloud_rule
                state.remoteMode == RemotePolicyMode.REDEEM -> R.string.policy_mode_cloud_redeem
                else -> R.string.policy_mode_cloud
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Draw one more frame when a remote switch needed the panel woken; the frame itself
            // is unchanged apart from the badge the state already switched.
            .drawWithContent {
                if (panelRefresh.value < 0) return@drawWithContent
                drawContent()
            }
            .focusRequester(idleFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // The sheet itself holds focus while idle so no action is pre-selected.
                // The first direction press enters the list; after that the column's own
                // navigation takes over.
                if (!pinMode && !durationMode && focusedAction == null &&
                    event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0
                ) {
                    when (event.nativeKeyEvent.keyCode) {
                        NativeKeyEvent.KEYCODE_DPAD_UP,
                        NativeKeyEvent.KEYCODE_DPAD_DOWN,
                        NativeKeyEvent.KEYCODE_DPAD_LEFT,
                        NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            PolicyFlowTrace.event(
                                "focus.idle.key",
                                "key" to event.nativeKeyEvent.keyCode,
                                "focused" to focusedAction,
                            )
                            if (runCatching { firstActionFocus.requestFocus() }.isSuccess) {
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                }
                if (!pinMode || event.type != KeyEventType.KeyDown || event.nativeKeyEvent.repeatCount != 0) {
                    return@onPreviewKeyEvent false
                }
                when (event.nativeKeyEvent.keyCode) {
                    in NativeKeyEvent.KEYCODE_0..NativeKeyEvent.KEYCODE_9 -> {
                        type('0' + (event.nativeKeyEvent.keyCode - NativeKeyEvent.KEYCODE_0))
                        true
                    }
                    NativeKeyEvent.KEYCODE_DEL -> {
                        erase()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The whole screen is Aerial's glass, edge to edge, so the frost stays visible instead of
        // turning the screen black. Its rim is off: around a surface that is the whole screen the
        // lit outline reads as a frame, so only the sheen inside the top remains. The backdrop and
        // night tint stay behind it for the fallbacks where the glass is a translucent colour
        // rather than a picture. No live blur: liquidGlass reuses the pre-baked frosted picture.
        PolicyParentBackdrop()
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x33101827), Color(0x6603060C)),
                    ),
                ),
        )
        // Full-screen glass. The controls below keep their centred 920x508 sheet, so extending
        // the glass to the edges moves nothing; this box is not focusable and draws behind its
        // (empty) content.
        Box(Modifier.fillMaxSize().liquidGlass(LockScreenGlassCorner, litRim = false))
        Row(
            modifier = Modifier
                .width(LockStageWidth)
                .height(LockStageHeight)
                .padding(horizontal = LockStagePaddingH, vertical = LockStagePaddingV),
            horizontalArrangement = Arrangement.spacedBy(LockColumnGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Identity side: V10 keeps only the source/activation badge, the clock (with the
            // avatar inside it) and the estimated next usable time. Legacy cloud keeps its
            // redemption guidance because that flow cannot be explained by the clock alone.
            Column(
                modifier = Modifier.width(LockColumnWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (v10) {
                    LockClockIdentity(
                        state = state,
                        modeLabel = identityModeLabel,
                        hosted = hosted,
                        resumeTick = resumeTick,
                        minutes = minutes,
                        showPending = state.sessionState == SessionState.PENDING && !pinMode,
                    )
                } else {
                    LegacyLockIdentity(
                        state = state,
                        modeLabel = identityModeLabel,
                        title = title,
                        body = body,
                        userName = userName,
                        cloudNeedsGrant = cloudNeedsGrant,
                        resumeTick = resumeTick,
                        minutes = minutes,
                        showPending = state.sessionState == SessionState.PENDING && !pinMode,
                    )
                }
            }

            // Action side: start the pending grant, the automatic segment, the manual segment,
            // or enter the parent PIN.
            Column(
                modifier = Modifier
                    .width(LockColumnWidth)
                    .offset { IntOffset(shake.value.roundToInt(), 0) }
                    .graphicsLayer { alpha = if (verifying) 0.55f else 1f }
                    .then(
                        // The main lock-screen actions are a vertical list. Standard remotes
                        // still send left/right, so map them to up/down and let all four
                        // directions walk the same list. The PIN pad keeps its own grid
                        // navigation; the duration wheel handles up/down itself.
                        if (pinMode || durationMode) {
                            Modifier
                        } else {
                            Modifier.onPreviewKeyEvent { event ->
                                val key = event.nativeKeyEvent.keyCode
                                if (key != NativeKeyEvent.KEYCODE_DPAD_LEFT &&
                                    key != NativeKeyEvent.KEYCODE_DPAD_RIGHT
                                ) {
                                    return@onPreviewKeyEvent false
                                }
                                if (event.type == KeyEventType.KeyDown) {
                                    focusManager.moveFocus(
                                        if (key == NativeKeyEvent.KEYCODE_DPAD_LEFT) {
                                            FocusDirection.Up
                                        } else {
                                            FocusDirection.Down
                                        },
                                    )
                                }
                                true
                            }
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    durationMode -> ManualDurationPicker(
                        minutes = selectedMinutes,
                        onStep = { up ->
                            selectedMinutes = stepManualDuration(selectedMinutes, up)
                        },
                        onConfirm = {
                            pendingMinutes = selectedMinutes
                            pinPurpose = PinPurpose.MANUAL_START
                            entered = ""
                            error = null
                            durationMode = false
                            pinMode = true
                        },
                        focusRequester = durationFocus,
                    )
                    pinMode -> {
                        BasicText(
                            stringResource(
                                if (pinPurpose == PinPurpose.MANUAL_START) {
                                    R.string.policy_manual_pin_title
                                } else {
                                    R.string.policy_parent_pin
                                },
                            ),
                            style = LockHeadingText,
                        )
                        Spacer(Modifier.height(4.dp))
                        BasicText(
                            stringResource(R.string.policy_pin_hint),
                            style = LockCaptionText.copy(
                                color = if (error == null) Palette.SecondaryLabel else Palette.Red,
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        if (pinPurpose == PinPurpose.MANUAL_START) {
                            pendingMinutes?.let { chosen ->
                                BasicText(
                                    stringResource(R.string.policy_manual_pin_note, chosen),
                                    style = LockCaptionText.copy(
                                        color = Palette.SecondaryLabel,
                                        textAlign = TextAlign.Center,
                                    ),
                                    modifier = Modifier.widthIn(max = 360.dp),
                                )
                            }
                        } else {
                            BasicText(
                                stringResource(R.string.policy_temp_unlock_note),
                                style = LockCaptionText.copy(
                                    color = Palette.SecondaryLabel,
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier.widthIn(max = 360.dp),
                            )
                            if (!(state.mode == PolicyMode.CLOUD && cloudNeedsGrant)) {
                                BasicText(
                                    stringResource(
                                        if (state.mode == PolicyMode.CLOUD) {
                                            R.string.policy_temp_unlock_cloud_note
                                        } else {
                                            R.string.policy_temp_unlock_local_note
                                        },
                                    ),
                                    style = LockCaptionText.copy(
                                        color = Palette.SecondaryLabel,
                                        textAlign = TextAlign.Center,
                                    ),
                                    modifier = Modifier.widthIn(max = 360.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            repeat(PIN_LENGTH) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (index < entered.length) Palette.Label else Color.Transparent)
                                        .border(1.5.dp, Palette.Label, CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            errorText ?: " ",
                            style = LockCaptionText.copy(color = Palette.Red, textAlign = TextAlign.Center),
                        )
                        Spacer(Modifier.height(8.dp))
                        val unlockPad = pinPurpose == PinPurpose.UNLOCK
                        PinKeypad(
                            onDigit = ::type,
                            onErase = ::erase,
                            firstKeyFocus = firstKeyFocus,
                            leftKeyLabel = stringResource(R.string.policy_back),
                            onLeftKey = ::closePinPad,
                        )
                        // Back fills the slot left of 0 on both pads, so the key block stays
                        // aligned. The unlock pad's recovery code is the rarer escape and
                        // sits below it.
                        if (unlockPad) {
                            Spacer(Modifier.height(8.dp))
                            LockActionButton(
                                label = stringResource(R.string.policy_recovery_key),
                                onClick = { recoveryMode = true },
                                width = 180.dp,
                                labelStyle = LockRecoveryKeyText,
                            )
                        }
                    }
                    else -> {
                        val resting = state.lockReason == LockReason.RESTING &&
                            state.restRemainingSeconds > 0
                        // Rest and a closed window are different reasons, but both mean the
                        // parent-controlled entries cannot start a session right now.
                        val manualAvailable = manualWaiting && !resting && manualWindowOpen &&
                            (state.lockReason == LockReason.MANUAL_WAITING || redeemManual)
                        val manualFrozen = manualWaiting && !resting && !manualAvailable
                        val quizEntryVisible = quizState.enabled && quizState.entry != "blocked"
                        val quizLabel = when {
                            quizState.sessionState == "running" -> stringResource(
                                R.string.policy_quiz_resume,
                                ((quizState.session?.run?.index ?: 0) + 1)
                                    .coerceAtMost(quizState.questionCount.coerceAtLeast(1)),
                                quizState.questionCount,
                            )
                            else -> stringResource(R.string.policy_quiz_entry)
                        }
                        val autoWaiting = v10 && state.activation == Activation.AUTO &&
                            !state.noTimedApps
                        // The automatic start keeps its slot between Study Mode and parent
                        // unlock even when it cannot start yet, so the layout and the OK target
                        // stay stable while rest, a closed window or a spent quota passes.
                        val autoUnavailable = autoWaiting && !autoReady && !resting &&
                            (state.lockReason == LockReason.AUTO_BLOCKED ||
                                state.lockReason == LockReason.OUTSIDE_WINDOW ||
                                state.lockReason == LockReason.TIME_UP)
                        // A manual grant can already be waiting for its local start (the
                        // phone precomputed the time for a redemption). The grant leaves
                        // lockReason at NONE, so keep the quiz entry available for it too.
                        val pendingManual = manualWaiting &&
                            state.sessionState == SessionState.PENDING && !resting
                        val quizAvailable = (manualAvailable || pendingManual) &&
                            quizEntryVisible
                        // Whether a start action is shown below. Study Mode is an entry,
                        // not the OK target.
                        val primaryAction = when {
                            state.sessionState == SessionState.PENDING && !resting -> true
                            state.noTimedApps -> false
                            autoReady -> true
                            manualAvailable -> true
                            else -> false
                        }
                        // The footer starts on the action that owns default focus, then follows
                        // whatever the child moves to. Entries that disappear (a window closing)
                        // fall back to the default instead of leaving stale copy on screen.
                        val defaultAction = when {
                            studyEntryVisible -> LockAction.STUDY
                            state.sessionState == SessionState.PENDING && !resting ->
                                LockAction.CONFIRM_START
                            autoReady && !resting -> LockAction.AUTO_START
                            manualAvailable -> LockAction.MANUAL
                            else -> LockAction.PARENT_UNLOCK
                        }
                        val footerAction = when (focusedAction) {
                            LockAction.STUDY ->
                                if (studyEntryVisible) LockAction.STUDY else defaultAction
                            LockAction.QUIZ ->
                                if (quizAvailable) LockAction.QUIZ else defaultAction
                            LockAction.MANUAL ->
                                if (manualAvailable) LockAction.MANUAL else defaultAction
                            LockAction.AUTO_START ->
                                if (autoReady && !resting) LockAction.AUTO_START else defaultAction
                            LockAction.CONFIRM_START ->
                                if (state.sessionState == SessionState.PENDING && !resting) {
                                    LockAction.CONFIRM_START
                                } else {
                                    defaultAction
                                }
                            LockAction.PARENT_UNLOCK, null -> focusedAction ?: defaultAction
                        }
                        var contentShown = false

                        // Study Mode is the child's first choice on every lock screen, but it
                        // never takes the default focus: the lock screen starts with no
                        // selection until a redemption hands focus to the start entry.
                        if (studyEntryVisible) {
                            LockActionButton(
                                label = stringResource(R.string.policy_study_enter),
                                onClick = ::enterStudy,
                                width = 320.dp,
                                focusRequester = firstActionFocus,
                                onFocused = { actionFocused(LockAction.STUDY, it) },
                            )
                            contentShown = true
                        }

                        if (resting) {
                            // A manual segment and the quiz are both parent-controlled manual
                            // unlocks, so both stay frozen until the rest interval passes. The
                            // automatic start waits out the same interval.
                            if (contentShown) Spacer(Modifier.height(12.dp))
                            when {
                                manualWaiting -> {
                                    // The quiz is a parent-controlled manual unlock too,
                                    // so keep its entry visible and frozen during rest.
                                    if (quizEntryVisible) {
                                        FrozenAction(label = quizLabel)
                                        Spacer(Modifier.height(10.dp))
                                    }
                                    FrozenAction(
                                        label = stringResource(R.string.policy_manual_confirm),
                                    )
                                }
                                autoWaiting -> {
                                    FrozenAction(label = stringResource(R.string.policy_start_timer))
                                }
                            }
                            contentShown = true
                        }

                        when {
                            state.sessionState == SessionState.PENDING && !resting -> {
                                if (contentShown) Spacer(Modifier.height(12.dp))
                                // A waiting manual grant (including a redemption) keeps the
                                // quiz entry; starting the granted segment stays the primary
                                // action below.
                                if (quizAvailable) {
                                    LockActionButton(
                                        label = quizLabel,
                                        onClick = { quizMode = true },
                                        width = 320.dp,
                                        onFocused = { actionFocused(LockAction.QUIZ, it) },
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                                // Both legacy redemption and a hosted MQTT/REST grant wait for
                                // this one local confirmation; nothing has been consumed yet.
                                LockActionButton(
                                    label = stringResource(R.string.policy_start_timer),
                                    onClick = {
                                        actionMessage = null
                                        scope.launch {
                                            val result = withContext(Dispatchers.Default) {
                                                PolicyModeCoordinator.startSession()
                                            }
                                            if (result is SessionStartResult.Rejected) {
                                                actionMessage = rejectMessage(result.reason)
                                            }
                                        }
                                    },
                                    focusRequester = startFocus,
                                    primary = true,
                                    accent = true,
                                    width = 320.dp,
                                    onFocused = { actionFocused(LockAction.CONFIRM_START, it) },
                                )
                                contentShown = true
                            }
                            state.noTimedApps -> Unit
                            autoReady -> {
                                if (contentShown) Spacer(Modifier.height(12.dp))
                                LockActionButton(
                                    label = stringResource(R.string.policy_start_timer),
                                    onClick = ::startAuto,
                                    primary = true,
                                    width = 320.dp,
                                    onFocused = { actionFocused(LockAction.AUTO_START, it) },
                                )
                                contentShown = true
                            }
                            autoUnavailable -> {
                                if (contentShown) Spacer(Modifier.height(12.dp))
                                FrozenAction(label = stringResource(R.string.policy_start_timer))
                                contentShown = true
                            }
                            manualFrozen -> {
                                if (contentShown) Spacer(Modifier.height(12.dp))
                                // Keep both parent-controlled entries visible but dimmed, so
                                // the child sees that they return when the next window opens.
                                // The left column already carries the next-window guidance.
                                if (quizEntryVisible) {
                                    FrozenAction(label = quizLabel)
                                    Spacer(Modifier.height(10.dp))
                                }
                                FrozenAction(
                                    label = stringResource(R.string.policy_manual_confirm),
                                )
                                contentShown = true
                            }
                            manualAvailable -> {
                                if (contentShown) Spacer(Modifier.height(12.dp))
                                // Manual mode order: Study Mode, quiz, one-off timer, parent.
                                // Hosted rule mode uses the same parent-confirmed picker as
                                // local manual. Only legacy redemption, or a hosted TV without
                                // a usable parent PIN, still asks the phone.
                                val localPicker = !state.bound ||
                                    (hostedManual && state.pinConfigured)
                                if (quizAvailable) {
                                    LockActionButton(
                                        label = quizLabel,
                                        onClick = {
                                            // Bound: the challenge screen pulls from the cloud
                                            // bank. Unbound: it shows the local QR. Either way
                                            // the question pool lives in the same cache, so the
                                            // entry itself is unconditional.
                                            quizMode = true
                                        },
                                        width = 320.dp,
                                        onFocused = { actionFocused(LockAction.QUIZ, it) },
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                                LockActionButton(
                                    label = stringResource(R.string.policy_manual_confirm),
                                    onClick = {
                                        // The parent PIN is the authority for a local start;
                                        // legacy redemption has no local picker at all.
                                        if (!localPicker) {
                                            promptMessage = R.string.policy_manual_phone_time
                                        } else {
                                            pendingMinutes = null
                                            actionMessage = null
                                            selectedMinutes = MANUAL_DURATION_DEFAULT_MINUTES
                                            durationMode = true
                                        }
                                    },
                                    primary = true,
                                    width = 320.dp,
                                    onFocused = { actionFocused(LockAction.MANUAL, it) },
                                )
                                contentShown = true
                            }
                        }

                        if (contentShown) Spacer(Modifier.height(12.dp))
                        LockActionButton(
                            label = stringResource(R.string.policy_parent_unlock),
                            onClick = {
                                pinPurpose = PinPurpose.UNLOCK
                                entered = ""
                                error = null
                                pinMode = true
                            },
                            width = 320.dp,
                            primary = !studyEntryVisible && !primaryAction,
                            onFocused = { actionFocused(LockAction.PARENT_UNLOCK, it) },
                        )
                        Spacer(Modifier.height(12.dp))
                        LockFooterHint(
                            action = footerAction,
                            state = state,
                            quizState = quizState,
                        )
                        if (actionText != null) {
                            Spacer(Modifier.height(8.dp))
                            BasicText(
                                actionText,
                                style = LockCaptionText.copy(
                                    color = Palette.Orange,
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier.widthIn(max = 360.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A five-row wheel for the manual segment length. Up/down change the value in five-minute
 * steps; OK moves to the parent PIN and Back returns to the lock-screen menu. The white plate
 * stays fixed while the values slide behind it, so the selected length is always in the centre.
 */
@Composable
private fun ManualDurationPicker(
    minutes: Int,
    onStep: (up: Boolean) -> Unit,
    onConfirm: () -> Unit,
    focusRequester: FocusRequester,
) {
    val reducedMotion = LocalReducedMotion.current
    val selectedLabel = stringResource(R.string.policy_parent_minutes, minutes)
    val canGoUp = minutes < MANUAL_DURATION_MAX_MINUTES
    val canGoDown = minutes > MANUAL_DURATION_MIN_MINUTES
    val rangeText = when {
        !canGoDown -> stringResource(
            R.string.policy_manual_duration_min,
            MANUAL_DURATION_MIN_MINUTES,
        )
        !canGoUp -> stringResource(
            R.string.policy_manual_duration_max,
            MANUAL_DURATION_MAX_MINUTES,
        )
        else -> stringResource(
            R.string.policy_manual_duration_range,
            MANUAL_DURATION_MIN_MINUTES,
            MANUAL_DURATION_MAX_MINUTES,
        )
    }
    BasicText(
        stringResource(R.string.policy_manual_choose_duration),
        style = LockHeadingText,
    )
    Spacer(Modifier.height(4.dp))
    BasicText(
        stringResource(R.string.policy_manual_duration_hint),
        style = LockCaptionText.copy(
            color = Palette.SecondaryLabel,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.widthIn(max = 360.dp),
    )
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    NativeKeyEvent.KEYCODE_DPAD_UP -> {
                        onStep(true)
                        true
                    }
                    NativeKeyEvent.KEYCODE_DPAD_DOWN -> {
                        onStep(false)
                        true
                    }
                    NativeKeyEvent.KEYCODE_DPAD_CENTER,
                    NativeKeyEvent.KEYCODE_ENTER,
                    NativeKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (event.nativeKeyEvent.repeatCount == 0) onConfirm()
                        true
                    }
                    else -> false
                }
            }
            .semantics { contentDescription = selectedLabel }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DurationChevron(up = true, enabled = canGoUp)
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(LockWheelHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(LockWheelPillWidth)
                    .height(LockWheelPillHeight)
                    .clip(RoundedCornerShape(LockWheelPillHeight / 2))
                    .background(Palette.Focus),
            )
            AnimatedContent(
                targetState = minutes,
                transitionSpec = {
                    val durationMillis = if (reducedMotion) 0 else 160
                    if (targetState > initialState) {
                        (slideInVertically(tween(durationMillis)) { it / 6 } +
                            fadeIn(tween(durationMillis))) togetherWith
                            (slideOutVertically(tween(durationMillis)) { -it / 6 } +
                                fadeOut(tween(durationMillis)))
                    } else {
                        (slideInVertically(tween(durationMillis)) { -it / 6 } +
                            fadeIn(tween(durationMillis))) togetherWith
                            (slideOutVertically(tween(durationMillis)) { it / 6 } +
                                fadeOut(tween(durationMillis)))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(LockWheelHeight).clipToBounds(),
                contentAlignment = Alignment.Center,
                label = "manual-duration",
            ) { value ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    for (offset in -2..2) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(LockWheelRowHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            val item = value + offset * MANUAL_DURATION_STEP_MINUTES
                            if (item in MANUAL_DURATION_MIN_MINUTES..MANUAL_DURATION_MAX_MINUTES) {
                                BasicText(
                                    stringResource(R.string.policy_parent_minutes, item),
                                    style = when (offset) {
                                        0 -> LockWheelSelectedText
                                        -1, 1 -> LockWheelNeighborText.copy(
                                            color = Palette.Label.copy(alpha = 0.55f),
                                        )
                                        else -> LockWheelFarText.copy(
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
        }
        Spacer(Modifier.height(2.dp))
        DurationChevron(up = false, enabled = canGoDown)
    }
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.width(360.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            rangeText,
            style = LockCaptionText.copy(color = Palette.SecondaryLabel),
            maxLines = 1,
        )
        BasicText(
            stringResource(R.string.policy_manual_duration_cancel),
            style = LockCaptionText.copy(color = Palette.SecondaryLabel),
            maxLines = 1,
        )
    }
}

@Composable
private fun DurationChevron(up: Boolean, enabled: Boolean) {
    GlyphIcon(
        glyph = Glyph.ChevronRight,
        color = Palette.Label.copy(alpha = if (enabled) 0.45f else 0.12f),
        size = 14.dp,
        modifier = Modifier.graphicsLayer { rotationZ = if (up) -90f else 90f },
    )
}

/**
 * The V10 lock-screen identity: source/activation badge, a minute clock, and the estimated next
 * usable time. While the child cannot start, the rule values behind that estimate are shown
 * below it (today's windows, rest, screen-off grace), and automatic mode adds the day's leftover
 * quota. The recipient avatar sits beside the estimate once a grant has supplied one. Everything
 * is laid out around the clock's colon axis so the values read as one composition.
 */
@Composable
private fun LockClockIdentity(
    state: PolicyUiState,
    modeLabel: String,
    hosted: Boolean,
    resumeTick: Int,
    minutes: Int,
    showPending: Boolean,
) {
    val now by rememberNow()
    // Diagnostic for the remote AUTO/MANUAL switch: log the values that actually reach the
    // lock-screen identity, not just the runtime state. A missing line here while the runtime
    // logs a new activation pinpoints a UI publication problem rather than a config problem.
    LaunchedEffect(state.activation, state.source, state.mode, hosted, modeLabel) {
        PolicyFlowTrace.event(
            "lock.identity",
            "activation" to state.activation,
            "source" to state.source,
            "mode" to state.mode,
            "hosted" to hosted,
            "label" to modeLabel,
        )
    }
    GlyphIcon(
        policyModeGlyph(if (hosted) PolicyMode.CLOUD else PolicyMode.LOCAL),
        Palette.Green,
        LockGlyphSize,
    )
    Spacer(Modifier.height(8.dp))
    BasicText(modeLabel, style = LockCaptionText.copy(color = Palette.SecondaryLabel))
    Spacer(Modifier.height(16.dp))
    val (lockDate, lockWeekday) = lockCalendarLines(now)
    LockClock(time = lockClockText(now), date = lockDate, weekday = lockWeekday)
    Spacer(Modifier.height(18.dp))
    val auto = state.activation == Activation.AUTO
    val showQuota = auto &&
        state.localRulesConfigured &&
        !state.setupRequired &&
        !state.noTimedApps &&
        state.sessionState == SessionState.IDLE &&
        state.dailyLimitSeconds > 0L
    val detailReason = lockDetailReason(state)
    val expectedLabel = stringResource(R.string.policy_lock_expected_label)
    val windowsLabel = stringResource(R.string.policy_lock_detail_windows)
    val restLabel = stringResource(R.string.policy_lock_detail_rest)
    val bufferLabel = stringResource(R.string.policy_lock_detail_buffer)
    val quotaLabel = stringResource(R.string.policy_lock_detail_quota)
    // The rule rows start on the first character of "预计可用" rather than at the clock axis.
    // The column is as wide as the longest label so no translation clips, and it is still
    // anchored to the axis on its right so the values keep their place beside the colon.
    val ruleLabelWidth = lockRuleLabelWidth(
        expectedLabel = expectedLabel,
        detailLabels = listOf(windowsLabel, restLabel, bufferLabel, quotaLabel),
    )
    LockSpecRow(
        label = expectedLabel,
        value = expectedAvailableLabel(state, now),
        labelStyle = LockExpectedLabelText,
        valueStyle = LockExpectedValueText,
        // A redemption grant carries the recipient icon; keep it beside the estimate and after
        // the grant is consumed, as before.
        leading = if (state.userIconUrl.isNotBlank()) {
            {
                PolicyAvatar(
                    url = state.userIconUrl,
                    size = LockHintAvatarSize,
                    resting = state.lockReason == LockReason.RESTING &&
                        state.restRemainingSeconds > 0L,
                    blinkKey = resumeTick,
                )
            }
        } else {
            null
        },
    )
    if (detailReason != null) {
        Spacer(Modifier.height(8.dp))
        BasicText(
            detailReason,
            style = LockDetailReasonText.copy(
                color = if (state.lockReason == LockReason.TIME_UP) {
                    Palette.Orange
                } else {
                    Palette.SecondaryLabel
                },
                textAlign = TextAlign.Start,
            ),
            modifier = Modifier
                .width(LockColumnWidth)
                .padding(start = LockSpecLabelWidth - ruleLabelWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        LockRuleDetails(
            state = state,
            showQuota = showQuota,
            labelWidth = ruleLabelWidth,
        )
    } else if (showQuota) {
        // The quota is live status, not one of the configured-rule lines: show it even when the
        // TV is usable now, so the child knows how much is left before starting.
        Spacer(Modifier.height(10.dp))
        LockSpecRow(
            label = quotaLabel,
            value = quotaRemainingLabel(state),
            labelStyle = LockDetailLabelText,
            valueStyle = LockDetailValueText,
            labelWidth = ruleLabelWidth,
            labelAlign = TextAlign.Start,
        )
    }
    if (showPending) {
        Spacer(Modifier.height(10.dp))
        BasicText(
            stringResource(R.string.policy_available_minutes, minutes),
            style = LockBodyText.copy(color = Palette.Label),
        )
        if (state.validUntil.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                state.validUntil,
                style = LockCaptionText.copy(
                    color = Palette.SecondaryLabel,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
    }
}

/**
 * Why the child cannot start now, or null when the estimate is "now"/"none". This follows the
 * lock reason rather than the localized estimate, so changing languages cannot change when the
 * rule details appear.
 */
@Composable
private fun lockDetailReason(state: PolicyUiState): String? {
    if (state.sessionState != SessionState.IDLE) return null
    if (!state.localRulesConfigured || state.setupRequired || state.noTimedApps) return null
    return when {
        state.lockReason == LockReason.RESTING && state.restRemainingSeconds > 0L ->
            stringResource(
                R.string.policy_rest_continue_in,
                durationText(state.restRemainingSeconds),
            )
        state.lockReason == LockReason.TIME_UP ->
            stringResource(R.string.policy_quota_spent)
        state.lockReason == LockReason.OUTSIDE_WINDOW ->
            stringResource(R.string.policy_outside_window)
        state.lockReason == LockReason.MANUAL_WAITING && state.windowRemainingSeconds <= 0L ->
            stringResource(R.string.policy_outside_window)
        state.lockReason == LockReason.AUTO_BLOCKED ->
            stringResource(R.string.policy_lock_detail_paused)
        else -> null
    }
}

/**
 * The configured rules behind the estimate. Four row slots are always reserved while this block
 * is visible: manual mode has no daily quota, and leaving its row blank keeps an AUTO/MANUAL
 * switch from moving the clock.
 */
@Composable
private fun LockRuleDetails(state: PolicyUiState, showQuota: Boolean, labelWidth: Dp) {
    val windows = state.todayWindowsLabel.ifBlank {
        stringResource(R.string.policy_window_none_today)
    }
    Column(modifier = Modifier.width(LockColumnWidth)) {
        LockSpecRow(
            label = stringResource(R.string.policy_lock_detail_windows),
            value = windows,
            labelStyle = LockDetailLabelText,
            valueStyle = LockDetailValueText,
            rowHeight = LockSpecRowHeight,
            labelWidth = labelWidth,
            labelAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(LockSpecRowGap))
        LockSpecRow(
            label = stringResource(R.string.policy_lock_detail_rest),
            value = configuredMinutesLabel(state.configuredRestMinutes),
            labelStyle = LockDetailLabelText,
            valueStyle = LockDetailValueText,
            rowHeight = LockSpecRowHeight,
            labelWidth = labelWidth,
            labelAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(LockSpecRowGap))
        LockSpecRow(
            label = stringResource(R.string.policy_lock_detail_buffer),
            value = configuredMinutesLabel(state.configuredBufferMinutes),
            labelStyle = LockDetailLabelText,
            valueStyle = LockDetailValueText,
            rowHeight = LockSpecRowHeight,
            labelWidth = labelWidth,
            labelAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(LockSpecRowGap))
        if (showQuota) {
            LockSpecRow(
                label = stringResource(R.string.policy_lock_detail_quota),
                value = quotaRemainingLabel(state),
                labelStyle = LockDetailLabelText,
                valueStyle = LockDetailValueText,
                rowHeight = LockSpecRowHeight,
                labelWidth = labelWidth,
                labelAlign = TextAlign.Start,
            )
        } else {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(LockSpecRowHeight),
            )
        }
    }
}

/**
 * The label column width for the rule lines. The lines share a left edge with the first character
 * of the estimate label; [LockSpecLabelWidth] is the right-hand boundary at the clock axis, so the
 * inner label box is right-aligned there and left-aligned inside. Measuring the widest label keeps
 * every translation readable without moving the values off the axis.
 */
@Composable
private fun lockRuleLabelWidth(expectedLabel: String, detailLabels: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density, expectedLabel, detailLabels) {
        val expectedWidth = measurer.measure(
            AnnotatedString(expectedLabel),
            style = LockExpectedLabelText,
            softWrap = false,
            maxLines = 1,
        ).size.width
        val detailWidth = detailLabels.maxOfOrNull { label ->
            measurer.measure(
                AnnotatedString(label),
                style = LockDetailLabelText,
                softWrap = false,
                maxLines = 1,
            ).size.width
        } ?: 0
        with(density) {
            maxOf(expectedWidth, detailWidth).toDp()
        }.coerceAtMost(LockSpecLabelWidth)
    }
}

/**
 * One label/value row split around the clock's colon axis: the label column ends at the axis, the
 * value starts just after it. [labelWidth] left-aligns the label inside that column; the default
 * keeps the estimate label right-aligned against the axis. [rowHeight] fixes the rule-detail rows
 * so the blank quota slot is exactly as tall as the others.
 */
@Composable
private fun LockSpecRow(
    label: String,
    value: String,
    labelStyle: TextStyle,
    valueStyle: TextStyle,
    leading: (@Composable () -> Unit)? = null,
    rowHeight: Dp? = null,
    labelWidth: Dp = LockSpecLabelWidth,
    labelAlign: TextAlign = TextAlign.End,
) {
    Row(
        modifier = Modifier
            .width(LockColumnWidth)
            .then(if (rowHeight != null) Modifier.height(rowHeight) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(LockSpecLabelWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier.width(labelWidth),
                contentAlignment = if (labelAlign == TextAlign.Start) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(10.dp))
                    }
                    BasicText(
                        label,
                        style = labelStyle.copy(textAlign = labelAlign),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(LockSpecGap))
        Box(
            modifier = Modifier.width(LockSpecValueWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                value,
                style = valueStyle.copy(textAlign = TextAlign.Start),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A configured rule duration. Zero means the rule inserts no rest, so say "none". */
@Composable
private fun configuredMinutesLabel(minutes: Int): String =
    if (minutes <= 0) {
        stringResource(R.string.policy_lock_detail_none)
    } else {
        durationText(minutes.toLong() * 60L)
    }

/**
 * The automatic quota left today, rounded down. The lock screen only shows it while no session
 * is running; [PolicyUiState.dailyUsedSeconds] is settled at session end.
 */
@Composable
private fun quotaRemainingLabel(state: PolicyUiState): String {
    val remaining = (state.dailyLimitSeconds - state.dailyUsedSeconds).coerceAtLeast(0L)
    val minutes = remaining / 60L
    return when {
        minutes > 0L -> durationText(minutes * 60L)
        remaining > 0L -> stringResource(R.string.policy_lock_detail_under_minute)
        else -> stringResource(R.string.policy_duration_minutes, 0)
    }
}

/** The original cloud-redemption identity, kept for the legacy flow that has no clock rules. */
@Composable
private fun LegacyLockIdentity(
    state: PolicyUiState,
    modeLabel: String,
    title: String,
    body: String,
    userName: String,
    cloudNeedsGrant: Boolean,
    resumeTick: Int,
    minutes: Int,
    showPending: Boolean,
) {
    // Same source shape and guard color as the desktop status pill: shield for local,
    // cloud for cloud, green while the guard is enforcing.
    GlyphIcon(policyModeGlyph(state.mode), Palette.Green, LockGlyphSize)
    Spacer(Modifier.height(8.dp))
    BasicText(modeLabel, style = LockCaptionText.copy(color = Palette.SecondaryLabel))
    Spacer(Modifier.height(4.dp))
    BasicText(title, style = LockTitleText, modifier = Modifier.widthIn(max = 360.dp))
    Spacer(Modifier.height(4.dp))
    if (cloudNeedsGrant) {
        BasicText(
            stringResource(R.string.policy_cloud_need_grant_body),
            style = LockCaptionText.copy(
                color = Palette.SecondaryLabel,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.widthIn(max = 360.dp),
        )
        Spacer(Modifier.height(4.dp))
        BasicText(
            stringResource(R.string.policy_cloud_need_grant_hint),
            style = LockCaptionText.copy(textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(max = 360.dp),
        )
    } else {
        BasicText(userName, style = LockBodyText.copy(color = Palette.SecondaryLabel))
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            BasicText(
                body,
                style = LockCaptionText.copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        LockTimeSummary(state = state, v10 = false)
    }
    Spacer(Modifier.height(18.dp))
    PolicyAvatar(
        url = state.userIconUrl,
        size = LockAvatarSize,
        resting = state.lockReason == LockReason.RESTING &&
            state.restRemainingSeconds > 0L,
        // Blink again after standby so the animation is visible when the screen wakes.
        blinkKey = resumeTick,
    )
    if (showPending) {
        Spacer(Modifier.height(12.dp))
        BasicText(
            stringResource(R.string.policy_available_minutes, minutes),
            style = LockHeadingText,
        )
        if (state.validUntil.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                state.validUntil,
                style = LockCaptionText.copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
    }
}

/** The lock-screen clock, always 24-hour and zero-padded so 09:05 never renders as 9:5. */
internal fun lockClockText(now: LocalDateTime): String =
    String.format(Locale.ROOT, "%02d:%02d", now.hour, now.minute)

/**
 * The lock-screen clock has no tile or border: it sits directly on the glass panel so it reads as
 * part of the background. The fixed box only reserves layout space, which keeps the identity column
 * from shifting as the digits change. Minute changes are direct: no second hand, no per-second
 * redraw.
 *
 * The date and weekday stack in two lines to the right of the digits, using the empty shoulder of
 * the 400 dp identity column. The weekday's baseline shares the digits' bottom, so the block hangs
 * off the clock's lower-right edge; the date sits above it with a 2 dp line gap. The time itself
 * keeps its centred position, so the badge and rows around it do not move.
 */
@Composable
private fun LockClock(time: String, date: String, weekday: String) {
    Layout(
        modifier = Modifier.width(LockColumnWidth).height(LockClockHeight),
        content = {
            BasicText(time, style = LockClockText, maxLines = 1)
            BasicText(date, style = LockClockDateText, maxLines = 1)
            BasicText(weekday, style = LockClockWeekdayText, maxLines = 1)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val clock = measurables[0].measure(loose)
        val dateLine = measurables[1].measure(loose)
        val weekdayLine = measurables[2].measure(loose)
        val clockLeft = (constraints.maxWidth - clock.width) / 2
        val clockTop = (constraints.maxHeight - clock.height) / 2
        // Bottom-baseline alignment: the weekday starts where the digits end.
        val clockBaseline = clockTop + clock[FirstBaseline]
        val weekdayTop = clockBaseline - weekdayLine[FirstBaseline]
        val dateTop = weekdayTop - LockClockLineGap.roundToPx() - dateLine.height
        // The block begins after the last digit, not after the old clock tile's edge, so the
        // gap stays optically tight for every minute.
        val blockLeft = clockLeft + clock.width + LockClockDateGap.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            clock.place(clockLeft, clockTop)
            dateLine.place(blockLeft, dateTop)
            weekdayLine.place(blockLeft, weekdayTop)
        }
    }
}

/**
 * Locale-aware short month/day and full weekday, formatted separately so they can stack. Android's
 * SimpleDateFormat (ICU) is used instead of java.time: through desugaring the latter gives Russian
 * months their stand-alone form in dates.
 */
@Composable
private fun lockCalendarLines(now: LocalDateTime): Pair<String, String> {
    val locale = Locale.getDefault()
    val patterns = remember(locale) {
        DateFormat.getBestDateTimePattern(locale, "MMMd") to
            DateFormat.getBestDateTimePattern(locale, "EEEE")
    }
    val millis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return SimpleDateFormat(patterns.first, locale).format(Date(millis)) to
        SimpleDateFormat(patterns.second, locale).format(Date(millis))
}

/**
 * The child-facing estimate. It is deliberately called an estimate: rest ending, a window
 * opening or a parent being nearby never guarantees that entertainment can start.
 */
@Composable
private fun expectedAvailableLabel(state: PolicyUiState, now: LocalDateTime): String {
    val none = stringResource(R.string.policy_lock_expected_none)
    val nowLabel = stringResource(R.string.policy_lock_expected_now)
    val tomorrow = stringResource(R.string.policy_tomorrow)
    if (state.setupRequired || state.noTimedApps || !state.localRulesConfigured) return none
    return when {
        state.sessionState == SessionState.PENDING -> nowLabel
        state.lockReason == LockReason.RESTING && state.restRemainingSeconds > 0L ->
            clockLabel(now, state.restRemainingSeconds, tomorrow) ?: none
        // Quota resets at the next local day; the exact first window is not in this state.
        state.lockReason == LockReason.TIME_UP -> tomorrow
        state.lockReason == LockReason.AUTO_BLOCKED ->
            clockLabel(now, state.nextWindowInSeconds, tomorrow, roundUp = true) ?: none
        state.lockReason == LockReason.OUTSIDE_WINDOW ->
            clockLabel(now, state.nextWindowInSeconds, tomorrow, roundUp = true) ?: none
        state.lockReason == LockReason.MANUAL_WAITING && state.windowRemainingSeconds <= 0L ->
            clockLabel(now, state.nextWindowInSeconds, tomorrow, roundUp = true) ?: none
        state.lockReason == LockReason.MANUAL_WAITING ||
            state.lockReason == LockReason.AUTO_READY ||
            state.lockReason == LockReason.NONE ||
            state.lockReason == LockReason.PARENT_STOP -> nowLabel
        else -> none
    }
}

@Composable
private fun clockLabel(
    now: LocalDateTime,
    seconds: Long,
    tomorrow: String,
    roundUp: Boolean = false,
): String? {
    if (seconds <= 0L) return null
    // The countdown comes from a snapshot, while [now] ticks on the minute. Rounding a window
    // start up to the next whole minute avoids showing 14:59 for a 15:00 window when the two
    // snapshots are seconds apart.
    val target = if (roundUp) {
        now.truncatedTo(ChronoUnit.MINUTES).plusMinutes((seconds + 59L) / 60L)
    } else {
        now.plusSeconds(seconds)
    }
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())
    if (days > 1L) return null
    val time = timeText(target)
    return if (days == 1L) "$tomorrow $time" else time
}

/**
 * The one hint under the action list. It describes the action that currently owns focus and
 * never repeats the source badge above ("local" already means unbound, "remote" means bound).
 */
@Composable
private fun LockFooterHint(
    action: LockAction,
    state: PolicyUiState,
    quizState: QuizRuntime.UiState,
) {
    val text = when (action) {
        LockAction.STUDY -> stringResource(
            if (state.studyModeAvailable) {
                R.string.policy_lock_study_footer
            } else {
                R.string.policy_lock_study_empty_footer
            },
        )
        LockAction.QUIZ -> when {
            quizState.sessionState == "running" -> stringResource(R.string.policy_lock_quiz_running_footer)
            quizState.sessionState == "ready" ||
                quizState.sessionState == "settling" ||
                quizState.sessionState == "starting" ||
                quizState.sessionState == "start_failed" ->
                stringResource(
                    if (quizState.source == QuizSource.CLOUD) {
                        R.string.policy_lock_quiz_remote_footer
                    } else {
                        R.string.policy_lock_quiz_local_footer
                    },
                )
            quizState.bankState != "ready" -> stringResource(R.string.policy_lock_quiz_empty_footer)
            quizState.source == QuizSource.CLOUD -> stringResource(R.string.policy_lock_quiz_remote_footer)
            else -> stringResource(R.string.policy_lock_quiz_local_footer)
        }
        LockAction.MANUAL -> stringResource(
            // The legacy controlled path is the only bound mode that still starts on the
            // phone; hosted rule mode uses the local picker and local footer.
            if (state.bound && state.source != PolicySource.HOSTED) {
                R.string.policy_lock_manual_remote_footer
            } else {
                R.string.policy_lock_manual_local_footer
            },
        )
        LockAction.AUTO_START -> stringResource(
            if (state.source == PolicySource.HOSTED) {
                R.string.policy_lock_auto_remote_footer
            } else {
                R.string.policy_lock_auto_local_footer
            },
        )
        LockAction.CONFIRM_START -> stringResource(R.string.policy_pending_hint)
        LockAction.PARENT_UNLOCK -> stringResource(R.string.policy_lock_parent_footer)
    }
    Box(
        modifier = Modifier.width(360.dp).height(LockFooterHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        BasicText(
            text,
            style = LockCaptionText.copy(
                color = Palette.SecondaryLabel,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The child-facing answer to "when can I watch, and for how long?". The schedule and the
 * remaining time sit together so the lock screen does not need a separate rules page.
 */
@Composable
private fun LockTimeSummary(state: PolicyUiState, v10: Boolean) {
    if (!v10) {
        // Legacy cloud redemption has no local window; keep its rest/next-window hint.
        if (state.restRemainingSeconds > 0L || state.nextWindowLabel.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                if (state.restRemainingSeconds > 0L) {
                    stringResource(R.string.policy_local_resting, state.restRemainingSeconds.toInt())
                } else {
                    state.nextWindowLabel
                },
                style = LockCaptionText.copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        return
    }

    val auto = state.activation == Activation.AUTO
    val usedMinutes = (state.dailyUsedSeconds / 60L).toInt()
    val dailyRemaining = (state.dailyLimitSeconds - state.dailyUsedSeconds).coerceAtLeast(0L)
    val windowRemaining = state.windowRemainingSeconds
    val sessionLimit = state.sessionLimitSeconds
    // What one more automatic segment can actually run: today's quota and the per-session
    // cap. The window only gates the start, so a session begun just before its end still
    // gets the full remaining segment.
    val availableNow = if (auto && state.sessionState != SessionState.PENDING) {
        minOf(
            dailyRemaining,
            sessionLimit.takeIf { it > 0L } ?: Long.MAX_VALUE,
        ).let { if (it == Long.MAX_VALUE) 0L else it }
    } else {
        0L
    }

    Spacer(Modifier.height(8.dp))
    BasicText(
        if (state.todayWindowsLabel.isNotBlank()) {
            stringResource(R.string.policy_window_today, state.todayWindowsLabel)
        } else {
            stringResource(R.string.policy_window_none_today)
        },
        style = LockCaptionText.copy(color = Palette.SecondaryLabel, textAlign = TextAlign.Center),
        modifier = Modifier.widthIn(max = 360.dp),
    )

    // The one line a child should notice first: when the TV is usable and for how long.
    val guidance = when {
        state.lockReason == LockReason.RESTING && state.restRemainingSeconds > 0L ->
            stringResource(
                R.string.policy_rest_continue_in,
                durationText(state.restRemainingSeconds),
            )
        state.lockReason == LockReason.TIME_UP -> stringResource(R.string.policy_quota_spent)
        state.lockReason == LockReason.OUTSIDE_WINDOW && state.nextWindowInSeconds > 0L ->
            stringResource(R.string.policy_ready_in, durationText(state.nextWindowInSeconds))
        // Manual mode locks as MANUAL_WAITING even outside a window; the child still needs
        // the same "when can I watch" answer.
        !auto && state.lockReason == LockReason.MANUAL_WAITING &&
            windowRemaining <= 0L && state.nextWindowInSeconds > 0L ->
            stringResource(R.string.policy_ready_in, durationText(state.nextWindowInSeconds))
        // "Available now" only makes sense when a start action is actually offered. Other
        // automatic lock reasons either have their own line or must not imply the TV is usable.
        auto && availableNow > 0L &&
            (state.lockReason == LockReason.AUTO_READY || state.lockReason == LockReason.NONE) ->
            stringResource(R.string.policy_can_watch_for, durationText(availableNow))
        else -> ""
    }
    if (guidance.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        BasicText(
            guidance,
            style = LockHeadingText.copy(textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(max = 360.dp),
        )
    }

    if (auto && windowRemaining > 0L && dailyRemaining > 0L &&
        state.lockReason != LockReason.AUTO_BLOCKED
    ) {
        Spacer(Modifier.height(2.dp))
        BasicText(
            stringResource(R.string.policy_window_left, durationText(windowRemaining)),
            style = LockCaptionText.copy(textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(max = 360.dp),
        )
    }

    Spacer(Modifier.height(2.dp))
    BasicText(
        if (auto) {
            stringResource(
                R.string.policy_local_used_today,
                usedMinutes,
                (state.dailyLimitSeconds / 60L).toInt(),
            )
        } else {
            stringResource(R.string.policy_manual_used_today, usedMinutes)
        },
        style = LockCaptionText.copy(textAlign = TextAlign.Center),
        modifier = Modifier.widthIn(max = 360.dp),
    )

    val showNextWindow = state.nextWindowLabel.isNotBlank() && when (state.lockReason) {
        LockReason.TIME_UP, LockReason.AUTO_BLOCKED, LockReason.OUTSIDE_WINDOW -> true
        LockReason.MANUAL_WAITING -> !auto && windowRemaining <= 0L
        else -> false
    }
    if (showNextWindow) {
        Spacer(Modifier.height(2.dp))
        BasicText(
            state.nextWindowLabel,
            style = LockCaptionText.copy(textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(max = 360.dp),
        )
    }
}

@Composable
private fun durationText(seconds: Long): String {
    val totalMinutes = ((seconds.coerceAtLeast(0L) + 59L) / 60L).coerceAtLeast(1L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 ->
            stringResource(R.string.policy_duration_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(R.string.policy_duration_hours, hours)
        else -> stringResource(R.string.policy_duration_minutes, minutes)
    }
}

private fun rejectMessage(reason: RejectReason): Int = when (reason) {
    RejectReason.RESTING -> R.string.policy_resting_title
    RejectReason.OUTSIDE_WINDOW -> R.string.policy_outside_window
    RejectReason.NO_TIMED_APPS, RejectReason.INVALID_SCOPE -> R.string.policy_no_timed_apps_title
    else -> R.string.policy_action_rejected
}

@Composable
private fun PinKeypad(
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    firstKeyFocus: FocusRequester,
    leftKeyLabel: String? = null,
    leftKeyStyle: TextStyle = LockActionText,
    onLeftKey: (() -> Unit)? = null,
) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { digit ->
                    LockActionButton(
                        label = digit.toString(),
                        onClick = { onDigit(digit) },
                        focusRequester = if (digit == '1') firstKeyFocus else null,
                        width = 90.dp,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Same three-column footprint as the rows above, with 0 centered and erase at
            // the right, like a phone lock screen. The left key (back or recovery) takes
            // the empty slot without moving 0: it is exactly as wide as the spacer it
            // replaces.
            if (onLeftKey != null) {
                LockActionButton(
                    label = leftKeyLabel.orEmpty(),
                    onClick = onLeftKey,
                    width = 90.dp,
                    labelStyle = leftKeyStyle,
                )
            } else {
                Spacer(Modifier.width(90.dp).height(56.dp))
            }
            LockActionButton(
                label = "0",
                onClick = { onDigit('0') },
                width = 90.dp,
            )
            LockActionButton(
                label = stringResource(R.string.policy_delete),
                onClick = onErase,
                width = 90.dp,
            )
        }
    }
}

/**
 * A lock-screen action at the large size. Other screens keep the compact [LockButton]
 * defaults, so the parent centre and the setup flows do not grow with this screen.
 */
@Composable
private fun LockActionButton(
    label: String,
    onClick: () -> Unit,
    width: Dp = LockActionWidth,
    focusRequester: FocusRequester? = null,
    primary: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
    labelStyle: TextStyle = LockActionText,
    onFocused: ((Boolean) -> Unit)? = null,
) {
    LockButton(
        label = label,
        onClick = onClick,
        width = width,
        focusRequester = focusRequester,
        primary = primary,
        accent = accent,
        enabled = enabled,
        height = LockActionHeight,
        labelStyle = labelStyle,
        onFocused = onFocused,
    )
}

/** A dimmed start action. The left column carries the reason and countdown. */
@Composable
private fun FrozenAction(label: String) {
    LockActionButton(
        label = label,
        onClick = {},
        width = 320.dp,
        enabled = false,
    )
}

/**
 * A small modal for an entry a bound TV cannot open locally. It owns the whole screen while it
 * is up, so no sheet button can take focus; Back or the button returns to the lock screen.
 */
@Composable
private fun PolicyLockPrompt(message: Int, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    BackHandler { onDismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        PolicyParentBackdrop()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Column(
            modifier = Modifier
                .widthIn(max = 660.dp)
                .liquidGlass(LockStageCorner)
                .padding(horizontal = 36.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                stringResource(message),
                style = LockBodyText.copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = 560.dp),
            )
            Spacer(Modifier.height(18.dp))
            LockActionButton(
                label = stringResource(R.string.policy_back),
                onClick = onDismiss,
                width = 200.dp,
                primary = true,
                focusRequester = focus,
            )
        }
    }
}

@Composable
internal fun LockButton(
    label: String,
    onClick: () -> Unit,
    width: Dp,
    focusRequester: FocusRequester? = null,
    primary: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    labelStyle: TextStyle = Type.Headline,
    onFocused: ((Boolean) -> Unit)? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
) {
    Pressable(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        focusedScale = 1.05f,
        onFocused = onFocused,
        onKey = onKey,
        modifier = Modifier.width(width),
    ) { state ->
        val background = when {
            !enabled -> Palette.Fill.copy(alpha = 0.55f)
            state.focused -> Palette.Focus
            accent -> Palette.Orange
            primary -> Palette.FillStrong
            else -> Palette.Fill
        }
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(13.dp))
                .background(background)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                label,
                style = labelStyle.copy(
                    color = when {
                        !enabled -> Palette.SecondaryLabel
                        state.focused -> Palette.OnFocus
                        accent -> Palette.OnFocus
                        else -> Palette.Label
                    },
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
