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

/** Historique des demandes et amis récents, stockés sur le téléphone uniquement. */
object Store {
    private const val FICHIER = "donnees"
    private const val K_HIST = "historique"
    private const val K_RECENTS = "recents"
    private const val MAX_HIST = 300
    private const val MAX_RECENTS = 12

    private fun p(ctx: Context) = ctx.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)

    // ---------- amis récents ----------

    fun recents(ctx: Context): ArrayList<String> {
        val out = ArrayList<String>()
        try {
            val a = JSONArray(p(ctx).getString(K_RECENTS, "[]"))
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (e: Exception) {
        }
        return out
    }

    private fun saveRecents(ctx: Context, list: List<String>) {
        val a = JSONArray()
        for (i in 0 until minOf(list.size, MAX_RECENTS)) a.put(list[i])
        p(ctx).edit().putString(K_RECENTS, a.toString()).apply()
    }

    fun addRecents(ctx: Context, noms: List<String>) {
        val cur = recents(ctx)
        val out = ArrayList<String>()
        for (n in noms) out.add(n)
        for (c in cur) {
            var dup = false
            for (n in noms) if (n.equals(c, ignoreCase = true)) dup = true
            if (!dup) out.add(c)
        }
        saveRecents(ctx, out)
    }

    fun removeRecent(ctx: Context, nom: String) {
        val out = ArrayList<String>()
        for (c in recents(ctx)) if (!c.equals(nom, ignoreCase = true)) out.add(c)
        saveRecents(ctx, out)
    }

    // ---------- historique ----------

    fun historique(ctx: Context): ArrayList<Demande> {
        val out = ArrayList<Demande>()
        try {
            val a = JSONArray(p(ctx).getString(K_HIST, "[]"))
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

    private fun save(ctx: Context, list: List<Demande>) {
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
        p(ctx).edit().putString(K_HIST, a.toString()).apply()
    }

    fun ajouter(ctx: Context, ami: String): Long {
        val list = historique(ctx)
        val now = System.currentTimeMillis()
        val d = Demande(now, ami, now, false, 0L)
        list.add(0, d)
        save(ctx, list)
        return d.id
    }

    fun marquerEnvoyee(ctx: Context, id: Long) {
        val list = historique(ctx)
        for (d in list) if (d.id == id && !d.envoyee) {
            d.envoyee = true
            d.envoyeeLe = System.currentTimeMillis()
        }
        save(ctx, list)
    }

    fun supprimer(ctx: Context, id: Long) {
        val out = ArrayList<Demande>()
        for (d in historique(ctx)) if (d.id != id) out.add(d)
        save(ctx, out)
    }

    fun effacer(ctx: Context) {
        p(ctx).edit().remove(K_HIST).apply()
    }

    fun nbEnvoyees(ctx: Context): Int {
        var n = 0
        for (d in historique(ctx)) if (d.envoyee) n++
        return n
    }
}
