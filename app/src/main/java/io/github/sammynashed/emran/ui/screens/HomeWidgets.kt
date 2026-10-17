package io.github.sammynashed.emran.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.R
import io.github.sammynashed.emran.data.PrayerTimeCalculator.PrayerTimes

import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Radius
import io.github.sammynashed.emran.ui.design.glassEntrance
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.time.LocalDate

import java.time.format.DateTimeFormatter

// ---- Ayah of the day ------------------------------------------------------------------------

/**
 * One ayah's translation shown on its own, inside our own “…”. Saheeh's speech marks can open or
 * close in a neighbouring ayah, so a lone ayah may hold an unpaired ". A close whose speech began
 * earlier is dropped; speech inside becomes ‘…’, closed at the end if it runs on.
 */
fun standaloneTranslation(text: String): String {
    var t = text.trim()
    if (t.count { it == '"' } % 2 == 1) {
        // An opening mark follows a space (or starts the text); a closing one follows a letter or stop.
        fun opens(i: Int) = i == 0 || t[i - 1] == ' '
        val first = t.indexOf('"')
        t = if (!opens(first)) t.removeRange(first, first + 1) else "$t\""
    }
    var open = true
    return buildString { for (c in t) if (c == '"') { append(if (open) '\u2018' else '\u2019'); open = !open } else append(c) }
}

class DailyAyah(
    val surah: Int, val ayah: Int, val text: String, val surahAr: String, val surahEn: String,
    /** Saheeh International, for English users. */
    val english: String? = null,
    /** Al-Mukhtasar (Tafsir Center for Quranic Studies), in Arabic and in English; the share picks the user's language. */
    val tafsirAr: String? = null,
    val tafsirEn: String? = null,
)

/** The day's ayah, read from the bundled Quran (no network). Null while loading. */
@Composable
fun rememberDailyAyah(day: LocalDate, english: Boolean): DailyAyah? {
    val context = LocalContext.current
    val picks = io.github.sammynashed.emran.data.DailyAyat.version.intValue
    return produceState<DailyAyah?>(null, day, english, io.github.sammynashed.emran.ui.design.SkyDebug.ayahIndex, picks) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val (s, a) = io.github.sammynashed.emran.ui.design.SkyDebug.ayahIndex
                    ?.let { io.github.sammynashed.emran.data.DailyAyat.LIST[Math.floorMod(it, io.github.sammynashed.emran.data.DailyAyat.LIST.size)] }
                    ?: io.github.sammynashed.emran.data.DailyAyat.forDay(context, day)
                val t = io.github.sammynashed.emran.data.DailyAyat.text(context, s, a)
                val ex = io.github.sammynashed.emran.data.AyahTafsir.get(context, s, a)
                DailyAyah(
                    s, a, t.text, t.surahAr, t.surahEn,
                    if (english) ex?.translation?.ifBlank { null } else null,
                    ex?.tafsirAr?.ifBlank { null },
                    ex?.tafsirEn?.ifBlank { null },
                )
            }.getOrNull()
        }
    }.value
}

