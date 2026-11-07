package io.github.sammynashed.emran.glance

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.widget.RemoteViews
import io.github.sammynashed.emran.R
import io.github.sammynashed.emran.glance.art.WidgetArt
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Emran's widgets. Each is one picture drawn by [WidgetArt] at the widget's exact size (glass,
 * calligraphy, Bodoni, Inter), plus a live, system-ticked countdown or clock where a widget shows
 * one. Tapping any of them opens Emran.
 *
 * They are drawn straight into RemoteViews and handed to [AppWidgetManager] here, in the app's
 * own process. They used to go through Glance, whose every redraw runs as a WorkManager job: on
 * phones that hold background jobs back (a friend's showed "next: Dhuhr" at 17:53, 2026-11-07),
 * the widgets froze for hours although the adhan alarms still fired.
 */
abstract class ArtWidgetReceiver(val kind: WidgetArt.Kind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DirectWidgets.redrawAsync(this, context, appWidgetIds)
    }

    /** Resized: draw it again at its new size. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        DirectWidgets.redrawAsync(this, context, intArrayOf(appWidgetId))
    }
}

class DayArabicWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.DAY_ARABIC)
class DayEnglishWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.DAY_ENGLISH)
class PrayerProgressWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.PRAYER_PROGRESS)
class PrayerTableWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.PRAYER_TABLE)
class NextPrayerWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.NEXT_PRAYER)
class HijriDateWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.HIJRI_DATE)
class HijriMonthWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.HIJRI_MONTH)
class AyahWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.AYAH)
class ClockWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.CLOCK)
class OccasionWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.OCCASION)
class PrayerRowWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.PRAYER_ROW)
class MonthCalligraphyWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.MONTH_CALLIGRAPHY)
class MonthElegantWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.MONTH_ELEGANT)
class DateCompactWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.DATE_COMPACT)
class DateBannerWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.DATE_BANNER)
class DualBannerWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.DUAL_BANNER)
class CalendarWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.CALENDAR)
class YearDotsWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.YEAR_DOTS)
class DateClockWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.DATE_CLOCK)
class ClockArabicWidgetReceiver : ArtWidgetReceiver(WidgetArt.Kind.CLOCK_ARABIC)

/** Draws Emran widgets directly, without Glance or WorkManager. */
object DirectWidgets {

    /** Every widget receiver and the picture it draws. */
    private val RECEIVERS: List<Pair<Class<out ArtWidgetReceiver>, WidgetArt.Kind>> = listOf(
        DayArabicWidgetReceiver::class.java to WidgetArt.Kind.DAY_ARABIC,
        DayEnglishWidgetReceiver::class.java to WidgetArt.Kind.DAY_ENGLISH,
        PrayerProgressWidgetReceiver::class.java to WidgetArt.Kind.PRAYER_PROGRESS,
        PrayerTableWidgetReceiver::class.java to WidgetArt.Kind.PRAYER_TABLE,
        NextPrayerWidgetReceiver::class.java to WidgetArt.Kind.NEXT_PRAYER,
        HijriDateWidgetReceiver::class.java to WidgetArt.Kind.HIJRI_DATE,
        HijriMonthWidgetReceiver::class.java to WidgetArt.Kind.HIJRI_MONTH,
        AyahWidgetReceiver::class.java to WidgetArt.Kind.AYAH,
        ClockWidgetReceiver::class.java to WidgetArt.Kind.CLOCK,
        OccasionWidgetReceiver::class.java to WidgetArt.Kind.OCCASION,
        PrayerRowWidgetReceiver::class.java to WidgetArt.Kind.PRAYER_ROW,
        MonthCalligraphyWidgetReceiver::class.java to WidgetArt.Kind.MONTH_CALLIGRAPHY,
        MonthElegantWidgetReceiver::class.java to WidgetArt.Kind.MONTH_ELEGANT,
        DateCompactWidgetReceiver::class.java to WidgetArt.Kind.DATE_COMPACT,
        DateBannerWidgetReceiver::class.java to WidgetArt.Kind.DATE_BANNER,
        DualBannerWidgetReceiver::class.java to WidgetArt.Kind.DUAL_BANNER,
        CalendarWidgetReceiver::class.java to WidgetArt.Kind.CALENDAR,
        YearDotsWidgetReceiver::class.java to WidgetArt.Kind.YEAR_DOTS,
        DateClockWidgetReceiver::class.java to WidgetArt.Kind.DATE_CLOCK,
        ClockArabicWidgetReceiver::class.java to WidgetArt.Kind.CLOCK_ARABIC,
    )

    /** Pictures that change between prayers (a ring, a bar): they also want the half-hourly refresh. */
    val MOVING = setOf(WidgetArt.Kind.PRAYER_PROGRESS, WidgetArt.Kind.NEXT_PRAYER, WidgetArt.Kind.PRAYER_ROW)

    data class Placed(val id: Int, val kind: WidgetArt.Kind)

    /** Every Emran widget on the home screen(s) right now. */
    fun placed(context: Context): List<Placed> {
        val mgr = AppWidgetManager.getInstance(context) ?: return emptyList()
        return RECEIVERS.flatMap { (cls, kind) ->
            runCatching { mgr.getAppWidgetIds(ComponentName(context, cls)) }.getOrDefault(IntArray(0)).map { Placed(it, kind) }
        }
    }

    fun kindOf(context: Context, id: Int): WidgetArt.Kind? {
        val cls = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className ?: return null
        return RECEIVERS.firstOrNull { it.first.name == cls }?.second
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Redraws from inside a broadcast: the receiver is kept alive (goAsync) until the widgets are
     * drawn, well inside the time a background broadcast is allowed.
     */
    fun redrawAsync(receiver: BroadcastReceiver, context: Context, ids: IntArray? = null) {
        val app = context.applicationContext
        val pending = runCatching { receiver.goAsync() }.getOrNull()
        scope.launch {
            try {
                val done = withTimeoutOrNull(20_000) { WidgetUpdater.updateAllWidgets(app, ids); true }
                if (done == null) WidgetUpdater.requestRefresh(app) // too slow here: let a job finish it
            } catch (e: Exception) {
                WidgetDebugLogger.e("DirectWidgets.redrawAsync failed", e)
                WidgetUpdater.requestRefresh(app)
            } finally {
                pending?.finish()
            }
        }
    }

    /** One widget's views, for each size the launcher may show it at (portrait and landscape). */
    fun views(context: Context, mgr: AppWidgetManager, w: Placed, state: EmranWidgetState): RemoteViews {
        val sizes = sizesOf(context, mgr, w.id)
        if (Build.VERSION.SDK_INT >= 31 && sizes.size > 1) {
            return RemoteViews(sizes.associateWith { one(context, w.kind, state, it.width, it.height) })
        }
        val s = sizes.first()
        return one(context, w.kind, state, s.width, s.height)
    }

    /** Sizes in dp, at most two (Android 12+ reports each size it lays the widget out at). */
    private fun sizesOf(context: Context, mgr: AppWidgetManager, id: Int): List<SizeF> {
        val o = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull() ?: Bundle()
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val list = o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.filter { it.width > 1f && it.height > 1f }?.distinct()?.take(2)
            if (!list.isNullOrEmpty()) return list
        }
        // Portrait shows the widget at its minimum width and maximum height.
        val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (wDp > 1 && hDp > 1) return listOf(SizeF(wDp.toFloat(), hDp.toFloat()))
        // Not laid out yet: the size it asked for.
        val info = mgr.getAppWidgetInfo(id)
        val dens = context.resources.displayMetrics.density
        return listOf(SizeF(((info?.minWidth ?: 250) / dens).coerceAtLeast(40f), ((info?.minHeight ?: 110) / dens).coerceAtLeast(40f)))
    }

    private fun one(context: Context, kind: WidgetArt.Kind, state: EmranWidgetState, wDp: Float, hDp: Float): RemoteViews {
        val tint = runCatching { WidgetArt.Tint.valueOf(state.tint) }.getOrDefault(WidgetArt.Tint.AUTO)
        val rv = RemoteViews(context.packageName, R.layout.widget_art)
        rv.setImageViewBitmap(R.id.widget_art, WidgetArt.render(context, kind, state, wDp, hDp, tint))
        rv.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        overlay(context, rv, kind, state, wDp, hDp)
        return rv
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent().setClassName(context.packageName, "io.github.sammynashed.emran.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 7700, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The live view on top of the picture, placed where the picture leaves room for it. */
    private fun overlay(context: Context, rv: RemoteViews, kind: WidgetArt.Kind, s: EmranWidgetState, w: Float, h: Float) {
        val px = context.resources.displayMetrics.density
        fun pad(left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f) =
            rv.setViewPadding(R.id.widget_overlay, (left * px).toInt(), (top * px).toInt(), (right * px).toInt(), (bottom * px).toInt())
        val ar = s.language == "ar"
        // The picture puts the prayer's time on the reading side: right in Arabic, left in English.
        val side = if (ar) Gravity.RIGHT else Gravity.LEFT
        rv.removeAllViews(R.id.widget_overlay)
        when (kind) {
            WidgetArt.Kind.PRAYER_PROGRESS -> {
                pad(18f, 54f, 18f, 0f)
                countdown(context, rv, s.nextPrayerEpochMillis, 15f, Gravity.TOP, side)
            }
            WidgetArt.Kind.NEXT_PRAYER -> if (WidgetArt.isShort(h)) {
                pad(18f, h * 0.5f, 18f, 0f)
                countdown(context, rv, s.nextPrayerEpochMillis, 16f, Gravity.TOP, side)
            } else {
                pad(0f, h * 0.60f, 0f, 0f) // inside the ring, under the prayer's name
                countdown(context, rv, s.nextPrayerEpochMillis, 14f, Gravity.TOP, Gravity.CENTER_HORIZONTAL)
            }
            WidgetArt.Kind.CLOCK -> {
                pad(bottom = h * 0.22f) // thin digits above the date line
                clock(context, rv, R.layout.widget_clock, (h * 0.36f).coerceIn(28f, 72f))
            }
            WidgetArt.Kind.DATE_CLOCK -> clock(context, rv, R.layout.widget_clock, minOf(h * 0.36f, w * 0.11f).coerceIn(18f, 40f))
            WidgetArt.Kind.CLOCK_ARABIC -> clock(context, rv, R.layout.widget_clock_arabic, minOf(h * 0.5f, w * 0.24f).coerceIn(24f, 110f))
            else -> Unit
        }
    }

    /**
     * A countdown the system ticks every second (Chronometer), so it never freezes between redraws.
     * Past zero it would count up again, so a redraw is scheduled for that moment (WidgetTicker).
     */
    private fun countdown(context: Context, rv: RemoteViews, target: Long?, sp: Float, vertical: Int, horizontal: Int) {
        val left = (target ?: return) - System.currentTimeMillis()
        if (left <= 0) return
        rv.setInt(R.id.widget_overlay, "setGravity", vertical)
        val c = RemoteViews(context.packageName, R.layout.widget_countdown_chronometer_start)
        c.setChronometer(R.id.widget_chronometer, SystemClock.elapsedRealtime() + left, null, true)
        c.setChronometerCountDown(R.id.widget_chronometer, true)
        c.setTextViewTextSize(R.id.widget_chronometer, TypedValue.COMPLEX_UNIT_SP, sp)
        c.setInt(R.id.widget_chronometer, "setGravity", horizontal or Gravity.CENTER_VERTICAL)
        rv.addView(R.id.widget_overlay, c)
    }

    private fun clock(context: Context, rv: RemoteViews, layout: Int, sp: Float) {
        rv.setInt(R.id.widget_overlay, "setGravity", Gravity.CENTER)
        val c = RemoteViews(context.packageName, layout)
        c.setTextViewTextSize(R.id.widget_clock, TypedValue.COMPLEX_UNIT_SP, sp)
        rv.addView(R.id.widget_overlay, c)
    }
}
