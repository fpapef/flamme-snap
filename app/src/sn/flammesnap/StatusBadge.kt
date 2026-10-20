package sn.flammesnap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Pastille d'état façon iOS : indicateur d'activité à 8 branches,
 * coche verte qui se dessine, point d'exclamation orange ou rouge.
 */
class StatusBadge(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    companion object {
        const val LOADING = 0
        const val FILLING = 1
        const val DONE = 2
        const val WARN = 3
        const val ERROR = 4
    }

    var state = LOADING
        private set

    private var enter = 1f
    private var animator: ValueAnimator? = null
    private val start = SystemClock.uptimeMillis()

    private val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val check = Path()
    private val seg = Path()
    private val pm = PathMeasure()

    private val gray = Motion.col("#8E8E93")
    private val green = Motion.col("#34C759")
    private val orange = Motion.col("#FF9F0A")
    private val red = Motion.col("#FF3B30")
    private val white = Motion.col("#FFFFFF")

    init {
        spokePaint.style = Paint.Style.STROKE
        spokePaint.strokeCap = Paint.Cap.ROUND
        spokePaint.color = gray
        markPaint.style = Paint.Style.STROKE
        markPaint.strokeCap = Paint.Cap.ROUND
        markPaint.strokeJoin = Paint.Join.ROUND
        markPaint.color = white
    }

    fun show(s: Int) {
        if (s == state) return
        val wasSpinner = state == LOADING || state == FILLING
        val isSpinner = s == LOADING || s == FILLING
        state = s
        animator?.cancel()
        if (wasSpinner && isSpinner) return // même indicateur, on ne relance rien
        if (!Motion.animationsOn()) {
            enter = 1f
            invalidate()
            return
        }
        enter = 0f
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 750
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { va ->
            enter = va.animatedValue as Float
            invalidate()
        }
        a.start()
        animator = a
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = min(w, h).toFloat()
        spokePaint.strokeWidth = s * 0.095f
        markPaint.strokeWidth = s * 0.11f
        val l = (w - s) / 2f
        val t = (h - s) / 2f
        check.reset()
        check.moveTo(l + s * 0.29f, t + s * 0.52f)
        check.lineTo(l + s * 0.44f, t + s * 0.66f)
        check.lineTo(l + s * 0.72f, t + s * 0.37f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        if (state == LOADING || state == FILLING) {
            // Indicateur d'activité : la branche « de tête » tourne par crans.
            val sec = (SystemClock.uptimeMillis() - start) / 1000f
            val step = (sec / 0.1f).toInt() % 8
            for (i in 0 until 8) {
                val age = (step - i + 8) % 8
                spokePaint.alpha = (255f * (1f - age / 8f * 0.85f)).toInt()
                canvas.save()
                canvas.rotate(i * 45f, cx, cy)
                canvas.drawLine(cx, cy - r * 0.48f, cx, cy - r * 0.9f, spokePaint)
                canvas.restore()
            }
            if (isAttachedToWindow && windowVisibility == VISIBLE) {
                // Un cran tous les 100 ms : on ne redessine qu'au cran suivant.
                val ms = (SystemClock.uptimeMillis() - start) % 100L
                postInvalidateDelayed(100L - ms)
            }
            return
        }
        val s = Motion.SPRING.getInterpolation(Motion.clamp01(enter / 0.6f))
        fillPaint.color = when (state) {
            DONE -> green
            WARN -> orange
            else -> red
        }
        canvas.drawCircle(cx, cy, r * s, fillPaint)
        val m = Motion.clamp01((enter - 0.32f) / 0.55f)
        if (m <= 0f) return
        if (state == DONE) {
            pm.setPath(check, false)
            seg.reset()
            pm.getSegment(0f, pm.length * Motion.IOS.getInterpolation(m), seg, true)
            canvas.drawPath(seg, markPaint)
        } else {
            val top = cy - r * 0.42f
            val bottom = cy + r * 0.12f
            canvas.drawLine(cx, top, cx, top + (bottom - top) * m, markPaint)
            if (m > 0.85f) {
                fillPaint.color = white
                canvas.drawCircle(cx, cy + r * 0.42f, markPaint.strokeWidth * 0.6f, fillPaint)
            }
        }
    }
}
