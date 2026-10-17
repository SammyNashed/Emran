package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.PrayerTimeCalculator
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Radius
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.design.Target
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.InterFontFamily
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import kotlinx.coroutines.delay
import java.time.LocalDateTime

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
 * The Prayers door on Home opens this: today's times and a bell per prayer, and
 * nothing else, on the same sky as the rest of the app.
 *
 * A prayer that has passed shows TOMORROW's time (like the wheel did): the page
 * answers "when is the next one". The bell is the same `notif_<Prayer>` switch the
 * rest of the app uses ([PrayerViewModel.togglePrayerNotification]).
 *
 * Elder-friendly on purpose: large numerals, 56 dp bells, and each row says in
 * words whether the adhan is on, so state never depends on reading an icon.
 */
@Composable
fun PrayerTimesScreen(
    viewModel: PrayerViewModel,
    onBack: () -> Unit,
) {
    val lang = LocalAppLanguage.current
    val ar = lang == "ar"
    val sky = LocalSky.current
    val pTimes by viewModel.prayerTimes.collectAsState()
    val fajrOn by viewModel.fahrNotifEnabled.collectAsState()
    val sunriseOn by viewModel.sunriseNotifEnabled.collectAsState()
    val dhuhrOn by viewModel.dhuhrNotifEnabled.collectAsState()
    val asrOn by viewModel.asrNotifEnabled.collectAsState()
    val maghribOn by viewModel.maghribNotifEnabled.collectAsState()
    val ishaOn by viewModel.ishaNotifEnabled.collectAsState()

    // The clock is re-read every few seconds so a row rolls over the moment its time arrives.
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            tick = System.currentTimeMillis()
        }
    }
    val now = remember(tick, pTimes) { LocalDateTime.now(pTimes.zone) }
    val today = now.toLocalDate()
    val timesToday = remember(pTimes, today) { viewModel.prayerTimesOn(today) }
    val timesTomorrow = remember(pTimes, today) { viewModel.prayerTimesOn(today.plusDays(1)) }

    fun row(
        key: String, en: String, ar: String,
        t: (PrayerTimeCalculator.PrayerTimes) -> LocalDateTime, on: Boolean, fard: Boolean,
    ): TimeRow {
        val todays = t(timesToday)
        val passed = !todays.isAfter(now)
        val shown = if (passed) t(timesTomorrow) else todays
        // Friday's Dhuhr row reads Jumu'ah (by the day of the time it shows, so Thursday night's "tomorrow" row does too).
        val (nameEn, nameAr) = if (key == "Dhuhr") io.github.sammynashed.emran.data.PrayerNames.name(key, false, shown.toLocalDate()) to io.github.sammynashed.emran.data.PrayerNames.name(key, true, shown.toLocalDate()) else en to ar
        return TimeRow(key, nameEn, nameAr, shown, on, fard, passed)
    }

    val rows = listOf(
        row("Fajr", "Fajr", "الفجر", { it.fajr }, fajrOn, true),
        row("Sunrise", "Sunrise", "الشروق", { it.sunrise }, sunriseOn, false),
        row("Dhuhr", "Dhuhr", "الظهر", { it.dhuhr }, dhuhrOn, true),
        row("Asr", "Asr", "العصر", { it.asr }, asrOn, true),
        row("Maghrib", "Maghrib", "المغرب", { it.maghrib }, maghribOn, true),
        row("Isha", "Isha", "العشاء", { it.isha }, ishaOn, true),
    )
    val nextKey = rows.filter { it.isFard }.minByOrNull { it.time }?.key
    val haptic = LocalHapticFeedback.current

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Space.screen)
                .padding(bottom = 124.dp),
        ) {
            ScreenHeader(title = if (ar) "مواقيت الصلاة" else "Prayer Times", onBack = onBack)

            // Scrolls on short phones so Maghrib and Isha are never out of reach.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                rows.forEach { r ->
                    val isNext = r.key == nextKey
                    GlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isNext) Modifier.border(1.6.dp, sky.ink.copy(alpha = 0.55f), RoundedCornerShape(Radius.row))
                                else Modifier,
                            ),
                        cornerRadius = Radius.row,
                        tint = if (isNext) 1.5f else 1f,
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (ar) r.ar else r.en,
                                    color = sky.ink, fontSize = 21.sp, fontWeight = FontWeight.Medium,
                                )
                                // On is the default and the bell already says so; words are kept for
                                // the cases that need them: switched off, or already tomorrow's time.
                                val status = listOfNotNull(
                                    if (!r.notifOn) (if (ar) "التنبيه مغلق" else "Adhan off") else null,
                                    if (r.tomorrow) (if (ar) "غداً" else "Tomorrow") else null,
                                ).joinToString(" · ")
                                // Only when there is something to say: an empty line here held the
                                // name above the row's centre.
                                if (status.isNotEmpty()) Text(
                                    text = status,
                                    color = sky.inkSoft, fontSize = 12.sp,
                                    maxLines = 1, softWrap = false,
                                )
                            }
                            // "05:10" large, "AM" small: the clock face stays the hero and the
                            // name and status beside it keep room to sit on one line.
                            val parts = formatPrayerTime(r.time, lang).split(" ")
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = parts[0],
                                    color = sky.ink, fontSize = 28.sp, fontWeight = FontWeight.Light,
                                    fontFamily = InterFontFamily,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    maxLines = 1, softWrap = false,
                                )
                                if (parts.size > 1) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = parts[1],
                                        color = sky.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 5.dp),
                                        maxLines = 1, softWrap = false,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Bell(
                                on = r.notifOn,
                                label = (if (ar) r.ar else r.en) + if (r.notifOn) (if (ar) " - التنبيه مفعّل" else " - adhan on")
                                else (if (ar) " - التنبيه مغلق" else " - adhan off"),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.togglePrayerNotification(r.key)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bell(on: Boolean, label: String, onClick: () -> Unit) {
    val sky = LocalSky.current
    Box56(
        on = on,
        onClick = onClick,
    ) {
        Icon(
            imageVector = if (on) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
            contentDescription = label,
            tint = if (on) sky.ink else sky.inkSoft,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun Box56(on: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val sky = LocalSky.current
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(Target.key)
            .clip(CircleShape)
            .background(sky.ink.copy(alpha = if (on) 0.14f else 0.06f))
            .border(1.dp, sky.ink.copy(alpha = if (on) 0.40f else 0.16f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
