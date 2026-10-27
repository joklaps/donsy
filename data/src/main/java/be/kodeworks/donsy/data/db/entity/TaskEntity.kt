// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "tasks",
    indices = [Index("status"), Index("deleted_at")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val status: TaskStatus = TaskStatus.ACTIVE,
    val importance: Importance = Importance.NORMAL,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate? = null,
    /** Null means the end of [dueDate]. Null without a due date, except for a routine: its reminder time. */
    @ColumnInfo(name = "due_time") val dueTime: LocalTime? = null,
    /** Routines only: weekday bit set (see RepeatDays). Null for an ordinary task. */
    @ColumnInfo(name = "repeat_days") val repeatDays: Int? = null,
    /** Routines only: the day it was last marked done. */
    @ColumnInfo(name = "routine_done_on") val routineDoneOn: LocalDate? = null,
    @ColumnInfo(name = "estimate_minutes") val estimateMinutes: Int? = null,
    val effort: Effort? = null,
    /** "Not now" hides the task from suggestions until this moment. */
    @ColumnInfo(name = "not_now_until") val notNowUntil: Instant? = null,
    @ColumnInfo(name = "last_worked_at") val lastWorkedAt: Instant? = null,
    @ColumnInfo(name = "source_brain_dump_id") val sourceBrainDumpId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "completed_at") val completedAt: Instant? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)
