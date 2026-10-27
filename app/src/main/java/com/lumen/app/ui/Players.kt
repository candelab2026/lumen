@file:OptIn(UnstableApi::class)

package com.lumen.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.lumen.app.data.AccountInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lumen.app.AppViewModel
import androidx.compose.ui.res.stringResource
import com.lumen.app.R
import com.lumen.app.Config
import com.lumen.app.Screen
import com.lumen.app.data.Programme
import kotlinx.coroutines.delay

@Composable
private fun rememberPlayer(): ExoPlayer {
    val ctx = LocalContext.current
    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_500, 3_000).build())
            .build()
    }
    val activity = ctx as? ComponentActivity
    DisposableEffect(player) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) player.pause()
            if (e == Lifecycle.Event.ON_START) player.play()
        }
        activity?.lifecycle?.addObserver(obs)
        onDispose {
            activity?.lifecycle?.removeObserver(obs)
            player.release()
        }
    }
    return player
}

@Composable
private fun VideoSurface(player: ExoPlayer) {
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                isFocusable = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private val DIGITS = mapOf(
    Key.Zero to 0, Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4,
    Key.Five to 5, Key.Six to 6, Key.Seven to 7, Key.Eight to 8, Key.Nine to 9,
    Key.NumPad0 to 0, Key.NumPad1 to 1, Key.NumPad2 to 2, Key.NumPad3 to 3, Key.NumPad4 to 4,
    Key.NumPad5 to 5, Key.NumPad6 to 6, Key.NumPad7 to 7, Key.NumPad8 to 8, Key.NumPad9 to 9,
)

private val BottomShade = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))

/** Messaggio comprensibile al posto del codice di errore di ExoPlayer. */
private fun playbackErrorText(ctx: android.content.Context, e: PlaybackException, acc: AccountInfo?): String {
    val code = generateSequence<Throwable>(e) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()?.responseCode
    return when {
        code == 401 || code == 403 ->
            ctx.getString(R.string.err_denied, acc?.let { ctx.getString(R.string.err_denied_conn, it.activeConnections, it.maxConnections) } ?: "")
        code == 404 -> ctx.getString(R.string.err_404)
        e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            ctx.getString(R.string.err_malformed)
        code != null && code >= 500 -> ctx.getString(R.string.err_server, code)
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            ctx.getString(R.string.err_network)
        else -> ctx.getString(R.string.err_generic, e.errorCodeName)
    }
}

private fun nextSleep(min: Int) = when (min) { 0 -> 30; 30 -> 60; 60 -> 90; else -> 0 }

/** Allo scadere del timer la riproduzione si ferma e si torna alla schermata precedente. */
@Composable
private fun SleepTimer(minutes: Int, player: ExoPlayer, onFire: () -> Unit) {
    LaunchedEffect(minutes) {
        if (minutes > 0) {
            delay(minutes * 60_000L)
            player.pause()
            onFire()
        }
    }
}

