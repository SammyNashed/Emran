package io.github.sammynashed.emran.service

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Everything that has to be true for the adhan to actually reach the user.
 *
 * "Exact alarms allowed" alone is not enough. On Xiaomi/HyperOS a separate
 * *Autostart* permission decides whether the system may start Emran's process to
 * deliver an alarm at all; with it off, a correctly scheduled alarm fires into
 * nothing and no notification appears. There is no public API for it, so it is
 * read through the same app-op the Security app writes.
 */
data class NotificationHealth(
    val notificationsAllowed: Boolean,
    val exactAlarmAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    /** null when this device has no such permission (everything except MIUI/HyperOS). */
    val autostartAllowed: Boolean?
) {
    val allGood: Boolean
        get() = notificationsAllowed && exactAlarmAllowed && batteryUnrestricted && autostartAllowed != false

    val problems: Int
        get() = listOf(!notificationsAllowed, !exactAlarmAllowed, !batteryUnrestricted, autostartAllowed == false).count { it }

    companion object {
        /** MIUI's private app-op for "Autostart". */
        private const val OP_AUTO_START = 10008

        fun read(context: Context): NotificationHealth {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() == true
            } else true
            return NotificationHealth(
                notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                exactAlarmAllowed = exact,
                batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(context.packageName) == true,
                autostartAllowed = miuiAutostart(context)
            )
        }

        private fun isXiaomiFamily(): Boolean {
            val m = Build.MANUFACTURER.lowercase()
            return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
        }

        private fun miuiAutostart(context: Context): Boolean? {
            if (!isXiaomiFamily()) return null
            return try {
                val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val check = AppOpsManager::class.java.getMethod(
                    "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
                )
                (check.invoke(ops, OP_AUTO_START, Process.myUid(), context.packageName) as Int) == AppOpsManager.MODE_ALLOWED
            } catch (e: Exception) {
                null
            }
        }

        /** Opens the Security app's Autostart list, or Emran's own settings page as a fallback. */
        fun openAutostartSettings(context: Context) {
            val attempts = listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
            for (i in attempts) {
                try {
                    context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (e: Exception) {
                    // try the next one
                }
            }
        }

        fun openNotificationSettings(context: Context) {
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(i)
            } catch (e: Exception) {
                openAutostartSettings(context)
            }
        }

        /** Fires the real adhan path once, so the user can hear and see whether it gets through. */
        fun sendTest(context: Context) {
            context.sendBroadcast(
                Intent(context, PrayerAlarmReceiver::class.java).putExtra("PRAYER_NAME", "Maghrib")
            )
        }
    }
}

/**
 * The current [NotificationHealth], re-read every time the screen resumes. Permissions
 * are changed in other apps' screens, so a value read once goes stale the moment the
 * user comes back from granting one -- which is exactly when they look at it.
 */
@Composable
fun rememberNotificationHealth(): NotificationHealth {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var health by remember { mutableStateOf(NotificationHealth.read(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) health = NotificationHealth.read(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return health
}
