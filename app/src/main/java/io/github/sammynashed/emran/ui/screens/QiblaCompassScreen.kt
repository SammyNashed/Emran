package io.github.sammynashed.emran.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.sammynashed.emran.LocalDockVisible
import io.github.sammynashed.emran.ui.design.GlassButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.R
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.ScreenHeader
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.localization.LocalAppLanguage
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import kotlin.math.abs

/**
 * Qibla, for someone about to pray: one question, which way do I face?
 *
 * The prayer mat turns live toward the Kaaba. Turn until the Kaaba reaches the mark at the
 * top and the mat stands straight; the phone buzzes once and says so. Everything that makes
 * this accurate (true north, smoothing, interference) happens out of sight in
 * [rememberQiblaReading]; the user only ever sees one line of guidance.
 */
@Composable
fun QiblaCompassScreen(
    viewModel: PrayerViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ar = LocalAppLanguage.current == "ar"
    val sky = LocalSky.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val lat by viewModel.latitude.collectAsState()
    val lon by viewModel.longitude.collectAsState()
    val q = rememberQiblaReading(lat, lon)

    fun hasCamera() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    // Already allowed once: go straight to the room view. Otherwise start on the compass and
    // explain, in words, before the system asks.
    var wantRoom by remember { mutableStateOf(hasCamera()) }
    var refused by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        wantRoom = ok
        refused = !ok
    }
    val roomView = wantRoom && hasCamera()

    // The room view is full screen; the dock steps aside while it is up.
    val dock = LocalDockVisible.current
    DisposableEffect(roomView) {
        dock.value = !roomView
        onDispose { dock.value = true }
    }

    LaunchedEffect(q.facing) {
        if (q.facing) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    if (roomView) {
        Box(modifier.fillMaxSize().background(Color.Black)) {
            CameraFeed(Modifier.fillMaxSize())
            QiblaArOverlay(q)
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundCamButton(Icons.AutoMirrored.Filled.ArrowBack, if (ar) "رجوع" else "Back", onBack)
                RoundCamButton(Icons.Outlined.Explore, if (ar) "البوصلة" else "Compass") { wantRoom = false }
            }
            val (line, sub) = if (q.ready && !q.upright) {
                (if (ar) "أمسك الهاتف بشكل عمودي" else "Hold the phone upright") to null
            } else qiblaHint(q, ar)
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(line, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
                if (sub != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(sub, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
        return
    }

    // The dial takes what the header, the hint and the camera button leave, so on a short
    // phone it shrinks instead of pushing the button under the dock.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
    val dialSize = minOf(maxWidth * 0.80f, maxHeight - 400.dp).coerceAtLeast(170.dp)
    val tight = maxHeight < 720.dp
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen)
            .padding(bottom = 124.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(title = if (ar) "القبلة" else "Qibla", onBack = onBack)
        Spacer(Modifier.weight(1f))
        QiblaDial(q, Modifier.size(dialSize))
        Spacer(Modifier.height(if (tight) 12.dp else 28.dp))
        AnimatedContent(
            targetState = qiblaHint(q, ar),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "hint",
        ) { (line, sub) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(line, color = sky.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                if (sub != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(sub, color = sky.inkSoft, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // The camera is an extra, asked for politely and explained first.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassButton(
                if (ar) "اعرض القبلة في غرفتك" else "Show the Qibla in your room",
                onClick = {
                    if (hasCamera()) wantRoom = true else ask.launch(Manifest.permission.CAMERA)
                },
                filled = true,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (refused) (if (ar) "الكاميرا غير مسموحة. يمكنك السماح بها من إعدادات الهاتف." else "Camera access is off. You can allow it in the phone's settings.")
                else (if (ar) "تُستخدم الكاميرا للعرض فقط. لا يُسجَّل ولا يُحفظ أي شيء." else "The camera is only used to show the view. Nothing is recorded or saved."),
                color = sky.inkSoft, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp,
            )
        }
    }
    }
}

@Composable
private fun RoundCamButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** The one line a user reads (and, rarely, a quieter second line). */
fun qiblaHint(q: QiblaReading, ar: Boolean): Pair<String, String?> = when {
    !q.ready -> (if (ar) "جارٍ تحديد الاتجاه…" else "Finding the direction…") to null
    q.calibrate -> (if (ar) "حرّك الهاتف على شكل ٨" else "Move your phone in a figure 8") to
        (if (ar) "بعيداً عن المعادن والمغناطيس" else "away from metal and magnets")
    q.facing -> (if (ar) "أنت باتجاه القبلة" else "You're facing the Qibla") to null
    q.turn > 0 -> (if (abs(q.turn) < 25f) (if (ar) "دوّر قليلاً لليمين" else "Turn a little right") else (if (ar) "دوّر لليمين" else "Turn right")) to null
    else -> (if (abs(q.turn) < 25f) (if (ar) "دوّر قليلاً لليسار" else "Turn a little left") else (if (ar) "دوّر لليسار" else "Turn left")) to null
}

/**
 * The turn as one continuous angle, so crossing ±180° keeps going the short way instead of
 * spinning the mat all the way round.
 */
@Composable
fun rememberMatAngle(turn: Float): Float {
    var unwrapped by remember { mutableFloatStateOf(turn) }
    var last by remember { mutableFloatStateOf(turn) }
    var d = turn - last
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    if (d != 0f) {
        unwrapped += d
        last = turn
    }
    return unwrapped
}

/**
 * A ring with a mark at the top (straight ahead of the phone), the Kaaba on the ring where the
 * Qibla is, and the prayer mat pointing at it. Flat colours only.
 */
@Composable
fun QiblaDial(q: QiblaReading, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    val angle = rememberMatAngle(q.turn)
    val lit by animateFloatAsState(if (q.facing) 1f else 0f, tween(260), label = "lit")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 - 14.dp.toPx()
            // The ring: thin and quiet, firmer once aligned.
            drawCircle(sky.ink.copy(alpha = 0.18f + 0.30f * lit), r, style = Stroke((1.5f + 1.5f * lit).dp.toPx()))
            // Straight ahead: a short bar at the top.
            val w = 4.dp.toPx()
            drawRoundRect(
                color = sky.ink.copy(alpha = 0.55f + 0.45f * lit),
                topLeft = Offset(center.x - w / 2, center.y - r - 13.dp.toPx()),
                size = Size(w, 15.dp.toPx()),
                cornerRadius = CornerRadius(w / 2),
            )
            // The Kaaba, on the ring, in the direction of the Qibla.
            rotate(if (q.ready) angle else 0f) {
                drawKaaba(Offset(center.x, center.y - r), 22.dp.toPx(), sky.ink)
            }
        }
        Image(
            painter = painterResource(R.drawable.prayer_mat),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.62f)
                .graphicsLayer { rotationZ = if (q.ready) angle else 0f },
        )
    }
}

/** A flat little Kaaba: a black block with the gold band of the kiswa, on a thin halo. */
fun DrawScope.drawKaaba(at: Offset, s: Float, halo: Color) {
    drawCircle(halo.copy(alpha = 0.22f), s * 0.95f, at)
    val tl = Offset(at.x - s / 2, at.y - s / 2)
    drawRoundRect(Color(0xFF15161A), tl, Size(s, s), CornerRadius(s * 0.12f))
    drawRect(Color(0xFFD4AF5A), Offset(tl.x, tl.y + s * 0.26f), Size(s, s * 0.12f))
    drawRect(Color(0xFFD4AF5A), Offset(tl.x + s * 0.60f, tl.y + s * 0.50f), Size(s * 0.18f, s * 0.36f))
}

