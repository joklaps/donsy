// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import be.kodeworks.donsy.core.focus.FocusSessionManager
import be.kodeworks.donsy.data.db.DonsyDatabase
import be.kodeworks.donsy.data.preferences.UserPreferencesRepository
import be.kodeworks.donsy.data.repository.RoomBrainDumpRepository
import be.kodeworks.donsy.data.repository.RoomEnergyRepository
import be.kodeworks.donsy.data.repository.RoomFocusRepository
import be.kodeworks.donsy.data.repository.RoomTaskRepository
import be.kodeworks.donsy.domain.time.FakeTimeSource
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** Real repositories on an in-memory database, with fake alarms and notifications. */
class FocusTestRig(
    scope: CoroutineScope,
    preferencesDir: File,
    val alarms: FakeFocusAlarmScheduler = FakeFocusAlarmScheduler(),
) {
    val database: DonsyDatabase = inMemoryDatabase()
    val time = FakeTimeSource()
    val tasks = RoomTaskRepository(database, time)
    val brainDump = RoomBrainDumpRepository(database.brainDumpDao(), time)
    val sessions = RoomFocusRepository(database.focusSessionDao())
    val energy = RoomEnergyRepository(database.energyCheckInDao(), time)
    val preferences = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(preferencesDir, "prefs.preferences_pb") },
    )
    val notifier = FakeFocusNotifier()
    val manager = FocusSessionManager(sessions, tasks, time, alarms, notifier, preferences)

    fun close() = database.close()
}
