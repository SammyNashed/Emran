package io.github.sammynashed.emran.data.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import io.github.sammynashed.emran.data.AyahTafsir
import io.github.sammynashed.emran.data.TafsirLibrary
import io.github.sammynashed.emran.data.audio.AudioStore
import io.github.sammynashed.emran.data.audio.Reciter
import io.github.sammynashed.emran.data.repository.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * "اصنع فيديو": a short Qur'an video made entirely on the phone.
 *
 * 1. [prepareAudio] gathers one MP3 per ayah (already-downloaded surahs are used as they are; anything else is
 *    fetched from EveryAyah into the cache, only the ayat asked for), plus the basmala before a surah's first ayah.
 * 2. [segments] times each ayah from its file, so the text changes exactly when the reciter moves on.
 * 3. [render] lays copies of the looping nature clip end to end under the recitation and draws [VideoCard] over
 *    every frame (Media3 Transformer, hardware H.264/AAC).
 *
 * Nothing leaves the phone except the EveryAyah downloads the person started by pressing "Make video".
 */
@OptIn(UnstableApi::class)
object QuranVideoMaker {
    /** Longest recitation a video may hold; past this the export gets slow and the file too big to share. */
    const val MAX_US = 5 * 60 * 1_000_000L

    enum class Aspect(val key: String, val wr: Int, val hr: Int) {
        PORTRAIT("9:16", 9, 16), SQUARE("1:1", 1, 1), LANDSCAPE("16:9", 16, 9);

        /** Output size for a short side of [shortSide] px (720 or 1080); both even, as encoders want. */
        fun size(shortSide: Int): Pair<Int, Int> {
            val s = shortSide
            return when (this) {
                PORTRAIT -> s to (s * 16 / 9 / 2 * 2)
                SQUARE -> s to s
                LANDSCAPE -> (s * 16 / 9 / 2 * 2) to s
            }
        }

        companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: PORTRAIT }
    }

    /** Which explanation goes under each ayah. Long tafsirs (as-Sa'di) don't fit a frame, so they aren't offered. */
    /** [labelAr]/[labelEn] is the small title drawn above the text in the video. */
    class Explain(val id: String, val ar: String, val en: String, val labelAr: String = ar, val labelEn: String = en) {
        fun name(ar: Boolean) = if (ar) this.ar else en
    }

    const val EXPLAIN_OFF = "off"
    const val EXPLAIN_SAHEEH = "saheeh"
    val EXPLAINS = listOf(
        Explain(EXPLAIN_OFF, "بدون تفسير", "No explanation"),
        Explain("muyassar", "التفسير الميسر", "Muyassar (Arabic)", labelEn = "Tafsir Al-Muyassar"),
        Explain(TafsirLibrary.MUKHTASAR_AR, "المختصر في التفسير", "Mukhtasar (Arabic)", labelEn = "Tafsir Al-Mukhtasar"),
        Explain("jalalayn-ar", "تفسير الجلالين", "Jalalayn (Arabic)", labelEn = "Tafsir Al-Jalalayn"),
        Explain(EXPLAIN_SAHEEH, "الترجمة الإنجليزية", "Saheeh International", "Saheeh International", "Saheeh International"),
    )

    class Spec(
        val surah: Int,
        val from: Int,
        val to: Int,
        val reciter: Reciter,
        val clip: VideoBackgrounds.Clip,
        val explainId: String,
        val showReciter: Boolean,
        val aspect: Aspect,
        val shortSide: Int,
        /** The words around the ayah (title, range, reciter, signature) follow the app's language. */
        val lang: String,
    ) {
        /** A surah's first ayah gets the basmala in front, except al-Fatiha (it is ayah 1) and at-Tawbah. */
        val withBasmala get() = from == 1 && surah != 1 && surah != 9
    }

    // ------------------------------------------------------------------ audio

    private fun ayahUrl(r: Reciter, s: Int, a: Int) = "https://everyayah.com/data/${r.folder}/%03d%03d.mp3".format(s, a)

    private fun cacheFile(context: Context, r: Reciter, s: Int, a: Int) =
        File(context.cacheDir, "video_audio/${r.id}/%03d%03d.mp3".format(s, a))

    /** True when every file is already on the phone, so making the video needs no internet. */
    fun audioReady(context: Context, spec: Spec): Boolean =
        audioPlan(context, spec).all { it.second.exists() }

    private fun audioPlan(context: Context, spec: Spec): List<Pair<String, File>> {
        val r = spec.reciter
        val out = mutableListOf<Pair<String, File>>()
        if (spec.withBasmala) {
            val own = AudioStore.bismillahFile(context, r.id)
            out += ayahUrl(r, 1, 1) to (if (own.exists()) own else cacheFile(context, r, 1, 1))
        }
        for (a in spec.from..spec.to) {
            val own = AudioStore.ayahFile(context, r.id, spec.surah, a)
            out += ayahUrl(r, spec.surah, a) to (if (own.exists()) own else cacheFile(context, r, spec.surah, a))
        }
        return out
    }

    /** Every file the video needs, in order; downloads what is missing. [onProgress] gets (done, total). */
    suspend fun prepareAudio(context: Context, spec: Spec, onProgress: (Int, Int) -> Unit): List<File> = withContext(Dispatchers.IO) {
        val plan = audioPlan(context, spec)
        var done = 0
        plan.map { (url, file) ->
            if (!file.exists()) fetch(url, file)
            done++
            withContext(Dispatchers.Main) { onProgress(done, plan.size) }
            file
        }
    }

    private fun fetch(from: String, to: File) {
        to.parentFile?.mkdirs()
        val tmp = File(to.path + ".tmp")
        val c = URL(from).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000; c.readTimeout = 30_000
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode} for $from")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (tmp.length() < 1_000 || !tmp.renameTo(to)) throw IOException("bad file $from")
        } finally {
            c.disconnect(); tmp.delete()
        }
    }

    fun durationUs(f: File): Long {
        runCatching {
            val ex = MediaExtractor()
            try {
                ex.setDataSource(f.path)
                val fmt = ex.getTrackFormat(0)
                if (fmt.containsKey(MediaFormat.KEY_DURATION)) return fmt.getLong(MediaFormat.KEY_DURATION)
            } finally { ex.release() }
        }
        return runCatching {
            val r = MediaMetadataRetriever()
            r.setDataSource(f.path)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            r.release(); ms * 1000
        }.getOrDefault(0L)
    }

    // ------------------------------------------------------------------ text

    /** The text and explanation of each ayah, timed from the audio files ([files] in [prepareAudio] order). */
    suspend fun segments(context: Context, spec: Spec, files: List<File>): List<VideoCard.Segment> = withContext(Dispatchers.IO) {
        val repo = QuranRepository(context.applicationContext)
        val verses = repo.getSurahVerses(spec.surah).verses
        val basmala = if (spec.withBasmala) repo.getSurahVerses(1).verses.firstOrNull()?.text.orEmpty() else ""
        val ayat = (if (spec.withBasmala) listOf(0) else emptyList()) + (spec.from..spec.to).toList()
        var t = 0L
        ayat.mapIndexed { i, a ->
            val d = durationUs(files[i])
            val seg = VideoCard.Segment(
                startUs = t, endUs = t + d, ayah = a,
                text = if (a == 0) basmala else verses.getOrNull(a - 1)?.text.orEmpty(),
                explain = if (a == 0) null else explainText(context, spec.explainId, spec.surah, a),
            )
            t += d
            seg
        }
    }

    fun explainText(context: Context, id: String, s: Int, a: Int): String? = when (id) {
        EXPLAIN_OFF -> null
        EXPLAIN_SAHEEH -> AyahTafsir.get(context, s, a)?.translation?.takeIf { it.isNotBlank() }
        // A surah's first entry opens with its introduction (name, aims) before "[التفسير]": keep only the tafsir.
        else -> TafsirLibrary.get(context, id, s, a)?.text?.let { t -> t.substringAfter("[التفسير]", t).trim() }?.takeIf { it.isNotBlank() }
    }

    fun explainLabel(id: String, ar: Boolean): String? =
        EXPLAINS.firstOrNull { it.id == id && id != EXPLAIN_OFF }?.let { if (ar) it.labelAr else it.labelEn }

    /** The explanation a new user starts with: the English translation for English, Al-Muyassar for Arabic. */
    fun defaultExplain(lang: String) = if (lang == "ar") "muyassar" else EXPLAIN_SAHEEH

    // ------------------------------------------------------------------ render

    /** Draws the card over each frame; re-draws only when the ayah or its fade step changes. */
    private class CardOverlay(
        private val card: VideoCard,
        private val w: Int,
        private val h: Int,
        private val style: VideoCard.Style,
        private val segs: List<VideoCard.Segment>,
    ) : BitmapOverlay() {
        private var bmp: Bitmap? = null
        private var lastKey = -1

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val i = segs.indexOfLast { presentationTimeUs >= it.startUs }.coerceAtLeast(0)
            val s = segs[i]
            val fadeIn = ((presentationTimeUs - s.startUs) / FADE_IN_US.toFloat()).coerceIn(0f, 1f)
            val fadeOut = if (i == segs.lastIndex) 1f else ((s.endUs - presentationTimeUs) / FADE_OUT_US.toFloat()).coerceIn(0f, 1f)
            val step = (minOf(fadeIn, fadeOut) * 10).toInt() // 11 steps is smooth at 30 fps and keeps re-draws rare
            // Tafsirs often explain a few ayat together: then the explanation stays on screen while the ayah changes.
            val sameBefore = i > 0 && segs[i - 1].explain == s.explain
            val sameAfter = i < segs.lastIndex && segs[i + 1].explain == s.explain
            val exStep = (minOf(if (sameBefore) 1f else fadeIn, if (sameAfter) 1f else fadeOut) * 10).toInt()
            val key = (i * 16 + step) * 16 + exStep
            if (key != lastKey || bmp == null) {
                bmp = card.render(w, h, style, s, step / 10f, bmp, exStep / 10f)
                lastKey = key
            }
            return bmp!!
        }

        companion object {
            const val FADE_IN_US = 350_000L
            const val FADE_OUT_US = 250_000L
        }
    }

    fun style(spec: Spec, surahAr: String, surahEn: String): VideoCard.Style {
        val ar = spec.lang == "ar"
        val range = when {
            ar && spec.from == spec.to -> "الآية ${VideoCard.arabicDigits(spec.from)}"
            ar -> "الآيات ${VideoCard.arabicDigits(spec.from)} – ${VideoCard.arabicDigits(spec.to)}"
            spec.from == spec.to -> "Ayah ${spec.from}"
            else -> "Ayat ${spec.from}–${spec.to}"
        }
        return VideoCard.Style(
            surahTitle = if (ar) "سورة $surahAr" else "Surah $surahEn",
            rangeLine = range,
            explainLabel = explainLabel(spec.explainId, ar),
            reciterLine = if (!spec.showReciter) null else if (ar) "بصوت ${spec.reciter.ar}" else "Recited by ${spec.reciter.en}",
            signaturePrefix = if (ar) "صُنع بتطبيق" else "Made with",
            signatureName = if (ar) "عِمران" else "Emran",
        )
    }

    fun outputDir(context: Context) = File(context.cacheDir, "video").apply { mkdirs() }

    /**
     * Exports the video to [out]. Must be called from the main thread's scope (Transformer lives on a Looper);
     * [onProgress] gets 0..100. Cancelling the coroutine stops the export and deletes the half-written file.
     */
    suspend fun render(
        context: Context,
        spec: Spec,
        surahAr: String,
        surahEn: String,
        files: List<File>,
        segs: List<VideoCard.Segment>,
        out: File,
        onProgress: (Int) -> Unit,
    ) {
        val (w, h) = spec.aspect.size(spec.shortSide)
        val total = segs.last().endUs
        val clipUs = withContext(Dispatchers.IO) { VideoBackgrounds.durationUs(context, spec.clip) }

        // The clip loops seamlessly, so copies laid end to end are one long take; the last copy is cut to fit.
        val copies = ((total + clipUs - 1) / clipUs).toInt().coerceAtLeast(1)
        val video = (0 until copies).map { i ->
            val left = total - i * clipUs
            val item = if (left < clipUs) {
                MediaItem.Builder().setUri(spec.clip.uri)
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(left / 1000).build())
                    .build()
            } else MediaItem.fromUri(spec.clip.uri)
            EditedMediaItem.Builder(item).setRemoveAudio(true).build()
        }
        val audio = files.map { EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it))).setRemoveVideo(true).build() }

        val overlay = CardOverlay(VideoCard(context), w, h, style(spec, surahAr, surahEn), segs)
        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(video).build(),
            EditedMediaItemSequence.Builder(audio).build(),
        ).setEffects(
            Effects(
                emptyList(),
                listOf(
                    Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
                    OverlayEffect(listOf(overlay)),
                ),
            ),
        ).build()

        out.delete()
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            lateinit var poll: Runnable
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        main.removeCallbacks(poll)
                        onProgress(100)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        main.removeCallbacks(poll)
                        out.delete()
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()
            val holder = ProgressHolder()
            poll = Runnable {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress.coerceIn(0, 99))
                main.postDelayed(poll, 250)
            }
            transformer.start(composition, out.path)
            main.postDelayed(poll, 250)
            cont.invokeOnCancellation {
                main.post { main.removeCallbacks(poll); transformer.cancel(); out.delete() }
            }
        }
    }

    // ------------------------------------------------------------------ hand it over

    /** Copies the video into the gallery (Movies/Emran). Android 10+ only; older phones use Share. */
    suspend fun saveToGallery(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
        val r = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Emran")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = r.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return@withContext false
        runCatching {
            r.openOutputStream(uri)?.use { o -> file.inputStream().use { it.copyTo(o) } } ?: error("no stream")
            r.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            true
        }.getOrElse { r.delete(uri, null, null); false }
    }

    /** Shares the MP4 with the same caption as the Ayah of the Day card: "Get Emran" and the channel link. */
    fun shareIntent(context: Context, file: File, lang: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("video/mp4")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, io.github.sammynashed.emran.ui.share.ShareCard.downloadCaption(lang))
                .apply { clipData = android.content.ClipData.newRawUri("Emran", uri) }
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            null,
        )
    }

    /**
     * Where to hear [reciter] read ayat [from]..[to] of [surah] before making anything: the phone's own copy when
     * the surah is downloaded (or a video already fetched it), otherwise EveryAyah, streamed.
     */
    fun previewUris(context: Context, reciter: Reciter, surah: Int, from: Int, to: Int): List<String> =
        (from..to).map { a ->
            val own = AudioStore.ayahFile(context, reciter.id, surah, a)
            val cached = cacheFile(context, reciter, surah, a)
            when {
                own.exists() -> Uri.fromFile(own).toString()
                cached.exists() -> Uri.fromFile(cached).toString()
                else -> ayahUrl(reciter, surah, a)
            }
        }
}
