// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithSubtasks(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "id", entityColumn = "task_id")
    val subtasks: List<SubtaskEntity>,
)
