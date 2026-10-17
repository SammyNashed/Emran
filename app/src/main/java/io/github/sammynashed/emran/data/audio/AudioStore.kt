package io.github.sammynashed.emran.data.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloaded Quran audio: `files/audio/<reciter>/<SSS>/<AAA>.mp3`, one file per ayah, plus the
 * reciter's Bismillah. A surah's folder only appears once every ayah of it has arrived (it fills
 * under a `.part` name and is renamed), so a half-finished download never plays, and a broken one
 * resumes where it stopped.
 */
object AudioStore {
    /** Ayat per surah, in the numbering EveryAyah and the bundled Qur'an share. */
    val AYAH_COUNT = intArrayOf(7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6)

    /** About 77,400 words in the muṣḥaf; with a reciter's bytes per word it gives a size estimate. */
    private const val QURAN_WORDS = 77_430L

    private val _changes = MutableStateFlow(0)
    /** Bumped whenever audio is added or removed, so screens re-read what is installed. */
    val changes: StateFlow<Int> = _changes
    fun notifyChanged() { _changes.value++ }

    private fun root(context: Context) = File(context.filesDir, "audio")
    private fun reciterDir(context: Context, reciter: String) = File(root(context), reciter)
    fun surahDir(context: Context, reciter: String, surah: Int) = File(reciterDir(context, reciter), "%03d".format(surah))
    fun ayahFile(context: Context, reciter: String, surah: Int, ayah: Int) =
        File(surahDir(context, reciter, surah), "%03d.mp3".format(ayah))
    fun bismillahFile(context: Context, reciter: String) = File(reciterDir(context, reciter), "bismillah.mp3")

    fun isInstalled(context: Context, reciter: String, surah: Int) = surahDir(context, reciter, surah).isDirectory

    fun installedSurahs(context: Context, reciter: String): List<Int> =
        reciterDir(context, reciter).listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d{3}")) }
            ?.mapNotNull { it.name.toIntOrNull() }?.sorted().orEmpty()

    fun bytesUsed(context: Context, reciter: String): Long =
        reciterDir(context, reciter).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun remove(context: Context, reciter: String) {
        reciterDir(context, reciter).deleteRecursively()
        notifyChanged()
    }

    fun wholeQuranBytes(r: Reciter) = r.bytesPerWord * QURAN_WORDS

    /** A surah's size from its word count (the reader has the text at hand). */
    fun surahBytes(r: Reciter, words: Int) = r.bytesPerWord.toLong() * words

    private fun url(r: Reciter, surah: Int, ayah: Int) =
        "https://everyayah.com/data/${r.folder}/%03d%03d.mp3".format(surah, ayah)

    /**
     * Downloads one surah (a few files at a time) and reports each finished ayah. Files already
     * fetched by an earlier, interrupted try are kept. Throws when the network fails.
     */
    suspend fun downloadSurah(context: Context, r: Reciter, surah: Int, onAyah: () -> Unit) = withContext(Dispatchers.IO) {
        if (isInstalled(context, r.id, surah)) return@withContext
        val bism = bismillahFile(context, r.id)
        if (!bism.exists()) { bism.parentFile?.mkdirs(); fetch(url(r, 1, 1), bism) }
        val part = File(surahDir(context, r.id, surah).path + ".part").apply { mkdirs() }
        val gate = Semaphore(4)
        coroutineScope {
            (1..AYAH_COUNT[surah - 1]).map { a ->
                async {
                    gate.withPermit {
                        ensureActive()
                        val f = File(part, "%03d.mp3".format(a))
                        if (!f.exists()) fetch(url(r, surah, a), f)
                        onAyah()
                    }
                }
            }.awaitAll()
        }
        if (!part.renameTo(surahDir(context, r.id, surah))) throw IOException("could not finish surah $surah")
        notifyChanged()
    }

    /** One file, written beside its final name and renamed, so a cut connection leaves nothing half-done. */
    private fun fetch(from: String, to: File) {
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
}
