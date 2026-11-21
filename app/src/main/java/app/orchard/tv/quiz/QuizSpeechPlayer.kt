package app.orchard.tv.quiz

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.orchard.tv.policy.PolicyVoicePlayer
import app.orchard.tv.policy.QuizAudioGate
import com.planyourtv.policy.quiz.QuizItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Reads a quiz question or a wrong-answer result through DeepSeek TTS.
 *
 * The speech endpoint is asked for raw PCM and played through [AudioTrack]
 * instead of a WAV file through MediaPlayer: some TV firmware hands streamed
 * WAV files to a vendor player that reports an empty stream. Raw PCM has no
 * container for those players to misread.
 *
 * Only one utterance is allowed at a time. A new question, an answer press, or
 * leaving the quiz bumps [generation], so an in-flight network request cannot
 * start speaking after the child has moved on. Failures are intentionally quiet:
 * voice is an aid, never a gate on answering.
 */
object QuizSpeechPlayer {
    private const val TAG = "QuizSpeech"
    private const val CUE_WAIT_MS = 2_500L
    private const val WRITE_CHUNK_BYTES = 8 * 1024
    private const val TRACK_BUFFER_BYTES = 16 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0)
    private var track: AudioTrack? = null
    private var appContext: Context? = null
    private var focusRequest: AudioFocusRequest? = null

    private val attributes: AudioAttributes
        get() = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    fun speakQuestion(context: Context, item: QuizItem) {
        if (Looper.myLooper() != main.looper) {
            main.post { speakQuestion(context, item) }
            return
        }
        val settings = QuizSessionStore.loadSpeechSettings()
        val text = if (settings.active) QuizSpeechText.question(item, settings) else null
        if (text == null || !deepSeekConfigured()) {
            stop()
            return
        }
        start(context.applicationContext, text)
    }

    /** Called only after a wrong or timed-out answer; never during the question. */
    fun speakAnswer(context: Context, item: QuizItem) {
        if (Looper.myLooper() != main.looper) {
            main.post { speakAnswer(context, item) }
            return
        }
        val settings = QuizSessionStore.loadSpeechSettings()
        val text = if (settings.active) QuizSpeechText.answer(item, settings) else null
        if (text == null || !deepSeekConfigured()) {
            stop()
            return
        }
        start(context.applicationContext, text)
    }

    /** Cancels synthesis or playback immediately. Safe to call repeatedly. */
    fun stop() {
        generation.incrementAndGet()
        if (Looper.myLooper() == main.looper) stopAudio() else main.post { stopAudio() }
    }

    private fun deepSeekConfigured(): Boolean =
        QuizProviderMeta.isOfficialDeepSeek(QuizSessionStore.loadProviderMeta().baseUrl)

    private fun start(context: Context, text: String) {
        stopAudio()
        val request = generation.incrementAndGet()
        appContext = context
        scope.launch {
            // The existing quiz cue ("correct", "wrong", ...) is short but uses
            // the same speaker. Let it finish instead of stacking two voices.
            if (!waitForCue(request)) return@launch

            val key = withContext(Dispatchers.IO) { QuizSessionStore.providerKey() } ?: return@launch
            if (request != generation.get()) return@launch
            val pcm = try {
                withContext(Dispatchers.IO) { DeepSeekTtsClient.synthesize(key, text) }
            } catch (e: Exception) {
                // Never log the text, answer or key. The class/code is enough to diagnose.
                Log.w(TAG, "synth failed: ${e.javaClass.simpleName}${e.message?.take(32)?.let { " $it" }.orEmpty()}")
                return@launch
            }
            if (request != generation.get()) return@launch
            // A cue can also begin while the audio is being synthesized.
            if (!waitForCue(request)) return@launch
            playPcm(context, pcm, request)
        }
    }

    /** Waits for a generated cue, but never past the cap; false means a newer request won. */
    private suspend fun waitForCue(request: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + CUE_WAIT_MS
        while (PolicyVoicePlayer.isPlaying() && SystemClock.elapsedRealtime() < deadline) {
            delay(40)
        }
        return request == generation.get()
    }

    private fun playPcm(context: Context, pcm: ByteArray, request: Long) {
        if (request != generation.get()) return
        val minBuffer = AudioTrack.getMinBufferSize(
            DeepSeekTtsClient.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "pcm unsupported: $minBuffer")
            return
        }
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(DeepSeekTtsClient.SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val audio = try {
            AudioTrack(
                attributes,
                format,
                maxOf(minBuffer, TRACK_BUFFER_BYTES),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        } catch (e: Exception) {
            Log.w(TAG, "track create failed: ${e.javaClass.simpleName}")
            return
        }
        if (audio.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG, "track not initialized: ${audio.state}")
            runCatching { audio.release() }
            return
        }
        track = audio
        requestFocus(context)
        try {
            audio.play()
        } catch (e: Exception) {
            Log.w(TAG, "track play failed: ${e.javaClass.simpleName}")
            finish(audio)
            return
        }
        scope.launch(Dispatchers.IO) {
            val complete = writePcm(audio, pcm, request)
            if (complete) waitForDrain(audio, pcm.size, request)
            withContext(Dispatchers.Main.immediate) {
                if (request == generation.get()) {
                    finish(audio)
                } else {
                    // stop() already paused and detached this track; the write
                    // coroutine owns the final release so release cannot race
                    // a blocking write.
                    runCatching { audio.release() }
                }
            }
        }
    }

    private fun writePcm(audio: AudioTrack, pcm: ByteArray, request: Long): Boolean {
        var offset = 0
        while (offset < pcm.size) {
            if (request != generation.get()) return false
            val count = minOf(WRITE_CHUNK_BYTES, pcm.size - offset)
            val written = try {
                audio.write(pcm, offset, count)
            } catch (e: Exception) {
                Log.w(TAG, "pcm write failed: ${e.javaClass.simpleName}")
                return false
            }
            if (written <= 0) {
                Log.w(TAG, "pcm write stopped: $written")
                return false
            }
            offset += written
        }
        return true
    }

    private suspend fun waitForDrain(audio: AudioTrack, bytes: Int, request: Long) {
        val totalFrames = bytes / DeepSeekTtsClient.BYTES_PER_SAMPLE
        val durationMs = totalFrames * 1000L / DeepSeekTtsClient.SAMPLE_RATE_HZ
        val deadline = SystemClock.elapsedRealtime() + durationMs + 3_000L
        while (request == generation.get() && SystemClock.elapsedRealtime() < deadline) {
            val head = runCatching { audio.playbackHeadPosition }.getOrDefault(0)
            if (head >= totalFrames) return
            delay(50)
        }
    }

    private fun finish(audio: AudioTrack) {
        if (track !== audio) return
        track = null
        runCatching { audio.stop() }
        runCatching { audio.release() }
        appContext?.let { abandonFocus(it) }
    }

    private fun stopAudio() {
        val audio = track
        track = null
        audio?.let {
            // Do not release here: a write may be blocked in the playback
            // coroutine, which releases the track after it wakes up.
            runCatching { it.pause() }
            runCatching { it.flush() }
        }
        appContext?.let { abandonFocus(it) }
    }

    private fun requestFocus(context: Context) {
        // The round gate already holds a stronger focus. A second request would replace it
        // (and abandoning it at the end of the utterance would leave the round unprotected).
        if (QuizAudioGate.isActive()) return
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
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
