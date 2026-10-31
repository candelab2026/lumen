@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.lumen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import com.lumen.app.AppStats
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumen.app.AppViewModel
import androidx.compose.ui.res.stringResource
import com.lumen.app.R
import com.lumen.app.BuildConfig
import com.lumen.app.ListState
import com.lumen.app.Screen
import com.lumen.app.Status
import com.lumen.app.data.Channel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TABS = listOf(R.string.tab_foryou, R.string.tab_live, R.string.tab_movies, R.string.tab_series, R.string.tab_guide, R.string.tab_settings)

@Composable
fun HomeScreen(vm: AppViewModel, active: Boolean) {
    val tabFrs = remember { List(TABS.size) { FocusRequester() } }
    val contentFr = remember { FocusRequester() }
    // all'avvio il layout può non essere pronto: si riprova finché il focus non è davvero sulla scheda
    LaunchedEffect(Unit) {
        repeat(20) {
            val r = runCatching { tabFrs[vm.tab].requestFocus() }
            if (r.getOrDefault(false)) return@LaunchedEffect
            delay(100)
        }
    }
    LaunchedEffect(vm.tab) {
        delay(300)
        vm.ensureLoaded(vm.tab)
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .focusProperties { if (!active) canFocus = false }
            .padding(horizontal = 40.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Logo(size = 22)
            Spacer(Modifier.width(16.dp))
            // le schede stanno in una riga scorrevole: se lo schermo è stretto non si comprimono mai
            Row(
                Modifier.weight(1f)
                    .focusProperties { enter = { tabFrs[vm.tab] } }
                    .focusGroup()
                    .noFocus().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
            TABS.forEachIndexed { i, labelId ->
                val label = stringResource(labelId)
                TvBox(
                    onClick = { vm.tab = i },
                    onFocus = { vm.tab = i },
                    focusRequester = tabFrs[i],
                    modifier = Modifier.focusProperties { down = contentFr },
                    selected = vm.tab == i,
                    focusScale = 1.06f,
                ) { f ->
                    val c = if (f || vm.tab == i) TextMain else TextDim
                    Row(
                        Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        TabIcon(i, c, 20.dp)
                        Text(
                            label,
                            color = c,
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (vm.tab == i) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxSize().focusRequester(contentFr).focusGroup()) {
            when (vm.tab) {
                0 -> ForYouTab(vm)
                1 -> LiveTab(vm, active)
                2 -> MovieTab(vm, active)
                3 -> SeriesTab(vm, active)
                4 -> GuideTab(vm)
                else -> SettingsTab(vm)
            }
        }
    }
    // scadenza vicina (30 giorni o meno): promemoria fisso e discreto in alto a destra
    val exp = vm.account?.expiry
    if (exp != null && (exp - System.currentTimeMillis()) / 86_400_000L <= 30) {
        Text(
            stringResource(R.string.expires_on, java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(Date(exp))),
            color = Gold, fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 40.dp),
        )
    }
    }
}

// ---------- parti comuni ----------

@Composable
private fun ErrorBox(text: String?, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text ?: stringResource(R.string.error_generic), color = TextDim, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Button(stringResource(R.string.retry), onClick = onRetry, primary = true)
    }
}

@Composable
private fun CategoryList(entries: List<Pair<String, String>>, selected: String, onFocusCat: (String) -> Unit, onPick: (String) -> Unit, onRight: () -> Unit) {
    val frs = remember { mutableMapOf<String, FocusRequester>() }
    LazyColumn(
        Modifier.fillMaxHeight()
            .onPreviewKeyEvent { ev ->
                if (ev.key == Key.DirectionRight && ev.type == KeyEventType.KeyDown) { onRight(); true } else false
            }
            .focusProperties { enter = { frs[selected] ?: FocusRequester.Default } }.focusGroup().noFocus(),
        verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(entries, key = { it.first }) { (id, label) ->
            val fr = remember { FocusRequester() }
            DisposableEffect(id) {
                frs[id] = fr
                onDispose { if (frs[id] === fr) frs.remove(id) }
            }
            TvBox(onClick = { onPick(id) }, onFocus = { onFocusCat(id) }, focusRequester = fr, selected = id == selected, modifier = Modifier.fillMaxWidth()) { f ->
                Text(
                    label,
                    color = if (f || id == selected) TextMain else TextDim,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

fun fmtTime(t: Long): String = timeFmt.format(Date(t))

// ---------- Live ----------

/** Canali della categoria scelta in TV Live ("fav" preferiti, "rec" recenti, "*" tutti). */
private fun channelsFor(vm: AppViewModel, st: ListState<Channel>): List<Channel> = when (vm.liveCat) {
    "*" -> st.items
    "fav" -> st.items.filter { "L${it.id}" in vm.favs }
    "rec" -> {
        val byId = st.items.associateBy { it.id }
        vm.recents.mapNotNull { byId[it] }
    }
    else -> st.byCat[vm.liveCat] ?: emptyList()
}

@Composable
private fun LiveTab(vm: AppViewModel, active: Boolean) {
    val st = vm.live
    when (st.status) {
        Status.Idle, Status.Loading -> Loading()
        Status.Error -> ErrorBox(st.error) { vm.ensureLoaded(1) }
        Status.Ready -> LiveContent(vm, st, active)
    }
}

@Composable
private fun LiveContent(vm: AppViewModel, st: ListState<Channel>, active: Boolean) {
    var query by remember { mutableStateOf("") }
    val statCtx = LocalContext.current
    LaunchedEffect(query.isNotBlank()) { if (query.isNotBlank()) AppStats.event(statCtx, "search_used") }
    val favs = vm.favs
    val lFav = stringResource(R.string.cat_favorites)
    val lRec = stringResource(R.string.cat_recent)
    val lAll = stringResource(R.string.cat_all_channels)
    val entries = remember(st, lFav) {
        listOf("fav" to lFav, "rec" to lRec, "*" to lAll) + st.cats.map { it.id to it.name }
    }
    val shown = remember(st, vm.liveCat, query, favs, vm.recents) {
        val q = query.trim()
        if (q.isNotEmpty()) st.items.filter { it.name.contains(q, true) } else channelsFor(vm, st)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jumpTick by remember { mutableIntStateOf(0) }

    // salto rapido nelle liste lunghe: avanti/indietro veloce (o pagina su/giù) = 10 canali
    fun jump(delta: Int) {
        if (shown.isEmpty()) return
        val cur = shown.indexOfFirst { "L${it.id}" == vm.lastFocus }.coerceAtLeast(0)
        val target = (cur + delta).coerceIn(0, shown.lastIndex)
        vm.lastFocus = "L${shown[target].id}"
        scope.launch {
            listState.scrollToItem(target)
            jumpTick++
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(200.dp)) {
            TvTextField(query, { query = it }, stringResource(R.string.search_channel), Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CategoryList(entries, vm.liveCat, onFocusCat = { vm.liveCat = it }, onPick = {
                vm.liveCat = it
                query = ""
            }, onRight = {
                if (shown.isNotEmpty()) {
                    val idx = shown.indexOfFirst { "L${it.id}" == vm.lastFocus }.coerceAtLeast(0)
                    vm.lastFocus = "L${shown[idx].id}"
                    scope.launch {
                        if (listState.layoutInfo.visibleItemsInfo.none { it.index == idx }) listState.scrollToItem(idx)
                        jumpTick++
                    }
                }
            })
        }
        Box(
            Modifier.weight(1f).onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.MediaFastForward, Key.PageDown, Key.ChannelDown -> { jump(10); true }
                    Key.MediaRewind, Key.PageUp, Key.ChannelUp -> { jump(-10); true }
                    else -> false
                }
            },
        ) {
            if (shown.isEmpty()) {
                CenterMessage(
                    when {
                        query.isNotBlank() -> stringResource(R.string.empty_no_channel_found)
                        vm.liveCat == "fav" -> stringResource(R.string.empty_no_fav_channel)
                        vm.liveCat == "rec" -> stringResource(R.string.empty_no_recent)
                        else -> stringResource(R.string.empty_no_channels)
                    },
                )
            } else {
                LazyColumn(
                    Modifier.noFocus().fillMaxSize(), state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(shown, key = { _, c -> c.id }) { i, ch ->
                        ChannelRow(vm, ch, shown, i, active, jumpTick)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(vm: AppViewModel, ch: Channel, list: List<Channel>, index: Int, active: Boolean, jumpTick: Int) {
    val key = "L${ch.id}"
    val fr = remember { FocusRequester() }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(active, jumpTick) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (active && vm.lastFocus == key) runCatching { fr.requestFocus() }
    }
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val ver = vm.epgVersion
    val (now, _) = vm.epg.nowNext(ch.epgId)

    TvBox(
        onClick = { vm.lastFocus = key; vm.playLive(list, index) },
        onLongClick = { vm.toggleFav(key) },
        onFocus = { vm.lastFocus = key },
        focusRequester = fr,
        modifier = Modifier.fillMaxWidth(),
        focusScale = 1.015f,
    ) { f ->
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(ch.num.toString(), color = TextDim, fontSize = 14.sp, modifier = Modifier.width(38.dp))
            Box(
                Modifier.size(width = 112.dp, height = 64.dp).clip(RoundedCornerShape(8.dp)).background(Bg),
                contentAlignment = Alignment.Center,
            ) {
                if (ch.logo != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(ch.logo).size(336, 192).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                } else {
                    Text(
                        ch.name.take(3).uppercase(), color = TextDim, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(ch.name, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (now != null) {
                    Text(
                        "${fmtTime(now.start)}-${fmtTime(now.stop)}  ${now.title}",
                        color = if (f) TextMain else TextDim,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val frac = ((System.currentTimeMillis() - now.start).toFloat() / (now.stop - now.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                    Box(Modifier.padding(top = 3.dp).fillMaxWidth().height(2.dp).background(Bg)) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
                    }
                }
            }
            if ("L${ch.id}" in vm.favs) Text("★", color = Gold, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

// ---------- Film / Serie ----------

@Composable
private fun MovieTab(vm: AppViewModel, active: Boolean) {
    val st = vm.vod
    when (st.status) {
        Status.Idle, Status.Loading -> Loading()
        Status.Error -> ErrorBox(st.error) { vm.ensureLoaded(2) }
        Status.Ready -> PosterSection(
            vm, st, active, "M", vm.vodCat, { vm.vodCat = it },
            id = { it.id }, name = { it.name }, image = { it.poster }, catId = { it.catId },
            onOpen = { vm.push(Screen.MovieDetail(it)) },
        )
    }
}

@Composable
private fun SeriesTab(vm: AppViewModel, active: Boolean) {
    val st = vm.series
    when (st.status) {
        Status.Idle, Status.Loading -> Loading()
        Status.Error -> ErrorBox(st.error) { vm.ensureLoaded(3) }
        Status.Ready -> PosterSection(
            vm, st, active, "S", vm.seriesCat, { vm.seriesCat = it },
            id = { it.id }, name = { it.name }, image = { it.cover }, catId = { it.catId },
            onOpen = { vm.push(Screen.SeriesDetail(it)) },
        )
    }
}

@Composable
private fun <T> PosterSection(
    vm: AppViewModel,
    st: ListState<T>,
    active: Boolean,
    prefix: String,
    selCat: String,
    setCat: (String) -> Unit,
    id: (T) -> Int,
    name: (T) -> String,
    image: (T) -> String?,
    catId: (T) -> String,
    onOpen: (T) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val statCtx = LocalContext.current
    LaunchedEffect(query.isNotBlank()) { if (query.isNotBlank()) AppStats.event(statCtx, "search_used") }
    val favs = vm.favs
    val sStart = stringResource(R.string.cat_started)
    val sFav = stringResource(R.string.cat_favorites)
    val sAll = stringResource(R.string.cat_all)
    val entries = remember(st, sFav) {
        listOf("start" to sStart, "fav" to sFav, "*" to sAll) + st.cats.map { it.id to it.name }
    }
    // titoli già iniziati, dal più recente: i film per chiave, le serie tramite l'episodio in corso
    val started = vm.cont.filter { vm.store.resume(it.key) > 30_000 }.mapNotNull { c ->
        if (prefix == "M") c.key.takeIf { c.kind == "movie" } else "S${c.parent}".takeIf { c.kind == "series" && c.parent.isNotEmpty() }
    }.distinct()
    val shown = remember(st, selCat, query, favs, started) {
        val q = query.trim()
        if (q.isNotEmpty()) {
            st.items.filter { name(it).contains(q, true) }
        } else when (selCat) {
            "start" -> {
                val byKey = st.items.associateBy { "$prefix${id(it)}" }
                started.mapNotNull { byKey[it] }
            }
            "*" -> st.items
            "fav" -> st.items.filter { "$prefix${id(it)}" in favs }
            else -> st.byCat[selCat] ?: emptyList()
        }
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var jumpTick by remember { mutableIntStateOf(0) }

    // salto rapido: avanti/indietro veloce (o pagina su/giù) = 3 righe di locandine
    fun jump(delta: Int) {
        if (shown.isEmpty()) return
        val cur = shown.indexOfFirst { "$prefix${id(it)}" == vm.lastFocus }.coerceAtLeast(0)
        val target = (cur + delta).coerceIn(0, shown.lastIndex)
        vm.lastFocus = "$prefix${id(shown[target])}"
        scope.launch {
            gridState.scrollToItem(target)
            jumpTick++
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(200.dp)) {
            TvTextField(query, { query = it }, stringResource(R.string.search), Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CategoryList(entries, selCat, onFocusCat = setCat, onPick = {
                setCat(it)
                query = ""
            }, onRight = {
                if (shown.isNotEmpty()) {
                    val idx = shown.indexOfFirst { "$prefix${id(it)}" == vm.lastFocus }.coerceAtLeast(0)
                    vm.lastFocus = "$prefix${id(shown[idx])}"
                    scope.launch {
                        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == idx }) gridState.scrollToItem(idx)
                        jumpTick++
                    }
                }
            })
        }
        Box(
            Modifier.weight(1f).onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.MediaFastForward, Key.PageDown, Key.ChannelDown -> { jump(15); true }
                    Key.MediaRewind, Key.PageUp, Key.ChannelUp -> { jump(-15); true }
                    else -> false
                }
            },
        ) {
            if (shown.isEmpty()) {
                CenterMessage(
                    when {
                        query.isNotBlank() -> stringResource(R.string.empty_no_results)
                        selCat == "start" -> stringResource(R.string.empty_nothing_started)
                        selCat == "fav" -> stringResource(R.string.empty_no_fav_title)
                        else -> stringResource(R.string.empty_no_content)
                    },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    state = gridState,
                    modifier = Modifier.noFocus().fillMaxSize(),
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { id(it) }) { item ->
                        val key = "$prefix${id(item)}"
                        val fr = remember { FocusRequester() }
                        val first = remember { booleanArrayOf(true) }
                        LaunchedEffect(active, jumpTick) {
                            if (first[0]) { first[0] = false; return@LaunchedEffect }
                            if (active && vm.lastFocus == key) runCatching { fr.requestFocus() }
                        }
                        TvBox(
                            onClick = { vm.lastFocus = key; onOpen(item) },
                            onLongClick = { vm.toggleFav(key) },
                            onFocus = { vm.lastFocus = key },
                            focusRequester = fr,
                        ) { f ->
                            Column {
                                Box {
                                    Poster(image(item), Modifier.fillMaxWidth())
                                    if (key in vm.favs) {
                                        Text("★", color = Gold, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                                    }
                                }
                                Text(
                                    name(item),
                                    color = if (f) TextMain else TextDim,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    minLines = 2,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Per te ----------

@Composable
private fun ForYouTab(vm: AppViewModel) {
    val byId = remember(vm.live) { vm.live.items.associateBy { it.id } }
    val recentCh = vm.recents.mapNotNull { byId[it] }
    val cont = vm.cont.filter { vm.store.resume(it.key) > 30_000 }
    val days = vm.account?.expiry?.let { (it - System.currentTimeMillis()) / 86_400_000L }
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().noFocus().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (days != null && days <= 7) {
            Text(
                if (days < 0) stringResource(R.string.sub_expired) else if (days == 0L) stringResource(R.string.sub_today) else stringResource(R.string.sub_days, days.toInt()),
                color = Gold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        if (recentCh.isNotEmpty()) {
            Text(stringResource(R.string.home_recent_channels), color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            LazyRow(Modifier.noFocus(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                itemsIndexed(recentCh, key = { _, c -> c.id }) { i, ch ->
                    TvBox(onClick = { vm.playLive(recentCh, i) }, modifier = Modifier.width(150.dp)) { f ->
                        Column {
                            Box(Modifier.fillMaxWidth().height(84.dp).background(Bg), contentAlignment = Alignment.Center) {
                                if (ch.logo != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(ctx).data(ch.logo).size(336, 192).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                    )
                                } else {
                                    Text(ch.name.take(3).uppercase(), color = TextDim, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                ch.name,
                                color = if (f) TextMain else TextDim,
                                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        if (cont.isNotEmpty()) {
            Text(stringResource(R.string.home_continue), color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            LazyRow(Modifier.noFocus(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                items(cont, key = { it.key }) { c ->
                    TvBox(
                        onClick = { vm.playContinue(c) },
                        onLongClick = { vm.dropContinue(c.key) },
                        modifier = Modifier.width(130.dp),
                    ) { f ->
                        Column {
                            Poster(c.poster, Modifier.fillMaxWidth())
                            Text(
                                c.title,
                                color = if (f) TextMain else TextDim,
                                fontSize = 12.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Text(
                                stringResource(R.string.resume_from, (vm.store.resume(c.key) / 60_000).toInt()),
                                color = Accent, fontSize = 11.sp,
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
            }
            Text(stringResource(R.string.home_hint_remove), color = TextDim, fontSize = 11.sp)
        }
        if (cont.isEmpty() && recentCh.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.home_empty),
                color = TextDim, fontSize = 16.sp,
            )
        }
    }
}

// ---------- Guida TV a griglia ----------

private const val GUIDE_WINDOW = 3 * 3_600_000L
private const val HOUR = 3_600_000L

private fun floorHalfHour(t: Long) = t - t % (HOUR / 2)

@Composable
private fun GuideTab(vm: AppViewModel) {
    val st = vm.live
    val statCtx = LocalContext.current
    LaunchedEffect(Unit) { AppStats.event(statCtx, "epg_opened") }
    when (st.status) {
        Status.Idle, Status.Loading -> Loading()
        Status.Error -> ErrorBox(st.error) { vm.ensureLoaded(4) }
        Status.Ready -> GuideContent(vm, st)
    }
}

@Composable
private fun GuideContent(vm: AppViewModel, st: ListState<Channel>) {
    var start by remember { mutableLongStateOf(floorHalfHour(System.currentTimeMillis())) }
    val shown = remember(st, vm.liveCat, vm.favs, vm.recents) { channelsFor(vm, st) }
    val catName = when (vm.liveCat) {
        "*" -> stringResource(R.string.cat_all_channels)
        "fav" -> stringResource(R.string.name_favorites)
        "rec" -> stringResource(R.string.cat_recent)
        else -> st.cats.firstOrNull { it.id == vm.liveCat }?.name ?: ""
    }
    val dayFmt = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }

    Column(
        Modifier.fillMaxSize().onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val now = floorHalfHour(System.currentTimeMillis())
            when (ev.key) {
                Key.DirectionRight -> { start = (start + HOUR).coerceAtMost(now + 24 * HOUR); true }
                Key.DirectionLeft -> { start = (start - HOUR).coerceAtLeast(now - 2 * HOUR); true }
                else -> false
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$catName  ·  ${dayFmt.format(Date(start))}",
                color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(250.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.weight(1f)) {
                for (i in 0 until 6) {
                    Text(fmtTime(start + i * HOUR / 2), color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        Text(
            stringResource(R.string.guide_nav_hint),
            color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp),
        )
        if (shown.isEmpty()) {
            CenterMessage(stringResource(R.string.guide_no_channels))
        } else {
            LazyColumn(Modifier.noFocus().fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(shown, key = { _, c -> c.id }) { i, ch ->
                    GuideRow(vm, ch, shown, i, start)
                }
            }
        }
    }
}

@Composable
private fun GuideRow(vm: AppViewModel, ch: Channel, list: List<Channel>, index: Int, start: Long) {
    @Suppress("UNUSED_VARIABLE") val ver = vm.epgVersion
    val end = start + GUIDE_WINDOW
    val progs = remember(vm.epgVersion, ch.epgId, start) { vm.epg.between(ch.epgId, start, end) }
    val now = System.currentTimeMillis()

    TvBox(
        onClick = { vm.playLive(list, index) },
        modifier = Modifier.fillMaxWidth(),
        focusScale = 1.01f,
    ) { f ->
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${ch.num}  ${ch.name}",
                color = if (f) TextMain else TextDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(244.dp),
            )
            BoxWithConstraints(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(6.dp))) {
                val w = maxWidth
                if (progs.isEmpty()) {
                    Text(stringResource(R.string.guide_unavailable), color = TextDim, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
                } else {
                    Row(Modifier.fillMaxSize()) {
                        var cursor = start
                        for (p in progs) {
                            val s = maxOf(p.start, start)
                            val e = minOf(p.stop, end)
                            if (e <= s) continue
                            if (s > cursor) Spacer(Modifier.width(w * ((s - cursor).toFloat() / GUIDE_WINDOW)))
                            Box(
                                Modifier
                                    .width(w * ((e - s).toFloat() / GUIDE_WINDOW))
                                    .fillMaxHeight()
                                    .padding(end = 2.dp)
                                    .background(if (p.start <= now && p.stop > now) SurfaceSel else Bg),
                            ) {
                                Text(
                                    p.title, color = if (f) TextMain else TextDim, fontSize = 12.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                            cursor = e
                        }
                    }
                }
            }
        }
    }
}

// ---------- Impostazioni ----------

private val SCALE_NAMES = listOf(R.string.scale_normal, R.string.scale_large, R.string.scale_xlarge)

@Composable
private fun SettingsTab(vm: AppViewModel) {
    val acc = vm.account
    val dateFmt = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT) }
    Column(
        Modifier.fillMaxSize().noFocus().verticalScroll(rememberScrollState()).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.set_account), color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.set_server, vm.server), color = TextDim, fontSize = 15.sp)
        if (acc != null) {
            Text(stringResource(R.string.set_user, acc.username), color = TextDim, fontSize = 15.sp)
            Text(stringResource(R.string.set_status, acc.status), color = TextDim, fontSize = 15.sp)
            Text(
                stringResource(R.string.set_expiry, acc.expiry?.let { dateFmt.format(Date(it)) } ?: stringResource(R.string.set_unlimited)),
                color = TextDim, fontSize = 15.sp,
            )
            Text(stringResource(R.string.set_connections, acc.activeConnections, acc.maxConnections), color = TextDim, fontSize = 15.sp)
        } else {
            Text(stringResource(R.string.set_offline), color = Gold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        val yesNo = { b: Boolean -> if (b) R.string.yes else R.string.no }
        val gap = Arrangement.spacedBy(12.dp)
        val w = Modifier.weight(1f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Button(stringResource(R.string.set_profile, vm.profile + 1, AppViewModel.PROFILES), onClick = { vm.switchProfile() }, modifier = w)
            Button(stringResource(R.string.set_text, stringResource(SCALE_NAMES[vm.textScale])), onClick = { vm.cycleTextScale() }, modifier = w)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Button(stringResource(R.string.set_autostart, stringResource(yesNo(vm.autoStart))), onClick = { vm.toggleAutoStart() }, modifier = w)
            Button(stringResource(R.string.set_pubguide, stringResource(yesNo(vm.publicEpg))), onClick = { vm.togglePublicEpg() }, modifier = w)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Button(stringResource(R.string.set_refresh), onClick = { vm.refreshAll() }, modifier = w)
            Button(
                vm.newVersion?.let { stringResource(R.string.upd_available_btn, it.version) } ?: stringResource(R.string.upd_check_btn),
                onClick = { if (vm.newVersion != null) vm.installUpdate() else vm.checkAppUpdate(true) },
                primary = vm.newVersion != null,
                modifier = w,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = gap) {
            Button(stringResource(R.string.set_help), onClick = { vm.push(Screen.Help) }, modifier = w)
            Button(stringResource(R.string.set_logout), onClick = { vm.logout() }, modifier = w)
        }
        vm.updateStatus?.let { Text(it, color = Gold, fontSize = 14.sp) }
        Text(stringResource(R.string.set_profile_note), color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.set_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = TextDim, fontSize = 14.sp)
    }
}
