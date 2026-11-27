package app.orchard.tv.ui.policy

import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.orchard.tv.R
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.quiz.CloudQuizPreparer
import app.orchard.tv.quiz.QuizChallenge
import app.orchard.tv.quiz.QuizConfigDocumentSync
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.QuizAudioGate
import app.orchard.tv.policy.media.PolicyVideoLibrary
import app.orchard.tv.quiz.QuizRecovery
import app.orchard.tv.quiz.QuizResultTone
import app.orchard.tv.quiz.QuizRuntime
import app.orchard.tv.quiz.QuizSpeechPlayer
import app.orchard.tv.quiz.cloudPlanMessage
import app.orchard.tv.system.Sounds
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.glassEdge
import app.orchard.tv.ui.components.isSelect
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.LocalQuizTextMetrics
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.ui.theme.quizTextMetrics
import app.orchard.tv.ui.wallpaper.latestBringIntoView
import app.orchard.tv.wallpaper.PhoneServer
import com.planyourtv.policy.PolicyVoiceBus
import com.planyourtv.policy.PolicyVoiceCue
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizRewardProfile
import com.planyourtv.policy.quiz.QuizScoring
import com.planyourtv.policy.quiz.QuizScoringV6
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizSubmission
import com.planyourtv.policy.quiz.normalizeAnswerHoldSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Quiz-only score colors. Gold means the child earned something (a correct
 * answer, a reward, an unlock); green means a deduction or an unmet threshold.
 * Green is a success color everywhere else in the app, so this inversion must
 * stay local to the quiz and must not be promoted into [Palette].
 */
private val QuizGold = Color(0xFFFFD45A)
private val QuizLoss = Palette.Green
// Format is not a score, so it must not borrow the quiz's green loss or gold
// reward colors. Blue marks single choice, orange marks multiple choice.
private val QuizFormatSingle = Color(0xFF64D2FF)

/**
 * The lock-screen quiz entry. It has three faces: the parent setup QR while a
 * session is missing or still a draft, the challenge itself once READY, and the
 * result after the TV has settled the round. Question data never leaves this
 * screen except through the encrypted session record.
 */
@Composable
fun PolicyQuizScreen(
    /**
     * The entertainment app that opened this quiz, or null for the dedicated quiz tile.
     * Only a successful start opens it; every other outcome returns to the desktop.
     */
    target: AppEntry?,
    onClose: () -> Unit,
) {
    val state by QuizRuntime.state.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    // The correct-answer reward flash lives above the round face, so the last
    // answer's flash still plays while the report replaces the round.
    var reward by remember { mutableStateOf<QuizReward?>(null) }
    // A bound TV always goes through the cloud preparer first; the local QR
    // setup belongs exclusively to unbound TVs.
    val hosted = QuizChallenge.isHosted()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The pool is small and quiz-scoped: preload it while the start film plays,
    // then release it as soon as the child leaves the challenge.
    DisposableEffect(context) {
        QuizSfx.install(context)
        onDispose {
            QuizSfx.release()
            // Leaving the challenge must not leave a synthesized question
            // talking over the desktop or the target app.
            QuizSpeechPlayer.stop()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Import a legacy local READY session or finish a round interrupted
            // by process death before choosing the screen branch.
            QuizRecovery.reconcile()
            QuizRuntime.refresh()
            // The lock screen keeps an expired record only long enough to explain
            // it. Opening the entry clears it and falls through to the setup QR.
            if (QuizRuntime.state.value.sessionState == "expired") QuizChallenge.abandon()
        }
        loaded = true
    }

    fun closeCleanly() {
        QuizSpeechPlayer.stop()
        onClose()
    }

    fun abandonAndClose() {
        scope.launch {
            withContext(Dispatchers.IO) { QuizChallenge.abandon() }
            closeCleanly()
        }
    }

    BackHandler {
        // Back may open the exit dialog before the child commits; stop the
        // reading so it does not talk over the confirmation.
        QuizSpeechPlayer.stop()
        when {
            confirmExit -> confirmExit = false
            state.sessionState == "running" -> confirmExit = true
            // The report screen owns Back while it is visible: it completes
            // the review, dismisses after a failed settlement, or exits after
            // a failed start.
            state.sessionState == "settling" ||
                state.sessionState == "starting" ||
                state.sessionState == "start_failed" -> Unit
            else -> closeCleanly()
        }
    }

    // A timeout can open the feedback page while the exit confirmation is
    // still up. Close the dialog when the round leaves the question face, so
    // it cannot sit over the feedback with focus on the page behind it.
    val feedbackOpen = state.session?.run?.feedbackOpen == true
    LaunchedEffect(state.sessionState, feedbackOpen) {
        if (state.sessionState != "running" || feedbackOpen) confirmExit = false
    }

    // The quiz typography is resolved once per round from the frozen session
    // config and this TV's real pixel height; the rest of the app keeps Type.
    val density = LocalDensity.current.density
    val displayMetrics = context.resources.displayMetrics
    val textConfig = state.session?.config ?: state.defaultChallengeConfig
    val quizMetrics = remember(textConfig, displayMetrics.heightPixels, density) {
        quizTextMetrics(textConfig, displayMetrics.heightPixels, density)
    }
    CompositionLocalProvider(LocalQuizTextMetrics provides quizMetrics) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101827), Color(0xFF03060C)))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !loaded -> QuizLoading()
            state.sessionState == "ready" && !hosted -> QuizReadyScreen(
                session = state.session ?: return@Box,
                questionCount = state.readyQuestionCount,
                answerScope = state.answerScope,
                onClose = ::closeCleanly,
            )
            state.sessionState == "running" -> QuizRoundScreen(
                session = state.session ?: return@Box,
                onExit = { confirmExit = true },
                onReward = { reward = it },
            )
            state.sessionState == "settling" ||
                state.sessionState == "starting" ||
                state.sessionState == "start_failed" -> QuizReviewScreen(
                session = state.session ?: return@Box,
                startTarget = target?.packageName,
                onComplete = ::closeCleanly,
                // A gateway entry starts the target from the host, above the lock screen;
                // this composition is usually gone by the time the settle returns.
                onStarted = { if (target == null) closeCleanly() },
                // Keep the round when the screen is dismissed after a failed
                // settlement: the child can re-enter the quiz and retry, and
                // Back must not trap them on the report.
                onDismiss = ::closeCleanly,
                onExit = ::abandonAndClose,
            )
            hosted -> QuizCloudSetupScreen(
                state = state,
                onClose = ::closeCleanly,
            )
            // A leftover or unreadable record can only be reset through the
            // parent page, so keep the setup QR available in that case.
            state.sessionState != "none" || !state.enabled -> QuizSetupScreen(
                onClose = ::closeCleanly,
            )
            // An empty or short bank still opens the level picker: the page already carries the
            // level list, the scan hint and the parent's QR, and the availability check waits
            // for the child's own start press instead of a separate full-screen notice.
            state.bankState == "blocked" || state.bankState == "paused" -> QuizBankEmptyScreen(
                state = state,
                onClose = ::closeCleanly,
            )
            else -> QuizBankStartScreen(
                state = state,
                onClose = ::closeCleanly,
            )
        }

        reward?.let { burst ->
            QuizRewardBurst(burst, onDone = { reward = null })
        }

        if (confirmExit) {
            QuizExitConfirm(
                onCancel = { confirmExit = false },
                onConfirm = {
                    confirmExit = false
                    abandonAndClose()
                },
            )
        }
    }
    }
}

@Composable
private fun QuizLoading() {
    BasicText(
        stringResource(R.string.policy_quiz_loading),
        style = Type.Caption.copy(color = Palette.SecondaryLabel),
    )
}

/**
 * Parent setup. The QR is a fixed LAN URL for this TV; the web page asks for the
 * parent PIN once and then remembers that phone. It cannot configure anything
 * except the quiz.
 */
@Composable
private fun QuizSetupScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val state by QuizRuntime.state.collectAsState()
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var url by remember { mutableStateOf<String?>(null) }
    var qrLoading by remember { mutableStateOf(true) }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val link = withContext(Dispatchers.IO) {
            PhoneServer.start(context)?.let { "${it}quiz" }
        }
        url = link
        qr = link?.let {
            withContext(Dispatchers.Default) {
                runCatching { PhoneServer.qr(it, scale = 12).asImageBitmap() }.getOrNull()
            }
        }
        qrLoading = false
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2_000)
            withContext(Dispatchers.IO) { QuizRuntime.refresh() }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backFocus.requestFocus() }
    }

    val ready = state.sessionState == "ready"
    val title = when (state.sessionState) {
        "ready" -> stringResource(R.string.policy_quiz_ready_title)
        "running", "settling", "starting" -> stringResource(R.string.policy_quiz_running_title)
        else -> stringResource(R.string.policy_quiz_scan_title)
    }
    val body = when (state.sessionState) {
        "ready" -> stringResource(
            R.string.policy_quiz_ready_body,
            state.readyQuestionCount,
            state.challengeConfig.minCorrectCount,
            state.challengeConfig.minutesPerQuestion,
        )
        "running", "settling", "starting" -> stringResource(R.string.policy_quiz_running_body)
        else -> stringResource(R.string.policy_quiz_scan_body)
    }

    Row(
        modifier = Modifier
            .widthIn(max = 900.dp)
            .liquidGlass(28.dp)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(236.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                qr?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: BasicText(
                    stringResource(
                        if (qrLoading) R.string.policy_quiz_qr_loading else R.string.policy_quiz_offline,
                    ),
                    style = Type.Caption.copy(
                        color = Color(0xFF444B57),
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.padding(12.dp),
                )
            }
            url?.let {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    it,
                    style = Type.Caption.copy(
                        color = Palette.SecondaryLabel,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.widthIn(max = 236.dp),
                )
            }
        }

        Column(modifier = Modifier.width(470.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Shield, Palette.Label, 22.dp)
                Spacer(Modifier.width(10.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_kicker),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
            }
            Spacer(Modifier.height(10.dp))
            BasicText(title, style = Type.LargeTitle)
            Spacer(Modifier.height(8.dp))
            BasicText(
                body,
                style = Type.Body.copy(color = Palette.SecondaryLabel),
                modifier = Modifier.widthIn(max = 450.dp),
            )
            if (!ready) {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_scan_hint),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                    modifier = Modifier.widthIn(max = 450.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            LockButton(
                label = stringResource(R.string.policy_back),
                onClick = onClose,
                width = 220.dp,
                focusRequester = backFocus,
                onKey = linearDpadFocus(),
            )
        }
    }
}

internal data class QuizQr(
    val url: String?,
    val bitmap: ImageBitmap?,
    val loading: Boolean,
)

/**
 * The quiz page link is stable for the life of the TV. A failed start leaves the
 * bitmap null so the caller can explain the offline state instead of showing an
 * empty square.
 */
