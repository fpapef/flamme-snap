package sn.flammesnap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Fine ligne de chargement (2 dp), à la manière de Safari. */
class GlowProgress(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    private var shown = 0f
    private var anim: ValueAnimator? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        fill.color = Motion.col("#F2550F")
    }

    fun setProgress(p: Int) {
        val target = (if (p < 0) 0 else if (p > 100) 100 else p) / 100f
        if (target < shown - 0.05f) shown = 0f // nouveau chargement
        anim?.cancel()
        val a = ValueAnimator.ofFloat(shown, target)
        a.duration = 350
        a.interpolator = Motion.IOS
        a.addUpdateListener { va ->
            shown = va.animatedValue as Float
            invalidate()
        }
        a.start()
        anim = a
        animate().cancel()
        if (target >= 1f) {
            animate().alpha(0f).setStartDelay(300).setDuration(300).start()
        } else {
            alpha = 1f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val fw = width * shown
        if (fw > 0f) canvas.drawRect(0f, 0f, fw, height.toFloat(), fill)
    }
}
