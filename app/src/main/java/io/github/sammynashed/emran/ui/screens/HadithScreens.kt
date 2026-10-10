package io.github.sammynashed.emran.ui.screens

import io.github.sammynashed.emran.ui.components.EmranAlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.assets.AdhkarFolder
import io.github.sammynashed.emran.data.assets.AdhkarItem
import io.github.sammynashed.emran.data.assets.AdhkarRepository
import io.github.sammynashed.emran.data.hadith.ArabicText
import io.github.sammynashed.emran.data.hadith.Bookmark
import io.github.sammynashed.emran.data.hadith.Hadith
import io.github.sammynashed.emran.data.hadith.HadithBook
import io.github.sammynashed.emran.data.hadith.HadithBookmarks
import io.github.sammynashed.emran.data.hadith.HadithHit
import io.github.sammynashed.emran.data.hadith.HadithLibrary
import io.github.sammynashed.emran.data.hadith.HadithPackStore
import io.github.sammynashed.emran.data.hadith.PackState
import io.github.sammynashed.emran.ui.components.ItemActions
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.tour.tourTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// -------------------------------------------------------------------------
// Shared pieces
// -------------------------------------------------------------------------

@Composable
private fun HadithRoot(lang: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content
    )
}

/** Same header shape as the Adhkar screens so the sub-screens read as one family. */
@Composable
private fun HadithTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .tourTarget("sub_screen_back_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AppTheme.colors.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

private fun sizeLabel(bytes: Long): String =
    if (bytes >= 1_048_576) "%.1f MB".format(bytes / 1_048_576.0) else "${(bytes + 512) / 1024} KB"

private fun countLabel(n: Int, lang: String): String = if (lang == "ar") "$n حديث" else "%,d hadith".format(n)

private fun shareText(book: HadithBook?, lang: String, h: Hadith): String = buildString {
    append(h.arabic)
    if (h.english.isNotBlank()) append("\n\n").append(h.english)
    append("\n\n— ").append(book?.title(lang) ?: "").append(" #").append(h.number)
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Hadith", text))
}

private fun shareIntent(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Marks every word of [words] found in [text], ignoring case. English only. */
private fun highlight(text: String, words: List<String>, color: Color): AnnotatedString {
    if (words.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    if (lower.length != text.length) return AnnotatedString(text)
    val marks = BooleanArray(text.length)
    for (w in words) {
        var i = lower.indexOf(w)
        while (i >= 0) {
            for (k in i until i + w.length) marks[k] = true
            i = lower.indexOf(w, i + w.length)
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            var j = i
            while (j < text.length && marks[j] == marks[i]) j++
            val part = text.substring(i, j)
            if (marks[i]) withStyle(SpanStyle(background = color.copy(alpha = 0.35f), fontWeight = FontWeight.Bold)) { append(part) }
            else append(part)
            i = j
        }
    }
}

// -------------------------------------------------------------------------
// Library: books, downloads, entry to search and saved
// -------------------------------------------------------------------------

@Composable
fun HadithLibraryScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val states by HadithPackStore.states.collectAsState()
    val bookmarks by HadithBookmarks.all.collectAsState()
    LaunchedEffect(Unit) { HadithBookmarks.ensureLoaded(context) }

    // The installed check is a stat() per book; re-run whenever a download changes state.
    val rows = remember(states) { HadithLibrary.books(context).map { it to HadithLibrary.isInstalled(context, it) } }
    val ready = rows.filter { it.second }.map { it.first }
    val available = rows.filter { !it.second }.map { it.first }
    val downloadedBytes = ready.filter { !it.bundled }.sumOf { it.bytes }
    var confirmDelete by remember { mutableStateOf<HadithBook?>(null) }
    val full = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }

    HadithRoot(lang) {
        Column(Modifier.fillMaxSize()) {
            HadithTopBar(
                title = if (lang == "ar") "الأحاديث" else "HADITH",
                onBack = onBack
            )
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { full() }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EntryChip(
                            icon = Icons.Default.Search,
                            label = if (lang == "ar") "بحث" else "Search",
                            modifier = Modifier.weight(1f),
                            onClick = onSearch
                        )
                        EntryChip(
                            icon = Icons.Default.Bookmark,
                            label = (if (lang == "ar") "المحفوظات" else "Saved") +
                                if (bookmarks.isNotEmpty()) " · ${bookmarks.size}" else "",
                            modifier = Modifier.weight(1f),
                            onClick = onBookmarks
                        )
                    }
                }
                item(span = { full() }) { SectionLabel(if (lang == "ar") "جاهزة للقراءة" else "READY TO READ") }
                gridItems(ready, key = { "r-" + it.id }) { book ->
                    HadithBookCard(
                        modifier = if (book.id == ready.first().id) Modifier.tourTarget("hadith_first_book") else Modifier,
                        book = book,
                        lang = lang,
                        subtitle = countLabel(book.hadiths, lang),
                        installed = true,
                        state = PackState.Idle,
                        onClick = { onOpenBook(book.id) },
                        onDownload = {},
                        onRemove = if (book.bundled) null else ({ confirmDelete = book })
                    )
                }
                if (available.isNotEmpty()) {
                    item(span = { full() }) {
                        Column {
                            SectionLabel(if (lang == "ar") "تنزيل المزيد" else "DOWNLOAD MORE")
                            Text(
                                text = if (lang == "ar") "تُحمَّل عند طلبك فقط، وتبقى على جهازك."
                                else "Fetched only when you tap, then kept on your device. Nothing runs in the background.",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    gridItems(available, key = { "a-" + it.id }) { book ->
                        HadithBookCard(
                            book = book,
                            lang = lang,
                            subtitle = countLabel(book.hadiths, lang) + " · " + sizeLabel(book.bytes),
                            installed = false,
                            state = states[book.id] ?: PackState.Idle,
                            onClick = {},
                            onDownload = { HadithPackStore.start(context, book) },
                            onRemove = null
                        )
                    }
                }
                if (downloadedBytes > 0) {
                    item(span = { full() }) {
                        Text(
                            text = (if (lang == "ar") "الكتب المحمّلة تشغل " else "Downloaded books use ") + sizeLabel(downloadedBytes),
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { book ->
        EmranAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (lang == "ar") "حذف ${book.titleAr}؟" else "Remove ${book.titleEn}?") },
            text = {
                Text(
                    if (lang == "ar") "سيُحرَّر ${sizeLabel(book.bytes)}، ويمكنك تنزيله مجددًا متى شئت. تبقى المحفوظات كما هي."
                    else "This frees ${sizeLabel(book.bytes)}. You can download it again any time; your bookmarks are kept."
                )
            },
            confirmButton = {
                TextButton(onClick = { HadithPackStore.delete(context, book); confirmDelete = null }) {
                    Text(if (lang == "ar") "حذف" else "Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(if (lang == "ar") "إلغاء" else "Cancel") }
            }
        )
    }
}

/**
 * A collection as a square glass card, the same shape as the Adhkar folders. An
 * installed book opens on tap; one that is not installed shows its size and a
 * download button that turns into a progress ring while it transfers.
 */
@Composable
private fun HadithBookCard(
    modifier: Modifier = Modifier,
    book: HadithBook,
    lang: String,
    subtitle: String,
    installed: Boolean,
    state: PackState,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val accent = if (installed) AppTheme.colors.primary else Color.White.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .liquidGlassCard(RoundedCornerShape(24.dp), accent.copy(alpha = if (installed) 0.6f else 0.3f))
            .clickable(enabled = installed || state !is PackState.Downloading) {
                if (installed) onClick() else onDownload()
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.MenuBook, contentDescription = null, tint = accent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = book.title(lang),
                color = if (installed) Color.White else Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontFamily = if (lang == "ar") SurahNameFontFamily else null
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
            if (state is PackState.Failed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (lang == "ar") "تعذّر التنزيل. اضغط للمحاولة" else "Failed. Tap to retry",
                    color = Color(0xFFFF8A80),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        // Corner control: progress ring, download, or remove.
        Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp), Alignment.Center) {
            when {
                state is PackState.Downloading -> CircularProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = AppTheme.colors.primary
                )
                !installed -> Icon(Icons.Default.Download, contentDescription = "Download", tint = AppTheme.colors.primary, modifier = Modifier.size(22.dp))
                onRemove != null -> IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun EntryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .liquidGlassCard(RoundedCornerShape(16.dp), AppTheme.colors.primary.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// -------------------------------------------------------------------------
// Chapters of a book
// -------------------------------------------------------------------------

@Composable
fun HadithBookScreen(bookId: String, onBack: () -> Unit, onOpenChapter: (Int) -> Unit) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val book = remember(bookId) { HadithLibrary.book(context, bookId) }
    val pack by produceState<io.github.sammynashed.emran.data.hadith.HadithPack?>(null, bookId) {
        value = withContext(Dispatchers.IO) { HadithLibrary.pack(context, bookId) }
    }

    HadithRoot(lang) {
        Column(Modifier.fillMaxSize()) {
            HadithTopBar(
                title = book?.title(lang) ?: "",
                subtitle = book?.let { countLabel(it.hadiths, lang) },
                onBack = onBack
            )
            val chapters = pack?.chapters
            if (chapters == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AppTheme.colors.primary) }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    gridItems(chapters, key = { it.id }) { ch ->
                        // Same square glass card as the Adhkar folders: number in place of the icon,
                        // up to three lines of title (chapter names are long), then the count.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .liquidGlassCard(RoundedCornerShape(24.dp), AppTheme.colors.primary.copy(alpha = 0.55f))
                                .clickable { onOpenChapter(ch.id) }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(14.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppTheme.colors.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(ch.id.toString(), color = AppTheme.colors.primary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, bypassNumerals = true)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = ch.title(lang),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp,
                                    fontFamily = if (lang == "ar") SurahNameFontFamily else null,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    bypassNumerals = true
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = countLabel(ch.count, lang),
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Reading a chapter
// -------------------------------------------------------------------------

@Composable
fun HadithChapterScreen(bookId: String, chapterId: Int, focusNumber: Int?, onBack: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val book = remember(bookId) { HadithLibrary.book(context, bookId) }
    val bookmarks by HadithBookmarks.all.collectAsState()
    LaunchedEffect(Unit) { HadithBookmarks.ensureLoaded(context) }

    val chapterInfo = remember(bookId, chapterId) { HadithLibrary.pack(context, bookId)?.chapter(chapterId) }
    val hadiths by produceState<List<Hadith>?>(null, bookId, chapterId) {
        value = withContext(Dispatchers.IO) { HadithLibrary.chapter(context, bookId, chapterId) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(hadiths) {
        val list = hadiths ?: return@LaunchedEffect
        val idx = if (focusNumber == null) -1 else list.indexOfFirst { it.number == focusNumber }
        if (idx >= 0) listState.scrollToItem(idx)
    }

    HadithRoot(lang) {
        Column(Modifier.fillMaxSize()) {
            val chapterTitle = chapterInfo?.title(lang) ?: ""
            HadithTopBar(
                title = chapterTitle,
                subtitle = book?.title(lang)?.takeIf { it != chapterTitle },
                onBack = onBack
            )
            val list = hadiths
            if (list == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AppTheme.colors.primary) }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(list, key = { it.number }) { h ->
                        val key = Bookmark.hadithKey(bookId, chapterId, h.number)
                        HadithCard(
                            hadith = h,
                            sourceLabel = "${book?.title(lang) ?: ""} · ${h.number}",
                            lang = lang,
                            bookmarked = bookmarks.any { it.key == key },
                            focused = h.number == focusNumber,
                            onToggleBookmark = { HadithBookmarks.toggleHadith(context, bookId, chapterId, h.number) },
                            onCopy = { copyToClipboard(context, shareText(book, lang, h)) },
                            onShare = {
                                io.github.sammynashed.emran.ui.share.ShareCard.share(
                                    context,
                                    io.github.sammynashed.emran.ui.share.ShareCard.Content(
                                        arabic = h.arabic,
                                        english = h.text.ifBlank { null },
                                        narrator = h.narrator.ifBlank { null },
                                        source = "${book?.title(lang) ?: ""} · ${h.number}"
                                    ),
                                    lang
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HadithCard(
    hadith: Hadith,
    sourceLabel: String,
    lang: String,
    bookmarked: Boolean,
    focused: Boolean,
    onToggleBookmark: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = AppTheme.colors.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassCard(RoundedCornerShape(20.dp), accent.copy(alpha = if (focused) 0.9f else 0.4f))
            .then(if (focused) Modifier.border(BorderStroke(1.5.dp, accent), RoundedCornerShape(20.dp)) else Modifier)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("#${hadith.number}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, bypassNumerals = true)
            }
            Spacer(Modifier.weight(1f))
            ItemActions(
                bookmarked = bookmarked,
                onToggleBookmark = onToggleBookmark,
                onCopy = onCopy,
                onShare = onShare
            )
        }
        Spacer(Modifier.height(8.dp))

        // Arabic is the text itself and always shown. The English follows it in English
        // mode; in Arabic mode it would only be noise.
        Text(
            text = hadith.arabic,
            color = Color.White,
            fontSize = 21.sp,
            lineHeight = 36.sp,
            fontFamily = SurahNameFontFamily,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth(),
            bypassNumerals = true
        )
        if (lang != "ar" && hadith.english.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(14.dp))
            if (hadith.narrator.isNotBlank()) {
                Text(
                    text = hadith.narrator,
                    color = accent.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                    bypassNumerals = true
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = hadith.text,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp,
                lineHeight = 24.sp,
                bypassNumerals = true
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = sourceLabel,
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 11.sp,
            bypassNumerals = true
        )
    }
}

// -------------------------------------------------------------------------
// Search: hadith and adhkar together
// -------------------------------------------------------------------------

private class DhikrHit(val folder: AdhkarFolder, val item: AdhkarItem)

private fun searchAdhkar(folders: List<AdhkarFolder>, query: String, limit: Int = 40): List<DhikrHit> {
    val arabic = query.any { it in '؀'..'ۿ' }
    val words = (if (arabic) ArabicText.normalize(query) else query.lowercase())
        .split(' ', '\t').filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val out = ArrayList<DhikrHit>()
    for (f in folders) for (i in f.items) {
        val hay = if (arabic) ArabicText.normalize(i.textAr)
        else "${i.textEn} ${i.transliteration.orEmpty()} ${i.source.orEmpty()}".lowercase()
        if (words.all { hay.contains(it) }) {
            out += DhikrHit(f, i)
            if (out.size >= limit) return out
        }
    }
    return out
}

@Composable
fun HadithSearchScreen(
    onBack: () -> Unit,
    onOpenHadith: (bookId: String, chapterId: Int, number: Int) -> Unit,
    onOpenDhikr: (folderId: String) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<HadithHit>>(emptyList()) }
    var dhikr by remember { mutableStateOf<List<DhikrHit>>(emptyList()) }
    var scanned by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val folders by produceState<List<AdhkarFolder>>(emptyList()) { value = AdhkarRepository.loadAdhkar(context) }
    val installedCount = remember { HadithLibrary.installedBooks(context).size }
    val totalBooks = remember { HadithLibrary.books(context).size }

    LaunchedEffect(Unit) { focus.requestFocus() }
    // Restarts (and so cancels the running scan) on every change of the text.
    LaunchedEffect(query, folders) {
        val q = query.trim()
        if (q.length < 2 && !q.all { it.isDigit() && q.isNotEmpty() }) {
            hits = emptyList(); dhikr = emptyList(); searching = false
            return@LaunchedEffect
        }
        delay(250)
        searching = true
        dhikr = if (q.all { it.isDigit() }) emptyList() else withContext(Dispatchers.Default) { searchAdhkar(folders, q) }
        HadithLibrary.search(context, q) { h, s, t -> hits = h; scanned = s; total = t }
        searching = false
    }

    val words = remember(query) { query.trim().lowercase().split(' ').filter { it.isNotEmpty() } }
    val arabicQuery = query.any { it in '؀'..'ۿ' }

    HadithRoot(lang) {
        Column(Modifier.fillMaxSize()) {
            HadithTopBar(title = if (lang == "ar") "بحث" else "SEARCH", onBack = onBack)
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        if (lang == "ar") "ابحث في الأحاديث والأذكار…" else "Search hadith and adhkar, or a hadith number…",
                        color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.5f))
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = AppTheme.colors.primary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .focusRequester(focus)
            )
            Spacer(Modifier.height(8.dp))

            val status = when {
                query.trim().length < 2 && !query.trim().all { it.isDigit() && query.isNotEmpty() } ->
                    if (lang == "ar") "اكتب حرفين على الأقل" else "Type at least two letters."
                searching -> if (lang == "ar") "جارٍ البحث… $scanned/$total" else "Searching… $scanned of $total books"
                hits.isEmpty() && dhikr.isEmpty() -> if (lang == "ar") "لا نتائج" else "No results."
                else -> ""
            }
            if (status.isNotEmpty()) {
                Text(status, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp))
            }
            if (installedCount < totalBooks && query.isNotBlank()) {
                Text(
                    text = if (lang == "ar") "البحث في $installedCount من $totalBooks كتابًا — نزّل المزيد من قسم الأحاديث."
                    else "Searching $installedCount of $totalBooks books. Download more from the Hadith page to widen it.",
                    color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (dhikr.isNotEmpty()) {
                    item { SectionLabel(if (lang == "ar") "الأذكار" else "ADHKAR") }
                    items(dhikr) { d ->
                        ResultCard(
                            title = if (lang == "ar") d.folder.titleAr else d.folder.titleEn,
                            body = if (lang == "ar") AnnotatedString(d.item.textAr) else highlight(d.item.textEn, words, AppTheme.colors.primary),
                            arabic = lang == "ar",
                            onClick = { onOpenDhikr(d.folder.id) }
                        )
                    }
                }
                if (hits.isNotEmpty()) {
                    item { SectionLabel(if (lang == "ar") "الأحاديث" else "HADITH") }
                    items(hits, key = { "${it.bookId}|${it.chapter.id}|${it.hadith.number}" }) { hit ->
                        val book = HadithLibrary.book(context, hit.bookId)
                        val showArabic = lang == "ar" || arabicQuery || hit.hadith.english.isBlank()
                        ResultCard(
                            title = "${book?.title(lang) ?: hit.bookId} · ${hit.hadith.number}",
                            body = if (showArabic) AnnotatedString(hit.hadith.arabic)
                            else highlight(hit.hadith.english, words, AppTheme.colors.primary),
                            arabic = showArabic,
                            onClick = { onOpenHadith(hit.bookId, hit.chapter.id, hit.hadith.number) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(title: String, body: AnnotatedString, arabic: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(RoundedCornerShape(16.dp), AppTheme.colors.primary.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(title, color = AppTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, bypassNumerals = true)
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = if (arabic) 18.sp else 14.sp,
            lineHeight = if (arabic) 30.sp else 21.sp,
            fontFamily = if (arabic) SurahNameFontFamily else null,
            textAlign = if (arabic) TextAlign.Right else null,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            bypassNumerals = true
        )
    }
}

// -------------------------------------------------------------------------
// Saved
// -------------------------------------------------------------------------

private class SavedRow(val bookmark: Bookmark, val title: String, val body: String, val arabic: Boolean)

@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenHadith: (bookId: String, chapterId: Int, number: Int) -> Unit,
    onOpenDhikr: (folderId: String) -> Unit
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val bookmarks by HadithBookmarks.all.collectAsState()
    LaunchedEffect(Unit) { HadithBookmarks.ensureLoaded(context) }

    val rows by produceState<List<SavedRow>?>(null, bookmarks, lang) {
        value = withContext(Dispatchers.IO) {
            val folders = AdhkarRepository.loadAdhkar(context)
            bookmarks.mapNotNull { b ->
                when (b) {
                    is Bookmark.HadithRef -> {
                        val h = HadithLibrary.hadith(context, b.bookId, b.chapterId, b.number)
                        val book = HadithLibrary.book(context, b.bookId)
                        // A downloaded book that has since been removed cannot be shown;
                        // the bookmark is kept so it reappears when the book comes back.
                        if (h == null) SavedRow(b, "${book?.title(lang) ?: b.bookId} · ${b.number}",
                            if (lang == "ar") "الكتاب غير مثبّت" else "This book is not installed.", false)
                        else {
                            val ar = lang == "ar" || h.english.isBlank()
                            SavedRow(b, "${book?.title(lang) ?: b.bookId} · ${b.number}", if (ar) h.arabic else h.english, ar)
                        }
                    }
                    is Bookmark.DhikrRef -> {
                        val folder = folders.firstOrNull { it.id == b.folderId } ?: return@mapNotNull null
                        val item = folder.items.withIndex().firstOrNull { (i, it) -> (it.id ?: i.toString()) == b.itemKey }?.value
                            ?: return@mapNotNull null
                        val ar = lang == "ar"
                        SavedRow(b, if (ar) folder.titleAr else folder.titleEn, if (ar) item.textAr else item.textEn, ar)
                    }
                }
            }
        }
    }

    HadithRoot(lang) {
        Column(Modifier.fillMaxSize()) {
            HadithTopBar(title = if (lang == "ar") "المحفوظات" else "SAVED", onBack = onBack)
            val list = rows
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = AppTheme.colors.primary) }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(40.dp), Alignment.Center) {
                    Text(
                        text = if (lang == "ar") "لا محفوظات بعد. اضغط رمز العلامة على أي حديث أو ذكر."
                        else "Nothing saved yet. Tap the bookmark on any hadith or dhikr and it shows up here.",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.bookmark.key }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassCard(RoundedCornerShape(16.dp), AppTheme.colors.primary.copy(alpha = 0.35f))
                                .clickable {
                                    when (val b = row.bookmark) {
                                        is Bookmark.HadithRef -> onOpenHadith(b.bookId, b.chapterId, b.number)
                                        is Bookmark.DhikrRef -> onOpenDhikr(b.folderId)
                                    }
                                }
                                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title, color = AppTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, bypassNumerals = true)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = row.body,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = if (row.arabic) 17.sp else 14.sp,
                                    lineHeight = if (row.arabic) 28.sp else 21.sp,
                                    fontFamily = if (row.arabic) SurahNameFontFamily else null,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    bypassNumerals = true
                                )
                            }
                            IconButton(onClick = { HadithBookmarks.remove(context, row.bookmark) }) {
                                Icon(Icons.Default.Bookmark, contentDescription = "Remove bookmark", tint = AppTheme.colors.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Hadith of the day, for the home screen
// -------------------------------------------------------------------------

@Composable
fun HadithOfTheDayCard(onOpen: (bookId: String, chapterId: Int, number: Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val hit by produceState<HadithHit?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { HadithLibrary.hadithOfTheDay(context) }.getOrNull() }
    }
    val h = hit ?: return
    val book = remember(h.bookId) { HadithLibrary.book(context, h.bookId) }
    val arabic = lang == "ar" || h.hadith.english.isBlank()
    val bookmarks by HadithBookmarks.all.collectAsState()
    LaunchedEffect(Unit) { HadithBookmarks.ensureLoaded(context) }
    val bmKey = Bookmark.hadithKey(h.bookId, h.chapter.id, h.hadith.number)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassCard(RoundedCornerShape(22.dp), AppTheme.colors.secondary)
            .clickable { onOpen(h.bookId, h.chapter.id, h.hadith.number) }
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (lang == "ar") "حديث اليوم" else "HADITH OF THE DAY",
                color = AppTheme.colors.secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            ItemActions(
                bookmarked = bookmarks.any { it.key == bmKey },
                onToggleBookmark = { HadithBookmarks.toggleHadith(context, h.bookId, h.chapter.id, h.hadith.number) },
                onCopy = { copyToClipboard(context, shareText(book, lang, h.hadith)) },
                onShare = {
                    io.github.sammynashed.emran.ui.share.ShareCard.share(
                        context,
                        io.github.sammynashed.emran.ui.share.ShareCard.Content(
                            arabic = h.hadith.arabic,
                            english = h.hadith.text.ifBlank { null },
                            narrator = h.hadith.narrator.ifBlank { null },
                            source = "${book?.title(lang) ?: ""} · ${h.hadith.number}"
                        ),
                        lang
                    )
                }
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (arabic) h.hadith.arabic else h.hadith.english,
            color = Color.White,
            fontSize = if (arabic) 19.sp else 15.sp,
            lineHeight = if (arabic) 32.sp else 23.sp,
            fontFamily = if (arabic) SurahNameFontFamily else null,
            textAlign = if (arabic) TextAlign.Right else null,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(end = 10.dp),
            bypassNumerals = true
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "${book?.title(lang) ?: ""} · ${h.hadith.number}",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 10.dp),
            bypassNumerals = true
        )
    }
}