@Composable
internal fun rememberQuizQr(
    scale: Int,
    attempt: Int = 0,
    enabled: Boolean = true,
    fragment: String? = null,
): QuizQr {
    val context = LocalContext.current
    var qr by remember { mutableStateOf(QuizQr(null, null, true)) }
    LaunchedEffect(scale, attempt, enabled, fragment) {
        // A pre-drawn but hidden panel may compose this; only a visible one may start the
        // phone server or spend CPU on the bitmap.
        if (!enabled) return@LaunchedEffect
        qr = QuizQr(qr.url, qr.bitmap, true)
        val link = withContext(Dispatchers.IO) {
            PhoneServer.start(context)?.let { "${it}quiz${fragment.orEmpty()}" }
        }
        val bitmap = link?.let {
            withContext(Dispatchers.Default) {
                runCatching { PhoneServer.qr(it, scale = scale).asImageBitmap() }.getOrNull()
            }
        }
        qr = QuizQr(link, bitmap, false)
    }
    return qr
}

@Composable
private fun QuizQrCard(qr: QuizQr, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        qr.bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: BasicText(
            stringResource(
                if (qr.loading) R.string.policy_quiz_qr_loading else R.string.policy_quiz_offline,
            ),
            style = Type.Caption.copy(
                color = Color(0xFF444B57),
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.padding(12.dp),
        )
    }
}

/**
 * The shared film-first start surface. The glass card and QR rise in with a
 * spring on the first frame after entry, over the film rather than after it;
 * the film keeps playing as the background. OK is swallowed until the card is
 * visible so repeated presses can never pick a level or begin a round. A round
 * starts on a fresh OK press once the card is visible.
 */
@Composable
private fun QuizStartShell(
    busy: Boolean,
    leaving: Boolean,
    onStart: () -> Unit,
    onStep: ((Int) -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    var revealed by remember { mutableStateOf(false) }
    var inputReady by remember { mutableStateOf(false) }
    var okPressed by remember { mutableStateOf(false) }
    var pendingRelease by remember { mutableStateOf<Int?>(null) }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Resolve once per entry and per child: a film selected in the parent center applies to the
    // next quiz entry, never half-way through this screen, and each member keeps their own
    // intro. Null means Aerial's default wallpaper, which is also the out-of-the-box state.
    val activeChild by FamilyHub.state.collectAsState()
    val introSource = remember(context, activeChild.activeUserId) {
        PolicyVideoLibrary.quizIntro(context)
    }

    fun reveal(byKey: Int?) {
        if (revealed || leaving) return
        revealed = true
        pendingRelease = byKey
        if (byKey != null) Sounds.focusMoved()
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
        // A focus request can lose the race with the screen transition; one
        // quiet retry covers that without stealing focus later.
        delay(80)
        runCatching { focus.requestFocus() }
    }
    LaunchedEffect(Unit) {
        delay(ENTRY_INPUT_GUARD_MS)
        inputReady = true
    }
    LaunchedEffect(Unit) {
        // The card is the point of this screen, not a reward for watching the
        // film. Reveal it on the first frame after layout so entry never waits
        // for the video or for a timer that the startup jank can push back.
        // The player's end callback still runs, but reveal() ignores it once
        // the card is already visible.
        withFrameNanos { }
        reveal(null)
    }
    LaunchedEffect(reduced) {
        if (reduced) reveal(null)
    }
    // A key-up can be lost when the screen is backgrounded mid-press; a short
    // timer keeps that from locking the card out of navigation and OK.
    LaunchedEffect(pendingRelease) {
        val pending = pendingRelease ?: return@LaunchedEffect
        delay(PRESS_RELEASE_GUARD_MS)
        if (pendingRelease == pending) pendingRelease = null
    }

    val revealProgress by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = if (reduced) tween(0) else tween(240, easing = Motion.EaseOut),
        label = "quiz-start-reveal",
    )
    // The card pops in like a game panel: a fast spring with a visible
    // overshoot, while the alpha and rise stay on a tween so the text never
    // fades in and out again.
    val revealScale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.84f,
        animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.55f, stiffness = 480f),
        label = "quiz-start-pop",
    )

    Box(Modifier.fillMaxSize()) {
        QuizStartBackdrop(
            intro = introSource,
            fadeOut = busy || leaving,
            onIntroFinished = {
                scope.launch {
                    // Let the final frame land before the card covers part of it.
                    delay(INTRO_END_BEAT_MS)
                    reveal(null)
                }
            },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    val down = event.type == KeyEventType.KeyDown
                    val up = event.type == KeyEventType.KeyUp
                    val repeat = event.nativeKeyEvent.repeatCount > 0
                    if (!isStartNavKey(code) && !event.isSelect()) {
                        return@onPreviewKeyEvent false
                    }

                    // Swallow the key-up that opened this page and the first
                    // moments of the transition, so neither can reveal or start.
                    if (!inputReady) return@onPreviewKeyEvent true

                    if (!revealed) {
                        // Only a direction key reveals the card. OK is
                        // swallowed here on purpose: a child mashing OK should
                        // not reveal the card and then start a round with the
                        // next press.
                        if (down && !repeat && isStartNavKey(code)) {
                            reveal(code)
                        } else if (up && pendingRelease == code) {
                            pendingRelease = null
                        }
                        return@onPreviewKeyEvent true
                    }

                    if (pendingRelease != null) {
                        // The press that revealed the card must be fully
                        // released before the card accepts navigation or OK.
                        if (up) pendingRelease = null
                        return@onPreviewKeyEvent true
                    }

                    if (busy || leaving) {
                        okPressed = false
                        return@onPreviewKeyEvent true
                    }

                    if (event.isSelect()) {
                        if (down && !repeat) {
                            okPressed = true
                        } else if (up) {
                            val pressed = okPressed
                            okPressed = false
                            if (pressed) {
                                Sounds.select()
                                onStart()
                            }
                        }
                        return@onPreviewKeyEvent true
                    }

                    if (down && !repeat) {
                        when (code) {
                            NativeKeyEvent.KEYCODE_DPAD_UP -> onStep?.invoke(-1)
                            NativeKeyEvent.KEYCODE_DPAD_DOWN -> onStep?.invoke(1)
                            // Left/right only reveal the card from the film;
                            // the level wheel stays a vertical up/down list.
                            else -> Unit
                        }
                    }
                    true
                },
        ) {
            // A light scrim separates the glass card from the moving film
            // without hiding the picture behind it.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f * revealProgress)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = revealProgress
                        translationY = (1f - revealProgress) * 30.dp.toPx()
                        scaleX = revealScale
                        scaleY = revealScale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
            ) {
                content()
            }
        }
    }
}

/**
 * The footer that replaces the old Start/Back buttons: the keys are physically
 * on the remote, so the card shows what they will do without adding a focusable
 * control that could steal the level wheel's keys. It is deliberately flat and
 * muted so it reads as a caption, not as another button row.
 */
@Composable
private fun QuizKeyHintRow(busy: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuizKeyHint(
            key = stringResource(R.string.policy_quiz_key_ok),
            action = stringResource(
                if (busy) R.string.policy_quiz_preparing else R.string.policy_quiz_start,
            ),
        )
        Spacer(Modifier.width(24.dp))
        QuizKeyHint(
            key = stringResource(R.string.policy_back),
            action = stringResource(R.string.policy_quiz_back_hint),
        )
    }
}

@Composable
private fun QuizKeyHint(key: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            key,
            style = Type.Caption.copy(
                color = Palette.SecondaryLabel.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.width(6.dp))
        BasicText(
            action,
            style = Type.Caption.copy(color = Palette.SecondaryLabel.copy(alpha = 0.75f)),
        )
    }
}

private fun isStartNavKey(code: Int): Boolean = when (code) {
    NativeKeyEvent.KEYCODE_DPAD_UP,
    NativeKeyEvent.KEYCODE_DPAD_DOWN,
    NativeKeyEvent.KEYCODE_DPAD_LEFT,
    NativeKeyEvent.KEYCODE_DPAD_RIGHT -> true
    else -> false
}

private const val ENTRY_INPUT_GUARD_MS = 350L
private const val PRESS_RELEASE_GUARD_MS = 700L
private const val INTRO_END_BEAT_MS = 200L

/**
 * Unbound start face. The child sees the same round summary as the cloud start and the parent
 * keeps a small QR to add more questions between rounds. An empty or short bank stays on this
 * face too: the level list (or the missing-level hint) and the QR are already here, so there is
 * no separate scan screen and the availability check runs only on the child's start press.
 */
@Composable
private fun QuizBankStartScreen(
    state: QuizRuntime.UiState,
    onClose: () -> Unit,
) {
    val config = state.defaultChallengeConfig
    val levels = state.answerLevels
    // Default to the first level that can fill a round, so the child does not
    // land on a short level after the parent reorders the list.
    val fallbackIndex = remember(levels, config.pullCount) {
        levels.indexOfFirst { it.available >= config.pullCount }.coerceAtLeast(0)
    }
    var selectedLevelId by remember { mutableStateOf<String?>(null) }
    val matchedIndex = levels.indexOfFirst { it.id == selectedLevelId }
    val selectedIndex = if (matchedIndex >= 0) matchedIndex else fallbackIndex
    val selected = levels.getOrNull(selectedIndex)
    // The contract is always the configured round size; a short level must not
    // silently turn it into a smaller round. Wrong review follows the same rule:
    // it may only start when enough mistakes remain to fill the full round.
    val roundCount = config.pullCount
    val qr = rememberQuizQr(scale = 8)
    var busy by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var shortShakeTick by remember { mutableIntStateOf(0) }
    val shortShake = remember { Animatable(0f) }
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(shortShakeTick) {
        if (shortShakeTick == 0) return@LaunchedEffect
        shortShake.snapTo(0f)
        if (reduced) return@LaunchedEffect
        // A single damped nudge, matching the lock screen's error shake. It
        // only moves the short-level warning, never the whole start card.
        shortShake.animateTo(
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
    LaunchedEffect(selectedIndex) {
        shortShake.snapTo(0f)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(3_000)
            withContext(Dispatchers.IO) { QuizRuntime.refresh() }
        }
    }

    fun choose(delta: Int) {
        // Read the current selection from state, not the value captured at the
        // last recomposition: two quick presses can arrive between frames.
        val current = levels.indexOfFirst { it.id == selectedLevelId }
            .takeIf { it >= 0 } ?: fallbackIndex
        val next = current + delta
        if (next !in levels.indices) return
        selectedLevelId = levels[next].id
        error = null
    }

    fun begin() {
        if (busy || leaving) return
        if (selected != null && selected.available < config.pullCount) {
            // Keep the dialog open and point at the reason instead of starting
            // a round that is shorter than the parent configured.
            error = null
            shortShakeTick += 1
            return
        }
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                QuizChallenge.start(levelId = selected?.id)
            }
            busy = false
            if (result is QuizChallenge.BeginResult.Rejected) {
                error = result.code
                withContext(Dispatchers.IO) { QuizRuntime.refresh() }
            }
            // The start cue is emitted by QuizRoundScreen once the intro film
            // is gone; never layer TTS over the MP4's own soundtrack.
        }
    }

    fun close() {
        if (leaving) return
        leaving = true
        scope.launch {
            delay(220)
            onClose()
        }
    }

    // The parent screen only needs BACK when a round is running; on this face
    // the fade-out belongs to the local close path.
    BackHandler { close() }

    QuizStartShell(
        busy = busy,
        leaving = leaving,
        onStart = ::begin,
        onStep = ::choose,
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 28.dp)
                .widthIn(max = 460.dp)
                .startFilmGlass(28.dp)
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Shield, Palette.Label, 20.dp)
                Spacer(Modifier.width(10.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_challenge_kicker),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(R.string.policy_quiz_start_title),
                style = Type.LargeTitle,
            )
            Spacer(Modifier.height(10.dp))
            // v5 local rounds show the exact difficulty table before the first
            // question, including the child-visible penalties and unlock gate.
            QuizScoringRules(config = config, roundCount = roundCount)
            Spacer(Modifier.height(10.dp))
            QuizLevelWheel(levels = levels, selectedIndex = selectedIndex)
            if (levels.isEmpty()) {
                // The wheel already says no level is set; the parent's next step is scanning the
                // QR below, so the hint sits directly under the level box.
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_level_empty_hint),
                    style = Type.Caption.copy(color = Palette.Orange),
                    modifier = Modifier.widthIn(max = 404.dp),
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Home, Palette.SecondaryLabel, 15.dp)
                    Spacer(Modifier.width(7.dp))
                    BasicText(
                        stringResource(
                            if (selected?.wrongReview == true) {
                                R.string.policy_quiz_wrong_stock
                            } else {
                                R.string.policy_quiz_bank_stock
                            },
                            selected?.available ?: 0,
                        ),
                        style = Type.Caption.copy(color = Palette.SecondaryLabel),
                    )
                }
            }
            selected?.takeIf { it.available < config.pullCount }?.let {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(
                        R.string.policy_quiz_level_short,
                        it.available,
                        config.pullCount,
                    ),
                    style = Type.Caption.copy(color = Palette.Orange),
                    modifier = Modifier
                        .widthIn(max = 404.dp)
                        .offset { IntOffset(shortShake.value.roundToInt(), 0) },
                )
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_start_rejected, rejectText(it)),
                    style = Type.Caption.copy(color = Palette.Orange),
                    modifier = Modifier.widthIn(max = 404.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            QuizKeyHintRow(busy = busy)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 36.dp, bottom = 28.dp)
                .width(160.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QuizQrCard(qr = qr, size = 124.dp)
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(R.string.policy_quiz_bank_qr_hint),
                style = Type.Caption.copy(
                    color = Palette.SecondaryLabel,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
    }
}

