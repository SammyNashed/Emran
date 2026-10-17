package io.github.sammynashed.emran.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.BuildConfig
import kotlinx.coroutines.launch
import io.github.sammynashed.emran.ui.design.EmranMark

import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.localization.Text

// The "About" family of Settings, in the glass dialog style. Every fact in here is checked
// against the project itself (licence file, dependencies, BuildConfig), so nothing claims
// more than Emran is.

private fun Context.openUrl(url: String) {
    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
}

/** A soft inset card inside a dialog: optional small heading, then whatever it holds. */
@Composable
private fun InfoCard(heading: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val sky = LocalSky.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(sky.ink.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (heading != null) {
            Text(heading, color = sky.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun Body(text: String) {
    val sky = LocalSky.current
    Text(text, color = sky.ink, fontSize = 15.sp, lineHeight = 22.sp)
}

@Composable
private fun Fine(text: String) {
    val sky = LocalSky.current
    Text(text, color = sky.inkSoft, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun FeatureLine(icon: ImageVector, text: String) {
    val sky = LocalSky.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = sky.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = sky.ink, fontSize = 15.sp, lineHeight = 20.sp)
    }
}

@Composable
fun AboutEmranDialog(ar: Boolean, onDismiss: () -> Unit) {
    val sky = LocalSky.current
    GlassDialog(onDismiss = onDismiss, title = if (ar) "عن عِمران" else "About Emran", dismissLabel = if (ar) "إغلاق" else "Close") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmranMark(72.dp)
            Spacer(Modifier.height(10.dp))
            Text(if (ar) "عِمران" else "Emran", color = sky.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (ar) "الصلاة عِمران الروح" else "The prosperity of the soul",
                color = sky.inkSoft, fontSize = 14.sp,
            )
            Text("v${BuildConfig.VERSION_NAME}", color = sky.inkSoft, fontSize = 12.sp, bypassNumerals = true)
        }
        Spacer(Modifier.height(8.dp))
        Body(
            if (ar) "رفيق إسلامي بسيط ومفتوح المصدر: مواقيت الصلاة والأذان، والقرآن والحفظ، والأذكار والحديث."
            else "A simple, open-source Islamic companion: prayer times and adhan, the Quran and Hifz, adhkar and hadith.",
        )
        Spacer(Modifier.height(6.dp))
        InfoCard {
            FeatureLine(Icons.Outlined.WbTwilight, if (ar) "سماء حيّة تتبع أوقات الصلاة" else "A living sky that follows the prayers")
            FeatureLine(Icons.Outlined.NotificationsActive, if (ar) "أذان في وقته بدقة" else "Adhan right on time")
            FeatureLine(Icons.AutoMirrored.Outlined.MenuBook, if (ar) "المصحف بأحكام التجويد" else "The muṣḥaf with tajweed colours")
            FeatureLine(Icons.Outlined.Mic, if (ar) "الحفظ: تسمع تلاوتك وتكشف الكلمات" else "Hifz: listens and reveals the words")
            FeatureLine(Icons.Outlined.Explore, if (ar) "بوصلة القبلة والكاميرا" else "Qibla compass and camera")
            FeatureLine(Icons.Outlined.TouchApp, if (ar) "التسبيح، والأذكار، والأحاديث" else "Tasbeeh, adhkar and hadith")
            FeatureLine(Icons.Outlined.WifiOff, if (ar) "يعمل دون إنترنت" else "Works fully offline")
            FeatureLine(Icons.Outlined.Language, if (ar) "بالعربية والإنجليزية" else "In Arabic and English")
        }
    }
}

@Composable
fun AboutDeveloperDialog(ar: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    GlassDialog(onDismiss = onDismiss, title = if (ar) "عن المطوّر" else "About the developer", dismissLabel = if (ar) "إغلاق" else "Close") {
        Body(
            if (ar) "أنا سامي ناشد، أبني تطبيقات تخدم المسلمين، تحترم خصوصيتهم وتجمع بين إتقان الصنعة وجمال المعنى."
            else "I'm Sammy Nashed. I build apps that serve Muslims, respect their privacy, and try to be both well made and beautiful.",
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LinkPill("LinkedIn", Modifier.weight(1f)) { context.openUrl("https://www.linkedin.com/in/sammy-nashed-480aaa318/") }
            LinkPill("GitHub", Modifier.weight(1f)) { context.openUrl("https://github.com/SammyNashed") }
        }
    }
}

/** A quiet full-width pill that opens a link. */
@Composable
private fun LinkPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(sky.ink.copy(alpha = 0.10f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = sky.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, bypassNumerals = true) }
}

@Composable
fun OpenSourceDialog(ar: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    GlassDialog(onDismiss = onDismiss, title = if (ar) "مفتوح المصدر" else "Open source", dismissLabel = if (ar) "إغلاق" else "Close") {
        Body(
            if (ar) "كل سطر في عِمران منشور للجميع: يمكنك قراءته، والتأكد مما يفعله، والمساهمة فيه."
            else "Every line of Emran is public: you can read it, check what it does, and contribute.",
        )
        Spacer(Modifier.height(6.dp))
        InfoCard(if (ar) "الترخيص" else "Licence") {
            Body("GNU GPL v3")
            Fine(
                if (ar) "لك أن تستخدمه وتدرسه وتعدّله وتشاركه بحرية، بشرط أن تبقى أي نسخة معدّلة تنشرها مفتوحة المصدر بالترخيص نفسه."
                else "You're free to use, study, change and share it, as long as any changed version you share stays open source under the same licence.",
            )
        }
        InfoCard(if (ar) "النص والخطوط" else "Text and fonts") {
            Fine(
                if (ar) "نص المصحف وخطه: مجمع الملك فهد لطباعة المصحف الشريف. تفسير آية اليوم: المختصر في تفسير القرآن الكريم، مركز تفسير للدراسات القرآنية (QuranEnc.com). الترجمة الإنجليزية: صحيح إنترناشيونال. خطوط Amiri وNoto Naskh وReadex Pro وOutfit وScheherazade New بترخيص SIL OFL 1.1."
                else "Muṣḥaf text and font: King Fahd Glorious Qur'an Printing Complex. Ayah-of-the-day tafsir: Al-Mukhtasar, Tafsir Center for Quranic Studies (QuranEnc.com). English translation: Saheeh International. Amiri, Noto Naskh, Readex Pro, Outfit and Scheherazade New fonts under the SIL OFL 1.1.",
            )
        }
        InfoCard(if (ar) "مبني بـ" else "Built with") {
            Fine("Kotlin · Jetpack Compose · Room · WorkManager · Glance · Moshi · Coil · ONNX Runtime")
        }
        Spacer(Modifier.height(4.dp))
        LinkPill(if (ar) "افتح المشروع على GitHub" else "Open the project on GitHub", Modifier.fillMaxWidth()) {
            context.openUrl("https://github.com/SammyNashed/Emran")
        }
    }
}

@Composable
fun PrivacyDialog(ar: Boolean, onDismiss: () -> Unit) {
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "خصوصيتك" else "Your privacy",
        subtitle = if (ar) "ما بينك وبين ربك يبقى لك." else "What's between you and your Lord stays yours.",
        dismissLabel = if (ar) "إغلاق" else "Close",
    ) {
        val items = if (ar) listOf(
            "لا تتبّع" to "لا يتتبع عِمران موقعك ولا طريقة استخدامك.",
            "لا تحليلات ولا إعلانات" to "لا أدوات تحليل، ولا تقارير أعطال، ولا إعلانات.",
            "لا حساب" to "لا بريد ولا كلمة مرور. تفتح التطبيق وتبدأ.",
            "على هاتفك فقط" to "إعداداتك وأذكارك الخاصة وعلاماتك تبقى على جهازك.",
            "تلاوتك لا تغادر هاتفك" to "في وضع الحفظ يُحلَّل صوتك على جهازك فقط، ولا يُرسَل ولا يُحفَظ.",
            "دون إنترنت" to "كل شيء يعمل دون إنترنت، حتى الحفظ والتفسير وكتب الحديث كلها. لا يتصل إلا حين تطلب أنت: «التحقق من التحديثات»، أو تنزيل تلاوة قارئ تختاره.",
        ) else listOf(
            "No tracking" to "Emran doesn't track your location or how you use it.",
            "No analytics, no ads" to "No analytics, no crash reports, no adverts.",
            "No account" to "No email, no password. Open it and start.",
            "On your phone only" to "Your settings, your own dhikrs and your bookmarks stay on your device.",
            "Your recitation stays on your phone" to "In Hifz mode your voice is analysed on your device only. It is never sent or saved.",
            "Offline" to "Everything works offline, including Hifz, tafsir and every hadith book. It only goes online when you ask it to: Check for updates, or downloading a reciter's recitation.",
        )
        items.forEach { (t, d) -> InfoCard(t) { Fine(d) } }
    }
}

