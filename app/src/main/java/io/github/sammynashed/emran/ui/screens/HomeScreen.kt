package io.github.sammynashed.emran.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.res.vectorResource
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material3.*
import io.github.sammynashed.emran.ui.localization.Text
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import io.github.sammynashed.emran.ui.tour.tourTarget
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel.CountdownState
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import java.time.LocalDate

val JunicodeRegular = FontFamily(
    Font(io.github.sammynashed.emran.R.font.junicode_regular)
)

val JunicodeItalic = FontFamily(
    Font(io.github.sammynashed.emran.R.font.junicode_italic)
)

fun toArabicNumerals(input: String): String {
    return input
}

@Composable
fun HomeScreen(
    viewModel: PrayerViewModel,
    onNavigateToPrayers: () -> Unit = {},
    onNavigateToQibla: () -> Unit = {},
    onNavigateToDhikr: () -> Unit = {},
    onNavigateToAdhkar: () -> Unit = {},
    onNavigateToHadith: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onOpenHadith: (String, Int, Int) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val lang = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("emran_settings", android.content.Context.MODE_PRIVATE) }
    // Digits stay Latin throughout the app.
    fun toArDigits(str: String): String = str
    val widgetList = remember {
        listOf("prayers", "qibla", "dhikr", "adhkar", "hadith", "bookmarks")
    }
    val chunks = widgetList.chunked(2)

    val countdownStateState = viewModel.countdownState.collectAsState()
    val themePre by viewModel.themePreset.collectAsState()
    val presetColors = remember(themePre) { getPresetColors(themePre) }
    val simulatedToday by viewModel.currentDate.collectAsState()

    val listState = rememberLazyListState()

    val tourManager = io.github.sammynashed.emran.ui.tour.LocalTourManager.current
    if (tourManager != null) {
        val currentStep by tourManager.currentStepIndex.collectAsState()
        val isActive by tourManager.isActive.collectAsState()
        LaunchedEffect(currentStep, isActive) {
            if (isActive) {
                val step = tourManager.steps.getOrNull(currentStep)
                if (step != null && step.targetRoute == "home") {
                    val baseIndex = 2
                    // Cards sit two to a row: prayers+qibla, dhikr+adhkar, hadith+saved.
                    val itemIndex = when (step.id) {
                        "spiritual_prayer_compass" -> 1
                        "today_prayers_widget_card" -> baseIndex
                        "home_widget_qibla" -> baseIndex
                        "home_widget_dhikr" -> baseIndex + 1
                        "home_widget_adhkar" -> baseIndex + 1
                        "home_widget_hadith" -> baseIndex + 2
                        else -> null
                    }
                    if (itemIndex != null) {
                        try {
                            listState.animateScrollToItem(itemIndex)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        run {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
            ) {
        // App Header - Redesigned to be Premium, Balanced, Minimalist, and Zen Moddy
        item {
            var showLanguageMenuHome by remember { mutableStateOf(false) }

            // High-Contrast Formatting for Centered Minimalist Hijri and Gregorian Dates
            val gregFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy") }
            val gregText = remember(simulatedToday, lang) {
                val formatted = simulatedToday.format(gregFormatter)
                if (lang == "ar") toArabicNumerals(formatted) else formatted
            }

            val hijriText = remember(simulatedToday, lang) {
                try {
                    val hijrahDate = java.time.chrono.HijrahDate.from(simulatedToday)
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    val formatted = hijrahDate.format(formatter)
                    if (lang == "ar") toArabicNumerals(formatted) else formatted
                } catch(e: Exception) {
                    if (lang == "ar") "١٤٤٧" else "1447"
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: symmetrical spacer
                Spacer(modifier = Modifier.weight(1f))

                // Center: Welcome message, Big and Minimalist Hijri & Small Gregorian date (Perfect horizontal self-centering)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1.5f)
                ) {
                    Text(
                        text = hijriText,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = gregText,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }

                // Right: Symmetrical layout spacer
                Spacer(modifier = Modifier.weight(1f))
            }
            
        }

        // Live Countdown Block - Replaced with a Signature Spiritual Compass Centered Masterpiece
        item {
            val primaryColor = AppTheme.colors.primary
            val secondaryColor = AppTheme.colors.secondary
            val fardKeys = remember { listOf("FAJR_FARD", "DHUHR_FARD", "ASR_FARD", "MAGHRIB_FARD", "ISHA_FARD") }
            
            // Outer Ring Math Layout Info for 5 Daily Prayers
            // Chronological pentagonal layout starting at Fajr = -162deg, putting Dhuhr at top zenith (-90deg)
            val isRtl = lang == "ar"
            val pathStartAngle = if (isRtl) 30f else -210f
            val pathSweepMax = if (isRtl) -360f else 360f
            val prayersList = remember(lang) {
                val isFriday = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY
                val isJumah = isFriday
                val dhuhrName = if (isJumah) "Jum'ah" else "Dhuhr"
                val dhuhrLabelEn = if (isJumah) "JUM'AH" else "DHUHR"
                val dhuhrLabelAr = if (isJumah) "الجمعة" else "الظهر"
                val dhuhrIcon = if (isJumah) Icons.Default.Mosque else Icons.Default.WbSunny

                if (isRtl) {
                    listOf(
                        CompassPrayerItem("Fajr", "الفجر", "FAJR_FARD", Icons.Default.WbTwilight, 30.0),
                        CompassPrayerItem("Sunrise", "الشروق", "SUNRISE", Icons.Default.WbSunny, -30.0),
                        CompassPrayerItem(dhuhrName, dhuhrLabelAr, "DHUHR_FARD", dhuhrIcon, -90.0),
                        CompassPrayerItem("Asr", "العصر", "ASR_FARD", Icons.Default.LightMode, -150.0),
                        CompassPrayerItem("Maghrib", "المغرب", "MAGHRIB_FARD", Icons.Default.WbTwilight, 150.0),
                        CompassPrayerItem("Isha", "العشاء", "ISHA_FARD", Icons.Default.NightsStay, 90.0)
                    )
                } else {
                    listOf(
                        CompassPrayerItem("Fajr", "FAJR", "FAJR_FARD", Icons.Default.WbTwilight, -210.0),
                        CompassPrayerItem("Sunrise", "SUNRISE", "SUNRISE", Icons.Default.WbSunny, -150.0),
                        CompassPrayerItem(dhuhrName, dhuhrLabelEn, "DHUHR_FARD", dhuhrIcon, -90.0),
                        CompassPrayerItem("Asr", "ASR", "ASR_FARD", Icons.Default.LightMode, -30.0),
                        CompassPrayerItem("Maghrib", "MAGHRIB", "MAGHRIB_FARD", Icons.Default.WbTwilight, 30.0),
                        CompassPrayerItem("Isha", "ISHA", "ISHA_FARD", Icons.Default.NightsStay, 90.0)
                    )
                }
            }

            AstrolabeCentralCompass(
                countdownStateProvider = { countdownStateState.value },
                lang = lang,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                prayersList = prayersList,
                pathStartAngle = pathStartAngle,
                pathSweepMax = pathSweepMax,
                toArDigits = { toArDigits(it) }
            )
        }






        chunks.forEachIndexed { rowIndex, rowWidgets ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowWidgets.forEachIndexed { colIndex, widgetId ->
                        // Left widgets are primary, right widgets are secondary
                        val widgetColor = if (colIndex == 0) AppTheme.colors.primary else AppTheme.colors.secondary
                        Box(modifier = Modifier.weight(1f)) {
                            DashboardWidgetBlock(
                                id = widgetId,
                                viewModel = viewModel,
                                onNavigateToPrayers = onNavigateToPrayers,
                                onNavigateToQibla = onNavigateToQibla,
                                onNavigateToDhikr = onNavigateToDhikr,
                                onNavigateToAdhkar = onNavigateToAdhkar,
                                onNavigateToHadith = onNavigateToHadith,
                                onNavigateToBookmarks = onNavigateToBookmarks,
                                widgetColor = widgetColor
                            )
                        }
                    }
                    if (rowWidgets.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        // Last, so the tour's scroll indices for the cards above do not shift.
        item {
            HadithOfTheDayCard(onOpen = onOpenHadith)
        }
    } // Closes LazyColumn
    } // Closes run
} // Closes Box
} // Closes HomeScreen

