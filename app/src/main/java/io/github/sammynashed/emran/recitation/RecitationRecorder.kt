package io.github.sammynashed.emran.recitation

import ai.ratl.core.Assessment
import ai.ratl.core.PartialAssessment
import ai.ratl.core.WordStatus
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Microphone capture wired into a RATL streaming session.
 *
 * RATL requires 16 kHz mono float PCM, which is exactly what is requested here,
 * so there is no resampling step to get wrong. Capture and inference both run
 * off the main thread.
 *
 * Partial results are **provisional**: RATL may revise a word it already
 * reported once more audio arrives. The UI must present them as in-progress,
 * never as a verdict. Only [Result.final] is settled.
 */
class RecitationRecorder(private val assessor: RecitationAssessor) {

    /**
     * One verse, prepared for both halves of the feature.
     *
     * [arabicWords] is what the reader sees; [targets] is what RATL is asked to
     * align against; [targetToArabic] maps a result index back to the Arabic
     * word that produced it, so a per-word display can never drift.
     */
    data class AyahTargets(
        val arabicWords: List<String>,
        val targets: List<String>,
        val targetToArabic: List<Int>,
    ) {
        companion object {
            val EMPTY = AyahTargets(emptyList(), emptyList(), emptyList())
        }
    }

    /**
     * A result, and the ayah it is about.
     *
     * The tag exists because stopping is not instant. `stop()` sets a flag; the
     * capture loop then has to finish its current read and run `finish()`
     * through the model before it actually ends. Anything still in flight during
     * that window used to arrive after the screen had already moved on, and got
     * applied to the next ayah - which revealed it whole and, with auto-advance
     * on, cascaded through the surah. A result that names its ayah can simply be
     * discarded when it is about a different one.
     */
    sealed interface Result {
        val tag: Int

        /** Provisional. Words may still change. */
        data class Partial(override val tag: Int, val assessment: PartialAssessment) : Result

        /** Settled, after the reciter stops or moves on. */
        data class Final(override val tag: Int, val assessment: Assessment) : Result

        data class Failed(override val tag: Int, val reason: String) : Result
    }

    private val _results = MutableSharedFlow<Result>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val results: SharedFlow<Result> = _results

    private val _level = MutableStateFlow(0f)

    /**
     * Loudness of the audio being captured, 0..1.
     *
     * Published so the screen can show that the microphone is actually hearing
     * something. Assessment results arrive every few hundred milliseconds at
     * best, and on a hard ayah they may not arrive at all - without this, a
     * reciter cannot tell a silent microphone from an engine that is thinking.
     */
    val level: StateFlow<Float> = _level.asStateFlow()

    /**
     * The next ayah to align against, handed over without closing the mic.
     *
     * Continuous recitation is one unbroken capture: reopening `AudioRecord`
     * between ayat costs a hundred milliseconds or so, which is exactly the
     * moment the next ayah begins. Only the RATL session is swapped.
     */
    @Volatile private var pending: Pair<List<String>, Int>? = null