@Composable
fun AyahOfTheDayCard(
    ayah: DailyAyah?,
    ar: Boolean,
    onOpen: (surah: Int, ayah: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sky = LocalSky.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    PressGlass(
        onClick = { ayah?.let { onOpen(it.surah, it.ayah) } },
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Radius.row,
        enterIndex = 0,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = sky.inkSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (ar) "آية اليوم" else "Ayah of the day", color = sky.inkSoft, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (ayah != null) {
                    Text(
                        if (ar) "${ayah.surahAr} : ${ayah.ayah}" else "${ayah.surahEn} ${ayah.surah}:${ayah.ayah}",
                        color = sky.inkSoft, fontSize = 12.5.sp, maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    // The picture, not the words, is what gets shared: no caption travels with it.
                    Box(
                        Modifier.size(width = 44.dp, height = 34.dp).clip(CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                io.github.sammynashed.emran.ui.share.ShareCard.share(
                                    context,
                                    io.github.sammynashed.emran.ui.share.ShareCard.Content(
                                        arabic = ayah.text,
                                        english = if (ar) ayah.tafsirAr else ayah.tafsirEn,
                                        quran = true,
                                        source = if (ar) "القرآن الكريم · سورة ${ayah.surahAr} · ${ayah.surah}:${ayah.ayah}"
                                        else "Qur'an · ${ayah.surahEn} ${ayah.surah}:${ayah.ayah} · Al-Mukhtasar",
                                    ),
                                    lang = if (ar) "ar" else "en",
                                    withCaption = false,
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Share, if (ar) "مشاركة" else "Share", tint = sky.ink, modifier = Modifier.size(19.dp))
                    }
                }
            }
            val english = if (ar) null else ayah?.english
            // Measured, not guessed: the largest size at which the whole ayah fits the lines it
            // is given. Qur'an is never cut off, however long the day's ayah is.
            if (english != null) {
                FitText(
                    text = "\u201C${standaloneTranslation(english)}\u201D",
                    maxSp = 17f, minSp = 11.5f, maxLines = 5, lineHeightRatio = 1.24f,
                    fontFamily = null,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 8.dp),
                )
            } else {
                FitText(
                    text = ayah?.text ?: " ",
                    maxSp = 23f, minSp = 14f, maxLines = 4, lineHeightRatio = 1.6f,
                    fontFamily = MushafFontFamily,
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
            }
        }
    }
}

/** Centred text at the largest size (between [minSp] and [maxSp]) that fits in [maxLines] at this width. */
@Composable
private fun FitText(
    text: String,
    maxSp: Float,
    minSp: Float,
    maxLines: Int,
    lineHeightRatio: Float,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    modifier: Modifier = Modifier,
) {
    val sky = LocalSky.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val base = androidx.compose.material3.LocalTextStyle.current
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight   // bounded on Home: whatever the arc and tiles leave
        val size = remember(text, widthPx, heightPx, fontFamily) {
            fun fitsAt(sp: Float): Boolean {
                val style = base.copy(fontSize = sp.sp, lineHeight = (sp * lineHeightRatio).sp, fontFamily = fontFamily ?: base.fontFamily)
                val r = measurer.measure(text, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = widthPx))
                return r.lineCount <= maxLines && r.size.height <= heightPx
            }
            var sp = maxSp
            while (sp > minSp && !fitsAt(sp)) sp -= 0.5f
            sp
        }
        Text(
            text = text,
            color = sky.ink,
            fontSize = size.sp,
            lineHeight = (size * lineHeightRatio).sp,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            bypassNumerals = true,
        )
    }
}

// ---- Tiles ----------------------------------------------------------------------------------

