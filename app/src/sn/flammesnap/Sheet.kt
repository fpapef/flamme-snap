package sn.flammesnap

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Feuille d'actions façon iOS, qui monte du bas de l'écran. */
class Sheet(private val activity: Activity) {

    private class Item(val label: String, val destructive: Boolean, val action: () -> Unit)

    private val items = ArrayList<Item>()
    private var title: String? = null
    private var message: String? = null

    fun title(t: String): Sheet {
        title = t
        return this
    }

    fun message(m: String): Sheet {
        message = m
        return this
    }

    fun add(label: String, action: () -> Unit): Sheet {
        items.add(Item(label, false, action))
        return this
    }

    fun addDestructive(label: String, action: () -> Unit): Sheet {
        items.add(Item(label, true, action))
        return this
    }

    fun show() {
        if (activity.isFinishing || activity.isDestroyed) return
        val d = activity.resources.displayMetrics.density
        fun px(v: Float) = (v * d).toInt()

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(px(8f), 0, px(8f), px(10f))

        val group = carte()
        if (title != null || message != null) {
            val head = LinearLayout(activity)
            head.orientation = LinearLayout.VERTICAL
            head.setPadding(px(18f), px(14f), px(18f), px(14f))
            val t = title
            if (t != null) {
                val tv = texte(t, 13f, R.color.secondary)
                tv.tag = "semibold"
                head.addView(tv)
            }
            val m = message
            if (m != null) {
                val tv = texte(m, 13f, R.color.secondary)
                if (t != null) tv.setPadding(0, px(4f), 0, 0)
                head.addView(tv)
            }
            group.addView(head)
        }
        for (i in items.indices) {
            val item = items[i]
            if (i > 0 || title != null || message != null) group.addView(filet())
            val tv = texte(item.label, 19f, if (item.destructive) R.color.red else R.color.tint)
            tv.minHeight = px(56f)
            tv.setBackgroundResource(R.drawable.bg_row)
            tv.setPadding(px(16f), px(12f), px(16f), px(12f))
            tv.setOnClickListener {
                dialog.dismiss()
                item.action()
            }
            group.addView(tv)
        }
        root.addView(group)

        val cancel = carte()
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = px(8f)
        cancel.layoutParams = lp
        val c = texte(activity.getString(R.string.cancel), 19f, R.color.tint)
        c.tag = "semibold"
        c.minHeight = px(56f)
        c.setBackgroundResource(R.drawable.bg_row)
        c.setOnClickListener { dialog.dismiss() }
        cancel.addView(c)
        root.addView(cancel)

        Typo.apply(root)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        val w = dialog.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.BOTTOM)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.32f)
            w.setWindowAnimations(R.style.SheetAnim)
        }
        dialog.show()
    }

    private fun carte(): LinearLayout {
        val g = LinearLayout(activity)
        g.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.cornerRadius = 14 * activity.resources.displayMetrics.density
        bg.setColor(activity.getColor(R.color.cell))
        g.background = bg
        g.clipToOutline = true
        return g
    }

    private fun texte(s: String, sp: Float, color: Int): TextView {
        val tv = TextView(activity)
        tv.text = s
        tv.textSize = sp
        tv.setTextColor(activity.getColor(color))
        tv.gravity = Gravity.CENTER
        return tv
    }

    private fun filet(): View {
        val v = View(activity)
        v.setBackgroundColor(activity.getColor(R.color.separator))
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        return v
    }
}
