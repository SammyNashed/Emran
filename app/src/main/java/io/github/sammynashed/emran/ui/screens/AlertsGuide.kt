package io.github.sammynashed.emran.ui.screens

import android.content.Intent
import android.net.Uri

import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sammynashed.emran.service.NotificationHealth
import io.github.sammynashed.emran.service.OemFamily
import io.github.sammynashed.emran.service.rememberNotificationHealth
import io.github.sammynashed.emran.ui.design.GlassPanel
import io.github.sammynashed.emran.ui.design.LocalSky
import io.github.sammynashed.emran.ui.design.Space
import io.github.sammynashed.emran.ui.design.dialogPanelColor
import io.github.sammynashed.emran.ui.localization.Text

/**
 * Shown once, right after setup, only on phone brands that can stop an app from waking up at prayer
 * time. It says why in plain words, what to tap, and lets the person carry on. It is never nagging:
 * "Later" closes it for good, and the same thing stays reachable in Settings → Reliable alerts.
 */
@Composable
fun AlertsGuide(ar: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val sky = LocalSky.current
    val context = LocalContext.current
    val health = rememberNotificationHealth()
    val oem = health.oem ?: OemFamily.detect()
    var confirmed by remember { mutableStateOf(false) }
    val done = confirmed || health.autostartAllowed == true

    BackHandler { NotificationHealth.markGuideDone(context); onDone() }

    Box(modifier.fillMaxSize().background(dialogPanelColor().copy(alpha = 1f))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.screen)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (ar) "خطوة أخيرة صغيرة" else "One last small step",
                    color = sky.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (ar) "هاتفك يحاول توفير البطارية بإيقاف التطبيقات التي لم تفتحها منذ فترة. إذا حدث هذا لِعمران فقد لا يُسمع الأذان. تحتاج دقيقة واحدة فقط، ومرة واحدة."
                    else "Your phone tries to save battery by switching off apps you haven't opened for a while. If that happens to Emran, the adhan may not ring. This takes one minute, and you only do it once.",
                    color = sky.inkSoft, fontSize = 17.sp, lineHeight = 25.sp,
                )

                if (oem != null) {
                    GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 26.dp, tint = 0.8f) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(oem.title(ar), color = sky.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                if (done) Icon(Icons.Default.Check, null, tint = sky.ink, modifier = Modifier.size(22.dp))
                            }
                            oem.steps(ar).forEachIndexed { i, text ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(
                                        Modifier.size(26.dp).clip(CircleShape).background(sky.ink.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("${i + 1}", color = sky.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                                    Spacer(Modifier.width(12.dp))
                                    Text(text, color = sky.ink, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Every phone: don't limit Emran's battery. One system question, one tap.
                GlassPanel(Modifier.fillMaxWidth(), cornerRadius = 26.dp, tint = 0.7f) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (ar) "لا تقيّد بطارية عِمران" else "Don't limit Emran's battery",
                                color = sky.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                            )
                            if (health.batteryUnrestricted) Icon(Icons.Default.Check, null, tint = sky.ink, modifier = Modifier.size(22.dp))
                        }
                        Text(
                            if (ar) "ستظهر لك رسالة من النظام. اختر «السماح»." else "Your phone will ask. Choose “Allow”.",
                            color = sky.inkSoft, fontSize = 15.sp, lineHeight = 21.sp,
                        )
                        if (!health.batteryUnrestricted) {
                            Text(
                                if (ar) "اسأل الآن" else "Ask now",
                                color = sky.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            // Buttons.
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (oem != null && !done) {
                    GuideButton(if (ar) "افتح الإعداد" else "Open the setting", filled = true) { NotificationHealth.openAutostartSettings(context) }
                    Spacer(Modifier.height(10.dp))
                    GuideButton(if (ar) "فعّلته" else "I've done it", filled = false) {
                        NotificationHealth.confirmAutostart(context)
                        confirmed = true
                    }
                } else {
                    GuideButton(if (ar) "تم" else "Done", filled = true) { NotificationHealth.markGuideDone(context); onDone() }
                }
                Text(
                    if (ar) "لاحقًا، من الإعدادات" else "Later, from Settings",
                    color = sky.inkSoft, fontSize = 14.sp,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            NotificationHealth.markGuideDone(context); onDone()
                        }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val sky = LocalSky.current
    Box(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(27.dp))
            .background(if (filled) sky.ink else sky.ink.copy(alpha = 0.12f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) dialogPanelColor() else sky.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}
