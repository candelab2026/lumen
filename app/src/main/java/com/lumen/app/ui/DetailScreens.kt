package com.lumen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
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
import com.lumen.app.data.MovieInfo
import com.lumen.app.data.SeriesDetail

object DetailScreens {

    @Composable
    private fun Backdrop(url: String?) {
        if (url == null) return
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).size(960, 540).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.18f),
        )
    }

    @Composable
    fun Movie(vm: AppViewModel, m: com.lumen.app.data.Movie) {
        var info by remember { mutableStateOf<MovieInfo?>(null) }
        LaunchedEffect(m.id) { info = try { vm.repo.movieInfo(m.id) } catch (e: Exception) { null } }
        val playFr = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { playFr.requestFocus() } }
        val key = "M${m.id}"
        val resume = vm.store.resume(key)

        Box(Modifier.fillMaxSize().background(Bg)) {
            Backdrop(info?.backdrop)
            Row(Modifier.fillMaxSize().padding(40.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Poster(m.poster, Modifier.width(190.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(m.name, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(info?.year, info?.duration, info?.genre, m.rating?.let { "★ $it" }).joinToString("  •  ")
                    if (meta.isNotEmpty()) Text(meta, color = TextDim, fontSize = 14.sp)
                    // i tasti stanno subito sotto il titolo: la trama lunga scorre (anche col dito) senza nasconderli
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(if (resume > 30_000) stringResource(R.string.btn_resume_at, fmtClock(resume)) else stringResource(R.string.btn_play), onClick = { vm.playMovie(m) }, primary = true, fr = playFr)
                        if (resume > 30_000) Button(stringResource(R.string.btn_from_start), onClick = { vm.store.clearResume(key); vm.playMovie(m) })
                        Button(if (key in vm.favs) stringResource(R.string.btn_in_favs) else stringResource(R.string.btn_add_fav), onClick = { vm.toggleFav(key) })
                    }
                    Column(Modifier.weight(1f).noFocus().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        info?.plot?.let { Text(it, color = TextMain, fontSize = 14.sp) }
                        info?.cast?.let { Text(stringResource(R.string.cast, it), color = TextDim, fontSize = 13.sp) }
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
        LaunchedEffect(detail) { if (detail != null) runCatching { firstFr.requestFocus() } }

        Box(Modifier.fillMaxSize().background(Bg)) {
            Backdrop(detail?.backdrop)
            Row(Modifier.fillMaxSize().padding(40.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.width(190.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Poster(s.cover, Modifier.fillMaxWidth())
                    Button(if (key in vm.favs) stringResource(R.string.btn_fav_short) else stringResource(R.string.btn_add_fav), onClick = { vm.toggleFav(key) }, modifier = Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.name, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(detail?.year, detail?.genre, s.rating?.let { "★ $it" }).joinToString("  •  ")
                    if (meta.isNotEmpty()) Text(meta, color = TextDim, fontSize = 14.sp)
                    (detail?.plot ?: s.plot)?.let { Text(it, color = TextMain, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }

                    val d = detail
                    when {
                        failed -> CenterMessage(stringResource(R.string.eps_failed))
                        d == null -> Loading()
                        d.seasons.isEmpty() -> CenterMessage(stringResource(R.string.eps_none))
                        else -> {
                            val seasons = d.seasons.keys.toList()
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
                            LazyColumn(Modifier.noFocus().fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(eps, key = { _, e -> e.id }) { i, e ->
                                    val watched = vm.store.resume("E${e.id}") > 30_000
                                    TvBox(onClick = { vm.playEpisodes(s, eps, i) }, modifier = Modifier.fillMaxWidth(), focusScale = 1.01f) { f ->
                                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                            Text(
                                                "${e.num}. ${e.title}" + (if (watched) stringResource(R.string.ep_in_progress) else ""),
                                                color = TextMain, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                            val sub = listOfNotNull(e.duration, e.plot).joinToString("  •  ")
                                            if (sub.isNotEmpty()) Text(sub, color = if (f) TextMain else TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
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
