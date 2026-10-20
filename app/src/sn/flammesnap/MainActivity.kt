package sn.flammesnap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var flame: FlameView
    private lateinit var friendInput: EditText
    private lateinit var continueButton: View
    private lateinit var error: View
    private lateinit var summary: TextView
    private var navigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Typo.apply(window.decorView)

        flame = findViewById(R.id.flame)
        friendInput = findViewById(R.id.friend_input)
        continueButton = findViewById(R.id.continue_button)
        error = findViewById(R.id.error)
        summary = findViewById(R.id.profile_summary)
        val settings = findViewById<View>(R.id.settings_button)

        continueButton.setOnClickListener { continuer() }
        settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        Motion.pressScale(continueButton)
        Motion.highlight(settings)

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
                if (error.alpha > 0f) error.animate().alpha(0f).setDuration(200).start()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (savedInstanceState == null) {
            flame.ignite()
            Motion.reveal(listOf<View>(findViewById(R.id.title), findViewById(R.id.subtitle)), 260, 110, 14f, 28f)
            Motion.reveal(listOf<View>(friendInput, continueButton, summary), 560, 70, 18f, 0f)
            Motion.reveal(listOf(settings), 700, 0, 0f, 0f)
        }
    }

    override fun onResume() {
        super.onResume()
        navigating = false
        summary.text = getString(R.string.profile_summary, Prefs.lire(this).pseudo)
    }

    private fun continuer() {
        if (navigating) return
        val pseudo = friendInput.text.toString().trim().trimStart('@').trim()
        if (pseudo.isEmpty()) {
            friendInput.requestFocus()
            Motion.shake(friendInput)
            Motion.hapticNo(friendInput)
            error.animate().alpha(1f).setDuration(220).start()
            flame.dim()
            return
        }
        navigating = true
        Motion.hapticOk(continueButton)
        flame.boost()
        // La flamme s'embrase un court instant, puis l'écran suivant glisse.
        continueButton.postDelayed({
            val intent = Intent(this, FormActivity::class.java)
            intent.putExtra(FormActivity.EXTRA_FRIEND, pseudo)
            startActivity(intent)
        }, if (Motion.animationsOn()) 240L else 0L)
    }
}
