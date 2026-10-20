package sn.flammesnap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Fine barre de chargement aux couleurs de la flamme, avec un reflet qui file. */
class GlowProgress(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    private var shown = 0f
    private var anim: ValueAnimator? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bandW = 0f
    private val start = SystemClock.uptimeMillis()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        fill.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(Motion.col("#FF3B1F"), Motion.col("#FF8A1F"), Motion.col("#FFC93C")),
            null, Shader.TileMode.CLAMP
        )
        bandW = w * 0.25f
        shine.shader = LinearGradient(
            0f, 0f, bandW, 0f,
            intArrayOf(0x00FFFFFF, 0xB3FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP
        )
    }

    fun setProgress(p: Int) {
        val target = (if (p < 0) 0 else if (p > 100) 100 else p) / 100f
        if (target < shown - 0.05f) shown = 0f // nouveau chargement
        anim?.cancel()
        val a = ValueAnimator.ofFloat(shown, target)
        a.duration = 300
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener { va ->
            shown = va.animatedValue as Float
            invalidate()
        }
        a.start()
        anim = a
        animate().cancel()
        if (target >= 1f) {
            animate().alpha(0f).setStartDelay(350).setDuration(350).start()
        } else {
            alpha = 1f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fw = width * shown
        if (fw <= 0f) return
        canvas.drawRect(0f, 0f, fw, height.toFloat(), fill)
        if (shown < 1f && bandW > 0f && Motion.animationsOn()) {
            val period = 1.1f
            val sec = (SystemClock.uptimeMillis() - start) / 1000f
            val p = (sec % period) / period
            val x = p * (fw + bandW) - bandW
            canvas.save()
            canvas.clipRect(0f, 0f, fw, height.toFloat())
            canvas.translate(x, 0f)
            canvas.drawRect(0f, 0f, bandW, height.toFloat(), shine)
            canvas.restore()
            if (isAttachedToWindow && windowVisibility == VISIBLE) postInvalidateOnAnimation()
        }
    }
}
