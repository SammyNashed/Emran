@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package io.github.sammynashed.emran.ui.tour

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.ui.theme.AppTheme

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch

class TourTargetRegistry {
    private val targets = mutableMapOf<String, Rect>()
    private val owners = mutableMapOf<String, Any>()
    private val requesters = mutableMapOf<String, BringIntoViewRequester>()
    var onTargetUpdated: ((String, Rect) -> Unit)? = null

    fun register(id: String, coordinates: LayoutCoordinates, owner: Any) {
        val bounds = coordinates.boundsInRoot()
        targets[id] = bounds
        owners[id] = owner
        onTargetUpdated?.invoke(id, bounds)
    }

    /**
     * Only the composable that last registered [id] may remove it. Several screens use the
     * same id ("sub_screen_back_button"), and when one screen replaces another the outgoing
     * one is disposed *after* the incoming one has registered; removing by id alone erased
     * the new screen's target, leaving the spotlight empty and every tap blocked.
     */
    fun unregister(id: String, owner: Any) {
        if (owners[id] === owner) {
            targets.remove(id)
            owners.remove(id)
            requesters.remove(id)
        }
    }

    fun getBounds(id: String): Rect? = targets[id]
    
    fun getRequester(id: String): BringIntoViewRequester {
        return requesters.getOrPut(id) { BringIntoViewRequester() }
    }
}

val LocalTourRegistry = staticCompositionLocalOf<TourTargetRegistry> { error("No registry") }

