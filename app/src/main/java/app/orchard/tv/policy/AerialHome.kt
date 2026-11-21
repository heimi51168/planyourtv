package app.orchard.tv.policy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import app.orchard.tv.MainActivity
import app.orchard.tv.guard.GuardSetup
import app.orchard.tv.guard.HomeGuardService
import app.orchard.tv.system.DiagLog
import com.planyourtv.policy.PolicyHost

/**
 * Product-owned adapter for bringing Aerial's Home back to the front.
 *
 * A running Home Guard stays the preferred path and keeps its own OEM, screen and standby
 * checks. Without the guard the adapter starts the same task directly, so policy entries do not
 * need to know which path is active. This file is the only product code that reaches into the
 * Aerial host for this action, which keeps an upstream merge to one adapter.
 */
internal object AerialHome {
    private const val TAG = "AerialHome"
    private const val DIRECT_COOLDOWN_MS = 2_500L

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var app: Context? = null
    private var lastDirectAt = 0L

    /** The fallback installed in PolicyRuntime; the adapter delegates to the guard itself. */
    val policyHost: PolicyHost = object : PolicyHost {
        override fun bringHome() = show("policy")

        override fun lockScreen(): Boolean = false
    }

    fun install(context: Context) {
        app = context.applicationContext
    }

    fun show(why: String) {
        val context = app ?: return
        // Guard first and unchanged: goHome already owns its screen and standby checks.
        if (HomeGuardService.bringAerialBack()) {
            DiagLog.i("$TAG: guard brought Aerial back, $why")
            return
        }
        handler.post { direct(context, why) }
    }

    private fun direct(context: Context, why: String) {
        // The guard may have bound after show() was called.
        if (HomeGuardService.bringAerialBack()) return
        if (HomeGuardService.isAerialResumed()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastDirectAt < DIRECT_COOLDOWN_MS) return
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isInteractive == false) return
        if (Settings.Global.getString(context.contentResolver, "tv_power_mode") == "QUICK") return
        if (!GuardSetup.isUserUnlocked(context)) return
        lastDirectAt = now
        DiagLog.i("$TAG: no guard; bringing Aerial back, $why")
        runCatching {
            context.startActivity(MainActivity.bringBackIntent(context))
        }.onFailure {
            DiagLog.i("$TAG: could not bring Aerial back, $why: $it")
        }
    }
}