/**
 * Unbound blocked/paused face: the local bank itself is unreadable or every level is paused, so
 * the large QR and the refresh action are the only useful controls. A merely empty or short bank
 * stays on the start face with its inline hint instead of opening this screen, and it keeps
 * refreshing there.
 */
@Composable
private fun QuizBankEmptyScreen(
    state: QuizRuntime.UiState,
    onClose: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val qr = rememberQuizQr(scale = 12, attempt = attempt)
    val scope = rememberCoroutineScope()
    val refreshFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2_500)
            withContext(Dispatchers.IO) { QuizRuntime.refresh() }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { refreshFocus.requestFocus() }
    }

    fun refresh() {
        scope.launch {
            withContext(Dispatchers.IO) { QuizRuntime.refresh() }
            if (qr.bitmap == null) attempt++
        }
    }

    Row(
        modifier = Modifier
            .widthIn(max = 860.dp)
            .liquidGlass(28.dp)
            .padding(horizontal = 44.dp, vertical = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuizQrCard(qr = qr, size = 300.dp)

        Column(modifier = Modifier.width(420.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Shield, Palette.Label, 22.dp)
                Spacer(Modifier.width(10.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_kicker),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
            }
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(
                    when (state.bankState) {
                        "blocked" -> R.string.policy_quiz_bank_blocked_title
                        "paused" -> R.string.policy_quiz_bank_paused_title
                        else -> R.string.policy_quiz_bank_empty_title
                    },
                ),
                style = Type.LargeTitle,
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(
                    when (state.bankState) {
                        "blocked" -> R.string.policy_quiz_bank_blocked_body
                        "paused" -> R.string.policy_quiz_bank_paused_body
                        else -> R.string.policy_quiz_bank_empty_body
                    },
                ),
                style = Type.Body.copy(color = Palette.SecondaryLabel),
                modifier = Modifier.widthIn(max = 410.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LockButton(
                    label = stringResource(R.string.policy_quiz_bank_refresh),
                    onClick = ::refresh,
                    width = 180.dp,
                    focusRequester = refreshFocus,
                    primary = true,
                    onKey = linearDpadFocus(next = backFocus),
                )
                LockButton(
                    label = stringResource(R.string.policy_back),
                    onClick = onClose,
                    width = 160.dp,
                    focusRequester = backFocus,
                    onKey = linearDpadFocus(previous = refreshFocus),
                )
            }
        }
    }
}

/**
 * Bound-TV start face. The active QCD owns the rules and the level list, so the
 * child picks a level here and only then fetches a round. There is no QR and no
 * local fallback: a missing document, an unfinished checkin or an empty level
 * list is an explicit notice with a refresh, never a server detail.
 */
@Composable
private fun QuizCloudSetupScreen(
    state: QuizRuntime.UiState,
    onClose: () -> Unit,
) {
    val config = state.defaultChallengeConfig
    val levels = state.answerLevels
    val notice = when {
        state.cloudPlanError != null -> cloudPlanMessage(state.cloudPlanError)
        state.cloudCheckinBlocked -> state.cloudCheckinMessage
            ?: stringResource(R.string.policy_quiz_cloud_checkin_default)
        !state.enabled -> stringResource(R.string.policy_quiz_cloud_disabled)
        levels.isEmpty() -> stringResource(R.string.policy_quiz_cloud_no_levels)
        else -> null
    }
    if (notice != null) {
        QuizCloudNoticeScreen(notice = notice, onClose = onClose)
        return
    }

    // Default to the first level that can fill its own round, so a reordered
    // list does not land the child on a short level.
    val fallbackIndex = remember(levels, config.pullCount) {
        levels.indexOfFirst {
            it.available >= (it.questionCount ?: config.pullCount)
        }.coerceAtLeast(0)
    }
    var selectedLevelId by remember { mutableStateOf<String?>(null) }
    val matchedIndex = levels.indexOfFirst { it.id == selectedLevelId }
    val selectedIndex = if (matchedIndex >= 0) matchedIndex else fallbackIndex
    val selected = levels.getOrNull(selectedIndex)
    val roundCount = selected?.questionCount ?: config.pullCount
    var busy by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rejectCode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // The document sync is event-driven; this only reads the stored copy so a
    // parent edit that just landed becomes visible without leaving the page.
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5_000)
            withContext(Dispatchers.IO) { QuizRuntime.refresh() }
        }
    }

    fun choose(delta: Int) {
        val current = levels.indexOfFirst { it.id == selectedLevelId }
            .takeIf { it >= 0 } ?: fallbackIndex
        val next = current + delta
        if (next !in levels.indices) return
        selectedLevelId = levels[next].id
        error = null
        rejectCode = null
    }

    fun begin() {
        if (busy || leaving) return
        val target = selected ?: return
        busy = true
        error = null
        rejectCode = null
        scope.launch {
            val outcome = CloudQuizPreparer.prepare(target.id)
            when (outcome) {
                CloudQuizPreparer.Outcome.Ready -> {
                    // This face already shows the level, payout and question
                    // source, so start the round in one press. Waiting for the
                    // child to confirm a second start face made cloud feel like
                    // it had asked for the level twice. Local mode keeps its
                    // own ready face and never reaches this branch.
                    val result = withContext(Dispatchers.Default) {
                        QuizChallenge.start()
                    }
                    if (result is QuizChallenge.BeginResult.Rejected) {
                        rejectCode = result.code
                        withContext(Dispatchers.IO) { QuizRuntime.refresh() }
                    }
                }
                is CloudQuizPreparer.Outcome.Unavailable -> {
                    error = outcome.message
                    withContext(Dispatchers.IO) { QuizRuntime.refresh() }
                }
            }
            busy = false
        }
    }

    fun close() {
        if (leaving) return
        leaving = true
        scope.launch {
            delay(220)
            onClose()
        }
    }

    BackHandler { close() }

    QuizStartShell(
        busy = busy,
        leaving = leaving,
        onStart = ::begin,
        onStep = ::choose,
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 28.dp)
                .widthIn(max = 460.dp)
                .startFilmGlass(28.dp)
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Cloud, Palette.Label, 20.dp)
                Spacer(Modifier.width(10.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_cloud_kicker),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(R.string.policy_quiz_start_title),
                style = Type.LargeTitle,
            )
            Spacer(Modifier.height(10.dp))
            // The cloud payout is exact seconds, so the table uses the frozen
            // day profile instead of the minute mirrors kept for legacy paths.
            QuizScoringRules(
                config = config,
                roundCount = roundCount,
                profile = state.cloudRewardProfile,
            )
            Spacer(Modifier.height(10.dp))
            QuizLevelWheel(levels = levels, selectedIndex = selectedIndex)
            selected?.takeIf { it.available < roundCount }?.let {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(
                        R.string.policy_quiz_cloud_level_short,
                        it.available,
                    ),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                    modifier = Modifier.widthIn(max = 404.dp),
                )
            }
            val rejection = error ?: rejectCode?.let { rejectText(it) }
            rejection?.let {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_start_rejected, it),
                    style = Type.Caption.copy(color = Palette.Orange),
                    modifier = Modifier.widthIn(max = 404.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            QuizKeyHintRow(busy = busy)
        }
    }
}

/**
 * Blocking notice for a bound TV. The only in-page action is a forced sync; the
 * copy comes from the plan or the question bank and never names a server error.
 */
@Composable
private fun QuizCloudNoticeScreen(notice: String, onClose: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val refreshFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { refreshFocus.requestFocus() }
    }

    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                QuizConfigDocumentSync.syncNow()
                QuizRuntime.refresh()
            }
            busy = false
        }
    }

    Row(
        modifier = Modifier
            .widthIn(max = 900.dp)
            .liquidGlass(28.dp)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(148.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Cloud, Palette.Label, 96.dp)
        }
        Column(modifier = Modifier.width(470.dp)) {
            BasicText(
                stringResource(R.string.policy_quiz_cloud_kicker),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
            )
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(R.string.policy_quiz_cloud_unavailable_title),
                style = Type.LargeTitle,
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                notice,
                style = Type.Body.copy(color = Palette.SecondaryLabel),
                modifier = Modifier.widthIn(max = 450.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LockButton(
                    label = stringResource(R.string.policy_quiz_cloud_retry),
                    onClick = ::refresh,
                    width = 180.dp,
                    focusRequester = refreshFocus,
                    primary = true,
                    enabled = !busy,
                    onKey = linearDpadFocus(next = backFocus),
                )
                LockButton(
                    label = stringResource(R.string.policy_back),
                    onClick = onClose,
                    width = 160.dp,
                    focusRequester = backFocus,
                    onKey = linearDpadFocus(previous = refreshFocus),
                )
            }
        }
    }
}

