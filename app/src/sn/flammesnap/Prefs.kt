package sn.flammesnap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Un profil : la personne au nom de qui la demande est envoyée. */
class Profil(
    val id: Long,
    var nom: String,
    var pseudo: String,
    var email: String,
    var telephone: String,
    var langue: String,
    var descFr: String,
    var descEn: String,
    val couleur: Int
) {
    /** Message dans la langue choisie (texte par défaut si vide). */
    val description: String
        get() = descriptionPour(langue)

    fun descriptionPour(l: String): String =
        if (l == "en") (if (descEn.isBlank()) Prefs.DEF_DESC_EN else descEn)
        else (if (descFr.isBlank()) Prefs.DEF_DESC else descFr)

    /** Pseudo et e-mail sont indispensables pour remplir le formulaire. */
    val complet: Boolean
        get() = pseudo.isNotBlank() && email.isNotBlank()

    fun initiale(): String {
        val t = nom.trim()
        return if (t.isEmpty()) "?" else t.substring(0, 1).uppercase()
    }
}

/** Profils enregistrés sur le téléphone, et profil actif. */
object Prefs {
    private const val FICHIER = "profils"
    private const val K_DATA = "data"

    // Anciennes clés (versions 1.0 à 1.4), reprises dans le profil « Moi ».
    private const val ANCIEN_FICHIER = "reglages"

    const val DEF_PSEUDO = ""
    const val DEF_EMAIL = ""
    const val DEF_TEL = ""
    const val DEF_DESC = "Bonjour, ma Snapstreak avec cet ami a disparu. " +
        "Pourriez-vous vérifier s'il y a eu un problème de votre côté et la restaurer si possible ? " +
        "Merci beaucoup !"
    const val DEF_DESC_EN = "Hello, my Snapstreak with this friend has disappeared. " +
        "Could you please check whether there was an issue on your side and restore it if possible? " +
        "Many thanks!"

    /** Couleurs des pastilles de profil (orange, bleu, vert, violet, rose, turquoise, indigo). */
    val COULEURS = intArrayOf(
        0xFFF2550F.toInt(), 0xFF0A84FF.toInt(), 0xFF30B158.toInt(), 0xFFAF52DE.toInt(),
        0xFFFF2D55.toInt(), 0xFF32ADE6.toInt(), 0xFF5856D6.toInt()
    )

    /** [suivant] : prochain numéro de profil (jamais réutilisé, même après une suppression). */
    private class Donnees(var actif: Long, val liste: ArrayList<Profil>, var suivant: Long)

    private fun p(ctx: Context) = ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    // ---------- lecture / écriture ----------

    private fun charger(ctx: Context): Donnees {
        val brut = p(ctx).getString(K_DATA, null)
        if (brut != null) {
            try {
                val o = JSONObject(brut)
                val a = o.getJSONArray("liste")
                val liste = ArrayList<Profil>()
                for (i in 0 until a.length()) liste.add(depuisJson(a.getJSONObject(i)))
                if (liste.isNotEmpty()) {
                    var actif = o.optLong("actif", liste[0].id)
                    if (trouver(liste, actif) == null) actif = liste[0].id
                    var max = 0L
                    for (pr in liste) if (pr.id > max) max = pr.id
                    return Donnees(actif, liste, Math.max(o.optLong("suivant", 0L), max + 1))
                }
            } catch (e: Exception) {
            }
        }
        return migrer(ctx)
    }

    /** Première ouverture de la 1.5 : les réglages existants deviennent le profil « Moi ». */
    private fun migrer(ctx: Context): Donnees {
        val a = ctx.getSharedPreferences(ANCIEN_FICHIER, Context.MODE_PRIVATE)
        val moi = Profil(
            1L,
            ctx.getString(R.string.profile_me),
            a.getString("pseudo", null) ?: DEF_PSEUDO,
            a.getString("email", null) ?: DEF_EMAIL,
            a.getString("telephone", null) ?: DEF_TEL,
            if (a.getString("langue", "fr") == "en") "en" else "fr",
            a.getString("description", null) ?: DEF_DESC,
            a.getString("description_en", null) ?: DEF_DESC_EN,
            0
        )
        val d = Donnees(moi.id, arrayListOf(moi), moi.id + 1)
        sauver(ctx, d)
        Store.migrer(ctx, moi.id)
        return d
    }

