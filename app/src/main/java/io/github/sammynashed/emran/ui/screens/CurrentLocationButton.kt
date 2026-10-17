package io.github.sammynashed.emran.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import io.github.sammynashed.emran.data.LocationPresets
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.sammynashed.emran.data.LocationPreset
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.design.LocalSky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * "Use my current location" for onboarding.
 *
 * Deliberately built on the platform's own [LocationManager] rather than Google
 * Play Services: Emran ships on F-Droid, and a GMS dependency would make that
 * impossible.
 *
 * That choice has a visible cost here. Play Services can raise the "turn on
 * location?" dialog that enables the providers in place, without leaving the
 * app; the platform API cannot, and Android permits no way to switch GPS on
 * programmatically. So when the providers are off, the most this can do is take
 * the reader to the right settings screen and pick the work back up when they
 * return - which is what it does, rather than telling them to go and find it.
 *
 * The coordinates never leave the device. They are written straight into the
 * app's own settings, and the permission is asked for once -- there is no
 * background location, no repeat prompt, and nothing to send anywhere.
 */
@Composable
fun CurrentLocationButton(
    lang: String,
    onLocationResolved: (LocationPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val sky = LocalSky.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    // Providers off is not a failure to report, it is a step to offer.
    var needsProviders by remember { mutableStateOf(false) }

    val gps = remember { GpsAcquisition(context) }
    val busy = gps.searching && gps.fix == null
    // The search runs while this screen is open and is torn down with it, so
    // leaving onboarding can never leave the receiver running.
    DisposableEffect(gps) { onDispose { gps.stop() } }

    // Accept on Emran's judgement rather than the reader's. Once the fix is
    // accurate enough to place them - and 50 m moves a prayer time by well under
    // a second - there is nothing for them to decide, so the app simply finishes.
    // A looser fix is given a moment to tighten, then taken anyway; a location
    // that is already good enough should never wait on a timer.
    LaunchedEffect(gps.fix, gps.settled) {
        val f = gps.fix ?: return@LaunchedEffect
        if (!gps.settled) kotlinx.coroutines.delay(REFINE_GRACE_MS)
        val best = gps.fix ?: f
        val name = withContext(Dispatchers.IO) {
            reverseGeocode(context, best.latitude, best.longitude)
                ?: nearestKnownPlace(best.latitude, best.longitude)
        }
        gps.stop()
        onLocationResolved(
            LocationPreset(
                name = name
                    ?: String.format(Locale.US, "%.3f, %.3f", best.latitude, best.longitude),
                latitude = best.latitude,
                longitude = best.longitude,
                // The device's own zone. Deriving one from coordinates offline
                // needs a boundary database Emran does not ship, and someone
                // standing at these coordinates is by definition in that zone.
                zoneId = java.util.TimeZone.getDefault().id,
            ),
        )
    }

    // Searching forever with nothing to show is its own kind of broken. If the
    // receiver has found nothing at all by now, say so plainly and let the
    // reader move on rather than leaving a spinner turning.
    LaunchedEffect(gps.searching) {
        if (!gps.searching) return@LaunchedEffect
        kotlinx.coroutines.delay(GIVE_UP_MS)
        if (gps.fix == null) {
            gps.stop()
            error = if (lang == "ar") {
                "تعذّر تحديد الموقع. جرّب في مكان مكشوف، أو اختر مدينتك من القائمة."
            } else {
                "Couldn't find your location. Try somewhere more open, or pick your city below."
            }
        }
    }

    fun providersOn(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun resolve() {
        if (!providersOn()) {
            // Asking for a fix the device cannot take would spend the wait and
            // report a failure the reader can actually do something about.
            needsProviders = true
            return
        }
        error = null
        needsProviders = false
        gps.start()
    }

    // Coming back from the location settings screen resumes what the reader
    // was doing, so enabling location is one step rather than two.
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        needsProviders = false
        if (providersOn()) resolve()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            resolve()
        } else {
            error = if (lang == "ar") {
                "لم يُمنح إذن الموقع. يمكنك اختيار مدينتك يدوياً من القائمة."
            } else {
                "Location permission denied. You can pick your city from the list instead."
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(sky.ink.copy(alpha = 0.12f))
                .border(1.dp, sky.ink.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                .clickable(enabled = !busy) {
                    if (hasLocationPermission(context)) {
                        resolve()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = sky.ink,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = sky.ink,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = when {
                            busy && lang == "ar" -> "جارٍ تحديد موقعك…"
                            busy -> "Finding your location..."
                            lang == "ar" -> "استخدام موقعي الحالي"
                            else -> "Use my current location"
                        },
                        color = sky.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (lang == "ar") {
                            "يبقى الموقع على جهازك ولا يُرسل لأي جهة. يُطلب مرة واحدة فقط."
                        } else {
                            "Stays on your device, sent nowhere. Asked once only."
                        },
                        color = sky.inkSoft,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        if (needsProviders) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (lang == "ar") {
                    "خدمة الموقع مغلقة على الجهاز. شغّلها ثم يتابع عمران تلقائيًا."
                } else {
                    "Location is switched off on this device. Turn it on and Emran will carry on by itself."
                },
                color = sky.inkSoft,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(sky.ink.copy(alpha = 0.14f))
                    .clickable {
                        locationSettingsLauncher.launch(
                            android.content.Intent(
                                android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                            ),
                        )
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (lang == "ar") "تشغيل خدمة الموقع" else "Turn on location",
                    color = sky.ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        error?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = it, color = sky.inkSoft, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

/**
 * Every provider worth asking, fused first.
 *
 * `getProviders(true)` does not reliably list the platform's fused provider,
 * and that is the one most likely to answer: it combines GNSS with sensors and
 * whatever cell and Wi-Fi data the device already holds, so it can often place
 * you indoors and offline where a bare GPS request cannot. It is reached through
 * `LocationManager`, so using it costs nothing in Play Services and keeps the
 * F-Droid build intact.
 */
internal fun providersToTry(manager: LocationManager): List<String> {
    val listed = try {
        manager.getProviders(true)
    } catch (e: Exception) {
        emptyList<String>()
    }
    val fused = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        listOf(LocationManager.FUSED_PROVIDER)
    } else {
        emptyList()
    }
    // Fused first, then whatever else is on, minus passive - which only ever
    // returns fixes some other app asked for, so it can produce nothing alone.
    return (fused + listed)
        .distinct()
        .filter { it != LocationManager.PASSIVE_PROVIDER }
}

/** City name if the device can resolve one; null offline, which is fine. */
internal fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
    // Most devices need the network for this, and some ship no geocoder at all.
    if (!Geocoder.isPresent()) return null
    return try {
    @Suppress("DEPRECATION")
    val results = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
    val address = results?.firstOrNull()
    listOfNotNull(
        address?.locality ?: address?.subAdminArea ?: address?.adminArea,
        address?.countryName
    ).joinToString(", ").ifBlank { null }
    } catch (e: Exception) {
        null
    }
}

/**
 * A name for somewhere the geocoder could not name, using the cities Emran
 * already ships.
 *
 * Prayer times are astronomical: latitude, longitude and a time zone are all
 * the calculation needs, so Emran computes correctly for any point on Earth
 * whether or not it has ever heard of the place. What it cannot always do is
 * *say* where that is - reverse geocoding needs the network, and the reader who
 * most needs this is the one without it.
 *
 * So the nearest of the bundled cities lends its name, and only if it is close
 * enough for that to mean something. The result is phrased as "near X" because
 * that is what it is: an approximation of the name, never of the position.
 *
 * The coordinates themselves are never touched. Snapping them to the nearest
 * city would move the reader tens of kilometres and quietly change every prayer
 * time - the opposite of the point.
 */
internal fun nearestKnownPlace(lat: Double, lon: Double): String? {
    val nearest = LocationPresets.list
        .map { it to haversineKm(lat, lon, it.latitude, it.longitude) }
        .minByOrNull { it.second }
        ?: return null
    val (place, km) = nearest
    return if (km <= NEAR_ENOUGH_KM) "near ${place.name}" else null
}

/** Great-circle distance in kilometres. */
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

/**
 * How far "near" is allowed to stretch.
 *
 * Wide enough to name the city someone is on the edge of, narrow enough that it
 * never claims a city in the next country.
 */
private const val NEAR_ENOUGH_KM = 60.0

/**
 * How long a loose fix is given to tighten before it is accepted anyway.
 *
 * Short, because the reader is waiting and a rough position still puts them in
 * the right district. A fix that is already accurate skips this entirely.
 */
private const val REFINE_GRACE_MS = 2_500L

/** How long to search before admitting the receiver cannot see the sky. */
private const val GIVE_UP_MS = 45_000L