@Composable
private fun QuizReadyScreen(
    session: QuizSession,
    questionCount: Int,
    answerScope: QuizAnswerScope?,
    onClose: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun begin() {
        if (busy || leaving) return
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.Default) { QuizChallenge.start() }
            busy = false
            if (result is QuizChallenge.BeginResult.Rejected) {
                error = result.code
            }
            // The start cue is emitted by QuizRoundScreen once the intro film
            // is gone; never layer TTS over the MP4's own soundtrack.
        }
    }

    fun close() {
        if (leaving) return
        leaving = true
        scope.launch {
            delay(220)
            onClose()
        }
    }

    BackHandler { close() }

    QuizStartShell(
        busy = busy,
        leaving = leaving,
        onStart = ::begin,
        onStep = null,
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 28.dp)
                .widthIn(max = 460.dp)
                .startFilmGlass(28.dp)
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Shield, Palette.Label, 20.dp)
                Spacer(Modifier.width(10.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_challenge_kicker),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(R.string.policy_quiz_start_title),
                style = Type.LargeTitle,
            )
            Spacer(Modifier.height(10.dp))
            // A frozen v6 profile is exact seconds and wins over the minute
            // mirrors. A legacy hosted round keeps its single-rate body copy.
            when {
                session.rewardProfile != null -> QuizScoringRules(
                    config = session.config,
                    roundCount = questionCount,
                    profile = session.rewardProfile,
                )
                session.originSchemaVersion >= 5 && !QuizChallenge.isCloudSession(session) ->
                    QuizScoringRules(config = session.config, roundCount = questionCount)
                else -> BasicText(
                    stringResource(
                        R.string.policy_quiz_start_body,
                        questionCount,
                        session.config.minCorrectCount,
                        session.config.minutesPerQuestion,
                    ),
                    style = Type.Body.copy(color = Palette.SecondaryLabel),
                    modifier = Modifier.widthIn(max = 404.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            QuizScopeCard(scope = answerScope)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Cloud, Palette.SecondaryLabel, 15.dp)
                Spacer(Modifier.width(7.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_source_cloud),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_start_rejected, rejectText(it)),
                    style = Type.Caption.copy(color = Palette.Orange),
                    modifier = Modifier.widthIn(max = 404.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            QuizKeyHintRow(busy = busy)
        }
    }
}

/**
 * The complete scoring contract shown on the transparent start card. Rewards,
 * deductions, unlock threshold and cap are all visible before the first tap, so
 * the child can judge the round without a parent explaining it.
 */
@Composable
private fun QuizScoringRules(
    config: QuizConfig,
    roundCount: Int,
    modifier: Modifier = Modifier,
    profile: QuizRewardProfile? = null,
) {
    // A frozen cloud profile is exact seconds; the minute mirrors below are
    // only a lossy legacy projection and must not be shown for it.
    val threshold = when {
        profile != null && profile.minUnlockSeconds == 0 ->
            stringResource(R.string.policy_quiz_rules_threshold_none)
        profile != null -> durationText(profile.minUnlockSeconds)
        config.minUnlockMinutes == 0 ->
            stringResource(R.string.policy_quiz_rules_threshold_none)
        else -> stringResource(
            R.string.policy_quiz_rules_threshold_minutes,
            config.minUnlockMinutes,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Fill)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        BasicText(
            stringResource(R.string.policy_quiz_rules_summary, roundCount),
            style = Type.Caption.copy(color = Palette.SecondaryLabel),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                stringResource(R.string.policy_quiz_rules_difficulty),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
                modifier = Modifier.weight(1f),
            )
            BasicText(
                stringResource(R.string.policy_quiz_rules_correct),
                style = Type.Caption.copy(
                    color = Palette.SecondaryLabel,
                    textAlign = TextAlign.End,
                ),
                modifier = Modifier.width(92.dp),
            )
            BasicText(
                stringResource(R.string.policy_quiz_rules_wrong),
                style = Type.Caption.copy(
                    color = Palette.SecondaryLabel,
                    textAlign = TextAlign.End,
                ),
                modifier = Modifier.width(120.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        if (profile != null) {
            QuizScoringRuleRow(
                label = stringResource(R.string.policy_quiz_difficulty_easy),
                reward = rewardText(profile.correctSeconds.easy),
                penalty = penaltyText(profile.penaltySeconds.easy),
            )
            QuizScoringRuleRow(
                label = stringResource(R.string.policy_quiz_difficulty_medium),
                reward = rewardText(profile.correctSeconds.medium),
                penalty = penaltyText(profile.penaltySeconds.medium),
            )
            QuizScoringRuleRow(
                label = stringResource(R.string.policy_quiz_difficulty_hard),
                reward = rewardText(profile.correctSeconds.hard),
                penalty = penaltyText(profile.penaltySeconds.hard),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                stringResource(R.string.policy_quiz_rules_unlock, threshold),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
            )
        } else {
            QuizScoringRuleRow(
                label = stringResource(R.string.policy_quiz_difficulty_easy),
                reward = stringResource(R.string.policy_quiz_rules_reward, config.easyRewardMinutes),
                penalty = config.easyPenaltyMinutes.takeIf { it > 0 }?.let {
                    stringResource(R.string.policy_quiz_rules_penalty, it)
                },
            )
            QuizScoringRuleRow(
                label = stringResource(R.string.policy_quiz_difficulty_medium),
                reward = stringResource(R.string.policy_quiz_rules_reward, config.mediumRewardMinutes),
                penalty = config.mediumPenaltyMinutes.takeIf { it > 0 }?.let {
                    stringResource(R.string.policy_quiz_rules_penalty, it)
                },
            )
            QuizScoringRuleRow(
                label = stringResource(R.string.policy_quiz_difficulty_hard),
                reward = stringResource(R.string.policy_quiz_rules_reward, config.hardRewardMinutes),
                penalty = config.hardPenaltyMinutes.takeIf { it > 0 }?.let {
                    stringResource(R.string.policy_quiz_rules_penalty, it)
                },
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                stringResource(R.string.policy_quiz_rules_unlock, threshold),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
            )
        }
    }
}

@Composable
private fun rewardText(seconds: Int): String =
    stringResource(R.string.policy_quiz_rules_reward_duration, durationText(seconds))

@Composable
private fun penaltyText(seconds: Int): String? =
    seconds.takeIf { it > 0 }?.let {
        stringResource(R.string.policy_quiz_rules_penalty_duration, durationText(it))
    }

/** Human duration for exact-second cloud rules; never a raw seconds count. */
@Composable
private fun durationText(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return when {
        minutes <= 0 -> stringResource(R.string.policy_quiz_duration_seconds, seconds)
        seconds == 0 -> stringResource(R.string.policy_quiz_duration_minutes, minutes)
        else -> stringResource(
            R.string.policy_quiz_duration_minutes_seconds,
            minutes,
            seconds,
        )
    }
}

@Composable
private fun QuizScoringRuleRow(label: String, reward: String, penalty: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            label,
            style = Type.Caption.copy(color = Palette.Label),
            modifier = Modifier.weight(1f),
        )
        BasicText(
            reward,
            style = Type.Caption.copy(
                color = QuizGold,
                textAlign = TextAlign.End,
            ),
            modifier = Modifier.width(92.dp),
        )
        BasicText(
            penalty ?: stringResource(R.string.policy_quiz_rules_no_penalty),
            style = Type.Caption.copy(
                color = if (penalty == null) Palette.SecondaryLabel else QuizLoss,
                textAlign = TextAlign.End,
            ),
            modifier = Modifier.width(120.dp),
        )
    }
}

/**
 * The two start cards lie on the intro film, whose picture should stay visible
 * through them, so they use a smoked pane instead of [liquidGlass] (which shows
 * the home wallpaper's frosted glass, not the film). The top is lighter than
 * the bottom so the pane still catches light like the other panels; the rim and
 * sheen come from the shared glass edge. Other sheets keep [liquidGlass].
 */
private fun Modifier.startFilmGlass(corner: Dp): Modifier = this
    .clip(RoundedCornerShape(corner))
    .drawWithCache {
        val radius = corner.toPx()
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        val edge = glassEdge(w, h, radius, 1.2.dp.toPx())
        val smoke = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.30f),
            1f to Color.Black.copy(alpha = 0.46f),
        )
        onDrawBehind {
            drawRoundRect(brush = smoke, cornerRadius = CornerRadius(radius, radius))
            drawImage(edge, dstSize = IntSize(w, h))
        }
    }

private fun announceAnswer(correct: Boolean, timedOut: Boolean) {
    // The sound effect and the voice start together; QuizSfx stays well below
    // the voice so the Chinese line remains the thing the child hears.
    when {
        timedOut -> {
            QuizSfx.play(QuizSfx.Event.TIMEOUT)
            PolicyVoiceBus.emit(PolicyVoiceCue.QUIZ_TIMEOUT)
        }
        correct -> {
            QuizSfx.play(QuizSfx.Event.CORRECT)
            PolicyVoiceBus.emit(PolicyVoiceCue.QUIZ_CORRECT)
        }
        else -> {
            QuizSfx.play(QuizSfx.Event.WRONG)
            PolicyVoiceBus.emit(PolicyVoiceCue.QUIZ_WRONG)
        }
    }
}

/**
 * One full-screen face of the round. While [QuizRun.feedbackOpen] is set the
 * answer page replaces the question page; both use the whole 960×540 canvas so
 * a long prompt, option or explanation is never cut off. Text is scrolled with
 * Up/Down instead of ellipsized.
 */
