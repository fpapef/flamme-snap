package sn.flammesnap

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Réglages façon app Réglages de l'iPhone : liste des profils (✓ sur le profil actif),
 * champs du profil actif, grand titre qui laisse place au petit titre, enregistrement automatique.
 */
class SettingsActivity : Activity() {

    companion object {
        /** Ouvre les réglages sur le premier champ manquant du profil actif. */
        const val EXTRA_COMPLETE = "complete"
    }

    private lateinit var courant: Profil
    private lateinit var group: LinearLayout
    private lateinit var fields: View
    private lateinit var header: TextView
    private lateinit var nameIn: EditText
    private lateinit var username: EditText
    private lateinit var email: EditText
    private lateinit var phone: EditText
    private lateinit var description: EditText
    private lateinit var lang: Segmented
    private lateinit var check: TextView
    private lateinit var deleteGroup: View
    private lateinit var scroll: ScrollView

    private var chargement = false
    private var nomActif: TextView? = null
    private var sousActif: TextView? = null
    private var avatarActif: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Typo.apply(window.decorView)

        group = findViewById(R.id.profiles_group)
        fields = findViewById(R.id.profile_fields)
        header = findViewById(R.id.profile_header)
        nameIn = findViewById(R.id.in_name)
        username = findViewById(R.id.in_username)
        email = findViewById(R.id.in_email)
        phone = findViewById(R.id.in_phone)
        description = findViewById(R.id.in_description)
        lang = findViewById(R.id.lang_segmented)
        check = findViewById(R.id.account_check)
        deleteGroup = findViewById(R.id.delete_group)
        scroll = findViewById(R.id.scroll)

        val back = findViewById<View>(R.id.back_button)
        val done = findViewById<View>(R.id.done_button)
        back.setOnClickListener { finish() }
        done.setOnClickListener {
            Motion.hapticOk(done)
            finish()
        }
        Motion.highlight(back)
        Motion.highlight(done)
        findViewById<View>(R.id.delete_profile).setOnClickListener { confirmerSuppression(courant) }

        // Version de l'app
        try {
            @Suppress("DEPRECATION")
            val v = packageManager.getPackageInfo(packageName, 0).versionName
            findViewById<TextView>(R.id.version_value).text = v ?: ""
        } catch (e: Exception) {
        }

        // Langue du message : chaque langue garde son propre texte.
        lang.setItems(arrayOf(getString(R.string.lang_fr), getString(R.string.lang_en)))
        lang.listener = { i ->
            garderMessage()
            courant.langue = if (i == 1) "en" else "fr"
            Prefs.enregistrer(this, courant)
            changerTexte(courant.description)
        }

        // Saisie : mise à jour en direct de la ligne du profil et de l'en-tête.
        nameIn.addTextChangedListener(surveiller {
            courant.nom = nameIn.text.toString().trim()
            nomActif?.text = nomAffiche(courant)
            Avatar.majInitiale(avatarActif, courant)
            header.text = getString(R.string.profile_header, nomAffiche(courant))
        })
        username.addTextChangedListener(surveiller {
            courant.pseudo = username.text.toString().trim().trimStart('@').trim()
            majSousTitre()
        })
        val verif = surveiller {
            courant.email = email.text.toString().trim()
            courant.telephone = phone.text.toString().trim()
            majSousTitre()
            verifier(true)
        }
        email.addTextChangedListener(verif)
        phone.addTextChangedListener(verif)

        // Grand titre → petit titre centré + filet, au fil du défilement.
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

        courant = Prefs.actif(this)
        if (savedInstanceState == null) {
            remplirChamps()
        } else {
            // Les champs sont restaurés par Android ; on resynchronise l'état.
            lang.select(if (courant.langue == "en") 1 else 0, false)
            header.text = getString(R.string.profile_header, nomAffiche(courant))
        }
        construireListe(-1L)

