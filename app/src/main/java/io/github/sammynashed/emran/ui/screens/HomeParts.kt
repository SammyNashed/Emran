package io.github.sammynashed.emran.ui.screens

import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.PrayerNames
import io.github.sammynashed.emran.data.PrayerTimeCalculator.PrayerTimes
import io.github.sammynashed.emran.data.TimeFormat
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Radius
import io.github.sammynashed.emran.ui.design.quietStars
import io.github.sammynashed.emran.ui.design.glassEntrance
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.InterFontFamily
import java.time.Duration
import java.time.LocalDateTime

private val TNUM = TextStyle(fontFeatureSettings = "tnum")

/**
 * [base] sp that follows the user's font size only up to [maxScale]. For text in a row that cannot get wider
 * (the five prayer columns): beyond that it would collide with its neighbours, so it stops growing, while
 * everything with room to wrap or reflow keeps scaling fully.
 */
@Composable
private fun cappedSp(base: Float, maxScale: Float = 1.2f): androidx.compose.ui.unit.TextUnit {
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
    return (base * minOf(fs, maxScale) / fs).sp
}

/** A round door in the top bar: one icon on a soft disc, same size and weight as its neighbours. */
@Composable
private fun BarDisc(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(sky.ink.copy(alpha = 0.10f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The calendar door: a calendar with today's date on it, so the date is still there at a glance. */
@Composable
fun CalendarButton(day: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    BarDisc(label, onClick, modifier) {
        Icon(Icons.Outlined.CalendarToday, null, tint = sky.ink, modifier = Modifier.size(28.dp))
        Text(
            day.toString(), color = sky.ink, fontSize = cappedSp(11.5f), lineHeight = cappedSp(13f), fontWeight = FontWeight.Bold, fontFamily = InterFontFamily,
            bypassNumerals = true, maxLines = 1, modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** The place the times are for. The crosshair means Emran is following you; the pin means it is the place you chose. */
@Composable
fun LocationChip(name: String, following: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(sky.ink.copy(alpha = 0.10f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .defaultMinSize(minHeight = 46.dp)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (following) Icons.Filled.MyLocation else Icons.Outlined.LocationOn, null, tint = sky.ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(name, color = sky.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A prayer the user tapped in the timetable, shown in the top block instead of the real next one. */
class HeroPreview(val key: String, val adhan: LocalDateTime)

private fun hms(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/**
 * The one thing Home exists to answer: how long until the next prayer.
 *
 * "Time left to the adhan", the countdown, and the prayer with its time beside its iqama. From an
 * adhan until its iqama it answers the question people actually have at that moment instead: time
 * left to the iqama. Tap another prayer in the timetable and the block shows that one instead
 * (counting to it, or since it), with a button back to the real next prayer.
 */
@Composable
fun NextPrayerHero(
    times: PrayerTimes,
    nextName: String,
    nextAt: LocalDateTime?,
    countdown: String,
    iqama: Map<String, Int>,
    now: LocalDateTime,
    ar: Boolean,
    preview: HeroPreview?,
    onReset: () -> Unit,
    /** Whether this prayer's adhan is on (null = no bell shown), and the switch for it. */
    bellOn: Boolean?,
    onBell: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sky = LocalSky.current
    val use24 = TimeFormat.use24
    val live = if (preview != null) null else listOf("Fajr" to times.fajr, "Dhuhr" to times.dhuhr, "Asr" to times.asr, "Maghrib" to times.maghrib, "Isha" to times.isha)
        .firstNotNullOfOrNull { (key, adhan) ->
            val at = io.github.sammynashed.emran.data.Iqama.at(key, adhan, iqama)
            if (at != null && !now.isBefore(adhan) && now.isBefore(at)) Triple(key, adhan, at) else null
        }
    val key = preview?.key ?: nextName.removeSuffix(" (Tomorrow)")

    // What the big number counts, what it counts to, and the one line beside it.
    val label: String
    val big: String
    val whoName: String
    val whoAt: LocalDateTime?
    val extraLabel: String?
    val extraAt: LocalDateTime?
    if (preview != null) {
        val secs = Duration.between(now, preview.adhan).seconds
        val name = PrayerNames.name(preview.key, ar, preview.adhan.toLocalDate())
        val sun = preview.key == "Sunrise"
        // The same words as for the real next prayer: the prayer's name is in the pill just below.
        label = when {
            secs >= 0 && sun -> if (ar) "باقي للشروق" else "Time to sunrise"
            secs >= 0 -> if (ar) "باقي للأذان" else "Time to adhan"
            sun -> if (ar) "مضى على الشروق" else "Since sunrise"
            else -> if (ar) "مضى على الأذان" else "Since adhan"
        }
        big = hms(kotlin.math.abs(secs))
        whoName = name
        whoAt = preview.adhan
        val iq = io.github.sammynashed.emran.data.Iqama.at(preview.key, preview.adhan, iqama)
        extraLabel = if (iq != null) (if (ar) "الإقامة" else "Iqama") else null
        extraAt = iq
    } else if (live != null) {
        val left = Duration.between(now, live.third).seconds.coerceAtLeast(0)
        val who = PrayerNames.name(live.first, ar, live.second.toLocalDate())
        label = if (ar) "باقي لإقامة $who" else "Time to $who iqama"
        big = "%02d:%02d".format(left / 60, left % 60)
        whoName = if (ar) "الإقامة" else "Iqama"
        whoAt = live.third
        extraLabel = if (ar) "الأذان" else "Adhan"
        extraAt = live.second
    } else {
        val name = PrayerNames.name(nextName, ar)
        label = if (key == "Sunrise") (if (ar) "باقي للشروق" else "Time to sunrise") else (if (ar) "باقي للأذان" else "Time to adhan")
        big = countdown
        whoName = name
        whoAt = nextAt
        val iq = nextAt?.let { io.github.sammynashed.emran.data.Iqama.at(key, it, iqama) }
        extraLabel = if (iq != null) (if (ar) "الإقامة" else "Iqama") else null
        extraAt = iq
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = sky.inkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.quietStars())
            if (bellOn != null && live == null) {
                val haptic = LocalHapticFeedback.current
                Spacer(Modifier.width(6.dp))
                // The adhan switch for this prayer, where you are already looking at it.
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBell()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (bellOn) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                        if (bellOn) (if (ar) "الأذان مفعّل" else "Adhan on") else (if (ar) "الأذان مغلق" else "Adhan off"),
                        tint = if (bellOn) sky.ink else sky.inkSoft, modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Text(
            big, color = sky.ink, fontSize = 54.sp, fontWeight = FontWeight.ExtraLight, fontFamily = InterFontFamily,
            style = TNUM, bypassNumerals = true, maxLines = 1, softWrap = false, modifier = Modifier.quietStars(),
        )
        Spacer(Modifier.height(2.dp))
        // The prayer and its iqama side by side: one glance, one row.
        // Two pills side by side; with a big system font they wrap onto a second line instead of overflowing.
        // When the prayer shown changes (a preview, or the real next one moving on) the new pair rises in as the
        // old one fades, instead of the words snapping.
        androidx.compose.animation.AnimatedContent(
            targetState = Triple(whoName, whoAt, extraAt),
            transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(170, delayMillis = 70)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(230, delayMillis = 70)) { it / 3 }) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(80))
            },
            contentKey = { it.first },   // the same prayer re-reading (a new second) is not a change
            label = "heroPills",
        ) { (name, at, extra) ->
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                Modifier.quietStars().fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (at != null) HeroPill(name, at, use24, ar, strong = true)
                if (extraLabel != null && extra != null) HeroPill(extraLabel, extra, use24, ar, strong = false)
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = preview != null,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180, delayMillis = 60)) +
                androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(0.85f, 380f)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(110)) +
                androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(180)),
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            val haptic = LocalHapticFeedback.current
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(sky.ink.copy(alpha = 0.18f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReset()
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .quietStars(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Replay, null, tint = sky.ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (ar) "العودة للصلاة القادمة" else "Back to the next prayer", color = sky.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
          }
        }
    }
}

/** A name and its time on a soft capsule; the main one is larger and brighter than its companion. */
@Composable
private fun HeroPill(label: String, at: LocalDateTime, use24: Boolean, ar: Boolean, strong: Boolean) {
    val sky = LocalSky.current
    val size = if (strong) 20.sp else 17.sp
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(sky.ink.copy(alpha = if (strong) 0.16f else 0.09f))
            .padding(horizontal = if (strong) 16.dp else 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (strong) sky.ink else sky.inkSoft, fontSize = size, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(8.dp))
        Text(
            TimeFormat.clock(at.toLocalTime(), use24), color = sky.ink, fontSize = size, fontFamily = InterFontFamily,
            style = TNUM, bypassNumerals = true, maxLines = 1, softWrap = false,
        )
        TimeFormat.suffix(at.toLocalTime(), ar, use24)?.let {
            Spacer(Modifier.width(2.dp))
            Text(it, color = sky.inkSoft, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * One day's five prayers in a single glass strip, with arrows (or a swipe) to the day before and
 * after. Tap a prayer to see it in the top block; tap it again to let go. On today the next prayer
 * is lit and the ones behind you are dimmed; on any other day it is just the timetable. Tap the
 * day's name to come back to today.
 */
@Composable
fun PrayerRail(
    times: PrayerTimes,
    dayOffset: Int,
    nextName: String,
    nextAt: LocalDateTime?,
    now: LocalDateTime,
    ar: Boolean,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onStep: (Int) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val next = nextName.removeSuffix(" (Tomorrow)")
    val use24 = TimeFormat.use24
    val isToday = dayOffset == 0
    val items = listOf("Fajr" to times.fajr, "Dhuhr" to times.dhuhr, "Asr" to times.asr, "Maghrib" to times.maghrib, "Isha" to times.isha)
    val locale = if (ar) java.util.Locale("ar") else java.util.Locale.ENGLISH
    val dayLabel = when (dayOffset) {
        0 -> if (ar) "اليوم" else "Today"
        1 -> if (ar) "غداً" else "Tomorrow"
        -1 -> if (ar) "أمس" else "Yesterday"
        else -> java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
            .withDecimalStyle(java.time.format.DecimalStyle.STANDARD).format(times.fajr)
    }
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .glassEntrance(1)
            .pointerInput(rtl) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(total) > 56.dp.toPx()) {
                            // Content follows the finger: in a right-to-left page the next day is to the left.
                            val forward = if (rtl) total > 0 else total < 0
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onStep(if (forward) 1 else -1)
                        }
                    },
                    onHorizontalDrag = { _, dx -> total += dx },
                )
            },
        cornerRadius = Radius.row,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DayArrow(back = true, label = if (ar) "اليوم السابق" else "Previous day") { onStep(-1) }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isToday, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToday()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(dayLabel, color = if (isToday) sky.inkSoft else sky.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                DayArrow(back = false, label = if (ar) "اليوم التالي" else "Next day") { onStep(1) }
            }
            androidx.compose.animation.AnimatedContent(
                targetState = dayOffset,
                transitionSpec = {
                    // Later days arrive from the reading end, earlier ones from the other, like turning a page.
                    val dir = if (targetState > initialState) 1 else -1
                    val flip = if (rtl) -1 else 1
                    (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { dir * flip * it / 4 } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMillis = 40))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -dir * flip * it / 4 } +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
                },
                label = "railDay",
            ) { _ ->
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                items.forEach { (key, day) ->
                    val isNext = isToday && key == next
                    // After Isha the lit prayer is tomorrow's Fajr, so it shows tomorrow's minute.
                    val at = if (isNext && nextAt != null) nextAt else day
                    val passed = isToday && !isNext && at.isBefore(now)
                    val picked = key == selectedKey && !isNext
                    val ink = if (passed && !picked) sky.inkSoft.copy(alpha = 0.55f) else sky.ink
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                androidx.compose.animation.animateColorAsState(
                                    if (isNext) sky.ink.copy(alpha = 0.16f) else if (picked) sky.ink.copy(alpha = 0.08f) else sky.ink.copy(alpha = 0f),
                                    androidx.compose.animation.core.tween(180), label = "cell",
                                ).value,
                            )
                            .then(if (picked) Modifier.border(1.5.dp, sky.ink.copy(alpha = 0.75f), RoundedCornerShape(18.dp)) else Modifier)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(key)
                            }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            PrayerNames.name(key, ar, at.toLocalDate()), color = ink, fontSize = cappedSp(14f),
                            fontWeight = if (isNext || picked) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            TimeFormat.clock(at.toLocalTime(), use24), color = ink, fontSize = cappedSp(17f), fontFamily = InterFontFamily,
                            fontWeight = if (isNext || picked) FontWeight.SemiBold else FontWeight.Normal, style = TNUM,
                            bypassNumerals = true, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            }
        }
    }
}

