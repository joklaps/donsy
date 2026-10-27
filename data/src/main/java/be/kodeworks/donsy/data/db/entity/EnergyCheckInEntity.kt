// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import be.kodeworks.donsy.domain.model.EnergyLevel
import java.time.Instant

/** Added in schema version 3. Old check-ins are purged on app start. */
@Entity(tableName = "energy_check_ins", indices = [Index("checked_in_at")])
data class EnergyCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: EnergyLevel,
    @ColumnInfo(name = "checked_in_at") val checkedInAt: Instant,
)
