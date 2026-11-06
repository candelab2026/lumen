@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lumen.app

import android.media.MediaCodec
import android.media.metrics.LogSessionId
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.transformer.Codec
import androidx.media3.transformer.DefaultDecoderFactory
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque

/**
 * L'audio MPEG-1/2 Layer I-II (MP2, comune nei canali che vengono dal digitale terrestre e dal satellite) non entra
 * in un .mp4 e molti dispositivi non hanno un decoder di sistema: lo decodifichiamo in software (JLayer) e
 * Transformer lo ricodifica in AAC. Per tutto il resto si usano i decoder di sistema.
 */
class LumenDecoderFactory(ctx: android.content.Context) : Codec.DecoderFactory {
    private val platform = DefaultDecoderFactory.Builder(ctx).setEnableDecoderFallback(true).build()

    override fun createForAudioDecoding(format: Format, logSessionId: LogSessionId?): Codec =
        if (format.sampleMimeType == MimeTypes.AUDIO_MPEG_L2 || format.sampleMimeType == MimeTypes.AUDIO_MPEG_L1) {
            // se JLayer non parte si lascia fare al sistema: al peggio la conversione fallisce e resta il .ts
            try { Mp2Decoder(format) } catch (e: Throwable) { platform.createForAudioDecoding(format, logSessionId) }
        } else {
            platform.createForAudioDecoding(format, logSessionId)
        }

    override fun createForVideoDecoding(format: Format, outputSurface: Surface, requestSdrToneMapping: Boolean, logSessionId: LogSessionId?): Codec =
        platform.createForVideoDecoding(format, outputSurface, requestSdrToneMapping, logSessionId)
}

/** Decoder MP2 → PCM 16 bit, sincrono: ogni frame in ingresso diventa subito un buffer in uscita. */
class Mp2Decoder(private val format: Format) : Codec {
    private class Out(val data: ByteBuffer, val info: MediaCodec.BufferInfo)

    private val decoder = javazoom.jl.decoder.Decoder()
    private val input: ByteBuffer = ByteBuffer.allocateDirect(maxOf(format.maxInputSize, 16 * 1024))
    private val queue = ArrayDeque<Out>()
    private var inputEnded = false
    private var outFormat: Format? = null

    override fun getConfigurationFormat() = format

    override fun getName() = "lumen.mp2.decoder"

    override fun getInputSurface(): Surface = throw UnsupportedOperationException()

    override fun maybeDequeueInputBuffer(inputBuffer: DecoderInputBuffer): Boolean {
        if (inputEnded || queue.size >= 8) return false
        input.clear()
        inputBuffer.data = input
        return true
    }

    override fun queueInputBuffer(inputBuffer: DecoderInputBuffer) {
        if (inputBuffer.isEndOfStream) {
            inputEnded = true
            return
        }
        val data = inputBuffer.data ?: return
        val bytes = ByteArray(data.remaining())
        data.get(bytes)
        decode(bytes, inputBuffer.timeUs)
    }

    private fun decode(bytes: ByteArray, timeUs: Long) {
        try {
            val bs = javazoom.jl.decoder.Bitstream(ByteArrayInputStream(bytes))
            var t = timeUs
            while (true) {
                val h = bs.readFrame() ?: break
                val sb = decoder.decodeFrame(h, bs) as javazoom.jl.decoder.SampleBuffer
                bs.closeFrame()
                val ch = sb.channelCount
                val rate = sb.sampleFrequency
                val n = sb.bufferLength
                if (outFormat == null) {
                    outFormat = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                        .setSampleRate(rate).setChannelCount(ch).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
                }
                val out = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
                out.asShortBuffer().put(sb.buffer, 0, n)
                out.limit(n * 2)
                val info = MediaCodec.BufferInfo().apply { set(0, n * 2, t, 0) }
                queue.add(Out(out, info))
                t += n.toLong() / ch * 1_000_000L / rate
            }
        } catch (e: Throwable) {
            // frame rovinato (tipico all'inizio di una registrazione partita a metà flusso): si salta
        }
    }

