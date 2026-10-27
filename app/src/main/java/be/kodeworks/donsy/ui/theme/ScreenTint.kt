// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A soft pastel hue per tab, used only as a small accent: the bottom bar's selected-tab pill and
 * the circle behind the tab icon in the top bar. Backgrounds stay neutral so nothing competes for
 * attention (the app is meant for people with ADD/ADHD). The hue is never the only cue.
 */
enum class ScreenTint(val pastel: Color) {
    HOME(Color(0xFF8AD8B2)), // mint
    BRAIN_DUMP(Color(0xFFB9A4F2)), // lavender
    TASKS(Color(0xFFFFB48C)), // peach
    FOCUS(Color(0xFF8EC6F0)), // sky
}

/** How strongly the pastel shows on top of the theme's surface. */
private const val ACCENT_LIGHT = 0.55f
private const val ACCENT_DARK = 0.35f

/** The tab's accent colour, softened against the current surface so it suits light and dark mode. */
fun ColorScheme.tabAccent(tint: ScreenTint): Color =
    lerp(surface, tint.pastel, if (surface.luminance() < 0.5f) ACCENT_DARK else ACCENT_LIGHT)

/** The tab a screen belongs to, shown as a small coloured icon in its top bar. */
data class TabAccent(val icon: ImageVector, val tint: ScreenTint)

/** Provided for screens inside a tab; null on screens opened on top (Settings, the task editor). */
val LocalTabAccent = staticCompositionLocalOf<TabAccent?> { null }
