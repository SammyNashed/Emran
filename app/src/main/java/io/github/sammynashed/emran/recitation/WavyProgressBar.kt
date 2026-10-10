package io.github.sammynashed.emran.recitation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The progress bar Recitation Check downloads behind, drawn in the expressive
 * style Android 16 uses for media playback: a wavy active track, a gap, a flat
 * remaining track, and a stop indicator at the end.
 *
 * It is drawn by hand rather than taken from Material 3. `LinearWavyProgressIndicator`
 * only exists in the expressive alphas, and Emran is on the stable Compose BOM;
 * moving the whole app onto an alpha to get one bar would be a poor trade.
 *
 * The waves carry meaning rather than decoration:
 *  - moving and rippling  -> bytes are arriving
 *  - flat and still       -> paused, exactly as the Android 16 player flattens
 *                            its wave when playback stops
 *
 * so a paused download reads as paused at a glance, without reading the label.
 */
@Composable
fun WavyProgressBar(
    /** 0f..1f. */
    progress: Float,
    /** False flattens the wave and stops it moving - the paused look. */
    animating: Boolean,
    /**
     * Nothing is known about the total size yet, so a sweeping segment runs
     * the length of the track instead of a fill. A determinate bar parked at
     * zero cannot be told apart from a frozen one, which is the whole reason
     * this mode exists.
     */
    indeterminate: Boolean = false,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val target = progress.coerceIn(0f, 1f)
    // Bytes arrive in bursts; the bar should not.
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 260, easing = LinearEasing),
        label = "download-progress",
    )
    val amplitude by animateFloatAsState(
        targetValue = if (animating) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "download-wave-amplitude",
    )
    val forever = rememberInfiniteTransition(label = "download-wave")
    val phase by forever.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
        label = "download-wave-phase",
    )
    val sweep by forever.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing)),
        label = "download-sweep",
    )

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Canvas(
        modifier
            .fillMaxWidth()
            .height(WAVE_HEIGHT),
    ) {
        // Everything below is written left-to-right; in Arabic the whole bar is
        // mirrored so it fills towards the same side the text runs.
        scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
            if (indeterminate) {
                drawSweep(sweep, amplitude, phase, color, trackColor)
            } else {
                drawBar(shown, amplitude, phase, color, trackColor)
            }
        }
    }
}

private fun DrawScope.drawBar(
    progress: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    trackColor: Color,
) {
    val stroke = STROKE.toPx()
    val gap = GAP.toPx()
    val wavelength = WAVELENGTH.toPx()
    val peak = AMPLITUDE.toPx() * amplitude
    val midY = size.height / 2f

    val left = stroke / 2f
    val right = size.width - stroke / 2f
    // The stop indicator owns the far end; the track stops short of it.
    val dotX = right
    val trackRight = dotX - stroke / 2f - gap

    val activeEnd = left + (trackRight - left) * progress

    if (activeEnd > left) {
        val path = Path().apply {
            moveTo(left, midY + waveAt(left, wavelength, phase, peak))
            var x = left + STEP
            while (x < activeEnd) {
                lineTo(x, midY + waveAt(x, wavelength, phase, peak))
                x += STEP
            }
            lineTo(activeEnd, midY + waveAt(activeEnd, wavelength, phase, peak))
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }

    val trackStart = activeEnd + gap
    if (trackStart < trackRight) {
        drawLine(
            color = trackColor,
            start = androidx.compose.ui.geometry.Offset(trackStart, midY),
            end = androidx.compose.ui.geometry.Offset(trackRight, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }

    drawCircle(
        color = color,
        radius = stroke / 2f,
        center = androidx.compose.ui.geometry.Offset(dotX, midY),
    )
}

/**
 * The indeterminate form: a short wavy segment travelling the track.
 *
 * It runs off the end and reappears rather than bouncing, so the motion always
 * reads as "still working" and never as a value creeping towards a total that
 * is not known.
 */
private fun DrawScope.drawSweep(
    position: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    trackColor: Color,
) {
    val stroke = STROKE.toPx()
    val wavelength = WAVELENGTH.toPx()
    val peak = AMPLITUDE.toPx() * amplitude
    val midY = size.height / 2f
    val left = stroke / 2f
    val right = size.width - stroke / 2f

    drawLine(
        color = trackColor,
        start = androidx.compose.ui.geometry.Offset(left, midY),
        end = androidx.compose.ui.geometry.Offset(right, midY),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )

    val span = right - left
    val width = span * SWEEP_FRACTION
    // Travel a full span plus the segment's own length, so it leaves cleanly
    // before it re-enters.
    val head = left + (span + width) * position
    val start = (head - width).coerceAtLeast(left)
    val end = head.coerceAtMost(right)
    if (end <= start) return

    val path = Path().apply {
        moveTo(start, midY + sin(start / wavelength * 2f * PI.toFloat() + phase) * peak)
        var x = start + STEP
        while (x < end) {
            lineTo(x, midY + sin(x / wavelength * 2f * PI.toFloat() + phase) * peak)
            x += STEP
        }
        lineTo(end, midY + sin(end / wavelength * 2f * PI.toFloat() + phase) * peak)
    }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

/**
 * The wave tapers to nothing over the first wavelength so the bar starts from
 * a clean point instead of a stump, the way the Android 16 player does.
 */
private fun waveAt(x: Float, wavelength: Float, phase: Float, peak: Float): Float {
    if (peak == 0f) return 0f
    val ramp = (x / wavelength).coerceIn(0f, 1f)
    return sin(x / wavelength * 2f * PI.toFloat() + phase) * peak * ramp
}

private val STROKE = 4.dp
private val GAP = 5.dp
private val WAVELENGTH = 22.dp
private val AMPLITUDE = 3.dp
private val WAVE_HEIGHT = 16.dp
private const val STEP = 2f
private const val SWEEP_FRACTION = 0.35f
