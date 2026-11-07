package app.orchard.tv.ui.policy

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import app.orchard.tv.R

/**
 * Short game sounds for the quiz. The voice clips stay the main channel: these
 * use [AudioAttributes.USAGE_GAME] so they do not request audio focus, and play
 * at a lower volume under [app.orchard.tv.policy.PolicyVoicePlayer].
 *
 * One [SoundPool] is created when the quiz opens and released when it closes;
 * loading four small WAVs takes a fraction of the start screen's video time.
 * The same event within [DEBOUNCE_MS] is ignored, so a child holding OK cannot
 * stack a sound storm.
 */
internal object QuizSfx {
    enum class Event { CORRECT, WRONG, TIMEOUT, SETTLE }

    private const val DEBOUNCE_MS = 80L

    private var context: Context? = null
    private var pool: SoundPool? = null
    private val sampleIds = mutableMapOf<Event, Int>()
    private var lastEvent: Event? = null
    private var lastPlayedAt = 0L

    fun install(context: Context) {
        val app = context.applicationContext
        if (pool != null && this.context === app) return
        release()
        this.context = app
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(attributes)
            .build()
        sampleIds[Event.CORRECT] = soundPool.load(app, R.raw.sfx_quiz_correct, 1)
        sampleIds[Event.WRONG] = soundPool.load(app, R.raw.sfx_quiz_wrong, 1)
        sampleIds[Event.TIMEOUT] = soundPool.load(app, R.raw.sfx_quiz_timeout, 1)
        sampleIds[Event.SETTLE] = soundPool.load(app, R.raw.sfx_quiz_settle, 1)
        pool = soundPool
    }

    fun play(event: Event) {
        val soundPool = pool ?: return
        val now = SystemClock.uptimeMillis()
        if (event == lastEvent && now - lastPlayedAt < DEBOUNCE_MS) return
        lastEvent = event
        lastPlayedAt = now
        val id = sampleIds[event] ?: return
        if (id == 0) return
        val volume = when (event) {
            // A short coin ding carries the gold +N min reward; the voice stays louder.
            Event.CORRECT -> 0.50f
            // The report film carries its own BGM under the settlement voice.
            Event.SETTLE -> 0.32f
            Event.WRONG, Event.TIMEOUT -> 0.38f
        }
        soundPool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool?.release()
        pool = null
        context = null
        sampleIds.clear()
        lastEvent = null
        lastPlayedAt = 0L
    }
}
