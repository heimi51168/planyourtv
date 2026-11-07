package app.orchard.tv.policy

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyUiState
import com.planyourtv.policy.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.min

/**
 * The ball only exists in the final 30 seconds, so a clock-style "0:30" wastes the minute
 * field and reads like a timer. Match the status pill and the 30/20/10 voice cadence: "30s".
 */
internal fun countdownLabel(seconds: Long): String =
    String.format(Locale.ROOT, "%ds", seconds.coerceAtLeast(0L))

/**
 * Last-minute countdown ball over a video app. It is intentionally a tiny, non-focusable,
 * non-touchable accessibility overlay: no full-screen mask, no new permission, no Compose.
 *
 * The window exists only while a session is ACTIVE with 1..30 seconds of effective time left and
 * Aerial is not in front. Everything else is a no-op.
 */
object PolicyCountdownOverlay {
    private const val TAG = "PolicyBall"
    private const val SMALL_BALL_DP = 48
    private const val EDGE_END_DP = 24
    private const val EDGE_TOP_DP = 84
    private const val LAST_SECONDS = 30L
    private val Critical = Color.rgb(255, 69, 58)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var service: AccessibilityService? = null
    private var windowManager: WindowManager? = null
    private var ballView: BallView? = null
    private var job: Job? = null
    private var receiverRegistered = false
    private var screenInteractive = true
    private var aerialVisible = true
    private var current: BallState? = null

