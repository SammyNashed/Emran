package io.github.sammynashed.emran.recitation

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Fetches the optional recitation model, with pause, resume and cancel.
 *
 * ## Why this is not `DownloadManager`
 *
 * The first version handed the transfer to Android's `DownloadManager`. Two
 * things made that untenable:
 *
 *  1. It needs `android.permission.INTERNET` from the *calling* app anyway -
 *     the system process moves the bytes, but the app writes the provider row.
 *     The permission is declared now, so this is no longer the blocker it was.
 *  2. It has no pause and no resume. It also silently parks a transfer -
 *     waiting for a network it deems acceptable, waiting for roaming to end -
 *     and reports that as "running, 0 bytes". A download stuck at 0% with no
 *     explanation is exactly the failure this feature must not have.
 *
 * So the transfer happens here, over a plain `HttpURLConnection`, which gives
 * the three things a normal download has: a real byte count, a pause that
 * keeps what already arrived, and a cancel that throws it away.
 *
 * ## Resume
 *
 * Pausing closes the connection but keeps the partial file. Resuming re-opens
 * the request with `Range: bytes=<size>-` and appends. A server that ignores
 * the range and answers 200 instead of 206 is handled by starting over, so a
 * mirror without range support still works - it is just not resumable.
 *
 * Whichever route the bytes take, the file is installed only after
 * [RecitationAssessor.installFrom] matches its SHA-256, so a resumed transfer
 * cannot smuggle in a different model.
 */
class ModelDownloader(private val context: Context) {

    sealed interface Progress {
        /** Asked, not yet answered. No percentage would be honest yet. */
        data object Connecting : Progress

        /** Bytes are moving. [total] is -1 when the server did not say. */
        data class Running(val fraction: Float, val bytes: Long, val total: Long) : Progress

        /** Held by the user; [bytes] are on disk and will be resumed from. */
        data class Paused(val fraction: Float, val bytes: Long, val total: Long) : Progress

        data object Verifying : Progress
        data object Installed : Progress

        /** Cancelled by the user; the partial file has been deleted. */
        data object Cancelled : Progress

        data class Failed(val reason: Reason) : Progress
    }

    /**
     * Why a download ended badly.
     *
     * These are separate because they need different things from the user:
     * [UNREACHABLE] means check the connection, [INTERRUPTED] means simply try
     * again, [SERVER] means the file is not where Emran expects it and no
     * amount of retrying will help, and [VERIFY] means what arrived was not
     * the model. Collapsing them into one message is what turned the first
     * failure into an unexplained flash.
     */
    enum class Reason { UNREACHABLE, INTERRUPTED, SERVER, VERIFY }

    @Volatile private var paused = false

    @Volatile private var cancelled = false

    /**
     * The connection currently in flight, so it can be torn down from outside.
     *
     * Flags alone are not enough. A flag is only read where the code looks at
     * it, and the code spends most of a bad download parked inside `connect()`
     * or a `read()` that will not return for tens of seconds. Through all of
     * that a cooperative pause or cancel does nothing whatsoever, which is
     * exactly how a stalled transfer ends up showing a frozen bar above two
     * dead buttons. Closing the socket makes the blocked call throw at once.
     */
    @Volatile private var live: HttpURLConnection? = null

    /** Tear down the in-flight connection so a blocked call returns now. */
    private fun abortLive() {
        val doomed = live ?: return
        live = null
        // disconnect() can itself block, and the caller here is the UI thread
        // reacting to a button, so it never runs inline.
        Thread { runCatching { doomed.disconnect() } }.start()
    }

    /** Hold the transfer. What has arrived stays on disk. */
    fun pause() {
        paused = true
        abortLive()
    }

    /** Continue a held transfer from where it stopped. */
    fun resume() {
        paused = false
    }

    /** Abandon the transfer and discard the partial file. */
    fun cancel() {
        cancelled = true
        paused = false
        abortLive()
    }

    /** True while the user has the transfer held. */
    val isPaused: Boolean get() = paused

