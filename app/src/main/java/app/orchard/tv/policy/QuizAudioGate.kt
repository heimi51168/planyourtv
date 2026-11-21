package app.orchard.tv.policy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.orchard.tv.system.DiagLog
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the answering round's audio to itself.
 *
 * The start film is released as soon as the round begins, so any remaining background sound can
 * only come from another app. Holding one transient focus request for the whole round makes
 * players that follow focus pause instead of ducking under every TTS line, and [LockAudioGate]
 * keeps pausing players that stay active until their MediaSession is paused.
 *
 * STREAM_MUSIC is deliberately not muted. TV firmware often aliases every stream to
 * STREAM_MUSIC (Xiaomi MFTR0, Android 14), which would also silence the quiz TTS and sound
 * effects. Focus is released automatically by the platform if the process dies, so a crashed
 * quiz cannot leave the television silent.
 */
internal object QuizAudioGate {
    private const val TICK_MS = 750L
    private val active = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var focusRequest: AudioFocusRequest? = null
    private var legacyFocus = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= 0) DiagLog.i("quiz-audio: focus lost ($change)")
    }
    private val tick = object : Runnable {
        override fun run() {
            val context = app ?: return
            LockAudioGate.pauseOthersNow(context, "quiz-audio")
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** Main thread only. Safe to call again while a round is already holding the gate. */
    fun acquire(context: Context) {
        if (!active.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        app = appContext
        requestFocus(appContext)
        LockAudioGate.pauseOthersNow(appContext, "quiz-audio")
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
        DiagLog.i("quiz-audio: held")
    }

    /** Main thread only. Releasing leaves the TV's own focus and volume state untouched. */
    fun release() {
        if (!active.compareAndSet(true, false)) return
        handler.removeCallbacks(tick)
        val context = app
        app = null
        if (context != null) abandonFocus(context)
        DiagLog.i("quiz-audio: released")
    }

    fun isActive(): Boolean = active.get()

    private fun requestFocus(context: Context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            // Use this object's own listener rather than null: the legacy focus stack keys
            // requests by listener, and the voice players abandon their null request on exit.
            @Suppress("DEPRECATION")
            legacyFocus = manager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus(context: Context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else if (legacyFocus) {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusListener)
            legacyFocus = false
        }
    }
}