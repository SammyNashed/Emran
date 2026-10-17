package io.github.sammynashed.emran.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.BuildConfig

import io.github.sammynashed.emran.data.testers.Tester
import io.github.sammynashed.emran.data.testers.TestersRepository
import io.github.sammynashed.emran.data.testers.TestersWall
import io.github.sammynashed.emran.ui.design.GlassButton
import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.GlassIconButton
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** One chip on the wall: a person, and (when they said something) one thing they said. */
private class Chip(val tester: Tester, val words: String?, val reviewIndex: Int?)

/**
 * "Made better by you": everyone who tested Emran, drifting past on the night sky in rows of
 * glass chips (photo, name, what they said), the way testimonial walls move on websites.
 * Data: assets/testers.json (docs/testers-format.md). In debug builds a + in the header adds
 * people on this phone, to be folded into the assets later; nothing can be edited here.
 */
@Composable
fun TestersScreen(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current
    val ar = lang == "ar"
    val context = LocalContext.current
    val sky = LocalSky.current
    var version by remember { mutableIntStateOf(0) }
    val wall by produceState<TestersWall?>(null, version) { value = TestersRepository.load(context) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Chip?>(null) }

    // Every review is a chip; a tester who said nothing still gets one, with their role.
    val chips = remember(wall, lang) {
        val w = wall ?: return@remember emptyList()
        val seen = HashMap<String, Int>()
        w.reviews.map { r ->
            val k = seen.getOrDefault(r.tester.id, 0).also { seen[r.tester.id] = it + 1 }
            Chip(r.tester, r.text(lang), k)
        } + w.testers.filter { t -> w.reviews.none { it.tester.id == t.id } }.map { Chip(it, null, null) }
    }
    val rows = remember(chips) { layoutRows(chips) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (ar) "شكرًا لكم" else "Thank you",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = Space.screen),
            actions = {
                if (BuildConfig.DEBUG) GlassIconButton(Icons.Outlined.PersonAdd, if (ar) "إضافة" else "Add", { adding = true })
            },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Crescent(Modifier.size(40.dp))
            Spacer(Modifier.height(Space.sm))
            Text(
                if (ar) "صار أفضل بفضلكم" else "Made better by you",
                color = sky.ink, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                if (ar) "آراؤكم في النسخة التجريبية ساعدتنا على بناء تجربة أهدأ وأقرب إلى القلب."
                else "Your beta feedback helps us build a calmer, more meaningful Muslim experience.",
                color = sky.inkSoft, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            wall?.let { w ->
                Spacer(Modifier.height(Space.sm))
                Text(
                    reviewCountLine(ar, w.reviews.size, w.testers.size),
                    color = sky.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, bypassNumerals = true,
                    modifier = Modifier.background(sky.ink.copy(alpha = 0.10f), CircleShape).padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.height(Space.lg))
            rows.forEachIndexed { i, row ->
                // Different speeds, so the rows slide past each other like a website's wall.
                Marquee(speedDp = listOf(26f, 20f, 32f)[i], gap = 14.dp) {
                    row.forEach { c -> TesterChip(c, lang, onTap = if (BuildConfig.DEBUG) ({ editing = c }) else null) }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (wall != null && chips.isEmpty()) {
                Text(
                    if (ar) "شكرًا لكل من جرّب عِمران." else "Thank you to everyone who tried Emran.",
                    color = sky.inkSoft, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp),
                )
            }
            Spacer(Modifier.height(Space.md))
        }
        // Pinned under the wall so it is on screen without scrolling.
        Column(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 6.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (ar) "لتظهر في صفحة الشكر بالتحديث القادم، أرسل رأيك مع صورتك (كلاهما مطلوب)."
                else "To appear on the next update's thank-you wall, send your opinion and your photo (both are required).",
                color = sky.inkSoft, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            GlassButton(
                label = if (ar) "أرسل رأيك" else "Share your feedback",
                filled = false,
                modifier = Modifier.background(sky.ink.copy(alpha = 0.10f), RoundedCornerShape(24.dp)).padding(horizontal = 8.dp),
                onClick = {
                    val msg = if (ar) "السلام عليكم، رأيي في تطبيق عِمران (وصورتي مرفقة): " else "Assalamu Alaikum Sammy, my thoughts on Emran (my photo is attached): "
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/963986157471?text=" + URLEncoder.encode(msg, "UTF-8"))))
                    } catch (e: Exception) { /* no app to open the link */ }
                },
            )
        }
    }

    if (adding) TesterDialog(ar, existing = null, onDismiss = { adding = false }, onSaved = { adding = false; version++ })
    editing?.let { c -> TesterDialog(ar, existing = c, onDismiss = { editing = null }, onSaved = { editing = null; version++ }) }
}

