package io.github.sammynashed.emran.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.audio.AudioDownloadWorker
import io.github.sammynashed.emran.data.audio.AudioStore
import io.github.sammynashed.emran.data.audio.QuranAudio
import io.github.sammynashed.emran.data.audio.Reciter
import io.github.sammynashed.emran.data.audio.Reciters
import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.GlassIconButton
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Radius
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text

/**
 * Pick a voice. Every reciter has a few seconds of Al-Fatiha built into the app, so you can hear
 * them with no download at all; tapping a row makes them your reciter. Your reciter's row also
 * holds their audio on this phone: download the whole Qur'an (its size said first), or delete it.
 * Single surahs are downloaded from the reader, where you are about to listen.
 */
@Composable
fun RecitersScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ar = LocalAppLanguage.current == "ar"
    val context = LocalContext.current
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    var chosen by remember { mutableStateOf(Reciters.chosen(context).id) }
    var playing by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val changes by AudioStore.changes.collectAsState()
    var confirmWhole by remember { mutableStateOf<Reciter?>(null) }
    var confirmDelete by remember { mutableStateOf<Reciter?>(null) }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playing = null
    }
    fun toggle(r: Reciter) {
        if (playing == r.id) { stop(); return }
        stop()
        runCatching {
            context.assets.openFd(r.previewAsset).use { fd ->
                val mp = MediaPlayer()
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                mp.setOnCompletionListener { if (player === mp) stop() }
                mp.prepare()
                mp.start()
                player = mp
                playing = r.id
            }
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() } } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen),
    ) {
        ScreenHeader(
            title = if (ar) "القرّاء" else "Reciters",
            subtitle = if (ar) "اسمع الفاتحة بصوت كل قارئ، ثم اختر صوتك" else "Hear Al-Fatiha in each voice, then pick yours",
            onBack = onBack,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Reciters.ALL.forEach { r ->
                val isChosen = chosen == r.id
                val surahsHere = remember(changes, r) { AudioStore.installedSurahs(context, r.id).size }
                val progressFlow = remember(r) { AudioDownloadWorker.progress(context, r) }
                val progress by progressFlow.collectAsState(initial = null)
                GlassPanel(
                    modifier = Modifier.fillMaxWidth().clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        Reciters.choose(context, r)
                        chosen = r.id
                        AudioStore.notifyChanged() // the reader's Listen row follows the new voice
                    },
                    cornerRadius = Radius.row,
                    lens = false,
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassIconButton(
                                icon = if (playing == r.id) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (ar) "استماع" else "Preview",
                                onClick = { toggle(r) },
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (ar) r.ar else r.en, color = sky.ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                if (isChosen) {
                                    Text(if (ar) "قارئك" else "Your reciter", color = sky.inkSoft, fontSize = 13.sp)
                                }
                            }
                            if (isChosen) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = sky.ink)
                            }
                        }
                        if (isChosen || surahsHere > 0 || progress != null) {
                            ReciterStorage(r, ar, surahsHere, progress, onWhole = { confirmWhole = r }, onDelete = { confirmDelete = r })
                        }
                    }
                }
            }
        }
    }

    confirmWhole?.let { r ->
        GlassDialog(
            onDismiss = { confirmWhole = null },
            title = if (ar) "القرآن كاملًا بصوت ${r.ar}" else "The whole Qur'an by ${r.en}",
            confirmLabel = if (ar) "نزّل" else "Download",
            onConfirm = {
                AudioDownloadWorker.enqueue(context, r, (1..114).toList())
                confirmWhole = null
            },
            dismissLabel = if (ar) "إلغاء" else "Cancel",
        ) {
            val size = sizeLabel(AudioStore.wholeQuranBytes(r), ar)
            Text(
                if (ar) "الحجم نحو $size. يُفضَّل التنزيل على شبكة واي فاي. يكمل وحده لو أغلقت التطبيق، ويمكنك إيقافه من هنا."
                else "About $size. Wi-Fi is best. It carries on if you close the app, and you can stop it here.",
                color = sky.ink, fontSize = 15.sp, lineHeight = 22.sp,
            )
        }
    }
    confirmDelete?.let { r ->
        GlassDialog(
            onDismiss = { confirmDelete = null },
            title = if (ar) "حذف تلاوة ${r.ar}؟" else "Delete ${r.en}'s audio?",
            confirmLabel = if (ar) "احذف" else "Delete",
            onConfirm = {
                AudioDownloadWorker.cancel(context, r)
                if (QuranAudio.state.value?.reciter == r.id) QuranAudio.stop()
                AudioStore.remove(context, r.id)
                confirmDelete = null
            },
            dismissLabel = if (ar) "إلغاء" else "Cancel",
        ) {
            val size = sizeLabel(AudioStore.bytesUsed(context, r.id), ar)
            Text(
                if (ar) "تُحذف كل السور المنزّلة بصوته من هاتفك ($size). يمكنك تنزيلها من جديد متى شئت."
                else "Every surah downloaded in this voice leaves your phone ($size). You can download them again any time.",
                color = sky.ink, fontSize = 15.sp, lineHeight = 22.sp,
            )
        }
    }
}

private fun sizeLabel(bytes: Long, ar: Boolean): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) "%.1f".format(java.util.Locale.US, mb / 1000) + (if (ar) " غ.ب" else " GB")
    else "${Math.round(mb).coerceAtLeast(1)}" + (if (ar) " م.ب" else " MB")
}

/** A reciter's audio on this phone: what is here, what is downloading, and what you can do. */
@Composable
private fun ReciterStorage(
    r: Reciter,
    ar: Boolean,
    surahsHere: Int,
    progress: Pair<Int, Int>?,
    onWhole: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val sky = LocalSky.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        if (progress != null) {
            val (done, total) = progress
            val pct = if (total > 0) done * 100 / total else 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (ar) "جارٍ التنزيل… $pct%" else "Downloading… $pct%", color = sky.inkSoft, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { AudioDownloadWorker.cancel(context, r) }) {
                    Text(if (ar) "إيقاف" else "Stop", color = sky.ink, fontSize = 13.sp)
                }
            }
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = sky.ink, trackColor = sky.ink.copy(alpha = 0.15f),
            )
            return@Column
        }
        if (surahsHere > 0) {
            Text(
                if (surahsHere == 114) (if (ar) "القرآن كاملًا على هاتفك" else "The whole Qur'an is on your phone")
                else (if (ar) "$surahsHere سورة على هاتفك" else "$surahsHere surah(s) on your phone"),
                color = sky.inkSoft, fontSize = 13.sp,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (surahsHere < 114) {
                TextButton(onClick = onWhole) {
                    Text(
                        (if (ar) "نزّل القرآن كاملًا · نحو " else "Download the whole Qur'an · about ") + sizeLabel(AudioStore.wholeQuranBytes(r), ar),
                        color = sky.ink, fontSize = 13.sp,
                    )
                }
            }
            if (surahsHere > 0) {
                TextButton(onClick = onDelete) { Text(if (ar) "حذف" else "Delete", color = sky.ink, fontSize = 13.sp) }
            }
        }
    }
}
