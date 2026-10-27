// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.kodeworks.donsy.core.focus.FocusAlarmScheduler
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.domain.archive.AutoArchive
import be.kodeworks.donsy.domain.focus.FocusDurations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    /** Precise alerts are on only when chosen and allowed by Android. */
    val preciseEndAlerts: Boolean = false,
    /** Session lengths offered on the Focus screen, in minutes. */
    val focusDurations: List<Int> = FocusDurations.DEFAULT_CHOICES,
    val focusDefaultMinutes: Int = FocusDurations.DEFAULT_MINUTES,
    /** Days until done tasks are archived by themselves; [AutoArchive.NEVER] is off. */
    val archiveAfterDays: Int = AutoArchive.DEFAULT_DAYS,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val alarms: FocusAlarmScheduler,
) : ViewModel() {

    /** Re-read when the screen resumes, since the permission is granted in system settings. */
    private val canScheduleExact = MutableStateFlow(alarms.canScheduleExact())

    val uiState: StateFlow<SettingsUiState> =
        combine(
            preferences.preciseEndAlerts,
            canScheduleExact,
            preferences.focusDurations,
            preferences.focusDefaultMinutes,
            preferences.archiveAfterDays,
        ) { chosen, allowed, durations, defaultMinutes, archiveAfterDays ->
            SettingsUiState(
                preciseEndAlerts = chosen && allowed,
                focusDurations = durations,
                focusDefaultMinutes = defaultMinutes,
                archiveAfterDays = archiveAfterDays,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Returns true when Android's permission screen should be opened to finish enabling it. */
    fun setPreciseEndAlerts(enabled: Boolean): Boolean {
        viewModelScope.launch { preferences.setPreciseEndAlerts(enabled) }
        return enabled && !canScheduleExact.value
    }

    fun setFocusDurationOffered(minutes: Int, offered: Boolean) {
        viewModelScope.launch { preferences.setFocusDurationOffered(minutes, offered) }
    }

    fun setFocusDefaultMinutes(minutes: Int) {
        viewModelScope.launch { preferences.setFocusDefaultMinutes(minutes) }
    }

    fun setArchiveAfterDays(days: Int) {
        viewModelScope.launch { preferences.setArchiveAfterDays(days) }
    }

    fun refresh() {
        canScheduleExact.value = alarms.canScheduleExact()
    }
}
