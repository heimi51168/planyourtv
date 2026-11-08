package app.orchard.tv.ui.policy

import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.quiz.CloudQuizPreparer
import app.orchard.tv.quiz.QuizChallenge
import app.orchard.tv.quiz.QuizRecovery
import app.orchard.tv.quiz.QuizRuntime
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.glassEdge
import app.orchard.tv.ui.components.isSelect
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import app.orchard.tv.ui.wallpaper.latestBringIntoView
import app.orchard.tv.wallpaper.PhoneServer
import com.planyourtv.policy.PolicyVoiceBus
import com.planyourtv.policy.PolicyVoiceCue
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizSubmission
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
 * The lock-screen quiz entry. It has three faces: the parent setup QR while a
 * session is missing or still a draft, the challenge itself once READY, and the
 * result after the TV has settled the round. Question data never leaves this
 * screen except through the encrypted session record.
 */
@Composable
fun PolicyQuizScreen(onClose: () -> Unit) {
    val state by QuizRuntime.state.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    // The correct-answer reward flash lives above the round face, so the last
    // answer's flash still plays while the report replaces the round.
    var reward by remember { mutableStateOf<QuizReward?>(null) }
    // A bound TV always goes through the cloud preparer first; the local QR
    // setup belongs exclusively to unbound TVs.
    val hosted = QuizChallenge.isHosted()
    var cloudReady by remember(hosted) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The pool is small and quiz-scoped: preload it while the start film plays,
    // then release it as soon as the child leaves the challenge.
    DisposableEffect(context) {
        QuizSfx.install(context)
        onDispose { QuizSfx.release() }
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
        onClose()
    }

    fun abandonAndClose() {
        scope.launch {
            withContext(Dispatchers.IO) { QuizChallenge.abandon() }
            closeCleanly()
        }
    }

    BackHandler {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101827), Color(0xFF03060C)))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !loaded -> QuizLoading()
            state.sessionState == "ready" && (!hosted || cloudReady) -> QuizReadyScreen(
                session = state.session ?: return@Box,
                questionCount = state.readyQuestionCount,
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
                onComplete = ::closeCleanly,
                // Keep the round when the screen is dismissed after a failed
                // settlement: the child can re-enter the quiz and retry, and
                // Back must not trap them on the report.
                onDismiss = ::closeCleanly,
                onExit = ::abandonAndClose,
            )
            hosted -> QuizCloudSetupScreen(
                onClose = ::closeCleanly,
                onReady = { cloudReady = true },
            )
            // A leftover or unreadable record can only be reset through the
            // parent page, so keep the setup QR available in that case.
            state.sessionState != "none" || !state.enabled -> QuizSetupScreen(
                onClose = ::closeCleanly,
            )
            state.bankState == "ready" -> QuizBankStartScreen(
                state = state,
                onClose = ::closeCleanly,
            )
            else -> QuizBankEmptyScreen(
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

private data class QuizQr(
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
private fun rememberQuizQr(scale: Int, attempt: Int = 0): QuizQr {
    val context = LocalContext.current
    var qr by remember { mutableStateOf(QuizQr(null, null, true)) }
    LaunchedEffect(scale, attempt) {
        qr = QuizQr(qr.url, qr.bitmap, true)
        val link = withContext(Dispatchers.IO) {
            PhoneServer.start(context)?.let { "${it}quiz" }
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
 * Unbound start face. The local bank is already populated, so the child sees
 * the same round summary as the cloud start and the parent keeps a small QR to
 * add more questions between rounds.
 */
@Composable
private fun QuizBankStartScreen(
    state: QuizRuntime.UiState,
    onClose: () -> Unit,
) {
    val config = state.defaultChallengeConfig
    val roundCount = config.pullCount.coerceAtMost(state.bankUnused)
    val qr = rememberQuizQr(scale = 8)
    var busy by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val startFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(3_000)
            withContext(Dispatchers.IO) { QuizRuntime.refresh() }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { startFocus.requestFocus() }
    }

    fun begin() {
        if (busy || leaving) return
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.Default) { QuizChallenge.start() }
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

    Box(Modifier.fillMaxSize()) {
        QuizStartBackdrop(fadeOut = busy || leaving)

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
            BasicText(
                stringResource(
                    R.string.policy_quiz_start_body,
                    roundCount,
                    config.minCorrectCount,
                    config.minutesPerQuestion,
                ),
                style = Type.Body.copy(color = Palette.SecondaryLabel),
                modifier = Modifier.widthIn(max = 404.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Home, Palette.SecondaryLabel, 15.dp)
                Spacer(Modifier.width(7.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_bank_stock, state.bankUnused),
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
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LockButton(
                    label = stringResource(R.string.policy_quiz_start),
                    onClick = ::begin,
                    width = 220.dp,
                    focusRequester = startFocus,
                    primary = true,
                    onKey = linearDpadFocus(next = backFocus),
                )
                LockButton(
                    label = stringResource(R.string.policy_back),
                    onClick = ::close,
                    width = 160.dp,
                    focusRequester = backFocus,
                    onKey = linearDpadFocus(previous = startFocus),
                )
            }
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
 * Unbound empty/short bank face. It is a parent hand-off: the large QR is the
 * only action that can unblock the child, and the screen keeps refreshing so it
 * turns into the start face as soon as the page appends enough questions.
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
    val need = (state.defaultChallengeConfig.minCorrectCount - state.bankUnused)
        .coerceAtLeast(0)

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
                stringResource(R.string.policy_quiz_bank_empty_title),
                style = Type.LargeTitle,
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(R.string.policy_quiz_bank_empty_body),
                style = Type.Body.copy(color = Palette.SecondaryLabel),
                modifier = Modifier.widthIn(max = 410.dp),
            )
            if (need > 0) {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_bank_insufficient, need),
                    style = Type.Caption.copy(color = Palette.Orange),
                    modifier = Modifier.widthIn(max = 410.dp),
                )
            }
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
 * Bound-TV setup. There is no QR and no local publish path here: the questions
 * come from the cloud bank tied to the family. A failed fetch is shown as plain
 * human text with a retry, never as a server detail.
 */
@Composable
private fun QuizCloudSetupScreen(onClose: () -> Unit, onReady: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    // Give a fast fetch one second to finish before showing this card. A
    // reusable round normally returns in a few hundred milliseconds; hiding
    // the card until then goes straight to the start face instead of flashing
    // the preparation screen first. A slow fetch still shows the loading card.
    var visible by remember { mutableStateOf(false) }
    val retryFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(1_000)
        visible = true
    }
    LaunchedEffect(attempt) {
        loading = true
        message = null
        val outcome = withContext(Dispatchers.IO) { CloudQuizPreparer.prepare() }
        loading = false
        when (outcome) {
            CloudQuizPreparer.Outcome.Ready -> onReady()
            is CloudQuizPreparer.Outcome.Unavailable -> message = outcome.message
        }
    }
    LaunchedEffect(loading, message, visible) {
        // The buttons are not composed until the card is visible. Requesting
        // focus earlier would detach it from the retry/back controls.
        if (!visible) return@LaunchedEffect
        withFrameNanos { }
        runCatching {
            if (!loading && message != null) retryFocus.requestFocus() else backFocus.requestFocus()
        }
    }

    if (!visible) return

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
            GlyphIcon(Glyph.Shield, Palette.Label, 96.dp)
        }
        Column(modifier = Modifier.width(470.dp)) {
            BasicText(
                stringResource(R.string.policy_quiz_cloud_kicker),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
            )
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(R.string.policy_quiz_cloud_title),
                style = Type.LargeTitle,
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(R.string.policy_quiz_cloud_body),
                style = Type.Body.copy(color = Palette.SecondaryLabel),
                modifier = Modifier.widthIn(max = 450.dp),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                message ?: stringResource(R.string.policy_quiz_cloud_loading),
                style = Type.Caption.copy(
                    color = if (message != null) Palette.Orange else Palette.SecondaryLabel,
                ),
                modifier = Modifier.widthIn(max = 450.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!loading && message != null) {
                    LockButton(
                        label = stringResource(R.string.policy_quiz_cloud_retry),
                        onClick = { attempt += 1 },
                        width = 180.dp,
                        focusRequester = retryFocus,
                        primary = true,
                        onKey = linearDpadFocus(next = backFocus),
                    )
                }
                LockButton(
                    label = stringResource(R.string.policy_back),
                    onClick = onClose,
                    width = 160.dp,
                    focusRequester = backFocus,
                    onKey = linearDpadFocus(
                        previous = if (!loading && message != null) retryFocus else null,
                    ),
                )
            }
        }
    }
}

