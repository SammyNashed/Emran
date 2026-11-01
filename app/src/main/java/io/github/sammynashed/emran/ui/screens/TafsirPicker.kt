package io.github.sammynashed.emran.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.sammynashed.emran.data.TafsirLibrary
import io.github.sammynashed.emran.ui.localization.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Tafsir: Ibn Kathir ▾": the tafsir being shown, and the way to change it. Opens the list of every installed
 * tafsir (the choice is remembered per language), with a button to add a pack file and a bin on the ones the
 * user added. Drawn in whatever colours the caller passes, so it sits on the page sheet and on the sky screens.
 */
@Composable
fun TafsirChip(
    ar: Boolean,
    ink: Color,
    panel: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lang = if (ar) "ar" else "en"
    var open by remember { mutableStateOf(false) }
    val tick = TafsirLibrary.version.intValue
    val current = remember(tick, lang) {
        TafsirLibrary.editions(context).firstOrNull { it.id == TafsirLibrary.selectedId(context, lang) }
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .clickable { open = true }
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (ar) "التفسير: " else "Tafsir: ") + (current?.name(ar) ?: ""),
            color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
        Icon(Icons.Default.ArrowDropDown, null, tint = accent, modifier = Modifier.size(20.dp))
    }
    if (open) TafsirDialog(ar, ink, panel, accent) { open = false }
}

@Composable
private fun TafsirDialog(ar: Boolean, ink: Color, panel: Color, accent: Color, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = if (ar) "ar" else "en"
    val tick = TafsirLibrary.version.intValue
    val editions = remember(tick, lang) { TafsirLibrary.editionsFor(context, lang) }
    val selected = remember(tick, lang) { TafsirLibrary.selectedId(context, lang) }
    var confirmDelete by remember { mutableStateOf<TafsirLibrary.Edition?>(null) }
    var busy by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val added = withContext(Dispatchers.IO) { TafsirLibrary.import(context, uri) }
            busy = false
            if (added != null) {
                TafsirLibrary.select(context, added.lang, added.id)
                Toast.makeText(context, if (ar) "أُضيف: ${added.nameAr}" else "Added: ${added.nameEn}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, if (ar) "هذا الملف ليس تفسيرًا صالحًا" else "That file is not a valid tafsir pack", Toast.LENGTH_LONG).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(panel)
                .border(1.dp, ink.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Text(if (ar) "اختر التفسير" else "Choose a tafsir", color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                editions.forEach { e ->
                    val on = e.id == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) accent.copy(alpha = 0.14f) else ink.copy(alpha = 0.05f))
                            .clickable { TafsirLibrary.select(context, lang, e.id); onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name(ar), color = ink, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                            val sub = buildString {
                                append(if (e.lang == "ar") (if (ar) "عربي" else "Arabic") else if (e.lang == "en") (if (ar) "إنجليزي" else "English") else e.lang)
                                if (!e.builtIn && e.sizeBytes > 0) append(" · ").append("%.1f MB".format(e.sizeBytes / 1e6))
                            }
                            Text(sub, color = ink.copy(alpha = 0.55f), fontSize = 12.sp)
                        }
                        if (on) Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(20.dp))
                        if (!e.builtIn) {
                            Box(
                                Modifier.padding(start = 6.dp).size(36.dp).clip(CircleShape).clickable { confirmDelete = e },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Delete, if (ar) "حذف" else "Delete", tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable(enabled = !busy) { pick.launch(arrayOf("*/*")) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) (if (ar) "جارٍ الإضافة…" else "Adding…") else (if (ar) "إضافة تفسير من ملف" else "Add a tafsir from a file"),
                    color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                if (ar) "ملفات التفاسير (\u200E.emtafsir\u200E) تصلك من قناة عِمران. التطبيق يعمل دون إنترنت."
                else "Tafsir files (.emtafsir) come from the Emran channel. The app itself stays offline.",
                color = ink.copy(alpha = 0.5f), fontSize = 11.sp, lineHeight = 15.sp,
            )
        }
    }

    confirmDelete?.let { e ->
        Dialog(onDismissRequest = { confirmDelete = null }) {
            Column(
                Modifier.clip(RoundedCornerShape(24.dp)).background(panel).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(if (ar) "حذف ${e.nameAr}؟" else "Delete ${e.nameEn}?", color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (ar) "يُحرَّر ${"%.1f".format(e.sizeBytes / 1e6)} ميغابايت. يمكنك إضافته من الملف في أي وقت."
                    else "Frees ${"%.1f".format(e.sizeBytes / 1e6)} MB. You can add it again from the file any time.",
                    color = ink.copy(alpha = 0.7f), fontSize = 14.sp, lineHeight = 20.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    Text(
                        if (ar) "إلغاء" else "Cancel", color = ink.copy(alpha = 0.7f), fontSize = 15.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { confirmDelete = null }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    Text(
                        if (ar) "احذف" else "Delete", color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                            TafsirLibrary.delete(context, e.id)
                            confirmDelete = null
                        }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
