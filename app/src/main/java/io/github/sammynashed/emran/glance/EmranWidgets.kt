package io.github.sammynashed.emran.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.state.EmranWidgetStateDefinition
import io.github.sammynashed.emran.glance.theme.EmranGlanceTheme
import io.github.sammynashed.emran.glance.ui.CombinedWidgetUi
import io.github.sammynashed.emran.glance.ui.CountdownWidgetUi
import io.github.sammynashed.emran.glance.ui.HijriWidgetUi
import io.github.sammynashed.emran.glance.ui.QuranProgressWidgetUi
import io.github.sammynashed.emran.glance.ui.TodayFiveWidgetUi
import io.github.sammynashed.emran.glance.ui.TodayPrayersV2WidgetUi
import io.github.sammynashed.emran.service.EmranWidgetUpdateWorker

/**
 * Shared plumbing for the widget family.
 *
 * Every widget reads the same [EmranWidgetState] and differs only in what it
 * renders, so the theme wrapper, the tap-to-open action and the responsive size
 * buckets all live here rather than being repeated five times.
 */
abstract class BaseEmranWidget : GlanceAppWidget() {
    override val stateDefinition = EmranWidgetStateDefinition

    /** Size buckets the host may ask for. Kept wide so resizing stays smooth. */
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(100.dp, 60.dp),
            DpSize(140.dp, 100.dp),
            DpSize(180.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 170.dp),
            DpSize(250.dp, 230.dp),
            DpSize(320.dp, 230.dp),
            DpSize(320.dp, 320.dp)
        )
    )

    @Composable
    protected abstract fun Content(state: EmranWidgetState)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<EmranWidgetState>()
            // Explicit intent: a ComponentName built from the widget's own package
            // avoids the mismatch that bites when the applicationId is suffixed.
            val intent = Intent().apply {
                setClassName(context.packageName, "io.github.sammynashed.emran.MainActivity")
            }
            EmranGlanceTheme(widgetState = state) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity(intent))
                ) {
                    Content(state)
                }
            }
        }
    }
}

/** Receivers all kick the same refresh worker when the host asks for an update. */
abstract class BaseEmranWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        try {
            super.onUpdate(context, appWidgetManager, appWidgetIds)
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<EmranWidgetUpdateWorker>().build())
        } catch (e: Exception) {
            WidgetDebugLogger.e("${this::class.java.simpleName}.onUpdate failed", e)
        }
    }
}

// ---- Today's five ------------------------------------------------------

class TodayFiveWidget : BaseEmranWidget() {
    @Composable
    override fun Content(state: EmranWidgetState) = TodayFiveWidgetUi(state)
}

class TodayFiveWidgetReceiver : BaseEmranWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayFiveWidget()
}

// ---- Hijri date --------------------------------------------------------

class HijriWidget : BaseEmranWidget() {
    @Composable
    override fun Content(state: EmranWidgetState) = HijriWidgetUi(state)
}

class HijriWidgetReceiver : BaseEmranWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HijriWidget()
}

// ---- Quran progress ----------------------------------------------------

class QuranProgressWidget : BaseEmranWidget() {
    @Composable
    override fun Content(state: EmranWidgetState) = QuranProgressWidgetUi(state)
}

class QuranProgressWidgetReceiver : BaseEmranWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuranProgressWidget()
}

// ---- Countdown ---------------------------------------------------------

class CountdownWidget : BaseEmranWidget() {
    @Composable
    override fun Content(state: EmranWidgetState) = CountdownWidgetUi(state)
}

class CountdownWidgetReceiver : BaseEmranWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}

// ---- Combined overview -------------------------------------------------

class CombinedWidget : BaseEmranWidget() {
    @Composable
    override fun Content(state: EmranWidgetState) = CombinedWidgetUi(state)
}

class CombinedWidgetReceiver : BaseEmranWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CombinedWidget()
}

// ---- Today's prayers V2 (astrolabe dial) --------------------------------

class TodayPrayersV2Widget : BaseEmranWidget() {
    @Composable
    override fun Content(state: EmranWidgetState) = TodayPrayersV2WidgetUi(state)
}

class TodayPrayersV2WidgetReceiver : BaseEmranWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayPrayersV2Widget()
}
