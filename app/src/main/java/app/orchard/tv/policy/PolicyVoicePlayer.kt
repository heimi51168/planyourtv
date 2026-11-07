package app.orchard.tv.policy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.util.Log
import app.orchard.tv.R
import com.planyourtv.policy.PolicyFlowTrace
import com.planyourtv.policy.PolicyVoiceBus
import com.planyourtv.policy.PolicyVoiceCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Plays the pre-generated Chinese clips. No TTS and no network at runtime; the cue set and
 * clips are produced by tools/tts/generate_voice.py. Voice is on by default; [enabled] is the
 * hook for a future parent setting.
 *
 * Clips duck other media with a transient audio focus instead of pausing it, and a new cue
 * replaces the clip that is still playing (for example START right after GRANT_READY).
 */
object PolicyVoicePlayer {
    private const val TAG = "PolicyVoice"
    private const val WRONG_EGG_CHANCE = 0.12f
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val started = AtomicBoolean(false)
    private var appContext: Context? = null
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    @Volatile
    var enabled: Boolean = true

    // Every approved quiz cue is a small set; a shuffle bag avoids playing the
    // same line twice in a row while still keeping the order unpredictable.
    private val bags = mutableMapOf<String, MutableList<Int>>()
    private val lastClip = mutableMapOf<String, Int>()

    private val correctClips = intArrayOf(
        R.raw.voice_quiz_correct_1,
        R.raw.voice_quiz_correct_2,
        R.raw.voice_quiz_correct_3,
        R.raw.voice_quiz_correct_4,
        R.raw.voice_quiz_correct_5,
        R.raw.voice_quiz_correct_6,
    )
    private val wrongClips = intArrayOf(
        R.raw.voice_quiz_wrong_1,
        R.raw.voice_quiz_wrong_2,
        R.raw.voice_quiz_wrong_3,
        R.raw.voice_quiz_wrong_4,
        R.raw.voice_quiz_wrong_5,
        R.raw.voice_quiz_wrong_6,
    )
    // The idiom lines are a rare treat, not part of the regular wrong rotation.
    private val wrongIdiomClips = intArrayOf(
        R.raw.voice_quiz_wrong_idiom_1,
        R.raw.voice_quiz_wrong_idiom_2,
    )
    private val timeoutClips = intArrayOf(
        R.raw.voice_quiz_timeout_1,
        R.raw.voice_quiz_timeout_2,
    )
    private val startClips = intArrayOf(
        R.raw.voice_quiz_start_1,
        R.raw.voice_quiz_start_2,
    )
    private val settleClips = intArrayOf(
        R.raw.voice_quiz_settle_1,
        R.raw.voice_quiz_settle_2,
    )