val LocalTourManager = staticCompositionLocalOf<TourManager?> { null }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.tourTarget(id: String): Modifier {
    val registry = LocalTourRegistry.current
    val requester = remember(registry, id) { registry.getRequester(id) }
    val owner = remember { Any() }
    DisposableEffect(registry, id, owner) {
        onDispose {
            registry.unregister(id, owner)
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            if (coordinates.isAttached) {
                registry.register(id, coordinates, owner)
            }
        }
}

@Composable
fun TourOverlay(
    tourManager: TourManager,
    registry: TourTargetRegistry
) {
    val isActive by tourManager.isActive.collectAsState()
    val currentStepIndex by tourManager.currentStepIndex.collectAsState()
    val successStepId by tourManager.successStepId.collectAsState()
    val lang = LocalAppLanguage.current

    if (!isActive || tourManager.steps.isEmpty()) return

    val currentStep = tourManager.steps.getOrNull(currentStepIndex) ?: return
    val currentTargetId = if (currentStep.id == "qibla_compass_status") "qibla_compass_dial" else currentStep.id
    var targetBounds by remember { mutableStateOf(registry.getBounds(currentTargetId)) }

    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(currentStepIndex) {
        dragOffsetX = 0f
        dragOffsetY = 0f
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(successStepId) {
        if (successStepId != null) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(1800)
            tourManager.nextStep()
            tourManager.clearSuccess()
        }
    }

    // Re-check bounds when step changes
    LaunchedEffect(currentStep.id) {
        val targetId = currentStep.id
        
        // 1. Set listener immediately so we catch all position changes
        registry.onTargetUpdated = { id, bounds ->
            if (id == targetId) {
                targetBounds = bounds
            }
        }
        
        // 2. Fetch initial bounds
        targetBounds = registry.getBounds(targetId)
        
        // 3. Wait for layout/transition to stabilize before requesting bringIntoView
        kotlinx.coroutines.delay(250)
        try {
            registry.getRequester(targetId).bringIntoView()
        } catch (e: Exception) {
            // Might not be attached yet, ignore
        }
        
        // 4. Fetch bounds again to ensure accuracy after any layout/scrolling adjustment
        targetBounds = registry.getBounds(targetId)
    }

    // A step marked optional describes something that only exists in some states (the Qada
    // onboarding screen, say, which nobody sees twice). If it has not appeared once the
    // screen has settled, move on instead of explaining something that is not there.
    LaunchedEffect(currentStepIndex) {
        val step = tourManager.steps.getOrNull(currentStepIndex)
        if (step != null && step.optional) {
            kotlinx.coroutines.delay(1100)
            if (tourManager.currentStepIndex.value == currentStepIndex && registry.getBounds(step.id) == null) {
                tourManager.nextStep()
            }
        }
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(500),
        label = "TourAlpha"
    )

    // Pulsing outer glow specs for the spotlight
    val infiniteTransition = rememberInfiniteTransition(label = "SpotlightPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    if (animatedAlpha > 0f) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
            val density = LocalDensity.current
            val screenHeight = constraints.maxHeight.toFloat()
            
            val bounds = targetBounds
            val spotlightPrimaryColor = AppTheme.colors.primary
            val spotlightSecondaryColor = AppTheme.colors.secondary
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                    if (bounds != null) {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                rect = bounds.inflate(12f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx())
                            )
                        )
                    }
                    fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                }
                // Luxurious dark space-tinted translucent backdrop
                drawPath(path, Color(0xFF03050B).copy(alpha = 0.82f * animatedAlpha))

                // Beautiful Spotlight Halo & Sweeping Gradient Ring
                if (bounds != null) {
                    val cornerRadiusPx = 16.dp.toPx()
                    val centerOffset = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)

                    // 1. Outer breathing radiant halo
                    drawRoundRect(
                        color = spotlightPrimaryColor.copy(alpha = pulseAlpha * 0.38f * animatedAlpha),
                        topLeft = Offset(
                            bounds.left - 12f - (16f * (pulseScale - 1f)),
                            bounds.top - 12f - (16f * (pulseScale - 1f))
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            bounds.width + 24f + (32f * (pulseScale - 1f)),
                            bounds.height + 24f + (32f * (pulseScale - 1f))
                        ),
                        cornerRadius = CornerRadius(
                            cornerRadiusPx + (16f * (pulseScale - 1f)),
                            cornerRadiusPx + (16f * (pulseScale - 1f))
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )

                    // 2. High-contrast premium dual sweep-gradient border outline
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                spotlightPrimaryColor,
                                spotlightSecondaryColor,
                                spotlightPrimaryColor
                            ),
                            center = centerOffset
                        ),
                        topLeft = Offset(bounds.left - 12f, bounds.top - 12f),
                        size = androidx.compose.ui.geometry.Size(bounds.width + 24f, bounds.height + 24f),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Target boundaries in pixels to handle blocking / click-through logic
            val screenWidth = constraints.maxWidth.toFloat()
            if (currentStep.type == TourStepType.INFORMATIONAL) {
                // For informational steps, tap anywhere to proceed
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentStep) {
                            detectTapGestures {
                                tourManager.nextStep()
                            }
                        }
                )
            } else {
                // Interactive step: Block touches outside the target bounds to guide attention,
                // while leaving the highlighted target area 100% click-through!
                if (bounds == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { /* Eat interaction */ }
                            }
                    )
                } else {
                    val boundsLeft = bounds.left.coerceAtLeast(0f)
                    val boundsTop = bounds.top.coerceAtLeast(0f)
                    val boundsRight = bounds.right.coerceAtMost(screenWidth)
                    val boundsBottom = bounds.bottom.coerceAtMost(screenHeight)

                    // Top blocker
                    if (boundsTop > 0f) {
                        Box(
                            modifier = Modifier
                                .size(
                                    width = with(density) { screenWidth.toDp() },
                                    height = with(density) { boundsTop.toDp() }
                                )
                                .offset(x = 0.dp, y = 0.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures { /* Eat interaction */ }
                                }
                        )
                    }
                    // Bottom blocker
                    if (screenHeight > boundsBottom) {
                        Box(
                            modifier = Modifier
                                .size(
                                    width = with(density) { screenWidth.toDp() },
                                    height = with(density) { (screenHeight - boundsBottom).coerceAtLeast(0f).toDp() }
                                )
                                .offset(x = 0.dp, y = with(density) { boundsBottom.toDp() })
                                .pointerInput(Unit) {
                                    detectTapGestures { /* Eat interaction */ }
                                }
                        )
                    }
                    // Left blocker
                    if (boundsLeft > 0f && (boundsBottom - boundsTop) > 0f) {
                        Box(
                            modifier = Modifier
                                .size(
                                    width = with(density) { boundsLeft.toDp() },
                                    height = with(density) { (boundsBottom - boundsTop).coerceAtLeast(0f).toDp() }
                                )
                                .offset(x = 0.dp, y = with(density) { boundsTop.toDp() })
                                .pointerInput(Unit) {
                                    detectTapGestures { /* Eat interaction */ }
                                }
                        )
                    }
                    // Right blocker
                    val rightWidth = screenWidth - boundsRight
                    if (rightWidth > 0f && (boundsBottom - boundsTop) > 0f) {
                        Box(
                            modifier = Modifier
                                .size(
                                    width = with(density) { rightWidth.toDp() },
                                    height = with(density) { (boundsBottom - boundsTop).coerceAtLeast(0f).toDp() }
                                )
                                .offset(x = with(density) { boundsRight.toDp() }, y = with(density) { boundsTop.toDp() })
                                .pointerInput(Unit) {
                                    detectTapGestures { /* Eat interaction */ }
                                }
                        )
                    }
                }
            }

            // Tooltip logic
            val isBoundsTall = bounds != null && (bounds.bottom - bounds.top) > screenHeight * 0.45f
            // Put the card on the side of the spotlight where it actually fits. Choosing by which
            // half of the screen the target sits in put it below a target in the upper half even
            // when that left no room, so the card ran off the bottom and its buttons could not be
            // reached (the Dhikr counter step).
            val cardHeightPx = with(density) { 380.dp.toPx() }
            val alignBottom = if (isBoundsTall) {
                true
            } else if (bounds == null) {
                false
            } else {
                val roomBelow = screenHeight - bounds.bottom - 24f
                val roomAbove = bounds.top - 24f
                when {
                    roomBelow >= cardHeightPx -> false
                    roomAbove >= cardHeightPx -> true
                    else -> roomAbove > roomBelow
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .align(if (alignBottom) Alignment.BottomCenter else Alignment.TopCenter)
                        .padding(
                            top = if (isBoundsTall) {
                                0.dp
                            } else {
                                if (!alignBottom && bounds != null) with(density) { (bounds.bottom + 24f).toDp() } 
                                else if (!alignBottom && bounds == null) with(density) { (screenHeight / 2).toDp() } else 0.dp
                            },
                            bottom = if (isBoundsTall) {
                                120.dp
                            } else {
                                if (alignBottom && bounds != null) with(density) { (screenHeight - bounds.top + 24f).toDp() } else 0.dp
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        val isSuccess = successStepId == currentStep.id
                        Box(
                            modifier = Modifier
                                .widthIn(max = 400.dp)
                                .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX += dragAmount.x
                                        dragOffsetY += dragAmount.y
                                    }
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* Eat clicks inside the card to prevent pass-through to screen components underneath */ }
                                )
                                .liquidGlassCard(
                                    shape = RoundedCornerShape(24.dp),
                                    accentColor = if (isSuccess) Color(0xFF10B981) else AppTheme.colors.primary
                                )
                                .background(
                                    Brush.verticalGradient(
                                        colors = if (isSuccess) {
                                            listOf(
                                                Color(0xFF064E3B).copy(alpha = 0.95f),
                                                Color(0xFF022C22).copy(alpha = 0.98f)
                                            )
                                        } else {
                                            listOf(
                                                AppTheme.colors.surface.copy(alpha = 0.94f),
                                                AppTheme.colors.surface.copy(alpha = 0.98f)
                                            )
                                        }
                                    )
                                )
                        ) {
                            CompositionLocalProvider(
                                androidx.compose.ui.platform.LocalLayoutDirection provides (if (lang == "ar") androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp)
                                ) {
                                    if (isSuccess) {
                                        // Gorgeous Premium Success State
                                        val successTitle = if (lang == "ar") "اكتمل التحقق بنجاح!" else "Action Confirmed!"
                                        val successDesc = when (currentStep.id) {
                                            "qibla_compass_status" -> if (lang == "ar") "تمت محاذاة القبلة بدقة متناهية باتجاه الكعبة المشرفة." else "Excellent alignment! Device precisely synchronized with Mecca."
                                            "prayers_first_row" -> if (lang == "ar") "رائع! تم تسجيل الصلاة في وقتها بنجاح. تقبل الله طاعاتكم." else "Excellent! Your prayer has been recorded on time. May your devotion be accepted."
                                            "dhikr_main_clicker" -> if (lang == "ar") "تم تسجيل التسبيح بنجاح. بدأت طمأنينة قلبك بذكر الله." else "Praise recorded. Your spiritual remembrance flow has started."
                                            "dhikr_next_preset" -> if (lang == "ar") "تم تبديل الذكر بنجاح." else "Dhikr switch confirmed. Transitioning praise cycle."
                                            "qada_onboarding_question" -> if (lang == "ar") "ممتاز! هنا تُدخل عدد فوائتك أو تقدّره بالاستبيان." else "Great! Here you enter your missed-prayer count, or estimate it with the questionnaire."
                                            "dhikr_fab_add" -> if (lang == "ar") "مستعد للتخصيص. أضف أذكاراً مخصصة تفيض بها مشاعرك." else "Dhikr creator active. Personalize your custom rosary."
                                            else -> if (lang == "ar") "تم إنجاز الخطوة بنجاح!" else "Step successfully completed!"
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            // Glowing animated circular checkmark
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .background(
                                                        Color(0xFF10B981).copy(alpha = 0.15f),
                                                        shape = CircleShape
                                                    )
                                                    .border(
                                                        width = 2.dp,
                                                        color = Color(0xFF10B981).copy(alpha = 0.6f),
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Success",
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Text(
                                                text = successTitle,
                                                color = Color(0xFF10B981),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Text(
                                                text = successDesc,
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                        }
                                    } else {
                                        // Custom Header Badge and Drag Indicator Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            // Capsule Badge for Step
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        AppTheme.colors.primary.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(100.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = AppTheme.colors.primary.copy(alpha = 0.35f),
                                                        shape = RoundedCornerShape(100.dp)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(AppTheme.colors.primary, shape = CircleShape)
                                                    )
                                                    Text(
                                                        text = if (lang == "ar") "الخطوة ${currentStepIndex + 1} من ${tourManager.steps.size}" else "STEP ${currentStepIndex + 1} OF ${tourManager.steps.size}",
                                                        color = AppTheme.colors.primary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.7.sp
                                                    )
                                                }
                                            }

                                            // Drag Handle to show moveability
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = if (lang == "ar") "اسحب للتحريك" else "DRAG TO MOVE",
                                                    color = AppTheme.colors.grayMuted.copy(alpha = 0.7f),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.DragHandle,
                                                    contentDescription = "Drag to move",
                                                    tint = AppTheme.colors.grayMuted.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Step Title
                                        Text(
                                            text = if (lang == "ar") currentStep.titleAr else currentStep.title,
                                            color = AppTheme.colors.onBackground,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.2.sp
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Step Description
                                        Text(
                                            text = if (lang == "ar") currentStep.descriptionAr else currentStep.description,
                                            color = AppTheme.colors.onBackground.copy(alpha = 0.8f),
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Premium Progress Bar
                                        val progress = (currentStepIndex + 1).toFloat() / tourManager.steps.size
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(100.dp)),
                                            color = AppTheme.colors.primary,
                                            trackColor = AppTheme.colors.primary.copy(alpha = 0.12f)
                                        )

                                        Spacer(modifier = Modifier.height(18.dp))

                                        // Action Buttons & Guides Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Skip Button (Styled transparently with clear hover ripple)
                                            TextButton(
                                                onClick = { tourManager.skipTour() },
                                                colors = ButtonDefaults.textButtonColors(contentColor = AppTheme.colors.grayMuted),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = if (lang == "ar") "تخطي الجولة" else "Skip Tour",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                            }

                                            if (currentStep.type == TourStepType.INFORMATIONAL) {
                                                // Primary Styled Action Button
                                                Button(
                                                    onClick = { tourManager.nextStep() },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = AppTheme.colors.primary,
                                                        contentColor = AppTheme.colors.onPrimary
                                                    ),
                                                    shape = RoundedCornerShape(14.dp),
                                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        val isLastStep = currentStepIndex == tourManager.steps.size - 1
                                                        Text(
                                                            text = if (isLastStep) {
                                                                (if (lang == "ar") "إنهاء" else "Finish")
                                                            } else {
                                                                (if (lang == "ar") "التالي" else "Next")
                                                            },
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        if (!isLastStep) {
                                                            Icon(
                                                                imageVector = if (lang == "ar") Icons.Default.ArrowBack else Icons.Default.ArrowForward,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Interactive steps: the spotlight and the text already say "tap this", so there is no
                                                // banner repeating it. Just a way out (a step that waits for a tap or a sensor must never
                                                // be the end of the road), at a normal size.
                                                OutlinedButton(
                                                    onClick = { tourManager.skipStep() },
                                                    shape = RoundedCornerShape(14.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.4f)),
                                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.primary)
                                                ) {
                                                    Text(
                                                        text = if (lang == "ar") "تخطي هذه الخطوة" else "Skip this step",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
