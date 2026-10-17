package io.github.sammynashed.emran.ui.screens

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where the Qibla is, relative to the top of the phone.
 *
 * [turn] is how far the user must turn, in degrees: negative = turn left, positive = right,
 * 0 = facing it. [facing] has hysteresis (on within 3°, off beyond 7°) so it does not flicker.
 * [calibrate] is true only while the compass is genuinely unreliable (low accuracy or a
 * magnetic field far outside the Earth's, i.e. a magnet or metal nearby) for more than a second.
 */
@Immutable
data class QiblaReading(
    val turn: Float,
    val facing: Boolean,
    val calibrate: Boolean,
    val ready: Boolean,
    /** True while the phone is held upright, camera facing forward (as opposed to lying flat). */
    val upright: Boolean = false,
)

/** The Kaaba, and the great-circle bearing to it from true north. */
fun qiblaBearing(lat: Double, lon: Double): Float {
    val kLat = Math.toRadians(21.4225)
    val kLon = 39.8262
    val la = Math.toRadians(lat)
    val dLon = Math.toRadians(kLon - lon)
    val y = sin(dLon) * cos(kLat)
    val x = cos(la) * sin(kLat) - sin(la) * cos(kLat) * cos(dLon)
    return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
}

@Composable
fun rememberQiblaReading(lat: Double, lon: Double): QiblaReading {
    val context = LocalContext.current
    val bearing = remember(lat, lon) { qiblaBearing(lat, lon) }
    // The phone's compass points to MAGNETIC north; the bearing is from TRUE north.
    // In Aleppo they differ by ~5°, which is the whole margin of a correct Qibla.
    val declination = remember(lat, lon) {
        runCatching {
            GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        }.getOrDefault(0f)
    }
    var reading by remember { mutableStateOf(QiblaReading(0f, false, false, false)) }
    // Only while the app is in front: a composable stays alive in the background, a compass must not.
    val lifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = lifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)

    DisposableEffect(bearing, declination, active) {
        if (!active) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val magnetic = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val r = FloatArray(9)
        val remapped = FloatArray(9)
        val ori = FloatArray(3)
        var heading = Float.NaN
        var magAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        var magStrength = 50f
        var badSince = 0L
        var facing = false
        var upright = false

        fun publish() {
            if (heading.isNaN()) return
            var turn = bearing - heading
            while (turn > 180f) turn -= 360f
            while (turn < -180f) turn += 360f
            facing = if (facing) abs(turn) < 7f else abs(turn) < 3f
            val bad = magAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW || magStrength !in 20f..75f
            val now = SystemClock.elapsedRealtime()
            badSince = if (bad) (if (badSince == 0L) now else badSince) else 0L
            val calibrate = badSince != 0L && now - badSince > 1000L
            reading = QiblaReading(turn, facing && !calibrate, calibrate, true, upright)
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                        magStrength = sqrt(x * x + y * y + z * z)
                    }
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(r, e.values)
                        // Lying flat or held upright, the "forward" of the phone is its top edge.
                        upright = abs(r[8]) < 0.55f
                        @Suppress("DEPRECATION")
                        val rot = wm.defaultDisplay.rotation
                        val (ax, ay) = when (rot) {
                            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                        }
                        val ok = if (upright) SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                        else SensorManager.remapCoordinateSystem(r, ax, ay, remapped)
                        if (!ok) return
                        SensorManager.getOrientation(remapped, ori)
                        val az = ((Math.toDegrees(ori[0].toDouble()).toFloat() + declination) % 360f + 360f) % 360f
                        heading = if (heading.isNaN()) az else {
                            // Smooth on the circle, so 359° -> 1° does not swing the long way round.
                            var d = az - heading
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            ((heading + d * 0.14f) % 360f + 360f) % 360f
                        }
                        publish()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) magAccuracy = accuracy
            }
        }
        if (rotation != null) sm.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        if (magnetic != null) sm.registerListener(listener, magnetic, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return reading
}
