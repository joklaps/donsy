// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import be.kodeworks.donsy.data.db.entity.BrainDumpItemEntity
import be.kodeworks.donsy.data.db.entity.FocusSessionEntity
import be.kodeworks.donsy.data.db.entity.SubtaskEntity
import be.kodeworks.donsy.data.db.entity.TaskEntity

/** Whole-table reads and writes for export and restore. Call within one transaction. */
@Dao
interface BackupDao {

    @Query("SELECT * FROM tasks ORDER BY id")
    suspend fun tasks(): List<TaskEntity>

    @Query("SELECT * FROM subtasks ORDER BY id")
    suspend fun subtasks(): List<SubtaskEntity>

    @Query("SELECT * FROM brain_dump_items ORDER BY id")
    suspend fun brainDumpItems(): List<BrainDumpItemEntity>

    /** A running or paused session belongs to this device's timer and alarm, so it isn't exported. */
    @Query("SELECT * FROM focus_sessions WHERE status NOT IN ('RUNNING', 'PAUSED') ORDER BY id")
    suspend fun finishedFocusSessions(): List<FocusSessionEntity>

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE status IN ('RUNNING', 'PAUSED')")
    suspend fun activeFocusSessionCount(): Int

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteFocusSessions()

    @Query("DELETE FROM brain_dump_items")
    suspend fun deleteBrainDumpItems()

    @Query("DELETE FROM subtasks")
    suspend fun deleteSubtasks()

    @Query("DELETE FROM tasks")
    suspend fun deleteTasks()

    @Query("DELETE FROM energy_check_ins")
    suspend fun deleteEnergyCheckIns()

    @Insert
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Insert
    suspend fun insertBrainDumpItems(items: List<BrainDumpItemEntity>)

    @Insert
    suspend fun insertFocusSessions(sessions: List<FocusSessionEntity>)
}
