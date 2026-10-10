package io.github.sammynashed.emran.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.R
import io.github.sammynashed.emran.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    appIconPreset: String,
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Animation States
    val bgGlowAlpha = remember { Animatable(0f) }
    
    // Staggered bars
    val barScales = List(5) { remember { Animatable(0f) } }
    
    // Logo container transparency & pulse
    val containerAlpha = remember { Animatable(0f) }
    val pulseScale = remember { Animatable(1f) }
    
    // Explosive soft halo
    val explosionHaloScale = remember { Animatable(0.8f) }
    val explosionHaloAlpha = remember { Animatable(0f) }

    // Text & subtitle animation states
    val emranAlpha = remember { Animatable(0f) }
    val emranY = remember { Animatable(20f) }
    val arabicAlpha = remember { Animatable(0f) }
    val englishAlpha = remember { Animatable(0f) }

    // Overall overlay fade-out state
    val overallAlpha = remember { Animatable(1f) }

    val presetColors = remember(appIconPreset) {
        io.github.sammynashed.emran.ui.theme.getColorsByPreset(appIconPreset)
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            // 1 & 2. Background glow fades in subtly
            launch {
                bgGlowAlpha.animateTo(
                    targetValue = 0.25f, // Barely visible
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                )
            }
            
            // 3 & 4. Bars emerge with smooth upward motion
            (0..4).forEach { index ->
                launch {
                    delay(50 + index * 50L)
                    barScales[index].animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                    )
                }
            }
            
            // Container border and background fade in smoothly
            launch {
                delay(200)
                containerAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            }
            
            // 5, 6, 7. Pulse together and soft halo expands
            launch {
                delay(500)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                
                launch {
                    pulseScale.animateTo(1.05f, tween(150, easing = FastOutSlowInEasing))
                    pulseScale.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
                }
                launch {
                    explosionHaloScale.snapTo(0.8f)
                    explosionHaloScale.animateTo(2.2f, tween(400, easing = FastOutSlowInEasing))
                }
                launch {
                    explosionHaloAlpha.snapTo(0.5f)
                    explosionHaloAlpha.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
                }
            }
            
            // 8. Fade in "Emran"
            launch {
                delay(600)
                launch { emranAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
                launch { emranY.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
            }
            
            // 9. Fade in Arabic tagline
            launch {
                delay(700)
                arabicAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            }
            
            // 10. Fade in English tagline
            launch {
                delay(800)
                englishAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
            }
            
            // 11. Hold for a brief moment
            delay(1200)
            
            // 12. Transition smoothly to home screen
            overallAlpha.animateTo(0f, tween(300, easing = LinearEasing))
            onSplashFinished()
        }
    }

    // Pure black screen
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(overallAlpha.value)
            .background(Color.Black), // Start pure black
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Main Logo Area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Subtle ambient center glow
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .alpha(bgGlowAlpha.value)
                        .blur(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    presetColors.primary.copy(alpha = 0.5f),
                                    presetColors.secondary.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Expanding Soft Halo (Pulse Effect)
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(explosionHaloScale.value)
                        .alpha(explosionHaloAlpha.value)
                        .blur(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    presetColors.primary.copy(alpha = 0.8f),
                                    presetColors.secondary.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Logo Container
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(pulseScale.value) // Pluses together
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    presetColors.primary.copy(alpha = 0.15f * containerAlpha.value),
                                    presetColors.secondary.copy(alpha = 0.05f * containerAlpha.value)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.15f * containerAlpha.value),
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // The 5 Bars
                    Row(
                        modifier = Modifier.padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val heights = listOf(28.dp, 40.dp, 52.dp, 40.dp, 28.dp)
                        heights.forEachIndexed { index, h ->
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height(h)
                                    .graphicsLayer {
                                        transformOrigin = TransformOrigin(0.5f, 1f) // Grow from bottom
                                        scaleY = barScales[index].value
                                        alpha = barScales[index].value.coerceAtLeast(0f)
                                    }
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                presetColors.primary,
                                                presetColors.secondary
                                            )
                                        )
                                    )
                                    .border(
                                        width = 0.5.dp,
                                        color = Color.White.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main branding text
            Text(
                text = "Emran",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .alpha(emranAlpha.value)
                    .graphicsLayer { translationY = emranY.value }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Arabic tagline
            Text(
                text = "الصلاة عمران الروح",
                color = presetColors.primary.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                modifier = Modifier.alpha(arabicAlpha.value)
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // English tagline
            Text(
                text = "The Prosperity of the Soul",
                color = AppTheme.colors.grayMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(englishAlpha.value)
            )
        }
    }
}
