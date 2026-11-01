package io.github.sammynashed.emran.data.hadith

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reads the shipped packs with the real decoder, so a layout change in tools/build_hadith_library.py that the
 * Kotlin side does not follow fails here, not on a phone. The numbers below are the ones people actually cite: if
 * one of these fails, the app would be sending people to the wrong hadith.
 */
class HadithPackTest {
    private fun open(id: String) =
        HadithPack.open(id, FilePackSource(File("src/main/assets/hadith/$id.hdp")))

    private fun all(id: String) = open(id).let { p -> p.chapters.flatMap { p.load(it) } }

    private fun refsContaining(id: String, phrase: String) =
        all(id).filter { ArabicText.normalize(it.arabic).contains(ArabicText.normalize(phrase)) }.map { it.ref }

    @Test
    fun bukhariFamousNumbersLandOnTheRightHadith() {
        assertTrue("1" in refsContaining("bukhari", "إنما الأعمال بالنيات"))
        assertTrue("6018" in refsContaining("bukhari", "فليقل خيرا أو ليصمت"))
        assertTrue("7563" in refsContaining("bukhari", "كلمتان حبيبتان"))
    }

    @Test
    fun muslimUsesAbdAlBaqiNumbers() {
        assertTrue("1907a" in refsContaining("muslim", "إنما الأعمال بالنية"))
        assertTrue("55a" in refsContaining("muslim", "الدين النصيحة"))
        assertTrue("45a" in refsContaining("muslim", "لا يؤمن أحدكم حتى يحب لأخيه"))
        assertTrue("101" in refsContaining("muslim", "من غشنا فليس منا"))
    }

    @Test
    fun theSunanShipOnlyHadithGradedSahih() {
        for (id in listOf("abudawud", "tirmidhi", "nasai", "ibnmajah")) {
            val list = all(id)
            assertTrue("$id is not empty", list.isNotEmpty())
            for (h in list) {
                assertEquals("$id ${h.ref}", Hadith.GRADE_ALBANI, h.grade)
                assertTrue("$id ${h.ref} has a number", h.ref.isNotBlank())
            }
        }
    }

    @Test
    fun bukhariAndMuslimCarryNoIndividualGrade() {
        for (id in listOf("bukhari", "muslim")) assertTrue(id, all(id).all { it.grade.isEmpty() })
    }

    @Test
    fun theLibraryHoldsExactlyTheApprovedBooks() {
        val names = File("src/main/assets/hadith").list()!!.filter { it.endsWith(".hdp") }.toSet()
        assertEquals(setOf("bukhari", "muslim", "abudawud", "tirmidhi", "nasai", "ibnmajah").map { "$it.hdp" }.toSet(), names)
    }

    @Test
    fun everyChapterIsConsistentAndEveryHadithHasText() {
        for (id in listOf("bukhari", "muslim", "abudawud", "tirmidhi", "nasai", "ibnmajah")) {
            val pack = open(id)
            var seen = 0
            val keys = HashSet<Int>()
            for (ch in pack.chapters) {
                val list = pack.load(ch)
                assertEquals("$id chapter ${ch.id}", ch.count, list.size)
                assertEquals(ch.firstNumber, list.first().number)
                assertEquals(ch.lastNumber, list.last().number)
                assertTrue("$id chapter ${ch.id} has an Arabic title", ch.titleAr.isNotBlank())
                for (h in list) {
                    assertTrue("$id ${h.number} has Arabic", h.arabic.isNotBlank())
                    assertTrue("$id ${h.number} is unique", keys.add(h.number))
                }
                seen += list.size
            }
            assertEquals(pack.hadithCount, seen)
        }
    }

    @Test
    fun anUnknownMuslimNumberIsLeftBlankNeverGuessed() {
        val list = all("muslim")
        assertTrue(list.any { it.ref.isBlank() })
        assertTrue(list.filter { it.ref.isNotBlank() }.all { Regex("""\d+[a-z]?""").matches(it.ref) })
    }

    @Test
    fun referenceSearchMatchesWhatPeopleType() {
        fun h(ref: String) = Hadith(1, "a", "", "t", ref)
        assertTrue(h("6018").matchesRef("6018"))
        assertFalse(h("60180").matchesRef("6018"))
        assertTrue(h("1907a").matchesRef("1907"))
        assertTrue(h("1907a").matchesRef("1907a"))
        assertFalse(h("1907b").matchesRef("1907a"))
        assertTrue(h("5709-5712").matchesRef("5711"))
        assertFalse(h("5709-5712").matchesRef("5713"))
        assertFalse(h("").matchesRef("1"))
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
    fun oldRiyadBookmarksMapToTheStandardNumbers() {
        assertEquals(1, HadithBookmarks.riyadStandardNumber(1218))
        assertEquals(679, HadithBookmarks.riyadStandardNumber(1896))
        assertEquals(680, HadithBookmarks.riyadStandardNumber(1))
        assertEquals(1896, HadithBookmarks.riyadStandardNumber(1217))
    }

    @Test
    fun savedHadithMoveToTheNewLibraryAndRemovedOnesAreMarked() {
        val table = File("src/main/assets/hadith/migration_v2.tsv").useLines { it.toList() }
        val first = table.first { it.startsWith("bukhari\t1\t") }.split('\t')
        val list = listOf<Bookmark>(
            Bookmark.HadithRef("bukhari", 1, 1, 5L),
            Bookmark.HadithRef("riyad", 3, 40, 6L),
            Bookmark.AyahRef(2, 255, 7L),
        )
        val out = HadithBookmarks.migrateToLibraryV2(list, table.asSequence())
        val moved = out[0] as Bookmark.HadithRef
        assertEquals(first[2].toInt(), moved.chapterId)
        assertEquals(first[3].toInt(), moved.number)
        assertEquals(5L, moved.savedAt)
        val shelved = out[1] as Bookmark.HadithRef
        assertEquals(HadithBookmarks.REMOVED_CHAPTER, shelved.chapterId)
        assertEquals("riyad", shelved.bookId)
        assertEquals(list[2], out[2])
    }

    @Test
    fun aMovedBookmarkStillOpensTheSameHadith() {
        val table = File("src/main/assets/hadith/migration_v2.tsv").useLines { it.toList() }
        val pack = open("bukhari")
        val line = table.first { it.startsWith("bukhari\t1\t") }.split('\t')
        val h = pack.load(pack.chapter(line[2].toInt())!!)
            .first { it.number == line[3].toInt() }
        assertTrue(ArabicText.normalize(h.arabic).contains(ArabicText.normalize("إنما الأعمال بالنيات")))
    }
}