@Composable
private fun OptionRow(text: String, onClick: () -> Unit, fr: FocusRequester? = null) {
    TvBox(onClick = onClick, focusRequester = fr, modifier = Modifier.fillMaxWidth(), focusScale = 1.01f) { f ->
        Text(
            text, color = if (f) TextMain else TextDim, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

/** Pannello laterale: timer di spegnimento, azioni del contesto, tracce audio e sottotitoli. */
@Composable
private fun OptionsPanel(
    player: ExoPlayer,
    sleepMin: Int,
    onSleep: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                tick++
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    val resCtx = LocalContext.current
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFr.requestFocus() } }

    @Suppress("UNUSED_VARIABLE") val t = tick
    val groups = player.currentTracks.groups
    val audio = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val text = groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val textOff = C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes

    fun label(g: Tracks.Group, i: Int, n: Int): String {
        val f = g.getTrackFormat(i)
        val lang = f.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage.takeIf { d -> d.isNotBlank() } }
        return f.label ?: lang ?: resCtx.getString(R.string.track_n, n)
    }

    fun select(g: Tracks.Group, i: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(g.type, false)
            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
            .build()
        tick++
    }

    Column(
        modifier.width(420.dp).fillMaxHeight().background(Color(0xEE0B0F1A)).padding(16.dp).noFocus().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.options), color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        OptionRow(stringResource(R.string.sleep_timer, if (sleepMin == 0) stringResource(R.string.sleep_off) else stringResource(R.string.sleep_min, sleepMin)), onSleep, firstFr)
        actions()

        Text(stringResource(R.string.audio), color = Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        var n = 0
        if (audio.isEmpty()) Text(stringResource(R.string.no_audio), color = TextDim, fontSize = 13.sp)
        audio.forEach { g ->
            for (i in 0 until g.length) {
                n++
                OptionRow(label(g, i, n) + if (g.isTrackSelected(i)) "   ✓" else "", { select(g, i) })
            }
        }

        Text(stringResource(R.string.subtitles), color = Accent, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        if (text.isEmpty()) {
            Text(stringResource(R.string.no_subs), color = TextDim, fontSize = 13.sp)
        } else {
            OptionRow(stringResource(R.string.subs_off) + if (textOff || text.none { it.isSelected }) "   ✓" else "", {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                tick++
            })
            var m = 0
            text.forEach { g ->
                for (i in 0 until g.length) {
                    m++
                    OptionRow(label(g, i, m) + if (!textOff && g.isTrackSelected(i)) "   ✓" else "", { select(g, i) })
                }
            }
        }
        Text(stringResource(R.string.back_close), color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

// ====================== LIVE ======================

@Composable
fun LivePlayerScreen(vm: AppViewModel, req: Screen.PlayLive) {
    val player = rememberPlayer()
    val ctx = LocalContext.current
    val channels = req.channels
    var index by remember { mutableIntStateOf(req.index.coerceIn(0, channels.lastIndex)) }
    val ch = channels[index]
    var ext by remember(ch.id) { mutableStateOf("m3u8") }
    var retry by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var panel by remember { mutableIntStateOf(0) } // 0 nessuno, 1 canali, 2 guida
    var typed by remember { mutableStateOf("") }
    var prevIndex by remember { mutableIntStateOf(-1) }
    var sleepMin by remember { mutableIntStateOf(0) }
    val fr = remember { FocusRequester() }

    fun goTo(i: Int) {
        if (i != index && i in channels.indices) {
            prevIndex = index
            index = i
        }
    }
    fun zapNext() = goTo((index + 1) % channels.size)
    fun zapPrev() = goTo((index - 1 + channels.size) % channels.size)
    fun toPrevious() { if (prevIndex >= 0) goTo(prevIndex) }
    SleepTimer(sleepMin, player) { vm.back() }

    LaunchedEffect(ch.id, ext, retry) {
        error = null
        buffering = true
        // chiude subito la connessione del canale precedente: molti server contano le connessioni attive
        player.stop()
        player.clearMediaItems()
        delay(600) // evita di martellare il server quando si fa zapping veloce
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(vm.repo.liveUrl(ch, ext))
                .setMimeType(if (ext == "m3u8") androidx.media3.common.MimeTypes.APPLICATION_M3U8 else androidx.media3.common.MimeTypes.VIDEO_MP2T)
                .build(),
        )
        player.prepare()
        player.playWhenReady = true
        vm.noteWatched(ch.id)
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            }

            override fun onPlayerError(e: PlaybackException) {
                if (ext == "m3u8") ext = "ts" else error = playbackErrorText(ctx, e, vm.account)
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    var failures by remember(ch.id) { mutableIntStateOf(0) }
    LaunchedEffect(error) {
        if (error != null) {
            // i primi errori dopo uno zapping sono spesso transitori (connessione precedente ancora aperta): riprova presto
            failures++
            delay(if (failures <= 3) 2_000L else 8_000L)
            ext = "m3u8"
            retry++
        }
    }
    LaunchedEffect(overlayTick) {
        overlay = true
        delay(5_000)
        overlay = false
    }
    LaunchedEffect(typed) {
        if (typed.isNotEmpty()) {
            delay(1_500)
            val n = typed.toIntOrNull()
            val i = channels.indexOfFirst { it.num == n }
            if (i >= 0) goTo(i)
            typed = ""
            overlayTick++
        }
    }
    LaunchedEffect(panel) { if (panel == 0) runCatching { fr.requestFocus() } }
    BackHandler(enabled = panel != 0) { panel = 0 }

    // EPG: dal file XMLTV, altrimenti quella breve del singolo canale
    @Suppress("UNUSED_VARIABLE") val ver = vm.epgVersion
    var fallback by remember(ch.id) { mutableStateOf<List<Programme>>(emptyList()) }
    LaunchedEffect(ch.id, vm.epgVersion) {
        if (!vm.epg.has(ch.epgId)) {
            fallback = try { vm.repo.shortEpg(ch.id, 10) } catch (e: Exception) { emptyList() }
        }
    }
    val t = System.currentTimeMillis()
    val (xNow, xNext) = vm.epg.nowNext(ch.epgId, t)
    val now = xNow ?: fallback.firstOrNull { it.start <= t && it.stop > t }
    val next = xNext ?: fallback.firstOrNull { it.start >= (now?.stop ?: t) }
    val upcoming = vm.epg.upcoming(ch.epgId, 12, t).ifEmpty { fallback.filter { it.stop > t } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { ev ->
                if (panel != 0) return@onKeyEvent false
                val down = ev.type == KeyEventType.KeyDown
                val first = ev.nativeKeyEvent.repeatCount == 0
                when {
                    // ▼ = canale successivo nell'elenco (quello sotto), ▲ = precedente (quello sopra)
                    ev.key == Key.DirectionDown || ev.key == Key.ChannelUp || ev.key == Key.PageUp -> {
                        if (down && first) { zapNext(); overlayTick++ }
                        true
                    }
                    ev.key == Key.DirectionUp || ev.key == Key.ChannelDown || ev.key == Key.PageDown -> {
                        if (down && first) { zapPrev(); overlayTick++ }
                        true
                    }
                    ev.key == Key.MediaRewind || ev.key == Key.MediaPrevious -> {
                        if (down && first) { toPrevious(); overlayTick++ }
                        true
                    }
                    ev.key == Key.DirectionLeft -> { if (down && first) panel = 1; true }
                    ev.key == Key.DirectionRight -> { if (down && first) panel = 2; true }
                    ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter -> {
                        if (down && first) { if (overlay) panel = 1 else overlayTick++ }
                        true
                    }
                    ev.key == Key.Menu || ev.key == Key.Info || ev.key == Key.Settings -> { if (down && first) panel = 3; true }
                    ev.key == Key.MediaPlayPause || ev.key == Key.MediaPlay || ev.key == Key.MediaPause -> {
                        if (down && first) player.playWhenReady = !player.playWhenReady
                        true
                    }
                    ev.key in DIGITS -> {
                        if (down && first && typed.length < 4) { typed += DIGITS[ev.key]; overlayTick++ }
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(fr)
            .focusable()
            // touchscreen: tocco = mostra/nasconde i comandi, swipe su/giù = cambia canale, swipe destra/sinistra = canali/guida
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (panel != 0) panel = 0 else if (overlay) overlay = false else overlayTick++
                })
            }
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        if (panel == 0) {
                            val vertical = kotlin.math.abs(dy) > kotlin.math.abs(dx)
                            when {
                                vertical && dy < -120f -> { zapNext(); overlayTick++ }
                                vertical && dy > 120f -> { zapPrev(); overlayTick++ }
                                !vertical && dx > 160f -> panel = 1
                                !vertical && dx < -160f -> panel = 2
                            }
                        }
                    },
                ) { _, amount ->
                    dx += amount.x
                    dy += amount.y
                }
            },
    ) {
        VideoSurface(player)

        if (buffering && error == null) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center))
        }
        error?.let {
            Text(
                stringResource(R.string.retry_soon, it),
                color = Gold, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000)).padding(16.dp),
            )
        }
        if (typed.isNotEmpty()) {
            Text(
                typed, color = TextMain, fontSize = 48.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(40.dp).background(Color(0xAA000000)).padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        if (overlay && panel == 0) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(BottomShade)
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${ch.num}", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(ch.name, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if ("L${ch.id}" in vm.favs) Text("★", color = Gold, fontSize = 20.sp)
                }
                if (now != null) {
                    Text(stringResource(R.string.now) + "  ${fmtTime(now.start)}-${fmtTime(now.stop)}  ${now.title}", color = TextMain, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val frac = ((t - now.start).toFloat() / (now.stop - now.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
                    }
                }
                if (next != null) {
                    Text(stringResource(R.string.next) + "  ${fmtTime(next.start)}-${fmtTime(next.stop)}  ${next.title}", color = TextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(stringResource(R.string.btn_exit), { vm.back() })
                    Button("▲", { zapPrev(); overlayTick++ })
                    Button("▼", { zapNext(); overlayTick++ })
                    Button(stringResource(R.string.channels), { panel = 1 })
                    Button(stringResource(R.string.guide), { panel = 2 })
                    Button(stringResource(R.string.options), { panel = 3 })
                }
                Text(
                    stringResource(R.string.live_hint),
                    color = TextDim, fontSize = 11.sp,
                )
            }
        }

        if (panel == 1) {
            val state = rememberLazyListState(initialFirstVisibleItemIndex = (index - 3).coerceAtLeast(0))
            Column(Modifier.align(Alignment.CenterStart).width(320.dp).fillMaxHeight().background(Color(0xEE0B0F1A)).padding(16.dp)) {
                Text(stringResource(R.string.channels), color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.noFocus(), state = state, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
                        val rowFr = remember { FocusRequester() }
                        if (i == index) LaunchedEffect(Unit) { runCatching { rowFr.requestFocus() } }
                        TvBox(
                            onClick = { goTo(i); panel = 0 },
                            selected = i == index,
                            focusRequester = rowFr,
                            modifier = Modifier.fillMaxWidth(),
                            focusScale = 1.01f,
                        ) { f ->
                            Text(
                                "${c.num}  ${c.name}",
                                color = if (f || i == index) TextMain else TextDim,
                                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        if (panel == 3) {
            val isFav = "L${ch.id}" in vm.favs
            OptionsPanel(player, sleepMin, { sleepMin = nextSleep(sleepMin) }, Modifier.align(Alignment.CenterEnd)) {
                if (prevIndex >= 0) {
                    OptionRow(stringResource(R.string.prev_channel, channels[prevIndex].name), { toPrevious(); panel = 0 })
                }
                OptionRow(if (isFav) "★ Rimuovi dai preferiti" else "☆ Aggiungi ai preferiti", { vm.toggleFav("L${ch.id}") })
            }
        }

        if (panel == 2) {
            Column(
                Modifier.align(Alignment.CenterEnd).width(400.dp).fillMaxHeight().background(Color(0xEE0B0F1A)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.guide_for, ch.name), color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (upcoming.isEmpty()) Text(stringResource(R.string.guide_none), color = TextDim, fontSize = 14.sp)
                upcoming.take(8).forEach { p ->
                    Column {
                        Text("${fmtTime(p.start)}  ${p.title}", color = TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p.desc.isNotEmpty()) Text(p.desc, color = TextDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.back_close), color = TextDim, fontSize = 11.sp)
            }
        }
    }
}

// ====================== FILM / SERIE ======================

@Composable
fun VodPlayerScreen(vm: AppViewModel, req: Screen.PlayVod) {
    val ctx = LocalContext.current
    val player = rememberPlayer()
    var idx by remember { mutableIntStateOf(req.index.coerceIn(0, req.entries.lastIndex)) }
    val entry = req.entries[idx]
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var overlay by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    val last = remember { longArrayOf(0L, 0L) }
    val fr = remember { FocusRequester() }
    var optOpen by remember { mutableStateOf(false) }
    var sleepMin by remember { mutableIntStateOf(0) }
    SleepTimer(sleepMin, player) { vm.back() }
    BackHandler(enabled = optOpen) { optOpen = false }

    LaunchedEffect(entry.key) {
        error = null
        buffering = true
        val saved = vm.store.resume(entry.key)
        player.setMediaItem(MediaItem.fromUri(entry.url), if (saved > 30_000) saved else 0L)
        player.prepare()
        player.playWhenReady = true
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    vm.dropContinue(entry.key)
                    if (idx + 1 < req.entries.size) idx++ else vm.back()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(e: PlaybackException) {
                error = playbackErrorText(ctx, e, vm.account)
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(entry.key) {
        var n = 0
        while (true) {
            pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            last[0] = pos
            last[1] = dur
            if (++n % 20 == 0 && dur > 0 && pos > 0) {
                vm.store.setResume(entry.key, pos, dur)
                if (pos > 30_000) entry.item?.let { vm.noteContinue(it) }
            }
            delay(500)
        }
    }
    DisposableEffect(entry.key) {
        onDispose {
            if (last[0] > 0) {
                vm.store.setResume(entry.key, last[0], last[1])
                if (last[0] > 30_000) entry.item?.let { vm.noteContinue(it) }
            }
        }
    }
    LaunchedEffect(overlayTick, playing) {
        overlay = true
        if (playing) {
            delay(4_000)
            overlay = false
        }
    }
    LaunchedEffect(optOpen) { if (!optOpen) runCatching { fr.requestFocus() } }

    fun seek(delta: Long) {
        val target = (player.currentPosition + delta).coerceIn(0L, if (dur > 0) dur else Long.MAX_VALUE)
        player.seekTo(target)
        pos = target
        overlayTick++
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { ev ->
                if (optOpen) return@onKeyEvent false
                val down = ev.type == KeyEventType.KeyDown
                val rep = ev.nativeKeyEvent.repeatCount
                when (ev.key) {
                    Key.DirectionLeft, Key.MediaRewind -> {
                        if (down) seek(-(if (rep > 8) 60_000L else if (rep > 2) 30_000L else 10_000L))
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (down) seek(if (rep > 8) 60_000L else if (rep > 2) 30_000L else 10_000L)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                        if (down && rep == 0) { player.playWhenReady = !player.playWhenReady; overlayTick++ }
                        true
                    }
                    Key.MediaPlay -> { if (down) player.playWhenReady = true; true }
                    Key.MediaPause -> { if (down) player.playWhenReady = false; true }
                    Key.DirectionUp, Key.Menu, Key.Info, Key.Settings -> { if (down && rep == 0) optOpen = true; true }
                    Key.DirectionDown -> { if (down) overlayTick++; true }
                    Key.MediaNext -> { if (down && idx + 1 < req.entries.size) idx++; true }
                    Key.MediaPrevious -> { if (down && idx > 0) idx--; true }
                    else -> false
                }
            }
            .focusRequester(fr)
            .focusable()
            // touchscreen: tocco = mostra/nasconde i comandi, doppio tocco a sinistra/destra = -10s/+10s
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (overlay) overlay = false else overlayTick++ },
                    onDoubleTap = { o -> seek(if (o.x < size.width / 2) -10_000L else 10_000L) },
                )
            },
    ) {
        VideoSurface(player)

        if (buffering && error == null) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center))
        }
        error?.let {
            Text(it, color = Gold, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000)).padding(16.dp))
        }
        if (overlay) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(BottomShade)
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text((if (!playing && !buffering) "⏸  " else "") + entry.title, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                // barra del tempo: toccabile e trascinabile
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .pointerInput(dur) {
                            detectTapGestures { o ->
                                if (dur > 0) {
                                    val t = (o.x / size.width * dur).toLong().coerceIn(0L, dur)
                                    player.seekTo(t)
                                    pos = t
                                    overlayTick++
                                }
                            }
                        }
                        .pointerInput(dur) {
                            detectHorizontalDragGestures { change, _ ->
                                if (dur > 0) {
                                    val t = (change.position.x / size.width * dur).toLong().coerceIn(0L, dur)
                                    player.seekTo(t)
                                    pos = t
                                    overlayTick++
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x55FFFFFF))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(stringResource(R.string.btn_exit), { vm.back() })
                    Button("⏪ 10s", { seek(-10_000L) })
                    Button(if (playing) stringResource(R.string.btn_pause) else stringResource(R.string.btn_resume), { player.playWhenReady = !player.playWhenReady; overlayTick++ })
                    Button("10s ⏩", { seek(10_000L) })
                    if (idx + 1 < req.entries.size) Button(stringResource(R.string.btn_next), { idx++ })
                    Button(stringResource(R.string.options), { optOpen = true })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${fmtClock(pos)} / ${fmtClock(dur)}", color = TextDim, fontSize = 13.sp)
                    Text(stringResource(R.string.vod_hint), color = TextDim, fontSize = 11.sp)
                }
            }
        }
        if (optOpen) {
            OptionsPanel(player, sleepMin, { sleepMin = nextSleep(sleepMin) }, Modifier.align(Alignment.CenterEnd))
        }
    }
}
