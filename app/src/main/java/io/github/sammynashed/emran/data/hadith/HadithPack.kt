package io.github.sammynashed.emran.data.hadith

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.Inflater

/**
 * One hadith. [narrator] and [text] are the English; [arabic] is the source text.
 * [narrator] is empty for collections whose translation carries no separate
 * narrator line.
 */
class Hadith(
    val number: Int,
    val arabic: String,
    val narrator: String,
    val text: String
) {
    val english: String get() = if (narrator.isBlank()) text else "$narrator $text"
}

class HadithChapter(
    val id: Int,
    val count: Int,
    val firstNumber: Int,
    val lastNumber: Int,
    val titleAr: String,
    val titleEn: String,
    internal val offset: Long,
    internal val compressedLength: Int,
    internal val rawLength: Int
) {
    fun title(lang: String): String =
        if (lang == "ar") titleAr.ifBlank { titleEn } else titleEn.ifBlank { titleAr }
}

/** Where a pack's bytes live: inside the APK, or in a downloaded file. */
interface PackSource {
    fun readAt(offset: Long, length: Int): ByteArray
    fun close()
}

/**
 * Reads straight out of the APK. Needs the asset to be stored uncompressed
 * (`noCompress += "hdp"`); each chapter is already deflated inside the pack, so
 * having the APK compress it a second time would only cost CPU and gain nothing,
 * and would make [android.content.res.AssetManager.openFd] refuse the file.
 */
class AssetPackSource(context: Context, assetName: String) : PackSource {
    private val afd = context.assets.openFd(assetName)
    private val channel: FileChannel = FileInputStream(afd.fileDescriptor).channel
    private val base = afd.startOffset

    override fun readAt(offset: Long, length: Int): ByteArray = channel.readFully(base + offset, length)
    override fun close() {
        runCatching { channel.close() }
        runCatching { afd.close() }
    }
}

class FilePackSource(file: File) : PackSource {
    private val raf = RandomAccessFile(file, "r")
    private val channel: FileChannel = raf.channel

    override fun readAt(offset: Long, length: Int): ByteArray = channel.readFully(offset, length)
    override fun close() {
        runCatching { raf.close() }
    }
}

private fun FileChannel.readFully(position: Long, length: Int): ByteArray {
    val buf = ByteBuffer.allocate(length)
    var pos = position
    while (buf.hasRemaining()) {
        // Positional read: does not move the channel, so concurrent chapter loads
        // (search on one thread, reader on another) cannot trample each other.
        val n = read(buf, pos)
        if (n < 0) throw java.io.EOFException("pack truncated at $pos")
        pos += n
    }
    return buf.array()
}

/**
 * A `.hdp` pack (see tools/build_hadith_packs.py for the layout). Only the chapter
 * table is held in memory; a chapter is inflated when asked for.
 */
class HadithPack private constructor(
    val id: String,
    private val source: PackSource,
    val chapters: List<HadithChapter>,
    val hadithCount: Int
) {
    private val byId = chapters.associateBy { it.id }

    fun chapter(id: Int): HadithChapter? = byId[id]

    /** The chapter holding hadith [number], or null if the pack has no such number. */
    fun chapterOf(number: Int): HadithChapter? =
        chapters.firstOrNull { number in it.firstNumber..it.lastNumber }

    fun load(chapter: HadithChapter): List<Hadith> {
        val z = source.readAt(chapter.offset, chapter.compressedLength)
        val raw = ByteArray(chapter.rawLength)
        val inflater = Inflater(true)
        try {
            inflater.setInput(z)
            var done = 0
            while (done < raw.size) {
                val n = inflater.inflate(raw, done, raw.size - done)
                if (n == 0 && (inflater.finished() || inflater.needsInput())) break
                done += n
            }
            require(done == raw.size) { "chapter ${chapter.id} of $id is corrupt" }
        } finally {
            inflater.end()
        }
        return decode(raw, chapter.count)
    }

    fun close() = source.close()

    companion object {
        fun open(id: String, source: PackSource): HadithPack {
            val head = ByteBuffer.wrap(source.readAt(0, 14))
            val magic = ByteArray(4).also { head.get(it) }
            require(String(magic, Charsets.US_ASCII) == "EHP1") { "$id is not a hadith pack" }
            val chapterCount = head.short.toInt() and 0xFFFF
            val hadithCount = head.int
            val headerLength = head.int

            val h = ByteBuffer.wrap(source.readAt(0, headerLength))
            h.position(14)
            val list = ArrayList<HadithChapter>(chapterCount)
            repeat(chapterCount) {
                val cid = h.short.toInt() and 0xFFFF
                val n = h.short.toInt() and 0xFFFF
                val first = h.int
                val last = h.int
                val off = h.int.toLong() and 0xFFFFFFFFL
                val cl = h.int
                val rl = h.int
                val ar = h.utf()
                val en = h.utf()
                list += HadithChapter(cid, n, first, last, ar, en, off, cl, rl)
            }
            return HadithPack(id, source, list, hadithCount)
        }

        private fun ByteBuffer.utf(): String {
            val len = short.toInt() and 0xFFFF
            val b = ByteArray(len)
            get(b)
            return String(b, Charsets.UTF_8)
        }

        private class Cursor(val d: ByteArray) {
            var i = 0
            fun varint(): Int {
                var r = 0
                var s = 0
                while (true) {
                    val x = d[i++].toInt()
                    r = r or ((x and 0x7F) shl s)
                    s += 7
                    if (x and 0x80 == 0) return r
                }
            }

            fun str(): String {
                val n = varint()
                val s = String(d, i, n, Charsets.UTF_8)
                i += n
                return s
            }
        }

        private fun decode(raw: ByteArray, n: Int): List<Hadith> {
            val c = Cursor(raw)
            val nums = IntArray(n) { c.varint() }
            val ar = Array(n) { c.str() }
            val nar = Array(n) { c.str() }
            val txt = Array(n) { c.str() }
            return List(n) { Hadith(nums[it], ar[it], nar[it], txt[it]) }
        }
    }
}
