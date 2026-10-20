package sn.flammesnap

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/** Petits outils d'animation partagés par les écrans. */
object Motion {

    /** Courbe « emphasized » de Material : départ franc, arrivée très douce. */
    val EMPHASIZED: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun dp(v: View, x: Float): Float = x * v.resources.displayMetrics.density

    fun col(hex: String): Int = Color.parseColor(hex)

    /** Respecte le réglage « Supprimer les animations » d'Android. */
    fun animationsOn(): Boolean =
        if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled() else true

    /** Le bouton s'enfonce sous le doigt puis rebondit. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View) {
        v.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f)
                        .setDuration(110).setInterpolator(DecelerateInterpolator()).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(420).setInterpolator(OvershootInterpolator(3f)).start()
                }
                return false
            }
        })
    }

    /** Apparition en cascade : chaque vue monte légèrement en fondu. */
    fun staggerIn(views: List<View>, startDelay: Long, step: Long, dyDp: Float) {
        for (i in views.indices) {
            val v = views[i]
            v.alpha = 0f
            v.translationY = dp(v, dyDp)
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(startDelay + i * step)
                .setDuration(560)
                .setInterpolator(EMPHASIZED)
                .start()
        }
    }

    /** Petit « non » de la tête. */
    fun shake(v: View) {
        val d = dp(v, 1f)
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, 14 * d, -11 * d, 8 * d, -5 * d, 2 * d, 0f)
            .setDuration(440).start()
    }

    /** Petit rebond (succès). */
    fun pop(v: View, amount: Float) {
        v.animate().cancel()
        v.scaleX = 1f + amount
        v.scaleY = 1f + amount
        v.animate().scaleX(1f).scaleY(1f).setDuration(520)
            .setInterpolator(OvershootInterpolator(2.5f)).start()
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
