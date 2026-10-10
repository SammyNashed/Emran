package io.github.sammynashed.emran.glance.theme

import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import io.github.sammynashed.emran.ui.theme.AppColors

/**
 * A purely Glance-compatible color palette.
 * This class ensures widgets never deal with raw Colors or Theme logic,
 * and always receive exactly what they need as ColorProviders.
 */
data class GlanceColors(
    val primary: ColorProvider,
    val secondary: ColorProvider,
    val background: ColorProvider,
    val surface: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val success: ColorProvider,
    val warning: ColorProvider,
    val error: ColorProvider,
    val divider: ColorProvider,
    val border: ColorProvider,

    /*
     * Raw colours, kept alongside the providers purely so the glass surfaces can
     * derive translucent tints. A ColorProvider cannot be read back, and the
     * glass look is built entirely out of alpha variants of the accent and
     * background, so the widgets need the underlying values.
     */
    val accentRaw: Color,
    val backgroundRaw: Color,
    val surfaceRaw: Color,
    val textPrimaryRaw: Color
)

/**
 * Maps the Compose AppColors to GlanceColors.
 * Handles fallback generation for missing colors in the Compose theme.
 */
fun AppColors.toGlanceColors(): GlanceColors {
    // Generate derived colors that might not exist explicitly in AppColors
    val warningColor = Color(0xFFF59E0B) // Amber-500 default
    val errorColor = Color(0xFFEF4444)   // Red-500 default
    
    // Derived borders and dividers from surface/background with varying opacity
    // In Glance, we can't easily do alpha composition on the fly with ColorProviders in the same way,
    // so we approximate by blending with background or using fixed hex values representing translucent overlays.
    // For simplicity, we just lower the alpha of onBackground.
    val dividerColor = this.onBackground.copy(alpha = 0.12f)
    val borderColor = this.onBackground.copy(alpha = 0.15f)

    return GlanceColors(
        primary = ColorProvider(this.primary),
        secondary = ColorProvider(this.secondary),
        background = ColorProvider(this.background),
        surface = ColorProvider(this.surface),
        textPrimary = ColorProvider(this.onBackground),
        textSecondary = ColorProvider(this.grayMuted),
        success = ColorProvider(this.success),
        warning = ColorProvider(warningColor),
        error = ColorProvider(errorColor),
        divider = ColorProvider(dividerColor),
        border = ColorProvider(borderColor),
        accentRaw = this.primary,
        backgroundRaw = this.background,
        surfaceRaw = this.surface,
        textPrimaryRaw = this.onBackground
    )
}
