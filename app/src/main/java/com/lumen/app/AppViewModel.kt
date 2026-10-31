package com.lumen.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.app.data.AccountInfo
import com.lumen.app.data.AuthException
import com.lumen.app.data.Category
import com.lumen.app.data.ContinueItem
import com.lumen.app.data.Channel
import com.lumen.app.data.Episode
import com.lumen.app.data.EpgStore
import com.lumen.app.data.Movie
import com.lumen.app.data.Repository
import com.lumen.app.data.Series
import com.lumen.app.data.AppUpdate
import com.lumen.app.data.Store
import com.lumen.app.data.normalizeServer
import kotlinx.coroutines.launch
import java.io.IOException

enum class Status { Idle, Loading, Ready, Error }

/** Contenuto di una sezione (live, film, serie) già raggruppato per categoria. */
class ListState<T>(
    val status: Status = Status.Idle,
    val cats: List<Category> = emptyList(),
    val items: List<T> = emptyList(),
    val byCat: Map<String, List<T>> = emptyMap(),
    val error: String? = null,
)

class VodEntry(val title: String, val url: String, val key: String, val item: ContinueItem? = null)

sealed class Screen {
    object Splash : Screen()
    object Login : Screen()
    object Home : Screen()
    object Help : Screen()
    class MovieDetail(val movie: Movie) : Screen()
    class SeriesDetail(val series: Series) : Screen()
    class PlayLive(val channels: List<Channel>, val index: Int) : Screen()
    class PlayVod(val entries: List<VodEntry>, val index: Int) : Screen()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val repo = Repository(app).also { it.publicEpg = store.publicEpg }
    val epg = EpgStore()

    private fun s(@androidx.annotation.StringRes id: Int, vararg a: Any): String = getApplication<Application>().getString(id, *a)

