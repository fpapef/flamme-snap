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
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQ_FORM = 7
        const val ACTION_NEW = "sn.flammesnap.NEW"
        const val ACTION_HISTORY = "sn.flammesnap.HISTORY"
    }

    private lateinit var flame: FlameView
    private lateinit var friendInput: EditText
    private lateinit var continueButton: TextView
    private lateinit var hint: TextView
    private lateinit var summary: TextView
    private lateinit var profileRow: View
    private lateinit var avatarBox: FrameLayout
    private lateinit var recentsBlock: View
    private lateinit var recents: LinearLayout
    private lateinit var paste: ImageView
    private lateinit var friendsRow: View
    private lateinit var friendsCount: TextView
    private var navigating = false
    private var hintIsError = false
    private var modeEffacer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Typo.apply(window.decorView)

        flame = findViewById(R.id.flame)
        friendInput = findViewById(R.id.friend_input)
        continueButton = findViewById(R.id.continue_button)
        hint = findViewById(R.id.hint)
        summary = findViewById(R.id.profile_summary)
        profileRow = findViewById(R.id.profile_row)
        avatarBox = findViewById(R.id.profile_avatar_box)
        profileRow.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        Motion.highlight(profileRow)
        recentsBlock = findViewById(R.id.recents_block)
        recents = findViewById(R.id.recents)
        paste = findViewById(R.id.paste_button)
        friendsRow = findViewById(R.id.friends_row)
        friendsCount = findViewById(R.id.friends_count)
        val settings = findViewById<View>(R.id.settings_button)
        val history = findViewById<View>(R.id.history_button)

        // Les blocs qui apparaissent / disparaissent glissent en douceur.
        findViewById<ViewGroup>(R.id.column).layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)

        continueButton.setOnClickListener { continuer() }
        settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        history.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        paste.setOnClickListener { if (modeEffacer) effacer() else coller() }
        friendsRow.setOnClickListener { startActivity(Intent(this, FriendsActivity::class.java)) }
        Motion.pressScale(continueButton)
        Motion.highlight(settings)
        Motion.highlight(history)
        Motion.highlight(paste)
        Motion.highlight(friendsRow)

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
                majBoutonChamp()
            }
        })

        if (savedInstanceState == null) {
            flame.ignite()
            Motion.reveal(listOf<View>(findViewById(R.id.title), findViewById(R.id.subtitle)), 260, 110, 14f, 28f)
            Motion.reveal(
                listOf<View>(findViewById(R.id.input_box), recentsBlock, friendsRow, continueButton, profileRow),
                560, 60, 18f, 0f
            )
            Motion.reveal(listOf(settings, history), 700, 0, 0f, 0f)
            recevoirPartage(intent)
            raccourci(intent)
        }
        majBoutonChamp()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recevoirPartage(intent)
        raccourci(intent)
    }

    // ---------- raccourcis de l'icône (appui long) ----------

    private fun raccourci(i: Intent?) {
        when (i?.action) {
            ACTION_HISTORY -> {
                setIntent(Intent(this, MainActivity::class.java))
                startActivity(Intent(this, HistoryActivity::class.java))
            }
            ACTION_NEW -> {
                setIntent(Intent(this, MainActivity::class.java))
                friendInput.post {
                    friendInput.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(friendInput, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }

    // ---------- bouton du champ : Coller ↔ Effacer (⊗) ----------

    private fun majBoutonChamp() {
        val effacer = friendInput.text.isNotEmpty()
        if (effacer == modeEffacer) return
        modeEffacer = effacer
        val icone = if (effacer) R.drawable.ic_clear else R.drawable.ic_paste
        paste.contentDescription = getString(if (effacer) R.string.clear else R.string.paste)
        if (!Motion.animationsOn()) {
            paste.setImageResource(icone)
            return
        }
        paste.animate().cancel()
        paste.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(110).withEndAction {
            paste.setImageResource(icone)
            paste.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(Motion.SPRING).start()
        }.start()
    }

    private fun effacer() {
        friendInput.setText("")
        friendInput.requestFocus()
        paste.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun onResume() {
        super.onResume()
        navigating = false
        majResume()
        majRecents()
        majAmis()
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
        val valides = ArrayList<Ami>()
        for (n in noms) if (Pseudos.estValide(n)) valides.add(Ami(n, ""))
        if (Store.ajouterAmis(this, Prefs.actifId(this), valides) > 0) {
            Toast.makeText(this, R.string.share_added, Toast.LENGTH_SHORT).show()
            majAmis()
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
        val libelle = if (noms.size > 1) getString(R.string.continue_n, noms.size)
        else getString(R.string.continue_btn)
        if (continueButton.text.toString() != libelle) {
            if (Motion.animationsOn() && continueButton.isLaidOut) {
                continueButton.animate().cancel()
                continueButton.animate().alpha(0.55f).setDuration(80).withEndAction {
                    continueButton.text = libelle
                    continueButton.animate().alpha(1f).setDuration(220).setInterpolator(Motion.IOS).start()
                }.start()
            } else {
                continueButton.text = libelle
            }
        }
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
        val list = Store.recents(this, Prefs.actifId(this))
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
                    Store.removeRecent(this, Prefs.actifId(this), nom)
                    majRecents()
                }.show()
                true
            }
            recents.addView(chip)
        }
        Typo.apply(recents)
    }

    // ---------- mes amis ----------

    private fun majAmis() {
        val n = Store.amis(this, Prefs.actifId(this)).size
        friendsCount.text = if (n == 0) getString(R.string.home_friends_empty) else getString(R.string.home_friends_count, n)
        friendsRow.contentDescription = getString(R.string.home_friends) + ", " +
            if (n == 0) getString(R.string.friends_empty_title) else resources.getQuantityString(R.plurals.friends_count, n, n)
    }

    // ---------- résumé ----------

    private fun nomAffiche(pr: Profil): String =
        if (pr.nom.isBlank()) getString(R.string.profile_unnamed) else pr.nom

    /** Pastille + nom du profil actif, sous le bouton. */
    private fun majResume() {
        val pr = Prefs.actif(this)
        avatarBox.removeAllViews()
        val d = resources.displayMetrics.density
        avatarBox.addView(Avatar.creer(this, pr, 22f), FrameLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt()))
        if (pr.complet) {
            val n = Store.nbEnvoyees(this, pr.id)
            var s = getString(R.string.profile_summary, nomAffiche(pr), pr.pseudo)
            if (n > 0) s += resources.getQuantityString(R.plurals.summary_sent, n, n)
            summary.text = s
            summary.setTextColor(getColor(R.color.secondary))
        } else {
            summary.text = getString(R.string.profile_summary_incomplete, nomAffiche(pr))
            summary.setTextColor(getColor(R.color.warn_text))
        }
        profileRow.contentDescription = getString(R.string.profile_a11y, nomAffiche(pr))
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
        val pr = Prefs.actif(this)
        if (!pr.complet) {
            // Sans pseudo ni e-mail, le formulaire ne peut pas être rempli : on complète d'abord.
            Motion.shake(profileRow)
            Motion.hapticNo(profileRow)
            montrerAide(getString(R.string.complete_profile_hint, nomAffiche(pr)), R.color.warn_text, true)
            profileRow.postDelayed({
                if (!isFinishing) {
                    val i = Intent(this, SettingsActivity::class.java)
                    i.putExtra(SettingsActivity.EXTRA_COMPLETE, true)
                    startActivity(i)
                }
            }, if (Motion.animationsOn()) 700L else 0L)
            return
        }
        navigating = true
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(friendInput.windowToken, 0)
        friendInput.clearFocus()
        Store.addRecents(this, pr.id, noms)
        Motion.hapticOk(continueButton)
        flame.boost()
        val intent = Intent(this, FormActivity::class.java)
        intent.putStringArrayListExtra(FormActivity.EXTRA_FRIENDS, noms)
        intent.putExtra(FormActivity.EXTRA_PROFILE, pr.id)
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
            cacherAide()
        }
    }
}
