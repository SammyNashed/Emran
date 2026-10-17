package io.github.sammynashed.emran.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.repository.QuranRepository
import io.github.sammynashed.emran.ui.design.GlassBackButton
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.share.ShareCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject

private class AyahPage(
    val surahAr: String,
    val surahEn: String,
    val arabic: String,
    val translation: String?,
    val explanation: String?,
    val group: IntRange? = null,
)

/**
 * The explanation of today's ayah: the ayah itself, then a short tafsir in the user's language
 * (Al-Mukhtasar fi Tafsir al-Quran, Tafsir Center for Quranic Studies, in Arabic and in its own English edition; it explains each ayah on its own). Opened from the
 * Home card; the Quran itself stays one tap away on its own tab.
 */
@Composable
fun AyahExplainScreen(
    surah: Int,
    ayah: Int,
    onBack: () -> Unit,
    /** Opens the "choose any ayah" search. */
    onChooseAnother: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ar = LocalAppLanguage.current == "ar"
    val sky = LocalSky.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val page by produceState<AyahPage?>(null, surah, ayah, ar) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val repo = QuranRepository(context.applicationContext)
                val chapter = repo.getChapters().first { it.id == surah }
                val verse = repo.getSurahVerses(surah).verses[ayah - 1]
                val ex = io.github.sammynashed.emran.data.AyahTafsir.get(context.applicationContext, surah, ayah)
                val tafsir = (if (ar) ex?.tafsirAr else ex?.tafsirEn)?.ifBlank { null }
                AyahPage(chapter.name, chapter.transliteration, verse.text, ex?.translation?.ifBlank { null }, tafsir, ex?.group)
            }.getOrNull()
        }
    }

    Column(
        modifier.fillMaxSize().padding(horizontal = Space.screen).padding(bottom = 112.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = Space.sm, bottom = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBackButton(onBack)
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(if (ar) "آية اليوم" else "Ayah of the day", color = sky.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                page?.let {
                    Text(
                        if (ar) "سورة ${it.surahAr} · الآية $ayah" else "${it.surahEn} $surah:$ayah",
                        color = sky.inkSoft, fontSize = 14.sp,
                    )
                }
            }
            page?.let { p ->
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            ShareCard.share(
                                context,
                                ShareCard.Content(
                                    arabic = p.arabic,
                                    english = p.explanation,
                                    quran = true,
                                    source = if (ar) "القرآن الكريم · سورة ${p.surahAr} · $surah:$ayah"
                                    else "Qur'an · ${p.surahEn} $surah:$ayah · Al-Mukhtasar",
                                ),
                                lang = if (ar) "ar" else "en",
                                withCaption = false,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Share, if (ar) "مشاركة" else "Share", tint = sky.ink, modifier = Modifier.size(22.dp)) }
            }
        }

        val p = page
        if (p != null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                // The ayah.
                GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            p.arabic, color = sky.ink, fontSize = 28.sp, lineHeight = 46.sp,
                            fontFamily = MushafFontFamily, textAlign = TextAlign.Center, bypassNumerals = true,
                        )
                        if (p.translation != null && !ar) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "“${standaloneTranslation(p.translation)}”", color = sky.inkSoft, fontSize = 17.sp, lineHeight = 25.sp, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                // The explanation.
                if (p.explanation != null) {
                    GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 28.dp, tint = 0.8f) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                            Text(
                                if (ar) "التفسير" else "Explanation",
                                color = sky.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            )
                            p.group?.let { g ->
                                // Al-Mukhtasar explains a few ayat together; say so, so it never reads as a mismatch.
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (ar) "تفسير الآيات ${g.first}–${g.last} معًا" else "Explains ayat ${g.first}–${g.last} together",
                                    color = sky.inkSoft, fontSize = 13.sp,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                p.explanation, color = sky.ink, fontSize = if (ar) 19.sp else 17.sp,
                                lineHeight = if (ar) 32.sp else 26.sp,
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                if (ar) "المختصر في تفسير القرآن الكريم، مركز تفسير للدراسات القرآنية"
                                else "Al-Mukhtasar in Interpreting the Noble Quran (Tafsir Center for Quranic Studies), via QuranEnc.com",
                                color = sky.inkSoft, fontSize = 12.sp, lineHeight = 17.sp,
                            )
                        }
                    }
                }
                // Any ayah of the Qur'an can be today's ayah; the choice lasts until midnight.
                val today = java.time.LocalDate.now()
                val pickedToday = remember(io.github.sammynashed.emran.data.DailyAyat.version.intValue) {
                    io.github.sammynashed.emran.data.DailyAyat.picked(context, today)
                }
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    io.github.sammynashed.emran.ui.design.GlassButton(
                        label = if (ar) "اختر آية أخرى" else "Choose another ayah",
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChooseAnother() },
                        filled = true,
                    )
                    if (pickedToday != null) {
                        Spacer(Modifier.height(8.dp))
                        io.github.sammynashed.emran.ui.design.GlassButton(
                            label = if (ar) "العودة إلى آية اليوم" else "Back to today's ayah",
                            onClick = {
                                io.github.sammynashed.emran.data.DailyAyat.pick(context, null, null)
                                kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
                                    runCatching { io.github.sammynashed.emran.glance.WidgetUpdater.updateAllWidgets(context.applicationContext) }
                                }
                                onBack()
                            },
                            filled = false,
                        )
                    }
                }
            }
        }
    }
}
