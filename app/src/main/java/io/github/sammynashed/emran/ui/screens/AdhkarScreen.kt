package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammynashed.emran.data.assets.AdhkarFolder
import io.github.sammynashed.emran.ui.design.GlassIconButton
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.GlassSectionLabel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.viewmodel.AdhkarViewModel

/** Adhkar on the sky: the daily ones first, then Hisn al-Muslim by theme. */
@Composable
fun AdhkarScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCategory: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    viewModel: AdhkarViewModel = viewModel(),
) {
    val lang = LocalAppLanguage.current
    val ar = lang == "ar"
    val sky = LocalSky.current
    val folders by viewModel.folders.collectAsState()
    val categories by viewModel.categories.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = Space.screen)) {
        ScreenHeader(
            title = if (ar) "الأذكار" else "Adhkar",
            onBack = onNavigateBack,
            actions = {
                GlassIconButton(Icons.Outlined.Search, if (ar) "بحث" else "Search", onSearch)
                GlassIconButton(Icons.Outlined.BookmarkBorder, if (ar) "المحفوظات" else "Saved", onBookmarks)
            },
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 124.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            val core = folders.filter { !it.isHisn }
            val hisn = folders.filter { it.isHisn }
            itemsIndexed(core, key = { _, f -> f.id }) { _, folder ->
                val emptyMine = folder.id == io.github.sammynashed.emran.data.assets.MyAdhkar.FOLDER_ID && folder.items.isEmpty()
                AdhkarFolderWidget(
                    folder = folder,
                    lang = lang,
                    countLabel = if (emptyMine) (if (ar) "أضف ذكرك" else "Add your own") else null,
                ) { onNavigateToDetail(folder.id) }
            }
            if (hisn.isNotEmpty() && categories.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = Space.lg)) {
                        GlassSectionLabel(if (ar) "حصن المسلم" else "HISN AL-MUSLIM")
                        Text(
                            text = if (ar) "${hisn.size} بابًا في ${categories.size} أقسام."
                            else "${hisn.size} chapters in ${categories.size} themes.",
                            color = sky.inkSoft, fontSize = 13.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                // Prefixed: a category shares its id with a core folder ("prayer"), and a lazy grid
                // crashes on a repeated key as soon as it scrolls to the second one.
                items(categories, key = { "category-${it.id}" }) { cat ->
                    val n = hisn.count { it.category == cat.id }
                    AdhkarFolderWidget(
                        folder = AdhkarFolder(
                            id = "category-${cat.id}", titleEn = cat.titleEn, titleAr = cat.titleAr,
                            icon = cat.icon, color = cat.color, items = emptyList(),
                        ),
                        lang = lang,
                        countLabel = chapterCountLabel(n, lang),
                    ) { onNavigateToCategory(cat.id) }
                }
            }
        }
    }
}

/**
 * "19 adhkar" / "١٩ ذكر", with Arabic pluralisation that matches the count
 * rather than always using the plural form.
 */
fun adhkarCountLabel(count: Int, lang: String): String = when {
    lang != "ar" -> if (count == 1) "1 dhikr" else "$count adhkar"
    count == 1 -> "ذكر واحد"
    count == 2 -> "ذكران"
    count in 3..10 -> "$count أذكار"
    else -> "$count ذكراً"
}

fun chapterCountLabel(count: Int, lang: String): String = when {
    lang != "ar" -> if (count == 1) "1 chapter" else "$count chapters"
    count == 1 -> "باب واحد"
    count == 2 -> "بابان"
    count in 3..10 -> "$count أبواب"
    else -> "$count باباً"
}

/** Icon names used in adhkar.json. To add one, add it here and to SUPPORTED_ICONS in tools/import_adhkar.py. */
@Composable
fun adhkarIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector = when (name) {
    "WbSunny" -> Icons.Default.WbSunny
    "NightsStay" -> Icons.Default.NightsStay
    "Mosque" -> Icons.Default.Mosque
    "Bedtime" -> Icons.Default.Bedtime
    "Brightness5" -> Icons.Default.Brightness5
    "Home" -> Icons.Default.Home
    // Material has no praying figure (its "SelfImprovement" is a yoga pose), so this is drawn: res/drawable/ic_prayer.xml.
    "Praying" -> androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = io.github.sammynashed.emran.R.drawable.ic_prayer)
    "Shield" -> Icons.Default.Shield
    "Favorite" -> Icons.Default.Favorite
    "Cloud" -> Icons.Default.Cloud
    "Restaurant" -> Icons.Default.Restaurant
    "Groups" -> Icons.Default.Groups
    "Flight" -> Icons.Default.Flight
    else -> Icons.Default.Book
}

/**
 * A square glass card: icon in a soft disc, the title, and how many adhkar it holds.
 * Gives a little under a finger, like the rows elsewhere.
 */
@Composable
fun AdhkarFolderWidget(
    folder: AdhkarFolder,
    lang: String,
    modifier: Modifier = Modifier,
    /** Overrides "N adhkar", e.g. for a category that holds chapters. */
    countLabel: String? = null,
    titleLines: Int = 2,
    onClick: () -> Unit,
) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "press")
    val title = if (lang == "ar") folder.titleAr else folder.titleEn

    // Scrolling grid: tint and rim, no lens (a bent copy of the sky would drift as it scrolls).
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        cornerRadius = 28.dp,
        lens = false,
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(adhkarIcon(folder.icon), contentDescription = null, tint = sky.ink, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.height(12.dp))
            // The title is capped so the count below is always the thing that survives.
            Text(
                text = title, color = sky.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp,
                maxLines = titleLines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = countLabel ?: adhkarCountLabel(folder.items.size, lang),
                color = sky.inkSoft, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center,
            )
        }
    }
}

/** The chapters of one Hisn al-Muslim theme, as the same square cards as the top-level page. */
@Composable
fun AdhkarCategoryScreen(
    categoryId: String,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: AdhkarViewModel = viewModel(),
) {
    val lang = LocalAppLanguage.current
    val folders by viewModel.folders.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val category = categories.firstOrNull { it.id == categoryId }
    val chapters = folders.filter { it.category == categoryId }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.screen)) {
        ScreenHeader(
            title = if (lang == "ar") category?.titleAr.orEmpty() else category?.titleEn.orEmpty(),
            subtitle = chapterCountLabel(chapters.size, lang),
            onBack = onNavigateBack,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 124.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            items(chapters, key = { it.id }) { folder ->
                // Chapter titles are sentences ("What to say upon completing ablution"),
                // so they get three lines and take the category's icon.
                AdhkarFolderWidget(
                    folder = folder.copy(icon = category?.icon ?: folder.icon, color = category?.color ?: folder.color),
                    lang = lang,
                    titleLines = 3,
                ) { onNavigateToDetail(folder.id) }
            }
        }
    }
}
