package sn.flammesnap

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Ouvre le formulaire officiel de Snapchat et remplit les champs, ami après ami.
 * L'app ne coche jamais le reCAPTCHA et n'envoie jamais le formulaire :
 * c'est l'utilisateur qui le fait. Elle repère seulement que l'envoi a eu lieu.
 */
class FormActivity : Activity() {

    companion object {
        const val EXTRA_FRIENDS = "friends"
        const val EXTRA_DAYS = "days"
        const val EXTRA_LOST = "lost"
        const val FORM_URL =
            "https://help.snapchat.com/hc/fr-fr/requests/new?co=true&ticket_form_id=149423"
        private const val REQ_FILE = 42

        /** Envoi d'une demande Zendesk : POST vers …/requests. */
        private val ENVOI = Pattern.compile("/requests(\\.json)?/?$")

        /** Après l'envoi : le formulaire est-il toujours là ? un message de succès ? */
        private const val VERIF_JS = """(function(){
  try {
    var ls = document.querySelectorAll('label,legend'), form = false;
    for (var i = 0; i < ls.length; i++) {
      var t = (ls[i].textContent || '').toLowerCase();
      if (t.indexOf('type de conversation') >= 0 || t.indexOf('conversation type') >= 0 || t.indexOf('votre ami') >= 0) { form = true; break; }
    }
    if (document.querySelector('.notification-error, .notification-alert, [role=alert] .error')) form = true;
    var b = (document.body ? document.body.innerText : '').toLowerCase();
    var ok = /(demande a (bien )?(été|ete) (envoy|soumis|transmis|reçu|recu))|(request (has been|was) (successfully )?(submitted|sent|received))|(nous avons (bien )?re(ç|c)u)|(we.ve received)/.test(b);
    return { form: form, ok: ok, ready: document.readyState === 'complete' };
  } catch (e) { return { form: true, ok: false, ready: true }; }
})()"""
    }

