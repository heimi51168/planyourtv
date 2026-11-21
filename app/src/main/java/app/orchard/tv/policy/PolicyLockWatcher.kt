package app.orchard.tv.policy

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import app.orchard.tv.guard.GuardSetup
import app.orchard.tv.guard.HomeGuardService
import app.orchard.tv.guard.UsageFront
import app.orchard.tv.system.DiagLog
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.shouldBringHomeWithoutGuard
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the policy lock surface in front where Home Guard cannot run.
 *
 * A guard policy edge already fronts Aerial from HomeGuardService; this watchdog only acts
 * while no guard is bound. It also catches a child opening another app after the lock is up,
 * and resets as soon as Aerial is in front or the parent unlocks.
 *
 * The guard exempts the lock desktop's own study/unlimited app; this watch has to do the same
 * or it pulls Aerial over the app a tick after launch and pauses the focus round. Usage stats
 * name that foreground when they may be read (GET_USAGE_STATS), which is the no-guard case.
 */
internal object PolicyLockWatcher {
    private const val TAG = "PolicyLockWatcher"
    private const val TICK_MS = 1_000L
    private const val BRING_COOLDOWN_MS = 2_500L
    /** The first read goes back far enough to find an app that was already in front. */
    private const val USAGE_LOOKBACK_MS = 60_000L
    private const val USAGE_OVERLAP_MS = 2_000L
    private const val USAGE_GAP_MS = 30_000L
    private const val ACTIVITY_SETTLE_MS = 1_000L

    private val started = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var app: Context? = null
    private var lastAttemptAt = 0L
    private val usagePicker = UsageFront.Picker()
    private var lastUsageAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun install(context: Context) {
        if (!started.compareAndSet(false, true)) return
        app = context.applicationContext
        handler.postDelayed(tick, TICK_MS)
    }

    private fun check() {
        val context = app ?: return
        // Aerial in front or a parent unlock means there is nothing to reclaim.
        if (!PolicyModeCoordinator.isLockScreen() || HomeGuardService.isAerialResumed()) {
            lastAttemptAt = 0L
            return
        }
        val lockOwnedAppInFront = lockOwnedAppInFront(context)
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val now = SystemClock.uptimeMillis()
        if (!shouldBringHomeWithoutGuard(
                lockScreen = true,
                guardRunning = HomeGuardService.isRunning,
                aerialInFront = false,
                lockOwnedAppInFront = lockOwnedAppInFront,
                screenInteractive = power?.isInteractive == true,
                userUnlocked = GuardSetup.isUserUnlocked(context),
                nowMs = now,
                lastAttemptMs = lastAttemptAt,
                cooldownMs = BRING_COOLDOWN_MS,
            )
        ) {
            // The allowed app took the screen back from a pending reclaim: the next different
            // app must be reclaimed at once, not after the old cooldown.
            if (lockOwnedAppInFront) lastAttemptAt = 0L
            return
        }
        lastAttemptAt = now
        DiagLog.i("$TAG: no guard; bringing the lock screen forward")
        PolicyRuntime.bringHome()
    }

    /**
     * The guard's own exception, read from usage stats here: the lock desktop's study app, an
     * app with a one-shot lock permit, or an unlimited app may stay in front. Without usage
     * stats, or before the first event arrives, an active lock permit gets the benefit of the
     * doubt: reclaiming it would break the only way into the study app on this TV.
     */
    private fun lockOwnedAppInFront(context: Context): Boolean {
        if (!LockLaunchPermit.hasForegroundAllowance()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 ||
            !HomeGuardService.usageStatsAllowed(context)
        ) {
            return true
        }
        val front = frontByUsage(context) ?: return true
        if (front.closed) return false
        // Aerial may still be the last named activity while the launched app is coming up.
        if (front.pkg == context.packageName) return true
        return LockLaunchPermit.allowsForeground(front.pkg) ||
            front.pkg in PolicyModeCoordinator.state.value.unlimitedPackages
    }

    /** The last activity moved to the front, kept across ticks so a still app stays named. */
    private fun frontByUsage(context: Context): UsageFront.Front? = runCatching {
        val now = System.currentTimeMillis()
        val from = if (lastUsageAt == 0L || now - lastUsageAt > USAGE_GAP_MS) {
            now - USAGE_LOOKBACK_MS
        } else {
            lastUsageAt - USAGE_OVERLAP_MS
        }
        lastUsageAt = now
        val events = (context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager)
            ?.queryEvents(from, now) ?: return null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            usagePicker.add(event.eventType, event.packageName, event.className, event.timeStamp)
        }
        usagePicker.result(now, ACTIVITY_SETTLE_MS)
    }.getOrNull()
}