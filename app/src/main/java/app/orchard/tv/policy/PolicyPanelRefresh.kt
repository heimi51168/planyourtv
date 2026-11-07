package app.orchard.tv.policy

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.orchard.tv.guard.HomeGuardService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Puts a remote AUTO/MANUAL switch or rule edit on the lock screen's panel while the TV is idle.
 *
 * The state and the voice cue switch at once, but an idle TV keeps scanning out the frame from
 * before the switch until a key arrives (MiTV-MFTR0, 2026-11-07: AUTO applied and was announced
 * at 00:16:09; at 00:17:45 the panel still showed MANUAL and the 00:16 clock. An injected DPAD at
 * 00:20:41 refreshed it to AUTO and 00:20). HomeGuard can inject BACK through its accessibility
 * service; that is the input the power service takes as user activity and dismisses the standby
 * with (`PowerManagerService: resetStandbyAction`). The lock screen consumes BACK in its normal
 * state, so nothing else changes there. [revision] then draws one more frame of the
 * already-updated composition on the awake panel.
 *
 * The panel freezes again within about half a minute: MANUAL applied at 00:38:13 and was awake
 * at 00:38:28, but AUTO at 00:38:47 did not present. That switch was suppressed because the
 * injected BACK itself looked like a recent key; keys this object injects are now ignored.
 *
 * A rule edit needs the same nudge: its windows, rest and buffer rows are new content on the
 * lock screen, and without an input the idle panel keeps showing the previous "available now"
 * state. The debounce keeps one config apply that changes both activation and rules to one BACK.
 */
object PolicyPanelRefresh {
    /**
     * A key this recently means a viewer is at the TV and the frame is still fresh; don't add a
     * BACK under their thumb. The panel froze 15-34 s after the 00:38:13 key, so a minute would
     * skip switches on an idle TV.
     */
    private const val RECENT_KEY_MS = 10_000L
    /** The BACK returns through dispatchKeyEvent; that echo must not count as a viewer's key. */
    private const val OWN_KEY_MS = 2_000L
    /** One config apply may change both activation and rules; don't inject a second BACK. */
    private const val NUDGE_GAP_MS = 1_500L
    /** Let the injected BACK reach the power service before drawing the frame it made visible. */
    private const val REDRAW_MS = 500L

    private val main = Handler(Looper.getMainLooper())
    private val _revision = MutableStateFlow(0)

    /** Read by the lock screen. A change draws one frame without changing what it shows. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Uptime of the last real key that reached Aerial; 0 until the first one. */
    @Volatile
    private var lastKeyAt = 0L

    /** When the last BACK was injected, in uptime. */
    @Volatile
    private var nudgedAt = 0L

    /** A PIN pad, quiz or prompt is up: BACK would dismiss it instead of only waking the panel. */
    @Volatile
    private var overlayOpen = false

    /** MainActivity calls this for every key that reaches the app. */
    fun noteKey() {
        val now = SystemClock.uptimeMillis()
        if (now - nudgedAt < OWN_KEY_MS) return
        lastKeyAt = now
    }

    /** The lock screen calls this when a flow that BACK would close opens or closes. */
    fun setOverlayOpen(open: Boolean) {
        overlayOpen = open
    }

    fun afterRemoteChange() {
        // The lock screen is the only screen this is for; another app must not get a BACK.
        if (!HomeGuardService.isAerialResumed()) return
        if (overlayOpen) return
        val now = SystemClock.uptimeMillis()
        if (now - lastKeyAt < RECENT_KEY_MS) return
        if (now - nudgedAt < NUDGE_GAP_MS) return
        nudgedAt = now
        HomeGuardService.nudgePanel()
        main.postDelayed({ _revision.value++ }, REDRAW_MS)
    }
}
