package app.orchard.tv.policy

import android.os.SystemClock

/**
 * The study app opened from the lock desktop's grid, while the lock screen stays behind it.
 *
 * The device-level lock refuses every launch. The historical dock path entered STUDY_MODE to pass
 * that gate, which replaced the lock with the old study desktop and brought back the standalone
 * "exit study mode" entry. Instead a dock tile grants exactly its listed package: [consume] lets
 * that one launch through MainActivity's gate, and [allowsForeground] tells the Home guard to
 * leave the same app in front while the lock is still the state behind it. Nothing else
 * changes: the guard stays ENFORCING, Back/Home comes back to the lock screen, and every other
 * package is still refused.
 *
 * The pending grant is one-shot and expires, so a launch that never arrives cannot authorize a
 * later one. The foreground allowance ends when Aerial is in front again ([clearForeground]).
 */
internal object LockStudyLaunch {
    private const val TtlMs = 5_000L
    private var pending: String? = null
    private var issuedAt = 0L
    private var inFront: String? = null

    /** A lock desktop tile: the next launch may be this package, and it may stay in front. */
    fun grant(pkg: String) {
        pending = pkg
        issuedAt = SystemClock.uptimeMillis()
    }

    /** The launch gate: one shot, clearing the pending grant whatever the package. */
    fun consume(pkg: String): Boolean {
        val granted = pending
        val fresh = SystemClock.uptimeMillis() - issuedAt <= TtlMs
        pending = null
        if (!fresh || granted != pkg) return false
        inFront = pkg
        return true
    }

    /** The guard's lock check: this is the study app the child opened from the lock desktop. */
    fun allowsForeground(pkg: String): Boolean = pkg == inFront

    /** Aerial is in front again: the study app is no longer the lock's foreground. */
    fun clearForeground() {
        inFront = null
    }
}
