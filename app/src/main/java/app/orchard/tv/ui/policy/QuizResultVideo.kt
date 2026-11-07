package app.orchard.tv.ui.policy

import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

private const val RESULT_VIDEO_VOLUME = 0.5f

/**
 * Square reward film on the quiz report. It plays once and keeps its last
 * frame; the settlement voice keeps priority because the clip asks for audio
 * focus, so ExoPlayer ducks its BGM while the voice speaks. The poster is the
 * film's last frame, so reduced motion and decoder failures show the same
 * reward without starting a player.
 *
 * TextureView rather than SurfaceView: this sits inside a rounded glass card,
 * where a SurfaceView could punch through the Compose clipping.
 */
@Composable
internal fun QuizResultVideo(size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = LocalReducedMotion.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var texture by remember { mutableStateOf<TextureView?>(null) }
    var firstFrame by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val playerNow by rememberUpdatedState(player)
    val videoAlpha by animateFloatAsState(
        targetValue = if (firstFrame) 1f else 0f,
        animationSpec = tween(220, easing = Motion.EaseOut),
        label = "quiz-result-video",
    )

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> playerNow?.pause()
                Lifecycle.Event.ON_START -> if (!reduced && !failed) playerNow?.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(reduced, failed) {
        if (reduced || failed) return@DisposableEffect onDispose { }
        firstFrame = false
        val exo = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("asset:///quiz/show_result.mp4"))
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
            volume = RESULT_VIDEO_VOLUME
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrame = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }
            })
            texture?.let { setVideoTextureView(it) }
            prepare()
            playWhenReady = true
        }
        player = exo
        onDispose {
            exo.release()
            if (player === exo) player = null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.quiz_show_result_poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (!reduced && !failed) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { view ->
                        texture = view
                        player?.setVideoTextureView(view)
                    }
                },
                update = { view -> player?.setVideoTextureView(view) },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(videoAlpha),
            )
        }
    }
}
