package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.repository.QuranRepository
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class PickRow(val surah: Int, val ayah: Int, val text: String, val plain: String, val surahAr: String, val surahEn: String)

/**
 * "Choose an ayah": any ayah of the Qur'an can be today's ayah, with its explanation.
 *
 * One box understands what people actually type: a reference ("2:255", "2 255", "البقرة 255",
 * "الكهف ١٠"), a surah name alone (lists that surah's ayat), or a few words of the ayah (searched
 * without tashkeel, like the surah picker).
 */
@Composable
fun AyahPickerScreen(
    onBack: () -> Unit,
    onPick: (surah: Int, ayah: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ar = LocalAppLanguage.current == "ar"
    val sky = LocalSky.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // Debug builds can prefill the box from adb (--es QURAN_SEARCH "..."), since adb cannot type Arabic.
    var query by remember {
        mutableStateOf(if (io.github.sammynashed.emran.BuildConfig.DEBUG) io.github.sammynashed.emran.ui.design.SkyDebug.searchQuery else "")
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val index by produceState<List<PickRow>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val repo = QuranRepository(context.applicationContext)
                repo.getChapters().flatMap { ch ->
                    repo.getSurahVerses(ch.id).verses.map { PickRow(ch.id, it.id, it.text, normalizeArabic(it.text), ch.name, ch.transliteration) }
                }
            }.getOrDefault(emptyList())
        }
    }
    val surahs = remember(index) {
        index.distinctBy { it.surah }.map { Triple(it.surah, normalizeArabic(it.surahAr).removePrefix("ال"), it.surahEn.lowercase().filter { c -> c.isLetter() }) }
    }

    val hits = remember(query, index) { search(query, index, surahs) }

    Column(modifier.fillMaxSize().padding(horizontal = Space.screen)) {
        ScreenHeader(
            title = if (ar) "اختر آية" else "Choose an ayah",
            subtitle = if (ar) "تصبح آية اليوم حتى منتصف الليل" else "It becomes today's ayah until midnight",
            onBack = onBack,
        )
        GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 28.dp, tint = 0.9f, lens = false) {
            Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = sky.inkSoft, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            if (ar) "اسم السورة ورقم الآية، أو كلمات منها" else "Surah and ayah number, or words from it",
                            color = sky.inkSoft, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = sky.ink, fontSize = 17.sp),
                        cursorBrush = SolidColor(sky.ink),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            when {
                query.isBlank() -> if (ar) "مثال: البقرة 255 · 2:255 · الكهف · فإن مع العسر" else "e.g. 2:255 · Al-Kahf 10 · Kahf"
                index.isEmpty() -> if (ar) "جارٍ التحميل…" else "Loading…"
                hits.isEmpty() -> if (ar) "لا نتائج. جرّب اسم السورة ورقم الآية." else "Nothing found. Try a surah and ayah number."
                else -> ""
            },
            color = sky.inkSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp),
        )
        Spacer(Modifier.height(Space.sm))
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 124.dp),
        ) {
            items(hits, key = { "${it.surah}:${it.ayah}" }) { h ->
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPick(h.surah, h.ayah)
                        },
                    cornerRadius = 24.dp,
                    tint = 0.8f,
                    lens = false,
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(
                            if (ar) "سورة ${h.surahAr} · الآية ${h.ayah}" else "${h.surahEn} ${h.surah}:${h.ayah}",
                            color = sky.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            h.text, color = sky.ink, fontSize = 22.sp, lineHeight = 38.sp,
                            fontFamily = MushafFontFamily, textAlign = TextAlign.Start, bypassNumerals = true,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Arabic-Indic and Persian digits to ASCII, so "الكهف ١٠" works. */
private fun asciiDigits(s: String): String = buildString(s.length) {
    for (c in s) append(
        when (c) {
            in '٠'..'٩' -> '0' + (c - '٠')
            in '۰'..'۹' -> '0' + (c - '۰')
            else -> c
        }
    )
}

private fun search(raw: String, index: List<PickRow>, surahs: List<Triple<Int, String, String>>): List<PickRow> {
    val q = asciiDigits(raw.trim())
    if (q.isEmpty() || index.isEmpty()) return emptyList()

    // "2:255", "2 255", "2/255"
    Regex("^(\\d{1,3})\\s*[:/.\\s-]\\s*(\\d{1,3})$").find(q)?.let { m ->
        val s = m.groupValues[1].toInt(); val a = m.groupValues[2].toInt()
        return index.filter { it.surah == s && it.ayah == a }
    }
    // "البقرة 255", "سورة الكهف 10", "Al-Kahf 10", or just a surah name / number.
    val m = Regex("^(.*?)\\s*(\\d{1,3})?$").find(q)
    val name = m?.groupValues?.get(1)?.trim().orEmpty()
    val num = m?.groupValues?.get(2)?.toIntOrNull()
    if (name.isEmpty() && num != null) return index.filter { it.surah == num }.take(300)
    if (name.isNotEmpty()) {
        val arName = normalizeArabic(name).removePrefix("سوره").trim().removePrefix("ال")
        val enName = name.lowercase().filter { it.isLetter() }.removePrefix("surah").removePrefix("sura")
        val surah = surahs.firstOrNull { (_, a, e) ->
            (arName.length >= 2 && a == arName) || (enName.length >= 3 && (e == enName || e.removePrefix("al") == enName.removePrefix("al")))
        } ?: surahs.firstOrNull { (_, a, e) ->
            (arName.length >= 3 && a.startsWith(arName)) || (enName.length >= 3 && e.removePrefix("al").startsWith(enName.removePrefix("al")))
        }
        if (surah != null) {
            return if (num != null) index.filter { it.surah == surah.first && it.ayah == num }
            else index.filter { it.surah == surah.first }.take(300)
        }
    }
    // Words of the ayah (Arabic only, at least 3 letters).
    val words = normalizeArabic(raw.trim())
    if (words.count { it.code in 0x0621..0x064A } < 3) return emptyList()
    return index.filter { it.plain.contains(words) }.take(80)
}
