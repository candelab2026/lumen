@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lumen.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registrazioni: durante la visione si salva su un file .ts temporaneo lo stesso flusso letto dal player
 * (una sola connessione al server). A registrazione finita il file viene ripulito (solo pacchetti TS validi)
 * e spostato nella cartella scelta. Niente conversione in .mp4: il .ts si apre con Lumen, VLC e quasi tutti
 * i lettori, e la conversione falliva con molti canali. I .ts rimasti da una registrazione interrotta
 * (app chiusa, TV spenta) si sistemano al successivo avvio.
 *
 * Cartella: "" = Download/Lumen (predefinita); "file:<percorso>" = cartella dell'app su USB/SD;
 * "content://..." = cartella scelta dall'utente (solo dove Android offre la scelta, non su Fire TV).
 */
object Recorder {
    class Folder(val label: String, val value: String, val free: Long)
    class Rec(val name: String, val uri: String, val size: Long, val modified: Long, val file: File? = null)

    private const val SUB = "Lumen"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queue = Mutex()

    /** Salvataggio in corso: (testo, frazione 0..1), null se nessuno. Mostrato in basso a sinistra. */
    var job by mutableStateOf<Pair<String, Float>?>(null)
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("recordings", Context.MODE_PRIVATE)

    fun folder(ctx: Context): String = prefs(ctx).getString("dir", "") ?: ""

    fun setFolder(ctx: Context, v: String) {
        prefs(ctx).edit().putString("dir", v).apply()
    }

