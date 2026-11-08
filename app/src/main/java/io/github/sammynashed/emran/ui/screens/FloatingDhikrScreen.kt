package io.github.sammynashed.emran.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.sammynashed.emran.data.assets.MyAdhkar
import io.github.sammynashed.emran.service.FloatingDhikr
import io.github.sammynashed.emran.service.FloatingDhikrOverlay
import io.github.sammynashed.emran.ui.design.ChoiceRow
import io.github.sammynashed.emran.ui.design.GlassButton
import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.GlassDivider
import io.github.sammynashed.emran.ui.design.GlassGroup
import io.github.sammynashed.emran.ui.design.GlassSectionLabel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.SettingsRow
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.Text

/** "الذكر العائم": everything about the floating card, with a live copy of it at the top. */
@Composable
fun FloatingDhikrScreen(lang: String, onBack: () -> Unit, onPick: () -> Unit = {}) {
    val ar = lang == "ar"
    val sky = LocalSky.current
    val context = LocalContext.current

    var on by remember { mutableStateOf(FloatingDhikr.isOn(context)) }
    var every by remember { mutableIntStateOf(FloatingDhikr.everyMinutes(context)) }
    var show by remember { mutableIntStateOf(FloatingDhikr.showSeconds(context)) }
    var quietOn by remember { mutableStateOf(FloatingDhikr.quietOn(context)) }
    var quietStart by remember { mutableIntStateOf(FloatingDhikr.quietStart(context)) }
    var quietEnd by remember { mutableIntStateOf(FloatingDhikr.quietEnd(context)) }
    var picked by remember { mutableStateOf(FloatingDhikr.selectedIds(context)) }
    var granted by remember { mutableStateOf(FloatingDhikr.canDraw(context)) }
    var choicesVersion by remember { mutableIntStateOf(0) }
    val sections = remember(choicesVersion) { FloatingDhikr.sections(context) }
    val choices = remember(sections) { sections.flatMap { it.items } }

    var showEvery by remember { mutableStateOf(false) }
    var showDuration by remember { mutableStateOf(false) }
    var showQuiet by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var askPermission by remember { mutableStateOf(false) }

    // Coming back from Android's "display over other apps" page: re-read the permission.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                // Back from the picker (or from adding one of your own): show the new choice.
                picked = FloatingDhikr.selectedIds(context); choicesVersion++
                val was = granted
                granted = FloatingDhikr.canDraw(context)
                if (!was && granted && !on) { on = true; FloatingDhikr.setOn(context, true) }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    fun everyLabel(m: Int) = when (m) {
        15 -> if (ar) "كل ربع ساعة" else "Every 15 minutes"
        30 -> if (ar) "كل نصف ساعة" else "Every 30 minutes"
        60 -> if (ar) "كل ساعة" else "Every hour"
        120 -> if (ar) "كل ساعتين" else "Every 2 hours"
        else -> if (ar) "كل ٣ ساعات" else "Every 3 hours"
    }
    fun clock(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        android.app.TimePickerDialog(
            context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60,
            android.text.format.DateFormat.is24HourFormat(context),
        ).show()
    }
    fun tryNow() {
        if (!FloatingDhikr.canDraw(context)) { askPermission = true; return }
        // Nothing chosen yet: choosing comes first.
        val d = FloatingDhikr.next(context) ?: run { onPick(); return }
        FloatingDhikrOverlay.show(context, d)
    }
    val sample = choices.firstOrNull { it.id in picked } ?: choices.firstOrNull()

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = if (ar) "الذكر العائم" else "Floating dhikr",
                subtitle = if (ar) "بطاقة هادئة تطفو فوق أي تطبيق بذكرٍ تختاره، ثم تختفي وحدها. اسحبها إلى ✕ لإخفائها."
                else "A calm card floats over any app with a dhikr you chose, then leaves by itself. Drag it onto ✕ to dismiss it.",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.screen).padding(bottom = 120.dp),
            ) {
                // The real card, looping, so what you set is what you see.
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val w = with(LocalDensity.current) { (maxWidth - 28.dp).toPx().toInt() }
                    sample?.let { FloatingDhikrPreview(it, lang, show, w) }
                }
                Spacer(Modifier.height(Space.sm))

                GlassGroup {
                    SettingsRow(
                        icon = Icons.Default.Layers,
                        minHeight = 50.dp,
                        title = if (ar) "الذكر العائم" else "Floating dhikr",
                        value = if (on && granted) everyLabel(every) else if (on) (if (ar) "يحتاج إذنًا" else "Needs permission") else (if (ar) "متوقف" else "Off"),
                        onClick = {
                            if (!on && !granted) askPermission = true
                            else if (!on && picked.isEmpty()) onPick()
                            else { on = !on; FloatingDhikr.setOn(context, on) }
                        },
                    )
                    if (on && !granted) {
                        GlassDivider()
                        SettingsRow(
                            icon = Icons.Default.AutoAwesome,
                            minHeight = 50.dp,
                            title = if (ar) "اسمح بالظهور فوق التطبيقات" else "Allow display over other apps",
                            value = if (ar) "مطلوب" else "Required",
                            onClick = { context.startActivity(FloatingDhikr.overlaySettingsIntent(context)) },
                        )
                    }
                }

                Spacer(Modifier.height(Space.md))
                GlassSectionLabel(if (ar) "متى وكيف" else "WHEN AND HOW")
                GlassGroup {
                    SettingsRow(
                        icon = Icons.Default.Schedule, minHeight = 50.dp,
                        title = if (ar) "كل كم يظهر" else "How often",
                        value = everyLabel(every), onClick = { showEvery = true },
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.Timer, minHeight = 50.dp,
                        title = if (ar) "مدة بقائه" else "Stays for",
                        value = if (ar) "$show ثوانٍ" else "$show seconds", onClick = { showDuration = true },
                    )
                    GlassDivider()
                    SettingsRow(
                        icon = Icons.Default.Bedtime, minHeight = 50.dp,
                        title = if (ar) "ساعات الهدوء" else "Quiet hours",
                        value = if (quietOn) "\u2066${clock(quietStart)}–${clock(quietEnd)}\u2069" else (if (ar) "متوقف" else "Off"),
                        onClick = { showQuiet = true },
                    )
                }

                Spacer(Modifier.height(Space.md))
                GlassSectionLabel(if (ar) "الأذكار" else "ADHKAR")
                GlassGroup {
                    val n = choices.count { it.id in picked }
                    SettingsRow(
                        icon = Icons.Default.FormatListBulleted, minHeight = 50.dp,
                        title = if (ar) "الأذكار التي تظهر" else "Adhkar it shows",
                        value = when {
                            n == 0 -> if (ar) "اختر الأذكار" else "Choose"
                            ar -> "$n ${if (n in 3..10) "أذكار" else "ذكرًا"}"
                            else -> "$n selected"
                        },
                        onClick = onPick,
                    )
                }

                Spacer(Modifier.height(Space.lg))
                GlassButton(if (ar) "جرّبه الآن" else "Try it now", { tryNow() }, filled = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.sm))
                Text(
                    if (ar) "يظهر فقط والشاشة مفتوحة، ولا يظهر أثناء المكالمات ولا في ساعات الهدوء. اضغط عليه لتقرأ الذكر كاملًا مع فضله ومصدره."
                    else "It only appears while the screen is on and unlocked, never during calls or quiet hours. Tap it to read the whole dhikr with its virtue and source.",
                    color = sky.inkSoft, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }

    // ---- Dialogs ----
    if (askPermission) {
        GlassDialog(
            onDismiss = { askPermission = false },
            title = if (ar) "إذن واحد فقط" else "One permission",
            subtitle = if (ar) "ليطفو الذكر فوق التطبيقات الأخرى يحتاج أندرويد إذن «الظهور فوق التطبيقات». عِمران لا يقرأ شيئًا من شاشتك؛ يرسم البطاقة فقط."
            else "To float over other apps, Android needs the “Display over other apps” permission. Emran reads nothing from your screen; it only draws the card.",
            confirmLabel = if (ar) "افتح الإعدادات" else "Open settings",
            onConfirm = { askPermission = false; context.startActivity(FloatingDhikr.overlaySettingsIntent(context)) },
            dismissLabel = if (ar) "ليس الآن" else "Not now",
        ) {}
    }

    if (showEvery) {
        GlassDialog(onDismiss = { showEvery = false }, title = if (ar) "كل كم يظهر" else "How often", confirmLabel = if (ar) "تم" else "Done", onConfirm = { showEvery = false }) {
            FloatingDhikr.EVERY_CHOICES.forEach { m ->
                ChoiceRow(label = everyLabel(m), selected = every == m, onClick = { every = m; FloatingDhikr.setEvery(context, m) })
            }
        }
    }

    if (showDuration) {
        GlassDialog(onDismiss = { showDuration = false }, title = if (ar) "مدة بقائه" else "Stays for",
            subtitle = if (ar) "يتوقف العدّ وأنت تمسك البطاقة" else "The countdown pauses while you hold the card",
            confirmLabel = if (ar) "تم" else "Done", onConfirm = { showDuration = false }) {
            FloatingDhikr.SHOW_CHOICES.forEach { s ->
                ChoiceRow(label = if (ar) "$s ثوانٍ" else "$s seconds", selected = show == s, onClick = { show = s; FloatingDhikr.setShow(context, s) })
            }
        }
    }

    if (showQuiet) {
        GlassDialog(onDismiss = { showQuiet = false }, title = if (ar) "ساعات الهدوء" else "Quiet hours",
            subtitle = if (ar) "لا يظهر الذكر العائم في هذا الوقت" else "No floating dhikr during these hours",
            confirmLabel = if (ar) "تم" else "Done", onConfirm = { showQuiet = false }) {
            ChoiceRow(label = if (ar) "متوقف" else "Off", selected = !quietOn, onClick = { quietOn = false; FloatingDhikr.setQuiet(context, false, quietStart, quietEnd) })
            ChoiceRow(
                label = "\u2066${clock(quietStart)}–${clock(quietEnd)}\u2069", selected = quietOn,
                sublabel = if (ar) "من وقت النوم إلى الصباح" else "From bedtime to morning",
                onClick = { quietOn = true; FloatingDhikr.setQuiet(context, true, quietStart, quietEnd) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(if (ar) "من ${clock(quietStart)}" else "From ${clock(quietStart)}", {
                    pickTime(quietStart) { quietStart = it; quietOn = true; FloatingDhikr.setQuiet(context, true, it, quietEnd) }
                }, filled = false, modifier = Modifier.weight(1f))
                GlassButton(if (ar) "إلى ${clock(quietEnd)}" else "To ${clock(quietEnd)}", {
                    pickTime(quietEnd) { quietEnd = it; quietOn = true; FloatingDhikr.setQuiet(context, true, quietStart, it) }
                }, filled = false, modifier = Modifier.weight(1f))
            }
        }
    }



    if (showAdd) {
        var text by remember { mutableStateOf("") }
        val colours = OutlinedTextFieldDefaults.colors(
            focusedTextColor = sky.ink, unfocusedTextColor = sky.ink,
            focusedBorderColor = sky.ink, unfocusedBorderColor = sky.ink.copy(alpha = 0.25f),
            focusedLabelColor = sky.inkSoft, unfocusedLabelColor = sky.inkSoft, cursorColor = sky.ink,
        )
        GlassDialog(
            onDismiss = { showAdd = false },
            title = if (ar) "ذكر خاص" else "Your own dhikr",
            subtitle = if (ar) "يُحفظ في «أذكاري» أيضًا" else "Also saved in “My adhkar”",
            confirmLabel = if (ar) "حفظ" else "Save",
            onConfirm = {
                if (text.isNotBlank()) {
                    MyAdhkar.save(context, null, text, "", 1, "")
                    val added = MyAdhkar.load(context).lastOrNull()?.id
                    if (added != null) { picked = picked + added; FloatingDhikr.setSelected(context, picked) }
                    choicesVersion++
                    showAdd = false
                }
            },
            dismissLabel = if (ar) "إلغاء" else "Cancel",
        ) {
            OutlinedTextField(
                text, { text = it.take(160) }, colors = colours, minLines = 2, maxLines = 4,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, lineHeight = 30.sp),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (ar) "نص الذكر (قصير)" else "The dhikr (short)") },
            )
        }
    }
}
