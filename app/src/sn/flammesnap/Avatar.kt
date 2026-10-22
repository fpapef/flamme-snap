package sn.flammesnap

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** Pastille ronde colorée avec l'initiale du profil (façon Contacts). */
object Avatar {

    fun creer(ctx: Context, pr: Profil, tailleDp: Float): TextView {
        val tv = TextView(ctx)
        val d = ctx.resources.displayMetrics.density
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Prefs.COULEURS[((pr.couleur % Prefs.COULEURS.size) + Prefs.COULEURS.size) % Prefs.COULEURS.size])
        tv.background = bg
        tv.gravity = Gravity.CENTER
        tv.setTextColor(0xFFFFFFFF.toInt())
        tv.textSize = tailleDp * 0.44f
        tv.includeFontPadding = false
        tv.tag = "semibold"
        tv.text = pr.initiale()
        tv.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val sb = Typo.face(ctx, "semibold")
        if (sb != null) tv.typeface = sb
        tv.minWidth = (tailleDp * d).toInt()
        tv.minHeight = (tailleDp * d).toInt()
        return tv
    }

    /** Met à jour l'initiale (pendant qu'on renomme le profil). */
    fun majInitiale(tv: TextView?, pr: Profil) {
        if (tv != null) tv.text = pr.initiale()
    }
}
