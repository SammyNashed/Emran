package io.github.sammynashed.emran.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammynashed.emran.ui.design.GlassBackButton
import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Localizations
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.InterFontFamily
import io.github.sammynashed.emran.ui.viewmodel.DhikrViewModel
import java.time.DayOfWeek
import java.time.LocalDate

private val PRESETS = listOf(
    Triple("سُبْحَانَ ٱللَّٰهِ", "Subhanallah", Color(0xFF64B5F6)),
    Triple("ٱلْحَمْدُ لِلَّٰهِ", "Alhamdulillah", Color(0xFF81C784)),
    Triple("لَا إِلَٰهَ إِلَّا ٱللَّٰهُ", "La ilaha illallah", Color(0xFFBA68C8)),
    Triple("ٱللَّٰهُ أَكْبَرُ", "Allahu Akbar", Color(0xFFFFB74D)),
)
private const val PRESET_TARGET = 33
private const val FRIDAY_TARGET = 1000
private val FRIDAY_GREEN = Color(0xFF66BB6A)
private val PEARL = Color(0xFFE9DCCB)
private val COUNTER_COLOURS = listOf(
    "#FFE57373", "#FFFFB74D", "#FFFFF176", "#FF81C784", "#FF64B5F6", "#FF7986CB", "#FFBA68C8",
)

private fun parseColour(hex: String, fallback: Color) =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

/** What is being counted right now. */
private class Current(val arabic: String, val latin: String, val colour: Color, val count: Int, val target: Int)

/**
 * Tasbeeh: a string of prayer beads you slide one bead at a time, by tapping anywhere on the
 * upper half. Your tasbeehs, each with its own colour and count, are listed underneath. On
 * Fridays the challenge sits on top. Everything the old masbaha did is still here.
 */
