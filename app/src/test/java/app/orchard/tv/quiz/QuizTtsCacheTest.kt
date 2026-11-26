package app.orchard.tv.quiz

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizTtsCacheTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        QuizTtsCache.install(context)
        QuizTtsCache.clear()
    }

    @After
    fun tearDown() {
        QuizTtsCache.clear()
    }

    @Test
    fun keysAreContentAddressedAndBounded() {
        val first = QuizTtsCache.keyFor("stem", "2+2=?")
        val same = QuizTtsCache.keyFor(QuizTtsCache.Part("stem", "2+2=?"))
        val otherPart = QuizTtsCache.keyFor("option0", "2+2=?")
        val otherText = QuizTtsCache.keyFor("stem", "3+3=?")

        assertEquals(first, same)
        assertEquals(64, first.length)
        assertNotEquals(first, otherPart)
        assertNotEquals(first, otherText)
    }

    @Test
    fun fileForRejectsMalformedOrEscapingKeys() {
        assertNull(QuizTtsCache.fileFor("../escape"))
        assertNull(QuizTtsCache.fileFor("a".repeat(63)))
        assertNull(QuizTtsCache.fileFor("A".repeat(64)))
        assertNull(QuizTtsCache.fileFor("g".repeat(64)))
        assertNull(QuizTtsCache.fileFor("0".repeat(65)))
    }

    @Test
    fun fileForOnlyReturnsExistingPlayableClips() {
        val key = QuizTtsCache.keyFor("stem", "2+2=?")
        val dir = File(context.filesDir, "quiz-tts")
        assertTrue(dir.isDirectory)
        assertNull(QuizTtsCache.fileFor(key))

        val durationTag = byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0)
        val file = File(dir, "$key.mp3")
        file.writeBytes(durationTag)
        assertTrue(QuizTtsCache.fileFor(key)?.isFile == true)

        QuizTtsCache.clear()
        assertNull(QuizTtsCache.fileFor(key))
        assertFalse(file.exists())
    }

    @Test
    fun onlyHttpsMp3UrlsAreAllowed() {
        assertTrue(
            QuizTtsCache.isAllowedAudioUrl(
                "https://cdn.example.com/qb_tts/a.mp3",
            ),
        )
        assertTrue(
            QuizTtsCache.isAllowedAudioUrl(
                "https://cdn.example.com/qb_tts/A.MP3",
            ),
        )
        listOf(
            "",
            "http://example.com/a.mp3",
            "https://user:pass@example.com/a.mp3",
            "https://example.com/a.wav",
            "https://example.com/",
            "not-a-url",
        ).forEach { bad ->
            assertFalse(bad, QuizTtsCache.isAllowedAudioUrl(bad))
        }
    }

    @Test
    fun mp3ValidationAcceptsId3AndFrameHeaders() {
        QuizTtsCache.validateMp3(byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0))
        QuizTtsCache.validateMp3(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte()))

        assertThrows(IOException::class.java) {
            QuizTtsCache.validateMp3(byteArrayOf(0x49, 0x44))
        }
        assertThrows(IOException::class.java) {
            QuizTtsCache.validateMp3("not mp3".toByteArray())
        }
    }

    @Test
    fun pruneKeepsTheProtectedClipAndRemovesTheOldestOthers() {
        val key = QuizTtsCache.keyFor("stem", "2+2=?")
        val keep = QuizTtsCache.keyFor("option0", "3")
        val dir = File(context.filesDir, "quiz-tts")
        val clip = byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0)
        val protectedFile = File(dir, "$key.mp3").apply { writeBytes(clip) }
        val oldFile = File(dir, "$keep.mp3").apply {
            writeBytes(clip)
            setLastModified(1_000L)
        }

        QuizTtsCache.prune(protectedKeys = setOf(key), maxBytes = 1L)

        assertTrue(protectedFile.exists())
        assertFalse(oldFile.exists())
    }

    @Test
    fun sha256MatchesTheJavaDigestOfTheSameBytes() {
        val bytes = "quiz".toByteArray(Charsets.UTF_8)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, QuizTtsCache.sha256(bytes))
        assertEquals(QuizTtsCache.sha256(bytes), QuizTtsCache.sha256(bytes.copyOf()))
    }
}
