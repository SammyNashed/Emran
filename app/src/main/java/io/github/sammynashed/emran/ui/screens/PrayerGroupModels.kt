package io.github.sammynashed.emran.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*

import androidx.compose.foundation.layout.*

import androidx.compose.material3.*

import androidx.compose.runtime.*

/*
 * Prayer grouping models and formatting helpers shared by the home and
 * today's-prayers screens.
 *
 * Part of the home screen; same package as HomeScreen.kt, so no additional
 * imports are required between them.
 */

fun formatPrayerTime(dateTime: java.time.LocalDateTime, lang: String = "en"): String {
    return io.github.sammynashed.emran.data.TimeFormat.full(dateTime, lang == "ar")
}
