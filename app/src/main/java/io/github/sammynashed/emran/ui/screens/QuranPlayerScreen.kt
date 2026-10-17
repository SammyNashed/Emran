package io.github.sammynashed.emran.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import io.github.sammynashed.emran.ui.design.GlassIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.QuranChapter
import io.github.sammynashed.emran.data.audio.AudioStore
import io.github.sammynashed.emran.data.audio.QuranAudio
import io.github.sammynashed.emran.data.audio.Reciters
import io.github.sammynashed.emran.ui.design.EmranIndigo
import io.github.sammynashed.emran.ui.design.EmranOrange
import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.design.drawEmranMark
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Emran's Qur'an player: a living cover (the Emran mark under a night sky; its five pillars
 * rise and settle in a slow wave and the stars breathe while the recitation plays, and all of it
 * rests when paused), the surah and ayah, a seekbar across the whole surah, the controls, repeat
 * (an ayah or a surah, for memorising) and the queue of surahs to recite one after another.
 */
@Composable
fun QuranPlayerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val now = QuranAudio.state.collectAsState().value
    if (now == null) {
        // Choosing a surah is an ordinary page on the sky; the player's night only comes with a recitation.
        QuranPlayerContent(onBack, modifier)
        return
    }
    // Inside the player the page sits on the cover's own night, so its text is always the light ink.
    val night = LocalSky.current.copy(darkInk = 0f)
    Box(Modifier.fillMaxSize()) {
        EmranBackdrop(now.playing, Modifier.fillMaxSize())
        CompositionLocalProvider(LocalSky provides night) { QuranPlayerContent(onBack, modifier) }
    }
}

