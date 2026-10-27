// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.model

import java.time.Instant

/** A soft-deleted task, as listed on the Recently deleted screen until it is purged. */
data class DeletedTask(
    val id: Long,
    val title: String,
    val isRoutine: Boolean,
    val deletedAt: Instant,
)

/** A soft-deleted BrainDump item, as listed on the Recently deleted screen until it is purged. */
data class DeletedBrainDumpItem(
    val id: Long,
    val text: String,
    val deletedAt: Instant,
)