    /** Where the partial lands; app-scoped, so no storage permission. */
    private val stagingFile: File
        get() = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            STAGING_NAME,
        )

    /**
     * Run the download, emitting progress until it installs, fails, or the
     * user cancels.
     *
     * @param cancelOnClose delete the partial if collection stops. True for the
     *   UI, where walking away should not leave 31 MB half-written. False for
     *   headless checks.
     */
    fun download(
        assessor: RecitationAssessor,
        cancelOnClose: Boolean = true,
    ): Flow<Progress> = flow {
        val file = stagingFile
        file.parentFile?.mkdirs()
        // A fresh start never resumes a partial left by an earlier attempt: its
        // provenance is unknown, and 31 MB spent to fail verification is worse
        // than 31 MB spent to succeed. Resume spans pause/resume within one
        // collection, nothing wider.
        file.delete()

        paused = false
        cancelled = false
        live = null

        var total = -1L
        var got = 0L
        var announcedPause = false

        try {
            loop@ while (true) {
                currentCoroutineContext().ensureActive()

                if (cancelled) {
                    file.delete()
                    emit(Progress.Cancelled)
                    return@flow
                }

                if (paused) {
                    if (!announcedPause) {
                        announcedPause = true
                        emit(Progress.Paused(fractionOf(got, total), got, total))
                    }
                    delay(IDLE_MS)
                    continue@loop
                }
                announcedPause = false

                got = if (file.exists()) file.length() else 0L
                if (total > 0 && got >= total) break@loop

                // "Connecting", not "0%". Announcing a percentage before the
                // server has answered is what made a hung connect look like a
                // download frozen at zero.
                if (got == 0L) {
                    emit(Progress.Connecting)
                } else {
                    emit(Progress.Running(fractionOf(got, total), got, total))
                }

                val conn = try {
                    open(got).also { live = it }
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    // A socket we closed on purpose throws here too; that is a
                    // pause or a cancel, not a failure.
                    if (cancelled || paused) continue@loop
                    Log.w(TAG, "connect to ${RecitationAssessor.MODEL_URL} failed", e)
                    file.delete()
                    emit(Progress.Failed(Reason.UNREACHABLE))
                    return@flow
                }

                var appending = got > 0
                try {
                    val code = try {
                        conn.responseCode
                    } catch (e: Throwable) {
                        currentCoroutineContext().ensureActive()
                        if (cancelled || paused) continue@loop
                        Log.w(TAG, "no response header", e)
                        file.delete()
                        emit(
                            Progress.Failed(
                                if (got > 0) Reason.INTERRUPTED else Reason.UNREACHABLE,
                            ),
                        )
                        return@flow
                    }
                    when (code) {
                        HttpURLConnection.HTTP_PARTIAL -> {
                            val stated =
                                totalFromContentRange(conn.getHeaderField("Content-Range"))
                            if (stated > 0) total = stated
                        }
                        HttpURLConnection.HTTP_OK -> {
                            // The server ignored the range; start over.
                            appending = false
                            got = 0L
                            total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                        }
                        RANGE_NOT_SATISFIABLE -> break@loop // every byte is already here
                        else -> {
                            Log.w(TAG, "unexpected HTTP $code")
                            file.delete()
                            emit(Progress.Failed(Reason.SERVER))
                            return@flow
                        }
                    }

                    emit(Progress.Running(fractionOf(got, total), got, total))

                    val stopped = try {
                        conn.inputStream.use { input ->
                            FileOutputStream(file, appending).use { out ->
                                pump(input, out, got, total) { bytes ->
                                    got = bytes
                                    emit(Progress.Running(fractionOf(bytes, total), bytes, total))
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        currentCoroutineContext().ensureActive()
                        if (cancelled || paused) {
                            Stop.USER
                        } else {
                            Log.w(TAG, "stream failed at $got of $total", e)
                            Stop.ERROR
                        }
                    }
                    if (stopped == Stop.USER) {
                        got = if (file.exists()) file.length() else 0L
                        continue@loop
                    }
                    if (stopped == Stop.ERROR) {
                        currentCoroutineContext().ensureActive()
                        file.delete()
                        emit(Progress.Failed(Reason.INTERRUPTED))
                        return@flow
                    }
                } finally {
                    live = null
                    runCatching { conn.disconnect() }
                }

                got = file.length()
                // Unknown total: a clean end of stream is the end of the file.
                if (total <= 0 || got >= total) break@loop
            }

            if (cancelled) {
                file.delete()
                emit(Progress.Cancelled)
                return@flow
            }

            emit(Progress.Verifying)
            val ok = assessor.installFrom(file)
            file.delete()
            emit(if (ok) Progress.Installed else Progress.Failed(Reason.VERIFY))
        } finally {
            // Collection stopped - cancelled, sheet closed, or coroutine torn
            // down. A partial left behind would later be read as a model.
            abortLive()
            if (cancelOnClose && !assessor.isInstalled && file.exists()) file.delete()
        }
    }.flowOn(Dispatchers.IO)

    private enum class Stop { DONE, USER, ERROR }

    /**
     * Copy until the stream ends or the user intervenes, reporting no more
     * often than [EMIT_MS] so the bar animates without flooding recomposition.
     */
    private suspend inline fun pump(
        input: InputStream,
        out: FileOutputStream,
        startAt: Long,
        total: Long,
        crossinline onBytes: suspend (Long) -> Unit,
    ): Stop {
        val buffer = ByteArray(BUFFER)
        var bytes = startAt
        var lastEmit = 0L
        while (true) {
            if (cancelled || paused) return Stop.USER
            val read = try {
                input.read(buffer)
            } catch (e: Throwable) {
                // Pausing closes the socket underneath this read, so the throw
                // is the pause arriving, not the network failing. Checking the
                // flags only at the top of the loop loses that race every time:
                // the read is already blocked when the socket goes, and the
                // loop never gets another turn. That is what turned a pause
                // into "the connection dropped partway through".
                return if (cancelled || paused) Stop.USER else Stop.ERROR
            }
            if (read < 0) return if (cancelled || paused) Stop.USER else Stop.DONE
            try {
                out.write(buffer, 0, read)
            } catch (e: Throwable) {
                return if (cancelled || paused) Stop.USER else Stop.ERROR
            }
            bytes += read
            val now = System.currentTimeMillis()
            if (now - lastEmit >= EMIT_MS || (total > 0 && bytes >= total)) {
                lastEmit = now
                onBytes(bytes)
            }
        }
    }

    private fun open(from: Long): HttpURLConnection =
        (URL(RecitationAssessor.MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            instanceFollowRedirects = true
            // Ranges are byte ranges of the stored file; a transfer encoding
            // would make the offsets we resume from meaningless.
            setRequestProperty("Accept-Encoding", "identity")
            if (from > 0) setRequestProperty("Range", "bytes=$from-")
            connect()
        }

    private fun fractionOf(bytes: Long, total: Long): Float =
        if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f

    /** `bytes 1024-30999999/31000000` -> 31000000. */
    private fun totalFromContentRange(header: String?): Long =
        header?.substringAfter('/', "")?.trim()?.toLongOrNull() ?: -1L

    private companion object {
        const val TAG = "RATL-DL"
        const val STAGING_NAME = "ratl-model.part"
        const val BUFFER = 64 * 1024
        const val EMIT_MS = 100L
        const val IDLE_MS = 120L
        const val CONNECT_MS = 15_000
        const val READ_MS = 30_000
        const val RANGE_NOT_SATISFIABLE = 416
    }
}
