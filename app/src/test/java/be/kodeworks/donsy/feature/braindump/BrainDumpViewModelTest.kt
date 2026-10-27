// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.braindump

import app.cash.turbine.test
import be.kodeworks.donsy.domain.time.FakeTimeSource
import be.kodeworks.donsy.testing.FakeBrainDumpRepository
import be.kodeworks.donsy.testing.MainDispatcherRule
import be.kodeworks.donsy.ui.format.CapturedAt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

class BrainDumpViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val time = FakeTimeSource()
    private val repository = FakeBrainDumpRepository(time)
    // Lazy: viewModelScope must be created after MainDispatcherRule has installed the test dispatcher.
    private val viewModel by lazy { BrainDumpViewModel(repository, time) }

    @Test
    fun showsItemsNewestFirstWithCaptureDay() = runTest {
        repository.capture("older")
        time.advanceBy(Duration.ofDays(1))
        repository.capture("newer")

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(listOf("newer", "older"), state.items.map { it.text })
        assertEquals(listOf(CapturedAt.Day.Today, CapturedAt.Day.Yesterday), state.items.map { it.capturedAt.day })
    }

    @Test
    fun deleteHidesItemAndReportsIt_undoBringsItBack() = runTest {
        val id = repository.capture("maybe")!!

        viewModel.events.test {
            viewModel.delete(id)
            assertEquals(BrainDumpEvent.Deleted(id), awaitItem())
        }
        assertTrue(repository.observeInbox().first().isEmpty())

        viewModel.undoDelete(id)
        assertEquals(listOf(id), repository.observeInbox().first().map { it.id })
    }

    @Test
    fun updateTextChangesTheItem() = runTest {
        val id = repository.capture("draft")!!

        viewModel.updateText(id, "final")

        assertEquals("final", repository.observeInbox().first().single().text)
    }

    @Test
    fun queryFiltersVisibleItemsButKeepsTheWholeInbox() = runTest {
        repository.capture("Buy a birthday card")
        repository.capture("Call the dentist")

        viewModel.setQuery("card BUY")

        val state = viewModel.uiState.first { !it.isLoading && it.query == "card BUY" }
        assertEquals(listOf("Buy a birthday card"), state.visibleItems.map { it.text })
        assertEquals(2, state.items.size)

        viewModel.setQuery("  ")
        assertEquals(2, viewModel.uiState.first { it.query == "  " }.visibleItems.size)
    }

    @Test
    fun searchMatchesEveryWordIgnoringCaseAndAccents() {
        assertTrue(matchesSearch("Café met Ëlla", "cafe ella"))
        assertTrue(matchesSearch("Buy a birthday card", "  BIRTH  "))
        assertTrue(matchesSearch("anything", ""))
        assertFalse(matchesSearch("Buy a birthday card", "birthday cake"))
    }

    @Test
    fun sortOrderFlipsBetweenNewestAndOldestFirst() = runTest {
        repository.capture("first")
        time.advanceBy(Duration.ofMinutes(5))
        repository.capture("second")
        assertEquals(listOf("second", "first"), viewModel.uiState.first { !it.isLoading }.visibleItems.map { it.text })

        viewModel.toggleSortOrder()

        val state = viewModel.uiState.first { !it.newestFirst }
        assertEquals(listOf("first", "second"), state.visibleItems.map { it.text })
        assertEquals(listOf("first", "second"), state.items.map { it.text })
    }
}
