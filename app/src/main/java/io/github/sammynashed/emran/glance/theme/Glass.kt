package io.github.sammynashed.emran.glance.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider

/**
 * The glass vocabulary shared by every Emran widget.
 *
 * A home-screen widget cannot blur what is behind it: Glance compiles to
 * RemoteViews, which has no RenderEffect, no Canvas, and no way to sample the
 * wallpaper. So the glass here is *simulated*, the same way the Android build of
 * apps like Glassify does it -- a translucent tinted pane, a hairline light
 * edge, and a soft inner wash of the accent colour. The translucency is real:
 * the widget genuinely lets the wallpaper through, which is what sells the
 * effect. What is missing is only the blur.
 */
object Glass {

    object Radius {
        val Outer: Dp = 28.dp
        val Inner: Dp = 27.dp
        val Pill: Dp = 999.dp
        val Chip: Dp = 14.dp
    }

    object Alpha {
        /** The pane itself. Low enough to read as glass, high enough for contrast. */
        const val PANE = 0.72f
        /** Hairline edge that catches "light" along the widget border. */
        const val EDGE = 0.22f
        /** Accent wash laid over the pane. */
        const val WASH = 0.10f
        /** Secondary text and labels. */
        const val MUTED = 0.55f
        /** Faint fills: progress tracks, inactive chips. */
        const val TRACK = 0.14f
    }

    object Type {
        /** Small uppercase label. Section headers, "NEXT PRAYER". */
        @Composable
        fun label(color: Color, rtl: Boolean = false) = TextStyle(
            color = ColorProvider(color.copy(alpha = Alpha.MUTED)),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = align(rtl)
        )

        /** The one number or word the widget exists to show. */
        @Composable
        fun hero(color: Color, size: Int = 34, rtl: Boolean = false) = TextStyle(
            color = ColorProvider(color),
            fontSize = size.sp,
            fontWeight = FontWeight.Bold,
            textAlign = align(rtl)
        )

        @Composable
        fun value(color: Color, size: Int = 20, rtl: Boolean = false) = TextStyle(
            color = ColorProvider(color),
            fontSize = size.sp,
            fontWeight = FontWeight.Bold,
            textAlign = align(rtl)
        )

        @Composable
        fun body(color: Color, size: Int = 13, rtl: Boolean = false) = TextStyle(
            color = ColorProvider(color),
            fontSize = size.sp,
            fontWeight = FontWeight.Normal,
            textAlign = align(rtl)
        )

        private fun align(rtl: Boolean): TextAlign =
            if (rtl) TextAlign.Right else TextAlign.Left
    }

    /**
     * Column alignment for the active language.
     *
     * Glance inherits layout direction from the *device* locale, but Emran's
     * language is an in-app setting, so a widget on an English phone set to
     * Arabic would otherwise stay left-anchored. Anchoring is therefore applied
     * explicitly rather than relying on the platform.
     */
    fun columnAlignment(rtl: Boolean): Alignment.Horizontal =
        if (rtl) Alignment.End else Alignment.Start
}

/**
 * The frosted pane every widget sits on: hairline edge, translucent tinted
 * body, accent wash.
 */
@Composable
fun GlassPane(
    modifier: GlanceModifier = GlanceModifier,
    contentPadding: Dp = 16.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit
) {
    val colors = EmranGlanceTheme.colors
    val pane = colors.backgroundRaw.copy(alpha = Glass.Alpha.PANE)
    val edge = colors.textPrimaryRaw.copy(alpha = Glass.Alpha.EDGE)
    val wash = colors.accentRaw.copy(alpha = Glass.Alpha.WASH)

    // Outer box paints the hairline edge; the 1dp padding is what makes it read
    // as a stroke rather than a fill.
    Box(
        modifier = modifier
            .background(ColorProvider(edge))
            .cornerRadius(Glass.Radius.Outer)
            .padding(1.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(pane))
                .cornerRadius(Glass.Radius.Inner)
        ) {
            // Accent wash, laid over the pane so the tint follows the app theme.
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(wash))
                    .cornerRadius(Glass.Radius.Inner)
                    .padding(contentPadding),
                contentAlignment = contentAlignment
            ) {
                content()
            }
        }
    }
}

/** A small translucent chip, for counts and accent labels. */
@Composable
fun GlassChip(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit
) {
    val colors = EmranGlanceTheme.colors
    Box(
        modifier = modifier
            .background(ColorProvider(colors.accentRaw.copy(alpha = 0.18f)))
            .cornerRadius(Glass.Radius.Chip)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