    override fun signalEndOfInputStream() {
        inputEnded = true
    }

    override fun getInputFormat() = format

    override fun getOutputFormat(): Format? = outFormat

    override fun getOutputBuffer(): ByteBuffer? = queue.peekFirst()?.data

    override fun getOutputBufferInfo(): MediaCodec.BufferInfo? = queue.peekFirst()?.info

    override fun releaseOutputBuffer(render: Boolean) {
        queue.pollFirst()
    }

    override fun releaseOutputBuffer(renderPresentationTimeUs: Long) {
        queue.pollFirst()
    }

    override fun isEnded() = inputEnded && queue.isEmpty()

    override fun release() {
        queue.clear()
    }
}

/**
 * Transformer ricodifica tutto il video quando i pixel non sono quadrati (canali SD 16:9 a 720x576, HD a 1440x1080):
 * lento e sulla Fire Stick fallisce (il decoder hardware è già occupato dal player). Si dichiara il video "a pixel
 * quadrati" così viene copiato com'è; il vero rapporto si rimette nel file finito (vedi [fixAspect]).
 */
class CopyVideoAssetLoaderFactory(private val base: androidx.media3.transformer.AssetLoader.Factory) : androidx.media3.transformer.AssetLoader.Factory {
    /** Rapporto dei pixel del video originale (1 se quadrati). */
    @Volatile
    var pixelRatio = 1f
        private set

    override fun createAssetLoader(
        editedMediaItem: androidx.media3.transformer.EditedMediaItem,
        looper: android.os.Looper,
        listener: androidx.media3.transformer.AssetLoader.Listener,
        compositionSettings: androidx.media3.transformer.AssetLoader.CompositionSettings,
    ): androidx.media3.transformer.AssetLoader = base.createAssetLoader(
        editedMediaItem, looper,
        object : androidx.media3.transformer.AssetLoader.Listener by listener {
            override fun onTrackAdded(inputFormat: Format, supportedOutputTypes: Int): Boolean {
                val video = MimeTypes.isVideo(inputFormat.sampleMimeType)
                val r = inputFormat.pixelWidthHeightRatio
                if (video && r > 0f && r != 1f) {
                    pixelRatio = r
                    return listener.onTrackAdded(inputFormat.buildUpon().setPixelWidthHeightRatio(1f).build(), supportedOutputTypes)
                }
                return listener.onTrackAdded(inputFormat, supportedOutputTypes)
            }
        },
        compositionSettings,
    )
}

/** Scrive nel box `pasp` del .mp4 il rapporto dei pixel originale (il muxer lo mette sempre 1:1). */
fun fixAspect(mp4: java.io.File, ratio: Float) {
    if (ratio <= 0f || ratio == 1f) return
    var h = Math.round(ratio * 1000)
    var v = 1000
    tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val g = gcd(h, v)
    h /= g
    v /= g
    java.io.RandomAccessFile(mp4, "rw").use { f ->
        // box di primo livello fino a moov, poi si cerca "pasp" dentro moov
        var pos = 0L
        val len = f.length()
        while (pos + 8 <= len) {
            f.seek(pos)
            var size = f.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also { f.readFully(it) }
            var header = 8
            if (size == 1L) { size = f.readLong(); header = 16 }
            if (size == 0L) size = len - pos
            if (size < header) return
            if (String(type, Charsets.US_ASCII) == "moov") {
                if (size > 64L shl 20) return
                val box = ByteArray(size.toInt())
                f.seek(pos)
                f.readFully(box)
                var i = 4
                while (i + 12 <= box.size) {
                    if (box[i] == 'p'.code.toByte() && box[i + 1] == 'a'.code.toByte() && box[i + 2] == 's'.code.toByte() && box[i + 3] == 'p'.code.toByte()) {
                        f.seek(pos + i + 4)
                        f.writeInt(h)
                        f.writeInt(v)
                        i += 12
                    } else {
                        i++
                    }
                }
                return
            }
            pos += size
        }
    }
}
