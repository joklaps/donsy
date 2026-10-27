// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings

import be.kodeworks.donsy.data.repository.RoomTaskRepository
import be.kodeworks.donsy.domain.model.TaskDraft
import be.kodeworks.donsy.domain.time.FakeTimeSource
import be.kodeworks.donsy.feature.settings.deleted.DeletedEntry
import be.kodeworks.donsy.feature.settings.deleted.DeletedKind
import be.kodeworks.donsy.feature.settings.deleted.RecentlyDeletedViewModel
import be.kodeworks.donsy.testing.FakeBrainDumpRepository
import be.kodeworks.donsy.testing.MainDispatcherRule
import be.kodeworks.donsy.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class RecentlyDeletedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val time = FakeTimeSource()
    private val database = inMemoryDatabase()
    private val tasks = RoomTaskRepository(database, time)
    private val brainDump = FakeBrainDumpRepository(time)

    // Lazy: viewModelScope must be created after MainDispatcherRule has installed the test dispatcher.
    private val viewModel by lazy { RecentlyDeletedViewModel(tasks, brainDump, time) }

    @After
    fun tearDown() = database.close()

    @Test
    fun listsDeletedTasksAndThoughtsWithTheDaysTheyAreStillKept() = runTest {
        val task = tasks.create(TaskDraft(title = "Essay"))!!
        tasks.delete(task)
        time.advanceBy(Duration.ofDays(2).plusHours(3))
        val thought = brainDump.capture("Call Sam")!!
        brainDump.delete(thought)

        val state = viewModel.uiState.first { !it.isLoading && !it.isEmpty }

        // Deleted 2 days and 3 hours ago: 4 days and 21 hours left, shown as 5.
        assertEquals(listOf(DeletedEntry(task, "Essay", daysLeft = 5)), state.tasks)
        assertEquals(listOf(DeletedEntry(thought, "Call Sam", daysLeft = 7)), state.thoughts)
    }

    @Test
    fun restoreAndDeleteForever() = runTest {
        val kept = tasks.create(TaskDraft(title = "Kept"))!!
        val gone = tasks.create(TaskDraft(title = "Gone"))!!
        tasks.delete(kept)
        tasks.delete(gone)

        viewModel.restore(DeletedKind.TASK, kept)
        viewModel.deleteForever(DeletedKind.TASK, gone)

        assertTrue(viewModel.uiState.first { !it.isLoading && it.tasks.isEmpty() }.isEmpty)
        assertEquals(listOf("Kept"), tasks.observeTasks().first().map { it.title })
    }
}
