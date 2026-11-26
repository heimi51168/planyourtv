package app.orchard.tv.ui.policy

import android.content.Context
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import app.orchard.tv.policy.PolicyPanelRefresh
import app.orchard.tv.policy.PolicyProduct
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
import app.orchard.tv.CcPage
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.R
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.PolicyHomeReady
import app.orchard.tv.policy.LockLaunchPermit
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.SourceEntrySettings
import app.orchard.tv.policy.media.PolicyMediaKind
import app.orchard.tv.quiz.QuizLaunchTarget
import app.orchard.tv.quiz.QuizRuntime
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.home.CornerClock
import app.orchard.tv.ui.home.LocalStatusPillTrailing
import app.orchard.tv.ui.home.ShelfClock
import app.orchard.tv.ui.home.StatusPill
import app.orchard.tv.ui.home.rememberNow
import app.orchard.tv.ui.home.timeText
import app.orchard.tv.ui.policy.lockdesktop.LockDesktopBackdrop
import app.orchard.tv.ui.policy.lockdesktop.LockDesktopDock
import app.orchard.tv.ui.policy.lockdesktop.LockDesktopStart
import app.orchard.tv.ui.policy.lockdesktop.PolicyLockFilm
import app.orchard.tv.ui.policy.lockdesktop.LockGuardPanel
import app.orchard.tv.ui.policy.lockdesktop.lockDockMetrics
import app.orchard.tv.ui.policy.lockdesktop.rememberLockDockState
import app.orchard.tv.ui.policy.lockdesktop.LockDockId
import app.orchard.tv.ui.policy.lockdesktop.LockDockSlot
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.Activation
import com.planyourtv.policy.CloudMember
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
import com.planyourtv.policy.UnlockRoute
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

/**
 * How long a decoded picture takes to replace a film's last frame. A film's first frame uses
 * the player's own 540 ms fade; this is the reverse direction, started only once the picture
 * is in memory so the surface never shows the window's black.
 */
private const val FILM_TO_STILL_FADE_MS = 450

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
private enum class LockAction { STUDY, QUIZ, MANUAL, CONFIRM_START, PARENT_UNLOCK }

/**
 * The whole TV becomes this screen whenever the session is not ACTIVE and the
 * guard is RUNNING. It is a normal Compose surface, not an overlay window.
 */
@Composable
fun PolicyHomeGate(
    state: PolicyUiState,
    vm: HomeViewModel,
    onLaunch: (AppEntry, Rect?) -> Unit,
    home: @Composable () -> Unit,
) {
    val homeVisible = showsHome(state)
    val desired = PolicyModeCoordinator.accessFingerprint(state)
    val applied by PolicyHomeReady.fingerprint.collectAsState()
    var preparing by remember { mutableStateOf(false) }
    var wasGate by remember { mutableStateOf(!homeVisible) }
    var lastDesired by remember { mutableStateOf(desired) }
    // The focus intro is a gate, not a round: it creates no timer and starts no app until
    // 开始专注, so a stray OK on a study tile cannot launch anything or arm a countdown.
    var studyIntroApp by remember { mutableStateOf<AppEntry?>(null) }
    // A quiz opened from an entertainment app or a TV input confirms the entry inside the
    // settlement, not in the lock screen: the earned session disposes the lock screen before the
    // review coroutine can call back, so the one-shot launch must live above it. Wait for the
    // policy session, resolve the entry again against the live scope and the current Home list,
    // then open it once.
    val quizLaunch by QuizLaunchTarget.confirmed.collectAsState()
    val stateNow by rememberUpdatedState(state)
    val launchNow by rememberUpdatedState(onLaunch)
    LaunchedEffect(quizLaunch) {
        val target = quizLaunch ?: return@LaunchedEffect
        val ready = withTimeoutOrNull(5_000L) {
            snapshotFlow {
                stateNow.sessionState == SessionState.ACTIVE &&
                    stateNow.guardState == GuardStatus.ENFORCING &&
                    vm.loaded
            }.first { it }
        } != null
        when (target) {
            is QuizLaunchTarget.Target.App -> {
                // The app may have been uninstalled or removed from the timed scope while the
                // round ran; only an app the child can actually open now is launched.
                val app = if (ready) {
                    vm.timedApps(stateNow.allowedPackages)
                        .firstOrNull { it.packageName == target.packageName }
                } else {
                    null
                }
                QuizLaunchTarget.consume(target)
                if (app == null) return@LaunchedEffect
                PolicyFlowTrace.event("quiz.launch.target", "package" to app.packageName)
                launchNow(app, null)
            }
            is QuizLaunchTarget.Target.Inputs -> {
                // Like an app tile, the parent's source switch must still be on after the round.
                // The earned session makes the normal Home input list the correct next step: the
                // child picks the input there, under the same policy filter Control Center uses.
                val allowed = ready && SourceEntrySettings.visible.value &&
                    PolicyModeCoordinator.sourceEntriesAllowed()
                QuizLaunchTarget.consume(target)
                if (!allowed) return@LaunchedEffect
                PolicyFlowTrace.event("quiz.launch.inputs")
                vm.overlay = Overlay.ControlCenter(CcPage.Inputs)
            }
        }
    }
    // The child wallpaper owns every surface while child mode is enforced: the lock screen
    // before a session starts and the active session's Home. Study mode, parent override,
    // policy-off Home and the parent center keep the owner's wallpaper. The default picture is
    // not a configured role but still fills these surfaces: a fresh install must look different
    // from parent mode before anything is chosen.
    val childActive = state.loaded && state.guardState == GuardStatus.ENFORCING
    // Study mode uses Home rather than the lock desktop, so it has no child wallpaper. It is still
    // a child desktop: Home must name the focused app there as it does in an active session.
    val childHome = childActive ||
        (state.loaded && state.mode != PolicyMode.NONE && state.guardState == GuardStatus.STUDY_MODE)
    // The parent center is a full-screen overlay above this gate. It is a parent surface, so the
    // child wallpaper's music and rotation pause underneath it.
    val parentCenterVisible by PolicyProduct.parentCenterVisible.collectAsState()
    // The screensaver is composited inside HomeScreen, never above this gate. While a child gate
    // (lock desktop, focus intro/timer, fingerprint wait) covers Home, the idle timer can still
    // set vm.screensaverVisible; treating that invisible flag as "the screensaver is showing"
    // froze the rotation and the music. Only let it pause child surfaces when Home is really on
    // screen, and clear a flag that was set while the gate was up.
    val gateHidesHome = !homeVisible || preparing || studyIntroApp != null ||
        (childActive && state.studyRound != null)
    val screensaverCoversChild = vm.screensaverVisible && !gateHidesHome
    LaunchedEffect(gateHidesHome, vm.screensaverVisible) {
        if (gateHidesHome && vm.screensaverVisible) vm.screensaverVisible = false
    }
    // The screensaver must not move the rotation to a picture the child never sees, and a
    // carousel film stops at its natural end instead of advancing underneath it.
    // The unlock choice overlay holds the same frame too: the desktop and its glass must not change
    // while the child is deciding, and closing it should return to the picture they left.
    var choiceOpen by remember { mutableStateOf(false) }
    // A quiz/recovery overlay takes the sound stage as well as the picture. The lock screen
    // reports it here because the music player lives above both the lock screen and Home.
    var quizOpen by remember { mutableStateOf(false) }
    LaunchedEffect(childActive) {
        if (!childActive) {
            choiceOpen = false
            quizOpen = false
        }
    }
    val childWallpaper = if (childActive) {
        rememberPolicyChildWallpaperState(
            playing = !screensaverCoversChild && !choiceOpen && !parentCenterVisible,
        )
    } else {
        null
    }
    val childAudio = if (childActive) rememberPolicyBackgroundAudio() else null
    // "Wallpaper sound" is the master switch for the child wallpaper. A selected background
    // track owns the audio stage and keeps videos muted; turning the switch off silences both.
    val childSoundEnabled = childWallpaper?.soundEnabled == true
    val videoSoundEnabled = childSoundEnabled && childAudio == null
    PolicyBackgroundMusic(
        entry = childAudio,
        active = childSoundEnabled && childActive && !screensaverCoversChild && !choiceOpen &&
            !quizOpen && !parentCenterVisible,
    )
    val childLoading = childActive && childWallpaper?.loading == true
    val childFilmCover = remember { Animatable(0f) }
    // A film fades its first frame in by itself (PolicyLockFilm). The reverse - a film giving
    // way to a picture or to the bundled default - starts only once that picture is decoded;
    // until then the surface keeps the film's last frame. Snapping the cover when the source
    // changed instead showed the previous picture (or black) for the decode time, which was
    // the flash seen when a rotation advanced.
    val childSourceKind = childWallpaper?.source?.kind
    val childStillReady = childWallpaper == null || childWallpaper.imageReady
    LaunchedEffect(childSourceKind, childStillReady) {
        if (childSourceKind != PolicyMediaKind.VIDEO && childStillReady) {
            childFilmCover.animateTo(
                targetValue = 0f,
                animationSpec = tween(FILM_TO_STILL_FADE_MS, easing = Motion.EaseOut),
            )
        }
    }
    val homeWallpaper = if (childActive) {
        PolicyHomeWallpaper(
            image = childWallpaper?.image,
            frosted = childWallpaper?.frosted,
            source = childWallpaper?.source,
            filmCover = childFilmCover,
            soundEnabled = videoSoundEnabled,
            playlistSize = childWallpaper?.playlistSize ?: 1,
            onVideoEnded = childWallpaper?.onVideoEnded,
            onVideoError = childWallpaper?.onVideoError,
        )
    } else {
        null
    }

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

    val studyRoundActive = state.studyRound != null
    LaunchedEffect(state.guardState, state.sessionState, studyRoundActive) {
        if (state.guardState != GuardStatus.ENFORCING ||
            state.sessionState == SessionState.ACTIVE ||
            studyRoundActive
        ) {
            studyIntroApp = null
        }
    }
    when {
        !state.loaded -> Box(Modifier.fillMaxSize().background(Color.Black))
        // The fingerprint wait is short, but it is the first frame after a start or a temporary
        // unlock: hold the target wallpaper there instead of a black screen.
        preparing || childLoading ->
            DesktopPreparingScreen(childWallpaper = childWallpaper, childActive = childActive)
        state.mode == PolicyMode.NONE -> PolicyHomeWithBadge(home)
        state.mode == PolicyMode.LOCAL && state.setupRequired -> PolicySetupScreen(state)
        state.mode == PolicyMode.CLOUD &&
            state.guardState == GuardStatus.PARENT_OVERRIDE && !state.bound && !state.bindingDeferred ->
            PolicyBindingScreen(state)
        // A focus round is still the lock state, not an entertainment session. While Aerial is
        // in front it shows this timer desktop; the round ends into the reward session and the
        // normal Home branch below takes over.
        state.guardState == GuardStatus.ENFORCING && state.studyRound != null -> {
            val round = requireNotNull(state.studyRound)
            val focusStudyApp = remember(
                round.studyPackage,
                vm.loaded,
                vm.apps,
                vm.hiddenApps,
                vm.folderApps,
            ) { vm.studyApps(setOf(round.studyPackage)).firstOrNull() }
            PolicyFocusStudyScreen(
                round = round,
                studyApp = focusStudyApp,
                childWallpaper = childWallpaper,
                onContinue = {
                    if (focusStudyApp == null) {
                        null
                    } else {
                        when (val result = PolicyModeCoordinator.resumeStudyRound()) {
                            SessionStartResult.Started -> {
                                LockLaunchPermit.grant(round.studyPackage)
                                onLaunch(focusStudyApp, null)
                                null
                            }
                            is SessionStartResult.Rejected ->
                                studyEarnResumeMessage(result.reason)
                            SessionStartResult.Pending -> null
                        }
                    }
                },
                onExit = {
                    PolicyModeCoordinator.cancelStudyRound("CHILD_EXIT")
                },
            )
        }
        state.guardState == GuardStatus.ENFORCING && state.studyRound == null &&
            state.sessionState != SessionState.ACTIVE && studyIntroApp != null -> {
            val introApp = requireNotNull(studyIntroApp)
            PolicyStudyFocusIntroScreen(
                studyApp = introApp,
                studyMinutes = state.studyEarnStudyMinutes,
                rewardMinutes = state.studyEarnRewardMinutes,
                childWallpaper = childWallpaper,
                onStart = {
                    when (val result = PolicyModeCoordinator.startStudyRound(
                        introApp.packageName,
                        targetPackage = null,
                    )) {
                        SessionStartResult.Started -> {
                            studyIntroApp = null
                            LockLaunchPermit.grant(introApp.packageName)
                            PolicyFlowTrace.event(
                                "launch.study.earn",
                                "package" to introApp.packageName,
                                "intro" to true,
                            )
                            onLaunch(introApp, null)
                            null
                        }
                        is SessionStartResult.Rejected ->
                            studyEarnRejectMessage(result.reason)
                        SessionStartResult.Pending -> null
                    }
                },
                onExit = { studyIntroApp = null },
            )
        }
        state.guardState == GuardStatus.ENFORCING && state.sessionState != SessionState.ACTIVE ->
            PolicyLockScreen(
                state = state,
                vm = vm,
                onLaunch = onLaunch,
                childWallpaper = childWallpaper,
                videoSoundEnabled = videoSoundEnabled,
                onChoiceOpenChanged = { choiceOpen = it },
                onQuizOpenChanged = { quizOpen = it },
                onStudyEarnIntro = { app ->
                    PolicyFlowTrace.event(
                        "launch.study.intro",
                        "package" to app.packageName,
                    )
                    studyIntroApp = app
                },
            )
        // Study mode also lands here. The executor publishes the study list as the APPS scope,
        // so HomeViewModel hands Home only those tiles; a dedicated launcher would duplicate the
        // grid and break the desktop's look, focus and navigation. Control Center carries the
        // study-mode exit row, so the child is not stuck on the study desktop.
        else -> PolicyHomeWithBadge(home, homeWallpaper, focusNames = childHome)
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
private fun DesktopPreparingScreen(
    childWallpaper: PolicyChildWallpaper?,
    childActive: Boolean,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PolicyPreparingBackdrop(childWallpaper, childActive)
        BasicText(
            text = stringResource(R.string.policy_desktop_preparing),
            style = Type.Title.copy(
                color = Color.White,
                shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 8f),
            ),
        )
    }
}

