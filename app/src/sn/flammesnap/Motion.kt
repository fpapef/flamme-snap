package sn.flammesnap

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import kotlin.math.cos
import kotlin.math.exp

/** Ressort amorti : arrivée vive, très léger rebond, puis repos (façon iOS). */
class Spring(private val bounce: Float) : Interpolator {
    override fun getInterpolation(x: Float): Float {
        if (x >= 1f) return 1f
        return 1f - exp(-6.2f * x) * cos(bounce * x)
    }
}

/** Outils d'animation partagés par les écrans. */
object Motion {

    /** Courbe des transitions de l'iPhone. */
    val IOS: Interpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)
    val SPRING: Interpolator = Spring(8f)
    val SOFT_SPRING: Interpolator = Spring(4.5f)

    fun dp(v: View, x: Float): Float = x * v.resources.displayMetrics.density

    fun col(hex: String): Int = Color.parseColor(hex)

    /** Respecte le réglage « Supprimer les animations » d'Android. */
    fun animationsOn(): Boolean =
        if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled() else true

    /** Gros bouton : s'enfonce légèrement, revient avec un ressort. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressScale(v: View) {
        v.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f)
                        .setDuration(140).setInterpolator(IOS).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(520).setInterpolator(SPRING).start()
                }
                return false
            }
        })
    }

    /** Bouton texte / icône : s'atténue sous le doigt, comme sur iOS. */
    @SuppressLint("ClickableViewAccessibility")
    fun highlight(v: View) {
        v.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate().cancel()
                        view.alpha = 0.3f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().alpha(1f).setDuration(260).setInterpolator(IOS).start()
                }
                return false
            }
        })
    }

    /** Apparition en cascade : fondu, légère montée et (Android 12+) netteté qui revient. */
    fun reveal(views: List<View>, startDelay: Long, step: Long, dyDp: Float, blurPx: Float) {
        val on = animationsOn()
        for (i in views.indices) {
            val v = views[i]
            if (!on) continue
            val delay = startDelay + i * step
            v.alpha = 0f
            v.translationY = dp(v, dyDp)
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delay)
                .setDuration(700)
                .setInterpolator(IOS)
                .start()
            if (blurPx > 0f && Build.VERSION.SDK_INT >= 31) Blur31.unblur(v, blurPx, 700, delay)
        }
    }

    /** Petit « non » de la tête (comme un mauvais code sur iPhone). */
    fun shake(v: View) {
        val d = dp(v, 1f)
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, 12 * d, -10 * d, 7 * d, -4 * d, 2 * d, 0f)
            .setDuration(420).start()
    }

    /** Respiration discrète (succès). */
    fun pop(v: View, amount: Float) {
        if (!animationsOn()) return
        v.animate().cancel()
        v.scaleX = 1f + amount
        v.scaleY = 1f + amount
        v.animate().scaleX(1f).scaleY(1f).setDuration(600).setInterpolator(SOFT_SPRING).start()
    }

    fun hapticOk(v: View) {
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    fun hapticNo(v: View) {
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
        )
    }

    fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

    /** Mélange deux couleurs (f = 0 → a, f = 1 → b). */
    fun mix(a: Int, b: Int, f: Float): Int {
        val g = clamp01(f)
        val ia = 1f - g
        return Color.argb(
            (Color.alpha(a) * ia + Color.alpha(b) * g).toInt(),
            (Color.red(a) * ia + Color.red(b) * g).toInt(),
            (Color.green(a) * ia + Color.green(b) * g).toInt(),
            (Color.blue(a) * ia + Color.blue(b) * g).toInt()
        )
    }
}

/** Flou progressif (Android 12+ uniquement ; isolé pour les anciens Android). */
private object Blur31 {
    fun unblur(v: View, from: Float, duration: Long, delay: Long) {
        v.setRenderEffect(RenderEffect.createBlurEffect(from, from, Shader.TileMode.DECAL))
        val a = ValueAnimator.ofFloat(from, 0f)
        a.startDelay = delay
        a.duration = duration
        a.interpolator = Motion.IOS
        a.addUpdateListener { va ->
            val r = va.animatedValue as Float
            v.setRenderEffect(if (r < 0.5f) null else RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL))
        }
        a.start()
    }
}