@Composable
private fun QuizRoundScreen(
    session: QuizSession,
    onExit: () -> Unit,
    onReward: (QuizReward) -> Unit,
) {
    val run = session.run ?: return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The round owns the speakers from its first question until it leaves this face. The gate
    // keeps background players paused for the whole round, including the wait between questions,
    // and is released before the report film so that film's own soundtrack can play.
    DisposableEffect(lifecycle, context) {
        var held = false

        fun hold() {
            if (!held) {
                QuizAudioGate.acquire(context)
                held = true
            }
        }

        fun release() {
            if (held) {
                QuizAudioGate.release()
                held = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> hold()
                Lifecycle.Event.ON_STOP -> release()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) hold()
        onDispose {
            lifecycle.removeObserver(observer)
            release()
        }
    }
    // Only local v5 rounds use the difficulty table. Hosted rounds remain
    // backend-authoritative and keep the old single-rate presentation.
    val difficultyScoring = session.originSchemaVersion >= 5 &&
        !QuizChallenge.isCloudSession(session)
    // The first question is the first moment the intro film has been released,
    // so the start line starts here and never over the MP4's own soundtrack.
    // The short age guard keeps it from replaying when a round is re-opened.
    LaunchedEffect(session.id) {
        val ageMs = System.currentTimeMillis() - run.startedAt
        if (run.index == 0 && run.submissions.isEmpty() && ageMs in 0L..5_000L) {
            PolicyVoiceBus.emit(PolicyVoiceCue.QUIZ_START)
        }
    }
    if (run.feedbackOpen) {
        val submission = run.submissions.lastOrNull()
        val answeredItem = submission?.let { sub ->
            session.items.firstOrNull { it.questionId == sub.questionId }
        }
        if (submission != null && answeredItem != null) {
            val rewardSeconds = session.rewardSecondsFor(answeredItem.difficulty, difficultyScoring)
            val penaltySeconds =
                session.penaltySecondsFor(answeredItem.difficulty, difficultyScoring)
            var resumeFailed by remember(submission.answeredAt) { mutableStateOf(false) }
            if (submission.correct) {
                // A correct answer never opens the answer page: flash the earned
                // time and resume the next question at once. Only a storage
                // failure falls back to the page so the round cannot hang blank.
                LaunchedEffect(submission.answeredAt) {
                    onReward(
                        QuizReward(
                            answeredAt = submission.answeredAt,
                            seconds = rewardSeconds,
                        ),
                    )
                    val outcome = withContext(Dispatchers.Default) {
                        QuizChallenge.closeFeedback()
                    }
                    if (outcome is QuizChallenge.FeedbackOutcome.Failed) {
                        resumeFailed = true
                    }
                }
                if (!resumeFailed) return
            }
            val feedbackHoldMs = if (submission.correct) {
                0L
            } else {
                // v6 freezes the exact floor; v5 keeps its historical seconds.
                submission.feedbackHoldMs
                    ?: (normalizeAnswerHoldSec(session.config.answerDisplaySec) * 1_000L)
            }
            QuizFeedbackPage(
                item = answeredItem,
                submission = submission,
                questionNumber = run.index.coerceIn(1, session.items.size),
                total = session.items.size,
                isLast = run.index >= session.items.size,
                rewardSeconds = rewardSeconds,
                penaltySeconds = penaltySeconds,
                holdMs = feedbackHoldMs,
            )
            return
        }
        // A persisted feedback pause always carries its submission; if a
        // corrupt session ever violates that, never leave a blank face.
        QuizFeedbackRecovery(onExit)
        return
    }
    val item = session.items.getOrNull(run.index)
    if (item == null) {
        QuizFeedbackRecovery(onExit)
        return
    }
    QuizQuestionPage(
        item = item,
        questionNumber = run.index + 1,
        total = session.items.size,
        deadlineAt = run.deadlineAt,
        rewardSeconds = session.rewardSecondsFor(item.difficulty, difficultyScoring),
        penaltySeconds = session.penaltySecondsFor(item.difficulty, difficultyScoring),
        onExit = onExit,
    )
}

/**
 * The amount one question actually settles, in canonical seconds. A v6 session
 * freezes the exact per-difficulty profile; the minute mirrors below truncate a
 * sub-minute reward to zero, so they only describe pre-v6 records.
 */
private fun QuizSession.rewardSecondsFor(difficulty: String, difficultyScoring: Boolean): Int {
    val profile = rewardProfile
    if (originSchemaVersion >= 6 && profile != null) {
        return QuizScoringV6.correctSeconds(difficulty, profile)
    }
    return if (difficultyScoring) {
        QuizScoring.rewardMinutes(difficulty, config) * 60
    } else {
        config.minutesPerQuestion * 60
    }
}

/**
 * Null means the round shows no per-question deduction on screen. Hosted rounds
 * keep that historic presentation even though v6 still settles their penalties.
 */
private fun QuizSession.penaltySecondsFor(difficulty: String, difficultyScoring: Boolean): Int? {
    if (!difficultyScoring) return null
    val profile = rewardProfile
    if (originSchemaVersion >= 6 && profile != null) {
        return QuizScoringV6.penaltySeconds(difficulty, profile)
    }
    return QuizScoring.penaltyMinutes(difficulty, config) * 60
}

/** Last-resort face for a session that cannot render a question or feedback. */
@Composable
private fun QuizFeedbackRecovery(onExit: () -> Unit) {
    val exitFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { exitFocus.requestFocus() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            stringResource(R.string.policy_quiz_error),
            style = Type.Title.copy(color = Palette.Orange),
        )
        Spacer(Modifier.height(18.dp))
        LockButton(
            label = stringResource(R.string.policy_quiz_exit),
            onClick = onExit,
            width = 200.dp,
            focusRequester = exitFocus,
            primary = true,
            onKey = linearDpadFocus(),
        )
    }
}

@Composable
private fun QuizQuestionPage(
    item: QuizItem,
    questionNumber: Int,
    total: Int,
    deadlineAt: Long,
    rewardSeconds: Int,
    penaltySeconds: Int?,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val textMetrics = LocalQuizTextMetrics.current
    var submitting by remember(item.questionId) { mutableStateOf(false) }
    var remaining by remember(item.questionId, deadlineAt) {
        mutableLongStateOf(QuizChallenge.remainingSeconds())
    }
    var error by remember(item.questionId) { mutableStateOf(false) }
    var needsSelection by remember(item.questionId) { mutableStateOf(false) }
    // Multiple-choice options are a draft until the child confirms the whole
    // set. Single-choice never populates this state and still commits on tap.
    var selectedIndices by remember(item.questionId) { mutableStateOf(emptySet<Int>()) }
    val promptFocus = remember(item.questionId) { FocusRequester() }
    // One requester per option so Up/Left and Down/Right can walk the whole
    // answer list, then hand over to the prompt or the exit button at its ends.
    val optionFocuses = remember(item.questionId, item.options.size) {
        List(item.options.size) { FocusRequester() }
    }
    val exitFocus = remember(item.questionId) { FocusRequester() }
    val confirmFocus = remember(item.questionId) { FocusRequester() }

    fun submit(selected: List<Int>, timedOut: Boolean) {
        if (submitting) return
        // Stop the read-aloud the moment an answer is committed, including a
        // timeout, so the result cue and the next question start clean.
        QuizSpeechPlayer.stop()
        submitting = true
        error = false
        needsSelection = false
        // Use the press time, not the time after storage I/O, so a tap made
        // just before the deadline still counts.
        val answeredAt = System.currentTimeMillis()
        scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                QuizChallenge.answer(selected, timedOut, answeredAt)
            }
            when (outcome) {
                QuizChallenge.AnswerOutcome.Answered -> {
                    // The model is authoritative if the tap raced the deadline.
                    val latest = QuizRuntime.state.value.session?.run?.submissions?.lastOrNull()
                    if (latest != null) announceAnswer(latest.correct, latest.timedOut)
                }
                is QuizChallenge.AnswerOutcome.Failed -> {
                    submitting = false
                    error = true
                }
            }
        }
    }

    LaunchedEffect(item.questionId, deadlineAt) {
        remaining = QuizChallenge.remainingSeconds()
        while (isActive) {
            val left = QuizChallenge.remainingSeconds()
            remaining = left
            if (left <= 0L) {
                submit(emptyList(), true)
                break
            }
            delay(1_000)
        }
    }
    LaunchedEffect(item.questionId) {
        withFrameNanos { }
        runCatching { promptFocus.requestFocus() }
    }
    LaunchedEffect(item.questionId) {
        QuizSpeechPlayer.speakQuestion(context, item)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    stringResource(R.string.policy_quiz_challenge_kicker),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_progress, questionNumber, total),
                    style = Type.Headline,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                BasicText(
                    stringResource(R.string.policy_quiz_time_left, remaining),
                    style = textMetrics.timer.copy(
                        color = when {
                            remaining <= 5 -> Palette.Red
                            remaining <= 10 -> Palette.Orange
                            else -> Palette.Label
                        },
                    ),
                )
                BasicText(
                    when {
                        penaltySeconds == null -> stringResource(
                            R.string.policy_quiz_points_hint_duration,
                            durationText(rewardSeconds),
                        )
                        penaltySeconds > 0 -> stringResource(
                            R.string.policy_quiz_scored_hint_duration,
                            durationText(rewardSeconds),
                            durationText(penaltySeconds),
                        )
                        else -> stringResource(
                            R.string.policy_quiz_scored_hint_no_penalty_duration,
                            durationText(rewardSeconds),
                        )
                    },
                    style = Type.Caption.copy(
                        color = Palette.SecondaryLabel,
                        textAlign = TextAlign.End,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        QuizFormatBanner(multiple = item.isMultipleChoice)
        Spacer(Modifier.height(8.dp))
        QuizQuestionBodyRouter(
            item = item,
            submitting = submitting,
            selectedIndices = selectedIndices,
            promptFocus = promptFocus,
            optionFocuses = optionFocuses,
            confirmFocus = confirmFocus,
            exitFocus = exitFocus,
            onOptionClick = { index ->
                if (item.isMultipleChoice) {
                    selectedIndices = if (index in selectedIndices) {
                        selectedIndices - index
                    } else {
                        selectedIndices + index
                    }
                    needsSelection = false
                } else {
                    submit(listOf(index), false)
                }
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                when {
                    error -> stringResource(R.string.policy_quiz_error)
                    needsSelection -> stringResource(R.string.policy_quiz_select_at_least_one)
                    else -> stringResource(R.string.policy_quiz_question_scroll_hint)
                },
                style = Type.Caption.copy(
                    color = if (error || needsSelection) {
                        Palette.Orange
                    } else {
                        Palette.SecondaryLabel
                    },
                ),
                modifier = Modifier.weight(1f),
            )
            if (item.isMultipleChoice) {
                LockButton(
                    label = stringResource(R.string.policy_quiz_confirm_answer),
                    onClick = {
                        if (selectedIndices.isEmpty()) {
                            error = false
                            needsSelection = true
                        } else {
                            submit(selectedIndices.sorted(), false)
                        }
                    },
                    width = 200.dp,
                    focusRequester = confirmFocus,
                    enabled = !submitting,
                    primary = true,
                    onKey = linearDpadFocus(
                        previous = optionFocuses.lastOrNull() ?: promptFocus,
                        next = exitFocus,
                    ),
                )
                Spacer(Modifier.width(8.dp))
            }
            LockButton(
                label = stringResource(R.string.policy_quiz_exit),
                onClick = {
                    QuizSpeechPlayer.stop()
                    onExit()
                },
                width = 160.dp,
                focusRequester = exitFocus,
                enabled = !submitting,
                // The exit button is the end of the chain: Up/Left goes back to
                // the answers (or confirmation), Down/Right stays here.
                onKey = linearDpadFocus(
                    previous = if (item.isMultipleChoice) {
                        confirmFocus
                    } else {
                        optionFocuses.lastOrNull() ?: promptFocus
                    },
                ),
            )
        }
    }
}

/**
 * The first row under the question header. The colored badge and the sentence
 * both repeat the format, so a child can tell an immediate single-choice commit
 * from a multi-select set that waits for the confirm button.
 */
