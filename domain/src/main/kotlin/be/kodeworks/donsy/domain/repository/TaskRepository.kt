// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.domain.repository

import be.kodeworks.donsy.domain.model.DeletedTask
import be.kodeworks.donsy.domain.model.Task
import be.kodeworks.donsy.domain.model.TaskDraft
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TaskRepository {
    /** All non-deleted tasks with their steps, oldest first. */
    fun observeTasks(): Flow<List<Task>>

    /** Archived tasks with their steps, most recently done first. */
    fun observeArchived(): Flow<List<Task>>

    /** Soft-deleted tasks that are not purged yet, most recently deleted first. */
    fun observeDeleted(): Flow<List<DeletedTask>>

    /** The task, or null when it doesn't exist or was deleted. */
    fun observeTask(id: Long): Flow<Task?>

    /** Creates an active task; blank titles are rejected with null. */
    suspend fun create(draft: TaskDraft): Long?

    /**
     * Creates a task from a BrainDump item and marks the item converted, in one transaction.
     * Returns null when the title is blank.
     */
    suspend fun createFromBrainDump(brainDumpItemId: Long, draft: TaskDraft): Long?

    /** Replaces the editable fields; blank titles are ignored. */
    suspend fun update(id: Long, draft: TaskDraft)

    /** Marks the task done; with [alsoCompleteOpenSteps] its open steps are checked off too. */
    suspend fun complete(id: Long, alsoCompleteOpenSteps: Boolean)

    /** Marks a routine done for today, or (with false) not done after all. Ignored for other tasks. */
    suspend fun setRoutineDone(id: Long, done: Boolean)

    /** Back to active. Step states are kept. */
    suspend fun reopen(id: Long)

    /** "Not for now": excluded from suggestions until resumed. */
    suspend fun pause(id: Long)

    suspend fun resume(id: Long)

    /** Records that the user just worked on the task (e.g. a focus session); feeds "continuity". */
    suspend fun recordWork(id: Long)

    /** Hides the task from suggestions until [until]; null shows it again. */
    suspend fun setNotNowUntil(id: Long, until: Instant?)

    /** Soft delete: restorable until purged. */
    suspend fun delete(id: Long)

    suspend fun restore(id: Long)

    /** Removes a soft-deleted task and its steps for good. Ignored for a task that isn't deleted. */
    suspend fun deleteForever(id: Long)

    /** Moves a done task out of the Tasks list into the archive. Ignored for other tasks. */
    suspend fun archive(id: Long)

    /** Brings an archived task back as done. Counts as a change, so it isn't archived again right away. */
    suspend fun unarchive(id: Long)

    /** Archives done tasks (not routines) last changed before [cutoff]; returns how many. */
    suspend fun archiveDoneBefore(cutoff: Instant): Int

    /** Appends a step at the end; blank titles are rejected with null. */
    suspend fun addStep(taskId: Long, title: String): Long?

    /** Appends several steps at the end, in order, in one transaction; blank titles are skipped. */
    suspend fun addSteps(taskId: Long, titles: List<String>): List<Long>

    suspend fun renameStep(stepId: Long, title: String)

    /** Checking a step off counts as working on the task. */
    suspend fun setStepDone(stepId: Long, done: Boolean)

    suspend fun deleteStep(stepId: Long)

    /** Moves the step at [fromIndex] to [toIndex] within the task's ordered steps. */
    suspend fun moveStep(taskId: Long, fromIndex: Int, toIndex: Int)
}
