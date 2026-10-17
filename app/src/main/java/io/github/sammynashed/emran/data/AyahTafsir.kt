package io.github.sammynashed.emran.data

import android.content.Context
import org.json.JSONObject

/**
 * The explanation of any ayah, offline: Al-Mukhtasar fi Tafsir al-Quran al-Karim (Tafsir Center for Quranic
 * Studies) in Arabic and in its own English edition, and Saheeh International's translation, one small
 * file per surah in assets/ayah/ (built by tools/build_daily_ayat.py from QuranEnc.com and alquran.cloud).
 */
object AyahTafsir {
    class Entry(
        val tafsirAr: String,
        val tafsirEn: String,
        val translation: String,
        /** Set when Al-Mukhtasar explains this ayah together with its neighbour(s), e.g. 3..4. */
        val group: IntRange?,
    )

    private val cache = object : android.util.LruCache<Int, JSONObject>(3) {}

    private fun surah(context: Context, s: Int): JSONObject? = cache.get(s) ?: runCatching {
        JSONObject(context.assets.open("ayah/$s.json").bufferedReader().use { it.readText() })
    }.getOrNull()?.also { cache.put(s, it) }

    /** Reads a file of a few hundred KB at most; call off the main thread. */
    fun get(context: Context, s: Int, a: Int): Entry? {
        val o = surah(context, s) ?: return null
        val i = a - 1
        val ar = o.optJSONArray("ar") ?: return null
        if (i !in 0 until ar.length()) return null
        val g = o.optJSONObject("g")?.optJSONArray("$a")?.let { it.getInt(0)..it.getInt(1) }
        return Entry(
            tafsirAr = ar.optString(i),
            tafsirEn = o.optJSONArray("en")?.optString(i).orEmpty(),
            translation = o.optJSONArray("tr")?.optString(i).orEmpty(),
            group = g,
        )
    }
}
