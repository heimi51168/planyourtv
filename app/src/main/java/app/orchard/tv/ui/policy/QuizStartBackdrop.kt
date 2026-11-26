package app.orchard.tv.ui.policy

import android.graphics.BitmapFactory
import android.net.Uri
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
import app.orchard.tv.policy.media.PolicyMediaKind
import app.orchard.tv.policy.media.PolicyQuizIntro
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Full-screen intro behind both "start this round" faces: one video or still picture, plus the
 * member's optional custom audio. It plays once, stops on its last frame, and never keeps the
 * decoder or audio focus after the page goes away.
 *
 * Sound contract:
 * - video + custom audio on: the film is muted and the track plays instead;
 * - video + custom audio off (or its file disappeared): the film's original audio plays;
 * - image + custom audio on: the track plays over the still;
 * - image + custom audio off: silence.
 * A video ending fades the custom track out with it; it is never left playing over the card.
 *
 * With no chosen visual, with reduced motion, or when the file cannot be decoded, the backdrop
 * is [PolicyDefaultBackdrop]: exactly Aerial's default wallpaper. There is no bundled fallback
 * film in the APK any more.
 *
 * [fadeOut] is driven by the start request: sound fades away while the round is being prepared,
 * then the players pause, and they come back if the request is refused. The page does not wait
 * for the fade before it starts the business call. [onIntroFinished] fires once when the visual
 * ends, fails, or is skipped by reduce-motion/no-visual; the caller reveals the card, never
 * starts a round from it.
 */
