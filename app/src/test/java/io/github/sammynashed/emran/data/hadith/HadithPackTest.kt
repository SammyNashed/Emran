package io.github.sammynashed.emran.data.hadith

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the shipped packs with the real decoder, so a change to the layout in
 * tools/build_hadith_packs.py that the Kotlin side does not follow fails here rather
 * than on a phone.
 */
class HadithPackTest {
    private fun open(id: String) =
        HadithPack.open(id, FilePackSource(File("src/main/assets/hadith/$id.hdp")))

    @Test
    fun nawawiHasFortyTwoHadithAndTheFamousFirst() {
        val pack = open("nawawi40")
        assertEquals(42, pack.hadithCount)
        val list = pack.load(pack.chapters.single())
        assertEquals(42, list.size)
        assertEquals(1, list.first().number)
        assertEquals(42, list.last().number)
        assertTrue(list.first().arabic.contains("أَمِيرِ الْمُؤْمِنِينَ أَبِي حَفْصٍ"))
        assertTrue(list.first().text.contains("Actions are (judged) by motives"))
        assertTrue(list.first().narrator.startsWith("It is narrated on the authority of Amirul Mu'minin"))
    }

    @Test
    fun riyadChapterCountsAddUpAndNumbersAreContiguous() {
        val pack = open("riyad")
        assertEquals(1896, pack.hadithCount)
        var seen = 0
        var expected = 1
        for (ch in pack.chapters) {
            val list = pack.load(ch)
            assertEquals("chapter ${ch.id} count", ch.count, list.size)
            assertEquals(ch.firstNumber, list.first().number)
            assertEquals(ch.lastNumber, list.last().number)
            for (h in list) assertEquals(expected++, h.number)
            seen += list.size
        }
        assertEquals(1896, seen)
    }

    @Test
    fun chapterOfFindsTheRightChapter() {
        val pack = open("riyad")
        val ch = pack.chapterOf(1000)
        assertNotNull(ch)
        assertTrue(1000 in ch!!.firstNumber..ch.lastNumber)
        assertNull(pack.chapterOf(99999))
    }

    @Test
    fun arabicSearchFoldsDiacriticsAndAlefForms() {
        assertEquals(
            ArabicText.normalize("إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ"),
            ArabicText.normalize("انما الاعمال بالنيات")
        )
        assertEquals(ArabicText.normalize("الصَّلَاةِ"), ArabicText.normalize("الصلاه"))
    }

    @Test
    fun theNiyyahHadithIsFoundByABareArabicQuery() {
        val pack = open("nawawi40")
        val q = ArabicText.normalize("الأعمال بالنيات")
        val hit = pack.load(pack.chapters.single()).firstOrNull { ArabicText.normalize(it.arabic).contains(q) }
        assertEquals(1, hit?.number)
    }
}
