package app.orchard.tv.ui.policy

import android.graphics.BitmapFactory
import android.view.SurfaceView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.orchard.tv.policy.media.PolicyVideoSource
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen intro film behind both "start this round" faces. It plays once,
 * stops on its last frame, and never keeps the decoder or audio focus after the
 * page goes away. A chosen film shows its own poster (or the shared default
 * wallpaper) until its first frame, so a mismatched poster never flashes.
 *
 * With no chosen film, with reduced motion, or when the file cannot be decoded,
 * the backdrop is [PolicyDefaultBackdrop]: exactly Aerial's default wallpaper.
 * There is no bundled fallback film in the APK any more.
 *
 * [fadeOut] is driven by the start request: the music fades away while the
 * round is being prepared, and comes back if the request is refused. The page
 * does not wait for the fade before it starts the business call. The intro
 * keeps its normal volume while the level card is visible; opening the card
 * never changes the TV's loudness.
 *
 * [onIntroFinished] fires once when the film ends, fails, or is skipped by the
 * reduce-motion or no-film setting; the caller reveals the card, never starts a
 * round from it.
 */
@Composable
internal fun QuizStartBackdrop(
    source: PolicyVideoSource?,
    fadeOut: Boolean,
    onIntroFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = LocalReducedMotion.current
    var player by remember(source) { mutableStateOf<ExoPlayer?>(null) }
    var surface by remember(source) { mutableStateOf<SurfaceView?>(null) }
    var firstFrame by remember(source) { mutableStateOf(false) }
    var failed by remember(source) { mutableStateOf(false) }
    var finished by remember(source) { mutableStateOf(false) }
    val wallpaperMode = source == null || reduced || failed
    val uri = source?.uri

    val posterBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        source?.poster?.path,
    ) {
        val poster = source?.poster
        value = if (poster == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(poster.path)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    val targetVolume = if (fadeOut) 0f else INTRO_VOLUME
    val audioVolume by animateFloatAsState(
        targetValue = targetVolume,
        animationSpec = tween(220, easing = Motion.EaseOut),
        label = "quiz-intro-volume",
    )
    val posterAlpha by animateFloatAsState(
        targetValue = if (firstFrame) 0f else 1f,
        animationSpec = tween(240, easing = Motion.EaseOut),
        label = "quiz-intro-poster",
    )
    val fadeOutNow by rememberUpdatedState(fadeOut)
    val playerNow by rememberUpdatedState(player)
    val finishedNow by rememberUpdatedState(onIntroFinished)

    val signalFinished = {
        if (!finished) {
            finished = true
            finishedNow()
        }
    }

    LaunchedEffect(player, audioVolume) {
        player?.volume = audioVolume
    }

    // No film, or reduced motion: the default wallpaper is the whole intro.
    LaunchedEffect(wallpaperMode) {
        if (wallpaperMode) signalFinished()
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> playerNow?.pause()
                Lifecycle.Event.ON_START -> {
                    if (!fadeOutNow && !reduced && !failed) {
                        playerNow?.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(uri, wallpaperMode) {
        if (wallpaperMode || uri == null) return@DisposableEffect onDispose { }

        var active = true
        val exo = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_OFF
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            volume = 0f
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (active) firstFrame = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (active && playbackState == Player.STATE_ENDED) signalFinished()
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!active) return
                    // No bundled fallback: the shared default wallpaper stands in.
                    failed = true
                    signalFinished()
                }
            })
            surface?.let { setVideoSurfaceView(it) }
            prepare()
            playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        player = exo

        onDispose {
            active = false
            if (player === exo) player = null
            exo.release()
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF03060C))) {
        if (wallpaperMode) {
            PolicyDefaultBackdrop()
        } else {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).also { view ->
                        surface = view
                        player?.setVideoSurfaceView(view)
                    }
                },
                onRelease = { view ->
                    player?.clearVideoSurfaceView(view)
                    if (surface === view) surface = null
                },
                modifier = Modifier.fillMaxSize(),
            )
            // The wallpaper and the film's poster hold the screen until ExoPlayer renders its
            // first frame; both fade out together, so a file without a poster never flashes
            // black and a file with one never shows a stale frame.
            if (posterBitmap == null) {
                PolicyDefaultBackdrop(modifier = Modifier.alpha(posterAlpha))
            }
            posterBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(posterAlpha),
                )
            }
        }
    }
}

private const val INTRO_VOLUME = 0.65f
