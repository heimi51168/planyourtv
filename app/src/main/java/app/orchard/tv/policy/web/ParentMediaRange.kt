package app.orchard.tv.policy.web

import java.io.InputStream

/**
 * One HTTP Range decision for a media file. Browsers seek a <video> by asking for a byte range,
 * and mobile Safari refuses to play a film without range support, so the parent page's stream
 * endpoint answers 206 with exactly the bytes requested.
 */
internal sealed class ByteRange {
    /** No usable Range header: serve the whole file with 200. */
    data object Whole : ByteRange()

    /** A syntactically valid range outside the file: answer 416. */
    data object Unsatisfiable : ByteRange()

    data class Partial(val start: Long, val endInclusive: Long) : ByteRange() {
        val length: Long get() = endInclusive - start + 1L
    }
}

/**
 * Parses the single-range forms browsers send. Multiple ranges are ignored as RFC 9110 allows
 * (the caller then serves 200), and malformed input is ignored rather than guessed at.
 */
internal fun parseByteRange(header: String?, totalBytes: Long): ByteRange {
    if (totalBytes <= 0L) return ByteRange.Unsatisfiable
    val raw = header?.trim().orEmpty()
    if (!raw.startsWith("bytes=", ignoreCase = true)) return ByteRange.Whole
    val spec = raw.substring("bytes=".length).trim()
    if (spec.isEmpty() || spec.contains(',')) return ByteRange.Whole
    val dash = spec.indexOf('-')
    if (dash < 0) return ByteRange.Whole
    val firstText = spec.substring(0, dash).trim()
    val lastText = spec.substring(dash + 1).trim()

    if (firstText.isEmpty()) {
        // bytes=-N: the last N bytes.
        val suffix = lastText.toLongOrNull() ?: return ByteRange.Whole
        if (suffix <= 0L) return ByteRange.Unsatisfiable
        val start = (totalBytes - suffix).coerceAtLeast(0L)
        return ByteRange.Partial(start, totalBytes - 1L)
    }

    val first = firstText.toLongOrNull() ?: return ByteRange.Whole
    if (first < 0L) return ByteRange.Whole
    if (first >= totalBytes) return ByteRange.Unsatisfiable
    if (lastText.isEmpty()) return ByteRange.Partial(first, totalBytes - 1L)

    val last = lastText.toLongOrNull() ?: return ByteRange.Whole
    if (last < first) return ByteRange.Whole
    return ByteRange.Partial(first, last.coerceAtMost(totalBytes - 1L))
}

/** Reads at most [remaining] bytes from [source]; never reads past the declared response size. */
internal class BoundedInputStream(
    private val source: InputStream,
    private var remaining: Long,
) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0L) return -1
        val value = source.read()
        if (value >= 0) remaining-- else remaining = 0L
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0L) return -1
        if (len == 0) return 0
        val read = source.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (read > 0) remaining -= read else if (read < 0) remaining = 0L
        return read
    }

    override fun skip(n: Long): Long {
        val skipped = source.skip(minOf(n, remaining).coerceAtLeast(0L))
        if (skipped > 0L) remaining -= skipped
        return skipped
    }

    override fun available(): Int = minOf(source.available().toLong(), remaining).toInt()

    override fun close() = source.close()
}