@Composable
private fun QuizFormatBanner(multiple: Boolean) {
    val accent = if (multiple) Palette.Orange else Palette.Green
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accent)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            BasicText(
                stringResource(
                    if (multiple) {
                        R.string.policy_quiz_format_multiple
                    } else {
                        R.string.policy_quiz_format_single
                    },
                ),
                style = Type.Caption.copy(
                    color = Palette.OnFocus,
                    fontWeight = FontWeight.W700,
                ),
            )
        }
        Spacer(Modifier.width(10.dp))
        BasicText(
            stringResource(
                if (multiple) {
                    R.string.policy_quiz_multiple_hint
                } else {
                    R.string.policy_quiz_single_hint
                },
            ),
            style = Type.Caption.copy(color = Palette.Label),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A radio ring for single choice and a square for multiple choice. The shape is
 * visible before selection, so the answer format stays readable even when the
 * first option already has focus.
 */
@Composable
private fun QuizOptionMarker(multiple: Boolean, selected: Boolean, focused: Boolean) {
    val accent = if (multiple) Palette.Orange else Palette.Green
    val shape = if (multiple) RoundedCornerShape(5.dp) else CircleShape
    val metrics = LocalQuizTextMetrics.current
    val edge = when {
        selected -> accent
        focused -> Palette.OnFocus
        else -> Palette.SecondaryLabel
    }
    Box(
        modifier = Modifier
            .size(metrics.markerSize)
            .clip(shape)
            .background(if (selected) accent else Color.Transparent)
            .border(1.5.dp, edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            if (multiple) {
                BasicText(
                    "✓",
                    style = Type.Headline.copy(
                        color = Palette.OnFocus,
                        fontWeight = FontWeight.W700,
                    ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Palette.OnFocus),
                )
            }
        }
    }
}

@Composable
internal fun QuizOptionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean,
    selected: Boolean = false,
    multiple: Boolean = false,
    onKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
) {
    Pressable(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        focusRequester = focusRequester,
        focusedScale = 1.02f,
        onKey = onKey,
    ) { state ->
        val metrics = LocalQuizTextMetrics.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = metrics.optionMinHeight)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    when {
                        state.focused -> Palette.Focus
                        selected -> Palette.FillStrong
                        else -> Palette.Fill
                    },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuizOptionMarker(
                multiple = multiple,
                selected = selected,
                focused = state.focused,
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                label,
                style = metrics.option.copy(
                    color = if (state.focused) Palette.OnFocus else Palette.Label,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A right-edge scroll bar drawn outside the scroll node, so it stays put. */
internal fun Modifier.quizScrollBar(scroll: ScrollState, focused: Boolean): Modifier = drawWithContent {
    drawContent()
    if (scroll.maxValue <= 0) return@drawWithContent
    val track = size.height - 16.dp.toPx()
    if (track <= 0f) return@drawWithContent
    val thumb = (track * size.height / (size.height + scroll.maxValue))
        .coerceAtLeast(12.dp.toPx())
    val top = 8.dp.toPx() + (track - thumb) * scroll.value / scroll.maxValue
    drawRoundRect(
        color = if (focused) Palette.SecondaryLabel else Palette.TertiaryLabel,
        topLeft = Offset(size.width - 7.dp.toPx(), top),
        size = Size(2.5.dp.toPx(), thumb),
        cornerRadius = CornerRadius(1.25.dp.toPx()),
    )
}

/**
 * The post-round report is a positive summary, not a punishment screen. It
 * shows the totals, the round's net score and a warm evaluation; there is no
 * forced wait before the child can start the earned session or go back. The
 * score is gold only when the round actually unlocked time, so a below-threshold
 * round reads as "not enough yet" without turning the whole page negative.
 */
@Composable
private fun QuizReviewScreen(
    session: QuizSession,
    /** The app that opened this round; confirmed for the host only if the session starts. */
    startTarget: String?,
    onComplete: () -> Unit,
    /** The earned session is active; the host opens the target app on its own. */
    onStarted: () -> Unit,
    onDismiss: () -> Unit,
    onExit: () -> Unit,
) {
    val run = session.run ?: return
    val total = session.items.size
    // Only local rounds settle on the local difficulty table; hosted rounds keep
    // the backend's own arithmetic and presentation. v6 pays the profile frozen
    // into the session; a v6 record without one (written by an earlier build)
    // falls back to the same v5 engine its settlement used.
    val difficultyScoring = session.originSchemaVersion >= 5 &&
        !QuizChallenge.isCloudSession(session)
    // This branch only renders local rounds; the per-round reward cap does not apply to
    // them, matching the settlement path. The day's quota limits the actual session.
    val scoreV6 = if (difficultyScoring && session.originSchemaVersion >= 6) {
        session.rewardProfile?.let {
            QuizScoringV6.score(session.items, run.submissions, it, applyCap = false)
        }
    } else {
        null
    }
    val score = if (difficultyScoring && scoreV6 == null) {
        QuizScoring.score(session.items, run.submissions, session.config, applyCap = false)
    } else {
        null
    }
    // The headline is the round's net score, not the granted time: below the
    // unlock threshold the round pays 0 but still has a score to show (for
    // example a 10-minute threshold with a 5-minute net score). Hosted rounds
    // have no local score, so they fall back to the backend's earned minutes.
    // V44: a local report must show exactly what the session will receive, so
    // the same planner that admits the start clamps the displayed value.
    val rawEarnedSeconds = when {
        scoreV6 != null -> scoreV6.earnedSeconds
        score != null -> score.earnedMinutes * 60
        else -> if (run.earnedSeconds > 0) run.earnedSeconds else run.earnedMinutes * 60
    }
    val localGrantedSeconds = if (difficultyScoring) {
        PolicyModeCoordinator.localQuizGrantSeconds(rawEarnedSeconds.toLong())?.toInt()
    } else {
        null
    }
    val scoreMinutes = localGrantedSeconds?.let { it / 60 }
        ?: score?.cappedMinutes
        ?: run.earnedMinutes
    val scoreUnlocked = when {
        scoreV6 != null -> scoreV6.unlockReached
        score != null -> score.unlockReached
        else -> run.earnedMinutes > 0
    } && (localGrantedSeconds == null || localGrantedSeconds > 0)
    val scoreColor = if (
        QuizResultTone.isGold(
            localGrantedSeconds ?: scoreV6?.cappedSeconds ?: scoreMinutes,
            scoreUnlocked,
        )
    ) {
        QuizGold
    } else {
        QuizLoss
    }
    val correct = run.correctCount.coerceIn(0, total)
    val ratio = if (total > 0) correct.toFloat() / total.toFloat() else 0f
    val stars = when {
        ratio >= 1f -> 3
        ratio >= 0.8f -> 2
        else -> 1
    }
    val failed = session.status == QuizSessionStatus.START_FAILED
    val starting = session.status == QuizSessionStatus.STARTING
    val scope = rememberCoroutineScope()
    val actionFocus = remember { FocusRequester() }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }
    val ready = !busy && !starting && !failed
    val unlocked = localGrantedSeconds?.let { it > 0 }
        ?: (run.earnedMinutes > 0 || run.earnedSeconds > 0)
    // The reward film hands an unlocked round to its session on its own. The button below
    // already starts early; this only saves the extra press once the film has finished.
    var filmEnded by remember(session.id) { mutableStateOf(false) }
    val errorCode = run.startErrorCode ?: localError
    val gradeTitle = stringResource(
        when {
            ratio >= 1f -> R.string.policy_quiz_report_grade_perfect
            ratio >= 0.8f -> R.string.policy_quiz_report_grade_great
            ratio >= 0.6f -> R.string.policy_quiz_report_grade_good
            else -> R.string.policy_quiz_report_grade_keeper
        },
    )
    val gradeBody = stringResource(
        when {
            ratio >= 1f -> R.string.policy_quiz_report_grade_perfect_body
            ratio >= 0.8f -> R.string.policy_quiz_report_grade_great_body
            ratio >= 0.6f -> R.string.policy_quiz_report_grade_good_body
            else -> R.string.policy_quiz_report_grade_keeper_body
        },
    )

    val finishReview: () -> Unit = {
        if (!busy && !failed) {
            busy = true
            localError = null
            serverError = null
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    // Settle by the round's own source: binding may have changed
                    // while this local or cloud round was still running.
                    if (QuizChallenge.isCloudSession(session)) {
                        QuizChallenge.completeReviewHosted(targetPackage = startTarget)
                    } else {
                        QuizChallenge.completeReview(targetPackage = startTarget)
                    }
                }
                busy = false
                when (outcome) {
                    QuizChallenge.ReviewOutcome.Started -> onStarted()
                    QuizChallenge.ReviewOutcome.Cleared -> onComplete()
                    is QuizChallenge.ReviewOutcome.Failed -> localError = outcome.code
                    is QuizChallenge.ReviewOutcome.Rejected -> serverError = outcome.message
                }
            }
        }
    }

    // The report speaks once it has settled. Zero earned minutes gets the
    // neutral line and no celebration sound, so the screen never amplifies a
    // failed round.
    LaunchedEffect(session.id) {
        if (session.status != QuizSessionStatus.SETTLING) return@LaunchedEffect
        delay(180)
        if (run.earnedMinutes > 0 || run.earnedSeconds > 0) {
            QuizSfx.play(QuizSfx.Event.SETTLE)
            PolicyVoiceBus.emit(PolicyVoiceCue.QUIZ_SETTLE)
        } else {
            PolicyVoiceBus.emit(PolicyVoiceCue.QUIZ_SETTLE_EMPTY)
        }
    }
    // A STARTING record means the process died between persisting the start and
    // clearing the quiz. completeReview reconciles it: Started/BUSY means the
    // policy session already exists, so the record can be cleared.
    LaunchedEffect(session.id, session.status) {
        if (session.status == QuizSessionStatus.STARTING) {
            withFrameNanos { }
            finishReview()
        }
    }
    // An unlocked report advances by itself once the reward film ends; the button below still
    // offers an early start. The short hold keeps the film's last frame readable. A failed
    // attempt must not loop: the child retries with the button, which shows the reason.
    LaunchedEffect(session.id, unlocked, filmEnded, ready) {
        if (!unlocked || !filmEnded || !ready) return@LaunchedEffect
        if (localError != null || serverError != null) return@LaunchedEffect
        delay(800)
        finishReview()
    }
    LaunchedEffect(ready, failed, busy, starting) {
        if (ready || failed) {
            withFrameNanos { }
            runCatching { actionFocus.requestFocus() }
        }
    }
    BackHandler {
        when {
            // Retrying while the request is in flight would double-submit.
            busy -> Unit
            failed -> onExit()
            // A failed settlement keeps SETTLING for a retry. Back must leave
            // the report instead of repeating the same failing request.
            localError != null || serverError != null -> onDismiss()
            ready -> finishReview()
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 880.dp)
            .liquidGlass(30.dp)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuizResultVideo(180.dp, onEnded = { filmEnded = true })
            Spacer(Modifier.width(26.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    stringResource(R.string.policy_quiz_report_kicker),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_result_title),
                    style = Type.LargeTitle,
                )
                Spacer(Modifier.height(6.dp))
                QuizStarRow(stars, 24.dp)
                Spacer(Modifier.height(10.dp))
                BasicText(
                    gradeTitle,
                    // Praise follows accuracy, not the payout; the score stat
                    // below carries the unlock outcome.
                    style = Type.Title.copy(color = if (failed) Palette.Orange else Palette.Label),
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    gradeBody,
                    style = Type.Body.copy(color = Palette.SecondaryLabel),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReportStat(
                label = stringResource(R.string.policy_quiz_report_correct_label),
                value = stringResource(R.string.policy_quiz_report_correct_value, correct, total),
                color = Palette.Label,
                modifier = Modifier.weight(1f),
            )
            ReportStat(
                // The net score, even when the threshold means none of it is
                // granted. Gold marks an unlocked round; green marks a score
                // that stayed below the line (the threshold hint explains 0
                // granted minutes).
                label = stringResource(R.string.policy_quiz_report_score_label),
                value = if (scoreV6 != null) {
                    quizSecondsText(scoreV6.cappedSeconds)
                } else {
                    stringResource(R.string.policy_quiz_report_minutes_value, scoreMinutes)
                },
                color = scoreColor,
                modifier = Modifier.weight(1f),
            )
        }
        if (scoreV6 != null) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                if (scoreV6.penaltySeconds > 0) {
                    stringResource(
                        R.string.policy_quiz_report_breakdown_seconds,
                        scoreV6.grossSeconds,
                        scoreV6.penaltySeconds,
                        quizSecondsText(scoreV6.cappedSeconds),
                    )
                } else {
                    stringResource(
                        R.string.policy_quiz_report_breakdown_no_penalty_seconds,
                        scoreV6.grossSeconds,
                        quizSecondsText(scoreV6.cappedSeconds),
                    )
                },
                style = Type.Caption.copy(
                    color = Palette.SecondaryLabel,
                    textAlign = TextAlign.Center,
                ),
            )
            if (scoreV6.netSeconds > scoreV6.cappedSeconds) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(
                        R.string.policy_quiz_report_capped_seconds,
                        quizSecondsText(scoreV6.cappedSeconds),
                    ),
                    style = Type.Caption.copy(
                        color = Palette.SecondaryLabel,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            if (scoreV6.thresholdSeconds > 0 && !scoreV6.unlockReached) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(
                        R.string.policy_quiz_report_threshold_seconds,
                        quizSecondsText(scoreV6.thresholdSeconds),
                        quizSecondsText(scoreV6.netSeconds),
                    ),
                    style = Type.Caption.copy(
                        color = QuizLoss,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        } else if (score != null) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                if (score.penaltyMinutes > 0) {
                    stringResource(
                        R.string.policy_quiz_report_breakdown,
                        score.grossMinutes,
                        score.penaltyMinutes,
                        score.cappedMinutes,
                    )
                } else {
                    stringResource(
                        R.string.policy_quiz_report_breakdown_no_penalty,
                        score.grossMinutes,
                        score.cappedMinutes,
                    )
                },
                style = Type.Caption.copy(
                    color = Palette.SecondaryLabel,
                    textAlign = TextAlign.Center,
                ),
            )
            if (score.netMinutes > score.cappedMinutes) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(
                        R.string.policy_quiz_report_capped,
                        score.cappedMinutes,
                    ),
                    style = Type.Caption.copy(
                        color = Palette.SecondaryLabel,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            if (score.thresholdMinutes > 0 && !score.unlockReached) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(
                        R.string.policy_quiz_report_threshold,
                        score.thresholdMinutes,
                        score.netMinutes,
                    ),
                    style = Type.Caption.copy(
                        color = QuizLoss,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        if (failed && errorCode != null) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(
                    R.string.policy_quiz_start_rejected,
                    // The backend's business refusal is already user-facing; a
                    // local code keeps its localized text.
                    run.startErrorMessage ?: rejectText(errorCode),
                ),
                style = Type.Caption.copy(color = Palette.Orange, textAlign = TextAlign.Center),
            )
        } else if (!failed && serverError != null) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(R.string.policy_quiz_start_rejected, serverError.orEmpty()),
                style = Type.Caption.copy(color = Palette.Orange, textAlign = TextAlign.Center),
            )
        } else if (!failed && localError != null) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(
                    R.string.policy_quiz_start_rejected,
                    rejectText(localError.orEmpty()),
                ),
                style = Type.Caption.copy(color = Palette.Orange, textAlign = TextAlign.Center),
            )
        }
        Spacer(Modifier.height(18.dp))
        LockButton(
            label = when {
                failed -> stringResource(R.string.policy_quiz_exit_confirm)
                busy || starting -> stringResource(R.string.policy_quiz_review_starting)
                unlocked -> stringResource(R.string.policy_quiz_review_start)
                else -> stringResource(R.string.policy_quiz_review_return)
            },
            onClick = {
                if (failed) onExit() else finishReview()
            },
            width = 260.dp,
            focusRequester = actionFocus,
            primary = true,
            enabled = failed || ready,
            // The report has a single action; every arrow keeps focus on it.
            onKey = linearDpadFocus(),
        )
    }
}

