// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * Shows that a screen scrolls: a thin scrollbar at the end edge, visible whenever the content is
 * longer than the screen (not only while scrolling), and a soft fade at the bottom while there is
 * more below. Purely visual; screen readers already get scroll actions.
 *
 * Put the modifier on the scrolling container itself, or before verticalScroll() in a chain, so it
 * draws over the visible area rather than the whole content.
 */

@Immutable
data class ScrollIndicatorColors(val thumb: Color, val fade: Color)

object ScrollIndicatorDefaults {
    /** A neutral thumb (calm-UI rule: no colour accents), fading into the screen's background. */
    @Composable
    fun colors() = ScrollIndicatorColors(
        thumb = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        fade = MaterialTheme.colorScheme.background,
    )
}

fun Modifier.scrollIndicators(state: ScrollState, colors: ScrollIndicatorColors): Modifier =
    drawIndicators(colors) { viewport ->
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) {
            null
        } else {
            Metrics(
                visibleFraction = viewport / (viewport + max),
                scrolledFraction = state.value.toFloat() / max,
                moreBelow = state.canScrollForward,
            )
        }
    }

/** Lazy lists only know the items on screen, so the thumb's size and place are estimates. */
fun Modifier.scrollIndicators(state: LazyListState, colors: ScrollIndicatorColors): Modifier =
    drawIndicators(colors) { viewport ->
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty() || !(state.canScrollForward || state.canScrollBackward)) {
            null
        } else {
            val averageItem = items.sumOf { it.size }.toFloat() / items.size
            val content = averageItem * info.totalItemsCount + info.beforeContentPadding + info.afterContentPadding
            val scrolled = state.firstVisibleItemIndex * averageItem + state.firstVisibleItemScrollOffset
            Metrics(
                visibleFraction = viewport / content,
                // Pinned at the ends, where the estimate could be slightly off.
                scrolledFraction = when {
                    !state.canScrollBackward -> 0f
                    !state.canScrollForward -> 1f
                    else -> scrolled / (content - viewport)
                },
                moreBelow = state.canScrollForward,
            )
        }
    }

private class Metrics(visibleFraction: Float, scrolledFraction: Float, val moreBelow: Boolean) {
    val visibleFraction = visibleFraction.coerceIn(0f, 1f)
    val scrolledFraction = scrolledFraction.coerceIn(0f, 1f)
}

// Fits in the 4 dp gutter beside the heading bands and cards (ListSectionHeaderPadding).
private val ThumbWidth = 3.dp
private val ThumbMinLength = 32.dp
private val ThumbInset = 0.5.dp
private val FadeHeight = 32.dp

/** [metrics] gets the visible height in pixels and returns null when nothing scrolls. */
private fun Modifier.drawIndicators(colors: ScrollIndicatorColors, metrics: (viewport: Float) -> Metrics?): Modifier =
    drawWithContent {
        drawContent()
        val m = metrics(size.height) ?: return@drawWithContent
        if (m.moreBelow) {
            val fade = FadeHeight.toPx().coerceAtMost(size.height / 3)
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, colors.fade), startY = size.height - fade, endY = size.height),
                topLeft = Offset(0f, size.height - fade),
                size = Size(size.width, fade),
            )
        }
        val width = ThumbWidth.toPx()
        val length = maxOf(ThumbMinLength.toPx(), size.height * m.visibleFraction).coerceAtMost(size.height)
        val top = (size.height - length) * m.scrolledFraction
        val inset = ThumbInset.toPx()
        val left = if (layoutDirection == LayoutDirection.Ltr) size.width - inset - width else inset
        drawRoundRect(
            color = colors.thumb,
            topLeft = Offset(left, top),
            size = Size(width, length),
            cornerRadius = CornerRadius(width / 2),
        )
    }
