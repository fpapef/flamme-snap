package sn.flammesnap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import java.util.Random
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Flamme dessinée à la main : trois couches qui vacillent, un halo qui respire
 * et des braises qui montent. Elle réagit quand on tape (kick), quand on valide
 * (boost) et quand on se trompe (dim).
 */
class FlameView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    private val outer = Path()
    private val mid = Path()
    private val core = Path()
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = Random()

    private val emberHot = Motion.col("#FFC53D")
    private val emberCold = Motion.col("#FF6A1F")

    private var t = 0f
    private var lastMs = 0L
    private var energy = 0f
    private var grow = 1f
    private var unit = 0f
    private var glowR = 0f
    private var bedR = 0f
    private var ready = false
    private var igniteBurstDone = true

    // Géométrie courante (sert aussi à placer les braises).
    private var cx = 0f
    private var baseY = 0f
    private var flameW = 0f
    private var flameH = 0f

    // Braises (tableaux parallèles, sans allocation pendant l'animation).
    private val cap = 160
    private val px = FloatArray(cap)
    private val py = FloatArray(cap)
    private val vx = FloatArray(cap)
    private val vy = FloatArray(cap)
    private val life = FloatArray(cap)
    private val maxLife = FloatArray(cap)
    private val size = FloatArray(cap)
    private var count = 0
    private var spawnAcc = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) {
            ready = false
            return
        }
        unit = min(w, h).toFloat()
        val by = h * 0.9f
        val top = by - unit * 0.8f
        outerPaint.shader = LinearGradient(
            0f, top, 0f, by,
            intArrayOf(Motion.col("#FF9F0A"), Motion.col("#FF6A1F"), Motion.col("#FF3B30")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        midPaint.shader = LinearGradient(
            0f, top + unit * 0.2f, 0f, by,
            Motion.col("#FFC53D"), Motion.col("#FF8A00"), Shader.TileMode.CLAMP
        )
        corePaint.shader = LinearGradient(
            0f, by - unit * 0.4f, 0f, by,
            Motion.col("#FFE7A3"), Motion.col("#FFF8E7"), Shader.TileMode.CLAMP
        )
        glowR = unit * 0.62f
        glowPaint.shader = RadialGradient(
            0f, 0f, glowR,
            intArrayOf(Motion.col("#38FFB340"), Motion.col("#14FFB340"), Motion.col("#00FFB340")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        bedR = unit * 0.34f
        bedPaint.shader = RadialGradient(
            0f, 0f, bedR, Motion.col("#26FF6A1F"), Motion.col("#00FF6A1F"), Shader.TileMode.CLAMP
        )
        ready = true
    }

    /** Petit sursaut (une lettre tapée). */
    fun kick() {
        energy = min(energy + 0.14f, 0.7f)
        burst(2, 0.8f)
    }

    /** Grand coup de feu (on valide). */
    fun boost() {
        energy = 1.0f
        burst(18, 1.5f)
    }

    /** La flamme se recroqueville (erreur). */
    fun dim() {
        energy = -0.5f
        invalidate()
    }

    /** Allumage : la flamme naît depuis sa base avec un rebond. */
    fun ignite() {
        if (!Motion.animationsOn()) {
            grow = 1f
            invalidate()
            return
        }
        grow = 0f
        igniteBurstDone = false
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.startDelay = 150
        a.duration = 1100
        a.interpolator = OvershootInterpolator(1.8f)
        a.addUpdateListener { va ->
            grow = va.animatedValue as Float
            if (!igniteBurstDone && grow > 0.7f) {
                igniteBurstDone = true
                burst(8, 1.2f)
            }
            invalidate()
        }
        a.start()
    }

    fun burst(n: Int, speed: Float) {
        if (!ready || !Motion.animationsOn()) return
        for (k in 0 until n) spawn(speed)
        invalidate()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            lastMs = 0L
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!ready) return
        val anim = Motion.animationsOn()
        val now = SystemClock.uptimeMillis()
        var dt = if (lastMs == 0L) 0.016f else (now - lastMs) / 1000f
        lastMs = now
        if (dt > 0.05f) dt = 0.05f
        if (dt < 0f) dt = 0f
        if (anim) t += dt
        energy *= exp(-dt * 2.4f)
        val inten = max(0.35f, 1f + min(energy, 0.45f))
        val heat = max(0f, energy)

        cx = width / 2f
        baseY = height * 0.9f
        flameW = unit * 0.27f
        flameH = min(unit * 0.66f * inten, baseY - unit * 0.04f)

        val fl = flameH * (1f + 0.05f * noise(t * 2.3f, 4.1f))
        val sway = flameW * 0.22f * noise(t * 1.1f, 1.3f)
        val bl = 1f + 0.07f * noise(t * 1.9f, 2.2f)
        val br = 1f + 0.07f * noise(t * 1.7f, 5.7f)
        val g = Motion.clamp01(grow)

        // Halo qui respire
        val glowScale = 0.8f + 0.2f * inten + 0.04f * sin(t * 3f) + heat * 0.25f
        glowPaint.alpha = (min(1f, 0.8f + heat * 0.4f) * 255f * g).toInt()
        canvas.save()
        canvas.translate(cx, baseY - flameH * 0.45f)
        canvas.scale(glowScale, glowScale)
        canvas.drawCircle(0f, 0f, glowR, glowPaint)
        canvas.restore()

        // Lueur au sol
        bedPaint.alpha = (255f * g).toInt()
        canvas.save()
        canvas.translate(cx, baseY)
        canvas.scale(1f, 0.26f)
        canvas.drawCircle(0f, 0f, bedR * (0.95f + 0.05f * sin(t * 5f)), bedPaint)
        canvas.restore()

        // Les trois couches de la flamme
        canvas.save()
        canvas.scale(grow, grow, cx, baseY)
        flamePath(outer, cx, baseY, flameW, fl, sway, bl, br)
        canvas.drawPath(outer, outerPaint)
        flamePath(
            mid, cx + sway * 0.08f, baseY - unit * 0.012f, flameW * 0.68f, fl * 0.72f,
            sway * 0.7f + flameW * 0.08f * noise(t * 2.6f, 9f),
            1f + 0.08f * noise(t * 2.4f, 3.3f), 1f + 0.08f * noise(t * 2.1f, 7.1f)
        )
        canvas.drawPath(mid, midPaint)
        flamePath(core, cx, baseY - unit * 0.02f, flameW * 0.38f, fl * 0.42f, sway * 0.4f, 1f, 1f)
        canvas.drawPath(core, corePaint)
        canvas.restore()

        // Braises
        if (anim) updateEmbers(dt, inten)
        drawEmbers(canvas)

        if (anim && isAttachedToWindow && windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }

    /** Somme de sinus : un bruit doux et sans répétition visible. */
    private fun noise(x: Float, s: Float): Float =
        0.5f * sin(x + s) + 0.3f * sin(x * 1.73f + s * 1.9f) + 0.2f * sin(x * 3.1f + s * 0.7f)

    private fun flamePath(p: Path, x: Float, by: Float, w: Float, h: Float, sway: Float, bl: Float, br: Float) {
        val tipX = x + sway
        val tipY = by - h
        p.reset()
        p.moveTo(tipX, tipY)
        p.cubicTo(tipX + w * 0.12f, tipY + h * 0.22f, x + w * br + sway * 0.3f, by - h * 0.48f, x + w * 0.96f * br, by - h * 0.22f)
        p.cubicTo(x + w * 0.92f * br, by - h * 0.02f, x + w * 0.5f, by, x, by)
        p.cubicTo(x - w * 0.5f, by, x - w * 0.92f * bl, by - h * 0.02f, x - w * 0.96f * bl, by - h * 0.22f)
        p.cubicTo(x - w * bl + sway * 0.3f, by - h * 0.48f, tipX - w * 0.12f, tipY + h * 0.22f, tipX, tipY)
        p.close()
    }

    private fun spawn(speedMul: Float) {
        if (count >= cap || flameH <= 0f) return
        val i = count++
        px[i] = cx + (rnd.nextFloat() - 0.5f) * flameW
        py[i] = baseY - flameH * (0.25f + rnd.nextFloat() * 0.45f)
        vx[i] = (rnd.nextFloat() - 0.5f) * unit * 0.12f * speedMul
        vy[i] = -unit * (0.28f + rnd.nextFloat() * 0.35f) * speedMul
        maxLife[i] = 0.7f + rnd.nextFloat() * 0.9f
        life[i] = maxLife[i]
        size[i] = unit * (0.006f + rnd.nextFloat() * 0.009f)
    }

    private fun updateEmbers(dt: Float, inten: Float) {
        if (grow > 0.6f) {
            spawnAcc += dt * (2.2f + 8f * max(0f, energy)) * inten
            while (spawnAcc >= 1f) {
                spawn(1f)
                spawnAcc -= 1f
            }
        }
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f || py[i] < -size[i] * 4f) {
                count--
                px[i] = px[count]; py[i] = py[count]
                vx[i] = vx[count]; vy[i] = vy[count]
                life[i] = life[count]; maxLife[i] = maxLife[count]
                size[i] = size[count]
                continue
            }
            vx[i] += sin(t * 3f + i) * unit * 0.25f * dt
            vy[i] -= unit * 0.05f * dt
            px[i] += vx[i] * dt
            py[i] += vy[i] * dt
            i++
        }
    }

    private fun drawEmbers(canvas: Canvas) {
        for (i in 0 until count) {
            val f = life[i] / maxLife[i]
            emberPaint.color = Motion.mix(emberCold, emberHot, f)
            emberPaint.alpha = (255f * min(1f, f * 1.6f)).toInt()
            canvas.drawCircle(px[i], py[i], size[i] * (0.6f + 0.4f * f), emberPaint)
        }
    }
}