/** "45 秒" / "1 分 45 秒"; exact minutes reuse the existing minute wording. */
@Composable
private fun quizSecondsText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val rest = safe % 60
    return when {
        minutes == 0 -> stringResource(R.string.policy_quiz_report_seconds_value, rest)
        rest == 0 -> stringResource(R.string.policy_quiz_report_minutes_value, minutes)
        else -> stringResource(R.string.policy_quiz_report_minutes_seconds_value, minutes, rest)
    }
}

@Composable
private fun ReportStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Fill)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        BasicText(
            label,
            style = Type.Caption.copy(color = Palette.SecondaryLabel),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        BasicText(
            value,
            style = Type.Title.copy(color = color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuizStarRow(stars: Int, size: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            QuizSmallStar(size = size, active = index < stars)
        }
    }
}

@Composable
private fun QuizSmallStar(size: Dp, active: Boolean) {
    Canvas(Modifier.size(size)) {
        val radius = this.size.minDimension * 0.48f
        drawPath(
            starPath(center, radius, radius * 0.46f),
            color = if (active) QuizGold else Color.White.copy(alpha = 0.14f),
        )
    }
}

/**
 * Full-screen answer page. The result and the question progress stay fixed;
 * the answer, correct option and explanation form one scrollable body. The
 * continue button always stays on screen, and every D-pad direction reaches
 * it (Up/Down first scroll while there is more to read), but the page never
 * advances by itself: only an explicit OK press on the button confirms.
 */
@Composable
private fun QuizFeedbackPage(
    item: QuizItem,
    submission: QuizSubmission,
    questionNumber: Int,
    total: Int,
    isLast: Boolean,
    rewardSeconds: Int,
    penaltySeconds: Int?,
    holdMs: Long,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var busy by remember(item.questionId, submission.answeredAt) { mutableStateOf(false) }
    var error by remember(item.questionId, submission.answeredAt) { mutableStateOf(false) }
    var bodyFocused by remember(item.questionId) { mutableStateOf(false) }
    val bodyFocus = remember(item.questionId, submission.answeredAt) { FocusRequester() }
    val continueFocus = remember(item.questionId, submission.answeredAt) { FocusRequester() }
    val scrollStep = with(LocalDensity.current) { 84.dp.toPx() }

    val correct = submission.correct
    // A correct answer normally never reaches this page. If a storage failure
    // falls back here, do not add a hold to the retry path.
    val effectiveHoldMs = if (correct) 0L else holdMs
    val holdDeadlineAt = submission.answeredAt + effectiveHoldMs
    var holdRemaining by remember(item.questionId, submission.answeredAt, effectiveHoldMs) {
        mutableStateOf(remainingHoldSeconds(holdDeadlineAt))
    }
    LaunchedEffect(item.questionId, submission.answeredAt, effectiveHoldMs) {
        while (true) {
            val remaining = remainingHoldSeconds(holdDeadlineAt)
            holdRemaining = remaining
            if (remaining == 0) break
            delay(200)
        }
    }
    val selectedText = optionLines(
        item,
        submission.selectedIndices.ifEmpty {
            submission.selectedIndex?.let(::listOf) ?: emptyList()
        },
    )
    val correctText = optionLines(item, item.correctIndices).orEmpty()
    val title = stringResource(
        when {
            correct -> R.string.policy_quiz_feedback_correct_title
            submission.timedOut -> R.string.policy_quiz_feedback_timeout_title
            else -> R.string.policy_quiz_feedback_wrong_title
        },
    )
    val body = stringResource(
        when {
            correct -> R.string.policy_quiz_feedback_correct_body
            submission.timedOut -> R.string.policy_quiz_feedback_timeout_body
            else -> R.string.policy_quiz_feedback_wrong_body
        },
    )

    fun close() {
        // Recompute from the clock instead of trusting the last polled state, so
        // a Back press or a late UI frame cannot skip the minimum stay.
        if (busy || remainingHoldSeconds(holdDeadlineAt) > 0) return
        // The next question's reading replaces the answer line; cut it here so
        // the two voices never overlap during the transition.
        QuizSpeechPlayer.stop()
        busy = true
        error = false
        scope.launch {
            val outcome = withContext(Dispatchers.Default) { QuizChallenge.closeFeedback() }
            if (outcome is QuizChallenge.FeedbackOutcome.Failed) {
                busy = false
                // The engine agrees with the same deadline; a boundary race is
                // not a user-visible error, the button simply stays disabled.
                if (outcome.code != "FEEDBACK_HOLD") error = true
            }
        }
    }

    LaunchedEffect(item.questionId, submission.answeredAt) {
        withFrameNanos { }
        runCatching { bodyFocus.requestFocus() }
    }
    // Only a wrong or timed-out answer reaches this page for reading. The
    // correct answer is never spoken: a correct submission advances straight
    // away, and speaking it here would reveal it after the child already won.
    LaunchedEffect(item.questionId, submission.answeredAt) {
        if (!correct) QuizSpeechPlayer.speakAnswer(context, item)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The correct-answer mark reuses the published phone app's launcher icon (an apple
            // with a check) so the reward the child sees on TV matches the phone app. The
            // wrong/timeout mark keeps the drawn face.
            if (correct) QuizRewardIcon(64.dp) else QuizOopsFace(64.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    stringResource(R.string.policy_quiz_progress, questionNumber, total),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    title,
                    style = Type.Title.copy(
                        color = if (correct) QuizGold else QuizLoss,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    body,
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
            }
            if (correct) {
                BasicText(
                    stringResource(
                        R.string.policy_quiz_feedback_points_duration,
                        durationText(rewardSeconds),
                    ),
                    style = Type.Title.copy(color = QuizGold),
                )
            } else if (penaltySeconds != null) {
                BasicText(
                    if (penaltySeconds > 0) {
                        stringResource(
                            R.string.policy_quiz_feedback_penalty_duration,
                            durationText(penaltySeconds),
                        )
                    } else {
                        stringResource(R.string.policy_quiz_feedback_no_penalty)
                    },
                    style = Type.Title.copy(
                        color = if (penaltySeconds > 0) QuizLoss else Palette.SecondaryLabel,
                    ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Palette.Hairline),
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .onFocusChanged { bodyFocused = it.isFocused }
                .focusRequester(bodyFocus)
                .onKeyEvent { event ->
                    val key = event.nativeKeyEvent.keyCode
                    val arrow = key == NativeKeyEvent.KEYCODE_DPAD_UP ||
                        key == NativeKeyEvent.KEYCODE_DPAD_DOWN ||
                        key == NativeKeyEvent.KEYCODE_DPAD_LEFT ||
                        key == NativeKeyEvent.KEYCODE_DPAD_RIGHT
                    if (!arrow) return@onKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        // Any arrow can reach the continue button: Left/Right jump
                        // there at once, while Up/Down keep scrolling first so a
                        // long explanation can still be read. Only OK confirms.
                        val firstPress = event.nativeKeyEvent.repeatCount == 0
                        when {
                            key == NativeKeyEvent.KEYCODE_DPAD_LEFT ||
                                key == NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (firstPress) runCatching { continueFocus.requestFocus() }
                            }
                            key == NativeKeyEvent.KEYCODE_DPAD_UP && scroll.value > 0 -> {
                                scope.launch {
                                    scroll.animateScrollBy(
                                        -scrollStep,
                                        spring(dampingRatio = 1f, stiffness = 500f),
                                    )
                                }
                            }
                            key == NativeKeyEvent.KEYCODE_DPAD_DOWN &&
                                scroll.value < scroll.maxValue -> {
                                scope.launch {
                                    scroll.animateScrollBy(
                                        scrollStep,
                                        spring(dampingRatio = 1f, stiffness = 500f),
                                    )
                                }
                            }
                            else -> {
                                if (firstPress) runCatching { continueFocus.requestFocus() }
                            }
                        }
                    }
                    true
                }
                .focusProperties { canFocus = true }
                .focusable()
                .quizScrollBar(scroll, bodyFocused)
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 22.dp, top = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FeedbackSection(stringResource(R.string.policy_quiz_review_question)) {
                BasicText(item.prompt, style = Type.Headline)
            }
            // Mirror the question page: the answer lines keep the left of the
            // body and the figure sits to their right, so a short prompt no
            // longer walls off an empty area beside the image. The review body
            // scrolls, so the card keeps a readable fixed height.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FeedbackSection(stringResource(R.string.policy_quiz_review_your_answer)) {
                        BasicText(
                            selectedText ?: stringResource(R.string.policy_quiz_review_unanswered),
                            style = Type.Body.copy(
                                color = if (correct) QuizGold else QuizLoss,
                            ),
                        )
                    }
                    FeedbackSection(stringResource(R.string.policy_quiz_review_correct_answer)) {
                        BasicText(
                            correctText,
                            // The correct option is information, not a score:
                            // keeping it neutral stops a failed question from
                            // reading as a green loss or a gold payout.
                            style = Type.Body.copy(color = Palette.Label),
                        )
                    }
                }
                if (item.visual?.rich != null) {
                    val visualHeight = 200.dp
                    QuizQuestionVisual(
                        item = item,
                        modifier = Modifier
                            .height(visualHeight)
                            .width(quizVisualWidth(item, visualHeight)),
                    )
                }
            }
            if (item.explanation.isNotBlank()) {
                FeedbackSection(stringResource(R.string.policy_quiz_review_solution)) {
                    BasicText(
                        item.explanation,
                        style = Type.Body.copy(color = Palette.SecondaryLabel),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                if (error) {
                    stringResource(R.string.policy_quiz_error)
                } else {
                    stringResource(R.string.policy_quiz_feedback_scroll_hint)
                },
                style = Type.Caption.copy(
                    color = if (error) Palette.Orange else Palette.SecondaryLabel,
                ),
                modifier = Modifier.weight(1f),
            )
            LockButton(
                label = if (holdRemaining > 0) {
                    stringResource(R.string.policy_quiz_feedback_wait, holdRemaining)
                } else {
                    stringResource(
                        if (isLast) {
                            R.string.policy_quiz_view_result
                        } else {
                            R.string.policy_quiz_feedback_continue
                        },
                    )
                },
                onClick = ::close,
                width = 300.dp,
                focusRequester = continueFocus,
                primary = true,
                enabled = !busy && holdRemaining == 0,
                // Up/Left returns to the explanation; Down/Right stays on the
                // only button instead of searching the page for another one.
                onKey = linearDpadFocus(previous = bodyFocus),
            )
        }
    }
}