    fun install(context: Context) {
        appContext = context.applicationContext
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            PolicyVoiceBus.cues.collect { cue -> play(cue) }
        }
    }

    private fun play(cue: PolicyVoiceCue) {
        val context = appContext ?: return
        if (!enabled) return
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        // Only the pause cue may play with the screen off; some TVs cut audio during sleep.
        if (power?.isInteractive == false && cue != PolicyVoiceCue.PAUSED) return
        val resource = resourceFor(cue, context)
        if (resource == 0) return
        // Preview/dev diagnostics: proves which cue was actually played without logging any
        // audio or user content. Pro builds compile this to a no-op.
        PolicyFlowTrace.event("voice.play", "cue" to cue.name)
        stopCurrent(context)
        val media = runCatching { MediaPlayer.create(context, resource) }.getOrNull() ?: return
        player = media
        media.setVolume(0.8f, 0.8f)
        requestFocus(context)
        media.setOnCompletionListener { release(context) }
        media.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "voice playback error: $what/$extra")
            release(context)
            true
        }
        runCatching { media.start() }.onFailure {
            Log.w(TAG, "voice start failed", it)
            release(context)
        }
    }

    private fun resourceFor(cue: PolicyVoiceCue, context: Context): Int = when (cue) {
        PolicyVoiceCue.GRANT_READY -> R.raw.voice_grant_ready
        PolicyVoiceCue.START -> R.raw.voice_start
        PolicyVoiceCue.ONE_MINUTE -> R.raw.voice_60
        PolicyVoiceCue.THIRTY_SECONDS -> R.raw.voice_30
        PolicyVoiceCue.TWENTY_SECONDS -> R.raw.voice_20
        PolicyVoiceCue.TEN_SECONDS -> R.raw.voice_10
        PolicyVoiceCue.FIVE_SECONDS -> R.raw.voice_5
        PolicyVoiceCue.PAUSED -> rawResource(context, "voice_paused")
        PolicyVoiceCue.RESUMED -> rawResource(context, "voice_resumed")
        PolicyVoiceCue.TIME_UP -> R.raw.voice_time_up
        PolicyVoiceCue.PARENT_STOP -> R.raw.voice_parent_stop
        PolicyVoiceCue.CANCELED -> R.raw.voice_canceled
        PolicyVoiceCue.PIN_UPDATED -> R.raw.voice_pin_updated
        PolicyVoiceCue.TEMP_UNLOCK -> R.raw.voice_temp_unlock
        PolicyVoiceCue.GUARD_RESUMED -> R.raw.voice_guard_resumed
        PolicyVoiceCue.CLOUD_MODE_RULE -> R.raw.voice_mode_rule
        PolicyVoiceCue.CLOUD_MODE_REDEEM -> R.raw.voice_mode_redeem
        PolicyVoiceCue.REMOTE_ACTIVATION_AUTO -> rawResource(context, "voice_activation_auto")
        PolicyVoiceCue.REMOTE_ACTIVATION_MANUAL -> rawResource(context, "voice_activation_manual")
        PolicyVoiceCue.QUIZ_START -> nextClip("quiz_start", startClips)
        PolicyVoiceCue.QUIZ_CORRECT -> nextClip("quiz_correct", correctClips)
        PolicyVoiceCue.QUIZ_WRONG -> nextWrongClip()
        PolicyVoiceCue.QUIZ_TIMEOUT -> nextClip("quiz_timeout", timeoutClips)
        PolicyVoiceCue.QUIZ_SETTLE -> nextClip("quiz_settle", settleClips)
        PolicyVoiceCue.QUIZ_SETTLE_EMPTY -> R.raw.voice_quiz_settle_0
    }

    /**
     * A wrong answer usually draws from the six ordinary lines; occasionally it
     * plays one of the two idiom eggs instead. The egg bag is separate so an egg
     * does not consume a regular line from the rotation.
     */
    private fun nextWrongClip(): Int =
        if (Random.nextFloat() < WRONG_EGG_CHANCE) {
            nextClip("quiz_wrong_idiom", wrongIdiomClips)
        } else {
            nextClip("quiz_wrong", wrongClips)
        }

    /** Draws the next clip from a per-set shuffle bag, never repeating the boundary line. */
    private fun nextClip(key: String, variants: IntArray): Int {
        if (variants.isEmpty()) return 0
        if (variants.size == 1) return variants[0]
        val bag = bags.getOrPut(key) { mutableListOf() }
        if (bag.isEmpty()) {
            val next = variants.toMutableList()
            // Fisher-Yates; avoids relying on a stdlib array shuffle overload.
            for (i in next.lastIndex downTo 1) {
                val j = Random.nextInt(i + 1)
                val swap = next[i]
                next[i] = next[j]
                next[j] = swap
            }
            val previous = lastClip[key]
            if (previous != null && next.first() == previous) {
                val swapAt = next.indexOfFirst { it != previous }
                if (swapAt > 0) {
                    val first = next[0]
                    next[0] = next[swapAt]
                    next[swapAt] = first
                }
            }
            bag.addAll(next)
        }
        return bag.removeAt(0).also { lastClip[key] = it }
    }

    /**
     * The V11 clips are generated by tools/tts and may be absent from an in-progress build.
     * Resolve them at runtime so the cue still degrades to silence instead of failing to build.
     */
    private fun rawResource(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "raw", context.packageName)

    private fun stopCurrent(context: Context) {
        val media = player ?: return
        player = null
        runCatching { if (media.isPlaying) media.stop() }
        runCatching { media.release() }
        abandonFocus(context)
    }

    private fun release(context: Context) {
        val media = player ?: return
        player = null
        runCatching { media.release() }
        abandonFocus(context)
    }

    private fun requestFocus(context: Context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    private fun abandonFocus(context: Context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
    }
}
