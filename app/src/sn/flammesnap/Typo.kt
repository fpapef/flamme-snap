package sn.flammesnap

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Typographie Inter (proche de la police d'Apple), embarquée dans l'app.
 * Chaque texte prend sa graisse selon son android:tag :
 * « display » (grands titres), « semibold », « medium », sinon regular.
 */
object Typo {
    private var loaded = false
    private var regular: Typeface? = null
    private var medium: Typeface? = null
    private var semibold: Typeface? = null
    private var display: Typeface? = null

    private fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val am = ctx.applicationContext.assets
        regular = load(am, "fonts/Inter-Regular.ttf")
        medium = load(am, "fonts/Inter-Medium.ttf")
        semibold = load(am, "fonts/Inter-SemiBold.ttf")
        display = load(am, "fonts/InterDisplay-Bold.ttf")
    }

    private fun load(am: AssetManager, path: String): Typeface? =
        try { Typeface.createFromAsset(am, path) } catch (e: Exception) { null }

    fun apply(root: View) {
        init(root.context)
        if (root is TextView) {
            val tf = when (root.tag) {
                "display" -> display
                "semibold" -> semibold
                "medium" -> medium
                else -> regular
            }
            if (tf != null) root.typeface = tf
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) apply(root.getChildAt(i))
        }
    }
}
