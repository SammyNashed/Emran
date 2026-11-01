package io.github.sammynashed.emran.data

import io.github.sammynashed.emran.BuildConfig

/**
 * Where "Report", "Suggest" and thank-you wall messages go. The number is not in the source:
 * release builds read `support.whatsapp` from the git-ignored local.properties. A build without
 * it, including every fdroid build, has nowhere to send them and hides those buttons.
 */
object SupportContact {
    val available: Boolean get() = BuildConfig.SUPPORT_WHATSAPP.isNotEmpty()

    fun whatsappUrl(text: String): String =
        "https://wa.me/${BuildConfig.SUPPORT_WHATSAPP}?text=" + java.net.URLEncoder.encode(text, "UTF-8")
}
