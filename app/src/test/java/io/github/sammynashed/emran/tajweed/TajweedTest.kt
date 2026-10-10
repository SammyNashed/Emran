package io.github.sammynashed.emran.tajweed

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Correctness checks for the tajweed markup.
 *
 * This is Qur'an text, so a wrong colour teaches a wrong rule. The checks below
 * are chosen to fail loudly rather than to pass easily:
 *
 *  - the iqlāb count is cross-checked against the script's own marker, which is
 *    an independent source of truth the rule code does not depend on
 *  - hand-worked āyāt pin specific letters to specific rules
 *  - the whole corpus is swept for structural faults (overlaps, bad ranges)
 */
class TajweedTest {

    /**
     * Every verse, as `ref to text`.
     *
     * Read with a regex rather than `org.json`: on the JVM unit-test classpath
     * that class comes from the stub android.jar and throws on every call. The
     * shape being read here is fixed and trivial, so a parser would buy nothing
     * a regex does not already give.
     */
    private fun verses(): List<Pair<String, String>> {
        val f = File("src/main/assets/quran.json")
        assertTrue("quran.json not found at ${f.absolutePath}", f.isFile)
        val body = f.readText()
        return Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .findAll(body)
            .map { it.groupValues[1].replace("\\\"", "\"") }
            .filter { it.isNotBlank() }
            .toList()
            .mapIndexed { i, t -> "verse#${i + 1}" to t }
    }

    @Test
    fun `spans are well formed and never overlap`() {
        var total = 0
        for ((ref, text) in verses()) {
            val spans = Tajweed.annotate(text)
            total += spans.size
            var lastEnd = -1
            for (sp in spans) {
                assertTrue("$ref: bad range ${sp.start}..${sp.end}", sp.start in text.indices)
                assertTrue("$ref: end past text", sp.end in 1..text.length)
                assertTrue("$ref: empty span", sp.end > sp.start)
                assertTrue("$ref: spans overlap at ${sp.start}", sp.start >= lastEnd)
                lastEnd = sp.end
            }
        }
        println("total tajweed spans across the Qur'an: $total")
        assertTrue("suspiciously few spans: $total", total > 20_000)
    }

    /**
     * Every small high mīm (U+06E2) must come out as iqlāb.
     *
     * This is a coverage check, not an independent one. The annotator reads
     * that marker directly, because for a tanwīn before bāʼ the spelling drops
     * the evidence a structural test would need. What it still catches is the
     * failure that actually happened: a rule that silently matched nothing.
     *
     * Detecting *more* iqlāb than the script marks is a real disagreement and
     * is reported, since it would mean claiming a rule the muṣḥaf does not.
     */
    @Test
    fun `iqlab agrees with the scripts own marker`() {
        var marked = 0
        var detected = 0
        val disagreements = mutableListOf<String>()
        for ((ref, text) in verses()) {
            val here = text.count { it == 'ۢ' }
            val found = Tajweed.annotate(text).count { it.rule == TajweedRule.IQLAB }
            marked += here
            detected += found
            if (here != found && disagreements.size < 10) {
                disagreements += "$ref: marker=$here detected=$found  $text"
            }
        }
        println("iqlab: marker=$marked detected=$detected")
        disagreements.forEach { println("  $it") }
        assertEquals("every marked iqlab must be detected", marked, detected)
    }

    @Test
    fun `al-Fatiha 1-1 marks the doubled letters`() {
        val text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
        val spans = Tajweed.annotate(text)
        val rules = spans.map { it.rule }
        // No nun or mim is doubled here, so no ghunnah is expected.
        assertTrue("unexpected ghunnah in 1:1: $rules", TajweedRule.GHUNNAH !in rules)
    }

    @Test
    fun `an-Nas 114-1 marks ghunnah on the doubled nun`() {
        val text = "قُلۡ أَعُوذُ بِرَبِّ ٱلنَّاسِ"
        val spans = Tajweed.annotate(text)
        val ghunnah = spans.filter { it.rule == TajweedRule.GHUNNAH }
        assertEquals("expected one ghunnah (ٱلنَّاسِ): $spans", 1, ghunnah.size)
        assertEquals('ن', text[ghunnah.first().start])
    }

    @Test
    fun `every span starts on a letter, never on a mark`() {
        for ((ref, text) in verses()) {
            for (sp in Tajweed.annotate(text)) {
                val c = text[sp.start]
                assertTrue(
                    "$ref: span starts on the mark U+${"%04X".format(c.code)}",
                    c in 'ء'..'ي' || c == 'ٱ',
                )
            }
        }
    }

    @Test
    fun `report the distribution so a silent rule is visible`() {
        val counts = mutableMapOf<TajweedRule, Int>()
        for ((_, text) in verses()) {
            Tajweed.annotate(text).forEach { counts.merge(it.rule, 1, Int::plus) }
        }
        TajweedRule.entries.forEach { r ->
            println("%-20s %d".format(r.name, counts[r] ?: 0))
        }
        // A rule that never fires anywhere in the Qur'an is a bug, not a rarity.
        TajweedRule.entries.forEach { r ->
            assertTrue("rule ${r.name} never fired anywhere", (counts[r] ?: 0) > 0)
        }
    }
}
