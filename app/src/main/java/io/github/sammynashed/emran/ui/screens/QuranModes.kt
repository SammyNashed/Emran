package io.github.sammynashed.emran.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.QuranChapter
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Radius
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.tour.tourTarget

/**
 * The Quran tab's front door: how do you want to read? Two big glass cards on the sky,
 * and a quiet shortcut back to where you stopped.
 */
@Composable
fun QuranModesPage(
    lang: String,
    resumeChapter: QuranChapter?,
    onTilawah: () -> Unit,
    onHifz: () -> Unit,
    onResume: (QuranChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ar = lang == "ar"
    val sky = LocalSky.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen)
            .padding(top = Space.xl, bottom = 124.dp),
    ) {
        Text(
            text = if (ar) "القرآن الكريم" else "Qur'an",
            color = sky.ink,
            fontSize = 36.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (ar) "كيف تريد أن تقرأ؟" else "How would you like to read?",
            color = sky.inkSoft,
            fontSize = 17.sp,
            modifier = Modifier.padding(top = Space.xs, bottom = Space.xl),
        )

        Column(
            modifier = Modifier.tourTarget("quran_mode_cards"),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            ModeCard(
                title = if (ar) "تلاوة" else "Tilawah",
                subtitle = if (ar) "اقرأ المصحف كاملًا، مع أحكام التجويد ملوّنة."
                else "Read the mushaf, with the tajweed rules coloured in.",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                onClick = onTilawah,
                modifier = Modifier.testTag("mode_card_tilawah"),
            )
            ModeCard(
                title = if (ar) "حفظ" else "Hifz",
                subtitle = if (ar) "تلاوة من الحفظ — تظهر الكلمات كلما تلوتها."
                else "Recite from memory - words appear as you say them.",
                icon = Icons.Outlined.Mic,
                onClick = onHifz,
                modifier = Modifier.testTag("mode_card_hifz"),
            )
        }

        if (resumeChapter != null) {
            Spacer(Modifier.height(Space.xl))
            val haptic = LocalHapticFeedback.current
            GlassPanel(
                modifier = Modifier
                    .testTag("bookmark_resume_card")
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onResume(resumeChapter)
                    },
                cornerRadius = Radius.control + 8.dp,
                tint = 0.9f,
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.BookmarkAdded, contentDescription = null, tint = sky.ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = (if (ar) "متابعة: " else "Resume: ") + resumeChapter.transliteration,
                        color = sky.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "press",
    )
    GlassPanel(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        cornerRadius = 32.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = sky.ink, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = sky.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = sky.inkSoft, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

/**
 * Pick a surah: a search field and the 114 surahs as quiet glass rows, on the sky.
 * Replaces the cream pop-up dialog, which belonged to no other screen in the app.
 */
@Composable
fun SurahPicker(
    lang: String,
    allChapters: List<QuranChapter>,
    onSelect: (QuranChapter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ar = lang == "ar"
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, allChapters) {
        if (query.isBlank()) allChapters
        else allChapters.filter {
            it.transliteration.contains(query, ignoreCase = true) ||
                it.name.contains(query, ignoreCase = true) ||
                it.id.toString() == query.trim()
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = Space.screen)) {
        ScreenHeader(title = if (ar) "اختر السورة" else "Select Surah", onBack = onBack)

        GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 28.dp, tint = 0.9f, lens = false) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = sky.inkSoft, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(if (ar) "بحث عن سورة..." else "Search Surah...", color = sky.inkSoft, fontSize = 17.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = sky.ink, fontSize = 17.sp),
                        cursorBrush = SolidColor(sky.ink),
                        modifier = Modifier.fillMaxWidth().testTag("surah_search"),
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.md))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // The list fades out under the dock instead of running through it.
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = 150.dp.toPx()
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Black, 1f to Color.Transparent,
                            startY = size.height - fade, endY = size.height,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade),
                        size = androidx.compose.ui.geometry.Size(size.width, fade),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 124.dp),
        ) {
            items(filtered, key = { it.id }) { chapter ->
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(chapter)
                        },
                    cornerRadius = 24.dp,
                    tint = 0.8f,
                    lens = false,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(chapter.id.toString(), color = sky.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chapter.transliteration, color = sky.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(surahMetaLabel(chapter.type, chapter.total_verses, lang), color = sky.inkSoft, fontSize = 12.sp, maxLines = 1)
                        }
                        Text(
                            chapter.name,
                            color = sky.ink, fontSize = 24.sp, maxLines = 1, softWrap = false,
                            fontFamily = SurahNameFontFamily,
                        )
                    }
                }
            }
        }
    }
}