/** A newer version is out: send the user to the channel, where the APK is a normal download. */
@Composable
fun UpdateDialog(ar: Boolean, versionName: String, notes: String, channelUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "إصدار جديد" else "A new version",
        subtitle = "v$versionName",
        confirmLabel = if (ar) "افتح القناة" else "Open the channel",
        onConfirm = {
            onDismiss()
            if (!io.github.sammynashed.emran.data.update.UpdateChecker.openChannel(context)) {
                Toast.makeText(context, if (ar) "تعذّر فتح تيليجرام" else "Couldn't open Telegram", Toast.LENGTH_SHORT).show()
            }
        },
        dismissLabel = if (ar) "لاحقًا" else "Later",
    ) {
        if (notes.isNotBlank()) Body(notes)
        Fine(
            if (ar) "افتح القناة واضغط على أحدث ملف للتطبيق لتثبيته فوق القديم. بياناتك تبقى كما هي."
            else "Open the channel and tap the newest app file to install it over the old one. Your data stays as it is.",
        )
    }
}

/**
 * Shown when the update check could not reach GitHub (it is blocked or slow on some Syrian
 * networks without a VPN). The channel always works through the Telegram app, so offer it.
 */
@Composable
fun UpdateUnreachableDialog(ar: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "تعذّر التحقق من الإنترنت" else "Couldn't check online",
        confirmLabel = if (ar) "افتح القناة" else "Open the channel",
        onConfirm = {
            onDismiss()
            if (!io.github.sammynashed.emran.data.update.UpdateChecker.openChannel(context)) {
                Toast.makeText(context, if (ar) "تعذّر فتح تيليجرام" else "Couldn't open Telegram", Toast.LENGTH_SHORT).show()
            }
        },
        dismissLabel = if (ar) "إغلاق" else "Close",
    ) {
        Body(
            if (ar) "بعض الشبكات تحجب موقع التحديثات. افتح القناة لترى إن كان هناك إصدار أحدث، وإن وجدت ملفًا أحدث ثبّته فوق القديم وستبقى بياناتك."
            else "Some networks block the update site. Open the channel to see whether there is a newer version, and if there is, install it over this one; your data stays.",
        )
    }
}

