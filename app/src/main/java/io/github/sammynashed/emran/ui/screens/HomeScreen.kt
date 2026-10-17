package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.sammynashed.emran.ui.design.DayArc
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
 * Home: the day as one arc, today's ayah, and four live glass tiles.
 *
 * The tiles do things in place (count tasbeeh, point to the Qibla) and open their full
 * screen on a tap (long-press for tasbeeh). Nothing here scrolls, so the glass always
 * sits exactly over the sky it is bending.
 */
@Composable
fun HomeScreen(
    viewModel: PrayerViewModel,
    onNavigateToPrayers: () -> Unit,
    onNavigateToQibla: () -> Unit,
    onNavigateToTasbeeh: () -> Unit,
    onNavigateToMore: () -> Unit,
    onOpenAyah: (surah: Int, ayah: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = LocalAppLanguage.current
    val ar = lang == "ar"
    val sky = LocalSky.current
    val pTimes by viewModel.prayerTimes.collectAsState()
    val countdown by viewModel.countdownState.collectAsState()
    val today by viewModel.currentDate.collectAsState()

    // The day on the left (weekday over the Hijri date), the Gregorian date on the right:
    // one row of its own above the arc, so the dates never crowd the prayer times.
    val locale = remember(ar) { if (ar) Locale("ar") else Locale.ENGLISH }
    val weekday = remember(today, locale) { DateTimeFormatter.ofPattern("EEEE", locale).format(today) }
    val hijri = remember(today, locale) {
        runCatching {
            DateTimeFormatter.ofPattern("d MMMM y", locale)
                .withDecimalStyle(DecimalStyle.STANDARD)
                .format(HijrahDate.from(today))
        }.getOrDefault("")
    }
    val gregorian = remember(today, locale) {
        DateTimeFormatter.ofPattern("d MMMM y", locale).withDecimalStyle(DecimalStyle.STANDARD).format(today)
    }
    val ayah = rememberDailyAyah(today, english = true)

    // Ordinary phones: one screen, the ayah taking whatever the arc and tiles leave. Short
    // phones (under ~720 dp of height) scroll instead, so nothing is squeezed out of sight.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
    val short = maxHeight < 720.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = Space.screen)
            .padding(bottom = 112.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.sm), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(weekday, color = sky.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(hijri, color = sky.inkSoft, fontSize = 14.sp)
            }
            Text(gregorian, color = sky.inkSoft, fontSize = 14.sp)
        }

        DayArc(
            times = pTimes,
            nextName = countdown.nextPrayerName,
            countdown = countdown.formattedRemaining,
            nextTime = countdown.nextPrayerAt?.let { io.github.sammynashed.emran.data.TimeFormat.full(it, lang == "ar") } ?: countdown.nextPrayerTime,
            lang = lang,
            modifier = Modifier,
        )

        // The arc and the tiles keep their size; the ayah takes what is left, and its words
        // fit that space (see FitText), so a long ayah never squeezes the tiles.
        Column(if (short) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()) {
            AyahOfTheDayCard(ayah = ayah, ar = ar, onOpen = onOpenAyah)
        }

        Spacer(Modifier.height(Space.md))

        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PrayerTimesTile(
                    times = pTimes, nextName = countdown.nextPrayerName, ar = ar, onOpen = onNavigateToPrayers,
                    modifier = Modifier.weight(1f),
                )
                QiblaTile(
                    ar = ar, onOpen = onNavigateToQibla,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                TasbeehTile(ar = ar, onOpen = onNavigateToTasbeeh, modifier = Modifier.weight(1f))
                MoreTile(ar = ar, onOpen = onNavigateToMore, modifier = Modifier.weight(1f))
            }
        }
    }
    }
}
