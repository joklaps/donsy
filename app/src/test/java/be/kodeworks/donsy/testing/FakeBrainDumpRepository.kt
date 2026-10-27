// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.testing

import be.kodeworks.donsy.domain.model.BrainDumpItem
import be.kodeworks.donsy.domain.model.BrainDumpStatus
import be.kodeworks.donsy.domain.model.DeletedBrainDumpItem
import be.kodeworks.donsy.domain.repository.BrainDumpRepository
import be.kodeworks.donsy.domain.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

class FakeBrainDumpRepository(private val time: TimeSource) : BrainDumpRepository {

    private data class Stored(val item: BrainDumpItem, val deletedAt: Instant? = null) {
        val deleted: Boolean get() = deletedAt != null
    }

    private val items = MutableStateFlow<List<Stored>>(emptyList())
    private var nextId = 1L

    override fun observeInbox(): Flow<List<BrainDumpItem>> = items.map { list ->
        list.filter { !it.deleted && it.item.status == BrainDumpStatus.INBOX }
            .map { it.item }
            .sortedByDescending { it.createdAt }
    }

    override fun observeDeleted(): Flow<List<DeletedBrainDumpItem>> = items.map { list ->
        list.mapNotNull { stored -> stored.deletedAt?.let { DeletedBrainDumpItem(stored.item.id, stored.item.text, it) } }
            .sortedByDescending { it.deletedAt }
    }

    override suspend fun get(id: Long): BrainDumpItem? =
        items.value.firstOrNull { it.item.id == id && !it.deleted }?.item

    override suspend fun capture(text: String): Long? {
        if (text.isBlank()) return null
        val id = nextId++
        val now = time.now()
        items.update { it + Stored(BrainDumpItem(id, text.trim(), BrainDumpStatus.INBOX, null, now, now)) }
        return id
    }

    override suspend fun updateText(id: Long, text: String) {
        if (text.isBlank()) return
        modify(id) { it.copy(item = it.item.copy(text = text.trim(), updatedAt = time.now())) }
    }

    override suspend fun delete(id: Long) = modify(id) { it.copy(deletedAt = time.now()) }

    override suspend fun restore(id: Long) = modify(id) { it.copy(deletedAt = null) }

    override suspend fun deleteForever(id: Long) {
        items.update { list -> list.filterNot { it.item.id == id && it.deleted } }
    }

    private fun modify(id: Long, change: (Stored) -> Stored) {
        items.update { list -> list.map { if (it.item.id == id) change(it) else it } }
    }
}
