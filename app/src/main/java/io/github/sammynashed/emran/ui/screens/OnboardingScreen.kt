package io.github.sammynashed.emran.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.sammynashed.emran.data.LocationPreset
import io.github.sammynashed.emran.data.LocationPresets
import io.github.sammynashed.emran.ui.design.ChoiceDialog
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.SkyHost
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.design.dialogPanelColor
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.InterFontFamily
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel

private const val STEPS = 4

private val METHODS = listOf(
    Triple("EGYPTIAN", "Egyptian General Authority", "الهيئة العامة المصرية للمساحة"),
    Triple("UMM_AL_QURA", "Umm Al-Qura (Mecca)", "جامعة أم القرى (مكة)"),
    Triple("MWL", "Muslim World League", "رابطة العالم الإسلامي"),
    Triple("ISNA", "Islamic Society of North America", "الجمعية الإسلامية لأمريكا الشمالية"),
    Triple("KARACHI", "University of Islamic Sciences, Karachi", "جامعة العلوم الإسلامية، كراتشي"),
)
private val SCHOOLS = listOf(
    Triple("SHAFII", "Standard (Shafi'i, Maliki, Hanbali)", "القياسي (الشافعي، المالكي، الحنبلي)"),
    Triple("HANAFI", "Hanafi", "الحنفي"),
)
private class AdhanChoice(val key: String, val en: String, val ar: String)
private val ADHAN_CHOICES = listOf(
    AdhanChoice("default", "Phone's default sound", "صوت الهاتف الافتراضي"),
    AdhanChoice("ahmad_alnafees", "Ahmad Alnafys", "أحمد النفيس"),
    AdhanChoice("mishary_alafasy", "Mishary Alafasy", "مشاري العفاسي"),
    AdhanChoice("saad_alghamdi", "Saad Alghamdy", "سعد الغامدي"),
)

/**
 * First run: four calm steps on the same sky and glass as the rest of the app.
 *   1. Welcome, and the language.
 *   2. Where you are (your location or a city), which is all the prayer times need.
 *   3. Reminders: allow notifications, choose the adhan.
 *   4. A short review (the two calculation choices live here, with sensible defaults), then in.
 */
@Composable
fun OnboardingScreen(
    viewModel: PrayerViewModel,
    currentLanguage: String = "en",
    onLanguageSelect: (String) -> Unit = {},
    onFinished: (themePreset: String, location: LocationPreset, notificationsEnabled: Boolean, calculationMethod: String, asrSchool: String, defaultAdhanSound: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ar = currentLanguage == "ar"
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var step by remember { mutableStateOf(0) }
    var location by remember { mutableStateOf<LocationPreset?>(null) }
    var method by remember { mutableStateOf("MWL") }
    var school by remember { mutableStateOf("SHAFII") }
    var sound by remember { mutableStateOf("default") }
    var notifications by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notifications = ok
        asked = true
    }

    BackHandler(enabled = step > 0) { step -= 1 }

    SkyHost(viewModel = viewModel, modifier = modifier) {
        val sky = LocalSky.current
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.screen),
        ) {
            // Where you are in the four steps.
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(STEPS) { i ->
                    Box(
                        Modifier.weight(1f).height(4.dp).clip(CircleShape)
                            .background(sky.ink.copy(alpha = if (i <= step) 0.9f else 0.2f)),
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val fwd = targetState > initialState
                        (slideInHorizontally(tween(320)) { if (fwd) it / 4 else -it / 4 } + fadeIn(tween(320))) togetherWith
                            (slideOutHorizontally(tween(220)) { if (fwd) -it / 4 else it / 4 } + fadeOut(tween(160)))
                    },
                    label = "onboarding",
                ) { s ->
                    when (s) {
                        0 -> WelcomeStep(ar, onLanguageSelect)
                        1 -> PlaceStep(ar, location) { location = it }
                        2 -> ReminderStep(ar, notifications, asked, sound, { sound = it }) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else notifications = true
                        }
                        else -> ReviewStep(ar, location, method, school, if (notifications) sound else null, { method = it }, { school = it })
                    }
                }
            }

            // Back, and the one big button.
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                val last = step == STEPS - 1
                val needsPlace = step == 1 && location == null
                BigButton(
                    label = when (step) {
                        0 -> if (ar) "ابدأ" else "Get started"
                        STEPS - 1 -> if (ar) "ابدأ باستخدام عِمران" else "Start using Emran"
                        else -> if (ar) "متابعة" else "Continue"
                    },
                    enabled = !needsPlace,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (last) {
                        val loc = location ?: (LocationPresets.list.firstOrNull { it.name.contains("Mecca") } ?: LocationPresets.list[0])
                        onFinished("EXPRESSIVE", loc, notifications, method, school, sound)
                    } else step += 1
                }
                if (step > 0) {
                    Text(
                        if (ar) "رجوع" else "Back",
                        color = sky.inkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { step -= 1 }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                } else Spacer(Modifier.height(44.dp))
            }
        }
    }
}