@Composable
private fun QuranPlayerContent(onBack: () -> Unit, modifier: Modifier) {
    val ar = LocalAppLanguage.current == "ar"
    val context = LocalContext.current
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val now by QuranAudio.state.collectAsState()
    val repeat by QuranAudio.repeat.collectAsState()
    val chapters by produceState<List<QuranChapter>>(emptyList()) {
        value = runCatching { io.github.sammynashed.emran.data.repository.QuranRepository(context.applicationContext).getChapters() }.getOrDefault(emptyList())
    }
    fun name(s: Int) = chapters.firstOrNull { it.id == s }?.let { if (ar) it.name else it.transliteration } ?: "$s"
    val reciter = remember(now?.reciter) { Reciters.ALL.firstOrNull { it.id == now?.reciter } ?: Reciters.chosen(context) }
    val audioChanges by AudioStore.changes.collectAsState()
    val downloaded = remember(audioChanges, reciter) { AudioStore.installedSurahs(context, reciter.id) }

    // Where we are in the surah, polled from the player a few times a second.
    var queue by remember { mutableStateOf(emptyList<Int>()) }
    var range by remember { mutableStateOf(IntRange.EMPTY) }
    var index by remember { mutableIntStateOf(0) }
    var itemPos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            QuranAudio.controllerOrNull()?.let { c ->
                queue = QuranAudio.queueSurahs()
                now?.let { range = QuranAudio.indicesOf(it.surah) }
                index = c.currentMediaItemIndex
                itemPos = c.currentPosition
            }
            delay(200)
        }
    }
    val lengths by produceState(emptyList<Long>(), range) { value = if (range.isEmpty()) emptyList() else QuranAudio.lengths(context, range) }
    val total = lengths.sum()
    val inSurah = (index - range.first).coerceIn(0, (lengths.size - 1).coerceAtLeast(0))
    val position = if (lengths.isEmpty()) 0L else lengths.take(inSurah).sum() + itemPos
    fun seek(ms: Long) {
        val c = QuranAudio.controllerOrNull() ?: return
        var left = ms
        for ((i, len) in lengths.withIndex()) {
            if (left < len || i == lengths.lastIndex) { c.seekTo(range.first + i, left.coerceAtMost(len)); return }
            left -= len
        }
    }
    var showQueue by remember { mutableStateOf(false) }
    var showRepeat by remember { mutableStateOf(false) }

    // A player is a screen of its own: the dock steps aside (Back leaves), and it all fits on one page.
    val dockVisible = io.github.sammynashed.emran.LocalDockVisible.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        dockVisible.value = false
        onDispose { dockVisible.value = true }
    }

    Column(
        modifier.fillMaxSize().padding(horizontal = Space.screen).navigationBarsPadding().padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(
            title = if (ar) "مشغّل القرآن" else "Qur'an player",
            onBack = onBack,
            actions = {
                // Ends the recitation for good: the queue is cleared and the notification goes.
                if (now != null) GlassIconButton(Icons.Filled.Stop, if (ar) "إيقاف التلاوة" else "Stop the recitation", { QuranAudio.stop() })
            },
        )

        val n = now
        if (n == null) {
            // Nothing playing: just the surahs you have, to pick from.
            Spacer(Modifier.height(8.dp))
            Text(if (ar) "اختر سورة للاستماع" else "Pick a surah to listen to", color = sky.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (downloaded.isEmpty()) (if (ar) "لا سور منزّلة بصوت ${reciter.ar} بعد. نزّلها من زر «استماع» في المصحف، أو من صفحة القرّاء." else "No surahs downloaded in ${reciter.en}'s voice yet. Download from Listen in the reader, or from Reciters.")
                else (if (ar) "بصوت ${reciter.ar}" else "Recited by ${reciter.en}"),
                color = sky.inkSoft, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(downloaded.size) { i ->
                    val s = downloaded[i]
                    SurahRow(name(s), s, current = false, onClick = { scope.launch { runCatching { QuranAudio.play(context, reciter, s, name(s)) } } }, onRemove = null)
                }
            }
            return@Column
        }

        // The cover takes whatever height is left, so the page never needs to scroll.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmranCover(
                playing = n.playing,
                modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1f, matchHeightConstraintsFirst = true),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (ar) "سورة ${name(n.surah)}" else name(n.surah),
            color = sky.ink, fontSize = 30.sp, fontFamily = if (ar) SurahNameFontFamily else null, maxLines = 1,
        )
        Text(
            (if (n.ayah == 0) (if (ar) "البسملة" else "Bismillah") else (if (ar) "الآية ${n.ayah} من ${AudioStore.AYAH_COUNT[n.surah - 1]}" else "Ayah ${n.ayah} of ${AudioStore.AYAH_COUNT[n.surah - 1]}")) +
                " · " + (if (ar) reciter.ar else reciter.en),
            color = sky.inkSoft, fontSize = 14.sp,
        )
        // While a repeat is on, say so plainly: a bar jumping back must never look like a glitch.
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            if (repeat != QuranAudio.Repeat.OFF) {
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(EmranOrange.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (repeat == QuranAudio.Repeat.AYAH) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null, tint = sky.ink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            repeat == QuranAudio.Repeat.SURAH -> if (ar) "تُكرَّر السورة كلها" else "The whole surah repeats"
                            n.ayah == 0 -> if (ar) "تُكرَّر البسملة" else "The Bismillah repeats"
                            else -> if (ar) "تُكرَّر الآية ${n.ayah}" else "Ayah ${n.ayah} repeats"
                        },
                        color = sky.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // The stretch being repeated, marked on the bar.
        val loop = when {
            total <= 0 || lengths.isEmpty() -> null
            repeat == QuranAudio.Repeat.SURAH -> 0f..1f
            repeat == QuranAudio.Repeat.AYAH -> {
                val from = lengths.take(inSurah).sum().toFloat() / total
                from..((from + (lengths.getOrElse(inSurah) { 0L }.toFloat() / total)).coerceAtMost(1f))
            }
            else -> null
        }
        QuranSeekBar(position, total, n.playing, onSeek = ::seek, loop = loop)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                Text(clock(position), color = sky.inkSoft, fontSize = 12.sp, bypassNumerals = true)
                Spacer(Modifier.weight(1f))
                Text(clock(total), color = sky.inkSoft, fontSize = 12.sp, bypassNumerals = true)
            }
            Spacer(Modifier.height(10.dp))
            // One row, as on every player: repeat · previous · play · next · queue. In Arabic "next ayah"
            // sits on the left, as pages turn.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SideButton(
                    if (repeat == QuranAudio.Repeat.AYAH) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    if (ar) "التكرار" else "Repeat", active = repeat != QuranAudio.Repeat.OFF,
                ) { showRepeat = true }
                RoundIcon(Icons.Filled.SkipPrevious, if (ar) "الآية التالية" else "Previous ayah", 56.dp) { if (ar) QuranAudio.next() else QuranAudio.previous() }
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(sky.ink)
                        .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); QuranAudio.togglePause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (n.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ar) "تشغيل/إيقاف" else "Play/pause",
                        tint = if (sky.darkInk > 0.5f) Color.White else Color(0xFF0B1B33), modifier = Modifier.size(40.dp),
                    )
                }
                RoundIcon(Icons.Filled.SkipNext, if (ar) "الآية السابقة" else "Next ayah", 56.dp) { if (ar) QuranAudio.previous() else QuranAudio.next() }
                SideButton(Icons.AutoMirrored.Filled.FormatListBulleted, if (ar) "القائمة" else "Queue", active = false) { showQueue = true }
            }
        }
    }

    if (showRepeat) {
        GlassDialog(onDismiss = { showRepeat = false }, title = if (ar) "التكرار" else "Repeat", dismissLabel = if (ar) "إغلاق" else "Close") {
            listOf(
                Triple(QuranAudio.Repeat.OFF, if (ar) "بلا تكرار" else "Off", if (ar) "تمضي التلاوة إلى آخرها" else "Plays straight through"),
                Triple(QuranAudio.Repeat.AYAH, if (ar) "تكرار الآية" else "Repeat the ayah", if (ar) "تُعاد الآية نفسها، للحفظ" else "The same ayah again and again, for memorising"),
                Triple(QuranAudio.Repeat.SURAH, if (ar) "تكرار السورة" else "Repeat the surah", if (ar) "تُعاد السورة من أولها حين تنتهي" else "The surah starts over when it ends"),
            ).forEach { (mode, title, sub) ->
                val on = repeat == mode
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (on) sky.ink.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { QuranAudio.setRepeat(mode); showRepeat = false }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = sky.ink, fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                        Text(sub, color = sky.inkSoft, fontSize = 13.sp)
                    }
                    if (on) Icon(Icons.Filled.Check, null, tint = sky.ink)
                }
            }
        }
    }

    if (showQueue) {
        val choices = downloaded.filterNot { it in queue }
        GlassDialog(onDismiss = { showQueue = false }, title = if (ar) "قائمة التلاوة" else "Queue", dismissLabel = if (ar) "إغلاق" else "Close") {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                queue.forEach { s ->
                    val current = s == now?.surah
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (current) sky.ink.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { QuranAudio.playSurahInQueue(s) }
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (current) Icon(Icons.Filled.PlayArrow, null, tint = sky.ink, modifier = Modifier.size(18.dp)) else Text("$s", color = sky.inkSoft, fontSize = 13.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (ar) "سورة ${name(s)}" else name(s), color = sky.ink, fontSize = 17.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (queue.size > 1) {
                            Icon(Icons.Filled.Close, if (ar) "إزالة" else "Remove", tint = sky.inkSoft,
                                modifier = Modifier.clip(CircleShape).clickable { QuranAudio.removeFromQueue(s) }.padding(12.dp).size(18.dp))
                        } else Spacer(Modifier.height(42.dp))
                    }
                }
                if (choices.isNotEmpty()) {
                    Text(if (ar) "أضف من سورك المنزّلة" else "Add from your downloads", color = sky.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp))
                    choices.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .clickable { scope.launch { runCatching { QuranAudio.enqueue(context, reciter, s, name(s)) } } }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Add, null, tint = sky.ink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (ar) "سورة ${name(s)}" else name(s), color = sky.ink, fontSize = 17.sp)
                        }
                    }
                } else {
                    Text(
                        if (ar) "لإضافة سور أخرى نزّلها أولًا من زر «استماع» في المصحف." else "To add more surahs, download them first from Listen in the reader.",
                        color = sky.inkSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/** A small labelled button beside the transport controls (repeat, queue); tinted orange while active. */
@Composable
private fun SideButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val sky = LocalSky.current
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) EmranOrange else sky.ink, modifier = Modifier.size(26.dp))
        Text(label, color = if (active) EmranOrange else sky.inkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SurahRow(name: String, number: Int, current: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    val sky = LocalSky.current
    val ar = LocalAppLanguage.current == "ar"
    GlassPanel(Modifier.fillMaxWidth().clickable { onClick() }, cornerRadius = 22.dp, tint = if (current) 1.3f else 0.8f, lens = false) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(sky.ink.copy(alpha = if (current) 0.9f else 0.12f)), contentAlignment = Alignment.Center) {
                if (current) Icon(Icons.Filled.PlayArrow, null, tint = if (sky.darkInk > 0.5f) Color.White else Color(0xFF0B1B33), modifier = Modifier.size(18.dp))
                else Text("$number", color = sky.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (ar) "سورة $name" else name, color = sky.ink, fontSize = if (ar) 22.sp else 16.sp,
                fontFamily = if (ar) SurahNameFontFamily else null, modifier = Modifier.weight(1f).padding(vertical = 6.dp), maxLines = 1,
            )
            if (onRemove != null) {
                Icon(
                    Icons.Filled.Close, if (ar) "إزالة" else "Remove", tint = sky.inkSoft,
                    modifier = Modifier.clip(CircleShape).clickable { onRemove() }.padding(12.dp).size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(size).clip(CircleShape).background(sky.ink.copy(alpha = 0.10f))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = sky.ink, modifier = Modifier.size(30.dp)) }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Seconds since the screen opened, advancing only while [playing] (a still picture costs no frames). */
@Composable
private fun playClock(playing: Boolean): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = 0L
        while (true) withFrameNanos { n -> if (last != 0L) t += (n - last) / 1e9f; last = n }
    }
    return t
}