    private fun sauver(ctx: Context, d: Donnees) {
        val a = JSONArray()
        for (pr in d.liste) a.put(versJson(pr))
        val o = JSONObject()
        o.put("actif", d.actif)
        o.put("suivant", d.suivant)
        o.put("liste", a)
        p(ctx).edit().putString(K_DATA, o.toString()).apply()
    }

    private fun versJson(pr: Profil): JSONObject {
        val o = JSONObject()
        o.put("id", pr.id)
        o.put("nom", pr.nom)
        o.put("pseudo", pr.pseudo)
        o.put("email", pr.email)
        o.put("tel", pr.telephone)
        o.put("langue", pr.langue)
        o.put("descFr", pr.descFr)
        o.put("descEn", pr.descEn)
        o.put("couleur", pr.couleur)
        return o
    }

    private fun depuisJson(o: JSONObject): Profil = Profil(
        o.getLong("id"),
        o.optString("nom", ""),
        o.optString("pseudo", ""),
        o.optString("email", ""),
        o.optString("tel", ""),
        if (o.optString("langue", "fr") == "en") "en" else "fr",
        o.optString("descFr", DEF_DESC),
        o.optString("descEn", DEF_DESC_EN),
        o.optInt("couleur", 0)
    )

    private fun trouver(liste: List<Profil>, id: Long): Profil? {
        for (pr in liste) if (pr.id == id) return pr
        return null
    }

    // ---------- API ----------

    fun profils(ctx: Context): ArrayList<Profil> = charger(ctx).liste

    fun actifId(ctx: Context): Long = charger(ctx).actif

    fun actif(ctx: Context): Profil {
        val d = charger(ctx)
        return trouver(d.liste, d.actif) ?: d.liste[0]
    }

    /** Profil actif (nom gardé pour les écrans existants). */
    fun lire(ctx: Context): Profil = actif(ctx)

    fun get(ctx: Context, id: Long): Profil? = trouver(charger(ctx).liste, id)

    fun langue(ctx: Context): String = actif(ctx).langue

    fun setActif(ctx: Context, id: Long) {
        val d = charger(ctx)
        if (trouver(d.liste, id) == null) return
        d.actif = id
        sauver(ctx, d)
    }

    /** Enregistre (ou ajoute) un profil. */
    fun enregistrer(ctx: Context, pr: Profil) {
        val d = charger(ctx)
        var remplace = false
        for (i in d.liste.indices) if (d.liste[i].id == pr.id) {
            d.liste[i] = pr
            remplace = true
        }
        if (!remplace) d.liste.add(pr)
        sauver(ctx, d)
    }

    /** Crée un profil vide (« Profil 2 »…), le rend actif et le renvoie. */
    fun creer(ctx: Context): Profil {
        val d = charger(ctx)
        val id = d.suivant
        val n = d.liste.size + 1
        val pr = Profil(
            id, ctx.getString(R.string.profile_new_name, n), "", "", "", "fr",
            DEF_DESC, DEF_DESC_EN, (id - 1).toInt() % COULEURS.size
        )
        d.liste.add(pr)
        d.actif = pr.id
        d.suivant = id + 1
        sauver(ctx, d)
        return pr
    }

    /** Supprime un profil et son historique. Le dernier profil ne peut pas être supprimé. */
    fun supprimer(ctx: Context, id: Long): Boolean {
        val d = charger(ctx)
        if (d.liste.size <= 1) return false
        val out = ArrayList<Profil>()
        for (pr in d.liste) if (pr.id != id) out.add(pr)
        if (out.size == d.liste.size) return false
        val actif = if (d.actif == id) out[0].id else d.actif
        sauver(ctx, Donnees(actif, out, d.suivant))
        Store.supprimerProfil(ctx, id)
        return true
    }
}
