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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Bookmark
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
 * Dashboard widget grid, today's prayer list, and the weekly pillars strip.
 * Extracted from HomeScreen.kt, which had grown to 4,814 lines.
 *
 * Part of the home screen; same package as HomeScreen.kt, so no additional
 * imports are required between them.
 */

@Composable
fun DashboardWidgetBlock(
    id: String,
    viewModel: PrayerViewModel,
    onNavigateToPrayers: () -> Unit,
    onNavigateToQibla: () -> Unit,
    onNavigateToDhikr: () -> Unit,
    onNavigateToAdhkar: () -> Unit = {},
    onNavigateToHadith: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    widgetColor: Color = AppTheme.colors.primary
) {
    val haptic = LocalHapticFeedback.current
    val lang = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current

    val (title, subtitle, icon, action, color) = when (id) {
        "prayers" -> {
            Quintet(
                io.github.sammynashed.emran.ui.localization.Localizations.string("widget_prayers_title", lang), 
                io.github.sammynashed.emran.ui.localization.Localizations.string("widget_prayers_desc", lang), 
                Icons.Default.Schedule, 
                onNavigateToPrayers, 
                widgetColor
            )
        }
        "qibla" -> {
            Quintet(
                io.github.sammynashed.emran.ui.localization.Localizations.string("widget_qibla_title", lang), 
                io.github.sammynashed.emran.ui.localization.Localizations.string("widget_qibla_desc", lang), 
                Icons.Default.Place, 
                onNavigateToQibla, 
                widgetColor
            )
        }
        "dhikr" -> {
            Quintet(
                io.github.sammynashed.emran.ui.localization.Localizations.string("widget_dhikr_title", lang), 
                io.github.sammynashed.emran.ui.localization.Localizations.string("widget_dhikr_desc", lang), 
                ImageVector.vectorResource(id = io.github.sammynashed.emran.R.drawable.ic_misbaha), 
                onNavigateToDhikr, 
                widgetColor
            )
        }
        "adhkar" -> {
            Quintet(
                if (lang == "ar") "الأذكار" else "Adhkar", 
                if (lang == "ar") "أذكار الصباح والمساء" else "Morning & Evening Duas",
                Icons.Default.MenuBook, 
                onNavigateToAdhkar, 
                widgetColor
            )
        }
        "hadith" -> {
            Quintet(
                if (lang == "ar") "الأحاديث" else "Hadith",
                if (lang == "ar") "كتب السنة" else "Books of Sunnah",
                Icons.Default.AutoStories,
                onNavigateToHadith,
                widgetColor
            )
        }
        "bookmarks" -> {
            Quintet(
                if (lang == "ar") "المحفوظات" else "Saved",
                if (lang == "ar") "علاماتك المرجعية" else "Your bookmarks",
                Icons.Default.Bookmark,
                onNavigateToBookmarks,
                widgetColor
            )
        }
        else -> {
            Quintet(
                "Unknown", 
                "", 
                Icons.Default.Info, 
                {}, 
                widgetColor
            )
        }
    }

    val tourTargetTag = when (id) {
        "prayers" -> "today_prayers_widget_card"
        "dhikr" -> "home_widget_dhikr"
        "qibla" -> "home_widget_qibla"
        "adhkar" -> "home_widget_adhkar"
        "hadith" -> "home_widget_hadith"
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .liquidGlassCard(RoundedCornerShape(22.dp), widgetColor)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                action()
            }
            .then(if (tourTargetTag != null) Modifier.tourTarget(tourTargetTag) else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = widgetColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text Info section
            Column {
                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