/**
 * The version, plain. Seven taps on the card show the details a bug report needs (method,
 * location, device), so a user can read them out when something looks wrong.
 */
@Composable
fun VersionDialog(
    ar: Boolean,
    method: String,
    school: String,
    location: String,
    lat: Double,
    lon: Double,
    onDismiss: () -> Unit,
) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    var taps by remember { mutableIntStateOf(0) }
    GlassDialog(onDismiss = onDismiss, title = if (ar) "الإصدار" else "Version", dismissLabel = if (ar) "إغلاق" else "Close") {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(sky.ink.copy(alpha = 0.07f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    taps++
                }
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("v${BuildConfig.VERSION_NAME}", color = sky.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, bypassNumerals = true)
            Text(
                (if (ar) "البناء " else "Build ") + BuildConfig.VERSION_CODE,
                color = sky.inkSoft, fontSize = 13.sp, bypassNumerals = true, textAlign = TextAlign.Center,
            )
        }
        if (taps >= 7) {
            InfoCard(if (ar) "تفاصيل لبلاغ مشكلة" else "Details for a bug report") {
                listOf(
                    "Method" to method, "Asr" to school, "Location" to location,
                    "Coordinates" to "%.4f, %.4f".format(java.util.Locale.US, lat, lon),
                    "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                ).forEach { (k, v) -> Fine("$k: $v") }
            }
        }
    }
}

