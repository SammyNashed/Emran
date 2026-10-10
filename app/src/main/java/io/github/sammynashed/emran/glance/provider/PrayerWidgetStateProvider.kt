package io.github.sammynashed.emran.glance.provider

import android.content.Context
import android.util.Log
import io.github.sammynashed.emran.data.PrayerTimeCalculator
import io.github.sammynashed.emran.glance.mapper.WidgetStateMapper
import io.github.sammynashed.emran.ui.theme.EmranTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import io.github.sammynashed.emran.glance.WidgetDebugLogger

class PrayerWidgetStateProvider(private val context: Context) {

    data class WidgetDomainData(
        val prayerData: WidgetStateMapper.PrayerDomainData,
        val calendarData: WidgetStateMapper.CalendarDomainData,
        val statsData: WidgetStateMapper.StatsDomainData,
        val themeData: WidgetStateMapper.ThemeDomainData,
        val localeData: WidgetStateMapper.LocaleDomainData
    )

    fun provideDomainData(): WidgetDomainData {
        WidgetDebugLogger.d("PrayerWidgetStateProvider.provideDomainData: entered | thread=${Thread.currentThread().name}")
        val prefs = context.getSharedPreferences("emran_settings", Context.MODE_PRIVATE)
        
        // Theme Data
        val themeName = prefs.getString("theme_preset", "EXPRESSIVE")
        val themeOverride = try { EmranTheme.fromPresetName(themeName) } catch (e: Exception) { EmranTheme.EXPRESSIVE }
        
        // Resolve the app's light/dark choice. SYSTEM is read from the widget host's
        // configuration, which is the closest a Glance provider can get to
        // isSystemInDarkTheme() outside a composition.
        val themeMode = prefs.getString("theme_mode", "DARK") ?: "DARK"
        val systemIsDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isLightMode = when (themeMode) {
            "LIGHT" -> true
            "DARK" -> false
            else -> !systemIsDark
        }

        val themeData = WidgetStateMapper.ThemeDomainData(
            followsAppTheme = true,
            themeOverride = themeOverride,
            isLightMode = isLightMode
        )
        WidgetDebugLogger.d("PrayerWidgetStateProvider.provideDomainData [THEME TRACE]: themeName=$themeName, themeOverride=$themeOverride, followsAppTheme=${themeData.followsAppTheme}")
        
        // Locale. The widget follows Emran's own language setting rather than the
        // device locale, and the device's 12/24-hour preference for the clock.
        val localeData = WidgetStateMapper.LocaleDomainData(
            language = prefs.getString("current_language", "en") ?: "en",
            use24Hour = android.text.format.DateFormat.is24HourFormat(context)
        )

        // Stats Data
        // Quran progress lives in the Quran screen's own preference file, not in
        // emran_settings, and is written by QuranViewModel.recordMushafProgress.
        val quranPrefs = context.getSharedPreferences("quran_prefs", Context.MODE_PRIVATE)
        val statsData = WidgetStateMapper.StatsDomainData(
            quranProgressPages = quranPrefs.getInt("mushaf_progress_page", 0),
            surahsCompleted = quranPrefs.getStringSet("surahs_completed", emptySet())?.size ?: 0,
            khatmahCount = quranPrefs.getInt("khatmah_count", 0)
        )
        
        // Calendar Data.
        // Resolved against the user's chosen location rather than the device, so
        // the widget's Hijri date and prayer list agree with the app.
        val zone = PrayerTimeCalculator.safeZone(
            prefs.getString("zone_id", null) ?: PrayerTimeCalculator.DEFAULT_ZONE_ID
        )
        val today = LocalDate.now(zone)
        var hijriDay: Int? = null
        var hijriMonth: Int? = null
        var hijriYear: Int? = null
        try {
            val hijrahDate = HijrahDate.from(today)
            hijriDay = hijrahDate.get(ChronoField.DAY_OF_MONTH)
            hijriMonth = hijrahDate.get(ChronoField.MONTH_OF_YEAR)
            hijriYear = hijrahDate.get(ChronoField.YEAR)
        } catch (e: Exception) {
            WidgetDebugLogger.e("PrayerWidgetStateProvider.provideDomainData: Failed Hijri date calculation", e)
        }
        
        val calendarData = WidgetStateMapper.CalendarDomainData(
            hijriDay = hijriDay,
            hijriMonth = hijriMonth,
            hijriYear = hijriYear
        )
        
        // Prayer Data
        var nextPrayerName: String? = null
        var nextPrayerTime: LocalDateTime? = null
        var currentPrayerName: String? = null
        var currentPrayerTime: LocalDateTime? = null
        var locationName = "Aleppo"
        var todayNames: List<String> = emptyList()
        var todayTimesList: List<LocalDateTime> = emptyList()

        try {
            val lat = prefs.getFloat("latitude", 36.2021f).toDouble()
            val lon = prefs.getFloat("longitude", 37.1343f).toDouble()
            val methodStr = prefs.getString("calculation_method", "EGYPTIAN") ?: "EGYPTIAN"
            val schoolStr = prefs.getString("asr_school", "SHAFII") ?: "SHAFII"
            val ruleStr = prefs.getString("high_latitude_rule", "AUTOMATIC") ?: "AUTOMATIC"
            locationName = prefs.getString("location_name", "Aleppo") ?: "Aleppo"

            val methodEnum = try { PrayerTimeCalculator.CalculationMethod.valueOf(methodStr) } catch(e: Exception) { PrayerTimeCalculator.CalculationMethod.EGYPTIAN }
            val schoolEnum = try { PrayerTimeCalculator.AsrSchool.valueOf(schoolStr) } catch(e: Exception) { PrayerTimeCalculator.AsrSchool.SHAFII }
            val ruleEnum = try { PrayerTimeCalculator.HighLatitudeRule.valueOf(ruleStr) } catch(e: Exception) { PrayerTimeCalculator.HighLatitudeRule.AUTOMATIC }

            val tomorrow = today.plusDays(1)
            val todayTimes = PrayerTimeCalculator.calculateTimes(
                date = today,
                latitude = lat,
                longitude = lon,
                zone = zone,
                method = methodEnum,
                school = schoolEnum,
                highLatitudeRule = ruleEnum
            )
            val tomorrowTimes = PrayerTimeCalculator.calculateTimes(
                date = tomorrow,
                latitude = lat,
                longitude = lon,
                zone = zone,
                method = methodEnum,
                school = schoolEnum,
                highLatitudeRule = ruleEnum
            )

            val prayersToday = listOf(
                "Fajr" to todayTimes.fajr,
                "Sunrise" to todayTimes.sunrise,
                "Dhuhr" to todayTimes.dhuhr,
                "Asr" to todayTimes.asr,
                "Maghrib" to todayTimes.maghrib,
                "Isha" to todayTimes.isha
            )

            val prayersTomorrow = listOf(
                "Fajr" to tomorrowTimes.fajr
            )

            // All six rows, Sunrise included: it is not a prayer, but it marks
            // the end of Fajr's window, which is exactly what a schedule is for.
            todayNames = prayersToday.map { it.first }
            todayTimesList = prayersToday.map { it.second }

            val now = LocalDateTime.now(zone)

            for (i in prayersToday.indices) {
                val (name, time) = prayersToday[i]
                if (now.isBefore(time)) {
                    nextPrayerName = name
                    nextPrayerTime = time
                    if (i > 0) {
                        currentPrayerName = prayersToday[i - 1].first
                        currentPrayerTime = prayersToday[i - 1].second
                    } else {
                        currentPrayerName = "Isha"
                        currentPrayerTime = null
                    }
                    break
                }
            }

            if (nextPrayerName == null) {
                nextPrayerName = prayersTomorrow[0].first
                nextPrayerTime = prayersTomorrow[0].second
                currentPrayerName = "Isha"
                currentPrayerTime = prayersToday.last().second
            }
        } catch (e: Exception) {
            WidgetDebugLogger.e("PrayerWidgetStateProvider.provideDomainData: Failed to calculate prayers", e)
            nextPrayerName = "Fajr"
            nextPrayerTime = today.atTime(5, 0)
        }

        val prayerData = WidgetStateMapper.PrayerDomainData(
            nextPrayerName = nextPrayerName,
            nextPrayerTime = nextPrayerTime,
            currentPrayerName = currentPrayerName,
            currentPrayerTime = currentPrayerTime,
            locationName = locationName,
            zone = zone,
            todayPrayerNames = todayNames,
            todayPrayerTimes = todayTimesList
        )

        val result = WidgetDomainData(
            prayerData = prayerData,
            calendarData = calendarData,
            statsData = statsData,
            themeData = themeData,
            localeData = localeData
        )
        WidgetDebugLogger.d("PrayerWidgetStateProvider.provideDomainData: exited | result=[nextPrayerName=${prayerData.nextPrayerName}, nextPrayerTime=${prayerData.nextPrayerTime}, locationName=${prayerData.locationName}]")
        return result
    }
}