/**
 * The cover: Emran's logo as a visualizer. While the recitation sounds, each pillar follows a band
 * of the reciter's voice, read from the audio itself ([RecitationLevels]): the voice's body in the
 * tall middle pillar, lower and higher bands outward. When the reciter is silent (a breath, the gap
 * between ayat) the pillars sink almost to nothing. When you pause, they ease back into the
 * logo's own shape, so a paused player is simply the Emran mark at rest.
 */
@Composable
fun EmranCover(playing: Boolean, modifier: Modifier = Modifier) {
    val bands by io.github.sammynashed.emran.data.audio.RecitationLevels.levels.collectAsState()
    val isPlaying by rememberUpdatedState(playing)
    // Pillar (left → right) ← band: air, vowels, voice body, chest, presence.
    val order = intArrayOf(4, 2, 1, 0, 3)
    // Each pillar's height as a fraction of the logo's own (1 = the mark as drawn).
    val h = remember { FloatArray(5) { 1f } }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            var moving = false
            for (i in 0 until 5) {
                // A small gate: below it is silence (the pillar sinks), above it the voice lifts the pillar.
                val raw = bands.getOrElse(order[i]) { 0f }
                val level = Math.pow(((raw - GATE) / (1f - GATE)).coerceIn(0f, 1f).toDouble(), 0.85).toFloat()
                // Voice: from a low floor (almost nothing in silence) up to the logo's full height.
                val target = if (isPlaying) FLOOR + (1f - FLOOR) * level else 1f
                // Quick enough to follow a syllable, gentle enough to stay graceful; slowest back to the logo.
                val rate = when {
                    !isPlaying -> 0.07f
                    target > h[i] -> 0.14f
                    else -> 0.07f
                }
                val step = (target - h[i]) * rate
                if (kotlin.math.abs(step) > 0.0004f) moving = true
                h[i] += step
            }
            if (moving) { frame++; withFrameNanos { } } else delay(120)   // a settled picture costs nothing
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION") frame // redraw every frame the levels move
        drawEmranMark(center, size.minDimension) { i -> h[i] }
    }
}

