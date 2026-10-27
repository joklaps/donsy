// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.kodeworks.donsy.domain.repository.TaskRepository
import be.kodeworks.donsy.domain.time.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ArchiveUiState(
    val isLoading: Boolean = true,
    val tasks: List<ArchivedTask> = emptyList(),
)

data class ArchivedTask(
    val id: Long,
    val title: String,
    val doneOn: LocalDate?,
    val doneThisYear: Boolean,
)

/** Archived tasks, most recently done first; each can be brought back to the Done section. */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    val uiState: StateFlow<ArchiveUiState> = repository.observeArchived()
        .map { tasks ->
            val zone = timeSource.zone()
            val thisYear = timeSource.now().atZone(zone).year
            ArchiveUiState(
                isLoading = false,
                tasks = tasks.map { task ->
                    val doneOn = task.completedAt?.atZone(zone)?.toLocalDate()
                    ArchivedTask(task.id, task.title, doneOn, doneThisYear = doneOn?.year == thisYear)
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUiState())

    private val broughtBackChannel = Channel<Long>(Channel.BUFFERED)

    /** A task went back to the Done section; the screen offers Undo. */
    val broughtBack: Flow<Long> = broughtBackChannel.receiveAsFlow()

    fun bringBack(id: Long) {
        viewModelScope.launch {
            repository.unarchive(id)
            broughtBackChannel.send(id)
        }
    }

    fun archive(id: Long) {
        viewModelScope.launch { repository.archive(id) }
    }
}