/** A 44 dp arrow; "back" points to the earlier day and mirrors itself in Arabic. */
@Composable
private fun DayArrow(back: Boolean, label: String, onClick: () -> Unit) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (back) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            label, tint = sky.ink, modifier = Modifier.size(30.dp),
        )
    }
}

private class Door(val icon: ImageVector, val ar: String, val en: String, val dot: Boolean, val onOpen: () -> Unit)

/**
 * The four things people open from Home besides the day's prayers, each with a real label (an icon alone is a
 * guess): Tasbeeh, Adhkar, Qibla and Hadith. During the morning and evening adhkar times the Adhkar door
 * wears a dot and opens that very set; the rest of the time it opens the full list.
 */
@Composable
fun DoorsCard(
    times: PrayerTimes,
    now: LocalDateTime,
    ar: Boolean,
    onTasbeeh: () -> Unit,
    onAdhkar: () -> Unit,
    onAdhkarNow: (String) -> Unit,
    onQibla: () -> Unit,
    onHadith: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adhkarNow = when {
        !now.isBefore(times.fajr) && now.isBefore(times.sunrise.plusMinutes(60)) -> "morning"
        !now.isBefore(times.asr) && now.isBefore(times.isha.plusMinutes(90)) -> "evening"
        else -> null
    }
    val doors = listOf(
        Door(ImageVector.vectorResource(io.github.sammynashed.emran.R.drawable.ic_misbaha), "التسبيح", "Tasbeeh", false, onTasbeeh),
        Door(Icons.AutoMirrored.Outlined.MenuBook, "الأذكار", "Adhkar", adhkarNow != null) { if (adhkarNow != null) onAdhkarNow(adhkarNow) else onAdhkar() },
        Door(Icons.Outlined.Explore, "القبلة", "Qibla", false, onQibla),
        Door(Icons.Outlined.AutoStories, "الأحاديث", "Hadith", false, onHadith),
    )
    GlassPanel(modifier.fillMaxWidth().glassEntrance(3), cornerRadius = Radius.row) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp)) {
            doors.forEach { d -> DoorCell(d, ar, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DoorCell(d: Door, ar: Boolean, modifier: Modifier) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.62f, stiffness = 520f), label = "door")
    val label = if (ar) d.ar else d.en
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                d.onOpen()
            }
            .semantics { contentDescription = label }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(d.icon, null, tint = sky.ink, modifier = Modifier.size(26.dp))
            if (d.dot) {
                // "Its time is now": a small bright dot on the corner of the disc.
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(11.dp)
                        .background(sky.orb, CircleShape).border(1.5.dp, sky.ink.copy(alpha = 0.55f), CircleShape),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = sky.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}
