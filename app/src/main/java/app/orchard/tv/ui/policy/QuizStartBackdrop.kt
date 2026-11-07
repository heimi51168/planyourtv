package app.orchard.tv.ui.policy

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import app.orchard.tv.R
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion

/**
 * Full-screen intro film behind both "start this round" faces. It plays once,
 * stops on its last frame, and never keeps the decoder or audio focus after the
 * page goes away. The poster is always present until the first video frame, and
 * remains as the whole backdrop when the TV is set to reduce motion or when the
 * film cannot be decoded.
 *
 * [fadeOut] is driven by the start request: the music ducks away while the
 * round is being prepared, and comes back if the request is refused. The page
 * does not wait for the fade before it starts the business call.
 */
@Composable
internal fun QuizStartBackdrop(
    fadeOut: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = LocalReducedMotion.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var surface by remember { mutableStateOf<SurfaceView?>(null) }
    var firstFrame by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

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

    LaunchedEffect(player, audioVolume) {
        player?.volume = audioVolume
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

    DisposableEffect(reduced, failed) {
        if (reduced || failed) return@DisposableEffect onDispose { }

        val exo = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("asset:///quiz/start_intro.mp4"))
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
                    firstFrame = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }
            })
            surface?.let { setVideoSurfaceView(it) }
            prepare()
            playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        player = exo

        onDispose {
            if (player === exo) player = null
            exo.release()
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF03060C))) {
        if (!reduced && !failed) {
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
        }
        Image(
            painter = painterResource(R.drawable.quiz_start_poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(posterAlpha),
        )
    }
}

private const val INTRO_VOLUME = 0.65f