    private fun downloads() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUB)

    /** Download/Lumen e, se presenti, chiavette USB / schede SD (cartelle dell'app: nessun permesso richiesto). */
    fun folders(ctx: Context): List<Folder> {
        val vols = ctx.getExternalFilesDirs(null).filterNotNull()
        val main = Folder("${Environment.DIRECTORY_DOWNLOADS}/$SUB", "", Environment.getExternalStorageDirectory().usableSpace)
        return listOf(main) + vols.drop(1).mapIndexed { i, d ->
            Folder(ctx.getString(R.string.rec_external, i + 1), "file:" + File(d, "Recordings").absolutePath, d.usableSpace)
        }
    }

    /** Android offre la scelta di una cartella qualsiasi (telefoni; non Fire TV). */
    fun canPick(ctx: Context) = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).resolveActivity(ctx.packageManager) != null

    fun label(ctx: Context): String {
        val v = folder(ctx)
        if (v.startsWith("content:")) return ctx.getString(R.string.rec_custom)
        return folders(ctx).firstOrNull { it.value == v }?.label ?: folders(ctx).first().label
    }

    /** Fino ad Android 9 scrivere in Download richiede il permesso di memoria: si chiede all'avvio della registrazione. */
    fun askPermission(ctx: Context) {
        val a = ctx as? android.app.Activity ?: return
        if (Build.VERSION.SDK_INT < 29 && folder(ctx).isEmpty() && !legacyGranted(ctx)) {
            a.requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 8)
        }
    }

    private fun legacyGranted(ctx: Context) =
        ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Ripiego quando Download non è scrivibile (permesso negato su Android ≤ 9). */
    private fun appDir(ctx: Context) = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Recordings")

    private fun clean(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\n\\t]"), "_").trim().take(80)

    /** Nome senza estensione: "2026-11-06 14-00 - Canale - Programma". */
    fun baseName(channel: String, title: String?): String {
        val t = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date())
        return clean(listOfNotNull(t, channel, title?.takeIf { it.isNotBlank() }).joinToString(" - "))
    }

    /** Il .ts temporaneo sta sullo stesso supporto della destinazione quando possibile (USB → USB). */
    private fun tmpDir(ctx: Context): File {
        val v = folder(ctx)
        val usb = if (v.startsWith("file:")) File(v.removePrefix("file:")).parentFile else null
        return File(usb ?: ctx.getExternalFilesDir(null) ?: ctx.filesDir, "rec_tmp").apply { mkdirs() }
    }

    private fun tmpDirs(ctx: Context) =
        (ctx.getExternalFilesDirs(null).filterNotNull() + ctx.filesDir).map { File(it, "rec_tmp") }.filter { it.isDirectory }

    /** File temporaneo dove scrivere il flusso durante la registrazione. */
    fun begin(ctx: Context, base: String): File = File(tmpDir(ctx), "$base.ts")

    /** Registrazione finita: pulizia e spostamento nella cartella scelta, in sottofondo e in coda. */
    fun finish(ctx: Context, ts: File) {
        val app = ctx.applicationContext
        scope.launch {
            queue.withLock { process(app, ts) }
        }
    }

    /** All'avvio: i .ts rimasti da registrazioni interrotte vengono salvati come se fossero appena finiti. */
    private var recovered = false

    fun recover(ctx: Context) {
        if (recovered) return
        recovered = true
        tmpDir(ctx)
        tmpDirs(ctx).forEach { d -> d.listFiles { f -> f.name.endsWith(".mp4") || f.name.endsWith(".clean") }?.forEach { it.delete() } }
        tmpDirs(ctx).flatMap { d -> d.listFiles { f -> f.name.endsWith(".ts") }?.toList() ?: emptyList() }
            .filter { it.length() > 0 }
            .forEach { finish(ctx, it) }
    }

    private suspend fun process(ctx: Context, ts: File) {
        if (!ts.exists()) return
        val base = ts.name.removeSuffix(".ts")
        job = ctx.getString(R.string.rec_saving) to 0.5f
        // se lo spostamento fallisce il file resta in rec_tmp e si riprova al prossimo avvio
        withContext(Dispatchers.IO) {
            val clean = File(ts.parentFile, "$base.clean")
            if (runCatching { cleanTs(ts, clean) }.getOrDefault(false)) {
                ts.delete()
                clean.renameTo(ts)
            } else {
                clean.delete()
            }
            deliver(ctx, ts, "$base.ts", "video/mp2t")
        }
        job = null
    }

    /**
     * Copia solo i pacchetti TS validi (188 byte che iniziano con 0x47 e sono seguiti da un altro 0x47):
     * scarta eventuali pezzi estranei (playlist, pagine d'errore) che renderebbero il file illeggibile.
     */
    private fun cleanTs(src: File, dst: File): Boolean {
        var kept = 0L
        src.inputStream().buffered(1 shl 20).use { inp ->
            dst.outputStream().buffered(1 shl 20).use { out ->
                val chunk = ByteArray(1 shl 20)
                var buf = ByteArray(0)
                while (true) {
                    val n = inp.read(chunk)
                    val eof = n < 0
                    val data = if (n > 0) buf + chunk.copyOf(n) else buf
                    var i = 0
                    while (i + 188 <= data.size) {
                        // per verificare il pacchetto serve l'inizio del successivo (salvo a fine file)
                        if (!eof && i + 189 > data.size) break
                        val nextOk = i + 188 >= data.size || data[i + 188] == 0x47.toByte()
                        if (data[i] == 0x47.toByte() && nextOk) {
                            out.write(data, i, 188)
                            kept += 188
                            i += 188
                        } else {
                            i++
                        }
                    }
                    buf = data.copyOfRange(i, data.size)
                    if (eof) break
                }
            }
        }
        return kept > 0
    }

    /** Sposta il file finito nella cartella scelta. */
    private fun deliver(ctx: Context, f: File, name: String, mime: String): Boolean = try {
        val v = folder(ctx)
        when {
            v.startsWith("content:") -> {
                val tree = Uri.parse(v)
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, mime, name)
                copyTo(f, doc?.let { ctx.contentResolver.openOutputStream(it) })
            }
            v.startsWith("file:") -> move(f, File(File(v.removePrefix("file:")).apply { mkdirs() }, name))
            Build.VERSION.SDK_INT >= 29 -> {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUB")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val cr = ctx.contentResolver
                val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                val ok = uri != null && copyTo(f, cr.openOutputStream(uri))
                if (uri != null) {
                    if (ok) cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    else cr.delete(uri, null, null)
                }
                ok
            }
            else -> {
                val dir = if (legacyGranted(ctx)) downloads() else appDir(ctx)
                val dst = File(dir.apply { mkdirs() }, name)
                move(f, dst).also { if (it) android.media.MediaScannerConnection.scanFile(ctx, arrayOf(dst.absolutePath), null, null) }
            }
        }
    } catch (e: Exception) {
        false
    }

    private fun copyTo(f: File, out: OutputStream?): Boolean {
        out ?: return false
        out.use { o -> f.inputStream().use { it.copyTo(o, 256 * 1024) } }
        f.delete()
        return true
    }

    private fun move(f: File, dst: File): Boolean {
        if (f.renameTo(dst)) return true
        return copyTo(f, dst.outputStream())
    }

    private fun isRec(name: String) = name.endsWith(".mp4", true) || name.endsWith(".ts", true)

    private fun listDir(d: File?): List<Rec> =
        (d?.listFiles { f -> f.isFile && isRec(f.name) } ?: emptyArray())
            .map { Rec(it.name, Uri.fromFile(it).toString(), it.length(), it.lastModified(), it) }

    fun list(ctx: Context): List<Rec> = try {
        val v = folder(ctx)
        when {
            v.startsWith("content:") -> {
                val tree = Uri.parse(v)
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val cols = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                )
                val out = ArrayList<Rec>()
                ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(1) ?: continue
                        if (isRec(name)) out += Rec(name, DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString(), c.getLong(2), c.getLong(3))
                    }
                }
                out
            }
            v.startsWith("file:") -> listDir(File(v.removePrefix("file:")))
            Build.VERSION.SDK_INT >= 29 -> {
                // senza permessi MediaStore mostra solo i file creati da quest'app: proprio le registrazioni
                val out = ArrayList<Rec>()
                val cols = arrayOf(
                    MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
                )
                ctx.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cols,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$SUB%"), null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(1) ?: continue
                        if (!isRec(name)) continue
                        val uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
                        out += Rec(name, uri.toString(), c.getLong(2), c.getLong(3) * 1000)
                    }
                }
                out
            }
            else -> listDir(downloads()) + listDir(appDir(ctx))
        }.sortedByDescending { it.modified }
    } catch (e: Exception) {
        emptyList()
    }

    fun delete(ctx: Context, r: Rec): Boolean = try {
        val u = Uri.parse(r.uri)
        when {
            r.file != null -> r.file.delete()
            u.authority == MediaStore.AUTHORITY -> ctx.contentResolver.delete(u, null, null) > 0
            else -> DocumentsContract.deleteDocument(ctx.contentResolver, u)
        }
    } catch (e: Exception) {
        false
    }

    /** Spazio libero dove si scrive; -1 se non si sa (cartella scelta dall'utente). */
    fun free(ctx: Context): Long {
        val v = folder(ctx)
        return when {
            v.startsWith("content:") -> -1L
            v.startsWith("file:") -> generateSequence(File(v.removePrefix("file:"))) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace ?: -1L
            else -> Environment.getExternalStorageDirectory().usableSpace
        }
    }
}

