package io.github.sammynashed.emran.data.audio

import android.content.Context

/**
 * The voices Emran offers for Quran audio. [folder] is the reciter's folder on EveryAyah, where the
 * ayat are downloaded from (one MP3 per ayah, so the reader always knows which ayah is playing).
 * The short preview (Al-Fatiha 1:2–4) ships inside the app so a voice can be heard before any
 * download. [bytesPerWord] was measured on Surat al-Mulk and only feeds size estimates.
 *
 * A few voices exist only as whole-surah recordings (mp3quran.net, [surahUrl]): one file per surah,
 * no ayah timings, so they play the surah from its start and nothing is marked on the page.
 * [missing] lists the surahs such a reciter never recorded; [extra] gives the file of a surah that
 * lives somewhere other than [surahUrl].
 */
data class Reciter(
    val id: String,
    val ar: String,
    val en: String,
    val folder: String,
    val bytesPerWord: Int,
    val surahUrl: String? = null,
    val missing: Set<Int> = emptySet(),
    val extra: Map<Int, String> = emptyMap(),
) {
    val previewAsset get() = "reciters/$id.ogg"

    /** One file per surah, so no ayah-by-ayah marking, repeat or skipping. */
    val wholeSurah get() = surahUrl != null

    fun has(surah: Int) = surah in 1..114 && surah !in missing

    val surahCount get() = 114 - missing.size

    /** Where a whole-surah reciter's [surah] is downloaded from. */
    fun surahFileUrl(surah: Int) = extra[surah] ?: "$surahUrl%03d.mp3".format(surah)
}

object Reciters {
    val ALL = listOf(
        Reciter("alafasy", "مشاري العفاسي", "Mishary Alafasy", "Alafasy_128kbps", 21391),
        Reciter("muaiqly", "ماهر المعيقلي", "Maher Al-Muaiqly", "MaherAlMuaiqly128kbps", 13353),
        Reciter("dosari", "ياسر الدوسري", "Yasser Al-Dosari", "Yasser_Ad-Dussary_128kbps", 18180),
        Reciter("juhany", "عبد الله عواد الجهني", "Abdullah Awad Al-Juhany", "Abdullaah_3awwaad_Al-Juhaynee_128kbps", 14424),
        Reciter("shatri", "أبو بكر الشاطري", "Abu Bakr Al-Shatri", "Abu_Bakr_Ash-Shaatree_128kbps", 18354),
        Reciter("qatami", "ناصر القطامي", "Nasser Al-Qatami", "Nasser_Alqatami_128kbps", 14742),
        Reciter("ghamdi", "سعد الغامدي", "Saad Al-Ghamdi", "Ghamadi_40kbps", 5687),
        Reciter("husary", "محمود خليل الحصري", "Mahmoud Khalil Al-Husary", "Husary_128kbps", 32113),
        Reciter("abdulbasit", "عبد الباسط عبد الصمد", "Abdul Basit Abdus Samad", "Abdul_Basit_Murattal_64kbps", 11626),
        Reciter("minshawi", "محمد صدّيق المنشاوي", "Muhammad Siddiq Al-Minshawi", "Minshawy_Murattal_128kbps", 19796),
        // Asked for by a friend (2026-11). Not on EveryAyah. mp3quran.net has 109 surahs; the other five
        // come from way2quran.com (At-Talaq checked: all 12 ayat, ayah lengths match the usual pattern).
        // bytesPerWord: Al-Fatiha, al-Mujadila, al-Mulk and an-Naba measured together (files are 96–128 kbps).
        Reciter(
            "islam_sobhi", "إسلام صبحي", "Islam Sobhi", folder = "", bytesPerWord = 21030,
            surahUrl = "https://cdn.mp3quran.net/audio/islam-sobhi/r1/",
            extra = listOf(37, 39, 40, 45, 65).associateWith { "https://media.way2quran.com/islam-sobhi/hafs-an-asim/%03d.mp3".format(it) },
        ),
    )

    private const val PREFS = "emran_settings"
    private const val KEY = "quran_reciter"

    fun chosen(context: Context): Reciter {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return ALL.firstOrNull { it.id == id } ?: ALL.first()
    }

    fun choose(context: Context, reciter: Reciter) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, reciter.id).apply()
    }
}
