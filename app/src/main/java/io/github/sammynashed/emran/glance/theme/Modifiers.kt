package io.github.sammynashed.emran.glance.theme

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.background

import androidx.glance.appwidget.cornerRadius
import androidx.compose.ui.unit.dp

/**
 * Extension functions to easily apply Emran Glance Theme colors to modifiers.
 */

@Composable
fun GlanceModifier.appBackground(): GlanceModifier = this.background(EmranGlanceTheme.colors.background)

@Composable
fun GlanceModifier.appSurface(): GlanceModifier = this.background(EmranGlanceTheme.colors.surface)

@Composable
fun GlanceModifier.appWidgetBackground(radius: androidx.compose.ui.unit.Dp = 16.dp): GlanceModifier =
    this.background(EmranGlanceTheme.colors.surface).cornerRadius(radius)
