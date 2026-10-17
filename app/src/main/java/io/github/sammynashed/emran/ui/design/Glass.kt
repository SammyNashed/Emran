package io.github.sammynashed.emran.ui.design

import android.graphics.RuntimeShader

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.graphics.BlurEffect

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect

import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * How much of the liquid-glass effect this device can draw.
 *
 * minSdk is 24 and AGSL needs 33, so glass degrades instead of disappearing:
 *  - LENS  (API 33+): the backdrop is blurred AND bent at the edges (refraction), like a real glass lens.
 *  - BLUR  (API 31-32): the backdrop is blurred, no bending.
 *  - FLAT  (below 31): a clean translucent tint with the same rim light.
 * Every tier keeps the rim and the tint, so the app is recognisably the same.
 */
enum class GlassTier { FLAT, BLUR, LENS }

fun currentGlassTier(): GlassTier = SkyDebug.glassTier ?: when {
    Build.VERSION.SDK_INT >= 33 -> GlassTier.LENS
    Build.VERSION.SDK_INT >= 31 -> GlassTier.BLUR
    else -> GlassTier.FLAT
}

// A rounded-rectangle signed-distance field gives the edge direction; samples are
// pulled from just inside the edge so the backdrop looks magnified and bent there.
private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bend;
uniform float edge;

float sdBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 fragCoord) {
    float2 hs = size * 0.5;
    float2 p = fragCoord - hs;
    float d = sdBox(p, hs, radius);
    float t = clamp(-d / edge, 0.0, 1.0);
    float k = (1.0 - t);
    k = k * k;
    float2 e = float2(1.0, 0.0);
    float2 n = float2(
        sdBox(p + e.xy, hs, radius) - sdBox(p - e.xy, hs, radius),
        sdBox(p + e.yx, hs, radius) - sdBox(p - e.yx, hs, radius));
    n = n / max(length(n), 0.0001);
    float2 o = -n * bend * k;
    // One sample for all three colours. A version that split red/green/blue slightly
    // (chromatic dispersion) looked like real glass over a still image, but over a
    // moving sky of clouds and stars it threw rainbow fringes around every pane.
    return half4(content.eval(fragCoord + o).rgb, 1.0);
}
"""

@RequiresApi(33)
private class LensEffect {
    private val shader = RuntimeShader(LENS_AGSL)
    private var cached: RenderEffect? = null
    private var w = -1f
    private var h = -1f
    private var r = -1f

    fun effect(width: Float, height: Float, radiusPx: Float, density: Float): RenderEffect {
        val hit = cached
        if (hit != null && width == w && height == h && radiusPx == r) return hit
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", radiusPx)
        shader.setFloatUniform("bend", 24f * density)
        shader.setFloatUniform("edge", 38f * density)
        val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        // No extra blur pass here: the sky texture is already soft, and one fewer full-pane
        // pass per glass pane is what keeps 30 fps affordable.
        val chained = lens.asComposeRenderEffect()
        cached = chained; w = width; h = height; r = radiusPx
        return chained
    }
}

/**
 * A pane of liquid glass.
 *
 * Glass needs something to look through. Because the sky is procedural, the panel
 * simply paints its own copy of the sky behind itself, shifted so it lines up with
 * the real one, and bends/blurs THAT. This avoids capturing the screen, costs one
 * small layer per panel, and is why glass is kept to a few surfaces.
 *
 * Panels assume they do not scroll against the sky (their position is read once per
 * layout). Long scrolling lists use [Modifier] tints instead of glass.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Radius.row,
    /** Scales the white tint: 1 is normal, lower is clearer glass. */
    tint: Float = 1f,
    /**
     * False for panes that scroll (long lists): they keep the tint and the rim but skip the
     * bent-sky backdrop, because a copy of the sky aligned once at layout would drift out of
     * register as the row moves.
     */
    lens: Boolean = true,
    /**
     * Where this pane's top-left sits in the root, when it MOVES by a graphics layer (the dock's
     * sliding lens). Layout-based positioning would not see the move, so the owner supplies it.
     */
    skyOrigin: (() -> Offset)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val sky = LocalSky.current
    val skySize = LocalSkySize.current
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val tier = currentGlassTier()
    // A shader that fails to compile on some GPU must cost the lens, never the app.
    val lensEffect = remember(tier) {
        if (tier == GlassTier.LENS && Build.VERSION.SDK_INT >= 33) runCatching { LensEffect() }.getOrNull() else null
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val skyLayer = LocalSkyLayer.current
    val clock = LocalSkySlowTime.current

    Box(
        modifier = modifier
            .then(if (lens) Modifier.onGloballyPositioned { origin = it.positionInRoot() } else Modifier)
            // Drawn before the clip, so the soft shadow can fall outside the glass.
            .then(if (lens) Modifier.drawBehind { drawGlassShadow(sky, radiusPx, density.density) } else Modifier)
            .clip(RoundedCornerShape(cornerRadius)),
    ) {
        // 1. The sky behind the glass, blurred and (on API 33+) bent at the edges.
        if (lens) Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    clip = true
                    renderEffect = when (tier) {
                        GlassTier.LENS -> (if (Build.VERSION.SDK_INT >= 33)
                            lensEffect?.let { runCatching { it.effect(size.width, size.height, radiusPx, density.density) }.getOrNull() }
                        else null)
                            ?: BlurEffect(2.5f * density.density, 2.5f * density.density, TileMode.Clamp)
                        GlassTier.BLUR -> BlurEffect(2.5f * density.density, 2.5f * density.density, TileMode.Clamp)
                        GlassTier.FLAT -> null
                    }
                }
                .drawBehind {
                    val o = skyOrigin?.invoke() ?: origin
                    translate(-o.x, -o.y) { drawSkyTexture(skyLayer, sky, skySize, clock.value, detail = false) }
                },
        )
        // 2. Tint, top-light and the specular rim.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawFrost(sky, radiusPx, density.density, moving = !lens)
                    drawGlassChrome(sky, tint, radiusPx, density.density)
                },
        )
        content()
    }
}

/**
 * The frost every pane carries: a steady film (deep navy by night, soft white by day) so the
 * sky shows through only faintly and text always has a calm ground, then a hairline rim.
 * Panes that move ([moving], no bent sky behind them) get a slightly denser film instead.
 */
internal fun DrawScope.drawFrost(sky: SkyState, radiusPx: Float, density: Float, moving: Boolean = true) {
    val night = Color(0xFF0A1630).copy(alpha = if (moving) 0.42f else 0.34f)
    val day = Color.White.copy(alpha = if (moving) 0.50f else 0.42f)
    drawRect(lerp(night, day, sky.darkInk))
    drawRect(
        Brush.verticalGradient(listOf(sky.ink.copy(alpha = 0.05f), Color.Transparent), startY = 0f, endY = size.height * 0.45f),
    )
    val stroke = 0.8f * density
    drawRoundRect(
        sky.ink.copy(alpha = 0.12f),
        topLeft = Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radiusPx - stroke / 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
    )
}

/** [drawFrost] as a modifier, for a card that is a Row or Column rather than a [GlassPanel]. */
fun Modifier.glassSurface(cornerRadius: Dp = Radius.row): Modifier = composed {
    val sky = LocalSky.current
    val density = LocalDensity.current
    val r = with(density) { cornerRadius.toPx() }
    this
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind { drawFrost(sky, r, density.density) }
}

/**
 * A soft shadow under the pane, drawn only OUTSIDE its outline (the inside is glass,
 * and a shadow showing through it would turn it grey). On bright skies this is what
 * lifts a clear pane off the page.
 */
private fun DrawScope.drawGlassShadow(sky: SkyState, radiusPx: Float, density: Float) {
    val path = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusPx)))
    }
    val shadow = lerp(Color.Black.copy(alpha = 0.34f), Color(0xFF0B1B33).copy(alpha = 0.24f), sky.darkInk)
    clipPath(path, ClipOp.Difference) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.BLACK
                setShadowLayer(24f * density, 0f, 10f * density, shadow.toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radiusPx, radiusPx, paint)
        }
    }
}

/**
 * The pane has no outline and no white film: just the sky bending through it and a soft shadow.
 * The only thing drawn on top is a faint wash while the pane is pressed or chosen (tint above 1), so
 * it still answers a finger.
 */
private fun DrawScope.drawGlassChrome(
    sky: SkyState,
    tint: Float,
    radiusPx: Float,
    density: Float,
) {
    val wash = ((tint - 1f) * 0.10f).coerceIn(0f, 0.12f)
    if (wash > 0f) drawRect(Color.White.copy(alpha = wash))
}
