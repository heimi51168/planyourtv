package app.orchard.tv.policy.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Range handling for the parent page's local video stream. Mobile Safari refuses to play a
 * film unless the server answers byte ranges correctly, so each browser form is pinned here.
 */
class ParentMediaRangeTest {
    @Test
    fun missingHeaderServesTheWholeFile() {
        assertEquals(ByteRange.Whole, parseByteRange(null, 1_000L))
        assertEquals(ByteRange.Whole, parseByteRange("", 1_000L))
        assertEquals(ByteRange.Whole, parseByteRange("items=0-9", 1_000L))
    }

    @Test
    fun openEndedRangeRunsToTheEnd() {
        assertEquals(ByteRange.Partial(0L, 999L), parseByteRange("bytes=0-", 1_000L))
        assertEquals(ByteRange.Partial(400L, 999L), parseByteRange("bytes=400-", 1_000L))
    }

    @Test
    fun closedRangeIsServedExactly() {
        val range = parseByteRange("bytes=100-199", 1_000L) as ByteRange.Partial
        assertEquals(100L, range.start)
        assertEquals(199L, range.endInclusive)
        assertEquals(100L, range.length)
    }

    @Test
    fun suffixRangeServesTheTail() {
        assertEquals(ByteRange.Partial(900L, 999L), parseByteRange("bytes=-100", 1_000L))
        assertEquals(ByteRange.Partial(0L, 999L), parseByteRange("bytes=-5000", 1_000L))
    }

    @Test
    fun endPastTheFileIsClamped() {
        assertEquals(ByteRange.Partial(900L, 999L), parseByteRange("bytes=900-5000", 1_000L))
    }

    @Test
    fun startPastTheFileIsUnsatisfiable() {
        assertEquals(ByteRange.Unsatisfiable, parseByteRange("bytes=1000-", 1_000L))
        assertEquals(ByteRange.Unsatisfiable, parseByteRange("bytes=2000-3000", 1_000L))
        assertEquals(ByteRange.Unsatisfiable, parseByteRange("bytes=-0", 1_000L))
        assertEquals(ByteRange.Unsatisfiable, parseByteRange(null, 0L))
    }

    @Test
    fun malformedOrMultiRangeIsIgnored() {
        assertEquals(ByteRange.Whole, parseByteRange("bytes=0-10,20-30", 1_000L))
        assertEquals(ByteRange.Whole, parseByteRange("bytes=abc-def", 1_000L))
        assertEquals(ByteRange.Whole, parseByteRange("bytes=200-100", 1_000L))
        assertEquals(ByteRange.Whole, parseByteRange("bytes=10", 1_000L))
    }

    @Test
    fun boundedStreamNeverReadsPastTheRange() {
        val source = "0123456789".byteInputStream()
        val bounded = BoundedInputStream(source, 4L)
        val buffer = ByteArray(8)
        val read = bounded.read(buffer, 0, buffer.size)
        assertEquals(4, read)
        assertEquals("0123", String(buffer, 0, read))
        assertEquals(-1, bounded.read())
        assertTrue(bounded.skip(10L) == 0L)
    }
}