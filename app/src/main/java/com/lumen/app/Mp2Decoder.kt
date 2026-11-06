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
    private val platform = DefaultDecoderFactory.Builder(ctx).build()

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
