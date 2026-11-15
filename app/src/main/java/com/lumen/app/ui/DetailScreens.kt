package com.lumen.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumen.app.AppViewModel
import com.lumen.app.R
import com.lumen.app.data.MovieInfo
import com.lumen.app.data.SeriesDetail
import com.lumen.app.data.TitleExtra
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Riquadro di testo che scorre anche col telecomando: quando ha il focus ▲ ▼ scorrono il contenuto e,
 * arrivati in cima o in fondo, il focus passa agli elementi vicini. Se tutto il testo è già visibile non
 * prende il focus. Con [modal] il focus non esce mai (riquadri a tutto schermo).
 */
@Composable
fun DpadScroll(
    modifier: Modifier = Modifier,
    fr: FocusRequester? = null,
    modal: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val st = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val step = with(LocalDensity.current) { 110.dp.toPx() }
    val shape = RoundedCornerShape(10.dp)
    var m = modifier
    if (fr != null) m = m.focusRequester(fr)
    Box(
        m.onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                val dir = when (ev.key) {
                    Key.DirectionDown -> 1
                    Key.DirectionUp -> -1
                    Key.DirectionLeft, Key.DirectionRight -> return@onKeyEvent modal
                    else -> return@onKeyEvent false
                }
                val can = if (dir > 0) st.canScrollForward else st.canScrollBackward
                if (!can) return@onKeyEvent modal
                if (ev.type == KeyEventType.KeyDown) scope.launch { st.animateScrollBy(dir * step) }
                true
            }
            .focusProperties { canFocus = modal || st.maxValue > 0 }
            .focusable()
            .clip(shape)
            .background(if (focused) Color(0x22FFFFFF) else Color.Transparent)
            .border(2.dp, if (focused) Color.White else Color.Transparent, shape),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(st).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
        if (st.canScrollForward && st.maxValue != Int.MAX_VALUE) {
            Text("▼", color = if (focused) TextMain else TextDim, fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
    }
}

object DetailScreens {

    @Composable
    private fun Backdrop(url: String?) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).size(960, 540).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.32f),
            )
        }
        // sfumatura: a sinistra, dove sta il testo, lo sfondo resta scuro e leggibile
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Bg, Bg.copy(alpha = 0.82f), Bg.copy(alpha = 0.45f)))))
    }

    /** Stelle (su 5) e voto (su 10), seguiti dalle etichette brevi: anno, durata, qualità… */
    @Composable
    private fun RatingRow(rating: Double?, chips: List<String>) {
        if (rating == null && chips.isEmpty()) return
        Row(
            Modifier.noFocus().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (rating != null) {
                val full = (rating / 2).roundToInt().coerceIn(0, 5)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("★".repeat(full), color = Gold, fontSize = 18.sp)
                    Text("★".repeat(5 - full), color = Color(0x44FFFFFF), fontSize = 18.sp)
                    Text("  " + "%.1f".format(rating), color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("/10", color = TextDim, fontSize = 12.sp)
                }
            }
            chips.forEach {
                Text(
                    it, color = TextMain, fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }

    @Composable
    private fun InfoLine(label: Int, value: String?) {
        if (value.isNullOrBlank()) return
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(label), color = TextDim, fontSize = 13.sp, modifier = Modifier.width(if (isCompact()) 96.dp else 130.dp))
            Text(value, color = TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
    }

    /** Trama e tutte le informazioni disponibili, una per riga. */
    @Composable
    private fun InfoBlock(name: String, plot: String?, genre: String?, cast: String?, duration: String?, x: TitleExtra) {
        if (plot != null) Text(plot, color = TextMain, fontSize = 15.sp, lineHeight = 21.sp)
        InfoLine(R.string.info_original, x.original?.takeIf { !it.equals(name, true) })
        InfoLine(R.string.info_genre, genre)
        InfoLine(R.string.info_director, x.director)
        InfoLine(R.string.info_cast, cast)
        InfoLine(R.string.info_country, x.country)
        InfoLine(R.string.info_released, x.released)
        InfoLine(R.string.info_duration, duration)
        InfoLine(R.string.info_age, x.age)
        InfoLine(R.string.info_video, x.quality)
        InfoLine(R.string.audio, x.audio)
    }

    private val dayFmt get() = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)

    /** Barra sottile con la parte già vista. */
    @Composable
    fun ProgressLine(frac: Float, modifier: Modifier = Modifier) {
        Box(modifier.height(3.dp).background(Color(0x44FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(Accent))
        }
    }

    /** "Visto il …" oppure "Fermo a … di … (..%) · data": tutto quello che si sa della visione di un titolo. */
    @Composable
    private fun WatchInfo(w: com.lumen.app.data.Watch?) {
        if (w == null || (!w.done && w.pos < 30_000)) return
        val day = if (w.time > 0) dayFmt.format(java.util.Date(w.time)) else ""
        if (w.pos >= 30_000 && w.dur > 0) {
            Text(
                (if (w.done) stringResource(R.string.watch_done) + "  ·  " else "") +
                    stringResource(R.string.watch_at, fmtClock(w.pos), fmtClock(w.dur), (w.frac * 100).toInt()) + "  ·  " + day,
                color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            ProgressLine(w.frac, Modifier.fillMaxWidth(0.6f))
        } else {
            Text(stringResource(R.string.watch_done_on, day), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    private fun trailerIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Il trailer si apre con YouTube o col browser: il pulsante compare solo se il dispositivo ne ha uno. */
    private fun canOpen(ctx: Context, url: String?): Boolean =
        url != null && runCatching { trailerIntent(url).resolveActivity(ctx.packageManager) != null }.getOrDefault(false)

    private fun ratingOf(x: TitleExtra?, listed: String?): Double? =
        x?.rating ?: listed?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0.0 && it <= 10.0 }

    @Composable
    fun Movie(vm: AppViewModel, m: com.lumen.app.data.Movie) {
        var info by remember { mutableStateOf<MovieInfo?>(null) }
        LaunchedEffect(m.id) { info = try { vm.repo.movieInfo(m.id) } catch (e: Exception) { null } }
        val playFr = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { playFr.requestFocus() } }
        val key = "M${m.id}"
        val resume = vm.store.resume(key)
        val compact = isCompact()
        val ctx = LocalContext.current
        val x = info?.extra ?: TitleExtra()
        val duration = x.runtime ?: info?.duration
        val trailer = x.trailer?.takeIf { canOpen(ctx, it) }

        Box(Modifier.fillMaxSize().background(Bg)) {
            Backdrop(info?.backdrop)
            Row(Modifier.fillMaxSize().padding(if (compact) 16.dp else 40.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp)) {
                Poster(m.poster, Modifier.width(if (compact) 110.dp else 200.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
                    Text(m.name, color = TextMain, fontSize = if (compact) 20.sp else 28.sp, fontWeight = FontWeight.Bold, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    RatingRow(ratingOf(info?.extra, m.rating), listOfNotNull(info?.year, duration, x.quality?.substringBefore(" · "), x.age))
                    WatchInfo(vm.store.watch(key))
                    // i tasti stanno subito sotto il titolo: trama e informazioni scorrono senza nasconderli
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(if (resume > 30_000) stringResource(R.string.btn_resume_at, fmtClock(resume)) else stringResource(R.string.btn_play), onClick = { vm.playMovie(m) }, primary = true, fr = playFr, small = compact)
                        if (resume > 30_000) Button(stringResource(R.string.btn_from_start), onClick = { vm.store.clearResume(key); vm.playMovie(m) }, small = compact)
                        Button(if (key in vm.favs) stringResource(R.string.btn_in_favs) else stringResource(R.string.btn_add_fav), onClick = { vm.toggleFav(key) }, small = compact)
                        if (trailer != null) Button(stringResource(R.string.btn_trailer), onClick = { runCatching { ctx.startActivity(trailerIntent(trailer)) } }, small = compact)
                    }
                    DpadScroll(Modifier.weight(1f).fillMaxWidth()) {
                        InfoBlock(m.name, info?.plot, info?.genre, info?.cast, duration, x)
                    }
                }
            }
        }
    }

    @Composable
    fun Series(vm: AppViewModel, s: com.lumen.app.data.Series) {
        var detail by remember { mutableStateOf<SeriesDetail?>(null) }
        var failed by remember { mutableStateOf(false) }
        var season by remember { mutableIntStateOf(-1) }
        LaunchedEffect(s.id) {
            try {
                val d = vm.repo.seriesDetail(s.id)
                detail = d
                season = d.seasons.keys.firstOrNull() ?: -1
            } catch (e: Exception) {
                failed = true
            }
        }
        val key = "S${s.id}"
        val firstFr = remember { FocusRequester() }
        val resumeFr = remember { FocusRequester() }
        // da dove riprendere: l'ultimo episodio guardato, oppure il successivo se quello è finito
        val lastSeen = remember(s.id) { vm.store.seriesLast(s.id.toString()) }
        val all = remember(detail) { detail?.seasons?.values?.flatten().orEmpty() }
        val target = remember(all) {
            val i = all.indexOfFirst { "E${it.id}" == lastSeen?.episodeKey }
            if (i < 0) -1 else if (vm.store.watch("E${all[i].id}")?.let { it.done && it.pos < 30_000 } == true) (i + 1).takeIf { it < all.size } ?: -1 else i
        }
        LaunchedEffect(detail) {
            if (detail == null) return@LaunchedEffect
            if (target >= 0) {
                season = all[target].season
                repeat(5) {
                    if (runCatching { resumeFr.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                    kotlinx.coroutines.delay(60)
                }
            }
            runCatching { firstFr.requestFocus() }
        }
        val epState = rememberLazyListState()

        val compact = isCompact()
        val ctx = LocalContext.current
        val x = detail?.extra ?: TitleExtra()
        val plot = detail?.plot ?: s.plot
        val trailer = x.trailer?.takeIf { canOpen(ctx, it) }
        var infoOpen by remember { mutableStateOf(false) }
        val infoBtnFr = remember { FocusRequester() }
        val infoFr = remember { FocusRequester() }
        val rating = ratingOf(detail?.extra, s.rating)
        val seasonCount = detail?.seasons?.size ?: 0
        val seenCount = remember(all) { all.count { vm.store.watch("E${it.id}")?.done == true } }
        val chips = listOfNotNull(
            detail?.year, x.runtime, x.age, if (seasonCount > 1) stringResource(R.string.info_seasons, seasonCount) else null,
            if (seenCount > 0 || lastSeen != null) stringResource(R.string.watch_count, seenCount, all.size).takeIf { all.isNotEmpty() } else null,
        )

        Box(Modifier.fillMaxSize().background(Bg)) {
            Backdrop(detail?.backdrop)
            Row(
                Modifier.fillMaxSize().focusProperties { if (infoOpen) canFocus = false }.padding(if (compact) 16.dp else 40.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp),
            ) {
                // su schermi bassi la colonna scorre: i pulsanti restano sempre raggiungibili
                Column(Modifier.width(if (compact) 120.dp else 190.dp).noFocus().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Poster(s.cover, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
                    Button(if (key in vm.favs) stringResource(R.string.btn_fav_short) else stringResource(R.string.btn_add_fav), onClick = { vm.toggleFav(key) }, modifier = Modifier.fillMaxWidth(), small = true)
                    Button(stringResource(R.string.btn_info), onClick = { infoOpen = true }, modifier = Modifier.fillMaxWidth(), small = true, fr = infoBtnFr)
                    if (trailer != null) Button(stringResource(R.string.btn_trailer), onClick = { runCatching { ctx.startActivity(trailerIntent(trailer)) } }, modifier = Modifier.fillMaxWidth(), small = true)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                    Text(s.name, color = TextMain, fontSize = if (compact) 20.sp else 28.sp, fontWeight = FontWeight.Bold, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                    RatingRow(rating, chips + listOfNotNull(detail?.genre))
                    plot?.let { Text(it, color = TextMain, fontSize = 13.sp, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis) }

                    val d = detail
                    when {
                        failed -> CenterMessage(stringResource(R.string.eps_failed))
                        d == null -> Loading()
                        d.seasons.isEmpty() -> CenterMessage(stringResource(R.string.eps_none))
                        else -> {
                            val seasons = d.seasons.keys.toList()
                            if (target >= 0) {
                                val te = all[target]
                                val tw = vm.store.watch("E${te.id}")
                                val code = "S${te.season}E${te.num}"
                                Button(
                                    if (tw != null && tw.pos >= 30_000) stringResource(R.string.btn_resume_ep, code, fmtClock(tw.pos)) else stringResource(R.string.btn_next_ep, code),
                                    onClick = { vm.playEpisodes(s, all, target) }, primary = true, small = true, fr = resumeFr,
                                )
                            }
                            LazyRow(Modifier.noFocus(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                items(seasons) { n ->
                                    TvBox(
                                        onClick = { season = n },
                                        onFocus = { season = n },
                                        focusRequester = if (n == seasons.first()) firstFr else null,
                                        selected = n == season,
                                    ) { f ->
                                        Text(
                                            stringResource(R.string.season_n, n),
                                            color = if (f || n == season) TextMain else TextDim,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        )
                                    }
                                }
                            }
                            val eps = d.seasons[season].orEmpty()
                            // l'elenco si apre sull'episodio da cui riprendere
                            LaunchedEffect(season, eps.size) {
                                val i = if (target >= 0) eps.indexOfFirst { it.id == all[target].id } else -1
                                if (i > 0) epState.scrollToItem(i - 1) else epState.scrollToItem(0)
                            }
                            LazyColumn(Modifier.noFocus().fillMaxSize(), state = epState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(eps, key = { _, e -> e.id }) { _, e ->
                                    val w = vm.store.watch("E${e.id}")
                                    val going = w != null && w.pos >= 30_000
                                    TvBox(onClick = { vm.playEpisodes(s, all, all.indexOf(e)) }, modifier = Modifier.fillMaxWidth(), focusScale = 1.01f) { f ->
                                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "${e.num}. ${e.title}",
                                                    color = if (w?.done == true && !going && !f) TextDim else TextMain, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                // stato: in corso (posizione / durata), visto, e quando
                                                val state = when {
                                                    going && w != null -> "▶ ${fmtClock(w.pos)} / ${fmtClock(w.dur)}"
                                                    w?.done == true -> stringResource(R.string.watch_done)
                                                    else -> null
                                                }
                                                if (state != null && w != null) {
                                                    Text(
                                                        state + (if (w.time > 0) "  ·  " + dayFmt.format(java.util.Date(w.time)) else ""),
                                                        color = if (going) Accent else TextDim, fontSize = 12.sp, maxLines = 1,
                                                        modifier = Modifier.padding(start = 10.dp),
                                                    )
                                                }
                                            }
                                            val sub = listOfNotNull(e.duration, e.plot).joinToString("  •  ")
                                            if (sub.isNotEmpty()) Text(sub, color = if (f) TextMain else TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (going && w != null) ProgressLine(w.frac, Modifier.fillMaxWidth().padding(top = 4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // trama completa e informazioni: riquadro a tutto schermo, ▲ ▼ scorrono, Indietro chiude
            if (infoOpen) {
                LaunchedEffect(Unit) { runCatching { infoFr.requestFocus() } }
                BackHandler {
                    infoOpen = false
                    runCatching { infoBtnFr.requestFocus() }
                }
                Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.fillMaxWidth(if (compact) 0.92f else 0.78f).fillMaxHeight(0.9f)
                            .clip(RoundedCornerShape(16.dp)).background(Surface).padding(if (compact) 14.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(s.name, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        RatingRow(rating, chips)
                        DpadScroll(Modifier.weight(1f).fillMaxWidth(), fr = infoFr, modal = true) {
                            InfoBlock(s.name, plot, detail?.genre, detail?.cast, x.runtime, x)
                        }
                        Text(stringResource(R.string.info_scroll_hint) + "    " + stringResource(R.string.back_close), color = TextDim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
