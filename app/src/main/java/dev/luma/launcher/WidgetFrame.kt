package dev.luma.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * Wraps a widget view so the launcher can detect a long press (which widgets would otherwise swallow)
 * and, in the same gesture, drag the widget around.
 */
class WidgetFrame(context: Context) : FrameLayout(context) {
    var onLongPress: (() -> Unit)? = null
    /** Movement since the previous event, in px. */
    var onDragBy: ((dx: Float, dy: Float) -> Unit)? = null
    var onDragEnd: ((cancelled: Boolean) -> Unit)? = null

    // Widgets don't all round their own corners (Samsung's stop when the host says it clips them), so the
    // host does it. Clipping the canvas works even when Compose draws this view directly, unlike an outline.
    private val clipPath = Path()
    // One UI Home's corner radius; the system's own app-widget radius is smaller and looks off next to it.
    private val cornerRadius: Float = 22f * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipPath.reset()
        clipPath.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), cornerRadius, cornerRadius, Path.Direction.CW)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
    }

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var pending = false
    private var longPressed = false

    private val longPressRunnable = Runnable {
        if (pending) {
            pending = false
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            parent?.requestDisallowInterceptTouchEvent(true)
            onLongPress?.invoke()
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // Captured up front: finishing the drag below resets the flag, but this final UP/CANCEL
        // must still be swallowed or the widget would see it as a tap and launch its app.
        val swallow = longPressed
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; lastX = downX; lastY = downY
                longPressed = false
                pending = true
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (pending && hypot(e.rawX - downX, e.rawY - downY) > slop) cancelPending()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelPending()
                // An intercepted UP/CANCEL never reaches onTouchEvent, so finish the drag here.
                if (longPressed) finishDrag(e.actionMasked == MotionEvent.ACTION_CANCEL)
            }
        }
        return swallow
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!longPressed) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                onDragBy?.invoke(e.rawX - lastX, e.rawY - lastY)
                lastX = e.rawX; lastY = e.rawY
            }
            MotionEvent.ACTION_UP -> finishDrag(false)
            MotionEvent.ACTION_CANCEL -> finishDrag(true)
        }
        return true
    }

    private fun cancelPending() {
        pending = false
        removeCallbacks(longPressRunnable)
    }

    private fun finishDrag(cancelled: Boolean) {
        longPressed = false
        onDragEnd?.invoke(cancelled)
    }
}
