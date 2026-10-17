package io.github.sammynashed.emran.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sammynashed.emran.glance.art.WidgetArt
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.state.EmranWidgetStateDefinition
import io.github.sammynashed.emran.glance.ui.LiveCountdown
import io.github.sammynashed.emran.service.EmranWidgetUpdateWorker

/**
 * Emran's widgets: each is one picture drawn by [WidgetArt] at the widget's exact size
 * (glass, calligraphy, Bodoni, Inter), plus a live, system-ticked countdown where a widget
 * shows one. Tapping any of them opens Emran.
 */
abstract class ArtWidget(private val kind: WidgetArt.Kind) : GlanceAppWidget() {
    override val stateDefinition = EmranWidgetStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<EmranWidgetState>()
            val size = LocalSize.current
            val intent = Intent().apply { setClassName(context.packageName, "io.github.sammynashed.emran.MainActivity") }
            val tint = runCatching { WidgetArt.Tint.valueOf(state.tint) }.getOrDefault(WidgetArt.Tint.AUTO)
            val art = WidgetArt.render(context, kind, state, size.width.value, size.height.value, tint)
            Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent))) {
                Image(ImageProvider(art), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
                Overlay(state)
            }
        }
    }

    @Composable
    protected open fun Overlay(state: EmranWidgetState) {}
}

abstract class ArtWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            super.onUpdate(context, appWidgetManager, appWidgetIds)
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<EmranWidgetUpdateWorker>().build())
        } catch (e: Exception) {
            WidgetDebugLogger.e("${this::class.java.simpleName}.onUpdate failed", e)
        }
    }
}

class DayArabicWidget : ArtWidget(WidgetArt.Kind.DAY_ARABIC)
class DayArabicWidgetReceiver : ArtWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DayArabicWidget() }

class DayEnglishWidget : ArtWidget(WidgetArt.Kind.DAY_ENGLISH)
class DayEnglishWidgetReceiver : ArtWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DayEnglishWidget() }

class PrayerProgressWidget : ArtWidget(WidgetArt.Kind.PRAYER_PROGRESS) {
    @Composable
    override fun Overlay(state: EmranWidgetState) {
        // The countdown sits under the next prayer's name, top corner.
        val ar = state.language == "ar"
        Box(GlanceModifier.fillMaxSize().padding(top = 34.dp, start = 18.dp, end = 18.dp), contentAlignment = if (ar) Alignment.TopStart else Alignment.TopEnd) {
            LiveCountdown(state.nextPrayerEpochMillis, Color.White.copy(alpha = 0.72f), fontSizeSp = 12f, centered = false, modifier = GlanceModifier.width(90.dp))
        }
    }
}
class PrayerProgressWidgetReceiver : ArtWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PrayerProgressWidget() }

class PrayerTableWidget : ArtWidget(WidgetArt.Kind.PRAYER_TABLE)
class PrayerTableWidgetReceiver : ArtWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PrayerTableWidget() }

class NextPrayerWidget : ArtWidget(WidgetArt.Kind.NEXT_PRAYER) {
    @Composable
    override fun Overlay(state: EmranWidgetState) {
        val size = LocalSize.current
        val ar = state.language == "ar"
        if (WidgetArt.isShort(size.height.value)) {
            // Short: under the prayer's name, on its side.
            Box(
                GlanceModifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                contentAlignment = if (ar) Alignment.BottomEnd else Alignment.BottomStart,
            ) {
                LiveCountdown(state.nextPrayerEpochMillis, Color.White.copy(alpha = 0.72f), fontSizeSp = 12f, centered = false, modifier = GlanceModifier.width(80.dp))
            }
        } else {
            Box(GlanceModifier.fillMaxSize().padding(bottom = 18.dp), contentAlignment = Alignment.BottomCenter) {
                LiveCountdown(state.nextPrayerEpochMillis, Color.White.copy(alpha = 0.72f), fontSizeSp = 13f, centered = true)
            }
        }
    }
}
class NextPrayerWidgetReceiver : ArtWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NextPrayerWidget() }

class HijriDateWidget : ArtWidget(WidgetArt.Kind.HIJRI_DATE)
class HijriDateWidgetReceiver : ArtWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HijriDateWidget() }

class HijriMonthWidget : ArtWidget(WidgetArt.Kind.HIJRI_MONTH)
class HijriMonthWidgetReceiver : ArtWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HijriMonthWidget() }

class AyahWidget : ArtWidget(WidgetArt.Kind.AYAH)
class AyahWidgetReceiver : ArtWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = AyahWidget() }

class ClockWidget : ArtWidget(WidgetArt.Kind.CLOCK) {
    @Composable
    override fun Overlay(state: EmranWidgetState) {
        val size = LocalSize.current
        val context = androidx.glance.LocalContext.current
        // Thin digits sized from the widget's height, above the date line.
        val sp = (size.height.value * 0.36f).coerceIn(28f, 72f)
        val views = android.widget.RemoteViews(context.packageName, io.github.sammynashed.emran.R.layout.widget_clock).apply {
            setTextViewTextSize(io.github.sammynashed.emran.R.id.widget_clock, android.util.TypedValue.COMPLEX_UNIT_SP, sp)
        }
        Box(GlanceModifier.fillMaxSize().padding(bottom = (size.height.value * 0.22f).dp), contentAlignment = Alignment.Center) {
            androidx.glance.appwidget.AndroidRemoteViews(views, GlanceModifier.fillMaxWidth().height((sp * 1.25f).dp))
        }
    }
}
class ClockWidgetReceiver : ArtWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ClockWidget() }

class OccasionWidget : ArtWidget(WidgetArt.Kind.OCCASION)
class OccasionWidgetReceiver : ArtWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = OccasionWidget() }
