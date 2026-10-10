package io.github.sammynashed.emran.ui.screens

import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Finding out where the reader actually is, without a network.
 *
 * ## Why this is not a timeout
 *
 * Prayer times are computed from coordinates, and coordinates matter at the
 * scale of a district: two people in different parts of Aleppo have genuinely
 * different times, and rounding one of them to "Syria" would be a worse answer
 * dressed up as a faster one. So the position has to be measured, not guessed.
 *
 * GPS measures it with no network at all - but a cold receiver has to read the
 * satellites' own ephemeris to do it, at fifty bits per second. With a network
 * that data arrives instantly and a fix takes seconds; without one it commonly
 * takes thirty to sixty seconds under open sky, and longer through a roof. An
 * earlier version asked once, waited twenty-five seconds and declared failure -
 * which cut the receiver off at exactly the moment it was working, then told the
 * reader it had not worked.
 *
 * So this does not give up. It keeps listening for as long as the screen is
 * open, reports what the receiver can currently see, and accepts the first fix
 * while continuing to improve on it. A search still running is not an error, and
 * is not presented as one.
 */
class GpsAcquisition(private val context: Context) {

    /** Best fix so far, or null while still searching. */
    var fix by mutableStateOf<Location?>(null)
        private set

    /** Satellites the receiver can see. Evidence that it is working. */
    var inView by mutableIntStateOf(0)
        private set

    /** Satellites actually contributing to a fix; four is the usual minimum. */
    var used by mutableIntStateOf(0)
        private set

    var searching by mutableStateOf(false)
        private set

    /** True once a fix is good enough that refining further is not worth waiting for. */
    val settled: Boolean
        get() = fix?.let { it.hasAccuracy() && it.accuracy <= GOOD_ENOUGH_M } == true

    private val manager: LocationManager?
        get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val listeners = mutableListOf<LocationListener>()
    private var gnss: GnssStatus.Callback? = null

    private fun accept(candidate: Location) {
        val current = fix
        // Keep the more accurate of the two. A later fix is not automatically a
        // better one - the first satellites to lock often give a loose position
        // that a network provider can beat, and vice versa.
        val better = when {
            current == null -> true
            !current.hasAccuracy() -> true
            candidate.hasAccuracy() && candidate.accuracy < current.accuracy -> true
            else -> false
        }
        if (better) fix = candidate
    }

    /** Begin searching. Safe to call twice; the second call does nothing. */
    fun start() {
        val m = manager ?: return
        if (searching) return
        searching = true

        providersToTry(m).forEach { provider ->
            try {
                val l = object : LocationListener {
                    override fun onLocationChanged(location: Location) = accept(location)

                    @Deprecated("Required by the interface below API 30")
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                }
                @Suppress("MissingPermission")
                m.requestLocationUpdates(provider, 0L, 0f, l, Looper.getMainLooper())
                listeners += l
            } catch (e: Exception) {
                // One provider missing must not stop the others.
            }
        }

        // Satellite counts turn "nothing is happening" into "it is working, wait".
        try {
            val cb = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    inView = status.satelliteCount
                    used = (0 until status.satelliteCount).count { status.usedInFix(it) }
                }
            }
            @Suppress("MissingPermission", "DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                m.registerGnssStatusCallback(cb, android.os.Handler(Looper.getMainLooper()))
                gnss = cb
            }
        } catch (e: Exception) {
            // Status reporting is a courtesy; its absence does not stop a fix.
        }

        // A cached fix, if the platform happens to hold one, is a free head start.
        providersToTry(m).forEach { provider ->
            try {
                @Suppress("MissingPermission")
                m.getLastKnownLocation(provider)?.let(::accept)
            } catch (e: Exception) {
                // Nothing cached for this provider.
            }
        }
    }

    fun stop() {
        val m = manager
        listeners.forEach { l -> try { m?.removeUpdates(l) } catch (e: Exception) { } }
        listeners.clear()
        gnss?.let { cb -> try { m?.unregisterGnssStatusCallback(cb) } catch (e: Exception) { } }
        gnss = null
        searching = false
        inView = 0
        used = 0
    }

    private companion object {
        /**
         * Accuracy at which refining further stops being worth the wait.
         *
         * Fifty metres moves a prayer time by well under a second, so a fix this
         * good is already exact for the purpose - the reader should not be kept
         * waiting for a number that will not change their day.
         */
        const val GOOD_ENOUGH_M = 50f
    }
}
