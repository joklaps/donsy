// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.energy

import be.kodeworks.donsy.domain.model.EnergyCheckIn
import be.kodeworks.donsy.domain.model.EnergyLevel
import java.time.Duration
import java.time.Instant

/** Energy changes during the day, so a check-in only counts for a while (docs/spec.md, "Energy check-in"). */
object EnergyCheckIns {

    val VALID_FOR: Duration = Duration.ofHours(4)

    /** Check-ins older than this are deleted on app start; only the latest one is ever used. */
    val RETENTION: Duration = Duration.ofDays(30)

    /** The energy to use for suggestions, or null when there is no recent check-in. */
    fun current(latest: EnergyCheckIn?, now: Instant): EnergyLevel? {
        if (latest == null) return null
        // A check-in "from the future" (the clock was moved back) still counts until it expires.
        val age = Duration.between(latest.checkedInAt, now)
        return if (age < VALID_FOR) latest.level else null
    }
}