/** How low a pillar sinks when the reciter is silent: a small stub, not zero. */
private const val FLOOR = 0.08f

/** The level (0..1 of the band's recent loudest) under which the reciter counts as silent. */
private const val GATE = 0.22f

/**
 * The player's page: the night behind Emran's logo (deep indigo above, an ember horizon below,
 * stars and a soft halo), blurred like light through frosted glass and slowly breathing while
 * the recitation plays.
 */
@Composable
fun EmranBackdrop(playing: Boolean, modifier: Modifier = Modifier) {
    val motion by animateFloatAsState(if (playing) 1f else 0f, tween(900), label = "backdrop")
    val t = playClock(playing)
    val stars = remember {
        val rnd = java.util.Random(7)
        List(70) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.75f, 1f + rnd.nextFloat() * 2.2f, rnd.nextFloat() * 6.28f, 0.3f + rnd.nextFloat() * 0.8f) }
    }
    // The page itself starts below the status bar and ends above the gesture bar; the night must
    // still reach both screen edges, so it is laid out taller than its slot and shifted up.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density)
    val bottom = WindowInsets.navigationBars.getBottom(density)
    Canvas(
        modifier
            .layout { measurable, c ->
                // Plus a margin all round: a blur fades out at its edges, and those edges must fall off-screen.
                val m = 48.dp.roundToPx()
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(c.maxWidth + 2 * m, c.maxHeight + top + bottom + 2 * m))
                layout(c.maxWidth, c.maxHeight) { placeable.place(-m, -top - m) }
            }
            .blur(18.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
    ) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF070B1F), Color(0xFF151A45), Color(0xFF3A2350), Color(0xFF7A3A2E))))
        drawCircle(
            Brush.radialGradient(listOf(EmranOrange.copy(alpha = 0.45f + 0.08f * sin(t * 0.6f) * motion), Color.Transparent), Offset(w / 2, h * 1.05f), w * 1.1f),
            radius = w * 1.1f, center = Offset(w / 2, h * 1.05f),
        )
        drawCircle(
            Brush.radialGradient(listOf(EmranIndigo.copy(alpha = 0.40f + 0.10f * sin(t * 0.9f) * motion), Color.Transparent), Offset(w / 2, h * 0.36f), w * 0.8f),
            radius = w * 0.8f, center = Offset(w / 2, h * 0.36f),
        )
        for (st in stars) {
            val a = (0.6f + 0.4f * sin(t * st[4] + st[3]) * motion).coerceIn(0.15f, 1f)
            drawCircle(Color.White.copy(alpha = a * 0.9f), radius = st[2].dp.toPx(), center = Offset(st[0] * w, st[1] * h))
        }
        // A light veil so the text above always reads.
        drawRect(Color.Black.copy(alpha = 0.18f))
    }
}

