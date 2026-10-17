package io.github.sammynashed.emran.data.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloads a reciter's surahs in the background, only because the user asked (per surah from the
 * reader, or the whole Qur'an from the Reciters page). It keeps going when Emran is closed, shows
 * its progress as a quiet notification, and resumes from where it stopped if the network drops.
 */
class AudioDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val r = Reciters.ALL.firstOrNull { it.id == inputData.getString(KEY_RECITER) } ?: return Result.failure()
        val surahs = (inputData.getIntArray(KEY_SURAHS) ?: return Result.failure())
            .filter { it in 1..114 && !AudioStore.isInstalled(applicationContext, r.id, it) }
        if (surahs.isEmpty()) return Result.success()
        val total = surahs.sumOf { AudioStore.AYAH_COUNT[it - 1] }
        val done = AtomicInteger(0)
        val ar = applicationContext.getSharedPreferences("emran_settings", Context.MODE_PRIVATE)
            .getString("current_language", "en") == "ar"
        runCatching { setForeground(foreground(r, ar, 0, total)) }
        return try {
            for (s in surahs) {
                AudioStore.downloadSurah(applicationContext, r, s) {
                    val n = done.incrementAndGet()
                    if (n % 5 == 0 || n == total) {
                        setProgressAsync(workDataOf(KEY_DONE to n, KEY_TOTAL to total, KEY_SURAH to s))
                        runCatching { notify(r, ar, n, total) }
                    }
                }
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private fun notification(r: Reciter, ar: Boolean, done: Int, total: Int) =
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(io.github.sammynashed.emran.R.drawable.ic_stat_emran)
            .setContentTitle(if (ar) "تنزيل تلاوة ${r.ar}" else "Downloading ${r.en}")
            .setContentText(if (total > 0) "${done * 100 / total}%" else "")
            .setProgress(total, done, total == 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun foreground(r: Reciter, ar: Boolean, done: Int, total: Int): ForegroundInfo {
        ensureChannel(applicationContext, ar)
        val n = notification(r, ar, done, total)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, n)
    }

    private fun notify(r: Reciter, ar: Boolean, done: Int, total: Int) {
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(r, ar, done, total))
    }

    companion object {
        private const val CHANNEL = "quran_audio_downloads"
        private const val NOTIFICATION_ID = 7301
        private const val KEY_RECITER = "reciter"
        private const val KEY_SURAHS = "surahs"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_SURAH = "surah"

        private fun workName(reciter: String) = "quran_audio_$reciter"

        private fun ensureChannel(context: Context, ar: Boolean) {
            if (Build.VERSION.SDK_INT < 26) return
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, if (ar) "تنزيل التلاوات" else "Recitation downloads", NotificationManager.IMPORTANCE_LOW),
            )
        }

        /** Queues [surahs] for [r]; asking again while one runs adds to the queue. */
        fun enqueue(context: Context, r: Reciter, surahs: List<Int>) {
            val req = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
                .setInputData(workDataOf(KEY_RECITER to r.id, KEY_SURAHS to surahs.toIntArray()))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(r.id), ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        fun cancel(context: Context, r: Reciter) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(r.id))
        }

        /** Progress of [r]'s downloads: (done, total) while running or waiting, null when idle. */
        fun progress(context: Context, r: Reciter): Flow<Pair<Int, Int>?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(r.id)).map { infos ->
                val active = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    ?: infos.firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                    ?: return@map null
                active.progress.getInt(KEY_DONE, 0) to active.progress.getInt(KEY_TOTAL, 0)
            }

        /** True while a surah of [r] is waiting or downloading. */
        fun surahPending(context: Context, r: Reciter, surah: Int): Flow<Boolean> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(r.id)).map { infos ->
                infos.any { !it.state.isFinished }
            }
    }
}