    private data class BallState(
        val mode: PolicyMode,
        /** Rounded up to a 10-second bucket, for the label and voice cadence. */
        val seconds: Long,
        /** Exact remaining time, so the ring shrinks every second. */
        val remainingSeconds: Long,
        val totalSeconds: Long,
    )

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenInteractive = (service?.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isInteractive ?: true
            evaluate(PolicyModeCoordinator.state.value)
        }
    }

    /** Called once when HomeGuardService is connected. */
    fun attach(service: AccessibilityService, aerialIsVisible: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            Log.i(TAG, "overlay not supported on API ${Build.VERSION.SDK_INT}")
            return
        }
        if (this.service === service && job != null) {
            aerialVisible = aerialIsVisible
            evaluate(PolicyModeCoordinator.state.value)
            return
        }
        Log.i(TAG, "attach; aerialVisible=$aerialIsVisible")
        detach()
        this.service = service
        aerialVisible = aerialIsVisible
        windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        screenInteractive = (service.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isInteractive ?: true
        Log.i(TAG, "windowManager=${windowManager != null}, interactive=$screenInteractive")
        registerScreenReceiver(service)
        job = scope.launch {
            PolicyModeCoordinator.state
                .map(::ballStateOf)
                .distinctUntilChanged()
                .collect { _ -> evaluate(PolicyModeCoordinator.state.value) }
        }
        evaluate(PolicyModeCoordinator.state.value)
    }

    /** Called when HomeGuardService is destroyed or unbound. */
    fun detach() {
        Log.i(TAG, "detach")
        job?.cancel()
        job = null
        unregisterScreenReceiver()
        removeWindow()
        current = null
        windowManager = null
        service = null
    }

    /** Aerial resumed/paused already drives this from HomeGuardService. */
    fun setAerialVisible(visible: Boolean) {
        aerialVisible = visible
        Log.i(TAG, "aerialVisible=$visible")
        scope.launch { evaluate(PolicyModeCoordinator.state.value) }
    }

    private fun registerScreenReceiver(service: AccessibilityService) {
        if (receiverRegistered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            ContextCompat.registerReceiver(
                service,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    private fun unregisterScreenReceiver() {
        if (!receiverRegistered) return
        val currentService = service ?: return
        runCatching { currentService.unregisterReceiver(screenReceiver) }
        receiverRegistered = false
    }

    private fun evaluate(state: PolicyUiState) {
        val desired = if (!aerialVisible && screenInteractive) ballStateOf(state) else null
        if (desired == null) {
            removeWindow()
            return
        }
        val view = ballView
        if (view == null) {
            addWindow(desired)
        } else if (desired != current) {
            view.update(desired)
            current = desired
        }
    }

    private fun ballStateOf(state: PolicyUiState): BallState? {
        if (state.sessionState != SessionState.ACTIVE) return null
        if (state.mode != PolicyMode.LOCAL && state.mode != PolicyMode.CLOUD) return null
        val remaining = state.effectiveRemainingSeconds
        if (remaining <= 0L || remaining > LAST_SECONDS) return null
        if (state.totalSeconds <= 0L) return null
        return BallState(
            mode = state.mode,
            seconds = roundUpToTen(remaining),
            remainingSeconds = remaining,
            totalSeconds = state.totalSeconds,
        )
    }

    private fun addWindow(state: BallState) {
        val service = service ?: return
        val windowManager = windowManager ?: return
        val size = dp(service, SMALL_BALL_DP)
        val types = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                add(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            }
            if (canDrawOverlays(service)) {
                add(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    },
                )
            }
        }.distinct()

        // Some TV firmwares reject TYPE_ACCESSIBILITY_OVERLAY with an unknown-token error even
        // though the service is connected. The app already has SYSTEM_ALERT_WINDOW for the boot
        // fallback, so try the equally small application overlay only when that permission exists.
        for (type in types) {
            val view = BallView(service).apply { update(state) }
            val params = WindowManager.LayoutParams(
                size,
                size,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(service, EDGE_END_DP)
                y = dp(service, EDGE_TOP_DP)
            }
            val added = runCatching { windowManager.addView(view, params) }
            if (added.isSuccess) {
                ballView = view
                current = state
                Log.i(TAG, "ball shown type=$type ${state.mode} ${state.seconds}s")
                return
            }
            Log.w(TAG, "addView type=$type failed", added.exceptionOrNull())
        }
        ballView = null
    }

    private fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun removeWindow() {
        val view = ballView ?: return
        ballView = null
        current = null
        Log.i(TAG, "ball removed")
        runCatching { windowManager?.removeView(view) }
            .onFailure { Log.w(TAG, "removeView failed", it) }
    }

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics)
            .toInt()

    private fun roundUpToTen(seconds: Long): Long =
        ((seconds.coerceAtLeast(0L) + 9L) / 10L) * 10L

    private class BallView(context: Context) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(196, 12, 20, 32)
            style = Paint.Style.FILL
        }
        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dpLocal(2.5f)
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dpLocal(2.5f)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = spLocal(12f)
            isFakeBoldText = true
        }
        private val arcRect = RectF()
        private var state: BallState? = null

        fun update(newState: BallState) {
            if (state == newState) return
            state = newState
            // The ball exists only in the final 30 seconds, so it is always the
            // critical color. Source and guard state stay in the status pill.
            progressPaint.color = Critical
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val currentState = state ?: return
            val cx = width / 2f
            val cy = height / 2f
            val stroke = trackPaint.strokeWidth
            val radius = min(width, height) / 2f - stroke
            canvas.drawCircle(cx, cy, radius, backgroundPaint)

            arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(arcRect, -90f, 360f, false, trackPaint)
            // A 30-second gauge stays visible; using the session total here would
            // leave less than a one-degree arc at the end of a long session.
            val window = minOf(LAST_SECONDS, currentState.totalSeconds).coerceAtLeast(1L)
            val fraction = (currentState.remainingSeconds.toFloat() / window.toFloat())
                .coerceIn(0f, 1f)
            if (fraction > 0f) {
                canvas.drawArc(arcRect, -90f, 360f * fraction, false, progressPaint)
            }

            val text = countdownLabel(currentState.seconds)
            val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(text, cx, baseline, textPaint)
        }

        private fun dpLocal(value: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

        private fun spLocal(value: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    }
}
