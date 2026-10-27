// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.focus

/** Which session lengths the Focus screen offers, and which one is preselected (docs/spec.md, "Focus sessions"). */
object FocusDurations {

    /** Lengths the user can choose to offer, in minutes. */
    val PRESETS = listOf(5, 10, 15, 20, 25, 30, 45, 60)

    val DEFAULT_CHOICES = listOf(5, 10, 15, 25)

    const val DEFAULT_MINUTES = 15

    /** The offered lengths: stored values that are still presets, sorted, or the defaults when none are left. */
    fun choices(stored: Set<Int>?): List<Int> =
        stored.orEmpty().filter { it in PRESETS }.sorted().ifEmpty { DEFAULT_CHOICES }

    /**
     * The length that fits a task's estimate: the shortest offered length that covers it, or the
     * longest one when the estimate is longer than all of them.
     */
    fun forEstimate(estimateMinutes: Int, choices: List<Int>): Int =
        choices.sorted().firstOrNull { it >= estimateMinutes } ?: choices.max()

    /** The preselected length: [stored] when it is still offered, otherwise the offered length closest to 15 minutes. */
    fun defaultMinutes(stored: Int?, choices: List<Int>): Int =
        stored?.takeIf { it in choices }
            ?: choices.minWith(compareBy<Int> { kotlin.math.abs(it - DEFAULT_MINUTES) }.thenBy { it })
}
