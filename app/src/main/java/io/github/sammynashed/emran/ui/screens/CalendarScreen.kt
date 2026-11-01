package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Radius
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.design.glassEntrance
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.InterFontFamily
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Locale

private val TNUM = TextStyle(fontFeatureSettings = "tnum")
private val tightLines = androidx.compose.ui.text.style.LineHeightStyle(
    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
)

/** A day the Hijri year counts down to. Month and day are Hijri. */
private class Occasion(val ar: String, val en: String, val month: Int, val day: Int)

private val OCCASIONS = listOf(
    Occasion("رأس السنة الهجرية", "Islamic New Year", 1, 1),
    Occasion("يوم عاشوراء", "Ashura", 1, 10),
    Occasion("بداية رمضان", "Start of Ramadan", 9, 1),
    Occasion("عيد الفطر", "Eid al-Fitr", 10, 1),
    Occasion("يوم عرفة", "Day of Arafah", 12, 9),
    Occasion("عيد الأضحى", "Eid al-Adha", 12, 10),
)

private fun hijriOn(year: Int, month: Int, day: Int): LocalDate = LocalDate.from(HijrahDate.of(year, month, day))

/** The next time [month]/[day] comes round, today included, as a date. */
private fun nextOccurrence(today: LocalDate, month: Int, day: Int): LocalDate {
    val year = HijrahDate.from(today).get(ChronoField.YEAR)
    val thisYear = hijriOn(year, month, day)
    return if (!thisYear.isBefore(today)) thisYear else hijriOn(year + 1, month, day)
}

/**
 * Everything about the date on ONE page, nothing hidden under a scroll: today in both calendars, how long
 * until Ramadan (and the next few days the year counts toward), and the Hijri month to page through. The month
 * takes whatever height is left, so the page fits any phone. The dates follow the Umm al-Qura calendar, which
 * can differ by a day from the moon your own country sights.
 */
