package io.github.sammynashed.emran.data.audio

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/**
 * The reciter's voice as five levels (0..1), one per pillar of the Emran mark, read from the
 * audio our own player is decoding (no microphone, no permission). Five frequency bands, low to
 * high; each is judged against its own recent loudest, so a quiet reciter moves the pillars as
 * much as a loud one. The decoder runs a little ahead of the speaker, so each reading is
 * published [LEAD_MS] later, roughly when it is heard.
 */
@OptIn(UnstableApi::class)
object RecitationLevels : TeeAudioProcessor.AudioBufferSink {
    /** Low → high: chest, voice body, vowels, presence, air. */
    val levels = MutableStateFlow(FloatArray(BANDS))

    private const val BANDS = 5
    private const val N = 1024
    private const val LEAD_MS = 180L
    private val EDGES_HZ = floatArrayOf(80f, 300f, 700f, 1500f, 3200f, 7000f)

    private val main = Handler(Looper.getMainLooper())
    private var sampleRate = 44100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private val window = FloatArray(N)
    private var filled = 0
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val peak = FloatArray(BANDS) { -40f }
    private val hann = FloatArray(N) { (0.5 - 0.5 * cos(2 * Math.PI * it / (N - 1))).toFloat() }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz; channels = channelCount; this.encoding = encoding; filled = 0
    }

    /** No audio has arrived for a moment (a pause between ayat, or the end): the pillars sink back. */
    private val silence = Runnable { levels.value = FloatArray(BANDS) }

    override fun handleBuffer(buffer: ByteBuffer) {
        main.removeCallbacks(silence)
        main.postDelayed(silence, LEAD_MS + 350)
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = when (encoding) { C.ENCODING_PCM_FLOAT -> 4; C.ENCODING_PCM_16BIT -> 2; else -> return }
        val frame = bytesPerSample * channels
        while (b.remaining() >= frame) {
            var mono = 0f
            for (c in 0 until channels) {
                mono += if (bytesPerSample == 2) b.short / 32768f else b.float
            }
            window[filled++] = mono / channels
            if (filled == N) { analyse(); filled = 0 }
        }
    }

    private fun analyse() {
        for (i in 0 until N) { re[i] = window[i] * hann[i]; im[i] = 0f }
        fft(re, im)
        val binHz = sampleRate.toFloat() / N
        val out = FloatArray(BANDS)
        for (k in 0 until BANDS) {
            val from = (EDGES_HZ[k] / binHz).toInt().coerceAtLeast(1)
            val until = (EDGES_HZ[k + 1] / binHz).toInt().coerceAtMost(N / 2)
            var e = 0f
            for (i in from until until) e += re[i] * re[i] + im[i] * im[i]
            val db = 10f * log10(e / max(1, until - from) + 1e-10f)
            // Each band's own recent loudest, slowly forgotten.
            peak[k] = max(peak[k] - 0.08f, db)
            // Only the top 24 dB below the recent loudest counts: the room's echo between words
            // (which sits well under the voice) then reads as quiet, not as sound.
            out[k] = ((db - (peak[k] - 24f)) / 24f).coerceIn(0f, 1f)
        }
        main.postDelayed({ levels.value = out }, LEAD_MS)
    }

    /** Clears the pillars back to rest (paused, stopped). */
    fun rest() { main.removeCallbacks(silence); main.post { levels.value = FloatArray(BANDS) } }

    /** In-place radix-2 FFT. */
    private fun fft(x: FloatArray, y: FloatArray) {
        val n = x.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = x[i]; x[i] = x[j]; x[j] = t; t = y[i]; y[i] = y[j]; y[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f; var ci = 0f
                for (k in 0 until len / 2) {
                    val ar = x[i + k + len / 2] * cr - y[i + k + len / 2] * ci
                    val ai = x[i + k + len / 2] * ci + y[i + k + len / 2] * cr
                    x[i + k + len / 2] = x[i + k] - ar; y[i + k + len / 2] = y[i + k] - ai
                    x[i + k] += ar; y[i + k] += ai
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
