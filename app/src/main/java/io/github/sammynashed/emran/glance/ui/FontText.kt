package io.github.sammynashed.emran.glance.ui

import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import io.github.sammynashed.emran.R

/** Which bundled face to render with. */
enum class WidgetFont { OUTFIT, READEX }

/** Horizontal placement inside the widget's width. */
enum class WidgetAlign { START, CENTER, END }

/**
 * Widget text in one of Emran's bundled fonts.
 *
 * Glance's `TextStyle.fontFamily` takes a family *name* and resolves it the way
 * `TextView.setFontFamily(String)` does -- against fonts the system knows about.
 * An app's own `res/font` resource is not one of those, so bundled fonts are
 * unreachable from Glance Text no matter what is passed.
 *
 * A RemoteViews layout is different: it is inflated with this app's resources,
 * so `android:fontFamily="@font/outfit"` declared in the XML resolves normally.
 * This composable hosts such a layout through [AndroidRemoteViews] and sets the
 * text, colour and size on it at runtime.
 *
 * Use it for the pieces that carry the design -- hero numbers, prayer names --
 * rather than every label: each instance is a nested RemoteViews, and widgets
 * have a finite view budget.
 */
@Composable
fun FontText(
    text: String,
    color: Color,
    fontSizeSp: Float,
    font: WidgetFont = WidgetFont.OUTFIT,
    align: WidgetAlign = WidgetAlign.START,
    modifier: GlanceModifier = GlanceModifier
) {
    if (text.isBlank()) return
    val context = LocalContext.current

    val layout = when (font) {
        WidgetFont.OUTFIT -> when (align) {
            WidgetAlign.START -> R.layout.widget_text_outfit_start
            WidgetAlign.CENTER -> R.layout.widget_text_outfit_center
            WidgetAlign.END -> R.layout.widget_text_outfit_end
        }
        WidgetFont.READEX -> when (align) {
            WidgetAlign.START -> R.layout.widget_text_readex_start
            WidgetAlign.CENTER -> R.layout.widget_text_readex_center
            WidgetAlign.END -> R.layout.widget_text_readex_end
        }
    }

    val views = RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.widget_font_text, text)
        setTextColor(R.id.widget_font_text, color.toArgb())
        setTextViewTextSize(R.id.widget_font_text, TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
    }

    // AndroidRemoteViews gives Glance nothing to measure, so the line box has to
    // be reserved explicitly or the row collapses.
    AndroidRemoteViews(
        remoteViews = views,
        modifier = modifier.fillMaxWidth().height((fontSizeSp * 1.35f).dp)
    )
}
