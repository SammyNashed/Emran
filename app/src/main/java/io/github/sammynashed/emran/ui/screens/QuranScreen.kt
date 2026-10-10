package io.github.sammynashed.emran.ui.screens

import io.github.sammynashed.emran.ui.components.EmranAlertDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.testTag
import io.github.sammynashed.emran.ui.tour.tourTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.sammynashed.emran.tajweed.TajweedRule
import io.github.sammynashed.emran.tajweed.MushafText
import io.github.sammynashed.emran.tajweed.tajweedPage
import io.github.sammynashed.emran.tajweed.wordKey
import io.github.sammynashed.emran.recitation.RecitationAssessor
import io.github.sammynashed.emran.recitation.rememberHifzSession
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammynashed.emran.R
import io.github.sammynashed.emran.data.QuranChapter
import io.github.sammynashed.emran.data.QuranVerse
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Localizations
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.viewmodel.QuranUiState
import io.github.sammynashed.emran.ui.viewmodel.QuranViewModel

/**
 * The three page finishes. Paper is a warm printed-page cream with ink-black text;
 * Night is warm charcoal with parchment text (not blue-grey, which reads as a screen);
 * AMOLED is true black with softened text so it does not glare. [ornament] is the
 * gilt of the frame, title cartouches and āyah rosettes.
 */
enum class QuranThemeMode(val backgroundColor: Color, val textColor: Color, val label: String, val ornament: Color) {
    DAY(Color(0xFFF7EFDF), Color(0xFF1E1912), "day_mode", Color(0xFFA9844A)),
    NIGHT(Color(0xFF16130F), Color(0xFFE8E0CF), "night_mode", Color(0xFFB59A64)),
    AMOLED(Color.Black, Color(0xFFCBC6BC), "amoled_black", Color(0xFF8A7650))
}

/**
 * The Uthmani script face used for Quran and adhkar text.
 *
 * Amiri Quran, not KFGQPC Hafs. The KFGQPC face is "all rights reserved" and
 * forbids modification, which cannot be reconciled with this repository's
 * GPL-3.0 licence -- the GPL grants every recipient the right to modify and
 * redistribute what they receive, and a file that forbids exactly that cannot
 * travel inside it. Amiri Quran is SIL OFL 1.1, purpose-built for Quranic
 * typesetting, and carries the same full harakat and Uthmani orthography.
 */
/**
 * The mushaf body face.
 *
 * Scheherazade New (SIL, OFL 1.1), which covers **every one of the 73
 * codepoints** in Emran's Uthmani text. The previous face, Amiri Quran, missed
 * `U+065E` - a mark that appears 1,807 times in the Qur'an - so those glyphs
 * fell back to whatever the system had, changing face mid-word.
 *
 * KFGQPC's own Uthmanic Hafs was the obvious candidate and was rejected on
 * measurement: it covers 1 of those 73 codepoints, because it is drawn against
 * KFGQPC's private encoding rather than Unicode and needs their text data to go
 * with it. Adopting it would mean replacing the entire bundled Qur'an, which
 * would in turn break the romaniser and the tajweed annotator, both of which
 * index into this text.
 *
 * OFL requires the licence to travel with the font - it is in
 * `assets/licenses/` - but requires no visible credit in the app.
 */
val HafsFontFamily = FontFamily(
    Font(resId = R.font.scheherazade_new)
)

/**
 * The basmala at the head of each surah.
 *
 * Amiri Quran, the same face as the ayah text. In a printed Mushaf the basmala
 * is set in the body script rather than a display face, and the previous
 * `bismillah.ttf` was a 3.5 KB single-glyph font: it carried the ligature and
 * almost nothing else, so the fully-vocalised basmala string fell back to the
 * system font mid-line, which is why it looked wrong.
 */
val BismillahFontFamily = FontFamily(
    Font(resId = R.font.scheherazade_new)
)

/**
 * Surah names in the chapter banner.
 *
 * Amiri proper -- a revival of the Bulaq Press Naskh that Mushaf headings have
 * been set in since the nineteenth century. Heavier and more ornate than the
 * body face, which is the relationship a printed Mushaf has between its
 * headings and its ayat.
 */
val SurahNameFontFamily = FontFamily(
    Font(resId = R.font.amiri_bold, FontWeight.Bold),
    Font(resId = R.font.amiri_regular, FontWeight.Normal)
)

// Decorative borders representing premium physical Mushaf page frames
fun Modifier.mushafBorder(color: Color): Modifier = drawBehind {
    val borderWidth = 1.dp.toPx()
    val padding = 12.dp.toPx()
    val doubleLineGap = 4.dp.toPx()
    
    // Draw Outer Rect
    drawRect(
        color = color.copy(alpha = 0.35f),
        topLeft = Offset(padding, padding),
        size = Size(size.width - padding * 2, size.height - padding * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderWidth)
    )
    
    // Draw Inner Rect
    val innerPadding = padding + doubleLineGap
    drawRect(
        color = color.copy(alpha = 0.2f),
        topLeft = Offset(innerPadding, innerPadding),
        size = Size(size.width - innerPadding * 2, size.height - innerPadding * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderWidth * 0.5f)
    )
    
    // Draw decorative corner blocks
    val cornerSize = 6.dp.toPx()
    // Top-left
    drawRect(
        color = color.copy(alpha = 0.5f),
        topLeft = Offset(padding - 1.dp.toPx(), padding - 1.dp.toPx()),
        size = Size(cornerSize, cornerSize)
    )
    // Top-right
    drawRect(
        color = color.copy(alpha = 0.5f),
        topLeft = Offset(size.width - padding - cornerSize + 1.dp.toPx(), padding - 1.dp.toPx()),
        size = Size(cornerSize, cornerSize)
    )
    // Bottom-left
    drawRect(
        color = color.copy(alpha = 0.5f),
        topLeft = Offset(padding - 1.dp.toPx(), size.height - padding - cornerSize + 1.dp.toPx()),
        size = Size(cornerSize, cornerSize)
    )
    // Bottom-right
    drawRect(
        color = color.copy(alpha = 0.5f),
        topLeft = Offset(size.width - padding - cornerSize + 1.dp.toPx(), size.height - padding - cornerSize + 1.dp.toPx()),
        size = Size(cornerSize, cornerSize)
    )
}

