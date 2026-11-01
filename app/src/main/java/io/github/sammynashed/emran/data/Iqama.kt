package io.github.sammynashed.emran.data

import android.content.SharedPreferences
import java.time.LocalDateTime

/**
 * Iqama: the minutes between a prayer's adhan and the call that starts the congregation.
 *
 * Mosques differ and Emran cannot know yours, so these are only starting points (the usual
 * Levant practice) that every person sets for their own mosque. Stored per prayer in
 * `iqama_<Key>`. Jumu'ah has no fixed gap (it follows the khutba), so Friday's Dhuhr has none.
 */
object Iqama {
    val KEYS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    val DEFAULTS = mapOf("Fajr" to 25, "Dhuhr" to 20, "Asr" to 20, "Maghrib" to 5, "Isha" to 20)
    const val MAX_MINUTES = 90

    fun load(prefs: SharedPreferences): Map<String, Int> =
        KEYS.associateWith { prefs.getInt("iqama_$it", DEFAULTS.getValue(it)).coerceIn(0, MAX_MINUTES) }

    /** The iqama moment for a prayer whose adhan is [adhan], or null when it has none (Sunrise, Jumu'ah). */
    fun at(key: String, adhan: LocalDateTime, minutes: Map<String, Int>): LocalDateTime? {
        val gap = minutes[key] ?: return null
        if (key == "Dhuhr" && PrayerNames.isJumuah(adhan.toLocalDate())) return null
        return adhan.plusMinutes(gap.toLong())
    }

}