private fun reviewCountLine(ar: Boolean, reviews: Int, people: Int): String =
    if (ar) {
        val r = when { reviews == 1 -> "رأي واحد"; reviews == 2 -> "رأيان"; reviews in 3..10 -> "$reviews آراء"; else -> "$reviews رأيًا" }
        val p = when { people == 1 -> "شخص واحد"; people == 2 -> "شخصين"; people in 3..10 -> "$people أشخاص"; else -> "$people شخصًا" }
        "$r من $p"
    } else "$reviews ${if (reviews == 1) "review" else "reviews"} from $people ${if (people == 1) "person" else "people"}"

/**
 * Deals the chips into rows so that one person never shows up twice in the same frame:
 *  - each person lives in ONE row (so their chips are never stacked above each other, whatever
 *    the rows' different speeds do to them), and
 *  - inside that row their chips sit at least one other chip apart, counting the wrap-around of
 *    the endless loop (a chip is ~300 dp wide, a screen ~360 dp).
 * Rows hold at least ~3 chips where there are enough, so a row is never a single chip repeated.
 */
private fun layoutRows(chips: List<Chip>): List<List<Chip>> {
    if (chips.isEmpty()) return emptyList()
    val rowCount = (chips.size / 3).coerceIn(1, 3)
    val groups = chips.groupBy { it.tester.id }.values.sortedByDescending { it.size }
    val rows = List(rowCount) { mutableListOf<List<Chip>>() }
    fun total(r: List<List<Chip>>) = r.sumOf { it.size }
    for (g in groups) rows.minByOrNull { total(it) }!!.add(g)

    // A row needs twice as many chips as its busiest person has, or that person's chips touch.
    fun short(r: List<List<Chip>>) = r.isNotEmpty() && total(r) < 2 * r.maxOf { it.size }
    var guard = 20
    while (guard-- > 0) {
        val needy = rows.firstOrNull { short(it) } ?: break
        val donor = rows.filter { it !== needy && it.size >= 2 }
            .maxByOrNull { total(it) } ?: break
        val g = donor.filter { total(donor) - it.size >= 2 * (donor.filter { x -> x !== it }.maxOfOrNull { x -> x.size } ?: 0) }
            .minByOrNull { it.size } ?: break
        donor.remove(g); needy.add(g)
    }

    // Spread each row: biggest groups take every other slot (0, 2, 4, ... then 1, 3, 5, ...).
    return rows.filter { it.isNotEmpty() }.map { r ->
        val flat = r.sortedByDescending { it.size }.flatten()
        val slots = (0 until flat.size step 2) + (1 until flat.size step 2)
        val out = arrayOfNulls<Chip>(flat.size)
        flat.forEachIndexed { i, c -> out[slots[i]] = c }
        out.filterNotNull()
    }
}

/** A thin crescent with a soft glow, drawn so it takes the sky's ink. */
@Composable
private fun Crescent(modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    Canvas(modifier) {
        val r = size.minDimension * 0.34f
        val c = center
        drawCircle(Brush.radialGradient(listOf(sky.ink.copy(alpha = 0.22f), Color.Transparent), c, size.minDimension / 2f), size.minDimension / 2f, c)
        val path = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(c, r))
        }
        val cut = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.42f, c.y - r * 0.22f), r * 0.92f))
        }
        val moon = androidx.compose.ui.graphics.Path().apply { op(path, cut, androidx.compose.ui.graphics.PathOperation.Difference) }
        drawPath(moon, sky.ink)
    }
}

