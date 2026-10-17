package io.github.sammynashed.emran.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import io.github.sammynashed.emran.ui.components.EmranDialog
import io.github.sammynashed.emran.ui.localization.Text

/** The paper of a dialog: deep navy under a dark sky, soft white under a bright one. */
@Composable
fun dialogPanelColor(): Color {
    val sky = LocalSky.current
    return lerp(Color(0xFF0D1828), Color(0xFFF1F6FC), sky.darkInk)
}

/**
 * A calm dialog in the language of the rest of the app: a rounded pane that follows the sky
 * (dark by night, light by day), a title, a body that scrolls if it must, and at most two
 * actions. It opens with a soft spring instead of appearing.
 */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sky = LocalSky.current
    val panel = dialogPanelColor()
    val shape = RoundedCornerShape(32.dp)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 320f)) }

    EmranDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .padding(horizontal = 22.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    val sc = 0.9f + 0.1f * p
                    scaleX = sc; scaleY = sc
                }
                .clip(shape)
                .background(panel.copy(alpha = 0.97f))
                .border(1.dp, sky.ink.copy(alpha = 0.16f), shape)
                .drawBehind {
                    // A little light falling from the top edge.
                    drawRect(
                        Brush.verticalGradient(
                            listOf(sky.ink.copy(alpha = 0.07f), Color.Transparent),
                            startY = 0f, endY = size.height * 0.35f,
                        ),
                    )
                }
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Text(title, color = sky.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.size(4.dp))
                Text(subtitle, color = sky.inkSoft, fontSize = 14.sp, lineHeight = 19.sp)
            }
            Spacer(Modifier.size(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
            if (confirmLabel != null || dismissLabel != null) {
                Spacer(Modifier.size(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissLabel != null) GlassButton(dismissLabel, onClick = onDismiss, filled = false)
                    if (confirmLabel != null && onConfirm != null) GlassButton(confirmLabel, onClick = onConfirm, filled = true)
                }
            }
        }
    }
}

/** A pill button: filled (ink on the pane) for the main action, quiet text for the other. */
@Composable
fun GlassButton(label: String, onClick: () -> Unit, filled: Boolean, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val panel = dialogPanelColor()
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .then(if (filled) Modifier.background(sky.ink) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) panel else sky.inkSoft,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One choice in a list: a label (and a quieter line under it), and a check when chosen.
 * The chosen row rests in a soft wash of ink.
 */
@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val panel = dialogPanelColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) sky.ink.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = sky.ink,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (sublabel != null) Text(sublabel, color = sky.inkSoft, fontSize = 12.5.sp, lineHeight = 16.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) sky.ink else Color.Transparent)
                .border(1.5.dp, if (selected) Color.Transparent else sky.ink.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = panel, modifier = Modifier.size(15.dp))
        }
    }
}

/** A list dialog that picks one thing and closes: method, school, rule. */
@Composable
fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    GlassDialog(onDismiss = onDismiss, title = title, subtitle = subtitle) {
        options.forEach { (key, label) ->
            ChoiceRow(label = label, selected = key == selectedKey, onClick = { onPick(key) })
        }
    }
}
