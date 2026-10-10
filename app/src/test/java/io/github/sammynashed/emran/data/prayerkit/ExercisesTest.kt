package io.github.sammynashed.emran.data.prayerkit

import io.github.sammynashed.emran.data.prayerkit.Exercises.Ayah
import io.github.sammynashed.emran.data.prayerkit.Exercises.ChainRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExercisesTest {
    // Surah al-Ikhlas, as in quran.json.
    private val ikhlas = listOf(
        "قُلۡ هُوَ ٱللَّهُ أَحَدٌ", "ٱللَّهُ ٱلصَّمَدُ", "لَمۡ يَلِدۡ وَلَمۡ يُولَدۡ", "وَلَمۡ يَكُن لَّهُۥ كُفُوًا أَحَدُۢ"
    ).mapIndexed { i, t -> Ayah(i + 1, Exercises.words(t)) }
    private val pool = ikhlas.flatMap { it.words } + Exercises.words("قُلۡ أَعُوذُ بِرَبِّ ٱلۡفَلَقِ مِن شَرِّ مَا خَلَقَ وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ")

    @Test fun normalizeIgnoresMarksAndAlefForms() {
        assertEquals(Exercises.normalize("ٱللَّهُ"), Exercises.normalize("اللَّه"))
        assertEquals("قل", Exercises.normalize("قُلۡ"))
    }

    @Test fun shortSurahIsOneLessonAndLongOnesSplit() {
        assertEquals(1, Exercises.chunk(ikhlas).size)
        val long = List(12) { Ayah(it + 1, List(9) { "w$it" }) }
        val chunks = Exercises.chunk(long)
        assertTrue(chunks.all { c -> c.sumOf { it.words.size } <= 30 && c.size <= 5 })
        assertEquals(12, chunks.sumOf { it.size })
        // An ayah longer than the budget is a lesson of its own.
        assertEquals(1, Exercises.chunk(listOf(Ayah(255, List(50) { "w" }))).single().size)
    }

    @Test fun segmentsAreBalancedAndKeepEveryWord() {
        val w = List(20) { "w$it" }
        val s = Exercises.segments(w)
        assertEquals(w, s.flatten())
        assertTrue(s.all { it.size <= 8 })
        assertTrue(s.maxOf { it.size } - s.minOf { it.size } <= 1)
    }

    @Test fun decoysNeverMatchTheAnswerOrEachOther() {
        repeat(200) { seed ->
            val rnd = Random(seed)
            for (a in ikhlas) for (w in a.words) {
                val c = Exercises.choices(w, pool, rnd)
                assertEquals(4, c.size)
                assertEquals(4, c.map { Exercises.normalize(it) }.toSet().size)
                assertTrue(c.any { Exercises.normalize(it) == Exercises.normalize(w) })
            }
        }
    }

    @Test fun arrangeDecoysAreNotInTheSegment() {
        repeat(100) { seed ->
            val seg = ikhlas[3].words
            val r = Exercises.arrange(seg, pool, Random(seed))
            val inSeg = seg.map { Exercises.normalize(it) }.toSet()
            assertTrue(r.tiles.filter { it.decoy }.none { Exercises.normalize(it.word) in inSeg })
            assertEquals(seg.size, r.tiles.count { !it.decoy })
        }
    }

    @Test fun chainPassesOnlyByChoosingEveryWord() {
        val run = ChainRun(ikhlas, pool, Random(1))
        var picks = 0
        while (!run.finished) {
            val want = run.currentAyah!!.words[run.revealed]
            run.pick(run.options.first { Exercises.normalize(it) == Exercises.normalize(want) })
            picks++
        }
        // Every word but the very first is chosen, including each ayah's first word.
        assertEquals(ikhlas.sumOf { it.words.size } - 1, picks)
        assertEquals(0, run.totalMistakes)
    }

    @Test fun secondMistakeRestartsTheAyah() {
        val run = ChainRun(ikhlas, pool, Random(2))
        // Finish ayah 1, then get into ayah 2.
        while (run.ayahIndex == 0) run.pick(run.currentAyah!!.words[run.revealed])
        run.pick(run.currentAyah!!.words[run.revealed])
        assertEquals(1, run.revealed)
        fun wrongOption() = run.options.first { Exercises.normalize(it) != Exercises.normalize(run.currentAyah!!.words[run.revealed]) }
        assertEquals(ChainRun.Result.WRONG, run.pick(wrongOption()))
        assertEquals(ChainRun.Result.AYAH_RESTARTED, run.pick(wrongOption()))
        assertEquals(1, run.ayahIndex)
        assertEquals(0, run.revealed) // not the first ayah: its first word must be recalled too
        assertFalse(run.finished)
    }

    @Test fun guessingAlmostNeverPasses() {
        var passes = 0
        repeat(2000) { seed ->
            val rnd = Random(seed)
            val run = ChainRun(ikhlas, pool, Random(seed + 7))
            var steps = 0
            // A guesser tapping at random, with a cap so a lucky streak can't loop forever.
            while (!run.finished && steps < 60) { run.pick(run.options.random(rnd)); steps++ }
            if (run.finished) passes++
        }
        assertTrue("random tapping passed $passes/2000 times", passes <= 20)
    }

    @Test fun optionsChangeBetweenAttempts() {
        val a = ChainRun(ikhlas, pool, Random(10)).options
        val b = ChainRun(ikhlas, pool, Random(11)).options
        assertNotEquals(a, b)
    }
}
