package io.github.sammynashed.emran.data.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * The nature clips a Qur'an video can sit on. They ship inside the app (assets/video_bg/), a few hundred KB each:
 * a downloadable pack would come from GitHub's file servers, which Syrian users can't reach without a VPN.
 *
 * Each clip is muted and loops without a visible seam (made with ffmpeg: the first two seconds are faded over the
 * tail; a clip whose sun climbs keeps one frozen sky over its moving clouds instead), so the renderer can simply
 * lay copies end to end for as long as the recitation lasts.
 */
object VideoBackgrounds {
    class Clip(val id: String, val ar: String, val en: String, val category: String) {
        val asset get() = "video_bg/$id.mp4"
        val uri get() = "asset:///$asset"
        fun name(ar: Boolean) = if (ar) this.ar else en
    }

    class Category(val id: String, val ar: String, val en: String) {
        fun name(ar: Boolean) = if (ar) this.ar else en
    }

    val CATEGORIES = listOf(
        Category("all", "الكل", "All"),
        Category("sun", "شروق وغروب", "Sunrise & sunset"),
        Category("water", "ماء وشلالات", "Water"),
        Category("sky", "سماء وغيوم", "Sky & clouds"),
    )

    val ALL = listOf(
        Clip("desert-sunset", "غروب الصحراء", "Desert sunset", "sun"),
        Clip("cloud-sea-sunrise", "شروق فوق الغيوم", "Sunrise over the clouds", "sun"),
        Clip("misty-lake", "بحيرة الضباب", "Misty lake", "water"),
        Clip("jungle-waterfall", "شلال الغابة", "Forest waterfall", "water"),
        Clip("beach-waves", "موج الشاطئ", "Beach waves", "water"),
        Clip("drifting-clouds", "غيوم عابرة", "Drifting clouds", "sky"),
    )

    fun byId(id: String?) = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /** A still from the clip for the picker grid, cached after the first time. Call off the main thread. */
    fun thumbnail(context: Context, clip: Clip): Bitmap? {
        val f = File(context.cacheDir, "video_bg_thumbs/${clip.id}.jpg")
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { return it }
        return runCatching {
            val r = MediaMetadataRetriever()
            context.assets.openFd(clip.asset).use { fd -> r.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            val frame = r.getFrameAtTime(1_500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            r.release()
            frame?.let { full ->
                val w = 270
                val small = Bitmap.createScaledBitmap(full, w, full.height * w / full.width, true)
                f.parentFile?.mkdirs()
                f.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 86, it) }
                small
            }
        }.getOrNull()
    }

    /** Length of one copy of the clip in microseconds. */
    fun durationUs(context: Context, clip: Clip): Long = runCatching {
        val r = MediaMetadataRetriever()
        context.assets.openFd(clip.asset).use { fd -> r.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 8000L
        r.release()
        ms * 1000
    }.getOrDefault(8_000_000L)
}
