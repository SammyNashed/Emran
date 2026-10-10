package io.github.sammynashed.emran.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import io.github.sammynashed.emran.ui.theme.AppTheme

/**
 * Frosted-glass card surface.
 *
 * The scrim and rim colours are chosen from the active palette rather than being
 * hardcoded white. White at 5% alpha over a light background is invisible, which
 * is why this used to be a dark-only effect; on a light surface the same illusion
 * needs a dark scrim and a white rim highlight instead.
 */
@Composable
fun Modifier.liquidGlassCard(
    shape: Shape = RoundedCornerShape(26.dp),
    accentColor: Color = Color.Transparent
): Modifier {
    val isLight = AppTheme.colors.isLight

    // On dark, the glass is lit from within by a white sheen. On light, the same
    // depth cue comes from a soft shadow tint plus a bright rim.
    val scrimStart = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f)
    val scrimEnd = if (isLight) Color.Black.copy(alpha = 0.015f) else Color.White.copy(alpha = 0.005f)
    val rimBright = if (isLight) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.28f)
    val rimFade = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.04f)
    val accentAlpha = if (isLight) 0.10f else 0.06f

    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(colors = listOf(scrimStart, scrimEnd))
        )
        .background(
            Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = accentAlpha),
                    Color.Transparent
                ),
                radius = 600f
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    rimBright,
                    rimFade,
                    Color.Transparent,
                    accentColor.copy(alpha = 0.16f)
                ),
                start = Offset(0f, 0f),
                end = Offset(400f, 800f) // Sleek diagonal light reflection
            ),
            shape = shape
        )
}

/**
 * The ambient app backdrop: two soft accent glows over a deep base.
 *
 * The base used to be a hardcoded obsidian `0xFF040307`, which meant every one of
 * the 20 presets was really the same near-black with a different accent. It now
 * follows the palette, so the light variants get a bright ground and the dark
 * ones keep the original look.
 */
@Composable
fun Modifier.cosmicAuroraBackground(
    accentColor: Color,
    secondaryAccent: Color = Color.Transparent
): Modifier {
    val colors = AppTheme.colors
    val isLight = colors.isLight
    val baseColor = colors.background

    val balanceColor = remember(accentColor, secondaryAccent) {
        if (secondaryAccent != Color.Transparent) secondaryAccent else accentColor
    }

    // Cache the color lists to prevent allocations inside the high-frequency drawBehind frame pass
    val fogColors = remember(isLight, baseColor) {
        if (isLight) {
            listOf(Color.White.copy(alpha = 0.7f), Color.Transparent)
        } else {
            listOf(Color(0xFF0E0B19), Color(0xFF040307))
        }
    }
    val primaryColors = remember(accentColor, isLight) {
        listOf(
            accentColor.copy(alpha = if (isLight) 0.13f else 0.16f),
            accentColor.copy(alpha = if (isLight) 0.05f else 0.04f),
            Color.Transparent
        )
    }
    val secondaryColors = remember(balanceColor, isLight) {
        listOf(
            balanceColor.copy(alpha = if (isLight) 0.11f else 0.13f),
            balanceColor.copy(alpha = if (isLight) 0.04f else 0.03f),
            Color.Transparent
        )
    }

    return this.drawBehind {
        val w = size.width
        val h = size.height

        if (w <= 0f || h <= 0f) return@drawBehind

        // Ground the whole surface first.
        drawRect(color = baseColor)

        // Central atmospheric fog.
        val r1 = (w * 1.4f).coerceAtLeast(1f)
        drawCircle(
            brush = Brush.radialGradient(colors = fogColors, radius = r1),
            radius = r1,
            center = Offset(w / 2f, h / 2f)
        )

        // Celestial Aurora Glow (Primary)
        val r2 = (w * 0.95f).coerceAtLeast(1f)
        drawCircle(
            brush = Brush.radialGradient(colors = primaryColors, radius = r2),
            radius = r2,
            center = Offset(w * 0.1f, h * 0.12f)
        )

        // Balancing Accent Glow (Secondary)
        val r3 = (w * 1.15f).coerceAtLeast(1f)
        drawCircle(
            brush = Brush.radialGradient(colors = secondaryColors, radius = r3),
            radius = r3,
            center = Offset(w * 0.9f, h * 0.85f)
        )
    }
}
