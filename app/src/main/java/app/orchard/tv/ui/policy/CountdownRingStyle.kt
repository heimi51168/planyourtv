package app.orchard.tv.ui.policy

/** The three urgency levels shown by the status-pill countdown ring. */
internal enum class CountdownTone {
    GREEN,
    ORANGE,
    RED,
}

/** Matches the one-minute warning and the floating ball's final-30-second window. */
internal fun countdownTone(remainingSeconds: Long): CountdownTone = when {
    remainingSeconds > 60L -> CountdownTone.GREEN
    remainingSeconds > 30L -> CountdownTone.ORANGE
    else -> CountdownTone.RED
}

/**
 * The ring is a session-progress ring until the last minute, then a visible
 * one-minute gauge. Using the session total at 30 seconds would leave a sliver
 * too small to read on a long session.
 */
internal fun countdownProgress(remainingSeconds: Long, totalSeconds: Long): Float {
    val total = totalSeconds.coerceAtLeast(0L)
    if (total <= 0L) return 0f
    val remaining = remainingSeconds.coerceIn(0L, total)
    if (remaining > 60L) return (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val window = minOf(60L, total)
    return (remaining.toFloat() / window.toFloat()).coerceIn(0f, 1f)
}