@Composable
private fun PolicyHomeWithBadge(
    home: @Composable () -> Unit,
    wallpaper: PolicyHomeWallpaper? = null,
    focusNames: Boolean = false,
) {
    val badge: @Composable (Color) -> Unit = { foreground ->
        PolicyStatusPillBadge(foreground)
    }
    CompositionLocalProvider(
        LocalStatusPillTrailing provides badge,
        LocalPolicyHomeWallpaper provides wallpaper,
        LocalPolicyFocusNames provides focusNames,
    ) {
        home()
    }
}

@Composable
private fun PolicyLockScreen(
    state: PolicyUiState,
    vm: HomeViewModel,
    onLaunch: (AppEntry, Rect?) -> Unit,
    childWallpaper: PolicyChildWallpaper?,
    /** False when background music owns the sound, so the lock film stays silent. */
    videoSoundEnabled: Boolean,
    onChoiceOpenChanged: (Boolean) -> Unit,
    onQuizOpenChanged: (Boolean) -> Unit,
    onStudyEarnIntro: (AppEntry) -> Unit,
) {
    // Read in the draw phase below: a remote config change during the TV's idle standby needs
    // one more frame after the injected BACK that wakes the panel (PolicyPanelRefresh).
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
    // The entertainment app that opened this quiz. A successful round launches it after the
    // earned session starts; the dedicated quiz tile leaves this null.
    var quizTarget by remember { mutableStateOf<AppEntry?>(null) }
    // The unlock choice overlay. It is opened by an entertainment icon only when a parent has
    // configured study-to-earn; otherwise the icon keeps opening the quiz directly.
    var unlockChoiceMode by remember { mutableStateOf(false) }
    var unlockChoiceTarget by remember { mutableStateOf<AppEntry?>(null) }
    var unlockChoiceMessage by remember { mutableStateOf<Int?>(null) }
    // V58 fruit wheel. It is a second modal over the same live desktop; [fruitFromChoice]
    // remembers whether Back should return to the four-route card or straight to the lock screen.
    var fruitMode by remember { mutableStateOf(false) }
    var fruitFromChoice by remember { mutableStateOf(false) }
    // Hosted fruit exchanges go through the orchard, so the wheel has a "submitting" phase and
    // can show the backend's own rejection text. LOCAL exchanges stay synchronous and never set
    // either value.
    var fruitSubmitting by remember { mutableStateOf(false) }
    var fruitMessageText by remember { mutableStateOf<String?>(null) }
    // The choice is drawn over this composition's live desktop, so the existing wallpaper player
    // keeps running. Hold only the rotation in the gate instead of letting the interval move the
    // picture behind the card; every close path (including this screen being replaced by a
    // session) hands the rotation back.
    val notifyChoiceOpen = rememberUpdatedState(onChoiceOpenChanged)
    SideEffect { notifyChoiceOpen.value(unlockChoiceMode || fruitMode) }
    // A quiz or recovery overlay owns the screen and must not compete with background music.
    // The same callback also releases it if this screen is replaced mid-flow by a session.
    val notifyQuizOpen = rememberUpdatedState(onQuizOpenChanged)
    SideEffect { notifyQuizOpen.value(quizMode || recoveryMode) }
    DisposableEffect(Unit) {
        onDispose {
            notifyChoiceOpen.value(false)
            notifyQuizOpen.value(false)
        }
    }
    // The main-menu footer follows the focused button. Null means the default action owns focus.
    var focusedAction by remember { mutableStateOf<LockAction?>(null) }
    // The study apps are real icons rather than an action card. Their focus is kept apart from
    // [focusedAction] so the footer can name the app and the idle key handler knows the dock owns
    // focus even while no fixed action is selected.
    var focusedStudyPackage by remember { mutableStateOf<String?>(null) }
    // Entertainment icons are the quiz entry, not launch shortcuts. They keep their own focus
    // marker so the footer explains the quiz without naming the app as an action.
    var focusedGatewayPackage by remember { mutableStateOf<String?>(null) }
    // Unlimited apps launch directly and are never timed. Their focus marker stays separate so
    // the footer can say the app does not count against the schedule.
    var focusedUnlimitedPackage by remember { mutableStateOf<String?>(null) }
    // The top-right guard badge opens a small parent panel. One state drives BACK, the overlay
    // flags and the panel itself, so no flow can leave it open behind another sheet.
    var guardPanelOpen by remember { mutableStateOf(false) }
    // The member sheet is inline, above the dock, rather than a Dialog window: on the projector
    // the new window took over a second to appear and read as a frozen remote.
    var subjectSheetOpen by remember { mutableStateOf(false) }
    // Set between opening the panel and handing focus back: the first composition (no panel has
    // ever opened) must not pull focus to the badge.
    var guardWasOpen by remember { mutableStateOf(false) }
    val quizState by QuizRuntime.state.collectAsState()
    // Compose focuses the first focusable child when the screen appears. This target sits on
    // the sheet itself so the lock screen opens with no action selected; a redemption then
    // moves focus to the start entry.
    val idleFocus = remember { FocusRequester() }
    // The first remote direction press enters the dock at its first enabled tile: a fixed action
    // when one is available, otherwise the first study app. This never fires on its own.
    val firstActionFocus = remember { FocusRequester() }
    val startFocus = remember { FocusRequester() }
    val firstKeyFocus = remember { FocusRequester() }
    val durationFocus = remember { FocusRequester() }
    val guardFocus = remember { FocusRequester() }
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
    // The desktop arrangement is the V10 main menu. PIN and duration hide the dock and open their
    // existing panel over the same wallpaper, so their keypad and wheel do not compete with the
    // dock for focus.
    val desktopMenu = v10 && !pinMode && !durationMode
    // A fixed action, a quiz-trigger app or a study icon can own the dock's focus. The root sheet
    // must stay unfocusable in all cases, or the idle next-key handler would intercept D-pad
    // movement.
    val dockFocusActive = focusedAction != null || focusedStudyPackage != null ||
        focusedGatewayPackage != null || focusedUnlimitedPackage != null
    // Redemption is a manual grant, not a separate unlock mode. The legacy cloud engine does
    // not publish the V10 window or MANUAL_WAITING, so treat a bound REDEEM policy as manual
    // here and let the existing manual entries use their normal cloud path.
    val redeemManual = state.mode == PolicyMode.CLOUD && state.bound &&
        state.remoteMode == RemotePolicyMode.REDEEM &&
        state.activation == Activation.MANUAL
    val localManual = v10 && state.activation == Activation.MANUAL &&
        state.mode == PolicyMode.LOCAL
    val hostedManual = v10 && state.activation == Activation.MANUAL && hosted
    // Product rule: Study mode and parent temporary unlock are fixed entries on every legacy
    // lock screen, whatever engine or activation is running. The V10 desktop replaces the study
    // card with the configured app icons; an empty list simply leaves no study tiles.
    val studyEntryVisible = true
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
    val quizFeatureVisible = quizState.enabled && quizState.entry != "blocked" &&
        state.quizGateEnabled
    val traceQuizVisible = quizFeatureVisible
    val traceQuizTile = quizState.lockEntryVisible
    val traceQuizAvailable = (traceManualAvailable || tracePending) && traceQuizVisible
    // V44: the three child routes are independent and the runtime planner is their only decision
    // layer. 2026-11-21: the hosted cloud rule guard projects the same routes (switch, daily
    // counts, X/Y, watch duration), so it renders them here instead of the old quiz-only
    // fallback. Legacy CLOUD redemption keeps that fallback.
    val hostedRuleGuard = state.mode == PolicyMode.CLOUD &&
        state.source == PolicySource.HOSTED &&
        state.activation == Activation.MANUAL
    val localUnlockRoutes = hostedRuleGuard || (
        state.mode == PolicyMode.LOCAL &&
            state.source == PolicySource.LOCAL &&
            state.activation == Activation.MANUAL
        )
    val routePlan = state.unlockRoutePlan?.takeIf { localUnlockRoutes }
    // The hosted fruit card reads the orchard's live account, not a LOCAL ledger. Pull it when
    // the lock screen appears in hosted mode and whenever the active child changes; a failure
    // keeps the previous cache instead of blanking the card.
    LaunchedEffect(hostedRuleGuard, state.cloudActiveMemberId, state.policyEpoch) {
        if (hostedRuleGuard) PolicyModeCoordinator.refreshHostedFruitFacts()
    }
    val studyEarnFeature = localUnlockRoutes && state.studyEarnEnabled &&
        state.studyEarnPackages.isNotEmpty()
    // Direct start is the child's own route: no PIN and no quiz. In hosted mode the cloud
    // projection (direct switch + watch duration + daily count) authorizes it.
    val directStartFeature = localUnlockRoutes && state.directStartEnabled
    val studyRouteAvailable = routePlan?.studyReward?.available == true
    val directRouteAvailable = routePlan?.direct?.available == true
    val quizRouteAvailable = if (routePlan != null) {
        routePlan.quiz.available && quizFeatureVisible
    } else {
        traceQuizAvailable
    }
    LaunchedEffect(
        state.sessionState,
        state.guardState,
        state.activation,
        state.lockReason,
        manualWindowOpen,
        traceManualAvailable,
        tracePending,
        traceQuizVisible,
        traceQuizTile,
        traceQuizAvailable,
        routePlan?.quiz?.visible,
        routePlan?.quiz?.available,
        routePlan?.studyReward?.visible,
        routePlan?.studyReward?.available,
        routePlan?.direct?.visible,
        routePlan?.direct?.available,
        routePlan?.quotaRemainingSeconds,
        state.studyModeAvailable,
        state.userName.isNotBlank(),
        state.userIconUrl.isNotBlank(),
        state.mode,
        state.source,
        state.allowedPackages.size,
        state.unlimitedPackages.size,
        state.studyPackages.size,
        state.studyEarnPackages.size,
        state.studyEarnEnabled,
        state.directStartEnabled,
    ) {
        PolicyFlowTrace.event(
            "lock.entries",
            "study" to studyEntryVisible,
            "studyAvailable" to state.studyModeAvailable,
            "quizVisible" to traceQuizVisible,
            "quizTile" to traceQuizTile,
            "quizAvailable" to traceQuizAvailable,
            "manualAvailable" to traceManualAvailable,
            "pending" to tracePending,
            "windowOpen" to manualWindowOpen,
            "resting" to restingNow,
            "session" to state.sessionState,
            "guard" to state.guardState,
            "lock" to state.lockReason,
            "mode" to state.mode,
            "source" to state.source,
            "allowed" to state.allowedPackages.size,
            "unlimited" to state.unlimitedPackages.size,
            "studyApps" to state.studyPackages.size,
            "earnApps" to state.studyEarnPackages.size,
            "studyEarnOn" to state.studyEarnEnabled,
            "directOn" to state.directStartEnabled,
            "quizRoute" to (routePlan?.quiz?.visible ?: false),
            "quizRouteAvailable" to (routePlan?.quiz?.available ?: traceQuizAvailable),
            "studyRoute" to (routePlan?.studyReward?.visible ?: false),
            "studyRouteAvailable" to (routePlan?.studyReward?.available ?: false),
            "directRoute" to (routePlan?.direct?.visible ?: false),
            "directRouteAvailable" to (routePlan?.direct?.available ?: false),
            "routeQuotaLeft" to (routePlan?.quotaRemainingSeconds ?: -1L),
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
            guardPanelOpen = false
            unlockChoiceMode = false
            fruitMode = false
            fruitFromChoice = false
            fruitSubmitting = false
            fruitMessageText = null
            unlockChoiceTarget = null
            unlockChoiceMessage = null
        }
        if (state.guardState == GuardStatus.PARENT_OVERRIDE) {
            // A parent can take over from the web while the child is inside a quiz, recovery
            // or prompt subflow. Those are full-screen overlays above this screen, so the
            // guard change alone does not dismiss them.
            quizMode = false
            quizTarget = null
            QuizLaunchTarget.clear()
            recoveryMode = false
            promptMessage = null
        }
    }
    LaunchedEffect(state.sessionState) {
        // A phone grant can arrive while the local picker or PIN pad is open. The grant owns
        // the single start gate, so close the local flow instead of leaving a confirm that
        // can only come back BUSY.
        if (state.sessionState == SessionState.PENDING &&
            (durationMode || unlockChoiceMode || fruitMode ||
                (pinMode && pinPurpose == PinPurpose.MANUAL_START))
        ) {
            pinMode = false
            durationMode = false
            pendingMinutes = null
            entered = ""
            error = null
            actionMessage = null
            unlockChoiceMode = false
            fruitMode = false
            fruitFromChoice = false
            fruitSubmitting = false
            fruitMessageText = null
            unlockChoiceTarget = null
            unlockChoiceMessage = null
        }
    }
    LaunchedEffect(
        pinMode,
        durationMode,
        quizMode,
        recoveryMode,
        unlockChoiceMode,
        fruitMode,
        state.lockReason,
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
                // The quiz and recovery screens own focus; the choice overlay requests its own row.
                quizMode || recoveryMode || unlockChoiceMode || fruitMode ||
                    promptMessage != null -> null
                // The lock screen has no default selection. A granted redemption on the legacy
                // sheet hands focus to the start button; the V10 desktop re-anchors below,
                // where it can see whether the start tile is the dock's first enabled tile.
                pendingStartVisible && !desktopMenu -> startFocus
                // The desktop's own effect below owns this request, because when START is the
                // row's first enabled tile it is attached to [firstActionFocus] instead.
                pendingStartVisible -> null
                else -> idleFocus
            }
            if (target != null) {
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
            focusedStudyPackage = null
            focusedGatewayPackage = null
            focusedUnlimitedPackage = null
            focusedAction = action
        } else if (focusedAction == action) {
            focusedAction = null
        }
        PolicyFlowTrace.event("focus.action", "action" to action, "focused" to focused)
    }

    fun studyAppFocused(app: AppEntry) {
        focusedAction = null
        focusedGatewayPackage = null
        focusedUnlimitedPackage = null
        focusedStudyPackage = app.packageName
        PolicyFlowTrace.event("focus.study.app", "package" to app.packageName)
    }

    fun gatewayAppFocused(app: AppEntry) {
        focusedAction = null
        focusedStudyPackage = null
        focusedUnlimitedPackage = null
        focusedGatewayPackage = app.packageName
        PolicyFlowTrace.event("focus.quiz.app", "package" to app.packageName)
    }

    fun unlimitedAppFocused(app: AppEntry) {
        focusedAction = null
        focusedStudyPackage = null
        focusedGatewayPackage = null
        focusedUnlimitedPackage = app.packageName
        PolicyFlowTrace.event("focus.unlimited.app", "package" to app.packageName)
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

    fun enterStudy() {
        actionMessage = null
        // An empty study list stays visible on the legacy sheet for the explanation, but must not
        // open an empty launcher: the footer already tells the child to ask a parent to add apps.
        if (!state.studyModeAvailable || state.studyPackages.isEmpty()) return
        if (!PolicyModeCoordinator.enterStudyMode()) {
            actionMessage = R.string.policy_action_rejected
        }
    }

    /**
     * Every study app is timed now: the tile starts a study round under the study window/quota
     * before [LockLaunchPermit] lets this one package through the lock. A reward-enabled app
     * first shows the focus intro, because its round will also end in an entertainment session;
     * a reward that cannot be earned right now does not block studying, it just does not end in
     * one. 2026-11-21: the hosted rule guard runs the same timed study round; only legacy CLOUD
     * redemption keeps its historical untimed launch.
     */
    fun openStudyApp(app: AppEntry, bounds: Rect?) {
        actionMessage = null
        if (!state.studyModeAvailable || app.packageName !in state.studyPackages) return
        if (!PolicyModeCoordinator.isLockScreen()) return
        val timedStudy = (state.mode == PolicyMode.LOCAL && state.source == PolicySource.LOCAL) ||
            (state.mode == PolicyMode.CLOUD && state.source == PolicySource.HOSTED)
        if (timedStudy) {
            val rewards = app.packageName in state.studyEarnPackages
            if (rewards && studyRouteAvailable) {
                // Accidental taps must not start a timer or open the app: the focus intro asks
                // for an explicit 开始专注 first. The route from an entertainment app keeps its
                // direct start; only this tile entry gains the confirmation step.
                onStudyEarnIntro(app)
                return
            }
            when (
                val result = PolicyModeCoordinator.startStudyRound(
                    app.packageName,
                    targetPackage = null,
                )
            ) {
                SessionStartResult.Started -> Unit
                is SessionStartResult.Rejected -> {
                    actionMessage = studyEarnRejectMessage(result.reason)
                    return
                }
                SessionStartResult.Pending -> return
            }
        }
        LockLaunchPermit.grant(app.packageName)
        PolicyFlowTrace.event(
            "launch.study",
            "package" to app.packageName,
            "timed" to timedStudy,
            "session" to state.sessionState,
        )
        onLaunch(app, bounds)
    }

    /**
     * An unlimited app is the one entry with no timer and no quota. Its one-shot permit is the
     * whole admission: no round is created, and leaving the app returns to the same lock screen.
     */
    fun openUnlimitedApp(app: AppEntry, bounds: Rect?) {
        actionMessage = null
        if (app.packageName !in state.unlimitedPackages) return
        if (!PolicyModeCoordinator.isLockScreen()) return
        LockLaunchPermit.grant(app.packageName)
        PolicyFlowTrace.event(
            "launch.unlimited",
            "package" to app.packageName,
            "session" to state.sessionState,
        )
        onLaunch(app, bounds)
    }

    /**
     * One direct-start attempt. The session starts before the lock screen leaves, and the
     * one-shot confirmation lets [PolicyHomeGate] open the target once the guard is ACTIVE.
     * A refusal stays on the lock screen and shows the runtime's reason.
     */
    fun startDirect(
        target: AppEntry?,
        onRejected: (Int) -> Unit = { actionMessage = it },
    ): SessionStartResult {
        val result = PolicyModeCoordinator.startLocalDirectSession()
        when (result) {
            SessionStartResult.Started -> {
                QuizLaunchTarget.confirm(target?.packageName)
                PolicyFlowTrace.event(
                    "session.direct.launch",
                    "package" to (target?.packageName ?: "inputs"),
                    "session" to state.sessionState,
                )
            }
            is SessionStartResult.Rejected -> {
                // Drop the armed input-page target; a refused start must not leave a pending
                // launch for a later session.
                if (target == null) QuizLaunchTarget.clear()
                onRejected(rejectMessage(result.reason))
            }
            SessionStartResult.Pending -> {
                if (target == null) QuizLaunchTarget.clear()
            }
        }
        return result
    }

    fun openUnlockChoice(target: AppEntry?, traceEvent: String) {
        unlockChoiceTarget = target
        unlockChoiceMessage = null
        // The hosted fruit row is part of this card. If the lock-screen pull raced the guard
        // attach or failed, retry now so a later recomposition can offer the row instead of
        // leaving the wheel unreachable for the whole stay on the card.
        if (hostedRuleGuard) PolicyModeCoordinator.refreshHostedFruitFacts()
        // Stop the rotation in the same input frame, before the overlay's first composition.
        onChoiceOpenChanged(true)
        unlockChoiceMode = true
        PolicyFlowTrace.event(
            traceEvent,
            "package" to (target?.packageName ?: "inputs"),
            "session" to state.sessionState,
        )
    }

    /**
     * V58: the fruit route always stops at its wheel, even when it is the only available route.
     * Starting a session here without the child choosing a whole-fruit count would silently
     * charge for a duration they never saw.
     */
    fun openFruitExchange(target: AppEntry?, fromChoice: Boolean) {
        unlockChoiceTarget = target
        unlockChoiceMessage = null
        fruitSubmitting = false
        fruitMessageText = null
        // The wheel's ceilings are the orchard's, and the lock screen may have been idle for
        // hours (daily reset, parent edit). Pull one fresh context so the first wheel value is
        // not planned against yesterday's balance; a failure keeps the previous cache.
        if (hostedRuleGuard) PolicyModeCoordinator.refreshHostedFruitFacts()
        // The wheel replaces the four-route card but keeps the same live desktop underneath.
        onChoiceOpenChanged(true)
        unlockChoiceMode = false
        fruitFromChoice = fromChoice
        fruitMode = true
        PolicyFlowTrace.event(
            "unlock.choice.fruit",
            "package" to (target?.packageName ?: "inputs"),
            "fromChoice" to fromChoice,
            "session" to state.sessionState,
        )
    }

    fun closeFruitExchange(returnToChoice: Boolean) {
        fruitMode = false
        fruitSubmitting = false
        fruitMessageText = null
        unlockChoiceMessage = null
        if (returnToChoice && fruitFromChoice) {
            fruitFromChoice = false
            unlockChoiceMode = true
        } else {
            fruitFromChoice = false
            unlockChoiceTarget = null
        }
    }

    /**
     * One fruit exchange attempt. The wheel value is deliberately not clamped here: the runtime
     * re-reads the account and re-runs the planner, and a changed balance must reject rather than
     * silently charge a different number of fruits.
     */
    fun launchFruitExchange(fruits: Int) {
        val target = unlockChoiceTarget
        if (hostedRuleGuard) {
            // The orchard debits the cloud account and starts this TV's timer itself. No LOCAL
            // ledger call: the two accounts never mix.
            if (fruitSubmitting) return
            fruitSubmitting = true
            unlockChoiceMessage = null
            fruitMessageText = null
            PolicyModeCoordinator.startHostedFruitSession(fruits) { result ->
                fruitSubmitting = false
                if (result.started) {
                    if (target == null) {
                        QuizLaunchTarget.armInputs()
                    } else {
                        QuizLaunchTarget.confirm(target.packageName)
                    }
                    PolicyFlowTrace.event(
                        "session.fruit.launch",
                        "fruits" to fruits,
                        "package" to (target?.packageName ?: "inputs"),
                        "cloud" to true,
                    )
                    fruitMode = false
                    fruitFromChoice = false
                    unlockChoiceMode = false
                    unlockChoiceTarget = null
                    unlockChoiceMessage = null
                    fruitMessageText = null
                } else {
                    if (target == null) QuizLaunchTarget.clear()
                    // The orchard's text says exactly why (rest, check-in, quota…). Only a
                    // transport failure has no business message and falls back to the generic.
                    fruitMessageText = result.message
                    if (result.message == null) {
                        unlockChoiceMessage = R.string.policy_fruit_changed
                    }
                }
            }
            return
        }
        if (target == null) QuizLaunchTarget.armInputs() else QuizLaunchTarget.clear()
        when (val result = PolicyModeCoordinator.startLocalFruitSession(
            fruits,
            target?.label ?: "TV",
        )) {
            SessionStartResult.Started -> {
                if (target != null) QuizLaunchTarget.confirm(target.packageName)
                PolicyFlowTrace.event(
                    "session.fruit.launch",
                    "fruits" to fruits,
                    "package" to (target?.packageName ?: "inputs"),
                )
                fruitMode = false
                fruitFromChoice = false
                unlockChoiceMode = false
                unlockChoiceTarget = null
                unlockChoiceMessage = null
            }
            is SessionStartResult.Rejected -> {
                if (target == null) QuizLaunchTarget.clear()
                unlockChoiceMessage = when (result.reason) {
                    RejectReason.RESTING -> R.string.policy_resting_title
                    RejectReason.OUTSIDE_WINDOW -> R.string.policy_outside_window
                    RejectReason.BUSY -> R.string.policy_route_busy
                    else -> R.string.policy_fruit_changed
                }
            }
            SessionStartResult.Pending -> Unit
        }
    }

    /**
     * The entertainment-icon route into a study round. The session is established here; the
     * overlay has already asked the child which study app to use.
     */
    fun launchStudyEarn(studyApp: AppEntry, target: AppEntry?) {
        when (
            val result = PolicyModeCoordinator.startStudyRound(
                studyApp.packageName,
                target?.packageName,
            )
        ) {
            SessionStartResult.Started -> {
                unlockChoiceMode = false
                unlockChoiceTarget = null
                unlockChoiceMessage = null
                LockLaunchPermit.grant(studyApp.packageName)
                PolicyFlowTrace.event(
                    "launch.study.earn",
                    "package" to studyApp.packageName,
                    "target" to (target?.packageName ?: "inputs"),
                )
                onLaunch(studyApp, null)
            }
            is SessionStartResult.Rejected ->
                unlockChoiceMessage = studyEarnRejectMessage(result.reason)
            SessionStartResult.Pending -> Unit
        }
    }

    /**
     * An entertainment icon in the dock is an unlock entry, not a launch shortcut. V44 keeps the
     * three routes independent: exactly one visible and startable route may open directly, and
     * anything else stops at the card so the child can read every reason.
     */
    fun openGatewayApp(app: AppEntry) {
        actionMessage = null
        if (app.packageName !in state.allowedPackages) return
        // A new round owns the launch; drop a stale confirmation whose launch never happened.
        QuizLaunchTarget.clear()
        focusedAction = null
        focusedStudyPackage = null
        focusedGatewayPackage = null
        focusedUnlimitedPackage = null
        val plan = routePlan
        if (plan != null) {
            val visible = plan.decisions.filter { it.visible }
            if (visible.isEmpty()) {
                openUnlockChoice(app, "unlock.choice.app")
                return
            }
            val sole = visible.singleOrNull()
            if (sole != null && sole.available) {
                when (sole.route) {
                    UnlockRoute.QUIZ -> if (quizFeatureVisible) {
                        PolicyFlowTrace.event(
                            "quiz.entry.app",
                            "package" to app.packageName,
                            "session" to state.sessionState,
                        )
                        quizTarget = app
                        quizMode = true
                        return
                    }
                    UnlockRoute.DIRECT -> {
                        startDirect(app)
                        return
                    }
                    UnlockRoute.FRUIT -> {
                        openFruitExchange(app, fromChoice = false)
                        return
                    }
                    // A study route always names its app in the overlay, even when only one
                    // study app is installed.
                    UnlockRoute.STUDY_REWARD -> Unit
                }
            }
            openUnlockChoice(app, "unlock.choice.app")
            return
        }
        // Hosted rules keep the historical quiz-only entry.
        if (!quizFeatureVisible) {
            // No route is active. The tiles are normally hidden in this state; this is the
            // fail-closed backstop for a stale focus or an external caller.
            actionMessage = R.string.policy_no_unlock_route
            return
        }
        PolicyFlowTrace.event(
            "quiz.entry.app",
            "package" to app.packageName,
            "session" to state.sessionState,
        )
        quizTarget = app
        quizMode = true
    }

    /**
     * The lock screen's top-right 信号源 button is the same kind of TV-use entry as an
     * entertainment icon. Quiz and direct may target an input; study cannot, so a study-only
     * configuration shows its greyed card instead of opening the input list.
     */
    fun openGatewayInputs() {
        actionMessage = null
        // A new round owns the launch; drop a stale confirmation whose launch never happened.
        QuizLaunchTarget.clear()
        focusedAction = null
        focusedStudyPackage = null
        focusedGatewayPackage = null
        focusedUnlimitedPackage = null
        val plan = routePlan
        if (plan != null) {
            val visible = plan.decisions.filter { it.visible }
            if (visible.isEmpty()) {
                openUnlockChoice(null, "unlock.choice.inputs")
                return
            }
            val sole = visible.singleOrNull()
            if (sole != null && sole.available) {
                when (sole.route) {
                    UnlockRoute.QUIZ -> if (quizFeatureVisible) {
                        QuizLaunchTarget.armInputs()
                        PolicyFlowTrace.event(
                            "quiz.entry.inputs",
                            "session" to state.sessionState,
                        )
                        quizTarget = null
                        quizMode = true
                        return
                    }
                    UnlockRoute.DIRECT -> {
                        QuizLaunchTarget.armInputs()
                        startDirect(null)
                        return
                    }
                    UnlockRoute.FRUIT -> {
                        QuizLaunchTarget.armInputs()
                        openFruitExchange(null, fromChoice = false)
                        return
                    }
                    UnlockRoute.STUDY_REWARD -> Unit
                }
            }
            openUnlockChoice(null, "unlock.choice.inputs")
            return
        }
        if (!quizFeatureVisible) {
            QuizLaunchTarget.clear()
            actionMessage = R.string.policy_no_unlock_route
            return
        }
        QuizLaunchTarget.armInputs()
        PolicyFlowTrace.event("quiz.entry.inputs", "session" to state.sessionState)
        quizTarget = null
        quizMode = true
    }

    // A remote switch must not inject BACK into a flow that BACK would dismiss; PolicyPanelRefresh
    // wakes the panel with BACK only when the lock screen's normal sheet is showing.
    SideEffect {
        PolicyPanelRefresh.setOverlayOpen(
            recoveryMode || quizMode || unlockChoiceMode || fruitMode || promptMessage != null ||
                durationMode || pinMode || guardPanelOpen,
        )
    }
    DisposableEffect(Unit) {
        onDispose { PolicyPanelRefresh.setOverlayOpen(false) }
    }
    // A dock tile has no blur callback. Every flow that covers or replaces the dock clears the
    // selection so the hint cannot name an action the child can no longer press.
    LaunchedEffect(
        pinMode,
        durationMode,
        quizMode,
        recoveryMode,
        unlockChoiceMode,
        fruitMode,
        promptMessage,
    ) {
        if (pinMode || durationMode || quizMode || recoveryMode || unlockChoiceMode ||
            fruitMode || promptMessage != null
        ) {
            focusedAction = null
            focusedStudyPackage = null
            focusedGatewayPackage = null
            focusedUnlimitedPackage = null
            guardPanelOpen = false
        }
    }
    if (recoveryMode) {
        PolicyRecoveryScreen(
            onCancel = { recoveryMode = false },
            onDone = { recoveryMode = false },
        )
        return
    }
    if (quizMode) {
        PolicyQuizScreen(
            target = quizTarget,
            onClose = {
                quizMode = false
                quizTarget = null
            },
        )
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
            // The overlay installs its own handler later, but keep the desktop fallback closed:
            // no state can leave BACK doing nothing while the choice owns input.
            unlockChoiceMode -> {
                unlockChoiceMode = false
                unlockChoiceTarget = null
                unlockChoiceMessage = null
            }
            fruitMode -> closeFruitExchange(returnToChoice = fruitFromChoice)
            guardPanelOpen -> guardPanelOpen = false
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
            state.lockReason == LockReason.MANUAL_WAITING ->
                if (localManual || hostedManual) {
                    R.string.policy_manual_waiting_title
                } else {
                    R.string.policy_hosted_manual_title
                }
            state.lockReason == LockReason.NO_TIMED_APPS -> R.string.policy_no_timed_apps_title
            state.lockReason == LockReason.SETUP_REQUIRED -> R.string.policy_setup_required_title
            else -> R.string.policy_locked_title
        },
    )
    val body = when {
        state.lockReason == LockReason.TIME_UP -> stringResource(R.string.policy_time_up_body)
        state.lockReason == LockReason.NO_TIMED_APPS -> stringResource(R.string.policy_no_timed_apps_body)
        state.lockReason == LockReason.MANUAL_WAITING && (hostedManual || localManual) ->
            stringResource(R.string.policy_manual_waiting_body)
        else -> ""
    }
    // The source badge is the only place the lock screen names local vs remote. It carries
    // "本地=未绑定 / 远程=绑定" by product definition, so the footer never repeats binding.
    val identityModeLabel = if (v10) {
        stringResource(
            if (hosted) R.string.policy_mode_remote_manual else R.string.policy_mode_local_manual,
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

    // The child-mode wallpaper chosen in the parent center. Null means Aerial's default wallpaper.
    // It keeps playing across the menu/PIN swap: one player behind both, its first frame
    // fading in over the poster. The state lives above the lock screen, so the same rotation
    // continues on the child's Home and a parent-center edit refreshes this screen in place.
    val lockSource = childWallpaper?.source
    val lockSound = videoSoundEnabled
    val lockFilmCover = remember { Animatable(0f) }
    // Same rule as the Home film above: a film's first frame fades in by itself; a picture
    // replaces a film only when it is ready, so the last film frame stays until then.
    val lockSourceKind = lockSource?.kind
    val lockStillReady = childWallpaper == null || childWallpaper.imageReady
    LaunchedEffect(lockSourceKind, lockStillReady) {
        if (lockSourceKind != PolicyMediaKind.VIDEO && lockStillReady) {
            lockFilmCover.animateTo(
                targetValue = 0f,
                animationSpec = tween(FILM_TO_STILL_FADE_MS, easing = Motion.EaseOut),
            )
        }
    }
    // The panel takes focus itself when it opens. Closing it (BACK, a tap outside, or the exit
    // row starting the PIN) gives focus back to the badge, unless another sheet now owns focus.
    // Opening on the first composition is not a return, so the idle sheet keeps it.
    LaunchedEffect(guardPanelOpen) {
        if (guardPanelOpen) {
            guardWasOpen = true
        } else if (guardWasOpen) {
            guardWasOpen = false
            withFrameNanos { }
            if (desktopMenu && !pinMode && !durationMode && !quizMode &&
                !recoveryMode && !unlockChoiceMode && !fruitMode && promptMessage == null
            ) {
                runCatching { guardFocus.requestFocus() }
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            // A window-wide opaque fill would hide the policy film's SurfaceView, which is
            // behind the window; a picture keeps the opaque root and is drawn by the backdrop.
            .background(
                // The film's SurfaceView is behind the window. Stay transparent for the whole
                // child wallpaper, not only while the active item is a video: when a film gives
                // way to a picture, the picture fades in over the film's last frame. The
                // backdrop's own opaque base keeps the surface hidden everywhere else.
                if (v10 && (lockSource?.kind == PolicyMediaKind.VIDEO || childWallpaper != null)) {
                    Color.Transparent
                } else {
                    Color.Black
                },
            )
            // Draw one more frame when a remote switch needed the panel woken; the frame itself
            // is unchanged apart from the badge the state already switched.
            .drawWithContent {
                if (panelRefresh.value < 0) return@drawWithContent
                drawContent()
            }
            .focusRequester(idleFocus)
            // The choice overlay traps focus on its own rows; the desktop root must not keep a
            // focusable node that focus search can fall back to. The fruit wheel likewise owns
            // every D-pad key while it is open.
            .focusable(
                enabled = (!desktopMenu || !dockFocusActive) &&
                    !unlockChoiceMode &&
                    !fruitMode &&
                    !subjectSheetOpen,
            )
            .onPreviewKeyEvent { event ->
                // The sheet itself holds focus while idle so no action is pre-selected.
                // The first direction press enters the list; after that the column's own
                // navigation takes over. The choice overlay and the fruit wheel consume their
                // own D-pad keys.
                if (!pinMode && !durationMode && !guardPanelOpen && !dockFocusActive &&
                    !unlockChoiceMode && !fruitMode && !subjectSheetOpen &&
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
        if (v10 && childWallpaper != null) {
            // Composed for a picture too: the SurfaceView keeps the last film frame while the
            // next picture fades in. PolicyLockFilm ignores a null or still source.
            PolicyLockFilm(
                source = lockSource,
                cover = lockFilmCover,
                soundEnabled = lockSound && lockSource?.hasAudio == true,
                loop = childWallpaper.playlistSize <= 1,
                onEnded = childWallpaper.onVideoEnded,
                onError = childWallpaper.onVideoError,
            )
        }
        if (desktopMenu) {
            val resting = state.lockReason == LockReason.RESTING &&
                state.restRemainingSeconds > 0
            val quizLabel = when {
                quizState.sessionState == "running" -> stringResource(
                    R.string.policy_quiz_resume,
                    ((quizState.session?.run?.index ?: 0) + 1)
                        .coerceAtMost(quizState.questionCount.coerceAtLeast(1)),
                    quizState.questionCount,
                )
                else -> stringResource(R.string.policy_quiz_entry)
            }
            // V44: the runtime planner decides each route, so a tile is enabled only when the
            // route can establish a session right now.
            val quizAvailable = quizRouteAvailable
            val directAvailable = directRouteAvailable
            // Display-only switch: when on, the dedicated quiz tile leads the dock. It never
            // falls back into the list when off, and it never affects the app triggers.
            val showQuizTile = quizState.lockEntryVisible && quizFeatureVisible
            val pendingStart = state.sessionState == SessionState.PENDING && !resting
            val startAction = if (pendingStart) LockAction.CONFIRM_START else LockAction.MANUAL
            val slots = mutableListOf<LockDockSlot>()
            // The parent's study list is drawn as real app tiles after the fixed actions. The
            // catalog holds every installed app, so a study app hidden from Home or placed in a
            // folder still gets its icon here.
            val studyApps = remember(
                state.studyPackages,
                vm.loaded,
                vm.apps,
                vm.hiddenApps,
                vm.folderApps,
            ) { vm.studyApps(state.studyPackages) }
            // Unlimited apps use the same catalog as study apps: the parent's list is the truth,
            // so an app hidden from Home still gets its tile here. They follow the study apps and
            // launch directly, with no round and no quota.
            val unlimitedApps = remember(
                state.unlimitedPackages,
                vm.loaded,
                vm.apps,
                vm.hiddenApps,
                vm.folderApps,
            ) { vm.studyApps(state.unlimitedPackages) }
            // The configured entertainment apps are the quiz entry now. Only Home's visible
            // icons are used, so every trigger can also be opened after the session starts.
            // Study and unlimited apps are never quiz triggers even if a stale rule left a
            // package in the entertainment list.
            val timedApps = remember(
                state.allowedPackages,
                state.studyPackages,
                state.unlimitedPackages,
                vm.loaded,
                vm.apps,
                vm.folderApps,
            ) {
                vm.timedApps(state.allowedPackages)
                    .filterNot {
                        it.packageName in state.studyPackages ||
                            it.packageName in state.unlimitedPackages
                    }
            }
            // What the child actually gets as tiles, in each category. Distinct from
            // lock.entries (which only carries counts): when a configured app does not show up,
            // this line tells whether the rule scope or the installed-app filter dropped it.
            val tilesSignature = remember(
                state.allowedPackages,
                state.studyPackages,
                state.unlimitedPackages,
                timedApps,
                studyApps,
                unlimitedApps,
            ) {
                "a=" + state.allowedPackages.sorted().joinToString(",") +
                    "|s=" + state.studyPackages.sorted().joinToString(",") +
                    "|u=" + state.unlimitedPackages.sorted().joinToString(",") +
                    "|t=" + timedApps.joinToString(",") { it.packageName } +
                    "|sT=" + studyApps.joinToString(",") { it.packageName } +
                    "|uT=" + unlimitedApps.joinToString(",") { it.packageName }
            }
            LaunchedEffect(tilesSignature) {
                PolicyFlowTrace.event(
                    "lock.tiles",
                    "allowed" to state.allowedPackages.sorted().joinToString(","),
                    "study" to state.studyPackages.sorted().joinToString(","),
                    "unlimited" to state.unlimitedPackages.sorted().joinToString(","),
                    "timedTiles" to timedApps.joinToString(",") { it.packageName },
                    "studyTiles" to studyApps.joinToString(",") { it.packageName },
                    "unlimitedTiles" to unlimitedApps.joinToString(",") { it.packageName },
                    "loaded" to vm.loaded,
                )
            }
            // An entertainment icon is an unlock entry, not a launch shortcut. Local rules keep
            // the icons reachable even with every route off, so pressing one opens the empty
            // state instead of making the configuration look broken; the overlay otherwise
            // carries the reason each route cannot start.
            val hasUnlockMethod = routePlan != null || quizFeatureVisible ||
                studyEarnFeature || directStartFeature
            val gatewayApps = if (hasUnlockMethod && !state.noTimedApps) {
                timedApps
            } else {
                emptyList()
            }
            // Keep the tiles focusable while any route is visible, so a greyed card can still be
            // opened to read its reason; all-off opens the same overlay's empty state.
            val gatewayEnabled = if (routePlan != null) {
                true
            } else {
                quizAvailable
            }
            if (showQuizTile) {
                slots += LockDockSlot(
                    id = LockDockId.QUIZ,
                    art = if (quizAvailable) {
                        R.drawable.policy_lock_dock_quiz
                    } else {
                        R.drawable.policy_lock_dock_quiz_off
                    },
                    label = quizLabel,
                    hint = lockFooterText(LockAction.QUIZ, state, quizState),
                    enabled = quizAvailable,
                )
            }
            // Manual mode has no one-off timer card. A parent grant (quiz reward or the phone
            // control page) still shows a pending START card, because that session has already
            // been approved and only needs the child's confirmation.
            if (pendingStart && !state.noTimedApps) {
                slots += LockDockSlot(
                    id = LockDockId.START,
                    art = R.drawable.policy_lock_dock_start,
                    label = stringResource(R.string.policy_start_timer),
                    hint = lockFooterText(LockAction.CONFIRM_START, state, quizState),
                    enabled = true,
                )
            }
            // Parent temporary unlock lives behind the top-right badge now, not in the dock.
            // PARENT_UNLOCK must not map to a dock id: the slot is gone, and the stale-slot
            // effect below would then clear the badge's focus on the frame it arrives.
            val focusedDock = when (focusedAction) {
                LockAction.QUIZ -> LockDockId.QUIZ
                LockAction.MANUAL, LockAction.CONFIRM_START -> LockDockId.START
                // The legacy study action has no card on the V10 desktop; the configured apps
                // take its place. Parent temporary unlock lives behind the top-right badge.
                LockAction.STUDY, LockAction.PARENT_UNLOCK -> null
                null -> null
            }
            // The row's identity covers fixed actions and app icons alike, so focus is cleared
            // when an unavailable action disappears or the parent removes the focused app.
            val dockSignature = slots.joinToString { "${it.id}:${it.enabled}" } +
                "|q:" + gatewayEnabled +
                "|" + gatewayApps.joinToString { it.packageName } +
                "|" + studyApps.joinToString { it.packageName } +
                "|" + unlimitedApps.joinToString { it.packageName }
            // A slot that becomes unavailable loses focus; AppTile disables its own focusable
            // node, so the sheet takes focus back and the next key re-enters the dock.
            LaunchedEffect(
                focusedDock,
                focusedGatewayPackage,
                focusedStudyPackage,
                focusedUnlimitedPackage,
                dockSignature,
            ) {
                val focusedSlot = slots.firstOrNull { it.id == focusedDock }
                val actionLost = focusedDock != null &&
                    (focusedSlot == null || !focusedSlot.enabled)
                val gatewayLost = focusedGatewayPackage != null &&
                    (!gatewayEnabled || gatewayApps.none {
                        it.packageName == focusedGatewayPackage
                    })
                val appLost = focusedStudyPackage != null &&
                    studyApps.none { it.packageName == focusedStudyPackage }
                val unlimitedLost = focusedUnlimitedPackage != null &&
                    unlimitedApps.none { it.packageName == focusedUnlimitedPackage }
                if (actionLost || gatewayLost || appLost || unlimitedLost) {
                    focusedAction = null
                    focusedGatewayPackage = null
                    focusedStudyPackage = null
                    focusedUnlimitedPackage = null
                    withFrameNanos { }
                    runCatching { idleFocus.requestFocus() }
                }
            }
            // A pending start owns focus on the V10 desktop too. AppTile is already attached to
            // [firstActionFocus] when START is the first enabled tile; ask the dedicated
            // requester otherwise.
            LaunchedEffect(pendingStartVisible, dockSignature) {
                if (!pendingStartVisible) return@LaunchedEffect
                withFrameNanos { }
                val startIsFirst = slots.firstOrNull { it.enabled }?.id == LockDockId.START
                runCatching {
                    (if (startIsFirst) firstActionFocus else startFocus).requestFocus()
                }
            }
            // One set of numbers places the dock, the grid below it and its plate. They come from
            // Aerial's one-row shelf, so the same peek, plate and column settings move both the
            // desktop and this screen.
            val dockTileCount = slots.size + gatewayApps.size + studyApps.size +
                unlimitedApps.size
            val dockMetrics = lockDockMetrics(AerialSettings.homeColumns, dockTileCount)
            val dockState = rememberLockDockState()
            val dockCollapsePx = with(LocalDensity.current) { dockMetrics.collapse.toPx() }
            // Down in the rows the clock and its info band have scrolled away; the pill carries
            // the time then, as Home's does.
            val pillFocused = focusedAction == LockAction.PARENT_UNLOCK
            // The face sits above the grid: focusing it returns the grid to the shelf and takes
            // the time out of the pill, the way focusing the pill does.
            var subjectFocused by remember { mutableStateOf(false) }
            // The member sheet is drawn here, above the dock. The desktop's focusable chrome is
            // disabled while it is up so D-pad focus stays in the sheet.
            val family by FamilyHub.state.collectAsState()
            val gridFocused by remember(dockMetrics, pillFocused, subjectFocused) {
                derivedStateOf {
                    if (pillFocused || subjectFocused) return@derivedStateOf false
                    val index = dockState.focusedIndex ?: return@derivedStateOf false
                    index in 0 until dockMetrics.tileCount && index >= dockMetrics.columns
                }
            }
            LockDesktopBackdrop(
                dockSlots = dockTileCount,
                cover = { lockFilmCover.value },
                scroll = { dockState.scroll.value },
                collapse = dockCollapsePx,
                policyDefault = true,
                policyImage = childWallpaper?.image,
                policyFrosted = childWallpaper?.frosted,
            )
            LockDesktopIdentity(
                state = state,
                vm = vm,
                hosted = hosted,
                resumeTick = resumeTick,
                minutes = minutes,
                showPending = state.sessionState == SessionState.PENDING,
                // The panel covers the pill; hiding it keeps the glass free of the glyphs
                // underneath and keeps focus from falling back to it. The member sheet keeps
                // the pill and face composed but takes them out of focus search below.
                statusInteractive = !guardPanelOpen,
                statusFocus = guardFocus,
                statusDownFocus = firstActionFocus,
                onStatusClick = { guardPanelOpen = true },
                onStatusFocused = { actionFocused(LockAction.PARENT_UNLOCK, it) },
                onSubjectFocused = { subjectFocused = it },
                showTime = gridFocused,
                scroll = { dockState.scroll.value },
                collapsePx = dockCollapsePx,
                subjectSheetOpen = subjectSheetOpen,
                onSubjectSheetOpen = { subjectSheetOpen = true },
            )
            LockDesktopDock(
                slots = slots,
                gatewayApps = gatewayApps,
                gatewayEnabled = gatewayEnabled,
                studyApps = studyApps,
                unlimitedApps = unlimitedApps,
                onFocused = { id ->
                    val action = when (id) {
                        LockDockId.QUIZ -> LockAction.QUIZ
                        LockDockId.START -> startAction
                        LockDockId.PARENT -> LockAction.PARENT_UNLOCK
                    }
                    actionFocused(action, true)
                },
                onSelect = { id ->
                    when (id) {
                        LockDockId.QUIZ -> {
                            focusedAction = null
                            quizTarget = null
                            QuizLaunchTarget.clear()
                            quizMode = true
                        }
                        LockDockId.START -> {
                            if (pendingStart) {
                                actionMessage = null
                                scope.launch {
                                    val result = withContext(Dispatchers.Default) {
                                        PolicyModeCoordinator.startSession()
                                    }
                                    if (result is SessionStartResult.Rejected) {
                                        actionMessage = rejectMessage(result.reason)
                                    }
                                }
                            } else {
                                val localPicker = !state.bound ||
                                    (hostedManual && state.pinConfigured)
                                focusedAction = null
                                if (!localPicker) {
                                    promptMessage = R.string.policy_manual_phone_time
                                } else {
                                    pendingMinutes = null
                                    actionMessage = null
                                    selectedMinutes = MANUAL_DURATION_DEFAULT_MINUTES
                                    durationMode = true
                                }
                            }
                        }
                        LockDockId.PARENT -> {
                            focusedAction = null
                            pinPurpose = PinPurpose.UNLOCK
                            entered = ""
                            error = null
                            pinMode = true
                        }
                    }
                },
                onGatewayAppFocused = ::gatewayAppFocused,
                onGatewayAppSelected = ::openGatewayApp,
                onStudyAppFocused = ::studyAppFocused,
                onStudyAppSelected = ::openStudyApp,
                onUnlimitedAppFocused = ::unlimitedAppFocused,
                onUnlimitedAppSelected = ::openUnlimitedApp,
                firstFocus = firstActionFocus,
                startFocus = startFocus,
                state = dockState,
                // Focusing the status badge or the face returns to the shelf, as Up on Home does.
                resetFocus = pillFocused || subjectFocused,
                // The fruit wheel is modal: keep every desktop tile out of focus search so a
                // missed focus request cannot hand the D-pad back to the grid behind it.
                interactive = !subjectSheetOpen && !fruitMode,
                // The tiles name themselves now. An action message owns the line under the dock,
                // so names step aside while it is showing instead of overlapping it.
                showNames = actionText == null,
                modifier = Modifier
                    .align(Alignment.TopStart),
            )
            // Only an action message remains under the dock. It starts on the same left edge as
            // the dock's first tile and the clock, just under the dock's glass, and never takes
            // part in the row's layout, so a longer message cannot move the dock.
            if (actionText != null) {
                BasicText(
                    actionText,
                    style = LockCaptionText.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        // The clock's white, like every other line on this page.
                        color = Palette.Label,
                        textAlign = TextAlign.Start,
                        shadow = LockDesktopTextShadow,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = LockDesktopStart, y = dockMetrics.hintTop)
                        .width(848.dp)
                        // It belongs to the shelf: down in the rows it fades away with the clock.
                        .graphicsLayer {
                            alpha = 1f - (dockState.scroll.value / dockCollapsePx * 1.6f)
                                .coerceIn(0f, 1f)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Drawn last, so its scrim covers the dock and its glass sits over the clock. It
            // reuses Aerial's Control Center model and rows: volume, brightness and the temporary
            // exit through the parent PIN.
            LockGuardPanel(
                visible = guardPanelOpen,
                vm = vm,
                onDismiss = { guardPanelOpen = false },
                onSourceEntry = ::openGatewayInputs,
                onExitChildMode = {
                    guardPanelOpen = false
                    focusedAction = null
                    pinPurpose = PinPurpose.UNLOCK
                    entered = ""
                    error = null
                    pinMode = true
                },
            )
            // Drawn last, over the still-live desktop: the child wallpaper film, its player and
            // the dock stay composed, so opening and closing the choice never restarts the
            // picture. The scrim only dims what is already there.
            val choiceTarget = unlockChoiceTarget
            val choicePlan = routePlan
            if (unlockChoiceMode && choicePlan != null) {
                val choiceStudyApps = remember(
                    state.studyEarnPackages,
                    vm.loaded,
                    vm.apps,
                    vm.hiddenApps,
                    vm.folderApps,
                ) { vm.studyApps(state.studyEarnPackages) }
                PolicyUnlockChoiceOverlay(
                    target = choiceTarget,
                    plan = choicePlan,
                    studyApps = choiceStudyApps,
                    quizFeatureAvailable = quizFeatureVisible,
                    studyMinutes = state.studyEarnStudyMinutes,
                    fruitBalanceHundredths = state.fruitAvailableFruitHundredths,
                    fruitRateMinutes = state.fruitExchangeRateMinutesPerFruit,
                    messageRes = unlockChoiceMessage,
                    onQuiz = {
                        unlockChoiceMode = false
                        unlockChoiceTarget = null
                        unlockChoiceMessage = null
                        // Settlement only carries a package, so the input target is armed here.
                        if (choiceTarget == null) QuizLaunchTarget.armInputs()
                        quizTarget = choiceTarget
                        quizMode = true
                    },
                    onStudy = { studyApp ->
                        launchStudyEarn(studyApp, choiceTarget)
                    },
                    onDirect = {
                        if (choiceTarget == null) QuizLaunchTarget.armInputs()
                        val result = startDirect(choiceTarget) {
                            unlockChoiceMessage = it
                        }
                        if (result == SessionStartResult.Started) {
                            unlockChoiceMode = false
                            unlockChoiceTarget = null
                            unlockChoiceMessage = null
                        }
                    },
                    onFruit = {
                        openFruitExchange(choiceTarget, fromChoice = true)
                    },
                    onBack = {
                        unlockChoiceMode = false
                        unlockChoiceTarget = null
                        unlockChoiceMessage = null
                    },
                )
            }
            // V58: the fruit wheel is the only new TV interaction panel. Its upper bound is
            // already the planner's minimum of balance, session cap, daily count and window.
            if (fruitMode && choicePlan != null) {
                val fruitRate = state.fruitExchangeRateMinutesPerFruit
                val rateSeconds = fruitRate.toLong() * 60L
                val maxFruits = if (rateSeconds > 0L) {
                    (choicePlan.fruit.grantSeconds / rateSeconds)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt()
                } else {
                    0
                }
                PolicyFruitExchangeOverlay(
                    targetLabel = choiceTarget?.label,
                    maxFruits = maxFruits,
                    remainingTries = choicePlan.fruit.remaining,
                    balanceFruitHundredths = state.fruitAvailableFruitHundredths,
                    rateMinutesPerFruit = fruitRate,
                    messageRes = unlockChoiceMessage,
                    submitting = fruitSubmitting,
                    messageText = fruitMessageText,
                    onExchange = ::launchFruitExchange,
                    onBack = { closeFruitExchange(returnToChoice = fruitFromChoice) },
                )
            }
            // Drawn after the dock and the choice overlay so the member sheet covers the whole
            // desktop. Its composables no longer open a Dialog; opening one blocked the remote
            // for 1-2 s on the projector (measured 2026-11-22).
            if (subjectSheetOpen) {
                val cloud = state.cloudSubjectMember()
                val activeMember = family.activeMember
                when {
                    cloud != null && state.cloudMembers.size > 1 -> CloudSubjectSwitchOverlay(
                        deviceNo = state.deviceNo,
                        members = state.cloudMembers,
                        activeMemberId = cloud.memberId,
                        protection = state.switchProtection,
                        onDismiss = { subjectSheetOpen = false },
                    )
                    cloud != null -> CloudSubjectProfileOverlay(
                        member = cloud,
                        onDismiss = { subjectSheetOpen = false },
                    )
                    activeMember != null && family.canSwitchMember -> SubjectSwitchOverlay(
                        family = family,
                        onDismiss = { subjectSheetOpen = false },
                    )
                    activeMember != null -> SubjectProfileOverlay(
                        member = activeMember,
                        showSingleHint = true,
                        onDismiss = { subjectSheetOpen = false },
                    )
                }
            }
        } else if (v10) {
            // PIN and duration open over the same wallpaper instead of replacing it with the
            // legacy glass sheet: the clock keeps the place the owner gave it when it is on the
            // left, the info band and dock hide, and the existing panel takes the right side. A
            // clock the owner put on the right hides under the panel so the two cannot overlap.
            LockDesktopBackdrop(
                cover = { lockFilmCover.value },
                policyDefault = true,
                policyImage = childWallpaper?.image,
                policyFrosted = childWallpaper?.frosted,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.40f to Color.Transparent,
                            0.62f to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
            )
            LockDesktopIdentity(
                state = state,
                vm = vm,
                hosted = hosted,
                resumeTick = resumeTick,
                minutes = minutes,
                showPending = state.sessionState == SessionState.PENDING,
                showDetails = false,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 56.dp)
                    .width(360.dp)
                    .graphicsLayer { alpha = if (verifying) 0.55f else 1f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LockOverlayPanel(
                    durationMode = durationMode,
                    pinMode = pinMode,
                    pinPurpose = pinPurpose,
                    selectedMinutes = selectedMinutes,
                    pendingMinutes = pendingMinutes,
                    entered = entered,
                    errorText = errorText,
                    onStep = { up -> selectedMinutes = stepManualDuration(selectedMinutes, up) },
                    onDurationConfirm = {
                        pendingMinutes = selectedMinutes
                        pinPurpose = PinPurpose.MANUAL_START
                        entered = ""
                        error = null
                        durationMode = false
                        pinMode = true
                    },
                    onDigit = ::type,
                    onErase = ::erase,
                    onClosePinPad = ::closePinPad,
                    onRecovery = { recoveryMode = true },
                    durationFocus = durationFocus,
                    firstKeyFocus = firstKeyFocus,
                )
            }
        } else {
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
                    durationMode || pinMode -> LockOverlayPanel(
                        durationMode = durationMode,
                        pinMode = pinMode,
                        pinPurpose = pinPurpose,
                        selectedMinutes = selectedMinutes,
                        pendingMinutes = pendingMinutes,
                        entered = entered,
                        errorText = errorText,
                        onStep = { up -> selectedMinutes = stepManualDuration(selectedMinutes, up) },
                        onDurationConfirm = {
                            pendingMinutes = selectedMinutes
                            pinPurpose = PinPurpose.MANUAL_START
                            entered = ""
                            error = null
                            durationMode = false
                            pinMode = true
                        },
                        onDigit = ::type,
                        onErase = ::erase,
                        onClosePinPad = ::closePinPad,
                        onRecovery = { recoveryMode = true },
                        durationFocus = durationFocus,
                        firstKeyFocus = firstKeyFocus,
                    )
                    else -> {
                        val resting = state.lockReason == LockReason.RESTING &&
                            state.restRemainingSeconds > 0
                        // Rest and a closed window are different reasons, but both mean the
                        // parent-controlled entries cannot start a session right now.
                        val manualAvailable = manualWaiting && !resting && manualWindowOpen &&
                            (state.lockReason == LockReason.MANUAL_WAITING || redeemManual)
                        val manualFrozen = manualWaiting && !resting && !manualAvailable
                        val quizFeatureVisible = quizState.enabled &&
                            quizState.entry != "blocked" &&
                            state.quizGateEnabled
                        // The legacy sheet has no app icons, so its fixed quiz action is the
                        // only entry and follows the display switch.
                        val quizActionVisible = quizFeatureVisible &&
                            quizState.lockEntryVisible
                        val quizLabel = when {
                            quizState.sessionState == "running" -> stringResource(
                                R.string.policy_quiz_resume,
                                ((quizState.session?.run?.index ?: 0) + 1)
                                    .coerceAtMost(quizState.questionCount.coerceAtLeast(1)),
                                quizState.questionCount,
                            )
                            else -> stringResource(R.string.policy_quiz_entry)
                        }
                        // A manual grant can already be waiting for its local start (the
                        // phone precomputed the time for a redemption). The grant leaves
                        // lockReason at NONE, so keep the quiz entry available for it too.
                        val pendingManual = manualWaiting &&
                            state.sessionState == SessionState.PENDING && !resting
                        val quizAvailable = (manualAvailable || pendingManual) &&
                            quizActionVisible
                        // Whether a start action is shown below. Study Mode is an entry,
                        // not the OK target.
                        val primaryAction = when {
                            state.sessionState == SessionState.PENDING && !resting -> true
                            state.noTimedApps -> false
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
                            // unlocks, so both stay frozen until the rest interval passes.
                            if (contentShown) Spacer(Modifier.height(12.dp))
                            if (manualWaiting) {
                                // The quiz is a parent-controlled manual unlock too,
                                // so keep its entry visible and frozen during rest.
                                if (quizActionVisible) {
                                    FrozenAction(label = quizLabel)
                                    Spacer(Modifier.height(10.dp))
                                }
                                FrozenAction(
                                    label = stringResource(R.string.policy_manual_confirm),
                                )
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
                                        onClick = {
                                            quizTarget = null
                                            QuizLaunchTarget.clear()
                                            quizMode = true
                                        },
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
                            manualFrozen -> {
                                if (contentShown) Spacer(Modifier.height(12.dp))
                                // Keep both parent-controlled entries visible but dimmed, so
                                // the child sees that they return when the next window opens.
                                // The left column already carries the next-window guidance.
                                if (quizActionVisible) {
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
                                            quizTarget = null
                                            QuizLaunchTarget.clear()
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
                            label = stringResource(R.string.policy_exit_child_mode),
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
}

/**
 * The PIN pad and the manual-length wheel. Both the legacy sheet and the V10 desktop overlay
 * use this one body so the two shells cannot drift apart; only their container differs.
 */
@Composable
private fun LockOverlayPanel(
    durationMode: Boolean,
    pinMode: Boolean,
    pinPurpose: PinPurpose,
    selectedMinutes: Int,
    pendingMinutes: Int?,
    entered: String,
    errorText: String?,
    onStep: (up: Boolean) -> Unit,
    onDurationConfirm: () -> Unit,
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    onClosePinPad: () -> Unit,
    onRecovery: () -> Unit,
    durationFocus: FocusRequester,
    firstKeyFocus: FocusRequester,
) {
    when {
        durationMode -> ManualDurationPicker(
            minutes = selectedMinutes,
            onStep = onStep,
            onConfirm = onDurationConfirm,
            focusRequester = durationFocus,
        )
        pinMode -> {
            BasicText(
                stringResource(
                    if (pinPurpose == PinPurpose.MANUAL_START) {
                        R.string.policy_manual_pin_title
                    } else {
                        // The unlock pad is the way out of child mode, so its heading is the
                        // prompt itself; there is no second line of instructions.
                        R.string.policy_parent_pin_prompt
                    },
                ),
                style = LockHeadingText,
            )
            if (pinPurpose == PinPurpose.MANUAL_START) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(R.string.policy_pin_hint),
                    style = LockCaptionText.copy(
                        color = if (errorText == null) Palette.SecondaryLabel else Palette.Red,
                    ),
                )
                Spacer(Modifier.height(6.dp))
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
                onDigit = onDigit,
                onErase = onErase,
                firstKeyFocus = firstKeyFocus,
                leftKeyLabel = stringResource(R.string.policy_back),
                onLeftKey = onClosePinPad,
            )
            // Back fills the slot left of 0 on both pads, so the key block stays
            // aligned. The unlock pad's recovery code is the rarer escape and
            // sits below it.
            if (unlockPad) {
                Spacer(Modifier.height(8.dp))
                LockActionButton(
                    label = stringResource(R.string.policy_recovery_key),
                    onClick = onRecovery,
                    width = 180.dp,
                    labelStyle = LockRecoveryKeyText,
                )
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
 * The V10 lock-screen identity: source/activation badge, a minute clock, and, while the child
 * cannot start now, the estimated next usable time. While the child cannot start, the rule values
 * behind that estimate are shown below it (today's windows, rest, screen-off grace), and automatic
 * mode adds the day's leftover quota. The recipient avatar sits beside the estimate once a grant
 * has supplied one; both are omitted while the estimate would read "now". Everything is laid out
 * around the clock's colon axis so the values read as one composition.
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
    // Diagnostic for the activation identity: log the values that actually reach the lock-screen
    // identity, not just the runtime state. A missing line here while the runtime logs a new
    // activation pinpoints a UI publication problem rather than a config problem.
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
    val detailReason = lockDetailReason(state)
    val expectedLabel = stringResource(R.string.policy_lock_expected_label)
    val windowsLabel = stringResource(R.string.policy_lock_detail_windows)
    val restLabel = stringResource(R.string.policy_lock_detail_rest)
    // The hidden buffer row still sets the shared label width, so hiding it cannot shift the
    // values that remain.
    val bufferLabel = stringResource(R.string.policy_lock_detail_buffer)
    // The rule rows start on the first character of "预计可用" rather than at the clock axis.
    // The column is as wide as the longest label so no translation clips, and it is still
    // anchored to the axis on its right so the values keep their place beside the colon.
    val ruleLabelWidth = lockRuleLabelWidth(
        expectedLabel = expectedLabel,
        detailLabels = listOf(windowsLabel, restLabel, bufferLabel),
    )
    val expectedAvailable = expectedAvailableLabel(state, now)
    if (expectedAvailable != null) {
        LockSpecRow(
            label = expectedLabel,
            value = expectedAvailable,
            labelStyle = LockExpectedLabelText,
            valueStyle = LockExpectedValueText,
            // A redemption grant carries the recipient icon; keep it beside the estimate and
            // after the grant is consumed, as before. It is hidden with the row while the
            // estimate is "now": the enabled start action already says the TV is usable.
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
    }
    if (detailReason != null) {
        if (expectedAvailable != null) Spacer(Modifier.height(8.dp))
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
            labelWidth = ruleLabelWidth,
        )
    }
    if (showPending) {
        if (expectedAvailable != null) Spacer(Modifier.height(10.dp))
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
 * Under the clock: one left-hand column carries the estimate, the reason and the rule details;
 * the right side stays wallpaper. 600 dp leaves room for the longest translations, and the
 * paired last detail row keeps the block clear of a four-column dock plate.
 */
private val LockDesktopColumnStart = LockDesktopStart
private val LockDesktopColumnWidth = 600.dp
private val LockDesktopDetailPairGap = 16.dp
private val LockDesktopDetailPairWidth = 292.dp

/**
 * Aerial reserves 152 dp for its large clock below its 80 dp top. The estimate band starts at the
 * end of that block for both clock sizes, so the child's avatar, the next usable time and the rest
 * reason keep the same place when the owner switches between the large and the small clock; with
 * the small clock the space above them is simply left empty.
 */
private val LockDesktopInfoTop = 232.dp

/** Aerial's clock coordinates on Home: large and small, left and right. */
private val LockDesktopClockTop = 80.dp
private val LockDesktopSmallClockTop = 28.dp
private val LockDesktopSmallClockHeight = 34.dp

/**
 * The child's face beside the clock's time line: 80 dp against the large 104 sp digits (whose ink
 * is about 76 dp tall), the old 34 dp chip against the small clock. The face is centred on the
 * digits and kept [LockDesktopAvatarGap] from the time's measured width, not from the column's:
 * the date and the weather run wider than the time, and a face placed after them read as a mark
 * of its own on the wallpaper (the owner, JmGO projector).
 */
private val LockDesktopLargeAvatar = 80.dp
private val LockDesktopSmallAvatar = 34.dp
private val LockDesktopAvatarGap = 16.dp
private val LockDesktopSmallAvatarGap = 14.dp
private val LockDesktopLargeAvatarTopInset = 28.dp
/** The small clock is time-only when no weather is known; 60 dp covers "12:59" at 19 sp. */
private val LockDesktopSmallClockColumn = 60.dp

/** The small right clock stays clear of the pill as it was: 14 dp gap, the old 34 dp chip, 10 dp. */
private val LockDesktopSmallClockChipSlot = 58.dp

/** Aerial's status-pill centre is 45 dp from the top; a 32 dp glyph is centred on it. */
private val LockDesktopBadgeTop = 29.dp
private val LockDesktopBadgeGap = 14.dp

private val LockDesktopStatusGlyph = 32.dp
private val LockDesktopLabelGap = 12.dp
private val LockDesktopTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1f),
    blurRadius = 6f,
)

/**
 * The garden member the backend resolved for this bound TV, or null on a local roster.
 * Both the desktop face and the sheet opened from it read this one derivation.
 */
private fun PolicyUiState.cloudSubjectMember(): CloudMember? =
    cloudActiveMemberId?.let { activeId ->
        val row = cloudMembers.firstOrNull { it.memberId == activeId }
        CloudMember(
            memberId = activeId,
            name = cloudActiveMemberName.ifBlank { row?.name.orEmpty() },
            avatarUrl = cloudActiveMemberIcon.ifBlank { row?.avatarUrl.orEmpty() },
            isChild = row?.isChild,
        )
    }?.takeIf { mode == PolicyMode.CLOUD }

/**
 * The V10 identity on the wallpaper, built on Aerial's own clock: [ShelfClock] or [CornerClock] at
 * the same coordinates and with the same [AerialSettings] choices Home uses (`largeClock`,
 * `clockRight`, `clockHours`, `showWeather`, the system language). Nothing about the clock is
 * re-implemented here, so an Aerial clock change reaches the lock screen unchanged.
 *
 * When the child cannot start now, the estimate sits under the clock on the left at
 * [LockDesktopInfoTop], followed by the rest reason and the rule details in the same column; the
 * right side stays wallpaper. The last detail row pairs the rest interval with the remaining
 * quota so a four-column dock plate never touches the block. The estimate row is omitted while
 * the estimate would read "now". That band does not move with the clock size or side, by the
 * owner's requirement.
 *
 * The child's face sits beside the clock's time line — right of a left clock, left of a right
 * clock — rather than at the top right, where the remote had to travel through the pill to reach
 * it. The vertical path is reversible: Up from the dock reaches the face, Up again the pill, and
 * Down from the pill comes back through the face to the dock. OK opens the member sheet, and the
 * face rides with the clock's scroll and fade. The large clock gives it 80 dp, the small clock
 * keeps the old 34 dp chip; the large face takes its place from the time's measured width, so the
 * date and weather lines below never push it away, and slides when the hour grows a digit. The
 * PIN/duration panel keeps no face.
 *
 * The top-right chrome is Aerial's own [StatusPill], the same one Home draws: the network glyph,
 * the settings/Control-Center glyph and the product's mode badge through
 * [LocalStatusPillTrailing]. It sits at Home's coordinates (28 dp from the top, 42 dp from the
 * end), so the lock screen and the desktop cannot drift apart. The pill opens the parent panel;
 * with the face it is the only focusable chrome outside the dock. The time stays out of the pill
 * because the lock screen always shows the clock, exactly as Home does while its clock is
 * visible.
 *
 * Where [showDetails] is false the PIN/duration panel owns the right side, so only the plain
 * shield/cloud mark is drawn, without focus. A clock the owner placed on the right is hidden
 * under the panel for the same reason; a left clock keeps its place.
 */
@Composable
private fun LockDesktopIdentity(
    state: PolicyUiState,
    vm: HomeViewModel,
    hosted: Boolean,
    resumeTick: Int,
    minutes: Int,
    showPending: Boolean,
    showDetails: Boolean = true,
    statusInteractive: Boolean = true,
    statusFocus: FocusRequester? = null,
    statusDownFocus: FocusRequester? = null,
    onStatusClick: (() -> Unit)? = null,
    onStatusFocused: ((Boolean) -> Unit)? = null,
    /** The face sits above the grid; focusing it returns the grid to the shelf, as the pill does. */
    onSubjectFocused: ((Boolean) -> Unit)? = null,
    /** The dock grid has the clock's place: the status pill carries the time instead. */
    showTime: Boolean = false,
    /** The dock grid's scroll in px; the clock and the info band ride up and fade with it. */
    scroll: () -> Float = { 0f },
    collapsePx: Float = 1f,
    /** The member sheet is rendered by the lock screen so it can cover the dock. */
    subjectSheetOpen: Boolean = false,
    onSubjectSheetOpen: () -> Unit = {},
) {
    val now by rememberNow()
    val clockRight = AerialSettings.clockRight
    val largeClock = AerialSettings.largeClock
    // V40 identity. The chip is part of the lock desktop only: the parent Home and the
    // PIN/duration panel keep no child avatar or switch entry.
    val family by FamilyHub.state.collectAsState()
    val member = family.activeMember
    // The garden family member the backend resolved for this TV (2026-11-21). A cloud-bound TV
    // shows this identity instead of the local roster: after binding, the desktop user is the
    // family member the parent configured in the Garden app. Older backends send no member;
    // those keep the local chip below unchanged.
    val cloudMember: CloudMember? = state.cloudSubjectMember()
    val subjectFocus = remember { FocusRequester() }
    var restoreSubjectFocus by remember { mutableStateOf(false) }
    val showSubjectChip = showDetails && statusInteractive && (cloudMember != null || member != null)
    LaunchedEffect(subjectSheetOpen) {
        if (!subjectSheetOpen && restoreSubjectFocus) {
            restoreSubjectFocus = false
            withFrameNanos { }
            runCatching { subjectFocus.requestFocus() }
        }
    }
    // The clock and the info band are Home's content above the rows: they ride up with the grid
    // and fade over the first part of its scroll, while the status pill above stays put.
    val scrollAway = Modifier.graphicsLayer {
        translationY = -scroll()
        alpha = 1f - (scroll() / collapsePx * 1.6f).coerceIn(0f, 1f)
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
    // Passing the callbacks only from the main menu keeps the same badge static under the
    // PIN/duration panel without a second drawing path.
    val guardBadge: @Composable (Modifier) -> Unit = { badgeModifier ->
        LockGuardBadge(
            hosted = hosted,
            modifier = badgeModifier,
            focusRequester = if (showDetails) statusFocus else null,
            downFocus = if (showDetails) statusDownFocus else null,
            onFocused = if (showDetails) onStatusFocused else null,
            onClick = if (showDetails) onStatusClick else null,
        )
    }
    val fallbackStatusFocus = remember { FocusRequester() }
    val resolvedStatusFocus = statusFocus ?: fallbackStatusFocus
    val statusPill: @Composable (Modifier) -> Unit = { pillModifier ->
        CompositionLocalProvider(
            LocalStatusPillTrailing provides { foreground -> PolicyStatusPillBadge(foreground) },
        ) {
            StatusPill(
                now = now,
                network = vm.network,
                showTime = showTime,
                enabled = statusInteractive && !subjectSheetOpen,
                focusRequester = resolvedStatusFocus,
                onFocused = { onStatusFocused?.invoke(it) },
                onClick = { onStatusClick?.invoke() },
                modifier = pillModifier
                    .graphicsLayer { alpha = if (statusInteractive) 1f else 0f }
                    // Left reaches the current-member chip without changing the familiar
                    // "Up to the pill" path. Down walks the same column back — the face first,
                    // then the dock — so Up from the dock and Down from the pill meet at the
                    // face. Without the face (PIN/duration panel) Down keeps going to the dock.
                    .onPreviewKeyEvent { event ->
                        val code = event.nativeKeyEvent.keyCode
                        if (showSubjectChip && code == NativeKeyEvent.KEYCODE_DPAD_LEFT) {
                            if (event.type == KeyEventType.KeyDown &&
                                event.nativeKeyEvent.repeatCount == 0
                            ) {
                                runCatching { subjectFocus.requestFocus() }
                            }
                            return@onPreviewKeyEvent true
                        }
                        if (code != NativeKeyEvent.KEYCODE_DPAD_DOWN) {
                            return@onPreviewKeyEvent false
                        }
                        if (event.type == KeyEventType.KeyDown &&
                            event.nativeKeyEvent.repeatCount == 0
                        ) {
                            val face = showSubjectChip &&
                                runCatching { subjectFocus.requestFocus() }.isSuccess
                            if (!face) runCatching { statusDownFocus?.requestFocus() }
                        }
                        true
                    },
            )
        }
    }
    // The face joins the clock on its outer side; a remote reaches it with Up from the dock, Up
    // again returns to the pill, and the sheet it opens is the established profile/switch
    // overlay.
    val subjectChip: @Composable (Dp, Modifier) -> Unit = { diameter, chipModifier ->
        if (showSubjectChip) {
            val cloud = cloudMember
            if (cloud != null) {
                CloudSubjectChip(
                    name = cloud.name,
                    avatarUrl = cloud.avatarUrl,
                    focusRequester = subjectFocus,
                    onClick = {
                        restoreSubjectFocus = true
                        onSubjectSheetOpen()
                    },
                    onUp = { runCatching { resolvedStatusFocus.requestFocus() }.isSuccess },
                    onRight = { runCatching { resolvedStatusFocus.requestFocus() }.isSuccess },
                    onDown = {
                        statusDownFocus?.let { runCatching { it.requestFocus() }.isSuccess } ?: false
                    },
                    diameter = diameter,
                    focusedScale = if (diameter >= 60.dp) 1.06f else 1.1f,
                    enabled = !subjectSheetOpen,
                    onFocused = onSubjectFocused,
                    modifier = chipModifier,
                )
            } else {
                member?.let { chipMember ->
                    SubjectChip(
                        member = chipMember,
                        focusRequester = subjectFocus,
                        onClick = {
                            restoreSubjectFocus = true
                            onSubjectSheetOpen()
                        },
                        onUp = { runCatching { resolvedStatusFocus.requestFocus() }.isSuccess },
                        onRight = { runCatching { resolvedStatusFocus.requestFocus() }.isSuccess },
                        onDown = {
                            statusDownFocus?.let { runCatching { it.requestFocus() }.isSuccess } ?: false
                        },
                        diameter = diameter,
                        focusedScale = if (diameter >= 60.dp) 1.06f else 1.1f,
                        enabled = !subjectSheetOpen,
                        onFocused = onSubjectFocused,
                        modifier = chipModifier,
                    )
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        when {
            // The right side belongs to the PIN/duration panel while it is open.
            !showDetails && clockRight ->
                guardBadge(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = LockDesktopBadgeTop, end = LockDesktopStart),
                )
            else -> {
                // Aerial's clock keeps its coordinates; the face sits on its outer side, clear of
                // the pill, and moves with the clock's scroll.
                if (largeClock) {
                    // The face keeps the time's place, not the column's: the date and the
                    // weather run wider than the digits, so anchoring after the column left a
                    // gap the owner read as two separate marks. The measured time keeps the face
                    // close, and the slide covers the hour growing a digit without a jump.
                    val time = timeText(now)
                    val measurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val reducedMotion = LocalReducedMotion.current
                    val timeWidth = remember(measurer, density, time) {
                        with(density) {
                            measurer.measure(
                                AnnotatedString(time),
                                style = Type.Clock,
                                softWrap = false,
                                maxLines = 1,
                            ).size.width.toDp()
                        }
                    }
                    val faceAnchor by animateDpAsState(
                        targetValue = timeWidth + LockDesktopAvatarGap,
                        animationSpec = if (reducedMotion) {
                            snap()
                        } else {
                            tween(320, easing = Motion.EaseOut)
                        },
                        label = "subject-chip-anchor",
                    )
                    Box(
                        modifier = Modifier
                            .align(if (clockRight) Alignment.TopEnd else Alignment.TopStart)
                            .padding(
                                start = if (clockRight) 0.dp else LockDesktopStart,
                                top = LockDesktopClockTop,
                                end = if (clockRight) LockDesktopStart else 0.dp,
                            )
                            // The face hangs past the clock's column (the time is narrower than
                            // the weather); the box grows around it so the scroll fade and the
                            // focus bounds cover it whole. The clock stays pinned to its edge.
                            .widthIn(min = faceAnchor + LockDesktopLargeAvatar)
                            .then(scrollAway),
                    ) {
                        ShelfClock(now = now, alignEnd = clockRight)
                        // Right of a left clock, left of a right clock: the offset is taken from
                        // the time's outer edge inside the shelf clock, not from the column's.
                        subjectChip(
                            LockDesktopLargeAvatar,
                            Modifier
                                .align(
                                    if (clockRight) Alignment.TopEnd else Alignment.TopStart,
                                )
                                .offset(
                                    x = if (clockRight) -faceAnchor else faceAnchor,
                                    y = LockDesktopLargeAvatarTopInset,
                                ),
                        )
                    }
                } else if (clockRight) {
                    // The small right clock sits just left of the status pill, with Home's 14 dp
                    // gap and its 42 dp end margin; the face moves to the clock's outer side and
                    // the old chip slot keeps the clock and the pill where they were.
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = LockDesktopSmallClockTop,
                                end = LockDesktopStart - LockDesktopBadgeGap,
                            )
                            .height(LockDesktopSmallClockHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        subjectChip(
                            LockDesktopSmallAvatar,
                            Modifier.padding(end = LockDesktopSmallAvatarGap).then(scrollAway),
                        )
                        Box(
                            modifier = Modifier.widthIn(min = LockDesktopSmallClockColumn),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            CornerClock(now, scrollAway)
                        }
                        Spacer(Modifier.width(LockDesktopSmallClockChipSlot))
                        statusPill(Modifier)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = LockDesktopStart, top = LockDesktopSmallClockTop)
                            .height(LockDesktopSmallClockHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = LockDesktopSmallClockColumn)
                                .then(scrollAway),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            CornerClock(now)
                        }
                        subjectChip(
                            LockDesktopSmallAvatar,
                            Modifier.padding(start = LockDesktopSmallAvatarGap).then(scrollAway),
                        )
                    }
                }
                // The small right clock carries the pill in its own row; the other layouts keep
                // Home's pill (or the panel's static badge) at the top right.
                if (largeClock || !clockRight) {
                    if (showDetails) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = LockDesktopSmallClockTop,
                                    end = LockDesktopStart - LockDesktopBadgeGap,
                                )
                                .height(LockDesktopSmallClockHeight),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            statusPill(Modifier)
                        }
                    } else {
                        guardBadge(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = LockDesktopBadgeTop, end = LockDesktopStart),
                        )
                    }
                }
            }
        }
        if (!showDetails) return@Box
        val detailReason = lockDetailReason(state)
        val expectedLabel = stringResource(R.string.policy_lock_expected_label)
        val windowsLabel = stringResource(R.string.policy_lock_detail_windows)
        val restLabel = stringResource(R.string.policy_lock_detail_rest)
        // The hidden buffer row still sets the shared label width, so hiding it cannot shift
        // the values that remain.
        val bufferLabel = stringResource(R.string.policy_lock_detail_buffer)
        val ruleLabelWidth = lockRuleLabelWidth(
            expectedLabel = expectedLabel,
            detailLabels = listOf(windowsLabel, restLabel, bufferLabel),
        )
        val expectedAvailable = expectedAvailableLabel(state, now)
        val expectedLabelStyle = LockExpectedLabelText.copy(
            color = Palette.Label,
            shadow = LockDesktopTextShadow,
        )
        val expectedValueStyle = LockExpectedValueText.copy(
            color = Palette.Label,
            shadow = LockDesktopTextShadow,
        )
        val detailLabelStyle = LockDetailLabelText.copy(
            color = Palette.Label,
            shadow = LockDesktopTextShadow,
        )
        val detailValueStyle = LockDetailValueText.copy(
            color = Palette.Label,
            shadow = LockDesktopTextShadow,
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = LockDesktopColumnStart, y = LockDesktopInfoTop)
                .width(LockDesktopColumnWidth)
                .then(scrollAway),
        ) {
            if (expectedAvailable != null) {
                LockDesktopSpecRow(
                    width = LockDesktopColumnWidth,
                    label = expectedLabel,
                    value = expectedAvailable,
                    labelStyle = expectedLabelStyle,
                    valueStyle = expectedValueStyle,
                    labelWidth = ruleLabelWidth,
                    rowHeight = 36.dp,
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
            }
            if (detailReason != null) {
                if (expectedAvailable != null) Spacer(Modifier.height(4.dp))
                BasicText(
                    detailReason,
                    style = LockDetailReasonText.copy(
                        color = Palette.Label,
                        shadow = LockDesktopTextShadow,
                    ),
                    modifier = Modifier.width(LockDesktopColumnWidth),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (detailReason != null) {
                if (expectedAvailable != null || detailReason != null) {
                    Spacer(Modifier.height(6.dp))
                }
                LockDesktopRuleDetails(
                    width = LockDesktopColumnWidth,
                    state = state,
                    labelWidth = ruleLabelWidth,
                    labelStyle = detailLabelStyle,
                    valueStyle = detailValueStyle,
                )
            }
            if (showPending) {
                if (expectedAvailable != null) Spacer(Modifier.height(4.dp))
                BasicText(
                    stringResource(R.string.policy_available_minutes, minutes),
                    style = LockBodyText.copy(
                        color = Palette.Label,
                        shadow = LockDesktopTextShadow,
                    ),
                )
                if (state.validUntil.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        state.validUntil,
                        style = LockCaptionText.copy(
                            color = Palette.Label,
                            shadow = LockDesktopTextShadow,
                        ),
                        modifier = Modifier.width(LockDesktopColumnWidth),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The top-right guard mark: local is Aerial's shield, remote its cloud, always the product green.
 * On the main menu it is also the parent panel's entry: still 32 dp and in the same place at
 * rest, with Aerial's white focus plate and a darkened glyph when focused. DOWN leaves it for
 * the dock; its callbacks are omitted under the PIN/duration panel, where it stays a plain mark.
 */
@Composable
private fun LockGuardBadge(
    hosted: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    downFocus: FocusRequester? = null,
    onFocused: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val glyph = policyModeGlyph(if (hosted) PolicyMode.CLOUD else PolicyMode.LOCAL)
    if (focusRequester != null && onClick != null) {
        Pressable(
            onClick = onClick,
            modifier = modifier,
            focusRequester = focusRequester,
            focusedScale = 1.08f,
            onFocused = onFocused,
            onKey = { event ->
                if (event.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_DPAD_DOWN) {
                    // Once the badge owns focus the root's idle-key handler stands down, so the
                    // badge itself hands DOWN back to the dock. UP/LEFT/RIGHT stay put.
                    if (event.type == KeyEventType.KeyDown &&
                        event.nativeKeyEvent.repeatCount == 0
                    ) {
                        runCatching { downFocus?.requestFocus() }
                    }
                    true
                } else {
                    false
                }
            },
        ) { state ->
            Box(
                Modifier
                    .size(LockDesktopStatusGlyph)
                    .background(
                        if (state.focused) Palette.Focus else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph,
                    if (state.focused) Palette.OnFocus else Palette.Green,
                    LockDesktopStatusGlyph,
                )
            }
        }
    } else {
        GlyphIcon(glyph, Palette.Green, LockDesktopStatusGlyph, modifier)
    }
}

@Composable
private fun LockDesktopSpecRow(
    label: String,
    value: String,
    labelStyle: TextStyle,
    valueStyle: TextStyle,
    labelWidth: Dp,
    rowHeight: Dp,
    leading: (@Composable () -> Unit)? = null,
    width: Dp = LockDesktopColumnWidth,
) {
    Row(
        modifier = Modifier.width(width).height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(labelWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(10.dp))
                }
                BasicText(
                    label,
                    style = labelStyle.copy(textAlign = TextAlign.Start),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(LockDesktopLabelGap))
        BasicText(
            value,
            style = valueStyle.copy(textAlign = TextAlign.Start),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The configured rules behind the estimate, in the same left-hand column. The windows take one
 * line; the rest interval sits on the next one. Manual timing has no daily quota. The screen-off
 * buffer stays a parent setting and is not repeated on the child's lock screen.
 */
@Composable
private fun LockDesktopRuleDetails(
    state: PolicyUiState,
    labelWidth: Dp,
    labelStyle: TextStyle,
    valueStyle: TextStyle,
    width: Dp = LockDesktopColumnWidth,
) {
    val windows = state.todayWindowsLabel.ifBlank {
        stringResource(R.string.policy_window_none_today)
    }
    Column(modifier = Modifier.width(width)) {
        LockDesktopSpecRow(
            width = width,
            label = stringResource(R.string.policy_lock_detail_windows),
            value = windows,
            labelStyle = labelStyle,
            valueStyle = valueStyle,
            labelWidth = labelWidth,
            rowHeight = LockSpecRowHeight,
        )
        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.width(width)) {
            LockDesktopSpecRow(
                width = LockDesktopDetailPairWidth,
                label = stringResource(R.string.policy_lock_detail_rest),
                value = configuredMinutesLabel(state.configuredRestMinutes),
                labelStyle = labelStyle,
                valueStyle = valueStyle,
                labelWidth = labelWidth,
                rowHeight = LockSpecRowHeight,
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
        else -> null
    }
}

/**
 * The configured rules behind the estimate in the legacy centred lock. Manual timing has no
 * daily quota, so only the window and rest rows are shown. The screen-off buffer is not repeated.
 */
@Composable
private fun LockRuleDetails(state: PolicyUiState, labelWidth: Dp) {
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
private fun LockClock(
    time: String,
    date: String,
    weekday: String,
    alignStart: Boolean = false,
    width: Dp = LockColumnWidth,
    height: Dp = LockClockHeight,
    onWallpaper: Boolean = false,
    /** False on the lock desktop, whose date and weekday sit on the line above the digits. */
    showCalendar: Boolean = true,
) {
    val timeStyle = if (onWallpaper) {
        LockClockText.copy(
            shadow = Shadow(Color.Black.copy(alpha = 0.28f), Offset(0f, 2f), blurRadius = 18f),
        )
    } else {
        LockClockText
    }
    val captionShadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), blurRadius = 6f)
    val dateStyle = if (onWallpaper) LockClockDateText.copy(shadow = captionShadow) else LockClockDateText
    val weekdayStyle = if (onWallpaper) LockClockWeekdayText.copy(shadow = captionShadow) else LockClockWeekdayText
    Layout(
        modifier = Modifier.width(width).height(height),
        content = {
            BasicText(time, style = timeStyle, maxLines = 1)
            if (showCalendar) {
                BasicText(date, style = dateStyle, maxLines = 1)
                BasicText(weekday, style = weekdayStyle, maxLines = 1)
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val clock = measurables[0].measure(loose)
        val clockLeft = if (alignStart) 0 else (constraints.maxWidth - clock.width) / 2
        val clockTop = (constraints.maxHeight - clock.height) / 2
        // Bottom-baseline alignment: the weekday starts where the digits end.
        val dateLine = if (showCalendar) measurables[1].measure(loose) else null
        val weekdayLine = if (showCalendar) measurables[2].measure(loose) else null
        val clockBaseline = clockTop + clock[FirstBaseline]
        val weekdayTop = weekdayLine?.let { clockBaseline - it[FirstBaseline] }
        val dateTop = if (weekdayLine != null && dateLine != null) {
            weekdayTop!! - LockClockLineGap.roundToPx() - dateLine.height
        } else {
            0
        }
        // The block begins after the last digit, not after the old clock tile's edge, so the
        // gap stays optically tight for every minute.
        val blockLeft = clockLeft + clock.width + LockClockDateGap.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            clock.place(clockLeft, clockTop)
            if (dateLine != null && weekdayLine != null && weekdayTop != null) {
                dateLine.place(blockLeft, dateTop)
                weekdayLine.place(blockLeft, weekdayTop)
            }
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
 * The child-facing estimate, or null when it would read "now". The row is omitted while the TV is
 * usable now: the enabled start action already carries that information, so the lock screen does
 * not repeat it. It is deliberately called an estimate: rest ending, a window opening or a parent
 * being nearby never guarantees that entertainment can start.
 */
@Composable
private fun expectedAvailableLabel(state: PolicyUiState, now: LocalDateTime): String? {
    val none = stringResource(R.string.policy_lock_expected_none)
    val tomorrow = stringResource(R.string.policy_tomorrow)
    if (state.setupRequired || state.noTimedApps || !state.localRulesConfigured) return none
    return when {
        state.sessionState == SessionState.PENDING -> null
        state.lockReason == LockReason.RESTING && state.restRemainingSeconds > 0L ->
            clockLabel(now, state.restRemainingSeconds, tomorrow) ?: none
        // Quota resets at the next local day; the exact first window is not in this state.
        state.lockReason == LockReason.TIME_UP -> tomorrow
        state.lockReason == LockReason.OUTSIDE_WINDOW ->
            clockLabel(now, state.nextWindowInSeconds, tomorrow, roundUp = true) ?: none
        state.lockReason == LockReason.MANUAL_WAITING && state.windowRemainingSeconds <= 0L ->
            clockLabel(now, state.nextWindowInSeconds, tomorrow, roundUp = true) ?: none
        state.lockReason == LockReason.MANUAL_WAITING ||
            state.lockReason == LockReason.NONE ||
            state.lockReason == LockReason.PARENT_STOP -> null
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
private fun lockFooterText(
    action: LockAction,
    state: PolicyUiState,
    quizState: QuizRuntime.UiState,
): String = when (action) {
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
        quizState.bankState == "blocked" ->
            stringResource(R.string.policy_lock_quiz_blocked_footer)
        quizState.bankState == "paused" ->
            stringResource(R.string.policy_lock_quiz_paused_footer)
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
        LockAction.CONFIRM_START -> stringResource(R.string.policy_pending_hint)
    LockAction.PARENT_UNLOCK -> stringResource(R.string.policy_lock_parent_footer)
}

@Composable
private fun LockFooterHint(
    action: LockAction,
    state: PolicyUiState,
    quizState: QuizRuntime.UiState,
) {
    val text = lockFooterText(action, state, quizState)
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

    val usedMinutes = (state.dailyUsedSeconds / 60L).toInt()
    val windowRemaining = state.windowRemainingSeconds

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
        state.lockReason == LockReason.MANUAL_WAITING &&
            windowRemaining <= 0L && state.nextWindowInSeconds > 0L ->
            stringResource(R.string.policy_ready_in, durationText(state.nextWindowInSeconds))
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

    Spacer(Modifier.height(2.dp))
    BasicText(
        stringResource(R.string.policy_manual_used_today, usedMinutes),
        style = LockCaptionText.copy(textAlign = TextAlign.Center),
        modifier = Modifier.widthIn(max = 360.dp),
    )

    val showNextWindow = state.nextWindowLabel.isNotBlank() && when (state.lockReason) {
        LockReason.TIME_UP, LockReason.OUTSIDE_WINDOW -> true
        LockReason.MANUAL_WAITING -> windowRemaining <= 0L
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
internal fun PinKeypad(
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
