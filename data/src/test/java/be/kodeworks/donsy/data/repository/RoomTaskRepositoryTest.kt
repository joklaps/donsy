// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.data.repository

import androidx.room.Room
import be.kodeworks.donsy.data.db.DonsyDatabase
import be.kodeworks.donsy.data.maintenance.DataPurger
import be.kodeworks.donsy.data.preferences.testPreferences
import be.kodeworks.donsy.domain.archive.AutoArchive
import be.kodeworks.donsy.domain.model.BrainDumpStatus
import be.kodeworks.donsy.domain.model.Effort
import be.kodeworks.donsy.domain.model.Importance
import be.kodeworks.donsy.domain.model.RepeatDays
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.model.TaskStatus
import be.kodeworks.donsy.domain.time.FakeTimeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class RoomTaskRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DonsyDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val time = FakeTimeSource()
    private val tasks = RoomTaskRepository(database, time)
    private val brainDump = RoomBrainDumpRepository(database.brainDumpDao(), time)

    @After
    fun tearDown() = database.close()

    private suspend fun task(id: Long) = tasks.observeTask(id).first()!!

    private suspend fun stepTitles(id: Long) = task(id).subtasks.map { it.title }

    @Test
    fun createStoresTrimmedFieldsAsActiveTask() = runTest {
        val draft = TaskDraft(
            title = "  Biology test ",
            notes = " chapters 3-5 ",
            importance = Importance.HIGH,
            dueDate = LocalDate.of(2026, 10, 24),
            dueTime = LocalTime.of(8, 30),
            estimateMinutes = 30,
            effort = Effort.MEDIUM,
        )
        val id = tasks.create(draft)!!

        with(task(id)) {
            assertEquals("Biology test", title)
            assertEquals("chapters 3-5", notes)
            assertEquals(TaskStatus.ACTIVE, status)
            assertEquals(Importance.HIGH, importance)
            assertEquals(LocalDate.of(2026, 10, 24), dueDate)
            assertEquals(LocalTime.of(8, 30), dueTime)
            assertEquals(30, estimateMinutes)
            assertEquals(Effort.MEDIUM, effort)
            assertNull(sourceBrainDumpId)
        }
    }

    @Test
    fun aDueTimeWithoutADateIsDropped() = runTest {
        val id = tasks.create(TaskDraft("No date", dueTime = LocalTime.of(9, 0)))!!
        assertNull(task(id).dueTime)
        tasks.update(id, TaskDraft("Dated", dueDate = LocalDate.of(2026, 10, 24), dueTime = LocalTime.of(23, 15)))
        assertEquals(LocalTime.of(23, 15), task(id).dueTime)
        tasks.update(id, TaskDraft("Undated again", dueTime = LocalTime.of(23, 15)))
        assertNull(task(id).dueTime)
    }

    @Test
    fun aRoutineKeepsItsTimeButNoDate() = runTest {
        val id = tasks.create(
            TaskDraft("Take my pill", dueDate = LocalDate.of(2026, 10, 24), dueTime = LocalTime.of(8, 0), repeatDays = RepeatDays.EVERY_DAY),
        )!!
        with(task(id)) {
            assertTrue(isRoutine)
            assertNull(dueDate)
            assertEquals(LocalTime.of(8, 0), dueTime)
            assertEquals(RepeatDays.EVERY_DAY, repeatDays)
        }
    }

    @Test
    fun aRoutineWithoutATimeIsAnOrdinaryTask() = runTest {
        val id = tasks.create(TaskDraft("Pack my bag", repeatDays = RepeatDays.WEEKDAYS))!!
        assertFalse(task(id).isRoutine)
    }

    @Test
    fun aRoutineIsDoneForTodayOnly() = runTest {
        val id = tasks.create(TaskDraft("Take my pill", dueTime = LocalTime.of(8, 0), repeatDays = RepeatDays.EVERY_DAY))!!
        tasks.setRoutineDone(id, done = true)
        assertEquals(LocalDate.of(2026, 10, 21), task(id).routineDoneOn)
        assertEquals(TaskStatus.ACTIVE, task(id).status)

        tasks.setRoutineDone(id, done = false)
        assertNull(task(id).routineDoneOn)

        val plain = tasks.create(TaskDraft("Plain"))!!
        tasks.setRoutineDone(plain, done = true)
        assertNull(task(plain).routineDoneOn)
    }

    @Test
    fun aRoutinesStepsStartFreshTheNextDay() = runTest {
        val id = tasks.create(TaskDraft("Pack my bag", dueTime = LocalTime.of(19, 0), repeatDays = RepeatDays.EVERY_DAY))!!
        val books = tasks.addStep(id, "Books")!!
        tasks.addStep(id, "Pencil case")
        tasks.setStepDone(books, done = true)
        assertEquals(1, task(id).doneStepCount)

        time.advanceBy(Duration.ofDays(1))
        assertEquals(0, task(id).doneStepCount)

        // An ordinary task keeps its steps checked.
        val plain = tasks.create(TaskDraft("Plain"))!!
        tasks.setStepDone(tasks.addStep(plain, "Step")!!, done = true)
        time.advanceBy(Duration.ofDays(1))
        assertEquals(1, task(plain).doneStepCount)
    }

    @Test
    fun turningARoutineIntoAnOrdinaryTaskForgetsItsDoneDay() = runTest {
        val id = tasks.create(TaskDraft("Take my pill", dueTime = LocalTime.of(8, 0), repeatDays = RepeatDays.EVERY_DAY))!!
        tasks.setRoutineDone(id, done = true)
        tasks.update(id, TaskDraft("Take my pill", dueTime = LocalTime.of(8, 0), repeatDays = RepeatDays.EVERY_DAY))
        assertEquals(LocalDate.of(2026, 10, 21), task(id).routineDoneOn)

        tasks.update(id, TaskDraft("Take my pill"))
        with(task(id)) {
            assertFalse(isRoutine)
            assertNull(routineDoneOn)
            assertNull(dueTime)
        }
    }

    @Test
    fun blankTitlesAreRejected() = runTest {
        assertNull(tasks.create(TaskDraft(title = "  ")))
        val id = tasks.create(TaskDraft(title = "Keep me"))!!
        tasks.update(id, TaskDraft(title = " "))
        assertEquals("Keep me", task(id).title)
        assertNull(tasks.addStep(id, "  "))
    }

    @Test
    fun convertingABrainDumpItemCreatesTheTaskAndTakesItOutOfTheInbox() = runTest {
        val itemId = brainDump.capture("Plan the party\nInvite everyone")!!

        val taskId = tasks.createFromBrainDump(itemId, TaskDraft(title = "Plan the party", notes = "Invite everyone"))!!

        assertEquals(itemId, task(taskId).sourceBrainDumpId)
        assertTrue(brainDump.observeInbox().first().isEmpty())
        val item = database.brainDumpDao().getById(itemId)!!
        assertEquals(BrainDumpStatus.CONVERTED, item.status)
        assertEquals(taskId, item.convertedTaskId)
    }

    @Test
    fun stepsAreAppendedInOrderAndCanBeMoved() = runTest {
        val id = tasks.create(TaskDraft(title = "Test prep"))!!
        listOf("Find topics", "Review", "Flashcards", "Quiz myself").forEach { tasks.addStep(id, it) }

        tasks.moveStep(id, fromIndex = 3, toIndex = 1)
        assertEquals(listOf("Find topics", "Quiz myself", "Review", "Flashcards"), stepTitles(id))

        tasks.moveStep(id, fromIndex = 0, toIndex = 3)
        assertEquals(listOf("Quiz myself", "Review", "Flashcards", "Find topics"), stepTitles(id))

        tasks.moveStep(id, fromIndex = 0, toIndex = 9) // out of range: ignored
        assertEquals(listOf("Quiz myself", "Review", "Flashcards", "Find topics"), stepTitles(id))
    }

    @Test
    fun newStepsGoAfterExistingOnesEvenAfterDeletes() = runTest {
        val id = tasks.create(TaskDraft(title = "Room"))!!
        val first = tasks.addStep(id, "Desk")!!
        tasks.addStep(id, "Floor")
        tasks.deleteStep(first)
        tasks.addStep(id, "Bed")

        assertEquals(listOf("Floor", "Bed"), stepTitles(id))
    }

    @Test
    fun addStepsAppendsInOrderAndSkipsBlankTitles() = runTest {
        val id = tasks.create(TaskDraft(title = "Biology test"))!!
        tasks.addStep(id, "Find topics")

        val added = tasks.addSteps(id, listOf(" Review ", " ", "Flashcards"))

        assertEquals(2, added.size)
        assertEquals(listOf("Find topics", "Review", "Flashcards"), stepTitles(id))
        assertEquals(listOf(0, 1, 2), task(id).subtasks.map { it.position })
    }

    @Test
    fun checkingAStepOffRecordsWorkOnTheTask() = runTest {
        val id = tasks.create(TaskDraft(title = "Essay"))!!
        val step = tasks.addStep(id, "Outline")!!
        time.advanceBy(Duration.ofMinutes(10))

        tasks.setStepDone(step, true)

        with(task(id)) {
            assertTrue(subtasks.single().isDone)
            assertEquals(time.now, subtasks.single().completedAt)
            assertEquals(time.now, lastWorkedAt)
        }
        tasks.setStepDone(step, false)
        assertNull(task(id).subtasks.single().completedAt)
    }

    @Test
    fun completeCanLeaveOrCheckOffOpenSteps_reopenKeepsStepStates() = runTest {
        val id = tasks.create(TaskDraft(title = "Trip"))!!
        val packed = tasks.addStep(id, "Pack")!!
        tasks.addStep(id, "Tickets")
        tasks.setStepDone(packed, true)

        tasks.complete(id, alsoCompleteOpenSteps = false)
        assertEquals(TaskStatus.DONE, task(id).status)
        assertEquals(time.now, task(id).completedAt)
        assertEquals(1, task(id).openStepCount)

        tasks.reopen(id)
        assertEquals(TaskStatus.ACTIVE, task(id).status)
        assertNull(task(id).completedAt)
        assertEquals(1, task(id).openStepCount)

        tasks.complete(id, alsoCompleteOpenSteps = true)
        assertEquals(0, task(id).openStepCount)
    }

    @Test
    fun notNowIsStoredAndCanBeCleared() = runTest {
        val id = tasks.create(TaskDraft(title = "Later"))!!
        val tomorrow = time.now.plus(Duration.ofHours(14))
        tasks.setNotNowUntil(id, tomorrow)
        assertEquals(tomorrow, task(id).notNowUntil)
        tasks.setNotNowUntil(id, null)
        assertNull(task(id).notNowUntil)
    }

    @Test
    fun pauseAndResume() = runTest {
        val id = tasks.create(TaskDraft(title = "Guitar"))!!
        tasks.pause(id)
        assertEquals(TaskStatus.PAUSED, task(id).status)
        tasks.resume(id)
        assertEquals(TaskStatus.ACTIVE, task(id).status)
    }

    @Test
    fun deletedTasksDisappear_restoreBringsThemBack_purgeRemovesOldOnesWithSteps() = runTest {
        val id = tasks.create(TaskDraft(title = "Old"))!!
        tasks.addStep(id, "Step")
        tasks.delete(id)
        assertNull(tasks.observeTask(id).first())
        assertTrue(tasks.observeTasks().first().isEmpty())

        tasks.restore(id)
        assertEquals(listOf(id), tasks.observeTasks().first().map { it.id })

        tasks.delete(id)
        time.advanceBy(Duration.ofDays(8))
        DataPurger(database.brainDumpDao(), database.taskDao(), database.energyCheckInDao(), testPreferences(), time).purge()
        assertNull(database.taskDao().getTask(id))
        assertTrue(database.taskDao().getSubtasks(id).isEmpty())
    }

    @Test
    fun archiveMovesADoneTaskToTheArchive_unarchiveBringsItBackAsDone() = runTest {
        val id = tasks.create(TaskDraft(title = "Essay"))!!
        tasks.archive(id)
        assertEquals("only done tasks are archived", TaskStatus.ACTIVE, task(id).status)

        tasks.complete(id, alsoCompleteOpenSteps = false)
        val completedAt = task(id).completedAt
        time.advanceBy(Duration.ofDays(2))
        tasks.archive(id)

        assertEquals(TaskStatus.ARCHIVED, task(id).status)
        assertEquals(listOf(id), tasks.observeArchived().first().map { it.id })
        assertEquals("archiving keeps when it was done", completedAt, task(id).completedAt)

        tasks.unarchive(id)
        assertEquals(TaskStatus.DONE, task(id).status)
        assertEquals(completedAt, task(id).completedAt)
        assertTrue(tasks.observeArchived().first().isEmpty())
    }

    @Test
    fun autoArchiveMovesDoneTasksLeftAloneForTheChosenDays() = runTest {
        val preferences = testPreferences()
        val purger = DataPurger(database.brainDumpDao(), database.taskDao(), database.energyCheckInDao(), preferences, time)
        val old = tasks.create(TaskDraft(title = "Old"))!!
        tasks.complete(old, alsoCompleteOpenSteps = false)
        time.advanceBy(Duration.ofDays(20))
        val recent = tasks.create(TaskDraft(title = "Recent"))!!
        tasks.complete(recent, alsoCompleteOpenSteps = false)
        val open = tasks.create(TaskDraft(title = "Open"))!!
        time.advanceBy(Duration.ofDays(11))

        purger.purge()

        // The default is 30 days: only the task done 31 days ago goes.
        assertEquals(TaskStatus.ARCHIVED, task(old).status)
        assertEquals(TaskStatus.DONE, task(recent).status)
        assertEquals(TaskStatus.ACTIVE, task(open).status)

        // Brought back, it counts as changed, so it isn't archived again on the next start.
        tasks.unarchive(old)
        purger.purge()
        assertEquals(TaskStatus.DONE, task(old).status)
    }

    @Test
    fun autoArchiveCanBeTurnedOff() = runTest {
        val preferences = testPreferences()
        preferences.setArchiveAfterDays(AutoArchive.NEVER)
        val id = tasks.create(TaskDraft(title = "Kept"))!!
        tasks.complete(id, alsoCompleteOpenSteps = false)
        time.advanceBy(Duration.ofDays(400))

        DataPurger(database.brainDumpDao(), database.taskDao(), database.energyCheckInDao(), preferences, time).purge()

        assertEquals(TaskStatus.DONE, task(id).status)
    }

    @Test
    fun recentlyDeletedListsDeletedTasks_deleteForeverRemovesOnlyThose() = runTest {
        val kept = tasks.create(TaskDraft(title = "Kept"))!!
        val first = tasks.create(TaskDraft(title = "First"))!!
        val second = tasks.create(TaskDraft(title = "Second"))!!
        tasks.addStep(second, "Step")
        tasks.delete(first)
        time.advanceBy(Duration.ofMinutes(1))
        tasks.delete(second)

        val deleted = tasks.observeDeleted().first()
        assertEquals(listOf("Second", "First"), deleted.map { it.title })
        assertEquals(time.now, deleted.first().deletedAt)

        tasks.deleteForever(kept)
        tasks.deleteForever(second)

        assertEquals(listOf(kept), tasks.observeTasks().first().map { it.id })
        assertEquals(listOf(first), tasks.observeDeleted().first().map { it.id })
        assertTrue(database.taskDao().getSubtasks(second).isEmpty())
    }
}
