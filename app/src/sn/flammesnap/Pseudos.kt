package sn.flammesnap

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/** Lecture des pseudos : saisie libre, liens de profil Snapchat, textes partagés. */
object Pseudos {

    const val MAX = 10

    private val SEP = Pattern.compile("[\\s,;]+")
    private val LISTE = Pattern.compile("[,;\\n\\r]+")
    private val LIEN = Pattern.compile("snapchat\\.com/add/([A-Za-z0-9._-]{2,30})", Pattern.CASE_INSENSITIVE)
    private val ETIQUETTE = Pattern.compile(
        "(?:nom d.utilisateur|username|pseudo)\\s*:\\s*@?([A-Za-z0-9._-]{2,30})",
        Pattern.CASE_INSENSITIVE
    )
    // Règles Snapchat : 3 à 15 caractères, commence par une lettre ; lettres, chiffres, - _ .
    private val VALIDE = Pattern.compile("^[A-Za-z][A-Za-z0-9._-]{2,14}$")

    /** « ami1, ami2 ami3 » → [ami1, ami2, ami3] (sans doublons, 10 max). */
    fun depuisSaisie(texte: String): ArrayList<String> {
        val out = ArrayList<String>()
        for (brut in SEP.split(texte.trim())) {
            var s = brut.trim()
            while (s.startsWith("@")) s = s.substring(1)
            val m = LIEN.matcher(s)
            if (m.find()) s = m.group(1) ?: s
            if (s.isEmpty()) continue
            ajouterSansDoublon(out, s)
            if (out.size >= MAX) break
        }
        return out
    }

    /** Texte partagé depuis Snapchat (lien de profil) ou presse-papiers. */
    fun depuisTexte(texte: String): ArrayList<String> {
        val out = ArrayList<String>()
        val m1 = LIEN.matcher(texte)
        while (m1.find()) ajouterSansDoublon(out, m1.group(1) ?: "")
        val m2 = ETIQUETTE.matcher(texte)
        while (m2.find()) ajouterSansDoublon(out, m2.group(1) ?: "")
        if (out.isEmpty()) {
            // Sinon : un pseudo seul, ou une liste séparée par virgules / retours à la ligne.
            // Une phrase (des mots séparés par des espaces) n'est pas prise pour des pseudos.
            val t = texte.trim()
            if (t.length in 1..300) {
                val parts = LISTE.split(t)
                val tmp = ArrayList<String>()
                var ok = true
                for (brut in parts) {
                    var s = brut.trim()
                    while (s.startsWith("@")) s = s.substring(1)
                    if (s.isEmpty()) continue
                    if (!estValide(s)) { ok = false; break }
                    ajouterSansDoublon(tmp, s)
                }
                if (ok) out.addAll(tmp)
            }
        }
        while (out.size > MAX) out.removeAt(out.size - 1)
        return out
    }

    fun estValide(s: String): Boolean = VALIDE.matcher(s).matches()

    private fun ajouterSansDoublon(list: ArrayList<String>, s: String) {
        if (s.isEmpty()) return
        for (x in list) if (x.equals(s, ignoreCase = true)) return
        list.add(s)
    }

    /**
     * Phrase ajoutée à la fin du message : durée de la flamme et date de disparition.
     * jours <= 0 : non précisé ; decalage < 0 : non précisé (0 = aujourd'hui, 1 = hier…).
     */
    fun details(langue: String, ami: String, jours: Int, decalage: Int, maintenant: Long): String {
        if (jours <= 0 && decalage < 0) return ""
        val date = if (decalage >= 0) {
            SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(maintenant - decalage * 86_400_000L))
        } else ""
        val sb = StringBuilder()
        if (langue == "en") {
            sb.append("\n\nDetails: streak with ").append(ami)
            if (jours > 0) sb.append(" (").append(jours).append(if (jours == 1) " day)" else " days)")
            if (date.isNotEmpty()) sb.append(", lost on ").append(date)
            sb.append(".")
        } else {
            sb.append("\n\nDétails : flamme avec ").append(ami)
            if (jours > 0) sb.append(" (").append(jours).append(if (jours == 1) " jour)" else " jours)")
            if (date.isNotEmpty()) sb.append(", disparue le ").append(date)
            sb.append(".")
        }
        return sb.toString()
    }
}
