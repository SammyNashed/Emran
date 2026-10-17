package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.audio.AudioDownloadWorker
import io.github.sammynashed.emran.data.audio.AudioStore
import io.github.sammynashed.emran.data.audio.QuranAudio
import io.github.sammynashed.emran.data.audio.Reciters
import kotlinx.coroutines.launch

/**
 * Listening, in the reader's tool panel: one big button that does the next sensible thing
 * (download this surah, showing its size first, or play it from the page you are on), and while it
 * plays, the usual previous / pause / next / stop. The voice is the one chosen on the Reciters page.
 */
@Composable
fun QuranListenRow(
    surah: Int,
    surahName: String,
    /** Words in the surah, for the download size. */
    surahWords: Int,
    /** First ayah of this surah on the page in view, where "play" starts. */
    startAyah: Int,
    theme: QuranThemeMode,
    lang: String,
    onChangeReciter: () -> Unit,
) {
    val ar = lang == "ar"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val changes by AudioStore.changes.collectAsState()
    val reciter = remember(changes) { Reciters.chosen(context) }
    val installed = remember(changes, reciter, surah) { AudioStore.isInstalled(context, reciter.id, surah) }
    val progressFlow = remember(reciter) { AudioDownloadWorker.progress(context, reciter) }
    val progress by progressFlow.collectAsState(initial = null)
    val now by QuranAudio.state.collectAsState()
    val ink = theme.textColor

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (ar) reciter.ar else reciter.en,
                color = ink.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1,
            )
            TextButton(onClick = onChangeReciter) {
                Text(if (ar) "تغيير القارئ" else "Change voice", color = ink.copy(alpha = 0.75f), fontSize = 13.sp)
            }
        }

        val playingHere = now?.let { it.surah == surah && it.reciter == reciter.id } == true
        when {
            playingHere -> {
                // Media controls read left-to-right in every language, like on any player.
                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { QuranAudio.stop() }) {
                            Icon(Icons.Filled.Close, if (ar) "إيقاف" else "Stop", tint = ink)
                        }
                        // In Arabic the next ayah sits to the left, as pages turn.
                        IconButton(onClick = { if (ar) QuranAudio.next() else QuranAudio.previous() }) {
                            Icon(Icons.Filled.SkipPrevious, if (ar) "الآية التالية" else "Previous ayah", tint = ink)
                        }
                        Button(
                            onClick = { QuranAudio.togglePause() },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = theme.backgroundColor),
                            modifier = Modifier.height(52.dp).width(88.dp),
                        ) {
                            Icon(if (now?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ar) "إيقاف مؤقت" else "Pause")
                        }
                        IconButton(onClick = { if (ar) QuranAudio.previous() else QuranAudio.next() }) {
                            Icon(Icons.Filled.SkipNext, if (ar) "الآية السابقة" else "Next ayah", tint = ink)
                        }
                        Spacer(Modifier.size(48.dp))
                    }
                }
            }
            installed -> Button(
                onClick = { scope.launch { runCatching { QuranAudio.play(context, reciter, surah, surahName, startAyah) } } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = theme.backgroundColor),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (startAyah > 1) (if (ar) "استمع من الآية $startAyah" else "Listen from ayah $startAyah")
                    else (if (ar) "استمع إلى السورة" else "Listen to the surah"),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            progress != null -> {
                val (done, total) = progress!!
                Text(
                    if (ar) "جارٍ التنزيل… ${if (total > 0) done * 100 / total else 0}%" else "Downloading… ${if (total > 0) done * 100 / total else 0}%",
                    color = ink.copy(alpha = 0.8f), fontSize = 13.sp,
                )
                LinearProgressIndicator(
                    progress = { if (total > 0) done.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = ink, trackColor = ink.copy(alpha = 0.15f),
                )
            }
            else -> {
                val mb = (AudioStore.surahBytes(reciter, surahWords) / 1_000_000.0).let { if (it < 1) 1 else Math.round(it).toInt() }
                Button(
                    onClick = { AudioDownloadWorker.enqueue(context, reciter, listOf(surah)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = ink.copy(alpha = 0.08f), contentColor = ink),
                ) {
                    Icon(Icons.Filled.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (ar) "نزّل السورة · $mb م.ب" else "Download · $mb MB",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
