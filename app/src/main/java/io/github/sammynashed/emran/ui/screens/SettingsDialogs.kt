package io.github.sammynashed.emran.ui.screens

import android.content.Intent

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast

import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import io.github.sammynashed.emran.data.LocationPreset
import io.github.sammynashed.emran.data.LocationPresets
import io.github.sammynashed.emran.service.NotificationHealth
import io.github.sammynashed.emran.service.OemFamily
import io.github.sammynashed.emran.ui.design.ChoiceRow
import io.github.sammynashed.emran.ui.design.GlassButton
import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.SettingsRow

import io.github.sammynashed.emran.ui.localization.Text
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------------------------
// Settings dialogs, all in the glass style: one calm pane, one idea, at most two actions.
// ---------------------------------------------------------------------------------------------

/** The four adhan voices. */
private data class Voice(val key: String, val en: String, val ar: String, val descEn: String, val descAr: String)

private val VOICES = listOf(
    Voice("default", "System sound", "صوت النظام", "Your phone's own notification sound", "نغمة الإشعارات في هاتفك"),
    Voice("ahmad_alnafees", "Ahmad Alnafys", "أحمد النفيس", "Calm and gentle", "هادئ ولطيف"),
    Voice("mishary_alafasy", "Mishary Alafasy", "مشاري العفاسي", "Clear and melodious", "نديّ وعذب"),
    Voice("saad_alghamdi", "Saad Alghamdy", "سعد الغامدي", "Deep and majestic", "خاشع ومهيب"),
)

/** Short name of a saved adhan, for the Settings row. */
fun adhanDisplayName(key: String, ar: Boolean): String =
    VOICES.firstOrNull { it.key == key }?.let { if (ar) it.ar else it.en } ?: key

