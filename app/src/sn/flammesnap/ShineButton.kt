package sn.flammesnap

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.widget.Button

/** Bouton principal : un reflet lumineux le traverse toutes les quelques secondes. */
class ShineButton(context: Context, attrs: AttributeSet?) : Button(context, attrs) {

    constructor(context: Context) : this(context, null)

    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val band = RectF()
    private val start = SystemClock.uptimeMillis()
    private var ready = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) {
            ready = false
            return
        }
        val bw = w * 0.32f
        shinePaint.shader = LinearGradient(
            0f, 0f, bw, 0f,
            intArrayOf(0x00FFFFFF, 0x4DFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP
        )
        band.set(0f, -h.toFloat(), bw, h * 2f)
        val r = Motion.dp(this, 14f)
        clip.reset()
        clip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW)
        ready = true
    }

    override fun onDraw(canvas: Canvas) {
        val period = 3800L
        val sweep = 1000L
        val e = (SystemClock.uptimeMillis() - start) % period
        val anim = Motion.animationsOn()
        if (ready && anim && isEnabled && e < sweep) {
            val p = e.toFloat() / sweep
            val x = -width * 0.4f + p * width * 1.5f
            canvas.save()
            canvas.clipPath(clip)
            canvas.translate(x, 0f)
            canvas.skew(-0.35f, 0f)
            canvas.drawRect(band, shinePaint)
            canvas.restore()
        }
        super.onDraw(canvas)
        if (anim && isAttachedToWindow && windowVisibility == VISIBLE) {
            if (e < sweep) postInvalidateOnAnimation() else postInvalidateDelayed(period - e)
        }
    }
}
