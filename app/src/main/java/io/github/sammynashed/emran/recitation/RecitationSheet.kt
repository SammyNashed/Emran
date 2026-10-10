package io.github.sammynashed.emran.recitation

import io.github.sammynashed.emran.ui.components.EmranAlertDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One ayah offered to the checker. */
data class RecitationAyah(val chapterId: Int, val ayahId: Int, val arabic: String)

/**
 * The recitation model: whether it is installed, and whether you want it.
 *
 * This sheet does not assess anything. Reciting happens in Hifz mode on the
 * muṣḥaf page itself, where the words are; a second recitation surface behind
 * the microphone icon was the same feature told worse, and it is gone.
 *
 * What is left is the one decision this file is for. The model is a ~31 MB
 * download that Emran never fetches on its own, so the sheet has to state
 * plainly what it costs, what it is for, and that the alternative - importing
 * the file by hand, with no network at all - exists and always works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecitationSheet(
    lang: String,
    onDismiss: () -> Unit,
) {
    val s = remember(lang) { RecitationStrings.forLanguage(lang) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val assessor = remember { RecitationAssessor(context) }

    var installed by remember { mutableStateOf(assessor.isInstalled) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var retryable by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var slowConnect by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    var soFar by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(-1L) }
    val downloader = remember { ModelDownloader(context) }

    val pickModel = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { assessor.importModel(uri) }
                busy = false
                r.fold(
                    {
                        installed = true
                        problem = null
                    },
                    { problem = it.message },
                )
            }
        }
    }

    // A connection that is merely slow and one that will never arrive look
    // identical for the first few seconds. Rather than guess, wait a beat and
    // then say what is worth checking - in some regions GitHub simply is not
    // reachable without a VPN, and nothing in the app can detect that.
    LaunchedEffect(connecting) {
        slowConnect = false
        if (connecting) {
            kotlinx.coroutines.delay(SLOW_CONNECT_MS)
            slowConnect = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { scope.launch { assessor.release() } }
    }

    val scheme = MaterialTheme.colorScheme

    // Hoisted out of the confirm dialog so Retry can run the same thing
    // without making the user re-read and re-accept the consent copy - they
    // already agreed to this download; it failed on us, not on them.
    val startDownload = {
        downloading = true
        connecting = true
        problem = null
        retryable = false
        fraction = 0f
        paused = false
        soFar = 0L
        totalBytes = -1L
        scope.launch {
                        downloader.download(assessor).collect { p ->
                            when (p) {
                                is ModelDownloader.Progress.Connecting -> {
                                    connecting = true
                                    paused = false
                                }
                                is ModelDownloader.Progress.Running -> {
                                    connecting = false
                                    paused = false
                                    fraction = p.fraction
                                    soFar = p.bytes
                                    totalBytes = p.total
                                }
                                is ModelDownloader.Progress.Paused -> {
                                    connecting = false
                                    paused = true
                                    fraction = p.fraction
                                    soFar = p.bytes
                                    totalBytes = p.total
                                }
                                is ModelDownloader.Progress.Verifying -> {
                                    connecting = false
                                    downloading = false; verifying = true; paused = false
                                }
                                is ModelDownloader.Progress.Installed -> {
                                    connecting = false
                                    downloading = false; verifying = false; installed = true
                                }
                                is ModelDownloader.Progress.Cancelled -> {
                                    connecting = false
                                    downloading = false; verifying = false; paused = false
                                    fraction = 0f
                                    problem = s.downloadCancelled
                                    retryable = true
                                }
                                is ModelDownloader.Progress.Failed -> {
                                    connecting = false
                                    downloading = false; verifying = false; paused = false
                                    problem = failureMessage(s, p.reason)
                                    // A wrong file will be wrong again, and a
                                    // missing one stays missing; offering to
                                    // repeat a 31 MB mistake is not help.
                                    retryable = p.reason == ModelDownloader.Reason.UNREACHABLE ||
                                        p.reason == ModelDownloader.Reason.INTERRUPTED
                                }
                            }
                        }
                    }
        Unit
    }

    if (confirming) {
        EmranAlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(s.confirmTitle) },
            text = { Text(s.confirmBody) },
            confirmButton = {
                M3TextButton(onClick = { confirming = false; startDownload() }) {
                    Text(s.confirmYes)
                }
            },
            dismissButton = {
                M3TextButton(onClick = { confirming = false }) { Text(s.confirmNo) }
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .animateContentSize(),
        ) {
            Text(
                s.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        s.privateTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                    Text(
                        s.privateBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = scheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            if (!installed) {
                Text(
                    s.modelTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(s.modelBody, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                when {
                    downloading || verifying -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WavyProgressBar(
                                progress = if (verifying) 1f else fraction,
                                // Flat and still while held: the state is
                                // legible before the label is read.
                                animating = verifying || !paused,
                                // Nothing is known about the size yet, so the
                                // bar sweeps rather than sitting at a zero it
                                // cannot justify.
                                indeterminate = connecting && !verifying,
                                modifier = Modifier.weight(1f),
                            )
                            // Verifying is a few hundred milliseconds of
                            // hashing with nothing left to stop, so the
                            // controls belong to the transfer only.
                            if (downloading) {
                                Spacer(Modifier.width(4.dp))
                                // There is nothing to hold until bytes are
                                // moving, but there is always something to
                                // abandon - so cancel is offered from the
                                // first instant and pause only once it means
                                // something.
                                if (!connecting) {
                                    IconButton(onClick = {
                                        if (paused) downloader.resume() else downloader.pause()
                                    }) {
                                        Icon(
                                            if (paused) {
                                                Icons.Filled.PlayArrow
                                            } else {
                                                Icons.Filled.Pause
                                            },
                                            if (paused) s.resume else s.pause,
                                            tint = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { downloader.cancel() }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        s.cancelDownload,
                                        tint = scheme.error,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            downloadLabel(
                                s, verifying, connecting, paused, fraction, soFar, totalBytes,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        if (connecting && slowConnect) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                s.connectingSlow,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        FilledTonalButton(
                            onClick = { confirming = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !busy,
                        ) { Text(s.downloadModel) }
                        Spacer(Modifier.height(2.dp))
                        // Kept so anyone who would rather Emran never touched the
                        // network at all still has a way in.
                        M3TextButton(
                            onClick = { pickModel.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                        ) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(s.checkingFile)
                            } else {
                                Text(s.importInstead)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(s.importNote, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                problem?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error)
                    if (retryable && !downloading && !verifying) {
                        M3TextButton(onClick = { startDownload() }) { Text(s.retry) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(s.whyTitle, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null, Modifier.size(18.dp), tint = scheme.primary,
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(scheme.surfaceVariant, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        listOf(s.why1, s.why2, s.why3).forEachIndexed { i, para ->
                            if (i > 0) Spacer(Modifier.height(10.dp))
                            Text(
                                para,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                return@Column
            }

            // ---------- installed ----------
            // Nothing here recites. Reciting is Hifz mode, on the muṣḥaf page
            // itself; this sheet only answers "is the model here, and how much
            // room is it taking". A second, lesser recitation screen behind the
            // microphone icon was the same feature told worse.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        s.ready,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface,
                    )
                    Text(
                        modelSize(assessor.installedBytes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                s.installedBody,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = {
                    assessor.removeModel()
                    installed = false
                    problem = null
                    retryable = false
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text(s.removeModel) }

            Spacer(Modifier.height(6.dp))
            Text(
                s.removeNote,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** The model's footprint on disk, in the units a storage screen would use. */
