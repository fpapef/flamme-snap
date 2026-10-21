package sn.flammesnap

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/** Contrôle segmenté façon iOS : un curseur blanc glisse d'un choix à l'autre. */
class Segmented(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    private var labels: Array<String> = arrayOf()
    var selected = -1
        private set

    /** Si vrai, toucher le choix actif le désélectionne. */
    var allowNone = false
    var listener: ((Int) -> Unit)? = null

    private var pos = 0f
    private var thumbAlpha = 0f
    private var anim: ValueAnimator? = null
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textOn = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        track.color = context.getColor(R.color.segment_track)
        thumb.color = context.getColor(R.color.segment_thumb)
        shade.color = 0x1A000000
        val size = 13.5f * resources.displayMetrics.scaledDensity
        text.color = context.getColor(R.color.label)
        text.textSize = size
        text.textAlign = Paint.Align.CENTER
        textOn.color = context.getColor(R.color.label)
        textOn.textSize = size
        textOn.textAlign = Paint.Align.CENTER
        val m = Typo.face(context, "medium")
        if (m != null) text.typeface = m
        val sb = Typo.face(context, "semibold")
        if (sb != null) textOn.typeface = sb
        isClickable = true
        isFocusable = true
    }

    fun labelAt(i: Int): String = if (i >= 0 && i < labels.size) labels[i] else ""

    fun setItems(items: Array<String>) {
        labels = items
        contentDescription = items.joinToString(", ")
        invalidate()
    }

    fun select(i: Int, animate: Boolean) {
        val target = if (i < 0 || i >= labels.size) -1 else i
        selected = target
        anim?.cancel()
        val toA = if (target >= 0) 1f else 0f
        if (target >= 0 && thumbAlpha <= 0.01f) pos = target.toFloat() // apparaît sur place
        val toPos = if (target >= 0) target.toFloat() else pos
        if (!animate || !Motion.animationsOn()) {
            pos = toPos
            thumbAlpha = toA
            invalidate()
            return
        }
        val fromPos = pos
        val fromA = thumbAlpha
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 460
        a.interpolator = Motion.SOFT_SPRING
        a.addUpdateListener { va ->
            val f = va.animatedValue as Float
            pos = fromPos + (toPos - fromPos) * f
            thumbAlpha = fromA + (toA - fromA) * Motion.clamp01(f * 1.8f)
            invalidate()
        }
        a.start()
        anim = a
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (32 * d).toInt()
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(h, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = labels.size
        if (n == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, 9 * d, 9 * d, track)
        val pad = 2 * d
        val segW = (w - 2 * pad) / n
        if (thumbAlpha > 0f) {
            val left = pad + pos * segW
            shade.alpha = (26 * thumbAlpha).toInt()
            rect.set(left, pad + 0.6f * d, left + segW, h - pad + 0.6f * d)
            canvas.drawRoundRect(rect, 7 * d, 7 * d, shade)
            thumb.alpha = (255 * thumbAlpha).toInt()
            rect.set(left, pad, left + segW, h - pad)
            canvas.drawRoundRect(rect, 7 * d, 7 * d, thumb)
        }
        val base = h / 2f - (text.descent() + text.ascent()) / 2f
        for (i in 0 until n) {
            val cx = pad + segW * (i + 0.5f)
            canvas.drawText(labels[i], cx, base, if (i == selected) textOn else text)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!isEnabled || labels.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val pad = 2 * d
                val segW = (width - 2 * pad) / labels.size
                var idx = ((e.x - pad) / segW).toInt()
                if (idx < 0) idx = 0
                if (idx >= labels.size) idx = labels.size - 1
                val nouveau = if (idx == selected && allowNone) -1 else idx
                if (nouveau != selected) {
                    select(nouveau, true)
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    listener?.invoke(nouveau)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()
}
