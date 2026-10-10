package io.github.sammynashed.emran.ui.screens
 
import android.Manifest
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.data.LocationPreset
import io.github.sammynashed.emran.data.LocationPresets
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    currentLanguage: String = "en",
    onLanguageSelect: (String) -> Unit = {},
    onFinished: (themePreset: String, location: LocationPreset, notificationsEnabled: Boolean, calculationMethod: String, asrSchool: String, defaultAdhanSound: String) -> Unit,
    onThemePreview: (String) -> Unit = {},
    onThemeConfirmed: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentPageIndex by remember { mutableStateOf(0) }
    var isAutostartConfigured by remember { mutableStateOf(false) }
    
    // Setup state configurable during onboarding
    var selectedThemePreset by remember { mutableStateOf("EXPRESSIVE") }
    var selectedAdhanSound by remember { mutableStateOf("default") }
    var selectedLocation by remember {
        mutableStateOf<LocationPreset?>(null) // No preselected station by default
    }
    var selectedCalculationMethod by remember { mutableStateOf("EGYPTIAN") }
    var selectedAsrSchool by remember { mutableStateOf("SHAFII") }
    var notificationsEnabled by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    // Request notification permission launcher (Android 13+)
    var hasAskedPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            notificationsEnabled = isGranted
        }
    )

    val activePages = remember(notificationsEnabled) {
        val list = mutableListOf(0, 1, 4, 5)
        if (notificationsEnabled) {
            list.add(6)  // Adhan Sound Selection Page
        }
        list
    }

    val totalPages = activePages.size

    LaunchedEffect(currentPageIndex) {
        val pageType = activePages.getOrElse(currentPageIndex) { 0 }
        if (pageType == 5 && !hasAskedPermission) {
            hasAskedPermission = true
            kotlinx.coroutines.delay(600) // Ensure enter transition finishes completely so focus is gained
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notificationsEnabled = true
                }
            } else {
                notificationsEnabled = true
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
        containerColor = Color.Transparent,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                // Page indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPages) { index ->
                        val isSelected = index == currentPageIndex
                        val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp)
                        val color = if (isSelected) AppTheme.colors.primary else AppTheme.colors.grayMuted.copy(alpha = 0.3f)
                        
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                // Actions (Back, Next/Complete)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPageIndex > 0) {
                        TextButton(
                            onClick = { currentPageIndex-- },
                            modifier = Modifier.testTag("onboarding_back_btn")
                        ) {
                            Text(
                                text = io.github.sammynashed.emran.ui.localization.Localizations.string("back", currentLanguage),
                                color = AppTheme.colors.grayMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(64.dp))
                    }
 
                    Button(
                        onClick = {
                            if (currentPageIndex < totalPages - 1) {
                                val pageType = activePages[currentPageIndex]
                                if (pageType == 4 && selectedLocation == null) {
                                    val errCity = io.github.sammynashed.emran.ui.localization.Localizations.string("toast_select_city", currentLanguage)
                                    android.widget.Toast.makeText(context, errCity, android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    if (pageType == 1) {
                                        onThemeConfirmed(selectedThemePreset)
                                    }
                                    currentPageIndex++
                                }
                            } else {
                                val fallbackLoc = selectedLocation ?: (LocationPresets.list.firstOrNull { it.name.contains("Mecca") } ?: LocationPresets.list[0])
                                onFinished(selectedThemePreset, fallbackLoc, notificationsEnabled, selectedCalculationMethod, selectedAsrSchool, selectedAdhanSound)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primary,
                            contentColor = AppTheme.colors.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("onboarding_next_btn")
                    ) {
                        Text(
                            text = if (currentPageIndex == totalPages - 1) {
                                io.github.sammynashed.emran.ui.localization.Localizations.string("complete_setup", currentLanguage)
                            } else {
                                io.github.sammynashed.emran.ui.localization.Localizations.string("next", currentLanguage)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = currentPageIndex,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }.using(
                        SizeTransform(clip = false)
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) { pageIdx ->
                val pageType = activePages.getOrElse(pageIdx) { 0 }
                when (pageType) {
                    0 -> OnboardingLanguagePage(
                        selectedLanguage = currentLanguage,
                        onLanguageSelected = onLanguageSelect
                    )
                    1 -> OnboardingWelcomePage(
                        selectedPreset = selectedThemePreset,
                        onPresetSelected = {
                            selectedThemePreset = it
                            onThemePreview(it)
                        }
                    )
                    4 -> OnboardingLocationPage(
                        selectedLocation = selectedLocation,
                        onLocationSelected = { selectedLocation = it },
                        selectedMethod = selectedCalculationMethod,
                        onMethodSelected = { selectedCalculationMethod = it },
                        selectedSchool = selectedAsrSchool,
                        onSchoolSelected = { selectedAsrSchool = it }
                    )
                    5 -> OnboardingNotificationPage(
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsToggled = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!hasPermission) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        notificationsEnabled = true
                                    }
                                } else {
                                    notificationsEnabled = true
                                }
                            } else {
                                notificationsEnabled = false
                            }
                        }
                    )
                    10 -> {
                        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                        var isIgnoringOptimizations by remember {
                            mutableStateOf(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    pm.isIgnoringBatteryOptimizations(context.packageName)
                                } else {
                                    true
                                }
                            )
                        }
                        
                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    isIgnoringOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        pm.isIgnoringBatteryOptimizations(context.packageName)
                                    } else {
                                        true
                                    }
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        OnboardingBatteryPage(
                            lang = currentLanguage,
                            isIgnoring = isIgnoringOptimizations
                        )
                    }
                    11 -> OnboardingAutostartPage(
                        lang = currentLanguage,
                        isConfigured = isAutostartConfigured,
                        onConfiguredChanged = { isAutostartConfigured = it }
                    )
                    6 -> OnboardingAdhanPage(
                        lang = currentLanguage,
                        selectedSound = selectedAdhanSound,
                        onSoundSelected = { selectedAdhanSound = it }
                    )
                }
            }
        }
    }
}

