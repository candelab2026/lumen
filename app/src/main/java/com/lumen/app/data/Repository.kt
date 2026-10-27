package com.lumen.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.Xml
import com.lumen.app.Config
import com.lumen.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone

private const val HOUR = 3_600_000L
private const val RELEASES_API = "https://api.github.com/repos/candelab2026/lumen/releases/latest"
private const val EXT = "ext:"
private const val EXT_EPG_URL = "https://epgshare01.online/epgshare01/epg_ripper_%s1.xml.gz"

/** Rende utilizzabile l'indirizzo scritto dall'utente: aggiunge http:// se manca e toglie spazi e slash finali. */
fun normalizeServer(raw: String): String {
    var u = raw.trim().trimEnd('/')
    if (u.isEmpty()) return ""
    if (!u.contains("://")) u = "http://$u"
    return u
}

/** Client Xtream Codes + cache su disco. Le liste grandi vengono lette in streaming per risparmiare RAM. */
class Repository(ctx: Context) {
    private val app = ctx.applicationContext
    private val dir = File(ctx.filesDir, "cache").apply { mkdirs() }
    private var base = ""
    private var user = ""
    private var pass = ""

    /** Se attivo, per i canali senza guida sul server si cerca la guida in un XMLTV pubblico (abbinando i nomi). */
    @Volatile
    var publicEpg = true

    fun setServer(url: String) {
        base = normalizeServer(url)
    }

    fun setCredentials(u: String, p: String) {
        user = u
        pass = p
    }

    /** Età in ms del file di cache (Long.MAX_VALUE se non esiste). */
    fun cacheAge(name: String): Long {
        val f = File(dir, name)
        return if (f.exists()) System.currentTimeMillis() - f.lastModified() else Long.MAX_VALUE
    }

    fun clearCache() {
        dir.listFiles()?.forEach { it.delete() }
    }

    // ---------- URL ----------

    private fun apiUrl(action: String?, extra: String = ""): String =
        "$base/player_api.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}" +
            (if (action != null) "&action=$action" else "") + extra

    fun liveUrl(c: Channel, ext: String) = "$base/live/${Uri.encode(user)}/${Uri.encode(pass)}/${c.id}.$ext"
    fun movieUrl(m: Movie) = "$base/movie/${Uri.encode(user)}/${Uri.encode(pass)}/${m.id}.${m.ext}"
    fun vodUrl(kind: String, rid: String, ext: String) = "$base/$kind/${Uri.encode(user)}/${Uri.encode(pass)}/$rid.$ext"
    fun episodeUrl(e: Episode) = "$base/series/${Uri.encode(user)}/${Uri.encode(pass)}/${e.id}.${e.ext}"

    // ---------- HTTP ----------

    private fun open(url: String, readTimeout: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = readTimeout
        c.setRequestProperty("User-Agent", Config.USER_AGENT)
        c.instanceFollowRedirects = true
        if (c.responseCode != 200) {
            val code = c.responseCode
            c.disconnect()
            throw IOException(app.getString(R.string.http_error, code))
        }
        return c
    }

