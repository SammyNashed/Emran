package io.github.sammynashed.emran.glance.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Text, names and formatting for the widget family.
 *
 * Two rules hold throughout:
 *
 *  - Labels follow the *app* language, not the device locale.
 *  - Digits stay Latin in both languages. Arabic-Indic numerals are an in-app
 *    preference; on a home screen, next to the launcher's own clock, Latin
 *    digits read better and were what was asked for.
 */
object WidgetText {

    fun isRtl(lang: String): Boolean = lang == "ar"

    // ---- Prayer names ----------------------------------------------------

    private val prayerAr = mapOf(
        "Fajr" to "الفجر",
        "Sunrise" to "الشروق",
        "Dhuhr" to "الظهر",
        "Asr" to "العصر",
        "Maghrib" to "المغرب",
        "Isha" to "العشاء"
    )

    fun prayerName(name: String?, lang: String): String {
        if (name.isNullOrBlank()) return ""
        return if (isRtl(lang)) prayerAr[name] ?: name else name
    }

    // ---- Hijri months ----------------------------------------------------

    private val hijriEn = listOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
        "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )

    private val hijriAr = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر",
        "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
        "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    /** [month] is 1-based, as produced by HijrahDate. */
    fun hijriMonth(month: Int?, lang: String): String {
        val idx = (month ?: 0) - 1
        val table = if (isRtl(lang)) hijriAr else hijriEn
        return table.getOrNull(idx) ?: ""
    }

    // ---- Static labels ---------------------------------------------------

    fun nextPrayer(lang: String) = if (isRtl(lang)) "الصلاة القادمة" else "NEXT PRAYER"
    fun today(lang: String) = if (isRtl(lang)) "صلوات اليوم" else "TODAY"
    fun hijriToday(lang: String) = if (isRtl(lang)) "التاريخ الهجري" else "HIJRI DATE"
    fun quran(lang: String) = if (isRtl(lang)) "تقدم القرآن" else "QURAN PROGRESS"
    fun pages(lang: String) = if (isRtl(lang)) "صفحة" else "pages"
    fun surahs(lang: String) = if (isRtl(lang)) "سورة" else "surahs"

    /** "3 khatmat completed" -- readings finished before the current cycle. */
    fun khatmat(count: Int, lang: String): String = if (isRtl(lang)) {
        when (count) {
            1 -> "ختمة مكتملة"
            2 -> "ختمتان مكتملتان"
            in 3..10 -> "$count ختمات مكتملة"
            else -> "$count ختمة مكتملة"
        }
    } else {
        if (count == 1) "1 khatmah completed" else "$count khatmat completed"
    }
    fun loading(lang: String) = if (isRtl(lang)) "جارٍ التحميل" else "Loading"
    fun hijriYearSuffix(lang: String) = if (isRtl(lang)) "هـ" else "AH"

    /** "in 1h 23m" / "بعد ١h.." -- Latin digits, Arabic unit words. */
    fun timeUntil(targetMillis: Long?, nowMillis: Long, lang: String): String {
        if (targetMillis == null) return ""
        val deltaMin = ((targetMillis - nowMillis) / 60000L).toInt()
        if (deltaMin < 0) return ""
        val h = deltaMin / 60
        val m = deltaMin % 60
        return if (isRtl(lang)) {
            when {
                h > 0 && m > 0 -> "بعد $h س $m د"
                h > 0 -> "بعد $h س"
                else -> "بعد $m د"
            }
        } else {
            when {
                h > 0 && m > 0 -> "in ${h}h ${m}m"
                h > 0 -> "in ${h}h"
                else -> "in ${m}m"
            }
        }
    }

    /**
     * Clock time. Honours the device's 24-hour setting instead of hardcoding
     * 12-hour, and always uses Latin digits via [Locale.US].
     */
    fun clock(epochMillis: Long?, use24Hour: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        if (epochMillis == null) return "--:--"
        return try {
            val pattern = if (use24Hour) "HH:mm" else "h:mm a"
            DateTimeFormatter.ofPattern(pattern, Locale.US)
                .withZone(zone)
                .format(Instant.ofEpochMilli(epochMillis))
        } catch (e: Exception) {
            "--:--"
        }
    }
}