/** A glass pane that gives under a finger; tap and (optionally) long-press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PressGlass(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: androidx.compose.ui.unit.Dp = 24.dp,
    enterIndex: Int? = null,
    hapticOnTap: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.62f, stiffness = 520f), label = "press")
    val glow by animateFloatAsState(if (pressed) 1.9f else 1f, spring(dampingRatio = 0.8f, stiffness = 420f), label = "glow")
    GlassPanel(
        modifier = modifier
            .then(if (enterIndex != null) Modifier.glassEntrance(enterIndex) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = {
                    if (hapticOnTap) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = onLongClick?.let { lc ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        lc()
                    }
                },
            ),
        cornerRadius = cornerRadius,
        tint = glow,
    ) { content() }
}

@Composable
private fun Tile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enterIndex: Int,
    hapticOnTap: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    val sky = LocalSky.current
    PressGlass(onClick, modifier.height(88.dp), onLongClick, enterIndex = enterIndex, hapticOnTap = hapticOnTap) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            // A quiet label; the tile's one strong thing is its value below.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = sky.inkSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = sky.inkSoft, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Spacer(Modifier.height(2.dp))
            body()
        }
    }
}

private val ORDER = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

/** Prayer times: the prayer AFTER the next one (the arc above already shows the next). */
@Composable
fun PrayerTimesTile(times: PrayerTimes, nextName: String, ar: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    val next = nextName.removeSuffix(" (Tomorrow)")
    val prayers = listOf("Fajr" to times.fajr, "Dhuhr" to times.dhuhr, "Asr" to times.asr, "Maghrib" to times.maghrib, "Isha" to times.isha)
    val nextIdx = ORDER.indexOf(next).coerceAtLeast(0)
    // First real prayer after `next` (Sunrise is not one); after Isha comes tomorrow's Fajr.
    val after = prayers.firstOrNull { ORDER.indexOf(it.first) > nextIdx } ?: prayers.first()
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Tile(Icons.Outlined.Schedule, if (ar) "المواقيت" else "Prayers", onOpen, modifier, enterIndex = 1) {
        Spacer(Modifier.weight(1f))
        Text(if (ar) "ثم" else "Then", color = sky.inkSoft, fontSize = 11.5.sp, lineHeight = 14.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(io.github.sammynashed.emran.data.PrayerNames.name(after.first, ar, after.second.toLocalDate()), color = sky.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            // The number stays big; "AM/PM" (ص/م) sits small beside it so the tile never clips it.
            Text(
                io.github.sammynashed.emran.data.TimeFormat.clock(after.second.toLocalTime()), color = sky.ink, fontSize = 20.sp, fontWeight = FontWeight.Normal,
                fontFamily = InterFontFamily, bypassNumerals = true, maxLines = 1, softWrap = false,
            )
            io.github.sammynashed.emran.data.TimeFormat.suffix(after.second.toLocalTime(), ar)?.let {
                Spacer(Modifier.width(3.dp))
                Text(it, color = sky.inkSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

/** Qibla: a door, like the other tiles. No sensor on Home; the turning happens on the Qibla screen. */
@Composable
fun QiblaTile(ar: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    Tile(Icons.Outlined.Explore, if (ar) "القبلة" else "Qibla", onOpen, modifier, enterIndex = 2) {
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (ar) "اعرف اتجاه القبلة" else "Find the Qibla",
                color = sky.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp,
                maxLines = 2, modifier = Modifier.weight(1f),
            )
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.prayer_mat),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

private val PRESET_DHIKR = listOf(
    "سبحان الله" to "SubhanAllah",
    "الحمد لله" to "Alhamdulillah",
    "لا إله إلا الله" to "La ilaha illallah",
    "الله أكبر" to "Allahu Akbar",
)

/**
 * Tasbeeh: a door to the masbaha, like every other tile. It shows where you left off there
 * (the same view model the masbaha uses, so the numbers always match); counting happens in
 * the masbaha itself, never by brushing Home.
 */
@Composable
fun TasbeehTile(ar: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    val context = LocalContext.current
    val owner = context as? androidx.lifecycle.ViewModelStoreOwner
    val vm: io.github.sammynashed.emran.ui.viewmodel.DhikrViewModel =
        if (owner != null) androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = owner)
        else androidx.lifecycle.viewmodel.compose.viewModel()
    val custom by vm.customTasbeehs.collectAsState()
    val selected by vm.selectedIndex
    val phrase by vm.activePresetPhraseIndex

    val customItem = if (selected in 1..custom.size) custom[selected - 1] else null
    val (label, count, target) = when {
        selected == 999 -> Triple(if (ar) "تحدي الجمعة" else "Friday challenge", vm.fridayCount.value, 1000)
        customItem != null -> Triple(customItem.textArabic.ifBlank { customItem.textTransliteration }, customItem.count, customItem.target)
        else -> {
            val p = PRESET_DHIKR[phrase.coerceIn(0, PRESET_DHIKR.lastIndex)]
            Triple(if (ar) p.first else p.second, vm.presetCounts[phrase.coerceIn(0, PRESET_DHIKR.lastIndex)], 33)
        }
    }

    Tile(
        icon = ImageVector.vectorResource(R.drawable.ic_misbaha),
        title = if (ar) "التسبيح" else "Tasbeeh",
        onClick = onOpen,
        modifier = modifier,
        enterIndex = 3,
    ) {
        Spacer(Modifier.weight(1f))
        Text(label, color = sky.inkSoft, fontSize = 11.5.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // One font, one weight, steady-width digits: "11 / 33" reads as one number.
        Text(
            "\u2066$count / $target\u2069",
            color = sky.ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = InterFontFamily,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            bypassNumerals = true,
        )
    }
}

@Composable
fun MoreTile(ar: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    Tile(Icons.Outlined.MoreHoriz, if (ar) "المزيد" else "More", onOpen, modifier, enterIndex = 4) {
        Spacer(Modifier.weight(1f))
        Text(
            if (ar) "الأذكار · الحديث\nالمحفوظات" else "Adhkar · Hadith\nSaved",
            color = sky.ink, fontSize = if (ar) 14.sp else 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp, maxLines = 2,
        )
    }
}