@Composable
fun SurahHeaderBanner(
    name: String,
    fontSizeMultiplier: Float,
    currentTheme: QuranThemeMode
) {
    val animatedTextColor by animateColorAsState(targetValue = currentTheme.textColor, animationSpec = tween(500), label = "text_header")
    val bannerBackground = if (currentTheme == QuranThemeMode.DAY) Color(0xFFEFEAE2) else currentTheme.backgroundColor.copy(alpha = 0.9f)
    val bannerBorder = if (currentTheme == QuranThemeMode.DAY) Color(0xFFC4B49F) else Color(0xFFFFD700).copy(alpha = 0.3f)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(bannerBackground, RoundedCornerShape(12.dp))
            .border(2.dp, bannerBorder, RoundedCornerShape(12.dp))
            .padding(4.dp)
            .border(1.dp, bannerBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "سُورَةُ $name",
            fontFamily = SurahNameFontFamily,
            fontSize = 30.sp,
            color = animatedTextColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BismillahText(
    fontSizeMultiplier: Float,
    currentTheme: QuranThemeMode
) {
    val animatedTextColor by animateColorAsState(targetValue = currentTheme.textColor, animationSpec = tween(500), label = "text_bismillah")
    Text(
        text = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ",
        fontFamily = BismillahFontFamily,
        fontSize = 34.sp,
        color = animatedTextColor,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahSelectorDialog(
    /** The surah being read, or null when choosing one for the first time. */
    currentChapter: QuranChapter?,
    allChapters: List<QuranChapter>,
    onSelect: (QuranChapter) -> Unit,
    onDismiss: () -> Unit,
    currentTheme: QuranThemeMode,
    lang: String
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredChapters = remember(searchQuery, allChapters) {
        if (searchQuery.isBlank()) {
            allChapters
        } else {
            allChapters.filter {
                it.transliteration.contains(searchQuery, ignoreCase = true) ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.id.toString() == searchQuery
            }
        }
    }

    EmranAlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .heightIn(max = 600.dp)
            .clip(RoundedCornerShape(24.dp)),
        confirmButton = {},
        dismissButton = {},
        // The page's own paper, so choosing a surah never leaves the muṣḥaf.
        containerColor = androidx.compose.ui.graphics.lerp(currentTheme.backgroundColor, currentTheme.textColor, 0.04f),
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (lang == "ar") "اختر السورة" else "Select Surah",
                    color = currentTheme.textColor,
                    fontSize = 24.sp,
                    fontFamily = SurahNameFontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (lang == "ar") "بحث عن سورة..." else "Search Surah...",
                            color = currentTheme.textColor.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = currentTheme.textColor.copy(alpha = 0.05f),
                        unfocusedContainerColor = currentTheme.textColor.copy(alpha = 0.05f),
                        focusedTextColor = currentTheme.textColor,
                        unfocusedTextColor = currentTheme.textColor,
                        cursorColor = currentTheme.ornament,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }
        },
        text = {
            // Opens at the surah being read, not at al-Fātiḥah.
            val listState = androidx.compose.foundation.lazy.rememberLazyListState(
                initialFirstVisibleItemIndex = (filteredChapters.indexOfFirst { it.id == currentChapter?.id } - 1).coerceAtLeast(0)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredChapters) { chapter ->
                    val isSelected = chapter.id == currentChapter?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) currentTheme.ornament.copy(alpha = 0.14f)
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) currentTheme.ornament.copy(alpha = 0.6f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(chapter) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (isSelected) currentTheme.ornament else currentTheme.textColor.copy(alpha = 0.08f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chapter.id.toString(),
                                    color = if (isSelected) currentTheme.backgroundColor else currentTheme.textColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = chapter.transliteration,
                                    color = currentTheme.textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = surahMetaLabel(chapter.type, chapter.total_verses, lang),
                                    color = currentTheme.textColor.copy(alpha = 0.55f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = chapter.name,
                            maxLines = 1,
                            softWrap = false,
                            fontFamily = SurahNameFontFamily,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(start = 8.dp),
                            color = if (isSelected) currentTheme.ornament else currentTheme.textColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    )
}

/** "meccan" / "medinan" come straight from quran.json; show them as proper words. */
internal fun surahMetaLabel(type: String, verses: Int, lang: String): String {
    val place = when (type.lowercase()) {
        "meccan" -> if (lang == "ar") "مكية" else "Meccan"
        "medinan" -> if (lang == "ar") "مدنية" else "Medinan"
        else -> type.replaceFirstChar { it.uppercase() }
    }
    return if (lang == "ar") "$place • $verses آية" else "$place • $verses Verses"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranScreen(
    modifier: Modifier = Modifier,
    viewModel: QuranViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPaginatedMode by viewModel.isPaginatedMode.collectAsState()
    val allChapters by viewModel.allChapters.collectAsState()

    val lang = LocalAppLanguage.current
    // One size: the printed page is set as large as the screen allows, and the old
    // flowing fallback below only appears if the layout file is missing.
    val fontSizeMultiplier = 1f

    // Parse the muṣḥaf text while the reader is still choosing a surah, so the page
    // (and every surah sharing it) is complete on its first frame.
    val mushafContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { MushafSource.load(mushafContext) }
    }
    val surahNames = remember(allChapters) { allChapters.associate { it.id to it.name } }
    // Tajweed colouring is part of how Emran renders the Qur'an, not a
    // preference. A reader learns the rules by seeing them applied, and a
    // switch that turns them off mostly serves people who already know them.
    val tajweedEnabled = true
    var showTajweedLegend by remember { mutableStateOf(false) }

    // Hifz mode: the same page, with everything not yet recited out of
    // focus. It is a way of reading the surah, not a separate screen, so it
    // lives here beside the other reading preferences.
    var hifzMode by remember { mutableStateOf(false) }
    val hifzContext = androidx.compose.ui.platform.LocalContext.current
    val hifzAssessor = remember { RecitationAssessor(hifzContext) }
    val hifz = rememberHifzSession(hifzAssessor)
    // The whole surah, not the current page: Hifz mode runs across all of it,
    // and in paginated reading the page only holds a slice.
    val hifzVerses = remember(uiState) {
        (uiState as? QuranUiState.SuccessVerses)
            ?.let { st -> st.verses.filter { it.chapterId == st.chapter.id } }
            ?: emptyList()
    }

    // Every word of the surah, addressed the way Hifz mode addresses them, so
    // progress counts the surah rather than whatever page happens to be on
    // screen. Word counts come from the same whitespace split the page builder
    // uses, so the two can never disagree about what a word is.
    val hifzWordKeys = remember(hifzVerses) {
        hifzVerses.flatMap { v ->
            v.text.split(Regex("""\s+""")).filter { it.isNotBlank() }
                .mapIndexed { i, _ -> wordKey(v.id, i) }
        }
    }
    val hifzWordTotal = hifzWordKeys.size

    // How much of the surah the transition is currently showing, 0..1.
    //
    // Entering Hifz the page holds itself visible for a beat and then empties
    // from the end backwards, so the last thing to go is the first word - the
    // one the reciter is about to say. Returning to Tilawah it fills forwards
    // from the opening, the way it is read. Both run over the same value; only
    // the direction it is read in differs.
    val sweep = remember { androidx.compose.animation.core.Animatable(1f) }
    var sweeping by remember { mutableStateOf(false) }

    LaunchedEffect(hifzMode, hifzWordTotal) {
        if (hifzWordTotal == 0) return@LaunchedEffect
        if (hifzMode) {
            // Every entry into Hifz is a clean page: words revealed in an earlier
            // run (or by "Reveal all") must not carry over.
            hifz.restart()
            sweeping = true
            sweep.snapTo(1f)
            // A beat to read the page before it goes.
            kotlinx.coroutines.delay(HIFZ_INTRO_HOLD_MS)
            sweep.animateTo(0f, androidx.compose.animation.core.tween(HIFZ_SWEEP_MS))
            sweeping = false
        } else {
            sweeping = true
            sweep.snapTo(0f)
            sweep.animateTo(1f, androidx.compose.animation.core.tween(HIFZ_SWEEP_MS))
            sweeping = false
        }
    }

    // What the page shows right now: whatever has been recited, plus whatever
    // the sweep is still holding visible.
    val hifzShown = remember(hifz.revealed, hifz.unsure, sweep.value, sweeping, hifzWordKeys) {
        val recited = hifz.revealed + hifz.unsure
        if (!sweeping) {
            recited
        } else {
            // Always the first n words. Direction lives in the animation, not
            // here: running n down empties from the end and leaves the opening
            // for last, running it up fills forward from the opening. One rule
            // serves both sweeps, which is why there is no branch.
            val n = (hifzWordKeys.size * sweep.value).toInt().coerceIn(0, hifzWordKeys.size)
            recited + hifzWordKeys.take(n)
        }
    }

    // Where the reciter is: the first word of the current ayah still unspoken.
    val hifzCurrent = remember(hifz.revealed, hifz.ayahNumber, hifzWordKeys, hifz.recording) {
        if (!hifz.recording) null
        else hifzWordKeys.firstOrNull {
            (it shr 12) == hifz.ayahNumber && it !in hifz.revealed
        }
    }

    // Feed assessment results into the session. Keyed on the recorder alone so
    // the collector is not torn down and restarted every time a word resolves.
    LaunchedEffect(hifz.recorder, hifzVerses) {
        hifz.recorder.results.collect { r ->
            hifz.onResult(r, hifzVerses.map { it.text }, hifzVerses.map { it.id })
        }
    }

    // Drive the reveal fade. A word arrives at full opacity in the mask layer
    // and is stepped down to nothing, so it resolves into focus rather than
    // appearing. The loop only runs while something is actually fading.
    LaunchedEffect(hifz.fading.isEmpty()) {
        while (hifz.fading.isNotEmpty()) {
            withFrameNanos { }
            hifz.tickFade(FADE_STEP)
        }
    }

    // Leaving the surah must not leave the microphone running.
    DisposableEffect(Unit) {
        onDispose {
            hifz.recorder.stop()
        }
    }
    var currentTheme by remember { mutableStateOf(QuranThemeMode.DAY) }
    var hifzNeedsMic by remember { mutableStateOf(false) }
    val hifzScope = rememberCoroutineScope()
    val askHifzMic = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> hifzNeedsMic = !granted }

    Column(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is QuranUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppTheme.colors.primary)
                }
            }
            is QuranUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Localizations.string("quran_err_fmt", lang, "Error: %s").format(state.message), color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadChapters() }) {
                            Text(Localizations.string("quran_retry", lang, "Retry"))
                        }
                    }
                }
            }
            is QuranUiState.SuccessChapters -> {
                // Choosing how to read comes before choosing what to read. The
                // old surah list lived here; it is now reached through the same
                // selector the reading screen uses, so there is one way to pick
                // a surah rather than two that drift apart.
                var pendingHifz by remember { mutableStateOf<Boolean?>(null) }
                val bookmarkedChapterId by viewModel.bookmarkedChapterId.collectAsState()

                // The picker is a screen of its own on the sky (not a pop-up), so Back must
                // close it and return to the two ways of reading.
                if (pendingHifz == null) {
                    QuranModesPage(
                        lang = lang,
                        resumeChapter = state.chapters.find { it.id == bookmarkedChapterId },
                        onTilawah = { pendingHifz = false },
                        onHifz = { pendingHifz = true },
                        onResume = { chapter ->
                            hifzMode = false
                            viewModel.loadSurah(chapter)
                        },
                    )
                } else {
                    BackHandler { pendingHifz = null }
                    SurahPicker(
                        lang = lang,
                        allChapters = state.chapters,
                        onSelect = { selected ->
                            hifzMode = pendingHifz == true
                            viewModel.loadSurah(selected)
                            pendingHifz = null
                        },
                        onBack = { pendingHifz = null },
                    )
                }
            }
            is QuranUiState.SuccessVerses -> {
                BackHandler {
                    viewModel.loadChapters()
                }
                
                val animatedBackgroundColor by animateColorAsState(
                    targetValue = currentTheme.backgroundColor,
                    animationSpec = tween(500), label = "bg"
                )
                val animatedTextColor by animateColorAsState(
                    targetValue = currentTheme.textColor,
                    animationSpec = tween(500), label = "text"
                )

                val dockVisible = io.github.sammynashed.emran.LocalDockVisible.current
                
                LaunchedEffect(Unit) {
                    dockVisible.value = false
                }
                
                DisposableEffect(Unit) {
                    onDispose { dockVisible.value = true }
                }

                // Controls visibility toggled via single tap for immersive experience
                var isControlsVisible by remember { mutableStateOf(true) }
                // Shown on arrival so they can be found, then out of the way: the page is
                // what matters. A tap brings them back. Hifz keeps them, it needs Start.
                LaunchedEffect(Unit) {
                    delay(2600)
                    if (!hifzMode) isControlsVisible = false
                }
                var showSurahSelector by remember { mutableStateOf(false) }
                var showRecitationSheet by remember { mutableStateOf(false) }
                val recitationContext = androidx.compose.ui.platform.LocalContext.current

                // Auto Scroll engine state (Scroll View only)
                var isAutoScrolling by remember { mutableStateOf(false) }
                var autoScrollSpeed by remember { mutableStateOf(2) } // Speeds from 1 (slow) to 5 (fast)

                val arabicDigits = remember { listOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩') }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                color = animatedBackgroundColor,
                                topLeft = Offset(0f, -300f),
                                size = Size(size.width, size.height + 600f)
                            )
                        }
                ) {
                    // The page owns the whole screen; the bars float over it, so showing or
                    // hiding them never re-lays-out the page underneath.
                    Box(modifier = Modifier.fillMaxSize()) {
                        // IMMERSIVE TOP BAR
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isControlsVisible,
                            modifier = Modifier.zIndex(1f),
                            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                        ) {
                            TopAppBar(
                                title = {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showSurahSelector = true }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = state.chapter.name,
                                            color = animatedTextColor,
                                            fontFamily = SurahNameFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Surah",
                                            tint = animatedTextColor,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { viewModel.loadChapters() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = animatedTextColor)
                                    }
                                },
                                actions = {
                                    // Switching how you read belongs beside the
                                    // surah you are reading, not buried in the
                                    // tools sheet: it changes what the page is,
                                    // not how it is decorated.
                                    IconButton(onClick = { hifzMode = !hifzMode }) {
                                        Icon(
                                            imageVector = if (hifzMode) {
                                                Icons.Default.MenuBook
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = when {
                                                hifzMode && lang == "ar" -> "التبديل إلى وضع التلاوة"
                                                hifzMode -> "Switch to Tilawah mode"
                                                lang == "ar" -> "التبديل إلى وضع الحفظ"
                                                else -> "Switch to Hifz mode"
                                            },
                                            tint = if (hifzMode) {
                                                currentTheme.ornament
                                            } else {
                                                animatedTextColor
                                            },
                                        )
                                    }

                                    // Recitation Check opens as a sheet from here rather
                                    // than sitting inline under the mushaf text.
                                    // Always the model sheet. Reciting lives
                                    // in Hifz mode on the page itself, so this
                                    // icon answers one question: is the model
                                    // here, and do I want it here.
                                    IconButton(onClick = { showRecitationSheet = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = if (lang == "ar") "تدقيق التلاوة" else "Recitation Check",
                                            tint = animatedTextColor
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = animatedBackgroundColor),
                                // The reader already sits below the status bar; a second inset left an empty band.
                                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                                // Opaque, with a hairline in the page's ornament colour: the page
                                // never ghosts through the bar.
                                modifier = Modifier.drawBehind {
                                    drawLine(
                                        currentTheme.ornament.copy(alpha = 0.45f),
                                        start = Offset(0f, size.height - 0.5.dp.toPx()),
                                        end = Offset(size.width, size.height - 0.5.dp.toPx()),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                },
                            )
                        }

                        // PAGE BOUNDARIES BORDER FOR AUTHENTIC PHYSICAL MUSHAF FEEL
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    // The printed page draws its own frame.
                                    if (isPaginatedMode || mushafLayoutAvailable()) Modifier else Modifier.mushafBorder(
                                        color = when (currentTheme) {
                                            QuranThemeMode.DAY -> Color(0xFFC4B49F)
                                            QuranThemeMode.NIGHT -> Color(0xFFFFD700)
                                            QuranThemeMode.AMOLED -> Color(0xFF333333)
                                        }
                                    )
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            isControlsVisible = !isControlsVisible
                                        }
                                    )
                                }
                        ) {
                            val sortedPageKeys = remember(state.verses) {
                                state.verses.groupBy { it.page }.keys.sorted()
                            }

                            val pages = remember(state.verses, sortedPageKeys) {
                                if (sortedPageKeys.isEmpty()) {
                                    listOf(emptyList<QuranVerse>())
                                } else {
                                    val groupedByPage = state.verses.groupBy { it.page }
                                    sortedPageKeys.map { pageNum ->
                                        groupedByPage[pageNum] ?: emptyList()
                                    }
                                }
                            }

                            val isContinuous by viewModel.isContinuousMode.collectAsState()

                            val targetPageNum = remember(state.chapter.id, state.verses) {
                                state.verses.firstOrNull { it.chapterId == state.chapter.id }?.page
                            }
                            val targetPageIndex = remember(targetPageNum, sortedPageKeys) {
                                targetPageNum?.let { sortedPageKeys.indexOf(it) } ?: -1
                            }

                            if (isPaginatedMode) {
                                val pagerState = rememberPagerState(pageCount = { pages.size })
                                
                                val bookmarkedChapterId by viewModel.bookmarkedChapterId.collectAsState()
                                val bookmarkedScrollPos by viewModel.bookmarkedScrollPos.collectAsState()
                                
                                LaunchedEffect(state.chapter.id) {
                                    if (isContinuous) {
                                        if (targetPageIndex != -1) {
                                            pagerState.scrollToPage(targetPageIndex)
                                        }
                                    } else {
                                        if (state.chapter.id == bookmarkedChapterId && bookmarkedScrollPos > 0 && bookmarkedScrollPos < pages.size) {
                                            pagerState.scrollToPage(bookmarkedScrollPos)
                                        }
                                    }
                                }

                                DisposableEffect(Unit) {
                                    onDispose {
                                        val currentPage = pagerState.currentPage
                                        val versesOnCurrentPage = pages.getOrNull(currentPage) ?: emptyList()
                                        val activeChapterId = versesOnCurrentPage.firstOrNull()?.chapterId ?: state.chapter.id
                                        if (currentPage > 0) {
                                            viewModel.saveBookmark(activeChapterId, currentPage)
                                        }
                                    }
                                }

                                // A surah counts as read once its last page has been
                                // reached, which is the only point where "finished it"
                                // is actually true. Recorded live rather than on exit,
                                // so the widget moves while reading.
                                LaunchedEffect(pagerState.currentPage, pages.size) {
                                    if (pages.isNotEmpty() && pagerState.currentPage == pages.lastIndex) {
                                        val versesOnPage = pages.getOrNull(pagerState.currentPage) ?: emptyList()
                                        val chapterId = versesOnPage.firstOrNull()?.chapterId ?: state.chapter.id
                                        viewModel.recordSurahCompleted(chapterId)
                                    }
                                }

                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize()
                                        ) { pageIndex ->
                                            val pageScrollState = rememberScrollState()
                                            val versesInPage = pages[pageIndex]
                                            val actualPageNum = sortedPageKeys.getOrNull(pageIndex) ?: 1

                                            // The printed page: fifteen justified lines on the
                                            // Madinah layout. Flowing text below is only the
                                            // fallback for a missing layout file.
                                            if (mushafLayoutAvailable()) {
                                                MushafPageView(
                                                    pageNumber = actualPageNum,
                                                    loadedVerses = versesInPage,
                                                    theme = currentTheme,
                                                    tajweed = tajweedEnabled,
                                                    hifzSurah = if (hifzMode || sweeping) state.chapter.id else null,
                                                    revealed = hifzShown,
                                                    fading = hifz.fading,
                                                    current = hifzCurrent,
                                                    highlight = currentTheme.ornament.copy(alpha = 0.28f),
                                                    arabicDigits = arabicDigits,
                                                    surahNames = surahNames + (state.chapter.id to state.chapter.name),
                                                    onlySurah = if (isContinuous) null else state.chapter.id,
                                                )
                                                return@HorizontalPager
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .verticalScroll(pageScrollState)
                                                    .padding(horizontal = 28.dp, vertical = 24.dp)
                                            ) {
                                                // The printed layout when it is
                                                // available: fifteen lines, each
                                                // ayah beginning where the muṣḥaf
                                                // begins it. Falls back to flowing
                                                // text if the asset is missing, so
                                                // a broken layout file costs the
                                                // geometry and not the Qur'an.
                                                if (mushafLayoutAvailable()) {
                                                    MushafPageLines(
                                                        pageNumber = actualPageNum,
                                                        verses = versesInPage,
                                                        fontSizeMultiplier = fontSizeMultiplier,
                                                        textColor = animatedTextColor,
                                                        darkTheme = currentTheme != QuranThemeMode.DAY,
                                                        tajweed = tajweedEnabled,
                                                        hifz = hifzMode || sweeping,
                                                        revealed = hifzShown,
                                                        fading = hifz.fading,
                                                        current = hifzCurrent,
                                                        highlight = currentTheme.ornament.copy(alpha = 0.18f),
                                                        arabicDigits = arabicDigits,
                                                        currentTheme = currentTheme,
                                                    )
                                                    return@Column
                                                }

                                                // Group verses by chapter to handle transitions/headers beautifully
                                                val chaptersOnPage = remember(versesInPage) {
                                                    versesInPage.groupBy { it.chapterId }
                                                }

                                                chaptersOnPage.forEach { (chapterId, verses) ->
                                                    val firstVerse = verses.first()
                                                    val chapterName = firstVerse.chapterName

                                                    if (firstVerse.id == 1) {
                                                        SurahHeaderBanner(name = chapterName, fontSizeMultiplier = fontSizeMultiplier, currentTheme = currentTheme)
                                                        Spacer(modifier = Modifier.height(16.dp))

                                                        if (chapterId != 1 && chapterId != 9) {
                                                            BismillahText(fontSizeMultiplier = fontSizeMultiplier, currentTheme = currentTheme)
                                                            Spacer(modifier = Modifier.height(16.dp))
                                                        }
                                                    }

                                                    val chapterText = remember(verses, tajweedEnabled, currentTheme) {
                                                        verses.map { verse ->
                                                            val arabicNumber = verse.id.toString().map { c ->
                                                                if (c.isDigit()) arabicDigits[c.digitToInt()] else c
                                                            }.joinToString("")

                                                            var cleanVerseText = verse.text.replace(Regex("[\\{\\}\\(\\)\\[\\]﴿﴾۝۞۩\\u06DD\\u06DE\\uFD3E\\uFD3F]"), "")
                                                            cleanVerseText = cleanVerseText.replace(Regex("[\\s\\d٠-٩\\u200E\\u200F\\u200C]+$"), "").trim()
                                                            Triple(verse.id, cleanVerseText, " \uFD3F$arabicNumber\uFD3E")
                                                        }.let { pieces ->
                                                            tajweedPage(
                                                                verses = pieces,
                                                                separator = "  ",
                                                                darkTheme = currentTheme != QuranThemeMode.DAY,
                                                                enabled = tajweedEnabled,
                                                            )
                                                        }
                                                    }

                                                    MushafText(
                                                        page = chapterText,
                                                        fontFamily = HafsFontFamily,
                                                        fontSize = (32 * fontSizeMultiplier).sp,
                                                        lineHeight = (32 * fontSizeMultiplier * 1.6f).sp,
                                                        color = animatedTextColor,
                                                        hifz = hifzMode || sweeping,
                                                        revealed = hifzShown,
                                                        fading = hifz.fading,
                                                        current = hifzCurrent,
                                                        highlight = currentTheme.ornament.copy(alpha = 0.18f),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                }

                                                Spacer(modifier = Modifier.height(32.dp))

                                                val pageLabel = if (lang == "ar") "صَفْحَة $actualPageNum" else "Page $actualPageNum"
                                                Text(
                                                    text = pageLabel,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = animatedTextColor.copy(alpha = 0.5f),
                                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                                )
                                                Spacer(modifier = Modifier.height(120.dp))
                                            }
                                        }

                                    }
                                }
                            } else {
                                val lazyListState = rememberLazyListState()
                                val bookmarkedChapterId by viewModel.bookmarkedChapterId.collectAsState()
                                val bookmarkedScrollPos by viewModel.bookmarkedScrollPos.collectAsState()

                                val scrollPages = remember(state.verses, sortedPageKeys) {
                                    if (sortedPageKeys.isEmpty()) {
                                        listOf(emptyList<QuranVerse>())
                                    } else {
                                        val groupedByPage = state.verses.groupBy { it.page }
                                        sortedPageKeys.map { pageNum ->
                                            groupedByPage[pageNum] ?: emptyList()
                                        }
                                    }
                                }

                                LaunchedEffect(state.chapter.id) {
                                    if (isContinuous) {
                                        if (targetPageIndex != -1) {
                                            lazyListState.scrollToItem(targetPageIndex)
                                        }
                                    } else {
                                        if (state.chapter.id == bookmarkedChapterId && bookmarkedScrollPos > 0 && bookmarkedScrollPos < scrollPages.size) {
                                            lazyListState.scrollToItem(bookmarkedScrollPos)
                                        }
                                    }
                                }

                                DisposableEffect(Unit) {
                                    onDispose {
                                        val firstVisibleIndex = lazyListState.firstVisibleItemIndex
                                        val versesOnCurrentPage = scrollPages.getOrNull(firstVisibleIndex) ?: emptyList()
                                        val activeChapterId = versesOnCurrentPage.firstOrNull()?.chapterId ?: state.chapter.id
                                        if (firstVisibleIndex > 0) {
                                            viewModel.saveBookmark(activeChapterId, firstVisibleIndex)
                                        }
                                    }
                                }

                                // Auto scroll engine tick
                                LaunchedEffect(isAutoScrolling, autoScrollSpeed) {
                                    if (isAutoScrolling) {
                                        while (true) {
                                            val delayMs = when (autoScrollSpeed) {
                                                1 -> 110L
                                                2 -> 80L
                                                3 -> 55L
                                                4 -> 35L
                                                5 -> 20L
                                                else -> 55L
                                            }
                                            delay(delayMs)
                                            try {
                                                lazyListState.scrollBy(2f)
                                            } catch (e: Exception) {
                                                // Ignore scroll exceptions during active interactions
                                            }
                                        }
                                    }
                                }

                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = if (mushafLayoutAvailable()) PaddingValues(0.dp) else PaddingValues(top = 16.dp, bottom = 140.dp)
                                ) {
                                    itemsIndexed(scrollPages) { pageIndex, versesInPage ->
                                        val actualPageNum = sortedPageKeys.getOrNull(pageIndex) ?: 1

                                        // Scrolling, the pages are the same printed pages,
                                        // one under the other, each a screen tall.
                                        if (mushafLayoutAvailable()) {
                                            MushafPageView(
                                                pageNumber = actualPageNum,
                                                loadedVerses = versesInPage,
                                                theme = currentTheme,
                                                tajweed = tajweedEnabled,
                                                hifzSurah = if (hifzMode || sweeping) state.chapter.id else null,
                                                revealed = hifzShown,
                                                fading = hifz.fading,
                                                current = hifzCurrent,
                                                highlight = currentTheme.ornament.copy(alpha = 0.28f),
                                                arabicDigits = arabicDigits,
                                                surahNames = surahNames + (state.chapter.id to state.chapter.name),
                                                onlySurah = if (isContinuous) null else state.chapter.id,
                                                modifier = Modifier.fillParentMaxHeight(),
                                            )
                                            return@itemsIndexed
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 12.dp)
                                        ) {
                                            // Group verses on this page by chapter to show headers nicely
                                            val chaptersOnPage = remember(versesInPage) {
                                                versesInPage.groupBy { it.chapterId }
                                            }

                                            chaptersOnPage.forEach { (chapterId, verses) ->
                                                val firstVerse = verses.first()
                                                val chapterName = firstVerse.chapterName

                                                if (firstVerse.id == 1) {
                                                    SurahHeaderBanner(name = chapterName, fontSizeMultiplier = fontSizeMultiplier, currentTheme = currentTheme)
                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    if (chapterId != 1 && chapterId != 9) {
                                                        BismillahText(fontSizeMultiplier = fontSizeMultiplier, currentTheme = currentTheme)
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                    }
                                                }

                                                val chapterText = remember(verses, tajweedEnabled, currentTheme) {
                                                    verses.map { verse ->
                                                        val arabicNumber = verse.id.toString().map { c ->
                                                            if (c.isDigit()) arabicDigits[c.digitToInt()] else c
                                                        }.joinToString("")

                                                        var cleanVerseText = verse.text.replace(Regex("[\\{\\}\\(\\)\\[\\]﴿﴾۝۞۩\\u06DD\\u06DE\\uFD3E\\uFD3F]"), "")
                                                        cleanVerseText = cleanVerseText.replace(Regex("[\\s\\d٠-٩\\u200E\\u200F\\u200C]+$"), "").trim()
                                                            Triple(verse.id, cleanVerseText, " \uFD3F$arabicNumber\uFD3E")
                                                    }.let { pieces ->
                                                        tajweedPage(
                                                            verses = pieces,
                                                            separator = "  ",
                                                            darkTheme = currentTheme != QuranThemeMode.DAY,
                                                            enabled = tajweedEnabled,
                                                        )
                                                    }
                                                }

                                                MushafText(
                                                        page = chapterText,
                                                        fontFamily = HafsFontFamily,
                                                        fontSize = (32 * fontSizeMultiplier).sp,
                                                        lineHeight = (32 * fontSizeMultiplier * 1.6f).sp,
                                                        color = animatedTextColor,
                                                        hifz = hifzMode || sweeping,
                                                        revealed = hifzShown,
                                                        fading = hifz.fading,
                                                        current = hifzCurrent,
                                                        highlight = currentTheme.ornament.copy(alpha = 0.18f),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                Spacer(modifier = Modifier.height(16.dp))
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            val pageLabel = if (lang == "ar") "صَفْحَة $actualPageNum" else "Page $actualPageNum"
                                            Text(
                                                text = pageLabel,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = animatedTextColor.copy(alpha = 0.5f),
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(animatedTextColor.copy(alpha = 0.1f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SEARCHABLE DIRECT SURAH SELECTION MODAL
                    val recitationAyat = remember(state.verses, state.chapter.id) {
                        state.verses
                            .filter { it.chapterId == state.chapter.id }
                            .map {
                                io.github.sammynashed.emran.recitation.RecitationAyah(
                                    chapterId = it.chapterId,
                                    ayahId = it.id,
                                    arabic = it.text,
                                )
                            }
                    }

                    if (showTajweedLegend) {
                        TajweedLegendDialog(
                            lang = lang,
                            darkTheme = currentTheme != QuranThemeMode.DAY,
                            onDismiss = { showTajweedLegend = false },
                        )
                    }

                    if (showRecitationSheet) {
                        io.github.sammynashed.emran.recitation.RecitationSheet(
                            lang = lang,
                            onDismiss = { showRecitationSheet = false },
                        )
                    }

                    if (showSurahSelector) {
                        SurahSelectorDialog(
                            currentChapter = state.chapter,
                            allChapters = allChapters,
                            onSelect = { selectedChapter ->
                                viewModel.loadSurah(selectedChapter)
                                showSurahSelector = false
                            },
                            onDismiss = { showSurahSelector = false },
                            currentTheme = currentTheme,
                            lang = lang
                        )
                    }

                    // FLOATING COLLAPSIBLE BOTTOM TOOL SHEET FOR SECONDARY READING UTILITIES
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isControlsVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        // Page-toned and nearly opaque: the Qur'an text must never show through
                        // the controls, so this is a quiet panel in the page's own colours.
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 18.dp)
                                .border(
                                    width = 1.dp,
                                    color = currentTheme.textColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(30.dp)
                                ),
                            shape = RoundedCornerShape(30.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = androidx.compose.ui.graphics.lerp(
                                    currentTheme.backgroundColor, currentTheme.textColor, 0.07f,
                                )
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            // Rarely used options stay folded away: calm by default.
                            var moreTools by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // The colour key. The colouring itself is
                                // always on; what a reader still needs is a way
                                // to find out what a colour is telling them.
                                if (moreTools) Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = { showTajweedLegend = true }) {
                                        Text(
                                            text = if (lang == "ar") "دليل الألوان" else "Colour key",
                                            color = currentTheme.textColor.copy(alpha = 0.8f),
                                            fontSize = 13.sp,
                                        )
                                    }
                                }

                                // Recording controls belong to the same overlay
                                // as the other reading tools, so entering Hifz
                                // mode adds to the page rather than replacing it.
                                if (hifzMode) {
                                    // Stacked, never side by side: three controls in one row left the
                                    // last one a sliver wide, and its label wrapped letter by letter
                                    // into a column hundreds of pixels tall.
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = {
                                                val granted = androidx.core.content.ContextCompat
                                                    .checkSelfPermission(
                                                        hifzContext,
                                                        android.Manifest.permission.RECORD_AUDIO,
                                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                if (!granted) {
                                                    askHifzMic.launch(
                                                        android.Manifest.permission.RECORD_AUDIO,
                                                    )
                                                } else {
                                                    hifz.toggle(
                                                        hifzScope,
                                                        hifzVerses.map { it.text },
                                                        hifzVerses.map { it.id },
                                                    ) { }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = currentTheme.textColor,
                                                contentColor = currentTheme.backgroundColor,
                                            ),
                                        ) {
                                            Text(
                                                text = when {
                                                    hifz.preparing && lang == "ar" -> "جارٍ التحضير…"
                                                    hifz.preparing -> "Preparing…"
                                                    hifz.recording && lang == "ar" -> "إيقاف"
                                                    hifz.recording -> "Stop"
                                                    lang == "ar" -> "ابدأ التلاوة"
                                                    else -> "Start reciting"
                                                },
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                        Text(
                                            text = "${hifz.revealed.size + hifz.unsure.size} / ${hifzWordTotal}",
                                            color = currentTheme.textColor.copy(alpha = 0.7f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(onClick = { hifz.restart() }) {
                                                Text(
                                                    text = if (lang == "ar") "من جديد" else "Start over",
                                                    color = currentTheme.textColor.copy(alpha = 0.8f),
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                )
                                            }
                                            TextButton(onClick = { hifz.revealAllKeys(hifzWordKeys) }) {
                                                Text(
                                                    text = if (lang == "ar") "إظهار الكل" else "Reveal all",
                                                    color = currentTheme.textColor.copy(alpha = 0.8f),
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                )
                                            }
                                        }
                                        }
                                    }
                                    hifz.problem?.let { msg ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = msg,
                                            color = Color(0xFFE57373),
                                            fontSize = 12.sp,
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    // Each choice is a tiny page in its own colours, with its name
                                    // under it: no guessing what a dark circle means.
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        QuranThemeMode.entries.forEach { mode ->
                                            val isSelected = currentTheme == mode
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { currentTheme = mode }
                                                    .padding(2.dp),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 38.dp, height = 46.dp)
                                                        .clip(RoundedCornerShape(7.dp))
                                                        .background(mode.backgroundColor)
                                                        .border(
                                                            width = if (isSelected) 2.dp else 1.dp,
                                                            color = if (isSelected) currentTheme.ornament else currentTheme.textColor.copy(alpha = 0.22f),
                                                            shape = RoundedCornerShape(7.dp)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("بِسۡمِ", fontFamily = MushafFontFamily, fontSize = 15.sp, color = mode.textColor)
                                                }
                                                Text(
                                                    text = when (mode) {
                                                        QuranThemeMode.DAY -> if (lang == "ar") "نهاري" else "Day"
                                                        QuranThemeMode.NIGHT -> if (lang == "ar") "ليلي" else "Night"
                                                        QuranThemeMode.AMOLED -> if (lang == "ar") "أسود" else "Black"
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = currentTheme.textColor.copy(alpha = if (isSelected) 0.95f else 0.6f),
                                                    modifier = Modifier.padding(top = 3.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                TextButton(
                                    onClick = { moreTools = !moreTools },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    Text(
                                        text = if (moreTools) {
                                            if (lang == "ar") "أقل" else "Fewer options"
                                        } else {
                                            if (lang == "ar") "خيارات أكثر" else "More options"
                                        },
                                        color = currentTheme.textColor.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                    )
                                }

                                if (moreTools) Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isContinuous by viewModel.isContinuousMode.collectAsState()
                                    Button(
                                        onClick = { viewModel.setContinuousMode(!isContinuous) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isContinuous) currentTheme.ornament else currentTheme.textColor.copy(alpha = 0.05f),
                                            contentColor = if (isContinuous) Color.White else currentTheme.textColor
                                        ),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isContinuous) Icons.Default.BookmarkAdded else Icons.Default.Bookmark,
                                            contentDescription = "Continuous Mode",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (lang == "ar") {
                                                if (isContinuous) "قراءة متصلة" else "سورة منفردة"
                                            } else {
                                                if (isContinuous) "Continuous" else "Isolated Surah"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    Button(
                                        onClick = { viewModel.setPaginatedMode(!isPaginatedMode) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPaginatedMode) currentTheme.ornament.copy(alpha = 0.8f) else currentTheme.textColor.copy(alpha = 0.05f),
                                            contentColor = if (isPaginatedMode) Color.White else currentTheme.textColor
                                        ),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewAgenda,
                                            contentDescription = "Layout Mode",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (lang == "ar") {
                                                if (isPaginatedMode) "صفحات" else "تمرير عمودي"
                                            } else {
                                                if (isPaginatedMode) "Swipe Pages" else "Scroll View"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                if (moreTools && !isPaginatedMode) {
                                    HorizontalDivider(color = currentTheme.textColor.copy(alpha = 0.1f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { isAutoScrolling = !isAutoScrolling },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    containerColor = if (isAutoScrolling) currentTheme.ornament else currentTheme.ornament.copy(alpha = 0.1f)
                                                ),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Auto Scroll",
                                                    tint = if (isAutoScrolling) Color.White else currentTheme.ornament,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (lang == "ar") "التمرير التلقائي" else "Auto-Scroll",
                                                color = currentTheme.textColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = if (lang == "ar") "السرعة" else "Speed",
                                                color = currentTheme.textColor.copy(alpha = 0.6f),
                                                fontSize = 11.sp
                                            )
                                            Slider(
                                                value = autoScrollSpeed.toFloat(),
                                                onValueChange = { autoScrollSpeed = it.toInt() },
                                                valueRange = 1f..5f,
                                                steps = 3,
                                                modifier = Modifier.width(100.dp),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = currentTheme.ornament,
                                                    activeTrackColor = currentTheme.ornament,
                                                    inactiveTrackColor = currentTheme.textColor.copy(alpha = 0.15f),
                                                    activeTickColor = currentTheme.backgroundColor.copy(alpha = 0.7f),
                                                    inactiveTickColor = currentTheme.ornament.copy(alpha = 0.7f),
                                                )
                                            )
                                            val speedLabel = when (autoScrollSpeed) {
                                                1 -> if (lang == "ar") "١" else "1"
                                                2 -> if (lang == "ar") "٢" else "2"
                                                3 -> if (lang == "ar") "٣" else "3"
                                                4 -> if (lang == "ar") "٤" else "4"
                                                5 -> if (lang == "ar") "٥" else "5"
                                                else -> autoScrollSpeed.toString()
                                            }
                                            Text(
                                                text = speedLabel,
                                                color = currentTheme.textColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterItem(chapter: QuranChapter, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val lang = LocalAppLanguage.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24).copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(AppTheme.colors.primary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chapter.id.toString(),
                        color = AppTheme.colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = chapter.transliteration,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = surahMetaLabel(chapter.type, chapter.total_verses, lang),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                text = chapter.name,
                fontFamily = SurahNameFontFamily,
                fontSize = 25.sp,
                color = AppTheme.colors.secondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun Modifier.drawScrollbar(
    state: androidx.compose.foundation.ScrollState,
    color: Color,
    width: Dp = 4.dp
): Modifier = composed {
    val targetAlpha = if (state.isScrollInProgress) 0.9f else 0.3f
    val duration = if (state.isScrollInProgress) 150 else 500
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration),
        label = "scrollbar_alpha_scroll"
    )

    drawWithContent {
        drawContent()
        
        if (state.maxValue > 0) {
            val totalHeight = size.height + state.maxValue
            val visibleFraction = size.height / totalHeight
            val scrollbarHeight = size.height * visibleFraction
            val maxScrollbarOffsetY = size.height - scrollbarHeight
            val scrollbarOffsetY = (state.value.toFloat() / state.maxValue) * maxScrollbarOffsetY
            
            val barWidthPx = width.toPx()
            
            // Draw track
            drawRoundRect(
                color = color.copy(alpha = 0.05f),
                topLeft = Offset(size.width - barWidthPx - 4.dp.toPx(), 0f),
                size = Size(barWidthPx, size.height),
                cornerRadius = CornerRadius(barWidthPx / 2, barWidthPx / 2)
            )
            
            // Draw thumb with rounded corners, matching the primary color
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(size.width - barWidthPx - 4.dp.toPx(), scrollbarOffsetY.coerceIn(0f, maxScrollbarOffsetY)),
                size = Size(barWidthPx, scrollbarHeight.coerceIn(30.dp.toPx(), size.height)),
                cornerRadius = CornerRadius(barWidthPx / 2, barWidthPx / 2)
            )
        }
    }
}

fun Modifier.drawScrollbar(
    state: LazyListState,
    color: Color,
    width: Dp = 4.dp
): Modifier = composed {
    val targetAlpha = if (state.isScrollInProgress) 0.9f else 0.3f
    val duration = if (state.isScrollInProgress) 150 else 500
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration),
        label = "scrollbar_alpha"
    )

    drawWithContent {
        drawContent()
        
        val layoutInfo = state.layoutInfo
        val totalItemsCount = layoutInfo.totalItemsCount
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        
        if (visibleItemsInfo.isNotEmpty() && totalItemsCount > visibleItemsInfo.size) {
            val firstVisibleItem = visibleItemsInfo.first()
            val lastVisibleItem = visibleItemsInfo.last()
            
            val totalScrollRange = totalItemsCount.toFloat()
            val visibleRange = (lastVisibleItem.index - firstVisibleItem.index + 1).toFloat()
            
            val firstItemOffset = -firstVisibleItem.offset.toFloat()
            val firstItemSize = firstVisibleItem.size.toFloat()
            val firstItemProgress = if (firstItemSize > 0f) firstItemOffset / firstItemSize else 0f
            
            val currentProgress = firstVisibleItem.index.toFloat() + firstItemProgress
            
            val scrollbarHeight = size.height * (visibleRange / totalScrollRange)
            val maxScrollbarOffsetY = size.height - scrollbarHeight
            val scrollbarOffsetY = (currentProgress / (totalScrollRange - visibleRange)) * maxScrollbarOffsetY
            
            val barWidthPx = width.toPx()
            
            // Draw track
            drawRoundRect(
                color = color.copy(alpha = 0.05f),
                topLeft = Offset(size.width - barWidthPx - 4.dp.toPx(), 0f),
                size = Size(barWidthPx, size.height),
                cornerRadius = CornerRadius(barWidthPx / 2, barWidthPx / 2)
            )
            
            // Draw thumb with rounded corners, matching the primary color
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(size.width - barWidthPx - 4.dp.toPx(), scrollbarOffsetY.coerceIn(0f, maxScrollbarOffsetY)),
                size = Size(barWidthPx, scrollbarHeight.coerceIn(30.dp.toPx(), size.height)),
                cornerRadius = CornerRadius(barWidthPx / 2, barWidthPx / 2)
            )
        }
    }
}


/**
 * The key to the tajweed colours.
 *
 * Every rule states plainly what it asks the reader to *do*, because a colour
 * the reader cannot name teaches nothing. The closing note is not a disclaimer
 * bolted on: Emran marks where a rule applies, and it must never be mistaken
 * for a judgement on how the reader recited it.
 */
@Composable
private fun TajweedLegendDialog(
    lang: String,
    darkTheme: Boolean,
    onDismiss: () -> Unit,
) {
    val ar = lang == "ar"
    EmranAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ar) "دليل ألوان التجويد" else "Tajweed colour key") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TajweedRule.entries.forEach { rule ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(12.dp)
                                .background(rule.colorFor(darkTheme), CircleShape),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = rule.nameFor(lang),
                                color = rule.colorFor(darkTheme),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = rule.hintFor(lang),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (ar) {
                        "تُبيّن هذه الألوان مواضع الأحكام في النص المكتوب لتساعدك على " +
                            "تعلّمها. لا يستمع التطبيق إلى تلاوتك ولا يحكم عليها."
                    } else {
                        "These colours show where each rule applies in the written " +
                            "text, to help you learn them. Emran does not listen to " +
                            "your recitation or judge it."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (ar) "تمام" else "Done") }
        },
    )
}

/**
 * How fast a revealed word comes into focus, as alpha removed per frame.
 *
 * About a fifth of a second at 60fps: long enough to read as a transition, short
 * enough that a fast reciter is never waiting for the page to catch up.
 */
private const val FADE_STEP = 0.08f

/** Long enough to take the page in before it empties. */
private const val HIFZ_INTRO_HOLD_MS = 1_000L

/** The sweep itself: brisk, so it reads as a transition and not a wait. */
private const val HIFZ_SWEEP_MS = 900
