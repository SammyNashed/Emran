package io.github.sammynashed.emran.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sammynashed.emran.glance.mapper.WidgetStateMapper
import io.github.sammynashed.emran.glance.provider.PrayerWidgetStateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object WidgetUpdater {

    private const val NOW_WORK = "widget_refresh_now"
    private const val PERIODIC_WORK = "widget_refresh"

    /** One pass at a time (two would draw the same pictures twice and hold twice the memory). */
    private val drawing = kotlinx.coroutines.sync.Mutex()
    /** When every widget was last drawn: Android asks again per widget type right after an update. */
    @Volatile private var lastFullDraw = 0L

    /**
     * Redraws every Emran widget (or only [only]) with fresh prayer times, then arms the next
     * redraw at the next prayer or midnight ([WidgetTicker]). Safe to call from any thread.
     */
    suspend fun updateAllWidgets(context: Context, only: IntArray? = null) = withContext(Dispatchers.Default) {
      drawing.withLock {
        // A request for some widgets just after all were drawn (same data, same minute) is already done.
        if (only != null && System.currentTimeMillis() - lastFullDraw < 3_000L) return@withLock
        WidgetDebugLogger.d("WidgetUpdater.updateAllWidgets: entered")
        val all = DirectWidgets.placed(context)
        val placed = if (only == null) all else all.filter { it.id in only }
        try {
            if (placed.isNotEmpty()) {
                val d = PrayerWidgetStateProvider(context).provideDomainData()
                val ayah = todaysAyah(context)
                val base = WidgetStateMapper().mapToPrayerWidgetState(
                    widgetId = 0, prayerData = d.prayerData, calendarData = d.calendarData,
                    statsData = d.statsData, themeData = d.themeData, localeData = d.localeData,
                ).copy(
                    ayahText = ayah?.text, ayahSurah = ayah?.surah ?: 0, ayahNumber = ayah?.ayah ?: 0,
                    ayahSurahAr = ayah?.surahAr, ayahSurahEn = ayah?.surahEn, ayahEnglish = ayah?.english,
                )
                val mgr = AppWidgetManager.getInstance(context)
                for (w in placed) {
                    // One widget failing to draw must not leave the others stale.
                    runCatching {
                        val state = base.copy(widgetId = w.id, tint = WidgetStyles.get(context, w.id).name)
                        mgr.updateAppWidget(w.id, DirectWidgets.views(context, mgr, w, state))
                    }.onFailure { WidgetDebugLogger.e("WidgetUpdater: widget ${w.id} (${w.kind}) failed", it) }
                }
            }
            WidgetDebugLogger.d("WidgetUpdater.updateAllWidgets: drew ${placed.size}")
            if (only == null) lastFullDraw = System.currentTimeMillis()
        } finally {
            WidgetTicker.schedule(context, all.isNotEmpty())
            keepPeriodic(context, all.any { it.kind in DirectWidgets.MOVING })
        }
      }
    }

    /** A redraw soon, from a job (coalesced: asking twice queues one). */
    fun requestRefresh(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_WORK, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<io.github.sammynashed.emran.service.EmranWidgetUpdateWorker>().build(),
            )
        }
    }

    /**
     * Rings and bars move between prayers, so those widgets also get a cheap half-hourly redraw.
     * Without such a widget there is nothing to move, and no job runs at all.
     */
    private fun keepPeriodic(context: Context, wanted: Boolean) {
        runCatching {
            val wm = WorkManager.getInstance(context)
            if (wanted) wm.enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<io.github.sammynashed.emran.service.EmranWidgetUpdateWorker>(30, TimeUnit.MINUTES).build(),
            ) else wm.cancelUniqueWork(PERIODIC_WORK)
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
        val dir = java.io.File(context.cacheDir, "widget_dump").apply { deleteRecursively(); mkdirs() }
        val A = io.github.sammynashed.emran.glance.art.WidgetArt
        val K = io.github.sammynashed.emran.glance.art.WidgetArt.Kind.entries
        val slim = 340f to 90f; val wide = 340f to 170f; val square = 170f to 170f
        val shots = K.flatMap { k ->
            when (k.name) {
                "DAY_ARABIC", "DAY_ENGLISH", "NEXT_PRAYER" -> listOf(k to square, k to (if (k.name == "NEXT_PRAYER") slim else wide))
                "CALENDAR" -> listOf(k to square, k to (340f to 200f))
                "PRAYER_ROW", "DATE_BANNER", "DUAL_BANNER", "DATE_CLOCK" -> listOf(k to slim)
                "DATE_COMPACT", "HIJRI_MONTH", "PRAYER_TABLE", "CLOCK", "OCCASION" -> listOf(k to square)
                else -> listOf(k to wide)
            }
        }
        A.render(context, K.first(), state, 10f, 10f)
        for (lang in listOf("en", "ar")) for ((kind, wh) in shots) {
            val bmp = A.render(context, kind, state.copy(language = lang), wh.first, wh.second)
            java.io.File(dir, "${kind.name.lowercase()}_${wh.first.toInt()}x${wh.second.toInt()}_$lang.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        java.io.File(dir, "missing.txt").writeText(io.github.sammynashed.emran.glance.art.TypeArt.missing.joinToString("\n"))
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
