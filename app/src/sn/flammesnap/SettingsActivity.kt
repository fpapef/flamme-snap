package sn.flammesnap

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView

/**
 * Réglages façon app Réglages de l'iPhone : listes groupées, grand titre
 * qui laisse place au petit titre quand on fait défiler, enregistrement automatique.
 */
class SettingsActivity : Activity() {

    private lateinit var username: EditText
    private lateinit var email: EditText
    private lateinit var phone: EditText
    private lateinit var description: EditText
    private lateinit var lang: Segmented
    private var langue = "fr"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Typo.apply(window.decorView)

        username = findViewById(R.id.in_username)
        email = findViewById(R.id.in_email)
        phone = findViewById(R.id.in_phone)
        description = findViewById(R.id.in_description)
        lang = findViewById(R.id.lang_segmented)

        val back = findViewById<View>(R.id.back_button)
        val done = findViewById<View>(R.id.done_button)
        back.setOnClickListener { finish() }
        done.setOnClickListener {
            Motion.hapticOk(done)
            finish()
        }
        Motion.highlight(back)
        Motion.highlight(done)

        // Version de l'app
        try {
            @Suppress("DEPRECATION")
            val v = packageManager.getPackageInfo(packageName, 0).versionName
            findViewById<TextView>(R.id.version_value).text = v ?: ""
        } catch (e: Exception) {
        }

        // Langue du message : chaque langue garde son propre texte.
        langue = Prefs.langue(this)
        lang.setItems(arrayOf(getString(R.string.lang_fr), getString(R.string.lang_en)))
        lang.select(if (langue == "en") 1 else 0, false)
        lang.listener = { i ->
            Prefs.setDescription(this, langue, description.text.toString().trim())
            langue = if (i == 1) "en" else "fr"
            Prefs.setLangue(this, langue)
            changerTexte(Prefs.description(this, langue))
        }

        // Grand titre → petit titre centré + filet, au fil du défilement.
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

        if (savedInstanceState == null) {
            val p = Prefs.lire(this)
            username.setText(p.pseudo)
            email.setText(p.email)
            phone.setText(p.telephone)
            description.setText(p.description)

            val list = findViewById<ViewGroup>(R.id.settings_list)
            val children = ArrayList<View>()
            for (i in 0 until list.childCount) children.add(list.getChildAt(i))
            Motion.reveal(children, 140, 40, 12f, 0f)
        }
    }

    /** Le message change de langue en fondu. */
    private fun changerTexte(texte: String) {
        if (!Motion.animationsOn()) {
            description.setText(texte)
            return
        }
        description.animate().cancel()
        description.animate().alpha(0f).setDuration(120).withEndAction {
            description.setText(texte)
            description.animate().alpha(1f).setDuration(260).setInterpolator(Motion.IOS).start()
        }.start()
    }

    /** Enregistrement automatique, comme dans l'app Réglages. */
    override fun onPause() {
        super.onPause()
        Prefs.enregistrerCompte(
            this,
            username.text.toString().trim().trimStart('@').trim(),
            email.text.toString().trim(),
            phone.text.toString().trim()
        )
        Prefs.setDescription(this, langue, description.text.toString().trim())
    }
}
