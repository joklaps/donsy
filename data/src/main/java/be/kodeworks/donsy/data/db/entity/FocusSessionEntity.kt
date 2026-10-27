// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import be.kodeworks.donsy.domain.model.FocusSessionStatus
import java.time.Instant

/**
 * Timer state is stored as timestamps, never as a ticking counter, so a session can be
 * recovered after process death, reboot or a clock change (see docs/spec.md, "Focus sessions").
 */
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = SubtaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["subtask_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("task_id"), Index("subtask_id"), Index("status")],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long? = null,
    @ColumnInfo(name = "subtask_id") val subtaskId: Long? = null,
    @ColumnInfo(name = "planned_duration_millis") val plannedDurationMillis: Long,
    val status: FocusSessionStatus,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "ended_at") val endedAt: Instant? = null,
    /** Run time of all finished run segments; the current segment is derived from the resume timestamps. */
    @ColumnInfo(name = "accumulated_run_millis") val accumulatedRunMillis: Long = 0,
    @ColumnInfo(name = "last_resumed_at_wall") val lastResumedAtWall: Instant? = null,
    @ColumnInfo(name = "last_resumed_at_elapsed") val lastResumedAtElapsedMillis: Long? = null,
    /** Device boot count when the session last resumed; a change means elapsed time was reset. */
    @ColumnInfo(name = "boot_count") val bootCount: Int,
    /** When the session was paused; used to wrap up long pauses. Added in schema version 2. */
    @ColumnInfo(name = "paused_at") val pausedAt: Instant? = null,
)
