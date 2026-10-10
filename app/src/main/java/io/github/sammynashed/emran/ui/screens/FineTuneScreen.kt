package io.github.sammynashed.emran.ui.screens

import io.github.sammynashed.emran.ui.components.EmranAlertDialog
import io.github.sammynashed.emran.ui.components.EmranDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.sammynashed.emran.BuildConfig
import io.github.sammynashed.emran.data.update.UpdateChecker
import kotlinx.coroutines.launch
import android.os.Build
import android.os.PowerManager
import android.app.AlarmManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.*
import androidx.compose.foundation.background
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.service.NotificationHealth
import io.github.sammynashed.emran.service.rememberNotificationHealth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.res.painterResource
import io.github.sammynashed.emran.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.sammynashed.emran.data.LocationPreset
import io.github.sammynashed.emran.data.LocationPresets
import io.github.sammynashed.emran.data.PrayerTimeCalculator
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.net.URLEncoder

@Composable
fun FineTuneScreen(
    viewModel: PrayerViewModel,
    modifier: Modifier = Modifier,
    onReplayTour: (() -> Unit)? = null,
    onOpenAcknowledgements: () -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val offsets by viewModel.offsets.collectAsState()
    val activeThemePreset by viewModel.themePreset.collectAsState()
    val activeAppIconPreset by viewModel.appIconPreset.collectAsState()
    val activeLocationName by viewModel.locationName.collectAsState()
    val activeLat by viewModel.latitude.collectAsState()
    val activeLon by viewModel.longitude.collectAsState()
    val activeZoneId by viewModel.zoneId.collectAsState()
    val activeMethod by viewModel.calculationMethod.collectAsState()
    val activeSchool by viewModel.asrSchool.collectAsState()
    val activeHighLatRule by viewModel.highLatitudeRule.collectAsState()
    val activeThemeMode by viewModel.themeMode.collectAsState()
    val currentAdhanFromFlow by viewModel.defaultAdhanSound.collectAsState()
    val health = rememberNotificationHealth()

    // Dialog triggering states
    var showLocationPopup by remember { mutableStateOf(false) }
    var showMethodDialog by remember { mutableStateOf(false) }
    var showSchoolDialog by remember { mutableStateOf(false) }
    var showHighLatDialog by remember { mutableStateOf(false) }
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showAdhanDialog by remember { mutableStateOf(false) }
    var showOffsetsGroup by remember { mutableStateOf(false) }
    var locationSearchQuery by remember { mutableStateOf("") }
    
    // Immersive dialog states
    var showNotificationPrecisionDialog by remember { mutableStateOf(false) }
    var showCalculationSettingsDialog by remember { mutableStateOf(false) }
    var showCustomizationDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutEmranDialog by remember { mutableStateOf(false) }
    var showAboutDeveloperDialog by remember { mutableStateOf(false) }
    var showDevUpdatesDialog by remember { mutableStateOf(false) }
    var showOpenSourceDialog by remember { mutableStateOf(false) }
    var showPrivacyFirstDialog by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    // Update check: null = never asked this visit; a tap runs it once and shows the answer.
    var updateChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var showClockDialogFor by remember { mutableStateOf<Triple<String, String, java.time.LocalDateTime>?>(null) }

    BackHandler(enabled = true) {
        if (showClockDialogFor != null) {
            showClockDialogFor = null
        } else if (showMethodDialog) {
            showMethodDialog = false
        } else if (showSchoolDialog) {
            showSchoolDialog = false
        } else if (showHighLatDialog) {
            showHighLatDialog = false
        } else if (showThemeModeDialog) {
            showThemeModeDialog = false
        } else if (showAdhanDialog) {
            showAdhanDialog = false
        } else if (showLocationPopup) {
            showLocationPopup = false
        } else if (showNotificationPrecisionDialog) {
            showNotificationPrecisionDialog = false
        } else if (showCalculationSettingsDialog) {
            showCalculationSettingsDialog = false
        } else if (showCustomizationDialog) {
            showCustomizationDialog = false
        } else if (showLanguageDialog) {
            showLanguageDialog = false
        } else if (showAboutEmranDialog) {
            showAboutEmranDialog = false
        } else if (showAboutDeveloperDialog) {
            showAboutDeveloperDialog = false
        } else if (showDevUpdatesDialog) {
            showDevUpdatesDialog = false
        } else if (showOpenSourceDialog) {
            showOpenSourceDialog = false
        } else if (showPrivacyFirstDialog) {
            showPrivacyFirstDialog = false
        } else if (showUpdateDialog) {
            showUpdateDialog = false
        } else if (showVersionDialog) {
            showVersionDialog = false
        } else if (showOffsetsGroup) {
            showOffsetsGroup = false
        } else {
            showExitConfirmDialog = true
        }
    }

    if (showExitConfirmDialog) {
        val direction = if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
        EmranDialog(onDismissRequest = { showExitConfirmDialog = false }) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
                    color = Color(0xFF14111E),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = if (lang == "ar") "تأكيد الخروج" else "Exit App",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (lang == "ar") "هل أنت متأكد من رغبتك في إغلاق التطبيق؟" else "Are you sure you want to exit the app?",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showExitConfirmDialog = false }
                            ) {
                                Text(
                                    text = if (lang == "ar") "إلغاء" else "Cancel",
                                    color = AppTheme.colors.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    showExitConfirmDialog = false
                                    (context as? android.app.Activity)?.finish()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (lang == "ar") "خروج" else "Exit",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Easter egg click state
    var developerTaps by remember { mutableStateOf(0) }

    val baseTimes = remember(activeLat, activeLon, activeZoneId, activeMethod, activeSchool, activeHighLatRule) {
        val methodEnum = try { PrayerTimeCalculator.CalculationMethod.valueOf(activeMethod) } catch(e: Exception) { PrayerTimeCalculator.CalculationMethod.EGYPTIAN }
        val schoolEnum = try { PrayerTimeCalculator.AsrSchool.valueOf(activeSchool) } catch(e: Exception) { PrayerTimeCalculator.AsrSchool.SHAFII }
        val ruleEnum = try { PrayerTimeCalculator.HighLatitudeRule.valueOf(activeHighLatRule) } catch(e: Exception) { PrayerTimeCalculator.HighLatitudeRule.AUTOMATIC }
        val zone = PrayerTimeCalculator.safeZone(activeZoneId)

        PrayerTimeCalculator.calculateTimes(
            date = LocalDate.now(zone),
            latitude = activeLat,
            longitude = activeLon,
            zone = zone,
            method = methodEnum,
            school = schoolEnum,
            highLatitudeRule = ruleEnum
        )
    }

    val adjustables = remember(lang, baseTimes) {
        listOf(
            Triple("FAJR", if (lang == "ar") "الفجر" else "Fajr", baseTimes.fajr),
            Triple("SUNRISE", if (lang == "ar") "الشروق" else "Sunrise", baseTimes.sunrise),
            Triple("DHUHR", if (lang == "ar") "الظهر" else "Dhuhr", baseTimes.dhuhr),
            Triple("ASR", if (lang == "ar") "العصر" else "Asr", baseTimes.asr),
            Triple("MAGHRIB", if (lang == "ar") "المغرب" else "Maghrib", baseTimes.maghrib),
            Triple("ISHA", if (lang == "ar") "العشاء" else "Isha", baseTimes.isha)
        )
    }

    val methods = remember(lang) {
        listOf(
            "EGYPTIAN" to if (lang == "ar") "الهيئة المصرية العامة للمساحة" else "Egyptian General Authority",
            "UMM_AL_QURA" to if (lang == "ar") "جامعة أم القرى (مكة المكرمة)" else "Umm Al-Qura (Mecca)",
            "MWL" to if (lang == "ar") "رابطة العالم الإسلامي" else "Muslim World League",
            "ISNA" to if (lang == "ar") "الجمعية الإسلامية لأمريكا الشمالية" else "Islamic Society of North America",
            "KARACHI" to if (lang == "ar") "جامعة العلوم الإسلامية بكراتشي" else "University of Islamic Sciences, Karachi",
            "DUBAI" to if (lang == "ar") "دبي (الإمارات)" else "Dubai (UAE)",
            "QATAR" to if (lang == "ar") "قطر" else "Qatar",
            "KUWAIT" to if (lang == "ar") "الكويت" else "Kuwait",
            "SINGAPORE" to if (lang == "ar") "سنغافورة (المجلس الإسلامي)" else "Singapore (MUIS)",
            "TURKEY" to if (lang == "ar") "الديانة التركية" else "Diyanet (Turkey)",
            "TEHRAN" to if (lang == "ar") "معهد الجيوفيزياء، طهران" else "Institute of Geophysics, Tehran",
            "JAFARI" to if (lang == "ar") "الجعفري (الإثنا عشري)" else "Shia Ithna Ashari (Ja'fari)"
        )
    }

    val schools = remember(lang) {
        listOf(
            "SHAFII" to if (lang == "ar") "الجمهور (شافعي، مالكي، حنبلي)" else "Standard (Shafi'i, Maliki, Hanbali)",
            "HANAFI" to if (lang == "ar") "المذهب الحنفي" else "Hanafi School"
        )
    }

    val themeModes = remember(lang) {
        listOf(
            "DARK" to if (lang == "ar") "داكن" else "Dark",
            "LIGHT" to if (lang == "ar") "فاتح" else "Light",
            "SYSTEM" to if (lang == "ar") "حسب النظام" else "Follow system"
        )
    }

    // Only meaningful above roughly 48 degrees, where the sun may never reach the
    // Fajr or Isha depression angle. AUTOMATIC leaves lower latitudes untouched.
    val highLatitudeRules = remember(lang) {
        listOf(
            "AUTOMATIC" to if (lang == "ar") "تلقائي (موصى به)" else "Automatic (recommended)",
            "MIDDLE_OF_NIGHT" to if (lang == "ar") "منتصف الليل" else "Middle of the night",
            "ONE_SEVENTH_OF_NIGHT" to if (lang == "ar") "سُبع الليل" else "One-seventh of the night",
            "ANGLE_BASED" to if (lang == "ar") "حسب الزاوية (تناسبي)" else "Angle-based (proportional)",
            "NONE" to if (lang == "ar") "بدون تعديل (فلكي بحت)" else "None (astronomical only)"
        )
    }

    val themePresetsList = remember(lang) {
        listOf(
            "EXPRESSIVE" to if (lang == "ar") "المعبّر الملون" else "Expressive",
            "BLACK_STONE" to if (lang == "ar") "الحجر الأسود" else "The Black Stone",
            "COSMIC" to if (lang == "ar") "الكون الكويكب" else "Cosmic Noir",
            "TEAL" to if (lang == "ar") "البحر الفيروزي" else "Midnight Teal",
            "ROSE" to if (lang == "ar") "الورد الشفقي" else "Sunset Rose",
            "GOLDEN" to if (lang == "ar") "الواحة الذهبية" else "Golden Oasis",
            "EMERALD" to if (lang == "ar") "الزمرد الصافي" else "Ice Emerald",
            "OCEAN" to if (lang == "ar") "بحر الهادئ" else "Pacific Sea",
            "AMYTHIST" to if (lang == "ar") "الجمشت البنفسجي" else "Velvet Amethyst",
            "SUNFIRE" to if (lang == "ar") "الوهج الشمسي" else "Solar Flare",
            "SAPPHIRE" to if (lang == "ar") "الياقوت الأزرق" else "Neon Sapphire",
            "LAVENDER" to if (lang == "ar") "الخزامى الريفي" else "Lavender Field",
            "MINT" to if (lang == "ar") "النعناع الجليدي" else "Polar Glacier",
            "FOREST" to if (lang == "ar") "صنوبر الغابة" else "Pine Evergreen",
            "BLOOD_MOON" to if (lang == "ar") "قمر الدم الأحمر" else "Crimson Moon",
            "MONOCHROME" to if (lang == "ar") "الأردواز الأحادي" else "Pure Slate",
            "SAKURA" to if (lang == "ar") "الساكورا الوردية" else "Tokyo Sakura",
            "NEBULA" to if (lang == "ar") "السديم الساطع" else "Nebula Spark",
            "DESERT" to if (lang == "ar") "صحراء النفوذ" else "Sahara Nomad",
            "AURORA" to if (lang == "ar") "الشفق المضيء" else "Boreal Lights"
        )
    }

    val listState = rememberLazyListState()
    // Settings has nothing to "refresh": every row reads live state from the ViewModel.
    // (An earlier pull-to-refresh here swallowed scrolls near the top, then waited 1.5 s
    // and reported "Settings refreshed" without doing anything.)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        // Premium Editorial Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = if (lang == "ar") "عِمران" else "EMRAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.primary,
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (lang == "ar") "الإعدادات" else "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp
                )
            }
        }

        // Shown only when something will stop the adhan reaching the user.
        if (!health.allGood) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFFFB74D).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showNotificationPrecisionDialog = true
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "ar") "قد لا يصلك الأذان" else "The adhan may not reach you",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lang == "ar") "${health.problems} إعدادات تحتاج إلى تفعيل. اضغط للإصلاح."
                            else "${health.problems} setting${if (health.problems > 1) "s" else ""} need attention. Tap to fix.",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
                }
            }
        }

        // --- PRAYER SECTION ---
        item {
            SettingsSectionHeader(title = if (lang == "ar") "مواقيت الصلاة" else "Prayer")
        }

        // 1. Adhan Notifications
        item {
            val adhanLabel = when (currentAdhanFromFlow) {
                "AL_AQSA" -> if (lang == "ar") "أذان المسجد الأقصى" else "Al-Aqsa Adhan"
                "MAKKAH" -> if (lang == "ar") "أذان مكة المكرمة" else "Makkah Adhan"
                "MADINAH" -> if (lang == "ar") "أذان المدينة المنورة" else "Madinah Adhan"
                "default" -> if (lang == "ar") "الصوت الافتراضي للنظام" else "Android System Default"
                "ahmad_alnafees" -> if (lang == "ar") "أحمد النفيس" else "Ahmad Alnafys"
                "mishary_alafasy" -> if (lang == "ar") "مشاري راشد العفاسي" else "Mshary Rashed Alaafasy"
                "saad_alghamdi" -> if (lang == "ar") "سعد الغامدي" else "Saad Alghamdy"
                else -> currentAdhanFromFlow
            }
            PremiumSettingsRow(
                title = if (lang == "ar") "أصوات الأذان والتنبيهات" else "Adhan Notifications",
                subtitle = adhanLabel,
                leadingIcon = Icons.Default.Notifications,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAdhanDialog = true
                }
            )
        }

        // 2. Notification Precision
        item {
            val precisionSubtitle = if (health.allGood) {
                if (lang == "ar") "كل شيء جاهز ✅ التنبيهات ستصل في وقتها" else "All set ✅ Adhan will arrive on time"
            } else {
                if (lang == "ar") "${health.problems} إعدادات تمنع وصول التنبيه ⚠️ اضغط للإصلاح"
                else "${health.problems} setting${if (health.problems > 1) "s" else ""} may block the adhan ⚠️ Tap to fix"
            }

            PremiumSettingsRow(
                title = if (lang == "ar") "دقة التنبيهات والاشعارات" else "Notification Precision",
                subtitle = precisionSubtitle,
                leadingIcon = Icons.Default.Alarm,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showNotificationPrecisionDialog = true
                }
            )
        }

        // 3. Prayer Calculation Method
        item {
            val curLabel = methods.find { it.first == activeMethod }?.second ?: activeMethod
            PremiumSettingsRow(
                title = if (lang == "ar") "طريقة حساب مواقيت الصلاة" else "Prayer Calculation Method",
                subtitle = LocationPresets.getLocalizedName(activeLocationName, lang) + " • " + curLabel,
                leadingIcon = Icons.Default.Explore,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showCalculationSettingsDialog = true
                }
            )
        }

        // --- PERSONALIZATION SECTION ---
        item {
            SettingsSectionHeader(title = if (lang == "ar") "التخصيص الشخصي" else "Personalization")
        }

        // 1. Customization (Theme, App Icon)
        item {
            val activeThemeLabel = themePresetsList.find { it.first == activeThemePreset }?.second ?: activeThemePreset
            val activeIconLabel = themePresetsList.find { it.first == activeAppIconPreset }?.second ?: activeAppIconPreset
            PremiumSettingsRow(
                title = if (lang == "ar") "تخصيص المظهر والأيقونة" else "Customization",
                subtitle = if (lang == "ar") "المظهر: $activeThemeLabel • الأيقونة: $activeIconLabel" else "Theme: $activeThemeLabel • Icon: $activeIconLabel",
                leadingIcon = Icons.Default.Palette,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showCustomizationDialog = true
                }
            )
        }

        // 2. Light / dark mode
        item {
            val activeModeLabel = themeModes.find { it.first == activeThemeMode }?.second ?: activeThemeMode
            PremiumSettingsRow(
                title = if (lang == "ar") "الوضع الليلي والنهاري" else "Appearance",
                subtitle = activeModeLabel,
                leadingIcon = when (activeThemeMode) {
                    "LIGHT" -> Icons.Default.LightMode
                    "DARK" -> Icons.Default.DarkMode
                    else -> Icons.Default.BrightnessAuto
                },
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showThemeModeDialog = true
                }
            )
        }

        // 3. Language
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "لغة التطبيق" else "Language",
                subtitle = if (lang == "ar") "العربية" else "English",
                leadingIcon = Icons.Default.Language,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showLanguageDialog = true
                }
            )
        }

        // --- SUPPORT SECTION ---
        item {
            SettingsSectionHeader(title = if (lang == "ar") "الدعم والمجتمع" else "Support")
        }

        // 1. Report an Issue
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "الإبلاغ عن مشكلة" else "Report an Issue",
                subtitle = if (lang == "ar") "تواصل مباشر مع المطور لحل أي خلل" else "Direct message with developer Sammy Nashed",
                leadingIcon = Icons.Default.BugReport,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val textMsg = if (lang == "ar") "السلام عليكم، واجهتني مشكلة في تطبيق عمران: " else "Assalamu Alaikum Sammy, I found an issue in Emran app: "
                    try {
                        val url = "https://wa.me/963986157471?text=" + URLEncoder.encode(textMsg, "UTF-8")
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(context, if (lang == "ar") "فشل فتح واتساب" else "Failed to open WhatsApp", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // 2. Suggest a Feature
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "اقتراح ميزة جديدة" else "Suggest a Feature",
                subtitle = if (lang == "ar") "شارك أفكارك وساهم في بناء مستقبل عمران" else "Participate in shaping the future of Emran",
                leadingIcon = Icons.Default.Lightbulb,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val textMsg = if (lang == "ar") "السلام عليكم، أود اقتراح ميزة لتطبيق عمران: " else "Assalamu Alaikum Sammy, I would like to suggest a feature for Emran: "
                    try {
                        val url = "https://wa.me/963986157471?text=" + URLEncoder.encode(textMsg, "UTF-8")
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(context, if (lang == "ar") "فشل فتح واتساب" else "Failed to open WhatsApp", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // 3. Replay Interactive Tour
        item {
            val tourManager = io.github.sammynashed.emran.ui.tour.LocalTourManager.current
            PremiumSettingsRow(
                title = if (lang == "ar") "إعادة جولة التطبيق التفاعلية" else "Replay Interactive Tour",
                subtitle = if (lang == "ar") "أعد استكشاف ميزات عمران التوجيهية" else "Rediscover Emran's interactive guide and tutorial",
                leadingIcon = Icons.Default.Navigation,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (onReplayTour != null) {
                        onReplayTour()
                    } else {
                        tourManager?.restartTour()
                    }
                }
            )
        }

        // --- ABOUT SECTION & SIGNATURE ---
        item {
            SettingsSectionHeader(title = if (lang == "ar") "حول عِمران" else "About")
        }

        // 1. About Emran Description
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "حول تطبيق عِمران" else "About Emran",
                subtitle = if (lang == "ar") "تعرّف على رسالة وهدف التطبيق السامية" else "The open-source companion designed for the soul",
                leadingIcon = Icons.Default.Info,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAboutEmranDialog = true
                }
            )
        }

        // 1b. About Developer
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "حول المطور" else "About Developer",
                subtitle = if (lang == "ar") "تواصل وتعرف على مطور التطبيق" else "Connect with the developer Sammy Nashed",
                leadingIcon = Icons.Default.Person,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAboutDeveloperDialog = true
                }
            )
        }

        // 2. Development Updates
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "تحديثات التطوير المستمرة" else "Development Updates",
                subtitle = if (lang == "ar") "تابع سجل التغييرات وخارطة الطريق" else "Chronological update history (Active Maintenance)",
                leadingIcon = Icons.Default.Update,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDevUpdatesDialog = true
                }
            )
        }

        // 3. Open Source Transparency
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "مشروع مفتوح المصدر" else "Open Source",
                subtitle = if (lang == "ar") "تصفّح الكود البرمجي والتراخيص والمساهمين" else "Code repository, licensing, and contributions",
                leadingIcon = Icons.Default.Code,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showOpenSourceDialog = true
                }
            )
        }

        // 4. Privacy First
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "الخصوصية أولاً ودائماً" else "Privacy First",
                subtitle = if (lang == "ar") "لا تتبع ولا إعلانات، ويعمل دون إنترنت" else "Fully offline. No ads, no data collected. Optional downloads only",
                leadingIcon = Icons.Default.Security,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showPrivacyFirstDialog = true
                }
            )
        }

        // 5. Acknowledgements
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "لوحة الشكر والتقدير" else "Acknowledgements",
                subtitle = if (lang == "ar") "شكر خاص للمختبرين الأوائل والمجتمع" else "Gratitude to testers and community feedback",
                leadingIcon = Icons.Default.Star,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenAcknowledgements()
                }
            )
        }

        // 5b. Check for updates (only when tapped; nothing here runs in the background)
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "التحقق من التحديثات" else "Check for Updates",
                subtitle = when {
                    updateChecking -> if (lang == "ar") "جارٍ التحقق…" else "Checking…"
                    updateResult is UpdateChecker.Result.UpToDate ->
                        if (lang == "ar") "أنت تستخدم أحدث إصدار" else "You have the latest version"
                    updateResult is UpdateChecker.Result.Offline ->
                        if (lang == "ar") "تعذّر التحقق، تأكد من اتصالك بالإنترنت" else "Couldn't check. Is your internet on?"
                    updateResult is UpdateChecker.Result.Failed ->
                        if (lang == "ar") "تعذّر التحقق الآن، حاول لاحقاً" else "Couldn't check right now. Try again later"
                    else -> if (lang == "ar") "اضغط للتحقق من وجود إصدار جديد" else "Tap to see if a newer version is out"
                },
                leadingIcon = Icons.Default.SystemUpdate,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!updateChecking) {
                        updateChecking = true
                        updateScope.launch {
                            val r = UpdateChecker.check(lang == "ar")
                            updateResult = r
                            updateChecking = false
                            if (r is UpdateChecker.Result.Available) showUpdateDialog = true
                        }
                    }
                }
            )
        }

        // 6. Version Detail
        item {
            PremiumSettingsRow(
                title = if (lang == "ar") "إصدار التطبيق والتقنيات" else "Version",
                subtitle = "v${BuildConfig.VERSION_NAME} • Built with care",
                leadingIcon = Icons.Default.PhoneAndroid,
                iconTint = AppTheme.colors.secondary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showVersionDialog = true
                }
            )
        }

        // Elegant minimal brand sign-off
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(AppTheme.colors.primary.copy(alpha = 0.25f), CircleShape)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (lang == "ar") "عِمران" else "EMRAN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = Color.White.copy(alpha = 0.25f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (lang == "ar") "صُنع بحب وإخلاص كصدقة جارية" else "Sustainably crafted with care & devotion",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.15f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.12f)
                )
            }
        }
    }

    // ==========================================
    // ============ DIALOGS & OVERLAYS ==========
    // ==========================================

    // 1. NOTIFICATION PRECISION CALIBRATION DIALOG
    if (showNotificationPrecisionDialog) {
        EmranAlertDialog(
            onDismissRequest = { showNotificationPrecisionDialog = false },
            title = {
                Text(
                    text = if (lang == "ar") "موثوقية التنبيهات" else "NOTIFICATION RELIABILITY",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (lang == "ar")
                            "لكي يصلك الأذان في وقته حتى لو كان التطبيق مغلقاً، يجب أن تكون كل هذه الإعدادات مفعّلة. تتحدّث الحالة تلقائياً عند عودتك من الإعدادات."
                        else
                            "For the adhan to reach you on time even when Emran is closed, all of these must be on. The status updates by itself when you come back from Settings.",
                        color = AppTheme.colors.grayMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    ReliabilityCard(
                        title = if (lang == "ar") "إذن الإشعارات" else "Notifications allowed",
                        detail = if (lang == "ar") "بدونه لا يستطيع التطبيق عرض أي تنبيه" else "Without this Emran cannot show any alert",
                        ok = health.notificationsAllowed,
                        okLabel = if (lang == "ar") "مسموح" else "Allowed",
                        badLabel = if (lang == "ar") "مغلق" else "Blocked",
                        actionLabel = if (lang == "ar") "فتح إعدادات الإشعارات" else "Open notification settings",
                        onAction = { NotificationHealth.openNotificationSettings(context) }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ReliabilityCard(
                            title = if (lang == "ar") "المنبهات الدقيقة" else "Exact alarms",
                            detail = if (lang == "ar") "يطلق الأذان في الثانية المحددة لا بعدها بدقائق" else "Fires the adhan at the exact second, not minutes later",
                            ok = health.exactAlarmAllowed,
                            okLabel = if (lang == "ar") "مسموح" else "Allowed",
                            badLabel = if (lang == "ar") "مرفوض" else "Restricted",
                            actionLabel = if (lang == "ar") "منح صلاحية المنبه الدقيق" else "Allow exact alarms",
                            onAction = {
                                try {
                                    context.startActivity(
                                        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                            .apply { data = Uri.parse("package:${context.packageName}") }
                                    )
                                } catch (e: Exception) {
                                    NotificationHealth.openNotificationSettings(context)
                                }
                            }
                        )
                    }

                    ReliabilityCard(
                        title = if (lang == "ar") "بلا قيود على البطارية" else "Battery unrestricted",
                        detail = if (lang == "ar") "يمنع النظام من تجميد مهام التطبيق في الخلفية" else "Stops Android freezing Emran's background work",
                        ok = health.batteryUnrestricted,
                        okLabel = if (lang == "ar") "مفعّل" else "Enabled",
                        badLabel = if (lang == "ar") "مقيّد" else "Optimised",
                        actionLabel = if (lang == "ar") "السماح بالعمل بلا قيود" else "Allow unrestricted",
                        onAction = {
                            try {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        .apply { data = Uri.parse("package:${context.packageName}") }
                                )
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Settings could not be opened", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    // Xiaomi / Redmi / POCO only. This is the one that silently kills alarms.
                    health.autostartAllowed?.let { allowed ->
                        ReliabilityCard(
                            title = if (lang == "ar") "التشغيل التلقائي (شاومي)" else "Autostart (Xiaomi)",
                            detail = if (lang == "ar")
                                "من دونه يمنع نظام شاومي التطبيق من الاستيقاظ وقت الأذان فلا يصلك أي تنبيه، حتى لو كل شيء آخر مفعّل"
                            else
                                "Without it, Xiaomi's system will not wake Emran at prayer time, so no alert arrives, even with everything else on",
                            ok = allowed,
                            okLabel = if (lang == "ar") "مفعّل" else "Enabled",
                            badLabel = if (lang == "ar") "مغلق" else "Off",
                            actionLabel = if (lang == "ar") "فتح التشغيل التلقائي" else "Open Autostart list",
                            onAction = { NotificationHealth.openAutostartSettings(context) }
                        )
                    }

                    Button(
                        onClick = {
                            NotificationHealth.sendTest(context)
                            Toast.makeText(
                                context,
                                if (lang == "ar") "أُرسل تنبيه تجريبي" else "Test alert sent",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (lang == "ar") "إرسال تنبيه تجريبي (يشغّل الأذان)" else "Send a test alert (plays the adhan)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationPrecisionDialog = false }) {
                    Text(if (lang == "ar") "حسناً" else "DONE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 2. UNIFIED PRAYER CALCULATION & LOCATION SETTINGS DIALOG
    if (showCalculationSettingsDialog) {
        val curMethodLabel = methods.find { it.first == activeMethod }?.second ?: activeMethod
        val curSchoolLabel = schools.find { it.first == activeSchool }?.second ?: activeSchool
        val curHighLatLabel = highLatitudeRules.find { it.first == activeHighLatRule }?.second ?: activeHighLatRule

        EmranAlertDialog(
            onDismissRequest = { showCalculationSettingsDialog = false },
            title = {
                Text(
                    text = if (lang == "ar") "حساب المواقيت والمعايرة" else "PRAYER CALCULATION SETTINGS",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (lang == "ar") "تعديل معايير الحساب والموقع وجدول الإزاحة الجغرافي:" else "Configure geographic parameters and fine-tune calculated times:",
                        color = AppTheme.colors.grayMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Option 1: Location selection
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showLocationPopup = true
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (lang == "ar") "الموقع الجغرافي" else "Location Preset", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(LocationPresets.getLocalizedName(activeLocationName, lang) + " (${String.format("%.2f", activeLat)}°, ${String.format("%.2f", activeLon)}°)", color = AppTheme.colors.grayMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Option 2: Calculation Method
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMethodDialog = true
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (lang == "ar") "طريقة الحساب والمنهج" else "Calculation Authority", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(curMethodLabel, color = AppTheme.colors.grayMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Option 3: Asr Juristic School
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSchoolDialog = true
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (lang == "ar") "المذهب الفقهي لعصر" else "Asr Juristic School", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(curSchoolLabel, color = AppTheme.colors.grayMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Option 4: High-latitude rule.
                    // Only surfaced past 48 degrees, where Fajr and Isha can stop
                    // having an astronomical time at all. Showing it in Cairo would
                    // be noise; hiding it in Stockholm would be a missing feature.
                    if (kotlin.math.abs(activeLat) > 48.0) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showHighLatDialog = true
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WbTwilight, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(if (lang == "ar") "قاعدة خطوط العرض العليا" else "High-Latitude Rule", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(curHighLatLabel, color = AppTheme.colors.grayMuted, fontSize = 11.sp)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Option 5: Calibrate Offsets collapsible trigger
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showOffsetsGroup = !showOffsetsGroup
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(if (lang == "ar") "معايرة أوقات الصلوات" else "Time Offsets Calibration", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(if (lang == "ar") "ضبط يدوي دقيق بالدقائق لكل صلاة" else "Fine-tune standard calculations in minutes", color = AppTheme.colors.grayMuted, fontSize = 11.sp)
                                }
                                Icon(
                                    imageVector = if (showOffsetsGroup) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = if (showOffsetsGroup) AppTheme.colors.secondary else Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = showOffsetsGroup,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    adjustables.forEach { (key, label, baseDateTime) ->
                                        val offsetVal = offsets[key] ?: 0
                                        val calibratedTime = baseDateTime.plusMinutes(offsetVal.toLong())
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showClockDialogFor = Triple(key, label, baseDateTime)
                                                }
                                                .padding(vertical = 10.dp, horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "Base: ${baseDateTime.format(formatter)}", color = AppTheme.colors.grayMuted, fontSize = 10.sp)
                                            }
                                            
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (offsetVal != 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(AppTheme.colors.secondary.copy(alpha = 0.12f))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(text = if (offsetVal > 0) "+$offsetVal m" else "$offsetVal m", color = AppTheme.colors.secondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Text(text = calibratedTime.format(formatter), color = if (offsetVal != 0) AppTheme.colors.secondary else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Icon(Icons.Default.Edit, contentDescription = null, tint = AppTheme.colors.primary.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalculationSettingsDialog = false }) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 3. COLOR THEME & CUSTOM APP ICON CUSTOMIZATION DIALOG
    if (showCustomizationDialog) {
        EmranDialog(
            onDismissRequest = { showCustomizationDialog = false }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF110D16),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (lang == "ar") "تخصيص المظهر والأيقونة" else "PERSONALIZATION GALLERY",
                                color = AppTheme.colors.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (lang == "ar") "السمات والرموز" else "Themes & App Icons",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        
                        // Clean, high-end close button
                        IconButton(
                            onClick = { showCustomizationDialog = false },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.04f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // Division Line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.06f))
                    )
                    
                    // Main Customization Options Area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. THEMES
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(AppTheme.colors.primary)
                            )
                            Text(
                                text = (if (lang == "ar") "سمات المظهر والألوان" else "Color Theme Presets").uppercase(),
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                        
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            items(themePresetsList) { (key, label) ->
                                val isSelected = activeThemePreset == key
                                val strokeColor = if (isSelected) AppTheme.colors.primary else Color.White.copy(alpha = 0.05f)
                                val colorAccent = when (key) {
                                    "EXPRESSIVE" -> Color(0xFF6366F1) to Color(0xFFF97316)
                                    "COSMIC" -> Color(0xFFFF3344) to Color(0xFFFBBF24)
                                    "TEAL" -> Color(0xFF06B6D4) to Color(0xFFF43F5E)
                                    "ROSE" -> Color(0xFFEC4899) to Color(0xFFFBBF24)
                                    "GOLDEN" -> Color(0xFFD4AF37) to Color(0xFFF59E0B)
                                    "EMERALD" -> Color(0xFF10B981) to Color(0xFF34D399)
                                    "OCEAN" -> Color(0xFF3B82F6) to Color(0xFF60A5FA)
                                    "AMYTHIST" -> Color(0xFF8B5CF6) to Color(0xFFD8B4FE)
                                    "SUNFIRE" -> Color(0xFFEF4444) to Color(0xFFF97316)
                                    "SAPPHIRE" -> Color(0xFF2563EB) to Color(0xFFA5F3FC)
                                    "LAVENDER" -> Color(0xFFA78BFA) to Color(0xFFF472B6)
                                    "MINT" -> Color(0xFF06B6D4) to Color(0xFF99F6E4)
                                    "FOREST" -> Color(0xFF4ADE80) to Color(0xFFFACC15)
                                    "BLOOD_MOON" -> Color(0xFFDC2626) to Color(0xFF9CA3AF)
                                    "MONOCHROME" -> Color(0xFFE2E8F0) to Color(0xFF94A3B8)
                                    "SAKURA" -> Color(0xFFFBCFE8) to Color(0xFFFDA4AF)
                                    "NEBULA" -> Color(0xFFC084FC) to Color(0xFF818CF8)
                                    "DESERT" -> Color(0xFFF59E0B) to Color(0xFFD97706)
                                    "AURORA" -> Color(0xFF2DD4BF) to Color(0xFFC084FC)
                                    "BLACK_STONE" -> Color(0xFFA3A3A3) to Color(0xFF6B7280)
                                    else -> Color(0xFF6366F1) to Color(0xFFEC4899)
                                }


                                Box(
                                    modifier = Modifier
                                        .width(112.dp)
                                        .height(82.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) colorAccent.first.copy(alpha = 0.08f)
                                            else Color.White.copy(alpha = 0.02f)
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) colorAccent.first.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.06f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setThemePreset(key)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            
                                            .padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Concentric Design Token representing colors
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .background(
                                                            Brush.radialGradient(
                                                                colors = listOf(colorAccent.first.copy(alpha = 0.3f), Color.Transparent)
                                                            )
                                                        )
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy((-6).dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(colorAccent.first)
                                                        .border(1.5.dp, Color(0xFF110D16), CircleShape)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(colorAccent.second)
                                                        .border(1.5.dp, Color(0xFF110D16), CircleShape)
                                                )
                                            }
                                        }

                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }

                                    // Top Right Selected Indicator Dot
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(colorAccent.first)
                                        )
                                    }

                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        // 2. APP ICONS
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(AppTheme.colors.secondary)
                            )
                            Text(
                                text = (if (lang == "ar") "أيقونة التطبيق المخصصة" else "App Icon Presets").uppercase(),
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                        
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            items(themePresetsList) { (key, label) ->
                                val isSelected = activeAppIconPreset == key
                                val strokeColor = if (isSelected) AppTheme.colors.secondary else Color.White.copy(alpha = 0.05f)


                                val iconColors = remember(key) {
                                    io.github.sammynashed.emran.ui.theme.getColorsByPreset(key)
                                }

                                Box(
                                    modifier = Modifier
                                        .width(108.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) AppTheme.colors.secondary.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.02f))
                                        .border(if (isSelected) 1.5.dp else 1.dp, strokeColor, RoundedCornerShape(12.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setAppIconPreset(key)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            
                                            .padding(vertical = 12.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            iconColors.primary.copy(alpha = 0.12f),
                                                            iconColors.secondary.copy(alpha = 0.04f)
                                                        )
                                                    )
                                                )
                                                .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(bottom = 3.dp),
                                                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                val heights = listOf(7.dp, 11.dp, 15.dp, 11.dp, 7.dp)
                                                heights.forEach { h ->
                                                    Box(
                                                        modifier = Modifier
                                                            .width(2.dp)
                                                            .height(h)
                                                            .clip(RoundedCornerShape(topStart = 0.5.dp, topEnd = 0.5.dp))
                                                            .background(Brush.verticalGradient(colors = listOf(iconColors.primary, iconColors.secondary)))
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else AppTheme.colors.grayMuted,
                                            fontSize = 10.5.sp,
                                            lineHeight = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }

                                }
                            }
                        }
                    }
                    
                    // High-end Apply capsule button
                    Button(
                        onClick = { showCustomizationDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.primary.copy(alpha = 0.12f),
                            contentColor = AppTheme.colors.primary
                        )
                    ) {
                        Text(
                            text = if (lang == "ar") "حفظ وتطبيق" else "APPLY & CLOSE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }

    // 4. LANGUAGE SELECTOR DIALOG
    if (showLanguageDialog) {
        EmranAlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = if (lang == "ar") "لغة التطبيق" else "APP LANGUAGE",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentSelection = lang
                    val langOptions = listOf("en" to "English (UK/US)", "ar" to "العربية (Arabic)")

                    langOptions.forEach { (code, label) ->
                        val isSelected = currentSelection == code
                        val isSelectedBg = if (isSelected) AppTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(isSelectedBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setCurrentLanguage(code)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else AppTheme.colors.onBackground,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = AppTheme.colors.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(if (lang == "ar") "إلغاء" else "CANCEL", color = AppTheme.colors.grayMuted)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 5. ABOUT EMRAN IMMERSIVE DIALOG
    if (showAboutEmranDialog) {
        EmranAlertDialog(
            onDismissRequest = { showAboutEmranDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == "ar") "حول تطبيق عِمران" else "ABOUT EMRAN",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. App Header with authentic launcher foreground logo
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "Emran Logo",
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (lang == "ar") "عِمران" else "Emran",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = if (lang == "ar") 0.sp else 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (lang == "ar") "عِمران الروح والوجدان" else "The Prosperity of the Soul",
                            color = AppTheme.colors.grayMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 2. Short Description (Max 2-3 lines)
                    Text(
                        text = if (lang == "ar")
                            "رفيق إسلامي مفتوح المصدر وبسيط: مواقيت الصلاة والأذان، والقرآن والحفظ، والأذكار والحديث."
                        else
                            "A simple, open-source Islamic companion: prayer times and adhan, the Quran and Hifz, remembrance and Hadith.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (lang == "ar") "نظرة عامة على الميزات" else "Feature Architecture",
                        color = AppTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val featuresList = listOf(
                        "🕌 " + (if (lang == "ar") "عجلة أوقات الصلوات" else "Prayer Times Wheel"),
                        "🔔 " + (if (lang == "ar") "تنبيهات الأذان الدقيقة" else "Precise Adhan Alerts"),
                        "📖 " + (if (lang == "ar") "مصحف لقراءة القرآن الكريم" else "Integrated Quran Reader"),
                        "🧭 " + (if (lang == "ar") "بوصلة القبلة الدقيقة" else "Qibla Compass"),
                        "📿 " + (if (lang == "ar") "عداد التسبيح الذكي" else "Interactive Tasbeeh"),
                        "📚 " + (if (lang == "ar") "مكتبة الأذكار المتكاملة" else "Adkhar Reference Library"),
                        "🎨 " + (if (lang == "ar") "سمات مظهر ملونة" else "Color Themes")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        featuresList.forEach { feat ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.02f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(text = feat, color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutEmranDialog = false }) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // About Developer Dialog
    if (showAboutDeveloperDialog) {
        EmranAlertDialog(
            onDismissRequest = { showAboutDeveloperDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == "ar") "حول المطور" else "ABOUT DEVELOPER",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (lang == "ar")
                            "مرحباً! أنا سامي ناشد، مطور برمجيات شغوف ببناء تطبيقات تخدم المجتمع الإسلامي وتجمع بين دقة التقنية وسمو الروح والأخلاق الفاضلة."
                        else
                            "Hello! I am Sammy Nashed, a software engineer passionate about building high-quality, privacy-focused Islamic applications that blend engineering excellence with spiritual beauty.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buttons Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinkedInIcon(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/sammy-nashed-480aaa318/"))
                                context.startActivity(intent)
                            }
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        GitHubIcon(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SammyNashed"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDeveloperDialog = false }) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 6. DEVELOPMENT UPDATES IMMERSIVE DIALOG
    if (showDevUpdatesDialog) {
        EmranAlertDialog(
            onDismissRequest = { showDevUpdatesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Update, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == "ar") "سجل تحديثات التطوير" else "DEVELOPMENT TIMELINE",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (lang == "ar") "خارطة الطريق وسجل الاصدارات البرمجية النشطة لعمران:" else "Chronological releases & active software updates for Emran:",
                        color = AppTheme.colors.grayMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    // Timeline updates.
                    //
                    // Two releases, because there have only ever been two: v1.0.0
                    // shipped as "Emad", and everything since is v2.0.0. Written
                    // for the person reading it, so it lists what changed for
                    // them, not how it was built.
                    val updates = listOf(
                        Triple(
                            "v2.0.0",
                            if (lang == "ar") "التحديث الكبير (عمران)" else "The Emran Overhaul",
                            listOf(
                                if (lang == "ar") "اسم جديد وهوية جديدة: عمران." else "A new name and a new identity: Emran.",
                                if (lang == "ar") "مصحف كامل بجميع السور الـ١١٤ للقراءة دون إنترنت، بالتنقّل بالسورة أو الجزء أو الصفحة، مع البحث والعلامات المرجعية." else "A complete Mushaf: all 114 surahs offline, browsable by surah, juz or page, with search and bookmarks.",
                                if (lang == "ar") "تتبّع قراءتك للقرآن سورةً بسورة، وعدّ ختماتك الكاملة، ولا تُحتسب السورة مرتين." else "Your Quran reading now tracks itself surah by surah and counts every khatmah you complete.",
                                if (lang == "ar") "مكتبة أذكار من ٥٥ ذكراً للصباح والمساء وبعد الصلاة وقبل النوم وعند الاستيقاظ، كلٌّ بمصدره وفضله." else "An adhkar library of 55 supplications for morning, evening, after prayer, before sleep and on waking, each with its source and virtue.",
                                if (lang == "ar") "وضع المسبحة داخل الأذكار: اضغط على الذكر ليبدأ العدّ، ويتقدّم تلقائياً إلى الذكر التالي عند بلوغ عدد تكراره." else "Masbaha mode in adhkar: tap a dhikr to start counting, and it moves to the next one when you reach its repetition.",
                                if (lang == "ar") "مسبحة رقمية مستقلة بأهداف مخصصة وعدّ للأشواط واهتزاز خفيف مع كل ضغطة للعدّ دون النظر إلى الشاشة." else "A standalone digital masbaha with custom targets, lap counting and a gentle buzz per count so you can tally without looking.",
                                if (lang == "ar") "ودجات للشاشة الرئيسية: صلوات اليوم، بوصلة الصلاة الدائرية، العدّ التنازلي الحيّ، سلسلة الأيام، التاريخ الهجري، وتقدّم القرآن." else "Home-screen widgets: today's prayers, the circular prayer compass, a live ticking countdown, your streak, the Hijri date and Quran progress.",
                                if (lang == "ar") "واجهة عربية كاملة من اليمين إلى اليسار، في التطبيق وفي الودجات." else "A full Arabic interface, right to left, across the app and the widgets.",
                                if (lang == "ar") "٢٠ سمة لونية بأيقونات مطابقة للتطبيق، وأوضاع فاتح وداكن أو حسب النظام." else "20 colour themes with matching app icons, in light, dark or follow-system.",
                                if (lang == "ar") "اثنتا عشرة طريقة معتمدة لحساب المواقيت، مع ضبط يدوي لكل صلاة وقواعد خاصة بالمناطق شمال خط العرض العالي." else "Twelve recognised calculation methods, per-prayer manual tuning, and rules for high-latitude regions where the sun never reaches the angle.",
                                if (lang == "ar") "أصوات أذان متعددة بأصوات مشاري العفاسي وسعد الغامدي وأحمد النفيس." else "A choice of adhan voices: Mishary Alafasy, Saad Al-Ghamdi and Ahmad Al-Nafees.",
                                if (lang == "ar") "جولة تعريفية تفاعلية عند أول تشغيل ترشدك عبر التطبيق خطوة بخطوة." else "An interactive guided tour on first launch that walks you through the app step by step.",
                                if (lang == "ar") "تحديد موقعك بضغطة واحدة عند الإعداد، ويبقى على جهازك ولا يُطلب منك مرة أخرى." else "One-tap location during setup. It stays on your device and you are never asked again.",
                                if (lang == "ar") "١٧ مهمة روحية و٨ مقامات ترتقي فيها، مع شارات تُفتح مع الاستمرار." else "17 spiritual quests and 8 ranks to climb, with badges that unlock as you keep going.",
                                if (lang == "ar") "احتفال بالإنجازات عند بلوغ ٧ و٣٠ و١٠٠ يوم متتالٍ وما بعدها، يظهر في الودجة أيضاً." else "Milestone celebrations at 7, 30, 100 consecutive days and beyond, shown on your widget too.",
                                if (lang == "ar") "بوصلة قبلة أدقّ بدمج حسّاسات الجهاز وتصحيح الانحراف المغناطيسي، مع إرشاد للمعايرة." else "A more accurate Qibla compass, correcting for magnetic declination with live calibration guidance.",
                                if (lang == "ar") "تصميم زجاجي جديد وخطوط أوضح في كل شاشة." else "A new glass design and clearer type throughout."
                            )
                        ),
                        Triple(
                            if (lang == "ar") "الإصلاحات" else "Fixes",
                            if (lang == "ar") "ما تم إصلاحه في هذا الإصدار" else "Repaired in this release",
                            listOf(
                                if (lang == "ar") "مواقيت الصلاة تتبع الآن مدينتك المختارة عند السفر، بدل توقيت الجهاز." else "Prayer times now follow the city you chose when you travel, instead of your phone's own clock.",
                                if (lang == "ar") "المواقيت صحيحة عند تغيّر التوقيت الصيفي، ولم تعد تنزاح ساعة مرتين في السنة." else "Times stay correct when daylight saving changes, instead of drifting by an hour twice a year.",
                                if (lang == "ar") "لم تعد تنبيهات الأذان تتكرّر، وتُعاد جدولتها تلقائياً بعد إعادة تشغيل الهاتف." else "Adhan notifications no longer arrive twice, and reschedule themselves after a restart.",
                                if (lang == "ar") "تتحدّث الودجات فوراً عند تغيير السمة أو الوضع الفاتح والداكن." else "Widgets update immediately when you change theme or switch between light and dark.",
                                if (lang == "ar") "عدد الأذكار يظهر الآن في كل التصنيفات بالعربية والإنجليزية على حدّ سواء." else "Adhkar counts now show on every category, in Arabic and English alike.",
                                if (lang == "ar") "لم تعد الأذكار تعرض لغتين معاً؛ تظهر بلغة التطبيق فقط." else "Adhkar no longer show two languages at once — you see them in the app's language only.",
                                if (lang == "ar") "أزرار المسبحة وشاشات الإعداد تظهر بالكامل على كل أحجام الشاشات." else "Masbaha controls and setup screens now fit every screen size instead of running off the bottom.",
                                if (lang == "ar") "البسملة وأسماء السور تُعرض بخط المصحف الصحيح بدل خط النظام." else "The basmala and surah names now render in proper Mushaf script instead of falling back to the system font."
                            )
                        ),
                        Triple(
                            "v1.0.0",
                            if (lang == "ar") "الإصدار الأول (باسم عماد)" else "Initial Release (as Emad)",
                            listOf(
                                if (lang == "ar") "سجل الصلوات اليومي مع العدّ التنازلي للصلاة القادمة." else "The daily prayer ledger, with a countdown to the next prayer.",
                                if (lang == "ar") "مِصهر القضاء لحساب الصلوات الفائتة وتتبّع سدادها." else "The Qada Forge, for working out missed prayers and clearing them.",
                                if (lang == "ar") "بوصلة القبلة." else "The Qibla compass.",
                                if (lang == "ar") "١٩ سمة لونية بأيقونات مطابقة." else "19 colour themes with matching app icons.",
                                if (lang == "ar") "الإنجازات ومستويات الاستمرارية." else "Achievements and consistency tiers.",
                                if (lang == "ar") "يعمل بالكامل دون إنترنت، وكل شيء يبقى على جهازك." else "Fully offline, with everything kept on your device."
                            )
                        )
                    )

                    updates.forEach { (version, date, points) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.02f))
                                .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = version, color = AppTheme.colors.secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(text = date, color = AppTheme.colors.grayMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            points.forEach { pt ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(text = "• ", color = AppTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(text = pt, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 15.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevUpdatesDialog = false }) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 7. OPEN SOURCE TRANSPARENCY DIALOG
    if (showOpenSourceDialog) {
        EmranAlertDialog(
            onDismissRequest = { showOpenSourceDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == "ar") "الشفافية مفتوحة المصدر" else "OPEN SOURCE PROJECT",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (lang == "ar")
                            "تطبيق عمران مبني بشفافية ومصداقية كاملة. الكود المصدري متاح بالكامل للجميع للتعلم والمساهمة البرمجية والتحقق من النقاء الفني والأمني:"
                        else
                            "Emran is built on complete trust, academic transparency, and zero monetization. The entire codebase is open-source, allowing peer-review, learning, and collaboration:",
                        color = AppTheme.colors.grayMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )

                    // Repository details card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = if (lang == "ar") "مستودع الكود البرمجي" else "CODE REPOSITORY", color = AppTheme.colors.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "GitHub Repository Link", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "https://github.com/SammyNashed/Emran", color = AppTheme.colors.grayMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SammyNashed/Emran"))
                                    try { context.startActivity(intent) } catch(e: Exception) {}
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.secondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (lang == "ar") "تصفّح كود المشروع" else "View Repository on GitHub", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // License Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = if (lang == "ar") "الترخيص البرمجي" else "SOFTWARE LICENSING", color = AppTheme.colors.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "MIT License", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (lang == "ar")
                                    "رخصة MIT تعني الحرية المطلقة. يمكنك نسخ، تعديل، دمج، أو توزيع هذا التطبيق لأي غرض أكاديمي أو ديني بحرية تامة ودون قيود."
                                else
                                    "Licensed under the highly permissive MIT Open Source License. This guarantees maximum academic freedom to copy, study, modify, or adapt Emran.",
                                color = AppTheme.colors.grayMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // Tech Stack acknowledgements
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = if (lang == "ar") "تراخيص المكتبات الخارجية" else "THIRD-PARTY SOFTWARE", color = AppTheme.colors.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (lang == "ar")
                                    "يقف عمران على أكتاف العمالقة. تم التطوير بالاعتماد على: Kotlin, Jetpack Compose, Room SQLite Database, Kotlin Coroutines, Vico chart graphics, Coil Image loading, and Material Design 3 templates."
                                else
                                    "We stand on the shoulders of giants. Emran is developed in complete accordance with modern standards using Kotlin, Jetpack Compose, Room Database, SQLite engine, Vico graphics, and Coil.",
                                color = AppTheme.colors.grayMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // Developer GitHub link button
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SammyNashed"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24292E)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (lang == "ar") "تابع حساب المطور Sammy" else "Follow Sammy Nashed on GitHub", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOpenSourceDialog = false }) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 8. PRIVACY FIRST EXPLANATION DIALOG
    if (showPrivacyFirstDialog) {
        EmranAlertDialog(
            onDismissRequest = { showPrivacyFirstDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == "ar") "الخصوصية المطلقة للبيانات" else "PRIVACY GUARANTEED",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (lang == "ar")
                            "خصوصيتك وعلاقتك بالله وإحصائياتك الإيمانية هي أمور شديدة الخصوصية والحميمية. لذلك، تم تصميم تطبيق عمران وفق أعلى معايير الحماية الرقمية والسيادة الكاملة على البيانات:"
                        else
                            "Your relationship with God, prayer logs, and personal details are deeply intimate. Emran is engineered around full digital sovereignty, keeping you in complete ownership of your data:",
                        color = AppTheme.colors.grayMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )

                    // Core Privacy Grid Items
                    val items = if (lang == "ar") listOf(
                        "✓ لا تتبع" to "التطبيق لا يحتوي على أي برمجيات تتبع لموقعك أو عادات استخدامك على الإطلاق.",
                        "✓ لا تحليلات" to "لا توجد أي برامج تحليلية للتحري عن سلوكك أو تجميع أي تقارير حول استعمالك.",
                        "✓ لا إعلانات" to "تطبيق عمران مجاني بالكامل وخالٍ تماماً من الإعلانات المشتتة أو النوافذ المنبثقة.",
                        "✓ لا حسابات" to "لا داعي لتسجيل بريدك الإلكتروني أو كلمة مرور أو تزويدنا بأي معلومات شخصية للدخول.",
                        "✓ دون إنترنت بالكامل" to "يعمل عمران كلياً على جهازك. لا يتصل بالإنترنت إلا لتنزيل اختياري تبدؤه أنت (نموذج الحفظ وكتب الحديث)، ولا يجمع بياناتك ولا يشاركها ولا يبيعها.",
                        "✓ لا ملف شخصي" to "لا يحفظ عمران اسمك ولا صورتك ولا سجلاً لصلواتك. تبقى إعداداتك وأذكارك الخاصة وعلاماتك المرجعية على هاتفك فقط."
                    ) else listOf(
                        "✓ No Tracking" to "Zero background tracking of your geographic location or software usage patterns.",
                        "✓ No Analytics" to "Zero third-party trackers, crash telemetry, or telemetry logs embedded inside.",
                        "✓ No Advertisements" to "Free from promotional banners, sponsorship deals, or commercial noise.",
                        "✓ No Account Required" to "Start instantly without passwords, email addresses, or phone verification.",
                        "✓ Fully Offline" to "Emran runs entirely on your device. The only time it goes online is an optional download you choose to start (the Hifz model, extra Hadith books). It never collects, shares or sells any data.",
                        "✓ No Profile" to "Emran keeps no name, photo or record of your prayers. Only your settings, your own dhikrs and your bookmarks are stored, and only on your phone."
                    )

                    items.forEach { (titleText, descText) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.02f))
                                .padding(12.dp)
                        ) {
                            Text(text = titleText, color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = descText, color = AppTheme.colors.grayMuted, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyFirstDialog = false }) {
                    Text(if (lang == "ar") "حسناً، أثق بعمران" else "I TRUST EMRAN", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 9b. NEW VERSION AVAILABLE: sends the user to the channel, where the APK is a normal download.
    (updateResult as? UpdateChecker.Result.Available)?.let { found ->
        if (showUpdateDialog) {
            EmranAlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = {
                    Text(
                        text = if (lang == "ar") "يتوفر إصدار جديد ${found.versionName}" else "Version ${found.versionName} is out",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                text = {
                    Column {
                        if (found.notes.isNotBlank()) {
                            Text(found.notes, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 21.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Text(
                            text = if (lang == "ar") "افتح القناة ثم اضغط على ملف التطبيق الأحدث لتثبيته فوق القديم. بياناتك تبقى كما هي."
                            else "Open the channel and tap the newest app file to install it over the old one. Your data stays as it is.",
                            color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showUpdateDialog = false
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.CHANNEL_URL)))
                        } catch (e: Exception) {
                            Toast.makeText(context, if (lang == "ar") "تعذّر فتح تيليجرام" else "Couldn't open Telegram", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text(if (lang == "ar") "فتح القناة" else "OPEN CHANNEL", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text(if (lang == "ar") "لاحقاً" else "LATER", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF131018),
                shape = RoundedCornerShape(24.dp)
            )
        }
    }

    // 10. VERSION INFORMATION & EASTER EGG DEVELOPER DIALOG
    if (showVersionDialog) {
        EmranAlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == "ar") "معلومات إصدار التطبيق" else "APP METADATA",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Tap-able metadata card for Easter Egg!
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                developerTaps++
                                if (developerTaps == 7) {
                                    Toast.makeText(context, "🎉 You are now an official Emran Spiritual Contributor!", Toast.LENGTH_LONG).show()
                                } else if (developerTaps in 1..6) {
                                    val remaining = 7 - developerTaps
                                    Toast.makeText(context, "Tap $remaining more times to unlock developer secrets", Toast.LENGTH_SHORT).show()
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("EMRAN COMPANION", color = AppTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AppTheme.colors.secondary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("v${BuildConfig.VERSION_NAME}", color = AppTheme.colors.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Emran App Core Profile", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "• Version Name: ${BuildConfig.VERSION_NAME} (Release build)", color = AppTheme.colors.grayMuted, fontSize = 11.sp)
                            Text(text = "• Version Code: 200", color = AppTheme.colors.grayMuted, fontSize = 11.sp)
                            Text(text = "• Build Language: Kotlin (Jetpack Compose)", color = AppTheme.colors.grayMuted, fontSize = 11.sp)
                            Text(text = "• Local Storage engine: SQLite Room Database", color = AppTheme.colors.grayMuted, fontSize = 11.sp)
                        }
                    }



                    // Easter egg developer options!
                    if (developerTaps >= 7) {
                        AnimatedVisibility(
                            visible = true,
                            enter = expandVertically() + fadeIn()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E1B18))
                                    .border(1.5.dp, AppTheme.colors.secondary, RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Build, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("DEVELOPER DIAGNOSTIC TOOLS", color = AppTheme.colors.secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                                
                                Text(text = "• Active Theme: $activeThemePreset", color = Color.White, fontSize = 11.sp)
                                Text(text = "• Active App Icon: $activeAppIconPreset", color = Color.White, fontSize = 11.sp)
                                Text(text = "• Active Location: $activeLocationName", color = Color.White, fontSize = 11.sp)
                                Text(text = "• Coordinates: Lat $activeLat / Lon $activeLon", color = Color.White, fontSize = 11.sp)
                                Text(text = "• Active School: $activeSchool", color = Color.White, fontSize = 11.sp)
                                Text(text = "• Active Method: $activeMethod", color = Color.White, fontSize = 11.sp)
                                Text(text = "• Android SDK Level: ${Build.VERSION.SDK_INT}", color = Color.White, fontSize = 11.sp)
                                Text(text = "• Device Model: ${Build.MODEL}", color = Color.White, fontSize = 11.sp)
                                Text(text = "• Local Database File: emran_db", color = Color.White, fontSize = 11.sp)

                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        developerTaps = 0
                                        Toast.makeText(context, "Developer options hidden", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Lock Developer Diagnostic Options", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionDialog = false }) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 11. NESTED PRESETS OR SELECT LOCATION DIALOG (Launches when clicking Location preset inside Calculation Method dialog)
    if (showLocationPopup) {
        EmranAlertDialog(
            onDismissRequest = { showLocationPopup = false },
            title = {
                Text(
                    text = if (lang == "ar") "اختر الموقع الجغرافي" else "SELECT LOCATION",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                val filteredLocations = remember(locationSearchQuery, activeLocationName, activeLat, activeLon, activeZoneId) {
                    val baseList = if (LocationPresets.list.any { it.name == activeLocationName }) {
                        LocationPresets.list
                    } else {
                        listOf(LocationPreset(activeLocationName, activeLat, activeLon, activeZoneId)) + LocationPresets.list
                    }
                    if (locationSearchQuery.isBlank()) {
                        baseList
                    } else {
                        baseList.filter {
                            it.name.contains(locationSearchQuery, ignoreCase = true)
                        }
                    }
                }
                Column {
                    OutlinedTextField(
                        value = locationSearchQuery,
                        onValueChange = { locationSearchQuery = it },
                        placeholder = { Text(if (lang == "ar") "ابحث عن مدينة أو دولة..." else "Search city or country...", color = AppTheme.colors.grayMuted, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search icon",
                                tint = AppTheme.colors.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (locationSearchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { locationSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
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
                            focusedBorderColor = AppTheme.colors.secondary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedContainerColor = Color.White.copy(alpha = 0.02f),
                            unfocusedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("settings_location_search_input")
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    ) {
                        val locationScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(locationScrollState)
                        ) {
                            if (filteredLocations.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (lang == "ar") "لا توجد نتائج مطابقة" else "No matching locations found",
                                        color = AppTheme.colors.grayMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                filteredLocations.forEach { preset ->
                                    val isSelected = activeLocationName == preset.name
                                    val isSelectedBg = if (isSelected) AppTheme.colors.secondary.copy(alpha = 0.12f) else Color.Transparent

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(isSelectedBg)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.setLocation(preset)
                                                showLocationPopup = false
                                            }
                                            .padding(vertical = 12.dp, horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = LocationPresets.getLocalizedName(preset.name, lang),
                                                color = if (isSelected) Color.White else AppTheme.colors.onBackground,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Lat: ${preset.latitude}, Lon: ${preset.longitude}",
                                                color = AppTheme.colors.grayMuted,
                                                fontSize = 11.sp
                                            )
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = AppTheme.colors.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showLocationPopup = false }
                ) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.secondary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 12. NESTED ADHAN SOUND SELECTION DIALOG
    if (showAdhanDialog) {
        var tempAdhanSelection by remember(currentAdhanFromFlow) { mutableStateOf(currentAdhanFromFlow) }
        EmranAlertDialog(
            onDismissRequest = { showAdhanDialog = false },
            title = {
                Text(
                    text = if (lang == "ar") "صوت الأذان والاشعارات" else "ADHAN NOTIFICATION SOUND",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    OnboardingAdhanPage(
                        modifier = Modifier.fillMaxSize(),
                        lang = lang,
                        selectedSound = tempAdhanSelection,
                        onSoundSelected = { tempAdhanSelection = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setDefaultAdhanSound(tempAdhanSelection)
                        showAdhanDialog = false
                        Toast.makeText(context, if (lang == "ar") "تم الحفظ" else "Saved successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (lang == "ar") "حفظ" else "SAVE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdhanDialog = false }) {
                    Text(if (lang == "ar") "إلغاء" else "CANCEL", color = AppTheme.colors.grayMuted)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 13. NESTED CALCULATION METHOD SELECTOR DIALOG
    if (showMethodDialog) {
        EmranAlertDialog(
            onDismissRequest = { showMethodDialog = false },
            title = {
                Text(
                    text = if (lang == "ar") "طريقة الحساب والمنهج" else "CALCULATION METHOD",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    methods.forEach { (key, label) ->
                        val isSelected = activeMethod == key
                        val isSelectedBg = if (isSelected) AppTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(isSelectedBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setCalculationMethod(key)
                                    showMethodDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else AppTheme.colors.onBackground,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMethodDialog = false }) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 14. NESTED JURISTIC SCHOOL SELECTOR DIALOG
    if (showSchoolDialog) {
        EmranAlertDialog(
            onDismissRequest = { showSchoolDialog = false },
            title = {
                Text(
                    text = if (lang == "ar") "المذهب الفقهي لعصر" else "ASR JURISTIC SCHOOL",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    schools.forEach { (key, label) ->
                        val isSelected = activeSchool == key
                        val isSelectedBg = if (isSelected) AppTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(isSelectedBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setAsrSchool(key)
                                    showSchoolDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else AppTheme.colors.onBackground,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSchoolDialog = false }) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 14a. NESTED APPEARANCE (LIGHT / DARK) SELECTOR DIALOG
    if (showThemeModeDialog) {
        EmranAlertDialog(
            onDismissRequest = { showThemeModeDialog = false },
            title = {
                Text(
                    text = if (lang == "ar") "الوضع الليلي والنهاري" else "APPEARANCE",
                    color = AppTheme.colors.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themeModes.forEach { (key, label) ->
                        val isSelected = activeThemeMode == key
                        val isSelectedBg = if (isSelected) AppTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(isSelectedBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setThemeMode(key)
                                    showThemeModeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = AppTheme.colors.onBackground,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (lang == "ar")
                            "تحتفظ كل سِمة بألوانها المميزة في الوضعين."
                        else
                            "Each of the 20 presets keeps its own accent colours in either mode.",
                        color = AppTheme.colors.grayMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeModeDialog = false }) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = AppTheme.colors.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 14b. NESTED HIGH-LATITUDE RULE SELECTOR DIALOG
    if (showHighLatDialog) {
        EmranAlertDialog(
            onDismissRequest = { showHighLatDialog = false },
            title = {
                Text(
                    text = if (lang == "ar") "قاعدة خطوط العرض العليا" else "HIGH-LATITUDE RULE",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (lang == "ar")
                            "في خطوط العرض العالية قد لا تصل الشمس إلى زاوية الفجر أو العشاء، فلا يكون لهما وقت فلكي. اختيار القاعدة مسألة فقهية لا حسابية."
                        else
                            "At high latitudes the sun may never reach the Fajr or Isha angle, so those prayers have no astronomical time. Which rule to apply is a fiqh question, not an astronomical one.",
                        color = AppTheme.colors.grayMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    highLatitudeRules.forEach { (key, label) ->
                        val isSelected = activeHighLatRule == key
                        val isSelectedBg = if (isSelected) AppTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(isSelectedBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setHighLatitudeRule(key)
                                    showHighLatDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else AppTheme.colors.onBackground,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showHighLatDialog = false }) {
                    Text(if (lang == "ar") "إغلاق" else "CLOSE", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131018),
            shape = RoundedCornerShape(24.dp)
        )
    }

    // 15. NESTED CLOCK OFFSET CALIBRATOR POPUP DIALOG
    if (showClockDialogFor != null) {
        val target = showClockDialogFor!!
        ClockPopupDialog(
            label = target.second,
            baseDateTime = target.third,
            currentOffset = offsets[target.first] ?: 0,
            onDismiss = { showClockDialogFor = null },
            onSave = { newOffset ->
                viewModel.updateOffset(target.first, newOffset)
                showClockDialogFor = null
            }
        )
    }
    }
}

// ==========================================
// ============ REUSABLE COMPONENTS ==========
// ==========================================

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = AppTheme.colors.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp, start = 4.dp)
    )
}

/** One setting, as a glass card in the same family as the Adhkar and Hadith screens. */
@Composable
fun PremiumSettingsRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = AppTheme.colors.secondary,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassCard(RoundedCornerShape(18.dp), iconTint.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(iconTint.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (trailingContent != null) {
            trailingContent()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** A status card in the notification-reliability dialog: what, why, whether it is on, and how to fix it. */
@Composable
private fun ReliabilityCard(
    title: String,
    detail: String,
    ok: Boolean,
    okLabel: String,
    badLabel: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val tone = if (ok) Color(0xFF4CAF50) else Color(0xFFFFB74D)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = 0.07f)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, tone.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tone.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = (if (ok) "✓ " else "! ") + (if (ok) okLabel else badLabel),
                        color = tone,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = detail, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, lineHeight = 16.sp)
            if (!ok) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = tone, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ClockPopupDialog(
    label: String,
    baseDateTime: java.time.LocalDateTime,
    currentOffset: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val lang = LocalAppLanguage.current
    val haptic = LocalHapticFeedback.current
    val baseHour = baseDateTime.hour
    val baseMinute = baseDateTime.minute
    
    val currentCalibrated = baseDateTime.plusMinutes(currentOffset.toLong())
    
    var hourText by remember { mutableStateOf(String.format("%02d", currentCalibrated.hour)) }
    var minuteText by remember { mutableStateOf(String.format("%02d", currentCalibrated.minute)) }
    
    val parsedHour = hourText.toIntOrNull()
    val parsedMinute = minuteText.toIntOrNull()
    
    val isValid = parsedHour != null && parsedHour in 0..23 && parsedMinute != null && parsedMinute in 0..59
    
    val calculatedOffset = if (isValid) {
        val baseMin = baseHour * 60 + baseMinute
        val targetMin = parsedHour!! * 60 + parsedMinute!!
        targetMin - baseMin
    } else {
        0
    }
    
    EmranAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surface,
        title = {
            Column {
                Text(
                    text = if (lang == "ar") "معايرة صلاة $label" else "CALIBRATE ${label.uppercase()}",
                    color = AppTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = if (lang == "ar") "تعديل الوقت الدقيق" else "Adjust Exact Time",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (lang == "ar") "توقيت الحساب القياسي الأساسي: ${String.format("%02d:%02d", baseHour, baseMinute)}" else "Base standard calculation: ${String.format("%02d:%02d", baseHour, baseMinute)}",
                    color = AppTheme.colors.grayMuted,
                    fontSize = 12.sp
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Hour inputs
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val current = hourText.toIntOrNull() ?: 0
                                val next = (current + 1) % 24
                                hourText = String.format("%02d", next)
                            },
                            modifier = Modifier.size(36.dp).testTag("hour_plus")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increment Hour",
                                tint = AppTheme.colors.primary
                            )
                        }
                        
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { input ->
                                if (input.length <= 2 && input.all { it.isDigit() }) {
                                    hourText = input
                                }
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AppTheme.colors.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = Color.White.copy(alpha = 0.02f),
                                unfocusedContainerColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .width(80.dp)
                                .testTag("dialog_hour_input")
                        )
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val current = hourText.toIntOrNull() ?: 0
                                val prev = (current - 1 + 24) % 24
                                hourText = String.format("%02d", prev)
                            },
                            modifier = Modifier.size(36.dp).testTag("hour_minus")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrement Hour",
                                tint = AppTheme.colors.primary
                            )
                        }
                        
                        Text(if (lang == "ar") "ساعة" else "HOUR", color = AppTheme.colors.grayMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Text(
                        text = ":",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    )
                    
                    // Minute inputs
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val current = minuteText.toIntOrNull() ?: 0
                                val next = (current + 1) % 60
                                minuteText = String.format("%02d", next)
                            },
                            modifier = Modifier.size(36.dp).testTag("minute_plus")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increment Minute",
                                tint = AppTheme.colors.primary
                            )
                        }
                        
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { input ->
                                if (input.length <= 2 && input.all { it.isDigit() }) {
                                    minuteText = input
                                }
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AppTheme.colors.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = Color.White.copy(alpha = 0.02f),
                                unfocusedContainerColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .width(80.dp)
                                .testTag("dialog_minute_input")
                        )
                        
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val current = minuteText.toIntOrNull() ?: 0
                                val prev = (current - 1 + 60) % 60
                                minuteText = String.format("%02d", prev)
                            },
                            modifier = Modifier.size(36.dp).testTag("minute_minus")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrement Minute",
                                tint = AppTheme.colors.primary
                            )
                        }
                        
                        Text(if (lang == "ar") "دقيقة" else "MINUTE", color = AppTheme.colors.grayMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                if (isValid) {
                    val shiftLabel = if (calculatedOffset >= 0) "+$calculatedOffset min" else "$calculatedOffset min"
                    val shiftColor = if (calculatedOffset == 0) AppTheme.colors.primary else AppTheme.colors.secondary
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(shiftColor.copy(alpha = 0.05f))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (lang == "ar") "الإزاحة الناتجة: $shiftLabel دقيقة" else "Resulting shift: $shiftLabel",
                            color = shiftColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Text(
                        text = if (lang == "ar") "الوقت غير صحيح! القيمة من 00-23 ومن 00-59" else "Invalid Time! Must be 00-23 and 00-59",
                        color = AppTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        onSave(calculatedOffset)
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.primary,
                    disabledContainerColor = AppTheme.colors.surface,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("dialog_save_btn")
            ) {
                Text(if (lang == "ar") "حفظ" else "SAVE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_btn")
            ) {
                Text(if (lang == "ar") "إلغاء" else "CANCEL", color = AppTheme.colors.grayMuted)
            }
        }
    )
}

@Composable
fun LinkedInIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("linkedin_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_linkedin),
            contentDescription = "LinkedIn",
            tint = Color.Unspecified,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun GitHubIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("github_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_github),
            contentDescription = "GitHub",
            tint = Color.Unspecified,
            modifier = Modifier.fillMaxSize()
        )
    }
}