@Composable
fun ExitDialog(ar: Boolean, onExit: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "الخروج من عِمران؟" else "Leave Emran?",
        subtitle = if (ar) "سيصلك الأذان في وقته كالمعتاد." else "The adhan will still reach you on time.",
        confirmLabel = if (ar) "خروج" else "Leave",
        onConfirm = onExit,
        dismissLabel = if (ar) "البقاء" else "Stay",
    ) { }
}

/**
 * A small update file (from Telegram, or picked in Settings): read it, say plainly whether it fits
 * this phone, rebuild the new version from the installed one, and hand it to Android to install.
 * Shown over whatever screen is open, the moment a file arrives.
 */
@Composable
fun PatchUpdateHost(ar: Boolean) {
    val uri = io.github.sammynashed.emran.data.update.PatchUpdate.incoming.value ?: return
    val PU = io.github.sammynashed.emran.data.update.PatchUpdate
    val context = LocalContext.current
    val sky = LocalSky.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // checking → (verdict) → building → installing, or failed.
    var check by remember(uri) { androidx.compose.runtime.mutableStateOf<io.github.sammynashed.emran.data.update.PatchUpdate.Check?>(null) }
    var building by remember(uri) { androidx.compose.runtime.mutableStateOf(false) }
    var progress by remember(uri) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var failed by remember(uri) { androidx.compose.runtime.mutableStateOf(false) }
    var waitingForAndroid by remember(uri) { androidx.compose.runtime.mutableStateOf(false) }
    var needPermission by remember(uri) { androidx.compose.runtime.mutableStateOf(false) }
    val installError = PU.installError.value

    androidx.compose.runtime.LaunchedEffect(uri) { check = PU.check(context, uri) }
    // The user cancelled Android's screen (or it failed): offer the button again.
    androidx.compose.runtime.LaunchedEffect(installError) { if (installError != null) waitingForAndroid = false }

    fun close() {
        PU.incoming.value = null
        PU.installError.value = null
        if (!building) PU.cleanUp(context)
    }

    fun start(ready: io.github.sammynashed.emran.data.update.PatchUpdate.Check.Ready) {
        if (!PU.canInstall(context)) { needPermission = true; return }
        needPermission = false
        building = true; failed = false; PU.installError.value = null
        scope.launch {
            val apk = PU.build(context, ready.file) { progress = it }
            building = false
            if (apk == null) { failed = true; return@launch }
            waitingForAndroid = true
            runCatching { PU.install(context, apk) }.onFailure { waitingForAndroid = false; failed = true }
        }
    }

    // Back from Android's "Install unknown apps" page: carry on by itself if it was allowed.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle, needPermission) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && needPermission && PU.canInstall(context)) {
                (check as? io.github.sammynashed.emran.data.update.PatchUpdate.Check.Ready)?.let { start(it) }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val here = BuildConfig.VERSION_NAME
    when (val c = check) {
        null -> GlassDialog(onDismiss = {}, title = if (ar) "ملف تحديث" else "Update file") {
            Body(if (ar) "جارٍ قراءة الملف…" else "Reading the file…")
        }
        io.github.sammynashed.emran.data.update.PatchUpdate.Check.NotAPatch -> GlassDialog(
            onDismiss = ::close, title = if (ar) "ليس ملف تحديث" else "Not an update file", dismissLabel = if (ar) "إغلاق" else "Close",
        ) {
            Body(if (ar) "هذا الملف ليس تحديثًا لعِمران. ملفات التحديث تنتهي بـ ‎.emran‎ وتجدها في قناة عِمران."
            else "This file isn't an Emran update. Update files end in .emran and are posted on the Emran channel.")
        }
        io.github.sammynashed.emran.data.update.PatchUpdate.Check.AlreadyUpToDate -> GlassDialog(
            onDismiss = ::close, title = if (ar) "عندك أحدث نسخة" else "Already up to date", dismissLabel = if (ar) "إغلاق" else "Close",
        ) {
            Body(if (ar) "عِمران عندك هو الإصدار $here، وهذا الملف لا يضيف شيئًا جديدًا." else "You have Emran $here; this file has nothing newer.")
        }
        is io.github.sammynashed.emran.data.update.PatchUpdate.Check.WrongVersion -> GlassDialog(
            onDismiss = ::close,
            title = if (ar) "هذا الملف لإصدار آخر" else "This file is for another version",
            confirmLabel = if (ar) "افتح القناة" else "Open the channel",
            onConfirm = { close(); io.github.sammynashed.emran.data.update.UpdateChecker.openChannel(context) },
            dismissLabel = if (ar) "إغلاق" else "Close",
        ) {
            val from = c.header.fromVersionName.ifBlank { "?" }
            Body(
                if (ar) "هذا الملف يحدّث الإصدار $from إلى ${c.header.toVersionName}، وعندك $here. نزّل من القناة ملف التطبيق الكامل (APK) وثبّته فوق القديم، وبياناتك تبقى."
                else "This file updates $from to ${c.header.toVersionName}, and you have $here. Download the full app (APK) from the channel and install it over this one; your data stays.",
            )
        }
        is io.github.sammynashed.emran.data.update.PatchUpdate.Check.Ready -> {
            val to = c.header.toVersionName
            when {
                building -> GlassDialog(onDismiss = {}, title = if (ar) "تحديث إلى $to" else "Updating to $to") {
                    Body(if (ar) "جارٍ تجهيز التحديث… ${(progress * 100).toInt()}%" else "Preparing the update… ${(progress * 100).toInt()}%")
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = sky.ink, trackColor = sky.ink.copy(alpha = 0.15f),
                    )
                }
                waitingForAndroid -> GlassDialog(
                    onDismiss = ::close, title = if (ar) "تحديث إلى $to" else "Updating to $to", dismissLabel = if (ar) "إغلاق" else "Close",
                ) {
                    Body(if (ar) "اضغط «تحديث» في نافذة أندرويد. سيُغلق عِمران لحظة ثم يعود بالإصدار الجديد، وبياناتك كما هي."
                    else "Tap \"Update\" on Android's screen. Emran closes for a moment and comes back as the new version, with your data as it was.")
                }
                failed -> GlassDialog(
                    onDismiss = ::close, title = if (ar) "تعذّر التحديث" else "Couldn't update",
                    confirmLabel = if (ar) "افتح القناة" else "Open the channel",
                    onConfirm = { close(); io.github.sammynashed.emran.data.update.UpdateChecker.openChannel(context) },
                    dismissLabel = if (ar) "إغلاق" else "Close",
                ) {
                    Body(if (ar) "الملف ناقص أو تالف، فلم يتغيّر شيء في التطبيق. نزّله من القناة مرة أخرى، أو نزّل التطبيق الكامل."
                    else "The file is incomplete or damaged, so nothing in the app changed. Download it again from the channel, or get the full app.")
                }
                needPermission -> GlassDialog(
                    onDismiss = ::close, title = if (ar) "خطوة واحدة أولًا" else "One step first",
                    confirmLabel = if (ar) "افتح الإعدادات" else "Open settings",
                    onConfirm = { PU.openInstallPermission(context) },
                    dismissLabel = if (ar) "إلغاء" else "Cancel",
                ) {
                    Body(if (ar) "اسمح لعِمران بتثبيت التحديثات (مرة واحدة فقط): فعّل «السماح من هذا المصدر» ثم ارجع، وسيكمل التحديث وحده."
                    else "Let Emran install updates (only once): turn on \"Allow from this source\", then come back and the update carries on by itself.")
                }
                else -> GlassDialog(
                    onDismiss = ::close,
                    title = if (ar) "تحديث إلى $to" else "Update to $to",
                    subtitle = if (ar) "من الإصدار $here" else "From version $here",
                    confirmLabel = if (ar) "حدّث الآن" else "Update now",
                    onConfirm = { start(c) },
                    dismissLabel = if (ar) "لاحقًا" else "Later",
                ) {
                    Body(if (ar) "الملف صغير لأنه يحمل الجديد فقط، والباقي يؤخذ من عِمران المثبّت عندك. بياناتك وإعداداتك تبقى كما هي."
                    else "The file is small because it carries only what's new; the rest comes from the Emran already on your phone. Your data and settings stay as they are.")
                    if (installError != null && installError != "aborted") Fine(
                        if (ar) "أندرويد لم يكمل التثبيت في المرة السابقة. جرّب مرة أخرى." else "Android didn't finish the install last time. Try again.",
                    )
                }
            }
        }
    }
}
