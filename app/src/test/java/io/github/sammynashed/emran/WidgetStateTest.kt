package io.github.sammynashed.emran

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.state.WidgetType
import io.github.sammynashed.emran.ui.theme.EmranTheme

@RunWith(RobolectricTestRunner::class)
class WidgetStateTest {
    @Test
    fun testSerialization() {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(EmranWidgetState::class.java)
        
        val state = EmranWidgetState(
            schemaVersion = 1,
            widgetId = 123,
            widgetType = WidgetType.PRAYER,
            followsAppTheme = true,
            themeOverride = EmranTheme.EXPRESSIVE,
            isLoading = false,
            nextPrayerName = "Fajr",
            nextPrayerEpochMillis = 1000L,
            currentPrayerName = "Isha",
            currentPrayerEpochMillis = 500L,
            locationName = "Aleppo",
            quranProgressPages = 10,
            quranTotalPages = 604,
            hijriDay = 1,
            hijriMonth = 1,
            hijriYear = 1445
        )
        
        // The widget state is stored as JSON: it must come back exactly as it went in.
        val decoded = adapter.fromJson(adapter.toJson(state))
        org.junit.Assert.assertEquals(state, decoded)
    }
}
