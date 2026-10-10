package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.sammynashed.emran.R
import io.github.sammynashed.emran.ui.design.GlassRow
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.tour.tourTarget

/** The rest of Emran, one calm tap from Home. */
@Composable
fun MoreScreen(
    onBack: () -> Unit,
    onOpenDhikr: () -> Unit,
    onOpenAdhkar: () -> Unit,
    onOpenHadith: () -> Unit,
    onOpenSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ar = LocalAppLanguage.current == "ar"
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen)
            .padding(bottom = 124.dp),
    ) {
        ScreenHeader(title = if (ar) "المزيد" else "More", onBack = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            GlassRow(
                title = if (ar) "الأذكار" else "Adhkar",
                subtitle = if (ar) "أذكار الصباح والمساء" else "Morning & evening",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                onClick = onOpenAdhkar,
                modifier = Modifier.tourTarget("home_widget_adhkar"),
            )
            GlassRow(
                title = if (ar) "التسبيح" else "Dhikr",
                subtitle = if (ar) "العدّاد والأوراد" else "Counter and litanies",
                icon = ImageVector.vectorResource(id = R.drawable.ic_misbaha),
                onClick = onOpenDhikr,
                modifier = Modifier.tourTarget("home_widget_dhikr"),
            )
            GlassRow(
                title = if (ar) "الأحاديث" else "Hadith",
                subtitle = if (ar) "كتب السنة" else "Books of Sunnah",
                icon = Icons.Outlined.AutoStories,
                onClick = onOpenHadith,
                modifier = Modifier.tourTarget("home_widget_hadith"),
            )
            GlassRow(
                title = if (ar) "المحفوظات" else "Saved",
                subtitle = if (ar) "علاماتك المرجعية" else "Your bookmarks",
                icon = Icons.Outlined.BookmarkBorder,
                onClick = onOpenSaved,
            )
        }
    }
}
