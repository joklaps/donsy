// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import be.kodeworks.donsy.data.db.entity.BrainDumpItemEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface BrainDumpDao {

    @Query(
        """
        SELECT * FROM brain_dump_items
        WHERE status = 'INBOX' AND deleted_at IS NULL
        ORDER BY created_at DESC, id DESC
        """,
    )
    fun observeInbox(): Flow<List<BrainDumpItemEntity>>

    @Query("SELECT * FROM brain_dump_items WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC, id DESC")
    fun observeDeleted(): Flow<List<BrainDumpItemEntity>>

    @Query("SELECT * FROM brain_dump_items WHERE id = :id")
    suspend fun getById(id: Long): BrainDumpItemEntity?

    @Insert
    suspend fun insert(item: BrainDumpItemEntity): Long

    @Query("UPDATE brain_dump_items SET text = :text, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateText(id: Long, text: String, updatedAt: Instant)

    @Query(
        """
        UPDATE brain_dump_items SET status = 'CONVERTED', converted_task_id = :taskId, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markConverted(id: Long, taskId: Long, updatedAt: Instant)

    /** Passing null restores a soft-deleted item. */
    @Query("UPDATE brain_dump_items SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: Instant?)

    @Query("DELETE FROM brain_dump_items WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Instant): Int

    @Query("DELETE FROM brain_dump_items WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun deleteForever(id: Long)
}
