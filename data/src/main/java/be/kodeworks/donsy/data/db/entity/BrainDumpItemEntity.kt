// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import be.kodeworks.donsy.domain.model.BrainDumpStatus
import java.time.Instant

@Entity(
    tableName = "brain_dump_items",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["converted_task_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("status"), Index("deleted_at"), Index("converted_task_id")],
)
data class BrainDumpItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val status: BrainDumpStatus = BrainDumpStatus.INBOX,
    @ColumnInfo(name = "converted_task_id") val convertedTaskId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)
