package io.github.sammynashed.emran.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.ui.screens.HafsFontFamily
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammynashed.emran.data.assets.AdhkarItem
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.ui.tour.tourTarget
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.viewmodel.AdhkarViewModel
import io.github.sammynashed.emran.ui.localization.Text

@Composable
fun AdhkarDetailScreen(
    folderId: String,
    onNavigateBack: () -> Unit,
    viewModel: AdhkarViewModel = viewModel()
) {
    val lang = LocalAppLanguage.current
    val folders by viewModel.folders.collectAsState()
    val folder = folders.find { it.id == folderId }
    val title = if (lang == "ar") folder?.titleAr ?: "" else folder?.titleEn ?: ""
    var selectedItem by remember { mutableStateOf<AdhkarItem?>(null) }
    val context = LocalContext.current
    val bookmarks by io.github.sammynashed.emran.data.hadith.HadithBookmarks.all.collectAsState()
    LaunchedEffect(Unit) { io.github.sammynashed.emran.data.hadith.HadithBookmarks.ensureLoaded(context) }
    val tourManager = io.github.sammynashed.emran.ui.tour.LocalTourManager.current

    // Masbaha mode walks this folder's adhkar in order, counting each one up to
    // its narrated repetition count before moving on. Entered by tapping any
    // dhikr, which also decides where the walk starts. Null means the list is
    // showing. Reset whenever the folder changes so switching folders never
    // resumes someone else's position.
    var masbahaStartIndex by remember(folderId) { mutableStateOf<Int?>(null) }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .tourTarget("sub_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                val startIndex = masbahaStartIndex
                if (folder != null && startIndex != null) {
                    AdhkarMasbaha(
                        items = folder.items,
                        startIndex = startIndex,
                        lang = lang,
                        onExit = { masbahaStartIndex = null }
                    )
                } else if (folder != null) {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .drawScrollbar(listState, color = AppTheme.colors.primary),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(folder.items) { index, item ->
                            val itemModifier = if (index == 0) Modifier.tourTarget("adhkar_first_item") else Modifier
                            val itemKey = item.id ?: index.toString()
                            val bmKey = io.github.sammynashed.emran.data.hadith.Bookmark.dhikrKey(folder.id, itemKey)
                            AdhkarListItem(
                                item = item,
                                lang = lang,
                                modifier = itemModifier,
                                bookmarked = bookmarks.any { it.key == bmKey },
                                onToggleBookmark = {
                                    io.github.sammynashed.emran.data.hadith.HadithBookmarks.toggleDhikr(context, folder.id, itemKey)
                                },
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Adhkar", buildString {
                                        append(if (lang == "ar") item.textAr else item.textEn)
                                        item.sourceFor(lang)?.takeIf { it.isNotBlank() }?.let { append("\n\n— ").append(it) }
                                    }))
                                    android.widget.Toast.makeText(
                                        context,
                                        if (lang == "ar") "تم نسخ المحتوى إلى الحافظة" else "Content copied to clipboard",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onShare = {
                                    io.github.sammynashed.emran.ui.share.ShareCard.share(
                                        context,
                                        io.github.sammynashed.emran.ui.share.ShareCard.Content(
                                            arabic = item.textAr,
                                            english = item.textEn,
                                            source = item.sourceFor(lang)?.takeIf { it.isNotBlank() }
                                                ?: (if (lang == "ar") "عِمران" else "Emran")
                                        ),
                                        lang
                                    )
                                },
                                // A tap starts counting this folder from here.
                                onClick = { masbahaStartIndex = index },
                                // A long press opens the reader, which is where
                                // copying lives.
                                onLongClick = {
                                    selectedItem = item
                                    if (index == 0 && tourManager != null && tourManager.isActive.value) {
                                        val step = tourManager.steps.getOrNull(tourManager.currentStepIndex.value)
                                        if (step != null && step.id == "adhkar_first_item") {
                                            tourManager.nextStep()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Fullscreen Popup
            AnimatedVisibility(
                visible = selectedItem != null,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                if (selectedItem != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.9f))
                            .clickable { selectedItem = null }
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val item = selectedItem!!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(AppTheme.colors.surface)
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState())
                                .clickable(enabled = false) {}, // consume clicks
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // One language only, matching the app language.
                            if (lang == "ar") {
                                Text(
                                    text = item.textAr,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontFamily = HafsFontFamily,
                                    lineHeight = 40.sp,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = item.textEn,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    lineHeight = 27.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            run {
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(AppTheme.colors.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (lang == "ar") "تُكرَّر ${item.reps}×" else "Repeat ${item.reps}x",
                                        color = AppTheme.colors.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Attribution. A supplication the user cannot trace is
                            // one they cannot verify, which matters more here than
                            // it would for ordinary app copy.
                            item.virtueFor(lang)?.takeIf { it.isNotBlank() }?.let { virtue ->
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = virtue,
                                    color = AppTheme.colors.grayMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            item.sourceFor(lang)?.takeIf { it.isNotBlank() }?.let { source ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = source,
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    bypassNumerals = lang == "ar"
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    // Copy the Arabic together with its attribution, so
                                    // what gets shared stays traceable to its source.
                                    val textToCopy = buildString {
                                        append(if (lang == "ar") item.textAr else item.textEn)
                                        item.sourceFor(lang)?.takeIf { it.isNotBlank() }?.let {
                                            append("\n\n— ").append(it)
                                        }
                                    }
                                    val clip = ClipData.newPlainText("Adhkar", textToCopy)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (lang == "ar") "تم نسخ المحتوى إلى الحافظة" else "Content copied to clipboard",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    selectedItem = null
                                    if (tourManager != null && tourManager.isActive.value) {
                                        val step = tourManager.steps.getOrNull(tourManager.currentStepIndex.value)
                                        if (step != null && step.id == "adhkar_copy_button") {
                                            tourManager.nextStep()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                                modifier = Modifier.tourTarget("adhkar_copy_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (lang == "ar") "نسخ" else "Copy")
                            }
                            // A picture card: the dhikr, where it is from, and how to get Emran.
                            Button(
                                onClick = {
                                    io.github.sammynashed.emran.ui.share.ShareCard.share(
                                        context,
                                        io.github.sammynashed.emran.ui.share.ShareCard.Content(
                                            arabic = item.textAr,
                                            english = item.textEn,
                                            source = item.sourceFor(lang)?.takeIf { it.isNotBlank() }
                                                ?: (if (lang == "ar") "عِمران" else "Emran")
                                        ),
                                        lang
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.secondary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (lang == "ar") "مشاركة" else "Share")
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Counting mode for a single folder's adhkar.
 *
 * Taps land on one large target rather than a small button: the point is to be
 * usable without looking at the screen while reciting. Each dhikr is counted up
 * to its own narrated [AdhkarItem.reps] -- 33x after prayer, 7x, or 1x where no
 * repetition is narrated -- and completing one advances to the next in this
 * folder only.
 */
@Composable
fun AdhkarMasbaha(
    items: List<AdhkarItem>,
    startIndex: Int,
    lang: String,
    onExit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var index by remember(items, startIndex) { mutableStateOf(startIndex.coerceIn(0, items.size)) }
    var count by remember(items, startIndex) { mutableStateOf(0) }

    // Same click sound as the standalone Masbaha, created once and released with
    // the screen so repeated taps do not stack MediaPlayer instances.
    val clickPlayer = remember(context) {
        try {
            android.media.MediaPlayer.create(context, io.github.sammynashed.emran.R.raw.masbaha)
        } catch (e: Exception) {
            null
        }
    }
    DisposableEffect(clickPlayer) {
        onDispose {
            try {
                clickPlayer?.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
    val playClickSound = {
        try {
            clickPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    player.seekTo(0)
                }
                player.start()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    val finished = index >= items.size
    val item = items.getOrNull(index)
    val target = (item?.reps ?: 1).coerceAtLeast(1)

    val progress by animateFloatAsState(
        targetValue = if (target > 0) count.toFloat() / target else 0f,
        animationSpec = tween(220),
        label = "masbahaProgress"
    )

    // The dial is sized from the space actually available rather than fixed at
    // 200dp: on a short screen -- or in landscape, where height roughly halves --
    // a fixed dial pushed the Undo/Skip row off the bottom entirely. The controls
    // are laid out first in priority and the dial takes what is left, so the
    // buttons can never be the thing that falls off.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val reservedForChrome = 210.dp   // position line, hint, buttons, padding
        val dialSize = (maxHeight - reservedForChrome).coerceIn(120.dp, 240.dp)
        val tight = maxHeight < 520.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (finished) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (lang == "ar") "تم إتمام الأذكار" else "Adhkar complete",
                color = AppTheme.colors.primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (lang == "ar") "تقبّل الله منك" else "May Allah accept it from you",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { index = 0; count = 0 },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary)
                ) {
                    Text(if (lang == "ar") "إعادة" else "Restart")
                }
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    Text(if (lang == "ar") "إنهاء" else "Done")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            return@Column
        }

        // Position within the folder
        Text(
            text = if (lang == "ar") "${index + 1} من ${items.size}" else "${index + 1} of ${items.size}",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // The dhikr being recited, in the app language only.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (item != null) {
                    if (lang == "ar") {
                        Text(
                            text = item.textAr,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontFamily = HafsFontFamily,
                            lineHeight = 38.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = item.textEn,
                            color = Color.White,
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (tight) 8.dp else 20.dp))

        // Tap target. Large, centred, and the whole thing is clickable so it can
        // be hit without looking.
        Box(
            modifier = Modifier
                .size(dialSize)
                .clip(RoundedCornerShape(999.dp))
                .background(AppTheme.colors.primary.copy(alpha = 0.10f))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    playClickSound()
                    if (count + 1 >= target) {
                        // Finished this dhikr: move to the next one in the folder.
                        index += 1
                        count = 0
                    } else {
                        count += 1
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(dialSize),
                color = AppTheme.colors.primary,
                trackColor = Color.White.copy(alpha = 0.08f),
                strokeWidth = 6.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$count",
                    color = Color.White,
                    fontSize = if (tight) 40.sp else 56.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (lang == "ar") "من $target" else "of $target",
                    color = AppTheme.colors.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(if (tight) 6.dp else 16.dp))

        Text(
            text = if (lang == "ar") "اضغط للعد" else "Tap to count",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 12.sp
        )

    }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AdhkarListItem(
    item: AdhkarItem,
    lang: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    bookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onCopy: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassCard(RoundedCornerShape(16.dp), AppTheme.colors.primary.copy(alpha = 0.5f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)
        ) {
            // Top row: the repetition count (always shown, "1x" included, so every dhikr
            // states its count) and the same bookmark / copy / share buttons the hadith
            // cards have.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AppTheme.colors.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${item.reps}x",
                        color = AppTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
                io.github.sammynashed.emran.ui.components.ItemActions(
                    bookmarked = bookmarked,
                    onToggleBookmark = onToggleBookmark,
                    onCopy = onCopy,
                    onShare = onShare
                )
            }

            Column(modifier = Modifier.padding(end = 8.dp)) {
                // One language only: Arabic script in Arabic mode, English in English
                // mode. No side-by-side translation.
                if (lang == "ar") {
                    Text(
                        text = item.textAr,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = HafsFontFamily,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Right,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = item.textEn,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item.sourceFor(lang)?.takeIf { it.isNotBlank() }?.let { source ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = source,
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        bypassNumerals = lang == "ar"
                    )
                }
            }
        }
    }
}
