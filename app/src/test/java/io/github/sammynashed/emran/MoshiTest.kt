package io.github.sammynashed.emran

import org.junit.Test
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.state.WidgetType
import io.github.sammynashed.emran.ui.theme.EmranTheme

class MoshiTest {
    @Test
    fun testSerialization() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(EmranWidgetState::class.java)
        val state = EmranWidgetState(
            nextPrayerName = "Maghrib", 
            locationName = "Aleppo",
            widgetType = WidgetType.PRAYER,
            themeOverride = EmranTheme.EXPRESSIVE
        )
        val json = adapter.toJson(state)
        println("JSON OUTPUT: $json")
        val back = adapter.fromJson(json)
        println("BACK: $back")
    }
}
