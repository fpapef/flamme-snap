package sn.flammesnap

import android.content.Context

/** Infos personnelles envoyées dans le formulaire (modifiables dans Réglages). */
class Profil(
    val pseudo: String,
    val email: String,
    val telephone: String,
    val description: String
)

object Prefs {
    private const val FICHIER = "reglages"
    private const val K_PSEUDO = "pseudo"
    private const val K_EMAIL = "email"
    private const val K_TEL = "telephone"
    private const val K_DESC = "description"

    const val DEF_PSEUDO = ""
    const val DEF_EMAIL = ""
    const val DEF_TEL = ""
    const val DEF_DESC = "Bonjour, ma Snapstreak avec cet ami a disparu. " +
        "Pourriez-vous vérifier s'il y a eu un problème de votre côté et la restaurer si possible ? " +
        "Merci beaucoup !"

    fun lire(ctx: Context): Profil {
        val p = ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
        return Profil(
            p.getString(K_PSEUDO, null) ?: DEF_PSEUDO,
            p.getString(K_EMAIL, null) ?: DEF_EMAIL,
            p.getString(K_TEL, null) ?: DEF_TEL,
            p.getString(K_DESC, null) ?: DEF_DESC
        )
    }

    fun enregistrer(ctx: Context, profil: Profil) {
        ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit()
            .putString(K_PSEUDO, profil.pseudo)
            .putString(K_EMAIL, profil.email)
            .putString(K_TEL, profil.telephone)
            .putString(K_DESC, profil.description)
            .apply()
    }
}
