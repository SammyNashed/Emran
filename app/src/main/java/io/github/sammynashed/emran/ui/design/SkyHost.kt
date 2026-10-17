package io.github.sammynashed.emran.ui.design

import android.app.Activity
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.layer.CompositingStrategy

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.toSize
import androidx.core.view.WindowCompat
import io.github.sammynashed.emran.ui.viewmodel.PrayerViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Developer switches for checking the design on a real phone without waiting for
 * the sun. Visual only; nothing here touches prayer times or alarms.
 *
 *   adb shell am start -n <pkg>/.MainActivityAliasDefault --ei SKY_DEBUG_MIN 1080
 *   adb shell am start ... --es GLASS_TIER FLAT        (FLAT | BLUR | LENS)
 */
object SkyDebug {
    /** Minute of the day (0 until 1440, fractional so a slow timelapse glides) to show instead of the real time, or null. */
    var minuteOfDay by mutableStateOf<Double?>(null)

    /** [minuteOfDay] as a time of day, or null when the real clock is in use. */
    fun timeOfDay(): LocalTime? = minuteOfDay?.let { LocalTime.ofNanoOfDay((it * 60e9).toLong().coerceIn(0L, 86_399_999_999_999L)) }

    /** How fast clouds and stars move; above 1 only during a SKY_TIMELAPSE, which also lifts the frame-rate caps. */
    var clockSpeed by mutableFloatStateOf(1f)

    /** Preview the Friday states (Masbaha challenge) on any day: adb ... --ez FORCE_FRIDAY true */
    var forceFriday by mutableStateOf(false)

    /** Show the first-run onboarding on top of an installed app, saving nothing: adb ... --ez FORCE_ONBOARDING true */
    var forceOnboarding by mutableStateOf(false)

    /** Show the one-time "keep the adhan reliable" page: adb ... --ez FORCE_GUIDE true */
    var forceGuide by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    /** Debug: show this entry of the daily-ayah list instead of today's. */
    var ayahIndex by mutableStateOf<Int?>(null)

    /** Force a glass tier to check the fallbacks, or null for the device's own. */
    var glassTier by mutableStateOf<GlassTier?>(null)

    /** Show the UI in "ar" or "en" without touching the saved language, or null. */
    var language by mutableStateOf<String?>(null)
}

/**
 * The sky's clock: seconds, ~30 updates a second, only while the app is on screen.
 *
 * It stands still (no battery cost at all) when the user has turned animations off
 * or battery saver is on. Readers read `.value` inside a draw lambda, so a tick
 * redraws the sky and nothing else; no screen recomposes.
 */
/** Where in the cloud pattern this launch starts, so the sky is not identical every time. Small on purpose. */
private val launchOffset: Double = kotlin.random.Random.nextDouble(0.0, 120.0)

@Composable
fun rememberSkyClock(animate: Boolean = true): State<Float> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(owner, animate) {
        if (!animate) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val motion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            val saver = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
            if (!motion || saver) return@repeatOnLifecycle
            var last = 0L
            var prev = -1L
            var elapsed = 0.0
            while (true) {
                withFrameNanos { n ->
                    if (prev < 0) prev = n
                    val speed = SkyDebug.clockSpeed
                    elapsed += (n - prev) / 1_000_000_000.0 * speed
                    prev = n
                    // 20 fps: stars still twinkle smoothly, a third less work. A promo timelapse
                    // (speed > 1) draws every frame, because a recording shows every dropped one.
                    if (speed > 1f || n - last >= 50_000_000L) {
                        last = n
                        // Seconds since the sky started (plus a per-launch offset), NOT the phone's uptime:
                        // the cloud shader adds this to its noise coordinates, and a number in the tens of
                        // thousands leaves a low-precision GPU too few digits (flat blocky clouds).
                        time.floatValue = (launchOffset + elapsed).toFloat()
                    }
                }
            }
        }
    }
    return time
}

