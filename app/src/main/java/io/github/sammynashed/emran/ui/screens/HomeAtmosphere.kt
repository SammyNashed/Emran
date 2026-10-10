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
 * Ambient visual furniture for the home screen: the streak fire animation,
 * the hero banner, and the ornamental divider.
 *
 * Part of the home screen; same package as HomeScreen.kt, so no additional
 * imports are required between them.
 */

@Composable
fun ThemedFireAnimation(
    modifier: Modifier = Modifier,
    flameColorPrimary: Color = AppTheme.colors.primary,
    flameColorSecondary: Color = AppTheme.colors.secondary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fire_anim")
    
    // Wave oscillation for flame flicker
    val flicker1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker1"
    )
    
    val flicker2 by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker2"
    )

    val wanderX1 by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wanderX1"
    )

    // Animated particles rising up
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    Canvas(
        modifier = modifier
            .testTag("themed_fire_animation")
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val bottomY = height * 0.9f
        
        // --- 1. Draw glowing background ---
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(flameColorPrimary.copy(alpha = 0.25f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(centerX, bottomY - height * 0.3f),
                radius = width * 0.7f
            )
        )

        // --- 2. Outer Flame (Teardrop shape scale-animated) ---
        val outerPath = Path().apply {
            val outerW = width * 0.55f * flicker1
            val outerH = height * 0.75f * flicker2
            moveTo(centerX, bottomY - outerH)
            cubicTo(
                centerX + outerW * 0.5f + wanderX1, bottomY - outerH * 0.6f,
                centerX + outerW * 0.6f, bottomY,
                centerX, bottomY
            )
            cubicTo(
                centerX - outerW * 0.6f, bottomY,
                centerX - outerW * 0.5f + wanderX1, bottomY - outerH * 0.6f,
                centerX, bottomY - outerH
            )
            close()
        }
        drawPath(
            path = outerPath,
            brush = Brush.verticalGradient(
                colors = listOf(flameColorSecondary.copy(alpha = 0.1f), flameColorPrimary.copy(alpha = 0.7f)),
                startY = bottomY - height * 0.8f,
                endY = bottomY
            )
        )

        // --- 3. Middle Flame (Slightly smaller, hotter colors) ---
        val middlePath = Path().apply {
            val middleW = width * 0.38f * flicker2
            val middleH = height * 0.55f * flicker1
            moveTo(centerX, bottomY - middleH)
            cubicTo(
                centerX + middleW * 0.5f - wanderX1 * 0.5f, bottomY - middleH * 0.6f,
                centerX + middleW * 0.6f, bottomY,
                centerX, bottomY
            )
            cubicTo(
                centerX - middleW * 0.6f, bottomY,
                centerX - middleW * 0.5f - wanderX1 * 0.5f, bottomY - middleH * 0.6f,
                centerX, bottomY - middleH
            )
            close()
        }
        drawPath(
            path = middlePath,
            brush = Brush.verticalGradient(
                colors = listOf(flameColorSecondary.copy(alpha = 0.5f), flameColorPrimary),
                startY = bottomY - height * 0.6f,
                endY = bottomY
            )
        )

        // --- 4. Inner Core (Very small, hottest center) ---
        val innerPath = Path().apply {
            val innerW = width * 0.22f * flicker1
            val innerH = height * 0.35f * flicker1
            moveTo(centerX, bottomY - innerH)
            cubicTo(
                centerX + innerW * 0.5f, bottomY - innerH * 0.4f,
                centerX + innerW * 0.5f, bottomY,
                centerX, bottomY
            )
            cubicTo(
                centerX - innerW * 0.5f, bottomY,
                centerX - innerW * 0.5f, bottomY - innerH * 0.4f,
                centerX, bottomY - innerH
            )
            close()
        }
        drawPath(
            path = innerPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, flameColorSecondary),
                startY = bottomY - height * 0.4f,
                endY = bottomY
            )
        )

        // --- 5. Rising Sparks ---
        val sparks = listOf(
            Ember(-0.25f, 0.7f, 3.dp.toPx(), 1.0f),
            Ember(0.2f, 0.5f, 2.dp.toPx(), 1.2f),
            Ember(-0.05f, 0.9f, 1.dp.toPx(), 0.8f)
        )

        sparks.forEach { spark ->
            val prog = (particleProgress * spark.speed) % 1f
            val sparkY = bottomY - (spark.startYMult * height * prog)
            val sparkX = centerX + (spark.offsetMult * width) + (kotlin.math.sin(prog * 6.2831853f) * 6f)
            val alpha = (1f - prog).coerceIn(0f, 1f)
            
            if (sparkY > 0f && alpha > 0f) {
                drawCircle(
                    color = flameColorSecondary.copy(alpha = alpha),
                    radius = spark.radiusPx,
                    center = androidx.compose.ui.geometry.Offset(sparkX, sparkY)
                )
            }
        }
    }
}

