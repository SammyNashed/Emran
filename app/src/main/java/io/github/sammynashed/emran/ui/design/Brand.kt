package io.github.sammynashed.emran.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val EmranIndigo = Color(0xFF6366F1)
val EmranOrange = Color(0xFFF97316)
private val TILE = Color(0xFF0A0A0A)

/** The icon's pillars in its 108-unit grid: left edge and top. All are 6 wide and end at 73. */
val EMRAN_PILLARS = listOf(31f to 51f, 41f to 43f, 51f to 35f, 61f to 43f, 71f to 51f)

/**
 * Emran's launcher icon, drawn: dark tile, five pillars fading indigo to orange.
 * [rise] (one value per pillar, 1 = full height) lets the splash animate the same drawing.
 * Returns the pillars' outline, so a caller can light them (the splash's sweep).
 */
fun DrawScope.drawEmranMark(center: Offset, tilePx: Float, rise: (Int) -> Float = { 1f }): Path {
    val tl = Offset(center.x - tilePx / 2f, center.y - tilePx / 2f)
    val corner = CornerRadius(tilePx * 0.27f)
    val tileSize = Size(tilePx, tilePx)
    drawRoundRect(TILE, tl, tileSize, corner)
    drawRoundRect(
        Brush.verticalGradient(listOf(EmranIndigo.copy(alpha = 0.16f), EmranOrange.copy(alpha = 0.16f)), tl.y, tl.y + tilePx),
        tl, tileSize, corner,
    )
    drawRoundRect(Color.White.copy(alpha = 0.16f), tl, tileSize, corner, style = Stroke(1.dp.toPx()))

    // Map the icon's visible 72-unit square (18..90) onto the tile.
    val k = tilePx / 72f
    fun x(v: Float) = tl.x + (v - 18f) * k
    fun y(v: Float) = tl.y + (v - 18f) * k
    val pillars = Path()
    EMRAN_PILLARS.forEachIndexed { i, (left, top) ->
        val p = rise(i)
        if (p <= 0.001f) return@forEachIndexed
        val bottom = y(73f)
        val h = (bottom - y(top)) * p
        // Volume holds: taller than full gets thinner, shorter (a rebound) gets wider.
        val w = 6f * k * (1f - 0.9f * (p - 1f)).coerceIn(0.86f, 1.14f)
        val cx = x(left + 3f)
        val r = CornerRadius(w / 2f)
        pillars.addRoundRect(
            RoundRect(Rect(cx - w / 2f, bottom - h, cx + w / 2f, bottom), topLeft = r, topRight = r),
        )
    }
    drawPath(pillars, Brush.verticalGradient(listOf(EmranIndigo, EmranOrange), y(35f), y(73f)))
    return pillars
}

/** The icon as a still mark, for About and similar places. */
@Composable
fun EmranMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawEmranMark(center, this.size.minDimension) }
}
