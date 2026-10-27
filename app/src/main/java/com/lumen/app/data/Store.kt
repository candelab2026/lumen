package com.lumen.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Un film o un episodio iniziato: abbastanza dati per riaprirlo senza rileggere le liste. */
class ContinueItem(
    val key: String,
    val title: String,
    val poster: String?,
    val kind: String, // "movie" o "series"
    val rid: String,
    val ext: String,
    val parent: String = "", // per gli episodi: id della serie
)

/** Credenziali (cifrate quando possibile), preferiti, recenti e posizioni di ripresa (per profilo). */
class Store(ctx: Context) {
    private val secure: SharedPreferences = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "lumen_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Throwable) {
        ctx.getSharedPreferences("lumen_plain", Context.MODE_PRIVATE)
    }
    private val data: SharedPreferences = ctx.getSharedPreferences("lumen_data", Context.MODE_PRIVATE)

    val user: String? get() = secure.getString("user", null)
    val pass: String? get() = secure.getString("pass", null)

    /** Indirizzo del server IPTV scelto dall'utente nella pagina di accesso. */
    val server: String? get() = secure.getString("server", null)

    fun saveLogin(s: String, u: String, p: String) {
        secure.edit().putString("server", s).putString("user", u).putString("pass", p).apply()
    }

    /** Esce dall'account ma ricorda l'indirizzo del server, per non doverlo riscrivere. */
    fun clearLogin() {
        val s = server
        secure.edit().clear().apply()
        data.edit().clear().apply()
        if (s != null) secure.edit().putString("server", s).apply()
    }

    // ---------- profili: il profilo 0 usa le chiavi storiche, gli altri un prefisso ----------

    var profile: Int
        get() = data.getInt("profile", 0)
        set(v) = data.edit().putInt("profile", v).apply()

    private fun k(name: String) = if (profile == 0) name else "p${profile}_$name"

    // ---------- impostazioni ----------

    /** All'apertura dell'app riparte l'ultimo canale visto. */
    var autoStart: Boolean
        get() = data.getBoolean("autoStart", false)
        set(v) = data.edit().putBoolean("autoStart", v).apply()

    /** Guida pubblica di riserva per i canali senza guida sul server. */
    var publicEpg: Boolean
        get() = data.getBoolean("publicEpg", true)
        set(v) = data.edit().putBoolean("publicEpg", v).apply()

    /** 0 normale, 1 grande, 2 molto grande. */
    var textScale: Int
        get() = data.getInt("textScale", 1).coerceIn(0, 2)
        set(v) = data.edit().putInt("textScale", v).apply()

    // ---------- preferiti ----------

    /** Chiavi tipo "L123" (live), "M45" (film), "S9" (serie). */
    fun favorites(): Set<String> = HashSet(data.getStringSet(k("favs"), emptySet()) ?: emptySet())

    fun setFavorites(s: Set<String>) {
        data.edit().putStringSet(k("favs"), HashSet(s)).apply()
    }

    // ---------- aperture e aggiornamenti ----------

    val launches: Int get() = data.getInt("launches", 0)

    fun registerLaunch() {
        data.edit().putInt("launches", launches + 1).apply()
    }

    /** True se sono passate almeno `every` aperture dell'app dall'ultimo aggiornamento della sezione. */
    fun due(section: String, every: Int = 3): Boolean = launches - data.getInt("ref_$section", -100) >= every

    fun markRefreshed(section: String) {
        data.edit().putInt("ref_$section", launches).apply()
    }

    // ---------- canali recenti ----------

    var lastChannel: Int
        get() = data.getInt(k("lastChannel"), -1)
        set(v) = data.edit().putInt(k("lastChannel"), v).apply()

    fun recentChannels(): List<Int> =
        (data.getString(k("recent"), "") ?: "").split(',').mapNotNull { it.toIntOrNull() }

    fun pushRecent(id: Int) {
        val l = (listOf(id) + recentChannels().filter { it != id }).take(20)
        data.edit().putString(k("recent"), l.joinToString(",")).apply()
    }

    // ---------- riprese ----------

    fun resume(key: String): Long = data.getLong(k("r_$key"), 0L)

    fun setResume(key: String, pos: Long, dur: Long) {
        val e = data.edit()
        if (dur > 0 && pos > dur * 0.95) e.remove(k("r_$key")) else e.putLong(k("r_$key"), pos)
        e.apply()
    }

    fun clearResume(key: String) {
        data.edit().remove(k("r_$key")).apply()
    }

    // ---------- "continua a guardare" ----------

    fun continueList(): List<ContinueItem> =
        (data.getString(k("cont"), "") ?: "").split('\n').mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 6) null else ContinueItem(f[0], f[1], f[2].ifEmpty { null }, f[3], f[4], f[5], f.getOrElse(6) { "" })
        }

    /** Porta l'elemento in cima. Restituisce true se la lista è cambiata. */
    fun touchContinue(item: ContinueItem): Boolean {
        val old = continueList()
        if (old.firstOrNull()?.key == item.key) return false
        saveContinue((listOf(item) + old.filter { it.key != item.key }).take(20))
        return true
    }

    fun removeContinue(key: String) {
        val old = continueList()
        if (old.any { it.key == key }) saveContinue(old.filter { it.key != key })
    }

    private fun saveContinue(l: List<ContinueItem>) {
        fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
        val text = l.joinToString("\n") {
            listOf(it.key, clean(it.title), it.poster ?: "", it.kind, it.rid, it.ext, it.parent).joinToString("\t")
        }
        data.edit().putString(k("cont"), text).apply()
    }
}