/** One option per line so a multiple-choice set stays readable on the TV. */
private fun optionLines(item: QuizItem, indices: List<Int>): String? =
    indices.distinct()
        .mapNotNull { index ->
            item.options.getOrNull(index)?.let { option -> optionLabel(index) + "  " + option }
        }
        .joinToString("\n")
        .ifEmpty { null }

/** Ceiling seconds so the countdown never shows 0 before the button is usable. */
private fun remainingHoldSeconds(
    deadlineAt: Long,
    nowMs: Long = System.currentTimeMillis(),
): Int = (((deadlineAt - nowMs).coerceAtLeast(0L) + 999L) / 1000L).toInt()

@Composable
private fun FeedbackSection(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicText(
            label,
            style = Type.Caption.copy(color = Palette.SecondaryLabel),
        )
        Spacer(Modifier.height(3.dp))
        content()
    }
}

/** One correct answer's reward flash, keyed by the time it was answered. */
private data class QuizReward(val answeredAt: Long, val seconds: Int)

/**
 * The reward mark of the published phone app: its launcher icon with the white
 * background cut out, so the apple floats on Aerial's dark surfaces.
 */
@Composable
private fun QuizRewardIcon(size: Dp) {
    Image(
        painter = painterResource(R.drawable.policy_quiz_correct_icon),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size),
    )
}

/**
 * Game-style pickup flash for a correct answer: the earned time is the whole
 * reward, so it lands as a large gold number that pops, drifts up and fades
 * out. It takes no focus, so the next question is already live underneath it.
 */
private val QuizRewardTextStyle = Type.LargeTitle.copy(
    // 3× the 26sp LargeTitle: this number is the moment.
    fontSize = 78.sp,
    fontWeight = FontWeight.W900,
    color = QuizGold,
    shadow = Shadow(
        color = Color(0x99FFB300),
        offset = Offset.Zero,
        blurRadius = 24f,
    ),
    textAlign = TextAlign.Center,
)

@Composable
private fun QuizRewardBurst(reward: QuizReward, onDone: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val pop = remember(reward.answeredAt) { Animatable(if (reduced) 1f else 0f) }
    val fade = remember(reward.answeredAt) { Animatable(1f) }
    LaunchedEffect(reward.answeredAt) {
        if (!reduced) {
            pop.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 460f))
        }
        delay(1100)
        if (!reduced) {
            fade.animateTo(0f, tween(durationMillis = 240, easing = Motion.EaseOut))
        }
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(
            stringResource(
                R.string.policy_quiz_feedback_points_duration,
                durationText(reward.seconds),
            ),
            style = QuizRewardTextStyle,
            modifier = Modifier.graphicsLayer {
                alpha = fade.value * pop.value.coerceIn(0f, 1f)
                val scale = 0.58f + 0.42f * pop.value
                scaleX = scale
                scaleY = scale
                translationY = -34.dp.toPx() * pop.value
            },
        )
    }
}

@Composable
private fun QuizOopsFace(size: Dp) {
    Canvas(Modifier.size(size)) {
        val radius = this.size.minDimension * 0.42f
        val c = center
        drawCircle(
            color = Color(0xFF7FA6FF).copy(alpha = 0.14f),
            radius = this.size.minDimension * 0.5f,
            center = c,
        )
        drawCircle(Color(0xFF5E73B8), radius * 0.86f, c)
        drawCircle(
            Color(0xFF5E73B8),
            radius * 0.38f,
            Offset(c.x - radius * 0.52f, c.y - radius * 0.42f),
        )
        drawCircle(
            Color(0xFF5E73B8),
            radius * 0.30f,
            Offset(c.x + radius * 0.52f, c.y - radius * 0.33f),
        )
        drawCircle(
            Color.White.copy(alpha = 0.10f),
            radius * 0.38f,
            Offset(c.x - radius * 0.28f, c.y - radius * 0.44f),
        )
        val eyeY = c.y - radius * 0.12f
        val eyeDx = radius * 0.22f
        val eyeRadius = radius * 0.065f
        drawCircle(Color(0xFF1E2740), eyeRadius, Offset(c.x - eyeDx, eyeY))
        drawCircle(Color(0xFF1E2740), eyeRadius, Offset(c.x + eyeDx, eyeY))
        drawArc(
            color = Color(0xFF1E2740),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(c.x - radius * 0.16f, c.y + radius * 0.22f),
            size = Size(radius * 0.32f, radius * 0.18f),
            style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round),
        )
        // Cool accents only: the wrong/timeout face must not carry the gold
        // that the quiz reserves for earned time.
        drawPath(
            starPath(
                Offset(c.x + radius * 0.86f, c.y - radius * 0.80f),
                radius * 0.22f,
                radius * 0.10f,
            ),
            Color(0xFF8AA6E8),
        )
        drawCircle(
            Color(0xFFD6E0FF).copy(alpha = 0.65f),
            radius * 0.035f,
            Offset(c.x - radius * 0.95f, c.y - radius * 0.58f),
        )
    }
}

private fun starPath(center: Offset, outer: Float, inner: Float): Path {
    val path = Path()
    repeat(10) { index ->
        val radius = if (index % 2 == 0) outer else inner
        val angle = (-PI / 2 + index * PI / 5).toFloat()
        val x = center.x + cos(angle.toDouble()).toFloat() * radius
        val y = center.y + sin(angle.toDouble()).toFloat() * radius
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

@Composable
private fun QuizExitConfirm(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancelFocus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .liquidGlass(26.dp)
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                stringResource(R.string.policy_quiz_exit_title),
                style = Type.Title.copy(textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(R.string.policy_quiz_exit_body),
                style = Type.Body.copy(
                    color = Palette.SecondaryLabel,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LockButton(
                    label = stringResource(R.string.policy_quiz_keep_going),
                    onClick = onCancel,
                    width = 180.dp,
                    focusRequester = cancelFocus,
                    primary = true,
                    onKey = linearDpadFocus(next = confirmFocus),
                )
                LockButton(
                    label = stringResource(R.string.policy_quiz_exit_confirm),
                    onClick = onConfirm,
                    width = 200.dp,
                    focusRequester = confirmFocus,
                    onKey = linearDpadFocus(previous = cancelFocus),
                )
            }
        }
    }
}

@Composable
private fun rejectText(code: String): String = when (code) {
    "EXPIRED" -> stringResource(R.string.policy_quiz_reason_expired)
    "STORAGE" -> stringResource(R.string.policy_quiz_reason_storage)
    "NOT_READY" -> stringResource(R.string.policy_quiz_reason_not_ready)
    "NOT_AVAILABLE" -> stringResource(R.string.policy_quiz_reason_not_available)
    "RESTING" -> stringResource(R.string.policy_resting_title)
    "OUTSIDE_WINDOW", "WINDOW_END" -> stringResource(R.string.policy_outside_window)
    "NO_TIMED_APPS", "INVALID_SCOPE" -> stringResource(R.string.policy_no_timed_apps_title)
    "SCREEN_OFF" -> stringResource(R.string.policy_quiz_reason_screen)
    "ALREADY_RUNNING" -> stringResource(R.string.policy_quiz_running_title)
    "CACHE_EMPTY", "LOCAL_BANK_EMPTY" -> stringResource(R.string.policy_quiz_reason_cache_empty)
    "NO_LEVELS" -> stringResource(R.string.policy_quiz_reason_no_levels)
    "LEVEL_PAUSED" -> stringResource(R.string.policy_quiz_reason_level_paused)
    "LOCAL_BANK_INSUFFICIENT" -> stringResource(R.string.policy_quiz_reason_cache_insufficient)
    "CACHE_UNAVAILABLE" -> stringResource(R.string.policy_quiz_reason_cache_unavailable)
    "SOURCE_DISABLED" -> stringResource(R.string.policy_quiz_reason_source_disabled)
    "SOURCE_UNSUPPORTED" -> stringResource(R.string.policy_quiz_reason_source_unsupported)
    else -> stringResource(R.string.policy_quiz_reason_generic)
}