    private var job: Job? = null
    private var record: AudioRecord? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    /**
     * Start recording and assessing against [targetWords].
     *
     * Caller must already hold RECORD_AUDIO.
     */
    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        targetWords: List<String>,
        tag: Int,
        /**
         * Settle the ayah on a pause instead of waiting to be stopped.
         *
         * Continuous recitation needs an end-of-ayah signal that does not
         * come from a button. It cannot come from the assessment either:
         * the ayah is only settled by `finish()`, and in continuous mode
         * `finish()` is what moving on would trigger - so waiting for the
         * result to decide when to move on never resolves. The reciter's
         * own pause is the only signal outside that loop.
         */
        continuous: Boolean = false,
    ) {
        if (isRecording) return
        isRecording = true
        pending = null

        job = scope.launch(Dispatchers.Default) {
            val minBuffer = AudioRecord.getMinBufferSize(
                RecitationAssessor.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) {
                isRecording = false
                _results.emit(Result.Failed(tag, "This device cannot record at 16 kHz mono."))
                return@launch
            }
            // Roughly 250 ms of audio per read; RATL recomputes a partial every
            // 500 ms of new audio, so reading faster than that only adds work.
            val chunk = (RecitationAssessor.SAMPLE_RATE / 4)
            // A full second of headroom. The reader below never blocks capture,
            // but a generous driver buffer costs nothing and absorbs a stall.
            val bufferBytes = maxOf(minBuffer, chunk * 2 * 4)

            var current = tag
            var session = try {
                assessor.startSession(targetWords)
            } catch (e: Throwable) {
                isRecording = false
                _results.emit(
                    Result.Failed(current, "Could not load the assessment engine: ${e.message}"),
                )
                return@launch
            }

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    RecitationAssessor.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes,
                )
            } catch (e: SecurityException) {
                isRecording = false
                _results.emit(Result.Failed(tag, "Microphone permission was denied."))
                return@launch
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                isRecording = false
                _results.emit(Result.Failed(tag, "The microphone is unavailable."))
                return@launch
            }
            record = recorder

            // Capture and inference are separate on purpose. They used to share
            // one loop: read 250 ms, run the engine, read again. The engine
            // re-decodes the whole buffer on every update and on a phone that
            // takes longer than the audio it is deciding about, so the mic's
            // buffer overflowed while the engine thought and the lost audio
            // became holes in the recitation. Measured on device 2026-10-10:
            // 4.2 s of speech reached the engine over 6.5 s of wall clock, and
            // the engine reported spoken words as omitted. Now one coroutine only
            // reads and queues - it can never be held up by the engine - and the
            // other takes everything queued since its last turn in a single push.
            val queue = Channel<Chunk>(Channel.UNLIMITED)
            val capture = launch(Dispatchers.IO) {
                val shorts = ShortArray(chunk)
                try {
                    recorder.startRecording()
                    while (isActive && isRecording) {
                        val n = recorder.read(shorts, 0, shorts.size)
                        if (n < 0) break
                        if (n == 0) continue
                        val floats = FloatArray(n)
                        var sumSquares = 0.0
                        for (i in 0 until n) {
                            val f = shorts[i] / 32768.0f
                            floats[i] = f
                            sumSquares += (f * f).toDouble()
                        }
                        // RMS, then a gain that puts ordinary recitation in the
                        // upper half of the range rather than a barely-visible
                        // twitch at the bottom.
                        val rms = kotlin.math.sqrt(sumSquares / n).toFloat()
                        _level.value = (rms * LEVEL_GAIN).coerceIn(0f, 1f)
                        queue.trySend(Chunk(floats, rms, n * 1000L / RecitationAssessor.SAMPLE_RATE))
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    _results.emit(Result.Failed(current, e.message ?: "Recording failed."))
                } finally {
                    queue.close()
                }
            }

            val debugDir = assessor.debugAudioDir()
            var heard = ArrayList<FloatArray>()
            fun dumpHeard() {
                val dir = debugDir ?: return
                if (heard.isEmpty()) return
                runCatching { writeWav(java.io.File(dir, "${System.currentTimeMillis()}-ayah$current.wav"), heard) }
                heard = ArrayList()
            }

            var silentMs = 0L
            var spokenMs = 0L
            // True between settling an ayah and being given the next one. Audio
            // in that gap is the pause itself and belongs to no ayah.
            var awaitingNext = false
            try {
                while (true) {
                    val first = queue.receiveCatching().getOrNull() ?: break
                    val batch = ArrayList<Chunk>(4)
                    batch += first
                    while (true) batch += (queue.tryReceive().getOrNull() ?: break)

                    // A handover settles the ayah just finished, then aligns
                    // against the next one - all without stopping capture.
                    pending?.let { (nextTarget, nextTag) ->
                        pending = null
                        if (!awaitingNext) {
                            runCatching {
                                if (!awaitingNext) _results.emit(Result.Final(current, session.finish()))
                            }
                        }
                        dumpHeard()
                        session = assessor.startSession(nextTarget)
                        current = nextTag
                        awaitingNext = false
                        silentMs = 0
                        spokenMs = 0
                    }

                    var samples = 0
                    for (c in batch) {
                        if (c.rms < SILENCE_RMS) {
                            silentMs += c.ms
                        } else {
                            silentMs = 0
                            spokenMs += c.ms
                        }
                        samples += c.samples.size
                    }
                    // While waiting to be handed the next ayah, keep the
                    // microphone open but feed nothing: this audio is the gap.
                    if (awaitingNext) continue

                    val audio = if (batch.size == 1) {
                        batch[0].samples
                    } else {
                        val merged = FloatArray(samples)
                        var at = 0
                        for (c in batch) {
                            c.samples.copyInto(merged, at)
                            at += c.samples.size
                        }
                        merged
                    }
                    if (debugDir != null) heard.add(audio)
                    val partial = session.pushAudio(audio)
                    _results.emit(Result.Partial(current, partial))

                    // A real pause after real recitation ends the ayah.
                    //
                    // But a pause is not an ending. In Hifz a reciter stops to
                    // remember, and settling the ayah on any 1.3 s gap skipped
                    // it with words unsaid (seen on device: 5 of 7 words
                    // reached, a pause, and the ayah was gone). So the pause
                    // only counts once the engine has reached all but one of the
                    // words, or after a long silence with at least half reached.
                    val reached = partial.words.count { it.status != WordStatus.PENDING }
                    val size = partial.words.size
                    val finished = reached >= size - 1 ||
                        (silentMs >= LONG_PAUSE_MS && reached * 2 >= size)
                    if (continuous && spokenMs >= MIN_SPEECH_MS && silentMs >= PAUSE_MS && finished) {
                        if (!awaitingNext) _results.emit(Result.Final(current, session.finish()))
                        awaitingNext = true
                        silentMs = 0
                        spokenMs = 0
                    }
                }
                if (!awaitingNext) _results.emit(Result.Final(current, session.finish()))
                dumpHeard()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _results.emit(Result.Failed(current, e.message ?: "Recording failed."))
            } finally {
                withContext(NonCancellable) { capture.cancelAndJoin() }
                _level.value = 0f
                runCatching { recorder.stop() }
                recorder.release()
                record = null
                isRecording = false
            }
        }
    }

    /**
     * Move on to the next ayah without interrupting capture.
     *
     * Does nothing if capture has already ended, so a late call cannot restart
     * a session the reciter has stopped.
     */
    fun advanceTo(targetWords: List<String>, tag: Int) {
        if (!isRecording) return
        pending = targetWords to tag
    }

    /** Stop recording; the final assessment follows on [results]. */
    fun stop() {
        pending = null
        isRecording = false
    }

    suspend fun release() {
        stop()
        job?.let { runCatching { it.cancel() } }
        job = null
        withContext(Dispatchers.Default) { record?.let { runCatching { it.release() } } }
        record = null
    }

    companion object {
        /** Turns RMS into something worth looking at without clipping. */
        private const val LEVEL_GAIN = 6f

        /**
         * Below this RMS the microphone is hearing a room, not a reciter.
         *
         * Measured room tone at -45 dBFS sits near 0.006; ordinary recitation
         * is an order of magnitude above. Set low deliberately: cutting someone
         * off mid-ayah is far worse than settling an ayah a moment late.
         */
        private const val SILENCE_RMS = 0.010f

        /** A pause this long, after real speech, ends the ayah. */
        private const val PAUSE_MS = 1_300L

        /** Silence this long settles an ayah that is only half reached. */
        private const val LONG_PAUSE_MS = 6_000L

        /** Below this, a cough is not a recitation. */
        private const val MIN_SPEECH_MS = 700L

        /**
         * Split a verse transliteration into RATL target words.
         *
         * Emran's verse model carries a transliteration string; RATL wants one
         * romanised form per word. Punctuation that is not part of the model's
         * 35-token vocabulary is dropped rather than passed through as OOV.
         */
        fun targetWordsFrom(transliteration: String?): List<String> =
            transliteration.orEmpty()
                .split(Regex("\\s+"))
                .map { w -> w.trim().lowercase().filter { it.isLetter() || it == '-' || it == '\'' } }
                .filter { it.isNotEmpty() }

        /**
         * Target words from Uthmani Arabic - the path Emran actually uses.
         *
         * Emran's bundled quran.json carries Arabic text and no per-verse
         * transliteration, so RATL romanises it itself (SPEC 5.5). Measured G2
         * through this path is 0.8504, against 0.8570 with reference
         * transliterations.
         */
        fun targetWordsFromArabic(arabic: String?): List<String> =
            if (arabic.isNullOrBlank()) emptyList()
            else ai.ratl.text.Normalizer.romanizeVerse(arabic)

        /**
         * The verse split for display, alongside the targets sent to RATL.
         *
         * A read-along view has to point each result back at the Arabic word it
         * came from, and `romanizeVerse` cannot support that on its own: it ends
         * in `.filter { it.isNotEmpty() }`, so a word that romanises to nothing
         * disappears from the target list while remaining in the verse, and
         * every later index refers to the wrong word.
         *
         * Measured over Emran's bundled text, no verse in the Qur'an currently
         * loses a word this way (`RomanizeVerseDropTest`), so today the two
         * lists happen to line up. Relying on that would be relying on a
         * property of the data rather than of the code, and the failure it
         * invites - highlighting the wrong word of the Qur'an - is not one to
         * leave to luck. So the map is built explicitly and kept.
         */
        fun targetsFromArabic(arabic: String?): AyahTargets {
            if (arabic.isNullOrBlank()) return AyahTargets.EMPTY
            val words = arabic.split(Regex("\\s+")).filter { it.isNotBlank() }
            val targets = mutableListOf<String>()
            val toArabic = mutableListOf<Int>()
            words.forEachIndexed { i, w ->
                val romanised = ai.ratl.text.Normalizer.romanize(w, utteranceInitial = i == 0)
                if (romanised.isNotEmpty()) {
                    targets += romanised
                    toArabic += i
                }
            }
            return AyahTargets(words, targets, toArabic)
        }
    }
}

/** One read from the microphone, with what the pause detector needs to know about it. */
private class Chunk(val samples: FloatArray, val rms: Float, val ms: Long)

/** 16 kHz mono 16-bit WAV, for tuning the engine offline. */
private fun writeWav(file: java.io.File, chunks: List<FloatArray>) {
    val n = chunks.sumOf { it.size }
    val out = java.io.DataOutputStream(file.outputStream().buffered())
    fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
    fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
    out.writeBytes("RIFF"); le32(36 + n * 2); out.writeBytes("WAVEfmt ")
    le32(16); le16(1); le16(1); le32(16000); le32(32000); le16(2); le16(16)
    out.writeBytes("data"); le32(n * 2)
    for (c in chunks) for (f in c) le16((f.coerceIn(-1f, 1f) * 32767f).toInt())
    out.close()
}
