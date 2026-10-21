package sn.flammesnap

import java.util.regex.Pattern

/** Décide, après un envoi possible, ce qui s'est passé. Fonction pure (testée à part). */
object EnvoiLogique {
    const val ENVOYEE = 0   // la demande est partie
    const val REFUSEE = 1   // Snapchat a renvoyé le formulaire (souvent : reCAPTCHA)
    const val RETOUR = 2    // simple retour sur le formulaire
    const val AILLEURS = 3  // navigation ailleurs, rien à conclure

    private val DEMANDE_CREEE = Pattern.compile("/requests/\\d+")

    /**
     * [form] : le formulaire est encore affiché ; [ok] : un message de confirmation est visible ;
     * [postSeen] : une requête d'envoi a été repérée ; [url] : adresse de la page actuelle.
     */
    fun decider(form: Boolean, ok: Boolean, postSeen: Boolean, url: String): Int {
        if (ok) return ENVOYEE
        if (!form && (postSeen || DEMANDE_CREEE.matcher(url).find())) return ENVOYEE
        val renvoye = form && url.contains("/requests") && !url.contains("/requests/new")
        if (postSeen || renvoye) return REFUSEE
        return if (form) RETOUR else AILLEURS
    }
}