@Composable
fun AdhanSoundDialog(
    ar: Boolean,
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember(current) { mutableStateOf(current) }
    var playing by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null; playing = null
    }

    fun preview(key: String) {
        val wasPlaying = playing == key
        stop()
        if (wasPlaying) return
        try {
            val p = if (key == "default") {
                MediaPlayer().apply {
                    setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    prepare()
                }
            } else {
                val id = context.resources.getIdentifier(key, "raw", context.packageName)
                if (id != 0) MediaPlayer.create(context, id) else null
            }
            if (p != null) {
                p.setOnCompletionListener { stop() }
                player = p; playing = key
                p.start()
            }
        } catch (e: Exception) {
            Toast.makeText(context, if (ar) "تعذّر تشغيل المعاينة" else "Couldn't play the preview", Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(Unit) { onDispose { stop() } }

    val sky = LocalSky.current
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "صوت الأذان" else "Adhan sound",
        subtitle = if (ar) "اضغط على صوت لتسمعه" else "Tap a voice to hear it",
        confirmLabel = if (ar) "حفظ" else "Save",
        onConfirm = { onSave(selected) },
        dismissLabel = if (ar) "إلغاء" else "Cancel",
    ) {
        VOICES.forEach { v ->
            ChoiceRow(
                label = if (ar) v.ar else v.en,
                sublabel = if (ar) v.descAr else v.descEn,
                selected = selected == v.key,
                onClick = { selected = v.key; preview(v.key) },
                trailing = {
                    Icon(
                        if (playing == v.key) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = sky.inkSoft,
                        modifier = Modifier.size(22.dp),
                    )
                },
            )
        }
    }
}

// ---- Reliable alerts ----------------------------------------------------------------------

@Composable
fun ReliabilityDialog(ar: Boolean, health: NotificationHealth, onDismiss: () -> Unit) {
    val context = LocalContext.current
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "وصول التنبيه" else "Reliable alerts",
        subtitle = if (ar) "لكي يصلك الأذان في وقته حتى والتطبيق مغلق، يجب أن تكون هذه كلها مفعّلة."
        else "For the adhan to reach you on time even when Emran is closed, all of these must be on.",
        confirmLabel = if (ar) "تم" else "Done",
        onConfirm = onDismiss,
    ) {
        HealthRow(
            title = if (ar) "الإشعارات" else "Notifications",
            detail = if (ar) "بدونها لا يمكن عرض أي تنبيه" else "Without this no alert can show",
            ok = health.notificationsAllowed,
            action = if (ar) "فتح الإعدادات" else "Open settings",
            onAction = { NotificationHealth.openNotificationSettings(context) },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            HealthRow(
                title = if (ar) "المنبّهات الدقيقة" else "Exact alarms",
                detail = if (ar) "يطلق الأذان في ثانيته لا بعدها بدقائق" else "Fires the adhan on the second, not minutes late",
                ok = health.exactAlarmAllowed,
                action = if (ar) "السماح" else "Allow",
                onAction = {
                    try {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .apply { data = Uri.parse("package:${context.packageName}") },
                        )
                    } catch (e: Exception) {
                        NotificationHealth.openNotificationSettings(context)
                    }
                },
            )
        }
        HealthRow(
            title = if (ar) "البطارية بلا قيود" else "Battery unrestricted",
            detail = if (ar) "يمنع النظام من تجميد التطبيق في الخلفية" else "Stops Android freezing Emran in the background",
            ok = health.batteryUnrestricted,
            action = if (ar) "السماح" else "Allow",
            onAction = {
                try {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .apply { data = Uri.parse("package:${context.packageName}") },
                    )
                } catch (e: Exception) {
                    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                }
            },
        )
        health.oem?.let { oem ->
            // Xiaomi can be read from the system; on the other brands there is no way to read it, so it
            // counts as done only once the person says so.
            var confirmed by remember { mutableStateOf(health.autostartAllowed == true) }
            HealthRow(
                title = oem.title(ar),
                detail = if (ar) "من دونه قد يوقف هاتفك عِمران فلا يُسمع الأذان" else "Without it your phone may stop Emran, and the adhan won't ring",
                ok = confirmed,
                action = if (ar) "افتح الإعداد" else "Open the setting",
                onAction = { NotificationHealth.openAutostartSettings(context) },
            )
            if (!confirmed && oem != OemFamily.XIAOMI) {
                Text(
                    if (ar) "فعّلته بالفعل" else "I've already done this",
                    color = LocalSky.current.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            NotificationHealth.confirmAutostart(context)
                            confirmed = true
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        GlassButton(
            label = if (ar) "إرسال تنبيه تجريبي" else "Send a test alert",
            onClick = {
                NotificationHealth.sendTest(context)
                Toast.makeText(context, if (ar) "أُرسل تنبيه تجريبي" else "Test alert sent", Toast.LENGTH_SHORT).show()
            },
            filled = false,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun HealthRow(title: String, detail: String, ok: Boolean, action: String, onAction: () -> Unit) {
    val sky = LocalSky.current
    val dot = if (ok) Color(0xFF4FD08A) else Color(0xFFFFB74D)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(sky.ink.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = sky.ink, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = sky.inkSoft, fontSize = 12.5.sp, lineHeight = 16.sp)
        }
        if (!ok) {
            Spacer(Modifier.width(8.dp))
            GlassButton(action, onClick = onAction, filled = true)
        }
    }
}

// ---- Calculation --------------------------------------------------------------------------

@Composable
fun CalculationDialog(
    ar: Boolean,
    locationLabel: String,
    methodLabel: String,
    schoolLabel: String,
    highLatLabel: String?,
    hasOffsets: Boolean,
    onLocation: () -> Unit,
    onMethod: () -> Unit,
    onSchool: () -> Unit,
    onHighLat: () -> Unit,
    onOffsets: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "حساب المواقيت" else "Prayer calculation",
        confirmLabel = if (ar) "تم" else "Done",
        onConfirm = onDismiss,
    ) {
        SettingsRow(Icons.Default.LocationOn, if (ar) "الموقع" else "Location", onClick = onLocation, value = locationLabel)
        SettingsRow(Icons.Default.Explore, if (ar) "الطريقة" else "Method", onClick = onMethod, value = methodLabel)
        SettingsRow(Icons.Default.Schedule, if (ar) "العصر" else "Asr", onClick = onSchool, value = schoolLabel)
        if (highLatLabel != null) {
            SettingsRow(Icons.Default.WbTwilight, if (ar) "خطوط العرض العليا" else "High latitude", onClick = onHighLat, value = highLatLabel)
        }
        SettingsRow(
            Icons.Default.Edit,
            if (ar) "ضبط الدقائق يدوياً" else "Fine-tune minutes",
            onClick = onOffsets,
            value = if (hasOffsets) (if (ar) "مفعّل" else "On") else null,
        )
    }
}

/** Six prayers, each with its calibrated time and a "+2 min" badge when it has been moved. */
@Composable
fun OffsetsDialog(
    ar: Boolean,
    rows: List<Triple<String, String, LocalDateTime>>,
    offsets: Map<String, Int>,
    onPick: (Triple<String, String, LocalDateTime>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sky = LocalSky.current
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "ضبط الدقائق" else "Fine-tune minutes",
        subtitle = if (ar) "حرّك كل صلاة بالدقائق إن اختلف وقتك المحلي." else "Nudge a prayer by a few minutes if your local timetable differs.",
        confirmLabel = if (ar) "تم" else "Done",
        onConfirm = onDismiss,
    ) {
        rows.forEach { row ->
            val off = offsets[row.first] ?: 0
            val shown = row.third.plusMinutes(off.toLong())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(row) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.second, color = sky.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (off != 0) {
                    Text(
                        if (off > 0) "+$off" else "$off",
                        color = sky.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(sky.ink.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(io.github.sammynashed.emran.data.TimeFormat.full(shown, ar), color = sky.ink, fontSize = 17.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

/** Move one prayer by whole minutes: large time, big plus and minus. */
@Composable
fun ClockPopupDialog(
    label: String,
    baseDateTime: LocalDateTime,
    currentOffset: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val ar = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current == "ar"
    val sky = LocalSky.current
    var offset by remember { mutableStateOf(currentOffset) }
    val shown = baseDateTime.plusMinutes(offset.toLong())
    GlassDialog(
        onDismiss = onDismiss,
        title = label,
        subtitle = (if (ar) "الوقت المحسوب: " else "Calculated: ") + io.github.sammynashed.emran.data.TimeFormat.full(baseDateTime, ar),
        confirmLabel = if (ar) "حفظ" else "Save",
        onConfirm = { onSave(offset) },
        dismissLabel = if (ar) "إلغاء" else "Cancel",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(Icons.Filled.Remove, onClick = { if (offset > -120) offset-- })
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(io.github.sammynashed.emran.data.TimeFormat.full(shown, ar), color = sky.ink, fontSize = 48.sp, fontWeight = FontWeight.Light, bypassNumerals = false)
                Text(
                    text = when {
                        offset == 0 -> if (ar) "بلا تعديل" else "No change"
                        offset > 0 -> if (ar) "+$offset دقيقة" else "+$offset min"
                        else -> if (ar) "$offset دقيقة" else "$offset min"
                    },
                    color = sky.inkSoft, fontSize = 14.sp,
                )
            }
            StepButton(Icons.Filled.Add, onClick = { if (offset < 120) offset++ })
        }
        if (offset != 0) {
            GlassButton(
                label = if (ar) "إعادة الضبط" else "Reset",
                onClick = { offset = 0 },
                filled = false,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val sky = LocalSky.current
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(sky.ink.copy(alpha = 0.12f))
            .border(1.dp, sky.ink.copy(alpha = 0.25f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = sky.ink, modifier = Modifier.size(26.dp)) }
}

// ---- Location -----------------------------------------------------------------------------

@Composable
fun LocationDialog(
    ar: Boolean,
    lang: String,
    activeName: String,
    activeLat: Double,
    activeLon: Double,
    activeZone: String,
    onPick: (LocationPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val sky = LocalSky.current
    var query by remember { mutableStateOf("") }
    val list = remember(query, activeName) {
        val base = if (LocationPresets.list.any { it.name == activeName }) LocationPresets.list
        else listOf(LocationPreset(activeName, activeLat, activeLon, activeZone)) + LocationPresets.list
        if (query.isBlank()) base else base.filter {
            it.name.contains(query, ignoreCase = true) ||
                LocationPresets.getLocalizedName(it.name, lang).contains(query, ignoreCase = true)
        }
    }
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "الموقع" else "Location",
        dismissLabel = if (ar) "إغلاق" else "Close",
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(if (ar) "ابحث عن مدينة…" else "Search a city…", color = sky.inkSoft, fontSize = 15.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = sky.inkSoft) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = sky.ink.copy(alpha = 0.08f),
                unfocusedContainerColor = sky.ink.copy(alpha = 0.08f),
                focusedTextColor = sky.ink,
                unfocusedTextColor = sky.ink,
                cursorColor = sky.ink,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().testTag("settings_location_search_input"),
        )
        Spacer(Modifier.height(4.dp))
        if (list.isEmpty()) {
            Text(
                if (ar) "لا توجد نتائج" else "No matches",
                color = sky.inkSoft, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
        list.forEach { preset ->
            ChoiceRow(
                label = LocationPresets.getLocalizedName(preset.name, lang),
                selected = preset.name == activeName,
                onClick = { onPick(preset) },
            )
        }
    }
}

// ---- Adhkar reminders -----------------------------------------------------------------------

/** On/off and the time of one reminder: a big time that opens the phone's own time picker, elder-friendly. */
@Composable
fun AdhkarReminderDialog(
    title: String,
    initialOn: Boolean,
    initialMinute: Int,
    defaultMinute: Int,
    onDismiss: () -> Unit,
    onSave: (on: Boolean, minute: Int) -> Unit,
    subtitle: String? = null,
    /** "Every day" unless said otherwise ("Every Friday"). */
    repeatLabel: String? = null,
) {
    val ar = io.github.sammynashed.emran.ui.localization.LocalAppLanguage.current == "ar"
    val sky = LocalSky.current
    var on by remember { mutableStateOf(initialOn) }
    var minute by remember { mutableStateOf(initialMinute) }
    val shown = java.time.LocalTime.of(minute / 60, minute % 60)
    GlassDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = subtitle ?: if (ar) "تذكير يومي بإشعار في هذا الوقت." else "A daily notification at this time.",
        confirmLabel = if (ar) "حفظ" else "Save",
        onConfirm = { onSave(on, minute) },
        dismissLabel = if (ar) "إلغاء" else "Cancel",
    ) {
        io.github.sammynashed.emran.ui.design.GlassSegmented(
            // Laid out left to right; in Arabic "on" belongs on the right.
            options = listOf("on" to (if (ar) "مفعّل" else "On"), "off" to (if (ar) "متوقف" else "Off")).let { if (ar) it.reversed() else it },
            selectedKey = if (on) "on" else "off",
            onSelect = { on = it == "on" },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        // The time itself is the button: it opens the phone's own time picker (whatever that phone's
        // Android draws), in the app's 12/24-hour choice. No steps to count through.
        val context = androidx.compose.ui.platform.LocalContext.current
        val openPicker = {
            if (on) android.app.TimePickerDialog(
                context,
                android.R.style.Theme_DeviceDefault_Dialog_Alert,
                { _, h, m -> minute = h * 60 + m },
                minute / 60, minute % 60,
                io.github.sammynashed.emran.data.TimeFormat.use24,
            ).show()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .alpha(if (on) 1f else 0.4f)
                .clip(RoundedCornerShape(24.dp))
                .background(sky.ink.copy(alpha = 0.08f))
                .clickable(enabled = on) { openPicker() }
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(io.github.sammynashed.emran.data.TimeFormat.full(shown, ar), color = sky.ink, fontSize = 44.sp, fontWeight = FontWeight.Light, bypassNumerals = false)
            Text(
                (repeatLabel ?: if (ar) "كل يوم" else "Every day") + (if (ar) " · اضغط لتغيير الوقت" else " · tap to change"),
                color = sky.inkSoft, fontSize = 14.sp,
            )
        }
        // Always laid out (just hidden at the default), so the dialog never jumps under a finger.
        val changed = minute != defaultMinute
        GlassButton(
            label = if (ar) "الوقت الافتراضي" else "Default time",
            onClick = { if (changed) minute = defaultMinute },
            filled = false,
            modifier = Modifier.align(Alignment.CenterHorizontally).alpha(if (changed) 1f else 0f),
        )
    }
}
