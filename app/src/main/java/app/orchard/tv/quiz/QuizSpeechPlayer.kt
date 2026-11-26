package app.orchard.tv.quiz

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.orchard.tv.policy.PolicyVoicePlayer
import app.orchard.tv.policy.QuizAudioGate
import com.planyourtv.policy.quiz.QuizItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Reads a quiz question or a wrong-answer result.
 *
 * Local rounds keep the existing DeepSeek path: raw PCM through [AudioTrack]
 * instead of a WAV file through MediaPlayer, because some TV firmware hands
 * streamed WAV files to a vendor player that reports an empty stream.
 *
 * Cloud rounds download QB-generated MP3 clips into [QuizTtsCache] and play the
 * local files. Playback therefore does not touch the network, and a clip cached
 * on the first hearing replays offline. The first clip starts as soon as it is
 * ready while the producer downloads the next part.
 *
 * Only one utterance is allowed at a time. A new question, an answer press, or
 * leaving the quiz bumps [generation], so an in-flight request cannot start
 * speaking after the child has moved on. Failures are intentionally quiet:
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
    private var mediaPlayer: MediaPlayer? = null
    private var cloudJob: Job? = null
    private var appContext: Context? = null
    private var focusRequest: AudioFocusRequest? = null
    private var focusHeld = false

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
        if (isCloudItem(item)) {
            val attemptUid = cloudAttempt(item)
            val parts = if (attemptUid == null) {
                emptyList()
            } else {
                QuizCloudSpeechParts.question(item, settings)
            }
            if (attemptUid == null || parts.isEmpty()) {
                stop()
            } else {
                startCloud(context.applicationContext, attemptUid, item.questionId, parts)
            }
            return
        }
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
        if (isCloudItem(item)) {
            val attemptUid = cloudAttempt(item)
            val parts = if (attemptUid == null) {
                emptyList()
            } else {
                QuizCloudSpeechParts.answer(item, settings)
            }
            if (attemptUid == null || parts.isEmpty()) {
                stop()
            } else {
                startCloud(context.applicationContext, attemptUid, item.questionId, parts)
            }
            return
        }
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

    /**
     * `qb_` is assigned only to QB questions, so it also selects the cloud
     * branch. A cloud item with no matching cloud session must stay silent:
     * falling through to DeepSeek would upload QB question text to a third
     * party.
     */
    private fun isCloudItem(item: QuizItem): Boolean =
        item.questionId.startsWith(QbClient.QUESTION_ID_PREFIX)

    /**
     * The attempt UID for a cloud question, or null when the current session
     * cannot prove which attempt it belongs to.
     */
    private fun cloudAttempt(item: QuizItem): String? {
        if (!isCloudItem(item)) return null
        val session = QuizSessionStore.loadSession() ?: return null
        if (session.source != QbClient.CLOUD_SESSION_SOURCE) return null
        return QuizCloudOutbox.attemptUidOf(session.id)
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

    private fun startCloud(
        context: Context,
        attemptUid: String,
        questionId: String,
        parts: List<QuizTtsCache.Part>,
    ) {
        stopAudio()
        val request = generation.incrementAndGet()
        appContext = context
        QuizTtsCache.install(context)
        val protectedKeys = parts.map { QuizTtsCache.keyFor(it) }.toSet()
        val job = scope.launch {
            val channel = Channel<File>(capacity = 1)
            val producer = launch(Dispatchers.IO) {
                try {
                    for (part in parts) {
                        if (request != generation.get()) break
                        val file = QuizTtsCache.ensure(
                            attemptUid,
                            questionId,
                            part,
                            protectedKeys,
                        )
                        if (request != generation.get()) break
                        if (file == null) break
                        channel.send(file)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "tts fetch failed: ${e.javaClass.simpleName}")
                } finally {
                    channel.close()
                }
            }
            var hasFocus = false
            try {
                for (file in channel) {
                    if (request != generation.get()) break
                    if (!waitForCue(request)) break
                    if (!hasFocus) {
                        hasFocus = requestFocus(context)
                    }
                    playMp3(file, request)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "tts playback failed: ${e.javaClass.simpleName}")
            } finally {
                producer.cancel()
                channel.cancel()
                if (hasFocus && request == generation.get()) abandonFocus(context)
            }
        }
        cloudJob = job
        job.invokeOnCompletion {
            main.post { if (cloudJob === job) cloudJob = null }
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

    private suspend fun playMp3(file: File, request: Long) {
        val player = MediaPlayer()
        val released = AtomicBoolean(false)
        fun release() {
            if (!released.compareAndSet(false, true)) return
            if (mediaPlayer === player) mediaPlayer = null
            runCatching { player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        mediaPlayer = player
        try {
            player.setAudioAttributes(attributes)
            player.setDataSource(file.absolutePath)
            suspendCancellableCoroutine<Unit> { continuation ->
                player.setOnPreparedListener { prepared ->
                    if (request != generation.get()) {
                        continuation.cancel()
                    } else {
                        try {
                            prepared.start()
                        } catch (e: Exception) {
                            Log.w(TAG, "mp3 start failed: ${e.javaClass.simpleName}")
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                }
                player.setOnCompletionListener {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                player.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "mp3 error: $what/$extra")
                    if (continuation.isActive) continuation.resume(Unit)
                    true
                }
                continuation.invokeOnCancellation {
                    // MediaPlayer calls must run on the main thread.
                    main.post { release() }
                }
                player.prepareAsync()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "mp3 play failed: ${e.javaClass.simpleName}")
        } finally {
            release()
        }
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
        cloudJob?.cancel()
        cloudJob = null
        // The playback coroutine owns the final release. Stopping here makes
        // cancellation immediate even before that coroutine resumes.
        runCatching { mediaPlayer?.stop() }
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

    private fun requestFocus(context: Context): Boolean {
        // The round gate already holds a stronger focus. A second request would replace it
        // (and abandoning it at the end of the utterance would leave the round unprotected).
        if (QuizAudioGate.isActive() || focusHeld) return false
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { }
                .build()
            val granted = manager.requestAudioFocus(request) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!granted) return false
            focusRequest = request
            focusHeld = true
        } else {
            @Suppress("DEPRECATION")
            val granted = manager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!granted) return false
            focusHeld = true
        }
        return true
    }

    /** Only release focus this player actually obtained; the round gate owns its own. */
    private fun abandonFocus(context: Context) {
        if (!focusHeld) return
        focusHeld = false
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
