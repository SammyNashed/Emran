package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.localization.Text
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.sammynashed.emran.data.LocationPresets
import io.github.sammynashed.emran.data.PrayerTimeCalculator
import io.github.sammynashed.emran.service.rememberNotificationHealth
import io.github.sammynashed.emran.ui.design.ChoiceDialog
import io.github.sammynashed.emran.ui.design.GlassDivider
import io.github.sammynashed.emran.ui.design.GlassGroup
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.SettingsRow
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import java.time.LocalDate
import java.time.LocalDateTime

// Pick lists for the calculation settings (shared by the page and anything else that needs them).

fun calculationMethods(ar: Boolean): List<Pair<String, String>> = listOf(
    "EGYPTIAN" to if (ar) "الهيئة المصرية العامة للمساحة" else "Egyptian General Authority",
    "UMM_AL_QURA" to if (ar) "جامعة أم القرى (مكة المكرمة)" else "Umm Al-Qura (Mecca)",
    "MWL" to if (ar) "رابطة العالم الإسلامي" else "Muslim World League",
    "ISNA" to if (ar) "الجمعية الإسلامية لأمريكا الشمالية" else "Islamic Society of North America",
    "KARACHI" to if (ar) "جامعة العلوم الإسلامية بكراتشي" else "University of Islamic Sciences, Karachi",
    "DUBAI" to if (ar) "دبي (الإمارات)" else "Dubai (UAE)",
    "JORDAN" to if (ar) "وزارة الأوقاف الأردنية" else "Ministry of Awqaf, Jordan",
    "MOROCCO" to if (ar) "وزارة الأوقاف المغربية" else "Ministry of Habous, Morocco",
    "TUNISIA" to if (ar) "تونس" else "Tunisia",
    "JAKIM" to if (ar) "ماليزيا (جاكيم)" else "JAKIM (Malaysia)",
    "KEMENAG" to if (ar) "إندونيسيا (وزارة الشؤون الدينية)" else "Kemenag (Indonesia)",
    "QATAR" to if (ar) "قطر" else "Qatar",
    "KUWAIT" to if (ar) "الكويت" else "Kuwait",
    "SINGAPORE" to if (ar) "سنغافورة (المجلس الإسلامي)" else "Singapore (MUIS)",
    "TURKEY" to if (ar) "الديانة التركية" else "Diyanet (Turkey)",
    "TEHRAN" to if (ar) "معهد الجيوفيزياء، طهران" else "Institute of Geophysics, Tehran",
    "JAFARI" to if (ar) "الجعفري (الإثنا عشري)" else "Shia Ithna Ashari (Ja'fari)",
)

fun asrSchools(ar: Boolean): List<Pair<String, String>> = listOf(
    "SHAFII" to if (ar) "الجمهور (شافعي، مالكي، حنبلي)" else "Standard (Shafi'i, Maliki, Hanbali)",
    "HANAFI" to if (ar) "المذهب الحنفي" else "Hanafi School",
)

/** Only meaningful above roughly 48 degrees, where the sun may never reach the Fajr or Isha angle. */
fun highLatitudeRules(ar: Boolean): List<Pair<String, String>> = listOf(
    "AUTOMATIC" to if (ar) "تلقائي (موصى به)" else "Automatic (recommended)",
    "MIDDLE_OF_NIGHT" to if (ar) "منتصف الليل" else "Middle of the night",
    "ONE_SEVENTH_OF_NIGHT" to if (ar) "سُبع الليل" else "One-seventh of the night",
    "ANGLE_BASED" to if (ar) "حسب الزاوية (تناسبي)" else "Angle-based (proportional)",
    "NONE" to if (ar) "بدون تعديل (فلكي بحت)" else "None (astronomical only)",
)

/**
 * Prayer calculation: where you are, which method and Asr rule, and small manual nudges.
 * One row per setting; each opens a small picker, like the Adhan page.
 */
