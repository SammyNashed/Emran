package io.github.sammynashed.emran.data.hadith

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HadithPartsTest {
    private fun all(id: String) = HadithPack.open(id, FilePackSource(File("src/main/assets/hadith/$id.hdp")))
        .let { p -> p.chapters.flatMap { p.load(it) } }

    private fun ref(id: String, r: String) = all(id).first { it.ref == r }

    @Test
    fun aPlainChainSplitsIntoItsNarrators() {
        val p = HadithParts.of(ref("bukhari", "6018").arabic)
        assertEquals(listOf("قُتَيْبَةُ بْنُ سَعِيدٍ", "أَبُو الأَحْوَصِ", "أَبِي حَصِينٍ", "أَبِي صَالِحٍ", "أَبِي هُرَيْرَةَ"), p.chain)
        assertTrue(p.lead, p.lead.contains("ﷺ"))
        assertTrue(p.words.startsWith("مَنْ كَانَ يُؤْمِنُ"))
        assertTrue(p.words.endsWith("لِيَصْمُتْ"))
    }

    @Test
    fun aBranchingChainStaysAParagraphWithAllItsWords() {
        val source = ref("muslim", "101").arabic
        val p = HadithParts.of(source)
        assertTrue(p.chain.toString(), p.chain.isEmpty())
        assertTrue(p.isnad, ArabicText.normalize(p.isnad).contains(ArabicText.normalize("كلاهما")))
        assertTrue(p.words, ArabicText.normalize(p.words).contains(ArabicText.normalize("من غشنا")))
    }

    /** Over every hadith: nothing but transmission words and honorifics may go missing. */
    @Test
    fun noWordOfAnyHadithIsLost() {
        val allowed = setOf("حدثنا", "حدثني", "اخبرنا", "اخبرني", "انبانا", "سمعت", "عن", "قال", "وحدثنا", "وحدثني", "واخبرنا", "واخبرني",
            "رضي", "رضى", "الله", "عنه", "عنها", "عنهما", "عنهم", "صلي", "صلى", "عليه", "وسلم", "ﷺ")
        val report = StringBuilder()
        var total = 0; var split = 0; var paragraph = 0; var plain = 0
        val strip = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
        fun w(s: String) = strip.replace(s, "").split(' ', '،', '"', ':', '‏', '.', '-', 'ـ').map { t -> t.filter { it.isLetter() } }.filter { it.isNotEmpty() }
        val sampleEvery = 137
        val lost = ArrayList<String>()
        val reasons = java.util.TreeMap<String, Int>()
        for (id in listOf("bukhari", "muslim", "abudawud", "tirmidhi", "nasai", "ibnmajah")) {
            for (h in all(id)) {
                total++
                val p = HadithParts.of(h.arabic) { why -> reasons.merge(why.substringBefore(": ").let { if (it.startsWith("word ")||it.startsWith("waw ")) it else it.take(14) }, 1, Int::plus) }
                when { p.chain.isNotEmpty() -> split++; p.isnad.isNotBlank() -> paragraph++; else -> plain++ }
                val shown = w(HadithParts.withSign(listOf(p.chain.joinToString(" "), p.lead, p.isnad, p.words).joinToString(" "))).groupingBy { it }.eachCount()
                val source = w(HadithParts.withSign(h.arabic)).groupingBy { it }.eachCount()
                for ((word, n) in source) {
                    val missing = n - (shown[word] ?: 0)
                    if (missing > 0 && word.replace('أ', 'ا').replace('ى', 'ي') !in allowed.map { it.replace('أ', 'ا').replace('ى', 'ي') }) {
                        lost += "$id ${h.ref} lost \"$word\""
                    }
                }
                if (p.chain.isNotEmpty() && total % sampleEvery == 0) report.append("$id ${h.ref}\t${p.chain.joinToString(" | ")}\t=> ${p.lead}\n")
                if (p.chain.any { it.split(' ').size >= 6 }) report.append("LONGNAME $id ${h.ref}\t${p.chain.joinToString(" | ")}\n")
            }
        }
        File("build/hadith-parts-report.txt").writeText("total $total chain $split paragraph $paragraph plain $plain\n" + reasons.entries.sortedByDescending { it.value }.take(40).joinToString(" ; ") { "${it.key}=${it.value}" } + "\nLOST ${lost.size}: ${lost.take(30)}\n$report")
        assertTrue("lost words: ${lost.take(10)}", lost.isEmpty())
        assertTrue("the parser stopped recognising chains", split > total / 12)
    }
}
