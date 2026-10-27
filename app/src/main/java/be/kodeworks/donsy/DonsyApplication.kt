// SPDX-License-Identifier: GPL-3.0-or-later

package be.kodeworks.donsy

import android.app.Application
import be.kodeworks.donsy.core.di.ApplicationScope
import be.kodeworks.donsy.core.focus.FocusSessionManager
import be.kodeworks.donsy.core.routine.RoutineReminders
import be.kodeworks.donsy.data.maintenance.DataPurger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DonsyApplication : Application() {

    @Inject
    lateinit var dataPurger: DataPurger

    @Inject
    lateinit var focusSessionManager: FocusSessionManager

    @Inject
    lateinit var routineReminders: RoutineReminders

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { dataPurger.purge() }
        // Completes a session that ran out while the app was gone, or restores its alarm and notification.
        applicationScope.launch { focusSessionManager.recover(userPresent = false) }
        // Shows due routine reminders and sets the next alarm, again after every task change.
        applicationScope.launch { routineReminders.keepInSync() }
    }
}
