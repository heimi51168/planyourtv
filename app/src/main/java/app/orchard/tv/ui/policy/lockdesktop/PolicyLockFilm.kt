package app.orchard.tv.ui.policy.lockdesktop

import android.view.SurfaceView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import app.orchard.tv.policy.media.PolicyMediaKind
import app.orchard.tv.policy.media.PolicyVideoSource
import app.orchard.tv.ui.theme.Motion

/**
 * The child-mode film chosen in the parent center, looping silently behind the lock desktop.
 *
 * A SurfaceView sits behind the window, so the lock screen keeps its root transparent while
 * this is composed and fades its own default-wallpaper picture out through [cover] once the
 * first frame is ready. The audio track is disabled as well as the volume muted: this is a
 * background wallpaper, not a second audio source.
 *
 * The decoder is released with the lock screen and paused when the app stops. A film that
 * cannot be decoded leaves [cover] at zero, so the default wallpaper simply stays.
 *
 * [active] false releases the decoder and drops [cover] back to zero while the caller shows its
 * poster instead (a screensaver, or a quiz start shell with an intro film of its own). The same
 * source starts again, from its first frame, once the caller lets it play.
 */
@Composable
internal fun PolicyLockFilm(
    source: PolicyVideoSource,
    cover: Animatable<Float, *>,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    // A still picture is drawn by the caller's backdrop; it must never reach ExoPlayer.
    if (source.kind != PolicyMediaKind.VIDEO) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var surface by remember(source) { mutableStateOf<SurfaceView?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    var firstFrame by remember(source) { mutableStateOf(false) }
    var player by remember(source) { mutableStateOf<ExoPlayer?>(null) }
    val playerNow by rememberUpdatedState(player)

    LaunchedEffect(source) {
        cover.snapTo(0f)
    }
    LaunchedEffect(active) {
        if (!active) {
            // The next player renders its first frame again; until then the caller's poster is
            // the picture, not the frame this one stopped on.
            firstFrame = false
            cover.snapTo(0f)
        }
    }
    LaunchedEffect(firstFrame, failed) {
        when {
            failed -> cover.snapTo(0f)
            firstFrame -> cover.animateTo(
                targetValue = 1f,
                animationSpec = tween(540, easing = Motion.EaseOut),
            )
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> playerNow?.pause()
                Lifecycle.Event.ON_START -> playerNow?.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(source, failed, active) {
        if (failed || !active) return@DisposableEffect onDispose { }

        var active = true
        val exo = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(source.uri))
            repeatMode = Player.REPEAT_MODE_ONE
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            volume = 0f
            trackSelectionParameters = TrackSelectionParameters.Builder()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (active) firstFrame = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (active) failed = true
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

    if (!failed) {
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
            modifier = modifier.fillMaxSize(),
        )
    }
}
