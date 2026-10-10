package io.github.sammynashed.emran.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import io.github.sammynashed.emran.ui.tour.LocalTourManager
import io.github.sammynashed.emran.ui.tour.tourTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

enum class DevicePosture {
    FLAT, ANGLED, UPRIGHT, MOVING, UNKNOWN
}

enum class EnvironmentQuality {
    EXCELLENT, GOOD, FAIR, POOR, UNRELIABLE
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Earth radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

@Composable
fun QiblaCompassScreen(
    viewModel: PrayerViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tourManager = LocalTourManager.current

    val activeLat by viewModel.latitude.collectAsState()
    val activeLon by viewModel.longitude.collectAsState()
    val locationName by viewModel.locationName.collectAsState()

    val meccaLat = 21.4225
    val meccaLon = 39.8262

    val distanceToKaaba = remember(activeLat, activeLon) {
        calculateDistance(activeLat, activeLon, meccaLat, meccaLon)
    }

    val qiblaBearingValue = remember(activeLat, activeLon) {
        val latR = Math.toRadians(activeLat)
        val latM = Math.toRadians(meccaLat)
        val deltaLon = Math.toRadians(meccaLon - activeLon)
        val y = sin(deltaLon) * cos(latM)
        val x = cos(latR) * sin(latM) - sin(latR) * cos(latM) * cos(deltaLon)
        var b = Math.toDegrees(atan2(y, x))
        ((b + 360.0) % 360.0).toFloat()
    }

    val trueAzimuthAnim = remember { Animatable(0f) }
    var rawAzimuth by remember { mutableStateOf(0f) }
    var continuousAzimuth by remember { mutableStateOf(0f) }
    var lastAzimuth by remember { mutableStateOf(0f) }

    // Sensor Fusion Engine State
    var magneticStrength by remember { mutableStateOf(50f) }
    var accelMagnitude by remember { mutableStateOf(9.8f) }
    var sensorAccuracy by remember { mutableStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE) }
    var devicePitch by remember { mutableStateOf(0f) }
    var deviceRoll by remember { mutableStateOf(0f) }
    