@Composable
internal fun QuizStartBackdrop(
    intro: PolicyQuizIntro,
    fadeOut: Boolean,
    onIntroFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = LocalReducedMotion.current
    val visual = intro.visual
    val visualKind = visual?.kind
    val customAudio = intro.customAudio && intro.audio != null

    var videoPlayer by remember(intro) { mutableStateOf<ExoPlayer?>(null) }
    var audioPlayer by remember(intro) { mutableStateOf<ExoPlayer?>(null) }
    var surface by remember(intro) { mutableStateOf<SurfaceView?>(null) }
    var firstFrame by remember(intro) { mutableStateOf(false) }
    var failed by remember(intro) { mutableStateOf(false) }
    var finished by remember(intro) { mutableStateOf(false) }
    var videoEnded by remember(intro) { mutableStateOf(false) }
    var audioEnded by remember(intro) { mutableStateOf(false) }
    var audioFailed by remember(intro) { mutableStateOf(false) }
    var imageDecoded by remember(intro) { mutableStateOf(false) }
    var imageFailed by remember(intro) { mutableStateOf(false) }

    val effectiveCustomAudio = customAudio && !audioFailed
    val wallpaperMode = visual == null || reduced || failed ||
        (visualKind == PolicyMediaKind.IMAGE && imageFailed)

    val imageBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        visual?.uri?.toString(),
        visualKind,
    ) {
        if (visualKind != PolicyMediaKind.IMAGE) {
            imageDecoded = true
            value = null
            return@produceState
        }
        val path = visual?.uri?.path
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                }.getOrNull()
            }
        }
        imageDecoded = true
    }

    val posterBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        visual?.poster?.path,
    ) {
        val poster = visual?.poster
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

    // A video uses its own soundtrack only when no custom track will replace it. An image has
    // no video element at all.
    val videoTargetVolume = if (visualKind == PolicyMediaKind.VIDEO && !effectiveCustomAudio) {
        if (fadeOut || failed || videoEnded) 0f else INTRO_VOLUME
    } else {
        0f
    }
    val audioTargetVolume = if (effectiveCustomAudio && !fadeOut && !videoEnded && !wallpaperMode) {
        INTRO_VOLUME
    } else {
        0f
    }
    val videoVolume by animateFloatAsState(
        targetValue = videoTargetVolume,
        animationSpec = tween(220, easing = Motion.EaseOut),
        label = "quiz-intro-video-volume",
    )
    val audioVolume by animateFloatAsState(
        targetValue = audioTargetVolume,
        animationSpec = tween(220, easing = Motion.EaseOut),
        label = "quiz-intro-audio-volume",
    )
    val posterAlpha by animateFloatAsState(
        targetValue = if (firstFrame) 0f else 1f,
        animationSpec = tween(240, easing = Motion.EaseOut),
        label = "quiz-intro-poster",
    )

    val fadeOutNow by rememberUpdatedState(fadeOut)
    val videoPlayerNow by rememberUpdatedState(videoPlayer)
    val audioPlayerNow by rememberUpdatedState(audioPlayer)
    val videoEndedNow by rememberUpdatedState(videoEnded)
    val audioEndedNow by rememberUpdatedState(audioEnded)
    val customAudioNow by rememberUpdatedState(effectiveCustomAudio)
    val finishedNow by rememberUpdatedState(onIntroFinished)

    val signalFinished = {
        if (!finished) {
            finished = true
            finishedNow()
        }
    }

    LaunchedEffect(videoPlayer, videoVolume) {
        videoPlayer?.volume = videoVolume
    }
    LaunchedEffect(audioPlayer, audioVolume) {
        audioPlayer?.volume = audioVolume
    }

    // A decoded picture is the whole visual; a decode failure falls back to the wallpaper just
    // like an undecodable film.
    LaunchedEffect(imageDecoded, imageBitmap, visualKind) {
        if (visualKind == PolicyMediaKind.IMAGE && imageDecoded && imageBitmap == null) {
            imageFailed = true
            signalFinished()
        }
    }
    LaunchedEffect(wallpaperMode) {
        if (wallpaperMode) {
            // A broken visual falls back to the wallpaper, and its custom track must not keep
            // playing over the question card.
            runCatching { audioPlayer?.pause() }
            signalFinished()
        }
    }
    // An image without custom audio is silent and has no end event of its own.
    LaunchedEffect(visualKind, effectiveCustomAudio, audioPlayer) {
        if (visualKind == PolicyMediaKind.IMAGE && !effectiveCustomAudio) signalFinished()
    }

    // The start request owns the round from the moment it is sent. Once sound reaches zero the
    // players pause instead of continuing under the incoming question page. A refused start
    // flips fadeOut back and resumes from the same position.
    LaunchedEffect(fadeOut, videoEnded, effectiveCustomAudio, videoPlayer, audioPlayer) {
        if (fadeOut) {
            delay(220)
            runCatching { videoPlayer?.pause() }
            runCatching { audioPlayer?.pause() }
            return@LaunchedEffect
        }
        if (videoEnded && effectiveCustomAudio) {
            delay(220)
            runCatching { audioPlayer?.pause() }
            return@LaunchedEffect
        }
        if (!reduced && !failed && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (!videoEnded) runCatching { videoPlayer?.play() }
            if (effectiveCustomAudio && !audioEnded) runCatching { audioPlayer?.play() }
        }
    }

    // The custom track owns audio focus while it is active. Rebuilding the film player would
    // restart the picture, so on a late audio failure only the video's audio attributes change.
    LaunchedEffect(audioFailed, customAudio) {
        val exo = videoPlayer ?: return@LaunchedEffect
        if (customAudio && audioFailed) {
            runCatching {
                exo.setAudioAttributes(audioAttributes(), true)
            }
            runCatching { audioPlayer?.pause() }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    videoPlayerNow?.pause()
                    audioPlayerNow?.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (!fadeOutNow && !reduced && !failed) {
                        if (!videoEndedNow) videoPlayerNow?.play()
                        if (customAudioNow && !audioEndedNow && !videoEndedNow) {
                            audioPlayerNow?.play()
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val videoUri = visual?.uri
    DisposableEffect(videoUri, wallpaperMode, customAudio) {
        if (wallpaperMode || videoUri == null || visualKind != PolicyMediaKind.VIDEO) {
            return@DisposableEffect onDispose { }
        }

        var active = true
        val exo = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_OFF
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            // Only one of the two players may request focus; the custom track does when present.
            setAudioAttributes(audioAttributes(), !customAudio)
            volume = 0f
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (active) firstFrame = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (active && playbackState == Player.STATE_ENDED) {
                        videoEnded = true
                        signalFinished()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!active) return
                    failed = true
                    signalFinished()
                }
            })
            surface?.let { setVideoSurfaceView(it) }
            prepare()
            playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        videoPlayer = exo

        onDispose {
            active = false
            if (videoPlayer === exo) videoPlayer = null
            exo.release()
        }
    }

    val audioFile = if (customAudio) intro.audio?.file else null
    DisposableEffect(audioFile) {
        if (audioFile == null || wallpaperMode) return@DisposableEffect onDispose { }

        var active = true
        val exo = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(audioFile)))
            repeatMode = Player.REPEAT_MODE_OFF
            setAudioAttributes(audioAttributes(), true)
            volume = 0f
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (active && playbackState == Player.STATE_ENDED) {
                        audioEnded = true
                        if (visualKind == PolicyMediaKind.IMAGE) signalFinished()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!active) return
                    // The visual keeps going; a film returns to its own soundtrack and a still
                    // simply has no sound left.
                    audioFailed = true
                    signalFinished()
                }
            })
            prepare()
            playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        audioPlayer = exo

        onDispose {
            active = false
            if (audioPlayer === exo) audioPlayer = null
            exo.release()
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF03060C))) {
        when {
            wallpaperMode -> PolicyDefaultBackdrop()
            visualKind == PolicyMediaKind.IMAGE -> {
                val bitmap = imageBitmap
                if (bitmap == null) {
                    PolicyDefaultBackdrop()
                } else {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            else -> {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { view ->
                            surface = view
                            videoPlayer?.setVideoSurfaceView(view)
                        }
                    },
                    onRelease = { view ->
                        videoPlayer?.clearVideoSurfaceView(view)
                        if (surface === view) surface = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // The wallpaper and the film's poster hold the screen until ExoPlayer renders
                // its first frame; both fade out together, so a file without a poster never
                // flashes black and a file with one never shows a stale frame.
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
}

private fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .build()

private const val INTRO_VOLUME = 0.65f