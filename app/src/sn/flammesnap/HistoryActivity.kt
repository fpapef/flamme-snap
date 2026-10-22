package sn.flammesnap

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Historique des demandes, regroupées par jour, façon liste groupée iOS. */
class HistoryActivity : Activity() {

    private lateinit var content: LinearLayout
    private lateinit var summary: TextView
    private var premiere = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        Typo.apply(window.decorView)
        content = findViewById(R.id.content)
        summary = findViewById(R.id.hist_summary)

        val back = findViewById<View>(R.id.back_button)
        back.setOnClickListener { finish() }
        Motion.highlight(back)

        val scroll = findViewById<ScrollView>(R.id.scroll)
        val navTitle = findViewById<View>(R.id.nav_title)
        val hairline = findViewById<View>(R.id.nav_hairline)
        val large = findViewById<View>(R.id.large_title)
        scroll.setOnScrollChangeListener(object : View.OnScrollChangeListener {
            override fun onScrollChange(v: View, x: Int, y: Int, oldX: Int, oldY: Int) {
                val h = if (large.height > 0) large.height.toFloat() else 1f
                val f = Motion.clamp01((y - h * 0.35f) / (h * 0.5f))
                navTitle.alpha = f
                navTitle.translationY = (1f - f) * Motion.dp(navTitle, 6f)
                hairline.alpha = Motion.clamp01(y / Motion.dp(hairline, 12f))
            }
        })
    }

    override fun onResume() {
        super.onResume()
        construire(premiere)
        premiere = false
    }

    private fun px(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun construire(animer: Boolean) {
        content.removeAllViews()
        val pr = Prefs.actif(this)
        val pid = pr.id
        val nom = if (pr.nom.isBlank()) getString(R.string.profile_unnamed) else pr.nom
        val data = Store.historique(this, pid)
        if (data.isEmpty()) {
            summary.text = nom
            content.addView(etatVide(nom))
            Typo.apply(content)
            if (animer) Motion.reveal(listOf<View>(content), 120, 0, 12f, 0f)
            return
        }
        var envoyees = 0
        for (d in data) if (d.envoyee) envoyees++
        val nonConf = data.size - envoyees
        var resume = resources.getQuantityString(R.plurals.hist_sent, envoyees, envoyees)
        if (nonConf > 0) resume += " · " + resources.getQuantityString(R.plurals.hist_unconfirmed, nonConf, nonConf)
        summary.text = "$nom · $resume"

        val jour = SimpleDateFormat("yyyyMMdd", Locale.FRANCE)
        val heure = SimpleDateFormat("HH:mm", Locale.FRANCE)
        val titreJour = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)
        val auj = jour.format(Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val hier = jour.format(cal.time)

        var cleCourante = ""
        var groupe: LinearLayout? = null
        for (d in data) {
            val cle = jour.format(Date(d.date))
            if (cle != cleCourante || groupe == null) {
                cleCourante = cle
                val titre = when (cle) {
                    auj -> getString(R.string.today)
                    hier -> getString(R.string.yesterday)
                    else -> {
                        val t = titreJour.format(Date(d.date))
                        t.substring(0, 1).uppercase(Locale.FRANCE) + t.substring(1)
                    }
                }
                content.addView(entete(titre))
                val g = carte()
                content.addView(g)
                groupe = g
            } else {
                groupe.addView(filet())
            }
            groupe.addView(ligne(d, heure, pid))
        }

        // Effacer l'historique
        content.addView(espace(28f))
        val g = carte()
        val clear = TextView(this)
        clear.text = getString(R.string.clear_history)
        clear.textSize = 17f
        clear.setTextColor(getColor(R.color.red))
        clear.gravity = Gravity.CENTER_VERTICAL
        clear.minHeight = px(52f)
        clear.setPadding(px(16f), 0, px(16f), 0)
        clear.setBackgroundResource(R.drawable.bg_row)
        clear.setOnClickListener {
            Sheet(this).message(getString(R.string.clear_confirm, nom))
                .addDestructive(getString(R.string.clear_history)) {
                    Store.effacer(this, pid)
                    construire(true)
                }.show()
        }
        g.addView(clear)
        content.addView(g)

        Typo.apply(content)
        if (animer) {
            val enfants = ArrayList<View>()
            for (i in 0 until content.childCount) enfants.add(content.getChildAt(i))
            Motion.reveal(enfants, 100, 35, 12f, 0f)
        }
    }

    private fun ligne(d: Demande, heure: SimpleDateFormat, pid: Long): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = px(62f)
        row.setPadding(px(16f), px(10f), px(16f), px(10f))
        row.setBackgroundResource(R.drawable.bg_row)
        row.isClickable = true

        val dot = View(this)
        val dotBg = GradientDrawable()
        dotBg.shape = GradientDrawable.OVAL
        dotBg.setColor(getColor(if (d.envoyee) R.color.green else R.color.orange))
        dot.background = dotBg
        val dlp = LinearLayout.LayoutParams(px(8f), px(8f))
        dlp.marginEnd = px(14f)
        row.addView(dot, dlp)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val nom = TextView(this)
        nom.text = d.ami
        nom.textSize = 17f
        nom.setTextColor(getColor(R.color.label))
        col.addView(nom)
        val sous = TextView(this)
        sous.text = if (d.envoyee) getString(R.string.sent_at, heure.format(Date(d.envoyeeLe)))
        else getString(R.string.filled_at, heure.format(Date(d.date)))
        sous.textSize = 13f
        sous.setTextColor(getColor(R.color.secondary))
        col.addView(sous)
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val chevron = ImageView(this)
        chevron.setImageResource(R.drawable.ic_chevron_right)
        chevron.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.addView(chevron)

        row.setOnClickListener { actions(d, pid) }
        return row
    }

    private fun actions(d: Demande, pid: Long) {
        val s = Sheet(this).title(d.ami)
        s.add(getString(R.string.redo)) {
            val i = Intent(this, FormActivity::class.java)
            val l = ArrayList<String>()
            l.add(d.ami)
            i.putStringArrayListExtra(FormActivity.EXTRA_FRIENDS, l)
            i.putExtra(FormActivity.EXTRA_PROFILE, pid)
            startActivity(i)
        }
        if (!d.envoyee) {
            s.add(getString(R.string.mark_sent)) {
                Store.marquerEnvoyee(this, pid, d.id)
                Motion.hapticOk(content)
                construire(false)
            }
        }
        s.addDestructive(getString(R.string.delete)) {
            Store.supprimer(this, pid, d.id)
            construire(false)
        }
        s.show()
    }

    private fun etatVide(nom: String): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(px(32f), px(110f), px(32f), 0)
        val icon = ImageView(this)
        icon.setImageResource(R.drawable.ic_clock)
        icon.alpha = 0.35f
        col.addView(icon, LinearLayout.LayoutParams(px(44f), px(44f)))
        val t = TextView(this)
        t.text = getString(R.string.hist_empty)
        t.tag = "semibold"
        t.textSize = 17f
        t.gravity = Gravity.CENTER
        t.setTextColor(getColor(R.color.label))
        t.setPadding(0, px(16f), 0, 0)
        col.addView(t)
        val s = TextView(this)
        s.text = getString(R.string.hist_empty_detail, nom)
        s.textSize = 15f
        s.gravity = Gravity.CENTER
        s.setTextColor(getColor(R.color.secondary))
        s.setPadding(0, px(6f), 0, 0)
        col.addView(s)
        return col
    }

    private fun entete(titre: String): View {
        val tv = TextView(this)
        tv.text = titre
        tv.textSize = 13f
        tv.setTextColor(getColor(R.color.secondary))
        tv.setPadding(px(32f), px(26f), px(32f), px(8f))
        return tv
    }

    private fun carte(): LinearLayout {
        val g = LinearLayout(this)
        g.orientation = LinearLayout.VERTICAL
        g.setBackgroundResource(R.drawable.bg_group)
        g.clipToOutline = true
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginStart = px(16f)
        lp.marginEnd = px(16f)
        g.layoutParams = lp
        return g
    }

    private fun filet(): View {
        val v = View(this)
        v.setBackgroundColor(getColor(R.color.separator))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        lp.marginStart = px(38f)
        v.layoutParams = lp
        return v
    }

    private fun espace(dp: Float): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(1, px(dp))
        return v
    }
}
