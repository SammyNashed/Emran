package io.github.sammynashed.emran.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.time.LocalDate

/**
 * The ayah of the day, shared by Home, the explanation page and the widget.
 *
 * Every phone walks the famous ayat in its OWN shuffled order (seeded once per install), so friends see
 * different ayat on the same day, nobody sees a repeat until the whole list has been shown, and two ayat
 * from the same passage never fall on consecutive days. The user can also pick any ayah of the Qur'an as
 * today's ayah; that choice lasts until midnight.
 */
object DailyAyat {
    /**
     * Famous ayat that stand on their own (tools/build_daily_ayat.py checks each one: at most 30 words,
     * explained by Al-Mukhtasar on its own; regenerate assets/daily_ayat_ar.json if this changes).
     */
    val LIST = listOf(
        1 to 2, 1 to 5, 1 to 6, 2 to 2, 2 to 32, 2 to 43, 2 to 45, 2 to 110, 2 to 115, 2 to 127,
        2 to 148, 2 to 152, 2 to 153, 2 to 155, 2 to 156, 2 to 157, 2 to 163, 2 to 183, 2 to 186, 2 to 195,
        2 to 201, 2 to 208, 2 to 238, 2 to 245, 2 to 250, 2 to 256, 2 to 263, 2 to 269, 2 to 280, 3 to 8,
        3 to 9, 3 to 31, 3 to 92, 3 to 102, 3 to 104, 3 to 110, 3 to 133, 3 to 134, 3 to 139, 3 to 147,
        3 to 160, 3 to 169, 3 to 173, 3 to 185, 3 to 190, 3 to 191, 3 to 193, 3 to 200, 4 to 28, 4 to 86,
        4 to 110, 4 to 147, 5 to 8, 5 to 35, 6 to 17, 6 to 103, 6 to 160, 6 to 162, 7 to 23, 7 to 31,
        7 to 55, 7 to 56, 7 to 180, 7 to 199, 7 to 201, 7 to 204, 7 to 205, 8 to 2, 8 to 30, 8 to 33,
        8 to 46, 9 to 51, 9 to 105, 9 to 119, 9 to 128, 9 to 129, 10 to 57, 10 to 58, 10 to 62, 11 to 6,
        11 to 114, 11 to 115, 12 to 18, 12 to 53, 12 to 86, 12 to 87, 12 to 92, 13 to 24, 13 to 28, 13 to 29,
        13 to 39, 14 to 7, 14 to 24, 14 to 27, 14 to 34, 14 to 40, 14 to 41, 15 to 9, 15 to 49, 15 to 97,
        15 to 98, 15 to 99, 16 to 18, 16 to 43, 16 to 53, 16 to 90, 16 to 96, 16 to 97, 16 to 125, 16 to 127,
        16 to 128, 17 to 9, 17 to 23, 17 to 24, 17 to 32, 17 to 36, 17 to 37, 17 to 44, 17 to 70, 17 to 79,
        17 to 80, 17 to 81, 17 to 82, 17 to 85, 18 to 10, 18 to 46, 18 to 107, 18 to 109, 19 to 4, 19 to 65,
        19 to 96, 20 to 2, 20 to 5, 20 to 8, 20 to 14, 20 to 25, 20 to 26, 20 to 46, 20 to 114, 20 to 124,
        20 to 132, 21 to 35, 21 to 47, 21 to 83, 21 to 87, 21 to 89, 21 to 107, 22 to 32, 22 to 37, 22 to 77,
        23 to 1, 23 to 2, 23 to 3, 23 to 97, 23 to 98, 23 to 115, 23 to 118, 25 to 58, 25 to 63, 25 to 70,
        25 to 74, 26 to 62, 26 to 78, 26 to 80, 26 to 83, 26 to 88, 27 to 62, 28 to 24, 28 to 56, 28 to 88,
        29 to 2, 29 to 45, 29 to 57, 29 to 64, 29 to 69, 30 to 21, 30 to 41, 30 to 60, 31 to 13, 31 to 14,
        31 to 17, 31 to 18, 31 to 19, 32 to 16, 32 to 17, 33 to 3, 33 to 21, 33 to 41, 33 to 42, 33 to 56,
        33 to 70, 33 to 71, 35 to 2, 35 to 5, 35 to 15, 35 to 28, 36 to 58, 36 to 82, 36 to 83, 37 to 180,
        37 to 181, 37 to 182, 38 to 29, 39 to 9, 39 to 10, 39 to 18, 39 to 36, 39 to 53, 39 to 54, 40 to 44,
        40 to 55, 40 to 60, 40 to 65, 41 to 30, 41 to 34, 41 to 46, 42 to 11, 42 to 19, 42 to 25, 42 to 30,
        42 to 36, 42 to 40, 42 to 43, 43 to 67, 46 to 13, 47 to 7, 47 to 19, 48 to 1, 49 to 10, 49 to 13,
        50 to 16, 50 to 18, 51 to 21, 51 to 22, 51 to 55, 51 to 56, 51 to 58, 52 to 48, 53 to 3, 53 to 4,
        53 to 39, 53 to 42, 54 to 17, 54 to 49, 55 to 13, 55 to 26, 55 to 27, 55 to 60, 56 to 79, 57 to 3,
        57 to 16, 59 to 18, 59 to 21, 59 to 24, 61 to 2, 61 to 3, 62 to 9, 62 to 10, 64 to 11, 64 to 16,
        65 to 3, 67 to 1, 67 to 2, 67 to 13, 67 to 14, 67 to 15, 68 to 4, 70 to 5, 71 to 10, 73 to 8,
        73 to 9, 74 to 7, 75 to 22, 76 to 9, 82 to 6, 84 to 6, 85 to 14, 87 to 1, 87 to 14, 87 to 16,
        87 to 17, 89 to 27, 89 to 28, 91 to 9, 91 to 10, 93 to 3, 93 to 4, 93 to 5, 93 to 7, 93 to 11,
        94 to 1, 94 to 4, 94 to 5, 94 to 6, 94 to 7, 94 to 8, 95 to 4, 96 to 1, 96 to 5, 96 to 19,
        97 to 1, 97 to 3, 97 to 5, 99 to 7, 99 to 8, 108 to 1, 108 to 2, 110 to 1, 110 to 3, 112 to 1,
        112 to 2, 112 to 3, 112 to 4, 113 to 1, 114 to 1,
    )

