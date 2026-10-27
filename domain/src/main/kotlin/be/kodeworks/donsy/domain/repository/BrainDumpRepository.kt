// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.repository

import be.kodeworks.donsy.domain.model.BrainDumpItem
import be.kodeworks.donsy.domain.model.DeletedBrainDumpItem
import kotlinx.coroutines.flow.Flow

interface BrainDumpRepository {
    /** Unprocessed, non-deleted items, newest first. */
    fun observeInbox(): Flow<List<BrainDumpItem>>

    /** Soft-deleted items that are not purged yet, most recently deleted first. */
    fun observeDeleted(): Flow<List<DeletedBrainDumpItem>>

    /** The item, or null when it doesn't exist or was deleted. */
    suspend fun get(id: Long): BrainDumpItem?

    /** Saves trimmed [text] and returns the new id, or null when the text is blank. */
    suspend fun capture(text: String): Long?

    /** Replaces the text; blank text is ignored so an item never ends up empty. */
    suspend fun updateText(id: Long, text: String)

    /** Soft delete: the item disappears but can be restored until it is purged. */
    suspend fun delete(id: Long)

    suspend fun restore(id: Long)

    /** Removes a soft-deleted item for good. Ignored for an item that isn't deleted. */
    suspend fun deleteForever(id: Long)
}
