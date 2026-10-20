package sn.flammesnap

import android.animation.LayoutTransition
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQ_FORM = 7
    }

    private lateinit var flame: FlameView
    private lateinit var friendInput: EditText
    private lateinit var continueButton: TextView
    private lateinit var hint: TextView
    private lateinit var summary: TextView
    private lateinit var recentsBlock: View
    private lateinit var recents: LinearLayout
    private lateinit var details: View
    private lateinit var detailsChevron: View
    private lateinit var daysInput: EditText
    private lateinit var lost: Segmented
    private lateinit var paste: View
    private var navigating = false
    private var hintIsError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Typo.apply(window.decorView)

        flame = findViewById(R.id.flame)
        friendInput = findViewById(R.id.friend_input)
        continueButton = findViewById(R.id.continue_button)
        hint = findViewById(R.id.hint)
        summary = findViewById(R.id.profile_summary)
        recentsBlock = findViewById(R.id.recents_block)
        recents = findViewById(R.id.recents)
        details = findViewById(R.id.details)
        detailsChevron = findViewById(R.id.details_chevron)
        daysInput = findViewById(R.id.days_input)
        lost = findViewById(R.id.lost_segmented)
        paste = findViewById(R.id.paste_button)
        val settings = findViewById<View>(R.id.settings_button)
        val history = findViewById<View>(R.id.history_button)
        val detailsToggle = findViewById<View>(R.id.details_toggle)

        // Les blocs qui apparaissent / disparaissent glissent en douceur.
        findViewById<ViewGroup>(R.id.column).layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)

        continueButton.setOnClickListener { continuer() }
        settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        history.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        paste.setOnClickListener { coller() }
        detailsToggle.setOnClickListener { basculerDetails() }
        Motion.pressScale(continueButton)
        Motion.highlight(settings)
        Motion.highlight(history)
        Motion.highlight(paste)
        Motion.highlight(detailsToggle)

        lost.setItems(arrayOf(getString(R.string.lost_today), getString(R.string.lost_yesterday), getString(R.string.lost_before)))
        lost.allowNone = true

        friendInput.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                val entree = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || entree) {
                    continuer()
                    return true
                }
                return false
            }
        })

        friendInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > before) flame.kick()
            }
            override fun afterTextChanged(s: Editable?) {
                majAide()
            }
        })

        if (savedInstanceState == null) {
            flame.ignite()
            Motion.reveal(listOf<View>(findViewById(R.id.title), findViewById(R.id.subtitle)), 260, 110, 14f, 28f)
            Motion.reveal(
                listOf<View>(findViewById(R.id.input_box), recentsBlock, detailsToggle, continueButton, summary),
                560, 60, 18f, 0f
            )
            Motion.reveal(listOf(settings, history), 700, 0, 0f, 0f)
            recevoirPartage(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recevoirPartage(intent)
    }

    override fun onResume() {
        super.onResume()
        navigating = false
        majResume()
        majRecents()
    }

    // ---------- partage depuis Snapchat / presse-papiers ----------

    private fun recevoirPartage(i: Intent?) {
        if (i == null || i.action != Intent.ACTION_SEND) return
        val texte = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
        setIntent(Intent(this, MainActivity::class.java))
        val noms = Pseudos.depuisTexte(texte)
        if (noms.isEmpty()) {
            Toast.makeText(this, R.string.share_none, Toast.LENGTH_LONG).show()
            return
        }
        ajouterNoms(noms)
    }

    private fun coller() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val texte = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString() ?: ""
        } else ""
        val noms = Pseudos.depuisTexte(texte)
        if (noms.isEmpty()) {
            Motion.shake(paste)
            Motion.hapticNo(paste)
            montrerAide(getString(R.string.nothing_to_paste), R.color.secondary, false)
            return
        }
        Motion.hapticOk(paste)
        ajouterNoms(noms)
    }

    private fun ajouterNoms(noms: List<String>) {
        val cur = Pseudos.depuisSaisie(friendInput.text.toString())
        for (n in noms) {
            var dup = false
            for (c in cur) if (c.equals(n, ignoreCase = true)) dup = true
            if (!dup && cur.size < Pseudos.MAX) cur.add(n)
        }
        val texte = cur.joinToString(" ")
        friendInput.setText(texte)
        friendInput.setSelection(texte.length)
        flame.kick()
    }

    // ---------- aide sous le champ ----------

    private fun majAide() {
        val noms = Pseudos.depuisSaisie(friendInput.text.toString())
        continueButton.text = if (noms.size > 1) getString(R.string.continue_n, noms.size)
        else getString(R.string.continue_btn)
        var invalide: String? = null
        for (n in noms) if (!Pseudos.estValide(n)) { invalide = n; break }
        when {
            invalide != null -> montrerAide(getString(R.string.invalid_hint, invalide), R.color.tint, false)
            noms.size > 1 -> montrerAide(getString(R.string.multi_hint, noms.size), R.color.secondary, false)
            else -> cacherAide()
        }
    }

    private fun montrerAide(texte: String, couleur: Int, erreur: Boolean) {
        hintIsError = erreur
        hint.text = texte
        hint.setTextColor(getColor(couleur))
        hint.animate().cancel()
        hint.animate().alpha(1f).setDuration(220).start()
    }

    private fun cacherAide() {
        if (hint.alpha == 0f) return
        hint.animate().cancel()
        hint.animate().alpha(0f).setDuration(200).start()
    }

    // ---------- amis récents ----------

    private fun majRecents() {
        recents.removeAllViews()
        val list = Store.recents(this)
        recentsBlock.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        val d = resources.displayMetrics.density
        for (nom in list) {
            val chip = TextView(this, null, 0, R.style.Chip)
            chip.text = nom
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (36 * d).toInt())
            lp.marginEnd = (8 * d).toInt()
            chip.layoutParams = lp
            chip.isClickable = true
            chip.setOnClickListener {
                Motion.pop(chip, 0.06f)
                ajouterNoms(listOf(nom))
            }
            chip.setOnLongClickListener {
                Motion.hapticOk(chip)
                Sheet(this).title(nom).addDestructive(getString(R.string.remove_recent)) {
                    Store.removeRecent(this, nom)
                    majRecents()
                }.show()
                true
            }
            recents.addView(chip)
        }
        Typo.apply(recents)
    }

    // ---------- détails facultatifs ----------

    private fun basculerDetails() {
        val ouvrir = details.visibility != View.VISIBLE
        details.visibility = if (ouvrir) View.VISIBLE else View.GONE
        detailsChevron.animate().rotation(if (ouvrir) 90f else 0f).setDuration(300)
            .setInterpolator(Motion.IOS).start()
        if (ouvrir) Motion.reveal(listOf(details), 0, 0, 8f, 0f)
    }

    private fun jours(): Int = try {
        Integer.parseInt(daysInput.text.toString().trim())
    } catch (e: Exception) {
        0
    }

    // ---------- résumé ----------

    private fun majResume() {
        val n = Store.nbEnvoyees(this)
        var s = getString(R.string.profile_summary, Prefs.lire(this).pseudo)
        if (n == 1) s += getString(R.string.summary_sent_one)
        else if (n > 1) s += getString(R.string.summary_sent_many, n)
        summary.text = s
    }

    // ---------- continuer ----------

    private fun continuer() {
        if (navigating) return
        val noms = Pseudos.depuisSaisie(friendInput.text.toString())
        if (noms.isEmpty()) {
            friendInput.requestFocus()
            Motion.shake(friendInput)
            Motion.hapticNo(friendInput)
            montrerAide(getString(R.string.err_friend_empty), R.color.red, true)
            flame.dim()
            return
        }
        navigating = true
        Store.addRecents(this, noms)
        Motion.hapticOk(continueButton)
        flame.boost()
        val intent = Intent(this, FormActivity::class.java)
        intent.putStringArrayListExtra(FormActivity.EXTRA_FRIENDS, noms)
        intent.putExtra(FormActivity.EXTRA_DAYS, jours())
        intent.putExtra(FormActivity.EXTRA_LOST, lost.selected)
        // La flamme s'embrase un court instant, puis l'écran suivant glisse.
        continueButton.postDelayed({
            if (!isFinishing) startActivityForResult(intent, REQ_FORM)
        }, if (Motion.animationsOn()) 240L else 0L)
    }

    @Deprecated("API Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FORM && resultCode == RESULT_OK) {
            // Au moins une demande envoyée : on repart d'une page blanche.
            friendInput.setText("")
            daysInput.setText("")
            lost.select(-1, false)
            if (details.visibility == View.VISIBLE) basculerDetails()
            cacherAide()
        }
    }
}
