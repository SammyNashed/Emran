package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.ui.design.DayArc
import io.github.sammynashed.emran.ui.design.GlassRow
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.tour.tourTarget
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter

/**
 * Home: the day as one arc, what is next, and four calm doors.
 *
 * Everything else lives one tap away (More). Nothing here scrolls, so the glass
 * rows always sit exactly over the sky they are bending.
 */
@Composable
fun HomeScreen(
    viewModel: PrayerViewModel,
    onNavigateToPrayers: () -> Unit,
    onNavigateToQibla: () -> Unit,
    onNavigateToQuran: () -> Unit,
    onNavigateToMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = LocalAppLanguage.current
    val ar = lang == "ar"
    val sky = LocalSky.current
    val pTimes by viewModel.prayerTimes.collectAsState()
    val countdown by viewModel.countdownState.collectAsState()
    val today by viewModel.currentDate.collectAsState()

    val hijri = remember(today) {
        try {
            HijrahDate.from(today).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) {
            "1447"
        }
    }
    val gregorian = remember(today) { today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen)
            .padding(bottom = 124.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hijri, color = sky.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, bypassNumerals = true)
            Text(gregorian, color = sky.inkSoft, fontSize = 12.sp, bypassNumerals = true)
        }

        DayArc(
            times = pTimes,
            nextName = countdown.nextPrayerName,
            countdown = countdown.formattedRemaining,
            nextTime = countdown.nextPrayerTime,
            lang = lang,
            modifier = Modifier.padding(top = Space.sm).tourTarget("spiritual_prayer_compass"),
        )

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            GlassRow(
                title = if (ar) "مواقيت الصلاة" else "Prayer Times",
                icon = Icons.Outlined.Schedule,
                onClick = onNavigateToPrayers,
                modifier = Modifier.tourTarget("today_prayers_widget_card"),
            )
            GlassRow(
                title = if (ar) "القبلة" else "Qibla",
                icon = Icons.Outlined.Explore,
                onClick = onNavigateToQibla,
                modifier = Modifier.tourTarget("home_widget_qibla"),
            )
            GlassRow(
                title = if (ar) "القرآن" else "Quran",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                onClick = onNavigateToQuran,
            )
            GlassRow(
                title = if (ar) "المزيد" else "More",
                icon = Icons.Outlined.MoreHoriz,
                onClick = onNavigateToMore,
            )
        }
    }
}
