package io.github.sammynashed.emran.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.TextureView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.sammynashed.emran.data.QuranChapter
import io.github.sammynashed.emran.data.audio.Reciters
import io.github.sammynashed.emran.data.repository.QuranRepository
import io.github.sammynashed.emran.data.video.QuranVideoMaker
import io.github.sammynashed.emran.data.video.VideoBackgrounds
import io.github.sammynashed.emran.data.video.VideoCard
import io.github.sammynashed.emran.ui.design.ChoiceRow
import io.github.sammynashed.emran.ui.design.GlassButton
import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.GlassDivider
import io.github.sammynashed.emran.ui.design.GlassGroup
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.GlassSectionLabel
import io.github.sammynashed.emran.ui.design.GlassSegmented
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.SettingsRow
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PREFS = "emran_video"

private sealed interface Work {
    data object Idle : Work
    data class Downloading(val done: Int, val total: Int) : Work
    data class Rendering(val percent: Int) : Work
    data class Done(val file: File) : Work
    data class Failed(val message: String) : Work
}

/**
 * "اصنع فيديو قرآني": choose ayat and a reciter, a nature clip, what shows on it, then make an MP4 on the phone.
 * The live preview at the top is drawn by the same painter as the export, over the real looping clip.
 */
@OptIn(UnstableApi::class)
@Composable
fun QuranVideoScreen(lang: String, startSurah: Int?, startAyah: Int?, onBack: () -> Unit) {
    val ar = lang == "ar"
    val sky = LocalSky.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val chapters by produceState<List<QuranChapter>>(emptyList()) {
        value = withContext(Dispatchers.IO) { runCatching { QuranRepository(context.applicationContext).getChapters() }.getOrDefault(emptyList()) }
    }
    val reciters = remember { Reciters.ALL.filter { !it.wholeSurah } }

    var surah by remember { mutableIntStateOf(startSurah ?: prefs.getInt("surah", 93)) }
    var from by remember { mutableIntStateOf(startAyah ?: prefs.getInt("from", 1)) }
    var to by remember { mutableIntStateOf(if (startAyah != null) startAyah else prefs.getInt("to", 5)) }
    var reciterId by remember { mutableStateOf(prefs.getString("reciter", null) ?: Reciters.chosen(context).id) }
    var clipId by remember { mutableStateOf(prefs.getString("clip", null) ?: VideoBackgrounds.ALL.first().id) }
    var category by remember { mutableStateOf("all") }
    var explainId by remember { mutableStateOf(prefs.getString("explain", null) ?: QuranVideoMaker.defaultExplain(lang)) }
    var showReciter by remember { mutableStateOf(prefs.getBoolean("show_reciter", true)) }
    var aspectKey by remember { mutableStateOf(prefs.getString("aspect", null) ?: "9:16") }
    var quality by remember { mutableIntStateOf(prefs.getInt("quality", 720)) }

    val chapter = chapters.firstOrNull { it.id == surah }
    val ayahCount = chapter?.total_verses ?: 286
    // Keep the range inside the surah, and from <= to.
    LaunchedEffect(surah, ayahCount) {
        if (chapter != null) {
            from = from.coerceIn(1, ayahCount)
            to = to.coerceIn(from, ayahCount)
        }
    }
    val reciter = reciters.firstOrNull { it.id == reciterId } ?: reciters.first()
    val clip = VideoBackgrounds.byId(clipId)
    val aspect = QuranVideoMaker.Aspect.of(aspectKey)

    fun saveChoices() {
        prefs.edit().putInt("surah", surah).putInt("from", from).putInt("to", to).putString("reciter", reciter.id)
            .putString("clip", clip.id).putString("explain", explainId).putBoolean("show_reciter", showReciter)
            .putString("aspect", aspect.key).putInt("quality", quality).apply()
    }

    val spec = QuranVideoMaker.Spec(surah, from, to, reciter, clip, explainId, showReciter, aspect, quality, lang)

    // Rough length from the words and the reciter's bytes per word (only to keep videos under five minutes).
    val estimateSec by produceState(0, surah, from, to, reciter.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val verses = QuranRepository(context.applicationContext).getSurahVerses(surah).verses
                val words = (from..to).sumOf { a -> verses.getOrNull(a - 1)?.text?.split(' ')?.count { it.isNotBlank() } ?: 0 }
                val kbps = Regex("(\\d+)kbps").find(reciter.folder)?.groupValues?.get(1)?.toIntOrNull() ?: 128
                (words.toLong() * reciter.bytesPerWord / (kbps * 125L)).toInt() + (if (spec.withBasmala) 6 else 0)
            }.getOrDefault(0)
        }
    }
    val tooLong = estimateSec > 330

    var work by remember { mutableStateOf<Work>(Work.Idle) }
    val scroll = rememberScrollState()
    var job by remember { mutableStateOf<Job?>(null) }
    val busy = work is Work.Downloading || work is Work.Rendering
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }
    // Any change after a finished video means the next press makes a new one.
    LaunchedEffect(surah, from, to, reciter.id, clip.id, explainId, showReciter, aspect, quality) {
        if (work is Work.Done || work is Work.Failed) work = Work.Idle
    }

    fun make() {
        saveChoices()
        val name = chapter?.name ?: "$surah"
        val nameEn = chapter?.transliteration ?: "$surah"
        // Progress and the finished video show at the top, in the preview: take the person there.
        scope.launch { scroll.animateScrollTo(0) }
        job = scope.launch {
            try {
                work = Work.Downloading(0, 1)
                val files = QuranVideoMaker.prepareAudio(context, spec) { d, t -> work = Work.Downloading(d, t) }
                val segs = QuranVideoMaker.segments(context, spec, files)
                if (segs.last().endUs > QuranVideoMaker.MAX_US + 30_000_000L) {
                    work = Work.Failed(if (ar) "التلاوة أطول من ٥ دقائق. اختر آيات أقل." else "The recitation is longer than 5 minutes. Choose fewer ayat.")
                    return@launch
                }
                work = Work.Rendering(0)
                val out = File(QuranVideoMaker.outputDir(context), "Emran-${chapter?.transliteration?.replace(Regex("[^A-Za-z-]"), "") ?: surah}-$from-$to.mp4")
                QuranVideoMaker.render(context, spec, name, nameEn, files, segs, out) { work = Work.Rendering(it) }
                work = Work.Done(out)
            } catch (e: CancellationException) {
                work = Work.Idle
                throw e
            } catch (e: java.io.IOException) {
                work = Work.Failed(if (ar) "تعذّر تنزيل التلاوة. تأكد من الاتصال بالإنترنت وحاول مجددًا." else "Couldn't download the recitation. Check your connection and try again.")
            } catch (e: Exception) {
                android.util.Log.e("QuranVideo", "export failed", e)
                work = Work.Failed(if (ar) "تعذّر إنشاء الفيديو على هذا الهاتف." else "Couldn't make the video on this phone.")
            }
        }
    }

    // ---- live preview: the real clip looping (or the finished video), with the card drawn over it
    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE; volume = 0f }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val done = work as? Work.Done
    LaunchedEffect(clip.id, done?.file) {
        val uri = done?.file?.let { android.net.Uri.fromFile(it).toString() } ?: clip.uri
        player.setMediaItem(MediaItem.fromUri(uri))
        player.volume = if (done != null) 1f else 0f
        player.prepare()
        player.play()
    }

    // ---- hear a reciter read the chosen ayat before making anything
    var listeningTo by remember { mutableStateOf<String?>(null) }
    val listener = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) listeningTo = null }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    listeningTo = null
                    Toast.makeText(context, if (ar) "تعذّر تشغيل التلاوة. تأكد من الاتصال بالإنترنت." else "Couldn't play the recitation. Check your connection.", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { listener.release() } }
    fun listen(r: io.github.sammynashed.emran.data.audio.Reciter) {
        if (listeningTo == r.id) { listener.stop(); listeningTo = null; return }
        player.pause()
        listener.setMediaItems(QuranVideoMaker.previewUris(context, r, surah, from, to).map { MediaItem.fromUri(it) })
        listener.prepare(); listener.play()
        listeningTo = r.id
    }
    // A different range means a different preview: stop the old one.
    LaunchedEffect(surah, from, to) { if (listeningTo != null) { listener.stop(); listeningTo = null } }
    LaunchedEffect(busy) { if (busy && listeningTo != null) { listener.stop(); listeningTo = null } }

    var showSurah by remember { mutableStateOf(false) }
    var showFrom by remember { mutableStateOf(false) }
    var showTo by remember { mutableStateOf(false) }
    var showReciterPick by remember { mutableStateOf(false) }
    var showExplain by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = if (ar) "اصنع فيديو قرآني" else "Make a Qur'an video",
                subtitle = if (ar) "بتلاوة قارئك المفضل، على خلفية من الطبيعة" else "Your favourite reciter, over a scene from nature",
                onBack = { job?.cancel(); onBack() },
                modifier = Modifier.padding(horizontal = Space.screen),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll)
                    .padding(horizontal = Space.screen).padding(bottom = 120.dp),
            ) {
                // ---- preview
                val previewWidth = when (aspect) {
                    QuranVideoMaker.Aspect.PORTRAIT -> 0.62f
                    QuranVideoMaker.Aspect.SQUARE -> 0.86f
                    QuranVideoMaker.Aspect.LANDSCAPE -> 1f
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BoxWithConstraints(
                        Modifier.fillMaxWidth(previewWidth).aspectRatio(aspect.wr / aspect.hr.toFloat())
                            .clip(RoundedCornerShape(22.dp)).background(Color.Black)
                            .border(1.dp, sky.ink.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                            .clickable(enabled = done != null) { if (player.isPlaying) player.pause() else player.play() },
                    ) {
                        val wPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
                        val hPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }
                        AndroidView(
                            factory = { ctx -> TextureView(ctx).also { tv -> attachCropped(player, tv) } },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (done == null) {
                            val card by produceState<Bitmap?>(null, wPx, hPx, surah, from, to, explainId, showReciter, reciter.id, chapter?.name, lang) {
                                value = withContext(Dispatchers.Default) {
                                    if (wPx <= 0 || hPx <= 0 || chapter == null) return@withContext null
                                    runCatching {
                                        val verses = QuranRepository(context.applicationContext).getSurahVerses(surah).verses
                                        val seg = VideoCard.Segment(0, 1, from, verses.getOrNull(from - 1)?.text.orEmpty(),
                                            QuranVideoMaker.explainText(context, explainId, surah, from))
                                        VideoCard(context).render(wPx, hPx, QuranVideoMaker.style(spec, chapter.name, chapter.transliteration), seg, 1f)
                                    }.getOrNull()
                                }
                            }
                            card?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        }
                    }
                }
                Spacer(Modifier.height(Space.lg))

                // ---- result / progress
                WorkCard(work, ar, onCancel = { job?.cancel() }, onRetry = { work = Work.Idle })
                (work as? Work.Done)?.let { d ->
                    Row(Modifier.fillMaxWidth().padding(bottom = Space.md), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(if (ar) "حفظ" else "Save", {
                            scope.launch {
                                val ok = QuranVideoMaker.saveToGallery(context, d.file)
                                Toast.makeText(context, if (ok) (if (ar) "حُفظ في المعرض (Movies/Emran)" else "Saved to the gallery (Movies/Emran)")
                                else (if (ar) "استخدم «مشاركة» لحفظه على هذا الهاتف" else "Use Share to keep it on this phone"), Toast.LENGTH_LONG).show()
                            }
                        }, filled = true, modifier = Modifier.weight(1f))
                        // Outlined, so it reads as a button and not a caption (elder-friendly rule: no chrome-less buttons).
                        GlassButton(if (ar) "مشاركة" else "Share", {
                            context.startActivity(QuranVideoMaker.shareIntent(context, d.file, lang))
                        }, filled = false, modifier = Modifier.weight(1f).border(1.2.dp, sky.ink.copy(alpha = 0.45f), RoundedCornerShape(22.dp)))
                    }
                }

                // ---- 1. ayat and recitation
                GlassSectionLabel(if (ar) "١ · الآيات والتلاوة" else "1 · AYAT AND RECITATION")
                GlassGroup {
                    SettingsRow(Icons.Default.MenuBook, if (ar) "السورة" else "Surah",
                        { if (!busy) showSurah = true }, value = chapter?.let { if (ar) it.name else it.transliteration } ?: "…", minHeight = 50.dp)
                    GlassDivider()
                    SettingsRow(Icons.Default.FormatListNumbered, if (ar) "من الآية" else "From ayah",
                        { if (!busy) showFrom = true }, value = num(from, ar), minHeight = 50.dp)
                    GlassDivider()
                    SettingsRow(Icons.Default.FormatListNumbered, if (ar) "إلى الآية" else "To ayah",
                        { if (!busy) showTo = true }, value = num(to, ar), minHeight = 50.dp)
                    GlassDivider()
                    SettingsRow(Icons.Default.GraphicEq, if (ar) "القارئ" else "Reciter",
                        { if (!busy) showReciterPick = true }, value = if (ar) reciter.ar else reciter.en, minHeight = 50.dp,
                        trailing = { Spacer(Modifier.width(8.dp)); ListenButton(listeningTo == reciter.id, ar) { if (!busy) listen(reciter) } })
                }
                Text(
                    when {
                        estimateSec <= 0 -> ""
                        tooLong -> if (ar) "المدة نحو ${mmss(estimateSec, ar)}: الحد ٥ دقائق، اختر آيات أقل." else "About ${mmss(estimateSec, ar)}: the limit is 5 minutes, choose fewer ayat."
                        else -> if (ar) "المدة نحو ${mmss(estimateSec, ar)} · حتى ٥ دقائق. تُنزَّل الآيات غير المحمّلة فقط." else "About ${mmss(estimateSec, ar)} · up to 5 minutes. Only ayat you haven't downloaded are fetched."
                    },
                    color = if (tooLong) Color(0xFFFFB4A8) else sky.inkSoft, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                )

                // ---- 2. background
                Spacer(Modifier.height(Space.sm))
                GlassSectionLabel(if (ar) "٢ · الخلفية" else "2 · BACKGROUND")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoBackgrounds.CATEGORIES.forEach { c ->
                        val on = c.id == category
                        Box(
                            Modifier.clip(CircleShape)
                                .background(if (on) sky.ink.copy(alpha = 0.9f) else sky.ink.copy(alpha = 0.10f))
                                .border(1.dp, sky.ink.copy(alpha = if (on) 0f else 0.22f), CircleShape)
                                .clickable { category = c.id }
                                .heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(c.name(ar), color = if (on) Color(0xFF14161F) else sky.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(Space.md))
                val shown = VideoBackgrounds.ALL.filter { category == "all" || it.category == category }
                shown.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { c -> ClipTile(c, c.id == clip.id, ar, Modifier.weight(1f)) { if (!busy) clipId = c.id } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                // ---- 3. design
                Spacer(Modifier.height(Space.sm))
                GlassSectionLabel(if (ar) "٣ · التصميم" else "3 · DESIGN")
                GlassGroup {
                    SettingsRow(Icons.Default.AutoStories, if (ar) "التفسير تحت الآية" else "Under the ayah",
                        { if (!busy) showExplain = true },
                        value = QuranVideoMaker.EXPLAINS.first { it.id == explainId }.name(ar), minHeight = 50.dp)
                    GlassDivider()
                    SettingsRow(Icons.Default.Person, if (ar) "اسم القارئ" else "Reciter's name",
                        { if (!busy) showReciter = !showReciter },
                        value = onOff(showReciter, ar), minHeight = 50.dp, chevron = false)
                }

                // ---- 4. save
                Spacer(Modifier.height(Space.lg))
                GlassSectionLabel(if (ar) "٤ · احفظ الفيديو" else "4 · SAVE THE VIDEO")
                GlassSegmented(
                    options = listOf("9:16" to (if (ar) "طولي ٩:١٦" else "Tall 9:16"), "1:1" to (if (ar) "مربع ١:١" else "Square 1:1"), "16:9" to (if (ar) "عرضي ١٦:٩" else "Wide 16:9")),
                    selectedKey = aspect.key, onSelect = { if (!busy) aspectKey = it },
                )
                Spacer(Modifier.height(10.dp))
                GlassSegmented(
                    options = listOf("720" to (if (ar) "720p · حجم أخف" else "720p · smaller"), "1080" to (if (ar) "1080p · أوضح" else "1080p · sharper")),
                    selectedKey = quality.toString(), onSelect = { if (!busy) quality = it.toInt() },
                )
                Spacer(Modifier.height(Space.lg))
                GlassButton(
                    when {
                        busy -> if (ar) "جارٍ الإنشاء…" else "Making it…"
                        work is Work.Done -> if (ar) "أنشئ فيديو آخر" else "Make another"
                        else -> if (ar) "إنشاء الفيديو" else "Make the video"
                    },
                    {
                        if (busy || tooLong || chapter == null) return@GlassButton
                        if (work is Work.Done) work = Work.Idle
                        make()
                    },
                    filled = true, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    if (ar) "يُصنع الفيديو على هاتفك بصيغة MP4. ابقَ في هذه الصفحة حتى ينتهي."
                    else "The MP4 is made on your phone. Stay on this page until it's done.",
                    color = sky.inkSoft, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }

    // ---- pickers
    if (showSurah) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (surah - 3).coerceAtLeast(0))
        GlassDialog(onDismiss = { showSurah = false }, title = if (ar) "السورة" else "Surah") {
            LazyColumn(Modifier.heightIn(max = 420.dp), state = listState) {
                items(chapters, key = { it.id }) { c ->
                    ChoiceRow(
                        label = if (ar) "${num(c.id, true)}. ${c.name}" else "${c.id}. ${c.transliteration}",
                        selected = c.id == surah,
                        onClick = { surah = c.id; from = 1; to = minOf(c.total_verses, 5); showSurah = false },
                    )
                }
            }
        }
    }
    if (showFrom || showTo) {
        val picking = if (showFrom) from else to
        val range = if (showFrom) 1..ayahCount else from..ayahCount
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (picking - range.first - 3).coerceAtLeast(0))
        GlassDialog(onDismiss = { showFrom = false; showTo = false }, title = if (showFrom) (if (ar) "من الآية" else "From ayah") else (if (ar) "إلى الآية" else "To ayah")) {
            LazyColumn(Modifier.heightIn(max = 420.dp), state = listState) {
                items(range.toList()) { n ->
                    ChoiceRow(label = num(n, ar), selected = n == picking, onClick = {
                        if (showFrom) { from = n; if (to < n) to = n } else to = n
                        showFrom = false; showTo = false
                    })
                }
            }
        }
    }
    if (showReciterPick) {
        GlassDialog(
            onDismiss = { showReciterPick = false; listener.stop(); listeningTo = null },
            title = if (ar) "القارئ" else "Reciter",
            subtitle = if (ar) "اضغط ▶ لتسمع الآيات التي اخترتها بصوت كل قارئ" else "Tap ▶ to hear your chosen ayat in each voice",
        ) {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(reciters, key = { it.id }) { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChoiceRow(
                            label = if (ar) r.ar else r.en, selected = r.id == reciter.id,
                            onClick = { reciterId = r.id; showReciterPick = false; listener.stop(); listeningTo = null },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        ListenButton(listeningTo == r.id, ar) { listen(r) }
                    }
                }
            }
        }
    }
    if (showExplain) {
        GlassDialog(onDismiss = { showExplain = false }, title = if (ar) "التفسير تحت الآية" else "Explanation under the ayah") {
            QuranVideoMaker.EXPLAINS.forEach { e ->
                ChoiceRow(label = e.name(ar), selected = e.id == explainId, onClick = { explainId = e.id; showExplain = false })
            }
        }
    }
}