@Composable
fun DhikrScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = context as? androidx.lifecycle.ViewModelStoreOwner
    val vm: DhikrViewModel = if (owner != null) viewModel(viewModelStoreOwner = owner) else viewModel()
    val lang = LocalAppLanguage.current
    val ar = lang == "ar"
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember(context) { context.getSharedPreferences("emran_settings", android.content.Context.MODE_PRIVATE) }
    var muted by remember { mutableStateOf(prefs.getBoolean("dhikr_is_muted", false)) }

    val custom by vm.customTasbeehs.collectAsState()
    var selected by vm.selectedIndex
    var phrase by vm.activePresetPhraseIndex
    val presetCounts = vm.presetCounts
    var fridayCount by vm.fridayCount
    val today = remember { LocalDate.now() }
    val isFriday = today.dayOfWeek == DayOfWeek.FRIDAY || io.github.sammynashed.emran.ui.design.SkyDebug.forceFriday
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(today) {
        val last = prefs.getString("friday_tasbeeh_last_date", "")
        if (isFriday && last != today.toString()) {
            fridayCount = 0
            prefs.edit().putString("friday_tasbeeh_last_date", today.toString()).apply()
        }
    }
    LaunchedEffect(isFriday) { if (!isFriday && selected == 999) selected = 0 }
    // A counter you just made becomes the one you are counting; one that vanished falls back.
    var seen by remember { mutableStateOf(custom.size) }
    LaunchedEffect(custom.size) {
        if (custom.size > seen) selected = custom.size
        else if (selected != 999 && selected > custom.size) selected = 0
        seen = custom.size
    }

    val player = remember(context) {
        runCatching { android.media.MediaPlayer.create(context, io.github.sammynashed.emran.R.raw.masbaha) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() } } }
    fun click() {
        if (muted) return
        runCatching { player?.let { if (it.isPlaying) { it.pause(); it.seekTo(0) }; it.start() } }
    }

    val customItem = if (selected in 1..custom.size) custom[selected - 1] else null
    val now: Current = when {
        selected == 999 -> Current("ٱللَّٰهُمَّ صَلِّ عَلَىٰ سَيِّدِنَا مُحَمَّدٍ", "Friday challenge", FRIDAY_GREEN, fridayCount, FRIDAY_TARGET)
        customItem != null -> Current(
            customItem.textArabic, customItem.textTransliteration,
            parseColour(customItem.colorHex, sky.ink), customItem.count, customItem.target.coerceAtLeast(1),
        )
        else -> {
            val p = PRESETS[phrase.coerceIn(0, 3)]
            Current(p.first, p.second, p.third, presetCounts[phrase.coerceIn(0, 3)], PRESET_TARGET)
        }
    }

    fun tap() {
        val before = now.count
        when {
            selected == 999 -> fridayCount += 1
            customItem != null -> vm.incrementCount(customItem)
            else -> presetCounts[phrase.coerceIn(0, 3)] = before + 1
        }
        click()
        val after = before + 1
        // A light tick each bead; a firmer one at the goal and at every full round of 33.
        val firm = after == now.target || after % 33 == 0
        haptic.performHapticFeedback(if (firm) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
    }

    fun reset() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when {
            selected == 999 -> fridayCount = 0
            customItem != null -> vm.resetCount(customItem)
            else -> presetCounts[phrase.coerceIn(0, 3)] = 0
        }
    }

    // The order "next" walks: the four classics, then your own.
    val order = (0..3).map { 0 to it } + custom.indices.map { (it + 1) to 0 }
    val here = order.indexOfFirst { (idx, ph) -> if (idx == 0) selected == 0 && ph == phrase else selected == idx }
    val next = order[(if (here < 0) 0 else here + 1) % order.size]
    val nextName = if (next.first == 0) PRESETS[next.second].let { if (ar) it.first else it.second }
    else custom[next.first - 1].let { it.textArabic.ifBlank { it.textTransliteration } }
    fun goNext() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        selected = next.first
        if (next.first == 0) phrase = next.second
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen)
            .padding(bottom = 112.dp),
    ) {
        // Top: back, and on Fridays the challenge.
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassBackButton(onBack, Modifier)
            if (isFriday) {
                FridayBanner(ar, fridayCount, selected == 999, Modifier.weight(1f)) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selected = 999
                }
            } else {
                Text(
                    if (ar) "التسبيح" else "Tasbeeh", color = sky.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // The tapping area: the dhikr, the count and the beads. Only THIS squeezes when pressed; the buttons
        // under it are separate, so pressing the circle never makes them pulse.
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val settle by animateFloatAsState(
            if (pressed) 0.985f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "tap",
        )
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = settle; scaleY = settle }
                .clickable(interactionSource = source, indication = null) { tap() }
                ,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                now.arabic, color = sky.ink, fontSize = 30.sp, fontFamily = HafsFontFamily,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 0.dp, bottom = 2.dp),
            )
            // One size, one font: "11 / 33" reads as a single number.
            Text(
                buildAnnotatedString {
                    append("\u2066${now.count}") // LRI: numbers read left to right, even in Arabic
                    withStyle(SpanStyle(color = sky.inkSoft)) { append(" / ${now.target}") }
                    append("\u2069")
                },
                color = sky.ink, fontSize = 32.sp, fontWeight = FontWeight.Light, fontFamily = InterFontFamily,
                style = TextStyle(fontFeatureSettings = "tnum"), bypassNumerals = true,
            )
            Misbaha(now.count, now.colour, Modifier.fillMaxWidth().height(56.dp).padding(top = 2.dp))
        }
        Spacer(Modifier.height(2.dp))
        // Sound, the big tap circle, reset.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SquareGlass(
                if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                if (ar) (if (muted) "الصوت مكتوم" else "الصوت مفعّل") else (if (muted) "Sound off" else "Sound on"),
            ) {
                muted = !muted
                prefs.edit().putBoolean("dhikr_is_muted", muted).apply()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            val circleSource = remember { MutableInteractionSource() }
            val circlePressed by circleSource.collectIsPressedAsState()
            val circleScale by animateFloatAsState(
                if (circlePressed) 0.94f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "circle",
            )
            GlassPanel(
                Modifier.size(92.dp).graphicsLayer { scaleX = circleScale; scaleY = circleScale }
                    .clickable(interactionSource = circleSource, indication = null) { tap() },
                cornerRadius = 46.dp, tint = if (circlePressed) 1.9f else 1f,
            ) {
                Column(
                    Modifier.fillMaxSize().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.TouchApp, null, tint = sky.ink, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (now.count >= now.target) (if (ar) "أتممتَ العدد ✓" else "Goal reached ✓")
                        else (if (ar) "اضغط للتسبيح" else "Tap to count"),
                        color = sky.inkSoft, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp,
                    )
                }
            }
            SquareGlass(Icons.Default.Refresh, if (ar) "إعادة الضبط" else "Reset") { reset() }
        }

        Spacer(Modifier.height(8.dp))
        // The next dhikr.
        GlassPanel(
            Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { goNext() }
                ,
            cornerRadius = 24.dp, tint = 0.8f,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = 18.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = sky.ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (ar) "الذكر التالي" else "Next dhikr", color = sky.inkSoft, fontSize = 12.sp)
                    Text(
                        nextName, color = sky.ink, fontSize = 20.sp, fontFamily = dhikrFont(nextName), maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        // Your tasbeehs: fills what is left; scrolls inside itself only if you add many.
        GlassPanel(Modifier.fillMaxWidth().weight(1f), cornerRadius = 26.dp, tint = 0.7f) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ar) "أذكارك المخصصة" else "Your tasbeehs",
                        color = sky.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .border(BorderStroke(1.2.dp, sky.ink.copy(alpha = 0.55f)), CircleShape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                showAdd = true
                            }
                            ,
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Add, if (ar) "إضافة ذكر جديد" else "Add a tasbeeh", tint = sky.ink, modifier = Modifier.size(20.dp)) }
                }
                PRESETS.forEachIndexed { i, p ->
                    ColourRow(p.third, if (ar) p.first else p.second, presetCounts[i], PRESET_TARGET, selected == 0 && phrase == i) {
                        selected = 0
                        phrase = i
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
                custom.forEachIndexed { i, item ->
                    ColourRow(
                        parseColour(item.colorHex, sky.ink), item.textArabic.ifBlank { item.textTransliteration },
                        item.count, item.target.coerceAtLeast(1), selected == i + 1,
                    ) {
                        selected = i + 1
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
        }
    }

    if (showAdd) AddTasbeehDialog(ar, lang, onDismiss = { showAdd = false }) { arabic, latin, target, colour ->
        vm.addCustomTasbeeh(arabic, latin, target, colour)
        showAdd = false
    }
}

@Composable
private fun FridayBanner(ar: Boolean, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sky = LocalSky.current
    GlassPanel(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        cornerRadius = 24.dp, tint = if (selected) 1.9f else 0.8f,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Mosque, null, tint = FRIDAY_GREEN, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (ar) "تحدي الجمعة" else "Friday challenge", color = sky.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                    val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                    drawRoundRect(sky.ink.copy(alpha = 0.22f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                    val w = size.width * (count.toFloat() / FRIDAY_TARGET).coerceIn(0f, 1f)
                    if (w > 0f) drawRoundRect(
                        FRIDAY_GREEN, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "⁦$count / $FRIDAY_TARGET⁩", color = sky.inkSoft, fontSize = 13.sp,
                fontFamily = InterFontFamily, style = TextStyle(fontFeatureSettings = "tnum"), bypassNumerals = true,
            )
        }
    }
}

@Composable
private fun SquareGlass(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val sky = LocalSky.current
    GlassPanel(
        Modifier.size(64.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        cornerRadius = 24.dp, tint = 0.8f,
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = sky.ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, color = sky.inkSoft, fontSize = 10.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
        }
    }
}

/**
 * A string of 33 prayer beads hanging in a gentle curve with a tassel. The beads you have counted
 * this round take the tasbeeh's colour; the one just counted swells a little as it lands.
 */
@Composable
private fun Misbaha(count: Int, colour: Color, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    val beads = 33
    val counted = if (count > 0 && count % beads == 0) beads else count % beads
    val pop = remember { Animatable(0f) }
    LaunchedEffect(count) {
        if (count > 0) {
            pop.snapTo(1f)
            pop.animateTo(0f, tween(260))
        }
    }
    Canvas(modifier) {
        // The curve: a quadratic from the upper left, sagging in the middle, to the upper right.
        val margin = 12.dp.toPx()
        val tassel = 14.dp.toPx()
        val p0 = Offset(margin + 6.2.dp.toPx(), size.height * 0.12f)
        val p2 = Offset(size.width - margin - tassel, size.height * 0.10f)
        val p1 = Offset((p0.x + p2.x) / 2f, size.height * 1.02f)
        fun at(t: Float): Offset {
            val u = 1 - t
            return Offset(u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x, u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y)
        }
        val string = Path().apply {
            moveTo(p0.x, p0.y)
            quadraticTo(p1.x, p1.y, p2.x, p2.y)
        }
        drawPath(string, sky.ink.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        val r = 6.2.dp.toPx()
        for (i in 0 until beads) {
            val c = at(i / (beads - 1f))
            val on = i < counted
            val head = i == counted - 1
            val rad = r * (if (head) 1f + 0.35f * pop.value else 1f)
            drawCircle(if (on) colour else PEARL, rad, c)
            drawCircle(Color.White.copy(alpha = if (on) 0.40f else 0.7f), rad * 0.32f, Offset(c.x - rad * 0.3f, c.y - rad * 0.32f))
        }
        // The tassel: a knot, a few flat strands.
        val k = at(1f)
        val navy = Color(0xFF1E3A8A)
        drawCircle(navy, 5.dp.toPx(), Offset(k.x + 5.dp.toPx(), k.y + 4.dp.toPx()))
        val body = Path().apply {
            moveTo(k.x + 1.dp.toPx(), k.y + 8.dp.toPx())
            lineTo(k.x + 10.dp.toPx(), k.y + 8.dp.toPx())
            lineTo(k.x + 13.dp.toPx(), k.y + 44.dp.toPx())
            lineTo(k.x - 2.dp.toPx(), k.y + 44.dp.toPx())
            close()
        }
        drawPath(body, navy)
        for (j in 0..3) {
            val x = k.x + (0 + j * 3.4f).dp.toPx()
            drawLine(Color.White.copy(alpha = 0.22f), Offset(x, k.y + 16.dp.toPx()), Offset(x + (j - 1.5f) * 0.9f.dp.toPx(), k.y + 44.dp.toPx()), 1.dp.toPx())
        }
    }
}

/** A tasbeeh in the list: tinted in its own colour, outlined, brighter when it is the one in use. */
@Composable
private fun ColourRow(colour: Color, name: String, count: Int, target: Int, selected: Boolean, onClick: () -> Unit) {
    val sky = LocalSky.current
    val shape = RoundedCornerShape(21.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(colour.copy(alpha = if (selected) 0.30f else 0.14f))
            .border(BorderStroke(if (selected) 1.6.dp else 1.dp, colour.copy(alpha = if (selected) 0.95f else 0.5f)), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(13.dp).clip(CircleShape).background(colour))
        Spacer(Modifier.width(12.dp))
        Text(
            name, color = sky.ink, fontSize = 16.sp, fontFamily = dhikrFont(name),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            "⁦$count / $target⁩", color = sky.inkSoft, fontSize = 15.sp,
            fontFamily = InterFontFamily, style = TextStyle(fontFeatureSettings = "tnum"), bypassNumerals = true,
        )
    }
}

@Composable
private fun AddTasbeehDialog(
    ar: Boolean,
    lang: String,
    onDismiss: () -> Unit,
    onCreate: (arabic: String, latin: String, target: Int, colourHex: String) -> Unit,
) {
    val sky = LocalSky.current
    var arabic by remember { mutableStateOf("") }
    var latin by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("100") }
    var colour by remember { mutableStateOf("#FF64B5F6") }
    val fieldColours = OutlinedTextFieldDefaults.colors(
        focusedTextColor = sky.ink, unfocusedTextColor = sky.ink,
        focusedBorderColor = sky.ink, unfocusedBorderColor = sky.ink.copy(alpha = 0.25f),
        focusedLabelColor = sky.inkSoft, unfocusedLabelColor = sky.inkSoft, cursorColor = sky.ink,
    )
    GlassDialog(
        onDismiss = onDismiss,
        title = Localizations.string("dhikr_new_tasbih", lang, if (ar) "ذكر جديد" else "New tasbeeh"),
        confirmLabel = Localizations.string("dhikr_create", lang, if (ar) "إنشاء" else "Create"),
        onConfirm = { if (arabic.isNotBlank()) onCreate(arabic, latin, target.toIntOrNull() ?: 100, colour) },
        dismissLabel = Localizations.string("dhikr_cancel", lang, if (ar) "إلغاء" else "Cancel"),
    ) {
        OutlinedTextField(
            arabic, { arabic = it }, singleLine = true, colors = fieldColours, modifier = Modifier.fillMaxWidth(),
            label = { Text(Localizations.string("dhikr_arabic_req", lang, "Arabic Text (Required)")) },
        )
        OutlinedTextField(
            latin, { latin = it }, singleLine = true, colors = fieldColours, modifier = Modifier.fillMaxWidth(),
            label = { Text(Localizations.string("dhikr_trans_opt", lang, "English Translation (Optional)")) },
        )
        OutlinedTextField(
            target, { target = it.filter(Char::isDigit) }, singleLine = true, colors = fieldColours,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            label = { Text(Localizations.string("dhikr_target_goal", lang, "Target Goal")) },
        )
        Spacer(Modifier.height(4.dp))
        Text(if (ar) "اختر لوناً للعداد" else "Choose a colour", color = sky.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            COUNTER_COLOURS.forEach { hex ->
                val c = parseColour(hex, sky.ink)
                val on = colour == hex
                Box(
                    Modifier.size(if (on) 36.dp else 30.dp).clip(CircleShape).background(c)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { colour = hex },
                    contentAlignment = Alignment.Center,
                ) { if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White)) }
            }
        }
    }
}

/** The dhikr face for Arabic words; transliterations stay in the interface font. */
private fun dhikrFont(text: String) = if (text.any { it in '\u0600'..'\u06FF' }) HafsFontFamily else null
