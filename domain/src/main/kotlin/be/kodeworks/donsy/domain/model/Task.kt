// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class Task(
    val id: Long,
    val title: String,
    val notes: String,
    val status: TaskStatus,
    val importance: Importance,
    val dueDate: LocalDate?,
    /**
     * Optional time on [dueDate]; null means the end of that day (before midnight). For a routine
     * it is the reminder time and always set.
     */
    val dueTime: LocalTime?,
    /** Set for a routine: the weekdays it repeats on. A routine has no [dueDate]. */
    val repeatDays: RepeatDays?,
    /** The day a routine was last marked done; it counts as done only on that day. */
    val routineDoneOn: LocalDate?,
    val estimateMinutes: Int?,
    val effort: Effort?,
    val notNowUntil: Instant?,
    val lastWorkedAt: Instant?,
    val sourceBrainDumpId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    /** Ordered by [Subtask.position]. */
    val subtasks: List<Subtask>,
) {
    val isRoutine: Boolean get() = repeatDays != null

    val openStepCount: Int get() = subtasks.count { !it.isDone }
    val doneStepCount: Int get() = subtasks.count { it.isDone }
}

data class Subtask(
    val id: Long,
    val taskId: Long,
    val title: String,
    val position: Int,
    val isDone: Boolean,
    val completedAt: Instant?,
)

/** The user-editable fields of a task. */
data class TaskDraft(
    val title: String,
    val notes: String = "",
    val importance: Importance = Importance.NORMAL,
    val dueDate: LocalDate? = null,
    /** Ignored without a [dueDate], except for a routine, which needs it. */
    val dueTime: LocalTime? = null,
    /** Makes the task a routine; its [dueDate] is then ignored. */
    val repeatDays: RepeatDays? = null,
    val estimateMinutes: Int? = null,
    val effort: Effort? = null,
)