/** Photo when there is one, else the first letter on a soft disc. */
@Composable
private fun Avatar(tester: Tester, lang: String, size: Dp) {
    val sky = LocalSky.current
    val context = LocalContext.current
    val photo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, tester) {
        value = withContext(Dispatchers.IO) { tester.loadPhoto(context)?.asImageBitmap() }
    }
    Box(Modifier.size(size).clip(CircleShape).background(sky.ink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        val p = photo
        if (p != null) Image(p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(
            tester.name(lang).trim().take(1).uppercase(), color = sky.ink,
            fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold, bypassNumerals = true,
        )
    }
}

@Composable
private fun TesterChip(chip: Chip, lang: String, onTap: (() -> Unit)? = null) {
    val sky = LocalSky.current
    val words = chip.words
    // The words keep the direction of the language they were written in.
    val textRtl = (words ?: chip.tester.name(lang)).any { it in '؀'..'ۿ' }
    CompositionLocalProvider(LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        GlassPanel(
            modifier = if (onTap != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap) else Modifier,
            cornerRadius = 36.dp, lens = false,
        ) {
            Row(
                Modifier.widthIn(max = 320.dp).padding(start = 10.dp, end = 22.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(chip.tester, lang, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(chip.tester.name(lang), color = sky.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, bypassNumerals = true)
                    val line = words?.let { "“$it”" } ?: chip.tester.role(lang)
                    if (line != null) {
                        CompositionLocalProvider(LocalLayoutDirection provides if (textRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                            Text(
                                // The whole quote, however long: what someone said is never cut short.
                                line, color = sky.inkSoft, fontSize = 14.sp, lineHeight = 19.sp, bypassNumerals = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Debug builds only: adds a person, or (from a tapped chip) changes one: photo, name, role and
 * that one thing they said. Saved on this phone, then moved into assets/testers.json.
 */
@Composable
private fun TesterDialog(ar: Boolean, existing: Chip?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val sky = LocalSky.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = if (ar) "ar" else "en"
    var name by remember { mutableStateOf(existing?.tester?.name(lang).orEmpty()) }
    var role by remember { mutableStateOf(existing?.tester?.role(lang).orEmpty()) }
    var words by remember { mutableStateOf(existing?.words.orEmpty()) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo = it ?: photo }
    val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo) {
        value = if (photo == null) existing?.tester?.let { t -> withContext(Dispatchers.IO) { t.loadPhoto(context)?.asImageBitmap() } } else photo?.let { u ->
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }
        }
    }

    GlassDialog(
        onDismiss = onDismiss,
        title = if (existing != null) (if (ar) "تعديل" else "Edit") else (if (ar) "إضافة شخص" else "Add a person"),
        subtitle = if (ar) "يُحفظ على هذا الهاتف، ثم يُنقل إلى التطبيق." else "Saved on this phone, then moved into the app.",
        confirmLabel = if (saving) "…" else if (existing != null) (if (ar) "حفظ" else "Save") else (if (ar) "إضافة" else "Add"),
        onConfirm = {
            if (name.isNotBlank() && !saving) {
                saving = true
                scope.launch {
                    if (existing != null) {
                        TestersRepository.editPending(context, existing.tester.id, name, role, words, existing.reviewIndex, photo, lang)
                    } else {
                        TestersRepository.addPending(context, name, role, words, photo)
                    }
                    onSaved()
                }
            }
        },
        dismissLabel = if (ar) "إلغاء" else "Cancel",
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(88.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.12f))
                .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center,
        ) {
            val p = preview
            if (p != null) Image(p, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else androidx.compose.material3.Icon(Icons.Outlined.AddAPhoto, null, tint = sky.ink, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Field(if (ar) "الاسم" else "Name", name) { name = it }
        Field(if (ar) "الصفة (اختياري)" else "Role (optional)", role) { role = it }
        Field(if (ar) "ماذا قال (اختياري)" else "What they said (optional)", words, lines = 3) { words = it }
        if (existing != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (confirmDelete) (if (ar) "اضغط مرة أخرى للحذف نهائيًا" else "Tap again to delete for good") else (if (ar) "حذف هذا الشخص" else "Delete this person"),
                color = Color(0xFFE5484D), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFE5484D).copy(alpha = if (confirmDelete) 0.22f else 0.10f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!confirmDelete) confirmDelete = true
                        else scope.launch { TestersRepository.deletePending(context, existing.tester.id); onSaved() }
                    }.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    val sky = LocalSky.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = sky.inkSoft, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = lines == 1,
            minLines = lines,
            textStyle = TextStyle(color = sky.ink, fontSize = 16.sp),
            cursorBrush = SolidColor(sky.ink),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(sky.ink.copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

// ---- the moving part ----------------------------------------------------

/**
 * An endless, seamless marquee: [content] is laid out as a strip and repeated, and the strip
 * slides by one repeat's width and starts over, so there is never a gap or a jump. Holding a
 * finger on it pauses it, so a chip can be read.
 *
 * Drives itself from the frame clock and moves with a graphicsLayer, so it animates without
 * recomposing anything. Always laid out left-to-right so "slides left" means the same thing
 * in both languages; the chips choose their own text direction.
 */
@Composable
private fun Marquee(
    speedDp: Float,
    gap: Dp,
    modifier: Modifier = Modifier,
    reverse: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { gap.toPx() }
    val speedPx = with(density) { speedDp.dp.toPx() }
    var period by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }

    LaunchedEffect(period, paused, speedPx) {
        if (period <= 0f || paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (last != 0L) offset = (offset + speedPx * (t - last) / 1_000_000_000f) % period
                last = t
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clipToBounds()
                .fadeEdges()
                // Holding pauses the drift; dragging sideways scrubs the strip (swipe left to see what is
                // coming from the right); lifting the finger lets it carry on from wherever it is.
                .pointerInput(period) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        paused = true
                        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (slop != null && period > 0f) {
                            horizontalDrag(slop.id) { change ->
                                offset = (((offset - change.positionChange().x) % period) + period) % period
                                change.consume()
                            }
                        }
                        paused = false
                    }
                },
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = if (reverse) -period + offset else -offset },
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                // Three repeats, so even a short strip always covers the screen.
                Row(Modifier.onSizeChanged { period = it.width + gapPx }, horizontalArrangement = Arrangement.spacedBy(gap)) { content() }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { content() }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { content() }
            }
        }
    }
}

/** Fades the left and right edges to transparent so chips glide in and out instead of being cut off. */
private fun Modifier.fadeEdges(width: Dp = 40.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = (width.toPx() / size.width).coerceIn(0f, 0.4f)
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Transparent, f to Color.Black, (1f - f) to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
