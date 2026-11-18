package app.orchard.tv.bot

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.planyourtv.log.TvLog

/**
 * Parent-to-TV messages: a banner or a danmaku drawn over whatever the child
 * is watching. SYSTEM_ALERT_WINDOW is already granted over ADB by the install
 * command, and the view never takes input.
 *
 * Both styles run on the main thread (WindowManager requirement); the bot's
 * IO coroutine hops there with [show].
 */
internal object BotOverlay {
    private const val TAG = "BotOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bannerView: TextView? = null
    private var danmakuView: TextView? = null

    /** Banner: slides in from the top, stays [durationMs], slides out. */
    fun showBanner(context: Context, text: String, durationMs: Long = 10_000L) {
        mainHandler.post { showBannerLocked(context.applicationContext, text, durationMs) }
    }

    /** Danmaku: floats right→left across the middle of the screen. */
    fun showDanmaku(context: Context, text: String) {
        mainHandler.post { showDanmakuLocked(context.applicationContext, text) }
    }

    @SuppressLint("InflateParams")
    private fun showBannerLocked(context: Context, text: String, durationMs: Long) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        // One banner at a time; a second message replaces the first.
        removeBanner(wm)

        val density = context.resources.displayMetrics.density
        val view = TextView(context).apply {
            setText(text)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            val hPad = (28 * density).toInt()
            val vPad = (18 * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(0xE6101020.toInt(), 0xE6181830.toInt())
                cornerRadii = floatArrayOf(
                    0f, 0f, 0f, 0f,           // top-left, top-right: square (flush with screen edge)
                    20f * density, 20f * density, 20f * density, 20f * density, // bottom: rounded
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        val added = runCatching { wm.addView(view, params) }.isSuccess
        if (!added) {
            TvLog.w(TAG, "banner addView failed")
            return
        }
        bannerView = view

        // Slide in from above the screen edge, hold, then slide back out.
        val screenH = context.resources.displayMetrics.heightPixels
        view.translationY = -view.layoutParams.height.toFloat() - 200f
        view.measure(0, 0)
        val slideIn = ObjectAnimator.ofFloat(view, "translationY", -view.measuredHeight - 100f, 0f)
        slideIn.duration = 400
        slideIn.start()

        mainHandler.postDelayed({
            val slideOut = ObjectAnimator.ofFloat(view, "translationY", 0f, -view.measuredHeight - 100f)
            slideOut.duration = 400
            slideOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeView(wm, view)
                    if (bannerView === view) bannerView = null
                }
            })
            slideOut.start()
        }, durationMs)
    }

    @SuppressLint("InflateParams")
    private fun showDanmakuLocked(context: Context, text: String) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val density = context.resources.displayMetrics.density
        val view = TextView(context).apply {
            setText(text)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            // Soft dark outline keeps the text readable over bright video.
            setShadowLayer(6f * density, 0f, 0f, 0xB0000000.toInt())
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
            // Slight vertical jitter so consecutive danmaku don't perfectly overlap.
            y = ((Math.random() - 0.5) * 120 * density).toInt()
        }

        val added = runCatching { wm.addView(view, params) }.isSuccess
        if (!added) {
            TvLog.w(TAG, "danmaku addView failed")
            return
        }
        danmakuView = view

        // Float from just past the right edge to just past the left.
        view.measure(0, 0)
        val screenW = context.resources.displayMetrics.widthPixels
        val startX = screenW + 50f
        val endX = -view.measuredWidth - 50f
        view.translationX = startX
        val scroll = ObjectAnimator.ofFloat(view, "translationX", startX, endX)
        scroll.duration = 8_000
        scroll.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                removeView(wm, view)
                if (danmakuView === view) danmakuView = null
            }
        })
        scroll.start()
    }

    private fun removeBanner(wm: WindowManager) {
        bannerView?.let { removeView(wm, it) }
        bannerView = null
    }

    private fun removeView(wm: WindowManager, view: TextView) {
        runCatching { wm.removeView(view) }
    }

    private fun overlayType(): Int = when {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ->
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else -> WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
    }
}
