package sn.flammesnap

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
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
    private lateinit var status: TextView
    private lateinit var badge: StatusBadge
    private lateinit var card: View
    private lateinit var progress: GlowProgress
    private lateinit var confetti: ConfettiView
    private val cardBg = GradientDrawable()
    private var cardColor = 0
    private var cardAnim: ValueAnimator? = null

    private var friend = ""
    private var fillScript = ""
    private var loadFailed = false
    private var celebrated = false
    private var webShown = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val colLoading = Motion.col("#FFFBD1")
    private val colFilling = Motion.col("#FFF3C4")
    private val colDone = Motion.col("#E3F6E8")
    private val colWarn = Motion.col("#FFEBDA")
    private val colError = Motion.col("#FDE4E4")

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form)
        title = getString(R.string.form_title)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        friend = intent.getStringExtra(EXTRA_FRIEND) ?: ""
        fillScript = assets.open("fill.js").bufferedReader(Charsets.UTF_8).use { it.readText() }

        web = findViewById(R.id.web)
        status = findViewById(R.id.status)
        badge = findViewById(R.id.badge)
        card = findViewById(R.id.status_card)
        progress = findViewById(R.id.progress)
        confetti = findViewById(R.id.confetti)

        cardBg.cornerRadius = Motion.dp(card, 18f)
        cardColor = colLoading
        cardBg.setColor(cardColor)
        card.background = cardBg

        val refill = findViewById<Button>(R.id.refill_button)
        val browser = findViewById<Button>(R.id.browser_button)
        refill.setOnClickListener { remplirANouveau() }
        browser.setOnClickListener { ouvrirDansNavigateur() }
        Motion.pressable(refill)
        Motion.pressable(browser)

        // Entrée : la carte d'état glisse depuis le haut, la page apparaît en fondu.
        if (Motion.animationsOn()) {
            card.alpha = 0f
            card.translationY = -Motion.dp(card, 18f)
            card.animate().alpha(1f).translationY(0f).setStartDelay(140).setDuration(520)
                .setInterpolator(Motion.EMPHASIZED).start()
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
                    afficherEtat(getString(R.string.status_load_error), StatusBadge.ERROR)
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

        afficherEtat(getString(R.string.status_loading), StatusBadge.LOADING)
        web.loadUrl(FORM_URL)
    }

    private fun montrerPage() {
        if (webShown || isDestroyed) return
        webShown = true
        web.animate().alpha(1f).setDuration(450).setInterpolator(DecelerateInterpolator()).start()
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
        afficherEtat(getString(R.string.status_filling), StatusBadge.FILLING)
        web.evaluateJavascript("window.__snapFillCfg = $cfg;\n$fillScript", null)
    }

    private fun remplirANouveau() {
        if (loadFailed || !estLeFormulaire(web.url)) {
            afficherEtat(getString(R.string.status_loading), StatusBadge.LOADING)
            web.loadUrl(FORM_URL)
        } else {
            injecter()
        }
    }

    /** Change le message, la pastille et la couleur de la carte, en douceur. */
    private fun afficherEtat(texte: CharSequence, etat: Int) {
        badge.show(etat)
        val cible = when (etat) {
            StatusBadge.DONE -> colDone
            StatusBadge.WARN -> colWarn
            StatusBadge.ERROR -> colError
            StatusBadge.FILLING -> colFilling
            else -> colLoading
        }
        if (cible != cardColor) {
            cardAnim?.cancel()
            val a = ValueAnimator.ofArgb(cardColor, cible)
            a.duration = 450
            a.addUpdateListener { va ->
                cardColor = va.animatedValue as Int
                cardBg.setColor(cardColor)
            }
            a.start()
            cardAnim = a
        }
        if (status.text.toString() == texte.toString()) return
        if (!Motion.animationsOn()) {
            status.text = texte
            return
        }
        val d = Motion.dp(status, 1f)
        status.animate().cancel()
        status.animate().alpha(0f).translationY(-6 * d).setStartDelay(0).setDuration(130)
            .withEndAction {
                status.text = texte
                status.translationY = 8 * d
                status.animate().alpha(1f).translationY(0f).setDuration(320)
                    .setInterpolator(Motion.EMPHASIZED).start()
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
            afficherEtat(getString(R.string.status_done, friend), StatusBadge.DONE)
            celebrer()
        } else {
            afficherEtat(getString(R.string.status_partial, manquants.joinToString(", ")), StatusBadge.WARN)
            Motion.shake(card)
            Motion.hapticNo(card)
        }
    }

    /** Petite fête quand tout est rempli. */
    private fun celebrer() {
        if (celebrated) return
        celebrated = true
        Motion.hapticOk(card)
        Motion.pop(card, 0.03f)
        val c = IntArray(2)
        val k = IntArray(2)
        card.getLocationInWindow(c)
        confetti.getLocationInWindow(k)
        val ox = c[0] - k[0] + card.width / 2f
        val oy = c[1] - k[1] + card.height.toFloat()
        confetti.burst(ox, oy, 120)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("API Activity")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        cardAnim?.cancel()
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}
