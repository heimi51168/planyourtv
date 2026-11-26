package app.orchard.tv.policy.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * V54 regression: a cached BGM file may be reused only when its cache key equals the track the
 * current plan selected. Reusing any usable file made a track switch look unchanged and kept
 * playing the previous song.
 */
class HostedAudioReuseTest {
    private fun plan(key: String): HostedAudioPlan = HostedAudioPlan(
        key = key,
        id = key.substringAfter("audio:").substringBeforeLast(":"),
        source = "official",
        name = "Track",
        nameEn = null,
        artist = null,
        license = null,
        url = "https://example.test/track.mp3",
        sha256 = "a".repeat(64),
        sizeBytes = 16L,
        durationMs = 1_000L,
        ext = "mp3",
        version = 1L,
    )

    private fun audio(key: String): HostedAudio = HostedAudio(
        key = key,
        id = key.substringAfter("audio:").substringBeforeLast(":"),
        source = "official",
        name = "Track",
        nameEn = null,
        artist = null,
        license = null,
        file = "files/track.mp3",
        sha256 = "a".repeat(64),
        sizeBytes = 16L,
        durationMs = 1_000L,
        ext = "mp3",
        version = 1L,
        createdAtMs = 1L,
    )

    @Test
    fun `same track is reusable`() {
        val key = "audio:official:playful:1"
        assertEquals(audio(key), reusableHostedAudio(plan(key), audio(key)))
    }

    @Test
    fun `switching track is not reusable`() {
        assertNull(
            reusableHostedAudio(
                plan("audio:official:owies-ukulele:1"),
                audio("audio:official:playful:1"),
            ),
        )
    }

    @Test
    fun `clearing the selection is not reusable`() {
        assertNull(reusableHostedAudio(null, audio("audio:official:playful:1")))
    }
}