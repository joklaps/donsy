// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** Material's minimum touch target. */
private val MinTouchTarget = 48.dp

/**
 * Checks every tappable element on screen: it has a name a screen reader can say (text or a
 * content description), and a touch target of at least 48 × 48 dp. Google's Accessibility Test
 * Framework finds nothing under Robolectric, so the app checks these two rules itself.
 */
fun SemanticsNodeInteractionsProvider.assertTappablesAreAccessible() {
    val problems = onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { node -> node.problem() }
    if (problems.isNotEmpty()) throw AssertionError("Accessibility problems:\n" + problems.joinToString("\n"))
}

private fun SemanticsNode.problem(): String? {
    val config = config
    val name = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        ?: config.getOrNull(SemanticsProperties.EditableText)?.text
    val label = config.getOrNull(SemanticsActions.OnClick)?.label
    // Scrolled out of view: checked on the screens where it shows.
    if (boundsInRoot.isEmpty) return null
    val density = Density(layoutInfo.density.density)
    val minPx = with(density) { MinTouchTarget.toPx() }
    // Touch bounds, as screen readers and taps see them: Compose extends a small element's touch
    // area towards 48 dp where neighbours leave room. They are clipped at the screen edge, so the
    // element's own full size counts too.
    val touch = touchBoundsInRoot
    val width = maxOf(touch.width, size.width.toFloat())
    val height = maxOf(touch.height, size.height.toFloat())
    return when {
        name.isNullOrBlank() && label.isNullOrBlank() -> "No name for a tappable element at $boundsInRoot"
        width < minPx - 0.5f || height < minPx - 0.5f ->
            "Touch target of “$name” is ${with(density) { width.toDp() }} × ${with(density) { height.toDp() }}, below $MinTouchTarget"
        else -> null
    }
}