    var isAnalyzing by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1200)
        isAnalyzing = false
    }

    val declination = remember(activeLat, activeLon) {
        try {
            val geomagneticField = android.hardware.GeomagneticField(
                activeLat.toFloat(), activeLon.toFloat(), 0f, System.currentTimeMillis()
            )
            geomagneticField.declination
        } catch (e: Exception) {
            0f
        }
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var initialized = false
        var lastAccelValues = FloatArray(3)
        var accelVariance = 0f

        val listener = object : SensorEventListener {
            private fun updateAzimuth(azimuthDeg: Float) {
                rawAzimuth = azimuthDeg
                if (!initialized) {
                    lastAzimuth = azimuthDeg
                    continuousAzimuth = azimuthDeg
                    initialized = true
                    scope.launch { trueAzimuthAnim.snapTo(continuousAzimuth) }
                    return
                }

                var diff = azimuthDeg - lastAzimuth
                while (diff < -180f) diff += 360f
                while (diff > 180f) diff -= 360f

                continuousAzimuth += diff
                lastAzimuth = azimuthDeg

                scope.launch {
                    trueAzimuthAnim.animateTo(
                        targetValue = continuousAzimuth,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                    val remappedR = FloatArray(9)
                    val displayRotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try { context.display?.rotation ?: android.view.Surface.ROTATION_0 } catch (e: Exception) { android.view.Surface.ROTATION_0 }
                    } else {
                        try {
                            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                            windowManager.defaultDisplay.rotation
                        } catch (e: Exception) { android.view.Surface.ROTATION_0 }
                    }

                    val (axisX, axisY) = when (displayRotation) {
                        android.view.Surface.ROTATION_90 -> Pair(SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X)
                        android.view.Surface.ROTATION_180 -> Pair(SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y)
                        android.view.Surface.ROTATION_270 -> Pair(SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X)
                        else -> Pair(SensorManager.AXIS_X, SensorManager.AXIS_Y)
                    }

                    if (SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedR)) {
                        val orientationValues = FloatArray(3)
                        SensorManager.getOrientation(remappedR, orientationValues)
                        val azimuthDeg = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                        devicePitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat()
                        deviceRoll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()
                        updateAzimuth((azimuthDeg + 360f) % 360f)
                    }
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    magneticStrength = sqrt(x*x + y*y + z*z)
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    accelMagnitude = sqrt(x*x + y*y + z*z)
                    
                    // Simple variance for movement detection
                    val currentDiff = abs(accelMagnitude - 9.81f)
                    accelVariance = (accelVariance * 0.8f) + (currentDiff * 0.2f)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    sensorAccuracy = accuracy
                }
            }
        }

        if (rotationSensor != null) sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        if (accelerometer != null) sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        if (magnetometer != null) sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_GAME)

        onDispose { sensorManager.unregisterListener(listener) }
    }

    // --- Intelligence Engine ---

    val devicePosture = remember(devicePitch, deviceRoll, accelMagnitude) {
        val pitchAbs = abs(devicePitch)
        val rollAbs = abs(deviceRoll)
        val isMoving = abs(accelMagnitude - 9.8f) > 1.5f
        
        when {
            isMoving -> DevicePosture.MOVING
            pitchAbs < 15f && rollAbs < 15f -> DevicePosture.FLAT
            pitchAbs > 65f || rollAbs > 65f -> DevicePosture.UPRIGHT
            else -> DevicePosture.ANGLED
        }
    }

    val envQuality = remember(magneticStrength) {
        val deviation = abs(magneticStrength - 50f)
        when {
            deviation < 15f -> EnvironmentQuality.EXCELLENT
            deviation < 25f -> EnvironmentQuality.GOOD
            deviation < 40f -> EnvironmentQuality.FAIR
            deviation < 60f -> EnvironmentQuality.POOR
            else -> EnvironmentQuality.UNRELIABLE
        }
    }

    val reliabilityPercentage = remember(sensorAccuracy, devicePosture, envQuality) {
        val sensorQuality = when (sensorAccuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 1.0f
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 0.8f
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 0.5f
            else -> 0.3f
        }
        val orientationQuality = when (devicePosture) {
            DevicePosture.FLAT -> 1.0f
            DevicePosture.ANGLED -> 0.7f
            DevicePosture.UPRIGHT -> 0.4f
            DevicePosture.MOVING -> 0.2f
            DevicePosture.UNKNOWN -> 0.5f
        }
        val envQualityFactor = when (envQuality) {
            EnvironmentQuality.EXCELLENT -> 1.0f
            EnvironmentQuality.GOOD -> 0.85f
            EnvironmentQuality.FAIR -> 0.6f
            EnvironmentQuality.POOR -> 0.3f
            EnvironmentQuality.UNRELIABLE -> 0.1f
        }
        
        val calibrationQuality = 0.95f
        val sensorAgreement = 1.0f
        
        val totalReliability = sensorQuality * orientationQuality * envQualityFactor * calibrationQuality * sensorAgreement
        (totalReliability * 100).toInt().coerceIn(0, 100)
    }

    val displayAzimuth = ((trueAzimuthAnim.value + declination + 360f * 100) % 360f)
    val relativeQiblaAngle = ((qiblaBearingValue - displayAzimuth + 360f) % 360f)

    // A reliable reading must have a minimum reliability percentage to be trusted for prayer.
    val isTrustworthy = reliabilityPercentage >= 50
    // We only show alignment if it is trustworthy AND actually aligned.
    val isAligned = !isAnalyzing && isTrustworthy && (abs(relativeQiblaAngle) <= 2.5f || abs(relativeQiblaAngle - 360f) <= 2.5f)

    LaunchedEffect(isAligned) {
        if (isAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            tourManager?.triggerSuccess("qibla_prayer_mat_align")
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val breatheAnim by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )

    val primaryColor = AppTheme.colors.primary
    val secondaryColor = Color(0xFFFF7A1A)

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .tourTarget("sub_screen_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (lang == "ar") "سجادة الصلاة" else "Prayer Mat",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }

            if (isAnalyzing) {
                Box(modifier = Modifier.fillMaxSize().weight(1f).defaultMinSize(minHeight = 400.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = primaryColor, strokeWidth = 2.dp, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = if (lang == "ar") "تحليل البيئة ومستشعرات الجهاز..." else "Analyzing environment and sensors...",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                // Hero Prayer Mat Area
                Box(
                    modifier = Modifier.size(420.dp).tourTarget("qibla_prayer_mat_align"),
                    contentAlignment = Alignment.Center
                ) {
                    // Soft background glow
                    if (isAligned) {
                        Canvas(modifier = Modifier.fillMaxSize().blur(48.dp)) {
                            drawCircle(secondaryColor.copy(alpha = 0.25f * breatheAnim), radius = size.minDimension / 3f)
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        
                        // Fixed Notch (Top phone alignment indicator)
                        val notchWidth = 4.dp.toPx()
                        val notchHeight = 16.dp.toPx()
                        drawRoundRect(
                            color = if (isAligned) secondaryColor else primaryColor.copy(alpha = 0.3f),
                            topLeft = Offset(center.x - notchWidth / 2, 24.dp.toPx()),
                            size = Size(notchWidth, notchHeight),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )

                        withTransform({ rotate(relativeQiblaAngle, center) }) {
                            val matWidth = 130.dp.toPx()
                            val matHeight = 260.dp.toPx()
                            val matCorner = CornerRadius(16.dp.toPx())
                            val matTopLeft = Offset(center.x - matWidth / 2, center.y - matHeight / 2)

                            // Mat background
                            drawRoundRect(
                                color = Color(0xFF0A0A0F),
                                topLeft = matTopLeft,
                                size = Size(matWidth, matHeight),
                                cornerRadius = matCorner
                            )

                            // Primary mat border
                            drawRoundRect(
                                color = primaryColor.copy(alpha = if (isAligned) 0.8f else 0.4f),
                                topLeft = matTopLeft,
                                size = Size(matWidth, matHeight),
                                cornerRadius = matCorner,
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // Inner mat border
                            val inset = 12.dp.toPx()
                            drawRoundRect(
                                color = primaryColor.copy(alpha = 0.15f),
                                topLeft = Offset(matTopLeft.x + inset, matTopLeft.y + inset),
                                size = Size(matWidth - inset * 2, matHeight - inset * 2),
                                cornerRadius = CornerRadius(8.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx())
                            )

                            // Mihrab (arch)
                            val archInset = 24.dp.toPx()
                            val archWidth = matWidth - archInset * 2
                            val archHeight = matHeight * 0.55f
                            val archTop = matTopLeft.y + 32.dp.toPx()

                            val archPath = Path().apply {
                                val startX = center.x - archWidth / 2
                                val startY = archTop + archHeight
                                moveTo(startX, startY)
                                lineTo(startX, archTop + archWidth / 2)
                                quadraticBezierTo(
                                    startX, archTop,
                                    center.x, archTop - 16.dp.toPx()
                                )
                                quadraticBezierTo(
                                    startX + archWidth, archTop,
                                    startX + archWidth, archTop + archWidth / 2
                                )
                                lineTo(startX + archWidth, startY)
                            }

                            drawPath(
                                path = archPath,
                                color = primaryColor.copy(alpha = 0.4f),
                                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                            
                            // Mat center design (minimal star/diamond)
                            val diamondSize = 16.dp.toPx()
                            val diamondPath = Path().apply {
                                val dCenterY = center.y + 16.dp.toPx()
                                moveTo(center.x, dCenterY - diamondSize)
                                lineTo(center.x + diamondSize * 0.6f, dCenterY)
                                lineTo(center.x, dCenterY + diamondSize)
                                lineTo(center.x - diamondSize * 0.6f, dCenterY)
                                close()
                            }
                            drawPath(
                                path = diamondPath,
                                color = primaryColor.copy(alpha = 0.1f)
                            )
                            drawPath(
                                path = diamondPath,
                                color = primaryColor.copy(alpha = 0.3f),
                                style = Stroke(width = 1.dp.toPx())
                            )

                            // Premium Minimalist Kaaba Icon
                            val kaabaSize = 26.dp.toPx()
                            val kaabaY = matTopLeft.y - kaabaSize - 24.dp.toPx()
                            val kaabaTopLeft = Offset(center.x - kaabaSize / 2, kaabaY)

                            drawRoundRect(
                                color = Color(0xFF111111),
                                topLeft = kaabaTopLeft,
                                size = Size(kaabaSize, kaabaSize),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                            drawRect(
                                color = secondaryColor,
                                topLeft = Offset(kaabaTopLeft.x, kaabaTopLeft.y + kaabaSize * 0.25f),
                                size = Size(kaabaSize, kaabaSize * 0.12f)
                            )

                            // Connection Line
                            drawLine(
                                color = primaryColor.copy(alpha = 0.2f),
                                start = Offset(center.x, kaabaY + kaabaSize + 8.dp.toPx()),
                                end = Offset(center.x, matTopLeft.y - 8.dp.toPx()),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                            )

                            if (isAligned) {
                                drawRoundRect(
                                    color = secondaryColor.copy(alpha = 0.6f * breatheAnim),
                                    topLeft = kaabaTopLeft,
                                    size = Size(kaabaSize, kaabaSize),
                                    cornerRadius = CornerRadius(4.dp.toPx()),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                                // Kaaba Glow
                                drawRoundRect(
                                    color = secondaryColor.copy(alpha = 0.2f * breatheAnim),
                                    topLeft = kaabaTopLeft,
                                    size = Size(kaabaSize, kaabaSize),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Telemetry & Information
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Alignment Status Banner
                    if (isAligned) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(secondaryColor.copy(alpha = 0.15f))
                                .border(1.dp, secondaryColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "ar") "متجه للقبلة بدقة" else "PRECISELY ALIGNED",
                                color = secondaryColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    // Device Posture Warning / Info
                    val postureMessage = when (devicePosture) {
                        DevicePosture.FLAT -> if (lang == "ar") "تم اكتشاف سطح مستوٍ. دقة محسنة." else "Stable surface detected. Optimizing accuracy."
                        DevicePosture.UPRIGHT -> if (lang == "ar") "الهاتف بوضع عمودي. الدقة قد تنخفض." else "Holding phone upright. Accuracy may be reduced."
                        DevicePosture.MOVING -> if (lang == "ar") "حركة مستمرة. يرجى التثبيت." else "Movement detected. Please hold still."
                        DevicePosture.ANGLED -> if (lang == "ar") "ضع الهاتف بشكل مسطح لأفضل دقة." else "Place phone flat for best accuracy."
                        else -> null
                    }
                    val isPostureIdeal = devicePosture == DevicePosture.FLAT || devicePosture == DevicePosture.ANGLED

                    if (postureMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isPostureIdeal) primaryColor.copy(alpha = 0.1f) else Color(0xFFFF3B30).copy(alpha = 0.1f))
                                .border(1.dp, if (isPostureIdeal) primaryColor.copy(alpha = 0.2f) else Color(0xFFFF3B30).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isPostureIdeal) Icons.Default.Info else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isPostureIdeal) primaryColor else Color(0xFFFF3B30),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = postureMessage,
                                color = if (isPostureIdeal) Color.White.copy(alpha = 0.9f) else Color(0xFFFF3B30),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Interference Warning
                    val envMessage = when (envQuality) {
                        EnvironmentQuality.POOR, EnvironmentQuality.UNRELIABLE -> 
                            if (lang == "ar") "تداخل مغناطيسي قوي. يرجى الابتعاد عن المعادن أو الإلكترونيات." else "Strong magnetic distortion. Move away from metal or electronics."
                        EnvironmentQuality.FAIR -> 
                            if (lang == "ar") "تداخل مغناطيسي طفيف مُمُتشف." else "Slight magnetic distortion detected."
                        else -> null
                    }
                    
                    if (envMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF3B30).copy(alpha = 0.1f))
                                .border(1.dp, Color(0xFFFF3B30).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = envMessage,
                                color = Color(0xFFFF3B30),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Qibla Distance Box
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(1.dp, primaryColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = if (lang == "ar") "المسافة" else "DISTANCE",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${String.format("%,d", distanceToKaaba.toInt())} km",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Reliability Engine Box
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(1.dp, primaryColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = if (lang == "ar") "الموثوقية" else "RELIABILITY",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$reliabilityPercentage%",
                                color = if (isTrustworthy) Color.White else Color(0xFFFF3B30),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(140.dp)) // Respect Emran dock overlay
        }
    }
}
