// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import be.kodeworks.donsy.data.db.entity.EnergyCheckInEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface EnergyCheckInDao {

    @Query("SELECT * FROM energy_check_ins ORDER BY checked_in_at DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<EnergyCheckInEntity?>

    @Insert
    suspend fun insert(checkIn: EnergyCheckInEntity): Long

    @Query("DELETE FROM energy_check_ins WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM energy_check_ins WHERE checked_in_at < :cutoff")
    suspend fun deleteBefore(cutoff: Instant)
}
