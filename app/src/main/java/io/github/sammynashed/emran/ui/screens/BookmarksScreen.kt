package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.assets.AdhkarRepository
import io.github.sammynashed.emran.data.hadith.Bookmark
import io.github.sammynashed.emran.data.hadith.HadithBookmarks
import io.github.sammynashed.emran.data.hadith.HadithLibrary
import io.github.sammynashed.emran.data.repository.QuranRepository
import io.github.sammynashed.emran.ui.design.GlassSectionLabel
import io.github.sammynashed.emran.ui.design.GlassSegmented
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.design.glassSurface
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A place in the Qur'an the reader marked: "I stopped here". */
private class AyahRow(val bookmark: Bookmark.AyahRef, val surahName: String, val page: Int, val snippet: String)

/** A saved dhikr or hadith, with the tag that says what it is for ("Invocation for sneezing"). */
private class SavedItem(val bookmark: Bookmark, val tag: String, val tagColor: Color?, val body: String, val arabic: Boolean)

private class Section(val title: String, val items: List<SavedItem>)

/**
 * Everything the user saved, in two tabs: the Qur'an (where they stopped, like a muṣḥaf's
 * bookmark ribbons) and the adhkar and hadith they kept, grouped by what they are for, each with
 * a tag so a dua never shows up as anonymous text.
 */
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenAyah: (surah: Int, ayah: Int) -> Unit,
    onOpenHadith: (bookId: String, chapterId: Int, number: Int) -> Unit,
    onOpenDhikr: (folderId: String) -> Unit,
    initialTab: String = "quran",
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val ar = lang == "ar"
    val sky = LocalSky.current
    val bookmarks by HadithBookmarks.all.collectAsState()
    LaunchedEffect(Unit) { HadithBookmarks.ensureLoaded(context) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }

    val ayat by produceState<List<AyahRow>?>(null, bookmarks, lang) {
        value = withContext(Dispatchers.IO) {
            val repo = QuranRepository(context.applicationContext)
            val chapters = runCatching { repo.getChapters() }.getOrDefault(emptyList())
            bookmarks.filterIsInstance<Bookmark.AyahRef>().mapNotNull { b ->
                val ch = chapters.firstOrNull { it.id == b.surah } ?: return@mapNotNull null
                val v = runCatching { repo.getSurahVerses(b.surah).verses.getOrNull(b.ayah - 1) }.getOrNull()
                    ?: return@mapNotNull null
                AyahRow(b, if (ar) ch.name else ch.transliteration, v.page, MushafSource.words(v.text).take(7).joinToString(" "))
            }
        }
    }

    val sections by produceState<List<Section>?>(null, bookmarks, lang) {
        value = withContext(Dispatchers.IO) {
            val folders = AdhkarRepository.loadAdhkar(context)
            val categories = AdhkarRepository.loadCategories(context)
            fun hex(s: String?) = runCatching { Color(android.graphics.Color.parseColor("#" + s)) }.getOrNull()

            // Daily adhkar first, then Hisn al-Muslim in its own category order, then hadith.
            val daily = mutableListOf<SavedItem>()
            val byCategory = linkedMapOf<String, MutableList<SavedItem>>().apply { categories.forEach { put(it.id, mutableListOf()) } }
            val hadith = mutableListOf<SavedItem>()
            for (b in bookmarks) when (b) {
                is Bookmark.DhikrRef -> {
                    val folder = folders.firstOrNull { it.id == b.folderId } ?: continue
                    val item = folder.items.withIndex().firstOrNull { (i, it) -> (it.id ?: i.toString()) == b.itemKey }?.value ?: continue
                    val cat = categories.firstOrNull { it.id == folder.category }
                    val row = SavedItem(
                        b,
                        tag = cleanTag(if (ar) folder.titleAr else folder.titleEn),
                        tagColor = hex(cat?.color ?: folder.color),
                        body = if (ar) item.textAr else item.textEn,
                        arabic = ar,
                    )
                    if (folder.isHisn && cat != null) byCategory.getValue(cat.id) += row else daily += row
                }
                is Bookmark.HadithRef -> {
                    val h = HadithLibrary.hadith(context, b.bookId, b.chapterId, b.number)
                    val book = HadithLibrary.book(context, b.bookId)
                    val title = "${book?.title(lang) ?: b.bookId} · ${b.number}"
                    hadith += if (h == null) {
                        // A downloaded book that has since been removed: keep the bookmark for its return.
                        SavedItem(b, title, null, if (ar) "الكتاب غير مثبّت" else "This book is not installed.", ar)
                    } else {
                        val arabic = ar || h.english.isBlank()
                        SavedItem(b, title, null, if (arabic) h.arabic else h.english, arabic)
                    }
                }
                is Bookmark.AyahRef -> Unit
            }
            buildList {
                if (daily.isNotEmpty()) add(Section(if (ar) "الأذكار اليومية" else "Daily adhkar", daily))
                byCategory.forEach { (id, list) ->
                    val c = categories.first { it.id == id }
                    if (list.isNotEmpty()) add(Section(if (ar) c.titleAr else c.titleEn, list))
                }
                if (hadith.isNotEmpty()) add(Section(if (ar) "الأحاديث" else "Hadith", hadith))
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = if (ar) "العلامات" else "Bookmarks",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
            // The segmented control is laid out left to right; in Arabic the first tab sits on the right.
            val quranOpt = "quran" to (if (ar) "القرآن" else "Qur'an")
            val adhkarOpt = "adhkar" to (if (ar) "الأذكار" else "Adhkar")
            GlassSegmented(
                options = if (ar) listOf(adhkarOpt, quranOpt) else listOf(quranOpt, adhkarOpt),
                selectedKey = tab,
                onSelect = { tab = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.screen),
            )
            Spacer(Modifier.height(14.dp))

            if (tab == "quran") {
                val list = ayat
                when {
                    list == null -> Loading()
                    list.isEmpty() -> Empty(
                        if (ar) "لا علامات بعد. في المصحف، اضغط على أي آية ثم «وقفت هنا»، أو اضغط رمز العلامة في الأعلى."
                        else "No bookmarks yet. In the Qur'an, tap any ayah and choose \"I stopped here\", or tap the bookmark at the top."
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { GlassSectionLabel(if (ar) "حيث توقفت" else "Where you stopped") }
                        items(list, key = { it.bookmark.key }) { row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .glassSurface(18.dp)
                                    .clickable { onOpenAyah(row.bookmark.surah, row.bookmark.ayah) }
                                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (ar) "سورة ${row.surahName} · الآية ${row.bookmark.ayah}"
                                        else "${row.surahName} · Ayah ${row.bookmark.ayah}",
                                        color = sky.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                        fontFamily = if (ar) SurahNameFontFamily else null,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        row.snippet, color = sky.inkSoft, fontSize = 17.sp, lineHeight = 30.sp,
                                        fontFamily = MushafFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        bypassNumerals = true,
                                    )
                                    Text(
                                        (if (ar) "الصفحة ${row.page} · الجزء ${juzOf(row.page)}" else "Page ${row.page} · Juz ${juzOf(row.page)}") +
                                            " · " + savedWhen(row.bookmark.savedAt, ar),
                                        color = sky.inkSoft, fontSize = 12.sp,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                                    tint = sky.inkSoft, modifier = Modifier.size(22.dp),
                                )
                                IconButton(onClick = { HadithBookmarks.remove(context, row.bookmark) }) {
                                    Icon(
                                        Icons.Outlined.RemoveCircleOutline,
                                        contentDescription = if (ar) "إزالة العلامة" else "Remove bookmark",
                                        tint = sky.inkSoft,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val list = sections
                when {
                    list == null -> Loading()
                    list.isEmpty() -> Empty(
                        if (ar) "لا محفوظات بعد. اضغط رمز العلامة على أي ذكر أو حديث."
                        else "Nothing saved yet. Tap the bookmark on any dhikr or hadith and it shows up here."
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Space.screen, end = Space.screen, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        list.forEachIndexed { i, section ->
                            item(key = "h$i${section.title}") {
                                GlassSectionLabel(section.title, Modifier.padding(top = if (i == 0) 0.dp else 10.dp))
                            }
                            items(section.items, key = { it.bookmark.key }) { row ->
                                SavedCard(
                                    row,
                                    onOpen = {
                                        when (val b = row.bookmark) {
                                            is Bookmark.HadithRef -> onOpenHadith(b.bookId, b.chapterId, b.number)
                                            is Bookmark.DhikrRef -> onOpenDhikr(b.folderId)
                                            is Bookmark.AyahRef -> onOpenAyah(b.surah, b.ayah)
                                        }
                                    },
                                    onRemove = { HadithBookmarks.remove(context, row.bookmark) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedCard(row: SavedItem, onOpen: () -> Unit, onRemove: () -> Unit) {
    val sky = LocalSky.current
    Row(
        Modifier
            .fillMaxWidth()
            .glassSurface(18.dp)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            // The tag: what this is for, in its category's colour.
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background((row.tagColor ?: sky.ink).copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(row.tagColor ?: sky.ink))
                Spacer(Modifier.width(6.dp))
                Text(
                    row.tag, color = sky.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, bypassNumerals = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                row.body,
                color = sky.ink.copy(alpha = 0.9f),
                fontSize = if (row.arabic) 17.sp else 14.sp,
                lineHeight = if (row.arabic) 28.sp else 21.sp,
                fontFamily = if (row.arabic) SurahNameFontFamily else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                bypassNumerals = true,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Bookmark, contentDescription = "Remove bookmark", tint = sky.ink)
        }
    }
}

@Composable
private fun Loading() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = LocalSky.current.ink) }

@Composable
private fun Empty(text: String) = Box(Modifier.fillMaxSize().padding(40.dp), Alignment.Center) {
    Text(text, color = LocalSky.current.inkSoft, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
}

/** Hisn al-Muslim's chapter titles carry stray spaces and tatweel from the source ("دعاء ُلبْس"). */
private fun cleanTag(s: String): String = s.replace(Regex("\\s+"), " ").replace(" ُ", " ").trim().trimEnd('.')

private fun savedWhen(at: Long, ar: Boolean): String {
    val then = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(then, java.time.LocalDate.now()).toInt()
    return when {
        days <= 0 -> if (ar) "اليوم" else "today"
        days == 1 -> if (ar) "أمس" else "yesterday"
        days == 2 && ar -> "قبل يومين"
        days < 11 && ar -> "قبل $days أيام"
        days < 7 -> "$days days ago"
        else -> then.format(java.time.format.DateTimeFormatter.ofPattern(if (ar) "d MMMM" else "d MMM", if (ar) java.util.Locale("ar") else java.util.Locale.ENGLISH))
    }
}