    private lateinit var web: WebView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusTexts: View
    private lateinit var badge: StatusBadge
    private lateinit var card: View
    private lateinit var progress: GlowProgress
    private lateinit var navTitle: TextView
    private lateinit var nextButton: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var amis = ArrayList<String>()
    private var index = 0
    private var jours = 0
    private var decalage = -1
    private var fillScript = ""
    private var loadFailed = false
    private var webShown = false
    private var celebrated = false
    private var sentCurrent = false
    private var pendingSubmit = false
    private var filled = false            // le formulaire de l'ami en cours a été rempli
    private var postSeen = false          // un envoi (POST) vers Snapchat a été repéré
    private var sendingShown = false
    private var pageLoading = false
    private var silentFill = false
    private var verifTries = 0
    private var clearHistoryNext = false
    private var historyId = -1L
    private var sentCount = 0
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val verifRunnable = Runnable { verifierEnvoi() }
    private val envoiRunnable = Runnable { debutEnvoi(true) }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form)
        Typo.apply(window.decorView)

        amis = intent.getStringArrayListExtra(EXTRA_FRIENDS) ?: ArrayList()
        if (amis.isEmpty()) {
            finish()
            return
        }
        jours = intent.getIntExtra(EXTRA_DAYS, 0)
        decalage = intent.getIntExtra(EXTRA_LOST, -1)
        // Écran recréé (mode sombre, langue du téléphone…) : on garde sa place dans la série.
        if (savedInstanceState != null) {
            index = savedInstanceState.getInt("index", 0).coerceIn(0, amis.size - 1)
            sentCount = savedInstanceState.getInt("sentCount", 0)
            historyId = savedInstanceState.getLong("historyId", -1L)
            sentCurrent = savedInstanceState.getBoolean("sentCurrent", false)
            filled = savedInstanceState.getBoolean("filled", false)
            if (sentCurrent) setResult(RESULT_OK)
        }
        fillScript = assets.open("fill.js").bufferedReader(Charsets.UTF_8).use { it.readText() }

        web = findViewById(R.id.web)
        statusTitle = findViewById(R.id.status_title)
        statusDetail = findViewById(R.id.status_detail)
        statusTexts = findViewById(R.id.status_texts)
        badge = findViewById(R.id.badge)
        card = findViewById(R.id.status_card)
        progress = findViewById(R.id.progress)
        navTitle = findViewById(R.id.nav_title)
        nextButton = findViewById(R.id.next_button)

        val back = findViewById<View>(R.id.back_button)
        val refill = findViewById<View>(R.id.refill_button)
        val browser = findViewById<View>(R.id.browser_button)
        back.setOnClickListener { finish() }
        refill.setOnClickListener { remplirANouveau() }
        browser.setOnClickListener { kitNavigateur() }
        nextButton.setOnClickListener { suivant() }
        Motion.highlight(back)
        Motion.highlight(refill)
        Motion.highlight(browser)
        Motion.highlight(nextButton)

        majNav()
        majActions()

        // Entrée : la carte d'état se dépose en douceur, la page apparaît en fondu.
        if (Motion.animationsOn()) {
            Motion.reveal(listOf(card), 180, 0, 10f, 0f)
            web.alpha = 0f
            web.postDelayed({ montrerPage() }, 6000)
        }

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.allowFileAccess = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        // Se présente comme Chrome mobile (sans la mention « wv » de WebView).
        s.userAgentString = s.userAgentString.replace("; wv", "")

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, true)

        web.addJavascriptInterface(Pont(), "SnapFill")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return false
                if (scheme == "http" || scheme == "https") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                } catch (e: Exception) {
                    // aucune app pour ce lien : on ignore
                }
                return true
            }

            // On ne modifie aucune requête : on repère seulement l'envoi du formulaire.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                try {
                    if ("POST".equals(request.method, ignoreCase = true)) {
                        val host = request.url.host ?: ""
                        val path = request.url.path ?: ""
                        if (host.endsWith("snapchat.com") && ENVOI.matcher(path).find()) {
                            handler.post(envoiRunnable)
                        }
                    }
                } catch (e: Exception) {
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                loadFailed = false
                pageLoading = true
                // Secours : la page change alors que le formulaire était rempli
                // (envoi classique sans requête repérée). On vérifie, sans rien supposer.
                if (filled && !sentCurrent && url != null && !url.contains("/requests/new")) debutEnvoi(false)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                pageLoading = false
                montrerPage()
                if (clearHistoryNext) {
                    clearHistoryNext = false
                    web.clearHistory()
                }
                if (pendingSubmit) {
                    handler.removeCallbacks(verifRunnable)
                    handler.postDelayed(verifRunnable, 3000)
                    return
                }
                if (!loadFailed && !sentCurrent && estUnePageDeDemande(url)) injecter()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    loadFailed = true
                    montrerPage()
                    afficherEtat(getString(R.string.st_error_title), getString(R.string.st_error_detail), StatusBadge.ERROR)
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.setProgress(newProgress)
                if (newProgress >= 70) montrerPage()
            }

            // Permet d'ajouter une capture d'écran dans « Pièces jointes ».
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    startActivityForResult(fileChooserParams.createIntent(), REQ_FILE)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        if (sentCurrent) afficherEnvoye()
        else afficherEtat(getString(R.string.st_loading_title), getString(R.string.st_loading_detail), StatusBadge.LOADING)
        web.loadUrl(FORM_URL)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("index", index)
        outState.putInt("sentCount", sentCount)
        outState.putLong("historyId", historyId)
        outState.putBoolean("sentCurrent", sentCurrent)
        outState.putBoolean("filled", filled)
    }

    private fun ami(): String = amis[index]

    private fun estUnePageDeDemande(url: String?): Boolean =
        url != null && url.contains("help.snapchat.com") && url.contains("/requests")

    private fun montrerPage() {
        if (webShown || isDestroyed) return
        webShown = true
        web.animate().alpha(1f).setDuration(500).setInterpolator(Motion.IOS).start()
    }

    private fun majNav() {
        navTitle.text = if (amis.size > 1) getString(R.string.nav_count, index + 1, amis.size)
        else getString(R.string.form_title)
    }

    private fun majActions() {
        val reste = index < amis.size - 1
        when {
            reste -> {
                nextButton.text = getString(R.string.next_friend, amis[index + 1])
                nextButton.visibility = View.VISIBLE
            }
            sentCurrent && amis.size > 1 -> {
                nextButton.text = getString(R.string.finish)
                nextButton.visibility = View.VISIBLE
            }
            else -> nextButton.visibility = View.GONE
        }
    }

    /** Envoie le script de remplissage avec les valeurs du profil et de l'ami en cours. */
    private fun injecter(silencieux: Boolean = false) {
        val p = Prefs.lire(this)
        val cfg = JSONObject()
        cfg.put("email", p.email)
        cfg.put("username", p.pseudo)
        cfg.put("phone", p.telephone)
        cfg.put("description", messageComplet(p))
        cfg.put("friend", ami())
        silentFill = silencieux
        if (!silencieux) {
            celebrated = false
            afficherEtat(getString(R.string.st_filling_title), getString(R.string.st_filling_detail), StatusBadge.FILLING)
        }
        web.evaluateJavascript("window.__snapFillCfg = $cfg;\n$fillScript", null)
    }

    private fun messageComplet(p: Profil): String =
        p.description + Pseudos.details(p.langue, ami(), jours, decalage, System.currentTimeMillis())

    private fun remplirANouveau() {
        handler.removeCallbacks(verifRunnable)
        pendingSubmit = false
        postSeen = false
        sendingShown = false
        val nouvelle = sentCurrent
        if (nouvelle) {
            // Déjà envoyée : on prépare une nouvelle demande pour le même ami.
            sentCurrent = false
            filled = false
            celebrated = false
            historyId = -1L
            majActions()
        }
        val url = web.url ?: ""
        if (nouvelle || loadFailed || !url.contains("/requests/new")) {
            afficherEtat(getString(R.string.st_loading_title), getString(R.string.st_loading_detail), StatusBadge.LOADING)
            web.loadUrl(FORM_URL)
        } else {
            injecter()
        }
    }

    // ---------- envoi ----------

    /**
     * Début possible d'un envoi. [post] : une requête d'envoi vers Snapchat a été vue
     * (signal fort) ; sinon la page a seulement changé (signal faible : il faudra
     * un message de confirmation pour conclure).
     */
    private fun debutEnvoi(post: Boolean) {
        if (sentCurrent || isDestroyed) return
        if (post) {
            postSeen = true
            if (!sendingShown) {
                sendingShown = true
                afficherEtat(getString(R.string.st_sending_title), getString(R.string.st_sending_detail), StatusBadge.LOADING)
            }
        }
        if (!pendingSubmit) {
            pendingSubmit = true
            verifTries = 0
            handler.removeCallbacks(verifRunnable)
            handler.postDelayed(verifRunnable, 5000) // si la page ne se recharge pas (envoi en arrière-plan)
        }
    }

    private fun verifierEnvoi() {
        if (!pendingSubmit || isDestroyed) return
        // Page encore en chargement : on attend un peu (jusqu'à ~20 s).
        if (pageLoading && verifTries < 12) {
            verifTries++
            handler.postDelayed(verifRunnable, 1500)
            return
        }
        web.evaluateJavascript(VERIF_JS, object : ValueCallback<String> {
            override fun onReceiveValue(value: String?) {
                if (!pendingSubmit || isDestroyed) return
                var form = true
                var ok = false
                var ready = true
                try {
                    val o = JSONObject(value ?: "{}")
                    form = o.optBoolean("form", true)
                    ok = o.optBoolean("ok", false)
                    ready = o.optBoolean("ready", true)
                } catch (e: Exception) {
                }
                if (!ready && verifTries < 12) {
                    verifTries++
                    handler.postDelayed(verifRunnable, 1500)
                    return
                }
                val decision = EnvoiLogique.decider(form, ok, postSeen, web.url ?: "")
                if (decision == EnvoiLogique.ENVOYEE) {
                    marquerEnvoye()
                    return
                }
                pendingSubmit = false
                if (decision == EnvoiLogique.REFUSEE) {
                    postSeen = false
                    sendingShown = false
                    afficherEtat(getString(R.string.st_notsent_title), getString(R.string.st_notsent_detail), StatusBadge.WARN)
                    Motion.hapticNo(card)
                }
                // Notre formulaire est affiché : on complète discrètement ce que la page aurait vidé.
                val u = web.url ?: ""
                val notreFormulaire = u.contains("ticket_form_id=149423") ||
                    (u.contains("/requests") && !u.contains("/requests/new"))
                if (form && notreFormulaire) injecter(true)
                // Sinon : simple navigation ailleurs, on ne change rien.
            }
        })
    }

    private fun marquerEnvoye() {
        pendingSubmit = false
        postSeen = false
        sendingShown = false
        silentFill = false
        sentCurrent = true
        sentCount++
        if (historyId == -1L) historyId = Store.ajouter(this, ami())
        Store.marquerEnvoyee(this, historyId)
        setResult(RESULT_OK)
        Motion.hapticOk(card)
        Motion.pop(card, 0.025f)
        afficherEnvoye()
    }

    /** Affiche l'état « envoyée » de l'ami en cours (et la suite de la série). */
    private fun afficherEnvoye() {
        val dernier = index >= amis.size - 1
        when {
            !dernier -> afficherEtat(
                getString(R.string.st_sent_title),
                getString(R.string.st_sent_next, ami(), amis[index + 1]), StatusBadge.DONE
            )
            amis.size > 1 -> afficherEtat(
                getString(R.string.st_all_title),
                resources.getQuantityString(R.plurals.st_all_detail, sentCount, sentCount), StatusBadge.DONE
            )
            else -> afficherEtat(getString(R.string.st_sent_title), getString(R.string.st_sent_detail, ami()), StatusBadge.DONE)
        }
        majActions()
    }

    /** Passe à l'ami suivant (ou termine la série). */
    private fun suivant() {
        if (index >= amis.size - 1) {
            finish()
            return
        }
        index++
        sentCurrent = false
        pendingSubmit = false
        filled = false
        postSeen = false
        sendingShown = false
        silentFill = false
        historyId = -1L
        celebrated = false
        handler.removeCallbacks(verifRunnable)
        majNav()
        majActions()
        Motion.hapticOk(nextButton)
        afficherEtat(getString(R.string.st_loading_title), getString(R.string.st_loading_detail), StatusBadge.LOADING)
        clearHistoryNext = true
        web.loadUrl(FORM_URL)
    }

    // ---------- état affiché ----------

    /** Change la pastille et le message, avec un fondu enchaîné discret. */
    private fun afficherEtat(titre: String, detail: String, etat: Int) {
        badge.show(etat)
        if (statusTitle.text.toString() == titre && statusDetail.text.toString() == detail) return
        if (!Motion.animationsOn()) {
            statusTitle.text = titre
            statusDetail.text = detail
            return
        }
        val d = Motion.dp(statusTexts, 1f)
        statusTexts.animate().cancel()
        statusTexts.animate().alpha(0f).translationY(-4 * d).setStartDelay(0).setDuration(140)
            .setInterpolator(Motion.IOS)
            .withEndAction {
                statusTitle.text = titre
                statusDetail.text = detail
                statusTexts.translationY = 6 * d
                statusTexts.animate().alpha(1f).translationY(0f).setDuration(420)
                    .setInterpolator(Motion.IOS).start()
            }.start()
    }

    /** Résultat envoyé par le script (thread WebView → thread UI). */
    inner class Pont {
        @JavascriptInterface
        fun onResult(json: String) {
            runOnUiThread { afficherResultat(json) }
        }
    }

    private fun afficherResultat(json: String) {
        if (sentCurrent || pendingSubmit || isDestroyed) return
        val res = try { JSONObject(json) } catch (e: Exception) { return }
        val discret = silentFill
        silentFill = false
        val champs = res.optJSONObject("fields") ?: JSONObject()
        val noms = linkedMapOf(
            "email" to R.string.f_email,
            "username" to R.string.f_username,
            "phone" to R.string.f_phone,
            "type" to R.string.f_type,
            "friend" to R.string.f_friend,
            "description" to R.string.f_description
        )
        val manquants = ArrayList<String>()
        if (!res.optBoolean("done")) {
            for ((cle, nom) in noms) {
                val etat = champs.optString(cle, "absent")
                if (etat != "ok" && etat != "vide") manquants.add(getString(nom))
            }
        }
        if (manquants.isEmpty()) {
            filled = true
            if (historyId == -1L) historyId = Store.ajouter(this, ami())
            if (discret) return // on garde le message « Pas encore envoyé »
            val detail = if (amis.size > 1) getString(R.string.st_done_detail_multi, ami(), index + 1, amis.size)
            else getString(R.string.st_done_detail, ami())
            afficherEtat(getString(R.string.st_done_title), detail, StatusBadge.DONE)
            if (!celebrated) {
                celebrated = true
                Motion.hapticOk(card)
                Motion.pop(card, 0.025f)
            }
        } else {
            afficherEtat(
                getString(R.string.st_partial_title),
                getString(R.string.st_partial_detail, manquants.joinToString(", ")),
                StatusBadge.WARN
            )
            Motion.hapticNo(card)
        }
    }

    // ---------- secours navigateur ----------

    /** Kit de secours : ouvre le formulaire dans le navigateur et copie chaque info d'un geste. */
    private fun kitNavigateur() {
        val p = Prefs.lire(this)
        val a = ami()
        Sheet(this)
            .title(getString(R.string.kit_title))
            .message(getString(R.string.kit_message))
            .add(getString(R.string.kit_open)) {
                copier(a, false)
                Toast.makeText(this, getString(R.string.toast_copied, a), Toast.LENGTH_SHORT).show()
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FORM_URL)))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
                }
            }
            .add(getString(R.string.kit_copy_friend, a)) { copier(a, true) }
            .add(getString(R.string.kit_copy_user)) { copier(p.pseudo, true) }
            .add(getString(R.string.kit_copy_email)) { copier(p.email, true) }
            .add(getString(R.string.kit_copy_phone)) { copier(p.telephone, true) }
            .add(getString(R.string.kit_copy_desc)) { copier(messageComplet(p), true) }
            .show()
    }

    private fun copier(texte: String, toast: Boolean) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Flamme Snap", texte))
        Motion.hapticOk(card)
        // Android 13+ affiche déjà sa propre confirmation de copie.
        if (toast && Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    @Deprecated("API Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @Deprecated("API Activity")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        super.onDestroy()
    }
}
