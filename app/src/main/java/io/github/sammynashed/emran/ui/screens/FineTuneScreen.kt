package io.github.sammynashed.emran.ui.screens

import io.github.sammynashed.emran.ui.design.GlassDialog

import android.content.Intent
import android.net.Uri
import io.github.sammynashed.emran.BuildConfig
import io.github.sammynashed.emran.data.update.UpdateChecker
import kotlinx.coroutines.launch

import android.widget.Toast
import androidx.activity.compose.BackHandler

import androidx.compose.animation.*
import androidx.compose.foundation.background

import io.github.sammynashed.emran.service.rememberNotificationHealth

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.MenuBook

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import io.github.sammynashed.emran.data.LocationPresets
import io.github.sammynashed.emran.data.PrayerTimeCalculator
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.design.GlassDivider
import io.github.sammynashed.emran.ui.design.ChoiceDialog
import io.github.sammynashed.emran.ui.design.GlassGroup
import io.github.sammynashed.emran.ui.design.RichRow
import io.github.sammynashed.emran.ui.design.GlassSectionLabel
import io.github.sammynashed.emran.ui.design.GlassSegmented
import io.github.sammynashed.emran.ui.design.GlassTile
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.SettingsRow
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.design.glassEntrance

import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.net.URLEncoder

@Composable
fun FineTuneScreen(
    viewModel: PrayerViewModel,
    modifier: Modifier = Modifier,
    onOpenAcknowledgements: () -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val offsets by viewModel.offsets.collectAsState()
    val activeThemePreset by viewModel.themePreset.collectAsState()
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
    var showAdhanDialog by remember { mutableStateOf(false) }
    var showOffsetsGroup by remember { mutableStateOf(false) }
    var locationSearchQuery by remember { mutableStateOf("") }
    
    // Immersive dialog states
    var showNotificationPrecisionDialog by remember { mutableStateOf(false) }
    var showCalculationSettingsDialog by remember { mutableStateOf(false) }
    var showAboutEmranDialog by remember { mutableStateOf(false) }
    var showAboutDeveloperDialog by remember { mutableStateOf(false) }
    var showOpenSourceDialog by remember { mutableStateOf(false) }
    var showPrivacyFirstDialog by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    // Update check: null = never asked this visit; a tap runs it once and shows the answer.
    var updateChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showUpdateUnreachable by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showUpdateChoice by remember { mutableStateOf(false) }
    val pickUpdateFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) io.github.sammynashed.emran.data.update.PatchUpdate.incoming.value = uri }

    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var reminderDialogFor by remember { mutableStateOf<io.github.sammynashed.emran.service.AdhkarReminders.Kind?>(null) }
    var reminderVersion by remember { mutableStateOf(0) }
    var showClockDialogFor by remember { mutableStateOf<Triple<String, String, java.time.LocalDateTime>?>(null) }

    BackHandler(enabled = true) {
        if (reminderDialogFor != null) {
            reminderDialogFor = null
        } else if (showClockDialogFor != null) {
            showClockDialogFor = null
        } else if (showMethodDialog) {
            showMethodDialog = false
        } else if (showSchoolDialog) {
            showSchoolDialog = false
        } else if (showHighLatDialog) {
            showHighLatDialog = false
        } else if (showAdhanDialog) {
            showAdhanDialog = false
        } else if (showLocationPopup) {
            showLocationPopup = false
        } else if (showNotificationPrecisionDialog) {
            showNotificationPrecisionDialog = false
        } else if (showCalculationSettingsDialog) {
            showCalculationSettingsDialog = false
        } else if (showAboutEmranDialog) {
            showAboutEmranDialog = false
        } else if (showAboutDeveloperDialog) {
            showAboutDeveloperDialog = false
        } else if (showOpenSourceDialog) {
            showOpenSourceDialog = false
        } else if (showPrivacyFirstDialog) {
            showPrivacyFirstDialog = false
        } else if (showUpdateChoice) {
            showUpdateChoice = false
        } else if (showUpdateDialog) {
            showUpdateDialog = false
        } else if (showUpdateUnreachable) {
            showUpdateUnreachable = false
        } else if (showVersionDialog) {
            showVersionDialog = false
        } else if (showOffsetsGroup) {
            showOffsetsGroup = false
        } else {
            showExitConfirmDialog = true
        }
    }

    if (showExitConfirmDialog) {
        ExitDialog(
            ar = lang == "ar",
            onExit = { showExitConfirmDialog = false; (context as? android.app.Activity)?.finish() },
            onDismiss = { showExitConfirmDialog = false },
        )
    }

    val baseTimes = remember(activeLat, activeLon, activeZoneId, activeMethod, activeSchool, activeHighLatRule) {
        val methodEnum = try { PrayerTimeCalculator.CalculationMethod.valueOf(activeMethod) } catch(e: Exception) { PrayerTimeCalculator.CalculationMethod.MWL }
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

    // The page: everything that matters is on one screen, nothing to hunt for. Frequent things
    // (the adhan, the language) are one tap or fewer; the rest sit in four short groups.
    val sky = LocalSky.current
    val ar = lang == "ar"
    var showAboutMenu by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                // Just clear of the dock: with this, the whole page fits a normal phone with no scroll
                // (very small screens still scroll).
                .padding(bottom = 104.dp),
        ) {
            Text(
                text = if (ar) "الإعدادات" else "Settings",
                color = sky.ink,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = Space.sm, start = 4.dp),
            )

            // --- Prayer ---
            val adhanLabel = when (currentAdhanFromFlow) {
                "AL_AQSA" -> if (ar) "المسجد الأقصى" else "Al-Aqsa"
                "MAKKAH" -> if (ar) "مكة المكرمة" else "Makkah"
                "MADINAH" -> if (ar) "المدينة المنورة" else "Madinah"
                "default" -> if (ar) "صوت النظام" else "System sound"
                "ahmad_alnafees" -> if (ar) "أحمد النفيس" else "Ahmad Alnafys"
                "mishary_alafasy" -> if (ar) "مشاري العفاسي" else "Mishary Alafasy"
                "saad_alghamdi" -> if (ar) "سعد الغامدي" else "Saad Alghamdy"
                else -> adhanDisplayName(currentAdhanFromFlow, ar)
            }
            val methodLabel = methods.find { it.first == activeMethod }?.second ?: activeMethod
            GlassSectionLabel(if (ar) "الصلاة" else "PRAYER")
            GlassGroup(Modifier.glassEntrance(0)) {
                SettingsRow(
                    icon = Icons.Default.Notifications,
                    minHeight = 50.dp,
                    title = if (ar) "صوت الأذان" else "Adhan sound",
                    value = adhanLabel,
                    onClick = { showAdhanDialog = true },
                )
                GlassDivider()
                SettingsRow(
                    icon = Icons.Default.Explore,
                    minHeight = 50.dp,
                    title = if (ar) "طريقة الحساب" else "Calculation",
                    value = LocationPresets.getLocalizedName(activeLocationName, lang),
                    onClick = { showCalculationSettingsDialog = true },
                )
                GlassDivider()
                SettingsRow(
                    icon = Icons.Default.Alarm,
                    minHeight = 50.dp,
                    title = if (ar) "وصول التنبيه" else "Reliable alerts",
                    onClick = { showNotificationPrecisionDialog = true },
                    trailing = {
                        val ok = health.allGood
                        val dot = if (ok) Color(0xFF5BD58A) else Color(0xFFFFB74D)
                        Box(Modifier.size(9.dp).background(dot, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (ok) (if (ar) "جاهز" else "All set")
                            else (if (ar) "يحتاج انتباهك" else "Needs attention"),
                            color = sky.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null, tint = sky.inkSoft, modifier = Modifier.size(22.dp),
                        )
                    },
                )
            }

            // --- Reminders: three tiles side by side (name and time), so Settings fits on one screen ---
            Spacer(Modifier.height(Space.sm))
            GlassSectionLabel(if (ar) "التذكيرات" else "REMINDERS")
            Row(Modifier.fillMaxWidth().glassEntrance(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                io.github.sammynashed.emran.service.AdhkarReminders.Kind.values().forEach { kind ->
                    val on = reminderVersion.let { io.github.sammynashed.emran.service.AdhkarReminders.isOn(context, kind) }
                    val minute = reminderVersion.let { io.github.sammynashed.emran.service.AdhkarReminders.minute(context, kind) }
                    GlassTile(
                        icon = when (kind) {
                            io.github.sammynashed.emran.service.AdhkarReminders.Kind.MORNING -> Icons.Outlined.WbSunny
                            io.github.sammynashed.emran.service.AdhkarReminders.Kind.EVENING -> Icons.Outlined.NightsStay
                            io.github.sammynashed.emran.service.AdhkarReminders.Kind.KAHF -> Icons.AutoMirrored.Outlined.MenuBook
                        },
                        // Short in English so each fits one line; the section already says these are reminders.
                        label = when (kind) {
                            io.github.sammynashed.emran.service.AdhkarReminders.Kind.MORNING -> if (ar) "أذكار الصباح" else "Morning"
                            io.github.sammynashed.emran.service.AdhkarReminders.Kind.EVENING -> if (ar) "أذكار المساء" else "Evening"
                            io.github.sammynashed.emran.service.AdhkarReminders.Kind.KAHF -> if (ar) "سورة الكهف" else "Al-Kahf"
                        },
                        value = if (on) {
                            val t = io.github.sammynashed.emran.data.TimeFormat.full(java.time.LocalTime.of(minute / 60, minute % 60), ar)
                            if (kind.fridayOnly) (if (ar) "الجمعة $t" else "Fri $t") else t
                        } else (if (ar) "متوقف" else "Off"),
                        onClick = { reminderDialogFor = kind },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // --- App ---
            Spacer(Modifier.height(Space.sm))
            GlassSectionLabel(if (ar) "التطبيق" else "APP")
            GlassGroup(Modifier.glassEntrance(1)) {
                SettingsRow(
                    icon = Icons.Default.Language,
                    minHeight = 50.dp,
                    title = if (ar) "اللغة" else "Language",
                    onClick = null,
                    trailing = {
                        GlassSegmented(
                            options = listOf("en" to "English", "ar" to "العربية"),
                            selectedKey = lang,
                            onSelect = { viewModel.setCurrentLanguage(it) },
                            modifier = Modifier.width(172.dp),
                        )
                    },
                )
                GlassDivider()
                SettingsRow(
                    icon = Icons.Outlined.Schedule,
                    minHeight = 50.dp,
                    title = if (ar) "صيغة الوقت" else "Clock",
                    onClick = null,
                    trailing = {
                        GlassSegmented(
                            options = listOf("12" to "12", "24" to "24"),
                            selectedKey = if (io.github.sammynashed.emran.data.TimeFormat.use24) "24" else "12",
                            onSelect = {
                                io.github.sammynashed.emran.data.TimeFormat.set(context, it == "24")
                                updateScope.launch { runCatching { io.github.sammynashed.emran.glance.WidgetUpdater.updateAllWidgets(context.applicationContext) } }
                            },
                            modifier = Modifier.width(132.dp),
                        )
                    },
                )
                GlassDivider()
                SettingsRow(
                    icon = Icons.Outlined.WbTwilight,
                    minHeight = 50.dp,
                    title = if (ar) "الخلفية" else "Background",
                    onClick = null,
                    trailing = {
                        GlassSegmented(
                            // The control lays out left to right; in Arabic the first choice belongs on the right.
                            options = listOf("live" to (if (ar) "حيّة" else "Live"), "calm" to (if (ar) "هادئة" else "Calm"))
                                .let { if (ar) it.reversed() else it },
                            selectedKey = if (io.github.sammynashed.emran.ui.design.SkySettings.calm) "calm" else "live",
                            onSelect = { io.github.sammynashed.emran.ui.design.SkySettings.set(context, it == "calm") },
                            modifier = Modifier.width(172.dp),
                        )
                    },
                )
            }

            // --- Help and About: one quiet group, the same rows as everything above ---
            Spacer(Modifier.height(Space.sm))
            GlassSectionLabel(if (ar) "المساعدة وحول التطبيق" else "HELP & ABOUT")
            val waMessage = { text: String ->
                try {
                    val url = "https://wa.me/963986157471?text=" + URLEncoder.encode(text, "UTF-8")
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(context, if (ar) "فشل فتح واتساب" else "Failed to open WhatsApp", Toast.LENGTH_SHORT).show()
                }
            }
            // Short, and only once there is an answer: the version itself lives on About.
            val updateValue = when {
                updateResult is UpdateChecker.Result.Available -> if (ar) "جديد!" else "New!"
                updateResult is UpdateChecker.Result.UpToDate -> if (ar) "محدّث" else "Up to date"
                updateResult is UpdateChecker.Result.Offline -> if (ar) "لا اتصال" else "Offline"
                updateResult is UpdateChecker.Result.Failed -> if (ar) "تعذّر" else "Failed"
                else -> null
            }
            // Four small tiles in one row: everything here is a single tap, and the page stays one screen.
            Row(Modifier.fillMaxWidth().glassEntrance(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassTile(
                    icon = Icons.Outlined.BugReport,
                    label = if (ar) "إبلاغ" else "Report",
                    onClick = { waMessage(if (ar) "السلام عليكم، واجهتني مشكلة في تطبيق عمران: " else "Assalamu Alaikum Sammy, I found an issue in Emran app: ") },
                    modifier = Modifier.weight(1f),
                )
                GlassTile(
                    icon = Icons.Outlined.Lightbulb,
                    label = if (ar) "اقتراح" else "Suggest",
                    onClick = { waMessage(if (ar) "السلام عليكم، أود اقتراح ميزة لتطبيق عمران: " else "Assalamu Alaikum Sammy, I would like to suggest a feature for Emran: ") },
                    modifier = Modifier.weight(1f),
                )
                GlassTile(
                    icon = Icons.Outlined.SystemUpdate,
                    label = when {
                        updateChecking -> if (ar) "تحقق…" else "Checking…"
                        updateValue != null -> updateValue
                        else -> if (ar) "تحديث" else "Updates"
                    },
                    // Two ways to update: a small file from the channel (works on any network),
                    // or asking online whether there is a newer version.
                    onClick = { showUpdateChoice = true },
                    modifier = Modifier.weight(1f),
                )
                GlassTile(
                    icon = Icons.Outlined.Info,
                    label = if (ar) "حول" else "About",
                    onClick = { showAboutMenu = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }

    // ==========================================
    // ============ DIALOGS & OVERLAYS ==========
    // ==========================================

    // About: a hero (mark, name, version) and then rows that each say what they open.
    if (showAboutMenu) {
        GlassDialog(
            onDismiss = { showAboutMenu = false },
            title = if (ar) "حول عِمران" else "About",
            dismissLabel = if (ar) "إغلاق" else "Close",
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                io.github.sammynashed.emran.ui.design.EmranMark(56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(if (ar) "عِمران" else "Emran", color = sky.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(sky.ink.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 3.dp),
                        ) { Text("v${BuildConfig.VERSION_NAME}", color = sky.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                    }
                    Text(if (ar) "الصلاة عِمران الروح" else "The prosperity of the soul", color = sky.inkSoft, fontSize = 13.5.sp)
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(sky.ink.copy(alpha = 0.06f)),
            ) {
                data class Item(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val sub: String, val open: () -> Unit)
                listOf(
                    Item(Icons.Default.Info, if (ar) "عن التطبيق" else "About Emran",
                        if (ar) "ما هو عِمران وما فيه" else "What Emran is and what it does") { showAboutEmranDialog = true },
                    Item(Icons.Default.FavoriteBorder, if (ar) "شكرًا للمختبرين" else "Thanks to our testers",
                        if (ar) "مختبرونا الأوائل وآراؤهم" else "Our early testers and what they said") { onOpenAcknowledgements() },
                    Item(Icons.Default.Person, if (ar) "المطوّر" else "Developer",
                        if (ar) "من صنع التطبيق" else "Who made it") { showAboutDeveloperDialog = true },
                    Item(Icons.Default.Code, if (ar) "مفتوح المصدر" else "Open source",
                        if (ar) "الرخصة والمكتبات والخطوط" else "Licence, libraries and fonts") { showOpenSourceDialog = true },
                    Item(Icons.Default.Security, if (ar) "الخصوصية" else "Privacy",
                        if (ar) "لا تتبّع، ولا حساب، ودون إنترنت" else "No tracking, no account, offline") { showPrivacyFirstDialog = true },
                    Item(Icons.Default.PhoneAndroid, if (ar) "الإصدار" else "Version",
                        if (ar) "رقم الإصدار والبناء" else "Version and build number") { showVersionDialog = true },
                ).forEachIndexed { i, it ->
                    if (i > 0) GlassDivider()
                    RichRow(
                        icon = it.icon, title = it.title, subtitle = it.sub,
                        onClick = { showAboutMenu = false; it.open() },
                    )
                }
            }
        }
    }

    // ---- Dialogs in the glass style (see SettingsDialogs.kt) ----
    if (showNotificationPrecisionDialog) {
        ReliabilityDialog(ar = ar, health = health, onDismiss = { showNotificationPrecisionDialog = false })
    }

    if (showCalculationSettingsDialog) {
        CalculationDialog(
            ar = ar,
            locationLabel = LocationPresets.getLocalizedName(activeLocationName, lang),
            methodLabel = methods.find { it.first == activeMethod }?.second ?: activeMethod,
            schoolLabel = schools.find { it.first == activeSchool }?.second ?: activeSchool,
            highLatLabel = if (kotlin.math.abs(activeLat) > 48.0)
                highLatitudeRules.find { it.first == activeHighLatRule }?.second ?: activeHighLatRule else null,
            hasOffsets = offsets.values.any { it != 0 },
            onLocation = { showLocationPopup = true },
            onMethod = { showMethodDialog = true },
            onSchool = { showSchoolDialog = true },
            onHighLat = { showHighLatDialog = true },
            onOffsets = { showOffsetsGroup = true },
            onDismiss = { showCalculationSettingsDialog = false },
        )
    }

    if (showOffsetsGroup) {
        OffsetsDialog(
            ar = ar,
            rows = adjustables,
            offsets = offsets,
            onPick = { showClockDialogFor = it },
            onDismiss = { showOffsetsGroup = false },
        )
    }

    if (showAboutEmranDialog) AboutEmranDialog(ar = ar, onDismiss = { showAboutEmranDialog = false })
    if (showAboutDeveloperDialog) AboutDeveloperDialog(ar = ar, onDismiss = { showAboutDeveloperDialog = false })
    if (showOpenSourceDialog) OpenSourceDialog(ar = ar, onDismiss = { showOpenSourceDialog = false })
    if (showPrivacyFirstDialog) PrivacyDialog(ar = ar, onDismiss = { showPrivacyFirstDialog = false })
    (updateResult as? UpdateChecker.Result.Available)?.let { found ->
        if (showUpdateDialog) {
            UpdateDialog(
                ar = ar, versionName = found.versionName, notes = found.notes,
                channelUrl = UpdateChecker.CHANNEL_URL, onDismiss = { showUpdateDialog = false },
            )
        }
    }
    if (showUpdateChoice) {
        GlassDialog(
            onDismiss = { showUpdateChoice = false },
            title = if (ar) "تحديث عِمران" else "Update Emran",
            subtitle = (if (ar) "الإصدار الحالي " else "You have ") + "v${BuildConfig.VERSION_NAME}",
            dismissLabel = if (ar) "إغلاق" else "Close",
        ) {
            Text(
                if (ar) "التحديثات تُنشر في قناة عِمران على تيليجرام. نزّل ملف التحديث الصغير (‎.emran‎) ثم افتحه بعِمران، أو اختره من هنا."
                else "Updates are posted on the Emran Telegram channel. Download the small update file (.emran) and open it with Emran, or pick it here.",
                color = sky.inkSoft, fontSize = 14.sp, lineHeight = 21.sp,
            )
            Spacer(Modifier.height(14.dp))
            io.github.sammynashed.emran.ui.design.GlassButton(
                label = if (ar) "تثبيت ملف تحديث" else "Install an update file",
                onClick = { showUpdateChoice = false; pickUpdateFile.launch(arrayOf("*/*")) },
                filled = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            io.github.sammynashed.emran.ui.design.GlassButton(
                label = if (ar) "افتح القناة" else "Open the channel",
                onClick = { showUpdateChoice = false; io.github.sammynashed.emran.data.update.UpdateChecker.openChannel(context) },
                filled = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            io.github.sammynashed.emran.ui.design.GlassButton(
                label = if (updateChecking) (if (ar) "جارٍ التحقق…" else "Checking…") else (if (ar) "هل يوجد إصدار أحدث؟" else "Is there a newer version?"),
                onClick = {
                    if (updateResult is UpdateChecker.Result.Available) {
                        showUpdateChoice = false; showUpdateDialog = true
                    } else if (!updateChecking) {
                        updateChecking = true
                        updateScope.launch {
                            val r = UpdateChecker.check(context, ar)
                            updateResult = r
                            updateChecking = false
                            showUpdateChoice = false
                            if (r is UpdateChecker.Result.Available) showUpdateDialog = true
                            if (r is UpdateChecker.Result.Offline || r is UpdateChecker.Result.Failed) showUpdateUnreachable = true
                            if (r is UpdateChecker.Result.UpToDate) Toast.makeText(context, if (ar) "أنت على آخر إصدار" else "You're up to date", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                filled = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (showUpdateUnreachable) UpdateUnreachableDialog(ar = ar, onDismiss = { showUpdateUnreachable = false })
    if (showVersionDialog) {
        VersionDialog(
            ar = ar, method = activeMethod, school = activeSchool, location = activeLocationName,
            lat = activeLat, lon = activeLon, onDismiss = { showVersionDialog = false },
        )
    }

    if (showLocationPopup) {
        LocationDialog(
            ar = ar, lang = lang,
            activeName = activeLocationName, activeLat = activeLat, activeLon = activeLon, activeZone = activeZoneId,
            onPick = { viewModel.setLocation(it); showLocationPopup = false },
            onDismiss = { showLocationPopup = false },
        )
    }

    if (showAdhanDialog) {
        AdhanSoundDialog(
            ar = ar,
            current = currentAdhanFromFlow,
            onSave = {
                viewModel.setDefaultAdhanSound(it)
                showAdhanDialog = false
                Toast.makeText(context, if (ar) "تم الحفظ" else "Saved", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAdhanDialog = false },
        )
    }

    if (showMethodDialog) {
        ChoiceDialog(
            title = if (ar) "طريقة الحساب" else "Calculation method",
            options = methods, selectedKey = activeMethod,
            onPick = { viewModel.setCalculationMethod(it); showMethodDialog = false },
            onDismiss = { showMethodDialog = false },
        )
    }

    if (showSchoolDialog) {
        ChoiceDialog(
            title = if (ar) "وقت العصر" else "Asr time",
            options = schools, selectedKey = activeSchool,
            onPick = { viewModel.setAsrSchool(it); showSchoolDialog = false },
            onDismiss = { showSchoolDialog = false },
        )
    }

    if (showHighLatDialog) {
        ChoiceDialog(
            title = if (ar) "خطوط العرض العليا" else "High latitude",
            subtitle = if (ar) "للمناطق التي قد لا تبلغ الشمس فيها زاوية الفجر أو العشاء."
            else "For places where the sun may never reach the Fajr or Isha angle.",
            options = highLatitudeRules, selectedKey = activeHighLatRule,
            onPick = { viewModel.setHighLatitudeRule(it); showHighLatDialog = false },
            onDismiss = { showHighLatDialog = false },
        )
    }

    showClockDialogFor?.let { target ->
        ClockPopupDialog(
            label = target.second,
            baseDateTime = target.third,
            currentOffset = offsets[target.first] ?: 0,
            onDismiss = { showClockDialogFor = null },
            onSave = { newOffset ->
                viewModel.updateOffset(target.first, newOffset)
                showClockDialogFor = null
            },
        )
    }

    reminderDialogFor?.let { kind ->
        AdhkarReminderDialog(
            title = kind.title(ar),
            subtitle = if (!kind.fridayOnly) null
            else if (ar) "كل جمعة في هذا الوقت، بحديث فضل سورة الكهف. الضغط على الإشعار يفتح السورة."
            else "Every Friday at this time, with the hadith on reading al-Kahf. Tapping it opens the surah.",
            repeatLabel = if (!kind.fridayOnly) null else if (ar) "كل جمعة" else "Every Friday",
            initialOn = io.github.sammynashed.emran.service.AdhkarReminders.isOn(context, kind),
            initialMinute = io.github.sammynashed.emran.service.AdhkarReminders.minute(context, kind),
            defaultMinute = kind.defaultMinute,
            onDismiss = { reminderDialogFor = null },
            onSave = { on, minute ->
                io.github.sammynashed.emran.service.AdhkarReminders.set(context, kind, on, minute)
                reminderVersion++
                reminderDialogFor = null
            },
        )
    }
    }
}

// ==========================================
// ============ REUSABLE COMPONENTS ==========
// ==========================================

