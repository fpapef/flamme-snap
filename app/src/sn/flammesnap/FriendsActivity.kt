package sn.flammesnap

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.Normalizer

/**
 * « Mes amis » : la liste des amis Snapchat du profil actif.
 * On y ajoute un pseudo, on colle une liste, ou on importe l'export « Mes données » de Snapchat.
 * Toucher des amis les sélectionne ; « Restaurer (n) » lance les demandes à la suite.
 */
class FriendsActivity : Activity() {

    companion object {
        private const val REQ_IMPORT = 11
        private const val REQ_FORM = 12
    }

    private lateinit var profil: Profil
    private lateinit var list: ListView
    private lateinit var header: View
    private lateinit var largeTitle: View
    private lateinit var subtitle: TextView
    private lateinit var search: EditText
    private lateinit var addBox: View
    private lateinit var addInput: EditText
    private lateinit var banner: TextView
    private lateinit var importNote: View
    private lateinit var section: TextView
    private lateinit var emptyBox: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDetail: TextView
    private lateinit var clearRow: View
    private lateinit var bar: View
    private lateinit var restoreButton: TextView
    private lateinit var cancelButton: View
    private lateinit var navTitle: View
    private lateinit var hairline: View

    private var tous = ArrayList<Ami>()
    private var visibles = ArrayList<Ami>()
    private val selection = LinkedHashSet<String>()   // pseudos en minuscules
    private val adaptateur = Adaptateur()
    private val handler = Handler(Looper.getMainLooper())
    private var barreVisible = false
    private var importEnCours = false
    private val cacherBanniere = Runnable { masquerBanniere() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)

        list = findViewById(R.id.list)
        bar = findViewById(R.id.restore_bar)
        restoreButton = findViewById(R.id.restore_button)
        cancelButton = findViewById(R.id.cancel_button)
        navTitle = findViewById(R.id.nav_title)
        hairline = findViewById(R.id.nav_hairline)

        val inflater = LayoutInflater.from(this)
        header = inflater.inflate(R.layout.friends_header, list, false)
        val footer = inflater.inflate(R.layout.friends_footer, list, false)
        largeTitle = header.findViewById(R.id.large_title)
        subtitle = header.findViewById(R.id.subtitle)
        search = header.findViewById(R.id.search)
        addBox = header.findViewById(R.id.add_box)
        addInput = header.findViewById(R.id.add_input)
        banner = header.findViewById(R.id.banner)
        importNote = header.findViewById(R.id.import_note)
        section = header.findViewById(R.id.section)
        emptyBox = footer.findViewById(R.id.empty_box)
        emptyTitle = footer.findViewById(R.id.empty_title)
        emptyDetail = footer.findViewById(R.id.empty_detail)
        clearRow = footer.findViewById(R.id.clear_row)
        list.addHeaderView(header, null, false)
        list.addFooterView(footer, null, false)
        list.adapter = adaptateur
        // Les champs Recherche / Ajouter (dans l'en-tête) gardent le focus quand le clavier s'ouvre.
        list.itemsCanFocus = true
        Typo.apply(window.decorView)
        Typo.apply(header)
        Typo.apply(footer)

        val back = findViewById<View>(R.id.back_button)
        val add = header.findViewById<View>(R.id.add_button)
        val paste = header.findViewById<View>(R.id.paste_row)
        val imp = header.findViewById<View>(R.id.import_row)
        back.setOnClickListener { finish() }
        cancelButton.setOnClickListener { toutDeselectionner() }
        add.setOnClickListener { ajouterSaisie() }
        paste.setOnClickListener { coller() }
        imp.setOnClickListener { importer() }
        clearRow.setOnClickListener { confirmerEffacement() }
        restoreButton.setOnClickListener { restaurerSelection() }
        for (v in listOf(back, cancelButton, add, clearRow)) Motion.highlight(v)
        Motion.pressScale(restoreButton)

