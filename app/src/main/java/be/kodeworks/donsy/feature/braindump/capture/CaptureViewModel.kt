// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.braindump.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.domain.repository.BrainDumpRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Quick capture into the BrainDump, shared by every screen that offers the capture button. */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val repository: BrainDumpRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    /** The keyboard-dictation tip is shown until the first thought is saved. */
    val showVoiceHint: StateFlow<Boolean> = preferences.voiceHintSeen
        .map { seen -> !seen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val savedEvents = Channel<Unit>(Channel.BUFFERED)

    /** Emits once per thought that was actually saved (blank input is ignored). */
    val saved: Flow<Unit> = savedEvents.receiveAsFlow()

    fun capture(text: String) {
        viewModelScope.launch {
            if (repository.capture(text) != null) {
                preferences.markVoiceHintSeen()
                savedEvents.send(Unit)
            }
        }
    }
}