@Composable
fun CalendarScreen(viewModel: PrayerViewModel, lang: String, onBack: () -> Unit) {
    val ar = lang == "ar"
    val sky = LocalSky.current
    val today by viewModel.currentDate.collectAsState()
    val hijriLead by viewModel.calendarBadgeHijri.collectAsState()
    val locale = remember(ar) { if (ar) Locale("ar") else Locale.ENGLISH }
    val std = DecimalStyle.STANDARD
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }

    // With a big system font the page cannot fit one screen: it scrolls (and the month rows get a fixed height).
    val big = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    val huge = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.6f
    val hToday = remember(today) { HijrahDate.from(today) }
    val shown = remember(hToday, monthOffset) { hToday.plus(monthOffset.toLong(), ChronoUnit.MONTHS).with(ChronoField.DAY_OF_MONTH, 1) }
    fun fmt(pattern: String, d: java.time.chrono.ChronoLocalDate) = DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(std).format(d)

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize().padding(horizontal = Space.screen).padding(bottom = 112.dp)) {
            ScreenHeader(
                title = if (ar) "التقويم" else "Calendar",
                subtitle = if (ar) "أم القرى · قد يختلف يوماً" else "Umm al-Qura \u00B7 \u00B11 day",
                onBack = onBack,
                // Which date leads, here and on the icon: the Gregorian day (default) or the Hijri one.
                actions = {
                    if (!huge) io.github.sammynashed.emran.ui.design.GlassSegmented(
                        // The control lays out left to right; in Arabic the first choice belongs on the right.
                        options = listOf("greg" to (if (ar) "ميلادي" else "Greg."), "hijri" to (if (ar) "هجري" else "Hijri"))
                            .let { if (ar) it.reversed() else it },
                        selectedKey = if (hijriLead) "hijri" else "greg",
                        onSelect = { viewModel.setCalendarBadgeHijri(it == "hijri") },
                        modifier = Modifier.width((124 * (androidx.compose.ui.platform.LocalDensity.current.fontScale / 1.15f).coerceIn(1f, 1.6f)).dp),
                    )
                },
            )
            Column(
                (if (big) Modifier.verticalScroll(rememberScrollState()) else Modifier.weight(1f)).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (huge) {
                    io.github.sammynashed.emran.ui.design.GlassSegmented(
                        options = listOf("greg" to (if (ar) "ميلادي" else "Greg."), "hijri" to (if (ar) "هجري" else "Hijri"))
                            .let { if (ar) it.reversed() else it },
                        selectedKey = if (hijriLead) "hijri" else "greg",
                        onSelect = { viewModel.setCalendarBadgeHijri(it == "hijri") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Today: the leading calendar's day big, the other one quiet underneath.
                GlassPanel(Modifier.fillMaxWidth().glassEntrance(0), cornerRadius = Radius.row) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (hijriLead) hToday.get(ChronoField.DAY_OF_MONTH) else today.dayOfMonth).toString(), color = sky.ink, fontSize = 54.sp,
                            fontWeight = FontWeight.ExtraLight, fontFamily = InterFontFamily, bypassNumerals = true, maxLines = 1,
                        )
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fmt("EEEE", today), color = sky.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (hijriLead) {
                                Text(fmt("MMMM y", hToday), color = sky.ink, fontSize = 16.sp, maxLines = 2)
                                Text(fmt("d MMMM y", today), color = sky.inkSoft, fontSize = 14.sp, maxLines = 2)
                            } else {
                                Text(fmt("MMMM y", today), color = sky.ink, fontSize = 16.sp, maxLines = 2)
                                Text(fmt("d MMMM y", hToday), color = sky.inkSoft, fontSize = 14.sp, maxLines = 2)
                            }
                        }
                    }
                }

                ComingUpCard(today, hToday, ar, locale, Modifier.glassEntrance(1))

                // The Hijri month, with the Gregorian day under each number. It takes the height that is left.
                GlassPanel(Modifier.fillMaxWidth().then(if (big) Modifier else Modifier.weight(1f)).glassEntrance(2), cornerRadius = Radius.row) {
                    Column((if (big) Modifier.fillMaxWidth() else Modifier.fillMaxSize()).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            MonthArrow(true, if (ar) "الشهر السابق" else "Previous month") { monthOffset-- }
                            Box(
                                Modifier.weight(1f).heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp)).clickable(
                                    enabled = monthOffset != 0, interactionSource = remember { MutableInteractionSource() }, indication = null,
                                ) { monthOffset = 0 },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(fmt("MMMM y", shown), color = sky.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    val a = LocalDate.from(shown)
                                    val b = a.plusDays(shown.lengthOfMonth() - 1L)
                                    val range = if (a.month == b.month) DateTimeFormatter.ofPattern("MMMM y", locale).format(a)
                                    else DateTimeFormatter.ofPattern("MMM", locale).format(a) + " – " + DateTimeFormatter.ofPattern("MMM y", locale).format(b)
                                    Text(range, color = sky.inkSoft, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
                                }
                            }
                            MonthArrow(false, if (ar) "الشهر التالي" else "Next month") { monthOffset++ }
                        }
                        Spacer(Modifier.height(4.dp))
                        MonthGrid(shown, today, ar, locale, (if (big) Modifier else Modifier.weight(1f)).fillMaxWidth(), rowHeight = if (big) (38 * androidx.compose.ui.platform.LocalDensity.current.fontScale).dp else null)
                    }
                }
            }
        }
    }
}

/**
 * One card for what the year counts toward: Ramadan first and big (days left, or which day of it you are in),
 * then the next two days after it.
 */
