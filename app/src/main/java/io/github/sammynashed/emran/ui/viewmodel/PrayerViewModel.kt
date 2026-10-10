package io.github.sammynashed.emran.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope // Trigger 2
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.sammynashed.emran.data.LocationPreset
import io.github.sammynashed.emran.data.LocationPresets
import io.github.sammynashed.emran.data.PrayerTimeCalculator
import io.github.sammynashed.emran.data.local.db.AppDatabase
import io.github.sammynashed.emran.data.repository.PrayerRepository
import io.github.sammynashed.emran.service.PrayerScheduleWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt


class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = PrayerRepository(database.prayerDao())

    private val preferences = application.getSharedPreferences("emran_settings", Context.MODE_PRIVATE)

    // User settings stored in SharedPreferences
    val onboardingCompleted = MutableStateFlow(preferences.getBoolean("onboarding_completed", false))
    val themePreset = MutableStateFlow(preferences.getString("theme_preset", "EXPRESSIVE") ?: "EXPRESSIVE")

    /**
     * Light / dark / follow-system. Defaults to DARK so existing installs look
     * exactly as they did before light mode existed.
     */
    val themeMode = MutableStateFlow(preferences.getString("theme_mode", "DARK") ?: "DARK")

    fun setThemeMode(mode: String) {
        preferences.edit().putString("theme_mode", mode).apply()
        themeMode.value = mode
        // The widget mirrors the app palette, so it has to be redrawn too.
        viewModelScope.launch {
            try {
                io.github.sammynashed.emran.glance.WidgetUpdater.updateAllWidgets(getApplication())
            } catch (e: Exception) {
                android.util.Log.e("PrayerViewModel", "Failed to refresh widgets after theme mode change", e)
            }
        }
    }
    val appIconPreset = MutableStateFlow(preferences.getString("app_icon_preset", "EXPRESSIVE") ?: "EXPRESSIVE")
    val locationName = MutableStateFlow(preferences.getString("location_name", "Aleppo, Syria") ?: "Aleppo, Syria")
    val currentLanguage = MutableStateFlow(preferences.getString("current_language", "en") ?: "en")
    val latitude = MutableStateFlow(preferences.getFloat("latitude", 36.2021f).toDouble())
    val longitude = MutableStateFlow(preferences.getFloat("longitude", 37.1343f).toDouble())

    /**
     * IANA zone for the selected location, and the source of truth for all
     * prayer time calculation. See [LocationPreset] for why a raw UTC offset is
     * not good enough.
     */
    val zoneId = MutableStateFlow(migrateOrReadZoneId())

    /**
     * Reads the stored zone id, migrating installs that predate it.
     *
     * Older builds persisted only a `timezone` float. The saved `location_name`
     * is the reliable way back to a real zone, so try the preset table first and
     * fall back to the device zone. The float is deliberately not used to guess a
     * zone: many offsets map to several zones with different DST rules, and
     * picking the wrong one reintroduces exactly the bug being fixed.
     */
    private fun migrateOrReadZoneId(): String {
        preferences.getString("zone_id", null)?.let { if (it.isNotBlank()) return it }

        val savedLocation = preferences.getString("location_name", null)
        val migrated = LocationPresets.list
            .firstOrNull { it.name == savedLocation }
            ?.zoneId
            ?: java.time.ZoneId.systemDefault().id

        preferences.edit().putString("zone_id", migrated).apply()
        return migrated
    }
    val defaultAdhanSound = MutableStateFlow(preferences.getString("default_adhan_sound", "default") ?: "default")

    fun setDefaultAdhanSound(soundKey: String) {
        preferences.edit().putString("default_adhan_sound", soundKey).apply()
        defaultAdhanSound.value = soundKey
    }

    val currentDate = MutableStateFlow(LocalDate.now())




    val calculationMethod = MutableStateFlow(preferences.getString("calculation_method", "EGYPTIAN") ?: "EGYPTIAN")
    val asrSchool = MutableStateFlow(preferences.getString("asr_school", "SHAFII") ?: "SHAFII")

    /**
     * Which convention to use where the sun never reaches the Fajr/Isha angle.
     * Defaults to AUTOMATIC, which only kicks in past 48 degrees of latitude and
     * is therefore a no-op for most users.
     */
    val highLatitudeRule = MutableStateFlow(
        preferences.getString("high_latitude_rule", "AUTOMATIC") ?: "AUTOMATIC"
    )

    // Tolerant enum parsing: a preference written by an older build (or a hand
    // edited prefs file) must not crash the prayer pipeline.
    private fun parseMethod(value: String) = try {
        PrayerTimeCalculator.CalculationMethod.valueOf(value)
    } catch (e: Exception) {
        PrayerTimeCalculator.CalculationMethod.EGYPTIAN
    }

    private fun parseSchool(value: String) = try {
        PrayerTimeCalculator.AsrSchool.valueOf(value)
    } catch (e: Exception) {
        PrayerTimeCalculator.AsrSchool.SHAFII
    }

    private fun parseHighLatitudeRule(value: String) = try {
        PrayerTimeCalculator.HighLatitudeRule.valueOf(value)
    } catch (e: Exception) {
        PrayerTimeCalculator.HighLatitudeRule.AUTOMATIC
    }

    fun setHighLatitudeRule(rule: String) {
        preferences.edit().putString("high_latitude_rule", rule).apply()
        highLatitudeRule.value = rule
        rescheduleAlarms("high latitude rule change")
    }

    private fun rescheduleAlarms(reason: String) {
        viewModelScope.launch {
            try {
                WorkManager.getInstance(getApplication()).enqueue(
                    OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build()
                )
            } catch (e: Exception) {
                android.util.Log.e("PrayerViewModel", "Failed to reschedule alarms after $reason", e)
            }
        }
    }

    fun setCalculationMethod(method: String) {
        preferences.edit().putString("calculation_method", method).apply()
        calculationMethod.value = method
        // Force reschedule alarms
        viewModelScope.launch {
            try {
                WorkManager.getInstance(getApplication()).enqueue(
                    OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build()
                )
            } catch (e: Exception) {
                android.util.Log.e("PrayerViewModel", "Failed to reschedule alarms after calculation method change", e)
            }
        }
    }

    fun setAsrSchool(school: String) {
        preferences.edit().putString("asr_school", school).apply()
        asrSchool.value = school
        // Force reschedule alarms
        viewModelScope.launch {
            try {
                WorkManager.getInstance(getApplication()).enqueue(
                    OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build()
                )
            } catch (e: Exception) {
                android.util.Log.e("PrayerViewModel", "Failed to reschedule alarms after Asr school change", e)
            }
        }
    }

    // Notification states for the five daily prayers and sunrise
    val fahrNotifEnabled = MutableStateFlow(preferences.getBoolean("notif_Fajr", true))
    val sunriseNotifEnabled = MutableStateFlow(preferences.getBoolean("notif_Sunrise", true))
    val dhuhrNotifEnabled = MutableStateFlow(preferences.getBoolean("notif_Dhuhr", true))
    val asrNotifEnabled = MutableStateFlow(preferences.getBoolean("notif_Asr", true))
    val maghribNotifEnabled = MutableStateFlow(preferences.getBoolean("notif_Maghrib", true))
    val ishaNotifEnabled = MutableStateFlow(preferences.getBoolean("notif_Isha", true))

    fun isPrayerNotificationEnabled(prayerName: String): Boolean {
        return when (prayerName) {
            "Fajr" -> fahrNotifEnabled.value
            "Sunrise" -> sunriseNotifEnabled.value
            "Dhuhr" -> dhuhrNotifEnabled.value
            "Asr" -> asrNotifEnabled.value
            "Maghrib" -> maghribNotifEnabled.value
            "Isha" -> ishaNotifEnabled.value
            else -> false
        }
    }

    fun togglePrayerNotification(prayerName: String) {
        viewModelScope.launch {
            val currentVal = isPrayerNotificationEnabled(prayerName)
            val newVal = !currentVal
            preferences.edit().putBoolean("notif_$prayerName", newVal).apply()
            
            when (prayerName) {
                "Fajr" -> fahrNotifEnabled.value = newVal
                "Sunrise" -> sunriseNotifEnabled.value = newVal
                "Dhuhr" -> dhuhrNotifEnabled.value = newVal
                "Asr" -> asrNotifEnabled.value = newVal
                "Maghrib" -> maghribNotifEnabled.value = newVal
                "Isha" -> ishaNotifEnabled.value = newVal
            }

            // Reschedule alarms immediately to reflect toggle
            try {
                WorkManager.getInstance(getApplication()).enqueue(
                    OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build()
                )
            } catch (e: Exception) {
                android.util.Log.e("PrayerViewModel", "Failed to reschedule alarms after toggle", e)
            }
        }
    }

    // Date state removed here because it's defined dynamically above for testing

    // Manual offset overrides (e.g., "FAJR" -> minutes offset)
    val offsets = repository.offsetsMap.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Calculated prayer times for current date (updated with user offsets and custom location)
    val prayerTimes = combine(
        currentDate,
        offsets,
        latitude,
        longitude,
        zoneId,
        calculationMethod,
        asrSchool,
        highLatitudeRule
    ) { array ->
        val date = array[0] as LocalDate
        val offsetMap = array[1] as Map<String, Int>
        val lat = array[2] as Double
        val lon = array[3] as Double
        val zone = array[4] as String
        val methodStr = array[5] as String
        val schoolStr = array[6] as String
        val ruleStr = array[7] as String

        PrayerTimeCalculator.calculateTimes(
            date = date,
            latitude = lat,
            longitude = lon,
            zoneId = zone,
            method = parseMethod(methodStr),
            school = parseSchool(schoolStr),
            highLatitudeRule = parseHighLatitudeRule(ruleStr),
            adjustments = PrayerTimeCalculator.Adjustments.fromMap(offsetMap)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PrayerTimeCalculator.calculateTimes(
            date = LocalDate.now(),
            latitude = latitude.value,
            longitude = longitude.value,
            zoneId = zoneId.value
        )
    )

    /**
     * Times for any date under the user's current settings.
     *
     * The Prayers page needs two days at once: a prayer that has passed today
     * shows tomorrow's time, the way the wheel counts down to it. [prayerTimes]
     * only describes one date, so the page asks for the other here. Callers
     * should recompute when [prayerTimes] changes - it re-emits on every setting
     * (location, method, adjustments) this reads.
     */
    fun prayerTimesOn(date: LocalDate): PrayerTimeCalculator.PrayerTimes =
        PrayerTimeCalculator.calculateTimes(
            date = date,
            latitude = latitude.value,
            longitude = longitude.value,
            zoneId = zoneId.value,
            method = parseMethod(calculationMethod.value),
            school = parseSchool(asrSchool.value),
            highLatitudeRule = parseHighLatitudeRule(highLatitudeRule.value),
            adjustments = PrayerTimeCalculator.Adjustments.fromMap(offsets.value)
        )

    // Countdown state (Next Prayer name, remaining time formatted, depleting ratio)
    data class CountdownState(
        val nextPrayerName: String = "Fajr",
        val formattedRemaining: String = "00:00:00",
        val nextPrayerTime: String = "00:00",
        val depletingRatio: Float = 1.0f,
        val entireDayProgress: Float = 0.0f
    )

    private val _countdownState = MutableStateFlow(CountdownState())
    val countdownState: StateFlow<CountdownState> = _countdownState.asStateFlow()



    private suspend fun updateCountdown() {
        val lat = latitude.value
        val lon = longitude.value
        val zone = zoneId.value
        val adjustments = PrayerTimeCalculator.Adjustments.fromMap(offsets.value)
        val resolvedZone = PrayerTimeCalculator.safeZone(zone)

        // "Now" and the day boundary are both evaluated in the *location's* zone,
        // not the device's. A traveller who keeps Emran pinned to their home city
        // should see that city's countdown, and comparing a device-local clock
        // against home-zone prayer times would be off by the difference.
        val now = LocalDateTime.now(resolvedZone)
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)

        fun timesFor(date: LocalDate) = PrayerTimeCalculator.calculateTimes(
            date = date,
            latitude = lat,
            longitude = lon,
            zoneId = zone,
            method = parseMethod(calculationMethod.value),
            school = parseSchool(asrSchool.value),
            highLatitudeRule = parseHighLatitudeRule(highLatitudeRule.value),
            adjustments = adjustments
        )

        val timesToday = timesFor(today)
        val timesTomorrow = timesFor(tomorrow)

        val prayerSequence = listOf(
            "Fajr" to timesToday.fajr,
            "Sunrise" to timesToday.sunrise,
            "Dhuhr" to timesToday.dhuhr,
            "Asr" to timesToday.asr,
            "Maghrib" to timesToday.maghrib,
            "Isha" to timesToday.isha,
            "Fajr (Tomorrow)" to timesTomorrow.fajr
        )

        // Find next prayer
        var nextPair = prayerSequence.find { it.second.isAfter(now) }
        var prevPair = prayerSequence.findLast { it.second.isBefore(now) }

        if (nextPair == null) {
            // Edge case: after Isha today
            nextPair = "Fajr" to timesTomorrow.fajr
        }
        if (prevPair == null) {
            // Edge case: before Fajr today, previous is Isha yesterday
            prevPair = "Isha" to timesFor(today.minusDays(1)).isha
        }

        var nextName = nextPair.first.replace(" (Tomorrow)", "")
        var nextTime = nextPair.second
        var prevTime = prevPair.second
        
        // Calculate the entire day progress mapped to the compass (real time travel)
        val majorBase = when (prevPair.first.replace(" (Tomorrow)", "")) {
            "Fajr" -> 0.0f
            "Sunrise" -> 1f / 6f
            "Dhuhr" -> 2f / 6f
            "Asr" -> 3f / 6f
            "Maghrib" -> 4f / 6f
            "Isha" -> 5f / 6f
            else -> 0.0f
        }
        val majorTotalSecs = Duration.between(prevTime, nextTime).seconds.toFloat()
        val majorElapsedSecs = Duration.between(prevTime, now).seconds.toFloat()
        val majorRatio = if (majorTotalSecs > 0) (majorElapsedSecs / majorTotalSecs).coerceIn(0f, 1f) else 0f
        val entireDayProgress = (majorBase + majorRatio * (1f / 6f)).coerceIn(0f, 1f)

        val durationRemaining = Duration.between(now, nextTime)
        val totalDuration = Duration.between(prevTime, nextTime)

        val secsRemaining = durationRemaining.seconds.coerceAtLeast(0)
        val hours = secsRemaining / 3600
        val mins = (secsRemaining % 3600) / 60
        val secs = secsRemaining % 60

        val formatted = String.format("%02d:%02d:%02d", hours, mins, secs)

        val ratio = if (totalDuration.seconds > 0) {
            (secsRemaining.toFloat() / totalDuration.seconds.toFloat()).coerceIn(0.0f, 1.0f)
        } else {
            1.0f
        }

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        _countdownState.value = CountdownState(
            nextPrayerName = nextName,
            formattedRemaining = formatted,
            nextPrayerTime = nextTime.format(timeFormatter),
            depletingRatio = ratio,
            entireDayProgress = entireDayProgress
        )
    }

    fun setOnboardingCompleted(
        completed: Boolean,
        initialTheme: String,
        initialLocName: String,
        lat: Double,
        lon: Double,
        zone: String,
        notificationsEnabled: Boolean = true,
        initialCalculationMethod: String = "EGYPTIAN",
        initialAsrSchool: String = "SHAFII",
        defaultAdhanSoundVal: String = "default"
    ) {
        viewModelScope.launch {
            preferences.edit()
                .putBoolean("onboarding_completed", completed)
                .putString("theme_preset", initialTheme)
                .putString("location_name", initialLocName)
                .putFloat("latitude", lat.toFloat())
                .putFloat("longitude", lon.toFloat())
                .putString("zone_id", zone)
                .putString("calculation_method", initialCalculationMethod)
                .putString("asr_school", initialAsrSchool)
                .putBoolean("notifications_enabled_global", notificationsEnabled)
                .putBoolean("notif_Fajr", notificationsEnabled)
                .putBoolean("notif_Dhuhr", notificationsEnabled)
                .putBoolean("notif_Asr", notificationsEnabled)
                .putBoolean("notif_Maghrib", notificationsEnabled)
                .putBoolean("notif_Isha", notificationsEnabled)
                .putString("default_adhan_sound", defaultAdhanSoundVal)
                .apply()

            onboardingCompleted.value = completed
            themePreset.value = initialTheme
            locationName.value = initialLocName
            latitude.value = lat
            longitude.value = lon
            zoneId.value = zone
            calculationMethod.value = initialCalculationMethod
            asrSchool.value = initialAsrSchool
            defaultAdhanSound.value = defaultAdhanSoundVal

            fahrNotifEnabled.value = notificationsEnabled
            dhuhrNotifEnabled.value = notificationsEnabled
            asrNotifEnabled.value = notificationsEnabled
            maghribNotifEnabled.value = notificationsEnabled
            ishaNotifEnabled.value = notificationsEnabled

            // Reschedule alarms immediately
            try {
                WorkManager.getInstance(getApplication()).enqueue(
                    OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build()
                )
            } catch (e: Exception) {
                android.util.Log.e("PrayerViewModel", "Failed to enqueue onboarding prayer schedule work", e)
            }
        }
    }

    private fun updateAppIcon(theme: String, restartApp: Boolean = false) {
        val app = getApplication<Application>()
        val packageManager = app.packageManager
        val packageName = app.packageName

        val aliases = listOf(
            "Expressive", "Cosmic", "Teal", "Rose", "Golden", "Emerald", 
            "Ocean", "Amythist", "Sunfire", "Sapphire", "Lavender", "Mint", 
            "Forest", "Blood_moon", "Monochrome", "Sakura", "Nebula", 
            "Desert", "Aurora", "Black_stone"
        )
        
        // Capitalize the first letter and lowercase the rest to match alias naming
        val activeAlias = when (theme) {
            "BLOOD_MOON" -> "Blood_moon"
            "BLACK_STONE" -> "Black_stone"
            else -> theme.lowercase().replaceFirstChar { it.uppercase() }
        }

        val previouslyActive = preferences.getString("currently_active_launcher_alias", "Expressive")
        if (activeAlias == previouslyActive) {
            // Already set, do nothing to avoid killing the process unnecessarily!
            return
        }

        try {
            for (alias in aliases) {
                val componentName = android.content.ComponentName(
                    packageName,
                    "io.github.sammynashed.emran.MainActivityAlias$alias"
                )
                val isTarget = alias == activeAlias
                val newState = if (isTarget) {
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                val currentState = packageManager.getComponentEnabledSetting(componentName)
                if (currentState != newState) {
                    packageManager.setComponentEnabledSetting(
                        componentName,
                        newState,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                }
            }
            
            // Save the successfully applied active launcher alias
            preferences.edit().putString("currently_active_launcher_alias", activeAlias).apply()
            
            if (restartApp) {
                // Manually restart the app to apply the alias change cleanly
                var intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent == null) {
                    intent = android.content.Intent().apply {
                        setClassName(packageName, "io.github.sammynashed.emran.MainActivity")
                    }
                }
                intent.putExtra("START_DESTINATION", "finetune")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                app.startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerViewModel", "Failed to update dynamic launcher icon", e)
        }
    }

    fun setThemePreset(preset: String) {
        preferences.edit().putString("theme_preset", preset).apply()
        themePreset.value = preset
        
        // Trigger widget update
        try {
            androidx.work.WorkManager.getInstance(getApplication()).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<io.github.sammynashed.emran.service.EmranWidgetUpdateWorker>().build()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setCurrentLanguage(lang: String) {
        preferences.edit().putString("current_language", lang).apply()
        currentLanguage.value = lang
    }

    fun setAppIconPreset(preset: String) {
        preferences.edit().putString("app_icon_preset", preset).apply()
        appIconPreset.value = preset
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            updateAppIcon(preset, restartApp = true)
        }
    }

    fun setLocation(name: String, lat: Double, lon: Double, zone: String) {
        viewModelScope.launch {
            preferences.edit()
                .putString("location_name", name)
                .putFloat("latitude", lat.toFloat())
                .putFloat("longitude", lon.toFloat())
                .putString("zone_id", zone)
                .apply()

            locationName.value = name
            latitude.value = lat
            longitude.value = lon
            zoneId.value = zone

            // Reschedule alarms immediately
            try {
                WorkManager.getInstance(getApplication()).enqueue(
                    OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build()
                )
            } catch (e: Exception) {
                android.util.Log.e("PrayerViewModel", "Failed to enqueue location prayer schedule work", e)
            }
        }
    }

    /** Convenience for callers that already hold a preset. */
    fun setLocation(preset: LocationPreset) =
        setLocation(preset.name, preset.latitude, preset.longitude, preset.zoneId)

    // Save manual offset adjustment
    fun updateOffset(prayerKey: String, offsetMinutes: Int) {
        viewModelScope.launch {
            repository.saveOffset(prayerKey, offsetMinutes)
            // Trigger alarm rescheduling immediately upon fine-tuning
            try {
                WorkManager.getInstance(getApplication()).enqueue(
                    OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build()
                )
            } catch (e: Exception) {
                android.util.Log.e("PrayerViewModel", "Failed to enqueue offset fine-tuning prayer schedule work", e)
            }
        }
    }

    init {
        io.github.sammynashed.emran.data.StoredDataPurge.run(getApplication(), preferences)

        // Trigger live countdown timer tick every second
        viewModelScope.launch {
            while (true) {
                updateCountdown()
                delay(1000)
            }
        }


        // Initialize default offsets & Qada structures if not existing in DB
         viewModelScope.launch {
             // Invalidate/trigger work manager to ensure sync is live on first launcher load
             try {
                 WorkManager.getInstance(getApplication()).enqueue(
                     OneTimeWorkRequestBuilder<PrayerScheduleWorker>().build()
                 )
             } catch (e: Exception) {
                 android.util.Log.e("PrayerViewModel", "Failed to enqueue initial prayer schedule work", e)
             }
         }
    }
}
