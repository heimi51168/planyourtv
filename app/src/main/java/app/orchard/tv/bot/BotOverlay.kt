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
    private var danmakuView: android.view.View? = null

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
                    0f, 0f, 0f, 0f,
                    20f * density, 20f * density, 20f * density, 20f * density,
                )
            }
        }
        if (!addOverlayView(wm, view, width = WindowManager.LayoutParams.MATCH_PARENT)) {
            TvLog.w(TAG, "banner: no window type worked")
            return
        }
        bannerView = view

        // Slide in from above the screen edge, hold, then slide back out.
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
        val textView = TextView(context).apply {
            setText(text)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(6f * density, 0f, 0f, 0xB0000000.toInt())
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
        }

        // A WRAP_CONTENT overlay window clips its content to the text width, so
        // translationX has almost nothing to travel through and the animation
        // looks instant. A full-width container gives the text the whole screen
        // to cross inside the window bounds.
        val container = android.widget.FrameLayout(context).apply {
            clipChildren = false
            addView(
                textView,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        // Pre-measure and park the text past the right edge BEFORE the window
        // is ever drawn, so the first rendered frame is already off-screen.
        textView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        val screenW = context.resources.displayMetrics.widthPixels
        val startX = screenW + 50f
        val endX = -textView.measuredWidth - 50f
        textView.translationX = startX

        if (!addOverlayView(
                wm, container,
                width = WindowManager.LayoutParams.MATCH_PARENT,
                gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT,
                yOffset = ((Math.random() - 0.5) * 120 * density).toInt(),
            )
        ) {
            TvLog.w(TAG, "danmaku: no window type worked")
            return
        }
        danmakuView = textView

        // Float from just past the right edge to just past the left.
        // LinearInterpolator: constant speed — the default accelerate/decelerate
        // easing reads as stutter on a TV's relatively low refresh rate.
        // Hardware layer keeps the rendering off the main-thread layout path.
        // 20 s to cross a couch-distance screen: slow enough to read while
        // the child watches.
        textView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        val scroll = ObjectAnimator.ofFloat(textView, "translationX", startX, endX)
        scroll.duration = 20_000
        scroll.interpolator = android.view.animation.LinearInterpolator()
        scroll.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                textView.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                runCatching { wm.removeView(container) }
                if (danmakuView === textView) danmakuView = null
            }
        })
        scroll.start()
    }

    /**
     * Tries window types in the same order as PolicyCountdownOverlay: accessibility
     * overlay first (renders above our own activity — the quiz page), then
     * application overlay (renders above other apps but may sit under our own
     * activity on some firmwares), then the legacy system alert.
     */
    private fun addOverlayView(
        wm: WindowManager,
        view: android.view.View,
        width: Int,
        gravity: Int = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        yOffset: Int = 0,
    ): Boolean {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        val types = buildList {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                add(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            }
            if (android.provider.Settings.canDrawOverlays(view.context)) {
                add(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    },
                )
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    add(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                }
            }
        }.distinct()

        for (type in types) {
            val params = WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                this.gravity = gravity
                y = yOffset
            }
            if (runCatching { wm.addView(view, params) }.isSuccess) return true
        }
        return false
    }

    private fun removeBanner(wm: WindowManager) {
        bannerView?.let { removeView(wm, it) }
        bannerView = null
    }

    private fun removeView(wm: WindowManager, view: android.view.View) {
        runCatching { wm.removeView(view) }
    }
}
