package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import io.github.sammynashed.emran.data.PrayerTimeCalculator
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/** One line of the list: a prayer, when it is, and whether its adhan is on. */
private data class TimeRow(
    val key: String,
    val en: String,
    val ar: String,
    val time: LocalDateTime,
    val notifOn: Boolean,
    val isFard: Boolean,
    /** True when today's time has passed and [time] is tomorrow's. */
    val tomorrow: Boolean,
)

/**
 * The Prayers card on Home opens this: today's times and a bell per prayer, and
 * nothing else.
 *
 * The bell is the same `notif_<Prayer>` switch the rest of the app uses
 * ([PrayerViewModel.togglePrayerNotification]), which also reschedules the alarms,
 * so turning one off here silences exactly that adhan and no other.
 *
 * Elder-friendly on purpose: big type, 56 dp bells, and each row says in words
 * whether the adhan is on, so the state never depends on reading an icon.
 */
@Composable
fun PrayerTimesScreen(
    viewModel: PrayerViewModel,
    onBack: () -> Unit,
) {
    val lang = LocalAppLanguage.current
    val ar = lang == "ar"
    val pTimes by viewModel.prayerTimes.collectAsState()
    val fajrOn by viewModel.fahrNotifEnabled.collectAsState()
    val sunriseOn by viewModel.sunriseNotifEnabled.collectAsState()
    val dhuhrOn by viewModel.dhuhrNotifEnabled.collectAsState()
    val asrOn by viewModel.asrNotifEnabled.collectAsState()
    val maghribOn by viewModel.maghribNotifEnabled.collectAsState()
    val ishaOn by viewModel.ishaNotifEnabled.collectAsState()

    // A prayer that has passed shows TOMORROW's time, like the wheel: the page
    // answers "when is the next one", not "what was today's". The clock is
    // re-read every few seconds so a row rolls over the moment its time arrives,
    // without leaving and re-entering the page.
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            tick = System.currentTimeMillis()
        }
    }
    val now = remember(tick, pTimes) { LocalDateTime.now(pTimes.zone) }
    val today = now.toLocalDate()
    // Keyed on pTimes so a change of location, method or adjustment recomputes both days.
    val timesToday = remember(pTimes, today) { viewModel.prayerTimesOn(today) }
    val timesTomorrow = remember(pTimes, today) { viewModel.prayerTimesOn(today.plusDays(1)) }

    fun row(key: String, en: String, ar: String, t: (PrayerTimeCalculator.PrayerTimes) -> LocalDateTime, on: Boolean, fard: Boolean): TimeRow {
        val todays = t(timesToday)
        val passed = !todays.isAfter(now)
        return TimeRow(key, en, ar, if (passed) t(timesTomorrow) else todays, on, fard, passed)
    }

    val rows = listOf(
        row("Fajr", "Fajr", "الفجر", { it.fajr }, fajrOn, true),
        row("Sunrise", "Sunrise", "الشروق", { it.sunrise }, sunriseOn, false),
        row("Dhuhr", "Dhuhr", "الظهر", { it.dhuhr }, dhuhrOn, true),
        row("Asr", "Asr", "العصر", { it.asr }, asrOn, true),
        row("Maghrib", "Maghrib", "المغرب", { it.maghrib }, maghribOn, true),
        row("Isha", "Isha", "العشاء", { it.isha }, ishaOn, true),
    )

    // The next prayer is the soonest of the five, marked so the eye finds it first.
    val nextKey = rows.filter { it.isFard }.minByOrNull { it.time }?.key

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (ar) "رجوع" else "Back", tint = Color.White) }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = if (ar) "مواقيت الصلاة" else "Prayer Times",
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rows.forEach { row ->
                    val isNext = row.key == nextKey
                    val accent = if (isNext) AppTheme.colors.primary else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassCard(RoundedCornerShape(22.dp), accent)
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (ar) row.ar else row.en,
                                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = when {
                                    row.notifOn -> if (ar) "التنبيه مفعّل" else "Adhan on"
                                    else -> if (ar) "التنبيه مغلق" else "Adhan off"
                                } + if (row.tomorrow) (if (ar) " · غداً" else " · tomorrow") else "",
                                color = if (row.notifOn) AppTheme.colors.primary else Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                            )
                        }
                        Text(
                            text = formatPrayerTime(row.time, lang),
                            color = if (isNext) AppTheme.colors.primary else Color.White,
                            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.togglePrayerNotification(row.key) },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (row.notifOn) AppTheme.colors.primary.copy(alpha = 0.18f)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    1.dp,
                                    if (row.notifOn) AppTheme.colors.primary.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                                    CircleShape,
                                ),
                        ) {
                            Icon(
                                imageVector = if (row.notifOn) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                                contentDescription = (if (ar) row.ar else row.en) + if (row.notifOn) {
                                    if (ar) " - التنبيه مفعّل" else " - adhan on"
                                } else {
                                    if (ar) " - التنبيه مغلق" else " - adhan off"
                                },
                                tint = if (row.notifOn) AppTheme.colors.primary else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
