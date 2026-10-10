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
/*
 * Prayer grouping models and formatting helpers shared by the home and
 * today's-prayers screens.
 *
 * Part of the home screen; same package as HomeScreen.kt, so no additional
 * imports are required between them.
 */

data class Quintet<A, B, C, D, E>(
    val a: A, val b: B, val c: C, val d: D, val e: E
)

// Model & Data Helper for Groups
data class PrayerGroup(
    val title: String,
    val arabicTitle: String,
    val baseKey: String,
    val components: List<PrayerComponent>
)

data class PrayerComponent(
    val key: String,
    val label: String,
    val subtitle: String,
    val isFard: Boolean
)

fun getDailyGroups(): List<PrayerGroup> {
    val isFriday = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.FRIDAY
    val isJumah = isFriday

    val dhuhrGroup = if (isJumah) {
        PrayerGroup(
            title = "Jum'ah",
            arabicTitle = "الْجُمُعَة",
            baseKey = "DHUHR",
            components = listOf(
                PrayerComponent("JUMAH_SUNNAH_ENTER", "Sunnah (Entering Masjid)", "2 Rakaats • Tahiyatul Masjid", false),
                PrayerComponent("DHUHR_FARD", "Jum'ah Fardh", "2 Rakaats • Mandatory", true),
                PrayerComponent("JUMAH_SUNNAH_AFTER", "Sunnah (After)", "4 Rakaats • Emphasized Sunnah", false)
            )
        )
    } else {
        PrayerGroup(
            title = "Dhuhr",
            arabicTitle = "الظُّهْر",
            baseKey = "DHUHR",
            components = listOf(
                PrayerComponent("DHUHR_SUNNAH_BEFORE", "Sunnah (Before)", "4 Rakaats • Emphasized Sunnah", false),
                PrayerComponent("DHUHR_FARD", "Dhuhr Fardh", "4 Rakaats • Mandatory", true),
                PrayerComponent("DHUHR_SUNNAH_AFTER", "Sunnah (After)", "2 Rakaats • Emphasized Sunnah", false)
            )
        )
    }

    return listOf(
        PrayerGroup(
            title = "Fajr",
            arabicTitle = "الْفَجْر",
            baseKey = "FAJR",
            components = listOf(
                PrayerComponent("FAJR_SUNNAH", "Sunnah (Before)", "2 Rakaats • Emphasized Sunnah", false),
                PrayerComponent("FAJR_FARD", "Fajr Fardh", "2 Rakaats • Mandatory", true)
            )
        ),
        dhuhrGroup,
        PrayerGroup(
            title = "Asr",
            arabicTitle = "الْعَصْر",
            baseKey = "ASR",
            components = listOf(
                PrayerComponent("ASR_FARD", "Asr Fardh", "4 Rakaats • Mandatory", true)
            )
        ),
        PrayerGroup(
            title = "Maghrib",
            arabicTitle = "الْمَغْرِب",
            baseKey = "MAGHRIB",
            components = listOf(
                PrayerComponent("MAGHRIB_FARD", "Maghrib Fardh", "3 Rakaats • Mandatory", true),
                PrayerComponent("MAGHRIB_SUNNAH", "Sunnah (After)", "2 Rakaats • Emphasized Sunnah", false)
            )
        ),
        PrayerGroup(
            title = "Isha",
            arabicTitle = "الْعِشَاء",
            baseKey = "ISHA",
            components = listOf(
                PrayerComponent("ISHA_SUNNAH_BEFORE", "Sunnah (Before)", "4 Rakaats • Optional Sunnah", false),
                PrayerComponent("ISHA_FARD", "Isha Fardh", "4 Rakaats • Mandatory", true),
                PrayerComponent("ISHA_SUNNAH_AFTER", "Sunnah (After)", "2 Rakaats • Emphasized Sunnah", false),
                PrayerComponent("ISHA_WITR", "Witr", "3 Rakaats • Highly Emphasized Wajib", false)
            )
        )
    )
}

fun formatPrayerTime(dateTime: java.time.LocalDateTime, lang: String = "en"): String {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH)
    val formatted = dateTime.format(formatter)
    return if (lang == "ar") {
        formatted.replace("AM", "ص").replace("PM", "م")
    } else {
        formatted
    }
}
