package io.github.sammynashed.emran.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
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
 * Rows rise into place one after another when a screen appears: a short spring, 55 ms
 * apart, so the page assembles itself instead of snapping in.
 */
fun Modifier.glassEntrance(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 170f))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 30.dp.toPx()
        val sc = 0.94f + 0.06f * p
        scaleX = sc; scaleY = sc
    }
}

/**
 * A full-width glass row: icon, title (and optional subtitle), chevron.
 * The workhorse of the redesign: one idea per row, a 72 dp touch target.
 * Under a finger it gives (spring scale) and brightens, like glass catching more light.
 */
@Composable
fun GlassRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /** Position in the staggered entrance; null for no entrance animation. */
    enterIndex: Int? = null,
) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val (source, scale) = rememberPress()
    val pressed by source.collectIsPressedAsState()
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1.9f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "glow",
    )
    GlassPanel(
        modifier = modifier
            .then(if (enterIndex != null) Modifier.glassEntrance(enterIndex) else Modifier)
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        cornerRadius = Radius.row,
        tint = glow,
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
 * The floating dock, with a liquid-glass lens for the selected tab.
 *
 * The lens is a second pane of glass that sits over the tabs. It
 *  - slides between tabs on a soft spring and overshoots a little,
 *  - stretches along its direction of travel and squashes across it, by how fast it moves,
 *  - lifts (grows, brightens) the moment a finger touches the dock,
 *  - follows the finger when dragged, and settles on the nearest tab when let go,
 *  - magnifies whatever is under it: the tabs are drawn twice, and the copy inside the
 *    lens is bigger and in full ink, so icons swell as the lens passes over them,
 *  - bends and blurs the sky behind it, as the panes do.
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
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Geometry is plain left-to-right; in Arabic the first tab simply sits on the right.
    val visual = if (rtl) tabs.reversed() else tabs
    val n = visual.size
    val selIndex = visual.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)

    val padPx = with(density) { 6.dp.toPx() }
    var dockW by remember { mutableFloatStateOf(0f) }
    var dockH by remember { mutableFloatStateOf(0f) }
    var dockOrigin by remember { mutableStateOf(Offset.Zero) }
    val tabW = if (dockW > 0f) (dockW - 2 * padPx) / n else 0f

    val pillX = remember { Animatable(0f) }      // left edge of the lens in the dock, px
    val lift = remember { Animatable(0f) }       // 0 resting .. 1 finger down
    val fingerSpeed = remember { Animatable(0f) } // px/s while dragging
    var dragging by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selIndex, tabW) {
        if (tabW <= 0f || dragging) return@LaunchedEffect
        val target = padPx + selIndex * tabW
        if (!placed) { pillX.snapTo(target); placed = true }
        else pillX.animateTo(target, spring(dampingRatio = 0.78f, stiffness = 48f))
    }

    val currentSel by rememberUpdatedState(selIndex)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentTabW by rememberUpdatedState(tabW)

    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .testTag("app_bottom_nav")
            .onGloballyPositioned { dockOrigin = it.positionInRoot() }
            .onSizeChanged { dockW = it.width.toFloat(); dockH = it.height.toFloat() }
            .pointerInput(n) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tw = currentTabW
                    if (tw <= 0f) return@awaitEachGesture
                    scope.launch { lift.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 220f)) }
                    var lastX = down.position.x
                    var lastT = down.uptimeMillis
                    var moved = false
                    var fingerX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                        fingerX = ch.position.x
                        if (!ch.pressed) break
                        if (!moved && abs(fingerX - down.position.x) > viewConfiguration.touchSlop) {
                            moved = true; dragging = true
                        }
                        if (moved) {
                            val dt = ((ch.uptimeMillis - lastT).coerceAtLeast(1L)) / 1000f
                            val v = abs(fingerX - lastX) / dt
                            lastX = fingerX; lastT = ch.uptimeMillis
                            ch.consume()
                            scope.launch {
                                pillX.snapTo((fingerX - tw / 2f).coerceIn(padPx, padPx + (n - 1) * tw))
                                fingerSpeed.snapTo(v)
                                fingerSpeed.animateTo(0f, tween(220))
                            }
                        }
                    }
                    dragging = false
                    val idx = ((fingerX - padPx) / tw).toInt().coerceIn(0, n - 1)
                    scope.launch { lift.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                    scope.launch { pillX.animateTo(padPx + idx * tw, spring(dampingRatio = 0.78f, stiffness = 48f)) }
                    if (idx != currentSel) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentSelect(visual[idx].key)
                    }
                }
            },
        cornerRadius = 36.dp,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // The tabs as they rest: soft ink, outline icons.
            DockRow(visual, selIndex, magnified = false, sky = sky, onSelect = onSelect, interactive = true)

            if (tabW > 0f) {
                val tabWDp = with(density) { tabW.toDp() }
                val dockWDp = with(density) { dockW.toDp() }
                val innerH = 60.dp
                GlassPanel(
                    modifier = Modifier
                        .offset(y = 6.dp)
                        .size(tabWDp, innerH)
                        .graphicsLayer {
                            translationX = pillX.value
                            // Liquid: stretch along the way it is moving, squash across it.
                            val speed = maxOf(abs(pillX.velocity), fingerSpeed.value)
                            val stretch = (speed / 5200f).coerceAtMost(0.24f)
                            val l = lift.value
                            val grow = 1f + 0.13f * l
                            scaleX = (1f + stretch) * grow
                            scaleY = (1f - stretch * 0.45f) * grow
                        },
                    cornerRadius = 30.dp,
                    tint = 1.35f,
                    skyOrigin = { dockOrigin + Offset(pillX.value, with(density) { 6.dp.toPx() }) },
                ) {
                    // The same tabs again, inside the lens: full ink, selected icons, magnified.
                    Box(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .size(dockWDp, innerH)
                            .graphicsLayer {
                                translationX = -pillX.value
                                // Keep icons undistorted when the lens itself stretches.
                                val speed = maxOf(abs(pillX.velocity), fingerSpeed.value)
                                val stretch = (speed / 5200f).coerceAtMost(0.24f)
                                val mag = 1.05f + 0.06f * lift.value
                                scaleX = mag / (1f + stretch)
                                scaleY = mag / (1f - stretch * 0.45f)
                                transformOrigin = TransformOrigin(
                                    ((pillX.value + tabW / 2f) / dockW).coerceIn(0f, 1f), 0.5f,
                                )
                            },
                    ) {
                        DockRow(visual, selIndex, magnified = true, sky = sky, onSelect = {}, interactive = false)
                    }
                    // Light pooling in the lens while it is held.
                    Box(
                        Modifier.matchParentSize().drawBehind {
                            val l = lift.value
                            if (l > 0.01f) drawRect(Color.White.copy(alpha = 0.16f * l))
                        },
                    )
                }
            }
        }
    }
}

/** One row of tabs. Drawn twice by [GlassDock]: at rest, and magnified inside the lens. */
@Composable
private fun DockRow(
    tabs: List<DockTab>,
    selIndex: Int,
    magnified: Boolean,
    sky: SkyState,
    onSelect: (String) -> Unit,
    interactive: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = if (magnified) 0.dp else 6.dp),
    ) {
        tabs.forEachIndexed { i, tab ->
            val selected = i == selIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(
                        if (interactive) Modifier
                            .semantics(mergeDescendants = true) {
                                this.selected = selected
                                role = Role.Tab
                                onClick { onSelect(tab.key); true }
                            }
                            .then(tab.modifier)
                        else Modifier,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = if (magnified) sky.ink else sky.inkSoft
                Icon(
                    if (magnified) tab.selectedIcon else tab.icon,
                    contentDescription = if (interactive) tab.label else null,
                    tint = tint,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    tab.label,
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = if (magnified) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** A round glass back button. */
@Composable
fun GlassBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val (source, scale) = rememberPress()
    GlassPanel(
        modifier = modifier
            .size(Target.min)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null) {
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
