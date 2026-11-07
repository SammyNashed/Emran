package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import java.time.chrono.HijrahDate
import java.time.format.DecimalStyle
import java.util.Locale
import java.time.format.DateTimeFormatter

/**
 * Home: the next prayer, today's timetable, the ayah, one card for what is worth doing now, and
 * the rest of Emran a tap away.
 *
 * The top block is the one thing Home answers (how long to the next prayer) and takes the spare
 * height, so it breathes; everything below it is a tight group of cards at one even gap. Tap a
 * prayer in the timetable to look at it in the top block, and a button brings you back. Short
 * phones scroll instead, so nothing is squeezed out of sight.
 */
@Composable
fun HomeScreen(
    viewModel: PrayerViewModel,
    onNavigateToPrayers: () -> Unit,
    onNavigateToQibla: () -> Unit,
    onNavigateToTasbeeh: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenHadith: () -> Unit,
    onOpenAdhkar: () -> Unit,
    onOpenAdhkarDetail: (String) -> Unit,
    onOpenAyah: (surah: Int, ayah: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = LocalAppLanguage.current
    val ar = lang == "ar"
    val pTimes by viewModel.prayerTimes.collectAsState()
    val countdown by viewModel.countdownState.collectAsState()
    val today by viewModel.currentDate.collectAsState()
    val iqama by viewModel.iqamaMinutes.collectAsState()
    val place by viewModel.locationName.collectAsState()
    val following by viewModel.followLocation.collectAsState()
    val zone by viewModel.zoneId.collectAsState()

    // "Now" at the prayer location, ticking with the countdown.
    val now = remember(countdown.formattedRemaining, zone) {
        // The design debug hour (SkyDebug) moves this clock with the sky and the countdown.
        io.github.sammynashed.emran.ui.design.SkyDebug.timeOfDay()?.let { java.time.LocalDateTime.of(today, it) }
            ?: java.time.LocalDateTime.now(io.github.sammynashed.emran.data.PrayerTimeCalculator.safeZone(zone))
    }
    val ayah = rememberDailyAyah(today, english = true)
    val badgeHijri by viewModel.calendarBadgeHijri.collectAsState()
    val badgeDay = remember(today, badgeHijri) {
        if (badgeHijri) HijrahDate.from(today).get(java.time.temporal.ChronoField.DAY_OF_MONTH) else today.dayOfMonth
    }

    var dayOffset by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var pickedKey by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val shown = if (dayOffset == 0) pTimes else remember(today, dayOffset, pTimes) { viewModel.prayerTimesOn(today.plusDays(dayOffset.toLong())) }
    // The prayer the user picked, unless it is simply the real next one (then there is nothing to preview).
    val preview = pickedKey?.let { k ->
        val at = when (k) { "Fajr" -> shown.fajr; "Dhuhr" -> shown.dhuhr; "Asr" -> shown.asr; "Maghrib" -> shown.maghrib; else -> shown.isha }
        if (at == countdown.nextPrayerAt) null else HeroPreview(k, at)
    }
    val tomorrowFajr = remember(today, pTimes) { viewModel.prayerTimesOn(today.plusDays(1)).fajr }

    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        // Short phones and big system fonts scroll rather than clip anything.
        val short = maxHeight < 790.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = Space.screen)
                .padding(bottom = 112.dp),
        ) {
            // Only what is about the day itself: today's date (the calendar) and where the times are for.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The page reads from the start edge: the city first (right in Arabic), the calendar at the far end.
                LocationChip(io.github.sammynashed.emran.data.LocationPresets.getLocalizedName(place, lang).substringBefore('،').substringBefore(',').trim(), following, onNavigateToLocation, Modifier.widthIn(max = 190.dp))
                Spacer(Modifier.weight(1f))
                CalendarButton(day = badgeDay, label = if (ar) "التقويم" else "Calendar", onClick = onOpenCalendar)
            }

            // The answer Home exists for, at its natural height with room to breathe.
            Box((if (short) Modifier.padding(vertical = Space.xl) else Modifier.weight(1f).heightIn(min = 150.dp)).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val key = preview?.key ?: countdown.nextPrayerName.removeSuffix(" (Tomorrow)")
                val fajr by viewModel.fahrNotifEnabled.collectAsState()
                val sunrise by viewModel.sunriseNotifEnabled.collectAsState()
                val dhuhr by viewModel.dhuhrNotifEnabled.collectAsState()
                val asr by viewModel.asrNotifEnabled.collectAsState()
                val maghrib by viewModel.maghribNotifEnabled.collectAsState()
                val isha by viewModel.ishaNotifEnabled.collectAsState()
                val bellOn = when (key) { "Fajr" -> fajr; "Sunrise" -> sunrise; "Dhuhr" -> dhuhr; "Asr" -> asr; "Maghrib" -> maghrib; else -> isha }
                NextPrayerHero(
                    pTimes, countdown.nextPrayerName, countdown.nextPrayerAt, countdown.formattedRemaining, iqama, now, ar,
                    preview = preview,
                    onReset = { pickedKey = null; dayOffset = 0 },
                    bellOn = bellOn,
                    onBell = { viewModel.togglePrayerNotification(key) },
                )
            }

            // Everything below is a compact group at one even gap; the spare height goes to the block above.
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                PrayerRail(
                    shown, dayOffset, countdown.nextPrayerName, countdown.nextPrayerAt, now, ar,
                    selectedKey = pickedKey,
                    onSelect = { k -> pickedKey = if (pickedKey == k) null else k },
                    onStep = { dayOffset = (dayOffset + it).coerceIn(-365, 365) },
                    onToday = { dayOffset = 0 },
                )
                AyahOfTheDayCard(ayah = ayah, ar = ar, onOpen = onOpenAyah)
                DoorsCard(
                    pTimes, now, ar,
                    onTasbeeh = onNavigateToTasbeeh, onAdhkar = onOpenAdhkar, onAdhkarNow = onOpenAdhkarDetail,
                    onQibla = onNavigateToQibla, onHadith = onOpenHadith,
                )
            }
        }
    }
}