private data class Ember(
    val offsetMult: Float,
    val startYMult: Float,
    val radiusPx: Float,
    val speed: Float
)
@Composable
fun IslamicAtmosphereHeroBanner(
    modifier: Modifier = Modifier,
    presetColors: Pair<Color, Color>
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0B21), 
                        Color(0xFF040307)
                    )
                )
            )
            .drawBehind {
                val w = size.width
                val h = size.height

                // Starry glow
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(presetColors.first.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.2f),
                        radius = w * 0.6f
                    )
                )

                // Stars
                listOf(
                    Offset(w * 0.12f, h * 0.25f),
                    Offset(w * 0.28f, h * 0.18f),
                    Offset(w * 0.42f, h * 0.32f),
                    Offset(w * 0.58f, h * 0.15f),
                    Offset(w * 0.72f, h * 0.35f),
                    Offset(w * 0.88f, h * 0.22f)
                ).forEach { pos ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = 1.3.dp.toPx(),
                        center = pos
                    )
                }

                // Crescent Moon
                val moonRadius = 14.dp.toPx()
                val moonCenter = Offset(w * 0.82f, h * 0.32f)
                drawCircle(
                    color = Color(0xFFFCD34D).copy(alpha = 0.9f),
                    radius = moonRadius,
                    center = moonCenter
                )
                drawCircle(
                    color = Color(0xFF0F0B21),
                    radius = moonRadius,
                    center = moonCenter - Offset(4.2.dp.toPx(), 3.dp.toPx())
                )

                // Silhouette
                val domePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, h * 0.8f)
                    
                    // Minaret 1 (Left)
                    lineTo(w * 0.1f, h * 0.8f)
                    lineTo(w * 0.1f, h * 0.45f)
                    lineTo(w * 0.08f, h * 0.45f)
                    lineTo(w * 0.12f, h * 0.38f)
                    lineTo(w * 0.16f, h * 0.45f)
                    lineTo(w * 0.14f, h * 0.45f)
                    lineTo(w * 0.14f, h * 0.8f)

                    // Dome Left
                    lineTo(w * 0.26f, h * 0.8f)
                    cubicTo(w * 0.28f, h * 0.8f, w * 0.3f, h * 0.68f, w * 0.33f, h * 0.65f)
                    cubicTo(w * 0.36f, h * 0.68f, w * 0.38f, h * 0.8f, w * 0.4f, h * 0.8f)

                    // Main Dome
                    lineTo(w * 0.42f, h * 0.8f)
                    cubicTo(w * 0.44f, h * 0.8f, w * 0.46f, h * 0.52f, w * 0.5f, h * 0.48f)
                    cubicTo(w * 0.54f, h * 0.52f, w * 0.56f, h * 0.8f, w * 0.58f, h * 0.8f)

                    // Dome Right
                    lineTo(w * 0.64f, h * 0.8f)
                    cubicTo(w * 0.66f, h * 0.8f, w * 0.68f, h * 0.68f, w * 0.71f, h * 0.65f)
                    cubicTo(w * 0.74f, h * 0.68f, w * 0.76f, h * 0.8f, w * 0.78f, h * 0.8f)

                    // Minaret 2 (Right)
                    lineTo(w * 0.84f, h * 0.8f)
                    lineTo(w * 0.84f, h * 0.42f)
                    lineTo(w * 0.82f, h * 0.42f)
                    lineTo(w * 0.86f, h * 0.33f)
                    lineTo(w * 0.90f, h * 0.42f)
                    lineTo(w * 0.88f, h * 0.42f)
                    lineTo(w * 0.88f, h * 0.8f)

                    lineTo(w, h * 0.8f)
                    lineTo(w, h)
                    close()
                }

                drawPath(
                    path = domePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            presetColors.first.copy(alpha = 0.28f),
                            Color(0xFF040307).copy(alpha = 0.95f)
                        )
                    )
                )

                drawPath(
                    path = domePath,
                    color = presetColors.first.copy(alpha = 0.08f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
            }
    )
}
@Composable
fun PremiumIslamicDivider(
    modifier: Modifier = Modifier,
    color: Color
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, color.copy(alpha = 0.5f))
                    )
                )
        )
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
    }
}
