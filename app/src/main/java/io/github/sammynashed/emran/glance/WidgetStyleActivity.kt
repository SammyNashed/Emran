package io.github.sammynashed.emran.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sammynashed.emran.glance.art.WidgetArt
import io.github.sammynashed.emran.glance.mapper.WidgetStateMapper
import io.github.sammynashed.emran.glance.provider.PrayerWidgetStateProvider
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.state.EmranWidgetStateDefinition
import io.github.sammynashed.emran.service.EmranWidgetUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Each widget's chosen glass tint, by its app-widget id. */
object WidgetStyles {
    private fun prefs(c: Context) = c.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)
    fun get(c: Context, id: Int): WidgetArt.Tint =
        runCatching { WidgetArt.Tint.valueOf(prefs(c).getString("tint_$id", null) ?: "AUTO") }.getOrDefault(WidgetArt.Tint.AUTO)
    fun set(c: Context, id: Int, t: WidgetArt.Tint) = prefs(c).edit().putString("tint_$id", t.name).apply()

    /** Saves the tint and redraws that one widget on the home screen right away. */
    suspend fun apply(c: Context, id: Int, t: WidgetArt.Tint) {
        set(c, id, t)
        runCatching {
            val gm = androidx.glance.appwidget.GlanceAppWidgetManager(c)
            val glanceId = gm.getGlanceIdBy(id)
            androidx.glance.appwidget.state.updateAppWidgetState(c, EmranWidgetStateDefinition, glanceId) { it.copy(tint = t.name) }
            widgetFor(kindOf(c, id)).update(c, glanceId)
        }
    }

    private fun widgetFor(kind: WidgetArt.Kind): androidx.glance.appwidget.GlanceAppWidget = when (kind) {
        WidgetArt.Kind.DAY_ARABIC -> DayArabicWidget()
        WidgetArt.Kind.DAY_ENGLISH -> DayEnglishWidget()
        WidgetArt.Kind.PRAYER_PROGRESS -> PrayerProgressWidget()
        WidgetArt.Kind.PRAYER_TABLE -> PrayerTableWidget()
        WidgetArt.Kind.NEXT_PRAYER -> NextPrayerWidget()
        WidgetArt.Kind.HIJRI_DATE -> HijriDateWidget()
        WidgetArt.Kind.HIJRI_MONTH -> HijriMonthWidget()
        WidgetArt.Kind.AYAH -> AyahWidget()
        WidgetArt.Kind.CLOCK -> ClockWidget()
        WidgetArt.Kind.OCCASION -> OccasionWidget()
    }

    /** Which widget an id belongs to, from its receiver. */
    fun kindOf(c: Context, id: Int): WidgetArt.Kind {
        val cls = AppWidgetManager.getInstance(c).getAppWidgetInfo(id)?.provider?.className.orEmpty()
        return when {
            cls.endsWith("DayArabicWidgetReceiver") -> WidgetArt.Kind.DAY_ARABIC
            cls.endsWith("DayEnglishWidgetReceiver") -> WidgetArt.Kind.DAY_ENGLISH
            cls.endsWith("PrayerProgressWidgetReceiver") -> WidgetArt.Kind.PRAYER_PROGRESS
            cls.endsWith("PrayerTableWidgetReceiver") -> WidgetArt.Kind.PRAYER_TABLE
            cls.endsWith("HijriDateWidgetReceiver") -> WidgetArt.Kind.HIJRI_DATE
            cls.endsWith("HijriMonthWidgetReceiver") -> WidgetArt.Kind.HIJRI_MONTH
            cls.endsWith("AyahWidgetReceiver") -> WidgetArt.Kind.AYAH
            cls.endsWith("ClockWidgetReceiver") -> WidgetArt.Kind.CLOCK
            cls.endsWith("OccasionWidgetReceiver") -> WidgetArt.Kind.OCCASION
            else -> WidgetArt.Kind.NEXT_PRAYER
        }
    }
}

/**
 * Opens when a widget is added (and from the launcher's pencil later, Android 12+): a live
 * preview of that widget and a row of glass tints. Saving stores the tint for this widget only.
 */
class WidgetStyleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the widget with its default (configuration is optional).
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val kind = WidgetStyles.kindOf(this, id)
        val ar = getSharedPreferences("emran_settings", MODE_PRIVATE).getString("current_language", "en") == "ar"

        setContent {
            var tint by remember { mutableStateOf(WidgetStyles.get(this, id)) }
            val state by produceState<EmranWidgetState?>(null) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val d = PrayerWidgetStateProvider(this@WidgetStyleActivity).provideDomainData()
                        WidgetStateMapper().mapToPrayerWidgetState(id, d.prayerData, d.calendarData, d.statsData, d.themeData, d.localeData)
                    }.getOrNull()
                }
            }
            val wide = kind in setOf(WidgetArt.Kind.DAY_ARABIC, WidgetArt.Kind.DAY_ENGLISH, WidgetArt.Kind.PRAYER_PROGRESS, WidgetArt.Kind.AYAH)
            val (wDp, hDp) = when (kind) {
                WidgetArt.Kind.DAY_ENGLISH -> 340f to 90f
                else -> if (wide) 340f to 170f else 180f to 180f
            }
            val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, state, tint) {
                val s = state ?: return@produceState
                value = withContext(Dispatchers.Default) { WidgetArt.render(this@WidgetStyleActivity, kind, s, wDp, hDp, tint).asImageBitmap() }
            }
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF3A2A6A), Color(0xFF1B2140), Color(0xFF0E1424))))
                    .statusBarsPadding().navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (ar) "لون الأداة" else "Widget style", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(if (ar) "اختر لون الزجاج" else "Choose the glass tint", color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    preview?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxWidth(if (wide) 1f else 0.6f)) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(WidgetArt.Tint.entries) { t ->
                        val body = WidgetArt.body(this@WidgetStyleActivity, t, kind)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { tint = t; lifecycleScope.launch { WidgetStyles.apply(this@WidgetStyleActivity, id, t) } }) {
                            Box(
                                Modifier.size(54.dp).clip(CircleShape)
                                    .background(body?.let { Color(it).copy(alpha = 1f) } ?: Color.White.copy(alpha = 0.25f))
                                    .border(if (t == tint) 3.dp else 1.dp, Color.White.copy(alpha = if (t == tint) 1f else 0.35f), CircleShape),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(if (ar) t.labelAr else t.label, color = Color.White.copy(alpha = if (t == tint) 1f else 0.7f), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(Color.White)
                        .clickable {
                            lifecycleScope.launch {
                                WidgetStyles.apply(this@WidgetStyleActivity, id, tint)
                                WorkManager.getInstance(this@WidgetStyleActivity).enqueue(OneTimeWorkRequestBuilder<EmranWidgetUpdateWorker>().build())
                                finish()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(if (ar) "تم" else "Done", color = Color(0xFF14182A), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
