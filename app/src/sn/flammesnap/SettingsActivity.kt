package sn.flammesnap

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var username: EditText
    private lateinit var email: EditText
    private lateinit var phone: EditText
    private lateinit var description: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        username = findViewById(R.id.in_username)
        email = findViewById(R.id.in_email)
        phone = findViewById(R.id.in_phone)
        description = findViewById(R.id.in_description)

        if (savedInstanceState == null) {
            val p = Prefs.lire(this)
            username.setText(p.pseudo)
            email.setText(p.email)
            phone.setText(p.telephone)
            description.setText(p.description)
        }

        findViewById<Button>(R.id.save_button).setOnClickListener { enregistrer() }
    }

    private fun enregistrer() {
        Prefs.enregistrer(
            this,
            Profil(
                username.text.toString().trim().trimStart('@').trim(),
                email.text.toString().trim(),
                phone.text.toString().trim(),
                description.text.toString().trim()
            )
        )
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
