package sn.flammesnap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Une demande de restauration (remplie, puis éventuellement envoyée). */
class Demande(
    val id: Long,
    val ami: String,
    val date: Long,
    var envoyee: Boolean,
    var envoyeeLe: Long
)

/** Un ami Snapchat de la liste « Mes amis » : pseudo, et nom affiché s'il est connu. */
class Ami(val pseudo: String, val nom: String)

/** Historique, amis récents et liste d'amis, propres à chaque profil, sur le téléphone uniquement. */
object Store {
    private const val FICHIER = "donnees"
    private const val MAX_HIST = 300
    private const val MAX_RECENTS = 12

    private fun p(ctx: Context) = ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
    private fun kHist(profil: Long) = "historique_$profil"
    private fun kRecents(profil: Long) = "recents_$profil"
    private fun kAmis(profil: Long) = "amis_$profil"

    /** Reprend l'historique et les récents d'avant les profils (versions 1.3–1.4). */
    fun migrer(ctx: Context, profil: Long) {
        val sp = p(ctx)
        val e = sp.edit()
        val h = sp.getString("historique", null)
        val r = sp.getString("recents", null)
        if (h != null && !sp.contains(kHist(profil))) e.putString(kHist(profil), h)
        if (r != null && !sp.contains(kRecents(profil))) e.putString(kRecents(profil), r)
        e.remove("historique").remove("recents").apply()
    }

    fun supprimerProfil(ctx: Context, profil: Long) {
        p(ctx).edit().remove(kHist(profil)).remove(kRecents(profil)).remove(kAmis(profil)).apply()
    }

    // ---------- amis récents ----------

    fun recents(ctx: Context, profil: Long): ArrayList<String> {
        val out = ArrayList<String>()
        try {
            val a = JSONArray(p(ctx).getString(kRecents(profil), "[]"))
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (e: Exception) {
        }
        return out
    }

    private fun saveRecents(ctx: Context, profil: Long, list: List<String>) {
        val a = JSONArray()
        for (i in 0 until minOf(list.size, MAX_RECENTS)) a.put(list[i])
        p(ctx).edit().putString(kRecents(profil), a.toString()).apply()
    }

    fun addRecents(ctx: Context, profil: Long, noms: List<String>) {
        val out = ArrayList<String>()
        for (n in noms) out.add(n)
        for (c in recents(ctx, profil)) {
            var dup = false
            for (n in noms) if (n.equals(c, ignoreCase = true)) dup = true
            if (!dup) out.add(c)
        }
        saveRecents(ctx, profil, out)
    }

    fun removeRecent(ctx: Context, profil: Long, nom: String) {
        val out = ArrayList<String>()
        for (c in recents(ctx, profil)) if (!c.equals(nom, ignoreCase = true)) out.add(c)
        saveRecents(ctx, profil, out)
    }

    // ---------- liste d'amis ----------

    /** Liste d'amis du profil, triée par nom (ou pseudo). */
    fun amis(ctx: Context, profil: Long): ArrayList<Ami> {
        val out = ArrayList<Ami>()
        try {
            val a = JSONArray(p(ctx).getString(kAmis(profil), "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Ami(o.optString("u"), o.optString("n")))
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun saveAmis(ctx: Context, profil: Long, list: List<Ami>) {
        val tri = ArrayList(list)
        tri.sortWith(object : Comparator<Ami> {
            override fun compare(a: Ami, b: Ami): Int = cleTri(a).compareTo(cleTri(b))
        })
        val a = JSONArray()
        for (x in tri) {
            val o = JSONObject()
            o.put("u", x.pseudo)
            if (x.nom.isNotBlank()) o.put("n", x.nom)
            a.put(o)
        }
        p(ctx).edit().putString(kAmis(profil), a.toString()).apply()
    }

    fun cleTri(a: Ami): String = (if (a.nom.isNotBlank()) a.nom else a.pseudo).lowercase()

    /** Ajoute des amis (sans doublon). Un nom connu complète un ami déjà présent. Renvoie le nombre de nouveaux. */
    fun ajouterAmis(ctx: Context, profil: Long, nouveaux: List<Ami>): Int {
        val list = amis(ctx, profil)
        val index = HashMap<String, Int>()
        for (i in list.indices) index[list[i].pseudo.lowercase()] = i
        var ajoutes = 0
        for (n in nouveaux) {
            if (n.pseudo.isBlank()) continue
            val cle = n.pseudo.lowercase()
            val i = index[cle]
            if (i == null) {
                index[cle] = list.size
                list.add(n)
                ajoutes++
            } else if (list[i].nom.isBlank() && n.nom.isNotBlank()) {
                list[i] = Ami(list[i].pseudo, n.nom)
            }
        }
        saveAmis(ctx, profil, list)
        return ajoutes
    }

    fun supprimerAmis(ctx: Context, profil: Long, pseudos: Collection<String>) {
        val cles = HashSet<String>()
        for (x in pseudos) cles.add(x.lowercase())
        val out = ArrayList<Ami>()
        for (a in amis(ctx, profil)) if (!cles.contains(a.pseudo.lowercase())) out.add(a)
        saveAmis(ctx, profil, out)
    }

    fun effacerAmis(ctx: Context, profil: Long) {
        p(ctx).edit().remove(kAmis(profil)).apply()
    }

    // ---------- historique ----------

    fun historique(ctx: Context, profil: Long): ArrayList<Demande> {
        val out = ArrayList<Demande>()
        try {
            val a = JSONArray(p(ctx).getString(kHist(profil), "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(
                    Demande(
                        o.optLong("id"), o.optString("ami"), o.optLong("date"),
                        o.optBoolean("envoyee"), o.optLong("envoyeeLe")
                    )
                )
            }
        } catch (e: Exception) {
        }
        return out // plus récente en premier
    }

    private fun save(ctx: Context, profil: Long, list: List<Demande>) {
        val a = JSONArray()
        for (i in 0 until minOf(list.size, MAX_HIST)) {
            val d = list[i]
            val o = JSONObject()
            o.put("id", d.id)
            o.put("ami", d.ami)
            o.put("date", d.date)
            o.put("envoyee", d.envoyee)
            o.put("envoyeeLe", d.envoyeeLe)
            a.put(o)
        }
        p(ctx).edit().putString(kHist(profil), a.toString()).apply()
    }

    fun ajouter(ctx: Context, profil: Long, ami: String): Long {
        val list = historique(ctx, profil)
        val now = System.currentTimeMillis()
        val d = Demande(now, ami, now, false, 0L)
        list.add(0, d)
        save(ctx, profil, list)
        return d.id
    }

    fun marquerEnvoyee(ctx: Context, profil: Long, id: Long) {
        val list = historique(ctx, profil)
        for (d in list) if (d.id == id && !d.envoyee) {
            d.envoyee = true
            d.envoyeeLe = System.currentTimeMillis()
        }
        save(ctx, profil, list)
    }

    fun supprimer(ctx: Context, profil: Long, id: Long) {
        val out = ArrayList<Demande>()
        for (d in historique(ctx, profil)) if (d.id != id) out.add(d)
        save(ctx, profil, out)
    }

    fun effacer(ctx: Context, profil: Long) {
        p(ctx).edit().remove(kHist(profil)).apply()
    }

    fun nbEnvoyees(ctx: Context, profil: Long): Int {
        var n = 0
        for (d in historique(ctx, profil)) if (d.envoyee) n++
        return n
    }
}
