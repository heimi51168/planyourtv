package app.orchard.tv.ui.policy

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.media.HostedMediaRoute
import app.orchard.tv.policy.media.PolicyAudioEntry
import app.orchard.tv.policy.media.PolicyVideoLibrary
import app.orchard.tv.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The active child member's background-music track. The library file is shared, but the
 * selection is per member; the preference listener refreshes the player when the parent picks
 * another track or removes it.
 */
@Composable
internal fun rememberPolicyBackgroundAudio(): PolicyAudioEntry? {
    val context = LocalContext.current.applicationContext
    val family by FamilyHub.state.collectAsState()
    // V50: the hosted route disables the local track in CLOUD and restores it on the way back,
    // neither of which touches a preference key. Re-read when the route revision changes.
    val hostedRevision by HostedMediaRoute.revision.collectAsState()
    var revision by remember(context) { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("quiz-intro", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (PolicyVideoLibrary.isAudioPreferenceKey(key)) revision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var entry by remember(context, family.activeUserId) {
        mutableStateOf<PolicyAudioEntry?>(null)
    }
    LaunchedEffect(context, family.activeUserId, revision, hostedRevision) {
        entry = withContext(Dispatchers.IO) {
            runCatching { PolicyVideoLibrary.lockAudio(context) }.getOrNull()
        }
    }
    return entry
}

/**
 * Looping background music for the child desktop. It is deliberately its own player, so the
 * track survives a rotation item change instead of restarting with every video. [active] is
 * false while the screensaver, the unlock choice, a quiz or a video's own audio owns the sound;
 * pausing (not merely muting) releases audio focus to the quiz/voice player.
 */
@Composable
internal fun PolicyBackgroundMusic(entry: PolicyAudioEntry?, active: Boolean) {
    val audio = entry ?: return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember(audio.id) { mutableStateOf<ExoPlayer?>(null) }
    val playerNow by rememberUpdatedState(player)
    val activeNow by rememberUpdatedState(active)

    DisposableEffect(audio.id) {
        val exo = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(audio.file)))
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            volume = 0f
            prepare()
        }
        player = exo
        onDispose {
            if (player === exo) player = null
            exo.release()
        }
    }

    val volume by animateFloatAsState(
        targetValue = if (active) BACKGROUND_MUSIC_VOLUME else 0f,
        animationSpec = tween(if (active) 600 else 240, easing = Motion.EaseOut),
        label = "policy-background-music-volume",
    )
    LaunchedEffect(player, volume) {
        player?.volume = volume
    }
    LaunchedEffect(player, active) {
        val exo = player ?: return@LaunchedEffect
        if (active) exo.play() else exo.pause()
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> playerNow?.pause()
                Lifecycle.Event.ON_START -> if (activeNow) playerNow?.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** Softer than the video's own soundtrack; speech and quiz prompts stay intelligible. */
private const val BACKGROUND_MUSIC_VOLUME = 0.30f