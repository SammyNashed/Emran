package io.github.sammynashed.emran.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.testers.Review
import io.github.sammynashed.emran.data.testers.Tester
import io.github.sammynashed.emran.data.testers.TestersRepository
import io.github.sammynashed.emran.data.testers.TestersWall
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.AppTheme
import java.net.URLEncoder

/**
 * The thank-you wall: everyone who tested Emran, and what they said, drifting past in the
 * way testimonial sections do on websites. Data comes from assets/testers.json, so adding a
 * person or a quote is a one-line edit (docs/testers-format.md).
 */
@Composable
fun TestersScreen(onBack: () -> Unit) {
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val wall by produceState<TestersWall?>(null) { value = TestersRepository.load(context) }
    val ar = lang == "ar"
    // Hearts rise once on entering the page, and again whenever the big heart is tapped.
    var burst by remember { mutableIntStateOf(1) }

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f))
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (ar) "لوحة الشكر والامتنان" else "WALL OF THANKS",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = if (ar) 0.sp else 1.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (ar) "شكر خاص للمختبرين الأوائل والمجتمع" else "Gratitude to our early testers and community",
                        color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val w = wall
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 140.dp)) {
                Hero(ar, w?.testers?.size ?: 0, w?.reviews?.size ?: 0, onHeartTap = { burst++ })
                Spacer(Modifier.height(20.dp))
                if (w != null && w.reviews.isNotEmpty()) {
                    SectionTitle(if (ar) "ماذا قالوا" else "WHAT THEY SAID")
                    Spacer(Modifier.height(10.dp))
                    // Few reviews read best as one row; from eight up they split into two rows
                    // drifting opposite ways, like a wall.
                    val rows = if (w.reviews.size >= 8) listOf(
                        w.reviews.filterIndexed { i, _ -> i % 2 == 0 },
                        w.reviews.filterIndexed { i, _ -> i % 2 == 1 }
                    ) else listOf(w.reviews)
                    rows.forEachIndexed { i, list ->
                        Marquee(speedDp = if (i == 0) 34f else 28f, reverse = i == 1, gap = 14.dp) {
                            list.forEachIndexed { k, r -> ReviewCard(r, lang, accent = if ((k + i) % 2 == 0) AppTheme.colors.primary else AppTheme.colors.secondary) }
                        }
                        if (i < rows.lastIndex) Spacer(Modifier.height(14.dp))
                    }
                }
                if (w != null && w.testers.isNotEmpty()) {
                    Spacer(Modifier.height(26.dp))
                    SectionTitle(if (ar) "المختبرون الأوائل" else "EARLY BETA TESTERS")
                    Spacer(Modifier.height(10.dp))
                    Marquee(speedDp = 22f, reverse = true, gap = 10.dp) {
                        w.testers.forEachIndexed { k, t -> TesterPill(t, lang, if (k % 2 == 0) AppTheme.colors.primary else AppTheme.colors.secondary) }
                    }
                }
                if (w != null && w.reviews.isEmpty() && w.testers.isEmpty()) {
                    Text(
                        text = if (ar) "شكراً لكل من جرّب عمران." else "Thank you to everyone who tried Emran.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
                Spacer(Modifier.height(28.dp))
                FeedbackCard(ar)
            }
        }
        HeartsOverlay(burst)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text, color = AppTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun Hero(ar: Boolean, testers: Int, reviews: Int, onHeartTap: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .liquidGlassCard(RoundedCornerShape(24.dp), AppTheme.colors.secondary)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A slow heartbeat. A tap only sets the hearts off; they carry the vibration themselves.
            val beat by rememberInfiniteTransition(label = "heart").animateFloat(
                initialValue = 1f, targetValue = 1.1f,
                animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "beat"
            )
            Box(
                Modifier.size(50.dp)
                    .graphicsLayer { scaleX = beat; scaleY = beat }
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AppTheme.colors.primary, AppTheme.colors.secondary)))
                    .clickable(onClick = onHeartTap),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = if (ar) "شكراً لكم" else "Thank you",
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
                )
                if (testers > 0 || reviews > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (testers > 0) StatChip(testers.toString(), if (ar) "مختبرين" else if (testers == 1) "tester" else "testers")
                        if (reviews > 0) StatChip(reviews.toString(), if (ar) "آراء" else if (reviews == 1) "review" else "reviews")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (ar) "خالص الشكر والتقدير لكل من ساهم في مراجعة وتدقيق واختبار تطبيق عمران ليكون رفيقاً إيمانياً متميزاً وموثوقاً."
            else "Sincere gratitude and prayers to everyone who supported, beta-tested and reviewed Emran.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 12.5.sp, lineHeight = 19.sp, maxLines = 3
        )
    }
}