    private const val PREFS = "emran_home"

    /** Bumped when the user picks an ayah, so Home and the explanation page re-read it at once. */
    val version = mutableIntStateOf(0)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile private var order: List<Pair<Int, Int>>? = null

    /** This phone's own order of [LIST]: shuffled by a per-install seed, with neighbouring ayat kept apart. */
    private fun order(context: Context): List<Pair<Int, Int>> = order ?: synchronized(this) {
        order ?: run {
            val p = prefs(context)
            if (!p.contains("ayah_order_seed")) p.edit().putLong("ayah_order_seed", kotlin.random.Random.nextLong()).commit()
            spread(LIST.shuffled(kotlin.random.Random(p.getLong("ayah_order_seed", 0L))))
        }.also { order = it }
    }

    /** True when [a] and [b] come from the same passage (same surah, at most 3 ayat apart). */
    private fun near(a: Pair<Int, Int>, b: Pair<Int, Int>) = a.first == b.first && kotlin.math.abs(a.second - b.second) <= 3

    /**
     * Moves items so no two consecutive days (including the wrap from the last day back to the first) share a
     * passage: each clash is swapped with the first item whose swap leaves all six affected spots clean.
     * Checked over 5,000 random seeds with zero clashes left.
     */
    internal fun spread(list: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val out = list.toMutableList()
        val n = out.size
        if (n < 8) return out
        fun clash(k: Int): Boolean {
            val i = Math.floorMod(k, n)
            return near(out[Math.floorMod(i - 1, n)], out[i]) || near(out[i], out[(i + 1) % n])
        }
        fun swap(i: Int, j: Int) { val t = out[i]; out[i] = out[j]; out[j] = t }
        repeat(10) {
            var moved = false
            for (i in 0 until n) {
                if (!near(out[Math.floorMod(i - 1, n)], out[i])) continue
                for (j in 0 until n) {
                    if (j == i) continue
                    swap(i, j)
                    if (!clash(i) && !clash(j) && !clash(i - 1) && !clash(j - 1) && !clash(i + 1) && !clash(j + 1)) { moved = true; break }
                    swap(i, j)
                }
            }
            if (!moved) return out
        }
        return out
    }

    /** (surah, ayah) for [day] on this phone: today's pick if the user chose one, else the next in this phone's order. */
    fun forDay(context: Context, day: LocalDate): Pair<Int, Int> {
        picked(context, day)?.let { return it }
        val o = order(context)
        return o[Math.floorMod(day.toEpochDay(), o.size.toLong()).toInt()]
    }

    /** The ayah the user chose for [day], if any. */
    fun picked(context: Context, day: LocalDate): Pair<Int, Int>? {
        val p = prefs(context)
        if (p.getLong("ayah_pick_day", Long.MIN_VALUE) != day.toEpochDay()) return null
        val s = p.getInt("ayah_pick_surah", 0)
        val a = p.getInt("ayah_pick_ayah", 0)
        return if (s in 1..114 && a >= 1) s to a else null
    }

    /** Makes (surah, ayah) today's ayah until midnight; null goes back to the daily one. */
    fun pick(context: Context, surah: Int?, ayah: Int?) {
        val e = prefs(context).edit()
        if (surah == null || ayah == null) e.remove("ayah_pick_day").remove("ayah_pick_surah").remove("ayah_pick_ayah")
        else e.putLong("ayah_pick_day", LocalDate.now().toEpochDay()).putInt("ayah_pick_surah", surah).putInt("ayah_pick_ayah", ayah)
        e.commit()
        version.intValue++
    }

    class Text(val text: String, val surahAr: String, val surahEn: String)

    /**
     * The ayah's words and surah names from assets/daily_ayat_ar.json (a few KB, built from
     * quran.json for exactly this list), so Home and the widget never parse the whole Quran
     * just to show one ayah. Falls back to the full text if an ayah is ever missing there.
     */
    suspend fun text(context: android.content.Context, surah: Int, ayah: Int): Text {
        runCatching {
            val o = context.assets.open("daily_ayat_ar.json").bufferedReader().use { org.json.JSONObject(it.readText()) }
                .optJSONObject("$surah:$ayah")
            if (o != null) return Text(o.getString("text"), o.getString("surahAr"), o.getString("surahEn"))
        }
        val repo = io.github.sammynashed.emran.data.repository.QuranRepository(context.applicationContext)
        val ch = repo.getChapters().first { it.id == surah }
        return Text(repo.getSurahVerses(surah).verses[ayah - 1].text, ch.name, ch.transliteration)
    }
}
