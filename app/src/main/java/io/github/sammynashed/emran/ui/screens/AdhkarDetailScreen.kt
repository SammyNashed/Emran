package io.github.sammynashed.emran.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import io.github.sammynashed.emran.ui.design.GlassBackButton
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammynashed.emran.data.assets.AdhkarItem
import io.github.sammynashed.emran.data.assets.MyAdhkar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import io.github.sammynashed.emran.ui.design.GlassIconButton
import io.github.sammynashed.emran.data.hadith.Bookmark
import io.github.sammynashed.emran.data.hadith.HadithBookmarks
import io.github.sammynashed.emran.ui.components.ItemActions
import io.github.sammynashed.emran.ui.design.GlassButton
import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Radius
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.share.ShareCard
import io.github.sammynashed.emran.ui.viewmodel.AdhkarViewModel

/** The dhikr and its source, as copied: what gets shared stays traceable. */
private fun AdhkarItem.copyText(lang: String) = buildString {
    append(if (lang == "ar") textAr else textEn)
    sourceFor(lang)?.takeIf { it.isNotBlank() }?.let { append("\n\n— ").append(it) }
}

private fun copyDhikr(context: Context, item: AdhkarItem, lang: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Adhkar", item.copyText(lang)))
    android.widget.Toast.makeText(context, if (lang == "ar") "تم النسخ" else "Copied", android.widget.Toast.LENGTH_SHORT).show()
}

private fun shareDhikr(context: Context, item: AdhkarItem, lang: String) {
    ShareCard.share(
        context,
        ShareCard.Content(
            arabic = item.textAr,
            english = item.textEn.takeIf { it != item.textAr },
            source = item.sourceFor(lang)?.takeIf { it.isNotBlank() } ?: (if (lang == "ar") "عِمران" else "Emran"),
        ),
        lang,
    )
}

