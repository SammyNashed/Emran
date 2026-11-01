package io.github.sammynashed.emran.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.PrayerNames
import io.github.sammynashed.emran.service.AdhanPolicy
import io.github.sammynashed.emran.service.PrayerAlarmReceiver
import io.github.sammynashed.emran.service.PrayerCountdown
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.Timer
import io.github.sammynashed.emran.ui.design.GlassButton
import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.GlassDivider
import io.github.sammynashed.emran.ui.design.GlassGroup
import io.github.sammynashed.emran.ui.design.GlassSectionLabel
import io.github.sammynashed.emran.ui.design.ChoiceRow
import io.github.sammynashed.emran.ui.design.ChoiceDialog
import io.github.sammynashed.emran.ui.design.GlassSegmented
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.SettingsRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel

private val PRAYERS = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

// Days are a bitmask, Monday = bit 0. New quiet hours start on Monday to Friday.
private const val MON_FRI = 0b0011111

/**
 * How the adhan plays. Each setting has exactly one home: whether a prayer's adhan is on at all is the bell on
 * the Prayers screen; this page is only about HOW it plays (voice, vibrate-only prayers, quiet hours, loudness,
 * and the opt-in Fajr wake-up).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AdhanBehaviorScreen(viewModel: PrayerViewModel, lang: String, onBack: () -> Unit) {
    val ar = lang == "ar"
    val sky = LocalSky.current
    val context = LocalContext.current

    // Everything lives in SharedPreferences (the alarm receiver reads it there); these only mirror it for the UI.
    val vibrateOnly = remember { PRAYERS.filter { AdhanPolicy.mode(context, it) == AdhanPolicy.Mode.VIBRATE }.toMutableStateList() }
    var loudness by remember { mutableFloatStateOf(AdhanPolicy.loudness(context).toFloat()) }
    var wakeLoudness by remember { mutableFloatStateOf(AdhanPolicy.wakeLoudness(context).toFloat()) }
    var wake by remember { mutableStateOf(AdhanPolicy.wakeForFajr(context)) }
    var quiet by remember { mutableStateOf(AdhanPolicy.quietWindows(context).firstOrNull()) }
    var dndGranted by remember { mutableStateOf(AdhanPolicy.canBypassDnd(context)) }
    val voice by viewModel.defaultAdhanSound.collectAsState()
    val hasFull = AdhanPolicy.fullFajrAdhan(context, voice) != null

    var showVoice by remember { mutableStateOf(false) }
    var showVibrate by remember { mutableStateOf(false) }
    var showLoudness by remember { mutableStateOf(false) }
    var showWake by remember { mutableStateOf(false) }
    var showQuiet by remember { mutableStateOf(false) }
    var showDays by remember { mutableStateOf(false) }
    var showCountdown by remember { mutableStateOf(false) }
    var lead by remember { mutableIntStateOf(PrayerCountdown.leadMinutes(context)) }

    fun fire(prayer: String) {
        context.sendBroadcast(
            Intent(context, PrayerAlarmReceiver::class.java).setPackage(context.packageName)
                .putExtra("PRAYER_NAME", prayer).putExtra("TEST", true),
        )
    }
    fun saveQuiet(w: AdhanPolicy.QuietWindow?) {
        quiet = w
        AdhanPolicy.setQuietWindows(context, listOfNotNull(w))
    }
    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        android.app.TimePickerDialog(
            context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60,
            android.text.format.DateFormat.is24HourFormat(context),
        ).show()
    }
    // With wake on, Fajr is handled by the wake-up, so it is not offered as "vibrate only".
    val vibrateChoices = PRAYERS.filter { !(wake && it == "Fajr") }
    val vibrateValue = vibrateOnly.filter { it in vibrateChoices }
        .let { l -> if (l.isEmpty()) (if (ar) "لا شيء" else "None") else l.joinToString(if (ar) "، " else ", ") { PrayerNames.name(it, ar) } }

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = if (ar) "الأذان" else "Adhan",
                subtitle = if (ar) "يحترم عِمران وضع هاتفك: الصامت والاهتزاز وعدم الإزعاج"
                else "Emran respects your phone: silent, vibrate and Do Not Disturb",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.screen).padding(bottom = 120.dp),
            ) {
                GlassGroup {
                    SettingsRow(
                        icon = Icons.Default.Notifications,
                        minHeight = 50.dp,
                        title = if (ar) "صوت الأذان" else "Adhan sound",
                        value = adhanDisplayName(voice, ar),
                        onClick = { showVoice = true },
                    )
                }

                Spacer(Modifier.height(Space.md))
                GlassSectionLabel(if (ar) "كيف يصلك" else "HOW IT PLAYS")
                GlassGroup {
                    SettingsRow(
                        icon = Icons.Default.Vibration,
                        minHeight = 50.dp,
                        title = if (ar) "اهتزاز فقط" else "Vibrate only",
                        value = vibrateValue,
                        onClick = { showVibrate = true },
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.Schedule,
                        minHeight = 50.dp,
                        title = if (ar) "ساعات الهدوء" else "Quiet hours",
                        value = quiet?.let { "${clock(it.startMin)}–${clock(it.endMin)}" } ?: (if (ar) "متوقف" else "Off"),
                        onClick = { showQuiet = true },
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        minHeight = 50.dp,
                        title = if (ar) "حدّ الصوت" else "Loudness cap",
                        value = "${loudness.toInt()}%",
                        onClick = { showLoudness = true },
                    )
                }

                Spacer(Modifier.height(Space.md))
                GlassSectionLabel(if (ar) "قبل الأذان" else "BEFORE THE ADHAN")
                GlassGroup {
                    SettingsRow(
                        icon = Icons.Default.Timer,
                        minHeight = 50.dp,
                        title = if (ar) "مؤقّت الأذان القادم" else "Countdown to the adhan",
                        value = if (lead == 0) (if (ar) "متوقف" else "Off") else (if (ar) "قبل $lead دقيقة" else "$lead min before"),
                        onClick = { showCountdown = true },
                    )
                }

                Spacer(Modifier.height(Space.md))
                GlassSectionLabel(if (ar) "الفجر" else "FAJR")
                GlassGroup {
                    SettingsRow(
                        icon = Icons.Default.Alarm,
                        minHeight = 50.dp,
                        title = if (ar) "أيقظني للفجر" else "Wake me for Fajr",
                        value = if (wake) (if (ar) "مفعّل" else "On") else (if (ar) "متوقف" else "Off"),
                        onClick = { showWake = true },
                    )
                }
            }
        }
    }

    // ---- Dialogs ----
    if (showVoice) {
        AdhanSoundDialog(
            ar = ar,
            current = voice,
            onSave = { viewModel.setDefaultAdhanSound(it); showVoice = false },
            onDismiss = { showVoice = false },
        )
    }

    if (showCountdown) {
        GlassDialog(
            onDismiss = { showCountdown = false },
            title = if (ar) "مؤقّت الأذان القادم" else "Countdown to the adhan",
            subtitle = if (ar) "إشعار صامت بعدّ تنازلي حيّ يظهر قبل كل أذان بهذه المدة، ويختفي عند حلول الأذان."
            else "A silent notification with a live countdown appears this long before each adhan and disappears when it's time.",
            confirmLabel = if (ar) "تم" else "Done",
            onConfirm = { showCountdown = false },
        ) {
            PrayerCountdown.LEAD_CHOICES.forEach { m ->
                ChoiceRow(
                    label = if (m == 0) (if (ar) "متوقف" else "Off") else (if (ar) "قبل $m دقيقة" else "$m minutes before"),
                    selected = lead == m,
                    onClick = { lead = m; PrayerCountdown.setLeadMinutes(context, m) },
                )
            }
            if (lead > 0) GlassButton(
                if (ar) "جرّب" else "Preview",
                { PrayerCountdown.show(context, "Maghrib", System.currentTimeMillis() + lead * 60_000L) },
                filled = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showVibrate) {
        GlassDialog(
            onDismiss = { showVibrate = false },
            title = if (ar) "اهتزاز فقط" else "Vibrate only",
            subtitle = if (ar) "هذه الصلوات يهتزّ لها الهاتف بلا صوت أذان. تشغيل الأذان أو إيقافه لكل صلاة من شاشة «مواقيت الصلاة»."
            else "For these prayers the phone vibrates with no adhan sound. Turn a prayer's adhan on or off from the Prayers screen.",
            confirmLabel = if (ar) "تم" else "Done",
            onConfirm = { showVibrate = false },
        ) {
            vibrateChoices.forEach { p ->
                val on = p in vibrateOnly
                ChoiceRow(
                    label = PrayerNames.name(p, ar),
                    selected = on,
                    onClick = {
                        if (on) vibrateOnly.remove(p) else vibrateOnly.add(p)
                        AdhanPolicy.setMode(context, p, if (on) AdhanPolicy.Mode.ADHAN else AdhanPolicy.Mode.VIBRATE)
                    },
                )
            }
        }
    }

    if (showLoudness) {
        GlassDialog(
            onDismiss = { showLoudness = false },
            title = if (ar) "حدّ الصوت" else "Loudness cap",
            subtitle = if (ar) "يتبع الأذان صوت الإشعارات في هاتفك، ويبدأ هادئًا، ولا يتجاوز هذا الحد."
            else "The adhan follows your phone's notification volume, fades in gently, and never goes past this cap.",
            confirmLabel = if (ar) "تم" else "Done",
            onConfirm = { showLoudness = false },
        ) {
            CapSlider(loudness, { loudness = it }, { AdhanPolicy.setLoudness(context, loudness.toInt()) }, 20f)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(if (ar) "جرّب" else "Test", { AdhanPolicy.setLoudness(context, loudness.toInt()); fire("Maghrib") }, filled = true, modifier = Modifier.weight(1f))
                GlassButton(if (ar) "إيقاف" else "Stop", { PrayerAlarmReceiver.muteAdhan(context) }, filled = false, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showWake) {
        GlassDialog(
            onDismiss = { showWake = false },
            title = if (ar) "أيقظني للفجر" else "Wake me for Fajr",
            subtitle = if (ar) "يرنّ أذان الفجر كاملًا بمستوى صوت المنبّه الذي تختاره ثم يعود صوتك كما كان، حتى لو كان الهاتف صامتًا أو على «عدم الإزعاج». وعند التفعيل يُشغَّل أذان الفجر تلقائيًا."
            else "Fajr plays in full at the wake-up volume you pick (your alarm volume is put back afterwards), even on silent or Do Not Disturb. Turning this on also switches Fajr's adhan on.",
            confirmLabel = if (ar) "تم" else "Done",
            onConfirm = { showWake = false },
        ) {
            ChoiceRow(
                label = if (ar) "متوقف" else "Off", selected = !wake,
                onClick = { wake = false; AdhanPolicy.setWakeForFajr(context, false) },
            )
            ChoiceRow(
                label = if (ar) "مفعّل" else "On", selected = wake,
                sublabel = if (hasFull) (if (ar) "الأذان كاملًا مع «الصلاة خير من النوم»" else "The full adhan with “الصلاة خير من النوم”")
                else (if (ar) "الأذان الكامل لهذا الصوت غير متوفر بعد، فسيُشغَّل الأذان القصير" else "The full Fajr adhan for this voice isn't available yet; the short adhan plays"),
                onClick = {
                    wake = true
                    AdhanPolicy.setWakeForFajr(context, true)
                    dndGranted = AdhanPolicy.canBypassDnd(context)
                    // Asking to be woken for Fajr means Fajr's adhan must be on: don't leave that to a second switch.
                    if (!viewModel.isPrayerNotificationEnabled("Fajr")) viewModel.togglePrayerNotification("Fajr")
                },
            )
            if (wake) {
                Text(if (ar) "مستوى صوت الإيقاظ (من أعلى مستوى للمنبّه)" else "Wake-up volume (of your alarm's maximum)", color = sky.inkSoft, fontSize = 13.sp)
                CapSlider(wakeLoudness, { wakeLoudness = it }, { AdhanPolicy.setWakeLoudness(context, wakeLoudness.toInt()) }, 10f)
                if (!dndGranted) {
                    GlassButton(
                        if (ar) "السماح بتجاوز عدم الإزعاج" else "Allow through Do Not Disturb",
                        { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                        filled = false, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(if (ar) "جرّب" else "Test", { AdhanPolicy.setWakeLoudness(context, wakeLoudness.toInt()); fire("Fajr") }, filled = true, modifier = Modifier.weight(1f))
                    GlassButton(if (ar) "إيقاف" else "Stop", { PrayerAlarmReceiver.muteAdhan(context) }, filled = false, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showQuiet) {
        GlassDialog(
            onDismiss = { showQuiet = false },
            title = if (ar) "ساعات الهدوء" else "Quiet hours",
            subtitle = if (ar) "في هذه الأوقات يهتزّ الهاتف فقط، بلا صوت." else "During these hours the phone only vibrates, with no sound.",
            confirmLabel = if (ar) "تم" else "Done",
            onConfirm = { showQuiet = false },
        ) {
            ChoiceRow(label = if (ar) "متوقف" else "Off", selected = quiet == null, onClick = { saveQuiet(null) })
            ChoiceRow(
                label = if (ar) "مفعّل" else "On", selected = quiet != null,
                onClick = { if (quiet == null) saveQuiet(AdhanPolicy.QuietWindow(days = MON_FRI, startMin = 8 * 60, endMin = 14 * 60)) },
            )
            quiet?.let { w ->
                ValueRow(if (ar) "الأيام" else "Days", daysLabel(w.days, ar)) { showDays = true }
                ValueRow(if (ar) "من" else "From", clock(w.startMin)) { pickTime(w.startMin) { saveQuiet(w.copy(startMin = it)) } }
                ValueRow(if (ar) "إلى" else "To", clock(w.endMin)) { pickTime(w.endMin) { saveQuiet(w.copy(endMin = it)) } }
            }
        }
    }

    if (showDays) {
        val mask = quiet?.days ?: MON_FRI
        GlassDialog(
            onDismiss = { showDays = false },
            title = if (ar) "الأيام" else "Days",
            confirmLabel = if (ar) "تم" else "Done",
            onConfirm = { showDays = false },
        ) {
            // Sunday first, the way the week reads in most of the Muslim world. Bit 0 is Monday.
            DAY_ORDER.forEach { d ->
                val on = (mask shr d and 1) == 1
                ChoiceRow(
                    label = dayName(d, ar),
                    selected = on,
                    onClick = {
                        val next = mask xor (1 shl d)
                        if (next != 0) quiet?.let { saveQuiet(it.copy(days = next)) } // at least one day stays
                    },
                )
            }
        }
    }
}

private fun clock(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

private val DAY_ORDER = listOf(6, 0, 1, 2, 3, 4, 5)

private fun dayName(d: Int, ar: Boolean): String =
    (if (ar) listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
    else listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"))[d]

/** "Every day", one or two day names, or a count. */
private fun daysLabel(mask: Int, ar: Boolean): String {
    val picked = DAY_ORDER.filter { (mask shr it and 1) == 1 }
    return when {
        picked.size == 7 -> if (ar) "كل يوم" else "Every day"
        picked.size <= 2 -> picked.joinToString(if (ar) "، " else ", ") { dayName(it, ar) }
        else -> if (ar) "${picked.size} أيام" else "${picked.size} days"
    }
}

/** A label on one side, its current value and a chevron on the other; tap to change it. */
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    val sky = LocalSky.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = sky.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(value, color = sky.inkSoft, fontSize = 15.sp, bypassNumerals = true)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = sky.inkSoft, modifier = Modifier.size(20.dp))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CapSlider(value: Float, onChange: (Float) -> Unit, onDone: () -> Unit, min: Float) {
    val sky = LocalSky.current
    Text("${value.toInt()}%", color = sky.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, bypassNumerals = true)
    Slider(
        value = value,
        onValueChange = onChange,
        onValueChangeFinished = onDone,
        valueRange = min..100f,
        thumb = { Box(Modifier.size(22.dp).clip(CircleShape).background(sky.ink)) },
        colors = SliderDefaults.colors(
            thumbColor = sky.ink,
            activeTrackColor = sky.ink,
            inactiveTrackColor = sky.ink.copy(alpha = 0.2f),
        ),
    )
}
