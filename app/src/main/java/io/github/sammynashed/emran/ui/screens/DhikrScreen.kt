package io.github.sammynashed.emran.ui.screens

import io.github.sammynashed.emran.ui.components.EmranDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import io.github.sammynashed.emran.ui.tour.tourTarget
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.ui.viewmodel.DhikrViewModel
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import androidx.compose.ui.window.Dialog
import io.github.sammynashed.emran.ui.localization.Localizations
import io.github.sammynashed.emran.ui.localization.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DhikrScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = context as? androidx.lifecycle.ViewModelStoreOwner
    val viewModel: DhikrViewModel = if (owner != null) {
        viewModel(viewModelStoreOwner = owner)
    } else {
        viewModel()
    }

    val lang = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val preferences = remember(context) { context.getSharedPreferences("emran_settings", android.content.Context.MODE_PRIVATE) }
    var isMuted by remember { mutableStateOf(preferences.getBoolean("dhikr_is_muted", false)) }
    val tourManager = io.github.sammynashed.emran.ui.tour.LocalTourManager.current
    
    val customList by viewModel.customTasbeehs.collectAsState()

    // 0: Default Preset (Cycles through Subhanallah, Alhamdulillah, La ilaha illallah, Allahu Akbar via Next Dhikr)
    // 1..N: Custom Counters (Custom index = 1 + idx)
    var selectedIndex by viewModel.selectedIndex
    var activePresetPhraseIndex by viewModel.activePresetPhraseIndex
    
    // Preset Dhikr counts preserved individually in memory
    val presetCounts = viewModel.presetCounts
    
    val presetDhikrList = listOf(
        "Subhanallah" to "سُبْحَانَ ٱللَّٰهِ",
        "Alhamdulillah" to "ٱلْحَمْدُ لِلَّٰهِ",
        "La ilaha illallah" to "لَا إِلَٰهَ إِلَّا ٱللَّٰهُ",
        "Allahu Akbar" to "ٱللَّٰهُ أَكْبَرُ"
    )

    val todayDateStr = remember { java.time.LocalDate.now().toString() }
    val isFriday = remember { java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY }

    var fridayCount by viewModel.fridayCount

    LaunchedEffect(todayDateStr) {
        val lastDate = preferences.getString("friday_tasbeeh_last_date", "")
        if (isFriday && lastDate != todayDateStr) {
            fridayCount = 0
            preferences.edit().putString("friday_tasbeeh_last_date", todayDateStr).apply()
        }
    }

    LaunchedEffect(isFriday) {
        if (!isFriday && selectedIndex == 999) {
            selectedIndex = 0
        }
    }

    // Spring tap compression animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "click_scale"
    )
    
    val coroutineScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    // Media Player for Masbaha click sound effect
    val clickPlayer = remember(context) {
        try {
            android.media.MediaPlayer.create(context, io.github.sammynashed.emran.R.raw.masbaha)
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                clickPlayer?.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    val playClickSound = {
        if (!isMuted) {
            try {
                clickPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                        player.seekTo(0)
                    }
                    player.start()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // Synchronize tab index and auto-select new item
    var prevSize by remember { mutableStateOf(0) }
    LaunchedEffect(customList.size) {
        if (customList.size > prevSize) {
            selectedIndex = 1 + customList.size - 1
        } else if (selectedIndex >= 1 + customList.size) {
            selectedIndex = 0
        }
        prevSize = customList.size
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 100.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())

            // Header with Back Button and Mute Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .tourTarget("sub_screen_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (lang == "ar") "الأذكار والتسبيحات" else "DHIKR & TASBIH",
                        color = AppTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
                IconButton(
                    onClick = { 
                        isMuted = !isMuted
                        preferences.edit().putBoolean("dhikr_is_muted", isMuted).apply()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .testTag("dhikr_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute sound" else "Mute sound",
                        tint = if (isMuted) AppTheme.colors.grayMuted else AppTheme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Always-visible Horizontal Scroll Tab Row (Displays presets + custom items)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .padding(4.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Single Main Tasbih Tab (Index 0)
                val presetTabName = if (lang == "ar") "التسبيح الرئيسي" else "Main Tasbih"
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedIndex == 0) AppTheme.colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .border(
                            width = if (selectedIndex == 0) 1.dp else 0.dp,
                            color = if (selectedIndex == 0) AppTheme.colors.primary.copy(alpha = 0.3f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedIndex = 0
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = presetTabName,
                        color = if (selectedIndex == 0) AppTheme.colors.primary else Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Friday Challenge Tab (Index 999) - Styled beautifully, highlighted on Friday!
                if (isFriday) {
                    val fridayTabName = if (lang == "ar") "تحدي الجمعة" else "Friday Challenge"
                    val fridayTabBorderColor = if (selectedIndex == 999) {
                        AppTheme.colors.primary
                    } else {
                        Color(0xFF4CAF50).copy(alpha = 0.4f)
                    }
                    val fridayTabBg = if (selectedIndex == 999) {
                        AppTheme.colors.primary.copy(alpha = 0.15f)
                    } else {
                        Color(0xFF4CAF50).copy(alpha = 0.08f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(fridayTabBg)
                            .border(
                                width = 1.dp,
                                color = fridayTabBorderColor,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedIndex = 999
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = fridayTabName,
                                color = if (selectedIndex == 999) {
                                    AppTheme.colors.primary
                                } else {
                                    Color(0xFF81C784)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                        }
                    }
                }

                // Custom added counters (1 onwards)
                customList.forEachIndexed { idx, item ->
                    val tabIndex = 1 + idx
                    val itemColor = try {
                        Color(android.graphics.Color.parseColor(item.colorHex))
                    } catch (e: Exception) {
                        AppTheme.colors.primary
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedIndex == tabIndex) itemColor.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                width = if (selectedIndex == tabIndex) 1.dp else 0.dp,
                                color = if (selectedIndex == tabIndex) itemColor.copy(alpha = 0.3f) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedIndex = tabIndex
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.textArabic.ifBlank { item.textTransliteration },
                            color = if (selectedIndex == tabIndex) itemColor else Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete Counter",
                            tint = if (selectedIndex == tabIndex) itemColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteTasbeeh(item)
                                    selectedIndex = 0
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = selectedIndex,
                transitionSpec = {
                    fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                },
                label = "dhikr_mode_fade",
                modifier = Modifier.weight(1f)
            ) { targetIdx ->
                if (targetIdx == 999) {
                    // Friday Challenge View!
                    val count = fridayCount
                    val targetGoal = 1000
                    val isCompleted = count >= targetGoal

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Current Recitation Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassCard(RoundedCornerShape(24.dp), if (isFriday) Color(0xFF0F3E21) else AppTheme.colors.secondary)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "اللهم صل على سيدنا محمد وعلى آله وصحبه وسلم",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 34.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (lang == "ar") "تحدي تسبيح يوم الجمعة (الهدف: ١٠٠٠ صلاة)" else "Friday Tasbeeh Challenge (Goal: 1,000)",
                                color = if (isFriday) Color(0xFF81C784) else AppTheme.colors.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (lang == "ar") "التقدم الحالي: $count / $targetGoal" else "Current Progress: $count / $targetGoal",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Beautiful Masbaha Clicker Ring
                        val totalBeads = 33
                        val currentBeadIndex = count % totalBeads
                        val beadRotationAngle by animateFloatAsState(
                            targetValue = count * (360f / totalBeads),
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "masbaha_friday_rotation"
                        )

                        Box(
                            modifier = Modifier.size(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Interactive sliding pearl beads
                            for (i in 0 until totalBeads) {
                                val angleRad = (i * (360f / totalBeads) - 90f + beadRotationAngle) * (Math.PI / 180f)
                                val isCounted = i < currentBeadIndex
                                val isActive = i == currentBeadIndex
                                
                                val beadSize by animateDpAsState(
                                    targetValue = if (isActive) 12.dp else if (isCounted) 8.dp else 6.dp,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                                    label = "friday_bead_size_$i"
                                )
                                val beadColor by animateColorAsState(
                                    targetValue = if (isActive) {
                                        if (isFriday) Color(0xFF4CAF50) else AppTheme.colors.primary
                                    } else if (isCounted) {
                                        if (isFriday) Color(0xFF4CAF50).copy(alpha = 0.8f) else AppTheme.colors.primary.copy(alpha = 0.8f)
                                    } else {
                                        Color.White.copy(alpha = 0.2f)
                                    },
                                    animationSpec = tween(300),
                                    label = "friday_bead_color_$i"
                                )

                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = (115 * kotlin.math.cos(angleRad)).dp,
                                            y = (115 * kotlin.math.sin(angleRad)).dp
                                        )
                                        .size(beadSize)
                                        .clip(CircleShape)
                                        .background(beadColor)
                                        .border(
                                            width = if (isActive) 1.5.dp else 0.5.dp,
                                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.1f),
                                            shape = CircleShape
                                        )
                                )
                            }

                            // Interactive inner core clicker
                            Box(
                                modifier = Modifier
                                    .size(190.dp)
                                    .scale(scale)
                                    .liquidGlassCard(CircleShape, if (isFriday) Color(0xFF0F3E21) else Color(0xFF231E39))
                                    .clickable(
                                        enabled = isFriday,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            playClickSound()
                                            fridayCount += 1
                                            isPressed = true
                                            coroutineScope.launch {
                                                delay(100)
                                                isPressed = false
                                            }
                                        }
                                    )
                                    .testTag("dhikr_friday_clicker"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (isFriday) {
                                        Text(
                                            text = count.toString(),
                                            color = Color.White,
                                            fontSize = 52.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        if (isCompleted) {
                                            Text(
                                                text = if (lang == "ar") "اكتمل التحدي! 🎉" else "Goal Completed! 🎉",
                                                color = Color(0xFF81C784),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Text(
                                                text = if (lang == "ar") "اضغط للتسبيح" else "Tap to count",
                                                color = AppTheme.colors.grayMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Locked",
                                            tint = Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (lang == "ar") "متاح يوم الجمعة فقط" else "Only on Fridays",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (targetIdx == 0) {
                    // Default Preset View
                    val dhikr = presetDhikrList[activePresetPhraseIndex]
                    val count = presetCounts[activePresetPhraseIndex]

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Current Recitation Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassCard(RoundedCornerShape(24.dp), AppTheme.colors.secondary)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = dhikr.second,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dhikr.first,
                                color = AppTheme.colors.grayMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Beautiful Masbaha Clicker Ring
                        val totalBeads = 33
                        val currentBeadIndex = count % totalBeads
                        val beadRotationAngle by animateFloatAsState(
                            targetValue = count * (360f / totalBeads),
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "masbaha_rotation"
                        )

                        Box(
                            modifier = Modifier.size(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Interactive sliding pearl beads
                            for (i in 0 until totalBeads) {
                                val angleRad = (i * (360f / totalBeads) - 90f + beadRotationAngle) * (Math.PI / 180f)
                                val isCounted = i < currentBeadIndex
                                val isActive = i == currentBeadIndex
                                
                                val beadSize by animateDpAsState(
                                    targetValue = if (isActive) 12.dp else if (isCounted) 8.dp else 6.dp,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                                    label = "bead_size_$i"
                                )
                                val beadColor by animateColorAsState(
                                    targetValue = if (isActive) AppTheme.colors.primary else if (isCounted) AppTheme.colors.primary.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                                    animationSpec = tween(300),
                                    label = "bead_color_$i"
                                )

                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = (115 * kotlin.math.cos(angleRad)).dp,
                                            y = (115 * kotlin.math.sin(angleRad)).dp
                                        )
                                        .size(beadSize)
                                        .clip(CircleShape)
                                        .background(beadColor)
                                        .border(
                                            width = if (isActive) 1.5.dp else 0.5.dp,
                                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.1f),
                                            shape = CircleShape
                                        )
                                )
                            }

                            // Interactive inner core clicker
                            Box(
                                modifier = Modifier
                                    .size(190.dp)
                                    .scale(scale)
                                    .liquidGlassCard(CircleShape, AppTheme.colors.primary)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            playClickSound()
                                            presetCounts[activePresetPhraseIndex] = count + 1
                                            isPressed = true
                                            coroutineScope.launch {
                                                delay(100)
                                                isPressed = false
                                            }
                                            if (tourManager != null && tourManager.isActive.value) {
                                                val step = tourManager.steps.getOrNull(tourManager.currentStepIndex.value)
                                                if (step != null && step.id == "dhikr_main_clicker") {
                                                    tourManager.triggerSuccess("dhikr_main_clicker")
                                                }
                                            }
                                        }
                                    )
                                    .testTag("dhikr_preset_clicker")
                                    .tourTarget("dhikr_main_clicker"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = count.toString(),
                                        color = Color.White,
                                        fontSize = 52.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = if (lang == "ar") "اضغط للتسبيح" else "Tap to count",
                                        color = AppTheme.colors.grayMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Controls: Reset and Next Dhikr (repositioned beautifully with generous padding)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    presetCounts[activePresetPhraseIndex] = 0 
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.03f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset Count",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Button(
                                onClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    activePresetPhraseIndex = (activePresetPhraseIndex + 1) % 4
                                    if (tourManager != null && tourManager.isActive.value) {
                                        val step = tourManager.steps.getOrNull(tourManager.currentStepIndex.value)
                                        if (step != null && step.id == "dhikr_next_preset") {
                                            tourManager.triggerSuccess("dhikr_next_preset")
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .height(48.dp)
                                    .tourTarget("dhikr_next_preset")
                            ) {
                                Text(
                                    text = Localizations.string("dhikr_next", lang, "Next Dhikr"), 
                                    color = Color.White, 
                                    fontSize = 14.sp, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    // Custom Counter View
                    val customItem = customList.getOrNull(targetIdx - 1)
                    if (customItem != null) {
                        val customItemColor = try {
                            Color(android.graphics.Color.parseColor(customItem.colorHex))
                        } catch (e: Exception) {
                            AppTheme.colors.primary
                        }
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Current Custom Recitation Card with Target
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassCard(RoundedCornerShape(24.dp), AppTheme.colors.secondary)
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = customItem.textArabic,
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                if (customItem.textTransliteration.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = customItem.textTransliteration,
                                        color = AppTheme.colors.grayMuted,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (lang == "ar") "العدد الحالي: ${customItem.count} / ${customItem.target}" else "Progress: ${customItem.count} / ${customItem.target}",
                                    color = customItemColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Custom Masbaha Clicker with Rotating Pearls
                            val totalBeads = 33
                            val currentBeadIndex = customItem.count % totalBeads
                            val beadRotationAngle by animateFloatAsState(
                                targetValue = customItem.count * (360f / totalBeads),
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                label = "masbaha_custom_rotation"
                            )

                            Box(
                                modifier = Modifier.size(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Beads
                                for (i in 0 until totalBeads) {
                                    val angleRad = (i * (360f / totalBeads) - 90f + beadRotationAngle) * (Math.PI / 180f)
                                    val isCounted = i < currentBeadIndex
                                    val isActive = i == currentBeadIndex
                                    
                                    val beadSize by animateDpAsState(
                                        targetValue = if (isActive) 12.dp else if (isCounted) 8.dp else 6.dp,
                                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                                        label = "bead_size_custom_$i"
                                    )
                                    val beadColor by animateColorAsState(
                                        targetValue = if (isActive) customItemColor else if (isCounted) customItemColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                                        animationSpec = tween(300),
                                        label = "bead_color_custom_$i"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .offset(
                                                x = (115 * kotlin.math.cos(angleRad)).dp,
                                                y = (115 * kotlin.math.sin(angleRad)).dp
                                            )
                                            .size(beadSize)
                                            .clip(CircleShape)
                                            .background(beadColor)
                                            .border(
                                                width = if (isActive) 1.5.dp else 0.5.dp,
                                                color = if (isActive) Color.White else Color.White.copy(alpha = 0.1f),
                                                shape = CircleShape
                                            )
                                    )
                                }

                                // Central Clicker that functions exactly like the rest with big number
                                Box(
                                    modifier = Modifier
                                        .size(190.dp)
                                        .scale(scale)
                                        .liquidGlassCard(CircleShape, customItemColor)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                playClickSound()
                                                viewModel.incrementCount(customItem)
                                                isPressed = true
                                                coroutineScope.launch {
                                                    delay(100)
                                                    isPressed = false
                                                }
                                            }
                                        )
                                        .testTag("dhikr_custom_clicker_${customItem.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = customItem.count.toString(),
                                            color = Color.White,
                                            fontSize = 52.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = if (lang == "ar") "اضغط للتسبيح" else "Tap to count",
                                            color = AppTheme.colors.grayMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))

                            // Controls: Reset for Custom Tasbeeh
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.resetCount(customItem)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reset Count",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (lang == "ar") "إعادة ضبط" else "Reset Counter", 
                                        color = Color.White.copy(alpha = 0.8f), 
                                        fontSize = 14.sp, 
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button for adding custom Tasbih
        FloatingActionButton(
            onClick = { 
                showAddDialog = true 
                if (tourManager != null && tourManager.isActive.value) {
                    val step = tourManager.steps.getOrNull(tourManager.currentStepIndex.value)
                    if (step != null && step.id == "dhikr_fab_add") {
                        tourManager.triggerSuccess("dhikr_fab_add")
                    }
                }
            },
            containerColor = AppTheme.colors.primary,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 112.dp) // Placed safely above the bottom navigation dock
                .size(56.dp)
                .testTag("dhikr_fab_add")
                .tourTarget("dhikr_fab_add")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Custom Tasbih",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // New Custom Tasbih Creator Dialog
    if (showAddDialog) {
        var arabicInput by remember { mutableStateOf("") }
        var transliterationInput by remember { mutableStateOf("") }
        var targetInput by remember { mutableStateOf("100") }
        
        val rainbowColors = listOf(
            "#FFE57373" to "Red",
            "#FFFFB74D" to "Orange",
            "#FFFFF176" to "Yellow",
            "#FF81C784" to "Green",
            "#FF64B5F6" to "Blue",
            "#FF7986CB" to "Indigo",
            "#FFBA68C8" to "Violet"
        )
        var selectedColorHex by remember { mutableStateOf("#FF64B5F6") }
        
        val direction = if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
        EmranDialog(onDismissRequest = { showAddDialog = false }) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = AppTheme.colors.surface
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = Localizations.string("dhikr_new_tasbih", lang, "New Tasbih Counter"),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = arabicInput,
                                onValueChange = { arabicInput = it },
                                label = { Text(Localizations.string("dhikr_arabic_req", lang, "Arabic Text (Required)"), color = Color.White.copy(alpha = 0.6f)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                                ),
                                singleLine = true,
                                placeholder = { Text(Localizations.string("dhikr_arabic_placeholder", lang, "e.g. سُبْحَانَ ٱللَّٰهِ")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dhikr_input_arabic")
                            )

                            OutlinedTextField(
                                value = transliterationInput,
                                onValueChange = { transliterationInput = it },
                                label = { Text(Localizations.string("dhikr_trans_opt", lang, "English Translation (Optional)"), color = Color.White.copy(alpha = 0.6f)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                                ),
                                singleLine = true,
                                placeholder = { Text(Localizations.string("dhikr_trans_placeholder", lang, "e.g. Subhanallah")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dhikr_input_name")
                            )

                            OutlinedTextField(
                                value = targetInput,
                                onValueChange = { targetInput = it.filter { digit -> digit.isDigit() } },
                                label = { Text(Localizations.string("dhikr_target_goal", lang, "Target Goal"), color = Color.White.copy(alpha = 0.6f)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dhikr_input_target")
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (lang == "ar") "اختر لوناً للعداد" else "Choose a color for the counter",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rainbowColors.forEach { (hex, _) ->
                                    val colorVal = try {
                                        Color(android.graphics.Color.parseColor(hex))
                                    } catch (e: Exception) {
                                        AppTheme.colors.primary
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(colorVal)
                                            .border(
                                                width = if (selectedColorHex == hex) 2.5.dp else 1.dp,
                                                color = if (selectedColorHex == hex) Color.White else Color.White.copy(alpha = 0.2f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                selectedColorHex = hex
                                            }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showAddDialog = false }) {
                                Text(Localizations.string("dhikr_cancel", lang, "Cancel"), color = AppTheme.colors.grayMuted)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (arabicInput.isNotBlank()) {
                                        val target = targetInput.toIntOrNull() ?: 100
                                        viewModel.addCustomTasbeeh(arabicInput, transliterationInput, target, selectedColorHex)
                                        showAddDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("dhikr_dialog_save")
                            ) {
                                Text(Localizations.string("dhikr_create", lang, "Create"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
