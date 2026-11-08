package io.github.sammynashed.emran.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.text.style.TextAlign
import io.github.sammynashed.emran.ui.design.GlassIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammynashed.emran.LocalDockVisible
import io.github.sammynashed.emran.data.assets.AdhkarFolder
import io.github.sammynashed.emran.data.assets.MyAdhkar
import io.github.sammynashed.emran.service.FloatingDhikr
import io.github.sammynashed.emran.ui.design.GlassButton
import io.github.sammynashed.emran.ui.design.GlassSectionLabel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.viewmodel.AdhkarViewModel

/**
 * Choosing what the floating card shows: the Adhkar page itself (same sections, same chapters, same cards),
 * with a check on each dhikr instead of its share buttons. Pick as many as you like anywhere, then Save once.
 * Back without saving keeps the old choice.
 */
@Composable
fun FloatingDhikrPickScreen(onBack: () -> Unit, viewModel: AdhkarViewModel = viewModel()) {
    val lang = LocalAppLanguage.current
    val ar = lang == "ar"
    val sky = LocalSky.current
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var picked by remember { mutableStateOf(FloatingDhikr.selectedIds(context)) }
    // Where we are inside the page: the top grid, a Hisn al-Muslim theme, or one section's adhkar.
    var categoryId by remember { mutableStateOf<String?>(null) }
    var folderId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    // Save sits where the dock usually is.
    val dock = LocalDockVisible.current
    DisposableEffect(Unit) {
        dock.value = false
        onDispose { dock.value = true }
    }
    BackHandler(enabled = folderId != null || categoryId != null) {
        if (folderId != null) folderId = null else categoryId = null
    }

    fun countIn(f: AdhkarFolder) = f.items.count { it.id in picked }
    fun pickedLabel(f: AdhkarFolder): String? = countIn(f).takeIf { it > 0 }?.let { n ->
        if (ar) "اخترت ${n} من ${f.items.size}" else "$n of ${f.items.size} picked"
    }

    val folder = folders.firstOrNull { it.id == folderId }
    val category = categories.firstOrNull { it.id == categoryId }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.screen)) {
        ScreenHeader(
            title = when {
                folder != null -> if (ar) folder.titleAr else folder.titleEn
                category != null -> if (ar) category.titleAr else category.titleEn
                else -> if (ar) "الأذكار التي تظهر" else "Adhkar it shows"
            },
            subtitle = when {
                folder != null && folder.items.isEmpty() -> null
                folder != null -> if (ar) "اضغط على الذكر لتختاره أو تلغيه" else "Tap a dhikr to pick or unpick it"
                else -> if (ar) "اختر من أي قسم، ثم احفظ. تظهر المختارة بالتناوب." else "Pick from any section, then save. They take turns."
            },
            onBack = { if (folderId != null) folderId = null else if (categoryId != null) categoryId = null else onBack() },
            actions = {
                if (folder?.id == MyAdhkar.FOLDER_ID && folder.items.isNotEmpty()) {
                    GlassIconButton(Icons.Outlined.Add, if (ar) "ذكر جديد" else "New dhikr", { adding = true })
                }
            },
        )

        Box(Modifier.weight(1f)) {
            when {
                folder != null && folder.id == MyAdhkar.FOLDER_ID && folder.items.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (ar) "اكتب ذكرك أو دعاءك، فيظهر في البطاقة العائمة ويُحفظ في «أذكاري»."
                        else "Write your own dhikr or du'a. It shows on the floating card and stays in “My adhkar”.",
                        color = sky.inkSoft, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    GlassButton(if (ar) "أضف ذكرًا" else "Add a dhikr", { adding = true }, filled = true)
                }

                folder != null -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    items(folder.items, key = { it.id ?: it.textAr }) { item ->
                        val id = item.id
                        val sel = id != null && id in picked
                        AdhkarListItem(
                            item = item, lang = lang, selected = sel,
                            onClick = {
                                if (id != null) {
                                    picked = if (sel) picked - id else picked + id
                                }
                            },
                            onLongClick = {},
                        )
                    }
                }

                category != null -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    items(folders.filter { it.category == category.id }, key = { it.id }) { f ->
                        AdhkarFolderWidget(
                            folder = f.copy(icon = category.icon, color = category.color),
                            lang = lang, titleLines = 3, countLabel = pickedLabel(f),
                        ) { folderId = f.id }
                    }
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    val core = folders.filter { !it.isHisn }
                    val hisn = folders.filter { it.isHisn }
                    items(core, key = { it.id }) { f ->
                        val emptyMine = f.id == MyAdhkar.FOLDER_ID && f.items.isEmpty()
                        AdhkarFolderWidget(
                            folder = f, lang = lang,
                            countLabel = if (emptyMine) (if (ar) "أضف ذكرك" else "Add your own") else pickedLabel(f),
                        ) { folderId = f.id }
                    }
                    if (hisn.isNotEmpty() && categories.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GlassSectionLabel(if (ar) "حصن المسلم" else "HISN AL-MUSLIM", Modifier.padding(top = Space.lg))
                        }
                        items(categories, key = { "category-${it.id}" }) { cat ->
                            val chapters = hisn.filter { it.category == cat.id }
                            val n = chapters.sumOf { countIn(it) }
                            AdhkarFolderWidget(
                                folder = AdhkarFolder(
                                    id = "category-${cat.id}", titleEn = cat.titleEn, titleAr = cat.titleAr,
                                    icon = cat.icon, color = cat.color, items = emptyList(),
                                ),
                                lang = lang,
                                countLabel = if (n > 0) (if (ar) "اخترت $n" else "$n picked") else chapterCountLabel(chapters.size, lang),
                            ) { categoryId = cat.id }
                        }
                    }
                }
            }
        }

        // Save: one press keeps everything picked across sections.
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 10.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GlassButton(
                when {
                    picked.isEmpty() -> if (ar) "اختر ذكرًا واحدًا على الأقل" else "Pick at least one dhikr"
                    ar -> "حفظ (${picked.size})"
                    else -> "Save (${picked.size})"
                },
                { if (picked.isNotEmpty()) { FloatingDhikr.setSelected(context, picked); onBack() } },
                filled = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
        }
    }

    if (adding) {
        // A dhikr written here is picked straight away.
        MyDhikrEditor(item = null, lang = lang, onDismiss = { adding = false }, onSaved = { picked = picked + it })
    }
}
