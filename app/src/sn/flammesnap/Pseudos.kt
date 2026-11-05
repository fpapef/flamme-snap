package sn.flammesnap

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
}
