package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.QuranChapter
import io.github.sammynashed.emran.data.audio.AudioStore
import io.github.sammynashed.emran.data.audio.Reciters
import io.github.sammynashed.emran.data.hadith.Bookmark
import io.github.sammynashed.emran.data.hadith.HadithBookmarks
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.viewmodel.QuranViewModel

/** Arabic for matching: drops diacritics and Quranic marks, and folds the letter variants people type differently. */
internal fun normalizeArabic(s: String): String = buildString(s.length) {
    for (ch in s) {
        val c = ch.code
        if (c in 0x064B..0x065F || c in 0x0610..0x061A || c in 0x06D6..0x06ED || c == 0x0670 || c == 0x0640 || c in 0x08D3..0x08FF) continue
        append(
            when (ch) {
                'ٱ', 'أ', 'إ', 'آ' -> 'ا'
                'ى', 'ی' -> 'ي'
                'ة' -> 'ه'
                'ک' -> 'ك'
                else -> ch
            }
        )
    }
}

/** Where each of the 30 ajzāʾ begins (surah, ayah); checked against the muṣḥaf's page starts. */
private val JUZ_START = listOf(
    1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41,
    9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56,
    29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1,
)

private class Hit(val surah: Int, val ayah: Int, val text: String, val plain: String)

/**
 * The Qur'an tab on one page: search the ayat, carry on where you stopped (with how far the
 * khatmah has come), jump to one of your marks, or open any surah or juzʾ in one tap. How to
 * read (Tilawah or Hifz) is chosen inside the reader, so nothing stands between you and the page.
 */
