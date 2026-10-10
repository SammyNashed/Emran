package io.github.sammynashed.emran

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.sammynashed.emran.glance.WidgetUpdater
import io.github.sammynashed.emran.glance.TodayFiveWidgetReceiver
import io.github.sammynashed.emran.glance.state.EmranWidgetStateDefinition
import kotlinx.coroutines.runBlocking
import android.util.Log

@RunWith(RobolectricTestRunner::class)
class WorkerTest {
    @Test
    fun testWorker() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            
            // Create a fake widget ID so GlanceAppWidgetManager sees it
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val shadowAppWidgetManager = shadowOf(appWidgetManager)
            val widgetId = shadowAppWidgetManager.createWidget(TodayFiveWidgetReceiver::class.java, 0)
            
            try {
                println("GlanceDebug: TEST START")
                WidgetUpdater.updateAllWidgets(context)
                println("GlanceDebug: Test complete")
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }
}
