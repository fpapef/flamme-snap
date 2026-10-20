package sn.flammesnap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Gerbe de confettis aux couleurs de la flamme. Laisse passer les touches. */
class ConfettiView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    private val cap = 180
    private val x = FloatArray(cap)
    private val y = FloatArray(cap)
    private val vx = FloatArray(cap)
    private val vy = FloatArray(cap)
    private val rot = FloatArray(cap)
    private val vr = FloatArray(cap)
    private val life = FloatArray(cap)
    private val sz = FloatArray(cap)
    private val color = IntArray(cap)
    private val shape = IntArray(cap)
    private var n = 0
    private var lastMs = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = Random()
    private val palette = intArrayOf(
        Motion.col("#FFFC00"), Motion.col("#FFC93C"), Motion.col("#FF8A1F"),
        Motion.col("#FF5A1F"), Motion.col("#FF3B1F"), Motion.col("#111111")
    )

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun burst(ox: Float, oy: Float, amount: Int) {
        if (!Motion.animationsOn()) return
        val d = resources.displayMetrics.density
        for (k in 0 until amount) {
            if (n >= cap) break
            val i = n++
            val angle = -PI / 2 + (rnd.nextDouble() - 0.5) * PI * 1.25
            val speed = d * (280f + rnd.nextFloat() * 520f)
            x[i] = ox
            y[i] = oy
            vx[i] = (cos(angle) * speed).toFloat()
            vy[i] = (sin(angle) * speed).toFloat()
            rot[i] = rnd.nextFloat() * 360f
            vr[i] = (rnd.nextFloat() - 0.5f) * 900f
            life[i] = 1.6f + rnd.nextFloat() * 0.9f
            sz[i] = d * (3.5f + rnd.nextFloat() * 4f)
            color[i] = palette[rnd.nextInt(palette.size)]
            shape[i] = rnd.nextInt(3)
        }
        lastMs = 0L
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (n == 0) return
        val now = SystemClock.uptimeMillis()
        var dt = if (lastMs == 0L) 0.016f else (now - lastMs) / 1000f
        lastMs = now
        if (dt > 0.05f) dt = 0.05f
        val d = resources.displayMetrics.density
        val gravity = d * 1100f
        var i = 0
        while (i < n) {
            life[i] -= dt
            if (life[i] <= 0f || y[i] > height + sz[i] * 4f) {
                n--
                x[i] = x[n]; y[i] = y[n]; vx[i] = vx[n]; vy[i] = vy[n]
                rot[i] = rot[n]; vr[i] = vr[n]; life[i] = life[n]; sz[i] = sz[n]
                color[i] = color[n]; shape[i] = shape[n]
                continue
            }
            vy[i] += gravity * dt
            vx[i] *= (1f - 1.6f * dt)
            vy[i] *= (1f - 0.6f * dt)
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            rot[i] += vr[i] * dt

            paint.color = color[i]
            paint.alpha = (255f * min(1f, life[i] / 0.6f)).toInt()
            val s = sz[i]
            canvas.save()
            canvas.translate(x[i], y[i])
            canvas.rotate(rot[i])
            when (shape[i]) {
                0 -> canvas.drawRect(-s, -s * 0.45f, s, s * 0.45f, paint)
                1 -> canvas.drawCircle(0f, 0f, s * 0.55f, paint)
                else -> {
                    canvas.scale(1f, 0.25f + 0.75f * abs(sin(rot[i] * 0.035f)))
                    canvas.drawRect(-s * 0.4f, -s * 1.1f, s * 0.4f, s * 1.1f, paint)
                }
            }
            canvas.restore()
            i++
        }
        if (n > 0) postInvalidateOnAnimation()
    }
}
