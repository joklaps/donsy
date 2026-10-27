// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import be.kodeworks.donsy.data.db.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Query("SELECT * FROM focus_sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY started_at DESC LIMIT 1")
    fun observeActive(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY started_at DESC LIMIT 1")
    suspend fun getActive(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun get(id: Long): FocusSessionEntity?

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)
}