@Composable
fun AdhkarDetailScreen(
    folderId: String,
    onNavigateBack: () -> Unit,
    viewModel: AdhkarViewModel = viewModel(),
) {
    val lang = LocalAppLanguage.current
    val sky = LocalSky.current
    val folders by viewModel.folders.collectAsState()
    val folder = folders.find { it.id == folderId }
    val title = if (lang == "ar") folder?.titleAr ?: "" else folder?.titleEn ?: ""
    var selectedItem by remember { mutableStateOf<AdhkarItem?>(null) }
    val context = LocalContext.current
    val bookmarks by HadithBookmarks.all.collectAsState()
    LaunchedEffect(Unit) { HadithBookmarks.ensureLoaded(context) }

    // Counting mode walks this folder's adhkar in order, each up to its narrated repetition
    // count, then on to the next. Entered by tapping a dhikr, which decides where the walk
    // starts. Null means the list is showing; reset when the folder changes.
    var masbahaStartIndex by remember(folderId) { mutableStateOf<Int?>(null) }
    // The phone's back button does what the on-screen one does: leave counting for the list, not the whole folder.
    androidx.activity.compose.BackHandler(enabled = masbahaStartIndex != null) { masbahaStartIndex = null }

    // The user's own folder can be written in: add, and (from the long-press view) edit or delete.
    val mine = folderId == MyAdhkar.FOLDER_ID
    var editing by remember { mutableStateOf<AdhkarItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AdhkarItem?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.screen)) {
        if (masbahaStartIndex == null) ScreenHeader(
            title = title,
            subtitle = folder?.let { if (mine && it.items.isEmpty()) null else adhkarCountLabel(it.items.size, lang) },
            onBack = { if (masbahaStartIndex != null) masbahaStartIndex = null else onNavigateBack() },
            actions = {
                if (mine && masbahaStartIndex == null) {
                    GlassIconButton(Icons.Outlined.Add, if (lang == "ar") "ذكر جديد" else "New dhikr", { adding = true })
                }
            },
        )

        val startIndex = masbahaStartIndex
        if (folder != null && startIndex != null) {
            AdhkarMasbaha(items = folder.items, startIndex = startIndex, lang = lang, title = title, onExit = { masbahaStartIndex = null })
        } else if (folder != null && mine && folder.items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(bottom = 124.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (lang == "ar") "اكتب أذكارك وأدعيتك هنا، وعُدّها واحفظها وشاركها مثل غيرها من الأذكار."
                    else "Write your own adhkar and du'as here, then count, save and share them like any other.",
                    color = sky.inkSoft, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                GlassButton(if (lang == "ar") "أضف ذكرًا" else "Add a dhikr", onClick = { adding = true }, filled = true)
            }
        } else if (folder != null) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().drawScrollbar(listState, color = sky.ink.copy(alpha = 0.5f)),
                contentPadding = PaddingValues(bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                itemsIndexed(folder.items) { index, item ->
                    val itemKey = item.id ?: index.toString()
                    val bmKey = Bookmark.dhikrKey(folder.id, itemKey)
                    AdhkarListItem(
                        item = item,
                        lang = lang,
                        bookmarked = bookmarks.any { it.key == bmKey },
                        onToggleBookmark = { HadithBookmarks.toggleDhikr(context, folder.id, itemKey) },
                        onCopy = { copyDhikr(context, item, lang) },
                        onShare = { shareDhikr(context, item, lang) },
                        // A tap starts counting this folder from here.
                        onClick = { masbahaStartIndex = index },
                        // A long press opens the whole dhikr with its virtue and source.
                        onLongClick = { selectedItem = item },
                    )
                }
            }
        }
    }

    selectedItem?.let { item ->
        DhikrReader(
            item, lang, onDismiss = { selectedItem = null },
            onEdit = if (mine) ({ selectedItem = null; editing = item }) else null,
            onDelete = if (mine) ({ selectedItem = null; deleting = item }) else null,
        )
    }
    if (adding || editing != null) {
        MyDhikrEditor(item = editing, lang = lang, onDismiss = { adding = false; editing = null })
    }
    deleting?.let { item ->
        GlassDialog(
            onDismiss = { deleting = null },
            title = if (lang == "ar") "حذف هذا الذكر؟" else "Delete this dhikr?",
            confirmLabel = if (lang == "ar") "احذف" else "Delete",
            onConfirm = { item.id?.let { MyAdhkar.delete(context, it) }; deleting = null },
            dismissLabel = if (lang == "ar") "إلغاء" else "Cancel",
        ) {
            Text(
                text = if (lang == "ar") item.textAr else item.textEn,
                color = sky.inkSoft, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Writing one of your own adhkar: the words (any language), an optional translation, how many
 * times, and an optional source or note. Saved straight into "أذكاري".
 */
@Composable
private fun MyDhikrEditor(item: AdhkarItem?, lang: String, onDismiss: () -> Unit) {
    val sky = LocalSky.current
    val context = LocalContext.current
    val ar = lang == "ar"
    var text by remember { mutableStateOf(item?.textAr.orEmpty()) }
    var translation by remember { mutableStateOf(item?.let(MyAdhkar::translationOf).orEmpty()) }
    var reps by remember { mutableStateOf((item?.reps ?: 1).toString()) }
    var source by remember { mutableStateOf(item?.source.orEmpty()) }
    val colours = OutlinedTextFieldDefaults.colors(
        focusedTextColor = sky.ink, unfocusedTextColor = sky.ink,
        focusedBorderColor = sky.ink, unfocusedBorderColor = sky.ink.copy(alpha = 0.25f),
        focusedLabelColor = sky.inkSoft, unfocusedLabelColor = sky.inkSoft, cursorColor = sky.ink,
    )
    GlassDialog(
        onDismiss = onDismiss,
        title = if (item == null) (if (ar) "ذكر جديد" else "New dhikr") else (if (ar) "تعديل الذكر" else "Edit dhikr"),
        confirmLabel = if (ar) "حفظ" else "Save",
        onConfirm = {
            if (text.isNotBlank()) {
                MyAdhkar.save(context, item?.id, text, translation, reps.toIntOrNull() ?: 1, source)
                onDismiss()
            }
        },
        dismissLabel = if (ar) "إلغاء" else "Cancel",
    ) {
        OutlinedTextField(
            text, { text = it }, colors = colours, minLines = 3, maxLines = 8,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, lineHeight = 30.sp),
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (ar) "نص الذكر" else "The dhikr") },
        )
        OutlinedTextField(
            translation, { translation = it }, colors = colours, maxLines = 4, modifier = Modifier.fillMaxWidth(),
            label = { Text(if (ar) "الترجمة (اختياري)" else "Translation (optional)") },
        )
        OutlinedTextField(
            reps, { v -> reps = v.filter(Char::isDigit).take(5) }, singleLine = true, colors = colours,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            label = { Text(if (ar) "عدد المرات" else "How many times") },
        )
        OutlinedTextField(
            source, { source = it }, colors = colours, maxLines = 2, modifier = Modifier.fillMaxWidth(),
            label = { Text(if (ar) "المصدر أو ملاحظة (اختياري)" else "Source or note (optional)") },
        )
    }
}

