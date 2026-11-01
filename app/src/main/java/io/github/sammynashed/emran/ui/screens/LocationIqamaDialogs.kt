package io.github.sammynashed.emran.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.data.Iqama
import io.github.sammynashed.emran.data.PrayerNames
import io.github.sammynashed.emran.ui.design.ChoiceRow
import io.github.sammynashed.emran.ui.design.GlassDialog
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.localization.Text
import io.github.sammynashed.emran.ui.theme.InterFontFamily

/** Turn "follow my location" on or off, and whether a new country should bring its usual method. */
@Composable
fun FollowLocationDialog(
    ar: Boolean,
    on: Boolean,
    autoMethod: Boolean,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onAutoMethod: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sky = LocalSky.current
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "تتبّع موقعي" else "Follow my location",
        dismissLabel = if (ar) "إغلاق" else "Close",
    ) {
        Text(
            if (ar) "عندما تسافر، تتحدّث مواقيت الصلاة تلقائياً إلى المكان الذي أنت فيه، فلا تحتاج أن تبحث عن أقرب مسجد لتعرف الوقت. يُفحص موقعك عند فتح عِمران فقط، ولا يخرج أي شيء من هاتفك."
            else "When you travel, prayer times move to where you are, so you never need a mosque nearby to know the time. Your location is checked when you open Emran, and nothing ever leaves your phone.",
            color = sky.inkSoft, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 8.dp),
        )
        ChoiceRow(if (ar) "مفعّل" else "On", selected = on, onClick = onTurnOn)
        ChoiceRow(if (ar) "متوقف" else "Off", selected = !on, onClick = onTurnOff)
        if (on) {
            Spacer(Modifier.height(8.dp))
            ChoiceRow(
                if (ar) "اختر الطريقة لي حسب البلد" else "Pick the method for me by country",
                selected = autoMethod,
                sublabel = if (ar) "مصر: المصرية، السعودية: أم القرى… وغيرها رابطة العالم الإسلامي" else "Egypt: Egyptian, Saudi: Umm al-Qura, and so on; elsewhere Muslim World League",
                onClick = { onAutoMethod(!autoMethod) },
            )
        }
    }
}

/**
 * Minutes from each adhan to its iqama. Emran cannot know your mosque, so these are only a start:
 * set the gaps your own mosque keeps. Big steppers, one minute at a time.
 */
@Composable
fun IqamaDialog(
    ar: Boolean,
    lang: String,
    minutes: Map<String, Int>,
    onChange: (key: String, minutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sky = LocalSky.current
    GlassDialog(
        onDismiss = onDismiss,
        title = if (ar) "الإقامة" else "Iqama",
        subtitle = if (ar) "كم دقيقة بعد الأذان تُقام الصلاة في مسجدك" else "How many minutes after the adhan your mosque prays",
        dismissLabel = if (ar) "تم" else "Done",
    ) {
        Iqama.KEYS.forEach { key ->
            val m = minutes[key] ?: Iqama.DEFAULTS.getValue(key)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PrayerNames.name(key, ar, java.time.LocalDate.of(2000, 1, 3)), color = sky.ink, fontSize = 17.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                )
                // Minus on the left, plus on the right, in Arabic too: numbers do not mirror.
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Stepper("−") { onChange(key, m - 1) }
                Text(
                    if (ar) "$m د" else "$m min", color = sky.ink, fontSize = 17.sp, fontFamily = InterFontFamily, bypassNumerals = true,
                    textAlign = TextAlign.Center, modifier = Modifier.width(72.dp), maxLines = 1, softWrap = false,
                )
                Stepper("+") { onChange(key, m + 1) }
                }
            }
        }
        Text(
            if (ar) "يوم الجمعة لا تُعرض إقامة الظهر لأنها تتبع الخطبة." else "On Fridays no iqama is shown for midday, because it follows the khutba.",
            color = sky.inkSoft, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** A 48 dp round button: the smallest target Emran allows. */
@Composable
private fun Stepper(label: String, onClick: () -> Unit) {
    val sky = LocalSky.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = sky.ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}
