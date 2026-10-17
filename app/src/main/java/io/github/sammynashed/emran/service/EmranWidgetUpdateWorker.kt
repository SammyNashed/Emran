package io.github.sammynashed.emran.service

import android.content.Context

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.sammynashed.emran.glance.WidgetUpdater
import io.github.sammynashed.emran.glance.WidgetDebugLogger

class EmranWidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        WidgetDebugLogger.d("EmranWidgetUpdateWorker.doWork: entered | thread=${Thread.currentThread().name}")
        try {
            WidgetUpdater.updateAllWidgets(applicationContext)
            WidgetDebugLogger.d("EmranWidgetUpdateWorker.doWork: exited successfully")
            return Result.success()
        } catch (e: Exception) {
            WidgetDebugLogger.e("EmranWidgetUpdateWorker.doWork: exited with error", e)
            return Result.retry()
        }
    }
}
