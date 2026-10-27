// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.archive

import java.time.Duration
import java.time.Instant

/**
 * Done tasks move to the archive by themselves once they have been left alone for a while
 * (docs/spec.md, "Archive"). The user picks how long, or turns it off.
 */
object AutoArchive {

    /** Turns automatic archiving off. */
    const val NEVER = 0

    const val DEFAULT_DAYS = 30

    /** The choices offered in Settings, in days; [NEVER] means off. */
    val CHOICES: List<Int> = listOf(NEVER, 7, 30, 90)

    /** Done tasks last changed before this moment get archived, or null when archiving is off. */
    fun cutoff(days: Int, now: Instant): Instant? =
        if (days <= NEVER) null else now.minus(Duration.ofDays(days.toLong()))
}
