// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.kodeworks.donsy.data.maintenance.DataPurger
import be.kodeworks.donsy.domain.repository.BrainDumpRepository
import be.kodeworks.donsy.domain.repository.TaskRepository
import be.kodeworks.donsy.domain.time.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class RecentlyDeletedUiState(
    val isLoading: Boolean = true,
    val tasks: List<DeletedEntry> = emptyList(),
    /** BrainDump items. */
    val thoughts: List<DeletedEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && thoughts.isEmpty()
}

data class DeletedEntry(
    val id: Long,
    val text: String,
    /** Days until the purge removes it for good; at least 1, as the purge only runs on app start. */
    val daysLeft: Int,
)

enum class DeletedKind { TASK, THOUGHT }

/** Recently deleted tasks and thoughts, until [DataPurger] removes them for good. */
@HiltViewModel
class RecentlyDeletedViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val brainDump: BrainDumpRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    val uiState: StateFlow<RecentlyDeletedUiState> =
        combine(tasks.observeDeleted(), brainDump.observeDeleted()) { deletedTasks, deletedThoughts ->
            val now = timeSource.now()
            RecentlyDeletedUiState(
                isLoading = false,
                tasks = deletedTasks.map { DeletedEntry(it.id, it.title, daysLeft(it.deletedAt, now)) },
                thoughts = deletedThoughts.map { DeletedEntry(it.id, it.text, daysLeft(it.deletedAt, now)) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecentlyDeletedUiState())

    private val restoredChannel = Channel<DeletedKind>(Channel.BUFFERED)

    /** A task or thought was restored; the screen says where it went. */
    val restored: Flow<DeletedKind> = restoredChannel.receiveAsFlow()

    fun restore(kind: DeletedKind, id: Long) {
        viewModelScope.launch {
            when (kind) {
                DeletedKind.TASK -> tasks.restore(id)
                DeletedKind.THOUGHT -> brainDump.restore(id)
            }
            restoredChannel.send(kind)
        }
    }

    fun deleteForever(kind: DeletedKind, id: Long) {
        viewModelScope.launch {
            when (kind) {
                DeletedKind.TASK -> tasks.deleteForever(id)
                DeletedKind.THOUGHT -> brainDump.deleteForever(id)
            }
        }
    }

    private fun daysLeft(deletedAt: Instant, now: Instant): Int {
        val left = Duration.between(now, deletedAt.plus(DataPurger.RETENTION))
        // Rounded up: 6 days and 2 hours is "7 days".
        return maxOf(1L, (left.toHours() + 23) / 24).toInt()
    }
}
