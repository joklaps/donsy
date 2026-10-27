// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.model

import java.time.Instant

/** "How's your energy?" answered on Home. Optional, and only the latest recent one counts. */
data class EnergyCheckIn(
    val id: Long,
    val level: EnergyLevel,
    val checkedInAt: Instant,
)
