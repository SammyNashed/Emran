package io.github.sammynashed.emran.recitation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.sammynashed.emran.tajweed.wordKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hifz mode over a whole surah.
 *
 * The reciter starts once and recites the surah through. RATL aligns against
 * one ayah at a time - that is what it is given a target for - so this walks the
 * surah ayah by ayah underneath, handing the microphone straight on without
 * ever closing it, while the page above stays a single continuous muṣḥaf whose
 * words unblur cumulatively and never re-blur.
 *
 * State is deliberately kept in page ordinals rather than per-ayah sets. The
 * page is what the reciter sees; keeping the model in the same terms means the
 * display never has to reconcile two numbering schemes, which is exactly where
 * an earlier version put highlights on the wrong words.
 */
class HifzSession(
    private val assessor: RecitationAssessor,
    val recorder: RecitationRecorder,
) {
    /** Page ordinals confirmed so far. Only ever grows within a run. */
    var revealed by mutableStateOf(setOf<Int>())
        private set

    /** Ordinals settled but not vouched for; shown, marked unsure. */
    var unsure by mutableStateOf(setOf<Int>())
        private set

    /** Ordinals mid-reveal, with the alpha to paint them at. */
    var fading by mutableStateOf(mapOf<Int, Float>())
        private set

    /** Index into the surah's verse list that the engine is aligned against. */
    var ayah by mutableIntStateOf(0)
        private set

    /** The āyah's own number, which is what word keys are built from. */
    var ayahNumber by mutableIntStateOf(1)
        private set

    var recording by mutableStateOf(false)
        private set

    var preparing by mutableStateOf(false)
        private set

    var problem by mutableStateOf<String?>(null)
        private set

    /** True from [restart] until the next [toggle]: results are about a finished run. */
    private var discard = false

    /** Clean restarts of the current ayah so far. */
    private var attempts = 0

    /** CORRECT in the previous partial, for the stability check. */
    private var steady = setOf<Int>()

    /** Verse index -> the map from RATL target index back to Arabic word. */
    private var targetMap: List<Int> = emptyList()

    fun reset() {
        ayahNumber = 1
        revealed = emptySet()
        unsure = emptySet()
        fading = emptyMap()
        steady = emptySet()
        ayah = 0
        attempts = 0
        problem = null
    }

    /**
     * Begin again from the first ayah, with the page blurred as new.
     *
     * Nothing used to clear the revealed words: "Reveal all" marked every word
     * shown for good, and leaving and re-entering Hifz kept them, so a finished
     * (or given-up) page could never be recited again. This stops the
     * microphone, wipes the run, and makes the session ignore anything still in
     * flight from the old one - the stop itself emits a final result, which would
     * otherwise land on the freshly reset first ayah.
     */
    fun restart() {
        discard = true
        if (recorder.isRecording) recorder.stop()
        recording = false
        preparing = false
        reset()
    }

    fun clearProblem() {
        problem = null
    }

    private fun targetsFor(verses: List<String>, index: Int): RecitationRecorder.AyahTargets =
        runCatching { RecitationRecorder.targetsFromArabic(verses.getOrNull(index)) }
            .getOrElse { RecitationRecorder.AyahTargets.EMPTY }

    /**
     * Begin, or stop if already running.
     *
     * Returns false when there is nothing to align against, so the caller can
     * say so rather than leaving a dead record button.
     */
    fun toggle(
        scope: CoroutineScope,
        verses: List<String>,
        numbers: List<Int>,
        onLoadFailed: (String) -> Unit,
    ): Boolean {
        if (recording) {
            recorder.stop()
            recording = false
            return true
        }
        val t = targetsFor(verses, ayah)
        if (t.targets.isEmpty()) return false

        discard = false
        targetMap = t.targetToArabic
        ayahNumber = numbers.getOrElse(ayah) { ayah + 1 }
        steady = emptySet()
        problem = null
        preparing = true
        recording = true
        scope.launch {
            // Loading the ONNX graph takes long enough to drop frames, so it
            // never happens on the thread drawing the page.
            val loaded = withContext(Dispatchers.IO) { runCatching { assessor.load() } }
            preparing = false
            val failure = loaded.exceptionOrNull()
            if (failure != null) {
                recording = false
                problem = failure.message
                onLoadFailed(failure.message ?: "Could not load the model")
            } else {
                recorder.start(scope, t.targets, ayah, continuous = true)
            }
        }
        return true
    }

    /**
     * Apply a streaming result.
     *
     * Needs no page: a word's identity is its āyah number and its place in that
     * āyah, which is known here and means the same on every page the surah is
     * drawn on.
     */
    fun onResult(
        r: RecitationRecorder.Result,
        verses: List<String>,
        numbers: List<Int>,
    ) {
        if (discard || r.tag != ayah) return
        when (r) {
            is RecitationRecorder.Result.Partial -> {
                val correct = r.assessment.words
                    .filter { it.status == ai.ratl.core.WordStatus.CORRECT }
                    .map { it.index }
                val now = correct.mapNotNull { keyOf(it) }.toSet()

                // Only the *last* confirmed word is the aligner's guess at the
                // frontier, and only that one needs a second partial to trust.
                // Everything behind it already has later audio confirming it, so
                // holding all of them back just made the page lag the reciter.
                val frontier = correct.maxOrNull()
                val safe = correct.filter { it != frontier }.mapNotNull { keyOf(it) }.toSet()
                // The frontier used to wait for a second partial. Measured on
                // device, the aligner revises words that were already "safe"
                // anyway (6 CORRECT, then 3, then 6), so the extra wait bought
                // little protection and cost a visible ~0.4 s of lag per word.
                val settled = now

                val fresh = settled - revealed
                if (fresh.isNotEmpty()) {
                    revealed = revealed + fresh
                    fading = fading + fresh.associateWith { 1f }
                }
                steady = now

                // Hand on only when EVERY word of this ayah has been revealed.
                //
                // Hifz means getting the ayah right, so the page waits for the
                // reciter. Two earlier rules handed on sooner and both lost
                // words: "the cursor reached the end" fired the instant the
                // engine began the last word (ينفقون was still being said,
                // INCOMPLETE, and was dropped - twice), and "a pause ends the
                // ayah" skipped ayat the reciter was only stopping to remember.
                // A word the engine has not confirmed is not lost: the reciter
                // simply recites again, see [redo].
                //
                // Handing on per ayah also bounds the engine's buffer to seconds:
                // a session re-decodes everything recited into it, so one that
                // lives for a whole surah falls further behind with every ayah.
                val words = r.assessment.words
                val total = targetMap.size
                if (total > 0) {
                    val complete = isComplete(total)
                    android.util.Log.d(
                        "HifzSession",
                        "ayah=$ayahNumber words=$total cursor=${r.assessment.cursor} " +
                            "correct=${correct.size} ms=${r.assessment.audioDurationMs.toLong()} " +
                            "complete=$complete " +
                            "status=" + words.groupingBy { it.status }.eachCount(),
                    )
                    if (complete) advance(verses, numbers)
                }
            }
            is RecitationRecorder.Result.Final -> {
                revealed = revealed + r.assessment.words
                    .filter { it.status == ai.ratl.core.WordStatus.CORRECT }
                    .mapNotNull { keyOf(it.index) }
                steady = emptySet()
                // Settled, but not necessarily right. A fully revealed ayah moves
                // on; otherwise it starts again, keeping what was earned - unless
                // the reciter plainly got to the end and the engine is merely
                // deaf to a word or two in the middle.
                //
                // That last case is real, not hypothetical: on the phone mic the
                // aligner kept reporting 2 words of an 8-word ayah as OMISSION
                // in every attempt, so "perfect or redo" would have held the
                // reciter on that ayah forever. A confirmed LAST word proves they
                // reached the end; the few unconfirmed ones are then shown, marked
                // unsure, for the reciter to check. Short ayat tolerate nothing.
                val total = targetMap.size
                val missing = (0 until total).filter { i -> keyOf(i)?.let { it !in revealed } ?: false }
                android.util.Log.d(
                    "HifzSession",
                    "FINAL ayah=$ayahNumber words=$total missing=$missing " +
                        "statuses=" + r.assessment.words.map { it.status.name.take(4) },
                )
                // Up to half the words may be unconfirmed once the end is proven:
                // measured on device, a correctly recited 7-word ayah came back
                // with only its last two words confirmed (3 stuck in the middle).
                val tolerance = if (total <= 3) 0 else total / 2
                val lastConfirmed = total > 0 && (keyOf(total - 1)?.let { it in revealed } ?: false)
                // Never strand the reciter. After a few clean restarts the ayah is
                // accepted with what is missing shown as unsure; the engine, not
                // the reciter, is the likelier cause by then.
                val giveUp = attempts >= MAX_ATTEMPTS
                when {
                    total == 0 || missing.isEmpty() -> advance(verses, numbers)
                    (lastConfirmed && missing.size <= tolerance) || giveUp -> {
                        val keys = missing.mapNotNull { keyOf(it) }
                        revealed = revealed + keys
                        unsure = unsure + keys
                        fading = fading + keys.associateWith { 1f }
                        advance(verses, numbers)
                    }
                    else -> {
                        attempts++
                        redo(verses)
                    }
                }
            }
            is RecitationRecorder.Result.Failed -> {
                problem = r.reason
                recording = false
            }
        }
    }

    /** True when every word of the current ayah is on the page. */
    private fun isComplete(total: Int): Boolean =
        (0 until total).all { i -> keyOf(i)?.let { it in revealed } ?: true }

    /**
     * Listen again to the same ayah.
     *
     * A fresh engine session on the same ayah: the audio of the failed attempt
     * would otherwise sit at the front of the buffer and stop the aligner from
     * finding the start. Words already revealed stay revealed, so the reciter
     * only has to land the ones that are still blurred.
     */
    private fun redo(verses: List<String>) {
        val t = targetsFor(verses, ayah)
        if (t.targets.isEmpty()) return
        targetMap = t.targetToArabic
        steady = emptySet()
        if (recorder.isRecording) recorder.advanceTo(t.targets, ayah) else recording = false
    }

    /** Hand the still-open microphone to the next ayah. */
    private fun advance(verses: List<String>, numbers: List<Int>) {
        val next = ayah + 1
        if (next > verses.lastIndex) {
            if (recorder.isRecording) recorder.stop()
            recording = false
            return
        }
        val t = targetsFor(verses, next)
        if (t.targets.isEmpty()) {
            if (recorder.isRecording) recorder.stop()
            recording = false
            return
        }
        targetMap = t.targetToArabic
        attempts = 0
        ayah = next
        ayahNumber = numbers.getOrElse(next) { next + 1 }
        if (recorder.isRecording) {
            recorder.advanceTo(t.targets, next)
            recording = true
        } else {
            recording = false
        }
    }

    /** RATL target index -> word key, via the Arabic word it came from. */
    private fun keyOf(targetIndex: Int): Int? {
        val arabicWord = targetMap.getOrNull(targetIndex) ?: return null
        return wordKey(ayahNumber, arabicWord)
    }

    /** Everything on the page, for the reader who gives up. */
    fun revealAllKeys(keys: List<Int>) {
        revealed = revealed + keys
        fading = fading - keys.toSet()
    }

    /** Drive the reveal animation; called from a frame loop. */
    fun tickFade(step: Float) {
        if (fading.isEmpty()) return
        val next = HashMap<Int, Float>(fading.size)
        for ((k, v) in fading) {
            val nv = v - step
            if (nv > 0f) next[k] = nv
        }
        fading = next
    }
}

/** Restarts of one ayah before it is accepted with its gaps marked unsure. */
private const val MAX_ATTEMPTS = 2

/** Remembers a session for the lifetime of the screen. */
@Composable
fun rememberHifzSession(assessor: RecitationAssessor): HifzSession {
    val recorder = remember(assessor) { RecitationRecorder(assessor) }
    return remember(recorder) { HifzSession(assessor, recorder) }
}