private fun modelSize(bytes: Long): String =
    if (bytes <= 0) "" else "%.1f MB".format(Locale.US, bytes / 1_048_576.0)

/**
 * What a failed download says.
 *
 * Each reason asks something different of the reader - check the connection,
 * simply try again, or stop trying and import the file - so they get different
 * sentences. Every one of them names the import fallback, because that path
 * always works and never depends on the network.
 */
private fun failureMessage(s: RecitationStrings, reason: ModelDownloader.Reason): String =
    when (reason) {
        ModelDownloader.Reason.UNREACHABLE -> s.downloadUnreachable
        ModelDownloader.Reason.INTERRUPTED -> s.downloadInterrupted
        ModelDownloader.Reason.SERVER -> s.downloadServerError
        ModelDownloader.Reason.VERIFY -> s.verifyFailed
    }

/** How long a wait has to run before it is worth explaining. */
private const val SLOW_CONNECT_MS = 6_000L

/**
 * What the line under the bar says.
 *
 * The byte counts are shown because a 31 MB transfer that only reports a
 * percentage gives no way to tell a slow connection from a stalled one, and
 * "stalled with no explanation" is the failure this download already had once.
 */
private fun downloadLabel(
    s: RecitationStrings,
    verifying: Boolean,
    connecting: Boolean,
    paused: Boolean,
    fraction: Float,
    soFar: Long,
    total: Long,
): String {
    if (verifying) return s.verifying
    if (connecting) return s.connecting
    val head = if (paused) s.paused else s.downloading
    // With no declared size there is no percentage to give, so report the one
    // number that is true: what has actually arrived.
    if (total <= 0) return "$head  ${megabytes(soFar)} MB"
    val percent = (fraction * 100).toInt()
    return "$head  $percent%  ·  ${megabytes(soFar)} / ${megabytes(total)} MB"
}

private fun megabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f", bytes / 1_048_576f)
