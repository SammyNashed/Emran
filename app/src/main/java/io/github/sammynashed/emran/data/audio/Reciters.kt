package io.github.sammynashed.emran.data.audio

import android.content.Context

/**
 * The voices Emran offers for Quran audio. [folder] is the reciter's folder on EveryAyah, where the
 * ayat are downloaded from (one MP3 per ayah, so the reader always knows which ayah is playing).
 * The short preview (Al-Fatiha 1:2–4) ships inside the app so a voice can be heard before any
 * download. [bytesPerWord] was measured on Surat al-Mulk and only feeds size estimates.
 */
data class Reciter(val id: String, val ar: String, val en: String, val folder: String, val bytesPerWord: Int) {
    val previewAsset get() = "reciters/$id.ogg"
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
