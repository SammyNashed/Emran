package io.github.sammynashed.emran.glance.mapper

import io.github.sammynashed.emran.glance.WidgetDebugLogger
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.state.WidgetType
import io.github.sammynashed.emran.ui.theme.EmranTheme
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetStateMapper {
    data class PrayerDomainData(
        val nextPrayerName: String?,
        val nextPrayerTime: LocalDateTime?,
        val currentPrayerName: String?,
        val currentPrayerTime: LocalDateTime?,
        val locationName: String?,
        /** Today's five obligatory prayers, in order. */
        val todayPrayerNames: List<String> = emptyList(),
        val todayPrayerTimes: List<LocalDateTime> = emptyList(),
        /**
         * Zone the prayer times belong to. The widget serialises absolute epoch
         * millis, so the wall times must be resolved against the location's zone
         * rather than the device's -- otherwise a user tracking a city in another
         * zone gets a countdown that is off by the difference between them.
         */
        val zone: ZoneId = ZoneId.systemDefault()
    )
    
    data class CalendarDomainData(
        val hijriDay: Int?,
        val hijriMonth: Int?,
        val hijriYear: Int?
    )
    
    data class StatsDomainData(
        val quranProgressPages: Int,
        val surahsCompleted: Int = 0,
        val khatmahCount: Int = 0
    )
    
    /** App-level presentation settings mirrored into the widget. */
    data class LocaleDomainData(
        val language: String,
        val use24Hour: Boolean
    )

    data class ThemeDomainData(
        val followsAppTheme: Boolean,
        val themeOverride: EmranTheme?,
        /** Mirrors the app's light/dark choice so the widget does not diverge from it. */
        val isLightMode: Boolean = false
    )

    fun mapToPrayerWidgetState(
        widgetId: Int,
        prayerData: PrayerDomainData,
        calendarData: CalendarDomainData,
        statsData: StatsDomainData,
        themeData: ThemeDomainData,
        localeData: LocaleDomainData
    ): EmranWidgetState {
        WidgetDebugLogger.d("WidgetStateMapper.mapToPrayerWidgetState: entered | widgetId=$widgetId | thread=${Thread.currentThread().name} | prayerData=[nextPrayerName=${prayerData.nextPrayerName}, nextPrayerTime=${prayerData.nextPrayerTime}]")
        WidgetDebugLogger.d("WidgetStateMapper.mapToPrayerWidgetState [THEME TRACE]: incoming themeData=[followsAppTheme=${themeData.followsAppTheme}, themeOverride=${themeData.themeOverride}]")
        val result = EmranWidgetState(
            schemaVersion = 1,
            widgetId = widgetId,
            widgetType = WidgetType.PRAYER,
            
            // Theme mapping
            followsAppTheme = themeData.followsAppTheme,
            themeOverride = themeData.themeOverride,
            isLightMode = themeData.isLightMode,
            
            // UI State
            isLoading = false,
            
            // Prayer info
            nextPrayerName = prayerData.nextPrayerName,
            nextPrayerEpochMillis = prayerData.nextPrayerTime?.let { safeToEpochMillis(it, prayerData.zone) },
            currentPrayerName = prayerData.currentPrayerName,
            currentPrayerEpochMillis = prayerData.currentPrayerTime?.let { safeToEpochMillis(it, prayerData.zone) },
            locationName = prayerData.locationName,
            language = localeData.language,
            use24Hour = localeData.use24Hour,
            todayPrayerNames = prayerData.todayPrayerNames,
            todayPrayerEpochMillis = prayerData.todayPrayerTimes.map {
                safeToEpochMillis(it, prayerData.zone)
            },
            
            // Stats
            quranProgressPages = statsData.quranProgressPages,
            quranTotalPages = 604, // Fixed for standard Mushaf
            surahsCompleted = statsData.surahsCompleted,
            khatmahCount = statsData.khatmahCount,
            
            // Calendar
            hijriDay = calendarData.hijriDay,
            hijriMonth = calendarData.hijriMonth,
            hijriYear = calendarData.hijriYear
        )
        WidgetDebugLogger.d("WidgetStateMapper.mapToPrayerWidgetState [THEME TRACE]: outgoing state=[followsAppTheme=${result.followsAppTheme}, themeOverride=${result.themeOverride}]")
        WidgetDebugLogger.d("WidgetStateMapper.mapToPrayerWidgetState: exited | result=[nextPrayerName=${result.nextPrayerName}, nextPrayerEpochMillis=${result.nextPrayerEpochMillis}, isLoading=${result.isLoading}]")
        return result
    }

    private fun safeToEpochMillis(dateTime: LocalDateTime, zone: ZoneId): Long {
        return try {
            dateTime.atZone(zone).toInstant().toEpochMilli()
        } catch (e: Exception) {
            dateTime.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }
    }
}