/**
 * Musik's squiggly seekbar in Emran's colours: the played part is a travelling wave in the mark's
 * indigo-to-orange, the rest a quiet line; the wave calms when paused or while dragging.
 */
@Composable
fun QuranSeekBar(
    positionMs: Long, durationMs: Long, playing: Boolean, onSeek: (Long) -> Unit, modifier: Modifier = Modifier,
    /** The part being repeated (fractions of the bar), drawn as a soft band; null when not repeating. */
    loop: ClosedFloatingPointRange<Float>? = null,
) {
    val sky = LocalSky.current
    var drag by remember { mutableStateOf<Float?>(null) }
    val amp by animateFloatAsState(if (playing && drag == null) 1f else 0f, tween(450), label = "amp")
    var phase by remember { mutableFloatStateOf(0f) }
    val moving by rememberUpdatedState(amp > 0f)
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = 0L
        while (true) withFrameNanos { n ->
            if (last != 0L && moving) phase = (phase + (n - last) / 1e9f * 2f * PI.toFloat() * 0.9f) % (2f * PI.toFloat())
            last = n
        }
    }
    val duration by rememberUpdatedState(durationMs)
    val seek by rememberUpdatedState(onSeek)
    val frac = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // Time runs left to right, as on every player.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier.fillMaxWidth().height(44.dp)
                .pointerInput(Unit) { detectTapGestures { o -> if (duration > 0) seek(((o.x / size.width).coerceIn(0f, 1f) * duration).toLong()) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { drag?.let { if (duration > 0) seek((it * duration).toLong()) }; drag = null },
                        onDragCancel = { drag = null },
                        onHorizontalDrag = { c, dx -> c.consume(); drag = ((drag ?: 0f) + dx / size.width).coerceIn(0f, 1f) },
                    )
                },
        ) {
            val stroke = 4.dp.toPx(); val pad = 10.dp.toPx(); val mid = size.height / 2
            val w = size.width - pad * 2; val x = pad + w * frac; val gap = 6.dp.toPx()
            val waveLen = 30.dp.toPx(); val a = 3.5.dp.toPx() * amp
            loop?.let { l ->
                val x0 = pad + w * l.start; val x1 = maxOf(pad + w * l.endInclusive, x0 + 4.dp.toPx())
                val bh = 18.dp.toPx()
                drawRoundRect(EmranOrange.copy(alpha = 0.22f), Offset(x0, mid - bh / 2), Size(x1 - x0, bh), CornerRadius(bh / 2))
            }
            if (x + gap < size.width - pad) drawLine(sky.ink.copy(alpha = 0.18f), Offset(x + gap, mid), Offset(size.width - pad, mid), stroke, StrokeCap.Round)
            if (x - gap > pad) {
                val path = Path(); var px = pad; path.moveTo(px, mid)
                while (px <= x - gap) {
                    val ease = ((px - pad) / (waveLen / 2)).coerceAtMost(1f)
                    path.lineTo(px, mid + sin((px / waveLen) * 2f * PI.toFloat() - phase) * a * ease)
                    px += 2f
                }
                drawPath(path, Brush.horizontalGradient(listOf(EmranIndigo, EmranOrange), pad, size.width - pad), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            val tw = (if (drag != null) 8 else 6).dp.toPx(); val th = 24.dp.toPx()
            drawRoundRect(sky.ink, Offset(x - tw / 2, mid - th / 2), Size(tw, th), CornerRadius(tw / 2))
        }
    }
}
