package io.github.sammynashed.emran.ui.share

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.github.sammynashed.emran.data.hadith.FilePackSource
import io.github.sammynashed.emran.data.hadith.Hadith
import io.github.sammynashed.emran.data.hadith.HadithBook
import io.github.sammynashed.emran.data.hadith.HadithPack
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Draws real pictures with the real renderer, so nothing that has to fit can be judged only by reading code. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PaperShareTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val out = File("build/share-samples").apply { mkdirs() }
    private val titles = mapOf(
        "bukhari" to ("Sahih al-Bukhari" to "صحيح البخاري"),
        "muslim" to ("Sahih Muslim" to "صحيح مسلم"),
        "ibnmajah" to ("Sahih selection from Sunan Ibn Majah" to "مختارات صحيحة من سنن ابن ماجه"),
    )
    private fun book(id: String) = titles.getValue(id).let { HadithBook(id, it.first, it.second, true, 0, 0, 0, "") }
    private fun all(id: String) = HadithPack.open(id, FilePackSource(File("src/main/assets/hadith/$id.hdp"))).let { p -> p.chapters.flatMap { p.load(it) } }
    private fun find(id: String, ref: String) = all(id).first { it.ref == ref }

    private fun keep(name: String, files: List<File>?) {
        assertNotNull(name, files)
        files!!.forEachIndexed { i, f -> f.copyTo(File(out, "$name-${i + 1}.jpg"), overwrite = true) }
    }

    @Test fun hadithSamples() {
        keep("a-6018", PaperShare.renderHadith(context, book("bukhari"), "ar", find("bukhari", "6018")))
        keep("b-6094", PaperShare.renderHadith(context, book("bukhari"), "ar", find("bukhari", "6094")))
        keep("c-101", PaperShare.renderHadith(context, book("muslim"), "ar", find("muslim", "101")))
        keep("d-8a", PaperShare.renderHadith(context, book("muslim"), "ar", find("muslim", "8a")))
        keep("e-majah", PaperShare.renderHadith(context, book("ibnmajah"), "ar", all("ibnmajah")[400]))
        keep("f-en", PaperShare.renderHadith(context, book("bukhari"), "en", find("bukhari", "6018")))
        keep("j-8b-en", PaperShare.renderHadith(context, book("muslim"), "en", find("muslim", "8b")))
        keep("k-8a-en", PaperShare.renderHadith(context, book("muslim"), "en", find("muslim", "8a")))
        keep("l-majah-en", PaperShare.renderHadith(context, book("ibnmajah"), "en", all("ibnmajah")[400]))
        keep("m-8b-ar", PaperShare.renderHadith(context, book("muslim"), "ar", find("muslim", "8b")))
    }

    @Test fun dhikrSamples() {
        val ik = "قُلْ هُوَ اللَّهُ أَحَدٌ ۝ اللَّهُ الصَّمَدُ ۝ لَمْ يَلِدْ وَلَمْ يُولَدْ ۝ وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ"
        keep("g-morning", PaperShare.renderDhikr(context, "morning", "أذكار الصباح", ik, null, "القرآن، سورة الإخلاص", "ar"))
        keep("h-evening", PaperShare.renderDhikr(context, "evening", "أذكار المساء", ik, null, "القرآن، سورة الإخلاص", "ar"))
        keep("i-prayer", PaperShare.renderDhikr(context, "prayer", "أذكار بعد الصلاة", "سُبْحَانَ اللَّهِ", null, "صحيح مسلم ٥٩٧", "ar"))
        keep("n-morning-en", PaperShare.renderDhikr(context, "morning", "Morning adhkar", ik, "Say: He is Allah, the One. Allah, the Eternal Refuge. He neither begets nor is born, nor is there to Him any equivalent.", "Quran, Surah Al-Ikhlas", "en"))
    }

    @Test fun theCaptionIsOnlyWhatEmranIsAndTheLink() {
        for (lang in listOf("ar", "en")) {
            val lines = ShareCard.pitchCaption(lang).lines()
            assertEquals(2, lines.size)
            assertEquals("https://t.me/emran_app", lines[1])
        }
    }

    /** Whatever the length: every picture is made, or it goes as full text. Nothing is cut. */
    @Test fun theLongestHadithIsNeverCut() {
        val longest = all("muslim").maxByOrNull { it.arabic.length }!!
        assertTrue(longest.arabic.length > 8000)
        val files = PaperShare.renderHadith(context, book("muslim"), "ar", longest)
        assertEquals("goes as plain text", null, files)
    }
}
