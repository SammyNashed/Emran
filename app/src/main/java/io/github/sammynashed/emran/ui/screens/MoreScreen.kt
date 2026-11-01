package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.ui.design.GlassRow
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.design.quietStars
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text

/**
 * The Library page: everything to read or listen to beyond the day's prayers, one tap from Home's top bar.
 * It replaced the shortcut row on Home so Home keeps only what is used all day.
 */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenAdhkar: () -> Unit,
    onOpenHadith: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenReciters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ar = LocalAppLanguage.current == "ar"
    val sky = LocalSky.current
    Column(modifier.fillMaxSize()) {
        io.github.sammynashed.emran.ui.design.ScreenHeader(
            title = if (ar) "المكتبة" else "Library",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = Space.screen),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen).padding(bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            GlassRow(
                title = if (ar) "الأذكار" else "Adhkar",
                enterIndex = 0,
                subtitle = if (ar) "أذكار الصباح والمساء وحصن المسلم" else "Morning, evening & duas",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                onClick = onOpenAdhkar,
            )
            GlassRow(
                title = if (ar) "الأحاديث" else "Hadith",
                enterIndex = 1,
                subtitle = if (ar) "كتب السنة" else "Books of Sunnah",
                icon = Icons.Outlined.AutoStories,
                onClick = onOpenHadith,
            )
            GlassRow(
                title = if (ar) "المحفوظات" else "Saved",
                enterIndex = 2,
                subtitle = if (ar) "علاماتك المرجعية" else "Your bookmarks",
                icon = Icons.Outlined.BookmarkBorder,
                onClick = onOpenSaved,
            )
            GlassRow(
                title = if (ar) "القرّاء" else "Reciters",
                enterIndex = 3,
                subtitle = if (ar) "اسمع الأصوات واختر قارئك" else "Hear the voices, pick yours",
                icon = Icons.Outlined.GraphicEq,
                onClick = onOpenReciters,
            )
        }
    }
}