@Composable
private fun StatChip(value: String, label: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(value, color = AppTheme.colors.secondary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun Avatar(name: String, accent: Color, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center
    ) {
        Text(name.trim().take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, bypassNumerals = true)
    }
}

@Composable
private fun ReviewCard(review: Review, lang: String, accent: Color) {
    val text = review.text(lang)
    // The words keep the direction of the language they were written in, whatever the app language is.
    val textIsArabic = text.any { it in '\u0600'..'\u06FF' }
    // The marquee itself is laid out left-to-right so it slides the same way in both languages;
    // the card inside follows the app's direction.
    val appDirection = if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides appDirection) {
    Column(
        modifier = Modifier
            .width(292.dp)
            .liquidGlassCard(RoundedCornerShape(24.dp), accent.copy(alpha = 0.6f))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("“", color = accent.copy(alpha = 0.8f), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp, bypassNumerals = true)
            Spacer(Modifier.weight(1f))
            review.stars?.let { s ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) { i ->
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp),
                            tint = if (i < s) Color(0xFFFFC107) else Color.White.copy(alpha = 0.18f))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        CompositionLocalProvider(LocalLayoutDirection provides if (textIsArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Text(
                text = text, color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, lineHeight = 25.sp,
                maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(), bypassNumerals = true
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(review.tester.name(lang), accent)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(review.tester.name(lang), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, bypassNumerals = true)
                review.tester.role(lang)?.let { Text(it, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, maxLines = 1) }
            }
        }
    }
    }
}

@Composable
private fun TesterPill(tester: Tester, lang: String, accent: Color) {
    CompositionLocalProvider(LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
    Row(
        modifier = Modifier
            .liquidGlassCard(RoundedCornerShape(999.dp), accent.copy(alpha = 0.45f))
            .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(tester.name(lang), accent, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(tester.name(lang), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, bypassNumerals = true)
    }
    }
}

@Composable
private fun FeedbackCard(ar: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.04f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (ar) "جرّبت عمران؟" else "Tried Emran?",
            color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (ar) "رأيك يهمّنا، وقد تجد كلماتك هنا في التحديث القادم." else "Your words matter, and yours could be on this wall in the next update.",
            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(listOf(AppTheme.colors.primary, AppTheme.colors.secondary)))
                .clickable {
                    val msg = if (ar) "السلام عليكم، رأيي في تطبيق عمران: " else "Assalamu Alaikum Sammy, my thoughts on Emran: "
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/963986157471?text=" + URLEncoder.encode(msg, "UTF-8"))))
                    } catch (e: Exception) { /* no app to open the link */ }
                }
                .padding(horizontal = 26.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (ar) "أرسل رأيك" else "Share your feedback", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---- the moving part ----------------------------------------------------

/**
 * An endless, seamless marquee: [content] is laid out as a strip and repeated, and the strip
 * slides by one repeat's width and starts over, so there is never a gap or a jump. Holding a
 * finger on it pauses it, so a card can be read.
 *
 * Drives itself from the frame clock and moves with a graphicsLayer, so it animates without
 * recomposing anything. Always laid out left-to-right so "slides left" means the same thing
 * in both languages; the cards choose their own text direction.
 */
@Composable
private fun Marquee(
    speedDp: Float,
    gap: Dp,
    modifier: Modifier = Modifier,
    reverse: Boolean = false,
    content: @Composable () -> Unit
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
                .pointerInput(Unit) { detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false }) }
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = if (reverse) -period + offset else -offset },
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                // Three repeats, so even a short strip always covers the screen.
                Row(Modifier.onSizeChanged { period = it.width + gapPx }, horizontalArrangement = Arrangement.spacedBy(gap)) { content() }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { content() }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { content() }
            }
        }
    }
}

/** Fades the left and right edges to transparent so cards glide in and out instead of being cut off. */
private fun Modifier.fadeEdges(width: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = (width.toPx() / size.width).coerceIn(0f, 0.4f)
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Transparent, f to Color.Black, (1f - f) to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn
        )
    }


// ---- little hearts ------------------------------------------------------

/** A standard heart outline in a 32 x 29.6 box. */
private const val HEART_PATH =
    "M23.6,0c-3.4,0-6.3,2.7-7.6,5.6C14.7,2.7,11.8,0,8.4,0C3.8,0,0,3.8,0,8.4c0,9.4,9.5,11.9,16,21.2" +
        "c6.1-9.3,16-12.1,16-21.2C32,3.8,28.2,0,23.6,0z"