/** ▶ / ■ in a soft disc: plays the chosen ayat in a reciter's voice. 44 dp so it is easy to hit. */
@Composable
private fun ListenButton(playing: Boolean, ar: Boolean, onClick: () -> Unit) {
    val sky = LocalSky.current
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(sky.ink.copy(alpha = if (playing) 0.85f else 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (playing) (if (ar) "إيقاف" else "Stop") else (if (ar) "استمع" else "Listen"),
            tint = if (playing) Color(0xFF14161F) else sky.ink, modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun WorkCard(work: Work, ar: Boolean, onCancel: () -> Unit, onRetry: () -> Unit) {
    val sky = LocalSky.current
    if (work is Work.Idle) return
    GlassPanel(Modifier.fillMaxWidth().padding(bottom = Space.md), lens = false) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            val (line, fraction) = when (work) {
                is Work.Downloading -> (if (ar) "تنزيل التلاوة… ${num(work.done, ar)} من ${num(work.total, ar)}" else "Downloading the recitation… ${work.done} of ${work.total}") to
                    (work.done / work.total.coerceAtLeast(1).toFloat()) * 0.15f
                is Work.Rendering -> (if (ar) "إنشاء الفيديو… ${num(work.percent, ar)}٪" else "Making the video… ${work.percent}%") to
                    (0.15f + work.percent / 100f * 0.85f)
                is Work.Done -> (if (ar) "فيديوك جاهز ✓  اضغط عليه لتشغيله" else "Your video is ready ✓  Tap it to play") to 1f
                is Work.Failed -> work.message to 0f
                Work.Idle -> "" to 0f
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (work is Work.Done) Icon(Icons.Default.CheckCircle, null, tint = sky.ink, modifier = Modifier.size(20.dp).padding(end = 0.dp))
                if (work is Work.Done) Spacer(Modifier.width(8.dp))
                Text(line, color = sky.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (work is Work.Downloading || work is Work.Rendering) {
                    Text(if (ar) "إلغاء" else "Cancel", color = sky.inkSoft, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onCancel).padding(horizontal = 10.dp, vertical = 8.dp))
                }
                if (work is Work.Failed) {
                    Text(if (ar) "حسنًا" else "OK", color = sky.ink, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onRetry).padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }
            if (work is Work.Downloading || work is Work.Rendering) {
                val f by animateFloatAsState(fraction, label = "progress")
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.14f))) {
                    Box(Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).height(6.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.85f)))
                }
            }
        }
    }
}

