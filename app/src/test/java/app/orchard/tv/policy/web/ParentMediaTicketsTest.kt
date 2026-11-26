package app.orchard.tv.policy.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Playback tickets let a header-less <video> prove the editor lease without putting that
 * lease in a URL. Grants are memory-only and must die with their editor.
 */
class ParentMediaTicketsTest {
    private val now = 1_000_000L
    private val mediaId = "a".repeat(32)

    @Test
    fun ticketIsBoundToItsMediaId() {
        val ticket = ParentMediaTickets.mint("editor-1", mediaId, now)
        assertTrue(ParentMediaTickets.check(ticket, mediaId, now + 1_000L))
        assertFalse(ParentMediaTickets.check(ticket, "b".repeat(32), now + 1_000L))
    }

    @Test
    fun streamKindsCannotCrossBetweenVideoAndAudio() {
        val video = ParentMediaTickets.mint("editor-1", mediaId, now,
            kind = ParentMediaTickets.KIND_MEDIA)
        val audio = ParentMediaTickets.mint("editor-1", mediaId, now,
            kind = ParentMediaTickets.KIND_AUDIO)

        assertTrue(ParentMediaTickets.check(video, mediaId, now,
            kind = ParentMediaTickets.KIND_MEDIA))
        assertTrue(ParentMediaTickets.check(audio, mediaId, now,
            kind = ParentMediaTickets.KIND_AUDIO))
        // A 32-hex id may name both libraries; the grant must never cross that boundary.
        assertFalse(ParentMediaTickets.check(video, mediaId, now,
            kind = ParentMediaTickets.KIND_AUDIO))
        assertFalse(ParentMediaTickets.check(audio, mediaId, now,
            kind = ParentMediaTickets.KIND_MEDIA))
    }

    @Test
    fun emptyTokensAreRejected() {
        assertFalse(ParentMediaTickets.check(null, mediaId, now))
        assertFalse(ParentMediaTickets.check("", mediaId, now))
        assertFalse(ParentMediaTickets.check("unknown", mediaId, now))
    }

    @Test
    fun ticketsAreRandom() {
        val first = ParentMediaTickets.mint("editor-1", mediaId, now)
        val second = ParentMediaTickets.mint("editor-1", mediaId, now)
        assertNotEquals(first, second)
        assertTrue(ParentMediaTickets.check(first, mediaId, now))
        assertTrue(ParentMediaTickets.check(second, mediaId, now))
    }

    @Test
    fun expiryEndsTheStream() {
        val ticket = ParentMediaTickets.mint("editor-1", mediaId, now)
        assertFalse(ParentMediaTickets.check(ticket, mediaId, now + 60 * 60 * 1000L))
        // The expired grant is dropped, so it cannot be revived by an older timestamp.
        assertFalse(ParentMediaTickets.check(ticket, mediaId, now))
    }

    @Test
    fun releasingOneEditorKeepsTheOtherEditorsGrants() {
        val first = ParentMediaTickets.mint("editor-1", mediaId, now)
        val second = ParentMediaTickets.mint("editor-2", mediaId, now)
        ParentMediaTickets.revokeEditor("editor-1")
        assertFalse(ParentMediaTickets.check(first, mediaId, now))
        assertTrue(ParentMediaTickets.check(second, mediaId, now))
        ParentMediaTickets.revokeEditor("editor-2")
        assertFalse(ParentMediaTickets.check(second, mediaId, now))
    }

    @Test
    fun aNewEditorTakeoverRevokesEveryStream() {
        val first = ParentMediaTickets.mint("editor-1", mediaId, now)
        val second = ParentMediaTickets.mint("editor-2", mediaId, now)
        ParentMediaTickets.revokeAll()
        assertFalse(ParentMediaTickets.check(first, mediaId, now))
        assertFalse(ParentMediaTickets.check(second, mediaId, now))
    }
}