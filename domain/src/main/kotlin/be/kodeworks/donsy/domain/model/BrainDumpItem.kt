// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.model

import java.time.Instant

/** A captured thought. Not a commitment: it never becomes overdue and never triggers reminders. */
data class BrainDumpItem(
    val id: Long,
    val text: String,
    val status: BrainDumpStatus,
    val convertedTaskId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