private class Heart(
    val delay: Float, val duration: Float, val x: Float, val sizeDp: Float,
    val swayDp: Float, val freq: Float, val phase: Float, val tilt: Float, val color: Color, val alpha: Float
)

/**
 * A gentle drift of small hearts rising from the bottom of the screen: staggered, soft, fading in
 * and out, swaying a little as they go. Each [burst] value plays it once. It draws nothing (and
 * runs no frames) when idle, does not take touches, and is skipped entirely when the system has
 * animations turned off.
 */
@Composable
internal fun HeartsOverlay(burst: Int) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val palette = listOf(AppTheme.colors.primary, AppTheme.colors.secondary, Color(0xFFFF6B9D))
    val animationsOn = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val base = remember { PathParser().parsePathString(HEART_PATH).toPath() }
    var hearts by remember { mutableStateOf<List<Heart>>(emptyList()) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vibratorOf(context)?.cancel() } }

    LaunchedEffect(burst) {
        vibratorOf(context)?.cancel()
        if (!animationsOn) return@LaunchedEffect
        val rnd = kotlin.random.Random(System.nanoTime())
        val list = List(20) { i ->
            Heart(
                delay = i * 0.2f + rnd.nextFloat() * 0.25f,
                duration = 3.8f + rnd.nextFloat() * 1.8f,
                x = 0.06f + rnd.nextFloat() * 0.88f,
                sizeDp = 11f + rnd.nextFloat() * 13f,
                swayDp = 8f + rnd.nextFloat() * 16f,
                freq = 0.8f + rnd.nextFloat() * 0.9f,
                phase = rnd.nextFloat() * 6.28f,
                tilt = (rnd.nextFloat() - 0.5f) * 26f,
                color = palette[rnd.nextInt(palette.size)],
                alpha = 0.45f + rnd.nextFloat() * 0.35f
            )
        }
        hearts = list
        elapsed = 0f
        val total = list.maxOf { it.delay + it.duration }
        vibrateWithHearts(context, list, total)
        var last = 0L
        while (elapsed < total) {
            withFrameNanos { t ->
                if (last != 0L) elapsed += (t - last) / 1_000_000_000f
                last = t
            }
        }
        hearts = emptyList()
    }

    if (hearts.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        for (h in hearts) {
            val t = (elapsed - h.delay) / h.duration
            if (t < 0f || t > 1f) continue
            // Fade in over the first 15%, out over the last 40%.
            val fade = minOf(1f, t / 0.15f) * minOf(1f, (1f - t) / 0.4f)
            val s = h.sizeDp * density * (0.75f + 0.35f * t)
            val x = size.width * h.x + kotlin.math.sin(t * h.freq * 9.4f + h.phase) * h.swayDp * density
            val y = size.height + 30f - t * size.height * 0.78f
            withTransform({
                translate(x - s / 2f, y - s / 2f)
                rotate(h.tilt * (1f - t), pivot = Offset(s / 2f, s / 2f))
                scale(s / 32f, s / 32f, pivot = Offset.Zero)
            }) {
                drawPath(base, color = h.color.copy(alpha = h.alpha * fade))
            }
        }
    }
}


private fun vibratorOf(context: android.content.Context): android.os.Vibrator? = try {
    if (android.os.Build.VERSION.SDK_INT >= 31)
        (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
} catch (e: Exception) { null }

/**
 * A medium, steady vibration that follows the hearts: it swells as more of them are on screen and
 * eases off as they fade, for as long as the burst lasts. Built from short steps, each with the
 * strength the visible hearts add up to.
 */
private fun vibrateWithHearts(context: android.content.Context, hearts: List<Heart>, total: Float) {
    val vibrator = vibratorOf(context) ?: return
    try {
        val step = 0.08f
        val n = (total / step).toInt().coerceAtLeast(1)
        val timings = LongArray(n) { (step * 1000).toLong() }
        val amps = IntArray(n) { i ->
            val time = i * step
            var sum = 0f
            for (h in hearts) {
                val t = (time - h.delay) / h.duration
                if (t in 0f..1f) sum += minOf(1f, t / 0.15f) * minOf(1f, (1f - t) / 0.4f)
            }
            (16 + 34 * minOf(1f, sum / 3f)).toInt().coerceIn(1, 255)
        }
        vibrator.vibrate(
            if (vibrator.hasAmplitudeControl()) android.os.VibrationEffect.createWaveform(timings, amps, -1)
            else android.os.VibrationEffect.createWaveform(timings, -1)
        )
    } catch (e: Exception) { /* no vibrator or not allowed */ }
}
