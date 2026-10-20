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
    private lateinit var summary: TextView
    private var navigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        flame = findViewById(R.id.flame)
        friendInput = findViewById(R.id.friend_input)
        continueButton = findViewById(R.id.continue_button)
        summary = findViewById(R.id.profile_summary)
        val settings = findViewById<View>(R.id.settings_button)

        continueButton.setOnClickListener { continuer() }
        settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        Motion.pressable(continueButton)
        Motion.pressable(settings)

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

        // La flamme sursaute à chaque lettre tapée.
        friendInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > before) flame.kick()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (savedInstanceState == null) {
            flame.ignite()
            Motion.staggerIn(
                listOf<View>(
                    findViewById(R.id.top_row),
                    findViewById(R.id.title),
                    findViewById(R.id.subtitle),
                    friendInput,
                    continueButton,
                    summary
                ),
                380, 70, 22f
            )
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
            friendInput.error = getString(R.string.err_friend_empty)
            friendInput.requestFocus()
            Motion.shake(friendInput)
            Motion.hapticNo(friendInput)
            flame.dim()
            return
        }
        navigating = true
        Motion.hapticOk(continueButton)
        flame.boost()
        // On laisse la flamme s'embraser un instant avant de changer d'écran.
        continueButton.postDelayed({
            val intent = Intent(this, FormActivity::class.java)
            intent.putExtra(FormActivity.EXTRA_FRIEND, pseudo)
            startActivity(intent)
        }, if (Motion.animationsOn()) 320L else 0L)
    }
}
