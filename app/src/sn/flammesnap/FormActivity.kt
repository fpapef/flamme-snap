package sn.flammesnap

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/**
 * Ouvre le formulaire officiel de Snapchat et remplit les champs.
 * L'app ne coche jamais le reCAPTCHA et n'envoie jamais le formulaire :
 * c'est l'utilisateur qui le fait.
 */
class FormActivity : Activity() {

    companion object {
        const val EXTRA_FRIEND = "friend"
        const val FORM_URL =
            "https://help.snapchat.com/hc/fr-fr/requests/new?co=true&ticket_form_id=149423"
        private const val REQ_FILE = 42
    }

    private lateinit var web: WebView
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusTexts: View
    private lateinit var badge: StatusBadge
    private lateinit var card: View
    private lateinit var progress: GlowProgress

    private var friend = ""
    private var fillScript = ""
    private var loadFailed = false
    private var celebrated = false
    private var webShown = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form)
        Typo.apply(window.decorView)

        friend = intent.getStringExtra(EXTRA_FRIEND) ?: ""
        fillScript = assets.open("fill.js").bufferedReader(Charsets.UTF_8).use { it.readText() }

        web = findViewById(R.id.web)
        statusTitle = findViewById(R.id.status_title)
        statusDetail = findViewById(R.id.status_detail)
        statusTexts = findViewById(R.id.status_texts)
        badge = findViewById(R.id.badge)
        card = findViewById(R.id.status_card)
        progress = findViewById(R.id.progress)

        val back = findViewById<View>(R.id.back_button)
        val refill = findViewById<View>(R.id.refill_button)
        val browser = findViewById<View>(R.id.browser_button)
        back.setOnClickListener { finish() }
        refill.setOnClickListener { remplirANouveau() }
        browser.setOnClickListener { ouvrirDansNavigateur() }
        Motion.highlight(back)
        Motion.highlight(refill)
        Motion.highlight(browser)

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

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                loadFailed = false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                montrerPage()
                if (!loadFailed && estLeFormulaire(url)) injecter()
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

        afficherEtat(getString(R.string.st_loading_title), getString(R.string.st_loading_detail), StatusBadge.LOADING)
        web.loadUrl(FORM_URL)
    }

    private fun montrerPage() {
        if (webShown || isDestroyed) return
        webShown = true
        web.animate().alpha(1f).setDuration(500).setInterpolator(Motion.IOS).start()
    }

    private fun estLeFormulaire(url: String?): Boolean =
        url != null && url.contains("help.snapchat.com") && url.contains("/requests/new")

    /** Envoie le script de remplissage avec les valeurs à la page. */
    private fun injecter() {
        val p = Prefs.lire(this)
        val cfg = JSONObject()
        cfg.put("email", p.email)
        cfg.put("username", p.pseudo)
        cfg.put("phone", p.telephone)
        cfg.put("description", p.description)
        cfg.put("friend", friend)
        celebrated = false
        afficherEtat(getString(R.string.st_filling_title), getString(R.string.st_filling_detail), StatusBadge.FILLING)
        web.evaluateJavascript("window.__snapFillCfg = $cfg;\n$fillScript", null)
    }

    private fun remplirANouveau() {
        if (loadFailed || !estLeFormulaire(web.url)) {
            afficherEtat(getString(R.string.st_loading_title), getString(R.string.st_loading_detail), StatusBadge.LOADING)
            web.loadUrl(FORM_URL)
        } else {
            injecter()
        }
    }

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
        val res = try { JSONObject(json) } catch (e: Exception) { return }
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
            afficherEtat(getString(R.string.st_done_title), getString(R.string.st_done_detail, friend), StatusBadge.DONE)
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

    /** Secours si le reCAPTCHA ne s'affiche pas : copie le pseudo et ouvre le navigateur. */
    private fun ouvrirDansNavigateur() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Pseudo de ton ami", friend))
        Toast.makeText(this, getString(R.string.toast_copied, friend), Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FORM_URL)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
        }
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
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}
