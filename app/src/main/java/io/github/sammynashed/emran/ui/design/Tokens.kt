package io.github.sammynashed.emran.ui.design

import androidx.compose.ui.unit.dp

/**
 * Design tokens for the redesign. New UI takes its spacing and corner radii from
 * here instead of raw `.dp` literals, so the whole app can be tuned in one place.
 * (Older screens still carry ~1,100 inline literals; they migrate stage by stage.)
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Side margin of every full-width screen. */
    val screen = 20.dp
}

object Radius {
    /** Big panels and sheets. */
    val panel = 32.dp

    /** Rows and cards. */
    val row = 26.dp

    /** Small controls (bells, chips). */
    val control = 18.dp
}

/** Smallest touch target; key actions use [Target.key]. Elder-friendly by rule. */
object Target {
    val min = 48.dp
    val key = 56.dp
}