/**
 * Puts the sky behind everything inside it.
 *
 * It reads today's and tomorrow's prayer times from the ViewModel, so a change of
 * city, method or Fine-Tune offset moves the sky with it, and re-reads the clock
 * every 20 seconds (the sky moves slowly; this is plenty).
 */
@Composable
fun SkyHost(
    viewModel: PrayerViewModel,
    modifier: Modifier = Modifier,
    /** False on screens not redesigned yet: they keep their own background and bar style. */
    showSky: Boolean = true,
    /** Status/gesture-bar icon style to use when the sky is not showing. */
    fallbackLightBars: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val pTimes by viewModel.prayerTimes.collectAsState()
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            tick = System.currentTimeMillis()
        }
    }

    val debugMinute = SkyDebug.minuteOfDay
    val sky = remember(tick / 20_000, pTimes, debugMinute) {
        val zoneNow = LocalDateTime.now(pTimes.zone)
        val day = LocalDate.now(pTimes.zone)
        val now = SkyDebug.timeOfDay()?.let { LocalDateTime.of(day, it) } ?: zoneNow
        SkyEngine.compute(now, viewModel.prayerTimesOn(day), viewModel.prayerTimesOn(day.plusDays(1)))
    }

    // Status-bar and gesture-bar icons must stay readable on a bright sky.
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val light = if (showSky) sky.darkInk > 0.5f else fallbackLightBars
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    SkySettings.ensureLoaded(LocalContext.current)
    var size by remember { mutableStateOf(Size.Zero) }
    val clock = rememberSkyClock(animate = showSky)
    val appContext = LocalContext.current.applicationContext
    val painter = remember { SkyPainter(appContext) }
    val layer = rememberGraphicsLayer()
    // The cloud texture refreshes ~12 times a second; stars and specks (cheap, and they need to look smooth) at 30.
    val slowClock = remember(clock) {
        derivedStateOf {
            if (SkyDebug.clockSpeed > 1f) clock.value   // timelapse recording: every frame
            else kotlin.math.floor(clock.value * SKY_TEXTURE_FPS) / SKY_TEXTURE_FPS
        }
    }
    val recorded = remember { object { var time = -1f; var size = IntSize.Zero; var sky: SkyState? = null; var calm = false } }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it.toSize() },
    ) {
        // The sky lives in its OWN layer. Painted on the root instead, every tick would
        // re-record the whole screen (all the text, rows and icons above it); in its own
        // layer a tick redraws the sky and nothing else.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { clip = false }
            .drawBehind {
                if (showSky && this.size.width > 0f && this.size.height > 0f) {
                    // One small render of the sky per tick; the window and every glass pane
                    // scale up the same texture, so the cloud shader runs once, not eight times.
                    val small = IntSize(
                        (this.size.width / SKY_DOWNSCALE).toInt().coerceAtLeast(1),
                        (this.size.height / SKY_DOWNSCALE).toInt().coerceAtLeast(1),
                    )
                    val t = slowClock.value
                    // Re-render the cloud texture only when its clock moved on (or the size/sky changed);
                    // the recorded layer is kept between ticks.
                    if (t != recorded.time || small != recorded.size || sky != recorded.sky || SkySettings.calm != recorded.calm) {
                        recorded.time = t; recorded.size = small; recorded.sky = sky; recorded.calm = SkySettings.calm
                        layer.record(this, layoutDirection, small) {
                            painter.paint(this, sky, Size(small.width.toFloat(), small.height.toFloat()), t)
                        }
                    }
                    layer.scaleX = this.size.width / small.width
                    layer.scaleY = this.size.height / small.height
                    layer.pivotOffset = Offset.Zero
                    layer.compositingStrategy = CompositingStrategy.Offscreen
                    drawSkyTexture(layer, sky, this.size, clock.value)
                }
            },
        )
        CompositionLocalProvider(
            LocalSky provides sky, LocalSkySize provides size, LocalSkyTime provides clock, LocalSkySlowTime provides slowClock,
            LocalSkyLayer provides layer,
        ) {
            content()
        }
    }
}