@Composable
private fun ComingUpCard(today: LocalDate, hToday: HijrahDate, ar: Boolean, locale: Locale, modifier: Modifier) {
    val sky = LocalSky.current
    val std = DecimalStyle.STANDARD
    val inRamadan = hToday.get(ChronoField.MONTH_OF_YEAR) == 9
    val title: String
    val big: String
    val unit: String
    val foot: String
    if (inRamadan) {
        val eid = nextOccurrence(today, 10, 1)
        title = if (ar) "أنت في شهر رمضان" else "You are in Ramadan"
        big = hToday.get(ChronoField.DAY_OF_MONTH).toString()
        unit = if (ar) "من رمضان" else "of Ramadan"
        foot = (if (ar) "باقي للعيد " else "Eid in ") + ChronoUnit.DAYS.between(today, eid) + (if (ar) " يوم" else " days")
    } else {
        val start = nextOccurrence(today, 9, 1)
        title = if (ar) "باقي لرمضان" else "Until Ramadan"
        big = ChronoUnit.DAYS.between(today, start).toString()
        unit = if (ar) "يوم" else "days"
        foot = DateTimeFormatter.ofPattern("EEEE d MMMM y", locale).withDecimalStyle(std).format(start)
    }
    val others = remember(today) {
        OCCASIONS.filter { !(it.month == 9 && it.day == 1) }
            .map { it to nextOccurrence(today, it.month, it.day) }.sortedBy { it.second }.take(2)
    }
    GlassPanel(modifier.fillMaxWidth(), cornerRadius = Radius.row) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = sky.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(foot, color = sky.inkSoft, fontSize = 13.sp, maxLines = 2)
                }
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(big, color = sky.ink, fontSize = 40.sp, fontWeight = FontWeight.ExtraLight, fontFamily = InterFontFamily, style = TNUM, bypassNumerals = true, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text(unit, color = sky.inkSoft, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(bottom = 7.dp))
                }
            }
            others.forEach { (o, on) ->
                val left = ChronoUnit.DAYS.between(today, on)
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(sky.ink.copy(alpha = 0.10f)))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (ar) o.ar else o.en, color = sky.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            left == 0L -> if (ar) "اليوم" else "Today"
                            ar -> "بعد $left يوم"
                            else -> "in $left days"
                        },
                        color = sky.inkSoft, fontSize = 14.sp, bypassNumerals = true, maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(back: Boolean, label: String, onClick: () -> Unit) {
    val sky = LocalSky.current
    Box(
        Modifier.size(46.dp).clip(CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (back) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            label, tint = sky.ink, modifier = Modifier.size(30.dp),
        )
    }
}

/** Saturday-first weeks (as the region's calendars run), the Hijri number big and the Gregorian one small. Rows share the height they are given. */
@Composable
private fun MonthGrid(first: HijrahDate, today: LocalDate, ar: Boolean, locale: Locale, modifier: Modifier, rowHeight: androidx.compose.ui.unit.Dp? = null) {
    val sky = LocalSky.current
    val week = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val arNames = listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة")
    val start = LocalDate.from(first)
    val lead = week.indexOf(start.dayOfWeek)
    val len = first.lengthOfMonth()
    val rows = (lead + len + 6) / 7
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            week.forEachIndexed { i, d ->
                Text(
                    if (ar) arNames[i] else d.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                    color = if (d == DayOfWeek.FRIDAY) sky.ink else sky.inkSoft, fontSize = (12f * minOf(androidx.compose.ui.platform.LocalDensity.current.fontScale, 1.2f) / androidx.compose.ui.platform.LocalDensity.current.fontScale).sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().then(if (rowHeight != null) Modifier.height(rowHeight) else Modifier.weight(1f))) {
                for (c in 0 until 7) {
                    val n = r * 7 + c - lead + 1
                    if (n < 1 || n > len) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val g = start.plusDays(n - 1L)
                        val isToday = g == today
                        Column(
                            Modifier.weight(1f).fillMaxSize().padding(2.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (isToday) sky.ink.copy(alpha = 0.20f) else androidx.compose.ui.graphics.Color.Transparent),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            // Line boxes trimmed to the digits, so two stacked numbers fit a short row without being cut.
                            Text(
                                n.toString(), color = sky.ink, fontSize = 16.sp, fontFamily = InterFontFamily,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                style = TextStyle(fontFeatureSettings = "tnum", lineHeight = 18.sp, lineHeightStyle = tightLines),
                                bypassNumerals = true, maxLines = 1,
                            )
                            Text(
                                g.dayOfMonth.toString(), color = sky.inkSoft, fontSize = 10.sp, fontFamily = InterFontFamily,
                                style = TextStyle(lineHeight = 12.sp, lineHeightStyle = tightLines), bypassNumerals = true, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
