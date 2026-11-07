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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.CalendarToday
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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
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
import androidx.compose.animation.core.tween
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
    onOpenAcknowledgements: () -> Unit = {},
    onOpenAdhanBehavior: () -> Unit = {},
    onOpenCalculation: () -> Unit = {},
    onOpenReliableAlerts: () -> Unit = {},
    onOpenFloatingDhikr: () -> Unit = {},
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
    
    // Immersive dialog states
    var showAboutMenu by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
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
    // Which card's page is open inside Settings (null = the hub).
    var settingsPage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) {
        if (reminderDialogFor != null) {
            reminderDialogFor = null
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
        } else if (showAboutMenu) {
            showAboutMenu = false
        } else if (settingsPage != null) {
            settingsPage = null
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

    val themeModes = remember(lang) {
        listOf(
            "DARK" to if (lang == "ar") "داكن" else "Dark",
            "LIGHT" to if (lang == "ar") "فاتح" else "Light",
            "SYSTEM" to if (lang == "ar") "حسب النظام" else "Follow system"
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
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Settings is a hub that never scrolls: a fixed grid of cards, each showing where it stands.
        // New features go INSIDE a card's page, never as new rows here, so this screen always fits.
        // (Room for 8 cards; the grid shrinks its cards rather than ever scrolling.)
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
        val rKinds = io.github.sammynashed.emran.service.AdhkarReminders.Kind.values()
        val fd = io.github.sammynashed.emran.service.FloatingDhikr
        val remindersOn = reminderVersion.let { rKinds.count { io.github.sammynashed.emran.service.AdhkarReminders.isOn(context, it) } } +
            (if (fd.isOn(context)) 1 else 0)
        val updateValue = when {
            updateResult is UpdateChecker.Result.Available -> if (ar) "جديد!" else "New!"
            updateResult is UpdateChecker.Result.UpToDate -> if (ar) "محدّث" else "Up to date"
            updateResult is UpdateChecker.Result.Offline -> if (ar) "لا اتصال" else "Offline"
            updateResult is UpdateChecker.Result.Failed -> if (ar) "تعذّر" else "Failed"
            else -> null
        }
        val waMessage = { text: String ->
            try {
                val url = io.github.sammynashed.emran.data.SupportContact.whatsappUrl(text)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(context, if (ar) "فشل فتح واتساب" else "Failed to open WhatsApp", Toast.LENGTH_SHORT).show()
            }
        }

        AnimatedContent(
            targetState = settingsPage,
            transitionSpec = {
                val inward = targetState != null
                val dir = (if (inward) 1 else -1) * (if (ar) -1 else 1)
                (slideInHorizontally(tween(320)) { w -> dir * w / 5 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(320)) { w -> -dir * w / 8 } + fadeOut(tween(180)))
            },
            label = "settingsPage",
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                null -> Column(
                    Modifier.fillMaxSize().padding(horizontal = Space.screen).padding(bottom = 104.dp),
                ) {
                    Text(
                        text = if (ar) "الإعدادات" else "Settings",
                        color = sky.ink,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp, bottom = Space.sm, start = 4.dp),
                    )
                    // The hub: two quiet groups of rows, one row per area, each saying where it stands.
                    // Rule: at most 3 groups / 9 rows here. A new feature goes INSIDE an area's page.
                    GlassSectionLabel(if (ar) "الصلاة" else "PRAYER")
                    GlassGroup(Modifier.glassEntrance(0)) {
                        SettingsRow(
                            icon = Icons.Default.Notifications,
                            title = if (ar) "الأذان" else "Adhan",
                            value = adhanLabel,
                            onClick = onOpenAdhanBehavior,
                        )
                        GlassDivider()
                        SettingsRow(
                            icon = Icons.Default.Explore,
                            title = if (ar) "المواقيت" else "Prayer times",
                            value = LocationPresets.getLocalizedName(activeLocationName, lang),
                            onClick = onOpenCalculation,
                        )
                        GlassDivider()
                        SettingsRow(
                            icon = Icons.Default.Alarm,
                            title = if (ar) "وصول التنبيه" else "Reliable alerts",
                            onClick = onOpenReliableAlerts,
                            trailing = {
                                val ok = health.allGood
                                Box(Modifier.size(8.dp).background(if (ok) Color(0xFF5BD58A) else Color(0xFFFFB74D), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (ok) (if (ar) "جاهز" else "All set") else (if (ar) "يحتاج انتباهك" else "Needs attention"),
                                    color = sky.inkSoft, fontSize = 14.sp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = sky.inkSoft, modifier = Modifier.size(22.dp))
                            },
                        )
                    }

                    Spacer(Modifier.height(Space.md))
                    GlassSectionLabel(if (ar) "عِمران" else "EMRAN")
                    GlassGroup(Modifier.glassEntrance(1)) {
                        SettingsRow(
                            icon = Icons.Outlined.NightsStay,
                            title = if (ar) "التذكيرات" else "Reminders",
                            value = if (remindersOn == 0) (if (ar) "متوقفة" else "Off") else (if (ar) "$remindersOn مفعّلة" else "$remindersOn on"),
                            onClick = { settingsPage = "reminders" },
                        )
                        GlassDivider()
                        SettingsRow(
                            icon = Icons.Outlined.WbTwilight,
                            title = if (ar) "التطبيق" else "App",
                            value = (if (ar) "العربية" else "English") + " · " +
                                (if (io.github.sammynashed.emran.data.TimeFormat.use24) (if (ar) "٢٤ ساعة" else "24h") else (if (ar) "١٢ ساعة" else "12h")),
                            onClick = { settingsPage = "app" },
                        )
                        GlassDivider()
                        SettingsRow(
                            icon = Icons.Outlined.Info,
                            title = if (ar) "المساعدة وحول" else "Help & about",
                            onClick = { settingsPage = "help" },
                            trailing = if (updateResult is UpdateChecker.Result.Available) ({
                                Box(Modifier.size(8.dp).background(Color(0xFFFFB74D), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(if (ar) "تحديث جديد" else "Update", color = sky.inkSoft, fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = sky.inkSoft, modifier = Modifier.size(22.dp))
                            }) else null,
                        )
                    }

                    // The quiet signature at the foot: the mark and the version, a tap opens About.
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = Space.md)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showAboutMenu = true }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        io.github.sammynashed.emran.ui.design.EmranMark(22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (if (ar) "عِمران " else "Emran ") + BuildConfig.VERSION_NAME,
                            color = sky.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                }

                "reminders" -> SettingsSubPage(
                    title = if (ar) "التذكيرات" else "Reminders",
                    subtitle = if (ar) "تنبيهات هادئة تذكّرك بالأذكار وسورة الكهف" else "Quiet nudges for your adhkar and al-Kahf",
                    onBack = { settingsPage = null },
                ) {
                    GlassGroup {
                        rKinds.forEachIndexed { i, kind ->
                            if (i > 0) GlassDivider()
                            val on = reminderVersion.let { io.github.sammynashed.emran.service.AdhkarReminders.isOn(context, kind) }
                            val minute = reminderVersion.let { io.github.sammynashed.emran.service.AdhkarReminders.minute(context, kind) }
                            val t = io.github.sammynashed.emran.data.TimeFormat.full(java.time.LocalTime.of(minute / 60, minute % 60), ar)
                            SettingsRow(
                                icon = when (kind) {
                                    io.github.sammynashed.emran.service.AdhkarReminders.Kind.MORNING -> Icons.Outlined.WbSunny
                                    io.github.sammynashed.emran.service.AdhkarReminders.Kind.EVENING -> Icons.Outlined.NightsStay
                                    io.github.sammynashed.emran.service.AdhkarReminders.Kind.KAHF -> Icons.AutoMirrored.Outlined.MenuBook
                                },
                                minHeight = 50.dp,
                                title = kind.title(ar),
                                value = if (!on) (if (ar) "متوقف" else "Off") else if (kind.fridayOnly) (if (ar) "الجمعة $t" else "Fri $t") else t,
                                onClick = { reminderDialogFor = kind },
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    GlassGroup {
                        SettingsRow(
                            icon = Icons.Default.Layers,
                            minHeight = 50.dp,
                            title = if (ar) "الذكر العائم" else "Floating dhikr",
                            value = if (!fd.isOn(context)) (if (ar) "متوقف" else "Off") else when (fd.everyMinutes(context)) {
                                15 -> if (ar) "كل ربع ساعة" else "Every 15 min"
                                30 -> if (ar) "كل نصف ساعة" else "Every 30 min"
                                60 -> if (ar) "كل ساعة" else "Every hour"
                                120 -> if (ar) "كل ساعتين" else "Every 2 hours"
                                else -> if (ar) "كل ٣ ساعات" else "Every 3 hours"
                            },
                            onClick = onOpenFloatingDhikr,
                        )
                    }
                }

                "app" -> SettingsSubPage(
                    title = if (ar) "التطبيق" else "App",
                    subtitle = if (ar) "اللغة وصيغة الوقت والخلفية" else "Language, clock and background",
                    onBack = { settingsPage = null },
                ) {
                    GlassGroup {
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
                                    modifier = Modifier.width(segW(172)),
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
                                    modifier = Modifier.width(segW(132)),
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
                                    modifier = Modifier.width(segW(if (ar) 172 else 150)),
                                )
                            },
                        )
                    }
                }

                else -> SettingsSubPage(
                    title = if (ar) "المساعدة وحول" else "Help & about",
                    subtitle = if (ar) "راسلنا، حدّث التطبيق، وتعرّف على عِمران" else "Reach us, update the app, and meet Emran",
                    onBack = { settingsPage = null },
                ) {
                    GlassGroup {
                        // Only builds that carry a contact number can send these.
                        if (io.github.sammynashed.emran.data.SupportContact.available) {
                            SettingsRow(
                                icon = Icons.Outlined.BugReport, minHeight = 50.dp,
                                title = if (ar) "أبلغ عن مشكلة" else "Report a problem",
                                onClick = { waMessage(if (ar) "السلام عليكم، واجهتني مشكلة في تطبيق عمران: " else "Assalamu Alaikum Sammy, I found an issue in Emran app: ") },
                            )
                            GlassDivider()
                            SettingsRow(
                                icon = Icons.Outlined.Lightbulb, minHeight = 50.dp,
                                title = if (ar) "اقترح ميزة" else "Suggest a feature",
                                onClick = { waMessage(if (ar) "السلام عليكم، أود اقتراح ميزة لتطبيق عمران: " else "Assalamu Alaikum Sammy, I would like to suggest a feature for Emran: ") },
                            )
                            GlassDivider()
                        }
                        // F-Droid builds are updated by F-Droid, so they have no update row at all.
                        if (BuildConfig.SELF_UPDATE) {
                            SettingsRow(
                                icon = Icons.Outlined.SystemUpdate, minHeight = 50.dp,
                                title = if (ar) "التحديثات" else "Updates",
                                value = if (updateChecking) (if (ar) "تحقق…" else "Checking…") else updateValue,
                                // Two ways to update: a small file from the channel (works on any network),
                                // or asking online whether there is a newer version.
                                onClick = { showUpdateChoice = true },
                            )
                            GlassDivider()
                        }
                        SettingsRow(
                            icon = Icons.Outlined.Info, minHeight = 50.dp,
                            title = if (ar) "حول عِمران" else "About Emran",
                            onClick = { showAboutMenu = true },
                        )
                    }
                }
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
                        onClick = { it.open() },
                    )
                }
            }
        }
    }

    // ---- Dialogs in the glass style (see SettingsDialogs.kt) ----
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



/** A switch's width in dp, growing with the user's font size so its labels never collide (up to 1.6x). */
@androidx.compose.runtime.Composable
private fun segW(base: Int): androidx.compose.ui.unit.Dp =
    (base * (androidx.compose.ui.platform.LocalDensity.current.fontScale / 1.15f).coerceIn(1f, 1.3f)).dp


/** A card's page inside Settings: a header with Back, then its rows (these pages may grow and scroll; the hub never does). */
@androidx.compose.runtime.Composable
private fun SettingsSubPage(title: String, subtitle: String?, onBack: () -> Unit, content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        io.github.sammynashed.emran.ui.design.ScreenHeader(
            title = title, subtitle = subtitle, onBack = onBack,
            modifier = Modifier.padding(horizontal = Space.screen),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen).padding(bottom = 120.dp),
            content = content,
        )
    }
}

