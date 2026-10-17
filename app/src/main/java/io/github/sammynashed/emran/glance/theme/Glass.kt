package io.github.sammynashed.emran.glance.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.glance.layout.Alignment

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

