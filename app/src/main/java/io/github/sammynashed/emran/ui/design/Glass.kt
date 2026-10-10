package io.github.sammynashed.emran.ui.design

import android.graphics.RuntimeShader
import android.graphics.Shader
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
 *  - LENS  (API 33+): the backdrop is blurred AND bent at the edges (refraction with
 *    a hint of colour fringing), like a real glass lens.
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
    half4 r = content.eval(fragCoord + o * 1.00);
    half4 g = content.eval(fragCoord + o * 0.90);
    half4 b = content.eval(fragCoord + o * 0.80);
    return half4(r.r, g.g, b.b, 1.0);
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
        shader.setFloatUniform("bend", 16f * density)
        shader.setFloatUniform("edge", 30f * density)
        val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        val blur = android.graphics.RenderEffect.createBlurEffect(2.5f * density, 2.5f * density, Shader.TileMode.CLAMP)
        val chained = android.graphics.RenderEffect.createChainEffect(lens, blur).asComposeRenderEffect()
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
    content: @Composable BoxScope.() -> Unit,
) {
    val sky = LocalSky.current
    val skySize = LocalSkySize.current
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val tier = currentGlassTier()
    // A shader that fails to compile on some GPU must cost the lens, never the app.
    val lens = remember(tier) {
        if (tier == GlassTier.LENS && Build.VERSION.SDK_INT >= 33) runCatching { LensEffect() }.getOrNull() else null
    }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(RoundedCornerShape(cornerRadius)),
    ) {
        // 1. The sky behind the glass, blurred and (on API 33+) bent at the edges.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    clip = true
                    renderEffect = when (tier) {
                        GlassTier.LENS -> (if (Build.VERSION.SDK_INT >= 33)
                            lens?.let { runCatching { it.effect(size.width, size.height, radiusPx, density.density) }.getOrNull() }
                        else null)
                            ?: BlurEffect(2.5f * density.density, 2.5f * density.density, TileMode.Clamp)
                        GlassTier.BLUR -> BlurEffect(2.5f * density.density, 2.5f * density.density, TileMode.Clamp)
                        GlassTier.FLAT -> null
                    }
                }
                .drawBehind { translate(-origin.x, -origin.y) { drawSky(sky, skySize) } },
        )
        // 2. Tint, top-light and the specular rim.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawGlassChrome(sky, tint, radiusPx, density.density) },
        )
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassChrome(
    sky: SkyState,
    tint: Float,
    radiusPx: Float,
    density: Float,
) {
    val a = (sky.glassAlpha * tint).coerceIn(0f, 0.7f)
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = a * 1.25f), Color.White.copy(alpha = a * 0.7f))),
    )
    // Light falling on the top edge.
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.18f * tint),
            0.45f to Color.Transparent,
        ),
    )
    val rim = 1.25f * density
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.80f),
            0.42f to Color.White.copy(alpha = 0.16f),
            0.58f to Color.White.copy(alpha = 0.10f),
            1f to Color.White.copy(alpha = 0.46f),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        topLeft = Offset(rim / 2, rim / 2),
        size = Size(size.width - rim, size.height - rim),
        cornerRadius = CornerRadius(radiusPx - rim / 2),
        style = Stroke(rim),
    )
}