/** Riceve i byte letti dal player e, mentre si registra, li scrive su file. Thread-safe. */
class RecTap {
    private var out: OutputStream? = null

    @Volatile
    var bytes = 0L
        private set

    /** Errore di scrittura (disco pieno, USB tolta): la registrazione si è fermata. */
    @Volatile
    var error: String? = null
        private set

    val active get() = out != null

    @Synchronized
    fun start(o: OutputStream) {
        stop()
        out = BufferedOutputStream(o, 256 * 1024)
        bytes = 0L
        error = null
    }

    @Synchronized
    fun stop() {
        val o = out ?: return
        out = null
        try {
            o.flush()
            o.close()
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun write(b: ByteArray, off: Int, len: Int) {
        val o = out ?: return
        try {
            o.write(b, off, len)
            bytes += len
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            out = null
            try { o.close() } catch (x: Exception) { }
        }
    }
}

/**
 * Sink per TeeDataSource: scrive solo i dati video .ts (flusso continuo o segmenti HLS). Si decide dal
 * contenuto: le playlist possono arrivare da indirizzi che non finiscono in .m3u8 (redirect del server)
 * e non devono finire nel file. Un dato video TS comincia sempre con il byte di sincronismo 0x47.
 */
class TapSink(private val tap: RecTap) : DataSink {
    private var state = 0 // 0 da decidere, 1 video, 2 da scartare

    override fun open(dataSpec: DataSpec) {
        val path = dataSpec.uri.path.orEmpty().lowercase()
        state = if (path.endsWith(".m3u8") || path.endsWith(".m3u") || path.endsWith(".key")) 2 else 0
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        if (state == 0) state = if (buffer[offset] == 0x47.toByte()) 1 else 2
        if (state == 1) tap.write(buffer, offset, length)
    }

    override fun close() {}
}