/** One dhikr in full: the words, how many times, its virtue and where it is from. */
@Composable
private fun DhikrReader(
    item: AdhkarItem,
    lang: String,
    onDismiss: () -> Unit,
    /** Only for the user's own adhkar. */
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val sky = LocalSky.current
    val context = LocalContext.current
    val ar = lang == "ar"
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "تُكرَّر ${item.reps} ${if (item.reps == 1) "مرة" else "مرات"}" else "Repeat ${item.reps}×",
        dismissLabel = if (ar) "إغلاق" else "Close",
    ) {
        Text(
            text = if (ar) item.textAr else item.textEn,
            color = sky.ink,
            fontSize = if (ar) 23.sp else 17.sp,
            lineHeight = if (ar) 40.sp else 27.sp,
            fontFamily = if (ar) HafsFontFamily else null,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // A supplication the user cannot trace is one they cannot verify.
        item.virtueFor(lang)?.takeIf { it.isNotBlank() }?.let { virtue ->
            Spacer(Modifier.height(12.dp))
            Text(virtue, color = sky.inkSoft, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        item.sourceFor(lang)?.takeIf { it.isNotBlank() }?.let { source ->
            Spacer(Modifier.height(8.dp))
            Text(source, color = sky.inkSoft, fontSize = 12.sp, textAlign = TextAlign.Center, bypassNumerals = ar, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            GlassButton(if (ar) "نسخ" else "Copy", onClick = { copyDhikr(context, item, lang); onDismiss() }, filled = false)
            GlassButton(if (ar) "مشاركة" else "Share", onClick = { shareDhikr(context, item, lang) }, filled = true)
        }
        if (onEdit != null && onDelete != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                GlassButton(if (ar) "تعديل" else "Edit", onClick = onEdit, filled = false)
                GlassButton(if (ar) "حذف" else "Delete", onClick = onDelete, filled = false)
            }
        }
    }
}

/**
 * Counting mode for a single folder's adhkar.
 *
 * Taps land on one large target rather than a small button: the point is to be usable
 * without looking at the screen while reciting. Each dhikr is counted up to its own narrated
 * [AdhkarItem.reps] (33 after prayer, 7, or 1 where no repetition is narrated), and finishing
 * one moves to the next in this folder only.
 */
@Composable
fun AdhkarMasbaha(
    items: List<AdhkarItem>,
    startIndex: Int,
    lang: String,
    title: String,
    onExit: () -> Unit,
) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val ar = lang == "ar"
    var index by remember(items, startIndex) { mutableIntStateOf(startIndex.coerceIn(0, items.size)) }
    var count by remember(items, startIndex) { mutableIntStateOf(0) }

    // Same click sound as the standalone Masbaha, created once and released with the screen
    // so repeated taps do not stack MediaPlayer instances.
    val clickPlayer = remember(context) {
        try { android.media.MediaPlayer.create(context, io.github.sammynashed.emran.R.raw.masbaha) } catch (e: Exception) { null }
    }
    DisposableEffect(clickPlayer) { onDispose { try { clickPlayer?.release() } catch (_: Exception) { } } }
    val playClick = {
        try {
            clickPlayer?.let { p -> if (p.isPlaying) { p.pause(); p.seekTo(0) }; p.start() }
        } catch (_: Exception) { }
    }

    val finished = index >= items.size
    val item = items.getOrNull(index)
    val target = (item?.reps ?: 1).coerceAtLeast(1)
    val progress by animateFloatAsState(count.toFloat() / target, tween(220), label = "masbahaProgress")

    if (finished) {
        Column(
            Modifier.fillMaxSize().padding(bottom = 124.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (ar) "تمّت الأذكار" else "Adhkar complete", color = sky.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(if (ar) "تقبّل الله منك" else "May Allah accept it from you", color = sky.inkSoft, fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(if (ar) "من البداية" else "Start again", onClick = { index = 0; count = 0 }, filled = false)
                GlassButton(if (ar) "تم" else "Done", onClick = onExit, filled = true)
            }
        }
        return
    }

    val source = remember { MutableInteractionSource() }
    val tap = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        playClick()
        if (count + 1 >= target) { index += 1; count = 0 } else count += 1
    }
    val pressed by source.collectIsPressedAsState()
    val squeeze by animateFloatAsState(if (pressed) 0.985f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "card")

    // The card is the counter: it hugs the words, shows the count inside, fills from the bottom as you recite,
    // and the whole card is the tap target.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = if (wide) 8.dp else 104.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                GlassBackButton(onExit)
                Spacer(Modifier.width(Space.md))
                Text(
                    title, color = sky.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(
                    if (ar) "${index + 1} من ${items.size}" else "${index + 1} of ${items.size}",
                    color = sky.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val room = this
                val numeralH = if (wide) 0.dp else 92.dp
                val numeralW = if (wide) 130.dp else 0.dp
                val text = if (item == null) "" else if (ar) item.textAr else item.textEn
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val family = if (ar) HafsFontFamily else null
                val minSp = if (ar) 20 else 16
                // The largest type that fits inside the card without scrolling; the card then wraps the result.
                val size = remember(text, room.maxWidth, room.maxHeight, ar, wide) {
                    val w = with(density) { (room.maxWidth - 32.dp - numeralW - 16.dp).roundToPx() }
                    val h = with(density) { (room.maxHeight - 32.dp - numeralH).roundToPx() }
                    var sp = if (ar) 38 else 28
                    var best = minSp
                    while (sp >= minSp) {
                        val r = measurer.measure(
                            text,
                            androidx.compose.ui.text.TextStyle(fontSize = sp.sp, lineHeight = (sp * (if (ar) 1.75f else 1.5f)).sp, fontFamily = family, textAlign = TextAlign.Center),
                            constraints = androidx.compose.ui.unit.Constraints(maxWidth = w),
                        )
                        if (r.size.height <= h) { best = sp; break }
                        sp -= 1
                    }
                    best
                }
                GlassPanel(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = room.maxHeight)
                        .graphicsLayer { scaleX = squeeze; scaleY = squeeze }
                        .clickable(interactionSource = source, indication = null) { tap() },
                    cornerRadius = Radius.panel,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Radius.panel))
                            .drawBehind {
                                // The water level: the card fills from the bottom as the count climbs.
                                drawRect(sky.ink.copy(alpha = 0.10f), topLeft = Offset(0f, this.size.height * (1f - progress)), size = Size(this.size.width, this.size.height * progress))
                            },
                    ) {
                        val words = @Composable { m: Modifier ->
                            Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = text, color = sky.ink, fontSize = size.sp,
                                    lineHeight = (size * (if (ar) 1.75f else 1.5f)).sp,
                                    fontFamily = family, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        val numeral = @Composable { m: Modifier ->
                            Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$count", color = sky.ink, fontSize = 56.sp, fontWeight = FontWeight.Light, style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                                Text(if (ar) "من $target" else "of $target", color = sky.inkSoft, fontSize = 14.sp)
                            }
                        }
                        if (wide) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                words(Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(16.dp))
                                numeral(Modifier.width(numeralW - 16.dp))
                            }
                        } else {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                words(Modifier.weight(1f, fill = false))
                                Spacer(Modifier.height(8.dp))
                                numeral(Modifier)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    onShare: () -> Unit = {},
) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val ar = lang == "ar"
    // A scrolling list: tint and rim, no lens.
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            ),
        cornerRadius = Radius.row,
        lens = false,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 16.dp)) {
            // Every dhikr states its count ("1×" included), next to the same bookmark / copy /
            // share buttons the hadith cards have.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.reps}×",
                    color = sky.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(sky.ink.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                ItemActions(bookmarked = bookmarked, onToggleBookmark = onToggleBookmark, onCopy = onCopy, onShare = onShare)
            }
            Column(Modifier.padding(end = 12.dp)) {
                // One language only: Arabic script in Arabic, English in English.
                Text(
                    text = if (ar) item.textAr else item.textEn,
                    color = sky.ink,
                    fontSize = if (ar) 20.sp else 16.sp,
                    lineHeight = if (ar) 34.sp else 24.sp,
                    fontFamily = if (ar) HafsFontFamily else null,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                item.sourceFor(lang)?.takeIf { it.isNotBlank() }?.let { source ->
                    Spacer(Modifier.height(10.dp))
                    Text(source, color = sky.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, bypassNumerals = ar)
                }
            }
        }
    }
}
