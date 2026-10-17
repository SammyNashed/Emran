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
    /**
     * null when this phone has no such switch (Google Pixel and other near-stock phones).
     * On Xiaomi it is read from the system; on the other brands that have one there is no way to
     * read it, so it is true only after the person says they have turned it on.
     */
    val autostartAllowed: Boolean?,
    /** Which brand's switch [autostartAllowed] is about, or null. */
    val oem: OemFamily? = null,
) {
    val allGood: Boolean
        get() = notificationsAllowed && exactAlarmAllowed && batteryUnrestricted && autostartAllowed != false

    val problems: Int
        get() = listOf(!notificationsAllowed, !exactAlarmAllowed, !batteryUnrestricted, autostartAllowed == false).count { it }

    companion object {
        /** MIUI's private app-op for "Autostart". */
        private const val OP_AUTO_START = 10008

        private const val KEY_AUTOSTART_CONFIRMED = "autostart_confirmed"
        private const val KEY_GUIDE_DONE = "alerts_guide_done"

        private fun prefs(context: Context) =
            context.getSharedPreferences("emran_settings", Context.MODE_PRIVATE)

        /** The person tells us they have turned the switch on (it cannot be read on most brands). */
        fun confirmAutostart(context: Context, confirmed: Boolean = true) {
            prefs(context).edit().putBoolean(KEY_AUTOSTART_CONFIRMED, confirmed).apply()
        }

        /** Whether the one-time "keep the adhan reliable" page should still be shown. */
        fun guideNeeded(context: Context): Boolean {
            if (OemFamily.detect() == null) return false
            if (prefs(context).getBoolean(KEY_GUIDE_DONE, false)) return false
            return read(context).autostartAllowed != true
        }

        fun markGuideDone(context: Context) {
            prefs(context).edit().putBoolean(KEY_GUIDE_DONE, true).apply()
        }

        fun read(context: Context): NotificationHealth {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() == true
            } else true
            val family = OemFamily.detect()
            val autostart = when {
                family == null -> null
                family == OemFamily.XIAOMI -> miuiAutostart(context)
                else -> prefs(context).getBoolean(KEY_AUTOSTART_CONFIRMED, false)
            }
            return NotificationHealth(
                notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                exactAlarmAllowed = exact,
                batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(context.packageName) == true,
                autostartAllowed = autostart,
                oem = family,
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

        /**
         * Opens this brand's own page for the switch, trying the known screens one after another and
         * ending on Emran's own settings page (always there) so the person is never left with nothing.
         */
        fun openAutostartSettings(context: Context) {
            val family = OemFamily.detect()
            val attempts = mutableListOf<Intent>()
            fun comp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
            when (family) {
                OemFamily.XIAOMI -> {
                    attempts += comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    attempts += Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
                }
                OemFamily.VIVO -> {
                    attempts += comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                    attempts += comp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                    attempts += comp("com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity")
                    attempts += comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
                }
                OemFamily.OPPO_FAMILY -> {
                    attempts += comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                    attempts += comp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
                    attempts += comp("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
                    attempts += comp("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
                }
                OemFamily.HUAWEI_FAMILY -> {
                    attempts += comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                    attempts += comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                    attempts += comp("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                }
                OemFamily.TRANSSION -> {
                    attempts += comp("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")
                    attempts += comp("com.itel.autostart", "com.itel.autostart.view.AutoStartActivity")
                }
                OemFamily.ASUS -> {
                    attempts += comp("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
                }
                OemFamily.SAMSUNG -> {
                    attempts += comp("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
                    attempts += Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
                }
                null -> {}
            }
            attempts += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
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
                Intent(context, PrayerAlarmReceiver::class.java).putExtra("PRAYER_NAME", "Maghrib").putExtra("TEST", true)
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

/**
 * The phone brands whose own battery manager can stop Emran from waking up at prayer time, and the
 * wording that tells a non-technical person exactly what to do about it.
 *
 * Source for which brands: DontKillMyApp.com's per-vendor data (Xiaomi, Vivo, Oppo/Realme/OnePlus,
 * Huawei/Honor, Tecno/Infinix/itel and Asus all have an "autostart" style switch; Samsung has
 * "Sleeping apps"). Google Pixel and near-stock phones have nothing to switch.
 * Only Xiaomi has been tried on a real phone; the others follow that site's published instructions.
 */
enum class OemFamily {
    XIAOMI, VIVO, OPPO_FAMILY, HUAWEI_FAMILY, TRANSSION, ASUS, SAMSUNG;

    /** One short line of what to do, in order. */
    fun steps(ar: Boolean): List<String> = when (this) {
        XIAOMI, VIVO -> if (ar) listOf("اضغط «افتح الإعداد» أدناه.", "ابحث عن «عِمران» في القائمة.", "شغّل المفتاح الذي بجانبه.")
        else listOf("Tap “Open the setting” below.", "Find “Emran” in the list.", "Switch it on.")
        OPPO_FAMILY -> if (ar) listOf("اضغط «افتح الإعداد» أدناه.", "ابحث عن «عِمران».", "شغّل «السماح بالتشغيل التلقائي» و«السماح بالنشاط في الخلفية».")
        else listOf("Tap “Open the setting” below.", "Find “Emran”.", "Switch on “Allow auto-launch” and “Allow background activity”.")
        HUAWEI_FAMILY -> if (ar) listOf("اضغط «افتح الإعداد» أدناه.", "ابحث عن «عِمران» وأوقف «الإدارة التلقائية».", "شغّل المفاتيح الثلاثة: التشغيل التلقائي، والتشغيل الثانوي، والعمل في الخلفية.")
        else listOf("Tap “Open the setting” below.", "Find “Emran” and turn off “Manage automatically”.", "Turn on all three switches: Auto-launch, Secondary launch and Run in background.")
        TRANSSION -> if (ar) listOf("اضغط «افتح الإعداد» أدناه (وإن لم يُفتح شيء فافتح تطبيق Phone Master).", "اختر «إدارة التشغيل التلقائي».", "شغّل «عِمران».")
        else listOf("Tap “Open the setting” below (if nothing opens, open the Phone Master app).", "Choose “Auto-start management”.", "Switch on “Emran”.")
        ASUS -> if (ar) listOf("افتح «مدير الجوال» ثم «مدير التشغيل التلقائي».", "ابحث عن «عِمران».", "اسمح له بالتشغيل.")
        else listOf("Open Mobile Manager, then Auto-start manager.", "Find “Emran”.", "Allow it.")
        SAMSUNG -> if (ar) listOf("اضغط «افتح الإعداد» أدناه.", "افتح «التطبيقات التي لا تنام أبدًا» وأضف «عِمران».", "وأوقف «إيقاف التطبيقات غير المستخدمة».")
        else listOf("Tap “Open the setting” below.", "Open “Never sleeping apps” and add “Emran”.", "Also switch off “Put unused apps to sleep”.")
    }

    fun title(ar: Boolean): String = when (this) {
        SAMSUNG -> if (ar) "ألا يوقف هاتفك عِمران" else "Don't let your phone put Emran to sleep"
        else -> if (ar) "السماح لعِمران بالتشغيل التلقائي" else "Let Emran start by itself"
    }

    companion object {
        /** The brand of this phone, or null when it has nothing to switch. */
        fun detect(): OemFamily? {
            val id = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
            return when {
                "xiaomi" in id || "redmi" in id || "poco" in id -> XIAOMI
                "vivo" in id || "iqoo" in id -> VIVO
                "oppo" in id || "realme" in id || "oneplus" in id -> OPPO_FAMILY
                "huawei" in id || "honor" in id -> HUAWEI_FAMILY
                "tecno" in id || "infinix" in id || "itel" in id || "transsion" in id -> TRANSSION
                "asus" in id -> ASUS
                "samsung" in id -> SAMSUNG
                else -> null
            }
        }
    }
}
