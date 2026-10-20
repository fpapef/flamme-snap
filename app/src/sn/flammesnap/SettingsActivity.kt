package sn.flammesnap

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText

class SettingsActivity : Activity() {

    private lateinit var username: EditText
    private lateinit var email: EditText
    private lateinit var phone: EditText
    private lateinit var description: EditText
    private lateinit var saveButton: Button
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        username = findViewById(R.id.in_username)
        email = findViewById(R.id.in_email)
        phone = findViewById(R.id.in_phone)
        description = findViewById(R.id.in_description)
        saveButton = findViewById(R.id.save_button)

        if (savedInstanceState == null) {
            val p = Prefs.lire(this)
            username.setText(p.pseudo)
            email.setText(p.email)
            phone.setText(p.telephone)
            description.setText(p.description)

            // Les champs arrivent en cascade.
            val list = findViewById<ViewGroup>(R.id.settings_list)
            val children = ArrayList<View>()
            for (i in 0 until list.childCount) children.add(list.getChildAt(i))
            Motion.staggerIn(children, 120, 40, 18f)
        }

        saveButton.setOnClickListener { enregistrer() }
        Motion.pressable(saveButton)
    }

    private fun enregistrer() {
        if (saving) return
        saving = true
        Prefs.enregistrer(
            this,
            Profil(
                username.text.toString().trim().trimStart('@').trim(),
                email.text.toString().trim(),
                phone.text.toString().trim(),
                description.text.toString().trim()
            )
        )
        // Le bouton devient vert avec une coche, puis on revient à l'accueil.
        Motion.hapticOk(saveButton)
        saveButton.setText(R.string.saved)
        val from = Motion.col("#111111")
        val to = Motion.col("#16A34A")
        val a = ValueAnimator.ofArgb(from, to)
        a.duration = 320
        a.addUpdateListener { va ->
            saveButton.backgroundTintList = ColorStateList.valueOf(va.animatedValue as Int)
        }
        a.start()
        Motion.pop(saveButton, 0.06f)
        saveButton.postDelayed({ finish() }, if (Motion.animationsOn()) 650L else 0L)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
