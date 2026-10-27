// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.repository

import be.kodeworks.donsy.data.db.dao.BrainDumpDao
import be.kodeworks.donsy.data.db.entity.BrainDumpItemEntity
import be.kodeworks.donsy.domain.model.BrainDumpItem
import be.kodeworks.donsy.domain.model.DeletedBrainDumpItem
import be.kodeworks.donsy.domain.repository.BrainDumpRepository
import be.kodeworks.donsy.domain.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomBrainDumpRepository @Inject constructor(
    private val dao: BrainDumpDao,
    private val timeSource: TimeSource,
) : BrainDumpRepository {

    override fun observeInbox(): Flow<List<BrainDumpItem>> =
        dao.observeInbox().map { items -> items.map { it.toDomain() } }

    override fun observeDeleted(): Flow<List<DeletedBrainDumpItem>> =
        dao.observeDeleted().map { items ->
            items.mapNotNull { item -> item.deletedAt?.let { DeletedBrainDumpItem(item.id, item.text, it) } }
        }

    override suspend fun get(id: Long): BrainDumpItem? =
        dao.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()

    override suspend fun capture(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val now = timeSource.now()
        return dao.insert(BrainDumpItemEntity(text = trimmed, createdAt = now, updatedAt = now))
    }

    override suspend fun updateText(id: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        dao.updateText(id, trimmed, timeSource.now())
    }

    override suspend fun delete(id: Long) {
        dao.setDeletedAt(id, timeSource.now())
    }

    override suspend fun restore(id: Long) {
        dao.setDeletedAt(id, null)
    }

    override suspend fun deleteForever(id: Long) {
        dao.deleteForever(id)
    }
}

private fun BrainDumpItemEntity.toDomain() = BrainDumpItem(
    id = id,
    text = text,
    status = status,
    convertedTaskId = convertedTaskId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
