package io.github.sammynashed.emran.glance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.Text
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.theme.EmranGlanceTheme
import io.github.sammynashed.emran.glance.theme.Glass
import io.github.sammynashed.emran.glance.theme.GlassPane

/**
 * Today's Prayers V2 -- the home screen's astrolabe compass, on the home screen.
 *
 * The dial itself is a Bitmap drawn by [AstrolabeRenderer], because Glance
 * cannot draw arcs. The centre is composed in Glance on top of it rather than
 * baked into the bitmap, so the countdown there is a live Chronometer and the
 * text stays crisp at any scale instead of being resampled with the image.
 */
@Composable
fun TodayPrayersV2WidgetUi(state: EmranWidgetState) {
    val c = EmranGlanceTheme.colors
    val lang = state.language
    val rtl = WidgetText.isRtl(lang)
    val size = LocalSize.current

    val times = state.todayPrayerEpochMillis
    val progress = AstrolabeRenderer.progressFor(times, System.currentTimeMillis())
    val labels = state.todayPrayerNames.map { WidgetText.prayerName(it, lang) }
    val highlight = AstrolabeRenderer.indexOf(state.nextPrayerName)

    val bitmap = AstrolabeRenderer.render(
        context = LocalContext.current,
        accent = c.accentRaw,
        onSurface = c.textPrimaryRaw,
        progress = progress,
        labels = labels,
        highlightIndex = highlight,
        rtl = rtl
    )

    // The dial is square; take the smaller edge so it never crops.
    val dial = if (size.width < size.height) size.width else size.height
    val dialSize = (dial - 16.dp).coerceAtLeast(120.dp)

    val nextName = WidgetText.prayerName(state.nextPrayerName, lang)
    val nextTime = WidgetText.clock(state.nextPrayerEpochMillis, state.use24Hour)
    val compact = dialSize < 200.dp

    GlassPane(
        modifier = GlanceModifier.fillMaxSize(),
        contentPadding = 8.dp,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier.size(dialSize),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                modifier = GlanceModifier.size(dialSize)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FontText(
                    text = if (nextName.isBlank()) WidgetText.loading(lang) else nextName,
                    color = c.textPrimaryRaw,
                    fontSizeSp = if (compact) 17f else 22f,
                    font = WidgetFont.READEX,
                    align = WidgetAlign.CENTER
                )
                LiveCountdown(
                    targetEpochMillis = state.nextPrayerEpochMillis,
                    color = c.accentRaw,
                    fontSizeSp = if (compact) 14f else 18f,
                    centered = true
                )
                if (!compact) {
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Text(
                        text = nextTime,
                        style = Glass.Type.label(c.textPrimaryRaw, rtl),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