    private fun dateText(ms: Long): String =
        java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(ms))

    /** Indirizzo del server scelto nella pagina di accesso. */
    var server by mutableStateOf(store.server.orEmpty())
        private set
    var publicEpg by mutableStateOf(store.publicEpg)
        private set

    /** Versione più recente trovata su GitHub (null se aggiornati o non ancora controllato). */
    var newVersion by mutableStateOf<AppUpdate?>(null)
        private set
    /** Avviso temporaneo (5 s) mostrato dopo l'intro; esito dell'ultimo controllo mostrato nelle Impostazioni. */
    var updateNotice by mutableStateOf<String?>(null)
        private set
    var updateStatus by mutableStateOf<String?>(null)
        private set
    private var updating = false

    fun clearUpdateNotice() {
        updateNotice = null
    }

    fun clearUpdateStatus() {
        updateStatus = null
    }

    /** Controllo automatico ogni 5 aperture; [manual] mostra sempre l'esito nelle Impostazioni. */
    fun checkAppUpdate(manual: Boolean) {
        viewModelScope.launch {
            try {
                if (manual) updateStatus = s(R.string.upd_checking)
                val u = repo.latestRelease(BuildConfig.VERSION_NAME)
                store.markRefreshed("appupdate")
                newVersion = u
                updateStatus = if (u == null) s(R.string.upd_uptodate) else null
                if (u != null && !manual) updateNotice = s(R.string.upd_notice, u.version)
            } catch (e: Exception) {
                updateStatus = if (manual) s(R.string.upd_failed, e.message ?: s(R.string.net_error)) else null
            }
        }
    }

    /** Scarica l'APK e apre l'installazione di Android: i dati dell'utente restano (stesso pacchetto e stessa firma). */
    fun installUpdate() {
        val u = newVersion ?: return checkAppUpdate(true)
        if (updating) return
        val ctx = getApplication<Application>()
        if (android.os.Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            updateStatus = s(R.string.upd_allow)
            ctx.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + ctx.packageName))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        updating = true
        updateStatus = null
        viewModelScope.launch {
            try {
                repo.progress = ::reportUpdate
                val f = repo.downloadApk(u, s(R.string.upd_downloading))
                update = null
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                ctx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: Exception) {
                update = null
                updateStatus = s(R.string.upd_failed, e.message ?: s(R.string.net_error))
            } finally {
                updating = false
            }
        }
    }

    var screens by mutableStateOf<List<Screen>>(listOf(Screen.Splash))
        private set
    // 0 Per te, 1 TV Live, 2 Film, 3 Serie, 4 Guida TV, 5 Impostazioni
    var tab by mutableIntStateOf(if (store.recentChannels().isNotEmpty() || store.continueList().isNotEmpty()) 0 else 1)
    var account by mutableStateOf<AccountInfo?>(null)
    var loginError by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var epgVersion by mutableIntStateOf(0)
        private set

    /** Aggiornamento in corso di canali e guida TV: (fase, frazione 0..1), null quando non c'è nulla da mostrare. */
    var update by mutableStateOf<Pair<String, Float>?>(null)
        private set
    private var updateJob: kotlinx.coroutines.Job? = null

    private fun reportUpdate(label: String, frac: Float) {
        updateJob?.cancel()
        update = label to frac
    }

    private fun finishUpdate() {
        updateJob?.cancel()
        if (update == null) return
        update = s(R.string.upd_done) to 1f
        updateJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1_500)
            update = null
        }
    }

    var favs by mutableStateOf(store.favorites())
        private set

    var live by mutableStateOf(ListState<Channel>())
        private set
    var vod by mutableStateOf(ListState<Movie>())
        private set
    var series by mutableStateOf(ListState<Series>())
        private set

    var recents by mutableStateOf(store.recentChannels())
        private set
    var cont by mutableStateOf(store.continueList())
        private set
    var profile by mutableIntStateOf(store.profile)
        private set
    var autoStart by mutableStateOf(store.autoStart)
        private set
    var textScale by mutableIntStateOf(store.textScale)
        private set

    // selezioni ricordate tra una schermata e l'altra: si parte dai preferiti se ce ne sono
    var liveCat by mutableStateOf(startCat("L"))
    var vodCat by mutableStateOf(startCat("M"))
    var seriesCat by mutableStateOf(startCat("S"))
    var lastFocus by mutableStateOf("")

    /** Compose non ha nessun elemento col focus: il primo tasto direzionale lo assegna invece di andare perso. */
    var anyFocus = false
    var focusKick by mutableIntStateOf(0)

    private var autoStartPending = false

    /** Promemoria di rinnovo da mostrare dopo l'accesso (un mese prima della scadenza); null se non serve. */
    var renewalNotice by mutableStateOf<String?>(null)
        private set

    fun dismissRenewal() {
        renewalNotice = null
    }

    fun expiryText(acc: AccountInfo? = account): String? {
        val exp = acc?.expiry ?: return null
        return s(R.string.expiry_line, dateText(exp))
    }

    private fun checkRenewal(acc: AccountInfo) {
        val exp = acc.expiry ?: return
        val days = (exp - System.currentTimeMillis()) / 86_400_000L
        if (days > 30) return
        val date = dateText(exp)
        renewalNotice = when {
            days < 0 -> s(R.string.renewal_expired, date)
            days == 0L -> s(R.string.renewal_today, date)
            else -> s(R.string.renewal_days, date, days.toInt())
        }
    }

    private fun startCat(prefix: String) = if (favs.any { it.startsWith(prefix) }) "fav" else "*"

    init {
        store.registerLaunch()
        if (store.due("appupdate")) checkAppUpdate(false)
        autoStartPending = store.autoStart && store.lastChannel >= 0
        val u = store.user
        val p = store.pass
        val srv = store.server
        if (u != null && p != null && !srv.isNullOrBlank()) {
            viewModelScope.launch {
                repo.setServer(srv)
                repo.setCredentials(u, p)
                try {
                    account = repo.login(u, p)
                    account?.let { checkRenewal(it) }
                    enterHome()
                } catch (e: AuthException) {
                    loginError = e.message
                    screens = listOf(Screen.Login)
                } catch (e: IOException) {
                    enterHome() // offline: si prova con la cache
                }
            }
        } else {
            screens = listOf(Screen.Login)
        }
    }

    // ---------- Navigazione ----------

    private fun enterHome() {
        screens = listOf(Screen.Home)
        ensureLoaded(tab)
    }

    fun push(s: Screen) {
        screens = screens + s
    }

    fun back(): Boolean {
        if (screens.size <= 1) return false
        screens = screens.dropLast(1)
        return true
    }

    // ---------- Login ----------

    fun login(srv: String, u: String, p: String) {
        if (busy) return
        val user = u.trim()
        val address = normalizeServer(srv)
        if (address.isEmpty() || user.isEmpty() || p.isEmpty()) {
            loginError = s(R.string.login_missing)
            return
        }
        busy = true
        loginError = null
        viewModelScope.launch {
            try {
                repo.setServer(address)
                val acc = repo.login(user, p)
                if (store.user != user || store.server != address) repo.clearCache()
                store.saveLogin(address, user, p)
                server = address
                account = acc
                checkRenewal(acc)
                live = ListState()
                vod = ListState()
                series = ListState()
                tab = 1
                enterHome()
            } catch (e: AuthException) {
                loginError = e.message
            } catch (e: IOException) {
                loginError = s(R.string.login_unreachable, e.message ?: s(R.string.login_check_conn))
            } finally {
                busy = false
            }
        }
    }

    fun logout() {
        store.clearLogin()
        repo.clearCache()
        account = null
        live = ListState()
        vod = ListState()
        series = ListState()
        favs = emptySet()
        recents = emptyList()
        cont = emptyList()
        profile = 0
        autoStart = false
        liveCat = "*"
        vodCat = "*"
        seriesCat = "*"
        tab = 1
        loginError = null
        screens = listOf(Screen.Login)
    }

    // ---------- Caricamento liste ----------

    fun ensureLoaded(t: Int) {
        when (t) {
            0, 1, 4 -> if (live.status == Status.Idle || live.status == Status.Error) loadLive(false)
            2 -> if (vod.status == Status.Idle || vod.status == Status.Error) loadVod(false)
            3 -> if (series.status == Status.Idle || series.status == Status.Error) loadSeries(false)
        }
    }

    fun refreshAll() {
        if (live.status != Status.Idle) loadLive(true)
        if (vod.status != Status.Idle) loadVod(true)
        if (series.status != Status.Idle) loadSeries(true)
    }

    /** Le liste si riscaricano ogni 3 aperture dell'app (o a richiesta); altrimenti si usa la cache. */
    private fun markIfFetched(section: String, file: String) {
        if (repo.cacheAge(file) < 5 * 60_000L) store.markRefreshed(section)
    }

    private fun loadLive(force: Boolean) {
        live = ListState(Status.Loading)
        viewModelScope.launch {
            try {
                val f = force || store.due("live")
                repo.progress = ::reportUpdate
                val cats = repo.liveCategories(f)
                val items = repo.liveChannels(f)
                markIfFetched("live", "live.json")
                live = ListState(Status.Ready, cats, items, items.groupBy { it.catId })
                loadEpg(items, f)
                if (autoStartPending) {
                    autoStartPending = false
                    val i = items.indexOfFirst { it.id == store.lastChannel }
                    if (i >= 0 && screens.lastOrNull() is Screen.Home) push(Screen.PlayLive(items, i))
                }
            } catch (e: Exception) {
                update = null
                live = ListState(Status.Error, error = errorText(e))
            }
        }
    }

    private fun loadVod(force: Boolean) {
        vod = ListState(Status.Loading)
        viewModelScope.launch {
            try {
                val f = force || store.due("vod")
                val cats = repo.vodCategories(f)
                val items = repo.movies(f)
                markIfFetched("vod", "vod.json")
                vod = ListState(Status.Ready, cats, items, items.groupBy { it.catId })
            } catch (e: Exception) {
                vod = ListState(Status.Error, error = errorText(e))
            }
        }
    }

    private fun loadSeries(force: Boolean) {
        series = ListState(Status.Loading)
        viewModelScope.launch {
            try {
                val f = force || store.due("series")
                val cats = repo.seriesCategories(f)
                val items = repo.seriesList(f)
                markIfFetched("series", "series.json")
                series = ListState(Status.Ready, cats, items, items.groupBy { it.catId })
            } catch (e: Exception) {
                series = ListState(Status.Error, error = errorText(e))
            }
        }
    }

    private fun errorText(e: Exception) = when (e) {
        is IOException -> s(R.string.list_download_err, e.message ?: s(R.string.net_error))
        else -> s(R.string.read_err)
    }

    private fun loadEpg(channels: List<Channel>, force: Boolean) {
        viewModelScope.launch {
            epg.set(repo.loadEpgCache())
            epgVersion++
            // ogni 3 aperture, ma almeno ogni 24 ore: i programmi coprono solo oggi e domani
            if (force || store.due("epg") || repo.epgAge() > 24 * 3_600_000L || !repo.epgAliasDone()) {
                try {
                    repo.progress = ::reportUpdate
                    epg.set(repo.refreshEpg(channels))
                    store.markRefreshed("epg")
                    // i canali coperti solo dalla guida pubblica prendono la chiave trovata (i nomi restano uguali)
                    val cur = live
                    if (cur.status == Status.Ready) {
                        val items = repo.applyEpgAlias(cur.items)
                        live = ListState(Status.Ready, cur.cats, items, items.groupBy { it.catId })
                    }
                    epgVersion++
                } catch (e: Exception) {
                    // l'EPG è opzionale: se manca si usa quella breve del singolo canale
                    update = null
                }
                finishUpdate()
            } else {
                finishUpdate()
            }
        }
    }

    // ---------- Preferiti ----------

    fun toggleFav(key: String) {
        if (key !in favs) AppStats.event(getApplication(), "favorite_added")
        favs = if (key in favs) favs - key else favs + key
        store.setFavorites(favs)
    }

    // ---------- Profili e impostazioni ----------

    fun switchProfile() {
        store.profile = (store.profile + 1) % PROFILES
        profile = store.profile
        favs = store.favorites()
        recents = store.recentChannels()
        cont = store.continueList()
        liveCat = startCat("L")
        vodCat = startCat("M")
        seriesCat = startCat("S")
        lastFocus = ""
    }

    fun togglePublicEpg() {
        store.publicEpg = !store.publicEpg
        publicEpg = store.publicEpg
        repo.publicEpg = publicEpg
        if (live.status == Status.Ready) loadEpg(live.items, true)
    }

    fun toggleAutoStart() {
        store.autoStart = !store.autoStart
        autoStart = store.autoStart
    }

    fun cycleTextScale() {
        store.textScale = (store.textScale + 1) % 3
        textScale = store.textScale
    }

    // ---------- Recenti e "continua a guardare" ----------

    fun noteWatched(channelId: Int) {
        store.lastChannel = channelId
        store.pushRecent(channelId)
        recents = store.recentChannels()
    }

    fun noteContinue(item: ContinueItem) {
        if (store.touchContinue(item)) cont = store.continueList()
    }

    fun dropContinue(key: String) {
        store.clearResume(key)
        store.removeContinue(key)
        cont = store.continueList()
    }

    // ---------- Riproduzione ----------

    fun playLive(list: List<Channel>, index: Int) {
        AppStats.event(getApplication(), "play_live")
        push(Screen.PlayLive(list, index))
    }

    fun playMovie(m: Movie) {
        AppStats.event(getApplication(), "play_movie")
        val item = ContinueItem("M${m.id}", m.name, m.poster, "movie", m.id.toString(), m.ext)
        push(Screen.PlayVod(listOf(VodEntry(m.name, repo.movieUrl(m), item.key, item)), 0))
    }

    fun playContinue(c: ContinueItem) =
        push(Screen.PlayVod(listOf(VodEntry(c.title, repo.vodUrl(c.kind, c.rid, c.ext), c.key, c)), 0))

    fun playEpisodes(s: Series, eps: List<Episode>, index: Int) {
        AppStats.event(getApplication(), "play_series")
        val entries = eps.map {
            val title = "${s.name}  S${it.season}E${it.num}  ${it.title}"
            val item = ContinueItem("E${it.id}", title, s.cover, "series", it.id, it.ext, s.id.toString())
            VodEntry(title, repo.episodeUrl(it), item.key, item)
        }
        push(Screen.PlayVod(entries, index))
    }

    companion object {
        const val PROFILES = 4
    }
}
