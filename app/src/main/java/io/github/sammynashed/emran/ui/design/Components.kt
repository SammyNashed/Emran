package io.github.sammynashed.emran.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.ui.localization.Text

/** A soft spring on press, the way iOS controls give under a finger. */
@Composable
private fun rememberPress(): Pair<MutableInteractionSource, Float> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "press",
    )
    return source to scale
}

/**
 * A full-width glass row: icon, title (and optional subtitle), chevron.
 * The workhorse of the redesign: one idea per row, a 72 dp touch target.
 */
@Composable
fun GlassRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val (source, scale) = rememberPress()
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        cornerRadius = Radius.row,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = sky.ink, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = sky.ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, color = sky.inkSoft, fontSize = 13.sp)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = sky.inkSoft,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

class DockTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    /** Test tag and tour target stay attached to the tab, exactly as before. */
    val modifier: Modifier = Modifier,
)

/**
 * The floating dock: a pane of glass with the three tabs. The selected tab sits in
 * a lighter glass pill that eases between positions.
 */
@Composable
fun GlassDock(
    tabs: List<DockTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("app_bottom_nav"),
        cornerRadius = 36.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEach { tab ->
                val selected = tab.key == selectedKey
                val pill by animateColorAsState(
                    targetValue = if (selected) sky.ink.copy(alpha = 0.14f) else Color.Transparent,
                    animationSpec = spring(stiffness = 380f),
                    label = "pill",
                )
                val tint by animateColorAsState(
                    targetValue = if (selected) sky.ink else sky.inkSoft,
                    label = "tint",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(30.dp))
                        .background(pill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(tab.key)
                        }
                        .then(tab.modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        tab.label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** A round glass back button. */
@Composable
fun GlassBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    GlassPanel(
        modifier = modifier
            .size(Target.min)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onBack()
            },
        cornerRadius = Target.min / 2,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = sky.ink,
            modifier = Modifier.size(24.dp).align(Alignment.Center),
        )
    }
}

/** Back button + a large calm title; the top of every inner screen. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassBackButton(onBack)
        Spacer(Modifier.width(Space.lg))
        Text(title, color = sky.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}