@Composable
fun CalculationScreen(viewModel: PrayerViewModel, lang: String, onBack: () -> Unit) {
    val ar = lang == "ar"
    val offsets by viewModel.offsets.collectAsState()
    val locationName by viewModel.locationName.collectAsState()
    val lat by viewModel.latitude.collectAsState()
    val lon by viewModel.longitude.collectAsState()
    val zoneId by viewModel.zoneId.collectAsState()
    val method by viewModel.calculationMethod.collectAsState()
    val school by viewModel.asrSchool.collectAsState()
    val highLat by viewModel.highLatitudeRule.collectAsState()

    val follow by viewModel.followLocation.collectAsState()
    val autoMethod by viewModel.autoMethod.collectAsState()
    val iqama by viewModel.iqamaMinutes.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showFollow by remember { mutableStateOf(false) }
    var showIqama by remember { mutableStateOf(false) }
    var followNote by remember { mutableStateOf<String?>(null) }
    val askLocation = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            viewModel.setFollowLocation(true)
            scope.launch {
                val moved = io.github.sammynashed.emran.data.LocationFollower.refresh(context, viewModel, force = true)
                followNote = if (moved) (if (ar) "تم تحديث المواقيت لموقعك الحالي" else "Prayer times updated for where you are")
                else (if (ar) "تعذّر تحديد موقعك الآن. سيحاول عِمران عند فتح التطبيق." else "Couldn't find you just now. Emran will try again when you open it.")
            }
        } else {
            viewModel.setFollowLocation(false)
            followNote = if (ar) "لم يُسمح بالموقع، فبقي الموقع كما اخترته." else "Location wasn't allowed, so your chosen place stays."
        }
    }
    var showLocation by remember { mutableStateOf(false) }
    var showMethod by remember { mutableStateOf(false) }
    var showSchool by remember { mutableStateOf(false) }
    var showHighLat by remember { mutableStateOf(false) }
    var showOffsets by remember { mutableStateOf(false) }
    var clockFor by remember { mutableStateOf<Triple<String, String, LocalDateTime>?>(null) }

    val methods = remember(ar) { calculationMethods(ar) }
    val schools = remember(ar) { asrSchools(ar) }
    val highLatRules = remember(ar) { highLatitudeRules(ar) }

    // The calibrated times, so the fine-tune picker can show what each prayer would become.
    val baseTimes = remember(lat, lon, zoneId, method, school, highLat) {
        val m = try { PrayerTimeCalculator.CalculationMethod.valueOf(method) } catch (e: Exception) { PrayerTimeCalculator.CalculationMethod.MWL }
        val sc = try { PrayerTimeCalculator.AsrSchool.valueOf(school) } catch (e: Exception) { PrayerTimeCalculator.AsrSchool.SHAFII }
        val rule = try { PrayerTimeCalculator.HighLatitudeRule.valueOf(highLat) } catch (e: Exception) { PrayerTimeCalculator.HighLatitudeRule.AUTOMATIC }
        val zone = PrayerTimeCalculator.safeZone(zoneId)
        PrayerTimeCalculator.calculateTimes(
            date = LocalDate.now(zone), latitude = lat, longitude = lon, zone = zone,
            method = m, school = sc, highLatitudeRule = rule,
        )
    }
    val adjustables = remember(ar, baseTimes) {
        listOf(
            Triple("FAJR", if (ar) "الفجر" else "Fajr", baseTimes.fajr),
            Triple("SUNRISE", if (ar) "الشروق" else "Sunrise", baseTimes.sunrise),
            Triple("DHUHR", if (ar) "الظهر" else "Dhuhr", baseTimes.dhuhr),
            Triple("ASR", if (ar) "العصر" else "Asr", baseTimes.asr),
            Triple("MAGHRIB", if (ar) "المغرب" else "Maghrib", baseTimes.maghrib),
            Triple("ISHA", if (ar) "العشاء" else "Isha", baseTimes.isha),
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = if (ar) "حساب المواقيت" else "Prayer calculation",
                subtitle = if (ar) "كيف يحسب عِمران أوقات الصلاة في مكانك" else "How Emran works out prayer times where you are",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.screen).padding(bottom = 120.dp),
            ) {
                GlassGroup {
                    SettingsRow(
                        Icons.Default.MyLocation, if (ar) "تتبّع موقعي" else "Follow my location",
                        onClick = { showFollow = true }, minHeight = 50.dp,
                        value = if (follow) (if (ar) "مفعّل" else "On") else (if (ar) "متوقف" else "Off"),
                    )
                    GlassDivider()
                    SettingsRow(
                        Icons.Default.LocationOn, if (ar) "الموقع" else "Location",
                        onClick = { showLocation = true }, minHeight = 50.dp,
                        value = LocationPresets.getLocalizedName(locationName, lang),
                    )
                    GlassDivider()
                    SettingsRow(
                        Icons.Default.Explore, if (ar) "الطريقة" else "Method",
                        onClick = { showMethod = true }, minHeight = 50.dp,
                        value = methods.find { it.first == method }?.second ?: method,
                    )
                    GlassDivider()
                    SettingsRow(
                        Icons.Default.Schedule, if (ar) "العصر" else "Asr",
                        onClick = { showSchool = true }, minHeight = 50.dp,
                        value = schools.find { it.first == school }?.second ?: school,
                    )
                    if (kotlin.math.abs(lat) > 48.0) {
                        GlassDivider()
                        SettingsRow(
                            Icons.Default.WbTwilight, if (ar) "خطوط العرض العليا" else "High latitude",
                            onClick = { showHighLat = true }, minHeight = 50.dp,
                            value = highLatRules.find { it.first == highLat }?.second ?: highLat,
                        )
                    }
                    GlassDivider()
                    SettingsRow(
                        Icons.Default.Edit, if (ar) "ضبط الدقائق يدوياً" else "Fine-tune minutes",
                        onClick = { showOffsets = true }, minHeight = 50.dp,
                        value = if (offsets.values.any { it != 0 }) (if (ar) "مفعّل" else "On") else null,
                    )
                    GlassDivider()
                    SettingsRow(
                        Icons.Default.Alarm, if (ar) "الإقامة" else "Iqama",
                        onClick = { showIqama = true }, minHeight = 50.dp,
                        value = if (ar) "بعد الأذان بدقائق" else "Minutes after adhan",
                    )
                }
                followNote?.let {
                    Spacer(Modifier.height(Space.sm))
                    Text(it, color = LocalSky.current.inkSoft, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
                }
                Spacer(Modifier.height(Space.md))
            }
        }
    }

    if (showFollow) {
        FollowLocationDialog(
            ar = ar, on = follow, autoMethod = autoMethod,
            onTurnOn = {
                showFollow = false
                if (io.github.sammynashed.emran.data.LocationFollower.hasPermission(context)) {
                    viewModel.setFollowLocation(true)
                    scope.launch {
                        val moved = io.github.sammynashed.emran.data.LocationFollower.refresh(context, viewModel, force = true)
                        followNote = if (moved) (if (ar) "تم تحديث المواقيت لموقعك الحالي" else "Prayer times updated for where you are") else null
                    }
                } else {
                    askLocation.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION))
                }
            },
            onTurnOff = { viewModel.setFollowLocation(false); showFollow = false },
            onAutoMethod = { viewModel.setAutoMethod(it) },
            onDismiss = { showFollow = false },
        )
    }
    if (showIqama) {
        IqamaDialog(
            ar = ar, lang = lang, minutes = iqama,
            onChange = { key, m -> viewModel.setIqamaMinutes(key, m) },
            onDismiss = { showIqama = false },
        )
    }
    if (showLocation) {
        LocationDialog(
            ar = ar, lang = lang,
            activeName = locationName, activeLat = lat, activeLon = lon, activeZone = zoneId,
            onPick = { viewModel.setLocation(it); showLocation = false },
            onDismiss = { showLocation = false },
        )
    }
    if (showMethod) {
        ChoiceDialog(
            title = if (ar) "طريقة الحساب" else "Calculation method",
            options = methods, selectedKey = method,
            onPick = { viewModel.setCalculationMethod(it); showMethod = false },
            onDismiss = { showMethod = false },
        )
    }
    if (showSchool) {
        ChoiceDialog(
            title = if (ar) "وقت العصر" else "Asr time",
            options = schools, selectedKey = school,
            onPick = { viewModel.setAsrSchool(it); showSchool = false },
            onDismiss = { showSchool = false },
        )
    }
    if (showHighLat) {
        ChoiceDialog(
            title = if (ar) "خطوط العرض العليا" else "High latitude",
            subtitle = if (ar) "للمناطق التي قد لا تبلغ الشمس فيها زاوية الفجر أو العشاء."
            else "For places where the sun may never reach the Fajr or Isha angle.",
            options = highLatRules, selectedKey = highLat,
            onPick = { viewModel.setHighLatitudeRule(it); showHighLat = false },
            onDismiss = { showHighLat = false },
        )
    }
    if (showOffsets) {
        OffsetsDialog(
            ar = ar, rows = adjustables, offsets = offsets,
            onPick = { clockFor = it },
            onDismiss = { showOffsets = false },
        )
    }
    clockFor?.let { target ->
        ClockPopupDialog(
            label = target.second,
            baseDateTime = target.third,
            currentOffset = offsets[target.first] ?: 0,
            onDismiss = { clockFor = null },
            onSave = { newOffset ->
                viewModel.updateOffset(target.first, newOffset)
                clockFor = null
            },
        )
    }
}

/** Reliable alerts: what must be on for the adhan to arrive on time even when Emran is closed. */
@Composable
fun ReliableAlertsScreen(lang: String, onBack: () -> Unit) {
    val ar = lang == "ar"
    val health = rememberNotificationHealth()
    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = if (ar) "وصول التنبيه" else "Reliable alerts",
                subtitle = if (ar) "فعّلها كلها ليصلك الأذان في وقته" else "Keep all of these on so the adhan arrives on time",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.screen).padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ReliabilityRows(ar, health)
            }
        }
    }
}
