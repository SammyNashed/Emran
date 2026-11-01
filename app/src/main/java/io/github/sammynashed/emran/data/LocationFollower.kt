package io.github.sammynashed.emran.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import io.github.sammynashed.emran.ui.screens.nearestKnownPlace
import io.github.sammynashed.emran.ui.screens.providersToTry
import io.github.sammynashed.emran.ui.screens.reverseGeocode
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Follow my location": when Emran opens, and the phone has moved, the prayer times move with it.
 *
 * Deliberately modest, so it stays offline-first and private: it asks the platform's own location
 * (no Google Play Services) only while the app is on screen, at most every 20 minutes, and only
 * acts once you are 25 km from where the times were last set. Nothing is sent anywhere. Android
 * gives apps no location in the background without a far stronger permission, so a long trip is
 * picked up the next time Emran is opened (a changed time zone flags it to check straight away).
 */
object LocationFollower {
    private const val MIN_MOVE_KM = 25.0
    private const val THROTTLE_MS = 20 * 60_000L
    private const val FRESH_MS = 30 * 60_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Called when the phone's time zone changes (travel): look again at the next opportunity. */
    fun flagMoved(context: Context) {
        context.getSharedPreferences("emran_settings", Context.MODE_PRIVATE).edit().putBoolean("follow_dirty", true).apply()
    }

    /** True if the times were moved to a new place. [force] skips the throttle and the 25 km rule (the "update now" button). */
    suspend fun refresh(context: Context, vm: PrayerViewModel, force: Boolean = false): Boolean {
        if (!vm.followLocation.value && !force) return false
        if (!hasPermission(context)) return false
        val prefs = context.getSharedPreferences("emran_settings", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && !prefs.getBoolean("follow_dirty", false) && now - prefs.getLong("follow_checked_at", 0L) < THROTTLE_MS) return false

        val fix = currentFix(context) ?: return false
        prefs.edit().putLong("follow_checked_at", now).putBoolean("follow_dirty", false).apply()

        val km = distanceKm(vm.latitude.value, vm.longitude.value, fix.latitude, fix.longitude)
        if (km < MIN_MOVE_KM && !force) return false

        val name = withContext(Dispatchers.IO) {
            reverseGeocode(context, fix.latitude, fix.longitude) ?: nearestKnownPlace(fix.latitude, fix.longitude)
        } ?: String.format(Locale.US, "%.3f, %.3f", fix.latitude, fix.longitude)
        // The device's own zone: someone standing at these coordinates is in it.
        vm.setLocation(name, fix.latitude, fix.longitude, TimeZone.getDefault().id)
        if (prefs.getBoolean("follow_auto_method", false)) {
            suggestedMethod(fix.latitude, fix.longitude)?.let { if (it != vm.calculationMethod.value) vm.setCalculationMethod(it) }
        }
        return true
    }

    private suspend fun currentFix(context: Context): Location? = withContext(Dispatchers.Main) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
        val providers = try { providersToTry(manager) } catch (e: Exception) { emptyList() }
        val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (cached != null && System.currentTimeMillis() - cached.time < FRESH_MS) return@withContext cached
        val provider = providers.firstOrNull() ?: return@withContext cached
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext cached
        val fresh = withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                try {
                    manager.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        fresh ?: cached
    }

    /**
     * The usual calculation method where these coordinates are, judged by the nearest bundled
     * city's country. Only a suggestion for the "pick the method for me" switch; a place far from
     * every bundled city (500 km) gets none.
     */
    fun suggestedMethod(lat: Double, lon: Double): String? {
        val near = LocationPresets.list
            .map { it to distanceKm(lat, lon, it.latitude, it.longitude) }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= 500.0 } ?: return null
        return when (near.first.name.substringAfterLast(',').trim()) {
            "Egypt", "Sudan", "Libya", "Tunisia" -> "EGYPTIAN"
            "Saudi Arabia", "Yemen" -> "UMM_AL_QURA"
            "UAE", "Oman" -> "DUBAI"
            "Qatar" -> "QATAR"
            "Kuwait" -> "KUWAIT"
            "Pakistan", "India", "Bangladesh", "Sri Lanka" -> "KARACHI"
            "United States", "Canada", "Mexico" -> "ISNA"
            "Turkey" -> "TURKEY"
            "Singapore", "Malaysia", "Indonesia" -> "SINGAPORE"
            "Iran" -> "TEHRAN"
            else -> "MWL"
        }
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 2 * 6371.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
