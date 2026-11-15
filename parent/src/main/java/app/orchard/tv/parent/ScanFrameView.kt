package app.orchard.tv.parent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Dims everything outside the scan window and draws a rounded outline. The view
 * is not clickable, so taps and pinch gestures fall through to the preview.
 */
class ScanFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }

    private val path = Path()
    private val frame = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width, height) * FRAME_SIZE
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        frame.set(left, top, left + side, top + side)
        val radius = 24f * resources.displayMetrics.density

        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRoundRect(frame, radius, radius, Path.Direction.CW)
        canvas.drawPath(path, scrimPaint)

        canvas.drawRoundRect(frame, radius, radius, framePaint)
    }

    private companion object {
        const val FRAME_SIZE = 0.68f
    }
}
