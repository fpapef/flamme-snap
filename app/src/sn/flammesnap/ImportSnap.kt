package sn.flammesnap

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Lecture de la liste d'amis dans l'export officiel « Mes données » de Snapchat :
 * le ZIP reçu par e-mail, ou directement le fichier friends.json qu'il contient.
 * Le format n'étant pas documenté publiquement, la lecture est tolérante
 * (emplacement du fichier, majuscules et variantes des noms de champs).
 */
object ImportSnap {

    class Resultat(val amis: List<Ami>, val erreur: Int)

    const val OK = 0
    const val PAS_DE_LISTE = 1      // ZIP sans friends.json (export demandé en HTML seulement ?)
    const val ILLISIBLE = 2         // ni ZIP ni JSON reconnu

    private const val MAX_JSON = 20 * 1024 * 1024

    /** Lit un ZIP (« mydata… .zip ») ou un fichier JSON. Le flux est lu jusqu'à trouver la liste. */
    fun lire(entree: InputStream): Resultat {
        val buf = java.io.BufferedInputStream(entree)
        buf.mark(8)
        val tete = ByteArray(4)
        val n = buf.read(tete)
        buf.reset()
        val estZip = n == 4 && tete[0] == 'P'.code.toByte() && tete[1] == 'K'.code.toByte()
        if (!estZip) {
            val texte = lireTout(buf) ?: return Resultat(emptyList(), ILLISIBLE)
            val amis = depuisJson(texte) ?: return Resultat(emptyList(), ILLISIBLE)
            return Resultat(amis, OK)
        }
        val zip = ZipInputStream(buf)
        var trouve = false
        while (true) {
            val e = zip.nextEntry ?: break
            val nom = e.name.lowercase().replace('\\', '/')
            if (e.isDirectory || !nom.endsWith("friends.json")) continue
            trouve = true
            val texte = lireTout(zip) ?: continue
            val amis = depuisJson(texte)
            if (amis != null) return Resultat(amis, OK)
        }
        return Resultat(emptyList(), if (trouve) ILLISIBLE else PAS_DE_LISTE)
    }

    private fun lireTout(s: InputStream): String? {
        val out = ByteArrayOutputStream()
        val b = ByteArray(16384)
        while (true) {
            val r = s.read(b)
            if (r < 0) break
            out.write(b, 0, r)
            if (out.size() > MAX_JSON) return null
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** Amis actuels uniquement (pas les supprimés, bloqués, demandes en attente…). */
    fun depuisJson(texte: String): List<Ami>? {
        val t = texte.trim().removePrefix("﻿")
        val liste: JSONArray = try {
            if (t.startsWith("[")) JSONArray(t) else choisirListe(JSONObject(t)) ?: return null
        } catch (e: Exception) {
            return null
        }
        val out = ArrayList<Ami>()
        val vus = HashSet<String>()
        for (i in 0 until liste.length()) {
            val o = liste.optJSONObject(i) ?: continue
            var pseudo = champ(o, "username", "user name", "user_name", "name")
            while (pseudo.startsWith("@")) pseudo = pseudo.substring(1)
            pseudo = pseudo.trim()
            if (pseudo.isEmpty() || !vus.add(pseudo.lowercase())) continue
            var nom = champ(o, "display name", "display_name", "displayname", "display")
            if (nom.equals(pseudo, ignoreCase = true)) nom = ""
            out.add(Ami(pseudo, nom))
        }
        return out
    }

    private fun normaliser(k: String) = k.lowercase().replace('_', ' ').trim()

    /** La liste « Friends » ; à défaut, une liste d'amis qui n'est ni supprimée, ni bloquée, ni en attente. */
    private fun choisirListe(o: JSONObject): JSONArray? {
        val exclus = listOf("delet", "remov", "block", "ignor", "request", "pending", "sent", "suggest")
        var secours: JSONArray? = null
        val cles = o.keys()
        while (cles.hasNext()) {
            val k = cles.next()
            val a = o.optJSONArray(k) ?: continue
            val nk = normaliser(k)
            if (nk == "friends" || nk == "friend list" || nk == "friends list") return a
            if (secours == null && nk.contains("friend") && exclus.none { nk.contains(it) }) secours = a
        }
        return secours
    }

    private fun champ(o: JSONObject, vararg noms: String): String {
        val cles = o.keys()
        val trouve = HashMap<String, String>()
        while (cles.hasNext()) {
            val k = cles.next()
            trouve[normaliser(k)] = k
        }
        for (n in noms) {
            val k = trouve[n] ?: continue
            val v = o.opt(k)
            if (v is String && v.isNotBlank()) return v.trim()
        }
        return ""
    }
}