        addInput.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                val entree = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_DONE || entree) {
                    ajouterSaisie()
                    return true
                }
                return false
            }
        })
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filtrer()
            }
        })

        list.onItemClickListener = object : AdapterView.OnItemClickListener {
            override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val i = position - list.headerViewsCount
                if (i < 0 || i >= visibles.size) return
                basculer(visibles[i], view)
            }
        }
        list.onItemLongClickListener = object : AdapterView.OnItemLongClickListener {
            override fun onItemLongClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Boolean {
                val i = position - list.headerViewsCount
                if (i < 0 || i >= visibles.size) return false
                actions(visibles[i])
                return true
            }
        }

        // Grand titre → petit titre centré + filet, au fil du défilement.
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
            override fun onScroll(view: AbsListView?, first: Int, visibleCount: Int, total: Int) {
                val y = if (first == 0 && list.childCount > 0) -list.getChildAt(0).top else 10000
                val h = if (largeTitle.height > 0) largeTitle.height.toFloat() else 1f
                val f = Motion.clamp01((y - h * 0.35f) / (h * 0.5f))
                navTitle.alpha = f
                navTitle.translationY = (1f - f) * Motion.dp(navTitle, 6f)
                hairline.alpha = Motion.clamp01(y / Motion.dp(hairline, 12f))
            }
        })

        profil = Prefs.actif(this)
        if (savedInstanceState != null) {
            val sel = savedInstanceState.getStringArrayList("selection")
            if (sel != null) selection.addAll(sel)
        } else {
            if (Motion.animationsOn()) Motion.reveal(listOf(list), 120, 0, 12f, 0f)
            fichierRecu(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        fichierRecu(intent)
    }

    /** L'export Snapchat ouvert ou partagé depuis une autre app (Fichiers, navigateur…). */
    private fun fichierRecu(i: Intent?) {
        if (i == null) return
        val uri: Uri? = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> flux(i)
            else -> null
        }
        if (uri == null) return
        setIntent(Intent(this, FriendsActivity::class.java))
        profil = Prefs.actif(this)
        lireFichier(uri)
    }

    @Suppress("DEPRECATION")
    private fun flux(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    override fun onResume() {
        super.onResume()
        profil = Prefs.actif(this)
        recharger()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("selection", ArrayList(selection))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------- données ----------

    private fun nomProfil(): String = if (profil.nom.isBlank()) getString(R.string.profile_unnamed) else profil.nom

    private fun recharger() {
        tous = Store.amis(this, profil.id)
        // La sélection ne garde que les amis encore présents.
        val presents = HashSet<String>()
        for (a in tous) presents.add(a.pseudo.lowercase())
        selection.retainAll(presents)
        subtitle.text = getString(R.string.friends_subtitle,
            resources.getQuantityString(R.plurals.friends_count, tous.size, tous.size), nomProfil())
        // La marche à suivre pour l'export n'est utile que tant que la liste est vide.
        importNote.visibility = if (tous.isEmpty()) View.VISIBLE else View.GONE
        filtrer()
        majBarre(false)
    }

    private fun normaliser(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    private fun filtrer() {
        val q = normaliser(search.text.toString().trim())
        visibles = if (q.isEmpty()) ArrayList(tous) else {
            val out = ArrayList<Ami>()
            for (a in tous) if (normaliser(a.pseudo).contains(q) || normaliser(a.nom).contains(q)) out.add(a)
            out
        }
        adaptateur.notifyDataSetChanged()
        when {
            tous.isEmpty() -> {
                section.visibility = View.GONE
                emptyBox.visibility = View.VISIBLE
                emptyTitle.text = getString(R.string.friends_empty_title)
                emptyDetail.text = getString(R.string.friends_empty_detail)
                clearRow.visibility = View.GONE
            }
            visibles.isEmpty() -> {
                section.visibility = View.GONE
                emptyBox.visibility = View.VISIBLE
                emptyTitle.text = getString(R.string.friends_none_found)
                emptyDetail.text = getString(R.string.friends_none_found_detail, search.text.toString().trim())
                clearRow.visibility = View.GONE
            }
            else -> {
                section.visibility = View.VISIBLE
                section.text = if (q.isEmpty()) getString(R.string.friends_section_hint,
                    resources.getQuantityString(R.plurals.friends_count, tous.size, tous.size))
                else getString(R.string.friends_results)
                emptyBox.visibility = View.GONE
                clearRow.visibility = if (q.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    // ---------- sélection ----------

    private fun basculer(a: Ami, vue: View?) {
        val cle = a.pseudo.lowercase()
        val choisi = if (selection.contains(cle)) { selection.remove(cle); false } else { selection.add(cle); true }
        val coche = vue?.findViewById<ImageView>(R.id.select)
        if (coche != null) {
            coche.setImageResource(if (choisi) R.drawable.ic_select_on else R.drawable.ic_select_off)
            if (choisi) Motion.pop(coche, 0.25f)
        }
        vue?.contentDescription = if (choisi) getString(R.string.friend_selected_a11y, afficher(a)) else afficher(a)
        vue?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        majBarre(true)
    }

    private fun toutDeselectionner() {
        selection.clear()
        adaptateur.notifyDataSetChanged()
        majBarre(true)
    }

    /** Le bouton « Restaurer (n) » monte avec un ressort quand on sélectionne, redescend quand on vide. */
    private fun majBarre(animer: Boolean) {
        val n = selection.size
        if (n > 0) restoreButton.text = resources.getQuantityString(R.plurals.restore_n, n, n)
        cancelButton.visibility = if (n > 0) View.VISIBLE else View.GONE
        val montrer = n > 0
        if (montrer == barreVisible) return
        barreVisible = montrer
        bar.animate().cancel()
        val bas = Motion.dp(bar, 140f)
        if (!animer || !Motion.animationsOn()) {
            bar.visibility = if (montrer) View.VISIBLE else View.INVISIBLE
            bar.translationY = 0f
            return
        }
        if (montrer) {
            bar.visibility = View.VISIBLE
            bar.translationY = bas
            bar.animate().translationY(0f).setDuration(520).setInterpolator(Motion.SOFT_SPRING).start()
        } else {
            bar.animate().translationY(bas).setDuration(260).setInterpolator(Motion.IOS)
                .withEndAction { bar.visibility = View.INVISIBLE; bar.translationY = 0f }.start()
        }
    }

    private fun selectionnes(): ArrayList<String> {
        val out = ArrayList<String>()
        for (a in tous) if (selection.contains(a.pseudo.lowercase())) out.add(a.pseudo)
        return out
    }

    // ---------- restaurer ----------

    private fun restaurerSelection() {
        val noms = selectionnes()
        if (noms.isNotEmpty()) restaurer(noms)
    }

    private fun restaurer(noms: ArrayList<String>) {
        if (!profil.complet) {
            // Sans pseudo ni e-mail, le formulaire ne peut pas être rempli : on complète d'abord.
            val i = Intent(this, SettingsActivity::class.java)
            i.putExtra(SettingsActivity.EXTRA_COMPLETE, true)
            startActivity(i)
            return
        }
        Motion.hapticOk(list)
        Store.addRecents(this, profil.id, noms)
        val i = Intent(this, FormActivity::class.java)
        i.putStringArrayListExtra(FormActivity.EXTRA_FRIENDS, noms)
        i.putExtra(FormActivity.EXTRA_PROFILE, profil.id)
        startActivityForResult(i, REQ_FORM)
    }

    // ---------- ajouter ----------

    private fun ajouterSaisie() {
        val noms = Pseudos.depuisSaisie(addInput.text.toString())
        if (noms.isEmpty()) {
            Motion.shake(addBox)
            Motion.hapticNo(addBox)
            return
        }
        var invalide: String? = null
        val ok = ArrayList<Ami>()
        for (n in noms) if (Pseudos.estValide(n)) ok.add(Ami(n, "")) else if (invalide == null) invalide = n
        val ajoutes = Store.ajouterAmis(this, profil.id, ok)
        if (invalide != null) {
            montrer(getString(R.string.invalid_hint, invalide), true)
            Motion.shake(addBox)
            Motion.hapticNo(addBox)
        } else {
            addInput.setText("")
            if (ajoutes > 0) {
                montrer(resources.getQuantityString(R.plurals.friends_added, ajoutes, ajoutes), false)
                Motion.hapticOk(addBox)
            } else {
                montrer(getString(R.string.friends_already), false)
            }
        }
        recharger()
    }

    private fun coller() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val texte = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() ?: "" else ""
        val noms = Pseudos.depuisTexte(texte)
        if (noms.isEmpty()) {
            montrer(getString(R.string.nothing_to_paste), true)
            Motion.hapticNo(list)
            return
        }
        val amis = ArrayList<Ami>()
        for (n in noms) amis.add(Ami(n, ""))
        val ajoutes = Store.ajouterAmis(this, profil.id, amis)
        montrer(if (ajoutes > 0) resources.getQuantityString(R.plurals.friends_added, ajoutes, ajoutes)
        else getString(R.string.friends_already), false)
        if (ajoutes > 0) Motion.hapticOk(list)
        recharger()
    }

    // ---------- importer l'export Snapchat ----------

    private fun importer() {
        if (importEnCours) return
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        i.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
            "application/zip", "application/x-zip-compressed", "application/json",
            "application/octet-stream", "text/plain"))
        try {
            startActivityForResult(i, REQ_IMPORT)
        } catch (e: ActivityNotFoundException) {
            montrer(getString(R.string.import_unreadable), true)
        }
    }

    private fun lireFichier(uri: Uri) {
        importEnCours = true
        montrer(getString(R.string.import_reading), false, false)
        val pid = profil.id
        Thread(object : Runnable {
            override fun run() {
                var res: ImportSnap.Resultat? = null
                try {
                    val flux = contentResolver.openInputStream(uri)
                    if (flux != null) {
                        try {
                            res = ImportSnap.lire(flux)
                        } finally {
                            flux.close()
                        }
                    }
                } catch (e: Exception) {
                    res = null
                }
                val r = res
                val ajoutes = if (r != null && r.erreur == ImportSnap.OK && r.amis.isNotEmpty())
                    Store.ajouterAmis(this@FriendsActivity, pid, r.amis) else 0
                runOnUiThread(object : Runnable {
                    override fun run() {
                        importEnCours = false
                        if (isDestroyed) return
                        when {
                            r == null || r.erreur == ImportSnap.ILLISIBLE -> montrer(getString(R.string.import_unreadable), true)
                            r.erreur == ImportSnap.PAS_DE_LISTE -> montrer(getString(R.string.import_no_list), true)
                            r.amis.isEmpty() -> montrer(getString(R.string.import_empty), true)
                            else -> montrer(resources.getQuantityString(R.plurals.import_done, ajoutes, ajoutes, r.amis.size), false)
                        }
                        if (r != null && r.amis.isNotEmpty()) Motion.hapticOk(list) else Motion.hapticNo(list)
                        recharger()
                    }
                })
            }
        }).start()
    }

    @Deprecated("API Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) lireFichier(uri)
        } else if (requestCode == REQ_FORM && resultCode == RESULT_OK) {
            // Au moins une demande envoyée : on repart d'une sélection vide.
            toutDeselectionner()
        }
    }

    // ---------- actions sur un ami ----------

    private fun afficher(a: Ami): String = if (a.nom.isNotBlank()) a.nom else a.pseudo

    private fun actions(a: Ami) {
        Motion.hapticOk(list)
        Sheet(this).title(afficher(a))
            .add(getString(R.string.row_restore)) { restaurer(arrayListOf(a.pseudo)) }
            .add(getString(R.string.row_copy)) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Flamme Snap", a.pseudo))
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            }
            .addDestructive(getString(R.string.row_remove)) {
                Store.supprimerAmis(this, profil.id, listOf(a.pseudo))
                recharger()
            }
            .show()
    }

    private fun confirmerEffacement() {
        val n = tous.size
        if (n == 0) return
        Sheet(this)
            .message(resources.getQuantityString(R.plurals.clear_list_confirm, n, n, nomProfil()))
            .addDestructive(getString(R.string.clear_list)) {
                Store.effacerAmis(this, profil.id)
                selection.clear()
                recharger()
            }
            .show()
    }

    // ---------- message sous les actions ----------

    private fun montrer(texte: String, alerte: Boolean, masquerApres: Boolean = true) {
        handler.removeCallbacks(cacherBanniere)
        banner.text = texte
        banner.setTextColor(getColor(if (alerte) R.color.warn_text else R.color.secondary))
        if (banner.visibility != View.VISIBLE) {
            banner.visibility = View.VISIBLE
            if (Motion.animationsOn()) {
                banner.alpha = 0f
                banner.animate().alpha(1f).setDuration(220).start()
            }
        }
        if (masquerApres) handler.postDelayed(cacherBanniere, 6000)
        if (addInput.hasFocus() && !alerte) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(addInput.windowToken, 0)
        }
    }

    private fun masquerBanniere() {
        if (banner.visibility != View.VISIBLE) return
        banner.animate().alpha(0f).setDuration(220).withEndAction { banner.visibility = View.GONE }.start()
    }

    // ---------- lignes de la liste ----------

    private inner class Adaptateur : BaseAdapter() {
        override fun getCount(): Int = visibles.size
        override fun getItem(position: Int): Any = visibles[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(this@FriendsActivity).inflate(R.layout.friend_row, parent, false)
                .also { Typo.apply(it) }
            val a = visibles[position]
            val av = v.findViewById<TextView>(R.id.avatar)
            val nom = v.findViewById<TextView>(R.id.name)
            val handle = v.findViewById<TextView>(R.id.handle)
            val coche = v.findViewById<ImageView>(R.id.select)
            val sep = v.findViewById<View>(R.id.separator)
            val titre = afficher(a)
            nom.text = titre
            if (a.nom.isNotBlank()) {
                handle.text = a.pseudo
                handle.visibility = View.VISIBLE
            } else {
                handle.visibility = View.GONE
            }
            av.text = titre.trim().take(1).uppercase()
            val bg = (av.background as? GradientDrawable) ?: GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                av.background = it
            }
            val c = Prefs.COULEURS
            bg.setColor(c[(a.pseudo.lowercase().hashCode() and 0x7fffffff) % c.size])
            val choisi = selection.contains(a.pseudo.lowercase())
            coche.setImageResource(if (choisi) R.drawable.ic_select_on else R.drawable.ic_select_off)
            coche.scaleX = 1f
            coche.scaleY = 1f
            sep.visibility = if (position == visibles.size - 1) View.INVISIBLE else View.VISIBLE
            v.contentDescription = if (choisi) getString(R.string.friend_selected_a11y, titre) else titre
            return v
        }
    }
}
