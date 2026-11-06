@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lumen.app

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Pausa della diretta: alla pausa il player chiude la sua connessione e questa classe scarica il canale (.ts)
 * in un file temporaneo; alla ripresa il player legge quel file mentre continua a crescere (differita).
 * Una sola connessione al server alla volta. Il file si cancella tornando in diretta o uscendo.
 */
class Timeshift(val file: File) {
    @Volatile
    var written = 0L
        private set

    /** Lo scaricamento è finito (errore, spazio esaurito o chiusura): oltre `written` non arriverà altro. */
    @Volatile
    var done = false
        private set

    @Volatile
    private var stop = false
    private var thread: Thread? = null

    val uri: Uri get() = Uri.Builder().scheme(SCHEME).path(file.absolutePath).build()

    fun start(url: String, userAgent: String) {
        file.delete()
        active[file.absolutePath] = this
        thread = Thread({ run(url, userAgent) }, "lumen-timeshift").apply {
            isDaemon = true
            start()
        }
    }

    private fun open(url: String, ua: String): HttpURLConnection {
        var u = url
        repeat(5) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", ua)
            val code = c.responseCode
            if (code in 300..399) {
                // anche i redirect tra http e https, che HttpURLConnection non segue da solo
                u = URL(URL(u), c.getHeaderField("Location") ?: throw IOException("redirect")).toString()
                c.disconnect()
            } else if (code in 200..299) {
                return c
            } else {
                c.disconnect()
                throw IOException("HTTP $code")
            }
        }
        throw IOException("too many redirects")
    }

    private fun run(url: String, ua: String) {
        var failures = 0
        // molti server contano ancora per un po' la connessione appena chiusa dal player (abbonamenti a 1
        // connessione): finché non è arrivato niente si riprova fino a un minuto, tanto si è in pausa
        val firstDeadline = System.currentTimeMillis() + 60_000L
        try {
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (!stop) {
                    try {
                        val c = open(url, ua)
                        android.util.Log.i("Lumen", "timeshift: connesso")
                        try {
                            c.inputStream.use { inp ->
                                while (!stop) {
                                    val n = inp.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    written += n
                                    failures = 0
                                    if (written % (32L shl 20) < n && file.usableSpace < MIN_FREE) stop = true
                                }
                            }
                        } finally {
                            c.disconnect()
                        }
                    } catch (e: Exception) {
                        if (stop) break
                        android.util.Log.i("Lumen", "timeshift: tentativo fallito (${e.message})")
                    }
                    if (stop) break
                    failures++
                    val giveUp = if (written == 0L) System.currentTimeMillis() > firstDeadline else failures > 6
                    if (giveUp) break
                    Thread.sleep(if (written == 0L) 2_000 else 1_000)
                }
            }
        } catch (e: Exception) {
        } finally {
            android.util.Log.i("Lumen", "timeshift: fine, scritti $written byte")
            done = true
        }
    }

    fun close() {
        stop = true
        done = true
        thread?.interrupt()
        active.remove(file.absolutePath)
        file.delete()
    }

    companion object {
        const val SCHEME = "lumen-ts"
        private const val MIN_FREE = 300L shl 20
        internal val active = ConcurrentHashMap<String, Timeshift>()
    }
}

/** Legge il file della differita mentre cresce: alla fine dei dati aspetta i successivi invece di terminare. */
class GrowingFileDataSource : BaseDataSource(false) {
    private var raf: RandomAccessFile? = null
    private var uri: Uri? = null
    private var shift: Timeshift? = null
    private var pos = 0L

    override fun open(dataSpec: DataSpec): Long {
        val path = dataSpec.uri.path ?: throw IOException("no path")
        transferInitializing(dataSpec)
        shift = Timeshift.active[path] ?: throw IOException("timeshift closed")
        raf = RandomAccessFile(path, "r")
        uri = dataSpec.uri
        pos = dataSpec.position
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val s = shift ?: return C.RESULT_END_OF_INPUT
        val f = raf ?: return C.RESULT_END_OF_INPUT
        var waited = 0
        while (true) {
            val avail = s.written - pos
            if (avail > 0) {
                f.seek(pos)
                val n = f.read(buffer, offset, minOf(length.toLong(), avail).toInt())
                if (n > 0) {
                    pos += n
                    bytesTransferred(n)
                    return n
                }
            }
            if (s.done) return C.RESULT_END_OF_INPUT
            try {
                Thread.sleep(20)
            } catch (e: InterruptedException) {
                throw InterruptedIOException()
            }
            waited += 20
            if (waited > 90_000) throw IOException("timeshift stalled")
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            raf?.close()
        } finally {
            raf = null
            if (uri != null) {
                uri = null
                transferEnded()
            }
        }
    }
}

/** Sceglie la sorgente in base all'indirizzo: file della differita oppure rete/file normali. */
class RoutingDataSource(private val base: DataSource.Factory) : DataSource {
    private val listeners = ArrayList<TransferListener>()
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        val ds = if (dataSpec.uri.scheme == Timeshift.SCHEME) GrowingFileDataSource() else base.createDataSource()
        listeners.forEach { ds.addTransferListener(it) }
        current = ds
        return ds.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = current?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }
}
