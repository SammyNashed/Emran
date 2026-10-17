package io.github.sammynashed.emran.glance

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.sammynashed.emran.glance.mapper.WidgetStateMapper
import io.github.sammynashed.emran.glance.provider.PrayerWidgetStateProvider
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.state.EmranWidgetStateDefinition

object WidgetUpdater {

    /**
     * Every widget class in the family. They all share one state definition, so
     * a single pass of the provider feeds all of them.
     */
    private val widgetClasses: List<Pair<Class<out GlanceAppWidget>, () -> GlanceAppWidget>> = listOf(
        DayArabicWidget::class.java to { DayArabicWidget() },
        DayEnglishWidget::class.java to { DayEnglishWidget() },
        PrayerProgressWidget::class.java to { PrayerProgressWidget() },
        PrayerTableWidget::class.java to { PrayerTableWidget() },
        NextPrayerWidget::class.java to { NextPrayerWidget() },
        HijriDateWidget::class.java to { HijriDateWidget() },
        HijriMonthWidget::class.java to { HijriMonthWidget() },
        AyahWidget::class.java to { AyahWidget() },
        ClockWidget::class.java to { ClockWidget() },
        OccasionWidget::class.java to { OccasionWidget() }
    )

    suspend fun updateAllWidgets(context: Context) {
        WidgetDebugLogger.d("WidgetUpdater.updateAllWidgets: entered")
        try {
            val domainData = PrayerWidgetStateProvider(context).provideDomainData()
            val mapper = WidgetStateMapper()
            val ayah = todaysAyah(context)
            val manager = GlanceAppWidgetManager(context)

            for ((clazz, factory) in widgetClasses) {
                val glanceIds = manager.getGlanceIds(clazz)
                if (glanceIds.isEmpty()) continue

                for (glanceId in glanceIds) {
                    updateAppWidgetState<EmranWidgetState>(
                        context,
                        EmranWidgetStateDefinition,
                        glanceId
                    ) {
                        val appWidgetId = manager.getAppWidgetId(glanceId)
                        mapper.mapToPrayerWidgetState(
                            widgetId = glanceId.hashCode(),
                            prayerData = domainData.prayerData,
                            calendarData = domainData.calendarData,
                            statsData = domainData.statsData,
                            themeData = domainData.themeData,
                            localeData = domainData.localeData
                        ).copy(
                            tint = WidgetStyles.get(context, appWidgetId).name,
                            ayahText = ayah?.text, ayahSurah = ayah?.surah ?: 0, ayahNumber = ayah?.ayah ?: 0,
                            ayahSurahAr = ayah?.surahAr, ayahSurahEn = ayah?.surahEn, ayahEnglish = ayah?.english,
                        )
                    }
                }
                factory().updateAll(context)
            }
            MidnightReceiver.schedule(context)
            WidgetDebugLogger.d("WidgetUpdater.updateAllWidgets: exited successfully")
        } catch (e: Exception) {
            WidgetDebugLogger.e("WidgetUpdater.updateAllWidgets: exited with exception", e)
            throw e
        }
    }

    /** Debug: draws every widget with today's real data into cache/widget_dump, to check them without a launcher. */
    suspend fun dumpPreviews(context: Context) {
        val d = PrayerWidgetStateProvider(context).provideDomainData()
        val ayah = todaysAyah(context)
        // Same data as a real widget gets (including today's ayah); previews are drawn in English.
        val state = WidgetStateMapper().mapToPrayerWidgetState(0, d.prayerData, d.calendarData, d.statsData, d.themeData, d.localeData).copy(
            language = "en",
            ayahText = ayah?.text, ayahSurah = ayah?.surah ?: 0, ayahNumber = ayah?.ayah ?: 0,
            ayahSurahAr = ayah?.surahAr, ayahSurahEn = ayah?.surahEn, ayahEnglish = ayah?.english,
        )
        val dir = java.io.File(context.cacheDir, "widget_dump").apply { mkdirs() }
        val sizes = mapOf(
            io.github.sammynashed.emran.glance.art.WidgetArt.Kind.DAY_ENGLISH to (360f to 90f),
            io.github.sammynashed.emran.glance.art.WidgetArt.Kind.DAY_ARABIC to (360f to 170f),
            io.github.sammynashed.emran.glance.art.WidgetArt.Kind.PRAYER_PROGRESS to (360f to 170f),
            io.github.sammynashed.emran.glance.art.WidgetArt.Kind.PRAYER_TABLE to (170f to 170f),
            io.github.sammynashed.emran.glance.art.WidgetArt.Kind.NEXT_PRAYER to (170f to 170f),
            io.github.sammynashed.emran.glance.art.WidgetArt.Kind.HIJRI_DATE to (170f to 170f),
            io.github.sammynashed.emran.glance.art.WidgetArt.Kind.HIJRI_MONTH to (170f to 170f),
        )
        val extra = listOf(
            Triple("next_prayer_short", io.github.sammynashed.emran.glance.art.WidgetArt.Kind.NEXT_PRAYER, 170f to 100f),
            Triple("hijri_date_short", io.github.sammynashed.emran.glance.art.WidgetArt.Kind.HIJRI_DATE, 170f to 100f),
            Triple("ayah", io.github.sammynashed.emran.glance.art.WidgetArt.Kind.AYAH, 360f to 180f),
            Triple("clock", io.github.sammynashed.emran.glance.art.WidgetArt.Kind.CLOCK, 170f to 170f),
            Triple("occasion", io.github.sammynashed.emran.glance.art.WidgetArt.Kind.OCCASION, 170f to 170f),
            Triple("prayer_table_wide", io.github.sammynashed.emran.glance.art.WidgetArt.Kind.PRAYER_TABLE, 360f to 200f),
        )
        for ((name, kind, wh) in extra) {
            val bmp = io.github.sammynashed.emran.glance.art.WidgetArt.render(context, kind, state, wh.first, wh.second)
            java.io.File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        for ((kind, wh) in sizes) {
            val bmp = io.github.sammynashed.emran.glance.art.WidgetArt.render(context, kind, state, wh.first, wh.second)
            java.io.File(dir, "${kind.name.lowercase()}.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private class Ayah(val surah: Int, val ayah: Int, val text: String, val surahAr: String, val surahEn: String, val english: String?)

    /** Today's ayah, read from the bundled Quran and translation. */
    private suspend fun todaysAyah(context: Context): Ayah? = runCatching {
        val (s, a) = io.github.sammynashed.emran.data.DailyAyat.forDay(context, java.time.LocalDate.now())
        val t = io.github.sammynashed.emran.data.DailyAyat.text(context, s, a)
        val en = io.github.sammynashed.emran.data.AyahTafsir.get(context, s, a)?.translation
            ?.ifBlank { null }?.let { io.github.sammynashed.emran.ui.screens.standaloneTranslation(it) }
        Ayah(s, a, t.text, t.surahAr, t.surahEn, en)
    }.getOrNull()
}
