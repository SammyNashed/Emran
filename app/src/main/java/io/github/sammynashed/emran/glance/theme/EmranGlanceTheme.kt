package io.github.sammynashed.emran.glance.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.ui.theme.EmranTheme
import io.github.sammynashed.emran.ui.theme.getColorsByPreset
import io.github.sammynashed.emran.ui.theme.ExpressiveThemeColors

val LocalGlanceColors = staticCompositionLocalOf<GlanceColors> { 
    ExpressiveThemeColors.toGlanceColors() 
}

/**
 * Provides the Emran theme to Glance widgets.
 * All Glance widgets should be wrapped in this composable.
 */
@Composable
fun EmranGlanceTheme(
    widgetState: EmranWidgetState,
    content: @Composable () -> Unit
) {
    val themeToUse = if (widgetState.followsAppTheme || widgetState.themeOverride == null) {
        // The WidgetUpdater sets themeOverride to the app's current theme when followsAppTheme is true.
        widgetState.themeOverride ?: EmranTheme.EXPRESSIVE
    } else {
        widgetState.themeOverride
    }
    
    val composeColors = getColorsByPreset(themeToUse.presetName, isLight = widgetState.isLightMode)
    val glanceColors = composeColors.toGlanceColors()

    io.github.sammynashed.emran.glance.WidgetDebugLogger.d("EmranGlanceTheme [THEME TRACE]: widgetState.themeOverride=${widgetState.themeOverride}, widgetState.followsAppTheme=${widgetState.followsAppTheme}")
    io.github.sammynashed.emran.glance.WidgetDebugLogger.d("EmranGlanceTheme [THEME TRACE]: themeToUse=$themeToUse")
    io.github.sammynashed.emran.glance.WidgetDebugLogger.d("EmranGlanceTheme [THEME TRACE]: getColorsByPreset(themeToUse)=$composeColors")
    io.github.sammynashed.emran.glance.WidgetDebugLogger.d("EmranGlanceTheme [THEME TRACE]: resolved background color=${composeColors.background}, primary color=${composeColors.primary}, text color=${composeColors.onBackground}")

    CompositionLocalProvider(LocalGlanceColors provides glanceColors) {
        content()
    }
}

/**
 * Accessor for the current Glance Theme colors.
 */
object EmranGlanceTheme {
    val colors: GlanceColors
        @Composable
        get() = LocalGlanceColors.current
}
