package io.github.sammynashed.emran.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * How the app decides between the light and dark rendering of a preset.
 *
 * Emran was dark-only through v2.0.0. The presets are still authored dark --
 * see [toLightScheme] for how the light variants are derived -- but the choice
 * is now the user's, and [SYSTEM] respects the platform setting so the app stops
 * being a bright rectangle in a dark room at Fajr.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.find { it.name == name } ?: SYSTEM
    }
}

@Composable
fun MyApplicationTheme(
    themePreset: String = "EXPRESSIVE",
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val basePalette = getColorsByPreset(themePreset)
    val chosenColors = if (useDark) basePalette else basePalette.toLightScheme()

    val materialDynamicScheme = if (useDark) {
        darkColorScheme(
            primary = chosenColors.primary,
            secondary = chosenColors.secondary,
            tertiary = chosenColors.success,
            background = chosenColors.background,
            surface = chosenColors.surface,
            onPrimary = chosenColors.onPrimary,
            onBackground = chosenColors.onBackground,
            onSurface = chosenColors.onBackground
        )
    } else {
        lightColorScheme(
            primary = chosenColors.primary,
            secondary = chosenColors.secondary,
            tertiary = chosenColors.success,
            background = chosenColors.background,
            surface = chosenColors.surface,
            onPrimary = chosenColors.onPrimary,
            onBackground = chosenColors.onBackground,
            onSurface = chosenColors.onBackground
        )
    }

    CompositionLocalProvider(LocalAppColors provides chosenColors) {
        MaterialTheme(
            colorScheme = materialDynamicScheme,
            typography = Typography,
            content = content
        )
    }
}

fun getColorsByPreset(preset: String): AppColors {
    return when (preset) {
        "COSMIC" -> CosmicNoirThemeColors
        "TEAL" -> MidnightTealThemeColors
        "ROSE" -> SunsetRoseThemeColors
        "GOLDEN" -> GoldenOasisThemeColors
        "EMERALD" -> IceEmeraldThemeColors
        "OCEAN" -> PacificSeaThemeColors
        "AMYTHIST" -> VelvetAmethystThemeColors
        "SUNFIRE" -> SolarFlareThemeColors
        "SAPPHIRE" -> NeonSapphireThemeColors
        "LAVENDER" -> LavenderFieldThemeColors
        "MINT" -> PolarGlacierThemeColors
        "FOREST" -> PineEvergreenThemeColors
        "BLOOD_MOON" -> CrimsonMoonThemeColors
        "MONOCHROME" -> PureSlateThemeColors
        "SAKURA" -> TokyoSakuraThemeColors
        "NEBULA" -> NebulaSparkThemeColors
        "DESERT" -> SaharaNomadThemeColors
        "AURORA" -> BorealLightsThemeColors
        "BLACK_STONE" -> BlackStoneThemeColors
        else -> ExpressiveThemeColors
    }
}

/**
 * The palette actually rendered for a preset under a given mode.
 * Used by the widget bridge, which has no composition to read [AppTheme] from.
 */
fun getColorsByPreset(preset: String, isLight: Boolean): AppColors =
    getColorsByPreset(preset).let { if (isLight) it.toLightScheme() else it }