@Composable
fun FivePillarsLogo(
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(28.dp, 40.dp, 52.dp, 40.dp, 28.dp)
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(h)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                primaryColor,
                                secondaryColor
                            )
                        )
                    )
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                    )
            )
        }
    }
}

data class OnboardingThemePreset(
    val key: String,
    val name: String,
    val primary: Color,
    val secondary: Color
)

@Composable
fun OnboardingWelcomePage(
    selectedPreset: String,
    onPresetSelected: (String) -> Unit
) {
    val lang = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current

    val presets = listOf(
        OnboardingThemePreset("EXPRESSIVE", if (lang == "ar") "المعبّر الملون" else "Expressive", Color(0xFF6366F1), Color(0xFFF97316)),
        OnboardingThemePreset("BLACK_STONE", if (lang == "ar") "الحجر الأسود" else "The Black Stone", Color(0xFFA3A3A3), Color(0xFF6B7280)),
        OnboardingThemePreset("COSMIC", if (lang == "ar") "الكون الكويكب" else "Cosmic Noir", Color(0xFFFF3344), Color(0xFFFBBF24)),
        OnboardingThemePreset("TEAL", if (lang == "ar") "البحر الفيروزي" else "Midnight Teal", Color(0xFF06B6D4), Color(0xFFF43F5E)),
        OnboardingThemePreset("ROSE", if (lang == "ar") "الورد الشفقي" else "Sunset Rose", Color(0xFFEC4899), Color(0xFFFBBF24))
    )
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppTheme.colors.primary.copy(alpha = 0.15f),
                            AppTheme.colors.secondary.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            FivePillarsLogo(
                primaryColor = AppTheme.colors.primary,
                secondaryColor = AppTheme.colors.secondary,
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = io.github.sammynashed.emran.ui.localization.Localizations.string("welcome", lang).uppercase(),
            color = AppTheme.colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = io.github.sammynashed.emran.ui.localization.Localizations.string("app_name_sub", lang),
            color = AppTheme.colors.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = io.github.sammynashed.emran.ui.localization.Localizations.string("app_description", lang),
            color = AppTheme.colors.grayMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = io.github.sammynashed.emran.ui.localization.Localizations.string("select_aesthetic", lang),
            color = AppTheme.colors.onBackground.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontally scrollable high-fidelity cards
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = selectedPreset == preset.key
                val outlineColor = if (isSelected) AppTheme.colors.primary else Color.White.copy(alpha = 0.08f)
                val bgColor = if (isSelected) AppTheme.colors.primary.copy(alpha = 0.15f) else AppTheme.colors.surface

                Card(
                    modifier = Modifier
                        .width(136.dp)
                        .height(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, outlineColor, RoundedCornerShape(16.dp))
                        .clickable { onPresetSelected(preset.key) }
                        .testTag("onboarding_theme_${preset.key}"),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Palette preview indicator circles
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(preset.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(preset.secondary)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = preset.name,
                            color = if (isSelected) Color.White else AppTheme.colors.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
    }

}

@Composable
fun StateExplainCard(
    icon: ImageVector,
    statusName: String,
    color: Color,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.surface)
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = statusName,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = desc,
                color = AppTheme.colors.grayMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun OnboardingLocationPage(
    lang: String = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current,
    selectedLocation: LocationPreset?,
    onLocationSelected: (LocationPreset) -> Unit,
    selectedMethod: String,
    onMethodSelected: (String) -> Unit,
    selectedSchool: String,
    onSchoolSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLocations = remember(searchQuery, lang) {
        if (searchQuery.isBlank()) {
            LocationPresets.list
        } else {
            LocationPresets.list.filter {
                val localizedName = io.github.sammynashed.emran.data.LocationPresets.getLocalizedName(it.name, lang)
                localizedName.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(top=32.dp, start=24.dp, end=24.dp, bottom=32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = if(lang=="ar") "حدد إحداثيات الصلاة" else "Set Prayer Coordinates",
            color = AppTheme.colors.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // The fastest correct answer, offered first: most people do not want to
        // hunt for their city in a list of hundreds.
        CurrentLocationButton(
            lang = lang,
            onLocationResolved = { onLocationSelected(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if(lang=="ar") "أو اختر مدينتك" else "Or pick your city",
            color = AppTheme.colors.grayMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Search Input only
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (lang == "ar") "البحث عن مدينة أو دولة..." else "Search city or country...", color = AppTheme.colors.grayMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search icon",
                    tint = AppTheme.colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search query",
                            tint = AppTheme.colors.grayMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else null,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 13.sp
            ),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AppTheme.colors.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedContainerColor = AppTheme.colors.surface,
                unfocusedContainerColor = AppTheme.colors.surface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_location_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Selected Status Card
        if (selectedLocation != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.primary.copy(alpha = 0.12f))
                    .border(1.dp, AppTheme.colors.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        val activeLocStr = io.github.sammynashed.emran.data.LocationPresets.getLocalizedName(selectedLocation.name, lang)
                        Text(
                            text = if(lang=="ar") "النشط: $activeLocStr" else "Active: ${selectedLocation.name}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Lat: ${String.format("%.4f", selectedLocation.latitude)}, Lon: ${String.format("%.4f", selectedLocation.longitude)}",
                            color = AppTheme.colors.grayMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Count doubles as a scroll affordance: a list that silently shows two
        // rows reads as "there are only two", which is how people conclude the
        // screen is broken rather than scrollable.
        Text(
            text = if (lang == "ar") {
                "${filteredLocations.size} مدينة — مرّر للمزيد"
            } else {
                "${filteredLocations.size} cities — scroll for more"
            },
            color = AppTheme.colors.grayMuted,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            textAlign = TextAlign.Start
        )

        // Dropdown/Scrollable selector for presets
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 180.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppTheme.colors.surface)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (filteredLocations.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if(lang=="ar") "لم يتم العثور على مواقع مطابقة" else "No matching locations found",
                                color = AppTheme.colors.grayMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(filteredLocations) { preset ->
                        val isSelected = preset.name == selectedLocation?.name
                        val bgColor = if (isSelected) AppTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .clickable { onLocationSelected(preset) }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = io.github.sammynashed.emran.data.LocationPresets.getLocalizedName(preset.name, lang),
                                    color = if (isSelected) Color.White else AppTheme.colors.onBackground,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Lat: ${preset.latitude}, Lon: ${preset.longitude}",
                                    color = AppTheme.colors.grayMuted,
                                    fontSize = 11.sp
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calculation Authority & Asr School Selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CalculationAuthorityDropdown(
                    selectedMethod = selectedMethod,
                    onMethodSelected = onMethodSelected
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                AsrJuristicSchoolDropdown(
                    selectedSchool = selectedSchool,
                    onSchoolSelected = onSchoolSelected
                )
            }
        }
    }

}

@Composable
fun OnboardingNotificationPage(
    lang: String = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current,
    notificationsEnabled: Boolean,
    onNotificationsToggled: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasSystemPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Icon(
            imageVector = Icons.Default.NotificationsActive,
            contentDescription = null,
            tint = AppTheme.colors.primary,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (lang == "ar") "ابقَ ثابتاً" else "STAY STEADFAST",
            color = AppTheme.colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Text(
            text = if (lang == "ar") "تنبيهات إشعارات الصلاة" else "Salah Notification Alerts",
            color = AppTheme.colors.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (lang == "ar") "تلقي تذكيرات فورية في لحظات الصلاة بالضبط يسهل الاستمرارية. تعمل المنبهات كمهام خلفية وتعمل دون offline بأمان." else "Receiving prompt reminders at exact prayer moments simplifies consistency. Alarms are computed background tasks, run offline securely.",
            color = AppTheme.colors.grayMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (lang == "ar") "محرك تنبيهات الصلاة" else "Prayer Alerts Engine",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (lang == "ar") "قم بتشغيل المنبهات النشطة للفجر والظهر والعصر والمغرب والعشاء." else "Turn on active alarms for Fajr, Dhuhr, Asr, Maghrib, and Isha.",
                        color = AppTheme.colors.grayMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Switch(
                    checked = notificationsEnabled && hasSystemPermission,
                    onCheckedChange = { onNotificationsToggled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppTheme.colors.primary,
                        checkedTrackColor = AppTheme.colors.primary.copy(alpha = 0.2f),
                        uncheckedThumbColor = AppTheme.colors.grayMuted,
                        uncheckedTrackColor = AppTheme.colors.surface
                    ),
                    modifier = Modifier.testTag("onboarding_notif_switch")
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasSystemPermission) {
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = if (lang == "ar") "يرجى العلم أن الإشعارات معطلة في إعدادات النظام حالياً." else "Please note system notifications are currently disabled in Android settings.",
                color = Color.Yellow.copy(alpha = 0.9f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (ex: Exception) {
                            android.widget.Toast.makeText(context, "Could not open system settings.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.primary.copy(alpha = 0.15f),
                    contentColor = AppTheme.colors.primary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = if (lang == "ar") "فتح الإعدادات لتمكينها" else "Open Settings to Enable",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    }

}



@Composable
fun CalculationAuthorityDropdown(
    lang: String = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current,
    selectedMethod: String,
    onMethodSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val methods = listOf(
        "EGYPTIAN" to (if (lang == "ar") "الهيئة العامة المصرية للمساحة" else "Egyptian General Authority"),
        "UMM_AL_QURA" to (if (lang == "ar") "جامعة أم القرى (مكة)" else "Umm Al-Qura (Mecca)"),
        "MWL" to (if (lang == "ar") "رابطة العالم الإسلامي" else "Muslim World League"),
        "ISNA" to (if (lang == "ar") "الجمعية الإسلامية لأمريكا الشمالية" else "Islamic Society of North America"),
        "KARACHI" to (if (lang == "ar") "جامعة العلوم الإسلامية، كراتشي" else "University of Islamic Sciences, Karachi")
    )
    val curLabel = methods.find { it.first == selectedMethod }?.second ?: selectedMethod

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (lang == "ar") "طريقة الحساب" else "CALCULATION METHOD",
            color = AppTheme.colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.surface)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(curLabel, color = Color.White, fontSize = 13.sp)
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = AppTheme.colors.primary
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(AppTheme.colors.surface)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
        ) {
            methods.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = Color.White, fontSize = 13.sp) },
                    onClick = {
                        onMethodSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun AsrJuristicSchoolDropdown(
    lang: String = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current,
    selectedSchool: String,
    onSchoolSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val schools = listOf(
        "SHAFII" to (if (lang == "ar") "القياسي (الشافعي، المالكي، الحنبلي)" else "Standard (Shafi'i, Maliki, Hanbali)"),
        "HANAFI" to (if (lang == "ar") "المذهب الحنفي" else "Hanafi School")
    )
    val curLabel = schools.find { it.first == selectedSchool }?.second ?: selectedSchool

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (lang == "ar") "المذهب الفقهي للعصر" else "ASR JURISTIC SCHOOL",
            color = AppTheme.colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.surface)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(curLabel, color = Color.White, fontSize = 13.sp)
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = AppTheme.colors.primary
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(AppTheme.colors.surface)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
        ) {
            schools.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = Color.White, fontSize = 13.sp) },
                    onClick = {
                        onSchoolSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun getPresetColors(preset: String): Pair<Color, Color> {
    return when (preset) {
        "EXPRESSIVE" -> Pair(Color(0xFF6366F1), Color(0xFFF97316))
        "COSMIC" -> Pair(Color(0xFFFF3344), Color(0xFFFBBF24))
        "TEAL" -> Pair(Color(0xFF06B6D4), Color(0xFFF43F5E))
        "ROSE" -> Pair(Color(0xFFEC4899), Color(0xFFFBBF24))
        "GOLDEN" -> Pair(Color(0xFFD4AF37), Color(0xFFF59E0B))
        "EMERALD" -> Pair(Color(0xFF10B981), Color(0xFF34D399))
        "OCEAN" -> Pair(Color(0xFF3B82F6), Color(0xFF60A5FA))
        "AMYTHIST" -> Pair(Color(0xFF8B5CF6), Color(0xFFD8B4FE))
        "SUNFIRE" -> Pair(Color(0xFFEF4444), Color(0xFFF97316))
        "SAPPHIRE" -> Pair(Color(0xFF2563EB), Color(0xFFA5F3FC))
        "LAVENDER" -> Pair(Color(0xFFA78BFA), Color(0xFFF472B6))
        "MINT" -> Pair(Color(0xFF06B6D4), Color(0xFF99F6E4))
        "FOREST" -> Pair(Color(0xFF4ADE80), Color(0xFFFACC15))
        "BLOOD_MOON" -> Pair(Color(0xFFDC2626), Color(0xFF9CA3AF))
        "MONOCHROME" -> Pair(Color(0xFFE2E8F0), Color(0xFF94A3B8))
        "SAKURA" -> Pair(Color(0xFFFBCFE8), Color(0xFFFDA4AF))
        "NEBULA" -> Pair(Color(0xFFC084FC), Color(0xFF818CF8))
        "DESERT" -> Pair(Color(0xFFF59E0B), Color(0xFFD97706))
        "AURORA" -> Pair(Color(0xFF2DD4BF), Color(0xFFC084FC))
        "BLACK_STONE" -> Pair(Color(0xFFA3A3A3), Color(0xFF6B7280))
        else -> Pair(Color(0xFF6366F1), Color(0xFFF97316))
    }
}

@Composable
fun OnboardingLanguagePage(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp, start = 24.dp, end = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppTheme.colors.primary.copy(alpha = 0.15f),
                            AppTheme.colors.secondary.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = AppTheme.colors.primary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (selectedLanguage == "ar") "اختر اللغة" else "SELECT LANGUAGE",
            color = AppTheme.colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = if (selectedLanguage == "ar") "تفضيلاتك" else "Preferences",
            color = AppTheme.colors.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (selectedLanguage == "ar") "اختر لغتك المفضلة للواجهات والحسابات واليوميات الروحية." else "Choose your preferred language for interfaces, calculations, and daily spiritual journaling.",
            color = AppTheme.colors.grayMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // English button
            val isEnSelected = selectedLanguage == "en"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.5.dp,
                        if (isEnSelected) AppTheme.colors.primary else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onLanguageSelected("en") }
                    .testTag("lang_en"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnSelected) AppTheme.colors.primary.copy(alpha = 0.15f) else AppTheme.colors.surface
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "English",
                        color = if (isEnSelected) Color.White else AppTheme.colors.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (isEnSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AppTheme.colors.primary
                        )
                    }
                }
            }

            // Arabic button
            val isArSelected = selectedLanguage == "ar"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.5.dp,
                        if (isArSelected) AppTheme.colors.primary else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onLanguageSelected("ar") }
                    .testTag("lang_ar"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isArSelected) AppTheme.colors.primary.copy(alpha = 0.15f) else AppTheme.colors.surface
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "العربية",
                        color = if (isArSelected) Color.White else AppTheme.colors.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (isArSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AppTheme.colors.primary
                        )
                    }
                }
            }
        }
    }
    

}

@Composable
fun OnboardingAdhanPage(
    modifier: Modifier = Modifier.fillMaxSize().padding(16.dp),
    lang: String = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current,
    selectedSound: String,
    onSoundSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var playingSoundId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
    }

    fun playPreview(soundKey: String) {
        if (playingSoundId == soundKey) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            playingSoundId = null
        } else {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            playingSoundId = null

            try {
                val player = if (soundKey == "default") {
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    MediaPlayer().apply {
                        setDataSource(context, defaultUri)
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        prepare()
                        start()
                    }
                } else {
                    val resId = context.resources.getIdentifier(soundKey, "raw", context.packageName)
                    if (resId != 0) {
                        MediaPlayer.create(context, resId)
                    } else {
                        null
                    }
                }

                if (player != null) {
                    player.setOnCompletionListener {
                        playingSoundId = null
                        mediaPlayer?.release()
                        mediaPlayer = null
                    }
                    mediaPlayer = player
                    playingSoundId = soundKey
                    player.start()
                } else {
                    android.widget.Toast.makeText(context, "Sound resource not found", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Error playing sound preview", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val voices = listOf(
        AdhanVoiceOption(
            key = "default",
            nameEn = "Android System Default",
            nameAr = "الصوت الافتراضي للنظام",
            descEn = "Your device's standard notification sound",
            descAr = "نغمة الإشعارات الافتراضية لهاتفك"
        ),
        AdhanVoiceOption(
            key = "ahmad_alnafees",
            nameEn = "Ahmad Alnafys",
            nameAr = "أحمد النفيس",
            descEn = "Calming and beautiful Adhan recitation",
            descAr = "أذان هادئ وجميل"
        ),
        AdhanVoiceOption(
            key = "mishary_alafasy",
            nameEn = "Mshary Rashed Alaafasy",
            nameAr = "مشاري راشد العفاسي",
            descEn = "Clear, melodious and highly inspiring voice",
            descAr = "صوت نديّ عذب ويبعث على الطمأنينة"
        ),
        AdhanVoiceOption(
            key = "saad_alghamdi",
            nameEn = "Saad Alghamdy",
            nameAr = "سعد الغامدي",
            descEn = "Deep, resonant and majestic Adhan style",
            descAr = "أداء خاشع ومهيب"
        )
    )
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = AppTheme.colors.primary,
            modifier = Modifier.size(54.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (lang == "ar") "صوت الأذان الافتراضي" else "DEFAULT ADHAN VOICE & SOUND",
            color = AppTheme.colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Text(
            text = if (lang == "ar") "اختر صوت الأذان المفضل" else "Select Prayer Notification Voice",
            color = AppTheme.colors.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (lang == "ar") "اختر الصوت الذي تود سماعه كتنبيه عند حلول الساعات المباركة للصلوات المفروضة. هذا الصوت سيكون التنبيه الأساسي للتطبيق." 
                   else "Select the voice to play when the blessed prayer times arrive. This will serve as your default notification sound.",
            color = AppTheme.colors.grayMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            voices.forEach { option ->
                val isSelected = selectedSound == option.key
                val outlineColor = if (isSelected) AppTheme.colors.primary else Color.White.copy(alpha = 0.05f)
                val bgColor = if (isSelected) AppTheme.colors.primary.copy(alpha = 0.12f) else AppTheme.colors.surface

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, outlineColor, RoundedCornerShape(14.dp))
                        .clickable { 
                            onSoundSelected(option.key)
                            playPreview(option.key)
                        }
                        .testTag("onboarding_sound_${option.key}"),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, if (isSelected) AppTheme.colors.primary else AppTheme.colors.grayMuted, CircleShape)
                                    .background(if (isSelected) AppTheme.colors.primary else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = if (lang == "ar") option.nameAr else option.nameEn,
                                    color = if (isSelected) Color.White else AppTheme.colors.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (lang == "ar") option.descAr else option.descEn,
                                    color = AppTheme.colors.grayMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        val isPlayingThis = playingSoundId == option.key
                        if (isPlayingThis) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Playing",
                                tint = AppTheme.colors.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    }

}

data class AdhanVoiceOption(
    val key: String,
    val nameEn: String,
    val nameAr: String,
    val descEn: String,
    val descAr: String
)

@Composable
fun OnboardingBatteryPage(
    lang: String = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current,
    isIgnoring: Boolean
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp, start = 24.dp, end = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BatteryChargingFull,
            contentDescription = null,
            tint = AppTheme.colors.primary,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (lang == "ar") "تحسين البطارية" else "BATTERY OPTIMIZATION",
            color = AppTheme.colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Text(
            text = if (lang == "ar") "نزع قيود البطارية للتنبيهات" else "Unrestrict Battery for Alarms",
            color = AppTheme.colors.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (lang == "ar") "لضمان عمل منبهات الصلاة بدقة وموثوقية عالية في الخلفية، يرجى تغيير تحسين البطارية لبرنامج (عمران) إلى 'بلا قيود' (No Restrictions)."
                   else "To guarantee prayer alarms run accurately and with high reliability in the background, please change Emran's battery use to 'No Restrictions' / 'Unrestricted'.",
            color = AppTheme.colors.grayMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isIgnoring) {
                        if (lang == "ar") "الحالة: مفعّل وبلا قيود ✅" else "Status: Unrestricted ✅"
                    } else {
                        if (lang == "ar") "الحالة: مقيد (البطارية محسّنة) ⚠️" else "Status: Battery Optimized ⚠️"
                    },
                    color = if (isIgnoring) Color(0xFF4CAF50) else Color(0xFFFFB74D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isIgnoring) {
                        if (lang == "ar") "عمل رائع! تنبيهات الصلاة ستعمل بدقة وموثوقية عالية." else "Perfect! Alarms will run with maximum reliability."
                    } else {
                        if (lang == "ar") "يرجى النقر أدناه واختيار 'السماح بالتشغيل دائماً في الخلفية' أو 'بلا قيود' لتفادي التأخر." else "Tap below and allow running in background with no restrictions."
                    },
                    color = AppTheme.colors.grayMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                if (!isIgnoring) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                                try {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    android.widget.Toast.makeText(context, "Could not open battery settings directly", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (lang == "ar") "طلب الإذن بالتشغيل في الخلفية" else "Grant Background Permission",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
    

}

@Composable
fun OnboardingAutostartPage(
    lang: String = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current,
    isConfigured: Boolean,
    onConfiguredChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 32.dp, start = 24.dp, end = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = AppTheme.colors.primary,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (lang == "ar") "التشغيل التلقائي" else "SYSTEM AUTOSTART",
            color = AppTheme.colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Text(
            text = if (lang == "ar") "السماح بالتشغيل التلقائي" else "Enable Autostart",
            color = AppTheme.colors.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (lang == "ar") "تحظر بعض الأجهزة (مثل شاومي، أوبو، فيفو، هواوي) عمل التنبيهات في الخلفية بعد إعادة تشغيل الهاتف ما لم يتم تفعيل خيار 'التشغيل التلقائي' (Autostart) يدوياً."
                   else "Some devices (Xiaomi, Oppo, Vivo, Huawei) block background alerts after a reboot unless 'Autostart' is manually enabled for the app.",
            color = AppTheme.colors.grayMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        launchAutostartSettings(context)
                        onConfiguredChanged(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (lang == "ar") "فتح إعدادات التشغيل التلقائي" else "Open Autostart Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onConfiguredChanged(!isConfigured) }
                        .padding(8.dp)
                ) {
                    Checkbox(
                        checked = isConfigured,
                        onCheckedChange = { onConfiguredChanged(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AppTheme.colors.primary,
                            uncheckedColor = AppTheme.colors.grayMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == "ar") "لقد قمت بتفعيل التشغيل التلقائي لبرنامج عمران" else "I have enabled Autostart for Emran",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    

}

fun launchAutostartSettings(context: android.content.Context) {
    val intents = listOf(
        android.content.Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        android.content.Intent().setComponent(android.content.ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutostartManageActivity")),
        android.content.Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
        android.content.Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        android.content.Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        android.content.Intent().setComponent(android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        android.content.Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
        android.content.Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
        android.content.Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        android.content.Intent().setComponent(android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        android.content.Intent().setComponent(android.content.ComponentName("com.htc.pitroad", "com.htc.pitroad.MainActivity")),
        android.content.Intent().setComponent(android.content.ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"))
    )
    
    var launched = false
    for (intent in intents) {
        try {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            launched = true
            break
        } catch (e: Exception) {
            // Keep trying
        }
    }
    
    if (!launched) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }
}
