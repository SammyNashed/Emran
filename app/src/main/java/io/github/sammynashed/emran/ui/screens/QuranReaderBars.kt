package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.audio.QuranAudio
import io.github.sammynashed.emran.data.audio.Reciters

/** Which panel the reader's bottom bar has open. */
enum class ReaderPanel { LISTEN, DISPLAY }

/** Height of [QuranBottomBar] above the system navigation area; panels sit on top of it. */
val ReaderBarHeight = 80.dp

/**
 * The reader's bottom bar: three labelled buttons that are always there, so nothing has to be
 * discovered by tapping the page. Listen and Display open their panel (tap again to close);
 * Hifz switches the mode itself and lights up while it is on.
 */
@Composable
fun QuranBottomBar(
    theme: QuranThemeMode,
    lang: String,
    open: ReaderPanel?,
    hifzOn: Boolean,
    onListen: () -> Unit,
    onHifz: () -> Unit,
    onDisplay: () -> Unit,
) {
    val ar = lang == "ar"
    Row(
        Modifier
            .fillMaxWidth()
            .background(theme.backgroundColor)
            .drawBehind {
                drawLine(
                    theme.ornament.copy(alpha = 0.45f),
                    start = Offset(0f, 0.5.dp.toPx()), end = Offset(size.width, 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .navigationBarsPadding()
            .height(ReaderBarHeight)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.Outlined.Headphones, if (ar) "استماع" else "Listen", open == ReaderPanel.LISTEN, theme, onListen, Modifier.weight(1f))
        BarButton(Icons.Outlined.Mic, if (ar) "الحفظ" else "Hifz", hifzOn, theme, onHifz, Modifier.weight(1f))
        BarButton(Icons.Outlined.Tune, if (ar) "العرض" else "Display", open == ReaderPanel.DISPLAY, theme, onDisplay, Modifier.weight(1f))
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, on: Boolean, theme: QuranThemeMode, onClick: () -> Unit, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val ink = if (on) theme.ornament else theme.textColor.copy(alpha = 0.78f)
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) theme.ornament.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = ink, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

/**
 * While a recitation plays: who is reciting and which ayah, with previous / pause / next / stop,
 * in a strip just above the bottom bar. It stays out of the way of the page and needs no panel.
 */
@Composable
fun QuranMiniPlayer(theme: QuranThemeMode, lang: String, modifier: Modifier = Modifier, onOpen: () -> Unit = {}) {
    val now by QuranAudio.state.collectAsState()
    val n = now ?: return
    val ar = lang == "ar"
    val reciter = remember(n.reciter) { Reciters.ALL.firstOrNull { it.id == n.reciter } }
    val ink = theme.textColor
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(androidx.compose.ui.graphics.lerp(theme.backgroundColor, theme.textColor, 0.08f))
            .clickable { onOpen() } // the full player
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                reciter?.let { if (ar) it.ar else it.en } ?: "",
                color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (n.ayah == 0) (if (ar) "البسملة" else "Bismillah") else (if (ar) "الآية ${n.ayah}" else "Ayah ${n.ayah}"),
                color = ink.copy(alpha = 0.65f), fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(4.dp))
        // Media controls read left-to-right on every player; in Arabic "next" is on the left, as pages turn.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (ar) QuranAudio.next() else QuranAudio.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, if (ar) "الآية التالية" else "Previous ayah", tint = ink)
                }
                IconButton(onClick = { QuranAudio.togglePause() }) {
                    Icon(if (n.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ar) "إيقاف مؤقت" else "Pause", tint = ink)
                }
                IconButton(onClick = { if (ar) QuranAudio.previous() else QuranAudio.next() }) {
                    Icon(Icons.Filled.SkipNext, if (ar) "الآية السابقة" else "Next ayah", tint = ink)
                }
                IconButton(onClick = { QuranAudio.stop() }) {
                    Icon(Icons.Filled.Close, if (ar) "إيقاف" else "Stop", tint = ink.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/**
 * "Where did you stop?": the ayah number in this surah, prefilled with the likeliest one, then
 * Save. When that ayah is already marked, the same button removes the mark. Drawn in the page's
 * own colours, like the rest of the reader.
 */
@Composable
fun AyahBookmarkDialog(
    surahName: String,
    ayahCount: Int,
    initialAyah: Int,
    isMarked: (Int) -> Boolean,
    /** The ayah this surah is already marked at, if any; a new number replaces it. */
    currentMark: Int?,
    theme: QuranThemeMode,
    lang: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val ar = lang == "ar"
    val ink = theme.textColor
    var text by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(initialAyah.toString()) }
    // Arabic-Indic digits typed on an Arabic keyboard count too.
    val ayah = text.map { c -> if (c in '٠'..'٩') '0' + (c - '٠') else c }.joinToString("").toIntOrNull()
    val valid = ayah != null && ayah in 1..ayahCount
    val marked = valid && isMarked(ayah!!)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.lerp(theme.backgroundColor, theme.textColor, 0.05f),
        title = {
            Text(
                if (ar) "وقفت عند أي آية؟" else "Which ayah did you stop at?",
                color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    if (ar) "سورة $surahName · من 1 إلى $ayahCount" else "$surahName · 1 to $ayahCount",
                    color = ink.copy(alpha = 0.65f), fontSize = 14.sp,
                )
                if (currentMark != null) {
                    Text(
                        if (ar) "علامتك الآن عند الآية $currentMark، والرقم الجديد يحلّ محلّها."
                        else "Your mark is at ayah $currentMark now; a new number replaces it.",
                        color = theme.ornament, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.ornament, unfocusedBorderColor = ink.copy(alpha = 0.25f), cursorColor = ink,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { if (valid) onConfirm(ayah!!) }, enabled = valid) {
                Text(
                    when {
                        marked -> if (ar) "إزالة العلامة" else "Remove bookmark"
                        currentMark != null -> if (ar) "انقل العلامة" else "Move bookmark"
                        else -> if (ar) "احفظ العلامة" else "Save bookmark"
                    },
                    color = if (valid) theme.ornament else ink.copy(alpha = 0.35f), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(if (ar) "إلغاء" else "Cancel", color = ink.copy(alpha = 0.75f), fontSize = 16.sp)
            }
        },
    )
}