// ---- 1. Welcome -------------------------------------------------------------------------------

@Composable
private fun WelcomeStep(ar: Boolean, onLanguage: (String) -> Unit) {
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Pillars(Modifier.size(132.dp))
        Spacer(Modifier.height(26.dp))
        Text(
            if (ar) "عِمران" else "EMRAN",
            color = sky.ink, fontSize = if (ar) 48.sp else 38.sp, fontWeight = FontWeight.Light,
            fontFamily = if (ar) null else InterFontFamily, letterSpacing = if (ar) 0.sp else 8.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (ar) "الصلاة والقرآن والأذكار\nبهدوء، ودون إنترنت" else "Prayer, Quran and dhikr,\nquietly and offline.",
            color = sky.inkSoft, fontSize = 17.sp, lineHeight = 26.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(44.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("ar" to "العربية", "en" to "English").forEach { (code, name) ->
                val on = (code == "ar") == ar
                GlassPanel(
                    Modifier.weight(1f).height(62.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLanguage(code)
                        },
                    cornerRadius = 31.dp, tint = if (on) 1.9f else 0.8f,
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        if (on) {
                            Icon(Icons.Default.Check, null, tint = sky.ink, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(name, color = if (on) sky.ink else sky.inkSoft, fontSize = 20.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

/** The five pillars of Emran's icon, flat, inside a thin ring. */
@Composable
private fun Pillars(modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    Canvas(modifier) {
        val u = size.minDimension / 72f
        drawCircle(sky.ink.copy(alpha = 0.22f), size.minDimension / 2 - 1.5f * u, style = Stroke(1.2f * u))
        val tops = listOf(54f, 46f, 38f, 46f, 54f)
        tops.forEachIndexed { i, top ->
            val x = (36f + (i - 2) * 9f - 3f) * u
            val y0 = (top - 19.5f) * u
            val y1 = (73f - 19.5f) * u
            drawRoundRect(sky.ink, Offset(x, y0), Size(6f * u, 6f * u), CornerRadius(3f * u))
            drawRect(sky.ink, Offset(x, y0 + 3f * u), Size(6f * u, y1 - y0 - 3f * u))
        }
    }
}

// ---- 2. Place ---------------------------------------------------------------------------------

@Composable
private fun PlaceStep(ar: Boolean, selected: LocationPreset?, onPick: (LocationPreset) -> Unit) {
    val sky = LocalSky.current
    var query by remember { mutableStateOf("") }
    val lang = if (ar) "ar" else "en"
    val shown = remember(query, lang) {
        if (query.isBlank()) LocationPresets.list
        else LocationPresets.list.filter { LocationPresets.getLocalizedName(it.name, lang).contains(query, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        Header(
            if (ar) "أين أنت؟" else "Where are you?",
            if (ar) "لحساب مواقيت الصلاة في مدينتك." else "To work out the prayer times for your city.",
        )
        CurrentLocationButton(lang = lang, onLocationResolved = onPick)
        Spacer(Modifier.height(12.dp))

        if (selected != null) {
            GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 22.dp, tint = 1.5f) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = sky.ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        LocationPresets.getLocalizedName(selected.name, lang),
                        color = sky.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            if (ar) "أو اختر مدينتك" else "Or choose your city",
            color = sky.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 24.dp, tint = 0.8f) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = sky.inkSoft, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(if (ar) "ابحث عن مدينة أو دولة" else "Search a city or country", color = sky.inkSoft, fontSize = 16.sp)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = sky.ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(sky.ink), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassPanel(Modifier.fillMaxWidth().weight(1f), cornerRadius = 26.dp, tint = 0.7f) {
            LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                items(shown) { p ->
                    val on = selected?.name == p.name
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 50.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPick(p) }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            LocationPresets.getLocalizedName(p.name, lang),
                            color = if (on) sky.ink else sky.inkSoft, fontSize = 16.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (on) Icon(Icons.Default.Check, null, tint = sky.ink, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ---- 3. Reminders -----------------------------------------------------------------------------

@Composable
private fun ReminderStep(
    ar: Boolean,
    granted: Boolean,
    asked: Boolean,
    sound: String,
    onSound: (String) -> Unit,
    onAllow: () -> Unit,
) {
    val sky = LocalSky.current
    val context = LocalContext.current
    var playing by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playing = null
    }
    DisposableEffect(Unit) { onDispose { stop() } }
    fun preview(key: String) {
        if (playing == key) { stop(); return }
        stop()
        runCatching {
            val p = if (key == "default") {
                MediaPlayer().apply {
                    setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    prepare()
                }
            } else {
                val res = context.resources.getIdentifier(key, "raw", context.packageName)
                if (res != 0) MediaPlayer.create(context, res) else null
            }
            if (p != null) {
                p.setOnCompletionListener { stop() }
                player = p; playing = key; p.start()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header(
            if (ar) "التذكير بالصلاة" else "Prayer reminders",
            if (ar) "ينبّهك عِمران عند كل صلاة، دون أن يرسل شيئًا إلى أي مكان." else "Emran tells you at each prayer, and sends nothing anywhere.",
        )
        if (!granted) {
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Notifications, null, tint = sky.ink, modifier = Modifier.size(44.dp)) }
                Spacer(Modifier.height(20.dp))
                BigButton(if (ar) "السماح بالإشعارات" else "Allow notifications", enabled = true, onClick = onAllow)
                if (asked) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (ar) "لم يُسمح بها. يمكنك تفعيلها لاحقًا من إعدادات الهاتف." else "Not allowed. You can turn them on later in the phone's settings.",
                        color = sky.inkSoft, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.weight(1.4f))
        } else {
            Text(
                if (ar) "اختر صوت الأذان" else "Choose the adhan",
                color = sky.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ADHAN_CHOICES.forEach { v ->
                    val on = sound == v.key
                    GlassPanel(
                        Modifier.fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSound(v.key) },
                        cornerRadius = 24.dp, tint = if (on) 1.9f else 0.75f,
                    ) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (ar) v.ar else v.en, color = if (on) sky.ink else sky.inkSoft, fontSize = 17.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f),
                            )
                            if (on) Icon(Icons.Default.Check, null, tint = sky.ink, modifier = Modifier.size(20.dp))
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { preview(v.key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (playing == v.key) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    if (ar) "استماع" else "Listen", tint = sky.ink, modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- 4. Review --------------------------------------------------------------------------------

@Composable
private fun ReviewStep(
    ar: Boolean,
    location: LocationPreset?,
    method: String,
    school: String,
    sound: String?,
    onMethod: (String) -> Unit,
    onSchool: (String) -> Unit,
) {
    val sky = LocalSky.current
    val lang = if (ar) "ar" else "en"
    var dialog by remember { mutableStateOf<String?>(null) }
    val pop by animateFloatAsState(1f, tween(500), label = "pop")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.6f))
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Check, null, tint = sky.ink, modifier = Modifier.size((40 * pop).dp)) }
        Spacer(Modifier.height(18.dp))
        Text(if (ar) "كل شيء جاهز" else "You're all set", color = sky.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (ar) "يمكنك تغيير أي شيء لاحقًا من الإعدادات." else "You can change any of this later in Settings.",
            color = sky.inkSoft, fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 26.dp, tint = 0.8f) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                ReviewRow(if (ar) "المدينة" else "City", location?.let { LocationPresets.getLocalizedName(it.name, lang) } ?: "—", false) {}
                ReviewRow(if (ar) "طريقة الحساب" else "Calculation", METHODS.first { it.first == method }.let { if (ar) it.third else it.second }, true) { dialog = "method" }
                ReviewRow(if (ar) "العصر" else "Asr", SCHOOLS.first { it.first == school }.let { if (ar) it.third else it.second }, true) { dialog = "school" }
                ReviewRow(
                    if (ar) "الأذان" else "Adhan",
                    sound?.let { k -> ADHAN_CHOICES.first { it.key == k }.let { if (ar) it.ar else it.en } } ?: (if (ar) "بدون إشعارات" else "No reminders"),
                    false,
                ) {}
            }
        }
        Spacer(Modifier.weight(1f))
    }
    when (dialog) {
        "method" -> ChoiceDialog(
            title = if (ar) "طريقة الحساب" else "Calculation method",
            options = METHODS.map { it.first to (if (ar) it.third else it.second) },
            selectedKey = method, onPick = { onMethod(it); dialog = null }, onDismiss = { dialog = null },
        )
        "school" -> ChoiceDialog(
            title = if (ar) "حساب العصر" else "Asr calculation",
            options = SCHOOLS.map { it.first to (if (ar) it.third else it.second) },
            selectedKey = school, onPick = { onSchool(it); dialog = null }, onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun ReviewRow(label: String, value: String, editable: Boolean, onClick: () -> Unit) {
    val sky = LocalSky.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp)
            .then(if (editable) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = sky.inkSoft, fontSize = 14.sp, modifier = Modifier.width(96.dp))
        Text(value, color = sky.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (editable) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = sky.inkSoft, modifier = Modifier.size(20.dp))
    }
}

// ---- shared -----------------------------------------------------------------------------------

@Composable
private fun Header(title: String, sub: String) {
    val sky = LocalSky.current
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp)) {
        Text(title, color = sky.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(sub, color = sky.inkSoft, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun BigButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val sky = LocalSky.current
    Box(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(29.dp))
            .background(sky.ink.copy(alpha = if (enabled) 1f else 0.3f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = dialogPanelColor(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