    private fun httpGet(url: String): String {
        val c = open(url, 30_000)
        try {
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Avanzamento dell'aggiornamento: etichetta della fase e frazione complessiva 0..1. */
    @Volatile
    var progress: ((String, Float) -> Unit)? = null

    /** Scarica [url]; se è indicata una fase, riporta l'avanzamento tra [lo] e [hi] (stimato se la dimensione è ignota). */
    private fun download(url: String, target: File, label: String? = null, lo: Float = 0f, hi: Float = 1f) {
        val tmp = File(target.path + ".tmp")
        val c = open(url, 120_000)
        try {
            val total = c.contentLengthLong
            var got = 0L
            var last = 0L
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        val t = System.currentTimeMillis()
                        if (label != null && t - last > 80) {
                            last = t
                            val f = if (total > 0) got.toFloat() / total else 1f - 1f / (1f + got / 1_500_000f)
                            progress?.invoke(label, lo + (hi - lo) * f.coerceIn(0f, 0.98f))
                        }
                    }
                }
            }
        } catch (e: IOException) {
            tmp.delete()
            throw e
        } finally {
            c.disconnect()
        }
        target.delete()
        if (!tmp.renameTo(target)) throw IOException(app.getString(R.string.cache_err))
        if (label != null) progress?.invoke(label, hi)
    }

    private fun cached(name: String, ttl: Long, force: Boolean, url: String, label: String? = null, lo: Float = 0f, hi: Float = 1f): File {
        val f = File(dir, name)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < ttl
        if (fresh && !force) return f
        try {
            download(url, f, label, lo, hi)
        } catch (e: IOException) {
            if (!f.exists()) throw e
        }
        return f
    }

    // ---------- Aggiornamento dell'app (GitHub Releases) ----------

    /** Ultima release pubblicata su GitHub se è più recente di [current], altrimenti null. */
    suspend fun latestRelease(current: String): AppUpdate? = withContext(Dispatchers.IO) {
        val j = JSONObject(httpGet(RELEASES_API))
        val version = j.optString("tag_name").trimStart('v', 'V')
        val assets = j.optJSONArray("assets") ?: return@withContext null
        var url: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", true)) url = a.optString("browser_download_url")
        }
        if (url == null || !isNewer(version, current)) null else AppUpdate(version, url)
    }

    /** Scarica l'APK nella cache; l'installazione sopra l'app esistente (stessa firma) conserva tutti i dati. */
    suspend fun downloadApk(u: AppUpdate, label: String): File = withContext(Dispatchers.IO) {
        val f = File(dir, "update.apk")
        download(u.url, f, label, 0f, 1f)
        f
    }

    private fun isNewer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    // ---------- Login ----------

    suspend fun login(u: String, p: String): AccountInfo = withContext(Dispatchers.IO) {
        setCredentials(u, p)
        val j = try {
            JSONObject(httpGet(apiUrl(null)))
        } catch (e: org.json.JSONException) {
            throw IOException(app.getString(R.string.login_invalid_resp))
        }
        val ui = j.optJSONObject("user_info") ?: throw AuthException(app.getString(R.string.login_bad))
        if (ui.optInt("auth", 0) != 1) throw AuthException(app.getString(R.string.login_bad))
        val status = ui.optString("status", "Active")
        if (!status.equals("Active", true)) throw AuthException(app.getString(R.string.login_inactive, status))
        AccountInfo(
            username = ui.optString("username", u),
            status = status,
            expiry = ui.optString("exp_date").toLongOrNull()?.times(1000),
            maxConnections = ui.optString("max_connections").toIntOrNull() ?: 1,
            activeConnections = ui.optString("active_cons").toIntOrNull() ?: 0,
        )
    }

    // ---------- Liste (streaming JSON) ----------

    private inline fun parseArray(f: File, block: (Map<String, String>) -> Unit) {
        JsonReader(InputStreamReader(BufferedInputStream(FileInputStream(f), 64 * 1024), "UTF-8")).use { r ->
            r.isLenient = true
            if (r.peek() != JsonToken.BEGIN_ARRAY) return
            r.beginArray()
            while (r.hasNext()) block(readFlat(r))
            r.endArray()
        }
    }

    private fun readFlat(r: JsonReader): Map<String, String> {
        val m = HashMap<String, String>(16)
        if (r.peek() != JsonToken.BEGIN_OBJECT) {
            r.skipValue()
            return m
        }
        r.beginObject()
        while (r.hasNext()) {
            val n = r.nextName()
            when (r.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> m[n] = r.nextString()
                JsonToken.BOOLEAN -> m[n] = r.nextBoolean().toString()
                JsonToken.NULL -> r.nextNull()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return m
    }

    private fun categories(name: String, action: String, force: Boolean): List<Category> {
        val f = cached(name, Long.MAX_VALUE, force, apiUrl(action))
        val out = ArrayList<Category>()
        parseArray(f) { m ->
            val id = m["category_id"] ?: return@parseArray
            out.add(Category(id, m["category_name"]?.trim().orEmpty().ifEmpty { app.getString(R.string.cat_other) }))
        }
        return out
    }

    private fun String?.clean(): String? = this?.takeIf { it.isNotBlank() && it != "null" }

    suspend fun liveCategories(force: Boolean) = withContext(Dispatchers.IO) {
        categories("live_cats.json", "get_live_categories", force)
    }

    suspend fun liveChannels(force: Boolean): List<Channel> = withContext(Dispatchers.IO) {
        val f = cached("live.json", Long.MAX_VALUE, force, apiUrl("get_live_streams"), app.getString(R.string.upd_channels), 0f, 0.3f)
        val out = ArrayList<Channel>()
        parseArray(f) { m ->
            val id = m["stream_id"]?.toIntOrNull() ?: return@parseArray
            out.add(
                Channel(
                    id = id,
                    num = m["num"]?.toIntOrNull() ?: (out.size + 1),
                    name = m["name"]?.trim().orEmpty(),
                    logo = m["stream_icon"].clean(),
                    catId = m["category_id"].orEmpty(),
                    epgId = m["epg_channel_id"].clean(),
                ),
            )
        }
        applyEpgAlias(out)
    }

    suspend fun vodCategories(force: Boolean) = withContext(Dispatchers.IO) {
        categories("vod_cats.json", "get_vod_categories", force)
    }

    suspend fun movies(force: Boolean): List<Movie> = withContext(Dispatchers.IO) {
        val f = cached("vod.json", Long.MAX_VALUE, force, apiUrl("get_vod_streams"))
        val out = ArrayList<Movie>()
        parseArray(f) { m ->
            val id = m["stream_id"]?.toIntOrNull() ?: return@parseArray
            out.add(
                Movie(
                    id = id,
                    name = m["name"]?.trim().orEmpty(),
                    poster = m["stream_icon"].clean(),
                    catId = m["category_id"].orEmpty(),
                    ext = m["container_extension"].clean() ?: "mp4",
                    rating = m["rating"].clean(),
                ),
            )
        }
        out
    }

    suspend fun seriesCategories(force: Boolean) = withContext(Dispatchers.IO) {
        categories("series_cats.json", "get_series_categories", force)
    }

    suspend fun seriesList(force: Boolean): List<Series> = withContext(Dispatchers.IO) {
        val f = cached("series.json", Long.MAX_VALUE, force, apiUrl("get_series"))
        val out = ArrayList<Series>()
        parseArray(f) { m ->
            val id = m["series_id"]?.toIntOrNull() ?: return@parseArray
            out.add(
                Series(
                    id = id,
                    name = m["name"]?.trim().orEmpty(),
                    cover = m["cover"].clean(),
                    catId = m["category_id"].orEmpty(),
                    plot = m["plot"].clean(),
                    rating = m["rating"].clean(),
                ),
            )
        }
        out
    }

    // ---------- Dettagli ----------

    suspend fun movieInfo(id: Int): MovieInfo = withContext(Dispatchers.IO) {
        val info = JSONObject(httpGet(apiUrl("get_vod_info", "&vod_id=$id"))).optJSONObject("info")
            ?: return@withContext MovieInfo(null, null, null, null, null, null)
        MovieInfo(
            plot = (info.optString("plot").ifEmpty { info.optString("description") }).clean(),
            genre = info.optString("genre").clean(),
            year = info.optString("releasedate").clean()?.take(4) ?: info.optString("year").clean(),
            cast = info.optString("cast").ifEmpty { info.optString("actors") }.clean(),
            duration = info.optString("duration").clean(),
            backdrop = info.optJSONArray("backdrop_path")?.optString(0).clean()
                ?: info.optString("movie_image").clean(),
        )
    }

    suspend fun seriesDetail(id: Int): SeriesDetail = withContext(Dispatchers.IO) {
        val j = JSONObject(httpGet(apiUrl("get_series_info", "&series_id=$id")))
        val info = j.optJSONObject("info")
        val seasons = sortedMapOf<Int, MutableList<Episode>>()

        fun addEpisode(o: JSONObject, seasonHint: Int) {
            val epId = o.optString("id").clean() ?: return
            val ei = o.optJSONObject("info")
            val season = o.optString("season").toIntOrNull() ?: seasonHint
            seasons.getOrPut(season) { ArrayList() }.add(
                Episode(
                    id = epId,
                    season = season,
                    num = o.optString("episode_num").toIntOrNull() ?: 0,
                    title = o.optString("title").ifEmpty { app.getString(R.string.episode_n, o.optString("episode_num").toIntOrNull() ?: 0) },
                    ext = o.optString("container_extension").clean() ?: "mp4",
                    plot = ei?.optString("plot").clean(),
                    image = ei?.optString("movie_image").clean(),
                    duration = ei?.optString("duration").clean(),
                ),
            )
        }

        fun addGroup(arr: JSONArray?, hint: Int) {
            if (arr == null) return
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { addEpisode(it, hint) }
        }

        when (val eps = j.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k -> addGroup(eps.optJSONArray(k), k.toIntOrNull() ?: 1) }
            is JSONArray -> for (i in 0 until eps.length()) {
                val g = eps.optJSONArray(i)
                if (g != null) addGroup(g, i + 1) else eps.optJSONObject(i)?.let { addEpisode(it, 1) }
            }
        }
        seasons.values.forEach { l -> l.sortBy { it.num } }

        SeriesDetail(
            plot = info?.optString("plot").clean(),
            genre = info?.optString("genre").clean(),
            year = info?.optString("releaseDate").clean()?.take(4) ?: info?.optString("release_date").clean()?.take(4),
            cast = info?.optString("cast").clean(),
            backdrop = info?.optJSONArray("backdrop_path")?.optString(0).clean() ?: info?.optString("cover").clean(),
            seasons = seasons,
        )
    }

    // ---------- EPG ----------

    suspend fun shortEpg(streamId: Int, limit: Int = 8): List<Programme> = withContext(Dispatchers.IO) {
        val arr = JSONObject(httpGet(apiUrl("get_short_epg", "&stream_id=$streamId&limit=$limit")))
            .optJSONArray("epg_listings") ?: return@withContext emptyList()
        val out = ArrayList<Programme>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val s = o.optString("start_timestamp").toLongOrNull() ?: continue
            val e = o.optString("stop_timestamp").toLongOrNull() ?: continue
            out.add(Programme(s * 1000, e * 1000, b64(o.optString("title")), b64(o.optString("description"))))
        }
        out
    }

    private fun b64(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        s
    }

    private val epgFile get() = File(dir, "epg.tsv")

    fun epgAge(): Long =
        if (epgFile.exists()) System.currentTimeMillis() - epgFile.lastModified() else Long.MAX_VALUE

    suspend fun loadEpgCache(): Map<String, List<Programme>> = withContext(Dispatchers.IO) {
        val f = epgFile
        if (!f.exists()) return@withContext emptyMap()
        val map = HashMap<String, MutableList<Programme>>()
        try {
            f.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val p = line.split('\t', limit = 5)
                    if (p.size < 5) continue
                    val s = p[1].toLongOrNull() ?: continue
                    val e = p[2].toLongOrNull() ?: continue
                    map.getOrPut(p[0]) { ArrayList() }.add(Programme(s, e, p[3], p[4]))
                }
            }
        } catch (e: IOException) {
            return@withContext emptyMap()
        }
        map.values.forEach { l -> l.sortBy { it.start } }
        map
    }

    private val aliasFile get() = File(dir, "epgalias.tsv")

    /** Canali senza guida sul server ma trovati in quella pubblica (per nome): stream_id -> chiave EPG. */
    private fun loadAlias(): Map<Int, String> {
        val f = aliasFile
        if (!f.exists()) return emptyMap()
        val m = HashMap<Int, String>()
        try {
            f.forEachLine { l ->
                val p = l.split('\t')
                if (p.size == 2) p[0].toIntOrNull()?.let { m[it] = p[1] }
            }
        } catch (e: IOException) {
            return emptyMap()
        }
        return m
    }

    /** Assegna ai canali la guida pubblica trovata per nome; i nomi mostrati non cambiano. */
    fun applyEpgAlias(list: List<Channel>): List<Channel> {
        val a = loadAlias()
        if (a.isEmpty()) return list
        return list.map { c -> a[c.id]?.let { c.copy(epgId = it) } ?: c }
    }

    /** Nome del canale ridotto a lettere e cifre, senza prefissi paese né suffissi di qualità: serve solo al confronto. */
    private fun epgKey(name: String): String {
        var n = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        n = n.replace(Regex("[\\(\\[][^\\)\\]]*[\\)\\]]"), " ")
            .replace(Regex("^[\\s|\\[(]*(it|ita|italia)[\\s|:\\])\\-]+"), " ")
            .replace("+", " plus ")
            .replace("&", " e ")
        n = n.replace(Regex("\\b(hd|fhd|uhd|sd|4k|h265|hevc|raw|hdr|full|ultra)\\b"), " ")
        return n.replace(Regex("[^a-z0-9]"), "")
    }

    /** Corrispondenza esatta, oppure il nome pubblico più lungo con cui inizia il canale (resto breve, mai "+1" o numeri). */
    private fun findExt(names: Map<String, String>, key: String): String? {
        if (key.length < 4) return null
        names[key]?.let { return it }
        var best: String? = null
        var bestLen = 0
        for ((k, id) in names) {
            if (k.length < 6 || k.length <= bestLen || !key.startsWith(k)) continue
            val rest = key.substring(k.length)
            if (rest.length > 4 || rest[0].isDigit() || rest.startsWith("plus")) continue
            best = id
            bestLen = k.length
        }
        return best
    }

    /** True se la ricerca nella guida pubblica è già stata fatta almeno una volta. */
    fun epgAliasDone(): Boolean = aliasFile.exists()

    /**
     * Legge un XMLTV e accumula in [sb] i programmi di oggi/domani dei canali per cui [isWanted] è vero.
     * [onChannel] riceve id e nomi dei canali; [onProgrammes] scatta prima del primo programma.
     */
    private fun scanXmltv(
        input: java.io.InputStream,
        sb: StringBuilder,
        keyPrefix: String,
        isWanted: (String) -> Boolean,
        onChannel: ((String, String) -> Unit)? = null,
        onProgrammes: (() -> Unit)? = null,
    ) {
        val now = System.currentTimeMillis()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var ev = parser.eventType
        var chan = ""
        var curChan = ""
        var start = 0L
        var stop = 0L
        var title = ""
        var desc = ""
        var keep = false
        var started = false
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> curChan = parser.getAttributeValue(null, "id").orEmpty()
                    "display-name" -> if (onChannel != null && curChan.isNotEmpty()) onChannel(curChan, parser.nextText())
                    "programme" -> {
                        if (!started) {
                            started = true
                            onProgrammes?.invoke()
                        }
                        chan = parser.getAttributeValue(null, "channel").orEmpty()
                        start = xmltvTime(parser.getAttributeValue(null, "start"))
                        stop = xmltvTime(parser.getAttributeValue(null, "stop"))
                        title = ""
                        desc = ""
                        keep = stop > now - 2 * HOUR && start < now + 36 * HOUR && isWanted(chan)
                    }
                    "title" -> if (keep && title.isEmpty()) title = flat(parser.nextText())
                    "desc" -> if (keep && desc.isEmpty()) desc = flat(parser.nextText())
                }
            } else if (ev == XmlPullParser.END_TAG && parser.name == "programme" && keep) {
                sb.append(keyPrefix).append(chan).append('\t').append(start).append('\t').append(stop).append('\t')
                    .append(title).append('\t').append(desc).append('\n')
                keep = false
            }
            ev = parser.next()
        }
    }

    /**
     * Scarica l'XMLTV del server e tiene solo i programmi di oggi/domani dei canali in elenco.
     * Per i canali che il server non copre cerca la guida pubblica abbinando i nomi.
     */
    suspend fun refreshEpg(channels: List<Channel>): Map<String, List<Programme>> = withContext(Dispatchers.IO) {
        val wanted = channels.mapNotNull { it.epgId }.filter { !it.startsWith(EXT) }.toSet()
        if (channels.isEmpty()) return@withContext emptyMap()
        val sb = StringBuilder()
        val have = HashSet<String>()
        if (wanted.isNotEmpty()) {
            val xml = File(dir, "xmltv.xml")
            download("$base/xmltv.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}", xml, app.getString(R.string.upd_server_guide), 0.3f, 0.7f)
            try {
                val tmp = StringBuilder()
                BufferedInputStream(FileInputStream(xml), 64 * 1024).use { scanXmltv(it, tmp, "", { c -> c in wanted }) }
                sb.append(tmp)
                tmp.lineSequence().forEach { l -> l.substringBefore('	').takeIf { it.isNotEmpty() }?.let(have::add) }
            } finally {
                xml.delete()
            }
        }
        val country = java.util.Locale.getDefault().country.uppercase()
        val needy = if (!publicEpg || country.isEmpty()) emptyList() else channels.filter { c -> c.epgId?.takeIf { !it.startsWith(EXT) } !in have }
        val alias = HashMap<Int, String>()
        if (needy.isNotEmpty()) {
            try {
                val gz = File(dir, "epg_ext.xml.gz")
                download(EXT_EPG_URL.format(country), gz, app.getString(R.string.upd_public_guide), 0.7f, 0.95f)
                try {
                    val names = HashMap<String, String>()
                    val ids = HashSet<String>()
                    java.util.zip.GZIPInputStream(BufferedInputStream(FileInputStream(gz), 64 * 1024)).use { gin ->
                        scanXmltv(
                            gin, sb, EXT, { c -> c in ids },
                            onChannel = { id, name ->
                                names.putIfAbsent(epgKey(name), id)
                                names.putIfAbsent(epgKey(id.replace(Regex("\\.[a-z]{2}$"), "")), id)
                            },
                            onProgrammes = {
                                for (c in needy) {
                                    val id = findExt(names, epgKey(c.name)) ?: continue
                                    alias[c.id] = EXT + id
                                    ids.add(id)
                                }
                            },
                        )
                    }
                } finally {
                    gz.delete()
                }
                aliasFile.writeText(alias.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            } catch (e: Exception) {
                // guida pubblica non raggiungibile: restano solo i canali coperti dal server, si riproverà
                alias.clear()
                aliasFile.writeText("")
            }
        } else {
            aliasFile.writeText("")
        }
        progress?.invoke(app.getString(R.string.upd_saving), 0.97f)
        epgFile.writeText(sb.toString())
        loadEpgCache()
    }

    private fun flat(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    private val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    private fun xmltvTime(s: String?): Long {
        if (s == null || s.length < 14) return 0
        return try {
            cal.clear()
            cal.set(
                s.substring(0, 4).toInt(), s.substring(4, 6).toInt() - 1, s.substring(6, 8).toInt(),
                s.substring(8, 10).toInt(), s.substring(10, 12).toInt(), s.substring(12, 14).toInt(),
            )
            var t = cal.timeInMillis
            val tz = s.substring(14).trim()
            if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
                val sign = if (tz[0] == '-') -1 else 1
                t -= sign * (tz.substring(1, 3).toInt() * 60 + tz.substring(3, 5).toInt()) * 60_000L
            }
            t
        } catch (e: NumberFormatException) {
            0
        }
    }
}

/** EPG in memoria: programmi per id canale, ordinati per orario. */
class EpgStore {
    @Volatile
    private var map: Map<String, List<Programme>> = emptyMap()

    fun set(m: Map<String, List<Programme>>) {
        map = m
    }

    fun has(id: String?) = id != null && map.containsKey(id)

    fun nowNext(id: String?, t: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
        val l = map[id ?: return null to null] ?: return null to null
        val i = l.indexOfFirst { it.stop > t }
        if (i < 0) return null to null
        val cur = l[i]
        return if (cur.start <= t) cur to l.getOrNull(i + 1) else null to cur
    }

    /** Programmi che si sovrappongono all'intervallo [from, to). */
    fun between(id: String?, from: Long, to: Long): List<Programme> {
        val l = map[id ?: return emptyList()] ?: return emptyList()
        return l.filter { it.stop > from && it.start < to }
    }

    fun upcoming(id: String?, limit: Int, t: Long = System.currentTimeMillis()): List<Programme> {
        val l = map[id ?: return emptyList()] ?: return emptyList()
        return l.filter { it.stop > t }.take(limit)
    }
}
