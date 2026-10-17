package io.github.sammynashed.emran.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * What each prayer is called on screen. On Friday the midday prayer is Jumuʿah, not Dhuhr:
 * every label (Home, Prayer Times, widgets, notifications) goes through here so they agree.
 * Keys stay "Dhuhr" everywhere else (alarms, prefs), only the name changes.
 */
object PrayerNames {
    fun isJumuah(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.FRIDAY

    /** [key] is "Fajr".."Isha" (a " (Tomorrow)" suffix is understood); [date] is the day of THAT prayer. */
    fun name(key: String, ar: Boolean, date: LocalDate = LocalDate.now()): String {
        val tomorrow = key.endsWith(" (Tomorrow)")
        val k = key.removeSuffix(" (Tomorrow)")
        val day = if (tomorrow) date.plusDays(1) else date
        return when (k) {
            "Fajr" -> if (ar) "الفجر" else "Fajr"
            "Sunrise" -> if (ar) "الشروق" else "Sunrise"
            "Dhuhr" -> if (isJumuah(day)) (if (ar) "الجمعة" else "Jumu'ah") else (if (ar) "الظهر" else "Dhuhr")
            "Asr" -> if (ar) "العصر" else "Asr"
            "Maghrib" -> if (ar) "المغرب" else "Maghrib"
            "Isha" -> if (ar) "العشاء" else "Isha"
            else -> k
        }
    }
}
