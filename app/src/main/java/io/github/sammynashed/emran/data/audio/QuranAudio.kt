package io.github.sammynashed.emran.data.audio

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.sammynashed.emran.service.QuranAudioService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * What screens use to play recitations, and what is playing right now. The playlist holds one
 * item per ayah, surah after surah (the "queue"); ayah 0 is the Bismillah that opens a surah (not
 * an ayah of it, so nothing is marked on the page while it plays).
 */
object QuranAudio {
    data class Now(val reciter: String, val surah: Int, val ayah: Int, val playing: Boolean)

    /** Off, the ayah again and again, or the surah again and again (for memorising). */
    enum class Repeat { OFF, AYAH, SURAH }

    /** The ayah sounding now (or paused on); null when nothing is loaded. */
    val state = MutableStateFlow<Now?>(null)
    val repeat = MutableStateFlow(Repeat.OFF)

    private var controller: MediaController? = null
    /** The connected controller, for screens that show position and the queue. */
    fun controllerOrNull(): MediaController? = controller?.takeIf { it.isConnected }

    fun parse(mediaId: String): Now? {
        val p = mediaId.split(':')
        if (p.size != 3) return null
        return Now(p[0], p[1].toIntOrNull() ?: return null, p[2].toIntOrNull() ?: return null, playing = false)
    }

    suspend fun connect(context: Context): MediaController {
        controllerOrNull()?.let { return it }
        val app = context.applicationContext
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, QuranAudioService::class.java))).buildAsync()
        return suspendCancellableCoroutine { cont ->
            future.addListener({
                runCatching { future.get() }
                    .onSuccess {
                        controller = it
                        it.repeatMode = if (repeat.value == Repeat.AYAH) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        cont.resume(it)
                    }
                    .onFailure { cont.resumeWithException(it) }
            }, ContextCompat.getMainExecutor(app))
            cont.invokeOnCancellation { future.cancel(true) }
        }
    }

    /** The playlist items for one surah, from [fromAyah] to its end (Bismillah first when starting at the top). */
    private fun itemsFor(context: Context, r: Reciter, surah: Int, surahName: String, fromAyah: Int = 1) = buildList {
        if (fromAyah <= 1 && surah != 1 && surah != 9) {
            val b = AudioStore.bismillahFile(context, r.id)
            if (b.exists()) add(item(r, surah, 0, b, surahName))
        }
        for (a in fromAyah.coerceAtLeast(1)..AudioStore.AYAH_COUNT[surah - 1]) {
            add(item(r, surah, a, AudioStore.ayahFile(context, r.id, surah, a), surahName))
        }
    }

    /** Plays [surah] by [r] from [fromAyah]; it replaces the queue. The surah must be downloaded. */
    suspend fun play(context: Context, r: Reciter, surah: Int, surahName: String, fromAyah: Int = 1) {
        val c = connect(context)
        c.setMediaItems(itemsFor(context, r, surah, surahName, fromAyah))
        c.prepare()
        c.play()
    }

    /** Adds a downloaded surah to the end of the queue (starts playing if nothing was). */
    suspend fun enqueue(context: Context, r: Reciter, surah: Int, surahName: String) {
        val c = connect(context)
        if (c.mediaItemCount == 0) { play(context, r, surah, surahName); return }
        c.addMediaItems(itemsFor(context, r, surah, surahName))
    }

    /** The surahs in the queue, in order (each once). */
    fun queueSurahs(): List<Int> {
        val c = controllerOrNull() ?: return emptyList()
        val out = mutableListOf<Int>()
        for (i in 0 until c.mediaItemCount) {
            val s = parse(c.getMediaItemAt(i).mediaId)?.surah ?: continue
            if (out.lastOrNull() != s) out += s
        }
        return out
    }

    /** Playlist indices of [surah]'s items in the queue, or empty. */
    fun indicesOf(surah: Int): IntRange {
        val c = controllerOrNull() ?: return IntRange.EMPTY
        var first = -1; var last = -1
        for (i in 0 until c.mediaItemCount) {
            if (parse(c.getMediaItemAt(i).mediaId)?.surah == surah) { if (first < 0) first = i; last = i }
            else if (first >= 0) break
        }
        return if (first < 0) IntRange.EMPTY else first..last
    }

    /** Jumps to the start of [surah] in the queue. */
    fun playSurahInQueue(surah: Int) {
        val c = controllerOrNull() ?: return
        val r = indicesOf(surah)
        if (!r.isEmpty()) { c.seekTo(r.first, 0); c.play() }
    }

    /** Removes [surah] from the queue (stops if it was the only one). */
    fun removeFromQueue(surah: Int) {
        val c = controllerOrNull() ?: return
        val r = indicesOf(surah)
        if (r.isEmpty()) return
        if (queueSurahs().size <= 1) { stop(); return }
        c.removeMediaItems(r.first, r.last + 1)
    }

    fun setRepeat(mode: Repeat) {
        repeat.value = mode
        controllerOrNull()?.repeatMode = if (mode == Repeat.AYAH) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    private fun item(r: Reciter, surah: Int, ayah: Int, file: File, surahName: String) = MediaItem.Builder()
        .setMediaId("${r.id}:$surah:$ayah")
        .setUri(Uri.fromFile(file))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(if (ayah == 0) "سورة $surahName" else "سورة $surahName · $ayah")
                .setArtist(r.ar)
                .build(),
        )
        .build()

    fun togglePause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    fun previous() { controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() } }
    fun stop() { controller?.run { stop(); clearMediaItems() }; state.value = null }

    // ---- Lengths, so the seekbar can span a whole surah rather than one ayah. ----

    private val lengths = ConcurrentHashMap<String, Long>()

    /** Length of one audio file in ms (read once, then remembered). */
    fun lengthOf(file: File): Long = lengths.getOrPut(file.path) {
        runCatching {
            val m = MediaMetadataRetriever()
            try { m.setDataSource(file.path); m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L } finally { m.release() }
        }.getOrDefault(0L)
    }

    /** Lengths of the playlist items at [range], measured off the main thread. */
    suspend fun lengths(context: Context, range: IntRange): List<Long> {
        val c = controllerOrNull() ?: return emptyList()
        // From the item's id, not its uri: a controller may not be handed the uri back.
        val files = range.map { i ->
            parse(c.getMediaItemAt(i).mediaId)?.let { n ->
                if (n.ayah == 0) AudioStore.bismillahFile(context, n.reciter) else AudioStore.ayahFile(context, n.reciter, n.surah, n.ayah)
            }
        }
        return withContext(Dispatchers.IO) { files.map { f -> f?.let(::lengthOf) ?: 0L } }
    }
}
