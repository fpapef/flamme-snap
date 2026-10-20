package sn.flammesnap

import android.content.Context

/** Infos personnelles envoyées dans le formulaire (modifiables dans Réglages). */
class Profil(
    val pseudo: String,
    val email: String,
    val telephone: String,
    val description: String,
    val langue: String
)

object Prefs {
    private const val FICHIER = "reglages"
    private const val K_PSEUDO = "pseudo"
    private const val K_EMAIL = "email"
    private const val K_TEL = "telephone"
    private const val K_DESC = "description"        // message en français
    private const val K_DESC_EN = "description_en"  // message en anglais
    private const val K_LANG = "langue"

    const val DEF_PSEUDO = ""
    const val DEF_EMAIL = ""
    const val DEF_TEL = ""
    const val DEF_DESC = "Bonjour, ma Snapstreak avec cet ami a disparu. " +
        "Pourriez-vous vérifier s'il y a eu un problème de votre côté et la restaurer si possible ? " +
        "Merci beaucoup !"
    const val DEF_DESC_EN = "Hello, my Snapstreak with this friend has disappeared. " +
        "Could you please check whether there was an issue on your side and restore it if possible? " +
        "Many thanks!"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    fun langue(ctx: Context): String = if (p(ctx).getString(K_LANG, "fr") == "en") "en" else "fr"

    fun setLangue(ctx: Context, l: String) {
        p(ctx).edit().putString(K_LANG, l).apply()
    }

    fun description(ctx: Context, l: String): String {
        val s = p(ctx).getString(if (l == "en") K_DESC_EN else K_DESC, null)
        return if (s == null || s.isBlank()) (if (l == "en") DEF_DESC_EN else DEF_DESC) else s
    }

    fun setDescription(ctx: Context, l: String, texte: String) {
        p(ctx).edit().putString(if (l == "en") K_DESC_EN else K_DESC, texte).apply()
    }

    fun lire(ctx: Context): Profil {
        val p = p(ctx)
        val l = langue(ctx)
        return Profil(
            p.getString(K_PSEUDO, null) ?: DEF_PSEUDO,
            p.getString(K_EMAIL, null) ?: DEF_EMAIL,
            p.getString(K_TEL, null) ?: DEF_TEL,
            description(ctx, l),
            l
        )
    }

    fun enregistrerCompte(ctx: Context, pseudo: String, email: String, telephone: String) {
        p(ctx).edit()
            .putString(K_PSEUDO, pseudo)
            .putString(K_EMAIL, email)
            .putString(K_TEL, telephone)
            .apply()
    }
}
