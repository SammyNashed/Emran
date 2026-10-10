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
        TodayFiveWidget::class.java to { TodayFiveWidget() },
        TodayPrayersV2Widget::class.java to { TodayPrayersV2Widget() },
        HijriWidget::class.java to { HijriWidget() },
        QuranProgressWidget::class.java to { QuranProgressWidget() },
        CountdownWidget::class.java to { CountdownWidget() },
        CombinedWidget::class.java to { CombinedWidget() }
    )

    suspend fun updateAllWidgets(context: Context) {
        WidgetDebugLogger.d("WidgetUpdater.updateAllWidgets: entered")
        try {
            val domainData = PrayerWidgetStateProvider(context).provideDomainData()
            val mapper = WidgetStateMapper()
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
                        mapper.mapToPrayerWidgetState(
                            widgetId = glanceId.hashCode(),
                            prayerData = domainData.prayerData,
                            calendarData = domainData.calendarData,
                            statsData = domainData.statsData,
                            themeData = domainData.themeData,
                            localeData = domainData.localeData
                        )
                    }
                }
                factory().updateAll(context)
            }
            WidgetDebugLogger.d("WidgetUpdater.updateAllWidgets: exited successfully")
        } catch (e: Exception) {
            WidgetDebugLogger.e("WidgetUpdater.updateAllWidgets: exited with exception", e)
            throw e
        }
    }
}
