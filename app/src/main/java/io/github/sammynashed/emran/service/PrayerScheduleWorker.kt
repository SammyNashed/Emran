package io.github.sammynashed.emran.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.sammynashed.emran.data.PrayerTimeCalculator
import io.github.sammynashed.emran.data.local.db.AppDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class PrayerScheduleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("PrayerScheduleWorker", "Scheduling prayer alarms...")
        try {
            val database = AppDatabase.getDatabase(applicationContext)
            val dao = database.prayerDao()

            // Fetch coordinates from SharedPreferences
            val preferences = applicationContext.getSharedPreferences("emran_settings", Context.MODE_PRIVATE)
            val lat = preferences.getFloat("latitude", 36.2021f).toDouble()
            val lon = preferences.getFloat("longitude", 37.1343f).toDouble()
            val zoneIdString = preferences.getString("zone_id", null)
                ?: PrayerTimeCalculator.DEFAULT_ZONE_ID
            val zone = PrayerTimeCalculator.safeZone(zoneIdString)

            val methodStr = preferences.getString("calculation_method", "MWL") ?: "MWL"
            val schoolStr = preferences.getString("asr_school", "SHAFII") ?: "SHAFII"
            val ruleStr = preferences.getString("high_latitude_rule", "AUTOMATIC") ?: "AUTOMATIC"

            // Fetch custom user-set offsets
            val offsets = dao.getAllOffsets().first().associate { it.prayerKey to it.offsetMinutes }

            // "Today" is the current date at the *prayer location*, which is not
            // necessarily the device's date -- a user in Sydney tracking Makkah
            // times is up to a day ahead.
            val today = LocalDate.now(zone)
            val tomorrow = today.plusDays(1)

            scheduleForDate(today, offsets, lat, lon, zoneIdString, methodStr, schoolStr, ruleStr)
            scheduleForDate(tomorrow, offsets, lat, lon, zoneIdString, methodStr, schoolStr, ruleStr)

            // The morning/evening adhkar reminders ride along with every re-arm of the adhan alarms.
            runCatching { AdhkarReminders.scheduleAll(applicationContext) }

            // Trigger Widget Update pipeline
            io.github.sammynashed.emran.glance.WidgetUpdater.updateAllWidgets(applicationContext)

            return Result.success()
        } catch (e: Exception) {
            Log.e("PrayerScheduleWorker", "Error scheduling prayers", e)
            return Result.retry()
        }
    }

    private fun scheduleForDate(
        date: LocalDate,
        offsets: Map<String, Int>,
        lat: Double,
        lon: Double,
        zoneIdString: String,
        methodStr: String,
        schoolStr: String,
        ruleStr: String
    ) {
        val methodEnum = try { PrayerTimeCalculator.CalculationMethod.valueOf(methodStr) } catch(e: Exception) { PrayerTimeCalculator.CalculationMethod.MWL }
        val schoolEnum = try { PrayerTimeCalculator.AsrSchool.valueOf(schoolStr) } catch(e: Exception) { PrayerTimeCalculator.AsrSchool.SHAFII }
        val ruleEnum = try { PrayerTimeCalculator.HighLatitudeRule.valueOf(ruleStr) } catch(e: Exception) { PrayerTimeCalculator.HighLatitudeRule.AUTOMATIC }

        // Manual corrections are applied inside the calculator so that the
        // adjusted time is the one converted to an instant below. Applying them
        // afterwards, to a LocalDateTime, would drop them across a DST boundary.
        val times = PrayerTimeCalculator.calculateTimes(
            date = date,
            latitude = lat,
            longitude = lon,
            zoneId = zoneIdString,
            method = methodEnum,
            school = schoolEnum,
            highLatitudeRule = ruleEnum,
            adjustments = PrayerTimeCalculator.Adjustments(
                fajr = offsets["FAJR"] ?: 0,
                sunrise = offsets["SUNRISE"] ?: 0,
                dhuhr = offsets["DHUHR"] ?: 0,
                asr = offsets["ASR"] ?: 0,
                maghrib = offsets["MAGHRIB"] ?: 0,
                isha = offsets["ISHA"] ?: 0
            )
        )

        val prayers = listOf(
            "Fajr" to PrayerTimeCalculator.Prayer.FAJR,
            "Sunrise" to PrayerTimeCalculator.Prayer.SUNRISE,
            "Dhuhr" to PrayerTimeCalculator.Prayer.DHUHR,
            "Asr" to PrayerTimeCalculator.Prayer.ASR,
            "Maghrib" to PrayerTimeCalculator.Prayer.MAGHRIB,
            "Isha" to PrayerTimeCalculator.Prayer.ISHA
        )

        val preferences = applicationContext.getSharedPreferences("emran_settings", Context.MODE_PRIVATE)
        val onboardingCompleted = preferences.getBoolean("onboarding_completed", false)
        if (!onboardingCompleted) {
            Log.d("PrayerScheduleWorker", "Onboarding not completed. Skipping alarm scheduling.")
            return
        }
        val globalEnabled = preferences.getBoolean("notifications_enabled_global", true)

        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for ((name, prayer) in prayers) {
            val isEnabled = PrayerAlarmReceiver.isAdhanEnabled(applicationContext, name)

            val requestCode = (date.hashCode() + name.hashCode()).hashCode()
            val intent = Intent(applicationContext, PrayerAlarmReceiver::class.java).apply {
                putExtra("PRAYER_NAME", name)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (!globalEnabled || !isEnabled) {
                alarmManager.cancel(pendingIntent)
                Log.d("PrayerScheduleWorker", "Skipped/Cancelled alarm for $name on $date (disabled)")
                continue
            }

            // Resolve to an absolute instant using the *location's* zone. The old
            // code converted with ZoneId.systemDefault(), which silently
            // reinterpreted a home-city wall time as a device-local one.
            val triggerAtMillis = times.epochMillisOf(prayer)
            val scheduledTime = times.timeOf(prayer)

            if (triggerAtMillis > System.currentTimeMillis()) {
                try {
                    val showAppIntent = Intent(applicationContext, io.github.sammynashed.emran.MainActivity::class.java)
                    val showAppPendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        0,
                        showAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showAppPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d("PrayerScheduleWorker", "Scheduled reliable exact alarm clock for $name at $scheduledTime")
                } catch (e: Exception) {
                    Log.e("PrayerScheduleWorker", "setAlarmClock failed, falling back to standard alarm", e)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerAtMillis,
                                    pendingIntent
                                )
                            } else {
                                alarmManager.setAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerAtMillis,
                                    pendingIntent
                                )
                            }
                        } else {
                            alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerAtMillis,
                                    pendingIntent
                            )
                        }
                    } catch (ex: Exception) {
                        Log.e("PrayerScheduleWorker", "Fallback alarm scheduling failed", ex)
                    }
                }
            }
        }
    }
}