@Composable
private fun ClipTile(clip: VideoBackgrounds.Clip, selected: Boolean, ar: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val sky = LocalSky.current
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(null, clip.id) {
        value = withContext(Dispatchers.IO) { VideoBackgrounds.thumbnail(context.applicationContext, clip) }
    }
    val shape = RoundedCornerShape(18.dp)
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(shape).background(sky.ink.copy(alpha = 0.08f))
                .border(BorderStroke(if (selected) 2.5.dp else 1.dp, sky.ink.copy(alpha = if (selected) 0.95f else 0.18f)), shape)
                .clickable(onClick = onClick),
        ) {
            thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (selected) Icon(
                Icons.Default.CheckCircle, null, tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp),
            ) else Icon(
                Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
            )
        }
        Text(
            clip.name(ar), color = sky.ink, fontSize = 14.sp, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

/** Plays [player] into [view] filling it like centre-crop (TextureView alone would stretch the picture). */
private fun attachCropped(player: ExoPlayer, view: TextureView) {
    player.setVideoTextureView(view)
    fun apply(size: VideoSize) {
        val vw = view.width.toFloat(); val vh = view.height.toFloat()
        if (size.width == 0 || size.height == 0 || vw == 0f || vh == 0f) return
        val videoAspect = size.width * size.pixelWidthHeightRatio / size.height
        val viewAspect = vw / vh
        val (sx, sy) = if (videoAspect > viewAspect) (videoAspect / viewAspect) to 1f else 1f to (viewAspect / videoAspect)
        view.setTransform(Matrix().apply { setScale(sx, sy, vw / 2f, vh / 2f) })
    }
    player.addListener(object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) = apply(videoSize)
    })
    view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply(player.videoSize) }
}

private fun num(n: Int, ar: Boolean) = if (ar) VideoCard.arabicDigits(n) else n.toString()
private fun mmss(sec: Int, ar: Boolean) = num(sec / 60, ar) + ":" + (if (ar) VideoCard.arabicDigits(sec % 60).padStart(2, '٠') else "%02d".format(sec % 60))
private fun onOff(on: Boolean, ar: Boolean) = if (on) (if (ar) "يظهر" else "Shown") else (if (ar) "مخفي" else "Hidden")