@Composable
private fun QuizReadyScreen(
    session: QuizSession,
    questionCount: Int,
    onClose: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val startFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { startFocus.requestFocus() }
    }

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

    Box(Modifier.fillMaxSize()) {
        QuizStartBackdrop(fadeOut = busy || leaving)

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
            BasicText(
                stringResource(
                    R.string.policy_quiz_start_body,
                    questionCount,
                    session.config.minCorrectCount,
                    session.config.minutesPerQuestion,
                ),
                style = Type.Body.copy(color = Palette.SecondaryLabel),
                modifier = Modifier.widthIn(max = 404.dp),
            )
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
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LockButton(
                    label = stringResource(R.string.policy_quiz_start),
                    onClick = ::begin,
                    width = 220.dp,
                    focusRequester = startFocus,
                    primary = true,
                    onKey = linearDpadFocus(next = backFocus),
                )
                LockButton(
                    label = stringResource(R.string.policy_back),
                    onClick = ::close,
                    width = 160.dp,
                    focusRequester = backFocus,
                    onKey = linearDpadFocus(previous = startFocus),
                )
            }
        }
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
            var resumeFailed by remember(submission.answeredAt) { mutableStateOf(false) }
            if (submission.correct) {
                // A correct answer never opens the answer page: flash the earned
                // time and resume the next question at once. Only a storage
                // failure falls back to the page so the round cannot hang blank.
                LaunchedEffect(submission.answeredAt) {
                    onReward(
                        QuizReward(
                            answeredAt = submission.answeredAt,
                            minutes = session.config.minutesPerQuestion,
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
            QuizFeedbackPage(
                item = answeredItem,
                submission = submission,
                questionNumber = run.index.coerceIn(1, session.items.size),
                total = session.items.size,
                isLast = run.index >= session.items.size,
                minutesPerQuestion = session.config.minutesPerQuestion,
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
        minutesPerQuestion = session.config.minutesPerQuestion,
        onExit = onExit,
    )
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
    minutesPerQuestion: Int,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var submitting by remember(item.questionId) { mutableStateOf(false) }
    var remaining by remember(item.questionId, deadlineAt) {
        mutableLongStateOf(QuizChallenge.remainingSeconds())
    }
    var error by remember(item.questionId) { mutableStateOf(false) }
    val promptFocus = remember(item.questionId) { FocusRequester() }
    // One requester per option so Up/Left and Down/Right can walk the whole
    // answer list, then hand over to the prompt or the exit button at its ends.
    val optionFocuses = remember(item.questionId, item.options.size) {
        List(item.options.size) { FocusRequester() }
    }
    val firstOptionFocus = optionFocuses.firstOrNull()
    val exitFocus = remember(item.questionId) { FocusRequester() }
    val promptScroll = remember(item.questionId) { ScrollState(0) }
    val optionsScroll = remember(item.questionId) { ScrollState(0) }
    var promptFocused by remember(item.questionId) { mutableStateOf(false) }
    val scrollStep = with(LocalDensity.current) { 80.dp.toPx() }

    fun submit(selectedIndex: Int?, timedOut: Boolean) {
        if (submitting) return
        submitting = true
        error = false
        // Use the press time, not the time after storage I/O, so a tap made
        // just before the deadline still counts.
        val answeredAt = System.currentTimeMillis()
        scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                QuizChallenge.answer(selectedIndex, timedOut, answeredAt)
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
                submit(null, true)
                break
            }
            delay(1_000)
        }
    }
    LaunchedEffect(item.questionId) {
        withFrameNanos { }
        runCatching { promptFocus.requestFocus() }
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
                    style = Type.Title.copy(
                        color = when {
                            remaining <= 5 -> Palette.Red
                            remaining <= 10 -> Palette.Orange
                            else -> Palette.Label
                        },
                    ),
                )
                BasicText(
                    stringResource(R.string.policy_quiz_points_hint, minutesPerQuestion),
                    style = Type.Caption.copy(color = Palette.SecondaryLabel),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (promptFocused) Palette.FillStrong else Palette.Fill),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { promptFocused = it.isFocused }
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
                                            firstOptionFocus?.let { runCatching { it.requestFocus() } }
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
                                    firstOptionFocus?.let { runCatching { it.requestFocus() } }
                                }
                                true
                            }

                            else -> if (event.isSelect()) {
                                if (event.type == KeyEventType.KeyDown) {
                                    firstOptionFocus?.let { runCatching { it.requestFocus() } }
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
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                        onClick = { submit(index, false) },
                        focusRequester = optionFocuses[index],
                        enabled = !submitting,
                        // Up/Left and Down/Right both walk the answer list. The
                        // first answer returns to the prompt, the last hands over
                        // to the exit button, so no arrow can lose focus.
                        onKey = linearDpadFocus(
                            previous = if (index == 0) promptFocus else optionFocuses[index - 1],
                            next = if (index == item.options.lastIndex) exitFocus else optionFocuses[index + 1],
                        ),
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
                    stringResource(R.string.policy_quiz_question_scroll_hint)
                },
                style = Type.Caption.copy(
                    color = if (error) Palette.Orange else Palette.SecondaryLabel,
                ),
                modifier = Modifier.weight(1f),
            )
            LockButton(
                label = stringResource(R.string.policy_quiz_exit),
                onClick = onExit,
                width = 160.dp,
                focusRequester = exitFocus,
                enabled = !submitting,
                // The exit button is the end of the chain: Up/Left goes back to
                // the answers, Down/Right stays here.
                onKey = linearDpadFocus(
                    previous = optionFocuses.lastOrNull() ?: promptFocus,
                ),
            )
        }
    }
}

