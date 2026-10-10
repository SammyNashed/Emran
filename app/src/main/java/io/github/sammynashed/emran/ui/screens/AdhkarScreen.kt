package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Book
import androidx.compose.ui.res.vectorResource
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammynashed.emran.data.assets.AdhkarFolder
import io.github.sammynashed.emran.data.assets.AdhkarCategory
import io.github.sammynashed.emran.ui.components.liquidGlassCard
import io.github.sammynashed.emran.ui.tour.tourTarget
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.theme.AppTheme
import io.github.sammynashed.emran.ui.viewmodel.AdhkarViewModel
import io.github.sammynashed.emran.ui.localization.Text

@Composable
fun AdhkarScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToCategory: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    viewModel: AdhkarViewModel = viewModel()
) {
    val lang = LocalAppLanguage.current
    val isLoading by viewModel.isLoading.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val categories by viewModel.categories.collectAsState()
    
    val title = if (lang == "ar") "الأذكار" else "ADHKAR"

    CompositionLocalProvider(
        LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .tourTarget("sub_screen_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )

                for ((icon, desc, action) in listOf(
                    Triple(Icons.Default.Search, "Search", onSearch),
                    Triple(Icons.Default.Bookmark, "Saved", onBookmarks)
                )) {
                    IconButton(
                        onClick = action,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(imageVector = icon, contentDescription = desc, tint = Color.White)
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppTheme.colors.primary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val core = folders.filter { !it.isHisn }
                    val hisn = folders.filter { it.isHisn }
                    itemsIndexed(core) { index, folder ->
                        val itemModifier = if (index == 0) Modifier.tourTarget("adhkar_first_folder") else Modifier
                        AdhkarFolderWidget(
                            folder = folder,
                            lang = lang,
                            modifier = itemModifier
                        ) {
                            onNavigateToDetail(folder.id)
                        }
                    }
                    if (hisn.isNotEmpty() && categories.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = if (lang == "ar") "حصن المسلم" else "MORE FROM HISN AL-MUSLIM",
                                    color = AppTheme.colors.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (lang == "ar") "${hisn.size} بابًا في ${categories.size} أقسام، منسوبة إلى أبواب الكتاب لا إلى رقم الحديث."
                                    else "${hisn.size} chapters in ${categories.size} themes, attributed to the book's chapter rather than to a hadith number.",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        items(categories, key = { it.id }) { cat ->
                            val n = hisn.count { it.category == cat.id }
                            AdhkarFolderWidget(
                                folder = AdhkarFolder(
                                    id = "category-${cat.id}", titleEn = cat.titleEn, titleAr = cat.titleAr,
                                    icon = cat.icon, color = cat.color, items = emptyList()
                                ),
                                lang = lang,
                                countLabel = chapterCountLabel(n, lang)
                            ) { onNavigateToCategory(cat.id) }
                        }
                    }
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

@Composable
fun AdhkarFolderWidget(
    folder: AdhkarFolder,
    lang: String,
    modifier: Modifier = Modifier,
    /** Overrides "N adhkar", e.g. for a category that holds chapters. */
    countLabel: String? = null,
    titleLines: Int = 2,
    onClick: () -> Unit
) {
    val title = if (lang == "ar") folder.titleAr else folder.titleEn

    val icon = adhkarIcon(folder.icon)

    val parsedColor = try {
        Color(android.graphics.Color.parseColor("#${folder.color}"))
    } catch (e: Exception) {
        AppTheme.colors.primary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .liquidGlassCard(RoundedCornerShape(24.dp), parsedColor)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = parsedColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Titles wrap to two lines in English ("Morning Adhkar"), which used to
            // push the count out of the square card and clip it. The title is capped
            // so the count below is always the thing that survives.
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
                maxLines = titleLines,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = countLabel ?: adhkarCountLabel(folder.items.size, lang),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
    viewModel: AdhkarViewModel = viewModel()
) {
    val lang = LocalAppLanguage.current
    val folders by viewModel.folders.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val category = categories.firstOrNull { it.id == categoryId }
    val chapters = folders.filter { it.category == categoryId }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (lang == "ar") category?.titleAr.orEmpty() else category?.titleEn.orEmpty(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = chapterCountLabel(chapters.size, lang),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(chapters, key = { it.id }) { folder ->
                    // Chapter titles are sentences ("What to say upon completing ablution"),
                    // so they get three lines and take the category's icon and colour.
                    AdhkarFolderWidget(
                        folder = folder.copy(icon = category?.icon ?: folder.icon, color = category?.color ?: folder.color),
                        lang = lang,
                        titleLines = 3
                    ) { onNavigateToDetail(folder.id) }
                }
            }
        }
    }
}
