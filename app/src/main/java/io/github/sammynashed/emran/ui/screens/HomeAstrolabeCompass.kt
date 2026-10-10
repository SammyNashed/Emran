package io.github.sammynashed.emran.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.res.vectorResource
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material3.*
import io.github.sammynashed.emran.ui.localization.Text
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import io.github.sammynashed.emran.ui.tour.tourTarget
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel.CountdownState
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import java.time.LocalDate
/*
 * The astrolabe prayer dial at the centre of the home screen.
 *
 * AstrolabeCentralCompass and CompassPrayerItem were top-level `private`,
 * which in Kotlin means file-scoped. Moving them out of HomeScreen.kt would
 * have put them out of reach of their caller, so both are now `internal`:
 * still not public API, but visible across the module.
 *
 * Part of the home screen; same package as HomeScreen.kt, so no additional
 * imports are required between them.
 */

@Composable
internal fun AstrolabeCentralCompass(
    countdownStateProvider: () -> CountdownState,
    lang: String,
    primaryColor: Color,
    secondaryColor: Color,
    prayersList: List<CompassPrayerItem>,
    pathStartAngle: Float,
    pathSweepMax: Float,
    toArDigits: (String) -> String
) {
    val countdownState = countdownStateProvider()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer circle container for the Entire Compass (diameter 380dp)
        Box(
            modifier = Modifier
                .size(380.dp)
                .testTag("spiritual_prayer_compass")
                .tourTarget("spiritual_prayer_compass"),
            contentAlignment = Alignment.Center
        ) {
            val entireDayProgress = countdownState.entireDayProgress

            // 1a. Fine graduated astrolabe ticks around the absolute outer border
            Canvas(modifier = Modifier.size(380.dp)) {
                val radius = size.minDimension / 2f
                val tickLength = 5.dp.toPx()
                for (angle in 0 until 360 step 3) {
                    val angleRad = Math.toRadians(angle.toDouble())
                    val cosVal = Math.cos(angleRad).toFloat()
                    val sinVal = Math.sin(angleRad).toFloat()
                    val startX = center.x + (radius - tickLength) * cosVal
                    val startY = center.y + (radius - tickLength) * sinVal
                    val endX = center.x + radius * cosVal
                    val endY = center.y + radius * sinVal
                    
                    val isMajor = angle % 30 == 0
                    drawCircle(
                        color = Color.White.copy(alpha = if (isMajor) 0.12f else 0.04f),
                        radius = if (isMajor) 1.5.dp.toPx() else 0.5.dp.toPx(),
                        center = Offset(endX, endY)
                    )
                }
            }

            // 1b. Draw the beautiful flowing progress path & track on the Outer Ring of the Compass
            Canvas(modifier = Modifier.size(320.dp)) {
                val strokeWidthTrack = 2.dp.toPx()
                val strokeWidthProgress = 4.dp.toPx()
                val strokeWidthProgressGlow = 10.dp.toPx()

                // Background track
                drawArc(
                    color = Color.White.copy(alpha = 0.05f),
                    startAngle = pathStartAngle,
                    sweepAngle = pathSweepMax,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthTrack)
                )

                // Glowing path behind
                drawArc(
                    color = primaryColor.copy(alpha = 0.28f),
                    startAngle = pathStartAngle,
                    sweepAngle = pathSweepMax * entireDayProgress,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidthProgressGlow,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )

                // Top solid path representing the prayer journey
                drawArc(
                    color = primaryColor,
                    startAngle = pathStartAngle,
                    sweepAngle = pathSweepMax * entireDayProgress,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidthProgress,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }

            // 1c. Concentric delicate dashed inner ring (Astrolabe precision feel)
            Canvas(modifier = Modifier.size(262.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.minDimension / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(5f, 6f),
                            0f
                        )
                    )
                )
            }

            // 1d. Thumb knob on the running progress track (Outer position at radius 160dp)
            val thumbAngleDegrees = (pathStartAngle + pathSweepMax * entireDayProgress).toDouble()
            val thumbAngleRad = Math.toRadians(thumbAngleDegrees)
            val thumbRadius = 160f
            val thumbX = (thumbRadius * Math.cos(thumbAngleRad).toFloat()).dp
            val thumbY = (thumbRadius * Math.sin(thumbAngleRad).toFloat()).dp

            Box(
                modifier = Modifier
                    .absoluteOffset(x = thumbX, y = thumbY)
                    .size(14.dp)
                    .background(Color.White, CircleShape)
                    .border(3.dp, primaryColor, CircleShape)
                    .drawBehind {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.5f),
                            radius = size.minDimension * 0.95f
                        )
                    }
            )

            // 2. Position the five daily prayers along the outer ring circumference
            prayersList.forEach { info ->
                val angleRad = Math.toRadians(info.angleDegrees)
                // Radius from center of 380dp box: track is at 160dp radius
                val outerRadius = 160f
                
                val dotXDp = (outerRadius * Math.cos(angleRad).toFloat()).dp
                val dotYDp = (outerRadius * Math.sin(angleRad).toFloat()).dp

                val isSunrise = info.key == "SUNRISE"
                
                // Current fard target checks: next fard name or sunrise interval matches
                val isCurrent = remember(countdownState.nextPrayerName) {
                    val isFriday = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY
                    val mappedNext = if (isFriday && countdownState.nextPrayerName == "Dhuhr") "Jum'ah" else countdownState.nextPrayerName
                    !isSunrise && (mappedNext == info.name || (mappedNext == "Sunrise" && info.name == "Fajr"))
                }

                // Dot exactly on the track
                Box(
                    modifier = Modifier
                        .absoluteOffset(x = dotXDp, y = dotYDp)
                        .size(if (isCurrent) 12.dp else 10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSunrise -> Color.Gray.copy(alpha = 0.4f)
                                isCurrent -> primaryColor
                                else -> Color.Black.copy(alpha = 0.8f)
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = when {
                                isSunrise -> Color.Gray.copy(alpha = 0.4f)
                                isCurrent -> primaryColor
                                else -> Color.White.copy(alpha = 0.25f)
                            },
                            shape = CircleShape
                        )
                )

                // Icon and label separated radially further outwards or inwards depending on angle.
                // Labels on the left/right flanks are ~75dp wide, so a centre placed at a fixed
                // radius pushes their outer edge onto the progress track and its thumb. Pull them
                // inwards in proportion to how horizontal they are.
                val labelRadius = 126f - 16f * Math.abs(Math.cos(angleRad).toFloat())
                val labelXDp = (labelRadius * Math.cos(angleRad).toFloat()).dp
                val labelYDp = (labelRadius * Math.sin(angleRad).toFloat()).dp

                Box(
                    modifier = Modifier
                        .absoluteOffset(x = labelXDp, y = labelYDp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = info.icon,
                            contentDescription = info.name,
                            tint = when {
                                isSunrise -> Color.White.copy(alpha = 0.3f)
                                isCurrent -> primaryColor
                                else -> Color.White.copy(alpha = 0.45f)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = info.label,
                            color = when {
                                isSunrise -> Color.White.copy(alpha = 0.3f)
                                isCurrent -> Color.White
                                else -> Color.White.copy(alpha = 0.45f)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // 3. CENTER CORE: High-contrast Dark Dial Lens (Sacred Astro-Compass center)
            Box(
                modifier = Modifier
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    val pNameRaw = countdownState.nextPrayerName
                    val isFriday = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY
                    val mappedNext = if (isFriday && pNameRaw == "Dhuhr") "Jum'ah" else pNameRaw
                    val pNameMap = mapOf(
                        "Fajr" to "الفجر",
                        "Sunrise" to "الشروق",
                        "Dhuhr" to "الظهر",
                        "Jum'ah" to "الجمعة",
                        "Asr" to "العصر",
                        "Maghrib" to "المغرب",
                        "Isha" to "العشاء"
                    )
                    val pNameAr = pNameMap[mappedNext] ?: mappedNext
                    
                    val nextIcon = prayersList.find { it.name == mappedNext }?.icon ?: Icons.Default.WbSunny

                    // Top Icon
                    Icon(
                        imageVector = nextIcon,
                        contentDescription = "Next Prayer Icon",
                        tint = secondaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = if (lang == "ar") "الصلاة القادمة" else "NEXT PRAYER",
                        color = secondaryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // A. Large Prayer Name in center (legible & focused)
                    Text(
                        text = if (lang == "ar") pNameAr else mappedNext.uppercase(),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = if (lang == "ar") 0.sp else 2.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))

                    // B. Countdown timer beneath it
                    Text(
                        text = toArDigits(countdownState.formattedRemaining),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // C. Next prayer information below countdown
                    // Stacked rather than one 130dp line: the single line ran straight into
                    // the Fajr and Maghrib labels that sit either side of it on the lower arc.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = if (lang == "ar") "الأذان" else "NEXT CALL",
                            color = secondaryColor.copy(alpha = 0.75f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = if (lang == "ar") 0.sp else 1.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = toArDigits(countdownState.nextPrayerTime),
                            color = secondaryColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

internal data class CompassPrayerItem(
    val name: String,
    val label: String,
    val key: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val angleDegrees: Double
)
