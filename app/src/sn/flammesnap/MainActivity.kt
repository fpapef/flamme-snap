package sn.flammesnap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var friendInput: EditText
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        friendInput = findViewById(R.id.friend_input)
        summary = findViewById(R.id.profile_summary)

        findViewById<Button>(R.id.continue_button).setOnClickListener { continuer() }
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
    }

    override fun onResume() {
        super.onResume()
        summary.text = getString(R.string.profile_summary, Prefs.lire(this).pseudo)
    }

    private fun continuer() {
        val pseudo = friendInput.text.toString().trim().trimStart('@').trim()
        if (pseudo.isEmpty()) {
            friendInput.error = getString(R.string.err_friend_empty)
            friendInput.requestFocus()
            return
        }
        val intent = Intent(this, FormActivity::class.java)
        intent.putExtra(FormActivity.EXTRA_FRIEND, pseudo)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, R.string.settings)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_SETTINGS) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val MENU_SETTINGS = 1
    }
}
