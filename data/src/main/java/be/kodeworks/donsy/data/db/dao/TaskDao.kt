// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import be.kodeworks.donsy.data.db.entity.SubtaskEntity
import be.kodeworks.donsy.data.db.entity.TaskEntity
import be.kodeworks.donsy.data.db.entity.TaskWithSubtasks
import be.kodeworks.donsy.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface TaskDao {

    @Transaction
    @Query("SELECT * FROM tasks WHERE deleted_at IS NULL ORDER BY created_at, id")
    fun observeTasks(): Flow<List<TaskWithSubtasks>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id AND deleted_at IS NULL")
    fun observeTask(id: Long): Flow<TaskWithSubtasks?>

    @Transaction
    @Query("SELECT * FROM tasks WHERE status = 'ARCHIVED' AND deleted_at IS NULL ORDER BY completed_at DESC, id DESC")
    fun observeArchived(): Flow<List<TaskWithSubtasks>>

    @Query("SELECT * FROM tasks WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC, id DESC")
    fun observeDeleted(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskEntity?

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query(
        """
        UPDATE tasks SET status = :status, completed_at = :completedAt, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun setStatus(id: Long, status: TaskStatus, completedAt: Instant?, updatedAt: Instant)

    /** Keeps completed_at: archiving and bringing back don't change when a task was done. */
    @Query("UPDATE tasks SET status = :to, updated_at = :updatedAt WHERE id = :id AND status = :from AND deleted_at IS NULL")
    suspend fun changeStatus(id: Long, from: TaskStatus, to: TaskStatus, updatedAt: Instant)

    @Query(
        """
        UPDATE tasks SET status = 'ARCHIVED', updated_at = :now
        WHERE status = 'DONE' AND repeat_days IS NULL AND deleted_at IS NULL AND updated_at < :cutoff
        """,
    )
    suspend fun archiveDoneBefore(cutoff: Instant, now: Instant): Int

    @Query("UPDATE tasks SET not_now_until = :until WHERE id = :id")
    suspend fun setNotNowUntil(id: Long, until: Instant?)

    @Query("UPDATE tasks SET last_worked_at = :at WHERE id = :id")
    suspend fun setLastWorkedAt(id: Long, at: Instant)

    /** Passing null restores a soft-deleted task. */
    @Query("UPDATE tasks SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun setDeletedAt(id: Long, deletedAt: Instant?)

    /** Steps are removed by the foreign key's cascade. */
    @Query("DELETE FROM tasks WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Instant): Int

    /** Only a soft-deleted task; steps go with it through the cascade. */
    @Query("DELETE FROM tasks WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun deleteForever(id: Long)

    @Query("SELECT * FROM subtasks WHERE task_id = :taskId ORDER BY position, id")
    suspend fun getSubtasks(taskId: Long): List<SubtaskEntity>

    @Query("SELECT * FROM subtasks WHERE id = :id")
    suspend fun getSubtask(id: Long): SubtaskEntity?

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM subtasks WHERE task_id = :taskId")
    suspend fun nextSubtaskPosition(taskId: Long): Int

    @Insert
    suspend fun insertSubtask(subtask: SubtaskEntity): Long

    @Update
    suspend fun updateSubtasks(subtasks: List<SubtaskEntity>)

    @Query("UPDATE subtasks SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun renameSubtask(id: Long, title: String, updatedAt: Instant)

    @Query(
        """
        UPDATE subtasks SET is_done = :done, completed_at = :completedAt, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun setSubtaskDone(id: Long, done: Boolean, completedAt: Instant?, updatedAt: Instant)

    @Query(
        """
        UPDATE subtasks SET is_done = 1, completed_at = :at, updated_at = :at
        WHERE task_id = :taskId AND is_done = 0
        """,
    )
    suspend fun completeOpenSubtasks(taskId: Long, at: Instant)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubtask(id: Long)
}
