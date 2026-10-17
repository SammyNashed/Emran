package io.github.sammynashed.emran.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.AyahTafsir
import io.github.sammynashed.emran.ui.localization.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What an ayah means, opened by tapping it in the reader: Al-Mukhtasar's explanation in Arabic,
 * or Saheeh International's translation and Al-Mukhtasar's English edition. Drawn in the page's
 * own colours (it belongs to the muṣḥaf, not to the sky), with the bookmark for "I stopped here".
 */
@Composable
fun AyahMeaningSheet(
    surah: Int,
    ayah: Int,
    surahName: String,
    theme: QuranThemeMode,
    lang: String,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ar = lang == "ar"
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val ink = theme.textColor
    val panel = lerp(theme.backgroundColor, theme.textColor, 0.06f)
    BackHandler(onBack = onDismiss)

    val entry by produceState<AyahTafsir.Entry?>(null, surah, ayah) {
        value = withContext(Dispatchers.IO) { AyahTafsir.get(context.applicationContext, surah, ayah) }
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The page stays in view behind a light veil; a tap on it closes the sheet.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.66f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(panel)
                    .border(1.dp, ink.copy(alpha = 0.12f), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    // Taps inside the sheet must not fall through to the veil.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 16.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp)
                        .clip(CircleShape).background(ink.copy(alpha = 0.22f)),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (ar) "سورة $surahName" else surahName,
                            color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            fontFamily = if (ar) SurahNameFontFamily else null,
                        )
                        Text(
                            if (ar) "الآية $ayah" else "Ayah $surah:$ayah",
                            color = ink.copy(alpha = 0.6f), fontSize = 13.sp,
                        )
                    }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(ink.copy(alpha = 0.07f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Close, if (ar) "إغلاق" else "Close", tint = ink.copy(alpha = 0.75f), modifier = Modifier.size(20.dp)) }
                }
                Spacer(Modifier.height(12.dp))

                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val e = entry
                    if (e == null) {
                        Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = theme.ornament, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        if (!ar && e.translation.isNotBlank()) {
                            Text(
                                "“${standaloneTranslation(e.translation)}”",
                                color = ink, fontSize = 18.sp, lineHeight = 27.sp,
                            )
                        }
                        val tafsir = if (ar) e.tafsirAr else e.tafsirEn
                        if (tafsir.isNotBlank()) {
                            Text(
                                if (ar) "المعنى" else "Explanation",
                                color = theme.ornament, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = if (ar) 0.dp else 6.dp),
                            )
                            e.group?.let { g ->
                                // Al-Mukhtasar explains a few ayat together; say so, so it never reads as a mismatch.
                                Text(
                                    if (ar) "تفسير الآيات ${g.first}–${g.last} معًا" else "Explains ayat ${g.first}–${g.last} together",
                                    color = ink.copy(alpha = 0.6f), fontSize = 12.sp,
                                )
                            }
                            Text(
                                tafsir, color = ink.copy(alpha = 0.92f),
                                fontSize = if (ar) 18.sp else 16.sp, lineHeight = if (ar) 31.sp else 25.sp,
                            )
                        }
                        Text(
                            if (ar) "المختصر في تفسير القرآن الكريم"
                            else "Saheeh International · Al-Mukhtasar (Tafsir Center for Quranic Studies)",
                            color = ink.copy(alpha = 0.45f), fontSize = 11.sp, lineHeight = 15.sp,
                        )
                    }
                }

                // Bookmarking lives on the top-bar bookmark (tap it, then the ayah), so the meaning stays just that.
            }
        }
    }
}
