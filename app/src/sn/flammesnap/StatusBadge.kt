package sn.flammesnap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.min
import kotlin.math.sin

/**
 * Pastille d'état animée : arc qui tourne (chargement / remplissage),
 * coche qui se dessine (réussi), point d'exclamation (à compléter / erreur).
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
    private val pop = OvershootInterpolator(2.2f)

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val check = Path()
    private val seg = Path()
    private val pm = PathMeasure()

    private val ink = Motion.col("#111111")
    private val orange = Motion.col("#FF6A1F")
    private val green = Motion.col("#16A34A")
    private val amber = Motion.col("#F97316")
    private val red = Motion.col("#DC2626")

    init {
        arcPaint.style = Paint.Style.STROKE
        arcPaint.strokeCap = Paint.Cap.ROUND
        markPaint.style = Paint.Style.STROKE
        markPaint.strokeCap = Paint.Cap.ROUND
        markPaint.strokeJoin = Paint.Join.ROUND
        markPaint.color = 0xFFFFFFFF.toInt()
    }

    fun show(s: Int) {
        if (s == state) return
        state = s
        animator?.cancel()
        if (!Motion.animationsOn()) {
            enter = 1f
            invalidate()
            return
        }
        enter = 0f
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 700
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
        val stroke = s * 0.11f
        arcPaint.strokeWidth = stroke
        markPaint.strokeWidth = s * 0.12f
        val l = (w - s) / 2f
        val t = (h - s) / 2f
        oval.set(l + stroke, t + stroke, l + s - stroke, t + s - stroke)
        check.reset()
        check.moveTo(l + s * 0.29f, t + s * 0.52f)
        check.lineTo(l + s * 0.44f, t + s * 0.67f)
        check.lineTo(l + s * 0.72f, t + s * 0.37f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        when (state) {
            LOADING, FILLING -> {
                val sec = (SystemClock.uptimeMillis() - start) / 1000f
                val rot = (sec * 320f) % 360f
                val sweep = 60f + 200f * (0.5f + 0.5f * sin(sec * 3.4f))
                arcPaint.color = if (state == FILLING) orange else ink
                arcPaint.alpha = (255f * Motion.clamp01(0.3f + enter)).toInt()
                canvas.drawArc(oval, rot, sweep, false, arcPaint)
                if (state == FILLING) {
                    fillPaint.color = orange
                    canvas.drawCircle(cx, cy, r * 0.22f * (0.85f + 0.15f * sin(sec * 9f)), fillPaint)
                }
                if (Motion.animationsOn() && isAttachedToWindow && windowVisibility == VISIBLE) {
                    postInvalidateOnAnimation()
                }
            }
            else -> {
                val s = pop.getInterpolation(Motion.clamp01(enter / 0.55f))
                fillPaint.color = when (state) {
                    DONE -> green
                    WARN -> amber
                    else -> red
                }
                canvas.drawCircle(cx, cy, r * s, fillPaint)
                val m = Motion.clamp01((enter - 0.3f) / 0.6f)
                if (m <= 0f) return
                if (state == DONE) {
                    pm.setPath(check, false)
                    seg.reset()
                    pm.getSegment(0f, pm.length * m, seg, true)
                    canvas.drawPath(seg, markPaint)
                } else {
                    val top = cy - r * 0.42f
                    val bottom = cy + r * 0.12f
                    canvas.drawLine(cx, top, cx, top + (bottom - top) * m, markPaint)
                    if (m > 0.85f) {
                        val dotPaintW = markPaint.strokeWidth
                        fillPaint.color = 0xFFFFFFFF.toInt()
                        canvas.drawCircle(cx, cy + r * 0.42f, dotPaintW * 0.6f, fillPaint)
                    }
                }
            }
        }
    }
}
