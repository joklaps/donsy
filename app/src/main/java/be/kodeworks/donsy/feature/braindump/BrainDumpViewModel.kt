// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.braindump

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.kodeworks.donsy.domain.model.BrainDumpItem
import be.kodeworks.donsy.domain.repository.BrainDumpRepository
import be.kodeworks.donsy.domain.time.TimeSource
import be.kodeworks.donsy.ui.format.CapturedAt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

data class BrainDumpUiState(
    val isLoading: Boolean = true,
    /** The whole inbox, also while searching. */
    val items: List<BrainDumpListItem> = emptyList(),
    val query: String = "",
    val newestFirst: Boolean = true,
    /** The items that match [query], in the chosen order; all items when the query is blank. */
    val visibleItems: List<BrainDumpListItem> = items,
)

data class BrainDumpListItem(
    val id: Long,
    val text: String,
    val capturedAt: CapturedAt,
)

sealed interface BrainDumpEvent {
    /** An item was deleted and can still be restored with [BrainDumpViewModel.undoDelete]. */
    data class Deleted(val id: Long) : BrainDumpEvent
}

@HiltViewModel
class BrainDumpViewModel @Inject constructor(
    private val repository: BrainDumpRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val newestFirst = MutableStateFlow(true)

    private val inbox: Flow<List<BrainDumpItem>> = repository.observeInbox()
        // Sorted here, so the order doesn't depend on the repository's. The id breaks ties.
        .map { items -> items.sortedWith(compareBy<BrainDumpItem> { it.createdAt }.thenBy { it.id }) }

    val uiState: StateFlow<BrainDumpUiState> = combine(inbox, query, newestFirst) { oldestFirst, search, newest ->
        val now = timeSource.now()
        val zone = timeSource.zone()
        val items = (if (newest) oldestFirst.asReversed() else oldestFirst)
            .map { BrainDumpListItem(it.id, it.text, CapturedAt.of(it.createdAt, now, zone)) }
        BrainDumpUiState(
            isLoading = false,
            items = items,
            query = search,
            newestFirst = newest,
            visibleItems = items.filter { matchesSearch(it.text, search) },
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrainDumpUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    fun toggleSortOrder() {
        newestFirst.value = !newestFirst.value
    }

    private val eventChannel = Channel<BrainDumpEvent>(Channel.BUFFERED)
    val events: Flow<BrainDumpEvent> = eventChannel.receiveAsFlow()

    /** Returns the job, so callers can wait until the text is stored. */
    fun updateText(id: Long, text: String): Job = viewModelScope.launch { repository.updateText(id, text) }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            eventChannel.send(BrainDumpEvent.Deleted(id))
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }
}

/**
 * True when [text] contains every word of [query], in any order. Ignores case and accents,
 * so "cafe" finds "Café".
 */
internal fun matchesSearch(text: String, query: String): Boolean {
    val words = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = searchable(text)
    return words.all { haystack.contains(searchable(it)) }
}

private fun searchable(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