        if (savedInstanceState == null) {
            val list = findViewById<ViewGroup>(R.id.settings_list)
            val children = ArrayList<View>()
            for (i in 0 until list.childCount) children.add(list.getChildAt(i))
            Motion.reveal(children, 140, 40, 12f, 0f)
            if (intent.getBooleanExtra(EXTRA_COMPLETE, false)) {
                scroll.postDelayed({ focusChampManquant() }, 450)
            }
        }
    }

    private fun surveiller(action: () -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (!chargement) action()
        }
    }

    private fun nomAffiche(pr: Profil): String =
        if (pr.nom.isBlank()) getString(R.string.profile_unnamed) else pr.nom

    // ---------- liste des profils ----------

    private fun px(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    /** Reconstruit la liste. [nouvelActif] : profil dont la coche apparaît avec un rebond. */
    private fun construireListe(nouvelActif: Long) {
        group.removeAllViews()
        nomActif = null
        sousActif = null
        avatarActif = null
        val profils = Prefs.profils(this)
        for (i in profils.indices) {
            val pr = if (profils[i].id == courant.id) courant else profils[i]
            group.addView(ligneProfil(pr, profils.size > 1, pr.id == nouvelActif))
            group.addView(filet(64f))
        }
        group.addView(ligneAjouter())
        deleteGroup.visibility = if (profils.size > 1) View.VISIBLE else View.GONE
        Typo.apply(group)
    }

    private fun ligneProfil(pr: Profil, plusieurs: Boolean, animerCoche: Boolean): View {
        val actif = pr.id == courant.id
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = px(62f)
        row.setPadding(px(16f), px(10f), px(16f), px(10f))
        row.setBackgroundResource(R.drawable.bg_row)
        row.isClickable = true

        val av = Avatar.creer(this, pr, 34f)
        val alp = LinearLayout.LayoutParams(px(34f), px(34f))
        alp.marginEnd = px(14f)
        row.addView(av, alp)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val nom = TextView(this)
        nom.text = nomAffiche(pr)
        nom.textSize = 17f
        nom.setTextColor(getColor(R.color.label))
        nom.isSingleLine = true
        col.addView(nom)
        val sous = TextView(this)
        remplirSousTitre(sous, pr)
        sous.textSize = 13f
        sous.isSingleLine = true
        col.addView(sous)
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val coche = ImageView(this)
        coche.setImageResource(R.drawable.ic_check)
        coche.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        coche.visibility = if (actif) View.VISIBLE else View.INVISIBLE
        row.addView(coche, LinearLayout.LayoutParams(px(22f), px(22f)))
        if (actif && animerCoche && Motion.animationsOn()) {
            coche.scaleX = 0.3f
            coche.scaleY = 0.3f
            coche.alpha = 0f
            coche.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(80).setDuration(520)
                .setInterpolator(Motion.SPRING).start()
            Motion.pop(av, 0.12f)
        }

        row.contentDescription = if (actif) getString(R.string.profile_active_a11y, nomAffiche(pr)) else nomAffiche(pr)
        row.setOnClickListener { changerProfil(pr.id) }
        row.setOnLongClickListener {
            Motion.hapticOk(row)
            val s = Sheet(this).title(nomAffiche(pr))
            if (!actif) s.add(getString(R.string.use_profile)) { changerProfil(pr.id) }
            if (plusieurs) s.addDestructive(getString(R.string.delete_profile)) { confirmerSuppression(pr) }
            s.show()
            true
        }
        if (actif) {
            nomActif = nom
            sousActif = sous
            avatarActif = av
        }
        return row
    }

    private fun remplirSousTitre(tv: TextView, pr: Profil) {
        if (pr.complet) {
            tv.text = pr.pseudo
            tv.setTextColor(getColor(R.color.secondary))
        } else {
            tv.text = getString(R.string.profile_incomplete)
            tv.setTextColor(getColor(R.color.warn_text))
        }
    }

    private fun majSousTitre() {
        val s = sousActif ?: return
        remplirSousTitre(s, courant)
    }

    private fun ligneAjouter(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = px(56f)
        row.setPadding(px(16f), px(8f), px(16f), px(8f))
        row.setBackgroundResource(R.drawable.bg_row)
        row.isClickable = true
        val plus = ImageView(this)
        plus.setImageResource(R.drawable.ic_plus_circle)
        plus.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val lp = LinearLayout.LayoutParams(px(34f), px(34f))
        lp.marginEnd = px(14f)
        row.addView(plus, lp)
        val t = TextView(this)
        t.text = getString(R.string.add_profile)
        t.textSize = 17f
        t.setTextColor(getColor(R.color.tint))
        row.addView(t)
        row.setOnClickListener { ajouterProfil() }
        return row
    }

    private fun filet(insetDp: Float): View {
        val v = View(this)
        v.setBackgroundColor(getColor(R.color.separator))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        lp.marginStart = px(insetDp)
        v.layoutParams = lp
        return v
    }

    // ---------- actions ----------

    private fun changerProfil(id: Long) {
        if (id == courant.id) return
        sauver()
        Prefs.setActif(this, id)
        courant = Prefs.actif(this)
        Motion.hapticOk(group)
        construireListe(courant.id)
        transitionChamps(null)
    }

    private fun ajouterProfil() {
        sauver()
        courant = Prefs.creer(this)
        Motion.hapticOk(group)
        construireListe(courant.id)
        transitionChamps {
            // On propose tout de suite de nommer le nouveau profil.
            nameIn.requestFocus()
            nameIn.selectAll()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(nameIn, InputMethodManager.SHOW_IMPLICIT)
            scroll.post { scroll.smoothScrollTo(0, Math.max(0, fields.top - px(8f))) }
        }
    }

    private fun confirmerSuppression(pr: Profil) {
        if (Prefs.profils(this).size <= 1) return
        val nom = nomAffiche(pr)
        Sheet(this)
            .message(getString(R.string.delete_profile_confirm, nom))
            .addDestructive(getString(R.string.delete_profile_action, nom)) {
                val etaitActif = pr.id == courant.id
                if (!etaitActif) sauver()
                Prefs.supprimer(this, pr.id)
                courant = Prefs.actif(this)
                Motion.hapticOk(group)
                construireListe(if (etaitActif) courant.id else -1L)
                if (etaitActif) transitionChamps(null)
            }
            .show()
    }

    /** Les champs disparaissent, prennent les valeurs du nouveau profil, puis reviennent. */
    private fun transitionChamps(apres: (() -> Unit)?) {
        if (!Motion.animationsOn()) {
            remplirChamps()
            apres?.invoke()
            return
        }
        val d = Motion.dp(fields, 1f)
        fields.animate().cancel()
        fields.animate().alpha(0f).translationY(-4 * d).setStartDelay(0).setDuration(130)
            .setInterpolator(Motion.IOS).withEndAction {
                remplirChamps()
                fields.translationY = 8 * d
                fields.animate().alpha(1f).translationY(0f).setDuration(380)
                    .setInterpolator(Motion.IOS).start()
                apres?.invoke()
            }.start()
    }

    private fun remplirChamps() {
        chargement = true
        nameIn.setText(courant.nom)
        username.setText(courant.pseudo)
        email.setText(courant.email)
        phone.setText(courant.telephone)
        description.setText(courant.description)
        lang.select(if (courant.langue == "en") 1 else 0, false)
        header.text = getString(R.string.profile_header, nomAffiche(courant))
        chargement = false
        verifier(false)
    }

    private fun focusChampManquant() {
        val champ = when {
            courant.pseudo.isBlank() -> username
            courant.email.isBlank() -> email
            else -> return
        }
        scroll.smoothScrollTo(0, Math.max(0, fields.top - px(8f)))
        champ.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(champ, InputMethodManager.SHOW_IMPLICIT)
    }

    // ---------- enregistrement ----------

    /** Range le message affiché dans la langue en cours du profil. */
    private fun garderMessage() {
        val t = description.text.toString().trim()
        if (courant.langue == "en") courant.descEn = t else courant.descFr = t
    }

    private fun sauver() {
        courant.nom = nameIn.text.toString().trim()
        courant.pseudo = username.text.toString().trim().trimStart('@').trim()
        courant.email = email.text.toString().trim()
        courant.telephone = phone.text.toString().trim()
        garderMessage()
        Prefs.enregistrer(this, courant)
    }

    /** Enregistrement automatique, comme dans l'app Réglages. */
    override fun onPause() {
        super.onPause()
        sauver()
    }

    // ---------- vérification douce ----------

    private fun verifier(animer: Boolean) {
        val e = email.text.toString().trim()
        val p = phone.text.toString().replace(" ", "").replace("-", "").replace(".", "")
        var chiffres = 0
        for (c in p) if (c.isDigit()) chiffres++
        val msg = when {
            e.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(e).matches() -> getString(R.string.check_email)
            p.isNotEmpty() && (!p.startsWith("+") || chiffres < 8) -> getString(R.string.check_phone)
            else -> ""
        }
        if (msg.isEmpty()) {
            if (check.visibility == View.VISIBLE) {
                if (animer && Motion.animationsOn()) {
                    check.animate().cancel()
                    check.animate().alpha(0f).setDuration(180).withEndAction { check.visibility = View.GONE }.start()
                } else {
                    check.visibility = View.GONE
                }
            }
            return
        }
        check.text = msg
        if (check.visibility != View.VISIBLE) {
            check.visibility = View.VISIBLE
            if (animer && Motion.animationsOn()) {
                check.alpha = 0f
                check.animate().cancel()
                check.animate().alpha(1f).setDuration(240).start()
            } else {
                check.alpha = 1f
            }
        } else {
            check.alpha = 1f
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
}