@Composable
fun QuranHomePage(
    lang: String,
    chapters: List<QuranChapter>,
    resumeChapter: QuranChapter?,
    khatmah: QuranViewModel.Khatmah?,
    onOpen: (QuranChapter, Int?) -> Unit,
    onOpenPlayer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ar = lang == "ar"
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) } // 0 = surahs, 1 = ajzāʾ

    val bookmarks by HadithBookmarks.all.collectAsState()
    LaunchedEffect(Unit) { HadithBookmarks.ensureLoaded(context) }
    val marks = remember(bookmarks) { bookmarks.filterIsInstance<Bookmark.AyahRef>().sortedByDescending { it.savedAt } }

    val audioChanges by AudioStore.changes.collectAsState()
    val withAudio = remember(audioChanges) { AudioStore.installedSurahs(context, Reciters.chosen(context).id).toSet() }

    // Every ayah, stripped for matching, built once in the background for the search.
    val index by produceState<List<Hit>>(emptyList()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val repo = io.github.sammynashed.emran.data.repository.QuranRepository(context.applicationContext)
                repo.getChapters().flatMap { ch -> repo.getSurahVerses(ch.id).verses.map { Hit(ch.id, it.id, it.text, normalizeArabic(it.text)) } }
            }.getOrDefault(emptyList())
        }
    }
    val hits = remember(query, index) {
        val q = normalizeArabic(query.trim())
        val ref = Regex("^(\\d{1,3})\\s*[:/.\\s]\\s*(\\d{1,3})$").find(query.trim())
        when {
            ref != null -> index.filter { it.surah == ref.groupValues[1].toInt() && it.ayah == ref.groupValues[2].toInt() }
            q.length >= 2 && q.any { it.code in 0x0600..0x06FF } -> index.filter { it.plain.contains(q) }.take(80)
            else -> emptyList()
        }
    }
    fun open(ch: QuranChapter, ayah: Int?) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpen(ch, ayah) }
    fun chapter(id: Int) = chapters.firstOrNull { it.id == id }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = Space.screen),
        contentPadding = PaddingValues(top = Space.xl, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(if (ar) "القرآن الكريم" else "Qur'an", color = sky.ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.md))
            // Search the words of the Qur'an, or go straight to 2:255.
            GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 28.dp, tint = 0.9f, lens = false) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = sky.inkSoft, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(if (ar) "ابحث في الآيات، أو اكتب 2:255" else "Search the ayat, or type 2:255", color = sky.inkSoft, fontSize = 16.sp)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = sky.ink, fontSize = 17.sp), cursorBrush = SolidColor(sky.ink),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close, if (ar) "مسح" else "Clear", tint = sky.inkSoft,
                            modifier = Modifier.size(22.dp).clickable { query = "" },
                        )
                    }
                }
            }
        }

        if (query.isNotBlank()) {
            if (hits.isEmpty()) item {
                Text(
                    if (ar) "لا توجد آية بهذه الكلمات." else "No ayah has these words. Try typing them in Arabic.",
                    color = sky.inkSoft, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }
            items(hits, key = { "hit-${it.surah}:${it.ayah}" }) { hit ->
                val ch = chapter(hit.surah) ?: return@items
                GlassPanel(Modifier.fillMaxWidth().clickable { open(ch, hit.ayah) }, cornerRadius = 24.dp, tint = 0.8f, lens = false) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(hit.text, color = sky.ink, fontSize = 22.sp, lineHeight = 36.sp, maxLines = 2, fontFamily = MushafFontFamily, modifier = Modifier.fillMaxWidth(), bypassNumerals = true)
                        Spacer(Modifier.height(6.dp))
                        Text(if (ar) "سورة ${ch.name} · الآية ${hit.ayah}" else "${ch.transliteration} · ${hit.surah}:${hit.ayah}", color = sky.inkSoft, fontSize = 13.sp)
                    }
                }
            }
            return@LazyColumn
        }

        // Carry on: the surah you were in, with the khatmah's progress beside it.
        item {
            val target = resumeChapter ?: chapter(1)
            if (target != null) {
                GlassPanel(Modifier.fillMaxWidth().clickable { open(target, null) }, cornerRadius = 28.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (resumeChapter != null) (if (ar) "تابع القراءة" else "Continue reading") else (if (ar) "ابدأ القراءة" else "Start reading"),
                                color = sky.inkSoft, fontSize = 14.sp,
                            )
                            Text(
                                if (ar) "سورة ${target.name}" else target.transliteration,
                                color = sky.ink, fontSize = 26.sp, fontFamily = if (ar) SurahNameFontFamily else null, maxLines = 1,
                            )
                            if (khatmah != null) {
                                Text(
                                    if (ar) "الختمة: ${khatmah.pages} من ${khatmah.totalPages} صفحة" else "Khatmah: ${khatmah.pages} of ${khatmah.totalPages} pages",
                                    color = sky.inkSoft, fontSize = 13.sp, bypassNumerals = true,
                                )
                            }
                        }
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            val frac = khatmah?.let { (it.pages.toFloat() / it.totalPages).coerceIn(0f, 1f) } ?: 0f
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                val stroke = 5.dp.toPx(); val inset = stroke / 2
                                val arc = Size(size.width - stroke, size.height - stroke)
                                drawArc(sky.ink.copy(alpha = 0.14f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                                drawArc(sky.ink, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                            }
                            Icon(Icons.Filled.PlayArrow, if (ar) "متابعة" else "Continue", tint = sky.ink, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }

        // Listening: the Qur'an player (what is reciting now, or pick a downloaded surah).
        item {
            val now by io.github.sammynashed.emran.data.audio.QuranAudio.state.collectAsState()
            GlassPanel(Modifier.fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpenPlayer() }, cornerRadius = 24.dp, tint = 0.9f, lens = false) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Headphones, null, tint = sky.ink, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    val n = now
                    Text(
                        if (n != null && n.playing) (if (ar) "يُتلى الآن: سورة ${chapter(n.surah)?.name ?: n.surah}" else "Now reciting: ${chapter(n.surah)?.transliteration ?: n.surah}")
                        else if (n != null) (if (ar) "موقوفة مؤقتًا: سورة ${chapter(n.surah)?.name ?: n.surah}" else "Paused: ${chapter(n.surah)?.transliteration ?: n.surah}")
                        else (if (ar) "استمع إلى التلاوة" else "Listen to a recitation"),
                        color = sky.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    if (n?.playing == true) Icon(Icons.Filled.PlayArrow, null, tint = sky.inkSoft, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Your marks ("I stopped here"), newest first.
        if (marks.isNotEmpty()) item {
            Column {
                Text(if (ar) "علاماتك" else "Your marks", color = sky.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(marks, key = { it.key }) { m ->
                        val ch = chapter(m.surah) ?: return@items
                        GlassPanel(Modifier.clickable { open(ch, m.ayah) }, cornerRadius = 20.dp, tint = 0.9f, lens = false) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Bookmark, null, tint = sky.ink, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (ar) "${ch.name} · ${m.ayah}" else "${ch.transliteration} ${m.surah}:${m.ayah}", color = sky.ink, fontSize = 15.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Surahs | Ajzāʾ
        item {
            GlassPanel(Modifier.fillMaxWidth().padding(top = 6.dp), cornerRadius = 22.dp, tint = 0.7f, lens = false) {
                Row(Modifier.fillMaxWidth().padding(4.dp)) {
                    listOf(if (ar) "السور" else "Surahs", if (ar) "الأجزاء" else "Ajzāʾ").forEachIndexed { i, label ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                .background(if (tab == i) sky.ink.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); tab = i
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = sky.ink, fontSize = 15.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium) }
                    }
                }
            }
        }

        if (tab == 0) {
            items(chapters, key = { "s${it.id}" }) { ch ->
                GlassPanel(Modifier.fillMaxWidth().clickable { open(ch, null) }, cornerRadius = 24.dp, tint = 0.8f, lens = false) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Text(ch.id.toString(), color = sky.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (ar) ch.name else ch.transliteration,
                                color = sky.ink, fontSize = if (ar) 24.sp else 17.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                                fontFamily = if (ar) SurahNameFontFamily else null,
                            )
                            Text(surahMetaLabel(ch.type, ch.total_verses, lang), color = sky.inkSoft, fontSize = 12.sp, maxLines = 1)
                        }
                        if (ch.id in withAudio) {
                            Icon(Icons.Outlined.Headphones, if (ar) "التلاوة منزّلة" else "Audio downloaded", tint = sky.inkSoft, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        } else {
            items(JUZ_START.size, key = { "j$it" }) { i ->
                val (s, a) = JUZ_START[i]
                val ch = chapter(s)
                GlassPanel(
                    Modifier.fillMaxWidth().clickable { if (ch != null) open(ch, if (a > 1) a else null) },
                    cornerRadius = 24.dp, tint = 0.8f, lens = false,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = sky.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (ar) "الجزء ${i + 1}" else "Juzʾ ${i + 1}", color = sky.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (ar) "يبدأ من سورة ${ch?.name ?: s} · الآية $a" else "Starts at ${ch?.transliteration ?: s} $s:$a",
                                color = sky.inkSoft, fontSize = 13.sp, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
