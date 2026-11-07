package io.github.sammynashed.emran.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * DEBUG ONLY. Shows one floating dhikr card right now (or hides it), so the overlay can be checked
 * over adb without opening the app:
 *   adb shell am broadcast -a io.github.sammynashed.emran.DEBUG_FLOAT_DHIKR [--es id baqiyat] [--ez hide true]
 */
class FloatingDhikrDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("hide", false)) { FloatingDhikrOverlay.hide(); return }
        val id = intent.getStringExtra("id")
        val d = FloatingDhikr.choices(context).firstOrNull { it.id == id } ?: FloatingDhikr.next(context) ?: return
        FloatingDhikrOverlay.show(context, d)
    }
}