@Composable
private fun QuizOptionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean,
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (state.focused) Palette.Focus else Palette.Fill)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                label,
                style = Type.Body.copy(
                    color = if (state.focused) Palette.OnFocus else Palette.Label,
                ),
            )
        }
    }
}

/** A right-edge scroll bar drawn outside the scroll node, so it stays put. */
private fun Modifier.quizScrollBar(scroll: ScrollState, focused: Boolean): Modifier = drawWithContent {
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
 * shows the totals, earned time and a warm evaluation; there is no forced wait
 * before the child can start the earned session or go back.
 */
@Composable
private fun QuizReviewScreen(
    session: QuizSession,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    onExit: () -> Unit,
) {
    val run = session.run ?: return
    val total = session.items.size
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
    val errorCode = run.startErrorCode ?: localError
    val needMore = (session.config.minCorrectCount - correct).coerceAtLeast(0)
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
                        QuizChallenge.completeReviewHosted()
                    } else {
                        QuizChallenge.completeReview()
                    }
                }
                busy = false
                when (outcome) {
                    QuizChallenge.ReviewOutcome.Started,
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
        if (run.earnedMinutes > 0) {
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
            QuizResultVideo(180.dp)
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
                    style = Type.Title.copy(color = if (failed) Palette.Orange else Palette.Green),
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
                color = Palette.Green,
                modifier = Modifier.weight(1f),
            )
            ReportStat(
                label = stringResource(R.string.policy_quiz_report_minutes_label),
                value = stringResource(R.string.policy_quiz_report_minutes_value, run.earnedMinutes),
                color = if (run.earnedMinutes > 0) Palette.Green else Palette.SecondaryLabel,
                modifier = Modifier.weight(1f),
            )
        }
        if (needMore > 0 && run.earnedMinutes <= 0) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                stringResource(R.string.policy_quiz_report_need_more, needMore),
                style = Type.Body.copy(
                    color = Palette.SecondaryLabel,
                    textAlign = TextAlign.Center,
                ),
            )
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
                run.earnedMinutes > 0 -> stringResource(R.string.policy_quiz_review_start)
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
            color = if (active) Color(0xFFFFC83D) else Color.White.copy(alpha = 0.14f),
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
    minutesPerQuestion: Int,
) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var busy by remember(item.questionId, submission.answeredAt) { mutableStateOf(false) }
    var error by remember(item.questionId, submission.answeredAt) { mutableStateOf(false) }
    var bodyFocused by remember(item.questionId) { mutableStateOf(false) }
    val bodyFocus = remember(item.questionId, submission.answeredAt) { FocusRequester() }
    val continueFocus = remember(item.questionId, submission.answeredAt) { FocusRequester() }
    val scrollStep = with(LocalDensity.current) { 84.dp.toPx() }

    val correct = submission.correct
    val selectedText = submission.selectedIndex
        ?.let { index -> item.options.getOrNull(index)?.let { optionLabel(index) + "  " + it } }
    val correctText = optionLabel(item.answerIndex) + "  " + item.options[item.answerIndex]
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
        if (busy) return
        busy = true
        error = false
        scope.launch {
            val outcome = withContext(Dispatchers.Default) { QuizChallenge.closeFeedback() }
            if (outcome is QuizChallenge.FeedbackOutcome.Failed) {
                busy = false
                error = true
            }
        }
    }

    LaunchedEffect(item.questionId, submission.answeredAt) {
        withFrameNanos { }
        runCatching { bodyFocus.requestFocus() }
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
                        color = if (correct) Palette.Green else Palette.Orange,
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
                    stringResource(R.string.policy_quiz_feedback_points, minutesPerQuestion),
                    style = Type.Title.copy(color = Palette.Green),
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
            FeedbackSection(stringResource(R.string.policy_quiz_review_your_answer)) {
                BasicText(
                    selectedText ?: stringResource(R.string.policy_quiz_review_unanswered),
                    style = Type.Body.copy(
                        color = if (correct) Palette.Green else Palette.Orange,
                    ),
                )
            }
            FeedbackSection(stringResource(R.string.policy_quiz_review_correct_answer)) {
                BasicText(
                    correctText,
                    style = Type.Body.copy(color = Palette.Green),
                )
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
                label = stringResource(
                    if (isLast) {
                        R.string.policy_quiz_view_result
                    } else {
                        R.string.policy_quiz_feedback_continue
                    },
                ),
                onClick = ::close,
                width = 300.dp,
                focusRequester = continueFocus,
                primary = true,
                enabled = !busy,
                // Up/Left returns to the explanation; Down/Right stays on the
                // only button instead of searching the page for another one.
                onKey = linearDpadFocus(previous = bodyFocus),
            )
        }
    }
}

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
private data class QuizReward(val answeredAt: Long, val minutes: Int)

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
    color = Color(0xFFFFD45A),
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
            stringResource(R.string.policy_quiz_feedback_points, reward.minutes),
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
        drawPath(
            starPath(
                Offset(c.x + radius * 0.86f, c.y - radius * 0.80f),
                radius * 0.22f,
                radius * 0.10f,
            ),
            Color(0xFFFFD54F),
        )
        drawCircle(
            Color(0xFFFFF3C4).copy(alpha = 0.8f),
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
    "LOCAL_BANK_INSUFFICIENT" -> stringResource(R.string.policy_quiz_reason_cache_insufficient)
    "CACHE_UNAVAILABLE" -> stringResource(R.string.policy_quiz_reason_cache_unavailable)
    "SOURCE_DISABLED" -> stringResource(R.string.policy_quiz_reason_source_disabled)
    "SOURCE_UNSUPPORTED" -> stringResource(R.string.policy_quiz_reason_source_unsupported)
    else -> stringResource(R.string.policy_quiz_reason_generic)
}

private fun optionLabel(index: Int): String =
    if (index in 0..5) ('A' + index).toString() else (index + 1).toString()
