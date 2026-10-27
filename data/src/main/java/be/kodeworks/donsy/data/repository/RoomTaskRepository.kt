// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.repository

import androidx.room.withTransaction
import be.kodeworks.donsy.data.db.DonsyDatabase
import be.kodeworks.donsy.data.db.entity.SubtaskEntity
import be.kodeworks.donsy.data.db.entity.TaskEntity
import be.kodeworks.donsy.data.db.entity.TaskWithSubtasks
import be.kodeworks.donsy.domain.model.DeletedTask
import be.kodeworks.donsy.domain.model.RepeatDays
import be.kodeworks.donsy.domain.model.Subtask
import be.kodeworks.donsy.domain.model.Task
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.domain.repository.TaskRepository
import be.kodeworks.donsy.domain.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

class RoomTaskRepository @Inject constructor(
    private val database: DonsyDatabase,
    private val timeSource: TimeSource,
) : TaskRepository {

    private val taskDao = database.taskDao()
    private val brainDumpDao = database.brainDumpDao()

    override fun observeTasks(): Flow<List<Task>> =
        taskDao.observeTasks().map { tasks -> tasks.map { it.toDomain(today(), timeSource.zone()) } }

    override fun observeArchived(): Flow<List<Task>> =
        taskDao.observeArchived().map { tasks -> tasks.map { it.toDomain(today(), timeSource.zone()) } }

    override fun observeDeleted(): Flow<List<DeletedTask>> =
        taskDao.observeDeleted().map { tasks ->
            tasks.mapNotNull { task -> task.deletedAt?.let { DeletedTask(task.id, task.title, task.repeatDays != null, it) } }
        }

    override fun observeTask(id: Long): Flow<Task?> =
        taskDao.observeTask(id).map { it?.toDomain(today(), timeSource.zone()) }

    private fun today(): LocalDate = timeSource.now().atZone(timeSource.zone()).toLocalDate()

    override suspend fun create(draft: TaskDraft): Long? = insert(draft, sourceBrainDumpId = null)

    override suspend fun createFromBrainDump(brainDumpItemId: Long, draft: TaskDraft): Long? =
        database.withTransaction {
            insert(draft, sourceBrainDumpId = brainDumpItemId)?.also { taskId ->
                brainDumpDao.markConverted(brainDumpItemId, taskId, timeSource.now())
            }
        }

    private suspend fun insert(draft: TaskDraft, sourceBrainDumpId: Long?): Long? {
        val title = draft.title.trim()
        if (title.isEmpty()) return null
        val now = timeSource.now()
        return taskDao.insertTask(
            TaskEntity(
                title = title,
                notes = draft.notes.trim(),
                importance = draft.importance,
                dueDate = draft.dueDate(),
                dueTime = draft.dueTime(),
                repeatDays = draft.routineDays()?.bits,
                estimateMinutes = draft.estimateMinutes,
                effort = draft.effort,
                sourceBrainDumpId = sourceBrainDumpId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun update(id: Long, draft: TaskDraft) {
        val title = draft.title.trim()
        if (title.isEmpty()) return
        database.withTransaction {
            val task = taskDao.getTask(id) ?: return@withTransaction
            taskDao.updateTask(
                task.copy(
                    title = title,
                    notes = draft.notes.trim(),
                    importance = draft.importance,
                    dueDate = draft.dueDate(),
                    dueTime = draft.dueTime(),
                    repeatDays = draft.routineDays()?.bits,
                    // Turning a routine into an ordinary task (or back) starts it fresh.
                    routineDoneOn = task.routineDoneOn.takeIf { draft.routineDays()?.bits == task.repeatDays },
                    estimateMinutes = draft.estimateMinutes,
                    effort = draft.effort,
                    updatedAt = timeSource.now(),
                ),
            )
        }
    }

    override suspend fun complete(id: Long, alsoCompleteOpenSteps: Boolean) {
        val now = timeSource.now()
        database.withTransaction {
            if (alsoCompleteOpenSteps) taskDao.completeOpenSubtasks(id, now)
            taskDao.setStatus(id, TaskStatus.DONE, completedAt = now, updatedAt = now)
        }
    }

    override suspend fun setRoutineDone(id: Long, done: Boolean) {
        database.withTransaction {
            val task = taskDao.getTask(id) ?: return@withTransaction
            if (task.repeatDays == null) return@withTransaction
            taskDao.updateTask(task.copy(routineDoneOn = if (done) today() else null))
        }
    }

    override suspend fun reopen(id: Long) = setStatus(id, TaskStatus.ACTIVE)

    override suspend fun pause(id: Long) = setStatus(id, TaskStatus.PAUSED)

    override suspend fun resume(id: Long) = setStatus(id, TaskStatus.ACTIVE)

    private suspend fun setStatus(id: Long, status: TaskStatus) {
        taskDao.setStatus(id, status, completedAt = null, updatedAt = timeSource.now())
    }

    override suspend fun recordWork(id: Long) {
        taskDao.setLastWorkedAt(id, timeSource.now())
    }

    override suspend fun setNotNowUntil(id: Long, until: Instant?) {
        taskDao.setNotNowUntil(id, until)
    }

    override suspend fun delete(id: Long) {
        taskDao.setDeletedAt(id, timeSource.now())
    }

    override suspend fun restore(id: Long) {
        taskDao.setDeletedAt(id, null)
    }

    override suspend fun deleteForever(id: Long) {
        taskDao.deleteForever(id)
    }

    override suspend fun archive(id: Long) {
        taskDao.changeStatus(id, from = TaskStatus.DONE, to = TaskStatus.ARCHIVED, updatedAt = timeSource.now())
    }

    override suspend fun unarchive(id: Long) {
        taskDao.changeStatus(id, from = TaskStatus.ARCHIVED, to = TaskStatus.DONE, updatedAt = timeSource.now())
    }

    override suspend fun archiveDoneBefore(cutoff: Instant): Int = taskDao.archiveDoneBefore(cutoff, timeSource.now())

    override suspend fun addStep(taskId: Long, title: String): Long? {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return null
        val now = timeSource.now()
        return database.withTransaction {
            taskDao.insertSubtask(
                SubtaskEntity(
                    taskId = taskId,
                    title = trimmed,
                    position = taskDao.nextSubtaskPosition(taskId),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    override suspend fun addSteps(taskId: Long, titles: List<String>): List<Long> {
        val trimmed = titles.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmed.isEmpty()) return emptyList()
        val now = timeSource.now()
        return database.withTransaction {
            val first = taskDao.nextSubtaskPosition(taskId)
            trimmed.mapIndexed { index, title ->
                taskDao.insertSubtask(
                    SubtaskEntity(taskId = taskId, title = title, position = first + index, createdAt = now, updatedAt = now),
                )
            }
        }
    }

    override suspend fun renameStep(stepId: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        taskDao.renameSubtask(stepId, trimmed, timeSource.now())
    }

    override suspend fun setStepDone(stepId: Long, done: Boolean) {
        val now = timeSource.now()
        database.withTransaction {
            val step = taskDao.getSubtask(stepId) ?: return@withTransaction
            taskDao.setSubtaskDone(stepId, done, completedAt = if (done) now else null, updatedAt = now)
            if (done) taskDao.setLastWorkedAt(step.taskId, now)
        }
    }

    override suspend fun deleteStep(stepId: Long) {
        taskDao.deleteSubtask(stepId)
    }

    override suspend fun moveStep(taskId: Long, fromIndex: Int, toIndex: Int) {
        database.withTransaction {
            val steps = taskDao.getSubtasks(taskId).toMutableList()
            if (fromIndex !in steps.indices || toIndex !in steps.indices || fromIndex == toIndex) {
                return@withTransaction
            }
            steps.add(toIndex, steps.removeAt(fromIndex))
            val now = timeSource.now()
            taskDao.updateSubtasks(
                steps.mapIndexedNotNull { index, step ->
                    if (step.position == index) null else step.copy(position = index, updatedAt = now)
                },
            )
        }
    }
}

/** A routine needs a time; without one the draft is an ordinary task. */
private fun TaskDraft.routineDays(): RepeatDays? = repeatDays?.takeIf { dueTime != null }

private fun TaskDraft.dueDate(): LocalDate? = if (routineDays() != null) null else dueDate

/** Kept for a dated task or a routine; dropped otherwise. */
private fun TaskDraft.dueTime(): LocalTime? = dueTime?.takeIf { routineDays() != null || dueDate != null }

/**
 * A routine's steps are a checklist for each day: a step checked off on an earlier day reads as
 * open again today, without rewriting it.
 */
private fun TaskWithSubtasks.toDomain(today: LocalDate, zone: ZoneId) = Task(
    id = task.id,
    title = task.title,
    notes = task.notes,
    status = task.status,
    importance = task.importance,
    dueDate = task.dueDate,
    dueTime = task.dueTime,
    repeatDays = RepeatDays.fromBits(task.repeatDays),
    routineDoneOn = task.routineDoneOn,
    estimateMinutes = task.estimateMinutes,
    effort = task.effort,
    notNowUntil = task.notNowUntil,
    lastWorkedAt = task.lastWorkedAt,
    sourceBrainDumpId = task.sourceBrainDumpId,
    createdAt = task.createdAt,
    updatedAt = task.updatedAt,
    completedAt = task.completedAt,
    subtasks = subtasks
        .sortedWith(compareBy({ it.position }, { it.id }))
        .map { step ->
            val isDone = step.isDone && (task.repeatDays == null || step.completedAt?.isOn(today, zone) == true)
            Subtask(step.id, step.taskId, step.title, step.position, isDone, step.completedAt.takeIf { isDone })
        },
)

private fun Instant.isOn(date: LocalDate, zone: ZoneId): Boolean = atZone(zone).toLocalDate() == date
