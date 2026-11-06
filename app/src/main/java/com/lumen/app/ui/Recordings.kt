@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.lumen.app.ui

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.app.AppViewModel
import com.lumen.app.R
import com.lumen.app.Recorder
import com.lumen.app.Screen
import com.lumen.app.VodEntry
import java.text.DateFormat
import java.util.Date

/** Riquadro modale a tutto schermo: il focus resta dentro finché non si chiude. */
@Composable
fun ModalBox(width: Int = 560, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(width.dp).focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()
                .clip(RoundedCornerShape(16.dp)).background(Surface).padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** Scelta della cartella: memoria interna, USB/SD collegate e, dove Android lo permette, una cartella qualsiasi. */
@Composable
fun RecFolderDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onClose() }
    val current = Recorder.folder(ctx)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            Recorder.setFolder(ctx, uri.toString())
            onClose()
        }
    }
    ModalBox {
        Text(stringResource(R.string.rec_folder_title), color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Recorder.folders(ctx).forEachIndexed { i, f ->
            val on = f.value == current
            Button(
                (if (on) "✓ " else "") + f.label + "  ·  " + stringResource(R.string.rec_free, Formatter.formatShortFileSize(ctx, f.free)),
                onClick = { Recorder.setFolder(ctx, f.value); onClose() },
                primary = on, fr = if (i == 0) fr else null, small = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (Recorder.canPick(ctx)) {
            val on = current.startsWith("content:")
            Button(
                (if (on) "✓ " + stringResource(R.string.rec_custom) + "  ·  " else "") + stringResource(R.string.rec_pick),
                onClick = { runCatching { pick.launch(null) } },
                primary = on, small = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stringResource(R.string.rec_note), color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** Elenco delle registrazioni: OK riproduce, pressione lunga / Menu elimina (con conferma). */
@Composable
fun RecordingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    // si rilegge anche quando finisce una conversione in MP4
    val recs = remember(tick, Recorder.job == null) { Recorder.list(ctx) }
    var toDelete by remember { mutableStateOf<Recorder.Rec?>(null) }
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(recs) { runCatching { firstFr.requestFocus() } }
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val compact = isCompact()

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 16.dp else 40.dp, vertical = if (compact) 10.dp else 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rec_title), color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val free = Recorder.free(ctx)
                Text(
                    Recorder.label(ctx) + if (free >= 0) "  ·  " + stringResource(R.string.rec_free, Formatter.formatShortFileSize(ctx, free)) else "",
                    color = TextDim, fontSize = 13.sp,
                )
            }
            Text(stringResource(R.string.rec_hint_list), color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
            // ultima conversione fallita: il dettaglio tecnico serve per segnalare il problema
            remember(recs) { Recorder.lastError(ctx) }?.let {
                Text(stringResource(R.string.rec_last_error, it), color = Gold, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (recs.isEmpty()) {
                CenterMessage(stringResource(R.string.rec_empty))
            } else {
                LazyColumn(Modifier.noFocus().fillMaxWidth().fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(recs, key = { _, r -> r.uri }) { i, r ->
                        TvBox(
                            onClick = {
                                val entries = recs.map { VodEntry(it.name.substringBeforeLast('.'), it.uri, "R:" + it.name) }
                                vm.push(Screen.PlayVod(entries, i))
                            },
                            onLongClick = { toDelete = r },
                            focusRequester = if (i == 0) firstFr else null,
                            modifier = Modifier.fillMaxWidth(),
                            focusScale = 1.01f,
                        ) { f ->
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(r.name.substringBeforeLast('.'), color = if (f) TextMain else TextDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(
                                    dateFmt.format(Date(r.modified)) + "   " + Formatter.formatShortFileSize(ctx, r.size),
                                    color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        val d = toDelete
        if (d != null) {
            val noFr = remember { FocusRequester() }
            LaunchedEffect(d) { runCatching { noFr.requestFocus() } }
            BackHandler { toDelete = null }
            ModalBox {
                Text(stringResource(R.string.rec_delete_q), color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(d.name.substringBeforeLast('.'), color = TextDim, fontSize = 15.sp, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(stringResource(R.string.yes), onClick = { Recorder.delete(ctx, d); toDelete = null; tick++ }, strong = true, modifier = Modifier.width(180.dp))
                    Button(stringResource(R.string.no), onClick = { toDelete = null }, strong = true, fr = noFr, modifier = Modifier.width(180.dp))
                }
            }
        }
    }
}
