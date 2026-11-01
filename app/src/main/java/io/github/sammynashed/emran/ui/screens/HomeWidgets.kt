package io.github.sammynashed.emran.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
import io.github.sammynashed.emran.ui.design.quietStars
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
    val tafsirPick = io.github.sammynashed.emran.data.TafsirLibrary.version.intValue
    return produceState<DailyAyah?>(null, day, english, io.github.sammynashed.emran.ui.design.SkyDebug.ayahIndex, picks, tafsirPick) {
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
                    // The tafsir the user chose for each language (Al-Mukhtasar unless they picked another).
                    io.github.sammynashed.emran.data.TafsirLibrary.let { l -> l.get(context, l.selectedId(context, "ar"), s, a)?.text },
                    io.github.sammynashed.emran.data.TafsirLibrary.let { l -> l.get(context, l.selectedId(context, "en"), s, a)?.text },
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
    /** True when the card is handed a bounded height: it then uses all of it, centring the ayah in the room. */
    fill: Boolean = false,
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
        Column((if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
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
                                        else "Qur'an · ${ayah.surahEn} ${ayah.surah}:${ayah.ayah} · " + io.github.sammynashed.emran.data.TafsirLibrary.let { l ->
                                            l.editions(context).firstOrNull { it.id == l.selectedId(context, "en") }?.nameEn ?: "Al-Mukhtasar"
                                        },
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
                    maxSp = 18f, minSp = 16f, maxLines = 3, lineHeightRatio = 1.3f,
                    fontFamily = null,
                    moreLabel = "\u2026  Read the full ayah \u203A",
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 8.dp),
                )
            } else {
                FitText(
                    text = ayah?.text ?: " ",
                    maxSp = 24f, minSp = 20f, maxLines = 3, lineHeightRatio = 1.6f,
                    fontFamily = MushafFontFamily,
                    moreLabel = "\u2026  اقرأ الآية كاملة \u2039",
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
            }
        }
    }
}

/**
 * The ayah as a teaser: shown whole when it fits [maxLines] comfortable lines, otherwise only its first lines
 * (whole words) and a line saying "read the full ayah". Home is not the place to read a long ayah; the card
 * is the door to it.
 */
@Composable
private fun FitText(
    text: String,
    maxSp: Float,
    minSp: Float,
    maxLines: Int,
    lineHeightRatio: Float,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    moreLabel: String,
    modifier: Modifier = Modifier,
) {
    val sky = LocalSky.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val base = androidx.compose.material3.LocalTextStyle.current
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val widthPx = constraints.maxWidth
        fun measure(t: String, sp: Float) = measurer.measure(
            t,
            base.copy(fontSize = sp.sp, lineHeight = (sp * lineHeightRatio).sp, fontFamily = fontFamily ?: base.fontFamily),
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = widthPx),
        )
        // (size, the text to show, whether it is only the start of it)
        val plan = remember(text, widthPx, maxLines, fontFamily) {
            var sp = maxSp
            while (sp > minSp && measure(text, sp).lineCount > maxLines) sp -= 0.5f
            if (measure(text, sp).lineCount <= maxLines) Triple(sp, text, false)
            else {
                // Too long: keep the whole words that fill all but the last of the lines.
                val words = text.split(' ')
                var lo = 1
                var hi = words.size
                while (lo < hi) {
                    val mid = (lo + hi + 1) / 2
                    if (measure(words.take(mid).joinToString(" "), sp).lineCount <= maxLines - 1) lo = mid else hi = mid - 1
                }
                Triple(sp, words.take(lo).joinToString(" "), true)
            }
        }
        val (size, shown, truncated) = plan
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = shown,
                color = sky.ink,
                fontSize = size.sp,
                lineHeight = (size * lineHeightRatio).sp,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().quietStars(),
                bypassNumerals = true,
            )
            if (truncated) {
                Text(moreLabel, color = sky.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// ---- Tiles ----------------------------------------------------------------------------------

/** A glass pane that gives under a finger; tap and (optionally) long-press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PressGlass(
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
